package com.oliver.witchmod.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import com.oliver.witchmod.WitchMod;

/**
 * per-effect power level (0–100) from a hand-editable {@code config/witchmod-power-levels.json}; drives the
 * compendium pips and the gamble bias. merged + rewritten on load (kept values, new effects at {@link
 * #DEFAULT}, removed ones pruned), re-read at setup / server start / {@code /reload}.
 */
public final class PowerLevels {
    private PowerLevels() {}

    /** value used for an effect not (yet) listed in the file. */
    public static final int DEFAULT = 50;
    public static final int MIN = 0;
    public static final int MAX = 100;

    private static final String FILE_NAME = "witchmod-power-levels.json";
    /** bundled-in-the-jar defaults (src/main/resources/witchmod-power-levels.json) — the numbers that SHIP. */
    private static final String BUNDLED_RESOURCE = "/witchmod-power-levels.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String README =
            "Power level 0-100 for every curse and blessing. It sets the Compendium's power rating, the "
            + "essence price/success bias, and the coin/gamble odds. Edit the numbers freely (clamped to 0-100). "
            + "New effects appear at " + DEFAULT + " and removed ones are pruned automatically on the next load; "
            + "edit then /reload (or restart) to apply.";

    /** id -> power, filled by {@link #load}. */
    private static volatile Map<ResourceLocation, Integer> VALUES = Map.of();

    /** the configured power (0–100) for {@code effect}, or {@link #DEFAULT} if it isn't listed. */
    public static int get(Effect effect) {
        ResourceLocation id = WitchModRegistries.EFFECT_REGISTRY.getKey(effect);
        Integer v = id == null ? null : VALUES.get(id);
        return v == null ? DEFAULT : v;
    }

    /** reads the file (creating/merging it against the live effect registry) and refreshes the lookup map. */
    public static synchronized void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);

        // existing user values (by id path), so an edit is never clobbered by the merge/rewrite.
        Map<String, Integer> existingCurses = new HashMap<>();
        Map<String, Integer> existingBlessings = new HashMap<>();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null) {
                    readSection(root, "curses", existingCurses);
                    readSection(root, "blessings", existingBlessings);
                }
            } catch (Exception e) {
                WitchMod.LOGGER.error("[PowerLevels] Failed to read {} — using defaults this load, not overwriting it.",
                        FILE_NAME, e);
                // don't rewrite a file we couldn't parse (would destroy the user's work); just fall back.
                VALUES = Map.of();
                return;
            }
        }

        // defaults that ship inside the jar (edit src/main/resources/witchmod-power-levels.json to change what
        // every fresh install starts at). The on-disk config always wins where it has a value.
        Map<String, Integer> bundledCurses = new HashMap<>();
        Map<String, Integer> bundledBlessings = new HashMap<>();
        readBundled(bundledCurses, bundledBlessings);

        // merge against the live registry: on-disk value → bundled default → 50; drop the unknown.
        Map<ResourceLocation, Integer> resolved = new HashMap<>();
        TreeMap<String, Integer> curseOut = new TreeMap<>();
        TreeMap<String, Integer> blessingOut = new TreeMap<>();
        for (Holder.Reference<Effect> holder : WitchModRegistries.EFFECT_REGISTRY.holders().toList()) {
            Effect effect = holder.value();
            if (!effect.selectable()) {
                continue; // internal attachments (e.g. Infectious) have no power to tune
            }
            String key = holder.key().location().getPath();
            boolean curse = effect.category() == EffectCategory.CURSE;
            Integer prev = (curse ? existingCurses : existingBlessings).get(key);
            Integer bundled = (curse ? bundledCurses : bundledBlessings).get(key);
            int value = clamp(prev != null ? prev : (bundled != null ? bundled : DEFAULT));
            (curse ? curseOut : blessingOut).put(key, value);
            resolved.put(holder.key().location(), value);
        }
        VALUES = resolved;
        write(path, curseOut, blessingOut);
    }

    /** reads the jar-bundled default numbers (absent in dev until you add the resource — that's fine). */
    private static void readBundled(Map<String, Integer> curses, Map<String, Integer> blessings) {
        try (InputStream in = PowerLevels.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                return; // no bundled defaults shipped — everything falls back to DEFAULT
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null) {
                    readSection(root, "curses", curses);
                    readSection(root, "blessings", blessings);
                }
            }
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[PowerLevels] Could not read bundled defaults {}", BUNDLED_RESOURCE, e);
        }
    }

    private static void readSection(JsonObject root, String name, Map<String, Integer> into) {
        if (!root.has(name) || !root.get(name).isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(name).entrySet()) {
            try {
                into.put(e.getKey(), e.getValue().getAsInt());
            } catch (RuntimeException ignored) {
                // a non-numeric value — skip it, the merge will re-default that key
            }
        }
    }

    private static void write(Path path, TreeMap<String, Integer> curses, TreeMap<String, Integer> blessings) {
        JsonObject root = new JsonObject();
        root.addProperty("_README", README);
        root.add("curses", section(curses));
        root.add("blessings", section(blessings));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            WitchMod.LOGGER.error("[PowerLevels] Failed to write {}", FILE_NAME, e);
        }
    }

    private static JsonObject section(TreeMap<String, Integer> values) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            obj.addProperty(e.getKey(), e.getValue());
        }
        return obj;
    }

    private static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    /** for a note in the file's log line. */
    public static List<ResourceLocation> knownIds() {
        return new ArrayList<>(VALUES.keySet());
    }
}
