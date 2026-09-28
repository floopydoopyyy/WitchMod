package com.oliver.witchmod.synergy;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.effects.Curses;

/**
 * the registry of attachment synergies — pairs of effects that gain an extra behaviour while one player holds
 * both. each is a live {@link Synergy#activeFor} check made by the participating effect, so a synergy switches
 * off the instant either half is removed. add one here, then have both effects (or one of them) query it.
 *
 * <p>this is the single home for these cross-effect relationships; the actual behaviour still lives in the
 * effect it modifies, gated on the matching field below.
 */
public final class Synergies {
    private static final List<Synergy> ALL = new ArrayList<>();

    /** backseat driver whips your mounts along faster while speed demon is also on you. */
    public static final Synergy SPEEDY_BACKSEAT = register("speedy_backseat",
            Curses.BACKSEAT_DRIVER, Blessings.SPEED_DEMON,
            "backseat driver drives hijacked mounts faster while speed demon is active");

    /** the swarm feeds the swarm: pests crawl out far more often while popularity is drawing a crowd. */
    public static final Synergy VIRAL_INFESTATION = register("viral_infestation",
            Curses.PESTS, Curses.POPULARITY,
            "pests spawn much more often while popularity is active");

    /** popularity's horde also conjures neutral mobs (endermen, ...) while neutral aggression can turn them on you. */
    public static final Synergy AGGRO_HORDE = register("aggro_horde",
            Curses.POPULARITY, Curses.NEUTRAL_AGGRESSION,
            "popularity also conjures neutral mobs while neutral aggression is active");

    /** a leader who commands a legion: leader's buff also lifts your army's defending mobs. */
    public static final Synergy RALLYING_LEADER = register("rallying_leader",
            Blessings.LEADER, Blessings.ARMY,
            "leader also buffs your defending mobs while army is active");

    /** body swapping can trade places with your own confusion clones, not just players. */
    public static final Synergy SWAP_DECOYS = register("swap_decoys",
            Curses.BODY_SWAPPING, Blessings.CONFUSION,
            "body swapping can swap you with your confusion clones");

    /** a fart while you already reek kicks up an extra stink cloud that shoves players and scatters mobs. */
    public static final Synergy RANK_GAS = register("rank_gas",
            Curses.GASSY, Curses.UNHYGIENIC,
            "gassy farts kick up an extra stink cloud that pushes players and makes mobs flee while unhygienic is active");

    /** thick skinned's nullification takes priority over glass cannon: small hits are negated, not doubled. */
    public static final Synergy TEMPERED_GLASS = register("tempered_glass",
            Curses.GLASS_CANNON, Blessings.THICK_SKINNED,
            "glass cannon skips doubling a hit small enough for thick skinned to negate, so nullification wins");

    /** luck sweetens the wind: windfall drifts better items and can unlock a special high-tier pool. */
    public static final Synergy FORTUNATE_WINDS = register("fortunate_winds",
            Blessings.WINDFALL, Blessings.LUCK,
            "windfall drops the junk and can float a special high-tier item while luck is active");

    /** a churning gut: a hiccup FIT can also let a fart slip while gassy is active. */
    public static final Synergy GUT_TROUBLE = register("gut_trouble",
            Curses.HICCUPS, Curses.GASSY,
            "a hiccup fit can also trigger a fart while gassy is active");

    /** nature runs wild: drive breeds nearby pairs with no food and the babies are conscripted straight into farmhand. */
    public static final Synergy NATURE = register("nature",
            Blessings.DRIVE, Curses.FARMHAND,
            "drive auto-breeds nearby pairs and the babies are instantly recruited to farmhand while both are active");

    /** a runaway freight train: speed makes brute charge quicker and hit harder. */
    public static final Synergy SPRINTING = register("sprinting",
            Blessings.BRUTE, Blessings.SPEED,
            "brute charges quicker and ploughs harder while a speed blessing is active");

    /** rubber in low gravity: bounces rebound noticeably harder. */
    public static final Synergy BOUNCINESS = register("bounciness",
            Curses.BOUNCY, Blessings.LOW_GRAVITY,
            "bouncy rebounds are stronger while low gravity is active");

    /** a duellist's flourish: reflected projectiles fly further while gladiator is also on you. */
    public static final Synergy DUELISTS = register("duelists",
            Blessings.GLADIATOR, Blessings.REFLECT,
            "reflect sends projectiles back faster (further) while gladiator is active");

    /** every second life readies fortune again: a resurrect refreshes twist of fate. */
    public static final Synergy REBIRTH = register("rebirth",
            Blessings.IMMORTALITY, Blessings.TWIST_OF_FATE,
            "an immortality/last-stand revive refreshes twist of fate's dodge while both are active");

    /** livestock rush: drive multiplies how fast farmer's spirit grows up nearby babies. */
    public static final Synergy GROWTH_SPURT = register("growth_spurt",
            Blessings.FARMERS_SPIRIT, Blessings.DRIVE,
            "farmer's spirit grows nearby babies up faster while drive is active");

    /** a real vampire: while disguised, sanguine turns the costume into a bat and grants free flight. */
    public static final Synergy VAMPIRE_BAT = register("vampire_bat",
            Blessings.SANGUINE, Blessings.DISGUISE,
            "disguise becomes a flying bat while sanguine is active (flight lost the instant the disguise breaks)");

    /** sticky counters butterfingers: a fumble is caught with a squelch instead of dropping the item. */
    public static final Synergy STUCK_FINGERS = register("stuck_fingers",
            Curses.BUTTERFINGERS, Curses.STICKY,
            "sticky catches a butterfingers fumble (swing + squelch, nothing dropped)");

    /** overkill from the heavens: thunder turns comic relief's killing bolt into a rapid multi-strike barrage. */
    public static final Synergy SMITING = register("smiting",
            Blessings.THUNDER, Curses.COMIC_RELIEF,
            "comic relief's killing bolt becomes a quick multi-strike barrage while thunder is active (kills the same, just funnier)");

    /** a walking lightning rod: magnet draws real strikes onto you during a thunderstorm while thunder is active. */
    public static final Synergy MAGNETIC_STORM = register("magnetic_storm",
            Curses.MAGNET, Blessings.THUNDER,
            "real lightning is drawn onto you in a thunderstorm while magnet + thunder are both active");

    /** the narrator won't let the insomnia go uncommented. */
    public static final Synergy NARRATED_SLEEPLESSNESS = register("narrated_sleeplessness",
            Curses.NARRATOR, Curses.INSOMNIAC,
            "the narrator remarks on your lack of sleep while insomniac is active");

    /** the narrator scores the trumpet gag: comments on the fanfare as you walk/sprint. */
    public static final Synergy NARRATED_TRUMPET = register("narrated_trumpet",
            Curses.NARRATOR, Curses.TRUMPET,
            "the narrator comments on the trumpet as you walk/sprint while trumpet is active");

    /** prop hunt + disguise cascade: prop first, then animal, then player — each change vanishes you briefly. */
    public static final Synergy CONCEALMENT = register("concealment",
            Blessings.PROP_HUNT, Blessings.DISGUISE,
            "prop takes priority; a hit/proximity knocks prop->animal->player, each change puffing you invisible briefly");

    /** a shadow thief: pickpocketing from BEHIND is far likelier while unseen. */
    public static final Synergy THIEVING_SHADOW = register("thieving_shadow",
            Blessings.PICKPOCKET, Blessings.UNSEEN,
            "pickpocket rate x2 when behind someone while unseen");

    /** a disguised thief: even likelier to lift while wearing a form (prop/animal). */
    public static final Synergy THIEVING_DECOY = register("thieving_decoy",
            Blessings.PICKPOCKET, Blessings.DISGUISE,
            "pickpocket rate x3 when behind someone while disguised as a prop/entity");

    /** the cow committee: with a disguise you're always, definitively, a cow. */
    public static final Synergy COW_COSTUME = register("cow_costume",
            Blessings.COW, Blessings.DISGUISE,
            "the disguise is always a cow while cow is active");

    /** a silver-tongued villager: the disguise becomes a villager and trades sometimes refund. */
    public static final Synergy SILVER_VILLAGER = register("silver_villager",
            Blessings.SILVER_TONGUE, Blessings.DISGUISE,
            "the disguise becomes a villager and trades have a small refund chance while silver tongue is active");

    /** a live studio audience: your yapped lines set the laugh track off as if you typed them. */
    public static final Synergy COMEDIC_TIMING = register("comedic_timing",
            Curses.YAP, Blessings.LAUGH_TRACK,
            "yap's chat lines trigger the laugh track as if typed");

    /** home safe: a safety teleport home lands you with a burst of Regeneration. */
    public static final Synergy HOMECOMING = register("homecoming",
            Blessings.HOMEBODY, Blessings.SAFETY,
            "a safety teleport grants Regeneration 10 for 3s while homebody is active");

    /** doubly slippery: slippery feet's shove arrives twice as fast on the ice. */
    public static final Synergy SLICK_FEET = register("slick_feet",
            Curses.ICE_SKATES, Curses.SLIPPERY_FEET,
            "slippery feet's crouch slip timer is halved while ice skates is active");

    /** a greased mount: speed demon's steed slides AND goes faster (stacks with other mount speed). */
    public static final Synergy GREASED_MOUNT = register("greased_mount",
            Curses.ICE_SKATES, Blessings.SPEED_DEMON,
            "your mount also skids and gains extra speed (stacks) while both are active");

    /** layered hide: tank's bulk raises thick skinned's negation floor. */
    public static final Synergy LAYERED_HIDE = register("layered_hide",
            Blessings.THICK_SKINNED, Blessings.TANK,
            "thick skinned's negation threshold is +1 while tank is active");

    /** dead weight: dense + bad swimmer drops you to the bottom like a stone. */
    public static final Synergy DEAD_WEIGHT = register("dead_weight",
            Curses.DENSE, Curses.BAD_SWIMMER,
            "you sink to the bottom of water like a stone while both are active");

    /** a scorched forge: floor is lava fuels hot stuff — furnaces cook far faster. */
    public static final Synergy SCORCHED_FORGE = register("scorched_forge",
            Curses.FLOOR_IS_LAVA, Blessings.HOT_STUFF,
            "hot stuff's furnace speed is doubled while floor is lava is active");

    /** dizzy heights: wonky + vertigo lowers the height threshold and strengthens the sway. */
    public static final Synergy DIZZY_HEIGHTS = register("dizzy_heights",
            Curses.WONKY, Curses.VERTIGO,
            "vertigo kicks in lower and the sway is stronger (even standing still) while wonky is active");

    /** a killing frenzy: violence's stolen swings feed berserker for free (they never miss). */
    public static final Synergy FRENZY = register("frenzy",
            Blessings.BERSERKER, Curses.VIOLENCE,
            "violence swings more often and gain extra berserker stacks (free, since they never miss)");

    /** a duellist's rhythm: a gladiator parry banks a chunk of berserker stacks. */
    public static final Synergy PARRY_FRENZY = register("parry_frenzy",
            Blessings.BERSERKER, Blessings.GLADIATOR,
            "a successful parry adds 3 berserker stacks while both are active");

    /** an identity crisis: giant + dwarfism can't agree on a size, so you flicker between huge and tiny. */
    public static final Synergy SIZE_CRISIS = register("size_crisis",
            Curses.GIANT, Curses.DWARFISM,
            "you randomly oscillate between giant and dwarf, taking on whichever curse's traits you're currently sized as");

    /** an actual spider: the disguise becomes a spider (matching the wall-climb) while spider is active. */
    public static final Synergy SPIDER_DISGUISE = register("spider_disguise",
            Blessings.SPIDER, Blessings.DISGUISE,
            "disguise becomes a spider while the spider blessing is active");

    /** duplicates share your look: confusion clones adopt whatever form (prop/animal) you're currently wearing. */
    public static final Synergy WILD_DECOYS = register("wild_decoys",
            Blessings.CONFUSION, Blessings.DISGUISE,
            "confusion clones copy your current prop/disguise form (and vanish with you on a form change)");

    /** a streamer's tic: yap sometimes addresses 'chat' out loud while the chat overlay blessing is running. */
    public static final Synergy STREAMER_BRAIN = register("streamer_brain",
            Curses.YAP, Blessings.CHAT,
            "yap sometimes talks to 'chat' while the chat blessing is active");

    /** a war council: under attack the angel super-buffs your bodyguard into a frenzy, and leaves a softer one on its death. */
    public static final Synergy GUARDED_ALLY = register("guarded_ally",
            Blessings.GUARDIAN_ANGEL, Blessings.BODYGUARD,
            "the guardian angel frenzies your bodyguard when you're attacked, and a softer frenzy when the angel dies");

    /** an angel's grudge: the angel won't purge the solicitor, it hunts the trader down and kills it, forcing a long cooldown. */
    public static final Synergy ANGELS_GRUDGE = register("angels_grudge",
            Blessings.GUARDIAN_ANGEL, Curses.SOLICITOR,
            "the guardian angel can't purge the solicitor, but after a hidden irritation timer it kidnaps and kills the trader, forcing it onto a long cooldown");

    /** a perfect hider: ugly + disguise makes you look like a random OTHER online player (or your normal self if alone). */
    public static final Synergy UGLY_DISGUISE = register("ugly_disguise",
            Curses.UGLY, Blessings.DISGUISE,
            "the disguise becomes a random online player (skin + nametag), or your normal self if alone — a hit reveals the ugly skin");

    private Synergies() {}

    private static Synergy register(String id, DeferredHolder<Effect, ?> first,
                                    DeferredHolder<Effect, ?> second, String description) {
        Synergy synergy = new Synergy(id, first, second, description);
        ALL.add(synergy);
        return synergy;
    }

    public static List<Synergy> all() {
        return ALL;
    }
}
