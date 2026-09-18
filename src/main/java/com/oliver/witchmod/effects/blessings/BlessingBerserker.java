package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;

/**
 * bloodlust: landing hit after hit without whiffing
 * winds your attack cooldown up faster and faster — each consecutive hit shaves another chunk off it, up to a
 * hard cap. The instant you MISS (swing at air) or go a few seconds without connecting, the whole thing
 * resets and you have to build it again.
 *
 * <p>The speed-up is a growing {@code ATTACK_SPEED} modifier (same route Main Character uses). Stacks are held
 * per-player server-side; {@code BlessingEventHandler} feeds a hit on {@code AttackEntityEvent}, the client
 * reports a miss (an air-swing) via a tiny C2S packet, and {@link #onTick} resets on the no-hit timeout and
 * self-heals the modifier after a reload.
 */
public final class BlessingBerserker extends Effect {
    public static final ResourceLocation ATTACK_SPEED_ID = EffectUtil.modifierId("blessing_berserker");

    private static final Map<UUID, Integer> STACKS = new HashMap<>();
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

    public BlessingBerserker() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 35, () -> Items.IRON_AXE);
    }

    /** discovered the first time a landed hit builds the frenzy. */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        int stacks = STACKS.getOrDefault(target.getUUID(), 0);
        double red = Math.min(Config.BERSERKER_MAX_REDUCTION.get(), stacks * Config.BERSERKER_REDUCTION_PER_HIT.get());
        return java.util.Optional.of(String.format("%.0f%% faster swings (%d hits)", red * 100, stacks));
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        UUID id = target.getUUID();
        STACKS.put(id, 0);
        LAST_HIT.put(id, target.serverLevel().getGameTime());
        target.setData(WitchModAttachments.BERSERKER_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.BERSERKER_ACTIVE) != 1) {
            target.setData(WitchModAttachments.BERSERKER_ACTIVE, 1); // self-heal after respawn/relog
        }
        UUID id = target.getUUID();
        int stacks = STACKS.getOrDefault(id, 0);
        if (stacks <= 0) {
            return;
        }
        // reset after going too long without a hit.
        long resetTicks = Math.round(Config.BERSERKER_RESET_SECONDS.get() * 20.0);
        if (target.serverLevel().getGameTime() - LAST_HIT.getOrDefault(id, 0L) > resetTicks) {
            reset(target);
            return;
        }
        applyModifier(target, stacks); // self-heal the transient modifier if a reload dropped it
        // subtle rolling feedback: a few embers per second, scaling with the stack count.
        if (target.serverLevel().getGameTime() % 4 == 0) {
            target.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    Math.min(6, 1 + stacks / 2), 0.35, 0.4, 0.35, 0.0);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        STACKS.remove(id);
        LAST_HIT.remove(id);
        EffectUtil.removeModifier(target, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        target.setData(WitchModAttachments.BERSERKER_ACTIVE, -1);
    }

    /** A landed hit: build a stack (or a couple with the Violence synergy) and refresh the no-hit timer. */
    public static void onHit(ServerPlayer player) {
        // frenzy synergy (with Violence): its stolen swings never miss, so each landed hit banks extra stacks.
        addStacks(player, com.oliver.witchmod.synergy.Synergies.FRENZY.activeFor(player) ? 2 : 1);
    }

    /** grant {@code amount} stacks (capped), refresh the timer, and pop a little gain-burst. Shared by the synergies. */
    public static void addStacks(ServerPlayer player, int amount) {
        UUID id = player.getUUID();
        int before = STACKS.getOrDefault(id, 0);
        int stacks = Math.min(maxStacks(), before + Math.max(1, amount));
        STACKS.put(id, stacks);
        LAST_HIT.put(id, player.serverLevel().getGameTime());
        applyModifier(player, stacks);
        if (stacks > before) {
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1.1, player.getZ(), 6, 0.3, 0.3, 0.3, 0.15);
        }
        Blessings.BERSERKER.get().markDiscoveredByVictim(player);
    }

    /** A miss (air-swing, reported by the client): the frenzy collapses. */
    public static void onMiss(ServerPlayer player) {
        if (STACKS.getOrDefault(player.getUUID(), 0) > 0) {
            reset(player);
        }
    }

    private static void reset(ServerPlayer player) {
        STACKS.put(player.getUUID(), 0);
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        // the frenzy fizzling out.
        player.serverLevel().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.8F);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
    }

    private static void applyModifier(ServerPlayer player, int stacks) {
        double reduction = Math.min(Config.BERSERKER_MAX_REDUCTION.get(), Config.BERSERKER_REDUCTION_PER_HIT.get() * stacks);
        // shorter cooldown -> higher attack speed. 70% shorter => 1/0.30 - 1 = +233% attack speed.
        double speedMult = 1.0 / (1.0 - reduction) - 1.0;
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, speedMult,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static int maxStacks() {
        return Config.BERSERKER_MAX_STACKS.get();
    }
}
