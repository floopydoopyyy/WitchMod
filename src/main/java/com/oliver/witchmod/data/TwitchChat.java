package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import com.oliver.witchmod.WitchMod;

/** chat blessing's fake-twitch pool ({@code data/witchmod/text/twitch_chat.json}, /reload-able), keyed category → lines. */
public final class TwitchChat extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "twitch_chat");

    private static Map<String, List<String>> categories = Map.of();

    public TwitchChat() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = map.get(FILE_ID);
        if (element == null || !element.isJsonObject()) {
            WitchMod.LOGGER.warn("[TwitchChat] No data/witchmod/text/twitch_chat.json found; chat overlay will be sparse.");
            categories = Map.of();
            return;
        }
        Map<String, List<String>> loaded = new HashMap<>();
        element.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().startsWith("_") || !entry.getValue().isJsonArray()) {
                return; // skip _comment and non-arrays
            }
            List<String> lines = new ArrayList<>();
            entry.getValue().getAsJsonArray().forEach(e -> {
                if (e.isJsonPrimitive()) {
                    lines.add(e.getAsString());
                }
            });
            if (!lines.isEmpty()) {
                loaded.put(entry.getKey(), List.copyOf(lines));
            }
        });
        categories = Map.copyOf(loaded);
    }

    /** a random line from {@code category}, else {@code generic}, else null. */
    public static String pick(String category, RandomSource random) {
        List<String> lines = categories.get(category);
        if (lines == null || lines.isEmpty()) {
            lines = categories.get("generic");
        }
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return lines.get(random.nextInt(lines.size()));
    }

    public static boolean isLoaded() {
        return !categories.isEmpty();
    }
}
