package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Blessings;

/**
 * assassin's edge (sacrificial item NETHER BRICK — Echo Shard was requested but is taken by Echoes): a melee
 * hit landed while you're BEHIND the target does {@code backstabDamageMultiplier}× (multiplying the final
 * damage, so it stacks with crits/enchants) with reduced knockback, and a sharp riposte ring.
 *
 * <p>Because a mob's AI keeps it facing you, the rear arc is deliberately far MORE generous for mobs
 * ({@code backstabMobDot}) than for players ({@code backstabPlayerDot}), so backstabbing something in a fight
 * is actually achievable.
 */
public final class BlessingBackstabbing extends Effect {
    /** entityId -> game tick a backstab landed on it, so the knockback hook (same tick) knows to soften it. */
    private static final Map<Integer, Long> KNOCKBACK_MARK = new HashMap<>();

    public BlessingBackstabbing() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 40, () -> Items.NETHER_BRICK);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** @return the damage multiplier to apply (1.0 = not a backstab). Also arms the knockback softener + FX. */
    public static float onMeleeHit(ServerPlayer attacker, LivingEntity victim) {
        if (!isBehind(attacker, victim)) {
            return 1.0F;
        }
        KNOCKBACK_MARK.put(victim.getId(), victim.level().getGameTime());
        victim.level().playSound(null, victim.blockPosition(), WitchModSounds.GLADIATOR_RIPOSTE.get(),
                SoundSource.PLAYERS, 0.8F, Config.BACKSTAB_SOUND_PITCH.get().floatValue());
        Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        ((net.minecraft.server.level.ServerLevel) victim.level()).sendParticles(
                net.minecraft.core.particles.ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.3);
        Blessings.BACKSTABBING.get().markDiscoveredByVictim(attacker);
        return Config.BACKSTAB_DAMAGE_MULT.get().floatValue();
    }

    /** @return the knockback multiplier for a victim that was just backstabbed this tick (else 1.0). */
    public static float knockbackMultiplier(LivingEntity victim) {
        Long tick = KNOCKBACK_MARK.remove(victim.getId());
        if (tick != null && tick == victim.level().getGameTime()) {
            return Config.BACKSTAB_KNOCKBACK_MULT.get().floatValue();
        }
        return 1.0F;
    }

    /** is the attacker in the target's rear arc? More generous for mobs than players. */
    private static boolean isBehind(ServerPlayer attacker, LivingEntity victim) {
        float yaw = victim instanceof Mob mob ? mob.yBodyRot : victim.getYRot();
        Vec3 facing = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 toAttacker = attacker.position().subtract(victim.position());
        Vec3 flat = new Vec3(toAttacker.x, 0, toAttacker.z);
        if (flat.lengthSqr() < 1.0e-4) {
            return false;
        }
        double dot = facing.dot(flat.normalize());
        double threshold = victim instanceof Player ? Config.BACKSTAB_PLAYER_DOT.get() : Config.BACKSTAB_MOB_DOT.get();
        return dot <= threshold;
    }
}
