package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * player essence — a bottled trace of a player, used to target the table's rituals. bound to a uuid, which
 * also picks a fixed essence "colour" via the item model. tooltip names the target (offline-aware) and shows
 * online state; a bound essence carries a glint.
 */
public final class PlayerEssenceItem extends BoundPlayerItem {
    public PlayerEssenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        if (bound == null) {
            // the fallback, editable in lang (item.witchmod.player_essence.no_target).
            tooltip.add(Component.translatable("item.witchmod.player_essence.no_target")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        tooltip.add(Component.translatable("item.witchmod.player_essence.target",
                Component.literal(bound.playerName()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));

        // online status is a client-only lookup (tooltips render on the client); skip it server-side.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Boolean online = EssenceTooltipClient.isOnline(bound.playerId());
            if (online != null) {
                tooltip.add(online
                        ? Component.translatable("item.witchmod.player_essence.online").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("item.witchmod.player_essence.offline").withStyle(ChatFormatting.RED));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(WitchModDataComponents.BOUND_PLAYER); // a bound essence shimmers; an empty one doesn't
    }
}
