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

    public static final ModConfigSpec.BooleanValue GLOBALS_ENABLED = BUILDER
            .comment("Whether Global events (server-wide, bank-funded) can be triggered.")
            .define("globalsEnabled", true);

    public static final ModConfigSpec.BooleanValue BACKFIRES_ENABLED = BUILDER
            .comment("Whether a failed Table ritual can backfire onto the caster. When false, that",
                    "probability mass falls through to a Neutral miss instead.")
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

    public static final ModConfigSpec.IntValue GLOBAL_BASE_CHANCE_PERCENT = BUILDER
            .comment("Globals share ONE server-wide cooldown (master-spec Section 8). This is the base ceiling",
                    "the shared success chance climbs toward — \"very low base odds\". The chance a player's",
                    "global attempt succeeds is this * (how far the shared charge has recharged).")
            .defineInRange("globalBaseChancePercent", 15, 0, 100);

    public static final ModConfigSpec.IntValue GLOBAL_RECHARGE_HOURS = BUILDER
            .comment("Hours of world runtime for the shared global success chance to climb from 0 back up to",
                    "globalBaseChancePercent after a global fires. 0 = always at the base chance (no cooldown).")
            .defineInRange("globalRechargeHours", 3, 0, 168);

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
