package com.oliver.witchmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Real server config (CLAUDE.md section 7/10 Phase 6), replacing the MDK's example scaffold. Every
 * gamerule named in section 7 lives here, plus the section 5.7 success/backfire formula constants, plus a
 * generic "id=value" override list for the ~70 individual per-effect essence costs from sections 5.1/5.2 —
 * a full one-field-per-effect config would be ~150 static fields for numbers this document itself calls
 * "brief functional prototype... treat none of the current implementations as final design intent"
 * (section 4), so the override-list format keeps every cost genuinely tunable without code changes while
 * staying proportionate to how provisional those numbers are.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CURSES_ENABLED = BUILDER
            .comment("Whether curses can be applied at all.")
            .define("cursesEnabled", true);

    public static final ModConfigSpec.BooleanValue BLESSINGS_ENABLED = BUILDER
            .comment("Whether blessings can be applied at all.")
            .define("blessingsEnabled", true);

    public static final ModConfigSpec.BooleanValue BACKFIRES_ENABLED = BUILDER
            .comment("Whether a failed Table ritual can backfire onto the caster. When false, that",
                    "probability mass falls through to a harmless fizzle instead.")
            .define("backfiresEnabled", true);

    public static final ModConfigSpec.BooleanValue WARD_DURABILITY_DECAYS = BUILDER
            .comment("Whether deflecting a curse costs the Ward a durability point. Set false to let",
                    "players opt out of the durability tax entirely.")
            .define("wardDurabilityDecays", true);

    public static final ModConfigSpec.IntValue GRACE_PERIOD_TICKS = BUILDER
            .comment("How many ticks after a player's first-ever join they cannot be targeted by another",
                    "player's curse/blessing (self-casts are unaffected). 6000 ticks = 5 minutes. 0 disables it.")
            .defineInRange("gracePeriodTicks", 6000, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_ACTIVE_EFFECTS_PER_PLAYER = BUILDER
            .comment("Max simultaneous curses+blessings a player can carry before further casts risk",
                    "backfiring instead of landing cleanly (CLAUDE.md section 2.7).")
            .defineInRange("maxActiveEffectsPerPlayer", 3, 1, 64);

    public static final ModConfigSpec.IntValue LEDGER_RANGE = BUILDER
            .comment("How far (in blocks) a Ledger block records ritual activity around it and reacts with",
                    "particle feedback. Opening a Ledger shows only hexes that happened within this range.")
            .defineInRange("ledgerRange", 24, 1, 256);

    public static final ModConfigSpec.IntValue WARDING_TOTEM_RANGE = BUILDER
            .comment("How far (in blocks) a Warding Totem shields players — anyone within this range of a placed",
                    "totem gets the 'Protected' effect and CANNOT have any curse/blessing/voodoo applied to them.")
            .defineInRange("wardingTotemRange", 32, 1, 256);

    public static final ModConfigSpec.IntValue PURIFY_DRAIN_TICKS_PER_TICK = BUILDER
            .comment("Holy (Purifying) Water: how many ticks of curse/blessing timer are burned off PER GAME TICK",
                    "while a player with active effects stands in it. 20 = 1 real second of timer drained every tick",
                    "(20x speed), so the timers visibly race down. Higher = faster cleanse.")
            .defineInRange("purifyDrainTicksPerTick", 20, 1, 1200);

    public static final ModConfigSpec.DoubleValue PURIFY_UNDEAD_DAMAGE = BUILDER
            .comment("Holy (Purifying) Water: damage dealt to an undead mob per hit while it stands in the fluid",
                    "(vanilla invulnerability frames space the hits out, so it's periodic tick damage).")
            .defineInRange("purifyUndeadDamage", 2.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue PURIFY_SHARD_CHANCE = BUILDER
            .comment("Holy Water acquisition: each Amethyst Shard right-clicked into a WATER cauldron adds this",
                    "much cumulative chance for the water to turn holy (0.06 = a stacking +6% per shard).")
            .defineInRange("purifyShardChance", 0.06, 0.0, 1.0);

    public static final ModConfigSpec.IntValue LIMIT_BACKFIRE_BOOST_PERCENT = BUILDER
            .comment("Flat backfire-chance increase (percentage points) applied when the target is already",
                    "at or over maxActiveEffectsPerPlayer. A Phase 6 balancing knob, not a value CLAUDE.md pins down.")
            .defineInRange("limitBackfireBoostPercent", 20, 0, 100);

    public static final ModConfigSpec.IntValue ESCALATING_COST_PERCENT = BUILDER
            .comment("Extra essence cost (percent) required to cast an effect on a target who already has",
                    "that exact effect active (CLAUDE.md section 2.7's \"escalating cost\").")
            .defineInRange("escalatingCostPercent", 50, 0, 500);

    public static final ModConfigSpec.IntValue SUCCESS_FLOOR_PERCENT = BUILDER
            .comment("Section 5.7 formula: minimum success chance regardless of essence spent.")
            .defineInRange("successFloorPercent", 30, 0, 100);

    public static final ModConfigSpec.IntValue SUCCESS_SPAN_PERCENT = BUILDER
            .comment("Section 5.7 formula: how much essenceSpent/baseCost can add on top of the floor.")
            .defineInRange("successSpanPercent", 65, 0, 100);

    public static final ModConfigSpec.IntValue SUCCESS_CAP_PERCENT = BUILDER
            .comment("Section 5.7 formula: success chance never exceeds this (Netherstar modifier overrides it).")
            .defineInRange("successCapPercent", 95, 0, 100);

    public static final ModConfigSpec.IntValue BACKFIRE_START_PERCENT = BUILDER
            .comment("Section 5.7 formula: backfire chance at essenceSpent = 0, scaling down to 0 at full cost.")
            .defineInRange("backfireStartPercent", 25, 0, 100);

    // --- Backfire tiering (⚠ PLACEHOLDER — keyed off baseCost as a stand-in for a real per-attachment tier/
    //     strength value that doesn't exist yet; see CLAUDE.md "Attachment strength/tier TODO"). ------------
    public static final ModConfigSpec.IntValue BACKFIRE_LOW_TIER_COST = BUILDER
            .comment("PLACEHOLDER tier: attachments with baseCost <= this are LOW tier and never backfire (0%).")
            .defineInRange("backfireLowTierCost", 20, 0, 1000);
    public static final ModConfigSpec.IntValue BACKFIRE_HIGH_TIER_COST = BUILDER
            .comment("PLACEHOLDER tier: attachments with baseCost >= this are HIGH tier and always keep a small",
                    "minimum backfire chance even at full essence.")
            .defineInRange("backfireHighTierCost", 50, 0, 1000);
    public static final ModConfigSpec.IntValue BACKFIRE_HIGH_TIER_FLOOR_PERCENT = BUILDER
            .comment("PLACEHOLDER tier: the minimum backfire chance HIGH-tier attachments retain at full essence.")
            .defineInRange("backfireHighTierFloorPercent", 5, 0, 100);

    // --- Backfire outcome (the ritual turning on the caster) ------------------------------------------
    public static final ModConfigSpec.DoubleValue BACKFIRE_EXPLOSION_POWER_MIN = BUILDER
            .comment("Backfire (table explodes): explosion power for the weakest attachment (⚠ scaled by baseCost",
                    "as a PLACEHOLDER for real attachment strength).")
            .defineInRange("backfireExplosionPowerMin", 1.5, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue BACKFIRE_EXPLOSION_POWER_MAX = BUILDER
            .comment("Backfire (table explodes): explosion power for the strongest attachment (⚠ baseCost placeholder).")
            .defineInRange("backfireExplosionPowerMax", 4.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue BACKFIRE_DAMAGE_MIN = BUILDER
            .comment("Backfire (damage type): hearts*2 dealt for the weakest attachment (⚠ baseCost placeholder).")
            .defineInRange("backfireDamageMin", 4.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue BACKFIRE_DAMAGE_MAX = BUILDER
            .comment("Backfire (damage type): hearts*2 dealt for the strongest attachment (⚠ baseCost placeholder).")
            .defineInRange("backfireDamageMax", 12.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue BACKFIRE_STRENGTH_COST_MIN = BUILDER
            .comment("⚠ PLACEHOLDER strength scale: the baseCost mapped to the MIN end of backfire explosion/damage.")
            .defineInRange("backfireStrengthCostMin", 15, 0, 1000);
    public static final ModConfigSpec.IntValue BACKFIRE_STRENGTH_COST_MAX = BUILDER
            .comment("⚠ PLACEHOLDER strength scale: the baseCost mapped to the MAX end of backfire explosion/damage.")
            .defineInRange("backfireStrengthCostMax", 100, 0, 1000);

    // --- Thrown jars (splash + lash) ------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue JAR_SPLASH_RADIUS = BUILDER
            .comment("Thrown Jar: the splash radius (blocks) its stored effects hit — deliberately bigger than a vanilla splash potion.")
            .defineInRange("jarSplashRadius", 5.0, 1.0, 24.0);
    public static final ModConfigSpec.DoubleValue LASH_RANGE = BUILDER
            .comment("Thrown Jar (lash): if the splash catches no one, a homing lash hunts the nearest player within this range (blocks).")
            .defineInRange("jarLashRange", 24.0, 4.0, 128.0);
    public static final ModConfigSpec.DoubleValue LASH_SPEED = BUILDER
            .comment("Thrown Jar (lash): how fast the lash surges toward its target (blocks/tick).")
            .defineInRange("jarLashSpeed", 0.9, 0.1, 5.0);
    public static final ModConfigSpec.IntValue LASH_EXPIRY_TICKS = BUILDER
            .comment("Thrown Jar (lash): how long (ticks) the lash chases before it fizzles out. Run far enough and it can't reach you.")
            .defineInRange("jarLashExpiryTicks", 200, 20, 1200);

    // --- Per-attachment balancing constants (added as each attachment is refined; see CLAUDE.md §16) ---

    public static final ModConfigSpec.IntValue ALLERGIC_BLINDNESS_SECONDS = BUILDER
            .comment("Allergic: how long the blindness lasts after eating a food your rolled diet forbids.")
            .defineInRange("allergicBlindnessSeconds", 8, 0, 600);

    public static final ModConfigSpec.IntValue ALLERGIC_POISON_SECONDS = BUILDER
            .comment("Allergic: how long the poison lasts after eating a forbidden food.")
            .defineInRange("allergicPoisonSeconds", 6, 0, 600);

    public static final ModConfigSpec.IntValue ALLERGIC_POISON_LEVEL = BUILDER
            .comment("Allergic: poison level (1 = Poison I) applied after eating a forbidden food.")
            .defineInRange("allergicPoisonLevel", 1, 1, 10);

    public static final ModConfigSpec.IntValue ALLERGIC_NUTRITION_PERCENT = BUILDER
            .comment("Allergic: percent of the hunger AND saturation a forbidden food actually gives you",
                    "(50 = half). Applied by measuring the real gain and taking the rest back, so it stays",
                    "correct even when you were nearly full.")
            .defineInRange("allergicNutritionPercent", 50, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_EPISODE_SECONDS = BUILDER
            .comment("Backseat Driver: how long the AI keeps the wheel once it takes over.")
            .defineInRange("backseatEpisodeSeconds", 10, 1, 300);

    public static final ModConfigSpec.IntValue BACKSEAT_COOLDOWN_SECONDS = BUILDER
            .comment("Backseat Driver: cooldown after a full-length takeover before another can start.")
            .defineInRange("backseatCooldownSeconds", 60, 0, 3600);

    public static final ModConfigSpec.IntValue BACKSEAT_EARLY_EXIT_COOLDOWN_SECONDS = BUILDER
            .comment("Backseat Driver: SHORTER cooldown used when the rider bailed out to end it early —",
                    "escaping buys you less peace than sitting through it.")
            .defineInRange("backseatEarlyExitCooldownSeconds", 20, 0, 3600);

    public static final ModConfigSpec.IntValue BACKSEAT_CHANCE_GROWTH_PER_SECOND = BUILDER
            .comment("Backseat Driver: percentage points the takeover chance grows per second of riding.")
            .defineInRange("backseatChanceGrowthPerSecond", 1, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_CHANCE_CAP_PERCENT = BUILDER
            .comment("Backseat Driver: normal ceiling the growing takeover chance is clamped to.")
            .defineInRange("backseatChanceCapPercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_HAZARD_CHANCE_CAP_PERCENT = BUILDER
            .comment("Backseat Driver: the ceiling is raised to THIS the moment a hazard (lava/water/a big",
                    "drop) is spotted nearby — the AI is far more likely to grab the wheel near danger.")
            .defineInRange("backseatHazardChanceCapPercent", 70, 0, 100);

    public static final ModConfigSpec.IntValue BACKSEAT_HAZARD_SCAN_RADIUS = BUILDER
            .comment("Backseat Driver: how far to look for hazards to steer into (and to raise the cap).")
            .defineInRange("backseatHazardScanRadius", 12, 1, 32);

    public static final ModConfigSpec.IntValue BACKSEAT_SPEED_BOOST_LEVEL = BUILDER
            .comment("Backseat Driver: Speed effect level given to the hijacked mount for the episode, so it",
                    "genuinely bolts under its own power (0 = no boost). The mount always uses its own",
                    "movement — we never shove it with raw velocity, which is what made it slide/hover.")
            .defineInRange("backseatSpeedBoostLevel", 2, 0, 5);

    public static final ModConfigSpec.DoubleValue BACKSEAT_NAV_SPEED_MULTIPLIER = BUILDER
            .comment("Backseat Driver: pathfinding speed multiplier for mounts that AREN'T rider-steered",
                    "(e.g. a pig without a carrot on a stick) — those walk there via their own navigation.")
            .defineInRange("backseatNavSpeedMultiplier", 1.6, 0.1, 5.0);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_GRAVITY_MULTIPLIER = BUILDER
            .comment("Bad Swimmer: gravity multiplier applied while in liquid. Vanilla divides gravity by 16",
                    "underwater (the slow bob), so ~16 restores air-like sinking. NOTE vanilla skips fluid",
                    "gravity ENTIRELY while sprinting, so this half only bites when you AREN'T swimming;",
                    "badSwimmerConstantPull is the half that always applies. 16.0 is the play-tested value.",
                    "(The gravity attribute is also hard-capped at 1.0, i.e. a multiplier of ~12.)")
            .defineInRange("badSwimmerGravityMultiplier", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_CONSTANT_PULL = BUILDER
            .comment("Bad Swimmer: constant downward pull (blocks/tick^2) while in liquid, applied client-side",
                    "so it bites even while SWIMMING — this is what makes staying on the surface a battle",
                    "instead of the curse simply switching off the moment you sprint-swim.",
                    "Reference: a full swim-up tops out around 0.048, so values near that make ascending",
                    "break even, and anything above it means you sink no matter how hard you swim.")
            .defineInRange("badSwimmerConstantPull", 0.04, 0.0, 0.5);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_ENTRY_PLUNGE = BUILDER
            .comment("Bad Swimmer: one-shot downward yank the moment you break the surface, so entering water",
                    "drags you under hard before the constant pull takes over.")
            .defineInRange("badSwimmerEntryPlunge", 0.4, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_WATER_EFFICIENCY = BUILDER
            .comment("Bad Swimmer: water movement efficiency while in liquid. 1.0 makes water acceleration",
                    "and friction match LAND exactly, i.e. you walk along the bottom instead of swimming.")
            .defineInRange("badSwimmerWaterEfficiency", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.IntValue VIOLENCE_CHECK_INTERVAL_TICKS = BUILDER
            .comment("Violence: how often (ticks) to roll for a forced swing. 20 = once a second.")
            .defineInRange("violenceCheckIntervalTicks", 20, 1, 1200);

    public static final ModConfigSpec.IntValue VIOLENCE_SIGHT_CONE_DEGREES = BUILDER
            .comment("Violence: how far off-centre something can be and still count as 'looked directly at'.",
                    "Sight-based swings also require line of sight, so you can't be set off through a wall.")
            .defineInRange("violenceSightConeDegrees", 20, 1, 90);

    public static final ModConfigSpec.IntValue VIOLENCE_SIGHT_CHANCE_PERCENT = BUILDER
            .comment("Violence: chance (%) per check of swinging at whatever you are looking straight at.",
                    "These take INSTANT priority over impulsive urges and never ramp — staring at something",
                    "in reach is simply dangerous, so keep your eyes off people.")
            .defineInRange("violenceSightChancePercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_SIGHT_HAZARD_CHANCE_PERCENT = BUILDER
            .comment("Violence: chance (%) per check when the thing you are looking at would be shoved off a",
                    "ledge or into lava. Looking at someone perched over a drop is close to a death sentence.")
            .defineInRange("violenceSightHazardChancePercent", 75, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_IMPULSIVE_BASE_CHANCE_PERCENT = BUILDER
            .comment("Violence: starting chance (%) per check of an impulsive swing — the camera-hijacking",
                    "kind, aimed at something you are NOT looking at. Can happen at any time.")
            .defineInRange("violenceImpulsiveBaseChancePercent", 2, 0, 100);

    public static final ModConfigSpec.DoubleValue VIOLENCE_IMPULSIVE_RAMP_PER_SECOND = BUILDER
            .comment("Violence: percentage points the impulsive chance grows per second since the last swing.",
                    "Unlike the sight swings, these DO build up the longer you have gone without one.")
            .defineInRange("violenceImpulsiveRampPerSecond", 0.5, 0.0, 100.0);

    public static final ModConfigSpec.IntValue VIOLENCE_IMPULSIVE_CAP_PERCENT = BUILDER
            .comment("Violence: ceiling the ramping impulsive chance is clamped to.")
            .defineInRange("violenceImpulsiveCapPercent", 30, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_IMPULSIVE_HAZARD_CHANCE_PERCENT = BUILDER
            .comment("Violence: the ramp is overridden with THIS the moment something has been loitering at a",
                    "ledge/hazard for violenceHazardSustainTicks — lingering next to a drop near a cursed",
                    "player is asking for it.")
            .defineInRange("violenceImpulsiveHazardChancePercent", 90, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_HAZARD_SUSTAIN_TICKS = BUILDER
            .comment("Violence: how long (ticks) something must stay perched at a ledge/hazard before it",
                    "massively boosts impulsive urges. 30 = 1.5 seconds.")
            .defineInRange("violenceHazardSustainTicks", 30, 1, 600);

    public static final ModConfigSpec.IntValue VIOLENCE_LOW_HEALTH_PERCENT = BUILDER
            .comment("Violence: at or below this % of max health, a target counts as wounded and climbs the",
                    "priority order — though never above an environmental-hazard shove, which outranks it.")
            .defineInRange("violenceLowHealthPercent", 25, 1, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_RANDOM_TARGET_CHANCE_PERCENT = BUILDER
            .comment("Violence: chance (%) that an impulsive urge throws the whole priority order out and",
                    "just swings at someone at random — so it never becomes perfectly predictable.")
            .defineInRange("violenceRandomTargetChancePercent", 10, 0, 100);

    public static final ModConfigSpec.DoubleValue VIOLENCE_TARGET_RANGE = BUILDER
            .comment("Violence: how far away something can be and still get swung at.")
            .defineInRange("violenceTargetRange", 3.5, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue VIOLENCE_PLAYER_PRIORITY_MULTIPLIER = BUILDER
            .comment("Violence: how much more attractive a player is than a mob when picking a victim.")
            .defineInRange("violencePlayerPriorityMultiplier", 3.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue VIOLENCE_HAZARD_BIAS_MULTIPLIER = BUILDER
            .comment("Violence: multiplier applied BOTH to a victim's pick weight and to the trigger chance",
                    "when knocking them back would shove them off a ledge or into something nasty — so the",
                    "curse both waits for those moments and aims at them.")
            .defineInRange("violenceHazardBiasMultiplier", 4.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue VIOLENCE_HAZARD_SHOVE_DISTANCE = BUILDER
            .comment("Violence: how many blocks along the knockback direction to check for a hazard/ledge.")
            .defineInRange("violenceHazardShoveDistance", 4, 1, 16);

    public static final ModConfigSpec.IntValue VIOLENCE_LEDGE_DROP_MIN = BUILDER
            .comment("Violence: how many blocks of empty air below a spot make it count as a ledge worth",
                    "shoving someone off.")
            .defineInRange("violenceLedgeDropMin", 3, 1, 32);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_CHANCE_PERCENT = BUILDER
            .comment("Violence: chance (%) that an urge becomes a full flurry instead of a single swing.")
            .defineInRange("violenceMultiHitChancePercent", 12, 0, 100);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_MIN = BUILDER
            .comment("Violence: fewest EXTRA swings in a flurry (on top of the first one).")
            .defineInRange("violenceMultiHitMin", 2, 1, 20);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_MAX = BUILDER
            .comment("Violence: most EXTRA swings in a flurry. Clamped to be >= violenceMultiHitMin at use.")
            .defineInRange("violenceMultiHitMax", 4, 1, 20);

    public static final ModConfigSpec.IntValue VIOLENCE_MULTI_HIT_SPACING_TICKS = BUILDER
            .comment("Violence: ticks between the swings of a flurry.")
            .defineInRange("violenceMultiHitSpacingTicks", 6, 1, 60);

    public static final ModConfigSpec.BooleanValue VIOLENCE_FULL_STRENGTH_SWINGS = BUILDER
            .comment("Violence: force each stolen swing to land at FULL attack strength, so it hits as hard",
                    "as a swing you timed yourself (and can sweep/crit) rather than for a fifth of the damage",
                    "if you happened to be mid-cooldown. Independent of the cooldown refund below.")
            .define("violenceFullStrengthSwings", true);

    public static final ModConfigSpec.BooleanValue VIOLENCE_REFUNDS_ATTACK_COOLDOWN = BUILDER
            .comment("Violence: whether a forced swing hands your attack cooldown back. FALSE (default) means",
                    "a stolen swing costs you the cooldown exactly like a real one, so your own next hit is",
                    "weakened — it takes something from you rather than being a free extra attack.")
            .define("violenceRefundsAttackCooldown", false);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_COOLDOWN_TICKS = BUILDER
            .comment("Butterfingers: internal cooldown after ANY fumble. Shared by all three triggers, so a",
                    "passive slip buys you the same grace as one caused by a hit — you can't be stripped.")
            .defineInRange("butterfingersCooldownTicks", 600, 0, 24000);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_PASSIVE_INTERVAL_TICKS = BUILDER
            .comment("Butterfingers: how often (ticks) to roll the out-of-nowhere fumble.")
            .defineInRange("butterfingersPassiveIntervalTicks", 100, 1, 1200);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_PASSIVE_CHANCE_PERCENT = BUILDER
            .comment("Butterfingers: chance (%) per passive check of just dropping something for no reason.",
                    "Deliberately rare — the point is that it mostly gets you at the worst moment instead.")
            .defineInRange("butterfingersPassiveChancePercent", 3, 0, 100);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_ON_DAMAGE_CHANCE_PERCENT = BUILDER
            .comment("Butterfingers: chance (%) of fumbling when you take a hit — much likelier than passive.")
            .defineInRange("butterfingersOnDamageChancePercent", 35, 0, 100);

    public static final ModConfigSpec.IntValue BUTTERFINGERS_ON_SWING_CHANCE_PERCENT = BUILDER
            .comment("Butterfingers: chance (%) of fumbling when you swing a tool or weapon (mining or",
                    "attacking) — the classic 'threw my pickaxe into the lava' moment.")
            .defineInRange("butterfingersOnSwingChancePercent", 15, 0, 100);

    public static final ModConfigSpec.BooleanValue BUTTERFINGERS_DROP_FROM_HOTBAR = BUILDER
            .comment("Butterfingers: when your hands are empty, fumble a random hotbar item instead of doing",
                    "nothing. False means empty hands are completely safe.")
            .define("butterfingersDropFromHotbar", true);

    public static final ModConfigSpec.BooleanValue BUTTERFINGERS_DROPS_WHOLE_STACK = BUILDER
            .comment("Butterfingers: drop the ENTIRE stack rather than a single item. True is far funnier and",
                    "more punishing; the items are all still on the floor to be picked back up.")
            .define("butterfingersDropsWholeStack", true);

    public static final ModConfigSpec.DoubleValue EXPLOSIVE_POWER = BUILDER
            .comment("Explosive: blast radius of the death explosion. TNT is 4.0, a creeper 3.0.")
            .defineInRange("explosivePower", 4.0, 0.1, 32.0);

    public static final ModConfigSpec.BooleanValue EXPLOSIVE_CREATES_FIRE = BUILDER
            .comment("Explosive: whether the blast leaves fires behind, like a charged creeper in the Nether.")
            .define("explosiveCreatesFire", false);

    public static final ModConfigSpec.IntValue EXPLOSIVE_DELAY_TICKS = BUILDER
            .comment("Explosive: ticks to wait after death before detonating. MUST be at least 1: the death",
                    "event fires BEFORE vanilla drops your inventory, so an instant blast would go off while",
                    "there is nothing to destroy. Waiting a tick lets the drops exist so they are caught in it.")
            .defineInRange("explosiveDelayTicks", 1, 1, 200);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MIN_TICKS = BUILDER
            .comment("Loading Screen: shortest fake load, in ticks. 30 = 1.5 seconds.")
            .defineInRange("loadingScreenMinTicks", 30, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MAX_TICKS = BUILDER
            .comment("Loading Screen: longest fake load, in ticks. 100 = 5 seconds.")
            .defineInRange("loadingScreenMaxTicks", 100, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_RESTART_CHANCE_PERCENT = BUILDER
            .comment("Loading Screen: chance (%) that on reaching the end, the bar slides back down and",
                    "loads all over again — the cruellest part of the joke.")
            .defineInRange("loadingScreenRestartChancePercent", 50, 0, 100);

    public static final ModConfigSpec.DoubleValue LOADING_SCREEN_TIP_SCROLL_SPEED = BUILDER
            .comment("Loading Screen: how fast the tip marquee travels, in pixels per tick. 6.0 = 120px/sec,",
                    "so a tip crosses a normal window in a few seconds — fast enough to actually read one",
                    "during a short load. Lower it and you only ever catch a fragment.")
            .defineInRange("loadingScreenTipScrollSpeed", 6.0, 0.1, 60.0);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_RESTART_MIN_TICKS = BUILDER
            .comment("Loading Screen: shortest extra load after a slide-back. 20 = 1 second.")
            .defineInRange("loadingScreenRestartMinTicks", 20, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_RESTART_MAX_TICKS = BUILDER
            .comment("Loading Screen: longest extra load after a slide-back. 60 = 3 seconds.")
            .defineInRange("loadingScreenRestartMaxTicks", 60, 1, 2400);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_STUTTER_CHANCE_PERCENT = BUILDER
            .comment("Loading Screen: chance (%) each second that the bar stutters — freezing in place as if",
                    "the game has hung, without extending the progress itself. At 40 most loads will hitch at",
                    "least once; loadingScreenMaxTotalTicks is what stops a bad run going on forever.")
            .defineInRange("loadingScreenStutterChancePercent", 40, 0, 100);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_STUTTER_MIN_TICKS = BUILDER
            .comment("Loading Screen: shortest stutter freeze. 20 = 1 second.")
            .defineInRange("loadingScreenStutterMinTicks", 20, 1, 600);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_STUTTER_MAX_TICKS = BUILDER
            .comment("Loading Screen: longest stutter freeze. 60 = 3 seconds.")
            .defineInRange("loadingScreenStutterMaxTicks", 60, 1, 600);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MAX_TOTAL_TICKS = BUILDER
            .comment("Loading Screen: hard safety cap on one session, including every stutter and restart.",
                    "Input is locked for the duration, so this guarantees you can never be stuck for longer",
                    "than this no matter how the rolls land. 400 = 20 seconds.")
            .defineInRange("loadingScreenMaxTotalTicks", 400, 20, 12000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_COUNT = BUILDER
            .comment("Loading Screen: number of frames in the animation sprite sheet (see CLAUDE.md 13.6).",
                    "The shipped chest sheet is 36 frames.")
            .defineInRange("loadingScreenFrameCount", 36, 1, 512);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_WIDTH = BUILDER
            .comment("Loading Screen: width in pixels of ONE frame. Frames need not be square.")
            .defineInRange("loadingScreenFrameWidth", 128, 1, 1024);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_HEIGHT = BUILDER
            .comment("Loading Screen: height in pixels of ONE frame. The shipped chest sheet is 128x152.")
            .defineInRange("loadingScreenFrameHeight", 152, 1, 1024);

    public static final ModConfigSpec.BooleanValue LOADING_SCREEN_SHEET_VERTICAL = BUILDER
            .comment("Loading Screen: true if the sheet stacks frames DOWN one column (an Aseprite vertical",
                    "export, like the shipped chest), false if they run left-to-right in one row.")
            .define("loadingScreenSheetVertical", true);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_1_WEIGHT = BUILDER
            .comment("Loading Screen: relative weight of hold-music track 1. The four weights are rolled",
                    "against their own total, so they need not add to anything in particular — the shipped",
                    "375/375/240/10 works out as 37.5% / 37.5% / 24% / 1%.")
            .defineInRange("loadingScreenMusic1Weight", 375, 0, 100000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_2_WEIGHT = BUILDER
            .comment("Loading Screen: relative weight of hold-music track 2.")
            .defineInRange("loadingScreenMusic2Weight", 375, 0, 100000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_3_WEIGHT = BUILDER
            .comment("Loading Screen: relative weight of hold-music track 3.")
            .defineInRange("loadingScreenMusic3Weight", 240, 0, 100000);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_MUSIC_GOOFY_WEIGHT = BUILDER
            .comment("Loading Screen: relative weight of the rare goofy track. 10 of 1000 = 1%.")
            .defineInRange("loadingScreenMusicGoofyWeight", 10, 0, 100000);

    public static final ModConfigSpec.DoubleValue LOADING_SCREEN_MUSIC_VOLUME = BUILDER
            .comment("Loading Screen: volume of the normal hold-music tracks.")
            .defineInRange("loadingScreenMusicVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LOADING_SCREEN_GOOFY_VOLUME = BUILDER
            .comment("Loading Screen: volume of the rare goofy track — deliberately quieter than the rest.")
            .defineInRange("loadingScreenGoofyVolume", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue LOADING_SCREEN_FRAME_TICKS = BUILDER
            .comment("Loading Screen: ticks each animation frame is held. 2 = 10fps, so the 36-frame chest",
                    "opens and shuts once every 3.6s — about the length of a typical fake load. 1 = 20fps.")
            .defineInRange("loadingScreenFrameTicks", 2, 1, 100);

    public static final ModConfigSpec.IntValue SUPER_EXPLOSIVE_CHANCE_PERCENT = BUILDER
            .comment("Super Explosive: flat chance (%) of detonating each time you take damage. Deliberately",
                    "constant — it never ramps, so it's a standing risk rather than a building one.")
            .defineInRange("superExplosiveChancePercent", 5, 0, 100);

    public static final ModConfigSpec.DoubleValue SUPER_EXPLOSIVE_POWER = BUILDER
            .comment("Super Explosive: blast radius. TNT is 4.0, a creeper 3.0.")
            .defineInRange("superExplosivePower", 2.5, 0.1, 32.0);

    public static final ModConfigSpec.IntValue SUPER_EXPLOSIVE_SELF_DAMAGE_PERCENT = BUILDER
            .comment("Super Explosive: percent of the blast's damage that you take yourself. You are at the",
                    "dead centre of your own explosion, so at 100 it would simply one-shot you every time —",
                    "15 keeps it a genuine hit without being an execution. Everyone ELSE takes it in full.")
            .defineInRange("superExplosiveSelfDamagePercent", 15, 0, 100);

    public static final ModConfigSpec.DoubleValue SUPER_EXPLOSIVE_KNOCKBACK_MULTIPLIER = BUILDER
            .comment("Super Explosive: knockback multiplier for the blast, applied to everyone caught in it",
                    "(you included). 1.0 is a vanilla explosion; higher sends people flying.")
            .defineInRange("superExplosiveKnockbackMultiplier", 2.5, 0.0, 20.0);

    public static final ModConfigSpec.IntValue PACING_MIN_SECONDS = BUILDER
            .comment("Pacing: shortest dramatic moment, in seconds.")
            .defineInRange("pacingMinSeconds", 5, 1, 120);

    public static final ModConfigSpec.IntValue PACING_MAX_SECONDS = BUILDER
            .comment("Pacing: longest dramatic moment, in seconds.")
            .defineInRange("pacingMaxSeconds", 30, 1, 120);

    public static final ModConfigSpec.DoubleValue PACING_LOW_BIAS_EXPONENT = BUILDER
            .comment("Pacing: how hard the random duration is biased toward the short end. The roll is",
                    "min + (max-min) * r^exp with r in [0,1); 1.0 is uniform, higher favours shorter moments.",
                    "2.0 means most moments land near 5s with the occasional long one.")
            .defineInRange("pacingLowBiasExponent", 2.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue PACING_FREEZE_RADIUS = BUILDER
            .comment("Pacing: how far around the victim entities are caught in the time-stop.")
            .defineInRange("pacingFreezeRadius", 8.0, 1.0, 32.0);

    public static final ModConfigSpec.IntValue PACING_MAX_INVOLVED = BUILDER
            .comment("Pacing: cap on how many nearby entities are frozen alongside the victim. This is a",
                    "SAFETY limit, not a presentation one — every frozen entity is also made invulnerable and",
                    "held in place each tick, so an uncapped sweep in a mob farm could hurt a server badly.")
            .defineInRange("pacingMaxInvolved", 12, 0, 64);

    public static final ModConfigSpec.IntValue PACING_SHOT_TICKS = BUILDER
            .comment("Pacing: ticks each camera angle is held before cutting to the next. 14 ~ 0.7s.")
            .defineInRange("pacingShotTicks", 14, 2, 200);

    public static final ModConfigSpec.IntValue PACING_THE_ONE_PIECE_CHANCE = BUILDER
            .comment("Pacing: 1-in-N chance that a camera-cut click is replaced by the revelation sound.")
            .defineInRange("pacingTheOnePieceChance", 250, 1, 100000);

    public static final ModConfigSpec.DoubleValue PACING_CLICK_VOLUME = BUILDER
            .comment("Pacing: volume of the click between camera cuts.")
            .defineInRange("pacingClickVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue PACING_THEME_VOLUME = BUILDER
            .comment("Pacing: volume of the dramatic theme.")
            .defineInRange("pacingThemeVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_INTERVAL_TICKS = BUILDER
            .comment("Popularity: how often (ticks) to try conjuring hostiles around the victim. 45 ~ 2.25s;",
                    "higher = the horde builds up more gradually.")
            .defineInRange("popularitySpawnIntervalTicks", 45, 1, 1200);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_ATTEMPTS = BUILDER
            .comment("Popularity: spawn attempts per interval (not all find a valid spot). Lower = gentler ramp.")
            .defineInRange("popularitySpawnAttempts", 2, 1, 40);

    public static final ModConfigSpec.IntValue POPULARITY_MAX_MOBS = BUILDER
            .comment("Popularity: cap on curse-spawned mobs alive around the victim, so the horde is a crowd",
                    "and not a lag machine. Spawning pauses once this many are already chasing.")
            .defineInRange("popularityMaxMobs", 34, 1, 300);

    public static final ModConfigSpec.IntValue POPULARITY_DISCOVERY_HORDE_SIZE = BUILDER
            .comment("Popularity: discovered for the victim once this many curse-spawned mobs have amassed",
                    "around them — you notice the mod when the crowd is unmistakable, not on the first spawn.")
            .defineInRange("popularityDiscoveryHordeSize", 15, 1, 300);

    public static final ModConfigSpec.IntValue POPULARITY_PROLONGED_TRACKING_TICKS = BUILDER
            .comment("Popularity: how long (ticks) a hostile keeps hunting you AFTER losing line of sight",
                    "before giving up — vanilla is ~60 (3s). 300 = 15s of dogged tracking through walls, but",
                    "they still have to SEE you first to lock on (no straight-up x-ray vision).")
            .defineInRange("popularityProlongedTrackingTicks", 300, 0, 6000);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_RADIUS_MIN = BUILDER
            .comment("Popularity: nearest a conjured mob appears — kept off the victim so they don't pop in",
                    "your face, they come running from a little way off.")
            .defineInRange("popularitySpawnRadiusMin", 6, 1, 64);

    public static final ModConfigSpec.IntValue POPULARITY_SPAWN_RADIUS_MAX = BUILDER
            .comment("Popularity: furthest a conjured mob appears.")
            .defineInRange("popularitySpawnRadiusMax", 20, 2, 128);

    public static final ModConfigSpec.IntValue POPULARITY_MAX_SPAWN_LIGHT = BUILDER
            .comment("Popularity: a conjured mob only appears where the light level is at or below this — the",
                    "'daylight and torches keep you safe' gate. 7 matches vanilla's dark-enough threshold, so",
                    "lit areas and daytime surfaces stay clear while caves and night fill up.")
            .defineInRange("popularityMaxSpawnLight", 7, 0, 15);

    public static final ModConfigSpec.IntValue POPULARITY_DETECTION_RADIUS = BUILDER
            .comment("Popularity: any hostile within this radius locks onto the victim, walls or not — this is",
                    "the 'detection radius is bigger' half, and it drags in natural spawns too, not just ours.")
            .defineInRange("popularityDetectionRadius", 48, 1, 128);

    public static final ModConfigSpec.IntValue POPULARITY_RETARGET_INTERVAL_TICKS = BUILDER
            .comment("Popularity: how often (ticks) nearby hostiles are set up as dedicated hunters (bigger",
                    "follow range, prolonged tracking, doors) and, if they can SEE you, re-aimed at you so you",
                    "stay the priority. 20 = 1s.")
            .defineInRange("popularityRetargetIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue YAP_INTERVAL_MIN_TICKS = BUILDER
            .comment("Yap: shortest gap between outbursts, in ticks. 200 = 10s.")
            .defineInRange("yapIntervalMinTicks", 200, 20, 24000);

    public static final ModConfigSpec.IntValue YAP_INTERVAL_MAX_TICKS = BUILDER
            .comment("Yap: longest gap between outbursts, in ticks. 600 = 30s.")
            .defineInRange("yapIntervalMaxTicks", 600, 20, 24000);

    public static final ModConfigSpec.IntValue YAP_SINGLE_WEIGHT = BUILDER
            .comment("Yap: relative chance an outburst is a single message (most common).")
            .defineInRange("yapSingleWeight", 70, 0, 1000);

    public static final ModConfigSpec.IntValue YAP_DOUBLE_WEIGHT = BUILDER
            .comment("Yap: relative chance an outburst is a scripted 2-message combo, sent one after another.")
            .defineInRange("yapDoubleWeight", 25, 0, 1000);

    public static final ModConfigSpec.IntValue YAP_TRIPLE_WEIGHT = BUILDER
            .comment("Yap: relative chance an outburst is a scripted 3-message combo (rarest).")
            .defineInRange("yapTripleWeight", 5, 0, 1000);

    public static final ModConfigSpec.IntValue YAP_MESSAGE_GAP_TICKS = BUILDER
            .comment("Yap: ticks between the messages of a multi-message combo, so they land in sequence",
                    "rather than all at once. 30 = 1.5s.")
            .defineInRange("yapMessageGapTicks", 30, 1, 200);

    public static final ModConfigSpec.IntValue YAP_EVENT_COOLDOWN_TICKS = BUILDER
            .comment("Yap: cooldown (ticks) per EVENT reaction type — taking damage, dealing damage, opening",
                    "a chest, dying, a player being near. Much longer than the ambient chatter so a reaction",
                    "line stays a treat, not a spam. 2400 = 2 min, tracked separately per event.")
            .defineInRange("yapEventCooldownTicks", 2400, 0, 72000);

    public static final ModConfigSpec.DoubleValue YAP_PROXIMITY_RADIUS = BUILDER
            .comment("Yap: how close another player must be to set off the 'someone's near' reaction.")
            .defineInRange("yapProximityRadius", 6.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue REPEL_RADIUS = BUILDER
            .comment("Repel: how close a dropped item or XP orb must be to the victim to start sliding away.")
            .defineInRange("repelRadius", 6.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue REPEL_SPEED = BUILDER
            .comment("Repel: horizontal slide speed (blocks/tick). Kept below sprint (~0.13) so you can always",
                    "chase your stuff down — 0.084 is a steady, catchable crawl.")
            .defineInRange("repelSpeed", 0.084, 0.01, 0.13);

    public static final ModConfigSpec.DoubleValue REPEL_HAZARD_SCAN_RADIUS = BUILDER
            .comment("Repel: how far from each sliding item to look for a hazard (TNT/lava/cactus/ledge/player)",
                    "to steer it toward — but only ever in a direction that still points AWAY from the victim.")
            .defineInRange("repelHazardScanRadius", 5.0, 1.0, 16.0);

    public static final ModConfigSpec.IntValue REPEL_LEDGE_DROP_MIN = BUILDER
            .comment("Repel: blocks of empty air below a neighbouring column for it to count as a ledge worth",
                    "nudging an item off.")
            .defineInRange("repelLedgeDropMin", 2, 1, 32);

    public static final ModConfigSpec.IntValue REPEL_MAX_ENTITIES = BUILDER
            .comment("Repel: cap on how many items/orbs are pushed per tick, so a giant pile can't lag.")
            .defineInRange("repelMaxEntities", 64, 1, 512);

    public static final ModConfigSpec.DoubleValue FARMHAND_RADIUS = BUILDER
            .comment("Farmhand: how far around the victim untamed animals get recruited to crowd them — a wide",
                    "radius, so even fairly distant animals come waddling over to get in the way.")
            .defineInRange("farmhandRadius", 24.0, 4.0, 64.0);

    public static final ModConfigSpec.DoubleValue FARMHAND_NAV_SPEED = BUILDER
            .comment("Farmhand: pathfinding speed multiplier for recruited animals — well above 1 so they",
                    "visibly hustle to crowd you rather than ambling over.")
            .defineInRange("farmhandNavSpeed", 1.05, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue FARMHAND_SPEED_BONUS = BUILDER
            .comment("Farmhand: extra MOVEMENT_SPEED given to a hijacked animal as a fraction of its base",
                    "(1.0 = double speed). A cow at base speed simply can't keep a player hemmed in; this is",
                    "applied by the goal and handed straight back when the curse ends.")
            .defineInRange("farmhandSpeedBonus", 0.15, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue FARMHAND_SURROUND_RADIUS = BUILDER
            .comment("Farmhand: how tight the ring of surrounding animals hugs the victim. Small = right in",
                    "your face.")
            .defineInRange("farmhandSurroundRadius", 1.3, 0.5, 8.0);

    public static final ModConfigSpec.IntValue FARMHAND_RECRUIT_INTERVAL_TICKS = BUILDER
            .comment("Farmhand: how often (ticks) nearby animals are (re)recruited and the crowd topped up.")
            .defineInRange("farmhandRecruitIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue FARMHAND_MIN_ANIMALS = BUILDER
            .comment("Farmhand: if fewer than this many animals are nearby, conjure more (given valid spawn",
                    "conditions) so you're never left in peace.")
            .defineInRange("farmhandMinAnimals", 3, 0, 64);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_COOLDOWN_TICKS = BUILDER
            .comment("Farmhand: minimum ticks between top-up spawn bursts.")
            .defineInRange("farmhandSpawnCooldownTicks", 300, 20, 12000);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_ATTEMPTS = BUILDER
            .comment("Farmhand: spawn attempts per top-up burst (not all find valid animal-spawn conditions).")
            .defineInRange("farmhandSpawnAttempts", 3, 1, 20);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_RADIUS_MIN = BUILDER
            .comment("Farmhand: nearest a conjured animal appears.")
            .defineInRange("farmhandSpawnRadiusMin", 6, 1, 32);

    public static final ModConfigSpec.IntValue FARMHAND_SPAWN_RADIUS_MAX = BUILDER
            .comment("Farmhand: furthest a conjured animal appears.")
            .defineInRange("farmhandSpawnRadiusMax", 16, 2, 64);

    public static final ModConfigSpec.IntValue GLUTTONY_SPRINT_CUTOFF = BUILDER
            .comment("Gluttony: you stop sprinting at or below this on the COMBINED 40-point bar — double",
                    "vanilla's threshold of 6, since the whole bar is doubled.")
            .defineInRange("gluttonySprintCutoff", 12, 0, 40);

    public static final ModConfigSpec.IntValue GLUTTONY_EAT_SPEED_PERCENT = BUILDER
            .comment("Gluttony: how much FASTER food is eaten, as a percent off the normal use time. 30 = a",
                    "30% shorter animation. The one perk of the curse — you have a lot of eating to do.")
            .defineInRange("gluttonyEatSpeedPercent", 30, 0, 90);

    public static final ModConfigSpec.IntValue GLUTTONY_SATURATION_PENALTY_PERCENT = BUILDER
            .comment("Gluttony: percent of saturation taken back off every food eaten, so meals don't last",
                    "and you have to keep grazing.")
            .defineInRange("gluttonySaturationPenaltyPercent", 20, 0, 100);

    public static final ModConfigSpec.DoubleValue GLUTTONY_MODEL_SCALE_BONUS = BUILDER
            .comment("Gluttony: added to the SCALE attribute, so 0.35 renders the player at 1.35x — physically",
                    "wider, and it makes tight gaps a real problem.")
            .defineInRange("gluttonyModelScaleBonus", 0.35, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_MOB_FLEE_RADIUS = BUILDER
            .comment("Unhygienic: how far the stink reaches for NON-UNDEAD mobs — anything inside runs away.",
                    "Undead are unbothered; they smell worse.")
            .defineInRange("unhygienicMobFleeRadius", 8.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_FLEE_SPEED = BUILDER
            .comment("Unhygienic: pathfinding speed multiplier for a mob fleeing the smell.")
            .defineInRange("unhygienicFleeSpeed", 1.35, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PANIC_RADIUS = BUILDER
            .comment("Unhygienic: get this close to a fleeing mob and it panics — it breaks into a proper run",
                    "instead of an unhurried walk away.")
            .defineInRange("unhygienicPanicRadius", 3.5, 0.5, 32.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PANIC_SPEED = BUILDER
            .comment("Unhygienic: pathfinding speed multiplier while panicking (inside unhygienicPanicRadius).")
            .defineInRange("unhygienicPanicSpeed", 2.0, 0.5, 4.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PLAYER_DRIFT_RADIUS = BUILDER
            .comment("Unhygienic: how close another PLAYER has to get before they start drifting away.")
            .defineInRange("unhygienicPlayerDriftRadius", 3.0, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_PLAYER_DRIFT_FORCE = BUILDER
            .comment("Unhygienic: how hard nearby players are nudged away per push. Deliberately subtle — a",
                    "slide they can walk against, not a shove.")
            .defineInRange("unhygienicPlayerDriftForce", 0.04, 0.0, 0.5);

    public static final ModConfigSpec.IntValue UNHYGIENIC_FLY_INTERVAL = BUILDER
            .comment("Unhygienic: ticks between fly specks. Spawning one every tick threw out ~80 particles a",
                    "second and looked like flung dust; a slower rate along a smooth path reads as an actual",
                    "fly and costs a fraction as much.")
            .defineInRange("unhygienicFlyInterval", 3, 1, 40);

    public static final ModConfigSpec.DoubleValue UNHYGIENIC_FLY_VOLUME = BUILDER
            .comment("Unhygienic: volume of the ambient fly buzz.")
            .defineInRange("unhygienicFlyVolume", 0.56, 0.0, 1.0);

    public static final ModConfigSpec.IntValue UNHYGIENIC_PARTICLE_INTERVAL = BUILDER
            .comment("Unhygienic: ticks between puffs of the green stink cloud.")
            .defineInRange("unhygienicParticleInterval", 10, 1, 200);

    public static final ModConfigSpec.IntValue UNHYGIENIC_FLY_SOUND_MIN_TICKS = BUILDER
            .comment("Unhygienic: shortest gap between ambient fly buzzes. 120 = 6s.")
            .defineInRange("unhygienicFlySoundMinTicks", 120, 20, 6000);

    public static final ModConfigSpec.IntValue UNHYGIENIC_FLY_SOUND_MAX_TICKS = BUILDER
            .comment("Unhygienic: longest gap between ambient fly buzzes. 400 = 20s.")
            .defineInRange("unhygienicFlySoundMaxTicks", 400, 20, 6000);

    public static final ModConfigSpec.IntValue ECHOES_INTERVAL_MIN_TICKS = BUILDER
            .comment("Echoes: shortest gap between hallucinations. 160 = 8s.")
            .defineInRange("echoesIntervalMinTicks", 160, 20, 24000);

    public static final ModConfigSpec.IntValue ECHOES_INTERVAL_MAX_TICKS = BUILDER
            .comment("Echoes: longest gap between hallucinations. 1300 = 65s. The wide spread is the point —",
                    "a predictable rhythm would tell the victim instantly which sounds were fake.")
            .defineInRange("echoesIntervalMaxTicks", 1300, 20, 24000);

    public static final ModConfigSpec.IntValue ECHOES_DISCOVERY_DELAY_TICKS = BUILDER
            .comment("Echoes: ticks after the FIRST hallucination before the victim is told what's happening,",
                    "so the penny drops a moment later rather than the alert spoiling the sound. 40 = 2s.")
            .defineInRange("echoesDiscoveryDelayTicks", 40, 0, 600);

    public static final ModConfigSpec.DoubleValue ECHOES_VOLUME = BUILDER
            .comment("Echoes: master volume for hallucinated sounds. 1.0 keeps them indistinguishable from",
                    "real ones, which is the whole point — turn it down only if they're overbearing.")
            .defineInRange("echoesVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BAD_SWIMMER_STEP_BONUS = BUILDER
            .comment("Bad Swimmer: extra step height while in liquid, on top of vanilla's 0.6. 0.5 takes it to",
                    "1.1, so a single block can be WALKED up on the bottom. This is the lever rather than a",
                    "weaker pull because underwater there is no impulse jump to boost — past the fluid-jump",
                    "threshold vanilla routes jumping to a gentle sustained thrust, so the only way to clear a",
                    "block by rising is to swim up, which is the one thing this curse exists to forbid.")
            .defineInRange("badSwimmerStepBonus", 0.5, 0.0, 2.0);

    // --- Slippery Feet ---------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SLIPPERY_CHECK_INTERVAL = BUILDER
            .comment("Slippery Feet: how often the standing-near-a-ledge roll is made, in ticks.")
            .defineInRange("slipperyCheckIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue SLIPPERY_STANDING_CHANCE = BUILDER
            .comment("Slippery Feet: percent chance per check of slipping while merely STANDING near a ledge",
                    "or hazard. Deliberately very low — standing near an edge should be a background dread,",
                    "not a coin flip. The crouch case is where the curse actually bites.")
            .defineInRange("slipperyStandingChancePercent", 2, 0, 100);

    public static final ModConfigSpec.IntValue SLIPPERY_CROUCH_MIN_TICKS = BUILDER
            .comment("Slippery Feet: shortest time crouched over a ledge before you are GUARANTEED to go off",
                    "it. 20 = 1s.")
            .defineInRange("slipperyCrouchMinTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue SLIPPERY_CROUCH_MAX_TICKS = BUILDER
            .comment("Slippery Feet: longest that grace period lasts. 40 = 2s. The actual value is re-rolled",
                    "between min and max each time you settle over an edge, so it can't be counted out.")
            .defineInRange("slipperyCrouchMaxTicks", 40, 1, 200);

    public static final ModConfigSpec.IntValue SLIPPERY_LEDGE_DROP_MIN = BUILDER
            .comment("Slippery Feet: how many clear blocks below a neighbouring column make it a ledge.")
            .defineInRange("slipperyLedgeDropMin", 2, 1, 32);

    public static final ModConfigSpec.DoubleValue SLIPPERY_PUSH_FORCE = BUILDER
            .comment("Slippery Feet: how hard you are shoved off, in blocks/tick.")
            .defineInRange("slipperyPushForce", 0.35, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue SLIPPERY_PUSH_LIFT = BUILDER
            .comment("Slippery Feet: small upward kick on the shove, so you clear the lip of the block rather",
                    "than scraping down its face.")
            .defineInRange("slipperyPushLift", 0.18, 0.0, 2.0);

    // --- Sticky ----------------------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue STICKY_BLOCKS_DROPPING = BUILDER
            .comment("Sticky: whether items refuse to be dropped (Q, and dragging out of the inventory).")
            .define("stickyBlocksDropping", true);

    public static final ModConfigSpec.BooleanValue STICKY_BLOCKS_ARMOUR = BUILDER
            .comment("Sticky: whether armour refuses to come off. Armour that BREAKS is still lost — the",
                    "restore only fires when the piece can actually be found after removal, so this can't",
                    "become an infinite-durability exploit.")
            .define("stickyBlocksArmourRemoval", true);

    // --- Glass Cannon ----------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue GLASS_CANNON_DAMAGE_TAKEN_MULT = BUILDER
            .comment("Glass Cannon: multiplier on ALL incoming damage. 2.0 = you take 200%.")
            .defineInRange("glassCannonDamageTakenMultiplier", 2.0, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue GLASS_CANNON_DAMAGE_DEALT_MULT = BUILDER
            .comment("Glass Cannon: multiplier on MELEE damage you deal (direct hits only, not projectiles).",
                    "1.5 = you deal 150%.")
            .defineInRange("glassCannonDamageDealtMultiplier", 1.5, 1.0, 10.0);

    // --- Giant -----------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue GIANT_SCALE = BUILDER
            .comment("Giant: SCALE multiplier (model AND hitbox). 3.0 = three times your size.")
            .defineInRange("giantScale", 3.0, 1.1, 10.0);
    public static final ModConfigSpec.DoubleValue GIANT_SPEED_MULT = BUILDER
            .comment("Giant: MOVEMENT_SPEED multiplier. 0.9 = 10% slower, a lumbering giant.")
            .defineInRange("giantSpeedMultiplier", 0.9, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue GIANT_ATTACK_SPEED_MULT = BUILDER
            .comment("Giant: ATTACK_SPEED multiplier — a longer swing cooldown. 0.7 = a 30% slower swing.")
            .defineInRange("giantAttackSpeedMultiplier", 0.7, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue GIANT_REACH_MULT = BUILDER
            .comment("Giant: ENTITY_INTERACTION_RANGE multiplier — the giant's reach. 2.0 = double, so it can hit things on the floor at its feet.")
            .defineInRange("giantReachMultiplier", 2.0, 1.0, 5.0);
    public static final ModConfigSpec.DoubleValue GIANT_DAMAGE_TAKEN_MULT = BUILDER
            .comment("Giant: multiplier on ALL incoming damage. 0.25 = you take 75% less.")
            .defineInRange("giantDamageTakenMultiplier", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GIANT_MELEE_DEALT_MULT = BUILDER
            .comment("Giant: multiplier on MELEE damage you deal. 1.8 = you deal 80% more.")
            .defineInRange("giantMeleeDealtMultiplier", 1.8, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue GIANT_STOMP_DAMAGE = BUILDER
            .comment("Giant: damage dealt to anything the giant physically stands on (before the melee multiplier).")
            .defineInRange("giantStompDamage", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue GIANT_STOMP_KNOCKBACK = BUILDER
            .comment("Giant: how hard a stomped entity is launched clear (velocity units).")
            .defineInRange("giantStompKnockback", 1.4, 0.0, 5.0);
    public static final ModConfigSpec.IntValue GIANT_STOMP_INTERVAL_TICKS = BUILDER
            .comment("Giant: minimum ticks between stomps on the SAME entity (they're launched clear, so this is a safety cap).")
            .defineInRange("giantStompIntervalTicks", 10, 1, 200);
    public static final ModConfigSpec.DoubleValue GIANT_HIT_KNOCKBACK = BUILDER
            .comment("Giant: EXTRA knockback strength applied to a base melee hit (on top of vanilla's), so the giant's hits fling things.")
            .defineInRange("giantHitKnockback", 1.0, 0.0, 5.0);

    // --- New QoL blessings: Speed / Forgiveness / Drive -----------------------------------------------
    public static final ModConfigSpec.DoubleValue SPEED_SPRINT_BONUS = BUILDER
            .comment("Blessing of Speed: MOVEMENT_SPEED bonus applied ONLY while sprinting (its own modifier, stacks with Speed/Ninja). 0.6 = ~60% faster sprint.")
            .defineInRange("speedSprintBonus", 0.6, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue FORGIVENESS_HITBOX_INFLATE = BUILDER
            .comment("Forgiveness: how many blocks bigger (each side) an entity's hitbox is FOR YOU — melee near-misses connect and your projectiles curve onto it. ~0.4 ≈ 40% bigger on a typical mob; a flat floor helps tiny/baby mobs.")
            .defineInRange("forgivenessHitboxInflate", 0.4, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue FORGIVENESS_PROJECTILE_STEER = BUILDER
            .comment("Forgiveness: how hard YOUR projectile curves onto an entity whose enlarged box it was about to pass through (0..1). Higher = it connects more reliably.")
            .defineInRange("forgivenessProjectileSteer", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DRIVE_RADIUS = BUILDER
            .comment("Drive: radius (blocks) within which nearby animals have their breeding cooldown cleared each tick.")
            .defineInRange("driveRadius", 16.0, 2.0, 64.0);

    // --- More QoL blessings: Vein Miner / Collector / Restock / Sanguine / Homebody / Sonar -----------
    public static final ModConfigSpec.IntValue VEIN_MINER_MAX = BUILDER
            .comment("Vein Miner: max blocks felled in one break (the vein/tree cap, for safety/lag).")
            .defineInRange("veinMinerMaxBlocks", 64, 1, 512);
    public static final ModConfigSpec.DoubleValue COLLECTOR_ITEM_RADIUS = BUILDER
            .comment("Collector: radius (blocks) within which dropped items are drawn to you.")
            .defineInRange("collectorItemRadius", 12.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue COLLECTOR_ITEM_SPEED = BUILDER
            .comment("Collector: pull speed (blocks/tick) for dropped items.")
            .defineInRange("collectorItemSpeed", 0.45, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue COLLECTOR_XP_RADIUS = BUILDER
            .comment("Collector: HUGE radius (blocks) within which XP orbs are dragged to you.")
            .defineInRange("collectorXpRadius", 48.0, 1.0, 128.0);
    public static final ModConfigSpec.DoubleValue COLLECTOR_XP_SPEED = BUILDER
            .comment("Collector: HUGE pull speed (blocks/tick) for XP orbs — the magnet on steroids.")
            .defineInRange("collectorXpSpeed", 1.6, 0.1, 6.0);
    public static final ModConfigSpec.IntValue COLLECTOR_GRACE_TICKS = BUILDER
            .comment("Collector: how long (ticks) a dropped item must sit before it starts drifting to you — a grace period so fresh drops don't insta-float.")
            .defineInRange("collectorGraceTicks", 60, 0, 600);
    public static final ModConfigSpec.DoubleValue COLLECTOR_CROUCH_RADIUS_MULT = BUILDER
            .comment("Collector: while CROUCHING, the collection radius is multiplied by this (0.15 = greatly reduced, so you can crouch to grab specific drops).")
            .defineInRange("collectorCrouchRadiusMultiplier", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SANGUINE_REGEN_MULT = BUILDER
            .comment("Sanguine: multiplier on your NATURAL regen. 0.2 = you heal at 20% of normal by resting.")
            .defineInRange("sanguineRegenMultiplier", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SANGUINE_LIFESTEAL = BUILDER
            .comment("Sanguine: fraction of damage you deal (melee AND projectile) healed back to you. 0.3 = 30% lifesteal.")
            .defineInRange("sanguineLifesteal", 0.3, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue HOMEBODY_RADIUS = BUILDER
            .comment("Homebody: how near your spawn point (blocks) you must be to get the regen + haste comfort.")
            .defineInRange("homebodyRadius", 24.0, 2.0, 128.0);
    public static final ModConfigSpec.IntValue HOMEBODY_REGEN_AMPLIFIER = BUILDER
            .comment("Homebody: Regeneration amplifier granted near home (0 = Regen I).")
            .defineInRange("homebodyRegenAmplifier", 0, 0, 4);
    public static final ModConfigSpec.IntValue HOMEBODY_HASTE_AMPLIFIER = BUILDER
            .comment("Homebody: Haste amplifier granted near home (0 = Haste I).")
            .defineInRange("homebodyHasteAmplifier", 0, 0, 4);
    public static final ModConfigSpec.IntValue SONAR_INTERVAL_TICKS = BUILDER
            .comment("Sonar: ticks between pings (2800 = 140s).")
            .defineInRange("sonarIntervalTicks", 2800, 20, 24000);
    public static final ModConfigSpec.IntValue SONAR_BUILDUP_TICKS = BUILDER
            .comment("Sonar: the CHARGE/buildup (ticks) before a pulse fires — a telegraphed swell of sound + light. 50 = 2.5s.")
            .defineInRange("sonarBuildupTicks", 50, 0, 400);
    public static final ModConfigSpec.DoubleValue SONAR_RADIUS = BUILDER
            .comment("Sonar: detection radius (blocks) of a ping.")
            .defineInRange("sonarRadius", 70.0, 4.0, 256.0);
    public static final ModConfigSpec.IntValue SONAR_GLOW_TICKS = BUILDER
            .comment("Sonar: how long (ticks) detected entities glow after a pulse. 50 = 2.5s.")
            .defineInRange("sonarGlowTicks", 50, 5, 200);
    public static final ModConfigSpec.DoubleValue SONAR_CROUCH_RADIUS_MULT = BUILDER
            .comment("Sonar: a CROUCHED player's effective detection radius multiplier (0.6 = 40% smaller, so crouching hides you better).")
            .defineInRange("sonarCrouchRadiusMultiplier", 0.6, 0.1, 1.0);
    public static final ModConfigSpec.IntValue SONAR_DAMAGE_REDUCTION_TICKS = BUILDER
            .comment("Sonar: how many ticks are shaved off the next ping each time you take damage. 20 = 1s.")
            .defineInRange("sonarDamageReductionTicks", 20, 0, 600);

    // --- Heavy Handed ----------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue HEAVY_HANDED_DURABILITY_MULT = BUILDER
            .comment("Heavy Handed: durability-loss multiplier on your tools and armour. 4.0 = they wear four",
                    "times as fast. Applied as EXTRA damage on top of the normal loss, so Unbreaking still",
                    "mitigates it — you're clumsy, not exempt from enchantments.")
            .defineInRange("heavyHandedDurabilityMultiplier", 4.0, 1.0, 20.0);

    // --- Trumpet ---------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue TRUMPET_VOLUME = BUILDER
            .comment("Trumpet: loop volume. Doubles as the audible RANGE — with linear attenuation a mono",
                    "sound carries roughly volume*16 blocks, so 1.0 ~ 16 blocks. (Needs a MONO ogg to",
                    "position/attenuate at all — a stereo file plays globally at constant volume.)")
            .defineInRange("trumpetVolume", 1.0, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue TRUMPET_SPRINT_PITCH = BUILDER
            .comment("Trumpet: playback pitch (= speed) while SPRINTING. 1.0 = normal; 1.15 is a slight,",
                    "comedic speed-up. Clamped by the engine to [0.5, 2.0].")
            .defineInRange("trumpetSprintPitch", 1.15, 0.5, 2.0);

    public static final ModConfigSpec.DoubleValue TRUMPET_WALK_THRESHOLD = BUILDER
            .comment("Trumpet: how much walk-animation speed counts as 'moving' before the music kicks in.",
                    "Small enough to catch a walk, large enough to ignore idle jitter.")
            .defineInRange("trumpetWalkThreshold", 0.03, 0.0, 1.0);

    // --- Fortune (blessing) ----------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue FORTUNE_EXTRA_MIN = BUILDER
            .comment("Fortune: fewest EXTRA drops an ore can give. Additive on top of enchantment Fortune, not",
                    "multiplicative — so it stacks but doesn't explode. Ore-tag blocks only.")
            .defineInRange("fortuneExtraMin", 0, 0, 64);

    public static final ModConfigSpec.IntValue FORTUNE_EXTRA_MODE = BUILDER
            .comment("Fortune: the MOST LIKELY number of extra drops — the peak of the distribution (a",
                    "triangular roll between min and max). 1 = usually one bonus, occasionally more or none.")
            .defineInRange("fortuneExtraMode", 1, 0, 64);

    public static final ModConfigSpec.IntValue FORTUNE_EXTRA_MAX = BUILDER
            .comment("Fortune: the most extra drops possible from one ore.")
            .defineInRange("fortuneExtraMax", 3, 0, 64);

    // --- Windfall (blessing) ---------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue WINDFALL_INTERVAL_MIN = BUILDER
            .comment("Windfall: shortest gap between items drifting by on the wind, in ticks. 340 = 17s.")
            .defineInRange("windfallIntervalMinTicks", 340, 20, 72000);

    public static final ModConfigSpec.IntValue WINDFALL_INTERVAL_MAX = BUILDER
            .comment("Windfall: longest gap between drifts, in ticks. 3000 = 2.5min.")
            .defineInRange("windfallIntervalMaxTicks", 3000, 20, 72000);

    public static final ModConfigSpec.DoubleValue WINDFALL_BENEFICIAL_CHANCE = BUILDER
            .comment("Windfall: chance (0..1) a drift is something GOOD rather than junk. 'Mostly beneficial'.")
            .defineInRange("windfallBeneficialChance", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.IntValue WINDFALL_ITEM_LIFESPAN = BUILDER
            .comment("Windfall: ticks a drifted item survives before it is instantly removed if not grabbed. 300 = 15s.")
            .defineInRange("windfallItemLifespanTicks", 300, 20, 6000);

    public static final ModConfigSpec.DoubleValue WINDFALL_WALK_AWAY_DISTANCE = BUILDER
            .comment("Windfall: if you get further than this (blocks) from a drifting item, it is instantly removed.")
            .defineInRange("windfallWalkAwayDistance", 12.0, 2.0, 64.0);

    // --- Immortality (blessing) ------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue IMMORTALITY_RECOVERY_BASE = BUILDER
            .comment("Immortality: recovery (rebuild) time for your FIRST death, in ticks. 160 = 8s.")
            .defineInRange("immortalityRecoveryBaseTicks", 160, 20, 12000);

    public static final ModConfigSpec.IntValue IMMORTALITY_RECOVERY_INCREMENT = BUILDER
            .comment("Immortality: each further death ADDS this many ticks to the recovery. 200 = +10s (=> 8s, 18s, 28s).")
            .defineInRange("immortalityRecoveryIncrementTicks", 200, 0, 12000);

    public static final ModConfigSpec.IntValue IMMORTALITY_MAX_USES = BUILDER
            .comment("Immortality: how many deaths it saves you from before the blessing breaks. Default 3.")
            .defineInRange("immortalityMaxUses", 3, 1, 20);

    // --- Sixth Sense (blessing) ------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SIXTHSENSE_INTERVAL_MIN = BUILDER
            .comment("Sixth Sense: shortest gap between ordinary action-bar hints, in ticks. 600 = 30s.")
            .defineInRange("sixthSenseIntervalMinTicks", 600, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_INTERVAL_MAX = BUILDER
            .comment("Sixth Sense: longest gap between ordinary hints, in ticks. 1400 = 70s.")
            .defineInRange("sixthSenseIntervalMaxTicks", 1400, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_RARE_COOLDOWN_MIN = BUILDER
            .comment("Sixth Sense: shortest gap AFTER announcing a rare structure (they're higher-value), in ticks. 1800 = 90s.")
            .defineInRange("sixthSenseRareCooldownMinTicks", 1800, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_RARE_COOLDOWN_MAX = BUILDER
            .comment("Sixth Sense: longest gap after a rare-structure hint, in ticks. 3000 = 150s.")
            .defineInRange("sixthSenseRareCooldownMaxTicks", 3000, 40, 24000);

    public static final ModConfigSpec.IntValue SIXTHSENSE_RARE_RADIUS_CHUNKS = BUILDER
            .comment("Sixth Sense: how far out (CHUNKS) to sense RARE structures (ancient city, end city, fortress...). 12 = 192 blocks.")
            .defineInRange("sixthSenseRareRadiusChunks", 12, 1, 64);

    public static final ModConfigSpec.IntValue SIXTHSENSE_STRUCTURE_RADIUS_CHUNKS = BUILDER
            .comment("Sixth Sense: how far out (in CHUNKS) to sense ordinary structures. 8 chunks = 128 blocks.")
            .defineInRange("sixthSenseStructureRadiusChunks", 8, 1, 64);

    public static final ModConfigSpec.DoubleValue SIXTHSENSE_PLAYER_RANGE = BUILDER
            .comment("Sixth Sense: how far out (blocks) to sense other players.")
            .defineInRange("sixthSensePlayerRange", 64.0, 8.0, 512.0);

    public static final ModConfigSpec.IntValue SIXTHSENSE_BIOME_RADIUS = BUILDER
            .comment("Sixth Sense: how far out (blocks) to sense a rare biome.")
            .defineInRange("sixthSenseBiomeRadius", 2048, 128, 8192);

    // --- Iron Stomach (blessing) -----------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue IRONSTOMACH_HUNGER_MULT = BUILDER
            .comment("Iron Stomach: hunger gain from a BAD food is multiplied by this (the extra is added on top). 3.0 = +200%.")
            .defineInRange("ironStomachHungerMultiplier", 3.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue IRONSTOMACH_SATURATION_MULT = BUILDER
            .comment("Iron Stomach: saturation gain from a BAD food is multiplied by this. 1.5 = +50%.")
            .defineInRange("ironStomachSaturationMultiplier", 1.5, 1.0, 4.0);

    // --- Steady Hands (blessing) -----------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue STEADYHANDS_SPREAD_RETAIN = BUILDER
            .comment("Steady Hands: fraction of a shot's NATURAL spread to keep (0 = dead on the crosshair, 1 = vanilla).",
                    "Compresses each shot toward your aim: single shots become very accurate, and multishot's",
                    "±10° fan is squeezed to a tight-but-visible spread that can still all hit one target. 0.2 = keep 20%.")
            .defineInRange("steadyHandsSpreadRetain", 0.2, 0.0, 1.0);

    public static final ModConfigSpec.IntValue STEADYHANDS_CHARGE_SPEEDUP_TICKS = BUILDER
            .comment("Steady Hands: extra charge ticks credited each use tick while drawing a bow/loading a crossbow.",
                    "1 = roughly double charge speed.")
            .defineInRange("steadyHandsChargeSpeedupTicks", 1, 0, 10);

    // --- Hawk Guy (blessing) ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue HAWKGUY_HOMING_STRENGTH = BUILDER
            .comment("Hawk Guy: how hard your projectiles bend toward the marked target each tick while airborne (0..1).",
                    "Higher = more blatant curving. 0.3 is a strong, obvious homing arc.")
            .defineInRange("hawkGuyHomingStrength", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HAWKGUY_ACQUIRE_CONE = BUILDER
            .comment("Hawk Guy: half-angle (degrees) of the cone around your aim used to pick the intended target on release.",
                    "Deliberately wide/forgiving so a target well off the crosshair is still grabbed and homed onto.")
            .defineInRange("hawkGuyAcquireConeDegrees", 45.0, 1.0, 90.0);

    public static final ModConfigSpec.DoubleValue HAWKGUY_ACQUIRE_RANGE = BUILDER
            .comment("Hawk Guy: how far (blocks) the acquisition raycast/cone looks for the intended target.")
            .defineInRange("hawkGuyAcquireRange", 24.0, 4.0, 128.0);

    public static final ModConfigSpec.DoubleValue HAWKGUY_MAX_HOMING_RANGE = BUILDER
            .comment("Hawk Guy: if the projectile ends up further than this (blocks) from its target, it stops homing.")
            .defineInRange("hawkGuyMaxHomingRange", 64.0, 8.0, 256.0);

    // --- Personal Trainer / Studious (blessings) -------------------------------------------------------
    public static final ModConfigSpec.DoubleValue TRAINER_VILLAGER_XP_MULT = BUILDER
            .comment("Personal Trainer: multiplier on the XP a villager gains from YOUR trades. 3.0 = 3x.")
            .defineInRange("trainerVillagerXpMultiplier", 3.0, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue STUDIOUS_XP_MULT = BUILDER
            .comment("Studious: multiplier on all XP you take in. 2.5 = 2.5x.")
            .defineInRange("studiousXpMultiplier", 2.5, 1.0, 16.0);

    // --- Twist of Fate (blessing) ----------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue TWIST_NEGATE_CHANCE = BUILDER
            .comment("Twist of Fate: chance (0..1) an incoming damage event is simply negated (when off cooldown). 0.12 = 12%.")
            .defineInRange("twistNegateChance", 0.12, 0.0, 1.0);

    public static final ModConfigSpec.IntValue TWIST_COOLDOWN_TICKS = BUILDER
            .comment("Twist of Fate: cooldown after a save before it can trigger again, in ticks. 200 = 10s.")
            .defineInRange("twistCooldownTicks", 200, 0, 12000);

    /** Length of the on-screen revive "totem" flash (Last Stand / Immortality), in ticks. Common constant so
     * both the server-side effects and the client overlay agree without the effects importing client code. */
    public static final int REVIVE_FLASH_TICKS = 28;

    // --- Main Character (blessing) ---------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue MAINCHAR_RADIUS = BUILDER
            .comment("Main Character: radius (blocks) it counts nearby hostiles / players in to decide if you're 'surrounded'.")
            .defineInRange("mainCharRadius", 12.0, 4.0, 48.0);

    public static final ModConfigSpec.IntValue MAINCHAR_HOSTILE_THRESHOLD = BUILDER
            .comment("Main Character: hostile mobs nearby to activate (tier 1). Tier 2 is DOUBLE this.")
            .defineInRange("mainCharHostileThreshold", 3, 1, 64);

    public static final ModConfigSpec.IntValue MAINCHAR_PLAYER_THRESHOLD = BUILDER
            .comment("Main Character: nearby players to activate (tier 1). Tier 2 is DOUBLE this.")
            .defineInRange("mainCharPlayerThreshold", 2, 1, 32);

    public static final ModConfigSpec.IntValue MAINCHAR_STICKY_TICKS = BUILDER
            .comment("Main Character: how long the buffs + music linger after you stop meeting the conditions, in ticks. 80 = 4s.")
            .defineInRange("mainCharStickyTicks", 80, 0, 600);

    public static final ModConfigSpec.DoubleValue MAINCHAR_COOLDOWN_REDUCTION_T1 = BUILDER
            .comment("Main Character: attack-cooldown reduction at tier 1 (0..1). 0.30 = 30% faster attacks.")
            .defineInRange("mainCharCooldownReductionT1", 0.30, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue MAINCHAR_COOLDOWN_REDUCTION_T2 = BUILDER
            .comment("Main Character: attack-cooldown reduction at tier 2 (0..1). 0.45 = 45% faster attacks.")
            .defineInRange("mainCharCooldownReductionT2", 0.45, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue MAINCHAR_KNOCKBACK_T1 = BUILDER
            .comment("Main Character: outgoing-knockback multiplier at tier 1. 1.3 = 1.3x.")
            .defineInRange("mainCharKnockbackT1", 1.3, 1.0, 5.0);

    public static final ModConfigSpec.DoubleValue MAINCHAR_KNOCKBACK_T2 = BUILDER
            .comment("Main Character: outgoing-knockback multiplier at tier 2. 1.6 = 1.6x.")
            .defineInRange("mainCharKnockbackT2", 1.6, 1.0, 5.0);

    public static final ModConfigSpec.DoubleValue MAINCHAR_MUSIC_VOLUME = BUILDER
            .comment("Main Character: volume of the battle-theme loop at the protagonist (fades with distance for onlookers).")
            .defineInRange("mainCharMusicVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue MAINCHAR_MUSIC_RANGE = BUILDER
            .comment("Main Character: how far (blocks) nearby players can hear the theme; volume fades to 0 at this range.",
                    "(Manual falloff, because the supplied stereo track can't be positionally attenuated by the engine.)")
            .defineInRange("mainCharMusicRange", 24.0, 4.0, 128.0);

    // --- Farmer's Spirit (blessing) --------------------------------------------------------------------
    public static final ModConfigSpec.IntValue FARMSPIRIT_RADIUS = BUILDER
            .comment("Farmer's Spirit: radius (blocks) around you that plants grow fast in — a small, tight patch.")
            .defineInRange("farmersSpiritRadius", 4, 1, 48);

    public static final ModConfigSpec.IntValue FARMSPIRIT_INTERVAL = BUILDER
            .comment("Farmer's Spirit: ticks between growth passes. 1 = every tick (constant).")
            .defineInRange("farmersSpiritIntervalTicks", 1, 1, 100);

    public static final ModConfigSpec.IntValue FARMSPIRIT_ATTEMPTS = BUILDER
            .comment("Farmer's Spirit: how many random nearby spots are 'bone-mealed' each pass.")
            .defineInRange("farmersSpiritAttemptsPerPass", 5, 1, 64);

    public static final ModConfigSpec.IntValue FARMSPIRIT_GROWTH_PER_HIT = BUILDER
            .comment("Farmer's Spirit: how many bone-meal applications a crop/sapling gets per hit — high, so",
                    "they shoot up fast for an over-the-top blessing (grass is exempt, always just one).")
            .defineInRange("farmersSpiritGrowthPerHit", 5, 1, 20);

    public static final ModConfigSpec.DoubleValue FARMSPIRIT_GRASS_CHANCE = BUILDER
            .comment("Farmer's Spirit: chance (0..1) a grass BLOCK is bone-mealed when picked — kept low, so grass",
                    "spreads flowers/tall grass only 'to a lesser extent' than crops/saplings shoot up.")
            .defineInRange("farmersSpiritGrassChance", 0.06, 0.0, 1.0);

    // --- Brute (blessing) ------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue BRUTE_RAMP_TIME = BUILDER
            .comment("Brute: ticks of consistent sprinting (no jumping/interruptions) to reach top speed. 54 = ~2.7s.")
            .defineInRange("bruteRampTimeTicks", 54, 5, 400);

    public static final ModConfigSpec.DoubleValue BRUTE_MAX_SPEED_MULT = BUILDER
            .comment("Brute: movement-speed multiplier at full charge. 2.2 = 2.2x.")
            .defineInRange("bruteMaxSpeedMultiplier", 2.2, 1.0, 5.0);

    public static final ModConfigSpec.IntValue BRUTE_AIRBORNE_GRACE = BUILDER
            .comment("Brute: how many ticks you may be off the ground (bumps/slopes) before the charge resets — a",
                    "real jump exceeds this, so jumping interrupts the build-up but little hops over terrain don't.")
            .defineInRange("bruteAirborneGraceTicks", 5, 0, 40);

    public static final ModConfigSpec.IntValue BRUTE_WINDUP_TIME = BUILDER
            .comment("Brute: an initial quiet windup (no particles, no speed) of consistent sprinting before the ramp.",
                    "30 = 1.5s. Once completed it is BANKED — a jump resets only the ramp, not this windup.")
            .defineInRange("bruteWindupTicks", 30, 0, 200);

    public static final ModConfigSpec.IntValue BRUTE_MAX_BLOCKS_PER_TICK = BUILDER
            .comment("Brute: max hard blocks smashed per tick (caps the health drained per tick while ramming a wall).")
            .defineInRange("bruteMaxBlocksPerTick", 3, 1, 20);

    public static final ModConfigSpec.IntValue BRUTE_SHAKE_TICKS = BUILDER
            .comment("Brute: how long the little camera jolt lasts when you smash a block, in ticks.")
            .defineInRange("bruteShakeTicks", 4, 0, 40);

    public static final ModConfigSpec.DoubleValue BRUTE_SHAKE_STRENGTH = BUILDER
            .comment("Brute: strength of that smash camera jolt (slight).")
            .defineInRange("bruteShakeStrength", 0.6, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue BRUTE_FOOTSTEP_VOLUME = BUILDER
            .comment("Brute: volume of the amplified footsteps while you wind up and sprint (vanilla steps are ~0.15).")
            .defineInRange("bruteFootstepVolume", 1.6, 0.0, 5.0);

    public static final ModConfigSpec.IntValue BRUTE_ENTITY_RECOVERY_TICKS = BUILDER
            .comment("Brute: ticks to re-reach full charge after ploughing into an entity — MUCH quicker than a",
                    "from-scratch build-up, so you keep barrelling through crowds. ~26 = about 66% faster than a full reset.")
            .defineInRange("bruteEntityRecoveryTicks", 26, 1, 400);

    public static final ModConfigSpec.DoubleValue BRUTE_CAP_THRESHOLD = BUILDER
            .comment("Brute: charge fraction (0..1) at which you start launching entities and smashing blocks.")
            .defineInRange("bruteCapThreshold", 0.85, 0.1, 1.0);

    public static final ModConfigSpec.DoubleValue BRUTE_LAUNCH_FORCE = BUILDER
            .comment("Brute: how hard entities you plough into are launched, at full charge.")
            .defineInRange("bruteLaunchForce", 2.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue BRUTE_LAUNCH_DAMAGE = BUILDER
            .comment("Brute: damage dealt to entities you plough into.")
            .defineInRange("bruteLaunchDamage", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue BRUTE_BLOCK_HARDNESS_MAX = BUILDER
            .comment("Brute: hardest block (by hardness) you can smash through. 3.0 ~ stone; obsidian (50) stops you.")
            .defineInRange("bruteBlockHardnessMax", 3.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue BRUTE_HP_COST_PER_BLOCK = BUILDER
            .comment("Brute: health spent per block smashed. You won't smash if it would drop you too low.")
            .defineInRange("bruteHpCostPerBlock", 1.0, 0.0, 20.0);

    // --- Chat (blessing) -------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue CHAT_INTERVAL_MIN = BUILDER
            .comment("Chat: shortest gap between chat messages, in ticks, at peak hype (chat floods when you pop off).")
            .defineInRange("chatIntervalMinTicks", 3, 1, 400);

    public static final ModConfigSpec.IntValue CHAT_INTERVAL_MAX = BUILDER
            .comment("Chat: longest gap between chat messages, in ticks, when the chat is basically dead.")
            .defineInRange("chatIntervalMaxTicks", 300, 2, 1200);

    public static final ModConfigSpec.DoubleValue CHAT_USEFUL_CHANCE = BUILDER
            .comment("Chat: chance (0..1) a given message is a genuinely-useful info drop (nearby structure/player/etc).")
            .defineInRange("chatUsefulChance", 0.12, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_SCORE_MAX = BUILDER
            .comment("Chat: cap on the internal entertainment score. Large = a real grind to fill (harder to climb).")
            .defineInRange("chatScoreMax", 1000.0, 10.0, 100000.0);

    public static final ModConfigSpec.DoubleValue CHAT_DEAD_THRESHOLD = BUILDER
            .comment("Chat: hype fraction (0..1) at/below which the chat goes DEAD — sparse, sad, no subs. Crawl out of it.")
            .defineInRange("chatDeadThreshold", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_REVIVE_THRESHOLD = BUILDER
            .comment("Chat: hype fraction (0..1) you must EXCEED to revive a dead chat (hysteresis, so it doesn't flicker).")
            .defineInRange("chatReviveThreshold", 0.14, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_PVP_SCORE = BUILDER
            .comment("Chat: entertainment score gained for a PvP hit (HIGH).")
            .defineInRange("chatPvpScore", 12.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_PVE_SCORE = BUILDER
            .comment("Chat: entertainment score gained for a PvE hit (small — it's the kill that matters).")
            .defineInRange("chatPveScore", 4.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_BUILD_SCORE = BUILDER
            .comment("Chat: entertainment score gained for placing a block (small).")
            .defineInRange("chatBuildScore", 2.5, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_MINE_SCORE = BUILDER
            .comment("Chat: entertainment score gained for breaking a block (small).")
            .defineInRange("chatMineScore", 1.8, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_IDLE_DECAY = BUILDER
            .comment("Chat: score lost per tick while IDLING (standing still doing nothing) — bleeds fast, keep performing.")
            .defineInRange("chatIdleDecay", 1.6, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue CHAT_PASSIVE_DECAY = BUILDER
            .comment("Chat: score lost per tick while doing nothing entertaining (moving about but not performing).")
            .defineInRange("chatPassiveDecay", 0.55, 0.0, 200.0);

    public static final ModConfigSpec.IntValue CHAT_SUBS_CAP = BUILDER
            .comment("Chat: maximum subs you can bank in one stream (caps the end reward). Big ceiling for big streams.")
            .defineInRange("chatSubsCap", 5000, 1, 1000000);

    public static final ModConfigSpec.IntValue CHAT_SUBS_PER_EMERALD = BUILDER
            .comment("Chat: subs required per EMERALD paid out at stream's end. Emeralds are the ONLY reward. Higher = stingier.")
            .defineInRange("chatSubsPerEmerald", 21, 1, 100000);

    public static final ModConfigSpec.DoubleValue CHAT_SLEEP_DECAY = BUILDER
            .comment("Chat: entertainment score lost per tick while you're SLEEPING (nobody tunes in to watch you nap) — a slight drain.")
            .defineInRange("chatSleepDecay", 0.8, 0.0, 50.0);

    public static final ModConfigSpec.DoubleValue CHAT_SUB_PER_VIEWER_TICK = BUILDER
            .comment("Chat: subs gained per VIEWER per tick. Since viewers scale exponentially with hype, subs explode at the top and barely move at the bottom.")
            .defineInRange("chatSubPerViewerTick", 0.00003, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CHAT_STRUCTURE_RADIUS_CHUNKS = BUILDER
            .comment("Chat: how far out (chunks) the useful-info drops look for structures.")
            .defineInRange("chatStructureRadiusChunks", 6, 1, 32);

    public static final ModConfigSpec.DoubleValue CHAT_KILL_SCORE = BUILDER
            .comment("Chat: score for killing a mob (a real highlight — spikes the chat).")
            .defineInRange("chatKillScore", 18.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_PVP_KILL_SCORE = BUILDER
            .comment("Chat: score for killing a PLAYER — the single most entertaining thing you can do.")
            .defineInRange("chatPvpKillScore", 85.0, 0.0, 3000.0);

    public static final ModConfigSpec.DoubleValue CHAT_CRIT_SCORE = BUILDER
            .comment("Chat: bonus score for landing a critical hit.")
            .defineInRange("chatCritScore", 6.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_DAMAGE_SCORE = BUILDER
            .comment("Chat: score for taking a big hit (drama is entertaining). Applies at/above the damage threshold.")
            .defineInRange("chatDamageScore", 10.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_DAMAGE_THRESHOLD = BUILDER
            .comment("Chat: minimum damage (half-hearts) taken in one hit to count as a 'big hit' for chat.")
            .defineInRange("chatDamageThreshold", 6.0, 0.5, 40.0);

    public static final ModConfigSpec.DoubleValue CHAT_CLUTCH_SCORE = BUILDER
            .comment("Chat: score for surviving a hit that leaves you on very low HP (a clutch — chat goes wild).")
            .defineInRange("chatClutchScore", 55.0, 0.0, 2000.0);

    public static final ModConfigSpec.DoubleValue CHAT_CLUTCH_HP = BUILDER
            .comment("Chat: HP (half-hearts) at or below which surviving a hit counts as a clutch.")
            .defineInRange("chatClutchHp", 4.0, 0.5, 20.0);

    public static final ModConfigSpec.DoubleValue CHAT_DEATH_PENALTY_FRACTION = BUILDER
            .comment("Chat: fraction (0..1) of your current entertainment score WIPED when you die — dying tanks interest.")
            .defineInRange("chatDeathPenaltyFraction", 0.55, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CHAT_DEATH_SPAM_TICKS = BUILDER
            .comment("Chat: how long (ticks) chat rapidly spams dealwithit/trolldance gifs after you die.")
            .defineInRange("chatDeathSpamTicks", 70, 0, 400);

    public static final ModConfigSpec.DoubleValue CHAT_FALL_SCORE = BUILDER
            .comment("Chat: score for taking notable fall damage (a classic fail).")
            .defineInRange("chatFallScore", 12.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_FALL_THRESHOLD = BUILDER
            .comment("Chat: minimum fall distance (blocks) to be worth a fail reaction.")
            .defineInRange("chatFallThreshold", 5.0, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue CHAT_ORE_SCORE = BUILDER
            .comment("Chat: score for mining a valuable ore (diamonds/emeralds/ancient debris).")
            .defineInRange("chatOreScore", 24.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_FISH_SCORE = BUILDER
            .comment("Chat: score for reeling in a catch.")
            .defineInRange("chatFishScore", 8.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_TAME_SCORE = BUILDER
            .comment("Chat: score for taming an animal (chat loves a new pet).")
            .defineInRange("chatTameScore", 28.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_TRADE_SCORE = BUILDER
            .comment("Chat: score for a villager trade.")
            .defineInRange("chatTradeScore", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_EAT_SCORE = BUILDER
            .comment("Chat: score for eating (small — mukbang content).")
            .defineInRange("chatEatScore", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_EXPLORE_SCORE = BUILDER
            .comment("Chat: score for entering a fresh chunk (exploring keeps the stream moving).")
            .defineInRange("chatExploreScore", 2.5, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue CHAT_COMBO_WINDOW_TICKS = BUILDER
            .comment("Chat: window (ticks) within which chained highlights build the hype train / combo multiplier.")
            .defineInRange("chatComboWindowTicks", 90.0, 20.0, 600.0);

    public static final ModConfigSpec.DoubleValue CHAT_COMBO_MAX_MULT = BUILDER
            .comment("Chat: maximum score multiplier from a hot combo streak.")
            .defineInRange("chatComboMaxMultiplier", 3.0, 1.0, 20.0);

    public static final ModConfigSpec.IntValue CHAT_HYPE_TRAIN_HITS = BUILDER
            .comment("Chat: number of chained highlights that triggers a HYPE TRAIN (big sub surge + banner).")
            .defineInRange("chatHypeTrainHits", 6, 2, 50);

    public static final ModConfigSpec.IntValue CHAT_HYPE_TRAIN_SUB_BONUS = BUILDER
            .comment("Chat: instant sub bonus awarded when a hype train launches.")
            .defineInRange("chatHypeTrainSubBonus", 25, 0, 100000);

    public static final ModConfigSpec.DoubleValue CHAT_DONATION_CHANCE = BUILDER
            .comment("Chat: per-message chance (0..1), scaled by hype, of a random bit donation event (bonus subs).")
            .defineInRange("chatDonationChance", 0.06, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_RAID_CHANCE = BUILDER
            .comment("Chat: per-message chance (0..1), scaled by hype, of a random incoming raid (viewer + sub surge).")
            .defineInRange("chatRaidChance", 0.02, 0.0, 1.0);

    public static final ModConfigSpec.IntValue CHAT_VIEWER_BASE = BUILDER
            .comment("Chat: viewer count at ZERO hype — keep it tiny (near-dead) so climbing means something.")
            .defineInRange("chatViewerBase", 1, 0, 100000);

    public static final ModConfigSpec.IntValue CHAT_VIEWER_MAX = BUILDER
            .comment("Chat: viewer count at FULL hype. Viewers scale EXPONENTIALLY base->max, so the top end blows up (a lot higher) while the bottom crawls.")
            .defineInRange("chatViewerMax", 12000, 1, 100000000);

    public static final ModConfigSpec.DoubleValue CHAT_HYPE_EASE = BUILDER
            .comment("Chat: how fast the shown hype eases toward the real score each tick (0..1). Smaller = more gradual climbs/drops (not instant).")
            .defineInRange("chatHypeEase", 0.025, 0.001, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_EMOTE_CHANCE = BUILDER
            .comment("Chat: chance (0..1) a normal chat line is a spammed EMOTE image instead of text, at full hype (scales down with hype).")
            .defineInRange("chatEmoteChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CHAT_GIF_CHANCE = BUILDER
            .comment("Chat: chance (0..1) a normal chat line is an animated GIF instead, at full hype (scales down with hype). Kept rarer than emotes.")
            .defineInRange("chatGifChance", 0.06, 0.0, 1.0);

    // --- Laugh Track (blessing) ------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue LAUGHTRACK_COOLDOWN = BUILDER
            .comment("Laugh Track: minimum ticks between reactions, so rapid-fire chat doesn't stack the crowd. 120 = 6s.")
            .defineInRange("laughTrackCooldownTicks", 120, 0, 2400);

    public static final ModConfigSpec.DoubleValue LAUGHTRACK_CHEER_CHANCE = BUILDER
            .comment("Laugh Track: chance (0..1) the reaction is a CHEER rather than a laugh. 0.1 = 10% cheers, 90% laughs.")
            .defineInRange("laughTrackCheerChance", 0.1, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LAUGHTRACK_VOLUME = BUILDER
            .comment("Laugh Track: volume of the crowd reaction played to everyone.")
            .defineInRange("laughTrackVolume", 1.0, 0.0, 1.0);

    // --- Coyote (blessing) -----------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue COYOTE_TICKS = BUILDER
            .comment("Coyote: how many ticks after walking off a ledge you can still jump (coyote time). 6 = 0.3s.")
            .defineInRange("coyoteTicks", 6, 0, 40);

    public static final ModConfigSpec.DoubleValue COYOTE_EDGE_MAGNETISM = BUILDER
            .comment("Coyote: gentle per-tick nudge (blocks/tick) toward a ledge you're falling short of, capped so it",
                    "assists rather than accelerates you.")
            .defineInRange("coyoteEdgeMagnetism", 0.03, 0.0, 0.5);

    public static final ModConfigSpec.DoubleValue COYOTE_ASSIST_MAX_SPEED = BUILDER
            .comment("Coyote: the edge assist only helps you up to THIS horizontal speed (blocks/tick) and never faster —",
                    "so it nudges slow/short jumps onto ledges but never turns a sprint jump into a rocket. ~0.26 = below a sprint jump.")
            .defineInRange("coyoteAssistMaxSpeed", 0.26, 0.05, 1.0);

    // --- Angler (blessing) -----------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue ANGLER_BITE_EXTRA_TICKS = BUILDER
            .comment("Angler: extra ticks knocked off the bite timer each tick (on top of vanilla's 1). 5 = fish bite ~6x faster.")
            .defineInRange("anglerBiteExtraTicks", 5, 0, 40);

    public static final ModConfigSpec.DoubleValue ANGLER_MULTI_CHANCE = BUILDER
            .comment("Angler: chance (0..1) a catch pulls out MULTIPLE things (an extra copy of the loot).")
            .defineInRange("anglerMultiChance", 0.35, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue ANGLER_SURPRISE_CHANCE = BUILDER
            .comment("Angler: chance (0..1) a catch also drags a comical surprise out of the water (treasure/fish/mob/TNT).")
            .defineInRange("anglerSurpriseChance", 0.22, 0.0, 1.0);

    // --- Excavation (blessing) -------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue EXCAVATION_RAMP_PER_BLOCK = BUILDER
            .comment("Excavation: extra mining-speed bonus gained per block broken. 0.2 = +20% speed per block.")
            .defineInRange("excavationRampPerBlock", 0.2, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue EXCAVATION_MAX_BONUS = BUILDER
            .comment("Excavation: cap on the mining-speed bonus. 4.0 = up to 5x mining speed — really fast.")
            .defineInRange("excavationMaxBonus", 4.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue EXCAVATION_DECAY_GRACE = BUILDER
            .comment("Excavation: ticks after your last block break before the built-up speed starts decaying. 40 = 2s.")
            .defineInRange("excavationDecayGraceTicks", 40, 0, 400);

    public static final ModConfigSpec.DoubleValue EXCAVATION_DECAY_PER_TICK = BUILDER
            .comment("Excavation: how much bonus is lost per tick once the grace period lapses.")
            .defineInRange("excavationDecayPerTick", 0.08, 0.0, 5.0);

    // --- Bouncy (blessing) -----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue BOUNCY_RESTITUTION = BUILDER
            .comment("Bouncy: how elastic your landing rebounds are (0..1). Lower settles in a few bounces; holding",
                    "jump adds on top, so you still build height. 0.65 bounces like a slime block that eventually stops.")
            .defineInRange("bouncyRestitution", 0.65, 0.0, 1.5);
    public static final ModConfigSpec.DoubleValue BOUNCY_JUMP_BONUS = BUILDER
            .comment("Bouncy (now a CURSE): JUMP_STRENGTH bonus as a fraction (0.5 = +50% jump height) — springy legs launch you higher.")
            .defineInRange("bouncyJumpBonus", 0.5, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_LANDING_MAX = BUILDER
            .comment("Bouncy: cap on rebound velocity, so it can't runaway into orbit.")
            .defineInRange("bouncyLandingMax", 1.9, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_MIN_FALL_VELOCITY = BUILDER
            .comment("Bouncy: minimum impact speed (blocks/tick) to rebound when you're NOT holding jump — so only",
                    "real falls from height bounce and normal jumping/landing doesn't spam little bounces. 0.5 ~ a",
                    "1.5-block drop. Holding jump drops the threshold so you can trampoline and build height. Sneak = never bounce.")
            .defineInRange("bouncyMinFallVelocity", 0.5, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_ENTITY_FORCE = BUILDER
            .comment("Bouncy: how hard entities are flung when you SPRINT into them / they sprint into you / they hit you.")
            .defineInRange("bouncyEntityForce", 1.6, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_WALK_FORCE_MULT = BUILDER
            .comment("Bouncy: fraction of the entity force applied when you merely WALK into something (a slight nudge).")
            .defineInRange("bouncyWalkForceMultiplier", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BOUNCY_COLLISION_SPEED = BUILDER
            .comment("Bouncy: how fast an entity must be closing on you (blocks/tick) to bounce off when YOU are still.")
            .defineInRange("bouncyCollisionSpeed", 0.12, 0.0, 2.0);

    // --- Hot Stuff (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue HOTSTUFF_SPEED_MULT = BUILDER
            .comment("Hot Stuff: how many times faster a furnace cooks while you're looking at it. 5.0 = 5x.")
            .defineInRange("hotStuffSpeedMultiplier", 5.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue HOTSTUFF_LOOK_RANGE = BUILDER
            .comment("Hot Stuff: how far (blocks) your gaze reaches to heat up a furnace.")
            .defineInRange("hotStuffLookRange", 8.0, 1.0, 32.0);

    // --- Silver Tongue (blessing) ----------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SILVERTONGUE_DISCOUNT = BUILDER
            .comment("Silver Tongue: how much cheaper villager trades are (0..1). 0.75 = pay 25%, get 75% refunded.")
            .defineInRange("silverTongueDiscount", 0.75, 0.0, 1.0);

    // --- Unseen (blessing) -----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue UNSEEN_REVEAL_DISTANCE = BUILDER
            .comment("Unseen: how close (blocks) someone must be to see you at all — beyond it you're fully hidden,",
                    "and mobs can't lock onto you. Getting within it is the obvious counterplay. ~6 blocks.")
            .defineInRange("unseenRevealDistance", 6.0, 1.0, 32.0);

    // --- Thick Skinned (blessing) ----------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue THICKSKIN_DAMAGE_FLOOR = BUILDER
            .comment("Thick Skinned: any single damage event AT OR BELOW this is fully ignored; damage must exceed it to land. 2.0 = 1 heart.")
            .defineInRange("thickSkinnedDamageFloor", 2.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue THICKSKIN_SHAKE_TICKS = BUILDER
            .comment("Thick Skinned: how long the little feedback screenshake lasts when a hit is shrugged off, in ticks.")
            .defineInRange("thickSkinnedShakeTicks", 4, 0, 40);

    public static final ModConfigSpec.DoubleValue THICKSKIN_SHAKE_STRENGTH = BUILDER
            .comment("Thick Skinned: strength of that feedback screenshake (subtle).")
            .defineInRange("thickSkinnedShakeStrength", 0.45, 0.0, 5.0);

    // --- Last Stand (blessing) -------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue LASTSTAND_REVIVE_HEALTH = BUILDER
            .comment("Last Stand: health you're left on after a fatal blow. 1.0 = half a heart.")
            .defineInRange("lastStandReviveHealth", 1.0, 1.0, 20.0);

    public static final ModConfigSpec.IntValue LASTSTAND_BUFF_TICKS = BUILDER
            .comment("Last Stand: duration of the Strength/Speed/Fire Resistance buffs, in ticks. 300 = 15s.")
            .defineInRange("lastStandBuffTicks", 300, 20, 6000);

    public static final ModConfigSpec.DoubleValue LASTSTAND_KNOCKBACK = BUILDER
            .comment("Last Stand: outward knockback strength applied to nearby entities on the burst (no damage).")
            .defineInRange("lastStandKnockback", 2.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue LASTSTAND_RADIUS = BUILDER
            .comment("Last Stand: radius (blocks) of the knockback burst.")
            .defineInRange("lastStandRadius", 6.0, 1.0, 32.0);

    // --- Pickpocket (blessing) -------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue PICKPOCKET_RADIUS = BUILDER
            .comment("Pickpocket: how close you must be to a player to lift from them (blocks). Very close —",
                    "you're brushing right up against them.")
            .defineInRange("pickpocketRadius", 1.5, 0.5, 8.0);

    public static final ModConfigSpec.IntValue PICKPOCKET_CHECK_INTERVAL = BUILDER
            .comment("Pickpocket: ticks between lift attempts. 100 = every 5s.")
            .defineInRange("pickpocketCheckIntervalTicks", 100, 1, 1200);

    public static final ModConfigSpec.DoubleValue PICKPOCKET_BASE_CHANCE = BUILDER
            .comment("Pickpocket: chance (0..1) per attempt to lift an item while facing/beside a player.")
            .defineInRange("pickpocketBaseChance", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue PICKPOCKET_BEHIND_MULT = BUILDER
            .comment("Pickpocket: the base chance is multiplied by this when you're BEHIND the victim — sneaking",
                    "up from behind is far more effective.")
            .defineInRange("pickpocketBehindMultiplier", 4.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue PICKPOCKET_HOTBAR_WEIGHT = BUILDER
            .comment("Pickpocket: relative weight of hotbar slots when choosing what to steal (main-inventory",
                    "slots are weight 1.0). Low, so you mostly lift from their backpack, not what they're holding.")
            .defineInRange("pickpocketHotbarWeight", 0.1, 0.0, 1.0);

    // --- Hype Man (blessing) ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue HYPEMAN_RADIUS = BUILDER
            .comment("Hype Man: how close a player must be to be dragged in as a hype-man and to hear the praise",
                    "(blocks). Also the pool the random 'speaker' of each line is picked from.")
            .defineInRange("hypemanRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.IntValue HYPEMAN_COOLDOWN = BUILDER
            .comment("Hype Man: minimum ticks between any two praises, across ALL trigger types, so a fight or a",
                    "pickup spree doesn't turn into a wall of text. 160 = ~8s.")
            .defineInRange("hypemanCooldownTicks", 160, 0, 6000);

    public static final ModConfigSpec.DoubleValue HYPEMAN_CHANCE = BUILDER
            .comment("Hype Man: chance (0..1) that an eligible action actually earns praise once the cooldown is",
                    "up. Below 1 so it stays a treat rather than clockwork.")
            .defineInRange("hypemanChance", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HYPEMAN_AMBIENT_INTERVAL = BUILDER
            .comment("Hype Man: ticks between checks for unprompted 'just being here' praise (still gated by the",
                    "cooldown and chance above). 120 = every 6s.")
            .defineInRange("hypemanAmbientIntervalTicks", 120, 20, 6000);

    // --- Tax Man (blessing) ----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue TAXMAN_SAFE_RADIUS = BUILDER
            .comment("Tax Man blessing: he'll only turn up to pay you back when no hostile mob is within this",
                    "radius (blocks) — he waits for it to be safe.")
            .defineInRange("taxmanSafeRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.IntValue TAXMAN_IDLE_TICKS = BUILDER
            .comment("Tax Man blessing: ticks of you standing roughly still before he'll approach. 60 = 3s.")
            .defineInRange("taxmanIdleTicks", 60, 1, 600);

    public static final ModConfigSpec.IntValue TAXMAN_GIFT_EMERALDS_MAX = BUILDER
            .comment("Tax Man blessing: when the tax bank is EMPTY he brings a gift instead. Emeralds: 1..MAX,",
                    "uniform.")
            .defineInRange("taxmanGiftEmeraldsMax", 10, 1, 64);

    public static final ModConfigSpec.IntValue TAXMAN_GIFT_GOLD_MAX = BUILDER
            .comment("Tax Man blessing gift: gold ingots 1..MAX, uniform.")
            .defineInRange("taxmanGiftGoldMax", 8, 1, 64);

    public static final ModConfigSpec.IntValue TAXMAN_GIFT_DIAMONDS_MAX = BUILDER
            .comment("Tax Man blessing gift: diamonds 0..MAX, BIASED toward 0 (min of two rolls), so a fistful",
                    "of diamonds is a rare treat.")
            .defineInRange("taxmanGiftDiamondsMax", 3, 0, 64);

    // --- Bodyguard (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue BODYGUARD_HEALTH = BUILDER
            .comment("Bodyguard: the skeleton's max health. Tough — it's meant to soak a beating.")
            .defineInRange("bodyguardHealth", 60.0, 1.0, 1024.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_DAMAGE = BUILDER
            .comment("Bodyguard: melee damage it deals once it's actually ATTACKING an aggressor.")
            .defineInRange("bodyguardDamage", 6.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_ARMOR = BUILDER
            .comment("Bodyguard: armour attribute (on top of any worn armour) — the 'armoured' in the brief.")
            .defineInRange("bodyguardArmor", 12.0, 0.0, 30.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_SPEED = BUILDER
            .comment("Bodyguard: movement speed. A touch quicker than a player so it can keep up and cut people off.")
            .defineInRange("bodyguardSpeed", 0.34, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_FOLLOW_DISTANCE = BUILDER
            .comment("Bodyguard: how far from the anchor it's happy to sit before trailing back to them (blocks).")
            .defineInRange("bodyguardFollowDistance", 4.0, 1.0, 32.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_TELEPORT_DISTANCE = BUILDER
            .comment("Bodyguard: if it strays (or the anchor pearls/flies) beyond this, it blinks back to the",
                    "anchor's side like a tamed wolf (blocks).")
            .defineInRange("bodyguardTeleportDistance", 12.0, 4.0, 96.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_WARNING_RADIUS = BUILDER
            .comment("Bodyguard: an intruder this close to the anchor gets WARNED to back off (blocks).")
            .defineInRange("bodyguardWarningRadius", 8.0, 1.0, 48.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_AGGRESSION_RADIUS = BUILDER
            .comment("Bodyguard: an intruder this close, ignoring the warnings, earns AGGRESSION — shoves and",
                    "warning hits (blocks).")
            .defineInRange("bodyguardAggressionRadius", 4.0, 1.0, 48.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_LEASH_RANGE = BUILDER
            .comment("Bodyguard: while ATTACKING, it gives up and returns once the aggressor is this far from the",
                    "anchor (blocks) — it guards a place, it doesn't chase to the ends of the earth.")
            .defineInRange("bodyguardLeashRange", 32.0, 4.0, 128.0);

    public static final ModConfigSpec.DoubleValue BODYGUARD_WARNING_HIT_DAMAGE = BUILDER
            .comment("Bodyguard: damage of a non-committal 'warning hit' during AGGRESSION. Low — it's a shove,",
                    "not an execution.")
            .defineInRange("bodyguardWarningHitDamage", 1.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue BODYGUARD_WARNING_HIT_INTERVAL = BUILDER
            .comment("Bodyguard: minimum ticks between warning hits.")
            .defineInRange("bodyguardWarningHitIntervalTicks", 30, 1, 200);

    public static final ModConfigSpec.IntValue BODYGUARD_PATIENCE = BUILDER
            .comment("Bodyguard: ticks an intruder can keep crowding at AGGRESSION range before the bodyguard",
                    "loses patience, draws its sword and actually attacks them (no attack from them needed).",
                    "100 = 5s. This is what makes it engage instead of shoving forever.")
            .defineInRange("bodyguardPatienceTicks", 100, 20, 1200);

    public static final ModConfigSpec.IntValue BODYGUARD_WARNINGS_BEFORE_ATTACK = BUILDER
            .comment("Bodyguard: how many spoken warnings it must actually deliver to a lingering intruder",
                    "before patience is allowed to draw steel — so it never silently jumps to violence.",
                    "(Being physically attacked still triggers immediate self-defence, warnings or not.)")
            .defineInRange("bodyguardWarningsBeforeAttack", 2, 0, 10);

    public static final ModConfigSpec.DoubleValue BODYGUARD_CHAT_RADIUS = BUILDER
            .comment("Bodyguard: only players within this radius hear it speak (blocks) — its lines are local,",
                    "not server-wide.")
            .defineInRange("bodyguardChatRadius", 24.0, 1.0, 128.0);

    public static final ModConfigSpec.IntValue BODYGUARD_RESPAWN_TICKS = BUILDER
            .comment("Bodyguard: after it dies, the blessing is NOT lost — a replacement is hired this many ticks",
                    "later. 7200 = 6 minutes.")
            .defineInRange("bodyguardRespawnTicks", 7200, 0, 720000);

    public static final ModConfigSpec.IntValue BODYGUARD_DIALOGUE_COOLDOWN = BUILDER
            .comment("Bodyguard: minimum ticks between one spoken dialogue tree and the next during a",
                    "confrontation (idle 'ambient' chatter is 3x rarer). 100 = ~5s — chatty enough to feel",
                    "alive without talking over itself.")
            .defineInRange("bodyguardDialogueCooldownTicks", 100, 20, 2000);

    public static final ModConfigSpec.IntValue BODYGUARD_DIALOGUE_LINE_GAP = BUILDER
            .comment("Bodyguard: ticks between successive lines WITHIN one dialogue tree.")
            .defineInRange("bodyguardDialogueLineGapTicks", 30, 1, 200);

    // --- Soul Bond (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SOULBOND_RADIUS = BUILDER
            .comment("Soul Bond: how close the nearest living thing must be to become your bound (blocks). The",
                    "bond continuously re-picks the nearest, so in a 1v1 it latches onto your opponent and with",
                    "a pet at your heels it latches onto the pet — which is the whole deterrent.")
            .defineInRange("soulBondRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue SOULBOND_DAMAGE_SHARE = BUILDER
            .comment("Soul Bond: fraction of the damage YOU take that your bound takes instead. 0.4 = they eat",
                    "40%, you eat the remaining 60%. Their share bypasses armour (it's a soul tether).")
            .defineInRange("soulBondDamageShare", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SOULBOND_REBIND_INTERVAL = BUILDER
            .comment("Soul Bond: ticks between re-picking the nearest living thing to bind. 20 = once a second.")
            .defineInRange("soulBondRebindIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.IntValue SOULBOND_PARTICLE_INTERVAL = BUILDER
            .comment("Soul Bond: ticks between the constant golden particles emitted on the bound entity.")
            .defineInRange("soulBondParticleIntervalTicks", 4, 1, 100);

    // --- Fullness (blessing) ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue FULLNESS_DRAIN_RATE = BUILDER
            .comment("Fullness: fraction of the NORMAL hunger drain that actually sticks. 0.2 = hunger (and its",
                    "hidden saturation) deplete at a fifth of the usual rate, so you rarely need to eat.")
            .defineInRange("fullnessDrainRate", 0.2, 0.0, 1.0);

    // --- Army (blessing) -------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue ARMY_RADIUS = BUILDER
            .comment("Army: radius (blocks) within which hostile mobs go neutral toward you and rally to your",
                    "defence when something hits you.")
            .defineInRange("armyRadius", 24, 1, 128);

    public static final ModConfigSpec.IntValue ARMY_DEFEND_DURATION = BUILDER
            .comment("Army: ticks the nearby horde keeps swarming whatever last hit you. 600 = 30s.")
            .defineInRange("armyDefendDurationTicks", 600, 20, 12000);

    public static final ModConfigSpec.IntValue ARMY_CHECK_INTERVAL = BUILDER
            .comment("Army: ticks between sweeps that keep hostiles off you (and re-aim the swarm). Low, so mobs",
                    "barely get a swing in before being pacified.")
            .defineInRange("armyCheckIntervalTicks", 5, 1, 100);

    public static final ModConfigSpec.BooleanValue ARMY_SAME_TYPE_EXCLUDED = BUILDER
            .comment("Army: if true, the swarm won't turn on its OWN kind — a zombie that hit you won't be",
                    "attacked by other zombies, but skeletons etc. still will.")
            .define("armySameTypeExcluded", true);

    // --- Reflect (blessing) ----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue REFLECT_VELOCITY_MULT = BUILDER
            .comment("Reflect: speed multiplier on a projectile sent back at its shooter. 1.5 = it returns half",
                    "again as fast as it arrived — harder to dodge, but dodgeable (it doesn't home).")
            .defineInRange("reflectVelocityMultiplier", 1.5, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue REFLECT_INACCURACY = BUILDER
            .comment("Reflect: spread on the return shot. 0.0 = dead-precise at the attacker; raise for sloppier",
                    "aim (vanilla arrows use ~1.0).")
            .defineInRange("reflectInaccuracy", 0.0, 0.0, 20.0);

    // --- Peace (blessing) ------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue PEACE_SPAWN_RATE_MULT = BUILDER
            .comment("Peace: fraction of hostile NATURAL spawns near you that are still ALLOWED. 0.3 = ~70% of",
                    "them are quietly cancelled, so far fewer monsters appear around you.")
            .defineInRange("peaceSpawnRateMultiplier", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.IntValue PEACE_RADIUS = BUILDER
            .comment("Peace: radius (blocks) around you that spawn suppression and detection reduction apply.")
            .defineInRange("peaceRadius", 48, 1, 128);

    public static final ModConfigSpec.DoubleValue PEACE_DETECTION_MULT = BUILDER
            .comment("Peace: fraction of a hostile's NORMAL follow range at which it can still notice you. 0.4 =",
                    "mobs only lock onto you at 40% of the usual distance — the opposite of Popularity.")
            .defineInRange("peaceDetectionMultiplier", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue PEACE_CHECK_INTERVAL = BUILDER
            .comment("Peace: ticks between sweeps that strip too-distant aggro and check for the discovery moment.")
            .defineInRange("peaceCheckIntervalTicks", 20, 1, 200);

    // --- Luck (blessing) -------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue LUCK_ATTRIBUTE_BONUS = BUILDER
            .comment("Luck: how much is added to your vanilla LUCK attribute. Nudges loot-table rolls (fishing,",
                    "chests) toward better outcomes. 5.0 is a big, if quiet, boost.")
            .defineInRange("luckAttributeBonus", 5.0, 0.0, 1024.0);

    // --- Siren's Call ----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SIREN_CHECK_INTERVAL = BUILDER
            .comment("Siren's Call: ticks between longing updates. 10 = twice a second.")
            .defineInRange("sirenCheckIntervalTicks", 10, 1, 100);

    public static final ModConfigSpec.DoubleValue SIREN_LONGING_MAX = BUILDER
            .comment("Siren's Call: the longing meter's ceiling. Stages are read against this.")
            .defineInRange("sirenLongingMax", 100.0, 1.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_DRY_GAIN = BUILDER
            .comment("Siren's Call: longing gained per check while OUT of water. At the default check rate,",
                    "0.2 fills an empty meter in roughly four minutes of staying dry.")
            .defineInRange("sirenDryGain", 0.2, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue SIREN_WATER_DRAIN = BUILDER
            .comment("Siren's Call: longing lost per check while in water, AFTER the grace period. Much faster",
                    "than it builds — a proper dip settles you quickly.")
            .defineInRange("sirenWaterDrain", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue SIREN_WATER_GRACE = BUILDER
            .comment("Siren's Call: minimum ticks you must stay in water before the longing begins to DROP.",
                    "During this grace the meter holds and the magenta mind-control shader fades out — so by",
                    "the time it's actually falling, the screen is clear again. 60 = 3s.")
            .defineInRange("sirenWaterGraceTicks", 60, 0, 600);

    // Six escalating stages (longing 0..100). Each is the longing at which that stage BEGINS.
    public static final ModConfigSpec.DoubleValue SIREN_STAGE1 = BUILDER
            .comment("Siren's Call stage 1 — UNEASE: faint bubbles and a rare water drip, no penalty yet.")
            .defineInRange("sirenStage1Threshold", 12.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE2 = BUILDER
            .comment("Siren's Call stage 2 — YEARNING: Mining Fatigue I + the occasional yearning cue.")
            .defineInRange("sirenStage2Threshold", 28.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE3 = BUILDER
            .comment("Siren's Call stage 3 — RESTLESSNESS: Mining Fatigue II, a more frequent cue, Nausea flickers.")
            .defineInRange("sirenStage3Threshold", 45.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE4 = BUILDER
            .comment("Siren's Call stage 4 — HEAVINESS: Slowness I on land.")
            .defineInRange("sirenStage4Threshold", 60.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE5 = BUILDER
            .comment("Siren's Call stage 5 — THE SEA'S GRIP: Slowness II on land + intermittent pull-bursts",
                    "toward water (the sea testing its hold; still resistible between bursts).")
            .defineInRange("sirenStage5Threshold", 78.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_STAGE6 = BUILDER
            .comment("Siren's Call stage 6 — THE MARCH: continuous movement hijack to the water, magenta shader.")
            .defineInRange("sirenStage6Threshold", 92.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SIREN_SHADER_MAX_ALPHA = BUILDER
            .comment("Siren's Call: peak opacity of the magenta mind-control overlay, 0..1. A tint, not a wall.")
            .defineInRange("sirenShaderMaxAlpha", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SIREN_WATER_SEARCH_RADIUS = BUILDER
            .comment("Siren's Call: how far to look for water to be dragged toward, in blocks.")
            .defineInRange("sirenWaterSearchRadius", 24, 1, 64);

    public static final ModConfigSpec.DoubleValue SIREN_PULL_FORCE = BUILDER
            .comment("Siren's Call: a small server-side velocity tug toward the water on top of the hijacked",
                    "walk, so even mid-air or on ice you drift the right way.")
            .defineInRange("sirenPullForce", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SIREN_DROWNED_SOOTHE_RADIUS = BUILDER
            .comment("Siren's Call: how close a Drowned must be to soothe you, in blocks. They protect their",
                    "own — nearby Drowned slow the longing and won't turn on the victim.")
            .defineInRange("sirenDrownedSootheRadius", 12.0, 0.0, 48.0);

    public static final ModConfigSpec.DoubleValue SIREN_DROWNED_GAIN_MULT = BUILDER
            .comment("Siren's Call: longing-gain multiplier while a Drowned is watching over you. Below 1.")
            .defineInRange("sirenDrownedGainMultiplier", 0.25, 0.0, 1.0);

    // --- Basement Dweller ------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue BASEMENT_DAMAGE_INTERVAL = BUILDER
            .comment("Basement Dweller: ticks between burns while stood in the open sun. 12 = 0.6s.")
            .defineInRange("basementDamageIntervalTicks", 12, 1, 200);

    public static final ModConfigSpec.DoubleValue BASEMENT_DAMAGE = BUILDER
            .comment("Basement Dweller: damage per burn in direct daylight, in half-hearts.")
            .defineInRange("basementDamage", 1.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue BASEMENT_HELMET_INTERVAL_MULT = BUILDER
            .comment("Basement Dweller: a hat SLOWS the burns rather than softening them — the interval is",
                    "multiplied by this while your head slot is occupied. Above 1 (2.5 = burns 2.5x further",
                    "apart), but never infinite: you still cook, just slower.")
            .defineInRange("basementHelmetIntervalMultiplier", 2.5, 1.0, 20.0);

    // --- Claustrophobia --------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue CLAUSTRO_DAMAGE_INTERVAL = BUILDER
            .comment("Claustrophobia: ticks between the dread biting while shut indoors. 16 = 0.8s.")
            .defineInRange("claustrophobiaDamageIntervalTicks", 16, 1, 200);

    public static final ModConfigSpec.DoubleValue CLAUSTRO_DAMAGE = BUILDER
            .comment("Claustrophobia: damage per tick while indoors, in half-hearts. Milder than Basement",
                    "Dweller's sun by design.")
            .defineInRange("claustrophobiaDamage", 0.5, 0.0, 40.0);

    // --- Stick Drift -----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue STICKDRIFT_CAMERA_CHANCE = BUILDER
            .comment("Stick Drift: percent chance the rolled drift is a CAMERA drift rather than a MOVEMENT",
                    "one. Decided once when the curse lands and fixed thereafter.")
            .defineInRange("stickDriftCameraChancePercent", 50, 0, 100);

    public static final ModConfigSpec.IntValue STICKDRIFT_GAP_MIN = BUILDER
            .comment("Stick Drift: shortest calm gap between drift episodes, in ticks.")
            .defineInRange("stickDriftGapMinTicks", 40, 0, 6000);

    public static final ModConfigSpec.IntValue STICKDRIFT_GAP_MAX = BUILDER
            .comment("Stick Drift: longest calm gap between drift episodes, in ticks.")
            .defineInRange("stickDriftGapMaxTicks", 200, 0, 6000);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_INTENSITY_MIN = BUILDER
            .comment("Stick Drift: weakest episode intensity, 0..1.")
            .defineInRange("stickDriftIntensityMin", 0.2, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_INTENSITY_MAX = BUILDER
            .comment("Stick Drift: strongest episode intensity, 0..1.")
            .defineInRange("stickDriftIntensityMax", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_DURATION_PRODUCT = BUILDER
            .comment("Stick Drift: intensity × duration is held roughly constant at this many tick-units, so a",
                    "stronger drift lasts a shorter time and vice versa (the controller-drift joke). Episode",
                    "length = this ÷ intensity, clamped to the bounds below.")
            .defineInRange("stickDriftDurationProduct", 60.0, 1.0, 6000.0);

    public static final ModConfigSpec.IntValue STICKDRIFT_DURATION_MIN = BUILDER
            .comment("Stick Drift: shortest an episode can last regardless of intensity, in ticks.")
            .defineInRange("stickDriftDurationMinTicks", 20, 1, 6000);

    public static final ModConfigSpec.IntValue STICKDRIFT_DURATION_MAX = BUILDER
            .comment("Stick Drift: longest an episode can last regardless of intensity, in ticks.")
            .defineInRange("stickDriftDurationMaxTicks", 300, 1, 6000);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_MOVE_SCALE = BUILDER
            .comment("Stick Drift: movement drift at full intensity, as a fraction of full stick input.")
            .defineInRange("stickDriftMoveScale", 0.7, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue STICKDRIFT_CAMERA_SCALE = BUILDER
            .comment("Stick Drift: camera drift at full intensity, in degrees per tick.")
            .defineInRange("stickDriftCameraScale", 2.0, 0.0, 20.0);

    // --- Wonky -----------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue WONKY_DRIFT_STRENGTH = BUILDER
            .comment("Wonky: how hard your movement wanders sideways while walking, as a fraction of full",
                    "strafe. Kept small — it should feel like you can't quite hold a line, not like being",
                    "shoved.")
            .defineInRange("wonkyDriftStrength", 0.18, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue WONKY_SPRINT_MULTIPLIER = BUILDER
            .comment("Wonky: how much the sideways wander is amplified while sprinting — you commit harder, so",
                    "the wobble is worse.")
            .defineInRange("wonkySprintMultiplier", 2.2, 1.0, 6.0);

    public static final ModConfigSpec.DoubleValue WONKY_PERIOD_TICKS = BUILDER
            .comment("Wonky: how many ticks one full left-right wander cycle takes. Longer = a lazier weave",
                    "that's harder to consciously correct for; shorter = a jitterier stagger.")
            .defineInRange("wonkyPeriodTicks", 34.0, 4.0, 200.0);

    // --- Flat Footed -----------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue FLATFOOT_STEP_DISTANCE = BUILDER
            .comment("Flat Footed: blocks of travel between amplified footfalls. Lower = more frequent stomps.")
            .defineInRange("flatFootedStepDistance", 1.8, 0.5, 8.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_VOLUME = BUILDER
            .comment("Flat Footed: volume of the amplified footstep. Vanilla steps are ~0.15, so this is",
                    "cartoonishly loud — that's the joke, and it's what makes you trackable.")
            .defineInRange("flatFootedVolume", 3.0, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_SNEAK_VOLUME_MULT = BUILDER
            .comment("Flat Footed: multiplier applied while sneaking. Below 1 so tiptoeing is a bit quieter —",
                    "but never silent, so sneaking away still gives you away.")
            .defineInRange("flatFootedSneakVolumeMultiplier", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_SHAKE_RADIUS = BUILDER
            .comment("Flat Footed: how close another player must be to feel the footstep camera-shudder.")
            .defineInRange("flatFootedShakeRadius", 8.0, 0.0, 48.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_SHAKE_STRENGTH = BUILDER
            .comment("Flat Footed: peak footstep camera-shudder amplitude in degrees. Slight — a nudge, not the",
                    "Heavyweight jolt.")
            .defineInRange("flatFootedShakeStrength", 0.6, 0.0, 10.0);

    public static final ModConfigSpec.IntValue FLATFOOT_SHAKE_TICKS = BUILDER
            .comment("Flat Footed: how long each footstep shudder lasts, in ticks.")
            .defineInRange("flatFootedShakeTicks", 5, 1, 100);

    public static final ModConfigSpec.DoubleValue FLATFOOT_DETECTION_BONUS = BUILDER
            .comment("Flat Footed: extra blocks of FOLLOW_RANGE handed to nearby hostile mobs, so your racket",
                    "reaches their ears from further off. Slight on purpose.")
            .defineInRange("flatFootedDetectionBonus", 8.0, 0.0, 48.0);

    public static final ModConfigSpec.DoubleValue FLATFOOT_DETECTION_RADIUS = BUILDER
            .comment("Flat Footed: how far out hostile mobs get that detection bonus applied, in blocks.")
            .defineInRange("flatFootedDetectionRadius", 24.0, 1.0, 64.0);

    // --- Broken Bonds ----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue BROKEN_BONDS_CHECK_INTERVAL = BUILDER
            .comment("Broken Bonds: ticks between hate-meter updates on nearby owned pets.")
            .defineInRange("brokenBondsCheckIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_RADIUS = BUILDER
            .comment("Broken Bonds: how close one of your pets must be for its resentment to build, in blocks.",
                    "Beyond this its meter cools off instead.")
            .defineInRange("brokenBondsRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_LIMIT = BUILDER
            .comment("Broken Bonds: how much hate a pet must accumulate before it snaps and untames.")
            .defineInRange("brokenBondsLimit", 100.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_PROXIMITY_GAIN = BUILDER
            .comment("Broken Bonds: hate per check while a pet is RIGHT next to you (it scales down with",
                    "distance, so being across the radius barely registers). Deliberately slow.")
            .defineInRange("brokenBondsProximityGain", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_FOLLOW_GAIN = BUILDER
            .comment("Broken Bonds: extra hate per check while a pet is actively trailing you around (standing,",
                    "not sitting) — spending time in your company wears on it.")
            .defineInRange("brokenBondsFollowGain", 1.5, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_RIDE_GAIN = BUILDER
            .comment("Broken Bonds: extra hate per check while you are RIDING the pet. Being sat on is the",
                    "fastest way to lose a friend.")
            .defineInRange("brokenBondsRideGain", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_DECAY = BUILDER
            .comment("Broken Bonds: hate lost per check when a pet is out of range or otherwise not building.",
                    "Similar to the proximity rate, so leaving it alone genuinely calms it down.")
            .defineInRange("brokenBondsDecay", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_RANDOMNESS = BUILDER
            .comment("Broken Bonds: +/- fraction of jitter on each hate change, so the exact moment a pet",
                    "snaps is never perfectly predictable. 0.3 = up to 30% either way.")
            .defineInRange("brokenBondsRandomness", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.IntValue BROKEN_BONDS_FLEE_TICKS = BUILDER
            .comment("Broken Bonds: how long a freshly-untamed pet's legs are hijacked to storm off. 200 = 10s.")
            .defineInRange("brokenBondsFleeTicks", 200, 20, 2400);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_FLEE_DISTANCE = BUILDER
            .comment("Broken Bonds: how far ahead it aims each leg of that escape, in blocks.")
            .defineInRange("brokenBondsFleeDistance", 12.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue BROKEN_BONDS_FLEE_SPEED = BUILDER
            .comment("Broken Bonds: movement speed multiplier while storming off.")
            .defineInRange("brokenBondsFleeSpeed", 1.3, 0.1, 5.0);

    // --- Oversharer ------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue OVERSHARER_INTERVAL_MIN = BUILDER
            .comment("Oversharer: shortest gap between leaks, in ticks. 1800 = 1.5min.")
            .defineInRange("oversharerIntervalMinTicks", 1800, 100, 72000);

    public static final ModConfigSpec.IntValue OVERSHARER_INTERVAL_MAX = BUILDER
            .comment("Oversharer: longest gap between leaks, in ticks. 6000 = 5min.")
            .defineInRange("oversharerIntervalMaxTicks", 6000, 100, 72000);

    // --- Clumsy ----------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue CLUMSY_BASE_CHANCE = BUILDER
            .comment("Clumsy: percent chance the very next block you place goes wrong, from a clean slate.")
            .defineInRange("clumsyBaseChancePercent", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue CLUMSY_PER_BLOCK_CHANCE = BUILDER
            .comment("Clumsy: extra percent added for each block placed without a slip. The chance ramps up",
                    "the longer you go clean, then resets the moment something goes wrong.")
            .defineInRange("clumsyPerBlockChancePercent", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue CLUMSY_MAX_CHANCE = BUILDER
            .comment("Clumsy: the ceiling that ramp climbs to, in percent.")
            .defineInRange("clumsyMaxChancePercent", 31.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue CLUMSY_ORIENTAL_ORIENTATION = BUILDER
            .comment("Clumsy: for a block WITH an orientation (stairs, doors, logs...), percent of slips that",
                    "come out facing the wrong way. This plus the next two should total 100.")
            .defineInRange("clumsyOrientalOrientationPercent", 60, 0, 100);

    public static final ModConfigSpec.IntValue CLUMSY_ORIENTAL_LOCATION = BUILDER
            .comment("Clumsy: for an oriented block, percent of slips that land in the wrong spot.")
            .defineInRange("clumsyOrientalLocationPercent", 32, 0, 100);

    public static final ModConfigSpec.IntValue CLUMSY_ORIENTAL_WRONG_BLOCK = BUILDER
            .comment("Clumsy: for an oriented block, percent of slips that place a DIFFERENT hotbar block.",
                    "Falls back to wrong-location if you have nothing else placeable to hand.")
            .defineInRange("clumsyOrientalWrongBlockPercent", 8, 0, 100);

    public static final ModConfigSpec.IntValue CLUMSY_PLAIN_LOCATION = BUILDER
            .comment("Clumsy: for a plain block with no orientation, percent of slips that land in the wrong",
                    "spot. This plus the next should total 100.")
            .defineInRange("clumsyPlainLocationPercent", 65, 0, 100);

    public static final ModConfigSpec.IntValue CLUMSY_PLAIN_WRONG_BLOCK = BUILDER
            .comment("Clumsy: for a plain block, percent of slips that place a DIFFERENT hotbar block. Falls",
                    "back to wrong-location if nothing else is placeable.")
            .defineInRange("clumsyPlainWrongBlockPercent", 35, 0, 100);

    // --- Taxes -----------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue TAXES_CHECK_INTERVAL = BUILDER
            .comment("Taxes: ticks between checks for whether you've been careless enough to be worth a visit.")
            .defineInRange("taxesCheckIntervalTicks", 100, 20, 12000);

    public static final ModConfigSpec.IntValue TAXES_SCAN_RADIUS = BUILDER
            .comment("Taxes: how far the Tax Man reaches for chests, floor items and your person, in blocks.")
            .defineInRange("taxesScanRadius", 12, 1, 48);

    public static final ModConfigSpec.IntValue TAXES_MIN_VALUE_TRIGGER = BUILDER
            .comment("Taxes: how much VALUE must be within reach before a visit is worth his time (see",
                    "taxesItemValues). This is the counterplay made concrete — stay under it and he never",
                    "comes.")
            .defineInRange("taxesMinValueTrigger", 24, 1, 100000);

    public static final ModConfigSpec.IntValue TAXES_HAUL_CAP = BUILDER
            .comment("Taxes: how much VALUE he takes in one visit before declaring himself satisfied. Weighted",
                    "rather than counted, so he can't strip a stack of netherite the way he would a stack of",
                    "copper. Kept low on purpose: this should be an irritation, not a robbery.")
            .defineInRange("taxesHaulValueCap", 40, 1, 100000);

    public static final ModConfigSpec.IntValue TAXES_DEFAULT_ITEM_VALUE = BUILDER
            .comment("Taxes: value of anything in the witchmod:valuables tag that isn't listed in",
                    "taxesItemValues — including modded ores, which is why this exists.")
            .defineInRange("taxesDefaultItemValue", 1, 0, 10000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAXES_ITEM_VALUES = BUILDER
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

    public static final ModConfigSpec.IntValue TAXES_BANK_CAPACITY = BUILDER
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

    public static final ModConfigSpec.IntValue TAXES_BANK_WARN_AT = BUILDER
            .comment("Taxes: percentage full at which commands start warning that the bank is filling up.")
            .defineInRange("taxesBankWarnAtPercent", 80, 1, 100);

    public static final ModConfigSpec.IntValue TAXES_COOLDOWN = BUILDER
            .comment("Taxes: ticks before he may return after a visit. 6000 = 5min. He does come back — the",
                    "curse isn't spent by one audit.")
            .defineInRange("taxesCooldownTicks", 6000, 100, 72000);

    public static final ModConfigSpec.IntValue TAXES_COLLECT_INTERVAL = BUILDER
            .comment("Audit: ticks between individual seizures. Item-by-item so you can watch it happen and",
                    "swear at him, but brisk (7 = ~0.35s) so a full chest doesn't take an age.")
            .defineInRange("taxesCollectIntervalTicks", 7, 1, 200);

    public static final ModConfigSpec.IntValue TAXES_ARRIVE_TICKS = BUILDER
            .comment("Audit: how long he stands there ominously before starting work. 20 = 1s.")
            .defineInRange("taxesArriveTicks", 20, 0, 600);

    public static final ModConfigSpec.IntValue TAXES_LEAVE_TICKS = BUILDER
            .comment("Audit: how long he lingers after finishing before vanishing. 25 = ~1.25s.")
            .defineInRange("taxesLeaveTicks", 25, 0, 600);

    public static final ModConfigSpec.IntValue TAXES_GIVE_UP_SWEEPS = BUILDER
            .comment("Audit: how many fruitless sweeps before he gives up and leaves.")
            .defineInRange("taxesGiveUpSweeps", 8, 1, 100);

    public static final ModConfigSpec.IntValue TAXES_ENDER_CHEST_AFTER = BUILDER
            .comment("Audit: fruitless sweeps before he places his OWN ender chest to go through yours — the",
                    "answer to hiding everything in the one container he can't otherwise reach.")
            .defineInRange("taxesEnderChestAfterSweeps", 2, 1, 100);

    public static final ModConfigSpec.DoubleValue TAXES_CHAT_RADIUS = BUILDER
            .comment("Taxes: how far away other players can hear him narrating, in blocks.")
            .defineInRange("taxesChatRadius", 24.0, 0.0, 128.0);

    // --- Comic Relief ----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue COMIC_CHECK_INTERVAL = BUILDER
            .comment("Comic Relief: ticks between rolls. 100 = every 5s.")
            .defineInRange("comicReliefCheckIntervalTicks", 100, 5, 2400);

    public static final ModConfigSpec.DoubleValue COMIC_LOW_HEALTH = BUILDER
            .comment("Comic Relief: at or below this health, in half-hearts, the sky starts taking an",
                    "interest. 6.0 = three hearts.")
            .defineInRange("comicReliefLowHealthThreshold", 6.0, 1.0, 40.0);

    public static final ModConfigSpec.DoubleValue COMIC_STRIKE_CHANCE = BUILDER
            .comment("Comic Relief: percent chance per check of a killing bolt while you're low. Small on",
                    "purpose — the joke only lands if it's genuinely unexpected.")
            .defineInRange("comicReliefStrikeChancePercent", 9.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_STRIKE_CHANCE = BUILDER
            .comment("Comic Relief: percent chance per check of a bolt landing on a pile of your dropped",
                    "items instead, destroying them.")
            .defineInRange("comicReliefItemStrikeChancePercent", 12.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_PILE_SCALING = BUILDER
            .comment("Comic Relief: extra percent added to the item-strike chance for each item beyond the",
                    "minimum pile size. A big pile of your worldly goods lying in a field is exactly the shot",
                    "the joke wants, so the bigger it is the likelier the sky is to take it.")
            .defineInRange("comicReliefItemPileScalingPercent", 2.5, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_CHANCE_CAP = BUILDER
            .comment("Comic Relief: ceiling for that scaled item-strike chance, per check.")
            .defineInRange("comicReliefItemChanceCapPercent", 65.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue COMIC_ITEM_PILE_MIN = BUILDER
            .comment("Comic Relief: how many item entities nearby count as a pile worth striking.")
            .defineInRange("comicReliefItemPileMin", 5, 1, 200);

    public static final ModConfigSpec.DoubleValue COMIC_ITEM_SCAN_RADIUS = BUILDER
            .comment("Comic Relief: how far to look for that pile, in blocks.")
            .defineInRange("comicReliefItemScanRadius", 8.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue COMIC_POSTHUMOUS_CHANCE = BUILDER
            .comment("Comic Relief: percent chance that dying earns you one more bolt on the spot a moment",
                    "later — which torches the drops you left behind. Deliberately rare, and deliberately",
                    "kicking you while you're down; that IS the joke.")
            .defineInRange("comicReliefPosthumousChancePercent", 8.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue COMIC_POSTHUMOUS_DELAY = BUILDER
            .comment("Comic Relief: ticks after death before that parting bolt lands. Long enough that the",
                    "death screen is already up, which is what sells it.")
            .defineInRange("comicReliefPosthumousDelayTicks", 40, 1, 600);

    public static final ModConfigSpec.DoubleValue COMIC_THUNDER_MULTIPLIER = BUILDER
            .comment("Comic Relief: all of the above chances are multiplied by this while it's thundering.")
            .defineInRange("comicReliefThunderMultiplier", 2.5, 1.0, 20.0);

    // --- Thirst Meter ----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue THIRST_MAX = BUILDER
            .comment("Thirst Meter: bar size, in points. 20 = ten droplets, matching hunger.")
            .defineInRange("thirstMax", 20, 2, 40);

    public static final ModConfigSpec.DoubleValue THIRST_IDLE_DRAIN = BUILDER
            .comment("Thirst Meter: thirst-exhaustion added per tick while doing nothing. At the default",
                    "exhaustion-per-point of 4.0, 0.01 works out to one droplet per 20s of standing still.")
            .defineInRange("thirstIdleDrainPerTick", 0.01, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THIRST_WALK_DRAIN = BUILDER
            .comment("Thirst Meter: extra thirst-exhaustion per tick while walking.")
            .defineInRange("thirstWalkDrainPerTick", 0.012, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THIRST_SPRINT_DRAIN = BUILDER
            .comment("Thirst Meter: extra thirst-exhaustion per tick while sprinting or swimming. This is the",
                    "point of the curse — it is meant to stop a target being very active.")
            .defineInRange("thirstSprintDrainPerTick", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue THIRST_ACTION_DRAIN = BUILDER
            .comment("Thirst Meter: thirst-exhaustion per strenuous action — mining a block, landing a hit.")
            .defineInRange("thirstActionDrain", 0.08, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue THIRST_EXHAUSTION_PER_POINT = BUILDER
            .comment("Thirst Meter: how much accumulated thirst-exhaustion costs one point off the bar.",
                    "Mirrors vanilla's hunger model, where 4.0 exhaustion costs a haunch.")
            .defineInRange("thirstExhaustionPerPoint", 4.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue THIRST_DEHYDRATION_MULT = BUILDER
            .comment("Thirst Meter: drain multiplier while the Dehydration effect is on you. 4.0 takes the",
                    "idle rate from one droplet per 20s down to one per 5s — hot biomes apply Dehydration, so",
                    "this is what sets the pace out in a desert.")
            .defineInRange("thirstDehydrationMultiplier", 4.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue THIRST_SATURATION_PER_POINT = BUILDER
            .comment("Thirst Meter: how much hidden saturation each restored point also grants. Saturation is",
                    "spent BEFORE the visible bar, exactly like hunger — it's the grace period that stops a",
                    "freshly-filled bar from starting to tick down the instant you finish drinking.")
            .defineInRange("thirstSaturationPerPoint", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.IntValue THIRST_FULL_BONUS_HEAL_INTERVAL = BUILDER
            .comment("Thirst Meter: ticks between bonus heals while BOTH hunger and thirst are completely",
                    "full. This is the reward for keeping on top of it, stacked on vanilla's own regen.")
            .defineInRange("thirstFullBonusHealIntervalTicks", 60, 5, 2400);

    public static final ModConfigSpec.DoubleValue THIRST_FULL_BONUS_HEAL = BUILDER
            .comment("Thirst Meter: health restored per bonus heal, in half-hearts.")
            .defineInRange("thirstFullBonusHeal", 1.0, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue THIRST_HOT_BIOME_TEMPERATURE = BUILDER
            .comment("Thirst Meter: biome base temperature at or above which you count as being somewhere hot",
                    "and start dehydrating. 1.0 catches desert, badlands, savanna and the Nether; jungle sits",
                    "just under at 0.95.")
            .defineInRange("thirstHotBiomeTemperature", 1.0, -1.0, 3.0);

    public static final ModConfigSpec.IntValue THIRST_ACTIVITY_DEHYDRATION_THRESHOLD = BUILDER
            .comment("Thirst Meter: how many points of the bar you must burn through while it's already below",
                    "half before sustained activity brings on Dehydration by itself.")
            .defineInRange("thirstActivityDehydrationThreshold", 4, 1, 40);

    public static final ModConfigSpec.IntValue THIRST_DEHYDRATION_DURATION = BUILDER
            .comment("Thirst Meter: how long Dehydration lasts once applied, in ticks. Refreshed while you",
                    "remain in a hot biome.")
            .defineInRange("thirstDehydrationDurationTicks", 400, 20, 24000);

    public static final ModConfigSpec.IntValue THIRST_SPRINT_CUTOFF = BUILDER
            .comment("Thirst Meter: at or below this many points you can no longer sprint.")
            .defineInRange("thirstSprintCutoff", 2, 0, 40);

    public static final ModConfigSpec.DoubleValue THIRST_EMPTY_DAMAGE = BUILDER
            .comment("Thirst Meter: damage per interval at an empty bar. Uses the mod's own dehydration damage",
                    "type, which is fatal on EVERY difficulty and bypasses armour.")
            .defineInRange("thirstEmptyDamage", 1.0, 0.0, 40.0);

    public static final ModConfigSpec.IntValue THIRST_EMPTY_DAMAGE_INTERVAL = BUILDER
            .comment("Thirst Meter: ticks between those damage ticks. 40 = 2s.")
            .defineInRange("thirstEmptyDamageIntervalTicks", 40, 5, 600);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_WATER_BOTTLE = BUILDER
            .comment("Thirst Meter: points restored by a water bottle.")
            .defineInRange("thirstRestoreWaterBottle", 6, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_POTION = BUILDER
            .comment("Thirst Meter: points restored by any other potion.")
            .defineInRange("thirstRestorePotion", 3, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_RAW_WATER = BUILDER
            .comment("Thirst Meter: points restored by drinking straight from a water source.")
            .defineInRange("thirstRestoreRawWater", 5, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RAW_WATER_RISK = BUILDER
            .comment("Thirst Meter: percent chance drinking untreated water makes you ill — weak Poison, or",
                    "Dehydration, which is the crueller of the two.")
            .defineInRange("thirstRawWaterRiskPercent", 45, 0, 100);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_RAW_FOOD = BUILDER
            .comment("Thirst Meter: points restored by raw food (uncooked meat and fish).")
            .defineInRange("thirstRestoreRawFood", 2, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_NATURAL_FOOD = BUILDER
            .comment("Thirst Meter: points restored by natural food — the water-rich plant stuff (melon,",
                    "apples, berries, carrots). Uses the same category split as the Allergic curse.")
            .defineInRange("thirstRestoreNaturalFood", 4, 0, 40);

    public static final ModConfigSpec.IntValue THIRST_RESTORE_OTHER_FOOD = BUILDER
            .comment("Thirst Meter: points restored by anything else edible — cooked meat, bread, stews. The",
                    "spec only named raw and natural, so this is the judgement call: dry, processed food is",
                    "worth a little but nothing like fruit. Set to 0 if it should be worth nothing at all.")
            .defineInRange("thirstRestoreOtherFood", 1, 0, 40);

    // --- Pests -----------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue PESTS_CHANCE = BUILDER
            .comment("Pests: percent chance per block mined that silverfish come out of it. Mining is a very",
                    "high-frequency action, so this is much lower than it looks — at 8% a normal tunnelling",
                    "session still produces a steady trickle.")
            .defineInRange("pestsChancePercent", 8, 0, 100);

    public static final ModConfigSpec.IntValue PESTS_MIN_PER_TRIGGER = BUILDER
            .comment("Pests: fewest silverfish per trigger.")
            .defineInRange("pestsMinPerTrigger", 1, 1, 16);

    public static final ModConfigSpec.IntValue PESTS_MAX_PER_TRIGGER = BUILDER
            .comment("Pests: most silverfish per trigger.")
            .defineInRange("pestsMaxPerTrigger", 3, 1, 16);

    public static final ModConfigSpec.IntValue PESTS_MAX_NEARBY = BUILDER
            .comment("Pests: skip spawning if this many silverfish are already near the victim. A necessary",
                    "guard rather than balance — silverfish CALL MORE SILVERFISH out of stone when hit, so",
                    "without a ceiling a mining session can snowball into an unrecoverable swarm.")
            .defineInRange("pestsMaxNearby", 12, 1, 64);

    public static final ModConfigSpec.DoubleValue PESTS_NEARBY_RADIUS = BUILDER
            .comment("Pests: radius the nearby-silverfish ceiling is measured over, in blocks.")
            .defineInRange("pestsNearbyRadius", 16.0, 1.0, 64.0);

    // --- Heavyweight -----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue HEAVYWEIGHT_BASE_BREAK_TICKS = BUILDER
            .comment("Heavyweight: flat ticks added to every break, before hardness is considered. Keeps even",
                    "the softest block from vanishing the instant you step on it.")
            .defineInRange("heavyweightBaseBreakTicks", 10, 0, 2400);

    public static final ModConfigSpec.DoubleValue HEAVYWEIGHT_HARDNESS_MULT = BUILDER
            .comment("Heavyweight: ticks added per point of block hardness. Roughly: leaves 0.8s, dirt 1.1s,",
                    "stone 2.4s, planks 3.0s, iron block 6.8s, obsidian ~60s. Obsidian stays a real deterrent",
                    "without being literally impossible, which a higher multiplier made it.")
            .defineInRange("heavyweightHardnessMultiplier", 25.0, 0.0, 600.0);

    public static final ModConfigSpec.DoubleValue HEAVYWEIGHT_WARN_FRACTION = BUILDER
            .comment("Heavyweight: how far through the break the audible creaking starts, 0-1. Before this",
                    "point the warning is purely visual.")
            .defineInRange("heavyweightWarnFraction", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HEAVYWEIGHT_COLLAPSE_RADIUS = BUILDER
            .comment("Heavyweight: how far the collapse spreads to neighbouring blocks, in blocks. The floor",
                    "giving way should take a chunk of itself with it, not punch one neat hole.")
            .defineInRange("heavyweightCollapseRadius", 1, 0, 4);

    public static final ModConfigSpec.IntValue HEAVYWEIGHT_COLLAPSE_CHANCE = BUILDER
            .comment("Heavyweight: percent chance each neighbouring block in range goes too. Below 100 so the",
                    "hole is ragged rather than a perfect square.")
            .defineInRange("heavyweightCollapseChancePercent", 60, 0, 100);

    public static final ModConfigSpec.IntValue HEAVYWEIGHT_SHAKE_TICKS = BUILDER
            .comment("Heavyweight: how long the camera rattles after a collapse. 12 = 0.6s.")
            .defineInRange("heavyweightShakeTicks", 12, 0, 200);

    public static final ModConfigSpec.DoubleValue HEAVYWEIGHT_SHAKE_STRENGTH = BUILDER
            .comment("Heavyweight: peak camera-shake amplitude in degrees. Decays to nothing over the window.")
            .defineInRange("heavyweightShakeStrength", 3.5, 0.0, 30.0);

    // --- Floor Is Lava ---------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue FIL_GRACE_TICKS = BUILDER
            .comment("Floor Is Lava: how long you may stand still before it starts hurting. 100 = 5s — long",
                    "enough to craft, read a sign or check a chest, short enough that you can never settle.")
            .defineInRange("floorIsLavaGraceTicks", 100, 0, 2400);

    public static final ModConfigSpec.IntValue FIL_DAMAGE_INTERVAL = BUILDER
            .comment("Floor Is Lava: ticks between burns once the grace period has run out. 20 = 1s.")
            .defineInRange("floorIsLavaDamageIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.DoubleValue FIL_BASE_DAMAGE = BUILDER
            .comment("Floor Is Lava: damage on the FIRST burn, in half-hearts.")
            .defineInRange("floorIsLavaBaseDamage", 1.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue FIL_RAMP_PER_BURN = BUILDER
            .comment("Floor Is Lava: extra damage added per consecutive burn. The ramp is the whole point —",
                    "standing still has to get worse the longer you do it, or it's just a slow tax.")
            .defineInRange("floorIsLavaRampPerBurn", 0.5, 0.0, 20.0);

    public static final ModConfigSpec.DoubleValue FIL_MAX_DAMAGE = BUILDER
            .comment("Floor Is Lava: the cap the ramp climbs to, in half-hearts per burn. Without a cap an",
                    "AFK player is simply executed, which isn't a joke, it's a disconnect.")
            .defineInRange("floorIsLavaMaxDamage", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue FIL_MOVEMENT_RESET_DISTANCE = BUILDER
            .comment("Floor Is Lava: how far you must move to be counted as moving and reset the timer. Small,",
                    "so shuffling on the spot genuinely counts — but not zero, or camera-only movement and",
                    "sub-block jitter would keep you safe forever.")
            .defineInRange("floorIsLavaMovementResetDistance", 0.5, 0.05, 8.0);

    // --- Social Outcast --------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue OUTCAST_REVEAL_DISTANCE = BUILDER
            .comment("Social Outcast: how close someone must get before you can see them at all, in blocks.",
                    "Small on purpose — they should be able to stand right next to you before appearing.")
            .defineInRange("outcastRevealDistance", 4.0, 0.5, 32.0);

    public static final ModConfigSpec.IntValue OUTCAST_DAMAGE_REVEAL_TICKS = BUILDER
            .comment("Social Outcast: how long someone stays visible after hitting you. 400 = 20s. Once it",
                    "lapses without another hit they vanish again, so a fight keeps them on screen but a",
                    "single ambush doesn't reveal them permanently.")
            .defineInRange("outcastDamageRevealTicks", 400, 20, 12000);

    public static final ModConfigSpec.BooleanValue OUTCAST_HIDES_VILLAGERS = BUILDER
            .comment("Social Outcast: whether villagers are hidden too, or only players.")
            .define("outcastHidesVillagers", true);

    // --- Minor Inconvenience ---------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue MINOR_RENAME_MIN = BUILDER
            .comment("Minor Inconvenience: shortest gap between window renames. 600 = 30s.")
            .defineInRange("minorInconvenienceRenameMinTicks", 600, 20, 24000);

    public static final ModConfigSpec.IntValue MINOR_RENAME_MAX = BUILDER
            .comment("Minor Inconvenience: longest gap between window renames. 2400 = 2min.")
            .defineInRange("minorInconvenienceRenameMaxTicks", 2400, 20, 24000);

    // --- Screensaver -----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SCREENSAVER_EPISODE_MIN = BUILDER
            .comment("Screensaver: shortest bouncing episode, in ticks. 400 = 20s.")
            .defineInRange("screensaverEpisodeMinTicks", 400, 20, 24000);

    public static final ModConfigSpec.IntValue SCREENSAVER_EPISODE_MAX = BUILDER
            .comment("Screensaver: longest bouncing episode, in ticks. 900 = 45s.")
            .defineInRange("screensaverEpisodeMaxTicks", 900, 20, 24000);

    public static final ModConfigSpec.IntValue SCREENSAVER_DUTY_PERCENT = BUILDER
            .comment("Screensaver: roughly what percentage of the time is spent BOUNCING, excluding the",
                    "transitions in and out. The idle gap is derived from this and the episode length, so",
                    "changing episode length keeps the same overall rhythm automatically.")
            .defineInRange("screensaverDutyPercent", 20, 1, 100);

    public static final ModConfigSpec.IntValue SCREENSAVER_TRANSITION_TICKS = BUILDER
            .comment("Screensaver: how long the shrink and grow take, in ticks. 80 = 4s each way. Deliberately",
                    "slow and eased — a snap resize is jarring and reads as a crash rather than a joke.")
            .defineInRange("screensaverTransitionTicks", 80, 4, 600);

    public static final ModConfigSpec.IntValue SCREENSAVER_WINDOW_SCALE_PERCENT = BUILDER
            .comment("Screensaver: the shrunk window's size as a percentage of the monitor. Kept LARGE on",
                    "purpose — a tiny window bouncing fast is a motion-sickness generator, and you still have",
                    "to be able to play.")
            .defineInRange("screensaverWindowScalePercent", 55, 10, 95);

    public static final ModConfigSpec.IntValue SCREENSAVER_SPEED_PX = BUILDER
            .comment("Screensaver: bounce speed in pixels per tick. Low by design, for the same reason.")
            .defineInRange("screensaverSpeedPx", 2, 1, 40);

    // --- Dwarfism --------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue DWARFISM_MODEL_SCALE = BUILDER
            .comment("Dwarfism: size multiplier. 0.5 is half height — and because vanilla's SCALE attribute",
                    "drives the bounding box as well as the model, you genuinely fit through 1-block gaps.")
            .defineInRange("dwarfismModelScale", 0.5, 0.1, 1.0);

    public static final ModConfigSpec.DoubleValue DWARFISM_HEALTH_MULT = BUILDER
            .comment("Dwarfism: max-health multiplier. 0.5 = 10 hearts down to 5.")
            .defineInRange("dwarfismHealthMultiplier", 0.5, 0.1, 1.0);

    public static final ModConfigSpec.BooleanValue DWARFISM_RIDE_PLAYERS = BUILDER
            .comment("Dwarfism: whether right-clicking another player lets you ride them.")
            .define("dwarfismCanRidePlayers", true);

    public static final ModConfigSpec.BooleanValue DWARFISM_RIDE_VILLAGERS = BUILDER
            .comment("Dwarfism: whether right-clicking a villager lets you ride them.")
            .define("dwarfismCanRideVillagers", true);

    // --- Neutral Aggression ----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue NEUTRAL_AGGRO_RADIUS = BUILDER
            .comment("Neutral Aggression: how close a neutral mob must be before it turns on you, in blocks.")
            .defineInRange("neutralAggroRadius", 24.0, 1.0, 64.0);

    public static final ModConfigSpec.IntValue NEUTRAL_AGGRO_CHECK_INTERVAL = BUILDER
            .comment("Neutral Aggression: ticks between sweeps for anything neutral nearby.")
            .defineInRange("neutralAggroCheckIntervalTicks", 40, 1, 600);

    public static final ModConfigSpec.BooleanValue NEUTRAL_AGGRO_TURNS_OWN_PETS = BUILDER
            .comment("Neutral Aggression: whether YOUR OWN tamed animals turn on you too. On by default —",
                    "your own dog deciding it hates you is the best thing this curse does.")
            .define("neutralAggroTurnsOwnPets", true);

    // --- Magnet ----------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue MAGNET_RADIUS = BUILDER
            .comment("Magnet: how close a projectile must be before it starts curving toward you, in blocks.")
            .defineInRange("magnetRadius", 16.0, 1.0, 64.0);

    public static final ModConfigSpec.DoubleValue MAGNET_STEER_STRENGTH = BUILDER
            .comment("Magnet: how sharply a projectile is bent toward you each tick, 0-1. This steers the",
                    "DIRECTION only and preserves the projectile's speed — accelerating them instead would",
                    "make arrows hit harder, which isn't the joke.")
            .defineInRange("magnetSteerStrength", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue MAGNET_AFFECTS_OWN = BUILDER
            .comment("Magnet: whether YOUR OWN projectiles curve back at you. Off by design — having your own",
                    "arrows boomerang is just unplayable rather than funny.")
            .define("magnetAffectsOwn", false);

    public static final ModConfigSpec.IntValue MAGNET_MAX_PROJECTILES = BUILDER
            .comment("Magnet: cap on projectiles steered per tick, so an arrow barrage can't cost the server.")
            .defineInRange("magnetMaxProjectiles", 32, 1, 256);

    // --- Heavy -----------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue HEAVY_FALL_SPEED_MULT = BUILDER
            .comment("Heavy: gravity multiplier. Applied to the GRAVITY attribute rather than by shoving the",
                    "player downward, so acceleration, terminal velocity and fall distance all stay vanilla's",
                    "— just heavier. NOTE the attribute is hard-capped at 1.0 by the game.")
            .defineInRange("heavyFallSpeedMultiplier", 1.8, 1.0, 12.0);

    public static final ModConfigSpec.DoubleValue HEAVY_FALL_DAMAGE_MULT = BUILDER
            .comment("Heavy: fall damage multiplier, on top of the extra distance the faster fall racks up.")
            .defineInRange("heavyFallDamageMultiplier", 1.75, 1.0, 10.0);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_MIN_FALL = BUILDER
            .comment("Heavy: how far you must fall, in blocks, before you land hard enough to crater.")
            .defineInRange("heavyCraterMinFall", 8, 1, 256);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_CAP_FALL = BUILDER
            .comment("Heavy: the fall distance at which the crater stops growing — THE CAP. Beyond this a",
                    "longer drop is no more destructive, so a void-height fall can't level a base.")
            .defineInRange("heavyCraterCapFall", 40, 2, 512);

    public static final ModConfigSpec.DoubleValue HEAVY_CRATER_POWER_MIN = BUILDER
            .comment("Heavy: explosion power at exactly the minimum fall distance. (TNT is 4.0.)")
            .defineInRange("heavyCraterPowerMin", 1.5, 0.1, 20.0);

    public static final ModConfigSpec.DoubleValue HEAVY_CRATER_POWER_MAX = BUILDER
            .comment("Heavy: explosion power once the fall reaches the cap.")
            .defineInRange("heavyCraterPowerMax", 4.0, 0.1, 20.0);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_SELF_DAMAGE_PERCENT = BUILDER
            .comment("Heavy: percent of its own crater blast the faller takes. They're at dead centre AND",
                    "have just eaten amplified fall damage, so full blast damage would execute them every",
                    "single time.")
            .defineInRange("heavyCraterSelfDamagePercent", 25, 0, 100);

    public static final ModConfigSpec.DoubleValue HEAVY_JUMP_COMPENSATION = BUILDER
            .comment("Heavy: multiplier on JUMP_STRENGTH, purely so a single block stays climbable. Jump apex",
                    "is roughly v^2/(2*gravity), so heavier gravity alone drops a normal 1.25-block jump to",
                    "about 0.6 — under the one block you need, which makes the curse a nuisance rather than a",
                    "joke. 1.35 puts the apex just over a block: you can still get up a step, but nothing",
                    "about it feels light. Raise if you change heavyFallSpeedMultiplier.")
            .defineInRange("heavyJumpCompensation", 1.35, 1.0, 4.0);

    public static final ModConfigSpec.IntValue HEAVY_CRATER_COOLDOWN = BUILDER
            .comment("Heavy: minimum ticks between craters. Stops the blast chaining — a crater blows the",
                    "ground out from under you, so you fall again, crater again, and keep digging yourself",
                    "downward. 60 = 3s.")
            .defineInRange("heavyCraterCooldownTicks", 60, 0, 2400);

    public static final ModConfigSpec.DoubleValue HEAVY_WATER_PULL = BUILDER
            .comment("Heavy: extra downward pull per tick while in water or lava — you sink like an anchor.",
                    "Needed as its own value because vanilla divides gravity by 16 in fluid, so the GRAVITY",
                    "attribute alone just gives a slightly brisker version of the same gentle bob. Applied",
                    "client-side, since player movement is client-authoritative. For reference, a full swim-up",
                    "tops out around 0.048/tick, so anything at or above that makes surfacing impossible.")
            .defineInRange("heavyWaterPull", 0.06, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HEAVY_CRATER_KNOCKBACK_MULT = BUILDER
            .comment("Heavy: knockback multiplier for the crater blast — the shockwave that throws everything",
                    "standing nearby.")
            .defineInRange("heavyCraterKnockbackMultiplier", 1.6, 0.0, 10.0);

    // --- Gassy -----------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue GASSY_INTERVAL_MIN = BUILDER
            .comment("Gassy: shortest gap between spontaneous farts. 400 = 20s.")
            .defineInRange("gassyIntervalMinTicks", 400, 20, 24000);

    public static final ModConfigSpec.IntValue GASSY_INTERVAL_MAX = BUILDER
            .comment("Gassy: longest gap between spontaneous farts. 1200 = 60s.")
            .defineInRange("gassyIntervalMaxTicks", 1200, 20, 24000);

    public static final ModConfigSpec.DoubleValue GASSY_VELOCITY_MIN = BUILDER
            .comment("Gassy: weakest launch, in blocks/tick.")
            .defineInRange("gassyVelocityMin", 0.45, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue GASSY_VELOCITY_MAX = BUILDER
            .comment("Gassy: strongest launch, in blocks/tick.")
            .defineInRange("gassyVelocityMax", 1.0, 0.0, 5.0);

    public static final ModConfigSpec.IntValue GASSY_BIG_CHANCE = BUILDER
            .comment("Gassy: percent chance a spontaneous fart is a BIG one (different sound, more velocity).")
            .defineInRange("gassyBigFartChancePercent", 8, 0, 100);

    public static final ModConfigSpec.DoubleValue GASSY_BIG_MULTIPLIER = BUILDER
            .comment("Gassy: velocity multiplier for a big fart.")
            .defineInRange("gassyBigFartMultiplier", 1.8, 1.0, 5.0);

    public static final ModConfigSpec.IntValue GASSY_RANDOM_DIRECTION_CHANCE = BUILDER
            .comment("Gassy: percent chance a fart ignores nearby hazards entirely and fires off in a random",
                    "direction. This is what stops the curse being a guaranteed lava delivery service — the",
                    "hazard bias must never be a certainty.")
            .defineInRange("gassyRandomDirectionChancePercent", 35, 0, 100);

    public static final ModConfigSpec.IntValue GASSY_HAZARD_SCAN_RADIUS = BUILDER
            .comment("Gassy: how far to look for something worth being launched into, in blocks.")
            .defineInRange("gassyHazardScanRadius", 8, 1, 32);

    public static final ModConfigSpec.IntValue GASSY_LEDGE_DROP_MIN = BUILDER
            .comment("Gassy: how many clear blocks below a spot make it count as a ledge worth aiming at.")
            .defineInRange("gassyLedgeDropMin", 3, 1, 32);

    public static final ModConfigSpec.IntValue GASSY_STRAIGHT_UP_CHANCE = BUILDER
            .comment("Gassy: percent chance a fart is mostly straight UP rather than off in a direction.")
            .defineInRange("gassyStraightUpChancePercent", 18, 0, 100);

    public static final ModConfigSpec.DoubleValue GASSY_VERTICAL_MIN = BUILDER
            .comment("Gassy: smallest upward kick added to a directional fart, so you get some air either way.",
                    "This is a RATIO against the horizontal, not an absolute speed — the launch vector is",
                    "normalised before the velocity is applied, so raising these tilts farts upward without",
                    "making them stronger.")
            .defineInRange("gassyVerticalMin", 0.45, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue GASSY_VERTICAL_MAX = BUILDER
            .comment("Gassy: largest upward kick added to a directional fart. See gassyVerticalMin — a ratio.")
            .defineInRange("gassyVerticalMax", 0.85, 0.0, 3.0);

    public static final ModConfigSpec.IntValue GASSY_ON_DAMAGE_CHANCE = BUILDER
            .comment("Gassy: percent chance that taking a hit frightens one out of you.")
            .defineInRange("gassyOnDamageChancePercent", 25, 0, 100);

    public static final ModConfigSpec.IntValue GASSY_ON_EXPLOSION_CHANCE = BUILDER
            .comment("Gassy: percent chance a nearby explosion or firework sets one off. Much likelier than a",
                    "plain hit — the whole joke is the sympathetic detonation.")
            .defineInRange("gassyOnExplosionChancePercent", 60, 0, 100);

    public static final ModConfigSpec.IntValue GASSY_EVENT_BIG_CHANCE = BUILDER
            .comment("Gassy: percent chance an event-triggered fart is a BIG one. Deliberately far higher than",
                    "the spontaneous rate.")
            .defineInRange("gassyEventBigFartChancePercent", 55, 0, 100);

    public static final ModConfigSpec.DoubleValue GASSY_EVENT_VELOCITY_MULT = BUILDER
            .comment("Gassy: extra velocity multiplier on event-triggered farts, on top of any big-fart bonus.")
            .defineInRange("gassyEventVelocityMultiplier", 1.5, 0.1, 5.0);

    public static final ModConfigSpec.IntValue GASSY_EVENT_COOLDOWN = BUILDER
            .comment("Gassy: minimum ticks between event-triggered farts, so one firework show doesn't launch",
                    "you forty times.")
            .defineInRange("gassyEventCooldownTicks", 40, 0, 2400);

    public static final ModConfigSpec.DoubleValue GASSY_EXPLOSION_HEAR_RADIUS = BUILDER
            .comment("Gassy: how close an explosion or firework must be to set one off, in blocks.")
            .defineInRange("gassyExplosionHearRadius", 12.0, 1.0, 64.0);

    // --- Delusions -------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue DELUSIONS_MAX_CONCURRENT = BUILDER
            .comment("Delusions: how many fake players may be visible at once. Low on purpose — a crowd of",
                    "them reads as a glitch, one lurking at the treeline reads as a person.")
            .defineInRange("delusionsMaxConcurrent", 2, 1, 8);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_INTERVAL_MIN = BUILDER
            .comment("Delusions: shortest gap between one appearing. 1200 = 60s.")
            .defineInRange("delusionsSpawnIntervalMinTicks", 1200, 40, 24000);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_INTERVAL_MAX = BUILDER
            .comment("Delusions: longest gap between one appearing. 3600 = 3min.")
            .defineInRange("delusionsSpawnIntervalMaxTicks", 3600, 40, 24000);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_RANGE_MIN = BUILDER
            .comment("Delusions: closest one will appear, in blocks. Never right on top of you.")
            .defineInRange("delusionsSpawnRangeMin", 12, 2, 64);

    public static final ModConfigSpec.IntValue DELUSIONS_SPAWN_RANGE_MAX = BUILDER
            .comment("Delusions: furthest one will appear, in blocks.")
            .defineInRange("delusionsSpawnRangeMax", 28, 4, 96);

    public static final ModConfigSpec.IntValue DELUSIONS_LIFETIME_MAX = BUILDER
            .comment("Delusions: longest one sticks around before quietly vanishing. 1200 = 60s.")
            .defineInRange("delusionsLifetimeMaxTicks", 1200, 100, 24000);

    public static final ModConfigSpec.IntValue DELUSIONS_DESPAWN_DISTANCE = BUILDER
            .comment("Delusions: one further away than this is dropped (you wandered off, or it did).")
            .defineInRange("delusionsDespawnDistance", 48, 16, 128);

    public static final ModConfigSpec.IntValue DELUSIONS_STATE_SWAP_INTERVAL = BUILDER
            .comment("Delusions: average ticks a behaviour lasts before it picks another. Each state rolls its",
                    "own length around this, so they don't all switch on the same beat.")
            .defineInRange("delusionsStateSwapInterval", 120, 20, 1200);

    public static final ModConfigSpec.IntValue DELUSIONS_REALISATION_SEEN_TICKS = BUILDER
            .comment("Delusions: how long you must have one in view (in your view cone AND in line of sight)",
                    "before it can notice you back. 60 = 3s of staring.")
            .defineInRange("delusionsRealisationSeenTicks", 60, 10, 600);

    public static final ModConfigSpec.IntValue DELUSIONS_REALISATION_CHANCE = BUILDER
            .comment("Delusions: percent chance per second, once you've watched one long enough, that it turns",
                    "around and stares back.")
            .defineInRange("delusionsRealisationChancePercent", 15, 0, 100);

    public static final ModConfigSpec.IntValue DELUSIONS_CHARGE_CHANCE = BUILDER
            .comment("Delusions: percent chance a realisation ends with it SPRINTING AT YOU rather than just",
                    "vanishing where it stands.")
            .defineInRange("delusionsChargeChancePercent", 40, 0, 100);

    public static final ModConfigSpec.DoubleValue DELUSIONS_HIT_REACH = BUILDER
            .comment("Delusions: how far your swing reaches one, in blocks. They are deliberately NOT pickable",
                    "by vanilla's crosshair (that would send the server an attack packet for an entity it has",
                    "never heard of), so the swing is ray-traced against them separately.")
            .defineInRange("delusionsHitReach", 4.0, 1.0, 8.0);

    public static final ModConfigSpec.DoubleValue DELUSIONS_VIEW_CONE_DOT = BUILDER
            .comment("Delusions: how centred in your view one must be to count as 'seen'. 0.75 is roughly a",
                    "41-degree cone off your crosshair; lower widens it.")
            .defineInRange("delusionsViewConeDot", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue DELUSIONS_OBSERVE_DISTANCE = BUILDER
            .comment("Delusions: how close one gets before it stops and just watches you, in blocks. Close",
                    "enough to be uncomfortable, far enough that it isn't touching you.")
            .defineInRange("delusionsObserveDistance", 4.0, 1.0, 24.0);

    public static final ModConfigSpec.BooleanValue DELUSIONS_MIRROR_SELF = BUILDER
            .comment("Delusions: whether YOUR OWN skin and name can be used for a fake player. When you are the",
                    "only player online it is used regardless — there is nobody else to be.")
            .define("delusionsMirrorSelf", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EFFECT_COST_OVERRIDES = BUILDER
            .comment("Per-effect essence cost overrides, format \"witchmod:effect_id=cost\". Any effect not",
                    "listed here keeps its code-defined default cost (section 5.1/5.2).")
            .defineListAllowEmpty("effectCostOverrides", List.of(), () -> "", Config::validateOverrideEntry);

    // --- Low Gravity (blessing) ------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue LOW_GRAVITY_GRAVITY_MULT = BUILDER
            .comment("Low Gravity: multiplier on the GRAVITY attribute — the whole floaty feel (slower fall + higher float). <1 = lighter.")
            .defineInRange("lowGravityGravityMultiplier", 0.55, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue LOW_GRAVITY_JUMP_MULT = BUILDER
            .comment("Low Gravity: multiplier on JUMP_STRENGTH — how much higher your jumps launch.")
            .defineInRange("lowGravityJumpMultiplier", 1.6, 1.0, 4.0);

    public static final ModConfigSpec.DoubleValue LOW_GRAVITY_FALL_DAMAGE_MULT = BUILDER
            .comment("Low Gravity: multiplier on fall damage taken (soft landings).")
            .defineInRange("lowGravityFallDamageMultiplier", 0.4, 0.0, 1.0);

    // --- Berserker (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue BERSERKER_REDUCTION_PER_HIT = BUILDER
            .comment("Berserker: attack-cooldown reduction gained per consecutive landed hit, as a fraction (0.07 = 7%).")
            .defineInRange("berserkerReductionPerHit", 0.07, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BERSERKER_MAX_REDUCTION = BUILDER
            .comment("Berserker: maximum total attack-cooldown reduction (0.70 = 70% shorter cooldown, i.e. ~3.3x faster).")
            .defineInRange("berserkerMaxReduction", 0.70, 0.0, 0.95);

    public static final ModConfigSpec.DoubleValue BERSERKER_RESET_SECONDS = BUILDER
            .comment("Berserker: seconds without landing a hit before the whole stack resets (also resets instantly on a miss).")
            .defineInRange("berserkerResetSeconds", 4.5, 0.5, 60.0);

    // --- Enchanter (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue ENCHANTER_XP_REDUCTION = BUILDER
            .comment("Enchanter: fraction (0..1) of the XP-level cost of enchanting-table use that is refunded. 0.8 = 80% cheaper (small costs become free).")
            .defineInRange("enchanterXpReduction", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.IntValue ENCHANTER_LEVEL_BONUS = BUILDER
            .comment("Enchanter: how many extra enchant levels each of the table's three options is bumped by (better enchants by default).")
            .defineInRange("enchanterLevelBonus", 3, 0, 30);

    // --- Pacifier (blessing) ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue PACIFIER_RADIUS = BUILDER
            .comment("Pacifier: radius (blocks) of the anti-grief aura for entities — primed TNT, creepers, fireballs.")
            .defineInRange("pacifierRadius", 8.0, 1.0, 32.0);

    public static final ModConfigSpec.IntValue PACIFIER_BLOCK_RADIUS = BUILDER
            .comment("Pacifier: radius (blocks) within which spreading fire is snuffed out.")
            .defineInRange("pacifierBlockRadius", 5, 1, 16);

    public static final ModConfigSpec.IntValue PACIFIER_CHECK_INTERVAL = BUILDER
            .comment("Pacifier: how often (ticks) the aura sweeps. Lower = snappier, more work.")
            .defineInRange("pacifierCheckIntervalTicks", 5, 1, 40);

    // --- Ocean's Blessing (blessing) -------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue OCEANS_SWIM_BOOST = BUILDER
            .comment("Ocean's Blessing: modest baseline forward velocity added per tick while swimming (a small nudge on its own — the real speed comes from dolphins).")
            .defineInRange("oceansSwimBoost", 0.025, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_MULT = BUILDER
            .comment("Ocean's Blessing: multiplier on the swim boost while you have Dolphin's Grace (from an attracted dolphin) — this is where the big speed lives.")
            .defineInRange("oceansDolphinMultiplier", 4.0, 1.0, 12.0);

    public static final ModConfigSpec.DoubleValue OCEANS_MAX_SPEED = BUILDER
            .comment("Ocean's Blessing: cap on horizontal swim speed (blocks/tick) the boost will drive you to.")
            .defineInRange("oceansMaxSpeed", 0.9, 0.1, 3.0);

    public static final ModConfigSpec.DoubleValue OCEANS_PACIFY_RADIUS = BUILDER
            .comment("Ocean's Blessing: radius (blocks) within which aggressive mobs are pacified toward you while you're in water.")
            .defineInRange("oceansPacifyRadius", 16.0, 1.0, 48.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_ATTRACT_RADIUS = BUILDER
            .comment("Ocean's Blessing: wide radius (blocks) from which dolphins are drawn to you and made to follow.")
            .defineInRange("oceansDolphinAttractRadius", 40.0, 4.0, 128.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_GRACE_RADIUS = BUILDER
            .comment("Ocean's Blessing: how close a dolphin must be (blocks) to grant you Dolphin's Grace (and thus the big speed).")
            .defineInRange("oceansDolphinGraceRadius", 12.0, 2.0, 48.0);

    public static final ModConfigSpec.DoubleValue OCEANS_DOLPHIN_NAV_SPEED = BUILDER
            .comment("Ocean's Blessing: navigation speed multiplier used when steering attracted dolphins toward you.")
            .defineInRange("oceansDolphinNavSpeed", 2.2, 0.5, 5.0);

    // --- Gladiator (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue GLADIATOR_WINDOW_TICKS = BUILDER
            .comment("Gladiator: length of the parry window (ticks) after right-clicking a sword/axe. 4 = 0.2s — tight, demands precision.")
            .defineInRange("gladiatorWindowTicks", 4, 2, 40);

    public static final ModConfigSpec.DoubleValue GLADIATOR_FRONT_DOT = BUILDER
            .comment("Gladiator: how 'in front' the attacker must be to parry (dot of look vs direction-to-attacker; higher = narrower frontal arc).")
            .defineInRange("gladiatorFrontDot", 0.2, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_RIPOSTE_DAMAGE_MULT = BUILDER
            .comment("Gladiator: NORMAL riposte damage as a multiple of your weapon's swing damage.")
            .defineInRange("gladiatorRiposteDamageMultiplier", 1.4, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_PERFECT_DAMAGE_MULT = BUILDER
            .comment("Gladiator: PERFECT-parry riposte damage multiple (a tight, early parry).")
            .defineInRange("gladiatorPerfectDamageMultiplier", 1.75, 0.0, 8.0);

    public static final ModConfigSpec.IntValue GLADIATOR_PERFECT_TICKS = BUILDER
            .comment("Gladiator: how tight the PERFECT window is — a parry landing within this many ticks of the danger counts as perfect. 2 = 0.1s.")
            .defineInRange("gladiatorPerfectTicks", 2, 1, 20);

    public static final ModConfigSpec.IntValue GLADIATOR_PERFECT_STUN_TICKS = BUILDER
            .comment("Gladiator: how long (ticks) a PERFECT parry stuns the foe (heavy Slowness+Weakness) so the riposte knockback throws them. 10 = 0.5s.")
            .defineInRange("gladiatorPerfectStunTicks", 10, 0, 100);

    public static final ModConfigSpec.IntValue GLADIATOR_LEEWAY_TICKS = BUILDER
            .comment("Gladiator: reactive leeway — you can still parry for this many ticks AFTER a hit lands (press it just late). ~4 = 0.18s.")
            .defineInRange("gladiatorLeewayTicks", 4, 0, 20);

    public static final ModConfigSpec.IntValue GLADIATOR_WHIFF_LOCK_EXTRA_TICKS = BUILDER
            .comment("Gladiator: while parrying you can't switch/swing/use your hand; a WHIFF extends that lock by this many ticks (2 = 0.1s) as extra risk.")
            .defineInRange("gladiatorWhiffLockExtraTicks", 2, 0, 20);

    public static final ModConfigSpec.IntValue GLADIATOR_SWORD_COOLDOWN_TICKS = BUILDER
            .comment("Gladiator: weapon swing cooldown imposed by a SUCCESSFUL parry (riposte/reflect) — a sword's timing. 13 ~ 0.65s (sword attack speed 1.6).")
            .defineInRange("gladiatorSwordCooldownTicks", 13, 0, 60);

    public static final ModConfigSpec.IntValue GLADIATOR_AXE_COOLDOWN_TICKS = BUILDER
            .comment("Gladiator: weapon swing cooldown imposed by a WHIFF — an axe's timing (slower). 22 ~ 1.1s (axe attack speed ~0.9).")
            .defineInRange("gladiatorAxeCooldownTicks", 22, 0, 80);

    public static final ModConfigSpec.DoubleValue GLADIATOR_RIPOSTE_KNOCKBACK = BUILDER
            .comment("Gladiator: extra knockback strength dealt by the riposte (perfect parries throw a little harder). Kept modest so foes don't fly.")
            .defineInRange("gladiatorRiposteKnockback", 0.8, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_SHAKE_STRENGTH = BUILDER
            .comment("Gladiator: base camera-shake strength on a parry (perfect shakes harder, whiff softer — scaled from this).")
            .defineInRange("gladiatorShakeStrength", 1.6, 0.0, 10.0);

    public static final ModConfigSpec.IntValue GLADIATOR_SHAKE_TICKS = BUILDER
            .comment("Gladiator: how long (ticks) the parry camera shake decays over.")
            .defineInRange("gladiatorShakeTicks", 6, 1, 40);

    public static final ModConfigSpec.IntValue GLADIATOR_RIPOSTE_DELAY_TICKS = BUILDER
            .comment("Gladiator: short delay (ticks) between a successful parry and the riposte hit landing.")
            .defineInRange("gladiatorRiposteDelayTicks", 4, 1, 20);

    public static final ModConfigSpec.DoubleValue GLADIATOR_COOLDOWN_SECONDS = BUILDER
            .comment("Gladiator: parry cooldown after a SUCCESSFUL parry (seconds).")
            .defineInRange("gladiatorCooldownSeconds", 2.5, 0.2, 30.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_WHIFF_COOLDOWN_SECONDS = BUILDER
            .comment("Gladiator: parry cooldown after a WHIFFED parry (seconds) — the success cooldown plus a 0.25s mistiming penalty.")
            .defineInRange("gladiatorWhiffCooldownSeconds", 2.75, 0.2, 30.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_SPEED = BUILDER
            .comment("Gladiator: launch speed of a reflected projectile, sent where you're LOOKING. A touch higher than incoming, but still fairly low.")
            .defineInRange("gladiatorReflectSpeed", 1.6, 0.2, 5.0);

    public static final ModConfigSpec.IntValue GLADIATOR_PROJECTILE_LEEWAY_TICKS = BUILDER
            .comment("Gladiator: extra ticks of slack on EACH side of the window for PROJECTILE parries (they're harder to time than melee, so more forgiving). 3 = ~0.15s.")
            .defineInRange("gladiatorProjectileLeewayTicks", 3, 0, 20);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_AIM_CONE = BUILDER
            .comment("Gladiator: half-angle (degrees) of the cone around your look within which a reflected projectile will assist-aim at an entity.")
            .defineInRange("gladiatorReflectAimConeDegrees", 28.0, 0.0, 90.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_AIM_BIAS = BUILDER
            .comment("Gladiator: how strongly a reflect leans toward an in-cone entity (0 = pure look direction, 1 = straight at the entity).")
            .defineInRange("gladiatorReflectAimBias", 0.75, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue GLADIATOR_REFLECT_AIM_RANGE = BUILDER
            .comment("Gladiator: how far (blocks) the reflect assist-aim looks for an entity in the cone.")
            .defineInRange("gladiatorReflectAimRange", 26.0, 2.0, 64.0);

    // --- Tank (blessing) -------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue TANK_BONUS_HEALTH = BUILDER
            .comment("Tank: bonus max health added via a MAX_HEALTH attribute (no status-effect UI at all). 20 = a whole extra health bar.")
            .defineInRange("tankBonusHealth", 20.0, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue TANK_REGEN_MULT = BUILDER
            .comment("Tank: multiplier on natural-regeneration healing (small heals) — significantly slower regen. 0.35 = 35% of normal.")
            .defineInRange("tankRegenMultiplier", 0.35, 0.0, 1.0);

    // --- Spider (blessing) -----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SPIDER_CLIMB_SPEED = BUILDER
            .comment("Spider: upward climb speed against a wall (blocks/tick). Vanilla spiders climb at ~0.2; higher is faster.")
            .defineInRange("spiderClimbSpeed", 0.3, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue SPIDER_WALL_JUMP_UP = BUILDER
            .comment("Spider: upward launch when you jump off a wall.")
            .defineInRange("spiderWallJumpUp", 0.56, 0.1, 2.0);

    public static final ModConfigSpec.DoubleValue SPIDER_WALL_JUMP_AWAY = BUILDER
            .comment("Spider: horizontal 'kick off' away from the wall when wall-jumping.")
            .defineInRange("spiderWallJumpAway", 0.42, 0.0, 2.0);

    public static final ModConfigSpec.IntValue SPIDER_WALL_JUMP_COOLDOWN = BUILDER
            .comment("Spider: minimum ticks between wall jumps (so one jump press = one kick).")
            .defineInRange("spiderWallJumpCooldownTicks", 6, 1, 40);

    // --- Ninja (blessing) ------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue NINJA_SPRINT_SPEED = BUILDER
            .comment("Ninja: extra movement speed as a fraction (0.25 = +25%), applied as a MOVEMENT_SPEED multiplier.")
            .defineInRange("ninjaSprintSpeed", 0.25, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue NINJA_ATTACK_SPEED = BUILDER
            .comment("Ninja: extra attack speed as a fraction (0.8 = +80% -> much faster swing recovery/animation).")
            .defineInRange("ninjaAttackSpeed", 0.8, 0.0, 4.0);

    public static final ModConfigSpec.DoubleValue NINJA_DOUBLE_JUMP_POWER = BUILDER
            .comment("Ninja: upward velocity of the mid-air second jump (a bit stronger than a normal jump).")
            .defineInRange("ninjaDoubleJumpPower", 0.64, 0.1, 2.0);

    public static final ModConfigSpec.DoubleValue NINJA_SWING_PITCH = BUILDER
            .comment("Ninja: pitch (speed) of the parry-whiff woosh played on each weapon swing. 1.7 = quick and sharp.")
            .defineInRange("ninjaSwingPitch", 1.7, 0.5, 2.0);

    // --- Backstabbing (blessing) -----------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue BACKSTAB_DAMAGE_MULT = BUILDER
            .comment("Backstabbing: melee damage multiplier when you hit a target from behind. Multiplies the final damage, so it STACKS with crits/enchants.")
            .defineInRange("backstabDamageMultiplier", 1.6, 1.0, 5.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_KNOCKBACK_MULT = BUILDER
            .comment("Backstabbing: knockback multiplier on a backstab (less knockback so you stay on them).")
            .defineInRange("backstabKnockbackMultiplier", 0.4, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_PLAYER_DOT = BUILDER
            .comment("Backstabbing vs PLAYERS: attacker counts as 'behind' when the dot of the victim's look and the direction-to-attacker is at/below this. Lower = stricter (must be well behind).")
            .defineInRange("backstabPlayerDot", -0.3, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_MOB_DOT = BUILDER
            .comment("Backstabbing vs MOBS: same threshold but MORE GENEROUS (mob AI faces you, so a wider rear arc counts). Higher = easier.")
            .defineInRange("backstabMobDot", 0.3, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue BACKSTAB_SOUND_PITCH = BUILDER
            .comment("Backstabbing: pitch of the riposte 'impact' sound played on a successful backstab.")
            .defineInRange("backstabSoundPitch", 1.2, 0.5, 2.0);

    // --- Prop Hunt (blessing) --------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue PROPHUNT_STILL_TICKS = BUILDER
            .comment("Prop Hunt: how long (ticks) you must crouch and stand still before you disguise as the block below you. 20 = 1s.")
            .defineInRange("propHuntStillTicks", 20, 5, 200);

    // --- Solicitor (curse) -----------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SOLICITOR_CHECK_INTERVAL = BUILDER
            .comment("Solicitor: how often (ticks) the curse re-checks/steers the trader.")
            .defineInRange("solicitorCheckIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec.DoubleValue SOLICITOR_MOVE_SPEED = BUILDER
            .comment("Solicitor: the trader's base MOVEMENT_SPEED attribute — low so it ambles rather than blitzing about (vanilla trader is ~0.5).")
            .defineInRange("solicitorMoveSpeed", 0.32, 0.05, 1.0);

    public static final ModConfigSpec.DoubleValue SOLICITOR_FOLLOW_SPEED = BUILDER
            .comment("Solicitor: navigation speed multiplier while walking toward you.")
            .defineInRange("solicitorFollowSpeed", 0.85, 0.3, 3.0);

    public static final ModConfigSpec.DoubleValue SOLICITOR_FOLLOW_DISTANCE = BUILDER
            .comment("Solicitor: it only walks toward you when further than this (blocks); closer than it, it STOPS so you can actually trade with it.")
            .defineInRange("solicitorFollowDistance", 3.5, 1.0, 16.0);

    public static final ModConfigSpec.DoubleValue SOLICITOR_TELEPORT_DISTANCE = BUILDER
            .comment("Solicitor: if the trader falls further than this (blocks) behind you, it teleports closer (it never gives up).")
            .defineInRange("solicitorTeleportDistance", 18.0, 6.0, 128.0);

    public static final ModConfigSpec.IntValue SOLICITOR_CHAT_MIN_TICKS = BUILDER
            .comment("Solicitor: shortest gap (ticks) between the trader's chat pitches.")
            .defineInRange("solicitorChatMinTicks", 200, 20, 6000);

    public static final ModConfigSpec.IntValue SOLICITOR_CHAT_MAX_TICKS = BUILDER
            .comment("Solicitor: longest gap (ticks) between the trader's chat pitches.")
            .defineInRange("solicitorChatMaxTicks", 500, 20, 12000);

    public static final ModConfigSpec.DoubleValue SOLICITOR_CHAT_RADIUS = BUILDER
            .comment("Solicitor: how far (blocks) from the trader its chat pitches are heard.")
            .defineInRange("solicitorChatRadius", 28.0, 4.0, 128.0);

    public static final ModConfigSpec.IntValue SOLICITOR_HIDE_MIN_SECONDS = BUILDER
            .comment("Solicitor: minimum time (seconds) the trader stays gone after you actually complete a trade with it.")
            .defineInRange("solicitorHideMinSeconds", 90, 5, 6000);

    public static final ModConfigSpec.IntValue SOLICITOR_HIDE_MAX_SECONDS = BUILDER
            .comment("Solicitor: maximum time (seconds) gone after a trade — the roll is biased toward the LONGER end.")
            .defineInRange("solicitorHideMaxSeconds", 600, 5, 12000);

    // --- The Snail (curse) -----------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SNAIL_BASE_SPEED = BUILDER
            .comment("The Snail: THE fundamental balancing constant — base chase speed in BLOCKS PER SECOND (its speed when right on top of you).")
            .defineInRange("snailBaseSpeedBlocksPerSecond", 0.9, 0.05, 20.0);

    public static final ModConfigSpec.DoubleValue SNAIL_DISTANCE_SCALE = BUILDER
            .comment("The Snail: how much its speed grows per block of distance (0.08 = +8% base speed per block away) — slow when close, much faster when far.")
            .defineInRange("snailDistanceScale", 0.08, 0.0, 2.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MAX_SPEED = BUILDER
            .comment("The Snail: hard cap on its chase speed (blocks/second), however far away you get.")
            .defineInRange("snailMaxSpeedBlocksPerSecond", 22.0, 0.5, 100.0);

    public static final ModConfigSpec.DoubleValue SNAIL_TOUCH_DISTANCE = BUILDER
            .comment("The Snail: how close (blocks) it must get to touch you and detonate.")
            .defineInRange("snailTouchDistance", 1.3, 0.3, 4.0);

    public static final ModConfigSpec.DoubleValue SNAIL_SPAWN_DISTANCE = BUILDER
            .comment("The Snail: how far away (blocks) it starts, and re-appears after catching you.")
            .defineInRange("snailSpawnDistance", 45.0, 8.0, 256.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MATERIALISE_RADIUS = BUILDER
            .comment("The Snail: within this distance (blocks) the real entity is spawned/shown; beyond it only the virtual position is tracked (nothing loaded).")
            .defineInRange("snailMaterialiseRadius", 40.0, 8.0, 128.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MUSIC_DISTANCE = BUILDER
            .comment("The Snail: max distance (blocks) the music can be heard; it fades in from here and stops beyond it.")
            .defineInRange("snailMusicDistance", 24.0, 4.0, 64.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MUSIC_FULL_DISTANCE = BUILDER
            .comment("The Snail: within this distance (blocks) the music is at full volume; between it and the max distance it fades.")
            .defineInRange("snailMusicFullDistance", 18.0, 2.0, 64.0);

    public static final ModConfigSpec.DoubleValue SNAIL_MUSIC_VOLUME = BUILDER
            .comment("The Snail: volume of the looping music.")
            .defineInRange("snailMusicVolume", 0.68, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SNAIL_EXPLODE_POWER = BUILDER
            .comment("The Snail: explosion power when it reaches you (also dealt lethal directly, so it's an instant kill barring a totem/Last Stand).")
            .defineInRange("snailExplodePower", 6.0, 0.0, 20.0);

    // --- The Dweller (curse) ---------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue DWELLER_CHECK_INTERVAL_TICKS = BUILDER
            .comment("The Dweller: how often (ticks) its stalking logic runs.")
            .defineInRange("dwellerCheckIntervalTicks", 4, 1, 40);
    public static final ModConfigSpec.DoubleValue DWELLER_ANGER_MAX = BUILDER
            .comment("The Dweller: the anger ceiling; the closer to it, the closer the chase.")
            .defineInRange("dwellerAngerMax", 100.0, 1.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_BASE_ANGER_PER_SECOND = BUILDER
            .comment("The Dweller: anger it gains every second no matter what — the slow, inevitable escalation. VERY low: at max (100) with the dark/alone accelerants a dread session should take ~13-18 min to peak. The big driver is being ALONE + in the DARK.")
            .defineInRange("dwellerBaseAngerPerSecond", 0.06, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_LOOK_ANGER_GAIN = BUILDER
            .comment("The Dweller: anger it gains each time you LOOK AT IT (it flees when spotted, but hates being seen). The objective is not to look.")
            .defineInRange("dwellerLookAngerGain", 7.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_VIEW_CONE_DOT = BUILDER
            .comment("The Dweller: how centred in your view it must be to count as 'looked at' (dot product; higher = you must look more directly at it).")
            .defineInRange("dwellerViewConeDot", 0.90, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER1_THRESHOLD = BUILDER
            .comment("The Dweller: anger at which it moves from Far observation to Close observation.")
            .defineInRange("dwellerTier1Threshold", 25.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER2_THRESHOLD = BUILDER
            .comment("The Dweller: anger at which it moves from Close observation to Inspection (it starts coming close).")
            .defineInRange("dwellerTier2Threshold", 55.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER3_THRESHOLD = BUILDER
            .comment("The Dweller: anger at which it enters the Chase tier (fake chases, then the real one).")
            .defineInRange("dwellerTier3Threshold", 88.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_THRESHOLD = BUILDER
            .comment("The Dweller: anger at which the FINAL chase begins — it rushes you through walls, and touching you ends it (a bloody death that consumes the curse).")
            .defineInRange("dwellerChaseThreshold", 100.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue DWELLER_FAR_DISTANCE = BUILDER
            .comment("The Dweller: distance (blocks) it watches from during Far observation.")
            .defineInRange("dwellerFarDistance", 22.0, 2.0, 64.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_DISTANCE = BUILDER
            .comment("The Dweller: distance (blocks) it watches from during Close observation.")
            .defineInRange("dwellerCloseDistance", 11.0, 2.0, 64.0);
    public static final ModConfigSpec.DoubleValue DWELLER_INSPECT_DISTANCE = BUILDER
            .comment("The Dweller: distance (blocks) it stands at during Inspection — right in your space.")
            .defineInRange("dwellerInspectDistance", 4.0, 1.0, 32.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SPEED = BUILDER
            .comment("The Dweller: chase speed (blocks/second) during the final chase — meant to catch you.")
            .defineInRange("dwellerChaseSpeed", 8.5, 1.0, 40.0);
    public static final ModConfigSpec.DoubleValue DWELLER_TOUCH_DISTANCE = BUILDER
            .comment("The Dweller: how close (blocks) it must get during the chase to catch and kill you.")
            .defineInRange("dwellerTouchDistance", 1.7, 0.5, 8.0);
    public static final ModConfigSpec.DoubleValue DWELLER_GHOST_CHASE_SPEED = BUILDER
            .comment("The Dweller: BASE chase speed (blocks/second) of the Ghost-Girl hunt at the moment a chase can start. Scales UP with dread by dwellerChaseSpeedDreadBonus. Below sprint but relentless.")
            .defineInRange("dwellerGhostChaseSpeed", 3.4, 0.5, 10.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SPEED_DREAD_BONUS = BUILDER
            .comment("The Dweller: fractional chase-speed bonus at MAX dread (e.g. 0.45 = up to +45% faster at full dread than the base speed). Higher dread = faster hunt.")
            .defineInRange("dwellerChaseSpeedDreadBonus", 0.45, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_STRIDE = BUILDER
            .comment("The Dweller: blocks the dweller must walk between footstep sounds during a chase (its stride length). Larger = fewer, more spaced-out steps. Tuned to match the movement speed.")
            .defineInRange("dwellerChaseStride", 1.15, 0.3, 4.0);
    public static final ModConfigSpec.IntValue DWELLER_SHAKE_TICKS = BUILDER
            .comment("The Dweller: how long (ticks) the on-hit camera shake lasts (bang-behind / lunge / the finale).")
            .defineInRange("dwellerShakeTicks", 9, 1, 60);
    public static final ModConfigSpec.DoubleValue DWELLER_SHAKE_STRENGTH = BUILDER
            .comment("The Dweller: peak camera-shake strength (degrees of random jolt) for the on-hit shake.")
            .defineInRange("dwellerShakeStrength", 1.2, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue DWELLER_WATCH_VANISH_DISTANCE = BUILDER
            .comment("The Dweller: any passive WATCH vanishes the moment you get within this many blocks of it (you can never close on a watch — it blinks away).")
            .defineInRange("dwellerWatchVanishDistance", 4.0, 1.0, 12.0);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_MIN_COOLDOWN_TICKS = BUILDER
            .comment("The Dweller: the SHORTEST chase cooldown (ticks) — reached at MAX dread. The cooldown is the window where the game won't roll a new chase; at max dread it's tiny, so hunts come back almost instantly. Default 5s.")
            .defineInRange("dwellerChaseMinCooldownTicks", 100, 0, 6000);
    public static final ModConfigSpec.IntValue DWELLER_MAX_DREAD_LOOK_TICKS = BUILDER
            .comment("The Dweller: at MAX dread, how long (ticks) you must hold eye contact with it before it goes aggressive and the hunt begins. Low = looking at it is near-instant death sentence.")
            .defineInRange("dwellerMaxDreadLookTicks", 8, 1, 100);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_STUCK_TICKS = BUILDER
            .comment("The Dweller: how long (ticks) the chase can fail to make progress (truly walled in, unable to path/climb/break through) before it subtly teleports closer as a LAST-RESORT fallback. Higher = the good AI is relied on more and teleports are rarer.")
            .defineInRange("dwellerChaseStuckTicks", 70, 4, 400);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_RAMP_TICKS = BUILDER
            .comment("The Dweller: how long (ticks) a chase takes to ramp from its slow starting speed up to full pace. The hunt starts a touch slow and builds momentum.")
            .defineInRange("dwellerChaseRampTicks", 120, 1, 1200);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_RAMP_START = BUILDER
            .comment("The Dweller: chase speed at the START of a hunt as a fraction of the dread-scaled full speed (e.g. 0.7 = starts at 70% pace, then builds).")
            .defineInRange("dwellerChaseRampStart", 0.7, 0.1, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_RAMP_END = BUILDER
            .comment("The Dweller: chase speed at the END of the ramp as a fraction of the dread-scaled speed (e.g. 1.15 = the fully wound-up hunt is 15% faster than the base dread-scaled speed).")
            .defineInRange("dwellerChaseRampEnd", 1.15, 0.5, 3.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_CLIMB_RATE = BUILDER
            .comment("The Dweller: how many blocks per tick the chaser can climb vertically while pursuing (lets it scale blocks/stairs to follow you up). Higher = snappier climbs.")
            .defineInRange("dwellerChaseClimbRate", 0.55, 0.1, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_BRIDGE_CHASE_MIN_CHANCE = BUILDER
            .comment("The Dweller: chance (0-1) that a watch/sizeup/lunge BRIDGES straight into a chase at the chase floor (low dread). The hunt can erupt from where the creature stands rather than always resetting.")
            .defineInRange("dwellerBridgeChaseMinChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_BRIDGE_CHASE_MAX_CHANCE = BUILDER
            .comment("The Dweller: chance (0-1) that a watch/sizeup/lunge bridges into a chase at MAX dread. Higher dread = the creature commits to the hunt from its current spot far more readily.")
            .defineInRange("dwellerBridgeChaseMaxChance", 0.55, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DWELLER_SIZEUP_STARE_TICKS = BUILDER
            .comment("The Dweller: during a SIZEUP (it stands right in front of you, passive), how long (ticks) you can stare back before its aggression ramps and it lunges/hunts.")
            .defineInRange("dwellerSizeupStareTicks", 34, 5, 200);
    public static final ModConfigSpec.DoubleValue DWELLER_LUNGE_SPEED = BUILDER
            .comment("The Dweller: the phantom-CHARGE speed (blocks/tick) of a lunge — it visibly rushes you at this pace, and the jumpscare fires the instant it arrives. Lower = a slower, more followable charge (not a teleport).")
            .defineInRange("dwellerLungeSpeed", 1.2, 0.3, 5.0);
    public static final ModConfigSpec.IntValue DWELLER_LUNGE_MAX_TICKS = BUILDER
            .comment("The Dweller: safety cap (ticks) on a lunge charge — it normally ends the instant it reaches you, but times out after this if something blocks the arrival.")
            .defineInRange("dwellerLungeMaxTicks", 40, 5, 200);
    // In-chase ravager LUNGE (high dread): a locked, dodgeable launch AT the player.
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_MIN_FRAC = BUILDER
            .comment("The Dweller: minimum dread fraction (0-1) for the mid-chase ravager LUNGE to be possible. Only higher dread earns the launch-at-you attacks.")
            .defineInRange("dwellerChaseLungeMinFrac", 0.66, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_RANGE = BUILDER
            .comment("The Dweller: max distance (blocks) at which a mid-chase lunge can start.")
            .defineInRange("dwellerChaseLungeRange", 16.0, 3.0, 48.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_SPEED = BUILDER
            .comment("The Dweller: distancegain lunge speed (blocks/second) — the straight launch at you used generally / as a close finisher. Fast but locked, so a sidestep dodges it.")
            .defineInRange("dwellerChaseLungeSpeed", 15.0, 4.0, 40.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_CLIMB_SPEED = BUILDER
            .comment("The Dweller: CLIMB lunge speed (blocks/second) — the upward-arcing launch that counters height-camping. Slightly slower so the vertical arc reads.")
            .defineInRange("dwellerChaseLungeClimbSpeed", 12.0, 4.0, 40.0);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_LUNGE_TICKS = BUILDER
            .comment("The Dweller: how long (ticks) a mid-chase lunge stays in its locked flight before it settles back to the walking chase if it missed.")
            .defineInRange("dwellerChaseLungeTicks", 14, 3, 60);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_LUNGE_COOLDOWN_TICKS = BUILDER
            .comment("The Dweller: cooldown (ticks) between mid-chase lunges, so it doesn't chain launches back-to-back.")
            .defineInRange("dwellerChaseLungeCooldownTicks", 70, 10, 600);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_CLIMB_HEIGHT = BUILDER
            .comment("The Dweller: how many blocks ABOVE the chaser you must be for a lunge to use the upward CLIMB variant (counter height-camping) instead of the straight distancegain launch.")
            .defineInRange("dwellerChaseLungeClimbHeight", 3.0, 1.0, 20.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_LUNGE_CLIMB_UP = BUILDER
            .comment("The Dweller: the UPWARD velocity (blocks/tick) of a CLIMB leap — how high it jumps to reach a player camping on height. ~0.42 clears 1 block; 0.85 clears ~4.")
            .defineInRange("dwellerChaseLungeClimbUp", 0.85, 0.3, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_MOVE_SPEED = BUILDER
            .comment("The Dweller: the chaser's MOVEMENT_SPEED attribute during a hunt. For a MOB this scale runs ~0.25 = a walk, so ~0.34 is a hard relentless sprint clearly faster than a fleeing player. The navigation multiplier below scales this further.")
            .defineInRange("dwellerChaseMoveSpeed", 0.34, 0.02, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SPRINT = BUILDER
            .comment("The Dweller: the base navigation SPEED MULTIPLIER on the chase MOVEMENT_SPEED (1.0 = sprint pace). A small in-hunt ramp (0.85→1.0) and dread bonus (+18% at max) ride on top.")
            .defineInRange("dwellerChaseSprint", 1.0, 0.3, 3.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_WATER_SPEED = BUILDER
            .comment("The Dweller: swim speed (blocks/tick) it's pushed toward you while IN WATER during a chase, so it powers across water fast instead of getting stuck. 0.5 ~ 10 b/s.")
            .defineInRange("dwellerChaseWaterSpeed", 0.5, 0.05, 2.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_START_DISTANCE = BUILDER
            .comment("The Dweller: how far behind you a FRESH chase places the creature by default (further away now, so a hunt has run-up).")
            .defineInRange("dwellerChaseStartDistance", 13.0, 3.0, 48.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_CHASE_DISTANCE = BUILDER
            .comment("The Dweller: the CLOSECHASE start distance — the old, much closer default. A fresh chase uses this instead of the far start on a dread-scaling chance.")
            .defineInRange("dwellerCloseChaseDistance", 6.0, 3.0, 24.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_CHASE_MIN_CHANCE = BUILDER
            .comment("The Dweller: chance (0-1) at LOW dread that a fresh chase is a CLOSECHASE (starts at the close distance rather than far).")
            .defineInRange("dwellerCloseChaseMinChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_CHASE_MAX_CHANCE = BUILDER
            .comment("The Dweller: chance (0-1) at MAX dread that a fresh chase is a CLOSECHASE. Higher dread = far more likely to start right on top of you.")
            .defineInRange("dwellerCloseChaseMaxChance", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_MIN_START_DISTANCE = BUILDER
            .comment("The Dweller: SAFETY floor — if a chase would begin closer than this (e.g. a sizeup/close watch bridging within a few blocks), the creature is pulled BACK to a safe start distance so it can't be an instant, uncounterable touch-kill.")
            .defineInRange("dwellerChaseMinStartDistance", 5.0, 2.0, 16.0);

    public static final ModConfigSpec.IntValue DWELLER_EVENT_INTERVAL_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum gap (ticks) between staged events (repositions, watches, breaks). Scaled shorter at higher tiers.")
            .defineInRange("dwellerEventIntervalMinTicks", 160, 20, 6000);
    public static final ModConfigSpec.IntValue DWELLER_EVENT_INTERVAL_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum gap (ticks) between staged events. Scaled shorter at higher tiers.")
            .defineInRange("dwellerEventIntervalMaxTicks", 500, 20, 12000);
    public static final ModConfigSpec.DoubleValue DWELLER_BED_BREAK_CHANCE = BUILDER
            .comment("The Dweller: chance a Sleep-watch event smashes the bed you're in (kicking you out of it).")
            .defineInRange("dwellerBedBreakChance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_DOOR_BREAK_CHANCE = BUILDER
            .comment("The Dweller: chance an event breaks a nearby door/trapdoor/glass to unnerve you (from tier 2).")
            .defineInRange("dwellerDoorBreakChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_EXPLODE_ENTITY_POWER = BUILDER
            .comment("The Dweller: explosion power when it detonates a nearby animal/villager as a warning (0 = kill it silently).")
            .defineInRange("dwellerExplodeEntityPower", 2.0, 0.0, 10.0);
    public static final ModConfigSpec.IntValue DWELLER_FAKE_CHASE_TICKS = BUILDER
            .comment("The Dweller: how long (ticks) a FAKE chase lunges at you before it stops and vanishes.")
            .defineInRange("dwellerFakeChaseTicks", 26, 4, 100);

    public static final ModConfigSpec.IntValue DWELLER_DORMANT_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum absence (ticks) between appearances at tier 0. Early FAR glimpses are meant to be fairly frequent (just distant); the absence shrinks smoothly as dread climbs.")
            .defineInRange("dwellerDormantMinTicks", 1000, 20, 24000);
    public static final ModConfigSpec.IntValue DWELLER_DORMANT_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum absence (ticks) between appearances at tier 0. Shrinks smoothly as dread climbs.")
            .defineInRange("dwellerDormantMaxTicks", 3600, 20, 48000);
    public static final ModConfigSpec.IntValue DWELLER_MANIFEST_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum time (ticks) a single appearance lasts before it melts away (at tier 0 — brief glimpses). Longer at higher tiers.")
            .defineInRange("dwellerManifestMinTicks", 40, 10, 6000);
    public static final ModConfigSpec.IntValue DWELLER_MANIFEST_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum time (ticks) a single appearance lasts. Longer at higher tiers.")
            .defineInRange("dwellerManifestMaxTicks", 120, 10, 12000);
    public static final ModConfigSpec.DoubleValue DWELLER_BOREDOM_BOOST = BUILDER
            .comment("The Dweller (BOREDOM): extra dread per second at low tiers (0-1) when no encounter has happened for a while — stops the early game stalling because nothing is pushing the tiers up.")
            .defineInRange("dwellerBoredomBoostPerSecond", 0.12, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_BOREDOM_DELAY_TICKS = BUILDER
            .comment("The Dweller (BOREDOM): how long (ticks) with no encounter before the boredom boost kicks in. Default 4s.")
            .defineInRange("dwellerBoredomDelayTicks", 80, 20, 2400);
    public static final ModConfigSpec.IntValue DWELLER_TIER0_STUCK_TICKS = BUILDER
            .comment("The Dweller (BOREDOM): ticks stuck at TIER 0 before an ADDITIONAL dread boost is added. Default 4.5min.")
            .defineInRange("dwellerTier0StuckTicks", 5400, 200, 48000);
    public static final ModConfigSpec.DoubleValue DWELLER_TIER0_STUCK_BOOST = BUILDER
            .comment("The Dweller (BOREDOM): extra dread per second once you've been stuck at tier 0 past dwellerTier0StuckTicks.")
            .defineInRange("dwellerTier0StuckBoostPerSecond", 0.15, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_INTERACT_DREAD = BUILDER
            .comment("The Dweller: bonus dread added each time you INTERACT with one of its effects instead of ignoring it — swinging at the stalker, or getting too close to a watch/mimic. The counterplay is to ignore, so engaging costs you.")
            .defineInRange("dwellerInteractDread", 7.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_TELL_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum length (ticks) of the TELL beat — the anticipation window before an appearance (heartbeat starts + quickens, a sign lands). Shrinks as dread climbs. Default ~3s.")
            .defineInRange("dwellerTellMinTicks", 60, 10, 1200);
    public static final ModConfigSpec.IntValue DWELLER_TELL_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum length (ticks) of the TELL anticipation beat. Default ~6s.")
            .defineInRange("dwellerTellMaxTicks", 120, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_RELEASE_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum length (ticks) of the RELEASE beat — the pointed quiet AFTER a scare, before the next lull. The exhale. Default ~4s.")
            .defineInRange("dwellerReleaseMinTicks", 80, 10, 1200);
    public static final ModConfigSpec.IntValue DWELLER_RELEASE_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum length (ticks) of the RELEASE exhale beat. Default ~8s.")
            .defineInRange("dwellerReleaseMaxTicks", 160, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_STARE_VANISH_TICKS = BUILDER
            .comment("The Dweller: staring straight at ANY watch for this long (ticks) makes it vanish — the double-take. Default 3s (60t). At high dread the vanish is instead a lunge/chase bridge, but the trigger time is the same.")
            .defineInRange("dwellerStareVanishTicks", 60, 5, 600);
    public static final ModConfigSpec.DoubleValue DWELLER_CREEP_SPEED = BUILDER
            .comment("The Dweller: how fast (blocks/second) it creeps toward you while you are NOT looking at it (weeping-angel style — it freezes when watched). Scaled up by tier.")
            .defineInRange("dwellerCreepSpeed", 1.7, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CLOSE_ENCOUNTER_DISTANCE = BUILDER
            .comment("The Dweller: how close (blocks) its unobserved creep must get to trigger a close encounter (a scare at low tiers; the chase at the top tier).")
            .defineInRange("dwellerCloseEncounterDistance", 2.3, 0.5, 8.0);
    public static final ModConfigSpec.DoubleValue DWELLER_WATCH_ANGER_PER_SECOND = BUILDER
            .comment("The Dweller: extra anger per second while you are actively looking at it — it hates being seen, so watching it hastens the end.")
            .defineInRange("dwellerWatchAngerPerSecond", 1.2, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_AMBIENT_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum gap (ticks) between the ambient dread cues (footsteps, breaths, a knock) it makes even while absent.")
            .defineInRange("dwellerAmbientMinTicks", 140, 10, 6000);
    public static final ModConfigSpec.IntValue DWELLER_AMBIENT_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum gap (ticks) between ambient dread cues.")
            .defineInRange("dwellerAmbientMaxTicks", 520, 10, 12000);

    public static final ModConfigSpec.DoubleValue DWELLER_WATCH_RADIUS = BUILDER
            .comment("The Dweller (Watch event): radius (blocks) to gather passive mobs that all freeze and stare at you.")
            .defineInRange("dwellerWatchRadius", 16.0, 4.0, 48.0);
    public static final ModConfigSpec.IntValue DWELLER_WATCH_MIN_MOBS = BUILDER
            .comment("The Dweller (Watch event): how many passive mobs must be nearby for the mass-stare to be possible.")
            .defineInRange("dwellerWatchMinMobs", 3, 1, 50);
    public static final ModConfigSpec.DoubleValue DWELLER_WATCH_CHANCE = BUILDER
            .comment("The Dweller (Watch event): chance the stare fires when enough passive mobs are around.")
            .defineInRange("dwellerWatchChance", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DWELLER_WATCH_MIN_TICKS = BUILDER
            .comment("The Dweller (Watch event): minimum time (ticks) the mobs hold their frozen stare.")
            .defineInRange("dwellerWatchMinTicks", 60, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_WATCH_MAX_TICKS = BUILDER
            .comment("The Dweller (Watch event): maximum time (ticks) the mobs hold their frozen stare.")
            .defineInRange("dwellerWatchMaxTicks", 200, 10, 4800);

    public static final ModConfigSpec.IntValue DWELLER_EYES_MIN = BUILDER
            .comment("The Dweller (Watchers event): minimum number of glowing-eye pairs that appear in the dark.")
            .defineInRange("dwellerEyesMin", 3, 1, 30);
    public static final ModConfigSpec.IntValue DWELLER_EYES_MAX = BUILDER
            .comment("The Dweller (Watchers event): maximum number of glowing-eye pairs.")
            .defineInRange("dwellerEyesMax", 7, 1, 40);
    public static final ModConfigSpec.DoubleValue DWELLER_EYES_DISTANCE = BUILDER
            .comment("The Dweller (Watchers event): how far out (blocks) the eyes hang, ringed around you in the dark.")
            .defineInRange("dwellerEyesDistance", 14.0, 4.0, 48.0);
    public static final ModConfigSpec.IntValue DWELLER_EYES_MIN_TICKS = BUILDER
            .comment("The Dweller (Watchers event): minimum time (ticks) the eyes watch before fading.")
            .defineInRange("dwellerEyesMinTicks", 80, 10, 2400);
    public static final ModConfigSpec.IntValue DWELLER_EYES_MAX_TICKS = BUILDER
            .comment("The Dweller (Watchers event): maximum time (ticks) the eyes watch before fading.")
            .defineInRange("dwellerEyesMaxTicks", 220, 10, 4800);

    public static final ModConfigSpec.IntValue DWELLER_KNOCK_BLOCKS = BUILDER
            .comment("The Dweller (Knock event): how many nearby doors/glass get knocked on (then shattered).")
            .defineInRange("dwellerKnockBlocks", 3, 1, 12);
    public static final ModConfigSpec.IntValue DWELLER_KNOCK_SHATTER_MIN_TICKS = BUILDER
            .comment("The Dweller (Knock event): minimum delay (ticks) after the knock before every doomed block shatters at once (1s default).")
            .defineInRange("dwellerKnockShatterMinTicks", 20, 1, 600);
    public static final ModConfigSpec.IntValue DWELLER_KNOCK_SHATTER_MAX_TICKS = BUILDER
            .comment("The Dweller (Knock event): maximum delay (ticks) after the knock before the shatter (6s default) — the long, silent pause is the point.")
            .defineInRange("dwellerKnockShatterMaxTicks", 120, 1, 1200);

    public static final ModConfigSpec.IntValue DWELLER_HALLUCINATION_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum gap (ticks) between AUDITORY hallucinations — fake footsteps, mining, chests, other-player sounds. Deliberately LONG so they stay rare and impactful; tier 0 is ~2.2x rarer still.")
            .defineInRange("dwellerHallucinationMinTicks", 1000, 10, 12000);
    public static final ModConfigSpec.IntValue DWELLER_HALLUCINATION_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum gap (ticks) between auditory hallucinations.")
            .defineInRange("dwellerHallucinationMaxTicks", 3400, 10, 24000);

    // --- The Dweller: DREAD (the fluid, multi-factor progression meter + its counterplay) ----------------
    public static final ModConfigSpec.DoubleValue DWELLER_DARK_DREAD = BUILDER
            .comment("The Dweller: EXTRA dread per second while standing in DARKNESS (light <= dwellerDarkLightLevel). The dark is where it thrives.")
            .defineInRange("dwellerDarkDreadPerSecond", 0.06, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_ISOLATION_DREAD = BUILDER
            .comment("The Dweller: EXTRA dread per second while you are ALONE (no other player within dwellerCompanyRadius). The dominant driver — being alone with it is where the horror lives, so this is large.")
            .defineInRange("dwellerIsolationDreadPerSecond", 0.05, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_NIGHT_DREAD = BUILDER
            .comment("The Dweller: EXTRA dread per second at NIGHT.")
            .defineInRange("dwellerNightDreadPerSecond", 0.02, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_ENCLOSED_DREAD = BUILDER
            .comment("The Dweller: EXTRA dread per second while ENCLOSED (no sky above you) — trapped underground with it.")
            .defineInRange("dwellerEnclosedDreadPerSecond", 0.02, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_LIGHT_CALM = BUILDER
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second while standing in bright light (light >= dwellerLightLevel), scaled by how bright.")
            .defineInRange("dwellerLightCalmPerSecond", 0.05, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_COMPANY_CALM = BUILDER
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second per nearby player (capped at 3) — safety in numbers.")
            .defineInRange("dwellerCompanyCalmPerSecond", 0.04, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_DAYLIGHT_CALM = BUILDER
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second in open DAYLIGHT (day + sky access) — it hates the sun.")
            .defineInRange("dwellerDaylightCalmPerSecond", 0.10, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_IGNORE_CALM = BUILDER
            .comment("The Dweller (COUNTERPLAY): dread CALMED per second while it is manifest and you are NOT looking at it — the real reward for ignoring it. Dread can now fall (never below 0), but the accelerants keep it climbing overall if you can't stay calm.")
            .defineInRange("dwellerIgnoreCalmPerSecond", 0.04, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_MIN_TICKS = BUILDER
            .comment("The Dweller (FOREPLAY): minimum length (ticks) of the opening phase — no fog/desaturation, music plays, only the odd VERY distant watch. Default 40s.")
            .defineInRange("dwellerForeplayMinTicks", 800, 100, 24000);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_MAX_TICKS = BUILDER
            .comment("The Dweller (FOREPLAY): maximum length (ticks) of the opening phase. Default 3min.")
            .defineInRange("dwellerForeplayMaxTicks", 3600, 100, 48000);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_WATCH_GAP_MIN = BUILDER
            .comment("The Dweller (FOREPLAY): minimum gap (ticks) between the rare distant watches during the opening phase.")
            .defineInRange("dwellerForeplayWatchGapMinTicks", 600, 40, 12000);
    public static final ModConfigSpec.IntValue DWELLER_FOREPLAY_WATCH_GAP_MAX = BUILDER
            .comment("The Dweller (FOREPLAY): maximum gap (ticks) between the rare distant watches during the opening phase.")
            .defineInRange("dwellerForeplayWatchGapMaxTicks", 1800, 40, 24000);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_MIN_TICKS = BUILDER
            .comment("The Dweller: minimum length (ticks) of a chase before it breaks off (if it can't catch you). Default 20s.")
            .defineInRange("dwellerChaseMinTicks", 400, 40, 2400);
    public static final ModConfigSpec.DoubleValue DWELLER_COMPANY_RADIUS = BUILDER
            .comment("The Dweller: radius (blocks) within which other players count as company (calming you and making it shy).")
            .defineInRange("dwellerCompanyRadius", 24.0, 4.0, 128.0);
    public static final ModConfigSpec.IntValue DWELLER_LIGHT_LEVEL = BUILDER
            .comment("The Dweller: light level at/above which you count as 'in the light' (calming, repels it).")
            .defineInRange("dwellerLightLevel", 8, 0, 15);
    public static final ModConfigSpec.IntValue DWELLER_DARK_LEVEL = BUILDER
            .comment("The Dweller: light level at/below which you count as 'in the dark' (dread rises, it grows bold).")
            .defineInRange("dwellerDarkLevel", 5, 0, 15);
    public static final ModConfigSpec.DoubleValue DWELLER_DREAD_FLOOR_PER_SECOND = BUILDER
            .comment("The Dweller: the inevitable rising FLOOR — dread can be held down by playing safe, but never below this slowly-climbing floor, so it always eventually progresses. Per second.")
            .defineInRange("dwellerDreadFloorPerSecond", 0.09, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_DREAD_FLOOR_CAP_PERCENT = BUILDER
            .comment("The Dweller: how high (percent of max) the inevitable floor can climb. Above this, only active dread from the dark/being alone/etc pushes you to the chase — so perfect play caps you just short of the hunt.")
            .defineInRange("dwellerDreadFloorCapPercent", 60.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue DWELLER_LIGHT_REPEL_PER_SECOND = BUILDER
            .comment("The Dweller (COUNTERPLAY): while it's manifest and you STARE it down FROM THE LIGHT, this 'banish' builds per second; at 1.0 it's driven off and loses dread. In the dark, staring only freezes it.")
            .defineInRange("dwellerLightRepelPerSecond", 0.5, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue DWELLER_REPEL_DREAD_DROP = BUILDER
            .comment("The Dweller (COUNTERPLAY): dread dropped when you banish a manifestation by staring it down in the light.")
            .defineInRange("dwellerRepelDreadDrop", 10.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DWELLER_LIGHT_LURK_DISTANCE = BUILDER
            .comment("The Dweller (COUNTERPLAY): while you're in bright light, it will not creep closer than this (blocks) — it lurks at the edge of the light.")
            .defineInRange("dwellerLightLurkDistance", 6.0, 0.0, 32.0);

    public static final ModConfigSpec.IntValue DWELLER_CHASE_MAX_TICKS = BUILDER
            .comment("The Dweller: maximum length (ticks) of a chase — each hunt rolls a duration in [chaseMinTicks, chaseMaxTicks] and breaks off at that if it can't catch you. Default 35s.")
            .defineInRange("dwellerChaseMaxTicks", 700, 40, 2400);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_ESCAPE_LIGHT_TICKS = BUILDER
            .comment("The Dweller (COUNTERPLAY): reach bright light (or other players) and hold it this many ticks mid-hunt and it breaks off — the light is your refuge.")
            .defineInRange("dwellerChaseEscapeLightTicks", 40, 5, 600);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SURVIVE_DREAD_DROP = BUILDER
            .comment("The Dweller: dread dropped when you SURVIVE a hunt — a hard-won reprieve before it builds again.")
            .defineInRange("dwellerChaseSurviveDreadDrop", 45.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_COOLDOWN_TICKS = BUILDER
            .comment("The Dweller: the LONGEST chase cooldown (ticks) — used at the moment dread first enters the chase range. Scales DOWN toward dwellerChaseMinCooldownTicks as dread climbs to max. Default 20s.")
            .defineInRange("dwellerChaseCooldownTicks", 400, 0, 24000);
    public static final ModConfigSpec.DoubleValue DWELLER_GRAB_FORCE = BUILDER
            .comment("The Dweller (Grab event): how hard it yanks you toward it.")
            .defineInRange("dwellerGrabForce", 1.1, 0.0, 5.0);

    // --- The Dweller: Possession event -----------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue DWELLER_POSSESS_RADIUS = BUILDER
            .comment("The Dweller (Possession event): radius (blocks) to find a mob to possess.")
            .defineInRange("dwellerPossessRadius", 20.0, 4.0, 64.0);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_TWITCH_MIN_TICKS = BUILDER
            .comment("The Dweller (Possession, passive branch): minimum build-up (ticks) a passive mob twitches before it turns on you.")
            .defineInRange("dwellerPossessTwitchMinTicks", 40, 5, 600);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_TWITCH_MAX_TICKS = BUILDER
            .comment("The Dweller (Possession, passive branch): maximum build-up (ticks) before it attacks.")
            .defineInRange("dwellerPossessTwitchMaxTicks", 100, 5, 1200);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_ATTACK_TICKS = BUILDER
            .comment("The Dweller (Possession, passive branch): how long (ticks) the possessed passive mob rampages before the spirit leaves it.")
            .defineInRange("dwellerPossessAttackTicks", 300, 20, 4800);
    public static final ModConfigSpec.DoubleValue DWELLER_POSSESS_DAMAGE = BUILDER
            .comment("The Dweller (Possession, passive branch): melee damage the possessed passive mob deals (attributed to it, so death messages name the mob).")
            .defineInRange("dwellerPossessDamage", 4.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_STRENGTH_LEVEL = BUILDER
            .comment("The Dweller (Possession): Strength amplifier granted to a possessed mob (0 = Strength I).")
            .defineInRange("dwellerPossessStrengthLevel", 2, 0, 9);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_SPEED_LEVEL = BUILDER
            .comment("The Dweller (Possession): Speed amplifier granted to a possessed mob (0 = Speed I).")
            .defineInRange("dwellerPossessSpeedLevel", 1, 0, 9);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_EFFECT_TICKS = BUILDER
            .comment("The Dweller (Possession, aggressive branch): how long (ticks) the Strength/Speed buffs last on an instantly-enraged hostile/neutral mob.")
            .defineInRange("dwellerPossessEffectTicks", 400, 20, 4800);

    // --- The Dweller: Isolation, Mimic, Phantom-attackers, Contagion, Finale ---------------------------
    public static final ModConfigSpec.IntValue DWELLER_ISOLATION_EVENT_TICKS = BUILDER
            .comment("The Dweller (Isolation event): how long (ticks) other players/villagers go UNRENDERED beyond 5 blocks — the world empties out to push you away from company.")
            .defineInRange("dwellerIsolationEventTicks", 1200, 100, 24000);
    public static final ModConfigSpec.IntValue DWELLER_MIMIC_BURST_TICKS = BUILDER
            .comment("The Dweller (Mimic event): how long (ticks) it refreshes the Delusions fake-player system for a fresh wave of impostors.")
            .defineInRange("dwellerMimicBurstTicks", 1200, 100, 24000);
    public static final ModConfigSpec.IntValue DWELLER_PHANTOM_MIN = BUILDER
            .comment("The Dweller (Phantom Attack event): minimum number of victim-only fake attackers that rush you.")
            .defineInRange("dwellerPhantomMin", 2, 1, 20);
    public static final ModConfigSpec.IntValue DWELLER_PHANTOM_MAX = BUILDER
            .comment("The Dweller (Phantom Attack event): maximum number of fake attackers.")
            .defineInRange("dwellerPhantomMax", 4, 1, 30);
    public static final ModConfigSpec.DoubleValue DWELLER_PHANTOM_SPEED = BUILDER
            .comment("The Dweller (Phantom Attack event): rush speed (blocks/second) of the fake attackers.")
            .defineInRange("dwellerPhantomSpeed", 6.5, 1.0, 30.0);
    public static final ModConfigSpec.DoubleValue DWELLER_PHANTOM_CONTACT = BUILDER
            .comment("The Dweller (Phantom Attack event): distance (blocks) at which a fake attacker 'hits' you.")
            .defineInRange("dwellerPhantomContact", 1.7, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue DWELLER_PHANTOM_TIER_POINTS = BUILDER
            .comment("The Dweller (Phantom Attack event): dread added each time you strike a fake attacker or one reaches you.")
            .defineInRange("dwellerPhantomTierPoints", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DWELLER_PHANTOM_BLIND_TICKS = BUILDER
            .comment("The Dweller (Phantom Attack event): brief Blindness (ticks) when a fake attacker vanishes.")
            .defineInRange("dwellerPhantomBlindTicks", 30, 0, 400);
    public static final ModConfigSpec.IntValue DWELLER_PHANTOM_LIFETIME_TICKS = BUILDER
            .comment("The Dweller (Phantom Attack event): how long (ticks) a fake attacker persists if it never reaches you.")
            .defineInRange("dwellerPhantomLifetimeTicks", 200, 20, 2400);

    public static final ModConfigSpec.DoubleValue DWELLER_CONTAGION_RADIUS = BUILDER
            .comment("The Dweller (Contagion — being NEAR the afflicted is dangerous): radius (blocks) within which OTHER players catch the edge of the madness.")
            .defineInRange("dwellerContagionRadius", 4.0, 1.0, 24.0);
    public static final ModConfigSpec.IntValue DWELLER_CONTAGION_INTERVAL_TICKS = BUILDER
            .comment("The Dweller (Contagion): how often (ticks) nearby others are checked.")
            .defineInRange("dwellerContagionIntervalTicks", 60, 5, 1200);
    public static final ModConfigSpec.DoubleValue DWELLER_CONTAGION_CHANCE = BUILDER
            .comment("The Dweller (Contagion): chance per check a nearby other player catches a jolt of the dread.")
            .defineInRange("dwellerContagionChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DWELLER_CONTAGION_DARKNESS_TICKS = BUILDER
            .comment("The Dweller (Contagion): Darkness (ticks) inflicted on a nearby other player.")
            .defineInRange("dwellerContagionDarknessTicks", 70, 0, 600);
    public static final ModConfigSpec.DoubleValue DWELLER_FINALE_EXPLOSION_POWER = BUILDER
            .comment("The Dweller (finale — the afflicted's death is a danger to those around them): explosion power at the death spot (hurts nearby others; no terrain damage).")
            .defineInRange("dwellerFinaleExplosionPower", 3.0, 0.0, 12.0);

    // --- The Dweller: escalating chases ----------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SPEED_FIRST = BUILDER
            .comment("The Dweller: chase speed (blocks/second) on the FIRST hunt — deliberately slow, a telegraphed hazard you can outrun while it warns you it's coming.")
            .defineInRange("dwellerChaseSpeedFirst", 4.0, 0.5, 30.0);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_SPEED_PER_CHASE = BUILDER
            .comment("The Dweller: how much faster (blocks/second) each subsequent hunt is, up to the dwellerChaseSpeed cap.")
            .defineInRange("dwellerChaseSpeedPerChase", 1.3, 0.0, 20.0);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_TELEPORT_FIRST_TICKS = BUILDER
            .comment("The Dweller: teleport 'switch-up' interval (ticks) on the FIRST hunt — long enough that it usually never blinks. Shrinks each hunt.")
            .defineInRange("dwellerChaseTeleportFirstTicks", 500, 20, 6000);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_TELEPORT_PER_CHASE = BUILDER
            .comment("The Dweller: how much the teleport interval shrinks (ticks) per hunt — later hunts blink far more often.")
            .defineInRange("dwellerChaseTeleportPerChase", 90, 0, 2000);
    public static final ModConfigSpec.IntValue DWELLER_CHASE_TELEPORT_MIN_TICKS = BUILDER
            .comment("The Dweller: the floor (ticks) on the teleport interval, however many hunts you've endured.")
            .defineInRange("dwellerChaseTeleportMinTicks", 55, 5, 2000);
    public static final ModConfigSpec.DoubleValue DWELLER_CHASE_TELEPORT_DISTANCE = BUILDER
            .comment("The Dweller: how far (blocks) it blinks away to when it teleports mid-hunt.")
            .defineInRange("dwellerChaseTeleportDistance", 11.0, 3.0, 40.0);

    // --- The Dweller: dread atmosphere (heartbeat + stray flickers) -------------------------------------
    public static final ModConfigSpec.IntValue DWELLER_HEARTBEAT_MIN_TICKS = BUILDER
            .comment("The Dweller: FASTEST gap (ticks) between the oppressive heartbeat you hear — reached at max dread / when it's right on top of you.")
            .defineInRange("dwellerHeartbeatMinTicks", 12, 3, 200);
    public static final ModConfigSpec.IntValue DWELLER_HEARTBEAT_MAX_TICKS = BUILDER
            .comment("The Dweller: SLOWEST gap (ticks) between heartbeats — the languid pulse of low dread.")
            .defineInRange("dwellerHeartbeatMaxTicks", 72, 5, 1200);
    public static final ModConfigSpec.DoubleValue DWELLER_HEARTBEAT_START_FRAC = BUILDER
            .comment("The Dweller: dread fraction (0..1) below which the heartbeat stays silent while it's absent — so calm, safe play is actually quiet.")
            .defineInRange("dwellerHeartbeatStartFrac", 0.22, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DWELLER_RANDOM_FLICKER_CHANCE = BUILDER
            .comment("The Dweller: chance per ~1.5s (tier 2+) that the lights FLICKER on their own — a stray jolt of wrongness, no event attached.")
            .defineInRange("dwellerRandomFlickerChance", 0.12, 0.0, 1.0);

    // --- Bedrock Moment (curse) — a parody of Bedrock-edition bugs -------------------------------------
    public static final ModConfigSpec.IntValue BEDROCK_EVENT_MIN_TICKS = BUILDER
            .comment("Bedrock Moment: minimum gap (ticks) between ACTIVE bug events. It's an overloaded curse, so they come thick and fast.")
            .defineInRange("bedrockEventMinTicks", 84, 20, 6000);
    public static final ModConfigSpec.IntValue BEDROCK_EVENT_MAX_TICKS = BUILDER
            .comment("Bedrock Moment: maximum gap (ticks) between active bug events.")
            .defineInRange("bedrockEventMaxTicks", 266, 20, 12000);
    // Active-event rarity tiers: every active bug is tagged tier 1/2/3 and drawn from a weighted pool.
    // Tier 1 = the mild everyday jank (very common); tier 2 = the disruptive stuff (medium); tier 3 = the
    // big shocks (rare). Retune the three weights to shift the whole balance at once.
    public static final ModConfigSpec.IntValue BEDROCK_TIER1_WEIGHT = BUILDER
            .comment("Bedrock Moment: pool weight of a TIER 1 (very common) active event.")
            .defineInRange("bedrockTier1Weight", 12, 0, 1000);
    public static final ModConfigSpec.IntValue BEDROCK_TIER2_WEIGHT = BUILDER
            .comment("Bedrock Moment: pool weight of a TIER 2 (medium rarity) active event.")
            .defineInRange("bedrockTier2Weight", 5, 0, 1000);
    public static final ModConfigSpec.IntValue BEDROCK_TIER3_WEIGHT = BUILDER
            .comment("Bedrock Moment: pool weight of a TIER 3 (rare) active event.")
            .defineInRange("bedrockTier3Weight", 2, 0, 1000);
    // Newer bug durations/chances.
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_ITEM_TICKS = BUILDER
            .comment("Bedrock Moment (Ghost Item): how long (ticks) your held item renders as a random other item.")
            .defineInRange("bedrockGhostItemTicks", 50, 5, 600);
    public static final ModConfigSpec.IntValue BEDROCK_INPUT_LAG_TICKS = BUILDER
            .comment("Bedrock Moment (Input Lag): how long (ticks) the input-lag window lasts.")
            .defineInRange("bedrockInputLagTicks", 120, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_INPUT_LAG_DELAY = BUILDER
            .comment("Bedrock Moment (Input Lag): how many ticks late your movement input is applied.")
            .defineInRange("bedrockInputLagDelayTicks", 6, 1, 40);
    public static final ModConfigSpec.IntValue BEDROCK_TEXTURE_FLICKER_TICKS = BUILDER
            .comment("Bedrock Moment (Texture Flicker): how long (ticks) the missing-texture flicker window lasts.")
            .defineInRange("bedrockTextureFlickerTicks", 70, 10, 600);
    public static final ModConfigSpec.IntValue BEDROCK_SPRINT_RESET_TICKS = BUILDER
            .comment("Bedrock Moment (Sprint Reset): how long (ticks) sprint keeps cutting out.")
            .defineInRange("bedrockSprintResetTicks", 140, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_PHASE_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Ghost Block Phase): min length (ticks) of the ghost-block spell (blocks placed/broken revert).")
            .defineInRange("bedrockGhostPhaseMinTicks", 80, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_PHASE_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Ghost Block Phase): max length (ticks) of the ghost-block spell (default 4–16s).")
            .defineInRange("bedrockGhostPhaseMaxTicks", 320, 20, 2400);
    public static final ModConfigSpec.IntValue BEDROCK_FOOD_REG_CHANCE = BUILDER
            .comment("Bedrock Moment (Food Reg): % chance an eaten food provides NO hunger (desync).")
            .defineInRange("bedrockFoodRegChancePercent", 14, 0, 100);
    public static final ModConfigSpec.IntValue BEDROCK_HIT_REG_CHANCE = BUILDER
            .comment("Bedrock Moment (Hit Reg): % chance a melee hit is invalidated (whiffs to the empty-swing sound).")
            .defineInRange("bedrockHitRegChancePercent", 6, 0, 100);
    public static final ModConfigSpec.IntValue BEDROCK_LANGUAGE_TICKS = BUILDER
            .comment("Bedrock Moment (Language Error): how long (ticks) the language stays swapped to pirate/welsh.")
            .defineInRange("bedrockLanguageTicks", 400, 60, 6000);
    public static final ModConfigSpec.IntValue BEDROCK_SPEEDBLITZ_FREEZE_TICKS = BUILDER
            .comment("Bedrock Moment (Speed Blitz): how long (ticks) you're frozen while your movement is stored.")
            .defineInRange("bedrockSpeedBlitzFreezeTicks", 20, 5, 200);
    public static final ModConfigSpec.IntValue BEDROCK_BSOD_TICKS = BUILDER
            .comment("Bedrock Moment (Fake BSOD): how long (ticks) the blue screen is shown.")
            .defineInRange("bedrockBsodTicks", 100, 20, 600);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIR_SWIM_CHANCE = BUILDER
            .comment("Bedrock Moment (Air Swimming): per-tick chance, WHILE swimming, that you keep swimming after leaving water.")
            .defineInRange("bedrockAirSwimChance", 0.02, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_AIR_SWIM_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Air Swimming): minimum ticks the air-swim lasts before it's force-cancelled.")
            .defineInRange("bedrockAirSwimMinTicks", 60, 10, 2000);
    public static final ModConfigSpec.IntValue BEDROCK_AIR_SWIM_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Air Swimming): maximum ticks (default 3–25s).")
            .defineInRange("bedrockAirSwimMaxTicks", 500, 10, 4000);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MOB_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Pause): min ticks nearby entities are paused (AI off + frozen).")
            .defineInRange("bedrockPauseMobMinTicks", 6, 1, 400);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MOB_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Pause): max ticks paused (default 0.3–5s).")
            .defineInRange("bedrockPauseMobMaxTicks", 100, 1, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_CATCHUP_MULT = BUILDER
            .comment("Bedrock Moment (Pause): after unpausing, entities tick THIS many times per tick (higher than tickspeed) to 'catch up'.")
            .defineInRange("bedrockPauseCatchupMultiplier", 10, 2, 40);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_CATCHUP_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Pause): min ticks of the post-unpause catch-up burst.")
            .defineInRange("bedrockPauseCatchupMinTicks", 20, 5, 400);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_CATCHUP_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Pause): max ticks of the catch-up burst (default 1–4s).")
            .defineInRange("bedrockPauseCatchupMaxTicks", 80, 5, 800);
    public static final ModConfigSpec.IntValue BEDROCK_FLOAT_TICKS = BUILDER
            .comment("Bedrock Moment (Float): how long (ticks) affected entities lose gravity.")
            .defineInRange("bedrockFloatTicks", 120, 10, 1200);
    public static final ModConfigSpec.DoubleValue BEDROCK_FLOAT_CHANCE = BUILDER
            .comment("Bedrock Moment (Float): per-entity chance each nearby entity loses gravity.")
            .defineInRange("bedrockFloatChance", 0.6, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_SLEEP_CANCEL_CHANCE = BUILDER
            .comment("Bedrock Moment (Sleep Cancel): per-tick chance, while sleeping, to be booted out of the bed.")
            .defineInRange("bedrockSleepCancelChance", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_COOLDOWNS_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Cooldowns — beneficial): min duration (ticks) of the quartered swing cooldown. Default 8s.")
            .defineInRange("bedrockCooldownsMinTicks", 160, 20, 6000);
    public static final ModConfigSpec.IntValue BEDROCK_COOLDOWNS_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Cooldowns — beneficial): max duration (ticks). Default 28s.")
            .defineInRange("bedrockCooldownsMaxTicks", 560, 20, 12000);
    public static final ModConfigSpec.DoubleValue BEDROCK_COOLDOWNS_ATTACK_SPEED_MULT = BUILDER
            .comment("Bedrock Moment (Cooldowns — beneficial): ATTACK_SPEED multiplier during the window. 4.0 = quartered cooldown (4x swings), Bedrock-style.")
            .defineInRange("bedrockCooldownsAttackSpeedMultiplier", 4.0, 1.0, 10.0);
    public static final ModConfigSpec.IntValue BEDROCK_HUNGRY_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Hungry): min ticks that right-click eats whatever you're holding.")
            .defineInRange("bedrockHungryMinTicks", 40, 10, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_HUNGRY_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Hungry): max ticks (default 2–11s).")
            .defineInRange("bedrockHungryMaxTicks", 220, 10, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_CREEPER_BOAT_SPEED = BUILDER
            .comment("Bedrock Moment (Charged Creeper Boat): per-tick speed the boat blitzes toward you. Fast — it's about the SHOCK of it screaming at you, not reliably killing.")
            .defineInRange("bedrockCreeperBoatSpeed", 3.0, 0.1, 12.0);
    public static final ModConfigSpec.IntValue BEDROCK_BLITZ_SPEED_LEVEL = BUILDER
            .comment("Bedrock Moment (Blitz): Speed amplifier given to blitzing creepers (0 = Speed I).")
            .defineInRange("bedrockBlitzSpeedLevel", 6, 0, 20);
    public static final ModConfigSpec.DoubleValue BEDROCK_BLITZ_DETONATE_DISTANCE = BUILDER
            .comment("Bedrock Moment (Blitz): distance (blocks) at which a blitzing creeper instantly detonates (no windup).")
            .defineInRange("bedrockBlitzDetonateDistance", 3.0, 1.0, 8.0);
    public static final ModConfigSpec.IntValue BEDROCK_MITOSIS_MAX = BUILDER
            .comment("Bedrock Moment (Mitosis): how many nearby mobs duplicate per event.")
            .defineInRange("bedrockMitosisMax", 4, 1, 30);
    public static final ModConfigSpec.IntValue BEDROCK_MITOSIS_NEARBY_CAP = BUILDER
            .comment("Bedrock Moment (Mitosis): SAFETY cap — skip duplicating if this many mobs are already nearby.")
            .defineInRange("bedrockMitosisNearbyCap", 40, 4, 400);
    public static final ModConfigSpec.IntValue BEDROCK_RUBBERBAND_COUNT = BUILDER
            .comment("Bedrock Moment (Rubberbanding): how many times it yanks you back to the saved spot.")
            .defineInRange("bedrockRubberbandCount", 10, 1, 40);
    public static final ModConfigSpec.IntValue BEDROCK_RUBBERBAND_DELAY_TICKS = BUILDER
            .comment("Bedrock Moment (Rubberbanding): gap (ticks) between each yank-back (short = frantic lag).")
            .defineInRange("bedrockRubberbandDelayTicks", 12, 2, 600);
    public static final ModConfigSpec.IntValue BEDROCK_BLUETOOTH_TICKS = BUILDER
            .comment("Bedrock Moment (Bluetooth Damage): how long (ticks) incoming damage is withheld and stored.")
            .defineInRange("bedrockBluetoothTicks", 90, 20, 600);
    public static final ModConfigSpec.IntValue BEDROCK_BLUETOOTH_RELEASE_DELAY = BUILDER
            .comment("Bedrock Moment (Bluetooth Damage): delay (ticks) after the window before the stored damage lands all at once.")
            .defineInRange("bedrockBluetoothReleaseDelay", 20, 1, 200);
    public static final ModConfigSpec.IntValue BEDROCK_HELICOPTER_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Helicopter): min spin-up time (ticks) before the anchored mob is launched (4s).")
            .defineInRange("bedrockHelicopterMinTicks", 80, 20, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_HELICOPTER_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Helicopter): max spin-up time (ticks) before launch (15s).")
            .defineInRange("bedrockHelicopterMaxTicks", 300, 20, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_HELICOPTER_LAUNCH = BUILDER
            .comment("Bedrock Moment (Helicopter): upward launch velocity when the spin ends.")
            .defineInRange("bedrockHelicopterLaunch", 2.6, 0.5, 10.0);
    public static final ModConfigSpec.IntValue BEDROCK_HELICOPTER_GROUND_TICKS = BUILDER
            .comment("Bedrock Moment (Helicopter): a brief ON-GROUND spin grace (ticks) — it whips into a spin where it stands before it starts rising.")
            .defineInRange("bedrockHelicopterGroundTicks", 30, 0, 400);
    public static final ModConfigSpec.DoubleValue BEDROCK_HELICOPTER_SPIN = BUILDER
            .comment("Bedrock Moment (Helicopter): degrees per tick it spins — big for a proper blur.")
            .defineInRange("bedrockHelicopterSpin", 62.0, 5.0, 180.0);
    public static final ModConfigSpec.IntValue BEDROCK_DROWN_TICKS = BUILDER
            .comment("Bedrock Moment (Desync Drowning): how long (ticks) the fake drowning lasts before it gives up on its own (also ends on touching water or dying).")
            .defineInRange("bedrockDrownTicks", 400, 40, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_GHOST_BLOCK_CHANCE = BUILDER
            .comment("Bedrock Moment (Ghost Blocks): chance a block you place turns out to be a ghost — it briefly appears then pops out (item still consumed).")
            .defineInRange("bedrockGhostBlockChance", 0.22, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_GHOST_BLOCK_DELAY_TICKS = BUILDER
            .comment("Bedrock Moment (Ghost Blocks): how long (ticks) the ghost block lingers before rejecting itself.")
            .defineInRange("bedrockGhostBlockDelayTicks", 8, 1, 100);
    public static final ModConfigSpec.IntValue BEDROCK_SOUND_DELAY_TICKS = BUILDER
            .comment("Bedrock Moment (Sound Delay): how long (ticks) the delayed-audio window lasts.")
            .defineInRange("bedrockSoundDelayTicks", 140, 20, 2400);
    public static final ModConfigSpec.IntValue BEDROCK_SOUND_DELAY_AMOUNT = BUILDER
            .comment("Bedrock Moment (Sound Delay): how far behind (ticks) sounds lag during the window.")
            .defineInRange("bedrockSoundDelayAmount", 18, 2, 100);
    public static final ModConfigSpec.IntValue BEDROCK_PHANTOM_DUR_TICKS = BUILDER
            .comment("Bedrock Moment (Phantom Durability): how long (ticks) your hotbar durability bars jitter for.")
            .defineInRange("bedrockPhantomDurTicks", 120, 20, 2400);
    public static final ModConfigSpec.IntValue BEDROCK_PERSPECTIVE_TICKS = BUILDER
            .comment("Bedrock Moment (Perspective Flip): how long (ticks) the camera is yanked to third-person.")
            .defineInRange("bedrockPerspectiveTicks", 50, 10, 600);
    public static final ModConfigSpec.DoubleValue BEDROCK_HOTBAR_DRIFT_CHANCE = BUILDER
            .comment("Bedrock Moment (Hotbar Drift): chance per ~1.5s that your selected hotbar slot drifts to another on its own.")
            .defineInRange("bedrockHotbarDriftChance", 0.14, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BEDROCK_MARKETPLACE_AD_COUNT = BUILDER
            .comment("Bedrock Moment (Marketplace): how many ad images exist (assets/witchmod/textures/gui/marketplace/ad_1.png .. ad_N.png) to pick from.")
            .defineInRange("bedrockMarketplaceAdCount", 3, 1, 64);
    public static final ModConfigSpec.IntValue BEDROCK_TICKSPEED_LEVEL = BUILDER
            .comment("Bedrock Moment (Tickspeed): how many times faster nearby entities tick (they're TICKED extra",
                    "times each server tick, mimicking a high-tickspeed/server-lag clip — not a Speed potion).")
            .defineInRange("bedrockTickspeedMultiplier", 4, 2, 20);
    public static final ModConfigSpec.IntValue BEDROCK_TICKSPEED_TICKS = BUILDER
            .comment("Bedrock Moment (Tickspeed): how long (ticks) nearby entities go haywire-fast.")
            .defineInRange("bedrockTickspeedTicks", 45, 5, 600);
    public static final ModConfigSpec.IntValue BEDROCK_DELAY_FALL_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Delay): min delay (ticks) before withheld FALL damage finally lands (0.5s).")
            .defineInRange("bedrockDelayFallMinTicks", 10, 1, 200);
    public static final ModConfigSpec.IntValue BEDROCK_DELAY_FALL_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Delay): max delay (ticks) before withheld fall damage lands (4s).")
            .defineInRange("bedrockDelayFallMaxTicks", 80, 1, 400);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Pause): min freeze (ticks) applied to your own projectiles (0.3s).")
            .defineInRange("bedrockPauseMinTicks", 6, 1, 200);
    public static final ModConfigSpec.IntValue BEDROCK_PAUSE_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Pause): max freeze (ticks) applied to your own projectiles (3s).")
            .defineInRange("bedrockPauseMaxTicks", 60, 1, 400);
    public static final ModConfigSpec.DoubleValue BEDROCK_PAUSE_CHANCE = BUILDER
            .comment("Bedrock Moment (Pause): chance a projectile you fire freezes mid-flight.")
            .defineInRange("bedrockPauseChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIMBOT_VELOCITY_MULT = BUILDER
            .comment("Bedrock Moment (Aimbot): velocity multiplier applied to skeleton arrows near you.")
            .defineInRange("bedrockAimbotVelocityMult", 2.5, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIMBOT_HOMING = BUILDER
            .comment("Bedrock Moment (Aimbot): how hard skeleton arrows curve toward you each tick.")
            .defineInRange("bedrockAimbotHoming", 0.22, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_AIMBOT_RADIUS = BUILDER
            .comment("Bedrock Moment (Aimbot): radius (blocks) within which skeleton arrows get the aimbot treatment.")
            .defineInRange("bedrockAimbotRadius", 26.0, 4.0, 96.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_FLING_CHANCE = BUILDER
            .comment("Bedrock Moment (Fling): chance a rideable entity flings itself the instant you mount it.")
            .defineInRange("bedrockFlingChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BEDROCK_FLING_FORCE = BUILDER
            .comment("Bedrock Moment (Fling): how hard the mount is flung.")
            .defineInRange("bedrockFlingForce", 1.7, 0.1, 6.0);
    public static final ModConfigSpec.IntValue BEDROCK_SERVERLAG_TICKS = BUILDER
            .comment("Bedrock Moment (Server Lag): how long (ticks) nearby entities freeze before snapping back to life.")
            .defineInRange("bedrockServerlagTicks", 16, 3, 200);
    public static final ModConfigSpec.IntValue BEDROCK_NIGHTCORE_TICKS = BUILDER
            .comment("Bedrock Moment (Nightcore): how long (ticks) game sounds play higher-pitched.")
            .defineInRange("bedrockNightcoreTicks", 110, 20, 2400);
    public static final ModConfigSpec.DoubleValue BEDROCK_NIGHTCORE_PITCH = BUILDER
            .comment("Bedrock Moment (Nightcore): pitch multiplier applied to sounds during the window.")
            .defineInRange("bedrockNightcorePitch", 1.5, 1.0, 2.0);
    public static final ModConfigSpec.IntValue BEDROCK_CHUNKREJECT_MIN_TICKS = BUILDER
            .comment("Bedrock Moment (Chunk Rejection): min time (ticks) chunks stop rendering (render distance forced low).")
            .defineInRange("bedrockChunkrejectMinTicks", 60, 10, 1200);
    public static final ModConfigSpec.IntValue BEDROCK_CHUNKREJECT_MAX_TICKS = BUILDER
            .comment("Bedrock Moment (Chunk Rejection): max time (ticks) chunks stop rendering.")
            .defineInRange("bedrockChunkrejectMaxTicks", 160, 10, 2400);

    // --- Splitscreen (curse) ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SPLITSCREEN_RANGE = BUILDER
            .comment("Splitscreen: max distance (blocks) to the partner. Beyond it the split ends and you play normally; you re-pair when someone comes back into range.")
            .defineInRange("splitscreenRange", 22.0, 2.0, 128.0);
    public static final ModConfigSpec.IntValue SPLITSCREEN_LOAD_MIN_TICKS = BUILDER
            .comment("Splitscreen: minimum length (ticks) of the fake 'entering/exiting splitscreen…' loading screen.")
            .defineInRange("splitscreenLoadMinTicks", 8, 1, 200);
    public static final ModConfigSpec.IntValue SPLITSCREEN_LOAD_MAX_TICKS = BUILDER
            .comment("Splitscreen: maximum length (ticks) of the fake loading screen (0.4–2s by default).")
            .defineInRange("splitscreenLoadMaxTicks", 40, 1, 400);
    // (Splitscreen's live-POV toggle is a CLIENT render preference — see ClientConfig.SPLITSCREEN_LIVE_POV.)

    // --- Cutaway Gag (curse) ---------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue CUTAWAY_COOLDOWN_SECONDS = BUILDER
            .comment("Cutaway Gag: minimum real seconds between cutaways. Nothing can fire during this window.")
            .defineInRange("cutawayCooldownSeconds", 350, 0, 36000);
    public static final ModConfigSpec.DoubleValue CUTAWAY_BASE_CHANCE = BUILDER
            .comment("Cutaway Gag: base % chance PER SECOND to start a cutaway once the cooldown has elapsed.")
            .defineInRange("cutawayBaseChancePercent", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_RAMP_PER_SECOND = BUILDER
            .comment("Cutaway Gag: how much the per-second chance ramps up each further second past the cooldown.")
            .defineInRange("cutawayRampPerSecondPercent", 0.1, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_MAX_CHANCE = BUILDER
            .comment("Cutaway Gag: cap on the ramping per-second chance.")
            .defineInRange("cutawayMaxChancePercent", 20.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CUTAWAY_GAG_DELAY_MIN_TICKS = BUILDER
            .comment("Cutaway Gag: minimum delay (ticks) after the camera cuts before the gag actually fires.")
            .defineInRange("cutawayGagDelayMinTicks", 10, 0, 600);
    public static final ModConfigSpec.IntValue CUTAWAY_GAG_DELAY_MAX_TICKS = BUILDER
            .comment("Cutaway Gag: maximum delay (ticks) before the gag fires (default 0.5–2.5s).")
            .defineInRange("cutawayGagDelayMaxTicks", 50, 0, 600);
    public static final ModConfigSpec.IntValue CUTAWAY_DURATION_MIN_TICKS = BUILDER
            .comment("Cutaway Gag: minimum length (ticks) you keep watching AFTER the gag fires.")
            .defineInRange("cutawayDurationMinTicks", 120, 20, 1200);
    public static final ModConfigSpec.IntValue CUTAWAY_DURATION_MAX_TICKS = BUILDER
            .comment("Cutaway Gag: maximum length (ticks) you keep watching after the gag fires (default 6–10s).")
            .defineInRange("cutawayDurationMaxTicks", 200, 20, 1200);
    public static final ModConfigSpec.IntValue CUTAWAY_HIT_END_CHANCE = BUILDER
            .comment("Cutaway Gag: % chance that taking a hit mid-cutaway snaps your camera back (the gag's effects still stick).")
            .defineInRange("cutawayHitEndChancePercent", 70, 0, 100);
    public static final ModConfigSpec.IntValue CUTAWAY_MARRIAGE_TICKS = BUILDER
            .comment("Cutaway Gag (Marriage): base length (ticks) of the wedding ceremony (normal path). The objection path runs +220 longer so it stays readable; explode/what are slightly shorter. Deliberately slow.")
            .defineInRange("cutawayMarriageTicks", 500, 80, 1200);
    public static final ModConfigSpec.DoubleValue CUTAWAY_MARRIAGE_EXPLODE_POWER = BUILDER
            .comment("Cutaway Gag (Marriage): explosion power when the 'explode' path fires (slight world damage).")
            .defineInRange("cutawayMarriageExplodePower", 2.0, 0.0, 8.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_SPECTATE_DISTANCE = BUILDER
            .comment("Cutaway Gag: horizontal distance (blocks) the overhead spectate camera sits from the victim.")
            .defineInRange("cutawaySpectateDistance", 6.0, 0.0, 24.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_CAMERA_HEIGHT = BUILDER
            .comment("Cutaway Gag: how high (blocks) above the victim the overhead spectate camera sits.")
            .defineInRange("cutawayCameraHeight", 5.0, 0.0, 24.0);
    public static final ModConfigSpec.IntValue CUTAWAY_WOOLIAM_CAP = BUILDER
            .comment("Cutaway Gag (Woolliam): max sheep the duplicating flock can reach.")
            .defineInRange("cutawayWoolliamCap", 16, 1, 120);
    public static final ModConfigSpec.IntValue CUTAWAY_DRIVEBY_COUNT = BUILDER
            .comment("Cutaway Gag (Driveby): number of skeletons in the drive-by.")
            .defineInRange("cutawayDrivebyCount", 4, 1, 12);
    public static final ModConfigSpec.IntValue CUTAWAY_JUMPED_COUNT = BUILDER
            .comment("Cutaway Gag (Jumped): number of buffed mobs that jump the victim.")
            .defineInRange("cutawayJumpedCount", 4, 1, 12);
    public static final ModConfigSpec.IntValue CUTAWAY_HOLE_DEPTH = BUILDER
            .comment("Cutaway Gag (Hole): how deep the hole is dug beneath the victim.")
            .defineInRange("cutawayHoleDepth", 14, 3, 64);
    public static final ModConfigSpec.DoubleValue CUTAWAY_LAUNCH_POWER = BUILDER
            .comment("Cutaway Gag (Launch): upward velocity of the slime-block catapult.")
            .defineInRange("cutawayLaunchPower", 2.6, 0.5, 10.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_SNAIL_SPEED = BUILDER
            .comment("Cutaway Gag (Snail): blocks/tick the fake immortal snail creeps toward the victim.")
            .defineInRange("cutawaySnailSpeed", 0.09, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_SNAIL_DAMAGE = BUILDER
            .comment("Cutaway Gag (Snail): damage of the non-terrain-damaging explosion when the snail touches you (then the gag ends).")
            .defineInRange("cutawaySnailDamage", 12.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_HELICOPTER_SPIN = BUILDER
            .comment("Cutaway Gag (Helicopter): max degrees/tick the spruce boat spins (it ramps up to this).")
            .defineInRange("cutawayHelicopterSpin", 62.0, 1.0, 180.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_HELICOPTER_RISE_MAX = BUILDER
            .comment("Cutaway Gag (Helicopter): max upward blocks/tick the ramping ascent reaches.")
            .defineInRange("cutawayHelicopterRiseMax", 0.9, 0.1, 5.0);
    public static final ModConfigSpec.IntValue CUTAWAY_TRAIN_WARNING_TICKS = BUILDER
            .comment("Cutaway Gag (I Like Trains): ticks the 'I like trains' line plays before the train rolls.",
                    "Set to roughly the length of the audio clip so it finishes just as the train starts.")
            .defineInRange("cutawayTrainWarningTicks", 34, 0, 200);
    public static final ModConfigSpec.DoubleValue CUTAWAY_TRAIN_SPEED = BUILDER
            .comment("Cutaway Gag (I Like Trains): blocks/tick the noclip train barrels along the rails.")
            .defineInRange("cutawayTrainSpeed", 7.0, 0.5, 30.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_TOKYO_SPEED = BUILDER
            .comment("Cutaway Gag (Tokyo Drifting): blocks/tick the cherry boat drifts the victim along the ground.")
            .defineInRange("cutawayTokyoSpeed", 2.6, 0.5, 10.0);
    public static final ModConfigSpec.IntValue CUTAWAY_PIED_PIPER_MIN = BUILDER
            .comment("Cutaway Gag (Pied Piper): minimum animals swarming the victim (spawns to fill the quota).")
            .defineInRange("cutawayPiedPiperMin", 4, 1, 40);
    public static final ModConfigSpec.IntValue CUTAWAY_FAKE_TNT_COUNT = BUILDER
            .comment("Cutaway Gag (Fake TNT): how many (mostly fake) TNT drop from above.")
            .defineInRange("cutawayFakeTntCount", 5, 1, 20);
    public static final ModConfigSpec.IntValue CUTAWAY_FAKE_TNT_REAL_CHANCE = BUILDER
            .comment("Cutaway Gag (Fake TNT): % chance each dropped TNT is a REAL one that actually explodes.")
            .defineInRange("cutawayFakeTntRealChancePercent", 15, 0, 100);
    public static final ModConfigSpec.IntValue CUTAWAY_ABDUCTION_HEIGHT = BUILDER
            .comment("Cutaway Gag (Abduction): how high (blocks) the UFO/tractor beam towers above the victim.")
            .defineInRange("cutawayAbductionHeight", 40, 6, 128);
    public static final ModConfigSpec.IntValue CUTAWAY_ABDUCTION_LIFT_TICKS = BUILDER
            .comment("Cutaway Gag (Abduction): how long (ticks) the beam yanks the victim upward before dropping them.")
            .defineInRange("cutawayAbductionLiftTicks", 60, 5, 400);
    public static final ModConfigSpec.IntValue CUTAWAY_ABDUCTION_LEVITATION = BUILDER
            .comment("Cutaway Gag (Abduction): Levitation amplifier — higher = sucked up faster/higher.")
            .defineInRange("cutawayAbductionLevitation", 9, 0, 127);
    public static final ModConfigSpec.IntValue CUTAWAY_AQUARIUM_COUNT = BUILDER
            .comment("Cutaway Gag (Aquarium): how many squids/fish spawn treating air like water.")
            .defineInRange("cutawayAquariumCount", 8, 1, 40);
    public static final ModConfigSpec.IntValue CUTAWAY_TRAIN_COUNT = BUILDER
            .comment("Cutaway Gag (I Like Trains): how many minecarts slam down the rails.")
            .defineInRange("cutawayTrainCount", 5, 1, 20);
    public static final ModConfigSpec.DoubleValue CUTAWAY_TRAIN_DAMAGE = BUILDER
            .comment("Cutaway Gag (I Like Trains): damage a cart deals if it hits the victim.")
            .defineInRange("cutawayTrainDamage", 24.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_BOWLING_DAMAGE = BUILDER
            .comment("Cutaway Gag (Bowling): damage the bowling ball deals to each pin it hits.")
            .defineInRange("cutawayBowlingDamage", 14.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue CUTAWAY_BOWLING_CLUSTER_MIN = BUILDER
            .comment("Cutaway Gag (Bowling): min entities clustered around the victim before Bowling gets its crowd-weighted pick.")
            .defineInRange("cutawayBowlingClusterMin", 3, 1, 100);
    public static final ModConfigSpec.DoubleValue CUTAWAY_BOWLING_CLUSTER_WEIGHT = BUILDER
            .comment("Cutaway Gag (Bowling): per-entity chance added toward picking Bowling when the victim is in a crowd (capped 70%).")
            .defineInRange("cutawayBowlingClusterWeight", 0.12, 0.0, 1.0);
    public static final ModConfigSpec.IntValue CUTAWAY_PARADE_COUNT = BUILDER
            .comment("Cutaway Gag (Parade): how many entities march by.")
            .defineInRange("cutawayParadeCount", 14, 2, 100);
    public static final ModConfigSpec.DoubleValue CUTAWAY_PARADE_SPEED = BUILDER
            .comment("Cutaway Gag (Parade): how fast the marchers walk (blocks/tick before friction).")
            .defineInRange("cutawayParadeSpeed", 0.2, 0.02, 1.0);
    public static final ModConfigSpec.DoubleValue CUTAWAY_PARADE_DAMAGE = BUILDER
            .comment("Cutaway Gag (Parade): damage a marcher deals to anything it tramples over.")
            .defineInRange("cutawayParadeDamage", 6.0, 0.0, 1000.0);

    // --- The Dweller: Possession + Behind (tier 2+ events) --------------------------------------------
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_CHANCE_DENOM = BUILDER
            .comment("Dweller (Possession): 1-in-N per-tick chance to possess a nearby passive mob (tier 2+).")
            .defineInRange("dwellerPossessChanceDenom", 1600, 1, 1000000);
    public static final ModConfigSpec.IntValue DWELLER_POSSESS_COOLDOWN = BUILDER
            .comment("Dweller (Possession): cooldown ticks after a possession before another can start.")
            .defineInRange("dwellerPossessCooldownTicks", 1200, 0, 100000);
    public static final ModConfigSpec.IntValue DWELLER_BEHIND_TURN_DEGREES = BUILDER
            .comment("Dweller (Behind): how sharp a one-tick turn (degrees) counts as a fast look.")
            .defineInRange("dwellerBehindTurnDegrees", 70, 10, 180);
    public static final ModConfigSpec.DoubleValue DWELLER_BEHIND_CHANCE = BUILDER
            .comment("Dweller (Behind): chance a fast turn briefly reveals him (tier 2+).")
            .defineInRange("dwellerBehindChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DWELLER_BEHIND_COOLDOWN = BUILDER
            .comment("Dweller (Behind): cooldown ticks between behind-you glimpses.")
            .defineInRange("dwellerBehindCooldownTicks", 200, 0, 100000);
    public static final ModConfigSpec.DoubleValue DWELLER_BEHIND_DISTANCE = BUILDER
            .comment("Dweller (Behind): how far ahead of your new look he appears (blocks).")
            .defineInRange("dwellerBehindDistance", 4.0, 1.0, 16.0);
    public static final ModConfigSpec.IntValue DWELLER_BEHIND_TICKS = BUILDER
            .comment("Dweller (Behind): how long the glimpse lasts before he vanishes (ticks).")
            .defineInRange("dwellerBehindTicks", 12, 2, 100);

    // --- Heavy Hitter blessing ------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue HEAVY_HITTER_KNOCKBACK_MULT = BUILDER
            .comment("Heavy Hitter: multiplier on your melee knockback (multiplies the final strength, so it",
                    "stacks multiplicatively with Knockback enchantments).")
            .defineInRange("heavyHitterKnockbackMultiplier", 2.0, 1.0, 10.0);

    // --- Speed Demon blessing -------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SPEED_DEMON_MOUNT_MULTIPLIER = BUILDER
            .comment("Speed Demon: movement-speed multiplier applied to any LIVING mount you ride (boats/",
                    "minecarts aren't attribute-driven, so they're not covered).")
            .defineInRange("speedDemonMountMultiplier", 2.0, 1.0, 10.0);

    // --- Narcolepsy curse -----------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue NARCOLEPSY_MIN_INTERVAL_TICKS = BUILDER
            .comment("Narcolepsy: minimum gap between sleeps (ticks). 700 = 35s.")
            .defineInRange("narcolepsyMinIntervalTicks", 700, 20, 200000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_MAX_INTERVAL_TICKS = BUILDER
            .comment("Narcolepsy: maximum gap between sleeps (ticks). 6600 = 5.5min. Biased toward the higher half.")
            .defineInRange("narcolepsyMaxIntervalTicks", 6600, 20, 200000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_MIN_SLEEP_TICKS = BUILDER
            .comment("Narcolepsy: minimum sleep length (ticks) if you don't mash out. 80 = 4s.")
            .defineInRange("narcolepsyMinSleepTicks", 80, 20, 2000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_MAX_SLEEP_TICKS = BUILDER
            .comment("Narcolepsy: maximum sleep length (ticks) if you don't mash out. 240 = 12s.")
            .defineInRange("narcolepsyMaxSleepTicks", 240, 20, 2000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_IDLE_ACCEL_TICKS = BUILDER
            .comment("Narcolepsy: after standing still this many ticks, the countdown to the next sleep runs at",
                    "DOUBLE speed (idling makes a narcoleptic nod off sooner). 60 = 3s.")
            .defineInRange("narcolepsyIdleAccelTicks", 60, 0, 12000);
    public static final ModConfigSpec.IntValue NARCOLEPSY_DEEP_CHANCE_PERCENT = BUILDER
            .comment("Narcolepsy: chance (%) a sleep is a DEEP one (needs noticeably more mashing + lasts longer).")
            .defineInRange("narcolepsyDeepChancePercent", 24, 0, 100);
    public static final ModConfigSpec.IntValue NARCOLEPSY_VERY_DEEP_CHANCE_PERCENT = BUILDER
            .comment("Narcolepsy: chance (%) a sleep is a VERY DEEP one (rarer than deep; the hardest to mash out",
                    "and the longest). Rolled only if the deep roll already passed.")
            .defineInRange("narcolepsyVeryDeepChancePercent", 33, 0, 100);

    // --- Blessing of Flight ---
    public static final ModConfigSpec.DoubleValue FLIGHT_RISE_SPEED = BUILDER
            .comment("Flight: max upward velocity (blocks/tick) while holding jump (reached after the accel buildup).")
            .defineInRange("flightRiseSpeed", 0.34, 0.05, 2.0);
    public static final ModConfigSpec.IntValue FLIGHT_ACCEL_TICKS = BUILDER
            .comment("Flight: ticks of acceleration buildup from a slow start up to the max rise speed. 20 = 1s.")
            .defineInRange("flightAccelTicks", 20, 1, 200);
    public static final ModConfigSpec.DoubleValue FLIGHT_DRAIN_PER_TICK = BUILDER
            .comment("Flight: fraction of the energy bar drained per tick while rising (0..1).")
            .defineInRange("flightDrainPerTick", 0.0032, 0.0001, 0.5);
    public static final ModConfigSpec.IntValue FLIGHT_REGEN_DELAY_TICKS = BUILDER
            .comment("Flight: delay (ticks) after you stop rising before the bar starts refilling. 8 = 0.4s.")
            .defineInRange("flightRegenDelayTicks", 8, 0, 200);
    public static final ModConfigSpec.DoubleValue FLIGHT_SPRINT_RISE_MULT = BUILDER
            .comment("Flight: while sprinting + holding a movement key, the upward rise is scaled by this (you",
                    "trade height for horizontal speed).")
            .defineInRange("flightSprintRiseMultiplier", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_SPRINT_HORIZONTAL = BUILDER
            .comment("Flight: horizontal speed (blocks/tick) when sprint-gliding forward.")
            .defineInRange("flightSprintHorizontal", 0.62, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue FLIGHT_AIRBORNE_DRAIN = BUILDER
            .comment("Flight: a small constant drain per tick just for being airborne (even when not rising), so",
                    "you can't loiter aloft forever. Refills only once you land.")
            .defineInRange("flightAirborneDrain", 0.0008, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue FLIGHT_GLIDE_DRAIN_MULT = BUILDER
            .comment("Flight: the horizontal sprint-glide drains this multiple of the normal push-up drain",
                    "(1.5 = 50% more).")
            .defineInRange("flightGlideDrainMultiplier", 1.5, 1.0, 5.0);
    public static final ModConfigSpec.IntValue FLIGHT_HOLD_TICKS = BUILDER
            .comment("Flight: jump must be HELD this many ticks before flight engages, so a quick tap is just a",
                    "normal jump (no flight, no drain). 4 = 0.2s.")
            .defineInRange("flightHoldTicks", 4, 0, 40);
    public static final ModConfigSpec.IntValue FLIGHT_AIRBORNE_GRACE_TICKS = BUILDER
            .comment("Flight: the constant airborne drain only starts after being off the ground this long, so a",
                    "natural jump never costs resource. 12 = 0.6s.")
            .defineInRange("flightAirborneGraceTicks", 12, 0, 200);
    public static final ModConfigSpec.DoubleValue FLIGHT_REGEN_PER_TICK = BUILDER
            .comment("Flight: fraction of the energy bar refilled per tick when on the ground / not rising.")
            .defineInRange("flightRegenPerTick", 0.006, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue FLIGHT_DAMAGE_COST = BUILDER
            .comment("Flight: fraction of the energy bar drained when you take damage (0.2 = 20%).")
            .defineInRange("flightDamageCost", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.IntValue FLIGHT_LOCKOUT_TICKS = BUILDER
            .comment("Flight: ticks you're knocked out of flight after taking damage. 12 = 0.6s.")
            .defineInRange("flightLockoutTicks", 12, 0, 200);

    // --- Blessing of Thunder ---
    public static final ModConfigSpec.IntValue THUNDER_TIER1_TICKS = BUILDER
            .comment("Thunder: seconds of not-swinging to reach charge tier 1/2/3/4 (in ticks).")
            .defineInRange("thunderTier1Ticks", 60, 5, 2000);
    public static final ModConfigSpec.IntValue THUNDER_TIER2_TICKS = BUILDER
            .comment("Thunder: ticks to tier 2 (5.5s).").defineInRange("thunderTier2Ticks", 110, 5, 2000);
    public static final ModConfigSpec.IntValue THUNDER_TIER3_TICKS = BUILDER
            .comment("Thunder: ticks to tier 3 (8s).").defineInRange("thunderTier3Ticks", 160, 5, 2000);
    public static final ModConfigSpec.IntValue THUNDER_TIER4_TICKS = BUILDER
            .comment("Thunder: ticks to tier 4 (13s).").defineInRange("thunderTier4Ticks", 260, 5, 4000);
    public static final ModConfigSpec.DoubleValue THUNDER_CHAIN_BASE = BUILDER
            .comment("Thunder: flat base damage every chain deals BEFORE the % of the hit is added on.")
            .defineInRange("thunderChainBase", 2.0, 0.0, 50.0);
    public static final ModConfigSpec.IntValue THUNDER_CHAIN_PERCENT = BUILDER
            .comment("Thunder: chain damage as a % of the base hit damage, added on top of thunderChainBase.")
            .defineInRange("thunderChainPercent", 30, 1, 500);
    public static final ModConfigSpec.DoubleValue THUNDER_CHAIN_CAP = BUILDER
            .comment("Thunder: hard cap on any single chain's damage.")
            .defineInRange("thunderChainCap", 12.0, 1.0, 100.0);
    public static final ModConfigSpec.DoubleValue THUNDER_CHAIN_RANGE = BUILDER
            .comment("Thunder: how far (blocks) a chain can jump to the next entity.")
            .defineInRange("thunderChainRange", 6.0, 1.0, 32.0);
    public static final ModConfigSpec.IntValue THUNDER_BURN_TICKS = BUILDER
            .comment("Thunder: burn applied to hit + chained entities (ticks). 60 = 3s.")
            .defineInRange("thunderBurnTicks", 60, 0, 600);
    public static final ModConfigSpec.DoubleValue THUNDER_TIER4_BONUS = BUILDER
            .comment("Thunder: bonus damage on the initial hit at tier 4.")
            .defineInRange("thunderTier4Bonus", 6.0, 0.0, 100.0);

    // --- Blessing of Spelunking ---
    public static final ModConfigSpec.IntValue SPELUNKING_RADIUS = BUILDER
            .comment("Spelunking: small, constant radius (blocks) to scan + highlight nearby ores.")
            .defineInRange("spelunkingRadius", 7, 3, 48);
    public static final ModConfigSpec.IntValue SPELUNKING_INTERVAL_TICKS = BUILDER
            .comment("Spelunking: how often (ticks) it re-scans and marks nearby ores (frequent = a constant glow).")
            .defineInRange("spelunkingIntervalTicks", 40, 10, 400);

    // --- Blessing of Safety ---
    public static final ModConfigSpec.IntValue SAFETY_CHANNEL_TICKS = BUILDER
            .comment("Safety: ticks of crouching still + looking down before you teleport home. 200 = 10s.")
            .defineInRange("safetyChannelTicks", 200, 20, 2000);
    public static final ModConfigSpec.IntValue SAFETY_COOLDOWN_TICKS = BUILDER
            .comment("Safety: cooldown (ticks) after a cancelled channel before you can try again. 120 = 6s.")
            .defineInRange("safetyCooldownTicks", 120, 0, 2000);
    public static final ModConfigSpec.IntValue SAFETY_USE_COOLDOWN_TICKS = BUILDER
            .comment("Safety: cooldown (ticks) after a SUCCESSFUL teleport home. 7200 = 6 minutes.")
            .defineInRange("safetyUseCooldownTicks", 7200, 0, 1728000);

    // --- Blessing of Disguise ---
    public static final ModConfigSpec.DoubleValue DISGUISE_BREAK_RADIUS = BUILDER
            .comment("Disguise: get this close (blocks) to a hostile and the costume breaks.")
            .defineInRange("disguiseBreakRadius", 3.0, 1.0, 12.0);
    public static final ModConfigSpec.IntValue DISGUISE_RETURN_TICKS = BUILDER
            .comment("Disguise: ticks you must go un-hit after a break before the costume returns. 240 = 12s.")
            .defineInRange("disguiseReturnTicks", 240, 20, 2000);

    // --- Blessing of Confusion ---
    public static final ModConfigSpec.IntValue CONFUSION_INTERVAL_TICKS = BUILDER
            .comment("Confusion: how often (ticks) a doppelganger may be emitted.")
            .defineInRange("confusionIntervalTicks", 70, 10, 600);
    public static final ModConfigSpec.IntValue CONFUSION_MAX_CLONES = BUILDER
            .comment("Confusion: max live doppelgangers at once.")
            .defineInRange("confusionMaxClones", 4, 1, 12);
    public static final ModConfigSpec.IntValue CONFUSION_CLONE_LIFETIME_TICKS = BUILDER
            .comment("Confusion: how long (ticks) a doppelganger lives before poofing.")
            .defineInRange("confusionCloneLifetimeTicks", 260, 40, 2000);

    // --- Blessing of Photosynthesis ---
    public static final ModConfigSpec.IntValue PHOTOSYNTHESIS_INTERVAL_TICKS = BUILDER
            .comment("Photosynthesis: how often (ticks) sunlight tops you up. 40 = 2s.")
            .defineInRange("photosynthesisIntervalTicks", 40, 5, 400);

    // --- Voodoo Doll / Needle ---
    public static final ModConfigSpec.IntValue VOODOO_DOLL_DURABILITY = BUILDER
            .comment("Voodoo Doll: max durability, applied when a doll is bound. Deliberately LOW — voodoo is",
                    "pretty much free damage, so a doll only lasts a few interactions.")
            .defineInRange("voodooDollDurability", 12, 1, 2000);
    public static final ModConfigSpec.DoubleValue VOODOO_UNPROTECTED_FRACTION = BUILDER
            .comment("Voodoo damage: the fraction of the hit that IGNORES armour. 0.5 = half the damage is always",
                    "unprotected, so armour helps but only half as much as against a normal hit.")
            .defineInRange("voodooUnprotectedFraction", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue VOODOO_NEEDLE_BASE_DAMAGE = BUILDER
            .comment("Voodoo Needle: jab damage (before the half-armour reduction).")
            .defineInRange("voodooNeedleBaseDamage", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue VOODOO_NEEDLE_DOLL_COST = BUILDER
            .comment("Voodoo Needle: durability spent from the doll per jab.")
            .defineInRange("voodooNeedleDollCost", 2, 1, 100);
    public static final ModConfigSpec.IntValue VOODOO_LAVA_FIRE_TICKS = BUILDER
            .comment("Voodoo Doll: how long (ticks) the bound player burns when the doll is destroyed in fire/lava.")
            .defineInRange("voodooLavaFireTicks", 160, 0, 2000);
    public static final ModConfigSpec.DoubleValue VOODOO_THROW_FORCE = BUILDER
            .comment("Voodoo Doll: how hard the victim is flung when you throw (drop) the doll.")
            .defineInRange("voodooThrowForce", 1.7, 0.0, 10.0);
    public static final ModConfigSpec.IntValue VOODOO_THROW_DOLL_COST = BUILDER
            .comment("Voodoo Doll: durability spent when you throw the doll to fling the victim (a lot).")
            .defineInRange("voodooThrowDollCost", 4, 1, 100);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_TICK_INTERVAL = BUILDER
            .comment("Voodoo Doll (squeeze): ticks between each damage/slow 'click' while holding right-click.")
            .defineInRange("voodooSqueezeTickInterval", 14, 2, 60);
    public static final ModConfigSpec.DoubleValue VOODOO_SQUEEZE_BASE_DAMAGE = BUILDER
            .comment("Voodoo Doll (squeeze): damage of the first tick; it ramps up each tick.")
            .defineInRange("voodooSqueezeBaseDamage", 1.0, 0.0, 50.0);
    public static final ModConfigSpec.DoubleValue VOODOO_SQUEEZE_RAMP = BUILDER
            .comment("Voodoo Doll (squeeze): extra damage added per successive tick.")
            .defineInRange("voodooSqueezeRamp", 0.5, 0.0, 20.0);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_DOLL_COST = BUILDER
            .comment("Voodoo Doll (squeeze): durability spent per damage tick (a lot over a full squeeze).")
            .defineInRange("voodooSqueezeDollCost", 1, 1, 100);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_COOLDOWN = BUILDER
            .comment("Voodoo Doll (squeeze): item-use cooldown (ticks) after a squeeze ends.")
            .defineInRange("voodooSqueezeCooldown", 100, 0, 2000);
    public static final ModConfigSpec.IntValue VOODOO_FEED_DOLL_COST = BUILDER
            .comment("Voodoo Doll (feeding): durability spent to feed the victim. 0 = free (it's a kindness).")
            .defineInRange("voodooFeedDollCost", 0, 0, 100);
    public static final ModConfigSpec.IntValue VOODOO_SHAKE_TICKS = BUILDER
            .comment("Voodoo Doll: camera-shake window (ticks) on the CASTER when they pin/squeeze the doll.")
            .defineInRange("voodooShakeTicks", 5, 0, 60);
    public static final ModConfigSpec.DoubleValue VOODOO_SHAKE_STRENGTH = BUILDER
            .comment("Voodoo Doll: camera-shake strength on the caster per pin/squeeze click.")
            .defineInRange("voodooShakeStrength", 0.8, 0.0, 10.0);
    public static final ModConfigSpec.IntValue VOODOO_SQUEEZE_SELF_SLOW = BUILDER
            .comment("Voodoo Doll (squeeze): Slowness amplifier on the CASTER while squeezing (movement penalty).")
            .defineInRange("voodooSqueezeSelfSlow", 2, 0, 10);
    public static final ModConfigSpec.IntValue VOODOO_POTION_DOLL_COST = BUILDER
            .comment("Voodoo Doll: durability spent per scan while a grounded doll sits in a lingering potion cloud.")
            .defineInRange("voodooPotionDollCost", 1, 0, 100);
    public static final ModConfigSpec.DoubleValue VOODOO_FISHING_FORCE = BUILDER
            .comment("Voodoo Doll: how hard the victim is flung when a FISHING ROD is used on the grounded doll",
                    "(much stronger than a throw).")
            .defineInRange("voodooFishingForce", 3.6, 0.0, 15.0);
    public static final ModConfigSpec.IntValue VOODOO_FISHING_DOLL_COST = BUILDER
            .comment("Voodoo Doll: durability spent when a fishing rod yanks the victim (a HUGE amount).")
            .defineInRange("voodooFishingDollCost", 8, 1, 200);
    public static final ModConfigSpec.DoubleValue VOODOO_FISHING_RANGE = BUILDER
            .comment("Voodoo Doll: how far (blocks) a used fishing rod looks for a bound doll in front of you.")
            .defineInRange("voodooFishingRange", 6.0, 1.0, 32.0);
    public static final ModConfigSpec.IntValue VOODOO_TABLE_DOLL_COST = BUILDER
            .comment("Voodoo Doll: durability spent when used AS a Player Essence in the Bewitching Table target",
                    "slot (the doll is returned, not consumed).")
            .defineInRange("voodooTableDollCost", 1, 0, 100);

    static final ModConfigSpec SPEC = BUILDER.build();

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

    private Config() {}
}
