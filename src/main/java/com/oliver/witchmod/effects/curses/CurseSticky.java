package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * everything you're holding is stuck to you. You cannot drop items and you cannot take
 * your armour off. Containers are deliberately untouched — chests, furnaces and the like all work exactly as
 * normal, so this is an inconvenience rather than a lockout.
 *
 * <p><b>Armour is restored rather than locked.</b> Vanilla gates armour removal inside
 * {@code ArmorSlot.mayPickup}, which checks for Curse of Binding and has no hook — and actually enchanting
 * the victim's gear was rejected: if the curse ever ended abnormally they'd be left with permanently bound
 * armour, an unrecoverable mess for a temporary joke. Instead {@code LivingEquipmentChangeEvent} catches the
 * removal and puts it straight back.
 *
 * <p><b>The restore only happens if the removed piece can actually be FOUND</b> (on the cursor, or in the
 * inventory where a shift-click put it). That single condition is what makes this safe: armour that BROKE
 * has gone nowhere, so nothing is found, so nothing is restored — otherwise this would quietly hand out
 * infinite-durability armour. Death is guarded separately, so death drops behave normally.
 *
 * <p>Dropping is blocked at both ends: the drop KEY client-side, so the stack normally never leaves its
 * slot at all, and {@code ItemTossEvent} server-side as the authoritative backstop.
 *
 * <p><b>⚠ Cancelling {@code ItemTossEvent} DESTROYS the item on its own.</b> NeoForge's
 * {@code CommonHooks.onPlayerTossEvent} has already pulled the stack out of the inventory by the time the
 * event fires, and cancelling merely skips spawning the {@code ItemEntity} — nothing puts it back. (Older
 * Forge did re-add it; 21.1 does not.) The handler therefore hands the stack back explicitly with
 * {@code placeItemBackInInventory}, which cannot void: with no room it drops the stack instead. Losing the
 * curse for one item beats deleting somebody's netherite. This was a real bug, caught in play.
 */
public final class CurseSticky extends Effect {
    /** ticks between squelches, so mashing the drop key doesn't machine-gun the sound. */
    private static final int SQUELCH_COOLDOWN_TICKS = 12;
    private static final Map<UUID, Long> NEXT_SQUELCH = new HashMap<>();

    public CurseSticky() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.HONEY_BOTTLE);
    }

    /** you find out the first time something refuses to leave your hands (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.STICKY_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.STICKY_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.STICKY_ACTIVE) < 0) {
            target.setData(WitchModAttachments.STICKY_ACTIVE, 1); // self-heal after a relog
        }
    }

    /**
     * hook for equipment changing — see {@code CurseEventHandler}. Puts armour back on if the victim just
     * took it off.
     */
    public static void onArmourRemoved(ServerPlayer player, EquipmentSlot slot,
                                       ItemStack removed, ItemStack replacement) {
        if (!Config.STICKY_BLOCKS_ARMOUR.get() || removed.isEmpty()) {
            return;
        }
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return; // hands are handled by the drop block, not this
        }
        if (!player.isAlive() || player.isDeadOrDying()) {
            return; // death drops are deliberately unaffected
        }
        // only give it back if we can find where it went. If it BROKE it's nowhere, and restoring it would
        // be an infinite-durability exploit rather than a curse.
        if (!reclaim(player, removed)) {
            return;
        }
        // A SWAP is still taking it off, or swapping in a leather cap would trivially defeat the curse. The
        // piece they tried to put on goes back to their cursor rather than being eaten.
        if (!replacement.isEmpty()) {
            player.containerMenu.setCarried(replacement);
        }
        player.setItemSlot(slot, removed);
        player.containerMenu.broadcastChanges();
        squelch(player);
    }

    /**
     * the feedback for something refusing to come off: a wet honey-block squelch and a few drips.
     *
     * <p>Rate-limited, because both triggers are things a frustrated player mashes — holding Q or clicking
     * repeatedly at a helmet would otherwise machine-gun the sound into noise. One squelch every
     * {@code SQUELCH_COOLDOWN_TICKS} reads as the curse resisting; twenty a second reads as a broken mod.
     */
    public static void squelch(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long allowed = NEXT_SQUELCH.get(player.getUUID());
        if (allowed != null && now < allowed) {
            return;
        }
        NEXT_SQUELCH.put(player.getUUID(), now + SQUELCH_COOLDOWN_TICKS);

        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS,
                0.7F, 0.7F + player.getRandom().nextFloat() * 0.2F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS,
                0.4F, 0.8F + player.getRandom().nextFloat() * 0.2F);
        // deliberately sparse — a few drips off the hands, not a honey fountain.
        level.sendParticles(ParticleTypes.FALLING_HONEY,
                player.getX(), player.getY() + 1.1, player.getZ(), 6, 0.35, 0.25, 0.35, 0.0);
        level.sendParticles(ParticleTypes.ITEM_SLIME,
                player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.2, 0.3, 0.01);
    }

    /** removes the just-taken-off piece from wherever the click put it. */
    private static boolean reclaim(ServerPlayer player, ItemStack removed) {
        AbstractContainerMenu menu = player.containerMenu;
        if (ItemStack.isSameItemSameComponents(menu.getCarried(), removed)) {
            menu.setCarried(ItemStack.EMPTY);
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameComponents(inventory.getItem(slot), removed)) {
                inventory.setItem(slot, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }
}
