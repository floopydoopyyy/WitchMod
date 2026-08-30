package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * A gifted enchanter (master-spec-style Enchanter, sacrificial item LAPIS LAZULI): the enchanting table is
 * kind to you. Enchanting costs you almost no XP, the options roll a couple of levels higher by default (so
 * better enchants), and you can never pull one of the "bad" enchantments out of a table.
 *
 * <p>Purely event-driven — the three perks live in {@code BlessingEventHandler}:
 * <ul>
 *   <li><b>Cheap XP</b> — a {@code PlayerXpEvent.LevelChange} handler softens the level loss while an
 *       enchanting table is open (see {@code onEnchanterCost}).</li>
 *   <li><b>Higher levels</b> — an {@code EnchantmentLevelSetEvent} handler bumps the offered levels when an
 *       Enchanter is at the table (see {@code onEnchanterLevels}).</li>
 *   <li><b>No bad enchants</b> — a {@code PlayerEnchantItemEvent} handler strips the bad ones off the result
 *       via {@link #stripBadEnchants} (see {@code onEnchanterResult}).</li>
 * </ul>
 */
public final class BlessingEnchanter extends Effect {
    public BlessingEnchanter() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.LAPIS_LAZULI);
    }

    /** Discovered the first time you actually enchant something. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** True if this enchantment is one of the spec's "bad" ones (or any curse). */
    public static boolean isBad(Holder<Enchantment> e) {
        return e.is(Enchantments.SMITE)
                || e.is(Enchantments.BANE_OF_ARTHROPODS)
                || e.is(Enchantments.BLAST_PROTECTION)
                || e.is(Enchantments.PROJECTILE_PROTECTION)
                || e.is(Enchantments.FIRE_PROTECTION)
                || e.is(Enchantments.PIERCING)
                || e.is(EnchantmentTags.CURSE);
    }

    /** Remove every bad enchantment from a freshly-enchanted item (covers gear and enchanted books). */
    public static void stripBadEnchants(ItemStack stack) {
        stripFrom(stack, DataComponents.ENCHANTMENTS);
        stripFrom(stack, DataComponents.STORED_ENCHANTMENTS);
    }

    private static void stripFrom(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments current = stack.get(type);
        if (current == null || current.isEmpty()) {
            return;
        }
        List<Holder<Enchantment>> bad = new ArrayList<>();
        for (Holder<Enchantment> e : current.keySet()) {
            if (isBad(e)) {
                bad.add(e);
            }
        }
        if (bad.isEmpty()) {
            return;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        for (Holder<Enchantment> e : bad) {
            mutable.set(e, 0); // level 0 removes it
        }
        stack.set(type, mutable.toImmutable());
    }
}
