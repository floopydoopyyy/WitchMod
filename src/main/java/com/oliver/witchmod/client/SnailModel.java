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

import com.oliver.witchmod.entities.SnailEntity;

/**
 * A tiny, animation-free snail built entirely in code (no Blockbench needed): a flat slug foot, a rounded
 * shell hump on its back, and two little eye stalks. Textured against
 * {@code assets/witchmod/textures/entity/snail.png} (32x32) — a nicer skin can be dropped in later; until then
 * it renders as the missing-texture checker.
 */
@OnlyIn(Dist.CLIENT)
public final class SnailModel<T extends SnailEntity> extends EntityModel<T> {
    private final ModelPart root;

    public SnailModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("snail", CubeListBuilder.create()
                        // slug foot/body
                        .texOffs(0, 0).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 2.0F, 8.0F)
                        // shell hump
                        .texOffs(0, 11).addBox(-2.5F, -6.5F, -1.5F, 5.0F, 5.0F, 5.0F)
                        // eye stalks (tucked into the free top-right of the sheet so nothing overlaps)
                        .texOffs(26, 0).addBox(-1.5F, -4.0F, -4.5F, 1.0F, 2.0F, 1.0F)
                        .texOffs(26, 4).addBox(0.5F, -4.0F, -4.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // no animation
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
