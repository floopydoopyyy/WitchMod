package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * A quick study: all XP you take in is multiplied by
 * {@code studiousXpMultiplier}. Applied in {@code BlessingEventHandler.onStudiousXp} on
 * {@code PlayerXpEvent.PickupXp} — it boosts the collected orb's value BEFORE the mending/XP split, so every
 * real XP source (mobs, mining, furnaces, fishing, breeding, bottles o' enchanting) is multiplied reliably even
 * through Mending gear. (Hooking {@code XpChange} instead silently missed all XP that Mending consumed before it
 * ever became XP.) Spending XP is untouched. The old prototype's fixed trickle is dropped.
 *
 * <p><b>Item moved Enchanted Book → Book</b> (its spec item), resolving the §11 Book clash: the retired
 * Mansplainer curse moves off Book onto its own spec item Written Book, freeing Book here.
 */
public final class BlessingStudious extends Effect {
    public BlessingStudious() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.BOOK);
    }

    /** you find out the first time XP pours in faster than it should (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
