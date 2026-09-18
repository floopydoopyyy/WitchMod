package com.oliver.witchmod.blocks;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * a floor-standing amethyst bell. right-click rings it: an aoe "gamble" ({@link AmethystBellEffects}) reshapes
 * every caught player's fate. ringing greys out EVERY bell in the dimension for a recharge window; that clock
 * is persisted ({@link AmethystBellData}) so a reload, a second bell, or a break-and-replace can't dodge it.
 */
public final class AmethystBellBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS =
            EnumProperty.create("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);

    private static final int RING_EVENT = 1;

    public AmethystBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmethystBellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, WitchModBlockEntities.AMETHYST_BELL.get(), AmethystBellBlockEntity::tick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> got, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == got ? (BlockEntityTicker<A>) ticker : null;
    }

    /** A bell placed while the dimension is still recharging comes up already greyed/locked. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof AmethystBellBlockEntity bell) {
            long until = AmethystBellData.get(sl).until();
            if (until > sl.getGameTime()) {
                bell.setInactiveUntil(until);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AmethystBellBlockEntity bell)) {
            return InteractionResult.PASS;
        }
        // Fully unresponsive while recharging — no swing, no toll, no message (both sides use the synced field).
        if (bell.isInactive()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel sl = (ServerLevel) level;
        // Server authority: the whole dimension shares one recharge, so a second bell (or a break-and-replace)
        // can't dodge it. A fresh block entity still greys itself from the clock here.
        if (AmethystBellData.get(sl).isInactive(sl)) {
            bell.setInactiveUntil(AmethystBellData.get(sl).until());
            return InteractionResult.PASS;
        }

        ring(sl, pos, state, player.getDirection());

        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.85, cz = pos.getZ() + 0.5;
        double aoe = Config.BELL_AOE_RADIUS.get();
        List<ServerPlayer> caught = sl.getPlayers(p -> p.isAlive()
                && p.distanceToSqr(cx, cy, cz) <= aoe * aoe);
        // Decide each caught player's fate NOW (so the consume ramp knows its length + weight), and play the
        // golden-apple-to-a-zombie-villager toll on each — the block entity runs the 3s ramp then resolves it.
        List<AmethystBellBlockEntity.Pending> pending = new ArrayList<>();
        for (ServerPlayer p : caught) {
            AmethystBellEffects.Outcome outcome = AmethystBellEffects.decide(p, p.getRandom());
            pending.add(new AmethystBellBlockEntity.Pending(p.getUUID(), outcome));
            boolean fizzle = outcome == AmethystBellEffects.Outcome.FIZZLE;
            int rampTicks = fizzle ? AmethystBellEffects.fizzleRampTicks() : AmethystBellEffects.applyRampTicks();
            // Synced to trackers so every client renders the consume ramp itself (no server particle packets).
            p.setData(WitchModAttachments.AMETHYST_CONSUME_END, sl.getGameTime() + rampTicks);
            p.setData(WitchModAttachments.AMETHYST_CONSUME_FIZZLE, fizzle ? 1 : 0);
            sl.playSound(null, p.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.9F, 1.0F);
        }
        // Lock every bell in the dimension for the recharge window.
        long until = sl.getGameTime() + Config.BELL_INACTIVE_TICKS.get();
        AmethystBellData.get(sl).set(sl, until);
        bell.ringActivate(pending);
        return InteractionResult.SUCCESS;
    }

    /** Swing (block event) + a dramatic toll + a bright central burst. The shockwave itself runs in the BE tick. */
    private void ring(ServerLevel level, BlockPos pos, BlockState state, Direction swing) {
        level.blockEvent(pos, this, RING_EVENT, swing.get3DDataValue());
        level.playSound(null, pos, WitchModSounds.AMETHYST_BELL_RING.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.7F);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.85, cz = pos.getZ() + 0.5;
        // The central burst + expanding shockwave are rendered CLIENT-side (AmethystBellBlockEntity, started by
        // the ring block-event above) so the server sends no particles for them.

        long until = level.getGameTime() + Config.BELL_SHAKE_TICKS.get();
        double shakeR = Config.BELL_SHAKE_RADIUS.get();
        for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(cx, cy, cz) <= shakeR * shakeR)) {
            nearby.setData(WitchModAttachments.AMETHYST_BELL_SHAKE_END, until);
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == RING_EVENT) {
            if (level.getBlockEntity(pos) instanceof AmethystBellBlockEntity bell) {
                bell.onHit(Direction.from3DDataValue(param));
            }
            return true;
        }
        return super.triggerEvent(state, level, pos, id, param);
    }
}
