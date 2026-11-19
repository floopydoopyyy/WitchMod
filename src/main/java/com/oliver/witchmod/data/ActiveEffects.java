package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;

/** the curses/blessings active on one player. persisted per-player; mutated only through {@link EffectManager}. */
public final class ActiveEffects {
    public static final Codec<ActiveEffects> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, ActiveEffectInstance.CODEC)
            .xmap(ActiveEffects::new, ActiveEffects::asMap);

    private static final Hooked[] NONE = new Hooked[0];

    private final Map<ResourceLocation, ActiveEffectInstance> effects;
    /** {@link #hooked()}'s cache; null after the set of effects changes. */
    private Hooked[] hooked;

    private ActiveEffects(Map<ResourceLocation, ActiveEffectInstance> effects) {
        this.effects = new LinkedHashMap<>(effects);
    }

    public static ActiveEffects empty() {
        return new ActiveEffects(Map.of());
    }

    private Map<ResourceLocation, ActiveEffectInstance> asMap() {
        return effects;
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public boolean isActive(ResourceLocation id) {
        return effects.containsKey(id);
    }

    public Optional<ActiveEffectInstance> get(ResourceLocation id) {
        return Optional.ofNullable(effects.get(id));
    }

    public Set<ResourceLocation> activeIds() {
        return Set.copyOf(effects.keySet());
    }

    public int size() {
        return effects.size();
    }

    void put(ResourceLocation id, ActiveEffectInstance instance) {
        if (effects.put(id, instance) == null) {
            hooked = null;
        }
    }

    ActiveEffectInstance remove(ResourceLocation id) {
        ActiveEffectInstance removed = effects.remove(id);
        if (removed != null) {
            hooked = null;
        }
        return removed;
    }

    /** an active effect with its id. */
    public record Hooked(ResourceLocation id, Effect effect) {}

    /**
     * the active effects in hook order — {@link Effect#hookPriority()} first, then registration order. cached
     * until the set changes, and never mutated, so it's safe to walk while hooks add or remove effects.
     */
    public Hooked[] hooked() {
        Hooked[] cached = hooked;
        if (cached != null) {
            return cached;
        }
        if (effects.isEmpty()) {
            return hooked = NONE;
        }
        List<Hooked> list = new ArrayList<>(effects.size());
        for (ResourceLocation id : effects.keySet()) {
            Effect effect = WitchModRegistries.EFFECT_REGISTRY.get(id);
            if (effect != null) {
                list.add(new Hooked(id, effect));
            }
        }
        list.sort(Comparator.comparingInt((Hooked h) -> -h.effect().hookPriority())
                .thenComparingInt(h -> WitchModRegistries.EFFECT_REGISTRY.getId(h.effect())));
        return hooked = list.toArray(NONE);
    }

    /** decrement every effect by one tick; returns ids that just expired. */
    List<ResourceLocation> tickDown() {
        return reduceAll(1);
    }

    /** decrement every effect by {@code amount} ticks; returns ids that just expired. */
    List<ResourceLocation> reduceAll(int amount) {
        List<ResourceLocation> expired = new ArrayList<>();
        for (var entry : effects.entrySet()) {
            int remaining = entry.getValue().remainingTicks() - amount;
            if (remaining <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(entry.getValue().withRemainingTicks(remaining));
            }
        }
        expired.forEach(effects::remove);
        if (!expired.isEmpty()) {
            hooked = null;
        }
        return expired;
    }
}
