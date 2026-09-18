package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * light fingers. Standing right up against another player
 * has a chance to quietly lift a random item out of their inventory and into yours — a chance that's
 * <b>greatly higher when you're behind them</b>. It prefers their backpack over their hotbar (so you rarely
 * grab what they're actively holding), gives you a quiet pickup sound when it lands, and simply <b>fails if
 * your own inventory is full</b> (nothing to pocket it into).
 *
 * <p>The hotbar bias is a WEIGHTED pick, not a hard exclusion — a hotbar slot can still be lifted, just far
 * less often ({@code pickpocketHotbarWeight} vs 1.0 for backpack slots). Only the 36 main+hotbar slots are
 * fair game; armour and offhand are left alone.
 */
public final class BlessingPickpocket extends Effect {
    /** slots 0..8 of the inventory are the hotbar. */
    private static final int HOTBAR_SLOTS = 9;

    public BlessingPickpocket() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.STRING);
    }

    /** you find out you've got light fingers the first time you actually lift something (Rule 2). */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        double r = Config.PICKPOCKET_RADIUS.get();
        boolean near = !target.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                target.getBoundingBox().inflate(r + 1), p -> p != target).isEmpty();
        return java.util.Optional.of(near ? "a mark is in reach" : "no one to pick");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        for (ServerPlayer p : target.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                target.getBoundingBox().inflate(8.0), p -> p != target && p.isAlive())) {
            attemptSteal(target, p);
            return "attempted to pickpocket " + p.getName().getString();
        }
        return "no other player nearby to pickpocket";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.PICKPOCKET_CHECK_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        double radius = Config.PICKPOCKET_RADIUS.get();
        for (ServerPlayer victim : level.getPlayers(p -> p != target && p.isAlive()
                && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(target) <= radius * radius)) {
            attemptSteal(target, victim);
        }
    }

    private static void attemptSteal(ServerPlayer thief, ServerPlayer victim) {
        double chance = Config.PICKPOCKET_BASE_CHANCE.get();
        if (isBehind(victim, thief)) {
            chance *= Config.PICKPOCKET_BEHIND_MULT.get();
            // thievery synergies: even sneakier from behind — a form (prop/entity) beats being merely unseen.
            if (thief.getData(com.oliver.witchmod.data.WitchModAttachments.PROPHUNT_BLOCK) >= 0
                    || thief.getData(com.oliver.witchmod.data.WitchModAttachments.DISGUISE_TYPE) >= 0) {
                chance *= Config.PICKPOCKET_DISGUISE_MULT.get();
            } else if (com.oliver.witchmod.synergy.Synergies.THIEVING_SHADOW.activeFor(thief)) {
                chance *= Config.PICKPOCKET_UNSEEN_MULT.get();
            }
        }
        if (thief.getRandom().nextDouble() >= chance) {
            return;
        }

        int slot = pickSlot(victim, thief.getRandom());
        if (slot < 0) {
            return; // nothing worth taking
        }
        ItemStack inSlot = victim.getInventory().getItem(slot);
        ItemStack attempt = inSlot.copy();
        thief.getInventory().add(attempt);            // add() adds what fits; `attempt` becomes the leftover
        int taken = inSlot.getCount() - attempt.getCount();
        if (taken <= 0) {
            return; // your pockets are full — the lift fails, nothing is moved
        }
        victim.getInventory().removeItem(slot, taken);
        thief.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.4F); // quiet, thief-only
        Blessings.PICKPOCKET.get().markDiscoveredByVictim(thief); // discovered on the first successful lift
    }

    /** true when the thief is in the victim's rear half — sneaking up from behind. */
    private static boolean isBehind(ServerPlayer victim, ServerPlayer thief) {
        Vec3 look = victim.getLookAngle();
        Vec3 toThief = thief.position().subtract(victim.position());
        return look.x * toThief.x + look.z * toThief.z < 0.0; // horizontal dot < 0 = thief is behind
    }

    /** A random non-empty slot from the 36 main+hotbar slots, weighting the hotbar down. -1 if all empty. */
    private static int pickSlot(ServerPlayer victim, RandomSource random) {
        NonNullList<ItemStack> items = victim.getInventory().items;
        double hotbarWeight = Config.PICKPOCKET_HOTBAR_WEIGHT.get();
        double total = 0.0;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                total += i < HOTBAR_SLOTS ? hotbarWeight : 1.0;
            }
        }
        if (total <= 0.0) {
            return -1;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                continue;
            }
            roll -= i < HOTBAR_SLOTS ? hotbarWeight : 1.0;
            if (roll < 0.0) {
                return i;
            }
        }
        return -1;
    }
}
