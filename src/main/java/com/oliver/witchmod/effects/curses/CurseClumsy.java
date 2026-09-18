package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Curses;

/**
 * you keep getting it slightly wrong. Every so often a block you place comes out facing
 * the wrong way, lands one over from where you aimed, or turns out to be a different block off your hotbar
 * entirely.
 *
 * <p><b>The chance RAMPS.</b> It starts at {@code BASE} (1%) and climbs by {@code PER_BLOCK} (1.5%) with
 * every block you place cleanly, up to {@code MAX} (31%) — then resets the instant something goes wrong. So a
 * long uninterrupted run of building gets steadily more precarious rather than being a flat tax on every
 * block.
 *
 * <p><b>What kind of mistake depends on the block.</b> Something with an orientation — stairs, a door, a log,
 * a furnace — is most likely to come out FACING wrong (that's the mistake that reads as clumsy for those),
 * then misplaced, then swapped. A plain cube can only really go to the wrong spot or be the wrong block.
 *
 * <p>Runs off {@code BlockEvent.EntityPlaceEvent} in {@code CurseEventHandler}: vanilla places the block and
 * handles all the item accounting, then this mutates the RESULT. That's deliberately simpler and safer than
 * intercepting the placement — the wrong-block case only has to move a single item across, and there is no
 * way for it to duplicate or void anything.
 */
public final class CurseClumsy extends Effect {
    /** victim -> blocks placed cleanly since the last slip, which drives the ramp. */
    private static final Map<UUID, Integer> CLEAN_RUN = new HashMap<>();

    /** properties whose presence means the block has an orientation worth getting wrong. */
    private static final Property<?>[] ORIENTATION_PROPS = {
            BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.FACING,
            BlockStateProperties.AXIS,
            BlockStateProperties.ROTATION_16,
    };

    private enum Slip { ORIENTATION, LOCATION, WRONG_BLOCK }

    public CurseClumsy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.EGG);
    }

    /** you find out the first time a block goes down wrong (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        CLEAN_RUN.remove(target.getUUID());
    }

    /**
     * hook for placing a block — see {@code CurseEventHandler}. The block is already in the world at
     * {@code pos} with {@code placedState}; if the ramping roll fires, this makes it go wrong.
     */
    public static void onBlockPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState placedState) {
        UUID id = player.getUUID();
        RandomSource rng = player.getRandom();

        int clean = CLEAN_RUN.getOrDefault(id, 0);
        double chance = Math.min(Config.CLUMSY_MAX_CHANCE.get(),
                Config.CLUMSY_BASE_CHANCE.get() + Config.CLUMSY_PER_BLOCK_CHANCE.get() * clean);
        if (rng.nextDouble() * 100.0 >= chance) {
            CLEAN_RUN.put(id, clean + 1); // placed cleanly — the odds tick up for next time
            return;
        }
        CLEAN_RUN.put(id, 0); // a slip resets the ramp

        boolean oriental = isOriented(placedState);
        Slip slip = chooseSlip(rng, oriental);
        boolean handled = switch (slip) {
            case ORIENTATION -> wrongOrientation(level, pos, placedState, rng);
            case LOCATION -> wrongLocation(level, pos, placedState, rng);
            case WRONG_BLOCK -> wrongBlock(player, level, pos, placedState, rng);
        };
        // if the chosen slip couldn't apply (nothing else in the hotbar, nowhere to misplace it), fall back
        // to something that always can, so a triggered slip is never silently wasted.
        if (!handled && slip != Slip.LOCATION) {
            handled = wrongLocation(level, pos, placedState, rng);
        }
        if (handled) {
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.5F, 0.8F);
            Curses.CLUMSY.value().markDiscoveredByVictim(player);
        }
    }

    private static Slip chooseSlip(RandomSource rng, boolean oriental) {
        int roll = rng.nextInt(100);
        if (oriental) {
            int orient = Config.CLUMSY_ORIENTAL_ORIENTATION.get();
            int loc = Config.CLUMSY_ORIENTAL_LOCATION.get();
            if (roll < orient) {
                return Slip.ORIENTATION;
            }
            return roll < orient + loc ? Slip.LOCATION : Slip.WRONG_BLOCK;
        }
        return roll < Config.CLUMSY_PLAIN_LOCATION.get() ? Slip.LOCATION : Slip.WRONG_BLOCK;
    }

    /** rotate the block to a different facing in place — the same block, just turned wrong. */
    private static boolean wrongOrientation(ServerLevel level, BlockPos pos, BlockState state, RandomSource rng) {
        Rotation[] turns = {Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
        // start at a random turn so it isn't always 90 clockwise, but accept any that actually changes it —
        // some blocks are symmetric under some rotations (a north-south log is unchanged by 180).
        int start = rng.nextInt(turns.length);
        for (int i = 0; i < turns.length; i++) {
            BlockState rotated = state.rotate(turns[(start + i) % turns.length]);
            if (rotated != state) {
                level.setBlock(pos, rotated, Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }

    /** yank the block one over from where it was aimed, if there's a sensible empty neighbour. */
    private static boolean wrongLocation(ServerLevel level, BlockPos pos, BlockState state, RandomSource rng) {
        List<Direction> dirs = new ArrayList<>(List.of(Direction.values()));
        java.util.Collections.shuffle(dirs, new java.util.Random(rng.nextLong()));
        for (Direction dir : dirs) {
            BlockPos moved = pos.relative(dir);
            if (level.getBlockState(moved).canBeReplaced()
                    && state.canSurvive(level, moved)) {
                level.removeBlock(pos, false);          // pick it back up, no drop
                level.setBlock(moved, state, Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }

    /**
     * put a DIFFERENT hotbar block down instead. The original block is picked back up and its item refunded;
     * one of the wrong item is consumed — so your counts move exactly as if you'd fumbled the wrong slot, and
     * nothing is ever created or destroyed.
     */
    private static boolean wrongBlock(ServerPlayer player, ServerLevel level, BlockPos pos,
                                      BlockState placedState, RandomSource rng) {
        Inventory inventory = player.getInventory();
        Block placedBlock = placedState.getBlock();

        List<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() != placedBlock
                    && stack.getCount() > 0) {
                candidates.add(slot);
            }
        }
        if (candidates.isEmpty()) {
            return false; // nothing else to fumble; caller falls back to wrong-location
        }
        int slot = candidates.get(rng.nextInt(candidates.size()));
        BlockItem wrong = (BlockItem) inventory.getItem(slot).getItem();

        level.removeBlock(pos, false);
        // refund the block we WOULD have placed (its item was already spent by vanilla)...
        inventory.add(new ItemStack(placedBlock));
        //...and spend one of the block that actually goes down.
        inventory.getItem(slot).shrink(1);
        level.setBlock(pos, wrong.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    private static boolean isOriented(BlockState state) {
        for (Property<?> property : ORIENTATION_PROPS) {
            if (state.hasProperty(property)) {
                return true;
            }
        }
        return false;
    }
}
