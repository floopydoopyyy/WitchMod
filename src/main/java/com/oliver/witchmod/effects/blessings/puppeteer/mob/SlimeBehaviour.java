package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the slime puppet. */
public final class SlimeBehaviour implements PuppetBehaviour {
    public static final SlimeBehaviour INSTANCE = new SlimeBehaviour();

    private SlimeBehaviour() {}

    // --- slime / magma cube ------------------------------------------------------------------------

    /** a slime puppet's size (1 small, 2 medium, 4 big — the real slime's own), from its synced data. */
    public static int slimeSize(Player player) {
        return Math.max(1, player.getData(WitchModAttachments.PUPPET_DATA).getInt("Size") + 1);
    }

    /** a slime's hit grows with it (vanilla: its attack damage is its size); a magma cube's hits harder (+2). */
    public static float slimeDamage(Player player, PuppetType type) {
        int size = slimeSize(player);
        return type == PuppetType.MAGMA_CUBE ? size + 2.0F : size;
    }

    /**
     * a slime puppet "dies": a big or medium one splits like the real thing — 2-4 smaller slimes burst out, and you
     * carry on as one of them (full health at the new size). a small one just dies. @return true if it split.
     */
    public static boolean split(ServerPlayer player, PuppetType type) {
        int size = slimeSize(player);
        if (size <= 1) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        int half = size / 2;
        int others = 1 + level.random.nextInt(3); // 2-4 offspring in all, one of them you
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        for (int i = 0; i < others; i++) {
            if (!((type == PuppetType.MAGMA_CUBE ? EntityType.MAGMA_CUBE : EntityType.SLIME).create(level)
                    instanceof net.minecraft.world.entity.monster.Slime child)) {
                break;
            }
            float ox = ((i % 2) - 0.5F) * size / 4.0F;
            float oz = ((i / 2) - 0.5F) * size / 4.0F;
            child.setSize(half, true);
            child.moveTo(player.getX() + ox, player.getY() + 0.5, player.getZ() + oz, level.random.nextFloat() * 360.0F, 0.0F);
            if (tag.getBoolean("PersistenceRequired")) {
                child.setPersistenceRequired();
            }
            level.addFreshEntity(child);
        }
        tag.putInt("Size", half - 1);
        tag.remove("attributes"); // let the restored slime re-derive its stats from the new size
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        float max = half * half; // vanilla: a slime's max health is its size squared
        BASE_MAX.put(player.getUUID(), max);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, max);
        player.setData(WitchModAttachments.PUPPET_HEALTH, max);
        scaleHealth(player);
        player.setData(WitchModAttachments.PUPPET_HEALTH, player.getData(WitchModAttachments.PUPPET_MAX_HEALTH));
        player.refreshDimensions();
        level.sendParticles(type == PuppetType.MAGMA_CUBE ? ParticleTypes.FLAME : ParticleTypes.ITEM_SLIME,
                player.getX(), player.getY() + 0.3, player.getZ(), 12 * size, 0.3 * size, 0.2 * size, 0.3 * size, 0.1);
        level.playSound(null, player.blockPosition(), type == PuppetType.MAGMA_CUBE ? SoundEvents.MAGMA_CUBE_DEATH : SoundEvents.SLIME_DEATH,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** slime puppets that were on the ground last tick (for the landing squelch / take-off boing). */
    private static final Set<UUID> SLIME_GROUNDED = EffectState.sessionSet("puppeteer/slime/slime_grounded");

    /**
     * a slime's sound and splash, as the real one makes them: a squelch and a ring of slime (or embers) on every
     * landing, scaled with its size, and a boing on every take-off. sent from the server so everyone gets them.
     */
    private static void tickSlimeFx(ServerPlayer player, PuppetType type) {
        boolean grounded = player.onGround();
        boolean was = grounded ? !SLIME_GROUNDED.add(player.getUUID()) : SLIME_GROUNDED.remove(player.getUUID());
        if (grounded == was) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int size = slimeSize(player);
        boolean magma = type == PuppetType.MAGMA_CUBE;
        boolean small = size <= 1;
        if (grounded) {
            net.minecraft.core.particles.ParticleOptions particle = magma ? ParticleTypes.FLAME : ParticleTypes.ITEM_SLIME;
            for (int i = 0; i < size * 8; i++) {
                float angle = level.random.nextFloat() * Mth.TWO_PI;
                float reach = (level.random.nextFloat() * 0.5F + 0.5F) * size * 0.5F;
                level.sendParticles(particle, player.getX() + Mth.sin(angle) * reach, player.getY(), player.getZ() + Mth.cos(angle) * reach,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            level.playSound(null, player.blockPosition(), magma ? (small ? SoundEvents.MAGMA_CUBE_SQUISH_SMALL : SoundEvents.MAGMA_CUBE_SQUISH)
                            : (small ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH), SoundSource.PLAYERS,
                    0.4F * size, ((level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
        } else if (player.getDeltaMovement().y > 0.0) {
            level.playSound(null, player.blockPosition(), magma ? SoundEvents.MAGMA_CUBE_JUMP : (small ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP),
                    SoundSource.PLAYERS, 0.4F * size, ((level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickSlimeFx(player, type);
    }

    /** its hits grow with its size (no weapon to speak of). */
    @Override
    public float finalMeleeDamage(ServerPlayer player, PuppetType type, float amount) {
        return slimeDamage(player, type);
    }

    /** "dies" → splits like the real one, and you carry on as one of the offspring (the smallest really dies). */
    @Override
    public boolean cheatsDeath(ServerPlayer player, PuppetType type, DamageSource source) {
        return split(player, type);
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** a slime's size scales its whole body (and your view) — and halves on each split. */
    @Override
    public float bodyScale(Player player, PuppetType type) {
        return slimeSize(player);
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        SLIME_GROUNDED.remove(player.getUUID());
    }

    /** a slime only bounces. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.FORWARD), "witchmod.puppeteer.action.hop", 0L);
    }
}
