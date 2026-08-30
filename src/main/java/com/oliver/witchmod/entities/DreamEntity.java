package com.oliver.witchmod.entities;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.WitchModDamageTypes;

/**
 * The Cutaway Gag's "Dream" — a player MIMIC. It jogs up at player-like speed, punches the victim a few times
 * with the custom {@code witchmod:dream} damage type (for a bespoke death message), and is removed with the
 * cutaway. Rendered as a real {@code PlayerModel} wearing the dream skin ({@code client/DreamRenderer}), so at
 * a glance it just reads as some random player who showed up to hit you.
 */
public final class DreamEntity extends PathfinderMob {
    @Nullable
    private UUID victimId;

    public DreamEntity(EntityType<? extends DreamEntity> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("Dream"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    public void setVictim(LivingEntity victim) {
        this.victimId = victim.getUUID();
        setTarget(victim);
    }

    /** Transient — the gag owns its lifetime, so a reload leaves no orphan. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32) // quick, player-like
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Keep hunting the assigned victim even if a goal clears the target.
        if (victimId != null && (getTarget() == null || !getTarget().isAlive())
                && level() instanceof ServerLevel sl
                && sl.getEntity(victimId) instanceof LivingEntity v && v.isAlive()) {
            setTarget(v);
        }
    }

    /** Its punch lands the custom Dream damage (so the death message is bespoke) rather than plain mob damage. */
    @Override
    public boolean doHurtTarget(Entity target) {
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hurt = target.hurt(WitchModDamageTypes.dream(level(), this), dmg);
        if (hurt) {
            swing(InteractionHand.MAIN_HAND);
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return hurt;
    }
}
