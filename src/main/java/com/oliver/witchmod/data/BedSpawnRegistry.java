package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tracks which players have their SPAWN POINT set at which bed — persisted server-wide so it works even for
 * OFFLINE players (whose current respawn we can't otherwise query). Updated from {@code PlayerSetSpawnEvent};
 * read when someone bottles a bed's Player Essence. Multiple players can share a bed, so {@link #at} returns
 * all of them.
 */
public final class BedSpawnRegistry extends SavedData {
    private static final String NAME = "witchmod_bed_spawns";
    private static final String KEY_LIST = "Spawns";

    /** player uuid -> where their spawn is set. */
    private final Map<UUID, Spawn> spawns = new HashMap<>();

    private record Spawn(String dim, long pos, String name) {}

    public BedSpawnRegistry() {}

    public static BedSpawnRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>((Supplier<BedSpawnRegistry>) BedSpawnRegistry::new,
                        (BiFunction<CompoundTag, HolderLookup.Provider, BedSpawnRegistry>) BedSpawnRegistry::load),
                NAME);
    }

    /** Record (or move) a player's spawn to {@code pos} in {@code dim}. */
    public void record(ResourceKey<Level> dim, BlockPos pos, UUID id, String name) {
        spawns.put(id, new Spawn(dim.location().toString(), pos.asLong(), name));
        setDirty();
    }

    /** Forget a player's spawn (they cleared it / it was destroyed). */
    public void clear(UUID id) {
        if (spawns.remove(id) != null) {
            setDirty();
        }
    }

    /** Every player whose spawn is set on the bed at (dim, pos) — within a block or two, since a bed is 2 wide. */
    public List<PlayerEssenceData> at(ResourceKey<Level> dim, BlockPos pos) {
        String d = dim.location().toString();
        List<PlayerEssenceData> out = new ArrayList<>();
        for (Map.Entry<UUID, Spawn> e : spawns.entrySet()) {
            Spawn s = e.getValue();
            if (s.dim.equals(d) && BlockPos.of(s.pos).closerThan(pos, 2.0)) {
                out.add(new PlayerEssenceData(e.getKey(), s.name));
            }
        }
        return out;
    }

    private static BedSpawnRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BedSpawnRegistry reg = new BedSpawnRegistry();
        ListTag list = tag.getList(KEY_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            try {
                reg.spawns.put(UUID.fromString(e.getString("id")),
                        new Spawn(e.getString("dim"), e.getLong("pos"), e.getString("name")));
            } catch (IllegalArgumentException ignored) {
                // skip a malformed entry rather than failing the whole load
            }
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Spawn> e : spawns.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey().toString());
            c.putString("dim", e.getValue().dim());
            c.putLong("pos", e.getValue().pos());
            c.putString("name", e.getValue().name());
            list.add(c);
        }
        tag.put(KEY_LIST, list);
        return tag;
    }
}
