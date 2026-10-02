package com.oliver.witchmod.effects.curses;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCure;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModMobEffects;

/**
 * the allergic reaction potion effect — amplifier 0..2 = tier 1..3. slows the attack cooldown and takes hearts of
 * max health per tier (always leaving one). nothing cures it but time (no milk, no totem); leaving the allergen's
 * area is the counterplay.
 */
public final class AllergicReaction extends MobEffect {
    private static final ResourceLocation ATTACK_ID = EffectUtil.modifierId("allergic_attack_cooldown");
    public static final ResourceLocation HEALTH_ID = EffectUtil.modifierId("allergic_heart_loss");
    /** the hard floor: one heart always survives, whatever else is shrinking your max health. */
    private static final double MIN_HEALTH = 2.0;

    public AllergicReaction() {
        super(MobEffectCategory.HARMFUL, 0x5DBB2F);
    }

    /** the reaction tier on this entity right now, 0 if none. */
    public static int tierOf(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(WitchModMobEffects.ALLERGIC_REACTION);
        return instance == null ? 0 : Math.min(3, instance.getAmplifier() + 1);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // deliberately empty: milk and totems don't touch it.
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(attributeMap, amplifier);
        applyModifiers(attributeMap, Math.min(3, amplifier + 1));
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        AttributeInstance attack = attributeMap.getInstance(Attributes.ATTACK_SPEED);
        if (attack != null) {
            attack.removeModifier(ATTACK_ID);
        }
        AttributeInstance health = attributeMap.getInstance(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_ID);
        }
    }

    /**
     * sets the tier's attack-speed and heart-loss modifiers. also re-run periodically, since other effects can
     * change max health underneath us. the heart loss is a final multiplier sized so the result lands exactly
     * on (max without us − lost hearts), clamped to one heart.
     */
    public static void applyModifiers(AttributeMap attributeMap, int tier) {
        AttributeInstance attack = attributeMap.getInstance(Attributes.ATTACK_SPEED);
        if (attack != null) {
            double longer = cooldownPercent(tier) / 100.0;
            attack.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_ID, 1.0 / (1.0 + longer) - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance health = attributeMap.getInstance(Attributes.MAX_HEALTH);
        if (health != null) {
            AttributeModifier ours = health.getModifier(HEALTH_ID);
            double without = health.getValue() / (1.0 + (ours == null ? 0.0 : ours.amount()));
            double target = Math.max(MIN_HEALTH, without - heartsLost(tier) * 2.0);
            double factor = without > MIN_HEALTH ? target / without - 1.0 : 0.0;
            if (ours == null || Math.abs(ours.amount() - factor) > 1.0E-4) {
                health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID, factor,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static int cooldownPercent(int tier) {
        return switch (tier) {
            case 1 -> Config.ALLERGIC_TIER1_COOLDOWN_PERCENT.get();
            case 2 -> Config.ALLERGIC_TIER2_COOLDOWN_PERCENT.get();
            default -> Config.ALLERGIC_TIER3_COOLDOWN_PERCENT.get();
        };
    }

    private static int heartsLost(int tier) {
        return switch (tier) {
            case 1 -> Config.ALLERGIC_TIER1_HEARTS_LOST.get();
            case 2 -> Config.ALLERGIC_TIER2_HEARTS_LOST.get();
            default -> Config.ALLERGIC_TIER3_HEARTS_LOST.get();
        };
    }

    /** fov narrowing for the tier; safe to call before the server config reaches the client. */
    public static float fovReduction(int tier) {
        try {
            return switch (tier) {
                case 1 -> Config.ALLERGIC_TIER1_FOV_REDUCTION.get().floatValue();
                case 2 -> Config.ALLERGIC_TIER2_FOV_REDUCTION.get().floatValue();
                case 3 -> Config.ALLERGIC_TIER3_FOV_REDUCTION.get().floatValue();
                default -> 0.0F;
            };
        } catch (IllegalStateException e) {
            return 0.0F;
        }
    }
}
