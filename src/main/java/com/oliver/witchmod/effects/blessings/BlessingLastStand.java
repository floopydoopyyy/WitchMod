package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
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
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;

/**
 * A comeback tool (Last Stand, TOTEM OF UNDYING). A fatal blow leaves you standing with a brief window of total
 * invulnerability + an outward knockback burst to make space. It doesn't heal you back up — it just buys you the
 * moment. Not consumed per use: instead an escalating cooldown (doubling each revive) up to {@code
 * lastStandMaxUses} revives, then the blessing breaks.
 */
public final class BlessingLastStand extends Effect {
    /** short-lived invuln window end tick, per player (2s comeback window). */
    private static final Map<UUID, Long> INVULN_END = new HashMap<>();
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.20F), 1.6F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.95F), 1.3F);

    public BlessingLastStand() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 50, () -> Items.TOTEM_OF_UNDYING);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.LAST_STAND_USES, 0);
        target.setData(WitchModAttachments.LAST_STAND_COOLDOWN_END, 0L);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        INVULN_END.remove(target.getUUID());
    }

    @Override
    public Optional<String> scryingDetail(ServerPlayer target) {
        long now = target.serverLevel().getGameTime();
        int left = Math.max(0, Config.LASTSTAND_MAX_USES.get() - target.getData(WitchModAttachments.LAST_STAND_USES));
        long cdEnd = target.getData(WitchModAttachments.LAST_STAND_COOLDOWN_END);
        if (now < cdEnd) {
            return Optional.of("recharging " + ((cdEnd - now) / 20) + "s — " + left + " revive(s) left");
        }
        return Optional.of("ready — " + left + " revive(s) left");
    }

    /** true while the post-revive invulnerability window is open (checked independently of the blessing being active). */
    public static boolean isInvulnerable(ServerPlayer player) {
        return player.serverLevel().getGameTime() < INVULN_END.getOrDefault(player.getUUID(), 0L);
    }

    /**
     * A fatal blow: revive if off cooldown, otherwise let death proceed. @return true if the blow was saved.
     * Escalating cooldown (doubles per use); the blessing is consumed after the last allowed revive.
     */
    public static boolean tryTrigger(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (now < player.getData(WitchModAttachments.LAST_STAND_COOLDOWN_END)) {
            return false;
        }
        int uses = player.getData(WitchModAttachments.LAST_STAND_USES) + 1;
        player.setData(WitchModAttachments.LAST_STAND_USES, uses);

        player.setHealth((float) (double) Config.LASTSTAND_REVIVE_HEALTH.get());
        player.clearFire();
        player.fallDistance = 0.0F;
        int invuln = Config.LASTSTAND_INVULN_TICKS.get();
        INVULN_END.put(player.getUUID(), now + invuln);
        EffectUtil.addTimedEffect(player, MobEffects.MOVEMENT_SPEED, invuln + 60, 1);  // Speed II to reposition
        EffectUtil.addTimedEffect(player, MobEffects.FIRE_RESISTANCE, invuln + 60, 0);
        knockbackNearby(player);

        long cd = (long) Config.LASTSTAND_BASE_COOLDOWN_TICKS.get() << (uses - 1);
        player.setData(WitchModAttachments.LAST_STAND_COOLDOWN_END, now + cd);
        player.setData(WitchModAttachments.REVIVE_FLASH_END, now + Config.REVIVE_FLASH_TICKS);
        burst(player);
        // life synergy: a revive readies twist of fate's dodge again (last stand shares the rebirth pairing).
        if (EffectManager.isActive(player, Blessings.TWIST_OF_FATE)) {
            BlessingTwistOfFate.refreshOnRevive(player);
        }
        if (uses >= Config.LASTSTAND_MAX_USES.get()) {
            EffectManager.remove(player, Blessings.LAST_STAND); // spent
        }
        return true;
    }

    private static void burst(ServerPlayer player) {
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

    /** shove every nearby living entity (not the player) away hard, with no damage. */
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
            double falloff = Math.max(0.2, 1.0 - dist / radius);
            Vec3 push = away.scale(1.0 / dist).scale(force * falloff);
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, Math.max(0.35, push.y * 0.5 + 0.35), push.z));
            target.hurtMarked = true;
        }
    }
}
