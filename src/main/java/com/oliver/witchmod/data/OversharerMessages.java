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
 * oversharer templates ({@code data/witchmod/text/oversharer.json}, /reload-able) — keyed by leak kind, each
 * a list of lines with {value} (and optional {player}) placeholders. an empty/absent category is skipped.
 */
public final class OversharerMessages extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "oversharer");

    private static Map<String, List<String>> templates = Map.of();

    public OversharerMessages() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = files.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[Oversharer] No usable {} - nothing will be leaked.", FILE_ID);
            templates = Map.of();
            return;
        }
        Map<String, List<String>> parsed = new HashMap<>();
        JsonObject root = element.getAsJsonObject();
        for (String key : root.keySet()) {
            JsonElement value = root.get(key);
            if (!value.isJsonArray()) {
                continue; // malformed entry — skip, don't crash
            }
            List<String> lines = new ArrayList<>();
            value.getAsJsonArray().forEach(line -> {
                if (line.isJsonPrimitive()) {
                    lines.add(line.getAsString());
                }
            });
            if (!lines.isEmpty()) {
                parsed.put(key, List.copyOf(lines));
            }
        }
        templates = Map.copyOf(parsed);
    }

    /** categories with at least one line, so the curse only picks fillable ones. */
    public static List<String> availableCategories() {
        return List.copyOf(templates.keySet());
    }

    /** a random template line for a category, {value} not yet substituted. */
    @Nullable
    public static String pickTemplate(String category, RandomSource random) {
        List<String> lines = templates.get(category);
        return lines == null || lines.isEmpty() ? null : lines.get(random.nextInt(lines.size()));
    }
}
