package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * you're a cow now (sacrificial item LEATHER). Right-clicking nothing with an empty bucket milks YOURSELF into
 * a milk bucket, and other players can milk you the same way (right-click you with an empty bucket). That's the
 * whole blessing — no other use. The milking is handled in the hooks below (a right-click-item hook
 * for self-milking and an entity-interact hook for being milked).
 */
public final class BlessingCow extends Effect {
    public BlessingCow() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.LEATHER);
    }

    /** discovered the first time you actually get milked. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // no persistent state — everything is event-driven.
    }

    @Override
    public void onRightClickItem(ServerPlayer self, PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().is(Items.BUCKET)) {
            milk(self, event.getHand(), event.getItemStack(), self);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @Override
    public void onInteractedWith(ServerPlayer self, Player by, PlayerInteractEvent.EntityInteract event) {
        if (event.getItemStack().is(Items.BUCKET)) {
            milk(by, event.getHand(), event.getItemStack(), self);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private void milk(Player milker, InteractionHand hand, ItemStack bucket, ServerPlayer cow) {
        milker.setItemInHand(hand, ItemUtils.createFilledResult(bucket, milker, new ItemStack(Items.MILK_BUCKET)));
        cow.serverLevel().playSound(null, cow.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
        markDiscoveredByVictim(cow);
    }
}
