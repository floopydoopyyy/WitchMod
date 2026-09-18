package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * nothing you use wears out. Tools AND armour take no
 * durability for the duration.
 *
 * <p><b>It WATCHES and heals, rather than stamping items {@code Unbreakable}.</b> Only the six slots that
 * actually wear — both hands and the four armour pieces — are checked each tick, and any damage one just took
 * is put straight back (the exact inverse of Heavy Handed). This is the safe choice: it modifies no item
 * component, so an abnormal end can never leave you with permanently-unbreakable gear (the same reasoning
 * Sticky uses for restoring armour rather than binding it), and since it heals every tick nothing ever
 * accumulates wear or reaches zero to break. Idle inventory items are ignored — they don't wear anyway.
 */
public final class BlessingWorkman extends Effect {
    private record Tracked(Item item, int damage) {}

    /** player -> last-seen (item, damage) per watched slot, so wear can be detected and healed straight back. */
    private static final Map<UUID, Map<EquipmentSlot, Tracked>> LAST = new HashMap<>();

    private static final EquipmentSlot[] WATCHED = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public BlessingWorkman() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.OBSIDIAN);
    }

    /** you find out the first time your gear shrugs off wear it should have taken (Rule 2). */
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
        Map<EquipmentSlot, Tracked> slots = LAST.computeIfAbsent(target.getUUID(), k -> new HashMap<>());

        for (EquipmentSlot slot : WATCHED) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                slots.remove(slot);
                continue;
            }
            Tracked previous = slots.get(slot);
            int damage = stack.getDamageValue();

            // same item as last tick and it took some wear? Heal it right back to where it was.
            if (previous != null && previous.item() == stack.getItem() && damage > previous.damage()) {
                stack.setDamageValue(previous.damage());
                damage = previous.damage();
                Blessings.WORKMAN.get().markDiscoveredByVictim(target); // discovered on the first wear healed
            }
            slots.put(slot, new Tracked(stack.getItem(), damage));
        }
    }
}
