package com.oliver.witchmod.entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import com.oliver.witchmod.items.JarEffects;
import com.oliver.witchmod.items.WitchModItems;

/**
 * A thrown jar — a bespoke splash-potion. It flies like a potion and, on any impact, {@link JarEffects#splash}
 * scatters its stored curses/blessings over an (over-sized) area and paints coloured particles; if it caught
 * nobody, a homing "lash" surges out to punish whoever threw it away.
 */
public final class JarThrowEntity extends ThrowableItemProjectile {
    public JarThrowEntity(EntityType<? extends JarThrowEntity> type, Level level) {
        super(type, level);
    }

    public JarThrowEntity(Level level, LivingEntity thrower) {
        super(WitchModEntities.JAR_THROW.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return WitchModItems.JAR.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05; // a touch floatier than a potion, so the arc reads as a lobbed jar
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            JarEffects.splash((ServerLevel) level(), position(), getItem(), getOwner());
            discard();
        }
    }
}
