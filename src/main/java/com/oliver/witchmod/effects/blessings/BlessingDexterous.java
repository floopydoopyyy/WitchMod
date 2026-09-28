package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * dexterous (Spectral Arrow): a rock-steady, quick draw — bows and crossbows are extremely accurate and charge
 * noticeably quicker, MULTISHOT is compressed so its pellets fly together, and eating/drinking/raising a shield
 * are quicker too.
 *
 * <p>Most of it lives in {@code ProjectileBlessingHandler}: fired arrows/fireworks are re-aimed to your exact
 * line with a tiny spread ({@code dexterousSpreadRetain}), and use-actions are sped up
 * ({@code dexterousChargeSpeedupTicks}) on the item-use tick. The shield-raise gate is a fixed vanilla constant,
 * so it's shortened via {@code ShieldRaiseMixin} off the synced active flag.
 */
public final class BlessingDexterous extends Effect {
    public BlessingDexterous() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.SPECTRAL_ARROW);
    }

    /** you find out the first time a shot flies dead straight (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.DEXTEROUS_ACTIVE, 1); // synced so the client speeds the draw up + the mixin fires
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.DEXTEROUS_ACTIVE, -1);
    }
}
