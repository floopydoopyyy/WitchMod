package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.Config;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import com.oliver.witchmod.WitchMod;

/**
 * A coach for your trade partners: villagers gain {@code trainerVillagerXpMultiplier}× the XP from YOUR trades, so they level up
 * their profession far faster. Applied in the hook below on
 * {@code TradeWithVillagerEvent}. The old prototype's trickle of player XP is dropped — the spec is about the
 * villager's XP, not yours.
 */
public final class BlessingTrainer extends Effect {
    public BlessingTrainer() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.EXPERIENCE_BOTTLE);
    }

    /** you find out the first time a trade juices a villager's progress (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** villagers gain extra xp from your trades and level up fast — the whole crowd learns. */
    @Override
    public void onTrade(ServerPlayer self, TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager villager)) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        int extra = (offer.shouldRewardExp() && offer.getXp() > 0)
                ? (int) Math.round(offer.getXp() * (Config.TRAINER_VILLAGER_XP_MULT.get() - 1.0)) : 0;
        if (extra > 0) {
            coachVillager(self, villager, extra, true);
            double r = Config.TRAINER_NEARBY_RADIUS.get();
            if (r > 0) {
                for (Villager other : self.serverLevel().getEntitiesOfClass(Villager.class,
                        villager.getBoundingBox().inflate(r), v -> v != villager && v.isAlive())) {
                    coachVillager(self, other, extra, false);
                }
            }
        }
        markDiscoveredByVictim(self);
    }

    /** add coached xp to one villager, schedule its fast level-up, and (for the traded one) live-update the screen. */
    private static void coachVillager(ServerPlayer player, Villager villager, int extra, boolean traded) {
        villager.setVillagerXp(villager.getVillagerXp() + extra);
        scheduleVillagerLevelUp(villager);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(),
                villager.getY() + villager.getBbHeight() * 0.6, villager.getZ(), traded ? 14 : 8, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6F, 1.2F);
        // the traded villager's screen keeps the xp it synced at open — re-push so its bar fills live (showProgress
        // hard true: a villager always shows the bar; the server menu's own flag reads false and hid it)
        if (traded && player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu) {
            player.sendMerchantOffers(menu.containerId, villager.getOffers(),
                    villager.getVillagerData().getLevel(), villager.getVillagerXp(), true, menu.canRestock());
        }
    }

    // villager's private level-up scheduling fields, resolved once. the same pair vanilla's rewardTradeXp writes:
    // the flag says "level up on the next merchant update" and the timer (which only counts down while you're NOT
    // trading with it) delays it a beat, so trades don't regenerate out from under your open screen.
    private static java.lang.reflect.Field villagerLevelUpFlag;
    private static java.lang.reflect.Field villagerUpdateTimer;
    private static boolean villagerFieldsResolved;

    private static void resolveVillagerFields() {
        if (villagerFieldsResolved) {
            return;
        }
        villagerFieldsResolved = true;
        try {
            villagerLevelUpFlag = Villager.class.getDeclaredField("increaseProfessionLevelOnUpdate");
            villagerLevelUpFlag.setAccessible(true);
            villagerUpdateTimer = Villager.class.getDeclaredField("updateMerchantTimer");
            villagerUpdateTimer.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            WitchMod.LOGGER.error("[Trainer] could not resolve villager level-up fields — villagers won't level "
                    + "up faster: {}", e.toString());
        }
    }

    /**
     * if the villager's xp is now at/over its level-up threshold, schedule the profession level-up exactly the
     * way vanilla does — needed because vanilla's own check ran on the base xp before this event fired.
     */
    private static void scheduleVillagerLevelUp(Villager villager) {
        int level = villager.getVillagerData().getLevel();
        if (!VillagerData.canLevelUp(level) || villager.getVillagerXp() < VillagerData.getMaxXpPerLevel(level)) {
            return;
        }
        resolveVillagerFields();
        if (villagerLevelUpFlag == null || villagerUpdateTimer == null) {
            return;
        }
        try {
            if (!villagerLevelUpFlag.getBoolean(villager)) { // don't reset an already-scheduled level-up
                villagerLevelUpFlag.setBoolean(villager, true);
                villagerUpdateTimer.setInt(villager, Config.TRAINER_LEVELUP_DELAY_TICKS.get());
            }
        } catch (ReflectiveOperationException e) {
            WitchMod.LOGGER.error("[Trainer] couldn't schedule villager level-up: {}", e.toString());
        }
    }
}
