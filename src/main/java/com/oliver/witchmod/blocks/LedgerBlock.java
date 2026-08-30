package com.oliver.witchmod.blocks;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.LedgerLog;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * A lectern-style read-only block (CLAUDE.md section 2.3). Right-clicking opens a custom Ledger screen listing
 * every ritual — landed or blocked — that happened within the block's configurable range
 * ({@link Config#LEDGER_RANGE}), newest first, with the modifier used. When an attachment is logged nearby the
 * block reacts with particle feedback ({@link LedgerFeedback}).
 *
 * <p>Uses the vanilla lectern MODEL (to be reskinned via its own witchmod textures); it is deliberately NOT a
 * {@code LecternBlock} subclass — that brings book-holding/redstone machinery this doesn't need.
 */
public final class LedgerBlock extends Block implements EntityBlock {
    /** Directional like a lectern, so the 3D book on top faces the reader (matches the lectern's book pose). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LedgerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LedgerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        LedgerFeedback.register(level, pos); // make sure this Ledger reacts to future hexes this session

        int range = Config.LEDGER_RANGE.get();
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        long now = level.getGameTime();

        List<WitchModNetwork.LedgerEntry> entries = new ArrayList<>();
        for (LedgerLog.Entry e : LedgerLog.entriesNear(here, range)) {
            String effect = DiscoveryManager.titleCase(e.effectId().getPath());
            String modifier = e.modifier().orElse("");
            String result = e.result() + " · " + ago(now - e.gameTime());
            entries.add(new WitchModNetwork.LedgerEntry(
                    e.casterName().orElse("(system)"), e.targetName(), effect, modifier, result, e.scribbled()));
        }
        PacketDistributor.sendToPlayer(serverPlayer, new WitchModNetwork.LedgerPayload(range, entries));
        return InteractionResult.SUCCESS;
    }

    /** A compact "how long ago" from a tick delta. */
    private static String ago(long ticks) {
        if (ticks < 0) {
            ticks = 0;
        }
        long seconds = ticks / 20;
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        return hours + "h ago";
    }
}
