package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * CLIENT-side ritual outcome FX (heavy particle work kept off the server — the server only sends a tiny
 * {@code RitualFxPayload}). Success reads as a "magic missile": the sacrificial item rises over the table
 * ringed by 3 accelerating orbs, then streaks off toward the target, and a beat later a lash homes in on the
 * target. Fizzle is a weak red/smoke puff; backfire is a messy red (or purple) burst out of the table.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class RitualFxClient {
    private RitualFxClient() {}

    private static final DustParticleOptions GOLD = dust(1.0F, 0.85F, 0.2F, 1.1F);
    private static final DustParticleOptions PURPLE = dust(0.66F, 0.2F, 0.85F, 1.1F);
    private static final DustParticleOptions BACKFIRE_RED = dust(0.92F, 0.16F, 0.12F, 1.5F);
    private static final DustParticleOptions BACKFIRE_PURPLE = dust(0.62F, 0.16F, 0.78F, 1.5F);

    private static DustParticleOptions dust(float r, float g, float b, float scale) {
        return new DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    private record Fx(int kind, boolean blessing, boolean purple, Vec3 origin, ItemStack item,
                      int targetId, int delay, long start) {}

    private static final List<Fx> ACTIVE = new ArrayList<>();

    /** called from the network handler when the server reports a ritual outcome. */
    public static void play(int kind, int flags, BlockPos table, ItemStack item, int targetId, int delay) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 origin = new Vec3(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
        ACTIVE.add(new Fx(kind, (flags & 1) != 0, (flags & 2) != 0, origin,
                item == null ? ItemStack.EMPTY : item, targetId, delay, level.getGameTime()));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        long now = level.getGameTime();
        ACTIVE.removeIf(fx -> tick(level, fx, (int) (now - fx.start)));
    }

    /** @return true once the FX is finished. */
    private static boolean tick(Level level, Fx fx, int t) {
        return switch (fx.kind) {
            case 1 -> tickFizzle(level, fx, t);
            case 2 -> tickBackfire(level, fx, t);
            case 3 -> tickShockwave(level, fx, t);
            default -> tickSuccess(level, fx, t);
        };
    }

    // ---- Shockwave: an expanding ground ring (Dragon's Breath), gold for a blessing / purple for a curse ----

    private static final int SHOCKWAVE_TICKS = 16;

    private static boolean tickShockwave(Level level, Fx fx, int t) {
        if (t > SHOCKWAVE_TICKS) {
            return true;
        }
        DustParticleOptions col = fx.blessing ? GOLD : PURPLE;
        double radius = fx.delay * (t / (double) SHOCKWAVE_TICKS); // fx.delay carries the radius in blocks
        double cx = fx.origin.x, cy = fx.origin.y - 0.8, cz = fx.origin.z; // near the caster's feet
        int points = (int) (radius * 6) + 10;
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2;
            double px = cx + Math.cos(a) * radius, pz = cz + Math.sin(a) * radius;
            level.addParticle(col, px, cy, pz, 0.0, 0.02, 0.0);
            if ((i & 3) == 0) {
                level.addParticle(ParticleTypes.DRAGON_BREATH, px, cy + 0.1, pz, 0.0, 0.01, 0.0);
            }
        }
        return false;
    }

    // ---- Success: item rises + orbs accelerate, then a lash to the target, then a lash into the target -----

    private static boolean tickSuccess(Level level, Fx fx, int t) {
        DustParticleOptions orb = fx.blessing ? GOLD : PURPLE;
        boolean self = fx.targetId < 0;

        // windup: the item floats up, ringed by three accelerating orbs.
        if (t < 30) {
            double rise = 0.4 + Math.min(1.0, t / 26.0) * 1.2;
            Vec3 item = fx.origin.add(0, rise, 0);
            if (!fx.item.isEmpty() && t % 2 == 0) {
                level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, fx.item), item.x, item.y, item.z, 0, 0.01, 0);
            }
            double conv = t < 26 ? 0.55 : 0.55 * (30 - t) / 4.0;      // pull in over the last 4 ticks
            double ang = t * (0.15 + t * 0.012);                       // accelerate
            for (int i = 0; i < 3; i++) {
                double a = ang + i * (Math.PI * 2 / 3);
                level.addParticle(orb, item.x + Math.cos(a) * conv, item.y + Math.sin(a * 1.3) * 0.12, item.z + Math.sin(a) * conv, 0, 0, 0);
            }
            return false;
        }
        Vec3 launch = fx.origin.add(0, 1.6, 0);
        if (t == 30) {
            for (int i = 0; i < 24; i++) {
                double a = Math.random() * Math.PI * 2;
                double s = 0.12 + Math.random() * 0.12;
                level.addParticle(orb, launch.x, launch.y, launch.z, Math.cos(a) * s, (Math.random() - 0.3) * s, Math.sin(a) * s);
            }
        }
        if (self) {
            return t > 34; // no lash for a self-cast — the effect just settles onto you
        }

        Entity target = level.getEntity(fx.targetId);
        Vec3 targetPos = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : null;

        // launch lash: a short streak leaving the table toward the target, fading fast.
        if (t >= 30 && t < 44 && targetPos != null) {
            Vec3 dir = targetPos.subtract(launch).normalize();
            double travel = (t - 30) * 0.6;
            Vec3 p = launch.add(dir.scale(Math.min(travel, 6.0)));
            for (int i = 0; i < 3; i++) {
                level.addParticle(orb, p.x, p.y, p.z, dir.x * 0.05, dir.y * 0.05, dir.z * 0.05);
            }
        }

        // target lash: after the delay, a lash appears out near the target and homes in.
        int ts = 30 + fx.delay;
        if (t >= ts && targetPos != null) {
            int tt = t - ts;
            if (tt > 16) {
                return true;
            }
            // the incoming lash approaches FROM the table's direction (where the ritual was cast), a bit above.
            Vec3 fromTable = fx.origin.subtract(targetPos);
            Vec3 dir = fromTable.lengthSqr() < 1.0e-3 ? new Vec3(1, 0.5, 0) : fromTable.normalize();
            Vec3 approach = targetPos.add(dir.scale(5.0)).add(0, 2.5, 0);
            Vec3 p = approach.lerp(targetPos, Math.min(1.0, tt / 12.0));
            for (int i = 0; i < 4; i++) {
                level.addParticle(orb, p.x, p.y, p.z, 0, 0, 0);
            }
            if (tt == 12) {
                for (int i = 0; i < 20; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double s = 0.15 + Math.random() * 0.15;
                    level.addParticle(orb, targetPos.x, targetPos.y, targetPos.z, Math.cos(a) * s, (Math.random() - 0.2) * s, Math.sin(a) * s);
                }
            }
            return false;
        }
        return t > 44 && (targetPos == null || t >= ts + 16);
    }

    // ---- Fizzle: a weak but pronounced red + smoke puff over the table --------------------------------------

    private static boolean tickFizzle(Level level, Fx fx, int t) {
        if (t < 10) {
            Vec3 o = fx.origin.add(0, 0.3, 0);
            for (int i = 0; i < 4; i++) {
                double a = Math.random() * Math.PI * 2;
                double s = 0.05 + Math.random() * 0.06;
                level.addParticle(BACKFIRE_RED, o.x, o.y, o.z, Math.cos(a) * s, 0.04 + Math.random() * 0.03, Math.sin(a) * s);
            }
            level.addParticle(ParticleTypes.SMOKE, o.x, o.y, o.z, (Math.random() - 0.5) * 0.03, 0.05, (Math.random() - 0.5) * 0.03);
            if (t % 3 == 0) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, o.x, o.y, o.z, 0, 0.03, 0);
            }
        }
        return t > 16;
    }

    // ---- Backfire: a messy red (or purple) burst out of the table ------------------------------------------

    private static boolean tickBackfire(Level level, Fx fx, int t) {
        ParticleOptions col = fx.purple ? BACKFIRE_PURPLE : BACKFIRE_RED;
        Vec3 o = fx.origin.add(0, 0.3, 0);
        if (t == 0) {
            level.addParticle(ParticleTypes.FLASH, o.x, o.y, o.z, 0, 0, 0);
        }
        if (t < 12) {
            int n = t == 0 ? 40 : 10;
            for (int i = 0; i < n; i++) {
                double a = Math.random() * Math.PI * 2;
                double p = Math.random() * Math.PI - Math.PI / 2;
                double s = 0.15 + Math.random() * 0.4;        // messy, varied speeds
                double vx = Math.cos(a) * Math.cos(p) * s;
                double vy = Math.sin(p) * s + 0.05;
                double vz = Math.sin(a) * Math.cos(p) * s;
                level.addParticle(col, o.x, o.y, o.z, vx, vy, vz);
                if (i % 3 == 0) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, o.x, o.y, o.z, vx * 0.4, vy * 0.4, vz * 0.4);
                }
            }
        }
        return t > 18;
    }
}
