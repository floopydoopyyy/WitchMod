package com.oliver.witchmod.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the on-screen "totem" pop played when Last Stand or Immortality saves you — but using the BLESSED effect
 * icon instead of a totem item, washed gold→white. Driven off the synced {@link WitchModAttachments#REVIVE_FLASH_END}
 * tick: the icon bursts up large and fades over the window, mimicking the vanilla totem-of-undying activation.
 */
public final class ReviveFlashOverlay implements LayeredDraw.Layer {
    /** the Blessed status-effect icon (16x16). */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/mob_effect/blessed.png");
    /** total length of the animation, in ticks — matched to the flash window the server sets. */
    public static final int DURATION_TICKS = com.oliver.witchmod.Config.REVIVE_FLASH_TICKS;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long end = player.getData(WitchModAttachments.REVIVE_FLASH_END);
        if (end <= 0L) {
            return;
        }
        float remaining = end - (mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false));
        if (remaining <= 0.0F) {
            return;
        }
        float progress = Mth.clamp(1.0F - remaining / DURATION_TICKS, 0.0F, 1.0F); // 0 -> 1 over the window

        // scale: bursts up fast, then eases larger. Alpha: full for the first half, then fades out.
        float scale = 3.0F + 9.0F * easeOut(Math.min(1.0F, progress * 2.0F));
        float alpha = progress < 0.5F ? 1.0F : 1.0F - (progress - 0.5F) / 0.5F;
        if (alpha <= 0.0F) {
            return;
        }
        // colour drifts gold -> white as it completes.
        float r = 1.0F;
        float g = Mth.lerp(progress, 0.82F, 1.0F);
        float b = Mth.lerp(progress, 0.25F, 0.95F);

        int cx = guiGraphics.guiWidth() / 2;
        int cy = guiGraphics.guiHeight() / 2 - (int) (progress * 20.0F); // drifts up slightly as it fades

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        guiGraphics.setColor(r, g, b, alpha);
        guiGraphics.blit(ICON, -8, -8, 0.0F, 0.0F, 16, 16, 16, 16);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }
}
