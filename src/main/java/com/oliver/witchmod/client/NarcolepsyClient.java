package com.oliver.witchmod.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * Narcolepsy (client half): while the synced {@link WitchModAttachments#NARCOLEPSY_SLEEP_END} is in the
 * future you're asleep — ALL input is dead, the screen goes almost black, and a "MASH TO WAKE" bar shows how
 * close you are to fighting free. The bar constantly drains ({@code DECAY}), so you have to hammer the
 * movement keys faster than it falls — a little struggle each time. Reaching full sends
 * {@link WitchModNetwork.NarcolepsyWakePayload} and the server ends the sleep early.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class NarcolepsyClient {
    private static final float MASH_PER_PRESS = 0.135F;
    private static final float DECAY_PER_TICK = 0.026F; // the constant drain that makes waking a battle

    private static float wakeProgress;
    private static boolean wasSleeping;
    private static boolean sentWake;
    private static boolean localPosed; // debug third-person: whether we've forced the LOCAL player to lie down
    private static float pinnedYaw;
    private static float pinnedPitch;
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.client.CameraType savedCamera; // camera to restore on waking
    private static float lastHealth; // to detect a hit landing while asleep (fills the bar)

    /** Raw depth attachment: low digit = tier (0/1/2), +10 = debug third-person sleep. */
    private static int sleepDepthRaw() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.getData(WitchModAttachments.NARCOLEPSY_DEPTH);
    }

    /** Sleep depth tier (0 normal / 1 deep / 2 very deep) — deeper needs more mashing. */
    private static int depthTier() {
        return sleepDepthRaw() % 10;
    }

    /** Debug "watch in third person" sleep. */
    private static boolean thirdPersonDebug() {
        return sleepDepthRaw() >= 10;
    }
    // Previous down-state of each mashable key, to detect fresh presses (up -> down).
    private static boolean pUp, pDown, pLeft, pRight, pJump, pAttack, pUse;

    private NarcolepsyClient() {}

    /** True while the local player is in a narcoleptic sleep. */
    public static boolean isSleeping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        long end = mc.player.getData(WitchModAttachments.NARCOLEPSY_SLEEP_END);
        return end > mc.level.getGameTime();
    }

    /** UUIDs of OTHER players we've faked into the sleeping pose, so we know which to restore. */
    private static final java.util.Set<java.util.UUID> FAKED = new java.util.HashSet<>();

    /**
     * Render OTHER narcoleptic players lying down — purely client-side (the server no longer sleeps them, so
     * their physics stay normal). We set the SLEEPING pose + sleeping-pos on their RemotePlayer; the server
     * keeps them standing and never sends a pose change, so our fake holds until they wake.
     */
    private static void renderOtherSleepers(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (p == mc.player) {
                continue;
            }
            boolean sl = p.getData(WitchModAttachments.NARCOLEPSY_SLEEP_END) > now;
            if (sl) {
                p.setSleepingPos(p.blockPosition());
                if (p.getPose() != net.minecraft.world.entity.Pose.SLEEPING) {
                    p.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                }
                FAKED.add(p.getUUID());
            } else if (FAKED.remove(p.getUUID())) {
                p.clearSleepingPos();
                if (p.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
                    p.setPose(net.minecraft.world.entity.Pose.STANDING);
                }
            }
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        renderOtherSleepers(mc); // lay out any OTHER narcoleptic players, regardless of our own state
        boolean sleeping = isSleeping();

        if (sleeping && !wasSleeping) {
            // Just nodded off — reset the struggle, pin where we were looking, and drop into first person ONCE.
            wakeProgress = 0F;
            sentWake = false;
            pinnedYaw = player.getYRot();
            pinnedPitch = player.getXRot();
            lastHealth = player.getHealth();
            savedCamera = mc.options.getCameraType();
            // Normally first person (steady view); the debug third-person sleep lets you watch your own lie-down.
            mc.options.setCameraType(thirdPersonDebug()
                    ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                    : net.minecraft.client.CameraType.FIRST_PERSON);
        }
        if (!sleeping && wasSleeping) {
            // Just woke — restore the camera the player had before, and drop any debug lie-down pose.
            if (savedCamera != null) {
                mc.options.setCameraType(savedCamera);
                savedCamera = null;
            }
            if (localPosed) {
                player.clearSleepingPos();
                if (player.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
                    player.setPose(net.minecraft.world.entity.Pose.STANDING);
                }
                localPosed = false;
            }
        }
        wasSleeping = sleeping;

        if (!sleeping) {
            wakeProgress = 0F;
            return;
        }

        // Freeze the look direction (so nothing banks up while asleep). The lying-down POSE is set server-side
        // only — forcing it on the local model too was what battled the server and made the camera glitchy.
        player.setYRot(pinnedYaw);
        player.setXRot(pinnedPitch);
        player.yRotO = pinnedYaw;
        player.xRotO = pinnedPitch;
        player.setYHeadRot(pinnedYaw);

        // Debug third-person: force the LOCAL player to lie down too, re-asserted each tick (vanilla's own
        // updatePlayerPose keeps SLEEPING while a sleeping-pos is set), so you can watch the animation yourself.
        if (thirdPersonDebug()) {
            player.setSleepingPos(player.blockPosition());
            localPosed = true;
        }

        // Taking a hit jolts you toward waking — fill 40% of the bar per point of health lost this tick.
        float hp = player.getHealth();
        if (hp < lastHealth) {
            wakeProgress += 0.4F;
        }
        lastHealth = hp;

        // Count fresh presses of any movement key OR attack/use (left/right click) as mashing.
        int presses = 0;
        presses += freshPress(mc.options.keyUp, pUp) ? 1 : 0;
        presses += freshPress(mc.options.keyDown, pDown) ? 1 : 0;
        presses += freshPress(mc.options.keyLeft, pLeft) ? 1 : 0;
        presses += freshPress(mc.options.keyRight, pRight) ? 1 : 0;
        presses += freshPress(mc.options.keyJump, pJump) ? 1 : 0;
        presses += freshPress(mc.options.keyAttack, pAttack) ? 1 : 0;
        presses += freshPress(mc.options.keyUse, pUse) ? 1 : 0;
        pUp = mc.options.keyUp.isDown();
        pDown = mc.options.keyDown.isDown();
        pLeft = mc.options.keyLeft.isDown();
        pRight = mc.options.keyRight.isDown();
        pJump = mc.options.keyJump.isDown();
        pAttack = mc.options.keyAttack.isDown();
        pUse = mc.options.keyUse.isDown();

        // Deeper sleeps need MORE mashing: each press gives less, and the drain is a touch stronger.
        int tier = depthTier();
        float pressScale = tier == 2 ? 0.42F : (tier == 1 ? 0.62F : 1.0F);
        float decayScale = tier == 2 ? 1.30F : (tier == 1 ? 1.15F : 1.0F);
        wakeProgress += presses * MASH_PER_PRESS * pressScale;
        wakeProgress -= DECAY_PER_TICK * decayScale;
        wakeProgress = Math.max(0F, Math.min(1F, wakeProgress));

        if (wakeProgress >= 1F && !sentWake) {
            sentWake = true;
            PacketDistributor.sendToServer(new WitchModNetwork.NarcolepsyWakePayload());
        }
    }

    private static boolean freshPress(KeyMapping key, boolean prevDown) {
        return key.isDown() && !prevDown;
    }

    /** Kill all movement input while asleep. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (isSleeping()) {
            event.getInput().forwardImpulse = 0F;
            event.getInput().leftImpulse = 0F;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
        }
    }

    /** Block attack / use / pick while asleep. */
    @SubscribeEvent
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (isSleeping()) {
            event.setCanceled(true);
        }
    }

    /** Pin the rendered view so the camera doesn't drift between ticks. */
    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (isSleeping()) {
            event.setYaw(pinnedYaw);
            event.setPitch(pinnedPitch);
        }
    }

    /** The near-black "asleep" wash + the drain-to-lose MASH bar. */
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isSleeping()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();

        // Vignette wash: lighter in the middle (you can just make out your surroundings), darker toward the
        // edges. Approximated with concentric frames whose darkness ramps up outward.
        int tier = depthTier();
        int baseAlpha = tier == 2 ? 0x55 : (tier == 1 ? 0x44 : 0x33); // deeper sleeps darken the wash
        g.fill(0, 0, w, h, baseAlpha << 24); // faint base so the centre isn't fully clear
        int step = Math.max(2, Math.min(w, h) / 44);
        int rings = Math.min(w, h) / 2 / step;
        for (int i = 0; i < rings; i++) {
            int inset = i * step;
            double t = 1.0 - (i / (double) rings); // 1 at the edge, 0 at the centre
            int a = (int) (0xC0 * t * t);
            if (a <= 2) {
                continue;
            }
            int col = a << 24;
            g.fill(inset, inset, w - inset, inset + step, col);                 // top
            g.fill(inset, h - inset - step, w - inset, h - inset, col);         // bottom
            g.fill(inset, inset + step, inset + step, h - inset - step, col);   // left
            g.fill(w - inset - step, inset + step, w - inset, h - inset - step, col); // right
        }

        // "MASH TO WAKE" prompt.
        Minecraft mc = Minecraft.getInstance();
        // Translatable so the text on each screen can be edited in the lang file.
        String key = tier == 2 ? "witchmod.narcolepsy.mash_very_deep"
                : (tier == 1 ? "witchmod.narcolepsy.mash_deep" : "witchmod.narcolepsy.mash");
        net.minecraft.network.chat.Component prompt = net.minecraft.network.chat.Component.translatable(key);
        int tw = mc.font.width(prompt);
        g.drawString(mc.font, prompt, w / 2 - tw / 2, h / 2 - 24, 0xFFDDDDDD, true);

        // The struggle bar.
        int barW = 180;
        int barH = 12;
        int bx = w / 2 - barW / 2;
        int by = h / 2 - 6;
        g.fill(bx - 2, by - 2, bx + barW + 2, by + barH + 2, 0xFF101010);
        g.fill(bx, by, bx + barW, by + barH, 0xFF2A2A2A);
        int fill = (int) (barW * wakeProgress);
        // Colour shifts from red (far) to green (nearly free).
        int colour = wakeProgress > 0.66F ? 0xFF66E060 : (wakeProgress > 0.33F ? 0xFFE0C040 : 0xFFE05040);
        g.fill(bx, by, bx + fill, by + barH, colour);
    }
}
