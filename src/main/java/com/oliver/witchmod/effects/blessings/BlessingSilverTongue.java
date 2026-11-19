package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.Config;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.minecraft.server.level.ServerPlayer;
import com.oliver.witchmod.synergy.Synergies;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * every villager suddenly thinks you're their best customer: villager trades are {@code silverTongueDiscount} (75%) cheaper. Implemented in
 * the trade hook below by refunding
 * that fraction of the cost you just paid — so you effectively pay only a quarter. The prototype's Hero of
 * the Village stand-in is dropped.
 */
public final class BlessingSilverTongue extends Effect {
    public BlessingSilverTongue() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.EMERALD_BLOCK);
    }

    /** you find out the first time you talk a villager down (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /**
     * villager trades are much cheaper, and the ui shows it: on opening a merchant each offer's special-price diff
     * (which drives both the shown price and what you pay) is knocked down and the offers re-synced. vanilla resets
     * that diff from reputation whenever anyone starts trading, so the discount never leaks to others.
     */
    @Override
    public void onContainerOpen(ServerPlayer self, PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof net.minecraft.world.inventory.MerchantMenu menu)) {
            return;
        }
        double discount = Config.SILVERTONGUE_DISCOUNT.get();
        net.minecraft.world.item.trading.MerchantOffers offers = menu.getOffers();
        for (MerchantOffer offer : offers) {
            int baseCount = offer.getBaseCostA().getCount();
            offer.setSpecialPriceDiff(-(int) Math.floor(baseCount * discount)); // costA -> ~(1-discount) of base, min 1
        }
        self.sendMerchantOffers(menu.containerId, offers, menu.getTraderLevel(), menu.getTraderXp(),
                readShowProgress(menu), menu.canRestock());
        markDiscoveredByVictim(self);
    }

    private static boolean readShowProgress(net.minecraft.world.inventory.MerchantMenu menu) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.inventory.MerchantMenu.class.getDeclaredField("showProgressBar");
            f.setAccessible(true);
            return f.getBoolean(menu);
        } catch (ReflectiveOperationException e) {
            return true; // villagers show the level bar; worst case is a cosmetic bar on a wandering trader
        }
    }

    /** silver villager synergy (with disguise): a villager-shaped trader sometimes waives a trade's cost. */
    @Override
    public void onTrade(ServerPlayer self, TradeWithVillagerEvent event) {
        if (!Synergies.SILVER_VILLAGER.activeFor(self)
                || self.getRandom().nextInt(100) >= Config.SILVER_VILLAGER_REFUND_CHANCE.get()) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        refundCost(self, offer.getCostA());
        refundCost(self, offer.getCostB());
        self.displayClientMessage(net.minecraft.network.chat.Component.translatable("witchmod.silver_tongue.refund"), true);
        self.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, self.getX(),
                self.getY() + 1.0, self.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
    }

    private static void refundCost(ServerPlayer player, ItemStack cost) {
        if (cost.isEmpty()) {
            return;
        }
        ItemStack give = cost.copy();
        if (!player.getInventory().add(give)) {
            player.drop(give, false);
        }
    }
}
