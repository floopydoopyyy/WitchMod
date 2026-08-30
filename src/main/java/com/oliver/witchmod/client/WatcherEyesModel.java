package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.entities.WatcherEyesEntity;

/**
 * A single small "face" plate for the {@link WatcherEyesEntity}. Its main texture is fully transparent — only
 * the emissive {@link WatcherEyesEyesLayer} draws anything, so all you ever see is a pair of glowing eyes
 * hanging in the dark.
 */
@OnlyIn(Dist.CLIENT)
public final class WatcherEyesModel<T extends WatcherEyesEntity> extends EntityModel<T> {
    private final ModelPart root;

    public WatcherEyesModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("face", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -1.5F, -0.5F, 6.0F, 3.0F, 1.0F),
                PartPose.offset(0.0F, 19.0F, 0.0F));
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
        this.root.xRot = headPitch * ((float) Math.PI / 180.0F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
