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

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/**
 * yap's message book ({@code data/witchmod/text/yap.json}, /reload-able) — singles/doubles/triples so
 * multi-message outbursts are scripted, not randomly paired, plus keyed event reactions. list weighted by config.
 */
public final class YapMessages extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "yap");

    /** each element is one complete outburst: 1, 2 or 3 messages sent in order. */
    private static List<String[]> singles = List.<String[]>of(new String[] {"..."});
    private static List<String[]> doubles = List.of();
    private static List<String[]> triples = List.of();
    /** reaction lines keyed by event: hurt, attack, chest, death, proximity. Each entry is one outburst. */
    private static Map<String, List<String[]>> events = Map.of();

    public YapMessages() {
        super(GSON, "text"); // scans data/<namespace>/text/*.json
    }

    @Override
    protected void apply(java.util.Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = map.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[Yap] No data/witchmod/text/yap.json found; using a single placeholder line.");
            singles = List.<String[]>of(new String[] {"..."});
            doubles = List.of();
            triples = List.of();
            return;
        }
        JsonObject root = element.getAsJsonObject();
        singles = parseGroup(root, "singles", 1);
        doubles = parseGroup(root, "doubles", 2);
        triples = parseGroup(root, "triples", 3);
        if (singles.isEmpty()) {
            singles = List.<String[]>of(new String[] {"..."}); // never leave the pick with nothing to say
        }

        // event reactions: an "events" object of { eventKey: [ outbursts ] }, where each outburst is a
        // string (single) OR an array (a scripted sequence), mixed freely.
        Map<String, List<String[]>> loadedEvents = new HashMap<>();
        if (root.has("events") && root.get("events").isJsonObject()) {
            for (var entry : root.getAsJsonObject("events").entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    loadedEvents.put(entry.getKey(), parseMixedList(entry.getValue().getAsJsonArray()));
                }
            }
        }
        events = Map.copyOf(loadedEvents);
    }

    /** parses a list whose entries may each be a plain string (single) or an array (a sequence). */
    private static List<String[]> parseMixedList(JsonArray array) {
        List<String[]> out = new ArrayList<>();
        for (JsonElement entry : array) {
            if (entry.isJsonPrimitive()) {
                out.add(new String[] {entry.getAsString()});
            } else if (entry.isJsonArray()) {
                List<String> lines = new ArrayList<>();
                entry.getAsJsonArray().forEach(e -> lines.add(e.getAsString()));
                if (!lines.isEmpty()) {
                    out.add(lines.toArray(new String[0]));
                }
            }
        }
        return out;
    }

    /**
     * Parses one group. {@code singles} is an array of strings (each becomes a 1-message outburst); {@code
     * doubles}/{@code triples} are arrays of arrays. Malformed entries are skipped with a warning, never fatal.
     */
    private static List<String[]> parseGroup(JsonObject root, String key, int expected) {
        List<String[]> out = new ArrayList<>();
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return out;
        }
        for (JsonElement entry : root.getAsJsonArray(key)) {
            if (expected == 1 && entry.isJsonPrimitive()) {
                out.add(new String[] {entry.getAsString()});
            } else if (entry.isJsonArray()) {
                JsonArray arr = entry.getAsJsonArray();
                List<String> lines = new ArrayList<>();
                arr.forEach(e -> lines.add(e.getAsString()));
                if (!lines.isEmpty()) {
                    out.add(lines.toArray(new String[0]));
                }
            } else {
                WitchMod.LOGGER.warn("[Yap] Skipping malformed entry in '{}'.", key);
            }
        }
        return out;
    }

    /** weighted-picks a list (singles/doubles/triples), then a random scripted outburst from it. */
    public static String[] pickOutburst(RandomSource random) {
        int ws = Config.YAP_SINGLE_WEIGHT.get() * (singles.isEmpty() ? 0 : 1);
        int wd = Config.YAP_DOUBLE_WEIGHT.get() * (doubles.isEmpty() ? 0 : 1);
        int wt = Config.YAP_TRIPLE_WEIGHT.get() * (triples.isEmpty() ? 0 : 1);
        int total = ws + wd + wt;
        if (total <= 0) {
            return singles.isEmpty() ? new String[] {"..."} : pick(singles, random);
        }
        int roll = random.nextInt(total);
        if ((roll -= ws) < 0) {
            return pick(singles, random);
        }
        if ((roll - wd) < 0) {
            return pick(doubles, random);
        }
        return pick(triples, random);
    }

    /** a random reaction outburst for the given event key, or {@code null} if that list is empty/absent. */
    @javax.annotation.Nullable
    public static String[] pickEvent(String key, RandomSource random) {
        List<String[]> list = events.get(key);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return pick(list, random);
    }

    private static String[] pick(List<String[]> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }
}
