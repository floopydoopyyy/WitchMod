package com.oliver.witchmod.data;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;

/**
 * funnel for who cursed/blessed whom (incl. blocked attempts). each logged cast is pushed at cast time into
 * the nearby Ledger blocks that persist it (see {@link com.oliver.witchmod.blocks.LedgerFeedback#record}) —
 * event-driven, no global store and no per-tick scanning. stores names, not uuids (display only).
 */
public final class LedgerLog {
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
        com.oliver.witchmod.blocks.LedgerFeedback.record(new Entry(casterName, targetName, effectId, result,
                gameTime, scribbled, Optional.ofNullable(pos), Optional.ofNullable(modifier)));
    }
}
