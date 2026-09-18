package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * the world leaves you alone — the exact opposite of Popularity.
 * Two halves:
 * <ul>
 *   <li><b>Far fewer spawns</b> — most hostile natural spawns near you are quietly cancelled
 *       ({@code peaceSpawnRateMultiplier}). That lives in {@code BlessingEventHandler} on
 *       {@code FinalizeSpawnEvent}, the one place a spawn can be vetoed.</li>
 *   <li><b>Shrunken detection</b> — a hostile only notices you at {@code peaceDetectionMultiplier} of its
 *       normal follow range, so things you'd normally have aggroed just... don't. Enforced here each sweep by
 *       dropping any aggro held from beyond that reduced range.</li>
 * </ul>
 *
 * <p>You <b>discover</b> it the first time you're stood inside a hostile's normal aggro range — close enough
 * that it should have turned on you — while it hasn't, because the reduction is keeping it calm.
 */
public final class BlessingPeace extends Effect {
    public BlessingPeace() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.POPPY);
    }

    /** discovered when a mob that ought to be hunting you conspicuously isn't. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.PEACE_CHECK_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        double radius = Config.PEACE_RADIUS.get();
        double detectionMult = Config.PEACE_DETECTION_MULT.get();
        AABB area = target.getBoundingBox().inflate(radius);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                m -> m.getType().getCategory() == MobCategory.MONSTER)) {
            double normalRange = mob.getAttribute(Attributes.FOLLOW_RANGE) != null
                    ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
            double reducedRange = normalRange * detectionMult;
            double dist = Math.sqrt(mob.distanceToSqr(target));

            // enforce the shrunken detection: any aggro on you from beyond the reduced range is dropped.
            if (mob.getTarget() == target && dist > reducedRange) {
                mob.setTarget(null);
            }

            // discovery: a hostile within its NORMAL aggro range, in view, that still isn't hunting you.
            if (dist <= normalRange && dist > reducedRange
                    && mob.getTarget() != target && mob.hasLineOfSight(target)) {
                markDiscoveredByVictim(target);
            }
        }
    }
}
