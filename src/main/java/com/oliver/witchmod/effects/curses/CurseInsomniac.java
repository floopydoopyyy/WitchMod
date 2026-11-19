package com.oliver.witchmod.effects.curses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.InsomniacMessages;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.minecraft.world.entity.player.Player;

/**
 * you can't sleep. Trying to get into bed just doesn't work — you get one of a few
 * silly excuses from a writable list instead, and because you never actually sleep, vanilla's own phantom
 * "time since rest" counter keeps climbing exactly as it would for anyone who stayed up all night.
 *
 * <p>The block lives in the hooks below on {@code CanPlayerSleepEvent}: it only fires when you
 * WOULD otherwise have slept (vanilla found no problem of its own), so a daytime or unsafe bed still shows
 * vanilla's normal reason rather than a curse line.
 */
public final class CurseInsomniac extends Effect {
    public CurseInsomniac() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.PHANTOM_MEMBRANE);
    }

    /** you find out the first time your bed just... won't take you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** hook for a thwarted bedtime — see the hooks below. */
    public void onSleepDenied(ServerPlayer target) {
        String line = InsomniacMessages.pick(target.getRandom());
        if (line != null) {
            target.displayClientMessage(Component.literal(line), true); // above the hotbar, not chat spam
        }
        markDiscoveredByVictim(target);
    }

    /**
     * the bed just won't take you. only overrides when vanilla would have let you sleep (so a daytime bed or nearby
     * monsters still show their normal reason) and blocks with OTHER_PROBLEM, which has no vanilla message, so the
     * curse's own line stands alone. never sleeping is exactly what keeps the phantom counter rising.
     */
    @Override
    public void onSleep(ServerPlayer self, CanPlayerSleepEvent event) {
        if (event.getProblem() == null) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            onSleepDenied(self);
        }
    }
}
