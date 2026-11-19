package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

/** puppeteer: the llama puppet. */
public final class LlamaBehaviour implements PuppetBehaviour {
    public static final LlamaBehaviour INSTANCE = new LlamaBehaviour();

    private LlamaBehaviour() {}

    // --- llama --------------------------------------------------------------------------------------

    /** llama: Spit (right-click) — the real llama's spit, from its mouth along your aim (1 damage; mostly an insult). */
    public static void spit(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.projectile.LlamaSpit spit = new net.minecraft.world.entity.projectile.LlamaSpit(EntityType.LLAMA_SPIT, level);
        spit.setOwner(player);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double out = (EntityType.LLAMA.getWidth() + 1.0) * 0.5; // vanilla's mouth offset for a llama
        spit.setPos(player.getX() - out * Mth.sin(yaw), player.getEyeY() - 0.1, player.getZ() + out * Mth.cos(yaw));
        Vec3 look = player.getLookAngle();
        spit.shoot(look.x, look.y, look.z, 1.5F, 1.0F);
        level.addFreshEntity(spit);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS, 1.0F,
                1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F);
    }

    /** the real spit — 1 damage, mostly an insult. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_LLAMA_SPIT_COOLDOWN_TICKS.get());
        spit(player);
        Puppets.countAction(player);
    }
}
