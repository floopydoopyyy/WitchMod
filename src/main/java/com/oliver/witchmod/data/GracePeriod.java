package com.oliver.witchmod.data;

import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;

/** new players can't be targeted for a configurable window after their first-ever join. */
public final class GracePeriod {
    private GracePeriod() {}

    /** called every login; only records the timestamp the first time, so reconnects don't reset the window. */
    public static void markFirstSeenIfAbsent(ServerPlayer player) {
        if (player.getData(WitchModAttachments.FIRST_SEEN_TICK) < 0) {
            player.setData(WitchModAttachments.FIRST_SEEN_TICK, player.serverLevel().getGameTime());
        }
    }

    public static boolean isInGracePeriod(ServerPlayer player) {
        int graceTicks = Config.GRACE_PERIOD_TICKS.get();
        if (graceTicks <= 0) {
            return false;
        }
        long firstSeen = player.getData(WitchModAttachments.FIRST_SEEN_TICK);
        if (firstSeen < 0) {
            // never recorded (pre-dates the system) — don't block
            return false;
        }
        return player.serverLevel().getGameTime() - firstSeen < graceTicks;
    }
}
