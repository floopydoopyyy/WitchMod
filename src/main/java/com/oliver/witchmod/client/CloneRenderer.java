package com.oliver.witchmod.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.entities.CloneEntity;

/**
 * Renders a Confusion doppelganger as a real {@link PlayerModel} wearing the OWNER'S actual skin (resolved
 * from the client's player list by UUID), so it's a convincing exact clone. Falls back to the default skin
 * if the owner isn't in the tab list.
 */
public final class CloneRenderer extends MobRenderer<CloneEntity, PlayerModel<CloneEntity>> {
    public CloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CloneEntity entity) {
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
