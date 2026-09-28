package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import com.oliver.witchmod.WitchMod;

/**
 * banter between a player's bodyguard, solicitor and guardian angel ({@code data/witchmod/text/companionship.json},
 * /reload-able), keyed by category. a single-string entry is a one-liner; a two-string array is an exchange
 * [opener, reply] where the reply is spoken back by the other party. {player} = the anchor's name, {solicitor}
 * = the trader's name. see {@code CompanionshipBanter} for who speaks which.
 */
public final class CompanionshipLines extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "companionship");

    /** category -> list of entries; an entry is an ordered list of 1-2 lines. */
    private static Map<String, List<List<String>>> entries = Map.of();

    public CompanionshipLines() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = files.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            entries = Map.of();
            return;
        }
        Map<String, List<List<String>>> parsed = new HashMap<>();
        JsonObject root = element.getAsJsonObject();
        for (String key : root.keySet()) {
            JsonElement value = root.get(key);
            if (!value.isJsonArray()) {
                continue;
            }
            List<List<String>> bucket = new ArrayList<>();
            for (JsonElement entry : value.getAsJsonArray()) {
                List<String> lines = new ArrayList<>();
                if (entry.isJsonArray()) {
                    for (JsonElement line : entry.getAsJsonArray()) {
                        if (line.isJsonPrimitive()) {
                            lines.add(line.getAsString());
                        }
                    }
                } else if (entry.isJsonPrimitive()) {
                    lines.add(entry.getAsString());
                }
                if (!lines.isEmpty()) {
                    bucket.add(List.copyOf(lines));
                }
            }
            if (!bucket.isEmpty()) {
                parsed.put(key, List.copyOf(bucket));
            }
        }
        entries = Map.copyOf(parsed);
    }

    /** a random entry (1-2 lines) for {@code category}, or empty if none exist. */
    public static List<String> pick(String category, RandomSource random) {
        List<List<String>> bucket = entries.get(category);
        if (bucket == null || bucket.isEmpty()) {
            return List.of();
        }
        return bucket.get(random.nextInt(bucket.size()));
    }

    public static boolean has(String category) {
        List<List<String>> bucket = entries.get(category);
        return bucket != null && !bucket.isEmpty();
    }
}
