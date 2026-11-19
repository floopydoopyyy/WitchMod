package com.oliver.witchmod.effects.curses;

import java.util.Map;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.effects.Curses;

/**
 * allergic, gluttony and munchies all measure the real gain from a meal and interlock (an allergen gives gluttony
 * nothing to spill; munchies compounds after gluttony; allergic claws back last), so they share one listener
 * rather than three hooks. low priority: runs after the effect hooks (thirst, bedrock's food reg) have had the
 * meal, as before.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class EatingCurses {
    /** food level + saturation as an eating player starts, so finishing can measure the actual gain. */
    private static final Map<UUID, float[]> PRE_EAT_FOOD = EffectState.sessionConcurrent("eating/pre_eat_food");

    private EatingCurses() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean allergic = EffectManager.isActive(player, Curses.ALLERGIC);
        boolean gluttony = EffectManager.isActive(player, Curses.GLUTTONY);
        boolean munchies = EffectManager.isActive(player, Curses.MUNCHIES);
        // snapshot before the food lands (vanilla clamps the gain when you're nearly full)
        if (allergic || gluttony || munchies) {
            PRE_EAT_FOOD.put(player.getUUID(),
                    new float[]{player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()});
        }
        // gluttony: wolf food down faster — the curse's one mercy, given how much of it you have to eat
        if (gluttony) {
            event.setDuration(Math.max(1, Math.round(event.getDuration()
                    * (1.0F - Config.GLUTTONY_EAT_SPEED_PERCENT.get() / 100.0F))));
        }
        // munchies: also eat faster, on top of gluttony's
        if (munchies) {
            event.setDuration(Math.max(1, Math.round(event.getDuration()
                    * (1.0F - Config.MUNCHIES_EAT_SPEED_PERCENT.get() / 100.0F))));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        float[] beforeEating = PRE_EAT_FOOD.remove(player.getUUID());
        FoodProperties food = stack.get(DataComponents.FOOD);
        boolean allergen = EffectManager.isActive(player, Curses.ALLERGIC)
                && CurseAllergic.isForbidden(CurseAllergic.dietOf(player), stack);

        // gluttony: the two rows are one bar, so nutrition vanilla couldn't fit spills up into the extra row. then
        // saturation is docked, so meals never stick. (an allergen gives nothing to spill.)
        if (food != null && beforeEating != null && !allergen && EffectManager.isActive(player, Curses.GLUTTONY)) {
            int gained = player.getFoodData().getFoodLevel() - (int) beforeEating[0];
            CurseGluttony.feed(player, food.nutrition() - gained);
            float satGained = player.getFoodData().getSaturationLevel() - beforeEating[1];
            if (satGained > 0.0F) {
                float keep = satGained * (1.0F - Config.GLUTTONY_SATURATION_PENALTY_PERCENT.get() / 100.0F);
                player.getFoodData().setSaturation(Math.max(0.0F, beforeEating[1] + keep));
            }
        }

        // munchies: dock most of the saturation the meal gave (measured from the real gain; compounds after gluttony)
        if (food != null && beforeEating != null && EffectManager.isActive(player, Curses.MUNCHIES)) {
            float satGained = player.getFoodData().getSaturationLevel() - beforeEating[1];
            if (satGained > 0.0F) {
                float keep = satGained * (Config.MUNCHIES_SATURATION_PERCENT.get() / 100.0F);
                player.getFoodData().setSaturation(Math.max(0.0F, beforeEating[1] + keep));
            }
            Curses.MUNCHIES.value().markDiscoveredByVictim(player); // discovered on the first meal
        }

        // allergic: vanilla has applied the food/potion, so claw the benefit back and react
        if (allergen) {
            CurseAllergic.onAteAllergen(player, stack, beforeEating);
        }
    }
}
