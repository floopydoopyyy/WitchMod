package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.entities.CloneEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * blessing of Confusion (OMINOUS BOTTLE): you throw off exact clones of yourself — same skin, same nametag —
 * that wander and do fake actions (swinging at nearby monsters, looking about), so onlookers and mobs can't
 * tell which one is you. Each clone has 1 HP, popping in a flash of dust when hit. It's meant to help you
 * blend into a crowd, so clones are emitted only rarely when there's nobody around to fool.
 */
public final class BlessingConfusion extends Effect {
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    private static final Map<UUID, List<Integer>> CLONES = new HashMap<>();

    public BlessingConfusion() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.RABBIT_HIDE);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        List<Integer> ids = CLONES.remove(target.getUUID());
        if (ids != null && target.level() instanceof ServerLevel level) {
            for (int id : ids) {
                if (level.getEntity(id) instanceof CloneEntity c) {
                    c.discard();
                }
            }
        }
        TIMER.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        ServerLevel level = target.serverLevel();
        List<Integer> ids = CLONES.computeIfAbsent(id, k -> new ArrayList<>());
        // prune dead / gone clones.
        for (Iterator<Integer> it = ids.iterator(); it.hasNext(); ) {
            Entity e = level.getEntity(it.next());
            if (!(e instanceof CloneEntity c) || !c.isAlive()) {
                it.remove();
            }
        }

        // every so often, tug a nearby hostile onto a clone instead of you — sow confusion.
        if (target.tickCount % 20 == 0 && !ids.isEmpty()) {
            confuseHostiles(target, ids);
        }

        int timer = TIMER.getOrDefault(id, Config.CONFUSION_INTERVAL_TICKS.get());
        if (--timer > 0) {
            TIMER.put(id, timer);
            return;
        }
        TIMER.put(id, Config.CONFUSION_INTERVAL_TICKS.get());
        maybeSpawn(target, ids);
    }

    private void maybeSpawn(ServerPlayer target, List<Integer> ids) {
        if (ids.size() >= Config.CONFUSION_MAX_CLONES.get()) {
            return;
        }
        RandomSource rng = target.getRandom();
        if (!hasAudience(target) && rng.nextInt(6) != 0) {
            return; // nobody to fool → only very rarely bother
        }
        ServerLevel level = target.serverLevel();
        CloneEntity clone = WitchModEntities.CLONE.get().create(level);
        if (clone == null) {
            return;
        }
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = 2.0 + rng.nextDouble() * 3.0;
        double x = target.getX() + Math.cos(ang) * dist;
        double z = target.getZ() + Math.sin(ang) * dist;
        clone.setOwnerInfo(target);
        clone.moveTo(x, target.getY(), z, rng.nextFloat() * 360F, 0F);
        clone.setLifetime(Config.CONFUSION_CLONE_LIFETIME_TICKS.get());
        level.addFreshEntity(clone);
        ids.add(clone.getId());
        // A stream of dust FROM you TO the clone, so it looks like the clone peels off you rather than
        // popping out of thin air.
        streamToClone(level, target.getX(), target.getEyeY(), target.getZ(), x, target.getY() + 1.0, z);
        level.sendParticles(ParticleTypes.POOF, x, target.getY() + 0.9, z, 12, 0.3, 0.5, 0.3, 0.02);
        markDiscoveredByVictim(target);
    }

    /** A trail of particles running from the player toward the new clone. */
    private static void streamToClone(ServerLevel level, double fx, double fy, double fz,
                                      double tx, double ty, double tz) {
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            level.sendParticles(ParticleTypes.CLOUD, fx + dx * f, fy + dy * f, fz + dz * f, 1, 0.03, 0.03, 0.03, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.POOF, fx + dx * f, fy + dy * f, fz + dz * f, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /** redirect some nearby hostiles onto the clones so you get lost in the crowd. */
    private void confuseHostiles(ServerPlayer target, List<Integer> ids) {
        ServerLevel level = target.serverLevel();
        AABB box = target.getBoundingBox().inflate(16.0);
        List<CloneEntity> clones = new ArrayList<>();
        for (int cid : ids) {
            if (level.getEntity(cid) instanceof CloneEntity c && c.isAlive()) {
                clones.add(c);
            }
        }
        if (clones.isEmpty()) {
            return;
        }
        // nearby hostiles auto-aggro onto the doppelgangers: anything idle, or already coming for YOU, gets
        // pointed at a clone instead — so the crowd of copies draws the heat.
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy
                && (e.getTarget() == null || e.getTarget() == target))) {
            m.setTarget(clones.get(target.getRandom().nextInt(clones.size())));
        }
    }

    /** other players or hostile/angry mobs nearby — someone worth blending in from. */
    private static boolean hasAudience(ServerPlayer target) {
        AABB box = target.getBoundingBox().inflate(24.0);
        for (LivingEntity e : target.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != target)) {
            if (e instanceof Player) {
                return true;
            }
            if (e instanceof Enemy) {
                return true;
            }
            if (e instanceof net.minecraft.world.entity.NeutralMob nm && nm.isAngry()) {
                return true;
            }
        }
        return false;
    }
}
