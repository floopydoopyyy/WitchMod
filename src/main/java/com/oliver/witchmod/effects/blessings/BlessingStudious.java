package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectState;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.UUID;

/**
 * A quick study: all XP you take in is multiplied by
 * {@code studiousXpMultiplier}. Applied in the hook below on
 * {@code PlayerXpEvent.PickupXp} — it boosts the collected orb's value BEFORE the mending/XP split, so every
 * real XP source (mobs, mining, furnaces, fishing, breeding, bottles o' enchanting) is multiplied reliably even
 * through Mending gear. (Hooking {@code XpChange} instead silently missed all XP that Mending consumed before it
 * ever became XP.) Spending XP is untouched. The old prototype's fixed trickle is dropped.
 *
 * <p><b>Item moved Enchanted Book → Book</b> (its spec item), resolving the §11 Book clash: the retired
 * Mansplainer curse moves off Book onto its own spec item Written Book, freeing Book here.
 */
public final class BlessingStudious extends Effect {
    public BlessingStudious() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.BOOK);
    }

    /** you find out the first time XP pours in faster than it should (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** last game tick the multiply fx played, per player, to rate-limit the chime across orb piles. */
    private static final Map<UUID, Long> FX_LAST = EffectState.session("studious/fx_last");

    /**
     * all xp you take in is multiplied. hooked on pickup (the instant an orb is collected, before the mending
     * split) and boosts the orb's own value, so every real xp source is multiplied, even through mending gear.
     */
    @Override
    public void onXpPickup(ServerPlayer self, PlayerXpEvent.PickupXp event) {
        net.minecraft.world.entity.ExperienceOrb orb = event.getOrb();
        int before = orb.value;
        int boosted = (int) Math.round(before * Config.STUDIOUS_XP_MULT.get());
        if (boosted <= before) {
            return;
        }
        orb.value = boosted;
        markDiscoveredByVictim(self);
        // an amethyst chime + subtle green motes, rate-limited: a pile of orbs fires this many times a tick
        long now = self.serverLevel().getGameTime();
        if (now - FX_LAST.getOrDefault(self.getUUID(), Long.MIN_VALUE / 2) >= 6L) {
            FX_LAST.put(self.getUUID(), now);
            ServerLevel level = self.serverLevel();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, self.getX(),
                    self.getY() + self.getBbHeight() * 0.6, self.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
            level.playSound(null, self.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
    }
}
