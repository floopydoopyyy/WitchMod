package com.oliver.witchmod.client;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;

/**
 * One fake player: a {@link RemotePlayer} that exists ONLY in this client's {@code ClientLevel}, wearing a
 * real online player's skin and nametag (master-spec Delusions).
 *
 * <p><b>Skin and name come free from vanilla, via two different routes.</b> The nametag is just
 * {@code Player.getName()}, which returns the GameProfile's name — so the profile is built with the mirrored
 * player's name but a FRESH random UUID, because reusing their real UUID would collide with the genuine
 * entity in the level's uuid lookup. The skin then can't come from the UUID any more, so {@link #getSkin()}
 * is overridden to hand back the mirrored {@code PlayerInfo}'s skin directly. That one override also settles
 * the body type: {@code EntityRenderDispatcher.getRenderer} picks the slim or wide player renderer from
 * {@code getSkin().model()}.
 *
 * <p><b>Movement is vanilla's, driven by fake key presses — nothing here is hand-rolled.</b> Each behaviour
 * sets only what a real player's inputs set: {@code zza}/{@code xxa} (WASD, as a throttle and a strafe), the
 * look angles (the mouse), {@code jumping}, sneak and sprint. {@link #aiStep()} then hands those to vanilla's
 * own {@code travel()}, which is what supplies acceleration, friction, inertia, gravity, terminal velocity,
 * the 0.6-block step-up, collision and fluid handling. Speed is never a number in this file: it comes from
 * {@code Attributes.MOVEMENT_SPEED}, and {@code setSprinting(true)} applies vanilla's own +30% sprint
 * modifier, so a delusion physically cannot move at a speed a real player couldn't.
 *
 * <p>That last point is load-bearing. An earlier version set the velocity directly and called {@code move()},
 * which meant no acceleration ramp, no friction, gravity that had to be reimplemented, and step-up that only
 * sometimes applied — they hit top speed instantly, skated over gaps and snagged on corners. Feeding vanilla
 * the inputs instead of the answer fixed all of it at once.
 *
 * <p>The one genuinely hand-written piece is the head/body split: real players' bodies lag their head and
 * snap when the twist passes ~50 degrees, and without that a delusion reads as a rotating statue.
 */
public final class DelusionPlayer extends RemotePlayer {
    /**
     * Fake entity ids are handed out from the top of the int range downwards. Client-created entities draw
     * from the same counter the server's ids land in, and {@code ClientLevel.addEntity} DISCARDS whatever
     * already holds an id — so a low id risks a delusion silently deleting a real entity (or vice versa).
     */
    private static final AtomicInteger FAKE_IDS = new AtomicInteger(Integer.MAX_VALUE - 1);

    /** Throttles, as a fraction of "holding W". Actual speed comes from the movement-speed attribute. */
    private static final float FULL_THROTTLE = 1.0F;
    private static final float SNEAK_THROTTLE = 0.3F;      // vanilla's own slow-movement factor
    private static final float FLY_THROTTLE = 0.6F;

    private static final int JUMP_COOLDOWN_TICKS = 10;     // vanilla's noJumpDelay, which is private
    private static final int STUCK_WINDOW_TICKS = 12;
    private static final double STUCK_MIN_TRAVEL = 0.6;    // blocks covered over the window before we care

    private static final float BODY_YAW_LIMIT = 50.0F;
    private static final float BODY_TURN_RATE = 9.0F;
    private static final float LOOK_TURN_RATE = 12.0F;

    /** The behaviours. REALISATION is never rolled — it's earned by being watched. */
    private enum State {
        IDLE, WANDERING, SPRINTING, SNEAKING, MINING, PUNCHING, WAVING,
        JUMPING, DANCING, TWERKING, SPINNING, FLYING, TELEPORTING, OBSERVING, REALISATION
    }

    /** Everything except REALISATION, weighted so the mundane ones carry the illusion. */
    private static final State[] ROLLABLE = {
            State.WANDERING, State.WANDERING, State.WANDERING,
            State.IDLE, State.IDLE,
            State.SPRINTING, State.SPRINTING,
            State.MINING, State.MINING,
            State.SNEAKING,
            State.PUNCHING,
            State.JUMPING,
            State.TELEPORTING,
            State.WAVING,
            State.DANCING,
            State.TWERKING,
            State.SPINNING,
            State.FLYING,
            State.OBSERVING, State.OBSERVING,
    };

    private final PlayerInfo mirrored;
    private final RandomSource rng;

    private State state = State.IDLE;
    private int stateTicks;
    private int stateDuration = 40;
    private int lifeTicks;

    /** Builds while the victim has this one in view, decays when they look away. */
    private double seenScore;
    private int realisationRollCooldown;

    // The "mouse": where it's looking, and where it's easing to.
    private float lookYaw;
    private float lookPitch;
    private float wishYaw;
    private float wishPitch;

    // The "keyboard" for this tick.
    private float headingYaw;
    private float throttle;
    private boolean wishSprint;
    private boolean wishJump;
    private int flyClimb;

    private int jumpCooldown;
    private int stuckTicks;
    private Vec3 stuckAnchor = Vec3.ZERO;
    private int lastAnimatedTick = -1;

    // Sporadic-movement state: players don't hold W in a straight line for twenty seconds.
    private int pauseTicks;
    private int burstTicks;

    private BlockPos miningPos;
    private int miningStage;

    private int realisePhase;
    private boolean charging;

    public DelusionPlayer(ClientLevel level, PlayerInfo mirrored, long seed) {
        super(level, new GameProfile(UUID.randomUUID(), mirrored.getProfile().getName()));
        this.mirrored = mirrored;
        this.rng = RandomSource.create(seed);
        setId(FAKE_IDS.getAndDecrement());
        // RemotePlayer's constructor turns physics off (real ones are positioned entirely by packets). These
        // walk around under their own steam, so it goes back on. Player.tick re-asserts it every tick anyway,
        // since isSpectator() is false here, so travel() always collides properly.
        this.noPhysics = false;
        setInvulnerable(true);
        // Which skin overlay layers to draw is normally SYNCED from the server, and its default is 0 — so
        // without this every delusion renders with no hat, jacket or sleeve layer, i.e. visibly bald and
        // wearing the wrong clothes. 0x7F turns on all seven PlayerModelParts.
        getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
    }

    /** The whole point: this fake wears a real player's skin, and with it their slim/wide body type. */
    @Override
    public PlayerSkin getSkin() {
        return mirrored.getSkin();
    }

    /**
     * Never let vanilla's crosshair pick one. If it did, attacking would send the SERVER an interact packet
     * naming an entity id it has never heard of — the swing is ray-traced against delusions separately in
     * {@link DelusionManager} instead.
     */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** They'd shove the real player around, which the server would immediately correct. */
    @Override
    protected void pushEntities() {
    }

    /**
     * <b>Required, or they do not move at all.</b> {@code LivingEntity.travel} wraps its ENTIRE body in this
     * check, and the default resolves to {@code !level.isClientSide} — false for anything client-side. Real
     * remote players don't care, because their positions arrive in packets rather than being simulated; a
     * delusion is simulated wholly and only by this client, so it genuinely IS locally controlled and has to
     * say so. Without it {@code travel()} is called every tick and silently does nothing.
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return true;
    }

    /**
     * <b>Guarded to once per tick, or the limbs swing at double speed.</b> {@code LivingEntity.travel} ends
     * by calling this itself, and {@code RemotePlayer.tick} then calls it a second time. For a genuine remote
     * player that's harmless — their {@code travel()} never runs, which is exactly WHY RemotePlayer has that
     * line — but a delusion runs both, and {@code WalkAnimationState.update} advances the limb phase on every
     * call. Two calls per tick means arms and legs cycling twice as fast as the distance covered warrants,
     * which is an instant tell. Whichever call lands first wins; the second is dropped.
     */
    @Override
    public void calculateEntityAnimation(boolean includeHeight) {
        if (lastAnimatedTick == tickCount) {
            return;
        }
        lastAnimatedTick = tickCount;
        super.calculateEntityAnimation(includeHeight);
    }

    /** The mirrored player's gamemode is none of this fake's business — it must always render solid. */
    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public void tick() {
        LocalPlayer victim = Minecraft.getInstance().player;
        if (victim == null) {
            return;
        }
        lifeTicks++;
        stateTicks++;

        throttle = 0.0F;
        wishSprint = false;
        wishJump = false;
        flyClimb = 0;

        if (state != State.REALISATION && stateTicks >= stateDuration) {
            pickState();
        }
        runState(victim);
        climbObstacles();
        applyRotation();
        applyInputs();

        super.tick();   // vanilla ticks the player, aiStep below does the actual moving

        checkStuck();
    }

    /**
     * Vanilla's movement, minus the parts that only make sense for a genuinely remote player.
     *
     * <p>{@code RemotePlayer.aiStep} exists to interpolate toward positions the server sent, and never calls
     * {@code travel()} at all — so inheriting it means no physics whatsoever. {@code LivingEntity.aiStep}
     * can't simply be called in its place either: on the client it damps velocity by 0.98 every tick (the
     * drift correction for entities being lerped from packets), which would quietly make every delusion
     * slower than the real thing. So this runs the parts that matter — jump, then {@code travel()} — and
     * lets vanilla own acceleration, friction, gravity, step-up and collision.
     */
    @Override
    public void aiStep() {
        this.oBob = this.bob;
        this.updateSwingTime();

        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
        if (this.jumping && this.onGround() && jumpCooldown == 0) {
            this.jumpFromGround();
            jumpCooldown = JUMP_COOLDOWN_TICKS;
        }
        // Creative-style flight gets its lift the same way LocalPlayer does — a direct nudge before travel,
        // which Player.travel then decays by 0.6 each tick.
        if (getAbilities().flying && flyClimb != 0) {
            setDeltaMovement(getDeltaMovement().add(0.0, flyClimb * getAbilities().getFlyingSpeed() * 3.0, 0.0));
        }

        this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
        this.travel(new Vec3(this.xxa, this.yya, this.zza));

        float ground = this.onGround() && !this.isDeadOrDying()
                ? (float) Math.min(0.1, this.getDeltaMovement().horizontalDistance())
                : 0.0F;
        this.bob = this.bob + (ground - this.bob) * 0.4F;
    }

    /**
     * Turns "walk toward {@code headingYaw} while looking at {@code lookYaw}" into the strafe/forward pair a
     * real player would be holding, since {@code moveRelative} rotates the input by the entity's yaw.
     */
    private void applyInputs() {
        setSprinting(wishSprint);
        setJumping(wishJump);

        if (throttle <= 0.001F) {
            this.xxa = 0.0F;
            this.zza = 0.0F;
            return;
        }
        double offset = Math.toRadians(Mth.wrapDegrees(headingYaw - getYRot()));
        this.zza = (float) (Math.cos(offset) * throttle);
        this.xxa = (float) (-Math.sin(offset) * throttle);
    }

    /**
     * Terrain handling, and the reason they stop snagging on hillsides. Vanilla's step-up only clears 0.6 of
     * a block, so a full block — a hill, a ledge, a single cobble someone left — stops a walker dead. Players
     * don't stop, they jump, and they start the jump BEFORE they're touching the thing.
     *
     * <p>So this fires on either signal: a look-ahead that spots a full block in the way with clear air above
     * it, and vanilla's own {@code horizontalCollision} from last tick as the backstop for anything the
     * look-ahead misses (corners, fences, odd shapes).
     */
    private void climbObstacles() {
        if (throttle <= 0.001F || getAbilities().flying || !onGround()) {
            return;
        }
        if (this.horizontalCollision || blockedAhead()) {
            wishJump = true;
        }
    }

    /** A full block at foot height in the direction of travel, with room to land on top of it. */
    private boolean blockedAhead() {
        double rad = Math.toRadians(headingYaw);
        double ahead = 0.7;
        double x = getX() - Math.sin(rad) * ahead;
        double z = getZ() + Math.cos(rad) * ahead;

        BlockPos feet = BlockPos.containing(x, getY() + 0.1, z);
        BlockPos head = feet.above();
        BlockPos clearance = feet.above(2);
        return !isPassable(feet) && isPassable(head) && isPassable(clearance);
    }

    private boolean isPassable(BlockPos pos) {
        return level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    /**
     * Last resort. If a run of jumping and shoving hasn't actually got us anywhere over the whole window,
     * it's not a step — it's a wall — so give up on this direction and head somewhere else.
     */
    private void checkStuck() {
        if (throttle <= 0.001F || getAbilities().flying) {
            stuckTicks = 0;
            stuckAnchor = position();
            return;
        }
        stuckTicks++;
        if (stuckTicks < STUCK_WINDOW_TICKS) {
            return;
        }
        double covered = position().subtract(stuckAnchor).horizontalDistance();
        stuckTicks = 0;
        stuckAnchor = position();
        if (covered < STUCK_MIN_TRAVEL) {
            headingYaw += 90.0F + rng.nextFloat() * 180.0F;
            wishYaw = headingYaw;
        }
    }

    // --- The behaviours --------------------------------------------------------------------------------

    private void pickState() {
        enter(ROLLABLE[rng.nextInt(ROLLABLE.length)]);
    }

    private void enter(State next) {
        clearMining();
        setShiftKeyDown(false);
        setPose(Pose.STANDING);
        getAbilities().flying = false;
        stuckTicks = 0;
        stuckAnchor = position();
        pauseTicks = 0;
        burstTicks = 0;

        state = next;
        stateTicks = 0;
        int base = Config.DELUSIONS_STATE_SWAP_INTERVAL.get();
        stateDuration = switch (next) {
            case TELEPORTING -> 20 + rng.nextInt(40);
            case WAVING -> 30 + rng.nextInt(40);
            case SPINNING -> 30 + rng.nextInt(50);
            case OBSERVING -> 140 + rng.nextInt(180);   // a long, patient stare
            case REALISATION -> Integer.MAX_VALUE;   // driven by its own phases
            default -> base / 2 + rng.nextInt(base);
        };

        if (next == State.WANDERING || next == State.SPRINTING || next == State.SNEAKING) {
            headingYaw = rng.nextFloat() * 360.0F;
            wishYaw = headingYaw;
        }
        if (next == State.FLYING) {
            getAbilities().flying = true;
        }
        if (next == State.REALISATION) {
            realisePhase = 0;
            charging = rng.nextInt(100) < Config.DELUSIONS_CHARGE_CHANCE.get();
        }
    }

    private void runState(LocalPlayer victim) {
        switch (state) {
            case IDLE -> tickIdle();
            case WANDERING -> tickWalk(false);
            case SPRINTING -> tickWalk(true);
            case SNEAKING -> tickSneak();
            case MINING -> tickMining();
            case PUNCHING -> tickPunching(victim);
            case WAVING -> tickWaving(victim);
            case JUMPING -> tickJumping();
            case DANCING -> tickDancing();
            case TWERKING -> tickTwerking(victim);
            case SPINNING -> tickSpinning();
            case FLYING -> tickFlying();
            case TELEPORTING -> tickTeleporting();
            case OBSERVING -> tickObserving(victim);
            case REALISATION -> tickRealisation(victim);
        }
    }

    /** Standing about, glancing around now and then — the small mouse movements nobody makes on purpose. */
    private void tickIdle() {
        if (stateTicks % (20 + rng.nextInt(40)) == 0) {
            wishYaw = lookYaw + (rng.nextFloat() - 0.5F) * 120.0F;
            wishPitch = (rng.nextFloat() - 0.5F) * 40.0F;
        }
    }

    /**
     * Walking, but never in a clean line. Nobody holds W for twenty seconds: they stop to look at something,
     * break into a jog, hop over nothing in particular, and drift off course constantly. The irregularity is
     * doing as much work here as the speed is.
     */
    private void tickWalk(boolean sprint) {
        // Stopped to look at something.
        if (pauseTicks > 0) {
            pauseTicks--;
            wishPitch = (float) Math.sin(stateTicks / 9.0) * 14.0F;
            return;
        }
        if (rng.nextInt(150) == 0) {
            pauseTicks = 6 + rng.nextInt(22);
            return;
        }

        throttle = FULL_THROTTLE;
        // An unprompted jog, even mid-walk — and a walker who breaks into one is very hard to read as fake.
        if (burstTicks > 0) {
            burstTicks--;
            wishSprint = true;
        } else {
            wishSprint = sprint;
            if (!sprint && rng.nextInt(220) == 0) {
                burstTicks = 20 + rng.nextInt(45);
            }
        }
        // A hop over nothing, the way people idly do.
        if (rng.nextInt(280) == 0) {
            wishJump = true;
        }

        // A course correction every couple of seconds — real players don't hold one heading forever.
        if (stateTicks % (wishSprint ? 60 : 40) == 0) {
            headingYaw += (rng.nextFloat() - 0.5F) * (wishSprint ? 40.0F : 80.0F);
        }
        // The head drifts a little off the direction of travel, as though watching the scenery go past.
        wishYaw = headingYaw + (float) Math.sin(stateTicks / 22.0) * 12.0F;
        wishPitch = (float) Math.sin(stateTicks / 31.0) * 6.0F;
        footsteps(wishSprint ? 6 : 9);
        if (wishSprint) {
            sprintDust();
        }
    }

    private void tickSneak() {
        setShiftKeyDown(true);
        setPose(Pose.CROUCHING);
        throttle = SNEAK_THROTTLE;
        if (stateTicks % 50 == 0) {
            headingYaw += (rng.nextFloat() - 0.5F) * 70.0F;
        }
        wishYaw = headingYaw;
        footsteps(14);
    }

    /**
     * Chipping away at a block: the arm swings on the vanilla mining cadence, the block's own hit sound
     * plays, and the cracking overlay really does creep across it — {@code destroyBlockProgress} is a
     * client-side renderer call, so the illusion costs the world nothing.
     */
    private void tickMining() {
        if (miningPos == null) {
            miningPos = findMinableBlock();
            if (miningPos == null) {
                enter(State.WANDERING);
                return;
            }
            miningStage = 0;
        }
        BlockState mined = level().getBlockState(miningPos);
        if (mined.isAir()) {
            clearMining();
            return;
        }
        faceBlock(miningPos);

        if (stateTicks % 5 == 0) {
            swing(InteractionHand.MAIN_HAND);
            playAt(mined.getSoundType(level(), miningPos, this).getHitSound(), 0.25F, 0.5F);
        }
        // Crack it open over ~2s, break it, then start on it again — as if working through a seam.
        if (stateTicks % 8 == 0) {
            miningStage++;
            if (miningStage > 9) {
                playAt(mined.getSoundType(level(), miningPos, this).getBreakSound(), 0.8F, 0.9F);
                breakParticles(miningPos, mined);
                clearMining();
                return;
            }
            Minecraft.getInstance().levelRenderer.destroyBlockProgress(getId(), miningPos, miningStage);
        }
    }

    private void tickPunching(LocalPlayer victim) {
        if (stateTicks % 40 < 20 && distanceToSqr(victim) < 400.0) {
            faceEntity(victim);
        } else if (stateTicks % 30 == 0) {
            wishYaw = lookYaw + (rng.nextFloat() - 0.5F) * 90.0F;
        }
        if (stateTicks % 12 == 0) {
            swing(InteractionHand.MAIN_HAND);
            playAt(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.6F, 0.95F + rng.nextFloat() * 0.1F);
        }
    }

    /** Locked onto you, arm going like a metronome. There is no wave animation — this IS how players wave. */
    private void tickWaving(LocalPlayer victim) {
        faceEntity(victim);
        if (stateTicks % 4 == 0) {
            swing(InteractionHand.MAIN_HAND);
        }
    }

    private void tickJumping() {
        wishJump = true;
        if (stateTicks % 30 == 0) {
            wishYaw = lookYaw + (rng.nextFloat() - 0.5F) * 60.0F;
        }
    }

    /** Strafe-spam plus jumps and a swinging camera — the universal "I'm bored in a lobby" dance. */
    private void tickDancing() {
        headingYaw = lookYaw + ((stateTicks / 6) % 2 == 0 ? 90.0F : -90.0F);
        throttle = FULL_THROTTLE;
        if (stateTicks % 14 == 0) {
            wishJump = true;
        }
        if (stateTicks % 8 == 0) {
            wishYaw = lookYaw + (rng.nextFloat() - 0.5F) * 40.0F;
        }
        if (stateTicks % 10 == 0) {
            swing(InteractionHand.MAIN_HAND);
        }
    }

    /** Crouch spam, aimed squarely at you. Authenticity demanded it. */
    private void tickTwerking(LocalPlayer victim) {
        faceEntity(victim);
        boolean down = (stateTicks / 3) % 2 == 0;
        setShiftKeyDown(down);
        setPose(down ? Pose.CROUCHING : Pose.STANDING);
    }

    /** Someone yanking the mouse in a circle. The body chases the head, which is what sells it. */
    private void tickSpinning() {
        lookYaw += 24.0F + rng.nextFloat() * 10.0F;
        wishYaw = lookYaw;
        wishPitch = (float) Math.sin(stateTicks / 8.0) * 20.0F;
    }

    /** Drifting along a few blocks up, holding jump or sneak to hold its height, exactly like creative flight. */
    private void tickFlying() {
        getAbilities().flying = true;
        throttle = FLY_THROTTLE;

        double desired = groundHeightBelow() + 4.0 + Math.sin(stateTicks / 25.0) * 1.5;
        double error = desired - getY();
        flyClimb = error > 0.4 ? 1 : (error < -0.4 ? -1 : 0);

        if (stateTicks % 45 == 0) {
            headingYaw += (rng.nextFloat() - 0.5F) * 90.0F;
        }
        wishYaw = headingYaw;
    }

    /**
     * Closes the distance and then just... watches. Deliberately the quietest state in the set: it walks in
     * at an ordinary pace with ordinary footsteps, and once it's near enough it stops dead and does nothing
     * at all — no swinging, no fidgeting, no sound. Everything else a delusion does is a person being
     * oblivious to you; this is the one that isn't.
     */
    private void tickObserving(LocalPlayer victim) {
        faceEntity(victim);
        double want = Config.DELUSIONS_OBSERVE_DISTANCE.get();
        if (distanceToSqr(victim) > want * want) {
            headingYaw = lookYaw;
            throttle = FULL_THROTTLE;
            footsteps(9);
        }
        // Otherwise: stand perfectly still and stare. The stillness IS the behaviour.
    }

    /** A couple of pearl-style hops, with the sound and particles at BOTH ends so it reads as a teleport. */
    private void tickTeleporting() {
        if (stateTicks % 14 != 0) {
            return;
        }
        puff(8);
        playAt(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.0F);

        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = 4.0 + rng.nextDouble() * 8.0;
            double nx = getX() + Math.cos(angle) * dist;
            double nz = getZ() + Math.sin(angle) * dist;
            Double ny = standableY(nx, nz, getY());
            if (ny != null) {
                setPos(nx, ny, nz);
                setDeltaMovement(Vec3.ZERO);
                // Without this it INTERPOLATES across the gap and slides there like a ghost on rails.
                setOldPosAndRot();
                stuckAnchor = position();
                break;
            }
        }
        puff(8);
        playAt(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.0F);
    }

    /**
     * It knows. Turn (fast, but not a snap — a snap looks like a teleport), stare, then either evaporate
     * where it stands or come straight at you.
     */
    private void tickRealisation(LocalPlayer victim) {
        faceEntity(victim);
        switch (realisePhase) {
            case 0 -> {
                if (stateTicks >= 8) {
                    realisePhase = 1;
                    stateTicks = 0;
                    stateDuration = 30 + rng.nextInt(70);
                }
            }
            case 1 -> {
                if (stateTicks >= stateDuration) {
                    realisePhase = 2;
                    stateTicks = 0;
                    stateDuration = charging ? 70 : rng.nextInt(45);
                }
            }
            default -> {
                if (charging) {
                    headingYaw = lookYaw;
                    throttle = FULL_THROTTLE;
                    wishSprint = true;
                    footsteps(5);
                    sprintDust();
                    if (distanceToSqr(victim) < 6.25 || stateTicks >= stateDuration) {
                        DelusionManager.vanish(this, true);
                    }
                } else if (stateTicks >= stateDuration) {
                    DelusionManager.vanish(this, true);
                }
            }
        }
    }

    // --- Rotation, and the small world queries the behaviours need --------------------------------------

    private void applyRotation() {
        lookYaw = approach(lookYaw, wishYaw, LOOK_TURN_RATE);
        lookPitch = Mth.clamp(approach(lookPitch, wishPitch, LOOK_TURN_RATE), -80.0F, 80.0F);
        setYRot(lookYaw);
        setYHeadRot(lookYaw);
        setXRot(lookPitch);

        if (throttle > 0.001F) {
            this.yBodyRot = approach(this.yBodyRot, headingYaw, BODY_TURN_RATE);
        }
        // A real player's body only follows once the neck runs out of twist.
        float twist = Mth.wrapDegrees(lookYaw - this.yBodyRot);
        if (Math.abs(twist) > BODY_YAW_LIMIT) {
            this.yBodyRot += twist - Math.copySign(BODY_YAW_LIMIT, twist);
        }
    }

    private static float approach(float current, float target, float maxStep) {
        return current + Mth.clamp(Mth.wrapDegrees(target - current), -maxStep, maxStep);
    }

    private void faceEntity(Entity entity) {
        faceTowards(entity.getEyePosition());
    }

    private void faceBlock(BlockPos pos) {
        faceTowards(Vec3.atCenterOf(pos));
    }

    private void faceTowards(Vec3 point) {
        Vec3 delta = point.subtract(getEyePosition());
        wishYaw = (float) (Mth.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        wishPitch = (float) (-(Mth.atan2(delta.y, flat) * (180.0 / Math.PI)));
    }

    /** A solid block within arm's reach — preferring one at foot level, like someone digging in. */
    private BlockPos findMinableBlock() {
        BlockPos origin = blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos candidate = origin.offset(rng.nextInt(5) - 2, rng.nextInt(3) - 1, rng.nextInt(5) - 2);
            BlockState state = level().getBlockState(candidate);
            if (!state.isAir() && state.getFluidState().isEmpty()
                    && state.getDestroySpeed(level(), candidate) >= 0.0F
                    && !state.getCollisionShape(level(), candidate).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private void clearMining() {
        if (miningPos != null) {
            // Anything outside 0..9 removes the overlay. Leaving it set would stick a crack on that block
            // for the rest of the session.
            Minecraft.getInstance().levelRenderer.destroyBlockProgress(getId(), miningPos, -1);
            miningPos = null;
        }
        miningStage = 0;
    }

    /** Called when the delusion goes away, however it goes away. */
    void cleanUp() {
        clearMining();
    }

    private double groundHeightBelow() {
        BlockPos pos = blockPosition();
        for (int i = 0; i < 24; i++) {
            BlockPos check = pos.below(i);
            if (!level().getBlockState(check).getCollisionShape(level(), check).isEmpty()) {
                return check.getY() + 1.0;
            }
        }
        return getY();
    }

    /** The Y a player would stand at over this column, or null if there's nowhere sensible nearby. */
    private Double standableY(double x, double z, double nearY) {
        BlockPos base = BlockPos.containing(x, nearY, z);
        for (int dy = 3; dy >= -6; dy--) {
            BlockPos floor = base.offset(0, dy, 0);
            BlockPos feet = floor.above();
            if (!level().getBlockState(floor).getCollisionShape(level(), floor).isEmpty()
                    && level().getBlockState(feet).getCollisionShape(level(), feet).isEmpty()
                    && level().getBlockState(feet.above()).getCollisionShape(level(), feet.above()).isEmpty()) {
                return (double) feet.getY();
            }
        }
        return null;
    }

    // --- Presentation ----------------------------------------------------------------------------------

    private void footsteps(int interval) {
        if (!onGround() || stateTicks % interval != 0) {
            return;
        }
        BlockPos below = blockPosition().below();
        BlockState state = level().getBlockState(below);
        if (state.isAir()) {
            return;
        }
        playAt(state.getSoundType(level(), below, this).getStepSound(), 0.15F, 1.0F);
    }

    private void sprintDust() {
        if (!onGround() || stateTicks % 2 != 0) {
            return;
        }
        BlockPos below = blockPosition().below();
        BlockState state = level().getBlockState(below);
        if (state.isAir()) {
            return;
        }
        level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                getX() + (rng.nextDouble() - 0.5) * 0.3, getY() + 0.1, getZ() + (rng.nextDouble() - 0.5) * 0.3,
                -getDeltaMovement().x * 4.0, 1.5, -getDeltaMovement().z * 4.0);
    }

    private void breakParticles(BlockPos pos, BlockState state) {
        for (int i = 0; i < 12; i++) {
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + rng.nextDouble(), pos.getY() + rng.nextDouble(), pos.getZ() + rng.nextDouble(),
                    (rng.nextDouble() - 0.5) * 0.2, rng.nextDouble() * 0.2, (rng.nextDouble() - 0.5) * 0.2);
        }
    }

    void puff(int count) {
        for (int i = 0; i < count; i++) {
            level().addParticle(ParticleTypes.POOF,
                    getX() + (rng.nextDouble() - 0.5) * 0.6,
                    getY() + rng.nextDouble() * 1.8,
                    getZ() + (rng.nextDouble() - 0.5) * 0.6,
                    (rng.nextDouble() - 0.5) * 0.06, rng.nextDouble() * 0.06, (rng.nextDouble() - 0.5) * 0.06);
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    getX() + (rng.nextDouble() - 0.5) * 0.5,
                    getY() + rng.nextDouble() * 1.8,
                    getZ() + (rng.nextDouble() - 0.5) * 0.5,
                    0.0, 0.02, 0.0);
        }
    }

    void playAt(SoundEvent sound, float volume, float pitch) {
        level().playLocalSound(getX(), getY(), getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    // --- Being watched ---------------------------------------------------------------------------------

    /** Feeds the realisation timer. Builds fast while watched, bleeds away slowly when you look elsewhere. */
    void updateSeen(boolean visible) {
        if (visible) {
            seenScore += 1.0;
        } else {
            seenScore = Math.max(0.0, seenScore - 0.5);
        }
        if (realisationRollCooldown > 0) {
            realisationRollCooldown--;
        }
    }

    boolean shouldRealise() {
        if (state == State.REALISATION || realisationRollCooldown > 0) {
            return false;
        }
        if (seenScore < Config.DELUSIONS_REALISATION_SEEN_TICKS.get()) {
            return false;
        }
        realisationRollCooldown = 20;
        return rng.nextInt(100) < Config.DELUSIONS_REALISATION_CHANCE.get();
    }

    void beginRealisation() {
        enter(State.REALISATION);
    }

    boolean isRealising() {
        return state == State.REALISATION;
    }

    int getLifeTicks() {
        return lifeTicks;
    }
}
