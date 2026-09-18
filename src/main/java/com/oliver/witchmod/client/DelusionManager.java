package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * client-side lifecycle for the Delusions curse: spawns the fake players, decides when one has been stared
 * at long enough to stare back, and pops them when they're hit, walked into, or simply outstay their welcome.
 *
 * <p>The server contributes exactly one number — {@link WitchModAttachments#DELUSIONS_SIGNAL} — which is
 * {@code 0} while the curse is off and gets a new random value each time another delusion is due. Everything
 * here runs on the victim's machine alone, which is what makes the illusion airtight: there is no entity for
 * anyone else's client to see and none for the server to be asked about.
 */
public final class DelusionManager {
    private static final List<DelusionPlayer> ACTIVE = new ArrayList<>();
    private static long lastSignal;

    private DelusionManager() {}

    public static void clientTick(Minecraft minecraft) {
        LocalPlayer victim = minecraft.player;
        ClientLevel level = minecraft.level;
        if (victim == null || level == null) {
            clearAll();
            return;
        }

        long signal = victim.getData(WitchModAttachments.DELUSIONS_SIGNAL);
        if (signal == 0L) {
            // curse cured or expired: they were never there in the first place, so they go without a puff.
            if (!ACTIVE.isEmpty()) {
                clearAll();
            }
            lastSignal = 0L;
            return;
        }
        double reach = Config.DELUSIONS_HIT_REACH.get();
        double despawn = Config.DELUSIONS_DESPAWN_DISTANCE.get();
        int lifetime = Config.DELUSIONS_LIFETIME_MAX.get();

        // retire the finished ones FIRST. The concurrent cap is checked against this list, so pruning after
        // spawning would let it be judged against stale entries.
        Iterator<DelusionPlayer> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            DelusionPlayer delusion = iterator.next();
            if (delusion.isRemoved() || delusion.level() != level) {
                iterator.remove();
                pop(delusion, false);
                continue;
            }
            // blundered into: close enough to touch counts as contact, since they aren't really collidable.
            if (delusion.distanceToSqr(victim) < 1.0) {
                iterator.remove();
                pop(delusion, true);
                continue;
            }
            if (delusion.getLifeTicks() > lifetime || delusion.distanceToSqr(victim) > despawn * despawn) {
                iterator.remove();
                pop(delusion, false);
                continue;
            }

            boolean watched = isWatchedBy(delusion, victim, reach);
            delusion.updateSeen(watched);
            if (delusion.shouldRealise()) {
                delusion.beginRealisation();
            }
        }

        // only now, against an accurate count, consider adding another.
        if (signal != lastSignal) {
            lastSignal = signal;
            trySpawn(minecraft, victim, level, signal);
        }
    }

    /**
     * the victim swung. Ray-traces their reach against delusions only — vanilla's crosshair deliberately
     * can't see them ({@code isPickable} is false), because targeting one would make the client send the
     * server an attack packet for an entity that does not exist on it.
     *
     * @return true if a delusion was hit, in which case the swing should not also hit the world behind it
     */
    public static boolean onAttack(LocalPlayer victim) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        double reach = Config.DELUSIONS_HIT_REACH.get();
        Vec3 eye = victim.getEyePosition();
        Vec3 end = eye.add(victim.getViewVector(1.0F).scale(reach));

        DelusionPlayer hit = null;
        double nearest = Double.MAX_VALUE;
        for (DelusionPlayer delusion : ACTIVE) {
            AABB box = delusion.getBoundingBox().inflate(0.3);
            var clip = box.clip(eye, end);
            if (clip.isPresent()) {
                double distance = eye.distanceToSqr(clip.get());
                if (distance < nearest) {
                    nearest = distance;
                    hit = delusion;
                }
            }
        }
        if (hit == null) {
            return false;
        }
        ACTIVE.remove(hit);
        pop(hit, true);
        return true;
    }

    /** used by the realisation state to remove itself mid-tick. */
    static void vanish(DelusionPlayer delusion, boolean withPuff) {
        ACTIVE.remove(delusion);
        pop(delusion, withPuff);
    }

    private static void pop(DelusionPlayer delusion, boolean withPuff) {
        if (withPuff) {
            delusion.puff(14);
            // vanilla on purpose (Oliver's call — this curse needs no custom sounds). Pitched-up enderman
            // reads as "gone in a puff" and quietly ties back to the Ender Pearl it's cast with.
            delusion.playAt(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.3F);
        }
        delusion.cleanUp();
        if (delusion.level() instanceof ClientLevel clientLevel) {
            clientLevel.removeEntity(delusion.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static void clearAll() {
        for (DelusionPlayer delusion : ACTIVE) {
            delusion.cleanUp();
            if (delusion.level() instanceof ClientLevel clientLevel) {
                clientLevel.removeEntity(delusion.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        ACTIVE.clear();
    }

    private static void trySpawn(Minecraft minecraft, LocalPlayer victim, ClientLevel level, long seed) {
        if (ACTIVE.size() >= Config.DELUSIONS_MAX_CONCURRENT.get()) {
            return;
        }
        PlayerInfo mirrored = pickPlayerToMirror(minecraft, victim, seed);
        if (mirrored == null) {
            return;
        }

        RandomSource rng = RandomSource.create(seed);
        Vec3 spot = findSpawnSpot(level, victim, rng);
        if (spot == null) {
            return;
        }

        DelusionPlayer delusion = new DelusionPlayer(level, mirrored, seed);
        delusion.setPos(spot.x, spot.y, spot.z);
        delusion.setOldPosAndRot();
        level.addEntity(delusion);
        ACTIVE.add(delusion);
    }

    /**
     * someone really on the server, so the skin and nametag are ones the victim recognises. Their own is in
     * the pool by default — and unavoidably so when they're alone, since there is nobody else to be.
     */
    private static PlayerInfo pickPlayerToMirror(Minecraft minecraft, LocalPlayer victim, long seed) {
        if (minecraft.getConnection() == null) {
            return null;
        }
        List<PlayerInfo> others = new ArrayList<>();
        List<PlayerInfo> self = new ArrayList<>();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (info.getProfile().getId().equals(victim.getUUID())) {
                self.add(info);
            } else {
                others.add(info);
            }
        }
        List<PlayerInfo> pool;
        if (others.isEmpty()) {
            pool = self;                                     // alone on the server: it can only be you
        } else if (Config.DELUSIONS_MIRROR_SELF.get()) {
            pool = new ArrayList<>(others);
            pool.addAll(self);
        } else {
            pool = others;
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(Math.floorMod(seed, pool.size()) % pool.size());
    }

    /**
     * somewhere it could plausibly already have been standing: real ground, at a distance, and — where
     * possible — out of the victim's current view, so they turn round and find it rather than watching it
     * blink into existence.
     */
    private static Vec3 findSpawnSpot(ClientLevel level, LocalPlayer victim, RandomSource rng) {
        int min = Config.DELUSIONS_SPAWN_RANGE_MIN.get();
        int max = Math.max(min + 1, Config.DELUSIONS_SPAWN_RANGE_MAX.get());
        Vec3 fallback = null;

        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double distance = min + rng.nextDouble() * (max - min);
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
            // prefer somewhere they aren't looking right now.
            Vec3 toSpot = spot.add(0.0, 1.0, 0.0).subtract(victim.getEyePosition()).normalize();
            if (victim.getViewVector(1.0F).dot(toSpot) < 0.4) {
                return spot;
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

    /** in the view cone, in line of sight, and not so close it's about to be walked into anyway. */
    private static boolean isWatchedBy(DelusionPlayer delusion, LocalPlayer victim, double reach) {
        Vec3 eye = victim.getEyePosition();
        Vec3 theirEye = delusion.getEyePosition();
        Vec3 delta = theirEye.subtract(eye);
        double distance = delta.length();
        if (distance < 0.5 || distance > 64.0) {
            return false;
        }
        if (victim.getViewVector(1.0F).dot(delta.scale(1.0 / distance)) < Config.DELUSIONS_VIEW_CONE_DOT.get()) {
            return false;
        }
        HitResult blocked = victim.level().clip(new ClipContext(
                eye, theirEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        return blocked.getType() == HitResult.Type.MISS;
    }
}
