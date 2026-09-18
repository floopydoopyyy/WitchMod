package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.curses.CurseFarmhand;
import com.oliver.witchmod.synergy.Synergies;

/**
 * drive (sacrificial item GOLDEN CARROT): animals around you have NO breeding cooldown — the post-breeding
 * love-lockout is cleared each tick, so a herd can be bred as fast as you can hand out food. On top of that,
 * once you STOP breeding them yourself for a while, nearby same-type pairs start breeding on their own with
 * no food. Adults only: a baby's negative "growing up" age is left alone (that isn't a breeding cooldown).
 *
 * <p>nature synergy (with Farmhand): the idle gate is skipped so the auto-breeding runs constantly, and every
 * baby it produces is conscripted straight into the farmhand nuisance.
 */
public final class BlessingDrive extends Effect {
    /** player -> last game-tick they bred an animal themselves (detected from a fresh post-breed lockout). */
    private static final Map<UUID, Long> LAST_MANUAL_BREED = new HashMap<>();

    public BlessingDrive() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.GOLDEN_CARROT);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // NOT discovered here — Drive isn't instantly noticeable. You find out the first time a nearby animal's
        // breeding cooldown is quietly wiped (below).
    }

    @Override
    public void onRemove(ServerPlayer target) {
        LAST_MANUAL_BREED.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();
        double r = Config.DRIVE_RADIUS.get();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, target.getBoundingBox().inflate(r),
                a -> a.isAlive() && a.getAge() > 0)) { // age > 0 = the post-breeding lockout on an ADULT
            animal.setAge(0);                          // ready to fall in love again immediately
            LAST_MANUAL_BREED.put(target.getUUID(), now); // a fresh lockout means you just bred one yourself
            markDiscoveredByVictim(target);            // discover on the first cleared cooldown
            heart(level, animal);
        }
        // villagers too — clear the post-breeding lockout AND keep them willing, so a village booms.
        for (Villager villager : level.getEntitiesOfClass(Villager.class, target.getBoundingBox().inflate(r),
                v -> v.isAlive() && v.getAge() > 0)) {
            villager.setAge(0);
            markDiscoveredByVictim(target);
            heart(level, villager);
        }

        // spontaneous auto-breed: once you've left them alone a while (or always, with the Farmhand synergy).
        if (now % Config.DRIVE_AUTO_BREED_INTERVAL_TICKS.get() == 0) {
            boolean nature = Synergies.NATURE.activeFor(target);
            boolean idle = now - LAST_MANUAL_BREED.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2)
                    >= Config.DRIVE_AUTO_BREED_IDLE_TICKS.get();
            if ((nature || idle) && level.random.nextInt(100) < Config.DRIVE_AUTO_BREED_CHANCE_PERCENT.get()) {
                autoBreedPair(target, level, r, nature);
            }
        }
    }

    /** find one nearby same-type ready pair and breed them with no food; recruit the baby if the synergy is on. */
    private void autoBreedPair(ServerPlayer target, ServerLevel level, double r, boolean recruitBaby) {
        List<Animal> ready = level.getEntitiesOfClass(Animal.class, target.getBoundingBox().inflate(r),
                a -> a.isAlive() && !a.isBaby() && a.getAge() == 0 && a.canFallInLove());
        for (int i = 0; i < ready.size(); i++) {
            Animal a = ready.get(i);
            for (int j = i + 1; j < ready.size(); j++) {
                Animal b = ready.get(j);
                if (a.getType() != b.getType()) {
                    continue;
                }
                AgeableMob baby = a.getBreedOffspring(level, b);
                if (baby == null) {
                    continue; // this species doesn't produce young this way (e.g. mules)
                }
                baby.setBaby(true);
                baby.moveTo(a.getX(), a.getY(), a.getZ(), 0.0F, 0.0F);
                level.addFreshEntity(baby);
                a.setAge(0);
                b.setAge(0);
                a.resetLove();
                b.resetLove();
                heart(level, a);
                heart(level, b);
                markDiscoveredByVictim(target);
                if (recruitBaby && baby instanceof Animal babyAnimal) {
                    CurseFarmhand.recruit(babyAnimal, target); // straight into the nuisance
                }
                return; // one pair per attempt keeps it a trickle, not a population bomb
            }
        }
    }

    private static void heart(ServerLevel level, net.minecraft.world.entity.Entity e) {
        level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight(), e.getZ(),
                1, 0.2, 0.2, 0.2, 0.0);
    }
}
