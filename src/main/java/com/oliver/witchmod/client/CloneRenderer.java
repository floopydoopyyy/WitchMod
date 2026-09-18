package com.oliver.witchmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.entities.CloneEntity;

/**
 * renders a Confusion doppelganger as a real {@link PlayerModel} wearing the OWNER'S actual skin (resolved
 * from the client's player list by UUID), so it's a convincing exact clone. Falls back to the default skin
 * if the owner isn't in the tab list.
 *
 * <p>wild-decoys synergy (with Disguise): while the owner is wearing a form the clones COPY it — a prop block
 * or the disguise mob — and, when the owner briefly puffs invisible on a form change, the clones vanish too.
 */
public final class CloneRenderer extends MobRenderer<CloneEntity, PlayerModel<CloneEntity>> {
    /** cached dummy mobs for decoys wearing a mob disguise, so we don't rebuild one every frame. */
    private static final Map<UUID, Mob> DECOY_MOBS = new HashMap<>();

    public CloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(CloneEntity clone, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Player owner = ownerPlayer(clone);
        if (owner != null) {
            // owner is mid form-change (the conceal flash) — the decoys puff out with them.
            long flash = owner.getData(WitchModAttachments.CONCEAL_FLASH_END);
            if (flash > 0 && owner.level().getGameTime() < flash) {
                return;
            }
            int prop = owner.getData(WitchModAttachments.PROPHUNT_BLOCK);
            if (prop >= 0) {
                poseStack.pushPose();
                poseStack.translate(-0.5, 0.0, -0.5); // block centred on the decoy's feet
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Block.stateById(prop), poseStack,
                        buffer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
                return;
            }
            int type = owner.getData(WitchModAttachments.DISGUISE_TYPE);
            if (type >= 0 && renderAsMob(clone, type, entityYaw, partialTick, poseStack, buffer, packedLight)) {
                return;
            }
        }
        super.render(clone, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** render the decoy as the owner's disguise mob; @return false to fall back to the player model. */
    private boolean renderAsMob(CloneEntity clone, int type, float yaw, float pt, PoseStack pose,
                                MultiBufferSource buffer, int light) {
        EntityType<?> want = switch (type) {
            case 1 -> EntityType.SHEEP;
            case 2 -> EntityType.PIG;
            case 3 -> EntityType.BAT;
            case 4 -> EntityType.SPIDER;
            case 5 -> EntityType.VILLAGER;
            default -> EntityType.COW;
        };
        if (DECOY_MOBS.size() > 64) {
            DECOY_MOBS.clear(); // stale decoys accumulate as clones pop — cheap periodic reset
        }
        Mob m = DECOY_MOBS.get(clone.getUUID());
        if (m == null || m.getType() != want) {
            if (!(want.create(clone.level()) instanceof Mob mob)) {
                return false;
            }
            m = mob;
            DECOY_MOBS.put(clone.getUUID(), m);
        }
        if (m instanceof net.minecraft.world.entity.ambient.Bat bat) {
            bat.setResting(false);
        }
        m.setYRot(yaw);
        m.yRotO = yaw;
        m.yBodyRot = yaw;
        m.yBodyRotO = yaw;
        m.yHeadRot = yaw;
        m.yHeadRotO = yaw;
        m.setXRot(clone.getXRot());
        m.xRotO = clone.getXRot();
        float dx = (float) (clone.getX() - clone.xOld);
        float dz = (float) (clone.getZ() - clone.zOld);
        m.walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F), 0.4F);
        m.tickCount = clone.tickCount;
        EntityRenderer<? super Mob> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(m);
        renderMob(renderer, m, yaw, pt, pose, buffer, light);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderMob(EntityRenderer renderer, Mob m, float yaw, float pt, PoseStack pose,
                                  MultiBufferSource buffer, int light) {
        renderer.render(m, yaw, pt, pose, buffer, light);
    }

    private static Player ownerPlayer(CloneEntity clone) {
        UUID owner = clone.getOwnerId().orElse(null);
        if (owner == null || Minecraft.getInstance().level == null) {
            return null;
        }
        return Minecraft.getInstance().level.getPlayerByUUID(owner);
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
