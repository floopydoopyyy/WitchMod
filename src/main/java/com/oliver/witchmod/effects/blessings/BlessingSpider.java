package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * wall-crawler (sacrificial item FERMENTED SPIDER EYE — Spider Eye is taken by Neutral Aggression): you climb
 * walls like a spider, but faster. Push into a wall to scale it, or sneak against one to cling.
 *
 * <p>Movement is client-authoritative, so the climbing lives in {@code client/ClientCurseHandler} off the
 * synced {@link WitchModAttachments#SPIDER_ACTIVE} flag; the server just flips it. Discovered on apply.
 */
public final class BlessingSpider extends Effect {
    public BlessingSpider() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.FERMENTED_SPIDER_EYE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SPIDER_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.SPIDER_ACTIVE) != 1) {
            target.setData(WitchModAttachments.SPIDER_ACTIVE, 1); // self-heal after respawn/relog
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.SPIDER_ACTIVE, -1);
    }
}
