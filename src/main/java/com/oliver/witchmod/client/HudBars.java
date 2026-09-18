package com.oliver.witchmod.client;

import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * shared layout for the right-side stacked hud bars so multiple can be active at once without overlapping.
 * bars stack upward from just above the vanilla hunger bar; each bar's row is the count of active bars below
 * it. order (bottom→top, nearest hunger first): gluttony (a second hunger row) then thirst.
 */
final class HudBars {
    /** row 0's top edge is this many pixels up from the bottom of the screen (hunger sits at 39). */
    private static final int ROW0_FROM_BOTTOM = 49;
    private static final int ROW_HEIGHT = 10;

    private HudBars() {}

    static boolean gluttonyActive(LocalPlayer player) {
        return player.getData(WitchModAttachments.GLUTTONY_HUNGER) >= 0;
    }

    static boolean thirstActive(LocalPlayer player) {
        return player.getData(WitchModAttachments.THIRST) >= 0;
    }

    /**
     * whether vanilla's air (bubble) bar is currently showing — it occupies row 0's space on the right, so
     * when it's up the mod bars shift one row higher to sit ABOVE it instead of overlapping.
     */
    static boolean airVisible(LocalPlayer player) {
        return player.getAirSupply() < player.getMaxAirSupply();
    }

    static int topForRow(LocalPlayer player, int guiHeight, int row) {
        int airOffset = airVisible(player) ? ROW_HEIGHT : 0;
        return guiHeight - ROW0_FROM_BOTTOM - airOffset - row * ROW_HEIGHT;
    }

    /** gluttony is the bottom bar (it IS a hunger row), so always row 0 when shown. */
    static int gluttonyRow(LocalPlayer player) {
        return 0;
    }

    /** thirst sits above Gluttony when both are active. */
    static int thirstRow(LocalPlayer player) {
        return gluttonyActive(player) ? 1 : 0;
    }
}
