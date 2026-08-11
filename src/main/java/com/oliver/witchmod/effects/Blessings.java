package com.oliver.witchmod.effects;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.blessings.*;

/**
 * Registers the blessings (master-spec Section 6; Company was CUT on Oliver's call). {@code locked_in} was renamed to {@code hawk_guy}
 * (Target Block) so its display name reads "Hawk Guy" (names derive from the id path); {@code trainer} is
 * still the prototype id for "Personal Trainer" — ids kept stable to
 * avoid breaking saved data, display names are a lang concern. (Workman was renamed from its old
 * {@code tools_dont_use_durability} id to {@code workman}.) The final block is Phase A's newly-built blessings.
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
    public static final DeferredHolder<Effect, BlessingSteadyHands> STEADY_HANDS = register("steady_hands", BlessingSteadyHands::new);
    public static final DeferredHolder<Effect, BlessingHawkGuy> HAWK_GUY = register("hawk_guy", BlessingHawkGuy::new);
    public static final DeferredHolder<Effect, BlessingMainCharacter> MAIN_CHARACTER = register("main_character", BlessingMainCharacter::new);
    public static final DeferredHolder<Effect, BlessingJesus> JESUS = register("jesus", BlessingJesus::new);

    // Phase A (master-spec Section 16): the 15 previously NOT-PROTOTYPED blessings, now built to the same
    // loosely-functional/command-startable bar as the rest.
    public static final DeferredHolder<Effect, BlessingThickSkinned> THICK_SKINNED = register("thick_skinned", BlessingThickSkinned::new);
    public static final DeferredHolder<Effect, BlessingFarmersSpirit> FARMERS_SPIRIT = register("farmers_spirit", BlessingFarmersSpirit::new);
    public static final DeferredHolder<Effect, BlessingBrute> BRUTE = register("brute", BlessingBrute::new);
    public static final DeferredHolder<Effect, BlessingBlacksmith> BLACKSMITH = register("blacksmith", BlessingBlacksmith::new);
    public static final DeferredHolder<Effect, BlessingUnseen> UNSEEN = register("unseen", BlessingUnseen::new);
    public static final DeferredHolder<Effect, BlessingSilverTongue> SILVER_TONGUE = register("silver_tongue", BlessingSilverTongue::new);
    public static final DeferredHolder<Effect, BlessingHotStuff> HOT_STUFF = register("hot_stuff", BlessingHotStuff::new);
    public static final DeferredHolder<Effect, BlessingBouncy> BOUNCY = register("bouncy", BlessingBouncy::new);
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
    public static final DeferredHolder<Effect, BlessingSpider> SPIDER = register("spider", BlessingSpider::new);
    public static final DeferredHolder<Effect, BlessingNinja> NINJA = register("ninja", BlessingNinja::new);
    public static final DeferredHolder<Effect, BlessingBackstabbing> BACKSTABBING = register("backstabbing", BlessingBackstabbing::new);
    public static final DeferredHolder<Effect, BlessingPropHunt> PROP_HUNT = register("prop_hunt", BlessingPropHunt::new);
    public static final DeferredHolder<Effect, BlessingLastStand> LAST_STAND = register("last_stand", BlessingLastStand::new);

    private Blessings() {}

    private static <T extends Effect> DeferredHolder<Effect, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EFFECTS.register(name, factory);
    }

    /** Forces this class to load (and thus register its blessings) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
