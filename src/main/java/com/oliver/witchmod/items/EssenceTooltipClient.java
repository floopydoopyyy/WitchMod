package com.oliver.witchmod.items;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only helper for the Player Essence tooltip's online/offline line (reads the tab list). */
@OnlyIn(Dist.CLIENT)
final class EssenceTooltipClient {
    private EssenceTooltipClient() {}

    /** True/false if the bound player is/ isn't in the current server's player list; null if we can't tell. */
    static Boolean isOnline(UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        return connection.getPlayerInfo(id) != null;
    }
}
