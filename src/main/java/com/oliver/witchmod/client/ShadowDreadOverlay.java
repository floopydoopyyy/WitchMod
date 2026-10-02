package com.oliver.witchmod.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.ClientConfig;
import com.oliver.witchmod.WitchMod;

/**
 * the shadow's dread: a purple vignette that closes in as your own shadow nears, throbbing with each heartbeat.
 * strength comes from {@link ShadowClient#dread()}; scaled by the screenShaderIntensity client setting.
 */
public final class ShadowDreadOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/misc/shadow_vignette.png");
    /** a heartbeat kicks the vignette up by this much, decaying back over a few ticks. */
    private static final float PULSE = 0.35F;
    private static long pulseAt;

    static void pulse() {
        pulseAt = net.minecraft.Util.getMillis();
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        float dread = ShadowClient.dread();
        if (dread <= 0.01F || Minecraft.getInstance().player == null) {
            return;
        }
        float sinceBeat = (net.minecraft.Util.getMillis() - pulseAt) / 250.0F;
        float beat = sinceBeat < 1.0F ? PULSE * (1.0F - sinceBeat) : 0.0F;
        float alpha = Math.min(1.0F, dread * 0.85F + beat * dread) * (float) ClientConfig.screenShaderIntensity();
        if (alpha <= 0.0F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        g.setColor(0.45F, 0.1F, 0.7F, alpha);
        g.blit(VIGNETTE, 0, 0, g.guiWidth(), g.guiHeight(), 0.0F, 0.0F, 512, 512, 512, 512);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
