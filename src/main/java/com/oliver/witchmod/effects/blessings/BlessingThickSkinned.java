package com.oliver.witchmod.effects.blessings;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * A hide like an armadillo's (master-spec Thick Skinned, sacrificial item ARMADILLO SCUTE): any single hit at
 * or below {@code thickSkinnedDamageFloor} (2.0 = one heart) just bounces off — the damage has to EXCEED the
 * floor to land at all. When a hit is shrugged off you get a little feedback: a burst of scute-coloured
 * "deflection" particles and a subtle screenshake.
 *
 * <p>The floor check lives in {@link com.oliver.witchmod.effects.BlessingEventHandler} on the incoming-damage
 * event, which calls {@link #neutralise} for the feedback.
 */
public final class BlessingThickSkinned extends Effect {
    /** Scute tan, for the "tough hide" particle burst. */
    private static final DustParticleOptions SCUTE = new DustParticleOptions(new Vector3f(0.72F, 0.53F, 0.32F), 1.1F);

    public BlessingThickSkinned() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.ARMADILLO_SCUTE);
    }

    /** You find out the first time a hit bounces off (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Feedback when a hit is shrugged off: deflection particles, a tiny screenshake, and a dull thud. */
    public static void neutralise(ServerPlayer player) {
        player.setData(WitchModAttachments.THICK_SKINNED_SHAKE_END,
                player.serverLevel().getGameTime() + Config.THICKSKIN_SHAKE_TICKS.get());

        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        double y = c.y + player.getBbHeight() * 0.6;
        level.sendParticles(ParticleTypes.CRIT, c.x, y, c.z, 12, 0.35, 0.4, 0.35, 0.25);
        level.sendParticles(SCUTE, c.x, y, c.z, 14, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 0.5F, 1.2F);
    }
}
