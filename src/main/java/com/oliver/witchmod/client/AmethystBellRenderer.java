package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.blocks.AmethystBellBlockEntity;

/**
 * Swings the amethyst bell body exactly like vanilla's {@code BellRenderer} — the same {@link ModelLayers#BELL}
 * part and the same decaying-sine swing driven by the block entity's shake state — with the purple amethyst
 * texture.
 */
public final class AmethystBellRenderer implements BlockEntityRenderer<AmethystBellBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/amethyst_bell.png");

    private final ModelPart bellBody;

    public AmethystBellRenderer(BlockEntityRendererProvider.Context context) {
        this.bellBody = context.bakeLayer(ModelLayers.BELL).getChild("bell_body");
    }

    @Override
    public void render(AmethystBellBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float f = be.ticks + partialTick;
        float xRot = 0.0F;
        float zRot = 0.0F;
        if (be.shaking) {
            float d = Mth.sin(f / (float) Math.PI) / (4.0F + f / 3.0F);
            switch (be.clickDirection) {
                case NORTH -> xRot = -d;
                case SOUTH -> xRot = d;
                case EAST -> zRot = -d;
                case WEST -> zRot = d;
                default -> { }
            }
        }
        this.bellBody.xRot = xRot;
        this.bellBody.zRot = zRot;
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        this.bellBody.render(pose, vc, light, overlay);
    }
}
