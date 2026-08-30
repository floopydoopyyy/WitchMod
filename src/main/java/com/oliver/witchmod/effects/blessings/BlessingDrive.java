package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Drive (sacrificial item GOLDEN CARROT): animals around you have NO breeding cooldown — the post-breeding
 * love-lockout is cleared each tick, so a herd can be bred as fast as you can hand out food. Grow your farm
 * in a flash. Adults only: a baby's negative "growing up" age is left alone (that isn't a breeding cooldown).
 */
public final class BlessingDrive extends Effect {
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
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        double r = Config.DRIVE_RADIUS.get();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, target.getBoundingBox().inflate(r),
                a -> a.isAlive() && a.getAge() > 0)) { // age > 0 = the post-breeding lockout on an ADULT
            animal.setAge(0);                          // ready to fall in love again immediately
            markDiscoveredByVictim(target);            // discover on the first cleared cooldown
            if (target.tickCount % 20 == 0 && level.random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(),
                        1, 0.2, 0.2, 0.2, 0.0);
            }
        }
        // Villagers too — clear the post-breeding lockout AND keep them willing, so a village booms.
        for (Villager villager : level.getEntitiesOfClass(Villager.class, target.getBoundingBox().inflate(r),
                v -> v.isAlive() && v.getAge() > 0)) {
            villager.setAge(0);
            markDiscoveredByVictim(target);
            if (target.tickCount % 20 == 0 && level.random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getY() + villager.getBbHeight(), villager.getZ(),
                        1, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }
}
