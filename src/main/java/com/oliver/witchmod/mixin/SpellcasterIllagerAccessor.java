package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.SpellcasterIllager;

/** puppeteer: an evoker puppet's casting pose (arms up) — the dummy's spell id, set by hand. */
@Mixin(SpellcasterIllager.class)
public interface SpellcasterIllagerAccessor {
    @Accessor("DATA_SPELL_CASTING_ID")
    static EntityDataAccessor<Byte> witchmodSpellKey() {
        throw new AssertionError();
    }
}
