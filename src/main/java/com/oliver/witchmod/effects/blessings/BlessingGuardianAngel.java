package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.data.WitchModMobEffects;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.effects.curses.CurseSolicitor;
import com.oliver.witchmod.entities.BodyguardEntity;
import com.oliver.witchmod.synergy.Synergies;

/**
 * A statement blessing (sacrificial item ELYTRA): a managed guardian {@link Allay} that looks after you with a
 * broad, layered behaviour set — REACTIVE saves always take priority, then playful INTERACTIONS driven by what
 * you're doing, then a slow trickle of beneficial AMBIENT gifts. It's {@code noAi} and driven entirely from
 * {@link #onTick}; it CAN be killed and returns after {@code guardianRespawnTicks}.
 *
 * <p>Reactive (any time): put out your fire, catch you from a fatal fall, keep you breathing underwater, purge
 * phantoms, lift a random curse, hover your fishing bobber, fetch your missed arrows, and Haste you through a
 * hard dig. Interactions (movement/mood): watch you sleep, perch when you idle, hang low + go quiet while you
 * sneak, dance if you spam-sneak, get curious about flowers/bees/villagers, panic when you're hit. Ambient
 * gifts: XP bottles, a torch in the dark, marking a nearby entity as glowing, mending your tools, tending pets.
 *
 * <p>Everything discrete is debug-forcible: {@code /bewitch debug force witchmod:guardian_angel @s <event>}.
 */
public final class BlessingGuardianAngel extends Effect {
    /** movement/animation mode — computed each tick, highest applicable wins (see {@link #computeMode}). */
    private enum Mode { FOLLOW, FLYTO, CARRY, KIDNAP, LIFT, BLINDING, PINGPONG, HEAL, GATHER, FIREWORKS,
        BED_GUARD, FRANTIC, FISHING, MINING, SLEEP, DANCE, STEALTH, CURIOSITY, BOREDOM }

    /** windup for an on-you gift (buffs/xp/durability): the allay visibly gathers first, so it never feels random. */
    private static final int GIFT_WINDUP = 44;

    /** soft golden halo dust. */
    private static final DustParticleOptions HALO = new DustParticleOptions(new Vector3f(1.0F, 0.92F, 0.55F), 0.7F);
    /** subtle pink self-heal dust. */
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0F, 0.65F, 0.85F), 0.7F);
    /** bright blue zap dust. */
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.25F, 0.6F, 1.0F), 1.0F);

    /** minimum horizontal clearance the allay keeps from you, so it never sits inside/on your body. */
    private static final double ROOM = 1.9;

    /** push a goal outward so it stays at least {@code room} blocks horizontally from the player (keeps your space). */
    private static Vec3 keepClear(Vec3 goal, ServerPlayer owner, double room) {
        double dx = goal.x - owner.getX();
        double dz = goal.z - owner.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h >= room) {
            return goal;
        }
        if (h < 1.0e-4) {
            dx = 1;
            dz = 0;
            h = 1;
        }
        double f = room / h;
        return new Vec3(owner.getX() + dx * f, goal.y, owner.getZ() + dz * f);
    }

    /** beneficial effects the Buffs gift draws 1-3 from. */
    private static final List<Holder<MobEffect>> GOOD_EFFECTS = List.of(
            MobEffects.MOVEMENT_SPEED, MobEffects.JUMP, MobEffects.REGENERATION, MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING,
            MobEffects.NIGHT_VISION, MobEffects.ABSORPTION, MobEffects.DIG_SPEED, MobEffects.LUCK,
            MobEffects.HEALTH_BOOST, MobEffects.SATURATION);

    private static final class State {
        UUID allayId;
        long respawnAt = -1;
        float orbit;
        Vec3 lastPos;
        long idleSince;
        Mode mode = Mode.FOLLOW;
        double followSide = 1.0;   // which side it prefers to hover (flips occasionally)
        long followFlip;

        // a short "fly to something" animation for an instant reactive/ambient act
        @Nullable UUID flyTarget;
        @Nullable Vec3 flyPoint;
        boolean flyOrbit;          // true = circle the owner (a beneficial act on YOU), false = fly to the point
        long flyUntil;

        // catch: physically carry the owner to a safe landing
        boolean carrying;
        @Nullable Vec3 carryDest;
        long carryUntil;

        // lift: fly at an attacker and levitate them (the non-desperate deterrent; kidnap's healthier sibling)
        @Nullable UUID liftTarget;
        long liftUntil;

        // kidnap: fly to the attacker, THEN haul them up and drop them (into lava if handy)
        @Nullable UUID kidnapTarget;
        boolean kidnapGrabbed;       // false = still flying toward them; true = carrying
        @Nullable Vec3 kidnapDest;   // null = straight up over the grab point
        double kidnapCeiling;        // y to release at
        boolean kidnapIntoLava;
        long kidnapUntil;

        long allayLastHurt;          // last time the allay took a hit (for its own fast regen)
        @Nullable UUID guidingTarget; // the highlighted highest-health foe (bonus damage)
        @Nullable BlockPos lastOrePos; // last valuable ore it celebrated (dedupe)
        @Nullable BlockPos pendingTorch; // a torch spot the allay is flying to before placing
        long pendingTorchUntil;
        // blinding light: charge, launch, impact
        @Nullable UUID blindingTarget;
        @Nullable Vec3 blindingSpot;  // where it freezes during the 3s windup
        long blindingChargeEnd;
        boolean blindingLaunched;

        long franticUntil;
        long healBlockedUntil;       // a hit stops the healing star for a moment
        long lastHurt;               // last time you took a hit (for the out-of-combat aura)
        long combatUntil;            // you're actively fighting → the guardian will zap enemies
        long pingpongUntil;          // knockback pinball while surrounded
        long fireworksUntil;         // celebration after a clean kill
        long miningWatchUntil;
        @Nullable BlockPos watchedBlock;
        int recentBreaks;
        long breakWindowStart;

        long danceUntil;
        boolean wasCrouching;
        long lastCrouchToggle;
        int crouchToggles;
        long crouchSince;

        @Nullable UUID curiosityTarget;
        @Nullable Vec3 curiosityPoint;
        long curiosityUntil;

        long nextAmbient;
        int pendingGift;             // a scheduled on-you gift building up: 1=buffs 2=xp 3=durability
        long pendingGiftAt;
        long solicitorStrikeAt = -1; // angel's grudge: when the hidden irritation timer fires on the solicitor's trader
        final Map<String, Long> cd = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public BlessingGuardianAngel() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 45, () -> Items.GOLDEN_APPLE);
    }

    // ---- Lifecycle -----------------------------------------------------------------------------------------

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        State s = STATES.computeIfAbsent(target.getUUID(), k -> new State());
        s.nextAmbient = target.level().getGameTime() + eventGap(target.getRandom());
        summon(target, s);
        // arrival flourish: a rising golden spiral gathers around you as your guardian descends.
        if (target.level() instanceof ServerLevel level) {
            for (int i = 0; i < 48; i++) {
                double a = i * 0.5;
                double h = i / 48.0 * 2.6;
                double r = 1.4 - i / 48.0 * 0.9;
                level.sendParticles(HALO, target.getX() + Math.cos(a) * r, target.getY() + h, target.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.2, target.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            level.playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        State s = STATES.remove(target.getUUID());
        if (s != null && target.level() instanceof ServerLevel level) {
            Allay allay = allay(level, s);
            // farewell flourish: the guardian gathers into light and winks out.
            Vec3 at = allay != null ? allay.position() : target.position().add(0, 1.5, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.3, 0.3, 0.3, 0.08);
            level.sendParticles(HALO, at.x, at.y, at.z, 30, 0.25, 0.25, 0.25, 0.06);
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.5F);
            if (allay != null) {
                allay.discard();
            }
            if (s.kidnapTarget != null) {
                endKidnap(level, s);
            }
        }
        target.setData(WitchModAttachments.GUARDIAN_GIFT_USED, 0); // a fresh cast gets a fresh gift
    }

    private static long eventGap(RandomSource rng) {
        int min = Config.GUARDIAN_EVENT_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.GUARDIAN_EVENT_MAX_TICKS.get());
        return min + rng.nextInt(max - min);
    }

    private void summon(ServerPlayer owner, State s) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        // reuse an existing guardian for this owner (e.g. after a relog / re-login) instead of spawning a
        // duplicate, and clean up any extras that accumulated. This is the fix for orphaned AI-less allays.
        Allay existing = adoptExisting(level, owner);
        if (existing != null) {
            s.allayId = existing.getUUID();
            return;
        }
        Allay allay = EntityType.ALLAY.create(level);
        if (allay == null) {
            return;
        }
        Vec3 spawn = owner.position().add(0, 2.0, 0);
        allay.moveTo(spawn.x, spawn.y, spawn.z, owner.getYRot(), 0);
        prep(allay, owner);
        level.addFreshEntity(allay);
        s.allayId = allay.getUUID();
        level.playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** stamp the managed-guardian flags/tags onto an allay. */
    private static void prep(Allay allay, ServerPlayer owner) {
        allay.setNoAi(true);
        allay.setNoGravity(true);
        allay.noPhysics = true; // driven by setPos — passes through walls, never collides with/shoves you
        allay.setPersistenceRequired();
        var maxHp = allay.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(Config.GUARDIAN_ALLAY_HEALTH.get());
            if (allay.getHealth() > allay.getMaxHealth()) {
                allay.setHealth(allay.getMaxHealth());
            }
        }
        if (!allay.getTags().contains("witchmod_guardian")) {
            allay.addTag("witchmod_guardian");
        }
        String ownerTag = "guardian_" + owner.getUUID();
        if (!allay.getTags().contains(ownerTag)) {
            allay.addTag(ownerTag);
        }
    }

    /** find a still-loaded guardian belonging to {@code owner}; adopt the first, discard any duplicates. */
    @Nullable
    private static Allay adoptExisting(ServerLevel level, ServerPlayer owner) {
        String ownerTag = "guardian_" + owner.getUUID();
        List<Allay> owned = level.getEntitiesOfClass(Allay.class, owner.getBoundingBox().inflate(160.0),
                a -> a.isAlive() && a.getTags().contains(ownerTag));
        if (owned.isEmpty()) {
            return null;
        }
        Allay keep = owned.get(0);
        prep(keep, owner);
        for (int i = 1; i < owned.size(); i++) {
            owned.get(i).discard(); // clear out any orphans left by earlier relogs
        }
        return keep;
    }

    @Nullable
    private static Allay allay(ServerLevel level, State s) {
        if (s.allayId == null) {
            return null;
        }
        return level.getEntity(s.allayId) instanceof Allay a && a.isAlive() ? a : null;
    }

    private static boolean ready(State s, String key, long now, long cooldown) {
        long last = s.cd.getOrDefault(key, Long.MIN_VALUE / 2);
        if (now - last >= cooldown) {
            s.cd.put(key, now);
            return true;
        }
        return false;
    }

    /** fly to another entity (an act ON something else — mark, phantom, pet…). */
    private static void flyTo(State s, Entity subject, long now, int ticks) {
        s.flyTarget = subject.getUUID();
        s.flyPoint = null;
        s.flyOrbit = false;
        s.flyUntil = now + ticks;
    }

    /** fly to a world point. */
    private static void flyTo(State s, Vec3 point, long now, int ticks) {
        s.flyTarget = null;
        s.flyPoint = point;
        s.flyOrbit = false;
        s.flyUntil = now + ticks;
    }

    /** A beneficial act on the OWNER — circle them for emphasis (buffs/purge/mend/xp…). */
    private static void flyOrbitOwner(State s, long now, int ticks) {
        s.flyTarget = null;
        s.flyPoint = null;
        s.flyOrbit = true;
        s.flyUntil = now + ticks;
    }

    // ---- Tick ----------------------------------------------------------------------------------------------

    @Override
    public void onTick(ServerPlayer owner, int ticksRemaining) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        State s = STATES.computeIfAbsent(owner.getUUID(), k -> new State());
        long now = level.getGameTime();

        if (s.respawnAt >= 0) {
            if (now >= s.respawnAt) {
                s.respawnAt = -1;
                summon(owner, s);
            }
            return;
        }
        Allay allay = allay(level, s);
        if (allay == null) {
            if (now % 40 == 0) {
                summon(owner, s);
            }
            return;
        }
        allay.noPhysics = true;
        // periodically discard any DUPLICATE guardians of ours (an orphan whose chunk loaded late after a relog).
        if ((now + owner.getId()) % 100 == 0) {
            String ownerTag = "guardian_" + owner.getUUID();
            for (Allay a : level.getEntitiesOfClass(Allay.class, owner.getBoundingBox().inflate(160.0),
                    a -> a.isAlive() && a.getTags().contains(ownerTag) && !a.getUUID().equals(s.allayId))) {
                a.discard();
            }
        }

        allayRegen(allay, level, s, now);

        // grace aura: a steady, always-on benefit so the blessing is felt even when nothing else is happening.
        graceAura(owner, s, now);
        // guiding light: in a crowd, highlight the toughest foe (you deal bonus damage to it).
        if ((now + owner.getId()) % 20 == 0) {
            refreshGuidingLight(owner, level, s);
        }
        // light-keeper: fly a torch out to a dark spot and place it there (not instant).
        lightKeeperTick(owner, level, allay, s, now);
        // ore highlight: fly to a valuable ore nearby and set off fireworks.
        if ((now + owner.getId()) % 30 == 0) {
            oreHighlight(owner, level, s, now);
        }

        // blinding light charge/launch takes over the allay until it strikes (driveBlinding moves it).
        if (s.blindingTarget != null && driveBlinding(owner, level, allay, s, now)) {
            s.mode = Mode.BLINDING;
            allay.setData(WitchModAttachments.GUARDIAN_RENDER, renderKind(Mode.BLINDING));
            return;
        }

        // lift: fly at an attacker and levitate them, then done (driveLift moves the allay).
        if (s.liftTarget != null && driveLift(owner, level, allay, s, now)) {
            s.mode = Mode.LIFT;
            allay.setData(WitchModAttachments.GUARDIAN_RENDER, renderKind(Mode.LIFT));
            return;
        }

        // kidnapping an attacker takes over the allay entirely until they're dropped (driveKidnap moves it).
        if (s.kidnapTarget != null && driveKidnap(owner, level, allay, s, now)) {
            s.mode = Mode.KIDNAP;
            allay.setData(WitchModAttachments.GUARDIAN_RENDER, renderKind(Mode.KIDNAP));
            return;
        }

        // carrying you to safety takes over everything until it's set you down.
        if (s.carrying) {
            boolean arrived = s.carryDest != null && allay.position().distanceToSqr(s.carryDest.x, s.carryDest.y + 1.2, s.carryDest.z) < 2.0;
            if (!owner.isPassenger() || now >= s.carryUntil || arrived) {
                endCarry(owner, level, s);
            } else {
                s.mode = Mode.CARRY;
                moveAndTrail(owner, level, allay, s, now);
                return;
            }
        }

        trackIdleAndCrouch(owner, s, now);

        // 1) REACTIVE — always allowed, highest priority.
        reactiveTick(owner, level, s, now);

        // 2) INTERACTIONS — resolve the movement/mood mode from what you're doing.
        s.mode = computeMode(owner, s, now);

        // 3) AMBIENT gifts — a slow trickle, hushed while you sneak or sleep.
        if (s.mode != Mode.STEALTH && s.mode != Mode.SLEEP && s.pendingGift == 0 && now >= s.flyUntil && now >= s.nextAmbient) {
            s.nextAmbient = now + eventGap(owner.getRandom());
            ambientTick(owner, level, s, now);
        }
        // A gathered on-you gift lands once its windup completes.
        if (s.mode == Mode.GATHER && now >= s.pendingGiftAt) {
            executeGift(owner, level, s, now);
        }

        // per-mode continuous effects (heal / pinball / gather / fireworks / bed defence).
        driveModeEffects(owner, level, allay, s, now);
        moveAndTrail(owner, level, allay, s, now);
    }

    /** continuous effects for the "active" movement modes, applied each tick they're in force. */
    private static void driveModeEffects(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        switch (s.mode) {
            case GATHER -> {
                // light streams from the allay to you, thickening as the gift charges — a clear "here it comes".
                double frac = Mth.clamp((s.pendingGiftAt - now) / (double) GIFT_WINDUP, 0.0, 1.0);
                int n = 1 + (int) ((1.0 - frac) * 3);
                Vec3 a = allay.position();
                Vec3 to = owner.position().add(0, 1.0, 0).subtract(a).normalize().scale(0.25);
                level.sendParticles(HALO, a.x, a.y, a.z, n, 0.05, 0.05, 0.05, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, a.x, a.y, a.z, 0, to.x, to.y, to.z, 0.4);
            }
            case HEAL -> {
                float rate = (float) (double) Config.GUARDIAN_HEAL_RATE.get() / 20.0F;
                owner.heal(rate);
                if (now % 3 == 0) {
                    level.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.2, owner.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
                }
            }
            case PINGPONG -> {
                double force = Config.GUARDIAN_KNOCKBACK_FORCE.get();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, allay.getBoundingBox().inflate(1.6))) {
                    if (e == owner || e instanceof Allay) {
                        continue;
                    }
                    Vec3 away = e.position().subtract(owner.position());
                    away = (away.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : away.normalize()).scale(force).add(0, 0.35, 0);
                    e.setDeltaMovement(away);
                    e.hurtMarked = true;
                    level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                }
            }
            case FIREWORKS -> {
                if (now % 6 == 0) {
                    shootFirework(level, allay);
                }
            }
            case BED_GUARD -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(4.0))) {
                    if (e == owner || e instanceof Allay) {
                        continue;
                    }
                    Vec3 away = e.position().subtract(owner.position());
                    away = (away.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : away.normalize()).scale(0.5).add(0, 0.2, 0);
                    e.setDeltaMovement(away);
                    e.hurtMarked = true;
                }
            }
            default -> { }
        }
    }

    private static void shootFirework(ServerLevel level, Allay allay) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion boom = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(0x2E6BFF, 0xFFE23D), IntList.of(0xFFFFFF), true, true); // blue + yellow, white fade
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, java.util.List.of(boom)));
        FireworkRocketEntity fw = new FireworkRocketEntity(level, allay.getX(), allay.getY() + 0.3, allay.getZ(), rocket);
        level.addFreshEntity(fw);
    }

    private static void trackIdleAndCrouch(ServerPlayer owner, State s, long now) {
        Vec3 pos = owner.position();
        if (s.lastPos == null || s.lastPos.distanceToSqr(pos) > 0.02) {
            s.idleSince = now;
        }
        s.lastPos = pos;

        boolean crouch = owner.isCrouching();
        if (crouch && !s.wasCrouching) {
            s.crouchToggles = (now - s.lastCrouchToggle < 12) ? s.crouchToggles + 1 : 1;
            s.lastCrouchToggle = now;
            if (s.crouchToggles >= 4) {
                s.danceUntil = now + 90;
                s.crouchToggles = 0;
            }
        }
        s.wasCrouching = crouch;
        s.crouchSince = crouch ? (s.crouchSince == 0 ? now : s.crouchSince) : 0;
    }

    // ---- Grace aura + Sacrifice (the always-on strength) ---------------------------------------------------

    /** A steady, always-on benefit: out-of-combat it keeps a few Absorption hearts topped up and speeds regen. */
    private static void graceAura(ServerPlayer owner, State s, long now) {
        if (now % Config.GUARDIAN_AURA_INTERVAL.get() != 0 || now - s.lastHurt <= 100) {
            return; // only out of combat (no hit in the last 5s)
        }
        int hearts = Config.GUARDIAN_ABSORPTION_HEARTS.get();
        if (hearts > 0 && owner.getAbsorptionAmount() < hearts * 2 - 0.5F) {
            int amp = Math.max(0, hearts / 2 - 1);
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, Config.GUARDIAN_AURA_INTERVAL.get() + 60, amp, false, false, true));
        }
        if (owner.getHealth() < owner.getMaxHealth()) {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, false, false, true));
        }
        // subtle indicator: a few soft golden motes rising around your feet (no sound), so the aura reads.
        for (int i = 0; i < 3; i++) {
            double a = owner.getRandom().nextDouble() * Math.PI * 2;
            owner.serverLevel().sendParticles(HALO, owner.getX() + Math.cos(a) * 0.6, owner.getY() + 0.1,
                    owner.getZ() + Math.sin(a) * 0.6, 1, 0.0, 0.08, 0.0, 0.02);
        }
    }

    /**
     * the guardian throws itself in the way of a killing blow: the hit is survived (you're left at 1 HP with a
     * strong recovery buff) and the allay dies in your place, returning after the respawn cooldown. Only works
     * while a guardian is actually alive — so it's a real second life, gated by the 5-min return.
     */
    public static boolean tryGuardianSacrifice(ServerPlayer owner) {
        if (!Config.GUARDIAN_SACRIFICE.get()) {
            return false;
        }
        State s = STATES.get(owner.getUUID());
        if (s == null || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        Allay allay = allay(level, s);
        if (allay == null) {
            return false; // no guardian present to give its life
        }
        Vec3 at = allay.position();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 80, 0.5, 0.7, 0.5, 0.5);
        level.sendParticles(ParticleTypes.FLASH, owner.getX(), owner.getY() + 1.0, owner.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, owner.getX(), owner.getY() + 1.0, owner.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
        level.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

        allay.discard();
        s.allayId = null;
        s.respawnAt = level.getGameTime() + Config.GUARDIAN_RESPAWN_TICKS.get();
        s.carrying = false;
        s.kidnapTarget = null;

        owner.setHealth(Math.max(owner.getHealth(), 1.0F));
        owner.clearFire();
        owner.setRemainingFireTicks(0);
        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2, false, true, true));
        owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2, false, true, true));
        owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1, false, true, true));
        owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, true, true));
        return true;
    }

    /** A little personality burst above the allay's head. */
    private static void emote(ServerLevel level, State s, ParticleOptions p, int count) {
        if (level.getEntity(s.allayId) instanceof Allay a) {
            level.sendParticles(p, a.getX(), a.getY() + 0.9, a.getZ(), count, 0.18, 0.12, 0.18, 0.01);
        }
    }

    /** the allay heals itself fast once it's gone un-hit for a few seconds (subtle pink dust while it does). */
    private static void allayRegen(Allay allay, ServerLevel level, State s, long now) {
        if (allay.hurtTime > 0) {
            s.allayLastHurt = now;
        }
        if (allay.getHealth() < allay.getMaxHealth() && now - s.allayLastHurt >= Config.GUARDIAN_ALLAY_REGEN_DELAY.get()) {
            allay.heal(1.0F);
            if (now % 2 == 0) {
                level.sendParticles(PINK, allay.getX(), allay.getY() + 0.4, allay.getZ(), 1, 0.15, 0.2, 0.15, 0.01);
            }
        }
    }

    /** guiding light: in a crowd, glow the highest-health foe and remember it (you deal bonus damage to it). */
    private static void refreshGuidingLight(ServerPlayer owner, ServerLevel level, State s) {
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(16.0),
                e -> e != owner && !(e instanceof Allay) && (e instanceof Enemy || (e instanceof Player p && p != owner)));
        if (foes.size() < Config.GUARDIAN_GUIDING_MIN_ENEMIES.get()) {
            s.guidingTarget = null;
            return;
        }
        LivingEntity toughest = foes.stream().max(java.util.Comparator.comparingDouble(LivingEntity::getHealth)).orElse(null);
        if (toughest != null) {
            boolean changed = !toughest.getUUID().equals(s.guidingTarget);
            s.guidingTarget = toughest.getUUID();
            toughest.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
            level.sendParticles(HALO, toughest.getX(), toughest.getY() + toughest.getBbHeight() + 0.3, toughest.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            if (changed) {
                holyChime(level, toughest.blockPosition(), 0.3F); // the "lock changed" cue
            }
        }
    }

    /** the holy-water consecration chime — reused for events that lacked a sound. */
    private static void holyChime(ServerLevel level, BlockPos pos, float volume) {
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, volume, 1.5F);
    }

    /** bonus damage the owner deals to the highlighted Guiding-light foe (0 otherwise) — with a gold hit-burst. */
    public static float guidingBonus(ServerPlayer owner, LivingEntity victim) {
        State s = STATES.get(owner.getUUID());
        if (s != null && victim.getUUID().equals(s.guidingTarget)) {
            if (victim.level() instanceof ServerLevel level) {
                double y = victim.getY() + victim.getBbHeight() * 0.6;
                level.sendParticles(HALO, victim.getX(), y, victim.getZ(), 14, 0.25, 0.35, 0.25, 0.15);
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), y, victim.getZ(), 10, 0.25, 0.3, 0.25, 0.25);
                level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5F, 1.5F);
            }
            return (float) (double) Config.GUARDIAN_GUIDING_BONUS.get();
        }
        return 0.0F;
    }

    // ---- Blinding light: charge, launch, impact ------------------------------------------------------------

    private void beginBlinding(State s, LivingEntity foe, long now) {
        s.blindingTarget = foe.getUUID();
        s.blindingChargeEnd = now + 60; // 3s charge
        s.blindingLaunched = false;
        s.blindingSpot = null;
    }

    /** @return true while the strike is still in progress (drives the allay this tick). */
    private boolean driveBlinding(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        if (!(level.getEntity(s.blindingTarget) instanceof LivingEntity foe) || !foe.isAlive()) {
            s.blindingTarget = null;
            s.blindingSpot = null;
            return false;
        }
        if (!s.blindingLaunched && now < s.blindingChargeEnd) {
            // CHARGE: freeze in the air above you and build an intensifying swirl of light for 3 seconds.
            if (s.blindingSpot == null) {
                s.blindingSpot = owner.getEyePosition().add(0, 1.6, 0);
                level.playSound(null, allay.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.3F);
            }
            allay.setPos(s.blindingSpot.x, s.blindingSpot.y, s.blindingSpot.z);
            allay.setDeltaMovement(Vec3.ZERO);
            double prog = 1.0 - (s.blindingChargeEnd - now) / 60.0; // 0 → 1
            int spokes = 6;
            double r = 0.3 + prog * 1.0;
            for (int i = 0; i < spokes; i++) {
                double a = now * 0.5 + i * (Math.PI * 2 / spokes);
                level.sendParticles(BLUE, allay.getX() + Math.cos(a) * r, allay.getY() + 0.4, allay.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, allay.getX() + Math.cos(-a) * r * 0.7, allay.getY() + 0.4, allay.getZ() + Math.sin(-a) * r * 0.7, 1, 0, 0, 0, 0);
            }
            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.FIREWORK, allay.getX(), allay.getY() + 0.4, allay.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
            }
            if (now == s.blindingChargeEnd - 1) {
                level.sendParticles(ParticleTypes.FLASH, allay.getX(), allay.getY() + 0.4, allay.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, allay.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 2.0F);
            }
            return true;
        }
        // LAUNCH: streak at the foe like a projectile.
        s.blindingLaunched = true;
        Vec3 target = foe.position().add(0, foe.getBbHeight() * 0.5, 0);
        Vec3 cur = allay.position();
        Vec3 d = target.subtract(cur);
        double dist = d.length();
        if (dist <= 1.2) {
            blindingImpact(owner, level, foe);
            s.blindingTarget = null;
            s.blindingSpot = null;
            return false;
        }
        double step = Math.min(dist, Math.max(1.6, Config.GUARDIAN_SPEED.get() * 3.5));
        Vec3 dir = d.scale(step / dist);
        allay.setPos(cur.x + dir.x, cur.y + dir.y, cur.z + dir.z);
        level.sendParticles(BLUE, cur.x, cur.y, cur.z, 4, 0.05, 0.05, 0.05, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, cur.x, cur.y, cur.z, 2, 0.05, 0.05, 0.05, 0.0);
        if (now > s.blindingChargeEnd + 60) { // safety timeout
            s.blindingTarget = null;
            s.blindingSpot = null;
            return false;
        }
        return true;
    }

    private static void blindingImpact(ServerPlayer owner, ServerLevel level, LivingEntity foe) {
        Vec3 at = foe.position().add(0, foe.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 50, 0.5, 0.5, 0.5, 0.25);
        level.sendParticles(BLUE, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.2);
        level.playSound(null, foe.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.3F);
        foe.hurt(level.damageSources().indirectMagic(owner, owner), 6.0F);
        Vec3 kb = foe.position().subtract(owner.position());
        foe.setDeltaMovement((kb.lengthSqr() < 1.0e-3 ? new Vec3(0, 0.6, 0) : kb.normalize().scale(0.6)).add(0, 0.3, 0));
        foe.hurtMarked = true;
        if (foe instanceof Player p) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 12 * 20, 0, false, true, true));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * 20, 1, false, true, true));
        } else {
            foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, true, true)); // stun
            if (foe instanceof Mob mob) {
                mob.setTarget(null); // break aggro
            }
        }
    }

    /** zap: while you're fighting, a CHAIN bolt arcs through a group — small damage each, so it whittles a crowd. */
    private void zapTick(ServerPlayer owner, ServerLevel level, State s, long now) {
        if (now >= s.combatUntil || !ready(s, "zap", now, Config.GUARDIAN_ZAP_COOLDOWN.get())) {
            return;
        }
        Mob first = level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(10.0),
                        m -> m instanceof Enemy && m.isAlive()).stream()
.min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(owner))).orElse(null);
        if (first == null) {
            return;
        }
        Allay allay = allay(level, s);
        Vec3 prev = allay != null ? allay.position().add(0, 0.3, 0) : owner.getEyePosition();
        double dmg = Config.GUARDIAN_ZAP_DAMAGE.get();
        double falloff = Config.GUARDIAN_ZAP_CHAIN_FALLOFF.get();
        double chainRange = Config.GUARDIAN_ZAP_CHAIN_RANGE.get();
        java.util.Set<Integer> hit = new java.util.HashSet<>();
        Mob cur = first;
        for (int jump = 0; jump <= Config.GUARDIAN_ZAP_CHAIN_MAX.get() && cur != null; jump++) {
            hit.add(cur.getId());
            Vec3 pt = cur.position().add(0, cur.getBbHeight() * 0.5, 0);
            zapArc(level, prev, pt);
            if (dmg > 0) {
                cur.hurt(WitchModDamageTypes.guardianZap(level, owner), (float) dmg);
            }
            cur.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 4, false, true, true)); // brief stun
            level.sendParticles(BLUE, pt.x, pt.y, pt.z, 8, 0.2, 0.25, 0.2, 0.06);
            level.sendParticles(ParticleTypes.FLASH, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            dmg *= falloff;
            prev = pt;
            final Mob from = cur;
            cur = level.getEntitiesOfClass(Mob.class, from.getBoundingBox().inflate(chainRange),
                            m -> m instanceof Enemy && m.isAlive() && !hit.contains(m.getId())).stream()
.min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(from))).orElse(null);
        }
        if (allay != null) {
            level.playSound(null, allay.blockPosition(), WitchModSounds.GUARDIAN_ZAP.get(), SoundSource.PLAYERS,
                    0.7F, 0.85F + owner.getRandom().nextFloat() * 0.5F);
        }
    }

    private static void zapArc(ServerLevel level, Vec3 a, Vec3 b) {
        for (int i = 0; i <= 8; i++) {
            Vec3 pt = a.lerp(b, i / 8.0);
            level.sendParticles(BLUE, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** proactive protection: a hostile that has just LOCKED ONTO you is grabbed (kidnap) or shoved back at once. */
    private void lockOnProtect(ServerPlayer owner, ServerLevel level, State s, long now) {
        if (s.kidnapTarget != null || s.liftTarget != null || s.carrying) {
            return;
        }
        Mob lockedOn = level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12.0),
                        m -> m instanceof Enemy && m.isAlive() && m.getTarget() == owner).stream()
.min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(owner))).orElse(null);
        if (lockedOn == null) {
            return;
        }
        if (ready(s, "kidnap", now, Config.GUARDIAN_KIDNAP_COOLDOWN.get())) {
            grabAttacker(level, s, owner, lockedOn, now);
        } else if (ready(s, "protectshove", now, 30)) {
            Vec3 away = lockedOn.position().subtract(owner.position());
            away = (away.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : away.normalize()).scale(Config.GUARDIAN_KNOCKBACK_FORCE.get()).add(0, 0.35, 0);
            lockedOn.setDeltaMovement(away);
            lockedOn.hurtMarked = true;
            flyTo(s, lockedOn, now, 14);
            level.sendParticles(ParticleTypes.CLOUD, lockedOn.getX(), lockedOn.getY() + lockedOn.getBbHeight() * 0.5, lockedOn.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, lockedOn.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    // ---- Reactive layer ------------------------------------------------------------------------------------

    private void reactiveTick(ServerPlayer owner, ServerLevel level, State s, long now) {
        // heavy world/entity scans run on a per-player-staggered 5-tick beat, so a server full of guardians
        // isn't scanning every one every tick. The cheap, time-critical checks below still run every tick.
        boolean scan = (now + owner.getId()) % 5 == 0;
        if (scan) {
            lockOnProtect(owner, level, s, now);
            zapTick(owner, level, s, now);
        }
        // escort: badly hurt with danger near → it lifts you and flies you to safety (Catch's low-health cousin).
        if (!s.carrying && s.kidnapTarget == null && s.blindingTarget == null
                && owner.getHealth() <= owner.getMaxHealth() * (Config.GUARDIAN_ESCORT_HEALTH_PERCENT.get() / 100.0F)
                && countHostilesNear(owner) > 0 && ready(s, "escort", now, 400)) {
            beginCarry(owner, level, s, now);
        }
        // blinding light: charge up and launch at the nearest foe (near enemies, long cooldown).
        if (scan && s.blindingTarget == null && s.kidnapTarget == null && !s.carrying
                && ready(s, "blinding", now, Config.GUARDIAN_BLINDING_COOLDOWN.get())) {
            LivingEntity foe = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(12.0),
                            e -> e != owner && !(e instanceof Allay) && (e instanceof Enemy || (e instanceof Player p && p != owner))).stream()
.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(owner))).orElse(null);
            if (foe != null) {
                beginBlinding(s, foe, now);
            }
        }
        // fishing: hover the bobber and hurry the bite along (stacks with Angler).
        if (owner.fishing != null) {
            BlessingAngler.hurry(owner.fishing, Config.GUARDIAN_FISHING_HURRY.get());
        }
        // anti-grief: a lit creeper / primed TNT close by is grabbed and flung away (high priority).
        if (scan && s.kidnapTarget == null && !s.carrying && ready(s, "antigrief", now, 40)) {
            LivingEntity hazard = nearbyHazardEntity(owner, level);
            if (hazard != null) {
                beginKidnap(level, s, hazard, now);
            }
        }
        // dampen: put out your fire.
        if (owner.isOnFire() && ready(s, "dampen", now, 20)) {
            owner.clearFire();
            flyOrbitOwner(s, now, 24);
            level.sendParticles(ParticleTypes.SPLASH, owner.getX(), owner.getY() + 1.0, owner.getZ(), 30, 0.4, 0.7, 0.4, 0.1);
            level.playSound(null, owner.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        // anti-fire: a fire block near you gets splashed out.
        if (scan && ready(s, "antifire", now, 25)) {
            BlockPos fire = nearbyFire(owner, level);
            if (fire != null) {
                level.removeBlock(fire, false);
                flyTo(s, Vec3.atCenterOf(fire), now, 20);
                level.sendParticles(ParticleTypes.SPLASH, fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
                level.playSound(null, fire, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.6F, 1.3F);
            }
        }
        // cleanse: purge 1-2 harmful potion effects.
        if (ready(s, "cleanse", now, 200) && hasHarmfulEffect(owner)) {
            cleanse(owner, level, s, now);
        }
        // hunger: below 40% food, fly in and top you up a little.
        if (owner.getFoodData().getFoodLevel() < 8 && ready(s, "hunger", now, 200)) {
            FoodData fd = owner.getFoodData();
            fd.setFoodLevel(Math.min(20, fd.getFoodLevel() + 4));
            fd.setSaturation(Math.min(fd.getFoodLevel(), fd.getSaturationLevel() + 2.5F));
            flyOrbitOwner(s, now, 24);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, owner.getX(), owner.getY() + 1.0, owner.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
            level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.3F, 1.6F);
        }
        // air save: brief water breathing when you'd start drowning.
        if (owner.isUnderWater() && owner.getAirSupply() <= 0 && ready(s, "air", now, 100)) {
            owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0, false, true, true));
            owner.setAirSupply(owner.getMaxAirSupply());
            flyOrbitOwner(s, now, 24);
            level.sendParticles(ParticleTypes.BUBBLE, owner.getX(), owner.getEyeY(), owner.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
        }
        // catch: a dangerous fall — physically scoop you up and fly you to a safe landing. Skipped entirely if
        // you already handle falls yourself (Twinkletoes / Flight), so it doesn't hijack a controlled descent.
        if (!s.carrying && !owner.onGround() && !owner.getAbilities().flying && owner.fallDistance > 6.0F
                && owner.getDeltaMovement().y < -0.55 && !fallSafe(owner) && ready(s, "catch", now, 60)) {
            beginCarry(owner, level, s, now);
        }
        // phantom purge: fly up and vaporise phantoms in a flash.
        if (scan) {
            List<Phantom> phantoms = level.getEntitiesOfClass(Phantom.class, owner.getBoundingBox().inflate(Config.GUARDIAN_PHANTOM_RADIUS.get()));
            if (!phantoms.isEmpty() && ready(s, "phantom", now, 60)) {
                for (Phantom p : phantoms) {
                    level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
                    p.hurt(level.damageSources().magic(), p.getMaxHealth() * 2.0F);
                }
                flyTo(s, phantoms.get(0), now, 20);
                level.playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1.0F, 1.7F);
            }
        }
        // curse purge: a chance to lift one random curse.
        if (EffectManager.hasActiveOfCategory(owner, EffectCategory.CURSE) && ready(s, "purge", now, 600)
                && owner.getRandom().nextInt(100) < Config.GUARDIAN_CURSE_PURGE_CHANCE.get()) {
            purgeOneCurse(owner, level, s, now);
        }
        // angel's grudge: a hidden irritation timer fills while the solicitor's trader is around, then the
        // angel hunts it down, kidnaps + kills it, and forces the solicitor onto a long cooldown.
        if (Synergies.ANGELS_GRUDGE.activeFor(owner)) {
            if (s.solicitorStrikeAt < 0) {
                s.solicitorStrikeAt = now + rollIrritation(owner);
            } else if (now >= s.solicitorStrikeAt) {
                s.solicitorStrikeAt = now + (strikeSolicitor(owner, level, s, now) ? rollIrritation(owner) : 100);
            }
        } else {
            s.solicitorStrikeAt = -1;
        }
        // arrow retrieval: fetch your missed arrows.
        if (scan && ready(s, "arrows", now, 40)) {
            retrieveArrows(owner, level, s, now);
        }
        // restock: fetch floor items you ALREADY carry, so a dropped stack you own drifts back to you.
        if (scan && ready(s, "restock", now, 30)) {
            collectKnownItems(owner, level);
        }
        // knockback: surrounded by a crowd → pinball off them to clear some space.
        if (now >= s.pingpongUntil && ready(s, "pingpong", now, 120)
                && countSurrounding(owner, level) >= Config.GUARDIAN_SURROUND_COUNT.get()
                && owner.getRandom().nextInt(100) < 70) {
            s.pingpongUntil = now + 40;
            level.playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.8F, 1.5F);
        }
    }

    private static int countSurrounding(ServerPlayer owner, ServerLevel level) {
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(4.0))) {
            if (e != owner && !(e instanceof Allay)) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    private static LivingEntity nearbyHazardEntity(ServerPlayer owner, ServerLevel level) {
        double r = Config.GUARDIAN_ANTIGRIEF_RADIUS.get();
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Creeper c : level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(r))) {
            if (c.isIgnited() || c.getSwelling(1.0F) > 0.0F || c.distanceToSqr(owner) < 9.0) {
                double d = c.distanceToSqr(owner);
                if (d < bestSq) {
                    bestSq = d;
                    best = c;
                }
            }
        }
        if (best != null) {
            return best;
        }
        // primed TNT isn't a LivingEntity — grab it by re-using the kidnap grab on the nearest one.
        PrimedTnt tnt = level.getEntitiesOfClass(PrimedTnt.class, owner.getBoundingBox().inflate(r)).stream()
.min(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(owner))).orElse(null);
        if (tnt != null) {
            grabAndFling(owner, level, tnt);
        }
        return null;
    }

    /** fling a non-living hazard (TNT) up and away from you (a lightweight cousin of kidnap). */
    private static void grabAndFling(ServerPlayer owner, ServerLevel level, Entity hazard) {
        Vec3 away = hazard.position().subtract(owner.position());
        away = (away.lengthSqr() < 1.0e-3 ? new Vec3(1, 0, 0) : away.normalize()).scale(1.4).add(0, 0.9, 0);
        hazard.setDeltaMovement(away);
        hazard.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD, hazard.getX(), hazard.getY() + 0.3, hazard.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
    }

    @Nullable
    private static BlockPos nearbyFire(ServerPlayer owner, ServerLevel level) {
        BlockPos base = owner.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (level.getBlockState(m).is(BlockTags.FIRE)) {
                        return m.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasHarmfulEffect(ServerPlayer owner) {
        return owner.getActiveEffects().stream().anyMatch(BlessingGuardianAngel::cleansable);
    }

    /** a harmful potion effect the angel may wipe — but never the mod's own mechanic markers (thirst's dehydration,
     * the cursed status wrapper), which drive their own systems/HUD and only flicker if cleansed. */
    private static boolean cleansable(MobEffectInstance e) {
        if (e.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
            return false;
        }
        return e.getEffect() != WitchModMobEffects.DEHYDRATION && e.getEffect() != WitchModMobEffects.CURSED;
    }

    private static void cleanse(ServerPlayer owner, ServerLevel level, State s, long now) {
        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance e : owner.getActiveEffects()) {
            if (cleansable(e)) {
                harmful.add(e.getEffect());
            }
        }
        int remove = Math.min(harmful.size(), 1 + owner.getRandom().nextInt(2));
        for (int i = 0; i < remove; i++) {
            owner.removeEffect(harmful.get(i));
        }
        flyOrbitOwner(s, now, 40);
        emote(level, s, ParticleTypes.HEART, 4);
        level.sendParticles(ParticleTypes.END_ROD, owner.getX(), owner.getY() + 1.0, owner.getZ(), 24, 0.4, 0.7, 0.4, 0.05);
        level.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** pull nearby floor items whose type you already hold to you (vanilla pickup then grabs them). */
    private static void collectKnownItems(ServerPlayer owner, ServerLevel level) {
        double r = Config.GUARDIAN_DETECT_RADIUS.get();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(r))) {
            if (!item.isAlive() || item.hasPickUpDelay()) {
                continue;
            }
            ItemStack stack = item.getItem();
            if (stack.isEmpty() || !owner.getInventory().contains(stack)) {
                continue; // only fetch what you already own
            }
            item.setNoPickUpDelay();
            item.setPos(owner.getX(), owner.getY() + 0.2, owner.getZ());
            level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.3, item.getZ(), 2, 0.05, 0.05, 0.05, 0.02);
        }
    }

    private static void purgeOneCurse(ServerPlayer owner, ServerLevel level, State s, long now) {
        // angel's grudge: the solicitor is exempt from a plain purge — the angel hunts its trader down instead.
        boolean grudge = Synergies.ANGELS_GRUDGE.activeFor(owner);
        List<ResourceLocation> curses = new ArrayList<>();
        for (ResourceLocation id : EffectManager.activeSnapshot(owner).keySet()) {
            EffectManager.holderOf(id).ifPresent(h -> {
                if (h.value().category() == EffectCategory.CURSE
                        && !(grudge && h.value() == Curses.SOLICITOR.get())) {
                    curses.add(id);
                }
            });
        }
        if (curses.isEmpty()) {
            return;
        }
        ResourceLocation pick = curses.get(owner.getRandom().nextInt(curses.size()));
        EffectManager.holderOf(pick).ifPresent(h -> EffectManager.remove(owner, h));
        flyOrbitOwner(s, now, 40);
        level.sendParticles(ParticleTypes.END_ROD, owner.getX(), owner.getY() + 1.0, owner.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, owner.getX(), owner.getY() + 1.0, owner.getZ(), 20, 0.4, 0.8, 0.4, 0.2);
        level.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    private static long rollIrritation(ServerPlayer owner) {
        int min = Config.ANGELS_GRUDGE_IRRITATION_MIN_TICKS.get();
        int max = Math.max(min, Config.ANGELS_GRUDGE_IRRITATION_MAX_TICKS.get());
        return min + owner.getRandom().nextInt(max - min + 1);
    }

    /** angel's grudge: hunt down the solicitor's trader, haul it up + kill it, forcing a long cooldown. */
    private static boolean strikeSolicitor(ServerPlayer owner, ServerLevel level, State s, long now) {
        WanderingTrader trader = CurseSolicitor.getTrader(owner);
        if (trader == null) {
            return false; // hiding already — try again shortly
        }
        flyTo(s, trader, now, 30);
        trader.setDeltaMovement(0, 0.6, 0); // a comic little abduction pop
        trader.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD, trader.getX(), trader.getY() + trader.getBbHeight(), trader.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, trader.getX(), trader.getY() + 1.0, trader.getZ(), 24, 0.3, 0.6, 0.3, 0.1);
        level.playSound(null, trader.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 0.8F);
        CurseSolicitor.abduct(owner, Config.ANGELS_GRUDGE_COOLDOWN_TICKS.get());
        return true;
    }

    private static void retrieveArrows(ServerPlayer owner, ServerLevel level, State s, long now) {
        double r = Config.GUARDIAN_DETECT_RADIUS.get();
        boolean got = false;
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, owner.getBoundingBox().inflate(r))) {
            if (arrow.pickup != AbstractArrow.Pickup.ALLOWED || arrow.getOwner() != owner
                    || arrow.tickCount < 10 || arrow.getDeltaMovement().lengthSqr() > 1.0e-5) {
                continue;
            }
            ItemStack stack = new ItemStack(arrow instanceof SpectralArrow ? Items.SPECTRAL_ARROW : Items.ARROW);
            if (!owner.getInventory().add(stack)) {
                continue; // inventory full — leave it lying there
            }
            level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 4, 0.05, 0.05, 0.05, 0.02);
            arrow.discard();
            got = true;
        }
        if (got) {
            level.playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
    }

    // ---- Catch: physically carry you to safety -------------------------------------------------------------

    /** you already survive falls (Twinkletoes / Flight / Spider / Slow Falling) → the Catch save is pointless. */
    private static boolean fallSafe(ServerPlayer owner) {
        return owner.hasEffect(MobEffects.SLOW_FALLING)
                || EffectManager.isActive(owner, Blessings.TWINKLETOES)
                || EffectManager.isActive(owner, Blessings.FLIGHT)
                || EffectManager.isActive(owner, Blessings.SPIDER);
    }

    private void beginCarry(ServerPlayer owner, ServerLevel level, State s, long now) {
        Allay allay = allay(level, s);
        if (allay == null) {
            owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, true, true)); // no allay — at least soften it
            return;
        }
        Vec3 grab = owner.position().add(0, owner.getBbHeight() + 0.2, 0);
        allay.setPos(grab.x, grab.y, grab.z);
        owner.startRiding(allay, true);
        owner.fallDistance = 0;
        s.carrying = true;
        s.carryDest = findSafeLanding(owner, level);
        s.carryUntil = now + 200; // 10s hard cap
        level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY(), owner.getZ(), 18, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    private void endCarry(ServerPlayer owner, ServerLevel level, State s) {
        s.carrying = false;
        if (owner.isPassenger()) {
            owner.stopRiding();
        }
        if (s.carryDest != null) {
            owner.teleportTo(s.carryDest.x, s.carryDest.y, s.carryDest.z);
            level.sendParticles(ParticleTypes.HEART, s.carryDest.x, s.carryDest.y + 1.0, s.carryDest.z, 8, 0.3, 0.4, 0.3, 0.0);
        }
        owner.fallDistance = 0;
        owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
        level.playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.7F, 1.5F);
        s.carryDest = null;
    }

    /** nearest safe solid top (2 air above, no hazard) from the owner's column outward; falls back to their spot. */
    private static Vec3 findSafeLanding(ServerPlayer owner, ServerLevel level) {
        int[] dx = {0, 2, -2, 0, 0, 4, -4, 0, 0, 3, -3};
        int[] dz = {0, 0, 0, 2, -2, 0, 0, 4, -4, 3, -3};
        for (int i = 0; i < dx.length; i++) {
            Vec3 spot = safeColumn(level, owner.blockPosition().offset(dx[i], 0, dz[i]));
            if (spot != null) {
                return spot;
            }
        }
        return owner.position();
    }

    @Nullable
    private static Vec3 safeColumn(ServerLevel level, BlockPos start) {
        int top = Math.min(start.getY(), level.getMaxBuildHeight() - 3);
        for (int y = top; y > level.getMinBuildHeight(); y--) {
            BlockPos ground = new BlockPos(start.getX(), y, start.getZ());
            BlockState gs = level.getBlockState(ground);
            BlockState a1 = level.getBlockState(ground.above());
            BlockState a2 = level.getBlockState(ground.above(2));
            if (gs.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP) && a1.isAir() && a2.isAir()
                    && !isHazard(gs) && !isHazard(a1)) {
                return new Vec3(start.getX() + 0.5, y + 1.0, start.getZ() + 0.5);
            }
        }
        return null;
    }

    private static boolean isHazard(BlockState st) {
        if (st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            return true;
        }
        return st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.CACTUS)
                || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.WITHER_ROSE)
                || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE);
    }

    // ---- Kidnap: haul an attacker up and drop them ---------------------------------------------------------

    private void beginKidnap(ServerLevel level, State st, LivingEntity attacker, long now) {
        Allay allay = allay(level, st);
        if (allay == null || st.carrying) {
            return;
        }
        // just MARK the target — the allay flies over to it first (driveKidnap), and only grabs on arrival.
        st.kidnapTarget = attacker.getUUID();
        st.kidnapGrabbed = false;
        st.kidnapUntil = now + 400; // includes the fly-over + haul
    }

    /** snap the grab details + take hold of the captive once the allay has reached it. */
    private void grabKidnap(ServerLevel level, State st, LivingEntity captive, long now) {
        Vec3 lava = nearbyLava(level, captive.blockPosition(), Config.GUARDIAN_KIDNAP_LAVA_RADIUS.get());
        st.kidnapGrabbed = true;
        st.kidnapIntoLava = lava != null;
        st.kidnapDest = lava;
        st.kidnapCeiling = captive.getY() + Config.GUARDIAN_KIDNAP_DROP_HEIGHT.get();
        if (captive instanceof Player p) {
            p.startRiding(allay(level, st), true); // forced to stay with the allay
        }
        captive.fallDistance = 0;
        level.playSound(null, captive.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** @return true while the kidnap is still in progress (drives the allay + captive this tick). */
    private boolean driveKidnap(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        Entity captive = level.getEntity(s.kidnapTarget);
        if (!(captive instanceof LivingEntity le) || !le.isAlive() || now >= s.kidnapUntil) {
            endKidnap(level, s);
            return false;
        }
        // phase 1: fly OVER to the target before grabbing (no teleport).
        if (!s.kidnapGrabbed) {
            Vec3 grab = captive.position().add(0, captive.getBbHeight() + 0.3, 0);
            Vec3 cur = allay.position();
            Vec3 d = grab.subtract(cur);
            double dist = d.length();
            if (dist <= 1.2) {
                grabKidnap(level, s, le, now);
            } else {
                double step = Math.min(dist, Math.max(1.0, Config.GUARDIAN_SPEED.get() * 2.0));
                allay.setPos(cur.x + d.x / dist * step, cur.y + d.y / dist * step, cur.z + d.z / dist * step);
                if (now % 3 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, cur.x, cur.y, cur.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
            return true;
        }
        boolean climbing = allay.getY() < s.kidnapCeiling;
        // fly up (or over the hazard) carrying the captive — deliberately SLOW for comedy.
        Vec3 goal;
        if (s.kidnapIntoLava && s.kidnapDest != null && !climbing) {
            // release a good bit ABOVE the hazard, so the victim gets a moment of counterplay on the way down.
            goal = new Vec3(s.kidnapDest.x, s.kidnapDest.y + Config.GUARDIAN_KIDNAP_HAZARD_HEIGHT.get(), s.kidnapDest.z);
            if (allay.position().multiply(1, 0, 1).distanceToSqr(s.kidnapDest.x, 0, s.kidnapDest.z) < 1.5) {
                endKidnap(level, s); // over the hazard — let them drop in
                return false;
            }
        } else if (climbing) {
            goal = new Vec3(captive.getX(), s.kidnapCeiling, captive.getZ());
        } else {
            endKidnap(level, s); // at the ceiling with no hazard — drop from height
            return false;
        }
        Vec3 cur = allay.position();
        Vec3 dir = goal.subtract(cur);
        double dist = dir.length();
        if (dist > 1.0e-3) {
            double step = Math.min(dist, Config.GUARDIAN_SPEED.get() * 0.75);
            allay.setPos(cur.x + dir.x / dist * step, cur.y + dir.y / dist * step, cur.z + dir.z / dist * step);
        }
        if (captive instanceof Player p) {
            if (!p.isPassenger()) {
                p.startRiding(allay, true); // re-grab if they wriggled loose (dismount is also cancelled)
            }
        } else {
            // drag a non-player captive along under the allay.
            captive.setPos(allay.getX(), allay.getY() - captive.getBbHeight() - 0.1, allay.getZ());
            captive.setDeltaMovement(Vec3.ZERO);
            captive.fallDistance = 0;
            if (captive instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
        if (now % 4 == 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, captive.getX(), captive.getY() + captive.getBbHeight(), captive.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }
        return true;
    }

    private void endKidnap(ServerLevel level, State s) {
        Entity captive = s.kidnapTarget == null ? null : level.getEntity(s.kidnapTarget);
        // clear the kidnap state FIRST — otherwise the dismount-cancel (isKidnapVictim) would trap the rider.
        s.kidnapTarget = null;
        s.kidnapGrabbed = false;
        s.kidnapDest = null;
        if (captive instanceof Player p && p.getVehicle() instanceof Allay) {
            p.stopRiding();
        }
        if (captive != null) {
            captive.fallDistance = 0; // the fall itself does the damage
            level.sendParticles(ParticleTypes.CLOUD, captive.getX(), captive.getY() + 0.5, captive.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        }
    }

    // ---- Lift: fly at an attacker and levitate them (kidnap's non-desperate sibling) ----------------------

    private void beginLift(State s, LivingEntity attacker, long now) {
        s.liftTarget = attacker.getUUID();
        s.liftUntil = now + 60;
    }

    /** @return true while flying to the target; applies Levitation 10 (3s) on arrival, then ends. */
    private boolean driveLift(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        if (!(level.getEntity(s.liftTarget) instanceof LivingEntity foe) || !foe.isAlive() || now >= s.liftUntil) {
            s.liftTarget = null;
            return false;
        }
        Vec3 target = foe.position().add(0, foe.getBbHeight() * 0.6, 0);
        Vec3 cur = allay.position();
        Vec3 d = target.subtract(cur);
        double dist = d.length();
        if (dist <= 1.4) {
            foe.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 9, false, true, true)); // Levitation 10, 3s
            foe.hurtMarked = true;
            level.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 20, 0.3, 0.4, 0.3, 0.05);
            level.sendParticles(HALO, target.x, target.y, target.z, 10, 0.2, 0.3, 0.2, 0.02);
            level.playSound(null, foe.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.7F, 1.6F);
            s.liftTarget = null;
            return false;
        }
        double step = Math.min(dist, Math.max(1.2, Config.GUARDIAN_SPEED.get() * 2.2));
        allay.setPos(cur.x + d.x / dist * step, cur.y + d.y / dist * step, cur.z + d.z / dist * step);
        if (now % 3 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, cur.x, cur.y, cur.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        return true;
    }

    /** true if {@code player} is currently being carried by a guardian kidnap (so they can't dismount to escape). */
    public static boolean isKidnapVictim(Player player) {
        for (State st : STATES.values()) {
            if (st.kidnapGrabbed && player.getUUID().equals(st.kidnapTarget)) {
                return true;
            }
        }
        return false;
    }

    /** nearest lava OR fire hazard with open air above, to drop a kidnap victim into. */
    @Nullable
    private static Vec3 nearbyLava(ServerLevel level, BlockPos around, double radius) {
        if (radius <= 0) {
            return null;
        }
        int r = (int) radius;
        Vec3 best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    m.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                    BlockState st = level.getBlockState(m);
                    boolean hazard = level.getFluidState(m).is(net.minecraft.tags.FluidTags.LAVA)
                            || st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE) || st.is(Blocks.MAGMA_BLOCK)
                            || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE);
                    if (hazard && level.getBlockState(m.above()).isAir()) {
                        double d = m.distSqr(around);
                        if (d < bestSq) {
                            bestSq = d;
                            best = new Vec3(m.getX() + 0.5, m.getY() + 1.0, m.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return best;
    }

    // ---- Interaction mode resolution -----------------------------------------------------------------------

    private Mode computeMode(ServerPlayer owner, State s, long now) {
        // urgent, self-defence modes win outright.
        if (now < s.pingpongUntil) {
            return Mode.PINGPONG;
        }
        if (owner.getHealth() <= owner.getMaxHealth() * 0.5F && now >= s.healBlockedUntil && owner.getHealth() < owner.getMaxHealth()) {
            return Mode.HEAL;
        }
        if (s.pendingGift != 0) {
            return Mode.GATHER;
        }
        if (now < s.flyUntil) {
            return Mode.FLYTO;
        }
        if (now < s.fireworksUntil) {
            return Mode.FIREWORKS;
        }
        if (owner.fishing != null) {
            return Mode.FISHING;
        }
        if (now < s.franticUntil) {
            return Mode.FRANTIC;
        }
        if (now < s.miningWatchUntil) {
            return Mode.MINING;
        }
        if (owner.isSleeping()) {
            // bed guardian: if hostiles are near, actively shove them off; otherwise a calm watch.
            return countHostilesNear(owner) > 0 ? Mode.BED_GUARD : Mode.SLEEP;
        }
        if (now < s.danceUntil) {
            return Mode.DANCE;
        }
        if (owner.isCrouching() && s.crouchSince > 0 && now - s.crouchSince > 20) {
            return Mode.STEALTH;
        }
        // curiosity: acquire, or follow the current subject.
        if (s.curiosityTarget != null || s.curiosityPoint != null) {
            if (now < s.curiosityUntil && refreshCuriosity(owner, s)) {
                return Mode.CURIOSITY;
            }
            s.curiosityTarget = null;
            s.curiosityPoint = null;
        } else if (now - s.idleSince > 60 && ready(s, "curiosity", now, 300)) {
            if (acquireCuriosity(owner, s, now)) {
                return Mode.CURIOSITY;
            }
        }
        if (now - s.idleSince > 200) {
            return Mode.BOREDOM;
        }
        return Mode.FOLLOW;
    }

    private static int countHostilesNear(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return 0;
        }
        return level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(8.0),
                m -> m instanceof Enemy && m.isAlive()).size();
    }

    /** nearest living entity to the OWNER, excluding the owner and the allay (for pinball / bed defence). */
    @Nullable
    private static LivingEntity nearestOther(ServerPlayer owner, ServerLevel level, Allay allay) {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(6.0))) {
            if (e == owner || e == allay) {
                continue;
            }
            double d = e.distanceToSqr(allay);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean refreshCuriosity(ServerPlayer owner, State s) {
        if (s.curiosityTarget != null && owner.level() instanceof ServerLevel level) {
            if (level.getEntity(s.curiosityTarget) instanceof Entity e && e.isAlive()
                    && e.distanceToSqr(owner) < 400) {
                s.curiosityPoint = e.position().add(0, e.getBbHeight() * 0.6, 0);
                return true;
            }
            return false;
        }
        return s.curiosityPoint != null && s.curiosityPoint.distanceToSqr(owner.position()) < 400;
    }

    private static boolean acquireCuriosity(ServerPlayer owner, State s, long now) {
        ServerLevel level = owner.serverLevel();
        // prefer a nearby bee or villager to circle; else a flower block.
        Entity best = null;
        double bestSq = 64.0;
        for (Entity e : level.getEntitiesOfClass(Entity.class, owner.getBoundingBox().inflate(8.0),
                e -> e instanceof Bee || e instanceof Villager)) {
            double d = e.distanceToSqr(owner);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        if (best != null) {
            s.curiosityTarget = best.getUUID();
            s.curiosityPoint = best.position();
            s.curiosityUntil = now + 160;
            return true;
        }
        BlockPos flower = findFlower(owner, level);
        if (flower != null) {
            s.curiosityTarget = null;
            s.curiosityPoint = Vec3.atCenterOf(flower);
            s.curiosityUntil = now + 120;
            return true;
        }
        return false;
    }

    @Nullable
    private static BlockPos findFlower(ServerPlayer owner, ServerLevel level) {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 24; i++) {
            BlockPos p = base.offset(owner.getRandom().nextInt(9) - 4, owner.getRandom().nextInt(5) - 2,
                    owner.getRandom().nextInt(9) - 4);
            if (level.getBlockState(p).is(net.minecraft.tags.BlockTags.FLOWERS)) {
                return p;
            }
        }
        return null;
    }

    // ---- Ambient gifts -------------------------------------------------------------------------------------

    private void ambientTick(ServerPlayer owner, ServerLevel level, State s, long now) {
        // the rare once-per-blessing gift: bestow a random low-power blessing.
        if (owner.getData(WitchModAttachments.GUARDIAN_GIFT_USED) == 0
                && owner.getRandom().nextInt(100) < Config.GUARDIAN_RARE_GIFT_CHANCE.get()
                && bestowRandomBlessing(owner, s, now)) {
            return;
        }
        // pick a gift whose precondition holds (a couple of tries). The three "on-you" gifts (buffs/xp/
        // durability) are SCHEDULED with a gather windup so they don't feel like they land out of nowhere;
        // the fly-to-a-target gifts already read as deliberate.
        for (int attempt = 0; attempt < 3; attempt++) {
            switch (owner.getRandom().nextInt(8)) {
                case 0 -> { scheduleGift(s, 2, now); return; }
                case 1 -> { if (placeTorch(owner, level, s, now)) return; }
                case 2 -> { if (markEntity(owner, level, s, now)) return; }
                case 3 -> { if (hasDamagedGear(owner)) { scheduleGift(s, 3, now); return; } }
                case 4 -> { scheduleGift(s, 1, now); return; }
                case 5 -> { if (planter(owner, level, s, now)) return; }
                case 6 -> { if (playWithAnimals(owner, level, s, now)) return; }
                default -> { if (tendPets(owner, level, s, now)) return; }
            }
        }
    }

    private static void scheduleGift(State s, int code, long now) {
        s.pendingGift = code;
        s.pendingGiftAt = now + GIFT_WINDUP;
    }

    private static boolean hasDamagedGear(ServerPlayer owner) {
        for (ItemStack st : owner.getInventory().items) {
            if (st.isDamageableItem() && st.getDamageValue() > 0) {
                return true;
            }
        }
        for (ItemStack st : owner.getInventory().armor) {
            if (st.isDamageableItem() && st.getDamageValue() > 0) {
                return true;
            }
        }
        return owner.getOffhandItem().isDamageableItem() && owner.getOffhandItem().getDamageValue() > 0;
    }

    /** run a scheduled on-you gift once its windup completes. */
    private void executeGift(ServerPlayer owner, ServerLevel level, State s, long now) {
        int g = s.pendingGift;
        s.pendingGift = 0;
        switch (g) {
            case 1 -> giveBuffs(owner, level, s, now);
            case 2 -> giveXp(owner, level, s, now);
            case 3 -> restoreDurability(owner, level, s, now);
            default -> { }
        }
    }

    /** planter: bonemeal a nearby growable plant straight to maturity. */
    private boolean planter(ServerPlayer owner, ServerLevel level, State s, long now) {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 30; i++) {
            BlockPos p = base.offset(owner.getRandom().nextInt(9) - 4, owner.getRandom().nextInt(5) - 2, owner.getRandom().nextInt(9) - 4);
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, p, st)) {
                for (int g = 0; g < 12; g++) {
                    BlockState cur = level.getBlockState(p);
                    if (!(cur.getBlock() instanceof BonemealableBlock b2) || !b2.isValidBonemealTarget(level, p, cur)) {
                        break;
                    }
                    b2.performBonemeal(level, level.random, p, cur);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
                flyTo(s, Vec3.atCenterOf(p), now, 24);
                holyChime(level, p, 0.3F);
                return true;
            }
        }
        return false;
    }

    /** push: gently fling nearby passive animals about, as if playing with them. */
    private boolean playWithAnimals(ServerPlayer owner, ServerLevel level, State s, long now) {
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, owner.getBoundingBox().inflate(6.0), Animal::isAlive);
        if (animals.isEmpty()) {
            return false;
        }
        Animal chosen = animals.get(owner.getRandom().nextInt(animals.size()));
        for (Animal a : animals) {
            if (owner.getRandom().nextFloat() > 0.5F && a != chosen) {
                continue;
            }
            Vec3 fling = new Vec3(owner.getRandom().nextDouble() - 0.5, 0.55, owner.getRandom().nextDouble() - 0.5).scale(0.7);
            a.setDeltaMovement(a.getDeltaMovement().add(fling));
            a.hurtMarked = true;
            a.fallDistance = 0;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, a.getX(), a.getY() + a.getBbHeight(), a.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }
        flyTo(s, chosen, now, 24);
        return true;
    }

    // ---- Light-keeper + ore highlight ----------------------------------------------------------------------

    private static final java.util.Set<net.minecraft.world.level.block.Block> VALUABLE_ORES = java.util.Set.of(
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);

    /** keep the dark lit: the allay flies a torch OUT to a spot, then places it there (one at a time). */
    private static void lightKeeperTick(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        if (s.pendingTorch != null) {
            // delivering: place once the allay reaches the spot; give up if it can't get there.
            if (allay.position().distanceToSqr(Vec3.atCenterOf(s.pendingTorch)) < 2.0) {
                BlockState st = level.getBlockState(s.pendingTorch);
                if ((st.isAir() || st.canBeReplaced()) && Blocks.TORCH.defaultBlockState().canSurvive(level, s.pendingTorch)) {
                    level.setBlockAndUpdate(s.pendingTorch, Blocks.TORCH.defaultBlockState());
                    level.sendParticles(ParticleTypes.FLAME, s.pendingTorch.getX() + 0.5, s.pendingTorch.getY() + 0.6, s.pendingTorch.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.01);
                    level.playSound(null, s.pendingTorch, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
                }
                s.pendingTorch = null;
            } else if (now >= s.pendingTorchUntil) {
                s.pendingTorch = null; // couldn't reach it — drop the errand
            } else {
                flyTo(s, Vec3.atCenterOf(s.pendingTorch), now, 5); // keep steering toward it
            }
            return;
        }
        if (now % Config.GUARDIAN_TORCH_INTERVAL.get() != 0) {
            return;
        }
        boolean dark = level.getMaxLocalRawBrightness(owner.blockPosition()) < 8 || !level.isDay();
        if (!dark) {
            return;
        }
        int spacing = Config.GUARDIAN_TORCH_SPACING.get();
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos col = owner.blockPosition().offset(owner.getRandom().nextInt(11) - 5, 0, owner.getRandom().nextInt(11) - 5);
            BlockPos spot = torchSpot(level, col, owner.blockPosition().getY());
            if (spot == null || level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, spot) >= 8 || torchNear(level, spot, spacing)) {
                continue;
            }
            s.pendingTorch = spot;
            s.pendingTorchUntil = now + 100; // 5s to get there
            flyTo(s, Vec3.atCenterOf(spot), now, 100);
            return;
        }
    }

    @Nullable
    private static BlockPos torchSpot(ServerLevel level, BlockPos col, int baseY) {
        for (int y = baseY + 2; y >= baseY - 3; y--) {
            BlockPos p = new BlockPos(col.getX(), y, col.getZ());
            BlockState st = level.getBlockState(p);
            if ((st.isAir() || st.canBeReplaced())
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                    && Blocks.TORCH.defaultBlockState().canSurvive(level, p)) {
                return p;
            }
        }
        return null;
    }

    private static boolean torchNear(ServerLevel level, BlockPos spot, int radius) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    m.set(spot.getX() + dx, spot.getY() + dy, spot.getZ() + dz);
                    BlockState st = level.getBlockState(m);
                    if (st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** fly to a nearby diamond / ancient-debris / emerald ore and set off a couple of fireworks. */
    private void oreHighlight(ServerPlayer owner, ServerLevel level, State s, long now) {
        if (!ready(s, "ore", now, 120)) {
            return;
        }
        BlockPos found = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos base = owner.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (VALUABLE_ORES.contains(level.getBlockState(m).getBlock()) && !m.equals(s.lastOrePos)) {
                        double d = m.distSqr(base);
                        if (d < bestSq) {
                            bestSq = d;
                            found = m.immutable();
                        }
                    }
                }
            }
        }
        if (found == null) {
            return;
        }
        s.lastOrePos = found;
        flyTo(s, Vec3.atCenterOf(found), now, 40);
        for (int i = 0; i < 3; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion boom = new FireworkExplosion(FireworkExplosion.Shape.STAR,
                    IntList.of(0x2E6BFF, 0xFFE23D), IntList.of(0xFFFFFF), true, true);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1, java.util.List.of(boom)));
            FireworkRocketEntity fw = new FireworkRocketEntity(level,
                    found.getX() + 0.5 + (owner.getRandom().nextDouble() - 0.5), found.getY() + 0.8, found.getZ() + 0.5 + (owner.getRandom().nextDouble() - 0.5), rocket);
            level.addFreshEntity(fw);
        }
        level.sendParticles(HALO, found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
    }

    private void giveBuffs(ServerPlayer owner, ServerLevel level, State s, long now) {
        RandomSource rng = owner.getRandom();
        int count = 1 + rng.nextInt(3);
        int min = Config.GUARDIAN_BUFF_MIN_SECONDS.get();
        int max = Math.max(min, Config.GUARDIAN_BUFF_MAX_SECONDS.get());
        List<Holder<MobEffect>> pool = new ArrayList<>(GOOD_EFFECTS);
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Holder<MobEffect> eff = pool.remove(rng.nextInt(pool.size()));
            int seconds = min + rng.nextInt(max - min + 1);
            owner.addEffect(new MobEffectInstance(eff, seconds * 20, 0, false, true, true));
        }
        flyOrbitOwner(s, now, 40);
        emote(level, s, ParticleTypes.HAPPY_VILLAGER, 5);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, owner.getX(), owner.getY() + 1.0, owner.getZ(), 16, 0.4, 0.6, 0.4, 0.0);
        level.playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.6F, 1.5F);
    }

    /** once per blessing: bestow one random blessing, weighted heavily toward LOW power levels. */
    private boolean bestowRandomBlessing(ServerPlayer owner, State s, long now) {
        List<Holder.Reference<Effect>> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Holder.Reference<Effect> h : WitchModRegistries.EFFECT_REGISTRY.holders().toList()) {
            Effect e = h.value();
            if (e.category() != EffectCategory.BLESSING || e == this || EffectManager.isActive(owner, h)) {
                continue;
            }
            int w = Math.max(1, 101 - e.powerLevel()); // low power → high weight
            w = w * w; // sharpen the bias
            pool.add(h);
            weights.add(w);
            total += w;
        }
        if (pool.isEmpty()) {
            return false;
        }
        int roll = owner.getRandom().nextInt(total);
        Holder.Reference<Effect> pick = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                pick = pool.get(i);
                break;
            }
        }
        int minutes = 30 + owner.getRandom().nextInt(31);
        EffectManager.apply(owner, pick, minutes * 60 * 20, null);
        owner.setData(WitchModAttachments.GUARDIAN_GIFT_USED, 1);
        flyOrbitOwner(s, now, 60);
        ServerLevel level = owner.serverLevel();
        emote(level, s, ParticleTypes.HAPPY_VILLAGER, 8);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, owner.getX(), owner.getY() + 1.0, owner.getZ(), 40, 0.5, 1.0, 0.5, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, owner.getX(), owner.getY() + 1.0, owner.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        return true;
    }

    private void giveXp(ServerPlayer owner, ServerLevel level, State s, long now) {
        Allay allay = allay(level, s);
        Vec3 from = allay != null ? allay.position() : owner.position().add(0, 2, 0);
        int bottles = 1 + owner.getRandom().nextInt(2);
        for (int i = 0; i < bottles; i++) {
            ThrownExperienceBottle xp = new ThrownExperienceBottle(level, owner);
            xp.setPos(from.x, from.y, from.z);
            Vec3 aim = owner.position().add(0, 1.0, 0).subtract(from).normalize().scale(0.6);
            xp.setDeltaMovement(aim.add((owner.getRandom().nextDouble() - 0.5) * 0.1, 0.1, (owner.getRandom().nextDouble() - 0.5) * 0.1));
            level.addFreshEntity(xp);
        }
        flyOrbitOwner(s, now, 20);
    }

    private boolean placeTorch(ServerPlayer owner, ServerLevel level, State s, long now) {
        if (level.getMaxLocalRawBrightness(owner.blockPosition()) >= 8) {
            return false;
        }
        for (int i = 0; i < 20; i++) {
            BlockPos p = owner.blockPosition().offset(owner.getRandom().nextInt(5) - 2, owner.getRandom().nextInt(3) - 1,
                    owner.getRandom().nextInt(5) - 2);
            BlockState state = level.getBlockState(p);
            BlockState below = level.getBlockState(p.below());
            if ((state.isAir() || state.canBeReplaced()) && below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                    && level.getMaxLocalRawBrightness(p) < 8) {
                level.setBlockAndUpdate(p, Blocks.TORCH.defaultBlockState());
                level.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.01);
                flyTo(s, Vec3.atCenterOf(p), now, 24);
                return true;
            }
        }
        return false;
    }

    private boolean markEntity(ServerPlayer owner, ServerLevel level, State s, long now) {
        LivingEntity best = null;
        double bestSq = Config.GUARDIAN_DETECT_RADIUS.get() * Config.GUARDIAN_DETECT_RADIUS.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(Config.GUARDIAN_DETECT_RADIUS.get()))) {
            if (e == owner || e instanceof Allay || e.hasEffect(MobEffects.GLOWING)) {
                continue;
            }
            double d = e.distanceToSqr(owner);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        if (best == null) {
            return false;
        }
        best.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, true));
        flyTo(s, best, now, 26);
        level.sendParticles(ParticleTypes.GLOW, best.getX(), best.getY() + best.getBbHeight() * 0.5, best.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        holyChime(level, best.blockPosition(), 0.35F);
        return true;
    }

    private boolean restoreDurability(ServerPlayer owner, ServerLevel level, State s, long now) {
        int pct = Config.GUARDIAN_DURABILITY_PERCENT.get();
        boolean any = false;
        for (ItemStack stack : owner.getInventory().items) {
            any |= mend(stack, pct);
        }
        for (ItemStack stack : owner.getInventory().armor) {
            any |= mend(stack, pct);
        }
        any |= mend(owner.getOffhandItem(), pct);
        if (!any) {
            return false;
        }
        flyOrbitOwner(s, now, 24);
        level.sendParticles(ParticleTypes.WAX_OFF, owner.getX(), owner.getY() + 1.0, owner.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, owner.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    private static boolean mend(ItemStack stack, int pct) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) {
            return false;
        }
        int heal = Math.max(1, stack.getMaxDamage() * pct / 100);
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - heal));
        return true;
    }

    private boolean tendPets(ServerPlayer owner, ServerLevel level, State s, long now) {
        List<LivingEntity> pets = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(Config.GUARDIAN_DETECT_RADIUS.get()))) {
            if (e instanceof OwnableEntity oe && owner.getUUID().equals(oe.getOwnerUUID())) {
                pets.add(e);
            }
        }
        if (pets.isEmpty()) {
            return false;
        }
        for (LivingEntity pet : pets) {
            pet.heal(4.0F);
            pet.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true, true));
            level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight(), pet.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
        }
        flyTo(s, pets.get(0), now, 24);
        return true;
    }

    // ---- Movement + trails ---------------------------------------------------------------------------------

    private static void moveAndTrail(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        Vec3 goal = goalFor(owner, level, allay, s, now);
        Vec3 cur = allay.position();
        Vec3 delta = goal.subtract(cur);
        double dist = delta.length();
        double speed = Config.GUARDIAN_SPEED.get() * switch (s.mode) {
            case PINGPONG -> 2.4;
            case FRANTIC, FIREWORKS -> 1.6;
            default -> 1.0;
        };
        // once perched it settles and holds still rather than jittering against your shoulder.
        boolean settle = s.mode == Mode.BOREDOM && dist < 0.35;
        if (settle) {
            allay.setDeltaMovement(Vec3.ZERO);
            allay.setYRot(owner.getYRot());
            allay.yBodyRot = owner.getYRot();
        } else if (dist > 1.0e-3) {
            // ease toward the goal (fraction of the remaining distance), capped by the speed — smooth, not robotic.
            double step = Mth.clamp(dist * 0.28, Math.min(dist, 0.05), speed);
            Vec3 dir = delta.scale(step / dist);
            allay.setPos(cur.x + dir.x, cur.y + dir.y, cur.z + dir.z);
            allay.setDeltaMovement(dir);
            float yaw = s.mode == Mode.BOREDOM ? owner.getYRot() : (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
            allay.setYRot(yaw);
            allay.yBodyRot = yaw;
        }
        // the halo + movement trail are rendered CLIENT-side from this marker (no per-tick particle packets) —
        // see ClientCurseHandler.tickGuardianFx. The client uses the allay's own rendered position (no lag).
        allay.setData(WitchModAttachments.GUARDIAN_RENDER, renderKind(s.mode));
    }

    /** stable render code synced to the client (decoupled from Mode's ordinal): trail colour + halo/none. */
    static int renderKind(Mode mode) {
        return switch (mode) {
            case FRANTIC, PINGPONG -> 2;   // angry
            case CURIOSITY -> 3;           // happy
            case FISHING -> 4;             // splash
            case DANCE -> 5;               // note
            case HEAL -> 6;                // heart
            case FIREWORKS, BLINDING -> 7; // firework
            case BOREDOM, SLEEP -> 8;      // halo only, no trail
            case STEALTH -> 9;             // nothing
            default -> 1;                  // end_rod + halo
        };
    }

    /** where the allay wants to be this tick, by mode. */
    private static Vec3 goalFor(ServerPlayer owner, ServerLevel level, Allay allay, State s, long now) {
        switch (s.mode) {
            case CARRY -> {
                Vec3 d = s.carryDest != null ? s.carryDest : owner.position();
                return d.add(0, 1.2, 0);
            }
            case PINGPONG, BED_GUARD -> {
                // dart to the nearest entity to bounce off it; if none, weave around you fast (kept clear of you).
                LivingEntity near = nearestOther(owner, level, allay);
                if (near != null) {
                    return near.position().add(0, near.getBbHeight() * 0.5, 0);
                }
                s.orbit += 0.9F;
                return keepClear(owner.position().add(Math.cos(s.orbit) * 2.4, 1.5, Math.sin(s.orbit) * 2.4), owner, ROOM);
            }
            case GATHER -> {
                // wind up an on-you gift: a fast circle that spirals INWARD and rises as it nears completion.
                s.orbit += 0.7F;
                double frac = Mth.clamp((s.pendingGiftAt - now) / (double) GIFT_WINDUP, 0.0, 1.0); // 1 → 0
                double r = 1.3 + 1.6 * frac;
                double h = 1.0 + (1.0 - frac) * 0.9;
                return keepClear(owner.position().add(Math.cos(s.orbit) * r, h, Math.sin(s.orbit) * r), owner, 1.1);
            }
            case HEAL -> {
                // trace a WIDE five-point star around you (5/2 turning ratio), rising and falling as it goes.
                s.orbit += 0.42F;
                double star = s.orbit * 2.0;
                double r = 2.8;
                return keepClear(owner.position().add(Math.cos(star) * r, 1.1 + Math.sin(s.orbit * 0.5) * 0.9, Math.sin(star) * r), owner, ROOM);
            }
            case FIREWORKS -> {
                s.orbit += 0.5F;
                double bob = Math.sin(now * 0.3) * 0.9;
                return keepClear(owner.position().add(Math.cos(s.orbit) * 3.0, 2.6 + bob, Math.sin(s.orbit) * 3.0), owner, ROOM);
            }
            case FLYTO -> {
                if (s.flyOrbit) {
                    // A beneficial act on YOU — sweep a WIDE circle around you for emphasis.
                    s.orbit += 0.4F;
                    double bob = Math.sin(now * 0.25) * 0.3;
                    return keepClear(owner.position().add(Math.cos(s.orbit) * 2.4, 1.4 + bob, Math.sin(s.orbit) * 2.4), owner, ROOM);
                }
                Vec3 p = s.flyPoint;
                if (s.flyTarget != null && level.getEntity(s.flyTarget) instanceof Entity e) {
                    p = e.position().add(0, e.getBbHeight() * 0.6, 0);
                }
                return p != null ? p : owner.position().add(0, 1.8, 0);
            }
            case FISHING -> {
                FishingHook hook = owner.fishing;
                Vec3 base = hook != null ? hook.position() : owner.position();
                return base.add(0, 1.2 + Math.sin(now * 0.2) * 0.15, 0);
            }
            case MINING -> {
                Vec3 b = s.watchedBlock != null ? Vec3.atCenterOf(s.watchedBlock) : owner.position().add(0, 1.5, 0);
                return b.add(0, 0.8, 0);
            }
            case SLEEP -> {
                // A wide, slow, watchful circle high above you.
                s.orbit += 0.05F;
                return keepClear(owner.position().add(Math.cos(s.orbit) * 2.2, 2.1, Math.sin(s.orbit) * 2.2), owner, ROOM);
            }
            case STEALTH -> {
                float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
                return keepClear(owner.position().add(-Math.sin(yaw) * 1.0, 0.8, Math.cos(yaw) * 1.0), owner, 1.2);
            }
            case DANCE -> {
                s.orbit += 0.5F;
                double bob = Math.abs(Math.sin(now * 0.4)) * 0.7;
                return keepClear(owner.position().add(Math.cos(s.orbit) * 1.9, 1.2 + bob, Math.sin(s.orbit) * 1.9), owner, ROOM);
            }
            case BOREDOM -> {
                // hover off to one side at shoulder height (kept clear of you) and settle there.
                float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
                double bob = Math.sin(now * 0.08) * 0.03;
                return keepClear(owner.position().add(Math.cos(yaw) * ROOM * s.followSide,
                        owner.getBbHeight() * 0.9 + bob, Math.sin(yaw) * ROOM * s.followSide), owner, ROOM);
            }
            case CURIOSITY -> {
                s.orbit += 0.26F;
                Vec3 c = s.curiosityPoint != null ? s.curiosityPoint : owner.position();
                return c.add(Math.cos(s.orbit) * 1.8, 0.8, Math.sin(s.orbit) * 1.8);
            }
            case FRANTIC -> {
                s.orbit += 0.6F;
                double jx = (owner.getRandom().nextDouble() - 0.5) * 0.6;
                double jz = (owner.getRandom().nextDouble() - 0.5) * 0.6;
                double bob = Math.sin(now * 0.5) * 0.6;
                return keepClear(owner.position().add(Math.cos(s.orbit) * 2.3 + jx, 1.6 + bob, Math.sin(s.orbit) * 2.3 + jz), owner, ROOM);
            }
            default -> {
                // FOLLOW: a wide, dynamic 3-D wander that TRAVELS around you (never a rigid orbit, never inside
                // your body). Layered sines give a lazy drifting figure; it favours one side, flipping now and then.
                if (now >= s.followFlip) {
                    s.followFlip = now + 120 + owner.getRandom().nextInt(180);
                    s.followSide = owner.getRandom().nextBoolean() ? 1.0 : -1.0;
                }
                float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
                double t = now * 0.04;
                double wx = Math.sin(t) * 0.95 + Math.sin(t * 0.43) * 0.55;
                double wy = 0.7 + Math.sin(t * 0.6) * 0.4;
                double wz = Math.cos(t * 0.83) * 0.95 + Math.cos(t * 0.51) * 0.55;
                Vec3 goal = owner.getEyePosition().add(Math.cos(yaw) * 1.2 * s.followSide + wx, wy, Math.sin(yaw) * 1.2 * s.followSide + wz);
                return keepClear(goal, owner, ROOM);
            }
        }
    }

    // ---- Hooks from event handlers -------------------------------------------------------------------------

    /** the owner took a hit: kidnap the attacker if it can, otherwise just panic around them. */
    public static void onOwnerAttacked(ServerPlayer owner, LivingEntity attacker) {
        State s = STATES.get(owner.getUUID());
        if (s == null || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        s.healBlockedUntil = now + 40; // a hit interrupts the healing star
        s.lastHurt = now;
        // guarded_ally: under attack, super-buff the bodyguard so it frenzies alongside the angel.
        if (Synergies.GUARDED_ALLY.activeFor(owner)) {
            frenzyBodyguard(owner, true);
        }
        boolean grabbable = (attacker instanceof Enemy || attacker instanceof Player) && !(attacker instanceof Allay);
        if (grabbable && s.kidnapTarget == null && s.liftTarget == null && !s.carrying && allay(level, s) != null
                && ready(s, "kidnap", now, Config.GUARDIAN_KIDNAP_COOLDOWN.get())) {
            self().grabAttacker(level, s, owner, attacker, now);
        } else {
            s.franticUntil = now + 60;
        }
    }

    private static BlessingGuardianAngel self() {
        return (BlessingGuardianAngel) Blessings.GUARDIAN_ANGEL.get();
    }

    /** grab an attacker: below 40% health it's a full KIDNAP (haul away/into hazard); otherwise a quick LIFT. */
    private void grabAttacker(ServerLevel level, State s, ServerPlayer owner, LivingEntity target, long now) {
        if (owner.getHealth() <= owner.getMaxHealth() * 0.4F) {
            beginKidnap(level, s, target, now);
        } else {
            beginLift(s, target, now);
        }
    }

    /** the owner attacked something: enter combat (enables the zap), and sulk if it was a villager or your pet. */
    public static void onOwnerAttack(ServerPlayer owner, Entity victim) {
        State s = STATES.get(owner.getUUID());
        if (s == null || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        s.combatUntil = level.getGameTime() + 60; // ~3s of "in combat"
        boolean pet = victim instanceof OwnableEntity oe && owner.getUUID().equals(oe.getOwnerUUID());
        if (victim instanceof Villager || pet) {
            emote(level, s, ParticleTypes.ANGRY_VILLAGER, 5); // disapproval
        }
    }

    /** the owner broke a block: a hard block, or a run of them, earns Haste + a watchful hover. */
    public static void onOwnerMined(ServerPlayer owner, BlockPos pos, float hardness) {
        State s = STATES.get(owner.getUUID());
        if (s == null || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now - s.breakWindowStart > 60) {
            s.breakWindowStart = now;
            s.recentBreaks = 0;
        }
        s.recentBreaks++;
        boolean hard = hardness >= 3.0F;
        if ((hard || s.recentBreaks >= 5) && ready(s, "mining", now, 60)) {
            owner.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Config.GUARDIAN_HASTE_SECONDS.get() * 20, 1, false, true, true));
            s.watchedBlock = pos;
            s.miningWatchUntil = now + Config.GUARDIAN_HASTE_SECONDS.get() * 20;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, owner.getX(), owner.getY() + 1.0, owner.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
        }
        if (now < s.miningWatchUntil) {
            s.watchedBlock = pos; // keep watching whatever you're currently mining
        }
    }

    /** the owner killed a hostile: if that was the last one nearby, a chance for a celebratory firework display. */
    public static void onOwnerKill(ServerPlayer owner) {
        State s = STATES.get(owner.getUUID());
        if (s == null || !(owner.level() instanceof ServerLevel level) || allay(level, s) == null) {
            return;
        }
        long now = level.getGameTime();
        if (countHostilesNear(owner) == 0 && now >= s.fireworksUntil && ready(s, "fireworks", now, 400)
                && owner.getRandom().nextInt(100) < 50) {
            s.fireworksUntil = now + 60;
            emote(level, s, ParticleTypes.HAPPY_VILLAGER, 6);
        }
    }

    /** A guardian allay died: put its owner on the respawn cooldown. */
    public static void onAllayKilled(Allay allay) {
        for (String tag : allay.getTags()) {
            if (tag.startsWith("guardian_")) {
                try {
                    UUID owner = UUID.fromString(tag.substring("guardian_".length()));
                    State s = STATES.get(owner);
                    if (s != null) {
                        s.respawnAt = allay.level().getGameTime() + Config.GUARDIAN_RESPAWN_TICKS.get();
                        s.allayId = null;
                    }
                    // guarded_ally: the angel's death leaves a softer frenzy in the bodyguard.
                    if (allay.level() instanceof ServerLevel level) {
                        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
                        if (p != null && Synergies.GUARDED_ALLY.activeFor(p)) {
                            frenzyBodyguard(p, false);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // malformed tag
                }
                return;
            }
        }
    }

    /** guarded_ally: whip the owner's bodyguard into a frenzy alongside the angel, if it's around. */
    private static void frenzyBodyguard(ServerPlayer owner, boolean strong) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        UUID bg = BodyguardEntity.canonicalFor(owner.getUUID());
        if (bg != null && level.getEntity(bg) instanceof BodyguardEntity guard) {
            guard.frenzy(strong);
        }
    }

    // ---- Debug -------------------------------------------------------------------------------------------

    @Override
    public @Nullable String debugForce(ServerPlayer target, @Nullable String arg) {
        if (!(target.level() instanceof ServerLevel level)) {
            return null;
        }
        State s = STATES.computeIfAbsent(target.getUUID(), k -> new State());
        if (allay(level, s) == null && s.respawnAt < 0) {
            summon(target, s);
        }
        long now = level.getGameTime();
        String which = arg == null ? "xp" : arg.toLowerCase();
        switch (which) {
            case "dampen" -> { target.igniteForSeconds(4); s.cd.remove("dampen"); reactiveTick(target, level, s, now); }
            case "air" -> { s.cd.remove("air"); target.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0, false, true, true)); target.setAirSupply(target.getMaxAirSupply()); flyTo(s, target, now, 24); }
            case "catch" -> beginCarry(target, level, s, now);
            case "phantom" -> { s.cd.remove("phantom"); reactiveTick(target, level, s, now); }
            case "purge" -> { if (!EffectManager.hasActiveOfCategory(target, EffectCategory.CURSE)) return "No curse to purge."; purgeOneCurse(target, level, s, now); }
            case "arrows" -> { s.cd.remove("arrows"); retrieveArrows(target, level, s, now); }
            case "frantic" -> s.franticUntil = now + 60;
            case "dance" -> s.danceUntil = now + 90;
            case "curiosity" -> { s.cd.remove("curiosity"); if (!acquireCuriosity(target, s, now)) return "Nothing nearby to be curious about."; }
            case "xp" -> giveXp(target, level, s, now);
            case "torch" -> { if (!placeTorch(target, level, s, now)) return "No dark, valid spot to place a torch."; }
            case "mark" -> { if (!markEntity(target, level, s, now)) return "No nearby entity to mark."; }
            case "durability" -> { if (!restoreDurability(target, level, s, now)) return "No damaged tools to restore."; }
            case "pets", "caretaker" -> { if (!tendPets(target, level, s, now)) return "No owned pets nearby to tend."; }
            case "mining" -> onOwnerMined(target, target.blockPosition().below(), 3.0F);
            case "restock" -> { s.cd.remove("restock"); collectKnownItems(target, level); }
            case "buffs" -> giveBuffs(target, level, s, now);
            case "gift" -> { target.setData(WitchModAttachments.GUARDIAN_GIFT_USED, 0); if (!bestowRandomBlessing(target, s, now)) return "No blessing available to gift."; }
            case "kidnap" -> {
                LivingEntity victim = nearestKidnappable(target, level);
                if (victim == null) return "No hostile/player nearby to kidnap.";
                beginKidnap(level, s, victim, now);
            }
            case "lift" -> {
                LivingEntity victim = nearestKidnappable(target, level);
                if (victim == null) return "No hostile/player nearby to lift.";
                beginLift(s, victim, now);
            }
            case "antigrief" -> {
                LivingEntity hazard = nearbyHazardEntity(target, level);
                if (hazard == null) return "No lit creeper nearby (any TNT was flung).";
                beginKidnap(level, s, hazard, now);
            }
            case "antifire" -> {
                BlockPos fire = nearbyFire(target, level);
                if (fire == null) return "No fire block nearby.";
                level.removeBlock(fire, false);
                flyTo(s, Vec3.atCenterOf(fire), now, 20);
                level.sendParticles(ParticleTypes.SPLASH, fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
            }
            case "cleanse" -> {
                if (!hasHarmfulEffect(target)) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0)); // give one to purge, for the demo
                }
                cleanse(target, level, s, now);
            }
            case "hunger" -> {
                FoodData fd = target.getFoodData();
                fd.setFoodLevel(Math.min(20, fd.getFoodLevel() + 4));
                fd.setSaturation(fd.getSaturationLevel() + 2.5F);
                flyOrbitOwner(s, now, 24);
            }
            case "heal" -> { target.setHealth(Math.max(1.0F, target.getMaxHealth() * 0.3F)); s.healBlockedUntil = 0; }
            case "knockback", "pingpong" -> { s.pingpongUntil = now + 40; }
            case "fireworks" -> { s.fireworksUntil = now + 60; }
            case "planter" -> { if (!planter(target, level, s, now)) return "No growable plant nearby."; }
            case "push" -> { if (!playWithAnimals(target, level, s, now)) return "No animals nearby to play with."; }
            case "escort" -> beginCarry(target, level, s, now);
            case "guiding" -> { refreshGuidingLight(target, level, s); if (s.guidingTarget == null) return "No group of foes to highlight."; }
            case "blinding" -> {
                LivingEntity foe = nearestKidnappable(target, level);
                if (foe == null) return "No enemy nearby to strike.";
                beginBlinding(s, foe, now);
            }
            case "sacrifice" -> { if (!tryGuardianSacrifice(target)) return "No guardian alive to sacrifice."; }
            case "lightkeeper", "light" -> {
                Allay a = allay(level, s);
                if (a == null) return "No guardian present.";
                s.pendingTorch = null;
                lightKeeperTick(target, level, a, s, now - now % Config.GUARDIAN_TORCH_INTERVAL.get());
                if (s.pendingTorch == null) return "No dark spot needs a torch nearby.";
            }
            case "ore" -> { s.cd.remove("ore"); s.lastOrePos = null; oreHighlight(target, level, s, now); if (s.lastOrePos == null) return "No valuable ore nearby."; }
            default -> {
                return "Unknown guardian event '" + which + "'.";
            }
        }
        return "Guardian Angel: " + which + ".";
    }

    @Nullable
    private static LivingEntity nearestKidnappable(ServerPlayer owner, ServerLevel level) {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(16.0))) {
            if (e == owner || e instanceof Allay || !(e instanceof Enemy || e instanceof Player)) {
                continue;
            }
            double d = e.distanceToSqr(owner);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    /** scrying Mirror: shows whether the once-per-blessing random-blessing gift is still available. */
    @Override
    public java.util.Optional<net.minecraft.network.chat.Component> scryingDetail(ServerPlayer target) {
        return java.util.Optional.of(net.minecraft.network.chat.Component.translatable(
                target.getData(WitchModAttachments.GUARDIAN_GIFT_USED) == 0
                        ? "witchmod.scry.guardian.gift_ready" : "witchmod.scry.guardian.gift_spent"));
    }

    @Override
    public java.util.List<String> debugArgs() {
        return java.util.List.of("dampen", "antifire", "air", "catch", "escort", "phantom", "purge", "cleanse",
                "hunger", "heal", "antigrief", "kidnap", "lift", "knockback", "blinding", "guiding", "sacrifice", "fireworks",
                "arrows", "frantic", "dance", "curiosity", "buffs", "xp", "torch", "lightkeeper", "ore", "mark",
                "durability", "pets", "mining", "restock", "planter", "push", "gift");
    }
}
