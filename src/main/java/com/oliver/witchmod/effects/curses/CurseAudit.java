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
 * somebody has noticed how much you're hoarding. While the curse is active the victim's
 * surroundings are periodically assessed, and once there's enough loot <b>in nearby chests or on the floor</b>
 * to be worth the paperwork, the {@link TaxManEntity} turns up and starts confiscating.
 *
 * <p><b>The trigger IS the counterplay, and it looks OUTWARD.</b> He only shows up for a stash you've left
 * lying about — chests and dropped items within {@code SCAN_RADIUS}. He deliberately does NOT come merely
 * because you're carrying valuables on your person (that was the old behaviour, and it made him turn up
 * constantly with nothing around to justify it); once he's here for the chests he'll still frisk your pockets
 * as a last resort, but your pockets alone won't summon him. Keep your hoard somewhere you aren't and he
 * never comes.
 *
 * <p>Valuables are the datapack tag {@link WitchModTags#VALUABLES}. Everything he takes goes into the
 * world-persistent {@code TaxBank} for the Payday blessing to hand back out later, possibly to somebody else.
 */
public final class CurseAudit extends Effect {
    /** victim -> game tick the Tax Man may next appear. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    /** victim -> the Tax Man currently visiting them, if any. */
    private static final Map<UUID, TaxManEntity> ACTIVE = new HashMap<>();

    public CurseAudit() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 80, () -> Items.EMERALD);
    }

    /** you find out when a man in a suit materialises next to your chests (Rule 2). */
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
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        if (ACTIVE.containsKey(target.getUUID())) {
            return java.util.Optional.of("audit under way");
        }
        return java.util.Optional.of(
                target.level().getGameTime() < COOLDOWN.getOrDefault(target.getUUID(), 0L) ? "audit complete" : "audit pending");
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        summon(target);
        return "Tax Man summoned to audit you";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.TAXES_CHECK_INTERVAL.get())) {
            return;
        }
        UUID id = target.getUUID();

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
        if (externalValueNear(target) < Config.TAXES_MIN_VALUE_TRIGGER.get()) {
            return; // nothing worth taking lying around — this is the counterplay working
        }
        summon(target);
    }

    /**
     * loot lying about that he'd come for: nearby chests and dropped items. <b>Deliberately excludes the
     * victim's own inventory</b> — carrying valuables shouldn't summon him, only leaving a stash out should.
     */
    private static int externalValueNear(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        int radius = Config.TAXES_SCAN_RADIUS.get();
        int total = 0;

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

    /** used by the Payday blessing to know whether there's anything to hand back. */
    @Nullable
    public static TaxManEntity visiting(ServerPlayer target) {
        return ACTIVE.get(target.getUUID());
    }
}
