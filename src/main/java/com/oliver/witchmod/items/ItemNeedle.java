package com.oliver.witchmod.items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * Right-click a Needle with a bound Voodoo Doll in your inventory to jab the doll's target with the custom
 * {@code witchmod:voodoo} damage (half-reduced by armour, no knockback). The stab spends the Needle and a few
 * points of the Doll's durability, jolts your camera, and lands with a smack + FX on the victim — no chat text.
 * You can also pick the Needle up in the GUI and right-click it onto the doll (see {@link ItemVoodooDoll}).
 */
public final class ItemNeedle extends Item {
    public ItemNeedle(Properties properties) {
        super(properties);
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

        // The jab (FX on the victim handled inside voodooHurt) + a camera jolt on the caster.
        ItemVoodooDoll.voodooHurt(target, caster, (float) (double) Config.VOODOO_NEEDLE_BASE_DAMAGE.get());
        ItemVoodooDoll.casterShake(caster);

        needle.shrink(1);
        doll.hurtAndBreak(Config.VOODOO_NEEDLE_DOLL_COST.get(), caster.serverLevel(), caster, item -> {});
        return InteractionResultHolder.success(needle);
    }
}
