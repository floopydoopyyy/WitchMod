package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * the organised blessing's 9 extra inventory slots, backed by a serialized attachment. opened as a plain
 * 1-row chest menu (no custom menu/screen) via a container that saves back to the attachment on close.
 */
public final class OrganisedStash {
    public static final int SIZE = 9;

    private OrganisedStash() {}

    /** opens the 9-slot stash as a 1-row chest menu for the owner. */
    public static void openMenu(ServerPlayer owner) {
        SimpleContainer container = openContainer(owner);
        owner.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, container, 1),
                Component.literal("Stash")));
    }

    /** container seeded from the stored slots that saves back to the attachment on close. */
    public static SimpleContainer openContainer(ServerPlayer owner) {
        SimpleContainer container = new SimpleContainer(SIZE) {
            @Override
            public void stopOpen(Player player) {
                super.stopOpen(player);
                save(owner, this);
            }
        };
        List<ItemStack> stored = owner.getData(WitchModAttachments.ORGANISED_ITEMS);
        for (int i = 0; i < SIZE && i < stored.size(); i++) {
            container.setItem(i, stored.get(i).copy());
        }
        return container;
    }

    private static void save(ServerPlayer owner, Container container) {
        List<ItemStack> items = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            items.add(container.getItem(i).copy());
        }
        owner.setData(WitchModAttachments.ORGANISED_ITEMS, items);
    }

    /** on losing the blessing, spill the extra row into the main inventory, drop the overflow, then clear it. */
    public static void returnOrDrop(ServerPlayer owner) {
        for (ItemStack stored : owner.getData(WitchModAttachments.ORGANISED_ITEMS)) {
            if (stored.isEmpty()) {
                continue;
            }
            ItemStack remainder = stored.copy();
            owner.getInventory().add(remainder); // mutates remainder to whatever didn't fit
            if (!remainder.isEmpty()) {
                owner.drop(remainder, false); // no room — drop the overflow
            }
        }
        owner.setData(WitchModAttachments.ORGANISED_ITEMS, List.of());
    }
}
