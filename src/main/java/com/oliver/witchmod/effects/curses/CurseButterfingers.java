package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;

/**
 * Whatever's in your hands has a habit of just... slipping (master-spec Butterfingers). Three ways to lose
 * your grip, all sharing ONE internal cooldown so a bad moment can't strip you bare:
 * <ul>
 *   <li><b>Passive</b> — rare, out of nowhere, for no reason at all.</li>
 *   <li><b>On taking damage</b> — much likelier; getting hit knocks it straight out of your hands.</li>
 *   <li><b>On swinging a tool</b> — the classic "threw my pickaxe into the lava" moment.</li>
 * </ul>
 *
 * <p>The shared cooldown is the whole balance of it: the passive roll is deliberately rare, so in practice
 * the curse gets you mid-fight or mid-swing — exactly when losing your gear hurts most.
 */
public final class CurseButterfingers extends Effect {
    public CurseButterfingers() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.MILK_BUCKET);
    }

    /** You find out the first time something leaps out of your hands (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        return dropSomething(target) ? "fumbled something out of your hands" : "nothing droppable in hand/hotbar";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (EffectUtil.every(ticksRemaining, Config.BUTTERFINGERS_PASSIVE_INTERVAL_TICKS.get())) {
            tryFumble(target, Config.BUTTERFINGERS_PASSIVE_CHANCE_PERCENT.get());
        }
    }

    /** Hook for taking a hit — see {@code CurseEventHandler}. */
    public static void onDamaged(ServerPlayer player) {
        tryFumble(player, Config.BUTTERFINGERS_ON_DAMAGE_CHANCE_PERCENT.get());
    }

    /** Hook for swinging a tool/weapon at a block or an entity — see {@code CurseEventHandler}. */
    public static void onToolSwing(ServerPlayer player) {
        tryFumble(player, Config.BUTTERFINGERS_ON_SWING_CHANCE_PERCENT.get());
    }

    /** Rolls for a fumble, respecting the one shared cooldown, and resets it only on an actual drop. */
    private static void tryFumble(ServerPlayer player, int chancePercent) {
        long now = player.serverLevel().getGameTime();
        if (now < player.getData(WitchModAttachments.BUTTERFINGERS_NEXT_ALLOWED)) {
            return;
        }
        if (player.getRandom().nextInt(100) >= chancePercent) {
            return;
        }
        if (!dropSomething(player)) {
            return; // nothing to lose — don't burn the cooldown on it
        }
        player.setData(WitchModAttachments.BUTTERFINGERS_NEXT_ALLOWED,
                now + Config.BUTTERFINGERS_COOLDOWN_TICKS.get());
        Curses.BUTTERFINGERS.value().markDiscoveredByVictim(player);
    }

    /**
     * Fumbles whatever is most embarrassing to lose: what you're holding first, then the offhand, then a
     * random hotbar slot if configured. @return whether anything was actually dropped.
     */
    private static boolean dropSomething(ServerPlayer player) {
        if (dropFromHand(player, InteractionHand.MAIN_HAND) || dropFromHand(player, InteractionHand.OFF_HAND)) {
            return true;
        }
        if (!Config.BUTTERFINGERS_DROP_FROM_HOTBAR.get()) {
            return false;
        }

        List<Integer> filled = new ArrayList<>();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
                filled.add(slot);
            }
        }
        if (filled.isEmpty()) {
            return false;
        }
        int slot = filled.get(player.getRandom().nextInt(filled.size()));
        return dropStack(player, player.getInventory().getItem(slot));
    }

    private static boolean dropFromHand(ServerPlayer player, InteractionHand hand) {
        return dropStack(player, player.getItemInHand(hand));
    }

    /** Splits the configured amount off {@code stack} (mutating the real inventory stack) and throws it. */
    private static boolean dropStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        int amount = Config.BUTTERFINGERS_DROPS_WHOLE_STACK.get() ? stack.getCount() : 1;
        player.drop(stack.split(amount), false);
        return true;
    }
}
