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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * scrying mirror — reveals the effects on you (or, right-clicked on a player, on them) in a styled panel,
 * with any per-effect specifics ({@link Effect#scryingDetail}). using it also discovers whatever it reveals.
 */
public final class ItemScryingMirror extends Item {
    public ItemScryingMirror(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            scry(sp, sp);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (player instanceof ServerPlayer viewer && entity instanceof ServerPlayer subject) {
            scry(viewer, subject);
            return InteractionResult.SUCCESS;
        }
        // non-player targets: nothing to reveal, but swallow the interaction on the client too.
        return entity instanceof ServerPlayer ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** gather {@code subject}'s active attachments, instantly discover them, and send the styled panel to {@code viewer}. */
    private static void scry(ServerPlayer viewer, ServerPlayer subject) {
        List<WitchModNetwork.ScryEntry> out = new ArrayList<>();
        ActiveEffects active = subject.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active != null) {
            for (ResourceLocation id : active.activeIds()) {
                active.get(id).ifPresent(inst -> {
                    Effect effect = WitchModRegistries.EFFECT_REGISTRY.getOptional(id).orElse(null);
                    int kind = effect != null && effect.category() == EffectCategory.BLESSING ? 1 : 0;
                    String detail = effect == null ? "" : effect.scryingDetail(subject).orElse("");
                    out.add(new WitchModNetwork.ScryEntry(
                            DiscoveryManager.titleCase(id.getPath()), kind, (int) (inst.remainingTicks() / 20L), detail));
                    if (effect != null) {
                        effect.markDiscoveredByVictim(subject); // the mirror forces instant discovery of what it shows
                    }
                });
            }
        }
        String title = subject == viewer ? "Yourself" : subject.getName().getString();
        PacketDistributor.sendToPlayer(viewer, new WitchModNetwork.ScryPayload(title, out));

        // A little arcane flourish at the mirror.
        viewer.serverLevel().playSound(null, viewer.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.5F);
        viewer.serverLevel().sendParticles(ParticleTypes.ENCHANT, viewer.getX(), viewer.getEyeY(), viewer.getZ(), 12, 0.3, 0.3, 0.3, 0.6);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witchmod.scrying_mirror.desc1").withStyle(ChatFormatting.GRAY));
    }
}
