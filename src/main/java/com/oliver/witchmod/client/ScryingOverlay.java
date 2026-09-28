package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.oliver.witchmod.items.WitchModItems;
import com.oliver.witchmod.network.WitchModNetwork.ScryEntry;

/**
 * the Scrying Mirror result — a translucent glassy side panel of the revealed curses/blessings (coloured
 * tags, names, timers, and the extra "specifics" some effects expose). it stays up while you HOLD the mirror
 * and lingers only LINGER_TICKS after you lower it. names clip, details wrap, so nothing overruns the frame.
 */
public final class ScryingOverlay implements LayeredDraw.Layer {
    private static final int LINGER_TICKS = 10; // frames the panel stays after you stop peering (then it's gone)

    // translucent "scrying glass" palette (RRGGBB; alpha OR'd in at draw time).
    private static final int GLASS_TOP = 0x241636;   // deep indigo, panel top
    private static final int GLASS_BOT = 0x120A20;   // darker, panel bottom
    private static final int FRAME = 0x8A5CC0;       // soft arcane purple border
    private static final int HEADER = 0xD8C7F0;      // light lavender header text
    private static final int INK_SOFT = 0x9A8CB4;    // muted detail/timer text
    private static final int CURSE = 0xC77BE0;       // purple
    private static final int BLESS = 0xF0C24C;       // gold

    private static final int WIDTH = 190;
    private static final int PAD = 8;
    private static final int NAME_LINE_H = 11;
    private static final int DETAIL_LINE_H = 9;
    private static final float BASE_ALPHA = 0.82f;   // translucent

    private static String title = "";
    private static List<ScryEntry> entries = List.of();
    private static boolean hasData;
    private static long hideAt;
    private static boolean wasPeering;

    public static void receive(String newTitle, List<ScryEntry> newEntries) {
        Minecraft mc = Minecraft.getInstance();
        title = newTitle;
        entries = newEntries;
        hasData = true; // an empty list is still a real result ("no afflictions")
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        hideAt = now + LINGER_TICKS; // holding will keep pushing this forward; a fallback if it isn't
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = mc.level.getGameTime();
        // while the local player is peering through the mirror, keep the panel alive; otherwise it counts down.
        boolean peering = mc.player.isUsingItem() && mc.player.getUseItem().is(WitchModItems.SCRYING_MIRROR.get());
        if (peering && !wasPeering) {
            // a fresh peer session — drop the previous target's data until this one's reveal arrives.
            entries = List.of();
            hasData = false;
            hideAt = 0;
        }
        wasPeering = peering;
        if (peering) {
            hideAt = now + LINGER_TICKS;
        }
        if (!hasData) {
            return;
        }
        long left = hideAt - now;
        if (left <= 0) {
            return;
        }
        float alpha = BASE_ALPHA * Math.min(1.0f, left / (float) LINGER_TICKS);
        int av = ((int) (alpha * 255) & 0xFF) << 24;

        Font font = mc.font;
        int innerW = WIDTH - PAD * 2;

        // pre-wrap each detail so the panel sizes to real content height.
        List<List<FormattedCharSequence>> details = new ArrayList<>();
        int contentH = entries.isEmpty() ? NAME_LINE_H : 0;
        for (ScryEntry e : entries) {
            List<FormattedCharSequence> dl = e.detail().getString().isEmpty() ? List.of()
                    : font.split(e.detail(), innerW - 6);
            details.add(dl);
            contentH += NAME_LINE_H + 2 + dl.size() * DETAIL_LINE_H;
        }

        int headerH = 19;
        int height = headerH + contentH + PAD;
        int x = g.guiWidth() - WIDTH - 8;
        int y = (g.guiHeight() - height) / 2;

        // translucent glass panel: a vertical gradient body inside a thin arcane border.
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, av | FRAME);
        g.fillGradient(x, y, x + WIDTH, y + height, av | GLASS_TOP, av | GLASS_BOT);

        // header + a soft rule beneath.
        g.drawString(font, Component.literal(clip(font, "✦ Scrying: " + title, innerW)), x + PAD, y + 6, av | HEADER, false);
        g.fill(x + PAD, y + 16, x + WIDTH - PAD, y + 17, (av >>> 1 & 0x7F000000) | FRAME);

        int ry = y + headerH;
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("witchmod.scrying.none"), x + PAD, ry, av | INK_SOFT, false);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            ScryEntry e = entries.get(i);
            int colour = e.kind() == 1 ? BLESS : CURSE;
            String timer = e.seconds() + "s";
            int timerW = font.width(timer);
            // coloured tag + clipped name (room left for the timer) + timer on the right.
            g.fill(x + PAD, ry + 1, x + PAD + 4, ry + 9, av | colour);
            String name = clip(font, e.name(), innerW - 10 - timerW - 4);
            g.drawString(font, Component.literal(name), x + PAD + 8, ry, av | colour, false);
            g.drawString(font, Component.literal(timer), x + WIDTH - PAD - timerW, ry, av | INK_SOFT, false);
            ry += NAME_LINE_H;
            for (FormattedCharSequence line : details.get(i)) {
                g.drawString(font, line, x + PAD + 8, ry, av | INK_SOFT, false);
                ry += DETAIL_LINE_H;
            }
            ry += 2;
        }
    }

    /** trim {@code s} with an ellipsis until it fits {@code maxW} pixels. */
    private static String clip(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        while (!s.isEmpty() && font.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
