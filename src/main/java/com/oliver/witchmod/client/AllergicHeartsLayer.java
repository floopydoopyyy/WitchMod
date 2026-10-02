package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.effects.curses.AllergicReaction;

/**
 * allergic reaction: draws the hearts the reaction took as green, dead hearts at the end of the health bar
 * (after any absorption). sits right above vanilla's health layer and reuses its row maths, so it lines up
 * exactly; if the green hearts spill onto a new row, the armour bar is pushed up to make room.
 */
public final class AllergicHeartsLayer implements LayeredDraw.Layer {
    private static final ResourceLocation CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation ALLERGIC = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "hud/heart/allergic");

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.options.hideGui || player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        int lost = lostHearts(player);
        if (lost <= 0) {
            return;
        }
        // mirror Gui.renderHealthLevel's layout (it has already added its rows to leftHeight).
        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), Mth.ceil(player.getHealth()));
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int rows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int leftHeightBefore = mc.gui.leftHeight - ((rows - 1) * rowHeight + 10);
        int x = guiGraphics.guiWidth() / 2 - 91;
        int y = guiGraphics.guiHeight() - leftHeightBefore;

        int start = Mth.ceil(maxHealth / 2.0) + Mth.ceil(absorption / 2.0);
        for (int n = 0; n < lost; n++) {
            int index = start + n;
            int hx = x + (index % 10) * 8;
            int hy = y - (index / 10) * rowHeight;
            guiGraphics.blitSprite(CONTAINER, hx, hy, 9, 9);
            guiGraphics.blitSprite(ALLERGIC, hx, hy, 9, 9);
        }
        int usedRows = Mth.ceil((start + lost) / 10.0F);
        if (usedRows > rows) {
            mc.gui.leftHeight += (usedRows - rows) * rowHeight;
        }
    }

    /** hearts the reaction's max-health modifier is currently taking away. */
    private static int lostHearts(LocalPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return 0;
        }
        AttributeModifier ours = health.getModifier(AllergicReaction.HEALTH_ID);
        if (ours == null || ours.amount() >= 0.0) {
            return 0;
        }
        double now = health.getValue();
        double without = now / (1.0 + ours.amount());
        return Math.max(0, Mth.ceil(without / 2.0) - Mth.ceil(now / 2.0));
    }
}
