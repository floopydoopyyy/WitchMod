package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.effects.Curses;

/**
 * shared machinery for the two environmental-damage curses (Basement Dweller = sun, Claustrophobia = boxed
 * in). Each ramps its own damage interval from a slow START down to a fast END over a decay window, tracked
 * per-player from the moment the condition is first entered.
 *
 * <p><b>Combined leeway:</b> while BOTH curses are on the same player, they'd be punishing together, so a
 * grace period is added before any damage starts, the gap between hits is stretched, and the decay is
 * slowed. It's read live off {@link EffectManager#isActive}, so it swaps off the instant either curse is
 * removed.
 */
final class EnvBurn {
    private EnvBurn() {}

    /** true while the player carries BOTH environmental curses — enables the combined leeway. */
    static boolean bothActive(ServerPlayer player) {
        return EffectManager.isActive(player, Curses.BASEMENT_DWELLER)
                && EffectManager.isActive(player, Curses.CLAUSTROPHOBIA);
    }

    /** extra grace ticks before damage begins after entering the condition (0 unless both curses are on). */
    static long graceTicks(boolean both) {
        return both ? Config.ENV_COMBINED_GRACE_TICKS.get() : 0L;
    }

    /**
     * the current gap between hits: a linear ramp from {@code start} down to {@code end} across
     * {@code rampTicks} of exposure, with the combined-curse multipliers folded in when {@code both}.
     *
     * @param elapsed ticks since the ramp's origin (grace end), clamped to ≥0.
     */
    static long interval(long elapsed, int start, int end, int rampTicks, boolean both) {
        long ramp = both ? Math.round(rampTicks * Config.ENV_COMBINED_DECAY_MULT.get()) : rampTicks;
        double t = ramp <= 0 ? 1.0 : Math.min(1.0, Math.max(0L, elapsed) / (double) ramp);
        long iv = Math.round(start + (end - start) * t);
        if (both) {
            iv = Math.round(iv * Config.ENV_COMBINED_INTERVAL_MULT.get());
        }
        return Math.max(1L, iv);
    }
}
