package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/**
 * pacing — a one piece-style "dramatic moment" time-stop. trigger chance ramps the longer it's gone; the
 * moment freezes + pins everything in the radius (re-swept so newcomers freeze too) and hijacks every caught
 * player's camera into the cinematic (cuts run client-side). capped for server safety.
 */
public final class PacingManager {
    private static final ResourceLocation PACING_ID = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "pacing");
    private static final int RAMP_TICKS = 2400;             // 0→full charge over ~2 min
    private static final double INITIAL_CHARGE = 0.85;      // "chance starts high" on curse apply
    private static final float MAX_HIT_CHANCE = 0.6F;       // per-hit chance at full charge

    /** {@code anchor} is where the entity stood when it froze — it is pinned back there every tick. */
    private record Frozen(Entity entity, boolean wasInvulnerable, boolean wasNoAi, Vec3 anchor, long restoreTick) {}

    /** an in-progress moment, kept so newcomers wandering into the radius get frozen too. */
    private record Moment(ServerPlayer victim, long end) {}

    private static final List<Frozen> FROZEN = new ArrayList<>();
    private static final List<Moment> MOMENTS = new ArrayList<>();
    private static final Set<Integer> FROZEN_IDS = new HashSet<>();
    /** how often the radius is re-swept for entities that have wandered in mid-moment. */
    private static final int RESWEEP_INTERVAL = 5;

    private PacingManager() {}

    /** called from the curse's onApply — pre-charges the ramp so the first moment comes quickly. */
    public static void onApply(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        player.setData(WitchModAttachments.PACING_CHARGE_START, now - (long) (RAMP_TICKS * INITIAL_CHARGE));
    }

    /** clears any lingering camera-focus flag when the curse ends. */
    public static void onRemove(ServerPlayer player) {
        player.setData(WitchModAttachments.PACING_FOCUS_ID, -1);
        player.setData(WitchModAttachments.PACING_END_TICK, 0L);
    }

    private static float charge(ServerPlayer player, long now) {
        long start = player.getData(WitchModAttachments.PACING_CHARGE_START);
        return (float) Mth.clamp((double) (now - start) / RAMP_TICKS, 0.0, 1.0);
    }

    private static boolean inMoment(ServerPlayer player, long now) {
        return now < player.getData(WitchModAttachments.PACING_END_TICK);
    }

    /** a combat hit by the cursed player: rolls the ramped per-hit chance. */
    public static void onHit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (inMoment(player, now)) {
            return;
        }
        if (player.getRandom().nextFloat() < charge(player, now) * MAX_HIT_CHANCE) {
            trigger(player);
        }
    }

    /** per-tick check for the cursed player: if the ramp has maxed out without a combat trigger, fire anyway. */
    public static void tickCharge(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (!inMoment(player, now) && charge(player, now) >= 1.0F) {
            trigger(player);
        }
    }

    /** rolls the dramatic-moment length: min..max seconds, biased toward the short end. */
    private static int rollDurationTicks(ServerPlayer victim) {
        int min = Config.PACING_MIN_SECONDS.get() * 20;
        int max = Math.max(min, Config.PACING_MAX_SECONDS.get() * 20);
        double r = victim.getRandom().nextDouble();
        double biased = Math.pow(r, Config.PACING_LOW_BIAS_EXPONENT.get()); // r^exp skews toward 0
        return min + (int) Math.round((max - min) * biased);
    }

    /** debug: force a dramatic time-stop moment now (used by {@code /bewitch debug force witchmod:pacing}). */
    public static void debugTrigger(ServerPlayer victim) {
        trigger(victim);
    }

    private static void trigger(ServerPlayer victim) {
        ServerLevel level = victim.serverLevel();
        long now = level.getGameTime();
        int duration = rollDurationTicks(victim);
        long end = now + duration;

        victim.setData(WitchModAttachments.PACING_END_TICK, end);
        victim.setData(WitchModAttachments.PACING_CHARGE_START, now); // reset ramp
        victim.setData(WitchModAttachments.PACING_FOCUS_ID, victim.getId()); // victim orbits itself
        DiscoveryManager.markEffectDiscovered(victim, PACING_ID); // discovered on the first moment (Rule 2)

        MOMENTS.add(new Moment(victim, end));
        sweep(victim, end);
    }

    /**
     * Freezes the victim and everything currently in the radius. Re-run periodically for the whole moment,
     * because a one-shot sweep silently misses anything that wanders in afterwards — over a moment lasting
     * up to 30 seconds that is most of what you end up looking at, and it read in play as "the AI pause is
     * unreliable and often just does not pause involved AI".
     *
     * <p><b>Capped at {@code PACING_MAX_INVOLVED}</b> (Oliver's call). The cap is a SAFETY limit rather than a
     * presentation one: everything frozen is also made invulnerable and pinned every tick, so an uncapped
     * sweep set off inside a mob farm could hurt a server badly. The count is of everything currently held,
     * so the re-sweep tops the set up as earlier members die or the moment moves on, rather than being able
     * to exceed it.
     */
    private static void sweep(ServerPlayer victim, long end) {
        ServerLevel level = victim.serverLevel();
        freeze(victim, end);
        int cap = Config.PACING_MAX_INVOLVED.get();
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(Config.PACING_FREEZE_RADIUS.get()), e -> e != victim)) {
            if (FROZEN.size() >= cap) {
                break;
            }
            boolean fresh = freeze(nearby, end);
            // any OTHER player caught in it experiences the cinematic too, focused on the victim.
            if (fresh && nearby instanceof ServerPlayer other) {
                other.setData(WitchModAttachments.PACING_END_TICK, end);
                other.setData(WitchModAttachments.PACING_FOCUS_ID, victim.getId());
            }
        }
    }

    /** @return true if this entity wasn't already frozen (so callers don't re-run one-time side effects) */
    private static boolean freeze(LivingEntity entity, long end) {
        if (!FROZEN_IDS.add(entity.getId())) {
            return false;
        }
        boolean wasNoAi = entity instanceof Mob mob && mob.isNoAi();
        FROZEN.add(new Frozen(entity, entity.isInvulnerable(), wasNoAi, entity.position(), end));
        entity.setInvulnerable(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true; // push the zeroed velocity to the client so it visibly stops dead
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        // a player moves client-side, so the server can't truly pin them; heavy slowness plus the
        // client-side input lock (ClientCurseHandler) is what actually holds them still.
        //
        // ⚠ NO Jump Boost here. It used to apply amplifier 128 with a comment claiming "level 129 = no jump",
        // but getJumpBoostPower() is 0.1 * (amplifier + 1) — so that was +12.9 jump power, i.e. a launch pad,
        // not a jump blocker. It fired whenever the freeze caught someone mid-jump. The client input lock
        // already stops jumping properly, so the effect was both redundant and the cause of the bug.
        if (entity instanceof ServerPlayer player) {
            int ticks = (int) Math.max(1, end - player.serverLevel().getGameTime());
            // amplifier 10 already clamps movement speed to zero, and stays well clear of the byte
            // boundaries that make extreme amplifiers behave unpredictably.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false));
        }
        return true;
    }

    /** called every server tick: keeps the freeze current, holds everything still, and restores on expiry. */
    public static void tickFreeze(MinecraftServer server) {
        long now = server.overworld().getGameTime();

        // pick up anything that has wandered into an in-progress moment.
        Iterator<Moment> moments = MOMENTS.iterator();
        while (moments.hasNext()) {
            Moment moment = moments.next();
            if (now >= moment.end() || !moment.victim().isAlive()) {
                moments.remove();
            } else if (now % RESWEEP_INTERVAL == 0) {
                sweep(moment.victim(), moment.end());
            }
        }

        if (FROZEN.isEmpty()) {
            return;
        }
        Iterator<Frozen> it = FROZEN.iterator();
        while (it.hasNext()) {
            Frozen f = it.next();
            if (now >= f.restoreTick() || !f.entity().isAlive()) {
                restore(f);
                it.remove();
                continue;
            }
            // hold it. Re-asserting every tick is what makes the pause actually reliable — noAi stops the
            // aI but a single zeroing at the start doesn't survive knockback, gravity or anything else that
            // nudges the entity over a 30-second stop. Players are left alone: their movement is
            // client-authoritative, so pinning them server-side would only fight the client and rubber-band.
            if (!(f.entity() instanceof ServerPlayer)) {
                f.entity().setDeltaMovement(Vec3.ZERO);
                f.entity().setPos(f.anchor().x, f.anchor().y, f.anchor().z);
                if (f.entity() instanceof Mob mob) {
                    mob.setNoAi(true);
                }
            }
        }
    }

    private static void restore(Frozen f) {
        FROZEN_IDS.remove(f.entity().getId());
        f.entity().setInvulnerable(f.wasInvulnerable());
        if (f.entity() instanceof Mob mob) {
            mob.setNoAi(f.wasNoAi());
        }
        if (f.entity() instanceof ServerPlayer player) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            // clear the old Jump Boost too, so anyone still carrying one from a previous build isn't left
            // with a launch pad attached to them.
            player.removeEffect(MobEffects.JUMP);
            player.setData(WitchModAttachments.PACING_FOCUS_ID, -1);
        }
    }
}
