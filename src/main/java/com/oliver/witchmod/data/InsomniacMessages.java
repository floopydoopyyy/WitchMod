package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import com.oliver.witchmod.WitchMod;

/** insomniac's "can't sleep" excuses ({@code data/witchmod/text/insomniac.json}, /reload-able) — one shown per failed bedtime. */
public final class InsomniacMessages extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "insomniac");
    private static final List<String> FALLBACK = List.of("You're not even tired.");

    private static List<String> lines = FALLBACK;

    public InsomniacMessages() {
        super(GSON, "text");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement element = files.get(FILE_ID);
        if (element == null || !element.isJsonArray()) {
            WitchMod.LOGGER.warn("[Insomniac] No usable {} - falling back to one line.", FILE_ID);
            lines = FALLBACK;
            return;
        }
        List<String> loaded = new ArrayList<>();
        element.getAsJsonArray().forEach(e -> {
            if (e.isJsonPrimitive()) {
                loaded.add(e.getAsString());
            }
        });
        lines = loaded.isEmpty() ? FALLBACK : List.copyOf(loaded);
    }

    @Nullable
    public static String pick(RandomSource random) {
        return lines.isEmpty() ? null : lines.get(random.nextInt(lines.size()));
    }
}
