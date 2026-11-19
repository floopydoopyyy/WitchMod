package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/** puppeteer: the goat puppet. */
public final class GoatBehaviour implements PuppetBehaviour {
    public static final GoatBehaviour INSTANCE = new GoatBehaviour();

    private GoatBehaviour() {}

    // --- goat ---------------------------------------------------------------------------------------

    /** a ram's power as 0..1 → its share of the full speed / length / knockback (a tap is about a third). */
    private static double ramShare(Player player) {
        return 0.33 + 0.67 * Mth.clamp(player.getData(WitchModAttachments.PUPPET_DASH_POWER) / 100.0, 0.0, 1.0);
    }

    public static int ramTicks(Player player) {
        return Math.max(4, (int) Math.round(Config.PUPPETEER_GOAT_RAM_TICKS.get() * ramShare(player)));
    }

    public static double ramSpeed(Player player) {
        return Config.PUPPETEER_GOAT_RAM_SPEED.get() * ramShare(player);
    }

    /** goat upkeep: the ram's charge (right-click) or a screaming goat's shriek (left-click), the ram itself, the slow. */
    private static void tickGoat(ServerPlayer player, PuppetType type) {
        boolean shrieking = type == PuppetType.SCREAMING_GOAT && ATTACK_HELD.contains(player.getUUID());
        if (shrieking) {
            if (ready2(player)) {
                int cap = Config.PUPPETEER_GOAT_SHRIEK_CHARGE_TICKS.get();
                int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
                if (hold == 0) {
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5F, 1.4F);
                }
                if (hold < cap) {
                    player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(cap, hold + chargeStep(player)));
                }
            }
        } else if (!LUNGES.containsKey(player.getUUID())) {
            tickHold(player, type);
        }
        tickLunge(player, type);
        chargeSlow(player);
    }

    /** goat: let go of the ram — off it goes (your client drives it, scaled by the charge: PUPPET_DASH_POWER). */
    private static void goatRam(ServerPlayer player, PuppetType type, int charge) {
        if (charge < 5 || !ready(player) || LUNGES.containsKey(player.getUUID())) {
            return;
        }
        int power = (int) Math.round(Mth.clamp(charge / (float) Config.PUPPETEER_GOAT_RAM_CHARGE_TICKS.get(), 0.0F, 1.0F) * 100);
        long now = player.level().getGameTime();
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, power);
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        cooldown(player, Config.PUPPETEER_GOAT_RAM_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GOAT_STEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** the ram connects: no damage at all — just an absurd launch the way you were going. it stops the ram. */
    private static void tickRam(ServerPlayer player, PuppetType type) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.4),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            double kb = Config.PUPPETEER_GOAT_RAM_KNOCKBACK.get() * ramShare(player)
                    * Math.max(0.3, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            e.setDeltaMovement(dir.x * kb, 0.4 + kb * 0.15, dir.z * kb);
            e.hurtMarked = true;
            e.resetFallDistance();
            player.serverLevel().playSound(null, e.blockPosition(),
                    type == PuppetType.SCREAMING_GOAT ? SoundEvents.GOAT_SCREAMING_RAM_IMPACT : SoundEvents.GOAT_RAM_IMPACT,
                    SoundSource.PLAYERS, 1.5F, 1.0F);
            endLunge(player);
            return;
        }
    }

    /**
     * screaming goat: let go of a FULLY charged shriek — a warden-style sonic boom straight down your aim that goes
     * through walls, does no damage, and blasts everything in its path away with one insanely high knockback, to the
     * goat's own scream.
     */
    private static void goatShriek(ServerPlayer player, int charge) {
        if (charge < Config.PUPPETEER_GOAT_SHRIEK_CHARGE_TICKS.get() || !ready2(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double range = Config.PUPPETEER_GOAT_SHRIEK_RANGE.get();
        for (int i = 1; i <= range; i++) {
            Vec3 at = eye.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        double kb = Config.PUPPETEER_GOAT_SHRIEK_KNOCKBACK.get();
        Vec3 end = eye.add(dir.scale(range));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, end).inflate(1.5),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(dir);
            if (along < 0.0 || along > range || to.subtract(dir.scale(along)).lengthSqr() > 2.25) {
                continue; // not in the blast's path
            }
            e.setDeltaMovement(dir.x * kb, Math.max(0.6, dir.y * kb + 0.6), dir.z * kb);
            e.hurtMarked = true;
            e.resetFallDistance();
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.2F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.PLAYERS, 3.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_SCREAMING_RAM_IMPACT, SoundSource.PLAYERS, 2.0F, 0.6F);
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, level.getGameTime() + Config.PUPPETEER_GOAT_SHRIEK_COOLDOWN_TICKS.get());
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickGoat(player, type);
    }

    /** the goat lowers its head with its own wind-up call. */
    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        player.serverLevel().playSound(null, player.blockPosition(), type == PuppetType.SCREAMING_GOAT
                ? SoundEvents.GOAT_SCREAMING_PREPARE_RAM : SoundEvents.GOAT_PREPARE_RAM, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        goatRam(player, type, charge);
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        tickRam(player, type);
    }

    /** a screaming goat's left-click is its shriek — no melee. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return type == PuppetType.SCREAMING_GOAT;
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return type != PuppetType.SCREAMING_GOAT;
    }

    /** goats shrug off falls (vanilla: 10 blocks less fall damage). */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, HorseBehaviour.HORSE_FALL_ID, 10.0, AttributeModifier.Operation.ADD_VALUE);
    }

    /** a screaming goat holds left-click to charge its shriek. */
    @Override
    public boolean holdsAttack(PuppetType type) {
        return type == PuppetType.SCREAMING_GOAT;
    }

    @Override
    public void releaseAttackHeld(ServerPlayer player, PuppetType type, int charge) {
        goatShriek(player, charge);
    }

    /** the ram — and a screaming goat's shriek on a held left-click. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        if (type == PuppetType.SCREAMING_GOAT) {
            out.add(hold(Key.ATTACK), "witchmod.puppeteer.action.shriek", PuppetControls.ready2(player));
        }
    }
}
