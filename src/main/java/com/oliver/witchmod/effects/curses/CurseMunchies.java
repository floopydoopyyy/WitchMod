package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * the munchies: food barely fills you (you keep only {@code munchiesSaturationPercent} of the saturation it
 * would give, so nothing sticks and you graze constantly) BUT you wolf it down {@code munchiesEatSpeedPercent}
 * faster. The eat-speed boost is applied the same way Gluttony's is (shortening the use duration on
 * {@code LivingEntityUseItemEvent.Start}), so the two STACK — a Gluttony+Munchies victim eats very fast
 * indeed. All wired in {@code CurseEventHandler}'s eat hooks.
 */
public final class CurseMunchies extends Effect {
    public CurseMunchies() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.PUMPKIN_PIE);
    }

    /** discovered the first time a meal barely fills you (marked in CurseEventHandler's eat-finish hook). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
