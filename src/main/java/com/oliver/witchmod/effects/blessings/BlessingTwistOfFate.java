package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * fortune's on your side (Twist of Fate, END CRYSTAL). An incoming hit has a chance to simply not happen —
 * cancelled, with a clear dodge flourish and a brief window of invulnerability. Bad-luck protection: the odds
 * climb per hit that doesn't dodge and reset on one that does. A brief cooldown follows each dodge.
 */
public final class BlessingTwistOfFate extends Effect {
    private static final Map<UUID, Long> NEXT_ALLOWED = new HashMap<>();
    private static final Map<UUID, Double> PITY = new HashMap<>();
    private static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.25F), 1.2F);

    public BlessingTwistOfFate() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.END_CRYSTAL);
    }

    @Override
    public Optional<String> scryingDetail(ServerPlayer target) {
        long now = target.level().getGameTime();
        if (now < NEXT_ALLOWED.getOrDefault(target.getUUID(), 0L)) {
            return Optional.of("dodge on cooldown");
        }
        double chance = Math.min(Config.TWIST_MAX_CHANCE.get(),
                Config.TWIST_NEGATE_CHANCE.get() + PITY.getOrDefault(target.getUUID(), 0.0));
        return Optional.of("dodge chance " + Math.round(chance * 100) + "%");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_ALLOWED.remove(target.getUUID());
        PITY.remove(target.getUUID());
    }

    /** life synergy: a resurrect readies the dodge again — clears the cooldown and maxes the pity. */
    public static void refreshOnRevive(ServerPlayer player) {
        NEXT_ALLOWED.remove(player.getUUID());
        PITY.put(player.getUUID(), Config.TWIST_MAX_CHANCE.get());
    }

    /** @return true if this hit is dodged. Rolls base+pity (off cooldown); resets pity + sets cooldown on a dodge. */
    public static boolean tryNegate(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = player.serverLevel().getGameTime();
        if (now < NEXT_ALLOWED.getOrDefault(id, 0L)) {
            return false;
        }
        double chance = Math.min(Config.TWIST_MAX_CHANCE.get(),
                Config.TWIST_NEGATE_CHANCE.get() + PITY.getOrDefault(id, 0.0));
        if (player.getRandom().nextDouble() >= chance) {
            PITY.merge(id, Config.TWIST_PITY_INCREMENT.get(), Double::sum);
            return false;
        }
        PITY.put(id, 0.0);
        NEXT_ALLOWED.put(id, now + Config.TWIST_COOLDOWN_TICKS.get());
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.TWIST_INVULN_TICKS.get());

        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.6;
        double z = player.getZ();
        level.sendParticles(YELLOW, x, y, z, 12, 0.5, 0.6, 0.5, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 10, 0.5, 0.6, 0.5, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 5, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, player.blockPosition(), WitchModSounds.BLESSED.get(), SoundSource.PLAYERS, 0.7F, 1.3F);
        return true;
    }
}
