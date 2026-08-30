package com.oliver.witchmod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.oliver.witchmod.ClientConfig;

/**
 * The EXPERIMENTAL live partner-POV for the Splitscreen curse: a genuine second {@code LevelRenderer.renderLevel}
 * pass from the partner's camera into an off-screen framebuffer, blitted into the side panel — the real
 * console-splitscreen effect. It's off by default ({@code splitscreenLivePov}) and every call is wrapped so a
 * single failure disables it for the session and {@link SplitscreenClient} shows the static fallback panel
 * instead; it never brings the game down.
 */
public final class SplitscreenPov {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    /** The panel is a genuine second world render (≈2× GPU cost), so we refresh it like a video feed rather
     *  than every frame — ~20 fps is plenty for a side panel and roughly halves the extra cost at 40+ fps. */
    private static final long REFRESH_INTERVAL_MS = 50L;
    private static RenderTarget target;
    private static java.lang.reflect.Field mainTargetField;
    private static boolean failed;
    private static boolean hasContent;
    private static long lastRenderMs;
    private static boolean loggedOk;

    private SplitscreenPov() {}

    /**
     * ⚠ SAFETY: the live pass is refused on FABULOUS graphics. Fabulous composites translucency through a
     * whole-screen transparency chain that our second {@code renderLevel} pass fights frame-to-frame,
     * producing dangerous full-screen strobing. On Fast/Fancy translucency renders inline and there's no
     * such conflict. When this returns false the static fallback panel is shown instead.
     */
    private static boolean graphicsAllowLivePov() {
        return Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FABULOUS;
    }

    /** Renders the partner's view into the off-screen target. Called before the HUD (world already drawn). */
    static void renderPartnerView(int partnerId, DeltaTracker delta) {
        if (!ClientConfig.SPLITSCREEN_LIVE_POV.get() || failed || !graphicsAllowLivePov()) {
            return;
        }
        // Throttle to a video-feed cadence; between refreshes the cached target is re-blitted unchanged.
        long now = System.currentTimeMillis();
        if (hasContent && now - lastRenderMs < REFRESH_INTERVAL_MS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(partnerId) instanceof LivingEntity partner)) {
            return;
        }
        lastRenderMs = now;
        RenderTarget realMain = mc.getMainRenderTarget();
        boolean swapped = false;
        try {
            if (mainTargetField == null) {
                mainTargetField = Minecraft.class.getDeclaredField("mainRenderTarget");
                mainTargetField.setAccessible(true);
            }
            // Size the target to the HALF-SCREEN region it fills (in real pixels) so the blit is 1:1 and the
            // world is rendered at the panel's own aspect — a true console half-and-half, not a squished panel.
            int wantW = Math.max(1, mc.getWindow().getWidth() / 2);
            int wantH = Math.max(1, mc.getWindow().getHeight());
            if (target == null || target.width != wantW || target.height != wantH) {
                if (target != null) {
                    target.destroyBuffers();
                }
                target = new TextureTarget(wantW, wantH, true, Minecraft.ON_OSX);
            }
            target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            target.clear(Minecraft.ON_OSX);

            // ⚠ renderLevel grabs Minecraft.getMainRenderTarget() internally and draws to it — so we point that
            // AT OUR OFF-SCREEN TARGET for the duration. Without this the partner's world splatters over your
            // real screen (the "transparent POV overlay" bug); your screen was already fully drawn by now, so
            // it stays untouched.
            mainTargetField.set(mc, target);
            swapped = true;
            target.bindWrite(true);

            Camera cam = new Camera();
            cam.setup(mc.level, (Entity) partner, false, false, delta.getGameTimeDeltaPartialTick(false));
            // Perspective at the HALF-region aspect (getProjectionMatrix uses the full-window aspect, which
            // would stretch the world horizontally into the tall half-panel).
            double fov = mc.options.fov().get();
            float far = Math.max(mc.options.getEffectiveRenderDistance() * 16.0F, 64.0F) * 4.0F;
            Matrix4f proj = new Matrix4f().perspective((float) (fov * Math.PI / 180.0), (float) wantW / (float) wantH, 0.05F, far);
            Matrix4f frustum = new Matrix4f()
                    .rotate(Axis.XP.rotationDegrees(cam.getXRot()))
                    .rotate(Axis.YP.rotationDegrees(cam.getYRot() + 180.0F));

            mc.levelRenderer.renderLevel(delta, false, cam, mc.gameRenderer, mc.gameRenderer.lightTexture(), frustum, proj);
            hasContent = true;
            if (!loggedOk) {
                loggedOk = true;
                LOG.info("[Splitscreen] live POV second render pass succeeded (partner id {})", partnerId);
            }
        } catch (Throwable t) {
            failed = true;
            LOG.error("[Splitscreen] live POV RENDER PASS failed — falling back to the static panel", t);
        } finally {
            if (swapped) {
                try {
                    mainTargetField.set(mc, realMain);
                } catch (Exception ignored) {
                    // extremely unlikely; the game keeps its real target reference
                }
            }
            realMain.bindWrite(true);
        }
    }

    /** Blits the rendered partner view into the panel rect. Returns false if there's nothing live to show. */
    static boolean blit(GuiGraphics g, int x, int y, int w, int h) {
        if (!ClientConfig.SPLITSCREEN_LIVE_POV.get() || failed || target == null || !hasContent
                || !graphicsAllowLivePov()) {
            return false;
        }
        try {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.disableBlend(); // the partner view is fully opaque — no ghosting of your own view underneath
            Matrix4f mat = g.pose().last().pose();
            // Framebuffer textures are bottom-up, so V runs 1→0 top to bottom.
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bb.addVertex(mat, x, y + h, 0).setUv(0.0F, 0.0F);
            bb.addVertex(mat, x + w, y + h, 0).setUv(1.0F, 0.0F);
            bb.addVertex(mat, x + w, y, 0).setUv(1.0F, 1.0F);
            bb.addVertex(mat, x, y, 0).setUv(0.0F, 1.0F);
            BufferUploader.drawWithShader(bb.buildOrThrow());
            RenderSystem.disableBlend();
            return true;
        } catch (Throwable t) {
            failed = true;
            LOG.error("[Splitscreen] live POV BLIT failed — falling back to the static panel", t);
            return false;
        }
    }
}
