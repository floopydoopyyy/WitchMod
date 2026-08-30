package com.oliver.witchmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.oliver.witchmod.WitchMod;

/**
 * Bedrock Moment (Fake BSOD): a full-screen fake blue-screen. The chosen image ROLLS DOWN from the top like a
 * real BSOD painting itself in, then holds. Dismissed by {@link BedrockClientBugs} when the synced window
 * ends (not by the player) — no buttons, no Esc; the world keeps ticking behind it.
 */
public final class FakeBsodScreen extends Screen {
    // 0 = standard, 1..3 = the funny ones. Native sizes so the stretch-to-fullscreen blit maps cleanly.
    private static final ResourceLocation[] TEX = {
        tex("bsod_standard"), tex("bsod_funny1"), tex("bsod_funny2"), tex("bsod_funny3")
    };
    private static final int[][] DIM = {{1860, 908}, {1861, 913}, {1853, 910}, {1856, 908}};
    private static final long ROLL_MS = 550L; // how long the top-down reveal takes

    private final int variant;
    private long shownAtMs = -1L;

    public FakeBsodScreen(int variant) {
        super(CommonComponents.EMPTY);
        this.variant = Mth.clamp(variant, 0, TEX.length - 1);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/" + name + ".png");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (shownAtMs < 0) {
            shownAtMs = System.currentTimeMillis();
        }
        // Behind everything is black — the machine is "gone".
        g.fill(0, 0, this.width, this.height, 0xFF000000);

        float roll = Mth.clamp((System.currentTimeMillis() - shownAtMs) / (float) ROLL_MS, 0.0F, 1.0F);
        int revealY = (int) (this.height * roll);
        if (revealY <= 0) {
            return;
        }
        g.enableScissor(0, 0, this.width, revealY);
        g.fill(0, 0, this.width, this.height, 0xFF0000AA); // classic blue, in case the image is slow to bind
        g.blit(TEX[variant], 0, 0, this.width, this.height, 0.0F, 0.0F, DIM[variant][0], DIM[variant][1], DIM[variant][0], DIM[variant][1]);
        g.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
