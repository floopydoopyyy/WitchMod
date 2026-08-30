package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.entities.SnailEntity;

/**
 * The Snail curse's looping dread music, following one Snail entity. A looping tickable instance (so it loops
 * seamlessly via OpenAL {@code AL_LOOPING} on the fully-buffered sound, and can be stopped the instant the
 * snail leaves range). Positional at the snail with LINEAR attenuation, so it swells as it closes in.
 */
@OnlyIn(Dist.CLIENT)
public final class SnailSoundInstance extends AbstractTickableSoundInstance {
    private final SnailEntity snail;

    // Constant playback speed; the VOLUME scales through these distance stages instead (louder as it nears).
    private static final float PITCH = 1.1F;
    private static final double MID_DIST = 10.0;
    private static final double NEAR_DIST = 5.0;
    private static final float VOL_NEAR = 1.0F, VOL_MID = 0.85F, VOL_FAR = 0.7F;

    public SnailSoundInstance(SnailEntity snail) {
        super(WitchModSounds.SNAIL_MUSIC.get(), SoundSource.HOSTILE, RandomSource.create());
        this.snail = snail;
        this.looping = true;
        this.delay = 0;
        this.volume = Config.SNAIL_MUSIC_VOLUME.get().floatValue();
        this.pitch = PITCH;
        this.attenuation = Attenuation.NONE; // we control the volume by distance ourselves, precisely
        this.x = snail.getX();
        this.y = snail.getY();
        this.z = snail.getZ();
    }

    /** Whether the music should still play: the snail is alive and within the MAX music distance of the player. */
    static boolean shouldPlay(SnailEntity snail) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || snail.isRemoved() || !snail.isAlive()) {
            return false;
        }
        double r = Config.SNAIL_MUSIC_DISTANCE.get();
        return snail.distanceToSqr(mc.player) <= r * r;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !shouldPlay(snail)) {
            stop();
            return;
        }
        this.x = snail.getX();
        this.y = snail.getY();
        this.z = snail.getZ();

        double dist = Math.sqrt(snail.distanceToSqr(mc.player));
        float base = Config.SNAIL_MUSIC_VOLUME.get().floatValue();
        this.volume = base * volumeFactor(dist, Config.SNAIL_MUSIC_FULL_DISTANCE.get(),
                Math.max(Config.SNAIL_MUSIC_FULL_DISTANCE.get() + 0.01, Config.SNAIL_MUSIC_DISTANCE.get()));
        this.pitch = PITCH; // permanent 1.1x
    }

    /** Volume steps UP as it nears (0.7 at the full ring → 0.85 at 10 → 1.0 within 5), fading to silence at max. */
    private static float volumeFactor(double dist, double full, double max) {
        if (dist <= NEAR_DIST) {
            return VOL_NEAR;
        }
        if (dist <= MID_DIST) {
            return lerp((dist - NEAR_DIST) / (MID_DIST - NEAR_DIST), VOL_NEAR, VOL_MID); // 5..10 -> 1.0..0.85
        }
        if (dist <= full) {
            return lerp((dist - MID_DIST) / (full - MID_DIST), VOL_MID, VOL_FAR); // 10..18 -> 0.85..0.7
        }
        return (float) Math.max(0.0, lerp((dist - full) / (max - full), VOL_FAR, 0.0F)); // 18..24 -> 0.7..0
    }

    private static float lerp(double t, float a, float b) {
        return (float) (a + (b - a) * t);
    }
}
