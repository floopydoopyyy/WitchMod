package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.TwitchChat;
import com.oliver.witchmod.data.Usernames;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * your own personal Twitch stream. A fake chat panel reacts
 * to everything you do, sometimes drops genuinely-useful info (nearby structures/players/chests), and runs an
 * internal <b>entertainment score</b> that drives a live <b>viewer count</b>, chains highlights into a
 * <b>hype train</b>, and earns <b>subs</b> — cashed in for rewards at the end (capped).
 *
 * <p>All the brains are here (server-side): the score is fed by action hooks in {@code BlessingEventHandler}
 * via {@link #quiet}/{@link #highlight}; the decay, sub economy, combo/hype-train, and random
 * donation/raid events live in {@link #onTick}; each line is pushed to the owner's overlay
 * ({@code client/ChatOverlayLayer}) through {@link WitchModNetwork#sendChatLine}.
 */
public final class BlessingChat extends Effect {
    // kinds understood by the overlay: 0 normal, 1 sub, 2 donation, 3 raid, 4 hype-highlight.
    private static final int KIND_NORMAL = 0, KIND_SUB = 1, KIND_DONATION = 2, KIND_RAID = 3, KIND_HYPE = 4;

    private static final Map<UUID, Double> SCORE = new HashMap<>();
    private static final Map<UUID, Double> SUB_ACCUM = new HashMap<>();
    private static final Map<UUID, Long> NEXT_MESSAGE = new HashMap<>();
    private static final Map<UUID, String> RECENT_ACTION = new HashMap<>();
    private static final Map<UUID, Long> RECENT_ACTION_TICK = new HashMap<>();
    private static final Map<UUID, Integer> COMBO = new HashMap<>();
    private static final Map<UUID, Long> COMBO_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHUNK = new HashMap<>();
    private static final Map<UUID, Boolean> ALIVE = new HashMap<>(); // is the chat currently awake (vs DEAD)?
    private static final Map<UUID, Double> SHOWN_HYPE = new HashMap<>(); // eased hype so climbs/drops are gradual, not instant
    private static final Map<UUID, Long> DEATH_SPAM_UNTIL = new HashMap<>(); // window of rapid dealwithit/trolldance spam
    private static final long ACTION_WINDOW = 80; // ticks a recent action keeps flavouring the chat

    /** message tokens the overlay recognises: an emote image or an animated gif, by name. */
    public static final String EMOTE_PREFIX = "E";
    public static final String GIF_PREFIX = "G";
    private static final String[] EMOTES = {"kappa", "kekw", "lul", "pog", "sadge"};
    private static final String[] GIFS = {"stevedance", "trolldance", "dealwithit"};

    /** categories that read as GOOD highlights → rendered with the hype background. */
    private static final Set<String> HYPE_CATEGORIES =
            Set.of("pvp_kill", "kill", "clutch", "diamond", "crit", "tame", "hype_train");

    /** stable-ish chatter name colours (twitch-ish). */
    private static final int[] NAME_COLORS = {
            0xFF4A80, 0x9147FF, 0x1DB9C3, 0x2ECC71, 0xE67E22, 0xF1C40F, 0x3498DB, 0xE74C3C, 0x00D1B2, 0xC792EA,
            0xFF7AC6, 0x8AE234, 0x5DA9FF, 0xFFB347, 0xB39DFF
    };

    public BlessingChat() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.PURPLE_WOOL);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        UUID id = target.getUUID();
        target.setData(WitchModAttachments.CHAT_OVERLAY, 1);
        target.setData(WitchModAttachments.CHAT_SUBS, 0);
        target.setData(WitchModAttachments.CHAT_HYPE, 0);
        // you start with a DEAD chat and have to earn your way out of it.
        SCORE.put(id, 0.0);
        SUB_ACCUM.put(id, 0.0);
        COMBO.put(id, 0);
        ALIVE.put(id, false);
        SHOWN_HYPE.put(id, 0.0);
        NEXT_MESSAGE.put(id, target.serverLevel().getGameTime() + 20);
        String dead = TwitchChat.pick("dead", target.getRandom());
        if (dead != null) {
            line(target, format(dead, target, ""), KIND_NORMAL);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        payoutSubs(target);
        UUID id = target.getUUID();
        target.setData(WitchModAttachments.CHAT_OVERLAY, -1);
        target.setData(WitchModAttachments.CHAT_HYPE, 0);
        SCORE.remove(id);
        SUB_ACCUM.remove(id);
        NEXT_MESSAGE.remove(id);
        RECENT_ACTION.remove(id);
        RECENT_ACTION_TICK.remove(id);
        COMBO.remove(id);
        COMBO_TICK.remove(id);
        LAST_CHUNK.remove(id);
        ALIVE.remove(id);
        SHOWN_HYPE.remove(id);
        DEATH_SPAM_UNTIL.remove(id);
    }

    /** the streamer died — chat tanks (a lot of interest lost) and rapidly spams the mocking gifs. */
    public static void onStreamerDeath(ServerPlayer player) {
        UUID id = player.getUUID();
        double score = SCORE.getOrDefault(id, 0.0);
        SCORE.put(id, score * (1.0 - Config.CHAT_DEATH_PENALTY_FRACTION.get()));
        COMBO.put(id, 0);
        DEATH_SPAM_UNTIL.put(id, player.serverLevel().getGameTime() + Config.CHAT_DEATH_SPAM_TICKS.get());
        String d = TwitchChat.pick("death", player.getRandom());
        if (d != null) {
            line(player, format(d, player, ""), KIND_NORMAL);
        }
    }

    /** the live viewer count for a given hype fraction — exponential from base to max (bottom crawls, top blows up). */
    public static double viewersAt(double hype01) {
        double base = Math.max(1.0, Config.CHAT_VIEWER_BASE.get());
        double max = Math.max(base, Config.CHAT_VIEWER_MAX.get());
        return base * Math.pow(max / base, Mth.clamp(hype01, 0.0, 1.0));
    }

    // --- Scoring API (called from the action hooks) --------------------------------------------------------

    /** A small, frequent action: feed the score + remember the category, but don't spam an instant line. */
    public static void quiet(ServerPlayer player, String category, double amount) {
        add(player, category, amount);
    }

    /** A notable moment: feed the score, react instantly with a themed line, and advance the hype combo. */
    public static void highlight(ServerPlayer player, String category, double amount) {
        add(player, category, amount);
        UUID id = player.getUUID();
        long now = player.serverLevel().getGameTime();
        // combo streak — chained highlights within the window build a multiplier and eventually a hype train.
        int streak = (now - COMBO_TICK.getOrDefault(id, -100000L) <= Config.CHAT_COMBO_WINDOW_TICKS.get())
                ? COMBO.getOrDefault(id, 0) + 1 : 1;
        COMBO.put(id, streak);
        COMBO_TICK.put(id, now);

        String msg = TwitchChat.pick(category, player.getRandom());
        if (msg != null) {
            line(player, format(msg, player, ""), HYPE_CATEGORIES.contains(category) ? KIND_HYPE : KIND_NORMAL);
        }
        if (streak >= Config.CHAT_HYPE_TRAIN_HITS.get()) {
            fireHypeTrain(player);
            COMBO.put(id, 0);
        }
    }

    private static void add(ServerPlayer player, String category, double amount) {
        UUID id = player.getUUID();
        double max = Config.CHAT_SCORE_MAX.get();
        double gain = amount * comboMultiplier(id, player.serverLevel().getGameTime());
        SCORE.put(id, Math.min(max, SCORE.getOrDefault(id, 0.0) + gain));
        RECENT_ACTION.put(id, category);
        RECENT_ACTION_TICK.put(id, player.serverLevel().getGameTime());
    }

    private static double comboMultiplier(UUID id, long now) {
        if (now - COMBO_TICK.getOrDefault(id, -100000L) > Config.CHAT_COMBO_WINDOW_TICKS.get()) {
            return 1.0;
        }
        int streak = COMBO.getOrDefault(id, 0);
        int hits = Math.max(2, Config.CHAT_HYPE_TRAIN_HITS.get());
        double step = (Config.CHAT_COMBO_MAX_MULT.get() - 1.0) / (hits - 1);
        return Math.min(Config.CHAT_COMBO_MAX_MULT.get(), 1.0 + Math.max(0, streak - 1) * step);
    }

    private static void fireHypeTrain(ServerPlayer player) {
        String msg = TwitchChat.pick("hype_train", player.getRandom());
        if (msg != null) {
            line(player, format(msg, player, ""), KIND_HYPE);
        }
        int subs = Math.min(Config.CHAT_SUBS_CAP.get(),
                player.getData(WitchModAttachments.CHAT_SUBS) + Config.CHAT_HYPE_TRAIN_SUB_BONUS.get());
        player.setData(WitchModAttachments.CHAT_SUBS, subs);
        // A hype train also pins the score high for a beat.
        SCORE.put(player.getUUID(), Math.min(Config.CHAT_SCORE_MAX.get(),
                SCORE.getOrDefault(player.getUUID(), 0.0) + Config.CHAT_SCORE_MAX.get() * 0.35));
    }

    // --- Per-tick loop -------------------------------------------------------------------------------------

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();
        double max = Config.CHAT_SCORE_MAX.get();

        // self-heal the overlay flag after a respawn/relog (the blessing persists through death).
        if (target.getData(WitchModAttachments.CHAT_OVERLAY) != 1) {
            target.setData(WitchModAttachments.CHAT_OVERLAY, 1);
        }

        // fresh-chunk exploration bonus (cheap: only when the chunk key changes).
        long chunk = target.chunkPosition().toLong();
        if (LAST_CHUNK.getOrDefault(id, Long.MIN_VALUE) != chunk) {
            if (LAST_CHUNK.containsKey(id)) {
                quiet(target, "explore", Config.CHAT_EXPLORE_SCORE.get());
            }
            LAST_CHUNK.put(id, chunk);
        }

        // score decay — idling loses a lot, drifting about loses a little; a recent action pauses the decay.
        // sleeping drains it regardless (nobody watches you nap) and chat cracks sleep-stream jokes.
        double score = SCORE.getOrDefault(id, 0.0);
        if (target.isSleeping()) {
            score = Math.max(0.0, score - Config.CHAT_SLEEP_DECAY.get());
            SCORE.put(id, score);
        } else {
            long sinceAction = now - RECENT_ACTION_TICK.getOrDefault(id, -1000L);
            if (sinceAction > ACTION_WINDOW) {
                boolean moving = target.getDeltaMovement().horizontalDistanceSqr() > 0.01 * 0.01;
                double decay = moving ? Config.CHAT_PASSIVE_DECAY.get() : Config.CHAT_IDLE_DECAY.get();
                score = Math.max(0.0, score - decay);
                SCORE.put(id, score);
            }
        }
        // eased hype so climbs (and the death drop) are GRADUAL, not instant — viewers ramp in over a beat.
        double rawHype = score / max;
        double shown = SHOWN_HYPE.getOrDefault(id, 0.0);
        shown += (rawHype - shown) * Config.CHAT_HYPE_EASE.get();
        if (Math.abs(rawHype - shown) < 0.0005) {
            shown = rawHype;
        }
        SHOWN_HYPE.put(id, shown);
        double hype = Mth.clamp(shown, 0.0, 1.0);
        target.setData(WitchModAttachments.CHAT_HYPE, (int) Math.round(hype * 100));

        // death spam: rapid dealwithit / trolldance right after a death.
        long spamUntil = DEATH_SPAM_UNTIL.getOrDefault(id, 0L);
        boolean deathSpam = now < spamUntil;
        if (deathSpam && now % 3 == 0) {
            gifLine(target, target.getRandom().nextBoolean() ? "dealwithit" : "trolldance", KIND_NORMAL);
        }

        // phase: DEAD chat has to be crawled out of. Hysteresis so it doesn't flicker on the boundary.
        boolean alive = ALIVE.getOrDefault(id, false);
        if (!alive && hype >= Config.CHAT_REVIVE_THRESHOLD.get()) {
            alive = true;
            ALIVE.put(id, true);
            String revive = TwitchChat.pick("revive", target.getRandom());
            if (revive != null) {
                line(target, format(revive, target, ""), KIND_HYPE);
            }
        } else if (alive && hype <= Config.CHAT_DEAD_THRESHOLD.get()) {
            alive = false;
            ALIVE.put(id, false);
            String died = TwitchChat.pick("dead", target.getRandom());
            if (died != null) {
                line(target, format(died, target, ""), KIND_NORMAL);
            }
        }

        double viewers = viewersAt(hype);

        // sub economy — subs are driven by the (exponential) viewer count, so they crawl at the bottom and
        // pour in at the top. Nothing accrues while the chat is dead.
        if (alive) {
            double accum = SUB_ACCUM.getOrDefault(id, 0.0) + viewers * Config.CHAT_SUB_PER_VIEWER_TICK.get();
            if (accum >= 1.0) {
                int gained = (int) accum;
                accum -= gained;
                int subs = Math.min(Config.CHAT_SUBS_CAP.get(), target.getData(WitchModAttachments.CHAT_SUBS) + gained);
                if (subs > target.getData(WitchModAttachments.CHAT_SUBS)) {
                    target.setData(WitchModAttachments.CHAT_SUBS, subs);
                    if (target.getRandom().nextFloat() < 0.4F) {
                        sendSubAlert(target);
                    }
                }
            }
            SUB_ACCUM.put(id, accum);
        }

        // chat messages — sparse & sad while dead; an escalating flood as hype climbs. Held during death spam.
        if (!deathSpam && now >= NEXT_MESSAGE.getOrDefault(id, 0L)) {
            RandomSource random = target.getRandom();
            int min = Config.CHAT_INTERVAL_MIN.get();
            int maxI = Math.max(min, Config.CHAT_INTERVAL_MAX.get());
            int interval;
            if (!alive) {
                // dead chat: rare, lonely tumbleweed lines.
                String d = TwitchChat.pick("dead", random);
                if (d != null) {
                    line(target, format(d, target, ""), KIND_NORMAL);
                }
                interval = maxI + random.nextInt(maxI); // even longer than the ambient max
            } else {
                // random monetisation events, scaled by hype.
                if (random.nextDouble() < Config.CHAT_RAID_CHANCE.get() * hype) {
                    sendRaid(target);
                } else if (random.nextDouble() < Config.CHAT_DONATION_CHANCE.get() * hype) {
                    sendDonation(target);
                } else {
                    int burst = 1 + (int) Math.floor(hype * 3.0); // chat floods harder the more hyped it is
                    for (int i = 0; i < burst; i++) {
                        sendMessage(target, hype);
                    }
                }
                // interval shrinks EXPONENTIALLY with hype (max->min), so the pace ramps hard near the top.
                interval = (int) Math.round(maxI * Math.pow((double) min / maxI, hype));
                interval = Math.max(min, interval);
                interval += random.nextInt(Math.max(1, interval / 3 + 1));
            }
            NEXT_MESSAGE.put(id, now + interval);
        }
    }

    // --- Message picking -----------------------------------------------------------------------------------

    private static void sendMessage(ServerPlayer player, double hype) {
        RandomSource random = player.getRandom();
        // A slice of chat is image spam — gifs (rare) and emotes (common), both scaling with hype.
        if (random.nextDouble() < Config.CHAT_GIF_CHANCE.get() * hype) {
            gifLine(player, GIFS[random.nextInt(GIFS.length)], KIND_NORMAL);
            return;
        }
        if (random.nextDouble() < Config.CHAT_EMOTE_CHANCE.get() * hype) {
            emoteLine(player, EMOTES[random.nextInt(EMOTES.length)]);
            return;
        }
        if (random.nextDouble() < Config.CHAT_USEFUL_CHANCE.get()) {
            String info = usefulInfo(player);
            if (info != null) {
                String template = TwitchChat.pick("useful", random);
                if (template != null) {
                    line(player, format(template, player, info), KIND_NORMAL);
                    return;
                }
            }
        }
        String category = pickCategory(player, hype);
        String msg = TwitchChat.pick(category, random);
        if (msg != null) {
            line(player, format(msg, player, ""), HYPE_CATEGORIES.contains(category) ? KIND_HYPE : KIND_NORMAL);
        }
    }

    private static String pickCategory(ServerPlayer player, double hype) {
        UUID id = player.getUUID();
        RandomSource random = player.getRandom();
        // sleeping? Chat mostly cracks sleep-stream jokes (with the odd emote for flavour).
        if (player.isSleeping() && random.nextFloat() < 0.8F) {
            return "sleep";
        }
        // A slice of chat is always just emote spam / lurkers, for authenticity.
        double r = random.nextDouble();
        if (r < 0.18) {
            return "emote";
        }
        if (r < 0.24) {
            return "lurk";
        }
        long since = player.serverLevel().getGameTime() - RECENT_ACTION_TICK.getOrDefault(id, -1000L);
        if (since < ACTION_WINDOW && RECENT_ACTION.containsKey(id) && random.nextFloat() < 0.7F) {
            return RECENT_ACTION.get(id);
        }
        if (hype > 0.66) {
            return random.nextBoolean() ? "hype" : "emote";
        }
        if (hype < 0.15) {
            return random.nextBoolean() ? "idle" : "generic";
        }
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.01 * 0.01;
        if (moving && random.nextBoolean()) {
            return "explore";
        }
        return "generic";
    }

    private static void sendSubAlert(ServerPlayer player) {
        String template = TwitchChat.pick("sub", player.getRandom());
        if (template == null) {
            return;
        }
        String subscriber = Usernames.random(player.getRandom());
        line(player, template.replace("{value}", subscriber).replace("{player}", name(player)), KIND_SUB);
    }

    private static void sendDonation(ServerPlayer player) {
        String template = TwitchChat.pick("donation", player.getRandom());
        if (template == null) {
            return;
        }
        String donor = Usernames.random(player.getRandom());
        line(player, template.replace("{value}", donor).replace("{player}", name(player)), KIND_DONATION);
        // A donation throws a couple of subs your way.
        int subs = Math.min(Config.CHAT_SUBS_CAP.get(),
                player.getData(WitchModAttachments.CHAT_SUBS) + 1 + player.getRandom().nextInt(3));
        player.setData(WitchModAttachments.CHAT_SUBS, subs);
    }

    private static void sendRaid(ServerPlayer player) {
        String template = TwitchChat.pick("raid", player.getRandom());
        if (template == null) {
            return;
        }
        String raider = Usernames.random(player.getRandom());
        line(player, template.replace("{value}", raider).replace("{player}", name(player)), KIND_RAID);
        // A raid surges viewers (score) and drops a handful of subs.
        SCORE.put(player.getUUID(), Math.min(Config.CHAT_SCORE_MAX.get(),
                SCORE.getOrDefault(player.getUUID(), 0.0) + Config.CHAT_SCORE_MAX.get() * 0.45));
        int subs = Math.min(Config.CHAT_SUBS_CAP.get(),
                player.getData(WitchModAttachments.CHAT_SUBS) + 2 + player.getRandom().nextInt(5));
        player.setData(WitchModAttachments.CHAT_SUBS, subs);
    }

    /** send one line with a random chatter name (coloured) + message + a kind for special styling. */
    private static void line(ServerPlayer player, String message, int kind) {
        String user = Usernames.random(player.getRandom());
        int color = NAME_COLORS[Math.floorMod(user.hashCode(), NAME_COLORS.length)];
        WitchModNetwork.sendChatLine(player, user, message, color, kind);
    }

    /** A chatter spamming a static emote image. */
    private static void emoteLine(ServerPlayer player, String emote) {
        line(player, EMOTE_PREFIX + emote, KIND_NORMAL);
    }

    /** A chatter posting an animated gif. */
    private static void gifLine(ServerPlayer player, String gif, int kind) {
        line(player, GIF_PREFIX + gif, kind);
    }

    private static String format(String raw, ServerPlayer player, String value) {
        return raw.replace("{player}", name(player)).replace("{value}", value);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    // --- Useful info ---------------------------------------------------------------------------------------

    /** A genuinely-useful nearby detail, or null if nothing resolved this time. */
    @Nullable
    private static String usefulInfo(ServerPlayer self) {
        ServerLevel level = self.serverLevel();
        RandomSource random = self.getRandom();
        int start = random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            String result = switch ((start + i) % 4) {
                case 0 -> nearestPlayer(level, self);
                case 1 -> nearestStructure(level, self);
                case 2 -> nearestChest(level, self);
                default -> nearestNotableMob(level, self);
            };
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Nullable
    private static String nearestPlayer(ServerLevel level, ServerPlayer self) {
        ServerPlayer nearest = null;
        double best = 96.0 * 96.0;
        for (ServerPlayer p : level.getPlayers(p -> p != self && p.isAlive() && !p.isSpectator())) {
            double d = p.distanceToSqr(self);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest == null ? null
                : name(nearest) + " is " + direction(self, nearest.getX(), nearest.getZ())
                        + ", ~" + (int) Math.sqrt(best) + " blocks away";
    }

    @Nullable
    private static String nearestStructure(ServerLevel level, ServerPlayer self) {
        var tag = self.getRandom().nextBoolean() ? StructureTags.VILLAGE : StructureTags.MINESHAFT;
        String structureName = tag == StructureTags.VILLAGE ? "a village" : "a mineshaft";
        BlockPos pos = level.findNearestMapStructure(tag, self.blockPosition(), Config.CHAT_STRUCTURE_RADIUS_CHUNKS.get(), false);
        if (pos == null) {
            return null;
        }
        int dist = (int) Math.sqrt(dist2d(self.getX(), self.getZ(), pos.getX(), pos.getZ()));
        return "there's " + structureName + " " + direction(self, pos.getX(), pos.getZ()) + ", ~" + dist + " blocks out";
    }

    @Nullable
    private static String nearestChest(ServerLevel level, ServerPlayer self) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos origin = self.blockPosition();
        int r = 6;
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.getBlockState(cursor).getBlock() instanceof ChestBlock) {
                        double d = cursor.distSqr(origin);
                        if (d < bestSqr) {
                            bestSqr = d;
                            best = cursor.immutable();
                        }
                    }
                }
            }
        }
        return best == null ? null
                : "there's a chest " + direction(self, best.getX(), best.getZ()) + ", ~" + (int) Math.sqrt(bestSqr) + " blocks";
    }

    @Nullable
    private static String nearestNotableMob(ServerLevel level, ServerPlayer self) {
        Mob nearest = null;
        double best = 24.0 * 24.0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, self.getBoundingBox().inflate(24.0), Mob::isAlive)) {
            double d = mob.distanceToSqr(self);
            if (d < best) {
                best = d;
                nearest = mob;
            }
        }
        return nearest == null ? null
                : "a " + nearest.getType().getDescription().getString() + " is lurking "
                        + direction(self, nearest.getX(), nearest.getZ());
    }

    // --- Rewards -------------------------------------------------------------------------------------------

    private static void payoutSubs(ServerPlayer player) {
        int subs = player.getData(WitchModAttachments.CHAT_SUBS);
        if (subs <= 0) {
            player.sendSystemMessage(Component.literal("Stream over — 0 subs. Better luck next time. Sadge").withColor(0xB79CE8));
            return;
        }
        // emeralds are the ONLY reward, and deliberately modest.
        int emeralds = subs / Config.CHAT_SUBS_PER_EMERALD.get();
        giveOrDrop(player, new ItemStack(Items.EMERALD, emeralds));
        player.sendSystemMessage(Component.literal("🎉 Stream over! " + subs + " subs → ")
.withColor(0x9147FF)
.append(Component.literal(emeralds + " emerald" + (emeralds == 1 ? "" : "s")).withColor(0xE6DCF5)));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    // --- Helpers -------------------------------------------------------------------------------------------

    private static double dist2d(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    private static String direction(ServerPlayer self, double x, double z) {
        double angle = Mth.atan2(x - self.getX(), -(z - self.getZ()));
        return switch ((int) Math.round(angle / (Math.PI / 4.0)) & 7) {
            case 0 -> "to the north";
            case 1 -> "to the north-east";
            case 2 -> "to the east";
            case 3 -> "to the south-east";
            case 4 -> "to the south";
            case 5 -> "to the south-west";
            case 6 -> "to the west";
            default -> "to the north-west";
        };
    }
}
