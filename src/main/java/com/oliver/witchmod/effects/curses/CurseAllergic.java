package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModMobEffects;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.synergy.Synergies;

/**
 * an allergy that turns places into no-go zones. the diet comes from the time of day it was cast (so casters can
 * aim for one) and the caster is told which. being near your allergen — certain blocks, mobs, players, or the
 * forbidden food itself on the ground or in someone's pockets — builds an {@link AllergicReaction} of up to three
 * tiers; eating it goes straight to the worst. the only counter is keeping away.
 *
 * <ul>
 *   <li><b>vegetarian</b> — meat.</li>
 *   <li><b>carnivore</b> — plant food.</li>
 *   <li><b>clean eater</b> — magic food and potions.</li>
 * </ul>
 * food categories come from item data (meat tag / food effects), so modded foods work too.
 */
public final class CurseAllergic extends Effect {
    /** ordinals are persisted in {@link WitchModAttachments#ALLERGIC_DIET} — don't reorder. */
    public enum Diet {
        VEGETARIAN, CARNIVORE, CLEAN_EATER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** built-up mob exposure in ticks (fractional, vegetarians build faster). */
    private static final Map<UUID, Float> EXPOSURE = new ConcurrentHashMap<>();
    /** target → {caster most/least bits, game tick to tell them}. */
    private static final Map<UUID, long[]> PENDING_CAST_MESSAGE = new ConcurrentHashMap<>();

    public CurseAllergic() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SWEET_BERRIES);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        Diet diet = dietForTime(target.server.overworld().getDayTime());
        target.setData(WitchModAttachments.ALLERGIC_DIET, diet.ordinal());
        if (caster != null) {
            UUID id = caster.getUUID();
            PENDING_CAST_MESSAGE.put(target.getUUID(), new long[]{id.getMostSignificantBits(),
                    id.getLeastSignificantBits(), target.level().getGameTime() + Config.ALLERGIC_CAST_MESSAGE_DELAY.get()});
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.removeEffect(WitchModMobEffects.ALLERGIC_REACTION);
        target.setData(WitchModAttachments.ALLERGY_TIER, 0);
        target.setData(WitchModAttachments.ALLERGIC_DIET, -1);
        EXPOSURE.remove(target.getUUID());
        PENDING_CAST_MESSAGE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        syncTier(target);
        tellCaster(target);
        int interval = Config.ALLERGIC_CHECK_INTERVAL.get();
        if (!EffectUtil.every(ticksRemaining, interval)) {
            return;
        }
        int tier = AllergicReaction.tierOf(target);
        if (tier > 0) {
            // other effects can move max health about, so keep the heart loss exact.
            AllergicReaction.applyModifiers(target.getAttributes(), tier);
            reactionSynergies(target, tier, interval);
        }
        checkExposure(target, interval);
    }

    @Override
    public Optional<Component> scryingDetail(ServerPlayer target) {
        return Optional.of(Component.translatable("witchmod.scry.allergic." + dietOf(target).key()));
    }

    /** discovered on the first reaction, not on landing. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    /** arg = "[tier] [seconds]" (defaults 1 and 20); "clear" ends any reaction. */
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        String[] parts = arg == null ? new String[0] : arg.trim().split("\\s+");
        if (parts.length > 0 && parts[0].equalsIgnoreCase("clear")) {
            target.removeEffect(WitchModMobEffects.ALLERGIC_REACTION);
            EXPOSURE.remove(target.getUUID());
            syncTier(target);
            return "allergic reaction cleared";
        }
        int tier = 1;
        int seconds = 20;
        try {
            if (parts.length > 0) {
                tier = Integer.parseInt(parts[0]);
            }
            if (parts.length > 1) {
                seconds = Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            return "usage: <tier 1-3> [seconds], or clear";
        }
        if (tier < 1 || tier > 3 || seconds < 1) {
            return "usage: <tier 1-3> [seconds], or clear";
        }
        // replace outright so a lower tier or shorter time isn't swallowed by an active reaction.
        target.removeEffect(WitchModMobEffects.ALLERGIC_REACTION);
        react(target, tier, seconds * 20);
        return "tier " + tier + " reaction for " + seconds + "s (" + dietOf(target).key() + ")";
    }

    @Override
    public List<String> debugArgs() {
        return List.of("1", "2", "3", "clear");
    }

    // --- diet ------------------------------------------------------------------------------------------

    /** the diet whose start time is the latest one at or before this time of day, wrapping round midnight. */
    public static Diet dietForTime(long dayTime) {
        int time = (int) Math.floorMod(dayTime, 24000L);
        int[] starts = {Config.ALLERGIC_VEGETARIAN_START.get(), Config.ALLERGIC_CARNIVORE_START.get(),
                Config.ALLERGIC_CLEAN_EATER_START.get()};
        Diet[] diets = {Diet.VEGETARIAN, Diet.CARNIVORE, Diet.CLEAN_EATER};
        int best = -1;
        int latest = -1;
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] <= time && (best < 0 || starts[i] > starts[best])) {
                best = i;
            }
            if (latest < 0 || starts[i] > starts[latest]) {
                latest = i;
            }
        }
        return diets[best >= 0 ? best : latest];
    }

    public static Diet dietOf(ServerPlayer player) {
        int index = player.getData(WitchModAttachments.ALLERGIC_DIET);
        if (index < 0 || index >= Diet.values().length) {
            // unset (old save) — take it from the current time instead.
            Diet diet = dietForTime(player.server.overworld().getDayTime());
            player.setData(WitchModAttachments.ALLERGIC_DIET, diet.ordinal());
            return diet;
        }
        return Diet.values()[index];
    }

    /** whether {@code stack} is off-limits for {@code diet}. */
    public static boolean isForbidden(Diet diet, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        boolean meat = stack.is(ItemTags.MEAT);
        boolean magicFood = food != null && !food.effects().isEmpty();
        boolean magicPotion = potion != null && potion.hasEffects();
        return switch (diet) {
            case VEGETARIAN -> meat;
            case CLEAN_EATER -> magicFood || magicPotion;
            case CARNIVORE -> food != null && !meat && !magicFood;
        };
    }

    // --- eating ----------------------------------------------------------------------------------------

    /** called after vanilla fed the player an allergen: take the benefit back and go straight to a bad reaction. */
    public static void onAteAllergen(ServerPlayer player, ItemStack stack, float @Nullable [] beforeEating) {
        if (beforeEating != null) {
            reduceGain(player, (int) beforeEating[0], beforeEating[1]);
        }
        stripBeneficialEffects(player, stack);
        react(player, Config.ALLERGIC_EAT_TIER.get(), Config.ALLERGIC_EAT_SECONDS.get() * 20);
    }

    /** keeps only allergicNutritionPercent of the real gain, measured from the pre-eat snapshot. */
    private static void reduceGain(ServerPlayer player, int foodBefore, float saturationBefore) {
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

    /** removes the beneficial effects the food or potion just granted. */
    private static void stripBeneficialEffects(ServerPlayer player, ItemStack stack) {
        List<Holder<MobEffect>> granted = new ArrayList<>();
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potion.getAllEffects().forEach(instance -> granted.add(instance.getEffect()));
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            food.effects().forEach(possible -> granted.add(possible.effect().getEffect()));
        }
        for (Holder<MobEffect> effect : granted) {
            if (effect.value().isBeneficial()) {
                player.removeEffect(effect);
            }
        }
    }

    // --- reaction --------------------------------------------------------------------------------------

    /**
     * adds (or tops up) a reaction. vanilla's effect merging does the rest: a higher tier replaces a lower one,
     * the same tier keeps the longer timer, and a lower tier never downgrades an active higher one.
     */
    public static void react(ServerPlayer player, int tier, int durationTicks) {
        boolean fresh = AllergicReaction.tierOf(player) == 0;
        player.addEffect(new MobEffectInstance(WitchModMobEffects.ALLERGIC_REACTION, durationTicks,
                Math.max(0, Math.min(2, tier - 1)), false, false, true));
        syncTier(player);
        if (fresh) {
            Curses.ALLERGIC.get().markDiscoveredByVictim(player);
        }
    }

    /** mirrors the reaction tier into the synced attachment (skin tint) and clamps health to the new max. */
    private static void syncTier(ServerPlayer player) {
        int tier = AllergicReaction.tierOf(player);
        if (player.getData(WitchModAttachments.ALLERGY_TIER) != tier) {
            player.setData(WitchModAttachments.ALLERGY_TIER, tier);
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void tellCaster(ServerPlayer target) {
        long[] pending = PENDING_CAST_MESSAGE.get(target.getUUID());
        if (pending == null || target.level().getGameTime() < pending[2]) {
            return;
        }
        PENDING_CAST_MESSAGE.remove(target.getUUID());
        ServerPlayer caster = target.server.getPlayerList().getPlayer(new UUID(pending[0], pending[1]));
        if (caster != null) {
            caster.displayClientMessage(Component.translatable("witchmod.allergic.cast", target.getDisplayName(),
                    Component.translatable("witchmod.allergic.diet." + dietOf(target).key())), true);
        }
    }

    /**
     * mob-type hazards build exposure (tier 1 → 2 → 3 the longer you stay), and drain it once you're clear.
     * allergen blocks only ever give a short tier 1. every hit re-tops the timer.
     */
    private static void checkExposure(ServerPlayer target, int interval) {
        Diet diet = dietOf(target);
        UUID id = target.getUUID();
        float exposure = EXPOSURE.getOrDefault(id, 0.0F);
        if (mobHazardNear(target, diet)) {
            float rate = diet == Diet.VEGETARIAN ? Config.ALLERGIC_VEGETARIAN_EXPOSURE_MULT.get().floatValue() : 1.0F;
            exposure += interval * rate;
            int tier2At = Config.ALLERGIC_MOB_TIER2_AFTER.get() * 20;
            int tier3At = tier2At + Config.ALLERGIC_MOB_TIER3_AFTER.get() * 20;
            if (exposure >= tier3At) {
                react(target, 3, Config.ALLERGIC_MOB_TIER3_SECONDS.get() * 20);
            } else if (exposure >= tier2At) {
                react(target, 2, Config.ALLERGIC_MOB_TIER2_SECONDS.get() * 20);
            } else {
                react(target, 1, Config.ALLERGIC_MOB_TIER1_SECONDS.get() * 20);
            }
        } else {
            exposure = Math.max(0.0F, exposure - interval * Config.ALLERGIC_EXPOSURE_DECAY.get().floatValue());
        }
        if (exposure > 0.0F) {
            EXPOSURE.put(id, exposure);
        } else {
            EXPOSURE.remove(id);
        }
        if (blockHazardNear(target, diet)) {
            react(target, 1, Config.ALLERGIC_BLOCK_SECONDS.get() * 20);
        }
    }

    private static boolean mobHazardNear(ServerPlayer target, Diet diet) {
        boolean items = Config.ALLERGIC_ITEMS_ARE_HAZARDS.get();
        if (items && Config.ALLERGIC_OWN_INVENTORY.get() && carriesAllergen(target, diet)) {
            return true;
        }
        double radius = Config.ALLERGIC_MOB_RADIUS.get();
        Predicate<EntityType<?>> hazardType = mobMatcher(diet);
        for (Entity entity : target.level().getEntities(target, target.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e.distanceToSqr(target) <= radius * radius)) {
            if (entity instanceof ItemEntity item) {
                if (items && isForbidden(diet, item.getItem())) {
                    return true;
                }
            } else if (entity instanceof ServerPlayer other) {
                if (other.isSpectator()) {
                    continue;
                }
                if (items && carriesAllergen(other, diet)) {
                    return true;
                }
                if (diet == Diet.CARNIVORE && Config.ALLERGIC_CARNIVORE_FEARS_VEGETARIANS.get()
                        && EffectManager.isActive(other, Curses.ALLERGIC) && dietOf(other) == Diet.VEGETARIAN) {
                    return true;
                }
                if (diet == Diet.CLEAN_EATER && Config.ALLERGIC_CLEAN_EATER_FEARS_BUFFS.get() && hasPotionBuff(other)) {
                    return true;
                }
            } else if (hazardType.test(entity.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean carriesAllergen(ServerPlayer player, Diet diet) {
        for (ItemStack stack : player.getInventory().items) {
            if (isForbidden(diet, stack)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isForbidden(diet, stack)) {
                return true;
            }
        }
        return false;
    }

    /** a positive effect from anywhere but this mod (the cursed/blessed/etc. markers don't count). */
    private static boolean hasPotionBuff(ServerPlayer player) {
        for (MobEffectInstance instance : player.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            if (effect.value().isBeneficial() && effect.unwrapKey()
                    .map(key -> !key.location().getNamespace().equals(WitchMod.MODID)).orElse(true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blockHazardNear(ServerPlayer target, Diet diet) {
        Predicate<BlockState> hazard = blockMatcher(diet);
        int r = Config.ALLERGIC_BLOCK_RADIUS.get();
        ServerLevel level = target.serverLevel();
        BlockPos feet = target.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-r, -r, -r), feet.offset(r, r + 1, r))) {
            if (hazard.test(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    // --- config list matching (ids or #tags), cached until the list changes ------------------------------

    private static final Map<Diet, Object[]> BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Diet, Object[]> MOB_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static Predicate<BlockState> blockMatcher(Diet diet) {
        List<? extends String> raw = switch (diet) {
            case VEGETARIAN -> Config.ALLERGIC_VEGETARIAN_BLOCKS.get();
            case CARNIVORE -> Config.ALLERGIC_CARNIVORE_BLOCKS.get();
            case CLEAN_EATER -> Config.ALLERGIC_CLEAN_EATER_BLOCKS.get();
        };
        Object[] cached = BLOCK_CACHE.get(diet);
        if (cached != null && cached[0] == raw) {
            return (Predicate<BlockState>) cached[1];
        }
        List<TagKey<Block>> tags = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        for (String entry : raw) {
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null) {
                    tags.add(TagKey.create(Registries.BLOCK, tag));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id != null) {
                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
                }
            }
        }
        Predicate<BlockState> matcher = state -> {
            if (state.isAir()) {
                return false;
            }
            for (Block block : blocks) {
                if (state.is(block)) {
                    return true;
                }
            }
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        };
        BLOCK_CACHE.put(diet, new Object[]{raw, matcher});
        return matcher;
    }

    @SuppressWarnings("unchecked")
    private static Predicate<EntityType<?>> mobMatcher(Diet diet) {
        List<? extends String> raw = switch (diet) {
            case VEGETARIAN -> Config.ALLERGIC_VEGETARIAN_MOBS.get();
            case CARNIVORE -> Config.ALLERGIC_CARNIVORE_MOBS.get();
            case CLEAN_EATER -> Config.ALLERGIC_CLEAN_EATER_MOBS.get();
        };
        Object[] cached = MOB_CACHE.get(diet);
        if (cached != null && cached[0] == raw) {
            return (Predicate<EntityType<?>>) cached[1];
        }
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();
        List<EntityType<?>> types = new ArrayList<>();
        for (String entry : raw) {
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null) {
                    tags.add(TagKey.create(Registries.ENTITY_TYPE, tag));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(types::add);
                }
            }
        }
        Predicate<EntityType<?>> matcher = type -> types.contains(type) || tags.stream().anyMatch(type::is);
        MOB_CACHE.put(diet, new Object[]{raw, matcher});
        return matcher;
    }

    // --- synergies -------------------------------------------------------------------------------------

    /** allergic + gassy / hiccups: the reaction shakes farts and hiccups loose, more often the worse it gets. */
    private static void reactionSynergies(ServerPlayer target, int tier, int interval) {
        RandomSource rng = target.getRandom();
        if (Synergies.ALLERGIC_GAS.activeFor(target)) {
            int perSecond = switch (tier) {
                case 1 -> Config.ALLERGIC_GASSY_TIER1_CHANCE.get();
                case 2 -> Config.ALLERGIC_GASSY_TIER2_CHANCE.get();
                default -> Config.ALLERGIC_GASSY_TIER3_CHANCE.get();
            };
            if (rng.nextDouble() * 100.0 < perSecond * interval / 20.0) {
                boolean big = tier >= 3 && rng.nextInt(100) < Config.ALLERGIC_GASSY_BIG_CHANCE.get();
                CurseGassy.externalFart(target, big);
            }
        }
        if (Synergies.ALLERGIC_HICCUPS.activeFor(target)) {
            int perSecond = switch (tier) {
                case 1 -> Config.ALLERGIC_HICCUPS_TIER1_CHANCE.get();
                case 2 -> Config.ALLERGIC_HICCUPS_TIER2_CHANCE.get();
                default -> Config.ALLERGIC_HICCUPS_TIER3_CHANCE.get();
            };
            if (rng.nextDouble() * 100.0 < perSecond * interval / 20.0) {
                CurseHiccups.externalHiccup(target);
            }
        }
    }
}
