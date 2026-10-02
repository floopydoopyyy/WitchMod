package com.oliver.witchmod.mixin;

import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** a guardian puppet's tail swish and spikes, animated by hand (the client-side dummy is never ticked). */
@Mixin(Guardian.class)
public interface GuardianAccessor {
    @Accessor("clientSideTailAnimation")
    float witchmodGetTail();

    @Accessor("clientSideTailAnimation")
    void witchmodSetTail(float value);

    @Accessor("clientSideTailAnimationO")
    void witchmodSetTailO(float value);

    @Accessor("clientSideTailAnimationSpeed")
    float witchmodGetTailSpeed();

    @Accessor("clientSideTailAnimationSpeed")
    void witchmodSetTailSpeed(float value);

    @Accessor("clientSideSpikesAnimation")
    float witchmodGetSpikes();

    @Accessor("clientSideSpikesAnimation")
    void witchmodSetSpikes(float value);

    @Accessor("clientSideSpikesAnimationO")
    void witchmodSetSpikesO(float value);
}
