package com.oliver.witchmod.effects.blessings.puppeteer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * what one kind of puppet does. every hook is optional — a puppet overrides only what makes it different, and the
 * shared machinery (Possession, Holds, Lunges, PuppetCombat, PuppetEvents) calls in at the right moment. one
 * behaviour can serve several {@link PuppetType}s (the zombie family, the horses...), so each hook is told which.
 * the type ↔ behaviour wiring is {@link PuppetBehaviours}.
 */
public interface PuppetBehaviour {
    // --- wearing it ---

    /** server, every tick while worn. */
    default void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {}

    /** just became this puppet, before the mob's weapon is copied onto the kit: hand over any kit (bows, crossbows). */
    default void giveKit(ServerPlayer player, PuppetType type) {}

    /** just became this puppet (attributes and data set): stats, starting state, flight. */
    default void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {}

    /**
     * stepping out — called on EVERY behaviour, whatever you were (a puppet can change type while worn: zombification,
     * infection), so each drops its own per-player state. {@code data} is the saved mob, about to be put back.
     */
    default void clear(ServerPlayer player, CompoundTag data) {}

    /** stepping out, after your own inventory is back (called on every behaviour). */
    default void released(ServerPlayer player) {}

    // --- moves ---

    /** a right-click (held moves go through {@link #releaseHeld} instead). by default it just counts as an action. */
    default void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (type.move() != PuppetMove.NONE && Puppets.ready(player)) {
            Puppets.countAction(player);
        }
    }

    /** true while this puppet can't use any move, or step out (a piglin locked in a barter). */
    default boolean stunned(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** crouch + right-click: true if this puppet uses it (otherwise it steps out, as ever). */
    default boolean crouchUse(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** whether the held right-click charges this tick. */
    default boolean canCharge(ServerPlayer player, PuppetType type) {
        return Puppets.ready(player);
    }

    /** the held right-click just started charging (a wind-up sound, say). */
    default void chargeStarted(ServerPlayer player, PuppetType type) {}

    /** let go of a held right-click after {@code charge} ticks. by default it just counts as an action. */
    default void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        Puppets.countAction(player);
    }

    /** a charged jump (holding space): started (phase 0), or launched after {@code charge} ticks (phase 1). */
    default void chargedJump(ServerPlayer player, PuppetType type, int phase, int charge) {}

    /** a left-click swing (reported even at thin air). */
    default void swing(ServerPlayer player, PuppetType type) {}

    /** both sides: true if this puppet has a held LEFT-click move (the client reports the attack button held / let go). */
    default boolean holdsAttack(PuppetType type) {
        return false;
    }

    /** let go of the held left-click after {@code charge} ticks (a ghast's volley, a screaming goat's shriek). */
    default void releaseAttackHeld(ServerPlayer player, PuppetType type, int charge) {}

    /** both sides: true if this puppet's left-click does something (the client reports a swing even at thin air). */
    default boolean usesLeftClick(PuppetType type) {
        return false;
    }

    /** a lunge's collisions, each tick of its charge (after any wind-up). */
    default void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {}

    /** a lunge ran its whole course without connecting (a vindicator's miss). */
    default void lungeExpired(ServerPlayer player, PuppetType type) {}

    /** another player uses {@code stack} on this puppet: true if it did something (milked, sheared, fed...). */
    default boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        return false;
    }

    // --- dealing damage ---

    /** both sides: true to stop this melee swing outright (no melee at all, or not right now). */
    default boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return false;
    }

    /** a melee swing that wasn't blocked (before vanilla resets the attack charge). */
    default void meleeSwung(Player player, PuppetType type) {}

    /** server, after the victim's grudge is set: true if this puppet lands the hit its own way (the swing is cancelled). */
    default boolean takesOverMelee(ServerPlayer player, PuppetType type, Entity target) {
        return false;
    }

    /** false: a direct hit from this puppet does no damage at all. */
    default boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return true;
    }

    /** a melee hit's damage before the mob's weapon enchantments ({@code swingStrength}: the attack charge, if caught). */
    default float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        return type.attackDamage();
    }

    /** a melee hit's damage after the weapon enchantments. */
    default float finalMeleeDamage(ServerPlayer player, PuppetType type, float amount) {
        return amount;
    }

    /** this puppet's damage landed on something (the source's entity is the puppet). */
    default void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {}

    /** this puppet killed something. */
    default void killed(ServerPlayer player, PuppetType type, LivingEntity victim) {}

    /** an arrow this puppet fired has just spawned. */
    default void arrowFired(ServerPlayer player, PuppetType type, Arrow arrow) {}

    /** a projectile this puppet fired is landing (cancel the event to let it pass through). */
    default void ownProjectileHit(ServerPlayer player, PuppetType type, net.minecraft.world.entity.projectile.Projectile projectile,
            @Nullable Entity struck, net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {}

    // --- taking damage ---

    /** a projectile is striking this puppet (cancel the event to bat it away). */
    default void struckByProjectile(ServerPlayer player, PuppetType type, net.minecraft.world.entity.projectile.Projectile projectile,
            net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {}

    /** incoming damage to the puppet, early: cancel it or scale it on the event. */
    default void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {}

    /** the puppet would die: true if it carries on in another form instead (infected, split). */
    default boolean cheatsDeath(ServerPlayer player, PuppetType type, DamageSource source) {
        return false;
    }

    /** the puppet took a hit (its health already lowered), before the flinch and knockback. */
    default void damaged(ServerPlayer player, PuppetType type, DamageSource source) {}

    /** the puppet took a hit from a living attacker, after the knockback. */
    default void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {}

    /** nothing will pick this puppet as a target right now (an axolotl playing dead). */
    default boolean untargetable(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** struck by lightning (a creeper charges). */
    default void struckByLightning(ServerPlayer player, PuppetType type) {}

    // --- the hud ---

    /** client: the controls the hud shows for this puppet — by default just its move. */
    default void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
    }

    /** client: the mouse wheel scrolls this puppet's belt (an evoker's spells, a warden's abilities) rather than the hotbar. */
    default boolean scrolls(PuppetType type) {
        return false;
    }

    /** the wheel turned one step (dir -1 / +1) — see {@link #scrolls}. */
    default void scroll(ServerPlayer player, PuppetType type, int dir) {}

    // --- possession ---

    /** how long possessing this mob takes (a warden fights it). */
    default int bindingTicks(PuppetType type) {
        return com.oliver.witchmod.Config.PUPPETEER_POSSESS_TICKS.get();
    }

    /** each tick of the possession taking hold ({@code progress} 0..1): the mob's own reaction, if it has one. */
    default void bindingTick(ServerPlayer player, net.minecraft.world.entity.Mob mob, float progress, long ticks) {}

    /** the possession finished or was broken off: undo whatever {@link #bindingTick} left on the mob. */
    default void bindingEnded(net.minecraft.world.entity.Mob mob) {}

    // --- body ---

    /** both sides: the body's scale on top of yours (a slime's size). */
    default float bodyScale(Player player, PuppetType type) {
        return 1.0F;
    }

    /** both sides: the body's final size, after the usual sizing (a burrowed warden's low mound). */
    default net.minecraft.world.entity.EntityDimensions size(Player player, PuppetType type, net.minecraft.world.entity.EntityDimensions dims) {
        return dims;
    }

    /** both sides: true if the body takes the mob's full width, not yours (a ghast's 4x4x4). */
    default boolean fullWidth(PuppetType type) {
        return false;
    }
}
