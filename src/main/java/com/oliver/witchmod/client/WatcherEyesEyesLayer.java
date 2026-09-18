package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.WatcherEyesEntity;

/** the glowing eyes — the only visible part of a {@link WatcherEyesEntity}. */
@OnlyIn(Dist.CLIENT)
public final class WatcherEyesEyesLayer extends RenderLayer<WatcherEyesEntity, WatcherEyesModel<WatcherEyesEntity>> {
    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/watcher_eyes_eyes.png"));

    public WatcherEyesEyesLayer(RenderLayerParent<WatcherEyesEntity, WatcherEyesModel<WatcherEyesEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, WatcherEyesEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        // the eyes BLINK — a quick close every few seconds, otherwise open and staring. Deterministic per entity
        // (and per-pair phase-shifted by the id) so a whole ring of them don't blink in unison.
        if (blinking(entity, ageInTicks)) {
            return;
        }
        this.getParentModel().renderToBuffer(poseStack, buffer.getBuffer(EYES), 15728640, OverlayTexture.NO_OVERLAY, -1);
    }

    /** A ~2-tick blink roughly every 2-4s, staggered per entity so they don't all close at once. */
    private static boolean blinking(WatcherEyesEntity entity, float ageInTicks) {
        int period = 55 + (entity.getId() * 7 & 31);     // 55..86 ticks between blinks, per-entity
        int phase = (entity.getId() * 13) % period;
        int t = ((int) ageInTicks + phase) % period;
        return t < 2;                                    // closed for the first ~2 ticks of each period
    }
}
