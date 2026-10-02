package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.effects.curses.CurseAllergic;
import com.oliver.witchmod.effects.curses.CurseGluttony;
import com.oliver.witchmod.synergy.Synergies;

/**
 * snack thief (pickpocket + munchies/gluttony): a separate roll from the normal lift that grabs one of a nearby
 * player's foods and eats it on the spot. the emptier your hunger, the higher the chance and the shorter the gap
 * between attempts, so a starving thief strips someone's food fast. allergic thieves always go for the food
 * they're allergic to.
 */
public final class PickpocketSnack {
    private PickpocketSnack() {}

    private static final Map<UUID, Long> NEXT_ATTEMPT = new ConcurrentHashMap<>();

    public static boolean activeFor(ServerPlayer thief) {
        return Synergies.SNACK_THIEF.activeFor(thief) || Synergies.SNACK_THIEF_GLUTTON.activeFor(thief);
    }

    /** called every pickpocket tick; gates itself on its own hunger-scaled interval. */
    public static void tick(ServerPlayer thief) {
        if (!activeFor(thief)) {
            return;
        }
        long now = thief.serverLevel().getGameTime();
        Long next = NEXT_ATTEMPT.get(thief.getUUID());
        if (next != null && now < next) {
            return;
        }
        float hunger = hungerFraction(thief);
        int interval = Math.round(Mth.lerp(hunger, Config.PICKPOCKET_SNACK_INTERVAL_STARVING.get(),
                Config.PICKPOCKET_SNACK_INTERVAL_FULL.get()));
        NEXT_ATTEMPT.put(thief.getUUID(), now + Math.max(1, interval));

        double chance = Mth.lerp(hunger, Config.PICKPOCKET_SNACK_CHANCE_STARVING.get(),
                Config.PICKPOCKET_SNACK_CHANCE_FULL.get());
        for (ServerPlayer victim : BlessingPickpocket.marksNear(thief)) {
            if (thief.getRandom().nextDouble() < chance && steal(thief, victim)) {
                return; // one mouthful per attempt
            }
        }
    }

    public static void clear(ServerPlayer thief) {
        NEXT_ATTEMPT.remove(thief.getUUID());
    }

    /** grabs and eats one food from the victim. false if they had nothing the thief can eat right now. */
    public static boolean steal(ServerPlayer thief, ServerPlayer victim) {
        int slot = pickFood(thief, victim, thief.getRandom());
        if (slot < 0) {
            return false;
        }
        ItemStack meal = victim.getInventory().getItem(slot).copyWithCount(1);
        // run the real use-start hook so the eat hooks (gluttony/munchies/allergic snapshots) see this meal.
        if (EventHooks.onItemUseStart(thief, meal, InteractionHand.MAIN_HAND, meal.getUseDuration(thief)) < 0) {
            return false;
        }
        victim.getInventory().removeItem(slot, 1);
        ServerLevel level = thief.serverLevel();
        thief.swing(InteractionHand.MAIN_HAND, true);
        eatingEffects(thief, level, meal);

        ItemStack leftover = EventHooks.onItemUseFinish(thief, meal.copy(), 0, meal.finishUsingItem(level, thief));
        if (!leftover.isEmpty() && !thief.getInventory().add(leftover)) {
            thief.drop(leftover, false); // bowl/bottle with nowhere to go
        }
        Blessings.PICKPOCKET.get().markDiscoveredByVictim(thief);
        return true;
    }

    /** a random edible food slot from the victim's 36 main slots, allergens first if the thief is allergic. */
    private static int pickFood(ServerPlayer thief, ServerPlayer victim, RandomSource random) {
        NonNullList<ItemStack> items = victim.getInventory().items;
        CurseAllergic.Diet diet = EffectManager.isActive(thief, Curses.ALLERGIC) ? CurseAllergic.dietOf(thief) : null;
        List<Integer> edible = new ArrayList<>();
        List<Integer> allergens = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null || !thief.canEat(food.canAlwaysEat())) {
                continue;
            }
            edible.add(i);
            if (diet != null && CurseAllergic.isForbidden(diet, stack)) {
                allergens.add(i);
            }
        }
        List<Integer> pool = allergens.isEmpty() ? edible : allergens;
        return pool.isEmpty() ? -1 : pool.get(random.nextInt(pool.size()));
    }

    /** 0 = starving, 1 = full. gluttony counts its extra row, so the full bar is 40. */
    private static float hungerFraction(ServerPlayer thief) {
        int food = thief.getFoodData().getFoodLevel();
        int extra = thief.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra >= 0) {
            return Mth.clamp((food + extra) / (float) (20 + CurseGluttony.EXTRA_MAX), 0.0F, 1.0F);
        }
        return Mth.clamp(food / 20.0F, 0.0F, 1.0F);
    }

    /** vanilla's end-of-eat crumbs + chomp (LivingEntity.triggerItemUseEffects is protected, so mirrored here). */
    private static void eatingEffects(ServerPlayer thief, ServerLevel level, ItemStack meal) {
        RandomSource random = thief.getRandom();
        if (meal.getUseAnimation() == UseAnim.DRINK) {
            level.playSound(null, thief, meal.getDrinkingSound(), SoundSource.PLAYERS, 0.5F,
                    random.nextFloat() * 0.1F + 0.9F);
            return;
        }
        ItemParticleOption crumbs = new ItemParticleOption(ParticleTypes.ITEM, meal);
        for (int i = 0; i < 16; i++) {
            Vec3 velocity = new Vec3((random.nextFloat() - 0.5) * 0.1, random.nextDouble() * 0.1 + 0.1, 0.0)
                    .xRot(-thief.getXRot() * Mth.DEG_TO_RAD).yRot(-thief.getYRot() * Mth.DEG_TO_RAD);
            Vec3 pos = new Vec3((random.nextFloat() - 0.5) * 0.3, -random.nextFloat() * 0.6 - 0.3, 0.6)
                    .xRot(-thief.getXRot() * Mth.DEG_TO_RAD).yRot(-thief.getYRot() * Mth.DEG_TO_RAD)
                    .add(thief.getX(), thief.getEyeY(), thief.getZ());
            // count 0 = the offsets are used as a direction/velocity, like the client-side spawn.
            level.sendParticles(crumbs, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y + 0.05, velocity.z, 1.0);
        }
        level.playSound(null, thief, thief.getEatingSound(meal), SoundSource.PLAYERS,
                0.5F + 0.5F * random.nextInt(2), (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
    }
}
