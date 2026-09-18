package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * you just never seem to get hungry. Hunger — and the hidden saturation buffer
 * behind it — drains at {@code fullnessDrainRate} of the normal rate, so you rarely have to eat.
 *
 * <p><b>There's no event to scale hunger drain, so this WATCHES the outputs.</b> {@code onTick} runs on
 * {@code PlayerTickEvent.Post}, i.e. AFTER vanilla's {@link FoodData#tick} has applied this tick's decrement
 * — and vanilla only ever drops ONE thing per tick (a point of saturation, or, once that's gone, a point of
 * food). So each tick we look at what actually went down and hand most of it back:
 * <ul>
 *   <li><b>Saturation</b> is a float, so we simply refund {@code (1 - rate)} of any natural drop.</li>
 *   <li><b>Food</b> is a whole number, so we undo the drop and bank it; only once enough drops have banked
 *       ({@code 1/rate} of them) do we let a single real point through. That yields the same rate exactly.</li>
 * </ul>
 * Increases (from eating) are positive deltas and are left completely alone. Discovered the first time a
 * natural drain is slowed.
 */
public final class BlessingFullness extends Effect {
    /** per-player last-seen food/saturation plus the banked fractional food debt. */
    private record Snapshot(int food, float saturation, float foodDebt) {}

    private static final Map<UUID, Snapshot> STATE = new HashMap<>();

    public BlessingFullness() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.BREAD);
    }

    /** you notice it the first time your hunger goes down slower than it should. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        FoodData food = target.getFoodData();
        STATE.put(target.getUUID(), new Snapshot(food.getFoodLevel(), food.getSaturationLevel(), 0.0F));
    }

    @Override
    public void onRemove(ServerPlayer target) {
        STATE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        FoodData fd = target.getFoodData();
        int food = fd.getFoodLevel();
        float sat = fd.getSaturationLevel();

        Snapshot prev = STATE.get(id);
        if (prev == null) {
            // missing (e.g. after a relog while the blessing persisted) — re-seed and wait a tick.
            STATE.put(id, new Snapshot(food, sat, 0.0F));
            return;
        }

        double rate = Config.FULLNESS_DRAIN_RATE.get();
        float debt = prev.foodDebt();
        boolean slowed = false;

        // saturation drained naturally this tick? Refund most of it (kept no higher than the food bar).
        if (sat < prev.saturation()) {
            float drop = prev.saturation() - sat;
            sat = Math.min(food, sat + drop * (float) (1.0 - rate));
            fd.setSaturation(sat);
            slowed = true;
        }

        // food bar dropped (saturation was empty)? Undo it, bank the drop, release one point per 1/rate banked.
        if (food < prev.food()) {
            int drop = prev.food() - food;
            food = prev.food();
            debt += drop;
            float threshold = rate > 0.0 ? (float) (1.0 / rate) : Float.MAX_VALUE;
            while (debt >= threshold && food > 0) {
                food -= 1;
                debt -= threshold;
            }
            fd.setFoodLevel(food);
            slowed = true;
        }

        if (slowed) {
            markDiscoveredByVictim(target);
        }
        STATE.put(id, new Snapshot(food, sat, debt));
    }
}
