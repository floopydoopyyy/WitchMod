package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * blessing of Disguise (ARMOR STAND): you're perfectly costumed as a cow, sheep or pig (consistent for the
 * duration). Hostiles treat you as livestock and leave you alone — unless you crowd them, or you get hit, at
 * which point the costume BREAKS (you flash back to yourself) and you must go un-hit for 12s for it to return.
 * The disguise-as-mob render + hidden nametag are client-side (see {@code client/DisguiseClient}); the SERVER
 * owns the mob choice ({@link WitchModAttachments#DISGUISE_TYPE}, -1 while broken) + the hostile passivity.
 */
public final class BlessingDisguise extends Effect {
    /** the vampire-bat disguise type (Sanguine synergy), above the 0/1/2 livestock set. */
    private static final int BAT = 3;
    /** the spider disguise type (Spider synergy). */
    private static final int SPIDER = 4;
    /** the villager disguise type (Silver Tongue synergy). */
    private static final int VILLAGER = 5;
    /** cow is the base type 0 — the Cow synergy just forces it. */
    private static final int COW = 0;
    /** player -> base disguise type (0 cow / 1 sheep / 2 pig), stable for the whole blessing. */
    private static final Map<UUID, Integer> BASE = new HashMap<>();
    /** player -> game tick the "broken" window ends; while broken they show their real model. */
    private static final Map<UUID, Long> BROKEN_UNTIL = new HashMap<>();
    /** player -> game tick the next ambient animal noise is due. */
    private static final Map<UUID, Long> NEXT_SOUND = new HashMap<>();

    public BlessingDisguise() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.ARMOR_STAND);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        int type = baseFor(target);
        BASE.put(target.getUUID(), type);
        target.setData(WitchModAttachments.DISGUISE_TYPE, type);
        markDiscoveredByVictim(target);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        Integer base = BASE.get(target.getUUID());
        String mob = base == null ? "livestock" : (base == 1 ? "sheep" : base == 2 ? "pig" : "cow");
        boolean active = target.getData(WitchModAttachments.DISGUISE_TYPE) >= 0;
        return java.util.Optional.of(active ? "disguised as a " + mob : "disguise broken (a " + mob + ")");
    }

    @Override
    public void onRemove(ServerPlayer target) {
        BASE.remove(target.getUUID());
        BROKEN_UNTIL.remove(target.getUUID());
        NEXT_SOUND.remove(target.getUUID());
        target.setData(WitchModAttachments.DISGUISE_TYPE, -1);
        setBatFlight(target, false);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        long now = target.level().getGameTime();
        int base = BASE.computeIfAbsent(id, k -> baseFor(target));
        double radius = Config.DISGUISE_BREAK_RADIUS.get();

        // concealment synergy: while you're a PROP the block takes priority (the animal is suppressed); getting
        // near a hostile knocks you out of the prop into the animal, rather than straight to your player.
        if (com.oliver.witchmod.synergy.Synergies.CONCEALMENT.activeFor(target)
                && target.getData(WitchModAttachments.PROPHUNT_BLOCK) >= 0) {
            if (hostileWithin(target, radius)) {
                concealCascade(target); // prop -> animal
            } else {
                if (target.getData(WitchModAttachments.DISGUISE_TYPE) != -1) {
                    target.setData(WitchModAttachments.DISGUISE_TYPE, -1);
                }
                setBatFlight(target, false);
            }
            return;
        }

        Long broken = BROKEN_UNTIL.get(id);
        boolean isBroken = broken != null && now < broken;

        // getting too close to a hostile blows your cover (cascading with a flash under the concealment synergy).
        if (!isBroken && hostileWithin(target, radius)) {
            if (com.oliver.witchmod.synergy.Synergies.CONCEALMENT.activeFor(target)) {
                concealCascade(target);
            } else {
                breakDisguise(target);
            }
            isBroken = true;
        }

        if (isBroken) {
            if (target.getData(WitchModAttachments.DISGUISE_TYPE) != -1) {
                target.setData(WitchModAttachments.DISGUISE_TYPE, -1);
            }
            setBatFlight(target, false); // flight is lost the instant the disguise breaks
            return;
        }

        // synergies re-skin the costume: Sanguine → bat (with flight), Spider → spider, Silver Tongue → villager,
        // Cow → always a cow.
        boolean bat = com.oliver.witchmod.synergy.Synergies.VAMPIRE_BAT.activeFor(target);
        boolean spider = !bat && com.oliver.witchmod.synergy.Synergies.SPIDER_DISGUISE.activeFor(target);
        boolean villager = !bat && !spider && com.oliver.witchmod.synergy.Synergies.SILVER_VILLAGER.activeFor(target);
        boolean cow = !bat && !spider && !villager && com.oliver.witchmod.synergy.Synergies.COW_COSTUME.activeFor(target);
        int shown = bat ? BAT : spider ? SPIDER : villager ? VILLAGER : cow ? COW : base;
        // costume intact: restore it if it was broken and the window's elapsed, and keep hostiles docile.
        if (target.getData(WitchModAttachments.DISGUISE_TYPE) != shown) {
            target.setData(WitchModAttachments.DISGUISE_TYPE, shown);
            poof(target, false);
        }
        setBatFlight(target, bat);
        clearHostileAggro(target, radius * 4.0);

        // the costume also SOUNDS right — occasional moos/baas/oinks/screeches/hisses/hmphs from where you stand.
        long next = NEXT_SOUND.getOrDefault(id, 0L);
        if (now >= next) {
            NEXT_SOUND.put(id, now + 60 + target.getRandom().nextInt(120)); // ~3–9s
            net.minecraft.sounds.SoundEvent amb = switch (shown) {
                case BAT -> net.minecraft.sounds.SoundEvents.BAT_AMBIENT;
                case SPIDER -> net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT;
                case VILLAGER -> net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT;
                case 1 -> net.minecraft.sounds.SoundEvents.SHEEP_AMBIENT;
                case 2 -> net.minecraft.sounds.SoundEvents.PIG_AMBIENT;
                default -> net.minecraft.sounds.SoundEvents.COW_AMBIENT;
            };
            target.serverLevel().playSound(null, target.blockPosition(), amb,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.9F + target.getRandom().nextFloat() * 0.2F);
        }
    }

    /** grant/revoke the vampire-bat's creative-style flight (survival players only). */
    private static void setBatFlight(ServerPlayer p, boolean allow) {
        if (p.isCreative() || p.isSpectator()) {
            return; // never touch a creative/spectator player's own flight
        }
        if (p.getAbilities().mayfly == allow) {
            return;
        }
        p.getAbilities().mayfly = allow;
        if (!allow) {
            p.getAbilities().flying = false;
        }
        p.onUpdateAbilities();
    }

    /** stable per-player mob choice so a re-application looks the same. */
    private static int baseFor(ServerPlayer target) {
        return Math.floorMod(target.getUUID().hashCode(), 3);
    }

    private static boolean hostileWithin(ServerPlayer target, double radius) {
        AABB box = target.getBoundingBox().inflate(radius);
        for (LivingEntity e : target.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive())) {
            if (e.distanceToSqr(target) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private static void clearHostileAggro(ServerPlayer target, double radius) {
        AABB box = target.getBoundingBox().inflate(radius);
        for (Mob m : target.serverLevel().getEntitiesOfClass(Mob.class, box,
                e -> e instanceof Enemy && e.getTarget() == target)) {
            m.setTarget(null);
        }
    }

    /** called from {@code BlessingEventHandler} when a disguised player is hit — the costume breaks. */
    public static void breakDisguise(ServerPlayer player) {
        if (!BASE.containsKey(player.getUUID())) {
            return;
        }
        BROKEN_UNTIL.put(player.getUUID(), player.level().getGameTime() + Config.DISGUISE_RETURN_TICKS.get());
        if (player.getData(WitchModAttachments.DISGUISE_TYPE) >= 0) {
            player.setData(WitchModAttachments.DISGUISE_TYPE, -1);
            poof(player, true);
        }
        setBatFlight(player, false); // a broken disguise drops you out of the sky
    }

    /**
     * concealment cascade — one step down the disguise ladder on a hit or hostile proximity: prop -> animal ->
     * player. Each change puffs you briefly invisible (armour and all, so nothing floats), like a smoke vanish.
     */
    public static void concealCascade(ServerPlayer player) {
        if (player.getData(WitchModAttachments.PROPHUNT_BLOCK) >= 0) {
            BlessingPropHunt.actionTaken(player);                 // drop the prop
            BROKEN_UNTIL.remove(player.getUUID());                // the animal shows immediately, no broken window
            player.setData(WitchModAttachments.DISGUISE_TYPE, BASE.computeIfAbsent(player.getUUID(), k -> baseFor(player)));
            flashConceal(player);
        } else if (player.getData(WitchModAttachments.DISGUISE_TYPE) >= 0) {
            breakDisguise(player);                                // animal -> player
            flashConceal(player);
        }
        // already your player self — nothing left to strip
    }

    /** the brief unrendered window + a puff of smoke that sells a form change as a vanish. */
    private static void flashConceal(ServerPlayer player) {
        player.setData(WitchModAttachments.CONCEAL_FLASH_END,
                player.level().getGameTime() + Config.CONCEAL_FLASH_TICKS.get());
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.9, player.getZ(),
                24, 0.35, 0.5, 0.35, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static void poof(ServerPlayer player, boolean breaking) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.9, player.getZ(),
                18, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, player.blockPosition(),
                breaking ? SoundEvents.CHICKEN_EGG : SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** true if the player is CURRENTLY shown as a mob (server-authoritative). */
    public static boolean isDisguised(ServerPlayer player) {
        return player.getData(WitchModAttachments.DISGUISE_TYPE) >= 0;
    }
}
