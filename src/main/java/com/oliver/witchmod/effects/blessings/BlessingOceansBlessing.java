package com.oliver.witchmod.effects.blessings;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The sea looks after its own (master-spec-style Ocean's Blessing, sacrificial item ANY CORAL): underwater
 * you swim <b>very fast</b> — and even faster with Dolphin's Grace on top — and aggressive mobs simply won't
 * come after you while you're in the water. Water travel becomes a joy. It does NOT give water breathing,
 * so you still have to come up for air.
 *
 * <p>The fast swim is client-side (movement is client-authoritative) off the synced
 * {@link WitchModAttachments#OCEANS_ACTIVE} flag — see {@code client/ClientCurseHandler}. The pacify is
 * server-side: a sweep here clears any aggressor already locked onto you while you're wet, and
 * {@code BlessingEventHandler} vetoes fresh target acquisition via {@code LivingChangeTargetEvent}.
 */
public final class BlessingOceansBlessing extends Effect {
    public static final TagKey<Item> CORALS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "corals"));

    public BlessingOceansBlessing() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.TUBE_CORAL);
    }

    @Override
    public Optional<TagKey<Item>> sacrificialTag() {
        return Optional.of(CORALS); // any coral plant/fan/block selects it at the Table
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.OCEANS_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.OCEANS_ACTIVE) != 1) {
            target.setData(WitchModAttachments.OCEANS_ACTIVE, 1); // self-heal after respawn/relog
        }
        ServerLevel level = target.serverLevel();
        // While you're in the water, shrug off anything already hunting you.
        if (target.isInWater()) {
            AABB pacifyBox = target.getBoundingBox().inflate(Config.OCEANS_PACIFY_RADIUS.get());
            for (Mob mob : level.getEntitiesOfClass(Mob.class, pacifyBox, m -> m.getTarget() == target)) {
                mob.setTarget(null);
            }
        }

        // Draw every nearby dolphin in and keep it following you — they grant Dolphin's Grace when close,
        // which (via the client swim boost) is where the real speed comes from. Runs on an interval since it
        // re-issues navigation; cheap entity scan.
        if (target.tickCount % 10 == 0) {
            double attract = Config.OCEANS_DOLPHIN_ATTRACT_RADIUS.get();
            double graceRadiusSqr = Config.OCEANS_DOLPHIN_GRACE_RADIUS.get() * Config.OCEANS_DOLPHIN_GRACE_RADIUS.get();
            AABB dolphinBox = target.getBoundingBox().inflate(attract);
            for (net.minecraft.world.entity.animal.Dolphin dolphin : level.getEntitiesOfClass(
                    net.minecraft.world.entity.animal.Dolphin.class, dolphinBox)) {
                dolphin.getNavigation().moveTo(target, Config.OCEANS_DOLPHIN_NAV_SPEED.get());
                if (dolphin.distanceToSqr(target) <= graceRadiusSqr) {
                    // Refresh Dolphin's Grace directly so the speed is reliable even if the vanilla
                    // swim-with-player goal hasn't kicked in yet.
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 60, 0, true, false, false));
                }
            }
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.OCEANS_ACTIVE, -1);
    }
}
