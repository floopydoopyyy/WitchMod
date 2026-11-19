package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Aim Assist (blessing), on your own client. every tick it gathers the targets in front of you that you can see; every
 * frame it picks the one your crosshair is on or nearest — its zone is the target plus a margin that grows with
 * distance — and helps in three ways, all scaled by how close to it your aim is:
 * <ul>
 * <li><b>friction</b>: your mouse slows down over it, so you don't slide off;</li>
 * <li><b>tracking</b>: your view moves with it — whatever it does, and whatever you do (strafing past it) — so a moving
 * target stays where your crosshair was on it;</li>
 * <li><b>a gentle pull</b> towards its middle, only while you're turning or moving (never when you're still).</li>
 * </ul>
 * turn hard and it lets go — it never locks on. hostile mobs, aggressive or angry neutral mobs and other players always
 * count; passive and calm neutral mobs only once you've attacked them (in melee, or hit one you were aiming at).
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class AimAssistClient {
    private AimAssistClient() {}

    /** the targets in front of you that you can see, refreshed each tick. */
    private static final List<LivingEntity> NEAR = new ArrayList<>();

    /** creatures you've attacked lately (entity id → until when), and when each was last near your aim (to credit a shot). */
    private static final Map<Integer, Long> PROVOKED = new HashMap<>();
    private static final Map<Integer, Long> AIMED_AT = new HashMap<>();

    /** the target being helped, and its angles from you last frame (its motion, for tracking). */
    private static int targetId = -1;
    private static float lastYaw;
    private static float lastPitch;
    private static boolean haveLast;

    /** how hard it helped last frame (the next mouse turn's friction). */
    private static float weight;

    /** your rotation as the assist left it last frame — what's changed since is you — and your own turning speed (deg/s). */
    private static float seenYaw;
    private static float seenPitch;
    private static boolean seen;
    private static float userSpeed;
    private static long lastNanos;

    private static boolean active(LocalPlayer me) {
        return strength(me) > 0.0F;
    }

    /**
     * how strongly it helps: fully with the blessing; a guardian puppet channelling its beam gets a faint touch of it
     * even without (puppeteerGuardianAimAssist); otherwise not at all.
     */
    private static float strength(LocalPlayer me) {
        if (me == null || me.isSpectator()) {
            return 0.0F;
        }
        if (me.getData(WitchModAttachments.AIM_ASSIST_ACTIVE) >= 0) {
            return 1.0F;
        }
        var type = com.oliver.witchmod.effects.blessings.puppeteer.PuppetType.byId(me.getData(WitchModAttachments.PUPPET_TYPE));
        if ((type == com.oliver.witchmod.effects.blessings.puppeteer.PuppetType.GUARDIAN
                || type == com.oliver.witchmod.effects.blessings.puppeteer.PuppetType.ELDER_GUARDIAN)
                && me.getData(WitchModAttachments.PUPPET_FUSE) > 0) {
            try {
                return Config.PUPPETEER_GUARDIAN_AIM_ASSIST.get().floatValue();
            } catch (IllegalStateException e) {
                return 0.0F;
            }
        }
        return 0.0F;
    }

    /** whether {@code e} is something to help you aim at. */
    private static boolean target(LocalPlayer me, Entity e, long now) {
        if (!(e instanceof LivingEntity l) || l == me || !l.isAlive() || l.isSpectator() || l instanceof ArmorStand
                || l.isInvisibleTo(me) || me.isAlliedTo(l)) {
            return false;
        }
        if (l instanceof OwnableEntity pet && me.getUUID().equals(pet.getOwnerUUID())) {
            return false; // never your own pets
        }
        if (l instanceof Player || l instanceof Enemy) {
            return true;
        }
        if (l instanceof Mob mob && mob.isAggressive() || l instanceof NeutralMob neutral && neutral.getRemainingPersistentAngerTime() > 0) {
            return true; // a neutral mob that's turned on someone
        }
        return PROVOKED.getOrDefault(l.getId(), 0L) > now; // a passive one you've gone for
    }

    // --- every tick: who's out there ---

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        NEAR.clear();
        if (mc.level == null || !active(me)) {
            PROVOKED.clear();
            AIMED_AT.clear();
            targetId = -1;
            haveLast = false;
            weight = 0.0F;
            return;
        }
        long now = mc.level.getGameTime();
        double range;
        try {
            range = Config.AIM_ASSIST_RANGE.get();
        } catch (IllegalStateException e) {
            return;
        }
        Vec3 eye = me.getEyePosition();
        Vec3 look = me.getViewVector(1.0F);
        double wide = Math.cos(Math.toRadians(40.0));
        double narrow = Math.cos(Math.toRadians(10.0));
        for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(range), e -> e instanceof LivingEntity)) {
            LivingEntity l = (LivingEntity) e;
            Vec3 to = l.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0E-3) {
                continue;
            }
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot >= narrow) {
                AIMED_AT.put(l.getId(), now);
            }
            // just hurt while you'd been aiming at it: you shot it — it's fair game now
            if (l.hurtTime > 0 && l.hurtTime >= l.hurtDuration - 1 && now - AIMED_AT.getOrDefault(l.getId(), Long.MIN_VALUE / 2) <= 40) {
                provoke(l, now);
            }
            if (dot >= wide && dist <= range && target(me, l, now) && seesIt(mc, eye, l)) {
                NEAR.add(l);
            }
        }
        PROVOKED.values().removeIf(t -> t <= now);
        AIMED_AT.values().removeIf(t -> now - t > 40);
    }

    private static boolean seesIt(Minecraft mc, Vec3 eye, LivingEntity l) {
        for (Vec3 at : new Vec3[] {l.getEyePosition(), l.getBoundingBox().getCenter()}) {
            if (mc.level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType()
                    == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private static void provoke(LivingEntity l, long now) {
        try {
            PROVOKED.put(l.getId(), now + Config.AIM_ASSIST_PROVOKED_TICKS.get());
        } catch (IllegalStateException ignored) {
            // config not synced yet
        }
    }

    /** a melee swing at something makes it a target (passive or not). */
    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity().level().isClientSide() && event.getEntity() == mc.player && event.getTarget() instanceof LivingEntity l && mc.level != null && active(mc.player)) {
            provoke(l, mc.level.getGameTime());
        }
    }

    // --- every frame: the help ---

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Mth.clamp((nanos - lastNanos) / 1.0E9F, 0.0F, 0.1F);
        lastNanos = nanos;
        if (mc.level == null || !active(me) || mc.screen != null || mc.isPaused()) {
            seen = false;
            haveLast = false;
            weight = 0.0F;
            return;
        }
        // how fast YOU are turning (what's changed since the assist last left your view)
        if (seen && dt > 0.0F) {
            float dy = Mth.wrapDegrees(me.getYRot() - seenYaw);
            float dp = me.getXRot() - seenPitch;
            float speed = (float) Math.sqrt(dy * dy + dp * dp) / dt;
            userSpeed += (speed - userSpeed) * Math.min(1.0F, dt * 25.0F);
        }
        float friction;
        float track;
        float pull;
        float perBlock;
        float marginMin;
        float marginMax;
        float letGo;
        float lettingGo;
        float scale = strength(me);
        try {
            friction = Config.AIM_ASSIST_FRICTION.get().floatValue() * scale;
            track = Config.AIM_ASSIST_TRACKING.get().floatValue() * scale;
            pull = Config.AIM_ASSIST_PULL.get().floatValue() * scale;
            perBlock = Config.AIM_ASSIST_MARGIN_PER_BLOCK.get().floatValue();
            marginMin = Config.AIM_ASSIST_MARGIN_MIN.get().floatValue();
            marginMax = Config.AIM_ASSIST_MARGIN_MAX.get().floatValue();
            letGo = Config.AIM_ASSIST_RELEASE_START.get().floatValue();
            lettingGo = Config.AIM_ASSIST_RELEASE_END.get().floatValue();
        } catch (IllegalStateException e) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 eye = me.getEyePosition(pt);
        Vec3 look = Vec3.directionFromRotation(me.getXRot(), me.getYRot());
        long now = mc.level.getGameTime();
        // the target your aim is on, or nearest to its zone (sticking with the current one when it's close)
        LivingEntity best = null;
        float bestScore = Float.MAX_VALUE;
        float bestNear = 0.0F;
        Vec3 bestAim = null;
        double bestDist = 0.0;
        for (LivingEntity l : NEAR) {
            if (!l.isAlive() || l.isRemoved() || !target(me, l, now)) {
                continue;
            }
            Vec3 shift = l.getPosition(pt).subtract(l.position());
            AABB box = l.getBoundingBox().move(shift);
            Vec3 aim = box.getCenter();
            Vec3 to = aim.subtract(eye);
            double dist = to.length();
            if (dist < 1.0E-3) {
                continue;
            }
            float margin = Mth.clamp((float) dist * perBlock, marginMin, marginMax);
            float half = (float) Math.max(box.getXsize(), box.getYsize()) * 0.5F;
            float zone = (float) Math.toDegrees(Math.atan((half + margin) / dist));
            float off = (float) Math.toDegrees(Math.acos(Mth.clamp(to.scale(1.0 / dist).dot(look), -1.0, 1.0)));
            float score = off / Math.max(0.5F, zone);
            if (box.clip(eye, eye.add(look.scale(dist + 4.0))).isPresent()) {
                score = Math.min(score, 0.15F); // right on it
            }
            if (l.getId() == targetId) {
                score *= 0.75F;
            }
            if (score < 1.0F && score < bestScore) {
                best = l;
                bestScore = score;
                bestNear = 1.0F - Math.min(1.0F, off / Math.max(0.5F, zone));
                bestAim = aim;
                bestDist = dist;
            }
        }
        if (best == null) {
            targetId = -1;
            haveLast = false;
            weight = Math.max(0.0F, weight - dt * 6.0F);
            remember(me);
            return;
        }
        // stronger the closer your aim is to it; a touch stronger at range; and it lets go when you turn hard
        float near = (float) Math.pow(Math.max(bestNear, 1.0F - bestScore), 0.7);
        float range = 0.8F + 0.2F * Math.min(1.0F, (float) bestDist / 24.0F);
        float release = 1.0F - Mth.clamp((userSpeed - letGo) / Math.max(1.0F, lettingGo - letGo), 0.0F, 1.0F);
        float w = near * range * release;
        Vec3 to = bestAim.subtract(eye);
        float yawTo = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitchTo = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        float turnYaw = 0.0F;
        float turnPitch = 0.0F;
        // tracking: follow how the target's moved across your view since last frame
        if (haveLast && best.getId() == targetId) {
            float dYaw = Mth.wrapDegrees(yawTo - lastYaw);
            float dPitch = pitchTo - lastPitch;
            if (Math.abs(dYaw) < 30.0F && Math.abs(dPitch) < 30.0F) {
                turnYaw += dYaw * track * w;
                turnPitch += dPitch * track * w;
            }
        }
        // the pull: only while you're turning or moving, never when you're still
        boolean input = userSpeed > 4.0F || me.input.forwardImpulse != 0.0F || me.input.leftImpulse != 0.0F;
        if (input && pull > 0.0F) {
            float k = 1.0F - (float) Math.pow(1.0F - Math.min(0.95F, pull), dt * 20.0F);
            turnYaw += Mth.wrapDegrees(yawTo - me.getYRot()) * k * w;
            turnPitch += (pitchTo - me.getXRot()) * k * w;
        }
        if (turnYaw != 0.0F || turnPitch != 0.0F) {
            me.turn(turnYaw / 0.15, turnPitch / 0.15); // (turn moves last tick's angles too: no stutter)
        }
        targetId = best.getId();
        lastYaw = yawTo;
        lastPitch = pitchTo;
        haveLast = true;
        weight = w * friction;
        remember(me);
    }

    private static void remember(LocalPlayer me) {
        seenYaw = me.getYRot();
        seenPitch = me.getXRot();
        seen = true;
    }

    /** friction: over a target, your mouse moves your view less (vanilla's sensitivity curve is cubic, so undo that). */
    @SubscribeEvent
    static void onTurn(CalculatePlayerTurnEvent event) {
        if (weight <= 0.0F || !active(Minecraft.getInstance().player)) {
            return;
        }
        float keep = Mth.clamp(1.0F - weight, 0.05F, 1.0F);
        double curve = event.getMouseSensitivity() * 0.6 + 0.2;
        double slowed = (Math.cbrt(keep) * curve - 0.2) / 0.6;
        event.setMouseSensitivity(Math.max(-1.0 / 3.0, slowed));
    }
}
