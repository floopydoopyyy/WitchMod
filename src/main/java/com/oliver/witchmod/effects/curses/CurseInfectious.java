package com.oliver.witchmod.effects.curses;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import com.oliver.witchmod.data.EffectManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * the hidden "infectious" state applied by the <b>Slime Ball modifier</b> (not cast directly — {@link #selectable()}
 * is false). While a player carries it, their attachments become a HOT POTATO: hitting another player transfers
 * ALL of their attachments — this one included — onto the victim with their timers preserved (see
 * the hook below). Lasts ~1 hour, or until it's passed on. Deliberately never
 * discovered ({@link #discoversOnTrigger()} true but it never marks), so it stays a hidden attachment.
 */
public final class CurseInfectious extends Effect {
    public CurseInfectious() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.SLIME_BALL);
    }

    @Override
    public boolean selectable() {
        return false;
    }

    @Override
    public boolean discoversOnTrigger() {
        return true; // never actually marked — a hidden attachment
    }

    /**
     * a hot potato: hitting another player moves all your attachments (this state included) onto them with
     * timers preserved, and leaves you clean.
     */
    @Override
    public void onAttack(ServerPlayer self, Entity target, AttackEntityEvent event) {
        if (!(target instanceof ServerPlayer victim) || victim == self) {
            return;
        }
        for (var entry : EffectManager.activeSnapshot(self).entrySet()) {
            EffectManager.holderOf(entry.getKey())
                    .ifPresent(h -> EffectManager.applyExact(victim, h, entry.getValue(), self));
        }
        EffectManager.removeAll(self, null); // you've passed the potato: nothing left on you
        splat(victim);
    }

    /** the slimy hit feedback, shared with very infectious. */
    static void splat(ServerPlayer victim) {
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.7F, 1.0F);
        victim.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME,
                victim.getX(), victim.getY() + 1.0, victim.getZ(), 16, 0.3, 0.6, 0.3, 0.0);
    }
}
