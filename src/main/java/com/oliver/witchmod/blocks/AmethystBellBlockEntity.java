package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drives the Amethyst Bell's swing animation, mirroring vanilla {@code BellBlockEntity}: {@link #onHit} starts
 * the shake in a direction, and {@link #tick} winds it down. The renderer reads {@link #ticks}/{@link #shaking}
 * /{@link #clickDirection} to swing the bell body exactly like a vanilla bell.
 */
public final class AmethystBellBlockEntity extends BlockEntity {
    public int ticks;
    public boolean shaking;
    public Direction clickDirection = Direction.NORTH;

    public AmethystBellBlockEntity(BlockPos pos, BlockState state) {
        super(WitchModBlockEntities.AMETHYST_BELL.get(), pos, state);
    }

    /** Start (or restart) the swing toward {@code direction}. */
    public void onHit(Direction direction) {
        this.clickDirection = direction;
        if (this.shaking) {
            this.ticks = 0;
        }
        this.shaking = true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AmethystBellBlockEntity be) {
        if (be.shaking) {
            be.ticks++;
            if (be.ticks >= 50) {
                be.shaking = false;
                be.ticks = 0;
            }
        }
    }
}
