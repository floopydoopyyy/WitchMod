package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Blessings;

/**
 * sonar (sacrificial item SCULK SENSOR — Spectral Arrow is taken by Steady Hands). A slow, dramatic ping:
 * every {@code sonarIntervalTicks} (140s) it spends a {@code sonarBuildupTicks} (2.5s) CHARGE — a swelling hum
 * and gathering rings of light around you — then bursts a pulse that reveals nearby entities: it makes them
 * GLOW briefly and streams your-eyes-only pointers straight at each. A CROUCHED player is stealthier (their
 * effective detection radius is shrunk), and danger sharpens your senses — every hit you take shaves time off
 * the next ping. The time to the next pulse is readable in the Scrying Mirror.
 */
public final class BlessingSonar extends Effect {
    /** player -> ticks until the next ping starts charging. */
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    /** player -> ticks left in the 2.5s buildup, or absent when not charging. */
    private static final Map<UUID, Integer> CHARGE = new HashMap<>();

    // A coherent radar-GREEN palette — reads like an actual sonar sweep, not a rainbow, and not the blue/cyan
    // of vanilla sculk/sonic. One main green with a brighter highlight for the pulse itself.
    private static final DustParticleOptions SONAR_GREEN = dust(0.15F, 0.9F, 0.35F);
    private static final DustParticleOptions SONAR_PALE = dust(0.6F, 1.0F, 0.7F);

    public BlessingSonar() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.SCULK_SENSOR);
    }

    /** not instantly noticeable — you discover it on the first ping. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        TIMER.put(target.getUUID(), Config.SONAR_INTERVAL_TICKS.get());
        CHARGE.remove(target.getUUID());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        TIMER.remove(target.getUUID());
        CHARGE.remove(target.getUUID());
    }

    /** debug: kick off a ping now (charge → scan). */
    @Override
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        beginCharge(target);
        return "sonar charging (scan in " + (Config.SONAR_BUILDUP_TICKS.get() / 20.0) + "s)";
    }

    /** time to the next pulse, shown in the Scrying Mirror. */
    @Override
    public Optional<String> scryingDetail(ServerPlayer target) {
        if (CHARGE.containsKey(target.getUUID())) {
            return Optional.of("§bpulse charging...");
        }
        int t = TIMER.getOrDefault(target.getUUID(), Config.SONAR_INTERVAL_TICKS.get());
        return Optional.of("§bnext pulse in " + Math.max(0, t / 20) + "s");
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        Integer charge = CHARGE.get(id);
        if (charge != null) {
            tickBuildup(target, charge);
            charge--;
            if (charge <= 0) {
                CHARGE.remove(id);
                scan(target);
                TIMER.put(id, Config.SONAR_INTERVAL_TICKS.get());
            } else {
                CHARGE.put(id, charge);
            }
            return;
        }
        int t = TIMER.getOrDefault(id, Config.SONAR_INTERVAL_TICKS.get()) - 1;
        if (t <= 0) {
            beginCharge(target);
        } else {
            TIMER.put(id, t);
        }
    }

    /** taking damage sharpens your senses: shave time off the next ping (called from BlessingEventHandler). */
    public static void onDamaged(ServerPlayer target) {
        Integer t = TIMER.get(target.getUUID());
        if (t != null) {
            TIMER.put(target.getUUID(), Math.max(1, t - Config.SONAR_DAMAGE_REDUCTION_TICKS.get()));
        }
    }

    private static void beginCharge(ServerPlayer target) {
        CHARGE.put(target.getUUID(), Config.SONAR_BUILDUP_TICKS.get());
        ServerLevel level = target.serverLevel();
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                WitchModSounds.SONAR_PING.get(), SoundSource.PLAYERS, 0.7F, 0.6F); // the low charging note
    }

    /** the buildup: gathering rings of warm light + a rising hum, so the pulse is TELEGRAPHED. */
    private void tickBuildup(ServerPlayer target, int chargeLeft) {
        ServerLevel level = target.serverLevel();
        int total = Config.SONAR_BUILDUP_TICKS.get();
        float progress = 1.0F - chargeLeft / (float) Math.max(1, total); // 0 → 1
        // A radar-green ring that shrinks IN toward you as it charges (energy gathering).
        double ringR = 3.5 * (1.0 - progress) + 0.4;
        int pts = 10;
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2 * i / pts) + progress * 4.0;
            double x = target.getX() + Math.cos(a) * ringR;
            double z = target.getZ() + Math.sin(a) * ringR;
            victimParticles(target, SONAR_GREEN, x, target.getY() + 0.6 + progress, z, 1, 0, 0, 0, 0);
        }
        // A rising hum tick a few times across the charge.
        if (chargeLeft % 12 == 0) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, 0.8F + progress * 0.8F);
        }
    }

    /** force a ping's SCAN immediately (skips the buildup) — used by the timer/charge and available internally. */
    public static void scan(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        double radius = Config.SONAR_RADIUS.get();
        double crouchMult = Config.SONAR_CROUCH_RADIUS_MULT.get();
        Vec3 origin = target.getEyePosition();

        // the pulse: a bright burst + a strong warm ring, plus the ping sound at full pitch.
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                WitchModSounds.SONAR_PING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        victimParticles(target, ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40;
            victimParticles(target, SONAR_PALE,
                    target.getX() + Math.cos(a) * 2.0, target.getY() + 1.0, target.getZ() + Math.sin(a) * 2.0, 1, 0, 0, 0, 0);
        }

        int glow = Config.SONAR_GLOW_TICKS.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                e -> e != target && e.isAlive() && !e.isSpectator())) {
            double range = radius;
            if (e instanceof Player p && p.isCrouching()) {
                range *= crouchMult; // crouched players are much harder to catch
            }
            if (target.distanceToSqr(e) > range * range) {
                continue;
            }
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, glow, 0, false, false, false));
            pointToward(target, origin, e);
        }
        Blessings.SONAR.get().markDiscoveredByVictim(target);
    }

    /** A line of your-eyes-only WARM particles from you toward a detected entity, so you're pointed right at it. */
    private static void pointToward(ServerPlayer target, Vec3 origin, LivingEntity e) {
        Vec3 to = e.getBoundingBox().getCenter();
        Vec3 dir = to.subtract(origin);
        double dist = dir.length();
        if (dist < 1.0E-3) {
            return;
        }
        dir = dir.scale(1.0 / dist);
        int steps = (int) Math.min(24, Math.max(4, dist / 1.2));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = origin.add(dir.scale(dist * i / steps));
            victimParticles(target, SONAR_GREEN, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        // A brighter marker right on the target (a radar "blip").
        victimParticles(target, SONAR_PALE, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.0);
    }

    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new Vector3f(r, g, b), 1.3F);
    }

    private static void victimParticles(ServerPlayer target, ParticleOptions particle, double x, double y, double z,
                                        int count, double dx, double dy, double dz, double speed) {
        target.connection.send(new ClientboundLevelParticlesPacket(particle, true, x, y, z,
                (float) dx, (float) dy, (float) dz, (float) speed, count));
    }
}
