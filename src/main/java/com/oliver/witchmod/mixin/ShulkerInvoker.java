package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** puppeteer: a shulker puppet's shell, run by hand on the model (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.monster.Shulker.class)
public interface ShulkerInvoker {
    @Invoker("setRawPeekAmount")
    void witchmodSetPeek(int peek);

    @Invoker("updatePeekAmount")
    boolean witchmodUpdatePeek();
}
