package com.oliver.witchmod.effects.curses;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * something you eat really doesn't agree with you any more. On application one of
 * three diets is rolled; eating anything that diet forbids blinds and poisons you, and you only keep a
 * fraction of the hunger AND saturation it would normally give.
 *
 * <ul>
 *   <li><b>Vegetarian</b> — can't eat meat.</li>
 *   <li><b>Carnivore</b> — can't eat natural (plant) food.</li>
 *   <li><b>Clean eater</b> — can't eat magic food, and gets NO positive effects from potions.</li>
 * </ul>
 *
 * <p>Classification deliberately uses in-game data rather than hardcoded item lists, so modded foods work
 * automatically: meat is the vanilla {@code minecraft:meat} item tag; "magic" is anything whose food
 * component grants effects (or a potion that grants effects); "natural" is the remainder — a food that is
 * neither meat nor magic. Water bottles grant no effects, so they stay drinkable for clean eaters.
 *
 * <p>The victim is never told which diet they rolled (Rule 6) — they find out by eating. Numbers are
 * config-exposed (see {@link Config}). Triggered from {@code CurseEventHandler}'s item-use hooks.
 */
public final class CurseAllergic extends Effect {
    /** the three diets. Ordinals are persisted in {@link WitchModAttachments#ALLERGIC_DIET} — don't reorder. */
    public enum Diet {
        VEGETARIAN("Vegetarian — meat makes you violently ill"),
        CARNIVORE("Carnivore — plants make you violently ill"),
        CLEAN_EATER("Clean Eater — magic food and potions make you violently ill");

        private final String displayName;

        Diet(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public CurseAllergic() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SWEET_BERRIES);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        Diet rolled = Diet.values()[target.getRandom().nextInt(Diet.values().length)];
        target.setData(WitchModAttachments.ALLERGIC_DIET, rolled.ordinal());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.ALLERGIC_DIET, -1);
    }

    /** the Scrying Mirror is the one thing that names your exact allergy instead of making you find out. */
    @Override
    public Optional<String> scryingDetail(ServerPlayer target) {
        return Optional.of(dietOf(target).displayName());
    }

    /** you only learn you're Allergic when something you ate first disagrees with you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** the player's rolled diet, rolling one if it's somehow unset (self-heals old/!onApply'd data). */
    public static Diet dietOf(ServerPlayer player) {
        int index = player.getData(WitchModAttachments.ALLERGIC_DIET);
        if (index < 0 || index >= Diet.values().length) {
            Diet rolled = Diet.values()[player.getRandom().nextInt(Diet.values().length)];
            player.setData(WitchModAttachments.ALLERGIC_DIET, rolled.ordinal());
            return rolled;
        }
        return Diet.values()[index];
    }

    /** whether {@code stack} is off-limits for {@code diet}. Category-based, so modded foods are covered. */
    public static boolean isForbidden(Diet diet, ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        boolean meat = stack.is(ItemTags.MEAT);
        boolean magicFood = food != null && !food.effects().isEmpty();
        boolean magicPotion = potion != null && potion.hasEffects();

        return switch (diet) {
            case VEGETARIAN -> meat;
            case CLEAN_EATER -> magicFood || magicPotion;
            // "Natural" = plant-ish food: edible, but neither meat nor magic.
            case CARNIVORE -> food != null && !meat && !magicFood;
        };
    }

    /** the allergic reaction itself: blindness + poison. */
    public static void reactBadly(ServerPlayer player) {
        int blindTicks = Config.ALLERGIC_BLINDNESS_SECONDS.get() * 20;
        int poisonTicks = Config.ALLERGIC_POISON_SECONDS.get() * 20;
        if (blindTicks > 0) {
            EffectUtil.addTimedEffect(player, MobEffects.BLINDNESS, blindTicks, 0);
        }
        if (poisonTicks > 0) {
            EffectUtil.addTimedEffect(player, MobEffects.POISON, poisonTicks, Math.max(0, Config.ALLERGIC_POISON_LEVEL.get() - 1));
        }
    }

    /**
     * takes back most of what the food just gave. Called AFTER vanilla applied it (the item-use Finish event
     * fires post-application), using the pre-eat readings so the player keeps exactly
     * {@code allergicNutritionPercent}% of the REAL gain — correct even if they were nearly full and the
     * gain got clamped.
     */
    public static void reduceGain(ServerPlayer player, int foodBefore, float saturationBefore) {
        FoodData data = player.getFoodData();
        int keptPercent = Config.ALLERGIC_NUTRITION_PERCENT.get();

        int gained = data.getFoodLevel() - foodBefore;
        if (gained > 0) {
            data.setFoodLevel(foodBefore + gained * keptPercent / 100);
        }
        float saturationGained = data.getSaturationLevel() - saturationBefore;
        if (saturationGained > 0.0F) {
            data.setSaturation(saturationBefore + saturationGained * keptPercent / 100.0F);
        }
    }

    /** clean eaters get nothing good out of a potion — strip the beneficial effects it just applied. */
    public static void stripBeneficialEffects(ServerPlayer player, ItemStack stack) {
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion == null) {
            return;
        }
        for (MobEffectInstance instance : potion.getAllEffects()) {
            if (instance.getEffect().value().isBeneficial()) {
                player.removeEffect(instance.getEffect());
            }
        }
    }
}
