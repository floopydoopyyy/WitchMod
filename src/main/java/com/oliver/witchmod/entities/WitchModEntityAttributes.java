package com.oliver.witchmod.entities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Attributes for the mod's custom entities. Registered directly on the MOD event bus from
 * {@code WitchMod}'s constructor — {@code @EventBusSubscriber(bus = MOD)} is deprecated in NeoForge 21.1.
 */
public final class WitchModEntityAttributes {
    private WitchModEntityAttributes() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(WitchModEntityAttributes::onCreateAttributes);
    }

    private static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(WitchModEntities.TAX_MAN.get(), TaxManEntity.createAttributes().build());
        event.put(WitchModEntities.BODYGUARD.get(), BodyguardEntity.createAttributes().build());
        event.put(WitchModEntities.SNAIL.get(), SnailEntity.createAttributes().build());
        event.put(WitchModEntities.SPAGHETTI_MAN.get(), SpaghettiManEntity.createAttributes().build());
        event.put(WitchModEntities.WATCHER_EYES.get(), WatcherEyesEntity.createAttributes().build());
        event.put(WitchModEntities.DREAM.get(), DreamEntity.createAttributes().build());
        event.put(WitchModEntities.CLONE.get(), CloneEntity.createAttributes().build());
    }
}
