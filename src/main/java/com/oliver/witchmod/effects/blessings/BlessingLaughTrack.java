package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Blessings;
import com.mojang.serialization.Codec;
import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectState;
import java.util.Map;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import java.util.UUID;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * every time you talk, the whole server hears a crowd laugh/cheer. Utterly pointless beyond that — which is the joke. The reaction (a random pick of the 5
 * supplied laugh/cheer OGGs) is played server-wide, per-listener, from
 * the chat hook, on a short cooldown so rapid chat
 * doesn't stack the crowd.
 */
public final class BlessingLaughTrack extends Effect {
    public BlessingLaughTrack() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.COCOA_BEANS);
    }

    /** you find out the first time your chat gets a laugh (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** player uuid -> game tick their next laugh-track reaction is allowed. */
    private static final Map<UUID, Long> COOLDOWN = EffectState.saved("laugh_track/cooldown", Codec.LONG);

    @Override
    public void onChat(ServerPlayer self, ServerChatEvent event) {
        trigger(self);
    }

    /** a server-wide crowd reaction for the speaker, on the shared cooldown — also used by yap's comedic_timing. */
    public static void trigger(ServerPlayer speaker) {
        long now = speaker.serverLevel().getGameTime();
        Long ready = COOLDOWN.get(speaker.getUUID());
        if (ready != null && now < ready) {
            return;
        }
        COOLDOWN.put(speaker.getUUID(), now + Config.LAUGHTRACK_COOLDOWN.get());
        // mostly laughs, the odd cheer. sent per listener, so it's a global "tv laugh track", not positional
        var sound = speaker.getRandom().nextDouble() < Config.LAUGHTRACK_CHEER_CHANCE.get()
                ? WitchModSounds.LAUGHTRACK_CHEER.get()
                : WitchModSounds.LAUGHTRACK_LAUGH.get();
        float volume = Config.LAUGHTRACK_VOLUME.get().floatValue();
        for (ServerPlayer listener : speaker.getServer().getPlayerList().getPlayers()) {
            listener.playNotifySound(sound, SoundSource.PLAYERS, volume, 1.0F);
        }
        Blessings.LAUGH_TRACK.get().markDiscoveredByVictim(speaker);
    }
}
