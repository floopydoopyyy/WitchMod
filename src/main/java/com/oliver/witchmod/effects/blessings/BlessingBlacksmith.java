package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * you know your way around an anvil. Repairs and enchants come cheap.
 *
 * <p>The anvil cost reduction (and lifting the "Too Expensive!" cap) is applied in
 * the anvil hook below. This class only
 * declares cost/sacrificial item.
 */
public final class BlessingBlacksmith extends Effect {
    public static final float ANVIL_COST_MULT = 0.4F;

    public BlessingBlacksmith() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.COPPER_BLOCK);
    }

    @Override
    public void onAnvil(ServerPlayer self, AnvilUpdateEvent event) {
        // "too expensive!" is the anvil's cost cap: lift it on the live menu so no repair is ever blocked (on the
        // menu directly, because the event fires before vanilla recomputes the cost)
        if (self.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu anvil) {
            anvil.setMaximumCost(Integer.MAX_VALUE);
        }
        // much cheaper: wipe the inputs' accumulated "prior work" penalty — the exponential driver of anvil costs
        // (1,3,7,15,...) that a skilled blacksmith just doesn't rack up. vanilla then recomputes the now-low cost;
        // the event cost is set too, for any custom-recipe output path.
        clearPriorWork(event.getLeft());
        clearPriorWork(event.getRight());
        event.setCost(Math.max(1L, Math.round(event.getCost() * ANVIL_COST_MULT)));
    }

    private static void clearPriorWork(ItemStack stack) {
        if (!stack.isEmpty() && stack.getOrDefault(DataComponents.REPAIR_COST, 0) > 0) {
            stack.set(DataComponents.REPAIR_COST, 0);
        }
    }
}
