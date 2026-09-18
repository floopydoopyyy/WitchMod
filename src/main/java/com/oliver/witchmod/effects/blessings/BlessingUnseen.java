package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModMobEffects;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * slip out of sight. You're rendered to NOBODY — armour and
 * held items included — and mobs can't lock onto you, unless they're within {@code unseenRevealDistance}
 * (~6 blocks). Getting close is the obvious, deliberate counterplay.
 *
 * <p>The full-render hide + the cloak/uncloak puff as someone crosses the threshold are client-side (off the
 * synced {@link WitchModAttachments#UNSEEN_ACTIVE} flag; see {@code ClientCurseHandler.onRenderLiving}). The
 * mob-target veto is server-side ({@code BlessingEventHandler.onUnseenChangeTarget}). This class also gives
 * the hidden player their OWN feedback: a puff around them the moment they slip out of / back into someone's
 * view. Replaces the prototype's vanilla Invisibility (which left armour showing).
 */
public final class BlessingUnseen extends Effect {
    /** player -> were they visible to anyone last tick (for the self-feedback puff). */
    private static final Map<UUID, Boolean> VISIBLE = new HashMap<>();

    /** small black smoke — used for BOTH cloaking and uncloaking (scale 0.7 keeps it subtle). */
    public static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.03F, 0.03F, 0.03F), 0.7F);

    public BlessingUnseen() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.INK_SAC);
    }

    /** you find out the moment you first slip out of sight (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.UNSEEN_ACTIVE, 1);
        VISIBLE.put(target.getUUID(), true); // assume seen at first, so casting it fires the "cloak" feedback
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.UNSEEN_ACTIVE, -1);
        target.removeEffect(WitchModMobEffects.UNSEEN);
        VISIBLE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        double reveal = Config.UNSEEN_REVEAL_DISTANCE.get();
        double revealSqr = reveal * reveal;

        // you're "seen" if any PLAYER or MOB is within the reveal distance.
        boolean seenByPlayer = !level.getPlayers(p -> p != target && p.isAlive() && !p.isSpectator()
                && p.distanceToSqr(target) <= revealSqr).isEmpty();
        boolean seenByMob = !level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(reveal),
                m -> m.isAlive() && m.distanceToSqr(target) <= revealSqr).isEmpty();
        boolean seen = seenByPlayer || seenByMob;

        // shake off any mob that locked onto you up close but is now beyond the reveal distance — cloaking
        // again genuinely loses them, rather than them tracking you from standard range.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(48.0),
                m -> m.getTarget() == target)) {
            if (mob.distanceToSqr(target) > revealSqr) {
                mob.setTarget(null);
            }
        }

        // the icon (no swirls) is shown WHILE cloaked, so you always know your current stealth state.
        if (!seen) {
            target.addEffect(new MobEffectInstance(WitchModMobEffects.UNSEEN, 40, 0, true, false, true));
        } else {
            target.removeEffect(WitchModMobEffects.UNSEEN);
        }

        boolean prev = VISIBLE.getOrDefault(target.getUUID(), true);
        if (seen == prev) {
            return;
        }
        VISIBLE.put(target.getUUID(), seen);

        // clear cloak/uncloak feedback — subtle black smoke either way + the Dweller's wind, sped up, server-side
        // so every nearby client (and the hidden player) gets it.
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        if (seen) {
            level.sendParticles(BLACK, x, y, z, 14, 0.3, 0.45, 0.3, 0.0);
            level.playSound(null, target.blockPosition(), WitchModSounds.SPELL_FAIL.get(), SoundSource.PLAYERS, 0.36F, 1.6F);
        } else {
            level.sendParticles(BLACK, x, y, z, 10, 0.25, 0.4, 0.25, 0.0);
            level.playSound(null, target.blockPosition(), WitchModSounds.UNSEEN_CLOAK.get(), SoundSource.PLAYERS, 0.36F, 1.6F);
            markDiscoveredByVictim(target);
        }
    }
}
