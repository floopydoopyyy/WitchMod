package com.oliver.witchmod.effects.goals;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.effects.Curses;

/**
 * unhygienic's "get away from that smell" ai — a non-undead mob inside the stink radius paths away, re-picking
 * its route as the victim follows. bound to a specific player and checked with {@link #isTargeting} (the
 * ServerPlayer is replaced on respawn/relog and a stale reference would leave the mob unbothered).
 */
public final class UnhygienicFleeGoal extends Goal {
    private static final int REPATH_INTERVAL = 10;
    /** how far ahead to aim the escape route. */
    private static final double FLEE_DISTANCE = 10.0;

    private final PathfinderMob mob;
    private final ServerPlayer target;
    private int repathCooldown;

    public UnhygienicFleeGoal(PathfinderMob mob, ServerPlayer target) {
        this.mob = mob;
        this.target = target;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** @see com.oliver.witchmod.effects.goals.FarmhandBlockGoal#isTargeting */
    public boolean isTargeting(ServerPlayer player) {
        return this.target == player;
    }

    @Override
    public boolean canUse() {
        if (target.isRemoved() || !target.isAlive() || target.level() != mob.level()) {
            return false;
        }
        if (!EffectManager.isActive(target, Curses.UNHYGIENIC)) {
            return false; // curse ended — the air clears
        }
        double radius = Config.UNHYGIENIC_MOB_FLEE_RADIUS.get();
        return mob.distanceToSqr(target) <= radius * radius;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--repathCooldown > 0) {
            return;
        }
        repathCooldown = REPATH_INTERVAL;

        Vec3 away = mob.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        if (flat.lengthSqr() < 1.0E-4) {
            // standing right on top of them — bolt in any direction.
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
            flat = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }
        Vec3 escape = mob.position().add(flat.normalize().scale(FLEE_DISTANCE));
        // close enough to really smell it? Break into a run rather than an unhurried walk away.
        double panic = Config.UNHYGIENIC_PANIC_RADIUS.get();
        double speed = mob.distanceToSqr(target) <= panic * panic
                ? Config.UNHYGIENIC_PANIC_SPEED.get()
                : Config.UNHYGIENIC_FLEE_SPEED.get();
        mob.getNavigation().moveTo(escape.x, escape.y, escape.z, speed);
    }
}
