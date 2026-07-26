package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * Every creature that would normally leave you alone has decided today is not that day (master-spec Neutral
 * Aggression). Endermen, zombified piglins, wolves, iron golems, bees, polar bears — anything neutral within
 * {@code RADIUS} turns on you.
 *
 * <p><b>"Neutral" is vanilla's own {@link NeutralMob} interface, not a hardcoded list.</b> Same principle as
 * Allergic's food categories: modded neutral mobs are picked up automatically, and the boundary always
 * matches what the game itself considers neutral rather than a list that silently rots.
 *
 * <p><b>Persistent anger is the mechanism, not {@code setTarget}.</b> A bare {@code setTarget} is wiped
 * within a tick or two by the mob's own target-selection goals — {@code ResetUniversalAngerTargetGoal} and
 * friends — so the aggro visibly flickered and died. Setting the persistent anger target and starting the
 * anger timer is how vanilla itself makes these mobs hostile (it's what hitting an enderman does), so the
 * hatred sticks and decays naturally on vanilla's own schedule once the curse ends.
 *
 * <p>This curse is why Popularity deliberately EXCLUDES neutral mobs from its horde: force-aggroing
 * endermen and zombified piglins is this one's whole job, and doing it in both would make this redundant.
 */
public final class CurseNeutralAggression extends Effect {
    public CurseNeutralAggression() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.SPIDER_EYE);
    }

    /** You find out the first time something that had no quarrel with you comes for you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.NEUTRAL_AGGRO_CHECK_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(Config.NEUTRAL_AGGRO_RADIUS.get());
        boolean turned = false;

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, CurseNeutralAggression::isNeutral)) {
            if (!Config.NEUTRAL_AGGRO_TURNS_OWN_PETS.get()
                    && mob instanceof TamableAnimal tamable && tamable.isOwnedBy(target)) {
                continue;
            }
            if (mob instanceof NeutralMob neutral) {
                if (neutral.isAngryAt(target) && mob.getTarget() == target) {
                    continue; // already after you; leave its timer alone
                }
                neutral.setPersistentAngerTarget(target.getUUID());
                neutral.startPersistentAngerTimer();
            } else if (mob.getTarget() == target) {
                continue;
            }
            mob.setTarget(target);
            turned = true;
        }

        if (turned) {
            markDiscoveredByVictim(target);
        }
    }

    /**
     * What counts as neutral. Vanilla's {@link NeutralMob} interface is the rule — plus SPIDERS, which are
     * the one glaring omission from it.
     *
     * <p>A spider is a {@code Monster}, not a {@code NeutralMob}: its daytime passivity comes from
     * {@code SpiderTargetGoal.canUse()} refusing to acquire a target above light level 0.5, rather than from
     * any anger system. So by the interface it's hostile, but by behaviour it is exactly what a player means
     * by "neutral" — it leaves you alone in daylight. Caught in play: "spiders aren't affected at all and
     * just don't attack in day still". CaveSpider extends Spider, so it's covered too.
     *
     * <p>Spiders have no persistent anger to set, so the target is simply re-applied on every sweep — which
     * also survives their own goals clearing it.
     */
    private static boolean isNeutral(Mob mob) {
        return mob.isAlive() && (mob instanceof NeutralMob || mob instanceof Spider);
    }
}
