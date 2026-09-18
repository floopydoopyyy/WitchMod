package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * narcolepsy (White Bed): every {@code narcolepsyMinInterval}..{@code narcolepsyMaxInterval} (35s–5.5min,
 * biased toward the longer half) you drop asleep right where you stand — the sleeping pose, all input blocked,
 * a dark shader, and a mash bar you have to overcome to wake early (see {@code client/NarcolepsyClient}). A
 * sleep lasts {@code narcolepsyMinSleep}..{@code narcolepsyMaxSleep} (4–12s) if you don't fight out of it.
 *
 * <p>The server owns the schedule + the synced end tick ({@link WitchModAttachments#NARCOLEPSY_SLEEP_END});
 * the client runs the overlay + mashing and sends {@link com.oliver.witchmod.network.WitchModNetwork.NarcolepsyWakePayload}
 * when you mash free, which calls {@link #wakeEarly}. The lying-down pose is re-asserted each tick so vanilla's
 * own sleep bookkeeping can't quietly cancel it.
 */
public final class CurseNarcolepsy extends Effect {
    /** rider UUID -> game tick the next sleep is due (transient; re-scheduled on relog like Yap). */
    private static final Map<UUID, Long> NEXT_SLEEP = new HashMap<>();
    /** rider UUID -> ticks stood still, for the idle-accelerated countdown. */
    private static final Map<UUID, Integer> IDLE_TICKS = new HashMap<>();
    /** rider UUID -> last position, to measure stillness. */
    private static final Map<UUID, net.minecraft.world.phys.Vec3> LAST_POS = new HashMap<>();

    public CurseNarcolepsy() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> net.minecraft.world.item.Items.WHITE_BED);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true; // discovers the first time they nod off
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.NARCOLEPSY_SLEEP_END, 0L);
        scheduleNext(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        if (isSleeping(target)) {
            wake(target);
        }
        NEXT_SLEEP.remove(target.getUUID());
        IDLE_TICKS.remove(target.getUUID());
        LAST_POS.remove(target.getUUID());
        target.setData(WitchModAttachments.NARCOLEPSY_SLEEP_END, 0L);
        target.setData(WitchModAttachments.NARCOLEPSY_DEPTH, 0);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        long now = target.level().getGameTime();
        long sleepEnd = target.getData(WitchModAttachments.NARCOLEPSY_SLEEP_END);

        if (sleepEnd > 0) {
            if (now >= sleepEnd) {
                wake(target);
            } else {
                // NO server-side sleep: the player stays a fully normal entity (gravity, fall damage, collision
                // all vanilla) — the lying-down ANIMATION is faked purely client-side on other viewers (see
                // narcolepsyClient), which is also what stopped the local camera juddering. Only the effects
                // that don't touch physics live here.
                // fast healing while you rest — 30% slower than before (every 7 ticks, not 5).
                if (target.tickCount % 7 == 0 && target.getHealth() < target.getMaxHealth()) {
                    target.heal(1.0F);
                }
                // intermittent snores at varied pitch.
                if (target.tickCount % 45 == 0) {
                    target.level().playSound(null, target.blockPosition(),
                            com.oliver.witchmod.data.WitchModSounds.NARCOLEPSY_SNORE.get(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.7F + target.getRandom().nextFloat() * 0.6F);
                }
                // sleep Zs drifting up off the head, seen by every viewer.
                if (target.tickCount % 11 == 0) {
                    target.serverLevel().sendParticles(com.oliver.witchmod.data.WitchModParticles.SLEEP_Z.get(),
                            target.getX(), target.getEyeY() + 0.25, target.getZ(),
                            1, 0.08, 0.02, 0.08, 0.0);
                }
            }
            return;
        }

        // awake: idling makes a narcoleptic nod off sooner — after a few still seconds the countdown to the
        // next sleep runs at DOUBLE speed.
        tickIdleAccel(target, now);

        Long next = NEXT_SLEEP.get(target.getUUID());
        if (next == null) {
            scheduleNext(target); // self-heal after a relog (the transient schedule was lost)
        } else if (now >= next) {
            startSleep(target);
        }
    }

    /** while awake and standing still past the grace period, pull the next-sleep tick one closer each tick (2x). */
    private void tickIdleAccel(ServerPlayer target, long now) {
        UUID id = target.getUUID();
        net.minecraft.world.phys.Vec3 pos = target.position();
        net.minecraft.world.phys.Vec3 last = LAST_POS.put(id, pos);
        boolean still = last != null && last.distanceToSqr(pos) < 0.0016; // ~0.04 blocks of drift = "still"
        int idle = still ? IDLE_TICKS.getOrDefault(id, 0) + 1 : 0;
        IDLE_TICKS.put(id, idle);
        int grace = Config.NARCOLEPSY_IDLE_ACCEL_TICKS.get();
        if (idle > grace) {
            Long next = NEXT_SLEEP.get(id);
            if (next != null && next > now) {
                NEXT_SLEEP.put(id, next - 1); // one extra tick shaved off → the countdown ticks down twice as fast
            }
        }
    }

    private void startSleep(ServerPlayer target) {
        startSleep(target, rollDepth(target), false);
    }

    /** begin a sleep of the given depth (0 normal / 1 deep / 2 very deep); {@code thirdPerson} = debug watch mode. */
    private void startSleep(ServerPlayer target, int depth, boolean thirdPerson) {
        int min = Config.NARCOLEPSY_MIN_SLEEP_TICKS.get();
        int max = Config.NARCOLEPSY_MAX_SLEEP_TICKS.get();
        int duration = min + target.getRandom().nextInt(Math.max(1, max - min + 1));
        // deeper sleeps last longer (and need more mashing — enforced client-side off the depth attachment).
        double lengthMult = switch (depth) { case 2 -> 1.8; case 1 -> 1.4; default -> 1.0; };
        duration = (int) Math.round(duration * lengthMult);
        target.setData(WitchModAttachments.NARCOLEPSY_SLEEP_END, target.level().getGameTime() + duration);
        target.setData(WitchModAttachments.NARCOLEPSY_DEPTH, depth + (thirdPerson ? 10 : 0));
        markDiscoveredByVictim(target);
    }

    /** roll a sleep depth: mostly normal, sometimes deep, rarely very deep (deep roll must pass first). */
    private int rollDepth(ServerPlayer target) {
        var r = target.getRandom();
        if (r.nextInt(100) < Config.NARCOLEPSY_DEEP_CHANCE_PERCENT.get()) {
            return r.nextInt(100) < Config.NARCOLEPSY_VERY_DEEP_CHANCE_PERCENT.get() ? 2 : 1;
        }
        return 0;
    }

    /** called by the mash payload — you fought your way awake early. */
    public static void wakeEarly(ServerPlayer target) {
        if (isSleeping(target)) {
            new CurseNarcolepsy().wake(target);
        }
    }

    private void wake(ServerPlayer target) {
        target.setData(WitchModAttachments.NARCOLEPSY_SLEEP_END, 0L);
        target.setData(WitchModAttachments.NARCOLEPSY_DEPTH, 0);
        scheduleNext(target);
    }

    private void scheduleNext(ServerPlayer target) {
        int min = Config.NARCOLEPSY_MIN_INTERVAL_TICKS.get();
        int max = Config.NARCOLEPSY_MAX_INTERVAL_TICKS.get();
        // sqrt(random) biases the gap toward the LONGER half (per the spec's "slightly biased toward higher").
        double factor = Math.sqrt(target.getRandom().nextDouble());
        long gap = min + Math.round((max - min) * factor);
        NEXT_SLEEP.put(target.getUUID(), target.level().getGameTime() + gap);
    }

    private static boolean isSleeping(ServerPlayer target) {
        return target.getData(WitchModAttachments.NARCOLEPSY_SLEEP_END) > 0;
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        return java.util.Optional.of(isSleeping(target) ? "asleep" : "sleep on cooldown");
    }

    @Override
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        if (isSleeping(target)) {
            return "already asleep";
        }
        String a = arg == null ? "" : arg.toLowerCase(java.util.Locale.ROOT);
        boolean tp = a.contains("third") || a.equals("tp");
        int depth = (a.contains("verydeep") || a.equals("deep2") || a.equals("2")) ? 2
                : (a.contains("deep") || a.equals("1")) ? 1 : 0;
        startSleep(target, depth, tp);
        return "forced a " + (depth == 2 ? "very deep " : depth == 1 ? "deep " : "") + "narcoleptic sleep"
                + (tp ? " (third-person)" : "");
    }

    @Override
    public java.util.List<String> debugArgs() {
        return java.util.List.of("thirdperson", "deep", "verydeep");
    }
}
