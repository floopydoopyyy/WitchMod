package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * The hidden "very infectious" state applied by the <b>Slime Block modifier</b> (not cast directly —
 * {@link #selectable()} is false). While a player carries it, hitting another player COPIES all of their OTHER
 * attachments onto the victim for a short window (10s) — you keep yours; the victim catches short-lived copies
 * (see {@code CurseEventHandler.onInfectiousAttack}). A far more aggressive spread than plain Infectious.
 */
public final class CurseVeryInfectious extends Effect {
    public CurseVeryInfectious() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 40, () -> Items.SLIME_BLOCK);
    }

    @Override
    public boolean selectable() {
        return false;
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
