package com.oliver.witchmod.effects.blessings;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Sanguine (sacrificial item RED DYE): a vampiric bargain. Your natural regeneration is throttled to
 * {@code sanguineRegenMultiplier} (20%) of normal — you don't heal by resting — but you LIFESTEAL
 * {@code sanguineLifesteal} (30%) of ALL damage you deal, melee AND projectile. Bleed your enemies to keep
 * yourself standing. The two halves live in {@code BlessingEventHandler} (heal + damage events).
 */
public final class BlessingSanguine extends Effect {
    public BlessingSanguine() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.RED_DYE);
    }

    /** Not instantly noticeable — you discover it the first time you drink someone's blood (first lifesteal). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Natural regen throttle — small heals (vanilla regen is ~1 HP/event) are cut to a fraction. */
    public static float throttleRegen(float amount) {
        return amount <= 1.5F ? amount * (float) (double) Config.SANGUINE_REGEN_MULT.get() : amount;
    }

    /** Heal the attacker for a share of the damage they just dealt (melee or projectile), with a cool blood-draw. */
    public static void lifesteal(ServerPlayer attacker, LivingEntity victim, float damageDealt) {
        if (damageDealt <= 0.0F || attacker.getHealth() >= attacker.getMaxHealth()) {
            return;
        }
        float heal = damageDealt * (float) (double) Config.SANGUINE_LIFESTEAL.get();
        attacker.heal(heal);
        ServerLevel level = attacker.serverLevel();
        DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.72F, 0.0F, 0.05F), 1.2F);

        // A stream of blood drawn FROM the victim INTO you.
        Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        Vec3 to = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
        int steps = 9;
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(blood, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        // The wound on the victim...
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getEyeY(), victim.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
        level.sendParticles(blood, victim.getX(), victim.getEyeY(), victim.getZ(), 12, 0.3, 0.3, 0.3, 0.12);
        // ...and the life you gained.
        level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getEyeY() + 0.3, attacker.getZ(), 3, 0.3, 0.3, 0.3, 0.0);

        // A subtle, positive "restored" chime — not the old glug.
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.35F);
        com.oliver.witchmod.effects.Blessings.SANGUINE.get().markDiscoveredByVictim(attacker);
    }
}
