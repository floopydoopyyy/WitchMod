package com.oliver.witchmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.WitchMod;

/** the Organised stash button: a 16x16 editable texture over a small background, shown on the inventory screen. */
@OnlyIn(Dist.CLIENT)
public final class OrganisedButton extends Button {
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/organised_button.png");

    public OrganisedButton(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.literal("Organised stash"), onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(getX() - 1, getY() - 1, getX() + 17, getY() + 17, isHoveredOrFocused() ? 0xC0000000 : 0x90000000);
        g.blit(TEX, getX(), getY(), 0, 0, 16, 16, 16, 16);
    }
}
