package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.entities.JarThrowEntity;
import com.oliver.witchmod.loot.NamedJar;
import com.oliver.witchmod.loot.NamedJars;

/**
 * a jar that stores effects (up to {@link JarContents#MAX}) and is thrown like a splash potion ({@link
 * JarEffects}). dynamic item: the variant (cursed/blessed/mixed) is derived from its contents. an empty jar
 * isn't thrown — it's the player-essence collecting tool.
 */
public final class ItemJar extends Item {
    public ItemJar(Properties properties) {
        super(properties);
    }

    /** a named-jar preset shows its own name (e.g. "jar of mining"); otherwise the plain variant name. */
    @Override
    public Component getName(ItemStack stack) {
        NamedJar named = namedPreset(stack);
        return named != null ? named.displayName() : super.getName(stack);
    }

    private static NamedJar namedPreset(ItemStack stack) {
        ResourceLocation id = stack.get(WitchModDataComponents.NAMED_JAR);
        return id == null ? null : NamedJars.byId(id);
    }

    /** right-click → THROW the jar (only when it's holding something; an empty jar is the essence tool). */
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

    /**
     * The three TYPED placeholder jars (Cursed / Blessed / Mixed) exist mainly for the creative menu and start
     * empty — an empty jar can't be thrown. When one turns up empty in a player's inventory it becomes one of
     * the predetermined NAMED jars of its variant (a real "jar of X"), so it's actually usable. The plain
     * {@code JAR} is left alone (it's the Player-Essence collecting tool).
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player) || this == WitchModItems.JAR.get()) {
            return;
        }
        if (JarContents.contents(stack).isEmpty()) {
            autoFill(stack, level.getRandom());
        }
    }

    private void autoFill(ItemStack stack, RandomSource rng) {
        int kind = this == WitchModItems.MIXED_JAR.get() ? JarContents.MIXED
                : this == WitchModItems.BLESSED_JAR.get() ? JarContents.BLESSED : JarContents.CURSED;
        NamedJar preset = NamedJars.pickForKind(kind, rng);
        if (preset != null) {
            NamedJars.fillStack(stack, preset, rng); // its variant already matches this item
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<CapturedEffect> captured = JarContents.contents(stack);
        if (captured.isEmpty()) {
            tooltip.add(Component.translatable("item.witchmod.jar.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // one-line description, then the (dynamic) list of what's actually inside.
        tooltip.add(Component.translatable("item.witchmod.jar.filled", captured.size(), JarContents.MAX)
                .withStyle(ChatFormatting.GRAY));
        for (CapturedEffect e : captured) {
            tooltip.add(Component.literal(" • " + prettyName(e.effectId())).withStyle(ChatFormatting.GRAY));
        }
    }

    static String prettyName(ResourceLocation id) {
        String path = id.getPath().replace('_', ' ');
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
