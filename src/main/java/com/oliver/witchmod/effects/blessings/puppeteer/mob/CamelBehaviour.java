package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/** puppeteer: the camel puppet. */
public final class CamelBehaviour implements PuppetBehaviour {
    public static final CamelBehaviour INSTANCE = new CamelBehaviour();

    private CamelBehaviour() {}

    // --- camel --------------------------------------------------------------------------------------

    /**
     * camel: let go of the dash charge — the real camel's dash (22.2222 x scale x its speed forward, 1.4285 x scale x its
     * jump up), boosted, on vanilla's jump-bar scale (a tap 40%, a full bar the whole thing).
     */
    private static void camelDash(ServerPlayer player, int charge) {
        if (!ready(player) || charge <= 0) {
            return;
        }
        int full = Config.PUPPETEER_CAMEL_DASH_CHARGE_TICKS.get();
        float scale = charge >= full ? 1.0F : 0.4F + 0.4F * charge / (float) full;
        double boost = Config.PUPPETEER_CAMEL_DASH_BOOST.get();
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
        double forward = 22.2222 * scale * 0.09 * boost; // vanilla: x the camel's movement speed (0.09)
        double up = 1.4285 * scale * 0.42; // vanilla: x the camel's jump power
        player.setDeltaMovement(look.x * forward, up, look.z * forward);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CAMEL_DASH, SoundSource.PLAYERS, 1.0F, 1.0F);
        cooldown(player, Config.PUPPETEER_CAMEL_DASH_COOLDOWN_TICKS.get());
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** a camel puppet (both sides — EntityCamelSeatMixin seats its two riders like the real camel's). */
    public static boolean camelPuppet(Player player) {
        return player.hasData(WitchModAttachments.PUPPET_TYPE)
                && PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.CAMEL;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        Holds.tickHold(player, type); // the dash's charge
        HorseBehaviour.syncRiders(player);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        camelDash(player, charge);
    }

    /** a camel steps up 1.5 blocks, like the real one. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        EffectUtil.addModifier(player, Attributes.STEP_HEIGHT, HorseBehaviour.HORSE_STEP_ID, 0.9, AttributeModifier.Operation.ADD_VALUE);
    }

    /** riders, a saddle. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        return HorseBehaviour.horseInteract(user, hand, stack, puppet, type);
    }
}
