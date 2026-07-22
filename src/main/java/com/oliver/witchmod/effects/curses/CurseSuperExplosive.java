package com.oliver.witchmod.effects.curses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Curses;

/**
 * You're a bit of a hazard to be around (master-spec Super Explosive). Every hit you take is a small,
 * <b>constant</b> chance of simply going off — a real explosion with full damage, big knockback, and block
 * damage gated on {@code mobGriefing} via {@link Level.ExplosionInteraction#MOB}.
 *
 * <p><b>You take only a fraction of your own blast.</b> You're standing at dead centre, so at full damage it
 * would just execute you every time; scaling your share down leaves it a serious hit without being an
 * instant death, while everyone else nearby takes it in full.
 *
 * <p>Both of those tweaks go through vanilla's own {@link net.minecraft.world.level.ExplosionDamageCalculator}
 * extension points ({@code getEntityDamageAmount} / {@code getKnockbackMultiplier}) rather than trying to
 * intercept the damage afterwards, so the explosion stays an ordinary explosion in every other respect.
 *
 * <p><b>Trap worth remembering:</b> the explosion is created with a {@code null} source ENTITY on purpose.
 * {@code Explosion} gathers its victims with {@code Level.getEntities(source, box)}, and that first argument
 * is the entity to <i>exclude</i> — so naming the player as the source silently leaves them out of their own
 * blast, taking neither damage nor knockback. The player is carried on the damage source instead (for kill
 * attribution) and in the calculator (for block resistance and the self-damage scale).
 *
 * <p>The trigger lives in {@code CurseEventHandler} — it needs the incoming-damage event, not a tick.
 */
public final class CurseSuperExplosive extends Effect {
    public CurseSuperExplosive() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 60, () -> Items.TNT);
    }

    /** You find out the first time you go off (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Hook for taking a hit — see {@code CurseEventHandler}. */
    public static void detonate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.explode(
                // The source entity is deliberately NULL, not the player. Explosion collects its victims
                // with Level.getEntities(source, box), whose first argument is the entity to EXCLUDE — so
                // naming the player as source quietly omits them from their own blast, and they take no
                // damage and no knockback at all. Attribution is preserved by the damage source instead.
                null,
                level.damageSources().explosion(null, player),
                new SelfSparingBlast(player),
                player.getX(), player.getY(), player.getZ(),
                Config.SUPER_EXPLOSIVE_POWER.get().floatValue(),
                false,
                Level.ExplosionInteraction.MOB); // MOB = vanilla's own mobGriefing gate
        Curses.SUPER_EXPLOSIVE.value().markDiscoveredByVictim(player);
    }

    /**
     * A normal entity-sourced explosion, except it hits its owner for a fraction of the damage and throws
     * everyone harder. Extending {@link EntityBasedExplosionDamageCalculator} keeps vanilla's block-
     * resistance behaviour for a player-caused blast.
     */
    private static final class SelfSparingBlast extends EntityBasedExplosionDamageCalculator {
        private final Entity owner;

        private SelfSparingBlast(Entity owner) {
            super(owner);
            this.owner = owner;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity) {
            float damage = super.getEntityDamageAmount(explosion, entity);
            if (entity != owner) {
                return damage; // bystanders get the full thing
            }
            return damage * (Config.SUPER_EXPLOSIVE_SELF_DAMAGE_PERCENT.get() / 100.0F);
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return Config.SUPER_EXPLOSIVE_KNOCKBACK_MULTIPLIER.get().floatValue();
        }
    }
}
