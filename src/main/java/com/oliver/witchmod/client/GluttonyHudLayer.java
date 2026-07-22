package com.oliver.witchmod.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Draws Gluttony's hunger display — <b>both rows</b>, not just the extra one. While the curse is active,
 * vanilla's own food bar is cancelled (see {@code ClientCurseHandler}) and this layer renders the whole
 * 40-point bar from the combined total.
 *
 * <p>That takeover exists for a reason: the real vanilla food value is deliberately held one point short of
 * full so that eating stays possible, so drawing the lower row straight from it made the bar permanently
 * look like its first point was missing. Splitting the combined total ourselves puts the shortfall at the
 * TOP of the bar, where it belongs, and removes the flicker as vanilla dips and is topped back up.
 *
 * <p>Both rows use the real vanilla sprites and mirror {@code Gui#renderFood}: the green {@code *_hunger}
 * set under the Hunger effect, and the jitter when saturation is empty.
 */
public final class GluttonyHudLayer implements LayeredDraw.Layer {
    private static final int ICONS = 10;             // 10 drumsticks per row, 2 hunger points each
    private static final int ROW_MAX = ICONS * 2;    // 20 points per row
    private static final int VANILLA_ROW_FROM_BOTTOM = 39; // where vanilla draws the food bar

    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation FOOD_EMPTY_HUNGER = ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
    private static final ResourceLocation FOOD_HALF_HUNGER = ResourceLocation.withDefaultNamespace("hud/food_half_hunger");
    private static final ResourceLocation FOOD_FULL_HUNGER = ResourceLocation.withDefaultNamespace("hud/food_full_hunger");

    /**
     * Whether we're taking over the hunger display this frame. Mirrors vanilla's own visibility rules so
     * cancelling its layer never leaves a gap: hidden GUI, creative/spectator, or riding a living mount
     * (vanilla hides food there to make room for the mount's health).
     */
    public static boolean shouldDrawCombinedBars(Minecraft mc) {
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return false;
        }
        if (mc.player.getVehicle() instanceof LivingEntity) {
            return false;
        }
        return mc.player.getData(WitchModAttachments.GLUTTONY_HUNGER) >= 0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldDrawCombinedBars(mc)) {
            return;
        }
        LocalPlayer player = mc.player;
        int extra = Math.max(0, player.getData(WitchModAttachments.GLUTTONY_HUNGER));
        int combined = player.getFoodData().getFoodLevel() + extra;

        // Split the true total across the rows: the lower row fills first, the upper row is the overflow.
        int lower = Math.min(ROW_MAX, combined);
        int upper = Math.max(0, combined - ROW_MAX);

        int right = guiGraphics.guiWidth() / 2 + 91;   // vanilla food-bar right edge
        int lowerTop = guiGraphics.guiHeight() - VANILLA_ROW_FROM_BOTTOM;
        int upperTop = HudBars.topForRow(guiGraphics.guiHeight(), HudBars.gluttonyRow(player));

        RenderSystem.enableBlend();
        drawRow(guiGraphics, player, right, lowerTop, lower);
        drawRow(guiGraphics, player, right, upperTop, upper);
        RenderSystem.disableBlend();
    }

    /** One 10-drumstick row, matching Gui#renderFood's sprite swap and jitter. */
    private static void drawRow(GuiGraphics guiGraphics, LocalPlayer player, int right, int top, int value) {
        boolean hungerEffect = player.hasEffect(MobEffects.HUNGER);
        ResourceLocation empty = hungerEffect ? FOOD_EMPTY_HUNGER : FOOD_EMPTY;
        ResourceLocation half = hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF;
        ResourceLocation full = hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL;
        boolean jitter = player.getFoodData().getSaturationLevel() <= 0.0F;

        for (int j = 0; j < ICONS; j++) {
            int x = right - j * 8 - 9;
            int y = top;
            if (jitter && player.tickCount % (value * 3 + 1) == 0) {
                y = top + (player.getRandom().nextInt(3) - 1);
            }
            guiGraphics.blitSprite(empty, x, y, 9, 9);
            if (j * 2 + 1 < value) {
                guiGraphics.blitSprite(full, x, y, 9, 9);
            } else if (j * 2 + 1 == value) {
                guiGraphics.blitSprite(half, x, y, 9, 9);
            }
        }
    }
}
