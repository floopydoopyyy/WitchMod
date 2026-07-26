package com.oliver.witchmod.effects.goals;

import java.util.EnumSet;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;

/**
 * The Broken Bonds curse's "I'm done with you" AI. When a mob's hate meter maxes out it untames and this
 * takes its legs for a while, walking it a good distance away from where it snapped.
 *
 * <p>Unlike the Unhygienic flee goal this is <b>time-limited</b> and flees a REMEMBERED point rather than a
 * live player: the mob is no longer owned by anyone once it's untamed, so it isn't reacting to the ex-owner
 * so much as storming off from the spot the betrayal happened. Once the timer runs out it goes dormant and
 * the mob is just an ordinary wild animal again.
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
