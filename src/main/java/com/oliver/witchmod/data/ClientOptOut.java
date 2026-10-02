package com.oliver.witchmod.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/**
 * per-uuid store of the effects a player has opted out of in their client config (reported by the c2s
 * ClientOptOutPayload on join / client-config reload). not persisted — cleared on logout, re-sent on join.
 * EffectManager.apply and BewitchingTableRitual.cast consult {@link #blocks} unless the server's
 * ignoreClientOptOuts rule is on.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class ClientOptOut {
    private ClientOptOut() {}

    private static final Map<UUID, Set<String>> OPT_OUTS = new ConcurrentHashMap<>();

    /** replaces whatever the player reported before. an empty list clears them. */
    public static void set(UUID player, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            OPT_OUTS.remove(player);
            return;
        }
        // cap the list — it's client-supplied, so don't let a modified client stuff the map.
        Set<String> clean = ConcurrentHashMap.newKeySet();
        for (String id : ids) {
            if (id != null && !id.isBlank() && id.length() <= 128 && clean.size() < 64) {
                clean.add(id);
            }
        }
        OPT_OUTS.put(player, clean);
    }

    public static List<String> get(UUID player) {
        Set<String> ids = OPT_OUTS.get(player);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    /** true if the target opted out of this effect and the server honours opt-outs. matched by full id OR path. */
    public static boolean blocks(ServerPlayer target, ResourceLocation effectId) {
        if (target == null || effectId == null || Config.ignoreClientOptOuts()) {
            return false;
        }
        Set<String> ids = OPT_OUTS.get(target.getUUID());
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return ids.contains(effectId.getPath()) || ids.contains(effectId.toString());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPT_OUTS.remove(event.getEntity().getUUID());
    }
}
