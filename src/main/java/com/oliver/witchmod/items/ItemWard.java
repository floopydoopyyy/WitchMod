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
 * ward — a durability item that blocks any attachment cast on you by someone else (your own casts pass). the
 * block is decided in EffectManager via a hook; this class supplies the has-one check and, on a block, fires
 * the coloured lash + flare ({@link WardEffects}) and spends 1 durability.
 */
public final class ItemWard extends Item {
    public ItemWard(Properties properties) {
        super(properties);
    }

    public static boolean hasActiveWard(ServerPlayer player) {
        return findWard(player) != null;
    }

    /** while you carry a Ward you're PROTECTED (the icon shows so you know it's working) — no particles of its own. */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && player.tickCount % 20 == 0
                && findWard(player) == stack) { // only the first ward applies it (avoid double-refresh)
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.oliver.witchmod.data.WitchModMobEffects.PROTECTED, 40, 0, true, false, true));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.ward.desc1").withStyle(ChatFormatting.GRAY));
    }

    /** called by {@code EffectManager} when a Ward stops an attachment from another player. */
    public static void onBlock(ServerPlayer target, ServerPlayer caster, boolean curse) {
        ItemStack ward = findWard(target);
        if (ward == null) {
            return;
        }
        // feedback is purely visual/audio (a lash streaking in from the caster, then a shield block).
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
