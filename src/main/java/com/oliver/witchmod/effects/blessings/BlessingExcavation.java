package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The more you dig, the faster you dig (master-spec Excavation, sacrificial item IRON PICKAXE). Constantly
 * breaking blocks builds a mining-speed bonus that ramps and ramps ({@code excavationRampPerBlock} per block,
 * up to {@code excavationMaxBonus}) — great for mindlessly clearing out big areas, where it gets really fast.
 * Stop for {@code excavationDecayGraceTicks} and it bleeds back down.
 *
 * <p><b>Not the Haste effect</b> — no icon, no vanilla particles. It works the same WAY (multiplying your break
 * speed) via {@code PlayerEvent.BreakSpeed} in {@code BlessingEventHandler}. The bonus lives on the synced
 * {@link WitchModAttachments#EXCAVATION_BONUS} attachment because break speed is computed CLIENT-side (mining
 * is client-authoritative), so the client needs the value to actually dig faster; the server drives it.
 */
public final class BlessingExcavation extends Effect {
    /** player -> game tick of their last block break, for the decay grace (server-only). */
    private static final Map<UUID, Long> LAST_MINE = new HashMap<>();

    public BlessingExcavation() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.IRON_PICKAXE);
    }

    /** You find out the first time the digging starts to snowball (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.EXCAVATION_BONUS, 0.0F);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.EXCAVATION_BONUS, -1.0F);
        LAST_MINE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        float bonus = target.getData(WitchModAttachments.EXCAVATION_BONUS);
        if (bonus <= 0.0F) {
            return;
        }
        long idle = target.serverLevel().getGameTime() - LAST_MINE.getOrDefault(target.getUUID(), 0L);
        if (idle > Config.EXCAVATION_DECAY_GRACE.get()) {
            target.setData(WitchModAttachments.EXCAVATION_BONUS,
                    Math.max(0.0F, bonus - Config.EXCAVATION_DECAY_PER_TICK.get().floatValue()));
        }
    }

    /** Called when an Excavation player breaks a block — ramps the bonus up and marks the mine time. */
    public void onBlockBroken(ServerPlayer player) {
        float bonus = Math.min(Config.EXCAVATION_MAX_BONUS.get().floatValue(),
                Math.max(0.0F, player.getData(WitchModAttachments.EXCAVATION_BONUS))
                        + Config.EXCAVATION_RAMP_PER_BLOCK.get().floatValue());
        player.setData(WitchModAttachments.EXCAVATION_BONUS, bonus);
        LAST_MINE.put(player.getUUID(), player.serverLevel().getGameTime());
        markDiscoveredByVictim(player);
    }
}
