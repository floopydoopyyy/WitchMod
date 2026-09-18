package com.oliver.witchmod.ui;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.SacrificialItems;
import com.oliver.witchmod.items.WitchModItems;

/**
 * one of the table's 4 ritual slots. any item can be placed; a wrong one is flagged (isCorrect) rather than
 * bounced, so the ui can teach what goes where.
 */
final class RitualSlot extends Slot {
    enum Kind {
        PLAYER_ESSENCE,
        CURSED_ESSENCE,
        SACRIFICIAL_ITEM,
        MODIFIER
    }

    private final Kind kind;

    RitualSlot(Container container, int index, int x, int y, Kind kind) {
        super(container, index, x, y);
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }

    /** any item may be placed; correctness (isCorrect) drives the red flag and cast validity instead. */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    /** whether {@code stack} is the right item for this slot — drives the red highlight, shift-click routing and cast validity. */
    boolean isCorrect(ItemStack stack) {
        if (stack.isEmpty()) {
            return true; // empty is never wrong; only an empty sacrificial slot blocks a cast
        }
        return switch (kind) {
            // player essence targets a player; a non-full jar bottles the effect instead
            case PLAYER_ESSENCE -> stack.getItem() == WitchModItems.PLAYER_ESSENCE.get()
                    || (stack.getItem() instanceof com.oliver.witchmod.items.ItemVoodooDoll
                        && stack.has(com.oliver.witchmod.data.WitchModDataComponents.BOUND_PLAYER))
                    || (stack.getItem() instanceof com.oliver.witchmod.items.ItemJar
                        && !com.oliver.witchmod.items.JarContents.isFull(stack));
            case CURSED_ESSENCE -> com.oliver.witchmod.blocks.BewitchingTableRitual.isEssenceItem(stack);
            // redstone is the "random attachment" table mechanic, not a normal selector
            case SACRIFICIAL_ITEM -> stack.is(Items.REDSTONE)
                    || com.oliver.witchmod.data.CoinGamble.typeOf(stack.getItem()) != null
                    || SacrificialItems.findEffect(stack.getItem()).isPresent();
            case MODIFIER -> ModifierItems.findModifier(stack.getItem()).isPresent();
        };
    }

    /** only cursed essence stacks (essenceSpent = count); the other 3 are single-item selectors. */
    @Override
    public int getMaxStackSize() {
        return kind == Kind.CURSED_ESSENCE ? 64 : 1;
    }
}
