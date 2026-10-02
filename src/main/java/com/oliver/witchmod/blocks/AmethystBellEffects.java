package com.oliver.witchmod.blocks;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * the bell's aoe gamble. outcome is decided at ring time ({@link #decide}) then resolved after the consume
 * ramp ({@link #apply}): an unaffected player may be gifted a batch; an affected one has each effect swapped
 * for a same-category one at similar power (rare high/low swings). power uses {@link Effect#powerLevel()}.
 * protection is bypassed on purpose (it's an area ritual, like a thrown jar).
 */
public final class AmethystBellEffects {
    // Shared amethyst/pink palette (also used by the block + block entity FX).
    public static final DustParticleOptions AMETHYST = new DustParticleOptions(new Vector3f(0.66F, 0.36F, 0.82F), 1.4F);
    public static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(0.96F, 0.32F, 0.72F), 1.5F);
    public static final DustParticleOptions PINK_SMALL = new DustParticleOptions(new Vector3f(0.98F, 0.55F, 0.82F), 1.0F);

    /** How long the purple "consume" ramp runs before the effects land / it fizzles. */
    public static int applyRampTicks() { return Config.BELL_APPLY_RAMP_TICKS.get(); }
    public static int fizzleRampTicks() { return Config.BELL_FIZZLE_RAMP_TICKS.get(); }

    /** What the bell decided to do to a caught player — chosen at ring time, resolved after the ramp. */
    public enum Outcome { SWAP, ADD, FIZZLE }

    private AmethystBellEffects() {}

    /** Decide a caught player's fate at ring time (so the ramp knows how long to run + how heavy to be). */
    public static Outcome decide(ServerPlayer p, RandomSource rng) {
        Map<ResourceLocation, Integer> snap = EffectManager.activeSnapshot(p);
        boolean hasReal = snap.keySet().stream().anyMatch(AmethystBellEffects::isRerollable);
        if (hasReal) {
            return Outcome.SWAP;
        }
        return rng.nextInt(100) < Config.BELL_ADD_CHANCE_PERCENT.get() ? Outcome.ADD : Outcome.FIZZLE;
    }

    /** Resolve the pre-decided outcome once the ramp completes. */
    public static void apply(ServerLevel level, ServerPlayer p, Outcome outcome) {
        if (!p.isAlive()) {
            return;
        }
        RandomSource rng = p.getRandom();
        switch (outcome) {
            case SWAP -> {
                swapAll(p, EffectManager.activeSnapshot(p), rng);
                swapFx(level, p);
                p.displayClientMessage(Component.translatable("witchmod.amethyst_bell.rerolled")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            case ADD -> {
                int added = addAttachments(p, rng);
                if (added > 0) {
                    addFx(level, p);
                    p.displayClientMessage(Component.translatable("witchmod.amethyst_bell.gifted")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                } else {
                    fizzleOut(level, p);
                }
            }
            case FIZZLE -> fizzleOut(level, p);
        }
    }

    private static int addAttachments(ServerPlayer p, RandomSource rng) {
        int one = Config.BELL_ADD_ONE_WEIGHT.get();
        int two = Config.BELL_ADD_TWO_WEIGHT.get();
        int r = rng.nextInt(Math.max(1, one + two + 15));
        int count = r < one ? 1 : r < one + two ? 2 : 3;
        int added = 0;
        int durMin = Config.RITUAL_MIN_DURATION_TICKS.get();
        int durMax = Math.max(durMin, Config.RITUAL_MAX_DURATION_TICKS.get());
        for (int i = 0; i < count; i++) {
            EffectCategory cat = rng.nextBoolean() ? EffectCategory.CURSE : EffectCategory.BLESSING;
            Holder.Reference<Effect> pick = pickByPower(cat, rollAddPower(rng), null, rng);
            if (pick == null) {
                continue;
            }
            int dur = durMin + rng.nextInt(durMax - durMin + 1);
            // DIRECT_HIT so a Ward/Totem doesn't stop the gamble; still respects the per-category hard cap.
            if (EffectManager.apply(p, pick, dur, null, EffectManager.ApplyOptions.DIRECT_HIT)) {
                added++;
            }
        }
        return added;
    }

    private static void swapAll(ServerPlayer p, Map<ResourceLocation, Integer> snap, RandomSource rng) {
        for (Map.Entry<ResourceLocation, Integer> e : snap.entrySet()) {
            ResourceLocation id = e.getKey();
            if (!isRerollable(id)) {
                continue; // leave hidden/internal states (infectious) alone
            }
            Holder.Reference<Effect> current = EffectManager.holderOf(id).orElse(null);
            if (current == null) {
                continue;
            }
            int curPower = current.value().powerLevel();
            int maxPower = Config.BELL_SWAP_MAX_POWER.get();
            int jitter = Config.BELL_SWAP_SIMILAR_JITTER.get();
            int target;
            int roll = rng.nextInt(100);
            int high = Config.BELL_SWAP_HIGH_CHANCE_PERCENT.get();
            int low = Config.BELL_SWAP_LOW_CHANCE_PERCENT.get();
            if (roll < high) {
                target = curPower + rng.nextInt(Math.max(1, maxPower - curPower + 1)); // high roll: bump up toward max
            } else if (roll < high + low) {
                target = rng.nextInt(curPower + 1);                                    // low roll: crash, even to 0
            } else {
                target = net.minecraft.util.Mth.clamp(
                        curPower - jitter + rng.nextInt(jitter * 2 + 1), 0, maxPower);
            }
            Holder.Reference<Effect> pick = pickByPower(current.value().category(), target, id, rng);
            if (pick == null) {
                continue;
            }
            EffectManager.remove(p, current);
            // applyExact preserves the remaining duration verbatim (a re-roll keeps your time left).
            EffectManager.applyExact(p, pick, e.getValue(), null);
        }
    }

    /** 40% low (0-40), 45% mid (41-70), 15% high (71-85). */
    private static int rollAddPower(RandomSource rng) {
        int low = Config.BELL_POWER_LOW_WEIGHT.get();
        int mid = Config.BELL_POWER_MID_WEIGHT.get();
        int r = rng.nextInt(Math.max(1, low + mid + 15));
        if (r < low) {
            return rng.nextInt(41);           // low band 0-40
        }
        if (r < low + mid) {
            return 41 + rng.nextInt(30);       // mid band 41-70
        }
        return 71 + rng.nextInt(15);           // high band 71-85
    }

    /** A selectable effect of {@code cat} near {@code target} power — nearer is far more likely (slight randomness). */
    @Nullable
    private static Holder.Reference<Effect> pickByPower(EffectCategory cat, int target, @Nullable ResourceLocation exclude, RandomSource rng) {
        List<Holder.Reference<Effect>> pool = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(h -> com.oliver.witchmod.data.SpecialAttachments.inRandomPools(h.value()))
                .filter(h -> h.value().category() == cat)
                .filter(h -> exclude == null || !h.key().location().equals(exclude))
                .toList();
        if (pool.isEmpty()) {
            return null;
        }
        double[] w = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < pool.size(); i++) {
            int d = Math.abs(pool.get(i).value().powerLevel() - target);
            w[i] = 1.0 / ((d + 1.0) * (d + 1.0));
            total += w[i];
        }
        double roll = rng.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= w[i];
            if (roll <= 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Only real, selectable curses/blessings get re-rolled — infectious/internal states are skipped. */
    private static boolean isRerollable(ResourceLocation id) {
        return EffectManager.holderOf(id).map(h -> h.value().selectable()).orElse(false);
    }

    // --- Per-player FX ---------------------------------------------------------------------------------

    /**
     * The "consume" ramp: purple dust progressively envelops the player over the delay, growing quadratically
     * so it climaxes right before the effects land. A pre-decided FIZZLE gets far fewer motes and no inward
     * stream. {@code progress} runs 0→1 across the ramp. CLIENT-RENDERED (addParticle) off the synced consume
     * attachment — the server sends no consume particles, so it doesn't lag with several players caught.
     */
    public static void emitConsume(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player p,
                                   float progress, boolean fizzle) {
        RandomSource rng = level.getRandom();
        double w = p.getBbWidth() * 0.6;
        double h = p.getBbHeight();
        double cx = p.getX(), cy = p.getY(), cz = p.getZ();
        int count = fizzle ? Math.round(1 + progress * 5) : Math.round(2 + progress * progress * 40);
        for (int i = 0; i < count; i++) {
            level.addParticle(AMETHYST, cx + (rng.nextDouble() - 0.5) * 2 * w, cy + rng.nextDouble() * h,
                    cz + (rng.nextDouble() - 0.5) * 2 * w, 0.0, 0.01, 0.0);
        }
        if (fizzle) {
            return;
        }
        int inward = Math.round(progress * 8);
        for (int i = 0; i < inward; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 1.4 * (1.0 - progress) + 0.5;
            double px = cx + Math.cos(a) * r, pz = cz + Math.sin(a) * r, py = cy + rng.nextDouble() * h;
            level.addParticle(PINK, px, py, pz, (cx - px) * 0.18, 0.0, (cz - pz) * 0.18);
        }
    }

    private static void addFx(ServerLevel level, ServerPlayer p) {
        double x = p.getX(), y = p.getY() + 1.0, z = p.getZ();
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 24, 0.3, 0.5, 0.3, 0.05);
        level.sendParticles(PINK, x, y, z, 30, 0.4, 0.6, 0.4, 0.03);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.4, z, 40, 0.2, 0.4, 0.2, 0.9);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5F, 1.7F);
    }

    private static void swapFx(ServerLevel level, ServerPlayer p) {
        double x = p.getX(), y = p.getY() + 1.0, z = p.getZ();
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            level.sendParticles(ParticleTypes.WITCH, x + Math.cos(a) * 0.5, y - 0.4, z + Math.sin(a) * 0.5, 0, Math.cos(a) * 0.2, 0.25, Math.sin(a) * 0.2, 1.0);
        }
        level.sendParticles(AMETHYST, x, y, z, 30, 0.35, 0.55, 0.35, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 16, 0.25, 0.45, 0.25, 0.04);
        level.sendParticles(PINK, x, y, z, 20, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.9F);
    }

    /** A pre-decided fizzle (or a rare add that landed nothing): a small poof, a soft break, and a note. */
    private static void fizzleOut(ServerLevel level, ServerPlayer p) {
        double x = p.getX(), y = p.getY() + 1.0, z = p.getZ();
        level.sendParticles(PINK_SMALL, x, y, z, 8, 0.25, 0.35, 0.25, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.2, 0.3, 0.2, 0.01);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.5F, 0.8F);
        p.displayClientMessage(Component.translatable("witchmod.amethyst_bell.nothing")
                .withStyle(ChatFormatting.GRAY), true);
    }
}
