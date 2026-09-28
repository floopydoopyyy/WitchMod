package com.oliver.witchmod.blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.gameevent.GameEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * cauldron behaviour for holy water — the acquisition route: an amethyst shard in a water cauldron has a
 * stacking chance to consecrate it, and holy-water buckets fill/empty a cauldron freely. interactions are
 * registered from common setup ({@link #registerInteractions()}).
 */
public final class HolyWaterCauldron {
    /**
     * Passed to the cauldron block at registration; populated in {@link #registerInteractions()}.
     * ⚠ MUST use {@code newInteractionMap} (an Object2ObjectOpenHashMap with a {@code DEFAULT} default-return),
     * NOT a plain HashMap — vanilla {@code AbstractCauldronBlock.useItemOn} calls {@code .interact()} on the
     * looked-up value WITHOUT a null check, so an unregistered item (e.g. an amethyst shard used on a HOLY
     * cauldron) returning {@code null} from a plain map crashed the game. DEFAULT just passes through.
     */
    public static final CauldronInteraction.InteractionMap INTERACTIONS =
            CauldronInteraction.newInteractionMap("witchmod:purifying_water");

    /** per-cauldron accumulated shard count (transient — the stacking-chance memory). */
    private static final Map<BlockPos, Integer> SHARDS = new ConcurrentHashMap<>();

    private HolyWaterCauldron() {}

    public static void registerInteractions() {
        // full holy cauldron + empty bucket → holy-water bucket, cauldron empties.
        INTERACTIONS.map().put(Items.BUCKET, (state, level, pos, player, hand, stack) -> {
            if (state.getValue(LayeredCauldronBlock.LEVEL) != 3) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player,
                        new ItemStack(WitchModFluids.PURIFYING_WATER_BUCKET.get())));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
                level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });

        // empty cauldron + holy-water bucket → full holy cauldron.
        CauldronInteraction.EMPTY.map().put(WitchModFluids.PURIFYING_WATER_BUCKET.get(), (state, level, pos, player, hand, stack) -> {
            if (!level.isClientSide) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                player.awardStat(Stats.FILL_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(WitchModFluids.PURIFYING_WATER_BUCKET.get()));
                level.setBlockAndUpdate(pos, WitchModFluids.PURIFYING_WATER_CAULDRON.get().defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL, 3));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });

        // WATER cauldron + amethyst shard → stacking chance to consecrate into holy water.
        CauldronInteraction.WATER.map().put(Items.AMETHYST_SHARD, (state, level, pos, player, hand, stack) -> {
            if (!level.isClientSide) {
                int lvl = state.getValue(LayeredCauldronBlock.LEVEL);
                int shards = SHARDS.merge(pos.immutable(), 1, Integer::sum);
                stack.consume(1, player);
                float chance = shards * (float) (double) Config.PURIFY_SHARD_CHANCE.get();
                if (level.random.nextFloat() < chance) {
                    SHARDS.remove(pos.immutable());
                    level.setBlockAndUpdate(pos, WitchModFluids.PURIFYING_WATER_CAULDRON.get().defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, lvl));
                    // becomes holy — a sped-up, quiet blessing chime + a burst of shine.
                    level.playSound(null, pos, WitchModSounds.BLESSED.get(), SoundSource.BLOCKS, 0.4F, 1.7F);
                    if (level instanceof ServerLevel sl) {
                        double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
                        sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 26, 0.24, 0.3, 0.24, 0.02);
                        sl.sendParticles(ParticleTypes.WITCH, x, y + 0.1, z, 14, 0.24, 0.3, 0.24, 0.0);
                        sl.sendParticles(ParticleTypes.GLOW, x, y, z, 8, 0.2, 0.25, 0.2, 0.0);
                    }
                } else {
                    // not yet — a small amethyst tick so you know the shard did something.
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.5F, 1.2F);
                    if (level instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5,
                                4, 0.2, 0.1, 0.2, 0.0);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });
    }
}
