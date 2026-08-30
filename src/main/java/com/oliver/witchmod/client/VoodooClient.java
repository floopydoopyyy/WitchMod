package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.items.ItemVoodooDoll;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * Voodoo Doll "shake" (client half): whipping your camera around while holding a bound doll tells the server to
 * slightly disorient the victim. Rate-limited so it's a gentle, occasional nudge, not a spam.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class VoodooClient {
    private static final float FAST_DEGREES = 22.0F; // total yaw+pitch change per tick to count as "shaking"
    private static float lastYaw;
    private static float lastPitch;
    private static int cooldown;

    private VoodooClient() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float delta = Math.abs(Mth.degreesDifference(lastYaw, yaw)) + Math.abs(pitch - lastPitch);
        lastYaw = yaw;
        lastPitch = pitch;

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (delta < FAST_DEGREES || !holdingBoundDoll(player)) {
            return;
        }
        cooldown = 8; // don't flood — a nudge a few times a second at most
        PacketDistributor.sendToServer(new WitchModNetwork.VoodooShakePayload());
    }

    private static boolean holdingBoundDoll(LocalPlayer player) {
        return isBoundDoll(player.getMainHandItem()) || isBoundDoll(player.getOffhandItem());
    }

    private static boolean isBoundDoll(ItemStack stack) {
        return stack.getItem() instanceof ItemVoodooDoll && stack.has(WitchModDataComponents.BOUND_PLAYER);
    }
}
