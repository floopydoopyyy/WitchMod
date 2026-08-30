package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.SolicitorLines;

/**
 * The Solicitor (sacrificial item BUNDLE — Emerald Block is taken by Silver Tongue): a door-to-door salesman.
 * A named wandering trader hounds you, pitching terrible deals in chat. Kill it and a fresh one turns up
 * INSTANTLY with a new name and some cheek; the only way to be rid of it for a while is to actually complete
 * one of its awful trades — then it slinks off for 1.5–10 minutes (biased toward the longer end). A minor,
 * relentless annoyance.
 *
 * <p>Uses a plain vanilla {@link WanderingTrader} (no custom entity), marked with scoreboard tags so the
 * kill/trade hooks in {@code CurseEventHandler} can find it and its victim. Names + dialogue come from
 * {@code data/witchmod/text/solicitor.json}.
 */
public final class CurseSolicitor extends Effect {
    public static final String TAG = "witchmod_solicitor";
    private static final String OWNER_PREFIX = "solowner_";

    private static final Map<UUID, WanderingTrader> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> HIDDEN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CHAT = new HashMap<>();

    public CurseSolicitor() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 22, () -> Items.BUNDLE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        summon(target, "arrive");
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.SOLICITOR_CHECK_INTERVAL.get())) {
            return;
        }
        UUID id = target.getUUID();
        long now = target.level().getGameTime();

        // Hiding after a completed trade — leave them be until the timer's up.
        Long hidden = HIDDEN_UNTIL.get(id);
        if (hidden != null) {
            if (now < hidden) {
                return;
            }
            HIDDEN_UNTIL.remove(id);
        }

        WanderingTrader trader = ACTIVE.get(id);
        if (trader == null || !trader.isAlive()) {
            trader = findExisting(target);
            if (trader == null) {
                summon(target, "arrive");
                return;
            }
            ACTIVE.put(id, trader);
        }

        follow(trader, target);

        if (now >= NEXT_CHAT.getOrDefault(id, 0L)) {
            pitch(trader, target, "pitch");
            int min = Config.SOLICITOR_CHAT_MIN_TICKS.get();
            int max = Math.max(min, Config.SOLICITOR_CHAT_MAX_TICKS.get());
            NEXT_CHAT.put(id, now + min + target.getRandom().nextInt(max - min + 1));
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        WanderingTrader trader = ACTIVE.remove(id);
        if (trader != null && trader.isAlive()) {
            trader.discard();
        }
        HIDDEN_UNTIL.remove(id);
        NEXT_CHAT.remove(id);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        if (ACTIVE.containsKey(target.getUUID())) {
            return java.util.Optional.of("solicitor is hounding you");
        }
        return java.util.Optional.of(target.level().getGameTime() < HIDDEN_UNTIL.getOrDefault(target.getUUID(), 0L)
                ? "solicitor lying low" : "solicitor incoming");
    }

    // --- Called from CurseEventHandler -------------------------------------------------------------------

    /** True if {@code entity} is a solicitor trader (by its tag). */
    public static boolean isSolicitor(net.minecraft.world.entity.Entity entity) {
        return entity instanceof WanderingTrader && entity.getTags().contains(TAG);
    }

    @Nullable
    public static UUID ownerOf(net.minecraft.world.entity.Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(OWNER_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(OWNER_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** The trader was killed — a fresh one turns up instantly with a new name and some backchat. */
    public static void onKilled(ServerPlayer victim) {
        ACTIVE.remove(victim.getUUID());
        summon(victim, "killed");
    }

    /** The victim actually completed a trade — the salesman slinks off for a good while (biased long). */
    public static void onTraded(ServerPlayer victim, WanderingTrader trader) {
        UUID id = victim.getUUID();
        pitch(trader, victim, "traded");
        ACTIVE.remove(id);
        trader.discard();
        long now = victim.level().getGameTime();
        double min = Config.SOLICITOR_HIDE_MIN_SECONDS.get() * 20.0;
        double max = Config.SOLICITOR_HIDE_MAX_SECONDS.get() * 20.0;
        // sqrt(r) skews the roll toward the higher end.
        long ticks = (long) (min + (max - min) * Math.sqrt(victim.getRandom().nextDouble()));
        HIDDEN_UNTIL.put(id, now + ticks);
    }

    // --- Internals ---------------------------------------------------------------------------------------

    private static void summon(ServerPlayer victim, String greeting) {
        ServerLevel level = victim.serverLevel();
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader == null) {
            return;
        }
        Vec3 spot = spawnSpot(victim);
        trader.moveTo(spot.x, spot.y, spot.z, victim.getYRot() + 180.0F, 0.0F);
        trader.setPersistenceRequired();
        trader.setDespawnDelay(Integer.MAX_VALUE);
        // Slow it right down so its own random-stroll AI can't blitz it around.
        var speed = trader.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(Config.SOLICITOR_MOVE_SPEED.get());
        }
        trader.setCustomName(Component.literal(SolicitorLines.randomName(victim.getRandom())));
        trader.setCustomNameVisible(true);
        trader.addTag(TAG);
        trader.addTag(OWNER_PREFIX + victim.getStringUUID());
        level.addFreshEntity(trader);
        giveTerribleOffers(trader);

        ACTIVE.put(victim.getUUID(), trader);
        NEXT_CHAT.put(victim.getUUID(), level.getGameTime() + 40);
        pitch(trader, victim, greeting);
        com.oliver.witchmod.effects.Curses.SOLICITOR.get().markDiscoveredByVictim(victim);
    }

    private static void giveTerribleOffers(WanderingTrader trader) {
        MerchantOffers offers = trader.getOffers();
        offers.clear();
        offers.add(offer(Items.EMERALD, 40, Items.DIRT));
        offers.add(offer(Items.DIAMOND, 6, Items.STICK));
        offers.add(offer(Items.EMERALD, 64, Items.POISONOUS_POTATO));
        offers.add(offer(Items.GOLD_INGOT, 32, Items.WHEAT_SEEDS));
        offers.add(offer(Items.EMERALD, 24, Items.COBBLESTONE));
    }

    private static MerchantOffer offer(net.minecraft.world.item.Item cost, int amount, net.minecraft.world.item.Item result) {
        return new MerchantOffer(new ItemCost(cost, amount), new ItemStack(result, 1), 9999, 0, 0.0F);
    }

    private static void follow(WanderingTrader trader, ServerPlayer victim) {
        double dist = trader.distanceToSqr(victim);
        double tp = Config.SOLICITOR_TELEPORT_DISTANCE.get();
        double follow = Config.SOLICITOR_FOLLOW_DISTANCE.get();
        if (dist > tp * tp) {
            Vec3 spot = spawnSpot(victim);
            trader.moveTo(spot.x, spot.y, spot.z, victim.getYRot() + 180.0F, 0.0F); // never loses you
        } else if (dist > follow * follow) {
            trader.getNavigation().moveTo(victim.getX(), victim.getY(), victim.getZ(), Config.SOLICITOR_FOLLOW_SPEED.get());
        } else {
            trader.getNavigation().stop(); // close enough — stand still so you can trade
            trader.getLookControl().setLookAt(victim); // ...and stare at you expectantly
        }
    }

    private static Vec3 spawnSpot(ServerPlayer victim) {
        Vec3 facing = victim.getLookAngle();
        return victim.position().add(facing.x * 2.5, 0.0, facing.z * 2.5);
    }

    /** Say a line (from the given dialogue key) in chat to everyone near the trader. */
    private static void pitch(WanderingTrader trader, ServerPlayer victim, String key) {
        RandomSource random = victim.getRandom();
        String line = SolicitorLines.pick(key, random);
        if (line == null) {
            return;
        }
        String name = trader.getCustomName() != null ? trader.getCustomName().getString() : "The Salesman";
        // Player-chat style: <Name> line, no quotes; same green name + cream line colours as before.
        Component msg = Component.literal("<" + name + "> ").withColor(0x4CC24C)
                .append(Component.literal(line.replace("{player}", victim.getGameProfile().getName())).withColor(0xE0E0C0));
        double r = Config.SOLICITOR_CHAT_RADIUS.get();
        AABB box = trader.getBoundingBox().inflate(r);
        for (ServerPlayer p : victim.serverLevel().getEntitiesOfClass(ServerPlayer.class, box)) {
            p.sendSystemMessage(msg);
        }
    }

    @Nullable
    private static WanderingTrader findExisting(ServerPlayer victim) {
        AABB box = victim.getBoundingBox().inflate(Config.SOLICITOR_TELEPORT_DISTANCE.get() + 16.0);
        for (WanderingTrader t : victim.serverLevel().getEntitiesOfClass(WanderingTrader.class, box,
                e -> e.getTags().contains(TAG) && victim.getUUID().equals(ownerOf(e)))) {
            return t; // re-adopt a solicitor that survived a reload
        }
        return null;
    }
}
