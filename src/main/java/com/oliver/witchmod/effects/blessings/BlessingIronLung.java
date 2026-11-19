package com.oliver.witchmod.effects.blessings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * you breathe anywhere: underwater AND buried in blocks. Done
 * with the mod's own lung-work rather than a vanilla Water Breathing effect —
 * <ul>
 *   <li><b>Underwater:</b> the air supply is topped back up every tick, so the bubble bar never drains and
 *       you never start drowning in the first place.</li>
 *   <li><b>In blocks:</b> suffocation (`in_wall`) damage is cancelled — and drowning too, as a backstop for
 *       the odd tick where the air top-up hasn't run yet — in the hooks below.</li>
 * </ul>
 * No status effect, so it can't be milked off and doesn't clutter the effect bar; the mod owns the behaviour.
 */
public final class BlessingIronLung extends Effect {
    public BlessingIronLung() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.KELP);
    }

    /** you find out the first time your lungs hold where they shouldn't — underwater or buried (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // runs on PlayerTickEvent.Post — after vanilla has decremented air this tick — so resetting it to
        // full here keeps it pinned there: full lungs, no depletion, no drowning.
        if (target.getAirSupply() < target.getMaxAirSupply()) {
            target.setAirSupply(target.getMaxAirSupply());
            Blessings.IRON_LUNG.get().markDiscoveredByVictim(target); // discovered on the first breath saved
        }
    }

    /** breathe in blocks: suffocation is negated (and drowning, backing up water breathing). */
    @Override
    public void onHurt(ServerPlayer self, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.DROWN)) {
            event.setCanceled(true);
            markDiscoveredByVictim(self); // discovered on the first breath saved
        }
    }
}
