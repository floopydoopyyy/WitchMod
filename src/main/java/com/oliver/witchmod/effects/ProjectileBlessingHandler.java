package com.oliver.witchmod.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The two archery blessings, which both hook the moment a projectile is fired.
 *
 * <ul>
 *   <li><b>Steady Hands</b> — bows/crossbows are extremely accurate (fired arrows/fireworks are re-aimed to
 *       your exact line with a tiny spread, which also COMPRESSES multishot so the pellets fly together and
 *       can all hit) and charge quicker (the draw/load use-tick is sped up).</li>
 *   <li><b>Hawk Guy</b> — every projectile you loose subtly HOMES on whoever you were aiming at: a cone
 *       raycast at spawn picks the intended target, stored on the projectile, and it bends toward that target
 *       each tick. Works for arrows and crossbow FIREWORKS alike.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class ProjectileBlessingHandler {
    private ProjectileBlessingHandler() {}

    /**
     * Steady Hands: draw a bow / load a crossbow quicker by crediting extra charge ticks each use tick.
     * <b>Runs on BOTH sides</b> (gated on the SYNCED flag, not {@code isActive}) so the client speeds up the
     * visible draw to match the server — otherwise the animation looks normal but the shot fires at full power,
     * which reads as broken.
     */
    @SubscribeEvent
    static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)
                || player.getData(WitchModAttachments.STEADY_HANDS_ACTIVE) < 0) {
            return;
        }
        if (event.getItem().getItem() instanceof BowItem || event.getItem().getItem() instanceof CrossbowItem) {
            int extra = Config.STEADYHANDS_CHARGE_SPEEDUP_TICKS.get();
            if (extra > 0) {
                event.setDuration(Math.max(0, event.getDuration() - extra));
            }
        }
    }

    /** As a projectile enters the world: Steady Hands re-aim, then Hawk Guy target acquisition. */
    @SubscribeEvent
    static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof Projectile projectile)
                || projectile instanceof FishingHook) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();

        // Steady Hands: COMPRESS each shot toward your aim rather than snapping it dead-on. Keeping a fraction
        // of the natural spread makes single shots very accurate while leaving multishot a tight-but-visible
        // fan (so the three arrows aren't stacked inside each other, yet can still all hit one target).
        if (EffectManager.isActive(shooter, Blessings.STEADY_HANDS)
                && (projectile instanceof AbstractArrow || projectile instanceof FireworkRocketEntity)) {
            Vec3 velocity = projectile.getDeltaMovement();
            double speed = velocity.length();
            if (speed > 1.0E-3) {
                Vec3 look = shooter.getViewVector(1.0F);
                double retain = Config.STEADYHANDS_SPREAD_RETAIN.get();
                Vec3 compressed = look.lerp(velocity.normalize(), retain); // look at retain=0, vanilla at retain=1
                if (compressed.lengthSqr() > 1.0E-6) {
                    faceVelocity(projectile, compressed.normalize().scale(speed));
                    Blessings.STEADY_HANDS.get().markDiscoveredByVictim(shooter);
                }
            }
        }

        // Hawk Guy: mark the intended target so the projectile homes on it.
        if (EffectManager.isActive(shooter, Blessings.HAWK_GUY)) {
            LivingEntity target = acquireTarget(level, shooter);
            if (target != null) {
                projectile.setData(WitchModAttachments.HAWKGUY_TARGET, target.getId());
                Blessings.HAWK_GUY.get().markDiscoveredByVictim(shooter);
            }
        }
    }

    /** Hawk Guy: each tick, bend a marked AIRBORNE projectile toward its target (speed-preserving). */
    @SubscribeEvent
    static void onProjectileTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Projectile projectile) || projectile.level().isClientSide()) {
            return;
        }
        // Forgiveness: YOUR projectiles have an enlarged effective hitbox — curve onto an entity whose 40%-bigger
        // box the shot was about to pass through, so near-misses connect. Only the shooter's own shots benefit.
        if (projectile.getDeltaMovement().lengthSqr() > 0.02 * 0.02
                && projectile.getOwner() instanceof ServerPlayer shooter
                && EffectManager.isActive(shooter, Blessings.FORGIVENESS)) {
            LivingEntity near = forgivenessTarget(projectile, shooter);
            if (near != null) {
                steer(projectile, near.getBoundingBox().getCenter(), Config.FORGIVENESS_PROJECTILE_STEER.get());
                Blessings.FORGIVENESS.get().markDiscoveredByVictim(shooter); // discover on the first assisted shot
            }
        }
        int targetId = projectile.getData(WitchModAttachments.HAWKGUY_TARGET);
        if (targetId < 0) {
            return;
        }
        // Once it's landed/stuck (a stuck arrow has zero velocity) or otherwise stopped, drop the mark so we
        // don't keep doing entity lookups + distance maths for it every tick for the rest of its life on the
        // floor. This is the fix for arrows "still tracking on the ground".
        if (projectile.getDeltaMovement().lengthSqr() < 0.02 * 0.02) {
            projectile.setData(WitchModAttachments.HAWKGUY_TARGET, -1);
            return;
        }
        Entity target = ((ServerLevel) projectile.level()).getEntity(targetId);
        double maxRange = Config.HAWKGUY_MAX_HOMING_RANGE.get();
        if (target == null || !target.isAlive() || projectile.distanceToSqr(target) > maxRange * maxRange) {
            projectile.setData(WitchModAttachments.HAWKGUY_TARGET, -1); // give up — target gone or too far
            return;
        }
        steer(projectile, target.getBoundingBox().getCenter(), Config.HAWKGUY_HOMING_STRENGTH.get());
    }

    /** Forgiveness: the nearest living entity whose ENLARGED box the projectile's forward path is about to cross. */
    private static LivingEntity forgivenessTarget(Projectile p, ServerPlayer shooter) {
        Vec3 vel = p.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-4) {
            return null;
        }
        Vec3 pos = p.position();
        Vec3 to = pos.add(vel.normalize().scale(2.5)); // look ~2.5 blocks ahead of the shot
        double baseInflate = Config.FORGIVENESS_HITBOX_INFLATE.get();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.0),
                e -> e != shooter && e.isAlive() && e.isPickable() && !e.isSpectator())) {
            double inf = Math.max(baseInflate, e.getBbWidth() * 0.2); // ~40% wider, with a floor that helps babies
            if (e.getBoundingBox().inflate(inf).clip(pos, to).isPresent()) {
                double d = e.distanceToSqr(pos);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /** The intended target: the living entity most in line with where the shooter is aiming, with clear sight. */
    private static LivingEntity acquireTarget(ServerLevel level, ServerPlayer shooter) {
        double range = Config.HAWKGUY_ACQUIRE_RANGE.get();
        double cosCone = Math.cos(Math.toRadians(Config.HAWKGUY_ACQUIRE_CONE.get()));
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();

        LivingEntity best = null;
        double bestDot = cosCone; // must be at least inside the cone to qualify
        AABB box = shooter.getBoundingBox().inflate(range);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != shooter && e.isAlive() && e.isPickable() && !e.isSpectator())) {
            Vec3 toTarget = candidate.getBoundingBox().getCenter().subtract(eye);
            double dist = toTarget.length();
            if (dist > range || dist < 1.0E-3) {
                continue;
            }
            double dot = toTarget.scale(1.0 / dist).dot(look);
            if (dot > bestDot && hasLineOfSight(level, shooter, eye, candidate)) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer shooter, Vec3 eye, LivingEntity target) {
        Vec3 targetCentre = target.getBoundingBox().getCenter();
        return level.clip(new ClipContext(eye, targetCentre, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Bend the projectile's velocity toward {@code point}, preserving speed (mirrors the Magnet steer). */
    private static void steer(Projectile projectile, Vec3 point, double strength) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-3) {
            return; // stuck in a block, or otherwise not moving
        }
        Vec3 toTarget = point.subtract(projectile.position());
        if (toTarget.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 aimed = velocity.normalize().lerp(toTarget.normalize(), strength);
        if (aimed.lengthSqr() < 1.0E-6) {
            return;
        }
        faceVelocity(projectile, aimed.normalize().scale(speed));
    }

    /** Set the projectile's velocity and point its model where it's now heading (or it flies sideways). */
    private static void faceVelocity(Projectile projectile, Vec3 result) {
        projectile.setDeltaMovement(result);
        projectile.hasImpulse = true;
        projectile.setYRot((float) (Mth.atan2(result.x, result.z) * (180.0 / Math.PI)));
        projectile.setXRot((float) (Mth.atan2(result.y, result.horizontalDistance()) * (180.0 / Math.PI)));
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
    }

    /**
     * Steady Hands: let each of your arrows OR fireworks deal FULL damage even when they land together — after
     * one lands, clear the victim's short invulnerability window so the next in the volley isn't absorbed
     * (vanilla otherwise ignores same-tick hits that aren't larger than the first). Using the post-damage hook
     * (rather than a projectile-impact one) is what lets it cover crossbow FIREWORKS, whose damage is an AOE
     * explosion that never goes through a direct projectile-on-entity impact.
     */
    @SubscribeEvent
    static void onSteadyHandsDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        var source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer shooter)
                || !EffectManager.isActive(shooter, Blessings.STEADY_HANDS)) {
            return;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow || direct instanceof FireworkRocketEntity) {
            event.getEntity().invulnerableTime = 0;
            event.getEntity().hurtTime = 0;
        }
    }
}
