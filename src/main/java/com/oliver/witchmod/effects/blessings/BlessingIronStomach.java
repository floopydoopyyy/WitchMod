package com.oliver.witchmod.effects.blessings;

import java.util.Map;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * cast-iron guts: you can eat any of the game's
 * "bad" foods with NO penalty, and you actually get MORE hunger and saturation out of them than anyone else
 * would. The work — stripping the food's negative effects and granting the bonus nutrition — happens on the
 * eat, in the hook below.
 */
public final class BlessingIronStomach extends Effect {
    /**
     * the vanilla foods that come with a downside — the ones this blessing is about. Kept a curated set rather
     * than reflecting each food's effects, which changed shape across 1.21.x; modded bad foods aren't covered
     * automatically, which is an acceptable simplification for now.
     */
    public static final Set<Item> BAD_FOODS = Set.of(
            Items.ROTTEN_FLESH, Items.CHICKEN, Items.PUFFERFISH, Items.POISONOUS_POTATO, Items.SPIDER_EYE);

    /**
     * extra normally-INEDIBLE things a cast-iron gut can stomach → the hunger each restores. Eaten via a
     * right-click (they have no vanilla use), handled in the hook below;
     * a glistering melon slice also grants a short burst of Regeneration. Curated data, like {@link #BAD_FOODS}.
     */
    public static final Map<Item, Integer> EXTRA_FOODS = Map.of(
            Items.GLISTERING_MELON_SLICE, 6,
            Items.FERMENTED_SPIDER_EYE, 6,
            Items.EGG, 1,
            Items.SUGAR, 1,
            Items.SUGAR_CANE, 2,
            Items.RED_MUSHROOM, 2,
            Items.BROWN_MUSHROOM, 2,
            Items.NETHER_WART, 4,
            Items.COCOA_BEANS, 1);

    public BlessingIronStomach() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CHICKEN);
    }

    /** you find out the first time you wolf down something rank and feel great for it (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /**
     * eating a "bad" food strips its downside (the food effects it just applied) and gives you more hunger and
     * saturation from it than normal. fires right after vanilla has applied the food.
     */
    @Override
    public void onUseFinish(ServerPlayer self, LivingEntityUseItemEvent.Finish event) {
        if (!BAD_FOODS.contains(event.getItem().getItem())) {
            return;
        }
        self.removeEffect(MobEffects.HUNGER);
        self.removeEffect(MobEffects.POISON);
        self.removeEffect(MobEffects.CONFUSION);
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food != null) {
            int bonusNutrition = (int) Math.ceil(food.nutrition() * (Config.IRONSTOMACH_HUNGER_MULT.get() - 1.0));
            float bonusSaturationMod = (float) (food.saturation() * Config.IRONSTOMACH_SATURATION_MULT.get());
            if (bonusNutrition > 0) {
                self.getFoodData().eat(bonusNutrition, bonusSaturationMod);
            }
        }
        markDiscoveredByVictim(self);
    }

    /**
     * a cast-iron gut can wolf down normally-inedible things (glistering melon, sugar, cane, mushrooms...). they
     * have no vanilla use, so a right-click in the air eats one; a glistering melon slice also grants regeneration.
     * planting cane / cocoa / wart is a block interaction, so only the air right-click is caught here.
     */
    @Override
    public void onRightClickItem(ServerPlayer self, PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Integer hunger = EXTRA_FOODS.get(stack.getItem());
        if (hunger == null) {
            return;
        }
        boolean melon = stack.is(Items.GLISTERING_MELON_SLICE);
        if (self.getFoodData().getFoodLevel() >= 20 && !melon) {
            return; // already full and nothing else to gain: leave the item be
        }
        self.getFoodData().eat(hunger, hunger * Config.IRONSTOMACH_EXTRA_SATURATION.get().floatValue());
        if (melon) {
            self.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    Config.IRONSTOMACH_MELON_REGEN_SECONDS.get() * 20,
                    Config.IRONSTOMACH_MELON_REGEN_LEVEL.get() - 1));
        }
        ItemStack particleItem = stack.copyWithCount(1); // before consuming, so the crunch shows the right item
        stack.consume(1, self);
        self.swing(event.getHand());
        ServerLevel level = self.serverLevel();
        level.playSound(null, self.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.9F, 1.0F);
        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, particleItem),
                self.getX(), self.getEyeY() - 0.2, self.getZ(), 8, 0.2, 0.15, 0.2, 0.05);
        markDiscoveredByVictim(self);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
