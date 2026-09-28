package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * you've got the hiccups: every so often you involuntarily HOP with a hic, and sometimes it's a whole FIT —
 * a run of hiccups close together. Each one briefly stops you dead (a short movement freeze) so it interrupts
 * whatever you were doing.
 *
 * <p>State (next-hiccup tick + how many are left in the current fit) lives in a transient map, re-seeded on
 * {@code onTick} if it's missing — so a relog can't silence it. ⚠ The sound is a VANILLA placeholder
 * ({@code PLAYER_BURP}, pitched up) for now; a modded {@code curse.hiccups.hiccup} OGG is intended to replace
 * it later.
 */
public final class CurseHiccups extends Effect {
    /** per-player {nextHiccupTick, hiccupsLeftInFit}. */
    private static final Map<UUID, long[]> STATE = new HashMap<>();

    public CurseHiccups() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.DRIED_KELP);
    }

    /** discovered the first time you hiccup (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    private static long nextGap(RandomSource rng) {
        int min = Config.HICCUPS_MIN_GAP_TICKS.get();
        int max = Math.max(min + 1, Config.HICCUPS_MAX_GAP_TICKS.get());
        return min + rng.nextInt(max - min);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        RandomSource rng = target.getRandom();
        long[] s = STATE.computeIfAbsent(target.getUUID(), k -> new long[]{now + nextGap(rng), 0});

        if (now < s[0]) {
            return;
        }
        if (s[1] > 0) {
            // mid-fit: fire, then either continue the fit or return to the long gap.
            hiccup(target, level, rng);
            fitFart(target, rng); // gut-trouble synergy: a fit can shake a fart loose too
            s[1]--;
            s[0] = s[1] > 0 ? now + Config.HICCUPS_FIT_SPACING_TICKS.get() : now + nextGap(rng);
            return;
        }
        // A fresh hiccup — with a chance to spiral into a fit of several.
        hiccup(target, level, rng);
        if (rng.nextInt(100) < Config.HICCUPS_FIT_CHANCE_PERCENT.get()) {
            int min = Config.HICCUPS_FIT_MIN.get();
            int max = Math.max(min, Config.HICCUPS_FIT_MAX.get());
            s[1] = min + (max > min ? rng.nextInt(max - min + 1) : 0);
            s[0] = now + Config.HICCUPS_FIT_SPACING_TICKS.get();
            fitFart(target, rng);
        } else {
            s[0] = now + nextGap(rng);
        }
    }

    /** gut-trouble synergy: while gassy is also active, a hiccup fit has a chance to also let out a fart. */
    private static void fitFart(ServerPlayer target, RandomSource rng) {
        if (com.oliver.witchmod.synergy.Synergies.GUT_TROUBLE.activeFor(target)
                && rng.nextInt(100) < Config.HICCUPS_FART_CHANCE_PERCENT.get()) {
            CurseGassy.externalFart(target, rng.nextInt(100) < Config.GASSY_BIG_CHANCE.get());
        }
    }

    /** one hiccup: a little hop, a brief input freeze, and the hic sound. */
    private static void hiccup(ServerPlayer target, ServerLevel level, RandomSource rng) {
        Vec3 v = target.getDeltaMovement();
        // kill horizontal momentum (the "briefly stop") and pop up.
        target.setDeltaMovement(v.x * 0.15, Config.HICCUPS_HOP_POWER.get(), v.z * 0.15);
        target.hurtMarked = true;
        // A brief movement freeze via a synced input-lock (NOT Slowness) — so it doesn't shrink your FOV.
        target.setData(WitchModAttachments.HICCUPS_FREEZE_END, level.getGameTime() + Config.HICCUPS_FREEZE_TICKS.get());
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                WitchModSounds.HICCUPS_HICCUP.get(), SoundSource.PLAYERS, 0.9F, 0.95F + rng.nextFloat() * 0.15F);
        com.oliver.witchmod.effects.Curses.HICCUPS.value().markDiscoveredByVictim(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        STATE.remove(target.getUUID());
    }

    @Override
    public @Nullable String debugForce(ServerPlayer target, @Nullable String arg) {
        if (!(target.level() instanceof ServerLevel level)) {
            return null;
        }
        RandomSource rng = target.getRandom();
        if (arg != null && arg.equalsIgnoreCase("fit")) {
            // a whole fit at once — each hiccup rolls the gut-trouble fart (needs Gassy too for a fart).
            int n = Math.max(2, Config.HICCUPS_FIT_MIN.get());
            for (int i = 0; i < n; i++) {
                hiccup(target, level, rng);
                fitFart(target, rng);
            }
            return "hiccup fit! (arg omitted = a single hic)";
        }
        hiccup(target, level, rng);
        return "*hic!*";
    }

    @Override
    public java.util.List<String> debugArgs() {
        return java.util.List.of("fit");
    }
}
