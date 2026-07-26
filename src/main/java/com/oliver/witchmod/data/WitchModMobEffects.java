package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/**
 * The three wrapper status effects from CLAUDE.md section 2.6. Purely informational — they carry no
 * mechanical behavior of their own; {@link StatusEffectSync} keeps their duration matched to whatever
 * curses/blessings/afflictions are actually active.
 */
public final class WitchModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, WitchMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> CURSED =
            MOB_EFFECTS.register("cursed", () -> new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8B00FF));
    public static final DeferredHolder<MobEffect, MobEffect> BLESSED =
            MOB_EFFECTS.register("blessed", () -> new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD700));
    public static final DeferredHolder<MobEffect, MobEffect> AFFLICTED =
            MOB_EFFECTS.register("afflicted", () -> new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0x800080));

    /**
     * Thirst Meter's Dehydration — Hunger, but for thirst: it makes the bar drain faster. The draining
     * itself lives in {@code CurseThirstMeter}; this is only the marker and the HUD icon.
     *
     * <p>Applied with {@code visible=false} so it shows NO ambient particles (Oliver's call — it comes and
     * goes far too often to be spewing swirls the whole time) but keeps {@code showIcon=true} so the effect
     * bar still tells you why the bar is emptying.
     */
    public static final DeferredHolder<MobEffect, MobEffect> DEHYDRATION =
            MOB_EFFECTS.register("dehydration", () -> new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x4A90C2));

    /**
     * Soul Bond's marker on the tethered entity — nearest living thing to the caster. It carries no behaviour
     * of its own (the damage-sharing and particles are driven by {@code BlessingSoulBond} on the caster);
     * this is the icon and the "you are bound" tell. Applied {@code visible=false} so it shows no vanilla
     * swirls — the bond has its own custom golden particles instead — but {@code showIcon=true} for the bar.
     * Gold, to match the Totem of Undying it's cast with.
     */
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_BOUND =
            MOB_EFFECTS.register("soul_bound", () -> new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFC83C));

    private WitchModMobEffects() {}

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }

    /** {@link MobEffect}'s constructor is protected; this just exposes it. */
    private static final class SimpleMobEffect extends MobEffect {
        SimpleMobEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
