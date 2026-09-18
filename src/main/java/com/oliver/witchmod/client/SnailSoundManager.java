package com.oliver.witchmod.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.SnailEntity;

/**
 * client driver for the Snail's music: every tick it looks for Snail entities within the music distance of the
 * local player and makes sure a looping {@link SnailSoundInstance} is playing at each. The instance stops
 * ITSELF the moment the snail leaves range or dies; this only (re)starts one, so the loop begins cleanly.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class SnailSoundManager {
    /** snail entity id -> its loop, so we never double up. */
    private static final Map<Integer, SnailSoundInstance> ACTIVE = new HashMap<>();

    private SnailSoundManager() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.entrySet().removeIf(e -> e.getValue().isStopped());

        double r = Config.SNAIL_MUSIC_DISTANCE.get();
        AABB box = mc.player.getBoundingBox().inflate(r);
        for (SnailEntity snail : mc.level.getEntitiesOfClass(SnailEntity.class, box)) {
            if (SnailSoundInstance.shouldPlay(snail) && !ACTIVE.containsKey(snail.getId())) {
                SnailSoundInstance instance = new SnailSoundInstance(snail);
                ACTIVE.put(snail.getId(), instance);
                mc.getSoundManager().play(instance);
            }
        }
    }
}
