package com.oliver.witchmod.blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * The over-the-top visual for a successful ritual: a rotating, rising ritual circle of coloured particles
 * around the table block (purple for a curse, warm yellow/white for a blessing — matching the Blessed Jar),
 * plus, when the victim is cast from afar, a directional "lash" that streaks INTO them from the direction of
 * the table, so they get a subtle hint of where it came from and see the curse/blessing enter them.
 *
 * <p>Both are multi-tick animations, so they're ticked from a {@link ServerTickEvent} (the same pattern the
 * thrown-jar lash uses) rather than fired as a single particle burst.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class RitualFx {
    // Purple curse motes; blessings reuse the Jar's warm palette.
    private static final DustParticleOptions CURSE_PURPLE = new DustParticleOptions(new Vector3f(0.62F, 0.18F, 0.85F), 1.3F);
    private static final DustParticleOptions BLESS_YELLOW = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.4F), 1.3F);
    private static final DustParticleOptions BLESS_PALE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.85F), 1.1F);

    private static final int CIRCLE_TICKS = 50;
    private static final int LASH_TICKS = 14;
    private static final double LASH_START_DISTANCE = 4.0;

    private static final List<Circle> CIRCLES = new ArrayList<>();
    private static final List<Lash> LASHES = new ArrayList<>();

    private RitualFx() {}

    /** Start the rising ritual-circle animation around the table block. */
    public static void startCircle(ServerLevel level, BlockPos pos, boolean blessing) {
        CIRCLES.add(new Circle(level, Vec3.atBottomCenterOf(pos).add(0, 0.05, 0), blessing));
    }

    /**
     * Start a directional lash into {@code target} coming from the direction of {@code tablePos}. Only worth
     * doing when the victim is some distance from the table (a self-cast at the block is its own spectacle).
     */
    public static void startLash(ServerLevel level, BlockPos tablePos, ServerPlayer target, boolean blessing) {
        Vec3 targetCentre = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 fromTable = Vec3.atCenterOf(tablePos).subtract(targetCentre);
        Vec3 dir = new Vec3(fromTable.x, 0, fromTable.z); // come in flat, from the table's compass direction
        if (dir.lengthSqr() < 1.0E-4) {
            dir = new Vec3(1, 0, 0);
        }
        Vec3 start = targetCentre.add(dir.normalize().scale(LASH_START_DISTANCE)).add(0, 0.4, 0);
        LASHES.add(new Lash(level, start, target, blessing));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        tickCircles();
        tickLashes();
    }

    private static void tickCircles() {
        Iterator<Circle> it = CIRCLES.iterator();
        while (it.hasNext()) {
            Circle c = it.next();
            double p = c.age / (double) CIRCLE_TICKS; // 0..1
            ParticleOptions[] palette = palette(c.blessing);

            // Three counter-rotating rings climbing a rising column, expanding then tightening — "all out".
            for (int ring = 0; ring < 3; ring++) {
                double baseH = 0.1 + p * 2.4 + ring * 0.35;
                double radius = (0.7 + 0.5 * Math.sin(p * Math.PI)) * (1.0 - ring * 0.12);
                double spin = c.age * 0.28 * (ring % 2 == 0 ? 1 : -1) + ring * 0.7;
                int points = 18;
                for (int i = 0; i < points; i++) {
                    double a = spin + i * (Math.PI * 2 / points);
                    double x = c.centre.x + Math.cos(a) * radius;
                    double z = c.centre.z + Math.sin(a) * radius;
                    double y = c.centre.y + baseH;
                    ParticleOptions particle = palette[(i + ring) % palette.length];
                    c.level.sendParticles(particle, x, y, z, 1, 0.0, 0.02, 0.0, 0.0);
                }
            }
            // A central updraft of sparks.
            for (int i = 0; i < 4; i++) {
                c.level.sendParticles(palette[i % palette.length],
                        c.centre.x + (c.level.random.nextDouble() - 0.5) * 0.3,
                        c.centre.y + 0.1 + c.level.random.nextDouble() * 0.4,
                        c.centre.z + (c.level.random.nextDouble() - 0.5) * 0.3,
                        0, 0.0, 0.18 + c.level.random.nextDouble() * 0.1, 0.0, 1.0);
            }

            if (c.age == CIRCLE_TICKS - 1) {
                // A final flourish + chime at the top of the column.
                double topY = c.centre.y + 2.6;
                for (ParticleOptions particle : palette) {
                    c.level.sendParticles(particle, c.centre.x, topY, c.centre.z, 30, 0.5, 0.3, 0.5, 0.08);
                }
                c.level.playSound(null, BlockPos.containing(c.centre.x, topY, c.centre.z),
                        c.blessing ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.BLOCKS, 0.8F, c.blessing ? 1.4F : 0.7F);
            }
            if (++c.age >= CIRCLE_TICKS) {
                it.remove();
            }
        }
    }

    private static void tickLashes() {
        Iterator<Lash> it = LASHES.iterator();
        while (it.hasNext()) {
            Lash lash = it.next();
            if (!lash.target.isAlive()) {
                it.remove();
                continue;
            }
            ParticleOptions[] palette = palette(lash.blessing);
            double t = lash.age / (double) LASH_TICKS;
            Vec3 targetCentre = lash.target.position().add(0, lash.target.getBbHeight() * 0.55, 0);
            // Ease-in so it accelerates into the victim.
            double eased = t * t;
            Vec3 head = lash.start.lerp(targetCentre, eased);
            // A short trail behind the head.
            for (int i = 0; i < 3; i++) {
                double back = eased - i * 0.06;
                if (back < 0) {
                    break;
                }
                Vec3 p = lash.start.lerp(targetCentre, back);
                lash.level.sendParticles(palette[i % palette.length], p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }

            if (lash.age >= LASH_TICKS - 1) {
                // Arrival: it sinks into them — a tight burst at their core.
                for (ParticleOptions particle : palette) {
                    lash.level.sendParticles(particle, targetCentre.x, targetCentre.y, targetCentre.z, 14, 0.25, 0.35, 0.25, 0.02);
                }
                it.remove();
                continue;
            }
            lash.age++;
        }
    }

    /** Curse = purple witch motes; blessing = warm yellow/white stars (matching the Blessed Jar). */
    private static ParticleOptions[] palette(boolean blessing) {
        return blessing
                ? new ParticleOptions[]{ParticleTypes.END_ROD, BLESS_YELLOW, BLESS_PALE}
                : new ParticleOptions[]{ParticleTypes.WITCH, CURSE_PURPLE, ParticleTypes.WITCH};
    }

    private static final class Circle {
        final ServerLevel level;
        final Vec3 centre;
        final boolean blessing;
        int age;

        Circle(ServerLevel level, Vec3 centre, boolean blessing) {
            this.level = level;
            this.centre = centre;
            this.blessing = blessing;
        }
    }

    private static final class Lash {
        final ServerLevel level;
        final Vec3 start;
        final ServerPlayer target;
        final boolean blessing;
        int age;

        Lash(ServerLevel level, Vec3 start, ServerPlayer target, boolean blessing) {
            this.level = level;
            this.start = start;
            this.target = target;
            this.blessing = blessing;
        }
    }
}
