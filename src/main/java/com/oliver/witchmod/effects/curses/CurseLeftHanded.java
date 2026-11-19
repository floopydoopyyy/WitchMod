package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.Config;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * everything's on the wrong side: your held item renders in the opposite hand, and trying to type in chat
 * comes out as letter spam. A small aim wobble is thrown in as an extra coordination issue
 * ({@code leftHandedDriftDegrees}). All client-side off the synced
 * {@link WitchModAttachments#LEFT_HANDED_ACTIVE} flag — see {@code client/ClientCurseHandler}.
 */
public final class CurseLeftHanded extends Effect {
    public CurseLeftHanded() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SHEARS);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.LEFT_HANDED_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.LEFT_HANDED_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.LEFT_HANDED_ACTIVE) < 0) {
            target.setData(WitchModAttachments.LEFT_HANDED_ACTIVE, 1); // self-heal after a relog
        }
    }

    /**
     * a slight bloom on anything you loose, on top of the client aim sway. the velocity is jittered but rescaled
     * to its original speed, so it only bends the aim, never the power.
     */
    @Override
    public void onProjectileLaunched(ServerPlayer self, Projectile projectile) {
        double bloom = Config.LEFT_HANDED_PROJECTILE_BLOOM.get();
        Vec3 v = projectile.getDeltaMovement();
        double speed = v.length();
        if (bloom <= 0.0 || speed < 1.0e-4) {
            return;
        }
        var rng = self.getRandom();
        Vec3 jitter = new Vec3(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5).scale(bloom);
        projectile.setDeltaMovement(v.add(jitter).normalize().scale(speed)); // steer only: keep the original speed
        projectile.hasImpulse = true;
    }
}
