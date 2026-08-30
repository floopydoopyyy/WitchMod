package com.oliver.witchmod.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import com.oliver.witchmod.network.WitchModNetwork.ScryEntry;

/**
 * The Scrying Mirror result — a styled side panel listing the revealed curses/blessings with coloured names,
 * timers and the extra "specifics" some effects expose. Far nicer than the old chat dump; it fades away after
 * a few seconds.
 */
public final class ScryingOverlay implements LayeredDraw.Layer {
    private static final int SHOW_TICKS = 170;   // ~8.5s
    private static final int FADE_TICKS = 25;
    private static final int CURSE = 0xC57BE8;    // purple
    private static final int BLESS = 0xFFD24A;    // gold
    private static final int PANEL = 0x000000;
    private static final int BORDER = 0x66FFFFFF;

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
        long now = mc.level.getGameTime();
        long left = endTick - now;
        if (left <= 0) {
            return;
        }
        float alpha = Math.min(1.0f, left / (float) FADE_TICKS);
        int a = ((int) (alpha * 255) & 0xFF) << 24;

        Font font = mc.font;
        int rowH = entries.isEmpty() ? 12 : 22;
        int width = 176;
        int height = 20 + Math.max(1, entries.size()) * rowH + 6;
        int x = g.guiWidth() - width - 8;
        int y = (g.guiHeight() - height) / 2;

        // Panel + border.
        g.fill(x, y, x + width, y + height, (0xCC << 24) | PANEL);
        g.fill(x, y, x + width, y + 1, BORDER & (a | 0x00FFFFFF));
        g.fill(x, y + height - 1, x + width, y + height, BORDER & (a | 0x00FFFFFF));
        g.fill(x, y, x + 1, y + height, BORDER & (a | 0x00FFFFFF));
        g.fill(x + width - 1, y, x + width, y + height, BORDER & (a | 0x00FFFFFF));

        // Header.
        g.drawString(font, Component.literal("✦ Scrying: " + title), x + 8, y + 6, a | 0xCFA0FF, true);
        g.fill(x + 6, y + 17, x + width - 6, y + 18, a | 0x40FFFFFF);

        int ry = y + 22;
        if (entries.isEmpty()) {
            g.drawString(font, Component.literal("Nothing unusual."), x + 10, ry, a | 0xAAAAAA, false);
            return;
        }
        for (ScryEntry e : entries) {
            int colour = e.kind() == 1 ? BLESS : CURSE;
            // Coloured tag square + name + timer.
            g.fill(x + 8, ry + 1, x + 12, ry + 9, a | colour);
            g.drawString(font, Component.literal(e.name()), x + 16, ry, a | colour, false);
            String timer = e.seconds() + "s";
            g.drawString(font, Component.literal(timer), x + width - 8 - font.width(timer), ry, a | 0xB0B0B0, false);
            // Specifics line (Allergic diet, Sonar next-ping, Stick Drift direction, …). A detail beginning
            // with '@' is a lang KEY, so its flavour text is editable in the lang file.
            if (!e.detail().isEmpty()) {
                Component detail = e.detail().startsWith("@")
                        ? Component.translatable(e.detail().substring(1))
                        : Component.literal(e.detail());
                g.drawString(font, Component.literal("  ").append(detail), x + 16, ry + 10, a | 0x9A9A9A, false);
            }
            ry += rowH;
        }
    }
}
