package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.SpellSequences;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;

/** forwards your own active curses onto another player when clicked. single-use. */
public final class ItemEffigy extends Item {
    public ItemEffigy(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.effigy.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand hand) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer caster) || caster == interactionTarget) {
            return InteractionResult.PASS;
        }
        // only forwards onto another player.
        if (!(interactionTarget instanceof ServerPlayer)) {
            return InteractionResult.PASS;
        }

        ActiveEffects active = caster.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            caster.displayClientMessage(Component.literal("You have no curses to forward."), true);
            return InteractionResult.FAIL;
        }

        List<Holder.Reference<Effect>> curses = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        for (ResourceLocation id : active.activeIds()) {
            Optional<Effect> effect = WitchModRegistries.EFFECT_REGISTRY.getOptional(id);
            if (effect.isEmpty() || effect.get().category() != EffectCategory.CURSE) {
                continue;
            }
            EffectManager.holderOf(id).ifPresent(holder -> {
                curses.add(holder);
                durations.add(active.get(id).orElseThrow().remainingTicks());
            });
        }
        if (curses.isEmpty()) {
            caster.displayClientMessage(Component.literal("You have no curses to forward."), true);
            return InteractionResult.FAIL;
        }

        // lift the curses off the caster NOW; they stream across and land on the victim at the end of the
        // short SpellSequences.effigy animation.
        for (Holder.Reference<Effect> holder : curses) {
            EffectManager.remove(caster, holder);
        }
        int[] durArr = durations.stream().mapToInt(Integer::intValue).toArray();
        SpellSequences.effigy(caster, interactionTarget, curses, durArr, true);
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
