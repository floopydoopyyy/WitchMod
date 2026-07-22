package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.goals.FarmhandBlockGoal;

/**
 * You cannot get a moment's peace (master-spec Farmhand). Every untamed animal nearby drops what it's doing
 * and makes it its life's mission to stand exactly where you're building (or hem you in) — and if there
 * aren't enough animals around, more are conjured (where the game would naturally allow it).
 *
 * <p>The "get in the way" behaviour is a {@link FarmhandBlockGoal} added at a priority below panic/flee, so
 * hurting an animal still sends it running — it just wanders straight back afterward. Recruitment is guarded
 * by checking the animal's goal list (NOT a persistent scoreboard tag): goals are transient, so a tag would
 * survive a world reload while the goal didn't, leaving previously-tagged animals permanently un-recruited.
 */
public final class CurseFarmhand extends Effect {
    /** Per-player cooldown before the next top-up spawn burst. */
    private static final Map<UUID, Long> NEXT_SPAWN = new HashMap<>();

    public CurseFarmhand() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.WHEAT);
    }

    /** You realise soon enough, when the cows won't leave you alone (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_SPAWN.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();

        // Discovery: the moment an animal is actually planted in front of you (idempotent — only alerts once).
        if (animalInFront(target, level)) {
            markDiscoveredByVictim(target);
        }

        if (level.getGameTime() % Config.FARMHAND_RECRUIT_INTERVAL_TICKS.get() != 0) {
            return;
        }

        List<Animal> nearby = level.getEntitiesOfClass(Animal.class,
                area(target), CurseFarmhand::isRecruitable);
        for (Animal animal : nearby) {
            FarmhandBlockGoal existing = findFarmhandGoal(animal);
            if (existing != null && existing.isTargeting(target)) {
                continue; // already working for this player
            }
            if (existing != null) {
                // Stale goal bound to a previous ServerPlayer instance (respawn/relog replaces the object),
                // which would sit dormant forever. Swap it for one bound to the player who's here now.
                animal.goalSelector.removeGoal(existing);
            }
            // PRIORITY 1: getting in the way is this animal's top job, above wandering/grazing/breeding.
            // (Float/panic also sit at 0-1, so a hit still makes it flinch — it just wanders back.)
            animal.goalSelector.addGoal(1, new FarmhandBlockGoal(animal, target));
        }

        if (nearby.size() < Config.FARMHAND_MIN_ANIMALS.get()) {
            topUp(target, level);
        }
    }

    /** True if a hijacked mob is right in front of the victim — close and within their view cone. */
    private static boolean animalInFront(ServerPlayer target, ServerLevel level) {
        Vec3 look = target.getLookAngle();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        if (lookFlat.lengthSqr() < 1.0E-4) {
            return false;
        }
        lookFlat = lookFlat.normalize();
        double frontDist = 2.5;
        double cosCone = Math.cos(Math.toRadians(45.0));
        for (Animal mob : level.getEntitiesOfClass(Animal.class,
                target.getBoundingBox().inflate(frontDist), CurseFarmhand::isRecruitable)) {
            Vec3 to = new Vec3(mob.getX() - target.getX(), 0.0, mob.getZ() - target.getZ());
            if (to.lengthSqr() < 1.0E-4) {
                return true; // standing on top of you counts
            }
            if (to.normalize().dot(lookFlat) >= cosCone) {
                return true;
            }
        }
        return false;
    }

    private static AABB area(ServerPlayer target) {
        return target.getBoundingBox().inflate(Config.FARMHAND_RADIUS.get());
    }

    /** Passive and NOT tamed/owned — only animals get conscripted (Oliver's call: passive mobs only). */
    private static boolean isRecruitable(Animal animal) {
        return animal.isAlive() && !(animal instanceof TamableAnimal tam && tam.isTame());
    }

    /**
     * This animal's existing Farmhand goal, or null. Checked instead of a save-persistent scoreboard tag:
     * goals are transient, so a tag would survive a world reload while the goal didn't.
     */
    private static FarmhandBlockGoal findFarmhandGoal(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream()
                .map(w -> w.getGoal())
                .filter(FarmhandBlockGoal.class::isInstance)
                .map(FarmhandBlockGoal.class::cast)
                .findFirst()
                .orElse(null);
    }

    // --- Top-up spawning (only where an animal would naturally spawn) ----------------------------------

    private void topUp(ServerPlayer target, ServerLevel level) {
        long now = level.getGameTime();
        if (now < NEXT_SPAWN.getOrDefault(target.getUUID(), 0L)) {
            return;
        }
        NEXT_SPAWN.put(target.getUUID(), now + Config.FARMHAND_SPAWN_COOLDOWN_TICKS.get());
        for (int i = 0; i < Config.FARMHAND_SPAWN_ATTEMPTS.get(); i++) {
            trySpawnAnimal(target, level);
        }
    }

    private void trySpawnAnimal(ServerPlayer target, ServerLevel level) {
        double min = Config.FARMHAND_SPAWN_RADIUS_MIN.get();
        double max = Config.FARMHAND_SPAWN_RADIUS_MAX.get();
        double angle = target.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = min + target.getRandom().nextDouble() * (max - min);
        int x = (int) Math.floor(target.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(target.getZ() + Math.sin(angle) * dist);

        // Which animals belong in this biome (cows/pigs/sheep in plains, etc.).
        var creatures = level.getBiome(new BlockPos(x, target.blockPosition().getY(), z))
                .value().getMobSettings().getMobs(MobCategory.CREATURE);
        if (creatures.isEmpty()) {
            return;
        }
        Optional<MobSpawnSettings.SpawnerData> pick = creatures.getRandom(target.getRandom());
        if (pick.isEmpty()) {
            return;
        }
        EntityType<?> type = pick.get().type;

        // Only where vanilla itself would allow this animal (grass, light, footing) — "correct conditions".
        for (int dy = 3; dy >= -6; dy--) {
            BlockPos pos = new BlockPos(x, target.blockPosition().getY() + dy, z);
            if (!SpawnPlacements.getPlacementType(type).isSpawnPositionOk(level, pos, type)) {
                continue;
            }
            if (!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, target.getRandom())) {
                return;
            }
            Entity created = type.create(level);
            if (!(created instanceof Animal animal)) {
                if (created != null) {
                    created.discard();
                }
                return;
            }
            animal.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    target.getRandom().nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(animal)) {
                animal.discard();
                return;
            }
            animal.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            animal.goalSelector.addGoal(1, new FarmhandBlockGoal(animal, target));
            level.addFreshEntity(animal);
            return;
        }
    }
}
