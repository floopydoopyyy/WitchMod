package com.oliver.witchmod.entities;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * confusion's doppelganger — an exact clone of the caster (skin + nametag) that wanders and fakes actions to
 * blend in. 1 hp, so one hit pops it to dust. transient (the blessing owns its lifetime).
 */
public final class CloneEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.STRING);

    private int life = 260;
    @org.jetbrains.annotations.Nullable
    private Vec3 flyTarget;   // bat-mode wander point
    private int flyRepick;

    public CloneEntity(EntityType<? extends CloneEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
    }

    public void setOwnerInfo(Player owner) {
        String name = owner.getGameProfile().getName();
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(OWNER_NAME, name);
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
    }

    public Optional<UUID> getOwnerId() {
        return entityData.get(OWNER);
    }

    public void setLifetime(int ticks) {
        this.life = ticks;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // the blessing owns it — no orphans across a reload
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)          // one hit pops it
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0)); // "run around"
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, true)); // "attack nearby mobs"
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // wild-decoys: mirror the owner's disguise MOVEMENT — a bat clone flies, a fish clone flops.
        int form = ownerForm();
        if (form == 3) {
            batFlight();
        } else {
            if (isNoGravity()) {
                setNoGravity(false);
            }
            if (form == 7) {
                fishFlop();
            }
        }
        if (--life <= 0) {
            poof();
            discard();
        }
    }

    /** the owner's current disguise type, or -1 (server-side lookup). */
    private int ownerForm() {
        UUID owner = getOwnerId().orElse(null);
        Player p = owner == null ? null : level().getPlayerByUUID(owner);
        return p == null ? -1 : p.getData(WitchModAttachments.DISGUISE_TYPE);
    }

    /** flit around the owner like a bat — a swarm of decoys in the air. */
    private void batFlight() {
        setNoGravity(true);
        setTarget(null);
        getNavigation().stop();
        UUID owner = getOwnerId().orElse(null);
        Player p = owner == null ? null : level().getPlayerByUUID(owner);
        Vec3 anchor = p != null ? p.position() : position();
        if (flyTarget == null || --flyRepick <= 0 || distanceToSqr(flyTarget) < 4.0) {
            double ang = getRandom().nextDouble() * Math.PI * 2;
            double r = 2.0 + getRandom().nextDouble() * 5.0;
            flyTarget = new Vec3(anchor.x + Math.cos(ang) * r,
                    anchor.y + 1.0 + getRandom().nextDouble() * 3.0, anchor.z + Math.sin(ang) * r);
            flyRepick = 30 + getRandom().nextInt(40);
        }
        Vec3 dir = flyTarget.subtract(position());
        if (dir.lengthSqr() > 1.0e-4) {
            dir = dir.normalize();
        }
        double speed = Config.CONFUSION_BAT_FLY_SPEED.get();
        Vec3 wanted = dir.scale(speed).add(0, Math.sin(tickCount * 0.3) * 0.02, 0);
        setDeltaMovement(getDeltaMovement().scale(0.8).add(wanted.scale(0.2)));
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1.0e-4) {
            float yaw = (float) (Math.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        hasImpulse = true;
    }

    /** flop about out of water like a landed fish (vanilla cadence — hops the instant it lands). */
    private void fishFlop() {
        if (isInWater() || !onGround()) {
            return;
        }
        double power = Config.FISH_FLOP_POWER.get();
        setDeltaMovement(getDeltaMovement().add((getRandom().nextFloat() * 2.0F - 1.0F) * 0.05, power,
                (getRandom().nextFloat() * 2.0F - 1.0F) * 0.05));
        hasImpulse = true;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.playSound(null, blockPosition(), SoundEvents.COD_FLOP, SoundSource.NEUTRAL,
                    0.7F, 0.9F + getRandom().nextFloat() * 0.2F);
        }
    }

    /**
     * No combat here — ANY hit just instantly pops it into dust (no damage number, knockback, or hurt flash).
     * Returning false means the attacker's swing simply passes through as the clone vanishes.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            poof();
            discard();
        }
        return false;
    }

    private void poof() {
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    getX(), getY() + 0.9, getZ(), 24, 0.35, 0.6, 0.35, 0.03);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    getX(), getY() + 0.9, getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            // A subtle "pff" of dust — deliberately NOT a teleport-y sound.
            sl.playSound(null, blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }
}
