package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModDamageTypes;

/**
 * You never go outside, and daylight makes that very clear (master-spec Basement Dweller). Standing in direct
 * sunlight burns you — a hat takes the edge off but never fully protects you.
 *
 * <p>A hat doesn't SOFTEN the burns, it SLOWS them: wearing anything on your head multiplies the gap between
 * burns, so you cook more slowly but never stop cooking. Tracking the next-burn tick per player (rather than
 * a fixed modulo) is what lets that interval change cleanly as a hat comes on or off.
 *
 * <p>Uses the mod's own {@code witchmod:sunburn} damage type: fatal on every difficulty, bypasses armour,
 * and carries no knockback (it's a slow cook, not a shove). "Direct sunlight" = daytime, clear sky access
 * (not raining/thundering over your head). Each burn hisses so it's clear where the damage is coming from.
 * Discovered on the first burn.
 */
public final class CurseBasementDweller extends Effect {
    /** victim -> game tick the next burn is due. */
    private static final Map<UUID, Long> NEXT_BURN = new HashMap<>();

    public CurseBasementDweller() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 40, () -> Items.GRASS_BLOCK);
    }

    /** You find out the first time the sun bites (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_BURN.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        UUID id = target.getUUID();

        // Genuinely IN the sun: daytime, sky visible straight up, and not raining on this spot.
        boolean inSun = level.isDay()
                && level.canSeeSky(target.blockPosition())
                && !level.isRainingAt(target.blockPosition().above());
        if (!inSun) {
            NEXT_BURN.remove(id); // in the shade the clock stops; stepping back out starts a fresh interval
            return;
        }

        long now = level.getGameTime();
        long next = NEXT_BURN.computeIfAbsent(id, k -> now); // first tick in the sun burns immediately
        if (now < next) {
            return;
        }

        boolean hatted = !target.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        long interval = Config.BASEMENT_DAMAGE_INTERVAL.get();
        if (hatted) {
            interval = Math.round(interval * Config.BASEMENT_HELMET_INTERVAL_MULT.get()); // a hat buys time
        }
        NEXT_BURN.put(id, now + interval);

        double damage = Config.BASEMENT_DAMAGE.get();
        if (damage <= 0.0) {
            return;
        }
        // A quiet sizzle at the victim so the source of the damage is obvious, to them and anyone nearby.
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 0.4F, 1.6F + target.getRandom().nextFloat() * 0.2F);
        if (target.hurt(WitchModDamageTypes.sunburn(level), (float) damage)) {
            markDiscoveredByVictim(target);
        }
    }
}
