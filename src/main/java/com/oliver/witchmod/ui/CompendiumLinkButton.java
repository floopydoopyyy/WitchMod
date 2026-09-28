package com.oliver.witchmod.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;

/**
 * the little book button beside the ritual table UI — opens the Compendium's Rituals chapter. 16x16 editable
 * texture ({@code assets/witchmod/textures/gui/container/compendium_button.png}) over a small backing panel.
 */
public final class CompendiumLinkButton extends Button {
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/container/compendium_button.png");

    public CompendiumLinkButton(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.translatable("witchmod.ritual.guide"), onPress, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.translatable("witchmod.ritual.guide")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(getX() - 1, getY() - 1, getX() + 17, getY() + 17, isHoveredOrFocused() ? 0xC0000000 : 0x90000000);
        g.blit(TEX, getX(), getY(), 0, 0, 16, 16, 16, 16);
    }
}
