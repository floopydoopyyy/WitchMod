package com.oliver.witchmod.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;

/**
 * in-memory log of who cursed/blessed whom (incl. blocked attempts). entries carry the world position + the
 * modifier used so a ledger block can filter to its range. stores names, not uuids (display only); not
 * persisted — per session.
 */
public final class LedgerLog {
    private static final int MAX_ENTRIES = 400;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private LedgerLog() {}

    public record Entry(Optional<String> casterName, String targetName, ResourceLocation effectId, String result,
                        long gameTime, boolean scribbled, Optional<GlobalPos> pos, Optional<String> modifier) {}

    public static void log(Optional<String> casterName, String targetName, ResourceLocation effectId, String result, long gameTime) {
        log(casterName, targetName, effectId, result, gameTime, false);
    }

    /** scribbled entries (the paper modifier) render as an unreadable scrawl. */
    public static void log(Optional<String> casterName, String targetName, ResourceLocation effectId, String result,
                           long gameTime, boolean scribbled) {
        log(casterName, targetName, effectId, result, gameTime, scribbled, null, null);
    }

    /** full entry: {@code pos} is where it happened (for range filtering); both nullable. */
    public static void log(Optional<String> casterName, String targetName, ResourceLocation effectId, String result,
                           long gameTime, boolean scribbled, @Nullable GlobalPos pos, @Nullable String modifier) {
        ENTRIES.addLast(new Entry(casterName, targetName, effectId, result, gameTime, scribbled,
                Optional.ofNullable(pos), Optional.ofNullable(modifier)));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
        }
    }

    public static List<Entry> recent() {
        return List.copyOf(ENTRIES);
    }

    /** entries within {@code range} of {@code center} (same dimension), newest first. */
    public static List<Entry> entriesNear(GlobalPos center, double range) {
        double r2 = range * range;
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.pos().isEmpty()) {
                continue;
            }
            GlobalPos gp = e.pos().get();
            if (gp.dimension().equals(center.dimension()) && gp.pos().distSqr(center.pos()) <= r2) {
                out.add(e);
            }
        }
        Collections.reverse(out);
        return out;
    }
}
