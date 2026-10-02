package com.oliver.witchmod.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;

/**
 * the small button hung just outside a book panel's top-right corner (on the dimmed backdrop, so it never covers
 * the page): a close X, or a back arrow. each texture is a 12x24 editable sheet — top half normal, bottom half hovered.
 */
public final class PanelCornerButton extends Button {
    public static final int SIZE = 12;
    /** gap between the panel's outer frame and the button. */
    private static final int GAP = 2;

    public static final ResourceLocation CLOSE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/close_button.png");
    public static final ResourceLocation BACK =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/back_button.png");

    private final ResourceLocation texture;

    private PanelCornerButton(int x, int y, ResourceLocation texture, Component label, OnPress onPress) {
        super(x, y, SIZE, SIZE, label, onPress, DEFAULT_NARRATION);
        this.texture = texture;
        setTooltip(Tooltip.create(label));
    }

    /**
     * placed beside the top-right of a panel whose outer frame is {@code frame} px thick. kept on-screen when the
     * window is too narrow (it then tucks just above the frame instead).
     */
    public static PanelCornerButton at(int screenWidth, int panelLeft, int panelTop, int panelWidth, int frame,
                                       ResourceLocation texture, Component label, OnPress onPress) {
        int x = panelLeft + panelWidth + frame + GAP;
        int y = panelTop - frame;
        if (x + SIZE > screenWidth) {
            x = panelLeft + panelWidth + frame - SIZE;
            y = panelTop - frame - GAP - SIZE;
        }
        return new PanelCornerButton(x, Math.max(0, y), texture, label, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.blit(texture, getX(), getY(), 0, isHoveredOrFocused() ? SIZE : 0, SIZE, SIZE, SIZE, SIZE * 2);
    }
}
