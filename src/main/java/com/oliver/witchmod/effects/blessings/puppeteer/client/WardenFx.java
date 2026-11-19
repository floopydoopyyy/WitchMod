package com.oliver.witchmod.effects.blessings.puppeteer.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenHearing;
import com.oliver.witchmod.mixin.WardenAccessor;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * puppeteer, client: what a warden sees of its world — drawn in light, through the fog and through the walls, for it
 * alone. footsteps glow where they fall and fade; every other sound ripples out from where it was made, and any that
 * gave someone away sends a bead of light running to the warden's head like vanilla's vibration; a sniff draws scent
 * from whoever's in the cone even as it's held, sweeps out when let go and draws threads back from whoever it caught;
 * the noticed carry pips over their heads, the marked a red heart-paced halo — and past where it can see them, a red
 * pillar. (the warden sight shader turns all of it to cold blue light, the marked to red.) the client's
 * wardenSenseIntensity scales it all.
 * <p>
 * also the warden puppet's model: every vanilla animation, driven by hand on the never-ticked dummy — the swing, the
 * sonic boom, the roar, the sniff, the dig and emergence (with its dust), the tendrils and the heart.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class WardenFx {
    private WardenFx() {}

    /** additive light, drawn over everything (no depth test), fog-free. built on first use, never during mod loading. */
    private static RenderType glow;

    private static RenderType glow() {
        if (glow == null) {
            glow = makeGlow();
        }
        return glow;
    }

    private static RenderType makeGlow() {
        return RenderType.create("witchmod_warden_sense", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 16384, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    }

    /** a sound heard: where, who made it, how loud (WardenHearing's kinds), when, and how far it had to travel. */
    private record Heard(Vec3 at, int entity, int kind, long born, float travel) {}

    /** a sniff let out: its cone, and when. */
    private record Sniff(Vec3 from, float yaw, float pitch, float range, float angle, long born) {}

    /** a thread of scent from someone a sniff caught back to the warden, starting at {@code born}. */
    private record Scent(int entity, Vec3 at, long born) {}

    /** a footprint, glowing where it fell: where, which way it faced, which foot, when, and whether its maker is marked. */
    private record Print(Vec3 at, float yaw, boolean left, long born, boolean marked) {}

    private static final List<Heard> HEARD = new ArrayList<>();
    private static final List<Print> PRINTS = new ArrayList<>();
    /** each walker's next foot. */
    private static final Map<Integer, Boolean> NEXT_FOOT = new HashMap<>();
    private static final int PRINT_LIFE = 70;
    private static final List<Sniff> SNIFFS = new ArrayList<>();
    private static final List<Scent> SCENTS = new ArrayList<>();

    /** each warden puppet's last one-off animation counter (PUPPET_WARDEN_FX). */
    private static final Map<UUID, Integer> LAST_FX = new HashMap<>();

    private static final int SNIFF_LIFE = 26;
    private static final int SCENT_LIFE = 28;

    // --- what it's told --------------------------------------------------------------------------------

    static void sound(Minecraft mc, WitchModNetwork.WardenHearPayload p) {
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        float travel = (float) Math.sqrt(mc.player.getEyePosition().distanceToSqr(at));
        long now = mc.level.getGameTime();
        if ((p.kind() & WardenHearing.FLAG_STEP) != 0 && p.entity() >= 0) {
            // a step: a glowing footprint, left and right in turn, facing the way they walk
            Entity walker = mc.level.getEntity(p.entity());
            boolean left = NEXT_FOOT.getOrDefault(p.entity(), true);
            NEXT_FOOT.put(p.entity(), !left);
            PRINTS.add(new Print(at, walker != null ? walker.getYRot() : 0.0F, left, now, (p.kind() & WardenHearing.FLAG_MARKED) != 0));
            if (PRINTS.size() > 240) {
                PRINTS.remove(0);
            }
            if ((p.kind() & WardenHearing.FLAG_COUNTED) == 0) {
                return; // (a step that gave nothing away is just its print)
            }
        }
        HEARD.add(new Heard(at, p.entity(), p.kind(), now, travel));
        if (HEARD.size() > 160) {
            HEARD.remove(0);
        }
    }

    static void sniff(Minecraft mc, WitchModNetwork.WardenSniffPayload p) {
        long now = mc.level.getGameTime();
        Vec3 from = new Vec3(p.x(), p.y(), p.z());
        SNIFFS.add(new Sniff(from, p.yaw(), p.pitch(), p.range(), p.angle(), now));
        for (int id : p.caught()) {
            Entity e = mc.level.getEntity(id);
            if (e != null) {
                // the scent comes back once the sweep reaches them
                float reach = (float) Math.sqrt(e.distanceToSqr(from)) / Math.max(1.0F, p.range());
                SCENTS.add(new Scent(id, e.position(), now + (long) (sweep(reach) * SNIFF_LIFE)));
            }
        }
    }

    /** when the sniff's sweep reaches a fraction {@code d} of its range (inverse of its ease-out), as a fraction of its life. */
    private static float sweep(float d) {
        return 1.0F - (float) Math.sqrt(Math.max(0.0F, 1.0F - Math.min(1.0F, d)));
    }

    // --- drawing ---------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || mc.level == null || mc.player == null) {
            return;
        }
        if (!WardenSenses.active()) {
            HEARD.clear();
            PRINTS.clear();
            NEXT_FOOT.clear();
            SNIFFS.clear();
            SCENTS.clear();
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = mc.level.getGameTime() + pt;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Draw d = new Draw(buffers.getBuffer(glow()), m, cam, camera.getLeftVector(), camera.getUpVector(),
                0.8F * (float) com.oliver.witchmod.ClientConfig.wardenSenseIntensity());
        Vec3 head = mc.player.getEyePosition(pt).subtract(0.0, 0.35, 0.0);
        drawPrints(d, time);
        drawHeard(d, time, head);
        drawSniffs(d, time);
        drawScents(mc, d, time, pt, head);
        drawPreview(mc, d, time, pt);
        drawKnown(mc, d, time, pt);
        buffers.endBatch(glow());
    }

    /** footprints: a soft glowing print where each step fell, fading over a few seconds. */
    private static void drawPrints(Draw d, float time) {
        PRINTS.removeIf(p -> time - p.born() > PRINT_LIFE);
        for (Print p : PRINTS) {
            float t = (time - p.born()) / PRINT_LIFE;
            float fade = (float) Math.pow(1.0F - t, 1.3) * 0.7F;
            float r = p.marked() ? 1.0F : 0.45F;
            float g = p.marked() ? 0.16F : 0.8F;
            float b = p.marked() ? 0.1F : 1.0F;
            float yaw = p.yaw() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 side = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
            Vec3 c = p.at().add(side.scale(p.left() ? 0.13 : -0.13)).add(0.0, 0.03, 0.0);
            d.print(c, forward, side, 0.15F, 0.07F, r, g, b, fade);
            d.print(c, forward, side, 0.24F, 0.13F, r, g, b, fade * 0.25F); // its glow
        }
    }

    /** each sound: a ring rolling out from it, a brief flash — and, if it gave someone away, a bead of light to you. */
    private static void drawHeard(Draw d, float time, Vec3 head) {
        HEARD.removeIf(h -> time - h.born() > 40);
        for (Heard h : HEARD) {
            float age = time - h.born();
            int weight = h.kind() & 3;
            boolean marked = (h.kind() & WardenHearing.FLAG_MARKED) != 0;
            boolean step = (h.kind() & WardenHearing.FLAG_STEP) != 0;
            boolean counted = (h.kind() & WardenHearing.FLAG_COUNTED) != 0;
            float r;
            float g;
            float b;
            float alpha;
            if (marked) {
                r = 1.0F;
                g = 0.16F;
                b = 0.1F;
                alpha = 0.7F;
            } else if (weight == WardenHearing.KIND_OBVIOUS) {
                r = 0.82F;
                g = 0.96F;
                b = 1.0F;
                alpha = 0.65F;
            } else if (weight == WardenHearing.KIND_SUBTLE) {
                r = 0.42F;
                g = 0.78F;
                b = 1.0F;
                alpha = 0.45F;
            } else {
                r = 0.3F;
                g = 0.5F;
                b = 0.8F;
                alpha = 0.3F;
            }
            if (!step) {
                // a ring rolling out from it (one; a loud sound's is wider and lasts longer)
                float radius = weight == WardenHearing.KIND_OBVIOUS ? 3.0F : weight == WardenHearing.KIND_SUBTLE ? 1.8F : 1.3F;
                float life = weight == WardenHearing.KIND_OBVIOUS ? 28.0F : 20.0F;
                float t = age / life;
                if (t < 1.0F) {
                    float out = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
                    d.ring(h.at().add(0.0, 0.06, 0.0), radius * out, 0.04F + 0.05F * (1.0F - t), r, g, b, (float) Math.pow(1.0F - t, 1.8) * alpha);
                }
                float flash = 1.0F - age / 8.0F;
                if (flash > 0.0F) {
                    d.billboard(h.at(), 0.08F + 0.14F * flash, r, g, b, flash * alpha * 0.8F);
                }
            }
            if (!counted) {
                continue;
            }
            // the bead running to you, with a short tail (as vanilla's vibration)
            float travel = 8.0F + h.travel() * 0.35F;
            float u = age / travel;
            if (u > 0.0F && u <= 1.0F) {
                for (int k = 0; k < 5; k++) {
                    float uk = u - k * 0.03F;
                    if (uk < 0.0F) {
                        break;
                    }
                    float e = uk * uk * (3.0F - 2.0F * uk);
                    Vec3 p = h.at().lerp(head, e).add(0.0, Mth.sin(uk * Mth.PI) * Math.min(2.0F, h.travel() * 0.1F), 0.0);
                    float tail = 1.0F - k / 5.0F;
                    d.billboard(p, (0.07F + (k == 0 ? 0.03F : 0.0F)) * tail, r, g, b, alpha * tail * (1.0F - u * 0.4F));
                }
            }
        }
    }

    /** a sniff: an arc of scent sweeping out across the cone (three, for its depth), with its edges. */
    private static void drawSniffs(Draw d, float time) {
        SNIFFS.removeIf(s -> time - s.born() > SNIFF_LIFE);
        for (Sniff s : SNIFFS) {
            float t = (time - s.born()) / SNIFF_LIFE;
            float reach = s.range() * (1.0F - (1.0F - t) * (1.0F - t));
            float fade = (1.0F - t) * 0.85F;
            float tilt = Math.min(s.angle(), 25.0F) * 0.6F;
            d.fan(s.from(), s.yaw(), s.pitch(), s.angle(), reach, 0.2F + reach * 0.012F, 0.25F, 1.0F, 0.85F, fade);
            d.fan(s.from(), s.yaw(), s.pitch() - tilt, s.angle() * 0.85F, reach * 0.97F, 0.15F, 0.25F, 1.0F, 0.85F, fade * 0.45F);
            d.fan(s.from(), s.yaw(), s.pitch() + tilt, s.angle() * 0.85F, reach * 0.97F, 0.15F, 0.25F, 1.0F, 0.85F, fade * 0.45F);
            Vec3 l = dir(s.yaw() - s.angle(), s.pitch()).scale(reach);
            Vec3 rr = dir(s.yaw() + s.angle(), s.pitch()).scale(reach);
            d.line(s.from(), s.from().add(l), 0.03F, 0.25F, 1.0F, 0.85F, fade * 0.35F);
            d.line(s.from(), s.from().add(rr), 0.03F, 0.25F, 1.0F, 0.85F, fade * 0.35F);
        }
    }

    /** threads of scent drawn back from whoever a sniff caught: a burst at them, then a wavering trail to you. */
    private static void drawScents(Minecraft mc, Draw d, float time, float pt, Vec3 head) {
        SCENTS.removeIf(s -> time - s.born() > SCENT_LIFE);
        for (Scent s : SCENTS) {
            float age = time - s.born();
            if (age < 0.0F) {
                continue;
            }
            Entity e = mc.level.getEntity(s.entity());
            Vec3 from = e != null ? e.getPosition(pt).add(0.0, e.getBbHeight() * 0.5, 0.0) : s.at().add(0.0, 1.0, 0.0);
            Vec3 feet = e != null ? e.getPosition(pt) : s.at();
            float t = age / SCENT_LIFE;
            d.ring(feet.add(0.0, 0.06, 0.0), 0.4F + 1.4F * t, 0.08F, 0.25F, 1.0F, 0.85F, (1.0F - t) * 0.9F);
            Vec3 along = head.subtract(from);
            Vec3 side = along.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            for (int k = 0; k < 14; k++) {
                float u = Math.min(1.0F, t * 1.4F) - k * 0.035F;
                if (u < 0.0F) {
                    break;
                }
                float wobble = Mth.sin(u * 14.0F + time * 0.6F) * 0.35F * Mth.sin(u * Mth.PI);
                Vec3 p = from.add(along.scale(u)).add(side.scale(wobble)).add(0.0, Mth.sin(u * Mth.PI) * 0.6F, 0.0);
                float tail = 1.0F - k / 14.0F;
                d.billboard(p, 0.08F * tail + 0.03F, 0.25F, 1.0F, 0.85F, tail * (1.0F - t) * 0.9F);
            }
        }
    }

    /**
     * your sniff as you hold it: the cone it'll cover, faint — and scent already drifting to you from everyone inside
     * it (through walls), so you know you're on the right track before you let it out.
     */
    private static void drawPreview(Minecraft mc, Draw d, float time, float pt) {
        Player me = mc.player;
        if (WardenBehaviour.state(me) != WardenBehaviour.SNIFFING) {
            return;
        }
        float share;
        double range;
        double angle;
        try {
            share = Mth.clamp(me.getData(WitchModAttachments.PUPPET_FUSE) / (float) Math.max(1,
                    com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_CHARGE_TICKS.get()), 0.0F, 1.0F);
            range = Mth.lerp(share, com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_MIN_RANGE.get(),
                    com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_MAX_RANGE.get());
            angle = Mth.lerp(share, com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_MIN_ANGLE.get(),
                    com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_MAX_ANGLE.get());
        } catch (IllegalStateException e) {
            return;
        }
        Vec3 eye = me.getEyePosition(pt);
        float yaw = me.getViewYRot(pt);
        float pitch = me.getViewXRot(pt);
        d.fan(eye, yaw, pitch, (float) angle, (float) range, 0.1F, 0.25F, 1.0F, 0.85F, 0.14F + 0.06F * Mth.sin(time * 0.3F));
        // whoever's in the cone right now (the same test the sniff makes): their scent drifts in to you
        Vec3 look = me.getViewVector(pt);
        double cos = Math.cos(Math.toRadians(angle));
        Vec3 nose = eye.add(look.scale(0.6)).subtract(0.0, 0.25, 0.0);
        for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(range), e -> WardenHearing.noticeable(me, e))) {
            Vec3 at = e.getPosition(pt).add(0.0, e.getBbHeight() * 0.5, 0.0);
            Vec3 to = at.subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0E-3 || to.scale(1.0 / dist).dot(look) < cos) {
                continue;
            }
            Vec3 along = nose.subtract(at);
            Vec3 side = along.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            for (int k = 0; k < 6; k++) {
                float f = (time * 0.04F + k / 6.0F + (e.getId() % 7) * 0.13F) % 1.0F; // 0 at them, 1 at your nose
                float wobble = Mth.sin(f * 11.0F + time * 0.5F + e.getId()) * 0.3F * Mth.sin(f * Mth.PI);
                Vec3 p = at.add(along.scale(f)).add(side.scale(wobble));
                d.billboard(p, 0.05F + 0.03F * (1.0F - f), 0.25F, 1.0F, 0.85F, 0.6F * Mth.sin(f * Mth.PI));
            }
            d.ring(e.getPosition(pt).add(0.0, 0.05, 0.0), 0.55F + 0.1F * Mth.sin(time * 0.4F), 0.05F, 0.25F, 1.0F, 0.85F, 0.35F);
        }
    }

    /**
     * the noticed: pips over their heads (how close to marked they are). the marked: a red halo at their feet pacing
     * with your heart, its arc the time left on the mark, a red diamond over them and a column of red — and past where
     * you can see them, a tall red pillar where they are.
     */
    private static void drawKnown(Minecraft mc, Draw d, float time, float pt) {
        int need;
        int max;
        try {
            need = com.oliver.witchmod.Config.PUPPETEER_WARDEN_MARK_POINTS.get();
            max = Math.max(1, com.oliver.witchmod.Config.PUPPETEER_WARDEN_MARK_MAX_TICKS.get());
        } catch (IllegalStateException e) {
            return;
        }
        int delay = WardenSenses.heartDelay(mc.player.getData(WitchModAttachments.PUPPET_WARDEN_ANGER));
        float beat = WardenSenses.pulse(mc.player.tickCount % delay + pt, delay);
        Vec3 me = mc.player.getEyePosition(pt);
        for (Map.Entry<Integer, WardenSenses.Known> entry : WardenSenses.KNOWN.entrySet()) {
            WardenSenses.Known k = entry.getValue();
            int left = WardenSenses.markLeft(entry.getKey());
            Entity e = mc.level.getEntity(entry.getKey());
            boolean near = e != null && e.distanceToSqr(me) < 48.0 * 48.0;
            if (left > 0) {
                Vec3 at = near ? e.getPosition(pt) : new Vec3(k.x(), k.y() - 0.9, k.z());
                float dist = (float) Math.sqrt(at.distanceToSqr(me));
                float scale = Math.max(1.0F, dist / 14.0F); // stays readable however far
                float glow = 0.8F + 0.2F * beat; // (only a slight pulse with your heart)
                float height = near ? e.getBbHeight() : 1.8F;
                d.arc(at.add(0.0, 0.05, 0.0), 0.9F, 0.07F, left / (float) max, time * 0.05F, 1.0F, 0.15F, 0.1F, 0.75F * glow);
                d.diamond(at.add(0.0, height + 0.55 * scale, 0.0), 0.14F * scale, 1.0F, 0.2F, 0.12F, 0.85F * glow);
                float column = near ? 4.0F : 48.0F;
                d.pillar(at, column, 0.05F * scale, 1.0F, 0.15F, 0.1F, near ? 0.25F * glow : 0.5F * glow);
                if (!near) {
                    d.pillar(at, column * 0.6F, 0.18F * scale, 1.0F, 0.12F, 0.08F, 0.15F * glow); // its haze
                }
            } else if (near && k.points() > 0) {
                Vec3 top = e.getPosition(pt).add(0.0, e.getBbHeight() + 0.45, 0.0);
                Vector3f left3 = d.left;
                for (int i = 0; i < need; i++) {
                    float off = (i - (need - 1) * 0.5F) * 0.28F;
                    Vec3 at = top.add(left3.x() * off, left3.y() * off, left3.z() * off);
                    boolean lit = i < k.points();
                    d.diamond(at, lit ? 0.09F : 0.06F, lit ? 0.35F : 0.2F, lit ? 0.85F : 0.4F, lit ? 1.0F : 0.6F, lit ? 0.9F : 0.3F);
                }
            }
        }
    }

    /** the unit vector for a yaw / pitch (minecraft's own angles). */
    private static Vec3 dir(float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /** quads of light, relative to the camera. */
    private static final class Draw {
        final VertexConsumer vc;
        final Matrix4f m;
        final Vec3 cam;
        final Vector3f left;
        final Vector3f up;
        /** how strongly it's all drawn (the client's wardenSenseIntensity). */
        final float strength;

        Draw(VertexConsumer vc, Matrix4f m, Vec3 cam, Vector3f left, Vector3f up, float strength) {
            this.vc = vc;
            this.m = m;
            this.cam = cam;
            this.left = left;
            this.up = up;
            this.strength = strength;
        }

        void vertex(double x, double y, double z, float r, float g, float b, float a) {
            vc.addVertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                    .setColor(r, g, b, Mth.clamp(a * strength, 0.0F, 1.0F));
        }

        /** a footprint lying on the ground: {@code length} along {@code forward}, {@code width} along {@code side}. */
        void print(Vec3 c, Vec3 forward, Vec3 side, float length, float width, float r, float g, float b, float a) {
            if (a <= 0.004F) {
                return;
            }
            Vec3 f = forward.scale(length);
            Vec3 w = side.scale(width);
            quad(c.add(f).add(w), c.add(f).subtract(w), c.subtract(f).subtract(w), c.subtract(f).add(w), r, g, b, a);
        }

        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 e, float r, float g, float bl, float alpha) {
            vertex(a.x, a.y, a.z, r, g, bl, alpha);
            vertex(b.x, b.y, b.z, r, g, bl, alpha);
            vertex(c.x, c.y, c.z, r, g, bl, alpha);
            vertex(e.x, e.y, e.z, r, g, bl, alpha);
        }

        /** a square of light facing you. */
        void billboard(Vec3 p, float size, float r, float g, float b, float a) {
            if (a <= 0.004F || size <= 0.0F) {
                return;
            }
            float lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
            float ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
            vertex(p.x + lx + ux, p.y + ly + uy, p.z + lz + uz, r, g, b, a);
            vertex(p.x - lx + ux, p.y - ly + uy, p.z - lz + uz, r, g, b, a);
            vertex(p.x - lx - ux, p.y - ly - uy, p.z - lz - uz, r, g, b, a);
            vertex(p.x + lx - ux, p.y + ly - uy, p.z + lz - uz, r, g, b, a);
        }

        /** a diamond of light facing you. */
        void diamond(Vec3 p, float size, float r, float g, float b, float a) {
            if (a <= 0.004F) {
                return;
            }
            vertex(p.x + up.x() * size, p.y + up.y() * size, p.z + up.z() * size, r, g, b, a);
            vertex(p.x + left.x() * size, p.y + left.y() * size, p.z + left.z() * size, r, g, b, a);
            vertex(p.x - up.x() * size, p.y - up.y() * size, p.z - up.z() * size, r, g, b, a);
            vertex(p.x - left.x() * size, p.y - left.y() * size, p.z - left.z() * size, r, g, b, a);
        }

        /** a flat ring lying on the ground around {@code c}. */
        void ring(Vec3 c, float radius, float width, float r, float g, float b, float a) {
            arc(c, radius, width, 1.0F, 0.0F, r, g, b, a);
        }

        /** part of a ground ring: {@code share} of the way round, starting at {@code start} radians. */
        void arc(Vec3 c, float radius, float width, float share, float start, float r, float g, float b, float a) {
            if (a <= 0.004F || radius <= 0.0F || share <= 0.0F) {
                return;
            }
            int segments = Math.max(6, (int) (40 * share));
            float inner = Math.max(0.0F, radius - width * 0.5F);
            float outer = radius + width * 0.5F;
            for (int i = 0; i < segments; i++) {
                float a0 = start + Mth.TWO_PI * share * i / segments;
                float a1 = start + Mth.TWO_PI * share * (i + 1) / segments;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                vertex(c.x + c0 * inner, c.y, c.z + s0 * inner, r, g, b, a);
                vertex(c.x + c0 * outer, c.y, c.z + s0 * outer, r, g, b, a);
                vertex(c.x + c1 * outer, c.y, c.z + s1 * outer, r, g, b, a);
                vertex(c.x + c1 * inner, c.y, c.z + s1 * inner, r, g, b, a);
            }
        }

        /** a column of light rising from {@code base}, facing you, fading towards the top. */
        void pillar(Vec3 base, float height, float width, float r, float g, float b, float a) {
            if (a <= 0.004F) {
                return;
            }
            Vec3 to = base.subtract(cam);
            Vec3 side = new Vec3(-to.z, 0.0, to.x);
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(width, 0.0, 0.0) : side.normalize().scale(width);
            vertex(base.x - side.x, base.y, base.z - side.z, r, g, b, a);
            vertex(base.x + side.x, base.y, base.z + side.z, r, g, b, a);
            vertex(base.x + side.x, base.y + height, base.z + side.z, r, g, b, 0.0F);
            vertex(base.x - side.x, base.y + height, base.z - side.z, r, g, b, 0.0F);
        }

        /** a ribbon of light from {@code a} to {@code b}, facing you. */
        void line(Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
            if (alpha <= 0.004F) {
                return;
            }
            Vec3 along = b.subtract(a);
            Vec3 side = along.cross(a.subtract(cam));
            if (side.lengthSqr() < 1.0E-8) {
                return;
            }
            side = side.normalize().scale(width);
            quad(a.subtract(side), a.add(side), b.add(side), b.subtract(side), r, g, bl, alpha);
        }

        /** an arc across a cone's width, {@code reach} out from {@code from}: a sniff's sweep. */
        void fan(Vec3 from, float yaw, float pitch, float angle, float reach, float width, float r, float g, float b, float a) {
            if (a <= 0.004F || reach <= 0.05F) {
                return;
            }
            int segments = Math.max(6, (int) (angle / 4.0F));
            Vec3 prev = null;
            for (int i = 0; i <= segments; i++) {
                float across = -angle + 2.0F * angle * i / segments;
                Vec3 p = from.add(dir(yaw + across, pitch).scale(reach));
                if (prev != null) {
                    // brighter in the middle of the cone, fading to its edges
                    float mid = 1.0F - Math.abs(across) / Math.max(1.0F, angle) * 0.6F;
                    line(prev, p, width, r, g, b, a * mid);
                }
                prev = p;
            }
        }
    }

    // --- the model -------------------------------------------------------------------------------------

    /**
     * one warden puppet's dummy, one tick on (PuppetDummies.animate): its mood, the tendrils and heart winding down and
     * the heart beating at its mood's pace (others hear it, as vanilla), each one-off animation once, and the dig's dust.
     */
    static void animate(Minecraft mc, Player p, Warden warden) {
        WardenAccessor acc = (WardenAccessor) warden;
        int anger = p.getData(WitchModAttachments.PUPPET_WARDEN_ANGER);
        warden.getEntityData().set(WardenAccessor.witchmodAngerLevel(), anger);
        int tendril = acc.witchmodGetTendril();
        acc.witchmodSetTendrilO(tendril);
        if (tendril > 0) {
            acc.witchmodSetTendril(tendril - 1);
        }
        int heart = acc.witchmodGetHeart();
        acc.witchmodSetHeartO(heart);
        if (heart > 0) {
            acc.witchmodSetHeart(heart - 1);
        }
        boolean under = WardenBehaviour.burrowed(p);
        if (p.tickCount % WardenSenses.heartDelay(anger) == 0) {
            acc.witchmodSetHeart(10);
            if (p != mc.player) { // (your own beat is WardenSenses')
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE,
                        under ? 1.0F : 5.0F, under ? 0.7F : 1.0F, false);
            }
        }
        int fx = p.getData(WitchModAttachments.PUPPET_WARDEN_FX);
        Integer last = LAST_FX.put(p.getUUID(), fx);
        if (last != null && fx != last) {
            switch (fx & 0xFF) {
                case WardenBehaviour.FX_SWING, WardenBehaviour.FX_TENDRILS, WardenBehaviour.FX_SONIC -> warden.handleEntityEvent((byte) (fx & 0xFF));
                case WardenBehaviour.FX_SONIC_STOP -> warden.sonicBoomAnimationState.stop();
                default -> {
                }
            }
        }
        // the ground breaking up around it, for the first 4.5s of a dig or an emergence (vanilla's own dust)
        Pose pose = WardenBehaviour.pose(p);
        // each pose's animation ends with the pose (vanilla's never needed to: a dug-in warden is gone — so its dig
        // animation held its last, buried frame, and the climbed-out warden was invisible)
        if (pose != Pose.DIGGING) {
            warden.diggingAnimationState.stop();
        }
        if (pose != Pose.EMERGING) {
            warden.emergeAnimationState.stop();
        }
        if (pose != Pose.ROARING) {
            warden.roarAnimationState.stop();
        }
        if (pose != Pose.SNIFFING) {
            warden.sniffAnimationState.stop();
        }
        if (pose == Pose.DIGGING || pose == Pose.EMERGING) {
            AnimationState state = pose == Pose.DIGGING ? warden.diggingAnimationState : warden.emergeAnimationState;
            BlockState ground = mc.level.getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ()));
            if (state.getAccumulatedTime() < 4500L && ground.getRenderShape() != RenderShape.INVISIBLE) {
                for (int i = 0; i < 30; i++) {
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            p.getX() + Mth.randomBetween(p.getRandom(), -0.7F, 0.7F), p.getY(),
                            p.getZ() + Mth.randomBetween(p.getRandom(), -0.7F, 0.7F), 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /** a dummy's gone (the puppet's over, or you left the world). */
    static void forget(UUID player) {
        LAST_FX.remove(player);
    }

    static void clearAll() {
        LAST_FX.clear();
        HEARD.clear();
        PRINTS.clear();
        NEXT_FOOT.clear();
        SNIFFS.clear();
        SCENTS.clear();
    }
}
