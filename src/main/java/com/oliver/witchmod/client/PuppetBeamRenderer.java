package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;

/**
 * puppeteer: a guardian puppet's laser, drawn the way vanilla draws a guardian's (same texture, same spinning
 * crossed quads) but from the puppet's eye to wherever its player is aiming. it warms from purple to yellow-white and
 * thickens as it charges towards the burst. drawn in the world pass so you see your own beam in first person too.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetBeamRenderer {
    private static final RenderType BEAM = RenderType.entityCutoutNoCull(
            ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png"));

    private PuppetBeamRenderer() {}

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.level == null) {
            return;
        }
        double range;
        int fullTicks;
        try {
            range = Config.PUPPETEER_GUARDIAN_BEAM_RANGE.get();
            fullTicks = Math.max(1, Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get());
        } catch (IllegalStateException e) {
            return; // not connected / config not synced yet
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        boolean drew = false;
        for (Player p : mc.level.players()) {
            int hold = p.getData(WitchModAttachments.PUPPET_FUSE);
            BlessingPuppeteer.PuppetType type = BlessingPuppeteer.PuppetType.byId(p.getData(WitchModAttachments.PUPPET_TYPE));
            if (hold <= 0 || type == null || type.move() != BlessingPuppeteer.Move.BEAM) {
                continue;
            }
            Vec3 look = p.getViewVector(pt);
            Vec3 start;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                // from just under your view, so you can see it leave you
                start = cam.add(look.scale(0.4)).add(0.0, -0.3 * Math.min(1.0, p.getEyeHeight() / 1.62), 0.0);
            } else {
                Vec3 feet = p.getPosition(pt);
                start = feet.add(0.0, type.entityType().getDimensions().eyeHeight(), 0.0).add(look.scale(0.4));
            }
            Vec3 end = BlessingPuppeteer.beamHit(p, range).end();
            float progress = Mth.clamp((hold + pt) / fullTicks, 0.0F, 1.0F);
            pose.pushPose();
            pose.translate(start.x - cam.x, start.y - cam.y, start.z - cam.z);
            drawBeam(pose, buffers.getBuffer(BEAM), end.subtract(start), p.tickCount + pt, progress);
            pose.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch(BEAM);
        }
    }

    /** vanilla's guardian beam geometry, along {@code along} from the current origin. */
    private static void drawBeam(PoseStack pose, VertexConsumer vc, Vec3 along, float time, float progress) {
        float length = (float) along.length();
        if (length < 0.05F) {
            return;
        }
        Vec3 dir = along.normalize();
        pose.mulPose(Axis.YP.rotation(Mth.HALF_PI - (float) Math.atan2(dir.z, dir.x)));
        pose.mulPose(Axis.XP.rotation((float) Math.acos(dir.y)));
        float spin = time * 0.05F * -1.5F * (1.0F + progress * 2.0F); // spins up as it charges
        float heat = progress * progress;
        int r = 64 + (int) (heat * 191.0F);
        int g = 32 + (int) (heat * 191.0F);
        int b = 128 - (int) (heat * 64.0F);
        float inner = 0.2F * (1.0F + progress * 0.6F);
        float outer = 0.282F * (1.0F + progress * 0.6F);
        float scroll = time * 0.5F % 1.0F;
        float v0 = -1.0F + scroll;
        float v1 = length * 2.5F + v0;
        PoseStack.Pose last = pose.last();
        float a = spin + Mth.PI;
        float c = spin;
        float d = spin + Mth.HALF_PI;
        float e = spin + Mth.PI * 1.5F;
        vertex(vc, last, Mth.cos(a) * inner, length, Mth.sin(a) * inner, r, g, b, 0.4999F, v1);
        vertex(vc, last, Mth.cos(a) * inner, 0.0F, Mth.sin(a) * inner, r, g, b, 0.4999F, v0);
        vertex(vc, last, Mth.cos(c) * inner, 0.0F, Mth.sin(c) * inner, r, g, b, 0.0F, v0);
        vertex(vc, last, Mth.cos(c) * inner, length, Mth.sin(c) * inner, r, g, b, 0.0F, v1);
        vertex(vc, last, Mth.cos(d) * inner, length, Mth.sin(d) * inner, r, g, b, 0.4999F, v1);
        vertex(vc, last, Mth.cos(d) * inner, 0.0F, Mth.sin(d) * inner, r, g, b, 0.4999F, v0);
        vertex(vc, last, Mth.cos(e) * inner, 0.0F, Mth.sin(e) * inner, r, g, b, 0.0F, v0);
        vertex(vc, last, Mth.cos(e) * inner, length, Mth.sin(e) * inner, r, g, b, 0.0F, v1);
        // the glowing cap where it lands
        float cap = ((int) time) % 2 == 0 ? 0.5F : 0.0F;
        float q1 = spin + Mth.PI * 0.75F;
        float q2 = spin + Mth.PI * 0.25F;
        float q3 = spin + Mth.PI * 1.25F;
        float q4 = spin + Mth.PI * 1.75F;
        vertex(vc, last, Mth.cos(q1) * outer, length, Mth.sin(q1) * outer, r, g, b, 0.5F, cap + 0.5F);
        vertex(vc, last, Mth.cos(q2) * outer, length, Mth.sin(q2) * outer, r, g, b, 1.0F, cap + 0.5F);
        vertex(vc, last, Mth.cos(q4) * outer, length, Mth.sin(q4) * outer, r, g, b, 1.0F, cap);
        vertex(vc, last, Mth.cos(q3) * outer, length, Mth.sin(q3) * outer, r, g, b, 0.5F, cap);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b,
                               float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
