package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;

/**
 * A joke about controllers (master-spec Stick Drift). When the curse lands it rolls, ONCE, a mode
 * (camera or movement) and a fixed direction to drift toward — a stick doesn't develop a new fault
 * mid-session, so both stay constant for the whole duration.
 *
 * <p>After that it drifts in <b>episodes</b>: random intensities for random lengths, with the twist that
 * <b>intensity × duration is held roughly constant</b> — a hard drift is short, a gentle one drags on — which
 * is exactly how a worn stick behaves. The direction is always the one rolled at the start.
 *
 * <p>The server owns only the schedule (mode, angle, and the current episode's intensity/end, all synced);
 * the drift itself is applied client-side in {@code ClientCurseHandler}, because both camera and movement are
 * client-authoritative and a server nudge would just be corrected away.
 */
public final class CurseStickDrift extends Effect {
    /** victim -> game tick the next episode may start (server-only). */
    private static final Map<UUID, Long> NEXT_EPISODE = new HashMap<>();

    public CurseStickDrift() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.FISHING_ROD);
    }

    /** You notice the moment your aim or your feet start sliding on their own (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        RandomSource rng = target.getRandom();
        int mode = rng.nextInt(100) < Config.STICKDRIFT_CAMERA_CHANCE.get() ? 0 : 1;
        target.setData(WitchModAttachments.STICK_DRIFT_MODE, mode);
        target.setData(WitchModAttachments.STICK_DRIFT_ANGLE, rng.nextFloat() * Mth.TWO_PI);
        target.setData(WitchModAttachments.STICK_DRIFT_INTENSITY, 0.0F);
        target.setData(WitchModAttachments.STICK_DRIFT_END, 0L);
        NEXT_EPISODE.put(target.getUUID(), target.level().getGameTime() + gap(rng));
        // NOT discovered here — you notice the first time it actually drifts, not the instant it lands.
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.STICK_DRIFT_MODE, -1);
        target.setData(WitchModAttachments.STICK_DRIFT_INTENSITY, 0.0F);
        target.setData(WitchModAttachments.STICK_DRIFT_END, 0L);
        NEXT_EPISODE.remove(target.getUUID());
    }

    /** The Scrying Mirror names the fault: which stick, and roughly which way it pulls. */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        int mode = target.getData(WitchModAttachments.STICK_DRIFT_MODE);
        if (mode < 0) {
            return java.util.Optional.empty();
        }
        float angle = target.getData(WitchModAttachments.STICK_DRIFT_ANGLE);
        String[] dirs = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int oct = Math.floorMod(Math.round(angle / (Mth.TWO_PI / 8)), 8);
        return java.util.Optional.of((mode == 0 ? "camera" : "movement") + " drift, pulling " + dirs[oct]);
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        startEpisode(target, target.serverLevel().getGameTime());
        return "stick-drift episode started";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // Self-heal the fixed roll after a relog (the schedule map is transient, the synced roll may be lost).
        if (target.getData(WitchModAttachments.STICK_DRIFT_MODE) < 0) {
            onApply(target, null, ticksRemaining);
            return;
        }
        long now = target.level().getGameTime();
        if (now < target.getData(WitchModAttachments.STICK_DRIFT_END)) {
            return; // an episode is running
        }

        Long next = NEXT_EPISODE.get(target.getUUID());
        if (next == null) {
            NEXT_EPISODE.put(target.getUUID(), now + gap(target.getRandom()));
            target.setData(WitchModAttachments.STICK_DRIFT_INTENSITY, 0.0F);
            return;
        }
        if (now < next) {
            if (target.getData(WitchModAttachments.STICK_DRIFT_INTENSITY) != 0.0F) {
                target.setData(WitchModAttachments.STICK_DRIFT_INTENSITY, 0.0F); // episode just ended — go calm
            }
            return;
        }
        startEpisode(target, now);
    }

    /** Rolls one episode: an intensity, then the duration that keeps intensity×duration ~ constant. */
    private static void startEpisode(ServerPlayer target, long now) {
        RandomSource rng = target.getRandom();
        double min = Config.STICKDRIFT_INTENSITY_MIN.get();
        double max = Math.max(min, Config.STICKDRIFT_INTENSITY_MAX.get());
        double intensity = min + rng.nextDouble() * (max - min);

        // The whole joke: stronger drift, shorter time; gentle drift, drags on.
        int duration = Mth.clamp((int) Math.round(Config.STICKDRIFT_DURATION_PRODUCT.get() / intensity),
                Config.STICKDRIFT_DURATION_MIN.get(), Config.STICKDRIFT_DURATION_MAX.get());

        target.setData(WitchModAttachments.STICK_DRIFT_INTENSITY, (float) intensity);
        target.setData(WitchModAttachments.STICK_DRIFT_END, now + duration);
        NEXT_EPISODE.put(target.getUUID(), now + duration + gap(rng));
        Curses.STICK_DRIFT.value().markDiscoveredByVictim(target); // first actual drift is the tell
    }

    private static long gap(RandomSource rng) {
        int min = Config.STICKDRIFT_GAP_MIN.get();
        int max = Math.max(min, Config.STICKDRIFT_GAP_MAX.get());
        return min + rng.nextInt(max - min + 1);
    }
}
