package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * cornered and dangerous: at/below half health you get combat buffs (Strength, Speed, Resistance I), and at/
 * below a quarter they step up (level II, plus Regeneration). Nothing while healthy — it only rewards fighting
 * from behind. Effects are re-applied each tick with a short duration so they fall off the instant you heal up.
 */
public final class BlessingUnderdog extends Effect {
    public BlessingUnderdog() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.WOODEN_SWORD);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        float max = target.getMaxHealth();
        if (max <= 0.0F) {
            return;
        }
        float percent = target.getHealth() / max * 100.0F;
        int tier;
        if (percent <= Config.UNDERDOG_QUARTER_PERCENT.get()) {
            tier = 2;
        } else if (percent <= Config.UNDERDOG_HALF_PERCENT.get()) {
            tier = 1;
        } else {
            return;
        }
        int amp = tier - 1; // tier 1 -> level I (amp 0), tier 2 -> level II (amp 1)
        add(target, MobEffects.DAMAGE_BOOST, amp);
        add(target, MobEffects.MOVEMENT_SPEED, amp);
        add(target, MobEffects.DAMAGE_RESISTANCE, amp);
        if (tier == 2) {
            add(target, MobEffects.REGENERATION, 0);
        }
    }

    private static void add(ServerPlayer target, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
        target.addEffect(new MobEffectInstance(effect, 40, amp, true, false, true));
    }
}
