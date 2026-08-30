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
 * The Cutaway Gag's Marriage lines, from {@code data/witchmod/text/marriage.json} ({@code /reload}-able). Keys:
 * {@code open} (officiant opener), {@code vows}, {@code pronounce}, {@code objections}. {@code {v}} is swapped
 * for the victim's name and {@code {s}} for the spouse's name by the gag.
 */
public final class MarriageLines extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "marriage");

    private static Map<String, List<String>> lines = Map.of();

    public MarriageLines() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = files.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[Cutaway/Marriage] No usable {} - falling back to built-in lines.", FILE_ID);
            lines = Map.of();
            return;
        }
        Map<String, List<String>> parsed = new HashMap<>();
        JsonObject root = element.getAsJsonObject();
        for (String key : root.keySet()) {
            JsonElement value = root.get(key);
            if (!value.isJsonArray()) {
                continue;
            }
            List<String> bucket = new ArrayList<>();
            value.getAsJsonArray().forEach(line -> {
                if (line.isJsonPrimitive()) {
                    bucket.add(line.getAsString());
                }
            });
            if (!bucket.isEmpty()) {
                parsed.put(key, List.copyOf(bucket));
            }
        }
        lines = Map.copyOf(parsed);
    }

    /** A random line for {@code key}, or {@code fallback} if the list is missing/empty. */
    public static String pick(String key, RandomSource random, String fallback) {
        List<String> bucket = lines.get(key);
        return bucket == null || bucket.isEmpty() ? fallback : bucket.get(random.nextInt(bucket.size()));
    }
}
