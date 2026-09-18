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

/**
 * bodyguard patter ({@code data/witchmod/text/bodyguard.json}, /reload-able), keyed by state. each state
 * holds dialogue trees (a sequence delivered line-by-line; a bare string = a one-liner tree). {player}=intruder.
 */
public final class BodyguardLines extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "bodyguard");

    /** state -> list of trees; a tree is an ordered list of lines. */
    private static Map<String, List<List<String>>> trees = Map.of();

    public BodyguardLines() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = files.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[Bodyguard] No usable {} - the bodyguard will work in silence.", FILE_ID);
            trees = Map.of();
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
                List<String> tree = new ArrayList<>();
                if (entry.isJsonArray()) {
                    for (JsonElement line : entry.getAsJsonArray()) {
                        if (line.isJsonPrimitive()) {
                            tree.add(line.getAsString());
                        }
                    }
                } else if (entry.isJsonPrimitive()) {
                    tree.add(entry.getAsString()); // a bare string is a one-line tree
                }
                if (!tree.isEmpty()) {
                    bucket.add(List.copyOf(tree));
                }
            }
            if (!bucket.isEmpty()) {
                parsed.put(key, List.copyOf(bucket));
            }
        }
        trees = Map.copyOf(parsed);
    }

    /** a random dialogue tree for {@code state}, or empty if none. */
    public static List<String> pickTree(String state, RandomSource random) {
        List<List<String>> bucket = trees.get(state);
        if (bucket == null || bucket.isEmpty()) {
            return List.of();
        }
        return bucket.get(random.nextInt(bucket.size()));
    }

    public static boolean has(String state) {
        List<List<String>> bucket = trees.get(state);
        return bucket != null && !bucket.isEmpty();
    }
}
