package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.entities.SpaghettiManEntity;

/**
 * the Dweller's BREATHING — a subtle positional loop played AT the dweller while it's manifest and watching
 * you, getting louder the closer it is (so you only really hear it when it's watching close). Driven off the
 * synced {@code DWELLER_BREATHING} flag; follows the (victim-only) dweller entity and stops when the flag
 * clears or the entity is gone.
 */
public final class DwellerBreathingSound extends AbstractTickableSoundInstance {
    public DwellerBreathingSound() {
        super(WitchModSounds.DWELLER_BREATHING.get(), SoundSource.HOSTILE, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    /** the victim's own dweller — the one the local player renders (victim-only). */
    private static SpaghettiManEntity dweller() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof SpaghettiManEntity d && d.getVictim().map(u -> u.equals(mc.player.getUUID())).orElse(false)) {
                return d;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getData(WitchModAttachments.DWELLER_BREATHING) != 1) {
            stop();
            return;
        }
        SpaghettiManEntity d = dweller();
        if (d == null) {
            // no body yet (or between beats) — go silent but keep the loop alive until the flag clears.
            this.volume = 0.0F;
            return;
        }
        this.x = d.getX();
        this.y = d.getY() + 1.4;
        this.z = d.getZ();
        double dist = d.distanceTo(mc.player);
        // subtle overall, and audible only when close — fades out past ~11 blocks.
        this.volume = (float) Mth.clamp(0.55 * (1.0 - dist / 11.0), 0.0, 0.55);
    }
}
