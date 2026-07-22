package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * hahaha funny ping pong
 * maths sucks!!!!!!!
 */
public final class CurseScreensaver extends Effect {
    public CurseScreensaver() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.PAINTING);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, -1);
    }
}
