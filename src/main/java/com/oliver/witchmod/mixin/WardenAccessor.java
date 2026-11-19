package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * puppeteer: a warden's tendrils, heart and mood, driven by hand — on a warden puppet's model (the client-side dummy is
 * never ticked) and on the real one as it fights a possession.
 */
@Mixin(Warden.class)
public interface WardenAccessor {
    @Accessor("tendrilAnimation")
    int witchmodGetTendril();

    @Accessor("tendrilAnimation")
    void witchmodSetTendril(int ticks);

    @Accessor("tendrilAnimationO")
    void witchmodSetTendrilO(int ticks);

    @Accessor("heartAnimation")
    int witchmodGetHeart();

    @Accessor("heartAnimation")
    void witchmodSetHeart(int ticks);

    @Accessor("heartAnimationO")
    void witchmodSetHeartO(int ticks);

    @Accessor("CLIENT_ANGER_LEVEL")
    static EntityDataAccessor<Integer> witchmodAngerLevel() {
        throw new AssertionError();
    }
}
