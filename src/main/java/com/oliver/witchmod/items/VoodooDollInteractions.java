package com.oliver.witchmod.items;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/** event-driven Voodoo Doll interactions: throwing it flings the victim, and a lightning strike hits them. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class VoodooDollInteractions {
    private VoodooDollInteractions() {}

    /** throwing (dropping) a bound doll flings the victim the same way — for a big chunk of durability. */
    @SubscribeEvent
    static void onToss(ItemTossEvent event) {
        ItemEntity itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof ItemVoodooDoll) || !stack.has(WitchModDataComponents.BOUND_PLAYER)
                || !(event.getPlayer() instanceof ServerPlayer caster)) {
            return;
        }
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            ItemVoodooDoll.offlineFizzle(caster);
            return;
        }
        ItemVoodooDoll.fling(target, caster.getLookAngle(), Config.VOODOO_THROW_FORCE.get(), 0.42);
        stack.hurtAndBreak(Config.VOODOO_THROW_DOLL_COST.get(), caster.serverLevel(), caster, it -> {});
        if (stack.isEmpty()) {
            itemEntity.discard();
        }
    }

    /** using a FISHING ROD on a bound doll lying in front of you YANKS the victim your way — huge durability cost. */
    @SubscribeEvent
    static void onFishingRod(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)
                || !(event.getItemStack().getItem() instanceof net.minecraft.world.item.FishingRodItem)) {
            return;
        }
        double range = Config.VOODOO_FISHING_RANGE.get();
        Vec3 look = caster.getLookAngle();
        ItemEntity dollEntity = null;
        for (ItemEntity ie : caster.serverLevel().getEntitiesOfClass(ItemEntity.class,
                caster.getBoundingBox().inflate(range),
                e -> e.isAlive() && e.getItem().getItem() instanceof ItemVoodooDoll
                        && e.getItem().has(WitchModDataComponents.BOUND_PLAYER))) {
            Vec3 dir = ie.position().subtract(caster.getEyePosition());
            if (dir.lengthSqr() > range * range || dir.normalize().dot(look) < 0.5) {
                continue; // must be roughly in front of you
            }
            dollEntity = ie;
            break;
        }
        if (dollEntity == null) {
            return; // no doll aimed at — let the rod cast normally
        }
        event.setCanceled(true); // it's a voodoo yank, not a fishing cast
        ItemStack doll = dollEntity.getItem();
        PlayerEssenceData bound = doll.get(WitchModDataComponents.BOUND_PLAYER);
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            ItemVoodooDoll.offlineFizzle(caster);
            return;
        }
        ItemVoodooDoll.fling(target, look, Config.VOODOO_FISHING_FORCE.get(), 0.55);
        caster.serverLevel().playSound(null, target.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
        doll.hurtAndBreak(Config.VOODOO_FISHING_DOLL_COST.get(), caster.serverLevel(), caster, it -> {});
        if (doll.isEmpty()) {
            dollEntity.discard();
        }
    }

    /** A bound doll struck by lightning calls a bolt down on the victim — and is destroyed. */
    @SubscribeEvent
    static void onLightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof ItemVoodooDoll) || !stack.has(WitchModDataComponents.BOUND_PLAYER)
                || !(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target != null && target.serverLevel() != null) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(target.serverLevel());
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                target.serverLevel().addFreshEntity(bolt);
            }
        }
        itemEntity.discard();
    }
}
