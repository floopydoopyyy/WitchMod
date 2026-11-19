package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.data.EffectManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * the hidden "very infectious" state applied by the <b>Slime Block modifier</b> (not cast directly —
 * {@link #selectable()} is false). While a player carries it, hitting another player COPIES all of their OTHER
 * attachments onto the victim for a short window (10s) — you keep yours; the victim catches short-lived copies
 * (see the hook below). A far more aggressive spread than plain Infectious.
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

    /** a hit copies your other attachments onto the victim for this many ticks (~10s). */
    private static final int SPREAD_TICKS = 200;

    /** hitting another player copies your other attachments onto them for a short window; you keep everything. */
    @Override
    public void onAttack(ServerPlayer self, Entity target, AttackEntityEvent event) {
        if (!(target instanceof ServerPlayer victim) || victim == self
                || EffectManager.isActive(self, Curses.INFECTIOUS)) {
            return; // plain infectious takes the hit instead
        }
        ResourceLocation ownId = Curses.VERY_INFECTIOUS.getId();
        for (var entry : EffectManager.activeSnapshot(self).entrySet()) {
            if (!entry.getKey().equals(ownId)) { // don't self-propagate the very-infectious state
                EffectManager.holderOf(entry.getKey())
                        .ifPresent(h -> EffectManager.applyExact(victim, h, SPREAD_TICKS, self));
            }
        }
        CurseInfectious.splat(victim);
    }
}
