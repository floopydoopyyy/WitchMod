package com.oliver.witchmod.entities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.data.WitchModMobEffects;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.items.JarEffects;

/**
 * the holy hand grenade: an invisible, gravity-bound projectile that arcs + bounces like a real grenade,
 * rolls where it lands, sits as a shimmer of glowing motes, then is blessed from the skies and detonates —
 * a slightly-under-TNT blast that cleanses + protects everyone inside and a wide amethyst-bell-style
 * shockwave (an expanding ring + gradual cleanse) that shields everyone it reaches. kills jar lashes while
 * live. all the shimmer/charge/ring fx are client-rendered from the entity's own tick.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class HolyHandGrenadeEntity extends Projectile {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.86F, 0.35F), 1.2F);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.86F), 1.0F);

    private boolean detonated;
    private boolean landed;

    public HolyHandGrenadeEntity(EntityType<? extends HolyHandGrenadeEntity> type, Level level) {
        super(type, level);
    }

    /** thrown by a player — spawns at their eyes, owned by them (attribution + risk-to-thrower). */
    public HolyHandGrenadeEntity(Level level, LivingEntity thrower) {
        this(WitchModEntities.HOLY_HAND_GRENADE.get(), level);
        setOwner(thrower);
        setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
    }

    /** placed by a dispenser. */
    public HolyHandGrenadeEntity(Level level, double x, double y, double z) {
        this(WitchModEntities.HOLY_HAND_GRENADE.get(), level);
        setPos(x, y, z);
    }

    private static int shimmerTicks() { return Config.GRENADE_SHIMMER_TICKS.get(); }
    private static int chargeTicks() { return Config.GRENADE_CHARGE_TICKS.get(); }

    @Override
    public void tick() {
        super.tick();
        int shimmer = shimmerTicks();
        int detonateAt = shimmer + chargeTicks();

        // charge start: it mostly settles, gathers a chime + burst.
        if (tickCount == shimmer) {
            setDeltaMovement(getDeltaMovement().scale(Config.GRENADE_CHARGE_MOMENTUM_KEEP.get()));
            if (!level().isClientSide()) {
                boolean rare = random.nextInt(Config.GRENADE_HALLELUJAH_RARE_ONE_IN.get()) == 0;
                // the rare variant is a much louder track, so it plays quieter.
                level().playSound(null, getX(), getY(), getZ(),
                        rare ? WitchModSounds.GRENADE_HALLELUJAH_RARE.get() : WitchModSounds.GRENADE_HALLELUJAH.get(),
                        SoundSource.PLAYERS, rare ? 0.5F : 1.1F, 1.0F);
                level().playSound(null, getX(), getY(), getZ(),
                        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.5F);
            }
        }

        if (tickCount < detonateAt) {
            physics();
            if (level().isClientSide()) {
                renderChargeFx(shimmer);
            } else {
                JarEffects.killLashesNear((ServerLevel) level(), position(), Config.GRENADE_LASH_KILL_RADIUS.get());
            }
            return;
        }

        // detonation moment — then the entity lingers to render the expanding ring.
        if (!detonated) {
            detonated = true;
            if (!level().isClientSide()) {
                detonate((ServerLevel) level());
            } else {
                // remember the blast so the client can mute the (unconditional) lightning thunder near it.
                CLIENT_BLASTS.add(new ClientBlast(getX(), getY(), getZ(), level().getGameTime() + 40));
            }
        }
        if (level().isClientSide()) {
            renderShockwaveRing(tickCount - detonateAt);
        } else if (tickCount - detonateAt > Config.GRENADE_SHOCKWAVE_VISUAL_TICKS.get()) {
            discard();
        }
    }

    /** dropped-grenade physics: gravity, a bounce off floors + walls, then roll with ground friction. */
    private void physics() {
        Vec3 vel = getDeltaMovement();
        if (!isNoGravity()) {
            vel = vel.subtract(0, Config.GRENADE_GRAVITY.get(), 0);
        }
        // persist the gravity into deltaMovement BEFORE moving, so downward speed accumulates each tick and it
        // actually arcs down (the ItemEntity pattern) — moving by a local vector alone never accelerates.
        setDeltaMovement(vel);
        Vec3 pre = vel;
        move(MoverType.SELF, getDeltaMovement());
        Vec3 post = getDeltaMovement();
        double nx = post.x, ny = post.y, nz = post.z;
        boolean bounced = false;

        if (verticalCollision && pre.y < 0) {
            // only a real fall bounces, and the rebound is capped so a fast impact never launches it; a soft
            // landing just settles.
            double up = Math.min(-pre.y * Config.GRENADE_BOUNCE.get(), Config.GRENADE_MAX_BOUNCE.get());
            if (pre.y < -0.2 && up >= 0.06) {
                ny = up;
                bounced = true;
            } else {
                ny = 0.0;
            }
            if (!landed && !level().isClientSide()) {
                landed = true; // first ground contact — a second pin clink
                level().playSound(null, getX(), getY(), getZ(), WitchModSounds.GRENADE_PIN.get(),
                        SoundSource.PLAYERS, 0.7F, 1.15F);
            }
            if (bounced && level().isClientSide()) {
                for (int i = 0; i < 4; i++) {
                    level().addParticle(GOLD, getX(), getY() + 0.05, getZ(),
                            (random.nextDouble() - 0.5) * 0.12, 0.03, (random.nextDouble() - 0.5) * 0.12);
                }
            }
        }
        if (horizontalCollision) {
            double wall = Config.GRENADE_WALL_BOUNCE.get();
            if (nx == 0 && Math.abs(pre.x) > 0.02) nx = -pre.x * wall;
            if (nz == 0 && Math.abs(pre.z) > 0.02) nz = -pre.z * wall;
        }
        double hf = (onGround() && !bounced)
                ? level().getBlockState(blockPosition().below()).getFriction(level(), blockPosition().below(), this) * 0.98F
                : 0.98F;
        setDeltaMovement(nx * hf, ny * 0.98, nz * hf);
    }

    /** recent client-side detonation points, so the client can mute the vanilla lightning thunder near them. */
    public record ClientBlast(double x, double y, double z, long expiry) {}
    public static final List<ClientBlast> CLIENT_BLASTS = new ArrayList<>();

    private void detonate(ServerLevel level) {
        double x = getX(), y = getY(), z = getZ();
        level.playSound(null, x, y, z, WitchModSounds.GRENADE_EXPLODE.get(), SoundSource.PLAYERS, 1.2F, 1.0F);

        // a visual-only bolt from the heavens (its thunder is muted client-side near the blast).
        net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        // one-shot shimmer burst + an upward "blessed" column (single packets — cheap); the ring animates client-side.
        level.sendParticles(ParticleTypes.FLASH, x, y + 0.4, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.4, z, 100, 0.6, 0.6, 0.6, 0.4);
        level.sendParticles(GOLD, x, y + 0.4, z, 90, 0.7, 0.7, 0.7, 0.3);
        level.sendParticles(PALE, x, y + 0.4, z, 70, 0.6, 0.6, 0.6, 0.22);
        for (int i = 0; i < 30; i++) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5 + i * 0.25, z, 1, 0.15, 0.1, 0.15, 0.02);
        }

        boolean grief = Config.GRENADE_BLOCK_DAMAGE.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        Level.ExplosionInteraction interaction = grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        // pass THIS as the source entity (it's discarded shortly) so nobody else is excluded from the blast; the
        // custom holy damage source credits the thrower for kill attribution + a themed death message.
        level.explode(this, WitchModDamageTypes.holy(level, getOwner()), null,
                x, y, z, (float) (double) Config.GRENADE_EXPLOSION_POWER.get(), false, interaction);

        // impact camera-shake for everyone nearby.
        double shakeR = Config.GRENADE_SHAKE_RADIUS.get();
        long shakeEnd = level.getGameTime() + Config.GRENADE_SHAKE_TICKS.get();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class,
                AABB.ofSize(position(), shakeR * 2, shakeR * 2, shakeR * 2),
                p -> p.distanceToSqr(x, y, z) <= shakeR * shakeR)) {
            p.setData(WitchModAttachments.GRENADE_SHAKE_END, shakeEnd);
        }

        // inner blast: instant full cleanse + long protection for anyone caught in it.
        double cleanseR = Config.GRENADE_EXPLOSION_CLEANSE_RADIUS.get();
        int protectTicks = Config.GRENADE_EXPLOSION_PROTECT_TICKS.get();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class,
                AABB.ofSize(position(), cleanseR * 2, cleanseR * 2, cleanseR * 2),
                p -> p.isAlive() && p.distanceToSqr(x, y, z) <= cleanseR * cleanseR)) {
            EffectManager.removeAll(p, null);
            p.addEffect(new MobEffectInstance(WitchModMobEffects.PROTECTED, protectTicks, 0, true, false, true));
        }

        // shockwave: a wider, gradual cleanse (the amethyst-bell overtake) + regen + protection.
        double waveR = Config.GRENADE_SHOCKWAVE_RADIUS.get();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class,
                AABB.ofSize(position(), waveR * 2, waveR * 2, waveR * 2),
                p -> p.isAlive() && p.distanceToSqr(x, y, z) <= waveR * waveR)) {
            beginShockwave(level, p);
        }

        // the shockwave also smites undead mobs + one-shots the Killer Bunny anywhere in its reach. the undead
        // damage is pre-divided so the shared holy ×undead hook lands it at exactly the configured amount.
        float undeadHit = (float) (Config.GRENADE_SHOCKWAVE_UNDEAD_DAMAGE.get() / Config.GRENADE_UNDEAD_DAMAGE_MULTIPLIER.get());
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), waveR * 2, waveR * 2, waveR * 2),
                e -> !(e instanceof net.minecraft.world.entity.player.Player) && e.isAlive()
                        && e.distanceToSqr(x, y, z) <= waveR * waveR)) {
            boolean killer = mob instanceof net.minecraft.world.entity.animal.Rabbit rab
                    && rab.getVariant() == net.minecraft.world.entity.animal.Rabbit.Variant.EVIL;
            if (killer || mob.isInvertedHealAndHarm()) {
                mob.hurt(WitchModDamageTypes.holy(level, getOwner()), killer ? 1.0F : undeadHit);
            }
        }
    }

    // --- shockwave: per-player gradual cleanse, ticked server-side (like jar lashes) ---
    private record Cleansing(ServerPlayer player, long end) {}
    private static final List<Cleansing> CLEANSING = new ArrayList<>();

    /** hit by the shockwave: shielded + Regen II now, timers race down over the overtake, then a pulse + full cleanse. */
    private static void beginShockwave(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        int wave = Config.GRENADE_SHOCKWAVE_TICKS.get();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Config.GRENADE_SHOCKWAVE_REGEN_TICKS.get(), 1, true, true, true));
        // protected during the overtake itself as well as after (the final pulse re-applies the full duration).
        p.addEffect(new MobEffectInstance(WitchModMobEffects.PROTECTED, wave + 5, 0, true, false, true));
        p.setData(WitchModAttachments.GRENADE_CLEANSE_END, now + wave);
        CLEANSING.removeIf(c -> c.player == p);
        CLEANSING.add(new Cleansing(p, now + wave));
    }

    /** holy damage smites the undead harder and always one-shots the Killer Bunny. */
    @SubscribeEvent
    static void onHolyDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!event.getSource().is(WitchModDamageTypes.HOLY)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target instanceof net.minecraft.world.entity.animal.Rabbit rabbit
                && rabbit.getVariant() == net.minecraft.world.entity.animal.Rabbit.Variant.EVIL) {
            event.setAmount(target.getMaxHealth() * 1000F); // the Killer Bunny dies no matter what
        } else if (target.isInvertedHealAndHarm()) {
            event.setAmount(event.getAmount() * (float) (double) Config.GRENADE_UNDEAD_DAMAGE_MULTIPLIER.get());
        }
    }

    /** the Monty Python payoff: grenade the Killer Bunny and the thrower mutters the line (quiet if no owner). */
    @SubscribeEvent
    static void onKilledEntity(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!event.getSource().is(WitchModDamageTypes.HOLY)
                || !(event.getEntity() instanceof net.minecraft.world.entity.animal.Rabbit rabbit)
                || rabbit.getVariant() != net.minecraft.world.entity.animal.Rabbit.Variant.EVIL) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer owner && owner.getServer() != null) {
            owner.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable("chat.type.text", owner.getDisplayName(),
                            net.minecraft.network.chat.Component.translatable("witchmod.holy_hand_grenade.lost")), false);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (CLEANSING.isEmpty()) {
            return;
        }
        int drain = Config.GRENADE_SHOCKWAVE_DRAIN.get();
        Iterator<Cleansing> it = CLEANSING.iterator();
        while (it.hasNext()) {
            Cleansing c = it.next();
            ServerPlayer p = c.player;
            if (!p.isAlive() || p.hasDisconnected() || !(p.level() instanceof ServerLevel sl)) {
                it.remove();
                continue;
            }
            long now = sl.getGameTime();
            EffectManager.reduceAllDurations(p, drain); // timers tick down QUICK
            // a gentle rattle during the overtake — but never stomp a bigger impact jolt still playing.
            if (now + 2 > p.getData(WitchModAttachments.GRENADE_SHAKE_END)) {
                p.setData(WitchModAttachments.GRENADE_SHAKE_END, now + 2);
            }
            if (now >= c.end) {
                EffectManager.removeAll(p, null); // the final pulse: fully cleansed (the mod's attachments)
                p.removeAllEffects();             // ...and every vanilla potion effect too
                p.addEffect(new MobEffectInstance(WitchModMobEffects.PROTECTED,
                        Config.GRENADE_SHOCKWAVE_PROTECT_TICKS.get(), 0, true, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        Config.GRENADE_SHOCKWAVE_REGEN_TICKS.get(), 1, true, true, true)); // fresh, since the purge cleared it
                double x = p.getX(), y = p.getY() + 1.0, z = p.getZ();
                sl.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 30, 0.4, 0.6, 0.4, 0.08);
                sl.sendParticles(GOLD, x, y, z, 24, 0.4, 0.6, 0.4, 0.05);
                p.setData(WitchModAttachments.GRENADE_CLEANSE_END, 0L);
                it.remove();
            }
        }
    }

    // --- client fx ------------------------------------------------------------------------------------
    /** the shimmer, and then the pronounced pull-in from around + from the skies as it charges. */
    private void renderChargeFx(int shimmerEnd) {
        RandomSource r = random;
        double cx = getX(), cy = getY() + 0.15, cz = getZ();
        if (tickCount < shimmerEnd) {
            for (int i = 0; i < 3; i++) {
                level().addParticle(GOLD, cx + (r.nextDouble() - 0.5) * 0.7, cy + r.nextDouble() * 0.5,
                        cz + (r.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
            }
            if (r.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.END_ROD, cx + (r.nextDouble() - 0.5) * 0.6, cy + r.nextDouble() * 0.6,
                        cz + (r.nextDouble() - 0.5) * 0.6, 0, 0.005, 0);
            }
            return;
        }
        float p = Mth.clamp((tickCount - shimmerEnd) / (float) chargeTicks(), 0F, 1F);
        int around = Math.round(4 + p * p * 22);
        for (int i = 0; i < around; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rad = 3.2 * (1.0 - p) + 0.5;
            double px = cx + Math.cos(a) * rad, pz = cz + Math.sin(a) * rad, py = cy + (r.nextDouble() - 0.3) * 2.0;
            DustParticleOptions dust = r.nextBoolean() ? GOLD : PALE;
            level().addParticle(dust, px, py, pz, (cx - px) * 0.16, (cy - py) * 0.16, (cz - pz) * 0.16);
        }
        int fromSky = Math.round(2 + p * 14);
        for (int i = 0; i < fromSky; i++) {
            double px = cx + (r.nextDouble() - 0.5) * 1.6;
            double pz = cz + (r.nextDouble() - 0.5) * 1.6;
            double py = cy + 3.0 + r.nextDouble() * (3.0 + p * 3.0);
            level().addParticle(ParticleTypes.END_ROD, px, py, pz, (cx - px) * 0.05, -0.18 - p * 0.15, (cz - pz) * 0.05);
            if (r.nextBoolean()) {
                level().addParticle(PALE, px, py, pz, (cx - px) * 0.05, -0.16 - p * 0.12, (cz - pz) * 0.05);
            }
        }
    }

    /** the expanding ground ring, just like the amethyst bell — but gold/holy, out to the shockwave radius. */
    private void renderShockwaveRing(int t) {
        int visual = Config.GRENADE_SHOCKWAVE_VISUAL_TICKS.get();
        if (t > visual) {
            return;
        }
        double cx = getX(), cy = getY() + 0.2, cz = getZ();
        double radius = Config.GRENADE_SHOCKWAVE_RADIUS.get() * (t / (double) visual);
        int points = (int) (radius * 6) + 10;
        RandomSource r = random;
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2;
            double px = cx + Math.cos(a) * radius, pz = cz + Math.sin(a) * radius;
            level().addParticle(GOLD, px, cy, pz, 0.0, 0.02, 0.0);
            if ((i & 1) == 0) {
                level().addParticle(ParticleTypes.END_ROD, px, cy + 0.1, pz, 0.0, 0.04, 0.0);
            }
            if (r.nextInt(6) == 0) {
                level().addParticle(PALE, px, cy + 0.05, pz, 0.0, 0.06, 0.0);
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        detonated = tag.getBoolean("Detonated");
        landed = tag.getBoolean("Landed");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean("Detonated", detonated);
        tag.putBoolean("Landed", landed);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return false; // it rolls THROUGH things — no projectile hit, the detonation does the work
    }
}
