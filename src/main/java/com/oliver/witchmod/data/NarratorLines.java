package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import com.oliver.witchmod.WitchMod;

/** narrator lines ({@code data/witchmod/text/narrator.json}, /reload-able), keyed category → lines; {player}=victim. */
public final class NarratorLines extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "narrator");

    private static Map<String, List<String>> lines = Map.of();

    public NarratorLines() {
        super(GSON, "text"); // scans data/<namespace>/text/*.json
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = map.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[Narrator] No data/witchmod/text/narrator.json found; the Narrator will be silent.");
            lines = Map.of();
            return;
        }
        Map<String, List<String>> loaded = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<String> list = new ArrayList<>();
            for (JsonElement e : entry.getValue().getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    list.add(e.getAsString());
                }
            }
            if (!list.isEmpty()) {
                loaded.put(entry.getKey(), list);
            }
        }
        lines = loaded;
        WitchMod.LOGGER.info("[Narrator] Loaded {} line categories.", lines.size());
    }

    /** a random line for {@code category}, or null if that category has none. */
    public static String pick(String category, RandomSource rng) {
        List<String> list = lines.get(category);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(rng.nextInt(list.size()));
    }

    /** all lines for {@code category} (empty if none) — used by the variant-cap anti-spam. */
    public static List<String> all(String category) {
        return lines.getOrDefault(category, List.of());
    }

    /** loaded category keys — surfaced as debug-force tab-completions. */
    public static java.util.Set<String> categories() {
        return lines.keySet();
    }
}
