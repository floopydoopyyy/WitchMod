package com.oliver.witchmod.effects.blessings.puppeteer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetGroup;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.AllayBehaviour;

import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetDummies.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppeteerClient.*;

/** puppeteer, client: what a puppet holds in first person (it has no hands of its own). */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetHands {
    private PuppetHands() {}

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || typeOf(player).isEmpty()) {
            return;
        }
        PuppetType type = PuppetType.byId(typeOf(player));
        // a puppet that holds a weapon (a skeleton's bow, a pillager's crossbow, a vindicator's axe, a wither
        // skeleton's sword, a drowned's trident...) shows it in first person — vanilla renders + swings it.
        if (event.getHand() == InteractionHand.MAIN_HAND && !player.getMainHandItem().isEmpty()) {
            // the vindicator's axe is drawn by hand — vanilla's first-person placement throws it off-screen for a puppet.
            // held at the ready, raised overhead while winding up the committed lunge, and jabbed on a swing.
            if (type != null && type.group() == PuppetGroup.VINDICATOR) {
                event.setCanceled(true);
                boolean winding = player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
                renderHeldWeapon(player, player.getMainHandItem().copy(), event, winding);
                return;
            }
            return; // other weapon-holders (bow, crossbow): let vanilla draw + animate them
        }
        event.setCanceled(true); // a puppet otherwise has no hands
        // ...but a fox can see what it's carrying: the item in its mouth, low in the middle of your view
        if (event.getHand() == InteractionHand.MAIN_HAND && type == PuppetType.FOX) {
            net.minecraft.nbt.ListTag hands = player.getData(WitchModAttachments.PUPPET_DATA).getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
            ItemStack carried = hands.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(player.registryAccess(), hands.getCompound(0));
            if (!carried.isEmpty()) {
                PoseStack pose = event.getPoseStack();
                pose.pushPose();
                pose.translate(0.0, -0.42, -0.72);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20.0F));
                pose.scale(0.65F, 0.65F, 0.65F);
                Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, carried,
                        net.minecraft.world.item.ItemDisplayContext.GROUND, false, pose, event.getMultiBufferSource(), event.getPackedLight());
                pose.popPose();
            }
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        // a drowned holds its trident — raised in the throw pose while winding a throw (FUSE), else at the ready and
        // jabbed forward on a melee swing (so meleeing reads as a real trident stab).
        if (type == PuppetType.DROWNED) {
            renderHeldWeapon(player, new ItemStack(Items.TRIDENT), event, player.getData(WitchModAttachments.PUPPET_FUSE) > 0);
        }
        // an iron golem holds a poppy out low while offering it (toward the bottom of the view, not up top).
        if (type == PuppetType.IRON_GOLEM && player.getData(WitchModAttachments.PUPPET_FUSE) > 0) {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0.0, -0.55, -0.6);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-25.0F));
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, new ItemStack(Items.POPPY),
                    net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, pose,
                    event.getMultiBufferSource(), event.getPackedLight());
            pose.popPose();
        }
        // an enderman shows the block it's carrying.
        if (type == PuppetType.ENDERMAN) {
            ItemStack block = endermanCarried(player);
            if (!block.isEmpty()) {
                renderFirstPersonItem(player, block, event, false);
            }
        }
        // an allay shows whatever it's carrying, held out low in the middle of the view.
        if (type == PuppetType.ALLAY) {
            ItemStack carried = AllayBehaviour.allayHeld(player);
            if (!carried.isEmpty()) {
                PoseStack pose = event.getPoseStack();
                pose.pushPose();
                pose.translate(0.0, -0.35, -0.6);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-20.0F));
                pose.scale(0.7F, 0.7F, 0.7F);
                Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, carried,
                        net.minecraft.world.item.ItemDisplayContext.GROUND, false, pose, event.getMultiBufferSource(), event.getPackedLight());
                pose.popPose();
            }
        }
    }

    /**
     * a weapon held in first person (a vindicator's axe, a drowned's trident): at the ready, raised overhead while
     * winding up a committed move, and jabbed forward on a melee swing so meleeing reads as a real stab/chop.
     */
    static void renderHeldWeapon(Player player, ItemStack stack, RenderHandEvent event, boolean raised) {
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (raised) {
            pose.translate(0.2, 0.2, -0.55);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-75.0F));
        } else {
            double jab = Math.sin(player.getAttackAnim(event.getPartialTick()) * Math.PI); // 0 at rest, peaks mid-swing
            pose.translate(0.32, -0.32 - jab * 0.08, -0.5 - jab * 0.45);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-18.0F - (float) (jab * 38.0)));
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, pose,
                event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /** render an item in a simple first-person held pose (raised = held up in front, like a trident/offering). */
    static void renderFirstPersonItem(Player player, ItemStack stack, RenderHandEvent event, boolean raised) {
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (raised) {
            pose.translate(0.2, 0.2, -0.55);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-75.0F));
        } else {
            pose.translate(0.25, -0.3, -0.6);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-15.0F));
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, pose,
                event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /** the block an enderman puppet is carrying (its own carriedBlockState key in the puppet data), as an item. */
    static ItemStack endermanCarried(Player player) {
        net.minecraft.nbt.CompoundTag data = player.getData(WitchModAttachments.PUPPET_DATA);
        if (!data.contains("carriedBlockState", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        try {
            net.minecraft.world.level.block.state.BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(
                    player.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), data.getCompound("carriedBlockState"));
            net.minecraft.world.item.Item item = state.getBlock().asItem();
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
