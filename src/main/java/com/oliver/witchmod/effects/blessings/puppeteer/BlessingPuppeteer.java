package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetHunters.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.BruteBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermanBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SlimeBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.VillagerBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;

/**
 * puppeteer: crouch + right-click a possessable mob to climb inside it. a short possession plays out (the mob
 * freezes, your soul streams in, your body is drawn into its spot), then you ARE it: its look, health, speed,
 * reach and damage, and right-click does its special move (shown bottom-right by the hud). crouch + right-click
 * again to step out. the real mob is saved and removed while you wear it and comes back where you stand, with
 * whatever health it has left. your inventory is stashed for the duration, so nothing can be used.
 *
 * <p>your own blessings and curses carry into the puppet: damage taken / dealt, healing and max health all
 * flow through it (a tank-blessed zombie is a raid boss). body-swapping ones (giant, dwarfism, gluttony's
 * size) are suspended while you're inside — see {@link #isPuppet}.
 *
 * <p>puppets: the zombie family (zombie, zombie villager, husk, drowned — babies too, half size and faster),
 * the creeper, the farm animals (pig, cow, sheep, chicken) and the fish (cod, salmon, pufferfish, tropical
 * fish). add one via {@link PuppetType}; docs/PUPPETEER_MOBS.md tracks the rest of the vanilla roster.
 */
public final class BlessingPuppeteer extends Effect {
    public BlessingPuppeteer() {
        super(EffectCategory.BLESSING, EffectCostTier.MAJOR, 75, () -> Items.BREEZE_ROD);
    }

    /** a puppet yanked away on a 90-second timer would be miserable. */
    @Override
    public boolean inRotations() {
        return false;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.PUPPETEER_ACTIVE, true);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        release(target, null, false);
        target.setData(WitchModAttachments.PUPPETEER_ACTIVE, false);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!target.getData(WitchModAttachments.PUPPETEER_ACTIVE)) {
            target.setData(WitchModAttachments.PUPPETEER_ACTIVE, true); // relog self-heal
        }
        Binding binding = BINDINGS.get(target.getUUID());
        if (binding != null) {
            tickBinding(target, binding);
            return;
        }
        PuppetType type = possessed(target);
        if (type == null) {
            return;
        }
        holdHunger(target); // a puppet never hungers: your own hunger waits until you step out
        if (ticksRemaining % 20 == 0) {
            if (type.burnsInSun() && Config.PUPPETEER_ZOMBIE_BURNS.get()) {
                sunburn(target);
            }
            scaleHealth(target);
        }
        type.behaviour().tick(target, type, ticksRemaining);
        if (type.entityType().fireImmune() && target.isOnFire()) {
            target.clearFire(); // fire-proof mobs never catch alight
        }
        if (ticksRemaining % 10 == 0) {
            // every puppet: nearby mobs that would hunt its mob TYPE pick it out (hunts() holds the faction logic),
            // so a stray puppet draws iron golems while a sheep puppet is left alone.
            drawHunters(target, type);
            PuppetFollowers.tick(target, type); // the young follow their (possessed) parent
        }
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        if ("release".equalsIgnoreCase(arg)) {
            return release(target, null, false) ? "released the puppet" : "not possessing anything";
        }
        if (arg != null && !arg.isBlank()) {
            return forceSpecial(target, arg.toLowerCase(java.util.Locale.ROOT));
        }
        target.setData(WitchModAttachments.PUPPET_COOLDOWN_END, 0L);
        Mob nearest = null;
        for (Mob mob : target.serverLevel().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(8.0),
                m -> PuppetType.of(m.getType()) != null)) {
            if (nearest == null || mob.distanceToSqr(target) < nearest.distanceToSqr(target)) {
                nearest = mob;
            }
        }
        if (nearest == null) {
            return "no possessable mob within 8 blocks";
        }
        return startPossession(target, nearest) ? "possessing a " + nearest.getType().getDescription().getString()
                : "couldn't possess (already inside one?)";
    }

    @Override
    public List<String> debugArgs() {
        return List.of("release", "recharge", "stare", "zombify", "infect", "split", "charge", "curse", "brood");
    }

    /**
     * debug: force a puppet's special state / event that normally needs the right circumstances. each checks you're
     * the right puppet and says what happened.
     */
    private static String forceSpecial(ServerPlayer player, String what) {
        PuppetType type = possessed(player);
        if (type == null) {
            return "not possessing anything";
        }
        ServerLevel level = player.serverLevel();
        switch (what) {
            case "recharge" -> {
                // every cooldown ready, every charge full
                player.setData(WitchModAttachments.PUPPET_ACTION_READY, 0L);
                player.setData(WitchModAttachments.PUPPET_ACTION2_READY, 0L);
                if (type == PuppetType.ENDERMAN) {
                    player.setData(WitchModAttachments.PUPPET_TP_CHARGES,
                            enraged(player) ? Config.PUPPETEER_ENDERMAN_RAGE_CHARGES.get() : 1);
                }
                return "all puppet cooldowns reset";
            }
            case "stare" -> {
                if (type != PuppetType.ENDERMAN) {
                    return "only an enderman puppet can be stared at";
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.5F, 1.0F);
                player.playNotifySound(SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.setData(WitchModAttachments.PUPPET_RAGE_END,
                        level.getGameTime() + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() * 20L);
                return "enraged (as if stared at) for " + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() + "s";
            }
            case "zombify" -> {
                if (type != PuppetType.HOGLIN) {
                    return "only a hoglin puppet zombifies";
                }
                zombify(player);
                return "zombified into a zoglin";
            }
            case "infect" -> {
                if (type != PuppetType.VILLAGER) {
                    return "only a villager puppet can be infected";
                }
                infect(player);
                return "infected into a zombie villager";
            }
            case "split" -> {
                if (type.group() != PuppetGroup.SLIME) {
                    return "only a slime / magma cube puppet splits";
                }
                return split(player, type) ? "split — you're one of the offspring now" : "already the smallest size (it would just die)";
            }
            case "charge" -> {
                if (type != PuppetType.CREEPER) {
                    return "only a creeper puppet can be charged";
                }
                CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
                tag.putBoolean("powered", true);
                player.setData(WitchModAttachments.PUPPET_DATA, tag);
                return "charged (as if struck by lightning)";
            }
            case "curse" -> {
                if (type != PuppetType.ELDER_GUARDIAN) {
                    return "only an elder guardian puppet curses";
                }
                elderCurse(player);
                return "cursed every other survival player in range";
            }
            case "brood" -> {
                Hideout hideout = HIDEOUTS.get(player.getUUID());
                if (type != PuppetType.SILVERFISH || hideout == null) {
                    return "only a silverfish puppet hidden in stone has a brood";
                }
                long full = (long) Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get() * Config.PUPPETEER_SILVERFISH_BROOD_MAX.get();
                HIDEOUTS.put(player.getUUID(), new Hideout(hideout.pos(), level.getGameTime() - full));
                return "brood full — burst out to release it";
            }
            default -> {
                return "unknown: " + what;
            }
        }
    }
}
