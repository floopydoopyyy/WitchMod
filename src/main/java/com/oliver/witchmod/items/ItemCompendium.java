package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** the compendium — right-click opens its two-page book ui; purely client-side display, no networking or menu. */
public final class ItemCompendium extends Item {
    public ItemCompendium(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            com.oliver.witchmod.client.CompendiumScreen.open();
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.compendium.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.witchmod.compendium.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
