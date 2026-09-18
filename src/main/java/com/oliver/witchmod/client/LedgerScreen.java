package com.oliver.witchmod.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.oliver.witchmod.network.WitchModNetwork;

/**
 * the Ledger's custom screen: a scrollable list of nearby ritual activity (who cursed/blessed whom, with the
 * modifier used and how long ago), newest first. Opened by right-clicking a Ledger block, populated from a
 * {@link WitchModNetwork.LedgerPayload} the server range-filters. Paper-scribbled entries render as an
 * unreadable obfuscated scrawl.
 */
public final class LedgerScreen extends Screen {
    private record Row(String caster, String target, String effect, String modifier, String result, boolean scribbled) {}

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 208;
    private static final int ROW_H = 23;

    private static final int FRAME = 0xFF241A12;
    private static final int FRAME_HI = 0xFF4A3722;
    private static final int PAGE = 0xFFEFE4C8;
    private static final int PAGE_EDGE = 0xFFD6C39A;
    private static final int INK = 0xFF3A2E1C;
    private static final int INK_SOFT = 0xFF7A684A;
    private static final int PURPLE = 0xFF7A4B8C;
    private static final int GREEN = 0xFF4E8A50;
    private static final int DIM = 0xC8000000;

    private final int range;
    private final List<Row> rows;
    private int scroll;
    private int maxScroll;
    private int listTop, listBottom, listLeft, listRight;

    private LedgerScreen(int range, List<Row> rows) {
        super(Component.literal("The Ledger"));
        this.range = range;
        this.rows = rows;
    }

    public static void open(int range, List<WitchModNetwork.LedgerEntry> entries) {
        Row[] rows = entries.stream()
                .map(e -> new Row(e.caster(), e.target(), e.effect(), e.modifier(), e.result(), e.scribbled()))
                .toArray(Row[]::new);
        Minecraft.getInstance().setScreen(new LedgerScreen(range, List.of(rows)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // no-op: the dim is drawn as border strips in render() so it never lands over the parchment.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Font font = this.font;
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        // dim only OUTSIDE the panel (opaque panel + translucent dim in the same pass would otherwise clash).
        int ox0 = left - 3, oy0 = top - 3, ox1 = left + PANEL_W + 3, oy1 = top + PANEL_H + 3;
        g.fill(0, 0, this.width, oy0, DIM);
        g.fill(0, oy1, this.width, this.height, DIM);
        g.fill(0, oy0, ox0, oy1, DIM);
        g.fill(ox1, oy0, this.width, oy1, DIM);

        g.fill(left - 3, top - 3, left + PANEL_W + 3, top + PANEL_H + 3, FRAME);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, FRAME_HI);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PAGE);

        // header.
        Component title = Component.literal("The Ledger").withStyle(s -> s.withBold(true));
        g.drawString(font, title, left + PANEL_W / 2 - font.width(title) / 2, top + 8, PURPLE, false);
        String sub = "recent activity within " + range + " blocks";
        g.drawString(font, Component.literal(sub), left + PANEL_W / 2 - font.width(sub) / 2, top + 19, INK_SOFT, false);
        g.fill(left + 12, top + 30, left + PANEL_W - 12, top + 31, PAGE_EDGE);

        listLeft = left + 12;
        listRight = left + PANEL_W - 12;
        listTop = top + 36;
        listBottom = top + PANEL_H - 10;

        if (rows.isEmpty()) {
            String none = "Nothing has happened within range.";
            g.drawString(font, Component.literal(none), left + PANEL_W / 2 - font.width(none) / 2, listTop + 20, INK_SOFT, false);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        int viewport = listBottom - listTop;
        int contentH = rows.size() * ROW_H;
        maxScroll = Math.max(0, contentH - viewport);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        g.enableScissor(listLeft - 2, listTop, listRight + 4, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop - scroll + i * ROW_H;
            if (y + ROW_H < listTop || y > listBottom) {
                continue;
            }
            drawRow(g, font, rows.get(i), listLeft, y, listRight - listLeft);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackX = listRight + 1;
            g.fill(trackX, listTop, trackX + 2, listBottom, 0x22000000);
            int thumbH = Math.max(12, (int) ((long) viewport * viewport / contentH));
            int thumbY = listTop + (int) ((long) (viewport - thumbH) * scroll / maxScroll);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAA000000 | (INK & 0xFFFFFF));
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawRow(GuiGraphics g, Font font, Row row, int x, int y, int w) {
        if (row.scribbled()) {
            g.drawString(font, Component.literal("someone did something to someone")
                    .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_GRAY), x, y, 0xFF555555, false);
            g.drawString(font, Component.literal("(scribbled out)").withStyle(ChatFormatting.ITALIC), x, y + 10, INK_SOFT, false);
            g.fill(x, y + ROW_H - 3, x + w, y + ROW_H - 2, 0x11000000);
            return;
        }
        // line 1: caster → target.
        var purple = net.minecraft.network.chat.TextColor.fromRgb(PURPLE & 0xFFFFFF);
        var soft = net.minecraft.network.chat.TextColor.fromRgb(INK_SOFT & 0xFFFFFF);
        net.minecraft.network.chat.MutableComponent who = Component.literal(row.caster()).withStyle(s -> s.withColor(purple))
                .append(Component.literal("  →  ").withStyle(s -> s.withColor(soft)))
                .append(Component.literal(row.target()).withStyle(s -> s.withColor(purple)));
        g.drawString(font, who, x, y, INK, false);

        // line 2: effect [+modifier] · result/age.
        var green = net.minecraft.network.chat.TextColor.fromRgb(GREEN & 0xFFFFFF);
        net.minecraft.network.chat.MutableComponent line = Component.literal(row.effect())
                .withStyle(s -> s.withBold(true));
        if (!row.modifier().isEmpty()) {
            line.append(Component.literal(" +" + row.modifier()).withStyle(s -> s.withColor(green).withBold(false)));
        }
        line.append(Component.literal("  ·  " + row.result()).withStyle(s -> s.withColor(soft).withBold(false)));
        g.drawString(font, line, x, y + 10, INK, false);

        g.fill(x, y + ROW_H - 3, x + w, y + ROW_H - 2, 0x11000000);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (maxScroll > 0 && mx >= listLeft - 2 && mx <= listRight + 4 && my >= listTop && my <= listBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(dy) * ROW_H));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
