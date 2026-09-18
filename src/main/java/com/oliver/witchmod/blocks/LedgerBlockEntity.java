package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** empty block entity for the ledger — holds no data; exists so the renderer can draw the 3d book on top. */
public final class LedgerBlockEntity extends BlockEntity {
    public LedgerBlockEntity(BlockPos pos, BlockState state) {
        super(WitchModBlockEntities.LEDGER.get(), pos, state);
    }
}
