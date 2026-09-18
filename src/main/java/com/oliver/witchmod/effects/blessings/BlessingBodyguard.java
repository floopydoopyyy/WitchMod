package com.oliver.witchmod.effects.blessings;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.entities.BodyguardEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * you've hired protection. Casting it summons a {@link BodyguardEntity} bound to you
 * as its anchor; it trails you and runs off anyone who crowds you. This class owns the entity's LIFECYCLE:
 * summon on cast, keep it around, and clear it when the blessing ends. Its <b>death</b> breaks the blessing
 * instantly — handled in {@code BlessingEventHandler} on the entity's death, which then calls back into
 * {@code EffectManager.remove}, whose {@link #onRemove} discards the (already-dead) entity harmlessly.
 */
public final class BlessingBodyguard extends Effect {
    // the anchor -> bodyguard mapping lives in BodyguardEntity.CANONICAL, so the entity itself can self-heal
    // duplicates against it (see BodyguardEntity). This class just claims/reads/releases it.

    public BlessingBodyguard() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.BONE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        summon(target);
    }

    /** anchor UUID -> game tick a hired replacement is due (set when the current bodyguard dies). */
    private static final java.util.Map<UUID, Long> RESPAWN_AT = new java.util.HashMap<>();

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        Long due = RESPAWN_AT.get(id);
        if (due != null) {
            // A replacement is on the way — wait it out, then hire a fresh one (with a line).
            if (target.level().getGameTime() >= due) {
                RESPAWN_AT.remove(id);
                summon(target);
                announce(target, "respawn");
            }
            return;
        }
        // re-summon if the bodyguard has gone missing for a reason OTHER than death (an unexpected unload, or
        // the anchor changing dimension).
        if (ticksRemaining % 40 == 0 && get(target) == null) {
            summon(target);
        }
    }

    /** called from the death hook: the bodyguard fell — schedule a replacement instead of losing the blessing. */
    public static void onBodyguardDeath(ServerPlayer anchor) {
        RESPAWN_AT.put(anchor.getUUID(), anchor.level().getGameTime() + Config.BODYGUARD_RESPAWN_TICKS.get());
        announce(anchor, "fell");
    }

    /** broadcast a Bodyguard line (from the given json key) to players near the anchor, in the entity's voice. */
    private static void announce(ServerPlayer anchor, String key) {
        if (!com.oliver.witchmod.data.BodyguardLines.has(key)) {
            return;
        }
        java.util.List<String> tree = com.oliver.witchmod.data.BodyguardLines.pickTree(key, anchor.getRandom());
        if (tree.isEmpty()) {
            return;
        }
        String name = anchor.getName().getString();
        double radius = Config.BODYGUARD_CHAT_RADIUS.get();
        for (String raw : tree) {
            net.minecraft.network.chat.Component msg =
                    net.minecraft.network.chat.Component.literal("<Bodyguard> " + raw.replace("{player}", name));
            for (ServerPlayer p : anchor.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                    anchor.getBoundingBox().inflate(radius))) {
                p.sendSystemMessage(msg);
            }
        }
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        BodyguardEntity bg = get(target);
        if (bg == null) {
            return java.util.Optional.of(RESPAWN_AT.containsKey(target.getUUID()) ? "replacement incoming" : "no guard present");
        }
        net.minecraft.world.entity.LivingEntity focus = bg.getTarget();
        return java.util.Optional.of(focus != null ? "on duty — marked " + focus.getName().getString() : "on duty");
    }

    @Override
    public void onRemove(ServerPlayer target) {
        BodyguardEntity bodyguard = get(target);
        if (bodyguard != null && bodyguard.isAlive()) {
            bodyguard.discard();
        }
        BodyguardEntity.release(target.getUUID());
        RESPAWN_AT.remove(target.getUUID());
    }

    /** the live bodyguard entity for {@code anchor}, or null if none is currently around. */
    @Nullable
    public static BodyguardEntity get(ServerPlayer anchor) {
        UUID id = BodyguardEntity.canonicalFor(anchor.getUUID());
        if (id == null) {
            return null;
        }
        return anchor.serverLevel().getEntity(id) instanceof BodyguardEntity bodyguard && bodyguard.isAlive()
                ? bodyguard : null;
    }

    /** drop the tracking entry when the entity dies (called from the death hook). */
    public static void forget(UUID anchorId) {
        BodyguardEntity.release(anchorId);
    }

    private void summon(ServerPlayer anchor) {
        ServerLevel level = anchor.serverLevel();
        discardExisting(level, anchor); // belt-and-braces against ever running two at once
        BodyguardEntity bodyguard = WitchModEntities.BODYGUARD.get().create(level);
        if (bodyguard == null) {
            return;
        }
        Vec3 spot = behind(anchor);
        bodyguard.moveTo(spot.x, spot.y, spot.z, anchor.getYRot(), 0.0F);
        bodyguard.setAnchor(anchor);
        level.addFreshEntity(bodyguard);
        BodyguardEntity.claim(anchor.getUUID(), bodyguard.getUUID()); // newest wins; older ones self-heal away
        level.playSound(null, anchor.blockPosition(), SoundEvents.SKELETON_AMBIENT, SoundSource.NEUTRAL, 0.8F, 0.7F);
    }

    /** remove any bodyguard already bound to this anchor before summoning a fresh one — no duplicates. */
    private static void discardExisting(ServerLevel level, ServerPlayer anchor) {
        UUID anchorId = anchor.getUUID();
        for (BodyguardEntity existing : level.getEntitiesOfClass(BodyguardEntity.class,
                anchor.getBoundingBox().inflate(96.0), b -> anchorId.equals(b.getAnchorId()))) {
            existing.discard();
        }
    }

    /** A couple of blocks behind the anchor, so it appears at their shoulder. */
    private static Vec3 behind(ServerPlayer anchor) {
        Vec3 back = anchor.getLookAngle().scale(-2.0);
        return anchor.position().add(back.x, 0.0, back.z);
    }

    /** used by the death hook: which anchor (if any) does this entity belong to? */
    @Nullable
    public static UUID anchorOf(Entity entity) {
        return entity instanceof BodyguardEntity bodyguard ? bodyguard.getAnchorId() : null;
    }
}
