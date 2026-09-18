package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * the hidden "infectious" state applied by the <b>Slime Ball modifier</b> (not cast directly — {@link #selectable()}
 * is false). While a player carries it, their attachments become a HOT POTATO: hitting another player transfers
 * ALL of their attachments — this one included — onto the victim with their timers preserved (see
 * {@code CurseEventHandler.onInfectiousAttack}). Lasts ~1 hour, or until it's passed on. Deliberately never
 * discovered ({@link #discoversOnTrigger()} true but it never marks), so it stays a hidden attachment.
 */
public final class CurseInfectious extends Effect {
    public CurseInfectious() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.SLIME_BALL);
    }

    @Override
    public boolean selectable() {
        return false;
    }

    @Override
    public boolean discoversOnTrigger() {
        return true; // never actually marked — a hidden attachment
    }
}
