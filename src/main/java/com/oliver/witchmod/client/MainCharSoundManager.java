package com.oliver.witchmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * Client driver for the Main Character theme. Every tick it looks over the players it can see (local + nearby
 * remote players, whose {@code MAINCHAR_TIER} is synced to trackers) and, for any that are powered up without a
 * loop already following them, spins one up. The instance stops itself and fades by distance, so onlookers
 * hear the protagonist's theme swell as they approach.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class MainCharSoundManager {
    /** powered-up player -> the loop following them, so we never double up. */
    private static final Map<UUID, MainCharSoundInstance> ACTIVE = new HashMap<>();

    private MainCharSoundManager() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.values().removeIf(MainCharSoundInstance::isStopped);

        for (Player player : mc.level.players()) {
            if (ACTIVE.containsKey(player.getUUID())) {
                continue;
            }
            if (MainCharSoundInstance.shouldPlay(player)) {
                MainCharSoundInstance instance = new MainCharSoundInstance(player);
                mc.getSoundManager().play(instance);
                ACTIVE.put(player.getUUID(), instance);
            }
        }
    }
}
