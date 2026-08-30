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
 * Player Essence: a bottled trace of a specific player, used to target the Bewitching Table's rituals. Bound
 * to a player's UUID (which never changes) — that UUID picks a fixed "colour" of essence via the item model,
 * a purely visual differential so you can tell whose essence you're holding at a glance.
 *
 * <p>The tooltip names the target by username (even offline) and, on the client, tells you whether they're
 * currently online. An UNBOUND essence (no target) is the funny fallback — see the {@code no_target} lang line.
 * A bound essence carries an enchantment glint; an empty one doesn't.
 */
public final class PlayerEssenceItem extends BoundPlayerItem {
    public PlayerEssenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        if (bound == null) {
            // The fallback, editable in lang (item.witchmod.player_essence.no_target).
            tooltip.add(Component.translatable("item.witchmod.player_essence.no_target")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        tooltip.add(Component.translatable("item.witchmod.player_essence.target",
                Component.literal(bound.playerName()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));

        // Online status is a client-only lookup (tooltips render on the client); skip it server-side.
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
