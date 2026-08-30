package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Death doesn't take you — it puts you into a slow, golden REBUILD instead (master-spec Immortality,
 * sacrificial item NETHER STAR). When a lethal blow lands you drop everything you're carrying, your body
 * dissolves into a hovering cloud of gold→white particles, and you slowly gather yourself back OUT OF THE AIR
 * — motes streaming in from all around — before popping back into existence right where you fell. The vanilla
 * respawn screen never appears.
 *
 * <p>It's repeatable, but the rebuild takes longer each time
 * ({@code immortalityRecoveryBaseTicks} + {@code immortalityRecoveryIncrementTicks} per prior death → 8s / 18s
 * / 28s), and after {@code immortalityMaxUses} deaths the blessing breaks. The death save is triggered from
 * {@link com.oliver.witchmod.effects.BlessingEventHandler}'s {@code LivingDeathEvent} listener via
 * {@link #beginRecovery}; the per-tick rebuild + finish run from {@link #onTick} while the blessing is active.
 *
 * <p><b>The drawback</b>: the gear you spill can be looted by anyone — but never picked back up by YOU (the
 * drops are tagged with your UUID and the pickup is vetoed for you in {@code BlessingEventHandler}). Die in
 * the open and you may well stand up to find your things already gone.
 */
public final class BlessingImmortality extends Effect {
    /** player -> the exact spot they fell, so they're rooted there and stand up in place. */
    private static final Map<UUID, Vec3> DEATH_SPOT = new HashMap<>();

    /** Gold, for the start of the rebuild. */
    private static final DustParticleOptions GOLD =
            new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.20F), 1.2F);
    /** White, for the end of the rebuild — the mix shifts gold→white as recovery completes. */
    private static final DustParticleOptions WHITE =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.95F), 1.0F);

    public BlessingImmortality() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 63, () -> Items.NETHER_STAR);
    }

    /** You find out you're immortal the first time it actually saves you (master-spec Rule 2: on trigger). */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        if (isRecovering(target)) {
            long left = (target.getData(WitchModAttachments.IMMORTALITY_RECOVERY_END) - target.level().getGameTime()) / 20;
            return java.util.Optional.of("reviving — " + Math.max(0, left) + "s");
        }
        int left = Config.IMMORTALITY_MAX_USES.get() - target.getData(WitchModAttachments.IMMORTALITY_USES);
        return java.util.Optional.of(Math.max(0, left) + " revives left");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // Fresh blessing: reset the use counter and clear any stale recovery flags.
        target.setData(WitchModAttachments.IMMORTALITY_USES, 0);
        clearRecoveryFlags(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        // If the blessing is stripped mid-rebuild, stand the player back up so they aren't left frozen.
        if (isRecovering(target)) {
            target.setHealth(target.getMaxHealth());
        }
        clearRecoveryFlags(target);
        DEATH_SPOT.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        long end = target.getData(WitchModAttachments.IMMORTALITY_RECOVERY_END);
        if (end <= 0L) {
            return; // not rebuilding
        }
        long now = target.serverLevel().getGameTime();
        if (now < end) {
            tickRebuild(target, now, end);
        } else {
            finishRebuild(target);
        }
    }

    /** True while the player is mid-rebuild — used by the damage guard and the client model-hide/input-lock. */
    public static boolean isRecovering(ServerPlayer player) {
        long end = player.getData(WitchModAttachments.IMMORTALITY_RECOVERY_END);
        return end > 0L && player.serverLevel().getGameTime() < end;
    }

    /** Kick off a death save: drop everything, freeze, and start the golden rebuild. */
    public static void beginRecovery(ServerPlayer player) {
        int uses = player.getData(WitchModAttachments.IMMORTALITY_USES) + 1;
        player.setData(WitchModAttachments.IMMORTALITY_USES, uses);

        // Everything you were carrying spills out where you fell — tagged so YOU can never grab it back.
        dropEverythingTagged(player);

        // Come back from the brink, not from full — you visibly rebuild up from near nothing.
        player.setHealth(1.0F);
        player.clearFire();
        player.setRemainingFireTicks(0);
        player.fallDistance = 0.0F;
        player.setAirSupply(player.getMaxAirSupply());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        Vec3 spot = player.position();
        DEATH_SPOT.put(player.getUUID(), spot);

        int base = Config.IMMORTALITY_RECOVERY_BASE.get();
        int increment = Config.IMMORTALITY_RECOVERY_INCREMENT.get();
        int recoveryTicks = Math.max(1, base + increment * (uses - 1)); // linear: 8s, 18s, 28s...

        long now = player.serverLevel().getGameTime();
        player.setData(WitchModAttachments.IMMORTALITY_RECOVERY_START, now);
        player.setData(WitchModAttachments.IMMORTALITY_RECOVERY_END, now + recoveryTicks);

        // Discovery is on the SAVE itself (Rule 2), not when the blessing was cast.
        Blessings.IMMORTALITY.value().markDiscoveredByVictim(player);

        // The on-screen totem-style flash (Blessed icon, client-side off the synced tick).
        player.setData(WitchModAttachments.REVIVE_FLASH_END, now + Config.REVIVE_FLASH_TICKS);

        // A big golden "you should have died" flash to open the rebuild, now heavier on gold + white.
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, spot.x, spot.y + 1.0, spot.z, 90,
                0.6, 0.9, 0.6, 0.45);
        level.sendParticles(GOLD, spot.x, spot.y + 1.0, spot.z, 80, 0.7, 1.0, 0.7, 0.05);
        level.sendParticles(WHITE, spot.x, spot.y + 1.0, spot.z, 55, 0.7, 1.0, 0.7, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 40, 0.5, 0.9, 0.5, 0.15);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Spill the whole inventory as ground items, each tagged with the owner so the owner can't re-collect. */
    private static void dropEverythingTagged(ServerPlayer player) {
        String owner = player.getUUID().toString();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity dropped = player.drop(stack, true, false);
            if (dropped != null) {
                dropped.setData(WitchModAttachments.IMMORTALITY_DROP_OWNER, owner);
            }
            inv.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Each tick of the rebuild: stay rooted, knit health back up, and gather yourself out of the air. */
    private static void tickRebuild(ServerPlayer player, long now, long end) {
        long start = player.getData(WitchModAttachments.IMMORTALITY_RECOVERY_START);
        float progress = start >= end ? 1.0F : (float) (now - start) / (float) (end - start);
        progress = Math.max(0.0F, Math.min(1.0F, progress));

        // Rooted: no drifting off the spot while you rebuild. Client input is also locked (ClientCurseHandler).
        Vec3 spot = DEATH_SPOT.get(player.getUUID());
        if (spot != null && player.position().distanceToSqr(spot) > 0.02) {
            player.teleportTo(spot.x, spot.y, spot.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();

        // Rebuild your health up from 1 to full across the recovery, so you can watch yourself knit back.
        float maxHealth = player.getMaxHealth();
        float wantHealth = 1.0F + (maxHealth - 1.0F) * progress;
        if (wantHealth > player.getHealth()) {
            player.setHealth(wantHealth);
        }

        ServerLevel level = player.serverLevel();
        emitSilhouette(level, player, progress);
        gatherFromAir(level, player, progress);
    }

    /**
     * The particle body: while the model is hidden (client-side), fill the player's hitbox with a shimmer of
     * gold→white motes so a ghostly particle version of them stands where they fell. The mix whitens as the
     * rebuild completes.
     */
    private static void emitSilhouette(ServerLevel level, ServerPlayer player, float progress) {
        AABB box = player.getBoundingBox();
        double w = box.getXsize();
        double h = box.getYsize();
        double d = box.getZsize();
        for (int i = 0; i < 7; i++) {
            double x = box.minX + level.random.nextDouble() * w;
            double y = box.minY + level.random.nextDouble() * h;
            double z = box.minZ + level.random.nextDouble() * d;
            DustParticleOptions dust = level.random.nextFloat() < progress ? WHITE : GOLD;
            level.sendParticles(dust, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * "Rebuilding yourself from the air": motes spawned a couple of blocks out in every direction, each given
     * a velocity STRAIGHT AT your body centre, so they visibly stream inward and collect into the silhouette.
     * (Vanilla treats {@code count == 0} as "spawn one particle whose velocity is (xd,yd,zd)×speed".)
     */
    private static void gatherFromAir(ServerLevel level, ServerPlayer player, float progress) {
        Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        for (int i = 0; i < 5; i++) {
            // A random direction on a sphere, at 1.6..3.6 blocks out.
            double theta = level.random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * level.random.nextDouble() - 1.0);
            double r = 1.6 + level.random.nextDouble() * 2.0;
            double ox = Math.sin(phi) * Math.cos(theta);
            double oy = Math.cos(phi);
            double oz = Math.sin(phi) * Math.sin(theta);
            double sx = centre.x + ox * r;
            double sy = centre.y + oy * r;
            double sz = centre.z + oz * r;
            // Velocity aimed back at the centre so the mote flies inward.
            double speed = 0.28;
            DustParticleOptions dust = level.random.nextFloat() < progress ? WHITE : GOLD;
            level.sendParticles(dust, sx, sy, sz, 0, -ox, -oy, -oz, speed);
        }
        if (now(level) % 5 == 0) {
            // A few brighter END_ROD sparks streaming in too, for glow.
            double theta = level.random.nextDouble() * Math.PI * 2.0;
            double r = 2.5;
            double sx = centre.x + Math.cos(theta) * r;
            double sz = centre.z + Math.sin(theta) * r;
            level.sendParticles(ParticleTypes.END_ROD, sx, centre.y + 1.0, sz, 0,
                    -Math.cos(theta), -0.2, -Math.sin(theta), 0.2);
        }
    }

    private static long now(ServerLevel level) {
        return level.getGameTime();
    }

    /** Stand back up: full health, clear the flags, a bright pop, and break the blessing if it's spent. */
    private static void finishRebuild(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        clearRecoveryFlags(player);
        DEATH_SPOT.remove(player.getUUID());

        // The "pop back into existence": a bright outward burst as the model returns.
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 50, 0.3, 0.6, 0.3, 0.25);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.0, c.z, 25, 0.4, 0.6, 0.4, 0.3);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);

        int uses = player.getData(WitchModAttachments.IMMORTALITY_USES);
        if (uses >= Config.IMMORTALITY_MAX_USES.get()) {
            // Spent — it breaks after the last save.
            EffectManager.remove(player, Blessings.IMMORTALITY);
        }
    }

    private static void clearRecoveryFlags(ServerPlayer player) {
        player.setData(WitchModAttachments.IMMORTALITY_RECOVERY_START, 0L);
        player.setData(WitchModAttachments.IMMORTALITY_RECOVERY_END, 0L);
    }
}
