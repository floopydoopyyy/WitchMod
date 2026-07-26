package com.oliver.witchmod.entities;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** The mod's custom entities (master-spec Section 0.4). */
public final class WitchModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WitchMod.MODID);

    /**
     * The Tax Man. {@code MobCategory.MISC} so natural spawning never touches him — he is only ever placed
     * deliberately by the Taxes curse — and player-sized so the humanoid model fits.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<TaxManEntity>> TAX_MAN =
            ENTITY_TYPES.register("tax_man", () -> EntityType.Builder
                    .of(TaxManEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(16)
                    .build("tax_man"));

    /**
     * The Bodyguard. {@code MobCategory.MISC} so it never spawns naturally — it's only ever summoned by the
     * blessing — and skeleton-sized so the skeleton model fits.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BodyguardEntity>> BODYGUARD =
            ENTITY_TYPES.register("bodyguard", () -> EntityType.Builder
                    .of(BodyguardEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.99F)
                    .clientTrackingRange(16)
                    .build("bodyguard"));

    private WitchModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
