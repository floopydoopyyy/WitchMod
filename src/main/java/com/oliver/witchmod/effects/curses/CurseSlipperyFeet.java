package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * A joke at the expense of one of Minecraft's most reflexive habits. Everyone
 * sneaks up to an edge to look over, trusting vanilla not to let them fall. This curse breaks that trust,
 * with a slide whistle.
 *
 * <p><b>Two triggers, and nothing else — this curse never shoves you at random.</b> That restraint is the
 * whole design: a push that could arrive anywhere would just be an annoyance tax on walking around, whereas
 * one that only ever happens at an edge makes edges themselves frightening.
 * <ul>
 *   <li><b>Standing</b> near a ledge or hazard: a very low roll every {@code CHECK_INTERVAL}. Background
 *       dread rather than a real threat.</li>
 *   <li><b>Crouching</b> over one: <b>guaranteed</b> after 1–2 seconds. The grace period is re-rolled every
 *       time you settle over an edge, so it can never be counted out and waited through.</li>
 * </ul>
 * If neither holds, nothing happens at all.
 *
 * <p>"Ledge" means a neighbouring column with a real drop under it; "hazard" covers lava, fire, magma, lit
 * campfires, cacti, powder snow, sweet berry bushes, wither roses and pointed dripstone — so being shoved
 * one block sideways into a fire pit counts just as much as being shoved off a cliff.
 *
 * <p>Discovery is on the first push, not on application: until your feet actually go, there is nothing to
 * notice.
 */
public final class CurseSlipperyFeet extends Effect {
    /** how long each victim has been crouched over an edge, and the grace period rolled for this stint. */
    private static final Map<UUID, Integer> CROUCH_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> CROUCH_LIMIT = new HashMap<>();

    public CurseSlipperyFeet() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.ICE);
    }

    /** you find out the first time your feet go out from under you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        clear(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        clear(target);
    }

    private static void clear(ServerPlayer target) {
        CROUCH_TICKS.remove(target.getUUID());
        CROUCH_LIMIT.remove(target.getUUID());
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        Vec3 look = target.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = new Vec3(1, 0, 0);
        }
        slip(target, target.position().add(dir.normalize().scale(2.0)));
        return "shoved your feet out from under you";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!target.onGround() || target.isPassenger()) {
            clear(target);
            return;
        }

        Vec3 edge = findEdge(target);
        if (edge == null) {
            clear(target);
            return;
        }

        if (target.isShiftKeyDown()) {
            crouchingOverEdge(target, edge);
            return;
        }
        clear(target);

        // merely standing near it: a long shot, rolled on an interval rather than every tick.
        if (target.tickCount % Config.SLIPPERY_CHECK_INTERVAL.get() != 0) {
            return;
        }
        if (target.getRandom().nextInt(100) < Config.SLIPPERY_STANDING_CHANCE.get()) {
            slip(target, edge);
        }
    }

    /** the guaranteed one. Vanilla is holding you back from the drop; this counts down until it doesn't. */
    private void crouchingOverEdge(ServerPlayer target, Vec3 edge) {
        UUID id = target.getUUID();
        int limit = CROUCH_LIMIT.computeIfAbsent(id, key -> {
            int min = Config.SLIPPERY_CROUCH_MIN_TICKS.get();
            int max = Math.max(min, Config.SLIPPERY_CROUCH_MAX_TICKS.get());
            return min + target.getRandom().nextInt(max - min + 1);
        });
        // slick_feet synergy (with Ice Skates): even more treacherous — the grace period is halved.
        if (com.oliver.witchmod.synergy.Synergies.SLICK_FEET.activeFor(target)) {
            limit = Math.max(1, limit / 2);
        }
        int elapsed = CROUCH_TICKS.merge(id, 1, Integer::sum);
        if (elapsed >= limit) {
            slip(target, edge);
            clear(target);
        }
    }

    /** whistle, shove, and a puff of ice crystals for the benefit of anyone watching. */
    private void slip(ServerPlayer target, Vec3 edge) {
        ServerLevel level = target.serverLevel();
        Vec3 away = edge.subtract(target.position());
        Vec3 push = new Vec3(away.x, 0.0, away.z);
        if (push.lengthSqr() < 1.0E-4) {
            return;
        }
        push = push.normalize().scale(Config.SLIPPERY_PUSH_FORCE.get());

        target.setDeltaMovement(target.getDeltaMovement()
.add(push.x, Config.SLIPPERY_PUSH_LIFT.get(), push.z));
        // without this the server never sends the velocity down and the victim doesn't budge on their screen.
        target.hurtMarked = true;
        target.hasImpulse = true;

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                WitchModSounds.SLIPPERY_SLIDE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getY() + 0.1, target.getZ(), 12, 0.25, 0.05, 0.25, 0.02);

        markDiscoveredByVictim(target);
    }

    /**
     * the nearest neighbouring column worth falling into — a real drop, or something nasty at foot level.
     * Returns its centre, or null if the player is standing somewhere perfectly safe.
     */
    @Nullable
    private static Vec3 findEdge(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        BlockPos origin = target.blockPosition();
        Vec3 from = target.position();

        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos column = origin.offset(dx, 0, dz);
                if (!isDrop(level, column) && !isHazard(level, column)) {
                    continue;
                }
                Vec3 centre = Vec3.atCenterOf(column);
                double distance = new Vec3(centre.x - from.x, 0.0, centre.z - from.z).lengthSqr();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = centre;
                }
            }
        }
        return best;
    }

    /** nothing solid for a few blocks under this column, and nothing solid to walk onto in it either. */
    private static boolean isDrop(ServerLevel level, BlockPos column) {
        if (!level.getBlockState(column).getCollisionShape(level, column).isEmpty()) {
            return false; // there's a block here at foot height — that's a step up, not a ledge
        }
        for (int dy = 1; dy <= Config.SLIPPERY_LEDGE_DROP_MIN.get(); dy++) {
            BlockPos below = column.below(dy);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** something you would very much rather not be shoved sideways into. */
    private static boolean isHazard(ServerLevel level, BlockPos column) {
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos pos = column.offset(0, dy, 0);
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                return true;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                    || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                    || state.is(Blocks.POINTED_DRIPSTONE)) {
                return true;
            }
            if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)) {
                return true;
            }
        }
        return false;
    }
}
