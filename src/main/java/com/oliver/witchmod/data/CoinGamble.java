package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

import com.oliver.witchmod.items.WitchModItems;

/**
 * Rolls the multi-effect gamble behind the three coins — used both when a coin is USED as an item (gambles on
 * the user) and when it's placed as a Sacrificial Item at the Bewitching Table (gambles on the target).
 *
 * <ul>
 *   <li><b>Blessed Coin</b> — 1–3 blessings, biased toward FEWER and LOWER-power ones.</li>
 *   <li><b>Cursed Coin</b> — 1–3 curses, biased toward fewer + lower-power. A {@link #BAD_DAY_PERCENT}% "bad
 *       day" instead lands 3 curses of {@link #BAD_DAY_MIN_POWER}+ power.</li>
 *   <li><b>Executioner's Coin</b> — 1–3 curses OR blessings, no bias, fully random.</li>
 * </ul>
 *
 * <p>Power bias reads {@link Effect#powerLevel()}, which is a placeholder 50 for every effect until the
 * strength-balancing pass — so the bias is structurally correct but currently behaves as uniform, and a "bad
 * day" (no 80+ effects yet) falls back to 3 random curses.
 */
public final class CoinGamble {
    private CoinGamble() {}

    public enum Type { CURSED, BLESSED, EXECUTIONER }

    /** Essence base cost the ritual uses for a coin (they land multiple effects, so it's pricey). */
    public static final int BASE_COST = 55;
    public static final int BAD_DAY_PERCENT = 5;
    public static final int BAD_DAY_MIN_POWER = 80;
    private static final int[] FEWER_WEIGHTS = {60, 30, 10};   // 1 / 2 / 3 effects — biased to fewer
    private static final int[] UNIFORM_WEIGHTS = {1, 1, 1};

    /** Which coin (if any) this item is, for the ritual + slot validity + item-use. */
    @Nullable
    public static Type typeOf(Item item) {
        if (item == WitchModItems.CURSED_COIN.get()) {
            return Type.CURSED;
        }
        if (item == WitchModItems.BLESSED_COIN.get()) {
            return Type.BLESSED;
        }
        if (item == WitchModItems.EXECUTIONERS_COIN.get()) {
            return Type.EXECUTIONER;
        }
        return null;
    }

    /** Rolls (but does not apply) the coin's effects. */
    public static List<Holder.Reference<Effect>> roll(Type type, RandomSource rng) {
        boolean badDay = type == Type.CURSED && rng.nextInt(100) < BAD_DAY_PERCENT;
        List<Holder.Reference<Effect>> pool = poolFor(type, badDay);
        if (pool.isEmpty()) {
            return List.of();
        }
        int count = badDay ? 3 : pickCount(type, rng);
        return pickDistinct(pool, count, type, badDay, rng);
    }

    /** Rolls AND applies the coin's effects to {@code target}; returns what landed (for feedback). */
    public static List<Holder.Reference<Effect>> gamble(ServerPlayer target, Type type, int durationTicks,
                                                        @Nullable ServerPlayer caster, RandomSource rng) {
        List<Holder.Reference<Effect>> chosen = roll(type, rng);
        for (Holder.Reference<Effect> effect : chosen) {
            EffectManager.apply(target, effect, durationTicks, caster);
        }
        return chosen;
    }

    /** Whether a given roll is a "bad day" — call {@link #roll} which handles it; exposed for messaging only. */
    public static boolean isBadDay(List<Holder.Reference<Effect>> rolled) {
        return rolled.size() == 3 && rolled.stream().allMatch(h -> h.value().category() == EffectCategory.CURSE
                && h.value().powerLevel() >= BAD_DAY_MIN_POWER);
    }

    private static List<Holder.Reference<Effect>> poolFor(Type type, boolean badDay) {
        List<Holder.Reference<Effect>> all = WitchModRegistries.EFFECT_REGISTRY.holders().toList();
        List<Holder.Reference<Effect>> pool = new ArrayList<>();
        for (Holder.Reference<Effect> h : all) {
            if (!h.value().selectable()) {
                continue; // internal attachments (e.g. the Infectious state) are never rolled
            }
            EffectCategory cat = h.value().category();
            boolean ok = switch (type) {
                case CURSED -> cat == EffectCategory.CURSE;
                case BLESSED -> cat == EffectCategory.BLESSING;
                case EXECUTIONER -> true;
            };
            if (ok) {
                pool.add(h);
            }
        }
        if (badDay) {
            List<Holder.Reference<Effect>> strong = pool.stream()
                    .filter(h -> h.value().powerLevel() >= BAD_DAY_MIN_POWER).toList();
            if (!strong.isEmpty()) {
                return new ArrayList<>(strong);
            }
            // No 80+ curses yet (powers are placeholders) — a bad day is still 3 random curses.
        }
        return pool;
    }

    private static int pickCount(Type type, RandomSource rng) {
        int[] weights = type == Type.EXECUTIONER ? UNIFORM_WEIGHTS : FEWER_WEIGHTS;
        int total = weights[0] + weights[1] + weights[2];
        int r = rng.nextInt(total);
        if (r < weights[0]) {
            return 1;
        }
        return r < weights[0] + weights[1] ? 2 : 3;
    }

    private static List<Holder.Reference<Effect>> pickDistinct(List<Holder.Reference<Effect>> pool, int count,
                                                               Type type, boolean badDay, RandomSource rng) {
        List<Holder.Reference<Effect>> avail = new ArrayList<>(pool);
        List<Holder.Reference<Effect>> chosen = new ArrayList<>();
        boolean uniform = badDay || type == Type.EXECUTIONER;
        for (int i = 0; i < count && !avail.isEmpty(); i++) {
            double total = 0;
            double[] w = new double[avail.size()];
            for (int k = 0; k < avail.size(); k++) {
                w[k] = uniform ? 1.0 : Math.max(1, 101 - avail.get(k).value().powerLevel());
                total += w[k];
            }
            double r = rng.nextDouble() * total;
            int idx = 0;
            for (; idx < avail.size() - 1; idx++) {
                r -= w[idx];
                if (r <= 0) {
                    break;
                }
            }
            chosen.add(avail.remove(idx));
        }
        return chosen;
    }
}
