package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import com.oliver.witchmod.Config;

/**
 * Everything the Tax Man has ever confiscated, held for the whole world (master-spec Taxes).
 *
 * <p>Stored as {@link SavedData} on the overworld's dimension storage, so it's genuinely one shared pot for
 * the entire server and survives restarts — exactly like {@link GlobalCharge}. It has to persist: the
 * <b>Tax Man blessing</b> is what eventually hands this back out, and that may happen days later, to a
 * completely different player.
 */
public final class TaxBank extends SavedData {
    private static final String NAME = "witchmod_tax_bank";
    private static final String KEY_ITEMS = "Items";

    private final List<ItemStack> held = new ArrayList<>();

    public TaxBank() {}

    public static TaxBank get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>((Supplier<TaxBank>) TaxBank::new,
                        (BiFunction<CompoundTag, HolderLookup.Provider, TaxBank>) TaxBank::load),
                NAME);
    }

    private static TaxBank load(CompoundTag tag, HolderLookup.Provider registries) {
        TaxBank bank = new TaxBank();
        ListTag list = tag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(bank.held::add);
        }
        return bank;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : held) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registries));
            }
        }
        tag.put(KEY_ITEMS, list);
        return tag;
    }

    /**
     * Adds a confiscated stack to the pot.
     *
     * @return false if the bank is full, in which case NOTHING was stored and the caller must leave the
     *         items where they are — see {@link #isFull()}
     */
    public boolean deposit(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (isFull()) {
            return false; // caller must not take it. Items are NEVER voided here.
        }
        held.add(stack.copy());
        setDirty();
        return true;
    }

    /**
     * <b>⚠ This ceiling exists for MEMORY, not balance, and it must never destroy anything.</b>
     *
     * <p>The bank is a persisted list that only ever grows — it is drained solely by the Tax Man BLESSING,
     * which may not be cast for weeks — so without a cap it would accumulate for the lifetime of the world.
     * When full, the correct behaviour everywhere is to STOP TAKING: the Taxes curse is refused at the table
     * and by command, and the Tax Man leaves valuables untouched. Discarding items to make room would be a
     * silent, unrecoverable loss of somebody's diamonds, and is never acceptable.
     */
    public boolean isFull() {
        return held.size() >= Config.TAXES_BANK_CAPACITY.get();
    }

    /** 0..1, how close the bank is to its memory ceiling. */
    public float fullness() {
        return held.size() / (float) Config.TAXES_BANK_CAPACITY.get();
    }

    /** True once it's worth telling an operator that the bank needs emptying. */
    public boolean shouldWarn() {
        return fullness() * 100.0F >= Config.TAXES_BANK_WARN_AT.get();
    }

    /** Everything currently held, for the Tax Man blessing to pay back out. */
    public List<ItemStack> contents() {
        return List.copyOf(held);
    }

    /** Empties the pot and hands back what was in it. */
    public List<ItemStack> withdrawAll() {
        List<ItemStack> payout = List.copyOf(held);
        held.clear();
        setDirty();
        return payout;
    }

    public int totalItems() {
        return held.stream().mapToInt(ItemStack::getCount).sum();
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }
}
