package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * OTHER players within {@code leaderRadius} (never you) are kept topped up with
 * regen and resis, with fx
 * drawn client-side off the synced {@code LEADER_ACTIVE} flag.
 */
public final class BlessingLeader extends Effect {
    public BlessingLeader() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.GOLDEN_HELMET);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.LEADER_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.LEADER_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (target.getData(WitchModAttachments.LEADER_ACTIVE) < 0) {
            target.setData(WitchModAttachments.LEADER_ACTIVE, 1); // self-heal (relog)
        }
        double radius = Config.LEADER_RADIUS.get();
        double r2 = radius * radius;
        int regen = Config.LEADER_REGEN_AMP.get();
        int res = Config.LEADER_RESISTANCE_AMP.get();
        boolean fxTick = target.tickCount % 8 == 0;
        for (ServerPlayer ally : level.players()) {
            if (ally == target || !ally.isAlive() || ally.isSpectator()) {
                continue;
            }
            if (ally.distanceToSqr(target) <= r2) {
                buff(level, ally, regen, res, fxTick);
            }
        }
        // SYNERGY!!! gets nearby mobs in on this
        if (com.oliver.witchmod.synergy.Synergies.RALLYING_LEADER.activeFor(target)) {
            net.minecraft.world.phys.AABB box =
                    new net.minecraft.world.phys.AABB(target.blockPosition()).inflate(radius);
            for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(
                    net.minecraft.world.entity.Mob.class, box,
                    m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive())) {
                buff(level, mob, regen, res, fxTick);
            }
        }
    }

    private static void buff(ServerLevel level, net.minecraft.world.entity.LivingEntity entity,
                             int regen, int res, boolean fxTick) {
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, regen, true, false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, res, true, false, true));
        if (fxTick) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(),
                    entity.getY() + entity.getBbHeight() + 0.2, entity.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }
}
