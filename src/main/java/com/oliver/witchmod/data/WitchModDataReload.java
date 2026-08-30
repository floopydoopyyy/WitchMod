package com.oliver.witchmod.data;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import com.oliver.witchmod.WitchMod;

/** Hooks the mod's datapack-backed text lists into the server resource reload (so {@code /reload} works). */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WitchModDataReload {
    private WitchModDataReload() {}

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new YapMessages());
        event.addListener(new EchoesChatMessages());
        event.addListener(new TaxManLines());
        event.addListener(new OversharerMessages());
        event.addListener(new InsomniacMessages());
        event.addListener(new BodyguardLines());
        event.addListener(new HypeManMessages());
        event.addListener(new Usernames());
        event.addListener(new TwitchChat());
        event.addListener(new SolicitorLines());
        event.addListener(new MarriageLines());
    }
}
