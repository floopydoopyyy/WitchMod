package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.entities.SnailEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * Cutaway Gag (statement curse) — Spyglass. Occasionally your camera CUTS AWAY to a random other player on
 * the server and you're forced to spectate, frozen, while a stupid event is inflicted on them; being hit
 * has a high chance to snap you back (the gag's effects still stick). A Family-Guy cutaway made real.
 *
 * <p><b>Why the watcher is teleported:</b> to spectate a distant player the client needs that player's
 * chunks loaded and the entity tracked, so on a cutaway the server relocates the watcher's (invisible,
 * gravity-less) body next to the victim, points the camera at them via the synced {@link
 * WitchModAttachments#CUTAWAY_TARGET} (the client half in {@code ClientCurseHandler}), and teleports them
 * back when it ends. The watcher is left hittable so "being hit ends it" works from the chaos they're now
 * standing in.
 *
 * <p>Reads as a CURSE (the watcher is immobilised, relocated and made vulnerable; random others get
 * griefed) — flip {@link EffectCategory} if that's ever reconsidered.
 */
public final class CurseCutawayGag extends Effect {
    /** The gag inflicted on the spectated victim. Most fire once; a few play out over the cutaway. */
    private enum Gag {
        CREEPER, ANVIL, GOLEM, WOOLIAM, DRIVEBY, DREAM, JUMPED, SNAIL, HOLE, ABDUCTION, LAUNCH,
        TOKYO_DRIFTING, PIED_PIPER, FAKE_TNT, AQUARIUM, I_LIKE_TRAINS, BOWLING, THE_BOUNCER, ANNOYING_MUSIC,
        BOUNCY, MARRIAGE, SPEED, HELICOPTER, PARADE
    }

    /** Parade: the pool of entity types that march by (a varied, mostly-passive lineup). */
    private static final net.minecraft.world.entity.EntityType<?>[] PARADE_TYPES = {
        net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.PIG,
        net.minecraft.world.entity.EntityType.SHEEP, net.minecraft.world.entity.EntityType.CHICKEN,
        net.minecraft.world.entity.EntityType.VILLAGER, net.minecraft.world.entity.EntityType.WOLF,
        net.minecraft.world.entity.EntityType.RABBIT, net.minecraft.world.entity.EntityType.CAT,
        net.minecraft.world.entity.EntityType.FOX, net.minecraft.world.entity.EntityType.GOAT,
        net.minecraft.world.entity.EntityType.PANDA, net.minecraft.world.entity.EntityType.ALLAY,
        net.minecraft.world.entity.EntityType.MOOSHROOM, net.minecraft.world.entity.EntityType.ARMADILLO,
        net.minecraft.world.entity.EntityType.POLAR_BEAR, net.minecraft.world.entity.EntityType.SNIFFER
    };

    /** Watcher UUID -> earliest tick a cutaway may next START (the hard cooldown). */
    private static final Map<UUID, Long> COOLDOWN_END = new HashMap<>();
    private static final Map<UUID, Long> END_TICK = new HashMap<>();
    private static final Map<UUID, SavedPos> SAVED = new HashMap<>();
    private static final Map<UUID, ActiveGag> GAG = new HashMap<>();
    /** Last gag each watcher got — the picker biases away from it so the same gag never fires twice running. */
    /** Watcher UUID -> the last few gags (newest first), so a gag can't repeat within RECENT_GAG_MEMORY. */
    private static final Map<UUID, java.util.Deque<Gag>> RECENT_GAGS = new HashMap<>();
    private static final int RECENT_GAG_MEMORY = 3;

    public CurseCutawayGag() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 90, () -> Items.SPYGLASS);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        if (target.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
            return java.util.Optional.of("cutting away now");
        }
        Long cd = COOLDOWN_END.get(target.getUUID());
        return java.util.Optional.of(cd != null && target.level().getGameTime() < cd ? "gag on cooldown" : "gag can trigger");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true; // you discover the first time you're yanked into a cutaway
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        setCooldown(target, target.level().getGameTime());
    }

    @Override
    public void onTick(ServerPlayer watcher, int ticksRemaining) {
        ServerLevel level = watcher.serverLevel();
        long now = level.getGameTime();
        UUID id = watcher.getUUID();

        // While a cutaway is in flight, the shared server-tick driver ({@link #driveActiveCutaway}) progresses
        // it — so don't drive it here too (that would double-tick the gag), and let it run even for a
        // debug-forced cutaway where the curse itself isn't applied.
        if (watcher.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
            return;
        }

        // Not mid-cutaway: after the hard cooldown, roll a low-but-ramping per-second chance.
        Long cd = COOLDOWN_END.get(id);
        if (cd == null) {
            setCooldown(watcher, now);
            return;
        }
        if (now >= cd && now % 20L == 0L) {
            double secs = (now - cd) / 20.0;
            double chance = Math.min(Config.CUTAWAY_MAX_CHANCE.get(),
                    Config.CUTAWAY_BASE_CHANCE.get() + Config.CUTAWAY_RAMP_PER_SECOND.get() * secs);
            if (watcher.getRandom().nextDouble() * 100.0 < chance) {
                startCutaway(watcher, now, null, false); // if nobody to watch, it just rolls again next second
            }
        }
    }

    /**
     * Progresses an in-flight cutaway. Called EVERY server tick (from {@code CurseEventHandler}) for any player
     * with {@code CUTAWAY_TARGET >= 0}, independent of whether the curse is applied — so a debug-forced cutaway
     * fires its gag and ends, and a cutaway whose curse was removed mid-flight still cleans up.
     */
    public static void driveActiveCutaway(ServerPlayer watcher, long now) {
        if (watcher.getData(WitchModAttachments.CUTAWAY_TARGET) < 0) {
            return;
        }
        ServerLevel level = watcher.serverLevel();
        UUID id = watcher.getUUID();

        SavedPos saved = SAVED.get(id);
        ActiveGag gag = GAG.get(id);
        if (saved == null || gag == null) {
            // Transient state lost (relog / world-reload). Teleport them HOME from the persisted return
            // position rather than stranding them at the overhead vantage, then clear.
            restoreFromReturnTag(watcher, level);
            clearSpectateState(watcher);
            setCooldown(watcher, now);
            return;
        }
        // If the victim died or logged off, the camera has nothing to frame — end it (no strobing).
        LivingEntity victim = level.getEntity(gag.victim) instanceof LivingEntity le ? le : null;
        if (victim == null || !victim.isAlive()) {
            endCutaway(watcher, now);
            return;
        }
        // Fire the gag once the 2–6s delay has elapsed, then let the ongoing ones play out. A misbehaving
        // gag must NEVER be able to abort the end-of-cutaway logic below — otherwise the watcher is stuck
        // spectating forever and looping sounds never stop — so the whole gag step is exception-guarded.
        try {
            if (!gag.started && now >= gag.gagStart) {
                startGag(level, watcher, victim, gag);
                gag.started = true;
            }
            if (gag.started) {
                tickGag(level, watcher, gag, now);
            }
        } catch (Exception e) {
            com.oliver.witchmod.WitchMod.LOGGER.error("[CutawayGag] gag tick failed; ending cutaway", e);
            endCutaway(watcher, now);
            return;
        }
        if (gag.endNow) {
            endCutaway(watcher, now);
            return;
        }
        watcher.setDeltaMovement(Vec3.ZERO); // hold the overhead vantage steady
        Long end = END_TICK.get(id);
        if (end == null || now >= end) {
            endCutaway(watcher, now);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        if (target.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
            endCutaway(target, target.level().getGameTime());
        }
        UUID id = target.getUUID();
        COOLDOWN_END.remove(id);
        END_TICK.remove(id);
        SAVED.remove(id);
        GAG.remove(id);
        RECENT_GAGS.remove(id);
    }

    /**
     * Death gate: dying mid-cutaway while the camera is hijacked strobes against the respawn screen, so snap
     * out of it immediately — WITHOUT teleporting the (now dead) body back, since respawn handles position.
     */
    public static void onWatcherDeath(ServerPlayer watcher) {
        if (watcher.getData(WitchModAttachments.CUTAWAY_TARGET) < 0) {
            return;
        }
        SavedPos saved = SAVED.get(watcher.getUUID());
        ActiveGag gag = GAG.get(watcher.getUUID());
        if (gag != null) {
            finishGag(watcher, gag);
        }
        discardTemps(watcher);
        clearSpectateState(watcher);
        if (saved != null) {
            watcher.setInvisible(saved.invisible);
            watcher.setNoGravity(saved.noGravity);
        }
        setCooldown(watcher, watcher.level().getGameTime());
    }

    @Override
    public List<String> debugArgs() {
        List<String> args = new ArrayList<>();
        args.add("villager");
        args.add("exit");
        for (Gag g : Gag.values()) {
            args.add(g.name().toLowerCase(java.util.Locale.ROOT));
        }
        // Marriage sub-paths (forcible via the same arg channel).
        args.add("object");
        args.add("explode");
        args.add("what");
        return args;
    }

    @Override
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        if (target.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
            endCutaway(target, target.level().getGameTime());
            return "Ended the active cutaway.";
        }
        // arg tokens: "villager" (cut away to the nearest VILLAGER, for solo testing) and/or a gag name.
        boolean targetVillager = false;
        Gag forced = null;
        if (arg != null && !arg.isBlank()) {
            for (String tok : arg.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
                if (tok.equals("exit") || tok.equals("off") || tok.isEmpty()) {
                    continue;
                }
                if (tok.equals("villager")) {
                    targetVillager = true;
                    continue;
                }
                // Marriage sub-paths: force which of the four weddings the marriage gag plays.
                int mp = switch (tok) {
                    case "normal", "kiss" -> MARRIAGE_NORMAL;
                    case "object", "objection", "objected" -> MARRIAGE_OBJECT;
                    case "explode", "explosion" -> MARRIAGE_EXPLODE;
                    case "what" -> MARRIAGE_WHAT;
                    default -> -1;
                };
                if (mp >= 0) {
                    forced = Gag.MARRIAGE;
                    forcedMarriagePath = mp;
                    continue;
                }
                try {
                    forced = Gag.valueOf(tok.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return "Unknown arg '" + tok + "'. Use 'villager' (solo test), a gag name, or a marriage "
                            + "path: normal / object / explode / what.";
                }
            }
        }
        if (startCutaway(target, target.level().getGameTime(), forced, targetVillager)) {
            return "Cutaway started" + (targetVillager ? " on nearest villager" : "")
                    + (forced != null ? " (" + forced.name().toLowerCase(java.util.Locale.ROOT) + ")" : "") + ".";
        }
        return targetVillager ? "No villager within 64 blocks to cut away to." : "No other player online to cut away to.";
    }

    // ---- cutaway lifecycle --------------------------------------------------------------------------

    private static void setCooldown(ServerPlayer watcher, long now) {
        COOLDOWN_END.put(watcher.getUUID(), now + Config.CUTAWAY_COOLDOWN_SECONDS.get() * 20L);
    }

    /** Uniform-random gag, but never the SAME as last time, and never Hole unless the ground suits it. */
    private static Gag pickGag(ServerLevel level, LivingEntity victim, java.util.Collection<Gag> recent, RandomSource rng) {
        boolean natural = naturalBelow(level, victim);
        // Bowling loves a crowd: the more entities clustered around the victim, the likelier it's picked — a
        // group makes for a proper set of pins.
        if (!recent.contains(Gag.BOWLING)) {
            int cluster = level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(6.0),
                    e -> e.isAlive() && e != victim).size();
            if (cluster >= Config.CUTAWAY_BOWLING_CLUSTER_MIN.get()
                    && rng.nextDouble() < Math.min(0.7, cluster * Config.CUTAWAY_BOWLING_CLUSTER_WEIGHT.get())) {
                return Gag.BOWLING;
            }
        }
        Gag gag = Gag.CREEPER;
        for (int tries = 0; tries < 24; tries++) {
            gag = Gag.values()[rng.nextInt(Gag.values().length)];
            if (gag == Gag.HOLE && !natural) {
                continue;
            }
            if (recent.contains(gag)) {
                continue; // bias away from the last few gags so the same ones don't keep coming up
            }
            return gag;
        }
        return gag == Gag.HOLE && !natural ? Gag.CREEPER : gag;
    }

    /**
     * Picks a victim + gag and relocates the watcher to an overhead vantage to spectate — the GAG itself is
     * deferred 2–6s (see {@link #onTick}), so you first watch the oblivious victim before it lands. False if
     * nobody to watch. {@code targetVillager} cuts away to the nearest villager instead of a random player,
     * so the curse can be tested solo (the "debug modes use villagers as stand-in players" pattern).
     */
    private static boolean startCutaway(ServerPlayer watcher, long now, @Nullable Gag forced, boolean targetVillager) {
        ServerLevel level = watcher.serverLevel();
        RandomSource rng = watcher.getRandom();
        LivingEntity victim;
        if (targetVillager) {
            victim = level.getEntitiesOfClass(Villager.class, watcher.getBoundingBox().inflate(64.0), Villager::isAlive)
                    .stream().min(java.util.Comparator.comparingDouble(watcher::distanceToSqr)).orElse(null);
            if (victim == null) {
                return false;
            }
        } else {
            List<ServerPlayer> candidates = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p != watcher && p.isAlive() && !p.isSpectator()) {
                    candidates.add(p);
                }
            }
            if (candidates.isEmpty()) {
                return false;
            }
            victim = candidates.get(rng.nextInt(candidates.size()));
        }

        java.util.Deque<Gag> recent = RECENT_GAGS.computeIfAbsent(watcher.getUUID(), k -> new java.util.ArrayDeque<>());
        Gag gag = forced != null ? forced : pickGag(level, victim, recent, rng);
        recent.addFirst(gag);
        while (recent.size() > RECENT_GAG_MEMORY) {
            recent.removeLast();
        }

        // Save where the watcher really is, then relocate them (invisible + gravity-less) to the vantage.
        SavedPos saved = new SavedPos(watcher.getX(), watcher.getY(), watcher.getZ(),
                watcher.getYRot(), watcher.getXRot(), watcher.isInvisible(), watcher.isNoGravity());
        SAVED.put(watcher.getUUID(), saved);
        // Persist the return position too, so a relog/world-reload mid-cutaway can teleport them home
        // instead of stranding them at the overhead vantage (the SAVED map above is transient).
        watcher.setData(WitchModAttachments.CUTAWAY_RETURN, saved.toTag(level.dimension()));

        Vec3 vantage = computeVantage(level, victim, rng);
        Vec3 centre = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        float[] look = lookAngles(vantage.add(0.0, watcher.getEyeHeight(), 0.0), centre);
        watcher.setInvisible(true);
        // Hold non-flyers in the air with noGravity. A FLYING player is already held by flight, and toggling
        // gravity on them was leaving their descent broken afterwards — so leave flyers' gravity alone.
        if (!watcher.getAbilities().flying) {
            watcher.setNoGravity(true);
        }
        watcher.teleportTo(level, vantage.x, vantage.y, vantage.z, look[0], look[1]);
        watcher.setDeltaMovement(Vec3.ZERO);
        watcher.hurtMarked = true;
        watcher.fallDistance = 0;
        watcher.onUpdateAbilities(); // re-sync flight cleanly so it isn't left in a stuck state

        int delMin = Config.CUTAWAY_GAG_DELAY_MIN_TICKS.get();
        int delMax = Math.max(delMin, Config.CUTAWAY_GAG_DELAY_MAX_TICKS.get());
        long gagStart = now + delMin + rng.nextInt(delMax - delMin + 1);
        int durMin = Config.CUTAWAY_DURATION_MIN_TICKS.get();
        int durMax = Math.max(durMin, Config.CUTAWAY_DURATION_MAX_TICKS.get());

        GAG.put(watcher.getUUID(), new ActiveGag(gag, victim.getUUID(), gagStart));
        END_TICK.put(watcher.getUUID(), gagStart + durMin + rng.nextInt(durMax - durMin + 1));
        watcher.setData(WitchModAttachments.CUTAWAY_TARGET, victim.getId());
        Curses.CUTAWAY_GAG.get().markDiscoveredByVictim(watcher);
        sendCutawayIntro(watcher, victim);
        return true;
    }

    /**
     * The Family-Guy "cutaway" camera-cut click. The title card itself ("Meanwhile…" + "with &lt;victim&gt;")
     * is drawn CLIENT-side as a cinematic overlay ({@code ClientCurseHandler} + the writable
     * {@code assets/witchmod/text/cutaway_titles.json}) so it sits over the letterbox bars and can't be hidden.
     */
    private static void sendCutawayIntro(ServerPlayer watcher, LivingEntity victim) {
        watcher.playNotifySound(com.oliver.witchmod.data.WitchModSounds.PACING_CLICK.get(), SoundSource.MASTER, 0.9F, 1.0F);
    }

    private static void endCutaway(ServerPlayer watcher, long now) {
        // Un-stick the camera + kill any looped SFX FIRST, so even if a step below throws the watcher is
        // never left permanently spectating with a helicopter/tractor-beam loop droning on.
        watcher.setData(WitchModAttachments.CUTAWAY_TARGET, -1);
        watcher.setData(WitchModAttachments.CUTAWAY_LOOP, -1); // stop any looped SFX (helicopter / tractor beam)
        watcher.setData(WitchModAttachments.CUTAWAY_LOOK, -1); // back to aiming at the victim next time

        ActiveGag gag = GAG.get(watcher.getUUID());
        if (gag != null) {
            try {
                finishGag(watcher, gag);
            } catch (Exception e) {
                com.oliver.witchmod.WitchMod.LOGGER.error("[CutawayGag] finishGag failed", e);
            }
        }
        SavedPos saved = SAVED.remove(watcher.getUUID());
        discardTemps(watcher);
        GAG.remove(watcher.getUUID());
        END_TICK.remove(watcher.getUUID());

        if (saved != null) {
            restoreTo(watcher, watcher.serverLevel(), saved, null);
        } else {
            // Transient SAVED gone (reload): fall back to the persisted return position if present.
            net.minecraft.nbt.CompoundTag ret = watcher.getData(WitchModAttachments.CUTAWAY_RETURN);
            if (ret != null && ret.contains("x")) {
                restoreTo(watcher, watcher.serverLevel(), SavedPos.fromTag(ret), null);
            } else {
                watcher.setInvisible(false);
                watcher.setNoGravity(false);
                watcher.onUpdateAbilities();
            }
        }
        watcher.setData(WitchModAttachments.CUTAWAY_RETURN, new net.minecraft.nbt.CompoundTag());
        setCooldown(watcher, now);
    }

    /** Clears the synced spectate target + server maps without teleporting (used on death, where respawn handles position). */
    private static void clearSpectateState(ServerPlayer watcher) {
        watcher.setData(WitchModAttachments.CUTAWAY_TARGET, -1);
        watcher.setData(WitchModAttachments.CUTAWAY_LOOP, -1);
        watcher.setData(WitchModAttachments.CUTAWAY_LOOK, -1);
        watcher.setData(WitchModAttachments.CUTAWAY_RETURN, new net.minecraft.nbt.CompoundTag());
        SAVED.remove(watcher.getUUID());
        GAG.remove(watcher.getUUID());
        END_TICK.remove(watcher.getUUID());
    }

    /** Removes the "slide/run away" gag entities (driveby, dream, the fake snail); real threats stay. */
    private static void discardTemps(ServerPlayer watcher) {
        ActiveGag gag = GAG.get(watcher.getUUID());
        if (gag != null) {
            for (int tempId : gag.temps) {
                var e = watcher.serverLevel().getEntity(tempId);
                if (e != null) {
                    e.discard();
                }
            }
        }
    }

    /** Called from the damage hook: a hit mid-cutaway has a high chance to snap the watcher back early. */
    public static void onWatcherHit(ServerPlayer watcher) {
        if (watcher.getData(WitchModAttachments.CUTAWAY_TARGET) < 0) {
            return;
        }
        if (watcher.getRandom().nextInt(100) < Config.CUTAWAY_HIT_END_CHANCE.get()) {
            endCutaway(watcher, watcher.level().getGameTime());
        }
    }

    /** Per-gag cleanup when a cutaway ends: clear placed blocks and undo lingering victim/entity state. */
    private static void finishGag(ServerPlayer watcher, ActiveGag gag) {
        ServerLevel level = watcher.serverLevel();
        for (BlockPos p : gag.placed) {
            if (level.getBlockState(p).is(Blocks.RAIL) || level.getBlockState(p).is(Blocks.RED_CARPET)) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        LivingEntity victim = level.getEntity(gag.victim) instanceof LivingEntity le ? le : null;
        switch (gag.gag) {
            case AQUARIUM -> {
                for (int id : gag.flock) {
                    if (level.getEntity(id) != null) {
                        level.getEntity(id).setNoGravity(false); // they drop out of the air and flop like normal
                    }
                }
            }
            case GOLEM -> {
                for (int id : gag.flock) {
                    if (level.getEntity(id) instanceof LivingEntity golem) {
                        golem.removeEffect(MobEffects.MOVEMENT_SPEED);
                        golem.removeEffect(MobEffects.DAMAGE_BOOST); // back to a plain (still aggro'd) golem
                    }
                }
            }
            case ANNOYING_MUSIC -> watcher.setData(WitchModAttachments.CUTAWAY_MUSIC_END, Long.MIN_VALUE);
            case BOUNCY -> {
                if (victim instanceof ServerPlayer p && gag.priorBouncy != -2) {
                    p.setData(WitchModAttachments.BOUNCY_ACTIVE, gag.priorBouncy);
                }
            }
            case MARRIAGE -> {
                watcher.setData(WitchModAttachments.CUTAWAY_LOOK, -1);
                if (victim != null) {
                    victim.setInvulnerable(false);
                    victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    victim.removeEffect(MobEffects.JUMP);
                    stopWedding(level, victim); // the wedding's over — cut the music
                }
            }
            case PARADE -> {
                if (victim != null) {
                    stopParade(level, victim); // the parade's over — cut the drum/march track
                }
            }
            default -> { /* nothing extra */ }
        }
    }

    /** Spawns the train (once) after the "I like trains" warning finishes, and plays the roll sound at it. */
    private static void spawnTrainCarts(ServerLevel level, LivingEntity victim, ActiveGag active) {
        RandomSource rng = level.random;
        EntityType<?>[] riders = {EntityType.VILLAGER, EntityType.COW};
        Vec3 dir = active.heliDir;
        // Anchor line the carts are pinned to (keeps them dead on the spawned rails).
        active.trainAlongX = dir.x != 0;
        active.trainY = victim.getY() + 0.3;
        active.trainPerp = active.trainAlongX ? victim.getZ() : victim.getX();
        Vec3 firstSpawn = victim.position().subtract(dir.scale(9.0));
        for (int i = 0; i < Config.CUTAWAY_TRAIN_COUNT.get(); i++) {
            Vec3 spawn = victim.position().subtract(dir.scale(9 + i * 2.0));
            var cart = new net.minecraft.world.entity.vehicle.Minecart(level, spawn.x, victim.getY() + 0.3, spawn.z);
            cart.noPhysics = true;
            level.addFreshEntity(cart);
            var rider = riders[rng.nextInt(riders.length)].spawn(level, BlockPos.containing(spawn), MobSpawnType.EVENT);
            if (rider != null) {
                rider.startRiding(cart, true);
                active.temps.add(rider.getId());
            }
            active.temps.add(cart.getId());
            active.flock.add(cart.getId());
        }
        level.playSound(null, BlockPos.containing(firstSpawn), com.oliver.witchmod.data.WitchModSounds.CUTAWAY_TRAIN_ROLL.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private static void trainHit(ServerLevel level, LivingEntity victim, net.minecraft.world.entity.Entity cart) {
        victim.hurt(com.oliver.witchmod.data.WitchModDamageTypes.train(level), (float) (double) Config.CUTAWAY_TRAIN_DAMAGE.get());
        Vec3 push = victim.position().subtract(cart.position()).normalize().scale(2.6).add(0.0, 1.0, 0.0);
        victim.setDeltaMovement(push);
        victim.hurtMarked = true;
        level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
        cart.discard();
    }

    /** 50% chance to queue another bowl (up to 4 total), 1–3s after the current one resolves (hit or miss). */
    private static void rollNextBowl(ActiveGag active, long elapsed, RandomSource rng) {
        if (active.bowlCount < 4 && rng.nextInt(100) < 50) {
            active.nextBowlTick = (int) elapsed + 20 + rng.nextInt(41);
        }
    }

    /** Hurls a (noclip) black-concrete "bowling ball" at the victim; counts toward the max of 4 bowls. */
    private static void throwBowlingBall(ServerLevel level, LivingEntity victim, ActiveGag active) {
        Vec3 from = ringPos(victim, 10.0, level.random).getCenter();
        var ball = FallingBlockEntity.fall(level, BlockPos.containing(from), Blocks.BLACK_CONCRETE.defaultBlockState());
        ball.setNoGravity(true);
        ball.noPhysics = true; // never stopped by terrain — it would never connect otherwise
        ball.time = 1;
        ball.setDeltaMovement(victim.position().add(0, 0.5, 0).subtract(from).normalize().scale(1.4));
        active.temps.add(ball.getId());
        active.flock.add(ball.getId());
        active.bowlCount++;
        level.playSound(null, BlockPos.containing(from), com.oliver.witchmod.data.WitchModSounds.CUTAWAY_BALL_THROW.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** Bowl over ONE pin (any entity the ball passes through) — a real scatter-launch in the ball's direction. */
    private static void bowlPin(ServerLevel level, net.minecraft.world.entity.Entity ball, LivingEntity pin, Vec3 dir) {
        pin.hurt(com.oliver.witchmod.data.WitchModDamageTypes.bowling(level), (float) (double) Config.CUTAWAY_BOWLING_DAMAGE.get());
        // Pins SCATTER — knocked forward along the ball's travel + up, like a struck bowling pin.
        Vec3 launch = dir.scale(1.3).add((level.random.nextDouble() - 0.5) * 0.4, 0.55, (level.random.nextDouble() - 0.5) * 0.4);
        pin.setDeltaMovement(pin.getDeltaMovement().add(launch));
        pin.hurtMarked = true;
        pin.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3)); // brief stun
        pin.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
        level.playSound(null, pin.blockPosition(), com.oliver.witchmod.data.WitchModSounds.CUTAWAY_BOWLING_STRIKE.get(),
                SoundSource.HOSTILE, 1.0F, 0.9F + level.random.nextFloat() * 0.3F);
    }

    /**
     * The Bouncer speaks with the EXACT Bodyguard blessing voice — same {@code bodyguard.json} line pool,
     * same {@code <Bodyguard> …} plain format, same {@link Config#BODYGUARD_CHAT_RADIUS} — so it reads as the
     * same character rather than a separate bespoke set.
     */
    private static void bouncerLine(ServerLevel level, LivingEntity victim, RandomSource rng) {
        String key = rng.nextBoolean() ? "warning" : "aggression";
        if (!com.oliver.witchmod.data.BodyguardLines.has(key)) {
            key = "warning";
        }
        java.util.List<String> tree = com.oliver.witchmod.data.BodyguardLines.pickTree(key, rng);
        if (tree.isEmpty()) {
            return;
        }
        Component message = Component.literal("<Bodyguard> "
                + tree.get(rng.nextInt(tree.size())).replace("{player}", victimName(victim)));
        double radius = Config.BODYGUARD_CHAT_RADIUS.get();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(victim) < radius * radius) {
                p.sendSystemMessage(message);
            }
        }
    }

    private static void announce(ServerLevel level, LivingEntity victim, String msg) {
        Component c = Component.literal(msg);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(victim) < 48.0 * 48.0) {
                p.sendSystemMessage(c);
            }
        }
    }

    private static String victimName(LivingEntity victim) {
        return victim.getName().getString();
    }

    // ==== Marriage (four comical paths + cinematic camera work) ========================================
    // Lines live in data/witchmod/text/marriage.json (via MarriageLines). The spectate camera aims at the
    // victim by default, or at CUTAWAY_LOOK when a path wants to frame something else (objector / spouse);
    // its POSITION is the watcher's body, teleported around each tick for dolly / cut / orbit shots.

    static final int MARRIAGE_NORMAL = 0, MARRIAGE_OBJECT = 1, MARRIAGE_EXPLODE = 2, MARRIAGE_WHAT = 3;
    /** A debug-forced path for the NEXT marriage (set from {@code debugForce}), or -1 to roll randomly. */
    private static int forcedMarriagePath = -1;

    private static void startMarriage(ServerLevel level, ServerPlayer watcher, LivingEntity victim, ActiveGag active, RandomSource rng) {
        victim.setInvulnerable(true);
        // ⚠ NO Jump effect — amp 128 is +12.9 jump power (a launch pad), not "no jump". Slowness clamps them;
        // tickMarriage pins them in place every tick.
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 32000, 250, false, false));

        Vec3 look = victim.getLookAngle();
        Vec3 aisle = new Vec3(look.x, 0.0, look.z);
        aisle = aisle.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : aisle.normalize();
        active.marriageAisle = aisle;
        Vec3 perp = new Vec3(aisle.z, 0.0, -aisle.x); // sideways across the aisle (for guest seating / the officiant)

        // Red-carpet aisle under the couple.
        boolean alongX = Math.abs(aisle.x) >= Math.abs(aisle.z);
        BlockPos foot = victim.blockPosition();
        for (int d = -1; d <= 4; d++) {
            BlockPos p = alongX ? foot.offset(d, 0, 0) : foot.offset(0, 0, d);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                level.setBlockAndUpdate(p, Blocks.RED_CARPET.defaultBlockState());
                active.placed.add(p);
            }
        }

        // The spouse: usually a villager, but comically sometimes a random creature.
        Vec3 spouseAt = victim.position().add(aisle.scale(2.5));
        EntityType<?> spouseType = EntityType.VILLAGER;
        if (rng.nextInt(100) < 35) {
            EntityType<?>[] silly = {EntityType.PIG, EntityType.COW, EntityType.CHICKEN, EntityType.MOOSHROOM,
                    EntityType.ZOMBIE_VILLAGER, EntityType.SNIFFER, EntityType.PUFFERFISH, EntityType.SLIME};
            spouseType = silly[rng.nextInt(silly.length)];
        }
        String name = com.oliver.witchmod.data.SolicitorLines.randomName(rng);
        var spouse = spouseType.spawn(level, BlockPos.containing(spouseAt), MobSpawnType.EVENT);
        if (spouse instanceof Mob m) {
            m.setCustomName(Component.literal(name));
            m.setInvulnerable(true);
            m.setNoAi(true);
            m.setPos(spouseAt.x, victim.getY(), spouseAt.z);
            m.setYRot((float) (Mth.atan2(aisle.x, -aisle.z) * (180.0 / Math.PI))); // face back toward the bride/groom
            m.yHeadRot = m.getYRot();
            m.yBodyRot = m.getYRot();
            active.temps.add(m.getId());
            active.marriageSpouseId = m.getId();
        }

        // The officiant: a villager standing to one side of the couple, facing across the aisle.
        Vec3 offAt = victim.position().add(aisle.scale(1.25)).add(perp.scale(1.8));
        var officiant = EntityType.VILLAGER.spawn(level, BlockPos.containing(offAt), MobSpawnType.EVENT);
        if (officiant != null) {
            officiant.setCustomName(Component.literal("Officiant"));
            officiant.setInvulnerable(true);
            officiant.setNoAi(true);
            officiant.setPos(offAt.x, victim.getY(), offAt.z);
            officiant.setYRot((float) (Mth.atan2(perp.x, -perp.z) * (180.0 / Math.PI))); // look across the aisle at the couple
            officiant.yHeadRot = officiant.getYRot();
            officiant.yBodyRot = officiant.getYRot();
            active.temps.add(officiant.getId());
            active.marriageOfficiantId = officiant.getId();
        }

        // Guests seated down both sides of the aisle.
        EntityType<?>[] guestTypes = {EntityType.VILLAGER, EntityType.VILLAGER, EntityType.VILLAGER,
                EntityType.ALLAY, EntityType.CAT, EntityType.WANDERING_TRADER};
        for (int side = -1; side <= 1; side += 2) {
            for (int row = 0; row < 3; row++) {
                Vec3 g = victim.position().add(aisle.scale(-0.6 + row * 1.5)).add(perp.scale(side * 2.7));
                EntityType<?> gt = guestTypes[rng.nextInt(guestTypes.length)];
                var guest = gt.spawn(level, BlockPos.containing(g), MobSpawnType.EVENT);
                if (guest instanceof Mob gm) {
                    gm.setInvulnerable(true);
                    gm.setNoAi(true);
                    gm.setPos(g.x, victim.getY(), g.z);
                    gm.setYRot((float) (Mth.atan2(-side * perp.x, side * perp.z) * (180.0 / Math.PI))); // face inward
                    gm.yHeadRot = gm.getYRot();
                    gm.yBodyRot = gm.getYRot();
                    active.temps.add(gm.getId());
                    active.marriageGuests.add(gm.getId());
                }
            }
        }

        // Ceremony script (open / vows / pronounce) from the editable list.
        active.marriageScript.add(com.oliver.witchmod.data.MarriageLines.pick("open", rng, "We are gathered here today..."));
        active.marriageScript.add(com.oliver.witchmod.data.MarriageLines.pick("vows", rng, "Do you, {v}, take {s}?")
                .replace("{v}", victimName(victim)).replace("{s}", name));
        active.marriageScript.add(com.oliver.witchmod.data.MarriageLines.pick("pronounce", rng, "I now pronounce you married!")
                .replace("{v}", victimName(victim)).replace("{s}", name));

        // Which of the four weddings is this? (debug can force it.)
        active.marriagePath = forcedMarriagePath >= 0 ? forcedMarriagePath : rng.nextInt(4);
        forcedMarriagePath = -1;

        // Give the ceremony room to play out. Each path needs AT LEAST enough ticks for its last scripted beat
        // (+ a tail), so we floor to that regardless of the configured base — otherwise a small/stale config
        // value cuts a path off before its event ever fires (explode/what were ending abruptly at base 260).
        // The config value can only EXTEND a path, never shorten it below what its choreography requires.
        int base = Config.CUTAWAY_MARRIAGE_TICKS.get();
        int total = switch (active.marriagePath) {
            case MARRIAGE_OBJECT -> Math.max(base, 720);   // record-scratch 300 … wedding OFF 665 … drift
            case MARRIAGE_EXPLODE -> Math.max(base, 480);  // creep 300 … BOOM 385 … drift
            case MARRIAGE_WHAT -> Math.max(base, 440);     // "what" 300 … flung/strider 355 … "anyway" 405 … drift
            default -> Math.max(base, 500);                // pronounce 300 … kiss 430 … orbit
        };
        END_TICK.put(watcher.getUUID(), active.gagStart + total);

        marriageSay(level, watcher, victim, "§d❤ A wedding! §f" + victimName(victim) + " §d& §f" + name + " §d❤");
        level.playSound(null, victim.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 0.8F, 1.0F);
        // The wedding MUSIC — played ONCE at the ceremony (it outlasts the scene), heard by everyone nearby
        // (incl. the spectating watcher). Stopped abruptly when an absurd event fires or the wedding ends.
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                com.oliver.witchmod.data.WitchModSounds.CUTAWAY_WEDDING.get(), SoundSource.RECORDS, 1.0F, 1.0F);
    }

    /** Cuts the wedding music dead for everyone who could hear it (an absurd twist, or the ceremony ending). */
    private static void stopWedding(ServerLevel level, LivingEntity victim) {
        var id = com.oliver.witchmod.data.WitchModSounds.CUTAWAY_WEDDING.get().getLocation();
        var pkt = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(id, SoundSource.RECORDS);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(victim) < 64.0 * 64.0) {
                p.connection.send(pkt);
            }
        }
    }

    /** Cuts BOTH parade tracks (drums + march) dead when the gag ends. */
    private static void stopParade(ServerLevel level, LivingEntity victim) {
        var drum = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                com.oliver.witchmod.data.WitchModSounds.CUTAWAY_PARADE_DRUM.get().getLocation(), SoundSource.RECORDS);
        var march = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                com.oliver.witchmod.data.WitchModSounds.CUTAWAY_PARADE_MARCH.get().getLocation(), SoundSource.RECORDS);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(victim) < 64.0 * 64.0) {
                p.connection.send(drum);
                p.connection.send(march);
            }
        }
    }

    /** Sends a marriage line to the spectating watcher AND anyone nearby the ceremony. */
    private static void marriageSay(ServerLevel level, ServerPlayer watcher, LivingEntity victim, String msg) {
        Component c = Component.literal(msg);
        watcher.sendSystemMessage(c);
        for (ServerPlayer p : level.players()) {
            if (p != watcher && p.distanceToSqr(victim) < 48.0 * 48.0) {
                p.sendSystemMessage(c);
            }
        }
    }

    private static Vec3 rotY(Vec3 v, double deg) {
        double r = Math.toRadians(deg);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new Vec3(v.x * c - v.z * s, 0.0, v.x * s + v.z * c);
    }

    /** Point the camera (the watcher's body) at an orbit position around {@code focus}: dist/height out, angle° around the aisle. */
    private static void camShot(ActiveGag a, Vec3 focus, double dist, double height, double angleDeg, double lerp) {
        Vec3 base = rotY(a.marriageAisle.scale(-1.0), angleDeg); // -aisle = in front of the bride/groom's face
        base = base.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : base.normalize();
        a.camTarget = focus.add(base.x * dist, height, base.z * dist);
        a.camLerp = lerp;
    }

    /** Eases the watcher's body toward {@code camTarget} each tick — a hard cut (lerp≥1) or a smooth push. */
    private static void applyCam(ServerLevel level, ServerPlayer watcher, ActiveGag a) {
        if (a.camTarget == null) {
            return;
        }
        Vec3 cur = watcher.position();
        Vec3 next = a.camLerp >= 1.0 ? a.camTarget : cur.lerp(a.camTarget, a.camLerp);
        watcher.teleportTo(level, next.x, next.y, next.z, watcher.getYRot(), watcher.getXRot());
        watcher.setDeltaMovement(Vec3.ZERO);
        watcher.hurtMarked = true;
        watcher.fallDistance = 0;
    }

    /** Officiant line, tagged in grey. */
    private static void officiantSay(ServerLevel level, ServerPlayer watcher, LivingEntity victim, String line) {
        marriageSay(level, watcher, victim, "§7[Officiant] " + line);
    }

    /** Cherry-blossom confetti drifting down over the aisle, plus the odd rising heart. */
    private static void marriagePetals(ServerLevel level, LivingEntity victim, ActiveGag a, Vec3 couple) {
        Vec3 along = a.marriageAisle.scale((level.random.nextDouble() - 0.3) * 4.0);
        Vec3 side = new Vec3(a.marriageAisle.z, 0, -a.marriageAisle.x).scale((level.random.nextDouble() - 0.5) * 5.0);
        double px = victim.getX() + along.x + side.x;
        double pz = victim.getZ() + along.z + side.z;
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, px, victim.getY() + 3.2, pz, 1, 0.2, 0.0, 0.2, 0.0);
        if (level.random.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.HEART, couple.x, couple.y + 0.5, couple.z, 2, 0.6, 0.5, 0.6, 0.0);
        }
    }

    /** Guests react: a little hop + a sound (cheer or gasp). */
    private static void guestsReact(ServerLevel level, ActiveGag a, boolean hop, net.minecraft.sounds.SoundEvent sound, float pitch) {
        for (int id : a.marriageGuests) {
            if (level.getEntity(id) instanceof LivingEntity g) {
                if (hop) {
                    g.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.42, (level.random.nextDouble() - 0.5) * 0.1);
                    g.hurtMarked = true;
                }
                if (level.random.nextInt(3) == 0) {
                    level.playSound(null, g.blockPosition(), sound, SoundSource.HOSTILE, 0.7F, pitch + (level.random.nextFloat() - 0.5F) * 0.2F);
                }
            }
        }
    }

    private static void tickMarriage(ServerLevel level, ServerPlayer watcher, LivingEntity victim, ActiveGag a, long elapsed) {
        // Pin the bride/groom at the altar (kill all motion, incl. Y).
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        victim.fallDistance = 0;
        LivingEntity spouse = a.marriageSpouseId >= 0 && level.getEntity(a.marriageSpouseId) instanceof LivingEntity s ? s : null;
        Vec3 couple = spouse != null
                ? victim.position().add(spouse.position()).scale(0.5).add(0.0, 1.0, 0.0)
                : victim.position().add(0.0, 1.0, 0.0);

        // --- shared intro (all four paths), deliberately slow and drawn-out ---
        if (elapsed == 0) {
            camShot(a, couple, 8.5, 4.6, 0, 1.0);      // wide, high, holds a long beat
        } else if (elapsed == 45) {
            camShot(a, couple, 5.8, 2.8, 8, 0.022);    // begin a very slow push-in
            officiantSay(level, watcher, victim, a.marriageScript.isEmpty() ? "Dearly beloved..." : a.marriageScript.get(0));
        } else if (elapsed == 135) {
            camShot(a, couple, 4.6, 1.5, 72, 0.028);   // ease to a side profile
        } else if (elapsed == 205) {
            if (a.marriageScript.size() > 1) {
                officiantSay(level, watcher, victim, a.marriageScript.get(1));
            }
        } else if (elapsed == 265) {
            camShot(a, couple, 3.4, 1.9, 152, 0.035);  // over-the-shoulder toward the moment
        }

        switch (a.marriagePath) {
            case MARRIAGE_OBJECT -> tickMarriageObject(level, watcher, victim, spouse, couple, a, elapsed);
            case MARRIAGE_EXPLODE -> tickMarriageExplode(level, watcher, victim, spouse, couple, a, elapsed);
            case MARRIAGE_WHAT -> tickMarriageWhat(level, watcher, victim, spouse, couple, a, elapsed);
            default -> tickMarriageNormal(level, watcher, victim, spouse, couple, a, elapsed);
        }

        // Falling petals throughout (except once the explode path has soured the mood).
        if (elapsed % 2 == 0 && !(a.marriagePath == MARRIAGE_EXPLODE && elapsed >= 240)) {
            marriagePetals(level, victim, a, couple);
        }
        applyCam(level, watcher, a);
    }

    /** Path 0 — a lovely wedding: pronounce, romantic zoom, kiss + fanfare, guests cheer, slow orbit of the newlyweds. */
    private static void tickMarriageNormal(ServerLevel level, ServerPlayer watcher, LivingEntity victim, @Nullable LivingEntity spouse, Vec3 couple, ActiveGag a, long elapsed) {
        if (elapsed == 300) {
            camShot(a, couple, 2.6, 1.7, 15, 0.03);    // slow push toward a two-shot
            if (a.marriageScript.size() > 2) {
                officiantSay(level, watcher, victim, a.marriageScript.get(2));
            }
            level.playSound(null, victim.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
        } else if (elapsed == 375) {
            camShot(a, couple, 2.0, 1.7, 20, 0.04);    // creep closer for the kiss
            officiantSay(level, watcher, victim, "You may kiss...");
        } else if (elapsed == 430) {
            camShot(a, couple, 1.7, 1.7, 24, 1.0);     // hard cut to an extreme close-up
            marriageSay(level, watcher, victim, com.oliver.witchmod.data.MarriageLines.pick("kiss", level.random,
                    "§d💍 They kissed! {v} & {s} are married! 💍")
                    .replace("{v}", victimName(victim)).replace("{s}", spouseName(spouse)));
            level.sendParticles(ParticleTypes.HEART, couple.x, couple.y + 0.5, couple.z, 60, 0.7, 0.7, 0.7, 0.15);
            level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 1.0F, 1.4F);
            level.playSound(null, victim.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.0F, 1.5F);
            guestsReact(level, a, true, SoundEvents.VILLAGER_CELEBRATE, 1.1F);
            if (spouse != null) { // a little lean-in peck
                spouse.setDeltaMovement(victim.position().subtract(spouse.position()).normalize().scale(0.12));
                spouse.hurtMarked = true;
            }
        } else if (elapsed >= 445 && elapsed % 3 == 0) {
            camShot(a, couple, 2.8, 1.6, 24 + (elapsed - 445) * 0.7, 0.10); // slow celebratory orbit
            if (elapsed % 45 == 0) {
                guestsReact(level, a, true, SoundEvents.VILLAGER_YES, 1.1F);
            }
        }
    }

    /** Path 1 — OBJECTION: a player-mimic bursts in — slow, drawn-out, readable drama — and the wedding is called off. */
    private static void tickMarriageObject(ServerLevel level, ServerPlayer watcher, LivingEntity victim, @Nullable LivingEntity spouse, Vec3 couple, ActiveGag a, long elapsed) {
        RandomSource rng = level.random;
        if (elapsed == 300) {
            // Mid-sentence — the record-scratch beat. Officiant trails off; long ominous hold.
            camShot(a, couple, 2.6, 0.6, 0, 1.0);      // low-hero on the couple
            if (a.marriageScript.size() > 2) {
                officiantSay(level, watcher, victim, a.marriageScript.get(2) + "§7...");
            }
            level.playSound(null, victim.blockPosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.HOSTILE, 1.0F, 0.5F);
        } else if (elapsed == 350) {
            // BURST IN — the doors fly open, from way down the aisle.
            LivingEntity objector = spawnObjector(level, victim, a, rng);
            String oName = objector != null ? objector.getName().getString() : "Someone";
            stopWedding(level, victim); // an absurd twist — the music cuts dead
            marriageSay(level, watcher, victim, "§4§l" + oName + ": I OBJECT!!!");
            level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.8F);
            level.playSound(null, victim.blockPosition(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.HOSTILE, 1.2F, 1.0F);
            guestsReact(level, a, false, SoundEvents.VILLAGER_NO, 0.8F);
            if (objector != null) {
                watcher.setData(WitchModAttachments.CUTAWAY_LOOK, objector.getId()); // frame the objector
                aimCameraAtObjector(a, objector, couple);
                a.camLerp = 1.0; // hard cut
            }
        } else if (elapsed > 350 && elapsed < 665) {
            // While the camera is ON the objector, ease toward its face and let it stride slowly in.
            LivingEntity objector = a.marriageObjectorId >= 0 && level.getEntity(a.marriageObjectorId) instanceof LivingEntity o ? o : null;
            if (objector != null && watcher.getData(WitchModAttachments.CUTAWAY_LOOK) == objector.getId()) {
                Vec3 toCouple = couple.subtract(objector.position());
                toCouple = new Vec3(toCouple.x, 0, toCouple.z);
                if (toCouple.lengthSqr() > 9.0) { // stride in until ~3 blocks away
                    Vec3 step = toCouple.normalize().scale(0.03); // a slow, menacing approach
                    objector.setPos(objector.getX() + step.x, objector.getY(), objector.getZ() + step.z);
                    objector.setYRot((float) (Mth.atan2(-toCouple.x, toCouple.z) * (180.0 / Math.PI)));
                    objector.yHeadRot = objector.getYRot();
                    objector.yBodyRot = objector.getYRot();
                }
                aimCameraAtObjector(a, objector, couple);
                a.camLerp = 0.06; // very gentle push toward the face
            }
            // Beats ~50+ ticks apart so every line + cut is readable.
            if (elapsed == 405 || elapsed == 505 || elapsed == 620) { // objector lines (on its shots)
                LivingEntity o = a.marriageObjectorId >= 0 && level.getEntity(a.marriageObjectorId) instanceof LivingEntity oo ? oo : null;
                if (o != null) {
                    marriageSay(level, watcher, victim, "§c" + o.getName().getString() + ": "
                            + com.oliver.witchmod.data.MarriageLines.pick("objector", rng, "You were supposed to marry ME!"));
                    level.playSound(null, o.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.0F, 0.9F);
                }
            } else if (elapsed == 455 || elapsed == 565) { // cut to the couple's shocked reaction (held)
                watcher.setData(WitchModAttachments.CUTAWAY_LOOK, -1);
                camShot(a, couple, 2.3, 1.5, 20, 1.0);
                guestsReact(level, a, false, SoundEvents.VILLAGER_AMBIENT, 0.7F);
                if (spouse != null) {
                    spouse.setDeltaMovement(0, 0.3, 0); // a little shocked hop
                    spouse.hurtMarked = true;
                }
            } else if (elapsed == 500 || elapsed == 590) { // cut back to the objector (just before its next line)
                LivingEntity o = a.marriageObjectorId >= 0 && level.getEntity(a.marriageObjectorId) instanceof LivingEntity oo ? oo : null;
                if (o != null) {
                    watcher.setData(WitchModAttachments.CUTAWAY_LOOK, o.getId());
                    aimCameraAtObjector(a, o, couple);
                    a.camLerp = 1.0;
                }
            }
        } else if (elapsed == 665) {
            // The wedding is OFF. Spouse bolts; camera pulls back to the wreckage.
            watcher.setData(WitchModAttachments.CUTAWAY_LOOK, -1);
            camShot(a, couple, 5.2, 2.6, 30, 1.0);
            marriageSay(level, watcher, victim, com.oliver.witchmod.data.MarriageLines.pick("objected", rng,
                    "§eThe wedding is OFF! {s} runs after their true love...").replace("{v}", victimName(victim)).replace("{s}", spouseName(spouse)));
            guestsReact(level, a, false, SoundEvents.VILLAGER_NO, 0.9F);
            if (spouse != null && level.getEntity(a.marriageSpouseId) instanceof Mob sm) {
                sm.setNoAi(false);
                sm.setDeltaMovement(a.marriageAisle.scale(1.3).add(0, 0.4, 0));
                sm.hurtMarked = true;
            }
            LivingEntity objector = a.marriageObjectorId >= 0 && level.getEntity(a.marriageObjectorId) instanceof LivingEntity o ? o : null;
            if (objector != null) {
                level.sendParticles(ParticleTypes.HEART, objector.getX(), objector.getY() + 1.8, objector.getZ(), 12, 0.4, 0.4, 0.4, 0.0);
            }
        } else if (elapsed > 665 && elapsed % 3 == 0) {
            camShot(a, couple, 5.2, 2.6, 30 + (elapsed - 665) * 0.6, 0.08); // slow drift over the aftermath
        }
    }

    /** Path 2 — EXPLODE: the spouse just... goes off. Slight world damage, and a deadpan death line. */
    private static void tickMarriageExplode(ServerLevel level, ServerPlayer watcher, LivingEntity victim, @Nullable LivingEntity spouse, Vec3 couple, ActiveGag a, long elapsed) {
        if (elapsed == 300 && spouse != null) {
            stopWedding(level, victim); // the mood's about to turn — kill the music
            watcher.setData(WitchModAttachments.CUTAWAY_LOOK, a.marriageSpouseId); // zoom on the doomed spouse
            camShot(a, spouse.position().add(0, spouse.getBbHeight() * 0.5, 0), 2.4, 1.0, 10, 1.0);
            a.camLerp = 0.04; // ominous slow creep in
            level.playSound(null, spouse.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 0.8F);
        } else if (elapsed > 300 && elapsed < 385 && spouse != null) {
            camShot(a, spouse.position().add(0, spouse.getBbHeight() * 0.5, 0), 2.4, 1.0, 10, 0.05);
            level.sendParticles(ParticleTypes.SMOKE, spouse.getX(), spouse.getY() + 0.8, spouse.getZ(),
                    1 + (int) ((elapsed - 300) / 20), 0.3, 0.4, 0.3, 0.01);
            if (elapsed % 10 == 0) {
                level.playSound(null, spouse.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 0.4F, 1.6F);
            }
        } else if (elapsed == 385) {
            String sName = spouseName(spouse);
            watcher.setData(WitchModAttachments.CUTAWAY_LOOK, -1); // cut back to the victim for the aftermath
            camShot(a, couple, 5.0, 2.4, 0, 1.0);
            if (spouse != null) {
                double x = spouse.getX();
                double y = spouse.getY() + 0.3;
                double z = spouse.getZ();
                spouse.discard();
                a.marriageSpouseId = -1;
                level.explode(null, x, y, z, (float) (double) Config.CUTAWAY_MARRIAGE_EXPLODE_POWER.get(),
                        net.minecraft.world.level.Level.ExplosionInteraction.MOB);
            }
            marriageSay(level, watcher, victim, "§c" + sName + " exploded for some reason");
            guestsReact(level, a, true, SoundEvents.VILLAGER_NO, 0.7F);
        } else if (elapsed > 385 && elapsed % 3 == 0) {
            camShot(a, couple, 5.0, 2.4, (elapsed - 385) * 0.5, 0.07); // slow drift over the smoking aftermath
        }
    }

    /** Path 3 — "what": the ceremony line becomes just "what", and the spouse is flung away or turns into a strider. */
    private static void tickMarriageWhat(ServerLevel level, ServerPlayer watcher, LivingEntity victim, @Nullable LivingEntity spouse, Vec3 couple, ActiveGag a, long elapsed) {
        if (elapsed == 300) {
            camShot(a, couple, 3.0, 1.7, 6, 1.0);      // deadpan flat medium shot, dead still
            stopWedding(level, victim); // deadpan — the music just stops
            marriageSay(level, watcher, victim, "§7[Officiant] what");
            level.playSound(null, victim.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.0F);
        } else if (elapsed == 355) {
            if (spouse != null && level.getEntity(a.marriageSpouseId) instanceof Mob sm) {
                if (level.random.nextBoolean()) { // flung away
                    marriageSay(level, watcher, victim, "§7what");
                    sm.setNoAi(false);
                    sm.setDeltaMovement((level.random.nextDouble() - 0.5) * 2.4, 1.1, (level.random.nextDouble() - 0.5) * 2.4);
                    sm.hurtMarked = true;
                    level.playSound(null, sm.blockPosition(), SoundEvents.SHULKER_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.2F);
                } else { // becomes a strider
                    marriageSay(level, watcher, victim, "§7...what");
                    Vec3 at = sm.position();
                    String nm = sm.getName().getString();
                    sm.discard();
                    a.marriageSpouseId = -1;
                    var strider = EntityType.STRIDER.spawn(level, BlockPos.containing(at), MobSpawnType.EVENT);
                    if (strider != null) {
                        strider.setCustomName(Component.literal(nm));
                        strider.setInvulnerable(true);
                        strider.setNoAi(true);
                        strider.setPos(at.x, at.y, at.z);
                        a.temps.add(strider.getId());
                        a.marriageSpouseId = strider.getId();
                    }
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.STRIDER_HAPPY, SoundSource.HOSTILE, 1.0F, 1.0F);
                    level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.6, at.z, 20, 0.4, 0.4, 0.4, 0.02);
                }
            }
        } else if (elapsed == 405) {
            marriageSay(level, watcher, victim, "§7[Officiant] anyway");
        } else if (elapsed >= 420 && elapsed % 5 == 0) {
            camShot(a, couple, 3.4, 1.7, 6 + (elapsed - 420) * 0.4, 0.07);
        }
    }

    /** Places the objector player-mimic (Dream entity, or a villager fallback) down the aisle in front of the couple. */
    @Nullable
    private static LivingEntity spawnObjector(ServerLevel level, LivingEntity victim, ActiveGag a, RandomSource rng) {
        Vec3 at = victim.position().add(a.marriageAisle.scale(7.0)); // burst in from down the aisle, a longer dramatic walk-in
        BlockPos pos = BlockPos.containing(at);
        String name = mimicName(level, victim, rng);
        LivingEntity objector = null;
        com.oliver.witchmod.entities.DreamEntity d = WitchModEntities.DREAM.get().spawn(level, pos, MobSpawnType.EVENT);
        if (d != null) {
            d.setNoAi(true); // we drive it by hand — no chasing/attacking
            objector = d;
        } else {
            var v = EntityType.VILLAGER.spawn(level, pos, MobSpawnType.EVENT);
            if (v instanceof Mob m) {
                m.setNoAi(true);
                objector = m;
            }
        }
        if (objector != null) {
            objector.setCustomName(Component.literal(name));
            objector.setInvulnerable(true);
            objector.setPos(at.x, victim.getY(), at.z);
            if (objector instanceof Mob mob) {
                mob.setYRot((float) (Mth.atan2(a.marriageAisle.x, -a.marriageAisle.z) * (180.0 / Math.PI)));
                mob.yHeadRot = mob.getYRot();
                mob.yBodyRot = mob.getYRot();
            }
            a.marriageObjectorId = objector.getId();
            a.temps.add(objector.getId());
        }
        return objector;
    }

    /** Frames the objector's FACE: camera sits between the objector and the couple, looking back at it (aim = objector). */
    private static void aimCameraAtObjector(ActiveGag a, LivingEntity objector, Vec3 couple) {
        Vec3 eye = objector.position().add(0, objector.getBbHeight() * 0.85, 0);
        Vec3 toCouple = couple.subtract(objector.position());
        toCouple = new Vec3(toCouple.x, 0, toCouple.z);
        toCouple = toCouple.lengthSqr() < 1.0e-6 ? a.marriageAisle : toCouple.normalize();
        a.camTarget = eye.add(toCouple.scale(1.8)).add(0, 0.15, 0);
    }

    /** A name to mimic: a real other online player if there is one, else a made-up username. */
    private static String mimicName(ServerLevel level, LivingEntity victim, RandomSource rng) {
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p != victim && !p.isSpectator() && p.getData(WitchModAttachments.CUTAWAY_TARGET) < 0) {
                others.add(p);
            }
        }
        if (!others.isEmpty()) {
            return others.get(rng.nextInt(others.size())).getGameProfile().getName();
        }
        return com.oliver.witchmod.data.SolicitorLines.randomName(rng);
    }

    private static String spouseName(@Nullable LivingEntity spouse) {
        return spouse != null ? spouse.getName().getString() : "them";
    }

    /** An overhead spot with a clear line of sight to the victim; falls back to straight above. */
    private static Vec3 computeVantage(ServerLevel level, LivingEntity victim, RandomSource rng) {
        double dist = Config.CUTAWAY_SPECTATE_DISTANCE.get();
        double height = Config.CUTAWAY_CAMERA_HEIGHT.get();
        Vec3 centre = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
        double baseAngle = rng.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < 6; i++) {
            double a = baseAngle + i * (Math.PI / 3.0);
            Vec3 cand = new Vec3(victim.getX() + Math.cos(a) * dist, victim.getY() + height, victim.getZ() + Math.sin(a) * dist);
            if (isClear(level, cand) && hasLineOfSight(level, cand, centre, victim)) {
                return cand;
            }
        }
        return new Vec3(victim.getX(), victim.getY() + height + dist * 0.5, victim.getZ()); // top-down fallback
    }

    private static boolean isClear(ServerLevel level, Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos);
        return level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to, LivingEntity victim) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, victim));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Minecraft yaw/pitch (degrees) to look from {@code from} at {@code to}. */
    private static float[] lookAngles(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horiz) * (180.0 / Math.PI)));
        return new float[] {yaw, pitch};
    }

    // ---- the gags -----------------------------------------------------------------------------------

    private static void startGag(ServerLevel level, ServerPlayer watcher, LivingEntity victim, ActiveGag active) {
        RandomSource rng = level.random;
        switch (active.gag) {
            case CREEPER -> {
                Vec3 behind = victim.position().subtract(victim.getLookAngle().normalize().scale(3.0));
                Creeper c = EntityType.CREEPER.spawn(level, BlockPos.containing(behind), MobSpawnType.EVENT);
                if (c != null) {
                    c.moveTo(behind.x, victim.getY(), behind.z, victim.getYRot() + 180.0F, 0.0F);
                    c.setTarget(victim);
                    c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1)); // a bit quicker on the approach
                }
            }
            case ANVIL -> {
                FallingBlockEntity anvil = FallingBlockEntity.fall(level, victim.blockPosition().above(8), Blocks.ANVIL.defaultBlockState());
                anvil.setHurtsEntities(2.0F, 40); // vanilla anvil values — FallingBlockEntity.fall doesn't set these
            }
            case GOLEM -> {
                IronGolem g = EntityType.IRON_GOLEM.spawn(level, ringPos(victim, 4.0, rng), MobSpawnType.EVENT);
                if (g != null) {
                    g.setPlayerCreated(false);
                    g.setTarget(victim);
                    g.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1));
                    g.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 0));
                    active.flock.add(g.getId()); // tracked so its buffs are stripped when the cutaway ends
                    // NOTE: iron golems can't break blocks in vanilla — "burst through walls" is a stand-in
                    // (aggro + speed). True wall-smashing would need a custom BreakBlockGoal.
                }
            }
            case WOOLIAM -> active.flock.add(spawnWoolliam(level, victim.position(), victim).getId());
            case DRIVEBY -> {
                Vec3 side = sideOffset(victim, 6.0, rng);
                for (int i = 0; i < Config.CUTAWAY_DRIVEBY_COUNT.get(); i++) {
                    Skeleton s = EntityType.SKELETON.spawn(level, BlockPos.containing(side.add(i * 0.8, 0, 0)), MobSpawnType.EVENT);
                    if (s != null) {
                        s.moveTo(side.x + i * 0.8, victim.getY(), side.z, victim.getYRot(), 0.0F);
                        s.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 3));
                        s.setTarget(victim);
                        active.temps.add(s.getId()); // slides away (despawns) when the cutaway ends
                        active.flock.add(s.getId()); // and force-fires fast (see tickGag) — 3x normal fire rate
                    }
                }
            }
            case DREAM -> {
                // A player-MIMIC (real PlayerModel + the dream skin) that jogs up and punches with the custom
                // witchmod:dream damage — reads as just some random player showing up to hit you.
                com.oliver.witchmod.entities.DreamEntity d =
                        WitchModEntities.DREAM.get().spawn(level, ringPos(victim, 8.0, rng), MobSpawnType.EVENT);
                if (d != null) {
                    d.setVictim(victim);
                    active.temps.add(d.getId()); // runs off (despawns) when the cutaway ends
                }
            }
            case JUMPED -> {
                for (int i = 0; i < Config.CUTAWAY_JUMPED_COUNT.get(); i++) {
                    Zombie z = EntityType.ZOMBIE.spawn(level, ringPos(victim, 3.0, rng), MobSpawnType.EVENT);
                    if (z != null) {
                        z.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 1));
                        z.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1));
                        z.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 1));
                        z.setTarget(victim);
                    }
                }
            }
            case SNAIL -> {
                // The snail has no gravity, so it must be placed on real ground close by, not floating in a ring.
                // NO one-shot music here — SnailSoundManager already loops the theme for any nearby SnailEntity
                // and stops it the moment the snail is removed, so playing it again just left it droning on.
                SnailEntity snail = WitchModEntities.SNAIL.get().spawn(level, groundSpotNear(level, victim, 6.0, rng), MobSpawnType.EVENT);
                if (snail != null) {
                    active.temps.add(snail.getId()); // the fake snail leaves (and the music stops) with the cutaway
                }
            }
            case HOLE -> level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.6F);
            case ABDUCTION -> {
                int lift = Config.CUTAWAY_ABDUCTION_LIFT_TICKS.get();
                active.anchorY = victim.getY(); // the UFO towers over the ground, not the rising victim
                victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, lift, Config.CUTAWAY_ABDUCTION_LEVITATION.get()));
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, lift + 40, 0));
                // The UFO arrives (one-shot), then the tractor beam hums for the whole lift (client loop, id 1).
                level.playSound(null, victim.blockPosition(), com.oliver.witchmod.data.WitchModSounds.CUTAWAY_UFO_ENTER.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                watcher.setData(WitchModAttachments.CUTAWAY_LOOP, 1);
            }
            case LAUNCH -> {
                BlockPos below = victim.blockPosition().below();
                level.destroyBlock(below, true); // drops whatever was there
                level.setBlockAndUpdate(below, Blocks.SLIME_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(below.below(), Blocks.PISTON.defaultBlockState());
                active.placed.add(below);          // the slime block — broken a beat after the launch
                active.placed.add(below.below());   // the piston
                double power = Config.CUTAWAY_LAUNCH_POWER.get();
                victim.setDeltaMovement(victim.getDeltaMovement().x, power, victim.getDeltaMovement().z);
                victim.hurtMarked = true;
                victim.fallDistance = 0;
                level.playSound(null, below, SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 1.0F, 0.7F);
                // NOTE: the full piston+pressure-plate contraption is simplified to a slime block + a genuine
                // upward launch — the visual pieces are placed for flavour, the catapult is the velocity.
            }
            case TOKYO_DRIFTING -> {
                net.minecraft.world.entity.vehicle.Boat boat =
                        new net.minecraft.world.entity.vehicle.Boat(level, victim.getX(), victim.getY(), victim.getZ());
                boat.setVariant(net.minecraft.world.entity.vehicle.Boat.Type.CHERRY);
                boat.setYRot(victim.getYRot());
                level.addFreshEntity(boat);
                victim.startRiding(boat, true);
                active.temps.add(boat.getId()); // riding ends + boat despawns when the cutaway does
                if (rng.nextInt(100) < 25) {
                    active.speedMult = 2.5; // 25% chance the whole drift is 2.5x faster
                }
                // (the drift screech plays repeatedly, varied, from the boat — see tickGag)
            }
            case PIED_PIPER -> {
                // Recruit nearby passive animals, then spawn to fill the minimum quota.
                for (net.minecraft.world.entity.animal.Animal a : level.getEntitiesOfClass(
                        net.minecraft.world.entity.animal.Animal.class, victim.getBoundingBox().inflate(24.0), net.minecraft.world.entity.animal.Animal::isAlive)) {
                    active.flock.add(a.getId());
                }
                EntityType<?>[] pool = {EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN};
                while (active.flock.size() < Config.CUTAWAY_PIED_PIPER_MIN.get()) {
                    var e = pool[rng.nextInt(pool.length)].spawn(level, groundSpotNear(level, victim, 8.0, rng), MobSpawnType.EVENT);
                    if (e == null) {
                        break;
                    }
                    active.flock.add(e.getId());
                }
            }
            case FAKE_TNT -> {
                for (int i = 0; i < Config.CUTAWAY_FAKE_TNT_COUNT.get(); i++) {
                    boolean real = rng.nextInt(100) < Config.CUTAWAY_FAKE_TNT_REAL_CHANCE.get();
                    net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(level,
                            victim.getX() + (rng.nextDouble() - 0.5) * 3.0, victim.getY() + 8 + rng.nextInt(4),
                            victim.getZ() + (rng.nextDouble() - 0.5) * 3.0, null);
                    tnt.setFuse(real ? 60 + rng.nextInt(30) : 1000); // fakes never reach 0 — we fizzle them out
                    level.addFreshEntity(tnt);
                    if (!real) {
                        active.flock.add(tnt.getId()); // fizzled mid-gag in tickGag
                        active.temps.add(tnt.getId()); // AND guaranteed cleanup on ANY end (so an early exit
                                                       // can't leave a live fuse ticking down to a real blast)
                    }
                }
            }
            case AQUARIUM -> {
                EntityType<?>[] pool = {EntityType.SQUID, EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH, EntityType.TROPICAL_FISH};
                for (int i = 0; i < Config.CUTAWAY_AQUARIUM_COUNT.get(); i++) {
                    Vec3 p = victim.position().add((rng.nextDouble() - 0.5) * 6.0, 1.0 + rng.nextDouble() * 3.0, (rng.nextDouble() - 0.5) * 6.0);
                    var e = pool[rng.nextInt(pool.length)].spawn(level, BlockPos.containing(p), MobSpawnType.EVENT);
                    if (e != null) {
                        e.moveTo(p.x, p.y, p.z, rng.nextFloat() * 360.0F, 0.0F);
                        e.setNoGravity(true); // air is water now, until the gag ends (finishGag drops them)
                        active.flock.add(e.getId());
                    }
                }
            }
            case I_LIKE_TRAINS -> {
                // Lay the (purely cosmetic) rails and record the direction, then play "I like trains" IN FULL —
                // tickGag spawns the actual train only once the warning has finished (cutawayTrainWarningTicks).
                boolean alongX = rng.nextBoolean();
                BlockPos foot = victim.blockPosition().below();
                for (int d = -14; d <= 14; d++) {
                    BlockPos p = alongX ? foot.offset(d, 0, 0) : foot.offset(0, 0, d);
                    if (level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                        level.setBlockAndUpdate(p.above(), Blocks.RAIL.defaultBlockState());
                        active.placed.add(p.above());
                    }
                }
                Vec3 dir = alongX ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
                if (rng.nextBoolean()) {
                    dir = dir.reverse();
                }
                active.heliDir = dir; // the train's travel direction, used when it rolls
                level.playSound(null, victim.blockPosition(), com.oliver.witchmod.data.WitchModSounds.CUTAWAY_TRAIN_WARNING.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            case BOWLING -> throwBowlingBall(level, victim, active);
            case HELICOPTER -> {
                net.minecraft.world.entity.vehicle.Boat boat =
                        new net.minecraft.world.entity.vehicle.Boat(level, victim.getX(), victim.getY(), victim.getZ());
                boat.setVariant(net.minecraft.world.entity.vehicle.Boat.Type.SPRUCE);
                level.addFreshEntity(boat);
                victim.startRiding(boat, true);
                active.flock.add(boat.getId()); // in flock (NOT temps) so the boat is KEPT after the gag ends
                watcher.setData(WitchModAttachments.CUTAWAY_LOOP, 0); // helicopter loop for the whole state
            }
            case THE_BOUNCER -> {
                var bouncer = WitchModEntities.BODYGUARD.get().spawn(level, groundSpotNear(level, victim, 3.0, rng), MobSpawnType.EVENT);
                if (bouncer != null) {
                    active.temps.add(bouncer.getId());
                    bouncerLine(level, victim, rng);
                }
            }
            case ANNOYING_MUSIC -> {
                // Positional loop at the victim (id 2), stopped when the cutaway ends (endCutaway clears CUTAWAY_LOOP).
                watcher.setData(WitchModAttachments.CUTAWAY_LOOP, 2);
                // NOTE: dedicated "annoying music" track pending — the client loops a Loading-Screen hold track for now.
            }
            case BOUNCY -> {
                if (victim instanceof ServerPlayer p) {
                    active.priorBouncy = p.getData(WitchModAttachments.BOUNCY_ACTIVE);
                    p.setData(WitchModAttachments.BOUNCY_ACTIVE, 1); // borrow the Bouncy blessing's client bounce
                }
            }
            case SPEED -> {
                int ticks = (int) Math.max(1, (END_TICK.getOrDefault(watcher.getUUID(), level.getGameTime()) - level.getGameTime()));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 49)); // amp 49 = Speed 50
                level.playSound(null, victim.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.0F, 1.5F);
            }
            case MARRIAGE -> startMarriage(level, watcher, victim, active, rng);
            case PARADE -> startParade(level, watcher, victim, active, rng);
        }
    }

    /**
     * Parade: a big lineup of varied entities spawns in a column to one side and MARCHES across the victim's
     * front in a straight line. All AI is off — they do nothing but march (driven in {@link #tickGag}) — and
     * they despawn (temps) when the cutaway ends.
     */
    private static void startParade(ServerLevel level, ServerPlayer watcher, LivingEntity victim, ActiveGag active, RandomSource rng) {
        // Frame it to the SPECTATOR camera, not the victim's own facing: the camera looks from its vantage at
        // the victim, so the parade must sweep across THAT line of sight. "forward" = the horizontal direction
        // from the camera to the victim; the column marches perpendicular to it and passes just in front of the
        // victim toward the camera, so it crosses the middle of frame, close and unmissable.
        Vec3 camToVictim = victim.position().subtract(watcher.position());
        Vec3 forward = new Vec3(camToVictim.x, 0, camToVictim.z);
        if (forward.lengthSqr() < 1.0e-4) {
            Vec3 look = victim.getLookAngle();
            forward = new Vec3(look.x, 0, look.z);
            if (forward.lengthSqr() < 1.0e-4) {
                forward = new Vec3(0, 0, 1);
            }
        }
        forward = forward.normalize();
        Vec3 marchDir = new Vec3(-forward.z, 0, forward.x); // 90° to the camera line — they sweep across frame
        active.paradeDir = marchDir;

        // Sit the column BETWEEN the camera and the victim (a little toward the camera), so it fills the frame
        // and reads big instead of shrinking off behind the victim.
        Vec3 crossCentre = victim.position().subtract(forward.scale(2.5));
        int count = Config.CUTAWAY_PARADE_COUNT.get();
        int perRow = 2;
        double along = 1.7;   // spacing between rows (down the march line)
        double across = 1.3;  // spacing between the two abreast (toward/away from the victim)
        int rows = (count + perRow - 1) / perRow;
        double startBack = rows * along; // start this far up-march so the column crosses through

        for (int i = 0; i < count; i++) {
            int row = i / perRow;
            int col = i % perRow;
            net.minecraft.world.entity.EntityType<?> type = PARADE_TYPES[rng.nextInt(PARADE_TYPES.length)];
            if (!(type.create(level) instanceof Mob m)) {
                continue;
            }
            Vec3 spot = crossCentre
                    .add(marchDir.scale(-startBack + row * along))
                    .add(forward.scale((col - (perRow - 1) / 2.0) * across));
            // Spawn at the VICTIM's height (NOT the surface heightmap — that put the column on the surface far
            // above an underground victim, up near the spectator camera, which is what let it hit the spectator).
            // Per-tick gravity in tickGag settles them onto whatever floor is actually there.
            m.moveTo(spot.x, victim.getY(), spot.z, marchYaw(marchDir), 0F);
            m.setNoAi(true);
            m.setYBodyRot(marchYaw(marchDir));
            m.setYHeadRot(marchYaw(marchDir));
            if (m instanceof net.minecraft.world.entity.PathfinderMob) {
                m.setPersistenceRequired();
            }
            level.addFreshEntity(m);
            active.temps.add(m.getId()); // despawns when the cutaway ends
            active.flock.add(m.getId()); // driven each tick in tickGag
        }
        // Both tracks together (drums + march music) at the parade, at 55% volume (45% quieter), heard by
        // everyone nearby — cut when the gag ends. Positioned at the marching column so it comes FROM the parade.
        float vol = 0.55F;
        level.playSound(null, crossCentre.x, crossCentre.y, crossCentre.z,
                com.oliver.witchmod.data.WitchModSounds.CUTAWAY_PARADE_DRUM.get(), SoundSource.RECORDS, vol, 1.0F);
        level.playSound(null, crossCentre.x, crossCentre.y, crossCentre.z,
                com.oliver.witchmod.data.WitchModSounds.CUTAWAY_PARADE_MARCH.get(), SoundSource.RECORDS, vol, 1.0F);
    }

    private static float marchYaw(Vec3 dir) {
        return (float) (net.minecraft.util.Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
    }

    /** Ongoing choreography for the few gags that play out over the cutaway. */
    private static void tickGag(ServerLevel level, ServerPlayer watcher, ActiveGag active, long now) {
        LivingEntity victim = level.getEntity(active.victim) instanceof LivingEntity le ? le : null;
        long elapsed = now - active.gagStart;
        switch (active.gag) {
            case WOOLIAM -> {
                // Double the flock rate — two sheep per interval.
                if (victim != null && elapsed % 15 == 0) {
                    for (int n = 0; n < 2 && active.flock.size() < Config.CUTAWAY_WOOLIAM_CAP.get(); n++) {
                        Vec3 near = victim.position().add((level.random.nextDouble() - 0.5) * 3.0, 0, (level.random.nextDouble() - 0.5) * 3.0);
                        active.flock.add(spawnWoolliam(level, near, victim).getId());
                    }
                }
            }
            case HOLE -> {
                if (victim != null && !active.digDone) {
                    if (active.holeWarnTicks < 0) {
                        active.holeWarnTicks = 30 + level.random.nextInt(11); // 1.5–2s warning
                    }
                    if (elapsed < active.holeWarnTicks && elapsed % 6 == 0) {
                        level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.5F);
                        level.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY(), victim.getZ(), 8, 0.4, 0.05, 0.4, 0.02);
                    } else if (elapsed >= active.holeWarnTicks) {
                        digHole(level, victim.blockPosition());
                        active.digDone = true;
                    }
                }
            }
            case ABDUCTION -> {
                // Only while the beam is actively yanking them up — once they're dropped, everything stops
                // (no particles left hanging in the sky).
                if (victim != null && elapsed < Config.CUTAWAY_ABDUCTION_LIFT_TICKS.get()) {
                    double cx = victim.getX();
                    double cz = victim.getZ();
                    double discY = active.anchorY + Config.CUTAWAY_ABDUCTION_HEIGHT.get(); // fixed high UFO
                    // A fast-spinning saucer rim high above...
                    int pts = 40;
                    double spin = elapsed * 0.6;
                    for (int i = 0; i < pts; i++) {
                        double a = spin + i * (Math.PI * 2.0 / pts);
                        level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * 3.0, discY, cz + Math.sin(a) * 3.0, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                    // ...a glowing dome on top...
                    level.sendParticles(ParticleTypes.GLOW, cx, discY + 0.8, cz, 6, 1.0, 0.4, 1.0, 0.0);
                    level.sendParticles(ParticleTypes.END_ROD, cx, discY + 1.4, cz, 3, 0.3, 0.2, 0.3, 0.0);
                    // ...and a tall green tractor beam from the victim right up to the saucer.
                    for (double y = victim.getY() + 0.2; y < discY; y += 0.6) {
                        double frac = (y - active.anchorY) / (discY - active.anchorY);
                        double r = 0.3 + (1.0 - frac) * 1.6; // widest at the ground, narrowing to the UFO
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                cx + (level.random.nextDouble() - 0.5) * r * 2.0, y,
                                cz + (level.random.nextDouble() - 0.5) * r * 2.0, 1, 0.0, 0.08, 0.0, 0.0);
                    }
                } else if (watcher.getData(WitchModAttachments.CUTAWAY_LOOP) == 1) {
                    watcher.setData(WitchModAttachments.CUTAWAY_LOOP, -1); // lift's over — cut the tractor-beam hum
                }
            }
            case SNAIL -> {
                // The immortal snail creeps toward the victim (it's AI-less, so it's slid by hand each tick).
                if (victim != null && !active.temps.isEmpty()
                        && level.getEntity(active.temps.get(0)) instanceof SnailEntity snail) {
                    moveSnailToward(level, snail, victim, Config.CUTAWAY_SNAIL_SPEED.get());
                    if (snail.distanceToSqr(victim) < 1.3 * 1.3) {
                        // It caught you — a blast that LOOKS real but only hits the victim (no terrain damage, no
                        // collateral on the watcher/bystanders), for a fixed amount, then the gag ends.
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, victim.getX(), victim.getY() + 0.4, victim.getZ(), 1, 0, 0, 0, 0);
                        level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 0.5, victim.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
                        level.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 1.0F);
                        victim.hurt(level.damageSources().explosion(null, null), (float) (double) Config.CUTAWAY_SNAIL_DAMAGE.get());
                        active.endNow = true;
                    }
                }
            }
            case TOKYO_DRIFTING -> {
                if (victim != null && !active.temps.isEmpty()
                        && level.getEntity(active.temps.get(0)) instanceof net.minecraft.world.entity.vehicle.Boat boat) {
                    if (victim.getVehicle() != boat) {
                        victim.startRiding(boat, true); // no escape — forced back into the boat each tick
                    }
                    double speed = Config.CUTAWAY_TOKYO_SPEED.get() * active.speedMult;
                    double heading = elapsed * 0.16; // slowly curving heading = the "drift"
                    Vec3 v = new Vec3(Math.cos(heading) * speed, boat.getDeltaMovement().y, Math.sin(heading) * speed);
                    boat.setDeltaMovement(v);
                    boat.setYRot((float) (Mth.atan2(-v.x, v.z) * (180.0 / Math.PI)));
                    boat.hurtMarked = true;
                    // Each drift: the screech at a widely-varied pitch, slightly quieter, at the boat.
                    if (elapsed % 22 == 0) {
                        level.playSound(null, boat.blockPosition(), com.oliver.witchmod.data.WitchModSounds.CUTAWAY_DRIFTING.get(),
                                SoundSource.HOSTILE, 0.75F, 0.6F + level.random.nextFloat() * 0.9F);
                    }
                }
            }
            case LAUNCH -> {
                if (!active.secondaryDone && elapsed >= 12) {
                    active.secondaryDone = true; // the launch pad breaks apart a beat after firing you off it
                    for (BlockPos p : active.placed) {
                        if (!level.getBlockState(p).isAir()) {
                            level.destroyBlock(p, true);
                        }
                    }
                    active.placed.clear();
                }
            }
            case PIED_PIPER -> {
                if (victim != null && elapsed % 10 == 0) {
                    for (int id : active.flock) {
                        if (level.getEntity(id) instanceof Mob m) {
                            m.getNavigation().moveTo(victim.getX(), victim.getY(), victim.getZ(), 1.3);
                        }
                    }
                }
            }
            case FAKE_TNT -> {
                if (!active.secondaryDone && elapsed >= 70) {
                    active.secondaryDone = true; // the fakes reach their moment... and fizzle
                    for (int id : active.flock) {
                        if (level.getEntity(id) instanceof net.minecraft.world.entity.item.PrimedTnt tnt) {
                            level.sendParticles(ParticleTypes.LARGE_SMOKE, tnt.getX(), tnt.getY() + 0.5, tnt.getZ(), 18, 0.3, 0.3, 0.3, 0.01);
                            level.playSound(null, tnt.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.9F, 1.0F);
                            tnt.discard();
                        }
                    }
                }
            }
            case AQUARIUM -> {
                if (elapsed % 5 == 0) {
                    for (int id : active.flock) {
                        if (level.getEntity(id) instanceof Mob fish && fish.isNoGravity()) {
                            fish.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.14,
                                    (level.random.nextDouble() - 0.5) * 0.1, (level.random.nextDouble() - 0.5) * 0.14);
                            fish.hurtMarked = true;
                        }
                    }
                }
            }
            case I_LIKE_TRAINS -> {
                if (victim != null) {
                    // Wait for the "I like trains" line to finish, THEN spawn the train (once) and roll it.
                    if (!active.secondaryDone && elapsed >= Config.CUTAWAY_TRAIN_WARNING_TICKS.get()) {
                        active.secondaryDone = true;
                        spawnTrainCarts(level, victim, active);
                    }
                    double trainSpeed = Config.CUTAWAY_TRAIN_SPEED.get();
                    for (int id : new ArrayList<>(active.flock)) {
                        if (level.getEntity(id) instanceof net.minecraft.world.entity.vehicle.Minecart cart) {
                            // Drive by hand + PIN to the rail line (Y + perpendicular coord) so nothing can slow,
                            // stop, or knock it off the tracks — it just barrels straight down the rails.
                            cart.noPhysics = true;
                            double along = (active.trainAlongX ? cart.getX() : cart.getZ())
                                    + (active.trainAlongX ? active.heliDir.x : active.heliDir.z) * trainSpeed;
                            double nx = active.trainAlongX ? along : active.trainPerp;
                            double nz = active.trainAlongX ? active.trainPerp : along;
                            cart.setPos(nx, active.trainY, nz);
                            cart.setDeltaMovement(active.heliDir.scale(trainSpeed)); // client lerp + rider follow
                            cart.hurtMarked = true;
                            if (cart.distanceToSqr(victim) < 2.5 * 2.5) {
                                trainHit(level, victim, cart);
                                active.flock.remove((Integer) id);
                            }
                        }
                    }
                }
            }
            case BOWLING -> {
                // A queued subsequent bowl comes due.
                if (active.nextBowlTick >= 0 && elapsed >= active.nextBowlTick && victim != null) {
                    active.nextBowlTick = -1;
                    throwBowlingBall(level, victim, active);
                }
                if (victim != null && !active.flock.isEmpty()
                        && level.getEntity(active.flock.get(0)) instanceof FallingBlockEntity ball) {
                    // Fly STRAIGHT (no homing) at a steady speed along its launch direction, so it's dodgeable —
                    // step out of the way and it whiffs right past you.
                    Vec3 v = ball.getDeltaMovement();
                    if (v.lengthSqr() > 1.0e-4) {
                        ball.setDeltaMovement(v.normalize().scale(1.4));
                    }
                    ball.setNoGravity(true);
                    ball.noPhysics = true; // never stopped by terrain
                    ball.time = 1;
                    ball.hurtMarked = true;
                    // PIERCE: bowl over EVERY entity in the ball's path (the victim + whatever cluster it's in),
                    // like a group of pins. The ball keeps going — it doesn't stop on the first hit.
                    Vec3 dir = ball.getDeltaMovement().lengthSqr() > 1.0e-4 ? ball.getDeltaMovement().normalize() : new Vec3(0, 0, 1);
                    for (LivingEntity pin : level.getEntitiesOfClass(LivingEntity.class, ball.getBoundingBox().inflate(1.4),
                            le -> le.isAlive() && !active.bowledIds.contains(le.getId()))) {
                        bowlPin(level, ball, pin, dir);
                        active.bowledIds.add(pin.getId());
                    }
                    double d2 = ball.distanceToSqr(victim.position().add(0, 0.5, 0));
                    if (d2 > 26.0 * 26.0) {
                        ball.discard(); // flown past the whole cluster — that bowl's over
                        active.flock.clear();
                        active.bowledIds.clear();
                        rollNextBowl(active, elapsed, level.random);
                    }
                }
            }
            case PARADE -> {
                // Drive the marchers in a dead-straight line — AI is off, so they're moved BY HAND each tick
                // (move() + manual gravity + a hop when blocked) so they walk over any terrain instead of
                // floating or getting stuck. Plus a shower of confetti over the column.
                double speed = Config.CUTAWAY_PARADE_SPEED.get();
                float yaw = marchYaw(active.paradeDir);
                for (int id : active.flock) {
                    if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                        m.setNoAi(true);
                        double dy = m.onGround() ? Math.max(0.0, m.getDeltaMovement().y) : m.getDeltaMovement().y - 0.08;
                        m.move(net.minecraft.world.entity.MoverType.SELF,
                                new Vec3(active.paradeDir.x * speed, dy, active.paradeDir.z * speed));
                        double storeY = m.onGround() ? 0.0 : dy;
                        if (m.horizontalCollision && m.onGround()) {
                            storeY = 0.5; // hop the step/obstacle so they keep marching over terrain
                        }
                        m.setDeltaMovement(0.0, storeY, 0.0);
                        m.setYRot(yaw);
                        m.setYBodyRot(yaw);
                        m.setYHeadRot(yaw);
                        m.hurtMarked = true;
                        // Trample: anything in the column's path is knocked away + hurt (vanilla i-frames stop spam).
                        // The spectating WATCHER is explicitly excluded — the cutaway must never hurt them.
                        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(0.5),
                                le -> le.isAlive() && le != m && le != watcher && !active.flock.contains(le.getId()))) {
                            hit.hurt(com.oliver.witchmod.data.WitchModDamageTypes.parade(level),
                                    (float) (double) Config.CUTAWAY_PARADE_DAMAGE.get());
                            Vec3 kb = active.paradeDir.scale(0.8).add(0.0, 0.42, 0.0);
                            hit.setDeltaMovement(hit.getDeltaMovement().add(kb));
                            hit.hurtMarked = true;
                        }
                        // Confetti raining over the marcher.
                        if (level.random.nextInt(2) == 0) {
                            var confetti = new net.minecraft.core.particles.DustParticleOptions(
                                    new org.joml.Vector3f(level.random.nextFloat(), level.random.nextFloat(), level.random.nextFloat()), 1.2F);
                            level.sendParticles(confetti, m.getX(), m.getY() + m.getBbHeight() + 1.2, m.getZ(),
                                    2, 0.4, 0.3, 0.4, 0.0);
                        }
                    }
                }
            }
            case THE_BOUNCER -> {
                if (victim != null && !active.temps.isEmpty() && level.getEntity(active.temps.get(0)) instanceof Mob bouncer) {
                    bouncer.getNavigation().moveTo(victim.getX(), victim.getY(), victim.getZ(), 1.1);
                    if (bouncer.distanceToSqr(victim) < 2.5 * 2.5 && elapsed % 12 == 0) {
                        Vec3 shove = victim.position().subtract(bouncer.position()).normalize().scale(0.6).add(0, 0.25, 0);
                        victim.setDeltaMovement(victim.getDeltaMovement().add(shove));
                        victim.hurtMarked = true;
                    }
                    if (elapsed % 45 == 0) {
                        bouncerLine(level, victim, level.random);
                    }
                }
            }
            case MARRIAGE -> {
                if (victim != null) {
                    tickMarriage(level, watcher, victim, active, elapsed);
                }
            }
            case DRIVEBY -> {
                // Force the skeletons to fire ~3x as fast (33% of the normal ~20t gap) by shooting them by hand.
                if (victim != null && elapsed % 7 == 0) {
                    for (int id : active.flock) {
                        if (level.getEntity(id) instanceof net.minecraft.world.entity.monster.AbstractSkeleton s && s.isAlive()) {
                            s.performRangedAttack(victim, 1.0F);
                        }
                    }
                }
            }
            case BOUNCY -> {
                // Actually FLING them so they ping around — a strong random launch every few ticks (the client
                // Bouncy bounce, borrowed via BOUNCY_ACTIVE, then rebounds them off whatever they hit).
                if (victim != null && elapsed % 8 == 0) {
                    double a = level.random.nextDouble() * Math.PI * 2.0;
                    // Heavy HORIZONTAL launch, only a little pop of height — pinball, not a catapult.
                    double h = 1.4 + level.random.nextDouble() * 0.6;
                    victim.setDeltaMovement(Math.cos(a) * h, 0.2 + level.random.nextDouble() * 0.15, Math.sin(a) * h);
                    victim.hurtMarked = true;
                    victim.fallDistance = 0;
                    level.playSound(null, victim.blockPosition(), com.oliver.witchmod.data.WitchModSounds.BOUNCY_BOING.get(),
                            SoundSource.HOSTILE, 0.8F, 1.0F + (level.random.nextFloat() - 0.5F) * 0.3F);
                }
            }
            case HELICOPTER -> {
                if (victim != null && !active.flock.isEmpty()
                        && level.getEntity(active.flock.get(0)) instanceof net.minecraft.world.entity.vehicle.Boat boat) {
                    if (victim.getVehicle() != boat) {
                        victim.startRiding(boat, true); // no escape
                    }
                    // Spin RAMPS up with the ascent — winds from moderate to a fast blur. Advance the yaw by a
                    // per-tick delta with yRotO = the PREVIOUS yaw, so the client interpolates FORWARD (the old
                    // absolute-yaw approach crossed the ±180 wrap and read as a back-and-forth wiggle).
                    float spinRate = (float) Math.min(Config.CUTAWAY_HELICOPTER_SPIN.get(), 12.0 + elapsed * 3.0);
                    float prev = boat.getYRot();
                    boat.yRotO = prev;
                    boat.setYRot(prev + spinRate);
                    double rise = Math.min(Config.CUTAWAY_HELICOPTER_RISE_MAX.get(), 0.08 + elapsed * 0.035);
                    boat.setDeltaMovement(0, rise, 0);
                    boat.hurtMarked = true;
                }
            }
            default -> { /* fire-and-forget gags need no per-tick work */ }
        }
    }

    private static Sheep spawnWoolliam(ServerLevel level, Vec3 pos, LivingEntity victim) {
        Sheep sheep = EntityType.SHEEP.spawn(level, BlockPos.containing(pos), MobSpawnType.EVENT);
        if (sheep != null) {
            sheep.moveTo(pos.x, pos.y, pos.z, victim.getYRot(), 0.0F);
            // Just the custom name — NOT setCustomNameVisible(true), which forces the always-on tag that reads
            // wrong (vanilla named mobs only show the tag when you look at them).
            sheep.setCustomName(Component.literal("Woolliam"));
        }
        return sheep != null ? sheep : new Sheep(EntityType.SHEEP, level); // never null in practice
    }

    private static void digHole(ServerLevel level, BlockPos centre) {
        int depth = Config.CUTAWAY_HOLE_DEPTH.get();
        for (int dy = 1; dy <= depth; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = centre.offset(dx, -dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (state.getDestroySpeed(level, p) >= 0.0F && !state.isAir()) {
                        level.destroyBlock(p, true);
                    }
                }
            }
        }
    }

    /** True if the ground under the victim is mainly natural terrain (so the Hole gag fits). */
    private static boolean naturalBelow(ServerLevel level, LivingEntity victim) {
        int natural = 0;
        int solid = 0;
        BlockPos base = victim.blockPosition();
        for (int dy = 1; dy <= 5; dy++) {
            BlockState state = level.getBlockState(base.below(dy));
            if (state.isAir()) {
                continue;
            }
            solid++;
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.DIRT)
                    || state.is(BlockTags.SAND) || state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                natural++;
            }
        }
        return solid >= 3 && natural >= solid - 1;
    }

    /** Slides the AI-less snail one step toward the victim, snapped to the ground surface and facing them. */
    private static void moveSnailToward(ServerLevel level, SnailEntity snail, LivingEntity victim, double speed) {
        Vec3 from = snail.position();
        double dx = victim.getX() - from.x;
        double dz = victim.getZ() - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-3) {
            return;
        }
        double step = Math.min(speed, horiz);
        double nx = from.x + dx / horiz * step;
        double nz = from.z + dz / horiz * step;
        double surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(nx), Mth.floor(nz));
        double ny = Math.abs(surface - from.y) <= 4.0 ? surface : from.y;
        snail.setPos(nx, ny, nz);
        float yaw = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI)); // model front (eye stalks) is -Z at yaw 0
        snail.setYRot(yaw);
        snail.setYBodyRot(yaw);
        snail.setYHeadRot(yaw);
        snail.setXRot(0.0F);
    }

    /** A standable spot near the victim (air with solid ground under it), snapped down; victim's block if none. */
    private static BlockPos groundSpotNear(ServerLevel level, LivingEntity victim, double radius, RandomSource rng) {
        double a = rng.nextDouble() * Math.PI * 2.0;
        int x = Mth.floor(victim.getX() + Math.cos(a) * radius);
        int z = Mth.floor(victim.getZ() + Math.sin(a) * radius);
        int startY = Mth.floor(victim.getY()) + 2;
        for (int y = startY; y > startY - 8; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return p;
            }
        }
        return victim.blockPosition();
    }

    private static BlockPos ringPos(LivingEntity victim, double radius, RandomSource rng) {
        double a = rng.nextDouble() * Math.PI * 2.0;
        return BlockPos.containing(victim.getX() + Math.cos(a) * radius, victim.getY(), victim.getZ() + Math.sin(a) * radius);
    }

    private static Vec3 sideOffset(LivingEntity victim, double distance, RandomSource rng) {
        Vec3 look = victim.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(distance * (rng.nextBoolean() ? 1 : -1));
        return victim.position().add(side);
    }

    private static final class SavedPos {
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final boolean invisible;
        final boolean noGravity;

        SavedPos(double x, double y, double z, float yaw, float pitch, boolean invisible, boolean noGravity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.invisible = invisible;
            this.noGravity = noGravity;
        }

        net.minecraft.nbt.CompoundTag toTag(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putString("dim", dim.location().toString());
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("z", z);
            t.putFloat("yaw", yaw);
            t.putFloat("pitch", pitch);
            t.putBoolean("invis", invisible);
            t.putBoolean("nograv", noGravity);
            return t;
        }

        static SavedPos fromTag(net.minecraft.nbt.CompoundTag t) {
            return new SavedPos(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                    t.getFloat("yaw"), t.getFloat("pitch"), t.getBoolean("invis"), t.getBoolean("nograv"));
        }
    }

    /** Teleport a watcher back to a saved spot and undo the spectate invisibility/gravity, robust to a cross-dimension return. */
    private static void restoreTo(ServerPlayer watcher, ServerLevel level, SavedPos saved, @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey) {
        ServerLevel dest = level;
        if (dimKey != null) {
            ServerLevel d = level.getServer().getLevel(dimKey);
            if (d != null) {
                dest = d;
            }
        }
        watcher.teleportTo(dest, saved.x, saved.y, saved.z, saved.yaw, saved.pitch);
        watcher.setInvisible(saved.invisible);
        watcher.setNoGravity(saved.noGravity);
        watcher.setDeltaMovement(Vec3.ZERO);
        watcher.hurtMarked = true;
        watcher.fallDistance = 0;
        watcher.onUpdateAbilities();
    }

    /** Teleport home from the persisted return tag, if present. Returns true if a recovery happened. */
    private static boolean restoreFromReturnTag(ServerPlayer watcher, ServerLevel level) {
        net.minecraft.nbt.CompoundTag ret = watcher.getData(WitchModAttachments.CUTAWAY_RETURN);
        if (ret == null || !ret.contains("x")) {
            return false;
        }
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey = null;
        if (ret.contains("dim")) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(ret.getString("dim"));
            if (rl != null) {
                dimKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
            }
        }
        restoreTo(watcher, level, SavedPos.fromTag(ret), dimKey);
        return true;
    }

    /**
     * ⚠ FOOLPROOF ANTI-TELEPORT GUARD. Called every server tick (and on login) for any player NOT currently
     * spectating. If a persisted return position is still stored, the cutaway ended abnormally — a relog,
     * server restart, death, or the curse being stripped — and the player is sitting at the spectate vantage
     * (right next to whoever they cut to). We ALWAYS send them back to their real pre-cutaway spot and clear
     * the state, so the gag can never be abused as a teleport-to-any-player. Cheap: the tag is empty for
     * everyone not mid-cutaway, so the {@code contains("x")} check short-circuits.
     */
    public static void recoverIfStranded(ServerPlayer watcher) {
        if (watcher.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
            return; // a genuine active cutaway — driveActiveCutaway owns it
        }
        net.minecraft.nbt.CompoundTag ret = watcher.getData(WitchModAttachments.CUTAWAY_RETURN);
        if (ret == null || !ret.contains("x")) {
            return; // not stranded
        }
        restoreFromReturnTag(watcher, watcher.serverLevel());
        clearSpectateState(watcher);
        setCooldown(watcher, watcher.level().getGameTime());
    }

    private static final class ActiveGag {
        final Gag gag;
        final UUID victim;
        final long gagStart; // tick the gag actually fires (after the 2–6s delay)
        boolean started;
        final List<Integer> flock = new ArrayList<>();   // navigated/managed mobs (pied piper, aquarium, fake tnt…)
        final List<Integer> temps = new ArrayList<>();   // entities discarded at end (carts, ball, bouncer, spouse…)
        final List<BlockPos> placed = new ArrayList<>();  // blocks to clear at end (rails, carpet)
        boolean digDone;
        boolean secondaryDone;    // fake-tnt fizzle / launch-block break / one-shot mid-gag steps
        boolean endNow;           // a gag (e.g. Snail impact) asked to end the cutaway this tick
        int holeWarnTicks = -1;   // Hole's rolled 1.5–2s warning length
        int priorBouncy = -2;     // victim's BOUNCY_ACTIVE before the Bouncy gag hijacked it (-2 = untouched)
        double speedMult = 1.0;   // Tokyo Drifting's 25% "2.5x overall" roll
        Vec3 heliDir = Vec3.ZERO; // I Like Trains travel direction (reused field)
        // I Like Trains: the rail line the carts are pinned to (so they stay anchored to the tracks).
        boolean trainAlongX;
        double trainY;
        double trainPerp;
        // Bowling: sequential bowls (50% chance each to throw another, max 4). The ball PIERCES the cluster,
        // bowling over everyone in its path like pins — bowledIds stops it re-hitting the same pin each tick.
        int bowlCount;
        int nextBowlTick = -1;
        final java.util.Set<Integer> bowledIds = new java.util.HashSet<>();
        // Parade: the straight-line march direction the columns walk in.
        Vec3 paradeDir = Vec3.ZERO;
        // Marriage: the randomly-chosen ceremony lines + which of the four paths this wedding takes.
        final List<String> marriageScript = new ArrayList<>();
        int marriageOutcome;
        boolean marriageObjection;
        int marriagePath = -1;           // 0 normal · 1 objected · 2 explode · 3 "what"
        int marriageSpouseId = -1;       // the entity being married
        int marriageObjectorId = -1;     // the player-mimic that bursts in on the objection path
        int marriageOfficiantId = -1;    // the villager conducting the ceremony
        final List<Integer> marriageGuests = new ArrayList<>(); // seated guests (for cheering / gasping)
        Vec3 marriageAisle = Vec3.ZERO;  // horizontal direction from the victim toward the spouse (drives camera framing)
        Vec3 camTarget;                  // where the spectate camera (the watcher's body) is easing toward
        double camLerp = 1.0;            // 1 = hard cut this tick, <1 = smooth push
        double anchorY;           // abduction: the ground Y the UFO towers above (so it doesn't rise with the victim)

        ActiveGag(Gag gag, UUID victim, long gagStart) {
            this.gag = gag;
            this.victim = victim;
            this.gagStart = gagStart;
        }
    }
}
