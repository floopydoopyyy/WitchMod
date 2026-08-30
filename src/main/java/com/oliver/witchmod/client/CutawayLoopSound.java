package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * A looping Cutaway Gag SFX (helicopter rotors / abduction tractor beam / annoying music) played
 * POSITIONALLY at the victim and followed each tick — so it comes from the event itself, not from the
 * spectating watcher. It stops itself when the synced {@code CUTAWAY_LOOP} id changes/clears or the victim
 * is gone.
 */
public final class CutawayLoopSound extends AbstractTickableSoundInstance {
    private final int loopId;

    public CutawayLoopSound(SoundEvent event, int loopId) {
        super(event, SoundSource.HOSTILE, RandomSource.create());
        this.loopId = loopId;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        Entity v = victim();
        if (v != null) {
            this.x = v.getX();
            this.y = v.getY();
            this.z = v.getZ();
        }
    }

    private static Entity victim() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        int id = mc.player.getData(WitchModAttachments.CUTAWAY_TARGET);
        return id >= 0 ? mc.level.getEntity(id) : null;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getData(WitchModAttachments.CUTAWAY_LOOP) != loopId) {
            stop();
            return;
        }
        Entity v = victim();
        if (v == null) {
            stop();
            return;
        }
        this.x = v.getX();
        this.y = v.getY();
        this.z = v.getZ();
    }
}
