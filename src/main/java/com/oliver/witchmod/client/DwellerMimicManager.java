package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the Dweller MIMIC — its own event, NOT the Delusions curse. Where Delusions is a wandering hallucination
 * (fake players going about their business, "am I seeing things?"), the mimic is ONE impostor wearing a real
 * player's face that just stands and STARES at you, unnaturally still, and then drops the mask: it dissolves
 * into shadow with the Dweller's scream, implying the thing stalking you was wearing that face all along.
 *
 * <p>It reuses the {@link DelusionPlayer} body only for the skin-mirroring render (pinned into a stare via
 * {@link DelusionPlayer#setStalkerStare}); the whole staging + reveal here is bespoke. Victim-only and
 * client-driven off the single synced {@link WitchModAttachments#DWELLER_MIMIC} session value, like the rest
 * of the curse — nobody else has an entity to see.
 */
public final class DwellerMimicManager {
    private static long lastSession;
    private static DelusionPlayer impostor;
    private static int ticks;
    private static int stareTicks;   // consecutive ticks the victim has held it in view
    private static boolean revealed;

    private DwellerMimicManager() {}

    public static void clientTick(Minecraft mc) {
        LocalPlayer victim = mc.player;
        ClientLevel level = mc.level;
        if (victim == null || level == null) {
            clear();
            lastSession = 0L;
            return;
        }
        long session = victim.getData(WitchModAttachments.DWELLER_MIMIC);
        if (session == 0L) {
            clear();
            lastSession = 0L;
            return;
        }
        if (session != lastSession) {
            lastSession = session;
            spawn(mc, victim, level, session); // a new impostor is due
        }
        if (impostor == null || revealed) {
            return; // already revealed / nothing to drive; wait for the session to clear
        }
        if (impostor.isRemoved() || impostor.level() != level) {
            clear();
            return;
        }

        ticks++;
        boolean watched = isWatched(victim, impostor);
        stareTicks = watched ? stareTicks + 1 : 0;

        // the reveal is EARNED by catching it staring back for a beat, or simply comes once the window is up.
        int window = Config.DWELLER_MIMIC_BURST_TICKS.get();
        if (stareTicks >= 12 || ticks >= window) {
            reveal(level, victim);
        }
    }

    /** drops the mask: the familiar face dissolves into shadow where it stood, with the Dweller's scream. */
    private static void reveal(ClientLevel level, LocalPlayer victim) {
        revealed = true;
        if (impostor == null) {
            return;
        }
        Vec3 p = impostor.position();
        for (int i = 0; i < 40; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = level.random.nextDouble() * 0.5;
            level.addParticle(ParticleTypes.LARGE_SMOKE, p.x + Math.cos(a) * r, p.y + 0.2 + level.random.nextDouble() * 1.9,
                    p.z + Math.sin(a) * r, 0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 16; i++) {
            level.addParticle(ParticleTypes.SMOKE, p.x, p.y + 1.0, p.z,
                    (level.random.nextDouble() - 0.5) * 0.3, level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.3);
        }
        // the mask drops with the glitchy ENRAGED scream — the impostor was the Dweller all along.
        level.playLocalSound(p.x, p.y, p.z, com.oliver.witchmod.data.WitchModSounds.DWELLER_ENRAGED.get(), SoundSource.HOSTILE, 1.1F, 1.0F, false);
        // A dread-scaling chance that the reveal is a full JUMPSCARE — a bright flash into darkness.
        float dread = victim.getData(WitchModAttachments.DWELLER_DREAD);
        if (level.random.nextFloat() < net.minecraft.util.Mth.lerp(dread, 0.2F, 0.85F)) {
            ClientCurseHandler.triggerDwellerFlash(8);
        }
        remove();
    }

    private static void spawn(Minecraft mc, LocalPlayer victim, ClientLevel level, long seed) {
        clear();
        ticks = 0;
        stareTicks = 0;
        revealed = false;
        PlayerInfo mirrored = pickPlayerToMirror(mc, victim, seed);
        if (mirrored == null) {
            return;
        }
        RandomSource rng = RandomSource.create(seed);
        Vec3 spot = findSpawnSpot(level, victim, rng);
        if (spot == null) {
            return;
        }
        DelusionPlayer imp = new DelusionPlayer(level, mirrored, seed);
        imp.setStalkerStare(true);
        imp.setPos(spot.x, spot.y, spot.z);
        imp.setOldPosAndRot();
        level.addEntity(imp);
        impostor = imp;
    }

    private static void remove() {
        if (impostor != null) {
            impostor.cleanUp();
            if (impostor.level() instanceof ClientLevel cl) {
                cl.removeEntity(impostor.getId(), Entity.RemovalReason.DISCARDED);
            }
            impostor = null;
        }
    }

    private static void clear() {
        remove();
        ticks = 0;
        stareTicks = 0;
        revealed = false;
    }

    /** in the view cone, in line of sight, at a believable distance — you've clocked it looking back. */
    private static boolean isWatched(LocalPlayer victim, DelusionPlayer imp) {
        Vec3 eye = victim.getEyePosition();
        Vec3 theirEye = imp.getEyePosition();
        Vec3 delta = theirEye.subtract(eye);
        double d = delta.length();
        if (d < 1.0 || d > 48.0) {
            return false;
        }
        if (victim.getViewVector(1.0F).dot(delta.scale(1.0 / d)) < 0.985) {
            return false; // must be looking fairly directly at it
        }
        HitResult blocked = victim.level().clip(new ClipContext(
                eye, theirEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        return blocked.getType() == HitResult.Type.MISS;
    }

    /** A real player on the server (their face is one you'd recognise); yourself only if you're alone. */
    private static PlayerInfo pickPlayerToMirror(Minecraft mc, LocalPlayer victim, long seed) {
        if (mc.getConnection() == null) {
            return null;
        }
        List<PlayerInfo> others = new ArrayList<>();
        PlayerInfo self = null;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile().getId().equals(victim.getUUID())) {
                self = info;
            } else {
                others.add(info);
            }
        }
        if (!others.isEmpty()) {
            return others.get(Math.floorMod(seed, others.size()));
        }
        return self; // alone on the server: the face it wears can only be your own
    }

    /** A believable spot at a distance, preferably just out of your current view so you TURN and find it. */
    private static Vec3 findSpawnSpot(ClientLevel level, LocalPlayer victim, RandomSource rng) {
        Vec3 fallback = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double distance = 8.0 + rng.nextDouble() * 12.0;
            double x = victim.getX() + Math.cos(angle) * distance;
            double z = victim.getZ() + Math.sin(angle) * distance;
            Double y = standableY(level, x, z, victim.getY());
            if (y == null) {
                continue;
            }
            Vec3 spot = new Vec3(x, y, z);
            if (fallback == null) {
                fallback = spot;
            }
            Vec3 toSpot = spot.add(0.0, 1.0, 0.0).subtract(victim.getEyePosition()).normalize();
            if (victim.getViewVector(1.0F).dot(toSpot) < 0.3) {
                return spot; // out of view — better
            }
        }
        return fallback;
    }

    private static Double standableY(ClientLevel level, double x, double z, double nearY) {
        BlockPos base = BlockPos.containing(x, nearY + 4.0, z);
        for (int dy = 0; dy < 24; dy++) {
            BlockPos floor = base.below(dy);
            BlockPos feet = floor.above();
            if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return (double) feet.getY();
            }
        }
        return null;
    }
}
