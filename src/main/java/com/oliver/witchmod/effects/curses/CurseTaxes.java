package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.TaxBank;
import com.oliver.witchmod.data.TaxValues;
import com.oliver.witchmod.data.WitchModTags;
import com.oliver.witchmod.entities.TaxManEntity;
import com.oliver.witchmod.entities.WitchModEntities;

/**
 * Somebody has noticed how much you're carrying (master-spec Taxes). While the curse is active the victim is
 * periodically assessed — chests nearby, items on the floor, and what's in their pockets — and once there's
 * enough within reach to be worth the paperwork, the {@link TaxManEntity} turns up and starts confiscating.
 *
 * <p><b>The trigger IS the counterplay.</b> Nothing happens at all until {@code MIN_VALUE_TRIGGER} valuables
 * are within {@code SCAN_RADIUS}, so a victim who keeps their hoard somewhere they aren't, and doesn't walk
 * around wearing their net worth, genuinely never sees him. That's the intended answer to the curse, and it
 * costs them real convenience — which is the point.
 *
 * <p>Valuables are the datapack tag {@link WitchModTags#VALUABLES} — every ore line and its ingots, gems and
 * blocks, deliberately excluding redstone and coal. Being a tag rather than a hardcoded list means modded
 * ores can be added without touching code.
 *
 * <p>After a visit he goes on {@code COOLDOWN} but is not spent: the curse keeps assessing, and he will come
 * back. Everything he takes goes into the world-persistent {@code TaxBank} for the Tax Man BLESSING to hand
 * back out later, possibly to somebody else entirely.
 */
public final class CurseTaxes extends Effect {
    /** victim -> game tick the Tax Man may next appear. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    /** victim -> the Tax Man currently visiting them, if any. */
    private static final Map<UUID, TaxManEntity> ACTIVE = new HashMap<>();

    public CurseTaxes() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 80, () -> Items.EMERALD);
    }

    /** You find out when a man in a suit materialises next to your chests (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        TaxManEntity visiting = ACTIVE.remove(target.getUUID());
        if (visiting != null && visiting.isAlive()) {
            visiting.dismiss(); // curse cured out from under him — he packs up and goes
        }
        COOLDOWN.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.TAXES_CHECK_INTERVAL.get())) {
            return;
        }
        UUID id = target.getUUID();

        // Still being audited? Nothing to do but let him work.
        TaxManEntity visiting = ACTIVE.get(id);
        if (visiting != null) {
            if (visiting.isAlive()) {
                return;
            }
            ACTIVE.remove(id);
            COOLDOWN.put(id, target.level().getGameTime() + Config.TAXES_COOLDOWN.get());
            return;
        }

        Long until = COOLDOWN.get(id);
        long now = target.level().getGameTime();
        if (until != null && now < until) {
            return;
        }
        if (TaxBank.get(target.server).isFull()) {
            return; // bank at its memory ceiling — he stops taking rather than voiding anything
        }
        if (valueNear(target) < Config.TAXES_MIN_VALUE_TRIGGER.get()) {
            return; // not worth the paperwork — this is the counterplay working
        }
        summon(target);
    }

    /** Everything within reach that he'd want: chests, the floor, and the victim's own pockets. */
    private static int valueNear(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        int radius = Config.TAXES_SCAN_RADIUS.get();
        int total = countIn(target.getInventory());

        AABB area = target.getBoundingBox().inflate(radius);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.getItem().is(WitchModTags.VALUABLES)) {
                total += TaxValues.valueOf(item.getItem());
            }
        }

        BlockPos origin = target.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius / 2, -radius),
                origin.offset(radius, radius / 2, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                total += countIn(container);
            }
        }
        return total;
    }

    private static int countIn(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(WitchModTags.VALUABLES)) {
                total += TaxValues.valueOf(stack);
            }
        }
        return total;
    }

    private void summon(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        TaxManEntity taxMan = WitchModEntities.TAX_MAN.get().create(level);
        if (taxMan == null) {
            return;
        }
        Vec3 spot = findStandingSpot(target);
        taxMan.moveTo(spot.x, spot.y, spot.z, target.getYRot() + 180.0F, 0.0F);
        taxMan.assignVictim(target);
        level.addFreshEntity(taxMan);

        ACTIVE.put(target.getUUID(), taxMan);
        markDiscoveredByVictim(target);
    }

    /** A few blocks in front of the victim, so he's unmistakably there for them. */
    private static Vec3 findStandingSpot(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        Vec3 facing = target.getLookAngle();
        for (double distance = 3.0; distance >= 1.0; distance -= 0.5) {
            Vec3 candidate = target.position().add(facing.x * distance, 0.0, facing.z * distance);
            BlockPos pos = BlockPos.containing(candidate);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return candidate;
            }
        }
        return target.position();
    }

    /** Used by the Tax Man blessing to know whether there's anything to hand back. */
    @Nullable
    public static TaxManEntity visiting(ServerPlayer target) {
        return ACTIVE.get(target.getUUID());
    }
}
