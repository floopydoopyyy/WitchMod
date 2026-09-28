package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * channels curse — swaps the victim's left/right audio. the right-ear axis is derived as forward×up, so
 * negating up flips it; needs a mixin because the sound listener has no event to hook. only positional
 * sounds are affected. gated on the synced channels flag.
 */
@Mixin(Listener.class)
public class ListenerMixin {
    @ModifyVariable(method = "setTransform", at = @At("HEAD"), argsOnly = true)
    private ListenerTransform witchmod$flipChannels(ListenerTransform transform) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && player.getData(WitchModAttachments.CHANNELS_ACTIVE) >= 0) {
            return new ListenerTransform(transform.position(), transform.forward(), transform.up().scale(-1.0));
        }
        return transform;
    }
}
