package com.oliver.witchmod.data;

import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** custom item data components for player-essence/voodoo binding and jar contents. */
public final class WitchModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WitchMod.MODID);

    /** which player an essence/doll is bound to; absent when unbound. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlayerEssenceData>> BOUND_PLAYER =
            DATA_COMPONENTS.registerComponentType("bound_player", builder -> builder
                    .persistent(PlayerEssenceData.CODEC)
                    .networkSynchronized(PlayerEssenceData.STREAM_CODEC));

    /** effects a jar holds, awaiting release on a throw. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<CapturedEffect>>> CAPTURED_EFFECTS =
            DATA_COMPONENTS.registerComponentType("captured_effects", builder -> builder
                    .persistent(CapturedEffect.CODEC.listOf())
                    .networkSynchronized(CapturedEffect.STREAM_CODEC.apply(ByteBufCodecs.list(16))));

    /** marks a jar as a named preset (a "jar of mining" etc.) — the id drives its display name. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> NAMED_JAR =
            DATA_COMPONENTS.registerComponentType("named_jar", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    private WitchModDataComponents() {}

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
