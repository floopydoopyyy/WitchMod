package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * you see in the dark, and nothing dims your view.
 * Every visual obstruction is switched off:
 * <ul>
 *   <li><b>No fog</b> anywhere — distance, water and lava fog are all stripped (client, off the synced flag).</li>
 *   <li><b>Full-bright</b> — gamma is forced to a full-bright value, so unlit caves are as clear as daylight
 *       (client).</li>
 *   <li><b>Immune to darkening effects</b> — Blindness and Darkness simply can't be applied to you, and are
 *       stripped if something already did (server; see the hook below).</li>
 * </ul>
 * The old prototype's night-time speed burst is dropped — the spec is about vision, not movement.
 */
public final class BlessingNightowl extends Effect {
    public BlessingNightowl() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.GLOW_BERRIES);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.NIGHTOWL_ACTIVE, 1);
        stripDarkening(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.NIGHTOWL_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // backstop: clear any Blindness/Darkness that slipped in (the Applicable veto is the primary guard).
        if (target.hasEffect(MobEffects.BLINDNESS) || target.hasEffect(MobEffects.DARKNESS)) {
            stripDarkening(target);
        }
    }

    private static void stripDarkening(ServerPlayer target) {
        target.removeEffect(MobEffects.BLINDNESS);
        target.removeEffect(MobEffects.DARKNESS);
    }

    /** blindness and darkness can't be applied to you. */
    @Override
    public void onMobEffect(ServerPlayer self, MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        if (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
