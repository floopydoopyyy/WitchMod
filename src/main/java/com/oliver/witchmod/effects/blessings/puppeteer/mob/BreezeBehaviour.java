package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;

/** puppeteer: the breeze puppet. */
public final class BreezeBehaviour implements PuppetBehaviour {
    public static final BreezeBehaviour INSTANCE = new BreezeBehaviour();

    private BreezeBehaviour() {}

    /**
     * breeze upkeep: anything hostile that comes too close pushes you away on the wind (gently), the gale charges, and
     * charging slows you.
     */
    private static void tickBreeze(ServerPlayer player) {
        double r = Config.PUPPETEER_BREEZE_REPEL_RANGE.get();
        if (r > 0.0) {
            Vec3 push = Vec3.ZERO;
            for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                    e -> e != player && e.isAlive() && e.distanceToSqr(player) <= r * r
                            && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
                Vec3 away = player.position().subtract(e.position());
                push = push.add(away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.1, 0.0) : away.normalize());
            }
            if (push.lengthSqr() > 0.0) {
                player.setDeltaMovement(player.getDeltaMovement().add(push.normalize().scale(Config.PUPPETEER_BREEZE_REPEL_STRENGTH.get())));
                player.hurtMarked = true;
            }
        }
        // holding left-click (the gale off its cooldown) builds the gale
        if (ATTACK_HELD.contains(player.getUUID()) && ready(player)) {
            int full = Config.PUPPETEER_BREEZE_GALE_CHARGE_TICKS.get();
            player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(full, player.getData(WitchModAttachments.PUPPET_FUSE) + chargeStep(player)));
        }
        // a left-click that wasn't the start of a hold: a plain wind charge
        Long pressed = PRESSED.get(player.getUUID());
        if (pressed != null && !ATTACK_HELD.contains(player.getUUID()) && player.level().getGameTime() - pressed >= 2) {
            PRESSED.remove(player.getUUID());
            breezeShot(player);
        }
        chargeSlow(player);
    }

    /** a left-click waiting to see if it's a tap (a wind charge) or the start of a hold (a gale): when it was pressed. */
    private static final java.util.Map<java.util.UUID, Long> PRESSED =
            com.oliver.witchmod.data.EffectState.sessionConcurrent("puppeteer/breeze/pressed");

    /** right-click: a quick dash on the wind, the way you're looking. */
    private static void dash(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now < player.getData(WitchModAttachments.PUPPET_RAGE_END)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_BREEZE_DASH_COOLDOWN_TICKS.get());
        // vanilla's slide: flat along the way you're facing, quick, for a few ticks
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        Vec3 dir = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
        SLIDES.put(player.getUUID(), new Slide(dir, Config.PUPPETEER_BREEZE_DASH_TICKS.get()));
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, SLIDING);
        player.resetFallDistance();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_SLIDE, SoundSource.PLAYERS, 1.2F, 1.0F);
        Puppets.countAction(player);
    }

    /** breeze: left-click — the real breeze's wind charge (its knockback, its gust, it trips doors and redstone). */
    private static void breezeShot(ServerPlayer player) {
        if (!ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_BREEZE_SHOT_COOLDOWN_TICKS.get());
        windCharge(player, 1.0F, false);
    }

    /** breeze: let go of the gale — at least half charged or it fizzles; then one slower, far stronger wind charge. */
    private static void breezeRelease(ServerPlayer player, int charge) {
        int full = Config.PUPPETEER_BREEZE_GALE_CHARGE_TICKS.get();
        if (charge < full / 2 || !ready(player)) {
            return;
        }
        cooldown(player, Config.PUPPETEER_BREEZE_GALE_COOLDOWN_TICKS.get());
        windCharge(player, Mth.clamp(charge / (float) full, 0.5F, 1.0F), true);
    }

    private static final String GALE_TAG = "witchmod_gale";

    /** gales in flight — they drag a howling wake of gusts behind them. */
    private static final Set<net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge> GALES = ConcurrentHashMap.newKeySet();

    /** each gale in flight leaves a trail of gusts (and a low whoosh now and then), so you see the weight of it coming. */
    public static void trailGales() {
        GALES.removeIf(gale -> {
            if (gale.isRemoved() || !(gale.level() instanceof ServerLevel level)) {
                return true;
            }
            Vec3 at = gale.position();
            level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 2, 0.25, 0.25, 0.25, 0.0);
            if (gale.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
            if (gale.tickCount % 8 == 0) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.2F, 0.5F);
            }
            return false;
        });
    }

    /** a breeze wind charge from your snout along your aim; a gale flies slower and remembers its strength (tags). */
    private static void windCharge(ServerPlayer player, float strength, boolean gale) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge charge =
                new net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE, level);
        charge.setOwner(player);
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.6));
        charge.setPos(from.x, from.y - 0.2, from.z);
        charge.shoot(look.x, look.y, look.z, gale ? 0.8F : 1.6F, 0.5F);
        if (gale) {
            charge.addTag(GALE_TAG);
            charge.addTag(GALE_TAG + ":" + Math.round(strength * 100));
        }
        level.addFreshEntity(charge);
        if (gale) {
            GALES.add(charge); // trails a howling wake (see trailGales)
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 2.0F, 0.5F);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS,
                gale ? 2.0F : 1.5F, gale ? 0.7F : 1.0F);
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /**
     * a gale lands: instead of the wind charge's own burst, a far bigger one — radius and knockback scaled by how long
     * it was charged — with the same gust and the same redstone-tripping. a direct hit also does the gale's damage.
     */
    public static boolean galeImpact(net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge charge,
                                      net.minecraft.world.phys.HitResult hit) {
        if (!charge.getTags().contains(GALE_TAG) || !(charge.level() instanceof ServerLevel level)) {
            return false;
        }
        float strength = 1.0F;
        for (String tag : charge.getTags()) {
            if (tag.startsWith(GALE_TAG + ":")) {
                strength = Integer.parseInt(tag.substring(GALE_TAG.length() + 1)) / 100.0F;
            }
        }
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() != charge.getOwner()) {
            entityHit.getEntity().hurt(charge.damageSources().windCharge(charge,
                    charge.getOwner() instanceof LivingEntity owner ? owner : null), Config.PUPPETEER_BREEZE_GALE_DAMAGE.get().floatValue() * strength);
        }
        Vec3 at = hit.getLocation();
        float radius = (float) (Config.PUPPETEER_BREEZE_GALE_RADIUS.get() * strength);
        float knockback = (float) (Config.PUPPETEER_BREEZE_GALE_KNOCKBACK.get() * strength);
        net.minecraft.world.level.ExplosionDamageCalculator calc = new net.minecraft.world.level.SimpleExplosionDamageCalculator(
                true, false, java.util.Optional.of(knockback),
                BuiltInRegistries.BLOCK.getTag(net.minecraft.tags.BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(java.util.function.Function.identity()));
        level.explode(charge, null, calc, at.x, at.y, at.z, radius, false, Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE, SoundEvents.BREEZE_WIND_CHARGE_BURST);
        charge.discard();
        return true;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickBreeze(player);
        // the slide carries it along, low and quick
        Slide slide = SLIDES.get(player.getUUID());
        if (slide != null) {
            double speed = Config.PUPPETEER_BREEZE_DASH_SPEED.get();
            player.setDeltaMovement(slide.dir().x * speed, Math.min(player.getDeltaMovement().y, 0.1), slide.dir().z * speed);
            player.hurtMarked = true;
            if (slide.left() <= 1) {
                SLIDES.remove(player.getUUID());
                player.setData(WitchModAttachments.PUPPET_DASH_POWER, IDLE);
            } else {
                SLIDES.put(player.getUUID(), new Slide(slide.dir(), slide.left() - 1));
            }
            if (player.tickCount % 2 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.SMALL_GUST, player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
        }
        // landed from a leap
        if (state(player) == LEAPING && player.onGround() && player.getDeltaMovement().y <= 0.0) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, IDLE);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** pressed: a wind charge if it's let go at once, the gale's charge if it's held. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        PRESSED.put(player.getUUID(), player.level().getGameTime());
    }

    @Override
    public boolean holdsAttack(PuppetType type) {
        return true;
    }

    /** let go: the gale, if it's half charged or more — else just a wind charge. */
    @Override
    public void releaseAttackHeld(ServerPlayer player, PuppetType type, int charge) {
        PRESSED.remove(player.getUUID());
        if (charge >= Config.PUPPETEER_BREEZE_GALE_CHARGE_TICKS.get() / 2 && ready(player)) {
            breezeRelease(player, charge);
        } else {
            breezeShot(player);
        }
    }

    /** right-click: the dash. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        dash(player);
    }

    /** no melee at all. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true;
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    // states (PUPPET_DASH_POWER, synced: the model's animation)
    public static final int IDLE = 0;
    public static final int INHALING = 1;
    public static final int LEAPING = 2;
    public static final int SLIDING = 3;

    public static int state(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.BREEZE
                ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : IDLE;
    }

    /** a slide's way and ticks left (it's carried along it, quick and low, as the real breeze slides). */
    private record Slide(Vec3 dir, int left) {}

    private static final java.util.Map<java.util.UUID, Slide> SLIDES =
            com.oliver.witchmod.data.EffectState.sessionConcurrent("puppeteer/breeze/slides");

    /** like a real breeze it doesn't fly: it leaps (hold jump), and hovers while it charges a gale. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, IDLE);
        Puppets.revokeBatFlight(player);
    }

    /** holding jump draws breath (vanilla's inhale); let go and it launches — the client flies it, the model leaps. */
    @Override
    public void chargedJump(ServerPlayer player, PuppetType type, int phase, int charge) {
        ServerLevel level = player.serverLevel();
        if (phase == 0) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, INHALING);
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, LEAPING);
            player.resetFallDistance();
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.2F, 1.0F);
            level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
        }
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** a breeze puppet deflects projectiles back where they came from (wind charges excepted, as in vanilla). */
    @Override
    public void struckByProjectile(ServerPlayer player, PuppetType type, Projectile projectile, ProjectileImpactEvent event) {
        if (!(projectile instanceof net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge)) {
            net.minecraft.world.entity.projectile.ProjectileDeflection.REVERSE.deflect(projectile, player, player.serverLevel().random);
            event.setCanceled(true);
        }
    }

    /** left-click: a wind charge. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        PRESSED.remove(player.getUUID());
        SLIDES.remove(player.getUUID());
    }

    /** wind charge (tap) and gale (hold) on left-click; the dash on right-click. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.wind_charge", PuppetControls.ready2(player));
        out.add(hold(Key.ATTACK), "witchmod.puppeteer.action.gale", PuppetControls.ready(player));
        out.add(tap(Key.USE), "witchmod.puppeteer.action.breeze_dash", player.getData(WitchModAttachments.PUPPET_RAGE_END));
    }
}
