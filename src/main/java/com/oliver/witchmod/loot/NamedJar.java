package com.oliver.witchmod.loot;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * one predetermined "jar of X" preset — a named jar holding a fixed set of effects, dropped from loot chests
 * and certain mobs. rarity is 1 (common) to 10 (rarest) and only sets the pick weight; the real drop rates
 * live in config. see {@link NamedJars} for the list and how weight is derived.
 *
 * <p>{@code variants} lets ONE preset roll one of several interchangeable effect sets at fill time (same pick
 * odds as any single jar); empty means it always bottles {@code effectIds}. {@code effectIds} is the
 * representative set used to derive the jar's cursed/blessed/mixed variant.
 */
public record NamedJar(ResourceLocation id, String translationKey, int defaultRarity,
                       List<ResourceLocation> effectIds, List<List<ResourceLocation>> variants) {
    /** the effect set to actually bottle: a random variant if any, else the single {@code effectIds}. */
    public List<ResourceLocation> rollEffects(RandomSource rng) {
        return variants.isEmpty() ? effectIds : variants.get(rng.nextInt(variants.size()));
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }
}
