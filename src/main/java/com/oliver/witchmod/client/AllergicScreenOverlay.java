package com.oliver.witchmod.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.ClientConfig;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.effects.curses.AllergicReaction;

/**
 * allergic reaction: a soft green haze creeping in from the screen edges (the vignette texture), stronger each
 * tier; tier 3 also washes the whole screen faintly green. scaled by the screenShaderIntensity client setting.
 */
public final class AllergicScreenOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/misc/allergic_vignette.png");
    /** vignette strength per tier. */
    private static final float[] EDGE_ALPHA = {0.5F, 0.8F, 1.0F};
    /** tier 3's flat wash over the whole screen. */
    private static final float TIER3_WASH_ALPHA = 0.14F;
    private static final int GREEN = 0x4FB82A;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int tier = AllergicReaction.tierOf(player);
        if (tier <= 0) {
            return;
        }
        float intensity = (float) ClientConfig.screenShaderIntensity();
        if (intensity <= 0.0F) {
            return;
        }
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        // one full-screen pass of the oval haze (texture is linear-filtered via its .mcmeta, so it stretches smoothly).
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, EDGE_ALPHA[tier - 1] * intensity);
        guiGraphics.blit(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, 512, 512, 512, 512);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);

        if (tier >= 3) {
            int alpha = Math.round(TIER3_WASH_ALPHA * intensity * 255.0F);
            guiGraphics.fill(0, 0, width, height, (alpha << 24) | GREEN);
        }
        RenderSystem.disableBlend();
    }
}
