package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.animal.sniffer.Sniffer;

/** puppeteer: a sniffer puppet's current state on the model (private in vanilla), so it only changes on a change. */
@Mixin(Sniffer.class)
public interface SnifferInvoker {
    @Invoker("getState")
    Sniffer.State witchmodState();
}
