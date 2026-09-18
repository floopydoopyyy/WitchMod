package com.oliver.witchmod.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/**
 * the effect registry (curses + blessings). synced to clients so the compendium can list every effect by id
 * without a server lookup for undiscovered "rumour" entries.
 */
public final class WitchModRegistries {
    public static final ResourceKey<Registry<Effect>> EFFECT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "effects"));

    public static final DeferredRegister<Effect> EFFECTS = DeferredRegister.create(EFFECT_REGISTRY_KEY, WitchMod.MODID);

    public static final Registry<Effect> EFFECT_REGISTRY = EFFECTS.makeRegistry(builder -> builder.sync(true));

    private WitchModRegistries() {}

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
