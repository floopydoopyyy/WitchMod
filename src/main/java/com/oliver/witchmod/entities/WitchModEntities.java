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

    /** A thrown jar — the splash-potion projectile. Potion-sized, short tracking, frequent position updates. */
    public static final DeferredHolder<EntityType<?>, EntityType<JarThrowEntity>> JAR_THROW =
            ENTITY_TYPES.register("jar_throw", () -> EntityType.Builder
                    .<JarThrowEntity>of(JarThrowEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("jar_throw"));

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

    /** The immortal Snail. {@code MobCategory.MISC} so it never spawns naturally — only the curse places it. Tiny. */
    public static final DeferredHolder<EntityType<?>, EntityType<SnailEntity>> SNAIL =
            ENTITY_TYPES.register("snail", () -> EntityType.Builder
                    .of(SnailEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.4F)
                    .clientTrackingRange(10)
                    .build("snail"));

    /**
     * The Spaghetti Man (The Dweller curse). {@code MobCategory.MISC} so it never spawns naturally — only the
     * curse places it — tall and thin. A wide client-tracking range so it can lurk at a distance and still be
     * rendered for its victim.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SpaghettiManEntity>> SPAGHETTI_MAN =
            ENTITY_TYPES.register("spaghetti_man", () -> EntityType.Builder
                    .of(SpaghettiManEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.5F)
                    .clientTrackingRange(12)
                    .build("spaghetti_man"));

    /** The Cutaway Gag's "Dream" player-mimic. Player-sized; never spawns naturally — only the gag places it. */
    public static final DeferredHolder<EntityType<?>, EntityType<DreamEntity>> DREAM =
            ENTITY_TYPES.register("dream", () -> EntityType.Builder
                    .of(DreamEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(16)
                    .build("dream"));

    /** Blessing of Confusion's doppelganger — an exact clone of the caster. Never spawns naturally. */
    public static final DeferredHolder<EntityType<?>, EntityType<CloneEntity>> CLONE =
            ENTITY_TYPES.register("clone", () -> EntityType.Builder
                    .of(CloneEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(16)
                    .build("clone"));

    /** The Dweller's "watchers" — disembodied glowing eyes in the dark. Tiny, never spawns naturally. */
    public static final DeferredHolder<EntityType<?>, EntityType<WatcherEyesEntity>> WATCHER_EYES =
            ENTITY_TYPES.register("watcher_eyes", () -> EntityType.Builder
                    .of(WatcherEyesEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(12)
                    .build("watcher_eyes"));

    private WitchModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
