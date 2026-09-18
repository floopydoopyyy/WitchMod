package com.oliver.witchmod.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.TaxManEntity;

/**
 * draws the Tax Man as a humanoid in a player skin.
 *
 * <p>Uses vanilla's {@link PlayerModel} rather than a bespoke model so he reads unmistakably as a person
 * rather than a mob — which is what makes an unkillable bureaucrat standing in your base unsettling instead
 * of just another hostile.
 *
 * <p>Texture: {@code assets/witchmod/textures/entity/tax_man.png}, standard 64x64 player-skin format. If it
 * isn't there yet you'll get the missing-texture checker, which is loud and obvious rather than silent.
 */
public final class TaxManRenderer extends HumanoidMobRenderer<TaxManEntity, PlayerModel<TaxManEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "tax_man"), "main");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/tax_man.png");

    public TaxManRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(LAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    /** wide (Steve) arms — the layer definition the renderer bakes. */
    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    @Override
    public ResourceLocation getTextureLocation(TaxManEntity entity) {
        return TEXTURE;
    }
}
