package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.WitchMod;

/**
 * The mod's own damage types. These are DATAPACK entries, not code registrations — the JSON lives at
 * {@code data/witchmod/damage_type/} and this class just holds the keys to look them up with.
 */
public final class WitchModDamageTypes {
    /**
     * Thirst Meter: dying of thirst. Deliberately <b>fatal on every difficulty</b> — vanilla starvation
     * stops short of killing you on Peaceful and Easy ({@code FoodData.tick} checks the difficulty before
     * dealing its damage), but this is applied directly and has no such check, so an empty bar is lethal
     * wherever you are.
     *
     * <p>{@code scaling: never} keeps the number the same on every difficulty, and the type is added to
     * {@code minecraft:bypasses_armor} — no amount of netherite helps you not be thirsty.
     */
    public static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "dehydration"));

    /** Basement Dweller: the sun doesn't agree with you. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> SUNBURN = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "sunburn"));

    /** Claustrophobia: the walls get to you. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> CAVE_DREAD = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "cave_dread"));

    /** Soul Bond: the share of the caster's damage the tethered entity takes. Bypasses armour, no knockback. */
    public static final ResourceKey<DamageType> SOUL_BOND = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "soul_bond"));

    private WitchModDamageTypes() {}

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
