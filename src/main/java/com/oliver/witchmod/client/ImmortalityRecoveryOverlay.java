package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Immortality's "rebuild" screen effect — a warm gold wash that brightens toward white as you knit yourself
 * back together, sold with a soft edge vignette and a gentle pulse. Driven entirely off the synced
 * {@link WitchModAttachments#IMMORTALITY_RECOVERY_START}/{@code _END} pair, so it needs no per-frame state:
 * progress is {@code (now - start) / (end - start)}, and the colour lerps gold→white across it.
 */
public final class ImmortalityRecoveryOverlay implements LayeredDraw.Layer {
    private static final int GOLD = 0xFFC833; // warm gold, RGB only
    private static final int WHITE = 0xFFFFF2;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long end = player.getData(WitchModAttachments.IMMORTALITY_RECOVERY_END);
        long start = player.getData(WitchModAttachments.IMMORTALITY_RECOVERY_START);
        long now = mc.level.getGameTime();
        if (end <= 0L || now >= end) {
            return;
        }

        float progress = end <= start ? 1.0F : (float) (now - start) / (float) (end - start);
        progress = Mth.clamp(progress, 0.0F, 1.0F);

        // Colour drifts gold -> white as the rebuild completes.
        int colour = lerpColour(GOLD, WHITE, progress);

        // A gentle breathing pulse plus a fade-out over the last fifth, so it eases away rather than blinking off.
        float pulse = 0.8F + 0.2F * (float) Math.sin((now + deltaTracker.getGameTimeDeltaPartialTick(false)) * 0.25);
        float fadeOut = progress > 0.8F ? (1.0F - progress) / 0.2F : 1.0F;
        int alpha = Math.round(0.34F * pulse * fadeOut * 255.0F);
        if (alpha <= 0) {
            return;
        }

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        // One EVEN wash across the whole screen — no vignette bands, which is what made the old version read
        // as "less yellow in the middle" (the edge gradients piled alpha at the edges and left the centre
        // bare). A single flat fill keeps the colour uniform everywhere.
        guiGraphics.fill(0, 0, width, height, (alpha << 24) | colour);
    }

    private static int lerpColour(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(Mth.lerp(t, fr, tr));
        int g = Math.round(Mth.lerp(t, fg, tg));
        int b = Math.round(Mth.lerp(t, fb, tb));
        return (r << 16) | (g << 8) | b;
    }
}
