package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Blessings;

/**
 * heavy Hitter (Mace): your melee knockback is doubled. It multiplies the FINAL knockback strength — which
 * already includes any Knockback-enchantment contribution — so it stacks MULTIPLICATIVELY with Knockback
 * (e.g. Knockback II + Heavy Hitter sends them properly flying).
 *
 * <p>Same mark-then-boost pattern as Backstabbing: the melee hit (which knows the attacker) marks the victim
 * for this tick, and the {@code LivingKnockBackEvent} hook (which doesn't know the attacker) reads that mark.
 */
public final class BlessingHeavyHitter extends Effect {
    /** entityId -> game tick a Heavy-Hitter hit landed on it, so the same-tick knockback hook boosts it. */
    private static final Map<Integer, Long> KNOCKBACK_MARK = new HashMap<>();

    public BlessingHeavyHitter() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.MACE);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** the blessed player just landed a melee hit — mark the victim so the knockback hook doubles it this tick. */
    public static void onMeleeHit(ServerPlayer attacker, LivingEntity victim) {
        KNOCKBACK_MARK.put(victim.getId(), victim.level().getGameTime());
        victim.level().playSound(null, victim.blockPosition(),
                com.oliver.witchmod.data.WitchModSounds.BIG_HIT.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F); // the heavy-hit sting on top of the normal hit
        Blessings.HEAVY_HITTER.get().markDiscoveredByVictim(attacker);
    }

    /**
     * @return the knockback multiplier for a victim hit by a Heavy Hitter this tick (else 1.0). A melee hit
     * fires TWO knockback events (the base 0.4 from {@code hurt}, then the enchant-inclusive one from
     * {@code Player.attack}), so this must double BOTH — hence it matches by tick rather than removing on the
     * first use; the stale mark is cleaned up on the next query for that victim.
     */
    public static float knockbackMultiplier(LivingEntity victim) {
        Long tick = KNOCKBACK_MARK.get(victim.getId());
        if (tick == null) {
            return 1.0F;
        }
        if (tick == victim.level().getGameTime()) {
            return Config.HEAVY_HITTER_KNOCKBACK_MULT.get().floatValue();
        }
        KNOCKBACK_MARK.remove(victim.getId()); // stale (a later tick) — clean it up
        return 1.0F;
    }
}
