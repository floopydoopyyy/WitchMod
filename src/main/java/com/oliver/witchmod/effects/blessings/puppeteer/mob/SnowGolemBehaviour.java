package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.CreeperBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SkeletonBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the snow golem puppet. */
public final class SnowGolemBehaviour implements PuppetBehaviour {
    public static final SnowGolemBehaviour INSTANCE = new SnowGolemBehaviour();

    private SnowGolemBehaviour() {}

    // --- snow golem --------------------------------------------------------------------------------

    private static final String SNOWBALL_TAG = "witchmod_puppet_snowball";

    private static final String BIG_SNOWBALL_TAG = "witchmod_puppet_big_snowball";

    /**
     * snow golems: melt in hot biomes and hurt in water / rain (1 every 10 ticks, like the real one), leave a snow
     * trail (mobGriefing), and slow down while winding up a volley.
     */
    private static void tickSnowGolem(ServerPlayer player, int ticksRemaining) {
        ServerLevel level = player.serverLevel();
        if (ticksRemaining % 10 == 0) {
            if (level.getBiome(player.blockPosition()).is(net.minecraft.tags.BiomeTags.SNOW_GOLEM_MELTS)) {
                player.hurt(player.damageSources().onFire(), 1.0F);
            } else if (player.isInWaterRainOrBubble()) {
                player.hurt(player.damageSources().drown(), 1.0F);
            }
        }
        if (EventHooks.canEntityGrief(level, player)) {
            BlockState snow = Blocks.SNOW.defaultBlockState();
            for (int i = 0; i < 4; i++) {
                BlockPos pos = BlockPos.containing(player.getX() + (i % 2 * 2 - 1) * 0.25, player.getY(),
                        player.getZ() + (i / 2 % 2 * 2 - 1) * 0.25);
                if (level.getBlockState(pos).isAir() && snow.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, snow);
                }
            }
        }
        tickHold(player, PuppetType.SNOW_GOLEM);
        tickBlizzard(player);
        boolean winding = player.getData(WitchModAttachments.PUPPET_FUSE) > 0 || BLIZZARDS.containsKey(player.getUUID());
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && winding != speed.hasModifier(FUSE_SLOW_ID)) {
            if (winding) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID, -Config.PUPPETEER_SNOW_GOLEM_VOLLEY_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(FUSE_SLOW_ID);
            }
        }
    }

    private static net.minecraft.world.entity.projectile.Snowball throwSnowball(ServerPlayer player, boolean big, float yawOffset,
                                                                                float pitchOffset, float velocity) {
        net.minecraft.world.entity.projectile.Snowball ball = new net.minecraft.world.entity.projectile.Snowball(player.level(), player);
        ball.addTag(big ? BIG_SNOWBALL_TAG : SNOWBALL_TAG); // (big = an enhanced volley snowball: it bursts where it lands)
        ball.shootFromRotation(player, player.getXRot() + pitchOffset, player.getYRot() + yawOffset, 0.0F, velocity, big ? 2.0F : 1.0F);
        player.level().addFreshEntity(ball);
        return ball;
    }

    /** snow golems mid-blizzard → salvos fired so far. */
    private static final Map<UUID, Integer> BLIZZARDS = EffectState.sessionConcurrent("puppeteer/snow_golem/blizzards");

    /**
     * the blizzard: puppeteerSnowGolemVolleySalvos salvos over puppeteerSnowGolemVolleyDurationTicks, each a wide fan
     * (puppeteerSnowGolemVolleySpread) of puppeteerSnowGolemVolleyCount enhanced snowballs thrown at once — lobbed at
     * mixed heights and speeds, so near and far both get covered and the whole lot blankets an area. each one bursts
     * where it lands (see onProjectileImpact). it follows your aim as it goes.
     */
    private static void tickBlizzard(ServerPlayer player) {
        Integer fired = BLIZZARDS.get(player.getUUID());
        if (fired == null) {
            return;
        }
        int salvos = Config.PUPPETEER_SNOW_GOLEM_VOLLEY_SALVOS.get();
        int count = Config.PUPPETEER_SNOW_GOLEM_VOLLEY_COUNT.get();
        float spread = Config.PUPPETEER_SNOW_GOLEM_VOLLEY_SPREAD.get().floatValue();
        int duration = Math.max(1, Config.PUPPETEER_SNOW_GOLEM_VOLLEY_DURATION_TICKS.get());
        // how many salvos should be out by now (spread evenly over the duration).
        int tick = fired == 0 ? 0 : BLIZZARD_TICK.merge(player.getUUID(), 1, Integer::sum);
        int due = Math.min(salvos, (int) Math.ceil((tick + 1) * salvos / (double) duration));
        ServerLevel level = player.serverLevel();
        for (; fired < due; fired++) {
            for (int i = 0; i < count; i++) {
                float across = count == 1 ? 0.0F : (i / (float) (count - 1) - 0.5F) * spread;
                float jitter = (level.random.nextFloat() - 0.5F) * spread / Math.max(1, count);
                float lob = -6.0F - level.random.nextFloat() * 26.0F; // 6-32° up: some land close, some far
                throwSnowball(player, true, across + jitter, lob, 1.1F + level.random.nextFloat() * 1.0F);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.PLAYERS, 1.2F,
                    0.6F + level.random.nextFloat() * 0.3F);
            level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        if (fired >= salvos) {
            BLIZZARDS.remove(player.getUUID());
            BLIZZARD_TICK.remove(player.getUUID());
        } else {
            BLIZZARDS.put(player.getUUID(), fired);
        }
    }

    private static final Map<UUID, Integer> BLIZZARD_TICK = EffectState.sessionConcurrent("puppeteer/snow_golem/blizzard_tick");

    /** snow golem: tap → a snowball that actually hurts; hold long enough → a blizzard volley of big ones (own cooldown). */
    private static void snowGolemRelease(ServerPlayer player, int charge) {
        ServerLevel level = player.serverLevel();
        if (charge >= Config.PUPPETEER_SNOW_GOLEM_VOLLEY_CHARGE_TICKS.get() && ready2(player) && !BLIZZARDS.containsKey(player.getUUID())) {
            BLIZZARDS.put(player.getUUID(), 0);
            BLIZZARD_DEALT.remove(player.getUUID()); // a fresh blizzard: a fresh damage cap per target
            BLIZZARD_TICK.put(player.getUUID(), 0);
            tickBlizzard(player); // the first ones fly right away
            // the wind-up lets go all at once: a whumph of snow bursting off you
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.2, player.getZ(), 80, 0.8, 0.8, 0.8, 0.25);
            level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SNOWBALL)),
                    player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.6, 0.6, 0.6, 0.3);
            level.playSound(null, player.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.PLAYERS, 2.0F, 0.6F);
            level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
            level.playSound(null, player.blockPosition(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.PLAYERS, 2.0F, 0.4F);
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                    level.getGameTime() + Config.PUPPETEER_SNOW_GOLEM_VOLLEY_COOLDOWN_TICKS.get());
        } else if (ready(player)) {
            throwSnowball(player, false, 0.0F, 0.0F, 1.6F);
            level.playSound(null, player.blockPosition(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.PLAYERS, 1.0F,
                    0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
            cooldown(player, Config.PUPPETEER_SNOW_GOLEM_SNOWBALL_COOLDOWN_TICKS.get());
        }
    }

    /** per snow golem: damage its current blizzard has dealt to each target (reset when a new blizzard starts). */
    private static final Map<UUID, Map<UUID, Float>> BLIZZARD_DEALT = EffectState.sessionConcurrent("puppeteer/snow_golem/blizzard_dealt");

    /** how much of {@code damage} a blizzard may still deal to {@code target} under puppeteerSnowGolemVolleyDamageCap. */
    private static float blizzardAllowance(@Nullable Entity owner, LivingEntity target, float damage) {
        double cap = Config.PUPPETEER_SNOW_GOLEM_VOLLEY_DAMAGE_CAP.get();
        if (cap <= 0.0 || owner == null) {
            return damage;
        }
        Map<UUID, Float> dealt = BLIZZARD_DEALT.computeIfAbsent(owner.getUUID(), k -> new ConcurrentHashMap<>());
        float sofar = dealt.getOrDefault(target.getUUID(), 0.0F);
        float allowed = (float) Math.max(0.0, Math.min(damage, cap - sofar));
        dealt.put(target.getUUID(), sofar + allowed);
        return allowed;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickSnowGolem(player, ticksRemaining);
    }

    /** the volley charges on its own cooldown: a tap still throws a plain snowball while it recharges. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return Puppets.ready2(player);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        snowGolemRelease(player, charge);
        Puppets.countAction(player);
    }

    /** a snow golem's punch barely hurts, but it chills. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() == player && event.getEntity() != player
                && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            int chill = Config.PUPPETEER_SNOW_GOLEM_CHILL_TICKS.get();
            if (chill > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, chill, 0), player);
            }
        }
    }

    /** shears take its pumpkin off, like the real thing — and it stays off when you leave. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (stack.is(Items.SHEARS) && puppet.getData(WitchModAttachments.PUPPET_DATA).getBoolean("Pumpkin")) {
            if (!user.level().isClientSide) {
                CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
                tag.putBoolean("Pumpkin", false);
                puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
                puppet.spawnAtLocation(new ItemStack(Items.CARVED_PUMPKIN), 1.7F);
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.SNOW_GOLEM_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, user, LivingEntity.getSlotForHand(hand));
            }
            return true;
        }
        return false;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        BLIZZARDS.remove(player.getUUID());
        BLIZZARD_TICK.remove(player.getUUID());
    }

    /**
     * a snow golem puppet's snowball landing (tagged, so it counts even if the golem's been stepped out of): it hurts
     * and chills; an enhanced volley one bursts and chills everything around, capped per target per blizzard.
     */
    public static void snowballLanded(ServerLevel level, net.minecraft.world.entity.projectile.Snowball ball, HitResult result,
            @Nullable Entity struck) {
        boolean big = ball.getTags().contains(BIG_SNOWBALL_TAG);
        if (!big && !ball.getTags().contains(SNOWBALL_TAG)) {
            return;
        }
        LivingEntity target = struck instanceof LivingEntity living && living != ball.getOwner() ? living : null;
        if (big) {
            // an enhanced snowball bursts where it lands — a puff of snow that hurts and chills everything around it.
            Vec3 at = result.getLocation();
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 8, 0.4, 0.3, 0.4, 0.04);
            level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SNOWBALL)),
                    at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.15);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SNOW_BREAK, SoundSource.PLAYERS, 0.35F, 1.2F);
            double r = Config.PUPPETEER_SNOW_GOLEM_VOLLEY_SPLASH_RADIUS.get();
            float splash = Config.PUPPETEER_SNOW_GOLEM_VOLLEY_SPLASH.get().floatValue();
            if (r > 0.0 && splash > 0.0F) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(r),
                        e -> e != target && e != ball.getOwner() && e.isAlive() && !e.isSpectator() && e.distanceToSqr(at) <= r * r)) {
                    float allowed = blizzardAllowance(ball.getOwner(), e, splash);
                    if (allowed > 0.0F) {
                        e.invulnerableTime = 0;
                        e.hurt(ball.damageSources().thrown(ball, ball.getOwner()), allowed);
                    }
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), ball.getOwner());
                }
            }
        }
        if (target == null) {
            return;
        }
        float damage = (big ? Config.PUPPETEER_SNOW_GOLEM_VOLLEY_DAMAGE.get() : Config.PUPPETEER_SNOW_GOLEM_SNOWBALL_DAMAGE.get()).floatValue();
        if (big) {
            // a volley is a shotgun: every snowball that lands counts — up to the per-target cap.
            damage = blizzardAllowance(ball.getOwner(), target, damage);
        }
        if (damage > 0.0F) {
            target.invulnerableTime = 0; // every snowball lands its damage (no hurt-cooldown swallowing quick throws)
            target.hurt(ball.damageSources().thrown(ball, ball.getOwner()), damage);
        }
        if (big) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), ball.getOwner());
        } else {
            // a plain snowball: a sharp, brief freeze.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Config.PUPPETEER_SNOW_GOLEM_SNOWBALL_SLOW_TICKS.get(),
                    Config.PUPPETEER_SNOW_GOLEM_SNOWBALL_SLOW_LEVEL.get() - 1), ball.getOwner());
        }
    }

    /** a tap throws a snowball (its own line, first), a hold winds up the blizzard. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.add(tap(Key.USE), "witchmod.puppeteer.action.snowball", PuppetControls.ready(player));
        out.addMove(player, type);
    }
}
