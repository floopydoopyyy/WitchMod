package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;
import com.oliver.witchmod.synergy.Synergies;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;

/** puppeteer: the creeper puppet. */
public final class CreeperBehaviour implements PuppetBehaviour {
    public static final CreeperBehaviour INSTANCE = new CreeperBehaviour();

    private CreeperBehaviour() {}

    public static final ResourceLocation FUSE_SLOW_ID = EffectUtil.modifierId("puppeteer_fuse_slow");

    /** the creeper goes off like a real one (radius from its own data, doubled when charged) and is spent. */
    public static void detonate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        int radius = tag.contains("ExplosionRadius") ? tag.getByte("ExplosionRadius") : 3;
        boolean charged = player.getData(WitchModAttachments.PUPPET_CHARGED);
        EXPLODING.add(player.getUUID());
        try {
            level.explode(player, player.getX(), player.getY(), player.getZ(), radius * (charged ? 2.0F : 1.0F),
                    Level.ExplosionInteraction.MOB);
        } finally {
            EXPLODING.remove(player.getUUID());
        }
        release(player, null, true);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickFuse(player);
        boolean charged = player.getData(WitchModAttachments.PUPPET_DATA).getBoolean("powered")
                || Synergies.CHARGED_PUPPET.activeFor(player);
        if (player.getData(WitchModAttachments.PUPPET_CHARGED) != charged) {
            player.setData(WitchModAttachments.PUPPET_CHARGED, charged);
        }
    }

    /** the fuse winds up while right-click is held and back down when let go; full, it goes off. */
    private static void tickFuse(ServerPlayer player) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        boolean held = Holds.HELD.contains(player.getUUID());
        int next = held ? hold + Holds.chargeStep(player) : Math.max(0, hold - 1);
        if (next == hold) {
            return;
        }
        if (hold == 0 && next > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.0F, 0.5F);
            EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID, -Config.PUPPETEER_CREEPER_FUSE_SLOW.get(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else if (next == 0) {
            EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID);
        }
        player.setData(WitchModAttachments.PUPPET_FUSE, next);
        if (next >= Config.PUPPETEER_CREEPER_FUSE_TICKS.get()) {
            detonate(player);
        }
    }

    @Override
    public void struckByLightning(ServerPlayer player, PuppetType type) {
        // a possessed creeper struck by lightning becomes charged, just like a real one
        net.minecraft.nbt.CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putBoolean("powered", true);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        // never called: letting go of a creeper's fuse just lets it wind back down (see Holds.setHeld)
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID);
    }
}
