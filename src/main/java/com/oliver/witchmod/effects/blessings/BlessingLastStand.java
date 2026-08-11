package com.oliver.witchmod.effects.blessings;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * One last surge (master-spec Last Stand, sacrificial item TOTEM OF UNDYING — swapped from Enchanted Golden
 * Apple, Oliver's call). A fatal blow instead leaves you on half a heart and CONSUMES the blessing, bursting
 * outward with high knockback (no damage) to everyone nearby and handing you brief Strength, Speed and Fire
 * Resistance to turn the fight around.
 *
 * <p>Triggered from {@link com.oliver.witchmod.effects.BlessingEventHandler}'s death hook via {@link #trigger}
 * (single-use, mirroring Immortality's hook). The custom "rise" sound (Section 12) is still pending — a
 * vanilla totem/explosion stand-in plays for now.
 */
public final class BlessingLastStand extends Effect {
    public BlessingLastStand() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 50, () -> Items.TOTEM_OF_UNDYING);
    }

    /** You find out when it actually saves you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Gold and white, for the revive burst. */
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.20F), 1.6F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.95F), 1.3F);

    /** The revive: half a heart, buffs, and an outward knockback burst. Called once, from the death hook. */
    public static void trigger(ServerPlayer player) {
        player.setHealth((float) (double) Config.LASTSTAND_REVIVE_HEALTH.get());
        player.clearFire();
        player.fallDistance = 0.0F;

        int buff = Config.LASTSTAND_BUFF_TICKS.get();
        EffectUtil.addTimedEffect(player, MobEffects.DAMAGE_BOOST, buff, 1);     // Strength II
        EffectUtil.addTimedEffect(player, MobEffects.MOVEMENT_SPEED, buff, 1);   // Speed II
        EffectUtil.addTimedEffect(player, MobEffects.FIRE_RESISTANCE, buff, 0);  // Fire Resistance
        EffectUtil.addTimedEffect(player, MobEffects.DIG_SPEED, buff, 1);        // Haste II

        knockbackNearby(player);

        // The on-screen totem-style flash (Blessed icon, client-side off the synced tick).
        player.setData(WitchModAttachments.REVIVE_FLASH_END, player.serverLevel().getGameTime() + Config.REVIVE_FLASH_TICKS);

        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.0, c.z, 80, 0.6, 0.9, 0.6, 0.5);
        level.sendParticles(GOLD, c.x, c.y + 1.0, c.z, 70, 0.7, 1.0, 0.7, 0.05);
        level.sendParticles(WHITE, c.x, c.y + 1.0, c.z, 50, 0.7, 1.0, 0.7, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 30, 0.5, 0.8, 0.5, 0.15);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** Shove every nearby living entity (not the player) away hard, with no damage. */
    private static void knockbackNearby(ServerPlayer player) {
        double radius = Config.LASTSTAND_RADIUS.get();
        double force = Config.LASTSTAND_KNOCKBACK.get();
        Vec3 centre = player.position();
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive())) {
            Vec3 away = target.position().subtract(centre);
            double dist = away.length();
            if (dist < 1.0E-3) {
                away = new Vec3(player.getRandom().nextDouble() - 0.5, 0.0, player.getRandom().nextDouble() - 0.5);
                dist = away.length();
            }
            double falloff = Math.max(0.2, 1.0 - dist / radius); // stronger up close
            Vec3 push = away.scale(1.0 / dist).scale(force * falloff);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, Math.max(0.35, push.y * 0.5 + 0.35), push.z));
            target.hurtMarked = true; // or the velocity never reaches the client
        }
    }
}
