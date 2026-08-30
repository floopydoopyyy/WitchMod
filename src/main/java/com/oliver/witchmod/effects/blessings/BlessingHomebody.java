package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Homebody (sacrificial item BED): there's no place like home. While you're near your SPAWN POINT — your bed
 * (or the world spawn if you've no bed) — you're granted Regeneration and Haste, so base is where you heal
 * and build fastest. Wander off and the comfort fades.
 */
public final class BlessingHomebody extends Effect {
    public BlessingHomebody() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.RED_BED);
    }

    /** Not instantly noticeable — you discover it the first time home wraps you in its comfort. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        BlockPos home = homeIn(target);
        if (home == null) {
            return;
        }
        double r = Config.HOMEBODY_RADIUS.get();
        if (target.distanceToSqr(home.getX() + 0.5, home.getY() + 0.5, home.getZ() + 0.5) > r * r) {
            return;
        }
        // Refreshed each tick (short duration + ambient so it doesn't clutter the HUD with swirls).
        int amp = Config.HOMEBODY_REGEN_AMPLIFIER.get();
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, amp, true, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, Config.HOMEBODY_HASTE_AMPLIFIER.get(), true, false, true));
        markDiscoveredByVictim(target);
    }

    /** Your spawn point in THIS dimension: your bed/anchor if set here, otherwise the overworld's shared spawn. */
    private static BlockPos homeIn(ServerPlayer target) {
        BlockPos respawn = target.getRespawnPosition();
        if (respawn != null && target.getRespawnDimension() == target.level().dimension()) {
            return respawn;
        }
        if (target.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            return target.serverLevel().getSharedSpawnPos();
        }
        return null;
    }
}
