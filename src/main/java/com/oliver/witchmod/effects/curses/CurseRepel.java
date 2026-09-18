package com.oliver.witchmod.effects.curses;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * your stuff wants nothing to do with you. Dropped items and XP orbs on the floor near
 * you slide slowly AWAY — slow enough to chase down at a sprint — and steer themselves toward the nastiest
 * thing they can reach, but ONLY ever in a direction that still points away from you (they'll never come
 * toward you to reach a ledge).
 *
 * <p>Steering priority, highest first: <b>lit TNT &gt; lava &gt; cacti &gt; ledges &gt; other players &gt;
 * (just away)</b>. Each candidate is only taken if its direction from the item is within the away
 * half-space, so "away" is always respected. Discovered the first time anything actually slides.
 */
public final class CurseRepel extends Effect {
    public CurseRepel() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.WATER_BUCKET);
    }

    /** you find out the moment your things start crawling off (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        AABB area = target.getBoundingBox().inflate(Config.REPEL_RADIUS.get());
        int budget = Config.REPEL_MAX_ENTITIES.get();
        boolean movedAny = false;

        for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
                e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.onGround())) {
            if (budget-- <= 0) {
                break;
            }
            if (slideAway(level, target, entity)) {
                movedAny = true;
            }
        }
        if (movedAny) {
            markDiscoveredByVictim(target);
        }
    }

    /** sets the entity's horizontal velocity along its chosen away/hazard direction. */
    private static boolean slideAway(ServerLevel level, ServerPlayer target, Entity entity) {
        Vec3 away = entity.position().subtract(target.position());
        Vec3 awayFlat = new Vec3(away.x, 0.0, away.z);
        if (awayFlat.lengthSqr() < 1.0E-4) {
            // sitting right on the player — shove it off in a random horizontal direction.
            double a = target.getRandom().nextDouble() * Math.PI * 2.0;
            awayFlat = new Vec3(Math.cos(a), 0.0, Math.sin(a));
        }
        Vec3 awayDir = awayFlat.normalize();

        Vec3 dir = steerDirection(level, entity, awayDir);
        double speed = Config.REPEL_SPEED.get();
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
        entity.hasImpulse = true; // make sure the new velocity is synced to clients
        return true;
    }

    /**
     * picks the slide direction: the highest-priority hazard whose direction from the item still points away
     * from the victim, else straight away. Everything returned is a horizontal unit vector in the away
     * half-space.
     */
    private static Vec3 steerDirection(ServerLevel level, Entity entity, Vec3 awayDir) {
        double scan = Config.REPEL_HAZARD_SCAN_RADIUS.get();
        Vec3 pos = entity.position();

        Vec3 tnt = towardNearestEntity(level, entity, awayDir, PrimedTnt.class, scan);
        if (tnt != null) {
            return tnt;
        }
        Vec3 lava = towardNearestBlock(level, pos, awayDir, scan, true);
        if (lava != null) {
            return lava;
        }
        Vec3 cactus = towardNearestBlock(level, pos, awayDir, scan, false);
        if (cactus != null) {
            return cactus;
        }
        Vec3 ledge = towardLedge(level, entity.blockPosition(), awayDir);
        if (ledge != null) {
            return ledge;
        }
        Vec3 player = towardNearestEntity(level, entity, awayDir, Player.class, scan);
        if (player != null) {
            return player;
        }
        return awayDir;
    }

    /** nearest entity of a class whose direction from the item still points away; null if none qualify. */
    @Nullable
    private static Vec3 towardNearestEntity(ServerLevel level, Entity item, Vec3 awayDir,
                                            Class<? extends Entity> type, double radius) {
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntitiesOfClass(type, item.getBoundingBox().inflate(radius), e -> e != item && e.isAlive())) {
            Vec3 toward = flatDirTo(item.position(), e.position());
            if (toward == null || toward.dot(awayDir) <= 0.0) {
                continue; // would drag the item back toward the player — not allowed
            }
            double d = e.distanceToSqr(item);
            if (d < bestDist) {
                bestDist = d;
                best = toward;
            }
        }
        return best;
    }

    /** nearest lava (or cactus) block whose direction from the item still points away; coarse grid scan. */
    @Nullable
    private static Vec3 towardNearestBlock(ServerLevel level, Vec3 itemPos, Vec3 awayDir, double radius, boolean lava) {
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(itemPos);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    boolean match = lava ? level.getFluidState(p).is(FluidTags.LAVA)
                            : level.getBlockState(p).is(Blocks.CACTUS);
                    if (!match) {
                        continue;
                    }
                    Vec3 toward = flatDirTo(itemPos, Vec3.atCenterOf(p));
                    if (toward == null || toward.dot(awayDir) <= 0.0) {
                        continue;
                    }
                    double d = p.distToCenterSqr(itemPos.x, itemPos.y, itemPos.z);
                    if (d < bestDist) {
                        bestDist = d;
                        best = toward;
                    }
                }
            }
        }
        return best;
    }

    /** A neighbouring compass direction (in the away half-space) that drops off a ledge, most-aligned first. */
    @Nullable
    private static Vec3 towardLedge(ServerLevel level, BlockPos itemPos, Vec3 awayDir) {
        Vec3 best = null;
        double bestAlign = 0.0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 dir = new Vec3(d.getStepX(), 0.0, d.getStepZ());
            double align = dir.dot(awayDir);
            if (align <= 0.0) {
                continue; // that way is back toward the player
            }
            if (isDrop(level, itemPos.relative(d)) && align > bestAlign) {
                bestAlign = align;
                best = dir;
            }
        }
        return best;
    }

    /** true if the column at {@code pos} has {@code repelLedgeDropMin} clear blocks straight down. */
    private static boolean isDrop(ServerLevel level, BlockPos pos) {
        for (int depth = 0; depth <= Config.REPEL_LEDGE_DROP_MIN.get(); depth++) {
            if (!level.getBlockState(pos.below(depth)).getCollisionShape(level, pos.below(depth)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** horizontal unit vector from {@code from} to {@code to}, or null if they're on the same column. */
    @Nullable
    private static Vec3 flatDirTo(Vec3 from, Vec3 to) {
        Vec3 flat = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        return flat.lengthSqr() < 1.0E-4 ? null : flat.normalize();
    }
}
