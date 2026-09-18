package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.CoinGamble;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.SpellSequences;

/**
 * gambles a coin onto the user (1–3 effects, per {@link CoinGamble}); the same gamble runs when the coin is
 * cast at the table, hitting the target instead.
 */
public final class ItemGambleCoin extends Item {
    private static final int DEFAULT_DURATION_TICKS = 45 * 60 * 20;

    private final CoinGamble.Type type;

    public ItemGambleCoin(Properties properties, CoinGamble.Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // per-coin one-liner: item.witchmod.<coin>.tip
        tooltip.add(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        // roll FIRST (so we know how many + which categories to break into), then run the short "coin splits
        // into its attachments" sequence, which APPLIES them a beat later (SpellSequences.coin). caster = null:
        // a self-gamble isn't attributed to anyone (it's your own bad luck).
        List<Holder.Reference<Effect>> got = CoinGamble.roll(type, serverPlayer.getRandom());
        if (got.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        SpellSequences.coin(serverPlayer, got, DEFAULT_DURATION_TICKS);
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }
}
