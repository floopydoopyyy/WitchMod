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
 * Blessing of Disguise (ARMOR STAND): you're perfectly costumed as a cow, sheep or pig (consistent for the
 * duration). Hostiles treat you as livestock and leave you alone — unless you crowd them, or you get hit, at
 * which point the costume BREAKS (you flash back to yourself) and you must go un-hit for 12s for it to return.
 * The disguise-as-mob render + hidden nametag are client-side (see {@code client/DisguiseClient}); the SERVER
 * owns the mob choice ({@link WitchModAttachments#DISGUISE_TYPE}, -1 while broken) + the hostile passivity.
 */
public final class BlessingDisguise extends Effect {
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
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        long now = target.level().getGameTime();
        int base = BASE.computeIfAbsent(id, k -> baseFor(target));
        double radius = Config.DISGUISE_BREAK_RADIUS.get();

        Long broken = BROKEN_UNTIL.get(id);
        boolean isBroken = broken != null && now < broken;

        // Getting too close to a hostile blows your cover.
        if (!isBroken && hostileWithin(target, radius)) {
            breakDisguise(target);
            isBroken = true;
        }

        if (isBroken) {
            if (target.getData(WitchModAttachments.DISGUISE_TYPE) != -1) {
                target.setData(WitchModAttachments.DISGUISE_TYPE, -1);
            }
            return;
        }

        // Costume intact: restore it if it was broken and the window's elapsed, and keep hostiles docile.
        if (target.getData(WitchModAttachments.DISGUISE_TYPE) != base) {
            target.setData(WitchModAttachments.DISGUISE_TYPE, base);
            poof(target, false);
        }
        clearHostileAggro(target, radius * 4.0);

        // The costume also SOUNDS right — occasional moos/baas/oinks from where you stand.
        long next = NEXT_SOUND.getOrDefault(id, 0L);
        if (now >= next) {
            NEXT_SOUND.put(id, now + 60 + target.getRandom().nextInt(120)); // ~3–9s
            net.minecraft.sounds.SoundEvent amb = switch (base) {
                case 1 -> net.minecraft.sounds.SoundEvents.SHEEP_AMBIENT;
                case 2 -> net.minecraft.sounds.SoundEvents.PIG_AMBIENT;
                default -> net.minecraft.sounds.SoundEvents.COW_AMBIENT;
            };
            target.serverLevel().playSound(null, target.blockPosition(), amb,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.9F + target.getRandom().nextFloat() * 0.2F);
        }
    }

    /** Stable per-player mob choice so a re-application looks the same. */
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

    /** Called from {@code BlessingEventHandler} when a disguised player is hit — the costume breaks. */
    public static void breakDisguise(ServerPlayer player) {
        if (!BASE.containsKey(player.getUUID())) {
            return;
        }
        BROKEN_UNTIL.put(player.getUUID(), player.level().getGameTime() + Config.DISGUISE_RETURN_TICKS.get());
        if (player.getData(WitchModAttachments.DISGUISE_TYPE) >= 0) {
            player.setData(WitchModAttachments.DISGUISE_TYPE, -1);
            poof(player, true);
        }
    }

    private static void poof(ServerPlayer player, boolean breaking) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.9, player.getZ(),
                18, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, player.blockPosition(),
                breaking ? SoundEvents.CHICKEN_EGG : SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /** True if the player is CURRENTLY shown as a mob (server-authoritative). */
    public static boolean isDisguised(ServerPlayer player) {
        return player.getData(WitchModAttachments.DISGUISE_TYPE) >= 0;
    }
}
