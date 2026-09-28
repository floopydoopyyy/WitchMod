package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.entities.HolyHandGrenadeEntity;

/** right-click hurls the grenade; also dispensable. the entity does the timeline + blast. */
public final class HolyHandGrenadeItem extends Item implements ProjectileItem {
    public HolyHandGrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.holy_hand_grenade.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                WitchModSounds.GRENADE_PIN.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                WitchModSounds.GRENADE_THROW.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        if (!level.isClientSide()) {
            HolyHandGrenadeEntity grenade = new HolyHandGrenadeEntity(level, player);
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                    (float) (double) Config.GRENADE_THROW_SPEED.get(), 0.4F);
            level.addFreshEntity(grenade);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        return new HolyHandGrenadeEntity(level, pos.x(), pos.y(), pos.z());
    }

    /** dispensers throw it in their facing direction — the easter-egg "target someone" trick. */
    public static void registerDispenserBehavior() {
        DispenserBlock.registerBehavior(WitchModItems.HOLY_HAND_GRENADE.get(),
                new ProjectileDispenseBehavior(WitchModItems.HOLY_HAND_GRENADE.get()));
    }
}
