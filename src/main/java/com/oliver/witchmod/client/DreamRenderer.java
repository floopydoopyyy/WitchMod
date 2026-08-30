package com.oliver.witchmod.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.DreamEntity;

/**
 * Renders the Cutaway Gag's Dream mimic as a real vanilla {@link PlayerModel} (the wide/Steve layout) wearing
 * the dream skin — so it looks like an ordinary player rather than a mob. Reuses the vanilla {@code PLAYER}
 * model layer, so no custom layer definition is needed.
 */
public final class DreamRenderer extends MobRenderer<DreamEntity, PlayerModel<DreamEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/ugly/dream.png");

    public DreamRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(DreamEntity entity) {
        return TEXTURE;
    }
}
