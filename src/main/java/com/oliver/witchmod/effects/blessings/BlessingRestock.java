package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * restock (sacrificial item CHEST): a stacked item you USE is instantly topped back up from your inventory —
 * place torches all day and your hand stays at a full 64 as long as you've spares in the backpack; the same
 * for arrows, food, blocks. Never break your rhythm to dig through the pack again.
 *
 * <p><b>It fires on USE, not on moves.</b> It watches the hotbar + offhand for a slot whose SAME item just
 * DROPPED in count (a placement/consumption), and tops it back up to a full stack. Shuffling items around the
 * inventory — dragging a stack out, shift-clicking it away, swapping slots — changes the slot's item TYPE or
 * empties it, which is deliberately NOT treated as a use, so re-organising no longer triggers a phantom refill.
 */
public final class BlessingRestock extends Effect {
    /** player -> the item that was in each watched slot last tick (hotbar 0-8 then offhand at index 9). */
    private static final Map<UUID, ItemStack[]> PREV = new HashMap<>();
    private static final int OFFHAND = 9; // our index for the offhand in the snapshot

    public BlessingRestock() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.CHEST);
    }

    /** not instantly noticeable — you discover it the first time a slot tops itself back up. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        PREV.remove(target.getUUID());
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        PREV.put(target.getUUID(), snapshot(target));
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        Inventory inv = target.getInventory();
        ItemStack[] prev = PREV.get(target.getUUID());
        if (prev == null) {
            PREV.put(target.getUUID(), snapshot(target));
            return;
        }
        boolean restocked = false;
        for (int i = 0; i <= OFFHAND; i++) {
            ItemStack now = slot(inv, i);
            ItemStack was = prev[i];
            // A USE: same item still there, but its count DROPPED (placed/consumed one) → top it back up to a full
            // stack from the backpack. A type change / empty slot is a MOVE, and is ignored.
            if (!now.isEmpty() && !was.isEmpty() && now.getMaxStackSize() > 1
                    && ItemStack.isSameItemSameComponents(now, was)
                    && now.getCount() < was.getCount()) {
                int need = now.getMaxStackSize() - now.getCount();
                if (need > 0 && pullMatching(inv, now, need) > 0) {
                    restocked = true;
                }
            }
        }
        PREV.put(target.getUUID(), snapshot(target));
        if (restocked) {
            markDiscoveredByVictim(target);
        }
    }

    /** moves up to {@code need} matching items out of the MAIN inventory INTO {@code dest} (the live slot stack). */
    private static int pullMatching(Inventory inv, ItemStack dest, int need) {
        int moved = 0;
        for (int s = 9; s < 36 && need > 0; s++) { // main storage rows only (not the hotbar/offhand)
            ItemStack src = inv.getItem(s);
            if (src.isEmpty() || !ItemStack.isSameItemSameComponents(src, dest)) {
                continue;
            }
            int take = Math.min(need, src.getCount());
            src.shrink(take);
            dest.grow(take);
            if (src.isEmpty()) {
                inv.setItem(s, ItemStack.EMPTY);
            }
            need -= take;
            moved += take;
        }
        return moved;
    }

    private static ItemStack slot(Inventory inv, int i) {
        return i == OFFHAND ? inv.offhand.get(0) : inv.getItem(i);
    }

    private static ItemStack[] snapshot(ServerPlayer target) {
        Inventory inv = target.getInventory();
        ItemStack[] snap = new ItemStack[OFFHAND + 1];
        for (int i = 0; i <= OFFHAND; i++) {
            snap[i] = slot(inv, i).copy();
        }
        return snap;
    }
}
