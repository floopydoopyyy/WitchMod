package com.oliver.witchmod.entities;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import com.oliver.witchmod.Config;

/** puppeteer (chicken): a charged egg that blows up where it lands — a bit gentler than tnt; respects mobGriefing. */
public final class ExplosiveEggEntity extends ThrowableItemProjectile {
    public ExplosiveEggEntity(EntityType<? extends ExplosiveEggEntity> type, Level level) {
        super(type, level);
    }

    public ExplosiveEggEntity(Level level, LivingEntity owner) {
        super(WitchModEntities.EXPLOSIVE_EGG.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.EGG;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ItemParticleOption shell = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EGG));
            for (int i = 0; i < 8; i++) {
                level().addParticle(shell, getX(), getY(), getZ(), (random.nextFloat() - 0.5) * 0.08,
                        (random.nextFloat() - 0.5) * 0.08, (random.nextFloat() - 0.5) * 0.08);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) 3);
            level().explode(this, getX(), getY(), getZ(), Config.PUPPETEER_CHICKEN_EGG_POWER.get().floatValue(),
                    Level.ExplosionInteraction.MOB);
            discard();
        }
    }
}
