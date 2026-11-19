package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SkeletonBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: held moves — right / left click charge while held and fire on release. */
public final class Holds {
    private Holds() {}

    /** a held move needs at least this many ticks of charge to fire (a trident's minimum). */
    public static final int MIN_CHARGE = 10;

    public static final Set<UUID> HELD = EffectState.sessionSet("puppeteer/holds/held");

    /** ghasts holding left-click (their volley charge). */
    public static final Set<UUID> ATTACK_HELD = EffectState.sessionSet("puppeteer/holds/attack_held");

    /** one tick of charge, sped up by the Dexterous blessing (every few ticks it credits an extra one). */
    public static int chargeStep(Player player) {
        if (player.getData(WitchModAttachments.DEXTEROUS_ACTIVE) < 0) {
            return 1;
        }
        double bonus;
        try {
            bonus = Config.PUPPETEER_DEXTEROUS_CHARGE_BONUS.get();
        } catch (IllegalStateException e) {
            return 1;
        }
        if (bonus <= 0.0) {
            return 1;
        }
        int whole = (int) bonus;
        double frac = bonus - whole;
        int every = frac > 1.0E-3 ? Math.max(1, (int) Math.round(1.0 / frac)) : 0;
        return 1 + whole + (every > 0 && player.tickCount % every == 0 ? 1 : 0);
    }

    /** clients report the attack button held / let go: a puppet with a held left-click move charges, and fires on release. */
    public static void setAttackHeld(ServerPlayer player, boolean held) {
        PuppetType type = possessed(player);
        if (type == null || !type.behaviour().holdsAttack(type)) {
            ATTACK_HELD.remove(player.getUUID());
            return;
        }
        if (held) {
            ATTACK_HELD.add(player.getUUID());
            return;
        }
        if (!ATTACK_HELD.remove(player.getUUID())) {
            return;
        }
        int charge = player.getData(WitchModAttachments.PUPPET_FUSE);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        type.behaviour().releaseAttackHeld(player, type, charge);
    }

    /** clients report right-click being held / let go. held moves charge while held and fire on release. */
    public static void setHeld(ServerPlayer player, boolean held) {
        PuppetType type = possessed(player);
        if (type == null || !type.move().held()) {
            HELD.remove(player.getUUID());
            return;
        }
        if (held && !player.isShiftKeyDown()) {
            HELD.add(player.getUUID());
            return;
        }
        boolean wasHeld = HELD.remove(player.getUUID());
        if (wasHeld && type.move() != PuppetMove.FUSE) {
            int charge = player.getData(WitchModAttachments.PUPPET_FUSE);
            player.setData(WitchModAttachments.PUPPET_FUSE, 0);
            fireHeld(player, type, charge);
        }
    }

    /** while right-click is held, the move builds charge — on the puppet's own terms (PuppetBehaviour.canCharge). */
    public static void tickHold(ServerPlayer player, PuppetType type) {
        if (!HELD.contains(player.getUUID())) {
            return;
        }
        PuppetBehaviour behaviour = type.behaviour();
        if (!behaviour.canCharge(player, type)) {
            return;
        }
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (hold == 0) {
            behaviour.chargeStarted(player, type);
        }
        player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(hold + chargeStep(player), 1200));
    }

    /** let go of a held move: the puppet does its thing with the charge (PuppetBehaviour.releaseHeld). */
    public static void fireHeld(ServerPlayer player, PuppetType type, int charge) {
        type.behaviour().releaseHeld(player, type, charge);
    }
}
