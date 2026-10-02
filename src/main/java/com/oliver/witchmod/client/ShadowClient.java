package com.oliver.witchmod.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.entities.ShadowEntity;

/**
 * client side of the shadow. for everyone: a purple dust trail and a string of fading afterimages (positions
 * recorded here, drawn by {@link ShadowRenderer}). for the hunted player only: the snail's music when it's
 * close, a heartbeat that quickens as it closes in, and a dread value the purple vignette
 * ({@link ShadowDreadOverlay}) pulses with.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class ShadowClient {
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.55F, 0.2F, 0.85F), 1.4F);
    private static final double VIEW = 48.0;
    /** afterimages: one recorded every ECHO_EVERY ticks, ECHO_COUNT kept. */
    private static final int ECHO_EVERY = 2;
    private static final int ECHO_COUNT = 5;
    /** within this many blocks of its victim the dread (heartbeat + vignette) builds. */
    private static final double DREAD_RANGE = 10.0;

    private static final Map<Integer, ShadowMusic> MUSIC = new HashMap<>();
    private static final Map<Integer, ArrayDeque<Vec3>> ECHOES = new HashMap<>();
    private static float dread;
    private static int heartbeatIn;

    private ShadowClient() {}

    /** 0..1 — how close the local player's own shadow is. */
    public static float dread() {
        return dread;
    }

    /** the shadow's recent positions, oldest first. */
    public static List<Vec3> echoes(ShadowEntity shadow) {
        ArrayDeque<Vec3> q = ECHOES.get(shadow.getId());
        return q == null ? List.of() : List.copyOf(q);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            MUSIC.clear();
            ECHOES.clear();
            dread = 0.0F;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        MUSIC.entrySet().removeIf(e -> e.getValue().isStopped());
        RandomSource rng = mc.level.random;
        double nearest = Double.MAX_VALUE;
        Map<Integer, ArrayDeque<Vec3>> seen = new HashMap<>();
        for (ShadowEntity shadow : mc.level.getEntitiesOfClass(ShadowEntity.class, mc.player.getBoundingBox().inflate(VIEW))) {
            ArrayDeque<Vec3> echoes = ECHOES.computeIfAbsent(shadow.getId(), k -> new ArrayDeque<>());
            seen.put(shadow.getId(), echoes);
            if (shadow.tickCount % ECHO_EVERY == 0) {
                echoes.addLast(shadow.position());
                while (echoes.size() > ECHO_COUNT) {
                    echoes.removeFirst();
                }
            }
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(TRAIL, shadow.getX() + (rng.nextDouble() - 0.5) * 0.5,
                        shadow.getY() + rng.nextDouble() * 1.7, shadow.getZ() + (rng.nextDouble() - 0.5) * 0.5, 0, 0, 0);
            }
            if (rng.nextInt(4) == 0) {
                mc.level.addParticle(ParticleTypes.SCULK_SOUL, shadow.getX(), shadow.getY() + 0.2, shadow.getZ(), 0, 0.02, 0);
            }
            boolean mine = shadow.getOwnerId().map(id -> id.equals(mc.player.getUUID())).orElse(false);
            if (!mine) {
                continue;
            }
            nearest = Math.min(nearest, Math.sqrt(shadow.distanceToSqr(mc.player)));
            if (ShadowMusic.inRange(shadow) && !MUSIC.containsKey(shadow.getId())) {
                ShadowMusic music = new ShadowMusic(shadow);
                MUSIC.put(shadow.getId(), music);
                mc.getSoundManager().play(music);
            }
        }
        ECHOES.keySet().retainAll(seen.keySet());

        // dread eases toward the target so the vignette swells and fades smoothly.
        float target = nearest < DREAD_RANGE ? (float) (1.0 - nearest / DREAD_RANGE) : 0.0F;
        dread += (target - dread) * 0.15F;
        if (dread > 0.05F && --heartbeatIn <= 0) {
            heartbeatIn = Math.round(Mth.lerp(dread, 22.0F, 7.0F)); // quickens as it closes in
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.4F + dread * 0.8F));
            ShadowDreadOverlay.pulse();
        }
    }

    /** the snail's loop, following the shadow; quiet at the edge of the range, full volume when it's on you. */
    private static final class ShadowMusic extends AbstractTickableSoundInstance {
        private final ShadowEntity shadow;

        ShadowMusic(ShadowEntity shadow) {
            super(WitchModSounds.SNAIL_MUSIC.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.shadow = shadow;
            this.looping = true;
            this.delay = 0;
            this.pitch = 1.1F;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.0F;
            tick();
        }

        static boolean inRange(ShadowEntity shadow) {
            Minecraft mc = Minecraft.getInstance();
            double r = Config.SHADOW_MUSIC_DISTANCE.get();
            return mc.player != null && shadow.isAlive() && !shadow.isRemoved() && shadow.distanceToSqr(mc.player) <= r * r;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !inRange(shadow)) {
                stop();
                return;
            }
            x = shadow.getX();
            y = shadow.getY();
            z = shadow.getZ();
            double r = Config.SHADOW_MUSIC_DISTANCE.get();
            float closeness = (float) (1.0 - Math.sqrt(shadow.distanceToSqr(mc.player)) / r);
            volume = Config.SNAIL_MUSIC_VOLUME.get().floatValue() * Mth.clamp(0.25F + closeness, 0.0F, 1.0F);
        }
    }
}
