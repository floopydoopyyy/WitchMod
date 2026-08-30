package com.oliver.witchmod.entities;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A pair of disembodied glowing eyes in the dark (The Dweller's "watchers" event). The body is invisible —
 * only the emissive eyes render ({@link com.oliver.witchmod.client.WatcherEyesEyesLayer}) — and, like the Mind
 * Dweller, it exists ONLY in the victim's world (render cancelled for everyone else, keyed off the synced
 * victim UUID). No AI; the curse owns its position.
 */
public final class WatcherEyesEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> VICTIM =
            SynchedEntityData.defineId(WatcherEyesEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public WatcherEyesEntity(EntityType<? extends WatcherEyesEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        this.noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VICTIM, Optional.empty());
    }

    public void setVictim(UUID id) {
        this.entityData.set(VICTIM, Optional.ofNullable(id));
    }

    public Optional<UUID> getVictim() {
        return this.entityData.get(VICTIM);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
