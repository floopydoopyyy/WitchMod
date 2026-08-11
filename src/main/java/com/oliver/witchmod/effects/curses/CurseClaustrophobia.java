package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModDamageTypes;

/**
 * The walls are too close (master-spec Claustrophobia, NEW — not prototyped). Being shut indoors, with no
 * sky above you, wears at you. Deliberately the gentler mirror of Basement Dweller: less damage, no hat
 * mitigation (a helmet doesn't help with the ceiling), and it bites whenever you can't see the sky —
 * night included, since the problem is the roof, not the sun.
 *
 * <p>Uses the mod's own {@code witchmod:cave_dread} damage type: fatal on every difficulty, bypasses armour,
 * no knockback. Discovered on the first bite.
 */
public final class CurseClaustrophobia extends Effect {
    public CurseClaustrophobia() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.DEEPSLATE); // moved off Cobbled Deepslate → freed it for Tank (still cave-thematic)
    }

    /** You find out the first time the walls close in (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.CLAUSTRO_DAMAGE_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        if (level.canSeeSky(target.blockPosition())) {
            return; // open air (or under the night sky) is fine — it's being boxed in that hurts
        }
        double damage = Config.CLAUSTRO_DAMAGE.get();
        if (damage <= 0.0) {
            return;
        }
        // A faint warden heartbeat as the walls press in — different from Basement Dweller's sizzle, and a
        // clear cue where the dread is coming from.
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5F, 1.1F);
        if (target.hurt(WitchModDamageTypes.caveDread(level), (float) damage)) {
            markDiscoveredByVictim(target);
        }
    }
}
