package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Blessing of Safety (RESPAWN ANCHOR): crouch, stand still and look DOWN to begin a 10s channel; hold it and
 * you're whisked home to your spawn point — and the blessing is spent. An action-bar countdown, gold sparkles
 * and a rising hum make it clear what's happening and where. Taking a hit or moving cancels it and imposes a
 * short cooldown before you can try again.
 */
public final class BlessingSafety extends Effect {
    private static final Map<UUID, Integer> CHANNEL = new HashMap<>();   // ticks channelled
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();     // game tick the cooldown ends
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();
    private static final Map<UUID, Integer> MOVE_GRACE = new HashMap<>(); // consecutive moving ticks (leeway)

    private static final double MOVE_THRESHOLD_SQR = 0.02;  // ~0.14 blocks/tick before it counts as "moving"
    private static final int MOVE_LEEWAY_TICKS = 5;         // a little leeway before movement cancels it
    private static final net.minecraft.core.particles.DustParticleOptions GOLD =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.82F, 0.29F), 1.2F);

    public BlessingSafety() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.RESPAWN_ANCHOR);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        Long cd = COOLDOWN.get(target.getUUID());
        return java.util.Optional.of(cd != null && target.level().getGameTime() < cd ? "on cooldown" : "ready");
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        CHANNEL.remove(id);
        COOLDOWN.remove(id);
        LAST_POS.remove(id);
        LAST_HEALTH.remove(id);
        MOVE_GRACE.remove(id);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        long now = target.level().getGameTime();

        Long cd = COOLDOWN.get(id);
        if (cd != null) {
            if (now < cd) {
                return; // still on cooldown
            }
            // Cooldown just ended — a little gold flourish + chime so you know it's ready again.
            COOLDOWN.remove(id);
            ServerLevel level = target.serverLevel();
            level.sendParticles(GOLD, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.35, 0.6, 0.35, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);
            target.displayClientMessage(Component.translatable("witchmod.safety.ready").withStyle(s -> s.withColor(0xFFD24A)), true);
        }

        Vec3 pos = target.position();
        Vec3 last = LAST_POS.put(id, pos);
        boolean moving = last != null && last.distanceToSqr(pos) >= MOVE_THRESHOLD_SQR;
        int mg = moving ? MOVE_GRACE.merge(id, 1, Integer::sum) : 0;
        if (!moving) {
            MOVE_GRACE.put(id, 0);
        }
        float hp = target.getHealth();
        Float lastHp = LAST_HEALTH.put(id, hp);
        boolean hurt = lastHp != null && hp < lastHp - 0.001F;

        boolean crouch = target.isCrouching();
        boolean channelling = CHANNEL.getOrDefault(id, 0) > 0;

        if (!channelling) {
            // Activation needs the full posture: crouched, still, and looking DOWN.
            if (crouch && !moving && !hurt && target.getXRot() > 55.0F) {
                CHANNEL.put(id, 1);
            }
            return;
        }

        // Once channelling you can look ANYWHERE — only leaving the crouch, real movement, or a hit cancels it.
        if (!crouch || hurt || mg > MOVE_LEEWAY_TICKS) {
            CHANNEL.remove(id);
            MOVE_GRACE.remove(id);
            COOLDOWN.put(id, now + Config.SAFETY_COOLDOWN_TICKS.get());
            target.displayClientMessage(Component.translatable("witchmod.safety.cancelled").withStyle(s -> s.withColor(0xFFB0B0)), true);
            target.level().playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.6F, 0.7F);
            return;
        }

        int c = CHANNEL.getOrDefault(id, 0) + 1;
        CHANNEL.put(id, c);
        int total = Config.SAFETY_CHANNEL_TICKS.get();
        int remSec = Math.max(0, (total - c + 19) / 20);
        target.displayClientMessage(Component.translatable("witchmod.safety.channelling", remSec)
                .withStyle(s -> s.withColor(0xFFD24A)), true);

        // Yellow sparkle column + a rising hum, so both the player and onlookers can see/hear it happening.
        ServerLevel level = target.serverLevel();
        double prog = (double) c / total;
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 0.1 + prog * 1.4, target.getZ(),
                3, 0.25, 0.15, 0.25, 0.01);
        level.sendParticles(GOLD, target.getX(), target.getY() + 0.9 + prog * 0.8, target.getZ(),
                4, 0.35, 0.5, 0.35, 0.0);
        // A ring of gold that tightens as it completes.
        double ringR = 1.4 * (1.0 - prog) + 0.3;
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + prog * 6.0;
            level.sendParticles(GOLD, target.getX() + Math.cos(a) * ringR, target.getY() + 0.15,
                    target.getZ() + Math.sin(a) * ringR, 1, 0, 0, 0, 0);
        }
        if (c % 10 == 0) {
            float pitch = 0.7F + 1.0F * (float) prog;
            level.playSound(null, target.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.7F, pitch);
        }

        if (c >= total) {
            teleportHome(target);
        }
    }

    private void teleportHome(ServerPlayer target) {
        UUID id = target.getUUID();
        CHANNEL.remove(id);
        ServerLevel from = target.serverLevel();
        // A big send-off at the departure point.
        departArriveBurst(from, target.getX(), target.getY(), target.getZ());
        from.playSound(null, target.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        from.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.6F);

        ResourceKey<Level> dimKey = target.getRespawnDimension();
        ServerLevel dest = target.server.getLevel(dimKey);
        if (dest == null) {
            dest = target.server.overworld();
        }
        BlockPos spawn = target.getRespawnPosition();
        if (spawn == null) {
            spawn = dest.getSharedSpawnPos();
        }
        target.teleportTo(dest, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5,
                target.getYRot(), 0.0F);
        departArriveBurst(dest, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        dest.playSound(null, spawn, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.2F);
        dest.playSound(null, spawn, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.4F);
        target.displayClientMessage(Component.translatable("witchmod.safety.home").withStyle(s -> s.withColor(0xFFD24A)), true);

        markDiscoveredByVictim(target);
        // Not consumed — you keep the blessing, but it's on a long cooldown before another trip home.
        COOLDOWN.put(target.getUUID(), target.level().getGameTime() + Config.SAFETY_USE_COOLDOWN_TICKS.get());
        MOVE_GRACE.remove(target.getUUID());
    }

    /** A tall gold+white pillar burst for the teleport at both the departure and arrival points. */
    private static void departArriveBurst(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.FLASH, x, y + 1.0, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 70, 0.4, 0.9, 0.4, 0.14);
        level.sendParticles(GOLD, x, y + 1.0, z, 60, 0.5, 1.0, 0.5, 0.05);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 25, 0.4, 0.8, 0.4, 0.2);
        // A rising twist of gold up the column.
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 8.0;
            double h = i * 0.12;
            level.sendParticles(GOLD, x + Math.cos(a) * 0.5, y + 0.1 + h, z + Math.sin(a) * 0.5, 1, 0, 0, 0, 0);
        }
    }

    @Override
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        teleportHome(target);
        return "teleported home (blessing consumed)";
    }
}
