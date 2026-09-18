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
 *. While you're low on health there's a small chance per check of a
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
    /** the Thunder-synergy smite barrage: extra VISUAL bolts staggered over a few ticks (no loot touched). */
    private static final List<Posthumous> SMITES = new ArrayList<>();

    public CurseComicRelief() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.LIGHTNING_ROD);
    }

    /** you find out the moment the sky takes an interest in you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        ServerLevel level = target.serverLevel();
        // `items` forces the item-pile strike; anything else forces the low-health bolt (condition bypassed).
        if (arg != null && arg.toLowerCase().startsWith("item")) {
            List<ItemEntity> pile = nearbyItems(target, level);
            if (pile.isEmpty()) {
                return "no dropped items nearby to smite — drop some first, then force `items`.";
            }
            strikeItems(target, level, pile);
            return "comedic lightning vaporised your item pile (" + pile.size() + " items).";
        }
        strikeVictim(target, level);
        return "comedic lightning called down on you (low-health condition bypassed).";
    }

    @Override
    public java.util.List<String> debugArgs() {
        return java.util.List.of("items");
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.COMIC_CHECK_INTERVAL.get())
                || target.isCreative() || target.isSpectator()) {
            return;
        }
        ServerLevel level = target.serverLevel();
        double multiplier = level.isThundering() ? Config.COMIC_THUNDER_MULTIPLIER.get() : 1.0;

        // the main event: low on health, and the sky notices.
        if (target.getHealth() <= Config.COMIC_LOW_HEALTH.get()
                && roll(target, Config.COMIC_STRIKE_CHANCE.get() * multiplier)) {
            strikeVictim(target, level);
            return; // one bolt per check is plenty
        }

        // or it takes your things instead, which is arguably worse.
        List<ItemEntity> pile = nearbyItems(target, level);
        int minimum = Config.COMIC_ITEM_PILE_MIN.get();
        if (pile.size() < minimum) {
            return;
        }
        // the chance CLIMBS with the size of the pile: everything you own scattered in a field is precisely
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

    /** instantly fatal — a bolt's normal 5 hearts wouldn't be the joke, and might not even finish you. */
    private void strikeVictim(ServerPlayer target, ServerLevel level) {
        bolt(level, target.position());
        // smiting synergy (with Thunder): rain a quick barrage of extra visual bolts on the spot for comic overkill.
        if (com.oliver.witchmod.synergy.Synergies.SMITING.activeFor(target)) {
            Vec3 spot = target.position();
            long now = level.getGameTime();
            int spacing = Config.COMIC_SMITE_SPACING_TICKS.get();
            for (int i = 1; i <= Config.COMIC_SMITE_BOLTS.get(); i++) {
                Vec3 jitter = spot.add((level.random.nextDouble() - 0.5) * 2.0, 0.0, (level.random.nextDouble() - 0.5) * 2.0);
                SMITES.add(new Posthumous(level, jitter, now + (long) i * spacing));
            }
        }
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

    /** called from the death hook — see {@code CurseEventHandler}. */
    public static void onDeath(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        double multiplier = level.isThundering() ? Config.COMIC_THUNDER_MULTIPLIER.get() : 1.0;
        if (!roll(target, Config.COMIC_POSTHUMOUS_CHANCE.get() * multiplier)) {
            return;
        }
        PENDING.add(new Posthumous(level, target.position(),
                level.getGameTime() + Config.COMIC_POSTHUMOUS_DELAY.get()));
    }

    /** called every server tick — lands any parting bolts (and smite-barrage visual bolts) that are due. */
    public static void tickPending() {
        SMITES.removeIf(smite -> {
            if (smite.level().getGameTime() < smite.dueTick()) {
                return false;
            }
            bolt(smite.level(), smite.pos()); // visual only — the kill already happened, loot untouched
            return true;
        });
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> {
            if (pending.level().getGameTime() < pending.dueTick()) {
                return false;
            }
            bolt(pending.level(), pending.pos());
            // whatever they dropped is standing right there, and lightning starts fires.
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
        // visual only — the damage is applied directly, so this can't set fire to half the neighbourhood.
        lightning.setVisualOnly(true);
        level.addFreshEntity(lightning);
    }
}
