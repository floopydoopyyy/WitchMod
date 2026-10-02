package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;

/**
 * puppeteer: a burrowing endermite puppet moves straight through blocks — except ones too hard to burrow through
 * (BlessingPuppeteer.burrowDelta). runs on both sides, so the server's replay of the move matches the client's.
 */
@Mixin(Entity.class)
public abstract class EntityBurrowMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void witchmod$burrow(MoverType type, Vec3 delta, CallbackInfo ci) {
        if ((Object) this instanceof Player player && BlessingPuppeteer.burrowed(player)) {
            Vec3 moved = BlessingPuppeteer.burrowDelta(player, delta);
            player.setPos(player.getX() + moved.x, player.getY() + moved.y, player.getZ() + moved.z);
            player.setOnGround(false);
            player.horizontalCollision = moved.x != delta.x || moved.z != delta.z;
            player.verticalCollision = moved.y != delta.y;
            player.resetFallDistance();
            ci.cancel();
        }
    }
}
