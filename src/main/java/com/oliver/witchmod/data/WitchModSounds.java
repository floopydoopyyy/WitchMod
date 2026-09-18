package com.oliver.witchmod.data;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** the mod's custom sound events. definitions in {@code assets/witchmod/sounds.json}, oggs under {@code sounds/}. */
public final class WitchModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WitchMod.MODID);

    /** the Loading Screen curse's hold music — one of these is picked per screen, weighted. */
    public static final Supplier<SoundEvent> LOADING_MUSIC_1 = register("curse.loading.music_1");
    public static final Supplier<SoundEvent> LOADING_MUSIC_2 = register("curse.loading.music_2");
    public static final Supplier<SoundEvent> LOADING_MUSIC_3 = register("curse.loading.music_3");
    /** the rare one. Deliberately awful, deliberately quiet. */
    public static final Supplier<SoundEvent> LOADING_MUSIC_GOOFY = register("curse.loading.music_goofy");

    /** pacing: the dramatic theme that plays for the whole time-stop, cut off the instant it ends. */
    public static final Supplier<SoundEvent> PACING_THEME = register("curse.pacing.theme");
    /** pacing: a quiet camera click between every cut. */
    public static final Supplier<SoundEvent> PACING_CLICK = register("curse.pacing.click");
    /** pacing: a 1/N chance to replace a click with... a revelation. */
    public static final Supplier<SoundEvent> PACING_THE_ONE_PIECE = register("curse.pacing.theonepiece");

    /** unhygienic: occasional ambient buzzing, one of three variants picked at random by vanilla. */
    public static final Supplier<SoundEvent> UNHYGIENIC_FLIES = register("curse.unhygienic.flies");

    /** slippery Feet: the descending slide whistle as your feet go out from under you. Three variants. */
    public static final Supplier<SoundEvent> SLIPPERY_SLIDE = register("curse.slippery.slide_whistle");

    /** gassy: the everyday one, four variants so a run of them doesn't sound copy-pasted. */
    public static final Supplier<SoundEvent> GASSY_FART_SMALL = register("curse.gassy.fart_small");
    /** gassy: the rare big one. Single file — it's an event, and it should sound like the same event. */
    public static final Supplier<SoundEvent> GASSY_FART_LARGE = register("curse.gassy.fart_large");

    /**
     * Echoes: a fake message-app ping. Deliberately a SINGLE file with no variants — a notification chime is
     * always identical in real life, so randomising it would give the game away instantly.
     */
    public static final Supplier<SoundEvent> ECHOES_PING = register("curse.echoes.ping");

    /**
     * Trumpet: the cartoonish fat-trumpet walking loop. Played CLIENT-side as a looping tickable
     * {@code SoundInstance} that starts/stops instantly with movement and is pitched up while sprinting —
     * see {@code client/TrumpetSoundInstance}. Must be a MONO OGG for OpenAL to position it (a stereo file
     * plays non-directionally at constant volume, defeating the "gives away your position" point).
     */
    public static final Supplier<SoundEvent> TRUMPET_WALK_LOOP = register("curse.trumpet.walk_loop");

    /** main Character: the looping "intense drum" battle theme, played (ambient) for the protagonist while active. */
    public static final Supplier<SoundEvent> MAINCHAR_THEME = register("blessing.mainchar.theme");

    /** guardian Angel: the zap it fires at enemies while you fight. */
    public static final Supplier<SoundEvent> GUARDIAN_ZAP = register("blessing.guardian.zap");

    /** brute: the meaty impact when you plough into an entity or wall. */
    public static final Supplier<SoundEvent> BRUTE_IMPACT = register("blessing.brute.impact");

    /** bouncy: the boing when you rebound or fling something (3 variants). */
    public static final Supplier<SoundEvent> BOUNCY_BOING = register("blessing.bouncy.boing");

    /** laugh Track: the crowd LAUGH played to everyone when the blessed player speaks (3 variants, the common one). */
    public static final Supplier<SoundEvent> LAUGHTRACK_LAUGH = register("blessing.laughtrack.laugh");
    /** laugh Track: the crowd CHEER (2 variants, the rarer 10% one). */
    public static final Supplier<SoundEvent> LAUGHTRACK_CHEER = register("blessing.laughtrack.cheer");

    /** gladiator: a sword-collision CLANG played instantly on a successful parry. */
    public static final Supplier<SoundEvent> GLADIATOR_PARRY = register("blessing.gladiator.parry");
    /** gladiator: a woosh — played on a whiffed parry, and again as the riposte swing leaves. */
    public static final Supplier<SoundEvent> GLADIATOR_WHIFF = register("blessing.gladiator.whiff");
    /** gladiator: a sword IMPACT played when the riposte counter-hit lands. */
    public static final Supplier<SoundEvent> GLADIATOR_RIPOSTE = register("blessing.gladiator.riposte");
    /** gladiator: a bright SHINE layered on top of a PERFECT parry. */
    public static final Supplier<SoundEvent> GLADIATOR_PERFECT = register("blessing.gladiator.perfect");
    /** gladiator: a subtle sword CLASH played when a projectile is reflected. */
    public static final Supplier<SoundEvent> GLADIATOR_REFLECT = register("blessing.gladiator.reflect");

    /** the Snail: looping dread music while the snail is close (client loop, {@code "stream": false} so it loops seamlessly). */
    public static final Supplier<SoundEvent> SNAIL_MUSIC = register("curse.snail.music");

    /** sonar blessing: the sweeping ping emitted on the timer. */
    public static final Supplier<SoundEvent> SONAR_PING = register("blessing.sonar.ping");

    // cutaway Gag sounds.
    public static final Supplier<SoundEvent> CUTAWAY_BALL_THROW = register("curse.cutaway.ball_throw");
    public static final Supplier<SoundEvent> CUTAWAY_BOWLING_STRIKE = register("curse.cutaway.bowling_strike");
    public static final Supplier<SoundEvent> CUTAWAY_DRIFTING = register("curse.cutaway.drifting");
    /** helicopter: looped while the victim is in the spinning boat (client loop, {@code "stream": false}). */
    public static final Supplier<SoundEvent> CUTAWAY_HELICOPTER = register("curse.cutaway.helicopter");
    /** "I like trains" — plays in full BEFORE the train rolls (the warning line). */
    public static final Supplier<SoundEvent> CUTAWAY_TRAIN_WARNING = register("curse.cutaway.train_warning");
    /** the train rolling toward the victim, played at the train's location. */
    public static final Supplier<SoundEvent> CUTAWAY_TRAIN_ROLL = register("curse.cutaway.train_roll");
    /** abduction: looped tractor-beam hum for the whole lift (client loop, {@code "stream": false}). */
    public static final Supplier<SoundEvent> CUTAWAY_TRACTORBEAM = register("curse.cutaway.tractorbeam");
    /** abduction: the UFO arriving, just before the tractor beam starts. */
    public static final Supplier<SoundEvent> CUTAWAY_UFO_ENTER = register("curse.cutaway.ufo_enter");
    /** cutaway MARRIAGE: the wedding music — played once at the ceremony, stoppable abruptly when it's cut short. */
    public static final Supplier<SoundEvent> CUTAWAY_WEDDING = register("curse.cutaway.wedding");

    // --- The Dweller ------------------------------------------------------------------------------------
    /** non-diegetic paranormal ambience (10 variants); frequency + volume scale with dread, never spammed. */
    public static final Supplier<SoundEvent> DWELLER_MOOD = register("curse.dweller.mood");
    /** diegetic wind — replaces the teleport/movement sounds; occasionally an ambient red herring (3 variants). */
    public static final Supplier<SoundEvent> DWELLER_WIND = register("curse.dweller.wind");
    /** diegetic laugh on a dread tier-up: 100% + full volume on a full tier, 50% + quieter on a half tier (3 variants). */
    public static final Supplier<SoundEvent> DWELLER_LAUGH = register("curse.dweller.laugh");
    /** diegetic scream at the start of a chase, at the dweller's spot; rarely a quieter ambient red herring (2 variants). */
    public static final Supplier<SoundEvent> DWELLER_SCREAM = register("curse.dweller.scream");
    /** diegetic breath — a subtle warning when it's close BEHIND you unseen; also a turn-to-face jumpscare. */
    public static final Supplier<SoundEvent> DWELLER_BREATH = register("curse.dweller.breath");
    /** diegetic breathing — loops (approximated) while it watches you, louder the closer it is. */
    public static final Supplier<SoundEvent> DWELLER_BREATHING = register("curse.dweller.breathing");
    /** diegetic footsteps AT the dweller during a chase, human-ish cadence (3 variants). */
    public static final Supplier<SoundEvent> DWELLER_STEP = register("curse.dweller.step");
    /** non-diegetic loud pop close behind you (2 variants). */
    public static final Supplier<SoundEvent> DWELLER_POP = register("curse.dweller.pop");
    /** death: the BANG (played at 50% volume) layered with the SPLATTER. */
    public static final Supplier<SoundEvent> DWELLER_BANG = register("curse.dweller.bang");
    /** death: the wet SPLATTER, simultaneous with the bang. */
    public static final Supplier<SoundEvent> DWELLER_SPLATTER = register("curse.dweller.splatter");
    /** a very loud glitchy scream — used on the mimic's mask-drop reveal (movable later). */
    public static final Supplier<SoundEvent> DWELLER_ENRAGED = register("curse.dweller.enraged");
    /** knock event: a knock/scrape AT the doomed blocks; a random one plays, then 1-6s later they all shatter (4 variants). */
    public static final Supplier<SoundEvent> DWELLER_KNOCK = register("curse.dweller.knock");

    // --- System / ritual / shared ------------------------------------------------------------------------
    /** any curse OR blessing landing — the initial "you've been afflicted" sting, played first. */
    public static final Supplier<SoundEvent> AFFLICTED = register("system.afflicted");
    /** a BLESSING landing — played slightly after {@link #AFFLICTED}. */
    public static final Supplier<SoundEvent> BLESSED = register("system.blessed");
    /** a CURSE landing — played slightly after {@link #AFFLICTED}. */
    public static final Supplier<SoundEvent> CURSED = register("system.cursed");
    /** a heavy hit — layered on top of the normal hit sounds by curses/blessings like Giant, Heavy Hitter. */
    public static final Supplier<SoundEvent> BIG_HIT = register("system.big_hit");
    /** the Bewitching Table's distinct FAILURE sting (legacy — superseded by fizzle/backfire below). */
    public static final Supplier<SoundEvent> RITUAL_FAIL = register("ritual.fail");
    /** a ritual SUCCEEDS — a little magic jingle. */
    public static final Supplier<SoundEvent> RITUAL_SUCCESS = register("ritual.success");
    /** a ritual BACKFIRES — a firework bang. */
    public static final Supplier<SoundEvent> RITUAL_BACKFIRE = register("ritual.backfire");
    /** a ritual FIZZLES (nothing happens) — a quick fizzle. */
    public static final Supplier<SoundEvent> RITUAL_FIZZLE = register("ritual.fizzle");
    /** the Amethyst Bell is rung — a dramatic bell ring. */
    public static final Supplier<SoundEvent> AMETHYST_BELL_RING = register("amethyst_bell.ring");
    /** the Ledger is updated — a subtle pencil-writing scratch. */
    public static final Supplier<SoundEvent> LEDGER_WRITE = register("ledger.write");

    /** a coin is flipped — the reworked coin items' "used" sound as it breaks into its 1–3 attachments. */
    public static final Supplier<SoundEvent> COIN_FLIP = register("item.coin_flip");
    /** a bottled attachment fails to catch anyone and spawns a homing LASH — played at the lash's spawn point. Also on an effigy forward. */
    public static final Supplier<SoundEvent> LASH_SPAWN = register("item.lash_spawn");
    /** an effigy is used on a target — the magic glint as the curses leap across. */
    public static final Supplier<SoundEvent> SPELL_GLINT = register("item.spell_glint");
    /** a spell FAILS to land because the target carries the Protected status effect (Warding Totem / Holy Water). */
    public static final Supplier<SoundEvent> SPELL_FAIL = register("system.spell_fail");
    /** unseen: the cloak "vanish" — a reversed spell-fail. */
    public static final Supplier<SoundEvent> UNSEEN_CLOAK = register("blessing.unseen.cloak");

    /** hiccups: the involuntary hic (7 variants so a fit doesn't sound copy-pasted). */
    public static final Supplier<SoundEvent> HICCUPS_HICCUP = register("curse.hiccups.hiccup");

    /** narcolepsy: intermittent snores (varied pitch) while asleep. */
    public static final Supplier<SoundEvent> NARCOLEPSY_SNORE = register("curse.narcolepsy.snore");
    /** cutaway Gag (Parade): the drum track — played TOGETHER with the march track for the whole gag. */
    public static final Supplier<SoundEvent> CUTAWAY_PARADE_DRUM = register("curse.cutaway.parade_drum");
    /** cutaway Gag (Parade): the march-music track — played TOGETHER with the drum track for the whole gag. */
    public static final Supplier<SoundEvent> CUTAWAY_PARADE_MARCH = register("curse.cutaway.parade_march");

    private static Supplier<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private WitchModSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
