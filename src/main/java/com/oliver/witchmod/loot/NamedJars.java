package com.oliver.witchmod.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.items.JarContents;

/**
 * the registry of predetermined named jars. add a preset with a single {@link #register} line — the jar's
 * variant (cursed/blessed/mixed) is derived from its effects automatically. rarity 1-10 sets only the pick
 * weight (rarer = heavier {@link Config#JAR_RARITY_WEIGHT_BASE} falloff); the actual drop rates are config.
 */
public final class NamedJars {
    public static final int MIN_RARITY = 1;
    public static final int MAX_RARITY = 10;

    private static final List<NamedJar> ALL = new ArrayList<>();
    private static final Map<ResourceLocation, NamedJar> BY_ID = new LinkedHashMap<>();

    // the presets. keep each to at most JarContents.MAX effects. mix categories freely — the variant follows.
    // rarity is 1 (common) to 10 (rarest); it only sets the pick weight, not whether a jar drops at all.
    static {
        // blessing jars
        register("mining", 4, Blessings.EXCAVATION, Blessings.VEIN_MINER, Blessings.BUILDER);
        register("charming", 2, Blessings.SILVER_TONGUE, Blessings.TRAINER);
        register("luck", 3, Blessings.FORTUNE, Blessings.LUCK);
        register("prosperity", 8, Blessings.PEACE);
        register("speed", 1, Blessings.SPEED, Blessings.SPEED_DEMON);
        register("power", 7, Blessings.THUNDER, Blessings.HEAVY_HITTER);
        register("combat", 9, Blessings.GLADIATOR, Blessings.NINJA, Blessings.MAIN_CHARACTER);
        register("cow", 5, Blessings.COW);
        register("precision", 7, Blessings.DEXTEROUS, Blessings.HAWK_GUY);
        register("saturation", 1, Blessings.FULLNESS);
        register("sight", 1, Blessings.NIGHTOWL);
        register("deep", 6, Blessings.IRON_LUNG, Blessings.OCEANS_BLESSING);
        // curse jars
        register("dwelling", 10, Curses.THE_DWELLER);
        register("confusion", 3, Curses.DELUSIONS, Curses.ECHOES, Curses.CHANNELS);
        register("snail", 9, Curses.SNAIL);
        register("sleep", 8, Curses.NARCOLEPSY, Curses.INSOMNIAC);
        register("accidents", 2, Curses.ICE_SKATES, Curses.SLIPPERY_FEET);
        register("famine", 4, Curses.GLUTTONY, Curses.MUNCHIES);
        register("conspicuousness", 6, Curses.TRUMPET, Curses.SPOTLIGHT, Curses.FLAT_FOOTED);
        register("uselessness", 7, Curses.BUTTERFINGERS, Curses.CLUMSY, Curses.HEAVY_HANDED);
        register("explosiveness", 2, Curses.EXPLOSIVE, Curses.SUPER_EXPLOSIVE);
        register("thirst", 1, Curses.THIRST_METER);
        register("infestation", 1, Curses.PESTS);
        register("stench", 4, Curses.GASSY, Curses.UNHYGIENIC);
        register("endless_speaking", 4, Curses.YAP, Curses.OVERSHARER);
        register("nuisance", 6, Curses.MINOR_INCONVENIENCE, Curses.SCREENSAVER);
        register("weight", 6, Curses.BAD_SWIMMER, Curses.DENSE);
        register("colossus", 9, Curses.GIANT);
        register("families", 9, Curses.CUTAWAY_GAG);
        register("mayhem", 10, Curses.BEDROCK_MOMENT);
        register("relentlessness", 10, Curses.POPULARITY, Curses.PESTS, Curses.NEUTRAL_AGGRESSION);
        // more blessing jars
        register("angler", 1, Blessings.ANGLER, Blessings.COLLECTOR);
        register("detection", 2, Blessings.SPELUNKING, Blessings.SONAR);
        register("scholars", 3, Blessings.STUDIOUS, Blessings.SIXTH_SENSE);
        register("levity", 4, Blessings.LOW_GRAVITY, Blessings.TWINKLETOES);
        register("bulwark", 7, Blessings.THICK_SKINNED, Blessings.REFLECT);
        register("vanguard", 7, Blessings.TANK, Blessings.LEADER);
        register("difficult_jobs", 8, Blessings.CHAT);
        register("safety", 3, Blessings.SAFETY, Blessings.HOMEBODY);
        register("props", 4, Blessings.PROP_HUNT, Blessings.SPEED);
        register("good_luck", 3, Blessings.LUCK, Blessings.WINDFALL);
        register("assassination", 9, Blessings.UNSEEN, Blessings.NINJA, Blessings.BACKSTABBING);
        register("brutality", 9, Blessings.BRUTE, Blessings.BERSERKER, Blessings.HEAVY_HITTER);
        register("sprinting", 8, Blessings.BRUTE, Blessings.SPEED);
        register("flight", 10, Blessings.FLIGHT);
        register("immortality", 10, Blessings.IMMORTALITY);
        register("imperviousness", 10, Blessings.TWIST_OF_FATE, Blessings.THICK_SKINNED, Blessings.TANK);
        register("dueling", 8, Blessings.GLADIATOR, Blessings.REFLECT, Blessings.THUNDER);
        register("cultivation", 4, Blessings.DRIVE, Blessings.FARMERS_SPIRIT, Blessings.ANGLER);
        register("life", 10, Blessings.IMMORTALITY, Blessings.LAST_STAND, Blessings.TWIST_OF_FATE);
        register("night", 9, Blessings.SANGUINE, Blessings.UNSEEN, Blessings.DISGUISE);
        register("disguises", 1, Blessings.DISGUISE);
        register("wilds", 6, Blessings.CONFUSION, Blessings.DISGUISE, Blessings.PROP_HUNT);
        // more curse jars
        register("ugliness", 1, Curses.UGLY);
        register("windows", 2, Curses.MINOR_INCONVENIENCE);
        register("bad_luck", 2, Curses.COMIC_RELIEF);
        register("solicitation", 3, Curses.SOLICITOR);
        register("entertainment", 3, Curses.PACING);
        register("bodily_control", 3, Curses.GASSY, Curses.HICCUPS);
        register("joysticks", 7, Curses.STICK_DRIFT, Curses.WONKY);
        register("narration", 7, Curses.NARRATOR);
        register("graphics", 8, Curses.SCREENSAVER);
        register("cooperation", 8, Curses.SPLITSCREEN);
        register("handling", 5, Curses.BUTTERFINGERS, Curses.HEAVY_HANDED, Curses.STICKY);
        register("movement", 9, Curses.VERTIGO, Curses.WONKY, Curses.MOONWALKER);
        register("haunt", 10, Curses.CARELESSNESS, Curses.SOCIAL_OUTCAST, Curses.THE_DWELLER);
        register("fragility", 4, Curses.CARELESSNESS, Curses.GLASS_CANNON);
        register("endless_annoyance", 10, Curses.NARRATOR, Curses.INSOMNIAC, Curses.TRUMPET);
        // mixed jars
        register("chaos", 4, Curses.BODY_SWAPPING, Blessings.CONFUSION);
        register("nature", 7, Blessings.DRIVE, Curses.FARMHAND);
        register("bounciness", 8, Curses.BOUNCY, Blessings.LOW_GRAVITY);
        register("endless_combat", 9, Curses.POPULARITY, Blessings.MAIN_CHARACTER, Blessings.HEAVY_HITTER);
        register("redirection", 5, Blessings.SOUL_BOND, Curses.GLASS_CANNON);
        register("smiting", 3, Blessings.THUNDER, Curses.COMIC_RELIEF);
        register("attraction", 5, Curses.MAGNET, Blessings.THUNDER);
        register("concealment", 7, Blessings.PROP_HUNT, Blessings.DISGUISE);
        register("deception", 7, Curses.UGLY, Blessings.DISGUISE);
        register("obscurity", 1, Blessings.UNSEEN, Curses.SOCIAL_OUTCAST);
        register("companionship", 8, Blessings.BODYGUARD, Blessings.GUARDIAN_ANGEL, Curses.SOLICITOR);
        register("driving", 2, Blessings.SPEED_DEMON, Curses.BACKSEAT_DRIVER);
        register("joker", 8, Blessings.TWIST_OF_FATE, Curses.COMIC_RELIEF);
        register("feasting", 2, Blessings.IRON_STOMACH, Curses.GLUTTONY);
        register("duality", 10, Blessings.THICK_SKINNED, Curses.GLASS_CANNON);
        register("trickster", 10, Blessings.UNSEEN, Blessings.CONFUSION, Curses.BODY_SWAPPING);
        register("protected", 10, Blessings.LEADER, Blessings.ARMY, Curses.POPULARITY);
        // batch 2026: themed synergy bundles
        registerVariants("thievery", 4,
                List.of(Blessings.UNSEEN, Blessings.PICKPOCKET),
                List.of(Blessings.PROP_HUNT, Blessings.PICKPOCKET),
                List.of(Blessings.DISGUISE, Blessings.PICKPOCKET));
        register("hunter", 3, Blessings.BLOODHOUND, Blessings.SONAR, Blessings.NIGHTOWL);
        register("humour", 1, Curses.YAP, Blessings.LAUGH_TRACK);
        register("juggernaut", 10, Curses.GIANT, Blessings.TANK, Blessings.THICK_SKINNED);
        register("anchor", 6, Curses.BAD_SWIMMER, Curses.DENSE);
        register("heat", 2, Blessings.HOT_STUFF, Curses.FLOOR_IS_LAVA);
        register("rage", 8, Curses.VIOLENCE, Blessings.BERSERKER, Blessings.GLADIATOR);
    }

    private NamedJars() {}

    @SafeVarargs
    private static void register(String path, int rarity, DeferredHolder<Effect, ?>... effects) {
        add(path, rarity, ids(effects), List.of());
    }

    /** a preset that rolls ONE of several interchangeable effect sets at fill time (same pick odds as a single jar). */
    @SafeVarargs
    private static void registerVariants(String path, int rarity, List<DeferredHolder<Effect, ?>>... sets) {
        List<List<ResourceLocation>> variants = new ArrayList<>();
        for (List<DeferredHolder<Effect, ?>> set : sets) {
            variants.add(ids(set.toArray(DeferredHolder[]::new)));
        }
        add(path, rarity, variants.get(0), List.copyOf(variants)); // first set is the representative for the variant
    }

    private static List<ResourceLocation> ids(DeferredHolder<Effect, ?>[] effects) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (DeferredHolder<Effect, ?> holder : effects) {
            ids.add(holder.getId());
        }
        return List.copyOf(ids);
    }

    private static void add(String path, int rarity, List<ResourceLocation> effectIds,
                            List<List<ResourceLocation>> variants) {
        NamedJar jar = new NamedJar(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, path),
                "jar.witchmod." + path, clampRarity(rarity), effectIds, variants);
        ALL.add(jar);
        BY_ID.put(jar.id(), jar);
    }

    public static List<NamedJar> all() {
        return ALL;
    }

    public static NamedJar byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /** config can override a jar's rarity by its path (e.g. "mining=6"); falls back to the coded default. */
    public static int effectiveRarity(NamedJar jar) {
        Integer override = Config.parsedJarRarityOverrides().get(jar.id().getPath());
        return clampRarity(override != null ? override : jar.defaultRarity());
    }

    /** the neutral rarity the weighting pivots around, so the push is centred rather than favouring commons. */
    private static final double MID_RARITY = (MIN_RARITY + MAX_RARITY) / 2.0;

    /** a GENTLE, centred nudge: weight = base^(MID - rarity), so mid-rarity is neutral and the extremes only lean
     *  slightly (a small base keeps it close to uniform with so many jars in the pool). */
    private static double weight(NamedJar jar) {
        return Math.pow(Config.JAR_RARITY_WEIGHT_BASE.get(), MID_RARITY - effectiveRarity(jar));
    }

    /** a rarity-weighted random preset (null only if the list is somehow empty). */
    public static NamedJar pick(RandomSource rng) {
        return pickFrom(ALL, rng);
    }

    /** the derived variant (cursed / blessed / mixed) a preset's effects add up to. */
    private static int kindOf(NamedJar jar) {
        boolean curse = false;
        boolean bless = false;
        for (ResourceLocation id : jar.effectIds()) {
            EffectCategory cat = JarContents.categoryOf(id);
            if (cat == EffectCategory.CURSE) {
                curse = true;
            } else if (cat == EffectCategory.BLESSING) {
                bless = true;
            }
        }
        if (curse && bless) {
            return JarContents.MIXED;
        }
        return bless ? JarContents.BLESSED : JarContents.CURSED;
    }

    /** a weighted preset whose variant matches {@code kind} (cursed/blessed/mixed), or null if there are none. */
    public static NamedJar pickForKind(int kind, RandomSource rng) {
        List<NamedJar> pool = new ArrayList<>();
        for (NamedJar jar : ALL) {
            if (kindOf(jar) == kind) {
                pool.add(jar);
            }
        }
        return pickFrom(pool, rng);
    }

    private static NamedJar pickFrom(List<NamedJar> pool, RandomSource rng) {
        double total = 0.0;
        for (NamedJar jar : pool) {
            total += weight(jar);
        }
        if (total <= 0.0) {
            return null;
        }
        double roll = rng.nextDouble() * total;
        for (NamedJar jar : pool) {
            roll -= weight(jar);
            if (roll < 0.0) {
                return jar;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** the preset's effects at rolled ritual-length durations. */
    private static List<CapturedEffect> buildCaptured(NamedJar jar, RandomSource rng) {
        int min = Config.RITUAL_MIN_DURATION_TICKS.get();
        int max = Config.RITUAL_MAX_DURATION_TICKS.get();
        List<CapturedEffect> list = new ArrayList<>();
        for (ResourceLocation effectId : jar.rollEffects(rng)) {
            list.add(new CapturedEffect(effectId, min + rng.nextInt(Math.max(1, max - min + 1))));
        }
        return list;
    }

    /** builds the itemstack: the derived jar variant, its effects at a rolled duration, tagged as the preset. */
    public static ItemStack createStack(NamedJar jar, RandomSource rng) {
        ItemStack stack = JarContents.stackFor(buildCaptured(jar, rng), 1);
        stack.set(WitchModDataComponents.NAMED_JAR, jar.id());
        return stack;
    }

    /** fills an EXISTING jar stack (its variant already matching) with the preset — used by the auto-fill. */
    public static void fillStack(ItemStack stack, NamedJar jar, RandomSource rng) {
        stack.set(WitchModDataComponents.CAPTURED_EFFECTS, List.copyOf(buildCaptured(jar, rng)));
        stack.set(WitchModDataComponents.NAMED_JAR, jar.id());
    }

    /** picks a weighted preset and builds its stack in one go. */
    public static ItemStack rollStack(RandomSource rng) {
        NamedJar jar = pick(rng);
        return jar == null ? ItemStack.EMPTY : createStack(jar, rng);
    }

    private static int clampRarity(int rarity) {
        return Math.max(MIN_RARITY, Math.min(MAX_RARITY, rarity));
    }
}
