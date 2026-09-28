package com.oliver.witchmod.blocks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.items.ItemJar;
import com.oliver.witchmod.items.ItemVoodooDoll;
import com.oliver.witchmod.items.JarContents;
import com.oliver.witchmod.items.WitchModItems;

/**
 * holy water cleanses witch items — empties a filled jar, unbinds a voodoo doll. triggered by dropping the
 * item into holy water or right-clicking it at a holy-water source.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class HolyWaterItems {
    private HolyWaterItems() {}

    /** the cleansed form of {@code stack}, or {@code null} if holy water does nothing to it. */
    private static ItemStack purified(ItemStack stack) {
        if (stack.getItem() instanceof ItemJar && !JarContents.contents(stack).isEmpty()) {
            return new ItemStack(WitchModItems.JAR.get(), stack.getCount()); // emptied jar
        }
        if (stack.getItem() instanceof ItemVoodooDoll && stack.has(WitchModDataComponents.BOUND_PLAYER.get())) {
            return new ItemStack(WitchModItems.VOODOO_DOLL.get(), stack.getCount()); // unbound doll
        }
        return null;
    }

    /** right-click a filled jar / bound doll while looking at holy water → cleanse it. crouching THROWS a filled jar as normal. */
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        // crouch + filled jar → let the throw happen (Jar.use); only the non-crouch dunk cleanses it.
        boolean jar = event.getItemStack().getItem() instanceof ItemJar;
        if (jar && player.isShiftKeyDown()) {
            return;
        }
        ItemStack purified = purified(event.getItemStack());
        if (purified == null) {
            return;
        }
        Level level = player.level();
        HitResult hit = player.pick(5.0, 0.0F, true); // include fluids
        if (!(hit instanceof BlockHitResult bhr) || !HolyWater.isProtectingFluidAt(level, bhr.getBlockPos())) {
            return;
        }
        if (!level.isClientSide()) {
            purified.setCount(event.getItemStack().getCount());
            player.setItemInHand(event.getHand(), purified);
            HolyWater.fizzle((ServerLevel) level, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z);
            if (jar) {
                cleanseJarFeedback((ServerLevel) level, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z);
            }
        }
        player.swing(event.getHand());
        event.setCanceled(true);
    }

    /** the extra "curses wash out" cue when a filled jar is emptied into holy water: a bottle glug + a spray of dissolving motes. */
    private static void cleanseJarFeedback(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, x, y + 0.4, z, 16, 0.35, 0.3, 0.35, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, x, y + 0.2, z, 12, 0.3, 0.2, 0.3, 0.05);
    }

    /** dropped filled jars / bound dolls floating in holy water get cleansed. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity e : level.getEntities().getAll()) {
                if (!(e instanceof ItemEntity item) || !HolyWater.isProtecting(item)) {
                    continue;
                }
                ItemStack purified = purified(item.getItem());
                if (purified != null) {
                    purified.setCount(item.getItem().getCount());
                    item.setItem(purified);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            item.getX(), item.getY() + 0.2, item.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                    level.playSound(null, item.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 1.5F);
                }
            }
        }
    }
}
