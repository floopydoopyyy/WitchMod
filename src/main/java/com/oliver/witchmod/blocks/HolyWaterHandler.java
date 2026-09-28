package com.oliver.witchmod.blocks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;

/**
 * standing-in-holy-water behaviour: a splash on entry, subtle shine, and — if you carry effects — a rapid
 * cleanse that drains their timers (synced so the on-screen effect shrinks too). also grants the global
 * PROTECTED effect while you bathe, which is what actually blocks magic ({@link EffectManager#isMagicProtected}).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class HolyWaterHandler {
    /** players currently in holy water, so we can play the one-shot splash on the tick they get in. */
    private static final Set<UUID> BATHING = ConcurrentHashMap.newKeySet();
    /** continuous ticks a player has spent bathing (drives the drain ramp); reset when they leave. */
    private static final Map<UUID, Integer> SOAK_TICKS = new ConcurrentHashMap<>();

    private HolyWaterHandler() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!HolyWater.isProtecting(player)) {
            BATHING.remove(player.getUUID());
            SOAK_TICKS.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();

        // holy water grants the global PROTECTED shield while you bathe (refreshed each tick). This is now the
        // single way magic is blocked (EffectManager checks the effect); a Warding Totem applies it the same way.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.oliver.witchmod.data.WitchModMobEffects.PROTECTED, 20, 0, true, false, true));

        // A soft splash the instant you get in.
        if (BATHING.add(player.getUUID())) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5F, 1.2F);
        }
        // occasional swim stroke while actually moving through it (guaranteed audio even if the water tag's
        // own swim sounds don't fire for a modded fluid).
        double horiz = player.getDeltaMovement().horizontalDistanceSqr();
        if (horiz > 0.004 && player.tickCount % 11 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SWIM, SoundSource.PLAYERS, 0.4F, 1.0F);
        }
        // sparse ambient shine — flavour only.
        if (player.tickCount % 15 == 0) {
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                    1, player.getBbWidth() * 0.5, player.getBbHeight() * 0.4, player.getBbWidth() * 0.5, 0.0);
        }

        // the longer you soak, the harder the cleanse bites: ramp from purifyRampMinMult to purifyRampMaxMult
        // over purifyRampTicks of continuous bathing.
        int soak = SOAK_TICKS.merge(player.getUUID(), 1, Integer::sum);

        if (EffectManager.activeCount(player) > 0) {
            int drain = Math.max(1, (int) Math.round(Config.PURIFY_DRAIN_TICKS_PER_TICK.get() * soakMultiplier(soak)));
            // drain the timers; reduceAllDurations re-syncs the wrapper so the visible effect ticks down too.
            EffectManager.reduceAllDurations(player, drain);
            // one mote drifting INTO you every few ticks — the cleanse leaving your body.
            if (player.tickCount % 8 == 0) {
                drainMote(level, player);
            }
            // A soft, soothing chime while it works.
            if (player.tickCount % 14 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.5F, 1.4F);
            }
            // the Narrator points out that you're washing off the very curse it's riding on.
            if (player.tickCount % 40 == 0) {
                com.oliver.witchmod.effects.curses.CurseNarrator.event(player, "holy_water");
            }
        }
    }

    /**
     * the drain multiplier for {@code soak} continuous ticks: a first ramp min→max over purifyRampTicks, then a
     * second ramp max→2×max over the next purifyRamp2Ticks, so a long soak really races the timers down.
     */
    private static double soakMultiplier(int soak) {
        double base = Config.PURIFY_RAMP_MIN_MULT.get();
        double peak = Config.PURIFY_RAMP_MAX_MULT.get();
        int ramp1 = Config.PURIFY_RAMP_TICKS.get();
        if (soak <= ramp1) {
            return base + (peak - base) * Math.min(1.0, (double) soak / ramp1);
        }
        int ramp2 = Config.PURIFY_RAMP2_TICKS.get();
        double frac2 = ramp2 <= 0 ? 1.0 : Math.min(1.0, (double) (soak - ramp1) / ramp2);
        return peak + peak * frac2; // climbs to double the peak
    }

    /** A single white mote spawned around the player, drifting toward their centre. */
    private static void drainMote(ServerLevel level, ServerPlayer p) {
        double ang = level.random.nextDouble() * Math.PI * 2;
        double r = 1.0 + level.random.nextDouble() * 0.4;
        double px = p.getX() + Math.cos(ang) * r;
        double pz = p.getZ() + Math.sin(ang) * r;
        double py = p.getY() + 0.4 + level.random.nextDouble() * p.getBbHeight();
        double vx = (p.getX() - px) * 0.22;
        double vz = (p.getZ() - pz) * 0.22;
        level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 0, vx, 0.02, vz, 1.0);
    }
}
