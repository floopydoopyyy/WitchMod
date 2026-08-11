package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * A reference to the genre of clip where someone is quietly having a bad time and the sky finishes the job
 * (master-spec Comic Relief). While you're low on health there's a small chance per check of a
 * comedically-timed bolt that kills you outright — and if you've got a pile of dropped items lying about,
 * the bolt may take those instead.
 *
 * <p><b>The odds are small on purpose.</b> The whole joke rests on it being unexpected; a bolt you can see
 * coming is just weather. They're multiplied by {@code THUNDER_MULTIPLIER} while it's actually thundering,
 * which is the one time the game has already primed you to expect lightning.
 *
 * <p><b>The posthumous strike is intentional</b>, and rare. Dying earns a small chance of one more bolt
 * landing on the spot a moment later — long enough that the death screen is already up — which torches the
 * drops you left behind. Kicking someone while they're down is the entire point of the bit.
 *
 * <p>Discovery fires on ANY bolt this curse causes, including the ones that only hit your items.
 */
public final class CurseComicRelief extends Effect {
    /** A bolt owed to someone who has already died: where, in which level, and when it lands. */
    private record Posthumous(ServerLevel level, Vec3 pos, long dueTick) {}

    private static final List<Posthumous> PENDING = new ArrayList<>();

    public CurseComicRelief() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.LIGHTNING_ROD);
    }

    /** You find out the moment the sky takes an interest in you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        strikeVictim(target, target.serverLevel());
        return "comedic lightning called down (low-health condition bypassed)";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.COMIC_CHECK_INTERVAL.get())
                || target.isCreative() || target.isSpectator()) {
            return;
        }
        ServerLevel level = target.serverLevel();
        double multiplier = level.isThundering() ? Config.COMIC_THUNDER_MULTIPLIER.get() : 1.0;

        // The main event: low on health, and the sky notices.
        if (target.getHealth() <= Config.COMIC_LOW_HEALTH.get()
                && roll(target, Config.COMIC_STRIKE_CHANCE.get() * multiplier)) {
            strikeVictim(target, level);
            return; // one bolt per check is plenty
        }

        // Or it takes your things instead, which is arguably worse.
        List<ItemEntity> pile = nearbyItems(target, level);
        int minimum = Config.COMIC_ITEM_PILE_MIN.get();
        if (pile.size() < minimum) {
            return;
        }
        // The chance CLIMBS with the size of the pile: everything you own scattered in a field is precisely
        // the shot this joke wants, so a big heap is far likelier to be taken than a couple of stray blocks.
        double chance = Math.min(
                Config.COMIC_ITEM_CHANCE_CAP.get(),
                Config.COMIC_ITEM_STRIKE_CHANCE.get()
                        + Config.COMIC_ITEM_PILE_SCALING.get() * (pile.size() - minimum));
        if (roll(target, chance * multiplier)) {
            strikeItems(target, level, pile);
        }
    }

    private static boolean roll(ServerPlayer target, double chancePercent) {
        return target.getRandom().nextDouble() * 100.0 < chancePercent;
    }

    private static List<ItemEntity> nearbyItems(ServerPlayer target, ServerLevel level) {
        double radius = Config.COMIC_ITEM_SCAN_RADIUS.get();
        AABB area = target.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive);
    }

    /** Instantly fatal — a bolt's normal 5 hearts wouldn't be the joke, and might not even finish you. */
    private void strikeVictim(ServerPlayer target, ServerLevel level) {
        bolt(level, target.position());
        target.hurt(level.damageSources().lightningBolt(), Float.MAX_VALUE);
        markDiscoveredByVictim(target);
    }

    private void strikeItems(ServerPlayer target, ServerLevel level, List<ItemEntity> pile) {
        Vec3 centre = pile.get(0).position();
        bolt(level, centre);
        double radius = Config.COMIC_ITEM_SCAN_RADIUS.get();
        for (ItemEntity item : pile) {
            if (item.position().distanceToSqr(centre) <= radius * radius) {
                item.discard(); // vaporised — no drops, no second chances
            }
        }
        markDiscoveredByVictim(target);
    }

    /** Called from the death hook — see {@code CurseEventHandler}. */
    public static void onDeath(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        double multiplier = level.isThundering() ? Config.COMIC_THUNDER_MULTIPLIER.get() : 1.0;
        if (!roll(target, Config.COMIC_POSTHUMOUS_CHANCE.get() * multiplier)) {
            return;
        }
        PENDING.add(new Posthumous(level, target.position(),
                level.getGameTime() + Config.COMIC_POSTHUMOUS_DELAY.get()));
    }

    /** Called every server tick — lands any parting bolts that have waited long enough. */
    public static void tickPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> {
            if (pending.level().getGameTime() < pending.dueTick()) {
                return false;
            }
            bolt(pending.level(), pending.pos());
            // Whatever they dropped is standing right there, and lightning starts fires.
            AABB area = new AABB(pending.pos(), pending.pos()).inflate(3.0);
            for (ItemEntity item : pending.level().getEntitiesOfClass(ItemEntity.class, area)) {
                item.discard();
            }
            return true;
        });
    }

    private static void bolt(ServerLevel level, Vec3 pos) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(pos)));
        // Visual only — the damage is applied directly, so this can't set fire to half the neighbourhood.
        lightning.setVisualOnly(true);
        level.addFreshEntity(lightning);
    }
}
