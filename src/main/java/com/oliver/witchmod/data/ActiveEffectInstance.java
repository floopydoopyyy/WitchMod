package com.oliver.witchmod.data;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * one active effect on a player: ticks left, who cast it (absent for command/system casts), and a display
 * override for two modifiers — ink sac hides the wrapper until discovery, wither rose disguises it as the
 * opposite category. {@link StatusEffectSync} honours it; discovery resets it to normal.
 */
public record ActiveEffectInstance(int remainingTicks, Optional<UUID> caster, int display) {
    public static final int DISPLAY_NORMAL = 0;
    public static final int DISPLAY_HIDDEN = 1;
    public static final int DISPLAY_DISGUISED = 2;

    public static final Codec<ActiveEffectInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("remaining_ticks").forGetter(ActiveEffectInstance::remainingTicks),
            UUIDUtil.CODEC.optionalFieldOf("caster").forGetter(ActiveEffectInstance::caster),
            Codec.INT.optionalFieldOf("display", DISPLAY_NORMAL).forGetter(ActiveEffectInstance::display))
            .apply(instance, ActiveEffectInstance::new));

    /** the common case — a normally-displayed instance. */
    public ActiveEffectInstance(int remainingTicks, Optional<UUID> caster) {
        this(remainingTicks, caster, DISPLAY_NORMAL);
    }

    ActiveEffectInstance withRemainingTicks(int newRemainingTicks) {
        return new ActiveEffectInstance(newRemainingTicks, caster, display);
    }

    ActiveEffectInstance withDisplay(int newDisplay) {
        return new ActiveEffectInstance(remainingTicks, caster, newDisplay);
    }
}
