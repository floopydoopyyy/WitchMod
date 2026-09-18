package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * blessing of Flight energy bar: a slim yellow bar just above the XP bar, with a slightly lighter glow at the
 * filled end. Hidden unless the blessing is active. Driven by the synced {@link WitchModAttachments#FLIGHT_ENERGY}.
 */
public final class FlightBarLayer implements LayeredDraw.Layer {
    private static final int WIDTH = 182;
    private static final int HEIGHT = 3;
    private static final int BACK = 0xC0202018;
    private static final int FILL = 0xFFFFE24D;   // warm yellow
    private static final int GLOW = 0xFFFFF7B0;   // lighter cap at the filled end
    private static final int FILL_LOCKED = 0xFF6A6A66; // greyed while depleted (can't fly)
    private static final int GLOW_LOCKED = 0xFF8A8A86;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        int active = player.getData(WitchModAttachments.FLIGHT_ACTIVE);
        if (active < 1 || FlightClient.barHidden()) {
            return; // not active, or auto-hidden after 2s at full charge
        }
        boolean depleted = active == 2; // grounded until the bar refills
        float energy = Math.max(0.0F, Math.min(1.0F, player.getData(WitchModAttachments.FLIGHT_ENERGY)));

        int left = (g.guiWidth() - WIDTH) / 2;
        int y = g.guiHeight() - 55; // well above the health/hunger row so it never clips them

        g.fill(left - 1, y - 1, left + WIDTH + 1, y + HEIGHT + 1, BACK);
        int fillW = Math.round(WIDTH * energy);
        if (fillW > 0) {
            g.fill(left, y, left + fillW, y + HEIGHT, depleted ? FILL_LOCKED : FILL);
            int glowStart = Math.max(left, left + fillW - 3);
            g.fill(glowStart, y, left + fillW, y + HEIGHT, depleted ? GLOW_LOCKED : GLOW);
        }
    }
}
