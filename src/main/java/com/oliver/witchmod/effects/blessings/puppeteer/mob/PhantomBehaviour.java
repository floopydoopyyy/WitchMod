package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/**
 * puppeteer: the phantom. it flies; left-click bites. right-click is a belt you scroll:
 * <ul>
 * <li><b>Dive</b> — tap: a quick dive down your look that bends towards whatever's ahead of it (your client flies it;
 * climbing, it's slower), hits hard on whatever it reaches, and pulls up. what it's aimed at (or hits) is <b>marked</b>:
 * green on your screen, and the phantoms near keep after it, swooping again and again, each on its own rhythm.</li>
 * <li><b>Teleport</b> — hold (15s): you hang still and shake as the dark gathers on you and on your quarry (the nearest
 * player who hasn't slept for three nights) — then you and every phantom near you are gone, and back high in the sky over
 * them. one cooldown shared by every phantom puppet.</li>
 * <li><b>Call</b> — tap: phantoms answer — two if none are near, one if a few are, none past that.</li>
 * </ul>
 * the sleepless are green on your screen alone; your bites and dives hurt them more (and everyone else less).
 * PUPPET_SPELL_SELECT is the belt; ACTION the dive's cooldown, ACTION2 the call's, ACTION3 the teleport's; PUPPET_MARK
 * the mark.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PhantomBehaviour implements PuppetBehaviour {
    public static final PhantomBehaviour INSTANCE = new PhantomBehaviour();

    private PhantomBehaviour() {}

    public static final int DIVE = 0;
    public static final int TELEPORT = 1;
    public static final int CALL = 2;
    private static final String[] BELT = {"phantom_dive", "phantom_teleport", "phantom_call"};

    /** the one Teleport cooldown every phantom puppet shares (key: nil uuid → when it's ready). */
    private static final Map<UUID, Long> TELEPORT_READY = EffectState.sessionConcurrent("puppeteer/phantom/teleport_ready");
    private static final UUID SHARED = new UUID(0L, 0L);

    /** a phantom puppet's mark: its prey, and until when. */
    private record Mark(UUID prey, long until) {}

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

    /** a phantom hunting a mark: until when, when its next swoop goes in, and when the one in flight gives up (0: circling). */
    private static final class Hunt {
        final UUID prey;
        long until;
        long nextSwoop;
        long swoopEnd;

        Hunt(UUID prey, long until, long nextSwoop) {
            this.prey = prey;
            this.until = until;
            this.nextSwoop = nextSwoop;
        }
    }

    private static final Map<UUID, Hunt> FLOCK = new ConcurrentHashMap<>();

    /** a teleport charged to the full: nothing more until let go. */
    private static final Set<UUID> SPENT = EffectState.sessionSet("puppeteer/phantom/spent");

    /** whoever a teleport's gathering on. */
    private static final Map<UUID, UUID> QUARRY = EffectState.sessionConcurrent("puppeteer/phantom/quarry");

    /** when "no one to find" was last said (so a held click doesn't repeat it every tick). */
    private static final Map<UUID, Long> SAID_NONE = new ConcurrentHashMap<>();

    /** whoever the last bite was at (so its damage knows if they're sleepless). */
    private static final Map<UUID, Entity> BIT = EffectState.sessionConcurrent("puppeteer/phantom/bit");

    public static int selection(Player player) {
        return Mth.clamp(player.getData(WitchModAttachments.PUPPET_SPELL_SELECT), 0, BELT.length - 1);
    }

    /** a phantom puppet gathering a teleport (both sides): it hangs still and shakes. */
    public static boolean teleportCharging(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PHANTOM && selection(player) == TELEPORT
                && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    // --- the sleepless -----------------------------------------------------------------------------------

    /** whether {@code player} is one a phantom would come for (synced, for the green). */
    public static boolean sleepless(Entity e) {
        return e instanceof Player p && p.hasData(WitchModAttachments.PUPPET_INSOMNIAC) && p.getData(WitchModAttachments.PUPPET_INSOMNIAC);
    }

    /** every few seconds, everyone's sleeplessness (vanilla's test: three days and nights, in survival); the marks and hunts. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) {
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                boolean awake = !p.isSpectator() && !p.isCreative()
                        && p.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= Config.PUPPETEER_PHANTOM_SLEEPLESS_TICKS.get();
                if (awake != p.getData(WitchModAttachments.PUPPET_INSOMNIAC)) {
                    p.setData(WitchModAttachments.PUPPET_INSOMNIAC, awake);
                }
            }
        }
        if (!MARKS.isEmpty()) {
            tickMarks(event.getServer());
        }
        if (!FLOCK.isEmpty()) {
            tickFlock(event.getServer());
        }
    }

    /** a phantom puppet's damage: more to the sleepless, less to anyone else. */
    private static float scaled(Entity victim, float amount) {
        return sleepless(victim) ? amount * (1.0F + Config.PUPPETEER_PHANTOM_SLEEPLESS_BONUS.get().floatValue())
                : amount * Config.PUPPETEER_PHANTOM_OTHERS_MULTIPLIER.get().floatValue();
    }

    // --- dive and the mark -------------------------------------------------------------------------------

    private static void startDive(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (LUNGES.containsKey(player.getUUID()) || !ready(player)) {
            return;
        }
        cooldown(player, Config.PUPPETEER_PHANTOM_DIVE_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.5F, 1.0F);
        Puppets.countAction(player);
        LivingEntity prey = beamHit(player, 64.0, 2.0).entity();
        if (prey != null) {
            mark(player, prey);
        }
    }

    /**
     * mark {@code prey}: green on your screen, and every phantom near hunts it for the mark's length — a first swoop a
     * moment after yours (each its own moment), then again and again. one already after it keeps its rhythm.
     */
    private static void mark(ServerPlayer player, LivingEntity prey) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long until = now + Config.PUPPETEER_PHANTOM_MARK_TICKS.get();
        MARKS.put(player.getUUID(), new Mark(prey.getUUID(), until));
        player.setData(WitchModAttachments.PUPPET_MARK, prey.getId());
        double r = Config.PUPPETEER_PHANTOM_FLOCK_RADIUS.get();
        int spread = Math.max(1, Config.PUPPETEER_PHANTOM_FLOCK_DELAY_TICKS.get());
        for (Phantom p : level.getEntitiesOfClass(Phantom.class, player.getBoundingBox().inflate(r), x -> x.isAlive() && !x.isNoAi())) {
            Hunt hunt = FLOCK.get(p.getUUID());
            if (hunt != null && hunt.prey.equals(prey.getUUID())) {
                hunt.until = until;
                continue;
            }
            FLOCK.put(p.getUUID(), new Hunt(prey.getUUID(), until, now + 3 + level.random.nextInt(spread)));
            p.setTarget(prey);
        }
    }

    /** marks run out (or their prey's gone): the green goes. */
    private static void tickMarks(net.minecraft.server.MinecraftServer server) {
        MARKS.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                return true;
            }
            boolean over = p.level().getGameTime() >= e.getValue().until()
                    || !(p.serverLevel().getEntity(e.getValue().prey()) instanceof LivingEntity prey && prey.isAlive());
            if (over && p.getData(WitchModAttachments.PUPPET_MARK) != -1) {
                p.setData(WitchModAttachments.PUPPET_MARK, -1);
            }
            return over;
        });
    }

    /** the hunts: each phantom kept on its mark (vanilla drops its target after every sweep), swooping on its own rhythm. */
    private static void tickFlock(net.minecraft.server.MinecraftServer server) {
        FLOCK.entrySet().removeIf(e -> {
            Hunt hunt = e.getValue();
            for (ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(e.getKey()) instanceof Phantom phantom)) {
                    continue;
                }
                long now = level.getGameTime();
                if (now >= hunt.until || !phantom.isAlive() || phantom.isNoAi()
                        || !(level.getEntity(hunt.prey) instanceof LivingEntity prey) || !prey.isAlive()
                        || (prey instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) {
                    return true;
                }
                hunt(level, phantom, prey, hunt, now);
                return false;
            }
            return true; // gone
        });
    }

    private static long swoopGap(ServerLevel level) {
        int gap = Config.PUPPETEER_PHANTOM_MARK_SWOOP_GAP_TICKS.get();
        return gap + level.random.nextInt(Math.max(1, gap * 3 / 5));
    }

    /** one hunting phantom's tick: circling (vanilla's), or a swoop straight in — a bite on contact, then it pulls away. */
    private static void hunt(ServerLevel level, Phantom phantom, LivingEntity prey, Hunt hunt, long now) {
        if (phantom.getTarget() != prey) {
            phantom.setTarget(prey);
        }
        if (hunt.swoopEnd != 0L && now >= hunt.swoopEnd) {
            hunt.swoopEnd = 0L; // missed: round again, a little sooner
            hunt.nextSwoop = now + swoopGap(level) / 2;
        }
        if (hunt.swoopEnd == 0L) {
            if (now < hunt.nextSwoop) {
                return;
            }
            hunt.swoopEnd = now + 60;
            level.playSound(null, phantom.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.2F,
                    0.9F + level.random.nextFloat() * 0.2F);
        }
        Vec3 at = new Vec3(prey.getX(), prey.getY(0.5), prey.getZ());
        ((com.oliver.witchmod.mixin.PhantomAccessor) phantom).witchmodSetMoveTarget(at);
        Vec3 to = at.subtract(phantom.position());
        if (to.lengthSqr() > 1.0E-4) {
            phantom.setDeltaMovement(to.normalize().scale(Config.PUPPETEER_PHANTOM_MARK_SWOOP_SPEED.get()));
            phantom.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
            phantom.yBodyRot = phantom.getYRot();
        }
        if (phantom.getBoundingBox().inflate(0.3).intersects(prey.getBoundingBox())) {
            phantom.doHurtTarget(prey);
            level.levelEvent(1039, phantom.blockPosition(), 0); // the bite
            phantom.setDeltaMovement(phantom.getDeltaMovement().multiply(0.4, 0.0, 0.4).add(0.0, 0.5, 0.0));
            hunt.swoopEnd = 0L;
            hunt.nextSwoop = now + swoopGap(level);
        } else if (phantom.horizontalCollision) {
            hunt.swoopEnd = 0L;
            hunt.nextSwoop = now + 20;
        }
    }

    /** the dive connects with the first thing it reaches (a generous reach): big damage, the mark, and you pull up. */
    private static void tickDive(ServerPlayer player, Lunge dive) {
        if (player.onGround() && player.level().getGameTime() - dive.start() > 2) {
            endLunge(player); // pulled up short — into the ground
            return;
        }
        double reach = Config.PUPPETEER_PHANTOM_DIVE_HIT_RADIUS.get();
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            SPECIAL_DAMAGE.add(player.getUUID());
            try {
                e.hurt(player.damageSources().playerAttack(player), scaled(e, Config.PUPPETEER_PHANTOM_DIVE_DAMAGE.get().floatValue()));
            } finally {
                SPECIAL_DAMAGE.remove(player.getUUID());
            }
            mark(player, e);
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.2F, 0.9F);
            // pull up out of the dive, like a phantom peeling away after a strike.
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, 0.7, 0.0));
            player.hurtMarked = true;
            endLunge(player);
            return;
        }
    }

    // --- teleport ---------------------------------------------------------------------------------------

    /** when the shared Teleport is ready again. */
    private static long teleportReady() {
        return TELEPORT_READY.getOrDefault(SHARED, 0L);
    }

    /** the nearest sleepless player (not you, same world), or null. */
    @Nullable
    private static ServerPlayer nearestSleepless(ServerPlayer player) {
        ServerPlayer best = null;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && sleepless(other) && other.isAlive()
                    && (best == null || other.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = other;
            }
        }
        return best;
    }

    /** the teleport's quarry: the one it's been gathering on while they're still fair game, else the nearest sleepless. */
    @Nullable
    private static ServerPlayer quarry(ServerPlayer player) {
        UUID kept = QUARRY.get(player.getUUID());
        if (kept != null && player.serverLevel().getPlayerByUUID(kept) instanceof ServerPlayer q && q.isAlive() && sleepless(q)) {
            return q;
        }
        ServerPlayer q = nearestSleepless(player);
        if (q == null) {
            QUARRY.remove(player.getUUID());
        } else {
            QUARRY.put(player.getUUID(), q.getUUID());
        }
        return q;
    }

    /**
     * the gathering: souls drawn in on a tightening spiral round you, soul fire at the end; over your quarry, souls drift
     * down out of the dark, thicker as it builds, and the phantoms' cry now and then.
     */
    private static void teleportFx(ServerPlayer player, int hold, int full) {
        ServerLevel level = player.serverLevel();
        float share = Math.min(1.0F, hold / (float) full);
        double a = player.tickCount * 0.45;
        double rr = 2.2 - 1.6 * share;
        for (int k = 0; k < 2; k++) {
            double ang = a + k * Math.PI;
            level.sendParticles(ParticleTypes.SOUL, player.getX() + Math.cos(ang) * rr, player.getY() + 0.2 + share * 1.2,
                    player.getZ() + Math.sin(ang) * rr, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.4, 0.3, 0.4, 0.01);
        }
        if (share > 0.75F && player.tickCount % 2 == 0) {
            double ang = level.random.nextDouble() * Mth.TWO_PI;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX() + Math.cos(ang) * 0.8, player.getY() + 0.3,
                    player.getZ() + Math.sin(ang) * 0.8, 1, 0.0, 0.05, 0.0, 0.01);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.6, player.getZ(), 2, 0.3, 0.3, 0.3, 0.05);
        }
        if (hold % 60 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.5F + 0.5F * share);
        }
        ServerPlayer q = quarry(player);
        if (q == null) {
            return;
        }
        ServerLevel there = q.serverLevel();
        int every = Math.max(1, Math.round(6.0F - 5.0F * share));
        if (player.tickCount % every == 0) {
            double ang = there.random.nextDouble() * Mth.TWO_PI;
            double ring = 1.0 + there.random.nextDouble() * 1.8;
            there.sendParticles(ParticleTypes.SOUL, q.getX() + Math.cos(ang) * ring, q.getY() + 2.4 + there.random.nextDouble(),
                    q.getZ() + Math.sin(ang) * ring, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (share > 0.5F && player.tickCount % 4 == 0) {
            double ang = player.tickCount * 0.3;
            there.sendParticles(ParticleTypes.SMOKE, q.getX() + Math.cos(ang) * 1.2, q.getY() + 0.1, q.getZ() + Math.sin(ang) * 1.2,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        if (hold % 80 == 40) {
            there.playSound(null, q.getX(), q.getY() + 6.0, q.getZ(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE,
                    0.6F + share, 0.7F);
        }
    }

    /**
     * gone in a burst — you and the flock — and back out of the dark high over your quarry, a ring of you, with the
     * phantoms' cry falling on them.
     */
    private static void teleport(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ServerPlayer quarry = quarry(player);
        QUARRY.remove(player.getUUID());
        if (quarry == null) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.phantom_no_quarry").withStyle(ChatFormatting.GRAY), true);
            return; // no one to find: nothing spent
        }
        long ready = level.getGameTime() + Config.PUPPETEER_PHANTOM_TELEPORT_COOLDOWN_TICKS.get();
        TELEPORT_READY.put(SHARED, ready);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (PuppetType.byId(p.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PHANTOM) {
                p.setData(WitchModAttachments.PUPPET_ACTION3_READY, ready); // (it's every phantom's cooldown)
            }
        }
        double r = Config.PUPPETEER_PHANTOM_FLOCK_RADIUS.get();
        List<Phantom> flock = level.getEntitiesOfClass(Phantom.class, player.getBoundingBox().inflate(r), x -> x.isAlive());
        double height = Config.PUPPETEER_PHANTOM_TELEPORT_HEIGHT.get();
        List<Entity> going = new java.util.ArrayList<>();
        going.add(player);
        going.addAll(flock);
        int i = 0;
        for (Entity e : going) {
            vanishFx(level, e.position());
            double a = Mth.TWO_PI * i / going.size() + level.random.nextDouble() * 0.4;
            double ring = 6.0 + level.random.nextDouble() * 6.0;
            double x = quarry.getX() + Math.cos(a) * ring;
            double z = quarry.getZ() + Math.sin(a) * ring;
            // in the open sky over them — above the ground there, even if they're down a hole (under a roof: just above them)
            double y = level.dimensionType().hasCeiling() ? quarry.getY() + 4.0
                    : Math.max(quarry.getY() + height, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                            Mth.floor(x), Mth.floor(z)) + 4.0) + level.random.nextDouble() * 6.0;
            Vec3 to = new Vec3(x, y, z);
            e.teleportTo(to.x, to.y, to.z);
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Phantom phantom) {
                phantom.setTarget(quarry);
            }
            appearFx(level, to);
            i++;
        }
        player.resetFallDistance();
        level.playSound(null, quarry.getX(), quarry.getY() + height * 0.5, quarry.getZ(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.6F);
        level.playSound(null, quarry.getX(), quarry.getY() + height * 0.5, quarry.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    private static void vanishFx(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.4, at.z, 16, 0.5, 0.3, 0.5, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.4, at.z, 10, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private static void appearFx(ServerLevel level, Vec3 at) {
        // out of the dark: a falling trail of soul fire and smoke, then the flash of it
        for (int k = 0; k < 6; k++) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 3.0 - k * 0.5, at.z, 2, 0.15, 0.1, 0.15, 0.01);
        }
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 14, 0.6, 0.4, 0.6, 0.05);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 20, 0.5, 0.3, 0.5, 0.03);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // --- call --------------------------------------------------------------------------------------------

    /** phantoms answer from the dark: two with none near, one with a few, none past that. */
    private static void call(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now < player.getData(WitchModAttachments.PUPPET_ACTION2_READY)) {
            return;
        }
        double r = Config.PUPPETEER_PHANTOM_FLOCK_RADIUS.get();
        int have = level.getEntitiesOfClass(Phantom.class, player.getBoundingBox().inflate(r), Phantom::isAlive).size();
        int want = have == 0 ? 2 : have <= Config.PUPPETEER_PHANTOM_CALL_FEW.get() ? 1 : 0;
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, now + Config.PUPPETEER_PHANTOM_CALL_COOLDOWN_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 2.5F, 0.7F);
        for (int k = 0; k < want; k++) {
            Phantom phantom = EntityType.PHANTOM.create(level);
            if (phantom == null) {
                break;
            }
            Vec3 at = player.position().add((level.random.nextDouble() - 0.5) * 10.0, 2.0 + level.random.nextDouble() * 4.0,
                    (level.random.nextDouble() - 0.5) * 10.0);
            phantom.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
            phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(phantom.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(phantom);
            appearFx(level, at);
        }
        Puppets.countAction(player);
    }

    // --- the belt ----------------------------------------------------------------------------------------

    @Override
    public boolean scrolls(PuppetType type) {
        return true;
    }

    @Override
    public void scroll(ServerPlayer player, PuppetType type, int dir) {
        if (player.getData(WitchModAttachments.PUPPET_FUSE) > 0) {
            return;
        }
        int sel = Math.floorMod(selection(player) + Integer.signum(dir), BELT.length);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, sel);
        player.playNotifySound(SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5F, 1.2F + sel * 0.2F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.phantom_select",
                Component.translatable("witchmod.puppeteer.action." + BELT[sel])).withStyle(ChatFormatting.AQUA), true);
    }

    /** only Teleport charges (the rest go off on the click) — and only with someone to find. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        if (selection(player) != TELEPORT || SPENT.contains(player.getUUID()) || player.level().getGameTime() < teleportReady()) {
            return false;
        }
        if (quarry(player) != null) {
            return true;
        }
        long now = player.level().getGameTime();
        if (now - SAID_NONE.getOrDefault(player.getUUID(), -100L) >= 40L) {
            SAID_NONE.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.phantom_no_quarry").withStyle(ChatFormatting.GRAY), true);
        }
        return false;
    }

    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.5F);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    /** let go: a dive or a call; a teleport let go early is called off. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (SPENT.remove(player.getUUID())) {
            return;
        }
        switch (selection(player)) {
            case DIVE -> startDive(player);
            case CALL -> call(player);
            default -> {
            }
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        Lunges.tickLunge(player, type); // the dive
        Holds.tickHold(player, type);
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (selection(player) == TELEPORT && hold > 0) {
            int full = Math.max(1, Config.PUPPETEER_PHANTOM_TELEPORT_CHARGE_TICKS.get());
            teleportFx(player, hold, full);
            if (hold >= full) {
                player.setData(WitchModAttachments.PUPPET_FUSE, 0);
                SPENT.add(player.getUUID());
                teleport(player);
            }
        }
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        tickDive(player, lunge);
    }

    /** a bite: more to the sleepless. */
    @Override
    public boolean takesOverMelee(ServerPlayer player, PuppetType type, Entity target) {
        BIT.put(player.getUUID(), target);
        return false;
    }

    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        Entity bit = BIT.get(player.getUUID());
        return bit == null ? type.attackDamage() : scaled(bit, type.attackDamage());
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, DIVE);
        player.setData(WitchModAttachments.PUPPET_ACTION3_READY, teleportReady());
        player.setData(WitchModAttachments.PUPPET_MARK, -1);
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        SPENT.remove(player.getUUID());
        BIT.remove(player.getUUID());
        QUARRY.remove(player.getUUID());
        SAID_NONE.remove(player.getUUID());
        MARKS.remove(player.getUUID()); // (the flock still finishes its hunt)
        if (player.getData(WitchModAttachments.PUPPET_MARK) != -1) {
            player.setData(WitchModAttachments.PUPPET_MARK, -1);
        }
    }

    /** the hud's bar: the teleport gathering. */
    public static float hudProgress(Player player) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (hold <= 0 || selection(player) != TELEPORT) {
            return -1.0F;
        }
        try {
            return Math.min(1.0F, hold / (float) Math.max(1, Config.PUPPETEER_PHANTOM_TELEPORT_CHARGE_TICKS.get()));
        } catch (IllegalStateException e) {
            return -1.0F;
        }
    }

    /** the belt (scroll, then its move), the bite, and its flight. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        int sel = selection(player);
        out.add(List.of(tap(Key.SCROLL)), Component.translatable("witchmod.puppeteer.phantom_select",
                Component.translatable("witchmod.puppeteer.action." + BELT[sel])), 0L);
        long ready = switch (sel) {
            case DIVE -> PuppetControls.ready(player);
            case CALL -> PuppetControls.ready2(player);
            default -> player.getData(WitchModAttachments.PUPPET_ACTION3_READY);
        };
        out.add(List.of(sel == TELEPORT ? hold(Key.USE) : tap(Key.USE)), Component.translatable("witchmod.puppeteer.action." + BELT[sel]), ready);
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.bite", 0L);
        out.add(doubleTap(Key.JUMP), "witchmod.puppeteer.action.fly", 0L);
    }
}
