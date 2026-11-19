package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import com.oliver.witchmod.Config;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.Tags;

/**
 * ore just gives you more. Breaking an ore-tag block yields
 * a few EXTRA drops on top of whatever it would normally give — and it's <b>additive</b>, applied after the
 * item's own enchantment Fortune has already rolled, so the two stack without multiplying into absurdity.
 * You get 0–{@code fortuneExtraMax} extra, biased toward {@code fortuneExtraMode} (usually one).
 *
 * <p>The actual drop-boosting lives in the hooks below on {@code BlockDropsEvent} — the
 * one place the finished drop list is in hand. This class only declares the blessing and defers discovery to
 * the first time it actually pays out.
 */
public final class BlessingFortune extends Effect {
    public BlessingFortune() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.DIAMOND);
    }

    /** you notice it the first time an ore hands you more than it should. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /**
     * an ore hands the breaker a few extra drops, on top of enchantment fortune (this fires with the rolled drop
     * list in hand). a silk-touched drop of the ore block itself is left alone, so it can't be duplicated.
     */
    @Override
    public void onBlockDrops(ServerPlayer self, BlockDropsEvent event) {
        if (!event.getState().is(Tags.Blocks.ORES)) {
            return;
        }
        List<ItemEntity> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }
        int extra = rollExtraDrops(self.getRandom());
        if (extra <= 0) {
            return;
        }
        Item blockItem = event.getState().getBlock().asItem();
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || stack.is(blockItem)) {
                continue; // skip a silk-touched ore-block drop: only the resource is boosted
            }
            stack.grow(extra);
            markDiscoveredByVictim(self);
            return;
        }
    }

    /** 0..max extra drops, peaked at {@code fortuneExtraMode} (a triangular roll). */
    private static int rollExtraDrops(RandomSource random) {
        int min = Config.FORTUNE_EXTRA_MIN.get();
        int max = Config.FORTUNE_EXTRA_MAX.get();
        if (max <= min) {
            return Math.max(0, min);
        }
        int mode = Mth.clamp(Config.FORTUNE_EXTRA_MODE.get(), min, max);
        double range = max - min;
        double c = (mode - min) / range;
        double u = random.nextDouble();
        double x = u < c
                ? min + Math.sqrt(u * range * (mode - min))
                : max - Math.sqrt((1.0 - u) * range * (max - mode));
        return (int) Math.round(x);
    }
}
