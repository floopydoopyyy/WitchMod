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

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.entities.BodyguardEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * You've hired protection (master-spec Bodyguard). Casting it summons a {@link BodyguardEntity} bound to you
 * as its anchor; it trails you and runs off anyone who crowds you. This class owns the entity's LIFECYCLE:
 * summon on cast, keep it around, and clear it when the blessing ends. Its <b>death</b> breaks the blessing
 * instantly — handled in {@code BlessingEventHandler} on the entity's death, which then calls back into
 * {@code EffectManager.remove}, whose {@link #onRemove} discards the (already-dead) entity harmlessly.
 */
public final class BlessingBodyguard extends Effect {
    // The anchor -> bodyguard mapping lives in BodyguardEntity.CANONICAL, so the entity itself can self-heal
    // duplicates against it (see BodyguardEntity). This class just claims/reads/releases it.

    public BlessingBodyguard() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.BONE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        summon(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // Re-summon if the bodyguard has gone missing for a reason OTHER than death (an unexpected unload, or
        // the anchor changing dimension) — death removes the blessing outright, so onTick won't run after it.
        if (ticksRemaining % 40 == 0 && get(target) == null) {
            summon(target);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        BodyguardEntity bodyguard = get(target);
        if (bodyguard != null && bodyguard.isAlive()) {
            bodyguard.discard();
        }
        BodyguardEntity.release(target.getUUID());
    }

    /** The live bodyguard entity for {@code anchor}, or null if none is currently around. */
    @Nullable
    public static BodyguardEntity get(ServerPlayer anchor) {
        UUID id = BodyguardEntity.canonicalFor(anchor.getUUID());
        if (id == null) {
            return null;
        }
        return anchor.serverLevel().getEntity(id) instanceof BodyguardEntity bodyguard && bodyguard.isAlive()
                ? bodyguard : null;
    }

    /** Drop the tracking entry when the entity dies (called from the death hook). */
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

    /** Remove any bodyguard already bound to this anchor before summoning a fresh one — no duplicates. */
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

    /** Used by the death hook: which anchor (if any) does this entity belong to? */
    @Nullable
    public static UUID anchorOf(Entity entity) {
        return entity instanceof BodyguardEntity bodyguard ? bodyguard.getAnchorId() : null;
    }
}
