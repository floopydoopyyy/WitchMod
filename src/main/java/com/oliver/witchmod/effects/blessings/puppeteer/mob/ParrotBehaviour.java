package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the parrot. flies freely but slowly (and drifts down slowly when it doesn't), no fall damage, no bite.
 * right-click does what a parrot would: with a jukebox playing nearby, hold to DANCE (the party bob, and you regenerate
 * fast); otherwise tap at a player or humanoid mob to PERCH on their shoulder (riding them — EntityCamelSeatMixin seats
 * you there — and hopping off as a shoulder parrot does); otherwise tap at any mob, even through walls, to MIMIC it.
 * its sharp eyes show you every mob nearby through walls (PuppetSenses, for you alone). left-click squawks. a cookie
 * still kills it. PUPPET_DASH_POWER is 1 while it dances (synced, for the model).
 */
public final class ParrotBehaviour implements PuppetBehaviour {
    public static final ParrotBehaviour INSTANCE = new ParrotBehaviour();

    private ParrotBehaviour() {}

    public static boolean parrotPuppet(@Nullable Entity entity) {
        return entity instanceof Player p && PuppetType.byId(p.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PARROT;
    }

    /** partying: right-click held with music in earshot (both sides). */
    public static boolean dancing(Player player) {
        return parrotPuppet(player) && player.getData(WitchModAttachments.PUPPET_DASH_POWER) == 1;
    }

    public static boolean hearsMusic(Player player) {
        return Puppets.hearsMusic(player, Config.PUPPETEER_PARROT_MUSIC_RADIUS.get());
    }

    /** someone a parrot will sit on: a player, or a mob shaped like one (illagers, zombies, skeletons, piglins, villagers, witches). */
    public static boolean perchable(@Nullable Entity entity) {
        return entity instanceof Player
                || entity instanceof net.minecraft.world.entity.monster.AbstractIllager
                || entity instanceof net.minecraft.world.entity.monster.Zombie z && !z.isBaby()
                || entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                || entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin pig && !pig.isBaby()
                || entity instanceof net.minecraft.world.entity.npc.AbstractVillager v && !v.isBaby()
                || entity instanceof net.minecraft.world.entity.monster.Witch;
    }

    /** the shoulder you'd land on right now: a perchable someone in reach, with a shoulder free (two each, as vanilla). */
    @Nullable
    public static LivingEntity perchTarget(Player player) {
        LivingEntity at = Puppets.beamHit(player, player.entityInteractionRange()).entity();
        if (!perchable(at) || at == player || at.getPassengers().size() >= 2
                || at.getPassengers().stream().anyMatch(p -> !parrotPuppet(p))) {
            return null;
        }
        return at;
    }

    /** the mob you'd mimic: the first one along your aim, walls or no walls (a parrot sees them all). */
    @Nullable
    public static Mob mimicTarget(Player player) {
        double range = Config.PUPPETEER_PARROT_MIMIC_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.0),
                e -> e instanceof Mob && e.isAlive() && !e.isSpectator(), 0.3F);
        return hit != null && hit.getEntity() instanceof Mob mob ? mob : null;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        Holds.tickHold(player, type); // (counts the hold, so letting go of a dance isn't read as a tap)
        // dancing: right-click held with a record playing near — the party bob, and quick regeneration
        boolean dance = Holds.HELD.contains(player.getUUID()) && hearsMusic(player);
        if (dance != dancing(player)) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, dance ? 1 : 0);
        }
        if (dance) {
            int amp = Config.PUPPETEER_PARROT_DANCE_REGEN_LEVEL.get() - 1;
            MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
            if (regen == null || regen.getAmplifier() < amp || regen.getDuration() < 10) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, amp, false, false, true));
            }
        }
        // on a shoulder: off it the way a shoulder parrot comes off — the perch falls, jumps, swims, sleeps or is hit
        if (player.getVehicle() instanceof LivingEntity perch && perchable(perch)) {
            if (!perch.isAlive() || perch.fallDistance > 0.5F || perch.isInWater() || perch.isSleeping() || perch.hurtTime > 0
                    || perch.isInPowderSnow || (perch instanceof Player p && p.isFallFlying())) {
                hopOff(player, perch);
            }
        }
    }

    private static void hopOff(ServerPlayer player, Entity perch) {
        player.stopRiding();
        if (perch instanceof ServerPlayer carrier) {
            HorseBehaviour.syncRiders(carrier);
        }
    }

    /** a tap (not a dance): perch on the shoulder you're looking at, else mimic the mob you're looking at. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge >= Holds.MIN_CHARGE && hearsMusic(player)) {
            return; // that was a dance
        }
        LivingEntity perch = perchTarget(player);
        if (perch != null && !player.isPassenger()) {
            player.startRiding(perch, true);
            if (perch instanceof ServerPlayer carrier) {
                HorseBehaviour.syncRiders(carrier); // their own client is told you're aboard
            }
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PARROT_FLY, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        Mob mob = mimicTarget(player);
        if (mob == null || !Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_PARROT_MIMIC_COOLDOWN_TICKS.get());
        // the parrot's own impression of a monster (as a real parrot does it); anything it has none for, it just copies
        SoundEvent sound = com.oliver.witchmod.mixin.ParrotInvoker.witchmodImitatedSound(mob.getType());
        float pitch = net.minecraft.world.entity.animal.Parrot.getPitch(player.getRandom());
        if (sound == SoundEvents.PARROT_AMBIENT) {
            SoundEvent own = ((com.oliver.witchmod.mixin.MobInvoker) mob).witchmodAmbientSound();
            if (own != null) {
                sound = own;
                pitch += 0.4F; // a parrot's voice is higher
            }
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.9F, pitch);
        Puppets.countAction(player);
    }

    /** a parrot charges nothing — holding is just for the dance. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return true;
    }

    /** left-click: a squawk. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (!Puppets.ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_PARROT_SQUAWK_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.PLAYERS, 1.0F,
                net.minecraft.world.entity.animal.Parrot.getPitch(player.getRandom()));
    }

    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    /** a parrot can't hurt anything. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** it flutters: no fall damage. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** a cookie is poison to a parrot — feeding it one still kills it, as it would the real thing. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (!stack.is(ItemTags.PARROT_POISONOUS_FOOD)) {
            return false;
        }
        if (!user.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            stack.consume(1, user);
            puppet.addEffect(new MobEffectInstance(MobEffects.POISON, 900));
            puppet.hurt(puppet.damageSources().playerAttack(user), Float.MAX_VALUE);
        }
        return true;
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
    }

    /** off any shoulder when you step out. */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        if (parrotPuppet(player) && player.getVehicle() != null) {
            hopOff(player, player.getVehicle());
        }
    }

    /** dance with music near; else perch on a shoulder, or mimic a mob; left-click squawks. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (hearsMusic(player)) {
            out.add(List.of(hold(Key.USE)), net.minecraft.network.chat.Component.translatable("witchmod.puppeteer.action.parrot_dance"), 0L);
        }
        if (perchTarget(player) != null && !player.isPassenger()) {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.parrot_perch", 0L);
        } else {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.parrot_mimic", PuppetControls.ready(player));
        }
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.parrot_squawk", PuppetControls.ready2(player));
    }
}
