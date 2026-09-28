package com.oliver.witchmod.loot;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.items.WitchModItems;

/**
 * mob-kill drops that keep the mod present: witches always trickle a little Cursed Essence (farmable), and
 * both witches and other undead have a rare SPECIAL drop — a prefilled jar, or (some of the time) a coin.
 * the special drop is gated on a recent player hit (vanilla's rare-drop rule) so mob-on-mob deaths can't farm it.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class JarDropHandler {
    private JarDropHandler() {}

    @SubscribeEvent
    static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        RandomSource rng = dead.getRandom();
        Level level = dead.level();

        // witches sprinkle essence on any death — the everyday reminder the mod is installed.
        if (dead instanceof Witch) {
            int essence = 0;
            for (int i = 0; i < Config.WITCH_ESSENCE_ROLLS.get(); i++) {
                if (rng.nextInt(100) < Config.WITCH_ESSENCE_ROLL_CHANCE.get()) {
                    essence++;
                }
            }
            if (essence > 0) {
                addDrop(event, level, dead, new ItemStack(WitchModItems.CURSED_ESSENCE.get(), essence));
            }
        }

        // the rare special drop: a jar/coin/grenade. gated so it can't clog mob farms — by default it needs a
        // player to land the killing blow; otherwise the old "recently hit by a player" rule.
        if (!Config.JAR_DROPS_ENABLED.get() || !Config.JAR_MOB_DROPS_ENABLED.get() || !killedByPlayer(event)) {
            return;
        }
        int oneIn = oneInFor(dead);
        if (oneIn <= 0 || rng.nextInt(oneIn) != 0) {
            return;
        }
        ItemStack special = rollSpecial(rng);
        if (!special.isEmpty()) {
            addDrop(event, level, dead, special);
        }
    }

    /** whether this death qualifies for a special drop: a player killing blow (default) or, if that's off, a recent player hit. */
    private static boolean killedByPlayer(LivingDropsEvent event) {
        if (Config.SPECIAL_DROP_REQUIRE_PLAYER_KILL.get()) {
            return event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player;
        }
        return event.isRecentlyHit();
    }

    /** a special drop: rarely the Holy Hand Grenade, else a coin some of the time, otherwise a prefilled named jar. */
    private static ItemStack rollSpecial(RandomSource rng) {
        int grenadeOneIn = Config.GRENADE_SPECIAL_ONE_IN.get();
        if (grenadeOneIn > 0 && rng.nextInt(grenadeOneIn) == 0) {
            return new ItemStack(WitchModItems.HOLY_HAND_GRENADE.get());
        }
        if (rng.nextInt(100) < Config.SPECIAL_DROP_COIN_CHANCE.get()) {
            Item coin = switch (rng.nextInt(3)) {
                case 0 -> WitchModItems.CURSED_COIN.get();
                case 1 -> WitchModItems.BLESSED_COIN.get();
                default -> WitchModItems.EXECUTIONERS_COIN.get();
            };
            return new ItemStack(coin);
        }
        return NamedJars.rollStack(rng);
    }

    private static void addDrop(LivingDropsEvent event, Level level, LivingEntity dead, ItemStack stack) {
        event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY(0.5), dead.getZ(), stack));
    }

    /** witches use their own rate; anything undead uses the rarer one; everything else never yields a special. */
    private static int oneInFor(LivingEntity dead) {
        if (dead instanceof Witch) {
            return Config.JAR_WITCH_ONE_IN.get();
        }
        if (dead.isInvertedHealAndHarm()) { // vanilla's "is undead" test — modded undead included
            return Config.JAR_UNDEAD_ONE_IN.get();
        }
        return 0;
    }
}
