package com.oliver.witchmod.mixin;

import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** a phantom puppet's flock: a hunting phantom's flight steered straight at the mark. */
@Mixin(Phantom.class)
public interface PhantomAccessor {
    @Accessor("moveTargetPoint")
    void witchmodSetMoveTarget(Vec3 point);
}
