package com.oliver.witchmod.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.SpaghettiManEntity;

/**
 * draws the {@link SpaghettiManEntity} with the code-baked {@link SpaghettiManModel} + glowing
 * {@link SpaghettiManEyesLayer}. Whether it's drawn AT ALL — only ever for its victim — is decided in
 * {@code ClientCurseHandler.onRenderLiving}, which cancels the render for anyone else.
 */
public final class SpaghettiManRenderer extends MobRenderer<SpaghettiManEntity, SpaghettiManModel<SpaghettiManEntity>> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "spaghetti_man"), "main");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/spaghetti_man.png");

    public SpaghettiManRenderer(EntityRendererProvider.Context context) {
        super(context, new SpaghettiManModel<>(context.bakeLayer(LAYER)), 0.4F);
        addLayer(new SpaghettiManEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SpaghettiManEntity entity) {
        return TEXTURE;
    }
}
