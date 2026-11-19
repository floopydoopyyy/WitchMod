package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.CreeperBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.IronGolemBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the vindicator puppet — and the piglin brute's axe kit (see PiglinBruteBehaviour), which shares it. */
public final class VindicatorBehaviour implements PuppetBehaviour {
    public static final VindicatorBehaviour INSTANCE = new VindicatorBehaviour();

    private VindicatorBehaviour() {}

    /** a vindicator lunge in flight → its charge-scaled damage multiplier; removed when it connects (else it missed). */
    private static final Map<UUID, Float> LUNGE_BONUS = EffectState.sessionConcurrent("puppeteer/vindicator/lunge_bonus");

    /** a lunge just over (hit or miss) → the tick its heavy aftermath ends. */
    private static final Map<UUID, Long> AFTERMATH_UNTIL = EffectState.sessionConcurrent("puppeteer/vindicator/aftermath_until");

    private static final ResourceLocation AFTERMATH_SLOW_ID = EffectUtil.modifierId("puppeteer_lunge_aftermath_slow");

    /** a vindicator that MISSED a lunge → the tick its recovery lockout ends (can't swing or lunge until then). */
    private static final Map<UUID, Long> RECOVER_UNTIL = EffectState.sessionConcurrent("puppeteer/vindicator/recover_until");

    /** a vindicator lunge's share of full reach from its charge (PUPPET_DASH_POWER); even a tap commits a real gap-closer. */
    private static double vindShare(Player player) {
        return 0.6 + 0.4 * Mth.clamp(player.getData(WitchModAttachments.PUPPET_DASH_POWER) / 100.0, 0.0, 1.0);
    }

    public static int vindLungeTicks(Player player) {
        double ticks = Config.PUPPETEER_VINDICATOR_LUNGE_TICKS.get() * vindShare(player) * reach(player);
        if (brute(player)) {
            ticks *= Config.PUPPETEER_PIGLIN_BRUTE_LUNGE_DISTANCE.get() / Config.PUPPETEER_PIGLIN_BRUTE_LUNGE_SPEED.get();
        }
        return Math.max(3, (int) Math.round(ticks));
    }

    public static double vindLungeSpeed(Player player) {
        double speed = Config.PUPPETEER_VINDICATOR_LUNGE_SPEED.get() * vindShare(player) * reach(player);
        return brute(player) ? speed * Config.PUPPETEER_PIGLIN_BRUTE_LUNGE_SPEED.get() : speed;
    }

    /** the piglin brute's lunge: slower than the vindicator settings, but it carries further. */
    private static boolean brute(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PIGLIN_BRUTE;
    }

    /** how far its lunge carries: a vindicator's goes further than a brute's (and Johnny's further still). */
    private static double reach(Player player) {
        double reach = PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VINDICATOR
                ? Config.PUPPETEER_VINDICATOR_LUNGE_REACH.get() : 1.0;
        return johnny(player) ? reach * Config.PUPPETEER_JOHNNY_LUNGE_REACH.get() : reach;
    }

    /** the lunge's cooldown, hit or miss: a vindicator's own, the brute's, none at all for Johnny. */
    private static int lungeCooldown(ServerPlayer player, PuppetType type) {
        if (johnny(player)) {
            return 0;
        }
        return type == PuppetType.VINDICATOR ? Config.PUPPETEER_VINDICATOR_LUNGE_COOLDOWN_TICKS.get()
                : Config.PUPPETEER_PIGLIN_BRUTE_LUNGE_COOLDOWN_TICKS.get();
    }

    // --- vindicator ---------------------------------------------------------------------------------

    /** a vindicator named Johnny (the real one's flag, or just the name) — the berserker. */
    public static boolean johnny(Player player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VINDICATOR
                && (tag.getBoolean("Johnny") || tag.getString("CustomName").contains("\"Johnny\""));
    }

    private static final ResourceLocation JOHNNY_SPEED_ID = EffectUtil.modifierId("puppeteer_johnny_speed");

    /** vindicator stats: the brute's weapon-style swing speed; Johnny's faster swing and extra pace on top. */
    private static void applyVindicatorStats(ServerPlayer player, PuppetType type) {
        boolean berserk = johnny(player);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            double speed = type == PuppetType.PIGLIN_BRUTE ? Config.PUPPETEER_PIGLIN_BRUTE_ATTACK_SPEED.get()
                    : berserk ? Config.PUPPETEER_JOHNNY_ATTACK_SPEED.get() : Config.PUPPETEER_VINDICATOR_ATTACK_SPEED.get();
            EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, GOLEM_ATTACK_SPEED_ID, speed - attackSpeed.getBaseValue(),
                    AttributeModifier.Operation.ADD_VALUE);
        }
        if (berserk) {
            EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, JOHNNY_SPEED_ID, Config.PUPPETEER_JOHNNY_BONUS.get(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /**
     * Johnny can't help himself: whenever his swing is charged and something living is within reach and in sight, he
     * swings at it — anything at all, as the real Johnny does.
     */
    private static void tickJohnny(ServerPlayer player) {
        if (!johnny(player) || player.getAttackStrengthScale(0.5F) < 1.0F || player.isSpectator()) {
            return;
        }
        double reach = player.entityInteractionRange();
        LivingEntity nearest = null;
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable()
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                        && player.hasLineOfSight(e))) {
            if (nearest == null || e.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = e;
            }
        }
        if (nearest != null && nearest.distanceToSqr(player) <= reach * reach) {
            player.attack(nearest);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /** vindicator upkeep: the committed Lunge (charge while held, dash + heavy hit on release) and Johnny's auto-swings. */
    private static void tickVindicator(ServerPlayer player, PuppetType type) {
        boolean lunging = LUNGES.containsKey(player.getUUID());
        if (!lunging && !recovering(player)) {
            tickHold(player, type); // builds the charge while right-click is held
        }
        tickLunge(player, type);
        vindicatorChargeSlow(player); // a heavy wind-up, and a heavy aftermath
        if (!lunging && !recovering(player)) {
            tickJohnny(player);
        }
    }

    /** the lunge's weight: slowed hard while winding it up, and again in the moments after it's spent. */
    private static void vindicatorChargeSlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        Long after = AFTERMATH_UNTIL.get(player.getUUID());
        boolean spent = after != null && player.level().getGameTime() < after && !LUNGES.containsKey(player.getUUID());
        if (spent != speed.hasModifier(AFTERMATH_SLOW_ID)) {
            if (spent) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, AFTERMATH_SLOW_ID, -Config.PUPPETEER_VINDICATOR_LUNGE_AFTER_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(AFTERMATH_SLOW_ID);
            }
        }
        if (after != null && player.level().getGameTime() >= after) {
            AFTERMATH_UNTIL.remove(player.getUUID());
        }
        double amount = Config.PUPPETEER_VINDICATOR_LUNGE_CHARGE_SLOW.get();
        boolean slow = amount > 0.0 && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
        if (slow != speed.hasModifier(FUSE_SLOW_ID)) {
            if (slow) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(FUSE_SLOW_ID);
            }
        }
    }

    /** whether a vindicator is in the brief lockout after a MISSED lunge — the commit. */
    public static boolean recovering(ServerPlayer player) {
        Long until = RECOVER_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /**
     * vindicator: let go of the committed Lunge — a forward dash your own client drives (see PuppeteerClient.tickLunge).
     * the first thing it reaches takes a charge-scaled heavy blow; a miss costs a recovery. Johnny's lunge is enhanced.
     */
    private static void vindicatorLunge(ServerPlayer player, int charge) {
        if (charge < MIN_CHARGE || !ready(player) || LUNGES.containsKey(player.getUUID()) || recovering(player)) {
            return;
        }
        int power = (int) Math.round(Mth.clamp(charge / (float) Config.PUPPETEER_VINDICATOR_LUNGE_CHARGE_TICKS.get(), 0.0F, 1.0F) * 100);
        double max = johnny(player) ? Config.PUPPETEER_JOHNNY_LUNGE_BONUS_MAX.get() : Config.PUPPETEER_VINDICATOR_LUNGE_BONUS_MAX.get();
        float bonus = (float) Mth.lerp(power / 100.0, Config.PUPPETEER_VINDICATOR_LUNGE_BONUS_MIN.get(), max);
        long now = player.level().getGameTime();
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, power);
        LUNGE_BONUS.put(player.getUUID(), bonus);
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** the lunge connects: the vindicator's base hit × the charge bonus (+ Johnny, + the axe's damage enchants), with weight. */
    private static void vindicatorLungeHit(ServerPlayer player, PuppetType type, LivingEntity e) {
        ServerLevel level = player.serverLevel();
        float amount = type.attackDamage() * LUNGE_BONUS.getOrDefault(player.getUUID(), 1.5F);
        if (johnny(player)) {
            amount *= 1.0F + Config.PUPPETEER_JOHNNY_BONUS.get().floatValue();
        }
        ItemStack weapon = mobWeapon(player, player.getData(WitchModAttachments.PUPPET_DATA));
        if (!weapon.isEmpty()) {
            amount = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level, weapon, e,
                    player.damageSources().playerAttack(player), amount);
        }
        LUNGE_BONUS.remove(player.getUUID());
        SPECIAL_DAMAGE.add(player.getUUID());
        try {
            e.hurt(player.damageSources().playerAttack(player), amount);
        } finally {
            SPECIAL_DAMAGE.remove(player.getUUID());
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        double kb = 0.5 * Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        e.setDeltaMovement(dir.x * kb, Math.max(e.getDeltaMovement().y, 0.0) + kb * 0.3, dir.z * kb);
        e.hurtMarked = true;
        level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickVindicator(player, type);
    }

    /** the committed lunge. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        vindicatorLunge(player, charge);
    }

    /** the first thing the lunge reaches takes the charged hit; a connect just goes on cooldown, no recovery. */
    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.4),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            vindicatorLungeHit(player, type, e); // (the lunge stops dead on the first thing it reaches, with the swing)
            Lunges.endLunge(player);
            aftermath(player);
            Puppets.cooldown(player, lungeCooldown(player, type));
            return;
        }
    }

    /** a lunge that ran its course without connecting is a MISS — the commit's recovery lockout. */
    @Override
    public void lungeExpired(ServerPlayer player, PuppetType type) {
        if (LUNGE_BONUS.remove(player.getUUID()) != null) {
            aftermath(player);
            RECOVER_UNTIL.put(player.getUUID(), player.level().getGameTime() + Config.PUPPETEER_VINDICATOR_LUNGE_RECOVERY_TICKS.get());
            if (type == PuppetType.VINDICATOR) {
                Puppets.cooldown(player, lungeCooldown(player, type)); // a vindicator's lunge cools down hit or miss
            }
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
    }

    /** the lunge spent: a heavy few moments after. */
    private static void aftermath(ServerPlayer player) {
        AFTERMATH_UNTIL.put(player.getUUID(), player.level().getGameTime() + Config.PUPPETEER_VINDICATOR_LUNGE_AFTER_SLOW_TICKS.get());
    }

    /** locked out of swinging during a missed lunge's recovery. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return player instanceof ServerPlayer sp && recovering(sp);
    }

    @Override
    public void meleeSwung(Player player, PuppetType type) {
        IronGolemBehaviour.recordSwing(player);
    }

    /** weapon-style swing scaling, and Johnny's bonus. */
    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        float amount = IronGolemBehaviour.swingScaled(type.attackDamage(), swingStrength);
        if (johnny(player)) {
            amount *= 1.0F + Config.PUPPETEER_JOHNNY_BONUS.get().floatValue();
        }
        return amount;
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        applyVindicatorStats(player, type);
    }

    /** Johnny's hits spark. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (johnny(player) && event.getSource().getDirectEntity() == player && event.getEntity() != player
                && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID()) && event.getEntity().level() instanceof ServerLevel hitLevel) {
            LivingEntity hit = event.getEntity();
            hitLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, hit.getX(), hit.getY(0.6), hit.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
            hitLevel.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY(0.6), hit.getZ(), 10, 0.3, 0.3, 0.3, 0.4);
            hitLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, hit.getX(), hit.getY(1.0) + 0.2, hit.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        LUNGE_BONUS.remove(player.getUUID());
        RECOVER_UNTIL.remove(player.getUUID());
        AFTERMATH_UNTIL.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, JOHNNY_SPEED_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, AFTERMATH_SLOW_ID);
    }

    /** the lunge — and, named Johnny, the warning. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        if (johnny(player)) {
            out.note(Component.translatable("witchmod.puppeteer.johnny").withStyle(ChatFormatting.RED));
        }
    }
}
