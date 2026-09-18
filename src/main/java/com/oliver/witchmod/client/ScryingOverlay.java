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

import com.oliver.witchmod.network.WitchModNetwork.ScryEntry;

/**
 * the Scrying Mirror result — a styled side panel listing the revealed curses/blessings with coloured names,
 * timers and the extra "specifics" some effects expose. Styled to match the Compendium (a parchment page with
 * a brown frame + ink text); long names are clipped and long details WRAP so nothing trails off the edge. It
 * fades away after a few seconds.
 */
public final class ScryingOverlay implements LayeredDraw.Layer {
    private static final int SHOW_TICKS = 170;   // ~8.5s
    private static final int FADE_TICKS = 25;
    // palette lifted from the Compendium so the two UIs read as one set (colours below are RRGGBB; the fade
    // alpha is OR'd in at draw time).
    private static final int FRAME = 0x241A12;
    private static final int FRAME_HI = 0x4A3722;
    private static final int PAGE = 0xEFE4C8;
    private static final int INK = 0x3A2E1C;
    private static final int INK_SOFT = 0x7A684A;
    private static final int CURSE = 0xB964CE;    // purple
    private static final int BLESS = 0xE3B23C;    // gold

    private static final int WIDTH = 196;
    private static final int PAD = 8;
    private static final int NAME_LINE_H = 11;
    private static final int DETAIL_LINE_H = 9;

    private static String title = "";
    private static List<ScryEntry> entries = List.of();
    private static long endTick;

    public static void receive(String newTitle, List<ScryEntry> newEntries) {
        Minecraft mc = Minecraft.getInstance();
        title = newTitle;
        entries = newEntries;
        endTick = (mc.level == null ? 0 : mc.level.getGameTime()) + SHOW_TICKS;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) {
            return;
        }
        long left = endTick - mc.level.getGameTime();
        if (left <= 0) {
            return;
        }
        float alpha = Math.min(1.0f, left / (float) FADE_TICKS);
        int av = ((int) (alpha * 255) & 0xFF) << 24;

        Font font = mc.font;
        int innerW = WIDTH - PAD * 2;

        // pre-wrap each detail so we can size the panel to the real content height (accounts for long text).
        List<List<FormattedCharSequence>> details = new ArrayList<>();
        int contentH = 0;
        for (ScryEntry e : entries) {
            List<FormattedCharSequence> dl = e.detail().isEmpty() ? List.of()
                    : font.split(detailComponent(e), innerW - 6);
            details.add(dl);
            contentH += NAME_LINE_H + 2 + dl.size() * DETAIL_LINE_H;
        }
        if (entries.isEmpty()) {
            contentH = NAME_LINE_H;
        }

        int headerH = 20;
        int height = headerH + contentH + PAD;
        int x = g.guiWidth() - WIDTH - 8;
        int y = (g.guiHeight() - height) / 2;

        // parchment page inside a two-tone brown frame (matches the Compendium's book).
        g.fill(x - 2, y - 2, x + WIDTH + 2, y + height + 2, av | FRAME);
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, av | FRAME_HI);
        g.fill(x, y, x + WIDTH, y + height, av | PAGE);

        // header — a clipped title so it never overruns the frame, with an ink rule beneath.
        String header = "✦ Scrying: " + title;
        g.drawString(font, Component.literal(clip(font, header, innerW)), x + PAD, y + 7, av | INK, false);
        g.fill(x + PAD, y + 17, x + WIDTH - PAD, y + 18, av | 0x33000000 | INK_SOFT);

        int ry = y + headerH;
        if (entries.isEmpty()) {
            g.drawString(font, Component.literal("Nothing unusual."), x + PAD + 2, ry, av | INK_SOFT, false);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            ScryEntry e = entries.get(i);
            int colour = e.kind() == 1 ? BLESS : CURSE;
            String timer = e.seconds() + "s";
            int timerW = font.width(timer);
            // coloured tag square + clipped name (leaves room for the timer) + timer on the right.
            g.fill(x + PAD, ry + 1, x + PAD + 4, ry + 9, av | colour);
            String name = clip(font, e.name(), innerW - 10 - timerW - 4);
            g.drawString(font, Component.literal(name), x + PAD + 8, ry, av | colour, false);
            g.drawString(font, Component.literal(timer), x + WIDTH - PAD - timerW, ry, av | INK_SOFT, false);
            ry += NAME_LINE_H;
            // wrapped specifics (Allergic diet, Sonar next-ping, Stick Drift direction, …).
            for (FormattedCharSequence line : details.get(i)) {
                g.drawString(font, line, x + PAD + 8, ry, av | INK_SOFT, false);
                ry += DETAIL_LINE_H;
            }
            ry += 2;
        }
    }

    /** A detail beginning with '@' is a lang KEY (editable flavour text), otherwise a literal. */
    private static Component detailComponent(ScryEntry e) {
        return e.detail().startsWith("@")
                ? Component.translatable(e.detail().substring(1))
                : Component.literal(e.detail());
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
