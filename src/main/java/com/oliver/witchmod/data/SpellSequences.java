package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * the short "cast" sequences for the coin + effigy: a server-time queue runs a brief particle build-up and
 * applies the effects at the END, so the onset feedback lands with the animation rather than before it.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class SpellSequences {
    private SpellSequences() {}

    /** ~0.7s coin build-up; ~0.8s effigy travel — short but readable. */
    private static final int COIN_DELAY_TICKS = 14;
    private static final int EFFIGY_DELAY_TICKS = 16;

    private static final Vector3f CURSE_COLOUR = new Vector3f(0.62f, 0.16f, 0.78f);   // magic purple
    private static final Vector3f BLESSING_COLOUR = new Vector3f(1.0f, 0.83f, 0.30f); // warm gold

    private enum Kind { COIN, EFFIGY }

    /** one rolled attachment plus the colour it should read as (by category). */
    private record Fx(ResourceLocation effectId, int duration, boolean curse) {}

    private record Seq(ServerLevel level, UUID caster, UUID target, List<Fx> effects,
                       long applyAt, Kind kind, boolean apply) {}

    private static final List<Seq> PENDING = new ArrayList<>();

    private static Fx fxOf(Holder.Reference<Effect> effect, int duration) {
        return new Fx(effect.key().location(), duration, effect.value().category() == EffectCategory.CURSE);
    }

    // ---- Coin: split at the user, then land on the user ---------------------------------------------------

    /** the reworked coin's "used" sequence: it breaks into {@code effects} (colour-coded), then applies them. */
    public static void coin(ServerPlayer user, List<Holder.Reference<Effect>> effects, int durationTicks) {
        if (!(user.level() instanceof ServerLevel level) || effects.isEmpty()) {
            return;
        }
        List<Fx> fx = new ArrayList<>();
        for (Holder.Reference<Effect> e : effects) {
            fx.add(fxOf(e, durationTicks));
        }
        level.playSound(null, user.blockPosition(), WitchModSounds.COIN_FLIP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        splitBurst(level, user.getX(), user.getY() + 1.0, user.getZ(), fx);
        PENDING.add(new Seq(level, user.getUUID(), user.getUUID(), fx,
                level.getGameTime() + COIN_DELAY_TICKS, Kind.COIN, true));
    }

    /** a coin BREAKS: a bright flash, then one outward puff per attachment in its own colour. */
    private static void splitBurst(ServerLevel level, double x, double y, double z, List<Fx> fx) {
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.1, 0.1, 0.1, 0.02);
        int n = fx.size();
        for (int i = 0; i < n; i++) {
            double angle = (Math.PI * 2.0 * i) / n + 0.4;
            double dx = Math.cos(angle) * 0.14, dz = Math.sin(angle) * 0.14;
            DustParticleOptions dust = new DustParticleOptions(fx.get(i).curse() ? CURSE_COLOUR : BLESSING_COLOUR, 1.4F);
            // count 0 = "one particle with this velocity" — a stream shooting out in the effect's direction.
            for (int p = 0; p < 6; p++) {
                level.sendParticles(dust, x, y, z, 0, dx * (0.6 + p * 0.2), 0.06, dz * (0.6 + p * 0.2), 0.5);
            }
            level.sendParticles(dust, x + dx * 3, y + 0.25, z + dz * 3, 6, 0.12, 0.12, 0.12, 0.01);
        }
    }

    // ---- Effigy: stream across from caster to victim, then land on the victim ------------------------------

    /**
     * The effigy forward sequence. {@code effects} were already lifted off the caster; they stream across and
     * land on {@code target} after a short delay. When {@code apply} is false (a DEBUG cast on a villager) the
     * whole animation still plays but nothing is actually applied.
     */
    public static void effigy(ServerPlayer caster, LivingEntity target,
                              List<Holder.Reference<Effect>> effects, int[] durations, boolean apply) {
        if (!(caster.level() instanceof ServerLevel level) || effects.isEmpty()) {
            return;
        }
        List<Fx> fx = new ArrayList<>();
        for (int i = 0; i < effects.size(); i++) {
            fx.add(fxOf(effects.get(i), durations[i]));
        }
        level.playSound(null, target.blockPosition(), WitchModSounds.SPELL_GLINT.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        level.playSound(null, target.blockPosition(), WitchModSounds.LASH_SPAWN.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
        PENDING.add(new Seq(level, caster.getUUID(), target.getUUID(), fx,
                level.getGameTime() + EFFIGY_DELAY_TICKS, Kind.EFFIGY, apply));
    }

    // ---- Ticking ------------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Seq> it = PENDING.iterator();
        while (it.hasNext()) {
            Seq s = it.next();
            long now = s.level().getGameTime();
            if (s.kind() == Kind.EFFIGY) {
                streamEffigy(s); // per-tick travel particles between caster and victim
            }
            if (now < s.applyAt()) {
                continue;
            }
            it.remove();
            land(s);
        }
    }

    /** a ribbon of colour flows from the caster toward the victim while the effigy sequence runs. */
    private static void streamEffigy(Seq s) {
        Entity caster = s.level().getEntity(s.caster());
        Entity target = s.level().getEntity(s.target());
        if (caster == null || target == null) {
            return;
        }
        Vec3 from = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int steps = 6;
        for (int i = 0; i < s.effects().size(); i++) {
            Fx fx = s.effects().get(i);
            DustParticleOptions dust = new DustParticleOptions(fx.curse() ? CURSE_COLOUR : BLESSING_COLOUR, 1.2F);
            for (int k = 0; k < steps; k++) {
                double t = (k + (s.level().getGameTime() % 4) * 0.25) / steps;
                Vec3 p = from.lerp(to, t);
                s.level().sendParticles(dust, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    /** sequence end: apply the effects (unless it's a debug no-apply run) with a landing flourish. */
    private static void land(Seq s) {
        Entity targetEntity = s.level().getEntity(s.target());
        double x = targetEntity != null ? targetEntity.getX() : 0;
        double y = (targetEntity != null ? targetEntity.getY() : 0) + 1.0;
        double z = targetEntity != null ? targetEntity.getZ() : 0;
        if (targetEntity != null) {
            for (Fx fx : s.effects()) {
                DustParticleOptions dust = new DustParticleOptions(fx.curse() ? CURSE_COLOUR : BLESSING_COLOUR, 1.5F);
                s.level().sendParticles(dust, x, y, z, 12, 0.35, 0.5, 0.35, 0.02);
            }
            s.level().sendParticles(ParticleTypes.ENCHANT, x, y, z, 20, 0.4, 0.6, 0.4, 0.6);
        }
        if (!s.apply()) {
            return; // DEBUG effigy on a non-player: show the animation, apply nothing
        }
        ServerPlayer target = s.level().getServer().getPlayerList().getPlayer(s.target());
        if (target == null) {
            return; // logged off mid-sequence
        }
        ServerPlayer caster = s.caster().equals(s.target())
                ? null // a coin self-gamble isn't attributed to anyone
                : s.level().getServer().getPlayerList().getPlayer(s.caster());
        for (Fx fx : s.effects()) {
            EffectManager.holderOf(fx.effectId()).ifPresent(holder ->
                    EffectManager.apply(target, holder, fx.duration(), caster));
        }
    }
}
