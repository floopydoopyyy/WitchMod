package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * nearby hostile mobs do not attack you and all aggro on sattackers nearby
 *
 * Two halves:
 *  — a nearby hostile simply cannot acquire you<: the
 * {@code LivingChangeTargetEvent} handler in {@code BlessingEventHandler} vetoes any attempt to set you
 *  as its target, so its own goals can't lock on. (Clearing the target after the fact instead — the first
 *  approach — flickered against those goals and let hits through, and the rally then turned it bad)
 *  As backstops, damage from a pacified hostile is cancelled outright, and this sweep clears any target
 *  one was already holding when the blessing landed.
 *  - being hit by a GENUINE aggressor (anything that isn't a pacified hostile)
 *  records it via {@link #markDefend} (from {@code BlessingEventHandler}); for
 *  {@code armyDefendDurationTicks} afterward the sweep re-aims every nearby hostile at it instead.
 *
 * intentionally does not touch neutrals cus neutral aggression curse.
 */
public final class BlessingArmy extends Effect {
    /** blessed player -> entity id of whatever last hit them. */
    private static final Map<UUID, Integer> DEFEND_TARGET = new HashMap<>();
    /** blessed player -> game tick the rally expires. */
    private static final Map<UUID, Long> DEFEND_UNTIL = new HashMap<>();

    public BlessingArmy() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.SHIELD);
    }

    /** discovered when a hostile that ought to be attacking you isn't, because it's been pacified. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** called from the damage hook: rally the horde onto {@code attacker}. */
    public static void markDefend(ServerPlayer defender, Entity attacker) {
        DEFEND_TARGET.put(defender.getUUID(), attacker.getId());
        DEFEND_UNTIL.put(defender.getUUID(),
                defender.serverLevel().getGameTime() + Config.ARMY_DEFEND_DURATION.get());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        DEFEND_TARGET.remove(target.getUUID());
        DEFEND_UNTIL.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.ARMY_CHECK_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        double radius = Config.ARMY_RADIUS.get();
        AABB area = target.getBoundingBox().inflate(radius);

        Entity quarry = currentQuarry(level, target);

        boolean sameTypeExcluded = Config.ARMY_SAME_TYPE_EXCLUDED.get();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, BlessingArmy::isConscriptable)) {
            if (quarry instanceof LivingEntity livingQuarry && quarry.isAlive() && quarry != mob
                    && !(sameTypeExcluded && mob.getType() == quarry.getType())) {
                // swarm whatever hit the blessed player (re-aimed each sweep so it sticks).
                mob.setTarget(livingQuarry);
            } else if (mob.getTarget() == target) {
                // keep them off me.!
                mob.setTarget(null);
            }

            // discovery: a hostile in its normal aggro range, in view
            double normalRange = mob.getAttribute(Attributes.FOLLOW_RANGE) != null
                    ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
            if (mob.getTarget() != target
                    && Math.sqrt(mob.distanceToSqr(target)) <= normalRange
                    && mob.hasLineOfSight(target)) {
                markDiscoveredByVictim(target);
            }
        }
    }

    //the living entity the horde should currently be swarming, or null if the rally has ended
    private static Entity currentQuarry(ServerLevel level, ServerPlayer defender) {
        Long until = DEFEND_UNTIL.get(defender.getUUID());
        if (until == null || level.getGameTime() > until) {
            return null;
        }
        Integer id = DEFEND_TARGET.get(defender.getUUID());
        return id == null ? null : level.getEntity(id);
    }

    // hostile MONSTER mobs that aren't neutral — the ones Army actually commands. funny names
    public static boolean isConscriptable(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER && !(mob instanceof NeutralMob);
    }
}
