package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;

/** puppeteer: the squid puppet. */
public final class SquidBehaviour implements PuppetBehaviour {
    public static final SquidBehaviour INSTANCE = new SquidBehaviour();

    private SquidBehaviour() {}

    /** squids: Flee — a short dash and a burst of ink that blinds everything nearby (a glow squid's also lights them up). */
    public static void flee(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        boolean glow = type == PuppetType.GLOW_SQUID;
        Vec3 dash = player.getLookAngle().scale(Config.PUPPETEER_SQUID_FLEE_DASH.get() * (player.isInWater() ? 1.0 : 0.3));
        player.setDeltaMovement(player.getDeltaMovement().add(dash));
        player.hurtMarked = true;
        double x = player.getX();
        double y = player.getY() + 0.5;
        double z = player.getZ();
        level.sendParticles(glow ? ParticleTypes.GLOW_SQUID_INK : ParticleTypes.SQUID_INK, x, y, z, 40, 0.6, 0.6, 0.6, 0.05);
        level.playSound(null, player.blockPosition(), glow ? SoundEvents.GLOW_SQUID_SQUIRT : SoundEvents.SQUID_SQUIRT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        double r = Config.PUPPETEER_SQUID_INK_RADIUS.get();
        int ticks = Config.PUPPETEER_SQUID_INK_SECONDS.get() * 20;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e != player && e.isAlive() && e.distanceToSqr(player) <= r * r)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), player);
            if (glow) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), player);
            }
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        FishBehaviour.tickFish(player, type);
    }

    /** flee: a dash and a burst of ink. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_SQUID_FLEE_COOLDOWN_TICKS.get());
        flee(player, type);
        Puppets.countAction(player);
    }
}
