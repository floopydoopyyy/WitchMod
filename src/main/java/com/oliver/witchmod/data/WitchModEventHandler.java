package com.oliver.witchmod.data;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.oliver.witchmod.WitchMod;

@EventBusSubscriber(modid = WitchMod.MODID)
public final class WitchModEventHandler {
    private WitchModEventHandler() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EffectManager.tick(player);
            AfflictionManager.tick(player);
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GracePeriod.markFirstSeenIfAbsent(player);
        }
    }

    /**
     * Curses/blessings persist through death (master-spec Rule 4). Their {@code ACTIVE_EFFECTS} data is
     * {@code copyOnDeath} and their transient state self-heals in each effect's {@code onTick}, but DEATH
     * clears the vanilla Cursed/Blessed/Afflicted wrapper effects — so re-sync them on respawn, or the
     * victim loses their status icon (and any wrapper-driven feedback) even though the effects are still on.
     */
    @SubscribeEvent
    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatusEffectSync.sync(player);
        }
    }
}
