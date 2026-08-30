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

/**
 * Blessing of Confusion's doppelganger: an exact clone of the caster (skin + nametag; see
 * {@code client/CloneRenderer}) that wanders and does fake actions — attacking nearby monsters, looking
 * around — to blend in with the real you. It has 1 HP, so one hit pops it in a flash of dust. Transient
 * (the blessing owns its lifetime).
 */
public final class CloneEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.STRING);

    private int life = 260;

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
        if (!level().isClientSide && --life <= 0) {
            poof();
            discard();
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
