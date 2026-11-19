package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.effects.blessings.puppeteer.*;

/** puppeteer: the bat puppet — flies like the bat disguise, and can't attack at all. */
public final class BatBehaviour implements PuppetBehaviour {
    public static final BatBehaviour INSTANCE = new BatBehaviour();

    private BatBehaviour() {}

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
    }

    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true; // bats can't attack
    }
}
