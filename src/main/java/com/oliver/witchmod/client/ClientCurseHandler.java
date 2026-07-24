package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * The client-side curses (Phase D), driven off auto-synced flags on the local player:
 * <ul>
 *   <li><b>Moonwalker</b> — reverses forward/back movement input.</li>
 *   <li><b>Screensaver</b> — episodic OS window shrink/bounce/grow; see {@link ScreensaverState}. It drops
 *       out of fullscreen only for the length of an episode and restores it afterwards.</li>
 *   <li><b>Minor Inconvenience</b> — refuses fullscreen and MAXIMISES instead (the renamed title bar is
 *       invisible in fullscreen, but a small window would be a real handicap), then renames the window from
 *       a writable list; see {@link WindowTitles}.</li>
 *   <li><b>Pacing</b> — a One Piece-style time-stop: hijacks the camera into third-person and cuts between
 *       angles around the frozen victim, then to the faces of nearby entities.</li>
 * </ul>
 *
 * <p>The camera hijack uses only public API — forced third-person + {@link Minecraft#setCameraEntity} +
 * the {@code ComputeCameraAngles} yaw/pitch. (True free-camera positioning isn't possible via events:
 * {@code Camera.setup} overwrites the camera position after the event; that would need a mixin — so cuts
 * orbit the camera-entity rather than flying to arbitrary points.)
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class ClientCurseHandler {
    /** Social Outcast: how close a sound must start to a hidden entity to be treated as theirs. */
    private static final double OUTCAST_SOUND_MATCH_RADIUS = 2.0;
    /** Reused rather than allocated per frame — this runs every render tick while a shake is active. */
    private static final java.util.Random SHAKE_RNG = new java.util.Random();

    private static int nextRenameTick;

    private static int tickCounter;
    private static boolean screensaverSeen;

    private static boolean minorSeen;
    private static boolean wasInLiquid; // Bad Swimmer: tracks the surface-break for the entry plunge
    private static float loadingLockedYaw;   // Loading Screen: the view is frozen here for the duration
    private static float loadingLockedPitch;

    // Pacing camera state. The camera entity is always the VICTIM (a player), so it never becomes a mob and
    // never inherits a disorienting spectator shader (spider vision, creeper tint, ...). Shots are precomputed
    // absolute (yaw, pitch) framings the camera cuts between.
    private static boolean pacingCamActive;
    private static CameraType savedCameraType;
    private static Entity pacingFocus;
    private static Entity[] shotEntity = new Entity[0];   // who the camera rides for this shot
    private static boolean[] shotFront = new boolean[0];  // front view (see their face) vs back
    private static float[] shotYaw = new float[0];        // NaN = don't override (let vanilla frame it)
    private static float[] shotPitch = new float[0];
    private static float[] shotRoll = new float[0];       // a dutch-angle tilt, for drama
    private static int currentShot = -1;
    private static int pacingTotalTicks = 1;
    private static SoundInstance pacingTheme;

    private ClientCurseHandler() {}

    /**
     * Loading Screen: kill every interaction key (attack, use, pick block) while the fake load is up. Menus
     * are deliberately left alone — those aren't routed through this event.
     */
    @SubscribeEvent
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        // Loading Screen and Pacing both kill every interaction key (attack, use, pick block). Menus are
        // deliberately left alone — those aren't routed through this event.
        if (LoadingScreenState.isActive() || pacingCamActive) {
            event.setCanceled(true);
            return;
        }

        // Delusions: swinging at one pops it. Cancelled so the swing doesn't ALSO carry on into whatever's
        // behind it — the arm still swings, so it reads as a hit that connected with something.
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && DelusionManager.onAttack(player)) {
            event.setSwingHand(true);
            event.setCanceled(true);
        }
    }

    /**
     * Social Outcast: other players and villagers simply aren't drawn — no model, no nametag — unless
     * they're close enough to touch or have hit you recently.
     *
     * <p>Cancelling {@code RenderLivingEvent.Pre} returns before {@code LivingEntityRenderer.render} reaches
     * its {@code super.render} call, which is what draws the nametag — so one cancel hides both. Nothing
     * about the world changes: they're still there, still solid, still able to kill you.
     */
    @SubscribeEvent
    static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE) < 0) {
            return;
        }
        if (isOutcastHidden(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Social Outcast: hidden entities are silenced as well as invisible. Hearing footsteps and villager
     * mumbling from thin air would give the whole thing away instantly — worse, it would tell you exactly
     * where the person you can't see is standing, which is the opposite of the intended effect.
     *
     * <p>A {@code SoundInstance} carries a position but not who made it, so this matches on ORIGIN: any
     * sound starting within a couple of blocks of a currently-hidden player or villager is dropped. That's
     * approximate by nature, and deliberately so — a sound coming from exactly where an invisible person is
     * standing should be suppressed whatever produced it.
     */
    @SubscribeEvent
    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (sound == null || player == null || mc.level == null
                || player.getData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE) < 0) {
            return;
        }
        // Only categories entities actually emit — block and ambient sounds are the world, not a person.
        SoundSource source = sound.getSource();
        if (source != SoundSource.PLAYERS && source != SoundSource.NEUTRAL && source != SoundSource.VOICE) {
            return;
        }
        Vec3 origin = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        AABB around = new AABB(origin, origin).inflate(OUTCAST_SOUND_MATCH_RADIUS);
        for (LivingEntity nearby : mc.level.getEntitiesOfClass(LivingEntity.class, around)) {
            if (isOutcastHidden(player, nearby)) {
                event.setSound(null);
                return;
            }
        }
    }

    /** Shared by the render and audio hooks, so the two can never disagree about who is hidden. */
    private static boolean isOutcastHidden(LocalPlayer player, LivingEntity entity) {
        if (entity == player) {
            return false; // you can always see and hear yourself
        }
        boolean hideable = entity instanceof Player
                || (entity instanceof Villager && Config.OUTCAST_HIDES_VILLAGERS.get());
        if (!hideable) {
            return false;
        }
        double reveal = Config.OUTCAST_REVEAL_DISTANCE.get();
        if (entity.distanceToSqr(player) <= reveal * reveal) {
            return false; // close enough to touch
        }
        return !player.getData(WitchModAttachments.SOCIAL_OUTCAST_REVEALED).contains(entity.getId());
    }

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        Input input = event.getInput();

        // Loading Screen / Pacing: you are not playing right now. Movement, jumping and sneaking all go dead.
        if (LoadingScreenState.isActive() || pacingCamActive) {
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
            return;
        }

        // Backseat Driver: the AI has the wheel. Your own steering is cut dead and replaced with its
        // heading — a ridden mount faces wherever the RIDER faces and throttles on the rider's forward
        // input, so turning the rider IS the steering, and the animal walks there under its own power.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null
                && mc.level.getGameTime() < event.getEntity().getData(WitchModAttachments.BACKSEAT_EPISODE_END)) {
            float wanted = event.getEntity().getData(WitchModAttachments.BACKSEAT_DRIVE_YAW);
            // Ease onto the heading rather than snapping, so it reads as the mount turning, not a teleport.
            float turned = mc.player.getYRot() + Mth.clamp(Mth.degreesDifference(mc.player.getYRot(), wanted), -8.0F, 8.0F);
            mc.player.setYRot(turned);
            mc.player.yHeadRot = turned;
            mc.player.yBodyRot = turned;

            input.leftImpulse = 0.0F;
            input.forwardImpulse = 1.0F; // full throttle — the mount does the actual walking
            input.up = true;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            return;
        }

        // Siren's Call: at full longing the water takes the wheel. Same hijack as Backseat Driver — ease the
        // heading toward the synced water bearing and force forward, so you march to the sea against your own
        // input. Sitting below Backseat's block so a mounted victim's mount steering still wins.
        if (mc.player != null && event.getEntity().getData(WitchModAttachments.SIREN_PULL_ACTIVE) >= 0) {
            float wanted = event.getEntity().getData(WitchModAttachments.SIREN_PULL_YAW);
            float turned = mc.player.getYRot()
                    + Mth.clamp(Mth.degreesDifference(mc.player.getYRot(), wanted), -8.0F, 8.0F);
            mc.player.setYRot(turned);
            mc.player.yBodyRot = turned;
            input.leftImpulse = 0.0F;
            input.forwardImpulse = 1.0F;
            input.up = true;
            input.down = false;
            input.left = false;
            input.right = false;
            return;
        }

        // Gluttony: no sprinting once the COMBINED bar is at/below the cutoff. This has to be done here,
        // client-side, and by suppressing the sprint KEY: sprinting is decided in LocalPlayer.aiStep, so a
        // server-side setSprinting(false) is overwritten immediately, and merely clearing the flag here just
        // lets the very next line of aiStep turn it straight back on.
        // Sticky: suppress the drop key outright, so the stack never leaves its slot in the first place.
        // This is the PRIMARY mechanism, not a nicety — the server-side ItemTossEvent cancel has to hand the
        // item back by hand (cancelling alone deletes it), and never letting it leave is far cleaner than
        // pulling it out and pushing it back in, which can land it in a different slot.
        if (mc.player != null && mc.player.getData(WitchModAttachments.STICKY_ACTIVE) >= 0) {
            mc.options.keyDrop.setDown(false);
        }

        // Thirst Meter cuts sprinting off the same way, and for the same client-authoritative reason.
        if (mc.player != null && (isGluttonySprintBlocked(mc.player) || isThirstSprintBlocked(mc.player))) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }

        // Moonwalker: W does nothing. Only FORWARD is blocked — back, left and right all work normally, which
        // is what makes it a shuffling-backwards curse rather than a full input scramble.
        if (event.getEntity().getData(WitchModAttachments.MOONWALKER_ACTIVE) >= 0 && input.forwardImpulse > 0.0F) {
            input.forwardImpulse = 0.0F;
            input.up = false;
        }

        // Wonky: a subtle sideways wander you can't quite hold a line against — but only while you're
        // actually trying to move, and worse when you sprint. A slow sine gives a lazy weave rather than
        // jitter; it's added to the strafe, so it never affects standing still or facing.
        if (mc.player != null && mc.player.getData(WitchModAttachments.WONKY_ACTIVE) >= 0
                && (Math.abs(input.forwardImpulse) > 0.0F || Math.abs(input.leftImpulse) > 0.0F)) {
            // Per-tick phase — this event fires once a tick, which is plenty for a lazy weave.
            double phase = mc.level.getGameTime() * (2.0 * Math.PI / Config.WONKY_PERIOD_TICKS.get());
            double strength = Config.WONKY_DRIFT_STRENGTH.get();
            if (mc.player.isSprinting()) {
                strength *= Config.WONKY_SPRINT_MULTIPLIER.get();
            }
            input.leftImpulse += (float) (Math.sin(phase) * strength);
        }

        // Stick Drift (MOVEMENT mode): a phantom stick input in the fixed drifted direction, always the same
        // way, at the current episode's intensity. Unlike Wonky this is constant while it runs — the stick is
        // stuck, not weaving.
        if (mc.player != null && mc.player.getData(WitchModAttachments.STICK_DRIFT_MODE) == 1) {
            float intensity = stickDriftIntensity(mc.player, mc.level.getGameTime());
            if (intensity > 0.0F) {
                double angle = mc.player.getData(WitchModAttachments.STICK_DRIFT_ANGLE);
                double push = intensity * Config.STICKDRIFT_MOVE_SCALE.get();
                input.leftImpulse += (float) (Math.cos(angle) * push);
                input.forwardImpulse += (float) (Math.sin(angle) * push);
            }
        }
    }

    /** Current Stick Drift episode intensity for this player, or 0 when between episodes. */
    private static float stickDriftIntensity(LocalPlayer player, long now) {
        if (now >= player.getData(WitchModAttachments.STICK_DRIFT_END)) {
            return 0.0F;
        }
        return player.getData(WitchModAttachments.STICK_DRIFT_INTENSITY);
    }

    /**
     * Stick Drift (CAMERA mode): rotates your ACTUAL aim a fixed way each tick — unlike a shake this really
     * moves where you're pointing, exactly like a drifting right stick, so it has to write the player's real
     * yaw/pitch rather than just the render view.
     */
    private static void tickStickDriftCamera(LocalPlayer player) {
        if (player.getData(WitchModAttachments.STICK_DRIFT_MODE) != 0) {
            return;
        }
        float intensity = stickDriftIntensity(player, player.level().getGameTime());
        if (intensity <= 0.0F) {
            return;
        }
        double angle = player.getData(WitchModAttachments.STICK_DRIFT_ANGLE);
        double deg = intensity * Config.STICKDRIFT_CAMERA_SCALE.get();
        player.setYRot(player.getYRot() + (float) (Math.cos(angle) * deg));
        player.setXRot(Mth.clamp(player.getXRot() + (float) (Math.sin(angle) * deg), -90.0F, 90.0F));
    }

    /** True while the thirst bar has run low enough to cut sprinting off. */
    private static boolean isThirstSprintBlocked(LocalPlayer player) {
        int thirst = player.getData(WitchModAttachments.THIRST);
        return thirst >= 0 && thirst <= Config.THIRST_SPRINT_CUTOFF.get();
    }

    /** True while Gluttony's combined bar has run low enough to cut sprinting off. */
    private static boolean isGluttonySprintBlocked(LocalPlayer player) {
        int extra = player.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra < 0) {
            return false; // curse not active
        }
        return player.getFoodData().getFoodLevel() + extra <= Config.GLUTTONY_SPRINT_CUTOFF.get();
    }

    /**
     * Gluttony draws BOTH hunger rows itself, so vanilla's own food bar is suppressed while it's active.
     * Otherwise the lower row shows the real vanilla value, which is deliberately held one point short of
     * full to keep eating possible — so the bar permanently looked like it was missing its first point.
     * {@link GluttonyHudLayer} redraws that row from the true combined total instead.
     */
    @SubscribeEvent
    static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())
                && GluttonyHudLayer.shouldDrawCombinedBars(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();

        // Loading Screen: hold the view perfectly still. The tick handler also pins the real rotation, but
        // mouse-look is applied per FRAME, so without this the camera would visibly jitter between ticks.
        if (LoadingScreenState.isActive()) {
            event.setYaw(loadingLockedYaw);
            event.setPitch(loadingLockedPitch);
            return;
        }

        if (!pacingCamActive || currentShot < 0 || currentShot >= shotYaw.length) {
            applyHeavyweightShake(mc, event);
            return;
        }
        // Orbit shots pin an absolute yaw/pitch so the mouse can't nudge them; face shots leave yaw/pitch to
        // vanilla (which frames the ridden entity's face straight-on). Both get a dutch-angle roll for drama.
        if (!Float.isNaN(shotYaw[currentShot])) {
            event.setYaw(shotYaw[currentShot]);
            event.setPitch(shotPitch[currentShot]);
        }
        event.setRoll(shotRoll[currentShot]);
    }

    /**
     * Heavyweight: rattles the view when the floor gives way. Vanilla has no screen shake at all, so this is
     * hand-rolled — a random jolt on all three axes, decaying to nothing across the window so it lands as one
     * impact rather than a wobble that outstays its welcome.
     *
     * <p>Applied to the camera ANGLES rather than the player's real rotation, so it never actually changes
     * where you're aiming — it only looks like it did.
     */
    private static void applyHeavyweightShake(Minecraft mc, ViewportEvent.ComputeCameraAngles event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        // Heavyweight's own collapse jolt...
        applyShake(event, now, mc.player.getData(WitchModAttachments.HEAVYWEIGHT_SHAKE_END),
                Config.HEAVYWEIGHT_SHAKE_TICKS.get(), Config.HEAVYWEIGHT_SHAKE_STRENGTH.get());
        // ...and Flat Footed's footstep shudder from a loud player nearby. Both stack additively.
        applyShake(event, now, mc.player.getData(WitchModAttachments.FLAT_FOOTED_SHAKE_END),
                Config.FLATFOOT_SHAKE_TICKS.get(), Config.FLATFOOT_SHAKE_STRENGTH.get());
    }

    /** One decaying random jolt on the camera ANGLES (never the real rotation), shared by both shake sources. */
    private static void applyShake(ViewportEvent.ComputeCameraAngles event, long now, long end,
                                   int windowTicks, double peakStrength) {
        if (now >= end) {
            return;
        }
        int window = Math.max(1, windowTicks);
        double decay = Math.min(1.0, (end - now) / (double) window);
        double strength = peakStrength * decay * decay;
        event.setYaw((float) (event.getYaw() + (SHAKE_RNG.nextDouble() - 0.5) * 2.0 * strength));
        event.setPitch((float) (event.getPitch() + (SHAKE_RNG.nextDouble() - 0.5) * 2.0 * strength));
        event.setRoll((float) (event.getRoll() + (SHAKE_RNG.nextDouble() - 0.5) * 2.0 * strength));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        tickCounter++;
        Window window = mc.getWindow();

        // Delusions: spawn/retire the fake players and run the "am I being watched" timer.
        DelusionManager.clientTick(mc);

        // Ugly: re-assert the swapped skin on every cursed player in view (and put faces back when cured).
        UglySkinManager.clientTick(mc);

        boolean minorActive = player.getData(WitchModAttachments.MINOR_INCONVENIENCE_ACTIVE) >= 0;
        boolean screensaverActive = player.getData(WitchModAttachments.SCREENSAVER_ACTIVE) >= 0;

        tickMinorInconvenience(mc, window, player, minorActive);

        // Screensaver — episodic: shrink out of fullscreen, bounce for a while, grow back. See
        // ScreensaverState for the duty cycle and why it isn't continuous.
        if (screensaverActive) {
            ScreensaverState.tick(mc, player.getRandom());
            screensaverSeen = true;
        } else if (screensaverSeen) {
            ScreensaverState.reset(mc);   // curse ended mid-episode — put their window back
            screensaverSeen = false;
        }

        tickLoadingScreen(player);
        tickBadSwimmer(player);
        tickHeavyAnchor(player);
        tickStickDriftCamera(player);
        tickPacingCamera(mc, player);
    }

    /**
     * Drives the fake load and pins the player's actual rotation, so mouse movement during it is discarded
     * rather than banked up and applied the moment the screen clears.
     */
    private static void tickLoadingScreen(LocalPlayer player) {
        boolean wasActive = LoadingScreenState.isActive();
        LoadingScreenState.tick(player);
        if (!LoadingScreenState.isActive()) {
            return;
        }
        if (!wasActive) {
            loadingLockedYaw = player.getYRot();
            loadingLockedPitch = player.getXRot();
        }
        player.setYRot(loadingLockedYaw);
        player.setXRot(loadingLockedPitch);
        player.yHeadRot = loadingLockedYaw;
        player.yBodyRot = loadingLockedYaw;
    }

    /**
     * Bad Swimmer's constant pull. The curse's other half is a gravity attribute, but vanilla skips fluid
     * gravity ENTIRELY while sprinting — so a sprint-swim used to switch the curse clean off, leaving no
     * middle ground between "can't stay up at all" and "swimming works fine". This pull is applied here, to
     * the delta directly, so it bites in BOTH states and swimming becomes a battle rather than an exemption.
     *
     * <p>Client-side because player movement is client-authoritative. Never applied while flying, so creative
     * flight is untouched.
     */
    private static void tickBadSwimmer(LocalPlayer player) {
        boolean inLiquid = (player.isInWater() || player.isInLava())
                && player.getData(WitchModAttachments.BAD_SWIMMER_ACTIVE) >= 0;

        if (inLiquid && !player.getAbilities().flying) {
            double pull = Config.BAD_SWIMMER_CONSTANT_PULL.get();
            if (!wasInLiquid) {
                pull += Config.BAD_SWIMMER_ENTRY_PLUNGE.get(); // the yank as you break the surface
            }
            player.setDeltaMovement(player.getDeltaMovement().subtract(0.0, pull, 0.0));

            // You never learned the stroke: the SWIMMING pose is off the table entirely, which is what
            // separates this from Heavy (that one just makes you sink; this one makes you unable to swim).
            // Vanilla only enters the pose when isSprinting() is true in water, so killing the sprint at
            // the KEY is the real lever — a bare setSwimming(false) would be recomputed straight back next
            // tick. Same client-authoritative lesson as Gluttony's sprint cutoff.
            mc().options.keySprint.setDown(false);
            player.setSprinting(false);
            player.setSwimming(false);
        }
        wasInLiquid = inLiquid;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /**
     * Minor Inconvenience: you cannot go fullscreen, and your window is named something stupid every so
     * often.
     *
     * <p><b>Fullscreen is refused, then the window is MAXIMISED.</b> Both halves matter: the renamed title
     * bar is invisible in fullscreen (so the joke needs windowed), but merely dropping to whatever windowed
     * size they last had would shrink the play area, which is a real handicap rather than a minor
     * inconvenience. Maximised gives the whole screen AND a visible title bar.
     *
     * <p>Maximising is only re-asserted on the edge — when they try to go fullscreen again — rather than
     * every tick, so it doesn't fight Screensaver's shrink if both curses are running at once.
     */
    private static void tickMinorInconvenience(Minecraft mc, Window window, LocalPlayer player, boolean active) {
        if (!active) {
            if (minorSeen) {
                window.setTitle("Minecraft");
                minorSeen = false;
            }
            return;
        }

        if (!minorSeen) {
            WindowTitles.reload();   // pick up edits to the list without a restart
            minorSeen = true;
            nextRenameTick = 0;      // rename immediately on activation
            forceWindowedMaximised(mc, window);
        } else if (window.isFullscreen()) {
            // They tried. Setting the OPTION runs vanilla's own change callback, which does the toggle —
            // calling toggleFullScreen() as well double-toggles straight back.
            forceWindowedMaximised(mc, window);
        }

        if (tickCounter >= nextRenameTick) {
            window.setTitle(WindowTitles.pick(player.getRandom()));
            int min = Config.MINOR_RENAME_MIN.get();
            int max = Math.max(min, Config.MINOR_RENAME_MAX.get());
            nextRenameTick = tickCounter + min + player.getRandom().nextInt(max - min + 1);
        }
    }

    private static void forceWindowedMaximised(Minecraft mc, Window window) {
        if (window.isFullscreen()) {
            mc.options.fullscreen().set(false);
        }
        GLFW.glfwMaximizeWindow(window.getWindow());
    }

    /**
     * Heavy: you sink like an anchor. Client-side for the same reason Bad Swimmer's pull is — player movement
     * is client-authoritative — and needed at all because vanilla divides gravity by 16 in fluid, so the
     * GRAVITY attribute on its own only gives a slightly brisker version of the usual gentle bob.
     */
    private static void tickHeavyAnchor(LocalPlayer player) {
        if (player.getData(WitchModAttachments.HEAVY_ACTIVE) < 0 || player.getAbilities().flying) {
            return;
        }
        if (player.isInWater() || player.isInLava()) {
            player.setDeltaMovement(player.getDeltaMovement()
                    .subtract(0.0, Config.HEAVY_WATER_PULL.get(), 0.0));
        }
    }

    private static void tickPacingCamera(Minecraft mc, LocalPlayer player) {
        long now = mc.level.getGameTime();
        long end = player.getData(WitchModAttachments.PACING_END_TICK);
        boolean active = now < end;

        if (active && !pacingCamActive) {
            startPacing(mc, player);
        }

        if (active && pacingCamActive) {
            // Cut to a new shot every PACING_SHOT_TICKS, looping through the framings if the moment is long.
            int elapsed = (int) (now - (end - pacingTotalTicks));
            int shot = shotEntity.length == 0 ? -1
                    : (elapsed / Config.PACING_SHOT_TICKS.get()) % shotEntity.length;
            if (shot != currentShot) {
                currentShot = shot;
                applyShot(mc, player, shot);
            }
        } else if (!active && pacingCamActive) {
            endPacing(mc, player);
        }
    }

    /** Sets up the cinematic: resolves the victim, builds the shot list, starts the theme. */
    private static void startPacing(Minecraft mc, LocalPlayer player) {
        int focusId = player.getData(WitchModAttachments.PACING_FOCUS_ID);
        pacingFocus = focusId >= 0 ? mc.level.getEntity(focusId) : player;
        if (pacingFocus == null || !pacingFocus.isAlive()) {
            pacingFocus = player;
        }

        pacingCamActive = true;
        currentShot = -1;
        pacingTotalTicks = (int) Math.max(1, player.getData(WitchModAttachments.PACING_END_TICK) - mc.level.getGameTime());
        savedCameraType = mc.options.getCameraType();

        buildShots(mc, player);
        startTheme();
    }

    /** Restores the player's own camera and perspective, and cuts the theme dead. */
    private static void endPacing(Minecraft mc, LocalPlayer player) {
        pacingCamActive = false;
        currentShot = -1;
        pacingFocus = null;
        mc.setCameraEntity(player);
        if (savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
        }
        mc.gameRenderer.shutdownEffect(); // clear any lingering mob-vision shader
        stopTheme();
    }

    /** Points the camera for one cut, kills any inherited mob shader, and clicks. */
    private static void applyShot(Minecraft mc, LocalPlayer player, int shot) {
        Entity entity = shotEntity[shot];
        if (entity == null || !entity.isAlive()) {
            entity = pacingFocus != null ? pacingFocus : player;
        }
        mc.setCameraEntity(entity);
        mc.options.setCameraType(shotFront[shot] ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        // Riding a mob loads its vision shader (spider/creeper/enderman); kill it immediately. checkEntityPostEffect
        // only re-runs on the next setCameraEntity, so this stays off for the whole shot.
        if (!(entity instanceof Player)) {
            mc.gameRenderer.shutdownEffect();
        }
        playCutSound(player); // a click between cuts... or, rarely, the truth
    }

    /**
     * Precomputes the shot list: the first 2–4 cuts are the victim (orbit angles), then it cuts to nearby
     * frozen entities — each a FRONT-view face shot — biased toward ones not yet featured, so the spotlight
     * spreads around rather than lingering on one. Every shot gets a small dutch-angle roll for drama.
     */
    private static void buildShots(Minecraft mc, LocalPlayer player) {
        List<Entity> entities = new ArrayList<>();
        List<Boolean> fronts = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        List<Float> pitches = new ArrayList<>();
        List<Float> rolls = new ArrayList<>();

        float base = pacingFocus.getYRot();
        float[] orbit = {35f, 125f, 215f, 305f};
        float[] tilt = {10f, 20f, -6f, 14f};

        // 2–4 opening shots, all of the victim, orbiting.
        int victimShots = 2 + player.getRandom().nextInt(3);
        for (int i = 0; i < victimShots; i++) {
            entities.add(pacingFocus);
            fronts.add(false);
            yaws.add(base + orbit[i % orbit.length]);
            pitches.add(tilt[i % tilt.length]);
            rolls.add(dutch(player));
        }

        // Nearby frozen entities, nearest first, each a front-view face zoom. Shuffled so repeat loops don't
        // always show them in the same order — the "variety bias" toward whoever hasn't been on screen.
        List<Entity> nearby = new ArrayList<>(mc.level.getEntitiesOfClass(LivingEntity.class,
                pacingFocus.getBoundingBox().inflate(Config.PACING_FREEZE_RADIUS.get()),
                e -> e != pacingFocus));
        nearby.sort((a, b) -> Double.compare(a.distanceToSqr(pacingFocus), b.distanceToSqr(pacingFocus)));
        int cap = Math.min(nearby.size(), Config.PACING_MAX_INVOLVED.get());
        java.util.Collections.shuffle(nearby.subList(0, cap), new java.util.Random(player.getRandom().nextLong()));
        for (int i = 0; i < cap; i++) {
            entities.add(nearby.get(i));
            fronts.add(true);         // face shot
            yaws.add(Float.NaN);       // let vanilla frame the face; only roll is overridden
            pitches.add(0f);
            rolls.add(dutch(player));
        }

        int n = entities.size();
        shotEntity = entities.toArray(new Entity[0]);
        shotFront = new boolean[n];
        shotYaw = new float[n];
        shotPitch = new float[n];
        shotRoll = new float[n];
        for (int i = 0; i < n; i++) {
            shotFront[i] = fronts.get(i);
            shotYaw[i] = yaws.get(i);
            shotPitch[i] = pitches.get(i);
            shotRoll[i] = rolls.get(i);
        }
    }

    /** A small random dutch-angle tilt, alternating sign so consecutive shots lean opposite ways. */
    private static float dutch(LocalPlayer player) {
        return (player.getRandom().nextBoolean() ? 1 : -1) * (4f + player.getRandom().nextFloat() * 6f);
    }

    private static void playCutSound(LocalPlayer player) {
        SoundEvent sound = player.getRandom().nextInt(Config.PACING_THE_ONE_PIECE_CHANCE.get()) == 0
                ? WitchModSounds.PACING_THE_ONE_PIECE.get()
                : WitchModSounds.PACING_CLICK.get();
        float volume = (float) (double) Config.PACING_CLICK_VOLUME.get();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, volume));
    }

    private static void startTheme() {
        stopTheme();
        pacingTheme = SimpleSoundInstance.forUI(WitchModSounds.PACING_THEME.get(), 1.0F,
                (float) (double) Config.PACING_THEME_VOLUME.get());
        Minecraft.getInstance().getSoundManager().play(pacingTheme);
    }

    private static void stopTheme() {
        if (pacingTheme != null) {
            Minecraft.getInstance().getSoundManager().stop(pacingTheme);
            pacingTheme = null;
        }
    }

}
