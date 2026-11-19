package com.oliver.witchmod.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;

import com.oliver.witchmod.ClientConfig;
import com.oliver.witchmod.blocks.WitchModFluids;

/** under holy water: a soft white radiance over the view, on top of the close white fog (see WitchModClient). */
public final class HolyWaterOverlay implements LayeredDraw.Layer {
    /** whether the camera's under holy water (the fog uses the same test). */
    public static boolean submerged(Minecraft mc) {
        if (mc.level == null) {
            return false;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        BlockPos pos = camera.getBlockPosition();
        FluidState state = mc.level.getFluidState(pos);
        return state.getFluidType() == WitchModFluids.PURIFYING_WATER_TYPE.get()
                && camera.getPosition().y < pos.getY() + state.getHeight(mc.level, pos);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        double glow = ClientConfig.holyWaterGlow();
        if (glow <= 0.0 || !submerged(mc)) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int alpha = (int) Math.round(255.0 * glow);
        graphics.fill(0, 0, w, h, alpha << 24 | 0xF2F8FF);
        // a little brighter still at the edges, as if lit from all round
        int edge = Math.max(8, Math.min(w, h) / 8);
        int rim = (int) Math.round(255.0 * Math.min(1.0, glow * 0.6));
        graphics.fillGradient(0, 0, w, edge, rim << 24 | 0xFFFFFF, 0x00FFFFFF);
        graphics.fillGradient(0, h - edge, w, h, 0x00FFFFFF, rim << 24 | 0xFFFFFF);
    }
}
