package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Light on your feet — you take no fall damage at all (master-spec Twinkletoes, sacrificial item HAY BALE).
 * The negation itself lives in {@code BlessingEventHandler}'s {@code LivingFallEvent} listener (it zeroes the
 * fall-damage multiplier); the old prototype's Slow Falling + Speed potion effects are dropped, since the spec
 * is simply fall-damage immunity, not floaty movement.
 */
public final class BlessingTwinkletoes extends Effect {
    public BlessingTwinkletoes() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.HAY_BLOCK);
    }

    /** You find out the first time a fall that should have hurt simply doesn't (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
