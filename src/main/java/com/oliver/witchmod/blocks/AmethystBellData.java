package com.oliver.witchmod.blocks;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * per-dimension recharge clock for the amethyst bell — one absolute game-tick (persisted), so ringing any
 * bell locks every bell in the dimension and a reload/relog/replace can't dodge it. this is the ring
 * authority; a block entity's field only mirrors it for the greyed render.
 */
public final class AmethystBellData extends SavedData {
    private static final String NAME = "witchmod_amethyst_bell";
    private static final String KEY = "until";

    private long until = 0L;

    public AmethystBellData() {}

    public static AmethystBellData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>((Supplier<AmethystBellData>) AmethystBellData::new,
                        (BiFunction<CompoundTag, HolderLookup.Provider, AmethystBellData>) AmethystBellData::load),
                NAME);
    }

    private static AmethystBellData load(CompoundTag tag, HolderLookup.Provider registries) {
        AmethystBellData data = new AmethystBellData();
        data.until = tag.getLong(KEY);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(KEY, until);
        return tag;
    }

    /** The game-tick every bell in this dimension becomes active again (0 if none is recharging). */
    public long until() {
        return until;
    }

    public boolean isInactive(ServerLevel level) {
        return level.getGameTime() < until;
    }

    public void set(ServerLevel level, long untilTick) {
        until = untilTick;
        setDirty();
    }
}
