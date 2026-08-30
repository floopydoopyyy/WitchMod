package com.oliver.witchmod.data;

import java.util.function.Supplier;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** The mod's custom particle types. */
public final class WitchModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, WitchMod.MODID);

    /** Sleep "Z" that drifts up off a narcoleptic's head while they're asleep. */
    public static final Supplier<SimpleParticleType> SLEEP_Z =
            PARTICLE_TYPES.register("sleep_z", () -> new SimpleParticleType(false));

    private WitchModParticles() {}

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
