package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * scrying mirror — HOLD right-click to peer into the effects on YOU, or on the player you're looking at.
 * peering slows you heavily and throws off arcane motes (client-rendered, no server particle spam); the
 * panel appears instantly, refreshes while held so timers stay live, and lingers a moment after you lower it.
 * reveals also discover what they show. no special use-pose (would clip badly in third person).
 */
public final class ItemScryingMirror extends Item {
    private static final double LOOK_REACH = 5.0;

    public ItemScryingMirror(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        // instant first reveal, no hold delay.
        if (player instanceof ServerPlayer sp) {
            scry(sp, lookedAtPlayer(sp), true);
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    // pass so a right-click aimed at a player still starts the hold via use() — the subject is resolved by
    // a look-raycast, so scrying self and others share one path.
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // hold as long as you like
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide()) {
            // arcane motes rendered locally (each client spawns its own), so nobody can flood the server.
            for (int i = 0; i < 2; i++) {
                double a = entity.getRandom().nextDouble() * Math.PI * 2;
                double r = 0.3 + entity.getRandom().nextDouble() * 0.5;
                level.addParticle(ParticleTypes.ENCHANT,
                        entity.getX() + Math.cos(a) * r, entity.getEyeY() + entity.getRandom().nextDouble() * 0.4 - 0.2,
                        entity.getZ() + Math.sin(a) * r, -Math.cos(a) * 0.4, 0.0, -Math.sin(a) * 0.4);
            }
            if (entity.getRandom().nextInt(6) == 0) {
                level.addParticle(ParticleTypes.PORTAL,
                        entity.getX(), entity.getEyeY(), entity.getZ(),
                        entity.getRandom().nextGaussian() * 0.1, 0.05, entity.getRandom().nextGaussian() * 0.1);
            }
            return;
        }
        if (!(entity instanceof ServerPlayer viewer)) {
            return;
        }
        // significantly slowed while peering — refreshed each tick, and cleared instantly on release (below).
        viewer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10,
                Config.SCRYING_SLOWNESS_AMPLIFIER.get(), true, false, false));

        // keep the panel's timers live while held (a fresh reveal was already sent on the click).
        int elapsed = getUseDuration(stack, entity) - remaining;
        int refresh = Math.max(1, Config.SCRYING_REFRESH_TICKS.get());
        if (elapsed > 0 && elapsed % refresh == 0) {
            scry(viewer, lookedAtPlayer(viewer), false);
        }
    }

    // the moment you lower the mirror the slow lifts (the UI still lingers a beat client-side).
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide()) {
            MobEffectInstance slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (slow != null && slow.getAmplifier() == Config.SCRYING_SLOWNESS_AMPLIFIER.get()) {
                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); // only our own peering slow
            }
        }
    }

    /** the player the viewer is looking at within reach, else the viewer themselves. */
    private static ServerPlayer lookedAtPlayer(ServerPlayer viewer) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = viewer.getViewVector(1.0F).scale(LOOK_REACH);
        Vec3 end = eye.add(look);
        AABB box = viewer.getBoundingBox().expandTowards(look).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, eye, end, box,
                e -> e instanceof ServerPlayer && e != viewer && e.isPickable(), LOOK_REACH * LOOK_REACH);
        return hit != null && hit.getEntity() instanceof ServerPlayer sp ? sp : viewer;
    }

    /** gather {@code subject}'s active attachments and send the styled panel to {@code viewer}; discovers + chimes only on the first reveal. */
    private static void scry(ServerPlayer viewer, ServerPlayer subject, boolean first) {
        List<WitchModNetwork.ScryEntry> out = new ArrayList<>();
        ActiveEffects active = subject.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active != null) {
            for (ResourceLocation id : active.activeIds()) {
                active.get(id).ifPresent(inst -> {
                    Effect effect = WitchModRegistries.EFFECT_REGISTRY.getOptional(id).orElse(null);
                    int kind = effect != null && effect.category() == EffectCategory.BLESSING ? 1 : 0;
                    Component detail = effect == null ? Component.empty() : effect.scryingDetail(subject).orElse(Component.empty());
                    out.add(new WitchModNetwork.ScryEntry(
                            DiscoveryManager.titleCase(id.getPath()), kind, (int) (inst.remainingTicks() / 20L), detail));
                    if (effect != null && first) {
                        effect.markDiscoveredByVictim(subject); // the mirror forces instant discovery of what it shows
                    }
                });
            }
        }
        String title = subject == viewer ? "Yourself" : subject.getName().getString();
        PacketDistributor.sendToPlayer(viewer, new WitchModNetwork.ScryPayload(title, out));

        // a soft chime only on the first reveal (even when nothing is present — the mirror still responds).
        if (first) {
            float pitch = out.isEmpty() ? 1.1F : 1.5F;
            viewer.serverLevel().playSound(null, viewer.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.6F, pitch);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.scrying_mirror.desc1").withStyle(ChatFormatting.GRAY));
    }
}
