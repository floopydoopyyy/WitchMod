package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Blessing of Thunder (TRIDENT): a static charge builds while you're NOT swinging, through four tiers. Your
 * next melee hit discharges it — burning the target and arcing chain-lightning to nearby foes (30% of the hit
 * damage per arc, capped flat). The deeper the charge, the more arcs and the nastier the payoff:
 * <ul>
 *   <li>T1 (3s) — burn + 1 chain.</li>
 *   <li>T2 (5.5s) — burn + 2 chains, each chained foe also burns.</li>
 *   <li>T3 (8s) — burn + 2 chains that can EACH arc once more to a fresh foe.</li>
 *   <li>T4 (13s) — a real lightning bolt + 6 bonus damage on the hit, plus 2 chains that can each arc to 2 more.</li>
 * </ul>
 * A synced {@link WitchModAttachments#THUNDER_TIER} drives the client's crackling aura; a ready cue fires the
 * instant you hit full charge.
 */
public final class BlessingThunder extends Effect {
    /** player -> game tick of the last discharge (a hit) — charge is the time since. */
    private static final Map<UUID, Long> LAST_DISCHARGE = new HashMap<>();
    /** player -> last tier seen, so the "fully charged" cue fires exactly once on reaching T4. */
    private static final Map<UUID, Integer> LAST_TIER = new HashMap<>();

    public BlessingThunder() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.TRIDENT);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        return java.util.Optional.of("charge tier " + currentTier(target) + "/4");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        LAST_DISCHARGE.put(target.getUUID(), target.level().getGameTime());
        target.setData(WitchModAttachments.THUNDER_TIER, 0);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        LAST_DISCHARGE.remove(target.getUUID());
        LAST_TIER.remove(target.getUUID());
        target.setData(WitchModAttachments.THUNDER_TIER, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int tier = currentTier(target);
        target.setData(WitchModAttachments.THUNDER_TIER, tier);

        int last = LAST_TIER.getOrDefault(target.getUUID(), 0);
        if (tier == 4 && last < 4) {
            // Fully charged — a single glint + a "charge ready" cue, nothing obtrusive.
            ServerLevel level = target.serverLevel();
            level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                    SoundSource.PLAYERS, 0.7F, 1.6F);
            level.sendParticles(ParticleTypes.GLOW, target.getX(), target.getEyeY(), target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY(), target.getZ(),
                    1, 0.05, 0.05, 0.05, 0.0);
        }
        LAST_TIER.put(target.getUUID(), tier);
    }

    private static int currentTier(ServerPlayer target) {
        long last = LAST_DISCHARGE.getOrDefault(target.getUUID(), target.level().getGameTime());
        long charge = target.level().getGameTime() - last;
        if (charge >= Config.THUNDER_TIER4_TICKS.get()) {
            return 4;
        }
        if (charge >= Config.THUNDER_TIER3_TICKS.get()) {
            return 3;
        }
        if (charge >= Config.THUNDER_TIER2_TICKS.get()) {
            return 2;
        }
        if (charge >= Config.THUNDER_TIER1_TICKS.get()) {
            return 1;
        }
        return 0;
    }

    /** Called from {@code BlessingEventHandler} when a Thunder-blessed player lands a MELEE hit. */
    public static void discharge(ServerPlayer player, LivingEntity primary, float baseDamage) {
        int tier = currentTier(player);
        LAST_DISCHARGE.put(player.getUUID(), player.level().getGameTime()); // spend the charge
        player.setData(WitchModAttachments.THUNDER_TIER, 0);
        if (tier <= 0) {
            return; // nothing stored yet
        }
        ServerLevel level = player.serverLevel();
        int burn = Config.THUNDER_BURN_TICKS.get();
        // A flat base every chain, PLUS a % of the hit — then capped.
        double chainDmg = Math.min(Config.THUNDER_CHAIN_CAP.get(),
                Config.THUNDER_CHAIN_BASE.get() + baseDamage * Config.THUNDER_CHAIN_PERCENT.get() / 100.0);

        primary.setRemainingFireTicks(Math.max(primary.getRemainingFireTicks(), burn));

        if (tier >= 4) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(primary.getX(), primary.getY(), primary.getZ());
                bolt.setVisualOnly(true); // we deal the damage ourselves
                bolt.setCause(player);
                level.addFreshEntity(bolt);
            }
            primary.hurt(player.damageSources().playerAttack(player), Config.THUNDER_TIER4_BONUS.get().floatValue());
        }

        Set<Integer> hit = new HashSet<>();
        hit.add(primary.getId());
        // start chains / per-chain children by tier.
        int start = tier == 1 ? 1 : 2;
        int childPerChain = tier == 4 ? 2 : (tier == 3 ? 1 : 0);
        int depth = childPerChain > 0 ? 1 : 0;
        impact(level, primary.getBoundingBox().getCenter(), tier); // the initial hit crackles too
        spawnChains(player, primary, hit, start, childPerChain, depth, chainDmg, burn, tier);
    }

    private static void spawnChains(ServerPlayer player, LivingEntity source, Set<Integer> hit,
                                    int count, int childCount, int depthLeft, double dmg, int burn, int tier) {
        ServerLevel level = player.serverLevel();
        double range = Config.THUNDER_CHAIN_RANGE.get();
        for (int i = 0; i < count; i++) {
            LivingEntity next = nearestUnhit(level, source, hit, range, player);
            if (next == null) {
                break;
            }
            hit.add(next.getId());
            beam(level, source.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), tier);
            next.hurt(player.damageSources().playerAttack(player), (float) dmg);
            next.setRemainingFireTicks(Math.max(next.getRemainingFireTicks(), burn));
            impact(level, next.getBoundingBox().getCenter(), tier);
            if (depthLeft > 0 && childCount > 0) {
                spawnChains(player, next, hit, childCount, childCount, depthLeft - 1, dmg, burn, tier);
            }
        }
    }

    /** A crackle burst where a bolt lands — punchier the deeper the charge. */
    private static void impact(ServerLevel level, Vec3 p, int tier) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 4 + tier * 3, 0.2, 0.3, 0.2, 0.15);
        level.sendParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 2 + tier * 2, 0.2, 0.25, 0.2, 0.05);
        if (tier >= 3) {
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, tier, 0.15, 0.25, 0.15, 0.08);
        }
        if (tier >= 4) {
            level.sendParticles(ParticleTypes.FLASH, p.x, p.y + 0.3, p.z, 1, 0, 0, 0, 0);
        }
    }

    @Nullable
    private static LivingEntity nearestUnhit(ServerLevel level, LivingEntity from, Set<Integer> hit,
                                             double range, ServerPlayer owner) {
        Vec3 c = from.getBoundingBox().getCenter();
        AABB box = new AABB(c.x - range, c.y - range, c.z - range, c.x + range, c.y + range, c.z + range);
        LivingEntity best = null;
        double bestSq = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != owner && !hit.contains(e.getId()))) {
            double d = e.distanceToSqr(c);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    /** A jagged lightning arc between two points — denser and brighter the higher the charge. */
    private static void beam(ServerLevel level, Vec3 a, Vec3 b, int tier) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        int steps = (int) Math.max(4, Math.min(40, len * (3 + tier)));
        Vec3 step = d.scale(1.0 / steps);
        double jitter = 0.08 + tier * 0.05; // higher charge = a wilder, more forked arc
        Vec3 p = a;
        for (int i = 0; i <= steps; i++) {
            double jx = (level.random.nextDouble() - 0.5) * jitter;
            double jy = (level.random.nextDouble() - 0.5) * jitter;
            double jz = (level.random.nextDouble() - 0.5) * jitter;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + jx, p.y + jy, p.z + jz, 1, 0.0, 0.0, 0.0, 0.0);
            if (tier >= 2 && i % 2 == 0) {
                level.sendParticles(ParticleTypes.WAX_OFF, p.x + jx, p.y + jy, p.z + jz, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (tier >= 4 && i % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, p.x + jx, p.y + jy, p.z + jz, 1, 0.0, 0.0, 0.0, 0.01);
            }
            p = p.add(step);
        }
    }

    @Override
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        // Force a full charge so the next hit is a tier-4 discharge (or set a tier via a number).
        int tier = 4;
        if (arg != null) {
            try {
                tier = Math.max(0, Math.min(4, Integer.parseInt(arg.trim())));
            } catch (NumberFormatException ignored) {
                // keep 4
            }
        }
        int[] thresholds = {0, Config.THUNDER_TIER1_TICKS.get(), Config.THUNDER_TIER2_TICKS.get(),
                Config.THUNDER_TIER3_TICKS.get(), Config.THUNDER_TIER4_TICKS.get()};
        LAST_DISCHARGE.put(target.getUUID(), target.level().getGameTime() - thresholds[tier]);
        target.setData(WitchModAttachments.THUNDER_TIER, tier);
        return "charged to tier " + tier + " — your next melee hit discharges it";
    }
}
