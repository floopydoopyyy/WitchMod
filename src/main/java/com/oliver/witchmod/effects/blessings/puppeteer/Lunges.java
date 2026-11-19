package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GoatBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.VindicatorBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the shared lunge machinery (charges, dives, mauls, rams) — the client drives the movement. */
public final class Lunges {
    private Lunges() {}

    // --- hoglin / zoglin: lone brutes, no rallies -----------------------------------------------------

    /** a lunge in progress (server side): when it started and who it has already hit. */
    public record Lunge(long start, Set<UUID> hit) {}

    public static final Map<UUID, Lunge> LUNGES = EffectState.sessionConcurrent("puppeteer/lunges/lunges");

    // the lunge machinery is shared: hoglin / zoglin charges and the phantom's dive (no wind-up, steered in 3d).
    public static int lungeWindup(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_WINDUP.get();
            case PHANTOM, KILLER_RABBIT, GOAT, SCREAMING_GOAT, FOX, VINDICATOR, PIGLIN_BRUTE, WOLF -> 0; // the charge/none is the wind-up
            case VEX -> Config.PUPPETEER_VEX_LUNGE_WINDUP_TICKS.get(); // a slight giggle-charge before it darts
            default -> Config.PUPPETEER_HOGLIN_LUNGE_WINDUP.get();
        };
    }

    /** a lunge's length for this player — a goat's ram / a vindicator's committed lunge depend on the charge. */
    public static int lungeTicks(Player player, PuppetType type) {
        if (type.group() == PuppetGroup.GOAT) {
            return ramTicks(player);
        }
        if (type.group() == PuppetGroup.VINDICATOR) { // (and the piglin brute)
            return vindLungeTicks(player);
        }
        if (type == PuppetType.WOLF) {
            return Math.max(3, (int) Math.round(lungeTicks(type) * com.oliver.witchmod.effects.blessings.puppeteer.mob.WolfBehaviour.maulShare(player)));
        }
        return lungeTicks(type);
    }

    /** a lunge's speed for this player — a goat's ram / a vindicator's committed lunge depend on the charge. */
    public static double lungeSpeed(Player player, PuppetType type) {
        if (type.group() == PuppetGroup.GOAT) {
            return ramSpeed(player);
        }
        if (type.group() == PuppetGroup.VINDICATOR) { // (and the piglin brute)
            return vindLungeSpeed(player);
        }
        if (type == PuppetType.WOLF) {
            return lungeSpeed(type) * com.oliver.witchmod.effects.blessings.puppeteer.mob.WolfBehaviour.maulShare(player);
        }
        return lungeSpeed(type);
    }

    public static int lungeTicks(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_TICKS.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_DIVE_TICKS.get();
            case KILLER_RABBIT -> Config.PUPPETEER_KILLER_RABBIT_MAUL_TICKS.get();
            case WOLF -> Config.PUPPETEER_WOLF_MAUL_TICKS.get();
            case FOX -> Config.PUPPETEER_FOX_POUNCE_TICKS.get();
            case VEX -> Config.PUPPETEER_VEX_LUNGE_TICKS.get();
            default -> Config.PUPPETEER_HOGLIN_LUNGE_TICKS.get();
        };
    }

    public static double lungeSpeed(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_SPEED.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_DIVE_SPEED.get();
            case KILLER_RABBIT -> Config.PUPPETEER_KILLER_RABBIT_MAUL_SPEED.get();
            case WOLF -> Config.PUPPETEER_WOLF_MAUL_SPEED.get();
            case FOX -> Config.PUPPETEER_FOX_POUNCE_SPEED.get();
            case VEX -> Config.PUPPETEER_VEX_LUNGE_SPEED.get();
            default -> Config.PUPPETEER_HOGLIN_LUNGE_SPEED.get();
        };
    }

    public static double lungeTurn(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_TURN.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_DIVE_TURN.get();
            case KILLER_RABBIT, WOLF -> 0.0; // a maul goes dead straight
            case GOAT, SCREAMING_GOAT, FOX, VINDICATOR, PIGLIN_BRUTE -> 3.0; // a ram (or a fox's rush / an axe lunge) barely turns
            case VEX -> Config.PUPPETEER_VEX_LUNGE_TURN.get(); // a vex homes in a bit as it darts
            default -> Config.PUPPETEER_HOGLIN_LUNGE_TURN.get();
        };
    }

    /** Lunge: stand still for the wind-up, then charge (your own client steers it — see PuppeteerClient.tickLunge). */
    public static void startLunge(ServerPlayer player, PuppetType type) {
        long now = player.level().getGameTime();
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        // subtle on purpose — it should pass for a real hoglin squaring up.
        player.serverLevel().playSound(null, player.blockPosition(),
                type == PuppetType.ZOGLIN ? SoundEvents.ZOGLIN_ANGRY : SoundEvents.HOGLIN_ANGRY, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void endLunge(ServerPlayer player) {
        LUNGES.remove(player.getUUID());
        if (player.getData(WitchModAttachments.PUPPET_LUNGE_START) != 0L) {
            player.setData(WitchModAttachments.PUPPET_LUNGE_START, 0L);
        }
    }

    /**
     * a lunge in progress: after its wind-up, each tick the puppet checks what it's run into (PuppetBehaviour.lungeContact);
     * once it's run its course it ends (PuppetBehaviour.lungeExpired — a vindicator's miss).
     */
    public static void tickLunge(ServerPlayer player, PuppetType type) {
        Lunge lunge = LUNGES.get(player.getUUID());
        if (lunge == null) {
            return;
        }
        long t = player.level().getGameTime() - lunge.start();
        int windup = lungeWindup(type);
        if (t >= windup + lungeTicks(player, type) + lungeGrace(type)) {
            type.behaviour().lungeExpired(player, type);
            endLunge(player);
            return;
        }
        if (t >= windup) {
            type.behaviour().lungeContact(player, type, lunge);
        }
    }

    /** a maul keeps looking a few ticks past the movement: the server sees your client-driven lunge late. */
    private static int lungeGrace(PuppetType type) {
        return type == PuppetType.KILLER_RABBIT || type == PuppetType.WOLF ? Config.PUPPETEER_LUNGE_HIT_GRACE_TICKS.get() : 0;
    }
}
