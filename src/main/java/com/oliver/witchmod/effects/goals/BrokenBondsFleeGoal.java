package com.oliver.witchmod.effects.goals;

import java.util.EnumSet;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;

/**
 * broken bonds' "i'm done with you" ai — when a mob's hate meter maxes it untames and storms a good distance
 * from where it snapped. time-limited and flees a remembered point (not a live player, since it's now
 * ownerless); goes dormant when the timer runs out.
 */
public final class BrokenBondsFleeGoal extends Goal {
    private static final int REPATH_INTERVAL = 10;

    private final PathfinderMob mob;
    private final Vec3 from;
    private int ticksLeft;
    private int repathCooldown;

    public BrokenBondsFleeGoal(PathfinderMob mob, Vec3 from, int durationTicks) {
        this.mob = mob;
        this.from = from;
        this.ticksLeft = durationTicks;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return ticksLeft > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return ticksLeft > 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticksLeft--;
        if (--repathCooldown > 0) {
            return;
        }
        repathCooldown = REPATH_INTERVAL;

        Vec3 away = mob.position().subtract(from);
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0E-4) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
            flat = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }
        Vec3 escape = mob.position().add(flat.normalize().scale(Config.BROKEN_BONDS_FLEE_DISTANCE.get()));
        mob.getNavigation().moveTo(escape.x, escape.y, escape.z, Config.BROKEN_BONDS_FLEE_SPEED.get());
    }
}
