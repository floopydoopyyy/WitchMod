package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.BodyguardEntity;

/**
 * Renders the Bodyguard's sunglasses, locked to the head bone so they turn with its head. The little box is
 * baked from {@link BodyguardRenderer#SUNGLASSES_LAYER}; here we just re-apply the parent model's head
 * transform and draw it, textured from {@code textures/entity/bodyguard/sunglasses.png}.
 */
public final class SunglassesLayer<T extends BodyguardEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/bodyguard/sunglasses.png");

    private final ModelPart glasses;

    public SunglassesLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.glasses = models.bakeLayer(BodyguardRenderer.SUNGLASSES_LAYER);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose); // ride the skull
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        glasses.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
