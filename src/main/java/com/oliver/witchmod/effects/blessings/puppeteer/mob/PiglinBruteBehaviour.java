package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the piglin brute. the vindicator's axe kit (big swings, the committed hold-to-Lunge, the miss recovery,
 * shared lunge settings) with the piglin's side: a tap of right-click rallies piglins and brutes, it's told when
 * someone opens a chest or breaks gold nearby, and it zombifies outside the nether. like the real brute it ignores
 * gold — no bartering, no sparing gold-wearers — and fears nothing.
 */
public final class PiglinBruteBehaviour implements PuppetBehaviour {
    public static final PiglinBruteBehaviour INSTANCE = new PiglinBruteBehaviour();

    /** the shared axe kit. */
    private static final VindicatorBehaviour AXE = VindicatorBehaviour.INSTANCE;

    private PiglinBruteBehaviour() {}

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        if (PiglinBehaviour.tickZombify(player)) {
            // a zombified piglin now: drop the axe kit's state and stats
            Lunges.endLunge(player);
            AXE.clear(player, player.getData(WitchModAttachments.PUPPET_DATA));
            EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, IronGolemBehaviour.GOLEM_ATTACK_SPEED_ID);
            EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, CreeperBehaviour.FUSE_SLOW_ID);
            return;
        }
        AXE.tick(player, type, ticksRemaining);
        PiglinBehaviour.tickAlert(player);
    }

    /** a tap rallies; a held charge is the lunge. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge >= Holds.MIN_CHARGE) {
            AXE.releaseHeld(player, type, charge);
        } else if (Puppets.ready2(player) && !VindicatorBehaviour.recovering(player)
                && !Lunges.LUNGES.containsKey(player.getUUID())) {
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                    player.level().getGameTime() + Config.PUPPETEER_PIGLIN_BRUTE_RALLY_COOLDOWN_TICKS.get());
            PiglinBehaviour.rally(player);
        }
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        AXE.lungeContact(player, type, lunge);
    }

    @Override
    public void lungeExpired(ServerPlayer player, PuppetType type) {
        AXE.lungeExpired(player, type);
    }

    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return AXE.blocksMelee(player, type, target);
    }

    @Override
    public void meleeSwung(Player player, PuppetType type) {
        AXE.meleeSwung(player, type);
    }

    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        return AXE.meleeDamage(player, type, swingStrength);
    }

    /** the axe's swing speed, and the zombifying clock carries on where the mob left off. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        AXE.possess(player, type, mob, data);
        Possession.HOGLIN_TIME.put(player.getUUID(), data.getInt("TimeInOverworld"));
    }

    /** hold: the lunge; tap: the rally. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.USE), "witchmod.puppeteer.action.piglin_rally", PuppetControls.ready2(player));
    }
}
