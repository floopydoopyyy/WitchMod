package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * everything thrown in your general direction finds you. Projectiles within
 * {@code RADIUS} have their velocity bent toward the victim every tick, so arrows that would have sailed
 * past instead curve in. It exists to make fighting at range miserable.
 *
 * <p><b>Direction is steered, speed is preserved.</b> The velocity is rotated toward the victim by
 * {@code STEER_STRENGTH} and then rescaled to its original magnitude, rather than having a pull added to it.
 * Adding acceleration would make every arrow hit harder the longer it flew, which turns a curse about
 * accuracy into a curse about damage — not the joke, and much harder to balance.
 *
 * <p><b>Your own projectiles are excluded</b> ({@code AFFECTS_OWN}, off by default). Having your own arrows
 * boomerang into your face isn't funny, it's just unplayable — you'd be unable to use a bow at all rather
 * than merely being easy to hit.
 */
public final class CurseMagnet extends Effect {
    public CurseMagnet() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.LODESTONE);
    }

    /** you find out the first time something visibly bends toward you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        double radius = Config.MAGNET_RADIUS.get();
        double strength = Config.MAGNET_STEER_STRENGTH.get();
        boolean affectsOwn = Config.MAGNET_AFFECTS_OWN.get();
        int cap = Config.MAGNET_MAX_PROJECTILES.get();

        int steered = 0;
        for (Projectile projectile : target.serverLevel().getEntitiesOfClass(Projectile.class,
                target.getBoundingBox().inflate(radius))) {
            if (steered >= cap) {
                break;
            }
            if (!affectsOwn && isOwnedBy(projectile, target)) {
                continue;
            }
            if (steer(projectile, target, strength)) {
                steered++;
            }
        }
        if (steered > 0) {
            markDiscoveredByVictim(target);
        }

        // beyond projectiles, harmful THINGS creep toward you too — subtly, so it reads as bad luck rather
        // than a yank. Primed TNT is the headline case: it drifts your way while its fuse burns down.
        double hazardPull = Config.MAGNET_HAZARD_PULL.get();
        if (hazardPull > 0.0) {
            for (PrimedTnt tnt : target.serverLevel().getEntitiesOfClass(PrimedTnt.class,
                    target.getBoundingBox().inflate(radius))) {
                Vec3 toVictim = target.position().subtract(tnt.position());
                double d = toVictim.length();
                if (d > 1.5) { // don't fuss with TNT already on top of you
                    tnt.setDeltaMovement(tnt.getDeltaMovement().add(toVictim.normalize().scale(hazardPull)));
                    tnt.hasImpulse = true;
                    markDiscoveredByVictim(target);
                }
            }
        }

        // magnetic-storm synergy (with Thunder): in a thunderstorm under open sky you literally draw lightning.
        net.minecraft.server.level.ServerLevel level = target.serverLevel();
        if (level.isThundering() && level.canSeeSky(target.blockPosition())
                && com.oliver.witchmod.data.EffectUtil.every(ticksRemaining, Config.MAGNET_STORM_INTERVAL.get())
                && target.getRandom().nextInt(100) < Config.MAGNET_STORM_CHANCE.get()
                && com.oliver.witchmod.synergy.Synergies.MAGNETIC_STORM.activeFor(target)) {
            net.minecraft.world.entity.LightningBolt bolt =
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(target.blockPosition()));
                level.addFreshEntity(bolt);
                markDiscoveredByVictim(target);
            }
        }
    }

    private static boolean isOwnedBy(Projectile projectile, ServerPlayer target) {
        Entity owner = projectile.getOwner();
        return owner != null && owner.getUUID().equals(target.getUUID());
    }

    /** @return true if this projectile was actually moving and got bent */
    private static boolean steer(Projectile projectile, ServerPlayer target, double strength) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-3) {
            return false; // already stuck in a block, or otherwise not going anywhere
        }
        Vec3 toVictim = target.getEyePosition().subtract(projectile.position());
        if (toVictim.lengthSqr() < 1.0E-4) {
            return false;
        }

        // rotate toward the victim, then restore the original speed — see the class note on why this is a
        // steer rather than a pull.
        Vec3 aimed = velocity.normalize().lerp(toVictim.normalize(), strength);
        if (aimed.lengthSqr() < 1.0E-6) {
            return false;
        }
        Vec3 result = aimed.normalize().scale(speed);
        projectile.setDeltaMovement(result);
        projectile.hasImpulse = true;

        // point it where it's actually going, or arrows visibly fly sideways down their old heading.
        projectile.setYRot((float) (Mth.atan2(result.x, result.z) * (180.0 / Math.PI)));
        projectile.setXRot((float) (Mth.atan2(result.y, result.horizontalDistance()) * (180.0 / Math.PI)));
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
        return true;
    }
}
