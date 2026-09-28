package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * purifying water's fluids — lowest priority: it refuses to spread into a block already holding any other
 * fluid, so it stops at the edge rather than replacing it. only {@code spreadTo} is overridden; the rest is
 * stock {@link BaseFlowingFluid} configured in {@link WitchModFluids}.
 */
public final class PurifyingWaterFluid {
    private PurifyingWaterFluid() {}

    /** true if {@code target} already contains a DIFFERENT fluid, so purifying water must not enter it. */
    private static boolean blockedByForeignFluid(BlockState target) {
        FluidState fs = target.getFluidState();
        if (fs.isEmpty()) {
            return false;
        }
        Fluid type = fs.getType();
        return type != WitchModFluids.PURIFYING_WATER.get() && type != WitchModFluids.PURIFYING_WATER_FLOWING.get();
    }

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState) {
            if (blockedByForeignFluid(blockState)) {
                return;
            }
            super.spreadTo(level, pos, blockState, direction, fluidState);
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState) {
            if (blockedByForeignFluid(blockState)) {
                return;
            }
            super.spreadTo(level, pos, blockState, direction, fluidState);
        }
    }
}
