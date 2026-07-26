package com.oliver.witchmod.effects.goals;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.effects.Curses;

/**
 * The Farmhand curse's "get in the way" AI: the animal makes it its whole job to stand exactly where the
 * cursed player is working — on the block under their crosshair, so placement/mining is physically blocked —
 * or, for part of the herd, in a tight ring pressing in around them.
 *
 * <p><b>Re-pathing is deliberately conservative.</b> The crosshair target moves constantly as the player
 * looks around, and calling {@code moveTo} every few ticks forces a full path recalculation each time, which
 * leaves the animal stuttering on the spot instead of actually travelling. A new path is only requested when
 * the destination has really moved, the current path is finished, or a slow refresh timer elapses — and only
 * once the path is done does it amble the last step via its MoveControl. Driving the MoveControl every tick
 * as well made the herd stampede rather than wander into the way, so that is deliberately not done.
 */
public final class FarmhandBlockGoal extends Goal {
    /** Slow safety refresh; real re-paths are driven by the destination actually moving. */
    private static final int PATH_REFRESH_TICKS = 20;
    /** How far the destination must move before it's worth recomputing a path (blocks, squared). */
    private static final double REPATH_DISTANCE_SQR = 2.25; // 1.5 blocks
    private static final double CROSSHAIR_REACH = 5.0;
    private static final double GOLDEN_ANGLE = 2.399963; // spreads surround slots evenly around the player
    private static final ResourceLocation SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "farmhand_speed");

    private final Mob mob;
    private final ServerPlayer target;
    private Vec3 pathedTo;
    private int refreshCooldown;

    public FarmhandBlockGoal(Mob mob, ServerPlayer target) {
        this.mob = mob;
        this.target = target;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Whether this goal is still bound to the given player. The victim's {@code ServerPlayer} object is
     * REPLACED on respawn (and on relog), which would otherwise leave every recruited animal holding a dead
     * reference and quietly ignoring the curse forever — the "it was already applied but did nothing until I
     * re-applied it" bug. The recruiter uses this to swap a stale goal for a fresh one.
     */
    public boolean isTargeting(ServerPlayer player) {
        return this.target == player;
    }

    @Override
    public boolean canUse() {
        if (target.isRemoved() || !target.isAlive() || target.level() != mob.level()) {
            return false;
        }
        if (!EffectManager.isActive(target, Curses.FARMHAND)) {
            return false; // curse ended — lose interest
        }
        double leash = Config.FARMHAND_RADIUS.get() + 8.0;
        return mob.distanceToSqr(target) <= leash * leash;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        refreshCooldown = 0;
        pathedTo = null;
        // A real speed boost, not just a pathfinding multiplier — a cow at base speed simply cannot keep a
        // player hemmed in. Tied to the goal, so stop() hands it straight back when the curse ends.
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SPEED_MODIFIER_ID)) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
                    Config.FARMHAND_SPEED_BONUS.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        pathedTo = null;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        Vec3 goal = chooseDestination();
        double speed = Config.FARMHAND_NAV_SPEED.get();
        PathNavigation nav = mob.getNavigation();

        boolean destinationMoved = pathedTo == null || pathedTo.distanceToSqr(goal) > REPATH_DISTANCE_SQR;
        boolean pathFinished = nav.isDone();
        if (destinationMoved || pathFinished || --refreshCooldown <= 0) {
            refreshCooldown = PATH_REFRESH_TICKS;
            pathedTo = goal;
            nav.moveTo(goal.x, goal.y, goal.z, speed);
        }

        // Path exhausted (arrived, or nowhere to path) — amble the last bit under our own steam. Deliberately
        // NOT applied while a path is running: driving them every tick made them shove like a stampede
        // instead of wandering into the way.
        if (nav.isDone()) {
            mob.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
        }
    }

    /**
     * MOST of the herd packs into a dense crowd hugging the player (concentric rings a few blocks out), and
     * only 1 in 4 peels off to harass the exact block under the crosshair.
     *
     * <p>This ratio used to be the other way round, and that was the whole problem: the crosshair sits up to
     * {@link #CROSSHAIR_REACH} blocks away and moves whenever the player looks around, so sending the
     * majority there left the herd milling about several blocks off in whatever direction the player glanced
     * — busy, but never actually underfoot. Crowding the player is what you feel.
     */
    private Vec3 chooseDestination() {
        if ((mob.getId() % 4) == 0) {
            Vec3 aim = crosshairTarget();
            if (aim != null) {
                return aim;
            }
        }
        // Concentric rings so a big herd nests around the player instead of fighting for one spot.
        double angle = mob.getId() * GOLDEN_ANGLE;
        double ring = Config.FARMHAND_SURROUND_RADIUS.get() + (mob.getId() % 3) * 0.5;
        return target.position().add(Math.cos(angle) * ring, 0.0, Math.sin(angle) * ring);
    }

    /** The block the player is looking at (server-side raycast), aimed at the space just on top of it. */
    private Vec3 crosshairTarget() {
        Vec3 eye = target.getEyePosition();
        Vec3 end = eye.add(target.getViewVector(1.0F).scale(CROSSHAIR_REACH));
        BlockHitResult hit = target.level().clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, target));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null; // looking at open sky — nothing to stand on
        }
        BlockPos p = hit.getBlockPos();
        return Vec3.atBottomCenterOf(p.above()); // stand where the next block would go
    }

}
