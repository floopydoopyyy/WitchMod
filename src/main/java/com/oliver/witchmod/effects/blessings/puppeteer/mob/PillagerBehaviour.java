package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;

/** puppeteer: the pillager puppet. */
public final class PillagerBehaviour implements PuppetBehaviour {
    public static final PillagerBehaviour INSTANCE = new PillagerBehaviour();

    private PillagerBehaviour() {}

    // --- pillager -----------------------------------------------------------------------------------

    /**
     * pillager: a real crossbow in every hotbar slot (unbreakable puppet tools) and a quiver that never runs dry — so
     * loading, firing and every crossbow blessing work exactly as normal. its bolts are boosted in onArrowSpawn.
     */
    private static void giveCrossbowKit(ServerPlayer player) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
        markPuppetTool(crossbow);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, crossbow.copy());
        }
        refillQuiver(player);
    }

    /** the pillager's bottomless quiver: a puppet-tool stack of arrows, topped back up as it's used. */
    private static void refillQuiver(ServerPlayer player) {
        ItemStack quiver = player.getInventory().getItem(9);
        if (quiver.is(Items.ARROW) && isPuppetTool(quiver) && quiver.getCount() >= 32) {
            return;
        }
        ItemStack arrows = new ItemStack(Items.ARROW, 64);
        markPuppetTool(arrows);
        player.getInventory().setItem(9, arrows);
        player.inventoryMenu.broadcastChanges();
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        refillQuiver(player);
    }

    /** real crossbows on the hotbar. */
    @Override
    public void giveKit(ServerPlayer player, PuppetType type) {
        giveCrossbowKit(player);
    }

    /** no melee: the crossbow's the weapon. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true;
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** its bolts hit a little harder — and nobody gets to keep them. */
    @Override
    public void arrowFired(ServerPlayer player, PuppetType type, Arrow arrow) {
        arrow.setBaseDamage(arrow.getBaseDamage() * Config.PUPPETEER_PILLAGER_BOLT_DAMAGE.get());
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
    }
}
