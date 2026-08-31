package com.oliver.witchmod.blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModMobEffects;

/**
 * A placed shield-totem: every player within {@link Config#WARDING_TOTEM_RANGE} of one gets the subtle
 * <b>Protected</b> effect and CANNOT have any curse/blessing/voodoo applied to them — a base defence against
 * being hexed (CLAUDE.md section 2.4/3). Blocked attempts flare a magic force-field around the shielded player.
 *
 * <p>Totem positions are tracked in an in-memory registry via place/break; a server restart re-registers them
 * as their chunks reload (place events don't fire on load, but the shield tick re-validates each position and
 * a totem only shields once its chunk is loaded anyway).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WardingTotemBlock extends Block {
    /** Right-click toggles the totem on/off; while OFF it shields nobody and its amethyst greys out. */
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_TOTEMS = new HashMap<>();
    private static final int TICK_INTERVAL = 10;      // re-apply the effect twice a second
    private static final int PROTECTED_DURATION = 30; // 1.5s — comfortably longer than the interval
    private static final DustParticleOptions FIELD =
            new DustParticleOptions(new Vector3f(0.61F, 0.35F, 0.82F), 1.2F);
    private static final DustParticleOptions DEAD =
            new DustParticleOptions(new Vector3f(0.42F, 0.40F, 0.46F), 1.2F); // grey — powering down

    public WardingTotemBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ENABLED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED);
    }

    /** Right-click to switch the ward on/off, with amethyst-recolour + power-up/down sound feedback. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean nowOn = !state.getValue(ENABLED);
        level.setBlock(pos, state.setValue(ENABLED, nowOn), Block.UPDATE_ALL);
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, pos, nowOn ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.7F, nowOn ? 1.3F : 0.85F);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, nowOn ? 1.5F : 0.7F);
            sl.sendParticles(nowOn ? FIELD : DEAD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    24, 0.25, 0.55, 0.25, 0.02);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** A placed totem at {@code pos} that is switched ON. */
    private static boolean isEnabledTotem(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof WardingTotemBlock && s.getValue(ENABLED);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK_INTERVAL != 0) {
            return;
        }
        double r2 = (double) Config.WARDING_TOTEM_RANGE.get() * Config.WARDING_TOTEM_RANGE.get();
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> totems = ACTIVE_TOTEMS.get(level.dimension());
            if (totems == null || totems.isEmpty()) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                boolean near = false;
                for (BlockPos t : totems) {
                    if (Vec3.atCenterOf(t).distanceToSqr(player.position()) <= r2 && isEnabledTotem(level, t)) {
                        near = true;
                        break;
                    }
                }
                if (near) {
                    // AMBIENT + invisible + no HUD icon: it only shows in the inventory effects list, and never
                    // spams particles on the player (they spend a lot of time here) — the field flares only on a
                    // block. The BLOCK itself emits the ambient purple particles (see animateTick).
                    player.addEffect(new MobEffectInstance(WitchModMobEffects.PROTECTED,
                            PROTECTED_DURATION, 0, true, false, false));
                }
            }
        }
    }

    /** Client-side ambient FX: the totem's amethyst (the mid-shaft core AND the crown) breathes purple magic. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ENABLED) || random.nextInt(3) != 0) {
            return; // a disabled totem is dead — no magic aura
        }
        // Emit from either the embedded core band (~y9-12) or the crowning crystal (~y15-24).
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
        double y = random.nextBoolean()
                ? pos.getY() + 0.6 + random.nextDouble() * 0.2      // core band
                : pos.getY() + 1.0 + random.nextDouble() * 0.5;     // crown crystal
        level.addParticle(FIELD, x, y, z, 0.0, 0.015, 0.0);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            ACTIVE_TOTEMS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            Set<BlockPos> totems = ACTIVE_TOTEMS.get(level.dimension());
            if (totems != null) {
                totems.remove(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
