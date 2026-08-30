package com.oliver.witchmod.client;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.oliver.witchmod.Config;

/**
 * The Screensaver curse's window choreography (master-spec Screensaver): every so often the game drops out
 * of fullscreen, <b>slowly</b> shrinks to a window, bounces around the monitor DVD-logo style for a while,
 * then grows back and returns to fullscreen.
 *
 * <p><b>It is episodic, not constant.</b> Roughly {@code DUTY_PERCENT} of the time is spent bouncing and the
 * rest is left completely alone — the idle gap is DERIVED from the duty cycle and the rolled episode length
 * ({@code gap = episode × (100 - duty) / duty}), so retuning episode length keeps the same overall rhythm
 * without also having to retune the gap. A permanently bouncing window stops being funny within a minute and
 * makes the game genuinely unplayable.
 *
 * <p><b>Everything here is deliberately slow and large.</b> The transitions are eased over several seconds
 * and the shrunk window stays over half the monitor. A small window snapping about at speed is a
 * motion-sickness generator rather than a joke, and you still have to be able to play through it.
 *
 * <p>The player's original state — fullscreen or not, and their windowed size and position — is captured
 * when an episode starts and restored when it ends, so the curse never permanently changes their setup.
 * If the monitor can't be queried, every stage no-ops (per spec).
 */
public final class ScreensaverState {
    private enum Phase { IDLE, SHRINKING, BOUNCING, GROWING }

    /**
     * Resizes are only pushed every other tick. Each one makes Minecraft rebuild its framebuffer, so doing it
     * 20x a second for four seconds stutters; at 10/s the easing still reads as smooth.
     */
    private static final int RESIZE_EVERY = 2;

    private static Phase phase = Phase.IDLE;
    private static int phaseTicks;
    private static int phaseDuration;
    private static int idleTicks;
    private static int idleTarget = -1;

    // What to put back when the episode ends.
    private static boolean wasFullscreen;
    private static int originX;
    private static int originY;
    private static int originW;
    private static int originH;

    // Where this episode shrinks to.
    private static int targetX;
    private static int targetY;
    private static int targetW;
    private static int targetH;

    private static double bounceVx = 1.0;
    private static double bounceVy = 1.0;

    private ScreensaverState() {}

    /** Called every client tick while the curse is active. */
    public static void tick(Minecraft minecraft, RandomSource random) {
        Window window = minecraft.getWindow();
        GLFWVidMode mode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (mode == null) {
            return; // window manipulation unavailable — no-op per spec
        }

        // If the player forces fullscreen mid-bounce, boot straight back out so the effect can keep up (the
        // bounce needs a floating window). Only during the windowed phases — GROWING restores fullscreen itself.
        if ((phase == Phase.SHRINKING || phase == Phase.BOUNCING) && window.isFullscreen()) {
            minecraft.options.fullscreen().set(false); // vanilla's option callback does the toggle
        }

        switch (phase) {
            case IDLE -> tickIdle(minecraft, window, mode, random);
            case SHRINKING -> tickTransition(window, mode, true);
            case BOUNCING -> tickBouncing(window, mode);
            case GROWING -> tickTransition(window, mode, false);
        }
    }

    /** Called when the curse ends — puts the window back however it was found. */
    public static void reset(Minecraft minecraft) {
        if (phase != Phase.IDLE) {
            restoreOriginal(minecraft);
        }
        phase = Phase.IDLE;
        phaseTicks = 0;
        idleTicks = 0;
        idleTarget = -1;
    }

    private static void tickIdle(Minecraft minecraft, Window window, GLFWVidMode mode, RandomSource random) {
        if (idleTarget < 0) {
            scheduleNextEpisode(random);
        }
        if (++idleTicks < idleTarget) {
            return;
        }
        idleTicks = 0;
        idleTarget = -1;

        // Capture what to restore later, BEFORE anything is changed.
        long handle = window.getWindow();
        wasFullscreen = window.isFullscreen();
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            GLFW.glfwRestoreWindow(handle); // a maximized window can be neither moved nor resized
        }
        if (wasFullscreen) {
            // Setting the OPTION runs vanilla's own change callback, which does the toggle for us. Calling
            // toggleFullScreen() as well double-toggles straight back — a real bug this project already hit.
            minecraft.options.fullscreen().set(false);
        }

        int[] wx = new int[1];
        int[] wy = new int[1];
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowPos(handle, wx, wy);
        GLFW.glfwGetWindowSize(handle, ww, wh);
        originX = wx[0];
        originY = wy[0];
        originW = ww[0];
        originH = wh[0];

        double scale = Config.SCREENSAVER_WINDOW_SCALE_PERCENT.get() / 100.0;
        targetW = (int) (mode.width() * scale);
        targetH = (int) (mode.height() * scale);
        targetX = (mode.width() - targetW) / 2;      // shrink toward the middle, so it doesn't lurch sideways
        targetY = (mode.height() - targetH) / 2;

        bounceVx = random.nextBoolean() ? 1.0 : -1.0;
        bounceVy = random.nextBoolean() ? 1.0 : -1.0;

        phase = Phase.SHRINKING;
        phaseTicks = 0;
        phaseDuration = Config.SCREENSAVER_TRANSITION_TICKS.get();
    }

    /** Eases the window between its original geometry and the shrunk one, in either direction. */
    private static void tickTransition(Window window, GLFWVidMode mode, boolean shrinking) {
        phaseTicks++;
        float raw = Mth.clamp(phaseTicks / (float) phaseDuration, 0.0F, 1.0F);
        float t = shrinking ? raw : 1.0F - raw;
        float eased = t * t * (3.0F - 2.0F * t);     // smoothstep: slow at both ends

        if (phaseTicks % RESIZE_EVERY == 0 || raw >= 1.0F) {
            applyGeometry(window, mode,
                    lerpInt(eased, originX, targetX),
                    lerpInt(eased, originY, targetY),
                    lerpInt(eased, originW, targetW),
                    lerpInt(eased, originH, targetH));
        }

        if (raw < 1.0F) {
            return;
        }
        if (shrinking) {
            phase = Phase.BOUNCING;
            phaseTicks = 0;
            phaseDuration = rollEpisodeLength();
        } else {
            finishEpisode(window);
        }
    }

    private static void tickBouncing(Window window, GLFWVidMode mode) {
        long handle = window.getWindow();
        int[] wx = new int[1];
        int[] wy = new int[1];
        int[] ww = new int[1];
        int[] wh = new int[1];
        GLFW.glfwGetWindowPos(handle, wx, wy);
        GLFW.glfwGetWindowSize(handle, ww, wh);

        int speed = Config.SCREENSAVER_SPEED_PX.get();
        int maxX = Math.max(0, mode.width() - ww[0]);
        int maxY = Math.max(0, mode.height() - wh[0]);
        int newX = wx[0] + (int) (bounceVx * speed);
        int newY = wy[0] + (int) (bounceVy * speed);

        if (newX <= 0 || newX >= maxX) {
            bounceVx = -bounceVx;
            newX = Mth.clamp(newX, 0, maxX);
        }
        if (newY <= 0 || newY >= maxY) {
            bounceVy = -bounceVy;
            newY = Mth.clamp(newY, 0, maxY);
        }
        GLFW.glfwSetWindowPos(handle, newX, newY);

        if (++phaseTicks >= phaseDuration) {
            // Grow back from wherever it drifted to, not from where it started shrinking.
            targetX = newX;
            targetY = newY;
            targetW = ww[0];
            targetH = wh[0];
            phase = Phase.GROWING;
            phaseTicks = 0;
            phaseDuration = Config.SCREENSAVER_TRANSITION_TICKS.get();
        }
    }

    private static void finishEpisode(Window window) {
        applyGeometry(window, null, originX, originY, originW, originH);
        if (wasFullscreen) {
            Minecraft.getInstance().options.fullscreen().set(true);
        }
        phase = Phase.IDLE;
        phaseTicks = 0;
        idleTicks = 0;
        idleTarget = -1;
    }

    private static void restoreOriginal(Minecraft minecraft) {
        applyGeometry(minecraft.getWindow(), null, originX, originY, originW, originH);
        if (wasFullscreen) {
            minecraft.options.fullscreen().set(true);
        }
    }

    private static void applyGeometry(Window window, GLFWVidMode mode, int x, int y, int width, int height) {
        long handle = window.getWindow();
        if (mode != null) {
            x = Mth.clamp(x, 0, Math.max(0, mode.width() - width));
            y = Mth.clamp(y, 0, Math.max(0, mode.height() - height));
        }
        GLFW.glfwSetWindowSize(handle, Math.max(1, width), Math.max(1, height));
        GLFW.glfwSetWindowPos(handle, x, y);
    }

    private static int lerpInt(float t, int from, int to) {
        return Math.round(from + (to - from) * t);
    }

    private static int rollEpisodeLength() {
        int min = Config.SCREENSAVER_EPISODE_MIN.get();
        int max = Math.max(min, Config.SCREENSAVER_EPISODE_MAX.get());
        return min + (int) (Math.random() * (max - min + 1));
    }

    /**
     * The gap is derived from the duty cycle rather than configured separately, so the ratio of bouncing to
     * peace holds no matter how the episode length is retuned.
     */
    private static void scheduleNextEpisode(RandomSource random) {
        int episode = rollEpisodeLength();
        int duty = Config.SCREENSAVER_DUTY_PERCENT.get();
        int gap = (int) (episode * (100.0 - duty) / duty);
        // A little jitter either way so episodes don't arrive on a countable metronome.
        idleTarget = Math.max(20, (int) (gap * (0.75 + random.nextDouble() * 0.5)));
    }
}
