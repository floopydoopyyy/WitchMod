package com.oliver.witchmod.data;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/**
 * The mod's custom SoundEvents (master-spec Section 12). Definitions live in
 * {@code assets/witchmod/sounds.json} and the OGGs under {@code assets/witchmod/sounds/...}.
 */
public final class WitchModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WitchMod.MODID);

    /** The Loading Screen curse's hold music — one of these is picked per screen, weighted. */
    public static final Supplier<SoundEvent> LOADING_MUSIC_1 = register("curse.loading.music_1");
    public static final Supplier<SoundEvent> LOADING_MUSIC_2 = register("curse.loading.music_2");
    public static final Supplier<SoundEvent> LOADING_MUSIC_3 = register("curse.loading.music_3");
    /** The rare one. Deliberately awful, deliberately quiet. */
    public static final Supplier<SoundEvent> LOADING_MUSIC_GOOFY = register("curse.loading.music_goofy");

    /** Pacing: the dramatic theme that plays for the whole time-stop, cut off the instant it ends. */
    public static final Supplier<SoundEvent> PACING_THEME = register("curse.pacing.theme");
    /** Pacing: a quiet camera click between every cut. */
    public static final Supplier<SoundEvent> PACING_CLICK = register("curse.pacing.click");
    /** Pacing: a 1/N chance to replace a click with... a revelation. */
    public static final Supplier<SoundEvent> PACING_THE_ONE_PIECE = register("curse.pacing.theonepiece");

    /** Unhygienic: occasional ambient buzzing, one of three variants picked at random by vanilla. */
    public static final Supplier<SoundEvent> UNHYGIENIC_FLIES = register("curse.unhygienic.flies");

    /** Slippery Feet: the descending slide whistle as your feet go out from under you. Three variants. */
    public static final Supplier<SoundEvent> SLIPPERY_SLIDE = register("curse.slippery.slide_whistle");

    /** Gassy: the everyday one, four variants so a run of them doesn't sound copy-pasted. */
    public static final Supplier<SoundEvent> GASSY_FART_SMALL = register("curse.gassy.fart_small");
    /** Gassy: the rare big one. Single file — it's an event, and it should sound like the same event. */
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

    /** Main Character: the looping "intense drum" battle theme, played (ambient) for the protagonist while active. */
    public static final Supplier<SoundEvent> MAINCHAR_THEME = register("blessing.mainchar.theme");

    /** Brute: the meaty impact when you plough into an entity or wall. */
    public static final Supplier<SoundEvent> BRUTE_IMPACT = register("blessing.brute.impact");

    /** Bouncy: the boing when you rebound or fling something (3 variants). */
    public static final Supplier<SoundEvent> BOUNCY_BOING = register("blessing.bouncy.boing");

    /** Laugh Track: the crowd LAUGH played to everyone when the blessed player speaks (3 variants, the common one). */
    public static final Supplier<SoundEvent> LAUGHTRACK_LAUGH = register("blessing.laughtrack.laugh");
    /** Laugh Track: the crowd CHEER (2 variants, the rarer 10% one). */
    public static final Supplier<SoundEvent> LAUGHTRACK_CHEER = register("blessing.laughtrack.cheer");

    /** Gladiator: a sword-collision CLANG played instantly on a successful parry. */
    public static final Supplier<SoundEvent> GLADIATOR_PARRY = register("blessing.gladiator.parry");
    /** Gladiator: a woosh — played on a whiffed parry, and again as the riposte swing leaves. */
    public static final Supplier<SoundEvent> GLADIATOR_WHIFF = register("blessing.gladiator.whiff");
    /** Gladiator: a sword IMPACT played when the riposte counter-hit lands. */
    public static final Supplier<SoundEvent> GLADIATOR_RIPOSTE = register("blessing.gladiator.riposte");
    /** Gladiator: a bright SHINE layered on top of a PERFECT parry. */
    public static final Supplier<SoundEvent> GLADIATOR_PERFECT = register("blessing.gladiator.perfect");
    /** Gladiator: a subtle sword CLASH played when a projectile is reflected. */
    public static final Supplier<SoundEvent> GLADIATOR_REFLECT = register("blessing.gladiator.reflect");

    /** The Snail: looping dread music while the snail is close (client loop, {@code "stream": false} so it loops seamlessly). */
    public static final Supplier<SoundEvent> SNAIL_MUSIC = register("curse.snail.music");

    private static Supplier<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private WitchModSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
