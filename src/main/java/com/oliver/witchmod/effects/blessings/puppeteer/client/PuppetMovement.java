package com.oliver.witchmod.effects.blessings.puppeteer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.network.WitchModNetwork;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.Holds;
import com.oliver.witchmod.effects.blessings.puppeteer.Lunges;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetGroup;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetMove;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.effects.blessings.puppeteer.Puppets;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermiteBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.HorseBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.PiglinBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.SlimeBehaviour;

import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetCamera.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppeteerClient.*;

/** puppeteer, client: your own puppet's movement — anchoring, hops, horse leaps, burrowing, the lunges you drive. */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetMovement {
    private PuppetMovement() {}

    /** the lunge's current heading (your own), and the lunge it belongs to. */
    static net.minecraft.world.phys.Vec3 lungeDir;

    static long lungeFor;

    /** a lunge you ran into a wall with — stopped here, before the server's own timer ends it. */
    static long lungeStopped;

    /**
     * sitting still on purpose: a guardian channelling its beam (easier tracking) or an axolotl playing dead —
     * right-click held (and off cooldown), or the move already running.
     */
    static boolean holdingStill(Player p) {
        if (PiglinBehaviour.bartering(p)) {
            return true; // a piglin admiring gold it was handed is rooted to the spot
        }
        PuppetType t = PuppetType.byId(typeOf(p));
        if (t == PuppetType.KILLER_RABBIT || t == PuppetType.SILVERFISH) {
            return p.getData(WitchModAttachments.PUPPET_FUSE) > 0; // mid-scrap / burrowing in or hidden
        }
        if (t == PuppetType.WOLF) { // mid-scrap (its charge, while right-click is still held, only slows it)
            return p.getData(WitchModAttachments.PUPPET_FUSE) > 0 && !(p == Minecraft.getInstance().player && held);
        }
        if (t == null || (t.move() != PuppetMove.BEAM && t.move() != PuppetMove.PLAY_DEAD)) {
            return false;
        }
        return p.getData(WitchModAttachments.PUPPET_FUSE) > 0
                || (p == Minecraft.getInstance().player && held
                && p.level().getGameTime() >= p.getData(WitchModAttachments.PUPPET_ACTION_READY));
    }

    /** ticks into the current lunge (wind-up then charge), or -1 if not lunging (or the charge was stopped locally). */
    static long lungeTick(Player p) {
        long start = p.getData(WitchModAttachments.PUPPET_LUNGE_START);
        PuppetType t = PuppetType.byId(typeOf(p));
        if (start == 0L || t == null || start == lungeStopped
                || (t.move() != PuppetMove.LUNGE && t != PuppetType.PHANTOM
                && t != PuppetType.KILLER_RABBIT && t != PuppetType.WOLF && t != PuppetType.VEX && t.group() != PuppetGroup.GOAT
                && t != PuppetType.FOX && t.group() != PuppetGroup.VINDICATOR)) {
            return -1L;
        }
        long tick = p.level().getGameTime() - start;
        try {
            return tick >= 0 && tick < Lunges.lungeWindup(t) + Lunges.lungeTicks(p, t) ? tick : -1L;
        } catch (IllegalStateException e) {
            return -1L;
        }
    }

    /** your own movement keys do nothing: mid-possession, channelling a beam (anchored), or winding up / charging a lunge. */
    static boolean anchored(Player p) {
        return binding(p) || holdingStill(p) || lungeTick(p) >= 0
                || com.oliver.witchmod.effects.blessings.puppeteer.mob.PandaBehaviour.rollTick(p) >= 0
                || com.oliver.witchmod.effects.blessings.puppeteer.mob.ArmadilloBehaviour.curled(p)
                || com.oliver.witchmod.effects.blessings.puppeteer.mob.SnifferBehaviour.busy(p)
                || com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour.rooted(p)
                || shulkerRooted(p) || com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.teleportCharging(p);
    }

    /** a shulker doesn't walk — it teleports (unless the config lets it). */
    static boolean shulkerRooted(Player p) {
        if (!com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.shulkerPuppet(p)) {
            return false;
        }
        try {
            return com.oliver.witchmod.Config.PUPPETEER_SHULKER_ROOTED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** a panda's roll: vanilla's handleRoll — a shove the way you face, hops at 7 / 15 / 23 — at the configured pace. */
    private static net.minecraft.world.phys.Vec3 rollDelta;
    private static long rollFor;

    static void tickRoll(Player p) {
        int t = com.oliver.witchmod.effects.blessings.puppeteer.mob.PandaBehaviour.rollTick(p);
        if (t < 0) {
            return;
        }
        long start = p.getData(WitchModAttachments.PUPPET_LUNGE_START);
        net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
        if (rollFor != start || rollDelta == null) {
            rollFor = start;
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double pace = com.oliver.witchmod.Config.PUPPETEER_PANDA_ROLL_SPEED.get()
                    * (p.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0 ? 0.5 : 1.0);
            rollDelta = new net.minecraft.world.phys.Vec3(v.x - Mth.sin(yaw) * pace, 0.0, v.z + Mth.cos(yaw) * pace);
            p.setDeltaMovement(rollDelta.add(0.0, 0.27, 0.0));
            return;
        }
        int counter = t + 1;
        if (counter != 7 && counter != 15 && counter != 23) {
            p.setDeltaMovement(rollDelta.x, v.y, rollDelta.z);
        } else {
            p.setDeltaMovement(0.0, p.onGround() ? 0.27 : v.y, 0.0);
        }
    }

    /** a strider bobs up out of lava onto its surface, as the real one floats itself. */
    static void floatStrider(Player p) {
        if (!p.isInLava()) {
            return;
        }
        net.minecraft.core.BlockPos at = p.blockPosition();
        if (net.minecraft.world.phys.shapes.CollisionContext.of(p).isAbove(net.minecraft.world.level.block.LiquidBlock.STABLE_SHAPE, at, true)
                && !p.level().getFluidState(at.above()).is(net.minecraft.tags.FluidTags.LAVA)) {
            p.setOnGround(true);
        } else {
            p.setDeltaMovement(p.getDeltaMovement().scale(0.5).add(0.0, 0.05, 0.0));
        }
    }

    /** frozen in place while your soul crosses over, a beam channels, or a lunge runs. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        if (leapCharge > 0) {
            // drawing breath for a leap: rooted, and no ordinary jump
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            return;
        }
        if (anchored(p)) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            if (binding(p)) {
                event.getInput().shiftKeyDown = false; // (only mid-possession: anchored otherwise, crouch + right-click still leaves)
            }
            return;
        }
        PuppetType own = PuppetType.byId(typeOf(p));
        Player steerer = own == null ? null : com.oliver.witchmod.effects.blessings.puppeteer.mob.RideSteering.steerer(p);
        if (steerer != null) {
            // a rider holding its stick drives: always forward, the way they face (as a ridden pig / strider goes)
            event.getInput().forwardImpulse = 1.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            p.setYRot(steerer.getYRot());
            p.setYHeadRot(steerer.getYRot());
            return;
        }
        if (own != null && own.group() == PuppetGroup.HORSE) {
            horseInput(p, own, event.getInput());
            return;
        }
        if (EndermiteBehaviour.burrowed(p)) {
            // a burrowing endermite steers itself (tickBurrow) — no walking or jumping
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            return;
        }
        PuppetType t = PuppetType.byId(typeOf(p));
        if (t != null && (t.group() == PuppetGroup.RABBIT || t.group() == PuppetGroup.SLIME)
                && !p.isInWater() && !p.isInLava() && !p.getAbilities().flying) {
            // rabbits and slimes only hop: on the ground a movement key launches a hop (its direction kept for onHop)
            // instead of walking. mid-air the keys still steer, so you never get stuck against a ledge.
            float forward = event.getInput().forwardImpulse;
            float left = event.getInput().leftImpulse;
            int pause = 0;
            if (t.group() == PuppetGroup.SLIME) {
                try {
                    pause = com.oliver.witchmod.Config.PUPPETEER_SLIME_HOP_PAUSE.get(); // the slime's boing... boing rhythm
                } catch (IllegalStateException ignored) {
                    // not synced yet: no pause
                }
            }
            if (p.onGround()) {
                if ((Math.abs(forward) > 0.01F || Math.abs(left) > 0.01F) && slimeGroundTicks >= pause) {
                    event.getInput().jumping = true;
                    float yaw = p.getYRot() * Mth.DEG_TO_RAD;
                    hopDir = new net.minecraft.world.phys.Vec3(-Mth.sin(yaw) * forward + Mth.cos(yaw) * left, 0.0,
                            Mth.cos(yaw) * forward + Mth.sin(yaw) * left).normalize();
                    // (sprint is read from the key: vanilla drops the sprint flag on the ground with no walking input)
                    hopSprint = Minecraft.getInstance().options.keySprint.isDown() || p.isSprinting();
                }
                event.getInput().forwardImpulse = 0.0F;
                event.getInput().leftImpulse = 0.0F;
            }
        }
    }

    /** whether the hop about to be launched is a sprinting one (a rabbit bolting). */
    static boolean hopSprint;

    /** ticks you've been on the ground (a slime sits squashed a moment between bounces). */
    static int slimeGroundTicks;

    /** a horse puppet's jump charge (ticks the jump key has been held on the ground). */
    static int horseCharge;

    /** the horse's jump bar, 0..1, for the hud (0 when not charging). */
    public static float horseJumpProgress() {
        try {
            return horseCharge <= 0 ? 0.0F : Math.min(1.0F, horseCharge / (float) com.oliver.witchmod.Config.PUPPETEER_HORSE_JUMP_CHARGE_TICKS.get());
        } catch (IllegalStateException e) {
            return 0.0F;
        }
    }

    /**
     * horses control like a ridden horse: HOLD jump to charge a leap (vanilla's scale: a tap is a 40% hop, a full bar
     * the whole jump, carried forward if you're moving), sprint freely, and in water they can't swim — no rising, no
     * swim-sprint, and they slowly sink.
     */
    static void horseInput(Player p, PuppetType type, net.minecraft.client.player.Input input) {
        if (p.isInWater() && type == PuppetType.SKELETON_HORSE) {
            horseCharge = 0;
            return; // a skeleton horse goes right on underwater: it moves (and rises) as on land, never drowns
        }
        if (p.isInWater()) {
            horseCharge = 0;
            input.jumping = false;
            p.setSprinting(false);
            p.setDeltaMovement(p.getDeltaMovement().add(0.0, -0.02, 0.0));
            return;
        }
        boolean holding = input.jumping;
        input.jumping = false; // never a plain player jump
        if (holding && p.onGround()) {
            horseCharge += Holds.chargeStep(p); // (dexterous fills the bar quicker)
            return;
        }
        if (!holding && horseCharge > 0) {
            int full;
            double strength;
            try {
                full = com.oliver.witchmod.Config.PUPPETEER_HORSE_JUMP_CHARGE_TICKS.get();
                strength = HorseBehaviour.horseJump(type);
            } catch (IllegalStateException e) {
                horseCharge = 0;
                return;
            }
            float scale = horseCharge >= full ? 1.0F : 0.4F + 0.4F * horseCharge / (float) full;
            horseCharge = 0;
            if (!p.onGround()) {
                return;
            }
            net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
            double fx = 0.0;
            double fz = 0.0;
            if (input.forwardImpulse > 0.0F) {
                float yaw = p.getYRot() * Mth.DEG_TO_RAD;
                fx = -0.4 * Mth.sin(yaw) * scale;
                fz = 0.4 * Mth.cos(yaw) * scale;
            }
            p.setDeltaMovement(v.x + fx, strength * scale, v.z + fz);
            p.hasImpulse = true;
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.HORSE_JUMP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.0F, false);
        }
        if (!p.onGround()) {
            horseCharge = 0;
        }
    }

    /** the direction of the hop about to be launched (set from the movement keys, used as the jump fires). */
    static net.minecraft.world.phys.Vec3 hopDir;

    /** a rabbit's hop: the jump carries it forward at the hop speed (more for the killer bunny). */
    @SubscribeEvent
    static void onHop(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || hopDir == null) {
            return;
        }
        PuppetType t = PuppetType.byId(typeOf(mc.player));
        if (t == null || (t.group() != PuppetGroup.RABBIT && t.group() != PuppetGroup.SLIME)) {
            hopDir = null;
            return;
        }
        double speed;
        double lift;
        try {
            if (t.group() == PuppetGroup.SLIME) {
                // a bigger slime bounds further (+10% per size step); a magma cube bounces higher (+0.1 per step)
                int size = SlimeBehaviour.slimeSize(mc.player);
                boolean magma = t == PuppetType.MAGMA_CUBE;
                speed = (magma ? com.oliver.witchmod.Config.PUPPETEER_MAGMA_CUBE_HOP_SPEED : com.oliver.witchmod.Config.PUPPETEER_SLIME_HOP_SPEED).get()
                        * (1.0 + 0.1 * size);
                lift = magma ? com.oliver.witchmod.Config.PUPPETEER_MAGMA_CUBE_HOP_LIFT.get() + 0.1 * size
                        : com.oliver.witchmod.Config.PUPPETEER_SLIME_HOP_LIFT.get();
            } else {
                boolean killer = t == PuppetType.KILLER_RABBIT;
                speed = (killer ? com.oliver.witchmod.Config.PUPPETEER_KILLER_RABBIT_HOP_SPEED : com.oliver.witchmod.Config.PUPPETEER_RABBIT_HOP_SPEED).get();
                lift = (killer ? com.oliver.witchmod.Config.PUPPETEER_KILLER_RABBIT_HOP_LIFT : com.oliver.witchmod.Config.PUPPETEER_RABBIT_HOP_LIFT).get();
                if (hopSprint) {
                    speed *= com.oliver.witchmod.Config.PUPPETEER_RABBIT_SPRINT_HOP.get(); // bolting
                }
            }
        } catch (IllegalStateException e) {
            return;
        }
        net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
        // a movement hop is a low, vanilla-style hop; the jump key gives the full jump, still carried forward.
        double up = mc.options.keyJump.isDown() ? Math.max(v.y, lift) : lift;
        mc.player.setDeltaMovement(hopDir.x * speed, up, hopDir.z * speed);
        hopDir = null;
    }

    /**
     * a burrowing endermite, steered here (your movement is client-side): in the rock you go where you look (forward /
     * back / strafe; jump rises, sneak sinks) at the burrow speed; out in open air (a cave) you only drift sideways and
     * fall until you're back in the rock. the hardness limit is enforced in EntityBurrowMixin.
     */
    static void tickBurrow(Minecraft mc) {
        Player p = mc.player;
        if (p == null || !EndermiteBehaviour.burrowed(p) || mc.screen != null) {
            return;
        }
        double speed;
        try {
            speed = com.oliver.witchmod.Config.PUPPETEER_ENDERMITE_BURROW_SPEED.get();
        } catch (IllegalStateException e) {
            return;
        }
        int forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        int rise = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        net.minecraft.world.phys.Vec3 v;
        if (EndermiteBehaviour.inEarth(p)) {
            v = p.getLookAngle().scale(forward).add(side.scale(strafe)).add(0.0, rise, 0.0);
            v = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(speed) : net.minecraft.world.phys.Vec3.ZERO;
        } else {
            net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(-Mth.sin(yaw) * forward, 0.0, Mth.cos(yaw) * forward)
                    .add(side.scale(strafe));
            flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(speed * 0.5) : net.minecraft.world.phys.Vec3.ZERO;
            v = new net.minecraft.world.phys.Vec3(flat.x, Math.max(-1.0, p.getDeltaMovement().y - 0.08), flat.z);
        }
        p.setDeltaMovement(v);
        p.resetFallDistance();
    }

    /**
     * your own lunge, driven here so it's smooth: stand still through the wind-up, then charge at the lunge's speed
     * along a heading that can only turn towards where you look by a few degrees a tick. a wall stops it.
     */
    static void tickLunge(Player p, @org.jetbrains.annotations.Nullable PuppetType type) {
        long tick = lungeTick(p);
        if (tick < 0 || type == null) {
            lungeDir = null;
            return;
        }
        long start = p.getData(WitchModAttachments.PUPPET_LUNGE_START);
        net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
        if (type == PuppetType.VEX && tick < Lunges.lungeWindup(type)) {
            p.setDeltaMovement(v.x * 0.2, v.y * 0.4, v.z * 0.2); // the slight giggle-charge: hover in place
            return;
        }
        if (type == PuppetType.PHANTOM || type == PuppetType.VEX) {
            dive(p, type, start, tick); // a 3-d dash that follows your look (the vex darts like a phantom's dive)
            return;
        }
        if (tick < Lunges.lungeWindup(type)) {
            p.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3); // dig in
            return;
        }
        net.minecraft.world.phys.Vec3 look = p.getLookAngle();
        net.minecraft.world.phys.Vec3 want = new net.minecraft.world.phys.Vec3(look.x, 0.0, look.z);
        want = want.lengthSqr() < 1.0E-4 ? new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0) : want.normalize();
        if (lungeDir == null || lungeFor != start) {
            lungeDir = want;
            lungeFor = start;
        } else {
            double have = Math.atan2(lungeDir.z, lungeDir.x);
            double diff = Mth.wrapDegrees((Math.atan2(want.z, want.x) - have) * Mth.RAD_TO_DEG);
            double turn = Mth.clamp(diff, -Lunges.lungeTurn(type), Lunges.lungeTurn(type)) * Mth.DEG_TO_RAD;
            lungeDir = new net.minecraft.world.phys.Vec3(Math.cos(have + turn), 0.0, Math.sin(have + turn));
            if (p.horizontalCollision && tick > Lunges.lungeWindup(type) + 2) {
                lungeStopped = start; // ran into a wall
                lungeDir = null;
                return;
            }
        }
        double speed = Lunges.lungeSpeed(p, type);
        p.setDeltaMovement(lungeDir.x * speed, v.y, lungeDir.z * speed);
    }

    /**
     * the phantom's dive: along your look in 3d, turning towards where you look only a few degrees a tick, and faster
     * the steeper it goes (up to 1.5x straight down). the ground ends it.
     */
    static void dive(Player p, PuppetType type, long start, long tick) {
        net.minecraft.world.phys.Vec3 want = p.getLookAngle().normalize();
        if (type == PuppetType.PHANTOM) {
            want = diveAssist(p, want);
        }
        if (lungeDir == null || lungeFor != start) {
            lungeDir = want;
            lungeFor = start;
        } else {
            double max = Lunges.lungeTurn(type) * Mth.DEG_TO_RAD;
            double angle = Math.acos(Mth.clamp(lungeDir.dot(want), -1.0, 1.0));
            if (angle > 1.0E-4) {
                // slerp towards the look, capped at the turn rate
                double f = Math.min(1.0, max / angle);
                lungeDir = lungeDir.scale(Math.sin((1.0 - f) * angle)).add(want.scale(Math.sin(f * angle)))
                        .scale(1.0 / Math.sin(angle)).normalize();
            }
            if ((p.onGround() || p.horizontalCollision) && tick > 2) {
                lungeStopped = start;
                lungeDir = null;
                return;
            }
        }
        double speed = Lunges.lungeSpeed(type) * (1.0 + 0.5 * Math.max(0.0, -lungeDir.y));
        if (type == PuppetType.PHANTOM && lungeDir.y > 0.0) {
            // climbing is hard work: up to puppeteerPhantomDiveUpPenalty slower, in full from about 30 degrees up
            double penalty;
            try {
                penalty = com.oliver.witchmod.Config.PUPPETEER_PHANTOM_DIVE_UP_PENALTY.get();
            } catch (IllegalStateException e) {
                penalty = 0.0;
            }
            speed *= 1.0 - penalty * Math.min(1.0, lungeDir.y * 2.0);
        }
        p.setDeltaMovement(lungeDir.scale(speed));
    }

    /** a phantom's dive bends towards whatever living thing is ahead of it, within puppeteerPhantomDiveAssistDegrees. */
    static net.minecraft.world.phys.Vec3 diveAssist(Player p, net.minecraft.world.phys.Vec3 look) {
        double cone;
        try {
            cone = Math.cos(Math.toRadians(com.oliver.witchmod.Config.PUPPETEER_PHANTOM_DIVE_ASSIST_DEGREES.get()));
        } catch (IllegalStateException e) {
            return look;
        }
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.Vec3 best = null;
        double bestDot = cone;
        for (net.minecraft.world.entity.Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(40.0),
                e -> e instanceof net.minecraft.world.entity.LivingEntity l && l.isAlive() && !l.isSpectator())) {
            net.minecraft.world.phys.Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 1.0E-3 || dist > 40.0) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = to.scale(1.0 / dist);
            }
        }
        return best == null ? look : look.add(best).normalize();
    }

    /** your own puppet's movement each tick: flight rules, the swimmer's dive, the blaze's slow fall, burrowing, lunges. */
    /** a breeze's leap charging (ticks), while jump's held on the ground. */
    static int leapCharge;

    /**
     * breeze: holding jump on the ground draws breath (no ordinary jump); let go and it launches — higher the longer
     * it was held (higher than a real breeze), and a little the way you face.
     */
    static void tickLeap(Minecraft mc, @org.jetbrains.annotations.Nullable PuppetType own) {
        Player p = mc.player;
        boolean jumpHeld = mc.options.keyJump.isDown() && mc.screen == null;
        if (own != PuppetType.BREEZE) {
            leapCharge = 0;
            return;
        }
        int full;
        double low;
        double high;
        try {
            full = Math.max(1, com.oliver.witchmod.Config.PUPPETEER_BREEZE_LEAP_CHARGE_TICKS.get());
            low = com.oliver.witchmod.Config.PUPPETEER_BREEZE_LEAP_MIN.get();
            high = com.oliver.witchmod.Config.PUPPETEER_BREEZE_LEAP_MAX.get();
        } catch (IllegalStateException e) {
            return;
        }
        if (jumpHeld && p.onGround()) {
            if (leapCharge == 0) {
                WitchModNetwork.sendPuppetJump(0, 0);
            }
            leapCharge = Math.min(full, leapCharge + 1);
        } else if (leapCharge > 0) {
            float share = leapCharge / (float) full;
            net.minecraft.world.phys.Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
            net.minecraft.world.phys.Vec3 ahead = look.lengthSqr() < 1.0E-4 ? net.minecraft.world.phys.Vec3.ZERO : look.normalize().scale(0.25 + 0.5 * share);
            p.setDeltaMovement(ahead.x, low + (high - low) * share, ahead.z);
            WitchModNetwork.sendPuppetJump(1, leapCharge);
            leapCharge = 0;
        }
    }

    /** charging a shot in the air holds a breeze (or an elder guardian's beam) aloft: it hovers instead of falling. */
    static void tickHover(Minecraft mc, @org.jetbrains.annotations.Nullable PuppetType own) {
        Player p = mc.player;
        boolean hovering = (own == PuppetType.BREEZE || own == PuppetType.ELDER_GUARDIAN) && !p.onGround() && !p.isInWater()
                && p.getData(com.oliver.witchmod.data.WitchModAttachments.PUPPET_FUSE) > 0 && !p.getAbilities().flying;
        if (own == PuppetType.PHANTOM && com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.teleportCharging(p)) {
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); // gathering a teleport: it hangs dead still in the air
            return;
        }
        if (hovering) {
            net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.85, v.y > 0.0 ? v.y * 0.7 : v.y * 0.2, v.z * 0.85);
            p.resetFallDistance();
        }
    }

    static void tickLocal(Minecraft mc, @org.jetbrains.annotations.Nullable PuppetType own) {
        tickLeap(mc, own);
        tickHover(mc, own);
        // the vex and allay fly but can't SPRINT-fly (which would double the speed) — clear sprint while airborne.
        if ((own == PuppetType.VEX || own == PuppetType.ALLAY) && mc.player.getAbilities().flying && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
        // a mob that never lands (bat, phantom, ghast, vex, allay, bee) can't walk: vanilla drops a survival player out of
        // flight on touching the ground (or a double-tap of jump), so put it straight back
        if (com.oliver.witchmod.effects.blessings.puppeteer.Puppets.permanentFlier(mc.player) && mc.player.getAbilities().mayfly
                && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        // puppets never enter swimming mode (EntitySwimMixin: the camera stays put), so a swimmer gets the one thing it
        // gave — diving and rising where you look while you swim forward — by hand, at vanilla's swimming rate.
        if (Puppets.swimmer(own) && mc.player.isInWater() && mc.player.input.forwardImpulse > 0.0F) {
            double lookY = mc.player.getLookAngle().y;
            double rate = lookY < -0.2 ? 0.085 : 0.06;
            net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, v.y + (lookY - v.y) * rate, v.z);
        }
        // a blaze (or a parrot) out of flight drifts down slowly, like the real one
        if ((own == PuppetType.BLAZE || own == PuppetType.PARROT) && !mc.player.getAbilities().flying && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < 0.0) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(1.0, 0.6, 1.0));
        }
        if (mc.player.onGround()) {
            slimeGroundTicks++;
        } else {
            slimeGroundTicks = 0;
        }
        tickBurrow(mc);
        tickLunge(mc.player, own);
        if (own == PuppetType.PANDA) {
            tickRoll(mc.player);
        } else if (own == PuppetType.STRIDER) {
            floatStrider(mc.player);
        }
        if (holdingStill(mc.player)) {
            // sit dead still: no drift or sinking in water; on land only gravity (so you settle, not hover).
            net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0.0, mc.player.isInWater() ? 0.0 : Math.min(v.y, 0.0), 0.0);
        }
    }

    /** leaving the world: no lunge carries over. */
    static void reset() {
        lungeDir = null;
    }
}
