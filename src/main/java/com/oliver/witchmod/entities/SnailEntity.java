package com.oliver.witchmod.entities;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The immortal Snail (Snail curse). A dumb, unkillable puppet — the curse ({@code CurseSnail}) owns its
 * position entirely and teleports it each tick, so it has NO AI, no gravity, and can't be hurt or shoved. It
 * only exists while the curse materialises it near the victim; when the victim is far the curse despawns it and
 * keeps advancing a VIRTUAL position instead, so nothing is loaded constantly.
 */
public final class SnailEntity extends PathfinderMob {
    public SnailEntity(EntityType<? extends SnailEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true); // the curse plays its sounds itself
        setInvulnerable(true);
        setCustomNameVisible(false);
        this.noPhysics = true; // slides straight through walls — boxing yourself in won't save you
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void registerGoals() {
        // none — the curse drives it
    }

    /** Transient: never written to disk, so a reload leaves no orphan (the curse re-materialises it). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // --- Immortal + inert ---------------------------------------------------------------------------------
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // you cannot kill the snail
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
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        // it does not get shoved off course
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // the curse manages its lifetime, not vanilla despawn rules
    }
}
