package com.oliver.witchmod.client;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import com.oliver.witchmod.WitchMod;

/**
 * the Minor Inconvenience curse's pool of silly OS window titles, read from a writable list.
 *
 * <p><b>Lives in {@code assets/}, not {@code data/}</b> — same call as the Loading Screen's tips, and for the
 * same reason: it's consumed purely client-side, so putting it in assets means it needs no server→client
 * syncing and reloads with an ordinary resource reload (F3+T) rather than {@code /reload}.
 *
 * <p>The file is re-read each time the curse becomes active rather than on every rename: a rename happens
 * every 30s–2min, and re-reading that often would be pointless churn. Edit the file and re-apply the curse
 * (or F3+T and re-apply) to pick changes up. A missing or malformed file logs a warning and falls back to a
 * single title rather than crashing.
 */
public final class WindowTitles {
    private static final ResourceLocation TITLES_FILE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "text/window_titles.json");
    private static final List<String> FALLBACK = List.of("Notepad");

    private static List<String> titles = FALLBACK;
    private static boolean loaded;

    private WindowTitles() {}

    /** called on the curse's inactive→active edge, so edits are picked up without a restart. */
    public static void reload() {
        titles = read();
        loaded = true;
    }

    public static String pick(RandomSource random) {
        if (!loaded) {
            reload();
        }
        return titles.get(random.nextInt(titles.size()));
    }

    private static List<String> read() {
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(TITLES_FILE)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            List<String> parsed = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                parsed.add(element.getAsString());
            }
            return parsed.isEmpty() ? FALLBACK : List.copyOf(parsed);
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[Minor Inconvenience] Could not read {}; falling back to one title.",
                    TITLES_FILE, e);
            return FALLBACK;
        }
    }
}
