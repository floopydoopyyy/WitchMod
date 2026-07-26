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
 * Draws the Bodyguard as an ordinary (vanilla-textured) skeleton, wearing its iron armour via the standard
 * armour layer, plus a {@link SunglassesLayer} across the eyes. The skeleton read is deliberate — a person's
 * hired muscle, unmistakably a skeleton, not some bespoke boss.
 *
 * <p><b>Sunglasses texture:</b> {@code assets/witchmod/textures/entity/bodyguard/sunglasses.png} (32x32). A
 * solid-black placeholder ships with the mod; see the guide in CLAUDE.md 13.7 for authoring a nicer pair.
 */
public final class BodyguardRenderer extends HumanoidMobRenderer<BodyguardEntity, BodyguardModel<BodyguardEntity>> {
    /** The little box across the eyes. Registered as its own layer definition and baked in the ctor. */
    public static final ModelLayerLocation SUNGLASSES_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "bodyguard"), "sunglasses");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

    public BodyguardRenderer(EntityRendererProvider.Context context) {
        // The SKELETON layer carries the thin-armed skeleton geometry; BodyguardModel adds proper held-item
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
        root.addOrReplaceChild("glasses",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -5.0F, -4.6F, 8.0F, 2.0F, 0.6F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ResourceLocation getTextureLocation(BodyguardEntity entity) {
        return TEXTURE;
    }
}
