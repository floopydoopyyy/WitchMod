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
 * You've got backup (master-spec Army, sacrificial item SHIELD). Nearby hostile mobs treat you as one of
 * their own — they won't attack you — and the instant something DOES hit you, every hostile in range turns on
 * whatever landed the blow and swarms it. Attack in the wrong neighbourhood and the whole area comes down on
 * you.
 *
 * <p>Two halves:
 * <ul>
 *   <li><b>Neutralised</b> — a nearby hostile simply <i>cannot acquire you</i>: the
 *       {@code LivingChangeTargetEvent} handler in {@code BlessingEventHandler} vetoes any attempt to set you
 *       as its target, so its own goals can't lock on. (Clearing the target after the fact instead — the first
 *       approach — flickered against those goals and let hits through, and the rally then turned it to chaos.)
 *       As backstops, damage from a pacified hostile is cancelled outright, and this sweep clears any target
 *       one was already holding when the blessing landed.</li>
 *   <li><b>Rally</b> — being hit by a GENUINE aggressor (a player, a golem — anything that isn't a pacified
 *       hostile) records it via {@link #markDefend} (from {@code BlessingEventHandler}); for
 *       {@code armyDefendDurationTicks} afterward the sweep re-aims every nearby hostile at it instead.
 *       Optionally the swarm won't turn on the attacker's OWN kind ({@code armySameTypeExcluded}).</li>
 * </ul>
 *
 * <p><b>Neutral mobs are untouched</b> — this only moves {@code MobCategory.MONSTER} mobs that aren't
 * {@link NeutralMob}s, so an enderman or zombified piglin behaves exactly as it always would. You
 * <b>discover</b> it when a hostile that should have turned on you conspicuously hasn't.
 */
public final class BlessingArmy extends Effect {
    /** blessed player -> entity id of whatever last hit them (the swarm's current quarry). */
    private static final Map<UUID, Integer> DEFEND_TARGET = new HashMap<>();
    /** blessed player -> game tick the rally expires. */
    private static final Map<UUID, Long> DEFEND_UNTIL = new HashMap<>();

    public BlessingArmy() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.SHIELD);
    }

    /** Discovered when a hostile that ought to be attacking you isn't, because it's been pacified. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Called from the damage hook: rally the horde onto {@code attacker} for the defend window. */
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
                // Rally: swarm whatever hit the blessed player (re-aimed each sweep so it sticks).
                mob.setTarget(livingQuarry);
            } else if (mob.getTarget() == target) {
                // Neutralised: keep them off you.
                mob.setTarget(null);
            }

            // Discovery: a hostile in its normal aggro range, in view, that isn't hunting you.
            double normalRange = mob.getAttribute(Attributes.FOLLOW_RANGE) != null
                    ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
            if (mob.getTarget() != target
                    && Math.sqrt(mob.distanceToSqr(target)) <= normalRange
                    && mob.hasLineOfSight(target)) {
                markDiscoveredByVictim(target);
            }
        }
    }

    /** The living entity the horde should currently be swarming, or null if the rally has lapsed. */
    private static Entity currentQuarry(ServerLevel level, ServerPlayer defender) {
        Long until = DEFEND_UNTIL.get(defender.getUUID());
        if (until == null || level.getGameTime() > until) {
            return null;
        }
        Integer id = DEFEND_TARGET.get(defender.getUUID());
        return id == null ? null : level.getEntity(id);
    }

    /** Hostile MONSTER mobs that aren't neutral — the ones Army actually commands (and pacifies). */
    public static boolean isConscriptable(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER && !(mob instanceof NeutralMob);
    }
}
