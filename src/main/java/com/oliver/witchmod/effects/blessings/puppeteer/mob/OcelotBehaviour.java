package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.CatBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SquidBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the ocelot puppet. */
public final class OcelotBehaviour implements PuppetBehaviour {
    public static final OcelotBehaviour INSTANCE = new OcelotBehaviour();

    private OcelotBehaviour() {}

    // --- ocelot -------------------------------------------------------------------------------------

    private static final ResourceLocation OCELOT_FLEE_ID = EffectUtil.modifierId("puppeteer_ocelot_flee");

    /** an ocelot's flee burst: when it wears off (game time). */
    private static final Map<UUID, Long> FLEE_END = EffectState.sessionConcurrent("puppeteer/ocelot/flee_end");

    /** right-click: flee — a big burst of speed, the way a wild ocelot bolts when you get close. */
    private static void ocelotFlee(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, OCELOT_FLEE_ID, Config.PUPPETEER_OCELOT_FLEE_BOOST.get(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        FLEE_END.put(player.getUUID(), level.getGameTime() + Config.PUPPETEER_OCELOT_FLEE_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.OCELOT_HURT, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** ocelot upkeep: creepers keep their distance (as from a cat); the flee burst runs out. no particles: a real one just bolts. */
    private static void tickOcelot(ServerPlayer player) {
        scareCreepers(player);
        Long end = FLEE_END.get(player.getUUID());
        if (end == null) {
            return;
        }
        if (player.level().getGameTime() >= end) {
            FLEE_END.remove(player.getUUID());
            EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, OCELOT_FLEE_ID);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickOcelot(player);
    }

    /** flee: a burst of speed, like a wild ocelot bolting. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_OCELOT_FLEE_COOLDOWN_TICKS.get());
        ocelotFlee(player);
        Puppets.countAction(player);
    }

    /** left-click: a meow (its own sounds). */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        CatBehaviour.catMeow(player);
    }

    /** no bite, like the cat. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true;
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, OCELOT_FLEE_ID);
        FLEE_END.remove(player.getUUID());
    }

    /** left-click: a meow. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.meow", PuppetControls.ready2(player));
    }
}
