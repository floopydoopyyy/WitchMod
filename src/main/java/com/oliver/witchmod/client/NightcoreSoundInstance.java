package com.oliver.witchmod.client;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Bedrock Moment (Nightcore bug): a thin wrapper around any {@link SoundInstance} that just plays it back at a
 * higher pitch. Everything else delegates straight through.
 */
@OnlyIn(Dist.CLIENT)
public final class NightcoreSoundInstance implements SoundInstance {
    private final SoundInstance wrapped;
    private final float pitchMult;

    public NightcoreSoundInstance(SoundInstance wrapped, float pitchMult) {
        this.wrapped = wrapped;
        this.pitchMult = pitchMult;
    }

    @Override
    public ResourceLocation getLocation() {
        return wrapped.getLocation();
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        return wrapped.resolve(manager);
    }

    @Override
    public Sound getSound() {
        return wrapped.getSound();
    }

    @Override
    public SoundSource getSource() {
        return wrapped.getSource();
    }

    @Override
    public boolean isLooping() {
        return wrapped.isLooping();
    }

    @Override
    public boolean isRelative() {
        return wrapped.isRelative();
    }

    @Override
    public int getDelay() {
        return wrapped.getDelay();
    }

    @Override
    public float getVolume() {
        return wrapped.getVolume();
    }

    @Override
    public float getPitch() {
        return Math.min(2.0F, wrapped.getPitch() * pitchMult);
    }

    @Override
    public double getX() {
        return wrapped.getX();
    }

    @Override
    public double getY() {
        return wrapped.getY();
    }

    @Override
    public double getZ() {
        return wrapped.getZ();
    }

    @Override
    public Attenuation getAttenuation() {
        return wrapped.getAttenuation();
    }

    @Override
    public boolean canStartSilent() {
        return wrapped.canStartSilent();
    }

    @Override
    public boolean canPlaySound() {
        return wrapped.canPlaySound();
    }
}
