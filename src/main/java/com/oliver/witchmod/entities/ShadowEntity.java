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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * the shadow curse's pursuer: a see-through purple copy of its owner that replays their path a few seconds
 * late. it has no ai or physics of its own — {@link com.oliver.witchmod.effects.curses.CurseShadow} places it
 * every tick. it can't be hit, pushed or targeted, isn't saved, and removes itself if nothing drives it.
 */
public final class ShadowEntity extends Mob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** ticks without being placed by the curse before it gives up and vanishes. */
    private static final int ORPHAN_TICKS = 20;

    private long lastDriven;

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    public Optional<UUID> getOwnerId() {
        return entityData.get(OWNER);
    }

    /** called by the curse each tick it places the shadow. */
    public void markDriven() {
        this.lastDriven = level().getGameTime();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // no ai means vanilla never animates the legs; drive the walk cycle from how far it moved.
            float dx = (float) (getX() - xo);
            float dz = (float) (getZ() - zo);
            walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F), 0.4F);
        } else if (level().getGameTime() - lastDriven > ORPHAN_TICKS) {
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }
}
