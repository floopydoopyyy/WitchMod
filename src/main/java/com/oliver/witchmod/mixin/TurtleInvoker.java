package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** puppeteer: a turtle puppet's egg-laying dig and full shell on the model (both package-private in vanilla). */
@Mixin(net.minecraft.world.entity.animal.Turtle.class)
public interface TurtleInvoker {
    @Invoker("setLayingEgg")
    void witchmodSetLayingEgg(boolean laying);

    @Invoker("setHasEgg")
    void witchmodSetHasEgg(boolean hasEgg);
}
