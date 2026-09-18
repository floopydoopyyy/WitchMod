package com.oliver.witchmod.blocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.oliver.witchmod.ui.BewitchingTableMenu;

/**
 * the ritual table block — right-clicking opens its screen. {@code useItemOn} isn't overridden; vanilla's
 * default falls through to {@code useWithoutItem} regardless of held item, so one method covers both cases.
 */
public final class BewitchingTableBlock extends BaseEntityBlock {
    public static final MapCodec<BewitchingTableBlock> CODEC = simpleCodec(BewitchingTableBlock::new);

    /**
     * {@code false} = nothing above, so the decorative candles on the tabletop are drawn (multipart blockstate).
     * {@code true} = a block sits directly on top, so the candles are dropped from the model to avoid clipping.
     */
    public static final BooleanProperty CAPPED = BooleanProperty.create("capped");

    public BewitchingTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CAPPED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CAPPED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(CAPPED, isCapped(context.getLevel(), context.getClickedPos()));
    }

    /** recompute the candle cap whenever the block directly above changes. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.setValue(CAPPED, !neighborState.isAir());
        }
        return state;
    }

    private static boolean isCapped(BlockGetter level, BlockPos pos) {
        return !level.getBlockState(pos.above()).isAir();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BewitchingTableBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof BewitchingTableBlockEntity table)) {
            return InteractionResult.SUCCESS;
        }

        MenuProvider menuProvider = new SimpleMenuProvider(
                (windowId, inventory, p) -> new BewitchingTableMenu(windowId, inventory, table),
                Component.translatable("block.witchmod.bewitching_table"));
        serverPlayer.openMenu(menuProvider, pos);
        return InteractionResult.SUCCESS;
    }
}
