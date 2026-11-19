package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetHunters.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.BreezeBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.EvokerBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.IronGolemBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.LlamaBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SnowGolemBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: damage in and out — the puppet's own base damage, what it shrugs off, and its health taking the hits. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PuppetCombat {
    private PuppetCombat() {}

    /** ticks of "invulnerability" after the puppet takes a hit, like vanilla's, so rapid sources don't shred it. */
    private static final int PUPPET_IFRAMES = 10;

    public static final Map<UUID, Long> LAST_PUPPET_HIT = EffectState.sessionConcurrent("puppeteer/puppet_combat/last_puppet_hit");

    /** players mid-detonation — their own blast doesn't hurt them (the creeper is spent instead). */
    public static final Set<UUID> EXPLODING = EffectState.sessionSet("puppeteer/puppet_combat/exploding");

    /**
     * projectiles landing: on a puppet (a breeze bats them back, a snowball stings a blaze), from a puppet (a piglin's
     * bolts pass through anyone in gold), and the tagged ones puppets fire, which count even after the puppet's been
     * stepped out of — a breeze's gale bursts big, a llama's spit only ever counts once, a snow golem's snowballs hurt.
     */
    @SubscribeEvent
    public static void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        net.minecraft.world.entity.projectile.Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.phys.HitResult result = event.getRayTraceResult();
        Entity struck = result instanceof net.minecraft.world.phys.EntityHitResult hit ? hit.getEntity() : null;
        if (struck instanceof ServerPlayer victim) {
            PuppetType type = possessed(victim);
            if (type != null) {
                type.behaviour().struckByProjectile(victim, type, projectile, event);
                if (event.isCanceled()) {
                    return;
                }
            }
        }
        if (projectile.getOwner() instanceof ServerPlayer owner) {
            PuppetType type = possessed(owner);
            if (type != null) {
                type.behaviour().ownProjectileHit(owner, type, projectile, struck, event);
                if (event.isCanceled()) {
                    return;
                }
            }
        }
        if (projectile instanceof net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge charge && galeImpact(charge, result)) {
            event.setCanceled(true);
            return;
        }
        if (projectile instanceof net.minecraft.world.entity.projectile.LlamaSpit && projectile.getOwner() instanceof ServerPlayer
                && struck != null) {
            if (!projectile.getTags().add(HIT_TAG)) {
                event.setCanceled(true); // already hit something: one spit, one hit
                projectile.discard();
                return;
            }
        }
        if (projectile instanceof net.minecraft.world.entity.projectile.Snowball ball) {
            snowballLanded(level, ball, result, struck);
        }
    }

    /** an arrow a puppet fires (a stray's slows, a bogged's poisons, a pillager's bolts hit harder). */
    @SubscribeEvent
    public static void onArrowSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof net.minecraft.world.entity.projectile.Arrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        PuppetType type = possessed(shooter);
        if (type != null) {
            type.behaviour().arrowFired(shooter, type, arrow);
        }
    }

    /**
     * runs FIRST: sets a puppet's hits to its own base damage (so your blessings/curses then scale THAT), and
     * swallows damage the puppet is immune to — your own creeper blast, anything mid-possession, what its mob shrugs
     * off (PuppetBehaviour.incomingDamage: a chicken's fall, a hidden silverfish...), and fire if its mob is fire-proof.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamageFirst(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof ServerPlayer attacker && source.getEntity() == attacker
                && !SPECIAL_DAMAGE.contains(attacker.getUUID())) {
            PuppetType type = possessed(attacker);
            if (type != null) {
                PuppetBehaviour behaviour = type.behaviour();
                if (!behaviour.dealsMelee(attacker, type)) {
                    event.setCanceled(true); // no direct melee
                    return;
                }
                float amount = behaviour.meleeDamage(attacker, type, SWING_STRENGTH.remove(attacker.getUUID()));
                ItemStack weapon = mobWeapon(attacker, attacker.getData(WitchModAttachments.PUPPET_DATA));
                if (!weapon.isEmpty() && attacker.level() instanceof ServerLevel weaponLevel) {
                    // the mob's own weapon's damage enchantments (sharpness, smite...) add on, as they would for it
                    amount = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(weaponLevel, weapon, event.getEntity(), source, amount);
                }
                event.setAmount(behaviour.finalMeleeDamage(attacker, type, amount));
            }
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (EXPLODING.contains(player.getUUID()) || (BINDINGS.containsKey(player.getUUID()) && !bypass)) {
            event.setCanceled(true);
            return;
        }
        PuppetType type = possessed(player);
        if (type == null) {
            return;
        }
        type.behaviour().incomingDamage(player, type, event);
        if (!event.isCanceled() && source.is(DamageTypeTags.IS_FIRE) && type.entityType().fireImmune()) {
            event.setCanceled(true); // only the truly fire-proof
        }
    }

    /**
     * runs LAST: whatever survived every other handler (thick skinned, glass cannon, ... have had their say) hits
     * the puppet's health instead of yours.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamageLast(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        PuppetType type = possessed(player);
        if (type == null) {
            return;
        }
        event.setCanceled(true);
        if (event.getAmount() <= 0.0F) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_PUPPET_HIT.get(player.getUUID());
        if (last != null && now - last < PUPPET_IFRAMES) {
            return;
        }
        LAST_PUPPET_HIT.put(player.getUUID(), now);
        DamageSource source = event.getSource();
        PuppetBehaviour behaviour = type.behaviour();
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH) - event.getAmount();
        player.level().playSound(null, player.blockPosition(), hurtOf(type), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (health <= 0.0F) {
            if (behaviour.cheatsDeath(player, type, source)) {
                return; // not dead — carrying on in another form
            }
            release(player, source, false);
            return;
        }
        player.setData(WitchModAttachments.PUPPET_HEALTH, health);
        behaviour.damaged(player, type, source);
        // the hit has to LOOK like a hit: the red flinch (on the puppet, for everyone — no player "oof") and knockback.
        player.serverLevel().getChunkSource().broadcastAndSend(player,
                new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(player));
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
            player.knockback(0.4, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
            player.hurtMarked = true;
            behaviour.hurtBy(player, type, source, attacker);
        }
    }

    /** healing (regeneration, blessings, food...) mends the puppet instead of you. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && possessed(player) != null) {
            scaleHeal(player, event.getAmount());
            event.setCanceled(true);
        }
    }

    /** a puppet's damage landed: its weapon's after-hit enchantments, then whatever its mob does to what it hits. */
    @SubscribeEvent
    public static void onHit(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        PuppetType type = possessed(attacker);
        if (type == null) {
            return;
        }
        if (event.getSource().getDirectEntity() == attacker && event.getEntity() != attacker
                && !SPECIAL_DAMAGE.contains(attacker.getUUID()) && event.getEntity().level() instanceof ServerLevel hitLevel) {
            // the mob's own weapon's after-hit enchantments (fire aspect...)
            ItemStack weapon = mobWeapon(attacker, attacker.getData(WitchModAttachments.PUPPET_DATA));
            if (!weapon.isEmpty()) {
                net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(hitLevel, event.getEntity(), event.getSource(), weapon);
            }
        }
        // a shot or a special move gives you away too (a plain melee hit is handled on the swing)
        if ((event.getSource().getDirectEntity() != attacker || SPECIAL_DAMAGE.contains(attacker.getUUID()))
                && event.getEntity() instanceof Mob victim && victim.isAlive()) {
            revealImposter(attacker, victim);
        }
        type.behaviour().dealtDamage(attacker, type, event);
    }

    /** a mob that saw through your disguise: who it caught, and until when it remembers. */
    private record Reveal(UUID imposter, long until) {}

    private static final Map<UUID, Reveal> REVEALED = EffectState.session("puppeteer/combat/revealed");

    /** a puppet's rally / mark: who it's sent its kin after, and until when. */
    private record Rally(UUID target, long until) {}

    private static final Map<UUID, Rally> RALLIES = EffectState.sessionConcurrent("puppeteer/combat/rallies");

    /**
     * {@code player} has rallied its kin against (or marked) {@code target} for {@code ticks}. while that holds, hitting
     * one of your own kind never gives you away: the rally comes first (a stray beam into a guardian, your own mark).
     */
    public static void rallied(ServerPlayer player, Entity target, int ticks) {
        RALLIES.put(player.getUUID(), new Rally(target.getUUID(), player.level().getGameTime() + Math.max(1, ticks)));
    }

    private static boolean rallying(ServerPlayer player) {
        Rally rally = RALLIES.get(player.getUUID());
        return rally != null && rally.until() >= player.level().getGameTime();
    }

    /**
     * you attacked {@code victim}: every mob of its type nearby has seen you for the imposter you are, and turns on you
     * (as the victim itself does) — puppets or not, monsters normally leave you alone. not while you've a rally or mark
     * going: that takes priority, always.
     */
    static void revealImposter(ServerPlayer player, Mob victim) {
        double r = Config.PUPPETEER_IMPOSTER_RADIUS.get();
        if (r <= 0.0 || !(victim.level() instanceof ServerLevel level) || rallying(player)) {
            return;
        }
        long now = level.getGameTime();
        int memory = Config.PUPPETEER_IMPOSTER_MEMORY_TICKS.get();
        REVEALED.values().removeIf(reveal -> reveal.until() < now);
        for (Mob kin : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(r),
                m -> m != victim && m.isAlive() && m.getType() == victim.getType())) {
            REVEALED.put(kin.getUUID(), new Reveal(player.getUUID(), now + memory));
            turnOn(kin, player, memory);
        }
    }

    /**
     * stepping out: everything that was after YOU (the puppet) goes after the mob you've just left instead — they were
     * fighting that mob, not you. (the imposter grudges are dropped too.)
     */
    public static void handOverGrudges(ServerPlayer player, LivingEntity body) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double r = Config.PUPPETEER_GRUDGE_HANDOVER_RADIUS.get();
        int memory = Config.PUPPETEER_IMPOSTER_MEMORY_TICKS.get();
        REVEALED.values().removeIf(reveal -> reveal.imposter().equals(player.getUUID()));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r), m -> m.isAlive() && m != body)) {
            boolean after = mob.getTarget() == player || mob.getLastHurtByMob() == player
                    || mob instanceof net.minecraft.world.entity.NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget())
                    || mob.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == player
                    || mob.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT).map(player.getUUID()::equals).orElse(false);
            if (!after) {
                continue;
            }
            if (mob.getLastHurtByMob() == player) {
                mob.setLastHurtByMob(body);
            }
            turnOn(mob, body, memory);
        }
    }

    /** {@code mob} goes for {@code target}: target, a neutral's lasting anger, and a brain mob's memories. */
    private static void turnOn(Mob mob, LivingEntity player, int ticks) {
        FORCED_TARGET.add(mob.getUUID()); // lets onTarget through even for an Enemy puppet's victim
        try {
            if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                neutral.setPersistentAngerTarget(player.getUUID());
                neutral.startPersistentAngerTimer();
            }
            mob.setTarget(player);
            // brain-driven mobs (piglins, hoglins...) fight from their memories, not the target field
            if (mob.getBrain().checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                    net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
                mob.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, player);
            }
            if (mob.getBrain().checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT,
                    net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
                mob.getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT,
                        player.getUUID(), ticks);
            }
        } finally {
            FORCED_TARGET.remove(mob.getUUID());
        }
    }

    /** whether {@code mob} has seen through {@code player}'s disguise and still remembers. */
    private static boolean revealed(Entity mob, LivingEntity player) {
        Reveal reveal = REVEALED.get(mob.getUUID());
        return reveal != null && reveal.imposter().equals(player.getUUID()) && reveal.until() >= mob.level().getGameTime();
    }

    /** monsters don't hunt you while you're inside a puppet. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        PuppetType type = target instanceof ServerPlayer player ? possessed(player) : null;
        // monsters leave a puppet alone — unless it attacked THEM: anything you hit (in survival) fights back.
        if (type != null && event.getEntity() instanceof Enemy && Config.PUPPETEER_MONSTERS_IGNORE.get()
                && event.getEntity().getLastHurtByMob() != target
                && !FORCED_TARGET.contains(event.getEntity().getUUID())
                && !revealed(event.getEntity(), target)
                && !(event.getEntity() instanceof Mob mob && hunts(mob, type))) {
            event.setCanceled(true);
        }
        // an evoker puppet's vexes only go for whoever their master last hit
        if (event.getEntity() instanceof Mob vex && target != null && !vexAllowed(vex, target)) {
            event.setCanceled(true);
            return;
        }
        // and some puppets nothing will pick a fight with right now (an axolotl playing dead)
        if (type != null && type.behaviour().untargetable((ServerPlayer) target, type)) {
            event.setCanceled(true);
        }
    }

    /**
     * a puppet's melee swing (both sides): its mob may not have one, or not right now (PuppetBehaviour.blocksMelee).
     * whatever it does hit turns on it — and some puppets land the hit their own way (a ravager's delayed bite).
     */
    @SubscribeEvent
    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        // (see revealImposter: a rally running means a hit on your own kind doesn't turn the rest)
        Player player = event.getEntity();
        PuppetType type = PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        if (type == null) {
            return;
        }
        PuppetBehaviour behaviour = type.behaviour();
        if (behaviour.blocksMelee(player, type, event.getTarget())) {
            event.setCanceled(true);
            return;
        }
        behaviour.meleeSwung(player, type);
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        // anything you attack while possessed turns on YOU and keeps the grudge after you unpossess — you were the
        // attacker. neutral mobs (iron golems, wolves...) get real persistent anger so it sticks, like being hit for real.
        if (event.getTarget() instanceof Mob victim) {
            turnOn(victim, sp, Config.PUPPETEER_IMPOSTER_MEMORY_TICKS.get());
            revealImposter(sp, victim);
        }
        if (behaviour.takesOverMelee(sp, type, event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
