package com.oliver.witchmod.data;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.oliver.witchmod.WitchMod;

/** common server hooks: ticks effects each player tick, records grace-period first-join, re-syncs wrappers on respawn. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WitchModEventHandler {
    private WitchModEventHandler() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffectManager.tick(player);
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GracePeriod.markFirstSeenIfAbsent(player);
        }
    }

    // effects persist through death (copy-on-death), but death clears the vanilla wrapper effects — re-sync
    // them on respawn or the victim loses the status icon while still cursed
    @SubscribeEvent
    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatusEffectSync.sync(player);
        }
    }
}
