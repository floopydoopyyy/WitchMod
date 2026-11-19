package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.FishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the guardian puppet. */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.oliver.witchmod.WitchMod.MODID)
public final class GuardianBehaviour implements PuppetBehaviour {
    public static final GuardianBehaviour INSTANCE = new GuardianBehaviour();

    private GuardianBehaviour() {}

    /** guardians whose current beam has already called the other guardians in. */
    private static final Map<UUID, Boolean> BEAM_RALLIED = EffectState.sessionConcurrent("puppeteer/guardian/beam_rallied");

    private static final ResourceLocation BEAM_SLOW_ID = EffectUtil.modifierId("puppeteer_beam_slow");

    /**
     * guardians: quick in water with endless air; out of it they flop and bounce about like a beached guardian
     * (awkward, but they never dry out). holding still puts the spikes out (thorns). an elder guardian curses every
     * other player nearby with mining fatigue now and then, jumpscare and all. the laser is {@link #tickBeam}.
     */
    private static void tickGuardian(ServerPlayer player, PuppetType type) {
        boolean wet = player.isInWater();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && wet == speed.hasModifier(FISH_LAND_ID)) {
            if (wet) {
                speed.removeModifier(FISH_LAND_ID);
            } else {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FISH_LAND_ID, -Config.PUPPETEER_GUARDIAN_LAND_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
        if (wet) {
            player.setAirSupply(player.getMaxAirSupply());
        } else if (player.onGround() && !player.getAbilities().flying && !channelling(player)) {
            // vanilla's beached guardian hops every time it touches down, twisting about at random. (not while
            // channelling the beam: you sit still so you can track your target.)
            player.setDeltaMovement(player.getDeltaMovement().add((player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.1,
                    Config.PUPPETEER_GUARDIAN_FLOP_POWER.get(), (player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.1));
            player.hurtMarked = true;
            player.serverLevel().playSound(null, player.blockPosition(),
                    type == PuppetType.ELDER_GUARDIAN ? SoundEvents.ELDER_GUARDIAN_FLOP : SoundEvents.GUARDIAN_FLOP,
                    SoundSource.PLAYERS, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
        Vec3 last = LAST_POS.put(player.getUUID(), player.position());
        if (last != null && last.distanceToSqr(player.position()) < 1.0E-4) {
            STILL.add(player.getUUID());
        } else {
            STILL.remove(player.getUUID());
        }
        tickBeam(player, type);
        if (type == PuppetType.ELDER_GUARDIAN && player.tickCount % 20 == 0) {
            followElder(player);
        }
        int interval = Config.PUPPETEER_ELDER_CURSE_INTERVAL_TICKS.get();
        if (type == PuppetType.ELDER_GUARDIAN && interval > 0 && player.level().getGameTime() % interval == 0) {
            elderCurse(player);
        }
    }

    /** guardians nearby that have nothing better to do drift after their elder (in the water, as near as they can get). */
    private static void followElder(ServerPlayer elder) {
        double r = Config.PUPPETEER_ELDER_FOLLOW_RADIUS.get();
        for (net.minecraft.world.entity.monster.Guardian g : elder.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Guardian.class,
                elder.getBoundingBox().inflate(r), g -> g.isAlive() && !(g instanceof net.minecraft.world.entity.monster.ElderGuardian)
                        && g.getTarget() == null && g.isInWater() && g.distanceToSqr(elder) > 36.0)) {
            g.getNavigation().moveTo(elder.getX(), elder.getY(), elder.getZ(), 0.6);
        }
    }

    /** elder guardian: mining fatigue III (and the ghostly face) on every other survival player in range. */
    public static void elderCurse(ServerPlayer elder) {
        double r = Config.PUPPETEER_ELDER_CURSE_RADIUS.get();
        int ticks = Config.PUPPETEER_ELDER_CURSE_SECONDS.get() * 20;
        for (ServerPlayer other : elder.serverLevel().players()) {
            if (other == elder || !other.gameMode.isSurvival() || other.distanceToSqr(elder) > r * r) {
                continue;
            }
            MobEffectInstance had = other.getEffect(MobEffects.DIG_SLOWDOWN);
            if (had != null && had.getAmplifier() >= 2 && had.getDuration() > ticks / 2) {
                continue; // still cursed — no face every time
            }
            other.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
            other.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2), elder);
        }
    }

    /**
     * guardians: hold right-click to fire a laser you aim yourself. you're anchored in place while it channels (your
     * own client holds you — see PuppeteerClient.anchored) so it's easy to track a target. it ticks damage on whatever
     * it's on (rallying guardians the first time it connects) and, held for the full length, ends in a BURST. let go
     * early: past the minimum it still bursts (weaker), before it just fizzles. fired out of water, the cooldown doubles.
     */
    private static void tickBeam(ServerPlayer player, PuppetType type) {
        int beam = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (!HELD.contains(player.getUUID()) || !ready(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean elder = type == PuppetType.ELDER_GUARDIAN;
        if (beam == 0) {
            // the one sound — vanilla's own beam wind-up. everything else stays vanilla-quiet.
            level.playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        int was = beam;
        beam += chargeStep(player); // (dexterous: the beam builds to its burst quicker)
        player.setData(WitchModAttachments.PUPPET_FUSE, beam);
        BeamHit hit = beamHit(player, Config.PUPPETEER_GUARDIAN_BEAM_RANGE.get(), Config.PUPPETEER_GUARDIAN_BEAM_WIDTH.get());
        int interval = Math.max(1, Config.PUPPETEER_GUARDIAN_BEAM_TICK_INTERVAL.get());
        if (hit.entity() != null && beam / interval > was / interval) { // crossed an interval (works at any charge step)
            // the trickle doesn't shove what it's on — only the burst has any force behind it.
            NO_KNOCKBACK.add(hit.entity().getUUID());
            hit.entity().invulnerableTime = 0; // a quick trickle: each tick of it lands, not just one every half second
            try {
                beamDamage(player, hit.entity(), (elder ? Config.PUPPETEER_ELDER_GUARDIAN_BEAM_TICK_DAMAGE.get()
                        : Config.PUPPETEER_GUARDIAN_BEAM_TICK_DAMAGE.get()).floatValue());
            } finally {
                NO_KNOCKBACK.remove(hit.entity().getUUID());
            }
            beamRally(player, type, hit.entity());
        }
        if (beam >= Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get()) {
            player.setData(WitchModAttachments.PUPPET_FUSE, 0);
            burst(player, type, 1.0F);
        }
    }

    /**
     * the end of the beam: full damage to what it's on, half to anything right beside it, all thrown back — with a
     * crack and a flash at the far end, and (client side, see PuppetCamera) a kick to your view.
     */
    public static void burst(ServerPlayer player, PuppetType type, float strength) {
        ServerLevel level = player.serverLevel();
        boolean elder = type == PuppetType.ELDER_GUARDIAN;
        BeamHit hit = beamHit(player, Config.PUPPETEER_GUARDIAN_BEAM_RANGE.get(), Config.PUPPETEER_GUARDIAN_BEAM_WIDTH.get());
        Vec3 at = hit.end();
        float damage = (elder ? Config.PUPPETEER_ELDER_GUARDIAN_BURST_DAMAGE.get()
                : Config.PUPPETEER_GUARDIAN_BURST_DAMAGE.get()).floatValue() * strength;
        double radius = elder ? Config.PUPPETEER_ELDER_GUARDIAN_BURST_RADIUS.get() : 1.5;
        double kb = (elder ? Config.PUPPETEER_ELDER_GUARDIAN_BURST_KNOCKBACK.get() : Config.PUPPETEER_GUARDIAN_BURST_KNOCKBACK.get())
                * strength;
        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0; // the tick damage just now mustn't eat the burst
            beamDamage(player, hit.entity(), damage);
            burstShove(player, hit.entity(), kb);
            beamRally(player, type, hit.entity());
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                e -> e != player && e != hit.entity() && e.isAlive() && !e.isSpectator())) {
            beamDamage(player, e, damage * 0.5F);
            burstShove(player, e, kb * 0.5);
        }
        // the impact: a crack and a flash where the beam ends, bigger for an elder
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                (elder ? 1.0F : 0.6F) * Math.max(0.4F, strength), elder ? 1.1F : 1.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GUARDIAN_HURT, SoundSource.PLAYERS, 1.0F, elder ? 0.5F : 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, (int) (4 * radius), radius * 0.3, radius * 0.3, radius * 0.3, 0.1);
        endBeam(player);
        int cd = Config.PUPPETEER_GUARDIAN_BEAM_COOLDOWN_TICKS.get();
        cooldown(player, player.isInWater() ? cd : cd * 2); // a guardian out of water is labouring
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** the burst throws {@code e} away from you (knockback resistance applies, as for any hit). */
    private static void burstShove(ServerPlayer player, LivingEntity e, double strength) {
        if (strength > 0.0 && e.isAlive()) {
            e.knockback(strength, player.getX() - e.getX(), player.getZ() - e.getZ());
            e.hurtMarked = true;
        }
    }

    private static void endBeam(ServerPlayer player) {
        BEAM_RALLIED.remove(player.getUUID());
    }

    /** whoever an elder's marked (entity uuid → until): a guardian's beam bites them harder. */
    private static final Map<UUID, Long> ELDER_MARKS = EffectState.sessionConcurrent("puppeteer/guardian/elder_marks");

    /** an elder fighting a possession → the way it faced when it began (its thrashing jerks round that, never drifting off). */
    private static final Map<UUID, Float> STRUGGLE_YAW = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean elderMarked(LivingEntity e) {
        Long until = ELDER_MARKS.get(e.getUUID());
        return until != null && until >= e.level().getGameTime();
    }

    /** a real guardian's beam on someone an elder's marked: the same bonus as a puppet's. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onGuardianHit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Guardian g
                && !(g instanceof net.minecraft.world.entity.monster.ElderGuardian) && elderMarked(event.getEntity())) {
            event.setAmount(event.getAmount() + Config.PUPPETEER_GUARDIAN_MARKED_BONUS.get().floatValue());
        }
    }

    /** magic damage, like a real guardian's laser (ignores armour), credited to you. */
    private static void beamDamage(ServerPlayer player, LivingEntity target, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        if (possessed(player) == PuppetType.GUARDIAN && elderMarked(target)) {
            amount += Config.PUPPETEER_GUARDIAN_MARKED_BONUS.get().floatValue(); // an elder's marked them
        }
        SPECIAL_DAMAGE.add(player.getUUID());
        try {
            target.hurt(player.damageSources().indirectMagic(player, player), amount);
        } finally {
            SPECIAL_DAMAGE.remove(player.getUUID());
        }
    }

    /**
     * the first time a beam connects. a guardian's is a SOFT rally: guardians near you that aren't already after
     * something go for it (no glow). an elder's is a command: the target glows and every guardian within
     * puppeteerElderRallyRadius drops what it's doing and goes for it with Speed II.
     */
    private static void beamRally(ServerPlayer player, PuppetType type, LivingEntity prey) {
        if (BEAM_RALLIED.put(player.getUUID(), Boolean.TRUE) != null) {
            return;
        }
        boolean elder = type == PuppetType.ELDER_GUARDIAN;
        double r = elder ? Config.PUPPETEER_ELDER_RALLY_RADIUS.get() : Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        int ticks = Config.PUPPETEER_ZOMBIE_RALLY_SECONDS.get() * 20;
        if (elder) {
            prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
            ELDER_MARKS.put(prey.getUUID(), player.level().getGameTime() + ticks);
        }
        com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.rallied(player, prey, ticks);
        for (net.minecraft.world.entity.monster.Guardian g : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Guardian.class, player.getBoundingBox().inflate(r),
                g -> g != prey && g.isAlive() && g.distanceToSqr(player) <= r * r)) {
            if (elder) {
                g.setTarget(prey);
                g.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
            } else if (g.getTarget() == null || !g.getTarget().isAlive()) {
                g.setTarget(prey);
            }
        }
    }

    /**
     * elder guardian: Call (left-click / attack, its own cooldown). if fewer than puppeteerElderCallTarget guardians
     * are near, the rest rise out of the water around you. no water near, no call (and no cooldown spent).
     */
    private static void elderCall(ServerPlayer player) {
        if (player.level().getGameTime() < player.getData(WitchModAttachments.PUPPET_ACTION2_READY)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double r = Config.PUPPETEER_ELDER_CALL_RADIUS.get();
        int have = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Guardian.class, player.getBoundingBox().inflate(r),
                g -> g.isAlive() && g.distanceToSqr(player) <= r * r && !(g instanceof net.minecraft.world.entity.monster.ElderGuardian)).size();
        // none here: two come; a few (up to puppeteerElderCallFew): one or two; more than that: one
        int few = Config.PUPPETEER_ELDER_CALL_FEW.get();
        int want = have == 0 ? 2 : have <= few ? 1 + level.random.nextInt(2) : 1;
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, level.getGameTime() + Config.PUPPETEER_ELDER_CALL_COOLDOWN_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 3.0F, 0.6F);
        int spawned = 0;
        int reach = (int) Math.ceil(r);
        for (int attempt = 0; attempt < 80 && spawned < want; attempt++) {
            BlockPos pos = player.blockPosition().offset(level.random.nextInt(reach * 2 + 1) - reach,
                    level.random.nextInt(9) - 4, level.random.nextInt(reach * 2 + 1) - reach);
            if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()
                    || !level.getFluidState(pos.above()).is(FluidTags.WATER) || pos.distSqr(player.blockPosition()) < 9) {
                continue;
            }
            net.minecraft.world.entity.monster.Guardian g = EntityType.GUARDIAN.create(level);
            if (g == null) {
                break;
            }
            g.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            g.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.REINFORCEMENT, null);
            level.addFreshEntity(g);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, g.getX(), g.getY() + 0.4, g.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            spawned++;
        }
        if (spawned == 0) {
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY, 0L); // nothing could answer: no cooldown spent
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.call_no_water").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickGuardian(player, type);
    }

    @Override
    public int bindingTicks(PuppetType type) {
        return type == PuppetType.ELDER_GUARDIAN && Config.PUPPETEER_ELDER_STRUGGLE.get()
                ? Config.PUPPETEER_ELDER_POSSESS_TICKS.get() : PuppetBehaviour.super.bindingTicks(type);
    }

    /**
     * an elder fights it: it turns its beam on you, thrashes, groans faster and faster, and at the end its ghostly face
     * and curse wash over everyone near — a warning.
     */
    @Override
    public void bindingTick(ServerPlayer player, net.minecraft.world.entity.Mob mob, float progress, long ticks) {
        if (!(mob instanceof net.minecraft.world.entity.monster.ElderGuardian elder) || !Config.PUPPETEER_ELDER_STRUGGLE.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long total = Math.max(1L, Config.PUPPETEER_ELDER_POSSESS_TICKS.get());
        if (ticks == 1) {
            ((com.oliver.witchmod.mixin.GuardianAccessor) elder).witchmodSetAttackTarget(player.getId()); // its beam, on you
            level.playSound(null, elder.blockPosition(), SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        // thrashing round where it faced at the start: a guardian with a beam on turns its body to its head, so jerks
        // added to its own yaw walked it round and round
        float base = STRUGGLE_YAW.computeIfAbsent(elder.getUUID(), k -> elder.getYRot());
        float jerk = (level.random.nextFloat() - 0.5F) * (8.0F + progress * 24.0F);
        elder.setYRot(base);
        elder.yBodyRot = base;
        elder.setYHeadRot(base + jerk);
        elder.setXRot(0.0F);
        if (ticks >= total - 1) {
            STRUGGLE_YAW.remove(elder.getUUID()); // (it's yours next tick)
        }
        if (player.distanceToSqr(elder.getBoundingBox().getCenter()) < 9.0) {
            // you're nearly in it: the beam lets go (a beam on something inside it twists the whole model off its axis)
            ((com.oliver.witchmod.mixin.GuardianAccessor) elder).witchmodSetAttackTarget(0);
        }
        int every = Math.max(4, 22 - (int) (progress * 18.0F));
        if (ticks % every == 0) {
            level.playSound(null, elder.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F + progress * 0.5F);
        }
        level.sendParticles(ParticleTypes.BUBBLE, elder.getX(), elder.getY() + 1.0, elder.getZ(), 2, 0.8, 0.6, 0.8, 0.05);
        if (ticks == total - 2) {
            // its curse: the ghostly face and the sound, to everyone near — but not you, already inside it
            for (ServerPlayer other : level.players()) {
                if (other != player && other.distanceToSqr(elder) <= 50.0 * 50.0) {
                    other.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                            net.minecraft.network.protocol.game.ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
                }
            }
            level.playSound(null, elder.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0F, 1.0F);
        }
    }

    @Override
    public void bindingEnded(net.minecraft.world.entity.Mob mob) {
        STRUGGLE_YAW.remove(mob.getUUID());
        if (mob instanceof net.minecraft.world.entity.monster.Guardian g) {
            ((com.oliver.witchmod.mixin.GuardianAccessor) g).witchmodSetAttackTarget(0);
        }
    }

    /** let go mid-beam: a weaker burst if it ran long enough, otherwise it just fizzles out. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge >= Config.PUPPETEER_GUARDIAN_BEAM_MIN_BURST_TICKS.get()) {
            burst(player, type, Mth.clamp(charge / (float) Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get(), 0.0F, 1.0F));
        } else {
            endBeam(player);
            if (charge > 0) {
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
        }
        // (burst counts its own action)
    }

    /** an elder guardian's left-click: the call. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (type == PuppetType.ELDER_GUARDIAN) {
            elderCall(player);
        }
    }

    /** a guardian holding still has its spikes out: melee attackers get pricked, like the real thing. */
    @Override
    public void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {
        if (Puppets.STILL.contains(player.getUUID()) && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            float thorns = Config.PUPPETEER_GUARDIAN_THORNS.get().floatValue();
            if (thorns > 0.0F) {
                Puppets.SPECIAL_DAMAGE.add(player.getUUID());
                try {
                    attacker.hurt(player.damageSources().thorns(player), thorns);
                } finally {
                    Puppets.SPECIAL_DAMAGE.remove(player.getUUID());
                }
            }
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, BEAM_SLOW_ID);
        BEAM_RALLIED.remove(player.getUUID());
    }

    /** an elder guardian's left-click is its call. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return type == PuppetType.ELDER_GUARDIAN;
    }

    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        if (type == PuppetType.ELDER_GUARDIAN) {
            out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.call", PuppetControls.ready2(player));
        }
    }
}
