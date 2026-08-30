package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * The behaviour of a jar breaking open — thrown, or destroyed in a hazard. Its stored effects splash over an
 * over-sized area; if no player is caught, a homing LASH surges out to punish whoever tried to throw it away.
 * There is no clean way to just bin these.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class JarEffects {
    private static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.4F), 1.3F);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.85F), 1.1F);

    /** Active lashes, ticked server-side. */
    private static final List<Lash> LASHES = new ArrayList<>();
    private static int hazardScanClock;

    private JarEffects() {}

    /** A jar breaks at {@code pos}: splash its effects on nearby players, or lash out if it caught nobody. */
    public static void splash(ServerLevel level, Vec3 pos, ItemStack jar, @Nullable Entity owner) {
        List<CapturedEffect> effects = JarContents.contents(jar);
        if (effects.isEmpty()) {
            return;
        }
        int kind = JarContents.kind(effects);
        double r = Config.JAR_SPLASH_RADIUS.get();
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
        splashParticles(level, pos, kind, r);

        String ownerName = owner instanceof ServerPlayer sp ? sp.getName().getString() : "a thrown jar";
        List<ServerPlayer> caught = level.getEntitiesOfClass(ServerPlayer.class,
                AABB.ofSize(pos, r * 2, r * 2, r * 2), p -> p.isAlive() && p.distanceToSqr(pos) <= r * r);
        if (!caught.isEmpty()) {
            for (ServerPlayer p : caught) {
                applyStored(level, p, effects, ownerName, pos);
            }
        } else {
            // No one in the blast — it lashes out at the nearest player instead.
            LASHES.add(new Lash(level, pos.add(0, 0.4, 0), new ArrayList<>(effects), kind, Config.LASH_EXPIRY_TICKS.get(), ownerName));
        }
    }

    /** Applies a jar's stored effects to {@code player} and records each one in the Ledger (landed or blocked). */
    private static void applyStored(ServerLevel level, ServerPlayer player, List<CapturedEffect> effects, String caster, Vec3 at) {
        net.minecraft.core.GlobalPos gp = net.minecraft.core.GlobalPos.of(level.dimension(), net.minecraft.core.BlockPos.containing(at));
        for (CapturedEffect e : effects) {
            WitchModRegistries.EFFECT_REGISTRY
                    .getHolder(ResourceKey.create(WitchModRegistries.EFFECT_REGISTRY_KEY, e.effectId()))
                    .ifPresent(h -> {
                        boolean landed = EffectManager.apply(player, h, Math.max(1, e.remainingTicks()), null);
                        com.oliver.witchmod.data.LedgerLog.log(java.util.Optional.of(caster), player.getName().getString(),
                                e.effectId(), landed ? "jar" : "blocked", level.getGameTime(), false, gp, null);
                    });
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        tickLashes();
        if (++hazardScanClock >= 10) { // every ~0.5s, catch jars dropped into lava/fire/cactus
            hazardScanClock = 0;
            scanHazards(event.getServer());
        }
    }

    private static void tickLashes() {
        double speed = Config.LASH_SPEED.get();
        double range = Config.LASH_RANGE.get();
        Iterator<Lash> it = LASHES.iterator();
        while (it.hasNext()) {
            Lash lash = it.next();
            if (--lash.ticksLeft <= 0 || lash.level.players().isEmpty()) {
                it.remove();
                continue;
            }
            // The nearest reachable player who ISN'T shielded by a Warding Totem — a lash won't chase into a
            // totem's protection (it just fizzles instead of pointlessly bursting on a protected player).
            ServerPlayer target = null;
            double best = range * range;
            for (ServerPlayer p : lash.level.players()) {
                if (com.oliver.witchmod.blocks.WardingTotemBlock.isProtected(p)) {
                    continue;
                }
                double d = p.distanceToSqr(lash.pos);
                if (d <= best) {
                    best = d;
                    target = p;
                }
            }
            if (target == null) {
                it.remove(); // no one in reach — it fizzles out (run far enough / hide behind a totem)
                continue;
            }
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 dir = to.subtract(lash.pos);
            double dist = dir.length();
            if (dist <= 1.1) {
                applyStored(lash.level, target, lash.effects, lash.ownerName, to);
                lashBurst(lash.level, to, lash.kind);
                it.remove();
                continue;
            }
            lash.pos = lash.pos.add(dir.scale(Math.min(speed, dist) / dist));
            lashParticles(lash.level, lash.pos, lash.kind);
        }
    }

    /** Jars dropped into a hazard break the same way — you can't quietly bin them. */
    private static void scanHazards(MinecraftServer server) {
        List<ItemEntity> doomed = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getEntities().getAll()) {
                if (e instanceof ItemEntity item && isDoomedJar(item)) {
                    doomed.add(item);
                }
            }
            for (ItemEntity item : doomed) {
                splash(level, item.position(), item.getItem(), item.getOwner());
                item.discard();
            }
            doomed.clear();
        }
    }

    private static boolean isDoomedJar(ItemEntity item) {
        if (!item.isAlive() || !(item.getItem().getItem() instanceof ItemJar) || JarContents.contents(item.getItem()).isEmpty()) {
            return false;
        }
        return item.isInLava() || item.isOnFire()
                || item.level().getBlockState(item.blockPosition()).is(net.minecraft.world.level.block.Blocks.CACTUS);
    }

    // --- particles ------------------------------------------------------------------------------------
    private static void splashParticles(ServerLevel level, Vec3 pos, int kind, double r) {
        int n = 60;
        for (ParticleOptions p : palette(kind)) {
            level.sendParticles(p, pos.x, pos.y + 0.2, pos.z, n, r * 0.5, 0.4, r * 0.5, 0.1);
        }
    }

    private static void lashParticles(ServerLevel level, Vec3 pos, int kind) {
        for (ParticleOptions p : palette(kind)) {
            level.sendParticles(p, pos.x, pos.y, pos.z, 3, 0.12, 0.12, 0.12, 0.01);
        }
    }

    private static void lashBurst(ServerLevel level, Vec3 pos, int kind) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
        for (ParticleOptions p : palette(kind)) {
            level.sendParticles(p, pos.x, pos.y, pos.z, 24, 0.3, 0.4, 0.3, 0.1);
        }
    }

    /** Cursed = purple witch stars; Blessed = warm yellow/white; Mixed = all of it. */
    private static ParticleOptions[] palette(int kind) {
        return switch (kind) {
            case JarContents.BLESSED -> new ParticleOptions[]{ParticleTypes.END_ROD, YELLOW, PALE};
            case JarContents.MIXED -> new ParticleOptions[]{ParticleTypes.WITCH, ParticleTypes.END_ROD, YELLOW};
            default -> new ParticleOptions[]{ParticleTypes.WITCH};
        };
    }

    private static final class Lash {
        final ServerLevel level;
        Vec3 pos;
        final List<CapturedEffect> effects;
        final int kind;
        int ticksLeft;
        final String ownerName;

        Lash(ServerLevel level, Vec3 pos, List<CapturedEffect> effects, int kind, int ticksLeft, String ownerName) {
            this.level = level;
            this.ownerName = ownerName;
            this.pos = pos;
            this.effects = effects;
            this.kind = kind;
            this.ticksLeft = ticksLeft;
        }
    }
}
