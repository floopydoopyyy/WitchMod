package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Carelessness (client half): while the curse is active, the vanilla health layer is cancelled and every
 * heart is drawn IDENTICAL and black — so you can't read your health at all. The real value is untouched;
 * you're just blind to it. Off the synced {@link WitchModAttachments#CARELESSNESS_ACTIVE} flag.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class CarelessnessHud {
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");

    private CarelessnessHud() {}

    @SubscribeEvent
    static void onRenderHealth(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.getData(WitchModAttachments.CARELESSNESS_ACTIVE) < 0) {
            return;
        }
        // Match vanilla's own survival-HUD gate (no hearts in creative/spectator).
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        event.setCanceled(true); // take over the health bar

        GuiGraphics g = event.getGuiGraphics();
        int hearts = Math.min(100, Mth.ceil(player.getMaxHealth() / 2.0F)); // cap absurd max-health rows
        int left = g.guiWidth() / 2 - 91;
        int top = g.guiHeight() - 39;
        for (int i = 0; i < hearts; i++) {
            int x = left + (i % 10) * 8;
            int y = top - (i / 10) * 10;
            g.blitSprite(CONTAINER, x, y, 9, 9);
            g.setColor(0F, 0F, 0F, 1F);
            g.blitSprite(FULL, x, y, 9, 9); // the red heart, tinted pure black
            g.setColor(1F, 1F, 1F, 1F);
        }
    }
}
