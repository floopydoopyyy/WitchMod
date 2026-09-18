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

/** echoes' fake chat lines ({@code data/witchmod/text/echoes_chat.json}, /reload-able) — shown to the victim in a real online player's mouth. */
public final class EchoesChatMessages extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "echoes_chat");

    private static List<String> lines = List.of();

    public EchoesChatMessages() {
        super(GSON, "text"); // scans data/<namespace>/text/*.json
    }

    @Override
    protected void apply(java.util.Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = map.get(FILE_ID);
        if (element == null || !element.isJsonArray()) {
            WitchMod.LOGGER.warn("[Echoes] No data/witchmod/text/echoes_chat.json found; fake chat disabled.");
            lines = List.of();
            return;
        }
        List<String> loaded = new ArrayList<>();
        element.getAsJsonArray().forEach(e -> {
            if (e.isJsonPrimitive()) {
                loaded.add(e.getAsString());
            }
        });
        lines = List.copyOf(loaded);
    }

    /** a random line, or null if none written (caller falls back to another hallucination). */
    public static String pick(RandomSource random) {
        return lines.isEmpty() ? null : lines.get(random.nextInt(lines.size()));
    }
}
