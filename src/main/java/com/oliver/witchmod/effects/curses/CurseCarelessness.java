package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * carelessness (Black Wool): your entire health bar is rendered as identical black hearts, so you genuinely
 * can't tell what health you're on. Purely a client-render change off the synced
 * {@link WitchModAttachments#CARELESSNESS_ACTIVE} flag — the real health value and all mechanics are untouched
 * (see {@code client/CarelessnessHud}), you're just flying blind.
 */
public final class CurseCarelessness extends Effect {
    public CurseCarelessness() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.BLACK_WOOL);
    }

    // discovery on apply (the centralised default): the blacked-out hearts are obvious the instant it lands.

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.CARELESSNESS_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.CARELESSNESS_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.CARELESSNESS_ACTIVE) < 0) {
            target.setData(WitchModAttachments.CARELESSNESS_ACTIVE, 1); // self-heal after a relog
        }
    }
}
