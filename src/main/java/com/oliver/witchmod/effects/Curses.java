package com.oliver.witchmod.effects;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.curses.*;

/**
 * Registers all 49 curses (master-spec Section 5). Some still carry the prototype's original ids where the
 * master spec later renamed them (loud = "Flat Footed", pidgeon_toed = "Wonky", sick_of_you = "Broken
 * Bonds"). Those are renamed AS EACH ONE IS REFINED rather than in bulk, because display names are derived
 * from the id path ({@code DiscoveryManager.titleCase}) — so the id IS the visible name, and leaving it
 * stale would leave the wrong name on screen. Renaming drops any saved instance of the old id as unknown,
 * which is harmless for a placeholder that was never shipped.
 */
public final class Curses {
    public static final DeferredHolder<Effect, CurseViolence> VIOLENCE = register("violence", CurseViolence::new);
    public static final DeferredHolder<Effect, CurseButterfingers> BUTTERFINGERS = register("butterfingers", CurseButterfingers::new);
    public static final DeferredHolder<Effect, CurseExplosive> EXPLOSIVE = register("explosive", CurseExplosive::new);
    public static final DeferredHolder<Effect, CursePopularity> POPULARITY = register("popularity", CursePopularity::new);
    public static final DeferredHolder<Effect, CurseYap> YAP = register("yap", CurseYap::new);
    public static final DeferredHolder<Effect, CurseUnhygienic> UNHYGIENIC = register("unhygienic", CurseUnhygienic::new);
    public static final DeferredHolder<Effect, CurseRepel> REPEL = register("repel", CurseRepel::new);
    public static final DeferredHolder<Effect, CurseEchoes> ECHOES = register("echoes", CurseEchoes::new);
    public static final DeferredHolder<Effect, CurseDelusions> DELUSIONS = register("delusions", CurseDelusions::new);
    public static final DeferredHolder<Effect, CurseGluttony> GLUTTONY = register("gluttony", CurseGluttony::new);
    public static final DeferredHolder<Effect, CurseGassy> GASSY = register("gassy", CurseGassy::new);
    public static final DeferredHolder<Effect, CurseFarmhand> FARMHAND = register("farmhand", CurseFarmhand::new);
    public static final DeferredHolder<Effect, CurseHeavy> HEAVY = register("heavy", CurseHeavy::new);
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
    public static final DeferredHolder<Effect, CurseHeavyweight> HEAVYWEIGHT = register("heavyweight", CurseHeavyweight::new);
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
    public static final DeferredHolder<Effect, CurseHeavyHanded> HEAVY_HANDED = register("heavy_handed", CurseHeavyHanded::new);
    public static final DeferredHolder<Effect, CurseMansplainer> MANSPLAINER = register("mansplainer", CurseMansplainer::new);

    // Phase A (master-spec Section 16): the 7 previously NOT-PROTOTYPED curses, now built to the same
    // loosely-functional/command-startable bar as the rest. (Heavy Handed was already present above.)
    public static final DeferredHolder<Effect, CurseSuperExplosive> SUPER_EXPLOSIVE = register("super_explosive", CurseSuperExplosive::new);
    public static final DeferredHolder<Effect, CurseClaustrophobia> CLAUSTROPHOBIA = register("claustrophobia", CurseClaustrophobia::new);
    public static final DeferredHolder<Effect, CurseMoonwalker> MOONWALKER = register("moonwalker", CurseMoonwalker::new);
    public static final DeferredHolder<Effect, CurseSirensCall> SIRENS_CALL = register("sirens_call", CurseSirensCall::new);
    public static final DeferredHolder<Effect, CurseLoadingScreen> LOADING_SCREEN = register("loading_screen", CurseLoadingScreen::new);
    public static final DeferredHolder<Effect, CursePacing> PACING = register("pacing", CursePacing::new);
    public static final DeferredHolder<Effect, CurseTrumpet> TRUMPET = register("trumpet", CurseTrumpet::new);

    private Curses() {}

    private static <T extends Effect> DeferredHolder<Effect, T> register(String name, java.util.function.Supplier<T> factory) {
        return WitchModRegistries.EFFECTS.register(name, factory);
    }

    /** Forces this class to load (and thus register its curses) before {@code RegisterEvent} fires. */
    public static void bootstrap() {}
}
