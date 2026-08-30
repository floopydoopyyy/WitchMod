package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.entities.JarThrowEntity;

/**
 * A jar that stores curses/blessings (up to {@link JarContents#MAX}) and is THROWN like a splash potion to
 * unleash them — see {@link JarEffects}. It's a DYNAMIC item: the variant (Cursed / Blessed / Mixed) is
 * derived from its contents, so filling a Cursed Jar with a blessing turns it into a Mixed Jar (the fill
 * happens at the Bewitching Table with a jar in the target slot, or via {@code /bewitch jar}). An EMPTY jar
 * isn't thrown — it stays the Player-Essence collecting tool.
 */
public final class ItemJar extends Item {
    public ItemJar(Properties properties) {
        super(properties);
    }

    /** Right-click → THROW the jar (only when it's holding something; an empty jar is the essence tool). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (JarContents.contents(stack).isEmpty()) {
            return InteractionResultHolder.pass(stack); // nothing to unleash — leave it to the essence handler
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5F, 0.4F);
        if (!level.isClientSide()) {
            JarThrowEntity jar = new JarThrowEntity(level, player);
            jar.setItem(stack);
            jar.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(jar);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<CapturedEffect> captured = JarContents.contents(stack);
        if (captured.isEmpty()) {
            tooltip.add(Component.literal("Empty — holds up to " + JarContents.MAX + " curse/blessing")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Fill it at a Bewitching Table (jar in the target slot).")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        tooltip.add(Component.literal("Holds " + captured.size() + " / " + JarContents.MAX + " — throw to unleash")
                .withStyle(ChatFormatting.GRAY));
        for (CapturedEffect e : captured) {
            tooltip.add(Component.literal(" • " + prettyName(e.effectId())).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    static String prettyName(ResourceLocation id) {
        String path = id.getPath().replace('_', ' ');
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
