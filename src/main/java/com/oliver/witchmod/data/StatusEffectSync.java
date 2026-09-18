package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * keeps the cursed/blessed wrapper effects matched to what's actually active. call {@link #sync} after any
 * mutation to a player's effects — {@link EffectManager} already does for every mutation it makes.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class StatusEffectSync {
    private static final int PARTICLE_COUNT = 20;

    private StatusEffectSync() {}

    /** a sound queued to play at a spot after a short delay (the blessed/cursed sting after the afflicted one). */
    private static final class Delayed {
        final ServerLevel level;
        final Vec3 pos;
        final SoundEvent sound;
        final float volume;
        final float pitch;
        int delay;
        Delayed(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch, int delay) {
            this.level = level;
            this.pos = pos;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.delay = delay;
        }
    }
    private static final List<Delayed> DELAYED = new ArrayList<>();

    private static final DustParticleOptions BLESS_YELLOW = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.4F), 1.3F);
    private static final DustParticleOptions BLESS_PALE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.85F), 1.1F);

    public static void sync(ServerPlayer player) {
        updateWrapper(player, WitchModMobEffects.CURSED, maxRemaining(player, EffectCategory.CURSE), false);
        updateWrapper(player, WitchModMobEffects.BLESSED, maxRemaining(player, EffectCategory.BLESSING), true);
    }

    private static int maxRemaining(ServerPlayer player, EffectCategory category) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null) {
            return 0;
        }
        int max = 0;
        for (ResourceLocation id : active.activeIds()) {
            ActiveEffectInstance inst = active.get(id).orElse(null);
            EffectCategory real = WitchModRegistries.EFFECT_REGISTRY.getOptional(id).map(Effect::category).orElse(null);
            if (inst == null || real == null) {
                continue;
            }
            // ink sac (hidden) contributes to neither wrapper; wither rose (disguised) to the opposite
            EffectCategory shown = effectiveCategory(real, inst.display());
            if (shown == category) {
                max = Math.max(max, inst.remainingTicks());
            }
        }
        return max;
    }

    private static EffectCategory effectiveCategory(EffectCategory real, int display) {
        if (display == ActiveEffectInstance.DISPLAY_HIDDEN) {
            return null;
        }
        if (display == ActiveEffectInstance.DISPLAY_DISGUISED) {
            return real == EffectCategory.CURSE ? EffectCategory.BLESSING : EffectCategory.CURSE;
        }
        return real;
    }

    /**
     * matches one wrapper's duration to {@code duration} (0 = remove). particles burst only on onset and
     * expiry, never in between; the wrapper is applied particles-off, icon-on. re-added fresh each sync so
     * the duration tracks exactly including shortening (holy-water burn-down) — a keep-longer merge would
     * refuse to shrink it.
     */
    private static void updateWrapper(ServerPlayer player, Holder<MobEffect> wrapper, int duration, boolean blessing) {
        boolean had = player.hasEffect(wrapper);
        if (duration > 0) {
            if (had) {
                player.removeEffect(wrapper);
            }
            player.addEffect(new MobEffectInstance(wrapper, duration, 0, false, false, true));
            if (!had) {
                burstParticles(player, blessing);
                playOnsetSounds(player, blessing);
            }
        } else if (had) {
            player.removeEffect(wrapper);
            burstParticles(player, blessing);
        }
    }

    /** onset stings: the generic "afflicted" sound immediately, then the blessed/cursed one a beat later. */
    private static void playOnsetSounds(ServerPlayer player, boolean blessing) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        level.playSound(null, player.blockPosition(), WitchModSounds.AFFLICTED.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        DELAYED.add(new Delayed(level, pos,
                (blessing ? WitchModSounds.BLESSED : WitchModSounds.CURSED).get(), 1.0F, 1.0F, 7));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (DELAYED.isEmpty()) {
            return;
        }
        Iterator<Delayed> it = DELAYED.iterator();
        while (it.hasNext()) {
            Delayed d = it.next();
            if (--d.delay <= 0) {
                d.level.playSound(null, d.pos.x, d.pos.y, d.pos.z, d.sound, SoundSource.PLAYERS, d.volume, d.pitch);
                it.remove();
            }
        }
    }

    /**
     * the onset/expiry burst, coloured by category: a curse throws purple witch motes, a blessing warm
     * yellow/white stars — so what enters you reads good or bad at a glance.
     */
    private static void burstParticles(ServerPlayer player, boolean blessing) {
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        if (blessing) {
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, PARTICLE_COUNT, 0.3, 0.5, 0.3, 0.02);
            level.sendParticles(BLESS_YELLOW, x, y, z, PARTICLE_COUNT, 0.3, 0.5, 0.3, 0.0);
            level.sendParticles(BLESS_PALE, x, y, z, PARTICLE_COUNT / 2, 0.3, 0.5, 0.3, 0.0);
        } else {
            level.sendParticles(ParticleTypes.WITCH, x, y, z, PARTICLE_COUNT, 0.3, 0.5, 0.3, 0.05);
        }
    }
}
