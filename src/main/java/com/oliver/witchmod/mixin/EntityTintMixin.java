package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import com.oliver.witchmod.client.EntityTints;

/**
 * recolours an entity's base body model (see {@link EntityTints}): the allergic green skin, the shadow's purple.
 * only the base model's colour changes, so armour, held items and capes (their own layers) keep their colours.
 */
@Mixin(LivingEntityRenderer.class)
public class EntityTintMixin {
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int witchmod$entityTint(int color, @Local(argsOnly = true) LivingEntity entity) {
        return EntityTints.tint(entity, color);
    }
}
