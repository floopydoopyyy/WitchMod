package com.oliver.witchmod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * the sleep "Z" — drifts slowly UP off a sleeper's head, swaying side to side, fading in then out. Spawned
 * server-side (so every viewer sees it) via {@code level.sendParticles(WitchModParticles.SLEEP_Z, ...)}.
 */
public class SleepZParticle extends TextureSheetParticle {
    private final double swayPhase;
    private final double swayAmount;

    protected SleepZParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.gravity = 0.0F;
        this.lifetime = 44 + this.random.nextInt(22);          // ~2.2–3.3s
        this.quadSize = 0.12F + this.random.nextFloat() * 0.07F;
        this.hasPhysics = false;
        this.swayPhase = this.random.nextDouble() * Math.PI * 2.0;
        this.swayAmount = 0.004 + this.random.nextDouble() * 0.004;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.xd = 0.0;
        this.yd = 0.027;                                        // gentle rise
        this.zd = 0.0;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        // update the drift BEFORE the move (super.tick moves by xd/yd/zd), so the sway shows this tick.
        double t = this.age * 0.22 + this.swayPhase;
        this.xd = Math.cos(t) * this.swayAmount;
        this.zd = Math.sin(t * 0.7) * this.swayAmount * 0.5;
        this.yd = 0.027;
        super.tick();
        // fade in over the first sixth, hold, then fade out over the last third.
        float frac = (float) this.age / (float) this.lifetime;
        this.alpha = frac < 0.16F ? frac / 0.16F
                : (frac > 0.62F ? Math.max(0.0F, 1.0F - (frac - 0.62F) / 0.38F) : 1.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** client factory registered against {@code WitchModParticles.SLEEP_Z}. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SleepZParticle(level, x, y, z, this.sprites);
        }
    }
}
