package com.oliver.witchmod;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * client-only config — visual/local/accessibility preferences that live on the rendering machine, not synced
 * from a server. in {@code config/witchmod/client.toml}; applies without a world reload.
 *
 * <p>the opt-out toggles are the one exception that reaches the server: on join the client reports which
 * effects the player has opted out of, and the server refuses to apply them (unless ignoreClientOptOuts is set
 * server-side). everything else here only changes what THIS player sees/hears.
 *
 * <p>every .comment() string below is user-facing toml documentation and ships in the jar — edit freely.
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── screen fx / accessibility (client.toml [screen]) ──
    static { BUILDER.comment("witchmod — client.toml: per-player visual/audio/accessibility preferences (NOT synced from a",
            "server). every comment line here ships in the jar; edit them freely.").push("screen"); }
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE_MULTIPLIER = BUILDER
            .comment("Multiplier on ALL screen shake this mod produces (Heavyweight/Dense collapses, Flat Footed",
                    "footsteps, Brute smashes, the Dweller, Gladiator parries, the Voodoo Doll, the Amethyst Bell, the",
                    "Holy Hand Grenade, Vertigo's camera sway). 1.0 = full, 0.5 = half, 0.0 = no shake at all.")
            .defineInRange("cameraShakeMultiplier", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SCREEN_SHADER_INTENSITY = BUILDER
            .comment("Strength of the mod's full-screen colour effects — the Dweller's desaturation, Siren's Call's",
                    "magenta wash, the Dweller chase red vignette. 1.0 = full, 0.0 = none. Does not touch the world fog.")
            .defineInRange("screenShaderIntensity", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue DISABLE_FULLSCREEN_SHADERS = BUILDER
            .comment("Hard-disable the GPU post-process shaders (the Dweller desaturation and the Bedrock Moment",
                    "'vibrant' saturation boost). Use this if post shaders cause performance or compatibility trouble;",
                    "the lighter colour tints are governed by screenShaderIntensity instead.")
            .define("disableFullscreenShaders", false);
    public static final ModConfigSpec.BooleanValue REDUCE_FLASHING = BUILDER
            .comment("Photosensitivity: greatly dampen sudden bright flashes (the Dweller jumpscare white flash, the",
                    "Immortality/Last Stand revive flash, the Bedrock Moment fake-BSOD glitch). Strongly recommended if",
                    "flashing imagery affects you.")
            .define("reduceFlashing", false);
    static { BUILDER.pop(); }

    // ── particles (client.toml [particles]) ──
    static { BUILDER.push("particles"); }
    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY = BUILDER
            .comment("Multiplier on the mod's heaviest client-rendered particle effects (the Guardian Angel trail/halo,",
                    "the Spotlight pillar, the Holy Hand Grenade shimmer, the Leader ring). 1.0 = current density (the",
                    "default), lower values thin them out for weaker machines. Does not affect gameplay.")
            .defineInRange("particleDensityMultiplier", 1.0, 0.1, 1.0);
    static { BUILDER.pop(); }

    // ── compendium (client.toml [compendium]) ──
    static { BUILDER.push("compendium"); }
    public static final ModConfigSpec.BooleanValue COMPENDIUM_OPEN_LAST_CHAPTER = BUILDER
            .comment("When true, opening the Compendium returns you to the last chapter you were reading instead of",
                    "the first. Off by default.")
            .define("compendiumOpenToLastChapter", false);
    static { BUILDER.pop(); }

    // ── curse opt-outs (client.toml [opt_outs]) ──
    // each toggle asks the server not to apply that curse to YOU. the server honours it unless its own
    // ignoreClientOptOuts rule is on; when a cast is refused this way the caster is told and refunded.
    static { BUILDER.push("opt_outs"); }
    public static final ModConfigSpec.BooleanValue OPT_OUT_SCREENSAVER = BUILDER
            .comment("Opt out of the Screensaver curse (the game drops out of fullscreen and bounces the window around).")
            .define("optOutScreensaver", false);
    public static final ModConfigSpec.BooleanValue OPT_OUT_MINOR_INCONVENIENCE = BUILDER
            .comment("Opt out of the Minor Inconvenience curse (can't go fullscreen; the window is renamed silly things).")
            .define("optOutMinorInconvenience", false);
    public static final ModConfigSpec.BooleanValue OPT_OUT_CHANNELS = BUILDER
            .comment("Opt out of the Channels curse (left/right positional audio is swapped).")
            .define("optOutChannels", false);
    public static final ModConfigSpec.BooleanValue OPT_OUT_LOADING_SCREEN = BUILDER
            .comment("Opt out of the Loading Screen curse (a fake loading screen locks your input at every door).")
            .define("optOutLoadingScreen", false);
    public static final ModConfigSpec.BooleanValue OPT_OUT_NARRATOR = BUILDER
            .comment("Opt out of the Narrator curse (a text-to-speech voice reads out your every move).")
            .define("optOutNarrator", false);
    static { BUILDER.pop(); }

    // ── splitscreen (client.toml [splitscreen]) ──
    static { BUILDER.push("splitscreen"); }
    public static final ModConfigSpec.BooleanValue SPLITSCREEN_LIVE_POV = BUILDER
            .comment("Splitscreen: render the partner's LIVE point of view in the side panel (a real second render pass).",
                    "EXPERIMENTAL — off by default. When off, the panel shows a 'PLAYER 2' screen (partner name + live",
                    "position/facing) and everything else works. Set true to try the live POV.")
            .define("splitscreenLivePov", false);
    static { BUILDER.pop(); }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    // ── safe accessors: a config value throws until the spec is loaded, so guard every read with a default. ──

    public static double cameraShakeMultiplier() {
        try {
            return CAMERA_SHAKE_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    /** 0..1 strength for colour tints/uniforms; 0 when the post shaders are hard-disabled would still leave tints, so this is independent. */
    public static double screenShaderIntensity() {
        try {
            return SCREEN_SHADER_INTENSITY.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    /** whether the heavy GPU post-process shaders may load at all. */
    public static boolean postShadersEnabled() {
        try {
            return !DISABLE_FULLSCREEN_SHADERS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean reduceFlashing() {
        try {
            return REDUCE_FLASHING.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static double particleDensity() {
        try {
            return PARTICLE_DENSITY.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    /** scale a batch particle count by the density preference (never negative). */
    public static int scaledParticleCount(int count) {
        return Math.max(0, (int) Math.round(count * particleDensity()));
    }

    /** for loops that emit one particle per pass: keep this pass with probability = density. */
    public static boolean particleAllowed() {
        double d = particleDensity();
        return d >= 1.0 || Math.random() < d;
    }

    public static boolean openToLastChapter() {
        try {
            return COMPENDIUM_OPEN_LAST_CHAPTER.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** the effect ids (bare paths) this client has opted out of receiving, built from the toggles above. */
    public static List<String> optedOutEffectIds() {
        List<String> ids = new ArrayList<>();
        try {
            if (OPT_OUT_SCREENSAVER.get()) {
                ids.add("screensaver");
            }
            if (OPT_OUT_MINOR_INCONVENIENCE.get()) {
                ids.add("minor_inconvenience");
            }
            if (OPT_OUT_CHANNELS.get()) {
                ids.add("channels");
            }
            if (OPT_OUT_LOADING_SCREEN.get()) {
                ids.add("loading_screen");
            }
            if (OPT_OUT_NARRATOR.get()) {
                ids.add("narrator");
            }
        } catch (IllegalStateException ignored) {
            // not loaded yet — report nothing opted out
        }
        return ids;
    }
}
