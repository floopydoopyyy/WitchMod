package com.oliver.witchmod.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/**
 * the informational wrapper status effects (cursed/blessed + a few markers). no behaviour of their own;
 * {@link StatusEffectSync} keeps their duration matched to whatever's actually active.
 */
public final class WitchModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, WitchMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> CURSED =
            MOB_EFFECTS.register("cursed", () -> new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x8B00FF));
    public static final DeferredHolder<MobEffect, MobEffect> BLESSED =
            MOB_EFFECTS.register("blessed", () -> new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0xFFD700));

    /** thirst meter's dehydration marker + hud icon (the draining lives in CurseThirstMeter). icon only, no swirls. */
    public static final DeferredHolder<MobEffect, MobEffect> DEHYDRATION =
            MOB_EFFECTS.register("dehydration", () -> new SimpleMobEffect(MobEffectCategory.HARMFUL, 0x4A90C2));

    /** soul bond's marker on the tethered entity — icon-only "you are bound" tell; the sharing lives in BlessingSoulBond. */
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_BOUND =
            MOB_EFFECTS.register("soul_bound", () -> new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFC83C));

    /** warding totem / holy water: protected — no curse/blessing/voodoo lands. icon only, no swirl. */
    public static final DeferredHolder<MobEffect, MobEffect> PROTECTED =
            MOB_EFFECTS.register("protected", () -> new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x9B59D0));

    /** unseen blessing: icon-only marker shown while you're actually cloaked. */
    public static final DeferredHolder<MobEffect, MobEffect> UNSEEN =
            MOB_EFFECTS.register("unseen", () -> new SimpleMobEffect(MobEffectCategory.BENEFICIAL, 0x37374F));

    private WitchModMobEffects() {}

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }

    /** exposes MobEffect's protected constructor. */
    private static final class SimpleMobEffect extends MobEffect {
        SimpleMobEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
