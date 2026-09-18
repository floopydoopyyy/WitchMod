package com.oliver.witchmod.client;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * the whole Loading Screen curse, client-side: how long the fake load runs, when it stutters, when it
 * cruelly starts over, and the input lock that lasts exactly as long as the animation.
 *
 * <p>The server only ever hands over a session id (a random long). Everything else is decided here, because
 * the length is <b>dynamic</b> — a stutter or a restart extends it — and the input lock has to release on
 * precisely the frame the bar finishes. Splitting that across the network would let the two drift apart.
 * the session id seeds the RNG, so the same door gives the same screen if it's ever replayed.
 */
public final class LoadingScreenState {
    private static final ResourceLocation TIPS_FILE = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "text/loading_tips.json");
    private static final List<String> FALLBACK_TIPS = List.of("TIP: Loading...");
    private static final int SLIDE_BACK_TICKS = 6; // how long the bar takes to whip back to zero
    private static final int DOT_CYCLE_TICKS = 8;  // ticks per step of the "Loading..." ellipsis

    private static long currentSession;
    private static boolean active;

    private static Random rng = new Random();
    private static List<String> tips = FALLBACK_TIPS;

    private static int phaseTicks;      // ticks elapsed in the current loading phase (frozen while stuttering)
    private static int phaseTarget;     // ticks this phase runs for
    private static int totalTicks;      // wall-clock ticks this session has lasted, for the safety cap
    private static int stutterLeft;     // ticks left frozen
    private static int slideBackLeft;   // ticks left of the bar visibly whipping back to zero
    private static boolean restartPending;
    private static float progress;      // 0..1, what the bar actually shows
    private static float progressAtSlideStart;

    private static int tipIndex;
    private static float tipScroll = Float.NaN; // pixel offset of the marquee; NaN = needs initialising

    /** the hold music currently playing, kept so it can be cut off the instant the screen ends. */
    @Nullable
    private static SoundInstance music;

    private LoadingScreenState() {}

    public static boolean isActive() {
        return active;
    }

    public static float progress() {
        return progress;
    }

    public static String currentTip() {
        return tips.isEmpty() ? "" : tips.get(Math.floorMod(tipIndex, tips.size()));
    }

    public static float tipScroll() {
        return tipScroll;
    }

    public static void setTipScroll(float value) {
        tipScroll = value;
    }

    public static void nextTip() {
        tipIndex++;
    }

    /** which animation frame to draw, derived from wall-clock ticks so it keeps spinning through stutters. */
    public static int animationFrame() {
        return (totalTicks / Config.LOADING_SCREEN_FRAME_TICKS.get()) % Config.LOADING_SCREEN_FRAME_COUNT.get();
    }

    /**
     * 1-3, for the "Loading." / ".." / "..." cycle. Driven off wall-clock ticks like the animation, so the
     * text keeps ticking over even while the bar is frozen mid-stutter — which is exactly what sells a
     * stutter as the game hanging rather than the overlay having died.
     */
    public static int loadingDots() {
        return 1 + (totalTicks / DOT_CYCLE_TICKS) % 3;
    }

    /** drives the whole thing. Called once per client tick from {@code ClientCurseHandler}. */
    public static void tick(LocalPlayer player) {
        long session = player.getData(WitchModAttachments.LOADING_SCREEN_SESSION);

        if (session != currentSession) {
            currentSession = session;
            if (session == 0L) {
                finish(); // curse cured/expired mid-screen
            } else {
                begin(session);
            }
        }
        if (!active) {
            return;
        }

        totalTicks++;
        if (totalTicks >= Config.LOADING_SCREEN_MAX_TOTAL_TICKS.get()) {
            finish(); // safety valve: never lock input longer than this, however the rolls landed
            return;
        }

        if (slideBackLeft > 0) {
            // the bar is visibly whipping back down to zero before loading "again".
            slideBackLeft--;
            progress = progressAtSlideStart * (slideBackLeft / (float) SLIDE_BACK_TICKS);
            if (slideBackLeft == 0) {
                phaseTicks = 0;
                phaseTarget = randomBetween(Config.LOADING_SCREEN_RESTART_MIN_TICKS.get(),
                        Config.LOADING_SCREEN_RESTART_MAX_TICKS.get());
                progress = 0.0F;
            }
            return;
        }

        if (stutterLeft > 0) {
            stutterLeft--; // frozen: the bar holds exactly where it is, as if the game has hung
            return;
        }
        // once a second, the bar might just... stop for a bit.
        if (totalTicks % 20 == 0 && rng.nextInt(100) < Config.LOADING_SCREEN_STUTTER_CHANCE_PERCENT.get()) {
            stutterLeft = randomBetween(Config.LOADING_SCREEN_STUTTER_MIN_TICKS.get(),
                    Config.LOADING_SCREEN_STUTTER_MAX_TICKS.get());
            return;
        }

        phaseTicks++;
        progress = Math.min(1.0F, phaseTicks / (float) phaseTarget);

        if (phaseTicks >= phaseTarget) {
            if (restartPending) {
                restartPending = false;
                progressAtSlideStart = progress;
                slideBackLeft = SLIDE_BACK_TICKS;
            } else {
                finish();
            }
        }
    }

    private static void begin(long session) {
        stopMusic(); // a door opened again mid-screen — never stack two tracks
        rng = new Random(session);
        tips = loadTips();

        active = true;
        phaseTicks = 0;
        totalTicks = 0;
        stutterLeft = 0;
        slideBackLeft = 0;
        progress = 0.0F;
        tipIndex = rng.nextInt(Math.max(1, tips.size()));
        tipScroll = Float.NaN;

        phaseTarget = randomBetween(Config.LOADING_SCREEN_MIN_TICKS.get(), Config.LOADING_SCREEN_MAX_TICKS.get());
        restartPending = rng.nextInt(100) < Config.LOADING_SCREEN_RESTART_CHANCE_PERCENT.get();

        startMusic();
    }

    /**
     * picks a hold-music track by weight and plays it straight through the client's own SoundManager — so
     * it exists only on the cursed player's machine, and nobody else hears a thing.
     */
    private static void startMusic() {
        int w1 = Config.LOADING_SCREEN_MUSIC_1_WEIGHT.get();
        int w2 = Config.LOADING_SCREEN_MUSIC_2_WEIGHT.get();
        int w3 = Config.LOADING_SCREEN_MUSIC_3_WEIGHT.get();
        int wGoofy = Config.LOADING_SCREEN_MUSIC_GOOFY_WEIGHT.get();
        int total = w1 + w2 + w3 + wGoofy;
        if (total <= 0) {
            return; // all four disabled
        }

        int roll = rng.nextInt(total);
        SoundEvent track;
        float volume = (float) (double) Config.LOADING_SCREEN_MUSIC_VOLUME.get();
        if ((roll -= w1) < 0) {
            track = WitchModSounds.LOADING_MUSIC_1.get();
        } else if ((roll -= w2) < 0) {
            track = WitchModSounds.LOADING_MUSIC_2.get();
        } else if (roll - w3 < 0) {
            track = WitchModSounds.LOADING_MUSIC_3.get();
        } else {
            track = WitchModSounds.LOADING_MUSIC_GOOFY.get();
            volume = (float) (double) Config.LOADING_SCREEN_GOOFY_VOLUME.get();
        }

        music = SimpleSoundInstance.forUI(track, 1.0F, volume);
        Minecraft.getInstance().getSoundManager().play(music);
    }

    /** cuts the music dead. Called the instant the screen stops, however it stopped. */
    private static void stopMusic() {
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
    }

    /** ends the screen and kills the music together, so the two can never fall out of step. */
    private static void finish() {
        active = false;
        stopMusic();
    }

    private static int randomBetween(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + rng.nextInt(high - low + 1);
    }

    /**
     * reads the writable tip list. Re-read per session so editing the file (plus F3+T) shows up without a
     * restart; it's one tiny file, once per door.
     */
    private static List<String> loadTips() {
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(TIPS_FILE)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            List<String> loaded = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                loaded.add(element.getAsString());
            }
            return loaded.isEmpty() ? FALLBACK_TIPS : List.copyOf(loaded);
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[Loading Screen] Could not read {}; using the fallback tip.", TIPS_FILE, e);
            return FALLBACK_TIPS;
        }
    }
}
