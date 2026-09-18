package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Curses;

/**
 * you go off when you go down. Dying detonates you properly: a real
 * {@link Level#explode} with full damage, knockback and block damage, so everything nearby gets the same
 * treatment a creeper would hand out — <b>and your dropped inventory is destroyed in the blast</b>, which
 * makes dying with this genuinely expensive rather than merely loud.
 *
 * <p><b>The blast is deliberately deferred by a tick</b>, and that isn't cosmetic. {@code LivingEntity.die}
 * fires {@code LivingDeathEvent} at the very top and only calls {@code dropAllDeathLoot} much further down,
 * so an explosion triggered straight from the death event would happen while the items <i>do not exist
 * yet</i> and would leave the whole inventory sitting neatly on the floor. One tick later the drops are real
 * entities and the explosion destroys them like any other item on the ground.
 *
 * <p>World damage is gated on the {@code mobGriefing} gamerule via
 * {@link Level.ExplosionInteraction#MOB}, which is vanilla's own switch for exactly that.
 *
 * <p>sacrificial item is gunpowder (super explosive is tnt) — the two are kept distinct.
 */
public final class CurseExplosive extends Effect {
    /** deaths waiting to go off — see the class note on why this can't happen immediately. */
    private static final List<PendingBlast> PENDING = new ArrayList<>();

    private record PendingBlast(ServerPlayer player, ServerLevel level, Vec3 pos, long fireAt) {}

    public CurseExplosive() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 65, () -> Items.GUNPOWDER);
    }

    /** you find out by dying (Rule 2) — there's no missing it. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** hook for dying — see {@code CurseEventHandler}. Queues the blast for a tick's time. */
    public static void onDeath(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        PENDING.add(new PendingBlast(player, level, player.position(),
                level.getGameTime() + Config.EXPLOSIVE_DELAY_TICKS.get()));
        Curses.EXPLOSIVE.value().markDiscoveredByVictim(player);
    }

    /** detonates anything whose moment has come. Called once per server tick from {@code CurseEventHandler}. */
    public static void tickPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(blast -> {
            if (blast.level().getGameTime() < blast.fireAt()) {
                return false;
            }
            // the dying player stays the source so the kill is attributed to them, not to thin air.
            blast.level().explode(blast.player(), blast.pos().x, blast.pos().y, blast.pos().z,
                    Config.EXPLOSIVE_POWER.get().floatValue(),
                    Config.EXPLOSIVE_CREATES_FIRE.get(),
                    Level.ExplosionInteraction.MOB); // MOB = vanilla's own mobGriefing gate
            return true;
        });
    }
}
