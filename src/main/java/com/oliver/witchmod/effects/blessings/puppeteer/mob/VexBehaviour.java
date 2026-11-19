package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermiteBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the vex puppet. */
public final class VexBehaviour implements PuppetBehaviour {
    public static final VexBehaviour INSTANCE = new VexBehaviour();

    private VexBehaviour() {}

    /** a vex puppet's dissolve tick (it's temporary); the action bar counts down to it. */
    private static final Map<UUID, Long> VEX_END = EffectState.sessionConcurrent("puppeteer/vex/vex_end");

    // --- vex ----------------------------------------------------------------------------------------

    /** vex upkeep: it never really lands (so it stays airborne + phasing), and it's temporary — an action-bar timer. */
    private static void tickVex(ServerPlayer player) {
        if (player.getAbilities().mayfly && !player.getAbilities().flying) {
            player.getAbilities().flying = true; // kept aloft (and so always phasing)
            player.onUpdateAbilities();
        }
        tickLunge(player, PuppetType.VEX); // the lunge's contact check
        int duration = Config.PUPPETEER_VEX_DURATION_TICKS.get();
        if (duration <= 0) {
            VEX_END.remove(player.getUUID());
            return; // no timer
        }
        long now = player.level().getGameTime();
        long end = VEX_END.computeIfAbsent(player.getUUID(), k -> now + duration);
        long left = end - now;
        if (left <= 0) {
            VEX_END.remove(player.getUUID());
            release(player, null, false); // the vex dissolves (dies, see Possession.release) and lets you go
            return;
        }
        if (now % 4 == 0) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.vex_timer", (int) Math.ceil(left / 20.0))
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /**
     * every swing: the real vex's charge — a giggle, then a dash down your aim that barely steers (its charging, red
     * look shows the whole way), the vex's only way to hit. nothing during the cooldown.
     */
    private static void startVexLunge(ServerPlayer player) {
        if (!ready(player) || LUNGES.containsKey(player.getUUID())) {
            return;
        }
        long now = player.level().getGameTime();
        cooldown(player, Config.PUPPETEER_VEX_LUNGE_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** the dash's contact: the first thing it reaches takes the vex's hit, then the lunge ends. */
    private static void tickVexLunge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            SPECIAL_DAMAGE.add(player.getUUID());
            try {
                e.hurt(player.damageSources().playerAttack(player), Config.PUPPETEER_VEX_ATTACK.get().floatValue());
            } finally {
                SPECIAL_DAMAGE.remove(player.getUUID());
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            level.playSound(null, e.blockPosition(), SoundEvents.VEX_HURT, SoundSource.PLAYERS, 1.0F, 1.3F);
            endLunge(player);
            return;
        }
    }

    /** right-click: just the annoying vex giggle. */
    private static void vexLaugh(ServerPlayer player) {
        if (!ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_VEX_LAUGH_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.VEX_AMBIENT, SoundSource.PLAYERS, 1.3F, 1.0F);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickVex(player);
    }

    /** right-click: just the annoying vex laugh. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        vexLaugh(player);
    }

    /** left-click: the lunge — its only way to hit. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        startVexLunge(player);
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        tickVexLunge(player);
    }

    /** the vex only hits via its lunge. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** always flying (it phases while it flies). */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
        if (player.getAbilities().mayfly) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        VEX_END.remove(player.getUUID());
    }

    /** left-click: the lunge. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }
}
