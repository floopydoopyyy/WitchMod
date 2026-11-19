package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.server.level.ServerPlayer;
import com.oliver.witchmod.synergy.Synergies;
import net.minecraft.world.phys.Vec3;

/**
 * projectiles bounce off you and go home. An arrow,
 * trident, snowball — anything thrown or fired at you — is caught mid-flight and sent straight back at whoever
 * loosed it, {@code reflectVelocityMultiplier} faster than it came in. It does <b>not</b> home: it's a precise
 * shot at the attacker's position, so they can (and probably will have to) dodge.
 *
 * <p>The actual catch-and-return lives in the hooks below on {@code ProjectileImpactEvent}
 * — the one hook that fires the instant a projectile would strike you, before it deals its damage. This class
 * just declares the blessing and defers discovery to the first projectile you turn around.
 */
public final class BlessingReflect extends Effect {
    public BlessingReflect() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.TURTLE_HELMET);
    }

    /** you find out the first time something you're shot with comes straight back. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /**
     * a projectile about to strike you is caught and sent straight back at its shooter, faster: the impact is
     * cancelled (no damage, keeps flying), it's re-owned to you (can't re-hit you, can hurt the shooter) and
     * re-aimed. it doesn't home — a straight, quick shot the attacker can dodge.
     */
    @Override
    public void onStruckByProjectile(ServerPlayer self, Projectile projectile, ProjectileImpactEvent event) {
        Entity shooter = projectile.getOwner();
        if (shooter == null || shooter == self) {
            return; // nothing to send it back to, and never reflect your own shot
        }
        Vec3 dir = shooter.getEyePosition().subtract(projectile.position());
        if (dir.lengthSqr() < 1.0e-4) {
            return;
        }
        event.setCanceled(true);
        double mult = Config.REFLECT_VELOCITY_MULT.get();
        if (Synergies.DUELISTS.activeFor(self)) {
            mult *= Config.REFLECT_GLADIATOR_VELOCITY_MULT.get(); // duellist's flourish: send it further
        }
        float speed = (float) (projectile.getDeltaMovement().length() * mult);
        projectile.setOwner(self);
        projectile.shoot(dir.x, dir.y, dir.z, speed, (float) (double) Config.REFLECT_INACCURACY.get());
        // nudge it a block out of your hitbox toward the shooter so it doesn't immediately re-collide with you
        Vec3 out = dir.normalize();
        projectile.setPos(self.getX() + out.x, self.getEyeY() + out.y * 0.5, self.getZ() + out.z);
        markDiscoveredByVictim(self);
    }
}
