package com.oliver.witchmod.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.WatcherEyesEntity;

/** draws the {@link WatcherEyesEntity}: an invisible body plus the glowing {@link WatcherEyesEyesLayer}. */
public final class WatcherEyesRenderer extends MobRenderer<WatcherEyesEntity, WatcherEyesModel<WatcherEyesEntity>> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "watcher_eyes"), "main");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/watcher_eyes.png");

    public WatcherEyesRenderer(EntityRendererProvider.Context context) {
        super(context, new WatcherEyesModel<>(context.bakeLayer(LAYER)), 0.0F);
        addLayer(new WatcherEyesEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEyesEntity entity) {
        return TEXTURE;
    }
}
