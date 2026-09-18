package com.oliver.witchmod.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.SnailEntity;

/** draws the {@link SnailEntity} with the code-baked {@link SnailModel}. */
public final class SnailRenderer extends MobRenderer<SnailEntity, SnailModel<SnailEntity>> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "snail"), "main");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/snail.png");

    public SnailRenderer(EntityRendererProvider.Context context) {
        super(context, new SnailModel<>(context.bakeLayer(LAYER)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(SnailEntity entity) {
        return TEXTURE;
    }
}
