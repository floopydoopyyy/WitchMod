package com.oliver.witchmod.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the Chat blessing's personal Twitch-chat overlay — a scrolling purple panel down the left side. Everything
 * is <b>server-driven</b> ({@code ChatLinePayload}): the server reacts to your gameplay, tracks an
 * entertainment score, sub count and hype level, and picks each line/username/kind. This layer buffers what it
 * receives and renders it Twitch-style — a LIVE header with a live viewer count + hype-train bar, chatter
 * badges, and highlighted banners for subs / donations / raids.
 */
public final class ChatOverlayLayer implements LayeredDraw.Layer {
    private static final int MAX_MESSAGES = 16;
    private static final int MAX_WRAPPED_LINES = 18;
    private static final int PANEL_WIDTH = 158;

    // message kinds pushed by the server.
    private static final int KIND_SUB = 1, KIND_DONATION = 2, KIND_RAID = 3, KIND_HYPE = 4;

    private static final int EMOTE_SIZE = 14, GIF_SIZE = 20; // on-screen px

    private record Line(String username, String message, int color, int kind) {}

    /** A laid-out chat entry: wrapped TEXT ({@code text != null}) or an emote/gif IMAGE ({@code imgType} 'E'/'G'). */
    private record RenderBlock(int kind, int height, List<FormattedCharSequence> text, char imgType, Line line) {}

    private static final Deque<Line> MESSAGES = new ArrayDeque<>();
    private static double displayedViewers = -1; // eased toward the live target for a natural wiggle

    // emote textures (static 128x128) and gif sheets (vertical, square frames): name -> {frameSize, frameCount, sheetHeight}.
    private static final Map<String, ResourceLocation> EMOTE_TEX = new HashMap<>();
    private static final Map<String, ResourceLocation> GIF_TEX = new HashMap<>();
    private static final Map<String, int[]> GIF_META = new HashMap<>();
    static {
        for (String e : new String[]{"kappa", "kekw", "lul", "pog", "sadge"}) {
            EMOTE_TEX.put(e, ResourceLocation.fromNamespaceAndPath("witchmod", "textures/gui/chat/emotes/" + e + ".png"));
        }
        registerGif("dealwithit", 256, 26, 6656);
        registerGif("stevedance", 128, 21, 2688);
        registerGif("trolldance", 256, 10, 2560);
    }

    private static void registerGif(String name, int frameSize, int frameCount, int sheetHeight) {
        GIF_TEX.put(name, ResourceLocation.fromNamespaceAndPath("witchmod", "textures/gui/chat/gifs/" + name + ".png"));
        GIF_META.put(name, new int[]{frameSize, frameCount, sheetHeight});
    }

    /** called from the network handler when the server pushes a chat line. */
    public static void receive(String username, String message, int color, int kind) {
        MESSAGES.addLast(new Line(username, message, color, kind));
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.removeFirst();
        }
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player.getData(WitchModAttachments.CHAT_OVERLAY) < 0) {
            MESSAGES.clear();
            displayedViewers = -1;
            return; // Chat blessing not active
        }
        Font font = mc.font;
        int subs = player.getData(WitchModAttachments.CHAT_SUBS);
        int hype = player.getData(WitchModAttachments.CHAT_HYPE); // 0..100

        // --- Live viewer count: EXPONENTIAL base->max with hype (tiny when near-dead, explosive at the top),
        // eased with a subtle wiggle so it feels alive.
        double hype01 = hype / 100.0;
        double vBase = Math.max(1.0, Config.CHAT_VIEWER_BASE.get());
        double vMax = Math.max(vBase, Config.CHAT_VIEWER_MAX.get());
        double target = vBase * Math.pow(vMax / vBase, hype01);
        double wiggle = Math.sin(System.currentTimeMillis() / 900.0) * Math.max(1.0, target * 0.03);
        target += wiggle;
        boolean dead = hype01 <= Config.CHAT_DEAD_THRESHOLD.get();
        if (displayedViewers < 0) {
            displayedViewers = target;
        } else {
            displayedViewers += (target - displayedViewers) * 0.06;
        }
        int viewers = Math.max(0, (int) Math.round(displayedViewers));

        // --- Build the message blocks (text OR emote/gif image), newest at the bottom.
        int textWidth = PANEL_WIDTH - 8;
        int lineHeight = font.lineHeight + 1;
        List<RenderBlock> blocks = new ArrayList<>();
        for (Line line : MESSAGES) {
            char token = tokenType(line.message());
            if (token != 0) {
                int size = token == 'G' ? GIF_SIZE : EMOTE_SIZE;
                blocks.add(new RenderBlock(line.kind(), size + 3, null, token, line));
            } else {
                MutableComponent c = Component.empty();
                String badge = badgeText(line);
                if (badge != null) {
                    c.append(Component.literal(badge + " ").withColor(badgeColor(line)));
                }
                c.append(Component.literal(line.username()).withColor(line.color()))
                        .append(Component.literal(": ").withColor(0x9A8AAE))
                        .append(Component.literal(line.message()).withColor(0xEDE6F7));
                List<FormattedCharSequence> wrapped = font.split(c, textWidth);
                blocks.add(new RenderBlock(line.kind(), wrapped.size() * lineHeight, wrapped, (char) 0, line));
            }
        }
        // trim from the top until the total body height fits.
        int maxBody = MAX_WRAPPED_LINES * lineHeight;
        int bodyHeight = blocks.stream().mapToInt(RenderBlock::height).sum();
        while (bodyHeight > maxBody && !blocks.isEmpty()) {
            bodyHeight -= blocks.get(0).height();
            blocks.remove(0);
        }

        int headerHeight = lineHeight + 2;
        int hypeBarHeight = 5;
        int panelHeight = headerHeight + hypeBarHeight + 3 + bodyHeight + 4;
        int x = 4;
        int y = g.guiHeight() / 6;

        // --- Panel background.
        g.fill(x - 3, y - 3, x + PANEL_WIDTH, y + panelHeight, 0xD21A0F2B);
        g.fill(x - 3, y - 3, x + PANEL_WIDTH, y - 2, 0x66FFFFFF); // top hairline

        // --- Header: LIVE dot, viewer count, subs.
        MutableComponent header = Component.literal(dead ? "○ " : "● ").withColor(dead ? 0x6B6B6B : 0xFF3B3B)
                .append(Component.literal(dead ? "OFFLINE?" : "LIVE").withColor(dead ? 0x8A8A8A : 0xFFFFFF))
                .append(Component.literal("  👁 " + formatCount(viewers)).withColor(0xE0D4F5))
                .append(Component.literal("  " + formatCount(subs) + " subs").withColor(0xB79CE8));
        g.drawString(font, header, x, y, 0xFFFFFF);

        // --- Hype bar.
        int barY = y + headerHeight;
        int barW = PANEL_WIDTH - 2;
        g.fill(x, barY, x + barW, barY + hypeBarHeight, 0xAA0E0A17); // well
        int fill = (int) (barW * hype01);
        boolean train = hype >= 80;
        int hypeColor = train ? 0xFFFF5FA2 : (hype >= 45 ? 0xFF9147FF : 0xFF5A3B8C);
        g.fill(x, barY, x + Math.max(1, fill), barY + hypeBarHeight, dead ? 0xFF3A2E4A : hypeColor);
        String barLabel = dead ? "💀 CHAT IS DEAD" : (train ? "🔥 HYPE TRAIN" : null);
        if (barLabel != null) {
            int lw = font.width(barLabel);
            g.drawString(font, barLabel, x + (barW - lw) / 2, barY - 1, dead ? 0x9A8AAE : 0xFFFFFF, false);
        }

        // --- Messages.
        int ly = barY + hypeBarHeight + 3;
        for (RenderBlock b : blocks) {
            int bg = highlightBg(b.kind());
            if (bg != 0) {
                g.fill(x - 2, ly - 1, x + PANEL_WIDTH - 1, ly + b.height() - 2, bg);
                g.fill(x - 2, ly - 1, x - 1, ly + b.height() - 2, accentColor(b.kind())); // left accent stripe
            }
            if (b.text() != null) {
                for (FormattedCharSequence seq : b.text()) {
                    g.drawString(font, seq, x, ly, 0xFFFFFF);
                    ly += lineHeight;
                }
            } else {
                drawImageLine(g, font, b, x, ly);
                ly += b.height();
            }
        }
    }

    // --- Emote / gif rendering ------------------------------------------------------------------------------

    private static char tokenType(String msg) {
        return msg.length() >= 2 && msg.charAt(0) == '' ? msg.charAt(1) : 0;
    }

    private static void drawImageLine(GuiGraphics g, Font font, RenderBlock b, int x, int y) {
        Line line = b.line();
        int size = b.imgType() == 'G' ? GIF_SIZE : EMOTE_SIZE;
        String name = line.message().substring(2);
        String user = line.username();
        int nameY = y + (size - font.lineHeight) / 2;
        g.drawString(font, Component.literal(user).withColor(line.color()), x, nameY, 0xFFFFFF);
        int imgX = x + font.width(user + " ") + 2;
        if (b.imgType() == 'G') {
            int[] meta = GIF_META.get(name);
            var tex = GIF_TEX.get(name);
            if (meta != null && tex != null) {
                int frame = (int) ((System.currentTimeMillis() / 70) % meta[1]);
                g.blit(tex, imgX, y, size, size, 0.0F, (float) (frame * meta[0]), meta[0], meta[0], meta[0], meta[2]);
            }
        } else {
            var tex = EMOTE_TEX.get(name);
            if (tex != null) {
                g.blit(tex, imgX, y, size, size, 0.0F, 0.0F, 128, 128, 128, 128);
            }
        }
    }

    // --- Styling helpers -----------------------------------------------------------------------------------

    private static String badgeText(Line line) {
        return switch (line.kind()) {
            case KIND_SUB -> "[SUB]";
            case KIND_DONATION -> "[BITS]";
            case KIND_RAID -> "[RAID]";
            default -> {
                // give some ordinary chatters flair, stable by name.
                int h = Math.floorMod(line.username().hashCode(), 12);
                yield switch (h) {
                    case 0, 1 -> "[MOD]";
                    case 2 -> "[VIP]";
                    case 3, 4 -> "[SUB]";
                    default -> null;
                };
            }
        };
    }

    private static int badgeColor(Line line) {
        return switch (line.kind()) {
            case KIND_DONATION -> 0xF1C40F;
            case KIND_RAID -> 0xFF5555;
            default -> {
                String b = badgeText(line);
                if (b == null) {
                    yield 0xFFFFFF;
                }
                yield switch (b) {
                    case "[MOD]" -> 0x00C853;
                    case "[VIP]" -> 0xE91E8C;
                    default -> 0x9147FF; // SUB
                };
            }
        };
    }

    private static int highlightBg(int kind) {
        return switch (kind) {
            case KIND_SUB -> 0x66512D8A;
            case KIND_DONATION -> 0x55806000;
            case KIND_RAID -> 0x55801515;
            case KIND_HYPE -> 0x449147FF;
            default -> 0;
        };
    }

    private static int accentColor(int kind) {
        return switch (kind) {
            case KIND_SUB -> 0xFF9147FF;
            case KIND_DONATION -> 0xFFF1C40F;
            case KIND_RAID -> 0xFFFF5555;
            case KIND_HYPE -> 0xFFC792EA;
            default -> 0;
        };
    }

    /** 1234 -> "1.2K" for the header counters. */
    private static String formatCount(int n) {
        if (n < 1000) {
            return Integer.toString(n);
        }
        double k = n / 1000.0;
        return (Math.round(k * 10) / 10.0) + "K";
    }
}
