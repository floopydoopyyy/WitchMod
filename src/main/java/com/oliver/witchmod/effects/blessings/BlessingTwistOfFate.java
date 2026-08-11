package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Fortune's on your side (master-spec Twist of Fate, sacrificial item GHAST TEAR — moved here from Nether Star
 * when Immortality took that item). Every incoming hit has a small chance to <b>simply not happen</b>: the
 * damage is cancelled, a little burst of enchant/end-rod particles and a subtle chime mark the near-miss, and
 * it then goes on cooldown so it can't just no-sell a whole fight.
 *
 * <p>The roll + cancel is driven from {@code BlessingEventHandler.onIncomingDamage} via {@link #tryNegate}.
 * The old prototype's slow-falling cushion is dropped.
 */
public final class BlessingTwistOfFate extends Effect {
    /** player -> game tick after which it can trigger again. */
    private static final Map<UUID, Long> NEXT_ALLOWED = new HashMap<>();

    public BlessingTwistOfFate() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.GHAST_TEAR);
    }

    /** You find out the first time a hit that should have landed simply... doesn't (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_ALLOWED.remove(target.getUUID());
    }

    /** @return true if this incoming hit should be negated (rolls the chance; respects/sets the cooldown). */
    public static boolean tryNegate(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (now < NEXT_ALLOWED.getOrDefault(player.getUUID(), 0L)) {
            return false; // still on cooldown
        }
        if (player.getRandom().nextDouble() >= Config.TWIST_NEGATE_CHANCE.get()) {
            return false;
        }
        NEXT_ALLOWED.put(player.getUUID(), now + Config.TWIST_COOLDOWN_TICKS.get());

        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.6;
        double z = player.getZ();
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 25, 0.5, 0.7, 0.5, 0.2);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.5F, 1.6F);
        return true;
    }
}
