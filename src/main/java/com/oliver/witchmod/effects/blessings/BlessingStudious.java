package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * A quick study (master-spec Studious, sacrificial item BOOK): all XP you take in is multiplied by
 * {@code studiousXpMultiplier}. Applied in {@code BlessingEventHandler.onStudiousXp} on
 * {@code PlayerXpEvent.XpChange} (so it covers every source — orbs, furnaces, trading, breeding, bottles o'
 * enchanting). Spending XP is untouched. The old prototype's fixed trickle is dropped.
 *
 * <p><b>Item moved Enchanted Book → Book</b> (its spec item), resolving the §11 Book clash: the retired
 * Mansplainer curse moves off Book onto its own spec item Written Book, freeing Book here.
 */
public final class BlessingStudious extends Effect {
    public BlessingStudious() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.BOOK);
    }

    /** You find out the first time XP pours in faster than it should (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
