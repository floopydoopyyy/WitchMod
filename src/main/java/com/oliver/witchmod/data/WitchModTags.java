package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import com.oliver.witchmod.WitchMod;

/** the mod's own tags. definitions are datapack json under {@code data/witchmod/tags/}. */
public final class WitchModTags {
    /** what the tax man takes — ore lines + their ingots/gems/blocks, minus redstone and coal. a tag so modded ores can be added without code. */
    public static final TagKey<Item> VALUABLES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "valuables"));

    private WitchModTags() {}
}
