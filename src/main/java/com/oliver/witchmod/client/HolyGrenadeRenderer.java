package com.oliver.witchmod.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.HolyHandGrenadeEntity;

/** the grenade is invisible — a renderer is still required for a tracked entity, so this one draws nothing. */
public final class HolyGrenadeRenderer extends EntityRenderer<HolyHandGrenadeEntity> {
    private static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/none.png");

    public HolyGrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(HolyHandGrenadeEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(HolyHandGrenadeEntity entity) {
        return NONE;
    }
}
