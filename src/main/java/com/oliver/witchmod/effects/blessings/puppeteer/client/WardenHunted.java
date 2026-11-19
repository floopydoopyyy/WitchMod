package com.oliver.witchmod.effects.blessings.puppeteer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.oliver.witchmod.ClientConfig;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * puppeteer, client: being hunted by a warden puppet. noticed, you see pips filling at the top of your screen (how
 * close it is to marking you). marked: a sting, a red flash, your own heart racing, the edges of your view bleeding red,
 * and the countdown — every sound you make tops it back up.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class WardenHunted {
    private WardenHunted() {}

    private static int points;
    private static int markTicks;
    private static int markMax = 1;
    /** when we were last told (the countdown runs on between), and when the countdown last grew (it shakes). */
    private static long told;
    private static long grew = Long.MIN_VALUE / 2;
    private static long flashUntil;
    private static int beat;

    public static void receive(WitchModNetwork.WardenHuntedPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (p.markTicks() > left(now) + 4) {
            grew = now;
        }
        points = p.points();
        markTicks = p.markTicks();
        markMax = Math.max(1, p.markMax());
        told = now;
        if (p.fresh()) {
            flashUntil = now + 10;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F, 0.6F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_TENDRIL_CLICKS, 1.2F, 0.9F));
        }
    }

    private static int left(long now) {
        return (int) Math.max(0L, markTicks - (now - told));
    }

    /** your heart, racing while you're marked (faster as the mark grows). */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            points = 0;
            markTicks = 0;
            return;
        }
        int left = left(mc.level.getGameTime());
        if (left <= 0) {
            beat = 0;
            return;
        }
        int pace = 16 - (int) (8.0F * left / markMax);
        if (++beat >= pace) {
            beat = 0;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.7F, 1.25F));
        }
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || points <= 0 && markTicks <= 0) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        int left = left(now);
        int w = g.guiWidth();
        int h = g.guiHeight();
        float intensity = (float) ClientConfig.screenShaderIntensity();
        if (left > 0) {
            // the edges bleed red, pulsing with the heartbeat
            float pulse = 0.5F + 0.5F * Mth.sin((mc.player.tickCount + pt) * 0.45F);
            int edge = (int) ((40 + 60 * pulse) * intensity);
            int band = h / 4;
            int red = 0x00B00010;
            g.fillGradient(0, 0, w, band, (edge << 24) | red, red);
            g.fillGradient(0, h - band, w, h, red, (edge << 24) | red);
            // the flash of being marked
            if (flashUntil > now) {
                float a = (flashUntil - now - pt) / 10.0F * (ClientConfig.reduceFlashing() ? 0.2F : 0.55F) * intensity;
                g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0.0F, 1.0F) * 255) << 24) | 0x00FF1A10);
            }
            // the countdown: a red bar that drains, and shakes when it's topped up
            float shake = now - grew < 8 ? (8 - (now - grew)) / 8.0F : 0.0F;
            int dx = (int) (Mth.sin((mc.player.tickCount + pt) * 3.1F) * 3.0F * shake);
            int barW = 120;
            int x = (w - barW) / 2 + dx;
            int y = 18;
            Component title = Component.translatable("witchmod.puppeteer.warden_heard");
            g.drawCenteredString(mc.font, title, w / 2 + dx, y - 11, 0xFF3020);
            g.fill(x - 1, y - 1, x + barW + 1, y + 4, 0xB0000000);
            float share = Mth.clamp((left - pt) / markMax, 0.0F, 1.0F);
            g.fill(x, y, x + Math.round(barW * share), y + 3, 0xFFE0281E);
            String secs = String.format(java.util.Locale.ROOT, "%.1fs", Math.max(0.0F, (left - pt) / 20.0F));
            g.drawCenteredString(mc.font, secs, w / 2 + dx, y + 7, 0xFFB0A0);
            return;
        }
        // noticed: pips filling towards a mark
        int need;
        try {
            need = com.oliver.witchmod.Config.PUPPETEER_WARDEN_MARK_POINTS.get();
        } catch (IllegalStateException e) {
            need = 3;
        }
        Component title = Component.translatable("witchmod.puppeteer.warden_noticed");
        g.drawCenteredString(mc.font, title, w / 2, 7, 0x7FD8E8);
        int size = 6;
        int gap = 5;
        int total = need * size + (need - 1) * gap;
        int x = (w - total) / 2;
        for (int i = 0; i < need; i++) {
            int px = x + i * (size + gap);
            boolean lit = i < points;
            g.fill(px, 19, px + size, 19 + size, lit ? 0xFF2FD8E0 : 0x60204050);
        }
    }
}
