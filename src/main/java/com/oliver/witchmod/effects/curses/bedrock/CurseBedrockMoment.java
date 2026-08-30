package com.oliver.witchmod.effects.curses.bedrock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;

/**
 * Bedrock Moment (sacrificial item CRYING OBSIDIAN): one of the most OVERLOADED curses you can get — very
 * expensive, no benefit, and it plagues the victim's game with a stream of pointlessly elaborate "bugs", a
 * parody of those old short-form clips of infamous Bedrock-edition jank.
 *
 * <p>Some effects are PASSIVE (they trigger off what you do — firing arrows, mounting things, taking fall
 * damage, being shot at). The rest are ACTIVE events fired from a weighted pool on a timer.
 */
public final class CurseBedrockMoment extends Effect {
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Guards damage we re-apply ourselves (Bluetooth/Delay) from being caught by the hook again. */
    private static final Set<UUID> APPLYING = new HashSet<>();
    /** Bedrock Cooldowns (the beneficial bug): the ATTACK_SPEED modifier that quarters your swing cooldown. */
    private static final net.minecraft.resources.ResourceLocation COOLDOWNS_ID =
            com.oliver.witchmod.data.EffectUtil.modifierId("bedrock_cooldowns");

    @FunctionalInterface
    private interface DelayedAct {
        void run(ServerPlayer target, ServerLevel level);
    }

    private static final class Pending {
        final DelayedAct act;
        int delay;
        Pending(DelayedAct act, int delay) {
            this.act = act;
            this.delay = delay;
        }
    }

    private record Cand(int weight, BooleanSupplier fn) {}

    /** Package-visible so the extracted {@link BedrockEvent} registry can pass it through. */
    static final class State {
        int nextEvent;
        boolean discovered;
        final List<Pending> pending = new ArrayList<>();
        // Bluetooth
        long bluetoothUntil;
        double bluetoothStored;
        // Delay / Pause / Aimbot bookkeeping
        final Set<Integer> seenOwnProjectiles = new HashSet<>();
        final Set<Integer> boostedArrows = new HashSet<>();
        int lastVehicleId = -1;
        // Drowning
        boolean drowning;
        int drownTick;
        // Helicopter
        int heliId = -1;
        int heliTimer;
        int heliGround;   // on-ground spin grace before it starts rising
        // Server lag
        final List<Integer> laggedIds = new ArrayList<>();
        final Map<Integer, Vec3> lagPos = new HashMap<>();
        int lagTimer;
        // Creeper boats / blitz creepers
        final List<Integer> creeperBoats = new ArrayList<>();
        final List<Integer> blitzCreepers = new ArrayList<>();
        long ghostPhaseUntil;   // Ghost Block Phase: blocks placed/broken until this tick revert
        final Set<Integer> tickspeedIds = new HashSet<>(); // Tickspeed: entities ticked extra each tick
        int tickspeedTimer;
        int tickspeedMult;
        final Set<Integer> pausedIds = new HashSet<>();    // Pause: frozen (AI off) entities
        int pauseTimer;
        final Set<Integer> catchupIds = new HashSet<>();   // Pause: post-unpause high-tick catch-up
        int catchupTimer;
        int catchupMult;
        final Set<Integer> floatIds = new HashSet<>();     // Float: gravity-off entities
        int floatTimer;
        long cooldownsUntil;   // Bedrock Cooldowns: game-tick the quartered-swing-cooldown buff ends
    }

    public CurseBedrockMoment() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 110, () -> net.minecraft.world.item.Items.CRYING_OBSIDIAN);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        State s = new State();
        s.nextEvent = eventGap(target);
        STATES.put(target.getUUID(), s);
        target.setData(WitchModAttachments.BEDROCK_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        State s = STATES.remove(target.getUUID());
        if (s != null) {
            ServerLevel level = target.serverLevel();
            if (s.heliId != -1 && level.getEntity(s.heliId) instanceof Mob m) {
                m.setNoGravity(false);
            }
            for (int id : s.creeperBoats) {
                if (level.getEntity(id) instanceof Boat b) {
                    b.getPassengers().forEach(Entity::discard);
                    b.discard();
                }
            }
            if (s.drowning) {
                target.setAirSupply(target.getMaxAirSupply());
            }
        }
        com.oliver.witchmod.data.EffectUtil.removeModifier(target, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, COOLDOWNS_ID);
        // Drop any TNT still clinging to you when the curse ends.
        for (net.minecraft.world.entity.item.PrimedTnt tnt : target.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.PrimedTnt.class, target.getBoundingBox().inflate(3.0),
                t -> t.getVehicle() == target)) {
            tnt.stopRiding();
        }
        target.setData(WitchModAttachments.BEDROCK_ACTIVE, -1);
        target.setData(WitchModAttachments.BEDROCK_NIGHTCORE, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_CHUNK_REJECT, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_SOUND_DELAY, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_PHANTOM_DUR, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_PERSPECTIVE, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_GHOST_ITEM, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_INPUT_LAG, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_TEXTURE_FLICKER, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_SPRINT_RESET, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_LANGUAGE, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_SPEEDBLITZ_FREEZE, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_SPEEDBLITZ_END, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_BSOD, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_AIR_SWIM, Long.MIN_VALUE);
        target.setData(WitchModAttachments.BEDROCK_HUNGRY, Long.MIN_VALUE);
        State st = STATES.get(target.getUUID());
        if (st != null) { // un-freeze / re-gravity anything left mid-effect
            for (int id : st.pausedIds) {
                if (target.serverLevel().getEntity(id) instanceof Mob m) {
                    m.setNoAi(false);
                }
            }
            for (int id : st.floatIds) {
                Entity e = target.serverLevel().getEntity(id);
                if (e != null) {
                    e.setNoGravity(false);
                }
            }
        }
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        State s = STATES.computeIfAbsent(target.getUUID(), k -> {
            State fresh = new State();
            fresh.nextEvent = eventGap(target);
            return fresh;
        });
        ServerLevel level = target.serverLevel();

        processPending(target, s);
        tickBluetoothWindow(target, s, level);
        tickDrowning(target, s);
        tickHelicopter(s, level);
        tickServerLag(s, level);
        tickTickspeed(s, level);
        tickPauseEntities(target, s, level);
        tickFloat(s, level);
        tickCreeperBoats(target, s, level);
        tickBlitz(target, s, level);
        if (target.tickCount % 2 == 0) {
            tickAimbot(target, s, level);
            tickPause(target, s, level);
        }
        tickFling(target, s);
        stickTnt(target, level);       // Sticky TNT (passive): lit TNT you touch clings to you
        tickCooldowns(target, s);      // Bedrock Cooldowns (beneficial): maintain/expire the quartered-swing buff
        // Air Swimming (passive): a chance, WHILE swimming, that you keep swimming after you leave the water.
        if (target.isSwimming()
                && level.getGameTime() >= target.getData(WitchModAttachments.BEDROCK_AIR_SWIM)
                && target.getRandom().nextDouble() < Config.BEDROCK_AIR_SWIM_CHANCE.get()) {
            airSwim(target, level);
        }
        // Sleep Cancel (passive): a chance each tick while sleeping to be booted out of the bed.
        if (target.isSleeping() && target.getRandom().nextDouble() < Config.BEDROCK_SLEEP_CANCEL_CHANCE.get()) {
            target.stopSleeping();
        }

        if (--s.nextEvent <= 0) {
            runActiveEvent(target, s, level);
            s.nextEvent = eventGap(target);
        }
    }

    /**
     * Debug: {@code arg} names a sub-bug to force (or none = a random active one). Passive/condition-based bugs
     * (aimbot, pause, delay, ghost blocks, silent creeper, hotbar drift) report how they trigger. See §16.2b.
     */
    /** Tab-suggests every forcible name: all active events (from the registry) + the passive/info keys. */
    @Override
    public java.util.List<String> debugArgs() {
        java.util.List<String> args = new java.util.ArrayList<>();
        for (BedrockEvent e : BedrockEvents.ALL) {
            args.add(e.id());
        }
        args.addAll(java.util.List.of("fling", "aimbot", "projpause", "delay", "ghost", "silentcreeper", "hotbardrift", "sleepcancel"));
        return args;
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        State s = STATES.get(target.getUUID());
        if (s == null) {
            return "not active — apply witchmod:bedrock_moment first";
        }
        ServerLevel level = target.serverLevel();
        if (arg == null) {
            runActiveEvent(target, s, level);
            return "fired a random active bug";
        }
        String key = arg.toLowerCase(java.util.Locale.ROOT);
        // Fling + the passive/hook-driven bugs aren't in the active registry — handled specially.
        switch (key) {
            case "fling": {
                Entity veh = target.getVehicle();
                if (veh == null) {
                    return "Fling is passive — it fires when you MOUNT a ridable (nothing to fling now)";
                }
                double f = Config.BEDROCK_FLING_FORCE.get();
                int axis = target.getRandom().nextInt(3);
                veh.setDeltaMovement(axis == 0 ? f : 0, axis == 1 ? f : 0, axis == 2 ? f : 0);
                veh.hurtMarked = true;
                return "flung your ride";
            }
            case "aimbot": return "Aimbot is passive — it homes skeleton arrows shot near you";
            case "projpause": return "Projectile-Pause is passive — it freezes YOUR projectiles when you fire them";
            case "delay": return "Delay is passive — it delays fall damage when you take it";
            case "ghost": case "ghostblock": return "Ghost Blocks are passive — a block you PLACE has a chance to reject itself";
            case "silentcreeper": return "Silent Creeper is passive/always-on while cursed";
            case "hotbardrift": case "hotbar": return "Hotbar Drift is passive/always-on while cursed";
            case "sleepcancel": return "Sleep Cancel is passive — a chance to be booted out of a bed while sleeping";
            default: break;
        }
        BedrockEvent event = BedrockEvents.byId(key);
        if (event != null) {
            boolean ok = event.run(this, target, s, level);
            return ok ? "forced '" + event.id() + "'" : "'" + event.id() + "' had no valid target/precondition";
        }
        return "unknown sub-event '" + arg + "'. active: "
                + BedrockEvents.ALL.stream().map(BedrockEvent::id).reduce((a, b) -> a + ", " + b).orElse("")
                + " — passive (info): fling, aimbot, pause, delay, ghost, silentcreeper, hotbardrift";
    }

    // --- Sticky TNT (passive) + Bedrock Cooldowns (beneficial) ------------------------------------------

    /** Passive: any lit TNT you're touching CLINGS to you (rides you), so it goes off in your face. */
    void stickTnt(ServerPlayer target, ServerLevel level) {
        for (net.minecraft.world.entity.item.PrimedTnt tnt : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.PrimedTnt.class, target.getBoundingBox().inflate(0.9),
                t -> !t.isPassenger() && t.getVehicle() == null)) {
            tnt.startRiding(target, true); // stuck — force=true because nothing normally rides a player
        }
    }

    /** Debug/force: spawns a lit TNT already stuck to you (the passive version needs live TNT to be near). */
    boolean stickyTntEvent(ServerPlayer target, ServerLevel level) {
        net.minecraft.world.entity.item.PrimedTnt tnt =
                new net.minecraft.world.entity.item.PrimedTnt(level, target.getX(), target.getY(), target.getZ(), target);
        tnt.setFuse(80);
        level.addFreshEntity(tnt);
        tnt.startRiding(target, true);
        return true;
    }

    /**
     * Bedrock Cooldowns (the one BENEFICIAL bug): quarters your weapon swing cooldown for 8-28s — Bedrock has no
     * attack cooldown, so this mimics it. Done as a temporary ATTACK_SPEED ×4 modifier.
     */
    boolean cooldownsEvent(ServerPlayer target, State state) {
        int min = Config.BEDROCK_COOLDOWNS_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.BEDROCK_COOLDOWNS_MAX_TICKS.get());
        state.cooldownsUntil = target.level().getGameTime() + min + target.getRandom().nextInt(max - min);
        applyCooldownBuff(target);
        target.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.4F);
        return true;
    }

    private static void applyCooldownBuff(ServerPlayer target) {
        var attr = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (attr != null && !attr.hasModifier(COOLDOWNS_ID)) {
            attr.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    COOLDOWNS_ID, Config.BEDROCK_COOLDOWNS_ATTACK_SPEED_MULT.get() - 1.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Keeps the cooldowns buff applied for its window and strips it when the window ends. */
    private void tickCooldowns(ServerPlayer target, State state) {
        if (state.cooldownsUntil <= 0) {
            return;
        }
        if (target.level().getGameTime() >= state.cooldownsUntil) {
            state.cooldownsUntil = 0;
            com.oliver.witchmod.data.EffectUtil.removeModifier(target,
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, COOLDOWNS_ID);
        } else {
            applyCooldownBuff(target); // re-assert (transient modifiers drop on reload)
        }
    }

    // --- Active event pool ------------------------------------------------------------------------------

    void runActiveEvent(ServerPlayer target, State state, ServerLevel level) {
        RandomSource r = target.getRandom();
        int t1 = Config.BEDROCK_TIER1_WEIGHT.get();
        int t2 = Config.BEDROCK_TIER2_WEIGHT.get();
        int t3 = Config.BEDROCK_TIER3_WEIGHT.get();
        List<Cand> pool = new ArrayList<>();

        // ── TIER 1 — very common: the mild, everyday sensory jank you'd shrug at ──────────────────────────
        add(pool, t1, BedrockEvents.NIGHTCORE, target, state, level);          // everything higher-pitched
        add(pool, t1, BedrockEvents.SOUND_DELAY, target, state, level);        // sounds replayed a beat late
        add(pool, t1, BedrockEvents.PHANTOM_DUR, target, state, level);        // jittering fake durability bars
        add(pool, t1, BedrockEvents.CHUNK_REJECT, target, state, level);       // render distance slams to 2
        add(pool, t1, BedrockEvents.RUBBERBAND, target, state, level);         // the classic laggy yank-back
        add(pool, t1, BedrockEvents.BLUETOOTH, target, state, level);          // damage withheld then dumped
        add(pool, t1, BedrockEvents.GHOST_ITEM, target, state, level);         // held item renders as a random other item
        add(pool, t1, BedrockEvents.INPUT_LAG, target, state, level);          // movement input applied a beat late
        add(pool, t1, BedrockEvents.VIBRANT, target, state, level);           // saturation-boost "Bedrock is vibrant" shader
        add(pool, t1, BedrockEvents.SPRINT_RESET, target, state, level);       // sprint keeps cutting out

        // ── TIER 2 — medium: the properly disruptive stuff ───────────────────────────────────────────────
        add(pool, t2, BedrockEvents.MITOSIS, target, state, level);           // mobs duplicate
        add(pool, t2, BedrockEvents.BLITZ, target, state, level);             // creepers detonate instantly
        add(pool, t2, BedrockEvents.SERVER_LAG, target, state, level);        // nearby entities freeze
        add(pool, t2, BedrockEvents.POP, target, state, level);              // armour stands/frames break
        add(pool, t2, BedrockEvents.INVENTORY_SHUFFLE, target, state, level); // inventory scrambled
        add(pool, t2, BedrockEvents.HELICOPTER, target, state, level);        // a mob spins up and away
        add(pool, t2, BedrockEvents.TICKSPEED, target, state, level);         // mobs get Speed + Haste
        add(pool, t2, BedrockEvents.MARKETPLACE, target, state, level);       // an ad popup you must close
        add(pool, t2, BedrockEvents.PERSPECTIVE, target, state, level);       // yanked to third-person-front
        add(pool, t2, BedrockEvents.GHOST_PHASE, target, state, level);       // 4–16s: placed/broken blocks revert
        add(pool, t2, BedrockEvents.LANGUAGE_ERROR, target, state, level);    // language swaps to a joke language
        add(pool, t2, BedrockEvents.SPEED_BLITZ, target, state, level);       // freeze, store input, replay at 3x
        add(pool, t2, BedrockEvents.PAUSE, target, state, level);            // nearby entities pause, then high-tick catch-up
        add(pool, t2, BedrockEvents.FLOAT, target, state, level);           // nearby entities lose gravity
        add(pool, t2, BedrockEvents.COOLDOWNS, target, state, level);        // BENEFICIAL: quartered swing cooldown 8-28s

        // ── TIER 3 — rare: the big shocks ────────────────────────────────────────────────────────────────
        add(pool, t3, BedrockEvents.CREEPER_BOAT, target, state, level);      // charged creeper screams in on a boat
        add(pool, t3, BedrockEvents.DROWNING, target, state, level);         // desync "drowning" on dry land
        add(pool, t3, BedrockEvents.FAKE_KICK, target, state, level);        // a believable fake disconnect screen
        add(pool, t3, BedrockEvents.FAKE_BSOD, target, state, level);        // fake blue-screen + "left the game" broadcast
        add(pool, t3, BedrockEvents.HUNGRY, target, state, level);          // right-click eats whatever you hold

        if (runWeighted(pool, r) && !state.discovered) {
            state.discovered = true;
            Curses.BEDROCK_MOMENT.get().markDiscoveredByVictim(target);
        }
    }

    /** Adds a weighted pool candidate that runs a {@link BedrockEvent}. */
    private void add(List<Cand> pool, int weight, BedrockEvent event, ServerPlayer target, State state, ServerLevel level) {
        pool.add(new Cand(weight, () -> event.run(this, target, state, level)));
    }

    private static boolean runWeighted(List<Cand> pool, RandomSource r) {
        while (!pool.isEmpty()) {
            int total = 0;
            for (Cand c : pool) {
                total += c.weight();
            }
            int pick = r.nextInt(Math.max(1, total));
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

    // --- Charged Creeper Boat ---------------------------------------------------------------------------

    boolean creeperBoat(ServerPlayer target, State state, ServerLevel level) {
        RandomSource r = target.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        double d = 12.0;
        double sx = target.getX() + Math.cos(a) * d;
        double sz = target.getZ() + Math.sin(a) * d;
        double sy = target.getY();
        Creeper creeper = EntityType.CREEPER.create(level);
        if (creeper == null) {
            return false;
        }
        Boat boat = new Boat(level, sx, sy, sz);
        creeper.moveTo(sx, sy + 0.2, sz, (float) Math.toDegrees(a), 0);
        level.addFreshEntity(boat);
        level.addFreshEntity(creeper);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(sx, sy, sz);
            level.addFreshEntity(bolt);
            creeper.thunderHit(level, bolt); // charge it
        }
        creeper.startRiding(boat, true);
        creeper.setTarget(target);
        state.creeperBoats.add(boat.getId());
        playToVictim(target, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 1.4F);
        return true;
    }

    void tickCreeperBoats(ServerPlayer target, State state, ServerLevel level) {
        Iterator<Integer> it = state.creeperBoats.iterator();
        double speed = Config.BEDROCK_CREEPER_BOAT_SPEED.get();
        while (it.hasNext()) {
            int id = it.next();
            if (!(level.getEntity(id) instanceof Boat boat) || !boat.isAlive() || boat.getPassengers().isEmpty()) {
                if (level.getEntity(id) instanceof Boat b) {
                    b.getPassengers().forEach(Entity::discard);
                    b.discard();
                }
                it.remove();
                continue;
            }
            Vec3 to = target.position().subtract(boat.position());
            double dist = to.horizontalDistance();
            // Drifted past / lost / timed out — despawn quickly.
            if (dist > 24.0 || boat.tickCount > 220) {
                boat.getPassengers().forEach(Entity::discard);
                boat.discard();
                it.remove();
                continue;
            }
            Vec3 dir = to.normalize();
            boat.setDeltaMovement(dir.x * speed, boat.getDeltaMovement().y, dir.z * speed);
            boat.setYRot((float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI)));
            boat.hurtMarked = true;
        }
    }

    // --- Blitz ------------------------------------------------------------------------------------------

    boolean blitz(ServerPlayer target, State state, ServerLevel level) {
        List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, target.getBoundingBox().inflate(24.0),
                c -> c.isAlive() && !state.blitzCreepers.contains(c.getId()));
        if (creepers.isEmpty()) {
            return false;
        }
        int n = 0;
        for (Creeper c : creepers) {
            if (n++ >= 3) {
                break;
            }
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, Config.BEDROCK_BLITZ_SPEED_LEVEL.get(), false, true));
            c.setTarget(target);
            state.blitzCreepers.add(c.getId());
        }
        playToVictim(target, SoundEvents.CREEPER_PRIMED, 0.8F, 1.6F);
        return true;
    }

    void tickBlitz(ServerPlayer target, State state, ServerLevel level) {
        double det = Config.BEDROCK_BLITZ_DETONATE_DISTANCE.get();
        Iterator<Integer> it = state.blitzCreepers.iterator();
        while (it.hasNext()) {
            int id = it.next();
            if (!(level.getEntity(id) instanceof Creeper c) || !c.isAlive()) {
                it.remove();
                continue;
            }
            if (c.distanceToSqr(target) <= det * det) {
                // No windup — instant detonation.
                level.explode(c, c.getX(), c.getY(), c.getZ(), c.isPowered() ? 6.0F : 3.0F, Level.ExplosionInteraction.MOB);
                c.discard();
                it.remove();
            } else if (c.tickCount > 400) {
                it.remove(); // stop tracking; the Speed wears off on its own
            }
        }
    }

    // --- Mitosis ----------------------------------------------------------------------------------------

    boolean mitosis(ServerPlayer target, ServerLevel level) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(12.0),
                m -> m.isAlive());
        if (mobs.isEmpty() || mobs.size() >= Config.BEDROCK_MITOSIS_NEARBY_CAP.get()) {
            return false;
        }
        RandomSource r = target.getRandom();
        int made = 0;
        for (Mob m : mobs) {
            if (made >= Config.BEDROCK_MITOSIS_MAX.get()) {
                break;
            }
            Entity copy = m.getType().create(level);
            if (copy instanceof Mob cm) {
                cm.moveTo(m.getX() + (r.nextDouble() - 0.5), m.getY(), m.getZ() + (r.nextDouble() - 0.5), m.getYRot(), 0);
                cm.setHealth(cm.getMaxHealth());
                level.addFreshEntity(cm);
                level.sendParticles(ParticleTypes.CLOUD, cm.getX(), cm.getY() + 0.4, cm.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                made++;
            }
        }
        return made > 0;
    }

    // --- Rubberbanding ----------------------------------------------------------------------------------

    boolean rubberband(ServerPlayer target, State state) {
        Vec3 saved = target.position();
        float yaw = target.getYRot();
        float pitch = target.getXRot();
        int delay = Config.BEDROCK_RUBBERBAND_DELAY_TICKS.get();
        int count = Config.BEDROCK_RUBBERBAND_COUNT.get();
        // 55% of the time it's the EXTRA-QUICK variant: twice the yanks, each at a third of the gap.
        if (target.getRandom().nextInt(100) < 55) {
            delay = Math.max(1, delay / 3);
            count *= 2;
        }
        for (int i = 1; i <= count; i++) {
            state.pending.add(new Pending((tgt, lvl) ->
                    // No sound — real rubberbanding is silent; the yank itself is the whole effect.
                    tgt.connection.teleport(saved.x, saved.y, saved.z, yaw, pitch), i * delay));
        }
        return true;
    }

    // --- Server lag -------------------------------------------------------------------------------------

    void serverLag(ServerPlayer target, State state, ServerLevel level) {
        state.laggedIds.clear();
        state.lagPos.clear();
        for (Entity e : level.getEntities(target, target.getBoundingBox().inflate(16.0), e -> !(e instanceof Player) && e.isAlive())) {
            state.laggedIds.add(e.getId());
            state.lagPos.put(e.getId(), e.position());
        }
        state.lagTimer = Config.BEDROCK_SERVERLAG_TICKS.get();
    }

    void tickServerLag(State state, ServerLevel level) {
        if (state.lagTimer <= 0) {
            return;
        }
        for (int id : state.laggedIds) {
            Entity e = level.getEntity(id);
            Vec3 p = state.lagPos.get(id);
            if (e != null && p != null) {
                e.setDeltaMovement(Vec3.ZERO);
                e.setPos(p.x, p.y, p.z);
                e.hurtMarked = true;
            }
        }
        if (--state.lagTimer <= 0) {
            state.laggedIds.clear();
            state.lagPos.clear();
        }
    }

    // --- Pop --------------------------------------------------------------------------------------------

    boolean pop(ServerPlayer target, ServerLevel level) {
        List<Entity> hangs = level.getEntitiesOfClass(Entity.class, target.getBoundingBox().inflate(8.0),
                e -> e.isAlive() && (e instanceof ArmorStand || e instanceof HangingEntity));
        if (hangs.isEmpty()) {
            return false;
        }
        Entity e = hangs.get(target.getRandom().nextInt(hangs.size()));
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.3, e.getZ(), 12, 0.2, 0.2, 0.2, 0.02);
        e.hurt(level.damageSources().generic(), 1000.0F); // breaks + drops
        playToVictim(target, SoundEvents.ITEM_FRAME_BREAK, 0.9F, 1.0F);
        return true;
    }

    // --- Nightcore / Chunk Rejection (client windows) --------------------------------------------------

    void nightcore(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_NIGHTCORE, level.getGameTime() + Config.BEDROCK_NIGHTCORE_TICKS.get());
    }

    void chunkReject(ServerPlayer target, ServerLevel level) {
        int min = Config.BEDROCK_CHUNKREJECT_MIN_TICKS.get();
        int max = Config.BEDROCK_CHUNKREJECT_MAX_TICKS.get();
        int dur = min + target.getRandom().nextInt(Math.max(1, max - min));
        target.setData(WitchModAttachments.BEDROCK_CHUNK_REJECT, level.getGameTime() + dur);
    }

    // --- Inventory shuffle ------------------------------------------------------------------------------

    boolean inventoryShuffle(ServerPlayer target) {
        var inv = target.getInventory();
        RandomSource r = target.getRandom();
        // Fisher-Yates over the main (non-hotbar) slots 9..35.
        for (int i = 35; i > 9; i--) {
            int j = 9 + r.nextInt(i - 9 + 1);
            ItemStack a = inv.getItem(i);
            inv.setItem(i, inv.getItem(j));
            inv.setItem(j, a);
        }
        target.inventoryMenu.broadcastChanges();
        return true;
    }

    // --- Bluetooth damage -------------------------------------------------------------------------------

    void bluetoothEvent(ServerPlayer target, State state, ServerLevel level) {
        state.bluetoothUntil = level.getGameTime() + Config.BEDROCK_BLUETOOTH_TICKS.get();
        state.bluetoothStored = 0;
    }

    void tickBluetoothWindow(ServerPlayer target, State state, ServerLevel level) {
        if (state.bluetoothUntil != 0 && level.getGameTime() >= state.bluetoothUntil) {
            double total = state.bluetoothStored;
            state.bluetoothStored = 0;
            state.bluetoothUntil = 0;
            if (total > 0) {
                state.pending.add(new Pending((tgt, lvl) ->
                        applyGuarded(tgt, lvl.damageSources().generic(), (float) total), Config.BEDROCK_BLUETOOTH_RELEASE_DELAY.get()));
            }
        }
    }

    // --- Drowning desync --------------------------------------------------------------------------------

    boolean drownEvent(ServerPlayer target, State state) {
        state.drowning = true;
        state.drownTick = 0;
        target.setAirSupply(0);
        playToVictim(target, SoundEvents.AMBIENT_UNDERWATER_ENTER, 0.8F, 1.0F);
        return true;
    }

    void tickDrowning(ServerPlayer target, State state) {
        if (!state.drowning) {
            return;
        }
        // Ends the moment you touch water, or after it gives up on its own.
        if (target.isInWater() || ++state.drownTick > Config.BEDROCK_DROWN_TICKS.get()) {
            state.drowning = false;
            state.drownTick = 0;
            target.setAirSupply(target.getMaxAirSupply());
            return;
        }
        target.setAirSupply(0);
        if (state.drownTick % 20 == 0) {
            target.hurt(target.serverLevel().damageSources().drown(), 2.0F);
        }
    }

    /** Called from the death handler — a death clears the fake drowning (it doesn't persist to respawn). */
    public static void onDeath(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null && s.drowning) {
            s.drowning = false;
            s.drownTick = 0;
        }
    }

    // --- Helicopter -------------------------------------------------------------------------------------

    boolean helicopter(ServerPlayer target, State state, ServerLevel level) {
        if (state.heliId != -1) {
            return false;
        }
        List<Mob> targets = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(12.0),
                m -> m.isAlive()
                        && !(m instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
                        && !(m instanceof net.minecraft.world.entity.boss.wither.WitherBoss)); // any non-boss entity
        if (targets.isEmpty()) {
            return false;
        }
        Mob m = targets.get(target.getRandom().nextInt(targets.size()));
        state.heliId = m.getId();
        int min = Config.BEDROCK_HELICOPTER_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.BEDROCK_HELICOPTER_MAX_TICKS.get());
        state.heliTimer = min + target.getRandom().nextInt(max - min);
        state.heliGround = Config.BEDROCK_HELICOPTER_GROUND_TICKS.get();
        m.setNoGravity(true);
        m.setNoAi(true); // otherwise the mob's own AI rewrites its rotation each tick and it never spins
        return true;
    }

    void tickHelicopter(State state, ServerLevel level) {
        if (state.heliId == -1) {
            return;
        }
        if (!(level.getEntity(state.heliId) instanceof Mob m) || !m.isAlive()) {
            state.heliId = -1;
            return;
        }
        m.setNoGravity(true);
        m.setDeltaMovement(0, 0, 0);
        // Spins IN PLACE on the ground first (the sudden whip-up), THEN starts to rise.
        boolean rising = state.heliGround <= 0;
        if (rising) {
            m.setPos(m.getX(), m.getY() + 0.06, m.getZ());
        } else {
            state.heliGround--;
        }
        float prev = m.getYRot();
        float yaw = prev + (float) Config.BEDROCK_HELICOPTER_SPIN.get().doubleValue();
        m.yRotO = prev; // interpolate FORWARD on the client instead of wiggling across the ±180 wrap
        m.setYRot(yaw);
        m.yBodyRotO = prev;
        m.setYBodyRot(yaw);
        m.yHeadRotO = prev;
        m.setYHeadRot(yaw);
        level.sendParticles(ParticleTypes.CRIT, m.getX(), m.getY() + 0.5, m.getZ(), 3, 0.35, 0.1, 0.35, 0.08);
        if (rising && --state.heliTimer <= 0) {
            m.setNoGravity(false);
            m.setNoAi(false);
            m.setDeltaMovement(0, Config.BEDROCK_HELICOPTER_LAUNCH.get(), 0);
            m.hurtMarked = true;
            state.heliId = -1;
        }
    }

    // --- Tickspeed --------------------------------------------------------------------------------------

    boolean tickspeed(ServerPlayer target, State state, ServerLevel level) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(12.0), Mob::isAlive);
        if (mobs.isEmpty()) {
            return false;
        }
        // Not a Speed potion — the entities are literally TICKED extra times each server tick, so they move,
        // path and animate at high tickspeed (the "server lag / everything sped up" clip). No sound.
        state.tickspeedIds.clear();
        for (Mob m : mobs) {
            state.tickspeedIds.add(m.getId());
        }
        state.tickspeedTimer = Config.BEDROCK_TICKSPEED_TICKS.get();
        state.tickspeedMult = Math.max(2, Config.BEDROCK_TICKSPEED_LEVEL.get());
        return true;
    }

    /** Tickspeed: run each affected entity's tick the extra times per server tick, for the window. */
    void tickTickspeed(State state, ServerLevel level) {
        if (state.tickspeedTimer <= 0) {
            return;
        }
        for (int id : state.tickspeedIds) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                for (int i = 1; i < state.tickspeedMult; i++) { // vanilla ticks it once already; add the rest
                    e.tick();
                }
            }
        }
        if (--state.tickspeedTimer <= 0) {
            state.tickspeedIds.clear();
        }
    }

    // --- Aimbot (passive: skeleton arrows near you) ----------------------------------------------------

    void tickAimbot(ServerPlayer target, State state, ServerLevel level) {
        double rad = Config.BEDROCK_AIMBOT_RADIUS.get();
        double homing = Config.BEDROCK_AIMBOT_HOMING.get();
        double mult = Config.BEDROCK_AIMBOT_VELOCITY_MULT.get();
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, target.getBoundingBox().inflate(rad),
                ar -> ar.isAlive() && ar.getOwner() instanceof AbstractSkeleton)) {
            Vec3 vel = arrow.getDeltaMovement();
            if (state.boostedArrows.add(arrow.getId())) {
                vel = vel.scale(mult);
            }
            Vec3 toYou = target.getEyePosition().add(0, -0.2, 0).subtract(arrow.position());
            if (toYou.lengthSqr() < 1.0E-4) {
                continue;
            }
            double speed = vel.length();
            Vec3 dir = vel.lengthSqr() < 1.0E-4 ? toYou.normalize()
                    : vel.normalize().scale(1 - homing).add(toYou.normalize().scale(homing)).normalize();
            arrow.setDeltaMovement(dir.scale(speed));
            arrow.hasImpulse = true;
        }
    }

    // --- Pause (passive: your own projectiles) --------------------------------------------------------

    void tickPause(ServerPlayer target, State state, ServerLevel level) {
        RandomSource r = target.getRandom();
        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, target.getBoundingBox().inflate(20.0),
                p -> p.isAlive() && p.getOwner() == target)) {
            if (!state.seenOwnProjectiles.add(proj.getId())) {
                continue;
            }
            if (proj.getDeltaMovement().lengthSqr() < 0.01 || r.nextFloat() >= Config.BEDROCK_PAUSE_CHANCE.get()) {
                continue;
            }
            Vec3 stored = proj.getDeltaMovement();
            boolean gravity = !proj.isNoGravity();
            proj.setDeltaMovement(Vec3.ZERO);
            proj.setNoGravity(true);
            proj.hasImpulse = true;
            int freeze = Config.BEDROCK_PAUSE_MIN_TICKS.get()
                    + r.nextInt(Math.max(1, Config.BEDROCK_PAUSE_MAX_TICKS.get() - Config.BEDROCK_PAUSE_MIN_TICKS.get()));
            int projId = proj.getId();
            state.pending.add(new Pending((tgt, lvl) -> {
                if (lvl.getEntity(projId) instanceof Projectile p2 && p2.isAlive()) {
                    p2.setDeltaMovement(stored);
                    p2.setNoGravity(!gravity); // restore its original gravity state
                    p2.hasImpulse = true;
                }
            }, freeze));
        }
    }

    // --- Fling (passive: mounting) ---------------------------------------------------------------------

    void tickFling(ServerPlayer target, State state) {
        Entity vehicle = target.getVehicle();
        if (vehicle == null) {
            state.lastVehicleId = -1;
            return;
        }
        if (vehicle.getId() == state.lastVehicleId) {
            return;
        }
        state.lastVehicleId = vehicle.getId();
        RandomSource r = target.getRandom();
        if (r.nextFloat() >= Config.BEDROCK_FLING_CHANCE.get()) {
            return;
        }
        // Physics engine "failing": catapult in a RANDOM 3D direction (with enough lift to clear the ground),
        // not the old axis-aligned slide that only worked launching straight up.
        double f = Config.BEDROCK_FLING_FORCE.get();
        double az = r.nextDouble() * Math.PI * 2.0;
        double horiz = 0.6 + r.nextDouble() * 0.9;       // strong sideways throw
        double up = 0.7 + r.nextDouble() * 0.9;          // always some up, so it actually launches
        Vec3 v = new Vec3(Math.cos(az) * horiz, up, Math.sin(az) * horiz).scale(f);
        vehicle.setDeltaMovement(v);
        vehicle.hurtMarked = true;
    }

    // --- Damage hook (static, called from the event handler) -------------------------------------------

    /** Bluetooth (whole window) + Delay (fall damage). Returns true if it consumed the damage. */
    public static boolean onIncomingDamage(ServerPlayer player, LivingIncomingDamageEvent event) {
        if (APPLYING.contains(player.getUUID())) {
            return false; // our own re-applied hit — let it through
        }
        State s = STATES.get(player.getUUID());
        if (s == null) {
            return false;
        }
        float amount = event.getAmount();
        if (amount <= 0) {
            return false;
        }
        // Bluetooth: everything during the window is withheld and stored.
        if (s.bluetoothUntil != 0) {
            s.bluetoothStored += amount;
            event.setAmount(0.0F);
            return true;
        }
        // Delay: fall damage lands late.
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            event.setAmount(0.0F);
            int min = Config.BEDROCK_DELAY_FALL_MIN_TICKS.get();
            int max = Math.max(min + 1, Config.BEDROCK_DELAY_FALL_MAX_TICKS.get());
            int delay = min + player.getRandom().nextInt(max - min);
            s.pending.add(new Pending((tgt, lvl) ->
                    applyGuarded(tgt, lvl.damageSources().fall(), amount), delay));
            return true;
        }
        return false;
    }

    private static void applyGuarded(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        APPLYING.add(player.getUUID());
        player.hurt(source, amount);
        APPLYING.remove(player.getUUID());
    }

    // --- Client-window bugs + Ghost Blocks -------------------------------------------------------------

    void soundDelay(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_SOUND_DELAY, level.getGameTime() + Config.BEDROCK_SOUND_DELAY_TICKS.get());
    }

    void phantomDur(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_PHANTOM_DUR, level.getGameTime() + Config.BEDROCK_PHANTOM_DUR_TICKS.get());
    }

    void perspectiveFlip(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_PERSPECTIVE, level.getGameTime() + Config.BEDROCK_PERSPECTIVE_TICKS.get());
    }

    // --- Newer bugs (client windows / passive phases) ---------------------------------------------------

    void ghostItem(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_GHOST_ITEM, level.getGameTime() + Config.BEDROCK_GHOST_ITEM_TICKS.get());
    }

    void inputLag(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_INPUT_LAG, level.getGameTime() + Config.BEDROCK_INPUT_LAG_TICKS.get());
    }

    /** Vibrant: a client saturation-boost post shader for the window ("Bedrock is more vibrant"). */
    void vibrant(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_TEXTURE_FLICKER, level.getGameTime() + Config.BEDROCK_TEXTURE_FLICKER_TICKS.get());
    }

    void hungry(ServerPlayer target, ServerLevel level) {
        int min = Config.BEDROCK_HUNGRY_MIN_TICKS.get();
        int max = Math.max(min, Config.BEDROCK_HUNGRY_MAX_TICKS.get());
        target.setData(WitchModAttachments.BEDROCK_HUNGRY, level.getGameTime() + min + target.getRandom().nextInt(max - min + 1));
    }

    void airSwim(ServerPlayer target, ServerLevel level) {
        int min = Config.BEDROCK_AIR_SWIM_MIN_TICKS.get();
        int max = Math.max(min, Config.BEDROCK_AIR_SWIM_MAX_TICKS.get());
        target.setData(WitchModAttachments.BEDROCK_AIR_SWIM, level.getGameTime() + min + target.getRandom().nextInt(max - min + 1));
    }

    /** Pause: nearby non-boss entities have AI off + are frozen for 0.3–5s, then a high-tick catch-up burst. */
    boolean pauseEntities(ServerPlayer target, State state, ServerLevel level) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(16.0),
                m -> m.isAlive()
                        && !(m instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
                        && !(m instanceof net.minecraft.world.entity.boss.wither.WitherBoss));
        if (mobs.isEmpty()) {
            return false;
        }
        state.pausedIds.clear();
        for (Mob m : mobs) {
            m.setNoAi(true);
            m.setDeltaMovement(Vec3.ZERO);
            state.pausedIds.add(m.getId());
        }
        int min = Config.BEDROCK_PAUSE_MOB_MIN_TICKS.get();
        int max = Math.max(min, Config.BEDROCK_PAUSE_MOB_MAX_TICKS.get());
        state.pauseTimer = min + target.getRandom().nextInt(max - min + 1);
        return true;
    }

    void tickPauseEntities(ServerPlayer target, State state, ServerLevel level) {
        // Paused phase: hold everything dead-still.
        if (state.pauseTimer > 0) {
            for (int id : state.pausedIds) {
                if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                    m.setDeltaMovement(Vec3.ZERO);
                    m.hurtMarked = true;
                }
            }
            if (--state.pauseTimer <= 0) {
                releasePause(target, state);
            }
        }
        // Catch-up phase: they tick MUCH faster than tickspeed for a bit, lurching to catch up.
        if (state.catchupTimer > 0) {
            for (int id : state.catchupIds) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) {
                    for (int i = 1; i < state.catchupMult; i++) {
                        e.tick();
                    }
                }
            }
            if (--state.catchupTimer <= 0) {
                state.catchupIds.clear();
            }
        }
    }

    /** Un-pauses the entities and kicks off the high-tick catch-up burst. */
    private void releasePause(ServerPlayer target, State state) {
        for (int id : state.pausedIds) {
            if (target.serverLevel().getEntity(id) instanceof Mob m) {
                m.setNoAi(false);
            }
        }
        state.catchupIds.clear();
        state.catchupIds.addAll(state.pausedIds);
        state.pausedIds.clear();
        state.pauseTimer = 0;
        int min = Config.BEDROCK_PAUSE_CATCHUP_MIN_TICKS.get();
        int max = Math.max(min, Config.BEDROCK_PAUSE_CATCHUP_MAX_TICKS.get());
        state.catchupTimer = min + target.getRandom().nextInt(max - min + 1);
        state.catchupMult = Config.BEDROCK_PAUSE_CATCHUP_MULT.get();
    }

    /** Force-unpause: if a PAUSED entity is hit, its curse's whole pause ends early (→ the catch-up burst). */
    public static void onEntityHurt(Entity victim) {
        for (var entry : STATES.entrySet()) {
            State s = entry.getValue();
            if (s.pauseTimer > 0 && s.pausedIds.contains(victim.getId())
                    && victim.level().getServer() != null) {
                ServerPlayer owner = victim.level().getServer().getPlayerList().getPlayer(entry.getKey());
                if (owner != null) {
                    ((CurseBedrockMoment) Curses.BEDROCK_MOMENT.get()).releasePause(owner, s);
                }
                return;
            }
        }
    }

    /** Float: nearby entities lose gravity (chance each) but keep pathfinding — they drift toward you. */
    boolean floatEvent(ServerPlayer target, State state, ServerLevel level) {
        List<Entity> nearby = level.getEntitiesOfClass(Entity.class, target.getBoundingBox().inflate(16.0),
                e -> e != target && e.isAlive() && !e.isNoGravity());
        if (nearby.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (Entity e : nearby) {
            if (target.getRandom().nextDouble() < Config.BEDROCK_FLOAT_CHANCE.get()) {
                e.setNoGravity(true);
                state.floatIds.add(e.getId());
                any = true;
            }
        }
        if (any) {
            state.floatTimer = Config.BEDROCK_FLOAT_TICKS.get();
        }
        return any;
    }

    void tickFloat(State state, ServerLevel level) {
        if (state.floatTimer <= 0) {
            return;
        }
        if (--state.floatTimer <= 0) {
            for (int id : state.floatIds) {
                if (level.getEntity(id) instanceof Entity e) {
                    e.setNoGravity(false);
                }
            }
            state.floatIds.clear();
        }
    }

    /** Hungry: consume one of the held stack (called from the client's C2S when its eat animation finishes). */
    public static void onHungryEat(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (player.level().getGameTime() >= player.getData(WitchModAttachments.BEDROCK_HUNGRY)) {
            return; // window closed — ignore stale packets
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        stack.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.0F);
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
        }
    }

    void sprintReset(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_SPRINT_RESET, level.getGameTime() + Config.BEDROCK_SPRINT_RESET_TICKS.get());
    }

    void ghostPhase(ServerPlayer target, State state, ServerLevel level) {
        int min = Config.BEDROCK_GHOST_PHASE_MIN_TICKS.get();
        int max = Math.max(min, Config.BEDROCK_GHOST_PHASE_MAX_TICKS.get());
        state.ghostPhaseUntil = level.getGameTime() + min + target.getRandom().nextInt(max - min + 1);
    }

    void languageError(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_LANGUAGE, level.getGameTime() + Config.BEDROCK_LANGUAGE_TICKS.get());
    }

    void speedBlitz(ServerPlayer target, ServerLevel level) {
        long now = level.getGameTime();
        int freeze = Config.BEDROCK_SPEEDBLITZ_FREEZE_TICKS.get();
        target.setData(WitchModAttachments.BEDROCK_SPEEDBLITZ_FREEZE, now + freeze);
        // The client ends the 3x replay once its buffer empties; this is just a hard safety cap.
        target.setData(WitchModAttachments.BEDROCK_SPEEDBLITZ_END, now + freeze + freeze + 40L);
    }

    void fakeKick(ServerPlayer target, ServerLevel level) {
        // Bump the nonce → the client shows the (fake) disconnect screen.
        target.setData(WitchModAttachments.BEDROCK_FAKE_KICK, level.getGameTime());
    }

    void fakeBsod(ServerPlayer target, ServerLevel level) {
        target.setData(WitchModAttachments.BEDROCK_BSOD, level.getGameTime() + Config.BEDROCK_BSOD_TICKS.get());
        // Broadcast the exact vanilla "left the game" message to EVERYONE, so it looks like they crashed out.
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component
                .translatable("multiplayer.player.left", target.getDisplayName())
                .withStyle(net.minecraft.ChatFormatting.YELLOW);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p != target) {
                p.sendSystemMessage(msg);
            }
        }
    }

    /** True while a Ghost Block Phase is running for this player (blocks placed/broken revert). */
    public static boolean inGhostPhase(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && player.level().getGameTime() < s.ghostPhaseUntil;
    }

    /** Ghost Block Phase break: cancel the break so the block re-appears (client predicted removal → desync). */
    public static boolean onBlockBroken(ServerPlayer player) {
        return inGhostPhase(player);
    }

    /** Food Reg: chance an eaten food gives NO hunger. Returns true if it negated the gain. */
    public static boolean onFoodEaten(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (player.getRandom().nextInt(100) >= Config.BEDROCK_FOOD_REG_CHANCE.get()) {
            return false;
        }
        net.minecraft.world.food.FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food == null) {
            return false;
        }
        player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - food.nutrition()));
        return true;
    }

    /** Hit Reg: chance a melee hit is invalidated (whiffs). Returns true to cancel + play the empty-swing sound. */
    public static boolean onAttack(ServerPlayer player) {
        if (player.getRandom().nextInt(100) >= Config.BEDROCK_HIT_REG_CHANCE.get()) {
            return false;
        }
        playToVictim(player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.0F);
        return true;
    }

    void marketplace(ServerPlayer target, ServerLevel level) {
        // A changing, never-zero nonce the client watches to pop the ad screen (also seeds which ad shows).
        long nonce = level.getGameTime() * 2862933555777941757L + target.getRandom().nextLong() | 1L;
        target.setData(WitchModAttachments.BEDROCK_MARKETPLACE, nonce);
    }


    /** Ghost Blocks (passive): a block you place briefly appears, then rejects itself. Called from the place hook. */
    public static void onBlockPlaced(ServerPlayer player, BlockPos pos) {
        State s = STATES.get(player.getUUID());
        if (s == null) {
            return;
        }
        // In a Ghost Block Phase every placed block is a ghost; otherwise it's the low passive chance.
        boolean phase = player.level().getGameTime() < s.ghostPhaseUntil;
        if (!phase && player.getRandom().nextFloat() >= Config.BEDROCK_GHOST_BLOCK_CHANCE.get()) {
            return;
        }
        BlockPos at = pos.immutable();
        s.pending.add(new Pending((tgt, lvl) -> {
            if (!lvl.getBlockState(at).isAir()) {
                lvl.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                lvl.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
                playToVictim(tgt, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6F, 1.4F);
            }
        }, Config.BEDROCK_GHOST_BLOCK_DELAY_TICKS.get()));
    }

    // --- Plumbing --------------------------------------------------------------------------------------

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
                    // stale reference — drop it
                }
                it.remove();
            }
        }
    }

    private static int eventGap(ServerPlayer target) {
        int min = Config.BEDROCK_EVENT_MIN_TICKS.get();
        int max = Config.BEDROCK_EVENT_MAX_TICKS.get();
        return min + target.getRandom().nextInt(Math.max(1, max - min));
    }

    private static void playToVictim(ServerPlayer target, SoundEvent sound, float volume, float pitch) {
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(sound), SoundSource.MASTER,
                target.getX(), target.getY(), target.getZ(), volume, pitch, target.getRandom().nextLong()));
    }
}
