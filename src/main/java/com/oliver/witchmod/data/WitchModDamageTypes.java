package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.WitchMod;

/** the mod's own damage types — datapack entries under {@code data/witchmod/damage_type/}; this just holds the keys. */
public final class WitchModDamageTypes {
    /** thirst meter: dying of thirst — fatal on every difficulty (applied directly, unlike vanilla starvation), bypasses armour. */
    public static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "dehydration"));

    /** basement Dweller: the sun doesn't agree with you. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> SUNBURN = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "sunburn"));

    /** claustrophobia: the walls get to you. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> CAVE_DREAD = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "cave_dread"));

    /** soul Bond: the share of the caster's damage the tethered entity takes. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> SOUL_BOND = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "soul_bond"));

    /** cutaway Gag (I Like Trains): flattened by a runaway minecart. Bypasses armour; DOES knock you flying. */
    public static final ResourceKey<DamageType> TRAIN = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "train"));

    /** cutaway Gag (Bowling): bowled over. Bypasses armour; DOES knock you flying. */
    public static final ResourceKey<DamageType> BOWLING = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "bowling"));

    /** cutaway Gag (Dream): beaten up by the player-mimic. Attributed to it for a bespoke death message. */
    public static final ResourceKey<DamageType> DREAM = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "dream"));

    /**
     * The Dweller: caught by the hunt. Bypasses armour AND invulnerability (a creative player dies too), no
     * knockback, and carries its own death message.
     */
    public static final ResourceKey<DamageType> THE_DWELLER = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "haunted"));

    /** giant: crushed underfoot by the giant. Attributed to it for a bespoke death message; DOES knock you flying. */
    public static final ResourceKey<DamageType> STOMP = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "stomp"));

    /** bewitching Table backfire: the ritual turns on the caster. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> RITUAL_BACKFIRE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "ritual_backfire"));

    /** cutaway Gag (Parade): trampled by the marching column. Bypasses armour. */
    public static final ResourceKey<DamageType> PARADE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "parade"));

    /** voodoo Doll (Needle stab): bypasses armour and SCALES WITH it — the more armour, the worse the jab. */
    public static final ResourceKey<DamageType> VOODOO = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "voodoo"));

    /** guardian Angel (Zap): the guardian's bolt. Attributed to the owner so kills name them; no knockback. */
    public static final ResourceKey<DamageType> GUARDIAN_ZAP = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "guardian_zap"));

    private WitchModDamageTypes() {}

    /** the guardian's zap, credited to the owner (causing entity) with no direct entity → no knockback. */
    public static DamageSource guardianZap(Level level, net.minecraft.world.entity.Entity owner) {
        return level.damageSources().source(GUARDIAN_ZAP, null, owner);
    }

    /** the Needle's voodoo jab, attributed to the caster so kills are theirs. */
    public static DamageSource voodoo(Level level, net.minecraft.world.entity.Entity caster) {
        return level.damageSources().source(VOODOO, caster);
    }

    public static DamageSource ritualBackfire(Level level) {
        return level.damageSources().source(RITUAL_BACKFIRE);
    }

    public static DamageSource parade(Level level) {
        return level.damageSources().source(PARADE);
    }

    /** the Giant's stomp, attributed to the giant so kills are theirs and the death message names them. */
    public static DamageSource stomp(Level level, net.minecraft.world.entity.Entity giant) {
        return level.damageSources().source(STOMP, giant);
    }

    public static DamageSource theDweller(Level level) {
        return level.damageSources().source(THE_DWELLER);
    }

    /** dream's punch, attributed to the mimic so the death message names it. */
    public static DamageSource dream(Level level, net.minecraft.world.entity.Entity dream) {
        return level.damageSources().source(DREAM, dream);
    }

    public static DamageSource train(Level level) {
        return level.damageSources().source(TRAIN);
    }

    public static DamageSource bowling(Level level) {
        return level.damageSources().source(BOWLING);
    }

    public static DamageSource dehydration(Level level) {
        return level.damageSources().source(DEHYDRATION);
    }

    public static DamageSource sunburn(Level level) {
        return level.damageSources().source(SUNBURN);
    }

    public static DamageSource caveDread(Level level) {
        return level.damageSources().source(CAVE_DREAD);
    }

    /**
     * Soul Bond's shared damage, attributed to the CASTER so kills are theirs (and the death message reads
     * sensibly). The two-arg {@code source} sets the causing entity to the bond owner.
     */
    public static DamageSource soulBond(Level level, net.minecraft.world.entity.Entity caster) {
        return level.damageSources().source(SOUL_BOND, caster);
    }
}
