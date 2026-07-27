package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

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
 * The Hype Man blessing's praise, from {@code data/witchmod/text/hypeman.json} ({@code /reload}-able). Keyed
 * by what the blessed player just did — {@code combat}, {@code pickup}, {@code loot}, {@code nearby} — each a
 * writable list of lines. {@code {player}} in a line is swapped for the blessed player's username, so most
 * lines can name them directly.
 */
public final class HypeManMessages extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "hypeman");

    private static Map<String, List<String>> lines = Map.of();

    public HypeManMessages() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = files.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[HypeMan] No usable {} - the crowd stays quiet.", FILE_ID);
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

    @Nullable
    public static String pick(String key, RandomSource random) {
        List<String> bucket = lines.get(key);
        return bucket == null || bucket.isEmpty() ? null : bucket.get(random.nextInt(bucket.size()));
    }
}
