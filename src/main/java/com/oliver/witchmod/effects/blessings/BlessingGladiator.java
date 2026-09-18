package com.oliver.witchmod.effects.blessings;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Blessings;

/**
 * A duellist's edge (Gladiator, sacrificial item GOLDEN SWORD) — a statement blessing. Right-click a sword or
 * axe to open a brief parry window; turn a frontal blow aside and answer with a heavier riposte.
 *
 * <p>The good stuff:
 * <ul>
 *   <li><b>Leeway</b> — you can parry for a few ticks AFTER a hit lands (press it a touch late and it still
 *       counts; the damage is refunded), so the timing never feels clunky.</li>
 *   <li><b>Perfect parry</b> — a tight, early parry hits harder ({@code gladiatorPerfectDamageMultiplier} vs
 *       the normal multiplier), STUNS the foe for half a second (heavy Slowness+Weakness so your knockback
 *       throws them), a bright shine plays on top, and the FX go up a gear.</li>
 *   <li><b>Projectile parry</b> — a frontal shot in-window is reflected exactly where you're LOOKING at a
 *       slightly higher speed (no auto-lock), with a subtle clash of its own.</li>
 *   <li>Full impact FX everywhere: directional sparks, a camera shake scaled per outcome, and layered sound.</li>
 * </ul>
 * A held shield always wins.
 */
public final class BlessingGladiator extends Effect {
    private record Riposte(int attackerId, long tick, float damage, boolean perfect) {}
    private record PendingHit(int attackerId, float damage, long tick) {}

    private static final Map<UUID, Riposte> PENDING_RIPOSTE = new HashMap<>();
    private static final Map<UUID, PendingHit> PENDING_HIT = new HashMap<>();
    /** tick the current window OPENED — projectile parries use this for a wider (both-ends) catch window than melee. */
    private static final Map<UUID, Long> WINDOW_OPEN = new HashMap<>();
    @Nullable private static Field attackTickerField;
    private static boolean attackTickerResolved;

    public BlessingGladiator() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.GOLDEN_SWORD);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.GLADIATOR_ACTIVE, 1);
        target.setData(WitchModAttachments.GLADIATOR_PARRY_END, 0L);
        target.setData(WitchModAttachments.GLADIATOR_COOLDOWN_END, 0L);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.GLADIATOR_ACTIVE) != 1) {
            target.setData(WitchModAttachments.GLADIATOR_ACTIVE, 1); // self-heal after respawn/relog
        }
        long now = target.serverLevel().getGameTime();

        long parryEnd = target.getData(WitchModAttachments.GLADIATOR_PARRY_END);
        if (parryEnd > 0 && now >= parryEnd) {
            whiff(target, now); // window is short now; the HUD bar carries the "ready" read, no ambient sparkle
        }

        Riposte r = PENDING_RIPOSTE.get(target.getUUID());
        if (r != null && now >= r.tick()) {
            PENDING_RIPOSTE.remove(target.getUUID());
            executeRiposte(target, r);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.GLADIATOR_ACTIVE, -1);
        target.setData(WitchModAttachments.GLADIATOR_PARRY_END, 0L);
        PENDING_RIPOSTE.remove(target.getUUID());
        PENDING_HIT.remove(target.getUUID());
        WINDOW_OPEN.remove(target.getUUID());
    }

    // --- Called from BlessingEventHandler ----------------------------------------------------------------

    public static boolean isParryWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    /** records an unparried melee hit so a slightly-late parry (leeway) can still catch it. */
    public static void recordLeewayHit(ServerPlayer player, int attackerId, float damage) {
        PENDING_HIT.put(player.getUUID(), new PendingHit(attackerId, damage, player.serverLevel().getGameTime()));
    }

    /** right-clicked a sword/axe: a reactive (leeway) parry if a hit just landed, else open a fresh window. */
    public static void tryStartParry(ServerPlayer player) {
        if (player.isBlocking() || !isParryWeapon(player.getMainHandItem())) {
            return;
        }
        long now = player.serverLevel().getGameTime();

        // leeway: caught a hit a fraction of a second ago? Parry it retroactively (refund the damage).
        PendingHit ph = PENDING_HIT.get(player.getUUID());
        if (ph != null && now - ph.tick() <= Config.GLADIATOR_LEEWAY_TICKS.get() && player.isAlive()) {
            Entity attacker = player.serverLevel().getEntity(ph.attackerId());
            if (attacker instanceof LivingEntity living && living.isAlive() && isFront(player, attacker.position())) {
                PENDING_HIT.remove(player.getUUID());
                player.heal(ph.damage()); // undo the damage you just took...
                player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0); //...and the knockback with it
                player.hurtMarked = true;
                boolean perfect = now - ph.tick() <= Config.GLADIATOR_PERFECT_TICKS.get();
                succeedMeleeParry(player, living, perfect);
                return;
            }
        }

        if (now < player.getData(WitchModAttachments.GLADIATOR_COOLDOWN_END)
                || player.getData(WitchModAttachments.GLADIATOR_PARRY_END) > 0) {
            return;
        }
        long end = now + Config.GLADIATOR_WINDOW_TICKS.get();
        player.setData(WitchModAttachments.GLADIATOR_PARRY_END, end);
        player.setData(WitchModAttachments.GLADIATOR_LOCK_END, end); // committed: no switch/swing/use until it resolves
        WINDOW_OPEN.put(player.getUUID(), now);
        player.serverLevel().playSound(null, player.blockPosition(),
                WitchModSounds.GLADIATOR_WHIFF.get(), SoundSource.PLAYERS, 0.7F, 1.1F);
    }

    /** A pre-emptive melee parry (window already open). Returns true if PARRIED (caller cancels the damage). */
    public static boolean tryParry(ServerPlayer player, Entity attacker) {
        if (player.isBlocking() || !(attacker instanceof LivingEntity living) || !isParryWeapon(player.getMainHandItem())) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        long parryEnd = player.getData(WitchModAttachments.GLADIATOR_PARRY_END);
        if (parryEnd <= 0 || now >= parryEnd || !isFront(player, attacker.position())) {
            return false;
        }
        // perfect if the danger arrived in the first few ticks of the window (you opened it just in time).
        boolean perfect = (parryEnd - now) >= (Config.GLADIATOR_WINDOW_TICKS.get() - Config.GLADIATOR_PERFECT_TICKS.get());
        succeedMeleeParry(player, living, perfect);
        return true;
    }

    /**
     * an EMPTY parry: a blockable, non-melee hit during the window (an explosion, a hurting-projectile impact
     * that wasn't reflected, etc.) is simply turned aside — with no riposte, since there's no one to hit back.
     * Parries anything a shield could (not fall/drowning etc., which bypass shields), and only from the front.
     * This is what makes the parry a real ALTERNATIVE to a shield rather than a strictly-worse one.
     */
    public static boolean tryEmptyParry(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        if (player.isBlocking() || !isParryWeapon(player.getMainHandItem())
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        long parryEnd = player.getData(WitchModAttachments.GLADIATOR_PARRY_END);
        if (parryEnd <= 0 || now >= parryEnd) {
            return false;
        }
        Vec3 srcPos = source.getSourcePosition();
        if (srcPos != null && !isFront(player, srcPos)) {
            return false; // must face it, like a shield
        }
        // success — negate it. No riposte, no weapon-swing cooldown (you didn't swing).
        ServerLevel level = player.serverLevel();
        WINDOW_OPEN.remove(player.getUUID());
        player.setData(WitchModAttachments.GLADIATOR_PARRY_END, 0L);
        player.setData(WitchModAttachments.GLADIATOR_LOCK_END, now);
        player.setData(WitchModAttachments.GLADIATOR_COOLDOWN_END,
                now + Math.round(Config.GLADIATOR_COOLDOWN_SECONDS.get() * 20.0));
        level.playSound(null, player.blockPosition(), WitchModSounds.GLADIATOR_PARRY.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
        parryFx(player, srcPos != null ? srcPos : player.position().add(player.getLookAngle()), false);
        shake(player, Config.GLADIATOR_SHAKE_STRENGTH.get(), now);
        Blessings.GLADIATOR.get().markDiscoveredByVictim(player);
        return true;
    }

    /**
     * A projectile about to hit you while a window is open. Returns true if PARRIED. Reflected exactly where
     * you're LOOKING at a slightly-higher-but-still-low speed — no auto-lock.
     */
    public static boolean tryParryProjectile(ServerPlayer player, Projectile projectile) {
        if (player.isBlocking() || !isParryWeapon(player.getMainHandItem())) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        // projectiles use a WIDER catch window than melee (a few ticks of slack on each side of the window),
        // and it survives the window's whiff so a fast shot caught in the tail still counts.
        Long open = WINDOW_OPEN.get(player.getUUID());
        if (open == null) {
            return false;
        }
        int lenient = Config.GLADIATOR_PROJECTILE_LEEWAY_TICKS.get();
        if (now < open - lenient || now > open + Config.GLADIATOR_WINDOW_TICKS.get() + lenient) {
            return false;
        }
        Vec3 vel = projectile.getDeltaMovement();
        if (vel.lengthSqr() < 1.0e-6 || player.getLookAngle().dot(vel.normalize().scale(-1.0)) < Config.GLADIATOR_FRONT_DOT.get()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        WINDOW_OPEN.remove(player.getUUID());
        player.setData(WitchModAttachments.GLADIATOR_PARRY_END, 0L);
        player.setData(WitchModAttachments.GLADIATOR_LOCK_END, now);
        player.setData(WitchModAttachments.GLADIATOR_COOLDOWN_END,
                now + Math.round(Config.GLADIATOR_COOLDOWN_SECONDS.get() * 20.0));
        setWeaponCooldown(player, Config.GLADIATOR_SWORD_COOLDOWN_TICKS.get()); // reflect -> sword swing timing

        // send it where you're aiming, with a soft assist toward an entity roughly in your view.
        Vec3 look = player.getLookAngle();
        Vec3 aim = look;
        LivingEntity assist = coneTarget(player, look, Config.GLADIATOR_REFLECT_AIM_CONE.get(), Config.GLADIATOR_REFLECT_AIM_RANGE.get());
        if (assist != null) {
            Vec3 toTarget = assist.getEyePosition().subtract(player.getEyePosition()).normalize();
            double bias = Config.GLADIATOR_REFLECT_AIM_BIAS.get();
            aim = look.scale(1.0 - bias).add(toTarget.scale(bias)).normalize();
        }
        projectile.setOwner(player);
        projectile.shoot(aim.x, aim.y, aim.z, Config.GLADIATOR_REFLECT_SPEED.get().floatValue(), 0.0F);
        Vec3 nudge = projectile.getDeltaMovement().normalize().scale(0.6);
        projectile.setPos(projectile.getX() + nudge.x, projectile.getY() + nudge.y, projectile.getZ() + nudge.z);

        level.playSound(null, player.blockPosition(), WitchModSounds.GLADIATOR_REFLECT.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        reflectFx(player, look);
        shake(player, Config.GLADIATOR_SHAKE_STRENGTH.get() * 0.6, now);
        Blessings.GLADIATOR.get().markDiscoveredByVictim(player);
        return true;
    }

    // --- Success / failure -------------------------------------------------------------------------------

    private static void succeedMeleeParry(ServerPlayer player, LivingEntity attacker, boolean perfect) {
        long now = player.serverLevel().getGameTime();
        WINDOW_OPEN.remove(player.getUUID());
        player.setData(WitchModAttachments.GLADIATOR_PARRY_END, 0L);
        player.setData(WitchModAttachments.GLADIATOR_LOCK_END, now); // parry landed — free to act again
        player.setData(WitchModAttachments.GLADIATOR_COOLDOWN_END,
                now + Math.round(Config.GLADIATOR_COOLDOWN_SECONDS.get() * 20.0));
        setWeaponCooldown(player, Config.GLADIATOR_SWORD_COOLDOWN_TICKS.get()); // riposte -> sword swing timing

        float weaponDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        PENDING_RIPOSTE.put(player.getUUID(),
                new Riposte(attacker.getId(), now + Config.GLADIATOR_RIPOSTE_DELAY_TICKS.get(), weaponDamage, perfect));

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), WitchModSounds.GLADIATOR_PARRY.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (perfect) {
            level.playSound(null, player.blockPosition(), WitchModSounds.GLADIATOR_PERFECT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        parryFx(player, attacker.position(), perfect);
        shake(player, Config.GLADIATOR_SHAKE_STRENGTH.get() * (perfect ? 1.7 : 1.0), now);
        Blessings.GLADIATOR.get().markDiscoveredByVictim(player);
        // parry_frenzy synergy (with Berserker): a clean parry banks a chunk of berserker stacks.
        if (com.oliver.witchmod.synergy.Synergies.PARRY_FRENZY.activeFor(player)) {
            BlessingBerserker.addStacks(player, Config.BERSERKER_GLADIATOR_PARRY_STACKS.get());
        }
    }

    private static void whiff(ServerPlayer player, long now) {
        player.setData(WitchModAttachments.GLADIATOR_PARRY_END, 0L);
        player.setData(WitchModAttachments.GLADIATOR_LOCK_END, now + Config.GLADIATOR_WHIFF_LOCK_EXTRA_TICKS.get()); // extra committed frames
        player.setData(WitchModAttachments.GLADIATOR_COOLDOWN_END,
                now + Math.round(Config.GLADIATOR_WHIFF_COOLDOWN_SECONDS.get() * 20.0));
        setWeaponCooldown(player, Config.GLADIATOR_AXE_COOLDOWN_TICKS.get()); // whiff -> slower axe swing timing
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), WitchModSounds.GLADIATOR_WHIFF.get(), SoundSource.PLAYERS, 0.8F, 0.9F);
        // A little fumble puff + a soft jolt.
        Vec3 c = player.position().add(player.getLookAngle().scale(0.5)).add(0, 1.1, 0);
        level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.15, 0.15, 0.15, 0.02);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.15, 0.15, 0.15, 0.05);
        shake(player, Config.GLADIATOR_SHAKE_STRENGTH.get() * 0.5, now);
    }

    private static void executeRiposte(ServerPlayer player, Riposte r) {
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.blockPosition(), WitchModSounds.GLADIATOR_WHIFF.get(), SoundSource.PLAYERS, 0.9F, 1.1F);

        Entity target = level.getEntity(r.attackerId());
        if (!(target instanceof LivingEntity victim) || !victim.isAlive()) {
            return; // they got away — the swing flails
        }
        float mult = r.perfect() ? Config.GLADIATOR_PERFECT_DAMAGE_MULT.get().floatValue()
                : Config.GLADIATOR_RIPOSTE_DAMAGE_MULT.get().floatValue();
        if (r.perfect()) {
            // stun so the knockback isn't shrugged off.
            int stun = Config.GLADIATOR_PERFECT_STUN_TICKS.get();
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 1, false, true));
        }
        victim.invulnerableTime = 0;
        victim.hurt(player.damageSources().playerAttack(player), r.damage() * mult);
        double kb = Config.GLADIATOR_RIPOSTE_KNOCKBACK.get() * (r.perfect() ? 1.4 : 1.0);
        victim.knockback(kb, player.getX() - victim.getX(), player.getZ() - victim.getZ());

        level.playSound(null, victim.blockPosition(), WitchModSounds.GLADIATOR_RIPOSTE.get(), SoundSource.PLAYERS, 1.0F, r.perfect() ? 1.15F : 1.0F);
        riposteFx(victim, r.perfect());
    }

    // --- FX ----------------------------------------------------------------------------------------------

    /** A tight CLANG spark at your guard, a short directed spark toward the attacker, and a single clean flash on perfect. */
    private static void parryFx(ServerPlayer player, Vec3 attackerPos, boolean perfect) {
        ServerLevel level = player.serverLevel();
        Vec3 guard = player.position().add(0, 1.1, 0);
        level.sendParticles(ParticleTypes.CRIT, guard.x, guard.y, guard.z, perfect ? 8 : 5, 0.2, 0.2, 0.2, 0.2);

        // A couple of sparks toward where the blow came from — readable, not spam.
        Vec3 dir = attackerPos.subtract(player.position()).normalize();
        for (int i = 1; i <= 2; i++) {
            Vec3 p = guard.add(dir.scale(0.45 * i));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.02);
        }
        if (perfect) {
            level.sendParticles(ParticleTypes.END_ROD, guard.x, guard.y, guard.z, 6, 0.25, 0.25, 0.25, 0.06);
            level.sendParticles(ParticleTypes.FLASH, guard.x, guard.y, guard.z, 1, 0, 0, 0, 0);
        }
    }

    private static void riposteFx(LivingEntity victim, boolean perfect) {
        ServerLevel level = (ServerLevel) victim.level();
        Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, perfect ? 12 : 7, 0.25, 0.25, 0.25, 0.25);
        if (perfect) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.08);
        }
    }

    private static void reflectFx(ServerPlayer player, Vec3 look) {
        ServerLevel level = player.serverLevel();
        Vec3 guard = player.getEyePosition().add(look.scale(0.4));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, guard.x, guard.y, guard.z, 1, 0, 0, 0, 0);
        for (int i = 1; i <= 3; i++) {
            Vec3 p = guard.add(look.scale(0.55 * i));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.02);
        }
    }

    // --- Helpers -----------------------------------------------------------------------------------------

    /** the living entity best aligned with the player's look, within the cone + range (assist-aim target). */
    @Nullable
    private static LivingEntity coneTarget(ServerPlayer player, Vec3 look, double coneDegrees, double range) {
        double minDot = Math.cos(Math.toRadians(coneDegrees));
        Vec3 eye = player.getEyePosition();
        LivingEntity best = null;
        double bestDot = minDot;
        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && en.attackable())) {
            Vec3 to = e.getEyePosition().subtract(eye);
            if (to.lengthSqr() < 1.0e-4) {
                continue;
            }
            double dot = look.dot(to.normalize());
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static boolean isFront(ServerPlayer player, Vec3 sourcePos) {
        Vec3 to = sourcePos.subtract(player.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() < 1.0e-4) {
            return true; // right on top of you — treat as parryable
        }
        Vec3 look = player.getLookAngle();
        return new Vec3(look.x, 0, look.z).normalize().dot(flat.normalize()) >= Config.GLADIATOR_FRONT_DOT.get();
    }

    private static void shake(ServerPlayer player, double strength, long now) {
        player.setData(WitchModAttachments.GLADIATOR_SHAKE_STRENGTH, strength);
        player.setData(WitchModAttachments.GLADIATOR_SHAKE_END, now + Config.GLADIATOR_SHAKE_TICKS.get());
    }

    /**
     * impose a fixed weapon swing cooldown of {@code cooldownTicks}, whatever weapon is held. The ticker
     * counts up to the held item's delay; setting it to {@code delay - cooldownTicks} (allowed to go negative
     * — the attack-strength SCALE is clamped to 0 while it's below zero) makes the recharge take exactly
     * {@code cooldownTicks}, so a sword's timing or an axe's timing can be applied to either weapon.
     */
    private static void setWeaponCooldown(ServerPlayer player, int cooldownTicks) {
        if (!attackTickerResolved) {
            attackTickerResolved = true;
            try {
                attackTickerField = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                attackTickerField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                WitchMod.LOGGER.warn("[Gladiator] Could not access attackStrengthTicker; the swing-cooldown effect is skipped.", e);
                attackTickerField = null;
            }
        }
        if (attackTickerField != null) {
            try {
                attackTickerField.setInt(player, (int) player.getCurrentItemAttackStrengthDelay() - cooldownTicks);
            } catch (ReflectiveOperationException ignored) {
                // non-fatal
            }
        }
        // the attack-indicator reads the CLIENT player's ticker, so sync the ready-tick down and let the
        // client set its own ticker (server-side alone never shows the cooldown).
        player.setData(WitchModAttachments.GLADIATOR_WEAPON_READY, player.serverLevel().getGameTime() + cooldownTicks);
    }
}
