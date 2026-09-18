package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * main Character's looping battle theme, following a powered-up player. Everyone nearby hears it, not just the
 * protagonist. The supplied track is stereo, which OpenAL can't positionally attenuate, so the volume is faded
 * MANUALLY by distance each tick (full at the source, silent past {@code mainCharMusicRange}). A looping
 * tickable instance so it stops the instant the tier drops to 0 (the server's sticky window keeps it up ~4s
 * after the crowd thins).
 */
@OnlyIn(Dist.CLIENT)
public final class MainCharSoundInstance extends AbstractTickableSoundInstance {
    private final Player source;

    public MainCharSoundInstance(Player source) {
        super(com.oliver.witchmod.data.WitchModSounds.MAINCHAR_THEME.get(), SoundSource.PLAYERS, RandomSource.create());
        this.source = source;
        this.looping = true;
        this.delay = 0;
        this.pitch = 1.0F;
        this.relative = true;             // volume is driven manually, so keep it centred on the listener
        this.attenuation = Attenuation.NONE;
        this.volume = computeVolume();
    }

    /** whether this source should still have a theme playing. */
    static boolean shouldPlay(Player source) {
        return source.isAlive() && !source.isRemoved()
                && source.getData(WitchModAttachments.MAINCHAR_TIER) > 0;
    }

    private float computeVolume() {
        LocalPlayer listener = Minecraft.getInstance().player;
        if (listener == null) {
            return 0.0F;
        }
        double dist = source == listener ? 0.0 : Math.sqrt(source.distanceToSqr(listener));
        double range = Config.MAINCHAR_MUSIC_RANGE.get();
        float falloff = (float) Mth.clamp(1.0 - dist / range, 0.0, 1.0);
        return Config.MAINCHAR_MUSIC_VOLUME.get().floatValue() * falloff;
    }

    @Override
    public void tick() {
        if (!shouldPlay(source)) {
            stop();
            return;
        }
        this.volume = computeVolume();
    }
}
