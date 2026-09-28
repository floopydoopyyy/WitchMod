package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * a second face of the Delusions curse (Oliver's addition): while it's active, REAL players around the victim
 * occasionally render as the WRONG online player — someone else's skin AND nametag — for a while, then look
 * like themselves again. victim-only, purely client-side: nobody else is misled and the server owns nothing.
 *
 * <p>The skin swap rides on {@link UglySkinManager#desiredSkin} (the single owner of the reflective
 * {@code PlayerInfo.skinLookup} override, so this never fights Ugly / player-disguise); the nametag is
 * overridden in {@code DisguiseClient.onNameTag}. This class only holds the schedule + the current
 * real-player → impersonated-player mapping, ticked from {@code ClientCurseHandler}.
 */
public final class DelusionMisidentify {
    private record Fake(UUID impersonated, long endTick) {}
    /** real online player uuid → who they currently look like (from the victim's POV). */
    private static final Map<UUID, Fake> MISID = new HashMap<>();
    private static long nextStartTick;

    private DelusionMisidentify() {}

    /** called every client tick, before UglySkinManager reads the mapping. */
    public static void tick(Minecraft mc) {
        LocalPlayer victim = mc.player;
        if (victim == null || mc.getConnection() == null
                || victim.getData(WitchModAttachments.DELUSIONS_SIGNAL) == 0L
                || Config.DELUSIONS_MISID_MAX.get() <= 0) {
            if (!MISID.isEmpty()) {
                MISID.clear(); // curse off / disconnected — everyone looks like themselves again
            }
            nextStartTick = 0;
            return;
        }
        long now = victim.level().getGameTime();
        // drop the ones whose window has ended, or whose impersonated player has logged off.
        MISID.entrySet().removeIf(e -> now >= e.getValue().endTick()
                || mc.getConnection().getPlayerInfo(e.getValue().impersonated()) == null);

        if (nextStartTick == 0L) {
            nextStartTick = now + interval(victim.getRandom());
            return;
        }
        if (now >= nextStartTick) {
            nextStartTick = now + interval(victim.getRandom());
            startOne(mc, victim, now);
        }
    }

    /** picks a real player to misidentify and a DIFFERENT online player for them to look like. */
    private static void startOne(Minecraft mc, LocalPlayer victim, long now) {
        if (MISID.size() >= Config.DELUSIONS_MISID_MAX.get()) {
            return;
        }
        // candidates: other real players in the world, not already misidentified.
        List<UUID> present = new ArrayList<>();
        for (Entity e : mc.level.players()) {
            UUID id = e.getUUID();
            if (!id.equals(victim.getUUID()) && !MISID.containsKey(id)) {
                present.add(id);
            }
        }
        if (present.isEmpty()) {
            return;
        }
        // identities to borrow: any online player except the one being disguised (self is allowed — a doppelganger).
        List<UUID> online = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            online.add(info.getProfile().getId());
        }
        RandomSource rng = victim.getRandom();
        UUID real = present.get(rng.nextInt(present.size()));
        online.remove(real);
        if (online.isEmpty()) {
            return; // nobody else to be
        }
        UUID asWho = online.get(rng.nextInt(online.size()));
        MISID.put(real, new Fake(asWho, now + duration(rng)));
    }

    private static int interval(RandomSource rng) {
        int min = Config.DELUSIONS_MISID_INTERVAL_MIN.get();
        int max = Math.max(min, Config.DELUSIONS_MISID_INTERVAL_MAX.get());
        return min + rng.nextInt(max - min + 1);
    }

    private static int duration(RandomSource rng) {
        int min = Config.DELUSIONS_MISID_DURATION_MIN.get();
        int max = Math.max(min, Config.DELUSIONS_MISID_DURATION_MAX.get());
        return min + rng.nextInt(max - min + 1);
    }

    /** the online player {@code realPlayerId} currently looks like, or null if not being misidentified. */
    @Nullable
    public static UUID impersonatedBy(UUID realPlayerId) {
        Fake fake = MISID.get(realPlayerId);
        return fake == null ? null : fake.impersonated();
    }
}
