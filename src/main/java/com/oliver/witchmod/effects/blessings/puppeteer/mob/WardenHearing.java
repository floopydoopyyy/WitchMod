package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * puppeteer (warden): how it hunts by ear. every vibration a real warden could hear (vanilla's warden_can_listen game
 * events, muffled by sneaking and wool the same way) within puppeteerWardenHearingRadius reaches the puppet: it SEES the
 * sound (WardenFx draws it, through its fog), and whoever made it gives themselves away — subtle sounds (walking,
 * landing, swimming) a point, obvious ones (sprinting, eating, shooting, breaking, doors and chests) two. at
 * puppeteerWardenMarkPoints they're MARKED: red for the warden at any distance, a countdown on their own screen that
 * every further sound tops up (it runs down faster while they're out of the warden's sight), extra damage. quiet
 * creatures lose their points over time; a mark that runs out forgets them entirely. once an action has given points,
 * that same action is in GRACE for a random 1-5s (other actions still count). setting off a sculk sensor it can hear is
 * a point of its own; a shrieker going off marks everyone near it; hitting the warden, or walking into it, marks you.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class WardenHearing {
    private WardenHearing() {}

    /** one creature as a warden knows it. */
    static final class Notice {
        int points;
        /** each action (a game event; a sprinting step apart from a walking one) → until when it gives no more points. */
        final Map<String, Long> grace = new HashMap<>();
        /** out of sight, the mark runs down faster: the part-tick owed. */
        float hiddenDebt;
        long lastShown = Long.MIN_VALUE / 2;
        long lastHeard;
        long markedUntil;
        int entityId = -1;
    }

    /** warden puppet → creature → what it knows of them. */
    private static final Map<UUID, Map<UUID, Notice>> NOTICES = EffectState.sessionConcurrent("puppeteer/warden/notices");

    /** warden puppet → when its tendrils last twitched / it last made its listening sound / last roared at a mark. */
    private static final Map<UUID, Long> LAST_TWITCH = EffectState.sessionConcurrent("puppeteer/warden/last_twitch");
    private static final Map<UUID, Long> LAST_LISTEN = EffectState.sessionConcurrent("puppeteer/warden/last_listen");
    private static final Map<UUID, Long> LAST_ROAR = EffectState.sessionConcurrent("puppeteer/warden/last_roar");

    /** sounds with no one behind them (a dispenser, a falling block), shown at most this often per spot. */
    private static final Map<Long, Long> SOURCELESS = new ConcurrentHashMap<>();

    /** players last told they're being hunted (so they can be told when it's over). */
    private static final Set<UUID> HUNTED = EffectState.sessionSet("puppeteer/warden/hunted");

    // what the client's sound marks mean (WardenFx): the weight, and flags
    public static final int KIND_SOURCELESS = 0;
    public static final int KIND_SUBTLE = 1;
    public static final int KIND_OBVIOUS = 2;
    public static final int FLAG_MARKED = 4;
    public static final int FLAG_STEP = 8;
    /** the sound gave points (only those send a bead of light to the warden). */
    public static final int FLAG_COUNTED = 16;

    /**
     * obvious: loud, deliberate things. (a sprinting step is obvious too.) by key — GameEvent is a record of just its
     * radius, so two events of the same radius are equal() and can't be told apart by value.
     */
    private static final Set<ResourceKey<GameEvent>> OBVIOUS = Set.of(
            GameEvent.EAT.key(), GameEvent.DRINK.key(), GameEvent.PROJECTILE_SHOOT.key(),
            GameEvent.BLOCK_DESTROY.key(), GameEvent.BLOCK_PLACE.key(), GameEvent.BLOCK_OPEN.key(),
            GameEvent.BLOCK_CLOSE.key(), GameEvent.CONTAINER_OPEN.key(), GameEvent.CONTAINER_CLOSE.key(),
            GameEvent.BLOCK_ACTIVATE.key(), GameEvent.BLOCK_DEACTIVATE.key(), GameEvent.EXPLODE.key(),
            GameEvent.PRIME_FUSE.key(), GameEvent.INSTRUMENT_PLAY.key(), GameEvent.NOTE_BLOCK_PLAY.key(),
            GameEvent.SHEAR.key(), GameEvent.ENTITY_DAMAGE.key(), GameEvent.ELYTRA_GLIDE.key(),
            GameEvent.TELEPORT.key(), GameEvent.FLUID_PLACE.key(), GameEvent.FLUID_PICKUP.key(),
            GameEvent.ENTITY_ACTION.key());

    // --- reading it -----------------------------------------------------------------------------------

    private static Map<UUID, Notice> notices(Player warden) {
        return NOTICES.computeIfAbsent(warden.getUUID(), k -> new ConcurrentHashMap<>());
    }

    /** whether {@code warden} has marked {@code who} right now. */
    public static boolean marked(Player warden, @Nullable Entity who) {
        if (who == null) {
            return false;
        }
        Map<UUID, Notice> known = NOTICES.get(warden.getUUID());
        Notice n = known == null ? null : known.get(who.getUUID());
        return n != null && n.markedUntil > warden.level().getGameTime();
    }

    /** something a warden takes notice of: a living creature, not itself, not one of its kind, not a ghost or a builder. */
    public static boolean noticeable(Player warden, @Nullable Entity e) {
        return e instanceof LivingEntity l && l != warden && l.isAlive() && !l.isSpectator() && !(l instanceof ArmorStand)
                && !(l instanceof Warden) && !(l instanceof Player p && (p.isCreative() || WardenBehaviour.wardenPuppet(p)));
    }

    // --- hearing ---------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onGameEvent(VanillaGameEvent event) {
        if (WardenBehaviour.WARDENS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Holder<GameEvent> heard = event.getVanillaEvent();
        if (!heard.is(GameEventTags.WARDEN_CAN_LISTEN)) {
            return;
        }
        if (heard.is(GameEvent.SHRIEK.key())) {
            shrieker(level, event.getEventPosition());
            return;
        }
        Entity cause = event.getCause();
        // muffled, as for a real warden: sneaking past (steps, landings...), or something that deadens its own sound
        if (cause != null && (cause.dampensVibrations()
                || (cause.isSteppingCarefully() && heard.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)))) {
            return;
        }
        Vec3 pos = event.getEventPosition();
        // a projectile's flight is its shooter's — but where it LANDS gives no one away (a thrown decoy works)
        boolean decoy = heard.is(GameEvent.PROJECTILE_LAND.key());
        Entity source = cause instanceof Projectile shot && shot.getOwner() != null ? shot.getOwner() : cause;
        double radius;
        try {
            radius = Config.PUPPETEER_WARDEN_HEARING_RADIUS.get();
        } catch (IllegalStateException e) {
            return;
        }
        for (ServerPlayer warden : level.players()) {
            if (warden == source || warden == cause || !WardenBehaviour.WARDENS.contains(warden.getUUID())
                    || !WardenBehaviour.wardenPuppet(warden) || warden.distanceToSqr(pos) > radius * radius
                    || occluded(level, pos, warden.getEyePosition())) {
                continue;
            }
            boolean step = heard.is(GameEvent.STEP.key());
            boolean sprinting = step && source != null && source.isSprinting();
            int weight = heard.unwrapKey().map(OBVIOUS::contains).orElse(false) || sprinting ? 2 : 1;
            String action = heard.unwrapKey().map(k -> k.location().getPath()).orElse("?") + (sprinting ? "/sprint" : "");
            hear(warden, level, pos, decoy ? null : source, weight, step, action);
        }
    }

    /** a sculk shrieker went off near a warden: everyone close to it is marked at once. */
    private static void shrieker(ServerLevel level, Vec3 pos) {
        double reach = Config.PUPPETEER_WARDEN_SHRIEKER_REACH.get();
        double radius = Config.PUPPETEER_WARDEN_SHRIEKER_RADIUS.get();
        for (ServerPlayer warden : level.players()) {
            if (!WardenBehaviour.WARDENS.contains(warden.getUUID()) || !WardenBehaviour.wardenPuppet(warden)
                    || warden.distanceToSqr(pos) > reach * reach) {
                continue;
            }
            send(warden, pos, -1, KIND_OBVIOUS | FLAG_MARKED);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(radius),
                    e -> e.distanceToSqr(pos) <= radius * radius)) {
                markNow(warden, e);
            }
            react(warden, level, level.getGameTime());
        }
    }

    /** vanilla's wool check: a vibration's blocked if wool lies across every way from it to the listener. */
    private static boolean occluded(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 a = new Vec3(Mth.floor(from.x) + 0.5, Mth.floor(from.y) + 0.5, Mth.floor(from.z) + 0.5);
        Vec3 b = new Vec3(Mth.floor(to.x) + 0.5, Mth.floor(to.y) + 0.5, Mth.floor(to.z) + 0.5);
        for (Direction direction : Direction.values()) {
            Vec3 nudged = a.relative(direction, 1.0E-5F);
            if (level.isBlockInLine(new ClipBlockStateContext(nudged, b, s -> s.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType()
                    != HitResult.Type.BLOCK) {
                return false;
            }
        }
        return true;
    }

    /** {@code warden} hears something at {@code pos}: it sees it, and whoever made it gives themselves away. */
    private static void hear(ServerPlayer warden, ServerLevel level, Vec3 pos, @Nullable Entity source, int weight, boolean step,
                             String action) {
        long now = level.getGameTime();
        if (!noticeable(warden, source)) {
            // no one to blame (or no one it cares about): just the sound, now and then per spot
            long key = net.minecraft.core.BlockPos.containing(pos).asLong() ^ warden.getUUID().getLeastSignificantBits();
            Long last = SOURCELESS.get(key);
            if (last == null || now - last >= 8) {
                SOURCELESS.put(key, now);
                if (SOURCELESS.size() > 512) {
                    SOURCELESS.clear();
                }
                send(warden, pos, -1, KIND_SOURCELESS | (step ? FLAG_STEP : 0));
            }
            return;
        }
        Notice n = notices(warden).computeIfAbsent(source.getUUID(), k -> new Notice());
        n.entityId = source.getId();
        boolean obvious = weight >= 2;
        // the grace: an action that just gave points can't give more for a while (others still can)
        boolean counts = n.grace.getOrDefault(action, Long.MIN_VALUE) <= now;
        if (counts) {
            int lo = Config.PUPPETEER_WARDEN_GRACE_MIN_TICKS.get();
            int hi = Math.max(lo, Config.PUPPETEER_WARDEN_GRACE_MAX_TICKS.get());
            n.grace.put(action, now + lo + level.random.nextInt(hi - lo + 1));
            addPoints(warden, (LivingEntity) source, n, weight);
            react(warden, level, now);
        }
        // every sound shows (rate-limited per creature, so a walk is a trail of footfalls, not a blur)
        if (counts || step || now - n.lastShown >= 5) {
            n.lastShown = now;
            int kind = (obvious ? KIND_OBVIOUS : KIND_SUBTLE) | (n.markedUntil > now ? FLAG_MARKED : 0) | (step ? FLAG_STEP : 0)
                    | (counts ? FLAG_COUNTED : 0);
            send(warden, pos, source.getId(), kind);
        }
    }

    private static void send(ServerPlayer warden, Vec3 pos, int entityId, int kind) {
        PacketDistributor.sendToPlayer(warden, new WitchModNetwork.WardenHearPayload(pos.x, pos.y, pos.z, entityId, kind));
    }

    /** a sound that counts: its tendrils twitch, and now and then it makes its listening sound (everyone near hears it). */
    private static void react(ServerPlayer warden, ServerLevel level, long now) {
        if (now - LAST_TWITCH.getOrDefault(warden.getUUID(), Long.MIN_VALUE / 2) >= 12) {
            LAST_TWITCH.put(warden.getUUID(), now);
            WardenBehaviour.fx(warden, WardenBehaviour.FX_TENDRILS);
        }
        if (now - LAST_LISTEN.getOrDefault(warden.getUUID(), Long.MIN_VALUE / 2) >= 60 && !WardenBehaviour.burrowed(warden)) {
            LAST_LISTEN.put(warden.getUUID(), now);
            boolean angry = warden.getData(WitchModAttachments.PUPPET_WARDEN_ANGER) >= 80;
            level.playSound(null, warden.getX(), warden.getY(), warden.getZ(),
                    angry ? SoundEvents.WARDEN_LISTENING_ANGRY : SoundEvents.WARDEN_LISTENING, SoundSource.HOSTILE, 1.5F, 1.0F);
        }
    }

    /** {@code who} gives {@code amount} points away to {@code warden} (a sound, a sniff, it surfacing beside them). */
    public static void addPoints(ServerPlayer warden, LivingEntity who, int amount) {
        if (!noticeable(warden, who)) {
            return;
        }
        Notice n = notices(warden).computeIfAbsent(who.getUUID(), k -> new Notice());
        n.entityId = who.getId();
        addPoints(warden, who, n, amount);
    }

    private static void addPoints(ServerPlayer warden, LivingEntity who, Notice n, int amount) {
        long now = warden.level().getGameTime();
        n.lastHeard = now;
        if (n.markedUntil > now) {
            // already marked: every sound tops the countdown up
            n.markedUntil = Math.min(now + Config.PUPPETEER_WARDEN_MARK_MAX_TICKS.get(),
                    n.markedUntil + (long) amount * Config.PUPPETEER_WARDEN_MARK_EXTEND_TICKS.get());
            return;
        }
        int was = n.points;
        int next = n.points + amount;
        if (who instanceof Player && WardenBehaviour.burrowed(warden)) {
            next = Math.min(next, Math.max(was, Config.PUPPETEER_WARDEN_BURROW_POINT_CAP.get())); // below ground it can't be sure
        }
        n.points = next;
        if (n.points >= Config.PUPPETEER_WARDEN_MARK_POINTS.get()) {
            mark(warden, who, n, now);
        }
    }

    /**
     * straight to marked (a shrieker went off by them, they hit the warden, they walked into it) — or, if it's already
     * marked them, a fresh full mark. (a player still can't pass the cap while the warden's below ground.)
     */
    public static void markNow(ServerPlayer warden, LivingEntity who) {
        if (!noticeable(warden, who)) {
            return;
        }
        Notice n = notices(warden).computeIfAbsent(who.getUUID(), k -> new Notice());
        n.entityId = who.getId();
        long now = warden.level().getGameTime();
        n.lastHeard = now;
        if (n.markedUntil > now) {
            n.markedUntil = Math.max(n.markedUntil, now + Config.PUPPETEER_WARDEN_MARK_TICKS.get());
            return;
        }
        int need = Config.PUPPETEER_WARDEN_MARK_POINTS.get();
        if (who instanceof Player && WardenBehaviour.burrowed(warden)) {
            n.points = Math.max(n.points, Math.min(need, Config.PUPPETEER_WARDEN_BURROW_POINT_CAP.get()));
            if (n.points < need) {
                return;
            }
        }
        n.points = Math.max(n.points, need);
        mark(warden, who, n, now);
    }

    /** a marked creature's mark back to its full length (a sniff caught it again), however quiet it's been. */
    public static void renewMark(ServerPlayer warden, LivingEntity who) {
        Map<UUID, Notice> known = NOTICES.get(warden.getUUID());
        Notice n = known == null ? null : known.get(who.getUUID());
        long now = warden.level().getGameTime();
        if (n != null && n.markedUntil > now) {
            n.markedUntil = Math.max(n.markedUntil, now + Config.PUPPETEER_WARDEN_MARK_TICKS.get());
            n.hiddenDebt = 0.0F;
            n.lastHeard = now;
            if (who instanceof ServerPlayer hunted) {
                sendHunted(hunted, false);
            }
        }
    }

    /** marked: red for the warden at any distance, a countdown on their screen — and the first in a while is roared at. */
    private static void mark(ServerPlayer warden, LivingEntity who, Notice n, long now) {
        n.markedUntil = now + Config.PUPPETEER_WARDEN_MARK_TICKS.get();
        ServerLevel level = warden.serverLevel();
        level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 1.5F, 1.0F);
        if (who instanceof ServerPlayer hunted) {
            sendHunted(hunted, true); // at once: the sting, the red edge, the countdown
        }
        if (Config.PUPPETEER_WARDEN_ROAR_ON_MARK.get()
                && now - LAST_ROAR.getOrDefault(warden.getUUID(), Long.MIN_VALUE / 2) >= 400) {
            if (WardenBehaviour.roar(warden)) {
                LAST_ROAR.put(warden.getUUID(), now);
            }
        }
    }

    // --- keeping it up to date ------------------------------------------------------------------------

    /** every tick, per warden puppet: points fade from the quiet, marks run out; every half second the client's told. */
    public static void tick(ServerPlayer warden) {
        Map<UUID, Notice> known = NOTICES.get(warden.getUUID());
        if (known == null) {
            return;
        }
        long now = warden.level().getGameTime();
        int decay = Config.PUPPETEER_WARDEN_POINT_DECAY_TICKS.get();
        known.values().removeIf(n -> {
            if (n.markedUntil > 0 && n.markedUntil <= now) {
                return true; // the mark ran out: lost them
            }
            if (n.markedUntil <= now && n.points > 0 && now - n.lastHeard >= decay) {
                n.points--;
                n.lastHeard = now;
            }
            return n.points <= 0 && n.markedUntil <= now && now - n.lastShown > 40;
        });
        if (warden.tickCount % 5 == 0) {
            // out of its sight, a mark runs down faster
            float faster = Config.PUPPETEER_WARDEN_MARK_HIDDEN_DECAY.get().floatValue() * 5.0F;
            ServerLevel level = warden.serverLevel();
            for (Map.Entry<UUID, Notice> e : known.entrySet()) {
                Notice n = e.getValue();
                if (n.markedUntil <= now || faster <= 0.0F) {
                    continue;
                }
                Entity who = level.getEntity(e.getKey());
                if (who == null || !warden.hasLineOfSight(who)) {
                    n.hiddenDebt += faster;
                    long whole = (long) n.hiddenDebt;
                    n.markedUntil -= whole;
                    n.hiddenDebt -= whole;
                }
            }
        }
        if (warden.tickCount % 10 != 0) {
            return;
        }
        ServerLevel level = warden.serverLevel();
        List<WitchModNetwork.WardenNotice> out = new ArrayList<>();
        int marks = 0;
        int most = 0;
        for (Map.Entry<UUID, Notice> e : known.entrySet()) {
            Notice n = e.getValue();
            Entity who = level.getEntity(e.getKey());
            if (who != null && !who.isAlive()) {
                known.remove(e.getKey()); // dead: nothing left to hunt
                continue;
            }
            if (who == null) {
                continue; // elsewhere (another dimension, unloaded): nothing to show
            }
            n.entityId = who.getId();
            int left = (int) Math.max(0L, n.markedUntil - now);
            if (left > 0) {
                marks++;
            }
            most = Math.max(most, n.points);
            if (n.points > 0 || left > 0) {
                out.add(new WitchModNetwork.WardenNotice(who.getId(), n.points, left,
                        (float) who.getX(), (float) (who.getY() + who.getBbHeight() * 0.5), (float) who.getZ()));
            }
        }
        PacketDistributor.sendToPlayer(warden, new WitchModNetwork.WardenNoticesPayload(out));
        // its mood follows the hunt (vanilla's anger scale: 40 agitated, 80 angry) — the heartbeat, the glow
        int anger = marks > 0 ? Math.min(150, 80 + 15 * (marks - 1) + 10) : Math.min(79, most * 25);
        if (anger != warden.getData(WitchModAttachments.PUPPET_WARDEN_ANGER)) {
            warden.setData(WitchModAttachments.PUPPET_WARDEN_ANGER, anger);
        }
    }

    /** every half second: each hunted player's own view of it — their points and their mark's countdown (the worst of it). */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0 || (NOTICES.isEmpty() && HUNTED.isEmpty())) {
            return;
        }
        Map<UUID, int[]> worst = new HashMap<>();
        for (Map.Entry<UUID, Map<UUID, Notice>> w : NOTICES.entrySet()) {
            ServerPlayer warden = event.getServer().getPlayerList().getPlayer(w.getKey());
            if (warden == null) {
                continue;
            }
            long now = warden.level().getGameTime();
            for (Map.Entry<UUID, Notice> e : w.getValue().entrySet()) {
                Notice n = e.getValue();
                int left = (int) Math.max(0L, n.markedUntil - now);
                int[] cur = worst.computeIfAbsent(e.getKey(), k -> new int[2]);
                cur[0] = Math.max(cur[0], left > 0 ? Config.PUPPETEER_WARDEN_MARK_POINTS.get() : n.points);
                cur[1] = Math.max(cur[1], left);
            }
        }
        Set<UUID> still = new java.util.HashSet<>();
        for (Map.Entry<UUID, int[]> e : worst.entrySet()) {
            ServerPlayer hunted = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (hunted == null || (e.getValue()[0] <= 0 && e.getValue()[1] <= 0)) {
                continue;
            }
            still.add(e.getKey());
            PacketDistributor.sendToPlayer(hunted, new WitchModNetwork.WardenHuntedPayload(e.getValue()[0], e.getValue()[1],
                    Config.PUPPETEER_WARDEN_MARK_MAX_TICKS.get(), false));
        }
        for (UUID gone : HUNTED) {
            if (!still.contains(gone)) {
                ServerPlayer hunted = event.getServer().getPlayerList().getPlayer(gone);
                if (hunted != null) {
                    PacketDistributor.sendToPlayer(hunted, new WitchModNetwork.WardenHuntedPayload(0, 0, 1, false));
                }
            }
        }
        HUNTED.clear();
        HUNTED.addAll(still);
    }

    /** tell {@code hunted} where they stand right away (just marked: {@code fresh} plays the sting). */
    private static void sendHunted(ServerPlayer hunted, boolean fresh) {
        int points = 0;
        int left = 0;
        for (Map.Entry<UUID, Map<UUID, Notice>> w : NOTICES.entrySet()) {
            Notice n = w.getValue().get(hunted.getUUID());
            if (n != null) {
                long now = hunted.level().getGameTime();
                left = Math.max(left, (int) Math.max(0L, n.markedUntil - now));
                points = Math.max(points, n.markedUntil > now ? Config.PUPPETEER_WARDEN_MARK_POINTS.get() : n.points);
            }
        }
        HUNTED.add(hunted.getUUID());
        PacketDistributor.sendToPlayer(hunted, new WitchModNetwork.WardenHuntedPayload(points, left,
                Config.PUPPETEER_WARDEN_MARK_MAX_TICKS.get(), fresh));
    }

    /** the warden puppet's gone (stepped out, logged off): everything it knew goes with it. */
    public static void forget(Player warden) {
        NOTICES.remove(warden.getUUID());
        LAST_TWITCH.remove(warden.getUUID());
        LAST_LISTEN.remove(warden.getUUID());
        LAST_ROAR.remove(warden.getUUID());
    }
}
