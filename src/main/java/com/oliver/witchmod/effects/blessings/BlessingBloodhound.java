package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * you track by scent: entities leave a bright footstep trail on the ground where they walk, sent to YOU only,
 * so you can follow it and even track invisible mobs (you own the particle). Each footstep is remembered and
 * RE-SENT every few ticks so the trail LINGERS ({@code bloodhoundLingerTicks}) rather than flashing once.
 */
public final class BlessingBloodhound extends Effect {
    private static final DustParticleOptions FOOTSTEP = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.1F), 1.0F);
    private static final int MAX_PRINTS = 400;

    private record Print(double x, double y, double z, long tick) {}

    /** player -> the live footprints to keep re-drawing. */
    private static final Map<UUID, List<Print>> TRAIL = new HashMap<>();
    /** player -> (entityId -> last position a print was dropped). */
    private static final Map<UUID, Map<Integer, Vec3>> LAST = new HashMap<>();

    public BlessingBloodhound() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.SNIFFER_EGG);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        double radius = Config.BLOODHOUND_RADIUS.get();
        double minMove = Config.BLOODHOUND_FOOTSTEP_MIN_MOVE.get();
        int linger = Config.BLOODHOUND_LINGER_TICKS.get();
        List<Print> trail = TRAIL.computeIfAbsent(target.getUUID(), k -> new ArrayList<>());
        Map<Integer, Vec3> last = LAST.computeIfAbsent(target.getUUID(), k -> new HashMap<>());

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius))) {
            if (e == target) {
                continue;
            }
            Vec3 pos = e.position();
            Vec3 prev = last.get(e.getId());
            if (prev == null || prev.distanceToSqr(pos) >= minMove * minMove) {
                if (prev != null) {
                    trail.add(new Print(pos.x, pos.y + 0.06, pos.z, now));
                }
                last.put(e.getId(), pos);
            }
        }

        trail.removeIf(p -> now - p.tick() > linger);
        while (trail.size() > MAX_PRINTS) {
            trail.remove(0);
        }
        // re-send the whole live trail periodically so each footprint stays lit for the linger duration.
        if (now % 4 == 0) {
            for (Print p : trail) {
                target.connection.send(new ClientboundLevelParticlesPacket(
                        FOOTSTEP, true, p.x(), p.y(), p.z(), 0.04F, 0.02F, 0.04F, 0.0F, 2));
            }
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        TRAIL.remove(target.getUUID());
        LAST.remove(target.getUUID());
    }
}
