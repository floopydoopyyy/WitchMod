package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.OrganisedStash;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * A place for everything. Grants 9 extra inventory
 * slots (a personal stash opened with {@code /bewitch organised}, backed by {@link OrganisedStash}), whose
 * contents drop when the blessing expires. As a bonus it also auto-consolidates matching stacks in your
 * main inventory.
 *
 * <p>Opened via a small stash button added to the inventory screen (no keybind), or {@code /bewitch organised}.
 */
public final class BlessingOrganised extends Effect {
    private static final int INTERVAL_TICKS = 100;

    public BlessingOrganised() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.SHULKER_SHELL);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.ORGANISED_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.ORGANISED_ACTIVE, -1);
        // when lost, the extra row's items try to go into the main inventory first; overflow is dropped.
        OrganisedStash.returnOrDrop(target);
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        com.oliver.witchmod.data.OrganisedStash.openMenu(target);
        return "opened your 9-slot stash (normally: the button on your inventory screen, or /bewitch organised)";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, INTERVAL_TICKS)) {
            return;
        }
        NonNullList<ItemStack> items = target.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack from = items.get(i);
            if (from.isEmpty() || from.getCount() >= from.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < items.size(); j++) {
                ItemStack into = items.get(j);
                if (into.isEmpty() || !ItemStack.isSameItemSameComponents(from, into) || into.getCount() >= into.getMaxStackSize()) {
                    continue;
                }
                int space = into.getMaxStackSize() - into.getCount();
                int moved = Math.min(space, from.getCount());
                into.grow(moved);
                from.shrink(moved);
                if (from.isEmpty()) {
                    break;
                }
            }
        }
    }
}
