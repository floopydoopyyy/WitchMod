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
 * client-side driver for the Trumpet curse's music. Every client tick it looks over the players it can see
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

    // real-displacement tracker — an EXTRA gate so the loop self-corrects even when walkAnimation.speed() is
    // wrong (it can get stuck non-zero — pushed, in a current, animation state not decaying — which made the
    // trumpet loop CONSTANTLY, not just while walking). We measure actual movement per tick and treat a player
    // as "moving" only if they've genuinely displaced recently.
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
    private static final double MOVE_EPSILON = 0.02;  // blocks/tick horizontal below which you count as still
    private static final int STILL_LIMIT = 4;         // ticks of no real movement before the loop is cut

    private TrumpetSoundManager() {}

    /** true only if the player has actually MOVED within the last few ticks (independent of walkAnimation). */
    static boolean isMoving(Player player) {
        return STILL_TICKS.getOrDefault(player.getUUID(), 99) <= STILL_LIMIT;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            LAST_POS.clear();
            STILL_TICKS.clear();
            return;
        }

        // update the real-movement tracker for every player we can see this tick.
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            seen.add(id);
            double[] last = LAST_POS.get(id);
            double dx = last == null ? 0 : player.getX() - last[0];
            double dz = last == null ? 0 : player.getZ() - last[1];
            boolean moved = last != null && (dx * dx + dz * dz) > MOVE_EPSILON * MOVE_EPSILON;
            STILL_TICKS.put(id, moved ? 0 : STILL_TICKS.getOrDefault(id, 99) + 1);
            LAST_POS.put(id, new double[]{player.getX(), player.getZ()});
        }
        LAST_POS.keySet().removeIf(id -> !seen.contains(id));
        STILL_TICKS.keySet().removeIf(id -> !seen.contains(id));

        // drop finished loops (a player stopped, crouched, cured, or went out of range) so they can restart.
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
