package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.effects.goals.BrokenBondsFleeGoal;

/**
 * your animals are quietly deciding they've had enough of you.
 *
 * <p>Every owned tamed mob nearby carries a hidden <b>hate meter</b>. It builds while the animal is close —
 * faster the closer it is — and faster still while it's actively trailing you around or, worst of all, being
 * ridden. There's a dash of randomness on every change, and when nothing is feeding it the meter cools back
 * down at a similar rate, so leaving a pet be genuinely calms it.
 *
 * <p>When the meter maxes out the animal <b>snaps</b>: it untames on the spot, throws you off if you were
 * riding it, and has its legs hijacked ({@link BrokenBondsFleeGoal}) to storm a good distance away. Its
 * other NBT — name tag, collar dye, everything that isn't the ownership itself — is left untouched, so what
 * walks off is recognisably the pet you lost.
 */
public final class CurseBrokenBonds extends Effect {
    /** owner -> (pet id -> current hate). Transient; rebuilt as pets are seen. */
    private static final Map<UUID, Map<UUID, Float>> HATE = new HashMap<>();

    public CurseBrokenBonds() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.LEAD);
    }

    /** you find out the first time one of your own animals turns its back on you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        HATE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.BROKEN_BONDS_CHECK_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        RandomSource rng = target.getRandom();
        Map<UUID, Float> meters = HATE.computeIfAbsent(target.getUUID(), k -> new HashMap<>());

        double radius = Config.BROKEN_BONDS_RADIUS.get();
        double limit = Config.BROKEN_BONDS_LIMIT.get();

        // grow the meter for every owned pet in range. Scanned as Animal + OwnableEntity, so it catches
        // BOTH taming systems — TamableAnimal (wolves, cats, parrots) AND AbstractHorse (horses, donkeys,
        // mules, llamas), which are owned through an entirely separate mechanism and were missed before.
        List<Animal> nearby = level.getEntitiesOfClass(Animal.class,
                target.getBoundingBox().inflate(radius),
                pet -> pet instanceof OwnableEntity owned && pet.isAlive()
                        && target.getUUID().equals(owned.getOwnerUUID()));
        for (Animal pet : nearby) {
            float updated = meters.getOrDefault(pet.getUUID(), 0.0F) + gainFor(target, pet, rng);
            if (updated >= limit) {
                meters.remove(pet.getUUID());
                snap(target, level, pet);
            } else {
                meters.put(pet.getUUID(), updated);
                sulk(level, pet, updated / (float) limit);
            }
        }

        // everything we're tracking that ISN'T in range this sweep cools off, and is forgotten at zero.
        decayAbsent(meters, nearby, rng);
    }

    /** how much resentment this pet earns this check: proximity, plus following, plus being ridden. */
    private static float gainFor(ServerPlayer owner, Animal pet, RandomSource rng) {
        double radius = Config.BROKEN_BONDS_RADIUS.get();
        double distance = Math.sqrt(pet.distanceToSqr(owner));
        double closeness = Math.max(0.0, 1.0 - distance / radius);   // 1 right next to you, 0 at the edge

        double gain = Config.BROKEN_BONDS_PROXIMITY_GAIN.get() * closeness;
        // A tamed animal that's standing (not parked in a sit) is trailing you around — that wears on it.
        // horses don't sit or follow, so they don't earn this; their gain comes from proximity and riding.
        if (pet instanceof TamableAnimal tamable && !tamable.isInSittingPose()) {
            gain += Config.BROKEN_BONDS_FOLLOW_GAIN.get();
        }
        // being sat on is the fast track to a broken bond.
        if (owner.getVehicle() == pet) {
            gain += Config.BROKEN_BONDS_RIDE_GAIN.get();
        }
        return jitter(gain, rng);
    }

    private static void decayAbsent(Map<UUID, Float> meters, List<Animal> present, RandomSource rng) {
        var presentIds = present.stream().map(Animal::getUUID).toList();
        Iterator<Map.Entry<UUID, Float>> it = meters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Float> entry = it.next();
            if (presentIds.contains(entry.getKey())) {
                continue;
            }
            float cooled = entry.getValue() - jitter(Config.BROKEN_BONDS_DECAY.get(), rng);
            if (cooled <= 0.0F) {
                it.remove();
            } else {
                entry.setValue(cooled);
            }
        }
    }

    private static float jitter(double base, RandomSource rng) {
        double spread = Config.BROKEN_BONDS_RANDOMNESS.get();
        return (float) (base * (1.0 + (rng.nextDouble() * 2.0 - 1.0) * spread));
    }

    /** the betrayal itself: untame, dismount, and drive it off — keeping everything else about it intact. */
    private void snap(ServerPlayer owner, ServerLevel level, Animal pet) {
        Vec3 from = owner.position();
        if (owner.getVehicle() == pet) {
            owner.stopRiding(); // thrown off
        }
        pet.ejectPassengers();

        // untame, clearing ONLY the ownership — name tag, collar dye, saddle, chest and any other NBT are
        // separate values, so they survive untouched, which is the point. The two taming systems clear it
        // differently, hence the split.
        if (pet instanceof TamableAnimal tamable) {
            tamable.setInSittingPose(false);
            tamable.setOrderedToSit(false);
            tamable.setTame(false, false);
            tamable.setOwnerUUID(null);
        } else if (pet instanceof AbstractHorse horse) {
            horse.setTamed(false);
            horse.setOwnerUUID(null);
        }
        pet.setTarget(null);

        if (pet instanceof PathfinderMob pathfinder) {
            pathfinder.goalSelector.addGoal(0,
                    new BrokenBondsFleeGoal(pathfinder, from, Config.BROKEN_BONDS_FLEE_TICKS.get()));
        }

        level.playSound(null, pet.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 1.0F, 0.9F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                pet.getX(), pet.getEyeY() + 0.4, pet.getZ(), 8, 0.3, 0.2, 0.3, 0.0);
        markDiscoveredByVictim(owner);
    }

    /** A wisp of irritation as the meter climbs, so a careful owner has a hint something's brewing. */
    private static void sulk(ServerLevel level, Animal pet, float fraction) {
        if (fraction < 0.5F || pet.getRandom().nextInt(4) != 0) {
            return; // only once it's genuinely stewing, and only occasionally
        }
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                pet.getX(), pet.getEyeY() + 0.4, pet.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
    }
}
