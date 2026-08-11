package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * A coach for your trade partners (master-spec Personal Trainer, id {@code trainer}, sacrificial item XP
 * BOTTLE): villagers gain {@code trainerVillagerXpMultiplier}× the XP from YOUR trades, so they level up
 * their profession far faster. Applied in {@code BlessingEventHandler.onPersonalTrainerTrade} on
 * {@code TradeWithVillagerEvent}. The old prototype's trickle of player XP is dropped — the spec is about the
 * villager's XP, not yours.
 */
public final class BlessingTrainer extends Effect {
    public BlessingTrainer() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.EXPERIENCE_BOTTLE);
    }

    /** You find out the first time a trade juices a villager's progress (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
