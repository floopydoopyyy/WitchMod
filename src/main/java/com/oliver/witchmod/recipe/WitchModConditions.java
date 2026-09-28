package com.oliver.witchmod.recipe;

import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.oliver.witchmod.WitchMod;

/** registers the mod's datapack conditions (currently the config-backed recipe toggle). */
public final class WitchModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, WitchMod.MODID);

    public static final Object RECIPE_ENABLED =
            CONDITIONS.register("recipe_enabled", () -> RecipeEnabledCondition.CODEC);

    private WitchModConditions() {}

    public static void register(IEventBus modEventBus) {
        CONDITIONS.register(modEventBus);
    }
}
