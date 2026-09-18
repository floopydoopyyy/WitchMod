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
 * the Cutaway Gag's pool of Family-Guy title-card lines ("Meanwhile…", "Cut to…", …), read from a writable
 * list. Client-side only (drawn as an overlay), so it lives in {@code assets/} and reloads with F3+T — same
 * approach as {@link WindowTitles} and the Loading Screen tips. Missing/malformed falls back to one line.
 */
public final class CutawayTitles {
    private static final ResourceLocation TITLES_FILE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "text/cutaway_titles.json");
    private static final List<String> FALLBACK = List.of("Meanwhile...");

    private static List<String> titles = FALLBACK;
    private static boolean loaded;

    private CutawayTitles() {}

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
            WitchMod.LOGGER.warn("[Cutaway Gag] Could not read {}; falling back to one title.", TITLES_FILE, e);
            return FALLBACK;
        }
    }
}
