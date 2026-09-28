package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.entities.CloneEntity;

/**
 * reality gets loose: every so often you're swapped in place with a sort-of-nearby player — not right next to
 * you, but within {@code bodySwapRadius} and at least {@code bodySwapMinDistance} away, so it's a genuine
 * "wait, where am I?" relocation. Both parties teleport, with an enderman-style pop at each end. at a lower
 * chance it swaps with a nearby confusion clone instead (fun with the "chaos" jar — swap with your own decoys).
 *
 * <p>Schedule lives in a transient map, re-seeded on tick. Debug: {@code /bewitch debug force
 * witchmod:body_swapping @s} swaps you with the nearest VILLAGER instead (for solo testing).
 */
public final class CurseBodySwapping extends Effect {
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    /** an in-progress FLICKER window: keep swapping with ONE fixed target on a per-second roll. */
    private record Flicker(UUID targetId, long endTick, long nextRollTick) {}
    private static final Map<UUID, Flicker> FLICKER = new HashMap<>();
    /** a scheduled FAKE-OUT follow-up swap (the second swap a short beat after the first). */
    private static final Map<UUID, Long> FAKEOUT_AT = new HashMap<>();

    public CurseBodySwapping() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.CHORUS_FRUIT);
    }

    private static long nextGap(RandomSource rng) {
        int min = Config.BODYSWAP_MIN_GAP_TICKS.get();
        int max = Math.max(min + 1, Config.BODYSWAP_MAX_GAP_TICKS.get());
        return min + rng.nextInt(max - min);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();

        // an active flicker window owns the player until it ends — keep the normal schedule paused meanwhile.
        if (tickFlicker(level, target, now)) {
            return;
        }
        // a pending fake-out follow-up swap.
        Long fake = FAKEOUT_AT.get(target.getUUID());
        if (fake != null && now >= fake) {
            FAKEOUT_AT.remove(target.getUUID());
            Entity partner = choosePartner(level, target);
            if (partner != null) {
                swap(target, partner);
            }
        }

        long next = NEXT.computeIfAbsent(target.getUUID(), k -> now + nextGap(target.getRandom()));
        if (now < next) {
            return;
        }
        NEXT.put(target.getUUID(), now + nextGap(target.getRandom()));

        // a small chance a swap turns into one of the four SPECIAL swaps.
        if (target.getRandom().nextInt(100) < Config.BODYSWAP_SPECIAL_CHANCE_PERCENT.get()) {
            doSpecialSwap(level, target, now);
            return;
        }
        Entity partner = choosePartner(level, target);
        if (partner != null) {
            swap(target, partner);
        }
    }

    /** picks and runs one of the four special swaps. */
    private static void doSpecialSwap(ServerLevel level, ServerPlayer target, long now) {
        switch (target.getRandom().nextInt(4)) {
            case 0 -> fakeOut(level, target, now);      // swap, then swap again 1.5s later
            case 1 -> startFlicker(level, target, now); // repeated swaps with one fixed target over 10s
            case 2 -> longDistance(level, target);      // a partner from well outside the normal range
            default -> frenzy(level, target);           // shuffle everyone nearby between each other's spots
        }
    }

    private static void fakeOut(ServerLevel level, ServerPlayer target, long now) {
        Entity partner = choosePartner(level, target);
        if (partner == null) {
            return;
        }
        swap(target, partner);
        FAKEOUT_AT.put(target.getUUID(), now + Config.BODYSWAP_FAKEOUT_DELAY_TICKS.get());
    }

    private static void startFlicker(ServerLevel level, ServerPlayer target, long now) {
        ServerPlayer partner = findPartner(level, target);
        if (partner == null) {
            return;
        }
        FLICKER.put(target.getUUID(), new Flicker(partner.getUUID(),
                now + Config.BODYSWAP_FLICKER_DURATION_TICKS.get(),
                now + Config.BODYSWAP_FLICKER_INTERVAL_TICKS.get()));
    }

    /** advances a flicker window; @return true while one is active (so onTick pauses the normal schedule). */
    private static boolean tickFlicker(ServerLevel level, ServerPlayer target, long now) {
        Flicker f = FLICKER.get(target.getUUID());
        if (f == null) {
            return false;
        }
        ServerPlayer partner = level.getServer().getPlayerList().getPlayer(f.targetId());
        if (now >= f.endTick() || partner == null || !partner.isAlive() || partner.level() != level) {
            FLICKER.remove(target.getUUID()); // window over, or the fixed target left
            return false;
        }
        if (now >= f.nextRollTick()) {
            if (target.getRandom().nextInt(100) < Config.BODYSWAP_FLICKER_CHANCE_PERCENT.get()) {
                swap(target, partner);
            }
            FLICKER.put(target.getUUID(), new Flicker(f.targetId(), f.endTick(),
                    now + Config.BODYSWAP_FLICKER_INTERVAL_TICKS.get()));
        }
        return true;
    }

    /** long-distance: a partner from BEYOND the normal radius, out to bodySwapLongDistanceMax. */
    private static void longDistance(ServerLevel level, ServerPlayer target) {
        double min = Config.BODYSWAP_RADIUS.get();
        double max = Config.BODYSWAP_LONG_DISTANCE_MAX.get();
        List<ServerPlayer> far = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other == target || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            double d = other.distanceTo(target);
            if (d > min && d <= max) {
                far.add(other);
            }
        }
        if (far.isEmpty()) {
            Entity fallback = choosePartner(level, target); // nobody far — a normal swap instead
            if (fallback != null) {
                swap(target, fallback);
            }
            return;
        }
        swap(target, far.get(target.getRandom().nextInt(far.size())));
    }

    /** frenzy: everyone within the radius (incl. the victim) is rotated one spot along, so they all shuffle. */
    private static void frenzy(ServerLevel level, ServerPlayer target) {
        double radius = Config.BODYSWAP_RADIUS.get();
        List<ServerPlayer> group = new ArrayList<>();
        group.add(target);
        for (ServerPlayer p : level.players()) {
            if (p != target && p.isAlive() && !p.isSpectator() && p.distanceTo(target) <= radius) {
                group.add(p);
            }
        }
        if (group.size() < 2) {
            ServerPlayer partner = findPartner(level, target);
            if (partner != null) {
                swap(target, partner);
            }
            return;
        }
        List<Vec3> positions = new ArrayList<>();
        for (ServerPlayer p : group) {
            positions.add(p.position());
        }
        // rotate by one — a derangement, so nobody ends up where they started.
        for (int i = 0; i < group.size(); i++) {
            ServerPlayer p = group.get(i);
            Vec3 to = positions.get((i + 1) % positions.size());
            puff(level, p.position());
            p.teleportTo(to.x, to.y, to.z);
            pop(level, to);
            puff(level, to);
        }
    }

    /**
     * a player partner most of the time; but while confusion is ALSO active (synergy), a nearby clone at the
     * lower {@code bodySwapCloneChancePercent} — so you can swap with your own decoys.
     */
    @Nullable
    private static Entity choosePartner(ServerLevel level, ServerPlayer self) {
        if (com.oliver.witchmod.synergy.Synergies.SWAP_DECOYS.activeFor(self)
                && self.getRandom().nextInt(100) < Config.BODYSWAP_CLONE_CHANCE_PERCENT.get()) {
            CloneEntity clone = findClone(level, self);
            if (clone != null) {
                return clone;
            }
        }
        return findPartner(level, self);
    }

    /** the nearest confusion clone within the swap band, or null. */
    @Nullable
    private static CloneEntity findClone(ServerLevel level, ServerPlayer self) {
        double radius = Config.BODYSWAP_RADIUS.get();
        double min = Config.BODYSWAP_MIN_DISTANCE.get();
        CloneEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (CloneEntity clone : level.getEntitiesOfClass(CloneEntity.class,
                new AABB(self.blockPosition()).inflate(radius))) {
            double d = clone.distanceTo(self);
            if (d >= min && d <= radius && d < best) {
                best = d;
                nearest = clone;
            }
        }
        return nearest;
    }

    /** A random online player in the same level within the swap band [minDistance, radius]. */
    @Nullable
    private static ServerPlayer findPartner(ServerLevel level, ServerPlayer self) {
        double radius = Config.BODYSWAP_RADIUS.get();
        double min = Config.BODYSWAP_MIN_DISTANCE.get();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other == self || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            double d = other.distanceTo(self);
            if (d >= min && d <= radius) {
                candidates.add(other);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(self.getRandom().nextInt(candidates.size()));
    }

    /** swap the positions of {@code a} (always a player) and {@code b} (a player or, for debug, any entity). */
    private static void swap(ServerPlayer a, Entity b) {
        ServerLevel level = a.serverLevel();
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        // departure puffs at the old spots.
        puff(level, pa);
        puff(level, pb);
        a.teleportTo(pb.x, pb.y, pb.z);
        if (b instanceof ServerPlayer sp) {
            sp.teleportTo(pa.x, pa.y, pa.z);
        } else {
            b.teleportTo(pa.x, pa.y, pa.z);
        }
        // play the pop AFTER the teleport, at each participant's NEW position — so both recipients actually
        // hear it arrive on them (playing at the old spots meant a recipient teleported away from their sound).
        pop(level, a.position());
        pop(level, b.position());
        puff(level, a.position());
        puff(level, b.position());
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 30, 0.4, 0.6, 0.4, 0.4);
    }

    private static void pop(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT.remove(target.getUUID());
        FLICKER.remove(target.getUUID());
        FAKEOUT_AT.remove(target.getUUID());
    }

    @Override
    public @Nullable String debugForce(ServerPlayer target, @Nullable String arg) {
        if (!(target.level() instanceof ServerLevel level)) {
            return null;
        }
        Villager nearest = null;
        double best = Double.MAX_VALUE;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(target.blockPosition()).inflate(24.0))) {
            double d = v.distanceToSqr(target);
            if (d < best) {
                best = d;
                nearest = v;
            }
        }
        if (nearest == null) {
            return "No villager within 24 blocks to swap with.";
        }
        swap(target, nearest);
        return "Swapped places with a villager.";
    }
}
