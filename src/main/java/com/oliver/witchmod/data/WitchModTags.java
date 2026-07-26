package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import com.oliver.witchmod.WitchMod;

/** The mod's own tags. Definitions are datapack JSON under {@code data/witchmod/tags/}. */
public final class WitchModTags {
    /**
     * What the Tax Man considers worth taking (master-spec Taxes): essentially every ore line and its
     * ingots, gems and storage blocks, deliberately EXCLUDING redstone and coal.
     *
     * <p>Kept as a datapack tag rather than a hardcoded list so it can be edited without touching code, and
     * so modded ores can be added to it by anyone.
     */
    public static final TagKey<Item> VALUABLES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "valuables"));

    private WitchModTags() {}
}
