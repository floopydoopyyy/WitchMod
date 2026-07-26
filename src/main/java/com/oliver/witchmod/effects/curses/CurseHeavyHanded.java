package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * You're rough with your gear (master-spec Heavy Handed — renamed from "Uncareful" per Oliver; the registry
 * id changed too, {@code uncareful} → {@code heavy_handed}, since display names derive from the id path via
 * {@code DiscoveryManager.titleCase}, so the id IS the visible name. Sacrificial item stays FLINT). Tools and
 * armour lose durability far faster than they should — {@code DURABILITY_MULT} (4x) as fast.
 *
 * <p><b>There's no event to modify a durability hit's amount</b>, so this watches instead: each tick it
 * remembers the damage value of everything you're holding or wearing, and when one of those goes UP (the
 * item just got used), it applies the shortfall again as EXTRA wear. Because the extra goes through
 * {@code ItemStack.hurtAndBreak}, Unbreaking still mitigates it and a piece that crosses its limit breaks
 * properly — you're careless, not enchantment-proof. Recording the value AFTER the extra is applied is what
 * stops the top-up being seen as fresh damage next tick and compounding.
 *
 * <p>Only the six slots that actually take wear are watched — both hands and the four armour pieces — so it
 * stays cheap and never touches idle inventory items, which don't lose durability anyway.
 */
public final class CurseHeavyHanded extends Effect {
    private record Tracked(Item item, int damage) {}

    /** victim -> last-seen (item, damage) per watched slot, so a rise can be detected and topped up. */
    private static final Map<UUID, Map<EquipmentSlot, Tracked>> LAST = new HashMap<>();

    private static final EquipmentSlot[] WATCHED = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public CurseHeavyHanded() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 40, () -> Items.FLINT);
    }

    /** You find out the first time your gear wears down faster than it should (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        LAST.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double extraFactor = Config.HEAVY_HANDED_DURABILITY_MULT.get() - 1.0;
        Map<EquipmentSlot, Tracked> slots = LAST.computeIfAbsent(target.getUUID(), k -> new HashMap<>());

        for (EquipmentSlot slot : WATCHED) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                slots.remove(slot);
                continue;
            }
            Tracked previous = slots.get(slot);
            int damage = stack.getDamageValue();

            // Same item as last tick, and it took some wear? Match that wear again (times the extra factor).
            if (previous != null && previous.item() == stack.getItem() && damage > previous.damage()) {
                int extra = (int) Math.round((damage - previous.damage()) * extraFactor);
                if (extra > 0) {
                    stack.hurtAndBreak(extra, level, target, item -> {});
                    markDiscoveredByVictim(target);
                }
            }
            // Record AFTER the top-up, so our own extra isn't mistaken for fresh damage next tick.
            slots.put(slot, new Tracked(stack.getItem(),
                    stack.isEmpty() ? 0 : stack.getDamageValue()));
        }
    }
}
