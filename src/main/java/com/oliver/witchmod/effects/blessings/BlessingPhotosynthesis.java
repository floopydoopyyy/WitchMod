package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Blessing of Photosynthesis (SUNFLOWER): you soak up the sun. In direct daylight you slowly regenerate and
 * gain a little hunger back; standing in WATER while sunlit makes it stronger (a well-watered plant). No
 * effect underground, at night, or in the rain. Discovers the first time the sun feeds you.
 */
public final class BlessingPhotosynthesis extends Effect {
    public BlessingPhotosynthesis() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.SUNFLOWER);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int interval = Config.PHOTOSYNTHESIS_INTERVAL_TICKS.get();
        if (target.tickCount % interval != 0) {
            return;
        }
        ServerLevel level = target.serverLevel();
        BlockPos pos = target.blockPosition();
        boolean sunlit = level.isDay() && level.canSeeSky(pos) && !level.isRaining();
        if (!sunlit) {
            return;
        }
        boolean watered = target.isInWater();           // a well-watered plant photosynthesises harder
        int amp = watered ? 1 : 0;
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, interval + 20, amp, false, false, true));
        if (target.getFoodData().getFoodLevel() < 20) {
            target.getFoodData().eat(watered ? 2 : 1, 0.1F);   // slight hunger gain
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1.0, target.getZ(),
                watered ? 5 : 2, 0.3, 0.5, 0.3, 0.0);
        markDiscoveredByVictim(target);
    }
}
