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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_TOTEMS = new HashMap<>();
    private static final int TICK_INTERVAL = 10;      // re-apply the effect twice a second
    private static final int PROTECTED_DURATION = 30; // 1.5s — comfortably longer than the interval
    private static final DustParticleOptions FIELD =
            new DustParticleOptions(new Vector3f(0.61F, 0.35F, 0.82F), 1.2F);

    public WardingTotemBlock(Properties properties) {
        super(properties);
    }

    /** The authoritative gate used by {@code EffectManager.apply}: is {@code player} inside a totem's range? */
    public static boolean isProtected(ServerPlayer player) {
        Set<BlockPos> totems = ACTIVE_TOTEMS.get(player.level().dimension());
        if (totems == null || totems.isEmpty()) {
            return false;
        }
        double r2 = (double) Config.WARDING_TOTEM_RANGE.get() * Config.WARDING_TOTEM_RANGE.get();
        Vec3 p = player.position();
        for (BlockPos t : totems) {
            if (Vec3.atCenterOf(t).distanceToSqr(p) <= r2) {
                return true;
            }
        }
        return false;
    }

    /**
     * A hex was deflected. A colour-coded lash streaks in from the caster's direction (purple for a curse,
     * gold for a blessing) and is caught by a flaring magic force-field dome around the shielded player, with a
     * soft chime. A null caster (jar / dummy / system) skips the directional lash — just the dome.
     */
    public static void onBlocked(ServerPlayer target, @Nullable ServerPlayer caster, boolean curse) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (caster != null) {
            com.oliver.witchmod.items.WardEffects.startLash(target, caster, curse); // the incoming coloured lash
        }
        forceField(level, target, 46);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
        level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    private static void forceField(ServerLevel level, ServerPlayer player, int count) {
        double cx = player.getX(), cy = player.getY() + 1.0, cz = player.getZ();
        double radius = 1.3;
        for (int i = 0; i < count; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * level.random.nextDouble() - 1);
            double dx = radius * Math.sin(phi) * Math.cos(theta);
            double dy = radius * Math.cos(phi);
            double dz = radius * Math.sin(phi) * Math.sin(theta);
            level.sendParticles(FIELD, cx + dx, cy + dy, cz + dz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 10, 0.6, 0.8, 0.6, 0.02);
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
                    if (Vec3.atCenterOf(t).distanceToSqr(player.position()) <= r2) {
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

    /** Client-side ambient FX: the totem's gem breathes periodic purple particles. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
        double y = pos.getY() + 0.95 + random.nextDouble() * 0.2;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
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
