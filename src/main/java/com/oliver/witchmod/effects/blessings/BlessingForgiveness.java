package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Forgiveness (sacrificial item NAME TAG): entity hitboxes are effectively ~40% bigger — but ONLY for YOU.
 * Near-misses land: a melee swing that just grazed a mob connects anyway (client-side enlarged pick on a
 * miss, see {@code client/ClientCurseHandler}), and YOUR projectiles curve onto an entity whose enlarged box
 * they were about to pass through (server-side, in {@code ProjectileBlessingHandler}). Nobody else's aim is
 * affected — it's your generosity, not theirs. Especially forgiving on tiny/baby mobs.
 *
 * <p>This class just owns the synced {@link WitchModAttachments#FORGIVENESS_ACTIVE} flag; the two assists read
 * it (the client one needs a synced flag since it can't see {@code ACTIVE_EFFECTS}).
 */
public final class BlessingForgiveness extends Effect {
    public BlessingForgiveness() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.NAME_TAG);
    }

    /** Not instantly noticeable — you discover it the first time a shot curves onto a near-miss (see the projectile handler). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.FORGIVENESS_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.FORGIVENESS_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.FORGIVENESS_ACTIVE) != 1) {
            target.setData(WitchModAttachments.FORGIVENESS_ACTIVE, 1); // self-heal after relog
        }
    }
}
