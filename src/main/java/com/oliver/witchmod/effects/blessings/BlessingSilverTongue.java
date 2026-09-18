package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * every villager suddenly thinks you're their best customer: villager trades are {@code silverTongueDiscount} (75%) cheaper. Implemented in
 * {@link com.oliver.witchmod.effects.BlessingEventHandler} on {@code TradeWithVillagerEvent} by refunding
 * that fraction of the cost you just paid — so you effectively pay only a quarter. The prototype's Hero of
 * the Village stand-in is dropped.
 */
public final class BlessingSilverTongue extends Effect {
    public BlessingSilverTongue() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.EMERALD_BLOCK);
    }

    /** you find out the first time you talk a villager down (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
