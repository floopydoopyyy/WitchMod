package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * A rock-steady draw (master-spec Steady Hands, sacrificial item SPECTRAL ARROW): bows and crossbows are
 * extremely accurate and charge noticeably quicker, and MULTISHOT in particular is compressed so its pellets
 * fly together and can all bite the same target.
 *
 * <p>All of it lives in {@code ProjectileBlessingHandler}: fired arrows/fireworks are re-aimed to your exact
 * line with a tiny spread ({@code steadyHandsInaccuracy}), and drawing a bow / loading a crossbow is sped up
 * ({@code steadyHandsChargeSpeedupTicks}) on the item-use tick. The old prototype's flat Strength effect is
 * dropped — the spec is archery, not melee damage.
 */
public final class BlessingSteadyHands extends Effect {
    public BlessingSteadyHands() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.SPECTRAL_ARROW);
    }

    /** You find out the first time a shot flies dead straight (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.STEADY_HANDS_ACTIVE, 1); // synced so the client speeds the draw up too
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.STEADY_HANDS_ACTIVE, -1);
    }
}
