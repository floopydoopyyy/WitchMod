package com.oliver.witchmod.effects;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.curses.*;
import com.oliver.witchmod.effects.curses.dweller.CurseTheDweller;
import com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment;

/**
 * registers all the curses. display names derive from the id path, so a rename changes the visible name (and
 * drops any saved instance of the old id — harmless for placeholders).
 */
public final class Curses {
    public static final DeferredHolder<Effect, CurseViolence> VIOLENCE = register("violence", CurseViolence::new);
    public static final DeferredHolder<Effect, CurseButterfingers> BUTTERFINGERS = register("butterfingers", CurseButterfingers::new);
    public static final DeferredHolder<Effect, CurseExplosive> EXPLOSIVE = register("martyrdom", CurseExplosive::new);
    public static final DeferredHolder<Effect, CursePopularity> POPULARITY = register("popularity", CursePopularity::new);
    public static final DeferredHolder<Effect, CurseYap> YAP = register("yap", CurseYap::new);
    public static final DeferredHolder<Effect, CurseUnhygienic> UNHYGIENIC = register("unhygienic", CurseUnhygienic::new);
    public static final DeferredHolder<Effect, CurseRepel> REPEL = register("repel", CurseRepel::new);
    public static final DeferredHolder<Effect, CurseEchoes> ECHOES = register("echoes", CurseEchoes::new);
    public static final DeferredHolder<Effect, CurseDelusions> DELUSIONS = register("delusions", CurseDelusions::new);
    public static final DeferredHolder<Effect, CurseGluttony> GLUTTONY = register("gluttony", CurseGluttony::new);
    public static final DeferredHolder<Effect, CurseGassy> GASSY = register("gassy", CurseGassy::new);
    public static final DeferredHolder<Effect, CurseFarmhand> FARMHAND = register("farmhand", CurseFarmhand::new);
    /** dense = the merged Heavy + Heavyweight (see {@link CurseDense}). The old {@code heavy}/{@code heavyweight} ids are retired. */
    public static final DeferredHolder<Effect, CurseDense> DENSE = register("dense", CurseDense::new);
    public static final DeferredHolder<Effect, CurseSlipperyFeet> SLIPPERY_FEET = register("slippery_feet", CurseSlipperyFeet::new);
    public static final DeferredHolder<Effect, CurseMagnet> MAGNET = register("magnet", CurseMagnet::new);
    public static final DeferredHolder<Effect, CurseNeutralAggression> NEUTRAL_AGGRESSION =
            register("neutral_aggression", CurseNeutralAggression::new);
    public static final DeferredHolder<Effect, CurseDwarfism> DWARFISM = register("dwarfism", CurseDwarfism::new);
    public static final DeferredHolder<Effect, CurseScreensaver> SCREENSAVER = register("screensaver", CurseScreensaver::new);
    public static final DeferredHolder<Effect, CurseMinorInconvenience> MINOR_INCONVENIENCE = register("minor_inconvenience", CurseMinorInconvenience::new);
    public static final DeferredHolder<Effect, CurseThirstMeter> THIRST_METER = register("thirst_meter", CurseThirstMeter::new);
    public static final DeferredHolder<Effect, CurseSocialOutcast> SOCIAL_OUTCAST = register("social_outcast", CurseSocialOutcast::new);
    public static final DeferredHolder<Effect, CurseFloorIsLava> FLOOR_IS_LAVA = register("floor_is_lava", CurseFloorIsLava::new);
    public static final DeferredHolder<Effect, CurseBadSwimmer> BAD_SWIMMER = register("bad_swimmer", CurseBadSwimmer::new);
    public static final DeferredHolder<Effect, CursePests> PESTS = register("pests", CursePests::new);
    public static final DeferredHolder<Effect, CurseAllergic> ALLERGIC = register("allergic", CurseAllergic::new);
    public static final DeferredHolder<Effect, CurseComicRelief> COMIC_RELIEF = register("comic_relief", CurseComicRelief::new);
    public static final DeferredHolder<Effect, CurseUgly> UGLY = register("ugly", CurseUgly::new);
    public static final DeferredHolder<Effect, CurseAudit> AUDIT = register("audit", CurseAudit::new);
    public static final DeferredHolder<Effect, CurseSticky> STICKY = register("sticky", CurseSticky::new);
    public static final DeferredHolder<Effect, CurseBackseatDriver> BACKSEAT_DRIVER = register("backseat_driver", CurseBackseatDriver::new);
    public static final DeferredHolder<Effect, CurseClumsy> CLUMSY = register("clumsy", CurseClumsy::new);
    public static final DeferredHolder<Effect, CurseOversharer> OVERSHARER = register("oversharer", CurseOversharer::new);
    public static final DeferredHolder<Effect, CurseBrokenBonds> BROKEN_BONDS = register("broken_bonds", CurseBrokenBonds::new);
    public static final DeferredHolder<Effect, CurseInsomniac> INSOMNIAC = register("insomniac", CurseInsomniac::new);
    public static final DeferredHolder<Effect, CurseFlatFooted> FLAT_FOOTED = register("flat_footed", CurseFlatFooted::new);
    public static final DeferredHolder<Effect, CurseWonky> WONKY = register("wonky", CurseWonky::new);
    public static final DeferredHolder<Effect, CurseStickDrift> STICK_DRIFT = register("stick_drift", CurseStickDrift::new);
    public static final DeferredHolder<Effect, CurseBasementDweller> BASEMENT_DWELLER = register("basement_dweller", CurseBasementDweller::new);
    public static final DeferredHolder<Effect, CurseGlassCannon> GLASS_CANNON = register("glass_cannon", CurseGlassCannon::new);
    public static final DeferredHolder<Effect, CurseGiant> GIANT = register("giant", CurseGiant::new);
    public static final DeferredHolder<Effect, CurseBouncy> BOUNCY = register("bouncy", CurseBouncy::new);
    public static final DeferredHolder<Effect, CurseHeavyHanded> HEAVY_HANDED = register("klutz", CurseHeavyHanded::new);

    // the previously not-prototyped curses
    public static final DeferredHolder<Effect, CurseSuperExplosive> SUPER_EXPLOSIVE = register("volatile", CurseSuperExplosive::new);
    public static final DeferredHolder<Effect, CurseClaustrophobia> CLAUSTROPHOBIA = register("claustrophobia", CurseClaustrophobia::new);
    public static final DeferredHolder<Effect, CurseMoonwalker> MOONWALKER = register("moonwalker", CurseMoonwalker::new);
    public static final DeferredHolder<Effect, CurseSirensCall> SIRENS_CALL = register("sirens_call", CurseSirensCall::new);
    public static final DeferredHolder<Effect, CurseLoadingScreen> LOADING_SCREEN = register("loading_screen", CurseLoadingScreen::new);
    public static final DeferredHolder<Effect, CursePacing> PACING = register("pacing", CursePacing::new);
    public static final DeferredHolder<Effect, CurseTrumpet> TRUMPET = register("trumpet", CurseTrumpet::new);
    public static final DeferredHolder<Effect, CurseSolicitor> SOLICITOR = register("solicitor", CurseSolicitor::new);
    public static final DeferredHolder<Effect, CurseSnail> SNAIL = register("snail", CurseSnail::new);
    public static final DeferredHolder<Effect, CurseTheDweller> THE_DWELLER = register("haunted", CurseTheDweller::new);
    public static final DeferredHolder<Effect, CurseBedrockMoment> BEDROCK_MOMENT = register("bedrock_moment", CurseBedrockMoment::new);

    public static final DeferredHolder<Effect, CurseSplitscreen> SPLITSCREEN = register("splitscreen", CurseSplitscreen::new);

    public static final DeferredHolder<Effect, CurseCutawayGag> CUTAWAY_GAG = register("cutaway_gag", CurseCutawayGag::new);
    public static final DeferredHolder<Effect, CurseCarelessness> CARELESSNESS = register("carelessness", CurseCarelessness::new);
    public static final DeferredHolder<Effect, CurseNarcolepsy> NARCOLEPSY = register("narcolepsy", CurseNarcolepsy::new);

    // later curse ideas
    public static final DeferredHolder<Effect, CurseLightweight> LIGHTWEIGHT = register("lightweight", CurseLightweight::new);
    public static final DeferredHolder<Effect, CurseMunchies> MUNCHIES = register("munchies", CurseMunchies::new);
    public static final DeferredHolder<Effect, CurseSpotlight> SPOTLIGHT = register("spotlight", CurseSpotlight::new);
    public static final DeferredHolder<Effect, CurseHiccups> HICCUPS = register("hiccups", CurseHiccups::new);
    public static final DeferredHolder<Effect, CurseBodySwapping> BODY_SWAPPING = register("body_swapping", CurseBodySwapping::new);
    public static final DeferredHolder<Effect, CurseLeftHanded> LEFT_HANDED = register("left_handed", CurseLeftHanded::new);
    public static final DeferredHolder<Effect, CurseIceSkates> ICE_SKATES = register("ice_skates", CurseIceSkates::new);
    public static final DeferredHolder<Effect, CurseVertigo> VERTIGO = register("vertigo", CurseVertigo::new);
    public static final DeferredHolder<Effect, CurseChannels> CHANNELS = register("channels", CurseChannels::new);
    public static final DeferredHolder<Effect, CurseNarrator> NARRATOR = register("narrator", CurseNarrator::new);

    // hidden internal attachments applied by the Slime Ball / Slime Block MODIFIERS (not selectable/castable).
    public static final DeferredHolder<Effect, CurseInfectious> INFECTIOUS = register("infectious", CurseInfectious::new);
    public static final DeferredHolder<Effect, CurseVeryInfectious> VERY_INFECTIOUS = register("very_infectious", CurseVeryInfectious::new);

    private Curses() {}

    private static <T extends Effect> DeferredHolder<Effect, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EFFECTS.register(name, factory);
    }

    /** forces this class to load (and thus register its curses) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
