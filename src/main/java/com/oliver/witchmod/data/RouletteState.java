package com.oliver.witchmod.data;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * what a pandora's box / cornucopia is holding on a player: the effects it granted (oldest first) and the game
 * tick of the next swap. persisted so a relog keeps the rotation.
 */
public record RouletteState(List<ResourceLocation> owned, long nextSwap) {
    public static final RouletteState EMPTY = new RouletteState(List.of(), 0L);

    public static final Codec<RouletteState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("owned").forGetter(RouletteState::owned),
            Codec.LONG.fieldOf("next_swap").forGetter(RouletteState::nextSwap)
    ).apply(i, RouletteState::new));

    public boolean owns(ResourceLocation id) {
        return owned.contains(id);
    }
}
