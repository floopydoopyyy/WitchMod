package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Speed Demon (client half): a ridden BOAT is client-authoritative (the controlling player simulates it and
 * sends its position), so a server-side speed boost wouldn't stick — this scales the boat's horizontal
 * velocity each client tick while the blessing is active, capped so it doesn't get silly. Living mounts
 * (server-driven) and minecarts (server-authoritative) are handled server-side in {@code BlessingSpeedDemon}.
 *
 * <p>The multiplier is a client constant matching the config default (2.0); the client can't read the server
 * config, and this is only a feel tweak.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class SpeedDemonClient {
    private static final double MULT = 2.0;
    private static final double CAP = 0.85; // ~2× a normal boat's ~0.4/tick top speed

    private SpeedDemonClient() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.getData(WitchModAttachments.SPEED_DEMON_ACTIVE) < 0) {
            return;
        }
        if (!(player.getVehicle() instanceof Boat boat)) {
            return;
        }
        Vec3 v = boat.getDeltaMovement();
        double horiz = Math.hypot(v.x, v.z);
        if (horiz < 0.02) {
            return; // not moving — nothing to boost
        }
        double nx = v.x * MULT;
        double nz = v.z * MULT;
        double nh = Math.hypot(nx, nz);
        if (nh > CAP) {
            double s = CAP / nh;
            nx *= s;
            nz *= s;
        }
        boat.setDeltaMovement(nx, v.y, nz);
        // A spray behind the speeding boat.
        var level = mc.level;
        if (level != null) {
            for (int i = 0; i < 3; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 0.8;
                double oz = (level.random.nextDouble() - 0.5) * 0.8;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        boat.getX() + ox, boat.getY() + 0.3, boat.getZ() + oz, -nx * 0.5, 0.05, -nz * 0.5);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                        boat.getX() + ox, boat.getY() + 0.2, boat.getZ() + oz, -nx, 0.1, -nz);
            }
        }
    }
}
