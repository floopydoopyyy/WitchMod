package com.oliver.witchmod.data;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/**
 * what the tax man thinks things are worth — per-item weighting so a haul cap means the same across copper
 * vs netherite. values come from config (parsed once + cached); an untagged-but-valuable item falls back to
 * the default, so modded ores get a sensible worth for free.
 */
public final class TaxValues {
    private static Map<ResourceLocation, Integer> values;

    private TaxValues() {}

    /** worth of a whole stack. */
    public static int valueOf(ItemStack stack) {
        return valuePerItem(stack) * stack.getCount();
    }

    public static int valuePerItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(WitchModTags.VALUABLES)) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return values().getOrDefault(id, Config.TAXES_DEFAULT_ITEM_VALUE.get());
    }

    private static Map<ResourceLocation, Integer> values() {
        if (values == null) {
            values = parse();
        }
        return values;
    }

    /** re-parse on config reload so edits apply without a restart. */
    public static void invalidate() {
        values = null;
    }

    private static Map<ResourceLocation, Integer> parse() {
        Map<ResourceLocation, Integer> parsed = new HashMap<>();
        for (String entry : Config.TAXES_ITEM_VALUES.get()) {
            String[] halves = entry.split("=", 2);
            if (halves.length != 2) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(halves[0].trim());
            if (id == null) {
                WitchMod.LOGGER.warn("[Taxes] Skipping unparseable item in taxesItemValues: {}", entry);
                continue;
            }
            try {
                parsed.put(id, Integer.parseInt(halves[1].trim()));
            } catch (NumberFormatException e) {
                WitchMod.LOGGER.warn("[Taxes] Skipping non-numeric value in taxesItemValues: {}", entry);
            }
        }
        return Map.copyOf(parsed);
    }
}
