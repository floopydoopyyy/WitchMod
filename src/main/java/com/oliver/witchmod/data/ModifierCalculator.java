package com.oliver.witchmod.data;

import org.jetbrains.annotations.Nullable;

import com.oliver.witchmod.Config;

/**
 * the success/backfire formulas + modifier deltas as standalone functions, so the table's live odds preview
 * and the server's resolution share one path. the curve constants are read live from {@link Config}.
 */
public final class ModifierCalculator {
    private ModifierCalculator() {}

    public static int applyCost(int baseCost, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseCost;
        }
        return Math.round(baseCost * (1 + modifier.costDeltaPercent() / 100F));
    }

    public static int applyDuration(int baseDurationTicks, @Nullable Modifier modifier,
                                    net.minecraft.util.RandomSource rng) {
        if (modifier == null) {
            return baseDurationTicks;
        }
        // compass/clock override the whole duration; everything else is a % delta plus any flat bonus
        if (modifier.fixedDurationTicks().isPresent()) {
            return modifier.fixedDurationTicks().getAsInt();
        }
        int scaled = Math.round(baseDurationTicks * (1 + modifier.durationDeltaPercent() / 100F));
        return scaled + modifier.flatDurationBonusTicks(rng);
    }

    /** {@code successChance = min(cap, floor + span * (essenceSpent / baseCost))}. */
    public static float baseSuccessChance(int essenceSpent, int baseCost) {
        float ratio = baseCost <= 0 ? 1F : (float) essenceSpent / baseCost;
        float floor = Config.SUCCESS_FLOOR_PERCENT.get() / 100F;
        float span = Config.SUCCESS_SPAN_PERCENT.get() / 100F;
        return Math.min(successCap(), floor + span * ratio);
    }

    public static float applySuccessChance(float baseSuccessChance, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseSuccessChance;
        }
        float withDelta = baseSuccessChance + modifier.successDeltaPercent() / 100F;
        float cap = modifier.successCapPercent().isPresent() ? modifier.successCapPercent().getAsInt() / 100F : successCap();
        return Math.min(cap, withDelta);
    }

    /** {@code backfireChance = max(0%, start - (essenceSpent / baseCost) * start)}. */
    public static float baseBackfireChance(int essenceSpent, int baseCost) {
        float ratio = baseCost <= 0 ? 1F : (float) essenceSpent / baseCost;
        float start = Config.BACKFIRE_START_PERCENT.get() / 100F;
        return Math.max(0F, start - ratio * start);
    }

    private static float successCap() {
        return Config.SUCCESS_CAP_PERCENT.get() / 100F;
    }

    public static float applyBackfireChance(float baseBackfireChance, @Nullable Modifier modifier) {
        if (modifier == null) {
            return baseBackfireChance;
        }
        if (modifier.forcesBackfireZero()) {
            return 0F;
        }
        return Math.max(0F, baseBackfireChance + modifier.backfireDeltaPercent() / 100F);
    }

    /**
     * tier clamp on the backfire chance, keyed off {@code rawBaseCost} as a stand-in for a real per-attachment
     * tier: low-tier never backfires, high-tier keeps a small floor at full essence, mid-tier uses the curve.
     */
    public static float applyTierBackfire(float backfireChance, int rawBaseCost) {
        if (rawBaseCost <= Config.BACKFIRE_LOW_TIER_COST.get()) {
            return 0F;
        }
        if (rawBaseCost >= Config.BACKFIRE_HIGH_TIER_COST.get()) {
            return Math.max(Config.BACKFIRE_HIGH_TIER_FLOOR_PERCENT.get() / 100F, backfireChance);
        }
        return backfireChance;
    }
}
