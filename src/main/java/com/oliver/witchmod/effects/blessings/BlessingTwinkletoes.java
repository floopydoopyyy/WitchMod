package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;

/**
 * light on your feet — you take no fall damage at all.
 * The negation itself lives in the {@code LivingFallEvent} listener (it zeroes the
 * fall-damage multiplier); the old prototype's Slow Falling + Speed potion effects are dropped, since the spec
 * is simply fall-damage immunity, not floaty movement.
 */
public final class BlessingTwinkletoes extends Effect {
    public BlessingTwinkletoes() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.HAY_BLOCK);
    }

    /** you find out the first time a fall that should have hurt simply doesn't (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** total fall-damage immunity. discovery only fires if the fall would actually have hurt. */
    @Override
    public void onFall(ServerPlayer self, LivingFallEvent event) {
        boolean wouldHaveHurt = event.getDistance() > 3.0F && event.getDamageMultiplier() > 0.0F;
        event.setDamageMultiplier(0.0F);
        if (wouldHaveHurt) {
            markDiscoveredByVictim(self);
            // a soft landing puff at the feet, more of it the harder the fall you shrugged off
            int count = (int) Math.min(24, 6 + event.getDistance());
            self.serverLevel().sendParticles(ParticleTypes.CLOUD, self.getX(), self.getY() + 0.05, self.getZ(),
                    count, 0.28, 0.02, 0.28, 0.02);
        }
    }
}
