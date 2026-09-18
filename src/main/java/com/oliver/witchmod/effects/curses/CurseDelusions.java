package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * there's someone out there. There isn't. Fake players wearing the skins and
 * nametags of real people on the server wander around at the edge of your vision, going about ordinary
 * player business — until one notices you looking.
 *
 * <p>Uses VANILLA sounds only (Oliver's call). That isn't a compromise here: every noise a delusion makes
 * should be one the victim has heard a thousand times from real players, so a bespoke sting would be the
 * one thing marking them out as fake.
 *
 * <p><b>The server owns nothing but the schedule.</b> It bumps the auto-synced
 * {@link WitchModAttachments#DELUSIONS_SIGNAL} to a fresh random value whenever another delusion should
 * appear; everything else — the entity, its skin, where it stands, which of the fourteen behaviours it
 * runs, when it realises it's being watched — lives in {@code client/DelusionManager} and
 * {@code client/DelusionPlayer}.
 *
 * <p>That split isn't stylistic. A delusion is a {@code RemotePlayer} inserted straight into ONE player's
 * {@code ClientLevel}, so it cannot exist server-side at all: nobody else's client is told about it, the
 * server has no entity to tick, and there is therefore nothing another player could walk over to and
 * confirm. Keeping the schedule on the server is what keeps the curse's lifetime and its discovery
 * authoritative.
 */
public final class CurseDelusions extends Effect {
    /** next game tick a delusion should be signalled for, per victim. Transient — see the self-heal below. */
    private static final Map<UUID, Long> NEXT_SPAWN = new HashMap<>();

    public CurseDelusions() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.ENDER_PEARL);
    }

    /** you find out the first time one of them shows up. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        target.setData(WitchModAttachments.DELUSIONS_SIGNAL, freshSignal(target));
        return "signalled a delusion (fake player) to appear";
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // A non-zero signal switches the client side on; the first delusion follows on the usual interval
        // rather than immediately, so the curse doesn't announce itself the second it lands.
        target.setData(WitchModAttachments.DELUSIONS_SIGNAL, freshSignal(target));
        schedule(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_SPAWN.remove(target.getUUID());
        // 0 is the client's cue to clear away every fake player it's currently showing.
        target.setData(WitchModAttachments.DELUSIONS_SIGNAL, 0L);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        long now = target.level().getGameTime();
        Long due = NEXT_SPAWN.get(target.getUUID());
        if (due == null) {
            // self-heal: the schedule is transient, so a curse that PERSISTED across a relog or world reload
            // (onApply never runs again) would otherwise go quiet forever. Same trap Yap and Gluttony hit.
            schedule(target);
            return;
        }
        if (now < due) {
            return;
        }
        schedule(target);
        target.setData(WitchModAttachments.DELUSIONS_SIGNAL, freshSignal(target));
        markDiscoveredByVictim(target);
    }

    private static void schedule(ServerPlayer target) {
        int min = Config.DELUSIONS_SPAWN_INTERVAL_MIN.get();
        int max = Math.max(min, Config.DELUSIONS_SPAWN_INTERVAL_MAX.get());
        long delay = min + target.getRandom().nextInt(max - min + 1);
        NEXT_SPAWN.put(target.getUUID(), target.level().getGameTime() + delay);
    }

    /** any non-zero value; the client watches for the CHANGE, and seeds that delusion's randomness from it. */
    private static long freshSignal(ServerPlayer target) {
        long value = target.getRandom().nextLong();
        return value == 0L ? 1L : value;
    }
}
