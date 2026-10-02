package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;

/**
 * puppeteer: a camel puppet seats its riders where the real camel does — one up front, a second behind (vanilla's
 * Camel.getPassengerAttachmentPoint) — instead of a player's single seat. both sides.
 */
@Mixin(Entity.class)
public abstract class EntityCamelSeatMixin {
    @Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
    private void witchmod$camelSeats(Entity passenger, EntityDimensions dimensions, float scale, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof Player camel && BlessingPuppeteer.camelPuppet(camel)) {
            int index = Math.max(camel.getPassengers().indexOf(passenger), 0);
            float along = camel.getPassengers().size() > 1 && index > 0 ? -0.7F : 0.5F;
            double up = dimensions.height() - 0.375F * scale;
            cir.setReturnValue(new Vec3(0.0, up, along * scale).yRot(-camel.yBodyRot * ((float) Math.PI / 180.0F)));
        }
    }
}
