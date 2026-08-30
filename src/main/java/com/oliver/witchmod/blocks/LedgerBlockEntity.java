package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A trivial block entity for the Ledger — it holds no data; it exists purely so a
 * {@code client/LedgerRenderer} can draw the 3D purple book resting on top (the same book the vanilla
 * lectern renders when a book is placed on it).
 */
public final class LedgerBlockEntity extends BlockEntity {
    public LedgerBlockEntity(BlockPos pos, BlockState state) {
        super(WitchModBlockEntities.LEDGER.get(), pos, state);
    }
}
