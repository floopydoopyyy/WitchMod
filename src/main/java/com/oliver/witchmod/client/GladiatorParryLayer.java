package com.oliver.witchmod.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * gladiator's parry gauge, drawn just under the crosshair as a tiny pixel-art SHIELD — a distinct, defensive
 * silhouette (not another bar) that fills from the bottom with the parry stage:
 * <ul>
 *   <li>glows <b>gold</b> and drains while a window is open,</li>
 *   <li>full <b>muted gold</b> when you're ready,</li>
 *   <li><b>grey</b>, refilling, while it recharges.</li>
 * </ul>
 * it <b>auto-hides</b> after sitting full/ready for a second, so it isn't a constant obstruction — reappearing
 * the instant you parry or it starts recharging. First person + a sword/axe in hand only.
 */
public final class GladiatorParryLayer implements LayeredDraw.Layer {
    // shield silhouette, top→bottom row widths (all odd, centred).
    private static final int[] WIDTHS = {7, 9, 9, 9, 9, 7, 5, 3, 1};

    private static final int OUTLINE = 0xB0202020;
    private static final int WELL = 0x70303030;
    private static final int READY_HIDE_TICKS = 13; // hide after full for ~0.65s

    private static long readySince = -1;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null
                || mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player.getData(WitchModAttachments.GLADIATOR_ACTIVE) < 0) {
            return;
        }
        var main = player.getMainHandItem().getItem();
        if (!(main instanceof SwordItem) && !(main instanceof AxeItem)) {
            return;
        }

        long now = mc.level.getGameTime();
        long parryEnd = player.getData(WitchModAttachments.GLADIATOR_PARRY_END);
        long cooldownEnd = player.getData(WitchModAttachments.GLADIATOR_COOLDOWN_END);

        boolean windowOpen = parryEnd > 0 && now < parryEnd;
        boolean cooling = now < cooldownEnd;

        // auto-hide once it's been sitting full/ready for a second.
        if (!windowOpen && !cooling) {
            if (readySince < 0) {
                readySince = now;
            }
            if (now - readySince >= READY_HIDE_TICKS) {
                return;
            }
        } else {
            readySince = -1;
        }

        float fill;
        int fillColor;
        int boss;
        if (windowOpen) {
            fill = Mth.clamp((float) (parryEnd - now) / Config.GLADIATOR_WINDOW_TICKS.get(), 0.0F, 1.0F);
            fillColor = 0xF0FFC63C;
            boss = 0xF0FFE79A;
        } else if (cooling) {
            float whiffTicks = (float) (Config.GLADIATOR_WHIFF_COOLDOWN_SECONDS.get() * 20.0);
            fill = 1.0F - Mth.clamp((float) (cooldownEnd - now) / whiffTicks, 0.0F, 1.0F);
            fillColor = 0x90808080;
            boss = 0x90A8A8A8;
        } else {
            fill = 1.0F;
            fillColor = 0xB0BFAE63;
            boss = 0xB0DBCB8C;
        }

        int rows = WIDTHS.length;
        int cx = g.guiWidth() / 2;
        int y0 = g.guiHeight() / 2 + 20; // lowered so it clears the vanilla attack-cooldown indicator
        int filledRows = Math.round(rows * fill);

        // A dark cap over the top edge.
        int topW = WIDTHS[0];
        g.fill(cx - topW / 2, y0 - 1, cx - topW / 2 + topW, y0, OUTLINE);

        for (int r = 0; r < rows; r++) {
            int w = WIDTHS[r];
            int left = cx - w / 2;
            int ry = y0 + r;
            boolean filled = (rows - r) <= filledRows; // fills from the bottom up
            g.fill(left, ry, left + w, ry + 1, filled ? fillColor : WELL);
            // dark side edges for the shield outline.
            g.fill(left, ry, left + 1, ry + 1, OUTLINE);
            g.fill(left + w - 1, ry, left + w, ry + 1, OUTLINE);
            // raised centre ridge on filled rows.
            if (filled && w >= 3) {
                g.fill(cx, ry, cx + 1, ry + 1, boss);
            }
        }
    }
}
