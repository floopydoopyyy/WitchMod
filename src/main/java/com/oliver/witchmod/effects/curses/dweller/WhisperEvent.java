package com.oliver.witchmod.effects.curses.dweller;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/** A whisper right at your ear — the never-fails fallback moment. */
public final class WhisperEvent implements DwellerEvent {
    @Override
    public String id() {
        return "whisper";
    }

    @Override
    public boolean run(CurseTheDweller curse, ServerPlayer target, CurseTheDweller.State state, ServerLevel level) {
        Vec3 ear = target.getEyePosition().add(target.getRandom().nextDouble() - 0.5, 0, target.getRandom().nextDouble() - 0.5);
        CurseTheDweller.playToVictimAt(target, SoundEvents.WARDEN_TENDRIL_CLICKS, ear.x, ear.y, ear.z, 0.6F, 0.55F);
        return true;
    }
}
