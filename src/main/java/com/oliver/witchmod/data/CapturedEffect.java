package com.oliver.witchmod.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** one stored effect in a jar (id + remaining ticks), waiting to be released on a throw. */
public record CapturedEffect(ResourceLocation effectId, int remainingTicks) {
    public static final Codec<CapturedEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("effect_id").forGetter(CapturedEffect::effectId),
            Codec.INT.fieldOf("remaining_ticks").forGetter(CapturedEffect::remainingTicks))
            .apply(instance, CapturedEffect::new));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, CapturedEffect> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, CapturedEffect::effectId,
            ByteBufCodecs.VAR_INT, CapturedEffect::remainingTicks,
            CapturedEffect::new);
}
