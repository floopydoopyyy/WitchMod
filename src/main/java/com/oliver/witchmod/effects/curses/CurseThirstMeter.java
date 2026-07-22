package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.data.WitchModMobEffects;

/**
 * A second bar you have to keep topped up (master-spec Thirst Meter). It works like hunger but is weighted
 * almost entirely toward ACTIVITY: standing about barely moves it, while sprinting, swimming, mining and
 * fighting drain it fast. The whole point of the curse is to stop a target being very busy.
 *
 * <p><b>Drain uses vanilla's exhaustion model rather than a flat timer.</b> Actions add "thirst exhaustion",
 * and every {@code EXHAUSTION_PER_POINT} of it costs one droplet — the same shape as hunger, so it behaves
 * the way players already expect a bar like this to behave.
 *
 * <p><b>Dehydration</b> is Hunger-but-for-thirst: it multiplies the drain. It arrives from being somewhere
 * hot (biome base temperature at or above {@code HOT_BIOME_TEMPERATURE} — desert, badlands, savanna, the
 * Nether) or from sustained activity on an already-low bar. It's applied with {@code visible=false} so it
 * shows no ambient particles (Oliver's call — it comes and goes far too often for that), but keeps its HUD
 * icon.
 *
 * <p><b>An empty bar is lethal on every difficulty.</b> Vanilla starvation deliberately stops short of
 * killing you on Peaceful and Easy; this uses the mod's own dehydration damage type, applied directly with
 * no difficulty check and bypassing armour.
 *
 * <p>Refills are handled in {@code CurseEventHandler}, which also has to work around vanilla refusing to let
 * you eat at full hunger — see {@code allowThirstyEating} there.
 */
public final class CurseThirstMeter extends Effect {
    /** victim -> accumulated thirst exhaustion, the same idea as vanilla's hunger exhaustion. */
    private static final Map<UUID, Float> EXHAUSTION = new HashMap<>();
    /**
     * victim -> hidden thirst saturation. Spent BEFORE the visible bar, exactly like hunger saturation, and
     * it exists for exactly the same reason: without it a bar you have just filled starts ticking down on the
     * very next point of exhaustion, which feels like the drink did nothing.
     */
    private static final Map<UUID, Float> SATURATION = new HashMap<>();
    /** victim -> points burned while the bar was already low, which is what brings on Dehydration. */
    private static final Map<UUID, Integer> STRAIN = new HashMap<>();

    public CurseThirstMeter() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.POTION);
    }

    /** The bar appearing is impossible to miss (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.THIRST, Config.THIRST_MAX.get());
        EXHAUSTION.put(target.getUUID(), 0.0F);
        SATURATION.put(target.getUUID(), 0.0F);
        STRAIN.put(target.getUUID(), 0);
        markDiscoveredByVictim(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.THIRST, -1); // -1 hides the bar
        EXHAUSTION.remove(target.getUUID());
        SATURATION.remove(target.getUUID());
        STRAIN.remove(target.getUUID());
        target.removeEffect(WitchModMobEffects.DEHYDRATION);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int thirst = target.getData(WitchModAttachments.THIRST);
        if (thirst < 0) {
            target.setData(WitchModAttachments.THIRST, Config.THIRST_MAX.get()); // self-heal after a relog
            return;
        }
        // Frozen in creative and spectator, matching the HUD — which already hides itself on the same
        // condition vanilla uses for health, hunger and air. Draining a bar nobody can see (or refill)
        // would just mean walking back into survival already parched.
        if (target.isCreative() || target.isSpectator()) {
            return;
        }

        applyHeatDehydration(target);
        thirst = drain(target, thirst);
        tickFullBonus(target, thirst);

        if (thirst <= 0 && target.tickCount % Config.THIRST_EMPTY_DAMAGE_INTERVAL.get() == 0) {
            // No difficulty check on purpose — unlike starving, dying of thirst happens on Peaceful too.
            target.hurt(WitchModDamageTypes.dehydration(target.level()),
                    Config.THIRST_EMPTY_DAMAGE.get().floatValue());
        }
    }

    /** Accumulates thirst exhaustion from what the player is actually doing, and spends it on the bar. */
    private static int drain(ServerPlayer target, int thirst) {
        double amount = Config.THIRST_IDLE_DRAIN.get();
        if (target.isSprinting() || target.isSwimming()) {
            amount += Config.THIRST_SPRINT_DRAIN.get();
        } else if (target.walkDist - target.walkDistO > 0.01F) {
            amount += Config.THIRST_WALK_DRAIN.get();
        }
        if (target.hasEffect(WitchModMobEffects.DEHYDRATION)) {
            amount *= Config.THIRST_DEHYDRATION_MULT.get();
        }
        return spend(target, (float) amount, thirst);
    }

    /**
     * Shared by the tick drain and the one-off action costs. Mirrors {@code FoodData.tick}: each unit of
     * exhaustion is taken off SATURATION first, and only eats the visible bar once saturation is gone.
     */
    private static int spend(ServerPlayer target, float amount, int thirst) {
        UUID id = target.getUUID();
        float total = EXHAUSTION.getOrDefault(id, 0.0F) + amount;
        float perPoint = Config.THIRST_EXHAUSTION_PER_POINT.get().floatValue();
        float saturation = SATURATION.getOrDefault(id, 0.0F);

        while (total >= perPoint) {
            total -= perPoint;
            if (saturation > 0.0F) {
                saturation = Math.max(0.0F, saturation - 1.0F); // grace period absorbs it
                continue;
            }
            if (thirst <= 0) {
                break;
            }
            thirst--;
            // Burning through the bar while it's ALREADY low is what counts as overexertion.
            if (thirst <= Config.THIRST_MAX.get() / 2) {
                int strain = STRAIN.merge(id, 1, Integer::sum);
                if (strain >= Config.THIRST_ACTIVITY_DEHYDRATION_THRESHOLD.get()) {
                    STRAIN.put(id, 0);
                    applyDehydration(target);
                }
            }
        }
        EXHAUSTION.put(id, total);
        SATURATION.put(id, saturation);
        target.setData(WitchModAttachments.THIRST, thirst);
        return thirst;
    }

    /** Vanilla regen already rewards a full stomach; this stacks a little more on for a full bar too. */
    private static void tickFullBonus(ServerPlayer target, int thirst) {
        if (thirst < Config.THIRST_MAX.get()
                || target.getFoodData().getFoodLevel() < 20
                || target.getHealth() >= target.getMaxHealth()) {
            return;
        }
        if (target.tickCount % Config.THIRST_FULL_BONUS_HEAL_INTERVAL.get() == 0) {
            target.heal(Config.THIRST_FULL_BONUS_HEAL.get().floatValue());
        }
    }

    /** Mining, fighting — the strenuous one-off actions. Called from {@code CurseEventHandler}. */
    public static void onStrenuousAction(ServerPlayer target) {
        int thirst = target.getData(WitchModAttachments.THIRST);
        if (thirst >= 0) {
            spend(target, Config.THIRST_ACTION_DRAIN.get().floatValue(), thirst);
        }
    }

    private static void applyHeatDehydration(ServerPlayer target) {
        float temperature = target.level().getBiome(target.blockPosition()).value().getBaseTemperature();
        if (temperature >= Config.THIRST_HOT_BIOME_TEMPERATURE.get()) {
            applyDehydration(target);
        }
    }

    public static void applyDehydration(ServerPlayer target) {
        // visible=false kills the ambient particles; showIcon=true keeps it readable in the effect bar.
        target.addEffect(new MobEffectInstance(WitchModMobEffects.DEHYDRATION,
                Config.THIRST_DEHYDRATION_DURATION.get(), 0, false, false, true));
    }

    // --- Refills ---------------------------------------------------------------------------------------

    /** Anything drunk or eaten. Called from the use-Finish hook in {@code CurseEventHandler}. */
    public static void onConsumed(ServerPlayer target, ItemStack stack) {
        int thirst = target.getData(WitchModAttachments.THIRST);
        if (thirst < 0) {
            return;
        }
        int restore = restoreFor(stack);
        if (restore > 0) {
            restore(target, restore);
        }
    }

    /**
     * How hydrating something is. Food categories deliberately reuse the same in-game split as the Allergic
     * curse — {@code minecraft:meat} tag, effect-granting = magic, the remainder = natural — so modded food
     * is classified automatically rather than from a list that rots.
     */
    private static int restoreFor(ItemStack stack) {
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            boolean plainWater = potion.is(Potions.WATER);
            return plainWater ? Config.THIRST_RESTORE_WATER_BOTTLE.get() : Config.THIRST_RESTORE_POTION.get();
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return 0;
        }
        boolean meat = stack.is(ItemTags.MEAT);
        boolean magic = !food.effects().isEmpty();
        if (meat) {
            // Raw meat still carries moisture; cooked is dry, and falls through to the "other" bucket.
            return isCooked(stack) ? Config.THIRST_RESTORE_OTHER_FOOD.get()
                    : Config.THIRST_RESTORE_RAW_FOOD.get();
        }
        if (!magic) {
            return Config.THIRST_RESTORE_NATURAL_FOOD.get(); // the water-rich plant stuff
        }
        return Config.THIRST_RESTORE_OTHER_FOOD.get();
    }

    /** No vanilla "is cooked" flag exists, so this reads the registry name — modded cooked food works too. */
    private static boolean isCooked(ItemStack stack) {
        String path = stack.getItemHolder().unwrapKey()
                .map(key -> key.location().getPath()).orElse("");
        return path.startsWith("cooked_") || path.contains("_cooked") || path.equals("dried_kelp");
    }

    /**
     * Drinking untreated water — from a pond, a cauldron, or (regrettably) a sponge. Generous, but you may
     * well come to regret it.
     *
     * @return true if a drink actually happened, so the caller can consume the water source
     */
    public static boolean drinkFromWorld(ServerPlayer target, InteractionHand hand) {
        int thirst = target.getData(WitchModAttachments.THIRST);
        if (thirst < 0 || thirst >= Config.THIRST_MAX.get()) {
            return false;
        }
        restore(target, Config.THIRST_RESTORE_RAW_WATER.get());
        target.swing(hand, true); // true = show the swing to everyone, not just the drinker
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6F, 1.0F);

        if (target.getRandom().nextInt(100) < Config.THIRST_RAW_WATER_RISK.get()) {
            if (target.getRandom().nextBoolean()) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0, false, true, true));
            } else {
                applyDehydration(target); // untreated water that makes things worse — the crueller outcome
            }
        }
        return true;
    }

    /** Restores points AND the hidden saturation that stops the bar draining the instant you stop drinking. */
    private static void restore(ServerPlayer target, int points) {
        int max = Config.THIRST_MAX.get();
        int updated = Math.min(max, target.getData(WitchModAttachments.THIRST) + points);
        target.setData(WitchModAttachments.THIRST, updated);

        UUID id = target.getUUID();
        float gained = points * Config.THIRST_SATURATION_PER_POINT.get().floatValue();
        // Capped at the bar itself, exactly as vanilla caps hunger saturation at the food level.
        float capped = Math.min(updated, SATURATION.getOrDefault(id, 0.0F) + gained);
        SATURATION.put(id, capped);
    }

    /** True when the bar is low enough that vanilla's full-hunger eating block should be worked around. */
    public static boolean wantsToDrink(ServerPlayer target) {
        int thirst = target.getData(WitchModAttachments.THIRST);
        return thirst >= 0 && thirst < Config.THIRST_MAX.get();
    }
}
