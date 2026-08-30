package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import com.oliver.witchmod.WitchMod;

/**
 * A shared pool of made-up usernames, from {@code data/witchmod/text/usernames.json} ({@code /reload}-able) —
 * a plain JSON array of strings. Used to stand in for a real player when one is needed but none is around:
 * the Hype Man blessing's "sighting" praise attributes itself to one of these when you're alone, and the
 * Chat (Twitch overlay) blessing draws its chatters' names from the same list.
 */
public final class Usernames extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "usernames");

    private static List<String> names = List.of();

    public Usernames() {
        super(GSON, "text");
    }

    @Override
    protected void apply(java.util.Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = map.get(FILE_ID);
        if (element == null || !element.isJsonArray()) {
            WitchMod.LOGGER.warn("[Usernames] No data/witchmod/text/usernames.json found; using a plain fallback.");
            names = List.of();
            return;
        }
        List<String> loaded = new ArrayList<>();
        element.getAsJsonArray().forEach(e -> {
            if (e.isJsonPrimitive()) {
                loaded.add(e.getAsString());
            }
        });
        names = List.copyOf(loaded);
    }

    /** A random made-up username, or "a fan" if the list is empty. */
    public static String random(RandomSource random) {
        return names.isEmpty() ? "a fan" : names.get(random.nextInt(names.size()));
    }
}
