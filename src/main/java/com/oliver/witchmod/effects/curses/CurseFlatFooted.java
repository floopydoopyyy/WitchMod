package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * your footsteps are deafening. Every footfall
 * plays the block's own step sound at cartoonish volume, so anyone nearby can hear exactly where you are —
 * even sneaking, where it's only somewhat quieter, never silent. Close enough and the stomps rattle their
 * screen, and hostile mobs pick you up from further off than they should.
 *
 * <p><b>Amplified steps rather than an ambient loop.</b> The curse tracks how far you've walked and, every
 * {@code STEP_DISTANCE} blocks on the ground, plays the step sound of whatever you're standing on — so it's
 * the real, correct footstep, just enormous, which reads as "you" rather than as a generic noise.
 */
public final class CurseFlatFooted extends Effect {
    private static final ResourceLocation DETECTION_ID = EffectUtil.modifierId("curse_flat_footed_detection");

    /** victim -> the walk distance at their last amplified footfall. */
    private static final Map<UUID, Float> LAST_STEP_DIST = new HashMap<>();

    public CurseFlatFooted() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> net.minecraft.world.item.Items.GOAT_HORN);
    }

    /** you find out the first time your own footsteps echo like thunder (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        LAST_STEP_DIST.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        stomp(target);
        if (EffectUtil.every(ticksRemaining, 20)) {
            amplifyMobHearing(target);
        }
    }

    /** emits an amplified footstep once enough ground has been covered since the last one. */
    private void stomp(ServerPlayer target) {
        UUID id = target.getUUID();
        float now = target.walkDist;
        float last = LAST_STEP_DIST.getOrDefault(id, now);
        if (now < last) {
            last = now; // reset (respawn/relog) — walkDist restarts
        }

        if (!target.onGround() || target.isPassenger()) {
            LAST_STEP_DIST.put(id, now);
            return;
        }
        if (now - last < Config.FLATFOOT_STEP_DISTANCE.get()) {
            return;
        }
        LAST_STEP_DIST.put(id, now);

        ServerLevel level = target.serverLevel();
        BlockPos below = target.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            return;
        }
        SoundType soundType = ground.getSoundType(level, below, target);
        SoundEvent step = soundType.getStepSound();

        double volume = Config.FLATFOOT_VOLUME.get();
        if (target.isShiftKeyDown()) {
            volume *= Config.FLATFOOT_SNEAK_VOLUME_MULT.get(); // quieter tiptoe, but you can still be heard
        }
        // broadcast from the victim's feet; the server sends it to every client in earshot.
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                step, SoundSource.PLAYERS, (float) volume, soundType.getPitch() * 0.9F);

        shakeNearbyPlayers(target, level);
        markDiscoveredByVictim(target);
    }

    /** A small screen-shudder for anyone standing close enough to feel the impact. */
    private static void shakeNearbyPlayers(ServerPlayer victim, ServerLevel level) {
        double radius = Config.FLATFOOT_SHAKE_RADIUS.get();
        if (radius <= 0.0) {
            return;
        }
        long until = level.getGameTime() + Config.FLATFOOT_SHAKE_TICKS.get();
        for (ServerPlayer nearby : level.getPlayers(p -> p != victim
                && p.distanceToSqr(victim) <= radius * radius)) {
            nearby.setData(WitchModAttachments.FLAT_FOOTED_SHAKE_END, until);
        }
    }

    /**
     * nearby hostile mobs get a little extra FOLLOW_RANGE — the in-game stand-in for "they heard you coming".
     * A transient, id-guarded modifier so it isn't stacked.
     *
     * <p>It scans a slightly WIDER ring than it boosts, and strips the modifier from any mob in the outer
     * band. Without that, every monster that ever came near a flat-footed player would keep the boost for the
     * session, since a transient modifier doesn't remove itself. Mobs that leave slowly cross the band and
     * get cleaned up; the rest lift on chunk unload anyway (transient modifiers aren't saved).
     */
    private static void amplifyMobHearing(ServerPlayer target) {
        double radius = Config.FLATFOOT_DETECTION_RADIUS.get();
        double bonus = Config.FLATFOOT_DETECTION_BONUS.get();
        if (bonus <= 0.0) {
            return;
        }
        double radiusSq = radius * radius;
        AABB area = target.getBoundingBox().inflate(radius * 1.5);
        for (Mob mob : target.serverLevel().getEntitiesOfClass(Mob.class, area,
                m -> m instanceof Enemy && m.isAlive())) {
            AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange == null) {
                continue;
            }
            boolean inRange = mob.distanceToSqr(target) <= radiusSq;
            if (inRange && !followRange.hasModifier(DETECTION_ID)) {
                followRange.addOrUpdateTransientModifier(new AttributeModifier(
                        DETECTION_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            } else if (!inRange && followRange.hasModifier(DETECTION_ID)) {
                followRange.removeModifier(DETECTION_ID); // wandered out of earshot again
            }
        }
    }
}
