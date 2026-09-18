package com.oliver.witchmod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.BodyguardEntity;

/**
 * draws the bodyguard as an ordinary vanilla skeleton in iron armour with sunglasses across the eyes — a
 * person's hired muscle, deliberately read as a plain skeleton rather than a bespoke boss. sunglasses texture
 * lives at {@code assets/witchmod/textures/entity/bodyguard/sunglasses.png} (a solid-black placeholder ships).
 */
public final class BodyguardRenderer extends HumanoidMobRenderer<BodyguardEntity, BodyguardModel<BodyguardEntity>> {
    /** the little box across the eyes. Registered as its own layer definition and baked in the ctor. */
    public static final ModelLayerLocation SUNGLASSES_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "bodyguard"), "sunglasses");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

    public BodyguardRenderer(EntityRendererProvider.Context context) {
        // the SKELETON layer carries the thin-armed skeleton geometry; BodyguardModel adds proper held-item
        // arm posing (SkeletonModel only adds the bow-aiming pose, and requires a RangedAttackMob).
        super(context, new BodyguardModel<>(context.bakeLayer(ModelLayers.SKELETON)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer())); // the drawn sword
        addLayer(new SunglassesLayer<>(this, context.getModelSet()));
    }

    /** A thin box sitting just in front of the skull at eye level. */
    public static LayerDefinition createSunglassesLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // sits just off the front of the skull (front face is z=-4); nudged out to -4.9 so it doesn't clip in.
        root.addOrReplaceChild("glasses",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -5.0F, -4.9F, 8.0F, 2.0F, 0.6F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ResourceLocation getTextureLocation(BodyguardEntity entity) {
        return TEXTURE;
    }
}
