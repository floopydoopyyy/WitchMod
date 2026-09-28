package com.oliver.witchmod.loot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.items.ItemJar;
import com.oliver.witchmod.items.JarContents;

/** collecting any jar instantly discovers everything inside it — an easy route to filling out the compendium. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class JarCollectHandler {
    private JarCollectHandler() {}

    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getOriginalStack();
        if (!(stack.getItem() instanceof ItemJar)) {
            return;
        }
        for (CapturedEffect captured : JarContents.contents(stack)) {
            DiscoveryManager.markEffectDiscovered(player, captured.effectId());
        }
    }
}
