package com.oliver.witchmod.blocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * A floor-standing amethyst bell (deepslate stand + amethyst bell). Unlike the vanilla bell it only places as
 * a free-standing stand (never on walls/ceilings) in one of TWO orientations (horizontal axis). Right-clicking
 * RINGS it — the bell swings (block-entity animation, like vanilla), a dramatic bell tolls, amethyst sparks
 * burst — and it flips one of the ringer's active effects for another of the same category, on a cooldown.
 */
public final class AmethystBellBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS =
            EnumProperty.create("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);

    private static final int COOLDOWN_TICKS = 20 * 30;
    private static final int RING_EVENT = 1;
    /** A very subtle camera jolt for anyone nearby when it's rung — for impact. */
    public static final int SHAKE_TICKS = 6;
    public static final double SHAKE_STRENGTH = 0.4;
    private static final double SHAKE_RADIUS = 10.0;
    private static final Map<UUID, Long> LAST_USE_TICK = new WeakHashMap<>();

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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        long now = level.getGameTime();
        Long lastUse = LAST_USE_TICK.get(serverPlayer.getUUID());
        boolean onCooldown = lastUse != null && now - lastUse < COOLDOWN_TICKS;

        // Ring visually + audibly regardless (it always tolls); only the EFFECT flip is gated by the cooldown.
        Direction swing = player.getDirection();
        ring((ServerLevel) level, pos, state, swing);

        if (onCooldown) {
            long secondsLeft = Math.max(1, (COOLDOWN_TICKS - (now - lastUse)) / 20);
            serverPlayer.displayClientMessage(Component.literal("On cooldown — its magic returns in " + secondsLeft + "s.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }
        if (flipRandomEffect(serverPlayer)) {
            LAST_USE_TICK.put(serverPlayer.getUUID(), now);
        } else {
            serverPlayer.displayClientMessage(Component.literal("Nothing to flip — you carry no curses or blessings.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Fires the block event (syncs the swing to all clients) + a dramatic toll + amethyst FX. */
    private void ring(ServerLevel level, BlockPos pos, BlockState state, Direction swing) {
        level.blockEvent(pos, this, RING_EVENT, swing.get3DDataValue());
        level.playSound(null, pos, WitchModSounds.AMETHYST_BELL_RING.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.85, cz = pos.getZ() + 0.5;
        // On-brand amethyst FX only: a ring of purple witch sparks bursting outward, an amethyst dust puff, and
        // a few white glints. (No enchant/glow-squid blue — those read as off-theme.)
        net.minecraft.core.particles.DustParticleOptions amethyst =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.66F, 0.36F, 0.82F), 1.4F);
        for (int i = 0; i < 28; i++) {
            double a = i / 28.0 * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.sendParticles(ParticleTypes.WITCH, cx + dx * 0.45, cy, cz + dz * 0.45, 0, dx * 0.22, 0.06, dz * 0.22, 1.0);
        }
        level.sendParticles(amethyst, cx, cy + 0.15, cz, 24, 0.32, 0.35, 0.32, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 8, 0.22, 0.28, 0.22, 0.02);

        // A very subtle camera jolt for anyone close enough to feel the toll.
        long until = level.getGameTime() + SHAKE_TICKS;
        for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(cx, cy, cz) <= SHAKE_RADIUS * SHAKE_RADIUS)) {
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

    private boolean flipRandomEffect(ServerPlayer player) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return false;
        }
        ResourceLocation currentId = active.activeIds().stream()
                .skip(player.getRandom().nextInt(active.activeIds().size()))
                .findFirst().orElseThrow();
        Holder.Reference<Effect> current = WitchModRegistries.EFFECT_REGISTRY.getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(WitchModRegistries.EFFECT_REGISTRY_KEY, currentId));
        EffectCategory category = current.value().category();
        int remainingTicks = active.get(currentId).map(instance -> instance.remainingTicks()).orElse(20 * 60);

        List<Holder.Reference<Effect>> sameCategoryPool = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().selectable())
                .filter(holder -> holder.value().category() == category && !holder.key().location().equals(currentId))
                .toList();
        if (sameCategoryPool.isEmpty()) {
            return false;
        }
        Holder.Reference<Effect> replacement = sameCategoryPool.get(player.getRandom().nextInt(sameCategoryPool.size()));
        EffectManager.remove(player, current);
        EffectManager.apply(player, replacement, remainingTicks, null);
        player.displayClientMessage(Component.literal("The bell tolls — something flips inside you...")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }
}
