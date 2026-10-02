package com.oliver.witchmod.client;

import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.entities.ShadowEntity;

/**
 * the shadow: a player model wearing its owner's real skin, drawn translucent purple (colour from
 * {@link EntityTints}). it climbs up out of the ground while summoning, and once risen trails a string of
 * fading afterimages at its recent positions ({@link ShadowClient#echoes}).
 */
public final class ShadowRenderer extends MobRenderer<ShadowEntity, PlayerModel<ShadowEntity>> {
    /** opacity of the newest afterimage; older ones fade from there. */
    private static final float ECHO_ALPHA = 0.45F;

    public ShadowRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
    }

    @Override
    public void render(ShadowEntity shadow, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        int summonTicks = summonTicks();
        float rise = summonTicks <= 0 ? 1.0F : Mth.clamp((shadow.tickCount + partialTick) / summonTicks, 0.0F, 1.0F);
        try {
            if (rise >= 1.0F) {
                double x = Mth.lerp(partialTick, shadow.xo, shadow.getX());
                double y = Mth.lerp(partialTick, shadow.yo, shadow.getY());
                double z = Mth.lerp(partialTick, shadow.zo, shadow.getZ());
                List<Vec3> echoes = ShadowClient.echoes(shadow);
                for (int i = 0; i < echoes.size(); i++) {
                    Vec3 at = echoes.get(i);
                    EntityTints.shadowAlpha = ECHO_ALPHA * (i + 1) / (echoes.size() + 1);
                    pose.pushPose();
                    pose.translate(at.x - x, at.y - y, at.z - z);
                    super.render(shadow, yaw, partialTick, pose, buffer, light);
                    pose.popPose();
                }
            }
            // summoning: claw up out of the ground, fading in.
            EntityTints.shadowAlpha = rise;
            pose.pushPose();
            pose.translate(0.0, -(1.0F - rise) * 1.9F, 0.0);
            super.render(shadow, yaw, partialTick, pose, buffer, light);
            pose.popPose();
        } finally {
            EntityTints.shadowAlpha = 1.0F;
        }
    }

    private static int summonTicks() {
        try {
            return Config.SHADOW_SUMMON_TICKS.get();
        } catch (IllegalStateException e) {
            return 25;
        }
    }

    @Override
    protected RenderType getRenderType(ShadowEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        UUID owner = entity.getOwnerId().orElse(null);
        if (owner != null && Minecraft.getInstance().getConnection() != null) {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(owner);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        return DefaultPlayerSkin.get(owner != null ? owner : entity.getUUID()).texture();
    }
}
