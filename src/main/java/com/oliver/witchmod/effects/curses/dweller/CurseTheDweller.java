package com.oliver.witchmod.effects.curses.dweller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.entities.SpaghettiManEntity;
import com.oliver.witchmod.entities.WatcherEyesEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * the Dweller (sacrificial item BOAT): a self-contained, progressive mini horror. A lanky humanoid — the
 * "Spaghetti Man" — that ONLY the victim can see, hear or interact with (a private client-side illusion, like
 * Delusions and Social Outcast) stalks them, and the whole experience escalates with a fluid, environment-
 * driven DREAD meter.
 *
 * <p><b>Dread is the engine.</b> It rises and falls every tick from where you are and how you play:
 * <ul>
 *   <li>UP in the dark, alone, at night, sealed underground, while you stare at it, while it looms close;</li>
 *   <li>DOWN in bright light, near other players, in open daylight — real, active COUNTERPLAY.</li>
 * </ul>
 * A slowly-rising floor guarantees it always eventually progresses, but perfect play caps you just short of
 * the hunt, so the worst horrors are EARNED by wandering the dark alone.
 *
 * <p><b>Four escalating tiers</b> drive how present, close and aggressive it is, and — client-side, off the
 * synced {@link WitchModAttachments#DWELLER_DREAD} — how far the desaturation and fog have rolled in.
 *
 * <p><b>The AI</b> stalks like a weeping angel (creeps only while unobserved, freezes when watched), keeps to
 * the dark and the edge of your light, and — when dread peaks — HUNTS. The hunt is survivable: reach the
 * light, reach other players, or simply outlast it and it breaks off, exhausted, buying you a reprieve before
 * it builds again. Only being caught is fatal.
 */
public final class CurseTheDweller extends Effect {
    private static final Map<UUID, State> STATES = new HashMap<>();

    // the encounter cycle: a scene with a real arc, not a steady drip of random scares.
    //   FOREPLAY — the opening phase: NO dread, NO fog/desaturation, music still plays, near-silence, only the
    //              odd VERY distant watch. Lasts 40s–3min, then the real haunt (and dread) begins.
    //   LULL     — genuine quiet (you relax). One faint far-off wrongness at most.
    //   TELL     — the anticipation beat: heartbeat starts + quickens, signs mount, nothing lunges yet.
    //   STALK    — it manifests and does its ONE thing: creeps closer only while unobserved (weeping angel).
    //   SPIKE    — resolved inside STALK: exactly one payoff (it reaches you / you keep it at bay and it's gone).
    //   RELEASE  — the exhale: pointed quiet, heartbeat fades, then back to LULL.
    //   CHASE    — the rare top-tier spike.
    private enum Phase { FOREPLAY, LULL, TELL, STALK, RELEASE, CHASE }

    @FunctionalInterface
    interface DelayedAct {
        void run(ServerPlayer target, ServerLevel level);
    }

    static final class Pending {
        final DelayedAct act;
        int delay;
        Pending(DelayedAct act, int delay) {
            this.act = act;
            this.delay = delay;
        }
    }

    /** shared mutable state for one victim. Package-visible so extracted {@link DwellerEvent} classes can read it. */
    static final class State {
        double anger;                 // the dread value, 0..DWELLER_ANGER_MAX (drives encounter frequency + severity)
        long age;                     // ticks the curse has been active (drives the inevitable floor)
        Phase phase = Phase.LULL;
        @Nullable SpaghettiManEntity entity;
        int timer;                    // ticks left in the current beat (lull / tell / stalk / release)
        int beatTotal;                // total length of the current beat (for progress-based cues)
        double heat;                  // 0..1 encounter tension, drives the heartbeat tempo (the one readable cue)
        boolean signGiven;            // one-shot: the mid-TELL sign has fired this beat
        int watched;                  // consecutive ticks observed during STALK
        int eventCd;
        int ambientCd;                // legacy timer, still seeded (harmless)
        int hallucCd;                 // auditory-hallucination timer (debug-forcible only now)
        int chaseTicks;               // how long the current hunt has run
        int chaseMaxThisRun = 600;    // rolled 20–35s length of THIS hunt before it breaks off
        int chaseSafeTicks;           // consecutive ticks safe (in light / with company) during a hunt
        int chaseCooldown;            // calm enforced after a survived hunt
        int chaseCount;               // how many hunts you've endured — each one faster + more teleports
        int chaseTeleportCd;          // countdown to the next mid-hunt teleport switch-up
        int chaseStuck;               // ticks it has failed to make progress (drives the teleport fallback)
        double chaseLastDist;         // its distance to you last tick (to detect being stuck)
        boolean discovered;
        final List<Pending> pending = new ArrayList<>();
        final List<Integer> eyesIds = new ArrayList<>();        // the watching eyes in the dark
        int eyesTimer;
        int eyesCd = 200;                                       // gap before eyes can open in the dark again
        int heartbeatCd;                                        // the oppressive dread pulse
        // FOREPLAY + STALK mode + the sound bookkeeping.
        int foreplayTimer;            // ticks left in the dread-free opening phase
        int sinceStalk;               // ticks since the last encounter started (drives the boredom dread boost)
        int tier0Ticks;               // ticks spent stuck at tier 0 (drives the extra "stuck" boost)
        int ambientEventCd = 200;     // timer for the ambient world-interaction events during a lull
        int lungeTicks;               // >0 while a lunge is mid-lurch (suspends the watch vanish rules)
        boolean sizeUp;               // this STALK is a SIZEUP (in front, passive → lunge on approach/stare)
        int watchTier;                // this watch's distance band: 0 FAR (30-50), 1 MEDIUM (18-29), 2 CLOSE (8-16)
        boolean windowWatch;          // this watch peers through a window: ANY direct line of sight makes it vanish
        boolean hasBeenSeen;          // you've looked at this watch at least once (drives the look-away vanish)
        final java.util.List<Integer> watchIds = new java.util.ArrayList<>(); // mob-stare: frozen passive mobs
        int watchTimer;               // mob-stare countdown
        int moodCd = 200;             // non-diegetic mood ambience / red-herring timer
        int breathCd;                 // subtle "close behind you" breath warning cooldown
        int breathArm;               // ticks the turn-to-face breath jumpscare stays armed
        double chaseStepDist;         // distance the dweller has walked since the last footstep (chase)
        int chaseLungeTicks;          // >0 while a mid-chase ravager LUNGE is in flight (locked direction)
        int chaseLungeCd;             // cooldown before the next chase-lunge can fire
        Vec3 chaseLungeVel = Vec3.ZERO; // the locked launch velocity of the current chase-lunge (dodgeable)
        int lastLaughBoundary = -1;   // highest dread half/full boundary already laughed at (tier-up laugh)
        boolean breathingOn;          // whether the client breathing loop is currently requested
        // POSSESSION: a passive mob it's puppeteering.
        int possessedId = -1;         // the possessed mob's entity id (-1 = none)
        int possessPhase;             // 0 = twitching in place, 1 = marching at you
        int possessTimer;             // ticks left in the current possession phase
        int possessCd;                // cooldown before another possession
        // BEHIND: the turn-around glimpse.
        float prevYaw = Float.NaN;    // last tick's look yaw, to detect a fast turn
        int behindCd;                 // cooldown for the behind-you glimpse
    }

    public CurseTheDweller() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 70, () -> Items.OAK_BOAT);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        State state = new State();
        beginForeplay(target, state);       // open on the quiet, dread-free FOREPLAY phase
        state.ambientCd = ambientGap(target);
        state.hallucCd = hallucinationGap(target, 0);
        STATES.put(target.getUUID(), state);
        target.setData(WitchModAttachments.DWELLER_ACTIVE, 0);
        target.setData(WitchModAttachments.DWELLER_DREAD, 0.0F);
        target.setData(WitchModAttachments.DWELLER_ANGER, 0.0); // fresh cast — no dread yet (persisted)
        // player (visual) hallucinations reuse the Delusions curse wholesale. KEEP_LONGER means if you already
        // have Delusions this just coexists (they stack).
        EffectManager.apply(target, Curses.DELUSIONS, durationTicks, null);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        State state = STATES.remove(target.getUUID());
        if (state != null) {
            despawn(state);
            clearEyes(target, state);
            clearWatch(target, state);
        }
        freeStuckPossessed(target.serverLevel(), target.getBoundingBox().inflate(64.0)); // never leave a mob bricked
        target.setData(WitchModAttachments.DWELLER_ACTIVE, -1);
        target.setData(WitchModAttachments.DWELLER_DREAD, 0.0F);
        target.setData(WitchModAttachments.DWELLER_ANGER, -1.0); // curse gone — next cast starts fresh
        target.setData(WitchModAttachments.DWELLER_MIMIC, 0L); // dismiss any impostor mid-vignette
        target.setData(WitchModAttachments.DWELLER_BREATHING, 0); // stop the breathing loop
    }

    /**
     * debug: {@code arg} = a number sets the DREAD stat (clamped to the curse's own 0..max), otherwise names a
     * sub-event to force. with no arg, forces a manifestation.
     */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        int tier = target.getData(WitchModAttachments.DWELLER_ACTIVE); // 0..3 (3 = chase); -1 while dormant
        return java.util.Optional.of("@witchmod.scry.dweller.tier" + Math.max(0, Math.min(3, tier)));
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        State s = STATES.get(target.getUUID());
        if (s == null) {
            return "not active — apply witchmod:haunted first";
        }
        ServerLevel level = target.serverLevel();
        double max = Config.DWELLER_ANGER_MAX.get();
        if (arg == null) {
            enterStalk(target, s, tier(s.anger));
            return "forced a stalk encounter (tier " + tier(s.anger) + ")";
        }
        try {
            double v = Double.parseDouble(arg);
            s.anger = Mth.clamp((float) v, 0.0F, (float) max);
            target.setData(WitchModAttachments.DWELLER_DREAD, (float) (s.anger / max));
            target.setData(WitchModAttachments.DWELLER_ACTIVE, s.phase == Phase.CHASE ? 3 : tier(s.anger));
            return String.format("dread set to %.1f / %d (tier %d)", s.anger, (int) max, tier(s.anger));
        } catch (NumberFormatException ignored) {
            // fall through to named sub-event
        }
        int tier = tier(s.anger);
        // core state-machine sub-events that aren't in the DwellerEvents pool.
        switch (arg.toLowerCase(Locale.ROOT)) {
            case "tier0", "t0" -> { return setTierDebug(target, s, 0); }
            case "tier1", "t1" -> { return setTierDebug(target, s, 1); }
            case "tier2", "t2" -> { return setTierDebug(target, s, 2); }
            case "tier3", "t3" -> { return setTierDebug(target, s, 3); }
            case "foreplay" -> { beginForeplay(target, s); return "back to the FOREPLAY opening (no dread/fog)"; }
            case "tell" -> { enterTell(target, s, tier); return "the tell begins"; }
            case "stalk", "manifest" -> { enterStalk(target, s, tier); return "stalking"; }
            case "chase" -> { beginChase(target, s); return "the hunt begins"; }
            case "hallucinate", "sound" -> { hallucinateSound(target, s, tier); return "auditory hallucination"; }
            default -> { /* fall through to the registry */ }
        }
        // any pool event, by its id (spawn a body first so the in-view scares have something to place).
        DwellerEvent event = DwellerEvents.byId(arg);
        if (event != null) {
            ensureEntity(target, s);
            boolean ok = event.run(this, target, s, level);
            return ok ? "forced '" + event.id() + "'" : "'" + event.id() + "' had no valid target/precondition (fired what it could)";
        }
        return "unknown sub-event '" + arg + "'. try a number (set dread), manifest, chase, hallucinate, or any of: "
                + DwellerEvents.ALL.stream().map(DwellerEvent::id).reduce((a, b) -> a + ", " + b).orElse("");
    }

    /** tab-completion for {@code /bewitch debug force witchmod:haunted}: the beats + every forcible event. */
    @Override
    public List<String> debugArgs() {
        List<String> args = new ArrayList<>(List.of("tier0", "tier1", "tier2", "tier3",
                "foreplay", "tell", "stalk", "manifest", "chase", "hallucinate"));
        for (DwellerEvent e : DwellerEvents.ALL) {
            args.add(e.id());
        }
        return args;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        State state = STATES.computeIfAbsent(target.getUUID(), k -> {
            State fresh = new State();
            beginForeplay(target, fresh);
            fresh.ambientCd = ambientGap(target);
            // restore PERSISTED dread across a relog / world-reload / normal death, so it doesn't reset to 0.
            double saved = target.getData(WitchModAttachments.DWELLER_ANGER);
            if (saved > 0.5) {
                fresh.anger = Math.min(saved, Config.DWELLER_ANGER_MAX.get());
                fresh.phase = Phase.LULL;                 // resume the haunt rather than replaying foreplay
                fresh.foreplayTimer = 0;
                fresh.timer = dormantDuration(target, dreadFrac(fresh));
            }
            return fresh;
        });
        state.age++;
        if (state.chaseCooldown > 0) {
            state.chaseCooldown--;
        }

        // POSSESSION + BEHIND are ticked HERE (before the foreplay early-return) so a possession started at any
        // phase — including a debug-forced one during foreplay — always advances (twitch → march → resolve)
        // instead of freezing a mob with its AI off forever.
        tickPossession(target, state, target.serverLevel());
        tickBehind(target, state, tier(state.anger));
        if (state.possessCd > 0) {
            state.possessCd--;
        }
        if (state.behindCd > 0) {
            state.behindCd--;
        }

        // FOREPLAY is dread-free and near-silent — run its own tiny loop and skip the whole dread/ambience
        // machine (no fog, no desaturation, music untouched, no heartbeat/mood).
        if (state.phase == Phase.FOREPLAY) {
            processPending(target, state);
            tickForeplay(target, state);
            target.setData(WitchModAttachments.DWELLER_ACTIVE, 0);
            target.setData(WitchModAttachments.DWELLER_DREAD, 0.0F);
            return;
        }

        // dread now climbs slowly and CAN recede with counterplay (see dreadDelta) — but only DYING TO THE
        // CHASE fully resets it (onVictimDeath, phase == CHASE → 0).
        double max = Config.DWELLER_ANGER_MAX.get();
        state.anger = Mth.clamp(state.anger + dreadDelta(target, state), 0.0, max);
        int tier = tier(state.anger);
        // boredom bookkeeping: how long since an encounter, and how long stuck at tier 0.
        state.sinceStalk++;
        state.tier0Ticks = tier == 0 ? state.tier0Ticks + 1 : 0;
        target.setData(WitchModAttachments.DWELLER_ACTIVE, state.phase == Phase.CHASE ? 3 : tier);
        target.setData(WitchModAttachments.DWELLER_DREAD, (float) (state.anger / max));
        target.setData(WitchModAttachments.DWELLER_ANGER, state.anger); // persist so relog doesn't reset dread

        tickPersistent(target, state, tier);

        switch (state.phase) {
            case LULL -> tickLull(target, state, tier);
            case TELL -> tickTell(target, state, tier);
            case STALK -> tickStalk(target, state, tier);
            case RELEASE -> tickRelease(target, state, tier);
            case CHASE -> driveChase(target, state);
            default -> { /* FOREPLAY handled above */ }
        }
    }

    // --- FOREPLAY: the dread-free opening (setup for the payoff) --------------------------------------

    void beginForeplay(ServerPlayer target, State state) {
        state.phase = Phase.FOREPLAY;
        state.anger = 0.0;
        state.heat = 0.0;
        int min = Config.DWELLER_FOREPLAY_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.DWELLER_FOREPLAY_MAX_TICKS.get());
        state.foreplayTimer = min + target.getRandom().nextInt(max - min);
        state.timer = foreplayWatchGap(target); // gap until the first distant watch
    }

    void tickForeplay(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        // the whole phase winds down; when it's spent, the real haunt begins.
        if (--state.foreplayTimer <= 0) {
            despawn(state);
            state.phase = Phase.LULL;
            state.timer = dormantDuration(target, 0.0);
            return;
        }
        if (state.entity == null) {
            // waiting quietly. Very occasionally, drop ONE faint, far mood sting (near-silence otherwise).
            if (state.foreplayTimer % 200 == 0 && target.getRandom().nextInt(4) == 0) {
                playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), 0.28F, 0.9F);
            }
            if (--state.timer <= 0) {
                // A watch appears — primarily FAR (75%), sometimes MEDIUM (22%), rarely CLOSE (3%). Passive.
                state.watchTier = pickWatchTier(0.0, true, target.getRandom());
                ensureEntity(target, state);
                if (state.entity != null) {
                    placeAt(state.entity, watchSpot(target, level, state.watchTier), target, level);
                    state.timer = 120 + target.getRandom().nextInt(200); // watched for 6–16s
                    playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), 0.35F, 0.8F);
                } else {
                    state.timer = foreplayWatchGap(target);
                }
            }
        } else {
            faceVictimHead(state.entity, target);
            // same hard rule as the real watches: approach it and it's gone.
            boolean gone = state.entity.distanceTo(target) <= Config.DWELLER_WATCH_VANISH_DISTANCE.get();
            if (gone || --state.timer <= 0) {
                despawn(state);
                state.timer = foreplayWatchGap(target); // back to the quiet until the next one
            }
        }
    }

    static int foreplayWatchGap(ServerPlayer target) {
        int min = Config.DWELLER_FOREPLAY_WATCH_GAP_MIN.get();
        int max = Math.max(min + 1, Config.DWELLER_FOREPLAY_WATCH_GAP_MAX.get());
        return min + target.getRandom().nextInt(max - min);
    }

    /**
     * per-tick dread change — a SLOW inexorable climb that quickens in the dark, alone, at night, and sealed
     * underground (it likes you vulnerable), but which you can now push DOWN with real counterplay: stay in the
     * light, near people, in daylight, and above all IGNORE it (don't look at it while it's manifest). Net dread
     * can fall (clamped at 0), so a careful player holds it back — but the accelerants mean it still creeps up
     * overall whenever you can't stay safe. Tuned so a dread session peaks in ~13–18 min, not ~4.
     */
    static double dreadDelta(ServerPlayer target, State state) {
        if (state.phase == Phase.FOREPLAY) {
            return 0.0; // the opening phase has no dread at all
        }
        ServerLevel level = target.serverLevel();
        BlockPos pos = target.blockPosition();
        int light = level.getMaxLocalRawBrightness(pos);
        boolean bright = light >= Config.DWELLER_LIGHT_LEVEL.get();
        boolean dark = light <= Config.DWELLER_DARK_LEVEL.get();
        boolean day = level.isDay();
        boolean sky = level.canSeeSky(pos);
        int company = nearbyPlayers(target);

        double rise = Config.DWELLER_BASE_ANGER_PER_SECOND.get();
        if (dark) rise += Config.DWELLER_DARK_DREAD.get();
        if (company == 0) rise += Config.DWELLER_ISOLATION_DREAD.get();
        if (!day) rise += Config.DWELLER_NIGHT_DREAD.get();
        if (!sky) rise += Config.DWELLER_ENCLOSED_DREAD.get();
        // BOREDOM: at low tiers, if nothing has happened for a while, the haunt festers faster — so the early
        // game can't stall for lack of events. An EXTRA boost once you've been stuck at tier 0 far too long.
        int t = tier(state.anger);
        if (t <= 1 && state.sinceStalk > Config.DWELLER_BOREDOM_DELAY_TICKS.get()) {
            rise += Config.DWELLER_BOREDOM_BOOST.get();
        }
        if (state.tier0Ticks >= Config.DWELLER_TIER0_STUCK_TICKS.get()) {
            rise += Config.DWELLER_TIER0_STUCK_BOOST.get();
        }

        // COUNTERPLAY — these subtract, and CAN take the net below zero (dread recedes).
        double calm = 0.0;
        if (bright) calm += Config.DWELLER_LIGHT_CALM.get() * (light / 15.0);
        if (company > 0) calm += Config.DWELLER_COMPANY_CALM.get() * Math.min(company, 3);
        if (day && sky) calm += Config.DWELLER_DAYLIGHT_CALM.get();
        // the big one: while it's HERE and you're NOT looking at it, ignoring it genuinely calms the haunt.
        if (state.entity != null && state.entity.isAlive() && !isObserving(target, state.entity, level)) {
            calm += Config.DWELLER_IGNORE_CALM.get();
        }

        return (rise - calm) / 20.0;
    }

    // --- LULL: genuine quiet (this is what makes the rest land) ---------------------------------------

    void tickLull(ServerPlayer target, State state, int tier) {
        if (state.entity != null) {
            despawn(state);
        }
        state.heat = Math.max(0.0, state.heat - 0.05);
        // asleep at tier 1+: the bedside vigil comes for you now, rather than waiting for the next scheduled beat
        // (sleep is too brief to line up otherwise — which is why the bed event felt like it never happened).
        if (target.isSleeping() && tier >= 1) {
            enterStalk(target, state, tier);
            return;
        }
        // the world still stirs between encounters — a door, a knock, a light going out, footsteps. Atmosphere
        // (and the odd dread nudge) so the lull isn't dead air.
        tickAmbientEvents(target, state, tier);
        if (--state.timer <= 0) {
            enterTell(target, state, tier);
        }
    }

    /**
     * between encounters the creature meddles with the world nearby: mostly ATMOSPHERIC (a door creaks, a knock,
     * distant footsteps, cold breath) and occasionally INTERACTIVE with a dread cost (it snuffs a light, throws
     * your dropped items, breaks something). Paced on a dread-scaled timer so the lull feels haunted, not busy.
     */
    void tickAmbientEvents(ServerPlayer target, State state, int tier) {
        state.ambientEventCd -= chaosStep(state);
        if (state.ambientEventCd > 0) {
            return;
        }
        double frac = dreadFrac(state);
        state.ambientEventCd = (int) Mth.lerp((float) frac, 500.0F, 240.0F) + target.getRandom().nextInt(280); // ~12–39s (sparser)
        ServerLevel level = target.serverLevel();
        RandomSource r = target.getRandom();
        // A VERY rare explode can happen from tier 1 — a sudden gib + a spook of everything nearby.
        if (tier >= 1 && r.nextInt(100) < 2) {
            explodeUntamedMob(target, level);
            return;
        }
        int roll = r.nextInt(100);
        if (roll < 12) {
            interactionEvent(target, state, level);                     // it meddles with a door/chest/barrel
        } else if (roll < 24) {
            knockEvent(target, state, level);                          // a knock, then a shatter
        } else if (roll < 40) {
            ambientFootsteps(target, state, r);                        // circling / running at you / sneaking up
        } else if (roll < 49) {
            // something falls / thuds somewhere far off in the dark
            double a = r.nextDouble() * Math.PI * 2;
            double d = 14.0 + r.nextDouble() * 16.0;
            playToVictimAt(target, WitchModSounds.DWELLER_BANG.get(),
                    target.getX() + Math.cos(a) * d, target.getY(), target.getZ() + Math.sin(a) * d, 0.35F, 0.8F);
        } else if (roll < 57) {
            // a cold rush of WIND passing behind you
            Vec3 b = target.position().add(directionBehind(target).scale(3.0));
            playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), b.x, b.y + 1.0, b.z, 0.55F, 1.0F);
        } else if (roll < 65) {
            DwellerEvents.WHISPER.run(this, target, state, level); // a whisper right at your ear (a line)
        } else if (roll < 73) {
            ambientSettle(target, level, r);                          // the place creaks / settles nearby
        } else if (roll < 80) {
            ambientMobUnease(target, level, r);                       // a nearby animal spooks (sometimes a whole radius)
        } else if (roll < 86 && frac > 0.35) {
            // a far, quiet scream — a red herring: was that the hunt? (it wasn't)
            double a = r.nextDouble() * Math.PI * 2;
            double d = 22.0 + r.nextDouble() * 18.0;
            playToVictimAt(target, WitchModSounds.DWELLER_SCREAM.get(),
                    target.getX() + Math.cos(a) * d, target.getY(), target.getZ() + Math.sin(a) * d, 0.3F, 1.0F);
        } else if (roll < 91 && tier >= 1) {
            breakShapeEvent(target, level);                            // INTERACTIVE: shatters a shape out of view
        } else if (roll < 95 && tier >= 1) {
            if (itemPoltergeist(target, level)) {                     // INTERACTIVE: it rifles your dropped items
                addInteractDread(state);
            }
        } else if (roll < 98 && tier >= 1) {
            if (snuffOneLight(target, level)) {                       // INTERACTIVE: it snuffs a nearby light
                playToVictim(target, SoundEvents.FIRE_EXTINGUISH, 0.7F, 0.5F);
                addInteractDread(state);
            }
        } else {
            playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), 0.4F + 0.3F * (float) frac, 0.9F); // a mood swell
        }
    }

    /** footsteps in the dark — circling you, RUNNING straight at you, or SNEAKING up slowly from behind. */
    void ambientFootsteps(ServerPlayer target, State state, RandomSource r) {
        int variant = r.nextInt(3);
        if (variant == 0) {
            // circling: a ring of steps around you
            double a0 = r.nextDouble() * Math.PI * 2;
            double d = 5.0 + r.nextDouble() * 4.0;
            for (int i = 0; i < 5; i++) {
                double a = a0 + i * (Math.PI * 2 / 5) * (r.nextBoolean() ? 1 : -1);
                double sx = target.getX() + Math.cos(a) * d;
                double sz = target.getZ() + Math.sin(a) * d;
                state.pending.add(new Pending((t, l) ->
                        playToVictimAt(t, WitchModSounds.DWELLER_STEP.get(), sx, t.getY(), sz, 0.6F, 1.0F), i * 3 + 1));
            }
        } else if (variant == 1) {
            // RUNNING at you: fast steps closing the distance from a random bearing, then stopping dead
            double a = r.nextDouble() * Math.PI * 2;
            double startD = 12.0 + r.nextDouble() * 6.0;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            for (int i = 0; i < 8; i++) {
                double d = startD * (1.0 - i / 8.0) + 1.5;
                double sx = target.getX() + dir.x * d;
                double sz = target.getZ() + dir.z * d;
                state.pending.add(new Pending((t, l) ->
                        playToVictimAt(t, WitchModSounds.DWELLER_STEP.get(), sx, t.getY(), sz, 0.85F, 1.25F), i * 2 + 1));
            }
        } else {
            // SNEAKING up: slow, quiet steps creeping in from behind, ending right at your back
            Vec3 behind = directionBehind(target);
            for (int i = 0; i < 6; i++) {
                double d = 7.0 * (1.0 - i / 6.0) + 1.0;
                double sx = target.getX() + behind.x * d;
                double sz = target.getZ() + behind.z * d;
                state.pending.add(new Pending((t, l) ->
                        playToVictimAt(t, WitchModSounds.DWELLER_STEP.get(), sx, t.getY(), sz, 0.35F, 0.85F), i * 6 + 1));
            }
        }
    }

    /** shatters a small SHAPE (cross / line / ring) out of a wall nearby but out of your view — with drops. */
    boolean breakShapeEvent(ServerPlayer target, ServerLevel level) {
        RandomSource r = target.getRandom();
        // find a solid block out of your line of sight to anchor the shape on.
        BlockPos origin = target.blockPosition();
        BlockPos anchor = null;
        for (int i = 0; i < 40; i++) {
            BlockPos p = origin.offset(r.nextInt(13) - 6, r.nextInt(5) - 1, r.nextInt(13) - 6);
            BlockState st = level.getBlockState(p);
            if (isCarvable(st) && !clearLineOfSight(level, target.getEyePosition(), p.getCenter())) {
                anchor = p.immutable();
                break;
            }
        }
        if (anchor == null) {
            return false;
        }
        // pick a shape in the vertical plane facing the anchor's most open side.
        boolean alongX = Math.abs(target.getX() - anchor.getX()) <= Math.abs(target.getZ() - anchor.getZ());
        List<BlockPos> shape = carveShape(anchor, alongX, r);
        boolean any = false;
        for (BlockPos p : shape) {
            if (isCarvable(level.getBlockState(p))) {
                level.destroyBlock(p, true); // dropped, so it's recoverable
                any = true;
            }
        }
        if (any) {
            playToVictimAt(target, SoundEvents.STONE_BREAK, anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5, 1.0F, 0.7F);
            particleToVictim(target, ParticleTypes.SMOKE, anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.01);
        }
        return any;
    }

    /** A cross / vertical line / small ring around the anchor, in the X or Z vertical plane. */
    private static List<BlockPos> carveShape(BlockPos c, boolean alongX, RandomSource r) {
        List<BlockPos> out = new ArrayList<>();
        java.util.function.BiFunction<Integer, Integer, BlockPos> at =
                (h, v) -> alongX ? c.offset(h, v, 0) : c.offset(0, v, h);
        switch (r.nextInt(3)) {
            case 0 -> { // cross / plus
                for (int d = -1; d <= 1; d++) {
                    out.add(at.apply(d, 0));
                    out.add(at.apply(0, d));
                }
            }
            case 1 -> { // vertical line
                for (int v = -1; v <= 2; v++) {
                    out.add(at.apply(0, v));
                }
            }
            default -> { // small ring / square outline
                out.add(at.apply(-1, 0));
                out.add(at.apply(1, 0));
                out.add(at.apply(0, 1));
                out.add(at.apply(0, -1));
                out.add(at.apply(-1, 1));
                out.add(at.apply(1, 1));
                out.add(at.apply(-1, -1));
                out.add(at.apply(1, -1));
            }
        }
        return out;
    }

    /** ordinary building blocks it's allowed to carve — never bedrock/containers/ores/valuables. */
    static boolean isCarvable(BlockState st) {
        if (st.isAir() || !st.getFluidState().isEmpty()) {
            return false;
        }
        if (st.getDestroySpeed(null, BlockPos.ZERO) < 0) {
            return false; // unbreakable (bedrock, barrier)
        }
        return st.is(BlockTags.MINEABLE_WITH_PICKAXE) || st.is(BlockTags.MINEABLE_WITH_AXE)
                || st.is(BlockTags.MINEABLE_WITH_SHOVEL) || st.getBlock() == Blocks.GLASS
                || st.is(BlockTags.PLANKS) || st.is(BlockTags.LOGS);
    }

    /** the place SETTLES — a nearby solid block gives a faint creak/knock, as if the building shifted. */
    private void ambientSettle(ServerPlayer target, ServerLevel level, RandomSource r) {
        BlockPos origin = target.blockPosition();
        for (int i = 0; i < 24; i++) {
            BlockPos pos = origin.offset(r.nextInt(11) - 5, r.nextInt(7) - 3, r.nextInt(11) - 5);
            BlockState st = level.getBlockState(pos);
            if (!st.isAir() && st.getFluidState().isEmpty()) {
                playToVictimAt(target, st.getSoundType().getStepSound(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.5F, 0.6F + r.nextFloat() * 0.2F);
                return;
            }
        }
        playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), 0.4F, 0.9F);
    }

    /**
     * A nearby passive animal suddenly SPOOKS — flinches and bolts, as if it saw something you didn't. 30% of
     * the time the panic is CONTAGIOUS: everything within a radius of that animal — the whole herd, and even
     * nearby HOSTILE mobs — bolts the same way at once, as though they all felt the Dweller pass through.
     */
    private void ambientMobUnease(ServerPlayer target, ServerLevel level, RandomSource r) {
        List<Animal> mobs = level.getEntitiesOfClass(Animal.class, target.getBoundingBox().inflate(16.0), Animal::isAlive);
        if (mobs.isEmpty()) {
            playToVictimAt(target, WitchModSounds.DWELLER_STEP.get(),
                    target.getX(), target.getY(), target.getZ(), 0.5F, 1.0F);
            return;
        }
        Animal m = mobs.get(r.nextInt(mobs.size()));
        if (r.nextFloat() < 0.30F) {
            // contagious panic radiating from the chosen animal — passive AND hostile alike.
            double radius = 8.0;
            List<Mob> around = level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(radius),
                    e -> e.isAlive() && !(e instanceof SpaghettiManEntity) && !(e instanceof WatcherEyesEntity));
            for (Mob e : around) {
                spookMob(e, target.position(), level);
            }
        } else {
            spookMob(m, target.position(), level);
        }
    }

    /** makes a single mob flinch and bolt AWAY from a point, with a puff of smoke — the panic gesture. */
    void spookMob(Mob m, Vec3 from, ServerLevel level) {
        Vec3 away = m.position().subtract(from);
        away = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        m.setDeltaMovement(away.x * 0.5, 0.42, away.z * 0.5);
        m.hurtMarked = true;
        level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getEyeY(), m.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
    }

    /** snuffs a SINGLE nearby light (with drop) — the atmospheric cousin of the full snuff_light set-piece. */
    boolean snuffOneLight(ServerPlayer target, ServerLevel level) {
        BlockPos origin = target.blockPosition();
        RandomSource r = target.getRandom();
        for (int i = 0; i < 40; i++) {
            BlockPos pos = origin.offset(r.nextInt(13) - 6, r.nextInt(7) - 3, r.nextInt(13) - 6);
            if (isLightSource(level.getBlockState(pos))) {
                BlockPos p = pos.immutable();
                level.destroyBlock(p, true);
                particleToVictim(target, ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 8, 0.1, 0.2, 0.1, 0.01);
                return true;
            }
        }
        return false;
    }

    // --- TELL: the anticipation beat — something's coming, nothing lunges yet -------------------------

    void enterTell(ServerPlayer target, State state, int tier) {
        state.phase = Phase.TELL;
        int min = Config.DWELLER_TELL_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.DWELLER_TELL_MAX_TICKS.get());
        // the warning shrinks the more dread has built — at the top it barely gives you time.
        state.beatTotal = Math.max(20, (int) ((min + target.getRandom().nextInt(max - min)) * (1.0 - 0.30 * tier / 3.0)));
        state.timer = state.beatTotal;
        state.signGiven = false;
        state.heat = 0.2;
        playToVictim(target, SoundEvents.WARDEN_LISTENING, 0.55F, 0.5F); // "it has noticed you"
    }

    void tickTell(ServerPlayer target, State state, int tier) {
        double p = 1.0 - state.timer / (double) Math.max(1, state.beatTotal);
        state.heat = 0.25 + 0.5 * p; // heartbeat quickens across the beat
        // one mid-beat sign — AUDIO only now (no snowflake dust, no screen flicker): a low mood swell from
        // somewhere behind you that something is about to happen.
        if (!state.signGiven && p >= 0.5) {
            state.signGiven = true;
            Vec3 b = target.position().add(directionBehind(target).scale(4.0));
            playToVictimAt(target, WitchModSounds.DWELLER_MOOD.get(), b.x, b.y + 1.0, b.z, 0.55F, 0.85F);
        }
        if (--state.timer <= 0) {
            enterStalk(target, state, tier);
        }
    }

    // --- STALK: it's here, and it only moves when you look away (weeping angel) -----------------------

    void enterStalk(ServerPlayer target, State state, int tier) {
        ServerLevel level = target.serverLevel();
        // NO DUPLICATES: don't spawn the real dweller while a mimic vignette is running — wait it out.
        if (target.getData(WitchModAttachments.DWELLER_MIMIC) != 0L) {
            state.phase = Phase.LULL;
            state.timer = 40;
            return;
        }
        // TIER 2/3: sometimes it stops hanging back and walks right up to SIZE YOU UP, face to face — a sting of
        // its rising aggression that can boil over into a lunge or the hunt.
        if (!target.isSleeping() && tier >= 2 && target.getRandom().nextFloat() < (tier >= 3 ? 0.35F : 0.22F)
                && enterSizeUp(target, state, tier)) {
            return;
        }
        // every stalk is now a passive WATCH in one of three distance bands (FAR/MEDIUM/CLOSE), chosen by dread
        // — FAR when calm, CLOSE near max. It stands and watches; it never creeps. All the aggression comes from
        // the bridges below (stare/proximity → vanish; high dread → lunge; max dread → chase).
        state.windowWatch = false;
        if (target.isSleeping() && tier >= 1) {
            state.watchTier = WATCH_CLOSE;
            ensureEntity(target, state);
            if (state.entity != null) {
                placeAt(state.entity, bedFootSpot(target, level), target, level); // at the FOOT of the bed, in view
            }
        } else {
            state.watchTier = pickWatchTier(dreadFrac(state), false, target.getRandom());
            // ~40% of the time, if there's real GLASS to peer through, it presses up against the far side of that
            // window instead — a window watch that only leaves once you get a clear line of sight on it.
            Vec3 spot = null;
            if (target.getRandom().nextFloat() < 0.40F) {
                Vec3 win = windowWatchSpot(target, level, 0);
                if (win != null) {
                    spot = win;
                    state.windowWatch = true;
                }
            }
            if (spot == null) {
                spot = watchSpot(target, level, state.watchTier);
            }
            ensureEntity(target, state);
            if (state.entity == null) {
                state.phase = Phase.LULL;
                state.timer = 40;
                return;
            }
            placeAt(state.entity, spot, target, level);
        }
        if (state.entity == null) {
            state.phase = Phase.LULL;
            state.timer = 40;
            return;
        }
        state.phase = Phase.STALK;
        state.watched = 0;
        state.hasBeenSeen = false;
        state.lungeTicks = 0;
        state.sizeUp = false;
        state.sinceStalk = 0; // an encounter is happening — the boredom clock resets
        state.beatTotal = manifestLifespan(target, tier);
        state.timer = state.beatTotal;
        state.heat = state.watchTier == WATCH_CLOSE ? 0.75 : 0.55;
        if (!state.discovered) {
            state.discovered = true;
            Curses.THE_DWELLER.get().markDiscoveredByVictim(target);
        }
    }

    void tickStalk(ServerPlayer target, State state, int tier) {
        ServerLevel level = target.serverLevel();
        SpaghettiManEntity dweller = state.entity;
        if (dweller == null || !dweller.isAlive()) {
            enterRelease(target, state, tier);
            return;
        }
        // SIZEUP has its own passive-in-front behaviour (approach/stare ramps aggression → lunge/chase).
        if (state.sizeUp) {
            tickSizeUp(target, state, tier);
            return;
        }
        faceVictimHead(dweller, target);

        // LUNGING: it's mid-charge toward you — driveLunge moves it; the watch rules (within-4 / stare vanish) are
        // suspended so it isn't dispelled before you see it arrive.
        if (state.lungeTicks > 0) {
            tickLunge(target, state, tier);
            return;
        }

        // bedside vigil: asleep, it looms over you and watches — with a chance to wrench the bed apart. When it
        // DOES break the bed (or the beat runs out) the encounter ENDS cleanly (the figure withdraws). This is the
        // fix for the "bed just insta-kills me": the vigil no longer lingers at ~1.4 blocks after you're thrown
        // out of bed, where the within-4 proximity rule was bridging straight into a touch-kill.
        if (target.isSleeping()) {
            boolean broke = bedVigil(target, state, tier, level);
            if (broke || --state.timer <= 0) {
                enterRelease(target, state, tier);
            }
            return;
        }

        boolean maxDread = tier >= 3;
        boolean observed = isObserving(target, dweller, level);
        double dist = dweller.distanceTo(target);
        double prox = Mth.clamp(1.0 - dist / 16.0, 0.0, 1.0);
        state.heat = Math.max(0.6, prox); // heartbeat pounds as it looms

        // BREATHING loops (client-side, volume by proximity) while it's close and watching you.
        setBreathing(target, state, dist < 11.0);

        // BREATH: close BEHIND you and unseen → a subtle warning it's right there, and it ARMS a turn-to-face
        // jumpscare. Turning round to find it inches away is exactly the bad idea the breath warns against.
        Vec3 dpos = dweller.position();
        boolean behind = !observed && dist < 6.5 && isBehind(target, dweller);
        if (behind) {
            state.breathArm = 12;
            if (--state.breathCd <= 0) {
                playToVictimAt(target, WitchModSounds.DWELLER_BREATH.get(), dpos.x, dpos.y + 1.4, dpos.z, 0.3F + 0.4F * (float) prox, 1.0F);
                state.breathCd = 45 + target.getRandom().nextInt(45);
            }
        } else if (state.breathCd > 0) {
            state.breathCd--;
        }
        if (state.breathArm > 0) {
            state.breathArm--;
            if (observed && dist < 7.0) {
                playToVictimAt(target, WitchModSounds.DWELLER_BREATH.get(), dpos.x, dpos.y + 1.4, dpos.z, 1.2F, 0.85F);
                state.heat = 1.0;
                state.breathArm = 0;
            }
        }

        // HARD RULE: get within the vanish distance of ANY watch and it blinks away — you can never close on it.
        // approaching it is INTERACTING, not ignoring, so it costs you a chunk of dread. At higher dread crowding
        // it can BRIDGE the watch straight into a hunt from where it stands instead of blinking away.
        // EXEMPTION: a WINDOW watch is meant to be close (pressed against the glass), so the within-N rule doesn't
        // apply to it — it only leaves once you get a clear line of sight (handled just below).
        if (!state.windowWatch && dist <= Config.DWELLER_WATCH_VANISH_DISTANCE.get()) {
            addInteractDread(state);
            if (maybeBridgeChase(target, state)) {
                return;
            }
            vanishWatch(target, state, tier);
            return;
        }

        // WINDOW WATCH: it's only meant to observe THROUGH a window. Catch it with a direct line of sight and
        // it's gone instantly — no stare timer.
        if (state.windowWatch && observed && clearLineOfSight(level, target.getEyePosition(), dpos.add(0, 1.6, 0))) {
            vanishWatch(target, state, tier);
            return;
        }

        if (observed) {
            state.watched++;
            state.hasBeenSeen = true;
            // MAX dread: meeting its gaze IS the trigger — hold eye contact briefly and it bridges into the hunt
            // (gated by the chase cooldown so hunts don't stack instantly).
            if (maxDread && state.chaseCooldown <= 0 && state.watched >= Config.DWELLER_MAX_DREAD_LOOK_TICKS.get()) {
                beginChase(target, state);
                return;
            }
            // your gaze still stokes the haunt a little.
            state.anger = Math.min(Config.DWELLER_ANGER_MAX.get(),
                    state.anger + Config.DWELLER_WATCH_ANGER_PER_SECOND.get() / 20.0);
            // HARD RULE: stare at ANY watch for > 3s and it MUST go. Depending on dread this departure can BRIDGE
            // into a hunt from where it stands, or (tier 2) LUNGE, else the usual run-off-and-vanish.
            if (state.watched >= Config.DWELLER_STARE_VANISH_TICKS.get()) {
                if (maybeBridgeChase(target, state)) {
                    return;
                }
                if (tier == 2) {
                    lungeScare(target, state); // watch → lunge bridge
                    return;
                }
                vanishWatch(target, state, tier);
                return;
            }
        } else {
            // LOOK-AWAY: if you've already seen it and then look away, it may just be gone when you look back —
            // very likely at low dread (~70%), almost never at high dread (~5%).
            if (state.hasBeenSeen && state.watched > 0) {
                double chance = Mth.lerp((float) dreadFrac(state), 0.70F, 0.05F);
                if (target.getRandom().nextFloat() < chance) {
                    vanishWatch(target, state, tier);
                    return;
                }
            }
            state.watched = 0;
        }
        if (--state.timer <= 0) {
            vanishWatch(target, state, tier); // it was there, then it wasn't
        }
    }

    /** bonus dread for INTERACTING with an effect (swinging at it / crowding it) — ignoring is the counterplay. */
    void addInteractDread(State state) {
        state.anger = Math.min(Config.DWELLER_ANGER_MAX.get(), state.anger + Config.DWELLER_INTERACT_DREAD.get());
    }

    /**
     * A watch's departure. Rather than a straight poof it usually RUNS out of your line of sight first — 78% at
     * FAR, 60% at MEDIUM, 28% at CLOSE — footsteps trailing off to sell it, then it's gone. Otherwise it just
     * isn't there any more.
     */
    void vanishWatch(ServerPlayer target, State state, int tier) {
        SpaghettiManEntity e = state.entity;
        // it much prefers to RUN and hide (footsteps trailing off) over blinking out on the spot.
        double runChance = switch (state.watchTier) {
            case WATCH_CLOSE -> 0.75;
            case WATCH_MEDIUM -> 0.90;
            default -> 0.97;
        };
        if (e != null && target.getRandom().nextFloat() < runChance) {
            ServerLevel level = target.serverLevel();
            Vec3 from = e.position();
            Vec3 flee = hiddenSpot(target, level, 7.0 + target.getRandom().nextDouble() * 6.0); // a non-LOS spot
            Vec3 to = flee != null ? flee : behindSpot(target, 9.0);
            for (int i = 1; i <= 4; i++) {
                final double f = i / 4.0;
                state.pending.add(new Pending((t, l) -> {
                    Vec3 p = from.lerp(to, f);
                    playToVictimAt(t, WitchModSounds.DWELLER_STEP.get(), p.x, p.y, p.z, 0.6F, 1.15F);
                }, i * 2));
            }
        }
        resolveSpike(target, state, tier, false);
    }

    /** bedside vigil — it looms over the sleeper; on a roll it rips the bed apart and throws you out. */
    /** the bedside vigil. Returns true the tick it wrenches the bed apart, so the caller ends the encounter. */
    private boolean bedVigil(ServerPlayer target, State state, int tier, ServerLevel level) {
        state.heat = 0.85;
        if (target.tickCount % 18 == 0) {
            playToVictim(target, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.5F);
            if (target.getRandom().nextFloat() < Config.DWELLER_BED_BREAK_CHANCE.get() && breakBedUnder(target, level)) {
                playToVictim(target, SoundEvents.WOOD_BREAK, 1.0F, 0.7F);
                playToVictim(target, SoundEvents.WARDEN_ANGRY, 0.7F, 0.7F);
                state.anger = Math.min(Config.DWELLER_ANGER_MAX.get(), state.anger + 5.0);
                return true;
            }
        }
        return false;
    }

    /** weeping-angel creep. Won't close past the light-lurk distance while you stand in bright light. */


    // --- SPIKE: exactly ONE payoff, then RELEASE ------------------------------------------------------

    void resolveSpike(ServerPlayer target, State state, int tier, boolean reachedYou) {
        RandomSource rng = target.getRandom();
        if (reachedYou) {
            // it reached you. At the top this can tip into the hunt; otherwise a single close-encounter hit.
            if (tier >= 3 && state.chaseCooldown <= 0 && rng.nextFloat() < 0.6F) {
                beginChase(target, state);
                return;
            }
            spikeCloseEncounter(target, state, tier);
        } else {
            // you held your ground. Usually it just isn't there any more (the double-take); once in a while it
            // goes out with a single set-piece instead of quietly.
            if (tier >= 1 && rng.nextFloat() < 0.30F) {
                oneSetPiece(target, state, tier);
            } else if (state.entity != null) {
                Vec3 p = state.entity.position();
                playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), p.x, p.y + 1.0, p.z, 0.5F, 1.0F); // it's just... gone
            }
        }
        enterRelease(target, state, tier);
    }

    private void spikeCloseEncounter(ServerPlayer target, State state, int tier) {
        Vec3 push = target.position().subtract(state.entity != null ? state.entity.position() : target.position());
        if (push.lengthSqr() > 1.0E-4) {
            push = push.normalize().scale(0.5);
            target.push(push.x, 0.25, push.z);
            target.hurtMarked = true;
        }
        playToVictim(target, WitchModSounds.DWELLER_SCREAM.get(), 0.9F, 1.0F); // the custom scream, not enderman
        dwellerShake(target); // it reached you — jolt the camera
        if (tier >= 2) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        }
        state.anger = Math.min(Config.DWELLER_ANGER_MAX.get(), state.anger + 6.0 + 4.0 * tier);
    }

    // --- RELEASE: the exhale --------------------------------------------------------------------------

    void enterRelease(ServerPlayer target, State state, int tier) {
        if (state.entity != null) {
            Vec3 p = state.entity.position();
            particleToVictim(target, ParticleTypes.SMOKE, p.x, p.y + 1.0, p.z, 14, 0.3, 0.7, 0.3, 0.01);
            despawn(state);
        }
        state.phase = Phase.RELEASE;
        int min = Config.DWELLER_RELEASE_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.DWELLER_RELEASE_MAX_TICKS.get());
        state.timer = min + target.getRandom().nextInt(max - min);
        if (!state.discovered) {
            state.discovered = true;
            Curses.THE_DWELLER.get().markDiscoveredByVictim(target);
        }
    }

    void tickRelease(ServerPlayer target, State state, int tier) {
        state.heat = Math.max(0.0, state.heat - 0.04);
        if (--state.timer <= 0) {
            state.phase = Phase.LULL;
            state.timer = dormantDuration(target, dreadFrac(state));
            state.ambientCd = ambientGap(target);
        }
    }

    // --- Staged mini-events -----------------------------------------------------------------------------

    /** A weighted, retryable candidate event: {@code fn} returns false if it couldn't fire (no target found). */
    private record Cand(int weight, BooleanSupplier fn) {}

    /**
     * ONE curated set-piece as an occasional alternative spike ending — a tight, hand-picked shortlist rather
     * than the old 30-event slot machine. The other events still exist (and are debug-forcible), just not on
     * an auto-timer, so the encounter's ONE payoff stays the focus. Runs while the body is still present.
     */
    void oneSetPiece(ServerPlayer target, State state, int tier) {
        ServerLevel level = target.serverLevel();
        RandomSource random = target.getRandom();
        List<Cand> pool = new ArrayList<>();
        add(pool, 10, DwellerEvents.SHADOW_PASS, target, state, level);  // footsteps circling + a rush of wind
        add(pool, 9, DwellerEvents.INTERACTION, target, state, level);   // it swings a door / opens a chest
        add(pool, 8, DwellerEvents.KNOCK, target, state, level);         // knocks, then a shatter
        if (tier >= 2) {
            add(pool, 11, DwellerEvents.LIGHTS_OUT, target, state, level);       // snuffs the lights, plunges to dark
            add(pool, 8, DwellerEvents.EXPLODE_MOB, target, state, level);       // gibs a nearby untamed mob
            add(pool, 7, DwellerEvents.ITEM_POLTERGEIST, target, state, level);  // your dropped items fling up
        }
        if (!runWeighted(pool, random)) {
            DwellerEvents.WHISPER.run(this, target, state, level); // nothing else landed — a whisper never fails
        }
    }

    /** adds a weighted pool candidate that runs a {@link DwellerEvent}. */
    private void add(List<Cand> pool, int weight, DwellerEvent event, ServerPlayer target, State state, ServerLevel level) {
        pool.add(new Cand(weight, () -> event.run(this, target, state, level)));
    }

    /** picks by weight and runs; on failure drops that candidate and retries until one fires or none remain. */
    static boolean runWeighted(List<Cand> pool, RandomSource random) {
        while (!pool.isEmpty()) {
            int total = 0;
            for (Cand c : pool) {
                total += c.weight();
            }
            int pick = random.nextInt(Math.max(1, total));
            int idx = 0;
            for (int i = 0; i < pool.size(); i++) {
                pick -= pool.get(i).weight();
                if (pick < 0) {
                    idx = i;
                    break;
                }
            }
            if (pool.get(idx).fn().getAsBoolean()) {
                return true;
            }
            pool.remove(idx);
        }
        return false;
    }

    static boolean sfxTrue(ServerPlayer target, SoundEvent sound, float vol, float pitch) {
        playToVictim(target, sound, vol, pitch);
        return true;
    }

    /** A shadow rushes past behind you: fast footsteps circling + a rush of WIND (no sculk stings). */
    void shadowPass(ServerPlayer target, State state) {
        double a0 = target.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 6; i++) {
            double a = a0 + i * (Math.PI / 3);
            double sx = target.getX() + Math.cos(a) * 3.0;
            double sz = target.getZ() + Math.sin(a) * 3.0;
            state.pending.add(new Pending((tgt, lvl) ->
                    playToVictimAt(tgt, WitchModSounds.DWELLER_STEP.get(), sx, tgt.getY(), sz, 0.8F, 1.2F), i * 2 + 1));
        }
        playToVictim(target, WitchModSounds.DWELLER_WIND.get(), 0.7F, 1.1F);
    }

    /** JUMPSCARE: a sudden loud BANG right behind you and a hard camera SHAKE — no figure, just fright. */
    void startle(ServerPlayer target) {
        RandomSource r = target.getRandom();
        Vec3 behind = target.position().add(directionBehind(target).scale(1.5));
        playToVictimAt(target, WitchModSounds.DWELLER_BANG.get(), behind.x, target.getEyeY(), behind.z, 1.0F, 0.9F + r.nextFloat() * 0.2F);
        dwellerShake(target);
    }

    /**
     * it rushes in FROM WHERE IT STANDS (the watch spot) and stops dead in your face, screaming, then it's gone.
     * Deliberately a touch slower than a blink — you see it coming across the room, which is worse. Triggered by
     * staring too long during a watch at high dread.
     */
    boolean lungeScare(ServerPlayer target, State state) {
        if (state.entity == null) {
            return false;
        }
        playToVictim(target, WitchModSounds.DWELLER_SCREAM.get(), 1.0F, 1.0F);
        // A visible phantom CHARGE — it rushes you from where it stands at a fast-but-followable pace, driven every
        // tick by tickLunge/driveLunge so you SEE it close the gap (no teleporty snap), and the jumpscare fires the
        // INSTANT it arrives (no awkward pause once it reaches you). lungeTicks is just a safety cap.
        state.lungeTicks = Config.DWELLER_LUNGE_MAX_TICKS.get();
        return true;
    }

    /** one tick of an in-progress lunge: advance the charge; on arrival (or timeout) scare, then resolve/bridge. */
    void tickLunge(ServerPlayer target, State state, int tier) {
        state.heat = 1.0;
        state.lungeTicks--;
        boolean arrived = driveLunge(target, state);
        if (arrived || state.lungeTicks <= 0) {
            if (!arrived) {
                lungeArriveScare(target, state); // capped out just short — still deliver the payoff
            }
            if (!maybeBridgeChase(target, state)) { // a lunge can tip into the hunt (dread-based)
                resolveSpike(target, state, tier, false);
            }
        }
    }

    /** moves the lunging dweller toward you at a steady, followable pace; fires the scare the instant it arrives. */
    boolean driveLunge(ServerPlayer target, State state) {
        SpaghettiManEntity e = state.entity;
        if (e == null) {
            return true;
        }
        Vec3 h = e.position();
        Vec3 flat = new Vec3(target.getX() - h.x, 0, target.getZ() - h.z);
        double gap = flat.length();
        double reach = 1.5;
        if (gap <= reach) {
            lungeArriveScare(target, state);
            return true;
        }
        Vec3 dir = flat.normalize();
        double stp = Math.min(Config.DWELLER_LUNGE_SPEED.get(), gap - reach); // slower than before, still urgent
        placeAt(e, h.add(dir.x * stp, 0, dir.z * stp), target, target.serverLevel());
        faceVictimBody(e, dir);
        return false;
    }

    /** the payoff at the end of a lunge: a breath in your face + (nearly always) the full flash jumpscare. */
    void lungeArriveScare(ServerPlayer target, State state) {
        playToVictim(target, WitchModSounds.DWELLER_BREATH.get(), 1.3F, 0.8F);
        if (target.getRandom().nextFloat() < 0.90F) {
            dwellerJumpscare(target, 60 + target.getRandom().nextInt(41)); // flinch + bright, then dark 3–5s
        } else {
            dwellerShake(target);
        }
    }

    /**
     * SIZEUP — it manifests RIGHT IN FRONT of you and just stares, unnaturally still and passive. It's a sting
     * that shows the creature's aggression is ramping: get too close, or hold its gaze too long, and the calm
     * breaks — it lunges (or, at higher dread, bridges straight into the hunt). Ignore it and it simply leaves.
     */
    boolean enterSizeUp(ServerPlayer target, State state, int tier) {
        ServerLevel level = target.serverLevel();
        if (target.getData(WitchModAttachments.DWELLER_MIMIC) != 0L) {
            return false; // never overlap a mimic vignette
        }
        ensureEntity(target, state);
        if (state.entity == null) {
            return false;
        }
        Vec3 spot = frontSpot(target, 4.5 + target.getRandom().nextDouble() * 2.0, level);
        placeAt(state.entity, spot, target, level);
        state.phase = Phase.STALK;
        state.sizeUp = true;
        state.windowWatch = false;
        state.watchTier = WATCH_CLOSE;
        state.watched = 0;
        state.hasBeenSeen = false;
        state.lungeTicks = 0;
        state.sinceStalk = 0;
        state.beatTotal = manifestLifespan(target, tier);
        state.timer = state.beatTotal;
        state.heat = 0.85;
        playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), 0.6F, 0.7F); // a low, close swell as it appears
        if (!state.discovered) {
            state.discovered = true;
            Curses.THE_DWELLER.get().markDiscoveredByVictim(target);
        }
        return true;
    }

    /** the passive front-stare, escalating to aggression on approach or a held stare. */
    void tickSizeUp(ServerPlayer target, State state, int tier) {
        ServerLevel level = target.serverLevel();
        SpaghettiManEntity d = state.entity;
        if (d == null || !d.isAlive()) {
            enterRelease(target, state, tier);
            return;
        }
        faceVictimHead(d, target);
        // A lunge kicked off from the sizeup — driveLunge charges it in and scares on arrival.
        if (state.lungeTicks > 0) {
            tickLunge(target, state, tier);
            return;
        }
        double dist = d.distanceTo(target);
        boolean observed = isObserving(target, d, level);
        state.heat = 0.9;
        setBreathing(target, state, dist < 12.0);
        // TOO CLOSE → the calm snaps. Approaching it is a provocation.
        if (dist <= Config.DWELLER_WATCH_VANISH_DISTANCE.get() + 1.0) {
            addInteractDread(state);
            sizeUpAggress(target, state, tier);
            return;
        }
        if (observed) {
            state.watched++;
            state.hasBeenSeen = true;
            state.anger = Math.min(Config.DWELLER_ANGER_MAX.get(),
                    state.anger + Config.DWELLER_WATCH_ANGER_PER_SECOND.get() / 20.0);
            // A rising tension as the seconds tick — a growl of breath partway through the stare-down.
            if (state.watched == Config.DWELLER_SIZEUP_STARE_TICKS.get() / 2) {
                Vec3 p = d.position();
                playToVictimAt(target, WitchModSounds.DWELLER_BREATH.get(), p.x, p.y + 1.4, p.z, 0.7F, 0.85F);
            }
            if (state.watched >= Config.DWELLER_SIZEUP_STARE_TICKS.get()) {
                sizeUpAggress(target, state, tier); // held the gaze too long — it commits
                return;
            }
        } else {
            state.watched = Math.max(0, state.watched - 1); // looking away eases it back off
        }
        if (--state.timer <= 0) {
            vanishWatch(target, state, tier); // you never provoked it — it loses interest and goes
        }
    }

    /** the sizeup boils over: a chase bridge (dread-based) or, failing that, a lunge in your face. */
    void sizeUpAggress(ServerPlayer target, State state, int tier) {
        if (maybeBridgeChase(target, state)) {
            return;
        }
        if (!lungeScare(target, state)) {
            resolveSpike(target, state, tier, true);
        }
    }

    /**
     * A dread-based roll for an event (watch / sizeup / lunge) to BRIDGE straight into the hunt from the
     * creature's CURRENT position — no reset, no reposition. Gated by the chase cooldown so hunts can't stack.
     */
    boolean maybeBridgeChase(ServerPlayer target, State state) {
        if (state.chaseCooldown > 0 || state.entity == null || !state.entity.isAlive()) {
            return false;
        }
        double chance = Mth.lerp((float) dreadFrac(state),
                (float) (double) Config.DWELLER_BRIDGE_CHASE_MIN_CHANCE.get(),
                (float) (double) Config.DWELLER_BRIDGE_CHASE_MAX_CHANCE.get());
        if (target.getRandom().nextFloat() >= chance) {
            return false;
        }
        beginChase(target, state);
        return true;
    }

    /** A ground spot directly in FRONT of where the player is looking, at the given distance. */
    static Vec3 frontSpot(ServerPlayer target, double dist, ServerLevel level) {
        Vec3 look = target.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        return groundSnap(target, target.position().add(flat.scale(dist)));
    }

    /** A nearby door/trapdoor/gate swings open (or shut) on its own. */
    /**
     * INTERACTION (formerly "door creak") — the creature MEDDLES with the fixtures around you on its own: swings
     * a door / trapdoor / fence gate, or OPENS a chest, trapped chest, ender chest or barrel (with the real lid
     * animation, then closes it a beat later). Deliberately GENEROUS: it scans a wide radius, and usually touches
     * exactly ONE thing but rarely 2-4 at once. This replaced the old door-only version that "did not work"
     * because it rarely found a qualifying block near the random offsets it sampled.
     */
    boolean interactionEvent(ServerPlayer target, State state, ServerLevel level) {
        RandomSource r = target.getRandom();
        BlockPos origin = target.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-7, -4, -7), origin.offset(7, 4, 7))) {
            if (isInteractable(level.getBlockState(p))) {
                candidates.add(p.immutable());
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        java.util.Collections.shuffle(candidates, new java.util.Random(r.nextLong()));
        int count = Math.min(r.nextFloat() < 0.82F ? 1 : 2 + r.nextInt(3), candidates.size()); // usually 1, rarely 2-4
        boolean any = false;
        for (int i = 0; i < count; i++) {
            any |= interactWith(target, state, level, candidates.get(i));
        }
        return any;
    }

    /** doors/trapdoors/gates (togglable) and openable containers (chest/trapped/ender/barrel). */
    static boolean isInteractable(BlockState st) {
        if ((st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.FENCE_GATES))
                && st.hasProperty(BlockStateProperties.OPEN)) {
            return true;
        }
        Block b = st.getBlock();
        return b instanceof ChestBlock || b instanceof EnderChestBlock || b instanceof BarrelBlock;
    }

    private boolean interactWith(ServerPlayer target, State state, ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        Block b = st.getBlock();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if ((st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.FENCE_GATES))
                && st.hasProperty(BlockStateProperties.OPEN)) {
            boolean open = st.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, st.setValue(BlockStateProperties.OPEN, !open), 3);
            if (st.is(BlockTags.DOORS)) { // keep both halves in step
                for (BlockPos n : new BlockPos[]{pos.above(), pos.below()}) {
                    BlockState ns = level.getBlockState(n);
                    if (ns.is(BlockTags.DOORS) && ns.hasProperty(BlockStateProperties.OPEN)) {
                        level.setBlock(n, ns.setValue(BlockStateProperties.OPEN, !open), 3);
                    }
                }
            }
            SoundEvent s = st.is(BlockTags.TRAPDOORS)
                    ? (open ? SoundEvents.WOODEN_TRAPDOOR_CLOSE : SoundEvents.WOODEN_TRAPDOOR_OPEN)
                    : st.is(BlockTags.FENCE_GATES)
                    ? (open ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN)
                    : (open ? SoundEvents.WOODEN_DOOR_CLOSE : SoundEvents.WOODEN_DOOR_OPEN);
            playToVictimAt(target, s, x, y, z, 1.0F, 0.85F);
            return true;
        }
        // containers: the real lid animation (barrel via its OPEN state, chests via a block event), open then
        // close a beat later.
        RandomSource r = target.getRandom();
        if (b instanceof BarrelBlock && st.hasProperty(BlockStateProperties.OPEN)) {
            level.setBlock(pos, st.setValue(BlockStateProperties.OPEN, true), 3);
            playToVictimAt(target, SoundEvents.BARREL_OPEN, x, y, z, 0.8F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) -> {
                BlockState now = lvl.getBlockState(pos);
                if (now.getBlock() instanceof BarrelBlock && now.hasProperty(BlockStateProperties.OPEN)) {
                    lvl.setBlock(pos, now.setValue(BlockStateProperties.OPEN, false), 3);
                    playToVictimAt(tgt, SoundEvents.BARREL_CLOSE, x, y, z, 0.8F, 1.0F);
                }
            }, 25 + r.nextInt(25)));
            return true;
        }
        if (b instanceof ChestBlock || b instanceof EnderChestBlock) {
            boolean ender = b instanceof EnderChestBlock;
            level.blockEvent(pos, b, 1, 1); // lid animation: 1 viewer = open
            playToVictimAt(target, ender ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.CHEST_OPEN, x, y, z, 0.8F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) -> {
                Block nb = lvl.getBlockState(pos).getBlock();
                if (nb instanceof ChestBlock || nb instanceof EnderChestBlock) {
                    lvl.blockEvent(pos, nb, 1, 0); // 0 viewers = close
                    playToVictimAt(tgt, ender ? SoundEvents.ENDER_CHEST_CLOSE : SoundEvents.CHEST_CLOSE, x, y, z, 0.8F, 1.0F);
                }
            }, 25 + r.nextInt(25)));
            return true;
        }
        return false;
    }

    /** your dropped items leap into the air and clatter around — a poltergeist rifling through your stuff. */
    boolean itemPoltergeist(ServerPlayer target, ServerLevel level) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(8.0), ItemEntity::isAlive);
        if (items.isEmpty()) {
            return false;
        }
        RandomSource r = target.getRandom();
        for (ItemEntity it : items) {
            it.setDeltaMovement((r.nextDouble() - 0.5) * 0.4, 0.4 + r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.4);
            it.hurtMarked = true;
            level.sendParticles(ParticleTypes.ENCHANT, it.getX(), it.getY() + 0.3, it.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        }
        playToVictim(target, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 0.6F);
        return true;
    }

    /** A hard camera shake for the victim (used by the bang/lunge scares). */
    void dwellerShake(ServerPlayer target) {
        target.setData(WitchModAttachments.DWELLER_SHAKE_END, target.level().getGameTime() + Config.DWELLER_SHAKE_TICKS.get());
    }

    /**
     * suddenly GIBS a nearby UNTAMED mob — a jumpscare and a genuine danger signal: the bloody burst (bang +
     * splatter, the SAME gore as the Dweller's death) PLUS the actual explosion sound at the mob, and a real
     * damaging shockwave to anything too close. Tamed pets are spared. (The DEATH itself never plays the boom.)
     */
    boolean explodeUntamedMob(ServerPlayer target, ServerLevel level) {
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class,
                target.getBoundingBox().inflate(14.0),
                e -> e.isAlive() && !(e instanceof SpaghettiManEntity) && !(e instanceof WatcherEyesEntity)
                        && !(e instanceof net.minecraft.world.entity.OwnableEntity oe && oe.getOwnerUUID() != null)
                        && !(e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isTame()));
        if (nearby.isEmpty()) {
            return false;
        }
        Mob poor = nearby.get(target.getRandom().nextInt(nearby.size()));
        double x = poor.getX();
        double y = poor.getY() + 0.3;
        double z = poor.getZ();
        poor.hurt(level.damageSources().magic(), 1000.0F); // gib the mob
        bloodyBurst(level, x, y, z);                        // blood + bang + splatter
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.0F); // the boom, at the mob
        shakeNearbyPlayers(target, level);                  // a shared flinch from the sudden violence
        // everything nearby feels it and BOLTS — the sudden violence panics the whole area.
        for (Mob e : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(18.0),
                e -> e.isAlive() && e != poor && !(e instanceof SpaghettiManEntity) && !(e instanceof WatcherEyesEntity))) {
            spookMob(e, new Vec3(x, y, z), level);
        }
        // A real damaging shockwave — a piece of whoever's stood too close — done by hand so there's no boom.
        double power = Math.max(0.5, Config.DWELLER_EXPLODE_ENTITY_POWER.get());
        double radius = 2.0 + power;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(new Vec3(x, y, z), radius * 2, radius * 2, radius * 2),
                le -> le.isAlive() && le != poor && !(le instanceof SpaghettiManEntity))) {
            double d = Math.sqrt(e.distanceToSqr(x, y, z));
            if (d > radius) {
                continue;
            }
            double falloff = 1.0 - d / radius;
            e.hurt(level.damageSources().magic(), (float) (power * 2.0 * falloff));
            Vec3 kb = e.position().subtract(x, y, z);
            if (kb.horizontalDistanceSqr() > 1.0E-4) {
                kb = kb.normalize().scale(0.6 * falloff);
                e.push(kb.x, 0.3 * falloff, kb.z);
                if (e instanceof ServerPlayer sp) {
                    sp.hurtMarked = true;
                }
            }
        }
        return true;
    }

    // --- POSSESSION + BEHIND (tier 2+ additions) --------------------------------------------------------

    /** tag on any mob currently being puppeteered, so a stuck one (relog/logic slip) can always be found + freed. */
    static final String POSSESSED_TAG = "witchmod_possessed";

    /** free any mob left tagged as possessed (setNoAi back on), so a possession can never permanently brick a mob's AI. */
    static void freeStuckPossessed(ServerLevel level, net.minecraft.world.phys.AABB box) {
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                e -> e.getTags().contains(POSSESSED_TAG))) {
            m.setNoAi(false);
            m.removeTag(POSSESSED_TAG);
        }
    }

    /** puppeteer a nearby passive, untamed mob: it twitches in place, then marches at you and resolves. */
    boolean possessionEvent(ServerPlayer target, State state, ServerLevel level) {
        // self-heal FIRST: free any mob left stuck-possessed from a previous slip, so nothing stays bricked.
        freeStuckPossessed(level, target.getBoundingBox().inflate(64.0));
        state.possessedId = -1;
        List<net.minecraft.world.entity.animal.Animal> mobs = level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, target.getBoundingBox().inflate(16.0),
                e -> e.isAlive()
                        && !(e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isTame())
                        && !(e instanceof net.minecraft.world.entity.OwnableEntity oe && oe.getOwnerUUID() != null));
        if (mobs.isEmpty()) {
            return false;
        }
        net.minecraft.world.entity.animal.Animal poor = mobs.get(target.getRandom().nextInt(mobs.size()));
        poor.setNoAi(true);
        poor.setDeltaMovement(Vec3.ZERO);
        poor.addTag(POSSESSED_TAG);
        state.possessedId = poor.getId();
        state.possessPhase = 0;
        state.possessTimer = 30 + target.getRandom().nextInt(50); // ~1.5–4s twitching
        state.possessCd = Config.DWELLER_POSSESS_COOLDOWN.get();
        playToVictimAt(target, WitchModSounds.DWELLER_MOOD.get(), poor.getX(), poor.getEyeY(), poor.getZ(), 0.6F, 0.5F);
        return true;
    }

    /** release the possessed mob cleanly (restore AI + drop the tag) and clear the state. */
    private static void releasePossessed(State state, ServerLevel level) {
        if (state.possessedId >= 0 && level.getEntity(state.possessedId) instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(false);
            mob.removeTag(POSSESSED_TAG);
        }
        state.possessedId = -1;
    }

    void tickPossession(ServerPlayer target, State state, ServerLevel level) {
        if (state.possessedId < 0) {
            return;
        }
        net.minecraft.world.entity.Entity e = level.getEntity(state.possessedId);
        if (!(e instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive()) {
            state.possessedId = -1;
            return;
        }
        if (state.possessPhase == 0) {
            // twitching, still — VIOLENTLY snapping its head/body around every tick (no particles).
            // ⚠ A noAi mob's rotation only reaches the client if we forward-interpolate it and mark the entity
            // dirty each tick — the exact trick the Helicopter event needed. Setting the *O (previous) fields to
            // the CURRENT rotation and then jumping the live rotation makes the client wind forward to the snap;
            // hurtMarked forces the tracker to send an update even though the mob isn't moving.
            mob.setNoAi(true);
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            RandomSource r = target.getRandom();
            float yaw = r.nextFloat() * 360F - 180F;   // full, unpredictable snaps
            float pitch = (r.nextFloat() - 0.5F) * 140F; // head thrown up/down hard
            mob.yRotO = mob.getYRot();
            mob.yBodyRotO = mob.yBodyRot;
            mob.yHeadRotO = mob.yHeadRot;
            mob.xRotO = mob.getXRot();
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setXRot(pitch);
            mob.hurtMarked = true;
            if (--state.possessTimer <= 0) {
                state.possessPhase = 1;
                state.possessTimer = 220; // give up after ~11s if it can't reach you
                mob.setNoAi(false);
            }
            return;
        }
        // marching straight at you.
        double reach = 2.2;
        if (mob.distanceToSqr(target) <= reach * reach) {
            Vec3 spot = mob.position();
            boolean flash = state.entity == null && target.getRandom().nextBoolean();
            if (flash) {
                mob.discard();
                flashVanish(target, state, level, spot);     // sudden appearance scare
            } else {
                explodePossessed(target, state, level, mob);  // it bursts like the explode event
            }
            state.possessedId = -1;
            return;
        }
        // drive it at you with real navigation AND a direct velocity backup, so it always visibly closes the gap
        // even if pathfinding stalls (odd terrain, the just-cleared noAi brain still spinning up).
        mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.35);
        mob.getLookControl().setLookAt(target);
        Vec3 toYou = target.position().subtract(mob.position());
        if (toYou.horizontalDistanceSqr() > 1.0E-4) {
            Vec3 step = new Vec3(toYou.x, 0, toYou.z).normalize().scale(0.14);
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(step.x, v.y, step.z);
            mob.hurtMarked = true;
            mob.setYRot((float) (Mth.atan2(step.z, step.x) * (180.0 / Math.PI)) - 90F);
            mob.setYBodyRot(mob.getYRot());
        }
        if (--state.possessTimer <= 0) {
            releasePossessed(state, level); // gave up — restore its AI + drop the tag
        }
    }

    /** A brief spaghetti-man flash at {@code spot}, then he's gone — a sudden-appearance scare. */
    void flashVanish(ServerPlayer target, State state, ServerLevel level, Vec3 spot) {
        SpaghettiManEntity temp = WitchModEntities.SPAGHETTI_MAN.get().create(level);
        if (temp == null) {
            return;
        }
        temp.setVictim(target.getUUID());
        temp.setPos(spot.x, surfaceY(level, spot.x, spot.z, target.getY()), spot.z);
        faceVictimBody(temp, target.position().subtract(temp.position()));
        level.addFreshEntity(temp);
        dwellerJumpscare(target, 40);
        playToVictim(target, WitchModSounds.DWELLER_SCREAM.get(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 0.6, spot.z, 25, 0.3, 0.5, 0.3, 0.02);
        state.pending.add(new Pending((t, l) -> temp.discard(), 8)); // gone in ~0.4s
    }

    /** the possessed mob bursts like the explode event. */
    void explodePossessed(ServerPlayer target, State state, ServerLevel level, net.minecraft.world.entity.Mob mob) {
        double x = mob.getX(), y = mob.getY() + 0.3, z = mob.getZ();
        mob.discard();
        bloodyBurst(level, x, y, z);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
        shakeNearbyPlayers(target, level);
        double power = Math.max(0.5, Config.DWELLER_EXPLODE_ENTITY_POWER.get());
        double radius = 2.0 + power;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(new Vec3(x, y, z), radius * 2, radius * 2, radius * 2),
                l -> l.isAlive() && !(l instanceof SpaghettiManEntity))) {
            double d = Math.sqrt(le.distanceToSqr(x, y, z));
            if (d > radius) {
                continue;
            }
            double falloff = 1.0 - d / radius;
            le.hurt(level.damageSources().magic(), (float) (power * 2.0 * falloff));
            Vec3 kb = le.position().subtract(x, y, z);
            if (kb.horizontalDistanceSqr() > 1.0E-4) {
                kb = kb.normalize().scale(0.6 * falloff);
                le.push(kb.x, 0.3 * falloff, kb.z);
                if (le instanceof ServerPlayer sp) {
                    sp.hurtMarked = true;
                }
            }
        }
    }

    /** the turn-around glimpse: a fast look means a chance to briefly catch him watching, then he's gone. */
    void tickBehind(ServerPlayer target, State state, int tier) {
        float yaw = target.getYRot();
        if (!Float.isNaN(state.prevYaw) && tier >= 2 && state.behindCd <= 0
                && state.entity == null && state.possessedId < 0 && state.phase != Phase.CHASE) {
            float delta = Math.abs(Mth.wrapDegrees(yaw - state.prevYaw));
            if (delta > Config.DWELLER_BEHIND_TURN_DEGREES.get()
                    && target.getRandom().nextFloat() < Config.DWELLER_BEHIND_CHANCE.get().floatValue()) {
                behindGlimpse(target, state, target.serverLevel());
                state.behindCd = Config.DWELLER_BEHIND_COOLDOWN.get();
            }
        }
        state.prevYaw = yaw;
    }

    boolean behindGlimpse(ServerPlayer target, State state, ServerLevel level) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        Vec3 spot = target.position().add(flat.normalize().scale(Config.DWELLER_BEHIND_DISTANCE.get()));
        SpaghettiManEntity temp = WitchModEntities.SPAGHETTI_MAN.get().create(level);
        if (temp == null) {
            return false;
        }
        temp.setVictim(target.getUUID());
        temp.setPos(spot.x, surfaceY(level, spot.x, spot.z, target.getY()), spot.z);
        faceVictimBody(temp, target.position().subtract(temp.position()));
        level.addFreshEntity(temp);
        playToVictim(target, WitchModSounds.DWELLER_BREATH.get(), 0.6F, 0.9F);
        state.pending.add(new Pending((t, l) -> temp.discard(), Config.DWELLER_BEHIND_TICKS.get()));
        return true;
    }

    /** the shared gib effect: a lot of blood, plus the death BANG (at half volume) + SPLATTER — and no boom. */
    void bloodyBurst(ServerLevel level, double x, double y, double z) {
        RandomSource rr = level.random;
        DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.6F, 0.0F, 0.0F), 2.5F);
        level.sendParticles(blood, x, y + 0.8, z, 140, 0.7, 0.9, 0.7, 0.3);
        level.sendParticles(blood, x, y + 0.8, z, 70, 0.25, 0.4, 0.25, 0.85);
        for (int i = 0; i < 40; i++) {
            double ang = rr.nextDouble() * Math.PI * 2;
            double sp = 0.4 + rr.nextDouble() * 0.7;
            level.sendParticles(blood, x, y + 0.8, z, 0, Math.cos(ang) * sp, 0.2 + rr.nextDouble() * 0.6, Math.sin(ang) * sp, 1.0);
        }
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 0.8, z, 70, 0.6, 0.8, 0.6, 0.1);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y + 0.8, z, 24, 0.4, 0.5, 0.4, 0.1);
        level.playSound(null, x, y, z, WitchModSounds.DWELLER_BANG.get(), SoundSource.HOSTILE, 0.5F, 1.0F);
        level.playSound(null, x, y, z, WitchModSounds.DWELLER_SPLATTER.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    boolean breakNearbyBlock(ServerPlayer target, ServerLevel level) {
        BlockPos origin = target.blockPosition();
        RandomSource random = target.getRandom();
        for (int attempt = 0; attempt < 28; attempt++) {
            BlockPos pos = origin.offset(random.nextInt(11) - 5, random.nextInt(6) - 2, random.nextInt(11) - 5);
            if (breakable(level.getBlockState(pos))) {
                level.destroyBlock(pos, false);
                return true;
            }
        }
        return false;
    }

    static boolean breakable(BlockState st) {
        return st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.WOODEN_FENCES)
                || st.getBlock() == Blocks.GLASS || st.is(BlockTags.IMPERMEABLE);
    }

    /** suddenly breaks EVERY light source in a radius at once (dropping them), plunging the area into dark. */
    boolean snuffNearbyLight(ServerPlayer target, ServerLevel level) {
        BlockPos origin = target.blockPosition();
        int r = 7;
        boolean any = false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -4, -r), origin.offset(r, 4, r))) {
            BlockState st = level.getBlockState(pos);
            if (isLightSource(st)) {
                BlockPos p = pos.immutable();
                level.destroyBlock(p, true); // true = drop the item
                particleToVictim(target, ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 8, 0.1, 0.2, 0.1, 0.01);
                any = true;
            }
        }
        return any;
    }

    static boolean isLightSource(BlockState st) {
        return st.is(BlockTags.CANDLES) || st.is(BlockTags.CAMPFIRES)
                || st.getBlock() == Blocks.TORCH || st.getBlock() == Blocks.WALL_TORCH
                || st.getBlock() == Blocks.SOUL_TORCH || st.getBlock() == Blocks.SOUL_WALL_TORCH
                || st.getBlock() == Blocks.LANTERN || st.getBlock() == Blocks.SOUL_LANTERN
                || st.getBlock() == Blocks.GLOWSTONE || st.getBlock() == Blocks.JACK_O_LANTERN
                || st.getBlock() == Blocks.SEA_LANTERN || st.getBlock() == Blocks.SHROOMLIGHT
                || st.getBlock() == Blocks.REDSTONE_LAMP && st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT)
                || st.getBlock() == Blocks.END_ROD;
    }

    boolean breakBedUnder(ServerPlayer target, ServerLevel level) {
        BlockPos pos = target.getSleepingPos().orElse(target.blockPosition());
        if (level.getBlockState(pos).is(BlockTags.BEDS)) {
            target.stopSleeping();
            level.destroyBlock(pos, false);
            return true;
        }
        return false;
    }

    // --- Persistent systems (every phase) ---------------------------------------------------------------

    void tickPersistent(ServerPlayer target, State state, int tier) {
        // POSSESSION auto-roll (the tick of an active possession happens in onTick, before the foreplay return).
        if (tier >= 2 && state.possessedId < 0 && state.possessCd <= 0
                && target.getRandom().nextInt(Math.max(1, Config.DWELLER_POSSESS_CHANCE_DENOM.get())) == 0) {
            possessionEvent(target, state, target.serverLevel());
        }
        processPending(target, state);      // delayed acts scheduled by set-pieces (knock→shatter etc.)
        tickEyes(target, state);            // maintain the watching-eyes if any are up (no-op otherwise)
        tickWatch(target, state);           // maintain a mob-stare if one is running (no-op otherwise)
        // the ONE readable cue.
        if (state.phase != Phase.CHASE) {
            tickHeartbeat(target, state);
        }
        tickMood(target, state);          // non-diegetic paranormal ambience + red herrings, dread-scaled
        laughOnTierUp(target, state);     // a diegetic laugh each time dread ticks up a (half/full) tier
        tickDarkEyes(target, state);      // in the dark, eyes open in the black and watch you
        // AUDITORY HALLUCINATIONS — fake sounds (footsteps, mining, a fight, a creeper, a cave) around you, so the
        // world feels genuinely HAUNTED and cursed. Paced (dread-scaled), between encounters AND during a chase,
        // where the pace QUADRUPLES (chaosStep) so everything goes haywire.
        if (state.phase == Phase.LULL || state.phase == Phase.RELEASE || state.phase == Phase.CHASE) {
            state.hallucCd -= chaosStep(state);
            if (state.hallucCd <= 0) {
                hallucinateSound(target, state, tier);
                state.hallucCd = hallucinationGap(target, tier);
            }
        }
        // CHASE CHAOS: the world stirs around you MID-HUNT too — doors, knocks, breaks, footsteps, poltergeists —
        // and at 4× the normal rate (chaosStep is applied inside), so it all goes haywire while you run.
        if (state.phase == Phase.CHASE) {
            tickAmbientEvents(target, state, tier);
        }
        // A low-tier ambient: nearby passive mobs occasionally fall silent and stare at you.
        if (tier <= 1 && state.watchTimer <= 0 && state.phase == Phase.LULL
                && target.getRandom().nextInt(1200) == 0) {
            mobStare(target, state);
        }
        if (state.phase != Phase.STALK) {
            setBreathing(target, state, false); // breathing only while it's manifest and watching
        }
    }

    // --- Custom-sound ambience ------------------------------------------------------------------------

    /**
     * the non-diegetic MOOD ambience + a couple of diegetic red herrings, on a dread-scaled timer with a hard
     * floor so it can NEVER be spammed. Mostly a mood sting centred on the victim (in-their-head), sometimes at
     * a nearby block (as if something's there); occasionally a wind red herring, a scream red herring, or a loud
     * pop right behind them.
     */
    /** during a CHASE, ambient noise/events run 4× as fast — everything goes haywire around you. */
    static int chaosStep(State state) {
        return state.phase == Phase.CHASE ? 4 : 1;
    }

    void tickMood(ServerPlayer target, State state) {
        state.moodCd -= chaosStep(state);
        if (state.moodCd > 0) {
            return;
        }
        RandomSource r = target.getRandom();
        double frac = dreadFrac(state);
        // gap: mood stings stay a rare, unsettling cue rather than a drone (×2.2 vs the original), dread-scaled +
        // jittered, floored so they never nag.
        int base = (int) (Mth.lerp((float) frac, 600.0F, 280.0F) * 2.2F);
        state.moodCd = Math.max(460, base / 2 + r.nextInt(base));
        float vol = 0.45F + 0.45F * (float) frac;
        int roll = r.nextInt(100);
        if (roll < 8) {
            // loud POP close behind you — non-diegetic jolt + a camera flinch.
            Vec3 b = target.position().add(directionBehind(target).scale(1.6 + r.nextDouble()));
            playToVictimAt(target, WitchModSounds.DWELLER_POP.get(), b.x, b.y + 1.0, b.z, 0.9F, 0.95F + r.nextFloat() * 0.1F);
            dwellerShake(target);
        } else if (roll < 20) {
            // WIND red herring — a gust as if it moved, but nothing's there.
            Vec3 b = target.position().add(directionBehind(target).scale(4.0 + r.nextDouble() * 6.0));
            playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), b.x, b.y + 1.0, b.z, 0.5F + 0.3F * (float) frac, 1.0F);
        } else if (roll < 25 && frac > 0.4) {
            // SCREAM red herring — rare, distant, quiet: was that a chase starting? (it wasn't.)
            double a = r.nextDouble() * Math.PI * 2;
            double d = 20.0 + r.nextDouble() * 20.0;
            playToVictimAt(target, WitchModSounds.DWELLER_SCREAM.get(),
                    target.getX() + Math.cos(a) * d, target.getY(), target.getZ() + Math.sin(a) * d, 0.35F, 1.0F);
        } else if (roll < 60) {
            // MOOD at a nearby block — the illusion of a source.
            BlockPos p = target.blockPosition().offset(r.nextInt(17) - 8, r.nextInt(5) - 2, r.nextInt(17) - 8);
            playMoodSound(target, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, true, vol);
        } else {
            // MOOD non-diegetic — in your head, no world location.
            playMoodSound(target, target.getX(), target.getY(), target.getZ(), false, vol);
        }
    }

    /**
     * plays a mood cue — but with a 28% chance it's a low WIND gust instead (volume 0.6, pitch 0.4-0.75), for a
     * slower, more ominous "the air just moved" flavour mixed into the ambience.
     */
    void playMoodSound(ServerPlayer target, double x, double y, double z, boolean positional, float vol) {
        RandomSource r = target.getRandom();
        if (r.nextFloat() < 0.28F) {
            float pitch = 0.4F + r.nextFloat() * 0.35F; // 0.4 .. 0.75
            if (positional) {
                playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), x, y, z, 0.6F, pitch);
            } else {
                playToVictim(target, WitchModSounds.DWELLER_WIND.get(), 0.6F, pitch);
            }
        } else if (positional) {
            playToVictimAt(target, WitchModSounds.DWELLER_MOOD.get(), x, y, z, vol, 1.0F);
        } else {
            playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), vol, 1.0F);
        }
    }

    /**
     * A diegetic laugh whenever dread crosses the next boundary. Boundaries alternate half / full: crossing a
     * FULL tier threshold always laughs at full volume; crossing the HALF-way point to the next tier laughs at
     * half volume, 50% of the time. It only fires on the way UP (dread is one-way anyway).
     */
    void laughOnTierUp(ServerPlayer target, State state) {
        double[] bounds = laughBoundaries();
        int idx = state.lastLaughBoundary;
        for (int i = bounds.length - 1; i > state.lastLaughBoundary; i--) {
            if (state.anger >= bounds[i]) {
                idx = i;
                break;
            }
        }
        if (idx <= state.lastLaughBoundary) {
            return;
        }
        boolean full = (idx % 2) == 1; // boundaries are [half, full, half, full, half, full]
        state.lastLaughBoundary = idx;
        if (full) {
            playToVictim(target, WitchModSounds.DWELLER_LAUGH.get(), 1.0F, 1.0F);
        } else if (target.getRandom().nextFloat() < 0.5F) {
            playToVictim(target, WitchModSounds.DWELLER_LAUGH.get(), 0.45F, 1.0F);
        }
    }

    /** the ordered dread boundaries that trigger a laugh: the midpoint then the threshold for each tier. */
    private static double[] laughBoundaries() {
        double t1 = Config.DWELLER_TIER1_THRESHOLD.get();
        double t2 = Config.DWELLER_TIER2_THRESHOLD.get();
        double t3 = Config.DWELLER_TIER3_THRESHOLD.get();
        return new double[] {t1 / 2.0, t1, (t1 + t2) / 2.0, t2, (t2 + t3) / 2.0, t3};
    }

    /** is the dweller BEHIND the victim (out of the way they're facing)? */
    static boolean isBehind(ServerPlayer target, SpaghettiManEntity dweller) {
        Vec3 to = dweller.position().subtract(target.position());
        if (to.horizontalDistanceSqr() < 1.0E-4) {
            return false;
        }
        return target.getViewVector(1.0F).dot(to.normalize()) < -0.1;
    }

    /** requests/clears the client-side breathing loop (a synced flag; the client owns the positional loop). */
    void setBreathing(ServerPlayer target, State state, boolean on) {
        if (on != state.breathingOn) {
            state.breathingOn = on;
            target.setData(WitchModAttachments.DWELLER_BREATHING, on ? 1 : 0);
        }
    }

    /**
     * the heartbeat — the single legible signal of where you are in the encounter. Silent in the LULL, a slow
     * pulse that quickens through the TELL, and a pounding tempo tied to how close it looms during the STALK.
     * Driven off {@code state.heat} (0..1) that each beat sets, so what you HEAR always matches the scene.
     */
    void tickHeartbeat(ServerPlayer target, State state) {
        double heat = Mth.clamp((float) state.heat, 0.0F, 1.0F);
        if (heat < 0.12) {
            state.heartbeatCd = 20; // genuinely silent while calm — the quiet is the point
            return;
        }
        if (--state.heartbeatCd > 0) {
            return;
        }
        int min = Config.DWELLER_HEARTBEAT_MIN_TICKS.get();
        int max = Config.DWELLER_HEARTBEAT_MAX_TICKS.get();
        state.heartbeatCd = Math.max(4, (int) Mth.lerp((float) heat, (float) max, (float) min));
        playToVictim(target, SoundEvents.WARDEN_HEARTBEAT, 0.35F + 0.55F * (float) heat, 0.5F + 0.65F * (float) heat);
    }

    /** stray flickers of the lights at high dread, no event attached — just wrongness. */

    void processPending(ServerPlayer target, State state) {
        if (state.pending.isEmpty()) {
            return;
        }
        ServerLevel level = target.serverLevel();
        Iterator<Pending> it = state.pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (--p.delay <= 0) {
                try {
                    p.act.run(target, level);
                } catch (Exception ignored) {
                    // stale reference after a relog etc — drop it quietly
                }
                it.remove();
            }
        }
    }

    // --- Knock event ------------------------------------------------------------------------------------

    boolean knockEvent(ServerPlayer target, State state, ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos origin = target.blockPosition();
        RandomSource random = target.getRandom();
        int want = Config.DWELLER_KNOCK_BLOCKS.get();
        for (int attempt = 0; attempt < 110 && found.size() < want; attempt++) {
            BlockPos pos = origin.offset(random.nextInt(17) - 8, random.nextInt(9) - 4, random.nextInt(17) - 8);
            if (isKnockable(level.getBlockState(pos)) && !found.contains(pos)) {
                found.add(pos.immutable());
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        // ONE knock AT the doomed blocks (a custom knock sound), a beat of dread, then the whole set SHATTERS at
        // once — the zombie door-break sound + a camera-shake FLINCH for every nearby player. Essentially a
        // jumpscare: quiet knock, long pause, then everything goes at the same instant.
        BlockPos centre = found.get(0);
        playToVictimAt(target, WitchModSounds.DWELLER_KNOCK.get(),
                centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 1.1F, 1.0F);
        int shatterDelay = Config.DWELLER_KNOCK_SHATTER_MIN_TICKS.get()
                + random.nextInt(Math.max(1, Config.DWELLER_KNOCK_SHATTER_MAX_TICKS.get() - Config.DWELLER_KNOCK_SHATTER_MIN_TICKS.get()));
        final List<BlockPos> doomed = found;
        state.pending.add(new Pending((tgt, lvl) -> {
            boolean any = false;
            for (BlockPos at : doomed) {
                BlockState st = lvl.getBlockState(at);
                if (!st.isAir()) {
                    // A burst of the block's OWN crack dust as it goes, plus smoke.
                    lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st),
                            at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 24, 0.3, 0.4, 0.3, 0.12);
                    lvl.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
                    lvl.destroyBlock(at, false);
                    any = true;
                }
            }
            if (any) {
                double cx = centre.getX() + 0.5, cy = centre.getY() + 0.5, cz = centre.getZ() + 0.5;
                lvl.playSound(null, cx, cy, cz, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.5F, 0.7F);
                lvl.playSound(null, cx, cy, cz, WitchModSounds.DWELLER_BANG.get(), SoundSource.HOSTILE, 1.0F, 1.0F); // the bang, on top
                shakeNearbyPlayers(tgt, lvl); // the flinch, for everyone in earshot
            }
        }, shatterDelay));
        return true;
    }

    /** blocks the knock can shatter — generous: doors/trapdoors, glass + panes, and ordinary carvable blocks. */
    static boolean isKnockable(BlockState st) {
        if (st.isAir() || !st.getFluidState().isEmpty()) {
            return false;
        }
        return st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.getBlock() == Blocks.GLASS
                || st.is(BlockTags.IMPERMEABLE) || st.is(BlockTags.WOODEN_FENCES) || isCarvable(st);
    }

    /** sends the camera-shake flinch to EVERY player near a point (the knock shatter is a shared jumpscare). */
    void shakeNearbyPlayers(ServerPlayer origin, ServerLevel level) {
        long end = level.getGameTime() + Config.DWELLER_SHAKE_TICKS.get();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, origin.getBoundingBox().inflate(16.0), ServerPlayer::isAlive)) {
            p.setData(WitchModAttachments.DWELLER_SHAKE_END, end);
        }
    }

    // --- Distant watch + auto eyes-in-the-dark ----------------------------------------------------------

    /** A far, in-view spot on the ground — where a distant watch stands. */
    static final int WATCH_FAR = 0, WATCH_MEDIUM = 1, WATCH_CLOSE = 2;

    /** picks a watch distance band. Scales with dread: FAR dominates when calm, CLOSE takes over near max. */
    static int pickWatchTier(double frac, boolean foreplay, RandomSource r) {
        if (foreplay) {
            int roll = r.nextInt(100);
            return roll < 3 ? WATCH_CLOSE : roll < 25 ? WATCH_MEDIUM : WATCH_FAR; // 75 far / 22 medium / 3 close
        }
        double far = 1.0 - 0.85 * frac;    // 1.00 → 0.15
        double med = 0.35 + 0.10 * frac;   // 0.35 → 0.45
        double close = 0.05 + 0.90 * frac; // 0.05 → 0.95
        double p = r.nextDouble() * (far + med + close);
        if (p < far) {
            return WATCH_FAR;
        }
        return p < far + med ? WATCH_MEDIUM : WATCH_CLOSE;
    }

    /**
     * A spot for a watch at the given band. FAR (30-50) and MEDIUM (18-29) demand a genuine clear line of
     * sight so the figure actually registers; CLOSE (8-16) doesn't (it's fine peering round something). Falls
     * back to a mid-band in-view spot if no LOS spot can be found (enclosed base) rather than failing.
     */
    Vec3 watchSpot(ServerPlayer target, ServerLevel level, int watchTier) {
        double dMin;
        double dMax;
        boolean needLos;
        switch (watchTier) {
            case WATCH_CLOSE -> { dMin = 8.0; dMax = 16.0; needLos = false; }
            case WATCH_MEDIUM -> { dMin = 18.0; dMax = 29.0; needLos = true; }
            default -> { dMin = 30.0; dMax = 50.0; needLos = true; }
        }
        RandomSource r = target.getRandom();
        // it no longer appears in your CURRENT view — the spot is chosen OUTSIDE your forward cone, so you have to
        // TURN and find it there (a clear line of sight once you do, for FAR/MEDIUM). Far less "it just popped up
        // dead ahead", far more "when did that get there".
        for (int i = 0; i < 20; i++) {
            double d = dMin + r.nextDouble() * (dMax - dMin);
            Vec3 s = outOfViewSpot(target, d);
            if (!needLos || clearLineOfSight(level, target.getEyePosition(), s.add(0, 1.6, 0))) {
                return s;
            }
        }
        return outOfViewSpot(target, (dMin + dMax) * 0.5);
    }

    /** A ground spot OUTSIDE the player's current view cone (70°-180° off their look) — they must turn to see it. */
    static Vec3 outOfViewSpot(ServerPlayer target, double dist) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        RandomSource r = target.getRandom();
        double side = r.nextBoolean() ? 1 : -1;
        double rot = side * (Math.toRadians(70) + r.nextDouble() * Math.toRadians(110)); // 70°..180° off centre
        return groundSnap(target, target.position().add(rotateY(flat, rot).scale(dist)));
    }

    /** debug: force a watch right now, its band scaled by dread. */
    boolean forceDistantWatch(ServerPlayer target, State state) {
        enterStalk(target, state, tier(state.anger));
        return state.phase == Phase.STALK;
    }

    /** debug: force a watch in a SPECIFIC distance band (FAR/MEDIUM/CLOSE). */
    boolean forceWatch(ServerPlayer target, State state, int band) {
        ServerLevel level = target.serverLevel();
        int tier = tier(state.anger);
        ensureEntity(target, state);
        if (state.entity == null) {
            return false;
        }
        state.watchTier = band;
        state.windowWatch = false;
        placeAt(state.entity, watchSpot(target, level, band), target, level);
        beginWatchBeat(target, state, tier);
        return true;
    }

    /** debug: force a WINDOW watch (peers through a wall/window; vanishes on a clear line of sight). */
    boolean forceWindowWatch(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        int tier = tier(state.anger);
        Vec3 spot = windowWatchSpot(target, level, 16.0);
        if (spot == null) {
            return false; // no window/wall to peer through
        }
        ensureEntity(target, state);
        if (state.entity == null) {
            return false;
        }
        state.watchTier = WATCH_MEDIUM;
        state.windowWatch = true;
        placeAt(state.entity, spot, target, level);
        beginWatchBeat(target, state, tier);
        return true;
    }

    private void beginWatchBeat(ServerPlayer target, State state, int tier) {
        state.phase = Phase.STALK;
        state.watched = 0;
        state.hasBeenSeen = false;
        state.sizeUp = false;
        state.lungeTicks = 0;
        state.beatTotal = manifestLifespan(target, tier);
        state.timer = state.beatTotal;
        state.heat = state.watchTier == WATCH_CLOSE ? 0.75 : 0.55;
    }

    /** in the dark, eyes open in the black around you and watch — auto-fired on a paced timer while it's dark. */
    void tickDarkEyes(ServerPlayer target, State state) {
        if (state.eyesTimer > 0 || --state.eyesCd > 0) {
            return;
        }
        state.eyesCd = 400 + target.getRandom().nextInt(600); // 20–50s between openings
        int light = target.serverLevel().getMaxLocalRawBrightness(target.blockPosition());
        if (light <= Config.DWELLER_DARK_LEVEL.get() && target.getRandom().nextFloat() < 0.6F) {
            eyesEvent(target, state);
        }
    }

    // --- Watching eyes: glowing eyes in the dark --------------------------------------------------------

    boolean eyesEvent(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        int min = Config.DWELLER_EYES_MIN.get();
        int max = Math.max(min, Config.DWELLER_EYES_MAX.get());
        int count = min + target.getRandom().nextInt(max - min + 1);
        double dist = Config.DWELLER_EYES_DISTANCE.get();
        RandomSource random = target.getRandom();
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i / count) + (random.nextDouble() - 0.5) * 0.6;
            double d = dist * (0.8 + random.nextDouble() * 0.4);
            Vec3 spot = groundSnap(target, target.position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            WatcherEyesEntity eyes = WitchModEntities.WATCHER_EYES.get().create(level);
            if (eyes == null) {
                continue;
            }
            eyes.setVictim(target.getUUID());
            eyes.setPos(spot.x, spot.y + 1.4 + random.nextDouble() * 0.6, spot.z);
            level.addFreshEntity(eyes);
            state.eyesIds.add(eyes.getId());
        }
        if (state.eyesIds.isEmpty()) {
            return false;
        }
        int minT = Config.DWELLER_EYES_MIN_TICKS.get();
        int maxT = Config.DWELLER_EYES_MAX_TICKS.get();
        state.eyesTimer = minT + random.nextInt(Math.max(1, maxT - minT));
        playToVictim(target, WitchModSounds.DWELLER_MOOD.get(), 0.4F, 0.7F); // a low swell as the eyes open
        return true;
    }

    void tickEyes(ServerPlayer target, State state) {
        if (state.eyesTimer <= 0) {
            return;
        }
        ServerLevel level = target.serverLevel();
        for (int id : state.eyesIds) {
            if (level.getEntity(id) instanceof WatcherEyesEntity eyes && eyes.isAlive()) {
                Vec3 dir = target.getEyePosition().subtract(eyes.position());
                if (dir.horizontalDistanceSqr() > 1.0E-4) {
                    float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
                    eyes.setYRot(yaw);
                    eyes.setYBodyRot(yaw);
                    eyes.setYHeadRot(yaw);
                    eyes.setXRot((float) (-(Mth.atan2(dir.y, dir.horizontalDistance()) * (180.0 / Math.PI))));
                }
            }
        }
        if (target.tickCount % 5 == 0) {
            state.eyesIds.removeIf(id -> {
                if (level.getEntity(id) instanceof WatcherEyesEntity eyes) {
                    if (eyes.distanceToSqr(target) < 9.0) {
                        eyes.discard();
                        return true;
                    }
                    return false;
                }
                return true;
            });
        }
        if (--state.eyesTimer <= 0 || state.eyesIds.isEmpty()) {
            clearEyes(target, state);
        }
    }

    void clearEyes(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        for (int id : state.eyesIds) {
            if (level.getEntity(id) instanceof WatcherEyesEntity eyes && eyes.isAlive()) {
                particleToVictim(target, ParticleTypes.SMOKE, eyes.getX(), eyes.getY(), eyes.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
                eyes.discard();
            }
        }
        state.eyesIds.clear();
        state.eyesTimer = 0;
    }

    // --- Mob stare: nearby passive mobs go silent and watch you --------------------------------------------

    /** every nearby passive mob/villager falls silent, freezes, and stares at you for 3–8s, doing nothing else. */
    boolean mobStare(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(16.0),
                m -> m.isAlive() && !(m instanceof SpaghettiManEntity) && !(m instanceof WatcherEyesEntity)
                        && (m instanceof Animal || m instanceof net.minecraft.world.entity.npc.AbstractVillager));
        if (mobs.isEmpty()) {
            return false;
        }
        clearWatch(target, state);
        for (Mob m : mobs) {
            m.setNoAi(true);
            m.setSilent(true);
            state.watchIds.add(m.getId());
        }
        state.watchTimer = 60 + target.getRandom().nextInt(101); // 3–8s
        return true;
    }

    void tickWatch(ServerPlayer target, State state) {
        if (state.watchTimer <= 0) {
            return;
        }
        ServerLevel level = target.serverLevel();
        for (int id : state.watchIds) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                m.setDeltaMovement(0, Math.min(0, m.getDeltaMovement().y), 0);
                m.hurtMarked = true;
                Vec3 dir = target.position().subtract(m.position());
                if (dir.horizontalDistanceSqr() > 1.0E-4) {
                    float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
                    m.setYRot(yaw);
                    m.setYBodyRot(yaw);
                    m.setYHeadRot(yaw);
                    m.setXRot((float) (-(Mth.atan2(dir.y, dir.horizontalDistance()) * (180.0 / Math.PI))));
                }
            }
        }
        if (--state.watchTimer <= 0) {
            clearWatch(target, state);
        }
    }

    void clearWatch(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        for (int id : state.watchIds) {
            if (level.getEntity(id) instanceof Mob m) {
                m.setNoAi(false);
                m.setSilent(false);
            }
        }
        state.watchIds.clear();
        state.watchTimer = 0;
    }

    /** the flash-and-dark jumpscare: a hard camera flinch + a bright white flash, then darkness for {@code darkTicks}. */
    void dwellerJumpscare(ServerPlayer target, int darkTicks) {
        dwellerShake(target);
        target.setData(WitchModAttachments.DWELLER_FLASH_END, target.level().getGameTime() + 8); // ~0.4s white flash
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darkTicks, 0, false, false)); // then the dark
    }

    // --- Mimic (its own disguise vignette, not the Delusions curse) -------------------------------------

    /**
     * MIMIC — its own event, NOT the Delusions curse. Fires ONE impostor wearing a real player's face that
     * stands and STARES, then drops the mask into shadow with a scream (the client vignette, off DWELLER_MIMIC),
     * implying the Dweller was wearing it. The client owns the staging + reveal; here we just start the session,
     * a listening cue, and a little dread — then clear the session after the vignette so it can fire again.
     */
    boolean mimicEvent(ServerPlayer target, State state) {
        // NO DUPLICATES: never run a mimic while the real dweller is out, or while a mimic is already up.
        if (state.entity != null || target.getData(WitchModAttachments.DWELLER_MIMIC) != 0L) {
            return false;
        }
        target.setData(WitchModAttachments.DWELLER_MIMIC, target.getRandom().nextLong() | 1L); // never 0
        playToVictim(target, SoundEvents.WARDEN_LISTENING, 0.6F, 0.7F);
        state.anger = Math.min(Config.DWELLER_ANGER_MAX.get(), state.anger + 3.0);
        // end the session a beat after the client's longest possible vignette, so the impostor can't linger.
        state.pending.add(new Pending(
                (t, l) -> t.setData(WitchModAttachments.DWELLER_MIMIC, 0L),
                Config.DWELLER_MIMIC_BURST_TICKS.get() + 40));
        return true;
    }




    /** A fake attacker vanishes — from you striking it OR it reaching you. Brief blindness + a jolt of dread. */


    // --- Static hooks from the event handler ------------------------------------------------------------

    /**
     * called from AttackEntityEvent: the stalker is unhittable (swallow the swing so no bad packet is sent), and
     * — the hard rule — swinging at a WATCH makes it vanish on the spot (it's never there to be hit). During a
     * CHASE the swing is simply eaten with no effect.
     */
    public static boolean onDwellerAttacked(ServerPlayer attacker, Entity entity) {
        State s = STATES.get(attacker.getUUID());
        if (s == null || entity != s.entity) {
            return false;
        }
        CurseTheDweller self = (CurseTheDweller) Curses.THE_DWELLER.get();
        self.addInteractDread(s); // swinging at it is INTERACTING, not ignoring — it costs you dread
        attacker.setData(WitchModAttachments.DWELLER_DREAD, (float) (s.anger / Config.DWELLER_ANGER_MAX.get()));
        if (s.phase == Phase.STALK) {
            self.vanishWatch(attacker, s, tier(s.anger));
        } else if (s.phase == Phase.FOREPLAY) {
            self.despawn(s);
            s.timer = foreplayWatchGap(attacker);
        }
        return true;
    }

    /**
     * called from LivingDeathEvent. Dying TO THE CHASE (it caught you) is the ONE thing that wipes the dread —
     * the reset valve, and the only escape. Any OTHER death leaves the dread exactly where it was: the haunt
     * doesn't care how you died, only that IT got you.
     */
    public static void onVictimDeath(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null) {
            return;
        }
        if (s.phase == Phase.CHASE) {
            s.anger = 0.0; // it caught you — a clean slate, and the only way to earn one
        }
        CurseTheDweller self = (CurseTheDweller) Curses.THE_DWELLER.get();
        self.despawn(s);
        self.clearEyes(player, s);
        self.clearWatch(player, s);
        s.pending.clear();
        s.phase = Phase.LULL;
        s.heat = 0.0;
        s.chaseCooldown = Config.DWELLER_CHASE_COOLDOWN_TICKS.get();
        s.timer = dormantDuration(player, dreadFrac(s));
        int t = tier(s.anger);
        player.setData(WitchModAttachments.DWELLER_ACTIVE, t);
        player.setData(WitchModAttachments.DWELLER_DREAD, (float) (s.anger / Config.DWELLER_ANGER_MAX.get()));
        player.setData(WitchModAttachments.DWELLER_ANGER, s.anger); // persist the reset/kept dread through the death
    }

    static double tierStart(int tier) {
        return switch (tier) {
            case 1 -> Config.DWELLER_TIER1_THRESHOLD.get();
            case 2 -> Config.DWELLER_TIER2_THRESHOLD.get();
            case 3 -> Config.DWELLER_TIER3_THRESHOLD.get();
            default -> 0.0;
        };
    }

    /** debug: jump dread straight to the start of a tier and re-sync the client. */
    private String setTierDebug(ServerPlayer target, State state, int tier) {
        if (state.phase == Phase.FOREPLAY) {
            state.phase = Phase.LULL;
            state.timer = dormantDuration(target, 0.0);
        }
        state.anger = Mth.clamp((float) tierStart(tier) + 0.5F, 0.0F, (float) (double) Config.DWELLER_ANGER_MAX.get());
        target.setData(WitchModAttachments.DWELLER_DREAD, (float) (state.anger / Config.DWELLER_ANGER_MAX.get()));
        target.setData(WitchModAttachments.DWELLER_ACTIVE, tier(state.anger));
        return "dread set to TIER " + tier + " (" + String.format("%.1f", state.anger) + ")";
    }

    // --- Auditory hallucinations ------------------------------------------------------------------------

    void hallucinateSound(ServerPlayer target, State state, int tier) {
        RandomSource random = target.getRandom();
        double a = random.nextDouble() * Math.PI * 2;
        double d = 3.0 + random.nextDouble() * (6.0 + 5.0 * tier);
        double x = target.getX() + Math.cos(a) * d;
        double z = target.getZ() + Math.sin(a) * d;
        double y = target.getY() + (random.nextDouble() - 0.4) * 3.0;
        int roll = random.nextInt(100);
        if (roll < 14) {
            // someone's footsteps closing in from out there.
            int steps = 3 + random.nextInt(3);
            for (int i = 0; i < steps; i++) {
                double f = 1.0 - i / (double) steps;
                double sx = target.getX() + Math.cos(a) * d * f;
                double sz = target.getZ() + Math.sin(a) * d * f;
                state.pending.add(new Pending((tgt, lvl) ->
                        playToVictimAt(tgt, WitchModSounds.DWELLER_STEP.get(), sx, tgt.getY(), sz, 0.7F, 1.0F), i * 5 + 1));
            }
        } else if (roll < 24) {
            // mining below you — a run of hits then a break.
            int hits = 3 + random.nextInt(4);
            for (int i = 0; i < hits; i++) {
                state.pending.add(new Pending((tgt, lvl) ->
                        playToVictimAt(tgt, SoundEvents.STONE_HIT, x, y - 2, z, 0.8F, 0.9F), i * (3 + random.nextInt(4)) + 1));
            }
            state.pending.add(new Pending((tgt, lvl) ->
                    playToVictimAt(tgt, SoundEvents.STONE_BREAK, x, y - 2, z, 0.8F, 0.9F), hits * 5 + 4));
        } else if (roll < 31) {
            playToVictimAt(target, SoundEvents.CHEST_OPEN, x, y, z, 0.8F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) ->
                    playToVictimAt(tgt, SoundEvents.CHEST_CLOSE, x, y, z, 0.8F, 1.0F), 25 + random.nextInt(20)));
        } else if (roll < 37) {
            playToVictimAt(target, random.nextBoolean() ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, x, y, z, 0.9F, 1.0F);
        } else if (roll < 43) {
            // someone fighting a zombie nearby — swings alternating with grunts, sometimes a death.
            int rounds = 2 + random.nextInt(3);
            for (int i = 0; i < rounds; i++) {
                state.pending.add(new Pending((tgt, lvl) ->
                        playToVictimAt(tgt, random.nextBoolean() ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_CRIT, x, y, z, 0.9F, 1.0F), i * 12 + 1));
                state.pending.add(new Pending((tgt, lvl) ->
                        playToVictimAt(tgt, SoundEvents.ZOMBIE_HURT, x, y, z, 0.9F, 1.0F), i * 12 + 5));
            }
            if (random.nextBoolean()) {
                state.pending.add(new Pending((tgt, lvl) ->
                        playToVictimAt(tgt, SoundEvents.ZOMBIE_DEATH, x, y, z, 0.9F, 1.0F), rounds * 12 + 6));
            }
        } else if (roll < 48) {
            playToVictimAt(target, SoundEvents.GENERIC_EAT, x, y, z, 0.7F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) ->
                    playToVictimAt(tgt, SoundEvents.PLAYER_BURP, x, y, z, 0.7F, 1.0F), 20 + random.nextInt(15)));
        } else if (roll < 53) {
            // an animal hurt nearby — the swing lands, THEN it cries out (the order sells it).
            SoundEvent cry = switch (random.nextInt(4)) {
                case 0 -> SoundEvents.COW_HURT;
                case 1 -> SoundEvents.PIG_HURT;
                case 2 -> SoundEvents.SHEEP_HURT;
                default -> SoundEvents.CHICKEN_HURT;
            };
            playToVictimAt(target, SoundEvents.PLAYER_ATTACK_STRONG, x, y, z, 0.8F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) -> playToVictimAt(tgt, cry, x, y, z, 0.9F, 1.0F), 4 + random.nextInt(4)));
        } else if (roll < 58) {
            SoundEvent amb = switch (random.nextInt(3)) {
                case 0 -> SoundEvents.SKELETON_AMBIENT;
                case 1 -> SoundEvents.SPIDER_AMBIENT;
                default -> SoundEvents.ZOMBIE_AMBIENT;
            };
            playToVictimAt(target, amb, x, y, z, 0.8F, 1.0F);
        } else if (roll < 63) {
            playToVictimAt(target, random.nextBoolean() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE, x, y, z, 0.8F, 1.0F);
        } else if (roll < 68) {
            playToVictimAt(target, SoundEvents.AMBIENT_CAVE.value(), x, y, z, 1.0F, 1.0F); // the cave that isn't there
        } else if (roll < 72) {
            // someone swimming — strokes then a splash.
            for (int i = 0; i < 3; i++) {
                state.pending.add(new Pending((tgt, lvl) ->
                        playToVictimAt(tgt, SoundEvents.PLAYER_SWIM, x, y, z, 0.8F, 1.0F), i * 6 + 1));
            }
            state.pending.add(new Pending((tgt, lvl) -> playToVictimAt(tgt, SoundEvents.PLAYER_SPLASH, x, y, z, 0.8F, 1.0F), 20));
        } else if (roll < 76) {
            playToVictimAt(target, SoundEvents.ENDERMAN_TELEPORT, x, y, z, 0.9F, 1.0F);
        } else if (roll < 80) {
            // A fuse then a distant blast.
            playToVictimAt(target, SoundEvents.TNT_PRIMED, x, y, z, 0.8F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) ->
                    playToVictimAt(tgt, SoundEvents.GENERIC_EXPLODE.value(), x + 6, y, z + 6, 0.6F, 1.0F), 34));
        } else if (roll < 83) {
            // A creeper priming then going off right behind you — with a camera flinch on the blast.
            Vec3 behind = target.position().add(directionBehind(target).scale(3.0));
            playToVictimAt(target, SoundEvents.CREEPER_PRIMED, behind.x, behind.y + 1.0, behind.z, 1.0F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) -> {
                playToVictimAt(tgt, SoundEvents.GENERIC_EXPLODE.value(), behind.x, behind.y + 1.0, behind.z, 0.9F, 1.0F);
                dwellerShake(tgt);
            }, 30));
        } else if (roll < 86) {
            playToVictimAt(target, SoundEvents.ANVIL_LAND, x, y, z, 0.7F, 1.0F);
        } else if (roll < 89) {
            playToVictimAt(target, SoundEvents.ARROW_SHOOT, x, y, z, 0.9F, 1.0F);
        } else if (roll < 92) {
            playToVictimAt(target, random.nextBoolean() ? SoundEvents.ITEM_PICKUP : SoundEvents.EXPERIENCE_ORB_PICKUP, x, y, z, 0.6F, 1.0F);
        } else if (roll < 95) {
            // someone lands nearby, then takes a step — sells it as a person.
            playToVictimAt(target, SoundEvents.PLAYER_BIG_FALL, x, y, z, 0.8F, 1.0F);
            state.pending.add(new Pending((tgt, lvl) -> playToVictimAt(tgt, WitchModSounds.DWELLER_STEP.get(), x, y, z, 0.6F, 1.0F), 6 + random.nextInt(6)));
        } else {
            playToVictimAt(target, WitchModSounds.DWELLER_MOOD.get(), x, y, z, 0.5F, 0.9F);
        }
    }

    // --- Ambient dread (while absent) -------------------------------------------------------------------


    // --- The hunt (survivable) --------------------------------------------------------------------------

    void beginChase(ServerPlayer target, State state) {
        state.phase = Phase.CHASE;
        state.chaseTicks = 0;
        state.chaseCount++;
        state.chaseStuck = 0;
        state.heat = 1.0;
        // this hunt runs a rolled ~20–35s before it breaks off — slightly longer at higher dread (the low end of
        // the roll creeps up toward the middle as dread maxes).
        int cmin = Config.DWELLER_CHASE_MIN_TICKS.get();
        int cmax = Math.max(cmin + 1, Config.DWELLER_CHASE_MAX_TICKS.get());
        double fdur = dreadFrac(state);
        int lo = (int) Mth.lerp((float) fdur, cmin, cmin + (cmax - cmin) * 0.5F);
        state.chaseMaxThisRun = lo + target.getRandom().nextInt(Math.max(1, cmax - lo));
        // BRIDGE: a watch/sizeup/lunge already out erupts into the hunt from where it stands — UNLESS that's
        // dangerously close (a sizeup / close watch bridging within a few blocks would be an instant, uncounterable
        // touch-kill), in which case it's pulled BACK to a safe start distance. A FRESH chase starts FAR by default,
        // with a small dread-scaling chance of a CLOSECHASE that begins at the old, much closer distance.
        boolean hadEntity = state.entity != null && state.entity.isAlive();
        double curDist = hadEntity ? state.entity.distanceTo(target) : Double.MAX_VALUE;
        ensureEntity(target, state);
        if (state.entity != null) {
            boolean keepPlace = hadEntity && curDist >= Config.DWELLER_CHASE_MIN_START_DISTANCE.get();
            if (!keepPlace) {
                double startDist;
                if (hadEntity) {
                    // a bridge that was too close — bring it back to at least the close-chase distance
                    startDist = Config.DWELLER_CLOSE_CHASE_DISTANCE.get();
                } else {
                    double closeChance = Mth.lerp((float) dreadFrac(state),
                            (float) (double) Config.DWELLER_CLOSE_CHASE_MIN_CHANCE.get(),
                            (float) (double) Config.DWELLER_CLOSE_CHASE_MAX_CHANCE.get());
                    startDist = target.getRandom().nextFloat() < closeChance
                            ? Config.DWELLER_CLOSE_CHASE_DISTANCE.get()   // CLOSECHASE — right on top of you
                            : Config.DWELLER_CHASE_START_DISTANCE.get();  // the far default
                }
                Vec3 spot = target.position().add(directionBehind(target).scale(startDist));
                placeAt(state.entity, groundSnap(target, spot), target, target.serverLevel());
            }
        }
        state.sizeUp = false;
        state.lungeTicks = 0;
        state.chaseLungeTicks = 0;
        state.chaseLungeCd = 0;
        state.chaseLastDist = state.entity != null ? state.entity.distanceTo(target) : 6.0;
        state.chaseStepDist = 0.0;
        // switch the entity into REAL PHYSICS mode for the hunt — gravity, collision, step-up, jumping, pathfinding
        // all vanilla. It genuinely SPRINTS after you along the ground instead of gliding through the air.
        enterPhysicsMode(state.entity);
        setBreathing(target, state, false); // the chase has its own soundscape
        target.setData(WitchModAttachments.DWELLER_ACTIVE, 3);
        // the SCREAM that begins the hunt — at the dweller's starting spot, so it comes from where it lunges from.
        Vec3 at = state.entity != null ? state.entity.position() : target.position();
        playToVictimAt(target, WitchModSounds.DWELLER_SCREAM.get(), at.x, at.y + 1.0, at.z, 1.2F, 1.0F);
    }

    /**
     * the hunt — it genuinely SPRINTS after you using REAL physics: vanilla ground pathfinding drives it, so it
     * runs along the floor, jumps up single blocks, climbs stairs, rounds corners, floats across water and obeys
     * gravity + collision. Only when it's TRULY stuck (walled in / you're somewhere it can't route to) for a long
     * stretch does it fall back to a rare teleport. Touch = instant death (the only thing that clears dread).
     */
    void driveChase(ServerPlayer target, State state) {
        ensureEntity(target, state);
        SpaghettiManEntity dweller = state.entity;
        if (dweller == null) {
            return;
        }
        Vec3 you = target.position();
        double dist = dweller.distanceTo(target);
        if (dist <= Config.DWELLER_TOUCH_DISTANCE.get()) {
            finale(target, state); // it caught you
            return;
        }
        // nowhere is safe while it hunts: bumping into ANY passive entity (an animal, villager, golem) mid-chase
        // sets off the same gory finale. ONLY the actual chase target is ever caught this way.
        if (Config.DWELLER_PASSIVE_COLLISION_KILL.get()
                && target.level() instanceof ServerLevel chaseLevel && collidedWithPassive(target, chaseLevel)) {
            finale(target, state);
            return;
        }

        if (state.chaseLungeCd > 0) {
            state.chaseLungeCd--;
        }
        // MID-CHASE LEAP in progress: it froze the AI and is arcing under pure physics — DON'T reassert
        // navigation (which would fight the impulse). Handle it and bail before enterPhysicsMode.
        if (state.chaseLungeTicks > 0) {
            driveChaseLunge(target, state);
            return;
        }
        enterPhysicsMode(dweller); // (re)assert physics + navigation for the ordinary sprint
        if (dreadFrac(state) >= Config.DWELLER_CHASE_LUNGE_MIN_FRAC.get() && state.chaseLungeCd <= 0
                && dist <= Config.DWELLER_CHASE_LUNGE_RANGE.get() && dweller.onGround()) {
            startChaseLunge(target, state, dweller.position(), you, dist);
            return;
        }

        // speed scales with dread AND RAMPS across the hunt (starts a touch slow, winds up faster). This is the
        // navigation SPEED MULTIPLIER on the entity's MOVEMENT_SPEED, so vanilla moves it under real physics.
        double f = dreadFrac(state);
        double rampProg = Mth.clamp(state.chaseTicks / (double) Config.DWELLER_CHASE_RAMP_TICKS.get(), 0.0, 1.0);
        // starts near a real sprint, builds a little across the hunt, plus a small dread bonus — deliberately not
        // beastly. This is the navigation speed multiplier on MOVEMENT_SPEED.
        double rampMult = Mth.lerp((float) rampProg, 0.85F, 1.0F);
        double sprint = Config.DWELLER_CHASE_SPRINT.get() * rampMult * (1.0 + 0.18 * f);

        // re-path toward you a few times a second (or the moment it finishes/loses its path), and keep it facing
        // you. Vanilla's serverAiStep (running because we cleared noAi) walks the path with gravity/collision.
        if (dweller.tickCount % 5 == 0 || dweller.getNavigation().isDone()) {
            boolean pathed = dweller.getNavigation().moveTo(you.x, you.y, you.z, sprint);
            if (!pathed) {
                // no path exists to you right now — nudge the move control straight at you as a stopgap.
                dweller.getMoveControl().setWantedPosition(you.x, you.y, you.z, sprint);
            }
        }
        dweller.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());

        // WATER: ground pathing crawls in water and it gets stuck, so while submerged we DRIVE it directly —
        // a strong swim straight at you plus buoyancy to hold it near the surface — so it powers across water fast.
        if (dweller.isInWater()) {
            Vec3 toYou = new Vec3(you.x - dweller.getX(), 0, you.z - dweller.getZ());
            if (toYou.lengthSqr() > 1.0E-4) {
                toYou = toYou.normalize().scale(Config.DWELLER_CHASE_WATER_SPEED.get());
            }
            Vec3 dm = dweller.getDeltaMovement();
            dweller.setDeltaMovement(toYou.x, Math.max(dm.y, 0.06), toYou.z);
            dweller.hasImpulse = true;
        }

        // FOOTSTEPS from the ACTUAL distance it moved this tick — so the cadence naturally quickens with the
        // (ramping) sprint. Pitch lifts a touch with the pace.
        double moved = dweller.position().distanceTo(new Vec3(dweller.xo, dweller.yo, dweller.zo));
        state.chaseStepDist += moved;
        double stride = Config.DWELLER_CHASE_STRIDE.get();
        if (state.chaseStepDist >= stride && moved > 0.01) {
            state.chaseStepDist -= stride;
            Vec3 fp = dweller.position(); // played AT the entity so you can hear exactly where it is
            float pitch = 0.9F + 0.3F * (float) rampMult + target.getRandom().nextFloat() * 0.1F;
            playToVictimAt(target, WitchModSounds.DWELLER_STEP.get(), fp.x, fp.y + 0.1, fp.z, 0.85F, pitch);
        }

        // stuck detection: it must keep CLOSING. Only when it truly cannot for a LONG stretch (no path + not
        // getting nearer) does it fall back to a rare teleport — the last resort, not the mover.
        double newDist = dweller.distanceTo(target);
        boolean progressing = newDist < state.chaseLastDist - 0.02;
        if (progressing) {
            state.chaseStuck = 0;
        } else {
            state.chaseStuck += dweller.getNavigation().isDone() ? 2 : 1;
        }
        state.chaseLastDist = newDist;
        int stuckThreshold = (int) (Config.DWELLER_CHASE_STUCK_TICKS.get() * (1.0 + f));
        if (state.chaseStuck >= stuckThreshold && dist > 5.0 && !dweller.isInWater()) {
            chaseTeleport(target, dweller);
            state.chaseStuck = 0;
            state.chaseLastDist = dweller.distanceTo(target);
        }

        if (target.tickCount % 6 == 0) {
            float q = (float) (1.0 - Mth.clamp(dist / 16.0, 0.0, 1.0));
            playToVictim(target, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.1F + 0.5F * q);
        }

        state.chaseTicks++;
        if (state.chaseTicks >= state.chaseMaxThisRun) {
            loseTrail(target, state);
        }
    }

    /** puts the entity into real-physics mode for a hunt (gravity/collision/pathfinding + a sprint speed). Idempotent. */
    static void enterPhysicsMode(SpaghettiManEntity e) {
        if (e == null) {
            return;
        }
        if (e.isNoAi()) {
            e.setNoAi(false); // let vanilla serverAiStep run navigation + move/look/jump control
        }
        e.setNoGravity(false);
        e.noPhysics = false;
        // NOTE: deliberately NOT setSprinting — that would stack a hidden +30% speed modifier on top of the
        // MOVEMENT_SPEED math below and make it beastly. The pace is entirely the attribute × navigation multiplier.
        var speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getBaseValue() != Config.DWELLER_CHASE_MOVE_SPEED.get()) {
            speed.setBaseValue(Config.DWELLER_CHASE_MOVE_SPEED.get());
        }
        if (e.getNavigation() instanceof GroundPathNavigation gpn) {
            gpn.setCanFloat(true);      // float/chase across water rather than sinking
            gpn.setCanOpenDoors(true);  // walk through doors it can open
        }
    }

    /**
     * kicks off a mid-chase LEAP — a real, dodgeable physics jump at you (a velocity impulse; gravity arcs it):
     * <ul>
     *   <li><b>climb</b> — you're camping height above it: a strong UP+toward leap to scale the pillar/tower;</li>
     *   <li><b>distancegain</b> — the general one / close finisher: a flat hurl straight at you.</li>
     * </ul>
     * The navigation is stopped for the leap window so it isn't fought; a sidestep beats it (no homing).
     */
    void startChaseLunge(ServerPlayer target, State state, Vec3 here, Vec3 you, double dist) {
        SpaghettiManEntity e = state.entity;
        if (e == null) {
            return;
        }
        boolean camping = you.y > here.y + Config.DWELLER_CHASE_LUNGE_CLIMB_HEIGHT.get();
        Vec3 flat = new Vec3(you.x - here.x, 0, you.z - here.z);
        flat = flat.lengthSqr() < 1.0E-4 ? directionBehind(target).scale(-1) : flat.normalize();
        double vh = (camping ? Config.DWELLER_CHASE_LUNGE_CLIMB_SPEED.get() : Config.DWELLER_CHASE_LUNGE_SPEED.get()) / 20.0;
        double vy = camping ? Config.DWELLER_CHASE_LUNGE_CLIMB_UP.get() : 0.42;
        e.getNavigation().stop();
        e.setNoAi(true); // freeze the AI so the moveControl can't override the leap; gravity + collision still apply
        e.setDeltaMovement(flat.x * vh, vy, flat.z * vh); // a genuine leap — physics carries and curves it
        e.hasImpulse = true;
        e.hurtMarked = true;
        state.chaseLungeTicks = Config.DWELLER_CHASE_LUNGE_TICKS.get();
        state.chaseLungeCd = Config.DWELLER_CHASE_LUNGE_COOLDOWN_TICKS.get();
        state.heat = 1.0;
        faceVictimBody(e, flat);
        Vec3 p = e.position();
        playToVictimAt(target, SoundEvents.RAVAGER_ROAR, p.x, p.y + 1.0, p.z, 1.3F, 0.5F); // slowed roar
        playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), p.x, p.y + 1.0, p.z, 0.9F, 0.8F);
    }

    /** watches a physics leap: touch = finale; it ends when it lands (back on the ground) or the window elapses. */
    void driveChaseLunge(ServerPlayer target, State state) {
        SpaghettiManEntity e = state.entity;
        if (e == null) {
            state.chaseLungeTicks = 0;
            return;
        }
        faceVictimBody(e, target.position().subtract(e.position()));
        if (e.distanceTo(target) <= Config.DWELLER_TOUCH_DISTANCE.get()) {
            finale(target, state);
            return;
        }
        state.chaseLungeTicks--;
        boolean landed = e.onGround() && state.chaseLungeTicks < Config.DWELLER_CHASE_LUNGE_TICKS.get() - 2;
        if (landed || state.chaseLungeTicks <= 0) {
            state.chaseLungeTicks = 0; // leap over — resume the sprint next tick
            state.chaseLastDist = e.distanceTo(target);
            state.chaseStuck = 0;
            state.chaseStepDist = 0.0;
        }
    }

    /** the fallback blink: it can't path to you, so it steps to a reachable ground spot noticeably closer. */
    void chaseTeleport(ServerPlayer target, SpaghettiManEntity dweller) {
        Vec3 from = dweller.position();
        particleToVictim(target, ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 18, 0.3, 0.6, 0.3, 0.2);
        // aim for a spot behind you at teleport distance, on the ground — closer than it currently is.
        double ang = target.getRandom().nextDouble() * Math.PI * 2;
        double d = Config.DWELLER_CHASE_TELEPORT_DISTANCE.get() * (0.6 + target.getRandom().nextDouble() * 0.5);
        Vec3 spot = groundSnap(target, target.position().add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
        dweller.setPos(spot.x, spot.y, spot.z);
        faceVictimBody(dweller, target.position().subtract(spot));
        particleToVictim(target, ParticleTypes.PORTAL, spot.x, spot.y + 1.0, spot.z, 18, 0.3, 0.6, 0.3, 0.2);
        // WIND at both ends — the diegetic sound of it MOVING, in place of a vanilla teleport pop.
        playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), from.x, from.y + 1.0, from.z, 0.9F, 1.0F);
        playToVictimAt(target, WitchModSounds.DWELLER_WIND.get(), spot.x, spot.y + 1.0, spot.z, 0.9F, 1.0F);
    }

    /** lost the trail (anti-softlock). Back to watching — but the dread stays MAXED, so it isn't over. */
    void loseTrail(ServerPlayer target, State state) {
        despawn(state);
        state.phase = Phase.RELEASE;
        state.heat = 0.4;
        state.timer = Config.DWELLER_RELEASE_MIN_TICKS.get();
        // the cooldown = the window where a new chase can't be rolled. It SHRINKS with dread: near max it's tiny
        // (~5s), so hunts come back almost at once; lower down it gives real breathing room.
        state.chaseCooldown = chaseCooldownFor(state);
        playToVictim(target, SoundEvents.WARDEN_DEATH, 0.7F, 1.1F);
    }

    /** the chase cooldown, scaled from the long value at the chase-range floor down to the min at max dread. */
    static int chaseCooldownFor(State state) {
        double t3 = Config.DWELLER_TIER3_THRESHOLD.get();
        double max = Config.DWELLER_ANGER_MAX.get();
        double f = Mth.clamp((float) ((state.anger - t3) / Math.max(1.0, max - t3)), 0.0F, 1.0F);
        return (int) Mth.lerp((float) f, Config.DWELLER_CHASE_COOLDOWN_TICKS.get(), Config.DWELLER_CHASE_MIN_COOLDOWN_TICKS.get());
    }

    /**
     * true if {@code target} is currently overlapping any PASSIVE living entity — an animal, villager, golem,
     * anything that isn't a player, a hostile ({@link net.minecraft.world.entity.monster.Enemy}), or one of the
     * mod's own illusion entities (the Dweller itself / its watcher eyes).
     */
    private boolean collidedWithPassive(ServerPlayer target, ServerLevel level) {
        return !level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(0.1),
                e -> e != target && e.isAlive()
                        && !(e instanceof net.minecraft.world.entity.player.Player)
                        && !(e instanceof net.minecraft.world.entity.monster.Enemy)
                        && !(e instanceof SpaghettiManEntity)
                        && !(e instanceof com.oliver.witchmod.entities.WatcherEyesEntity)).isEmpty();
    }

    void finale(ServerPlayer target, State state) {
        ServerLevel level = target.serverLevel();
        double x = target.getX(), y = target.getY(), z = target.getZ();
        RandomSource rr = target.getRandom();
        // the gore: a big bloody burst with the bang (half vol) + splatter.
        bloodyBurst(level, x, y + 0.7, z);
        // ...and a real explosion so it goes off like the explosion event — but NO terrain damage (Oliver's call),
        // so it's the boom + knockback + collateral to nearby others, never a crater in the world.
        // ⚠ The source ENTITY must be null: Explosion collects victims via getEntities(source, box) EXCLUDING
        // the source, so naming the target would leave them out of their own blast. (Same trap as Super
        // explosive / Heavy.) They're killed by the haunted damage below anyway; this is for the boom + collateral.
        float power = (float) (double) Config.DWELLER_FINALE_EXPLOSION_POWER.get();
        if (power > 0.0F) {
            level.explode(null, x, y + 0.5, z, power, Level.ExplosionInteraction.NONE);
        }

        // the remains are PLACED on the nearest ground — your own head + a splatter of redstone — rather than
        // dropped as items; they fall back to scattered items only if there's nowhere to place them.
        placeRemains(level, target, rr);

        // TREAT THE TOUCH AS INSTANT DEATH — even if the actual kill is refused (creative / invulnerable): the
        // chase is concluded and the dread wiped here and now, so it can never leave you stuck at max dread with
        // an undead body. This is the ONE reset.
        state.anger = 0.0;
        despawn(state);
        clearEyes(target, state);
        state.pending.clear();
        state.phase = Phase.LULL;
        state.heat = 0.0;
        setBreathing(target, state, false);
        state.chaseCooldown = Config.DWELLER_CHASE_COOLDOWN_TICKS.get();
        state.timer = dormantDuration(target, dreadFrac(state));
        target.setData(WitchModAttachments.DWELLER_ACTIVE, 0);
        target.setData(WitchModAttachments.DWELLER_DREAD, 0.0F);
        target.setData(WitchModAttachments.DWELLER_ANGER, 0.0);
        // the kill itself — the custom witchmod:haunted source (its own death message). SKIPPED in creative:
        // the touch still does its full burst + ends the whole encounter and wipes dread, you just aren't killed.
        if (!target.isCreative()) {
            target.hurt(com.oliver.witchmod.data.WitchModDamageTypes.theDweller(level), Float.MAX_VALUE);
        }
    }

    /** places the victim's head + a redstone splatter on the nearest ground; drops them as items as a fallback. */
    private void placeRemains(ServerLevel level, ServerPlayer target, RandomSource rr) {
        double x = target.getX(), y = target.getY(), z = target.getZ();
        boolean placedHead = false;
        double gy = surfaceY(level, x, z, y);
        BlockPos headPos = BlockPos.containing(x, gy, z);
        if (level.getBlockState(headPos).canBeReplaced()
                && level.getBlockState(headPos.below()).isFaceSturdy(level, headPos.below(), net.minecraft.core.Direction.UP)) {
            level.setBlockAndUpdate(headPos, Blocks.PLAYER_HEAD.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SkullBlock.ROTATION, rr.nextInt(16)));
            if (level.getBlockEntity(headPos) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity sbe) {
                sbe.setOwner(new ResolvableProfile(target.getGameProfile()));
            }
            placedHead = true;
        }
        // redstone splatter around the head on any sturdy ground.
        boolean placedRedstone = false;
        for (int i = 0; i < 5; i++) {
            double ox = x + (rr.nextDouble() - 0.5) * 3.0;
            double oz = z + (rr.nextDouble() - 0.5) * 3.0;
            double oy = surfaceY(level, ox, oz, y);
            BlockPos rp = BlockPos.containing(ox, oy, oz);
            if (level.getBlockState(rp).canBeReplaced()
                    && level.getBlockState(rp.below()).isFaceSturdy(level, rp.below(), net.minecraft.core.Direction.UP)) {
                level.setBlockAndUpdate(rp, Blocks.REDSTONE_WIRE.defaultBlockState());
                placedRedstone = true;
            }
        }
        if (!placedHead) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(target.getGameProfile()));
            spawnScattered(level, x, y, z, head, rr);
        }
        if (!placedRedstone) {
            spawnScattered(level, x, y, z, new ItemStack(Items.REDSTONE, 3 + rr.nextInt(5)), rr);
        }
    }

    /** drops an item flung out with a random pop and facing, so it lands at a random orientation. */
    static void spawnScattered(ServerLevel level, double x, double y, double z, ItemStack stack, RandomSource r) {
        ItemEntity e = new ItemEntity(level, x, y + 0.5, z, stack);
        e.setDeltaMovement((r.nextDouble() - 0.5) * 0.5, 0.25 + r.nextDouble() * 0.35, (r.nextDouble() - 0.5) * 0.5);
        e.setYRot(r.nextFloat() * 360.0F);
        level.addFreshEntity(e);
    }

    // --- Manifestation spots ----------------------------------------------------------------------------

    Vec3 pickManifestSpot(ServerPlayer target, ServerLevel level, State state) {
        int tier = tier(state.anger);
        if (target.isSleeping() && tier >= 1) {
            return bedsideSpot(target);
        }
        double frac = dreadFrac(state);
        double dist = manifestDistance(target, state);
        // COUNTERPLAY: while you're lit, it keeps its distance — no close manifestations in bright light.
        if (inBrightLight(target)) {
            dist = Math.max(dist, Config.DWELLER_LIGHT_LURK_DISTANCE.get() + 3.0);
        }
        // low dread: it WANTS to be seen — a distant figure standing in your view with a clear line of sight,
        // so the far watches actually register. High dread: it stalks out of view (peripheral/behind/windows).
        double inViewChance = Mth.lerp((float) frac, 0.8F, 0.15F);
        if (target.getRandom().nextDouble() < inViewChance) {
            Vec3 v = visibleInViewSpot(target, level, dist);
            if (v != null) {
                return v;
            }
        }
        int roll = target.getRandom().nextInt(100);
        if (tier >= 2 && roll < 45) {
            return inViewSpot(target, dist);
        }
        if (tier >= 1 && roll < 62) {
            Vec3 win = windowWatchSpot(target, level, dist);
            if (win != null) {
                return win;
            }
        }
        if (roll < 82) {
            return peripheralSpot(target, dist);
        }
        return behindSpot(target, dist);
    }

    /** an in-view spot at distance with a genuinely CLEAR line of sight to you — so you actually catch it. */
    @Nullable
    static Vec3 visibleInViewSpot(ServerPlayer target, ServerLevel level, double dist) {
        for (int i = 0; i < 8; i++) {
            Vec3 s = inViewSpot(target, dist);
            if (clearLineOfSight(level, target.getEyePosition(), s.add(0, 1.6, 0))) {
                return s;
            }
        }
        return null;
    }

    static Vec3 inViewSpot(ServerPlayer target, double dist) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        double rot = (target.getRandom().nextDouble() * 2 - 1) * 0.35;
        return groundSnap(target, target.position().add(rotateY(flat, rot).scale(dist)));
    }

    static Vec3 peripheralSpot(ServerPlayer target, double dist) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        RandomSource r = target.getRandom();
        double side = r.nextBoolean() ? 1 : -1;
        double rot = side * (1.05 + r.nextDouble() * 0.5);
        return groundSnap(target, target.position().add(rotateY(flat, rot).scale(dist)));
    }

    static Vec3 behindSpot(ServerPlayer target, double dist) {
        return groundSnap(target, target.position().add(directionBehind(target).scale(dist)));
    }

    static Vec3 bedsideSpot(ServerPlayer target) {
        RandomSource random = target.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        return groundSnap(target, target.position().add(Math.cos(angle) * 1.4, 0, Math.sin(angle) * 1.4));
    }

    /**
     * the FOOT of the bed — a couple of blocks beyond the foot end, facing you — so a sleeper looking down the
     * bed actually SEES it looming there. Falls back to a random bedside spot if the bed can't be resolved.
     */
    static Vec3 bedFootSpot(ServerPlayer target, ServerLevel level) {
        BlockPos bed = target.getSleepingPos().orElse(target.blockPosition());
        BlockState st = level.getBlockState(bed);
        if (st.getBlock() instanceof BedBlock && st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = st.getValue(BlockStateProperties.HORIZONTAL_FACING); // points FOOT -> HEAD
            BlockPos foot = st.getValue(BedBlock.PART) == BedPart.HEAD ? bed.relative(facing.getOpposite()) : bed;
            Direction away = facing.getOpposite(); // beyond the foot, so it stands at the end you look toward
            Vec3 spot = Vec3.atBottomCenterOf(foot).add(away.getStepX() * 1.7, 0, away.getStepZ() * 1.7);
            return groundSnap(target, spot);
        }
        return bedsideSpot(target);
    }

    /**
     * A genuine WINDOW watch: finds actual GLASS (block or pane) near the player and stands the figure right up
     * against the FAR side of it, so it's literally peering at you through the window. Returns null if there's no
     * glass to lurk behind (the caller then falls back to an ordinary watch). The {@code dist} arg is ignored —
     * a window watch is defined by the window, not a distance band.
     */
    @Nullable
    static Vec3 windowWatchSpot(ServerPlayer target, ServerLevel level, double dist) {
        BlockPos origin = target.blockPosition();
        RandomSource r = target.getRandom();
        List<BlockPos> glass = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-6, -2, -6), origin.offset(6, 3, 6))) {
            if (isGlass(level.getBlockState(p))) {
                glass.add(p.immutable());
            }
        }
        if (glass.isEmpty()) {
            return null;
        }
        java.util.Collections.shuffle(glass, new java.util.Random(r.nextLong()));
        for (BlockPos g : glass) {
            Vec3 gc = Vec3.atCenterOf(g);
            Vec3 dir = new Vec3(gc.x - target.getX(), 0, gc.z - target.getZ());
            if (dir.horizontalDistanceSqr() < 0.25) {
                continue; // glass basically on top of you — no "far side" to stand on
            }
            dir = dir.normalize();
            // stand pressed against the OUTSIDE of the pane, at the window's height, on whatever ground is there.
            Vec3 spot = groundSnap(target, new Vec3(gc.x + dir.x * 1.05, g.getY(), gc.z + dir.z * 1.05));
            // the glass must actually be BETWEEN you and it (a clear line would mean it isn't really a window watch).
            if (!clearLineOfSight(level, target.getEyePosition(), spot.add(0, 1.4, 0))) {
                return spot;
            }
        }
        return null;
    }

    /** solid glass blocks AND glass panes (but not iron bars) — the "window" materials it lurks behind. */
    static boolean isGlass(BlockState st) {
        if (st.is(BlockTags.IMPERMEABLE)) {
            return true; // all solid glass blocks (clear/stained/tinted)
        }
        Block b = st.getBlock();
        return b == Blocks.GLASS_PANE || b instanceof net.minecraft.world.level.block.StainedGlassPaneBlock;
    }

    /** A peripheral spot with NO clear line of sight to you — used to run-and-hide / manifest out of view. */
    @Nullable
    static Vec3 hiddenSpot(ServerPlayer target, ServerLevel level, double dist) {
        for (int i = 0; i < 6; i++) {
            Vec3 spot = peripheralSpot(target, dist);
            if (!clearLineOfSight(level, target.getEyePosition(), spot.add(0, 1.6, 0))) {
                return spot;
            }
        }
        return null;
    }

    // --- Entity plumbing --------------------------------------------------------------------------------

    void ensureEntity(ServerPlayer target, State state) {
        if (state.entity == null || !state.entity.isAlive()) {
            ServerLevel level = target.serverLevel();
            SpaghettiManEntity dweller = WitchModEntities.SPAGHETTI_MAN.get().create(level);
            if (dweller == null) {
                return;
            }
            dweller.setVictim(target.getUUID());
            Vec3 spot = behindSpot(target, Config.DWELLER_FAR_DISTANCE.get());
            dweller.setPos(spot.x, spot.y, spot.z);
            level.addFreshEntity(dweller);
            state.entity = dweller;
        }
        // NO DUPLICATES: discard any OTHER dweller bound to this victim (stray from a crash/relog/logic slip),
        // so there is only ever one of it out at a time.
        ServerLevel level = target.serverLevel();
        for (SpaghettiManEntity other : level.getEntitiesOfClass(SpaghettiManEntity.class,
                target.getBoundingBox().inflate(80.0),
                e -> e != state.entity && e.getVictim().map(u -> u.equals(target.getUUID())).orElse(false))) {
            other.discard();
        }
    }

    void despawn(State state) {
        if (state.entity != null && state.entity.isAlive()) {
            state.entity.discard();
        }
        state.entity = null;
    }

    static void placeAt(@Nullable SpaghettiManEntity dweller, Vec3 spot, ServerPlayer target, ServerLevel level) {
        if (dweller == null) {
            return;
        }
        dweller.setPos(spot.x, surfaceY(level, spot.x, spot.z, target.getY()), spot.z);
        faceVictimBody(dweller, target.position().subtract(dweller.position()));
    }

    static void faceVictimBody(SpaghettiManEntity dweller, Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
        dweller.setYRot(yaw);
        dweller.setYBodyRot(yaw);
        dweller.setYHeadRot(yaw);
    }

    static void faceVictimHead(SpaghettiManEntity dweller, ServerPlayer target) {
        Vec3 dir = target.getEyePosition().subtract(dweller.getEyePosition());
        if (dir.horizontalDistanceSqr() < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
        dweller.setYHeadRot(yaw);
        float pitch = (float) (-(Mth.atan2(dir.y, dir.horizontalDistance()) * (180.0 / Math.PI)));
        dweller.setXRot(Mth.clamp(pitch, -40.0F, 40.0F));
    }

    // --- Observation / geometry / environment -----------------------------------------------------------

    static boolean isObserving(ServerPlayer target, SpaghettiManEntity dweller, ServerLevel level) {
        Vec3 eye = target.getEyePosition();
        Vec3 to = dweller.position().add(0, dweller.getBbHeight() * 0.7, 0);
        Vec3 dir = to.subtract(eye);
        double len = dir.length();
        if (len < 0.6) {
            return true;
        }
        double dot = target.getLookAngle().dot(dir.scale(1.0 / len));
        return dot >= Config.DWELLER_VIEW_CONE_DOT.get() && clearLineOfSight(level, eye, to);
    }

    static boolean clearLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(from) >= to.distanceTo(from) - 0.6;
    }

    static boolean inBrightLight(ServerPlayer target) {
        return target.serverLevel().getMaxLocalRawBrightness(target.blockPosition()) >= Config.DWELLER_LIGHT_LEVEL.get();
    }

    static boolean isDark(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        return level.getMaxLocalRawBrightness(target.blockPosition()) <= 7 || !level.isDay();
    }

    static int nearbyPlayers(ServerPlayer target) {
        double r = Config.DWELLER_COMPANY_RADIUS.get();
        return target.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                target.getBoundingBox().inflate(r), p -> p != target && p.isAlive() && !p.isSpectator()).size();
    }

    static Vec3 directionBehind(ServerPlayer target) {
        double yaw = Math.toRadians(target.getYRot());
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 behind = facing.scale(-1);
        double rot = (target.getRandom().nextDouble() * 2 - 1) * 1.1;
        return rotateY(behind, rot);
    }

    static Vec3 rotateY(Vec3 v, double rot) {
        double c = Math.cos(rot), s = Math.sin(rot);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
    }

    static Vec3 groundSnap(ServerPlayer target, Vec3 spot) {
        return new Vec3(spot.x, surfaceY(target.serverLevel(), spot.x, spot.z, target.getY()), spot.z);
    }

    /**
     * finds the floor to stand on NEAR the player's own height — NOT the world surface. Scans down from just
     * above the player's feet for the first block that blocks motion with clear air over it, so it anchors to
     * the cave floor / bridge / ledge you're actually on. Falls back to the player's Y (rather than the sky)
     * if there's nothing solid within range, so it never plants itself on the surface far overhead in a cave.
     */
    static double surfaceY(ServerLevel level, double x, double z, double refY) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        int top = Mth.floor(refY) + 3;
        int bottom = Mth.floor(refY) - 24;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            pos.set(ix, y, iz);
            BlockState st = level.getBlockState(pos);
            // stand ON the WATER SURFACE (where water meets open air) — so it can haunt you even lost at sea.
            if (st.getFluidState().is(FluidTags.WATER)) {
                BlockState above = level.getBlockState(pos.above());
                if (!above.getFluidState().is(FluidTags.WATER) && !above.blocksMotion()) {
                    return y + 1.0;
                }
                continue;
            }
            if (st.blocksMotion() && !level.getBlockState(pos.above()).blocksMotion()) {
                return y + 1.0;
            }
        }
        return refY;
    }

    static void setFlicker(ServerPlayer target, int ticks) {
        target.setData(WitchModAttachments.DWELLER_FLICKER, target.serverLevel().getGameTime() + ticks);
    }

    // --- Tier / timing ----------------------------------------------------------------------------------

    static int tier(double anger) {
        if (anger >= Config.DWELLER_TIER3_THRESHOLD.get()) return 3;
        if (anger >= Config.DWELLER_TIER2_THRESHOLD.get()) return 2;
        if (anger >= Config.DWELLER_TIER1_THRESHOLD.get()) return 1;
        return 0;
    }

    /**
     * how far it manifests — a SMOOTH slide from far to close as dread climbs, so it eases in over many
     * sightings instead of jumping "barely notice → in your face". Farther early, closer late, with jitter.
     */
    static double manifestDistance(ServerPlayer target, State state) {
        double frac = Mth.clamp((float) (state.anger / Config.DWELLER_ANGER_MAX.get()), 0.0F, 1.0F);
        double far = Config.DWELLER_FAR_DISTANCE.get();
        double near = Config.DWELLER_INSPECT_DISTANCE.get();
        double dist = far + (near - far) * frac;
        double jitter = 0.85 + target.getRandom().nextDouble() * 0.3;
        return Math.max(near, dist * jitter);
    }

    static double dreadFrac(State state) {
        return Mth.clamp((float) (state.anger / Config.DWELLER_ANGER_MAX.get()), 0.0F, 1.0F);
    }

    static int dormantDuration(ServerPlayer target, double dreadFrac) {
        int min = Config.DWELLER_DORMANT_MIN_TICKS.get();   // 50s
        int max = Config.DWELLER_DORMANT_MAX_TICKS.get();   // 180s
        double f = Mth.clamp((float) dreadFrac, 0.0F, 1.0F);
        // downtime stays SUBSTANTIAL at all times (50–180s) — encounters are never constant, even at max dread.
        // higher dread only biases toward the shorter end of that range, it never collapses it.
        int hi = (int) Mth.lerp(f, max, min + (max - min) * 0.45);
        int lo = min + (int) ((hi - min) * 0.25);
        return lo + target.getRandom().nextInt(Math.max(1, hi - lo));
    }

    static int manifestLifespan(ServerPlayer target, int tier) {
        int min = Config.DWELLER_MANIFEST_MIN_TICKS.get();
        int max = Config.DWELLER_MANIFEST_MAX_TICKS.get();
        double scale = 1.0 + 0.9 * tier;
        int lo = (int) (min * scale);
        int hi = (int) (max * scale);
        return lo + target.getRandom().nextInt(Math.max(1, hi - lo));
    }

    static int ambientGap(ServerPlayer target) {
        int min = Config.DWELLER_AMBIENT_MIN_TICKS.get();
        int max = Config.DWELLER_AMBIENT_MAX_TICKS.get();
        return min + target.getRandom().nextInt(Math.max(1, max - min));
    }

    static int eventGap(ServerPlayer target, int tier) {
        int min = Config.DWELLER_EVENT_INTERVAL_MIN_TICKS.get();
        int max = Config.DWELLER_EVENT_INTERVAL_MAX_TICKS.get();
        double scale = 1.0 - 0.22 * tier;
        return (int) Math.max(20, (min + target.getRandom().nextInt(Math.max(1, max - min))) * scale);
    }

    static int hallucinationGap(ServerPlayer target, int tier) {
        int min = Config.DWELLER_HALLUCINATION_MIN_TICKS.get();
        int max = Config.DWELLER_HALLUCINATION_MAX_TICKS.get();
        // rare and impactful — rarest of all early on, easing up only as dread climbs.
        double mult = switch (tier) {
            case 0 -> 2.2;
            case 1 -> 1.4;
            case 2 -> 1.0;
            default -> 0.7;
        };
        return (int) Math.max(40, (min + target.getRandom().nextInt(Math.max(1, max - min))) * mult);
    }

    // --- Victim-only feedback ---------------------------------------------------------------------------

    static void playToVictim(ServerPlayer target, SoundEvent sound, float volume, float pitch) {
        playToVictimAt(target, sound, target.getX(), target.getY(), target.getZ(), volume, pitch);
    }

    static void playToVictimAt(ServerPlayer target, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        Holder<SoundEvent> holder = Holder.direct(sound);
        target.connection.send(new ClientboundSoundPacket(holder, SoundSource.HOSTILE,
                x, y, z, volume, pitch, target.getRandom().nextLong()));
    }

    static void particleToVictim(ServerPlayer target, ParticleOptions particle, double x, double y, double z,
                                         int count, double dx, double dy, double dz, double speed) {
        target.connection.send(new ClientboundLevelParticlesPacket(particle, true, x, y, z,
                (float) dx, (float) dy, (float) dz, (float) speed, count));
    }
}
