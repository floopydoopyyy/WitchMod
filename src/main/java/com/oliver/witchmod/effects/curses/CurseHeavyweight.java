package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the floor can't take you. Stand on anything with air beneath it — a second
 * storey, a bridge, a ledge — and it starts to give way under your weight.
 *
 * <p><b>Break time scales with the block's own hardness</b>: leaves ~0.8s, dirt ~1.1s, stone ~2.4s, planks
 * ~3.0s, iron block ~6.8s, obsidian ~60s. Obsidian stays a real deterrent without being literally
 * impossible. Unbreakable blocks (bedrock, barriers — hardness below zero) are skipped entirely, so nothing
 * can chew through the world bottom.
 *
 * <p><b>The warning is primarily VISUAL, and the collapse is loud.</b> That split is deliberate. Dust pours
 * out of the underside the whole way through, thickening as it goes, with fragments spitting off the top
 * once it's close — while the audio stays sparse and quiet, because a constant creak was both grating and
 * easy to tune out. Then the collapse itself is the noise: the zombie door-smash (the most "something just
 * gave way" sound vanilla has), a burst of debris, a camera shake, and a ragged hole taken out of the
 * surrounding floor. One block quietly disappearing didn't read as a collapse at all.
 *
 * <p>Progress is tied to one block position and resets the moment you step off it, so walking is safe and
 * only loitering is punished. World damage is gated on {@code mobGriefing}, matching every other
 * block-destroying effect in the mod.
 */
public final class CurseHeavyweight extends Effect {
    private record Progress(BlockPos pos, int ticks) {}

    private static final Map<UUID, Progress> BREAKING = new HashMap<>();

    public CurseHeavyweight() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.IRON_BLOCK);
    }

    /** you find out the first time the floor gives out (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        BREAKING.remove(target.getUUID());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        clear(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            clear(target);
            return;
        }

        BlockPos standing = candidateBelow(target);
        if (standing == null) {
            clear(target);
            return;
        }

        Progress current = BREAKING.get(target.getUUID());
        if (current == null || !current.pos().equals(standing)) {
            // stepped onto a different block — the old one recovers completely.
            clear(target);
            current = new Progress(standing, 0);
        }

        int ticks = current.ticks() + 1;
        BREAKING.put(target.getUUID(), new Progress(standing, ticks));

        BlockState state = level.getBlockState(standing);
        int needed = breakTicks(level, state, standing);
        if (ticks >= needed) {
            collapse(target, level, standing, state);
            return;
        }
        showProgress(target, level, standing, state, ticks / (float) needed);
    }

    /**
     * the block underfoot, but only if there's air beneath it — a floor with nothing holding it up. Returns
     * null if it isn't a candidate, including for anything unbreakable.
     */
    @Nullable
    private static BlockPos candidateBelow(ServerPlayer target) {
        if (!target.onGround() || target.isPassenger()) {
            return null;
        }
        ServerLevel level = target.serverLevel();
        BlockPos below = target.blockPosition().below();
        BlockState state = level.getBlockState(below);
        if (state.isAir() || state.getCollisionShape(level, below).isEmpty()) {
            return null;
        }
        if (state.getDestroySpeed(level, below) < 0.0F) {
            return null; // bedrock and friends — never chew through the world bottom
        }
        BlockPos under = below.below();
        return level.getBlockState(under).getCollisionShape(level, under).isEmpty() ? below : null;
    }

    private static int breakTicks(ServerLevel level, BlockState state, BlockPos pos) {
        float hardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        return Math.max(1, Config.HEAVYWEIGHT_BASE_BREAK_TICKS.get()
                + (int) (hardness * Config.HEAVYWEIGHT_HARDNESS_MULT.get()));
    }

    /**
     * the warning. <b>Primarily VISUAL</b> — the crack overlay plus dust that thickens as it goes, with the
     * audio kept sparse and quiet. An early version creaked constantly and loudly, which was both grating and
     * easy to tune out; particles falling harder and harder out of the block you're standing on read far
     * better and don't fight everything else the game is doing.
     */
    private static void showProgress(ServerPlayer target, ServerLevel level, BlockPos pos,
                                     BlockState state, float fraction) {
        int stage = Math.min(9, (int) (fraction * 10.0F));
        level.destroyBlockProgress(target.getId(), pos, stage);

        // dust from the underside, scaling the whole way through rather than only after the warn point, so
        // the block is visibly failing long before it makes any noise about it.
        int dust = 1 + (int) (fraction * 7.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5,
                dust, 0.3, 0.02, 0.3, 0.0);
        if (fraction > 0.6F) {
            // fragments spitting off the top edge too, once it's genuinely close.
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5,
                    2 + (int) (fraction * 4.0F), 0.35, 0.05, 0.35, 0.04);
        }

        if (fraction < Config.HEAVYWEIGHT_WARN_FRACTION.get()) {
            return;
        }
        // sparse and quiet: an occasional creak that speeds up slightly, not a constant grinding.
        int interval = Math.max(8, (int) (26 - fraction * 14.0F));
        if (target.tickCount % interval != 0) {
            return;
        }
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                state.getSoundType(level, pos, target).getHitSound(), SoundSource.BLOCKS,
                0.35F, 0.55F + fraction * 0.3F);
    }

    /**
     * the floor going. Deliberately loud and messy: the zombie door-smash sound (the most "something just
     * gave way" noise vanilla has), a burst of debris, a camera shake, and a ragged hole rather than one neat
     * missing block — a single block quietly disappearing didn't read as a collapse at all.
     */
    private void collapse(ServerPlayer target, ServerLevel level, BlockPos pos, BlockState state) {
        level.destroyBlockProgress(target.getId(), pos, -1); // clear the overlay before the block goes
        BREAKING.remove(target.getUUID());

        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.4F, 0.7F);
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                state.getSoundType(level, pos, target).getBreakSound(), SoundSource.BLOCKS, 1.2F, 0.6F);
        debris(level, pos, state);

        level.destroyBlock(pos, true, target); // drops, and credits the victim so it's their doing
        spreadCollapse(target, level, pos);

        target.setData(WitchModAttachments.HEAVYWEIGHT_SHAKE_END,
                level.getGameTime() + Config.HEAVYWEIGHT_SHAKE_TICKS.get());
        com.oliver.witchmod.effects.Curses.DENSE.value().markDiscoveredByVictim(target);
    }

    /** takes a ragged bite out of the surrounding floor, so it reads as a collapse and not a trapdoor. */
    private static void spreadCollapse(ServerPlayer target, ServerLevel level, BlockPos centre) {
        int radius = Config.HEAVYWEIGHT_COLLAPSE_RADIUS.get();
        int chance = Config.HEAVYWEIGHT_COLLAPSE_CHANCE.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx == 0 && dz == 0) || target.getRandom().nextInt(100) >= chance) {
                    continue;
                }
                BlockPos neighbour = centre.offset(dx, 0, dz);
                BlockState state = level.getBlockState(neighbour);
                if (state.isAir() || state.getDestroySpeed(level, neighbour) < 0.0F) {
                    continue; // never take unbreakable blocks with it
                }
                BlockPos under = neighbour.below();
                if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
                    continue; // only floor that was already unsupported goes
                }
                debris(level, neighbour, state);
                level.destroyBlock(neighbour, true, target);
            }
        }
    }

    private static void debris(ServerLevel level, BlockPos pos, BlockState state) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                60, 0.45, 0.45, 0.45, 0.25);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                12, 0.4, 0.2, 0.4, 0.03);
    }

    private static void clear(Player target) {
        Progress previous = BREAKING.remove(target.getUUID());
        if (previous != null && target.level() instanceof ServerLevel level) {
            // anything outside 0..9 removes it. Left set, the cracks would stick on that block for good.
            level.destroyBlockProgress(target.getId(), previous.pos(), -1);
        }
    }
}
