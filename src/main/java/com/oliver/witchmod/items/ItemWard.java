package com.oliver.witchmod.items;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.oliver.witchmod.Config;

/**
 * Ward (CLAUDE.md section 4, redefined): a durability item that BLOCKS any attachment (curse OR blessing) cast
 * on you by someone ELSE — your own casts pass through. The block is decided in {@code data.EffectManager} via
 * the hook registered in {@code WitchMod}; this class supplies the "do you have one" check and, on a block,
 * fires the incoming coloured lash + block flare ({@link WardEffects}) and spends 1 of its durability.
 */
public final class ItemWard extends Item {
    public ItemWard(Properties properties) {
        super(properties);
    }

    public static boolean hasActiveWard(ServerPlayer player) {
        return findWard(player) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.ward.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.witchmod.ward.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.witchmod.ward.desc3").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Called by {@code EffectManager} when a Ward stops an attachment from another player. */
    public static void onBlock(ServerPlayer target, ServerPlayer caster, boolean curse) {
        ItemStack ward = findWard(target);
        if (ward == null) {
            return;
        }
        // Feedback is purely visual/audio (a lash streaking in from the caster, then a shield block).
        WardEffects.startLash(target, caster, curse);
        if (Config.WARD_DURABILITY_DECAYS.get()) {
            ward.hurtAndBreak(1, target.serverLevel(), target, item -> {
                ServerLevel level = target.serverLevel();
                level.playSound(null, target.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
            });
        }
    }

    @Nullable
    private static ItemStack findWard(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ItemWard) {
                return stack;
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof ItemWard ? off : null;
    }
}
