package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.goals.UnhygienicFleeGoal;

/**
 * You reek (master-spec, formerly "Green Aura" — renamed to Unhygienic on Oliver's call). The victim leaks a
 * green stink cloud with a few flies orbiting them, and:
 * <ul>
 *   <li><b>Non-undead mobs flee.</b> Anything inside the stink radius gets an {@link UnhygienicFleeGoal} and
 *       runs for it. Undead are pointedly unbothered — they smell worse.</li>
 *   <li><b>Other players are nudged away.</b> Anyone who gets close is pushed back with a small, repeated
 *       shove — subtle enough to walk against, obvious enough to be insulting.</li>
 *   <li><b>Flies buzz</b> every so often, one of three variants, so it sounds as bad as it looks.</li>
 * </ul>
 */
public final class CurseUnhygienic extends Effect {
    /** A sickly green haze. DUST hangs in the air; the potion-bubble particle read as brewing, not stink. */
    private static final DustParticleOptions GAS =
            new DustParticleOptions(new Vector3f(0.35F, 0.75F, 0.15F), 1.6F);
    /** Tiny dark specks for the flies — small enough to read as insects rather than motes of dust. */
    private static final SimpleParticleType FLY = ParticleTypes.MYCELIUM;
    private static final int FLY_COUNT = 4;
    private static final int PLAYER_PUSH_INTERVAL = 5;

    /** When the next ambient buzz is due, per player. Transient — re-rolled by the self-heal below. */
    private static final Map<UUID, Long> NEXT_BUZZ = new HashMap<>();

    public CurseUnhygienic() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.ROTTEN_FLESH);
    }

    /** You work it out when things start backing away from you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_BUZZ.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();

        emitParticles(target, level, now);
        buzz(target, level, now);
        repelMobs(target, level);
        if (now % PLAYER_PUSH_INTERVAL == 0) {
            repelPlayers(target, level);
        }
    }

    /** The green haze, plus a few flies buzzing erratically around the victim's head. */
    private static void emitParticles(ServerPlayer target, ServerLevel level, long now) {
        if (now % Config.UNHYGIENIC_PARTICLE_INTERVAL.get() == 0) {
            // A soft green DUST haze. ENTITY_EFFECT was the obvious pick but renders as potion bubbles,
            // which read as "brewing", not "stink" — coloured dust hangs in the air far better.
            level.sendParticles(GAS, target.getX(), target.getY() + 1.0, target.getZ(),
                    5, 0.35, 0.55, 0.35, 0.0);
        }

        // Flies. Each one follows a smooth-but-uneven path — wobbling radius, uneven angular speed and its
        // own bobbing height, from sines at differing frequencies — so it never reads as a clean orbit.
        //
        // Crucially the specks are spawned STATIONARY and only every few ticks. Giving them a darting
        // velocity and spawning every tick meant ~80 moving particles a second, which looked like flung dust
        // rather than insects (and was pure waste). Dropped along a continuous path instead, consecutive
        // specks land close together and the eye interpolates them into one moving fly.
        if (now % Config.UNHYGIENIC_FLY_INTERVAL.get() != 0) {
            return;
        }
        for (int i = 0; i < FLY_COUNT; i++) {
            double t = now * 0.15 + i * 2.1;
            double radius = 0.55 + Math.sin(t * 1.7 + i) * 0.35;
            double angle = t + Math.sin(t * 0.6 + i * 1.3) * 0.9;
            double height = 1.15 + Math.sin(t * 1.3 + i * 1.9) * 0.45;
            level.sendParticles(FLY,
                    target.getX() + Math.cos(angle) * radius,
                    target.getY() + height,
                    target.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0); // one speck, no spread, no velocity
        }
    }

    /** An occasional buzz at the victim, on a randomised gap. */
    private static void buzz(ServerPlayer target, ServerLevel level, long now) {
        UUID id = target.getUUID();
        Long due = NEXT_BUZZ.get(id);
        if (due == null) {
            scheduleBuzz(target, now); // self-heal: transient map, so a persisted curse re-schedules here
            return;
        }
        if (now < due) {
            return;
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                WitchModSounds.UNHYGIENIC_FLIES.get(), SoundSource.PLAYERS,
                (float) (double) Config.UNHYGIENIC_FLY_VOLUME.get(),
                0.9F + target.getRandom().nextFloat() * 0.2F);
        scheduleBuzz(target, now);
    }

    private static void scheduleBuzz(ServerPlayer target, long now) {
        int min = Config.UNHYGIENIC_FLY_SOUND_MIN_TICKS.get();
        int max = Math.max(min, Config.UNHYGIENIC_FLY_SOUND_MAX_TICKS.get());
        NEXT_BUZZ.put(target.getUUID(), now + min + target.getRandom().nextInt(max - min + 1));
    }

    /** Every non-undead mob in range gets the flee goal (added once, bound to this player). */
    private void repelMobs(ServerPlayer target, ServerLevel level) {
        double radius = Config.UNHYGIENIC_MOB_FLEE_RADIUS.get();
        for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class,
                target.getBoundingBox().inflate(radius),
                m -> m.isAlive() && !m.getType().is(EntityTypeTags.UNDEAD))) {
            UnhygienicFleeGoal existing = findFleeGoal(mob);
            if (existing != null && existing.isTargeting(target)) {
                continue;
            }
            if (existing != null) {
                mob.goalSelector.removeGoal(existing); // stale player reference (respawn/relog) — replace it
            }
            // Above wandering but below panic/float, so being hurt still takes priority.
            mob.goalSelector.addGoal(2, new UnhygienicFleeGoal(mob, target));
            markDiscoveredByVictim(target);
        }
    }

    /** Nearby players slide gently away — a shove they can fight, not a knockback. */
    private void repelPlayers(ServerPlayer target, ServerLevel level) {
        double radius = Config.UNHYGIENIC_PLAYER_DRIFT_RADIUS.get();
        double force = Config.UNHYGIENIC_PLAYER_DRIFT_FORCE.get();
        for (ServerPlayer other : level.getPlayers(p -> p != target && p.isAlive()
                && p.distanceToSqr(target) <= radius * radius)) {
            double dx = other.getX() - target.getX();
            double dz = other.getZ() - target.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4) {
                continue;
            }
            other.push(dx / len * force, 0.0, dz / len * force);
            other.hurtMarked = true; // players need the velocity explicitly synced
            markDiscoveredByVictim(target);
        }
    }

    private static UnhygienicFleeGoal findFleeGoal(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream()
                .map(w -> w.getGoal())
                .filter(UnhygienicFleeGoal.class::isInstance)
                .map(UnhygienicFleeGoal.class::cast)
                .findFirst()
                .orElse(null);
    }
}
