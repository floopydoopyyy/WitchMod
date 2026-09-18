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
 * a placed shield-totem: players within {@link Config#WARDING_TOTEM_RANGE} get the Protected effect and can't
 * be hexed; blocked attempts flare a force-field. positions live in an in-memory registry keyed by
 * place/break and re-registered on chunk load, so a restart doesn't lose them.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WardingTotemBlock extends Block {
    /** right-click toggles the totem on/off; while OFF it shields nobody and its amethyst greys out. */
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_TOTEMS = new HashMap<>();
    private static final int TICK_INTERVAL = 10;      // re-apply the effect twice a second
    private static final int PROTECTED_DURATION = 30; // 1.5s — comfortably longer than the interval
    private static final DustParticleOptions FIELD =
            new DustParticleOptions(new Vector3f(0.61F, 0.35F, 0.82F), 1.2F);
    private static final DustParticleOptions DEAD =
            new DustParticleOptions(new Vector3f(0.42F, 0.40F, 0.46F), 1.2F); // grey — powering down
    /** magenta — the one-shot "you crossed the boundary" cue when you enter or leave the ward. */
    private static final DustParticleOptions MAGENTA =
            new DustParticleOptions(new Vector3f(0.95F, 0.15F, 0.75F), 1.3F);

    public WardingTotemBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ENABLED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED);
    }

    /** right-click to switch the ward on/off, with amethyst-recolour + power-up/down sound feedback. */
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

    /** whether {@code player} is currently inside an enabled totem's radius (for the "Test the Waters" report). */
    public static boolean isInRange(ServerPlayer player) {
        Set<BlockPos> totems = ACTIVE_TOTEMS.get(player.level().dimension());
        if (totems == null) {
            return false;
        }
        double r2 = (double) Config.WARDING_TOTEM_RANGE.get() * Config.WARDING_TOTEM_RANGE.get();
        for (BlockPos t : totems) {
            if (Vec3.atCenterOf(t).distanceToSqr(player.position()) <= r2 && isEnabledTotem(player.level(), t)) {
                return true;
            }
        }
        return false;
    }

    /** A placed totem at {@code pos} that is switched ON. */
    private static boolean isEnabledTotem(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof WardingTotemBlock && s.getValue(ENABLED);
    }

    /** players currently inside an enabled totem's radius — so we can play a one-shot cue when they enter. */
    private static final java.util.Set<java.util.UUID> INSIDE = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK_INTERVAL != 0) {
            return;
        }
        double r2 = (double) Config.WARDING_TOTEM_RANGE.get() * Config.WARDING_TOTEM_RANGE.get();
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> totems = ACTIVE_TOTEMS.get(level.dimension());
            for (ServerPlayer player : level.players()) {
                boolean near = false;
                if (totems != null) {
                    for (BlockPos t : totems) {
                        if (Vec3.atCenterOf(t).distanceToSqr(player.position()) <= r2 && isEnabledTotem(level, t)) {
                            near = true;
                            break;
                        }
                    }
                }
                if (near) {
                    // the Protected effect: shields you AND shows its inventory icon, but NO particles of its
                    // own (showParticles=false) — the only player particles are the enter/exit cue below.
                    player.addEffect(new MobEffectInstance(WitchModMobEffects.PROTECTED,
                            PROTECTED_DURATION, 0, true, false, true));
                    if (INSIDE.add(player.getUUID())) {
                        // just crossed IN — a subtle chime + a ring of magenta "entered a magic zone" motes.
                        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                                SoundSource.PLAYERS, 0.35F, 1.7F);
                        wardBoundary(level, player);
                    }
                } else if (INSIDE.remove(player.getUUID())) {
                    // just crossed OUT (or the totem was switched off) — the same magenta cue, a touch lower.
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 0.3F, 1.1F);
                    wardBoundary(level, player);
                }
            }
        }
    }

    /**
     * The totem registry is in-memory, and {@code onPlace} only fires on real placement — so after a server
     * restart or {@code /reload} it would be empty and no one gets shielded. Re-register totems as their chunks
     * load, by scanning the chunk's non-empty sections.
     */
    @SubscribeEvent
    static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int si = 0; si < sections.length; si++) {
            net.minecraft.world.level.chunk.LevelChunkSection sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) {
                continue;
            }
            int y0 = level.getMinBuildHeight() + si * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (sec.getBlockState(x, y, z).getBlock() instanceof WardingTotemBlock) {
                            ACTIVE_TOTEMS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet())
                                    .add(new BlockPos(baseX + x, y0 + y, baseZ + z));
                        }
                    }
                }
            }
        }
    }

    /** A one-shot ring of magenta motes around a player who just entered/left the ward — the "boundary" cue. */
    private static void wardBoundary(ServerLevel level, ServerPlayer p) {
        double cy = p.getY() + p.getBbHeight() * 0.5;
        for (int i = 0; i < 14; i++) {
            double a = i / 14.0 * Math.PI * 2;
            level.sendParticles(MAGENTA, p.getX() + Math.cos(a) * 0.7, cy + (level.random.nextDouble() - 0.5) * 0.9,
                    p.getZ() + Math.sin(a) * 0.7, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    /** client-side ambient FX: the totem's amethyst (the mid-shaft core AND the crown) breathes purple magic. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ENABLED) || random.nextInt(3) != 0) {
            return; // a disabled totem is dead — no magic aura
        }
        // emit from either the embedded core band (~y9-12) or the crowning crystal (~y15-24).
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
