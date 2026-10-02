package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import com.oliver.witchmod.client.PuppeteerClient;

/** puppeteer: an enraged enderman puppet sees whoever looked it in the eye glowing — on its own screen only. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void witchmod$starersGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PuppeteerClient.glowsForMe(entity)) {
            cir.setReturnValue(true);
        }
    }
}
