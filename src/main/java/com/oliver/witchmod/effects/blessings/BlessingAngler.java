package com.oliver.witchmod.effects.blessings;

import java.lang.reflect.Field;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * A born fisher (master-spec Angler, sacrificial item SALMON). Fish bite very fast, and you've a decent chance
 * to reel in more than one thing. Your LUCK is untouched — that's a different blessing's job.
 *
 * <p>And every so often the water gives up something it shouldn't (weighted, most→least likely): bonus
 * treasure, a live fish or squid, a Drowned, a Strider, a sheep called Woolliam, or — rarest, to discourage
 * AFK-farming — a lit stick of TNT. The fast-bite half reflects into {@link FishingHook#timeUntilLured}; the
 * multi-catch and the surprises are applied on the catch in {@code BlessingEventHandler}.
 */
public final class BlessingAngler extends Effect {
    private static Field timeUntilLuredField;
    private static Field timeUntilHookedField;

    public BlessingAngler() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.SALMON);
    }

    /** You find out the first time a bite comes suspiciously fast (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        FishingHook hook = target.fishing;
        if (hook == null) {
            return;
        }
        int extra = Config.ANGLER_BITE_EXTRA_TICKS.get();
        // Hurry the "waiting for a bite" timer along — reflected, since the fields are private (the project
        // already uses reflection elsewhere rather than take on an access transformer).
        boolean lured = reduceField(hook, timeUntilLuredField(), extra);
        reduceField(hook, timeUntilHookedField(), extra);
        if (lured) {
            markDiscoveredByVictim(target);
        }
    }

    private static boolean reduceField(FishingHook hook, @org.jetbrains.annotations.Nullable Field field, int by) {
        if (field == null) {
            return false;
        }
        try {
            int value = field.getInt(hook);
            if (value > 1) {
                field.setInt(hook, Math.max(1, value - by));
                return true;
            }
        } catch (IllegalAccessException ignored) {
            // give up quietly; the bite just runs at vanilla speed
        }
        return false;
    }

    private static Field timeUntilLuredField() {
        if (timeUntilLuredField == null) {
            timeUntilLuredField = findField("timeUntilLured");
        }
        return timeUntilLuredField;
    }

    private static Field timeUntilHookedField() {
        if (timeUntilHookedField == null) {
            timeUntilHookedField = findField("timeUntilHooked");
        }
        return timeUntilHookedField;
    }

    @org.jetbrains.annotations.Nullable
    private static Field findField(String name) {
        try {
            Field f = FishingHook.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    // --- The comical "surprise" pull, called from the fishing-catch handler. --------------------------------

    /** Spawn a comical surprise flung out of the water at the hook, weighted most→least likely. */
    public static void spawnSurprise(ServerPlayer player, FishingHook hook) {
        ServerLevel level = player.serverLevel();
        Vec3 at = hook.position();
        int roll = player.getRandom().nextInt(100);

        // Weighted (cumulative): treasure 44 · fish/squid 26 · drowned 14 · strider 8 · Woolliam 5 · TNT 3.
        if (roll < 44) {
            spawnTreasure(player, hook);
            return;
        }
        Entity entity;
        if (roll < 70) {
            EntityType<?>[] pool = {EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH,
                    EntityType.TROPICAL_FISH, EntityType.SQUID};
            entity = pool[player.getRandom().nextInt(pool.length)].create(level);
        } else if (roll < 84) {
            entity = EntityType.DROWNED.create(level);
        } else if (roll < 92) {
            entity = EntityType.STRIDER.create(level);
        } else if (roll < 97) {
            var sheep = EntityType.SHEEP.create(level);
            if (sheep != null) {
                sheep.setCustomName(Component.literal("Woolliam"));
                sheep.setCustomNameVisible(true);
            }
            entity = sheep;
        } else {
            entity = EntityType.TNT.create(level); // rarest — a nasty surprise for AFK anglers
        }
        if (entity == null) {
            return;
        }
        entity.moveTo(at.x, at.y + 0.2, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        // Fling it up and toward you, arcing out of the water.
        Vec3 toPlayer = player.position().subtract(at);
        Vec3 flat = new Vec3(toPlayer.x, 0.0, toPlayer.z);
        Vec3 launch = (flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize()).scale(0.35).add(0.0, 0.55, 0.0);
        entity.setDeltaMovement(launch);
        entity.hurtMarked = true;
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.EVENT, null);
        }
        level.addFreshEntity(entity);
    }

    private static void spawnTreasure(ServerPlayer player, FishingHook hook) {
        Item[] treasure = {Items.NAUTILUS_SHELL, Items.NAME_TAG, Items.SADDLE, Items.EMERALD, Items.AMETHYST_SHARD,
                Items.PRISMARINE_CRYSTALS, Items.LAPIS_LAZULI, Items.GOLD_INGOT, Items.EXPERIENCE_BOTTLE, Items.BOOK};
        ServerLevel level = player.serverLevel();
        Vec3 at = hook.position();
        var stack = new net.minecraft.world.item.ItemStack(treasure[player.getRandom().nextInt(treasure.length)],
                1 + player.getRandom().nextInt(2));
        var item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y + 0.2, at.z, stack);
        Vec3 toPlayer = player.position().subtract(at).scale(0.1).add(0.0, 0.3, 0.0);
        item.setDeltaMovement(toPlayer);
        level.addFreshEntity(item);
    }
}
