package com.oliver.witchmod.effects;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.blessings.*;

/**
 * registers all the blessings. some ids differ from their display names (e.g. {@code locked_in} = "hawk guy",
 * {@code trainer} = "personal trainer") — ids are kept stable to protect saved data; names derive from lang / the id path.
 */
public final class Blessings {
    public static final DeferredHolder<Effect, BlessingFortune> FORTUNE = register("fortune", BlessingFortune::new);
    public static final DeferredHolder<Effect, BlessingPeace> PEACE = register("peace", BlessingPeace::new);
    public static final DeferredHolder<Effect, BlessingLuck> LUCK = register("luck", BlessingLuck::new);
    public static final DeferredHolder<Effect, BlessingFullness> FULLNESS = register("fullness", BlessingFullness::new);
    public static final DeferredHolder<Effect, BlessingArmy> ARMY = register("army", BlessingArmy::new);
    public static final DeferredHolder<Effect, BlessingReflect> REFLECT = register("reflect", BlessingReflect::new);
    public static final DeferredHolder<Effect, BlessingSoulBond> SOUL_BOND = register("soul_bond", BlessingSoulBond::new);
    public static final DeferredHolder<Effect, BlessingBodyguard> BODYGUARD = register("bodyguard", BlessingBodyguard::new);
    public static final DeferredHolder<Effect, BlessingPayday> PAYDAY = register("payday", BlessingPayday::new);
    public static final DeferredHolder<Effect, BlessingHypeMan> HYPE_MAN = register("hype_man", BlessingHypeMan::new);
    public static final DeferredHolder<Effect, BlessingWorkman> WORKMAN =
            register("workman", BlessingWorkman::new);
    public static final DeferredHolder<Effect, BlessingPickpocket> PICKPOCKET = register("pickpocket", BlessingPickpocket::new);
    public static final DeferredHolder<Effect, BlessingWindfall> WINDFALL = register("windfall", BlessingWindfall::new);
    public static final DeferredHolder<Effect, BlessingImmortality> IMMORTALITY = register("immortality", BlessingImmortality::new);
    public static final DeferredHolder<Effect, BlessingSixthSense> SIXTH_SENSE = register("sixth_sense", BlessingSixthSense::new);
    public static final DeferredHolder<Effect, BlessingIronStomach> IRON_STOMACH = register("iron_stomach", BlessingIronStomach::new);
    public static final DeferredHolder<Effect, BlessingIronLung> IRON_LUNG = register("iron_lung", BlessingIronLung::new);
    public static final DeferredHolder<Effect, BlessingAnchor> ANCHOR = register("anchor", BlessingAnchor::new);
    public static final DeferredHolder<Effect, BlessingTwinkletoes> TWINKLETOES = register("twinkletoes", BlessingTwinkletoes::new);
    public static final DeferredHolder<Effect, BlessingTrainer> TRAINER = register("trainer", BlessingTrainer::new);
    public static final DeferredHolder<Effect, BlessingStudious> STUDIOUS = register("studious", BlessingStudious::new);
    public static final DeferredHolder<Effect, BlessingTwistOfFate> TWIST_OF_FATE = register("twist_of_fate", BlessingTwistOfFate::new);
    public static final DeferredHolder<Effect, BlessingOrganised> ORGANISED = register("organised", BlessingOrganised::new);
    public static final DeferredHolder<Effect, BlessingNightowl> NIGHTOWL = register("nightowl", BlessingNightowl::new);
    /** renamed Steady Hands → Dexterous (id drives the display name); also speeds shield-raise/eat/drink. */
    public static final DeferredHolder<Effect, BlessingDexterous> DEXTEROUS = register("dexterous", BlessingDexterous::new);
    public static final DeferredHolder<Effect, BlessingHawkGuy> HAWK_GUY = register("hawk_guy", BlessingHawkGuy::new);
    public static final DeferredHolder<Effect, BlessingMainCharacter> MAIN_CHARACTER = register("main_character", BlessingMainCharacter::new);
    public static final DeferredHolder<Effect, BlessingJesus> JESUS = register("jesus", BlessingJesus::new);

    // the previously not-prototyped blessings
    public static final DeferredHolder<Effect, BlessingThickSkinned> THICK_SKINNED = register("thick_skinned", BlessingThickSkinned::new);
    public static final DeferredHolder<Effect, BlessingFarmersSpirit> FARMERS_SPIRIT = register("farmers_spirit", BlessingFarmersSpirit::new);
    public static final DeferredHolder<Effect, BlessingBrute> BRUTE = register("brute", BlessingBrute::new);
    public static final DeferredHolder<Effect, BlessingBlacksmith> BLACKSMITH = register("blacksmith", BlessingBlacksmith::new);
    public static final DeferredHolder<Effect, BlessingUnseen> UNSEEN = register("unseen", BlessingUnseen::new);
    public static final DeferredHolder<Effect, BlessingSilverTongue> SILVER_TONGUE = register("silver_tongue", BlessingSilverTongue::new);
    public static final DeferredHolder<Effect, BlessingHotStuff> HOT_STUFF = register("hot_stuff", BlessingHotStuff::new);
    public static final DeferredHolder<Effect, BlessingExcavation> EXCAVATION = register("excavation", BlessingExcavation::new);
    public static final DeferredHolder<Effect, BlessingAngler> ANGLER = register("angler", BlessingAngler::new);
    public static final DeferredHolder<Effect, BlessingLaughTrack> LAUGH_TRACK = register("laugh_track", BlessingLaughTrack::new);
    public static final DeferredHolder<Effect, BlessingChat> CHAT = register("chat", BlessingChat::new);
    public static final DeferredHolder<Effect, BlessingCoyote> COYOTE = register("coyote", BlessingCoyote::new);
    public static final DeferredHolder<Effect, BlessingLowGravity> LOW_GRAVITY = register("low_gravity", BlessingLowGravity::new);
    public static final DeferredHolder<Effect, BlessingBuilder> BUILDER = register("builder", BlessingBuilder::new);
    public static final DeferredHolder<Effect, BlessingBerserker> BERSERKER = register("berserker", BlessingBerserker::new);
    public static final DeferredHolder<Effect, BlessingEnchanter> ENCHANTER = register("enchanter", BlessingEnchanter::new);
    public static final DeferredHolder<Effect, BlessingPacifier> PACIFIER = register("pacifier", BlessingPacifier::new);
    public static final DeferredHolder<Effect, BlessingOceansBlessing> OCEANS_BLESSING = register("oceans_blessing", BlessingOceansBlessing::new);
    public static final DeferredHolder<Effect, BlessingGladiator> GLADIATOR = register("gladiator", BlessingGladiator::new);
    public static final DeferredHolder<Effect, BlessingCow> COW = register("cow", BlessingCow::new);
    public static final DeferredHolder<Effect, BlessingTank> TANK = register("tank", BlessingTank::new);
    public static final DeferredHolder<Effect, BlessingSpeed> SPEED = register("speed", BlessingSpeed::new);
    public static final DeferredHolder<Effect, BlessingForgiveness> FORGIVENESS = register("forgiveness", BlessingForgiveness::new);
    public static final DeferredHolder<Effect, BlessingDrive> DRIVE = register("drive", BlessingDrive::new);
    public static final DeferredHolder<Effect, BlessingVeinMiner> VEIN_MINER = register("vein_miner", BlessingVeinMiner::new);
    public static final DeferredHolder<Effect, BlessingCollector> COLLECTOR = register("collector", BlessingCollector::new);
    public static final DeferredHolder<Effect, BlessingRestock> RESTOCK = register("restock", BlessingRestock::new);
    public static final DeferredHolder<Effect, BlessingSanguine> SANGUINE = register("sanguine", BlessingSanguine::new);
    public static final DeferredHolder<Effect, BlessingHomebody> HOMEBODY = register("homebody", BlessingHomebody::new);
    public static final DeferredHolder<Effect, BlessingSonar> SONAR = register("sonar", BlessingSonar::new);
    public static final DeferredHolder<Effect, BlessingSpider> SPIDER = register("spider", BlessingSpider::new);
    public static final DeferredHolder<Effect, BlessingNinja> NINJA = register("ninja", BlessingNinja::new);
    public static final DeferredHolder<Effect, BlessingBackstabbing> BACKSTABBING = register("backstabbing", BlessingBackstabbing::new);
    public static final DeferredHolder<Effect, BlessingPropHunt> PROP_HUNT = register("prop_hunt", BlessingPropHunt::new);
    public static final DeferredHolder<Effect, BlessingLastStand> LAST_STAND = register("last_stand", BlessingLastStand::new);
    public static final DeferredHolder<Effect, BlessingHeavyHitter> HEAVY_HITTER = register("heavy_hitter", BlessingHeavyHitter::new);
    public static final DeferredHolder<Effect, BlessingSpeedDemon> SPEED_DEMON = register("speed_demon", BlessingSpeedDemon::new);
    public static final DeferredHolder<Effect, BlessingFlight> FLIGHT = register("flight", BlessingFlight::new);
    public static final DeferredHolder<Effect, BlessingThunder> THUNDER = register("thunder", BlessingThunder::new);
    public static final DeferredHolder<Effect, BlessingSpelunking> SPELUNKING = register("spelunking", BlessingSpelunking::new);
    public static final DeferredHolder<Effect, BlessingSafety> SAFETY = register("safety", BlessingSafety::new);
    public static final DeferredHolder<Effect, BlessingDisguise> DISGUISE = register("disguise", BlessingDisguise::new);
    public static final DeferredHolder<Effect, BlessingConfusion> CONFUSION = register("confusion", BlessingConfusion::new);
    public static final DeferredHolder<Effect, BlessingPhotosynthesis> PHOTOSYNTHESIS = register("photosynthesis", BlessingPhotosynthesis::new);

    // new blessing batch (2026-09-02).
    public static final DeferredHolder<Effect, BlessingLeader> LEADER = register("leader", BlessingLeader::new);
    public static final DeferredHolder<Effect, BlessingUnderdog> UNDERDOG = register("underdog", BlessingUnderdog::new);
    public static final DeferredHolder<Effect, BlessingBloodhound> BLOODHOUND = register("bloodhound", BlessingBloodhound::new);
    public static final DeferredHolder<Effect, BlessingGuardianAngel> GUARDIAN_ANGEL = register("guardian_angel", BlessingGuardianAngel::new);

    private Blessings() {}

    private static <T extends Effect> DeferredHolder<Effect, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EFFECTS.register(name, factory);
    }

    /** forces this class to load (and thus register its blessings) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
