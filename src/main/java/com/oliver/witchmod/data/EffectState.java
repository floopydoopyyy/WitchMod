package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.oliver.witchmod.WitchMod;

/**
 * the per-player state effects keep between ticks (cooldowns, meters, trackers). keyed by uuid rather than by
 * the player entity, so it survives death, leaving and rejoining; scoped to the running world, so nothing leaks
 * between singleplayer saves.
 *
 * <p>{@link #saved} state is also written into the world and survives a server restart. {@link #session} state
 * lasts until the server stops — for entity references and in-flight moments that mean nothing after a restart.
 * each call returns a live {@code Map<UUID, T>} (or {@code Set<UUID>}) that's used exactly like a plain map.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class EffectState {
    private static final String DATA_NAME = WitchMod.MODID + "_effect_state";
    private static final List<Slot> SLOTS = new ArrayList<>();
    private static final Map<String, Slot> BY_NAME = new HashMap<>();
    /** used while no server is running, so a stray call never throws. */
    private static final Store DETACHED = new Store();

    @Nullable
    private static volatile MinecraftServer boundServer;
    @Nullable
    private static volatile Store bound;

    private EffectState() {}

    /** state that lasts until the server stops. */
    public static <T> Map<UUID, T> session(String name) {
        return new StateMap<>(slot(name, null, false, false));
    }

    /** {@link #session} state backed by a concurrent map, for state touched off the server thread. */
    public static <T> Map<UUID, T> sessionConcurrent(String name) {
        return new StateMap<>(slot(name, null, false, true));
    }

    /** state saved with the world, so it also survives a restart. */
    public static <T> Map<UUID, T> saved(String name, Codec<T> codec) {
        return new StateMap<>(slot(name, codec, false, false));
    }

    /** a set of players that lasts until the server stops. */
    public static Set<UUID> sessionSet(String name) {
        return new StateSet(slot(name, null, true, false));
    }

    /** a set of players saved with the world. */
    public static Set<UUID> savedSet(String name) {
        return new StateSet(slot(name, Codec.STRING, true, false));
    }

    private static synchronized Slot slot(String name, @Nullable Codec<?> codec, boolean set, boolean concurrent) {
        if (BY_NAME.containsKey(name)) {
            throw new IllegalStateException("effect state '" + name + "' is declared twice");
        }
        Slot slot = new Slot(SLOTS.size(), name, codec, set, concurrent);
        SLOTS.add(slot);
        BY_NAME.put(name, slot);
        return slot;
    }

    /** the running world's store (loaded on first use). */
    private static Store store() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return DETACHED;
        }
        Store store = bound;
        return store != null && boundServer == server ? store : bind(server);
    }

    private static synchronized Store bind(MinecraftServer server) {
        if (boundServer == server && bound != null) {
            return bound;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return DETACHED; // mid-startup: nothing to load into yet
        }
        Store store = overworld.getDataStorage().computeIfAbsent(Store.FACTORY, DATA_NAME);
        bound = store;
        boundServer = server;
        return store;
    }

    @SubscribeEvent
    static synchronized void onServerStopped(ServerStoppedEvent event) {
        bound = null;
        boundServer = null;
        DETACHED.clear();
    }

    private record Slot(int index, String name, @Nullable Codec<?> codec, boolean set, boolean concurrent) {}

    /** one world's state: a table per slot, plus saved tables whose owning class hasn't loaded yet. */
    private static final class Store extends SavedData {
        static final SavedData.Factory<Store> FACTORY =
                new SavedData.Factory<>((Supplier<Store>) Store::new, Store::load, null);

        private Object[] tables = new Object[64];
        private final CompoundTag unclaimed = new CompoundTag();
        @Nullable
        private HolderLookup.Provider registries;

        private static Store load(CompoundTag tag, HolderLookup.Provider registries) {
            Store store = new Store();
            store.registries = registries;
            for (String key : tag.getAllKeys()) {
                store.unclaimed.put(key, tag.get(key));
            }
            return store;
        }

        Object table(Slot slot) {
            Object[] current = tables;
            if (slot.index() < current.length && current[slot.index()] != null) {
                return current[slot.index()];
            }
            return create(slot);
        }

        private synchronized Object create(Slot slot) {
            if (slot.index() >= tables.length) {
                tables = Arrays.copyOf(tables, Math.max(slot.index() + 1, tables.length * 2));
            }
            Object table = tables[slot.index()];
            if (table != null) {
                return table;
            }
            table = slot.set()
                    ? (slot.concurrent() ? ConcurrentHashMap.newKeySet() : new HashSet<UUID>())
                    : (slot.concurrent() ? new ConcurrentHashMap<UUID, Object>() : new HashMap<UUID, Object>());
            Tag saved = unclaimed.get(slot.name());
            if (saved != null && slot.codec() != null) {
                unclaimed.remove(slot.name());
                decode(slot, saved, table);
            }
            tables[slot.index()] = table;
            return table;
        }

        @SuppressWarnings("unchecked")
        private void decode(Slot slot, Tag saved, Object table) {
            if (slot.set() && saved instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    uuid(list.getString(i)).ifPresent(((Set<UUID>) table)::add);
                }
            } else if (saved instanceof CompoundTag map) {
                Codec<Object> codec = (Codec<Object>) slot.codec();
                for (String key : map.getAllKeys()) {
                    uuid(key).ifPresent(id -> codec.parse(ops(), map.get(key))
                            .resultOrPartial(err -> WitchMod.LOGGER.warn("[EffectState] {}: {}", slot.name(), err))
                            .ifPresent(value -> ((Map<UUID, Object>) table).put(id, value)));
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            this.registries = registries;
            for (String key : unclaimed.getAllKeys()) {
                tag.put(key, unclaimed.get(key).copy());
            }
            List<Slot> slots;
            synchronized (EffectState.class) {
                slots = List.copyOf(SLOTS);
            }
            for (Slot slot : slots) {
                Object table = slot.codec() != null && slot.index() < tables.length ? tables[slot.index()] : null;
                if (table == null) {
                    continue;
                }
                if (slot.set()) {
                    ListTag list = new ListTag();
                    for (UUID id : (Set<UUID>) table) {
                        list.add(StringTag.valueOf(id.toString()));
                    }
                    tag.put(slot.name(), list);
                } else {
                    Codec<Object> codec = (Codec<Object>) slot.codec();
                    CompoundTag map = new CompoundTag();
                    for (Map.Entry<UUID, Object> entry : ((Map<UUID, Object>) table).entrySet()) {
                        codec.encodeStart(ops(), entry.getValue())
                                .resultOrPartial(err -> WitchMod.LOGGER.warn("[EffectState] {}: {}", slot.name(), err))
                                .ifPresent(value -> map.put(entry.getKey().toString(), value));
                    }
                    tag.put(slot.name(), map);
                }
            }
            return tag;
        }

        /** values change in place all the time, so always write on save (it's small). */
        @Override
        public boolean isDirty() {
            return true;
        }

        private DynamicOps<Tag> ops() {
            return registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
        }

        synchronized void clear() {
            Arrays.fill(tables, null);
            unclaimed.getAllKeys().clear();
        }

        private static java.util.Optional<UUID> uuid(String text) {
            try {
                return java.util.Optional.of(UUID.fromString(text));
            } catch (IllegalArgumentException e) {
                return java.util.Optional.empty();
            }
        }
    }

    /** a live map view onto one slot of the running world's store. */
    private static final class StateMap<T> implements Map<UUID, T> {
        private final Slot slot;

        StateMap(Slot slot) {
            this.slot = slot;
        }

        @SuppressWarnings("unchecked")
        private Map<UUID, T> map() {
            return (Map<UUID, T>) store().table(slot);
        }

        @Override public int size() { return map().size(); }
        @Override public boolean isEmpty() { return map().isEmpty(); }
        @Override public boolean containsKey(Object key) { return map().containsKey(key); }
        @Override public boolean containsValue(Object value) { return map().containsValue(value); }
        @Override public T get(Object key) { return map().get(key); }
        @Override public T put(UUID key, T value) { return map().put(key, value); }
        @Override public T remove(Object key) { return map().remove(key); }
        @Override public void putAll(Map<? extends UUID, ? extends T> m) { map().putAll(m); }
        @Override public void clear() { map().clear(); }
        @Override public Set<UUID> keySet() { return map().keySet(); }
        @Override public Collection<T> values() { return map().values(); }
        @Override public Set<Entry<UUID, T>> entrySet() { return map().entrySet(); }
        @Override public T getOrDefault(Object key, T fallback) { return map().getOrDefault(key, fallback); }
        @Override public void forEach(BiConsumer<? super UUID, ? super T> action) { map().forEach(action); }
        @Override public void replaceAll(BiFunction<? super UUID, ? super T, ? extends T> fn) { map().replaceAll(fn); }
        @Override public T putIfAbsent(UUID key, T value) { return map().putIfAbsent(key, value); }
        @Override public boolean remove(Object key, Object value) { return map().remove(key, value); }
        @Override public boolean replace(UUID key, T oldValue, T newValue) { return map().replace(key, oldValue, newValue); }
        @Override public T replace(UUID key, T value) { return map().replace(key, value); }
        @Override public T computeIfAbsent(UUID key, Function<? super UUID, ? extends T> fn) { return map().computeIfAbsent(key, fn); }
        @Override public T computeIfPresent(UUID key, BiFunction<? super UUID, ? super T, ? extends T> fn) { return map().computeIfPresent(key, fn); }
        @Override public T compute(UUID key, BiFunction<? super UUID, ? super T, ? extends T> fn) { return map().compute(key, fn); }
        @Override public T merge(UUID key, T value, BiFunction<? super T, ? super T, ? extends T> fn) { return map().merge(key, value, fn); }
        @Override public boolean equals(Object o) { return map().equals(o); }
        @Override public int hashCode() { return map().hashCode(); }
        @Override public String toString() { return slot.name() + map(); }
    }

    /** a live set view onto one slot of the running world's store. */
    private static final class StateSet implements Set<UUID> {
        private final Slot slot;

        StateSet(Slot slot) {
            this.slot = slot;
        }

        @SuppressWarnings("unchecked")
        private Set<UUID> set() {
            return (Set<UUID>) store().table(slot);
        }

        @Override public int size() { return set().size(); }
        @Override public boolean isEmpty() { return set().isEmpty(); }
        @Override public boolean contains(Object o) { return set().contains(o); }
        @Override public Iterator<UUID> iterator() { return set().iterator(); }
        @Override public Object[] toArray() { return set().toArray(); }
        @Override public <A> A[] toArray(A[] a) { return set().toArray(a); }
        @Override public boolean add(UUID id) { return set().add(id); }
        @Override public boolean remove(Object o) { return set().remove(o); }
        @Override public boolean containsAll(Collection<?> c) { return set().containsAll(c); }
        @Override public boolean addAll(Collection<? extends UUID> c) { return set().addAll(c); }
        @Override public boolean retainAll(Collection<?> c) { return set().retainAll(c); }
        @Override public boolean removeAll(Collection<?> c) { return set().removeAll(c); }
        @Override public boolean removeIf(Predicate<? super UUID> filter) { return set().removeIf(filter); }
        @Override public void clear() { set().clear(); }
        @Override public void forEach(Consumer<? super UUID> action) { set().forEach(action); }
        @Override public Spliterator<UUID> spliterator() { return set().spliterator(); }
        @Override public boolean equals(Object o) { return set().equals(o); }
        @Override public int hashCode() { return set().hashCode(); }
        @Override public String toString() { return slot.name() + set(); }
    }
}
