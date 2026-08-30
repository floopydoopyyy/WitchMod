package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.entities.SnailEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * The Snail (sacrificial item NAUTILUS SHELL): a tiny, immortal snail that never stops chasing you and
 * detonates you the instant it touches you.
 *
 * <p><b>Speed is the whole balance.</b> {@code snailBaseSpeedBlocksPerSecond} is the fundamental constant: how
 * fast it glides when right on top of you. That speed SCALES UP with distance ({@code snailDistanceScale} per
 * block, capped) — so it dawdles like a real snail when near but hunts you down fast when you run.
 *
 * <p><b>It's an illusion when you're not looking.</b> The curse owns a VIRTUAL position that advances every
 * tick regardless; only within {@code snailMaterialiseRadius} is the real {@link SnailEntity} spawned and slid
 * along the ground toward you (facing you). Beyond it, the entity is despawned and the virtual chase carries
 * on — so it's always where it should be when you turn around, without keeping anything loaded.
 *
 * <p>Its dread music (a client-side loop within {@code snailMusicDistance}) is handled by
 * {@code client/SnailSoundManager}.
 */
public final class CurseSnail extends Effect {
    private static final Map<UUID, Vec3> VIRTUAL = new HashMap<>();
    private static final Map<UUID, SnailEntity> ENTITY = new HashMap<>();

    public CurseSnail() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 22, () -> net.minecraft.world.item.Items.NAUTILUS_SHELL);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true; // you realise the first time it draws near
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        VIRTUAL.put(target.getUUID(), farPointAround(target));
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        ServerLevel level = target.serverLevel();
        Vec3 snail = VIRTUAL.computeIfAbsent(id, k -> farPointAround(target));
        Vec3 you = target.position();
        double dist = snail.distanceTo(you);

        // Touched you — detonate, then reappear far off to begin the hunt anew.
        if (dist <= Config.SNAIL_TOUCH_DISTANCE.get()) {
            detonate(target);
            VIRTUAL.put(id, farPointAround(target));
            dematerialise(id);
            return;
        }

        // Advance the virtual position toward you at the distance-scaled speed.
        double bps = Math.min(Config.SNAIL_MAX_SPEED.get(),
                Config.SNAIL_BASE_SPEED.get() * (1.0 + dist * Config.SNAIL_DISTANCE_SCALE.get()));
        double step = Math.min(bps / 20.0, dist);
        Vec3 dir = you.subtract(snail).normalize();
        snail = snail.add(dir.scale(step));
        VIRTUAL.put(id, snail);
        dist = snail.distanceTo(you);

        if (dist <= Config.SNAIL_MUSIC_DISTANCE.get()) {
            Curses.SNAIL.get().markDiscoveredByVictim(target); // the music kicks in — you know now
        }

        // Materialise the real entity only when close enough to be seen, and slide it along the ground.
        if (dist <= Config.SNAIL_MATERIALISE_RADIUS.get()) {
            SnailEntity entity = ENTITY.get(id);
            if (entity == null || !entity.isAlive()) {
                entity = WitchModEntities.SNAIL.get().create(level);
                if (entity == null) {
                    return;
                }
                level.addFreshEntity(entity);
                ENTITY.put(id, entity);
            }
            // Snap to the ground surface at its spot (so it slides ACROSS the ground); if that's far from the
            // tracked height (you're underground/up high), fall back to the tracked height instead.
            double surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(snail.x), Mth.floor(snail.z));
            double y = Math.abs(surface - snail.y) <= 4.0 ? surface : snail.y;
            entity.setPos(snail.x, y, snail.z);
            // Face you — the model's front (eye stalks) is -Z at yaw 0.
            float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
            entity.setYRot(yaw);
            entity.setYBodyRot(yaw);
            entity.setYHeadRot(yaw);
            entity.setXRot(0.0F);
        } else {
            dematerialise(id);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        dematerialise(id);
        VIRTUAL.remove(id);
    }

    // --- Internals ---------------------------------------------------------------------------------------

    private static void detonate(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        level.explode(null, target.getX(), target.getY(), target.getZ(),
                Config.SNAIL_EXPLODE_POWER.get().floatValue(), Level.ExplosionInteraction.MOB);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
        // Guaranteed lethal (barring a totem / Last Stand, which is fair counterplay).
        target.hurt(level.damageSources().explosion(null, null), 1000.0F);
    }

    private static void dematerialise(UUID id) {
        SnailEntity entity = ENTITY.remove(id);
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
    }

    /** A point {@code snailSpawnDistance} away from the victim, in a random horizontal direction, at their height. */
    private static Vec3 farPointAround(ServerPlayer target) {
        RandomSource random = target.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double d = Config.SNAIL_SPAWN_DISTANCE.get();
        return target.position().add(Math.cos(angle) * d, 0.0, Math.sin(angle) * d);
    }
}
