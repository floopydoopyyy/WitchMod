package com.oliver.witchmod.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.blocks.BewitchingTableBlockEntity;
import com.oliver.witchmod.blocks.BewitchingTableRitual;
import com.oliver.witchmod.blocks.WitchModBlocks;

/**
 * the ritual table's container menu — 4 ritual slots in a triangle plus the player inventory. casting is
 * driven by a c2s payload; clickMenuButton is kept as a vanilla-button fallback (server-side only).
 */
public final class BewitchingTableMenu extends AbstractContainerMenu {
    public static final int BUTTON_CAST = 0;

    private static final int RITUAL_SLOT_COUNT = BewitchingTableBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = RITUAL_SLOT_COUNT + 36;

    private final Container table;
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** client-side reconstruction from the open packet. */
    public BewitchingTableMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        this(windowId, playerInventory, new SimpleContainer(RITUAL_SLOT_COUNT),
                ContainerLevelAccess.create(playerInventory.player.level(), pos), pos);
    }

    /** server-side, opened from the block. */
    public BewitchingTableMenu(int windowId, Inventory playerInventory, BewitchingTableBlockEntity table) {
        this(windowId, playerInventory, table, ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()), table.getBlockPos());
    }

    private BewitchingTableMenu(int windowId, Inventory playerInventory, Container table, ContainerLevelAccess access, BlockPos pos) {
        super(WitchModMenus.BEWITCHING_TABLE.get(), windowId);
        this.table = table;
        this.access = access;
        this.pos = pos;

        // slots must be added in container-index order (0..3) so slots.get(SLOT_*) matches the block entity's
        // index — a mismatched order silently swaps the sacrificial/essence slots
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE, 43, 48, RitualSlot.Kind.PLAYER_ESSENCE));
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM, 79, 35, RitualSlot.Kind.SACRIFICIAL_ITEM));
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE, 79, 64, RitualSlot.Kind.CURSED_ESSENCE));
        addSlot(new RitualSlot(table, BewitchingTableBlockEntity.SLOT_MODIFIER, 115, 48, RitualSlot.Kind.MODIFIER));

        // player inventory + hotbar, pushed down to clear the ritual/bar/button area
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 145 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 203));
        }
    }

    /** the table's world position — used client-side for ambient particles. */
    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, WitchModBlocks.BEWITCHING_TABLE.get());
    }

    /** cast button fallback — only runs for a real server player, so the ritual never runs client-side. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CAST || !(player instanceof ServerPlayer serverPlayer) || !(table instanceof BewitchingTableBlockEntity real)) {
            return false;
        }
        access.execute((level, pos) -> BewitchingTableRitual.cast((ServerLevel) level, pos, real, serverPlayer));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < RITUAL_SLOT_COUNT) {
                if (!moveItemStackTo(stack, RITUAL_SLOT_COUNT, PLAYER_INV_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int target = resolveRitualSlot(stack);
                if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    private int resolveRitualSlot(ItemStack stack) {
        for (int i = 0; i < RITUAL_SLOT_COUNT; i++) {
            // route a shift-click only to the slot it belongs in (mayPlace accepts anything, so isCorrect routes)
            if (this.slots.get(i) instanceof RitualSlot ritual && ritual.isCorrect(stack)) {
                return i;
            }
        }
        return -1;
    }
}
