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
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

/**
 * the spaghetti man (the dweller curse) — a tall silent stalker driven entirely by {@code CurseTheDweller}
 * (no ai). immortal, transient, phases through walls, and only ever rendered for its victim (synced uuid);
 * everyone else sees just the aftermath.
 */
public final class SpaghettiManEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> VICTIM =
            SynchedEntityData.defineId(SpaghettiManEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public SpaghettiManEntity(EntityType<? extends SpaghettiManEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        setCustomNameVisible(false);
        this.noPhysics = true; // it moves through walls when it wants to
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
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
    protected void registerGoals() {
        // none — the curse drives everything
    }

    @Override
    public void tick() {
        super.tick();
        // it CANNOT use boats — and any boat it so much as touches during a HUNT is torn apart in a blast (a boat
        // is no escape). Gated on physics/chase mode (!noPhysics): while it's the floaty watch/bed illusion it
        // never explodes anything, so a bed vigil can never set off a blast.
        if (!level().isClientSide && !this.noPhysics) {
            for (Boat boat : level().getEntitiesOfClass(Boat.class, getBoundingBox().inflate(0.85))) {
                level().explode(this, boat.getX(), boat.getY() + 0.3, boat.getZ(), 2.5F, Level.ExplosionInteraction.MOB);
                boat.discard();
            }
        }
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false; // never boards anything — boats especially
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // unstoppable
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
    protected void doPush(Entity entity) {
        // never shoved
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
