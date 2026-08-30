package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.blocks.LedgerBlock;
import com.oliver.witchmod.blocks.LedgerBlockEntity;

/**
 * Renders a 3D purple book resting on the Ledger — exactly the pose the vanilla {@code LecternRenderer} uses
 * for a book placed on a lectern (same {@link BookModel}, same transform), with a purple-recoloured texture.
 */
public final class LedgerRenderer implements BlockEntityRenderer<LedgerBlockEntity> {
    private static final ResourceLocation BOOK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/ledger_book.png");

    private final BookModel bookModel;

    public LedgerRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(LedgerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        pose.pushPose();
        pose.translate(0.5F, 1.0625F, 0.5F);
        float f = state.getValue(LedgerBlock.FACING).getClockWise().toYRot();
        pose.mulPose(Axis.YP.rotationDegrees(-f));
        pose.mulPose(Axis.ZP.rotationDegrees(67.5F));
        pose.translate(0.0F, -0.125F, 0.0F);
        this.bookModel.setupAnim(0.0F, 0.1F, 0.9F, 1.2F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        this.bookModel.render(pose, vc, light, overlay, -1);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LedgerBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 1.5, p.getZ() + 1.0);
    }
}
