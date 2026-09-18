package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * siren's Call's magenta "mind-control" tint. Drawn while the sea has (or is releasing) its hold: the synced
 * {@link WitchModAttachments#SIREN_SHADER} value (0..1) ramps up as the march takes over and fades to 0 across
 * the water grace period, so the screen is clear again by the time the longing actually starts to drop.
 *
 * <p>Deliberately just a flat magenta fill at {@code SIREN_SHADER * sirenShaderMaxAlpha} — a tint you see
 * through, not a wall — plus a slightly heavier vignette at the edges so it reads as pressure closing in
 * rather than a colour wash. All driven off the local player's attachment, so it needs no per-frame state.
 */
public final class SirenShaderOverlay implements LayeredDraw.Layer {
    /** magenta, RGB only — the alpha is filled in per frame from the synced strength. */
    private static final int MAGENTA = 0xC81E8C;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        float strength = player.getData(WitchModAttachments.SIREN_SHADER);
        if (strength <= 0.0F) {
            return;
        }

        float maxAlpha = Config.SIREN_SHADER_MAX_ALPHA.get().floatValue();
        int alpha = Math.round(Math.min(1.0F, strength) * maxAlpha * 255.0F);
        if (alpha <= 0) {
            return;
        }

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        // the flat tint over everything.
        guiGraphics.fill(0, 0, width, height, (alpha << 24) | MAGENTA);

        // A heavier band down each edge so it feels like the sea pressing in from the sides.
        int edgeAlpha = Math.min(255, alpha + alpha / 2);
        int band = Math.max(24, width / 8);
        guiGraphics.fillGradient(0, 0, band, height, (edgeAlpha << 24) | MAGENTA, 0x00000000 | MAGENTA);
        guiGraphics.fillGradient(width - band, 0, width, height, 0x00000000 | MAGENTA, (edgeAlpha << 24) | MAGENTA);
    }
}
