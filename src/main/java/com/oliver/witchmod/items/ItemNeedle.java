package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * right-click a needle with a bound doll held to jab the target ({@code witchmod:voodoo} damage, armour-scaling,
 * no knockback). spends the needle + some doll durability. can also be right-clicked onto the doll in the gui.
 */
public final class ItemNeedle extends Item {
    public ItemNeedle(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.needle.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack needle = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer caster)) {
            return InteractionResultHolder.success(needle);
        }

        ItemStack doll = ItemVoodooDoll.findBoundDoll(caster);
        if (doll.isEmpty()) {
            return InteractionResultHolder.fail(needle); // no doll — nothing happens
        }
        PlayerEssenceData bound = doll.get(WitchModDataComponents.BOUND_PLAYER);
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            ItemVoodooDoll.offlineFizzle(caster);
            return InteractionResultHolder.fail(needle);
        }

        // the jab (FX on the victim handled inside voodooHurt) + a camera jolt on the caster.
        ItemVoodooDoll.voodooHurt(target, caster, (float) (double) Config.VOODOO_NEEDLE_BASE_DAMAGE.get());
        ItemVoodooDoll.casterShake(caster);

        needle.shrink(1);
        doll.hurtAndBreak(Config.VOODOO_NEEDLE_DOLL_COST.get(), caster.serverLevel(), caster, item -> {});
        return InteractionResultHolder.success(needle);
    }
}
