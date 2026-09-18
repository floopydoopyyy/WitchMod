package com.oliver.witchmod.effects.blessings;

import java.util.HashSet;
import java.util.Set;
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
 * blessing of Flight (ELYTRA): creative-style flight tied to a resource. Hold JUMP to rise, draining a small
 * yellow energy bar above your XP; let go and it refills. Taking a hit spends 20% of the bar and knocks you
 * out of flight for 0.6s. The rising itself is client-authoritative (see {@code client/FlightClient}); the
 * SERVER owns the energy + the hit-lockout and cancels fall damage while the blessing is active.
 */
public final class BlessingFlight extends Effect {
    /** player -> rise mode reported by the client (0 none / 1 push-up / 2 sprint-glide). */
    private static final java.util.Map<UUID, Integer> MODE = new java.util.HashMap<>();
    /** players who've bottomed out the bar — locked out of flight until it refills to the threshold. */
    private static final Set<UUID> DEPLETED = new HashSet<>();
    /** player -> game tick they last rose, so the bar waits a beat before refilling. */
    private static final java.util.Map<UUID, Long> LAST_RISE_TICK = new java.util.HashMap<>();
    /** player -> consecutive ticks off the ground, so a brief natural jump doesn't cost the airborne drain. */
    private static final java.util.Map<UUID, Integer> AIRBORNE_TICKS = new java.util.HashMap<>();
    private static final float REFILL_THRESHOLD = 0.2F; // 20% back before you can fly again

    public BlessingFlight() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 50, () -> Items.GHAST_TEAR);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.FLIGHT_ACTIVE, 1);
        target.setData(WitchModAttachments.FLIGHT_ENERGY, 1.0F);
        target.setData(WitchModAttachments.FLIGHT_LOCKOUT_END, 0L);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.FLIGHT_ACTIVE, -1);
        MODE.remove(target.getUUID());
        DEPLETED.remove(target.getUUID());
        LAST_RISE_TICK.remove(target.getUUID());
        AIRBORNE_TICKS.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        long now = target.level().getGameTime();
        boolean lockedOut = now < target.getData(WitchModAttachments.FLIGHT_LOCKOUT_END);
        boolean depleted = DEPLETED.contains(id);
        float energy = target.getData(WitchModAttachments.FLIGHT_ENERGY);
        int mode = MODE.getOrDefault(id, 0);
        boolean rising = mode >= 1 && !lockedOut && !depleted && energy > 0.0F;
        boolean airborne = !target.onGround();
        int airTicks = airborne ? AIRBORNE_TICKS.merge(id, 1, Integer::sum) : 0;
        if (!airborne) {
            AIRBORNE_TICKS.put(id, 0);
        }

        if (rising) {
            // glide (mode 2) drains more than a plain push-up (mode 1); flying an elytra burns faster still.
            double drain = Config.FLIGHT_DRAIN_PER_TICK.get()
                    * (mode == 2 ? Config.FLIGHT_GLIDE_DRAIN_MULT.get() : 1.0);
            if (target.isFallFlying()) {
                drain *= Config.FLIGHT_ELYTRA_DRAIN_MULT.get();
            }
            energy -= (float) drain;
            markDiscoveredByVictim(target);
            LAST_RISE_TICK.put(id, now);
            // A subtle, magical shimmer around your upper body while you rise.
            net.minecraft.server.level.ServerLevel level = target.serverLevel();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    target.getX(), target.getY() + 1.95, target.getZ(), 2, 0.16, 0.25, 0.16, 0.006);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    target.getX(), target.getY() + 2.5, target.getZ(), 3, 0.28, 0.35, 0.28, 0.0);
        } else if (target.isFallFlying()) {
            // elytra glide with NO active push-up: the blessing REFUELS while you soar — its niche as an
            // elytra enhancer. (While actively rising above, it still drains.)
            energy += (float) (double) Config.FLIGHT_GLIDE_REGEN_PER_TICK.get();
        } else if (airborne && airTicks > Config.FLIGHT_AIRBORNE_GRACE_TICKS.get()) {
            // just being aloft (past a short grace, so a natural jump is free) costs a trickle — refill by landing.
            energy -= (float) (double) Config.FLIGHT_AIRBORNE_DRAIN.get();
        } else if (!airborne) {
            // on the ground: refill, after a short beat once flight ends.
            long lastRise = LAST_RISE_TICK.getOrDefault(id, Long.MIN_VALUE / 2);
            if (now - lastRise >= Config.FLIGHT_REGEN_DELAY_TICKS.get()) {
                energy += (float) (double) Config.FLIGHT_REGEN_PER_TICK.get();
            }
        }
        energy = Math.max(0.0F, Math.min(1.0F, energy));
        target.setData(WitchModAttachments.FLIGHT_ENERGY, energy);

        // depletion lockout: hit 0 and you're grounded until the bar refills to the threshold.
        if (energy <= 0.0F) {
            depleted = true;
        } else if (energy >= REFILL_THRESHOLD) {
            depleted = false;
        }
        if (depleted) {
            DEPLETED.add(id);
        } else {
            DEPLETED.remove(id);
        }
        // 1 = ready, 2 = depleted-lockout (client greys the bar + won't rise). Set every tick (sync-only attachment).
        target.setData(WitchModAttachments.FLIGHT_ACTIVE, depleted ? 2 : 1);
    }

    /** set from the {@code FlightRisePayload} (0 none / 1 push-up / 2 sprint-glide). */
    public static void setMode(ServerPlayer player, int mode) {
        if (mode <= 0) {
            MODE.remove(player.getUUID());
        } else {
            MODE.put(player.getUUID(), mode);
        }
    }

    /** called from {@code BlessingEventHandler} when a Flight-blessed player takes damage: spend 20% + lock out. */
    public static void onHurt(ServerPlayer player) {
        float energy = player.getData(WitchModAttachments.FLIGHT_ENERGY);
        energy = Math.max(0.0F, energy - (float) (double) Config.FLIGHT_DAMAGE_COST.get());
        player.setData(WitchModAttachments.FLIGHT_ENERGY, energy);
        player.setData(WitchModAttachments.FLIGHT_LOCKOUT_END,
                player.level().getGameTime() + Config.FLIGHT_LOCKOUT_TICKS.get());
        MODE.remove(player.getUUID());
    }
}
