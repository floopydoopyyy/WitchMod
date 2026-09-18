package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * please wait... loading region... this Bethesda joke never ends.
 *
 * <p><b>Every</b> door, trapdoor or fence gate you open drops a full fake loading screen over your entire
 * view — no chance roll, no cooldown. That's deliberate: unlike most curses this one is <i>completely
 * avoidable</i>, since you choose when to touch a door, so making it certain is what gives it teeth.
 * All input except menus is dead until it finishes.
 *
 * <p>This class only declares the cost/item and starts a session; the screen itself is entirely client-side
 * (see {@code client/LoadingScreenState}), coordinated through the auto-synced
 * {@link WitchModAttachments#LOADING_SCREEN_SESSION} — the server never needs to know how long a given
 * screen ends up lasting, which matters because stutters and restarts make that length dynamic.
 */
public final class CurseLoadingScreen extends Effect {
    public CurseLoadingScreen() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.GLISTERING_MELON_SLICE);
    }

    /** you find out by having it happen to you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** hook for opening a door-ish block — see {@code CurseEventHandler}. */
    public static void trigger(ServerPlayer player) {
        long session = player.getRandom().nextLong();
        if (session == 0L) {
            session = 1L; // 0 means "nothing to show", so never hand it out as a session id
        }
        player.setData(WitchModAttachments.LOADING_SCREEN_SESSION, session);
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        trigger(target);
        return "loading screen shown";
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.LOADING_SCREEN_SESSION, 0L); // dismiss any screen in progress
    }
}
