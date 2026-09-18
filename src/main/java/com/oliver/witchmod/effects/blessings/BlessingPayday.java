package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.entities.TaxManEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * the Tax Man, but this time he owes YOU. He turns up, walks over, drops your money in front of you, and leaves — and that <b>one
 * delivery consumes the blessing</b>. What he hands over is everything the {@link com.oliver.witchmod.data.TaxBank}
 * has confiscated (across the whole world, memory-capped) — or, if the bank is empty, a random consolation gift.
 *
 * <p><b>He waits for a good moment</b> rather than barging in: no hostile mob within {@code taxmanSafeRadius}
 * AND you standing roughly still for {@code taxmanIdleTicks}. Until then the blessing just watches. The
 * payout itself (draining the bank, or rolling the gift) happens on the entity at the instant of hand-off, so
 * the bank is only ever drained on a genuine, completed delivery.
 */
public final class BlessingPayday extends Effect {
    /** per-recipient waiting state: how long they've been still, where they were, and the summoned entity. */
    private static final class Watch {
        int idleTicks;
        Vec3 lastPos;
        @Nullable UUID entityId;
        boolean delivered; // he actually handed the goods over (vs. vanishing to a relog before he arrived)

        Watch(Vec3 pos) {
            this.lastPos = pos;
        }
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();
    private static final double MOVE_EPSILON = 0.05; // per-tick displacement that counts as "moving"

    public BlessingPayday() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.GOLD_INGOT);
    }

    /** you find out when the Tax Man actually turns up and pays out (Rule 2), not when it's cast. */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        Watch w = WATCHES.get(target.getUUID());
        if (w == null) {
            return java.util.Optional.of("payday pending");
        }
        return java.util.Optional.of(w.delivered ? "paid" : "delivery en route");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        WATCHES.put(target.getUUID(), new Watch(target.position()));
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        Watch watch = WATCHES.computeIfAbsent(target.getUUID(), k -> new Watch(target.position()));
        summonDelivery(target, watch);
        return "Payday: the Tax Man is on his way to pay you";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        Watch watch = WATCHES.computeIfAbsent(target.getUUID(), k -> new Watch(target.position()));

        // already sent him.
        if (watch.entityId != null) {
            TaxManEntity taxMan = find(target, watch.entityId);
            if (taxMan != null) {
                if (taxMan.hasPaidOut()) {
                    if (!watch.delivered) {
                        Blessings.PAYDAY.get().markDiscoveredByVictim(target); // discovered on the payout
                    }
                    watch.delivered = true; // remember it happened; he'll leave under his own steam
                }
                return;
            }
            // he's gone. If he actually paid out, the one use is spent; if he vanished first (a relog before
            // he arrived), forget him so a fresh one is sent when the moment's right again.
            if (watch.delivered) {
                EffectManager.remove(target, Blessings.PAYDAY);
            } else {
                watch.entityId = null;
            }
            return;
        }

        // track idleness: reset the moment they move, otherwise let it build.
        if (target.position().distanceTo(watch.lastPos) > MOVE_EPSILON) {
            watch.idleTicks = 0;
        } else {
            watch.idleTicks++;
        }
        watch.lastPos = target.position();

        if (watch.idleTicks >= Config.TAXMAN_IDLE_TICKS.get() && noHostilesNearby(target)) {
            summonDelivery(target, watch);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        Watch watch = WATCHES.remove(target.getUUID());
        if (watch != null && watch.entityId != null) {
            TaxManEntity taxMan = find(target, watch.entityId);
            if (taxMan != null && taxMan.isAlive() && !taxMan.hasPaidOut()) {
                taxMan.discard(); // cured before he arrived — no payout happened, bank untouched
            }
        }
    }

    private static boolean noHostilesNearby(ServerPlayer target) {
        double radius = Config.TAXMAN_SAFE_RADIUS.get();
        return target.serverLevel().getEntitiesOfClass(Mob.class,
                target.getBoundingBox().inflate(radius), m -> m instanceof Enemy).isEmpty();
    }

    private void summonDelivery(ServerPlayer recipient, Watch watch) {
        ServerLevel level = recipient.serverLevel();
        TaxManEntity taxMan = WitchModEntities.TAX_MAN.get().create(level);
        if (taxMan == null) {
            return;
        }
        Vec3 spot = walkInSpot(recipient);
        taxMan.moveTo(spot.x, spot.y, spot.z, recipient.getYRot() + 180.0F, 0.0F);
        taxMan.assignDelivery(recipient);
        level.addFreshEntity(taxMan);
        watch.entityId = taxMan.getUUID();
    }

    /** A few blocks out in front of the recipient, so he's seen walking in to make the delivery. */
    private static Vec3 walkInSpot(ServerPlayer recipient) {
        Vec3 forward = recipient.getLookAngle();
        Vec3 flat = new Vec3(forward.x, 0.0, forward.z).normalize().scale(6.0);
        return recipient.position().add(flat);
    }

    @Nullable
    private static TaxManEntity find(ServerPlayer anchor, UUID id) {
        return anchor.serverLevel().getEntity(id) instanceof TaxManEntity taxMan ? taxMan : null;
    }
}
