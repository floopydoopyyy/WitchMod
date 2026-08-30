package com.oliver.witchmod.ui;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.SacrificialItems;
import com.oliver.witchmod.items.WitchModItems;

/**
 * One of the Table's 4 ritual slots (CLAUDE.md section 2.1), restricted to the item type that slot
 * accepts — mirrors the item-identity checks the Phase 4 placeholder ({@code BewitchingTableBlock})
 * used directly, now centralized here since the GUI is the only way to fill these slots.
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

    /**
     * Any item may be PLACED in any ritual slot — a wrong one is highlighted red and blocks the cast (see
     * {@link BewitchingTableScreen}). This is friendlier than silently bouncing the item back with no reason,
     * and lets the UI teach the player what goes where. Correctness is {@link #isCorrect}.
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    /** Whether {@code stack} is the RIGHT item for this slot — drives the red highlight, shift-click routing and cast validity. */
    boolean isCorrect(ItemStack stack) {
        if (stack.isEmpty()) {
            return true; // empty is never "wrong" — only the Sacrificial slot being empty blocks a cast
        }
        return switch (kind) {
            // A Player Essence targets a player; a jar bottles the effect instead of hitting anyone (it can't
            // already be full — a full jar has nowhere to put the result).
            case PLAYER_ESSENCE -> stack.getItem() == WitchModItems.PLAYER_ESSENCE.get()
                    || (stack.getItem() instanceof com.oliver.witchmod.items.ItemVoodooDoll
                        && stack.has(com.oliver.witchmod.data.WitchModDataComponents.BOUND_PLAYER))
                    || (stack.getItem() instanceof com.oliver.witchmod.items.ItemJar
                        && !com.oliver.witchmod.items.JarContents.isFull(stack));
            case CURSED_ESSENCE -> stack.getItem() == WitchModItems.CURSED_ESSENCE.get();
            // Redstone Dust is accepted too: it's the "random attachment" table mechanic (master-spec
            // Section 3), not a normal effect selector.
            case SACRIFICIAL_ITEM -> stack.is(Items.REDSTONE)
                    || com.oliver.witchmod.data.CoinGamble.typeOf(stack.getItem()) != null
                    || SacrificialItems.findEffect(stack.getItem()).isPresent();
            case MODIFIER -> ModifierItems.findModifier(stack.getItem()).isPresent();
        };
    }

    /** Only Cursed Essence stacks (essenceSpent = stack count, CLAUDE.md section 5.7) — the other 3 are single-item selectors. */
    @Override
    public int getMaxStackSize() {
        return kind == Kind.CURSED_ESSENCE ? 64 : 1;
    }
}
