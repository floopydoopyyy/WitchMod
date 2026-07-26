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
 * Client-side driver for the Trumpet curse's music. Every client tick it looks over the players it can see
 * (the local player and every remote player within tracking range — the ones whose position it actually
 * knows) and, for any that are cursed AND walking AND not crouched, makes sure a {@link TrumpetSoundInstance}
 * is playing at them. The instance stops ITSELF the moment the conditions lapse; this only handles (re)starting
 * one, so a player who stops and moves again gets a clean loop from the top.
 *
 * <p>Because the {@code TRUMPET_ACTIVE} flag is synced to trackers, every nearby client runs this for the same
 * cursed player — which is what makes the trumpet give their position away to everyone in earshot, not just
 * to the victim.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class TrumpetSoundManager {
    /** cursed player -> the loop currently following them, so we never double up. */
    private static final Map<UUID, TrumpetSoundInstance> ACTIVE = new HashMap<>();

    private TrumpetSoundManager() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }

        // Drop finished loops (a player stopped, crouched, cured, or went out of range) so they can restart.
        ACTIVE.values().removeIf(TrumpetSoundInstance::isStopped);

        for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            if (ACTIVE.containsKey(id)) {
                continue; // already sounding — the instance manages its own stop
            }
            if (TrumpetSoundInstance.shouldPlay(player)) {
                TrumpetSoundInstance instance = new TrumpetSoundInstance(player);
                mc.getSoundManager().play(instance);
                ACTIVE.put(id, instance);
            }
        }
    }
}
