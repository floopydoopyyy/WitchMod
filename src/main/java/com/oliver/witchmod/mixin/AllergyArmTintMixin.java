package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import com.oliver.witchmod.client.AllergyClient;

/** allergic reaction: the first-person arm (and sleeve) goes green too. vanilla draws them with no colour argument. */
@Mixin(PlayerRenderer.class)
public class AllergyArmTintMixin {
    @WrapOperation(method = "renderHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void witchmod$tintArm(ModelPart part, PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                                  Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayer player) {
        int color = AllergyClient.tint(player, -1);
        if (color == -1) {
            original.call(part, poseStack, consumer, light, overlay);
        } else {
            part.render(poseStack, consumer, light, overlay, color);
        }
    }
}
