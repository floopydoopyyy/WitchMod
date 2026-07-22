package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/**
 * The Loading Screen curse's fake fullscreen overlay: an animated sprite, a progress bar, a live percentage
 * and a tip scrolling along the bottom. All timing lives in {@link LoadingScreenState}; this only draws
 * whatever that says the current state is.
 *
 * <p>Registered above every other HUD layer so it covers the lot. The page is a <b>slightly translucent
 * white</b> — the visual interest comes from the world showing faintly through it rather than from anything
 * painted on. Everything except the animation strip is drawn from plain fills, so the whole look lives in
 * the constants below; see CLAUDE.md 13.6 for which one moves which element.
 */
public final class LoadingScreenOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/hud/loading_animation.png");

    private static final int BAR_WIDTH = 240;
    private static final int BAR_HEIGHT = 10;

    /** Soft white, deliberately not fully opaque so the world reads through as the background interest. */
    private static final int BACKGROUND = 0xDCF7F5F0;
    private static final int BAR_RIM = 0xFFA9B79A;      // muted sage — the accent, quiet next to the fill
    private static final int BAR_BACKING = 0xFF3A3A3A;
    private static final int BAR_FILL = 0xFF92C83E;     // the 360 green
    private static final int BAR_HIGHLIGHT = 0xFFB6E063; // 1px lift along the top of the fill
    private static final int TEXT_COLOUR = 0xFF2E2E2E;
    private static final int TIP_COLOUR = 0xFF5A5A5A;
    private static final int FAINT_COLOUR = 0xFF7C7C78;
    private static final int RULE_COLOUR = 0xFFC6C3BC;
    private static final int SHADOW = 0x1E000000;

    private static final String LOADING_WORD = "Loading";

    // Layout. Vertical positions are measured from the centre so they scale with the window; everything is
    // horizontally centred.
    private static final int ANIMATION_BASE = 18; // where the chest's feet sit, below centre
    private static final int TEXT_Y = 60;         // "Loading..." baseline, below centre
    private static final int BAR_Y = 78;          // bar top, below centre
    private static final int TIP_FROM_BOTTOM = 26;
    private static final int RULE_FROM_BOTTOM = 40;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!LoadingScreenState.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        guiGraphics.fill(0, 0, width, height, BACKGROUND);

        drawAnimation(guiGraphics, width, height);
        drawBarAndCaption(guiGraphics, font, width, height);
        drawFooter(guiGraphics, font, deltaTracker, width, height);
    }

    /**
     * One frame of the sprite sheet, centred and sitting on a soft shadow. Handles both sheet layouts: a
     * vertical column (Aseprite's default export, which the shipped chest sheet is) or a horizontal row.
     */
    private static void drawAnimation(GuiGraphics guiGraphics, int width, int height) {
        int frameWidth = Config.LOADING_SCREEN_FRAME_WIDTH.get();
        int frameHeight = Config.LOADING_SCREEN_FRAME_HEIGHT.get();
        int frameCount = Config.LOADING_SCREEN_FRAME_COUNT.get();
        boolean vertical = Config.LOADING_SCREEN_SHEET_VERTICAL.get();
        int frame = LoadingScreenState.animationFrame();

        int sheetWidth = vertical ? frameWidth : frameWidth * frameCount;
        int sheetHeight = vertical ? frameHeight * frameCount : frameHeight;
        float u = vertical ? 0.0F : (float) frame * frameWidth;
        float v = vertical ? (float) frame * frameHeight : 0.0F;

        int base = height / 2 + ANIMATION_BASE;
        // Clamp so a very tall sheet on a very small GUI can't run off the top of the screen.
        int y = Math.max(2, base - frameHeight);

        drawShadow(guiGraphics, width / 2, base);
        guiGraphics.blit(ANIMATION, (width - frameWidth) / 2, y,
                u, v, frameWidth, frameHeight, sheetWidth, sheetHeight);
    }

    /** A squashed ellipse under the chest, so it sits on the page instead of floating above it. */
    private static void drawShadow(GuiGraphics guiGraphics, int centreX, int baseY) {
        int[] halfWidths = {30, 38, 42, 38, 30};
        int rowHeight = 2;
        int y = baseY - (halfWidths.length * rowHeight) / 2;
        for (int halfWidth : halfWidths) {
            guiGraphics.fill(centreX - halfWidth, y, centreX + halfWidth, y + rowHeight, SHADOW);
            y += rowHeight;
        }
    }

    private static void drawBarAndCaption(GuiGraphics guiGraphics, Font font, int width, int height) {
        // Cycles "Loading." -> ".." -> "..." so the screen never looks frozen, even mid-stutter. Centred on
        // the WIDEST form so it can't twitch sideways as the dots come and go, and drawn without a shadow,
        // which would read as mud against a light background.
        String caption = LOADING_WORD + ".".repeat(LoadingScreenState.loadingDots());
        int captionX = (width - font.width(LOADING_WORD + "...")) / 2;
        guiGraphics.drawString(font, caption, captionX, height / 2 + TEXT_Y, TEXT_COLOUR, false);

        int barWidth = Math.min(BAR_WIDTH, width - 32);
        int x = (width - barWidth) / 2; // the bar itself is what must read as centred
        int y = height / 2 + BAR_Y;

        guiGraphics.fill(x - 2, y - 2, x + barWidth + 2, y + BAR_HEIGHT + 2, BAR_RIM);
        guiGraphics.fill(x, y, x + barWidth, y + BAR_HEIGHT, BAR_BACKING);

        float progress = Math.max(0.0F, Math.min(1.0F, LoadingScreenState.progress()));
        int filled = Math.round(barWidth * progress);
        if (filled > 0) {
            guiGraphics.fill(x, y, x + filled, y + BAR_HEIGHT, BAR_FILL);
            guiGraphics.fill(x, y, x + filled, y + 1, BAR_HIGHLIGHT); // gives the fill a little depth
        }

        // A live percentage beside the bar. Free from progress(), and it makes a stutter far funnier —
        // you sit and watch a number refuse to move.
        String percent = Math.round(progress * 100.0F) + "%";
        guiGraphics.drawString(font, percent, x + barWidth + 8,
                y + (BAR_HEIGHT - font.lineHeight) / 2 + 1, FAINT_COLOUR, false);
    }

    /** The hairline rule and the scrolling tip. */
    private static void drawFooter(GuiGraphics guiGraphics, Font font, DeltaTracker deltaTracker,
                                   int width, int height) {
        int margin = Math.max(16, width / 8);
        int ruleY = height - RULE_FROM_BOTTOM;
        guiGraphics.fill(margin, ruleY, width - margin, ruleY + 1, RULE_COLOUR);

        drawScrollingTip(guiGraphics, font, deltaTracker, width, height);
    }

    /** The tip marquee: scrolls right-to-left, and moves to the next tip once it has fully left the screen. */
    private static void drawScrollingTip(GuiGraphics guiGraphics, Font font, DeltaTracker deltaTracker,
                                         int width, int height) {
        String tip = LoadingScreenState.currentTip();
        if (tip.isEmpty()) {
            return;
        }
        int textWidth = font.width(tip);

        float scroll = LoadingScreenState.tipScroll();
        if (Float.isNaN(scroll)) {
            scroll = width; // start just off the right edge
        }
        scroll -= (float) (Config.LOADING_SCREEN_TIP_SCROLL_SPEED.get() * deltaTracker.getGameTimeDeltaTicks());
        if (scroll < -textWidth) {
            LoadingScreenState.nextTip();
            scroll = width;
        }
        LoadingScreenState.setTipScroll(scroll);

        int y = height - TIP_FROM_BOTTOM;
        guiGraphics.enableScissor(0, y - 2, width, y + font.lineHeight + 2);
        guiGraphics.drawString(font, tip, (int) scroll, y, TIP_COLOUR, false);
        guiGraphics.disableScissor();
    }
}
