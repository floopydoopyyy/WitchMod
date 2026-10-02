package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;

/** puppeteer: lets the client-side creeper puppet show the possessor's fuse and charge. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("swell")
    void witchmodSetSwell(int swell);

    @Accessor("oldSwell")
    void witchmodSetOldSwell(int oldSwell);

    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> witchmodPoweredKey() {
        throw new AssertionError();
    }
}
