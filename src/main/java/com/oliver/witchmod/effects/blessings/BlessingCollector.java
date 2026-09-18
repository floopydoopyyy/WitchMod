package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * collector (sacrificial item BARREL): nearby dropped items drift to you, and XP is dragged in from a HUGE
 * radius at huge speed — the XP magnet especially is on steroids. A vacuum for loot and levels.
 */
public final class BlessingCollector extends Effect {
    public BlessingCollector() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> net.minecraft.world.item.Items.BARREL);
    }

    /** not instantly noticeable — you discover it the first time something is pulled toward you. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // discovered on the first pull, in onTick
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        Vec3 me = target.position().add(0, 0.4, 0);
        boolean pulled = false;

        // CROUCH toggles the vacuum way down, so you can crouch to grab specific drops / not hoover a whole floor.
        double crouch = target.isCrouching() ? Config.COLLECTOR_CROUCH_RADIUS_MULT.get() : 1.0;
        int grace = Config.COLLECTOR_GRACE_TICKS.get();

        double itemR = Config.COLLECTOR_ITEM_RADIUS.get() * crouch;
        double itemSpeed = Config.COLLECTOR_ITEM_SPEED.get();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(itemR),
                e -> e.isAlive() && !e.hasPickUpDelay() && e.tickCount >= grace)) { // GRACE: let fresh drops settle first
            drawIn(item, me, itemSpeed);
            pulled = true;
        }

        // XP magnet ON STEROIDS — a huge radius and huge speed so levels come flying in.
        double xpR = Config.COLLECTOR_XP_RADIUS.get() * crouch;
        double xpSpeed = Config.COLLECTOR_XP_SPEED.get();
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, target.getBoundingBox().inflate(xpR),
                ExperienceOrb::isAlive)) {
            drawIn(orb, me, xpSpeed);
            pulled = true;
        }

        if (pulled) {
            markDiscoveredByVictim(target);
        }
    }

    private static void drawIn(net.minecraft.world.entity.Entity e, Vec3 target, double speed) {
        Vec3 dir = target.subtract(e.position());
        double d = dir.length();
        if (d < 0.6) {
            return; // close enough — let vanilla absorb it
        }
        e.setDeltaMovement(dir.scale(speed / d));
        e.hasImpulse = true;
    }
}
