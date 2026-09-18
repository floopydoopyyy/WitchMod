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
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * the client-side curses, driven off auto-synced flags on the local player:
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
    /** social Outcast: how close a sound must start to a hidden entity to be treated as theirs. */
    private static final double OUTCAST_SOUND_MATCH_RADIUS = 2.0;
    /** reused rather than allocated per frame — this runs every render tick while a shake is active. */
    private static final java.util.Random SHAKE_RNG = new java.util.Random();

    private static int nextRenameTick;

    private static int tickCounter;

    // builder: cached reflection into the two private client-side place/break delay counters.
    private static java.lang.reflect.Field rightClickDelayField;
    private static java.lang.reflect.Field destroyDelayField;
    private static boolean builderReflectInit;

    // gladiator: the hotbar slot held when the parry input-lock began (so switching is reverted).
    private static int gladiatorLockSlot = -1;
    // gladiator: mirror the server's parry weapon-cooldown onto the CLIENT ticker so the attack indicator shows it.
    private static long gladiatorWeaponReadySeen = Long.MIN_VALUE;
    private static java.lang.reflect.Field clientAttackTickerField;
    private static boolean clientAttackTickerResolved;

    private static void setClientAttackTicker(LocalPlayer player, int ticks) {
        if (!clientAttackTickerResolved) {
            clientAttackTickerResolved = true;
            try {
                clientAttackTickerField = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                clientAttackTickerField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                clientAttackTickerField = null;
            }
        }
        if (clientAttackTickerField != null) {
            try {
                clientAttackTickerField.setInt(player, ticks);
            } catch (ReflectiveOperationException ignored) {
                // non-fatal
            }
        }
    }

    /** prop Hunt: lift the disguise block back up by vanilla's sneak render offset (2px) while crouched-anchored. */
    private static final double PROPHUNT_CROUCH_LIFT = 0.125;

    private static int spiderWallJumpCooldown;
    private static boolean spiderJumpWasDown;
    private static boolean spiderAttached; // must attach (climb/cling) before a wall-jump is allowed

    /** true if there's a wall within reach (adjacency, so clinging works without pushing into it). */
    private static boolean spiderNearWall(LocalPlayer player) {
        return !player.level().noCollision(player, player.getBoundingBox().inflate(0.12, 0.0, 0.12));
    }

    // ninja state.
    private static boolean ninjaCanDoubleJump = true;
    private static boolean ninjaJumpWasDown;
    private static boolean ninjaWasSwinging;
    private static boolean ninjaJumpArmed; // must RELEASE jump mid-air before the double jump can fire

    /** ninja: mid-air double jump, a sharp woosh on every swing, and ninja-smoke FX. Client-authoritative. */
    private static void tickNinja(Minecraft mc, LocalPlayer player) {
        boolean active = player.getData(WitchModAttachments.NINJA_ACTIVE) >= 0;
        boolean jumpDown = mc.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !ninjaJumpWasDown;
        ninjaJumpWasDown = jumpDown;
        if (!active) {
            ninjaWasSwinging = player.swinging;
            return;
        }
        if (player.onGround() || player.onClimbable() || player.isInWater()) {
            ninjaCanDoubleJump = true;
            ninjaJumpArmed = false; // reset — you must leave the ground and release jump to arm the second jump
        } else if (!jumpDown) {
            ninjaJumpArmed = true; // released mid-air → a fresh press can now double-jump
        }
        if (jumpPressed && ninjaJumpArmed && !player.onGround() && !player.onClimbable() && !player.isInWater()
                && !player.getAbilities().flying && ninjaCanDoubleJump) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Config.NINJA_DOUBLE_JUMP_POWER.get(), v.z);
            player.hurtMarked = true;
            player.fallDistance = 0;
            ninjaCanDoubleJump = false;
            ninjaDoubleJumpFx(player);
        }
        if (player.swinging && !ninjaWasSwinging) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    WitchModSounds.GLADIATOR_WHIFF.get(), SoundSource.PLAYERS, 0.6F, Config.NINJA_SWING_PITCH.get().floatValue(), false);
        }
        ninjaWasSwinging = player.swinging;
        if (player.isSprinting() && tickCounter % 3 == 0) {
            player.level().addParticle(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.15, player.getZ(), 0, 0.01, 0);
        }
    }

    private static void ninjaDoubleJumpFx(LocalPlayer player) {
        for (int i = 0; i < 22; i++) {
            double a = i / 22.0 * Math.PI * 2.0;
            player.level().addParticle(ParticleTypes.CLOUD,
                    player.getX() + Math.cos(a) * 0.7, player.getY() + 0.1, player.getZ() + Math.sin(a) * 0.7,
                    Math.cos(a) * 0.14, 0.03, Math.sin(a) * 0.14);
        }
        player.level().addParticle(ParticleTypes.POOF, player.getX(), player.getY() + 0.3, player.getZ(), 0, 0.1, 0);
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                WitchModSounds.GLADIATOR_WHIFF.get(), SoundSource.PLAYERS, 0.8F, 0.85F, false);
    }

    /** prop Hunt: render the disguised player as their chosen block instead of the player model. */
    @SubscribeEvent
    static void onPropHuntRender(RenderPlayerEvent.Pre event) {
        int id = event.getEntity().getData(WitchModAttachments.PROPHUNT_BLOCK);
        if (id < 0) {
            return;
        }
        event.setCanceled(true); // no player model or nametag while disguised
        BlockState state = Block.stateById(id);
        var player = event.getEntity();
        float pt = event.getPartialTick();
        double px = Mth.lerp(pt, player.xOld, player.getX());
        double py = Mth.lerp(pt, player.yOld, player.getY());
        double pz = Mth.lerp(pt, player.zOld, player.getZ());

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        long anchor = player.getData(WitchModAttachments.PROPHUNT_ANCHOR);
        if (anchor != Long.MIN_VALUE) {
            // anchored (crouched): render at the EXACT world cell the server pinned. Crouching only happens
            // here, and vanilla's sneak pose drops the render ~2px AFTER this event fires — add it back so the
            // block sits at true block height instead of sinking into the floor.
            BlockPos bp = BlockPos.of(anchor);
            pose.translate(bp.getX() - px, bp.getY() - py + PROPHUNT_CROUCH_LIFT, bp.getZ() - pz);
        } else {
            // moving: the block follows you, centred on your feet.
            pose.translate(-0.5, 0.0, -0.5);
        }
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, event.getMultiBufferSource(),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * spider: climb a wall you push into, CLING to it (hang on the edge) while crouching, and WALL-JUMP off
     * it — a fresh jump press kicks you up and away, so you can bounce between walls. Client-authoritative.
     */
    private static void tickSpider(Minecraft mc, LocalPlayer player) {
        if (spiderWallJumpCooldown > 0) {
            spiderWallJumpCooldown--;
        }
        boolean jumpDown = mc.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !spiderJumpWasDown; // rising edge
        spiderJumpWasDown = jumpDown;

        if (player.getData(WitchModAttachments.SPIDER_ACTIVE) < 0
                || player.onGround() || player.isInWater() || player.getAbilities().flying
                || !spiderNearWall(player)) {
            spiderAttached = false; // on the ground / off the wall — must re-attach before jumping off
            return;
        }
        Vec3 v = player.getDeltaMovement();

        // wall jump — ONLY once attached (so running into a wall can't fling you), launching where you LOOK.
        if (jumpPressed && spiderAttached && spiderWallJumpCooldown <= 0) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1.0e-4) {
                flat = flat.normalize().scale(Config.SPIDER_WALL_JUMP_AWAY.get());
            }
            player.setDeltaMovement(flat.x, Config.SPIDER_WALL_JUMP_UP.get(), flat.z);
            player.hurtMarked = true;
            player.fallDistance = 0;
            spiderAttached = false;
            spiderWallJumpCooldown = Config.SPIDER_WALL_JUMP_COOLDOWN.get();
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.SPIDER_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.4F, false);
            return;
        }

        if (mc.options.keyUp.isDown()) {
            player.setDeltaMovement(v.x, Config.SPIDER_CLIMB_SPEED.get(), v.z); // climb up
            player.fallDistance = 0;
            spiderAttached = true;
        } else if (player.isShiftKeyDown()) {
            player.setDeltaMovement(v.x, 0.0, v.z); // cling / stay put on the wall
            player.fallDistance = 0;
            spiderAttached = true;
        }
    }

    /** gladiator: true while the hand is committed to a parry (no switch/swing/use). */
    private static boolean gladiatorLocked(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && player.getData(WitchModAttachments.GLADIATOR_LOCK_END) > mc.level.getGameTime();
    }

    /** blocks attack/use/pick while parrying; the slot revert + swap/drop drain live in the tick handlers. */
    @SubscribeEvent
    static void onGladiatorInputLock(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && gladiatorLocked(player) && (event.isAttack() || event.isUseItem() || event.isPickBlock())) {
            event.setCanceled(true);
        }
    }

    /** drains swap-offhand / drop before the tick processes them, while parrying. */
    @SubscribeEvent
    static void onGladiatorLockPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !gladiatorLocked(mc.player)) {
            return;
        }
        while (mc.options.keySwapOffhand.consumeClick()) {
            // discard
        }
        while (mc.options.keyDrop.consumeClick()) {
            // discard
        }
    }
    private static boolean screensaverSeen;

    private static boolean minorSeen;
    private static boolean wasInLiquid; // Bad Swimmer: tracks the surface-break for the entry plunge
    private static float loadingLockedYaw;   // Loading Screen: the view is frozen here for the duration
    private static float loadingLockedPitch;

    // pacing camera state. The camera entity is always the VICTIM (a player), so it never becomes a mob and
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

    // cutaway Gag camera state: an overhead standstill from the watcher's own eyes (server places us at a good
    // vantage), aimed at the victim. The pinned angles hold the shot steady between ticks.
    private static boolean cutawayCamActive;
    private static CameraType cutawaySavedCamera;
    private static float cutawayLockedYaw;
    private static float cutawayLockedPitch;
    private static SoundInstance cutawayLoopSound; // Helicopter / tractor-beam / annoying-music positional loop
    private static DwellerBreathingSound dwellerBreathing; // the Dweller's subtle watching-breath loop
    private static int cutawayLoopId = -1;
    private static long cutawayStartTick;      // when the cinematic engaged (drives the title-card timing)
    private static String cutawayTitle = "";   // the "Meanwhile…" card for this cutaway (from a writable list)
    private static String cutawayVictimName = "";

    private ClientCurseHandler() {}

    /**
     * loading Screen: kill every interaction key (attack, use, pick block) while the fake load is up. Menus
     * are deliberately left alone — those aren't routed through this event.
     */
    @SubscribeEvent
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        // loading Screen, Pacing and Cutaway Gag all kill every interaction key (attack, use, pick block).
        // menus are deliberately left alone — those aren't routed through this event.
        LocalPlayer cutawayCheck = Minecraft.getInstance().player;
        if (LoadingScreenState.isActive() || pacingCamActive
                || (cutawayCheck != null && cutawayCheck.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0)) {
            event.setCanceled(true);
            return;
        }

        // delusions: swinging at one pops it. Cancelled so the swing doesn't ALSO carry on into whatever's
        // behind it — the arm still swings, so it reads as a hit that connected with something.
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && DelusionManager.onAttack(player)) {
            event.setSwingHand(true);
            event.setCanceled(true);
        }
    }

    /**
     * social Outcast: other players and villagers simply aren't drawn — no model, no nametag — unless
     * they're close enough to touch or have hit you recently.
     *
     * <p>Cancelling {@code RenderLivingEvent.Pre} returns before {@code LivingEntityRenderer.render} reaches
     * its {@code super.render} call, which is what draws the nametag — so one cancel hides both. Nothing
     * about the world changes: they're still there, still solid, still able to kill you.
     */
    /**
     * the local player may see a Dweller (Spaghetti Man / watcher eyes) if it's their OWN ({@code victim} matches),
     * or if they themselves currently carry the Haunted curse (synced {@code DWELLER_ACTIVE >= 0}) — so multiple
     * cursed players share the nightmare instead of an entity that half-renders. Everyone else sees nothing.
     */
    private static boolean canSeeDweller(java.util.UUID victim) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return false;
        }
        return me.getUUID().equals(victim) || me.getData(WitchModAttachments.DWELLER_ACTIVE) >= 0;
    }

    @SubscribeEvent
    static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        // the Dweller: the Spaghetti Man is a private horror — drawn for its own VICTIM, and (so the nightmare is
        // shared, not duplicated into invisibility bugs) for ANYONE ELSE who ALSO currently carries the Haunted
        // curse. Uncursed players' clients refuse to render it, so to them it never existed.
        if (event.getEntity() instanceof com.oliver.witchmod.entities.SpaghettiManEntity dweller) {
            if (!canSeeDweller(dweller.getVictim().orElse(null))) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getEntity() instanceof com.oliver.witchmod.entities.WatcherEyesEntity watcher) {
            if (!canSeeDweller(watcher.getVictim().orElse(null))) {
                event.setCanceled(true);
                return;
            }
        }

        // immortality: while a player is rebuilding, their MODEL is hidden so the gold→white particle
        // silhouette stands in its place until they pop back. Visible to everyone (the recovery flags are
        // synced to trackers), so any onlooker sees the particle-body too.
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player p && isImmortalityRebuilding(p)) {
            event.setCanceled(true);
            return;
        }

        // unseen: another player who's cloaked is hidden entirely from you unless you're within the reveal
        // distance; a cloak/uncloak puff fires as they cross it, so both parties get clear feedback.
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer != null && event.getEntity() instanceof net.minecraft.world.entity.player.Player other
                && other != viewer && other.getData(WitchModAttachments.UNSEEN_ACTIVE) >= 0) {
            double reveal = Config.UNSEEN_REVEAL_DISTANCE.get();
            boolean shouldRender = viewer.distanceToSqr(other) <= reveal * reveal;
            boolean wasRendered = UNSEEN_RENDERED.getOrDefault(other.getId(), Boolean.TRUE);
            if (shouldRender != wasRendered) {
                UNSEEN_RENDERED.put(other.getId(), shouldRender);
                spawnUnseenTransition(other, shouldRender);
            }
            if (!shouldRender) {
                event.setCanceled(true);
                return;
            }
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE) < 0) {
            return;
        }
        if (isOutcastHidden(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * social Outcast: hidden entities are silenced as well as invisible. Hearing footsteps and villager
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
        // only categories entities actually emit — block and ambient sounds are the world, not a person.
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

    /** shared by the render and audio hooks, so the two can never disagree about who is hidden. */
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

        // loading Screen / Pacing / Cutaway: you are not playing right now. Movement, jumping and sneaking all
        // go dead.
        if (LoadingScreenState.isActive() || pacingCamActive || isImmortalityRebuilding(event.getEntity())
                || event.getEntity().getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
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

        Minecraft mc = Minecraft.getInstance();

        // hiccups: a brief movement freeze on each hic — an input lock (NOT Slowness), so your FOV never dips.
        if (mc.player != null && mc.level != null
                && mc.level.getGameTime() < mc.player.getData(WitchModAttachments.HICCUPS_FREEZE_END)) {
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            return;
        }

        // backseat Driver: the AI has the wheel. Your own steering is cut dead and replaced with its
        // heading — a ridden mount faces wherever the RIDER faces and throttles on the rider's forward
        // input, so turning the rider IS the steering, and the animal walks there under its own power.
        if (mc.player != null && mc.level != null
                && mc.level.getGameTime() < event.getEntity().getData(WitchModAttachments.BACKSEAT_EPISODE_END)) {
            float wanted = event.getEntity().getData(WitchModAttachments.BACKSEAT_DRIVE_YAW);
            // ease onto the heading rather than snapping, so it reads as the mount turning, not a teleport.
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

        // siren's Call: at full longing the water takes the wheel. Same hijack as Backseat Driver — ease the
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

        // gluttony: no sprinting once the COMBINED bar is at/below the cutoff. This has to be done here,
        // client-side, and by suppressing the sprint KEY: sprinting is decided in LocalPlayer.aiStep, so a
        // server-side setSprinting(false) is overwritten immediately, and merely clearing the flag here just
        // lets the very next line of aiStep turn it straight back on.
        // sticky: suppress the drop key outright, so the stack never leaves its slot in the first place.
        // this is the PRIMARY mechanism, not a nicety — the server-side ItemTossEvent cancel has to hand the
        // item back by hand (cancelling alone deletes it), and never letting it leave is far cleaner than
        // pulling it out and pushing it back in, which can land it in a different slot.
        if (mc.player != null && mc.player.getData(WitchModAttachments.STICKY_ACTIVE) >= 0) {
            mc.options.keyDrop.setDown(false);
        }

        // thirst Meter cuts sprinting off the same way, and for the same client-authoritative reason.
        if (mc.player != null && (isGluttonySprintBlocked(mc.player) || isThirstSprintBlocked(mc.player))) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }

        // moonwalker: W does nothing. Only FORWARD is blocked — back, left and right all work normally, which
        // is what makes it a shuffling-backwards curse rather than a full input scramble.
        if (event.getEntity().getData(WitchModAttachments.MOONWALKER_ACTIVE) >= 0 && input.forwardImpulse > 0.0F) {
            input.forwardImpulse = 0.0F;
            input.up = false;
        }

        // wonky: a subtle sideways wander you can't quite hold a line against — but only while you're
        // actually trying to move, and worse when you sprint. A slow sine gives a lazy weave rather than
        // jitter; it's added to the strafe, so it never affects standing still or facing.
        if (mc.player != null && mc.player.getData(WitchModAttachments.WONKY_ACTIVE) >= 0
                && (Math.abs(input.forwardImpulse) > 0.0F || Math.abs(input.leftImpulse) > 0.0F)) {
            // per-tick phase — this event fires once a tick, which is plenty for a lazy weave.
            double phase = mc.level.getGameTime() * (2.0 * Math.PI / Config.WONKY_PERIOD_TICKS.get());
            double strength = Config.WONKY_DRIFT_STRENGTH.get();
            if (mc.player.isSprinting()) {
                strength *= Config.WONKY_SPRINT_MULTIPLIER.get();
            }
            input.leftImpulse += (float) (Math.sin(phase) * strength);
        }

        // vertigo (MOVEMENT sway): high up, your feet wander too — a sideways weave (like Wonky) that kicks in
        // above vertigoMovementStartY and grows with height, on TOP of the camera sway. It ALSO applies when
        // you're standing still (at ~60%), so even PILLARING up is dangerous — you drift off the block.
        if (mc.player != null && mc.player.getData(WitchModAttachments.VERTIGO_ACTIVE) >= 0) {
            // dizzy_heights synergy (with Wonky): lower onset, stronger weave, more sway even standing still.
            boolean dizzy = mc.player.getData(WitchModAttachments.WONKY_ACTIVE) >= 0;
            double startY = Config.VERTIGO_MOVEMENT_START_Y.get() * (dizzy ? Config.DIZZY_HEIGHTS_START_MULT.get() : 1.0);
            double t = vertigoRamp(mc.player.getY(), startY);
            if (t > 0.0) {
                boolean moving = Math.abs(input.forwardImpulse) > 0.0F || Math.abs(input.leftImpulse) > 0.0F;
                double factor = moving ? 1.0 : (dizzy ? 0.9 : 0.6); // still-standing sway — pillaring stays perilous
                double strength = Config.VERTIGO_MOVEMENT_STRENGTH.get() * (dizzy ? Config.DIZZY_HEIGHTS_SWAY_MULT.get() : 1.0);
                double phase = System.nanoTime() / 1.0e9 * 1.9;
                input.leftImpulse += (float) (Math.sin(phase) * strength * t * factor);
            }
        }

        // stick Drift (MOVEMENT mode): a phantom stick input in the fixed drifted direction, always the same
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

    /** true while this player is mid-rebuild for the Immortality blessing — movement input is locked. */
    private static boolean isImmortalityRebuilding(net.minecraft.world.entity.player.Player player) {
        long end = player.getData(WitchModAttachments.IMMORTALITY_RECOVERY_END);
        return end > 0L && player.level().getGameTime() < end;
    }

    /**
     * jesus: hold the player on the water surface. Not crouching = you stand on / float up to the top; crouching
     * (or flying) skips this, so sneaking drops you under. Water only. Client-side, since player movement is
     * client-authoritative — the resulting position syncs up so others see you on the water.
     */
    private static void tickJesus(LocalPlayer player) {
        if (player.getData(WitchModAttachments.JESUS_ACTIVE) < 0
                || player.isShiftKeyDown() || player.getAbilities().flying) {
            return;
        }
        Double surface = waterSurfaceAt(player.level(), player.getX(), player.getY(), player.getZ());
        if (surface == null || player.getY() > surface + 0.2) {
            return; // no water column here, or you're still up in the air above it — fall onto it normally
        }
        net.minecraft.world.phys.Vec3 dm = player.getDeltaMovement();
        if (player.getY() < surface - 0.05) {
            // below the surface: buoy up toward it rather than sinking.
            player.setDeltaMovement(dm.x, Math.max(dm.y, 0.15), dm.z);
        } else {
            // at the surface: stand on it.
            player.setPos(player.getX(), surface, player.getZ());
            if (dm.y < 0.0) {
                player.setDeltaMovement(dm.x, 0.0, dm.z);
            }
            player.setOnGround(true);
            player.resetFallDistance();
        }
    }

    private static net.minecraft.world.phys.Vec3 bouncyPrevVel;
    private static boolean bouncyPrevGround;

    private static int coyoteLastGroundTick = -100;
    private static boolean coyoteUsed;
    private static boolean coyoteDepartedByJump;
    private static boolean coyotePrevGround;

    /**
     * coyote: a late jump for {@code coyoteTicks} after you WALK off a ledge (not after you jump — no double
     * jumps), plus a gentle forward pull toward a ledge edge ahead you're falling just short of. All
     * client-side, since movement is client-authoritative.
     */
    private static void tickCoyote(Minecraft mc, LocalPlayer player) {
        if (player.getData(WitchModAttachments.COYOTE_ACTIVE) < 0) {
            return;
        }
        boolean ground = player.onGround();
        net.minecraft.world.phys.Vec3 cur = player.getDeltaMovement();

        if (ground) {
            coyoteLastGroundTick = tickCounter;
            coyoteUsed = false;
        } else if (coyotePrevGround) {
            coyoteDepartedByJump = cur.y > 0.15; // left the ground rising = a real jump; ~0 = walked off
        }

        if (!ground && !coyoteDepartedByJump && !coyoteUsed
                && tickCounter - coyoteLastGroundTick <= Config.COYOTE_TICKS.get()
                && mc.options.keyJump.isDown() && cur.y < 0.3) {
            player.setDeltaMovement(cur.x, 0.42, cur.z); // vanilla jump velocity
            coyoteUsed = true;
        }

        if (!ground) {
            edgeMagnetism(player, cur);
        }
        coyotePrevGround = ground;
    }

    private static final net.minecraft.core.particles.DustParticleOptions LEADER_GOLD =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.84F, 0.2F), 1.0F);

    /** leader: draw the aura radius as a constant golden ring around the local leader (client-side, no server load). */
    private static void tickLeader(Minecraft mc, LocalPlayer player) {
        if (player.getData(WitchModAttachments.LEADER_ACTIVE) < 0 || tickCounter % 4 != 0) {
            return;
        }
        double radius = Config.LEADER_RADIUS.get();
        int points = (int) Mth.clamp(radius * 5.0, 32, 140);
        double y = player.getY() + 0.1;
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points;
            player.level().addParticle(LEADER_GOLD,
                    player.getX() + radius * Math.cos(a), y, player.getZ() + radius * Math.sin(a), 0.0, 0.0, 0.0);
        }
    }

    private static final net.minecraft.core.particles.DustParticleOptions GUARDIAN_HALO =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.92F, 0.55F), 0.7F);

    /**
     * guardian Angel: render each guardian allay's halo + movement trail on the CLIENT, off the synced
     * {@code GUARDIAN_RENDER} marker (kind code). Moves the two highest-frequency emitters off the server —
     * and, using the allay's own interpolated render position, the trail no longer lags the model.
     */
    private static void tickGuardianFx(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.animal.allay.Allay allay)) {
                continue;
            }
            int kind = allay.getData(WitchModAttachments.GUARDIAN_RENDER);
            if (kind <= 0) {
                continue;
            }
            double x = allay.getX();
            double y = allay.getY();
            double z = allay.getZ();
            if (kind != 9 && now % 3 == 0) { // halo (all but stealth)
                double ha = now * 0.25;
                for (int i = 0; i < 3; i++) {
                    double a = ha + i * (Math.PI * 2.0 / 3.0);
                    mc.level.addParticle(GUARDIAN_HALO, x + Math.cos(a) * 0.28, y + 0.72, z + Math.sin(a) * 0.28, 0, 0, 0);
                }
            }
            net.minecraft.core.particles.ParticleOptions trail = switch (kind) {
                case 2 -> ParticleTypes.ANGRY_VILLAGER;
                case 3 -> ParticleTypes.HAPPY_VILLAGER;
                case 4 -> ParticleTypes.SPLASH;
                case 5 -> ParticleTypes.NOTE;
                case 6 -> ParticleTypes.HEART;
                case 7 -> ParticleTypes.FIREWORK;
                case 1 -> ParticleTypes.END_ROD;
                default -> null; // 8 (idle-still) / 9 (stealth) leave no trail
            };
            int cadence = (kind == 3 || kind == 5 || kind == 6 || kind == 7) ? 4 : 7;
            if (trail != null && now % cadence == 0) {
                mc.level.addParticle(trail, x, y + 0.3, z, 0.0, 0.0, 0.0);
            }
        }
    }

    /** farmer's Spirit: client-side radius ring + motes on growable plants, shown only when near valid crops. */
    private static void tickFarmersSpirit(Minecraft mc, LocalPlayer player) {
        if (player.getData(WitchModAttachments.FARMERS_SPIRIT_ACTIVE) < 0 || tickCounter % 4 != 0) {
            return;
        }
        var level = player.level();
        int radius = Config.FARMSPIRIT_RADIUS.get();
        net.minecraft.core.BlockPos centre = player.blockPosition();
        boolean anyCrop = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    net.minecraft.core.BlockPos p = centre.offset(dx, dy, dz);
                    net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
                    boolean crop = s.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock
                            && !(s.getBlock() instanceof net.minecraft.world.level.block.GrassBlock);
                    if (crop || s.getBlock() instanceof net.minecraft.world.level.block.FarmBlock) {
                        anyCrop = true; // ring shows near any crops/farmland
                    }
                    // motes only on plants actually being helped (still growable).
                    if (crop && ((net.minecraft.world.level.block.BonemealableBlock) s.getBlock())
                            .isValidBonemealTarget(level, p, s) && player.getRandom().nextInt(4) == 0) {
                        level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 0.0, 0.0, 0.0);
                    }
                }
            }
        }
        if (!anyCrop) {
            return;
        }
        int points = 56;
        double y = player.getY() + 0.1;
        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.COMPOSTER,
                    player.getX() + radius * Math.cos(a), y, player.getZ() + radius * Math.sin(a), 0.0, 0.0, 0.0);
        }
    }

    /**
     * bias the player onto a landable ledge just ahead — but only while DESCENDING, only up to a modest
     * horizontal speed cap, and only if they're below it. So a short/slow jump gets nudged onto the ledge,
     * while a sprint jump (already at/above the cap) gets nothing — no accelerating "flash", no huge distance.
     */
    private static void edgeMagnetism(LocalPlayer player, net.minecraft.world.phys.Vec3 cur) {
        if (cur.y >= 0.0) {
            return; // only assist on the way DOWN toward a landing
        }
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(cur.x, 0.0, cur.z);
        double speed = flat.length();
        double cap = Config.COYOTE_ASSIST_MAX_SPEED.get();
        if (speed < 0.03 || speed >= cap) {
            return; // not moving, or already fast enough that it doesn't need (and shouldn't get) a boost
        }
        net.minecraft.world.phys.Vec3 dir = flat.scale(1.0 / speed);
        if (!landableLedgeAhead(player, dir)) {
            return;
        }
        // nudge the horizontal speed up TOWARD the cap (never past it) in the direction you're already going.
        double target = Math.min(cap, speed + Config.COYOTE_EDGE_MAGNETISM.get());
        player.setDeltaMovement(dir.x * target, cur.y, dir.z * target);
    }

    /** true if there's a landable ledge top a short way ahead in {@code dir}, at or below the player's feet. */
    private static boolean landableLedgeAhead(LocalPlayer player, net.minecraft.world.phys.Vec3 dir) {
        var level = player.level();
        double feetY = player.getY();
        for (double d = 0.5; d <= 2.0; d += 0.5) {
            int bx = net.minecraft.util.Mth.floor(player.getX() + dir.x * d);
            int bz = net.minecraft.util.Mth.floor(player.getZ() + dir.z * d);
            for (int topY = net.minecraft.util.Mth.floor(feetY + 0.3); topY >= net.minecraft.util.Mth.floor(feetY - 1.5); topY--) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(bx, topY, bz);
                boolean solid = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
                boolean airAbove = level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
                if (solid && airAbove && feetY >= topY + 0.8) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * bouncy: rebound off floors (scaled by the accumulated fall speed, and HOLDING JUMP adds on top so
     * repeated jumps build height), off walls (sprint into one and ping back the opposite way) and off ceilings
     * (bonk and drop). All client-side because player movement is client-authoritative. Every bounce tells the
     * server (via a packet) so it plays the boing + particles for everyone nearby.
     */
    private static void tickBouncy(Minecraft mc, LocalPlayer player) {
        if (player.getData(WitchModAttachments.BOUNCY_ACTIVE) < 0) {
            bouncyPrevVel = null;
            return;
        }
        net.minecraft.world.phys.Vec3 prev = bouncyPrevVel;
        net.minecraft.world.phys.Vec3 cur = player.getDeltaMovement();
        boolean ground = player.onGround();
        // low-gravity synergy (flag == 2): rebounds are stronger and the cap is lifted the same.
        double synergyMult = player.getData(WitchModAttachments.BOUNCY_ACTIVE) >= 2
                ? Config.BOUNCY_LOWGRAV_RESTITUTION_MULT.get() : 1.0;
        double rest = Config.BOUNCY_RESTITUTION.get() * synergyMult;
        double cap = Config.BOUNCY_LANDING_MAX.get() * synergyMult;

        // the speed you were falling at ENTERING this tick (last tick's downward velocity).
        double impactSpeed = prev != null ? -prev.y : 0.0;
        double minBounce = Config.BOUNCY_MIN_FALL_VELOCITY.get();

        // A landing is either: you touched down normally (ground now, airborne last tick), OR you touched down
        // and the auto-jump fired the SAME tick (still falling last tick, now rising) — both need catching, or
        // holding jump would hide the landing behind the jump. Sneak to land without bouncing.
        boolean landedGrounded = ground && !bouncyPrevGround;
        boolean landedJumped = !ground && prev != null && prev.y < -0.1 && cur.y > 0.05;

        // when you're NOT holding jump, only a real fall bounces (so normal jumping/landing doesn't spam little
        // bounces). Holding jump drops the bar so you can trampoline and build height. Sneak never bounces.
        double threshold = mc.options.keyJump.isDown() ? 0.1 : minBounce;

        boolean bounced = false;
        if ((landedGrounded || landedJumped) && impactSpeed > threshold && !player.isShiftKeyDown()) {
            // cur.y already holds the auto-jump if you held jump this tick, so it stacks onto the rebound and
            // continuously holding jump compounds into more height; without a jump it just decays and settles.
            double up = Math.max(0.0, cur.y) + Math.min(cap, impactSpeed * rest);
            player.setDeltaMovement(cur.x, Math.min(cap + 0.5, up), cur.z);
            bounced = true;
        } else if (prev != null && player.horizontalCollision
                && prev.horizontalDistanceSqr() > Config.BOUNCY_WALL_MIN_SPEED.get() * Config.BOUNCY_WALL_MIN_SPEED.get()) {
            player.setDeltaMovement(-prev.x * rest, cur.y, -prev.z * rest); // wall: ping back the way you came
            bounced = true;
        } else if (prev != null && player.verticalCollision && !ground && prev.y > 0.15) {
            player.setDeltaMovement(cur.x, -prev.y * rest, cur.z); // ceiling: bonk and drop
            bounced = true;
        }
        if (bounced) {
            player.hurtMarked = true;
            player.resetFallDistance();
            // tell the server so EVERYONE hears/sees the bounce, not just us.
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.oliver.witchmod.network.WitchModNetwork.BouncyBoingPayload(
                            player.getX(), player.getY(), player.getZ()));
        }
        bouncyPrevVel = player.getDeltaMovement();
        bouncyPrevGround = ground;
    }

    /** top of the water column at the player's feet (scanning up for the surface), or null if no water here. */
    private static Double waterSurfaceAt(net.minecraft.world.level.Level level, double px, double py, double pz) {
        int x = net.minecraft.util.Mth.floor(px);
        int z = net.minecraft.util.Mth.floor(pz);
        int startY = net.minecraft.util.Mth.floor(py);
        int baseY;
        if (level.getFluidState(new net.minecraft.core.BlockPos(x, startY, z)).is(net.minecraft.tags.FluidTags.WATER)) {
            baseY = startY;
        } else if (level.getFluidState(new net.minecraft.core.BlockPos(x, startY - 1, z)).is(net.minecraft.tags.FluidTags.WATER)) {
            baseY = startY - 1;
        } else {
            return null;
        }
        int y = baseY;
        for (int i = 0; i < 24 && level.getFluidState(new net.minecraft.core.BlockPos(x, y + 1, z)).is(net.minecraft.tags.FluidTags.WATER); i++) {
            y++;
        }
        net.minecraft.core.BlockPos top = new net.minecraft.core.BlockPos(x, y, z);
        return top.getY() + (double) level.getFluidState(top).getHeight(level, top);
    }

    /** per-viewer render state of Unseen players (entity id -> was rendered last frame), for the transition puff. */
    private static final java.util.Map<Integer, Boolean> UNSEEN_RENDERED = new java.util.HashMap<>();

    /** client-side subtle black-smoke burst at an Unseen player as they cross the reveal edge (both directions). */
    private static void spawnUnseenTransition(net.minecraft.world.entity.player.Player other, boolean uncloak) {
        var level = other.level();
        int count = uncloak ? 10 : 6; // reveal a touch heavier than the faint cloak
        for (int i = 0; i < count; i++) {
            double px = other.getX() + (level.random.nextDouble() - 0.5) * other.getBbWidth() * 1.2;
            double py = other.getY() + level.random.nextDouble() * other.getBbHeight();
            double pz = other.getZ() + (level.random.nextDouble() - 0.5) * other.getBbWidth() * 1.2;
            level.addParticle(com.oliver.witchmod.effects.blessings.BlessingUnseen.BLACK, px, py, pz, 0.0, 0.01, 0.0);
        }
    }

    private static Double nightowlSavedGamma = null;
    private static java.lang.reflect.Field gammaValueField;

    private static Integer bedrockSavedRenderDistance = null;
    private static net.minecraft.client.CameraType bedrockSavedCamera = null;
    private static long bedrockLastMarketNonce = 0L;

    /** bedrock Moment per-tick client bugs: hotbar drift, perspective flip, split-screen POV, ad popup, sound delay. */
    private static void tickBedrockClientBugs(Minecraft mc, LocalPlayer player) {
        boolean active = player.getData(WitchModAttachments.BEDROCK_ACTIVE) >= 0;
        long now = mc.level != null ? mc.level.getGameTime() : 0L;

        // replay any delayed sounds whose beat has come.
        if (!bedrockDelayQueue.isEmpty()) {
            java.util.Iterator<DelayedSound> it = bedrockDelayQueue.iterator();
            while (it.hasNext()) {
                DelayedSound d = it.next();
                if (--d.ticks <= 0) {
                    bedrockDelayPass.add(d.sound);
                    mc.getSoundManager().play(d.sound);
                    it.remove();
                }
            }
        }

        // hotbar drift — your selected slot wanders on its own.
        if (active && player.tickCount % 30 == 0 && Math.random() < Config.BEDROCK_HOTBAR_DRIFT_CHANCE.get()) {
            int slot = (int) (Math.random() * 9);
            player.getInventory().selected = slot;
            if (mc.getConnection() != null) {
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
            }
        }

        // perspective flip — the camera is yanked to a disorienting front third-person view for a bit.
        long perspEnd = player.getData(WitchModAttachments.BEDROCK_PERSPECTIVE);
        boolean flip = perspEnd != Long.MIN_VALUE && now < perspEnd;
        if (flip && bedrockSavedCamera == null) {
            bedrockSavedCamera = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (!flip && bedrockSavedCamera != null) {
            mc.options.setCameraType(bedrockSavedCamera);
            bedrockSavedCamera = null;
        }

        // marketplace popup — a changing nonce means a fresh ad wants your attention.
        long nonce = player.getData(WitchModAttachments.BEDROCK_MARKETPLACE);
        if (nonce != 0L && nonce != bedrockLastMarketNonce) {
            bedrockLastMarketNonce = nonce;
            if (!(mc.screen instanceof com.oliver.witchmod.client.MarketplaceAdScreen)) {
                int count = Math.max(1, Config.BEDROCK_MARKETPLACE_AD_COUNT.get());
                int ad = (int) Math.floorMod(nonce, count) + 1;
                mc.setScreen(new com.oliver.witchmod.client.MarketplaceAdScreen(ad));
            }
        }
    }


    /** bedrock Moment (Chunk Rejection): while the window is up, slam render distance to 2 so chunks unload. */
    private static void tickBedrockChunkReject(Minecraft mc, LocalPlayer player) {
        long end = player.getData(WitchModAttachments.BEDROCK_CHUNK_REJECT);
        boolean reject = end != Long.MIN_VALUE && mc.level != null && mc.level.getGameTime() < end;
        if (reject && bedrockSavedRenderDistance == null) {
            bedrockSavedRenderDistance = mc.options.renderDistance().get();
            mc.options.renderDistance().set(2);
        } else if (!reject && bedrockSavedRenderDistance != null) {
            mc.options.renderDistance().set(bedrockSavedRenderDistance);
            bedrockSavedRenderDistance = null;
        }
    }

    private static boolean dwellerShaderActive = false;
    private static boolean dwellerShaderFailed = false;
    private static java.lang.reflect.Field postEffectField;
    private static final net.minecraft.resources.ResourceLocation DWELLER_SHADER =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.oliver.witchmod.WitchMod.MODID, "shaders/post/dweller.json");

    /**
     * the Dweller: a black-and-white post shader that drains the world's colour GRADUALLY as dread rises (via a
     * {@code DreadAmount} uniform set each tick), rather than snapping on — the world looks completely normal
     * early, so the rare first glimpse has nothing to soften it. No vanilla desaturate effect exists, so the mod
     * ships its own ({@code assets/witchmod/shaders/...}); a load failure degrades silently (fog + audio still
     * carry the dread) and is never retried.
     */
    private static void tickDwellerShader(Minecraft mc, LocalPlayer player) {
        float dread = player.getData(WitchModAttachments.DWELLER_DREAD);
        boolean want = dread > 0.06F;
        if (want && !dwellerShaderActive && !dwellerShaderFailed) {
            try {
                mc.gameRenderer.loadEffect(DWELLER_SHADER);
                dwellerShaderActive = true;
            } catch (Throwable t) {
                dwellerShaderFailed = true;
            }
        } else if (!want && dwellerShaderActive) {
            mc.gameRenderer.shutdownEffect();
            dwellerShaderActive = false;
        }
        if (dwellerShaderActive) {
            float amount = net.minecraft.util.Mth.clamp((dread - 0.06F) / (0.85F - 0.06F), 0.0F, 1.0F);
            amount = amount * amount * amount; // very drawn out — barely there until dread is high, bites near the top
            try {
                if (postEffectField == null) {
                    postEffectField = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("postEffect");
                    postEffectField.setAccessible(true);
                }
                Object pc = postEffectField.get(mc.gameRenderer);
                if (pc instanceof net.minecraft.client.renderer.PostChain chain) {
                    chain.setUniform("DreadAmount", amount);
                }
            } catch (Throwable ignored) {
                // uniform drive unavailable — the shader still runs at its default full strength
            }
        }

        // past a certain depth, all in-game music dies — jukeboxes/records, the ambient background, the lot.
        if (player.getData(WitchModAttachments.DWELLER_ACTIVE) >= 2) {
            mc.getMusicManager().stopPlaying();
        }
    }

    /**
     * nightowl full-bright: forces the gamma option's value past its normal 0..1 slider clamp (via the same
     * reflection approach the project already uses elsewhere), saving the player's real gamma and restoring it
     * when the blessing ends. Nothing is written to options.txt, so it never permanently changes their setting.
     */
    private static void tickNightowlBrightness(Minecraft mc, LocalPlayer player) {
        boolean active = player.getData(WitchModAttachments.NIGHTOWL_ACTIVE) >= 0;
        net.minecraft.client.OptionInstance<Double> gamma = mc.options.gamma();
        if (active) {
            if (nightowlSavedGamma == null) {
                nightowlSavedGamma = gamma.get();
            }
            setGammaRaw(gamma, 15.0); // re-assert each tick in case something resets it
        } else if (nightowlSavedGamma != null) {
            setGammaRaw(gamma, nightowlSavedGamma);
            nightowlSavedGamma = null;
        }
    }

    private static void setGammaRaw(net.minecraft.client.OptionInstance<Double> gamma, double value) {
        try {
            if (gammaValueField == null) {
                gammaValueField = net.minecraft.client.OptionInstance.class.getDeclaredField("value");
                gammaValueField.setAccessible(true);
            }
            gammaValueField.set(gamma, value);
        } catch (ReflectiveOperationException e) {
            gamma.set(value); // fallback: clamps to 1.0, still brighter than nothing
        }
    }

    /** current Stick Drift episode intensity for this player, or 0 when between episodes. */
    private static float stickDriftIntensity(LocalPlayer player, long now) {
        if (now >= player.getData(WitchModAttachments.STICK_DRIFT_END)) {
            return 0.0F;
        }
        return player.getData(WitchModAttachments.STICK_DRIFT_INTENSITY);
    }

    /**
     * stick Drift (CAMERA mode): rotates your ACTUAL aim a fixed way each tick — unlike a shake this really
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

    // --- New prototype batch (2026-09-02) client curses -------------------------------------------------

    private static net.minecraft.world.entity.HumanoidArm leftHandedSavedArm = null;
    private static float leftHandedSwayYaw;
    private static float leftHandedSwayPitch;

    /**
     * left Handed: flip the RENDERED main hand to the wrong side (saving + restoring the player's real setting,
     * so it never permanently changes their option), plus a PATTERNED aim sway that only bites while you're
     * aiming a ranged weapon — a smooth sine weave (its per-tick DELTA is applied, and the phase is tracked
     * even when not aiming, so resuming never jumps). Chat scrambling is separate, in {@link #onLeftHandedChat}.
     */
    private static void tickLeftHanded(Minecraft mc, LocalPlayer player) {
        boolean active = player.getData(WitchModAttachments.LEFT_HANDED_ACTIVE) >= 0;
        if (active) {
            if (leftHandedSavedArm == null) {
                leftHandedSavedArm = mc.options.mainHand().get();
            }
            net.minecraft.world.entity.HumanoidArm flipped = leftHandedSavedArm == net.minecraft.world.entity.HumanoidArm.RIGHT
                    ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT;
            if (mc.options.mainHand().get() != flipped) {
                mc.options.mainHand().set(flipped);
            }
            // patterned aim sway — while aiming/using ANYTHING (bow, trident, spyglass, ...) or a loaded crossbow.
            double amp = Config.LEFT_HANDED_DRIFT_DEGREES.get();
            double clock = System.nanoTime() / 1.0e9;
            float curYaw = (float) (Math.sin(clock * 2.2) * amp);
            float curPitch = (float) (Math.sin(clock * 1.6) * amp * 0.5);
            if (amp > 0.0 && isAiming(player)) {
                player.setYRot(player.getYRot() + (curYaw - leftHandedSwayYaw));
                player.setXRot(Mth.clamp(player.getXRot() + (curPitch - leftHandedSwayPitch), -90.0F, 90.0F));
            }
            leftHandedSwayYaw = curYaw;   // keep tracking the phase so it never jumps on resume
            leftHandedSwayPitch = curPitch;
        } else if (leftHandedSavedArm != null) {
            mc.options.mainHand().set(leftHandedSavedArm);
            leftHandedSavedArm = null;
        }
    }

    /** true while the player is actively USING/aiming any item (bow, trident, spyglass, ...), or a loaded crossbow. */
    private static boolean isAiming(LocalPlayer player) {
        if (player.isUsingItem()) {
            return true;
        }
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                net.minecraft.world.item.component.ChargedProjectiles charged =
                        s.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                if (charged != null && !charged.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** left Handed: what you type comes out as letter spam (commands are left alone so you can still cure it). */
    @SubscribeEvent
    static void onLeftHandedChat(net.neoforged.neoforge.client.event.ClientChatEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getData(WitchModAttachments.LEFT_HANDED_ACTIVE) < 0) {
            return;
        }
        String msg = event.getMessage();
        if (msg.startsWith("/")) {
            return; // let commands through
        }
        StringBuilder sb = new StringBuilder(msg.length());
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            sb.append(Character.isWhitespace(c) ? c : (char) ('a' + SHAKE_RNG.nextInt(26)));
        }
        event.setMessage(sb.toString());
    }

    private static net.minecraft.world.phys.Vec3 iceSkatesPrev = net.minecraft.world.phys.Vec3.ZERO;
    private static boolean iceSkatesWasSprinting;

    /**
     * ice Skates: the ground stops gripping — but ONLY the braking is affected, not your top speed. While
     * you're actively pressing a movement key you move at normal speed; the moment you LET GO, most of last
     * tick's momentum is retained ({@code iceSkatesSlip}) so you keep gliding a good way and can't stop dead.
     * letting go off a SPRINT gives a longer, faster skid ({@code iceSkatesSprintSlip}/{@code Cap}). Sneaking
     * grips (no glide). Client-authoritative movement.
     */
    private static void tickIceSkates(LocalPlayer player) {
        if (player.getData(WitchModAttachments.ICE_SKATES_ACTIVE) < 0) {
            iceSkatesPrev = net.minecraft.world.phys.Vec3.ZERO;
            iceSkatesWasSprinting = false;
            return;
        }
        net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
        boolean input = Math.abs(player.zza) > 1.0e-4F || Math.abs(player.xxa) > 1.0e-4F;
        if (input) {
            // remember whether you were sprinting AS you moved, so the skid off the back of it is enhanced.
            iceSkatesWasSprinting = player.isSprinting();
        } else if (player.onGround() && !player.isShiftKeyDown() && !player.getAbilities().flying) {
            // no input = you're trying to stop, but you can't: keep most of last tick's horizontal momentum.
            double slip = iceSkatesWasSprinting ? Config.ICE_SKATES_SPRINT_SLIP.get() : Config.ICE_SKATES_SLIP.get();
            double cap = iceSkatesWasSprinting ? Config.ICE_SKATES_SPRINT_CAP.get() : Config.ICE_SKATES_CAP.get();
            double nx = iceSkatesPrev.x * slip;
            double nz = iceSkatesPrev.z * slip;
            double sp = Math.sqrt(nx * nx + nz * nz);
            if (sp > cap) {
                nx = nx / sp * cap;
                nz = nz / sp * cap;
            }
            player.setDeltaMovement(nx, v.y, nz);
        }
        iceSkatesPrev = player.getDeltaMovement();
    }

    /**
     * vertigo: a smooth, continuous camera wobble that grows with altitude between {@code vertigoMinAltitude}
     * and {@code vertigoMaxAltitude}. Applied to the camera ANGLES only (never the real rotation), so it never
     * changes where you're aiming — it just makes heights feel sickening. Driven off a real-time clock so it's
     * smooth per-frame rather than stepping at 20 Hz.
     */
    private static void applyVertigo(Minecraft mc, ViewportEvent.ComputeCameraAngles event) {
        if (mc.player == null || mc.player.getData(WitchModAttachments.VERTIGO_ACTIVE) < 0) {
            return;
        }
        // dizzy_heights synergy (with Wonky): vertigo bites lower and sways harder.
        boolean dizzy = mc.player.getData(WitchModAttachments.WONKY_ACTIVE) >= 0;
        double startY = Config.VERTIGO_CAMERA_START_Y.get() * (dizzy ? Config.DIZZY_HEIGHTS_START_MULT.get() : 1.0);
        double t = vertigoRamp(mc.player.getY(), startY);
        if (t <= 0.0) {
            return;
        }
        double strength = Config.VERTIGO_CAMERA_STRENGTH.get() * t * (dizzy ? Config.DIZZY_HEIGHTS_SWAY_MULT.get() : 1.0);
        double clock = System.nanoTime() / 1.0e9;
        event.setRoll((float) (event.getRoll() + Math.sin(clock * 1.7) * strength));
        event.setPitch((float) (event.getPitch() + Math.sin(clock * 1.3) * strength * 0.5));
        event.setYaw((float) (event.getYaw() + Math.cos(clock * 1.1) * strength * 0.5));
    }

    /** vertigo intensity 0..1 for altitude {@code y}, ramping from {@code startY} up to the configured maxY. */
    private static double vertigoRamp(double y, double startY) {
        double maxY = Math.max(startY + 1, Config.VERTIGO_MAX_Y.get());
        return Mth.clamp((y - startY) / (maxY - startY), 0.0, 1.0);
    }

    private static boolean narratorPrevFocused = true;
    private static boolean narratorPrevPaused;
    private static int narratorUnfocusedTicks;
    /** saved 'pause on lost focus' option, stashed while the Narrator overrides it so it can be restored. */
    private static Boolean narratorSavedPauseOnLostFocus;

    /**
     * narrator: report the CLIENT-only events the server can't see — tabbing out (window loses focus), coming
     * back, and pausing (Esc menu while still focused). Edge-triggered where it should fire once; but once
     * you've been tabbed out longer than {@code narratorTabSpamSeconds} it starts reporting {@code
     * tabbed_out_long} on a steady interval so the narrator harasses you CONSTANTLY until you return. Detecting
     * window focus via GLFW is a normal query, not anything malware-like.
     *
     * <p>While active it also suppresses vanilla's {@code pauseOnLostFocus} (single-player pauses the sim the
     * instant the window loses focus, which would freeze the whole tab-out gag) — the player's original setting
     * is stashed and restored the moment the curse ends.
     */
    private static void tickNarratorClient(Minecraft mc, LocalPlayer player) {
        boolean focused = GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        boolean paused = mc.isPaused();
        boolean narratorActive = player.getData(WitchModAttachments.NARRATOR_ACTIVE) >= 0;
        // override pause-on-lost-focus while cursed (so tabbing out doesn't pause the sim and kill the gag).
        if (narratorActive) {
            if (narratorSavedPauseOnLostFocus == null) {
                narratorSavedPauseOnLostFocus = mc.options.pauseOnLostFocus;
                mc.options.pauseOnLostFocus = false;
            }
        } else if (narratorSavedPauseOnLostFocus != null) {
            mc.options.pauseOnLostFocus = narratorSavedPauseOnLostFocus;
            narratorSavedPauseOnLostFocus = null;
        }
        if (narratorActive) {
            if (narratorPrevFocused && !focused) {
                sendNarratorEvent("tabbed_out"); // just left
                narratorUnfocusedTicks = 0;
            } else if (!narratorPrevFocused && focused) {
                sendNarratorEvent("returned"); // just came back
                narratorUnfocusedTicks = 0;
            } else if (!focused) {
                narratorUnfocusedTicks++;
                int threshold = Config.NARRATOR_TAB_SPAM_SECONDS.get() * 20;
                int interval = Math.max(1, Config.NARRATOR_TAB_SPAM_INTERVAL_TICKS.get());
                if (narratorUnfocusedTicks >= threshold && narratorUnfocusedTicks % interval == 0) {
                    sendNarratorEvent("tabbed_out_long"); // stayed gone too long — constant harassment
                }
            } else if (!narratorPrevPaused && paused) {
                sendNarratorEvent("paused"); // an Esc-pause while still focused (not a tab-out)
            }
        } else {
            narratorUnfocusedTicks = 0;
        }
        narratorPrevFocused = focused;
        narratorPrevPaused = paused;
    }

    private static void sendNarratorEvent(String category) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.oliver.witchmod.network.WitchModNetwork.NarratorClientEventPayload(category));
    }

    /**
     * narrator: swinging at NOTHING — a left-click that hits no block and no entity fires this client-only event
     * ({@code LeftClickEmpty} is only raised on the client), reported so the narrator can mock the whiff.
     */
    @SubscribeEvent
    static void onNarratorWhiff(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity() instanceof LocalPlayer player
                && player.getData(WitchModAttachments.NARRATOR_ACTIVE) >= 0) {
            sendNarratorEvent("whiff");
        }
    }

    /** organised: a small stash button on the inventory screen (no keybind) when you carry the blessing. */
    @SubscribeEvent
    static void onInventoryScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getData(WitchModAttachments.ORGANISED_ACTIVE) < 0) {
            return;
        }
        // below the hotbar row (bottom of the panel), centred — clear of the effect display on the side.
        int x = screen.getGuiLeft() + 88 - 8;
        int y = screen.getGuiTop() + 166 + 4;
        OrganisedButton button = new OrganisedButton(x, y,
                b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.oliver.witchmod.network.WitchModNetwork.OpenOrganisedPayload()));
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.literal("Organised stash")));
        event.addListener(button);
    }

    /** organised: a "back to inventory" button on the stash screen (the GENERIC_9x1 chest titled "Stash"). */
    @SubscribeEvent
    static void onStashScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen)
                || !(screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu chest)
                || chest.getRowCount() != 1
                || !screen.getTitle().getString().equals("Stash")) {
            return;
        }
        net.minecraft.client.gui.components.Button back = net.minecraft.client.gui.components.Button.builder(
                net.minecraft.network.chat.Component.literal("◀ Inventory"),
                b -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(
                                mc.player.containerMenu.containerId));
                        mc.player.containerMenu = mc.player.inventoryMenu;
                        mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    }
                })
                .bounds(screen.getGuiLeft(), screen.getGuiTop() - 22, 90, 20)
                .build();
        event.addListener(back);
    }

    /**
     * spotlight: draw the rotating white double-helix pillar over every spotlit player LOCALLY (off the synced
     * {@code SPOTLIGHT_ACTIVE} flag), so the constant particle stream is a client concern and never floods the
     * server with particle packets. Starts above the head so it never blocks the victim's view.
     */
    private static void tickSpotlight(Minecraft mc) {
        if (mc.level == null || tickCounter % 2 != 0) {
            return;
        }
        double gap = Config.SPOTLIGHT_BEAM_GAP.get();
        double top = gap + Config.SPOTLIGHT_BEAM_HEIGHT.get();
        double spin = tickCounter * 0.16;
        double radius = 0.38;
        for (Player p : mc.level.players()) {
            if (p.getData(WitchModAttachments.SPOTLIGHT_ACTIVE) < 0) {
                continue;
            }
            for (double y = gap; y <= top; y += 0.5) {
                double angle = y * 1.15 + spin;
                for (int strand = 0; strand < 2; strand++) {
                    double a = angle + strand * Math.PI;
                    mc.level.addParticle(ParticleTypes.END_ROD,
                            p.getX() + Math.cos(a) * radius, p.getY() + y, p.getZ() + Math.sin(a) * radius, 0, 0, 0);
                }
            }
            if (mc.level.random.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.FIREWORK,
                        p.getX(), p.getY() + gap + mc.level.random.nextDouble() * (top - gap), p.getZ(), 0, 0.03, 0);
            }
        }
    }

    /**
     * amethyst Bell: render the "consume" purple-dust ramp on any caught player, CLIENT-side, off the synced
     * {@code AMETHYST_CONSUME_*} attachments — so the server sends no consume particles however many players a
     * ring catches. Progress is derived from the synced end-tick and the known ramp length.
     */
    private static void tickBellConsume(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            long end = p.getData(WitchModAttachments.AMETHYST_CONSUME_END);
            if (end <= now) {
                continue;
            }
            boolean fizzle = p.getData(WitchModAttachments.AMETHYST_CONSUME_FIZZLE) == 1;
            int duration = fizzle ? com.oliver.witchmod.blocks.AmethystBellEffects.fizzleRampTicks()
                    : com.oliver.witchmod.blocks.AmethystBellEffects.applyRampTicks();
            float progress = net.minecraft.util.Mth.clamp((duration - (end - now)) / (float) duration, 0F, 1F);
            com.oliver.witchmod.blocks.AmethystBellEffects.emitConsume(mc.level, p, progress, fizzle);
        }
    }

    /** true while the thirst bar has run low enough to cut sprinting off. */
    private static boolean isThirstSprintBlocked(LocalPlayer player) {
        int thirst = player.getData(WitchModAttachments.THIRST);
        return thirst >= 0 && thirst <= Config.THIRST_SPRINT_CUTOFF.get();
    }

    /** true while Gluttony's combined bar has run low enough to cut sprinting off. */
    private static boolean isGluttonySprintBlocked(LocalPlayer player) {
        int extra = player.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra < 0) {
            return false; // curse not active
        }
        return player.getFoodData().getFoodLevel() + extra <= Config.GLUTTONY_SPRINT_CUTOFF.get();
    }

    /**
     * gluttony draws BOTH hunger rows itself, so vanilla's own food bar is suppressed while it's active.
     * otherwise the lower row shows the real vanilla value, which is deliberately held one point short of
     * full to keep eating possible — so the bar permanently looked like it was missing its first point.
     * {@link GluttonyHudLayer} redraws that row from the true combined total instead.
     */
    @SubscribeEvent
    static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        // cutaway Gag: hide the gameplay HUD for a clean cinematic — but NOT via hideGui, which also hid chat
        // (where gag flavour text lives). Cancel the individual layers and leave CHAT + our overlay alone.
        if (cutawayCamActive && CUTAWAY_HIDDEN_LAYERS.contains(event.getName())) {
            event.setCanceled(true);
            return;
        }
        if (VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())
                && GluttonyHudLayer.shouldDrawCombinedBars(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    /** the vanilla HUD layers suppressed during a cutaway (chat + our cinematic overlay stay). */
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> CUTAWAY_HIDDEN_LAYERS = java.util.Set.of(
            VanillaGuiLayers.DEBUG_OVERLAY, VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.EFFECTS);

    private static float dwellerChaseRed; // eased red-overlay intensity during a Dweller chase

    /**
     * the Dweller: a subtle RED wash during a chase that fades in and scales with how close it is — a flat tint
     * plus a stronger top/bottom vignette that pulses as it bears down. Eased so it swells and recedes smoothly.
     */
    @SubscribeEvent
    static void onDwellerChaseOverlay(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            dwellerChaseRed = 0.0F;
            return;
        }
        float target = 0.0F;
        if (mc.player.getData(WitchModAttachments.DWELLER_ACTIVE) == 3) {
            double dist = 24.0;
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof com.oliver.witchmod.entities.SpaghettiManEntity d
                        && d.getVictim().map(u -> u.equals(mc.player.getUUID())).orElse(false)) {
                    dist = d.distanceTo(mc.player);
                    break;
                }
            }
            target = (float) net.minecraft.util.Mth.clamp(1.0 - dist / 22.0, 0.0, 1.0);
        }
        dwellerChaseRed += (target - dwellerChaseRed) * 0.08F; // smooth fade in/out
        if (dwellerChaseRed < 0.02F) {
            return;
        }
        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int flatA = (int) (dwellerChaseRed * 40.0F);              // faint full-screen tint
        int edgeA = (int) (dwellerChaseRed * 150.0F);             // stronger at the top/bottom edges
        int red = 0x00FF0000;
        int band = h / 3;
        g.fill(0, 0, w, h, (flatA << 24) | red);
        g.fillGradient(0, 0, w, band, (edgeA << 24) | red, red);          // top: red → transparent
        g.fillGradient(0, h - band, w, h, red, (edgeA << 24) | red);      // bottom: transparent → red
    }

    /** client-local jumpscare flash window (mimic reveal sets this directly, since it runs on the client). */
    private static long dwellerFlashLocalEnd;

    public static void triggerDwellerFlash(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            dwellerFlashLocalEnd = mc.level.getGameTime() + ticks;
        }
    }

    /**
     * the Dweller jumpscare FLASH: a bright white burst that snaps to full and fades over ~8 ticks (the "bright
     * lights"). The "then darkness" is a Darkness effect applied server-side (vanilla renders it). Driven by the
     * synced DWELLER_FLASH_END (server events like lunge) or the client-local window (the mimic reveal). The
     * flash window is a fixed ~8 ticks, so ticks-left maps straight to brightness.
     */
    @SubscribeEvent
    static void onDwellerFlash(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        long end = Math.max(mc.player.getData(WitchModAttachments.DWELLER_FLASH_END), dwellerFlashLocalEnd);
        long left = end - now;
        if (left <= 0 || left > 8) {
            return;
        }
        float a = Mth.clamp(left / 8.0F, 0.0F, 1.0F); // full white at the start, fading to nothing
        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * 255) << 24) | 0x00FFFFFF);
    }

    /** cutaway Gag: the letterbox bars + "Meanwhile…" title card overlay (drawn over the spectate view). */
    @SubscribeEvent
    static void onCutawayCinematic(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        if (!cutawayCamActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int bar = Math.max(14, h / 9); // small cinematic bars, top and bottom
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // family-Guy title card, held for the opening ~3s.
        if (mc.level.getGameTime() - cutawayStartTick < 60 && !cutawayTitle.isEmpty()) {
            int ty = h - bar - 34;
            g.drawCenteredString(mc.font, cutawayTitle, w / 2, ty, 0xFFFFFFFF);
            if (!cutawayVictimName.isEmpty()) {
                g.drawCenteredString(mc.font, "with " + cutawayVictimName, w / 2, ty + 12, 0xFFB0B0B0);
            }
        }
    }

    /** cutaway Gag: no first-person hand/held item while spectating — just a clean hover over the victim. */
    @SubscribeEvent
    static void onCutawayRenderHand(RenderHandEvent event) {
        if (cutawayCamActive) {
            event.setCanceled(true);
        }
    }

    /** nightowl: strip fog everywhere (distance, water, lava) by pushing the fog planes out of view. */
    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // the Dweller: a hard FLICKER — the lights go out for a beat (Shadow-Pass / Lights-Out) — slams the fog
        // right in, then it snaps back.
        long flicker = player.getData(WitchModAttachments.DWELLER_FLICKER);
        if (flicker != Long.MIN_VALUE && player.level().getGameTime() < flicker) {
            event.setNearPlaneDistance(0.1F);
            event.setFarPlaneDistance(2.5F);
            event.setCanceled(true);
            return;
        }
        // the Dweller: a murk that closes in GRADUALLY as the curse's dread rises — so you can never see far
        // enough to keep track of where it is. It's barely there early (the world looks fine, which makes a rare
        // glimpse far worse) and suffocates to ~8 blocks by the end. Only ever pulls the fog IN, never pushes it
        // out past vanilla.
        float dread = player.getData(WitchModAttachments.DWELLER_DREAD);
        if (dread > 0.02F) {
            float t = net.minecraft.util.Mth.clamp((dread - 0.05F) / 0.95F, 0.0F, 1.0F);
            t = t * t * t; // cubic ease-in — fog stays far for most of the climb, only closing in near max dread
            float far = net.minecraft.util.Mth.lerp(t, 110.0F, 8.0F);
            // during a CHASE, ease the murk back a bit so you can actually see it coming and route around it.
            if (player.getData(WitchModAttachments.DWELLER_ACTIVE) == 3) {
                far = Math.max(far, 18.0F);
            }
            if (far < event.getFarPlaneDistance()) {
                event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), 1.0F));
                event.setFarPlaneDistance(far);
                event.setCanceled(true);
            }
            return;
        }
        if (player.getData(WitchModAttachments.NIGHTOWL_ACTIVE) >= 0) {
            event.setNearPlaneDistance(-8.0F);
            event.setFarPlaneDistance(1_000_000.0F);
            event.setCanceled(true);
            return;
        }
        // holy water is clear, blessed water — see much further through it than murky ordinary water.
        if (player.getEyeInFluidType() == com.oliver.witchmod.blocks.WitchModFluids.PURIFYING_WATER_TYPE.get()) {
            event.setNearPlaneDistance(-4.0F);
            event.setFarPlaneDistance(48.0F);
            event.setCanceled(true);
        }
    }

    /**
     * the Dweller (deep tier): kills all music — jukebox records, the ambient background score, everything on
     * the MUSIC/RECORDS channels — so the only thing you ever hear is it. (The title screen can't be gated by a
     * per-player curse, so that's left alone.)
     */
    @SubscribeEvent
    static void onDwellerSilenceMusic(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getData(WitchModAttachments.DWELLER_ACTIVE) < 2) {
            return;
        }
        net.minecraft.client.resources.sounds.SoundInstance sound = event.getSound();
        if (sound != null && (sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.RECORDS)) {
            event.setSound(null);
        }
    }

    /** bedrock Moment (Nightcore bug): while the window is up, every sound plays back higher-pitched. */
    @SubscribeEvent
    static void onBedrockNightcore(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long end = player.getData(WitchModAttachments.BEDROCK_NIGHTCORE);
        if (end == Long.MIN_VALUE || player.level().getGameTime() >= end) {
            return;
        }
        net.minecraft.client.resources.sounds.SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof com.oliver.witchmod.client.NightcoreSoundInstance) {
            return;
        }
        event.setSound(new com.oliver.witchmod.client.NightcoreSoundInstance(sound, Config.BEDROCK_NIGHTCORE_PITCH.get().floatValue()));
    }

    /** delayedSound queue for the Bedrock "Sound Delay" bug — sounds we held back and will replay later. */
    private static final class DelayedSound {
        final net.minecraft.client.resources.sounds.SoundInstance sound;
        int ticks;
        DelayedSound(net.minecraft.client.resources.sounds.SoundInstance sound, int ticks) {
            this.sound = sound;
            this.ticks = ticks;
        }
    }
    private static final java.util.List<DelayedSound> bedrockDelayQueue = new java.util.ArrayList<>();
    private static final java.util.Set<net.minecraft.client.resources.sounds.SoundInstance> bedrockDelayPass =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** bedrock Moment: SILENT CREEPER (always on while cursed) + SOUND DELAY (windowed). */
    @SubscribeEvent
    static void onBedrockSoundBugs(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        net.minecraft.client.resources.sounds.SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }
        // silent creeper — swallow every creeper noise (the classic terror).
        if (player.getData(WitchModAttachments.BEDROCK_ACTIVE) >= 0 && sound.getLocation().getPath().contains("creeper")) {
            event.setSound(null);
            return;
        }
        // sound delay — hold one-shot sounds back and replay them a beat late.
        if (bedrockDelayPass.remove(sound)) {
            return; // this IS a replayed one — let it through
        }
        long end = player.getData(WitchModAttachments.BEDROCK_SOUND_DELAY);
        if (end != Long.MIN_VALUE && player.level().getGameTime() < end && !sound.isLooping() && bedrockDelayQueue.size() < 64) {
            bedrockDelayQueue.add(new DelayedSound(sound, Config.BEDROCK_SOUND_DELAY_AMOUNT.get()));
            event.setSound(null);
        }
    }

    /** bedrock Moment: PHANTOM DURABILITY — jittering durability bars over your hotbar. */
    @SubscribeEvent
    static void onBedrockPhantomDurability(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long end = player.getData(WitchModAttachments.BEDROCK_PHANTOM_DUR);
        if (end == Long.MIN_VALUE || player.level().getGameTime() >= end) {
            return;
        }
        net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int itemY = h - 19;
        for (int slot = 0; slot < 9; slot++) {
            if (!player.getInventory().getItem(slot).isDamageableItem()) {
                continue;
            }
            int itemX = w / 2 - 90 + slot * 20 + 2;
            int bx = itemX + 2;
            int by = itemY + 13;
            int fill = (int) (Math.random() * 14);
            int color = 0xFF000000 | (int) (Math.random() * 0x1000000);
            g.fill(bx, by, bx + 13, by + 2, 0xFF000000);
            g.fill(bx, by, bx + fill, by + 1, color);
        }
    }

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();

        // loading Screen: hold the view perfectly still. The tick handler also pins the real rotation, but
        // mouse-look is applied per FRAME, so without this the camera would visibly jitter between ticks.
        if (LoadingScreenState.isActive()) {
            event.setYaw(loadingLockedYaw);
            event.setPitch(loadingLockedPitch);
            return;
        }

        // cutaway Gag: hold the overhead shot's aim rock-steady between ticks (look is applied per frame).
        if (cutawayCamActive) {
            event.setYaw(cutawayLockedYaw);
            event.setPitch(cutawayLockedPitch);
            return;
        }

        if (!pacingCamActive || currentShot < 0 || currentShot >= shotYaw.length) {
            applyHeavyweightShake(mc, event);
            applyVertigo(mc, event);
            return;
        }
        // orbit shots pin an absolute yaw/pitch so the mouse can't nudge them; face shots leave yaw/pitch to
        // vanilla (which frames the ridden entity's face straight-on). Both get a dutch-angle roll for drama.
        if (!Float.isNaN(shotYaw[currentShot])) {
            event.setYaw(shotYaw[currentShot]);
            event.setPitch(shotPitch[currentShot]);
        }
        event.setRoll(shotRoll[currentShot]);
    }

    /**
     * heavyweight: rattles the view when the floor gives way. Vanilla has no screen shake at all, so this is
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
        // heavyweight's own collapse jolt...
        applyShake(event, now, mc.player.getData(WitchModAttachments.HEAVYWEIGHT_SHAKE_END),
                Config.HEAVYWEIGHT_SHAKE_TICKS.get(), Config.HEAVYWEIGHT_SHAKE_STRENGTH.get());
        // ...and Flat Footed's footstep shudder from a loud player nearby. Both stack additively.
        applyShake(event, now, mc.player.getData(WitchModAttachments.FLAT_FOOTED_SHAKE_END),
                Config.FLATFOOT_SHAKE_TICKS.get(), Config.FLATFOOT_SHAKE_STRENGTH.get());
        // ...and Thick Skinned's little "shrugged it off" jolt when a hit is neutralised.
        applyShake(event, now, mc.player.getData(WitchModAttachments.THICK_SKINNED_SHAKE_END),
                Config.THICKSKIN_SHAKE_TICKS.get(), Config.THICKSKIN_SHAKE_STRENGTH.get());
        // ...and Brute's smash jolt when you crash through a wall.
        applyShake(event, now, mc.player.getData(WitchModAttachments.BRUTE_SHAKE_END),
                Config.BRUTE_SHAKE_TICKS.get(), Config.BRUTE_SHAKE_STRENGTH.get());
        // ...and the Dweller's on-hit jolt (bang-behind / lunge / the finale).
        applyShake(event, now, mc.player.getData(WitchModAttachments.DWELLER_SHAKE_END),
                Config.DWELLER_SHAKE_TICKS.get(), Config.DWELLER_SHAKE_STRENGTH.get());
        // ...and Gladiator's parry jolt (strength is synced per outcome: perfect > normal > whiff).
        applyShake(event, now, mc.player.getData(WitchModAttachments.GLADIATOR_SHAKE_END),
                Config.GLADIATOR_SHAKE_TICKS.get(), mc.player.getData(WitchModAttachments.GLADIATOR_SHAKE_STRENGTH));
        // ...and the Voodoo Doll caster's pin/squeeze jolt.
        applyShake(event, now, mc.player.getData(WitchModAttachments.VOODOO_SHAKE_END),
                Config.VOODOO_SHAKE_TICKS.get(), Config.VOODOO_SHAKE_STRENGTH.get());
        // ...and the Amethyst Bell's subtle toll jolt for anyone nearby.
        applyShake(event, now, mc.player.getData(WitchModAttachments.AMETHYST_BELL_SHAKE_END),
                Config.BELL_SHAKE_TICKS.get(), Config.BELL_SHAKE_STRENGTH.get());
    }

    /** one decaying random jolt on the camera ANGLES (never the real rotation), shared by both shake sources. */
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

        // gladiator: while committed to a parry, pin the hotbar slot (revert any switch attempt).
        if (gladiatorLocked(player)) {
            if (gladiatorLockSlot < 0) {
                gladiatorLockSlot = player.getInventory().selected;
            }
            player.getInventory().selected = gladiatorLockSlot;
        } else {
            gladiatorLockSlot = -1;
        }

        // gladiator: when a parry imposed a weapon cooldown, set the CLIENT ticker so the indicator recharges.
        long weaponReady = player.getData(WitchModAttachments.GLADIATOR_WEAPON_READY);
        if (weaponReady != gladiatorWeaponReadySeen) {
            gladiatorWeaponReadySeen = weaponReady;
            long nowTick = mc.level.getGameTime();
            if (weaponReady > nowTick) {
                setClientAttackTicker(player, (int) player.getCurrentItemAttackStrengthDelay() - (int) (weaponReady - nowTick));
            }
        }

        // nightowl: force full-bright gamma while active, restoring the player's own gamma when it ends.
        tickNightowlBrightness(mc, player);

        // the Dweller: drain the world of colour while the curse is on you.
        tickDwellerShader(mc, player);

        // bedrock Moment (Chunk Rejection bug): force render distance right down so nearby chunks vanish.
        tickBedrockChunkReject(mc, player);

        // bedrock Moment: hotbar drift, perspective flip, split-screen POV steal, the ad popup, delayed audio.
        tickBedrockClientBugs(mc, player);

        // jesus: walk on the water surface (unless crouching, which drops you under).
        tickJesus(player);

        // bouncy: rubbery rebounds off floors/walls/ceilings (client-authoritative movement).
        tickBouncy(mc, player);

        // coyote: coyote-time late jump + edge magnetism to make parkour forgiving.
        tickCoyote(mc, player);

        // farmer's Spirit: draw the radius ring + motes on growable plants when near crops.
        tickFarmersSpirit(mc, player);

        // leader: draw the aura radius ring constantly.
        tickLeader(mc, player);

        // guardian Angel: render the allay's halo + movement trail CLIENT-side (off the synced marker).
        tickGuardianFx(mc);

        // amethyst Bell: render the "consume" purple-dust ramp CLIENT-side (off the synced consume attachment).
        tickBellConsume(mc);

        // builder: zero the place/break delays so you can build and tear down at click speed.
        tickBuilder(mc, player);

        // spider: climb walls when pushing into them (or cling while sneaking).
        tickSpider(mc, player);

        // ninja: double jump + swing woosh + smoke.
        tickNinja(mc, player);

        // ocean's Blessing: fast forward swim while in water (enhanced by Dolphin's Grace).
        tickOceansSwim(player);

        // delusions: spawn/retire the fake players and run the "am I being watched" timer.
        DelusionManager.clientTick(mc);

        // the Dweller's MIMIC: the single impostor wearing a real face that stares, then drops the mask.
        DwellerMimicManager.clientTick(mc);

        // the Dweller's BREATHING loop — start it when requested; the instance stops itself when the flag clears.
        if (mc.player.getData(WitchModAttachments.DWELLER_BREATHING) == 1
                && (dwellerBreathing == null || dwellerBreathing.isStopped())) {
            dwellerBreathing = new DwellerBreathingSound();
            mc.getSoundManager().play(dwellerBreathing);
        }

        // ugly: re-assert the swapped skin on every cursed player in view (and put faces back when cured).
        UglySkinManager.clientTick(mc);

        boolean minorActive = player.getData(WitchModAttachments.MINOR_INCONVENIENCE_ACTIVE) >= 0;
        boolean screensaverActive = player.getData(WitchModAttachments.SCREENSAVER_ACTIVE) >= 0;

        tickMinorInconvenience(mc, window, player, minorActive);

        // screensaver — episodic: shrink out of fullscreen, bounce for a while, grow back. See
        // screensaverState for the duty cycle and why it isn't continuous.
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
        tickLeftHanded(mc, player);
        tickIceSkates(player);
        tickSpotlight(mc);
        tickNarratorClient(mc, player);
        NarratorClient.tickSubtitle(mc);
        tickStickDriftCamera(player);
        tickPacingCamera(mc, player);
        tickCutaway(mc, player);
        tickCutawayLoop(mc, player);
    }

    /**
     * cutaway Gag: the looped SFX — Helicopter rotors (0) / abduction tractor beam (1) / annoying music (2) —
     * driven off the synced {@code CUTAWAY_LOOP} id. Uses a POSITIONAL instance that follows the victim
     * ({@link CutawayLoopSound}) so the sound comes from the event, not from the spectating watcher.
     */
    private static void tickCutawayLoop(Minecraft mc, LocalPlayer player) {
        int id = player.getData(WitchModAttachments.CUTAWAY_LOOP);
        if (id == cutawayLoopId && (id < 0 || cutawayLoopSound != null)) {
            return; // no change
        }
        if (cutawayLoopSound != null) {
            mc.getSoundManager().stop(cutawayLoopSound);
            cutawayLoopSound = null;
        }
        cutawayLoopId = id;
        if (id >= 0) {
            SoundEvent ev = switch (id) {
                case 0 -> WitchModSounds.CUTAWAY_HELICOPTER.get();
                case 1 -> WitchModSounds.CUTAWAY_TRACTORBEAM.get();
                default -> WitchModSounds.LOADING_MUSIC_GOOFY.get(); // annoying music placeholder
            };
            cutawayLoopSound = new CutawayLoopSound(ev, id);
            mc.getSoundManager().play(cutawayLoopSound);
        }
    }

    /**
     * cutaway Gag: while the synced {@code CUTAWAY_TARGET} points at a victim, hold a steady OVERHEAD shot of
     * them. The server has placed the (invisible) watcher body at a good vantage, so we keep the camera on
     * ourselves in first person and just pin the view to look at the victim — far more stable than a
     * third-person attach, which was the buggy "can't see the event" version. If the victim isn't loaded yet
     * we wait; on death we snap out immediately (dying while hijacked strobes against the respawn screen).
     */
    private static void tickCutaway(Minecraft mc, LocalPlayer player) {
        int id = player.getData(WitchModAttachments.CUTAWAY_TARGET);
        boolean spectating = id >= 0 && mc.level != null && !player.isDeadOrDying();
        if (spectating) {
            Entity victim = mc.level.getEntity(id);
            if (!cutawayCamActive) {
                // engage the cinematic AS SOON as the target is set — even before the victim's chunks finish
                // loading — so there's always an immediate cue (bars + title card) rather than a frozen "nothing".
                cutawaySavedCamera = mc.options.getCameraType();
                cutawayCamActive = true;
                cutawayStartTick = mc.level.getGameTime();
                cutawayTitle = CutawayTitles.pick(player.getRandom());
                cutawayVictimName = victim != null ? victim.getName().getString() : "";
            }
            // NOTE: we do NOT set hideGui — that hid the CHAT too, so gag flavour text (marriage vows, the
            // bouncer) never appeared. Instead the individual gameplay HUD layers are cancelled in
            // onRenderGuiLayer while chat + our cinematic overlay stay visible.
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
            }
            if (victim != null) {
                cutawayVictimName = victim.getName().getString();
                // A gag can point the camera at something OTHER than the victim (the marriage objector, the
                // exploding spouse…) via CUTAWAY_LOOK — for dramatic framing. Default (-1) aims at the victim.
                int lookId = player.getData(WitchModAttachments.CUTAWAY_LOOK);
                Entity look = lookId >= 0 ? mc.level.getEntity(lookId) : null;
                Entity aim = look != null ? look : victim;
                Vec3 eye = player.getEyePosition(1.0F);
                Vec3 tgt = aim.position().add(0.0, aim.getBbHeight() * 0.5, 0.0);
                double dx = tgt.x - eye.x;
                double dy = tgt.y - eye.y;
                double dz = tgt.z - eye.z;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                cutawayLockedYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                cutawayLockedPitch = (float) (-(Mth.atan2(dy, horiz) * (180.0 / Math.PI)));
                player.setYRot(cutawayLockedYaw);
                player.setXRot(cutawayLockedPitch);
                player.yHeadRot = cutawayLockedYaw;
                player.yBodyRot = cutawayLockedYaw;
            }
        } else if (cutawayCamActive) {
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
            }
            if (cutawaySavedCamera != null) {
                mc.options.setCameraType(cutawaySavedCamera);
            }
            cutawayCamActive = false;
        }
    }

    /**
     * builder: zero the two client-side cooldowns vanilla imposes between actions — {@code rightClickDelay}
     * (place/use) and {@code MultiPlayerGameMode.destroyDelay} (the pause after breaking a block) — so both
     * happen at click speed. Both fields are private with no setter, so this reflects into them (cached),
     * degrading gracefully if the fields ever can't be resolved.
     */
    private static void tickBuilder(Minecraft mc, LocalPlayer player) {
        if (player.getData(WitchModAttachments.BUILDER_ACTIVE) < 0) {
            return;
        }
        if (!builderReflectInit) {
            builderReflectInit = true;
            try {
                rightClickDelayField = Minecraft.class.getDeclaredField("rightClickDelay");
                rightClickDelayField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                rightClickDelayField = null;
            }
            try {
                destroyDelayField = net.minecraft.client.multiplayer.MultiPlayerGameMode.class.getDeclaredField("destroyDelay");
                destroyDelayField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                destroyDelayField = null;
            }
        }
        try {
            if (rightClickDelayField != null) {
                rightClickDelayField.setInt(mc, 0);
            }
            if (destroyDelayField != null && mc.gameMode != null) {
                destroyDelayField.setInt(mc.gameMode, 0);
            }
        } catch (ReflectiveOperationException ignored) {
            // give up quietly — the field couldn't be written this tick
        }
    }

    /**
     * ocean's Blessing: a strong forward push while swimming, so water travel is fast. Client-authoritative
     * movement, so it's applied here off the synced flag; Dolphin's Grace multiplies it, and the boost is
     * capped so you can't accelerate without limit.
     */
    private static void tickOceansSwim(LocalPlayer player) {
        if (player.getData(WitchModAttachments.OCEANS_ACTIVE) < 0 || !player.isInWater() || player.zza <= 0.0F) {
            return; // only while active, wet, and actually swimming forward
        }
        Vec3 v = player.getDeltaMovement();
        if (v.length() >= Config.OCEANS_MAX_SPEED.get()) {
            return;
        }
        double boost = Config.OCEANS_SWIM_BOOST.get();
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE)) {
            boost *= Config.OCEANS_DOLPHIN_MULT.get();
        }
        player.setDeltaMovement(v.add(player.getLookAngle().scale(boost)));
    }

    /**
     * berserker: an air-swing (nothing in reach) is a MISS, and misses reset the frenzy. Whether a swing
     * connected is client-authoritative, so the client reports the miss and the server resets the stacks.
     */
    @SubscribeEvent
    static void onBerserkerMiss(PlayerInteractEvent.LeftClickEmpty event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getData(WitchModAttachments.BERSERKER_ACTIVE) >= 0) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.oliver.witchmod.network.WitchModNetwork.BerserkerMissPayload());
        }
    }

    /**
     * forgiveness: a melee swing that MISSED (nothing in vanilla's pick) still connects if a mob's ~40%-bigger
     * hitbox was on your aim line — enlarged hitboxes, but only FOR YOU. Especially forgiving on tiny/baby mobs.
     * runs on the miss (LeftClickEmpty), does an inflated raycast, and attacks the best candidate itself.
     */
    @SubscribeEvent
    static void onForgivenessAssist(PlayerInteractEvent.LeftClickEmpty event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null
                || player.getData(WitchModAttachments.FORGIVENESS_ACTIVE) < 0) {
            return;
        }
        net.minecraft.world.entity.Entity target = forgivenessPick(mc, player);
        if (target != null) {
            mc.gameMode.attack(player, target);
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /**
     * blessing of Speed: keep only a SLIGHT sprint-FOV zoom rather than the big one the boosted movement speed
     * would produce — so sprinting still reads as fast without the nauseating full zoom for that speed.
     */
    @SubscribeEvent
    static void onSpeedFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        if (event.getPlayer().getData(WitchModAttachments.SPEED_ACTIVE) >= 0) {
            float natural = event.getNewFovModifier();
            if (natural > 1.0F) { // only while sprinting (the zoom-out)
                event.setNewFovModifier(1.0F + (natural - 1.0F) * 0.3F); // keep ~30% of the sprint zoom
            }
        }
    }

    /** flight + elytra: a slight FOV zoom-out while sprint-gliding, for the sense of speed the push gives. */
    @SubscribeEvent
    static void onFlightFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        if (event.getPlayer().getData(WitchModAttachments.FLIGHT_ACTIVE) >= 1
                && event.getPlayer().isFallFlying() && event.getPlayer().isSprinting()) {
            event.setNewFovModifier(event.getNewFovModifier() * Config.FLIGHT_ELYTRA_FOV.get().floatValue());
        }
    }

    private static net.minecraft.world.entity.Entity forgivenessPick(Minecraft mc, LocalPlayer player) {
        double reach = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(reach));
        net.minecraft.world.phys.AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        double base = com.oliver.witchmod.Config.FORGIVENESS_HITBOX_INFLATE.get();
        net.minecraft.world.entity.Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntities(player, search,
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e.isPickable() && e != player && !e.isSpectator())) {
            double inf = Math.max(base, e.getBbWidth() * 0.2); // ~40% wider, floor helps tiny/baby mobs
            var hit = e.getBoundingBox().inflate(inf).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /**
     * drives the fake load and pins the player's actual rotation, so mouse movement during it is discarded
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
     * bad Swimmer's constant pull. The curse's other half is a gravity attribute, but vanilla skips fluid
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
            // dead_weight synergy: Dense (heavy anchor) + Bad Swimmer drops you like a stone.
            if (player.getData(WitchModAttachments.HEAVY_ACTIVE) >= 0) {
                pull *= Config.DEAD_WEIGHT_PULL_MULT.get();
            }
            if (!wasInLiquid) {
                pull += Config.BAD_SWIMMER_ENTRY_PLUNGE.get(); // the yank as you break the surface
            }
            player.setDeltaMovement(player.getDeltaMovement().subtract(0.0, pull, 0.0));

            // you never learned the stroke: the SWIMMING pose is off the table entirely, which is what
            // separates this from Heavy (that one just makes you sink; this one makes you unable to swim).
            // vanilla only enters the pose when isSprinting() is true in water, so killing the sprint at
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
     * minor Inconvenience: you cannot go fullscreen, and your window is named something stupid every so
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
        }

        // refuse fullscreen, and keep the window MAXIMISED so it still fills the whole monitor (the renamed
        // title bar is invisible in fullscreen, and a small windowed size would be a real handicap). Setting
        // the OPTION runs vanilla's own change callback, which does the toggle — calling toggleFullScreen() as
        // well double-toggles straight back.
        if (window.isFullscreen()) {
            mc.options.fullscreen().set(false);
        } else {
            // exiting fullscreen makes vanilla RESTORE the previous (small) windowed size, one frame AFTER we
            // asked — so a one-shot maximise gets overwritten and you end up seeing the desktop. Re-assert the
            // maximise each tick until it actually sticks (checking the GLFW attribute so it isn't spammed).
            // skipped while Screensaver is bouncing the window, so the two curses don't fight.
            boolean screensaverBouncing = player.getData(WitchModAttachments.SCREENSAVER_ACTIVE) >= 0;
            if (!screensaverBouncing && !isMaximised(window)) {
                GLFW.glfwMaximizeWindow(window.getWindow());
            }
        }

        if (tickCounter >= nextRenameTick) {
            window.setTitle(WindowTitles.pick(player.getRandom()));
            int min = Config.MINOR_RENAME_MIN.get();
            int max = Math.max(min, Config.MINOR_RENAME_MAX.get());
            nextRenameTick = tickCounter + min + player.getRandom().nextInt(max - min + 1);
        }
    }

    private static boolean isMaximised(Window window) {
        return GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
    }

    /**
     * heavy: you sink like an anchor. Client-side for the same reason Bad Swimmer's pull is — player movement
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
            // cut to a new shot every PACING_SHOT_TICKS, looping through the framings if the moment is long.
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

    /** sets up the cinematic: resolves the victim, builds the shot list, starts the theme. */
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

    /** restores the player's own camera and perspective, and cuts the theme dead. */
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

    /** points the camera for one cut, kills any inherited mob shader, and clicks. */
    private static void applyShot(Minecraft mc, LocalPlayer player, int shot) {
        Entity entity = shotEntity[shot];
        if (entity == null || !entity.isAlive()) {
            entity = pacingFocus != null ? pacingFocus : player;
        }
        mc.setCameraEntity(entity);
        mc.options.setCameraType(shotFront[shot] ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        // riding a mob loads its vision shader (spider/creeper/enderman); kill it immediately. checkEntityPostEffect
        // only re-runs on the next setCameraEntity, so this stays off for the whole shot.
        if (!(entity instanceof Player)) {
            mc.gameRenderer.shutdownEffect();
        }
        playCutSound(player); // a click between cuts... or, rarely, the truth
    }

    /**
     * precomputes the shot list: the first 2–4 cuts are the victim (orbit angles), then it cuts to nearby
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

        // nearby frozen entities, nearest first, each a front-view face zoom. Shuffled so repeat loops don't
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
