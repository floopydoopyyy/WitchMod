package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * ore just gives you more. Breaking an ore-tag block yields
 * a few EXTRA drops on top of whatever it would normally give — and it's <b>additive</b>, applied after the
 * item's own enchantment Fortune has already rolled, so the two stack without multiplying into absurdity.
 * You get 0–{@code fortuneExtraMax} extra, biased toward {@code fortuneExtraMode} (usually one).
 *
 * <p>The actual drop-boosting lives in {@code effects/BlessingEventHandler} on {@code BlockDropsEvent} — the
 * one place the finished drop list is in hand. This class only declares the blessing and defers discovery to
 * the first time it actually pays out.
 */
public final class BlessingFortune extends Effect {
    public BlessingFortune() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.DIAMOND);
    }

    /** you notice it the first time an ore hands you more than it should. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
