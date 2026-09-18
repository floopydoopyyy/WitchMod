package com.oliver.witchmod.data;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import com.oliver.witchmod.WitchMod;

/** hooks the mod's datapack text lists into the server resource reload, so {@code /reload} picks them up. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WitchModDataReload {
    private WitchModDataReload() {}

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        // power-levels lives in config/, not a datapack — piggy-back on /reload to re-read it
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager rm, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager rm, ProfilerFiller profiler) {
                PowerLevels.load();
            }
        });
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
        event.addListener(new CompanionshipLines());
        event.addListener(new MarriageLines());
        event.addListener(new NarratorLines());
    }
}
