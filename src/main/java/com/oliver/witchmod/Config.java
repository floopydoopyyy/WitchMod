package com.oliver.witchmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * server config, split by topic into several files under {@code config/witchmod/} — rules.toml (gamerules +
 * ritual formula), curses.toml, blessings.toml, modifiers.toml, items.toml, synergies.toml — each sectioned
 * into labelled sub-groups. all values tunable without code changes; server-type (per-world, auto-synced to
 * clients) since they decide cast resolution. the .comment() strings are user-facing toml docs (full sentences).
 * client-only preferences live separately in {@link ClientConfig}.
 */
public final class Config {
    private static final ModConfigSpec.Builder RULES = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CURSES = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder BLESSINGS = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder MODIFIERS = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder ITEMS = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SYNERGIES = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder MISC = new ModConfigSpec.Builder();

    // ── general server rules (rules.toml [general]) ──
    // NOTE: every .comment("...") string in this file becomes a comment in the generated .toml and ships in the
    //       jar — edit them freely to write your own explanations. this first one is the rules.toml file header.
    static { RULES.comment("witchmod — rules.toml: server gamerules + the ritual success/backfire formula.",
            "server-type config: it lives per-world under config/witchmod/ and is auto-synced to clients.").push("general"); }
    public static final ModConfigSpec.BooleanValue DISCOVERY_ENABLED = RULES
            .comment("Whether the discovery / Compendium rumour system runs at all. When false NOTHING is ever",
                    "'discovered' (effects AND modifiers), no discovery chat alerts fire, and the Compendium shows",
                    "everything as already known (no rumour pages).")
            .define("discoverySystemEnabled", true);
    public static final ModConfigSpec.DoubleValue GLOBAL_COST_MULTIPLIER = RULES
            .comment("A global multiplier on every ritual's Cursed Essence cost (1.0 = unchanged, 0.5 = half price,",
                    "2.0 = double). Stacks on top of per-effect costs and modifier deltas.")
            .defineInRange("globalCostMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.BooleanValue GUARANTEED_IF_FULLY_PAID = RULES
            .comment("When true, paying the FULL essence cost GUARANTEES success — the residual ~5% fizzle at full",
                    "payment is removed. Underpaying can still fail. (ritualNeverFails is the stronger 'always succeed'.)")
            .define("guaranteedIfFullyPaid", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ATTACHMENTS = RULES
            .comment("Attachment (curse/blessing) ids that are DISABLED and can never be present — e.g.",
                    "\"witchmod:violence\" or just \"violence\". Every in-world route (Table, jar, coin, effigy, bell,",
                    "infectious spread) refuses them; disabledAttachmentsCommandBypass decides whether op commands still can.")
            .defineListAllowEmpty("disabledAttachments", List.of(), () -> "", Config::validateId);
    public static final ModConfigSpec.BooleanValue DISABLED_ATTACHMENTS_COMMAND_BYPASS = RULES
            .comment("When true, the op commands /bewitch apply|dummy may STILL apply a disabled attachment (only the",
                    "in-world routes are blocked). When false, commands are blocked too.")
            .define("disabledAttachmentsCommandBypass", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_MODIFIERS = RULES
            .comment("Table modifier ids that are DISABLED — e.g. \"dragons_breath\", \"clock\". A disabled modifier is",
                    "refused in the Table's modifier slot (no command bypass).")
            .defineListAllowEmpty("disabledModifiers", List.of(), () -> "", Config::validateId);
    public static final ModConfigSpec.BooleanValue IGNORE_CLIENT_OPT_OUTS = RULES
            .comment("Players can opt out of certain client-side curses in their own client config. When this is false",
                    "(the default) the server HONOURS those opt-outs — a cast of an opted-out effect on that player is",
                    "refused, the caster is quietly told, and any essence is refunded. Set true to IGNORE all client",
                    "opt-outs, so those curses always land regardless of the target's preference.")
            .define("ignoreClientOptOuts", false);
    public static final ModConfigSpec.BooleanValue SPECIAL_ATTACHMENTS_GATED = RULES
            .comment("The secret attachments (Pandora's Box, Shadow, Cornucopia, Puppeteer). When true (the default) each",
                    "stays hidden from the Compendium and from every random roll until a player has discovered every",
                    "other effect of its kind (all curses / all blessings). A caster without that knowledge is warned on",
                    "their first attempt, and every later attempt BACKFIRES. Set false to treat them like any other",
                    "attachment: visible as rumours, rollable, and castable by anyone.")
            .define("specialAttachmentsGated", true);
    static { RULES.pop(); }

    // ── recipe toggles (items.toml [recipes]) ──
    static { ITEMS.comment("witchmod — items.toml: item/block knobs (jars, grenade, ward, bell, holy water …),",
            "recipe toggles and banned items. edit any comment line below; it ships in the jar.").push("recipes"); }
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_RECIPES = ITEMS
            .comment("Item ids whose crafting recipe is DISABLED — e.g. \"witchmod:voodoo_doll\". Any recipe producing a",
                    "listed item is filtered out at recipe load, so it can't be crafted (loot/other routes still work).",
                    "Accepts the id with or without the witchmod: namespace. Takes effect on world load or /reload.")
            .defineListAllowEmpty("disabledRecipes", List.of(), () -> "", Config::validateId);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_ITEMS = ITEMS
            .comment("Fully BANNED item ids — any item, vanilla or modded, e.g. \"minecraft:tnt\" or \"witchmod:voodoo_doll\".",
                    "A banned item cannot be crafted or smelted (its result is voided), and, with forceClearBannedItems on,",
                    "is stripped from player inventories on sight. Broader than disabledRecipes, which only blocks this",
                    "mod's own recipes. Accepts an id with or without its namespace.")
            .defineListAllowEmpty("bannedItems", List.of(), () -> "", Config::validateId);
    public static final ModConfigSpec.BooleanValue FORCE_CLEAR_BANNED_ITEMS = ITEMS
            .comment("When true, any bannedItems already in a player's inventory are periodically deleted, not just blocked",
                    "from being made — so pre-existing stocks are cleared out too. Off by default (blocking only).")
            .define("forceClearBannedItems", false);
    static { ITEMS.pop(); }

    static { RULES.push("curses"); }
    public static final ModConfigSpec.BooleanValue CURSES_ENABLED = RULES
            .comment("Whether curses can be applied at all.")
            .define("cursesEnabled", true);

    static { RULES.pop(); }
    static { RULES.push("blessings"); }
    public static final ModConfigSpec.BooleanValue BLESSINGS_ENABLED = RULES
            .comment("Whether blessings can be applied at all.")
            .define("blessingsEnabled", true);

    static { RULES.pop(); }
    static { RULES.push("backfires"); }
    public static final ModConfigSpec.BooleanValue BACKFIRES_ENABLED = RULES
            .comment("Whether a failed Table ritual can backfire onto the caster. When false, that",
                    "probability mass falls through to a harmless fizzle instead.")
            .define("backfiresEnabled", true);

    static { RULES.pop(); }
    static { RULES.push("ritual"); }
    public static final ModConfigSpec.BooleanValue RITUAL_NEVER_FAILS = RULES
            .comment("When true, a Table ritual always succeeds — the slight chance of a fizzle/backfire is removed,",
                    "so essence still just tunes cost, not the odds.")
            .define("ritualNeverFails", false);

    static { ITEMS.push("ward"); }
    public static final ModConfigSpec.BooleanValue WARD_DURABILITY_DECAYS = ITEMS
            .comment("Whether deflecting a curse costs the Ward a durability point. Set false to let",
                    "players opt out of the durability tax entirely.")
            .define("wardDurabilityDecays", true);

    static { RULES.pop(); }
    static { RULES.push("grace"); }
    public static final ModConfigSpec.IntValue GRACE_PERIOD_TICKS = RULES
            .comment("How many ticks after a player's first-ever join they cannot be targeted by another",
                    "player's curse/blessing (self-casts are unaffected). 6000 ticks = 5 minutes. 0 disables it.")
            .defineInRange("gracePeriodTicks", 6000, 0, Integer.MAX_VALUE);

    static { RULES.pop(); }
    static { RULES.push("max"); }
    public static final ModConfigSpec.IntValue MAX_ACTIVE_EFFECTS_PER_PLAYER = RULES
            .comment("The Limit: the most curses AND the most blessings a player can carry at once. This is an",
                    "ABSOLUTE hard cap — every apply path (table, jar, coin, effigy, command) refuses to exceed it",
                    "(Infectious is exempt). It also doubles as the backfire-RISK threshold at the Table.")
            .defineInRange("maxActiveEffectsPerPlayer", 3, 1, 64);

    static { RULES.pop(); }
    static { RULES.push("starter"); }
    public static final ModConfigSpec.BooleanValue STARTER_DISCOVERY_ENABLED = RULES
            .comment("Whether every player silently discovers a few starter curses/blessings the first time they",
                    "join — so the Compendium isn't entirely blank to begin with. One-time, per player.")
            .define("starterDiscoveryEnabled", true);
    public static final ModConfigSpec.IntValue STARTER_DISCOVERY_CURSES = RULES
            .comment("Starter discovery: how many random curses to reveal on first login.")
            .defineInRange("starterDiscoveryCurses", 1, 0, 64);
    public static final ModConfigSpec.IntValue STARTER_DISCOVERY_BLESSINGS = RULES
            .comment("Starter discovery: how many random blessings to reveal on first login.")
            .defineInRange("starterDiscoveryBlessings", 1, 0, 64);
    public static final ModConfigSpec.IntValue STARTER_DISCOVERY_POWER_MIN = RULES
            .comment("Starter discovery: only reveal effects with a power level at or above this (0-100).")
            .defineInRange("starterDiscoveryPowerMin", 5, 0, 100);
    public static final ModConfigSpec.IntValue STARTER_DISCOVERY_POWER_MAX = RULES
            .comment("Starter discovery: only reveal effects with a power level at or below this (0-100).")
            .defineInRange("starterDiscoveryPowerMax", 25, 0, 100);

    static { ITEMS.pop(); }
    static { ITEMS.push("ledger"); }
    public static final ModConfigSpec.IntValue LEDGER_RANGE = ITEMS
            .comment("How far (in blocks) a Ledger block records ritual activity around it and reacts with",
                    "particle feedback. Each Ledger permanently keeps its most recent casts within this range.")
            .defineInRange("ledgerRange", 48, 1, 256);
    public static final ModConfigSpec.IntValue LEDGER_MAX_ENTRIES = ITEMS
            .comment("How many recent casts a single Ledger keeps (oldest is dropped to record a new one). Persisted with the world.")
            .defineInRange("ledgerMaxEntries", 3, 1, 64);

    static { ITEMS.pop(); }
    static { ITEMS.push("warding"); }
    public static final ModConfigSpec.IntValue WARDING_TOTEM_RANGE = ITEMS
            .comment("How far (in blocks) a Warding Totem shields players — anyone within this range of a placed",
                    "totem gets the 'Protected' effect and CANNOT have any curse/blessing/voodoo applied to them.")
            .defineInRange("wardingTotemRange", 32, 1, 256);

    static { ITEMS.pop(); }
    static { ITEMS.push("scrying"); }
    public static final ModConfigSpec.IntValue SCRYING_REFRESH_TICKS = ITEMS
            .comment("Scrying Mirror: while you keep holding, the panel re-reads the target this often so the timers stay live.")
            .defineInRange("scryingRefreshTicks", 10, 1, 200);
    public static final ModConfigSpec.IntValue SCRYING_SLOWNESS_AMPLIFIER = ITEMS
            .comment("Scrying Mirror: the Slowness level applied while peering (0 = Slowness I; 3 = Slowness IV — significantly slowed).")
            .defineInRange("scryingSlownessAmplifier", 3, 0, 9);

    static { ITEMS.pop(); }
    static { ITEMS.push("purify"); }
    public static final ModConfigSpec.IntValue PURIFY_DRAIN_TICKS_PER_TICK = ITEMS
            .comment("Holy (Purifying) Water: how many ticks of curse/blessing timer are burned off PER GAME TICK",
                    "while a player with active effects stands in it. 20 = 1 real second of timer drained every tick",
                    "(20x speed), so the timers visibly race down. Higher = faster cleanse.")
            .defineInRange("purifyDrainTicksPerTick", 20, 1, 1200);
    public static final ModConfigSpec.IntValue PURIFY_RAMP_TICKS = ITEMS
            .comment("Holy Water: the cleanse RAMPS the longer you soak — it starts at purifyRampMinMult of the base",
                    "drain and climbs to purifyRampMaxMult over this many ticks of continuous bathing (reset when you",
                    "leave the water). 400 = 20 seconds.")
            .defineInRange("purifyRampTicks", 400, 1, 12000);
    public static final ModConfigSpec.DoubleValue PURIFY_RAMP_MIN_MULT = ITEMS
            .comment("Holy Water: the drain multiplier the instant you get in (0.5 = half the standard speed).")
            .defineInRange("purifyRampMinMult", 0.5, 0.05, 10.0);
    public static final ModConfigSpec.DoubleValue PURIFY_RAMP_MAX_MULT = ITEMS
            .comment("Holy Water: the drain multiplier once the first ramp is full (3.0 = 3x the standard speed).")
            .defineInRange("purifyRampMaxMult", 3.0, 0.1, 20.0);
    public static final ModConfigSpec.IntValue PURIFY_RAMP2_TICKS = ITEMS
            .comment("Holy Water: a SECOND ramp after the first — over this many more ticks of soaking, the drain",
                    "climbs from purifyRampMaxMult to DOUBLE it, so a long soak really races. 200 = 10 seconds; 0 disables it.")
            .defineInRange("purifyRamp2Ticks", 200, 0, 12000);
    static { ITEMS.pop(); }
    static { ITEMS.push("holy"); }
    public static final ModConfigSpec.IntValue HOLY_WATER_DRINK_DRAIN_TICKS = ITEMS
            .comment("Holy (Purifying) Water: ticks of timer washed off EVERY active attachment when a Thirst-cursed",
                    "player DRINKS it (a clean, risk-free drink). 6000 = 5 minutes.")
            .defineInRange("holyWaterDrinkDrainTicks", 6000, 0, 72000);

    static { ITEMS.pop(); }
    static { ITEMS.push("purify"); }
    public static final ModConfigSpec.DoubleValue PURIFY_UNDEAD_DAMAGE = ITEMS
            .comment("Holy (Purifying) Water: damage dealt to an undead mob per hit while it stands in the fluid",
                    "(vanilla invulnerability frames space the hits out, so it's periodic tick damage).")
            .defineInRange("purifyUndeadDamage", 2.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue PURIFY_SHARD_CHANCE = ITEMS
            .comment("Holy Water acquisition: each Amethyst Shard right-clicked into a WATER cauldron adds this",
                    "much cumulative chance for the water to turn holy (0.06 = a stacking +6% per shard).")
            .defineInRange("purifyShardChance", 0.06, 0.0, 1.0);

    static { RULES.pop(); }
    static { RULES.push("limit"); }
    public static final ModConfigSpec.IntValue LIMIT_BACKFIRE_BOOST_PERCENT = RULES
            .comment("Flat backfire-chance increase (percentage points) applied when the target is already",
                    "at or over maxActiveEffectsPerPlayer.")
            .defineInRange("limitBackfireBoostPercent", 20, 0, 100);

    static { RULES.pop(); }
    static { RULES.push("escalating"); }
    public static final ModConfigSpec.IntValue ESCALATING_COST_PERCENT = RULES
            .comment("Extra essence cost (percent) required to cast an effect on a target who already has",
                    "that exact effect active (the escalating cost).")
            .defineInRange("escalatingCostPercent", 50, 0, 500);

    static { RULES.pop(); }
    static { RULES.push("success"); }
    public static final ModConfigSpec.IntValue SUCCESS_FLOOR_PERCENT = RULES
            .comment("Section 5.7 formula: minimum success chance regardless of essence spent.")
            .defineInRange("successFloorPercent", 30, 0, 100);

    public static final ModConfigSpec.IntValue SUCCESS_SPAN_PERCENT = RULES
            .comment("Section 5.7 formula: how much essenceSpent/baseCost can add on top of the floor.")
            .defineInRange("successSpanPercent", 65, 0, 100);

    public static final ModConfigSpec.IntValue SUCCESS_CAP_PERCENT = RULES
            .comment("Section 5.7 formula: success chance never exceeds this (Netherstar modifier overrides it).")
            .defineInRange("successCapPercent", 95, 0, 100);

    static { RULES.pop(); }
    static { RULES.push("backfire"); }
    public static final ModConfigSpec.IntValue BACKFIRE_START_PERCENT = RULES
            .comment("Section 5.7 formula: backfire chance at essenceSpent = 0, scaling down to 0 at full cost.")
            .defineInRange("backfireStartPercent", 25, 0, 100);

    // backfire tiering — keyed off baseCost as a stand-in for a real per-attachment tier/strength value
    public static final ModConfigSpec.IntValue BACKFIRE_LOW_TIER_COST = RULES
            .comment("PLACEHOLDER tier: attachments with baseCost <= this are LOW tier and never backfire (0%).")
            .defineInRange("backfireLowTierCost", 20, 0, 1000);
    public static final ModConfigSpec.IntValue BACKFIRE_HIGH_TIER_COST = RULES
            .comment("PLACEHOLDER tier: attachments with baseCost >= this are HIGH tier and always keep a small",
                    "minimum backfire chance even at full essence.")
            .defineInRange("backfireHighTierCost", 50, 0, 1000);
    public static final ModConfigSpec.IntValue BACKFIRE_HIGH_TIER_FLOOR_PERCENT = RULES
            .comment("PLACEHOLDER tier: the minimum backfire chance HIGH-tier attachments retain at full essence.")
            .defineInRange("backfireHighTierFloorPercent", 5, 0, 100);

    // --- Backfire outcome (the ritual turning on the caster) ------------------------------------------
    public static final ModConfigSpec.DoubleValue BACKFIRE_EXPLOSION_POWER_MIN = RULES
            .comment("Backfire (table explodes): explosion power for the weakest attachment (⚠ scaled by baseCost",
                    "as a PLACEHOLDER for real attachment strength).")
            .defineInRange("backfireExplosionPowerMin", 1.5, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue BACKFIRE_EXPLOSION_POWER_MAX = RULES
            .comment("Backfire (table explodes): explosion power for the strongest attachment (⚠ baseCost placeholder).")
            .defineInRange("backfireExplosionPowerMax", 4.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue BACKFIRE_DAMAGE_MIN = RULES
            .comment("Backfire (damage type): hearts*2 dealt for the weakest attachment (⚠ baseCost placeholder).")
            .defineInRange("backfireDamageMin", 4.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue BACKFIRE_DAMAGE_MAX = RULES
            .comment("Backfire (damage type): hearts*2 dealt for the strongest attachment (⚠ baseCost placeholder).")
            .defineInRange("backfireDamageMax", 12.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue BACKFIRE_STRENGTH_COST_MIN = RULES
            .comment("⚠ PLACEHOLDER strength scale: the baseCost mapped to the MIN end of backfire explosion/damage.")
            .defineInRange("backfireStrengthCostMin", 15, 0, 1000);
    public static final ModConfigSpec.IntValue BACKFIRE_STRENGTH_COST_MAX = RULES
            .comment("⚠ PLACEHOLDER strength scale: the baseCost mapped to the MAX end of backfire explosion/damage.")
            .defineInRange("backfireStrengthCostMax", 100, 0, 1000);

    // --- Thrown jars (splash + lash) ------------------------------------------------------------------
    static { ITEMS.pop(); }
    static { ITEMS.push("jar"); }
    public static final ModConfigSpec.DoubleValue JAR_SPLASH_RADIUS = ITEMS
            .comment("Thrown Jar: the splash radius (blocks) its stored effects hit — deliberately bigger than a vanilla splash potion.")
            .defineInRange("jarSplashRadius", 5.0, 1.0, 24.0);
    static { ITEMS.pop(); }
    static { ITEMS.push("lash"); }
    public static final ModConfigSpec.DoubleValue LASH_RANGE = ITEMS
            .comment("Thrown Jar (lash): if the splash catches no one, a homing lash hunts the nearest player within this range (blocks).")
            .defineInRange("jarLashRange", 24.0, 4.0, 128.0);
    public static final ModConfigSpec.DoubleValue LASH_SPEED = ITEMS
            .comment("Thrown Jar (lash): how fast the lash surges toward its target (blocks/tick).")
            .defineInRange("jarLashSpeed", 0.9, 0.1, 5.0);
    public static final ModConfigSpec.IntValue LASH_EXPIRY_TICKS = ITEMS
            .comment("Thrown Jar (lash): how long (ticks) the lash chases before it fizzles out. Run far enough and it can't reach you.")
            .defineInRange("jarLashExpiryTicks", 200, 20, 1200);

    // per-attachment balancing constants

    static { CURSES.comment("witchmod — curses.toml: per-curse balance knobs, one [section] per curse.").push("allergic"); }
    // -- diet: picked from the overworld time of day when the curse lands --
    public static final ModConfigSpec.IntValue ALLERGIC_CARNIVORE_START = CURSES
            .comment("Allergic: time of day (0..23999, 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight) from",
                    "which a cast gives the CARNIVORE diet (allergic to plants). Each diet runs from its start until",
                    "the next diet's start, wrapping round midnight.")
            .defineInRange("allergicCarnivoreStartTime", 0, 0, 23999);
    public static final ModConfigSpec.IntValue ALLERGIC_VEGETARIAN_START = CURSES
            .comment("Allergic: time of day from which a cast gives the VEGETARIAN diet (allergic to meat).")
            .defineInRange("allergicVegetarianStartTime", 8000, 0, 23999);
    public static final ModConfigSpec.IntValue ALLERGIC_CLEAN_EATER_START = CURSES
            .comment("Allergic: time of day from which a cast gives the CLEAN EATER diet (allergic to magic food and potions).")
            .defineInRange("allergicCleanEaterStartTime", 16000, 0, 23999);
    public static final ModConfigSpec.IntValue ALLERGIC_CAST_MESSAGE_DELAY = CURSES
            .comment("Allergic: ticks after the curse lands before the caster's action bar names the allergy (so it",
                    "doesn't instantly overwrite the ritual's own success message).")
            .defineInRange("allergicCastMessageDelayTicks", 40, 0, 200);

    // -- eating an allergen --
    public static final ModConfigSpec.IntValue ALLERGIC_NUTRITION_PERCENT = CURSES
            .comment("Allergic: percent of the hunger AND saturation a forbidden food actually gives you (0 = nothing).",
                    "Beneficial food/potion effects are always stripped.")
            .defineInRange("allergicNutritionPercent", 10, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_EAT_TIER = CURSES
            .comment("Allergic: reaction tier (1..3) caused by eating or drinking a forbidden item.")
            .defineInRange("allergicEatTier", 3, 1, 3);
    public static final ModConfigSpec.IntValue ALLERGIC_EAT_SECONDS = CURSES
            .comment("Allergic: how long that eating reaction lasts (seconds).")
            .defineInRange("allergicEatSeconds", 30, 1, 600);

    // -- exposure scanning --
    public static final ModConfigSpec.IntValue ALLERGIC_CHECK_INTERVAL = CURSES
            .comment("Allergic: ticks between exposure scans (10 = twice a second).")
            .defineInRange("allergicCheckIntervalTicks", 10, 1, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_BLOCK_RADIUS = CURSES
            .comment("Allergic: how close (blocks) an allergen BLOCK must be to set you off.")
            .defineInRange("allergicBlockRadius", 3, 1, 8);
    public static final ModConfigSpec.IntValue ALLERGIC_BLOCK_SECONDS = CURSES
            .comment("Allergic: an allergen block only ever causes a TIER 1 reaction, lasting this long (topped up while near).")
            .defineInRange("allergicBlockSeconds", 5, 1, 120);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLERGIC_VEGETARIAN_BLOCKS = CURSES
            .comment("Allergic: blocks that set off a VEGETARIAN. Block ids, or #tags.")
            .defineListAllowEmpty("allergicVegetarianBlocks", List.of("minecraft:hay_block", "minecraft:smoker"),
                    () -> "", Config::validateId);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLERGIC_CARNIVORE_BLOCKS = CURSES
            .comment("Allergic: blocks that set off a CARNIVORE. Block ids, or #tags.")
            .defineListAllowEmpty("allergicCarnivoreBlocks", List.of("#minecraft:crops", "#minecraft:flowers",
                    "minecraft:beehive", "minecraft:bee_nest"), () -> "", Config::validateId);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLERGIC_CLEAN_EATER_BLOCKS = CURSES
            .comment("Allergic: blocks that set off a CLEAN EATER. Block ids, or #tags.")
            .defineListAllowEmpty("allergicCleanEaterBlocks", List.of("minecraft:brewing_stand", "minecraft:enchanting_table",
                    "witchmod:bewitching_table", "minecraft:lapis_block", "witchmod:cursed_essence_block"),
                    () -> "", Config::validateId);
    public static final ModConfigSpec.DoubleValue ALLERGIC_MOB_RADIUS = CURSES
            .comment("Allergic: how close (blocks) a hazardous mob, player or dropped item must be to set you off.")
            .defineInRange("allergicMobRadius", 5.0, 1.0, 16.0);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLERGIC_VEGETARIAN_MOBS = CURSES
            .comment("Allergic: entity types that set off a VEGETARIAN. Entity ids, or #tags. (Empty by default — for",
                    "vegetarians it's the meat itself: dropped meat and meat in inventories.)")
            .defineListAllowEmpty("allergicVegetarianMobs", List.of(), () -> "", Config::validateId);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLERGIC_CARNIVORE_MOBS = CURSES
            .comment("Allergic: entity types that set off a CARNIVORE. Entity ids, or #tags.")
            .defineListAllowEmpty("allergicCarnivoreMobs", List.of("minecraft:bee"), () -> "", Config::validateId);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLERGIC_CLEAN_EATER_MOBS = CURSES
            .comment("Allergic: entity types that set off a CLEAN EATER. Entity ids, or #tags.")
            .defineListAllowEmpty("allergicCleanEaterMobs", List.of("minecraft:witch", "#minecraft:illager"),
                    () -> "", Config::validateId);
    public static final ModConfigSpec.BooleanValue ALLERGIC_ITEMS_ARE_HAZARDS = CURSES
            .comment("Allergic: forbidden items on the ground or in a nearby player's inventory count as hazardous mobs.")
            .define("allergicItemsAreHazards", true);
    public static final ModConfigSpec.BooleanValue ALLERGIC_OWN_INVENTORY = CURSES
            .comment("Allergic: carrying a forbidden item in your OWN inventory counts as a hazard too.")
            .define("allergicOwnInventoryCounts", true);
    public static final ModConfigSpec.BooleanValue ALLERGIC_CARNIVORE_FEARS_VEGETARIANS = CURSES
            .comment("Allergic: other players with the Vegetarian allergy count as hazards for a Carnivore.")
            .define("allergicCarnivoreVegetarianPlayers", true);
    public static final ModConfigSpec.BooleanValue ALLERGIC_CLEAN_EATER_FEARS_BUFFS = CURSES
            .comment("Allergic: other players with a positive potion effect (not this mod's blessing markers) count as",
                    "hazards for a Clean Eater.")
            .define("allergicCleanEaterBuffedPlayers", true);

    // -- mob exposure build-up: tier 1 on contact, tier 2 after a while, tier 3 after a while longer --
    public static final ModConfigSpec.IntValue ALLERGIC_MOB_TIER1_SECONDS = CURSES
            .comment("Allergic: a hazardous mob nearby causes tier 1 for this long (topped up while near).")
            .defineInRange("allergicMobTier1Seconds", 8, 1, 120);
    public static final ModConfigSpec.IntValue ALLERGIC_MOB_TIER2_AFTER = CURSES
            .comment("Allergic: seconds of mob exposure before the reaction becomes tier 2.")
            .defineInRange("allergicMobTier2AfterSeconds", 5, 1, 600);
    public static final ModConfigSpec.IntValue ALLERGIC_MOB_TIER2_SECONDS = CURSES
            .comment("Allergic: tier 2 from mob exposure lasts this long (topped up while near).")
            .defineInRange("allergicMobTier2Seconds", 15, 1, 300);
    public static final ModConfigSpec.IntValue ALLERGIC_MOB_TIER3_AFTER = CURSES
            .comment("Allergic: FURTHER seconds of mob exposure (on top of the tier 2 wait) before it becomes tier 3.")
            .defineInRange("allergicMobTier3AfterSeconds", 30, 1, 600);
    public static final ModConfigSpec.IntValue ALLERGIC_MOB_TIER3_SECONDS = CURSES
            .comment("Allergic: tier 3 from mob exposure lasts this long (topped up while near).")
            .defineInRange("allergicMobTier3Seconds", 30, 1, 600);
    public static final ModConfigSpec.DoubleValue ALLERGIC_VEGETARIAN_EXPOSURE_MULT = CURSES
            .comment("Allergic: vegetarians build mob exposure this much faster (1.25 = 25% faster).")
            .defineInRange("allergicVegetarianExposureMultiplier", 1.25, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue ALLERGIC_EXPOSURE_DECAY = CURSES
            .comment("Allergic: how fast built-up exposure drains away once clear of every hazard, relative to how fast",
                    "it builds (1.0 = the same rate, 2.0 = twice as fast).")
            .defineInRange("allergicExposureDecayMultiplier", 1.0, 0.0, 20.0);

    // -- what each reaction tier does --
    public static final ModConfigSpec.IntValue ALLERGIC_TIER1_COOLDOWN_PERCENT = CURSES
            .comment("Allergic tier 1: attack cooldown is this percent longer.")
            .defineInRange("allergicTier1AttackCooldownPercent", 40, 0, 1000);
    public static final ModConfigSpec.IntValue ALLERGIC_TIER2_COOLDOWN_PERCENT = CURSES
            .comment("Allergic tier 2: attack cooldown is this percent longer.")
            .defineInRange("allergicTier2AttackCooldownPercent", 80, 0, 1000);
    public static final ModConfigSpec.IntValue ALLERGIC_TIER3_COOLDOWN_PERCENT = CURSES
            .comment("Allergic tier 3: attack cooldown is this percent longer.")
            .defineInRange("allergicTier3AttackCooldownPercent", 150, 0, 1000);
    public static final ModConfigSpec.IntValue ALLERGIC_TIER1_HEARTS_LOST = CURSES
            .comment("Allergic tier 1: hearts of max health lost (shown as green hearts). Always leaves at least 1 heart.")
            .defineInRange("allergicTier1HeartsLost", 2, 0, 50);
    public static final ModConfigSpec.IntValue ALLERGIC_TIER2_HEARTS_LOST = CURSES
            .comment("Allergic tier 2: total hearts of max health lost.")
            .defineInRange("allergicTier2HeartsLost", 4, 0, 50);
    public static final ModConfigSpec.IntValue ALLERGIC_TIER3_HEARTS_LOST = CURSES
            .comment("Allergic tier 3: total hearts of max health lost.")
            .defineInRange("allergicTier3HeartsLost", 5, 0, 50);
    public static final ModConfigSpec.DoubleValue ALLERGIC_TIER1_FOV_REDUCTION = CURSES
            .comment("Allergic tier 1: field of view narrowed by this fraction (0.05 = 5% narrower).")
            .defineInRange("allergicTier1FovReduction", 0.0, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue ALLERGIC_TIER2_FOV_REDUCTION = CURSES
            .comment("Allergic tier 2: field of view narrowed by this fraction.")
            .defineInRange("allergicTier2FovReduction", 0.05, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue ALLERGIC_TIER3_FOV_REDUCTION = CURSES
            .comment("Allergic tier 3: field of view narrowed by this fraction.")
            .defineInRange("allergicTier3FovReduction", 0.1, 0.0, 0.5);

    static { CURSES.pop(); }
    static { CURSES.push("backseat"); }
    public static final ModConfigSpec.IntValue BACKSEAT_EPISODE_SECONDS = CURSES
            .comment("Backseat Driver: how long the AI keeps the wheel once it takes over.")
            .defineInRange("backseatEpisodeSeconds", 10, 1, 300);


    public static final ModConfigSpec.IntValue BACKSEAT_EARLY_EXIT_COOLDOWN_SECONDS = CURSES
            .comment("Backseat Driver: SHORTER cooldown used when the rider bailed out to end it early —",
                    "escaping buys you less peace than sitting through it.")
            .defineInRange("backseatEarlyExitCooldownSeconds", 20, 0, 3600);

    public static final ModConfigSpec.IntValue BACKSEAT_CHANCE_GROWTH_PER_SECOND = CURSES
            .comment("Backseat Driver: percentage points the takeover chance grows per second of riding.")
            .defineInRange("backseatChanceGrowthPerSecond", 1, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_CHANCE_CAP_PERCENT = CURSES
            .comment("Backseat Driver: normal ceiling the growing takeover chance is clamped to.")
            .defineInRange("backseatChanceCapPercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_HAZARD_CHANCE_CAP_PERCENT = CURSES
            .comment("Backseat Driver: the ceiling is raised to THIS the moment a hazard (lava/water/a big",
                    "drop) is spotted nearby — the AI is far more likely to grab the wheel near danger.")
            .defineInRange("backseatHazardChanceCapPercent", 70, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_HAZARD_SCAN_RADIUS = CURSES
            .comment("Backseat Driver: how far to look for hazards to steer into (and to raise the cap).")
            .defineInRange("backseatHazardScanRadius", 12, 1, 32);

    public static final ModConfigSpec.IntValue BACKSEAT_SPEED_BOOST_LEVEL = CURSES
            .comment("Backseat Driver: Speed effect level given to the hijacked mount for the episode, so it",
                    "genuinely bolts under its own power (0 = no boost). The mount always uses its own",
                    "movement — we never shove it with raw velocity, which is what made it slide/hover.")
            .defineInRange("backseatSpeedBoostLevel", 2, 0, 5);

    public static final ModConfigSpec.DoubleValue BACKSEAT_NAV_SPEED_MULTIPLIER = CURSES
            .comment("Backseat Driver: pathfinding speed multiplier for mounts that AREN'T rider-steered",
                    "(e.g. a pig without a carrot on a stick) — those walk there via their own navigation.")
            .defineInRange("backseatNavSpeedMultiplier", 1.6, 0.1, 5.0);

    static { CURSES.pop(); }
    static { CURSES.push("bad"); }
    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_GRAVITY_MULTIPLIER = CURSES
            .comment("Bad Swimmer: gravity multiplier applied while in liquid. Vanilla divides gravity by 16",
                    "underwater (the slow bob), so ~16 restores air-like sinking. NOTE vanilla skips fluid",
                    "gravity ENTIRELY while sprinting, so this half only bites when you AREN'T swimming;",
                    "badSwimmerConstantPull is the half that always applies. 16.0 is the play-tested value.",
                    "(The gravity attribute is also hard-capped at 1.0, i.e. a multiplier of ~12.)")
            .defineInRange("badSwimmerGravityMultiplier", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_CONSTANT_PULL = CURSES
            .comment("Bad Swimmer: constant downward pull (blocks/tick^2) while in liquid, applied client-side",
                    "so it bites even while SWIMMING — this is what makes staying on the surface a battle",
                    "instead of the curse simply switching off the moment you sprint-swim.",
                    "Reference: a full swim-up tops out around 0.048, so values near that make ascending",
                    "break even, and anything above it means you sink no matter how hard you swim.")
            .defineInRange("badSwimmerConstantPull", 0.04, 0.0, 0.5);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_ENTRY_PLUNGE = CURSES
            .comment("Bad Swimmer: one-shot downward yank the moment you break the surface, so entering water",
                    "drags you under hard before the constant pull takes over.")
            .defineInRange("badSwimmerEntryPlunge", 0.4, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_WATER_EFFICIENCY = CURSES
            .comment("Bad Swimmer: water movement efficiency while in liquid. 1.0 makes water acceleration",
                    "and friction match LAND exactly, i.e. you walk along the bottom instead of swimming.")
            .defineInRange("badSwimmerWaterEfficiency", 1.0, 0.0, 1.0);

    static { CURSES.pop(); }
    static { CURSES.push("violence"); }
    public static final ModConfigSpec.IntValue VIOLENCE_CHECK_INTERVAL_TICKS = CURSES
            .comment("Violence: how often (ticks) to roll for a forced swing. 20 = once a second.")
            .defineInRange("violenceCheckIntervalTicks", 20, 1, 1200);

    public static final ModConfigSpec.IntValue VIOLENCE_SIGHT_CONE_DEGREES = CURSES
            .comment("Violence: how far off-centre something can be and still count as 'looked directly at'.",
                    "Sight-based swings also require line of sight, so you can't be set off through a wall.")
            .defineInRange("violenceSightConeDegrees", 20, 1, 90);

    public static final ModConfigSpec.IntValue VIOLENCE_SIGHT_CHANCE_PERCENT = CURSES
            .comment("Violence: chance (%) per check of swinging at whatever you are looking straight at.",
                    "These take INSTANT priority over impulsive urges and never ramp — staring at something",
                    "in reach is simply dangerous, so keep your eyes off people.")
            .defineInRange("violenceSightChancePercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_SIGHT_HAZARD_CHANCE_PERCENT = CURSES
            .comment("Violence: chance (%) per check when the thing you are looking at would be shoved off a",
                    "ledge or into lava. Looking at someone perched over a drop is close to a death sentence.")
            .defineInRange("violenceSightHazardChancePercent", 75, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_IMPULSIVE_BASE_CHANCE_PERCENT = CURSES
            .comment("Violence: starting chance (%) per check of an impulsive swing — the camera-hijacking",
                    "kind, aimed at something you are NOT looking at. Can happen at any time.")
            .defineInRange("violenceImpulsiveBaseChancePercent", 2, 0, 100);

    public static final ModConfigSpec.DoubleValue VIOLENCE_IMPULSIVE_RAMP_PER_SECOND = CURSES
            .comment("Violence: percentage points the impulsive chance grows per second since the last swing.",
                    "Unlike the sight swings, these DO build up the longer you have gone without one.")
            .defineInRange("violenceImpulsiveRampPerSecond", 0.5, 0.0, 100.0);

    public static final ModConfigSpec.IntValue VIOLENCE_IMPULSIVE_CAP_PERCENT = CURSES
            .comment("Violence: ceiling the ramping impulsive chance is clamped to.")
            .defineInRange("violenceImpulsiveCapPercent", 30, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_IMPULSIVE_HAZARD_CHANCE_PERCENT = CURSES
            .comment("Violence: the ramp is overridden with THIS the moment something has been loitering at a",
                    "ledge/hazard for violenceHazardSustainTicks — lingering next to a drop near a cursed",
                    "player is asking for it.")
            .defineInRange("violenceImpulsiveHazardChancePercent", 90, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_HAZARD_SUSTAIN_TICKS = CURSES
            .comment("Violence: how long (ticks) something must stay perched at a ledge/hazard before it",
                    "massively boosts impulsive urges. 30 = 1.5 seconds.")
            .defineInRange("violenceHazardSustainTicks", 30, 1, 600);

    public static final ModConfigSpec.IntValue VIOLENCE_LOW_HEALTH_PERCENT = CURSES
            .comment("Violence: at or below this % of max health, a target counts as wounded and climbs the",
                    "priority order — though never above an environmental-hazard shove, which outranks it.")
            .defineInRange("violenceLowHealthPercent", 25, 1, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_RANDOM_TARGET_CHANCE_PERCENT = CURSES
            .comment("Violence: chance (%) that an impulsive urge throws the whole priority order out and",
                    "just swings at someone at random — so it never becomes perfectly predictable.")
            .defineInRange("violenceRandomTargetChancePercent", 10, 0, 100);

    public static final ModConfigSpec.DoubleValue VIOLENCE_TARGET_RANGE = CURSES
            .comment("Violence: how far away something can be and still get swung at.")
            .defineInRange("violenceTargetRange", 3.5, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue VIOLENCE_PLAYER_PRIORITY_MULTIPLIER = CURSES
            .comment("Violence: how much more attractive a player is than a mob when picking a victim.")
            .defineInRange("violencePlayerPriorityMultiplier", 3.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue VIOLENCE_HAZARD_BIAS_MULTIPLIER = CURSES
            .comment("Violence: multiplier applied BOTH to a victim's pick weight and to the trigger chance",
                    "when knocking them back would shove them off a ledge or into something nasty — so the",
                    "curse both waits for those moments and aims at them.")
            .defineInRange("violenceHazardBiasMultiplier", 4.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue VIOLENCE_HAZARD_SHOVE_DISTANCE = CURSES
            .comment("Violence: how many blocks along the knockback direction to check for a hazard/ledge.")
            .defineInRange("violenceHazardShoveDistance", 4, 1, 16);

    public static final ModConfigSpec.IntValue VIOLENCE_LEDGE_DROP_MIN = CURSES
            .comment("Violence: how many blocks of empty air below a spot make it count as a ledge worth",
                    "shoving someone off.")
            .defineInRange("violenceLedgeDropMin", 3, 1, 32);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_CHANCE_PERCENT = CURSES
            .comment("Violence: chance (%) that an urge becomes a full flurry instead of a single swing.")
            .defineInRange("violenceMultiHitChancePercent", 12, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_MIN = CURSES
            .comment("Violence: fewest EXTRA swings in a flurry (on top of the first one).")
            .defineInRange("violenceMultiHitMin", 2, 1, 20);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_MAX = CURSES
            .comment("Violence: most EXTRA swings in a flurry. Clamped to be >= violenceMultiHitMin at use.")
            .defineInRange("violenceMultiHitMax", 4, 1, 20);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_SPACING_TICKS = CURSES
            .comment("Violence: ticks between the swings of a flurry.")
            .defineInRange("violenceMultiHitSpacingTicks", 6, 1, 60);

    public static final ModConfigSpec.BooleanValue VIOLENCE_FULL_STRENGTH_SWINGS = CURSES
            .comment("Violence: force each stolen swing to land at FULL attack strength, so it hits as hard",
                    "as a swing you timed yourself (and can sweep/crit) rather than for a fifth of the damage",
                    "if you happened to be mid-cooldown. Independent of the cooldown refund below.")
            .define("violenceFullStrengthSwings", true);

    public static final ModConfigSpec.BooleanValue VIOLENCE_REFUNDS_ATTACK_COOLDOWN = CURSES
            .comment("Violence: whether a forced swing hands your attack cooldown back. FALSE (default) means",
                    "a stolen swing costs you the cooldown exactly like a real one, so your own next hit is",
                    "weakened — it takes something from you rather than being a free extra attack.")
            .define("violenceRefundsAttackCooldown", false);

    static { CURSES.pop(); }
    static { CURSES.push("butterfingers"); }
    public static final ModConfigSpec.IntValue BUTTERFINGERS_COOLDOWN_TICKS = CURSES
            .comment("Butterfingers: internal cooldown after ANY fumble. Shared by all three triggers, so a",
                    "passive slip buys you the same grace as one caused by a hit — you can't be stripped.")
            .defineInRange("butterfingersCooldownTicks", 600, 0, 24000);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_PASSIVE_INTERVAL_TICKS = CURSES
            .comment("Butterfingers: how often (ticks) to roll the out-of-nowhere fumble.")
            .defineInRange("butterfingersPassiveIntervalTicks", 100, 1, 1200);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_PASSIVE_CHANCE_PERCENT = CURSES
            .comment("Butterfingers: chance (%) per passive check of just dropping something for no reason.",
                    "Deliberately rare — the point is that it mostly gets you at the worst moment instead.")
            .defineInRange("butterfingersPassiveChancePercent", 3, 0, 100);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_ON_DAMAGE_CHANCE_PERCENT = CURSES
            .comment("Butterfingers: chance (%) of fumbling when you take a hit — much likelier than passive.")
            .defineInRange("butterfingersOnDamageChancePercent", 35, 0, 100);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_ON_SWING_CHANCE_PERCENT = CURSES
            .comment("Butterfingers: chance (%) of fumbling when you swing a tool or weapon (mining or",
                    "attacking) — the classic 'threw my pickaxe into the lava' moment.")
            .defineInRange("butterfingersOnSwingChancePercent", 15, 0, 100);

    public static final ModConfigSpec.BooleanValue BUTTERFINGERS_DROP_FROM_HOTBAR = CURSES
            .comment("Butterfingers: when your hands are empty, fumble a random hotbar item instead of doing",
                    "nothing. False means empty hands are completely safe.")
            .define("butterfingersDropFromHotbar", true);

    public static final ModConfigSpec.BooleanValue BUTTERFINGERS_DROPS_WHOLE_STACK = CURSES
            .comment("Butterfingers: drop the ENTIRE stack rather than a single item. True is far funnier and",
                    "more punishing; the items are all still on the floor to be picked back up.")
            .define("butterfingersDropsWholeStack", true);

    static { CURSES.pop(); }
    static { CURSES.push("explosive"); }
    public static final ModConfigSpec.DoubleValue EXPLOSIVE_POWER = CURSES
            .comment("Explosive: blast radius of the death explosion. TNT is 4.0, a creeper 3.0.")
            .defineInRange("explosivePower", 4.0, 0.1, 32.0);

    public static final ModConfigSpec.BooleanValue EXPLOSIVE_CREATES_FIRE = CURSES
            .comment("Explosive: whether the blast leaves fires behind, like a charged creeper in the Nether.")
            .define("explosiveCreatesFire", false);

    public static final ModConfigSpec.IntValue EXPLOSIVE_DELAY_TICKS = CURSES
            .comment("Explosive: ticks to wait after death before detonating. MUST be at least 1: the death",
                    "event fires BEFORE vanilla drops your inventory, so an instant blast would go off while",
                    "there is nothing to destroy. Waiting a tick lets the drops exist so they are caught in it.")
            .defineInRange("explosiveDelayTicks", 1, 1, 200);

    public static final ModConfigSpec.DoubleValue EXPLOSIVE_KNOCKUP = CURSES
            .comment("Explosive: extra UPWARD launch given to anything caught in the death blast, on top of the",
                    "explosion's own knockback — scaled down by distance. 0 disables the knockup.")
            .defineInRange("explosiveKnockup", 0.7, 0.0, 4.0);

    static { CURSES.pop(); }
    static { CURSES.push("loading"); }
    public static final ModConfigSpec.IntValue LOADING_SCREEN_MIN_TICKS = CURSES
            .comment("Loading Screen: shortest fake load, in ticks. 30 = 1.5 seconds.")
            .defineInRange("loadingScreenMinTicks", 30, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MAX_TICKS = CURSES
            .comment("Loading Screen: longest fake load, in ticks. 100 = 5 seconds.")
            .defineInRange("loadingScreenMaxTicks", 100, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_RESTART_CHANCE_PERCENT = CURSES
            .comment("Loading Screen: chance (%) that on reaching the end, the bar slides back down and",
                    "loads all over again — the cruellest part of the joke.")
            .defineInRange("loadingScreenRestartChancePercent", 50, 0, 100);

    public static final ModConfigSpec.DoubleValue LOADING_SCREEN_TIP_SCROLL_SPEED = CURSES
            .comment("Loading Screen: how fast the tip marquee travels, in pixels per tick. 6.0 = 120px/sec,",
                    "so a tip crosses a normal window in a few seconds — fast enough to actually read one",
                    "during a short load. Lower it and you only ever catch a fragment.")
            .defineInRange("loadingScreenTipScrollSpeed", 6.0, 0.1, 60.0);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_RESTART_MIN_TICKS = CURSES
            .comment("Loading Screen: shortest extra load after a slide-back. 20 = 1 second.")
            .defineInRange("loadingScreenRestartMinTicks", 20, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_RESTART_MAX_TICKS = CURSES
            .comment("Loading Screen: longest extra load after a slide-back. 60 = 3 seconds.")
            .defineInRange("loadingScreenRestartMaxTicks", 60, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_STUTTER_CHANCE_PERCENT = CURSES
            .comment("Loading Screen: chance (%) each second that the bar stutters — freezing in place as if",
                    "the game has hung, without extending the progress itself. At 40 most loads will hitch at",
                    "least once; loadingScreenMaxTotalTicks is what stops a bad run going on forever.")
            .defineInRange("loadingScreenStutterChancePercent", 40, 0, 100);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_STUTTER_MIN_TICKS = CURSES
            .comment("Loading Screen: shortest stutter freeze. 20 = 1 second.")
            .defineInRange("loadingScreenStutterMinTicks", 20, 1, 600);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_STUTTER_MAX_TICKS = CURSES
            .comment("Loading Screen: longest stutter freeze. 60 = 3 seconds.")
            .defineInRange("loadingScreenStutterMaxTicks", 60, 1, 600);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MAX_TOTAL_TICKS = CURSES
            .comment("Loading Screen: hard safety cap on one session, including every stutter and restart.",
                    "Input is locked for the duration, so this guarantees you can never be stuck for longer",
                    "than this no matter how the rolls land. 400 = 20 seconds.")
            .defineInRange("loadingScreenMaxTotalTicks", 400, 20, 12000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_COUNT = CURSES
            .comment("Loading Screen: number of frames in the animation sprite sheet.",
                    "The shipped chest sheet is 36 frames.")
            .defineInRange("loadingScreenFrameCount", 36, 1, 512);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_WIDTH = CURSES
            .comment("Loading Screen: width in pixels of ONE frame. Frames need not be square.")
            .defineInRange("loadingScreenFrameWidth", 128, 1, 1024);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_HEIGHT = CURSES
            .comment("Loading Screen: height in pixels of ONE frame. The shipped chest sheet is 128x152.")
            .defineInRange("loadingScreenFrameHeight", 152, 1, 1024);

    public static final ModConfigSpec.BooleanValue LOADING_SCREEN_SHEET_VERTICAL = CURSES
            .comment("Loading Screen: true if the sheet stacks frames DOWN one column (an Aseprite vertical",
                    "export, like the shipped chest), false if they run left-to-right in one row.")
            .define("loadingScreenSheetVertical", true);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_1_WEIGHT = CURSES
            .comment("Loading Screen: relative weight of hold-music track 1. The four weights are rolled",
                    "against their own total, so they need not add to anything in particular — the shipped",
                    "375/375/240/10 works out as 37.5% / 37.5% / 24% / 1%.")
            .defineInRange("loadingScreenMusic1Weight", 375, 0, 100000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_2_WEIGHT = CURSES
            .comment("Loading Screen: relative weight of hold-music track 2.")
            .defineInRange("loadingScreenMusic2Weight", 375, 0, 100000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_3_WEIGHT = CURSES
            .comment("Loading Screen: relative weight of hold-music track 3.")
            .defineInRange("loadingScreenMusic3Weight", 240, 0, 100000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_GOOFY_WEIGHT = CURSES
            .comment("Loading Screen: relative weight of the rare goofy track. 10 of 1000 = 1%.")
            .defineInRange("loadingScreenMusicGoofyWeight", 10, 0, 100000);

    public static final ModConfigSpec.DoubleValue LOADING_SCREEN_MUSIC_VOLUME = CURSES
            .comment("Loading Screen: volume of the normal hold-music tracks.")
            .defineInRange("loadingScreenMusicVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LOADING_SCREEN_GOOFY_VOLUME = CURSES
            .comment("Loading Screen: volume of the rare goofy track — deliberately quieter than the rest.")
            .defineInRange("loadingScreenGoofyVolume", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_TICKS = CURSES
            .comment("Loading Screen: ticks each animation frame is held. 2 = 10fps, so the 36-frame chest",
                    "opens and shuts once every 3.6s — about the length of a typical fake load. 1 = 20fps.")
            .defineInRange("loadingScreenFrameTicks", 2, 1, 100);

    static { CURSES.pop(); }
    static { CURSES.push("super"); }
    public static final ModConfigSpec.IntValue SUPER_EXPLOSIVE_CHANCE_PERCENT = CURSES
            .comment("Super Explosive: flat chance (%) of detonating each time you take damage. Deliberately",
                    "constant — it never ramps, so it's a standing risk rather than a building one.")
            .defineInRange("superExplosiveChancePercent", 5, 0, 100);

    public static final ModConfigSpec.DoubleValue SUPER_EXPLOSIVE_POWER = CURSES
            .comment("Super Explosive: blast radius. TNT is 4.0, a creeper 3.0.")
            .defineInRange("superExplosivePower", 2.5, 0.1, 32.0);

    public static final ModConfigSpec.IntValue SUPER_EXPLOSIVE_SELF_DAMAGE_PERCENT = CURSES
            .comment("Super Explosive: percent of the blast's damage that you take yourself. You are at the",
                    "dead centre of your own explosion, so at 100 it would simply one-shot you every time —",
                    "15 keeps it a genuine hit without being an execution. Everyone ELSE takes it in full.")
            .defineInRange("superExplosiveSelfDamagePercent", 15, 0, 100);

    public static final ModConfigSpec.DoubleValue SUPER_EXPLOSIVE_KNOCKBACK_MULTIPLIER = CURSES
            .comment("Super Explosive: knockback multiplier for the blast, applied to everyone caught in it",
                    "(you included). 1.0 is a vanilla explosion; higher sends people flying.")
            .defineInRange("superExplosiveKnockbackMultiplier", 2.5, 0.0, 20.0);

    static { CURSES.pop(); }
    static { CURSES.push("pacing"); }
    public static final ModConfigSpec.IntValue PACING_MIN_SECONDS = CURSES
            .comment("Pacing: shortest dramatic moment, in seconds.")
            .defineInRange("pacingMinSeconds", 5, 1, 120);

    public static final ModConfigSpec.IntValue PACING_MAX_SECONDS = CURSES
            .comment("Pacing: longest dramatic moment, in seconds.")
            .defineInRange("pacingMaxSeconds", 30, 1, 120);

    public static final ModConfigSpec.DoubleValue PACING_LOW_BIAS_EXPONENT = CURSES
            .comment("Pacing: how hard the random duration is biased toward the short end. The roll is",
                    "min + (max-min) * r^exp with r in [0,1); 1.0 is uniform, higher favours shorter moments.",
                    "2.0 means most moments land near 5s with the occasional long one.")
            .defineInRange("pacingLowBiasExponent", 2.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue PACING_FREEZE_RADIUS = CURSES
            .comment("Pacing: how far around the victim entities are caught in the time-stop.")
            .defineInRange("pacingFreezeRadius", 8.0, 1.0, 32.0);

    public static final ModConfigSpec.IntValue PACING_MAX_INVOLVED = CURSES
            .comment("Pacing: cap on how many nearby entities are frozen alongside the victim. This is a",
                    "SAFETY limit, not a presentation one — every frozen entity is also made invulnerable and",
                    "held in place each tick, so an uncapped sweep in a mob farm could hurt a server badly.")
            .defineInRange("pacingMaxInvolved", 12, 0, 64);

    public static final ModConfigSpec.IntValue PACING_SHOT_TICKS = CURSES
            .comment("Pacing: ticks each camera angle is held before cutting to the next. 14 ~ 0.7s.")
            .defineInRange("pacingShotTicks", 14, 2, 200);

    public static final ModConfigSpec.IntValue PACING_THE_ONE_PIECE_CHANCE = CURSES
            .comment("Pacing: 1-in-N chance that a camera-cut click is replaced by the revelation sound.")
            .defineInRange("pacingTheOnePieceChance", 250, 1, 100000);

    public static final ModConfigSpec.DoubleValue PACING_CLICK_VOLUME = CURSES
            .comment("Pacing: volume of the click between camera cuts.")
            .defineInRange("pacingClickVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue PACING_THEME_VOLUME = CURSES
            .comment("Pacing: volume of the dramatic theme.")
            .defineInRange("pacingThemeVolume", 1.0, 0.0, 1.0);

    static { CURSES.pop(); }
    static { CURSES.push("popularity"); }
    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_INTERVAL_TICKS = CURSES
            .comment("Popularity: how often (ticks) to try conjuring hostiles around the victim. 45 ~ 2.25s;",
                    "higher = the horde builds up more gradually.")
            .defineInRange("popularitySpawnIntervalTicks", 45, 1, 1200);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_ATTEMPTS = CURSES
            .comment("Popularity: spawn attempts per interval (not all find a valid spot). Lower = gentler ramp.")
            .defineInRange("popularitySpawnAttempts", 2, 1, 40);

    public static final ModConfigSpec.IntValue POPULARITY_MAX_MOBS = CURSES
            .comment("Popularity: cap on curse-spawned mobs alive around the victim, so the horde is a crowd",
                    "and not a lag machine. Spawning pauses once this many are already chasing.")
            .defineInRange("popularityMaxMobs", 34, 1, 300);

    public static final ModConfigSpec.IntValue POPULARITY_DISCOVERY_HORDE_SIZE = CURSES
            .comment("Popularity: discovered for the victim once this many curse-spawned mobs have amassed",
                    "around them — you notice the mod when the crowd is unmistakable, not on the first spawn.")
            .defineInRange("popularityDiscoveryHordeSize", 15, 1, 300);

    public static final ModConfigSpec.IntValue POPULARITY_PROLONGED_TRACKING_TICKS = CURSES
            .comment("Popularity: how long (ticks) a hostile keeps hunting you AFTER losing line of sight",
                    "before giving up — vanilla is ~60 (3s). 300 = 15s of dogged tracking through walls, but",
                    "they still have to SEE you first to lock on (no straight-up x-ray vision).")
            .defineInRange("popularityProlongedTrackingTicks", 300, 0, 6000);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_RADIUS_MIN = CURSES
            .comment("Popularity: nearest a conjured mob appears — kept off the victim so they don't pop in",
                    "your face, they come running from a little way off.")
            .defineInRange("popularitySpawnRadiusMin", 6, 1, 64);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_RADIUS_MAX = CURSES
            .comment("Popularity: furthest a conjured mob appears.")
            .defineInRange("popularitySpawnRadiusMax", 20, 2, 128);

    public static final ModConfigSpec.IntValue POPULARITY_MAX_SPAWN_LIGHT = CURSES
            .comment("Popularity: a conjured mob only appears where the light level is at or below this — the",
                    "'daylight and torches keep you safe' gate. 7 matches vanilla's dark-enough threshold, so",
                    "lit areas and daytime surfaces stay clear while caves and night fill up.")
            .defineInRange("popularityMaxSpawnLight", 7, 0, 15);

    public static final ModConfigSpec.IntValue POPULARITY_DETECTION_RADIUS = CURSES
            .comment("Popularity: any hostile within this radius locks onto the victim, walls or not — this is",
                    "the 'detection radius is bigger' half, and it drags in natural spawns too, not just ours.")
            .defineInRange("popularityDetectionRadius", 48, 1, 128);

    public static final ModConfigSpec.IntValue POPULARITY_RETARGET_INTERVAL_TICKS = CURSES
            .comment("Popularity: how often (ticks) nearby hostiles are set up as dedicated hunters (bigger",
                    "follow range, prolonged tracking, doors) and, if they can SEE you, re-aimed at you so you",
                    "stay the priority. 20 = 1s.")
            .defineInRange("popularityRetargetIntervalTicks", 20, 1, 200);

    static { CURSES.pop(); }
    static { CURSES.push("yap"); }
    public static final ModConfigSpec.IntValue YAP_INTERVAL_MIN_TICKS = CURSES
            .comment("Yap: shortest gap between outbursts, in ticks. 200 = 10s.")
            .defineInRange("yapIntervalMinTicks", 200, 20, 24000);

    public static final ModConfigSpec.IntValue YAP_INTERVAL_MAX_TICKS = CURSES
            .comment("Yap: longest gap between outbursts, in ticks. 600 = 30s.")
            .defineInRange("yapIntervalMaxTicks", 600, 20, 24000);

    public static final ModConfigSpec.IntValue YAP_SINGLE_WEIGHT = CURSES
            .comment("Yap: relative chance an outburst is a single message (most common).")
            .defineInRange("yapSingleWeight", 70, 0, 1000);

    public static final ModConfigSpec.IntValue YAP_DOUBLE_WEIGHT = CURSES
            .comment("Yap: relative chance an outburst is a scripted 2-message combo, sent one after another.")
            .defineInRange("yapDoubleWeight", 25, 0, 1000);

    public static final ModConfigSpec.IntValue YAP_TRIPLE_WEIGHT = CURSES
            .comment("Yap: relative chance an outburst is a scripted 3-message combo (rarest).")
            .defineInRange("yapTripleWeight", 5, 0, 1000);

    public static final ModConfigSpec.IntValue YAP_MESSAGE_GAP_TICKS = CURSES
            .comment("Yap: ticks between the messages of a multi-message combo, so they land in sequence",
                    "rather than all at once. 30 = 1.5s.")
            .defineInRange("yapMessageGapTicks", 30, 1, 200);

    public static final ModConfigSpec.IntValue YAP_EVENT_COOLDOWN_TICKS = CURSES
            .comment("Yap: cooldown (ticks) per EVENT reaction type — taking damage, dealing damage, opening",
                    "a chest, dying, a player being near. Much longer than the ambient chatter so a reaction",
                    "line stays a treat, not a spam. 2400 = 2 min, tracked separately per event.")
            .defineInRange("yapEventCooldownTicks", 2400, 0, 72000);

    public static final ModConfigSpec.DoubleValue YAP_PROXIMITY_RADIUS = CURSES
            .comment("Yap: how close another player must be to set off the 'someone's near' reaction.")
            .defineInRange("yapProximityRadius", 6.0, 1.0, 32.0);
    public static final ModConfigSpec.IntValue YAP_STREAMER_CHANCE = CURSES
            .comment("Yap+Chat synergy: percent chance an ambient outburst is a 'talking to chat' streamer line (from the yap.json events 'streamer' list) instead of a normal one.")
            .defineInRange("yapStreamerChancePercent", 35, 0, 100);

    static { CURSES.pop(); }
    static { CURSES.push("repel"); }
    public static final ModConfigSpec.DoubleValue REPEL_RADIUS = CURSES
            .comment("Repel: how close a dropped item or XP orb must be to the victim to start sliding away.")
            .defineInRange("repelRadius", 6.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue REPEL_SPEED = CURSES
            .comment("Repel: horizontal slide speed (blocks/tick). Kept below sprint (~0.13) so you can always",
                    "chase your stuff down — 0.084 is a steady, catchable crawl.")
            .defineInRange("repelSpeed", 0.084, 0.01, 0.13);

    public static final ModConfigSpec.DoubleValue REPEL_HAZARD_SCAN_RADIUS = CURSES
            .comment("Repel: how far from each sliding item to look for a hazard (TNT/lava/cactus/ledge/player)",
                    "to steer it toward — but only ever in a direction that still points AWAY from the victim.")
            .defineInRange("repelHazardScanRadius", 5.0, 1.0, 16.0);

    public static final ModConfigSpec.IntValue REPEL_LEDGE_DROP_MIN = CURSES
            .comment("Repel: blocks of empty air below a neighbouring column for it to count as a ledge worth",
                    "nudging an item off.")
            .defineInRange("repelLedgeDropMin", 2, 1, 32);

    public static final ModConfigSpec.IntValue REPEL_MAX_ENTITIES = CURSES
            .comment("Repel: cap on how many items/orbs are pushed per tick, so a giant pile can't lag.")
            .defineInRange("repelMaxEntities", 64, 1, 512);

    static { CURSES.pop(); }
    static { CURSES.push("farmhand"); }
    public static final ModConfigSpec.DoubleValue FARMHAND_RADIUS = CURSES
            .comment("Farmhand: how far around the victim untamed animals get recruited to crowd them — a wide",
                    "radius, so even fairly distant animals come waddling over to get in the way.")
            .defineInRange("farmhandRadius", 24.0, 4.0, 64.0);

    public static final ModConfigSpec.DoubleValue FARMHAND_NAV_SPEED = CURSES
            .comment("Farmhand: pathfinding speed multiplier for recruited animals — well above 1 so they",
                    "visibly hustle to crowd you rather than ambling over.")
            .defineInRange("farmhandNavSpeed", 1.05, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue FARMHAND_SPEED_BONUS = CURSES
            .comment("Farmhand: extra MOVEMENT_SPEED given to a hijacked animal as a fraction of its base",
                    "(1.0 = double speed). A cow at base speed simply can't keep a player hemmed in; this is",
                    "applied by the goal and handed straight back when the curse ends.")
            .defineInRange("farmhandSpeedBonus", 0.15, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue FARMHAND_SURROUND_RADIUS = CURSES
            .comment("Farmhand: how tight the ring of surrounding animals hugs the victim. Small = right in",
                    "your face.")
            .defineInRange("farmhandSurroundRadius", 1.3, 0.5, 8.0);

    public static final ModConfigSpec.IntValue FARMHAND_RECRUIT_INTERVAL_TICKS = CURSES
            .comment("Farmhand: how often (ticks) nearby animals are (re)recruited and the crowd topped up.")
            .defineInRange("farmhandRecruitIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue FARMHAND_MIN_ANIMALS = CURSES
            .comment("Farmhand: if fewer than this many animals are nearby, conjure more (given valid spawn",
                    "conditions) so you're never left in peace.")
            .defineInRange("farmhandMinAnimals", 3, 0, 64);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_COOLDOWN_TICKS = CURSES
            .comment("Farmhand: minimum ticks between top-up spawn bursts.")
            .defineInRange("farmhandSpawnCooldownTicks", 300, 20, 12000);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_ATTEMPTS = CURSES
            .comment("Farmhand: spawn attempts per top-up burst (not all find valid animal-spawn conditions).")
            .defineInRange("farmhandSpawnAttempts", 3, 1, 20);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_RADIUS_MIN = CURSES
            .comment("Farmhand: nearest a conjured animal appears.")
            .defineInRange("farmhandSpawnRadiusMin", 6, 1, 32);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_RADIUS_MAX = CURSES
            .comment("Farmhand: furthest a conjured animal appears.")
            .defineInRange("farmhandSpawnRadiusMax", 16, 2, 64);

    static { CURSES.pop(); }
    static { CURSES.push("gluttony"); }
    public static final ModConfigSpec.IntValue GLUTTONY_SPRINT_CUTOFF = CURSES
            .comment("Gluttony: you stop sprinting at or below this on the COMBINED 40-point bar — double",
                    "vanilla's threshold of 6, since the whole bar is doubled.")
            .defineInRange("gluttonySprintCutoff", 12, 0, 40);

    public static final ModConfigSpec.IntValue GLUTTONY_EAT_SPEED_PERCENT = CURSES
            .comment("Gluttony: how much FASTER food is eaten, as a percent off the normal use time. 30 = a",
                    "30% shorter animation. The one perk of the curse — you have a lot of eating to do.")
            .defineInRange("gluttonyEatSpeedPercent", 30, 0, 90);

    public static final ModConfigSpec.IntValue GLUTTONY_SATURATION_PENALTY_PERCENT = CURSES
            .comment("Gluttony: percent of saturation taken back off every food eaten, so meals don't last",
                    "and you have to keep grazing.")
            .defineInRange("gluttonySaturationPenaltyPercent", 20, 0, 100);

    public static final ModConfigSpec.DoubleValue GLUTTONY_MODEL_SCALE_BONUS = CURSES
            .comment("Gluttony: added to the SCALE attribute, so 0.35 renders the player at 1.35x — physically",
                    "wider, and it makes tight gaps a real problem.")
            .defineInRange("gluttonyModelScaleBonus", 0.35, 0.0, 2.0);

    static { CURSES.pop(); }
    static { CURSES.push("unhygienic"); }
    public static final ModConfigSpec.DoubleValue UNHYGIENIC_MOB_FLEE_RADIUS = CURSES
            .comment("Unhygienic: how far the stink reaches for NON-UNDEAD mobs — anything inside runs away.",
                    "Undead are unbothered; they smell worse.")
            .defineInRange("unhygienicMobFleeRadius", 8.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_FLEE_SPEED = CURSES
            .comment("Unhygienic: pathfinding speed multiplier for a mob fleeing the smell.")
            .defineInRange("unhygienicFleeSpeed", 1.35, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PANIC_RADIUS = CURSES
            .comment("Unhygienic: get this close to a fleeing mob and it panics — it breaks into a proper run",
                    "instead of an unhurried walk away.")
            .defineInRange("unhygienicPanicRadius", 3.5, 0.5, 32.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PANIC_SPEED = CURSES
            .comment("Unhygienic: pathfinding speed multiplier while panicking (inside unhygienicPanicRadius).")
            .defineInRange("unhygienicPanicSpeed", 2.0, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PLAYER_DRIFT_RADIUS = CURSES
            .comment("Unhygienic: how close another PLAYER has to get before they start drifting away.")
            .defineInRange("unhygienicPlayerDriftRadius", 3.0, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PLAYER_DRIFT_FORCE = CURSES
            .comment("Unhygienic: how hard nearby players are nudged away per push. Deliberately subtle — a",
                    "slide they can walk against, not a shove.")
            .defineInRange("unhygienicPlayerDriftForce", 0.04, 0.0, 0.5);

    public static final ModConfigSpec.IntValue UNHYGIENIC_FLY_INTERVAL = CURSES
            .comment("Unhygienic: ticks between fly specks. Spawning one every tick threw out ~80 particles a",
                    "second and looked like flung dust; a slower rate along a smooth path reads as an actual",
                    "fly and costs a fraction as much.")
            .defineInRange("unhygienicFlyInterval", 3, 1, 40);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_FLY_VOLUME = CURSES
            .comment("Unhygienic: volume of the ambient fly buzz.")
            .defineInRange("unhygienicFlyVolume", 0.56, 0.0, 1.0);

    public static final ModConfigSpec.IntValue UNHYGIENIC_PARTICLE_INTERVAL = CURSES
            .comment("Unhygienic: ticks between puffs of the green stink cloud.")
            .defineInRange("unhygienicParticleInterval", 10, 1, 200);

    public static final ModConfigSpec.IntValue UNHYGIENIC_FLY_SOUND_MIN_TICKS = CURSES
            .comment("Unhygienic: shortest gap between ambient fly buzzes. 120 = 6s.")
            .defineInRange("unhygienicFlySoundMinTicks", 120, 20, 6000);

    public static final ModConfigSpec.IntValue UNHYGIENIC_FLY_SOUND_MAX_TICKS = CURSES
            .comment("Unhygienic: longest gap between ambient fly buzzes. 400 = 20s.")
            .defineInRange("unhygienicFlySoundMaxTicks", 400, 20, 6000);

    static { CURSES.pop(); }
    static { CURSES.push("echoes"); }
    public static final ModConfigSpec.IntValue ECHOES_INTERVAL_MIN_TICKS = CURSES
            .comment("Echoes: shortest gap between hallucinations. 160 = 8s.")
            .defineInRange("echoesIntervalMinTicks", 160, 20, 24000);

    public static final ModConfigSpec.IntValue ECHOES_INTERVAL_MAX_TICKS = CURSES
            .comment("Echoes: longest gap between hallucinations. 1300 = 65s. The wide spread is the point —",
                    "a predictable rhythm would tell the victim instantly which sounds were fake.")
            .defineInRange("echoesIntervalMaxTicks", 1300, 20, 24000);

    public static final ModConfigSpec.IntValue ECHOES_DISCOVERY_DELAY_TICKS = CURSES
            .comment("Echoes: ticks after the FIRST hallucination before the victim is told what's happening,",
                    "so the penny drops a moment later rather than the alert spoiling the sound. 40 = 2s.")
            .defineInRange("echoesDiscoveryDelayTicks", 40, 0, 600);

    public static final ModConfigSpec.DoubleValue ECHOES_VOLUME = CURSES
            .comment("Echoes: master volume for hallucinated sounds. 1.0 keeps them indistinguishable from",
                    "real ones, which is the whole point — turn it down only if they're overbearing.")
            .defineInRange("echoesVolume", 1.0, 0.0, 1.0);

    static { CURSES.pop(); }
    static { CURSES.push("bad"); }
    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_STEP_BONUS = CURSES
            .comment("Bad Swimmer: extra step height while in liquid, on top of vanilla's 0.6. 0.5 takes it to",
                    "1.1, so a single block can be WALKED up on the bottom. This is the lever rather than a",
                    "weaker pull because underwater there is no impulse jump to boost — past the fluid-jump",
                    "threshold vanilla routes jumping to a gentle sustained thrust, so the only way to clear a",
                    "block by rising is to swim up, which is the one thing this curse exists to forbid.")
            .defineInRange("badSwimmerStepBonus", 0.5, 0.0, 2.0);

    // --- Slippery Feet ---------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("slippery"); }
    public static final ModConfigSpec.IntValue SLIPPERY_CHECK_INTERVAL = CURSES
            .comment("Slippery Feet: how often the standing-near-a-ledge roll is made, in ticks.")
            .defineInRange("slipperyCheckIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue SLIPPERY_STANDING_CHANCE = CURSES
            .comment("Slippery Feet: percent chance per check of slipping while merely STANDING near a ledge",
                    "or hazard. Deliberately very low — standing near an edge should be a background dread,",
                    "not a coin flip. The crouch case is where the curse actually bites.")
            .defineInRange("slipperyStandingChancePercent", 2, 0, 100);

    public static final ModConfigSpec.IntValue SLIPPERY_CROUCH_MIN_TICKS = CURSES
            .comment("Slippery Feet: shortest time crouched over a ledge before you are GUARANTEED to go off",
                    "it. 20 = 1s.")
            .defineInRange("slipperyCrouchMinTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue SLIPPERY_CROUCH_MAX_TICKS = CURSES
            .comment("Slippery Feet: longest that grace period lasts. 40 = 2s. The actual value is re-rolled",
                    "between min and max each time you settle over an edge, so it can't be counted out.")
            .defineInRange("slipperyCrouchMaxTicks", 40, 1, 200);

    public static final ModConfigSpec.IntValue SLIPPERY_LEDGE_DROP_MIN = CURSES
            .comment("Slippery Feet: how many clear blocks below a neighbouring column make it a ledge.")
            .defineInRange("slipperyLedgeDropMin", 2, 1, 32);

    public static final ModConfigSpec.DoubleValue SLIPPERY_PUSH_FORCE = CURSES
            .comment("Slippery Feet: how hard you are shoved off, in blocks/tick.")
            .defineInRange("slipperyPushForce", 0.35, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue SLIPPERY_PUSH_LIFT = CURSES
            .comment("Slippery Feet: small upward kick on the shove, so you clear the lip of the block rather",
                    "than scraping down its face.")
            .defineInRange("slipperyPushLift", 0.18, 0.0, 2.0);

    // --- Sticky ----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("sticky"); }
    public static final ModConfigSpec.BooleanValue STICKY_BLOCKS_DROPPING = CURSES
            .comment("Sticky: whether items refuse to be dropped (Q, and dragging out of the inventory).")
            .define("stickyBlocksDropping", true);

    public static final ModConfigSpec.BooleanValue STICKY_BLOCKS_ARMOUR = CURSES
            .comment("Sticky: whether armour refuses to come off. Armour that BREAKS is still lost — the",
                    "restore only fires when the piece can actually be found after removal, so this can't",
                    "become an infinite-durability exploit.")
            .define("stickyBlocksArmourRemoval", true);

    // --- Glass Cannon ----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("glass"); }
    public static final ModConfigSpec.DoubleValue GLASS_CANNON_DAMAGE_TAKEN_MULT = CURSES
            .comment("Glass Cannon: multiplier on ALL incoming damage. 2.0 = you take 200%.")
            .defineInRange("glassCannonDamageTakenMultiplier", 2.0, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue GLASS_CANNON_DAMAGE_DEALT_MULT = CURSES
            .comment("Glass Cannon: multiplier on MELEE damage you deal (direct hits only, not projectiles).",
                    "1.5 = you deal 150%.")
            .defineInRange("glassCannonDamageDealtMultiplier", 1.5, 1.0, 10.0);

    // --- Giant -----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("giant"); }
    public static final ModConfigSpec.DoubleValue GIANT_SCALE = CURSES
            .comment("Giant: SCALE multiplier (model AND hitbox). 3.0 = three times your size.")
            .defineInRange("giantScale", 3.0, 1.1, 10.0);
    public static final ModConfigSpec.DoubleValue GIANT_SPEED_MULT = CURSES
            .comment("Giant: MOVEMENT_SPEED multiplier. 0.9 = 10% slower, a lumbering giant.")
            .defineInRange("giantSpeedMultiplier", 0.9, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue GIANT_ATTACK_SPEED_MULT = CURSES
            .comment("Giant: ATTACK_SPEED multiplier — a longer swing cooldown. 0.7 = a 30% slower swing.")
            .defineInRange("giantAttackSpeedMultiplier", 0.7, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue GIANT_REACH_MULT = CURSES
            .comment("Giant: ENTITY_INTERACTION_RANGE multiplier — the giant's reach. 2.0 = double, so it can hit things on the floor at its feet.")
            .defineInRange("giantReachMultiplier", 2.0, 1.0, 5.0);
    public static final ModConfigSpec.DoubleValue GIANT_DAMAGE_TAKEN_MULT = CURSES
            .comment("Giant: multiplier on ALL incoming damage. 0.25 = you take 75% less.")
            .defineInRange("giantDamageTakenMultiplier", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GIANT_MELEE_DEALT_MULT = CURSES
            .comment("Giant: multiplier on MELEE damage you deal. 1.8 = you deal 80% more.")
            .defineInRange("giantMeleeDealtMultiplier", 1.8, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue GIANT_STOMP_DAMAGE = CURSES
            .comment("Giant: damage dealt to anything the giant physically stands on (before the melee multiplier).")
            .defineInRange("giantStompDamage", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue GIANT_STOMP_KNOCKBACK = CURSES
            .comment("Giant: how hard a stomped entity is launched clear (velocity units).")
            .defineInRange("giantStompKnockback", 1.4, 0.0, 5.0);
    public static final ModConfigSpec.IntValue GIANT_STOMP_INTERVAL_TICKS = CURSES
            .comment("Giant: minimum ticks between stomps on the SAME entity (they're launched clear, so this is a safety cap).")
            .defineInRange("giantStompIntervalTicks", 10, 1, 200);
    public static final ModConfigSpec.DoubleValue GIANT_HIT_KNOCKBACK = CURSES
            .comment("Giant: EXTRA knockback strength applied to a base melee hit (on top of vanilla's), so the giant's hits fling things.")
            .defineInRange("giantHitKnockback", 1.0, 0.0, 5.0);

    // --- New QoL blessings: Speed / Forgiveness / Drive -----------------------------------------------
    static { BLESSINGS.comment("witchmod — blessings.toml: per-blessing balance knobs, one [section] per blessing.").push("speed"); }
    public static final ModConfigSpec.DoubleValue SPEED_SPRINT_BONUS = BLESSINGS
            .comment("Blessing of Speed: MOVEMENT_SPEED bonus applied ONLY while sprinting (its own modifier, stacks with Speed/Ninja). 0.6 = ~60% faster sprint.")
            .defineInRange("speedSprintBonus", 0.6, 0.0, 3.0);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("forgiveness"); }
    public static final ModConfigSpec.DoubleValue FORGIVENESS_HITBOX_INFLATE = BLESSINGS
            .comment("Forgiveness: how many blocks bigger (each side) an entity's hitbox is FOR YOU — melee near-misses connect and your projectiles curve onto it. ~0.4 ≈ 40% bigger on a typical mob; a flat floor helps tiny/baby mobs.")
            .defineInRange("forgivenessHitboxInflate", 0.4, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue FORGIVENESS_PROJECTILE_STEER = BLESSINGS
            .comment("Forgiveness: how hard YOUR projectile curves onto an entity whose enlarged box it was about to pass through (0..1). Higher = it connects more reliably.")
            .defineInRange("forgivenessProjectileSteer", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FORGIVENESS_PROJECTILE_LOOKAHEAD_TICKS = BLESSINGS
            .comment("Forgiveness: how many ticks of travel ahead the projectile scans for an about-to-pass entity.",
                    "Scaled by the shot's speed, so FAST arrows (≈3 blocks/tick) are actually caught, not just slow lobs.")
            .defineInRange("forgivenessProjectileLookaheadTicks", 4.0, 1.0, 20.0);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("drive"); }
    public static final ModConfigSpec.DoubleValue DRIVE_RADIUS = BLESSINGS
            .comment("Drive: radius (blocks) within which nearby animals have their breeding cooldown cleared each tick.")
            .defineInRange("driveRadius", 16.0, 2.0, 64.0);
    public static final ModConfigSpec.IntValue DRIVE_AUTO_BREED_IDLE_TICKS = BLESSINGS
            .comment("Drive: after this long without the blessed player breeding an animal themselves, nearby pairs start breeding on their own.")
            .defineInRange("driveAutoBreedIdleTicks", 600, 0, 24000);
    public static final ModConfigSpec.IntValue DRIVE_AUTO_BREED_INTERVAL_TICKS = BLESSINGS
            .comment("Drive: how often a spontaneous auto-breed is attempted (once the idle window has passed).")
            .defineInRange("driveAutoBreedIntervalTicks", 100, 20, 2400);
    public static final ModConfigSpec.IntValue DRIVE_AUTO_BREED_CHANCE_PERCENT = BLESSINGS
            .comment("Drive: chance per attempt that a nearby same-type pair breeds with no food. With the Farmhand synergy the idle gate is ignored and babies join the nuisance.")
            .defineInRange("driveAutoBreedChancePercent", 60, 0, 100);

    // --- More QoL blessings: Vein Miner / Collector / Restock / Sanguine / Homebody / Sonar -----------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("vein"); }
    public static final ModConfigSpec.IntValue VEIN_MINER_MAX = BLESSINGS
            .comment("Vein Miner: max blocks felled in one break (the vein/tree cap, for safety/lag).")
            .defineInRange("veinMinerMaxBlocks", 64, 1, 512);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("collector"); }
    public static final ModConfigSpec.DoubleValue COLLECTOR_ITEM_RADIUS = BLESSINGS
            .comment("Collector: radius (blocks) within which dropped items are drawn to you.")
            .defineInRange("collectorItemRadius", 12.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue COLLECTOR_ITEM_SPEED = BLESSINGS
            .comment("Collector: pull speed (blocks/tick) for dropped items.")
            .defineInRange("collectorItemSpeed", 0.45, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue COLLECTOR_XP_RADIUS = BLESSINGS
            .comment("Collector: HUGE radius (blocks) within which XP orbs are dragged to you.")
            .defineInRange("collectorXpRadius", 48.0, 1.0, 128.0);
    public static final ModConfigSpec.DoubleValue COLLECTOR_XP_SPEED = BLESSINGS
            .comment("Collector: HUGE pull speed (blocks/tick) for XP orbs — the magnet on steroids.")
            .defineInRange("collectorXpSpeed", 1.6, 0.1, 6.0);
    public static final ModConfigSpec.IntValue COLLECTOR_GRACE_TICKS = BLESSINGS
            .comment("Collector: how long (ticks) a dropped item must sit before it starts drifting to you — a grace period so fresh drops don't insta-float.")
            .defineInRange("collectorGraceTicks", 60, 0, 600);
    public static final ModConfigSpec.DoubleValue COLLECTOR_CROUCH_RADIUS_MULT = BLESSINGS
            .comment("Collector: while CROUCHING, the collection radius is multiplied by this (0.15 = greatly reduced, so you can crouch to grab specific drops).")
            .defineInRange("collectorCrouchRadiusMultiplier", 0.15, 0.0, 1.0);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("sanguine"); }
    public static final ModConfigSpec.DoubleValue SANGUINE_REGEN_MULT = BLESSINGS
            .comment("Sanguine: multiplier on your NATURAL regen. 0.2 = you heal at 20% of normal by resting.")
            .defineInRange("sanguineRegenMultiplier", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SANGUINE_LIFESTEAL = BLESSINGS
            .comment("Sanguine: fraction of damage you deal (melee AND projectile) healed back to you. 0.3 = 30% lifesteal.")
            .defineInRange("sanguineLifesteal", 0.3, 0.0, 2.0);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("homebody"); }
    public static final ModConfigSpec.DoubleValue HOMEBODY_RADIUS = BLESSINGS
            .comment("Homebody: how near your spawn point (blocks) you must be to get the regen + haste comfort.")
            .defineInRange("homebodyRadius", 24.0, 2.0, 128.0);
    public static final ModConfigSpec.IntValue HOMEBODY_REGEN_AMPLIFIER = BLESSINGS
            .comment("Homebody: Regeneration amplifier granted near home (0 = Regen I).")
            .defineInRange("homebodyRegenAmplifier", 0, 0, 4);
    public static final ModConfigSpec.IntValue HOMEBODY_HASTE_AMPLIFIER = BLESSINGS
            .comment("Homebody: Haste amplifier granted near home (0 = Haste I).")
            .defineInRange("homebodyHasteAmplifier", 0, 0, 4);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("sonar"); }
    public static final ModConfigSpec.IntValue SONAR_INTERVAL_TICKS = BLESSINGS
            .comment("Sonar: ticks between pings (2800 = 140s).")
            .defineInRange("sonarIntervalTicks", 2800, 20, 24000);
    public static final ModConfigSpec.IntValue SONAR_BUILDUP_TICKS = BLESSINGS
            .comment("Sonar: the CHARGE/buildup (ticks) before a pulse fires — a telegraphed swell of sound + light. 50 = 2.5s.")
            .defineInRange("sonarBuildupTicks", 50, 0, 400);
    public static final ModConfigSpec.DoubleValue SONAR_RADIUS = BLESSINGS
            .comment("Sonar: detection radius (blocks) of a ping.")
            .defineInRange("sonarRadius", 70.0, 4.0, 256.0);
    public static final ModConfigSpec.IntValue SONAR_GLOW_TICKS = BLESSINGS
            .comment("Sonar: how long (ticks) detected entities glow after a pulse. 50 = 2.5s.")
            .defineInRange("sonarGlowTicks", 50, 5, 200);
    public static final ModConfigSpec.DoubleValue SONAR_CROUCH_RADIUS_MULT = BLESSINGS
            .comment("Sonar: a CROUCHED player's effective detection radius multiplier (0.6 = 40% smaller, so crouching hides you better).")
            .defineInRange("sonarCrouchRadiusMultiplier", 0.6, 0.1, 1.0);
    public static final ModConfigSpec.IntValue SONAR_DAMAGE_REDUCTION_TICKS = BLESSINGS
            .comment("Sonar: how many ticks are shaved off the next ping each time you take damage. 20 = 1s.")
            .defineInRange("sonarDamageReductionTicks", 20, 0, 600);

    // --- Heavy Handed ----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("heavy"); }
    public static final ModConfigSpec.DoubleValue HEAVY_HANDED_DURABILITY_MULT = CURSES
            .comment("Heavy Handed: durability-loss multiplier on your tools and armour. 4.0 = they wear four",
                    "times as fast. Applied as EXTRA damage on top of the normal loss, so Unbreaking still",
                    "mitigates it — you're clumsy, not exempt from enchantments.")
            .defineInRange("heavyHandedDurabilityMultiplier", 4.0, 1.0, 20.0);

    // --- Trumpet ---------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("trumpet"); }
    public static final ModConfigSpec.DoubleValue TRUMPET_VOLUME = CURSES
            .comment("Trumpet: loop volume. Doubles as the audible RANGE — with linear attenuation a mono",
                    "sound carries roughly volume*16 blocks, so 1.0 ~ 16 blocks. (Needs a MONO ogg to",
                    "position/attenuate at all — a stereo file plays globally at constant volume.)")
            .defineInRange("trumpetVolume", 1.0, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue TRUMPET_SPRINT_PITCH = CURSES
            .comment("Trumpet: playback pitch (= speed) while SPRINTING. 1.0 = normal; 1.15 is a slight,",
                    "comedic speed-up. Clamped by the engine to [0.5, 2.0].")
            .defineInRange("trumpetSprintPitch", 1.15, 0.5, 2.0);

    public static final ModConfigSpec.DoubleValue TRUMPET_WALK_THRESHOLD = CURSES
            .comment("Trumpet: how much walk-animation speed counts as 'moving' before the music kicks in.",
                    "Small enough to catch a walk, large enough to ignore idle jitter.")
            .defineInRange("trumpetWalkThreshold", 0.03, 0.0, 1.0);

    // --- Fortune (blessing) ----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("fortune"); }
    public static final ModConfigSpec.IntValue FORTUNE_EXTRA_MIN = BLESSINGS
            .comment("Fortune: fewest EXTRA drops an ore can give. Additive on top of enchantment Fortune, not",
                    "multiplicative — so it stacks but doesn't explode. Ore-tag blocks only.")
            .defineInRange("fortuneExtraMin", 0, 0, 64);

    public static final ModConfigSpec.IntValue FORTUNE_EXTRA_MODE = BLESSINGS
            .comment("Fortune: the MOST LIKELY number of extra drops — the peak of the distribution (a",
                    "triangular roll between min and max). 1 = usually one bonus, occasionally more or none.")
            .defineInRange("fortuneExtraMode", 1, 0, 64);

    public static final ModConfigSpec.IntValue FORTUNE_EXTRA_MAX = BLESSINGS
            .comment("Fortune: the most extra drops possible from one ore.")
            .defineInRange("fortuneExtraMax", 3, 0, 64);

    // --- Windfall (blessing) ---------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("windfall"); }
    public static final ModConfigSpec.IntValue WINDFALL_INTERVAL_MIN = BLESSINGS
            .comment("Windfall: shortest gap between items drifting by on the wind, in ticks. 340 = 17s.")
            .defineInRange("windfallIntervalMinTicks", 340, 20, 72000);

    public static final ModConfigSpec.IntValue WINDFALL_INTERVAL_MAX = BLESSINGS
            .comment("Windfall: longest gap between drifts, in ticks. 3000 = 2.5min.")
            .defineInRange("windfallIntervalMaxTicks", 3000, 20, 72000);

    public static final ModConfigSpec.DoubleValue WINDFALL_BENEFICIAL_CHANCE = BLESSINGS
            .comment("Windfall: chance (0..1) a drift is something GOOD rather than junk. 'Mostly beneficial'.")
            .defineInRange("windfallBeneficialChance", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.IntValue WINDFALL_ITEM_LIFESPAN = BLESSINGS
            .comment("Windfall: ticks a drifted item survives before it is instantly removed if not grabbed. 300 = 15s.")
            .defineInRange("windfallItemLifespanTicks", 300, 20, 6000);

    public static final ModConfigSpec.DoubleValue WINDFALL_WALK_AWAY_DISTANCE = BLESSINGS
            .comment("Windfall: if you get further than this (blocks) from a drifting item, it is instantly removed.")
            .defineInRange("windfallWalkAwayDistance", 12.0, 2.0, 64.0);

    // --- Immortality (blessing) ------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("immortality"); }
    public static final ModConfigSpec.IntValue IMMORTALITY_RECOVERY_BASE = BLESSINGS
            .comment("Immortality: recovery (rebuild) time for your FIRST death, in ticks. 160 = 8s.")
            .defineInRange("immortalityRecoveryBaseTicks", 160, 20, 12000);

    public static final ModConfigSpec.IntValue IMMORTALITY_RECOVERY_INCREMENT = BLESSINGS
            .comment("Immortality: each further death ADDS this many ticks to the recovery. 200 = +10s (=> 8s, 18s, 28s).")
            .defineInRange("immortalityRecoveryIncrementTicks", 200, 0, 12000);

    public static final ModConfigSpec.IntValue IMMORTALITY_MAX_USES = BLESSINGS
            .comment("Immortality: how many deaths it saves you from before the blessing breaks. Default 3.")
            .defineInRange("immortalityMaxUses", 3, 1, 20);
    public static final ModConfigSpec.IntValue IMMORTALITY_SHINE_SOUND_INTERVAL = BLESSINGS
            .comment("Immortality: how often (ticks) a soft crystalline 'shine' chime plays while you rebuild —",
                    "its pitch rises as you knit back together. 0 = silent rebuild.")
            .defineInRange("immortalityShineSoundIntervalTicks", 8, 0, 200);
    public static final ModConfigSpec.DoubleValue IMMORTALITY_SHINE_VOLUME = BLESSINGS
            .comment("Immortality: volume of the rebuild shine chime.")
            .defineInRange("immortalityShineVolume", 0.5, 0.0, 1.0);

    // --- Jesus (blessing) ------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("jesus"); }
    public static final ModConfigSpec.DoubleValue JESUS_DEPTH_STRIDER_SPEED_PER_LEVEL = BLESSINGS
            .comment("Jesus: while walking on the water SURFACE, Depth Strider on your boots instead speeds you up —",
                    "this fractional MOVEMENT_SPEED bonus per Depth Strider level (0.2 = +20% per level, +60% at III).")
            .defineInRange("jesusDepthStriderSpeedPerLevel", 0.2, 0.0, 2.0);

    // --- Sixth Sense (blessing) ------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("sixthsense"); }
    public static final ModConfigSpec.IntValue SIXTHSENSE_INTERVAL_MIN = BLESSINGS
            .comment("Sixth Sense: shortest gap between ordinary action-bar hints, in ticks. 600 = 30s.")
            .defineInRange("sixthSenseIntervalMinTicks", 600, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_INTERVAL_MAX = BLESSINGS
            .comment("Sixth Sense: longest gap between ordinary hints, in ticks. 1400 = 70s.")
            .defineInRange("sixthSenseIntervalMaxTicks", 1400, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_RARE_COOLDOWN_MIN = BLESSINGS
            .comment("Sixth Sense: shortest gap AFTER announcing a rare structure (they're higher-value), in ticks. 1800 = 90s.")
            .defineInRange("sixthSenseRareCooldownMinTicks", 1800, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_RARE_COOLDOWN_MAX = BLESSINGS
            .comment("Sixth Sense: longest gap after a rare-structure hint, in ticks. 3000 = 150s.")
            .defineInRange("sixthSenseRareCooldownMaxTicks", 3000, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_RARE_RADIUS_CHUNKS = BLESSINGS
            .comment("Sixth Sense: how far out (CHUNKS) to sense RARE structures (ancient city, end city, fortress...). 12 = 192 blocks.")
            .defineInRange("sixthSenseRareRadiusChunks", 12, 1, 64);

    public static final ModConfigSpec.IntValue SIXTHSENSE_STRUCTURE_RADIUS_CHUNKS = BLESSINGS
            .comment("Sixth Sense: how far out (in CHUNKS) to sense ordinary structures. 8 chunks = 128 blocks.")
            .defineInRange("sixthSenseStructureRadiusChunks", 8, 1, 64);

    public static final ModConfigSpec.DoubleValue SIXTHSENSE_PLAYER_RANGE = BLESSINGS
            .comment("Sixth Sense: how far out (blocks) to sense other players.")
            .defineInRange("sixthSensePlayerRange", 64.0, 8.0, 512.0);

    public static final ModConfigSpec.IntValue SIXTHSENSE_BIOME_RADIUS = BLESSINGS
            .comment("Sixth Sense: how far out (blocks) to sense a rare biome.")
            .defineInRange("sixthSenseBiomeRadius", 2048, 128, 8192);

    // --- Iron Stomach (blessing) -----------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("ironstomach"); }
    public static final ModConfigSpec.DoubleValue IRONSTOMACH_HUNGER_MULT = BLESSINGS
            .comment("Iron Stomach: hunger gain from a BAD food is multiplied by this (the extra is added on top). 3.0 = +200%.")
            .defineInRange("ironStomachHungerMultiplier", 3.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue IRONSTOMACH_SATURATION_MULT = BLESSINGS
            .comment("Iron Stomach: saturation gain from a BAD food is multiplied by this. 1.5 = +50%.")
            .defineInRange("ironStomachSaturationMultiplier", 1.5, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue IRONSTOMACH_EXTRA_SATURATION = BLESSINGS
            .comment("Iron Stomach: saturation modifier for the EXTRA edibles (glistering melon, sugar, cane, etc.) —",
                    "the granted saturation is their hunger value times this. 0.1 = light, doesn't stick long.")
            .defineInRange("ironStomachExtraSaturation", 0.1, 0.0, 2.0);
    public static final ModConfigSpec.IntValue IRONSTOMACH_MELON_REGEN_SECONDS = BLESSINGS
            .comment("Iron Stomach: seconds of Regeneration a glistering melon slice grants when eaten. 3 seconds.")
            .defineInRange("ironStomachMelonRegenSeconds", 3, 0, 60);
    public static final ModConfigSpec.IntValue IRONSTOMACH_MELON_REGEN_LEVEL = BLESSINGS
            .comment("Iron Stomach: Regeneration level a glistering melon slice grants (5 = Regeneration V).")
            .defineInRange("ironStomachMelonRegenLevel", 5, 1, 10);

    // --- Dexterous (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("dexterous"); }
    public static final ModConfigSpec.DoubleValue DEXTEROUS_SPREAD_RETAIN = BLESSINGS
            .comment("Dexterous: fraction of a shot's NATURAL spread to keep (0 = dead on the crosshair, 1 = vanilla).",
                    "Compresses each shot toward your aim: single shots become very accurate, and multishot's",
                    "±10° fan is squeezed to a tight-but-visible spread that can still all hit one target. 0.2 = keep 20%.")
            .defineInRange("dexterousSpreadRetain", 0.2, 0.0, 1.0);

    public static final ModConfigSpec.IntValue DEXTEROUS_CHARGE_SPEEDUP_TICKS = BLESSINGS
            .comment("Dexterous: extra charge ticks credited each use tick while drawing a bow/loading a crossbow.",
                    "1 = roughly double charge speed.")
            .defineInRange("dexterousChargeSpeedupTicks", 1, 0, 10);

    public static final ModConfigSpec.IntValue DEXTEROUS_SHIELD_DELAY_TICKS = BLESSINGS
            .comment("Dexterous: ticks a shield takes to raise into a block (vanilla is 5). Lower = faster guard.")
            .defineInRange("dexterousShieldDelayTicks", 2, 0, 5);

    // --- Hawk Guy (blessing) ---------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("hawkguy"); }
    public static final ModConfigSpec.DoubleValue HAWKGUY_HOMING_STRENGTH = BLESSINGS
            .comment("Hawk Guy: how hard your projectiles bend toward the marked target each tick while airborne (0..1)",
                    "at CLOSE range (<= hawkGuyHomingNearDistance). 0.3 is a strong, obvious homing arc up close.")
            .defineInRange("hawkGuyHomingStrength", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue HAWKGUY_HOMING_STRENGTH_FAR = BLESSINGS
            .comment("Hawk Guy: homing strength at/beyond hawkGuyHomingFarDistance — much more blatant, so a long-range",
                    "shot curves hard onto the target even when it left your bow well off-line. Lerped from the close value.")
            .defineInRange("hawkGuyHomingStrengthFar", 0.7, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue HAWKGUY_HOMING_NEAR_DISTANCE = BLESSINGS
            .comment("Hawk Guy: at/under this distance (blocks) from the target the CLOSE strength applies (kept gentle).")
            .defineInRange("hawkGuyHomingNearDistance", 10.0, 0.0, 128.0);
    public static final ModConfigSpec.DoubleValue HAWKGUY_HOMING_FAR_DISTANCE = BLESSINGS
            .comment("Hawk Guy: at/over this distance (blocks) the FAR strength applies; between the two it lerps.")
            .defineInRange("hawkGuyHomingFarDistance", 32.0, 1.0, 256.0);

    public static final ModConfigSpec.DoubleValue HAWKGUY_ACQUIRE_CONE = BLESSINGS
            .comment("Hawk Guy: half-angle (degrees) of the cone around your aim used to pick the intended target on release.",
                    "Deliberately wide/forgiving so a target well off the crosshair is still grabbed and homed onto.")
            .defineInRange("hawkGuyAcquireConeDegrees", 45.0, 1.0, 90.0);

    public static final ModConfigSpec.DoubleValue HAWKGUY_ACQUIRE_RANGE = BLESSINGS
            .comment("Hawk Guy: how far (blocks) the acquisition raycast/cone looks for the intended target. Generous so",
                    "distant targets get locked (the far-range homing then curves the shot in).")
            .defineInRange("hawkGuyAcquireRange", 40.0, 4.0, 128.0);

    public static final ModConfigSpec.DoubleValue HAWKGUY_MAX_HOMING_RANGE = BLESSINGS
            .comment("Hawk Guy: if the projectile ends up further than this (blocks) from its target, it stops homing.")
            .defineInRange("hawkGuyMaxHomingRange", 64.0, 8.0, 256.0);

    // --- Personal Trainer / Studious (blessings) -------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("trainer"); }
    public static final ModConfigSpec.DoubleValue TRAINER_VILLAGER_XP_MULT = BLESSINGS
            .comment("Personal Trainer: multiplier on the XP a villager gains from YOUR trades. Cranked high for",
                    "now so the level-up is blatant/visible (villager XP is otherwise invisible); tune back toward",
                    "~3x for release balance.")
            .defineInRange("trainerVillagerXpMultiplier", 25.0, 1.0, 100.0);
    public static final ModConfigSpec.DoubleValue TRAINER_NEARBY_RADIUS = BLESSINGS
            .comment("Personal Trainer: radius (blocks) around a trade in which OTHER villagers also get coached.")
            .defineInRange("trainerNearbyRadius", 8.0, 0.0, 32.0);
    public static final ModConfigSpec.IntValue TRAINER_LEVELUP_DELAY_TICKS = BLESSINGS
            .comment("Personal Trainer: ticks before a coached villager levels up (vanilla is 40). Small = near-instant.")
            .defineInRange("trainerLevelUpDelayTicks", 1, 1, 40);

    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("studious"); }
    public static final ModConfigSpec.DoubleValue STUDIOUS_XP_MULT = BLESSINGS
            .comment("Studious: multiplier on all XP you take in. 2.5 = 2.5x.")
            .defineInRange("studiousXpMultiplier", 2.5, 1.0, 16.0);

    // --- Twist of Fate (blessing) ----------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("twist"); }
    public static final ModConfigSpec.DoubleValue TWIST_NEGATE_CHANCE = BLESSINGS
            .comment("Twist of Fate: BASE chance (0..1) an incoming hit is negated (when off cooldown). 0.12 = 12%.")
            .defineInRange("twistNegateChance", 0.12, 0.0, 1.0);

    public static final ModConfigSpec.IntValue TWIST_COOLDOWN_TICKS = BLESSINGS
            .comment("Twist of Fate: brief cooldown after a dodge before it can trigger again, in ticks. 60 = 3s.")
            .defineInRange("twistCooldownTicks", 60, 0, 12000);
    public static final ModConfigSpec.DoubleValue TWIST_PITY_INCREMENT = BLESSINGS
            .comment("Twist of Fate: how much the dodge chance climbs per hit that DIDN'T dodge (bad-luck protection);",
                    "resets to 0 on a successful dodge.")
            .defineInRange("twistPityIncrement", 0.06, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue TWIST_MAX_CHANCE = BLESSINGS
            .comment("Twist of Fate: ceiling the base+pity dodge chance can reach.")
            .defineInRange("twistMaxChance", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.IntValue TWIST_INVULN_TICKS = BLESSINGS
            .comment("Twist of Fate: brief invulnerability granted right after a dodge, in ticks. 15 = 0.75s.")
            .defineInRange("twistInvulnTicks", 15, 0, 100);

    /** Length of the on-screen revive "totem" flash (Last Stand / Immortality), in ticks. Common constant so
     * both the server-side effects and the client overlay agree without the effects importing client code. */
    public static final int REVIVE_FLASH_TICKS = 28;

    // --- Main Character (blessing) ---------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("mainchar"); }
    public static final ModConfigSpec.DoubleValue MAINCHAR_RADIUS = BLESSINGS
            .comment("Main Character: radius (blocks) it counts nearby hostiles / players in to decide if you're 'surrounded'.")
            .defineInRange("mainCharRadius", 12.0, 4.0, 48.0);

    public static final ModConfigSpec.IntValue MAINCHAR_HOSTILE_THRESHOLD = BLESSINGS
            .comment("Main Character: hostile mobs nearby to activate (tier 1). Tier 2 is DOUBLE this.")
            .defineInRange("mainCharHostileThreshold", 3, 1, 64);

    public static final ModConfigSpec.IntValue MAINCHAR_PLAYER_THRESHOLD = BLESSINGS
            .comment("Main Character: nearby players to activate (tier 1). Tier 2 is DOUBLE this.")
            .defineInRange("mainCharPlayerThreshold", 2, 1, 32);

    public static final ModConfigSpec.IntValue MAINCHAR_STICKY_TICKS = BLESSINGS
            .comment("Main Character: how long the buffs + music linger after you stop meeting the conditions, in ticks. 80 = 4s.")
            .defineInRange("mainCharStickyTicks", 80, 0, 600);

    public static final ModConfigSpec.DoubleValue MAINCHAR_COOLDOWN_REDUCTION_T1 = BLESSINGS
            .comment("Main Character: attack-cooldown reduction at tier 1 (0..1). 0.30 = 30% faster attacks.")
            .defineInRange("mainCharCooldownReductionT1", 0.30, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue MAINCHAR_COOLDOWN_REDUCTION_T2 = BLESSINGS
            .comment("Main Character: attack-cooldown reduction at tier 2 (0..1). 0.45 = 45% faster attacks.")
            .defineInRange("mainCharCooldownReductionT2", 0.45, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue MAINCHAR_KNOCKBACK_T1 = BLESSINGS
            .comment("Main Character: outgoing-knockback multiplier at tier 1. 1.3 = 1.3x.")
            .defineInRange("mainCharKnockbackT1", 1.3, 1.0, 5.0);

    public static final ModConfigSpec.DoubleValue MAINCHAR_KNOCKBACK_T2 = BLESSINGS
            .comment("Main Character: outgoing-knockback multiplier at tier 2. 1.6 = 1.6x.")
            .defineInRange("mainCharKnockbackT2", 1.6, 1.0, 5.0);

    public static final ModConfigSpec.IntValue MAINCHAR_HORDE_THRESHOLD = BLESSINGS
            .comment("Main Character: hostiles aggroed onto you to reach the HORDE tier (3) — a swarm this size grants big",
                    "buffs (Strength, Resistance II, Regeneration). Works with any horde source, not just Popularity.")
            .defineInRange("mainCharHordeThreshold", 10, 3, 100);
    public static final ModConfigSpec.DoubleValue MAINCHAR_COOLDOWN_REDUCTION_T3 = BLESSINGS
            .comment("Main Character: attack-cooldown reduction at the HORDE tier (0..1). 0.6 = 60% faster attacks.")
            .defineInRange("mainCharCooldownReductionT3", 0.6, 0.0, 0.9);
    public static final ModConfigSpec.DoubleValue MAINCHAR_KNOCKBACK_T3 = BLESSINGS
            .comment("Main Character: outgoing-knockback multiplier at the HORDE tier. 2.0 = 2x.")
            .defineInRange("mainCharKnockbackT3", 2.0, 1.0, 6.0);

    public static final ModConfigSpec.DoubleValue MAINCHAR_MUSIC_VOLUME = BLESSINGS
            .comment("Main Character: volume of the battle-theme loop at the protagonist (fades with distance for onlookers).")
            .defineInRange("mainCharMusicVolume", 0.52, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue MAINCHAR_MUSIC_RANGE = BLESSINGS
            .comment("Main Character: how far (blocks) nearby players can hear the theme; volume fades to 0 at this range.",
                    "(Manual falloff, because the supplied stereo track can't be positionally attenuated by the engine.)")
            .defineInRange("mainCharMusicRange", 24.0, 4.0, 128.0);

    // --- Farmer's Spirit (blessing) --------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("farmspirit"); }
    public static final ModConfigSpec.IntValue FARMSPIRIT_RADIUS = BLESSINGS
            .comment("Farmer's Spirit: radius (blocks) of the CIRCLE around you that plants grow fast in.")
            .defineInRange("farmersSpiritRadius", 4, 1, 48);

    public static final ModConfigSpec.IntValue FARMSPIRIT_INTERVAL = BLESSINGS
            .comment("Farmer's Spirit: ticks between growth passes. Higher = crops climb their stages a touch slower.")
            .defineInRange("farmersSpiritIntervalTicks", 4, 1, 100);

    public static final ModConfigSpec.IntValue FARMSPIRIT_ATTEMPTS = BLESSINGS
            .comment("Farmer's Spirit: how many random nearby spots are 'bone-mealed' each pass (covers the circle).")
            .defineInRange("farmersSpiritAttemptsPerPass", 20, 1, 64);

    public static final ModConfigSpec.IntValue FARMSPIRIT_PRODUCE_TICKS = BLESSINGS
            .comment("Farmer's Spirit: extra random-ticks applied per hit to fruit/stalk PRODUCERS (pumpkin/melon",
                    "stems, sugar cane, cactus, bamboo) so they crank out blocks fast enough to farm effectively.")
            .defineInRange("farmersSpiritProduceTicks", 8, 0, 64);

    public static final ModConfigSpec.IntValue FARMSPIRIT_GROWTH_PER_HIT = BLESSINGS
            .comment("Farmer's Spirit: bone-meal applications a crop gets per hit. 1 = climb ONE stage at a time",
                    "(quick, but you can watch it grow rather than instant).")
            .defineInRange("farmersSpiritGrowthPerHit", 1, 1, 20);

    public static final ModConfigSpec.DoubleValue FARMSPIRIT_GRASS_CHANCE = BLESSINGS
            .comment("Farmer's Spirit: chance (0..1) a grass BLOCK is bone-mealed when picked — kept low, so grass",
                    "spreads flowers/tall grass only 'to a lesser extent' than crops/saplings shoot up.")
            .defineInRange("farmersSpiritGrassChance", 0.06, 0.0, 1.0);
    public static final ModConfigSpec.IntValue FARMSPIRIT_BABY_GROWTH_TICKS = BLESSINGS
            .comment("Farmer's Spirit: age-ticks shaved off every nearby BABY animal each pass, so young livestock grow up quicker.")
            .defineInRange("farmersSpiritBabyGrowthTicks", 20, 0, 400);
    public static final ModConfigSpec.DoubleValue FARMSPIRIT_DRIVE_GROWTH_MULTIPLIER = BLESSINGS
            .comment("Farmer's Spirit+Drive synergy: baby-growth speed is multiplied by this while Drive is also active.")
            .defineInRange("farmersSpiritDriveGrowthMultiplier", 3.0, 1.0, 10.0);

    // --- Brute (blessing) ------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("brute"); }
    public static final ModConfigSpec.IntValue BRUTE_RAMP_TIME = BLESSINGS
            .comment("Brute: ticks of consistent sprinting (no jumping/interruptions) to reach top speed. 30 = 1.5s.")
            .defineInRange("bruteRampTimeTicks", 30, 5, 400);

    public static final ModConfigSpec.DoubleValue BRUTE_MAX_SPEED_MULT = BLESSINGS
            .comment("Brute: movement-speed multiplier at full charge. 2.6 = 2.6x.")
            .defineInRange("bruteMaxSpeedMultiplier", 2.6, 1.0, 5.0);
    public static final ModConfigSpec.IntValue BRUTE_SPRINT_GRACE_TICKS = BLESSINGS
            .comment("Brute: how long the charge survives a brief sprint drop (a wall collision cancels vanilla",
                    "sprint, a jump/turn can too) before it resets. Keeps the build-up fluid.")
            .defineInRange("bruteSprintGraceTicks", 10, 0, 60);


    public static final ModConfigSpec.IntValue BRUTE_WINDUP_TIME = BLESSINGS
            .comment("Brute: an initial quiet windup (no particles, no speed) of consistent sprinting before the ramp.",
                    "15 = 0.75s. Once completed it is BANKED — a jump resets only the ramp, not this windup.")
            .defineInRange("bruteWindupTicks", 15, 0, 200);


    public static final ModConfigSpec.IntValue BRUTE_SHAKE_TICKS = BLESSINGS
            .comment("Brute: how long the little camera jolt lasts when you smash a block, in ticks.")
            .defineInRange("bruteShakeTicks", 4, 0, 40);

    public static final ModConfigSpec.DoubleValue BRUTE_SHAKE_STRENGTH = BLESSINGS
            .comment("Brute: strength of that smash camera jolt (slight).")
            .defineInRange("bruteShakeStrength", 0.6, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue BRUTE_FOOTSTEP_VOLUME = BLESSINGS
            .comment("Brute: volume of the amplified footsteps while you wind up and sprint (vanilla steps are ~0.15).")
            .defineInRange("bruteFootstepVolume", 1.6, 0.0, 5.0);

    public static final ModConfigSpec.IntValue BRUTE_ENTITY_HIT_COOLDOWN = BLESSINGS
            .comment("Brute: per-entity cooldown (ticks) between plough-hits, so you cleave through a crowd hitting",
                    "each once rather than the same body every tick. You keep full charge (no momentum bleed).")
            .defineInRange("bruteEntityHitCooldownTicks", 12, 1, 200);

    public static final ModConfigSpec.DoubleValue BRUTE_CAP_THRESHOLD = BLESSINGS
            .comment("Brute: charge fraction (0..1) at which you start launching entities and smashing blocks.")
            .defineInRange("bruteCapThreshold", 0.85, 0.1, 1.0);

    public static final ModConfigSpec.DoubleValue BRUTE_LAUNCH_FORCE = BLESSINGS
            .comment("Brute: how hard entities you plough into are launched, at full charge.")
            .defineInRange("bruteLaunchForce", 3.4, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue BRUTE_SMASH_POWER = BLESSINGS
            .comment("Brute: explosion power when you ram a wall at full charge — blasts a hole so you run through.",
                    "Respects blast resistance (obsidian/bedrock stop you) and mobGriefing. 0 = no smashing.")
            .defineInRange("bruteSmashPower", 3.5, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue BRUTE_SMASH_HP_COST = BLESSINGS
            .comment("Brute: health spent per wall-smash explosion (won't smash if it would drop you too low).")
            .defineInRange("bruteSmashHpCost", 2.0, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue BRUTE_LAUNCH_DAMAGE = BLESSINGS
            .comment("Brute: damage dealt to entities you plough into.")
            .defineInRange("bruteLaunchDamage", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.IntValue BRUTE_SPEED_CHARGE_BONUS = BLESSINGS
            .comment("Brute+Speed synergy: extra charge ticks banked per tick while a speed blessing is on you (1 = charges twice as fast).")
            .defineInRange("bruteSpeedChargeBonus", 1, 0, 10);
    public static final ModConfigSpec.DoubleValue BRUTE_SPEED_KNOCKBACK_MULT = BLESSINGS
            .comment("Brute+Speed synergy: entities you plough into are flung this much harder.")
            .defineInRange("bruteSpeedKnockbackMultiplier", 1.5, 1.0, 4.0);
    public static final ModConfigSpec.DoubleValue BRUTE_SPEED_BONUS_DAMAGE = BLESSINGS
            .comment("Brute+Speed synergy: extra flat damage on a plough hit.")
            .defineInRange("bruteSpeedBonusDamage", 3.0, 0.0, 20.0);


    // --- Chat (blessing) -------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("chat"); }
    public static final ModConfigSpec.IntValue CHAT_INTERVAL_MIN = BLESSINGS
            .comment("Chat: shortest gap between chat messages, in ticks, at peak hype (chat floods when you pop off).")
            .defineInRange("chatIntervalMinTicks", 3, 1, 400);

    public static final ModConfigSpec.IntValue CHAT_INTERVAL_MAX = BLESSINGS
            .comment("Chat: longest gap between chat messages, in ticks, when the chat is basically dead.")
            .defineInRange("chatIntervalMaxTicks", 300, 2, 1200);

    public static final ModConfigSpec.DoubleValue CHAT_USEFUL_CHANCE = BLESSINGS
            .comment("Chat: chance (0..1) a given message is a genuinely-useful info drop (nearby structure/player/etc).")
            .defineInRange("chatUsefulChance", 0.12, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_SCORE_MAX = BLESSINGS
            .comment("Chat: cap on the internal entertainment score. Large = a real grind to fill (harder to climb).")
            .defineInRange("chatScoreMax", 1000.0, 10.0, 100000.0);

    public static final ModConfigSpec.DoubleValue CHAT_DEAD_THRESHOLD = BLESSINGS
            .comment("Chat: hype fraction (0..1) at/below which the chat goes DEAD — sparse, sad, no subs. Crawl out of it.")
            .defineInRange("chatDeadThreshold", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_REVIVE_THRESHOLD = BLESSINGS
            .comment("Chat: hype fraction (0..1) you must EXCEED to revive a dead chat (hysteresis, so it doesn't flicker).")
            .defineInRange("chatReviveThreshold", 0.14, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_PVP_SCORE = BLESSINGS
            .comment("Chat: entertainment score gained for a PvP hit (HIGH).")
            .defineInRange("chatPvpScore", 12.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_PVE_SCORE = BLESSINGS
            .comment("Chat: entertainment score gained for a PvE hit (small — it's the kill that matters).")
            .defineInRange("chatPveScore", 4.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_BUILD_SCORE = BLESSINGS
            .comment("Chat: entertainment score gained for placing a block (small).")
            .defineInRange("chatBuildScore", 2.5, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_MINE_SCORE = BLESSINGS
            .comment("Chat: entertainment score gained for breaking a block (small).")
            .defineInRange("chatMineScore", 1.8, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_IDLE_DECAY = BLESSINGS
            .comment("Chat: score lost per tick while IDLING (standing still doing nothing) — bleeds fast, keep performing.")
            .defineInRange("chatIdleDecay", 1.6, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue CHAT_PASSIVE_DECAY = BLESSINGS
            .comment("Chat: score lost per tick while doing nothing entertaining (moving about but not performing).")
            .defineInRange("chatPassiveDecay", 0.55, 0.0, 200.0);

    public static final ModConfigSpec.IntValue CHAT_SUBS_CAP = BLESSINGS
            .comment("Chat: maximum subs you can bank in one stream (caps the end reward). Big ceiling for big streams.")
            .defineInRange("chatSubsCap", 5000, 1, 1000000);

    public static final ModConfigSpec.IntValue CHAT_SUBS_PER_EMERALD = BLESSINGS
            .comment("Chat: subs required per EMERALD paid out at stream's end. Emeralds are the ONLY reward. Higher = stingier.")
            .defineInRange("chatSubsPerEmerald", 21, 1, 100000);

    public static final ModConfigSpec.DoubleValue CHAT_SLEEP_DECAY = BLESSINGS
            .comment("Chat: entertainment score lost per tick while you're SLEEPING (nobody tunes in to watch you nap) — a slight drain.")
            .defineInRange("chatSleepDecay", 0.8, 0.0, 50.0);

    public static final ModConfigSpec.DoubleValue CHAT_SUB_PER_VIEWER_TICK = BLESSINGS
            .comment("Chat: subs gained per VIEWER per tick. Since viewers scale exponentially with hype, subs explode at the top and barely move at the bottom.")
            .defineInRange("chatSubPerViewerTick", 0.00003, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CHAT_STRUCTURE_RADIUS_CHUNKS = BLESSINGS
            .comment("Chat: how far out (chunks) the useful-info drops look for structures.")
            .defineInRange("chatStructureRadiusChunks", 6, 1, 32);

    public static final ModConfigSpec.DoubleValue CHAT_KILL_SCORE = BLESSINGS
            .comment("Chat: score for killing a mob (a real highlight — spikes the chat).")
            .defineInRange("chatKillScore", 18.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_PVP_KILL_SCORE = BLESSINGS
            .comment("Chat: score for killing a PLAYER — the single most entertaining thing you can do.")
            .defineInRange("chatPvpKillScore", 85.0, 0.0, 3000.0);

    public static final ModConfigSpec.DoubleValue CHAT_CRIT_SCORE = BLESSINGS
            .comment("Chat: bonus score for landing a critical hit.")
            .defineInRange("chatCritScore", 6.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_DAMAGE_SCORE = BLESSINGS
            .comment("Chat: score for taking a big hit (drama is entertaining). Applies at/above the damage threshold.")
            .defineInRange("chatDamageScore", 10.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_DAMAGE_THRESHOLD = BLESSINGS
            .comment("Chat: minimum damage (half-hearts) taken in one hit to count as a 'big hit' for chat.")
            .defineInRange("chatDamageThreshold", 6.0, 0.5, 40.0);

    public static final ModConfigSpec.DoubleValue CHAT_CLUTCH_SCORE = BLESSINGS
            .comment("Chat: score for surviving a hit that leaves you on very low HP (a clutch — chat goes wild).")
            .defineInRange("chatClutchScore", 55.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_CLUTCH_HP = BLESSINGS
            .comment("Chat: HP (half-hearts) at or below which surviving a hit counts as a clutch.")
            .defineInRange("chatClutchHp", 4.0, 0.5, 20.0);

    public static final ModConfigSpec.DoubleValue CHAT_DEATH_PENALTY_FRACTION = BLESSINGS
            .comment("Chat: fraction (0..1) of your current entertainment score WIPED when you die — dying tanks interest.")
            .defineInRange("chatDeathPenaltyFraction", 0.55, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CHAT_DEATH_SPAM_TICKS = BLESSINGS
            .comment("Chat: how long (ticks) chat rapidly spams dealwithit/trolldance gifs after you die.")
            .defineInRange("chatDeathSpamTicks", 70, 0, 400);

    public static final ModConfigSpec.DoubleValue CHAT_FALL_SCORE = BLESSINGS
            .comment("Chat: score for taking notable fall damage (a classic fail).")
            .defineInRange("chatFallScore", 12.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_FALL_THRESHOLD = BLESSINGS
            .comment("Chat: minimum fall distance (blocks) to be worth a fail reaction.")
            .defineInRange("chatFallThreshold", 5.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue CHAT_ORE_SCORE = BLESSINGS
            .comment("Chat: score for mining a valuable ore (diamonds/emeralds/ancient debris).")
            .defineInRange("chatOreScore", 24.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_FISH_SCORE = BLESSINGS
            .comment("Chat: score for reeling in a catch.")
            .defineInRange("chatFishScore", 8.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_TAME_SCORE = BLESSINGS
            .comment("Chat: score for taming an animal (chat loves a new pet).")
            .defineInRange("chatTameScore", 28.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_TRADE_SCORE = BLESSINGS
            .comment("Chat: score for a villager trade.")
            .defineInRange("chatTradeScore", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_EAT_SCORE = BLESSINGS
            .comment("Chat: score for eating (small — mukbang content).")
            .defineInRange("chatEatScore", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_EXPLORE_SCORE = BLESSINGS
            .comment("Chat: score for entering a fresh chunk (exploring keeps the stream moving).")
            .defineInRange("chatExploreScore", 2.5, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_COMBO_WINDOW_TICKS = BLESSINGS
            .comment("Chat: window (ticks) within which chained highlights build the hype train / combo multiplier.")
            .defineInRange("chatComboWindowTicks", 90.0, 20.0, 600.0);

    public static final ModConfigSpec.DoubleValue CHAT_COMBO_MAX_MULT = BLESSINGS
            .comment("Chat: maximum score multiplier from a hot combo streak.")
            .defineInRange("chatComboMaxMultiplier", 3.0, 1.0, 20.0);

    public static final ModConfigSpec.IntValue CHAT_HYPE_TRAIN_HITS = BLESSINGS
            .comment("Chat: number of chained highlights that triggers a HYPE TRAIN (big sub surge + banner).")
            .defineInRange("chatHypeTrainHits", 6, 2, 50);

    public static final ModConfigSpec.IntValue CHAT_HYPE_TRAIN_SUB_BONUS = BLESSINGS
            .comment("Chat: instant sub bonus awarded when a hype train launches.")
            .defineInRange("chatHypeTrainSubBonus", 25, 0, 100000);

    public static final ModConfigSpec.DoubleValue CHAT_DONATION_CHANCE = BLESSINGS
            .comment("Chat: per-message chance (0..1), scaled by hype, of a random bit donation event (bonus subs).")
            .defineInRange("chatDonationChance", 0.06, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_RAID_CHANCE = BLESSINGS
            .comment("Chat: per-message chance (0..1), scaled by hype, of a random incoming raid (viewer + sub surge).")
            .defineInRange("chatRaidChance", 0.02, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CHAT_VIEWER_BASE = BLESSINGS
            .comment("Chat: viewer count at ZERO hype — keep it tiny (near-dead) so climbing means something.")
            .defineInRange("chatViewerBase", 1, 0, 100000);

    public static final ModConfigSpec.IntValue CHAT_VIEWER_MAX = BLESSINGS
            .comment("Chat: viewer count at FULL hype. Viewers scale EXPONENTIALLY base->max, so the top end blows up (a lot higher) while the bottom crawls.")
            .defineInRange("chatViewerMax", 12000, 1, 100000000);

    public static final ModConfigSpec.DoubleValue CHAT_HYPE_EASE = BLESSINGS
            .comment("Chat: how fast the shown hype eases toward the real score each tick (0..1). Smaller = more gradual climbs/drops (not instant).")
            .defineInRange("chatHypeEase", 0.025, 0.001, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_EMOTE_CHANCE = BLESSINGS
            .comment("Chat: chance (0..1) a normal chat line is a spammed EMOTE image instead of text, at full hype (scales down with hype).")
            .defineInRange("chatEmoteChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_GIF_CHANCE = BLESSINGS
            .comment("Chat: chance (0..1) a normal chat line is an animated GIF instead, at full hype (scales down with hype). Kept rarer than emotes.")
            .defineInRange("chatGifChance", 0.06, 0.0, 1.0);

    // --- Laugh Track (blessing) ------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("laughtrack"); }
    public static final ModConfigSpec.IntValue LAUGHTRACK_COOLDOWN = BLESSINGS
            .comment("Laugh Track: minimum ticks between reactions, so rapid-fire chat doesn't stack the crowd. 120 = 6s.")
            .defineInRange("laughTrackCooldownTicks", 120, 0, 2400);

    public static final ModConfigSpec.DoubleValue LAUGHTRACK_CHEER_CHANCE = BLESSINGS
            .comment("Laugh Track: chance (0..1) the reaction is a CHEER rather than a laugh. 0.1 = 10% cheers, 90% laughs.")
            .defineInRange("laughTrackCheerChance", 0.1, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LAUGHTRACK_VOLUME = BLESSINGS
            .comment("Laugh Track: volume of the crowd reaction played to everyone.")
            .defineInRange("laughTrackVolume", 1.0, 0.0, 1.0);

    // --- Coyote (blessing) -----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("coyote"); }
    public static final ModConfigSpec.IntValue COYOTE_TICKS = BLESSINGS
            .comment("Coyote: how many ticks after walking off a ledge you can still jump (coyote time). 6 = 0.3s.")
            .defineInRange("coyoteTicks", 6, 0, 40);

    public static final ModConfigSpec.DoubleValue COYOTE_EDGE_MAGNETISM = BLESSINGS
            .comment("Coyote: gentle per-tick nudge (blocks/tick) toward a ledge you're falling short of, capped so it",
                    "assists rather than accelerates you.")
            .defineInRange("coyoteEdgeMagnetism", 0.03, 0.0, 0.5);

    public static final ModConfigSpec.DoubleValue COYOTE_ASSIST_MAX_SPEED = BLESSINGS
            .comment("Coyote: the edge assist only helps you up to THIS horizontal speed (blocks/tick) and never faster —",
                    "so it nudges slow/short jumps onto ledges but never turns a sprint jump into a rocket. ~0.26 = below a sprint jump.")
            .defineInRange("coyoteAssistMaxSpeed", 0.26, 0.05, 1.0);

    // --- Angler (blessing) -----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("angler"); }
    public static final ModConfigSpec.IntValue ANGLER_BITE_EXTRA_TICKS = BLESSINGS
            .comment("Angler: extra ticks knocked off the bite timer each tick (on top of vanilla's 1). 5 = fish bite ~6x faster.")
            .defineInRange("anglerBiteExtraTicks", 5, 0, 40);

    public static final ModConfigSpec.DoubleValue ANGLER_MULTI_CHANCE = BLESSINGS
            .comment("Angler: chance (0..1) a catch pulls out MULTIPLE things (an extra copy of the loot).")
            .defineInRange("anglerMultiChance", 0.35, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue ANGLER_SURPRISE_CHANCE = BLESSINGS
            .comment("Angler: chance (0..1) a catch also drags a comical surprise out of the water (treasure/fish/mob/TNT).")
            .defineInRange("anglerSurpriseChance", 0.22, 0.0, 1.0);

    // --- Excavation (blessing) -------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("excavation"); }
    public static final ModConfigSpec.DoubleValue EXCAVATION_RAMP_PER_BLOCK = BLESSINGS
            .comment("Excavation: extra mining-speed bonus gained per block broken. 0.15 = +15%/block (~40 to cap).")
            .defineInRange("excavationRampPerBlock", 0.15, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue EXCAVATION_MAX_BONUS = BLESSINGS
            .comment("Excavation: cap on the mining-speed bonus. 6.0 = up to 7x mining speed.")
            .defineInRange("excavationMaxBonus", 6.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue EXCAVATION_DECAY_GRACE = BLESSINGS
            .comment("Excavation: ticks after your last break before the built-up speed decays (covers mining one",
                    "slow block). 20 = 1s.")
            .defineInRange("excavationDecayGraceTicks", 20, 0, 400);

    public static final ModConfigSpec.DoubleValue EXCAVATION_DECAY_PER_TICK = BLESSINGS
            .comment("Excavation: bonus lost per tick once the grace lapses. 0.06 fades a full 6.0 stack to 0 over ~5s.")
            .defineInRange("excavationDecayPerTick", 0.06, 0.0, 5.0);

    // --- Bouncy (blessing) -----------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("bouncy"); }
    public static final ModConfigSpec.DoubleValue BOUNCY_RESTITUTION = CURSES
            .comment("Bouncy: how elastic your landing rebounds are (0..1). Lower settles in a few bounces; holding",
                    "jump adds on top, so you still build height. 0.65 bounces like a slime block that eventually stops.")
            .defineInRange("bouncyRestitution", 0.65, 0.0, 1.5);
    public static final ModConfigSpec.DoubleValue BOUNCY_JUMP_BONUS = CURSES
            .comment("Bouncy (now a CURSE): JUMP_STRENGTH bonus as a fraction (0.5 = +50% jump height) — springy legs launch you higher.")
            .defineInRange("bouncyJumpBonus", 0.5, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_LANDING_MAX = CURSES
            .comment("Bouncy: cap on rebound velocity, so it can't runaway into orbit.")
            .defineInRange("bouncyLandingMax", 1.9, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_MIN_FALL_VELOCITY = CURSES
            .comment("Bouncy: minimum impact speed (blocks/tick) to rebound when you're NOT holding jump — so only",
                    "real falls from height bounce and normal jumping/landing doesn't spam little bounces. 0.5 ~ a",
                    "1.5-block drop. Holding jump drops the threshold so you can trampoline and build height. Sneak = never bounce.")
            .defineInRange("bouncyMinFallVelocity", 0.5, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_ENTITY_FORCE = CURSES
            .comment("Bouncy: how hard entities are flung when you SPRINT into them / they sprint into you / they hit you.")
            .defineInRange("bouncyEntityForce", 1.6, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_WALK_FORCE_MULT = CURSES
            .comment("Bouncy: fraction of the entity force applied when you merely WALK into something (a slight nudge).")
            .defineInRange("bouncyWalkForceMultiplier", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_COLLISION_SPEED = CURSES
            .comment("Bouncy: how fast an entity must be closing on you (blocks/tick) to bounce off when YOU are still.")
            .defineInRange("bouncyCollisionSpeed", 0.12, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue BOUNCY_WALL_MIN_SPEED = CURSES
            .comment("Bouncy: horizontal speed (blocks/tick) needed to rebound off a wall. Must sit below sprint speed",
                    "(~0.13) or you can never wall-bounce while running — the old hardcoded 0.15 was above it, so wall bounces never fired.")
            .defineInRange("bouncyWallMinSpeed", 0.07, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BOUNCY_LOWGRAV_RESTITUTION_MULT = CURSES
            .comment("Bouncy+Low Gravity synergy: floor/wall/ceiling rebounds are this much stronger (and the landing cap is lifted the same).")
            .defineInRange("bouncyLowGravRestitutionMultiplier", 1.4, 1.0, 3.0);

    // --- Hot Stuff (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("hotstuff"); }
    public static final ModConfigSpec.DoubleValue HOTSTUFF_SPEED_MULT = BLESSINGS
            .comment("Hot Stuff: how many times faster a furnace cooks while you're looking at it. 10.0 = 10x.")
            .defineInRange("hotStuffSpeedMultiplier", 10.0, 1.0, 40.0);
    public static final ModConfigSpec.DoubleValue HOTSTUFF_SCORCHED_MULT = BLESSINGS
            .comment("Hot Stuff+Floor is Lava synergy: the furnace speed multiplier is scaled by this again (2.0 = double).")
            .defineInRange("hotStuffScorchedMultiplier", 2.0, 1.0, 8.0);

    // --- Batch synergy knobs (cross-effect combos, see Synergies) --------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("pickpocket"); }
    public static final ModConfigSpec.DoubleValue PICKPOCKET_UNSEEN_MULT = BLESSINGS
            .comment("Pickpocket+Unseen synergy: pickpocket chance multiplier when behind someone while unseen.")
            .defineInRange("pickpocketUnseenMultiplier", 2.0, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue PICKPOCKET_DISGUISE_MULT = BLESSINGS
            .comment("Pickpocket+Disguise/Prop Hunt synergy: pickpocket chance multiplier when behind someone while wearing a form.")
            .defineInRange("pickpocketDisguiseMultiplier", 3.0, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue PICKPOCKET_SNACK_CHANCE_FULL = BLESSINGS
            .comment("Pickpocket+Munchies/Gluttony synergy (snack thief): chance (0..1) per attempt to steal and instantly",
                    "eat one of a nearby player's foods when your hunger is nearly full. Rolled independently of the",
                    "normal pickpocket lift. The chance slides toward pickpocketSnackChanceStarving as your hunger empties.")
            .defineInRange("pickpocketSnackChanceFull", 0.08, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue PICKPOCKET_SNACK_CHANCE_STARVING = BLESSINGS
            .comment("Snack thief: chance (0..1) per attempt when your hunger bar is empty.")
            .defineInRange("pickpocketSnackChanceStarving", 0.7, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PICKPOCKET_SNACK_INTERVAL_FULL = BLESSINGS
            .comment("Snack thief: ticks between snack attempts when your hunger is nearly full (60 = every 3s).",
                    "Slides toward pickpocketSnackIntervalStarvingTicks as your hunger empties.")
            .defineInRange("pickpocketSnackIntervalFullTicks", 60, 1, 1200);
    public static final ModConfigSpec.IntValue PICKPOCKET_SNACK_INTERVAL_STARVING = BLESSINGS
            .comment("Snack thief: ticks between snack attempts when your hunger bar is empty (10 = every 0.5s).")
            .defineInRange("pickpocketSnackIntervalStarvingTicks", 10, 1, 1200);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("silver"); }
    public static final ModConfigSpec.IntValue SILVER_VILLAGER_REFUND_CHANCE = BLESSINGS
            .comment("Silver Tongue+Disguise synergy: percent chance a completed villager trade refunds its cost.")
            .defineInRange("silverVillagerRefundChancePercent", 10, 0, 100);
    static { SYNERGIES.comment("witchmod — synergies.toml: knobs for cross-effect synergies (bonuses that exist only",
            "while a player carries BOTH of two effects).").push("homecoming"); }
    public static final ModConfigSpec.IntValue HOMECOMING_REGEN_SECONDS = SYNERGIES
            .comment("Homebody+Safety synergy: seconds of Regeneration granted after a teleport home.")
            .defineInRange("homecomingRegenSeconds", 3, 0, 60);
    public static final ModConfigSpec.IntValue HOMECOMING_REGEN_AMPLIFIER = SYNERGIES
            .comment("Homebody+Safety synergy: Regeneration amplifier after a teleport (9 = Regeneration 10).")
            .defineInRange("homecomingRegenAmplifier", 9, 0, 20);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("allergic"); }
    public static final ModConfigSpec.IntValue ALLERGIC_GASSY_TIER1_CHANCE = SYNERGIES
            .comment("Allergic+Gassy synergy: percent chance PER SECOND of a reaction fart at tier 1.")
            .defineInRange("allergicGassyTier1ChancePercent", 3, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_GASSY_TIER2_CHANCE = SYNERGIES
            .comment("Allergic+Gassy synergy: percent chance per second of a reaction fart at tier 2.")
            .defineInRange("allergicGassyTier2ChancePercent", 8, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_GASSY_TIER3_CHANCE = SYNERGIES
            .comment("Allergic+Gassy synergy: percent chance per second of a reaction fart at tier 3.")
            .defineInRange("allergicGassyTier3ChancePercent", 20, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_GASSY_BIG_CHANCE = SYNERGIES
            .comment("Allergic+Gassy synergy: percent of tier 3 reaction farts that are BIG ones.")
            .defineInRange("allergicGassyTier3BigChancePercent", 50, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_HICCUPS_TIER1_CHANCE = SYNERGIES
            .comment("Allergic+Hiccups synergy: percent chance PER SECOND of a reaction hiccup at tier 1.")
            .defineInRange("allergicHiccupsTier1ChancePercent", 3, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_HICCUPS_TIER2_CHANCE = SYNERGIES
            .comment("Allergic+Hiccups synergy: percent chance per second of a reaction hiccup at tier 2.")
            .defineInRange("allergicHiccupsTier2ChancePercent", 8, 0, 100);
    public static final ModConfigSpec.IntValue ALLERGIC_HICCUPS_TIER3_CHANCE = SYNERGIES
            .comment("Allergic+Hiccups synergy: percent chance per second of a reaction hiccup at tier 3.")
            .defineInRange("allergicHiccupsTier3ChancePercent", 20, 0, 100);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("layered"); }
    public static final ModConfigSpec.DoubleValue LAYERED_HIDE_FLOOR_BONUS = SYNERGIES
            .comment("Thick Skinned+Tank synergy: extra damage added to the negation floor (1.0 = threshold +1).")
            .defineInRange("layeredHideFloorBonus", 1.0, 0.0, 10.0);
    static { CURSES.pop(); }
    static { CURSES.push("violence"); }
    public static final ModConfigSpec.DoubleValue VIOLENCE_FRENZY_CHANCE_MULT = CURSES
            .comment("Violence+Berserker (frenzy) synergy: the impulsive swing chance AND its cap are scaled by this.")
            .defineInRange("violenceFrenzyChanceMultiplier", 2.5, 1.0, 6.0);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("dead"); }
    public static final ModConfigSpec.DoubleValue DEAD_WEIGHT_PULL_MULT = SYNERGIES
            .comment("Dense+Bad Swimmer synergy: the client sink pull is scaled by this — you drop like a stone.")
            .defineInRange("deadWeightPullMultiplier", 3.0, 1.0, 12.0);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("dizzy"); }
    public static final ModConfigSpec.DoubleValue DIZZY_HEIGHTS_START_MULT = SYNERGIES
            .comment("Wonky+Vertigo synergy: vertigo's onset height is scaled by this (0.8 = 20% lower).")
            .defineInRange("dizzyHeightsStartMultiplier", 0.8, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue DIZZY_HEIGHTS_SWAY_MULT = SYNERGIES
            .comment("Wonky+Vertigo synergy: vertigo's camera + movement sway is scaled by this.")
            .defineInRange("dizzyHeightsSwayMultiplier", 1.5, 1.0, 4.0);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("speed"); }
    public static final ModConfigSpec.DoubleValue SPEED_DEMON_ICE_BONUS = BLESSINGS
            .comment("Speed Demon+Ice Skates (greased_mount) synergy: extra stacking mount-speed bonus (0.5 = +50%).")
            .defineInRange("speedDemonIceBonus", 0.5, 0.0, 4.0);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("size"); }
    public static final ModConfigSpec.IntValue SIZE_CRISIS_MIN_GAP_TICKS = SYNERGIES
            .comment("Giant+Dwarfism (size_crisis) synergy: shortest gap (ticks) before the size flips.")
            .defineInRange("sizeCrisisMinGapTicks", 60, 10, 2000);
    public static final ModConfigSpec.IntValue SIZE_CRISIS_MAX_GAP_TICKS = SYNERGIES
            .comment("Giant+Dwarfism (size_crisis) synergy: longest gap (ticks) before the size flips.")
            .defineInRange("sizeCrisisMaxGapTicks", 160, 10, 4000);
    public static final ModConfigSpec.DoubleValue SIZE_CRISIS_CRUSH_DAMAGE = SYNERGIES
            .comment("Giant+Dwarfism (size_crisis) synergy: crush damage dealt to a mount you shrink on top of.")
            .defineInRange("sizeCrisisCrushDamage", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue SIZE_CRISIS_SMALL_REACH_MULT = SYNERGIES
            .comment("Giant+Dwarfism (size_crisis) synergy: while SMALL, the kept giant reach bonus is scaled by this (0.5 = extra reach halved).")
            .defineInRange("sizeCrisisSmallReachMultiplier", 0.5, 0.0, 1.0);

    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("hotstuff"); }
    public static final ModConfigSpec.DoubleValue HOTSTUFF_LOOK_RANGE = BLESSINGS
            .comment("Hot Stuff: how far (blocks) your gaze reaches to heat up a furnace.")
            .defineInRange("hotStuffLookRange", 8.0, 1.0, 32.0);

    // --- Silver Tongue (blessing) ----------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("silvertongue"); }
    public static final ModConfigSpec.DoubleValue SILVERTONGUE_DISCOUNT = BLESSINGS
            .comment("Silver Tongue: how much cheaper villager trades are (0..1). 0.75 = pay 25%, get 75% refunded.")
            .defineInRange("silverTongueDiscount", 0.75, 0.0, 1.0);

    // --- Unseen (blessing) -----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("unseen"); }
    public static final ModConfigSpec.DoubleValue UNSEEN_REVEAL_DISTANCE = BLESSINGS
            .comment("Unseen: how close (blocks) someone must be to see you at all — beyond it you're fully hidden,",
                    "and mobs can't lock onto you. Getting within it is the obvious counterplay. ~6 blocks.")
            .defineInRange("unseenRevealDistance", 6.0, 1.0, 32.0);

    // --- Thick Skinned (blessing) ----------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("thickskin"); }
    public static final ModConfigSpec.DoubleValue THICKSKIN_DAMAGE_FLOOR = BLESSINGS
            .comment("Thick Skinned: any single damage event AT OR BELOW this is fully ignored; damage must exceed it to land. 2.0 = 1 heart.")
            .defineInRange("thickSkinnedDamageFloor", 2.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue THICKSKIN_SHAKE_TICKS = BLESSINGS
            .comment("Thick Skinned: how long the little feedback screenshake lasts when a hit is shrugged off, in ticks.")
            .defineInRange("thickSkinnedShakeTicks", 4, 0, 40);

    public static final ModConfigSpec.DoubleValue THICKSKIN_SHAKE_STRENGTH = BLESSINGS
            .comment("Thick Skinned: strength of that feedback screenshake (subtle).")
            .defineInRange("thickSkinnedShakeStrength", 0.45, 0.0, 5.0);

    // --- Last Stand (blessing) -------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("laststand"); }
    public static final ModConfigSpec.DoubleValue LASTSTAND_REVIVE_HEALTH = BLESSINGS
            .comment("Last Stand: health you're left on after a fatal blow. 1.0 = half a heart.")
            .defineInRange("lastStandReviveHealth", 6.0, 1.0, 20.0);
    public static final ModConfigSpec.IntValue LASTSTAND_INVULN_TICKS = BLESSINGS
            .comment("Last Stand: complete invulnerability granted right after a revive — a comeback window. 40 = 2s.")
            .defineInRange("lastStandInvulnTicks", 40, 0, 200);
    public static final ModConfigSpec.IntValue LASTSTAND_MAX_USES = BLESSINGS
            .comment("Last Stand: how many times it can revive you before the blessing is consumed.")
            .defineInRange("lastStandMaxUses", 3, 1, 20);
    public static final ModConfigSpec.IntValue LASTSTAND_BASE_COOLDOWN_TICKS = BLESSINGS
            .comment("Last Stand: cooldown after the FIRST revive, in ticks (doubles each subsequent revive). 6000 = 5min.")
            .defineInRange("lastStandBaseCooldownTicks", 6000, 20, 720000);


    public static final ModConfigSpec.DoubleValue LASTSTAND_KNOCKBACK = BLESSINGS
            .comment("Last Stand: outward knockback strength applied to nearby entities on the burst (no damage).")
            .defineInRange("lastStandKnockback", 2.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue LASTSTAND_RADIUS = BLESSINGS
            .comment("Last Stand: radius (blocks) of the knockback burst.")
            .defineInRange("lastStandRadius", 6.0, 1.0, 32.0);

    // --- Cornucopia (secret blessing) --------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("cornucopia"); }
    public static final ModConfigSpec.IntValue CORNUCOPIA_SLOTS = BLESSINGS
            .comment("Cornucopia: how many random blessings it holds on you at once, ON TOP of the normal blessing limit.")
            .defineInRange("cornucopiaSlots", 3, 1, 10);
    public static final ModConfigSpec.IntValue CORNUCOPIA_SWAP_SECONDS = BLESSINGS
            .comment("Cornucopia: seconds between swaps — each swap trades the OLDEST held blessing for a new random one.")
            .defineInRange("cornucopiaSwapSeconds", 90, 5, 3600);

    // --- Puppeteer (secret blessing) ---------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("puppeteer"); }
    public static final ModConfigSpec.IntValue PUPPETEER_COOLDOWN_SECONDS = BLESSINGS
            .comment("Puppeteer: seconds after leaving (or losing) a puppet before you can possess another.",
                    "(1 while testing — raise it for real play, e.g. 60.)")
            .defineInRange("puppeteerCooldownSeconds", 1, 0, 3600);
    public static final ModConfigSpec.IntValue PUPPETEER_POSSESS_TICKS = BLESSINGS
            .comment("Puppeteer: length (ticks) of the possession itself — the mob freezes, your soul streams into it and",
                    "your body is drawn into its spot before you take control.")
            .defineInRange("puppeteerPossessTicks", 22, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_RELEASE_GRACE_TICKS = BLESSINGS
            .comment("Puppeteer: when you step out, the mob stands dazed (no AI) for this many ticks so it doesn't",
                    "instantly attack you. Saved with the mob, so a crash or unload can never leave it stuck that way.")
            .defineInRange("puppeteerReleaseGraceTicks", 40, 0, 400);
    public static final ModConfigSpec.BooleanValue PUPPETEER_MONSTERS_IGNORE = BLESSINGS
            .comment("Puppeteer: while you're possessing something, monsters don't target you (you're one of them, or prey",
                    "they'd ignore anyway).")
            .define("puppeteerMonstersIgnore", true);

    // zombie family (zombie, husk, drowned, zombie villager, zombified piglin)
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOMBIE_SPEED = BLESSINGS
            .comment("Puppeteer (zombies): your movement speed as a multiple of normal (sprinting still works).")
            .defineInRange("puppeteerZombieSpeedMultiplier", 0.9, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOMBIE_ATTACK = BLESSINGS
            .comment("Puppeteer (zombies): damage of each hit (before your own blessings/curses scale it).")
            .defineInRange("puppeteerZombieAttackDamage", 3.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOMBIE_REACH = BLESSINGS
            .comment("Puppeteer (zombies): how far you can hit (blocks) — a zombie's short reach, not a player's 3.")
            .defineInRange("puppeteerZombieReach", 2.0, 0.5, 6.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOMBIE_RALLY_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (zombies): ticks between Rallies (right-click). 140 = 7s.")
            .defineInRange("puppeteerZombieRallyCooldownTicks", 140, 0, 2400);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOMBIE_RALLY_RANGE = BLESSINGS
            .comment("Puppeteer (zombies): Rally MARKS whoever you're LOOKING AT, up to this many blocks away.")
            .defineInRange("puppeteerZombieRallyRange", 24.0, 1.0, 96.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOMBIE_RALLY_RADIUS = BLESSINGS
            .comment("Puppeteer (zombies): the marked target glows, and every zombie variant within this many blocks of you",
                    "goes after it with a speed boost. (Skeleton puppets use the same radius: skeletons near you shoot at",
                    "whatever your arrows hit.)")
            .defineInRange("puppeteerZombieRallyRadius", 16.0, 1.0, 64.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOMBIE_RALLY_SECONDS = BLESSINGS
            .comment("Puppeteer (zombies): how long the mark (glowing) and the rallied zombies' speed boost last.")
            .defineInRange("puppeteerZombieRallySeconds", 10, 1, 120);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOMBIE_RALLY_SPAWN_LIGHT = BLESSINGS
            .comment("Puppeteer (zombies): if a Rally finds NO zombies nearby and the light where you stand is at or below",
                    "this level (0-15), 1-2 of YOUR kind (zombies, husks, ...) claw their way up nearby to answer the call.")
            .defineInRange("puppeteerZombieRallySpawnLight", 7, 0, 15);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOMBIE_RALLY_SPAWN_MAX = BLESSINGS
            .comment("Puppeteer (zombies): the most a dark Rally can summon (it rolls 1..this).")
            .defineInRange("puppeteerZombieRallySpawnMax", 2, 0, 8);
    public static final ModConfigSpec.BooleanValue PUPPETEER_ZOMBIE_BURNS = BLESSINGS
            .comment("Puppeteer (zombie, zombie villager, drowned): burn in direct daylight without a helmet, like the real",
                    "thing (the puppet takes it). Husks and zombified piglins never burn.")
            .define("puppeteerZombieBurnsInDaylight", true);
    public static final ModConfigSpec.IntValue PUPPETEER_HUSK_HUNGER_SECONDS = BLESSINGS
            .comment("Puppeteer (husk): each hit gives the victim Hunger for this long, like a real husk.")
            .defineInRange("puppeteerHuskHungerSeconds", 7, 0, 120);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DROWNED_SWIM_BONUS = BLESSINGS
            .comment("Puppeteer (drowned): extra swim speed in water (1.0 = double), and you never run out of air.")
            .defineInRange("puppeteerDrownedSwimBonus", 1.0, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DROWNED_MELEE_DAMAGE = BLESSINGS
            .comment("Puppeteer (drowned): melee damage when it jabs with the trident instead of throwing it (a real drowned's trident melee).")
            .defineInRange("puppeteerDrownedMeleeDamage", 6.0, 0.0, 20.0);
    public static final ModConfigSpec.IntValue PUPPETEER_DROWNED_TRIDENT_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (drowned): ticks between trident throws. Hold right-click to charge (like a real trident,",
                    "at least 10 ticks), let go to throw. Anything it hits is marked for nearby drowned — and if none are",
                    "around and it's dark or near water, 1-2 drowned rise to answer (the Rally knobs above apply).")
            .defineInRange("puppeteerDrownedTridentCooldownTicks", 40, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_DROWNED_TRIDENT_LIFE_TICKS = BLESSINGS
            .comment("Puppeteer (drowned): thrown tridents can't be picked up and vanish after this many ticks.")
            .defineInRange("puppeteerDrownedTridentLifeTicks", 60, 5, 1200);

    // creeper
    public static final ModConfigSpec.DoubleValue PUPPETEER_CREEPER_SPEED = BLESSINGS
            .comment("Puppeteer (creeper): your movement speed as a multiple of normal (sprinting still works).")
            .defineInRange("puppeteerCreeperSpeedMultiplier", 1.0, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CREEPER_ATTACK = BLESSINGS
            .comment("Puppeteer (creeper): damage of each hit — creepers aren't fighters, it's a shove.")
            .defineInRange("puppeteerCreeperAttackDamage", 1.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CREEPER_REACH = BLESSINGS
            .comment("Puppeteer (creeper): how far you can hit (blocks).")
            .defineInRange("puppeteerCreeperReach", 2.0, 0.5, 6.0);
    public static final ModConfigSpec.IntValue PUPPETEER_CREEPER_FUSE_TICKS = BLESSINGS
            .comment("Puppeteer (creeper): hold right-click this long (ticks) to explode (a real creeper's is 30). Letting go",
                    "winds the fuse back down.")
            .defineInRange("puppeteerCreeperFuseTicks", 30, 5, 200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CREEPER_FUSE_SLOW = BLESSINGS
            .comment("Puppeteer (creeper): how much slower you move while the fuse is lit (0.75 = 75% slower).")
            .defineInRange("puppeteerCreeperFuseSlow", 0.75, 0.0, 1.0);

    // farm animals (pig, cow, sheep, chicken)
    public static final ModConfigSpec.DoubleValue PUPPETEER_ANIMAL_SPEED = BLESSINGS
            .comment("Puppeteer (farm animals): your movement speed as a multiple of normal (sprinting still works).")
            .defineInRange("puppeteerAnimalSpeedMultiplier", 0.8, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ANIMAL_ATTACK = BLESSINGS
            .comment("Puppeteer (farm animals): damage of each hit — a headbutt, not a weapon.")
            .defineInRange("puppeteerAnimalAttackDamage", 1.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ANIMAL_REACH = BLESSINGS
            .comment("Puppeteer (farm animals): how far you can hit (blocks).")
            .defineInRange("puppeteerAnimalReach", 1.5, 0.5, 6.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ANIMAL_SOUND_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (pig / cow / sheep): ticks between Oinks, Moos and grazes.")
            .defineInRange("puppeteerAnimalSoundCooldownTicks", 20, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ANIMAL_LURE_RANGE = BLESSINGS
            .comment("Puppeteer (farm animals): another player holding your animal's food (wheat, carrots, seeds...) within",
                    "this many blocks drags you towards them, like Siren's Call. 0 = off.")
            .defineInRange("puppeteerAnimalLureRange", 8.0, 0.0, 32.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ANIMAL_LURE_STRENGTH = BLESSINGS
            .comment("Puppeteer (farm animals): how hard that food drags you (added speed per tick).")
            .defineInRange("puppeteerAnimalLureStrength", 0.06, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_CHICKEN_EGG_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (chicken): ticks between LAID eggs (tap right-click). 500 = 25s. Chickens also fall gently.")
            .defineInRange("puppeteerChickenEggCooldownTicks", 500, 0, 12000);
    public static final ModConfigSpec.IntValue PUPPETEER_CHICKEN_EXPLOSIVE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (chicken): ticks between EXPLOSIVE eggs — HOLD right-click to charge (like a bow, at least",
                    "10 ticks) and let go to throw. Its own cooldown, separate from laying. 160 = 8s.")
            .defineInRange("puppeteerChickenExplosiveCooldownTicks", 160, 0, 12000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CHICKEN_EGG_VELOCITY = BLESSINGS
            .comment("Puppeteer (chicken): throw speed of a fully charged explosive egg (scaled by charge like a bow).")
            .defineInRange("puppeteerChickenEggVelocity", 2.24, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CHICKEN_EGG_POWER = BLESSINGS
            .comment("Puppeteer (chicken): explosion power of the explosive egg (tnt is 4). Respects mobGriefing.")
            .defineInRange("puppeteerChickenEggPower", 3.0, 0.0, 8.0);

    // skeleton
    public static final ModConfigSpec.DoubleValue PUPPETEER_SKELETON_SPEED = BLESSINGS
            .comment("Puppeteer (skeleton): your movement speed as a multiple of normal (sprinting still works).")
            .defineInRange("puppeteerSkeletonSpeedMultiplier", 1.0, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SKELETON_ATTACK = BLESSINGS
            .comment("Puppeteer (skeleton): damage of a melee hit (your arrows are a real bow's).")
            .defineInRange("puppeteerSkeletonAttackDamage", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SKELETON_REACH = BLESSINGS
            .comment("Puppeteer (skeleton): how far you can hit in melee (blocks).")
            .defineInRange("puppeteerSkeletonReach", 2.0, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SKELETON_DRAW_SPEED = BLESSINGS
            .comment("Puppeteer (skeleton): you draw a REAL bow (arrows and archery blessings work as normal) this much",
                    "faster than a player (1.25 = 25% faster). Stacks with draw-speed blessings like Dexterous.")
            .defineInRange("puppeteerSkeletonDrawSpeed", 1.25, 1.0, 4.0);

    // spiders (spider, cave spider)
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_SPEED = BLESSINGS
            .comment("Puppeteer (spiders): your movement speed as a multiple of normal. You climb walls like the Spider",
                    "blessing (hold forward against a wall), and every swing POUNCES you forward like a real spider's attack.")
            .defineInRange("puppeteerSpiderSpeedMultiplier", 1.1, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_ATTACK = BLESSINGS
            .comment("Puppeteer (spiders): damage of each bite.")
            .defineInRange("puppeteerSpiderAttackDamage", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_REACH = BLESSINGS
            .comment("Puppeteer (spiders): how far you can bite (blocks).")
            .defineInRange("puppeteerSpiderReach", 2.0, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_POUNCE_POWER = BLESSINGS
            .comment("Puppeteer (spiders): how far a swing's pounce throws you forward (a vanilla spider's leap is 0.4).")
            .defineInRange("puppeteerSpiderPouncePower", 0.6, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_POUNCE_LIFT = BLESSINGS
            .comment("Puppeteer (spiders): and how high (vanilla's is 0.4).")
            .defineInRange("puppeteerSpiderPounceLift", 0.4, 0.0, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SPIDER_POUNCE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (spiders): ticks between pounces (you have to be on the ground, too).")
            .defineInRange("puppeteerSpiderPounceCooldownTicks", 20, 0, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_CAVE_SPIDER_POISON_SECONDS = BLESSINGS
            .comment("Puppeteer (cave spider): bites poison the victim for this long, like a real cave spider's.")
            .defineInRange("puppeteerCaveSpiderPoisonSeconds", 7, 0, 120);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_AIR_POUNCE_POWER = BLESSINGS
            .comment("Puppeteer (spiders): swinging MID-JUMP pounces again (once per jump) — this much harder forward than",
                    "a ground pounce, for travel.")
            .defineInRange("puppeteerSpiderAirPounceMultiplier", 1.6, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SPIDER_AIR_POUNCE_LIFT = BLESSINGS
            .comment("Puppeteer (spiders): and a little extra lift on the air pounce.")
            .defineInRange("puppeteerSpiderAirPounceLift", 0.2, 0.0, 3.0);

    // wither skeleton
    public static final ModConfigSpec.DoubleValue PUPPETEER_WITHER_SKELETON_ATTACK = BLESSINGS
            .comment("Puppeteer (wither skeleton): damage of each hit (no special move — it hasn't one in vanilla either).")
            .defineInRange("puppeteerWitherSkeletonAttackDamage", 7.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WITHER_SKELETON_REACH = BLESSINGS
            .comment("Puppeteer (wither skeleton): reach (blocks) — it's tall.")
            .defineInRange("puppeteerWitherSkeletonReach", 2.5, 0.5, 6.0);
    public static final ModConfigSpec.IntValue PUPPETEER_WITHER_SKELETON_WITHER_SECONDS = BLESSINGS
            .comment("Puppeteer (wither skeleton): hits inflict Wither for this long (vanilla: 10). It's fire immune and",
                    "never burns in daylight.")
            .defineInRange("puppeteerWitherSkeletonWitherSeconds", 10, 0, 120);

    // guardians (guardian, elder guardian)
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_SWIM_BONUS = BLESSINGS
            .comment("Puppeteer (guardians): extra swim speed in water (1.0 = double). Endless air.")
            .defineInRange("puppeteerGuardianSwimBonus", 1.0, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_LAND_SLOW = BLESSINGS
            .comment("Puppeteer (guardians): out of water you flop and bounce about like a beached guardian (it doesn't",
                    "dry out, it's just awkward) — this much slower (0.5 = half speed).")
            .defineInRange("puppeteerGuardianLandSlow", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_FLOP_POWER = BLESSINGS
            .comment("Puppeteer (guardians): how high each beached bounce goes.")
            .defineInRange("puppeteerGuardianFlopPower", 0.5, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_BEAM_RANGE = BLESSINGS
            .comment("Puppeteer (guardians): the laser's reach (blocks). HOLD right-click to fire a beam you aim yourself;",
                    "it ticks damage on whatever it's on and ends in a burst.")
            .defineInRange("puppeteerGuardianBeamRange", 16.0, 2.0, 64.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GUARDIAN_BEAM_TICKS = BLESSINGS
            .comment("Puppeteer (guardians): full beam length (ticks). Holding this long fires the full BURST automatically.")
            .defineInRange("puppeteerGuardianBeamTicks", 60, 10, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_GUARDIAN_BEAM_MIN_BURST_TICKS = BLESSINGS
            .comment("Puppeteer (guardians): let go after at least this many ticks and you still get a (weaker) burst,",
                    "scaled by how long you held. Let go sooner and the beam just fizzles out.")
            .defineInRange("puppeteerGuardianBeamMinBurstTicks", 20, 1, 400);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_BEAM_TICK_DAMAGE = BLESSINGS
            .comment("Puppeteer (guardians): damage the beam does to what it's on, every 10 ticks (magic — ignores armour).")
            .defineInRange("puppeteerGuardianBeamTickDamage", 1.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_BURST_DAMAGE = BLESSINGS
            .comment("Puppeteer (guardian): damage of the full burst (a real guardian's beam does 6).")
            .defineInRange("puppeteerGuardianBurstDamage", 6.0, 0.0, 80.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ELDER_GUARDIAN_BURST_DAMAGE = BLESSINGS
            .comment("Puppeteer (elder guardian): damage of the full burst — an elder's beam is a real threat.")
            .defineInRange("puppeteerElderGuardianBurstDamage", 24.0, 0.0, 200.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ELDER_GUARDIAN_BEAM_TICK_DAMAGE = BLESSINGS
            .comment("Puppeteer (elder guardian): the beam's damage every 10 ticks while it's on something.")
            .defineInRange("puppeteerElderGuardianBeamTickDamage", 3.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GUARDIAN_BEAM_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (guardians): ticks after a burst before the beam can fire again. While channelling you're",
                    "anchored in place (easier to track a target); fire it out of water and this cooldown is doubled.")
            .defineInRange("puppeteerGuardianBeamCooldownTicks", 40, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ELDER_RALLY_RADIUS = BLESSINGS
            .comment("Puppeteer (elder guardian): its beam marks the target (glowing) and EVERY guardian within this many",
                    "blocks drops what it's doing and goes for it with Speed II. (A normal guardian's beam is a soft rally:",
                    "guardians within puppeteerZombieRallyRadius only join in if they aren't already busy.)")
            .defineInRange("puppeteerElderRallyRadius", 32.0, 1.0, 128.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ELDER_CALL_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (elder guardian): left-click (attack) is also Call — if fewer than",
                    "puppeteerElderCallTarget guardians are nearby, more rise from the water around you. Its cooldown.")
            .defineInRange("puppeteerElderCallCooldownTicks", 400, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_ELDER_CALL_TARGET = BLESSINGS
            .comment("Puppeteer (elder guardian): Call tops the guardians within puppeteerElderCallRadius up to this many.")
            .defineInRange("puppeteerElderCallTarget", 4, 1, 16);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ELDER_CALL_RADIUS = BLESSINGS
            .comment("Puppeteer (elder guardian): how far Call counts guardians, and looks for water to raise them from.")
            .defineInRange("puppeteerElderCallRadius", 12.0, 2.0, 48.0);

    // dolphin
    public static final ModConfigSpec.DoubleValue PUPPETEER_DOLPHIN_SWIM_BONUS = BLESSINGS
            .comment("Puppeteer (dolphin): extra swim speed (2.0 = triple). Dolphins breathe air: underwater your air lasts",
                    "16x longer (a real dolphin's 4 minutes) but you DO have to surface.")
            .defineInRange("puppeteerDolphinSwimBonus", 2.0, 0.0, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DOLPHIN_ATTACK = BLESSINGS
            .comment("Puppeteer (dolphin): damage of each hit (vanilla 3).")
            .defineInRange("puppeteerDolphinAttackDamage", 3.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DOLPHIN_LAND_SLOW = BLESSINGS
            .comment("Puppeteer (dolphin): out of water you flop about and are this much slower (0.7 = 70% slower).")
            .defineInRange("puppeteerDolphinLandSlow", 0.7, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_DOLPHIN_MOISTURE_TICKS = BLESSINGS
            .comment("Puppeteer (dolphin): ticks out of water (rain counts as water) before you start drying out (vanilla 2400).")
            .defineInRange("puppeteerDolphinMoistureTicks", 2400, 20, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_DOLPHIN_INSPIRE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (dolphin): Inspire cooldown. Right-click: every player and water creature near you gets",
                    "Dolphin's Grace (much faster swimming).")
            .defineInRange("puppeteerDolphinInspireCooldownTicks", 300, 0, 72000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DOLPHIN_INSPIRE_RADIUS = BLESSINGS
            .comment("Puppeteer (dolphin): reach of Inspire (blocks).")
            .defineInRange("puppeteerDolphinInspireRadius", 12.0, 1.0, 64.0);
    public static final ModConfigSpec.IntValue PUPPETEER_DOLPHIN_INSPIRE_SECONDS = BLESSINGS
            .comment("Puppeteer (dolphin): how long Inspire's Dolphin's Grace lasts.")
            .defineInRange("puppeteerDolphinInspireSeconds", 8, 1, 600);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DOLPHIN_FISH_HEAL = BLESSINGS
            .comment("Puppeteer (dolphin): health a fish heals (swim into a dropped raw cod / salmon / ..., or someone feeds",
                    "you one by right-clicking you with it — what a real dolphin eats).")
            .defineInRange("puppeteerDolphinFishHeal", 4.0, 0.0, 40.0);

    // axolotl
    public static final ModConfigSpec.DoubleValue PUPPETEER_AXOLOTL_ATTACK = BLESSINGS
            .comment("Puppeteer (axolotl): damage of each bite (vanilla 2).")
            .defineInRange("puppeteerAxolotlAttackDamage", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_AXOLOTL_SWIM_BONUS = BLESSINGS
            .comment("Puppeteer (axolotl): extra swim speed. Endless air underwater; walks fine on land.")
            .defineInRange("puppeteerAxolotlSwimBonus", 0.5, 0.0, 5.0);
    public static final ModConfigSpec.IntValue PUPPETEER_AXOLOTL_MOISTURE_TICKS = BLESSINGS
            .comment("Puppeteer (axolotl): ticks out of water (rain counts) before you start drying out (vanilla 6000).")
            .defineInRange("puppeteerAxolotlMoistureTicks", 6000, 20, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_AXOLOTL_PLAY_DEAD_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (axolotl): HOLD right-click to play dead for as long as you like — you lie still, regenerate",
                    "fast, and every mob after you loses interest. This cooldown starts when you get back up.")
            .defineInRange("puppeteerAxolotlPlayDeadCooldownTicks", 240, 0, 72000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_AXOLOTL_PLAY_DEAD_REGEN = BLESSINGS
            .comment("Puppeteer (axolotl): health regenerated per second while playing dead.")
            .defineInRange("puppeteerAxolotlPlayDeadRegen", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_AXOLOTL_PLAY_DEAD_RADIUS = BLESSINGS
            .comment("Puppeteer (axolotl): mobs within this range that are after you lose interest when you play dead.")
            .defineInRange("puppeteerAxolotlPlayDeadRadius", 32.0, 1.0, 128.0);

    // phantom
    public static final ModConfigSpec.DoubleValue PUPPETEER_PHANTOM_FLY_SPEED = BLESSINGS
            .comment("Puppeteer (phantom): flight speed (double-tap jump, like the bat; vanilla creative is 0.05). No",
                    "sprint-flying — unless you have the Flight blessing, which unlocks a fast sprint glide",
                    "(puppeteerPhantomFlySprintSpeed), exactly as it does for the bat disguise. Burns in daylight.")
            .defineInRange("puppeteerPhantomFlySpeed", 0.04, 0.005, 0.5);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PHANTOM_FLY_SPRINT_SPEED = BLESSINGS
            .comment("Puppeteer (phantom): sprint-fly speed with the Flight blessing.")
            .defineInRange("puppeteerPhantomFlySprintSpeed", 0.09, 0.005, 1.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PHANTOM_BITE_DAMAGE = BLESSINGS
            .comment("Puppeteer (phantom): Bite damage — your normal left-click attack (vanilla phantom: 6).")
            .defineInRange("puppeteerPhantomBiteDamage", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_PHANTOM_DIVE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (phantom): Dive (right-click) cooldown. A fast free dive along where you look with only",
                    "limited steering — a skill shot: hit something and it takes puppeteerPhantomDiveDamage and you pull up.")
            .defineInRange("puppeteerPhantomDiveCooldownTicks", 100, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_PHANTOM_DIVE_TICKS = BLESSINGS
            .comment("Puppeteer (phantom): how long a dive lasts at most (it also ends on hitting something or the ground).")
            .defineInRange("puppeteerPhantomDiveTicks", 30, 1, 200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PHANTOM_DIVE_SPEED = BLESSINGS
            .comment("Puppeteer (phantom): dive speed (blocks per tick) — faster still the steeper you dive (up to 1.5x).")
            .defineInRange("puppeteerPhantomDiveSpeed", 0.9, 0.05, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PHANTOM_DIVE_TURN = BLESSINGS
            .comment("Puppeteer (phantom): how far the dive can turn towards your look, degrees per tick.")
            .defineInRange("puppeteerPhantomDiveTurnDegrees", 3.0, 0.0, 180.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PHANTOM_DIVE_DAMAGE = BLESSINGS
            .comment("Puppeteer (phantom): damage of a dive that connects (the skill-shot bonus over a bite).")
            .defineInRange("puppeteerPhantomDiveDamage", 12.0, 0.0, 80.0);

    // iron golem
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOLEM_SPEED = BLESSINGS
            .comment("Puppeteer (iron golem): movement speed multiplier (sprinting allowed).")
            .defineInRange("puppeteerGolemSpeedMultiplier", 0.9, 0.1, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOLEM_ATTACK = BLESSINGS
            .comment("Puppeteer (iron golem): damage of a fully charged swing (vanilla's attribute is 15). Swings use a",
                    "weapon-style cooldown (puppeteerGolemAttackSpeed) and spamming does less, exactly like a sword or axe.")
            .defineInRange("puppeteerGolemAttackDamage", 15.0, 0.0, 80.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOLEM_ATTACK_SPEED = BLESSINGS
            .comment("Puppeteer (iron golem): attacks per second at full charge (a sword is 1.6, an axe 0.8-1.0).")
            .defineInRange("puppeteerGolemAttackSpeed", 0.9, 0.1, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOLEM_REACH = BLESSINGS
            .comment("Puppeteer (iron golem): reach (blocks).")
            .defineInRange("puppeteerGolemReach", 3.0, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOLEM_FLING = BLESSINGS
            .comment("Puppeteer (iron golem): how hard a swing flings its target UP, on top of the hit's own knockback (vanilla",
                    "golem 0.4; applied at the end of the tick, since vanilla's knockback would otherwise flatten it — a touch",
                    "higher by default to make up for that tick). You take no knockback yourself. Monsters that hate golems",
                    "(zombies, skeletons, spiders, illagers, ravagers) hunt you like a real one.")
            .defineInRange("puppeteerGolemFling", 0.5, 0.0, 4.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GOLEM_POPPY_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (iron golem): Offer Poppy cooldown. HOLD right-click to hold a poppy out to whoever you're",
                    "looking at; held long enough, they get brief Strength and Resistance.")
            .defineInRange("puppeteerGolemPoppyCooldownTicks", 600, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_GOLEM_POPPY_HOLD_TICKS = BLESSINGS
            .comment("Puppeteer (iron golem): how long you hold the poppy out before it's taken.")
            .defineInRange("puppeteerGolemPoppyHoldTicks", 20, 1, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_GOLEM_POPPY_SECONDS = BLESSINGS
            .comment("Puppeteer (iron golem): how long the poppy's Strength I + Resistance I last.")
            .defineInRange("puppeteerGolemPoppySeconds", 10, 1, 600);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOLEM_IRON_HEAL = BLESSINGS
            .comment("Puppeteer (iron golem): health an iron ingot repairs when someone right-clicks you with it (vanilla 25).")
            .defineInRange("puppeteerGolemIronHeal", 25.0, 0.0, 200.0);

    // snow golem
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_SPEED = BLESSINGS
            .comment("Puppeteer (snow golem): movement speed multiplier. Leaves a snow trail (mobGriefing), melts in hot",
                    "biomes and is hurt by water / rain, like the real one.")
            .defineInRange("puppeteerSnowGolemSpeedMultiplier", 0.85, 0.1, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_ATTACK = BLESSINGS
            .comment("Puppeteer (snow golem): melee damage — next to nothing, but it chills (brief Slowness).")
            .defineInRange("puppeteerSnowGolemAttackDamage", 0.5, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_CHILL_TICKS = BLESSINGS
            .comment("Puppeteer (snow golem): Slowness I from a melee hit, in ticks.")
            .defineInRange("puppeteerSnowGolemChillTicks", 40, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_SNOWBALL_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (snow golem): tap right-click to throw a snowball that actually hurts. Its cooldown.")
            .defineInRange("puppeteerSnowGolemSnowballCooldownTicks", 10, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_SNOWBALL_DAMAGE = BLESSINGS
            .comment("Puppeteer (snow golem): a snowball's damage.")
            .defineInRange("puppeteerSnowGolemSnowballDamage", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_SNOWBALL_SLOW_LEVEL = BLESSINGS
            .comment("Puppeteer (snow golem): a snowball's Slowness level — significant, but brief (see ...SlowTicks).")
            .defineInRange("puppeteerSnowGolemSnowballSlowLevel", 3, 1, 10);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_SNOWBALL_SLOW_TICKS = BLESSINGS
            .comment("Puppeteer (snow golem): how long a snowball's Slowness lasts.")
            .defineInRange("puppeteerSnowGolemSnowballSlowTicks", 25, 1, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_VOLLEY_DURATION_TICKS = BLESSINGS
            .comment("Puppeteer (snow golem): the blizzard fires its salvos over this many ticks — each salvo a wide fan of",
                    "enhanced snowballs lobbed at mixed heights and speeds, so together they blanket a big area (it's for crowds).",
                    "You stay slowed while it pours out. Every snowball can hit — no hurt-cooldown.")
            .defineInRange("puppeteerSnowGolemVolleyDurationTicks", 24, 1, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_VOLLEY_SALVOS = BLESSINGS
            .comment("Puppeteer (snow golem): salvos in a blizzard (each fires puppeteerSnowGolemVolleyCount snowballs at once).")
            .defineInRange("puppeteerSnowGolemVolleySalvos", 12, 1, 64);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_VOLLEY_SPREAD = BLESSINGS
            .comment("Puppeteer (snow golem): how wide each salvo fans out (degrees, side to side).")
            .defineInRange("puppeteerSnowGolemVolleySpread", 80.0, 0.0, 180.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_VOLLEY_DAMAGE_CAP = BLESSINGS
            .comment("Puppeteer (snow golem): the most damage one blizzard can deal to any single target (direct hits and",
                    "splashes together; it still chills them). 0 = no cap.")
            .defineInRange("puppeteerSnowGolemVolleyDamageCap", 30.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_VOLLEY_SPLASH = BLESSINGS
            .comment("Puppeteer (snow golem): enhanced snowballs burst where they land — this much damage (and a chill) to",
                    "everything within puppeteerSnowGolemVolleySplashRadius, besides whatever they hit directly.")
            .defineInRange("puppeteerSnowGolemVolleySplash", 1.5, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_VOLLEY_SPLASH_RADIUS = BLESSINGS
            .comment("Puppeteer (snow golem): that burst's radius (blocks).")
            .defineInRange("puppeteerSnowGolemVolleySplashRadius", 1.75, 0.0, 8.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_VOLLEY_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (snow golem): HOLD right-click this long (you slow down while winding up) and let go to",
                    "fire a huge volley of bigger, better snowballs.")
            .defineInRange("puppeteerSnowGolemVolleyChargeTicks", 30, 5, 400);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_VOLLEY_SLOW = BLESSINGS
            .comment("Puppeteer (snow golem): how much slower you move while winding the volley up (0.6 = 60%).")
            .defineInRange("puppeteerSnowGolemVolleySlow", 0.6, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_VOLLEY_COUNT = BLESSINGS
            .comment("Puppeteer (snow golem): snowballs per salvo (all thrown at once).")
            .defineInRange("puppeteerSnowGolemVolleyCount", 30, 1, 128);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SNOW_GOLEM_VOLLEY_DAMAGE = BLESSINGS
            .comment("Puppeteer (snow golem): damage of an enhanced volley snowball's direct hit (they also chill harder: Slowness II).")
            .defineInRange("puppeteerSnowGolemVolleyDamage", 3.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SNOW_GOLEM_VOLLEY_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (snow golem): the volley's own cooldown.")
            .defineInRange("puppeteerSnowGolemVolleyCooldownTicks", 200, 0, 72000);

    // rabbit / killer rabbit
    public static final ModConfigSpec.DoubleValue PUPPETEER_RABBIT_HOP_SPEED = BLESSINGS
            .comment("Puppeteer (rabbit): you can only move by hopping — hold a movement key and you bound along in small,",
                    "vanilla-rabbit hops. Each hop's forward speed (blocks per tick). No move of its own; carrots and dandelions lure it.")
            .defineInRange("puppeteerRabbitHopSpeed", 0.3, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RABBIT_SPRINT_HOP = BLESSINGS
            .comment("Puppeteer (rabbit / killer rabbit): sprinting hops this much further — a rabbit bolting when it's hurt.",
                    "(You can also steer mid-hop, so you don't get stuck.)")
            .defineInRange("puppeteerRabbitSprintHopMultiplier", 1.6, 1.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RABBIT_HOP_LIFT = BLESSINGS
            .comment("Puppeteer (rabbit): how high a movement hop goes (upward speed; a normal jump is 0.42).")
            .defineInRange("puppeteerRabbitHopLift", 0.3, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RABBIT_JUMP_BOOST = BLESSINGS
            .comment("Puppeteer (rabbit): extra height on a real jump (the jump key), e.g. 0.4 = +40% jump strength. Fall",
                    "damage starts higher to match.")
            .defineInRange("puppeteerRabbitJumpBoost", 0.4, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_KILLER_RABBIT_HOP_SPEED = BLESSINGS
            .comment("Puppeteer (killer rabbit — the Caerbannog variant): built for the chase — long, low, distance-eating hops.")
            .defineInRange("puppeteerKillerRabbitHopSpeed", 1.0, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_KILLER_RABBIT_HOP_LIFT = BLESSINGS
            .comment("Puppeteer (killer rabbit): how high a movement hop goes (kept low — it's all distance).")
            .defineInRange("puppeteerKillerRabbitHopLift", 0.32, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_KILLER_RABBIT_JUMP_BOOST = BLESSINGS
            .comment("Puppeteer (killer rabbit): extra height on a real jump (the jump key).")
            .defineInRange("puppeteerKillerRabbitJumpBoost", 1.0, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_KILLER_RABBIT_ATTACK = BLESSINGS
            .comment("Puppeteer (killer rabbit): melee (left-click) damage — huge (vanilla's killer bunny does 8).")
            .defineInRange("puppeteerKillerRabbitAttackDamage", 18.0, 0.0, 80.0);
    public static final ModConfigSpec.IntValue PUPPETEER_KILLER_RABBIT_MAUL_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (killer rabbit): Maul (right-click) cooldown. You lunge a huge distance; the first living thing",
                    "you hit stops you, and the two of you are locked in a frenzied scrap (both stuck) while it takes",
                    "puppeteerKillerRabbitMaulHits hits.")
            .defineInRange("puppeteerKillerRabbitMaulCooldownTicks", 60, 0, 72000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_KILLER_RABBIT_MAUL_SPEED = BLESSINGS
            .comment("Puppeteer (killer rabbit): Maul lunge speed (blocks per tick).")
            .defineInRange("puppeteerKillerRabbitMaulSpeed", 1.6, 0.1, 5.0);
    public static final ModConfigSpec.IntValue PUPPETEER_KILLER_RABBIT_MAUL_TICKS = BLESSINGS
            .comment("Puppeteer (killer rabbit): how long the Maul lunge flies before giving up.")
            .defineInRange("puppeteerKillerRabbitMaulTicks", 14, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_KILLER_RABBIT_MAUL_HITS = BLESSINGS
            .comment("Puppeteer (killer rabbit): hits in the scrap.")
            .defineInRange("puppeteerKillerRabbitMaulHits", 8, 1, 50);
    public static final ModConfigSpec.IntValue PUPPETEER_KILLER_RABBIT_MAUL_HIT_INTERVAL = BLESSINGS
            .comment("Puppeteer (killer rabbit): ticks between hits in the scrap.")
            .defineInRange("puppeteerKillerRabbitMaulHitInterval", 4, 1, 40);
    public static final ModConfigSpec.DoubleValue PUPPETEER_KILLER_RABBIT_MAUL_DAMAGE = BLESSINGS
            .comment("Puppeteer (killer rabbit): damage per hit in the scrap (8 hits x 3.5 = 28 by default).")
            .defineInRange("puppeteerKillerRabbitMaulDamage", 3.5, 0.0, 40.0);

    // silverfish
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_EMBED_TICKS = BLESSINGS
            .comment("Puppeteer (silverfish): right-click while looking at a stone-type block (one that can be infested) to",
                    "Embed: you burrow in over this many ticks, then you're hidden inside it (you can still look out).",
                    "Right-click again — any time — to BURST out. Not looking at stone, right-click calls reinforcements.")
            .defineInRange("puppeteerSilverfishEmbedTicks", 30, 1, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_BROOD_TICKS = BLESSINGS
            .comment("Puppeteer (silverfish): every this many ticks hidden, one more silverfish bursts out with you.")
            .defineInRange("puppeteerSilverfishBroodTicks", 40, 1, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_BROOD_MAX = BLESSINGS
            .comment("Puppeteer (silverfish): the most that can burst out with you (not counting you).")
            .defineInRange("puppeteerSilverfishBroodMax", 9, 0, 32);
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_BURST_SPEED_TICKS = BLESSINGS
            .comment("Puppeteer (silverfish): bursting out gives you and the brood Speed II for this long.")
            .defineInRange("puppeteerSilverfishBurstSpeedTicks", 60, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_CALL_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (silverfish): Call reinforcements cooldown — every infested block nearby breaks open and its",
                    "silverfish join you (vanilla's own wake-up; needs mobGriefing), and nearby silverfish go for what you're looking at.")
            .defineInRange("puppeteerSilverfishCallCooldownTicks", 100, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_CALL_INFEST = BLESSINGS
            .comment("Puppeteer (silverfish): Call also INFESTS up to this many stone-type blocks near you (seeding them for the",
                    "next call). Silverfish also rally like the zombies (no glow): whatever you bite, the silverfish near you",
                    "go for; and bursting out of stone sends the brood at the closest mob.")
            .defineInRange("puppeteerSilverfishCallInfest", 6, 0, 64);
    public static final ModConfigSpec.IntValue PUPPETEER_SILVERFISH_CALL_RADIUS = BLESSINGS
            .comment("Puppeteer (silverfish): how far Call reaches (blocks; half that up and down).")
            .defineInRange("puppeteerSilverfishCallRadius", 10, 1, 32);

    // enderman
    public static final ModConfigSpec.DoubleValue PUPPETEER_ENDERMAN_ATTACK = BLESSINGS
            .comment("Puppeteer (enderman): melee damage (vanilla 7). Endermen you hit turn on you.")
            .defineInRange("puppeteerEndermanAttackDamage", 7.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ENDERMAN_TELEPORT_RANGE = BLESSINGS
            .comment("Puppeteer (enderman): right-click is a smart teleport to the block you're looking at, within this many",
                    "blocks — biased to land you on solid ground (it looks down from there for a floor). Crouch + right-click a",
                    "block picks it up / puts it down (crouch + right-click at nothing still leaves the puppet).")
            .defineInRange("puppeteerEndermanTeleportRange", 22.4, 2.0, 64.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ENDERMAN_TELEPORT_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (enderman): teleport recharge (one charge).")
            .defineInRange("puppeteerEndermanTeleportCooldownTicks", 80, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_ENDERMAN_RAGE_SECONDS = BLESSINGS
            .comment("Puppeteer (enderman): anyone who looks you in the eye ENRAGES you for this long (refreshed while they",
                    "stare): Speed, two teleport charges with a faster recharge, your jaw drops and you shake — and, for you",
                    "alone, whoever looked at you glows.")
            .defineInRange("puppeteerEndermanRageSeconds", 10, 1, 600);
    public static final ModConfigSpec.IntValue PUPPETEER_ENDERMAN_RAGE_CHARGES = BLESSINGS
            .comment("Puppeteer (enderman): teleport charges while enraged.")
            .defineInRange("puppeteerEndermanRageCharges", 2, 1, 10);
    public static final ModConfigSpec.IntValue PUPPETEER_ENDERMAN_RAGE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (enderman): teleport recharge per charge while enraged.")
            .defineInRange("puppeteerEndermanRageCooldownTicks", 40, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_ENDERMAN_RAGE_SPEED = BLESSINGS
            .comment("Puppeteer (enderman): Speed level while enraged (0 = none).")
            .defineInRange("puppeteerEndermanRageSpeed", 2, 0, 10);
    public static final ModConfigSpec.BooleanValue PUPPETEER_ENDERMAN_DODGE_PROJECTILES = BLESSINGS
            .comment("Puppeteer (enderman): like the real thing, arrows and other projectiles never hit you — you blink away",
                    "a short distance instead (free, no charge). Water and rain hurt you, too.")
            .define("puppeteerEndermanDodgeProjectiles", true);

    // horse / donkey / mule
    public static final ModConfigSpec.DoubleValue PUPPETEER_HORSE_SPEED = BLESSINGS
            .comment("Puppeteer (horse): movement speed multiplier — controls like riding one (sprint, HOLD jump to charge a",
                    "leap, steps up full blocks, sinks rather than swims), but you ARE the horse. Other players right-click to",
                    "ride (no taming, no saddle needed; you steer). Saddles and horse armour can be put on you.")
            .defineInRange("puppeteerHorseSpeedMultiplier", 1.76, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HORSE_JUMP = BLESSINGS
            .comment("Puppeteer (horse): a fully charged leap's strength (a horse's jump_strength: 0.7 ≈ 3 blocks, 1.0 ≈ 5).")
            .defineInRange("puppeteerHorseJumpStrength", 0.85, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DONKEY_SPEED = BLESSINGS
            .comment("Puppeteer (donkey / mule): movement speed multiplier — a bit slower than a horse. Others can strap a chest",
                    "on you, then crouch + right-click to open it.")
            .defineInRange("puppeteerDonkeySpeedMultiplier", 1.48, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_DONKEY_JUMP = BLESSINGS
            .comment("Puppeteer (donkey / mule): a fully charged leap's strength.")
            .defineInRange("puppeteerDonkeyJumpStrength", 0.65, 0.1, 2.0);
    public static final ModConfigSpec.IntValue PUPPETEER_HORSE_JUMP_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (horses): ticks of holding jump for a full-strength leap (vanilla's jump bar fills in about 10).")
            .defineInRange("puppeteerHorseJumpChargeTicks", 10, 1, 100);

    // slime / magma cube
    public static final ModConfigSpec.DoubleValue PUPPETEER_SLIME_HOP_SPEED = BLESSINGS
            .comment("Puppeteer (slime): you get about by bouncing — a movement key on the ground launches a hop (steerable",
                    "mid-air). Its forward speed, +10% per size step (small 1, medium 2, big 4). No fall damage. When it",
                    "\"dies\" a big or medium one splits like the real thing and you carry on as one of the offspring.")
            .defineInRange("puppeteerSlimeHopSpeed", 0.3, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SLIME_HOP_LIFT = BLESSINGS
            .comment("Puppeteer (slime): how high a bounce goes (upward speed; a normal jump is 0.42).")
            .defineInRange("puppeteerSlimeHopLift", 0.42, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_MAGMA_CUBE_HOP_SPEED = BLESSINGS
            .comment("Puppeteer (magma cube): its bounce's forward speed (as the slime). Fire and lava proof.")
            .defineInRange("puppeteerMagmaCubeHopSpeed", 0.3, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_MAGMA_CUBE_HOP_LIFT = BLESSINGS
            .comment("Puppeteer (magma cube): its bounce's height — they jump higher than slimes, +0.1 per size step like vanilla.")
            .defineInRange("puppeteerMagmaCubeHopLift", 0.5, 0.05, 2.0);

    public static final ModConfigSpec.IntValue PUPPETEER_SLIME_HOP_PAUSE = BLESSINGS
            .comment("Puppeteer (slime / magma cube): ticks you sit squashed on the ground between bounces — the real slime's",
                    "boing... boing rhythm (vanilla waits far longer; this keeps it playable).")
            .defineInRange("puppeteerSlimeHopPauseTicks", 5, 0, 40);

    // llama / trader llama
    public static final ModConfigSpec.DoubleValue PUPPETEER_LLAMA_SPEED = BLESSINGS
            .comment("Puppeteer (llama / trader llama): movement speed multiplier (a llama's pace). Right-click spits — about",
                    "as much use as the real llama's spit (1 damage).")
            .defineInRange("puppeteerLlamaSpeedMultiplier", 1.3, 0.1, 6.0);
    public static final ModConfigSpec.IntValue PUPPETEER_LLAMA_SPIT_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (llama): ticks between spits.")
            .defineInRange("puppeteerLlamaSpitCooldownTicks", 20, 0, 1200);

    // ghast
    public static final ModConfigSpec.DoubleValue PUPPETEER_GHAST_FLY_SPEED = BLESSINGS
            .comment("Puppeteer (ghast): you're always flying, slowly (a ghast's drift; vanilla creative is 0.05). It can never",
                    "sprint-fly (the Flight blessing just nudges its speed: puppeteerGhastFlySprintSpeed). Your hitbox is the",
                    "ghast's full 4x4x4, so you need its room. Fire and lava proof.")
            .defineInRange("puppeteerGhastFlySpeed", 0.012, 0.002, 0.5);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GHAST_FLY_SPRINT_SPEED = BLESSINGS
            .comment("Puppeteer (ghast): its fly speed with the Flight blessing — a little quicker, but NEVER a sprint (a slow",
                    "flier can't sprint-fly at all).")
            .defineInRange("puppeteerGhastFlySprintSpeed", 0.016, 0.002, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GHAST_FIREBALL_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (ghast): right-click spits a single fireball (the ghast's own: its blast power, its open mouth",
                    "and cry as it fires). Its cooldown.")
            .defineInRange("puppeteerGhastFireballCooldownTicks", 60, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (ghast): HOLD left-click this long to charge the volley (you slow, shake and your view narrows),",
                    "then let go: a flamethrower-like stream of fireballs, one after another with a little scatter.",
                    "Let go before it's charged and nothing happens.")
            .defineInRange("puppeteerGhastVolleyChargeTicks", 25, 1, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_GHAST_VOLLEY_COUNT = BLESSINGS
            .comment("Puppeteer (ghast): fireballs in the volley.")
            .defineInRange("puppeteerGhastVolleyCount", 5, 1, 32);
    public static final ModConfigSpec.IntValue PUPPETEER_GHAST_VOLLEY_INTERVAL_TICKS = BLESSINGS
            .comment("Puppeteer (ghast): ticks between the volley's fireballs.")
            .defineInRange("puppeteerGhastVolleyIntervalTicks", 2, 1, 40);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GHAST_VOLLEY_SCATTER = BLESSINGS
            .comment("Puppeteer (ghast): how far each volley fireball strays from your aim (degrees).")
            .defineInRange("puppeteerGhastVolleyScatter", 2.5, 0.0, 45.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GHAST_VOLLEY_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (ghast): the volley's own cooldown.")
            .defineInRange("puppeteerGhastVolleyCooldownTicks", 120, 0, 72000);

    // blaze
    public static final ModConfigSpec.DoubleValue PUPPETEER_BLAZE_FLY_SPEED = BLESSINGS
            .comment("Puppeteer (blaze): free flight (double-tap jump), never sprint-flying (the Flight blessing just nudges",
                    "its speed: puppeteerBlazeFlySprintSpeed). Out of flight it drifts down slowly, like the real blaze. Water, rain and",
                    "snowballs hurt it; it's fire proof, and it lights up while it's in a fight.")
            .defineInRange("puppeteerBlazeFlySpeed", 0.03, 0.002, 0.5);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BLAZE_FLY_SPRINT_SPEED = BLESSINGS
            .comment("Puppeteer (blaze): its fly speed with the Flight blessing — a little quicker, never a sprint.")
            .defineInRange("puppeteerBlazeFlySprintSpeed", 0.04, 0.002, 1.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BLAZE_ATTACK = BLESSINGS
            .comment("Puppeteer (blaze): melee damage (vanilla 6).")
            .defineInRange("puppeteerBlazeAttackDamage", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_WINDUP_TICKS = BLESSINGS
            .comment("Puppeteer (blaze): HOLD right-click to wind up an enhanced fireball volley (you slow, shake, view narrows)",
                    "for at least this long; the longer you hold (up to puppeteerBlazeChargeTicks), the more fireballs. Just TAP",
                    "right-click instead for the real blaze's burst: puppeteerBlazeTapFireballs at its own pace.")
            .defineInRange("puppeteerBlazeWindupTicks", 8, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_TAP_FIREBALLS = BLESSINGS
            .comment("Puppeteer (blaze): fireballs in a tapped burst (vanilla's blaze: 3, six ticks apart, at its normal speed).")
            .defineInRange("puppeteerBlazeTapFireballs", 3, 1, 16);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (blaze): a full charge — the most fireballs.")
            .defineInRange("puppeteerBlazeChargeTicks", 40, 1, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_MIN_FIREBALLS = BLESSINGS
            .comment("Puppeteer (blaze): fireballs at the minimum wind-up.")
            .defineInRange("puppeteerBlazeMinFireballs", 3, 1, 32);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_MAX_FIREBALLS = BLESSINGS
            .comment("Puppeteer (blaze): fireballs at a full charge.")
            .defineInRange("puppeteerBlazeMaxFireballs", 6, 1, 32);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_VOLLEY_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (blaze): the volley's cooldown.")
            .defineInRange("puppeteerBlazeVolleyCooldownTicks", 60, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_BLAZE_COMBAT_SECONDS = BLESSINGS
            .comment("Puppeteer (blaze): how long it stays lit up after hitting, being hit or firing.")
            .defineInRange("puppeteerBlazeCombatSeconds", 5, 0, 120);

    // breeze
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_FLY_SPEED = BLESSINGS
            .comment("Puppeteer (breeze): free flight like the blaze (never sprint-flying). No melee —",
                    "and anything hostile that gets too close pushes you away on the wind. Projectiles bounce off you, as off",
                    "the real breeze. Left-click: a wind charge; hold right-click: a gale.")
            .defineInRange("puppeteerBreezeFlySpeed", 0.035, 0.002, 0.5);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_FLY_SPRINT_SPEED = BLESSINGS
            .comment("Puppeteer (breeze): its fly speed with the Flight blessing — a little quicker, never a sprint.")
            .defineInRange("puppeteerBreezeFlySprintSpeed", 0.045, 0.002, 1.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_REPEL_RANGE = BLESSINGS
            .comment("Puppeteer (breeze): hostile mobs closer than this push you away (gently — less than an animal's lure).")
            .defineInRange("puppeteerBreezeRepelRange", 3.0, 0.0, 16.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_REPEL_STRENGTH = BLESSINGS
            .comment("Puppeteer (breeze): that push, per tick.")
            .defineInRange("puppeteerBreezeRepelStrength", 0.04, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_BREEZE_SHOT_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (breeze): left-click wind charge cooldown (the real breeze's wind charge: knockback, triggers",
                    "doors / buttons / redstone, the gust).")
            .defineInRange("puppeteerBreezeShotCooldownTicks", 15, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_BREEZE_GALE_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (breeze): HOLD right-click this long for a full gale (you slow, view narrows); let go to fire one",
                    "slower, far stronger wind charge — bigger the longer you held (half-charged at least, or it fizzles).")
            .defineInRange("puppeteerBreezeGaleChargeTicks", 30, 1, 400);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_GALE_RADIUS = BLESSINGS
            .comment("Puppeteer (breeze): a full gale's blast radius (a wind charge's is 3).")
            .defineInRange("puppeteerBreezeGaleRadius", 7.5, 1.0, 16.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_GALE_KNOCKBACK = BLESSINGS
            .comment("Puppeteer (breeze): a full gale's knockback multiplier (a wind charge is 1.0).")
            .defineInRange("puppeteerBreezeGaleKnockback", 3.06, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_BREEZE_GALE_DAMAGE = BLESSINGS
            .comment("Puppeteer (breeze): a gale's direct-hit damage (a wind charge does 1).")
            .defineInRange("puppeteerBreezeGaleDamage", 5.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_BREEZE_GALE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (breeze): the gale's cooldown.")
            .defineInRange("puppeteerBreezeGaleCooldownTicks", 140, 0, 72000);

    // witch
    public static final ModConfigSpec.IntValue PUPPETEER_WITCH_THROW_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (witch): your hotbar becomes her potion belt (scroll to pick). Right-click throws the picked potion",
                    "as a splash potion; hold right-click to brew it into a lingering potion; left-click drinks it (the witch's own",
                    "swig: 1.6s, slowed). Cooldown after a splash throw (all three share the belt's cooldown sweep).")
            .defineInRange("puppeteerWitchThrowCooldownTicks", 30, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_WITCH_LINGER_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (witch): hold right-click this long to throw a lingering potion instead (let go sooner: a splash).")
            .defineInRange("puppeteerWitchLingerChargeTicks", 25, 1, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_WITCH_LINGER_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (witch): cooldown after a lingering potion.")
            .defineInRange("puppeteerWitchLingerCooldownTicks", 120, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_WITCH_DRINK_TICKS = BLESSINGS
            .comment("Puppeteer (witch): how long drinking takes (vanilla 32); the potion works when you finish.")
            .defineInRange("puppeteerWitchDrinkTicks", 32, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_WITCH_DRINK_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (witch): cooldown after drinking.")
            .defineInRange("puppeteerWitchDrinkCooldownTicks", 60, 0, 72000);

    // camel
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAMEL_SPEED = BLESSINGS
            .comment("Puppeteer (camel): movement speed multiplier — a nimble ride (steps up 1.5 blocks; the jump key jumps as",
                    "normal). Other players right-click to ride (you steer). HOLD right-click to charge a dash, like a horse's",
                    "jump bar; let go to dash.")
            .defineInRange("puppeteerCamelSpeedMultiplier", 1.4, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAMEL_DASH_BOOST = BLESSINGS
            .comment("Puppeteer (camel): the dash, relative to the real camel's (1.05 = a touch stronger).")
            .defineInRange("puppeteerCamelDashBoost", 1.05, 0.1, 5.0);
    public static final ModConfigSpec.IntValue PUPPETEER_CAMEL_DASH_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (camel): ticks of holding for a full dash (a tap is a 40% dash, like a horse's jump).")
            .defineInRange("puppeteerCamelDashChargeTicks", 10, 1, 100);
    public static final ModConfigSpec.IntValue PUPPETEER_CAMEL_DASH_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (camel): dash cooldown (vanilla 55).")
            .defineInRange("puppeteerCamelDashCooldownTicks", 55, 0, 1200);

    // goat
    public static final ModConfigSpec.IntValue PUPPETEER_GOAT_RAM_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (goat): HOLD right-click to lower your head and charge a ram — the longer, the further, faster and",
                    "harder it goes (this many ticks for a full charge). It does NO damage, just sends whatever it hits flying the",
                    "way you were going. Good for getting about, too. Goats fall further unhurt.")
            .defineInRange("puppeteerGoatRamChargeTicks", 40, 1, 400);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOAT_RAM_SPEED = BLESSINGS
            .comment("Puppeteer (goat): a full ram's speed (blocks per tick; a tap gets about a third of it).")
            .defineInRange("puppeteerGoatRamSpeed", 1.3, 0.1, 5.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GOAT_RAM_TICKS = BLESSINGS
            .comment("Puppeteer (goat): a full ram's length (ticks; a tap is about a third).")
            .defineInRange("puppeteerGoatRamTicks", 24, 1, 200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOAT_RAM_KNOCKBACK = BLESSINGS
            .comment("Puppeteer (goat): a full ram's knockback (blocks per tick launched — absurd on purpose; a tap is about a third).")
            .defineInRange("puppeteerGoatRamKnockback", 4.5, 0.0, 20.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GOAT_RAM_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (goat): ram cooldown.")
            .defineInRange("puppeteerGoatRamCooldownTicks", 40, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_GOAT_SHRIEK_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (screaming goat): HOLD left-click to wind up a SHRIEK — it only goes off fully charged (this many",
                    "ticks). A warden-style sonic boom that goes straight through walls, does no damage, and blasts everything",
                    "in its path away with one insanely high knockback — with the goat's scream.")
            .defineInRange("puppeteerGoatShriekChargeTicks", 40, 1, 400);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOAT_SHRIEK_RANGE = BLESSINGS
            .comment("Puppeteer (screaming goat): the shriek's reach (blocks, through anything).")
            .defineInRange("puppeteerGoatShriekRange", 20.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GOAT_SHRIEK_KNOCKBACK = BLESSINGS
            .comment("Puppeteer (screaming goat): the shriek's knockback (blocks per tick launched).")
            .defineInRange("puppeteerGoatShriekKnockback", 21.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue PUPPETEER_GOAT_SHRIEK_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (screaming goat): shriek cooldown.")
            .defineInRange("puppeteerGoatShriekCooldownTicks", 600, 0, 72000);

    // pillager
    public static final ModConfigSpec.DoubleValue PUPPETEER_PILLAGER_BOLT_DAMAGE = BLESSINGS
            .comment("Puppeteer (pillager): your hotbar is crossbows (real ones: right-click to load, right-click to fire, every",
                    "crossbow blessing works) with a bottomless quiver. No melee. Its bolts hit this much harder (1.25 = +25%).")
            .defineInRange("puppeteerPillagerBoltDamageMultiplier", 1.25, 0.1, 10.0);

    // fox
    public static final ModConfigSpec.DoubleValue PUPPETEER_FOX_SPRINT_BONUS = BLESSINGS
            .comment("Puppeteer (fox): extra speed while sprinting, on top of the normal sprint (0.2 = +20%). Crouching makes you",
                    "completely silent. You pick items up in your mouth (one at a time — the drop key lets go of it) and eat",
                    "food you're holding after a moment.")
            .defineInRange("puppeteerFoxSprintBonus", 0.2, 0.0, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_FOX_EAT_TICKS = BLESSINGS
            .comment("Puppeteer (fox): ticks you hold food in your mouth before eating it (0 = never eat).")
            .defineInRange("puppeteerFoxEatTicks", 60, 0, 1200);
    public static final ModConfigSpec.IntValue PUPPETEER_FOX_POUNCE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (fox): Pounce (right-click) cooldown. A short rush: the first player or mob you reach that's",
                    "holding something loses it to you (whatever's in your mouth is dropped for it). Nobody to rob: you bite the first thing you reach.")
            .defineInRange("puppeteerFoxPounceCooldownTicks", 160, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_FOX_POUNCE_TICKS = BLESSINGS
            .comment("Puppeteer (fox): how long the pounce rushes for.")
            .defineInRange("puppeteerFoxPounceTicks", 8, 1, 100);
    public static final ModConfigSpec.DoubleValue PUPPETEER_FOX_POUNCE_SPEED = BLESSINGS
            .comment("Puppeteer (fox): the pounce's speed (blocks per tick).")
            .defineInRange("puppeteerFoxPounceSpeed", 0.9, 0.1, 4.0);

    // vindicator
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_SPEED = BLESSINGS
            .comment("Puppeteer (vindicator): movement speed multiplier — a brute. A vindicator named Johnny is faster still",
                    "(puppeteerJohnnyBonus), hits harder and faster, sparks on every hit — and can't help attacking anything near.")
            .defineInRange("puppeteerVindicatorSpeedMultiplier", 1.35, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_ATTACK = BLESSINGS
            .comment("Puppeteer (vindicator): damage of a fully charged swing (weapon-style cooldown, like the iron golem's;",
                    "the axe's enchantments add on).")
            .defineInRange("puppeteerVindicatorAttackDamage", 11.0, 0.0, 80.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_ATTACK_SPEED = BLESSINGS
            .comment("Puppeteer (vindicator): attacks per second at full charge (an axe is 0.8-1.0).")
            .defineInRange("puppeteerVindicatorAttackSpeed", 1.15, 0.1, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_JOHNNY_BONUS = BLESSINGS
            .comment("Puppeteer (Johnny): how much more speed and damage Johnny gets (0.3 = +30%).")
            .defineInRange("puppeteerJohnnyBonus", 0.3, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_JOHNNY_ATTACK_SPEED = BLESSINGS
            .comment("Puppeteer (Johnny): attacks per second at full charge.")
            .defineInRange("puppeteerJohnnyAttackSpeed", 1.6, 0.1, 4.0);
    public static final ModConfigSpec.IntValue PUPPETEER_VINDICATOR_LUNGE_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (vindicator): ticks to fully charge the committed Lunge (hold right-click); a tap lunges weakly.")
            .defineInRange("puppeteerVindicatorLungeChargeTicks", 20, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_VINDICATOR_LUNGE_TICKS = BLESSINGS
            .comment("Puppeteer (vindicator): how long the forward lunge dash lasts at full charge (ticks) — its reach.")
            .defineInRange("puppeteerVindicatorLungeTicks", 9, 1, 60);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_LUNGE_SPEED = BLESSINGS
            .comment("Puppeteer (vindicator): forward speed of the lunge dash at full charge (blocks/tick) — its reach.")
            .defineInRange("puppeteerVindicatorLungeSpeed", 1.0, 0.1, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_LUNGE_CHARGE_SLOW = BLESSINGS
            .comment("Puppeteer (vindicator): how much you slow while WINDING UP the lunge (0.2 = -20%). Kept light so",
                    "sprinting into the lunge doesn't feel clunky; the commitment is the dash + the miss recovery, not a crawl.")
            .defineInRange("puppeteerVindicatorLungeChargeSlow", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_LUNGE_BONUS_MIN = BLESSINGS
            .comment("Puppeteer (vindicator): the lunge's damage multiplier at a bare tap (x the vindicator's base hit).")
            .defineInRange("puppeteerVindicatorLungeBonusMin", 1.5, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VINDICATOR_LUNGE_BONUS_MAX = BLESSINGS
            .comment("Puppeteer (vindicator): the lunge's damage multiplier at full charge.")
            .defineInRange("puppeteerVindicatorLungeBonusMax", 3.0, 1.0, 20.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_JOHNNY_LUNGE_BONUS_MAX = BLESSINGS
            .comment("Puppeteer (Johnny): a possessed Johnny's lunge is enhanced — this replaces the full-charge damage multiplier.")
            .defineInRange("puppeteerJohnnyLungeBonusMax", 4.5, 1.0, 20.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_JOHNNY_LUNGE_REACH = BLESSINGS
            .comment("Puppeteer (Johnny): multiplier on his lunge's dash speed and length (1.4 = 40% further and faster).")
            .defineInRange("puppeteerJohnnyLungeReach", 1.4, 1.0, 4.0);
    public static final ModConfigSpec.IntValue PUPPETEER_VINDICATOR_LUNGE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (vindicator): cooldown after a lunge that CONNECTS (ticks).")
            .defineInRange("puppeteerVindicatorLungeCooldownTicks", 40, 0, 600);
    public static final ModConfigSpec.IntValue PUPPETEER_VINDICATOR_LUNGE_RECOVERY_TICKS = BLESSINGS
            .comment("Puppeteer (vindicator): the commit — a MISSED lunge locks you out of swinging/lunging for this many ticks.")
            .defineInRange("puppeteerVindicatorLungeRecoveryTicks", 12, 0, 200);

    // wolf
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_SPEED = BLESSINGS
            .comment("Puppeteer (wolf): movement speed multiplier.")
            .defineInRange("puppeteerWolfSpeedMultiplier", 1.15, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_ATTACK = BLESSINGS
            .comment("Puppeteer (wolf): bite damage (swings pounce, like a real wolf).")
            .defineInRange("puppeteerWolfAttackDamage", 4.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_SCENT_RADIUS = BLESSINGS
            .comment("Puppeteer (wolf): how far (blocks) its always-on scent sense tracks living mobs — a limited Bloodhound.")
            .defineInRange("puppeteerWolfScentRadius", 16, 1, 64);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_SCENT_INTERVAL = BLESSINGS
            .comment("Puppeteer (wolf): ticks between scent-trail samples.")
            .defineInRange("puppeteerWolfScentInterval", 6, 1, 40);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_SCENT_LINGER_TICKS = BLESSINGS
            .comment("Puppeteer (wolf): how long each scent footprint lingers before fading (ticks).")
            .defineInRange("puppeteerWolfScentLingerTicks", 80, 20, 600);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_FEED_HEAL = BLESSINGS
            .comment("Puppeteer (wolf): health restored when another player feeds the wolf its food (meat), like a real wolf.")
            .defineInRange("puppeteerWolfFeedHeal", 4.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_FRENZY_TICKS = BLESSINGS
            .comment("Puppeteer (wolf): how long the rabid Frenzy lasts after being hit (refreshed by each hit). 200 = 10s.")
            .defineInRange("puppeteerWolfFrenzyTicks", 200, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_FRENZY_ATTACK_SPEED = BLESSINGS
            .comment("Puppeteer (wolf): extra attack speed while frenzied (1.0 = +100%, biting much faster in pursuit).")
            .defineInRange("puppeteerWolfFrenzyAttackSpeed", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_FRENZY_DAMAGE = BLESSINGS
            .comment("Puppeteer (wolf): bite-damage multiplier while frenzied (1.6 = +60%).")
            .defineInRange("puppeteerWolfFrenzyDamage", 1.6, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_POUNCE_POWER = BLESSINGS
            .comment("Puppeteer (wolf): how far a swing's pounce throws you forward — bigger than a spider's leap so you can actually close on prey.")
            .defineInRange("puppeteerWolfPouncePower", 1.1, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_POUNCE_LIFT = BLESSINGS
            .comment("Puppeteer (wolf): and how high the pounce lifts you.")
            .defineInRange("puppeteerWolfPounceLift", 0.42, 0.0, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_MAUL_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (wolf): cooldown of the frenzy Maul (a swing while frenzied grabs + bursts, a tamer killer-bunny maul).")
            .defineInRange("puppeteerWolfMaulCooldownTicks", 60, 0, 600);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_MAUL_SPEED = BLESSINGS
            .comment("Puppeteer (wolf): the frenzy Maul's lunge speed (blocks/tick).")
            .defineInRange("puppeteerWolfMaulSpeed", 1.2, 0.1, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_MAUL_TICKS = BLESSINGS
            .comment("Puppeteer (wolf): the frenzy Maul's lunge length (ticks).")
            .defineInRange("puppeteerWolfMaulTicks", 12, 1, 40);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_MAUL_HITS = BLESSINGS
            .comment("Puppeteer (wolf): how many bites the frenzy Maul lands once it latches on (weaker than the killer bunny's).")
            .defineInRange("puppeteerWolfMaulHits", 5, 1, 30);
    public static final ModConfigSpec.DoubleValue PUPPETEER_WOLF_MAUL_DAMAGE = BLESSINGS
            .comment("Puppeteer (wolf): damage per bite in the frenzy Maul.")
            .defineInRange("puppeteerWolfMaulDamage", 2.5, 0.0, 20.0);
    public static final ModConfigSpec.IntValue PUPPETEER_WOLF_MAUL_HIT_INTERVAL = BLESSINGS
            .comment("Puppeteer (wolf): ticks between bites in the frenzy Maul.")
            .defineInRange("puppeteerWolfMaulHitInterval", 4, 1, 20);

    // cat
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAT_SPEED = BLESSINGS
            .comment("Puppeteer (cat): movement speed multiplier — cats are quick.")
            .defineInRange("puppeteerCatSpeedMultiplier", 1.3, 0.1, 6.0);
    public static final ModConfigSpec.IntValue PUPPETEER_CAT_MEOW_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (cat): cooldown of the left-click Meow.")
            .defineInRange("puppeteerCatMeowCooldownTicks", 20, 0, 200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAT_CREEPER_SCARE_RADIUS = BLESSINGS
            .comment("Puppeteer (cat): creepers within this radius deflate and flee, like a real cat scaring them off.")
            .defineInRange("puppeteerCatCreeperScareRadius", 8.0, 0.0, 32.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAT_SCARE_BACK = BLESSINGS
            .comment("Puppeteer (cat): how hard the Scare launches you BACKWARDS (blocks/tick).")
            .defineInRange("puppeteerCatScareBack", 0.85, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAT_SCARE_UP = BLESSINGS
            .comment("Puppeteer (cat): how hard the Scare launches you UP (blocks/tick) — a big startled leap.")
            .defineInRange("puppeteerCatScareUp", 1.1, 0.0, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_CAT_SCARE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (cat): cooldown of the Scare (right-click, or forced by a nearby loud bang). 120 = 6s.")
            .defineInRange("puppeteerCatScareCooldownTicks", 120, 0, 600);
    public static final ModConfigSpec.DoubleValue PUPPETEER_CAT_SCARE_LOUD_RADIUS = BLESSINGS
            .comment("Puppeteer (cat): a loud bang — an explosion or firework — within this radius FORCES the Scare (respecting its cooldown).")
            .defineInRange("puppeteerCatScareLoudRadius", 10.0, 0.0, 48.0);

    // ravager
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_SPEED = BLESSINGS
            .comment("Puppeteer (ravager): movement-speed multiplier — fast, for running an attacker down like a real ravager.")
            .defineInRange("puppeteerRavagerSpeedMultiplier", 1.35, 0.1, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_ATTACK = BLESSINGS
            .comment("Puppeteer (ravager): bite damage (its head pokes out to land the hit a moment after the swing).")
            .defineInRange("puppeteerRavagerAttackDamage", 12.0, 0.0, 60.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_REACH = BLESSINGS
            .comment("Puppeteer (ravager): how far it can bite (blocks) — a long neck.")
            .defineInRange("puppeteerRavagerReach", 3.5, 0.5, 8.0);
    public static final ModConfigSpec.IntValue PUPPETEER_RAVAGER_BITE_DELAY_TICKS = BLESSINGS
            .comment("Puppeteer (ravager): ticks between the swing and the bite actually landing — the head-poke delay (vanilla ravagers bite on a wind-up).")
            .defineInRange("puppeteerRavagerBiteDelayTicks", 6, 0, 20);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_BITE_KNOCKUP = BLESSINGS
            .comment("Puppeteer (ravager): how hard the bite tosses its victim up and back, like a real ravager's hit.")
            .defineInRange("puppeteerRavagerBiteKnockup", 0.5, 0.0, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_RAVAGER_ROAR_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (ravager): hold right-click to charge the knockback ROAR; this many ticks is a full charge (your view shakes + zooms as it builds).")
            .defineInRange("puppeteerRavagerRoarChargeTicks", 40, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_RAVAGER_ROAR_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (ravager): cooldown of the roar.")
            .defineInRange("puppeteerRavagerRoarCooldownTicks", 120, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_ROAR_RADIUS = BLESSINGS
            .comment("Puppeteer (ravager): the roar's radius at a full charge (scales down with a shorter charge).")
            .defineInRange("puppeteerRavagerRoarRadius", 7.0, 1.0, 16.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_ROAR_KNOCKBACK = BLESSINGS
            .comment("Puppeteer (ravager): how hard the roar flings everything around you away (at a full charge).")
            .defineInRange("puppeteerRavagerRoarKnockback", 2.6, 0.0, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_ROAR_DAMAGE = BLESSINGS
            .comment("Puppeteer (ravager): damage the roar deals to everything it flings (at a full charge). Raiders (pillagers etc.) are never hurt.")
            .defineInRange("puppeteerRavagerRoarDamage", 9.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_RAVAGER_ROAR_STUN_TICKS = BLESSINGS
            .comment("Puppeteer (ravager): how long the roar leaves caught mobs/players reeling (Slowness).")
            .defineInRange("puppeteerRavagerRoarStunTicks", 50, 0, 200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_RAVAGER_RALLY_RADIUS = BLESSINGS
            .comment("Puppeteer (ravager): nearby pillagers/illagers within this range soft-rally onto whatever you bite (no glow, low priority).")
            .defineInRange("puppeteerRavagerRallyRadius", 20.0, 0.0, 64.0);

    // vex
    public static final ModConfigSpec.IntValue PUPPETEER_VEX_DURATION_TICKS = BLESSINGS
            .comment("Puppeteer (vex): how long you can stay a vex before it dissolves and releases you (an action-bar timer counts it down). 600 = 30s.")
            .defineInRange("puppeteerVexDurationTicks", 600, 100, 24000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VEX_MAX_HARDNESS = BLESSINGS
            .comment("Puppeteer (vex): the hardest block it can phase through while flying. 50.1 lets it pass obsidian (50) but not anything harder.")
            .defineInRange("puppeteerVexMaxHardness", 50.1, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VEX_ATTACK = BLESSINGS
            .comment("Puppeteer (vex): lunge damage (its only way to hit).")
            .defineInRange("puppeteerVexAttackDamage", 9.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_VEX_LUNGE_WINDUP_TICKS = BLESSINGS
            .comment("Puppeteer (vex): the slight charge before a lunge (it giggles + glows as it winds up).")
            .defineInRange("puppeteerVexLungeWindupTicks", 5, 0, 40);
    public static final ModConfigSpec.IntValue PUPPETEER_VEX_LUNGE_TICKS = BLESSINGS
            .comment("Puppeteer (vex): how long the lunge dash lasts.")
            .defineInRange("puppeteerVexLungeTicks", 8, 1, 40);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VEX_LUNGE_SPEED = BLESSINGS
            .comment("Puppeteer (vex): the lunge dash speed (blocks/tick).")
            .defineInRange("puppeteerVexLungeSpeed", 1.3, 0.1, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VEX_LUNGE_TURN = BLESSINGS
            .comment("Puppeteer (vex): how sharply the lunge can steer toward your aim (degrees/tick).")
            .defineInRange("puppeteerVexLungeTurn", 8.0, 0.0, 45.0);
    public static final ModConfigSpec.IntValue PUPPETEER_VEX_LUNGE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (vex): left-click lunge cooldown — you can't hit at all during it. 40 = 2s.")
            .defineInRange("puppeteerVexLungeCooldownTicks", 40, 0, 400);
    public static final ModConfigSpec.IntValue PUPPETEER_VEX_LAUGH_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (vex): right-click laugh cooldown (just the annoying vex sounds). 40 = 2s.")
            .defineInRange("puppeteerVexLaughCooldownTicks", 40, 0, 400);

    // allay
    public static final ModConfigSpec.DoubleValue PUPPETEER_ALLAY_PICKUP_RADIUS = BLESSINGS
            .comment("Puppeteer (allay): right-click picks up the nearest ground item within this range (up to a stack), or drops what it holds.")
            .defineInRange("puppeteerAllayPickupRadius", 4.0, 0.5, 16.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ALLAY_MITOSIS_CHARGE_TICKS = BLESSINGS
            .comment("Puppeteer (allay): near jukebox music, HOLD right-click this long to split off a copy of yourself (mitosis).")
            .defineInRange("puppeteerAllayMitosisChargeTicks", 40, 5, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_ALLAY_MITOSIS_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (allay): cooldown of the mitosis split. 500 = 25s.")
            .defineInRange("puppeteerAllayMitosisCooldownTicks", 500, 0, 6000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ALLAY_MUSIC_RADIUS = BLESSINGS
            .comment("Puppeteer (allay): how far it can hear a playing jukebox for the mitosis charge to build.")
            .defineInRange("puppeteerAllayMusicRadius", 6.0, 1.0, 16.0);

    // evoker
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_FANGS_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (evoker): scroll the spell belt (FANG RING / LINE OF FANGS / SUMMON VEXES), right-click casts the",
                    "selected one. Aim at a sheep: WOLOLO (blue turns red, red turns blue); at a villager: CONVERT it into a witch.",
                    "Left-click: the rallying horn. This is the fangs cooldown (the ring and the line share it).")
            .defineInRange("puppeteerEvokerFangsCooldownTicks", 80, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_VEX_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (evoker): summon vex cooldown. They hunt whoever you last hit or horned (and never you).")
            .defineInRange("puppeteerEvokerVexCooldownTicks", 300, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_HORN_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (evoker): left-click at something blows the RALLYING HORN — no damage, but every illager (and",
                    "witch, and ravager) in range turns on that one target, with a burst of speed. Its cooldown.")
            .defineInRange("puppeteerEvokerHornCooldownTicks", 400, 0, 72000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_EVOKER_HORN_RANGE = BLESSINGS
            .comment("Puppeteer (evoker): how far you can mark a target with the horn, and how far away illagers answer it.")
            .defineInRange("puppeteerEvokerHornRange", 32.0, 1.0, 128.0);
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_HORN_SPEED_TICKS = BLESSINGS
            .comment("Puppeteer (evoker): how long the rallied get Speed (level puppeteerEvokerHornSpeedLevel).")
            .defineInRange("puppeteerEvokerHornSpeedTicks", 200, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_HORN_SPEED_LEVEL = BLESSINGS
            .comment("Puppeteer (evoker): the rallied's Speed level (1 = Speed I).")
            .defineInRange("puppeteerEvokerHornSpeedLevel", 1, 1, 10);
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_VEX_COUNT = BLESSINGS
            .comment("Puppeteer (evoker): vexes per summon (vanilla 3).")
            .defineInRange("puppeteerEvokerVexCount", 3, 1, 16);
    public static final ModConfigSpec.IntValue PUPPETEER_EVOKER_WINDUP_TICKS = BLESSINGS
            .comment("Puppeteer (evoker): the wind-up before a wololo or a conversion goes off (the fangs and vexes wind up while you hold).")
            .defineInRange("puppeteerEvokerWindupTicks", 20, 0, 200);

    // general
    public static final ModConfigSpec.DoubleValue PUPPETEER_DEXTEROUS_CHARGE_BONUS = BLESSINGS
            .comment("Puppeteer: with the Dexterous blessing every charge-up goes this much quicker (0.25 = 25%) — held moves,",
                    "the creeper's fuse, the guardian beam, the poppy, embedding, a horse's jump bar...")
            .defineInRange("puppeteerDexterousChargeBonus", 0.25, 0.0, 4.0);

    // endermite
    public static final ModConfigSpec.DoubleValue PUPPETEER_ENDERMITE_BURROW_SPEED = BLESSINGS
            .comment("Puppeteer (endermite): right-click to BURROW — you sink into the ground and move straight through blocks",
                    "(where you look, jump to rise; you can see through the rock around you). Right-click again to surface",
                    "(up to the nearest open space). Its speed, blocks per tick. Anything as hard as obsidian or harder",
                    "(see ...MaxHardness), or unbreakable (bedrock), can't be passed.")
            .defineInRange("puppeteerEndermiteBurrowSpeed", 0.25, 0.01, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ENDERMITE_MAX_HARDNESS = BLESSINGS
            .comment("Puppeteer (endermite): blocks this hard or harder can't be burrowed through (obsidian is 50; unbreakable",
                    "blocks never can). Covers other mods' toughest blocks too.")
            .defineInRange("puppeteerEndermiteMaxHardness", 50.0, 0.0, 10000.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ENDERMITE_BURROW_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (endermite): ticks between burrowing / surfacing.")
            .defineInRange("puppeteerEndermiteBurrowCooldownTicks", 20, 0, 1200);

    // villager
    public static final ModConfigSpec.IntValue PUPPETEER_VILLAGER_HMM_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (villager): right-click goes \"hmm\" — and every villager nearby turns to look at you and",
                    "hmms back. Its cooldown. (Other players can right-click you to TRADE with your villager's real trades;",
                    "whatever emeralds they pay go in your till, paid out when you step out.)")
            .defineInRange("puppeteerVillagerHmmCooldownTicks", 20, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_VILLAGER_HMM_RADIUS = BLESSINGS
            .comment("Puppeteer (villager): how far the hmm carries.")
            .defineInRange("puppeteerVillagerHmmRadius", 16.0, 1.0, 64.0);
    public static final ModConfigSpec.BooleanValue PUPPETEER_VILLAGER_INFECTION = BLESSINGS
            .comment("Puppeteer (villager): killed by a zombie (or a zombie puppet), you don't die — you're INFECTED and",
                    "carry on as a zombie villager puppet (your trades and profession come along). Zombies and illagers hunt a",
                    "villager puppet; iron golems defend it; it panics (Speed II) when hurt.")
            .define("puppeteerVillagerInfection", true);

    // hoglin / zoglin
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_SPEED = BLESSINGS
            .comment("Puppeteer (hoglin): movement speed multiplier. Hoglins are lone brutes — no rallies.")
            .defineInRange("puppeteerHoglinSpeedMultiplier", 1.15, 0.1, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOGLIN_SPEED = BLESSINGS
            .comment("Puppeteer (zoglin): movement speed multiplier.")
            .defineInRange("puppeteerZoglinSpeedMultiplier", 1.3, 0.1, 4.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_ATTACK = BLESSINGS
            .comment("Puppeteer (hoglin): damage of each tusk swing (vanilla 6).")
            .defineInRange("puppeteerHoglinAttackDamage", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOGLIN_ATTACK = BLESSINGS
            .comment("Puppeteer (zoglin): damage of each tusk swing.")
            .defineInRange("puppeteerZoglinAttackDamage", 7.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_REACH = BLESSINGS
            .comment("Puppeteer (hoglin / zoglin): reach (blocks).")
            .defineInRange("puppeteerHoglinReach", 2.5, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_KNOCKBACK = BLESSINGS
            .comment("Puppeteer (hoglin): how hard a swing tosses what it hits (vanilla's tusk throw — up and away).")
            .defineInRange("puppeteerHoglinKnockback", 1.0, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOGLIN_KNOCKBACK = BLESSINGS
            .comment("Puppeteer (zoglin): the same, harder.")
            .defineInRange("puppeteerZoglinKnockback", 1.4, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_FUNGUS_RANGE = BLESSINGS
            .comment("Puppeteer (hoglin, babies too): like the real thing, PLACED warped fungus (and the rest of vanilla's",
                    "hoglin_repellents: nether portals, respawn anchors) within this many blocks (horizontally; 4 up/down)",
                    "drives you away from it. Zoglins don't care. 0 = off.")
            .defineInRange("puppeteerHoglinFungusRange", 8.0, 0.0, 16.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_FUNGUS_STRENGTH = BLESSINGS
            .comment("Puppeteer (hoglin): how hard a repellent pushes you away each tick.")
            .defineInRange("puppeteerHoglinFungusStrength", 0.08, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_HOGLIN_BABY_FLEE_TICKS = BLESSINGS
            .comment("Puppeteer (baby hoglin): when hurt it bolts away from whatever hit it (Speed II + a shove) for this long.")
            .defineInRange("puppeteerHoglinBabyFleeTicks", 40, 0, 400);
    public static final ModConfigSpec.BooleanValue PUPPETEER_HOGLIN_ZOMBIFIES = BLESSINGS
            .comment("Puppeteer (hoglin): like the real thing, a hoglin outside the Nether shakes and, after",
                    "puppeteerHoglinZombifyTicks, turns into a zoglin — and you with it.")
            .define("puppeteerHoglinZombifies", true);
    public static final ModConfigSpec.IntValue PUPPETEER_HOGLIN_ZOMBIFY_TICKS = BLESSINGS
            .comment("Puppeteer (hoglin): ticks outside the Nether before it zombifies (vanilla 300).")
            .defineInRange("puppeteerHoglinZombifyTicks", 300, 1, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_HOGLIN_LUNGE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (hoglin): Lunge cooldown. Right-click: you stop briefly, then charge with limited turning.",
                    "The first thing you plough into takes puppeteerHoglinLungeDamage and is sent flying, and the charge stops.")
            .defineInRange("puppeteerHoglinLungeCooldownTicks", 180, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOGLIN_LUNGE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (zoglin): Lunge cooldown. Shorter wind-up, faster, turns better, and ploughs THROUGH",
                    "everything it hits (each thing once).")
            .defineInRange("puppeteerZoglinLungeCooldownTicks", 100, 0, 72000);
    public static final ModConfigSpec.IntValue PUPPETEER_HOGLIN_LUNGE_WINDUP = BLESSINGS
            .comment("Puppeteer (hoglin): ticks you stand still before the charge.")
            .defineInRange("puppeteerHoglinLungeWindupTicks", 12, 0, 100);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOGLIN_LUNGE_WINDUP = BLESSINGS
            .comment("Puppeteer (zoglin): ticks you stand still before the charge.")
            .defineInRange("puppeteerZoglinLungeWindupTicks", 6, 0, 100);
    public static final ModConfigSpec.IntValue PUPPETEER_HOGLIN_LUNGE_TICKS = BLESSINGS
            .comment("Puppeteer (hoglin): how long the charge lasts.")
            .defineInRange("puppeteerHoglinLungeTicks", 25, 1, 200);
    public static final ModConfigSpec.IntValue PUPPETEER_ZOGLIN_LUNGE_TICKS = BLESSINGS
            .comment("Puppeteer (zoglin): how long the charge lasts.")
            .defineInRange("puppeteerZoglinLungeTicks", 30, 1, 200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_LUNGE_SPEED = BLESSINGS
            .comment("Puppeteer (hoglin): charge speed (blocks per tick, roughly; sprinting is ~0.28).")
            .defineInRange("puppeteerHoglinLungeSpeed", 0.5, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOGLIN_LUNGE_SPEED = BLESSINGS
            .comment("Puppeteer (zoglin): charge speed.")
            .defineInRange("puppeteerZoglinLungeSpeed", 0.65, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_LUNGE_TURN = BLESSINGS
            .comment("Puppeteer (hoglin): how far the charge can turn towards where you look, in degrees per tick.")
            .defineInRange("puppeteerHoglinLungeTurnDegrees", 2.5, 0.0, 180.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOGLIN_LUNGE_TURN = BLESSINGS
            .comment("Puppeteer (zoglin): the same — more, but still limited.")
            .defineInRange("puppeteerZoglinLungeTurnDegrees", 5.0, 0.0, 180.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_LUNGE_DAMAGE = BLESSINGS
            .comment("Puppeteer (hoglin): damage to what the charge hits.")
            .defineInRange("puppeteerHoglinLungeDamage", 10.0, 0.0, 80.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ZOGLIN_LUNGE_DAMAGE = BLESSINGS
            .comment("Puppeteer (zoglin): damage to each thing the charge ploughs through.")
            .defineInRange("puppeteerZoglinLungeDamage", 10.0, 0.0, 80.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_HOGLIN_LUNGE_LAUNCH = BLESSINGS
            .comment("Puppeteer (hoglin / zoglin): how hard the charge sends its victim flying (horizontal; upward is 40% of it).")
            .defineInRange("puppeteerHoglinLungeLaunch", 1.6, 0.0, 6.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_GUARDIAN_THORNS = BLESSINGS
            .comment("Puppeteer (guardians): while you're holding still your spikes are out — anything that hits you in melee",
                    "takes this much thorns damage, like a real guardian.")
            .defineInRange("puppeteerGuardianThornsDamage", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ELDER_CURSE_INTERVAL_TICKS = BLESSINGS
            .comment("Puppeteer (elder guardian): every this many ticks you curse every other player nearby with Mining",
                    "Fatigue III — the ghostly face jumpscare and all, like a real elder guardian. 0 = off.")
            .defineInRange("puppeteerElderCurseIntervalTicks", 1200, 0, 72000);
    public static final ModConfigSpec.DoubleValue PUPPETEER_ELDER_CURSE_RADIUS = BLESSINGS
            .comment("Puppeteer (elder guardian): reach of that curse (blocks).")
            .defineInRange("puppeteerElderCurseRadius", 32.0, 1.0, 128.0);
    public static final ModConfigSpec.IntValue PUPPETEER_ELDER_CURSE_SECONDS = BLESSINGS
            .comment("Puppeteer (elder guardian): how long the Mining Fatigue lasts (vanilla's is 300).")
            .defineInRange("puppeteerElderCurseSeconds", 60, 1, 600);

    // squids (squid, glow squid)
    public static final ModConfigSpec.DoubleValue PUPPETEER_SQUID_SWIM = BLESSINGS
            .comment("Puppeteer (squids): swim speed change in water (-0.3 = 30% slower — squids are slow). Endless air",
                    "underwater; out of water you can hardly move and you dry out and die.")
            .defineInRange("puppeteerSquidSwimBonus", -0.3, -0.9, 3.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SQUID_FLEE_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (squids): ticks between Flees (right-click): a short dash and a cloud of ink.")
            .defineInRange("puppeteerSquidFleeCooldownTicks", 60, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SQUID_FLEE_DASH = BLESSINGS
            .comment("Puppeteer (squids): strength of the Flee dash.")
            .defineInRange("puppeteerSquidFleeDash", 0.9, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_SQUID_INK_RADIUS = BLESSINGS
            .comment("Puppeteer (squids): everything within this many blocks of the ink cloud is blinded (a glow squid's",
                    "ink also makes them glow).")
            .defineInRange("puppeteerSquidInkRadius", 4.0, 0.5, 16.0);
    public static final ModConfigSpec.IntValue PUPPETEER_SQUID_INK_SECONDS = BLESSINGS
            .comment("Puppeteer (squids): how long the ink's Blindness (and the glow squid's Glowing) lasts.")
            .defineInRange("puppeteerSquidInkSeconds", 4, 1, 60);

    // zombified piglin
    public static final ModConfigSpec.DoubleValue PUPPETEER_PIGLIN_SPEED = BLESSINGS
            .comment("Puppeteer (zombified piglin): your movement speed as a multiple of normal.")
            .defineInRange("puppeteerPiglinSpeedMultiplier", 1.0, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PIGLIN_ATTACK = BLESSINGS
            .comment("Puppeteer (zombified piglin): damage of each hit (a real one's golden sword hits for 5).")
            .defineInRange("puppeteerPiglinAttackDamage", 5.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PIGLIN_CALL_RADIUS = BLESSINGS
            .comment("Puppeteer (zombified piglin): no Rally, but whenever you hit something OR something hits you, every",
                    "zombified piglin within this many blocks turns on it — the whole pack, like the real thing.")
            .defineInRange("puppeteerPiglinCallRadius", 24.0, 1.0, 64.0);
    public static final ModConfigSpec.IntValue PUPPETEER_PIGLIN_FRENZY_SECONDS = BLESSINGS
            .comment("Puppeteer (zombified piglin): that call gives you AND the pack Speed II for this long.")
            .defineInRange("puppeteerPiglinFrenzySeconds", 8, 1, 120);
    public static final ModConfigSpec.DoubleValue PUPPETEER_PIGLIN_CONVERT_RADIUS = BLESSINGS
            .comment("Puppeteer (zombified piglin): right-click a PIG to Convert it — and every pig within this many blocks",
                    "of it — into zombified piglins.")
            .defineInRange("puppeteerPiglinConvertRadius", 8.0, 0.0, 32.0);
    public static final ModConfigSpec.IntValue PUPPETEER_PIGLIN_CONVERT_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (zombified piglin): ticks between Converts.")
            .defineInRange("puppeteerPiglinConvertCooldownTicks", 200, 0, 6000);

    // babies
    public static final ModConfigSpec.DoubleValue PUPPETEER_BABY_ZOMBIE_SPEED = BLESSINGS
            .comment("Puppeteer: a BABY zombie-family puppet is this much faster, like a real baby zombie (0.5 = +50%,",
                    "vanilla's own boost). Every baby puppet is also half size, so you fit where it fits.")
            .defineInRange("puppeteerBabyZombieSpeedBonus", 0.5, 0.0, 3.0);

    // fish (cod, salmon, pufferfish, tropical fish)
    public static final ModConfigSpec.DoubleValue PUPPETEER_FISH_SWIM_BONUS = BLESSINGS
            .comment("Puppeteer (fish): extra swim speed in water (1.5 = +150%). Underwater you never run out of air.")
            .defineInRange("puppeteerFishSwimBonus", 1.5, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_FISH_LAND_SLOW = BLESSINGS
            .comment("Puppeteer (fish): out of water you can barely move (0.85 = 85% slower) and you flop about like a real",
                    "fish (the bounce is the fishFlopPower synergy knob). Your air runs out on land, then you dry out.")
            .defineInRange("puppeteerFishLandSlow", 0.85, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PUPPETEER_FISH_LEAP_COOLDOWN_TICKS = BLESSINGS
            .comment("Puppeteer (fish): ticks between Leaps (right-click). 80 = 4s.")
            .defineInRange("puppeteerFishLeapCooldownTicks", 80, 0, 1200);
    public static final ModConfigSpec.DoubleValue PUPPETEER_FISH_LEAP_POWER = BLESSINGS
            .comment("Puppeteer (fish): out of water, Leap launches you this hard along where you're looking (horizontal)...")
            .defineInRange("puppeteerFishLeapPower", 1.3, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_FISH_LEAP_LIFT = BLESSINGS
            .comment("Puppeteer (fish): ...and this hard upwards, for a big arc. Fish puppets take no fall damage.")
            .defineInRange("puppeteerFishLeapLift", 0.8, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_FISH_DASH_POWER = BLESSINGS
            .comment("Puppeteer (fish): in water, Leap is a quick dash this strong, straight where you're looking.")
            .defineInRange("puppeteerFishDashPower", 1.6, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue PUPPETEER_FISH_ATTACK = BLESSINGS
            .comment("Puppeteer (fish): damage of each hit — it's a fish.")
            .defineInRange("puppeteerFishAttackDamage", 0.5, 0.0, 40.0);
    public static final ModConfigSpec.IntValue PUPPETEER_PUFFERFISH_POISON_SECONDS = BLESSINGS
            .comment("Puppeteer (pufferfish): hits poison the victim for this long, like a real pufferfish's spines.")
            .defineInRange("puppeteerPufferfishPoisonSeconds", 6, 0, 120);

    // --- Pickpocket (blessing) -------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("pickpocket"); }
    public static final ModConfigSpec.DoubleValue PICKPOCKET_RADIUS = BLESSINGS
            .comment("Pickpocket: how close you must be to a player to lift from them (blocks). Very close —",
                    "you're brushing right up against them.")
            .defineInRange("pickpocketRadius", 1.5, 0.5, 8.0);

    public static final ModConfigSpec.IntValue PICKPOCKET_CHECK_INTERVAL = BLESSINGS
            .comment("Pickpocket: ticks between lift attempts. 100 = every 5s.")
            .defineInRange("pickpocketCheckIntervalTicks", 100, 1, 1200);

    public static final ModConfigSpec.DoubleValue PICKPOCKET_BASE_CHANCE = BLESSINGS
            .comment("Pickpocket: chance (0..1) per attempt to lift an item while facing/beside a player.")
            .defineInRange("pickpocketBaseChance", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue PICKPOCKET_BEHIND_MULT = BLESSINGS
            .comment("Pickpocket: the base chance is multiplied by this when you're BEHIND the victim — sneaking",
                    "up from behind is far more effective.")
            .defineInRange("pickpocketBehindMultiplier", 4.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue PICKPOCKET_HOTBAR_WEIGHT = BLESSINGS
            .comment("Pickpocket: relative weight of hotbar slots when choosing what to steal (main-inventory",
                    "slots are weight 1.0). Low, so you mostly lift from their backpack, not what they're holding.")
            .defineInRange("pickpocketHotbarWeight", 0.1, 0.0, 1.0);

    // --- Hype Man (blessing) ---------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("hypeman"); }
    public static final ModConfigSpec.DoubleValue HYPEMAN_RADIUS = BLESSINGS
            .comment("Hype Man: how close a player must be to be dragged in as a hype-man and to hear the praise",
                    "(blocks). Also the pool the random 'speaker' of each line is picked from.")
            .defineInRange("hypemanRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.IntValue HYPEMAN_COOLDOWN = BLESSINGS
            .comment("Hype Man: minimum ticks between any two praises, across ALL trigger types, so a fight or a",
                    "pickup spree doesn't turn into a wall of text. 160 = ~8s.")
            .defineInRange("hypemanCooldownTicks", 160, 0, 6000);

    public static final ModConfigSpec.DoubleValue HYPEMAN_CHANCE = BLESSINGS
            .comment("Hype Man: chance (0..1) that an eligible action actually earns praise once the cooldown is",
                    "up. Below 1 so it stays a treat rather than clockwork.")
            .defineInRange("hypemanChance", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HYPEMAN_AMBIENT_INTERVAL = BLESSINGS
            .comment("Hype Man: ticks between checks for unprompted 'just being here' praise (still gated by the",
                    "cooldown and chance above). 120 = every 6s.")
            .defineInRange("hypemanAmbientIntervalTicks", 120, 20, 6000);
    public static final ModConfigSpec.IntValue HYPEMAN_DOWNTIME_INTERVAL = BLESSINGS
            .comment("Hype Man: ticks between checks for a rarer 'downtime' line — random crowd chatter after a lull",
                    "with no praise. 600 = every 30s.")
            .defineInRange("hypemanDowntimeIntervalTicks", 600, 40, 12000);
    public static final ModConfigSpec.IntValue HYPEMAN_DOWNTIME_THRESHOLD = BLESSINGS
            .comment("Hype Man: how long there must have been NO praise before a downtime line can fire. 400 = 20s.")
            .defineInRange("hypemanDowntimeThresholdTicks", 400, 40, 12000);
    public static final ModConfigSpec.DoubleValue HYPEMAN_BUFF_CHANCE = BLESSINGS
            .comment("Hype Man: chance that a compliment also hands you a random positive potion effect. 0.35 = 35%.")
            .defineInRange("hypemanBuffChance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.IntValue HYPEMAN_BUFF_MIN_SECONDS = BLESSINGS
            .comment("Hype Man: shortest duration of a compliment buff.")
            .defineInRange("hypemanBuffMinSeconds", 10, 1, 600);
    public static final ModConfigSpec.IntValue HYPEMAN_BUFF_MAX_SECONDS = BLESSINGS
            .comment("Hype Man: longest duration of a compliment buff.")
            .defineInRange("hypemanBuffMaxSeconds", 60, 1, 600);

    // --- Tax Man (blessing) ----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("taxman"); }
    public static final ModConfigSpec.DoubleValue TAXMAN_SAFE_RADIUS = BLESSINGS
            .comment("Tax Man blessing: he'll only turn up to pay you back when no hostile mob is within this",
                    "radius (blocks) — he waits for it to be safe.")
            .defineInRange("taxmanSafeRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.IntValue TAXMAN_IDLE_TICKS = BLESSINGS
            .comment("Tax Man blessing: ticks of you standing roughly still before he'll approach. 60 = 3s.")
            .defineInRange("taxmanIdleTicks", 60, 1, 600);

    public static final ModConfigSpec.IntValue TAXMAN_GIFT_EMERALDS_MAX = BLESSINGS
            .comment("Tax Man blessing: when the tax bank is EMPTY he brings a gift instead. Emeralds: 1..MAX,",
                    "uniform.")
            .defineInRange("taxmanGiftEmeraldsMax", 10, 1, 64);

    public static final ModConfigSpec.IntValue TAXMAN_GIFT_GOLD_MAX = BLESSINGS
            .comment("Tax Man blessing gift: gold ingots 1..MAX, uniform.")
            .defineInRange("taxmanGiftGoldMax", 8, 1, 64);

    public static final ModConfigSpec.IntValue TAXMAN_GIFT_DIAMONDS_MAX = BLESSINGS
            .comment("Tax Man blessing gift: diamonds 0..MAX, BIASED toward 0 (min of two rolls), so a fistful",
                    "of diamonds is a rare treat.")
            .defineInRange("taxmanGiftDiamondsMax", 3, 0, 64);

    // --- Bodyguard (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("bodyguard"); }
    public static final ModConfigSpec.DoubleValue BODYGUARD_HEALTH = BLESSINGS
            .comment("Bodyguard: the skeleton's max health. Tough — it's meant to soak a beating.")
            .defineInRange("bodyguardHealth", 60.0, 1.0, 1024.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_DAMAGE = BLESSINGS
            .comment("Bodyguard: melee damage it deals once it's actually ATTACKING an aggressor.")
            .defineInRange("bodyguardDamage", 6.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_ARMOR = BLESSINGS
            .comment("Bodyguard: armour attribute (on top of any worn armour) — the 'armoured' in the brief.")
            .defineInRange("bodyguardArmor", 12.0, 0.0, 30.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_SPEED = BLESSINGS
            .comment("Bodyguard: movement speed. A touch quicker than a player so it can keep up and cut people off.")
            .defineInRange("bodyguardSpeed", 0.34, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_FOLLOW_DISTANCE = BLESSINGS
            .comment("Bodyguard: how far from the anchor it's happy to sit before trailing back to them (blocks).")
            .defineInRange("bodyguardFollowDistance", 4.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_TELEPORT_DISTANCE = BLESSINGS
            .comment("Bodyguard: if it strays (or the anchor pearls/flies) beyond this, it blinks back to the",
                    "anchor's side like a tamed wolf (blocks).")
            .defineInRange("bodyguardTeleportDistance", 12.0, 4.0, 96.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_WARNING_RADIUS = BLESSINGS
            .comment("Bodyguard: an intruder this close to the anchor gets WARNED to back off (blocks).")
            .defineInRange("bodyguardWarningRadius", 8.0, 1.0, 48.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_AGGRESSION_RADIUS = BLESSINGS
            .comment("Bodyguard: an intruder this close, ignoring the warnings, earns AGGRESSION — shoves and",
                    "warning hits (blocks).")
            .defineInRange("bodyguardAggressionRadius", 4.0, 1.0, 48.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_LEASH_RANGE = BLESSINGS
            .comment("Bodyguard: while ATTACKING, it gives up and returns once the aggressor is this far from the",
                    "anchor (blocks) — it guards a place, it doesn't chase to the ends of the earth.")
            .defineInRange("bodyguardLeashRange", 32.0, 4.0, 128.0);
    public static final ModConfigSpec.DoubleValue BODYGUARD_THREAT_LEASH_RANGE = BLESSINGS
            .comment("Bodyguard: the longer leash used for a genuine ATTACKER (anything that struck you or it), so",
                    "it actually commits to a real threat instead of standing down the moment they back off.")
            .defineInRange("bodyguardThreatLeashRange", 48.0, 4.0, 256.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_WARNING_HIT_DAMAGE = BLESSINGS
            .comment("Bodyguard: damage of a non-committal 'warning hit' during AGGRESSION. Low — it's a shove,",
                    "not an execution.")
            .defineInRange("bodyguardWarningHitDamage", 1.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue BODYGUARD_WARNING_HIT_INTERVAL = BLESSINGS
            .comment("Bodyguard: minimum ticks between warning hits.")
            .defineInRange("bodyguardWarningHitIntervalTicks", 30, 1, 200);

    public static final ModConfigSpec.IntValue BODYGUARD_PATIENCE = BLESSINGS
            .comment("Bodyguard: ticks an intruder can keep crowding at AGGRESSION range before the bodyguard",
                    "loses patience, draws its sword and actually attacks them (no attack from them needed).",
                    "100 = 5s. This is what makes it engage instead of shoving forever.")
            .defineInRange("bodyguardPatienceTicks", 100, 20, 1200);

    public static final ModConfigSpec.IntValue BODYGUARD_WARNINGS_BEFORE_ATTACK = BLESSINGS
            .comment("Bodyguard: how many spoken warnings it must actually deliver to a lingering intruder",
                    "before patience is allowed to draw steel — so it never silently jumps to violence.",
                    "(Being physically attacked still triggers immediate self-defence, warnings or not.)")
            .defineInRange("bodyguardWarningsBeforeAttack", 2, 0, 10);

    public static final ModConfigSpec.DoubleValue BODYGUARD_CHAT_RADIUS = BLESSINGS
            .comment("Bodyguard: only players within this radius hear it speak (blocks) — its lines are local,",
                    "not server-wide.")
            .defineInRange("bodyguardChatRadius", 24.0, 1.0, 128.0);

    public static final ModConfigSpec.IntValue BODYGUARD_RESPAWN_TICKS = BLESSINGS
            .comment("Bodyguard: after it dies, the blessing is NOT lost — a replacement is hired this many ticks",
                    "later. 7200 = 6 minutes.")
            .defineInRange("bodyguardRespawnTicks", 7200, 0, 720000);

    public static final ModConfigSpec.IntValue BODYGUARD_DIALOGUE_COOLDOWN = BLESSINGS
            .comment("Bodyguard: minimum ticks between one spoken dialogue tree and the next during a",
                    "confrontation (idle 'ambient' chatter is 3x rarer). 100 = ~5s — chatty enough to feel",
                    "alive without talking over itself.")
            .defineInRange("bodyguardDialogueCooldownTicks", 100, 20, 2000);

    public static final ModConfigSpec.IntValue BODYGUARD_DIALOGUE_LINE_GAP = BLESSINGS
            .comment("Bodyguard: ticks between successive lines WITHIN one dialogue tree.")
            .defineInRange("bodyguardDialogueLineGapTicks", 30, 1, 200);
    public static final ModConfigSpec.IntValue BODYGUARD_STRUGGLE_TELEPORTS = BLESSINGS
            .comment("Bodyguard: how many blink-back teleports within the struggle window (usually because you're",
                    "airborne and it can't keep up) before it grumbles a 'struggle' line.")
            .defineInRange("bodyguardStruggleTeleports", 3, 1, 50);
    public static final ModConfigSpec.IntValue BODYGUARD_STRUGGLE_WINDOW_TICKS = BLESSINGS
            .comment("Bodyguard: the rolling window for counting struggle teleports. 80 = 4s.")
            .defineInRange("bodyguardStruggleWindowTicks", 80, 5, 400);
    public static final ModConfigSpec.IntValue BODYGUARD_STRUGGLE_COOLDOWN_TICKS = BLESSINGS
            .comment("Bodyguard: minimum ticks between two struggle grumbles, so a long flight doesn't spam chat.")
            .defineInRange("bodyguardStruggleCooldownTicks", 160, 20, 2000);

    // --- Guarded Ally synergy (Guardian Angel + Bodyguard) ---------------------------------------------
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("guarded"); }
    public static final ModConfigSpec.IntValue GUARDED_ALLY_STRONG_SECONDS = SYNERGIES
            .comment("Guarded Ally (Guardian Angel + Bodyguard): duration of the FULL frenzy the angel whips the",
                    "bodyguard into when you're attacked (Strength/Speed/Resistance). 12 = 12s.")
            .defineInRange("guardedAllyStrongSeconds", 12, 1, 120);
    public static final ModConfigSpec.IntValue GUARDED_ALLY_STRONG_AMPLIFIER = SYNERGIES
            .comment("Guarded Ally: amplifier of the strong frenzy's buffs (0 = level I, 1 = level II, ...).")
            .defineInRange("guardedAllyStrongAmplifier", 1, 0, 4);
    public static final ModConfigSpec.IntValue GUARDED_ALLY_SOFT_SECONDS = SYNERGIES
            .comment("Guarded Ally: duration of the SOFTER frenzy left in the bodyguard when the angel dies. 8 = 8s.")
            .defineInRange("guardedAllySoftSeconds", 8, 1, 120);
    public static final ModConfigSpec.IntValue GUARDED_ALLY_SOFT_AMPLIFIER = SYNERGIES
            .comment("Guarded Ally: amplifier of the soft frenzy's buffs (0 = level I).")
            .defineInRange("guardedAllySoftAmplifier", 0, 0, 4);
    public static final ModConfigSpec.IntValue GUARDED_ALLY_COOLDOWN_TICKS = SYNERGIES
            .comment("Guarded Ally: minimum ticks between one under-attack frenzy trigger and the next, so a run of",
                    "hits doesn't re-buff every tick. 100 = 5s.")
            .defineInRange("guardedAllyCooldownTicks", 100, 0, 2000);

    // --- Angel's Grudge synergy (Guardian Angel + Solicitor) -------------------------------------------
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("angels"); }
    public static final ModConfigSpec.IntValue ANGELS_GRUDGE_IRRITATION_MIN_TICKS = SYNERGIES
            .comment("Angel's Grudge (Guardian Angel + Solicitor): the low end of the hidden irritation timer that",
                    "must fill (while the trader is around) before the angel hunts it down. 1800 = 90s.")
            .defineInRange("angelsGrudgeIrritationMinTicks", 1800, 200, 72000);
    public static final ModConfigSpec.IntValue ANGELS_GRUDGE_IRRITATION_MAX_TICKS = SYNERGIES
            .comment("Angel's Grudge: the high end of the hidden irritation timer (a bit of randomness on top). 3600 = 3min.")
            .defineInRange("angelsGrudgeIrritationMaxTicks", 3600, 200, 72000);
    public static final ModConfigSpec.IntValue ANGELS_GRUDGE_COOLDOWN_TICKS = SYNERGIES
            .comment("Angel's Grudge: how long the solicitor is forced to lie low after the angel kills its trader.",
                    "6000 = 5 minutes.")
            .defineInRange("angelsGrudgeCooldownTicks", 6000, 200, 72000);

    // --- Soul Bond (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("soulbond"); }
    public static final ModConfigSpec.DoubleValue SOULBOND_RADIUS = BLESSINGS
            .comment("Soul Bond: how close the nearest living thing must be to become your bound (blocks). The",
                    "bond continuously re-picks the nearest, so in a 1v1 it latches onto your opponent and with",
                    "a pet at your heels it latches onto the pet — which is the whole deterrent.")
            .defineInRange("soulBondRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue SOULBOND_DAMAGE_SHARE = BLESSINGS
            .comment("Soul Bond: fraction of the damage YOU take that your bound takes instead. 0.4 = they eat",
                    "40%, you eat the remaining 60%. Their share bypasses armour (it's a soul tether).")
            .defineInRange("soulBondDamageShare", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SOULBOND_REBIND_INTERVAL = BLESSINGS
            .comment("Soul Bond: ticks between re-picking the nearest living thing to bind. 20 = once a second.")
            .defineInRange("soulBondRebindIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue SOULBOND_PARTICLE_INTERVAL = BLESSINGS
            .comment("Soul Bond: ticks between the constant golden particles emitted on the bound entity.")
            .defineInRange("soulBondParticleIntervalTicks", 4, 1, 100);

    // --- Fullness (blessing) ---------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("fullness"); }
    public static final ModConfigSpec.DoubleValue FULLNESS_DRAIN_RATE = BLESSINGS
            .comment("Fullness: fraction of the NORMAL hunger drain that actually sticks. 0.2 = hunger (and its",
                    "hidden saturation) deplete at a fifth of the usual rate, so you rarely need to eat.")
            .defineInRange("fullnessDrainRate", 0.2, 0.0, 1.0);

    // --- Army (blessing) -------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("army"); }
    public static final ModConfigSpec.IntValue ARMY_RADIUS = BLESSINGS
            .comment("Army: radius (blocks) within which hostile mobs go neutral toward you and rally to your",
                    "defence when something hits you.")
            .defineInRange("armyRadius", 24, 1, 128);

    public static final ModConfigSpec.IntValue ARMY_DEFEND_DURATION = BLESSINGS
            .comment("Army: ticks the nearby horde keeps swarming whatever last hit you. 600 = 30s.")
            .defineInRange("armyDefendDurationTicks", 600, 20, 12000);

    public static final ModConfigSpec.IntValue ARMY_CHECK_INTERVAL = BLESSINGS
            .comment("Army: ticks between sweeps that keep hostiles off you (and re-aim the swarm). Low, so mobs",
                    "barely get a swing in before being pacified.")
            .defineInRange("armyCheckIntervalTicks", 5, 1, 100);

    public static final ModConfigSpec.BooleanValue ARMY_SAME_TYPE_EXCLUDED = BLESSINGS
            .comment("Army: if true, the swarm won't turn on its OWN kind — a zombie that hit you won't be",
                    "attacked by other zombies, but skeletons etc. still will.")
            .define("armySameTypeExcluded", true);

    // --- Reflect (blessing) ----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("reflect"); }
    public static final ModConfigSpec.DoubleValue REFLECT_VELOCITY_MULT = BLESSINGS
            .comment("Reflect: speed multiplier on a projectile sent back at its shooter. 1.5 = it returns half",
                    "again as fast as it arrived — harder to dodge, but dodgeable (it doesn't home).")
            .defineInRange("reflectVelocityMultiplier", 1.5, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue REFLECT_INACCURACY = BLESSINGS
            .comment("Reflect: spread on the return shot. 0.0 = dead-precise at the attacker; raise for sloppier",
                    "aim (vanilla arrows use ~1.0).")
            .defineInRange("reflectInaccuracy", 0.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue REFLECT_GLADIATOR_VELOCITY_MULT = BLESSINGS
            .comment("Reflect+Gladiator synergy: extra speed multiplier on a reflected shot, so it flies further before dropping.")
            .defineInRange("reflectGladiatorVelocityMultiplier", 1.6, 1.0, 5.0);

    // --- Peace (blessing) ------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("peace"); }
    public static final ModConfigSpec.DoubleValue PEACE_SPAWN_RATE_MULT = BLESSINGS
            .comment("Peace: fraction of hostile NATURAL spawns near you that are still ALLOWED. 0.3 = ~70% of",
                    "them are quietly cancelled, so far fewer monsters appear around you.")
            .defineInRange("peaceSpawnRateMultiplier", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.IntValue PEACE_RADIUS = BLESSINGS
            .comment("Peace: radius (blocks) around you that spawn suppression and detection reduction apply.")
            .defineInRange("peaceRadius", 48, 1, 128);

    public static final ModConfigSpec.DoubleValue PEACE_DETECTION_MULT = BLESSINGS
            .comment("Peace: fraction of a hostile's NORMAL follow range at which it can still notice you. 0.4 =",
                    "mobs only lock onto you at 40% of the usual distance — the opposite of Popularity.")
            .defineInRange("peaceDetectionMultiplier", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue PEACE_CHECK_INTERVAL = BLESSINGS
            .comment("Peace: ticks between sweeps that strip too-distant aggro and check for the discovery moment.")
            .defineInRange("peaceCheckIntervalTicks", 20, 1, 200);

    // --- Luck (blessing) -------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("luck"); }
    public static final ModConfigSpec.DoubleValue LUCK_ATTRIBUTE_BONUS = BLESSINGS
            .comment("Luck: how much is added to your vanilla LUCK attribute. Nudges loot-table rolls (fishing,",
                    "chests) toward better outcomes. 5.0 is a big, if quiet, boost.")
            .defineInRange("luckAttributeBonus", 5.0, 0.0, 1024.0);

    // --- Siren's Call ----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("siren"); }
    public static final ModConfigSpec.IntValue SIREN_CHECK_INTERVAL = CURSES
            .comment("Siren's Call: ticks between longing updates. 10 = twice a second.")
            .defineInRange("sirenCheckIntervalTicks", 10, 1, 100);

    public static final ModConfigSpec.DoubleValue SIREN_LONGING_MAX = CURSES
            .comment("Siren's Call: the longing meter's ceiling. Stages are read against this.")
            .defineInRange("sirenLongingMax", 100.0, 1.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_DRY_GAIN = CURSES
            .comment("Siren's Call: longing gained per check while OUT of water. At the default check rate,",
                    "0.2 fills an empty meter in roughly four minutes of staying dry.")
            .defineInRange("sirenDryGain", 0.2, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue SIREN_WATER_DRAIN = CURSES
            .comment("Siren's Call: longing lost per check while in water, AFTER the grace period. Much faster",
                    "than it builds — a proper dip settles you quickly.")
            .defineInRange("sirenWaterDrain", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue SIREN_WATER_GRACE = CURSES
            .comment("Siren's Call: minimum ticks you must stay in water before the longing begins to DROP.",
                    "During this grace the meter holds and the magenta mind-control shader fades out — so by",
                    "the time it's actually falling, the screen is clear again. 60 = 3s.")
            .defineInRange("sirenWaterGraceTicks", 60, 0, 600);

    // Six escalating stages (longing 0..100). Each is the longing at which that stage BEGINS.
    public static final ModConfigSpec.DoubleValue SIREN_STAGE1 = CURSES
            .comment("Siren's Call stage 1 — UNEASE: faint bubbles and a rare water drip, no penalty yet.")
            .defineInRange("sirenStage1Threshold", 12.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE2 = CURSES
            .comment("Siren's Call stage 2 — YEARNING: Mining Fatigue I + the occasional yearning cue.")
            .defineInRange("sirenStage2Threshold", 28.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE3 = CURSES
            .comment("Siren's Call stage 3 — RESTLESSNESS: Mining Fatigue II, a more frequent cue, Nausea flickers.")
            .defineInRange("sirenStage3Threshold", 45.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE4 = CURSES
            .comment("Siren's Call stage 4 — HEAVINESS: Slowness I on land.")
            .defineInRange("sirenStage4Threshold", 60.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE5 = CURSES
            .comment("Siren's Call stage 5 — THE SEA'S GRIP: Slowness II on land + intermittent pull-bursts",
                    "toward water (the sea testing its hold; still resistible between bursts).")
            .defineInRange("sirenStage5Threshold", 78.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE6 = CURSES
            .comment("Siren's Call stage 6 — THE MARCH: continuous movement hijack to the water, magenta shader.")
            .defineInRange("sirenStage6Threshold", 92.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_SHADER_MAX_ALPHA = CURSES
            .comment("Siren's Call: peak opacity of the magenta mind-control overlay, 0..1. A tint, not a wall.")
            .defineInRange("sirenShaderMaxAlpha", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SIREN_WATER_SEARCH_RADIUS = CURSES
            .comment("Siren's Call: how far to look for water to be dragged toward, in blocks.")
            .defineInRange("sirenWaterSearchRadius", 24, 1, 64);

    public static final ModConfigSpec.DoubleValue SIREN_PULL_FORCE = CURSES
            .comment("Siren's Call: a small server-side velocity tug toward the water on top of the hijacked",
                    "walk, so even mid-air or on ice you drift the right way.")
            .defineInRange("sirenPullForce", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SIREN_DROWNED_SOOTHE_RADIUS = CURSES
            .comment("Siren's Call: how close a Drowned must be to soothe you, in blocks. They protect their",
                    "own — nearby Drowned slow the longing and won't turn on the victim.")
            .defineInRange("sirenDrownedSootheRadius", 12.0, 0.0, 48.0);

    public static final ModConfigSpec.DoubleValue SIREN_DROWNED_GAIN_MULT = CURSES
            .comment("Siren's Call: longing-gain multiplier while a Drowned is watching over you. Below 1.")
            .defineInRange("sirenDrownedGainMultiplier", 0.25, 0.0, 1.0);

    // --- Basement Dweller ------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("basement"); }
    public static final ModConfigSpec.IntValue BASEMENT_START_INTERVAL = CURSES
            .comment("Basement Dweller: ticks between burns WHEN YOU FIRST step into the sun (the slow start).")
            .defineInRange("basementStartIntervalTicks", 80, 1, 400);
    public static final ModConfigSpec.IntValue BASEMENT_END_INTERVAL = CURSES
            .comment("Basement Dweller: ticks between burns once the ramp is fully wound up (the fast end).")
            .defineInRange("basementEndIntervalTicks", 15, 1, 400);
    public static final ModConfigSpec.IntValue BASEMENT_RAMP_TICKS = CURSES
            .comment("Basement Dweller: how long (ticks) continuous sun takes to decay from the start interval",
                    "to the end interval. 500 = 25s.")
            .defineInRange("basementRampTicks", 500, 1, 6000);


    public static final ModConfigSpec.DoubleValue BASEMENT_DAMAGE = CURSES
            .comment("Basement Dweller: damage per burn in direct daylight, in half-hearts.")
            .defineInRange("basementDamage", 1.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue BASEMENT_HELMET_INTERVAL_MULT = CURSES
            .comment("Basement Dweller: a hat SLOWS the burns rather than softening them — the interval is",
                    "multiplied by this while your head slot is occupied. Above 1 (2.5 = burns 2.5x further",
                    "apart), but never infinite: you still cook, just slower.")
            .defineInRange("basementHelmetIntervalMultiplier", 2.5, 1.0, 20.0);

    // --- Claustrophobia --------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("claustro"); }
    public static final ModConfigSpec.IntValue CLAUSTRO_START_INTERVAL = CURSES
            .comment("Claustrophobia: ticks between bites WHEN YOU FIRST get boxed in (the slow start).")
            .defineInRange("claustrophobiaStartIntervalTicks", 70, 1, 400);
    public static final ModConfigSpec.IntValue CLAUSTRO_END_INTERVAL = CURSES
            .comment("Claustrophobia: ticks between bites once the ramp is fully wound up (the fast end).")
            .defineInRange("claustrophobiaEndIntervalTicks", 25, 1, 400);
    public static final ModConfigSpec.IntValue CLAUSTRO_RAMP_TICKS = CURSES
            .comment("Claustrophobia: how long (ticks) staying boxed-in takes to decay from the start interval",
                    "to the end interval. 200 = 10s.")
            .defineInRange("claustrophobiaRampTicks", 200, 1, 6000);


    public static final ModConfigSpec.DoubleValue CLAUSTRO_DAMAGE = CURSES
            .comment("Claustrophobia: damage per tick while indoors, in half-hearts. Milder than Basement",
                    "Dweller's sun by design.")
            .defineInRange("claustrophobiaDamage", 0.5, 0.0, 40.0);

    // --- Basement Dweller + Claustrophobia: leeway while BOTH are on you at once ------------------------
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("env"); }
    public static final ModConfigSpec.IntValue ENV_COMBINED_GRACE_TICKS = SYNERGIES
            .comment("Basement+Claustrophobia together: extra grace (ticks) after entering a damage condition",
                    "before ANY damage starts, since suffering both at once is brutal. 70 = 3.5s. Applied only",
                    "while both curses are active; swaps off the moment one is removed.")
            .defineInRange("envCombinedGraceTicks", 70, 0, 600);
    public static final ModConfigSpec.DoubleValue ENV_COMBINED_INTERVAL_MULT = SYNERGIES
            .comment("Basement+Claustrophobia together: multiply the gap between hits by this while both are on.")
            .defineInRange("envCombinedIntervalMultiplier", 1.3, 1.0, 4.0);
    public static final ModConfigSpec.DoubleValue ENV_COMBINED_DECAY_MULT = SYNERGIES
            .comment("Basement+Claustrophobia together: multiply the ramp/decay time by this while both are on.")
            .defineInRange("envCombinedDecayMultiplier", 1.45, 1.0, 4.0);

    // --- Stick Drift -----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("stickdrift"); }
    public static final ModConfigSpec.IntValue STICKDRIFT_CAMERA_CHANCE = CURSES
            .comment("Stick Drift: percent chance the rolled drift is a CAMERA drift rather than a MOVEMENT",
                    "one. Decided once when the curse lands and fixed thereafter.")
            .defineInRange("stickDriftCameraChancePercent", 50, 0, 100);

    public static final ModConfigSpec.IntValue STICKDRIFT_GAP_MIN = CURSES
            .comment("Stick Drift: shortest calm gap between drift episodes, in ticks.")
            .defineInRange("stickDriftGapMinTicks", 40, 0, 6000);

    public static final ModConfigSpec.IntValue STICKDRIFT_GAP_MAX = CURSES
            .comment("Stick Drift: longest calm gap between drift episodes, in ticks.")
            .defineInRange("stickDriftGapMaxTicks", 200, 0, 6000);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_INTENSITY_MIN = CURSES
            .comment("Stick Drift: weakest episode intensity, 0..1.")
            .defineInRange("stickDriftIntensityMin", 0.2, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_INTENSITY_MAX = CURSES
            .comment("Stick Drift: strongest episode intensity, 0..1.")
            .defineInRange("stickDriftIntensityMax", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_DURATION_PRODUCT = CURSES
            .comment("Stick Drift: intensity × duration is held roughly constant at this many tick-units, so a",
                    "stronger drift lasts a shorter time and vice versa (the controller-drift joke). Episode",
                    "length = this ÷ intensity, clamped to the bounds below.")
            .defineInRange("stickDriftDurationProduct", 60.0, 1.0, 6000.0);

    public static final ModConfigSpec.IntValue STICKDRIFT_DURATION_MIN = CURSES
            .comment("Stick Drift: shortest an episode can last regardless of intensity, in ticks.")
            .defineInRange("stickDriftDurationMinTicks", 20, 1, 6000);

    public static final ModConfigSpec.IntValue STICKDRIFT_DURATION_MAX = CURSES
            .comment("Stick Drift: longest an episode can last regardless of intensity, in ticks.")
            .defineInRange("stickDriftDurationMaxTicks", 300, 1, 6000);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_MOVE_SCALE = CURSES
            .comment("Stick Drift: movement drift at full intensity, as a fraction of full stick input.")
            .defineInRange("stickDriftMoveScale", 0.7, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_CAMERA_SCALE = CURSES
            .comment("Stick Drift: camera drift at full intensity, in degrees per tick.")
            .defineInRange("stickDriftCameraScale", 2.0, 0.0, 20.0);

    // --- Wonky -----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("wonky"); }
    public static final ModConfigSpec.DoubleValue WONKY_DRIFT_STRENGTH = CURSES
            .comment("Wonky: how hard your movement wanders sideways while walking, as a fraction of full",
                    "strafe. Kept small — it should feel like you can't quite hold a line, not like being",
                    "shoved.")
            .defineInRange("wonkyDriftStrength", 0.18, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue WONKY_SPRINT_MULTIPLIER = CURSES
            .comment("Wonky: how much the sideways wander is amplified while sprinting — you commit harder, so",
                    "the wobble is worse.")
            .defineInRange("wonkySprintMultiplier", 2.2, 1.0, 6.0);

    public static final ModConfigSpec.DoubleValue WONKY_PERIOD_TICKS = CURSES
            .comment("Wonky: how many ticks one full left-right wander cycle takes. Longer = a lazier weave",
                    "that's harder to consciously correct for; shorter = a jitterier stagger.")
            .defineInRange("wonkyPeriodTicks", 34.0, 4.0, 200.0);

    // --- Flat Footed -----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("flatfoot"); }
    public static final ModConfigSpec.DoubleValue FLATFOOT_STEP_DISTANCE = CURSES
            .comment("Flat Footed: blocks of travel between amplified footfalls. Lower = more frequent stomps.")
            .defineInRange("flatFootedStepDistance", 1.8, 0.5, 8.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_VOLUME = CURSES
            .comment("Flat Footed: volume of the amplified footstep. Vanilla steps are ~0.15, so this is",
                    "cartoonishly loud — that's the joke, and it's what makes you trackable.")
            .defineInRange("flatFootedVolume", 3.0, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_SNEAK_VOLUME_MULT = CURSES
            .comment("Flat Footed: multiplier applied while sneaking. Below 1 so tiptoeing is a bit quieter —",
                    "but never silent, so sneaking away still gives you away.")
            .defineInRange("flatFootedSneakVolumeMultiplier", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_SHAKE_RADIUS = CURSES
            .comment("Flat Footed: how close another player must be to feel the footstep camera-shudder.")
            .defineInRange("flatFootedShakeRadius", 8.0, 0.0, 48.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_SHAKE_STRENGTH = CURSES
            .comment("Flat Footed: peak footstep camera-shudder amplitude in degrees. Slight — a nudge, not the",
                    "Heavyweight jolt.")
            .defineInRange("flatFootedShakeStrength", 0.6, 0.0, 10.0);

    public static final ModConfigSpec.IntValue FLATFOOT_SHAKE_TICKS = CURSES
            .comment("Flat Footed: how long each footstep shudder lasts, in ticks.")
            .defineInRange("flatFootedShakeTicks", 5, 1, 100);

    public static final ModConfigSpec.DoubleValue FLATFOOT_DETECTION_BONUS = CURSES
            .comment("Flat Footed: extra blocks of FOLLOW_RANGE handed to nearby hostile mobs, so your racket",
                    "reaches their ears from further off. Slight on purpose.")
            .defineInRange("flatFootedDetectionBonus", 8.0, 0.0, 48.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_DETECTION_RADIUS = CURSES
            .comment("Flat Footed: how far out hostile mobs get that detection bonus applied, in blocks.")
            .defineInRange("flatFootedDetectionRadius", 24.0, 1.0, 64.0);

    // --- Broken Bonds ----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("broken"); }
    public static final ModConfigSpec.IntValue BROKEN_BONDS_CHECK_INTERVAL = CURSES
            .comment("Broken Bonds: ticks between hate-meter updates on nearby owned pets.")
            .defineInRange("brokenBondsCheckIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_RADIUS = CURSES
            .comment("Broken Bonds: how close one of your pets must be for its resentment to build, in blocks.",
                    "Beyond this its meter cools off instead.")
            .defineInRange("brokenBondsRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_LIMIT = CURSES
            .comment("Broken Bonds: how much hate a pet must accumulate before it snaps and untames.")
            .defineInRange("brokenBondsLimit", 100.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_PROXIMITY_GAIN = CURSES
            .comment("Broken Bonds: hate per check while a pet is RIGHT next to you (it scales down with",
                    "distance, so being across the radius barely registers). Deliberately slow.")
            .defineInRange("brokenBondsProximityGain", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_FOLLOW_GAIN = CURSES
            .comment("Broken Bonds: extra hate per check while a pet is actively trailing you around (standing,",
                    "not sitting) — spending time in your company wears on it.")
            .defineInRange("brokenBondsFollowGain", 1.5, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_RIDE_GAIN = CURSES
            .comment("Broken Bonds: extra hate per check while you are RIDING the pet. Being sat on is the",
                    "fastest way to lose a friend.")
            .defineInRange("brokenBondsRideGain", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_DECAY = CURSES
            .comment("Broken Bonds: hate lost per check when a pet is out of range or otherwise not building.",
                    "Similar to the proximity rate, so leaving it alone genuinely calms it down.")
            .defineInRange("brokenBondsDecay", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_RANDOMNESS = CURSES
            .comment("Broken Bonds: +/- fraction of jitter on each hate change, so the exact moment a pet",
                    "snaps is never perfectly predictable. 0.3 = up to 30% either way.")
            .defineInRange("brokenBondsRandomness", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.IntValue BROKEN_BONDS_FLEE_TICKS = CURSES
            .comment("Broken Bonds: how long a freshly-untamed pet's legs are hijacked to storm off. 200 = 10s.")
            .defineInRange("brokenBondsFleeTicks", 200, 20, 2400);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_FLEE_DISTANCE = CURSES
            .comment("Broken Bonds: how far ahead it aims each leg of that escape, in blocks.")
            .defineInRange("brokenBondsFleeDistance", 12.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_FLEE_SPEED = CURSES
            .comment("Broken Bonds: movement speed multiplier while storming off.")
            .defineInRange("brokenBondsFleeSpeed", 1.3, 0.1, 5.0);

    // --- Oversharer ------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("oversharer"); }
    public static final ModConfigSpec.IntValue OVERSHARER_INTERVAL_MIN = CURSES
            .comment("Oversharer: shortest gap between leaks, in ticks. 1800 = 1.5min.")
            .defineInRange("oversharerIntervalMinTicks", 1800, 100, 72000);

    public static final ModConfigSpec.IntValue OVERSHARER_INTERVAL_MAX = CURSES
            .comment("Oversharer: longest gap between leaks, in ticks. 6000 = 5min.")
            .defineInRange("oversharerIntervalMaxTicks", 6000, 100, 72000);

    // --- Clumsy ----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("clumsy"); }
    public static final ModConfigSpec.DoubleValue CLUMSY_BASE_CHANCE = CURSES
            .comment("Clumsy: percent chance the very next block you place goes wrong, from a clean slate.")
            .defineInRange("clumsyBaseChancePercent", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue CLUMSY_PER_BLOCK_CHANCE = CURSES
            .comment("Clumsy: extra percent added for each block placed without a slip. The chance ramps up",
                    "the longer you go clean, then resets the moment something goes wrong.")
            .defineInRange("clumsyPerBlockChancePercent", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue CLUMSY_MAX_CHANCE = CURSES
            .comment("Clumsy: the ceiling that ramp climbs to, in percent.")
            .defineInRange("clumsyMaxChancePercent", 31.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue CLUMSY_ORIENTAL_ORIENTATION = CURSES
            .comment("Clumsy: for a block WITH an orientation (stairs, doors, logs...), percent of slips that",
                    "come out facing the wrong way. This plus the next two should total 100.")
            .defineInRange("clumsyOrientalOrientationPercent", 60, 0, 100);

    public static final ModConfigSpec.IntValue CLUMSY_ORIENTAL_LOCATION = CURSES
            .comment("Clumsy: for an oriented block, percent of slips that land in the wrong spot.")
            .defineInRange("clumsyOrientalLocationPercent", 32, 0, 100);


    public static final ModConfigSpec.IntValue CLUMSY_PLAIN_LOCATION = CURSES
            .comment("Clumsy: for a plain block with no orientation, percent of slips that land in the wrong",
                    "spot. This plus the next should total 100.")
            .defineInRange("clumsyPlainLocationPercent", 65, 0, 100);


    // --- Taxes -----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("taxes"); }
    public static final ModConfigSpec.IntValue TAXES_CHECK_INTERVAL = CURSES
            .comment("Taxes: ticks between checks for whether you've been careless enough to be worth a visit.")
            .defineInRange("taxesCheckIntervalTicks", 100, 20, 12000);

    public static final ModConfigSpec.IntValue TAXES_SCAN_RADIUS = CURSES
            .comment("Taxes: how far the Tax Man reaches for chests, floor items and your person, in blocks.")
            .defineInRange("taxesScanRadius", 12, 1, 48);

    public static final ModConfigSpec.IntValue TAXES_MIN_VALUE_TRIGGER = CURSES
            .comment("Taxes: how much VALUE must be within reach before a visit is worth his time (see",
                    "taxesItemValues). This is the counterplay made concrete — stay under it and he never",
                    "comes.")
            .defineInRange("taxesMinValueTrigger", 24, 1, 100000);

    public static final ModConfigSpec.IntValue TAXES_HAUL_CAP = CURSES
            .comment("Taxes: how much VALUE he takes in one visit before declaring himself satisfied. Weighted",
                    "rather than counted, so he can't strip a stack of netherite the way he would a stack of",
                    "copper. Kept low on purpose: this should be an irritation, not a robbery.")
            .defineInRange("taxesHaulValueCap", 40, 1, 100000);

    public static final ModConfigSpec.IntValue TAXES_DEFAULT_ITEM_VALUE = CURSES
            .comment("Taxes: value of anything in the witchmod:valuables tag that isn't listed in",
                    "taxesItemValues — including modded ores, which is why this exists.")
            .defineInRange("taxesDefaultItemValue", 1, 0, 10000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAXES_ITEM_VALUES = CURSES
            .comment("Taxes: per-item worth, as \"item=value\". Storage blocks are worth roughly 9x their",
                    "ingot because that's what they're made of. Anything omitted falls back to",
                    "taxesDefaultItemValue.")
            .defineListAllowEmpty("taxesItemValues", List.of(
                    "minecraft:raw_iron=1", "minecraft:iron_ingot=1", "minecraft:iron_ore=1",
                    "minecraft:deepslate_iron_ore=1", "minecraft:iron_nugget=1",
                    "minecraft:iron_block=9", "minecraft:raw_iron_block=9",
                    "minecraft:raw_copper=1", "minecraft:copper_ingot=1", "minecraft:copper_ore=1",
                    "minecraft:deepslate_copper_ore=1", "minecraft:copper_block=9", "minecraft:raw_copper_block=9",
                    "minecraft:raw_gold=2", "minecraft:gold_ingot=2", "minecraft:gold_ore=2",
                    "minecraft:deepslate_gold_ore=2", "minecraft:nether_gold_ore=2", "minecraft:gold_nugget=1",
                    "minecraft:gold_block=18", "minecraft:raw_gold_block=18",
                    "minecraft:lapis_lazuli=1", "minecraft:lapis_ore=1", "minecraft:deepslate_lapis_ore=1",
                    "minecraft:lapis_block=9",
                    "minecraft:quartz=1", "minecraft:nether_quartz_ore=1", "minecraft:quartz_block=4",
                    "minecraft:amethyst_shard=1", "minecraft:amethyst_block=4",
                    "minecraft:emerald=3", "minecraft:emerald_ore=3", "minecraft:deepslate_emerald_ore=3",
                    "minecraft:emerald_block=27",
                    "minecraft:diamond=4", "minecraft:diamond_ore=4", "minecraft:deepslate_diamond_ore=4",
                    "minecraft:diamond_block=36",
                    "minecraft:ancient_debris=16", "minecraft:netherite_scrap=16",
                    "minecraft:netherite_ingot=16", "minecraft:netherite_block=144"),
                    entry -> entry instanceof String text && text.contains("="));

    public static final ModConfigSpec.IntValue TAXES_BANK_CAPACITY = CURSES
            .comment("Taxes: how many stacks the world-wide tax bank can hold.",
                    "",
                    "⚠ THIS IS A MEMORY LIMIT, NOT A BALANCE ONE, AND IT MUST NEVER VOID ITEMS.",
                    "The bank is a SavedData list that persists forever and is only drained by the Tax Man",
                    "BLESSING, so without a ceiling it grows without bound for the life of the world. When it",
                    "is full the correct behaviour is to STOP TAKING — the Taxes curse is refused at the",
                    "table and by command, and the Tax Man leaves things where they are. Never discard.",
                    "",
                    "Sized far larger than one visit's haul so it comfortably holds many taxations.")
            .defineInRange("taxesBankCapacityStacks", 512, 1, 100000);

    public static final ModConfigSpec.IntValue TAXES_BANK_WARN_AT = CURSES
            .comment("Taxes: percentage full at which commands start warning that the bank is filling up.")
            .defineInRange("taxesBankWarnAtPercent", 80, 1, 100);

    public static final ModConfigSpec.IntValue TAXES_COOLDOWN = CURSES
            .comment("Taxes: ticks before he may return after a visit. 6000 = 5min. He does come back — the",
                    "curse isn't spent by one audit.")
            .defineInRange("taxesCooldownTicks", 6000, 100, 72000);

    public static final ModConfigSpec.IntValue TAXES_COLLECT_INTERVAL = CURSES
            .comment("Audit: ticks between individual seizures. Item-by-item so you can watch it happen and",
                    "swear at him, but brisk (7 = ~0.35s) so a full chest doesn't take an age.")
            .defineInRange("taxesCollectIntervalTicks", 7, 1, 200);

    public static final ModConfigSpec.IntValue TAXES_ARRIVE_TICKS = CURSES
            .comment("Audit: how long he stands there ominously before starting work. 20 = 1s.")
            .defineInRange("taxesArriveTicks", 20, 0, 600);

    public static final ModConfigSpec.IntValue TAXES_LEAVE_TICKS = CURSES
            .comment("Audit: how long he lingers after finishing before vanishing. 25 = ~1.25s.")
            .defineInRange("taxesLeaveTicks", 25, 0, 600);

    public static final ModConfigSpec.IntValue TAXES_GIVE_UP_SWEEPS = CURSES
            .comment("Audit: how many fruitless sweeps before he gives up and leaves.")
            .defineInRange("taxesGiveUpSweeps", 8, 1, 100);

    public static final ModConfigSpec.IntValue TAXES_ENDER_CHEST_AFTER = CURSES
            .comment("Audit: fruitless sweeps before he places his OWN ender chest to go through yours — the",
                    "answer to hiding everything in the one container he can't otherwise reach.")
            .defineInRange("taxesEnderChestAfterSweeps", 2, 1, 100);

    public static final ModConfigSpec.DoubleValue TAXES_CHAT_RADIUS = CURSES
            .comment("Taxes: how far away other players can hear him narrating, in blocks.")
            .defineInRange("taxesChatRadius", 24.0, 0.0, 128.0);

    // --- Comic Relief ----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("comic"); }
    public static final ModConfigSpec.IntValue COMIC_CHECK_INTERVAL = CURSES
            .comment("Comic Relief: ticks between rolls. 100 = every 5s.")
            .defineInRange("comicReliefCheckIntervalTicks", 100, 5, 2400);

    public static final ModConfigSpec.DoubleValue COMIC_LOW_HEALTH = CURSES
            .comment("Comic Relief: at or below this health, in half-hearts, the sky starts taking an",
                    "interest. 6.0 = three hearts.")
            .defineInRange("comicReliefLowHealthThreshold", 6.0, 1.0, 40.0);

    public static final ModConfigSpec.DoubleValue COMIC_STRIKE_CHANCE = CURSES
            .comment("Comic Relief: percent chance per check of a killing bolt while you're low. Small on",
                    "purpose — the joke only lands if it's genuinely unexpected.")
            .defineInRange("comicReliefStrikeChancePercent", 9.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_STRIKE_CHANCE = CURSES
            .comment("Comic Relief: percent chance per check of a bolt landing on a pile of your dropped",
                    "items instead, destroying them.")
            .defineInRange("comicReliefItemStrikeChancePercent", 12.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_PILE_SCALING = CURSES
            .comment("Comic Relief: extra percent added to the item-strike chance for each item beyond the",
                    "minimum pile size. A big pile of your worldly goods lying in a field is exactly the shot",
                    "the joke wants, so the bigger it is the likelier the sky is to take it.")
            .defineInRange("comicReliefItemPileScalingPercent", 2.5, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_CHANCE_CAP = CURSES
            .comment("Comic Relief: ceiling for that scaled item-strike chance, per check.")
            .defineInRange("comicReliefItemChanceCapPercent", 65.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue COMIC_ITEM_PILE_MIN = CURSES
            .comment("Comic Relief: how many item entities nearby count as a pile worth striking.")
            .defineInRange("comicReliefItemPileMin", 5, 1, 200);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_SCAN_RADIUS = CURSES
            .comment("Comic Relief: how far to look for that pile, in blocks.")
            .defineInRange("comicReliefItemScanRadius", 8.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue COMIC_POSTHUMOUS_CHANCE = CURSES
            .comment("Comic Relief: percent chance that dying earns you one more bolt on the spot a moment",
                    "later — which torches the drops you left behind. Deliberately rare, and deliberately",
                    "kicking you while you're down; that IS the joke.")
            .defineInRange("comicReliefPosthumousChancePercent", 8.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue COMIC_POSTHUMOUS_DELAY = CURSES
            .comment("Comic Relief: ticks after death before that parting bolt lands. Long enough that the",
                    "death screen is already up, which is what sells it.")
            .defineInRange("comicReliefPosthumousDelayTicks", 40, 1, 600);

    public static final ModConfigSpec.DoubleValue COMIC_THUNDER_MULTIPLIER = CURSES
            .comment("Comic Relief: all of the above chances are multiplied by this while it's thundering.")
            .defineInRange("comicReliefThunderMultiplier", 2.5, 1.0, 20.0);
    public static final ModConfigSpec.IntValue COMIC_SMITE_BOLTS = CURSES
            .comment("Comic Relief+Thunder synergy: extra visual bolts rained down in a quick barrage when the killing bolt fires (still kills the same way, just funnier).")
            .defineInRange("comicReliefSmiteBolts", 5, 0, 30);
    public static final ModConfigSpec.IntValue COMIC_SMITE_SPACING_TICKS = CURSES
            .comment("Comic Relief+Thunder synergy: ticks between the barrage's extra bolts.")
            .defineInRange("comicReliefSmiteSpacingTicks", 2, 1, 20);

    // --- Thirst Meter ----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("thirst"); }
    public static final ModConfigSpec.IntValue THIRST_MAX = CURSES
            .comment("Thirst Meter: bar size, in points. 20 = ten droplets, matching hunger.")
            .defineInRange("thirstMax", 20, 2, 40);

    public static final ModConfigSpec.DoubleValue THIRST_IDLE_DRAIN = CURSES
            .comment("Thirst Meter: thirst-exhaustion added per tick while doing nothing. At the default",
                    "exhaustion-per-point of 4.0, 0.01 works out to one droplet per 20s of standing still.")
            .defineInRange("thirstIdleDrainPerTick", 0.01, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THIRST_WALK_DRAIN = CURSES
            .comment("Thirst Meter: extra thirst-exhaustion per tick while walking.")
            .defineInRange("thirstWalkDrainPerTick", 0.012, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THIRST_SPRINT_DRAIN = CURSES
            .comment("Thirst Meter: extra thirst-exhaustion per tick while sprinting or swimming. This is the",
                    "point of the curse — it is meant to stop a target being very active.")
            .defineInRange("thirstSprintDrainPerTick", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THIRST_ACTION_DRAIN = CURSES
            .comment("Thirst Meter: thirst-exhaustion per strenuous action — mining a block, landing a hit.")
            .defineInRange("thirstActionDrain", 0.08, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue THIRST_EXHAUSTION_PER_POINT = CURSES
            .comment("Thirst Meter: how much accumulated thirst-exhaustion costs one point off the bar.",
                    "Mirrors vanilla's hunger model, where 4.0 exhaustion costs a haunch.")
            .defineInRange("thirstExhaustionPerPoint", 4.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue THIRST_DEHYDRATION_MULT = CURSES
            .comment("Thirst Meter: drain multiplier while the Dehydration effect is on you. 4.0 takes the",
                    "idle rate from one droplet per 20s down to one per 5s — hot biomes apply Dehydration, so",
                    "this is what sets the pace out in a desert.")
            .defineInRange("thirstDehydrationMultiplier", 4.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue THIRST_SATURATION_PER_POINT = CURSES
            .comment("Thirst Meter: how much hidden saturation each restored point also grants. Saturation is",
                    "spent BEFORE the visible bar, exactly like hunger — it's the grace period that stops a",
                    "freshly-filled bar from starting to tick down the instant you finish drinking.")
            .defineInRange("thirstSaturationPerPoint", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.IntValue THIRST_FULL_BONUS_HEAL_INTERVAL = CURSES
            .comment("Thirst Meter: ticks between bonus heals while BOTH hunger and thirst are completely",
                    "full. This is the reward for keeping on top of it, stacked on vanilla's own regen.")
            .defineInRange("thirstFullBonusHealIntervalTicks", 60, 5, 2400);

    public static final ModConfigSpec.DoubleValue THIRST_FULL_BONUS_HEAL = CURSES
            .comment("Thirst Meter: health restored per bonus heal, in half-hearts.")
            .defineInRange("thirstFullBonusHeal", 1.0, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue THIRST_HOT_BIOME_TEMPERATURE = CURSES
            .comment("Thirst Meter: biome base temperature at or above which you count as being somewhere hot",
                    "and start dehydrating. 1.0 catches desert, badlands, savanna and the Nether; jungle sits",
                    "just under at 0.95.")
            .defineInRange("thirstHotBiomeTemperature", 1.0, -1.0, 3.0);

    public static final ModConfigSpec.IntValue THIRST_ACTIVITY_DEHYDRATION_THRESHOLD = CURSES
            .comment("Thirst Meter: how many points of the bar you must burn through while it's already below",
                    "half before sustained activity brings on Dehydration by itself.")
            .defineInRange("thirstActivityDehydrationThreshold", 4, 1, 40);

    public static final ModConfigSpec.IntValue THIRST_DEHYDRATION_DURATION = CURSES
            .comment("Thirst Meter: how long Dehydration lasts once applied, in ticks. Refreshed while you",
                    "remain in a hot biome.")
            .defineInRange("thirstDehydrationDurationTicks", 400, 20, 24000);

    public static final ModConfigSpec.IntValue THIRST_SPRINT_CUTOFF = CURSES
            .comment("Thirst Meter: at or below this many points you can no longer sprint.")
            .defineInRange("thirstSprintCutoff", 2, 0, 40);

    public static final ModConfigSpec.DoubleValue THIRST_EMPTY_DAMAGE = CURSES
            .comment("Thirst Meter: damage per interval at an empty bar. Uses the mod's own dehydration damage",
                    "type, which is fatal on EVERY difficulty and bypasses armour.")
            .defineInRange("thirstEmptyDamage", 1.0, 0.0, 40.0);

    public static final ModConfigSpec.IntValue THIRST_EMPTY_DAMAGE_INTERVAL = CURSES
            .comment("Thirst Meter: ticks between those damage ticks. 40 = 2s.")
            .defineInRange("thirstEmptyDamageIntervalTicks", 40, 5, 600);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_WATER_BOTTLE = CURSES
            .comment("Thirst Meter: points restored by a water bottle.")
            .defineInRange("thirstRestoreWaterBottle", 6, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_POTION = CURSES
            .comment("Thirst Meter: points restored by any other potion.")
            .defineInRange("thirstRestorePotion", 3, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_RAW_WATER = CURSES
            .comment("Thirst Meter: points restored by drinking straight from a water source.")
            .defineInRange("thirstRestoreRawWater", 5, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RAW_WATER_RISK = CURSES
            .comment("Thirst Meter: percent chance drinking untreated water makes you ill — weak Poison, or",
                    "Dehydration, which is the crueller of the two.")
            .defineInRange("thirstRawWaterRiskPercent", 45, 0, 100);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_RAW_FOOD = CURSES
            .comment("Thirst Meter: points restored by raw food (uncooked meat and fish).")
            .defineInRange("thirstRestoreRawFood", 2, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_NATURAL_FOOD = CURSES
            .comment("Thirst Meter: points restored by natural food — the water-rich plant stuff (melon,",
                    "apples, berries, carrots). Uses the same category split as the Allergic curse.")
            .defineInRange("thirstRestoreNaturalFood", 4, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_OTHER_FOOD = CURSES
            .comment("Thirst Meter: points restored by anything else edible — cooked meat, bread, stews. The",
                    "spec only named raw and natural, so this is the judgement call: dry, processed food is",
                    "worth a little but nothing like fruit. Set to 0 if it should be worth nothing at all.")
            .defineInRange("thirstRestoreOtherFood", 1, 0, 40);

    // --- Pandora's Box (secret curse) -----------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("pandoras_box"); }
    public static final ModConfigSpec.IntValue PANDORA_SLOTS = CURSES
            .comment("Pandora's Box: how many random curses it holds on you at once. These are ON TOP of the normal",
                    "curse limit (they don't count toward it).")
            .defineInRange("pandorasBoxSlots", 3, 1, 10);
    public static final ModConfigSpec.IntValue PANDORA_SWAP_SECONDS = CURSES
            .comment("Pandora's Box: seconds between swaps — each swap trades the OLDEST held curse for a new random one.")
            .defineInRange("pandorasBoxSwapSeconds", 90, 5, 3600);

    // --- Shadow (secret curse) ----------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("shadow"); }
    public static final ModConfigSpec.IntValue SHADOW_DELAY_TICKS = CURSES
            .comment("Shadow: how far behind you (ticks) your shadow retraces your exact path. 42 = 2.1 seconds. Stand",
                    "still for this long and it reaches you.")
            .defineInRange("shadowDelayTicks", 42, 10, 1200);
    public static final ModConfigSpec.IntValue SHADOW_MAX_CHASE_TICKS = CURSES
            .comment("Shadow: chases come in bouts. After chasing (risen) for this many ticks it gives up and vanishes, as",
                    "if you'd gone into water, then returns after shadowRespawnTicks + the delay. 1440 = 1.2 minutes.")
            .defineInRange("shadowMaxChaseTicks", 1440, 100, 72000);
    public static final ModConfigSpec.IntValue SHADOW_SUMMON_TICKS = CURSES
            .comment("Shadow: length (ticks) of its summoning — it claws up out of the ground and can't catch anyone",
                    "until it has fully risen.")
            .defineInRange("shadowSummonTicks", 25, 0, 200);
    public static final ModConfigSpec.DoubleValue SHADOW_SPAWN_MIN_DISTANCE = CURSES
            .comment("Shadow: idle protection for SPAWNING only — it won't form while its spawn point (your position",
                    "shadowDelayTicks ago) is closer to you than this, so standing still can't make it appear on top of",
                    "you. It waits until you've moved away. Once it exists, the normal chase rules apply.")
            .defineInRange("shadowSpawnMinDistance", 4.0, 0.0, 32.0);
    public static final ModConfigSpec.IntValue SHADOW_RESPAWN_TICKS = CURSES
            .comment("Shadow: after it vanishes (you went into water, changed dimension, or it just caught you), how long",
                    "(ticks) before it starts following again. It then re-forms shadowDelayTicks behind you.")
            .defineInRange("shadowRespawnTicks", 100, 0, 6000);
    public static final ModConfigSpec.DoubleValue SHADOW_CATCH_DISTANCE = CURSES
            .comment("Shadow: how close (blocks, centre to centre) it has to get to catch — and kill — you.")
            .defineInRange("shadowCatchDistance", 0.6, 0.1, 3.0);
    public static final ModConfigSpec.DoubleValue SHADOW_MUSIC_DISTANCE = CURSES
            .comment("Shadow: its chase music (the Snail's) starts when it's within this many blocks of you, and swells",
                    "to full volume as it closes in. Only the hunted player hears it.")
            .defineInRange("shadowMusicDistance", 48.0, 1.0, 128.0);

    // --- Pests -----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("pests"); }
    public static final ModConfigSpec.IntValue PESTS_CHANCE = CURSES
            .comment("Pests: percent chance per block mined that silverfish come out of it. Mining is a very",
                    "high-frequency action, so this is much lower than it looks — at 8% a normal tunnelling",
                    "session still produces a steady trickle.")
            .defineInRange("pestsChancePercent", 8, 0, 100);

    public static final ModConfigSpec.IntValue PESTS_MOB_HIT_CHANCE = CURSES
            .comment("Pests: percent chance per melee hit on a mob that silverfish crawl out of it (hitting a",
                    "silverfish never spawns more).")
            .defineInRange("pestsMobHitChancePercent", 7, 0, 100);

    public static final ModConfigSpec.IntValue PESTS_MIN_PER_TRIGGER = CURSES
            .comment("Pests: fewest silverfish per trigger.")
            .defineInRange("pestsMinPerTrigger", 1, 1, 16);

    public static final ModConfigSpec.IntValue PESTS_MAX_PER_TRIGGER = CURSES
            .comment("Pests: most silverfish per trigger.")
            .defineInRange("pestsMaxPerTrigger", 3, 1, 16);

    public static final ModConfigSpec.IntValue PESTS_MAX_NEARBY = CURSES
            .comment("Pests: skip spawning if this many silverfish are already near the victim. A necessary",
                    "guard rather than balance — silverfish CALL MORE SILVERFISH out of stone when hit, so",
                    "without a ceiling a mining session can snowball into an unrecoverable swarm.")
            .defineInRange("pestsMaxNearby", 12, 1, 64);

    public static final ModConfigSpec.DoubleValue PESTS_NEARBY_RADIUS = CURSES
            .comment("Pests: radius the nearby-silverfish ceiling is measured over, in blocks.")
            .defineInRange("pestsNearbyRadius", 16.0, 1.0, 64.0);

    // --- Heavyweight -----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("heavyweight"); }
    public static final ModConfigSpec.IntValue HEAVYWEIGHT_BASE_BREAK_TICKS = CURSES
            .comment("Heavyweight: flat ticks added to every break, before hardness is considered. Keeps even",
                    "the softest block from vanishing the instant you step on it.")
            .defineInRange("heavyweightBaseBreakTicks", 10, 0, 2400);

    public static final ModConfigSpec.DoubleValue HEAVYWEIGHT_HARDNESS_MULT = CURSES
            .comment("Heavyweight: ticks added per point of block hardness. Roughly: leaves 0.8s, dirt 1.1s,",
                    "stone 2.4s, planks 3.0s, iron block 6.8s, obsidian ~60s. Obsidian stays a real deterrent",
                    "without being literally impossible, which a higher multiplier made it.")
            .defineInRange("heavyweightHardnessMultiplier", 25.0, 0.0, 600.0);

    public static final ModConfigSpec.DoubleValue HEAVYWEIGHT_WARN_FRACTION = CURSES
            .comment("Heavyweight: how far through the break the audible creaking starts, 0-1. Before this",
                    "point the warning is purely visual.")
            .defineInRange("heavyweightWarnFraction", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HEAVYWEIGHT_COLLAPSE_RADIUS = CURSES
            .comment("Heavyweight: how far the collapse spreads to neighbouring blocks, in blocks. The floor",
                    "giving way should take a chunk of itself with it, not punch one neat hole.")
            .defineInRange("heavyweightCollapseRadius", 1, 0, 4);

    public static final ModConfigSpec.IntValue HEAVYWEIGHT_COLLAPSE_CHANCE = CURSES
            .comment("Heavyweight: percent chance each neighbouring block in range goes too. Below 100 so the",
                    "hole is ragged rather than a perfect square.")
            .defineInRange("heavyweightCollapseChancePercent", 60, 0, 100);

    public static final ModConfigSpec.IntValue HEAVYWEIGHT_SHAKE_TICKS = CURSES
            .comment("Heavyweight: how long the camera rattles after a collapse. 12 = 0.6s.")
            .defineInRange("heavyweightShakeTicks", 12, 0, 200);

    public static final ModConfigSpec.DoubleValue HEAVYWEIGHT_SHAKE_STRENGTH = CURSES
            .comment("Heavyweight: peak camera-shake amplitude in degrees. Decays to nothing over the window.")
            .defineInRange("heavyweightShakeStrength", 3.5, 0.0, 30.0);

    // --- Floor Is Lava ---------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("fil"); }
    public static final ModConfigSpec.IntValue FIL_GRACE_TICKS = CURSES
            .comment("Floor Is Lava: how long you may stand still before it starts hurting. 100 = 5s — long",
                    "enough to craft, read a sign or check a chest, short enough that you can never settle.")
            .defineInRange("floorIsLavaGraceTicks", 100, 0, 2400);

    public static final ModConfigSpec.IntValue FIL_DAMAGE_INTERVAL = CURSES
            .comment("Floor Is Lava: ticks between burns once the grace period has run out. 20 = 1s.")
            .defineInRange("floorIsLavaDamageIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.DoubleValue FIL_BASE_DAMAGE = CURSES
            .comment("Floor Is Lava: damage on the FIRST burn, in half-hearts.")
            .defineInRange("floorIsLavaBaseDamage", 1.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue FIL_RAMP_PER_BURN = CURSES
            .comment("Floor Is Lava: extra damage added per consecutive burn. The ramp is the whole point —",
                    "standing still has to get worse the longer you do it, or it's just a slow tax.")
            .defineInRange("floorIsLavaRampPerBurn", 0.5, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue FIL_MAX_DAMAGE = CURSES
            .comment("Floor Is Lava: the cap the ramp climbs to, in half-hearts per burn. Without a cap an",
                    "AFK player is simply executed, which isn't a joke, it's a disconnect.")
            .defineInRange("floorIsLavaMaxDamage", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue FIL_MOVEMENT_RESET_DISTANCE = CURSES
            .comment("Floor Is Lava: how far you must move to be counted as moving and reset the timer. Small,",
                    "so shuffling on the spot genuinely counts — but not zero, or camera-only movement and",
                    "sub-block jitter would keep you safe forever.")
            .defineInRange("floorIsLavaMovementResetDistance", 0.5, 0.05, 8.0);

    // --- Social Outcast --------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("outcast"); }
    public static final ModConfigSpec.DoubleValue OUTCAST_REVEAL_DISTANCE = CURSES
            .comment("Social Outcast: how close someone must get before you can see them at all, in blocks.",
                    "Small on purpose — they should be able to stand right next to you before appearing.")
            .defineInRange("outcastRevealDistance", 4.0, 0.5, 32.0);

    public static final ModConfigSpec.IntValue OUTCAST_DAMAGE_REVEAL_TICKS = CURSES
            .comment("Social Outcast: how long someone stays visible after hitting you. 400 = 20s. Once it",
                    "lapses without another hit they vanish again, so a fight keeps them on screen but a",
                    "single ambush doesn't reveal them permanently.")
            .defineInRange("outcastDamageRevealTicks", 400, 20, 12000);

    public static final ModConfigSpec.BooleanValue OUTCAST_HIDES_VILLAGERS = CURSES
            .comment("Social Outcast: whether villagers are hidden too, or only players.")
            .define("outcastHidesVillagers", true);

    // --- Minor Inconvenience ---------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("minor"); }
    public static final ModConfigSpec.IntValue MINOR_RENAME_MIN = CURSES
            .comment("Minor Inconvenience: shortest gap between window renames. 600 = 30s.")
            .defineInRange("minorInconvenienceRenameMinTicks", 600, 20, 24000);

    public static final ModConfigSpec.IntValue MINOR_RENAME_MAX = CURSES
            .comment("Minor Inconvenience: longest gap between window renames. 2400 = 2min.")
            .defineInRange("minorInconvenienceRenameMaxTicks", 2400, 20, 24000);

    // --- Screensaver -----------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("screensaver"); }
    public static final ModConfigSpec.IntValue SCREENSAVER_EPISODE_MIN = CURSES
            .comment("Screensaver: shortest bouncing episode, in ticks. 400 = 20s.")
            .defineInRange("screensaverEpisodeMinTicks", 400, 20, 24000);

    public static final ModConfigSpec.IntValue SCREENSAVER_EPISODE_MAX = CURSES
            .comment("Screensaver: longest bouncing episode, in ticks. 900 = 45s.")
            .defineInRange("screensaverEpisodeMaxTicks", 900, 20, 24000);

    public static final ModConfigSpec.IntValue SCREENSAVER_DUTY_PERCENT = CURSES
            .comment("Screensaver: roughly what percentage of the time is spent BOUNCING, excluding the",
                    "transitions in and out. The idle gap is derived from this and the episode length, so",
                    "changing episode length keeps the same overall rhythm automatically.")
            .defineInRange("screensaverDutyPercent", 20, 1, 100);

    public static final ModConfigSpec.IntValue SCREENSAVER_TRANSITION_TICKS = CURSES
            .comment("Screensaver: how long the shrink and grow take, in ticks. 80 = 4s each way. Deliberately",
                    "slow and eased — a snap resize is jarring and reads as a crash rather than a joke.")
            .defineInRange("screensaverTransitionTicks", 80, 4, 600);

    public static final ModConfigSpec.IntValue SCREENSAVER_WINDOW_SCALE_PERCENT = CURSES
            .comment("Screensaver: the shrunk window's size as a percentage of the monitor. Kept LARGE on",
                    "purpose — a tiny window bouncing fast is a motion-sickness generator, and you still have",
                    "to be able to play.")
            .defineInRange("screensaverWindowScalePercent", 55, 10, 95);

    public static final ModConfigSpec.IntValue SCREENSAVER_SPEED_PX = CURSES
            .comment("Screensaver: bounce speed in pixels per tick. Low by design, for the same reason.")
            .defineInRange("screensaverSpeedPx", 2, 1, 40);

    // --- Dwarfism --------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("dwarfism"); }
    public static final ModConfigSpec.DoubleValue DWARFISM_MODEL_SCALE = CURSES
            .comment("Dwarfism: size multiplier. 0.5 is half height — and because vanilla's SCALE attribute",
                    "drives the bounding box as well as the model, you genuinely fit through 1-block gaps.")
            .defineInRange("dwarfismModelScale", 0.5, 0.1, 1.0);

    public static final ModConfigSpec.DoubleValue DWARFISM_HEALTH_MULT = CURSES
            .comment("Dwarfism: max-health multiplier. 0.5 = 10 hearts down to 5.")
            .defineInRange("dwarfismHealthMultiplier", 0.5, 0.1, 1.0);

    public static final ModConfigSpec.BooleanValue DWARFISM_RIDE_PLAYERS = CURSES
            .comment("Dwarfism: whether right-clicking another player lets you ride them.")
            .define("dwarfismCanRidePlayers", true);

    public static final ModConfigSpec.BooleanValue DWARFISM_RIDE_VILLAGERS = CURSES
            .comment("Dwarfism: whether right-clicking a villager lets you ride them.")
            .define("dwarfismCanRideVillagers", true);

    // --- Neutral Aggression ----------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("neutral"); }
    public static final ModConfigSpec.DoubleValue NEUTRAL_AGGRO_RADIUS = CURSES
            .comment("Neutral Aggression: how close a neutral mob must be before it turns on you, in blocks.")
            .defineInRange("neutralAggroRadius", 24.0, 1.0, 64.0);

    public static final ModConfigSpec.IntValue NEUTRAL_AGGRO_CHECK_INTERVAL = CURSES
            .comment("Neutral Aggression: ticks between sweeps for anything neutral nearby.")
            .defineInRange("neutralAggroCheckIntervalTicks", 40, 1, 600);

    public static final ModConfigSpec.BooleanValue NEUTRAL_AGGRO_TURNS_OWN_PETS = CURSES
            .comment("Neutral Aggression: whether YOUR OWN tamed animals turn on you too. On by default —",
                    "your own dog deciding it hates you is the best thing this curse does.")
            .define("neutralAggroTurnsOwnPets", true);

    // --- Magnet ----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("magnet"); }
    public static final ModConfigSpec.DoubleValue MAGNET_RADIUS = CURSES
            .comment("Magnet: how close a projectile must be before it starts curving toward you, in blocks.")
            .defineInRange("magnetRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue MAGNET_STEER_STRENGTH = CURSES
            .comment("Magnet: how sharply a projectile is bent toward you each tick, 0-1. This steers the",
                    "DIRECTION only and preserves the projectile's speed — accelerating them instead would",
                    "make arrows hit harder, which isn't the joke.")
            .defineInRange("magnetSteerStrength", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue MAGNET_AFFECTS_OWN = CURSES
            .comment("Magnet: whether YOUR OWN projectiles curve back at you. Off by design — having your own",
                    "arrows boomerang is just unplayable rather than funny.")
            .define("magnetAffectsOwn", false);

    public static final ModConfigSpec.IntValue MAGNET_MAX_PROJECTILES = CURSES
            .comment("Magnet: cap on projectiles steered per tick, so an arrow barrage can't cost the server.")
            .defineInRange("magnetMaxProjectiles", 32, 1, 256);
    public static final ModConfigSpec.DoubleValue MAGNET_HAZARD_PULL = CURSES
            .comment("Magnet: a SUBTLE per-tick velocity nudge (blocks/tick) that drifts nearby HARMFUL things",
                    "(primed TNT) toward you within magnetRadius. Kept small so it's a creeping menace, not a",
                    "yank. 0 disables the hazard pull.")
            .defineInRange("magnetHazardPull", 0.035, 0.0, 0.5);
    public static final ModConfigSpec.IntValue MAGNET_STORM_INTERVAL = CURSES
            .comment("Magnet+Thunder synergy: ticks between rolls for a real lightning strike drawn onto you during a thunderstorm (only under open sky).")
            .defineInRange("magnetStormIntervalTicks", 60, 5, 1200);
    public static final ModConfigSpec.IntValue MAGNET_STORM_CHANCE = CURSES
            .comment("Magnet+Thunder synergy: percent chance per roll that lightning strikes you while it's thundering.")
            .defineInRange("magnetStormChancePercent", 25, 0, 100);

    // --- Heavy -----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("heavy"); }
    public static final ModConfigSpec.DoubleValue HEAVY_FALL_SPEED_MULT = CURSES
            .comment("Heavy: gravity multiplier. Applied to the GRAVITY attribute rather than by shoving the",
                    "player downward, so acceleration, terminal velocity and fall distance all stay vanilla's",
                    "— just heavier. NOTE the attribute is hard-capped at 1.0 by the game.")
            .defineInRange("heavyFallSpeedMultiplier", 1.8, 1.0, 12.0);

    public static final ModConfigSpec.DoubleValue HEAVY_FALL_DAMAGE_MULT = CURSES
            .comment("Heavy: fall damage multiplier, on top of the extra distance the faster fall racks up.")
            .defineInRange("heavyFallDamageMultiplier", 1.75, 1.0, 10.0);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_MIN_FALL = CURSES
            .comment("Heavy: how far you must fall, in blocks, before you land hard enough to crater.")
            .defineInRange("heavyCraterMinFall", 8, 1, 256);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_CAP_FALL = CURSES
            .comment("Heavy: the fall distance at which the crater stops growing — THE CAP. Beyond this a",
                    "longer drop is no more destructive, so a void-height fall can't level a base.")
            .defineInRange("heavyCraterCapFall", 40, 2, 512);

    public static final ModConfigSpec.DoubleValue HEAVY_CRATER_POWER_MIN = CURSES
            .comment("Heavy: explosion power at exactly the minimum fall distance. (TNT is 4.0.)")
            .defineInRange("heavyCraterPowerMin", 1.5, 0.1, 20.0);

    public static final ModConfigSpec.DoubleValue HEAVY_CRATER_POWER_MAX = CURSES
            .comment("Heavy: explosion power once the fall reaches the cap.")
            .defineInRange("heavyCraterPowerMax", 4.0, 0.1, 20.0);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_SELF_DAMAGE_PERCENT = CURSES
            .comment("Heavy: percent of its own crater blast the faller takes. They're at dead centre AND",
                    "have just eaten amplified fall damage, so full blast damage would execute them every",
                    "single time.")
            .defineInRange("heavyCraterSelfDamagePercent", 25, 0, 100);

    public static final ModConfigSpec.DoubleValue HEAVY_JUMP_COMPENSATION = CURSES
            .comment("Heavy: multiplier on JUMP_STRENGTH, purely so a single block stays climbable. Jump apex",
                    "is roughly v^2/(2*gravity), so heavier gravity alone drops a normal 1.25-block jump to",
                    "about 0.6 — under the one block you need, which makes the curse a nuisance rather than a",
                    "joke. 1.35 puts the apex just over a block: you can still get up a step, but nothing",
                    "about it feels light. Raise if you change heavyFallSpeedMultiplier.")
            .defineInRange("heavyJumpCompensation", 1.35, 1.0, 4.0);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_COOLDOWN = CURSES
            .comment("Heavy: minimum ticks between craters. Stops the blast chaining — a crater blows the",
                    "ground out from under you, so you fall again, crater again, and keep digging yourself",
                    "downward. 60 = 3s.")
            .defineInRange("heavyCraterCooldownTicks", 60, 0, 2400);

    public static final ModConfigSpec.DoubleValue HEAVY_WATER_PULL = CURSES
            .comment("Heavy: extra downward pull per tick while in water or lava — you sink like an anchor.",
                    "Needed as its own value because vanilla divides gravity by 16 in fluid, so the GRAVITY",
                    "attribute alone just gives a slightly brisker version of the same gentle bob. Applied",
                    "client-side, since player movement is client-authoritative. For reference, a full swim-up",
                    "tops out around 0.048/tick, so anything at or above that makes surfacing impossible.")
            .defineInRange("heavyWaterPull", 0.06, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HEAVY_CRATER_KNOCKBACK_MULT = CURSES
            .comment("Heavy: knockback multiplier for the crater blast — the shockwave that throws everything",
                    "standing nearby.")
            .defineInRange("heavyCraterKnockbackMultiplier", 1.6, 0.0, 10.0);

    // --- Gassy -----------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("gassy"); }
    public static final ModConfigSpec.IntValue GASSY_INTERVAL_MIN = CURSES
            .comment("Gassy: shortest gap between spontaneous farts. 400 = 20s.")
            .defineInRange("gassyIntervalMinTicks", 400, 20, 24000);

    public static final ModConfigSpec.IntValue GASSY_INTERVAL_MAX = CURSES
            .comment("Gassy: longest gap between spontaneous farts. 1200 = 60s.")
            .defineInRange("gassyIntervalMaxTicks", 1200, 20, 24000);

    public static final ModConfigSpec.DoubleValue GASSY_VELOCITY_MIN = CURSES
            .comment("Gassy: weakest launch, in blocks/tick.")
            .defineInRange("gassyVelocityMin", 0.45, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue GASSY_VELOCITY_MAX = CURSES
            .comment("Gassy: strongest launch, in blocks/tick.")
            .defineInRange("gassyVelocityMax", 1.0, 0.0, 5.0);

    public static final ModConfigSpec.IntValue GASSY_BIG_CHANCE = CURSES
            .comment("Gassy: percent chance a spontaneous fart is a BIG one (different sound, more velocity).")
            .defineInRange("gassyBigFartChancePercent", 8, 0, 100);

    public static final ModConfigSpec.DoubleValue GASSY_BIG_MULTIPLIER = CURSES
            .comment("Gassy: velocity multiplier for a big fart.")
            .defineInRange("gassyBigFartMultiplier", 1.8, 1.0, 5.0);

    public static final ModConfigSpec.IntValue GASSY_RANDOM_DIRECTION_CHANCE = CURSES
            .comment("Gassy: percent chance a fart ignores nearby hazards entirely and fires off in a random",
                    "direction. This is what stops the curse being a guaranteed lava delivery service — the",
                    "hazard bias must never be a certainty.")
            .defineInRange("gassyRandomDirectionChancePercent", 35, 0, 100);

    public static final ModConfigSpec.IntValue GASSY_HAZARD_SCAN_RADIUS = CURSES
            .comment("Gassy: how far to look for something worth being launched into, in blocks.")
            .defineInRange("gassyHazardScanRadius", 8, 1, 32);

    public static final ModConfigSpec.IntValue GASSY_LEDGE_DROP_MIN = CURSES
            .comment("Gassy: how many clear blocks below a spot make it count as a ledge worth aiming at.")
            .defineInRange("gassyLedgeDropMin", 3, 1, 32);

    public static final ModConfigSpec.IntValue GASSY_STRAIGHT_UP_CHANCE = CURSES
            .comment("Gassy: percent chance a fart is mostly straight UP rather than off in a direction.")
            .defineInRange("gassyStraightUpChancePercent", 18, 0, 100);

    public static final ModConfigSpec.DoubleValue GASSY_VERTICAL_MIN = CURSES
            .comment("Gassy: smallest upward kick added to a directional fart, so you get some air either way.",
                    "This is a RATIO against the horizontal, not an absolute speed — the launch vector is",
                    "normalised before the velocity is applied, so raising these tilts farts upward without",
                    "making them stronger.")
            .defineInRange("gassyVerticalMin", 0.45, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue GASSY_VERTICAL_MAX = CURSES
            .comment("Gassy: largest upward kick added to a directional fart. See gassyVerticalMin — a ratio.")
            .defineInRange("gassyVerticalMax", 0.85, 0.0, 3.0);

    public static final ModConfigSpec.IntValue GASSY_ON_DAMAGE_CHANCE = CURSES
            .comment("Gassy: percent chance that taking a hit frightens one out of you.")
            .defineInRange("gassyOnDamageChancePercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue GASSY_ON_EXPLOSION_CHANCE = CURSES
            .comment("Gassy: percent chance a nearby explosion or firework sets one off. Much likelier than a",
                    "plain hit — the whole joke is the sympathetic detonation.")
            .defineInRange("gassyOnExplosionChancePercent", 60, 0, 100);

    public static final ModConfigSpec.IntValue GASSY_EVENT_BIG_CHANCE = CURSES
            .comment("Gassy: percent chance an event-triggered fart is a BIG one. Deliberately far higher than",
                    "the spontaneous rate.")
            .defineInRange("gassyEventBigFartChancePercent", 55, 0, 100);

    public static final ModConfigSpec.DoubleValue GASSY_EVENT_VELOCITY_MULT = CURSES
            .comment("Gassy: extra velocity multiplier on event-triggered farts, on top of any big-fart bonus.")
            .defineInRange("gassyEventVelocityMultiplier", 1.5, 0.1, 5.0);

    public static final ModConfigSpec.IntValue GASSY_EVENT_COOLDOWN = CURSES
            .comment("Gassy: minimum ticks between event-triggered farts, so one firework show doesn't launch",
                    "you forty times.")
            .defineInRange("gassyEventCooldownTicks", 40, 0, 2400);

    public static final ModConfigSpec.DoubleValue GASSY_EXPLOSION_HEAR_RADIUS = CURSES
            .comment("Gassy: how close an explosion or firework must be to set one off, in blocks.")
            .defineInRange("gassyExplosionHearRadius", 12.0, 1.0, 64.0);

    // --- Delusions -------------------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("delusions"); }
    public static final ModConfigSpec.IntValue DELUSIONS_MAX_CONCURRENT = CURSES
            .comment("Delusions: how many fake players may be visible at once. Low on purpose — a crowd of",
                    "them reads as a glitch, one lurking at the treeline reads as a person.")
            .defineInRange("delusionsMaxConcurrent", 2, 1, 8);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_INTERVAL_MIN = CURSES
            .comment("Delusions: shortest gap between one appearing. 1200 = 60s.")
            .defineInRange("delusionsSpawnIntervalMinTicks", 1200, 40, 24000);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_INTERVAL_MAX = CURSES
            .comment("Delusions: longest gap between one appearing. 3600 = 3min.")
            .defineInRange("delusionsSpawnIntervalMaxTicks", 3600, 40, 24000);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_RANGE_MIN = CURSES
            .comment("Delusions: closest one will appear, in blocks. Never right on top of you.")
            .defineInRange("delusionsSpawnRangeMin", 12, 2, 64);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_RANGE_MAX = CURSES
            .comment("Delusions: furthest one will appear, in blocks.")
            .defineInRange("delusionsSpawnRangeMax", 28, 4, 96);

    public static final ModConfigSpec.IntValue DELUSIONS_LIFETIME_MAX = CURSES
            .comment("Delusions: longest one sticks around before quietly vanishing. 1200 = 60s.")
            .defineInRange("delusionsLifetimeMaxTicks", 1200, 100, 24000);

    public static final ModConfigSpec.IntValue DELUSIONS_DESPAWN_DISTANCE = CURSES
            .comment("Delusions: one further away than this is dropped (you wandered off, or it did).")
            .defineInRange("delusionsDespawnDistance", 48, 16, 128);

    public static final ModConfigSpec.IntValue DELUSIONS_STATE_SWAP_INTERVAL = CURSES
            .comment("Delusions: average ticks a behaviour lasts before it picks another. Each state rolls its",
                    "own length around this, so they don't all switch on the same beat.")
            .defineInRange("delusionsStateSwapInterval", 120, 20, 1200);

    public static final ModConfigSpec.IntValue DELUSIONS_REALISATION_SEEN_TICKS = CURSES
            .comment("Delusions: how long you must have one in view (in your view cone AND in line of sight)",
                    "before it can notice you back. 60 = 3s of staring.")
            .defineInRange("delusionsRealisationSeenTicks", 60, 10, 600);

    public static final ModConfigSpec.IntValue DELUSIONS_REALISATION_CHANCE = CURSES
            .comment("Delusions: percent chance per second, once you've watched one long enough, that it turns",
                    "around and stares back.")
            .defineInRange("delusionsRealisationChancePercent", 15, 0, 100);

    public static final ModConfigSpec.IntValue DELUSIONS_CHARGE_CHANCE = CURSES
            .comment("Delusions: percent chance a realisation ends with it SPRINTING AT YOU rather than just",
                    "vanishing where it stands.")
            .defineInRange("delusionsChargeChancePercent", 40, 0, 100);

    public static final ModConfigSpec.DoubleValue DELUSIONS_HIT_REACH = CURSES
            .comment("Delusions: how far your swing reaches one, in blocks. They are deliberately NOT pickable",
                    "by vanilla's crosshair (that would send the server an attack packet for an entity it has",
                    "never heard of), so the swing is ray-traced against them separately.")
            .defineInRange("delusionsHitReach", 4.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue DELUSIONS_VIEW_CONE_DOT = CURSES
            .comment("Delusions: how centred in your view one must be to count as 'seen'. 0.75 is roughly a",
                    "41-degree cone off your crosshair; lower widens it.")
            .defineInRange("delusionsViewConeDot", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue DELUSIONS_OBSERVE_DISTANCE = CURSES
            .comment("Delusions: how close one gets before it stops and just watches you, in blocks. Close",
                    "enough to be uncomfortable, far enough that it isn't touching you.")
            .defineInRange("delusionsObserveDistance", 4.0, 1.0, 24.0);

    public static final ModConfigSpec.BooleanValue DELUSIONS_MIRROR_SELF = CURSES
            .comment("Delusions: whether YOUR OWN skin and name can be used for a fake player. When you are the",
                    "only player online it is used regardless — there is nobody else to be.")
            .define("delusionsMirrorSelf", true);

    public static final ModConfigSpec.IntValue DELUSIONS_MISID_MAX = CURSES
            .comment("Delusions: how many REAL players can be misidentified at once — shown to the victim wearing",
                    "the wrong player's skin + nametag. 0 disables the misidentification entirely.")
            .defineInRange("delusionsMisidentifyMax", 2, 0, 16);
    public static final ModConfigSpec.IntValue DELUSIONS_MISID_INTERVAL_MIN = CURSES
            .comment("Delusions: minimum ticks between a new real-player misidentification starting. 400 = 20s.")
            .defineInRange("delusionsMisidentifyIntervalMin", 400, 20, 24000);
    public static final ModConfigSpec.IntValue DELUSIONS_MISID_INTERVAL_MAX = CURSES
            .comment("Delusions: maximum ticks between a new real-player misidentification starting. 1600 = 80s.")
            .defineInRange("delusionsMisidentifyIntervalMax", 1600, 40, 48000);
    public static final ModConfigSpec.IntValue DELUSIONS_MISID_DURATION_MIN = CURSES
            .comment("Delusions: minimum ticks a misidentification lasts before the real player looks like themselves again. 200 = 10s.")
            .defineInRange("delusionsMisidentifyDurationMin", 200, 20, 12000);
    public static final ModConfigSpec.IntValue DELUSIONS_MISID_DURATION_MAX = CURSES
            .comment("Delusions: maximum ticks a misidentification lasts. 600 = 30s.")
            .defineInRange("delusionsMisidentifyDurationMax", 600, 40, 24000);

    static { RULES.pop(); }
    static { RULES.push("effect"); }
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EFFECT_COST_OVERRIDES = RULES
            .comment("Per-effect essence cost overrides, format \"witchmod:effect_id=cost\". Any effect not",
                    "listed here keeps its code-defined default cost.")
            .defineListAllowEmpty("effectCostOverrides", List.of(), () -> "", Config::validateOverrideEntry);

    // --- Low Gravity (blessing) ------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("low"); }
    public static final ModConfigSpec.DoubleValue LOW_GRAVITY_GRAVITY_MULT = BLESSINGS
            .comment("Low Gravity: multiplier on the GRAVITY attribute — the whole floaty feel (slower fall + higher float). <1 = lighter.")
            .defineInRange("lowGravityGravityMultiplier", 0.55, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue LOW_GRAVITY_JUMP_MULT = BLESSINGS
            .comment("Low Gravity: multiplier on JUMP_STRENGTH — how much higher your jumps launch.")
            .defineInRange("lowGravityJumpMultiplier", 1.6, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue LOW_GRAVITY_FALL_DAMAGE_MULT = BLESSINGS
            .comment("Low Gravity: multiplier on fall damage taken (soft landings).")
            .defineInRange("lowGravityFallDamageMultiplier", 0.4, 0.0, 1.0);

    // --- Berserker (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("berserker"); }
    public static final ModConfigSpec.DoubleValue BERSERKER_REDUCTION_PER_HIT = BLESSINGS
            .comment("Berserker: attack-cooldown reduction gained per consecutive landed hit, as a fraction (0.07 = 7%).")
            .defineInRange("berserkerReductionPerHit", 0.07, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BERSERKER_MAX_REDUCTION = BLESSINGS
            .comment("Berserker: maximum total attack-cooldown reduction (0.80 = 80% shorter cooldown, i.e. ~5x faster).")
            .defineInRange("berserkerMaxReduction", 0.80, 0.0, 0.95);
    public static final ModConfigSpec.IntValue BERSERKER_MAX_STACKS = BLESSINGS
            .comment("Berserker: the total consecutive-hit stacks you can build (the reduction still caps at berserkerMaxReduction).")
            .defineInRange("berserkerMaxStacks", 14, 1, 64);

    public static final ModConfigSpec.DoubleValue BERSERKER_RESET_SECONDS = BLESSINGS
            .comment("Berserker: seconds without landing a hit before the whole stack resets (also resets instantly on a miss).")
            .defineInRange("berserkerResetSeconds", 4.5, 0.5, 60.0);
    public static final ModConfigSpec.IntValue BERSERKER_GLADIATOR_PARRY_STACKS = BLESSINGS
            .comment("Berserker+Gladiator synergy: berserker stacks granted per successful parry.")
            .defineInRange("berserkerGladiatorParryStacks", 3, 0, 20);

    // --- Enchanter (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("enchanter"); }
    public static final ModConfigSpec.DoubleValue ENCHANTER_XP_REDUCTION = BLESSINGS
            .comment("Enchanter: fraction (0..1) of the XP-level cost of enchanting-table use that is refunded. 0.8 = 80% cheaper (small costs become free).")
            .defineInRange("enchanterXpReduction", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.IntValue ENCHANTER_LEVEL_BONUS = BLESSINGS
            .comment("Enchanter: how many extra enchant levels each of the table's three options is bumped by (better enchants by default).")
            .defineInRange("enchanterLevelBonus", 3, 0, 30);

    // --- Pacifier (blessing) ---------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("pacifier"); }
    public static final ModConfigSpec.DoubleValue PACIFIER_RADIUS = BLESSINGS
            .comment("Pacifier: radius (blocks) of the anti-grief aura for entities — primed TNT, creepers, fireballs.")
            .defineInRange("pacifierRadius", 8.0, 1.0, 32.0);

    public static final ModConfigSpec.IntValue PACIFIER_BLOCK_RADIUS = BLESSINGS
            .comment("Pacifier: radius (blocks) within which spreading fire is snuffed out.")
            .defineInRange("pacifierBlockRadius", 5, 1, 16);

    public static final ModConfigSpec.IntValue PACIFIER_CHECK_INTERVAL = BLESSINGS
            .comment("Pacifier: how often (ticks) the aura sweeps. Lower = snappier, more work.")
            .defineInRange("pacifierCheckIntervalTicks", 5, 1, 40);

    // --- Ocean's Blessing (blessing) -------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("oceans"); }
    public static final ModConfigSpec.DoubleValue OCEANS_SWIM_BOOST = BLESSINGS
            .comment("Ocean's Blessing: modest baseline forward velocity added per tick while swimming (a small nudge on its own — the real speed comes from dolphins).")
            .defineInRange("oceansSwimBoost", 0.025, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_MULT = BLESSINGS
            .comment("Ocean's Blessing: multiplier on the swim boost while you have Dolphin's Grace (from an attracted dolphin) — this is where the big speed lives.")
            .defineInRange("oceansDolphinMultiplier", 4.0, 1.0, 12.0);

    public static final ModConfigSpec.DoubleValue OCEANS_MAX_SPEED = BLESSINGS
            .comment("Ocean's Blessing: cap on horizontal swim speed (blocks/tick) the boost will drive you to.")
            .defineInRange("oceansMaxSpeed", 0.9, 0.1, 3.0);

    public static final ModConfigSpec.DoubleValue OCEANS_PACIFY_RADIUS = BLESSINGS
            .comment("Ocean's Blessing: radius (blocks) within which aggressive mobs are pacified toward you while you're in water.")
            .defineInRange("oceansPacifyRadius", 16.0, 1.0, 48.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_ATTRACT_RADIUS = BLESSINGS
            .comment("Ocean's Blessing: wide radius (blocks) from which dolphins are drawn to you and made to follow.")
            .defineInRange("oceansDolphinAttractRadius", 40.0, 4.0, 128.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_GRACE_RADIUS = BLESSINGS
            .comment("Ocean's Blessing: how close a dolphin must be (blocks) to grant you Dolphin's Grace (and thus the big speed).")
            .defineInRange("oceansDolphinGraceRadius", 12.0, 2.0, 48.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_NAV_SPEED = BLESSINGS
            .comment("Ocean's Blessing: navigation speed multiplier used when steering attracted dolphins toward you.")
            .defineInRange("oceansDolphinNavSpeed", 2.2, 0.5, 5.0);

    // --- Gladiator (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("gladiator"); }
    public static final ModConfigSpec.IntValue GLADIATOR_WINDOW_TICKS = BLESSINGS
            .comment("Gladiator: length of the parry window (ticks) after right-clicking a sword/axe. 4 = 0.2s — tight, demands precision.")
            .defineInRange("gladiatorWindowTicks", 4, 2, 40);

    public static final ModConfigSpec.DoubleValue GLADIATOR_FRONT_DOT = BLESSINGS
            .comment("Gladiator: how 'in front' the attacker must be to parry (dot of look vs direction-to-attacker; higher = narrower frontal arc).")
            .defineInRange("gladiatorFrontDot", 0.2, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_RIPOSTE_DAMAGE_MULT = BLESSINGS
            .comment("Gladiator: NORMAL riposte damage as a multiple of your weapon's swing damage.")
            .defineInRange("gladiatorRiposteDamageMultiplier", 1.4, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_PERFECT_DAMAGE_MULT = BLESSINGS
            .comment("Gladiator: PERFECT-parry riposte damage multiple (a tight, early parry).")
            .defineInRange("gladiatorPerfectDamageMultiplier", 1.75, 0.0, 8.0);

    public static final ModConfigSpec.IntValue GLADIATOR_PERFECT_TICKS = BLESSINGS
            .comment("Gladiator: how tight the PERFECT window is — a parry landing within this many ticks of the danger counts as perfect. 2 = 0.1s.")
            .defineInRange("gladiatorPerfectTicks", 2, 1, 20);

    public static final ModConfigSpec.IntValue GLADIATOR_PERFECT_STUN_TICKS = BLESSINGS
            .comment("Gladiator: how long (ticks) a PERFECT parry stuns the foe (heavy Slowness+Weakness) so the riposte knockback throws them. 10 = 0.5s.")
            .defineInRange("gladiatorPerfectStunTicks", 10, 0, 100);

    public static final ModConfigSpec.IntValue GLADIATOR_LEEWAY_TICKS = BLESSINGS
            .comment("Gladiator: reactive leeway — you can still parry for this many ticks AFTER a hit lands (press it just late). ~4 = 0.18s.")
            .defineInRange("gladiatorLeewayTicks", 4, 0, 20);

    public static final ModConfigSpec.IntValue GLADIATOR_WHIFF_LOCK_EXTRA_TICKS = BLESSINGS
            .comment("Gladiator: while parrying you can't switch/swing/use your hand; a WHIFF extends that lock by this many ticks (2 = 0.1s) as extra risk.")
            .defineInRange("gladiatorWhiffLockExtraTicks", 2, 0, 20);

    public static final ModConfigSpec.IntValue GLADIATOR_SWORD_COOLDOWN_TICKS = BLESSINGS
            .comment("Gladiator: weapon swing cooldown imposed by a SUCCESSFUL parry (riposte/reflect) — a sword's timing. 13 ~ 0.65s (sword attack speed 1.6).")
            .defineInRange("gladiatorSwordCooldownTicks", 13, 0, 60);

    public static final ModConfigSpec.IntValue GLADIATOR_AXE_COOLDOWN_TICKS = BLESSINGS
            .comment("Gladiator: weapon swing cooldown imposed by a WHIFF — an axe's timing (slower). 22 ~ 1.1s (axe attack speed ~0.9).")
            .defineInRange("gladiatorAxeCooldownTicks", 22, 0, 80);

    public static final ModConfigSpec.DoubleValue GLADIATOR_RIPOSTE_KNOCKBACK = BLESSINGS
            .comment("Gladiator: extra knockback strength dealt by the riposte (perfect parries throw a little harder). Kept modest so foes don't fly.")
            .defineInRange("gladiatorRiposteKnockback", 0.8, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_SHAKE_STRENGTH = BLESSINGS
            .comment("Gladiator: base camera-shake strength on a parry (perfect shakes harder, whiff softer — scaled from this).")
            .defineInRange("gladiatorShakeStrength", 1.6, 0.0, 10.0);

    public static final ModConfigSpec.IntValue GLADIATOR_SHAKE_TICKS = BLESSINGS
            .comment("Gladiator: how long (ticks) the parry camera shake decays over.")
            .defineInRange("gladiatorShakeTicks", 6, 1, 40);

    public static final ModConfigSpec.IntValue GLADIATOR_RIPOSTE_DELAY_TICKS = BLESSINGS
            .comment("Gladiator: short delay (ticks) between a successful parry and the riposte hit landing.")
            .defineInRange("gladiatorRiposteDelayTicks", 4, 1, 20);

    public static final ModConfigSpec.DoubleValue GLADIATOR_COOLDOWN_SECONDS = BLESSINGS
            .comment("Gladiator: parry cooldown after a SUCCESSFUL parry (seconds).")
            .defineInRange("gladiatorCooldownSeconds", 2.5, 0.2, 30.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_WHIFF_COOLDOWN_SECONDS = BLESSINGS
            .comment("Gladiator: parry cooldown after a WHIFFED parry (seconds) — the success cooldown plus a 0.25s mistiming penalty.")
            .defineInRange("gladiatorWhiffCooldownSeconds", 2.75, 0.2, 30.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_SPEED = BLESSINGS
            .comment("Gladiator: launch speed of a reflected projectile, sent where you're LOOKING. A touch higher than incoming, but still fairly low.")
            .defineInRange("gladiatorReflectSpeed", 1.6, 0.2, 5.0);

    public static final ModConfigSpec.IntValue GLADIATOR_PROJECTILE_LEEWAY_TICKS = BLESSINGS
            .comment("Gladiator: extra ticks of slack on EACH side of the window for PROJECTILE parries (they're harder to time than melee, so more forgiving). 3 = ~0.15s.")
            .defineInRange("gladiatorProjectileLeewayTicks", 3, 0, 20);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_AIM_CONE = BLESSINGS
            .comment("Gladiator: half-angle (degrees) of the cone around your look within which a reflected projectile will assist-aim at an entity.")
            .defineInRange("gladiatorReflectAimConeDegrees", 28.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_AIM_BIAS = BLESSINGS
            .comment("Gladiator: how strongly a reflect leans toward an in-cone entity (0 = pure look direction, 1 = straight at the entity).")
            .defineInRange("gladiatorReflectAimBias", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_AIM_RANGE = BLESSINGS
            .comment("Gladiator: how far (blocks) the reflect assist-aim looks for an entity in the cone.")
            .defineInRange("gladiatorReflectAimRange", 26.0, 2.0, 64.0);

    // --- Tank (blessing) -------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("tank"); }
    public static final ModConfigSpec.DoubleValue TANK_BONUS_HEALTH = BLESSINGS
            .comment("Tank: bonus max health added via a MAX_HEALTH attribute (no status-effect UI at all). 20 = a whole extra health bar.")
            .defineInRange("tankBonusHealth", 20.0, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue TANK_REGEN_MULT = BLESSINGS
            .comment("Tank: multiplier on natural-regeneration healing (small heals) — significantly slower regen. 0.35 = 35% of normal.")
            .defineInRange("tankRegenMultiplier", 0.35, 0.0, 1.0);

    // --- Spider (blessing) -----------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("spider"); }
    public static final ModConfigSpec.DoubleValue SPIDER_CLIMB_SPEED = BLESSINGS
            .comment("Spider: upward climb speed against a wall (blocks/tick). Vanilla spiders climb at ~0.2; higher is faster.")
            .defineInRange("spiderClimbSpeed", 0.3, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue SPIDER_WALL_JUMP_UP = BLESSINGS
            .comment("Spider: upward launch when you jump off a wall.")
            .defineInRange("spiderWallJumpUp", 0.56, 0.1, 2.0);

    public static final ModConfigSpec.DoubleValue SPIDER_WALL_JUMP_AWAY = BLESSINGS
            .comment("Spider: horizontal 'kick off' away from the wall when wall-jumping.")
            .defineInRange("spiderWallJumpAway", 0.42, 0.0, 2.0);

    public static final ModConfigSpec.IntValue SPIDER_WALL_JUMP_COOLDOWN = BLESSINGS
            .comment("Spider: minimum ticks between wall jumps (so one jump press = one kick).")
            .defineInRange("spiderWallJumpCooldownTicks", 6, 1, 40);

    // --- Ninja (blessing) ------------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("ninja"); }
    public static final ModConfigSpec.DoubleValue NINJA_SPRINT_SPEED = BLESSINGS
            .comment("Ninja: extra movement speed as a fraction (0.25 = +25%), applied as a MOVEMENT_SPEED multiplier.")
            .defineInRange("ninjaSprintSpeed", 0.25, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue NINJA_ATTACK_SPEED = BLESSINGS
            .comment("Ninja: extra attack speed as a fraction (0.8 = +80% -> much faster swing recovery/animation).")
            .defineInRange("ninjaAttackSpeed", 0.8, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue NINJA_DOUBLE_JUMP_POWER = BLESSINGS
            .comment("Ninja: upward velocity of the mid-air second jump (a bit stronger than a normal jump).")
            .defineInRange("ninjaDoubleJumpPower", 0.64, 0.1, 2.0);

    public static final ModConfigSpec.DoubleValue NINJA_SWING_PITCH = BLESSINGS
            .comment("Ninja: pitch (speed) of the parry-whiff woosh played on each weapon swing. 1.7 = quick and sharp.")
            .defineInRange("ninjaSwingPitch", 1.7, 0.5, 2.0);

    public static final ModConfigSpec.DoubleValue NINJA_KNOCKBACK_MULT = BLESSINGS
            .comment("Ninja: knockback multiplier on YOUR melee hits — 0.34 = 66% less, so victims barely stagger",
                    "and you stay on them. Disregarded (no reduction) if you also carry a knockback-boosting",
                    "effect like Heavy Hitter or Main Character.")
            .defineInRange("ninjaKnockbackMultiplier", 0.34, 0.0, 1.0);

    // --- Backstabbing (blessing) -----------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("backstab"); }
    public static final ModConfigSpec.DoubleValue BACKSTAB_DAMAGE_MULT = BLESSINGS
            .comment("Backstabbing: melee damage multiplier when you hit a target from behind. Multiplies the final damage, so it STACKS with crits/enchants.")
            .defineInRange("backstabDamageMultiplier", 1.6, 1.0, 5.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_KNOCKBACK_MULT = BLESSINGS
            .comment("Backstabbing: knockback multiplier on a backstab (less knockback so you stay on them).")
            .defineInRange("backstabKnockbackMultiplier", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_PLAYER_DOT = BLESSINGS
            .comment("Backstabbing vs PLAYERS: attacker counts as 'behind' when the dot of the victim's look and the direction-to-attacker is at/below this. Lower = stricter (must be well behind).")
            .defineInRange("backstabPlayerDot", -0.3, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_MOB_DOT = BLESSINGS
            .comment("Backstabbing vs MOBS: same threshold but MORE GENEROUS (mob AI faces you, so a wider rear arc counts). Higher = easier.")
            .defineInRange("backstabMobDot", 0.3, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_SOUND_PITCH = BLESSINGS
            .comment("Backstabbing: pitch of the riposte 'impact' sound played on a successful backstab.")
            .defineInRange("backstabSoundPitch", 1.2, 0.5, 2.0);

    // --- Prop Hunt (blessing) --------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("prophunt"); }
    public static final ModConfigSpec.IntValue PROPHUNT_STILL_TICKS = BLESSINGS
            .comment("Prop Hunt: how long (ticks) you must crouch and stand still before you disguise as the block below you. 20 = 1s.")
            .defineInRange("propHuntStillTicks", 20, 5, 200);

    // --- Solicitor (curse) -----------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("solicitor"); }
    public static final ModConfigSpec.IntValue SOLICITOR_CHECK_INTERVAL = CURSES
            .comment("Solicitor: how often (ticks) the curse re-checks/steers the trader.")
            .defineInRange("solicitorCheckIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.DoubleValue SOLICITOR_MOVE_SPEED = CURSES
            .comment("Solicitor: the trader's base MOVEMENT_SPEED attribute — low so it ambles rather than blitzing about (vanilla trader is ~0.5).")
            .defineInRange("solicitorMoveSpeed", 0.32, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue SOLICITOR_FOLLOW_SPEED = CURSES
            .comment("Solicitor: navigation speed multiplier while walking toward you.")
            .defineInRange("solicitorFollowSpeed", 0.85, 0.3, 3.0);

    public static final ModConfigSpec.DoubleValue SOLICITOR_FOLLOW_DISTANCE = CURSES
            .comment("Solicitor: it only walks toward you when further than this (blocks); closer than it, it STOPS so you can actually trade with it.")
            .defineInRange("solicitorFollowDistance", 3.5, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue SOLICITOR_TELEPORT_DISTANCE = CURSES
            .comment("Solicitor: if the trader falls further than this (blocks) behind you, it teleports closer (it never gives up).")
            .defineInRange("solicitorTeleportDistance", 18.0, 6.0, 128.0);

    public static final ModConfigSpec.IntValue SOLICITOR_CHAT_MIN_TICKS = CURSES
            .comment("Solicitor: shortest gap (ticks) between the trader's chat pitches.")
            .defineInRange("solicitorChatMinTicks", 200, 20, 6000);

    public static final ModConfigSpec.IntValue SOLICITOR_CHAT_MAX_TICKS = CURSES
            .comment("Solicitor: longest gap (ticks) between the trader's chat pitches.")
            .defineInRange("solicitorChatMaxTicks", 500, 20, 12000);

    public static final ModConfigSpec.DoubleValue SOLICITOR_CHAT_RADIUS = CURSES
            .comment("Solicitor: how far (blocks) from the trader its chat pitches are heard.")
            .defineInRange("solicitorChatRadius", 28.0, 4.0, 128.0);

    public static final ModConfigSpec.IntValue SOLICITOR_HIDE_MIN_SECONDS = CURSES
            .comment("Solicitor: minimum time (seconds) the trader stays gone after you actually complete a trade with it.")
            .defineInRange("solicitorHideMinSeconds", 90, 5, 6000);

    public static final ModConfigSpec.IntValue SOLICITOR_HIDE_MAX_SECONDS = CURSES
            .comment("Solicitor: maximum time (seconds) gone after a trade — the roll is biased toward the LONGER end.")
            .defineInRange("solicitorHideMaxSeconds", 600, 5, 12000);

    // --- The Snail (curse) -----------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("snail"); }
    public static final ModConfigSpec.DoubleValue SNAIL_BASE_SPEED = CURSES
            .comment("The Snail: THE fundamental balancing constant — base chase speed in BLOCKS PER SECOND (its speed when right on top of you).")
            .defineInRange("snailBaseSpeedBlocksPerSecond", 0.9, 0.05, 20.0);

    public static final ModConfigSpec.DoubleValue SNAIL_DISTANCE_SCALE = CURSES
            .comment("The Snail: how much its speed grows per block of distance (0.08 = +8% base speed per block away) — slow when close, much faster when far.")
            .defineInRange("snailDistanceScale", 0.08, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MAX_SPEED = CURSES
            .comment("The Snail: hard cap on its chase speed (blocks/second), however far away you get.")
            .defineInRange("snailMaxSpeedBlocksPerSecond", 22.0, 0.5, 100.0);

    public static final ModConfigSpec.DoubleValue SNAIL_TOUCH_DISTANCE = CURSES
            .comment("The Snail: how close (blocks) it must get to touch you and detonate.")
            .defineInRange("snailTouchDistance", 1.3, 0.3, 4.0);

    public static final ModConfigSpec.DoubleValue SNAIL_SPAWN_DISTANCE = CURSES
            .comment("The Snail: how far away (blocks) it starts, and re-appears after catching you.")
            .defineInRange("snailSpawnDistance", 45.0, 8.0, 256.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MATERIALISE_RADIUS = CURSES
            .comment("The Snail: within this distance (blocks) the real entity is spawned/shown; beyond it only the virtual position is tracked (nothing loaded).")
            .defineInRange("snailMaterialiseRadius", 40.0, 8.0, 128.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MUSIC_DISTANCE = CURSES
            .comment("The Snail: max distance (blocks) the music can be heard; it fades in from here and stops beyond it.")
            .defineInRange("snailMusicDistance", 24.0, 4.0, 64.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MUSIC_FULL_DISTANCE = CURSES
            .comment("The Snail: within this distance (blocks) the music is at full volume; between it and the max distance it fades.")
            .defineInRange("snailMusicFullDistance", 18.0, 2.0, 64.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MUSIC_VOLUME = CURSES
            .comment("The Snail: volume of the looping music.")
            .defineInRange("snailMusicVolume", 0.68, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SNAIL_EXPLODE_POWER = CURSES
            .comment("The Snail: explosion power when it reaches you (also dealt lethal directly, so it's an instant kill barring a totem/Last Stand).")
            .defineInRange("snailExplodePower", 6.0, 0.0, 20.0);

    // --- The Dweller (curse) ---------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("dweller"); }
    public static final ModConfigSpec.DoubleValue DWELLER_ANGER_MAX = CURSES
            .comment("The Dweller: the anger ceiling; the closer to it, the closer the chase.")
            .defineInRange("dwellerAngerMax", 100.0, 1.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_BASE_ANGER_PER_SECOND = CURSES
            .comment("The Dweller: anger it gains every second no matter what — the slow, inevitable escalation. VERY low: at max (100) with the dark/alone accelerants a dread session should take ~13-18 min to peak. The big driver is being ALONE + in the DARK.")
            .defineInRange("dwellerBaseAngerPerSecond", 0.06, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_VIEW_CONE_DOT = CURSES
            .comment("The Dweller: how centred in your view it must be to count as 'looked at' (dot product; higher = you must look more directly at it).")
            .defineInRange("dwellerViewConeDot", 0.90, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER1_THRESHOLD = CURSES
            .comment("The Dweller: anger at which it moves from Far observation to Close observation.")
            .defineInRange("dwellerTier1Threshold", 25.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER2_THRESHOLD = CURSES
            .comment("The Dweller: anger at which it moves from Close observation to Inspection (it starts coming close).")
            .defineInRange("dwellerTier2Threshold", 55.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER3_THRESHOLD = CURSES
            .comment("The Dweller: anger at which it enters the Chase tier (fake chases, then the real one).")
            .defineInRange("dwellerTier3Threshold", 88.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue DWELLER_FAR_DISTANCE = CURSES
            .comment("The Dweller: distance (blocks) it watches from during Far observation.")
            .defineInRange("dwellerFarDistance", 22.0, 2.0, 64.0);
    public static final ModConfigSpec.DoubleValue DWELLER_INSPECT_DISTANCE = CURSES
            .comment("The Dweller: distance (blocks) it stands at during Inspection — right in your space.")
            .defineInRange("dwellerInspectDistance", 4.0, 1.0, 32.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TOUCH_DISTANCE = CURSES
            .comment("The Dweller: how close (blocks) it must get during the chase to catch and kill you.")
            .defineInRange("dwellerTouchDistance", 1.7, 0.5, 8.0);
    public static final ModConfigSpec.BooleanValue DWELLER_PASSIVE_COLLISION_KILL = CURSES
            .comment("The Dweller: during a chase, colliding with ANY passive entity (an animal, villager, golem)",
                    "instantly triggers the gory finale too — nowhere is safe once it's hunting you.")
            .define("dwellerPassiveCollisionKill", true);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_STRIDE = CURSES
            .comment("The Dweller: blocks the dweller must walk between footstep sounds during a chase (its stride length). Larger = fewer, more spaced-out steps. Tuned to match the movement speed.")
            .defineInRange("dwellerChaseStride", 1.15, 0.3, 4.0);
    public static final ModConfigSpec.IntValue DWELLER_SHAKE_TICKS = CURSES
            .comment("The Dweller: how long (ticks) the on-hit camera shake lasts (bang-behind / lunge / the finale).")
            .defineInRange("dwellerShakeTicks", 9, 1, 60);
    public static final ModConfigSpec.DoubleValue DWELLER_SHAKE_STRENGTH = CURSES
            .comment("The Dweller: peak camera-shake strength (degrees of random jolt) for the on-hit shake.")
            .defineInRange("dwellerShakeStrength", 1.2, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue DWELLER_WATCH_VANISH_DISTANCE = CURSES
            .comment("The Dweller: any passive WATCH vanishes the moment you get within this many blocks of it (you can never close on a watch — it blinks away).")
            .defineInRange("dwellerWatchVanishDistance", 4.0, 1.0, 12.0);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_MIN_COOLDOWN_TICKS = CURSES
            .comment("The Dweller: the SHORTEST chase cooldown (ticks) — reached at MAX dread. The cooldown is the window where the game won't roll a new chase; at max dread it's tiny, so hunts come back almost instantly. Default 5s.")
            .defineInRange("dwellerChaseMinCooldownTicks", 100, 0, 6000);
    public static final ModConfigSpec.IntValue DWELLER_MAX_DREAD_LOOK_TICKS = CURSES
            .comment("The Dweller: at MAX dread, how long (ticks) you must hold eye contact with it before it goes aggressive and the hunt begins. Low = looking at it is near-instant death sentence.")
            .defineInRange("dwellerMaxDreadLookTicks", 8, 1, 100);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_STUCK_TICKS = CURSES
            .comment("The Dweller: how long (ticks) the chase can fail to make progress (truly walled in, unable to path/climb/break through) before it subtly teleports closer as a LAST-RESORT fallback. Higher = the good AI is relied on more and teleports are rarer.")
            .defineInRange("dwellerChaseStuckTicks", 70, 4, 400);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_RAMP_TICKS = CURSES
            .comment("The Dweller: how long (ticks) a chase takes to ramp from its slow starting speed up to full pace. The hunt starts a touch slow and builds momentum.")
            .defineInRange("dwellerChaseRampTicks", 120, 1, 1200);
    public static final ModConfigSpec.DoubleValue DWELLER_BRIDGE_CHASE_MIN_CHANCE = CURSES
            .comment("The Dweller: chance (0-1) that a watch/sizeup/lunge BRIDGES straight into a chase at the chase floor (low dread). The hunt can erupt from where the creature stands rather than always resetting.")
            .defineInRange("dwellerBridgeChaseMinChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_BRIDGE_CHASE_MAX_CHANCE = CURSES
            .comment("The Dweller: chance (0-1) that a watch/sizeup/lunge bridges into a chase at MAX dread. Higher dread = the creature commits to the hunt from its current spot far more readily.")
            .defineInRange("dwellerBridgeChaseMaxChance", 0.55, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DWELLER_SIZEUP_STARE_TICKS = CURSES
            .comment("The Dweller: during a SIZEUP (it stands right in front of you, passive), how long (ticks) you can stare back before its aggression ramps and it lunges/hunts.")
            .defineInRange("dwellerSizeupStareTicks", 34, 5, 200);
    public static final ModConfigSpec.DoubleValue DWELLER_LUNGE_SPEED = CURSES
            .comment("The Dweller: the phantom-CHARGE speed (blocks/tick) of a lunge — it visibly rushes you at this pace, and the jumpscare fires the instant it arrives. Lower = a slower, more followable charge (not a teleport).")
            .defineInRange("dwellerLungeSpeed", 1.2, 0.3, 5.0);
    public static final ModConfigSpec.IntValue DWELLER_LUNGE_MAX_TICKS = CURSES
            .comment("The Dweller: safety cap (ticks) on a lunge charge — it normally ends the instant it reaches you, but times out after this if something blocks the arrival.")
            .defineInRange("dwellerLungeMaxTicks", 40, 5, 200);
    // In-chase ravager LUNGE (high dread): a locked, dodgeable launch AT the player.
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_MIN_FRAC = CURSES
            .comment("The Dweller: minimum dread fraction (0-1) for the mid-chase ravager LUNGE to be possible. Only higher dread earns the launch-at-you attacks.")
            .defineInRange("dwellerChaseLungeMinFrac", 0.66, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_RANGE = CURSES
            .comment("The Dweller: max distance (blocks) at which a mid-chase lunge can start.")
            .defineInRange("dwellerChaseLungeRange", 16.0, 3.0, 48.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_SPEED = CURSES
            .comment("The Dweller: distancegain lunge speed (blocks/second) — the straight launch at you used generally / as a close finisher. Fast but locked, so a sidestep dodges it.")
            .defineInRange("dwellerChaseLungeSpeed", 15.0, 4.0, 40.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_CLIMB_SPEED = CURSES
            .comment("The Dweller: CLIMB lunge speed (blocks/second) — the upward-arcing launch that counters height-camping. Slightly slower so the vertical arc reads.")
            .defineInRange("dwellerChaseLungeClimbSpeed", 12.0, 4.0, 40.0);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_LUNGE_TICKS = CURSES
            .comment("The Dweller: how long (ticks) a mid-chase lunge stays in its locked flight before it settles back to the walking chase if it missed.")
            .defineInRange("dwellerChaseLungeTicks", 14, 3, 60);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_LUNGE_COOLDOWN_TICKS = CURSES
            .comment("The Dweller: cooldown (ticks) between mid-chase lunges, so it doesn't chain launches back-to-back.")
            .defineInRange("dwellerChaseLungeCooldownTicks", 70, 10, 600);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_CLIMB_HEIGHT = CURSES
            .comment("The Dweller: how many blocks ABOVE the chaser you must be for a lunge to use the upward CLIMB variant (counter height-camping) instead of the straight distancegain launch.")
            .defineInRange("dwellerChaseLungeClimbHeight", 3.0, 1.0, 20.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_CLIMB_UP = CURSES
            .comment("The Dweller: the UPWARD velocity (blocks/tick) of a CLIMB leap — how high it jumps to reach a player camping on height. ~0.42 clears 1 block; 0.85 clears ~4.")
            .defineInRange("dwellerChaseLungeClimbUp", 0.85, 0.3, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_MOVE_SPEED = CURSES
            .comment("The Dweller: the chaser's MOVEMENT_SPEED attribute during a hunt. For a MOB this scale runs ~0.25 = a walk, so ~0.34 is a hard relentless sprint clearly faster than a fleeing player. The navigation multiplier below scales this further.")
            .defineInRange("dwellerChaseMoveSpeed", 0.34, 0.02, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SPRINT = CURSES
            .comment("The Dweller: the base navigation SPEED MULTIPLIER on the chase MOVEMENT_SPEED (1.0 = sprint pace). A small in-hunt ramp (0.85→1.0) and dread bonus (+18% at max) ride on top.")
            .defineInRange("dwellerChaseSprint", 1.0, 0.3, 3.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_WATER_SPEED = CURSES
            .comment("The Dweller: swim speed (blocks/tick) it's pushed toward you while IN WATER during a chase, so it powers across water fast instead of getting stuck. 0.5 ~ 10 b/s.")
            .defineInRange("dwellerChaseWaterSpeed", 0.5, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_START_DISTANCE = CURSES
            .comment("The Dweller: how far behind you a FRESH chase places the creature by default (further away now, so a hunt has run-up).")
            .defineInRange("dwellerChaseStartDistance", 13.0, 3.0, 48.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_CHASE_DISTANCE = CURSES
            .comment("The Dweller: the CLOSECHASE start distance — the old, much closer default. A fresh chase uses this instead of the far start on a dread-scaling chance.")
            .defineInRange("dwellerCloseChaseDistance", 6.0, 3.0, 24.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_CHASE_MIN_CHANCE = CURSES
            .comment("The Dweller: chance (0-1) at LOW dread that a fresh chase is a CLOSECHASE (starts at the close distance rather than far).")
            .defineInRange("dwellerCloseChaseMinChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_CHASE_MAX_CHANCE = CURSES
            .comment("The Dweller: chance (0-1) at MAX dread that a fresh chase is a CLOSECHASE. Higher dread = far more likely to start right on top of you.")
            .defineInRange("dwellerCloseChaseMaxChance", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_MIN_START_DISTANCE = CURSES
            .comment("The Dweller: SAFETY floor — if a chase would begin closer than this (e.g. a sizeup/close watch bridging within a few blocks), the creature is pulled BACK to a safe start distance so it can't be an instant, uncounterable touch-kill.")
            .defineInRange("dwellerChaseMinStartDistance", 5.0, 2.0, 16.0);

    public static final ModConfigSpec.IntValue DWELLER_EVENT_INTERVAL_MIN_TICKS = CURSES
            .comment("The Dweller: minimum gap (ticks) between staged events (repositions, watches, breaks). Scaled shorter at higher tiers.")
            .defineInRange("dwellerEventIntervalMinTicks", 160, 20, 6000);
    public static final ModConfigSpec.IntValue DWELLER_EVENT_INTERVAL_MAX_TICKS = CURSES
            .comment("The Dweller: maximum gap (ticks) between staged events. Scaled shorter at higher tiers.")
            .defineInRange("dwellerEventIntervalMaxTicks", 500, 20, 12000);
    public static final ModConfigSpec.DoubleValue DWELLER_BED_BREAK_CHANCE = CURSES
            .comment("The Dweller: chance a Sleep-watch event smashes the bed you're in (kicking you out of it).")
            .defineInRange("dwellerBedBreakChance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_DOOR_BREAK_CHANCE = CURSES
            .comment("The Dweller: chance an event breaks a nearby door/trapdoor/glass to unnerve you (from tier 2).")
            .defineInRange("dwellerDoorBreakChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_EXPLODE_ENTITY_POWER = CURSES
            .comment("The Dweller: explosion power when it detonates a nearby animal/villager as a warning (0 = kill it silently).")
            .defineInRange("dwellerExplodeEntityPower", 2.0, 0.0, 10.0);

    public static final ModConfigSpec.IntValue DWELLER_DORMANT_MIN_TICKS = CURSES
            .comment("The Dweller: minimum absence (ticks) between appearances at tier 0. Early FAR glimpses are meant to be fairly frequent (just distant); the absence shrinks smoothly as dread climbs.")
            .defineInRange("dwellerDormantMinTicks", 1000, 20, 24000);
    public static final ModConfigSpec.IntValue DWELLER_DORMANT_MAX_TICKS = CURSES
            .comment("The Dweller: maximum absence (ticks) between appearances at tier 0. Shrinks smoothly as dread climbs.")
            .defineInRange("dwellerDormantMaxTicks", 3600, 20, 48000);
    public static final ModConfigSpec.IntValue DWELLER_MANIFEST_MIN_TICKS = CURSES
            .comment("The Dweller: minimum time (ticks) a single appearance lasts before it melts away (at tier 0 — brief glimpses). Longer at higher tiers.")
            .defineInRange("dwellerManifestMinTicks", 40, 10, 6000);
    public static final ModConfigSpec.IntValue DWELLER_MANIFEST_MAX_TICKS = CURSES
            .comment("The Dweller: maximum time (ticks) a single appearance lasts. Longer at higher tiers.")
            .defineInRange("dwellerManifestMaxTicks", 120, 10, 12000);
    public static final ModConfigSpec.DoubleValue DWELLER_BOREDOM_BOOST = CURSES
            .comment("The Dweller (BOREDOM): extra dread per second at low tiers (0-1) when no encounter has happened for a while — stops the early game stalling because nothing is pushing the tiers up.")
            .defineInRange("dwellerBoredomBoostPerSecond", 0.12, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_BOREDOM_DELAY_TICKS = CURSES
            .comment("The Dweller (BOREDOM): how long (ticks) with no encounter before the boredom boost kicks in. Default 4s.")
            .defineInRange("dwellerBoredomDelayTicks", 80, 20, 2400);
    public static final ModConfigSpec.IntValue DWELLER_TIER0_STUCK_TICKS = CURSES
            .comment("The Dweller (BOREDOM): ticks stuck at TIER 0 before an ADDITIONAL dread boost is added. Default 4.5min.")
            .defineInRange("dwellerTier0StuckTicks", 5400, 200, 48000);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER0_STUCK_BOOST = CURSES
            .comment("The Dweller (BOREDOM): extra dread per second once you've been stuck at tier 0 past dwellerTier0StuckTicks.")
            .defineInRange("dwellerTier0StuckBoostPerSecond", 0.15, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_INTERACT_DREAD = CURSES
            .comment("The Dweller: bonus dread added each time you INTERACT with one of its effects instead of ignoring it — swinging at the stalker, or getting too close to a watch/mimic. The counterplay is to ignore, so engaging costs you.")
            .defineInRange("dwellerInteractDread", 7.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_TELL_MIN_TICKS = CURSES
            .comment("The Dweller: minimum length (ticks) of the TELL beat — the anticipation window before an appearance (heartbeat starts + quickens, a sign lands). Shrinks as dread climbs. Default ~3s.")
            .defineInRange("dwellerTellMinTicks", 60, 10, 1200);
    public static final ModConfigSpec.IntValue DWELLER_TELL_MAX_TICKS = CURSES
            .comment("The Dweller: maximum length (ticks) of the TELL anticipation beat. Default ~6s.")
            .defineInRange("dwellerTellMaxTicks", 120, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_RELEASE_MIN_TICKS = CURSES
            .comment("The Dweller: minimum length (ticks) of the RELEASE beat — the pointed quiet AFTER a scare, before the next lull. The exhale. Default ~4s.")
            .defineInRange("dwellerReleaseMinTicks", 80, 10, 1200);
    public static final ModConfigSpec.IntValue DWELLER_RELEASE_MAX_TICKS = CURSES
            .comment("The Dweller: maximum length (ticks) of the RELEASE exhale beat. Default ~8s.")
            .defineInRange("dwellerReleaseMaxTicks", 160, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_STARE_VANISH_TICKS = CURSES
            .comment("The Dweller: staring straight at ANY watch for this long (ticks) makes it vanish — the double-take. Default 3s (60t). At high dread the vanish is instead a lunge/chase bridge, but the trigger time is the same.")
            .defineInRange("dwellerStareVanishTicks", 60, 5, 600);
    public static final ModConfigSpec.DoubleValue DWELLER_WATCH_ANGER_PER_SECOND = CURSES
            .comment("The Dweller: extra anger per second while you are actively looking at it — it hates being seen, so watching it hastens the end.")
            .defineInRange("dwellerWatchAngerPerSecond", 1.2, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_AMBIENT_MIN_TICKS = CURSES
            .comment("The Dweller: minimum gap (ticks) between the ambient dread cues (footsteps, breaths, a knock) it makes even while absent.")
            .defineInRange("dwellerAmbientMinTicks", 140, 10, 6000);
    public static final ModConfigSpec.IntValue DWELLER_AMBIENT_MAX_TICKS = CURSES
            .comment("The Dweller: maximum gap (ticks) between ambient dread cues.")
            .defineInRange("dwellerAmbientMaxTicks", 520, 10, 12000);


    public static final ModConfigSpec.IntValue DWELLER_EYES_MIN = CURSES
            .comment("The Dweller (Watchers event): minimum number of glowing-eye pairs that appear in the dark.")
            .defineInRange("dwellerEyesMin", 3, 1, 30);
    public static final ModConfigSpec.IntValue DWELLER_EYES_MAX = CURSES
            .comment("The Dweller (Watchers event): maximum number of glowing-eye pairs.")
            .defineInRange("dwellerEyesMax", 7, 1, 40);
    public static final ModConfigSpec.DoubleValue DWELLER_EYES_DISTANCE = CURSES
            .comment("The Dweller (Watchers event): how far out (blocks) the eyes hang, ringed around you in the dark.")
            .defineInRange("dwellerEyesDistance", 14.0, 4.0, 48.0);
    public static final ModConfigSpec.IntValue DWELLER_EYES_MIN_TICKS = CURSES
            .comment("The Dweller (Watchers event): minimum time (ticks) the eyes watch before fading.")
            .defineInRange("dwellerEyesMinTicks", 80, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_EYES_MAX_TICKS = CURSES
            .comment("The Dweller (Watchers event): maximum time (ticks) the eyes watch before fading.")
            .defineInRange("dwellerEyesMaxTicks", 220, 10, 4800);

    public static final ModConfigSpec.IntValue DWELLER_KNOCK_BLOCKS = CURSES
            .comment("The Dweller (Knock event): how many nearby doors/glass get knocked on (then shattered).")
            .defineInRange("dwellerKnockBlocks", 3, 1, 12);
    public static final ModConfigSpec.IntValue DWELLER_KNOCK_SHATTER_MIN_TICKS = CURSES
            .comment("The Dweller (Knock event): minimum delay (ticks) after the knock before every doomed block shatters at once (1s default).")
            .defineInRange("dwellerKnockShatterMinTicks", 20, 1, 600);
    public static final ModConfigSpec.IntValue DWELLER_KNOCK_SHATTER_MAX_TICKS = CURSES
            .comment("The Dweller (Knock event): maximum delay (ticks) after the knock before the shatter (6s default) — the long, silent pause is the point.")
            .defineInRange("dwellerKnockShatterMaxTicks", 120, 1, 1200);

    public static final ModConfigSpec.IntValue DWELLER_HALLUCINATION_MIN_TICKS = CURSES
            .comment("The Dweller: minimum gap (ticks) between AUDITORY hallucinations — fake footsteps, mining, chests, other-player sounds. Deliberately LONG so they stay rare and impactful; tier 0 is ~2.2x rarer still.")
            .defineInRange("dwellerHallucinationMinTicks", 1000, 10, 12000);
    public static final ModConfigSpec.IntValue DWELLER_HALLUCINATION_MAX_TICKS = CURSES
            .comment("The Dweller: maximum gap (ticks) between auditory hallucinations.")
            .defineInRange("dwellerHallucinationMaxTicks", 3400, 10, 24000);

    // --- The Dweller: DREAD (the fluid, multi-factor progression meter + its counterplay) ----------------
    public static final ModConfigSpec.DoubleValue DWELLER_DARK_DREAD = CURSES
            .comment("The Dweller: EXTRA dread per second while standing in DARKNESS (light <= dwellerDarkLightLevel). The dark is where it thrives.")
            .defineInRange("dwellerDarkDreadPerSecond", 0.06, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_ISOLATION_DREAD = CURSES
            .comment("The Dweller: EXTRA dread per second while you are ALONE (no other player within dwellerCompanyRadius). The dominant driver — being alone with it is where the horror lives, so this is large.")
            .defineInRange("dwellerIsolationDreadPerSecond", 0.05, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_NIGHT_DREAD = CURSES
            .comment("The Dweller: EXTRA dread per second at NIGHT.")
            .defineInRange("dwellerNightDreadPerSecond", 0.02, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_ENCLOSED_DREAD = CURSES
            .comment("The Dweller: EXTRA dread per second while ENCLOSED (no sky above you) — trapped underground with it.")
            .defineInRange("dwellerEnclosedDreadPerSecond", 0.02, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_LIGHT_CALM = CURSES
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second while standing in bright light (light >= dwellerLightLevel), scaled by how bright.")
            .defineInRange("dwellerLightCalmPerSecond", 0.05, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_COMPANY_CALM = CURSES
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second per nearby player (capped at 3) — safety in numbers.")
            .defineInRange("dwellerCompanyCalmPerSecond", 0.04, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_DAYLIGHT_CALM = CURSES
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second in open DAYLIGHT (day + sky access) — it hates the sun.")
            .defineInRange("dwellerDaylightCalmPerSecond", 0.10, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_IGNORE_CALM = CURSES
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second while it is manifest and you are NOT looking at it — the real reward for ignoring it. Dread can now fall (never below 0), but the accelerants keep it climbing overall if you can't stay calm.")
            .defineInRange("dwellerIgnoreCalmPerSecond", 0.04, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_MIN_TICKS = CURSES
            .comment("The Dweller (FOREPLAY): minimum length (ticks) of the opening phase — no fog/desaturation, music plays, only the odd VERY distant watch. Default 40s.")
            .defineInRange("dwellerForeplayMinTicks", 800, 100, 24000);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_MAX_TICKS = CURSES
            .comment("The Dweller (FOREPLAY): maximum length (ticks) of the opening phase. Default 3min.")
            .defineInRange("dwellerForeplayMaxTicks", 3600, 100, 48000);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_WATCH_GAP_MIN = CURSES
            .comment("The Dweller (FOREPLAY): minimum gap (ticks) between the rare distant watches during the opening phase.")
            .defineInRange("dwellerForeplayWatchGapMinTicks", 600, 40, 12000);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_WATCH_GAP_MAX = CURSES
            .comment("The Dweller (FOREPLAY): maximum gap (ticks) between the rare distant watches during the opening phase.")
            .defineInRange("dwellerForeplayWatchGapMaxTicks", 1800, 40, 24000);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_MIN_TICKS = CURSES
            .comment("The Dweller: minimum length (ticks) of a chase before it breaks off (if it can't catch you). Default 20s.")
            .defineInRange("dwellerChaseMinTicks", 400, 40, 2400);
    public static final ModConfigSpec.DoubleValue DWELLER_COMPANY_RADIUS = CURSES
            .comment("The Dweller: radius (blocks) within which other players count as company (calming you and making it shy).")
            .defineInRange("dwellerCompanyRadius", 24.0, 4.0, 128.0);
    public static final ModConfigSpec.IntValue DWELLER_LIGHT_LEVEL = CURSES
            .comment("The Dweller: light level at/above which you count as 'in the light' (calming, repels it).")
            .defineInRange("dwellerLightLevel", 8, 0, 15);
    public static final ModConfigSpec.IntValue DWELLER_DARK_LEVEL = CURSES
            .comment("The Dweller: light level at/below which you count as 'in the dark' (dread rises, it grows bold).")
            .defineInRange("dwellerDarkLevel", 5, 0, 15);

    public static final ModConfigSpec.DoubleValue DWELLER_LIGHT_LURK_DISTANCE = CURSES
            .comment("The Dweller (COUNTERPLAY): while you're in bright light, it will not creep closer than this (blocks) — it lurks at the edge of the light.")
            .defineInRange("dwellerLightLurkDistance", 6.0, 0.0, 32.0);

    public static final ModConfigSpec.IntValue DWELLER_CHASE_MAX_TICKS = CURSES
            .comment("The Dweller: maximum length (ticks) of a chase — each hunt rolls a duration in [chaseMinTicks, chaseMaxTicks] and breaks off at that if it can't catch you. Default 35s.")
            .defineInRange("dwellerChaseMaxTicks", 700, 40, 2400);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_COOLDOWN_TICKS = CURSES
            .comment("The Dweller: the LONGEST chase cooldown (ticks) — used at the moment dread first enters the chase range. Scales DOWN toward dwellerChaseMinCooldownTicks as dread climbs to max. Default 20s.")
            .defineInRange("dwellerChaseCooldownTicks", 400, 0, 24000);

    // --- The Dweller: Possession event -----------------------------------------------------------------

    // --- The Dweller: Isolation, Mimic, Phantom-attackers, Contagion, Finale ---------------------------
    public static final ModConfigSpec.IntValue DWELLER_MIMIC_BURST_TICKS = CURSES
            .comment("The Dweller (Mimic event): how long (ticks) it refreshes the Delusions fake-player system for a fresh wave of impostors.")
            .defineInRange("dwellerMimicBurstTicks", 1200, 100, 24000);

    public static final ModConfigSpec.DoubleValue DWELLER_FINALE_EXPLOSION_POWER = CURSES
            .comment("The Dweller (finale): explosion power at the death spot so the catch goes off like the explosion event",
                    "(gory burst + a real blast that hurts nearby others). 0 = gore only. NO terrain damage.")
            .defineInRange("dwellerFinaleExplosionPower", 3.0, 0.0, 12.0);

    // --- The Dweller: escalating chases ----------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_TELEPORT_DISTANCE = CURSES
            .comment("The Dweller: how far (blocks) it blinks away to when it teleports mid-hunt.")
            .defineInRange("dwellerChaseTeleportDistance", 11.0, 3.0, 40.0);

    // --- The Dweller: dread atmosphere (heartbeat + stray flickers) -------------------------------------
    public static final ModConfigSpec.IntValue DWELLER_HEARTBEAT_MIN_TICKS = CURSES
            .comment("The Dweller: FASTEST gap (ticks) between the oppressive heartbeat you hear — reached at max dread / when it's right on top of you.")
            .defineInRange("dwellerHeartbeatMinTicks", 12, 3, 200);
    public static final ModConfigSpec.IntValue DWELLER_HEARTBEAT_MAX_TICKS = CURSES
            .comment("The Dweller: SLOWEST gap (ticks) between heartbeats — the languid pulse of low dread.")
            .defineInRange("dwellerHeartbeatMaxTicks", 72, 5, 1200);

    // --- Bedrock Moment (curse) — a parody of Bedrock-edition bugs -------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("bedrock"); }
    public static final ModConfigSpec.IntValue BEDROCK_EVENT_MIN_TICKS = CURSES
            .comment("Bedrock Moment: minimum gap (ticks) between ACTIVE bug events. It's an overloaded curse, so they come thick and fast.")
            .defineInRange("bedrockEventMinTicks", 84, 20, 6000);
    public static final ModConfigSpec.IntValue BEDROCK_EVENT_MAX_TICKS = CURSES
            .comment("Bedrock Moment: maximum gap (ticks) between active bug events.")
            .defineInRange("bedrockEventMaxTicks", 266, 20, 12000);
    // Active-event rarity tiers: every active bug is tagged tier 1/2/3 and drawn from a weighted pool.
    // Tier 1 = the mild everyday jank (very common); tier 2 = the disruptive stuff (medium); tier 3 = the
    // big shocks (rare). Retune the three weights to shift the whole balance at once.
    public static final ModConfigSpec.IntValue BEDROCK_TIER1_WEIGHT = CURSES
            .comment("Bedrock Moment: pool weight of a TIER 1 (very common) active event.")
            .defineInRange("bedrockTier1Weight", 12, 0, 1000);
    public static final ModConfigSpec.IntValue BEDROCK_TIER2_WEIGHT = CURSES
            .comment("Bedrock Moment: pool weight of a TIER 2 (medium rarity) active event.")
            .defineInRange("bedrockTier2Weight", 5, 0, 1000);
    public static final ModConfigSpec.IntValue BEDROCK_TIER3_WEIGHT = CURSES
            .comment("Bedrock Moment: pool weight of a TIER 3 (rare) active event.")
            .defineInRange("bedrockTier3Weight", 2, 0, 1000);
    // Newer bug durations/chances.
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_ITEM_TICKS = CURSES
            .comment("Bedrock Moment (Ghost Item): how long (ticks) your held item renders as a random other item.")
            .defineInRange("bedrockGhostItemTicks", 50, 5, 600);
    public static final ModConfigSpec.IntValue BEDROCK_INPUT_LAG_TICKS = CURSES
            .comment("Bedrock Moment (Input Lag): how long (ticks) the input-lag window lasts.")
            .defineInRange("bedrockInputLagTicks", 120, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_INPUT_LAG_DELAY = CURSES
            .comment("Bedrock Moment (Input Lag): how many ticks late your movement input is applied.")
            .defineInRange("bedrockInputLagDelayTicks", 6, 1, 40);
    public static final ModConfigSpec.IntValue BEDROCK_TEXTURE_FLICKER_TICKS = CURSES
            .comment("Bedrock Moment (Texture Flicker): how long (ticks) the missing-texture flicker window lasts.")
            .defineInRange("bedrockTextureFlickerTicks", 70, 10, 600);
    public static final ModConfigSpec.IntValue BEDROCK_SPRINT_RESET_TICKS = CURSES
            .comment("Bedrock Moment (Sprint Reset): how long (ticks) sprint keeps cutting out.")
            .defineInRange("bedrockSprintResetTicks", 140, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_PHASE_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Ghost Block Phase): min length (ticks) of the ghost-block spell (blocks placed/broken revert).")
            .defineInRange("bedrockGhostPhaseMinTicks", 80, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_PHASE_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Ghost Block Phase): max length (ticks) of the ghost-block spell (default 4–16s).")
            .defineInRange("bedrockGhostPhaseMaxTicks", 320, 20, 2400);
    public static final ModConfigSpec.IntValue BEDROCK_FOOD_REG_CHANCE = CURSES
            .comment("Bedrock Moment (Food Reg): % chance an eaten food provides NO hunger (desync).")
            .defineInRange("bedrockFoodRegChancePercent", 14, 0, 100);
    public static final ModConfigSpec.IntValue BEDROCK_HIT_REG_CHANCE = CURSES
            .comment("Bedrock Moment (Hit Reg): % chance a melee hit is invalidated (whiffs to the empty-swing sound).")
            .defineInRange("bedrockHitRegChancePercent", 6, 0, 100);
    public static final ModConfigSpec.IntValue BEDROCK_LANGUAGE_TICKS = CURSES
            .comment("Bedrock Moment (Language Error): how long (ticks) the language stays swapped to pirate/welsh.")
            .defineInRange("bedrockLanguageTicks", 400, 60, 6000);
    public static final ModConfigSpec.IntValue BEDROCK_SPEEDBLITZ_FREEZE_TICKS = CURSES
            .comment("Bedrock Moment (Speed Blitz): how long (ticks) you're frozen while your movement is stored.")
            .defineInRange("bedrockSpeedBlitzFreezeTicks", 20, 5, 200);
    public static final ModConfigSpec.IntValue BEDROCK_BSOD_TICKS = CURSES
            .comment("Bedrock Moment (Fake BSOD): how long (ticks) the blue screen is shown.")
            .defineInRange("bedrockBsodTicks", 100, 20, 600);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIR_SWIM_CHANCE = CURSES
            .comment("Bedrock Moment (Air Swimming): per-tick chance, WHILE swimming, that you keep swimming after leaving water.")
            .defineInRange("bedrockAirSwimChance", 0.02, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_AIR_SWIM_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Air Swimming): minimum ticks the air-swim lasts before it's force-cancelled.")
            .defineInRange("bedrockAirSwimMinTicks", 60, 10, 2000);
    public static final ModConfigSpec.IntValue BEDROCK_AIR_SWIM_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Air Swimming): maximum ticks (default 3–25s).")
            .defineInRange("bedrockAirSwimMaxTicks", 500, 10, 4000);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MOB_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Pause): min ticks nearby entities are paused (AI off + frozen).")
            .defineInRange("bedrockPauseMobMinTicks", 6, 1, 400);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MOB_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Pause): max ticks paused (default 0.3–5s).")
            .defineInRange("bedrockPauseMobMaxTicks", 100, 1, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_CATCHUP_MULT = CURSES
            .comment("Bedrock Moment (Pause): after unpausing, entities tick THIS many times per tick (higher than tickspeed) to 'catch up'.")
            .defineInRange("bedrockPauseCatchupMultiplier", 10, 2, 40);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_CATCHUP_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Pause): min ticks of the post-unpause catch-up burst.")
            .defineInRange("bedrockPauseCatchupMinTicks", 20, 5, 400);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_CATCHUP_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Pause): max ticks of the catch-up burst (default 1–4s).")
            .defineInRange("bedrockPauseCatchupMaxTicks", 80, 5, 800);
    public static final ModConfigSpec.IntValue BEDROCK_FLOAT_TICKS = CURSES
            .comment("Bedrock Moment (Float): how long (ticks) affected entities lose gravity.")
            .defineInRange("bedrockFloatTicks", 120, 10, 1200);
    public static final ModConfigSpec.DoubleValue BEDROCK_FLOAT_CHANCE = CURSES
            .comment("Bedrock Moment (Float): per-entity chance each nearby entity loses gravity.")
            .defineInRange("bedrockFloatChance", 0.6, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_SLEEP_CANCEL_CHANCE = CURSES
            .comment("Bedrock Moment (Sleep Cancel): per-tick chance, while sleeping, to be booted out of the bed.")
            .defineInRange("bedrockSleepCancelChance", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_COOLDOWNS_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Cooldowns — beneficial): min duration (ticks) of the quartered swing cooldown. Default 8s.")
            .defineInRange("bedrockCooldownsMinTicks", 160, 20, 6000);
    public static final ModConfigSpec.IntValue BEDROCK_COOLDOWNS_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Cooldowns — beneficial): max duration (ticks). Default 28s.")
            .defineInRange("bedrockCooldownsMaxTicks", 560, 20, 12000);
    public static final ModConfigSpec.DoubleValue BEDROCK_COOLDOWNS_ATTACK_SPEED_MULT = CURSES
            .comment("Bedrock Moment (Cooldowns — beneficial): ATTACK_SPEED multiplier during the window. 4.0 = quartered cooldown (4x swings), Bedrock-style.")
            .defineInRange("bedrockCooldownsAttackSpeedMultiplier", 4.0, 1.0, 10.0);
    public static final ModConfigSpec.IntValue BEDROCK_HUNGRY_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Hungry): min ticks that right-click eats whatever you're holding.")
            .defineInRange("bedrockHungryMinTicks", 40, 10, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_HUNGRY_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Hungry): max ticks (default 2–11s).")
            .defineInRange("bedrockHungryMaxTicks", 220, 10, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_CREEPER_BOAT_SPEED = CURSES
            .comment("Bedrock Moment (Charged Creeper Boat): per-tick speed the boat blitzes toward you. Fast — it's about the SHOCK of it screaming at you, not reliably killing.")
            .defineInRange("bedrockCreeperBoatSpeed", 3.0, 0.1, 12.0);
    public static final ModConfigSpec.IntValue BEDROCK_BLITZ_SPEED_LEVEL = CURSES
            .comment("Bedrock Moment (Blitz): Speed amplifier given to blitzing creepers (0 = Speed I).")
            .defineInRange("bedrockBlitzSpeedLevel", 6, 0, 20);
    public static final ModConfigSpec.DoubleValue BEDROCK_BLITZ_DETONATE_DISTANCE = CURSES
            .comment("Bedrock Moment (Blitz): distance (blocks) at which a blitzing creeper instantly detonates (no windup).")
            .defineInRange("bedrockBlitzDetonateDistance", 3.0, 1.0, 8.0);
    public static final ModConfigSpec.IntValue BEDROCK_MITOSIS_MAX = CURSES
            .comment("Bedrock Moment (Mitosis): how many nearby mobs duplicate per event.")
            .defineInRange("bedrockMitosisMax", 4, 1, 30);
    public static final ModConfigSpec.IntValue BEDROCK_MITOSIS_NEARBY_CAP = CURSES
            .comment("Bedrock Moment (Mitosis): SAFETY cap — skip duplicating if this many mobs are already nearby.")
            .defineInRange("bedrockMitosisNearbyCap", 40, 4, 400);
    public static final ModConfigSpec.IntValue BEDROCK_RUBBERBAND_COUNT = CURSES
            .comment("Bedrock Moment (Rubberbanding): how many times it yanks you back to the saved spot.")
            .defineInRange("bedrockRubberbandCount", 10, 1, 40);
    public static final ModConfigSpec.IntValue BEDROCK_RUBBERBAND_DELAY_TICKS = CURSES
            .comment("Bedrock Moment (Rubberbanding): gap (ticks) between each yank-back (short = frantic lag).")
            .defineInRange("bedrockRubberbandDelayTicks", 12, 2, 600);
    public static final ModConfigSpec.IntValue BEDROCK_BLUETOOTH_TICKS = CURSES
            .comment("Bedrock Moment (Bluetooth Damage): how long (ticks) incoming damage is withheld and stored.")
            .defineInRange("bedrockBluetoothTicks", 90, 20, 600);
    public static final ModConfigSpec.IntValue BEDROCK_BLUETOOTH_RELEASE_DELAY = CURSES
            .comment("Bedrock Moment (Bluetooth Damage): delay (ticks) after the window before the stored damage lands all at once.")
            .defineInRange("bedrockBluetoothReleaseDelay", 20, 1, 200);
    public static final ModConfigSpec.IntValue BEDROCK_HELICOPTER_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Helicopter): min spin-up time (ticks) before the anchored mob is launched (4s).")
            .defineInRange("bedrockHelicopterMinTicks", 80, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_HELICOPTER_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Helicopter): max spin-up time (ticks) before launch (15s).")
            .defineInRange("bedrockHelicopterMaxTicks", 300, 20, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_HELICOPTER_LAUNCH = CURSES
            .comment("Bedrock Moment (Helicopter): upward launch velocity when the spin ends.")
            .defineInRange("bedrockHelicopterLaunch", 2.6, 0.5, 10.0);
    public static final ModConfigSpec.IntValue BEDROCK_HELICOPTER_GROUND_TICKS = CURSES
            .comment("Bedrock Moment (Helicopter): a brief ON-GROUND spin grace (ticks) — it whips into a spin where it stands before it starts rising.")
            .defineInRange("bedrockHelicopterGroundTicks", 30, 0, 400);
    public static final ModConfigSpec.DoubleValue BEDROCK_HELICOPTER_SPIN = CURSES
            .comment("Bedrock Moment (Helicopter): degrees per tick it spins — big for a proper blur.")
            .defineInRange("bedrockHelicopterSpin", 62.0, 5.0, 180.0);
    public static final ModConfigSpec.IntValue BEDROCK_DROWN_TICKS = CURSES
            .comment("Bedrock Moment (Desync Drowning): how long (ticks) the fake drowning lasts before it gives up on its own (also ends on touching water or dying).")
            .defineInRange("bedrockDrownTicks", 400, 40, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_GHOST_BLOCK_CHANCE = CURSES
            .comment("Bedrock Moment (Ghost Blocks): chance a block you place turns out to be a ghost — it briefly appears then pops out (item still consumed).")
            .defineInRange("bedrockGhostBlockChance", 0.22, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_BLOCK_DELAY_TICKS = CURSES
            .comment("Bedrock Moment (Ghost Blocks): how long (ticks) the ghost block lingers before rejecting itself.")
            .defineInRange("bedrockGhostBlockDelayTicks", 8, 1, 100);
    public static final ModConfigSpec.IntValue BEDROCK_SOUND_DELAY_TICKS = CURSES
            .comment("Bedrock Moment (Sound Delay): how long (ticks) the delayed-audio window lasts.")
            .defineInRange("bedrockSoundDelayTicks", 140, 20, 2400);
    public static final ModConfigSpec.IntValue BEDROCK_SOUND_DELAY_AMOUNT = CURSES
            .comment("Bedrock Moment (Sound Delay): how far behind (ticks) sounds lag during the window.")
            .defineInRange("bedrockSoundDelayAmount", 18, 2, 100);
    public static final ModConfigSpec.IntValue BEDROCK_PHANTOM_DUR_TICKS = CURSES
            .comment("Bedrock Moment (Phantom Durability): how long (ticks) your hotbar durability bars jitter for.")
            .defineInRange("bedrockPhantomDurTicks", 120, 20, 2400);
    public static final ModConfigSpec.IntValue BEDROCK_PERSPECTIVE_TICKS = CURSES
            .comment("Bedrock Moment (Perspective Flip): how long (ticks) the camera is yanked to third-person.")
            .defineInRange("bedrockPerspectiveTicks", 50, 10, 600);
    public static final ModConfigSpec.DoubleValue BEDROCK_HOTBAR_DRIFT_CHANCE = CURSES
            .comment("Bedrock Moment (Hotbar Drift): chance per ~1.5s that your selected hotbar slot drifts to another on its own.")
            .defineInRange("bedrockHotbarDriftChance", 0.14, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_MARKETPLACE_AD_COUNT = CURSES
            .comment("Bedrock Moment (Marketplace): how many ad images exist (assets/witchmod/textures/gui/marketplace/ad_1.png .. ad_N.png) to pick from.")
            .defineInRange("bedrockMarketplaceAdCount", 3, 1, 64);
    public static final ModConfigSpec.IntValue BEDROCK_TICKSPEED_LEVEL = CURSES
            .comment("Bedrock Moment (Tickspeed): how many times faster nearby entities tick (they're TICKED extra",
                    "times each server tick, mimicking a high-tickspeed/server-lag clip — not a Speed potion).")
            .defineInRange("bedrockTickspeedMultiplier", 4, 2, 20);
    public static final ModConfigSpec.IntValue BEDROCK_TICKSPEED_TICKS = CURSES
            .comment("Bedrock Moment (Tickspeed): how long (ticks) nearby entities go haywire-fast.")
            .defineInRange("bedrockTickspeedTicks", 45, 5, 600);
    public static final ModConfigSpec.IntValue BEDROCK_DELAY_FALL_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Delay): min delay (ticks) before withheld FALL damage finally lands (0.5s).")
            .defineInRange("bedrockDelayFallMinTicks", 10, 1, 200);
    public static final ModConfigSpec.IntValue BEDROCK_DELAY_FALL_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Delay): max delay (ticks) before withheld fall damage lands (4s).")
            .defineInRange("bedrockDelayFallMaxTicks", 80, 1, 400);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Pause): min freeze (ticks) applied to your own projectiles (0.3s).")
            .defineInRange("bedrockPauseMinTicks", 6, 1, 200);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Pause): max freeze (ticks) applied to your own projectiles (3s).")
            .defineInRange("bedrockPauseMaxTicks", 60, 1, 400);
    public static final ModConfigSpec.DoubleValue BEDROCK_PAUSE_CHANCE = CURSES
            .comment("Bedrock Moment (Pause): chance a projectile you fire freezes mid-flight.")
            .defineInRange("bedrockPauseChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIMBOT_VELOCITY_MULT = CURSES
            .comment("Bedrock Moment (Aimbot): velocity multiplier applied to skeleton arrows near you.")
            .defineInRange("bedrockAimbotVelocityMult", 2.5, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIMBOT_HOMING = CURSES
            .comment("Bedrock Moment (Aimbot): how hard skeleton arrows curve toward you each tick.")
            .defineInRange("bedrockAimbotHoming", 0.22, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIMBOT_RADIUS = CURSES
            .comment("Bedrock Moment (Aimbot): radius (blocks) within which skeleton arrows get the aimbot treatment.")
            .defineInRange("bedrockAimbotRadius", 26.0, 4.0, 96.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_FLING_CHANCE = CURSES
            .comment("Bedrock Moment (Fling): chance a rideable entity flings itself the instant you mount it.")
            .defineInRange("bedrockFlingChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_FLING_FORCE = CURSES
            .comment("Bedrock Moment (Fling): how hard the mount is flung.")
            .defineInRange("bedrockFlingForce", 1.7, 0.1, 6.0);
    public static final ModConfigSpec.IntValue BEDROCK_SERVERLAG_TICKS = CURSES
            .comment("Bedrock Moment (Server Lag): how long (ticks) nearby entities freeze before snapping back to life.")
            .defineInRange("bedrockServerlagTicks", 16, 3, 200);
    public static final ModConfigSpec.IntValue BEDROCK_NIGHTCORE_TICKS = CURSES
            .comment("Bedrock Moment (Nightcore): how long (ticks) game sounds play higher-pitched.")
            .defineInRange("bedrockNightcoreTicks", 110, 20, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_NIGHTCORE_PITCH = CURSES
            .comment("Bedrock Moment (Nightcore): pitch multiplier applied to sounds during the window.")
            .defineInRange("bedrockNightcorePitch", 1.5, 1.0, 2.0);
    public static final ModConfigSpec.IntValue BEDROCK_CHUNKREJECT_MIN_TICKS = CURSES
            .comment("Bedrock Moment (Chunk Rejection): min time (ticks) chunks stop rendering (render distance forced low).")
            .defineInRange("bedrockChunkrejectMinTicks", 60, 10, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_CHUNKREJECT_MAX_TICKS = CURSES
            .comment("Bedrock Moment (Chunk Rejection): max time (ticks) chunks stop rendering.")
            .defineInRange("bedrockChunkrejectMaxTicks", 160, 10, 2400);

    // --- Splitscreen (curse) ---------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("splitscreen"); }
    public static final ModConfigSpec.DoubleValue SPLITSCREEN_RANGE = CURSES
            .comment("Splitscreen: max distance (blocks) to the partner. Beyond it the split ends and you play normally; you re-pair when someone comes back into range.")
            .defineInRange("splitscreenRange", 22.0, 2.0, 128.0);
    public static final ModConfigSpec.IntValue SPLITSCREEN_LOAD_MIN_TICKS = CURSES
            .comment("Splitscreen: minimum length (ticks) of the fake 'entering/exiting splitscreen…' loading screen.")
            .defineInRange("splitscreenLoadMinTicks", 8, 1, 200);
    public static final ModConfigSpec.IntValue SPLITSCREEN_LOAD_MAX_TICKS = CURSES
            .comment("Splitscreen: maximum length (ticks) of the fake loading screen (0.4–2s by default).")
            .defineInRange("splitscreenLoadMaxTicks", 40, 1, 400);
    // (Splitscreen's live-POV toggle is a CLIENT render preference — see ClientConfig.SPLITSCREEN_LIVE_POV.)

    // --- Cutaway Gag (curse) ---------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("cutaway"); }
    public static final ModConfigSpec.IntValue CUTAWAY_COOLDOWN_SECONDS = CURSES
            .comment("Cutaway Gag: minimum real seconds between cutaways. Nothing can fire during this window.")
            .defineInRange("cutawayCooldownSeconds", 350, 0, 36000);
    public static final ModConfigSpec.DoubleValue CUTAWAY_BASE_CHANCE = CURSES
            .comment("Cutaway Gag: base % chance PER SECOND to start a cutaway once the cooldown has elapsed.")
            .defineInRange("cutawayBaseChancePercent", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_RAMP_PER_SECOND = CURSES
            .comment("Cutaway Gag: how much the per-second chance ramps up each further second past the cooldown.")
            .defineInRange("cutawayRampPerSecondPercent", 0.1, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_MAX_CHANCE = CURSES
            .comment("Cutaway Gag: cap on the ramping per-second chance.")
            .defineInRange("cutawayMaxChancePercent", 20.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CUTAWAY_GAG_DELAY_MIN_TICKS = CURSES
            .comment("Cutaway Gag: minimum delay (ticks) after the camera cuts before the gag actually fires.")
            .defineInRange("cutawayGagDelayMinTicks", 10, 0, 600);
    public static final ModConfigSpec.IntValue CUTAWAY_GAG_DELAY_MAX_TICKS = CURSES
            .comment("Cutaway Gag: maximum delay (ticks) before the gag fires (default 0.5–2.5s).")
            .defineInRange("cutawayGagDelayMaxTicks", 50, 0, 600);
    public static final ModConfigSpec.IntValue CUTAWAY_DURATION_MIN_TICKS = CURSES
            .comment("Cutaway Gag: minimum length (ticks) you keep watching AFTER the gag fires.")
            .defineInRange("cutawayDurationMinTicks", 120, 20, 1200);
    public static final ModConfigSpec.IntValue CUTAWAY_DURATION_MAX_TICKS = CURSES
            .comment("Cutaway Gag: maximum length (ticks) you keep watching after the gag fires (default 6–10s).")
            .defineInRange("cutawayDurationMaxTicks", 200, 20, 1200);
    public static final ModConfigSpec.IntValue CUTAWAY_HIT_END_CHANCE = CURSES
            .comment("Cutaway Gag: % chance that taking a hit mid-cutaway snaps your camera back (the gag's effects still stick).")
            .defineInRange("cutawayHitEndChancePercent", 70, 0, 100);
    public static final ModConfigSpec.IntValue CUTAWAY_MARRIAGE_TICKS = CURSES
            .comment("Cutaway Gag (Marriage): base length (ticks) of the wedding ceremony (normal path). The objection path runs +220 longer so it stays readable; explode/what are slightly shorter. Deliberately slow.")
            .defineInRange("cutawayMarriageTicks", 500, 80, 1200);
    public static final ModConfigSpec.DoubleValue CUTAWAY_MARRIAGE_EXPLODE_POWER = CURSES
            .comment("Cutaway Gag (Marriage): explosion power when the 'explode' path fires (slight world damage).")
            .defineInRange("cutawayMarriageExplodePower", 2.0, 0.0, 8.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_SPECTATE_DISTANCE = CURSES
            .comment("Cutaway Gag: horizontal distance (blocks) the overhead spectate camera sits from the victim.")
            .defineInRange("cutawaySpectateDistance", 6.0, 0.0, 24.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_CAMERA_HEIGHT = CURSES
            .comment("Cutaway Gag: how high (blocks) above the victim the overhead spectate camera sits.")
            .defineInRange("cutawayCameraHeight", 5.0, 0.0, 24.0);
    public static final ModConfigSpec.IntValue CUTAWAY_WOOLIAM_CAP = CURSES
            .comment("Cutaway Gag (Woolliam): max sheep the duplicating flock can reach.")
            .defineInRange("cutawayWoolliamCap", 16, 1, 120);
    public static final ModConfigSpec.IntValue CUTAWAY_DRIVEBY_COUNT = CURSES
            .comment("Cutaway Gag (Driveby): number of skeletons in the drive-by.")
            .defineInRange("cutawayDrivebyCount", 4, 1, 12);
    public static final ModConfigSpec.IntValue CUTAWAY_JUMPED_COUNT = CURSES
            .comment("Cutaway Gag (Jumped): number of buffed mobs that jump the victim.")
            .defineInRange("cutawayJumpedCount", 4, 1, 12);
    public static final ModConfigSpec.IntValue CUTAWAY_HOLE_DEPTH = CURSES
            .comment("Cutaway Gag (Hole): how deep the hole is dug beneath the victim.")
            .defineInRange("cutawayHoleDepth", 14, 3, 64);
    public static final ModConfigSpec.DoubleValue CUTAWAY_LAUNCH_POWER = CURSES
            .comment("Cutaway Gag (Launch): upward velocity of the slime-block catapult.")
            .defineInRange("cutawayLaunchPower", 2.6, 0.5, 10.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_SNAIL_SPEED = CURSES
            .comment("Cutaway Gag (Snail): blocks/tick the fake immortal snail creeps toward the victim.")
            .defineInRange("cutawaySnailSpeed", 0.09, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_SNAIL_DAMAGE = CURSES
            .comment("Cutaway Gag (Snail): damage of the non-terrain-damaging explosion when the snail touches you (then the gag ends).")
            .defineInRange("cutawaySnailDamage", 12.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_HELICOPTER_SPIN = CURSES
            .comment("Cutaway Gag (Helicopter): max degrees/tick the spruce boat spins (it ramps up to this).")
            .defineInRange("cutawayHelicopterSpin", 62.0, 1.0, 180.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_HELICOPTER_RISE_MAX = CURSES
            .comment("Cutaway Gag (Helicopter): max upward blocks/tick the ramping ascent reaches.")
            .defineInRange("cutawayHelicopterRiseMax", 0.9, 0.1, 5.0);
    public static final ModConfigSpec.IntValue CUTAWAY_TRAIN_WARNING_TICKS = CURSES
            .comment("Cutaway Gag (I Like Trains): ticks the 'I like trains' line plays before the train rolls.",
                    "Set to roughly the length of the audio clip so it finishes just as the train starts.")
            .defineInRange("cutawayTrainWarningTicks", 34, 0, 200);
    public static final ModConfigSpec.DoubleValue CUTAWAY_TRAIN_SPEED = CURSES
            .comment("Cutaway Gag (I Like Trains): blocks/tick the noclip train barrels along the rails.")
            .defineInRange("cutawayTrainSpeed", 7.0, 0.5, 30.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_TOKYO_SPEED = CURSES
            .comment("Cutaway Gag (Tokyo Drifting): blocks/tick the cherry boat drifts the victim along the ground.")
            .defineInRange("cutawayTokyoSpeed", 2.6, 0.5, 10.0);
    public static final ModConfigSpec.IntValue CUTAWAY_PIED_PIPER_MIN = CURSES
            .comment("Cutaway Gag (Pied Piper): minimum animals swarming the victim (spawns to fill the quota).")
            .defineInRange("cutawayPiedPiperMin", 4, 1, 40);
    public static final ModConfigSpec.IntValue CUTAWAY_FAKE_TNT_COUNT = CURSES
            .comment("Cutaway Gag (Fake TNT): how many (mostly fake) TNT drop from above.")
            .defineInRange("cutawayFakeTntCount", 5, 1, 20);
    public static final ModConfigSpec.IntValue CUTAWAY_FAKE_TNT_REAL_CHANCE = CURSES
            .comment("Cutaway Gag (Fake TNT): % chance each dropped TNT is a REAL one that actually explodes.")
            .defineInRange("cutawayFakeTntRealChancePercent", 15, 0, 100);
    public static final ModConfigSpec.IntValue CUTAWAY_ABDUCTION_HEIGHT = CURSES
            .comment("Cutaway Gag (Abduction): how high (blocks) the UFO/tractor beam towers above the victim.")
            .defineInRange("cutawayAbductionHeight", 40, 6, 128);
    public static final ModConfigSpec.IntValue CUTAWAY_ABDUCTION_LIFT_TICKS = CURSES
            .comment("Cutaway Gag (Abduction): how long (ticks) the beam yanks the victim upward before dropping them.")
            .defineInRange("cutawayAbductionLiftTicks", 60, 5, 400);
    public static final ModConfigSpec.IntValue CUTAWAY_ABDUCTION_LEVITATION = CURSES
            .comment("Cutaway Gag (Abduction): Levitation amplifier — higher = sucked up faster/higher.")
            .defineInRange("cutawayAbductionLevitation", 9, 0, 127);
    public static final ModConfigSpec.IntValue CUTAWAY_AQUARIUM_COUNT = CURSES
            .comment("Cutaway Gag (Aquarium): how many squids/fish spawn treating air like water.")
            .defineInRange("cutawayAquariumCount", 8, 1, 40);
    public static final ModConfigSpec.IntValue CUTAWAY_TRAIN_COUNT = CURSES
            .comment("Cutaway Gag (I Like Trains): how many minecarts slam down the rails.")
            .defineInRange("cutawayTrainCount", 5, 1, 20);
    public static final ModConfigSpec.DoubleValue CUTAWAY_TRAIN_DAMAGE = CURSES
            .comment("Cutaway Gag (I Like Trains): damage a cart deals if it hits the victim.")
            .defineInRange("cutawayTrainDamage", 24.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_BOWLING_DAMAGE = CURSES
            .comment("Cutaway Gag (Bowling): damage the bowling ball deals to each pin it hits.")
            .defineInRange("cutawayBowlingDamage", 14.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue CUTAWAY_BOWLING_CLUSTER_MIN = CURSES
            .comment("Cutaway Gag (Bowling): min entities clustered around the victim before Bowling gets its crowd-weighted pick.")
            .defineInRange("cutawayBowlingClusterMin", 3, 1, 100);
    public static final ModConfigSpec.DoubleValue CUTAWAY_BOWLING_CLUSTER_WEIGHT = CURSES
            .comment("Cutaway Gag (Bowling): per-entity chance added toward picking Bowling when the victim is in a crowd (capped 70%).")
            .defineInRange("cutawayBowlingClusterWeight", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.IntValue CUTAWAY_PARADE_COUNT = CURSES
            .comment("Cutaway Gag (Parade): how many entities march by.")
            .defineInRange("cutawayParadeCount", 14, 2, 100);
    public static final ModConfigSpec.DoubleValue CUTAWAY_PARADE_SPEED = CURSES
            .comment("Cutaway Gag (Parade): how fast the marchers walk (blocks/tick before friction).")
            .defineInRange("cutawayParadeSpeed", 0.2, 0.02, 1.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_PARADE_DAMAGE = CURSES
            .comment("Cutaway Gag (Parade): damage a marcher deals to anything it tramples over.")
            .defineInRange("cutawayParadeDamage", 6.0, 0.0, 1000.0);

    // --- The Dweller: Possession + Behind (tier 2+ events) --------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("dweller"); }
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_CHANCE_DENOM = CURSES
            .comment("Dweller (Possession): 1-in-N per-tick chance to possess a nearby passive mob (tier 2+).")
            .defineInRange("dwellerPossessChanceDenom", 1600, 1, 1000000);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_COOLDOWN = CURSES
            .comment("Dweller (Possession): cooldown ticks after a possession before another can start.")
            .defineInRange("dwellerPossessCooldownTicks", 1200, 0, 100000);
    public static final ModConfigSpec.IntValue DWELLER_BEHIND_TURN_DEGREES = CURSES
            .comment("Dweller (Behind): how sharp a one-tick turn (degrees) counts as a fast look.")
            .defineInRange("dwellerBehindTurnDegrees", 70, 10, 180);
    public static final ModConfigSpec.DoubleValue DWELLER_BEHIND_CHANCE = CURSES
            .comment("Dweller (Behind): chance a fast turn briefly reveals him (tier 2+).")
            .defineInRange("dwellerBehindChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DWELLER_BEHIND_COOLDOWN = CURSES
            .comment("Dweller (Behind): cooldown ticks between behind-you glimpses.")
            .defineInRange("dwellerBehindCooldownTicks", 200, 0, 100000);
    public static final ModConfigSpec.DoubleValue DWELLER_BEHIND_DISTANCE = CURSES
            .comment("Dweller (Behind): how far ahead of your new look he appears (blocks).")
            .defineInRange("dwellerBehindDistance", 4.0, 1.0, 16.0);
    public static final ModConfigSpec.IntValue DWELLER_BEHIND_TICKS = CURSES
            .comment("Dweller (Behind): how long the glimpse lasts before he vanishes (ticks).")
            .defineInRange("dwellerBehindTicks", 12, 2, 100);

    // --- Heavy Hitter blessing ------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("heavy"); }
    public static final ModConfigSpec.DoubleValue HEAVY_HITTER_KNOCKBACK_MULT = BLESSINGS
            .comment("Heavy Hitter: multiplier on your melee knockback (multiplies the final strength, so it",
                    "stacks multiplicatively with Knockback enchantments).")
            .defineInRange("heavyHitterKnockbackMultiplier", 2.0, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue HEAVY_HITTER_FLAT_DAMAGE = BLESSINGS
            .comment("Heavy Hitter: flat extra damage added to every melee hit you land (on top of the weapon's own).")
            .defineInRange("heavyHitterFlatDamage", 2.0, 0.0, 20.0);

    // --- Speed Demon blessing -------------------------------------------------------------------------
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("speed"); }
    public static final ModConfigSpec.DoubleValue SPEED_DEMON_MOUNT_MULTIPLIER = BLESSINGS
            .comment("Speed Demon: movement-speed multiplier applied to any LIVING mount you ride (boats/",
                    "minecarts aren't attribute-driven, so they're not covered).")
            .defineInRange("speedDemonMountMultiplier", 2.0, 1.0, 10.0);

    // --- Narcolepsy curse -----------------------------------------------------------------------------
    static { CURSES.pop(); }
    static { CURSES.push("narcolepsy"); }
    public static final ModConfigSpec.IntValue NARCOLEPSY_MIN_INTERVAL_TICKS = CURSES
            .comment("Narcolepsy: minimum gap between sleeps (ticks). 700 = 35s.")
            .defineInRange("narcolepsyMinIntervalTicks", 700, 20, 200000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_MAX_INTERVAL_TICKS = CURSES
            .comment("Narcolepsy: maximum gap between sleeps (ticks). 6600 = 5.5min. Biased toward the higher half.")
            .defineInRange("narcolepsyMaxIntervalTicks", 6600, 20, 200000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_MIN_SLEEP_TICKS = CURSES
            .comment("Narcolepsy: minimum sleep length (ticks) if you don't mash out. 80 = 4s.")
            .defineInRange("narcolepsyMinSleepTicks", 80, 20, 2000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_MAX_SLEEP_TICKS = CURSES
            .comment("Narcolepsy: maximum sleep length (ticks) if you don't mash out. 240 = 12s.")
            .defineInRange("narcolepsyMaxSleepTicks", 240, 20, 2000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_IDLE_ACCEL_TICKS = CURSES
            .comment("Narcolepsy: after standing still this many ticks, the countdown to the next sleep runs at",
                    "DOUBLE speed (idling makes a narcoleptic nod off sooner). 60 = 3s.")
            .defineInRange("narcolepsyIdleAccelTicks", 60, 0, 12000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_DEEP_CHANCE_PERCENT = CURSES
            .comment("Narcolepsy: chance (%) a sleep is a DEEP one (needs noticeably more mashing + lasts longer).")
            .defineInRange("narcolepsyDeepChancePercent", 24, 0, 100);
    public static final ModConfigSpec.IntValue NARCOLEPSY_VERY_DEEP_CHANCE_PERCENT = CURSES
            .comment("Narcolepsy: chance (%) a sleep is a VERY DEEP one (rarer than deep; the hardest to mash out",
                    "and the longest). Rolled only if the deep roll already passed.")
            .defineInRange("narcolepsyVeryDeepChancePercent", 33, 0, 100);

    // --- Blessing of Flight ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("flight"); }
    public static final ModConfigSpec.DoubleValue FLIGHT_RISE_SPEED = BLESSINGS
            .comment("Flight: max upward velocity (blocks/tick) while holding jump (reached after the accel buildup).")
            .defineInRange("flightRiseSpeed", 0.34, 0.05, 2.0);
    public static final ModConfigSpec.IntValue FLIGHT_ACCEL_TICKS = BLESSINGS
            .comment("Flight: ticks of acceleration buildup from a slow start up to the max rise speed. 20 = 1s.")
            .defineInRange("flightAccelTicks", 20, 1, 200);
    public static final ModConfigSpec.DoubleValue FLIGHT_DRAIN_PER_TICK = BLESSINGS
            .comment("Flight: fraction of the energy bar drained per tick while rising (0..1).")
            .defineInRange("flightDrainPerTick", 0.0032, 0.0001, 0.5);
    public static final ModConfigSpec.IntValue FLIGHT_REGEN_DELAY_TICKS = BLESSINGS
            .comment("Flight: delay (ticks) after you stop rising before the bar starts refilling. 8 = 0.4s.")
            .defineInRange("flightRegenDelayTicks", 8, 0, 200);
    public static final ModConfigSpec.DoubleValue FLIGHT_SPRINT_RISE_MULT = BLESSINGS
            .comment("Flight: while sprinting + holding a movement key, the upward rise is scaled by this (you",
                    "trade height for horizontal speed).")
            .defineInRange("flightSprintRiseMultiplier", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_SPRINT_HORIZONTAL = BLESSINGS
            .comment("Flight: horizontal speed (blocks/tick) when sprint-gliding forward.")
            .defineInRange("flightSprintHorizontal", 0.62, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_AIRBORNE_DRAIN = BLESSINGS
            .comment("Flight: a small constant drain per tick just for being airborne (even when not rising), so",
                    "you can't loiter aloft forever. Refills only once you land.")
            .defineInRange("flightAirborneDrain", 0.0008, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue FLIGHT_GLIDE_DRAIN_MULT = BLESSINGS
            .comment("Flight: the horizontal sprint-glide drains this multiple of the normal push-up drain",
                    "(1.5 = 50% more).")
            .defineInRange("flightGlideDrainMultiplier", 1.5, 1.0, 5.0);
    public static final ModConfigSpec.IntValue FLIGHT_HOLD_TICKS = BLESSINGS
            .comment("Flight: jump must be HELD this many ticks before flight engages, so a quick tap is just a",
                    "normal jump (no flight, no drain). 4 = 0.2s.")
            .defineInRange("flightHoldTicks", 4, 0, 40);
    public static final ModConfigSpec.IntValue FLIGHT_AIRBORNE_GRACE_TICKS = BLESSINGS
            .comment("Flight: the constant airborne drain only starts after being off the ground this long, so a",
                    "natural jump never costs resource. 12 = 0.6s.")
            .defineInRange("flightAirborneGraceTicks", 12, 0, 200);
    public static final ModConfigSpec.DoubleValue FLIGHT_REGEN_PER_TICK = BLESSINGS
            .comment("Flight: fraction of the energy bar refilled per tick when on the ground / not rising.")
            .defineInRange("flightRegenPerTick", 0.006, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue FLIGHT_GLIDE_REGEN_PER_TICK = BLESSINGS
            .comment("Flight + Elytra: fraction of the energy bar refilled per tick while gliding on an elytra with",
                    "NO active push-up — this makes Flight a refuelling enhancer for elytra travel.")
            .defineInRange("flightGlideRegenPerTick", 0.004, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue FLIGHT_ELYTRA_LIFT_MULT = BLESSINGS
            .comment("Flight + Elytra: how much of the rise speed becomes extra LIFT (added on top of the glide) when",
                    "you hold jump while gliding — deliberately small, just enough to stop you constantly losing height.")
            .defineInRange("flightElytraLiftMult", 0.2, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_ELYTRA_SPRINT_SPEED = BLESSINGS
            .comment("Flight + Elytra: extra forward speed per tick added along your look while HOLDING SPRINT and",
                    "gliding on an elytra (accumulates against vanilla drag toward a decent glide speed).")
            .defineInRange("flightElytraSprintSpeed", 0.08, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_ELYTRA_DRAIN_MULT = BLESSINGS
            .comment("Flight + Elytra: the energy drain while actively pushing (lift/sprint) on an elytra is multiplied",
                    "by this — flying an elytra with the blessing burns the bar faster.")
            .defineInRange("flightElytraDrainMult", 2.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_ELYTRA_FOV = BLESSINGS
            .comment("Flight + Elytra: FOV multiplier applied while sprint-gliding on an elytra (a slight zoom-out for",
                    "the sense of speed). 1.0 = no change.")
            .defineInRange("flightElytraFov", 1.12, 1.0, 1.5);
    public static final ModConfigSpec.DoubleValue FLIGHT_DAMAGE_COST = BLESSINGS
            .comment("Flight: fraction of the energy bar drained when you take damage (0.2 = 20%).")
            .defineInRange("flightDamageCost", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.IntValue FLIGHT_LOCKOUT_TICKS = BLESSINGS
            .comment("Flight: ticks you're knocked out of flight after taking damage. 12 = 0.6s.")
            .defineInRange("flightLockoutTicks", 12, 0, 200);

    // --- Blessing of Thunder ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("thunder"); }
    public static final ModConfigSpec.IntValue THUNDER_TIER1_TICKS = BLESSINGS
            .comment("Thunder: seconds of not-swinging to reach charge tier 1/2/3/4 (in ticks).")
            .defineInRange("thunderTier1Ticks", 60, 5, 2000);
    public static final ModConfigSpec.IntValue THUNDER_TIER2_TICKS = BLESSINGS
            .comment("Thunder: ticks to tier 2 (5.5s).").defineInRange("thunderTier2Ticks", 110, 5, 2000);
    public static final ModConfigSpec.IntValue THUNDER_TIER3_TICKS = BLESSINGS
            .comment("Thunder: ticks to tier 3 (8s).").defineInRange("thunderTier3Ticks", 160, 5, 2000);
    public static final ModConfigSpec.IntValue THUNDER_TIER4_TICKS = BLESSINGS
            .comment("Thunder: ticks to tier 4 (13s).").defineInRange("thunderTier4Ticks", 260, 5, 4000);
    public static final ModConfigSpec.DoubleValue THUNDER_CHAIN_BASE = BLESSINGS
            .comment("Thunder: flat base damage every chain deals BEFORE the % of the hit is added on.")
            .defineInRange("thunderChainBase", 2.0, 0.0, 50.0);
    public static final ModConfigSpec.IntValue THUNDER_CHAIN_PERCENT = BLESSINGS
            .comment("Thunder: chain damage as a % of the base hit damage, added on top of thunderChainBase.")
            .defineInRange("thunderChainPercent", 30, 1, 500);
    public static final ModConfigSpec.DoubleValue THUNDER_CHAIN_CAP = BLESSINGS
            .comment("Thunder: hard cap on any single chain's damage.")
            .defineInRange("thunderChainCap", 12.0, 1.0, 100.0);
    public static final ModConfigSpec.DoubleValue THUNDER_CHAIN_RANGE = BLESSINGS
            .comment("Thunder: how far (blocks) a chain can jump to the next entity.")
            .defineInRange("thunderChainRange", 6.0, 1.0, 32.0);
    public static final ModConfigSpec.IntValue THUNDER_BURN_TICKS = BLESSINGS
            .comment("Thunder: burn applied to hit + chained entities (ticks). 60 = 3s.")
            .defineInRange("thunderBurnTicks", 60, 0, 600);
    public static final ModConfigSpec.DoubleValue THUNDER_TIER4_BONUS = BLESSINGS
            .comment("Thunder: bonus damage on the initial hit at tier 4.")
            .defineInRange("thunderTier4Bonus", 6.0, 0.0, 100.0);

    // --- Blessing of Spelunking ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("spelunking"); }
    public static final ModConfigSpec.IntValue SPELUNKING_RADIUS = BLESSINGS
            .comment("Spelunking: small, constant radius (blocks) to scan + highlight nearby ores.")
            .defineInRange("spelunkingRadius", 7, 3, 48);
    public static final ModConfigSpec.IntValue SPELUNKING_INTERVAL_TICKS = BLESSINGS
            .comment("Spelunking: how often (ticks) it re-scans and marks nearby ores (frequent = a constant glow).")
            .defineInRange("spelunkingIntervalTicks", 40, 10, 400);

    // --- Blessing of Safety ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("safety"); }
    public static final ModConfigSpec.IntValue SAFETY_CHANNEL_TICKS = BLESSINGS
            .comment("Safety: ticks of crouching still + looking down before you teleport home. 200 = 10s.")
            .defineInRange("safetyChannelTicks", 200, 20, 2000);
    public static final ModConfigSpec.IntValue SAFETY_COOLDOWN_TICKS = BLESSINGS
            .comment("Safety: cooldown (ticks) after a cancelled channel before you can try again. 120 = 6s.")
            .defineInRange("safetyCooldownTicks", 120, 0, 2000);
    public static final ModConfigSpec.IntValue SAFETY_USE_COOLDOWN_TICKS = BLESSINGS
            .comment("Safety: cooldown (ticks) after a SUCCESSFUL teleport home. 7200 = 6 minutes.")
            .defineInRange("safetyUseCooldownTicks", 7200, 0, 1728000);

    // --- Blessing of Disguise ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("disguise"); }
    public static final ModConfigSpec.DoubleValue DISGUISE_BREAK_RADIUS = BLESSINGS
            .comment("Disguise: get this close (blocks) to a hostile and the costume breaks.")
            .defineInRange("disguiseBreakRadius", 3.0, 1.0, 12.0);
    public static final ModConfigSpec.IntValue DISGUISE_RETURN_TICKS = BLESSINGS
            .comment("Disguise: ticks you must go un-hit after a break before the costume returns. 240 = 12s.")
            .defineInRange("disguiseReturnTicks", 240, 20, 2000);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("conceal"); }
    public static final ModConfigSpec.IntValue CONCEAL_FLASH_TICKS = SYNERGIES
            .comment("Concealment synergy (Prop Hunt + Disguise): ticks you're briefly unrendered (armour and all) after a form change, so a switch reads as a puff-of-smoke vanish.")
            .defineInRange("concealFlashTicks", 10, 1, 60);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("fish"); }
    public static final ModConfigSpec.DoubleValue FISH_FLOP_POWER = SYNERGIES
            .comment("Fish disguise (Disguise + a water effect): out of water you FLOP with the same cadence as a",
                    "vanilla fish — hopping the instant you land, so you bounce continuously. This is the upward",
                    "power of each hop (vanilla's fish flop is 0.4).")
            .defineInRange("fishFlopPower", 0.4, 0.0, 2.0);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("bat"); }
    public static final ModConfigSpec.DoubleValue BAT_FLY_SPEED = SYNERGIES
            .comment("Bat disguise (Sanguine + Disguise): the fly speed while bat-flying, slow by default (vanilla",
                    "creative fly is 0.05). You can't sprint-fly on your own — pair it with the Flight blessing for that.")
            .defineInRange("batFlySpeed", 0.03, 0.005, 0.2);
    public static final ModConfigSpec.DoubleValue BAT_FLIGHT_SPRINT_SPEED = SYNERGIES
            .comment("Bat disguise + Flight blessing: the fly speed while SPRINT-flying (vanilla then doubles it in",
                    "the air), so the Flight blessing lets a bat dash about quickly.")
            .defineInRange("batFlightSprintSpeed", 0.06, 0.01, 0.4);

    // --- Blessing of Confusion ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("confusion"); }
    public static final ModConfigSpec.IntValue CONFUSION_INTERVAL_TICKS = BLESSINGS
            .comment("Confusion: how often (ticks) a doppelganger may be emitted.")
            .defineInRange("confusionIntervalTicks", 70, 10, 600);
    public static final ModConfigSpec.IntValue CONFUSION_MAX_CLONES = BLESSINGS
            .comment("Confusion: max live doppelgangers at once.")
            .defineInRange("confusionMaxClones", 10, 1, 32);
    public static final ModConfigSpec.IntValue CONFUSION_CLONE_LIFETIME_TICKS = BLESSINGS
            .comment("Confusion: how long (ticks) a doppelganger lives before poofing.")
            .defineInRange("confusionCloneLifetimeTicks", 260, 40, 2000);
    public static final ModConfigSpec.IntValue CONFUSION_ATTACK_SPAWN_COUNT = BLESSINGS
            .comment("Confusion: how many extra clones a hit forces out over the following window, bypassing the",
                    "usual audience/rarity gate — so getting attacked always throws up decoys. Still capped by",
                    "confusionMaxClones.")
            .defineInRange("confusionAttackSpawnCount", 2, 0, 16);
    public static final ModConfigSpec.IntValue CONFUSION_ATTACK_SPAWN_WINDOW_TICKS = BLESSINGS
            .comment("Confusion: the window the forced attack-clones spawn over. 40 = 2 seconds.")
            .defineInRange("confusionAttackSpawnWindowTicks", 40, 2, 200);
    public static final ModConfigSpec.DoubleValue CONFUSION_BAT_FLY_SPEED = BLESSINGS
            .comment("Confusion: how fast a clone flies while the owner is bat-disguised (blocks/tick of steering),",
                    "so a swarm of bat-clones flits around you.")
            .defineInRange("confusionBatFlySpeed", 0.28, 0.02, 1.0);

    // --- Blessing of Photosynthesis ---
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("photosynthesis"); }
    public static final ModConfigSpec.IntValue PHOTOSYNTHESIS_INTERVAL_TICKS = BLESSINGS
            .comment("Photosynthesis: how often (ticks) sunlight tops you up. 40 = 2s.")
            .defineInRange("photosynthesisIntervalTicks", 40, 5, 400);

    // --- Voodoo Doll / Needle ---
    static { ITEMS.pop(); }
    static { ITEMS.push("voodoo"); }
    public static final ModConfigSpec.IntValue VOODOO_DOLL_DURABILITY = ITEMS
            .comment("Voodoo Doll: max durability, applied when a doll is bound. Deliberately LOW — voodoo is",
                    "pretty much free damage, so a doll only lasts a few interactions.")
            .defineInRange("voodooDollDurability", 12, 1, 2000);
    public static final ModConfigSpec.DoubleValue VOODOO_TRACK_RADIUS = ITEMS
            .comment("Voodoo Doll (tracker): while you hold a bound doll and the target is ONLINE, in the same",
                    "dimension and within this many blocks, a subtle particle points toward them.")
            .defineInRange("voodooTrackRadius", 180.0, 0.0, 2048.0);
    public static final ModConfigSpec.DoubleValue VOODOO_UNPROTECTED_FRACTION = ITEMS
            .comment("Voodoo damage: the fraction of the hit that IGNORES armour. 0.5 = half the damage is always",
                    "unprotected, so armour helps but only half as much as against a normal hit.")
            .defineInRange("voodooUnprotectedFraction", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue VOODOO_NEEDLE_BASE_DAMAGE = ITEMS
            .comment("Voodoo Needle: jab damage (before the half-armour reduction).")
            .defineInRange("voodooNeedleBaseDamage", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue VOODOO_NEEDLE_DOLL_COST = ITEMS
            .comment("Voodoo Needle: durability spent from the doll per jab.")
            .defineInRange("voodooNeedleDollCost", 2, 1, 100);
    public static final ModConfigSpec.IntValue VOODOO_LAVA_FIRE_TICKS = ITEMS
            .comment("Voodoo Doll: how long (ticks) the bound player burns when the doll is destroyed in fire/lava.")
            .defineInRange("voodooLavaFireTicks", 160, 0, 2000);
    public static final ModConfigSpec.DoubleValue VOODOO_THROW_FORCE = ITEMS
            .comment("Voodoo Doll: how hard the victim is flung when you throw (drop) the doll.")
            .defineInRange("voodooThrowForce", 1.7, 0.0, 10.0);
    public static final ModConfigSpec.IntValue VOODOO_THROW_DOLL_COST = ITEMS
            .comment("Voodoo Doll: durability spent when you throw the doll to fling the victim (a lot).")
            .defineInRange("voodooThrowDollCost", 4, 1, 100);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_TICK_INTERVAL = ITEMS
            .comment("Voodoo Doll (squeeze): ticks between each damage/slow 'click' while holding right-click.")
            .defineInRange("voodooSqueezeTickInterval", 14, 2, 60);
    public static final ModConfigSpec.DoubleValue VOODOO_SQUEEZE_BASE_DAMAGE = ITEMS
            .comment("Voodoo Doll (squeeze): damage of the first tick; it ramps up each tick.")
            .defineInRange("voodooSqueezeBaseDamage", 1.0, 0.0, 50.0);
    public static final ModConfigSpec.DoubleValue VOODOO_SQUEEZE_RAMP = ITEMS
            .comment("Voodoo Doll (squeeze): extra damage added per successive tick.")
            .defineInRange("voodooSqueezeRamp", 0.5, 0.0, 20.0);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_DOLL_COST = ITEMS
            .comment("Voodoo Doll (squeeze): durability spent per damage tick (a lot over a full squeeze).")
            .defineInRange("voodooSqueezeDollCost", 1, 1, 100);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_COOLDOWN = ITEMS
            .comment("Voodoo Doll (squeeze): item-use cooldown (ticks) after a squeeze ends.")
            .defineInRange("voodooSqueezeCooldown", 100, 0, 2000);
    public static final ModConfigSpec.IntValue VOODOO_FEED_DOLL_COST = ITEMS
            .comment("Voodoo Doll (feeding): durability spent to feed the victim. 0 = free (it's a kindness).")
            .defineInRange("voodooFeedDollCost", 0, 0, 100);
    public static final ModConfigSpec.IntValue VOODOO_SHAKE_TICKS = ITEMS
            .comment("Voodoo Doll: camera-shake window (ticks) on the CASTER when they pin/squeeze the doll.")
            .defineInRange("voodooShakeTicks", 5, 0, 60);
    public static final ModConfigSpec.DoubleValue VOODOO_SHAKE_STRENGTH = ITEMS
            .comment("Voodoo Doll: camera-shake strength on the caster per pin/squeeze click.")
            .defineInRange("voodooShakeStrength", 0.8, 0.0, 10.0);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_SELF_SLOW = ITEMS
            .comment("Voodoo Doll (squeeze): Slowness amplifier on the CASTER while squeezing (movement penalty).")
            .defineInRange("voodooSqueezeSelfSlow", 2, 0, 10);
    public static final ModConfigSpec.IntValue VOODOO_POTION_DOLL_COST = ITEMS
            .comment("Voodoo Doll: durability spent per scan while a grounded doll sits in a lingering potion cloud.")
            .defineInRange("voodooPotionDollCost", 1, 0, 100);
    public static final ModConfigSpec.DoubleValue VOODOO_FISHING_FORCE = ITEMS
            .comment("Voodoo Doll: how hard the victim is flung when a FISHING ROD is used on the grounded doll",
                    "(much stronger than a throw).")
            .defineInRange("voodooFishingForce", 3.6, 0.0, 15.0);
    public static final ModConfigSpec.IntValue VOODOO_FISHING_DOLL_COST = ITEMS
            .comment("Voodoo Doll: durability spent when a fishing rod yanks the victim (a HUGE amount).")
            .defineInRange("voodooFishingDollCost", 8, 1, 200);
    public static final ModConfigSpec.DoubleValue VOODOO_FISHING_RANGE = ITEMS
            .comment("Voodoo Doll: how far (blocks) a used fishing rod looks for a bound doll in front of you.")
            .defineInRange("voodooFishingRange", 6.0, 1.0, 32.0);
    public static final ModConfigSpec.IntValue VOODOO_TABLE_DOLL_COST = ITEMS
            .comment("Voodoo Doll: durability spent when used AS a Player Essence in the Bewitching Table target",
                    "slot (the doll is returned, not consumed).")
            .defineInRange("voodooTableDollCost", 1, 0, 100);

    // --- New prototype batch (2026-09-02) ---------------------------------------------------------------

    // Lightweight
    static { CURSES.pop(); }
    static { CURSES.push("lightweight"); }
    public static final ModConfigSpec.DoubleValue LIGHTWEIGHT_KNOCKBACK_MULT = CURSES
            .comment("Lightweight: multiplier on knockback dealt TO you. Stacks on top of the attacker's own",
                    "Knockback enchants/enhancements.")
            .defineInRange("lightweightKnockbackMultiplier", 2.0, 1.0, 8.0);

    // Munchies
    static { CURSES.pop(); }
    static { CURSES.push("munchies"); }
    public static final ModConfigSpec.IntValue MUNCHIES_SATURATION_PERCENT = CURSES
            .comment("Munchies: percent of the SATURATION a food would give that you actually keep (lower = you",
                    "have to eat far more often).")
            .defineInRange("munchiesSaturationPercent", 25, 0, 100);
    public static final ModConfigSpec.IntValue MUNCHIES_EAT_SPEED_PERCENT = CURSES
            .comment("Munchies: percent faster you eat. Stacks with Gluttony's own eat-speed boost.")
            .defineInRange("munchiesEatSpeedPercent", 40, 0, 90);

    // Spotlight
    static { CURSES.pop(); }
    static { CURSES.push("spotlight"); }
    public static final ModConfigSpec.DoubleValue SPOTLIGHT_BEAM_HEIGHT = CURSES
            .comment("Spotlight: how tall (blocks) the beam of light raining down onto you is (measured from the",
                    "beam's start above your head, see spotlightBeamGap).")
            .defineInRange("spotlightBeamHeight", 8.0, 1.0, 32.0);
    public static final ModConfigSpec.DoubleValue SPOTLIGHT_BEAM_GAP = CURSES
            .comment("Spotlight: how far above your head the beam STARTS, so it isn't in your face / blocking",
                    "your view.")
            .defineInRange("spotlightBeamGap", 3.0, 0.0, 16.0);
    public static final ModConfigSpec.DoubleValue SPOTLIGHT_DETECTION_RADIUS = CURSES
            .comment("Spotlight: radius within which hostile mobs get a follow-range boost so they spot you more",
                    "easily (stacks with other curses' detection boosts). 0 to disable.")
            .defineInRange("spotlightDetectionRadius", 32.0, 0.0, 128.0);
    public static final ModConfigSpec.DoubleValue SPOTLIGHT_DETECTION_BONUS = CURSES
            .comment("Spotlight: extra follow-range (blocks) granted to nearby hostiles so they see you easier.")
            .defineInRange("spotlightDetectionBonus", 12.0, 0.0, 64.0);

    // Hiccups
    static { CURSES.pop(); }
    static { CURSES.push("hiccups"); }
    public static final ModConfigSpec.IntValue HICCUPS_MIN_GAP_TICKS = CURSES
            .comment("Hiccups: minimum ticks between hiccups (outside a fit).")
            .defineInRange("hiccupsMinGapTicks", 120, 5, 2000);
    public static final ModConfigSpec.IntValue HICCUPS_MAX_GAP_TICKS = CURSES
            .comment("Hiccups: maximum ticks between hiccups (outside a fit).")
            .defineInRange("hiccupsMaxGapTicks", 360, 10, 4000);
    public static final ModConfigSpec.DoubleValue HICCUPS_HOP_POWER = CURSES
            .comment("Hiccups: upward velocity of the involuntary hop (0.42 ~= a vanilla jump).")
            .defineInRange("hiccupsHopPower", 0.42, 0.0, 1.5);
    public static final ModConfigSpec.IntValue HICCUPS_FIT_CHANCE_PERCENT = CURSES
            .comment("Hiccups: chance a hiccup spirals into a whole FIT of several in a row.")
            .defineInRange("hiccupsFitChancePercent", 7, 0, 100);
    public static final ModConfigSpec.IntValue HICCUPS_FIT_MIN = CURSES
            .comment("Hiccups: fewest EXTRA hiccups in a fit.")
            .defineInRange("hiccupsFitMin", 2, 1, 20);
    public static final ModConfigSpec.IntValue HICCUPS_FIT_MAX = CURSES
            .comment("Hiccups: most EXTRA hiccups in a fit.")
            .defineInRange("hiccupsFitMax", 5, 1, 40);
    public static final ModConfigSpec.IntValue HICCUPS_FIT_SPACING_TICKS = CURSES
            .comment("Hiccups: ticks between hiccups WITHIN a fit.")
            .defineInRange("hiccupsFitSpacingTicks", 12, 2, 100);
    public static final ModConfigSpec.IntValue HICCUPS_FREEZE_TICKS = CURSES
            .comment("Hiccups: how many ticks each hiccup briefly freezes your movement for.")
            .defineInRange("hiccupsFreezeTicks", 6, 1, 40);

    // Body Swapping
    static { CURSES.pop(); }
    static { CURSES.push("bodyswap"); }
    public static final ModConfigSpec.IntValue BODYSWAP_MIN_GAP_TICKS = CURSES
            .comment("Body Swapping: minimum ticks between swaps.")
            .defineInRange("bodySwapMinGapTicks", 600, 20, 20000);
    public static final ModConfigSpec.IntValue BODYSWAP_MAX_GAP_TICKS = CURSES
            .comment("Body Swapping: maximum ticks between swaps.")
            .defineInRange("bodySwapMaxGapTicks", 2400, 40, 40000);
    public static final ModConfigSpec.DoubleValue BODYSWAP_RADIUS = CURSES
            .comment("Body Swapping: farthest a swap partner can be.")
            .defineInRange("bodySwapRadius", 48.0, 4.0, 256.0);
    public static final ModConfigSpec.DoubleValue BODYSWAP_MIN_DISTANCE = CURSES
            .comment("Body Swapping: nearest a swap partner can be — keeps it from swapping with someone right",
                    "next to you (so it's a real relocation).")
            .defineInRange("bodySwapMinDistance", 8.0, 0.0, 64.0);
    public static final ModConfigSpec.IntValue BODYSWAP_CLONE_CHANCE_PERCENT = CURSES
            .comment("Body Swapping: chance a swap targets a nearby Confusion clone instead of a player (so it can",
                    "swap you with your own decoys — deliberately lower than swapping with a real player). 0 disables it.")
            .defineInRange("bodySwapCloneChancePercent", 25, 0, 100);
    public static final ModConfigSpec.IntValue BODYSWAP_SPECIAL_CHANCE_PERCENT = CURSES
            .comment("Body Swapping: chance a swap becomes a 'special swap' — one of fake-out / flicker / long-distance",
                    "/ frenzy — instead of an ordinary one-off swap. 0 disables the special swaps.")
            .defineInRange("bodySwapSpecialChancePercent", 2, 0, 100);
    public static final ModConfigSpec.IntValue BODYSWAP_FAKEOUT_DELAY_TICKS = CURSES
            .comment("Body Swapping FAKE-OUT: ticks after the first swap before it swaps you again. 30 = 1.5 seconds.")
            .defineInRange("bodySwapFakeoutDelayTicks", 30, 1, 200);
    public static final ModConfigSpec.IntValue BODYSWAP_FLICKER_DURATION_TICKS = CURSES
            .comment("Body Swapping FLICKER: total length of the flicker window with one fixed target. 200 = 10 seconds.")
            .defineInRange("bodySwapFlickerDurationTicks", 200, 20, 2400);
    public static final ModConfigSpec.IntValue BODYSWAP_FLICKER_INTERVAL_TICKS = CURSES
            .comment("Body Swapping FLICKER: ticks between each swap roll during the window. 20 = once per second.")
            .defineInRange("bodySwapFlickerIntervalTicks", 20, 1, 200);
    public static final ModConfigSpec.IntValue BODYSWAP_FLICKER_CHANCE_PERCENT = CURSES
            .comment("Body Swapping FLICKER: chance PER roll to swap with the fixed target.")
            .defineInRange("bodySwapFlickerChancePercent", 65, 0, 100);
    public static final ModConfigSpec.DoubleValue BODYSWAP_LONG_DISTANCE_MAX = CURSES
            .comment("Body Swapping LONG-DISTANCE: farthest a long-distance partner can be (they must be beyond the",
                    "normal bodySwapRadius). A big relocation across the map.")
            .defineInRange("bodySwapLongDistanceMax", 2000.0, 16.0, 100000.0);

    // --- attachment synergies (extra behaviour while one player holds both effects; see the synergy package) ---
    static { CURSES.pop(); }
    static { CURSES.push("backseat"); }
    public static final ModConfigSpec.IntValue BACKSEAT_SPEED_DEMON_BONUS_LEVEL = CURSES
            .comment("Synergy (Backseat Driver + Speed Demon): extra Speed levels given to a hijacked mount while",
                    "both are active. Added on top of backseatSpeedBoostLevel.")
            .defineInRange("backseatSpeedDemonBonusLevel", 2, 0, 10);
    static { CURSES.pop(); }
    static { CURSES.push("pests"); }
    public static final ModConfigSpec.DoubleValue PESTS_POPULARITY_MULTIPLIER = CURSES
            .comment("Synergy (Pests + Popularity): the per-block silverfish chance is multiplied by this while both",
                    "are active, so mining during a Popularity horde is crawling with them.")
            .defineInRange("pestsPopularityMultiplier", 2.5, 1.0, 20.0);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("stench"); }
    public static final ModConfigSpec.DoubleValue STENCH_PUSH_RADIUS = SYNERGIES
            .comment("Synergy (Gassy + Unhygienic): radius of the extra stink cloud a fart kicks up — players inside",
                    "are shoved away and non-undead mobs are sped up to flee.")
            .defineInRange("stenchPushRadius", 6.0, 1.0, 32.0);
    public static final ModConfigSpec.DoubleValue STENCH_PLAYER_PUSH = SYNERGIES
            .comment("Synergy (Gassy + Unhygienic): how hard the stink cloud shoves nearby players away (a big fart",
                    "shoves harder).")
            .defineInRange("stenchPlayerPush", 0.6, 0.0, 4.0);
    static { CURSES.pop(); }
    static { CURSES.push("hiccups"); }
    public static final ModConfigSpec.IntValue HICCUPS_FART_CHANCE_PERCENT = CURSES
            .comment("Synergy (Hiccups + Gassy): chance each hiccup DURING A FIT also lets out a fart while both",
                    "are active.")
            .defineInRange("hiccupsFartChancePercent", 40, 0, 100);
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("windfall"); }
    public static final ModConfigSpec.DoubleValue WINDFALL_HIGH_TIER_CHANCE = BLESSINGS
            .comment("Synergy (Windfall + Luck): chance a windfall floats a SPECIAL high-tier item (diamonds, golden",
                    "apples, ...) instead of the ordinary good pool while both are active. Junk is skipped entirely.")
            .defineInRange("windfallHighTierChance", 0.3, 0.0, 1.0);
    static { SYNERGIES.pop(); }
    static { SYNERGIES.push("companionship"); }
    public static final ModConfigSpec.IntValue COMPANIONSHIP_BANTER_MIN_TICKS = SYNERGIES
            .comment("Companionship banter: shortest gap (ticks) between banter lines when a player carries a mix of",
                    "Bodyguard / Solicitor / Guardian Angel. 400 = 20s.")
            .defineInRange("companionshipBanterMinTicks", 400, 40, 24000);
    public static final ModConfigSpec.IntValue COMPANIONSHIP_BANTER_MAX_TICKS = SYNERGIES
            .comment("Companionship banter: longest gap (ticks) between banter lines. 900 = 45s.")
            .defineInRange("companionshipBanterMaxTicks", 900, 40, 48000);
    public static final ModConfigSpec.IntValue COMPANIONSHIP_ARGUE_DELAY_TICKS = SYNERGIES
            .comment("Companionship banter: delay (ticks) before the reply lands in an 'argue' exchange. 40 = 2s.")
            .defineInRange("companionshipArgueDelayTicks", 40, 5, 200);
    public static final ModConfigSpec.IntValue COMPANIONSHIP_DEATH_REACT_CHANCE_PERCENT = SYNERGIES
            .comment("Companionship banter: chance the surviving companion remarks when the bodyguard, solicitor or",
                    "guardian angel is killed. Kept low so it stays an occasional touch.")
            .defineInRange("companionshipDeathReactChancePercent", 40, 0, 100);

    // Left Handed
    static { CURSES.pop(); }
    static { CURSES.push("left"); }
    public static final ModConfigSpec.DoubleValue LEFT_HANDED_DRIFT_DEGREES = CURSES
            .comment("Left Handed: amplitude (degrees) of the PATTERNED aim sway that bites while aiming/using ANY",
                    "item (bow, trident, spyglass, ...). 0 = no sway, just the flipped hand + scrambled chat.")
            .defineInRange("leftHandedDriftDegrees", 2.5, 0.0, 15.0);
    public static final ModConfigSpec.DoubleValue LEFT_HANDED_PROJECTILE_BLOOM = CURSES
            .comment("Left Handed: slight extra spread added to anything you throw/shoot (steers the aim only, the",
                    "speed is preserved). 0 disables it.")
            .defineInRange("leftHandedProjectileBloom", 0.06, 0.0, 1.0);

    // Ice Skates
    static { CURSES.pop(); }
    static { CURSES.push("ice"); }
    public static final ModConfigSpec.DoubleValue ICE_SKATES_SLIP = CURSES
            .comment("Ice Skates: how much horizontal momentum is retained each tick when you let go of the",
                    "controls (higher = a longer, more prominent glide; 1.0 would never stop).")
            .defineInRange("iceSkatesSlip", 0.965, 0.5, 0.999);
    public static final ModConfigSpec.DoubleValue ICE_SKATES_SPRINT_SLIP = CURSES
            .comment("Ice Skates: the retained-momentum factor used when you were SPRINTING as you let go — a",
                    "longer skid off the back of a sprint. Should be >= iceSkatesSlip.")
            .defineInRange("iceSkatesSprintSlip", 0.985, 0.5, 0.999);
    public static final ModConfigSpec.DoubleValue ICE_SKATES_CAP = CURSES
            .comment("Ice Skates: horizontal speed cap while sliding (walking release).")
            .defineInRange("iceSkatesCap", 0.55, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue ICE_SKATES_SPRINT_CAP = CURSES
            .comment("Ice Skates: horizontal speed cap while sliding off a sprint (a faster skid).")
            .defineInRange("iceSkatesSprintCap", 0.75, 0.1, 3.0);

    // Vertigo — height-scaled: a CAMERA sway from cameraStartY up, and (higher) a MOVEMENT sway from
    // movementStartY up; both ramp to full strength at maxY.
    static { CURSES.pop(); }
    static { CURSES.push("vertigo"); }
    public static final ModConfigSpec.IntValue VERTIGO_CAMERA_START_Y = CURSES
            .comment("Vertigo: Y at which the CAMERA sway begins (a slight sway, growing with height).")
            .defineInRange("vertigoCameraStartY", 90, -64, 320);
    public static final ModConfigSpec.IntValue VERTIGO_MOVEMENT_START_Y = CURSES
            .comment("Vertigo: Y at which a MOVEMENT sway ALSO kicks in (your feet start to wander), growing",
                    "with height on top of the camera sway.")
            .defineInRange("vertigoMovementStartY", 120, -64, 320);
    public static final ModConfigSpec.IntValue VERTIGO_MAX_Y = CURSES
            .comment("Vertigo: Y at which both sways reach full strength (the height limit).")
            .defineInRange("vertigoMaxY", 320, -64, 384);
    public static final ModConfigSpec.DoubleValue VERTIGO_CAMERA_STRENGTH = CURSES
            .comment("Vertigo: peak camera wobble in degrees at maxY.")
            .defineInRange("vertigoCameraStrength", 2.6, 0.0, 15.0);
    public static final ModConfigSpec.DoubleValue VERTIGO_MOVEMENT_STRENGTH = CURSES
            .comment("Vertigo: peak movement sway at maxY, as a fraction of full strafe (like Wonky's drift).")
            .defineInRange("vertigoMovementStrength", 0.24, 0.0, 1.0);

    // Narrator
    static { CURSES.pop(); }
    static { CURSES.push("narrator"); }
    public static final ModConfigSpec.DoubleValue NARRATOR_HEAR_RADIUS = CURSES
            .comment("Narrator: how close another player must be to also have their client speak the line (each",
                    "hears it flat/non-positional — TTS isn't a world sound).")
            .defineInRange("narratorHearRadius", 16.0, 0.0, 64.0);
    public static final ModConfigSpec.IntValue NARRATOR_EVENT_CHANCE = CURSES
            .comment("Narrator: percent chance an eligible event actually gets narrated (so it doesn't comment on",
                    "literally everything).")
            .defineInRange("narratorEventChancePercent", 65, 0, 100);
    public static final ModConfigSpec.IntValue NARRATOR_AMBIENT_MIN_TICKS = CURSES
            .comment("Narrator: minimum ticks between ambient (idle) narration lines.")
            .defineInRange("narratorAmbientMinTicks", 400, 40, 12000);
    public static final ModConfigSpec.IntValue NARRATOR_AMBIENT_MAX_TICKS = CURSES
            .comment("Narrator: maximum ticks between ambient (idle) narration lines.")
            .defineInRange("narratorAmbientMaxTicks", 1200, 80, 24000);
    public static final ModConfigSpec.IntValue NARRATOR_REPEAT_GAP_TICKS = CURSES
            .comment("Narrator anti-spam: for a SUSTAINED activity (same category over and over, e.g. swimming),",
                    "each repeat adds this many ticks to the required gap, so it slows down the longer it drags on.",
                    "Resets the moment a different reaction fires.")
            .defineInRange("narratorRepeatGapTicks", 60, 0, 2000);
    public static final ModConfigSpec.IntValue NARRATOR_REPEAT_GAP_MAX_TICKS = CURSES
            .comment("Narrator anti-spam: the ceiling the escalating same-category gap grows to.")
            .defineInRange("narratorRepeatGapMaxTicks", 500, 20, 6000);
    public static final ModConfigSpec.IntValue NARRATOR_VARIANT_CAP = CURSES
            .comment("Narrator anti-spam: how many times ONE specific line may be spoken during a single",
                    "same-category run before it's retired; once every variant is used up the category goes",
                    "silent until a different reaction happens.")
            .defineInRange("narratorVariantCap", 3, 1, 20);
    public static final ModConfigSpec.DoubleValue NARRATOR_SUBTITLE_TICKS_PER_CHAR = CURSES
            .comment("Narrator: how long the on-screen subtitle for the spoken line lingers, in ticks per",
                    "character (it's removed as soon as the line is done). Makes it accessible for non-Windows.")
            .defineInRange("narratorSubtitleTicksPerChar", 1.6, 0.2, 10.0);
    public static final ModConfigSpec.IntValue NARRATOR_CALLOUT_CHANCE = CURSES
            .comment("Narrator: percent chance a spoken line's subtitle is replaced (on non-Windows clients only)",
                    "by an OS jab from linux_mac_callout instead of the readable line.")
            .defineInRange("narratorCalloutChancePercent", 20, 0, 100);
    public static final ModConfigSpec.IntValue NARRATOR_TAB_SPAM_SECONDS = CURSES
            .comment("Narrator: once tabbed out longer than this many seconds, it starts narrating CONSTANTLY",
                    "(ignoring the anti-spam escalation) until you come back — being deliberately annoying.")
            .defineInRange("narratorTabSpamSeconds", 30, 5, 600);
    public static final ModConfigSpec.IntValue NARRATOR_TAB_SPAM_INTERVAL_TICKS = CURSES
            .comment("Narrator: how often (ticks) the constant tabbed-out spam fires once past the threshold.")
            .defineInRange("narratorTabSpamIntervalTicks", 60, 20, 400);
    public static final ModConfigSpec.IntValue NARRATOR_POST_LINE_GAP_MAX_TICKS = CURSES
            .comment("Narrator: the gap between lines is a random 0..this many ticks AFTER the previous line is",
                    "estimated to finish speaking (20 = 1s, so the default 40 = up to 2s). Lines flow one after",
                    "another rather than on a fixed floor.")
            .defineInRange("narratorPostLineGapMaxTicks", 40, 0, 200);
    public static final ModConfigSpec.IntValue NARRATOR_ENV_CHANCE = CURSES
            .comment("Narrator: percent chance, on an idle/ambient tick with a surrounding condition present (rain,",
                    "night, nearby mobs, deep underground, ...), to comment on the SURROUNDINGS instead of the",
                    "player — kept low so it sprinkles in and never starves the idle/ambient lines.")
            .defineInRange("narratorEnvChancePercent", 30, 0, 100);
    public static final ModConfigSpec.IntValue NARRATOR_DARK_LIGHT_LEVEL = CURSES
            .comment("Narrator: the 'dark' surroundings line only fires when the EFFECTIVE light level (block + any",
                    "propagated daylight) at the victim is at or below this — so standing in daylight never triggers",
                    "a 'place a torch' jab.")
            .defineInRange("narratorDarkLightLevel", 6, 0, 15);

    // --- New blessing batch (2026-09-02) ---------------------------------------------------------------

    // Leader
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("leader"); }
    public static final ModConfigSpec.DoubleValue LEADER_RADIUS = BLESSINGS
            .comment("Leader: radius within which OTHER players (not you) get Regeneration + Resistance.")
            .defineInRange("leaderRadius", 16.0, 2.0, 64.0);
    public static final ModConfigSpec.IntValue LEADER_REGEN_AMP = BLESSINGS
            .comment("Leader: Regeneration amplifier granted to nearby allies (0 = Regen I).")
            .defineInRange("leaderRegenAmplifier", 0, 0, 4);
    public static final ModConfigSpec.IntValue LEADER_RESISTANCE_AMP = BLESSINGS
            .comment("Leader: Resistance amplifier granted to nearby allies (0 = Resistance I).")
            .defineInRange("leaderResistanceAmplifier", 0, 0, 4);

    // Underdog
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("underdog"); }
    public static final ModConfigSpec.IntValue UNDERDOG_HALF_PERCENT = BLESSINGS
            .comment("Underdog: health percent at/below which the FIRST tier of buffs kicks in.")
            .defineInRange("underdogHalfPercent", 50, 1, 100);
    public static final ModConfigSpec.IntValue UNDERDOG_QUARTER_PERCENT = BLESSINGS
            .comment("Underdog: health percent at/below which the STRONGER tier kicks in.")
            .defineInRange("underdogQuarterPercent", 25, 1, 100);

    // Bloodhound
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("bloodhound"); }
    public static final ModConfigSpec.DoubleValue BLOODHOUND_RADIUS = BLESSINGS
            .comment("Bloodhound: how far you can 'scent' entities (their-eyes-only particles, even on invisible",
                    "ones).")
            .defineInRange("bloodhoundRadius", 24.0, 4.0, 96.0);
    public static final ModConfigSpec.DoubleValue BLOODHOUND_FOOTSTEP_MIN_MOVE = BLESSINGS
            .comment("Bloodhound: how far (blocks) an entity must move before it drops a footstep breadcrumb.")
            .defineInRange("bloodhoundFootstepMinMove", 0.4, 0.05, 4.0);
    public static final ModConfigSpec.IntValue BLOODHOUND_LINGER_TICKS = BLESSINGS
            .comment("Bloodhound: how long (ticks) each footstep stays lit — the trail is re-sent so it lingers. 100 = 5s.")
            .defineInRange("bloodhoundLingerTicks", 100, 20, 600);

    // Guardian Angel
    static { BLESSINGS.pop(); }
    static { BLESSINGS.push("guardian"); }
    public static final ModConfigSpec.IntValue GUARDIAN_RESPAWN_TICKS = BLESSINGS
            .comment("Guardian Angel: ticks before a killed allay returns (6000 = 5 minutes).")
            .defineInRange("guardianRespawnTicks", 6000, 20, 72000);
    public static final ModConfigSpec.DoubleValue GUARDIAN_SPEED = BLESSINGS
            .comment("Guardian Angel: the allay's movement speed cap (blocks/tick). High = fast + hard to hit.")
            .defineInRange("guardianSpeed", 0.75, 0.1, 3.0);
    public static final ModConfigSpec.IntValue GUARDIAN_EVENT_MIN_TICKS = BLESSINGS
            .comment("Guardian Angel: minimum ticks between scheduled (passive) events — buffs / item-fetch.")
            .defineInRange("guardianEventMinTicks", 400, 40, 24000);
    public static final ModConfigSpec.IntValue GUARDIAN_EVENT_MAX_TICKS = BLESSINGS
            .comment("Guardian Angel: maximum ticks between scheduled (passive) events.")
            .defineInRange("guardianEventMaxTicks", 1100, 80, 48000);
    public static final ModConfigSpec.DoubleValue GUARDIAN_DETECT_RADIUS = BLESSINGS
            .comment("Guardian Angel: radius it watches for attackers, items and lit TNT.")
            .defineInRange("guardianDetectRadius", 20.0, 4.0, 64.0);
    public static final ModConfigSpec.IntValue GUARDIAN_FISHING_HURRY = BLESSINGS
            .comment("Guardian Angel (Fishing): extra ticks shaved off the bite timer per tick while it hovers your bobber (stacks with Angler).")
            .defineInRange("guardianFishingHurry", 4, 1, 40);
    public static final ModConfigSpec.IntValue GUARDIAN_HASTE_SECONDS = BLESSINGS
            .comment("Guardian Angel (Mining): seconds of Haste II granted when you break a hard block or a run of blocks.")
            .defineInRange("guardianHasteSeconds", 8, 1, 60);
    public static final ModConfigSpec.IntValue GUARDIAN_DURABILITY_PERCENT = BLESSINGS
            .comment("Guardian Angel (Durability): % of max durability restored to every worn/held/stored tool when it tends your gear.")
            .defineInRange("guardianDurabilityPercent", 25, 1, 100);
    public static final ModConfigSpec.IntValue GUARDIAN_CURSE_PURGE_CHANCE = BLESSINGS
            .comment("Guardian Angel (Curse purge): % chance, each opportunity (~every 30s while you carry a curse), to lift one random curse.")
            .defineInRange("guardianCursePurgeChance", 30, 0, 100);
    public static final ModConfigSpec.DoubleValue GUARDIAN_PHANTOM_RADIUS = BLESSINGS
            .comment("Guardian Angel (Phantom purge): radius within which it flies up and vaporises phantoms.")
            .defineInRange("guardianPhantomRadius", 16.0, 4.0, 48.0);
    public static final ModConfigSpec.IntValue GUARDIAN_BUFF_MIN_SECONDS = BLESSINGS
            .comment("Guardian Angel (Buffs): minimum seconds for each of the 1-3 random buffs it bestows.")
            .defineInRange("guardianBuffMinSeconds", 15, 1, 600);
    public static final ModConfigSpec.IntValue GUARDIAN_BUFF_MAX_SECONDS = BLESSINGS
            .comment("Guardian Angel (Buffs): maximum seconds for each random buff.")
            .defineInRange("guardianBuffMaxSeconds", 120, 1, 1200);
    public static final ModConfigSpec.IntValue GUARDIAN_KIDNAP_DROP_HEIGHT = BLESSINGS
            .comment("Guardian Angel (Kidnap): how high it hauls an attacker before dropping them (only below 40% health).")
            .defineInRange("guardianKidnapDropHeight", 28, 4, 160);
    public static final ModConfigSpec.IntValue GUARDIAN_KIDNAP_HAZARD_HEIGHT = BLESSINGS
            .comment("Guardian Angel (Kidnap): how high above a hazard (lava/fire) it releases you, so there's room for counterplay.")
            .defineInRange("guardianKidnapHazardHeight", 7, 1, 40);
    public static final ModConfigSpec.IntValue GUARDIAN_KIDNAP_COOLDOWN = BLESSINGS
            .comment("Guardian Angel (Kidnap): ticks between kidnaps (otherwise a hit just makes it panic).")
            .defineInRange("guardianKidnapCooldown", 300, 20, 24000);
    public static final ModConfigSpec.DoubleValue GUARDIAN_KIDNAP_LAVA_RADIUS = BLESSINGS
            .comment("Guardian Angel (Kidnap): if lava is within this radius of the attacker, they get dropped INTO it instead.")
            .defineInRange("guardianKidnapLavaRadius", 10.0, 0.0, 32.0);
    public static final ModConfigSpec.IntValue GUARDIAN_RARE_GIFT_CHANCE = BLESSINGS
            .comment("Guardian Angel (Blessing gift): % chance per ambient opportunity to bestow ONE random blessing (once per blessing, biased to low power).")
            .defineInRange("guardianRareGiftChance", 5, 0, 100);
    public static final ModConfigSpec.DoubleValue GUARDIAN_HEAL_RATE = BLESSINGS
            .comment("Guardian Angel (Heal): health per second knit up while it flies the healing star around you (below half health; a hit cancels it).")
            .defineInRange("guardianHealRate", 2.0, 0.1, 20.0);
    public static final ModConfigSpec.IntValue GUARDIAN_SURROUND_COUNT = BLESSINGS
            .comment("Guardian Angel (Knockback): how many nearby entities count as 'surrounded', triggering the pinball knockback.")
            .defineInRange("guardianSurroundCount", 5, 2, 30);
    public static final ModConfigSpec.DoubleValue GUARDIAN_KNOCKBACK_FORCE = BLESSINGS
            .comment("Guardian Angel (Knockback): how hard it flings entities away when it pinballs to give you space.")
            .defineInRange("guardianKnockbackForce", 1.3, 0.2, 5.0);
    public static final ModConfigSpec.DoubleValue GUARDIAN_ANTIGRIEF_RADIUS = BLESSINGS
            .comment("Guardian Angel (Anti-grief): radius within which it grabs and flings away a lit creeper / primed TNT.")
            .defineInRange("guardianAntigriefRadius", 6.0, 2.0, 24.0);
    public static final ModConfigSpec.IntValue GUARDIAN_ABSORPTION_HEARTS = BLESSINGS
            .comment("Guardian Angel (Grace aura): extra Absorption hearts kept topped up while you're out of combat (0 = off).")
            .defineInRange("guardianAbsorptionHearts", 4, 0, 20);
    public static final ModConfigSpec.IntValue GUARDIAN_AURA_INTERVAL = BLESSINGS
            .comment("Guardian Angel (Grace aura): ticks between aura top-ups (absorption + out-of-combat regen).")
            .defineInRange("guardianAuraIntervalTicks", 60, 20, 1200);
    public static final ModConfigSpec.BooleanValue GUARDIAN_SACRIFICE = BLESSINGS
            .comment("Guardian Angel (Sacrifice): a killing blow is negated and the guardian dies in your place (returns after the respawn cooldown).")
            .define("guardianSacrifice", true);
    public static final ModConfigSpec.DoubleValue GUARDIAN_ZAP_DAMAGE = BLESSINGS
            .comment("Guardian Angel (Zap): damage to the FIRST target of a chain bolt (chained foes take less).")
            .defineInRange("guardianZapDamage", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue GUARDIAN_ZAP_COOLDOWN = BLESSINGS
            .comment("Guardian Angel (Zap): ticks between zaps (only fires while in combat; it chains, so it's less frequent).")
            .defineInRange("guardianZapCooldown", 55, 5, 400);
    public static final ModConfigSpec.IntValue GUARDIAN_ZAP_CHAIN_MAX = BLESSINGS
            .comment("Guardian Angel (Zap): how many extra foes each bolt arcs through (whittles a group instead of one target).")
            .defineInRange("guardianZapChainMax", 4, 0, 20);
    public static final ModConfigSpec.DoubleValue GUARDIAN_ZAP_CHAIN_RANGE = BLESSINGS
            .comment("Guardian Angel (Zap): max distance the bolt can arc from one foe to the next.")
            .defineInRange("guardianZapChainRange", 5.0, 1.0, 16.0);
    public static final ModConfigSpec.DoubleValue GUARDIAN_ZAP_CHAIN_FALLOFF = BLESSINGS
            .comment("Guardian Angel (Zap): damage retained per chain jump (0.7 = each foe takes 70% of the last).")
            .defineInRange("guardianZapChainFalloff", 0.75, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue GUARDIAN_ALLAY_HEALTH = BLESSINGS
            .comment("Guardian Angel: the allay's max health in HP (10 = 5 hearts).")
            .defineInRange("guardianAllayHealth", 10.0, 2.0, 200.0);
    public static final ModConfigSpec.IntValue GUARDIAN_ALLAY_REGEN_DELAY = BLESSINGS
            .comment("Guardian Angel: ticks the allay must go un-hit before it regenerates fast (60 = 3s).")
            .defineInRange("guardianAllayRegenDelay", 60, 10, 600);
    public static final ModConfigSpec.DoubleValue GUARDIAN_GUIDING_BONUS = BLESSINGS
            .comment("Guardian Angel (Guiding light): bonus damage you deal to the highlighted highest-health foe (3 = 1.5 hearts).")
            .defineInRange("guardianGuidingBonus", 3.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue GUARDIAN_GUIDING_MIN_ENEMIES = BLESSINGS
            .comment("Guardian Angel (Guiding light): how many nearby foes count as a 'group' before it highlights one.")
            .defineInRange("guardianGuidingMinEnemies", 2, 1, 20);
    public static final ModConfigSpec.IntValue GUARDIAN_BLINDING_COOLDOWN = BLESSINGS
            .comment("Guardian Angel (Blinding light): ticks between the charge-and-launch strike (only near enemies).")
            .defineInRange("guardianBlindingCooldown", 300, 40, 6000);
    public static final ModConfigSpec.IntValue GUARDIAN_ESCORT_HEALTH_PERCENT = BLESSINGS
            .comment("Guardian Angel (Escort): at/below this % health (with danger near) it lifts you and flies you to safety.")
            .defineInRange("guardianEscortHealthPercent", 20, 0, 100);
    public static final ModConfigSpec.IntValue GUARDIAN_TORCH_INTERVAL = BLESSINGS
            .comment("Guardian Angel (Light-keeper): ticks between torch-placing passes while it's dark/night (lower = more torches).")
            .defineInRange("guardianTorchIntervalTicks", 30, 5, 600);
    public static final ModConfigSpec.IntValue GUARDIAN_TORCH_SPACING = BLESSINGS
            .comment("Guardian Angel (Light-keeper): minimum block spacing between placed torches.")
            .defineInRange("guardianTorchSpacing", 4, 1, 12);

    // --- Ritual Table -----------------------------------------------------------------------------------
    static { RULES.pop(); }
    static { RULES.push("ritual"); }
    public static final ModConfigSpec.IntValue RITUAL_MIN_DURATION_TICKS = RULES
            .comment("Ritual Table: minimum rolled effect duration in ticks (42000 = 35 min).")
            .defineInRange("ritualMinDurationTicks", 42000, 1200, 1728000);
    public static final ModConfigSpec.IntValue RITUAL_MAX_DURATION_TICKS = RULES
            .comment("Ritual Table: maximum rolled effect duration in ticks (72000 = 60 min).")
            .defineInRange("ritualMaxDurationTicks", 72000, 1200, 1728000);
    public static final ModConfigSpec.BooleanValue RITUAL_DURATION_NO_VARIATION = RULES
            .comment("When true, effect durations have NO random variation — every cast lasts exactly the maximum",
                    "(ritualMaxDurationTicks) instead of a random roll between min and max. Set the min and max equal for",
                    "a different fixed value.")
            .define("ritualDurationNoVariation", false);
    public static final ModConfigSpec.IntValue RITUAL_INFECTIOUS_DURATION_TICKS = RULES
            .comment("Ritual Table: fixed duration for a spread Infectious modifier in ticks (72000 = 60 min).")
            .defineInRange("ritualInfectiousDurationTicks", 72000, 1200, 1728000);
    static { MODIFIERS.comment("witchmod — modifiers.toml: knobs for the ritual Table's modifier items.").push("dragons"); }
    public static final ModConfigSpec.DoubleValue DRAGONS_BREATH_RADIUS = MODIFIERS
            .comment("Dragon's Breath modifier: radius (blocks) of the quarter-dose splash + its visual shockwave ring.")
            .defineInRange("dragonsBreathRadius", 12.0, 1.0, 64.0);

    // --- Amethyst Bell ----------------------------------------------------------------------------------
    static { ITEMS.pop(); }
    static { ITEMS.push("bell"); }
    public static final ModConfigSpec.DoubleValue BELL_AOE_RADIUS = ITEMS
            .comment("Amethyst Bell: how far the ring's gamble reaches players.")
            .defineInRange("bellAoeRadius", 8.0, 1.0, 64.0);
    public static final ModConfigSpec.IntValue BELL_INACTIVE_TICKS = ITEMS
            .comment("Amethyst Bell: dimension-wide recharge after a ring, in ticks (36000 = 30 min).")
            .defineInRange("bellInactiveTicks", 36000, 20, 1728000);
    public static final ModConfigSpec.IntValue BELL_APPLY_RAMP_TICKS = ITEMS
            .comment("Amethyst Bell: 'consume' ramp before effects land, in ticks (60 = 3s).")
            .defineInRange("bellApplyRampTicks", 60, 5, 600);
    public static final ModConfigSpec.IntValue BELL_FIZZLE_RAMP_TICKS = ITEMS
            .comment("Amethyst Bell: lighter ramp before a pre-decided fizzle, in ticks (30 = 1.5s).")
            .defineInRange("bellFizzleRampTicks", 30, 5, 600);
    public static final ModConfigSpec.IntValue BELL_SHAKE_TICKS = ITEMS
            .comment("Amethyst Bell: camera-shake window for nearby players on ring, in ticks.")
            .defineInRange("bellShakeTicks", 7, 0, 100);
    public static final ModConfigSpec.DoubleValue BELL_SHAKE_STRENGTH = ITEMS
            .comment("Amethyst Bell: camera-shake strength on ring.")
            .defineInRange("bellShakeStrength", 0.5, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue BELL_SHAKE_RADIUS = ITEMS
            .comment("Amethyst Bell: how far the ring's camera shake reaches.")
            .defineInRange("bellShakeRadius", 12.0, 0.0, 64.0);
    public static final ModConfigSpec.IntValue BELL_ADD_CHANCE_PERCENT = ITEMS
            .comment("Amethyst Bell: chance an unaffected player is GIFTED a batch (rest fizzle).")
            .defineInRange("bellAddChancePercent", 30, 0, 100);
    public static final ModConfigSpec.IntValue BELL_ADD_ONE_WEIGHT = ITEMS
            .comment("Amethyst Bell (gift count weight): weight for 1 effect.")
            .defineInRange("bellAddOneWeight", 50, 0, 1000);
    public static final ModConfigSpec.IntValue BELL_ADD_TWO_WEIGHT = ITEMS
            .comment("Amethyst Bell (gift count weight): weight for 2 effects (remainder = 3).")
            .defineInRange("bellAddTwoWeight", 35, 0, 1000);
    public static final ModConfigSpec.IntValue BELL_POWER_LOW_WEIGHT = ITEMS
            .comment("Amethyst Bell (gift power band): weight for a low roll (0-40).")
            .defineInRange("bellPowerLowWeight", 40, 0, 1000);
    public static final ModConfigSpec.IntValue BELL_POWER_MID_WEIGHT = ITEMS
            .comment("Amethyst Bell (gift power band): weight for a mid roll (41-70; remainder = high 71-85).")
            .defineInRange("bellPowerMidWeight", 45, 0, 1000);
    public static final ModConfigSpec.IntValue BELL_SWAP_HIGH_CHANCE_PERCENT = ITEMS
            .comment("Amethyst Bell (re-roll): chance a swap bumps power UP toward the max.")
            .defineInRange("bellSwapHighChancePercent", 15, 0, 100);
    public static final ModConfigSpec.IntValue BELL_SWAP_LOW_CHANCE_PERCENT = ITEMS
            .comment("Amethyst Bell (re-roll): chance a swap crashes power down (even to 0).")
            .defineInRange("bellSwapLowChancePercent", 15, 0, 100);
    public static final ModConfigSpec.IntValue BELL_SWAP_SIMILAR_JITTER = ITEMS
            .comment("Amethyst Bell (re-roll): power +/- jitter on an ordinary similar-power swap.")
            .defineInRange("bellSwapSimilarJitter", 8, 0, 100);
    public static final ModConfigSpec.IntValue BELL_SWAP_MAX_POWER = ITEMS
            .comment("Amethyst Bell (re-roll): the highest power a swap can reach.")
            .defineInRange("bellSwapMaxPower", 90, 0, 100);

    // --- named jar drops (jars of effects from loot chests / mob kills) ---
    static { ITEMS.pop(); }
    static { ITEMS.push("jar"); }
    public static final ModConfigSpec.BooleanValue JAR_DROPS_ENABLED = ITEMS
            .comment("Master switch for named-jar drops (jars of effects) from loot chests and mob kills. Turn this",
                    "off to disable ALL special mob drops (jars/coins/grenade) entirely — witch essence still drops.")
            .define("jarDropsEnabled", true);
    public static final ModConfigSpec.BooleanValue JAR_CHEST_DROPS_ENABLED = ITEMS
            .comment("Whether named jars appear in LOOT CHESTS (the per-chest 1-in-N chances below). Requires jarDropsEnabled.")
            .define("jarChestDropsEnabled", true);
    public static final ModConfigSpec.BooleanValue JAR_MOB_DROPS_ENABLED = ITEMS
            .comment("Whether the rare SPECIAL drop (jar/coin/grenade) can drop from MOB KILLS. Requires jarDropsEnabled.",
                    "Witch essence still trickles regardless of this.")
            .define("jarMobDropsEnabled", true);
    static { ITEMS.pop(); }
    static { ITEMS.push("special"); }
    public static final ModConfigSpec.BooleanValue SPECIAL_DROP_REQUIRE_PLAYER_KILL = ITEMS
            .comment("If true, the rare SPECIAL mob drop (jar/coin/grenade) only rolls when a player lands the killing",
                    "blow — so it can't clog mob farms (where mobs are killed by fall/lava/other mobs). Off = the old",
                    "'recently hit by a player' rule.")
            .define("specialDropRequirePlayerKill", true);
    static { ITEMS.pop(); }
    static { ITEMS.push("jar"); }
    public static final ModConfigSpec.DoubleValue JAR_RARITY_WEIGHT_BASE = ITEMS
            .comment("How much a jar's rarity (1-10) nudges its odds when a drop rolls. Pick weight = base^(5.5 - rarity),",
                    "CENTRED on mid-rarity, so 1.0 = every jar equally likely and higher values only gently lean commons",
                    "up / rares down. Kept small (1.12 ≈ ±65% across the whole range) since the pool is large.")
            .defineInRange("jarRarityWeightBase", 1.12, 1.0, 4.0);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> JAR_RARITY_OVERRIDES = ITEMS
            .comment("Per-jar rarity overrides as \"jarId=rarity\" (1-10), e.g. \"mining=6\". Anything not listed keeps",
                    "its built-in rarity. Only the pick weight changes; the drop rates are the rates below.")
            .defineListAllowEmpty("jarRarityOverrides", List.of(), () -> "", Config::validateOverrideEntry);

    public static final ModConfigSpec.IntValue JAR_WITCH_ONE_IN = ITEMS
            .comment("A slain witch yields a SPECIAL drop (a prefilled jar or a coin) with a 1-in-N chance (requires",
                    "a recent player hit). 0 disables.")
            .defineInRange("jarWitchDropOneIn", 50, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue JAR_UNDEAD_ONE_IN = ITEMS
            .comment("Any other slain undead mob yields a SPECIAL drop (jar or coin) with a 1-in-N chance (requires a",
                    "recent player hit). 0 disables.")
            .defineInRange("jarUndeadDropOneIn", 850, 0, Integer.MAX_VALUE);
    static { ITEMS.pop(); }
    static { ITEMS.push("special"); }
    public static final ModConfigSpec.IntValue SPECIAL_DROP_COIN_CHANCE = ITEMS
            .comment("When a special drop rolls, the chance it's a COIN (one of Cursed/Blessed/Executioner's) instead",
                    "of a prefilled jar. 40 = 40%.")
            .defineInRange("specialDropCoinChancePercent", 40, 0, 100);
    static { ITEMS.pop(); }
    static { ITEMS.push("witch"); }
    public static final ModConfigSpec.IntValue WITCH_ESSENCE_ROLLS = ITEMS
            .comment("How many independent rolls a slain witch makes for a Cursed Essence drop (each at",
                    "witchEssenceRollChancePercent). 2 rolls at 33% => 0-2 essence, farmable, so the mod stays present.")
            .defineInRange("witchEssenceRolls", 2, 0, 16);
    public static final ModConfigSpec.IntValue WITCH_ESSENCE_ROLL_CHANCE = ITEMS
            .comment("The chance PER ROLL that a slain witch drops one Cursed Essence. 33 = 1/3.")
            .defineInRange("witchEssenceRollChancePercent", 33, 0, 100);

    static { ITEMS.pop(); }
    static { ITEMS.push("jar"); }
    public static final ModConfigSpec.IntValue JAR_CHEST_ANCIENT_CITY_ONE_IN = ITEMS
            .comment("Ancient city chests: 1-in-N chance to also contain a named jar (the likeliest chest source). 0 disables.")
            .defineInRange("jarChestAncientCityOneIn", 30, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue JAR_CHEST_SHIPWRECK_ONE_IN = ITEMS
            .comment("Shipwreck treasure chests: 1-in-N chance to also contain a named jar. 0 disables.")
            .defineInRange("jarChestShipwreckOneIn", 70, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue JAR_CHEST_DUNGEON_ONE_IN = ITEMS
            .comment("Dungeon (monster room) chests: 1-in-N chance to also contain a named jar. 0 disables.")
            .defineInRange("jarChestDungeonOneIn", 90, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue JAR_CHEST_TEMPLE_ONE_IN = ITEMS
            .comment("Temple (desert pyramid / jungle temple) chests: 1-in-N chance to also contain a named jar. 0 disables.")
            .defineInRange("jarChestTempleOneIn", 90, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue JAR_CHEST_IGLOO_ONE_IN = ITEMS
            .comment("Igloo basement chests: 1-in-N chance to also contain a named jar. 0 disables.")
            .defineInRange("jarChestIglooOneIn", 120, 0, Integer.MAX_VALUE);

    // --- Holy Hand Grenade ---
    static { ITEMS.pop(); }
    static { ITEMS.push("grenade"); }
    public static final ModConfigSpec.IntValue GRENADE_SPECIAL_ONE_IN = ITEMS
            .comment("When a mob's special drop rolls, the 1-in-N chance it's a Holy Hand Grenade instead of the",
                    "usual coin/jar. Checked FIRST, so 100 => ~1% of special drops are the grenade. 0 disables.")
            .defineInRange("grenadeSpecialDropOneIn", 100, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue GRENADE_THROW_SPEED = ITEMS
            .comment("How hard the grenade is lobbed (a snowball is 1.5). Kept low so it arcs like a thrown grenade,",
                    "not a bullet.")
            .defineInRange("grenadeThrowSpeed", 0.6, 0.1, 5.0);
    public static final ModConfigSpec.DoubleValue GRENADE_GRAVITY = ITEMS
            .comment("Downward pull per tick on the thrown grenade (a dropped item is 0.04) — high enough that it",
                    "clearly arcs DOWN.")
            .defineInRange("grenadeGravity", 0.09, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GRENADE_BOUNCE = ITEMS
            .comment("How much of its downward speed the grenade keeps when it bounces off the floor — a subtle,",
                    "mostly-visual hop (0 = no bounce).")
            .defineInRange("grenadeBounce", 0.28, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GRENADE_MAX_BOUNCE = ITEMS
            .comment("Hard cap on the rebound speed of a bounce, so a fast impact can never launch it skyward.")
            .defineInRange("grenadeMaxBounce", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GRENADE_WALL_BOUNCE = ITEMS
            .comment("How much speed the grenade keeps when it caroms off a wall.")
            .defineInRange("grenadeWallBounce", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GRENADE_CHARGE_MOMENTUM_KEEP = ITEMS
            .comment("Fraction of its velocity the grenade keeps when the charge-up starts — it mostly settles then.")
            .defineInRange("grenadeChargeMomentumKeep", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.IntValue GRENADE_SHOCKWAVE_VISUAL_TICKS = ITEMS
            .comment("How long the expanding shockwave RING takes to reach its full radius (the amethyst-bell visual).")
            .defineInRange("grenadeShockwaveVisualTicks", 18, 1, 200);
    public static final ModConfigSpec.IntValue GRENADE_SHAKE_TICKS = ITEMS
            .comment("Camera-shake window (ticks) for the detonation impact jolt.")
            .defineInRange("grenadeShakeTicks", 16, 1, 200);
    public static final ModConfigSpec.DoubleValue GRENADE_SHAKE_STRENGTH = ITEMS
            .comment("Peak camera-shake strength (degrees) for the detonation impact jolt; the shockwave overtake reuses",
                    "this at a small window for a gentle rattle.")
            .defineInRange("grenadeShakeStrength", 4.0, 0.0, 30.0);
    public static final ModConfigSpec.DoubleValue GRENADE_SHAKE_RADIUS = ITEMS
            .comment("How far the detonation's impact camera-shake reaches.")
            .defineInRange("grenadeShakeRadius", 14.0, 0.0, 64.0);
    public static final ModConfigSpec.IntValue GRENADE_SHIMMER_TICKS = ITEMS
            .comment("How long the grenade is just a shimmer of glowing particles before the halleluiah + charge.")
            .defineInRange("grenadeShimmerTicks", 30, 1, 400);
    public static final ModConfigSpec.IntValue GRENADE_CHARGE_TICKS = ITEMS
            .comment("How long the grenade pulls in the shimmer (blessed from the skies) before it detonates.")
            .defineInRange("grenadeChargeTicks", 44, 1, 400);
    public static final ModConfigSpec.IntValue GRENADE_HALLELUJAH_RARE_ONE_IN = ITEMS
            .comment("1-in-N chance the rare halleluiah replaces the usual one on charge-up.")
            .defineInRange("grenadeHallelujahRareOneIn", 50, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue GRENADE_EXPLOSION_POWER = ITEMS
            .comment("The detonation's explosion power (TNT is 4.0 — kept a touch smaller here).")
            .defineInRange("grenadeExplosionPower", 3.0, 0.0, 20.0);
    public static final ModConfigSpec.BooleanValue GRENADE_BLOCK_DAMAGE = ITEMS
            .comment("Whether the detonation damages the world (still gated on the mobGriefing gamerule too).")
            .define("grenadeBlockDamage", true);
    public static final ModConfigSpec.DoubleValue GRENADE_EXPLOSION_CLEANSE_RADIUS = ITEMS
            .comment("Radius within which the detonation instantly + fully cleanses players and protects them.")
            .defineInRange("grenadeExplosionCleanseRadius", 6.0, 0.0, 32.0);
    public static final ModConfigSpec.IntValue GRENADE_EXPLOSION_PROTECT_TICKS = ITEMS
            .comment("How long players caught in the detonation are protected (instant, full cleanse).")
            .defineInRange("grenadeExplosionProtectTicks", 600, 0, 24000);
    public static final ModConfigSpec.DoubleValue GRENADE_SHOCKWAVE_RADIUS = ITEMS
            .comment("How far the cleansing shockwave reaches in every direction.")
            .defineInRange("grenadeShockwaveRadius", 10.0, 0.0, 64.0);
    public static final ModConfigSpec.IntValue GRENADE_SHOCKWAVE_TICKS = ITEMS
            .comment("How long the shockwave 'overtake' lasts before the pulse + full cleanse (like the amethyst bell).")
            .defineInRange("grenadeShockwaveTicks", 30, 1, 400);
    public static final ModConfigSpec.IntValue GRENADE_SHOCKWAVE_DRAIN = ITEMS
            .comment("How many ticks of effect time are burned off PER TICK while the shockwave overtakes you (timers tick QUICK).")
            .defineInRange("grenadeShockwaveDrainPerTick", 40, 0, 1200);
    public static final ModConfigSpec.IntValue GRENADE_SHOCKWAVE_PROTECT_TICKS = ITEMS
            .comment("How long a shockwave-hit player is protected after the final pulse.")
            .defineInRange("grenadeShockwaveProtectTicks", 200, 0, 24000);
    public static final ModConfigSpec.IntValue GRENADE_SHOCKWAVE_REGEN_TICKS = ITEMS
            .comment("How long the shockwave's Regeneration II lasts.")
            .defineInRange("grenadeShockwaveRegenTicks", 100, 0, 24000);
    public static final ModConfigSpec.DoubleValue GRENADE_LASH_KILL_RADIUS = ITEMS
            .comment("The live grenade kills any jar lashes that stray within this radius of it.")
            .defineInRange("grenadeLashKillRadius", 4.0, 0.0, 32.0);
    public static final ModConfigSpec.DoubleValue GRENADE_UNDEAD_DAMAGE_MULTIPLIER = ITEMS
            .comment("Holy blast damage dealt to undead is multiplied by this (the Killer Bunny is always one-shot regardless).")
            .defineInRange("grenadeUndeadDamageMultiplier", 1.5, 1.0, 100.0);
    public static final ModConfigSpec.DoubleValue GRENADE_SHOCKWAVE_UNDEAD_DAMAGE = ITEMS
            .comment("Holy damage the wider shockwave deals to undead mobs it reaches (the Killer Bunny is one-shot regardless).")
            .defineInRange("grenadeShockwaveUndeadDamage", 8.0, 0.0, 1000.0);
    static { RULES.pop(); }
    static { ITEMS.pop(); }
    static { CURSES.pop(); }
    static { BLESSINGS.pop(); }
    static { SYNERGIES.pop(); }
    static { MODIFIERS.pop(); }

    static final ModConfigSpec RULES_SPEC = RULES.build();
    static final ModConfigSpec CURSES_SPEC = CURSES.build();
    static final ModConfigSpec BLESSINGS_SPEC = BLESSINGS.build();
    static final ModConfigSpec MODIFIERS_SPEC = MODIFIERS.build();
    static final ModConfigSpec ITEMS_SPEC = ITEMS.build();
    static final ModConfigSpec SYNERGIES_SPEC = SYNERGIES.build();

    /** any non-empty string is a valid id-list entry (effect/modifier/recipe ids). */
    private static boolean validateId(Object obj) {
        return obj instanceof String s && !s.isBlank();
    }

    // ── safe accessors: a SERVER config value throws until the config is loaded (e.g. main menu), so guard
    //    every read with a sensible default so nothing crashes before a world is joined. ──
    public static boolean discoveryEnabled() {
        try {
            return DISCOVERY_ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static double globalCostMultiplier() {
        try {
            return GLOBAL_COST_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static boolean guaranteedIfFullyPaid() {
        try {
            return GUARANTEED_IF_FULLY_PAID.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean ignoreClientOptOuts() {
        try {
            return IGNORE_CLIENT_OPT_OUTS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** when true, an effect duration is always the max (no random roll). */
    public static boolean ritualDurationNoVariation() {
        try {
            return RITUAL_DURATION_NO_VARIATION.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean forceClearBannedItems() {
        try {
            return FORCE_CLEAR_BANNED_ITEMS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** whether {@code itemId} is fully banned (no craft/smelt; swept from inventories when forceClearBannedItems). */
    public static boolean isBannedItem(ResourceLocation itemId) {
        return idInSet(toSet(BANNED_ITEMS), itemId);
    }

    /** whether any items are banned at all — a cheap gate before scanning inventories. */
    public static boolean hasBannedItems() {
        return !toSet(BANNED_ITEMS).isEmpty();
    }

    public static boolean jarChestDropsEnabled() {
        try {
            return JAR_CHEST_DROPS_ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean jarMobDropsEnabled() {
        try {
            return JAR_MOB_DROPS_ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static Set<String> toSet(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        Set<String> set = new HashSet<>();
        try {
            for (String s : value.get()) {
                if (s != null && !s.isBlank()) {
                    set.add(s.trim());
                }
            }
        } catch (IllegalStateException ignored) {
            // not loaded yet — treat as empty
        }
        return set;
    }

    /** id lists accept "witchmod:foo" or bare "foo", so match on both the full id and the path. */
    private static boolean idInSet(Set<String> set, ResourceLocation id) {
        return set.contains(id.toString()) || set.contains(id.getPath());
    }

    public static boolean isAttachmentDisabled(ResourceLocation effectId) {
        return idInSet(toSet(DISABLED_ATTACHMENTS), effectId);
    }

    public static boolean disabledAttachmentsCommandBypass() {
        try {
            return DISABLED_ATTACHMENTS_COMMAND_BYPASS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** {@code modifierId} is a bare modifier id like "dragons_breath". */
    public static boolean isModifierDisabled(String modifierId) {
        Set<String> set = toSet(DISABLED_MODIFIERS);
        return set.contains(modifierId) || set.contains("witchmod:" + modifierId);
    }

    public static boolean isRecipeDisabled(ResourceLocation resultId) {
        return idInSet(toSet(DISABLED_RECIPES), resultId);
    }

    private static boolean validateOverrideEntry(Object obj) {
        if (!(obj instanceof String entry)) {
            return false;
        }
        int split = entry.indexOf('=');
        if (split < 0) {
            return false;
        }
        try {
            Integer.parseInt(entry.substring(split + 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Parses {@link #EFFECT_COST_OVERRIDES} into an id-&gt;cost lookup. Cheap enough to call per-cast; not cached since config can reload. */
    public static Map<String, Integer> parsedEffectCostOverrides() {
        Map<String, Integer> parsed = new HashMap<>();
        for (String entry : EFFECT_COST_OVERRIDES.get()) {
            int split = entry.indexOf('=');
            if (split < 0) {
                continue;
            }
            try {
                parsed.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            } catch (NumberFormatException ignored) {
                // Already validated on load; defensive only.
            }
        }
        return parsed;
    }

    /** the "one in N" chest chance for a chest key (matching the loot_modifiers jsons); 0 = disabled/unknown. */
    public static int jarChestOneIn(String chestKey) {
        return switch (chestKey) {
            case "ancient_city" -> JAR_CHEST_ANCIENT_CITY_ONE_IN.get();
            case "shipwreck" -> JAR_CHEST_SHIPWRECK_ONE_IN.get();
            case "dungeon" -> JAR_CHEST_DUNGEON_ONE_IN.get();
            case "temple" -> JAR_CHEST_TEMPLE_ONE_IN.get();
            case "igloo" -> JAR_CHEST_IGLOO_ONE_IN.get();
            default -> 0;
        };
    }

    /** parses {@link #JAR_RARITY_OVERRIDES} into a jarId-&gt;rarity lookup; not cached since config can reload. */
    public static Map<String, Integer> parsedJarRarityOverrides() {
        Map<String, Integer> parsed = new HashMap<>();
        for (String entry : JAR_RARITY_OVERRIDES.get()) {
            int split = entry.indexOf('=');
            if (split < 0) {
                continue;
            }
            try {
                parsed.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            } catch (NumberFormatException ignored) {
                // already validated on load; defensive only.
            }
        }
        return parsed;
    }

    private Config() {}
}
