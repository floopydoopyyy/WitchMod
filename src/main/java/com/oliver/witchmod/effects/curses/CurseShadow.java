package com.oliver.witchmod.effects.curses;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.entities.ShadowEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * secret curse: your shadow. like mario's shadow levels — a copy of you retraces your exact path a few seconds
 * behind (shadowDelayTicks), so stopping is deadly: stand still that long and it walks into you, and its touch
 * kills. it can't touch anyone else, and it can't enter water: if the path leads into water (or you change
 * dimension, or it catches you) it vanishes, then re-forms behind you after shadowRespawnTicks.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.oliver.witchmod.WitchMod.MODID)
public final class CurseShadow extends Effect {
    /**
     * logging out (or the server stopping) drops the chase cleanly: the shadow poofs and its recorded path is
     * forgotten, so you come back to a fresh shadow rather than one replaying a stale path. the entity itself is
     * never saved, and an orphan removes itself, so a hard crash can't leave one behind either.
     */
    @net.neoforged.bus.api.SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State s = STATES.remove(player.getUUID());
            if (s != null) {
                despawn(player.serverLevel(), s);
            }
        }
    }

    private record Snap(double x, double y, double z, float yRot, float xRot, Pose pose) {}

    private static final class State {
        final ArrayDeque<Snap> history = new ArrayDeque<>();
        @Nullable UUID shadowId;
        long respawnAt;
        long summonUntil;
        @Nullable ResourceKey<Level> dimension;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public CurseShadow() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 90, () -> Items.DRAGON_HEAD);
    }

    /** too drastic to land on a 90-second pandora's box timer. */
    @Override
    public boolean inRotations() {
        return false;
    }

    /** discovered the first time it appears behind you. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        STATES.putIfAbsent(target.getUUID(), new State());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        State s = STATES.remove(target.getUUID());
        if (s != null) {
            despawn(target.serverLevel(), s);
        }
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        State s = STATES.computeIfAbsent(target.getUUID(), k -> new State());
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();
        if (s.dimension != null && s.dimension != level.dimension()) {
            vanish(level, s, now); // the old shadow is stranded in the other dimension
        }
        s.dimension = level.dimension();
        if (target.isSpectator() || !target.isAlive()) {
            vanish(level, s, now);
            return;
        }
        if (now < s.respawnAt) {
            return;
        }

        s.history.addLast(new Snap(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot(),
                target.getPose() == Pose.CROUCHING || target.getPose() == Pose.SWIMMING ? target.getPose() : Pose.STANDING));
        int delay = Config.SHADOW_DELAY_TICKS.get();
        if (s.history.size() <= delay) {
            return; // still forming
        }

        ShadowEntity shadow = find(level, s);
        if (shadow == null) {
            Snap at = s.history.pollFirst();
            // idle protection: never form on (or right next to) a player who's been standing still, or in water.
            double min = Config.SHADOW_SPAWN_MIN_DISTANCE.get();
            if (inWater(level, at) || horizontalDistSq(target, at) < min * min) {
                return;
            }
            summon(level, s, target, at, now);
            return;
        }
        if (now < s.summonUntil) {
            // rising out of the ground in place; the path keeps recording, so it lunges to catch up afterwards.
            shadow.markDriven();
            level.sendParticles(ParticleTypes.SCULK_SOUL, shadow.getX(), shadow.getY() + 0.2, shadow.getZ(), 2, 0.3, 0.1, 0.3, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, shadow.getX(), shadow.getY() + 0.1, shadow.getZ(), 2, 0.35, 0.05, 0.35, 0.0);
            return;
        }
        if (now - s.summonUntil > Config.SHADOW_MAX_CHASE_TICKS.get()) {
            vanish(level, s, now); // a bout lasts so long, then it gives up and comes back later
            return;
        }
        Snap at = s.history.pollFirst();
        if (s.history.size() > delay) {
            at = s.history.pollFirst(); // behind after the summon: double-step until it's back on your heels
        }
        if (inWater(level, at)) {
            vanish(level, s, now);
            return;
        }
        shadow.setPos(at.x(), at.y(), at.z());
        shadow.setYRot(at.yRot());
        shadow.setYHeadRot(at.yRot());
        shadow.setYBodyRot(at.yRot());
        shadow.setXRot(at.xRot());
        shadow.setPose(at.pose());
        shadow.markDriven();

        double dx = target.getX() - at.x();
        double dz = target.getZ() - at.z();
        double reach = Config.SHADOW_CATCH_DISTANCE.get();
        if (!target.isCreative() && dx * dx + dz * dz <= reach * reach && Math.abs(target.getY() - at.y()) < 1.0) {
            target.hurt(WitchModDamageTypes.shadow(level), Float.MAX_VALUE);
            caughtBurst(level, target);
            vanish(level, s, now);
        }
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        State s = STATES.computeIfAbsent(target.getUUID(), k -> new State());
        ServerLevel level = target.serverLevel();
        if ("vanish".equalsIgnoreCase(arg)) {
            vanish(level, s, level.getGameTime());
            return "shadow vanished — it re-forms behind you after the respawn delay";
        }
        // skip the wait: fill the history with where you stand now, so it appears on you... almost.
        despawn(level, s);
        s.history.clear();
        s.respawnAt = 0;
        for (int i = 0; i < Config.SHADOW_DELAY_TICKS.get(); i++) {
            s.history.addLast(new Snap(target.getX() - target.getLookAngle().x * 5, target.getY(),
                    target.getZ() - target.getLookAngle().z * 5, target.getYRot(), 0, Pose.STANDING));
        }
        return "shadow summoning 5 blocks behind you — move! (arg 'vanish' makes it poof)";
    }

    @Override
    public List<String> debugArgs() {
        return List.of("vanish");
    }

    /** the shadow claws up out of the ground at {@code at}: it rises for shadowSummonTicks before it can move or catch. */
    private static void summon(ServerLevel level, State s, ServerPlayer target, Snap at, long now) {
        ShadowEntity shadow = WitchModEntities.SHADOW.get().create(level);
        if (shadow == null) {
            return;
        }
        shadow.setOwner(target.getUUID());
        shadow.moveTo(at.x(), at.y(), at.z(), at.yRot(), 0.0F);
        shadow.markDriven();
        level.addFreshEntity(shadow);
        s.shadowId = shadow.getUUID();
        s.summonUntil = now + Config.SHADOW_SUMMON_TICKS.get();
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x(), at.y() + 0.1, at.z(), 20, 0.5, 0.1, 0.5, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, at.x(), at.y() + 0.5, at.z(), 40, 0.4, 0.4, 0.4, 0.6);
        level.playSound(null, at.x(), at.y(), at.z(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5F, 0.5F);
        Curses.SHADOW.get().markDiscoveredByVictim(target);
    }

    private static double horizontalDistSq(ServerPlayer target, Snap at) {
        double dx = target.getX() - at.x();
        double dz = target.getZ() - at.z();
        return dx * dx + dz * dz;
    }

    /** the path point is water (or a boat / lily pad on water) — somewhere it can't go. */
    private static boolean inWater(ServerLevel level, Snap at) {
        return level.getFluidState(BlockPos.containing(at.x(), at.y(), at.z())).is(FluidTags.WATER)
                || level.getFluidState(BlockPos.containing(at.x(), at.y() - 0.5, at.z())).is(FluidTags.WATER);
    }

    @Nullable
    private static ShadowEntity find(ServerLevel level, State s) {
        if (s.shadowId == null) {
            return null;
        }
        Entity e = level.getEntity(s.shadowId);
        return e instanceof ShadowEntity shadow && shadow.isAlive() ? shadow : null;
    }

    /** poof, forget the path, and wait out the respawn delay before following again. */
    private static void vanish(ServerLevel level, State s, long now) {
        boolean had = despawn(level, s);
        s.history.clear();
        if (had || s.respawnAt <= now) {
            s.respawnAt = now + Config.SHADOW_RESPAWN_TICKS.get();
        }
    }

    private static boolean despawn(ServerLevel level, State s) {
        ShadowEntity shadow = find(level, s);
        s.shadowId = null;
        if (shadow == null) {
            return false;
        }
        puff(level, shadow.getX(), shadow.getY(), shadow.getZ());
        shadow.discard();
        return true;
    }

    /** the catch: a burst of purple — dust, dragon's breath and reversed portal sparks — with a bang (no block damage). */
    private static void caughtBurst(ServerLevel level, ServerPlayer target) {
        double x = target.getX();
        double y = target.getY() + 1.0;
        double z = target.getZ();
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 4, 0.6, 0.6, 0.6, 0.0);
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6F, 0.15F, 0.9F), 3.0F),
                x, y, z, 90, 1.3, 1.1, 1.3, 0.0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 50, 0.9, 0.9, 0.9, 0.06);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 70, 0.4, 0.6, 0.4, 0.7);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    private static void puff(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 30, 0.3, 0.6, 0.3, 0.4);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 8, 0.25, 0.5, 0.25, 0.01);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 0.6F);
    }
}
