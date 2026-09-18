package com.oliver.witchmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.WitchMod;

/**
 * bedrock Moment (Marketplace popup): a fake in-your-face "Marketplace" ad that takes over the screen and can
 * ONLY be dismissed by clicking its little ✕ with the cursor (Esc won't save you). The game keeps running
 * behind it — so fumbling for the X while a creeper closes in is entirely your problem.
 *
 * <p>The ad artwork is a plain 256x256 PNG at {@code assets/witchmod/textures/gui/marketplace/ad_<n>.png}.
 */
@OnlyIn(Dist.CLIENT)
public final class MarketplaceAdScreen extends Screen {
    private static final int PANEL = 210;   // on-screen size of the ad
    private static final int X_SIZE = 18;   // close-button size

    private final ResourceLocation texture;

    public MarketplaceAdScreen(int adIndex) {
        super(Component.literal("Marketplace"));
        this.texture = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/marketplace/ad_" + adIndex + ".png");
    }

    private int panelX() {
        return (this.width - PANEL) / 2;
    }

    private int panelY() {
        return (this.height - PANEL) / 2;
    }

    private int xX() {
        return panelX() + PANEL - X_SIZE / 2;
    }

    private int xY() {
        return panelY() - X_SIZE / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int px = panelX();
        int py = panelY();
        // dim the world behind, and give the ad a drop shadow / border.
        g.fill(0, 0, this.width, this.height, 0x66000000);
        g.fill(px - 3, py - 3, px + PANEL + 3, py + PANEL + 3, 0xFF161616);
        g.blit(texture, px, py, PANEL, PANEL, 0.0F, 0.0F, 256, 256, 256, 256);
        g.drawString(this.font, "SPONSORED", px + 2, py - 11, 0xFFBBBBBB, false);

        // the tiny, deliberately-awkward close button.
        int xx = xX();
        int xy = xY();
        boolean hover = mouseX >= xx && mouseX <= xx + X_SIZE && mouseY >= xy && mouseY <= xy + X_SIZE;
        g.fill(xx, xy, xx + X_SIZE, xy + X_SIZE, hover ? 0xFFFF5555 : 0xFFCC2222);
        g.fill(xx, xy, xx + X_SIZE, xy + 1, 0x66FFFFFF);
        g.drawCenteredString(this.font, "X", xx + X_SIZE / 2, xy + X_SIZE / 2 - 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int xx = xX();
        int xy = xY();
        if (mx >= xx && mx <= xx + X_SIZE && my >= xy && my <= xy + X_SIZE) {
            onClose();
            return true;
        }
        return true; // swallow every other click — no clicking through to the world
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // you MUST use the cursor
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the game keeps running behind it
    }
}
