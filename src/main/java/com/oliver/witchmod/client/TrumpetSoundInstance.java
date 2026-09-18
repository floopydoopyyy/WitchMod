package com.oliver.witchmod.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the Trumpet curse's looping fat-trumpet music, following one cursed player. A looping tickable instance so
 * it (a) loops seamlessly — the engine sets OpenAL {@code AL_LOOPING} on the fully-buffered sound — and
 * (b) can be stopped the instant the player stops moving, which a fire-and-forget {@code playSound} can't.
 *
 * <p>Each tick it re-checks the play conditions and {@link #stop()}s itself the moment any fails (curse gone,
 * player gone, crouching, or not walking). {@link TrumpetSoundManager} owns creation — when a qualifying
 * player starts moving again a fresh instance is spun up, so the loop restarts cleanly from the top.
 *
 * <p>Pitch is read live by the sound engine every tick, so bumping it to {@code trumpetSprintPitch} while
 * sprinting speeds the loop up without any restart hiccup.
 */
@OnlyIn(Dist.CLIENT)
public final class TrumpetSoundInstance extends AbstractTickableSoundInstance {
    private final Player player;

    public TrumpetSoundInstance(Player player) {
        super(com.oliver.witchmod.data.WitchModSounds.TRUMPET_WALK_LOOP.get(), SoundSource.PLAYERS,
                RandomSource.create());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = Config.TRUMPET_VOLUME.get().floatValue();
        this.pitch = 1.0F;
        this.attenuation = Attenuation.LINEAR;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    /** whether this instance should still be playing for {@code player} this tick. */
    static boolean shouldPlay(Player player) {
        return player.isAlive()
                && !player.isRemoved()
                && player.getData(WitchModAttachments.TRUMPET_ACTIVE) == 1
                && !player.isCrouching()                                       // crouch = quiet, the counterplay
                && TrumpetSoundManager.isMoving(player)                        // EXTRA gate: actual displacement, so
                                                                              // a stuck walk-animation can't loop it forever
                && player.walkAnimation.speed() > Config.TRUMPET_WALK_THRESHOLD.get().floatValue();
    }

    @Override
    public void tick() {
        if (!shouldPlay(player)) {
            stop();
            return;
        }
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.pitch = player.isSprinting() ? Config.TRUMPET_SPRINT_PITCH.get().floatValue() : 1.0F;
    }
}
