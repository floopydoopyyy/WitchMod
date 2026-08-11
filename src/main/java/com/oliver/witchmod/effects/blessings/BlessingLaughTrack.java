package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Every time you talk, the whole server hears a crowd laugh/cheer (master-spec Laugh Track, sacrificial item
 * COCOA BEANS). Utterly pointless beyond that — which is the joke. The reaction (a random pick of the 5
 * supplied laugh/cheer OGGs) is played server-wide, per-listener, from
 * {@link com.oliver.witchmod.effects.BlessingEventHandler}'s chat hook, on a short cooldown so rapid chat
 * doesn't stack the crowd.
 */
public final class BlessingLaughTrack extends Effect {
    public BlessingLaughTrack() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.COCOA_BEANS);
    }

    /** You find out the first time your chat gets a laugh (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
