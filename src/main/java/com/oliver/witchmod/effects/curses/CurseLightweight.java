package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.Config;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * you're a pushover: knockback dealt TO you is multiplied ({@code lightweightKnockbackMultiplier}), and it
 * stacks on top of the attacker's own Knockback enchants/enhancements. The multiply is applied on
 * {@code LivingKnockBackEvent} in the hooks below (which fires for the entity being knocked back, so
 * it keys off the victim), after vanilla + enchants have set the base strength — so it genuinely amplifies
 * whatever would have launched you.
 */
public final class CurseLightweight extends Effect {
    public CurseLightweight() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SNOWBALL);
    }

    /** discovered the first time you get launched further than you should (see the hooks below). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** multiplies after vanilla and enchants set the base strength, so it amplifies whatever would have launched you. */
    @Override
    public void onKnockback(ServerPlayer self, LivingKnockBackEvent event) {
        event.setStrength(event.getStrength() * Config.LIGHTWEIGHT_KNOCKBACK_MULT.get().floatValue());
        markDiscoveredByVictim(self);
    }
}
