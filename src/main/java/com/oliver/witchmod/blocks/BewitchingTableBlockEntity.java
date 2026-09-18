package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** holds the table's 4 ritual slots; implements {@link Container} so the menu can wrap them with vanilla slots. */
public final class BewitchingTableBlockEntity extends BlockEntity implements Container {
    public static final int SLOT_PLAYER_ESSENCE = 0;
    public static final int SLOT_SACRIFICIAL_ITEM = 1;
    public static final int SLOT_CURSED_ESSENCE = 2;
    public static final int SLOT_MODIFIER = 3;
    public static final int SLOT_COUNT = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public BewitchingTableBlockEntity(BlockPos pos, BlockState state) {
        super(WitchModBlockEntities.BEWITCHING_TABLE.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public boolean isSlotEmpty(int slot) {
        return items.get(slot).isEmpty();
    }

    public void clearAll() {
        clearContent();
    }

    @Override
    public void clearContent() {
        // ⚠ items is a FIXED-SIZE NonNullList.withSize (Arrays.asList-backed), so add() throws
        // unsupportedOperationException. NonNullList.clear() already resets EVERY slot to the default EMPTY
        // while keeping the size — that's all we need. The old clear()+add() loop threw on every cast, which
        // is what aborted the whole ritual after the slots were emptied (items consumed, nothing applied).
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // nonNullList.withSize is FIXED-SIZE (Arrays.asList-backed), so add() throws
        // unsupportedOperationException — which was killing every load and wiping the table's contents.
        // clear() is all that's needed: with a non-null default it re-sets every slot to EMPTY and keeps
        // the size, which is exactly the reset loadAllItems wants.
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
