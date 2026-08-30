package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.CoinGamble;
import com.oliver.witchmod.data.Effect;

/**
 * Gambles a coin onto the USER (1–3 effects, per {@link CoinGamble} — Cursed / Blessed / Executioner's biases).
 * The same gamble runs when the coin is placed as a Sacrificial Item at the Bewitching Table, there hitting the
 * ritual's target instead. CLAUDE.md section 3/4.
 */
public final class ItemGambleCoin extends Item {
    private static final int DEFAULT_DURATION_TICKS = 45 * 60 * 20;

    private final CoinGamble.Type type;

    public ItemGambleCoin(Properties properties, CoinGamble.Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        // caster = null: a self-gamble isn't attributed to anyone (matches the coin being your own bad luck).
        List<Holder.Reference<Effect>> got = CoinGamble.gamble(serverPlayer, type, DEFAULT_DURATION_TICKS, null, serverPlayer.getRandom());
        if (got.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }
}
