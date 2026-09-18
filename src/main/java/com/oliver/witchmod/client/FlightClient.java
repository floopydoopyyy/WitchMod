package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * blessing of Flight (client half): while it's active, holding JUMP pulls you up (client-authoritative
 * movement), draining the energy bar. We report the rising state to the server (which owns the energy +
 * the after-hit lockout) only when it changes.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class FlightClient {
    private static int lastMode;      // 0 none / 1 push-up / 2 sprint-glide, last reported to the server
    private static int risingTicks;   // for the ~1s acceleration buildup
    private static int jumpHeldTicks; // how long jump has been held (a tap = a normal jump, not flight)
    private static int fullIdleTicks;  // ticks at max energy without flying (bar auto-hides after 2s)

    private FlightClient() {}

    /** the bar hides once you've been at full charge and unused for ~2s. */
    public static boolean barHidden() {
        return fullIdleTicks > 40;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        int active = player.getData(WitchModAttachments.FLIGHT_ACTIVE);
        if (active < 1) { // -1/0 = blessing not active
            if (lastMode != 0) {
                PacketDistributor.sendToServer(new WitchModNetwork.FlightRisePayload(0));
                lastMode = 0;
            }
            risingTicks = 0;
            fullIdleTicks = 0;
            return;
        }
        long now = player.level().getGameTime();
        boolean locked = now < player.getData(WitchModAttachments.FLIGHT_LOCKOUT_END);
        float energy = player.getData(WitchModAttachments.FLIGHT_ENERGY);
        // jump must be HELD past a short threshold before flight engages — a quick tap is just a normal jump
        // (no flight, no drain), so you can still hop about and refill on the ground.
        if (mc.options.keyJump.isDown()) {
            jumpHeldTicks++;
        } else {
            jumpHeldTicks = 0;
        }
        // active == 2 is the depleted lockout: you can't rise until it refills (server flips it back to 1).
        boolean canFly = active == 1 && energy > 0.0F && !locked && !player.isSpectator();
        boolean jumpHeld = jumpHeldTicks >= Config.FLIGHT_HOLD_TICKS.get();
        boolean rising = canFly && jumpHeld;
        boolean elytra = player.isFallFlying() && canFly;
        boolean activeElytra = elytra && (jumpHeld || player.isSprinting());

        // auto-hide the bar after 2s at max charge with no flying.
        if (energy >= 1.0F && !rising && !activeElytra) {
            fullIdleTicks++;
        } else {
            fullIdleTicks = 0;
        }

        int mode = 0; // 0 none / 1 push-up / 2 sprint-glide
        double max = Config.FLIGHT_RISE_SPEED.get();
        int accel = Math.max(1, Config.FLIGHT_ACCEL_TICKS.get());
        if (elytra) {
            // ENHANCE the elytra rather than override it: hold jump for a SMALL extra lift (so you stop losing
            // height), hold sprint for a decent forward push. either counts as active flight (drains the bar);
            // just gliding with neither refuels it (mode 0). keeps the elytra's own momentum.
            Vec3 v = player.getDeltaMovement();
            Vec3 look = player.getLookAngle();
            boolean sprint = player.isSprinting();
            double lift = 0.0;
            if (jumpHeld) {
                risingTicks++;
                double frac = Math.min(1.0, risingTicks / (double) accel);
                lift = max * (0.22 + 0.78 * frac) * Config.FLIGHT_ELYTRA_LIFT_MULT.get();
            } else {
                risingTicks = 0;
            }
            double push = sprint ? Config.FLIGHT_ELYTRA_SPRINT_SPEED.get() : 0.0;
            if (lift != 0.0 || push != 0.0) {
                player.setDeltaMovement(v.x + look.x * push, v.y + lift, v.z + look.z * push);
            }
            mode = sprint ? 2 : (jumpHeld ? 1 : 0);
        } else if (rising) {
            risingTicks++;
            double frac = Math.min(1.0, risingTicks / (double) accel);
            double rise = max * (0.22 + 0.78 * frac); // slow start, ramping to full over ~1s
            Vec3 v = player.getDeltaMovement();
            boolean moveKey = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
            if (player.isSprinting() && moveKey) {
                // sprinting + a movement key trades upward rise for a fast forward GLIDE along your look direction.
                mode = 2;
                rise *= Config.FLIGHT_SPRINT_RISE_MULT.get();
                Vec3 look = player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0, look.z);
                if (flat.lengthSqr() > 1.0e-4) {
                    flat = flat.normalize();
                }
                double h = Config.FLIGHT_SPRINT_HORIZONTAL.get();
                player.setDeltaMovement(flat.x * h, rise, flat.z * h);
            } else {
                mode = 1;
                player.setDeltaMovement(v.x, rise, v.z);
            }
            player.resetFallDistance();
        } else {
            risingTicks = 0;
        }
        if (mode != lastMode) {
            PacketDistributor.sendToServer(new WitchModNetwork.FlightRisePayload(mode));
            lastMode = mode;
        }
    }
}
