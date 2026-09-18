package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * the ward's block visual: a coloured lash streaks in from the caster's direction, then flares and is blocked
 * at the victim with a shield clang (purple curse / gold blessing).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WardEffects {
    private static final DustParticleOptions CURSE_DUST = new DustParticleOptions(new Vector3f(0.62F, 0.12F, 0.82F), 1.3F);
    private static final DustParticleOptions BLESS_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.32F), 1.3F);
    private static final double LASH_SPEED = 0.75;

    private static final class Lash {
        final ServerLevel level;
        final UUID victimId;
        Vec3 pos;
        final boolean curse;
        int ticks;

        Lash(ServerLevel level, UUID victimId, Vec3 pos, boolean curse, int ticks) {
            this.level = level;
            this.victimId = victimId;
            this.pos = pos;
            this.curse = curse;
            this.ticks = ticks;
        }
    }

    private static final List<Lash> LASHES = new ArrayList<>();

    private WardEffects() {}

    /** fire a lash coming from the caster toward the victim, which ends in a block flare. */
    public static void startLash(ServerPlayer victim, ServerPlayer caster, boolean curse) {
        ServerLevel level = victim.serverLevel();
        Vec3 to = victim.position().add(0, 1.0, 0);
        Vec3 from = caster.position().add(0, 1.0, 0);
        Vec3 dir = from.subtract(to);
        double dist = dir.length();
        if (dist < 0.6) {
            blockFlare(level, to, curse); // caster right on top of you — just the block flare
            return;
        }
        // start a few blocks out toward the caster (capped, so a distant cast still resolves quickly).
        Vec3 start = to.add(dir.scale(Math.min(dist, 8.0) / dist));
        LASHES.add(new Lash(level, victim.getUUID(), start, curse, 60));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (LASHES.isEmpty()) {
            return;
        }
        Iterator<Lash> it = LASHES.iterator();
        while (it.hasNext()) {
            Lash lash = it.next();
            ServerPlayer victim = lash.level.getServer().getPlayerList().getPlayer(lash.victimId);
            if (victim == null || --lash.ticks <= 0) {
                it.remove();
                continue;
            }
            Vec3 to = victim.position().add(0, 1.0, 0);
            Vec3 dir = to.subtract(lash.pos);
            double dist = dir.length();
            if (dist <= 1.3) {
                blockFlare(lash.level, to, lash.curse);
                it.remove();
                continue;
            }
            lash.pos = lash.pos.add(dir.scale(Math.min(LASH_SPEED, dist) / dist));
            trail(lash.level, lash.pos, lash.curse);
        }
    }

    private static void trail(ServerLevel level, Vec3 pos, boolean curse) {
        level.sendParticles(curse ? CURSE_DUST : BLESS_DUST, pos.x, pos.y, pos.z, 3, 0.06, 0.06, 0.06, 0.0);
        level.sendParticles(curse ? ParticleTypes.WITCH : ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
    }

    /** the block itself: a bright ring flare + a subtle shield clang. */
    private static void blockFlare(ServerLevel level, Vec3 at, boolean curse) {
        DustParticleOptions dust = curse ? CURSE_DUST : BLESS_DUST;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            level.sendParticles(dust, at.x + Math.cos(a) * 0.7, at.y, at.z + Math.sin(a) * 0.7, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.2F);
    }
}
