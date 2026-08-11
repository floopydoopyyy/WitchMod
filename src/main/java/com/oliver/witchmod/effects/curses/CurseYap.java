package com.oliver.witchmod.effects.curses;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.YapMessages;
import com.oliver.witchmod.effects.Curses;

/**
 * You cannot stop talking (master-spec Yap). Two streams of chatter, both broadcast to server chat as if you
 * typed them, both drawn from the writable {@code data/witchmod/text/yap.json} (see {@link YapMessages}):
 * <ul>
 *   <li><b>Ambient</b> — every {@code INTERVAL_MIN}..{@code MAX} a line from the singles/doubles/triples
 *       lists (multi-message combos sent in order; more messages rarer).</li>
 *   <li><b>Reactions</b> — a much rarer line keyed to an event: taking damage, dealing damage, opening a
 *       chest, dying, or a player getting close. Each event has its own long cooldown.</li>
 * </ul>
 */
public final class CurseYap extends Effect {
    /** When this player's next ambient outburst is due (game tick). */
    private static final Map<UUID, Long> NEXT_YAP = new HashMap<>();
    /** Remaining lines of an in-progress multi-message outburst, sent one per gap. */
    private static final Map<UUID, Deque<String>> QUEUE = new HashMap<>();
    /** When the next queued line should go out. */
    private static final Map<UUID, Long> NEXT_LINE = new HashMap<>();
    /** Per-player, per-event cooldown end tick, keyed "uuid|event". */
    private static final Map<String, Long> EVENT_COOLDOWN = new HashMap<>();

    private static final int PROXIMITY_CHECK_INTERVAL = 20;

    public CurseYap() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.PAPER);
    }

    /** You discover this the first time your mouth runs off without you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        scheduleNext(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        NEXT_YAP.remove(id);
        QUEUE.remove(id);
        NEXT_LINE.remove(id);
        EVENT_COOLDOWN.keySet().removeIf(k -> k.startsWith(id + "|"));
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        beginOutburst(target, YapMessages.pickOutburst(target.getRandom()));
        return "yapped an outburst";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        long now = target.serverLevel().getGameTime();

        // Self-heal: the schedule lives in a transient map, so a curse that persisted across a relog/world
        // reload never ran onApply. Re-schedule if we have no record, so it can never go silent forever.
        if (!NEXT_YAP.containsKey(id)) {
            scheduleNext(target);
        }

        // Mid-outburst: send the next queued line when its moment comes.
        Deque<String> queue = QUEUE.get(id);
        if (queue != null && !queue.isEmpty()) {
            if (now >= NEXT_LINE.getOrDefault(id, 0L)) {
                say(target, queue.poll());
                if (queue.isEmpty()) {
                    QUEUE.remove(id);
                } else {
                    NEXT_LINE.put(id, now + Config.YAP_MESSAGE_GAP_TICKS.get());
                }
            }
            return; // don't start anything new while one is still going
        }

        // A nearby player is its own (rare) reaction — checked on an interval, gated by the event cooldown.
        if (now % PROXIMITY_CHECK_INTERVAL == 0 && hasPlayerNearby(target)) {
            triggerEvent(target, "proximity");
        }

        // Ambient outburst when due.
        if (now >= NEXT_YAP.getOrDefault(id, Long.MAX_VALUE)) {
            beginOutburst(target, YapMessages.pickOutburst(target.getRandom()));
            scheduleNext(target);
        }
    }

    // --- Event reactions (called from CurseEventHandler) -----------------------------------------------

    /** Fires an event reaction line if that event's long cooldown has elapsed and it isn't mid-outburst. */
    public static void triggerEvent(ServerPlayer target, String eventKey) {
        UUID id = target.getUUID();
        long now = target.serverLevel().getGameTime();
        Deque<String> queue = QUEUE.get(id);
        if (queue != null && !queue.isEmpty()) {
            return; // already talking
        }
        String cdKey = id + "|" + eventKey;
        if (now < EVENT_COOLDOWN.getOrDefault(cdKey, 0L)) {
            return; // still on cooldown
        }
        String[] lines = YapMessages.pickEvent(eventKey, target.getRandom());
        if (lines == null) {
            return; // no lines written for this event
        }
        EVENT_COOLDOWN.put(cdKey, now + Config.YAP_EVENT_COOLDOWN_TICKS.get());
        beginOutburst(target, lines);
    }

    // --- Shared helpers --------------------------------------------------------------------------------

    /** Says the first line now and queues the rest, so combos land one after another. */
    private static void beginOutburst(ServerPlayer target, String[] lines) {
        if (lines == null || lines.length == 0) {
            return;
        }
        say(target, lines[0]);
        if (lines.length > 1) {
            Deque<String> rest = new ArrayDeque<>(lines.length - 1);
            for (int i = 1; i < lines.length; i++) {
                rest.add(lines[i]);
            }
            QUEUE.put(target.getUUID(), rest);
            NEXT_LINE.put(target.getUUID(), target.serverLevel().getGameTime() + Config.YAP_MESSAGE_GAP_TICKS.get());
        }
        Curses.YAP.value().markDiscoveredByVictim(target);
    }

    private static boolean hasPlayerNearby(ServerPlayer target) {
        double r = Config.YAP_PROXIMITY_RADIUS.get();
        return target.serverLevel().getPlayers(p -> p != target && p.isAlive()
                && p.distanceToSqr(target) <= r * r).size() > 0;
    }

    private static void scheduleNext(ServerPlayer target) {
        int min = Config.YAP_INTERVAL_MIN_TICKS.get();
        int max = Math.max(min, Config.YAP_INTERVAL_MAX_TICKS.get());
        long delay = min + target.getRandom().nextInt(max - min + 1);
        NEXT_YAP.put(target.getUUID(), target.serverLevel().getGameTime() + delay);
    }

    /** Broadcasts one line to the whole server as if the player had typed it in chat. */
    private static void say(ServerPlayer target, String line) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            return;
        }
        Component chat = Component.translatable("chat.type.text", target.getDisplayName(), Component.literal(line));
        server.getPlayerList().broadcastSystemMessage(chat, false);
    }
}
