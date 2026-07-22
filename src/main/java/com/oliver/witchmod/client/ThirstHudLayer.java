package com.oliver.witchmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModMobEffects;

/**
 * The Thirst Meter bar: a row of droplets mirroring vanilla's hunger row, driven by the auto-synced
 * {@link WitchModAttachments#THIRST} value ({@code -1} = curse inactive → hidden entirely).
 *
 * <p>Deliberately built as a close copy of {@code Gui#renderFood} so it reads as part of the HUD rather than
 * as something bolted on: same 10 icons, same 8px spacing, same right-to-left fill, and the same **jitter**
 * vanilla applies when you're in trouble — here driven by the Dehydration effect rather than by hunger.
 * Under Dehydration the icons also swap to their own tinted variants, which is what tells you at a glance
 * why the bar is emptying so fast.
 *
 * <p>Hidden in creative and spectator via the same {@code canHurtPlayer} gate vanilla uses for health,
 * hunger and air.
 */
public final class ThirstHudLayer implements LayeredDraw.Layer {
    private static final int ICONS = 10;
    private static final int ICON_SPACING = 8;
    private static final int ICON_SIZE = 9;

    private static final ResourceLocation FULL = hud("thirst_full");
    private static final ResourceLocation HALF = hud("thirst_half");
    private static final ResourceLocation EMPTY = hud("thirst_empty");
    private static final ResourceLocation FULL_DRY = hud("thirst_full_dehydration");
    private static final ResourceLocation HALF_DRY = hud("thirst_half_dehydration");
    private static final ResourceLocation EMPTY_DRY = hud("thirst_empty_dehydration");

    private static ResourceLocation hud(String name) {
        return ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/hud/" + name + ".png");
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // Hidden in creative/spectator, matching vanilla's survival-HUD gate (hunger, health, air).
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        LocalPlayer player = mc.player;
        int thirst = player.getData(WitchModAttachments.THIRST);
        if (thirst < 0) {
            return; // curse not active
        }

        boolean dry = player.hasEffect(WitchModMobEffects.DEHYDRATION);
        ResourceLocation full = dry ? FULL_DRY : FULL;
        ResourceLocation half = dry ? HALF_DRY : HALF;
        ResourceLocation empty = dry ? EMPTY_DRY : EMPTY;

        int max = Config.THIRST_MAX.get();
        int right = guiGraphics.guiWidth() / 2 + 91;                       // vanilla food-bar right edge
        int top = HudBars.topForRow(guiGraphics.guiHeight(), HudBars.thirstRow(player));

        // Vanilla jitters the hunger icons when you're starving; here it's Dehydration that shakes them,
        // seeded off the tick so the whole row doesn't wobble in lockstep.
        RandomSource jitter = player.getRandom();

        for (int i = 0; i < ICONS; i++) {
            int x = right - i * ICON_SPACING - ICON_SIZE;
            int y = top;
            if (dry && jitter.nextFloat() < 0.08F) {
                y += jitter.nextInt(3) - 1;
            }
            // Two thirst points per icon, filling right to left exactly like hunger.
            int pointsHere = Math.max(0, Math.min(2, thirst - i * (max / ICONS)));

            guiGraphics.blit(empty, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            if (pointsHere >= 2) {
                guiGraphics.blit(full, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            } else if (pointsHere == 1) {
                guiGraphics.blit(half, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            }
        }
    }
}
