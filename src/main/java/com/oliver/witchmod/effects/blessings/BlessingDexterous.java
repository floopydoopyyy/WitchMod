package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import net.minecraft.world.entity.projectile.AbstractArrow;
import com.oliver.witchmod.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import com.oliver.witchmod.effects.ProjectileBlessingHandler;
import net.minecraft.world.phys.Vec3;

/**
 * dexterous (Spectral Arrow): a rock-steady, quick draw — bows and crossbows are extremely accurate and charge
 * noticeably quicker, MULTISHOT is compressed so its pellets fly together, and eating/drinking/raising a shield
 * are quicker too.
 *
 * <p>Most of it lives in {@code ProjectileBlessingHandler}: fired arrows/fireworks are re-aimed to your exact
 * line with a tiny spread ({@code dexterousSpreadRetain}), and use-actions are sped up
 * ({@code dexterousChargeSpeedupTicks}) on the item-use tick. The shield-raise gate is a fixed vanilla constant,
 * so it's shortened via {@code ShieldRaiseMixin} off the synced active flag.
 */
public final class BlessingDexterous extends Effect {
    public BlessingDexterous() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.SPECTRAL_ARROW);
    }

    /** you find out the first time a shot flies dead straight (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.DEXTEROUS_ACTIVE, 1); // synced so the client speeds the draw up + the mixin fires
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.DEXTEROUS_ACTIVE, -1);
    }

    /**
     * compress each shot toward your aim rather than snapping it dead-on: keeping a fraction of the natural
     * spread makes single shots very accurate while multishot stays a tight-but-visible fan.
     */
    @Override
    public void onProjectileLaunched(ServerPlayer self, Projectile projectile) {
        if (!(projectile instanceof AbstractArrow) && !(projectile instanceof FireworkRocketEntity)) {
            return;
        }
        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.length();
        if (speed > 1.0E-3) {
            Vec3 look = self.getViewVector(1.0F);
            double retain = Config.DEXTEROUS_SPREAD_RETAIN.get();
            Vec3 compressed = look.lerp(velocity.normalize(), retain); // look at retain=0, vanilla at retain=1
            if (compressed.lengthSqr() > 1.0E-6) {
                ProjectileBlessingHandler.faceVelocity(projectile, compressed.normalize().scale(speed));
                markDiscoveredByVictim(self);
            }
        }
    }

    /**
     * every arrow or firework of a volley deals full damage: after one lands, clear the victim's short
     * invulnerability so the next isn't absorbed. post-damage (not projectile impact) so crossbow fireworks —
     * an aoe blast, never a direct impact — are covered too.
     */
    @Override
    public void onDamagedOther(ServerPlayer self, LivingEntity victim, LivingDamageEvent.Post event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof AbstractArrow || direct instanceof FireworkRocketEntity) {
            victim.invulnerableTime = 0;
            victim.hurtTime = 0;
        }
    }
}
