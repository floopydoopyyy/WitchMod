package com.oliver.witchmod.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.oliver.witchmod.WitchMod;

/**
 * speaks a Narrator-curse line via Minecraft's OWN bundled text-to-speech ({@code com.mojang.text2speech} —
 * the same library the accessibility Narrator uses). This is a normal in-process library call to the platform
 * accessibility TTS, NOT a shelled-out process, so it's the safe route.
 *
 * <p>To guarantee lines never OVERLAP we {@code clear()} then {@code say(..., interrupt=true)}, so a new line
 * always cuts any still-speaking one. TTS is platform-dependent — great on Windows/macOS, and silent on Linux
 * without {@code speech-dispatcher}; a missing engine just no-ops here.
 *
 * <p>The line is ALSO shown as a subtitle on the action bar for {@code subtitleTicks}, re-asserted each client
 * tick and cleared the instant it lapses — so non-Windows players (whose TTS may not fire) can still read it.
 */
public final class NarratorClient {
    private NarratorClient() {}

    private static Component subtitle;
    private static int subtitleTicksLeft;

    public static void speak(String text, String callout, int subtitleTicks) {
        Minecraft mc = Minecraft.getInstance();
        // the subtitle is the spoken line, UNLESS this client isn't on Windows and the server rolled an OS jab,
        // in which case the jab takes the subtitle slot that once. The client decides by its OWN platform.
        boolean nonWindows = Util.getPlatform() != Util.OS.WINDOWS;
        String shown = (nonWindows && callout != null && !callout.isEmpty()) ? callout : text;
        if (mc.player != null && shown != null && !shown.isEmpty()) {
            subtitle = Component.literal(shown);
            subtitleTicksLeft = Math.max(subtitleTicks, 1);
            mc.gui.setOverlayMessage(subtitle, false); // action bar; false = no colour animation
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            com.mojang.text2speech.Narrator narrator = com.mojang.text2speech.Narrator.getNarrator();
            if (narrator != null && narrator.active()) {
                narrator.clear();
                narrator.say(text, true);
            }
        } catch (Throwable t) {
            // platform TTS unavailable — the curse just goes quiet here, no harm done.
            WitchMod.LOGGER.debug("[Narrator] TTS unavailable: {}", t.toString());
        }
    }

    /** keep the current line's subtitle solid on the action bar until it lapses, then clear it at once. */
    public static void tickSubtitle(Minecraft mc) {
        if (subtitleTicksLeft <= 0 || mc.player == null || subtitle == null) {
            return;
        }
        subtitleTicksLeft--;
        if (subtitleTicksLeft <= 0) {
            mc.gui.setOverlayMessage(Component.empty(), false); // done speaking — drop it immediately
            subtitle = null;
        } else {
            mc.gui.setOverlayMessage(subtitle, false); // re-assert so it stays fully opaque, no vanilla fade
        }
    }
}
