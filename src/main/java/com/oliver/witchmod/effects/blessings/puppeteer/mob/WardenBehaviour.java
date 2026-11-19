package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;
import com.oliver.witchmod.network.WitchModNetwork;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the warden. a power fantasy that can't see: blind past a few blocks (WardenSenses' fog and sight shader),
 * it hunts by ear and by nose (WardenHearing). left-click is its swing — vanilla's damage and knockback, on a weapon-style
 * cooldown, shields disabled, more against the marked. right-click is a belt you scroll, like an evoker's:
 * <ul>
 * <li><b>Shriek</b> — hold: the sonic boom winds up (34 ticks, a slight slow and zoom), then fires down your aim
 * through walls, hitting everything on its line; harder on the marked.</li>
 * <li><b>Sniff</b> — hold: the longer, the wider and further the cone; let go and everyone in it, through walls, gives
 * a point away (the counter to standing still).</li>
 * <li><b>Dig</b> — hold: vanilla's whole dig, then you're burrowed: unseen and untouchable, creeping below the ground
 * still listening (players can't be marked meanwhile), on a timer. right-click climbs out — vanilla's emergence —
 * and everyone near gives a point away.</li>
 * </ul>
 * every vanilla animation plays (WardenFx): the swing, the boom, the roar, the sniff, the dig and the emergence, the
 * tendrils twitching as it hears, the heart beating with its anger. it pulses Darkness out to players nearby, as the
 * real one does; possessing one is a struggle that ends in its roar.
 * <p>
 * PUPPET_DASH_POWER is its state (synced: the model's pose), PUPPET_LUNGE_START when that state began, PUPPET_SPELL_SELECT
 * the belt, PUPPET_RAGE_END the dig's cooldown, PUPPET_WARDEN_FX its one-off animations, PUPPET_WARDEN_ANGER its mood.
 */
public final class WardenBehaviour implements PuppetBehaviour {
    public static final WardenBehaviour INSTANCE = new WardenBehaviour();

    private WardenBehaviour() {}

    // states (PUPPET_DASH_POWER)
    public static final int IDLE = 0;
    public static final int ROARING = 1;
    public static final int SNIFFING = 2;
    public static final int DIGGING = 3;
    public static final int EMERGING = 4;
    public static final int BURROWED = 5;
    public static final int ROAR_WINDUP = 6;

    // the belt (PUPPET_SPELL_SELECT)
    public static final int SHRIEK = 0;
    public static final int SNIFF = 1;
    public static final int DIG = 2;
    public static final int ROAR = 3;
    private static final String[] BELT = {"warden_shriek", "warden_sniff", "warden_dig", "warden_roar"};

    // one-off animations (PUPPET_WARDEN_FX): vanilla's entity events, and a few of our own
    public static final int FX_SWING = 4;
    public static final int FX_TENDRILS = 61;
    public static final int FX_SONIC = 62;
    public static final int FX_SONIC_STOP = 63;
    public static final int FX_BOOM = 100;
    public static final int FX_EMERGE = 101;

    /** vanilla's roar: 84 ticks. */
    private static final int ROAR_TICKS = 84;

    private static final String BURROW_KEY = "witchmod_warden_burrow";

    private static final ResourceLocation KB_RESIST_ID = EffectUtil.modifierId("puppeteer_warden_kb_resist");
    private static final ResourceLocation ATTACK_SPEED_ID = EffectUtil.modifierId("puppeteer_warden_attack_speed");
    private static final ResourceLocation ATTACK_KB_ID = EffectUtil.modifierId("puppeteer_warden_attack_kb");
    private static final ResourceLocation BURROW_SPEED_ID = EffectUtil.modifierId("puppeteer_warden_burrow_speed");

    /** every warden puppet (so the hearing does nothing at all when there are none). */
    public static final Set<UUID> WARDENS = EffectState.sessionSet("puppeteer/warden/wardens");

    /** ticks in the current state. */
    private static final Map<UUID, Integer> IN_STATE = EffectState.sessionConcurrent("puppeteer/warden/in_state");

    /** right-click was held last tick (a new press climbs out of the ground). */
    private static final Set<UUID> WAS_HELD = EffectState.sessionSet("puppeteer/warden/was_held");

    /** the held move already went off (a boom, a full sniff, the dig): nothing more until right-click's let go. */
    private static final Set<UUID> SPENT = EffectState.sessionSet("puppeteer/warden/spent");

    /** whoever the last swing was at (so its damage knows if they're marked). */
    private static final Map<UUID, Entity> SWUNG_AT = EffectState.sessionConcurrent("puppeteer/warden/swung_at");

    /** how far it's walked since its last heavy footfall. */
    private static final Map<UUID, Float> STRIDE = EffectState.sessionConcurrent("puppeteer/warden/stride");

    // --- reading it (both sides) ----------------------------------------------------------------------

    public static boolean wardenPuppet(Player p) {
        return PuppetType.byId(p.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.WARDEN;
    }

    public static int state(Player p) {
        return wardenPuppet(p) ? p.getData(WitchModAttachments.PUPPET_DASH_POWER) : IDLE;
    }

    public static boolean burrowed(Player p) {
        return state(p) == BURROWED;
    }

    /** digging in or climbing out: going nowhere, doing nothing else (your client holds you too). */
    public static boolean rooted(Player p) {
        int s = state(p);
        return s == DIGGING || s == EMERGING;
    }

    /** under, or on the way in or out: nothing can touch it. */
    private static boolean untouchable(Player p) {
        int s = state(p);
        return s == DIGGING || s == EMERGING || s == BURROWED;
    }

    public static int selection(Player p) {
        return Mth.clamp(p.getData(WitchModAttachments.PUPPET_SPELL_SELECT), 0, BELT.length - 1);
    }

    /** ticks since the current state began (synced, for the hud and the model). */
    public static long inState(Player p) {
        return p.level().getGameTime() - p.getData(WitchModAttachments.PUPPET_LUNGE_START);
    }

    /** the model's pose for its state. */
    public static Pose pose(Player p) {
        return switch (state(p)) {
            case ROARING -> Pose.ROARING;
            case SNIFFING -> Pose.SNIFFING;
            case DIGGING -> Pose.DIGGING;
            case EMERGING -> Pose.EMERGING;
            default -> p.getPose() == Pose.CROUCHING ? Pose.STANDING : p.getPose(); // (a warden never crouches)
        };
    }

    /** the hud's bar: the charge being held, the climb in or out, or the burrow's time running down. */
    public static float hudProgress(Player p) {
        int hold = p.getData(WitchModAttachments.PUPPET_FUSE);
        try {
            return switch (state(p)) {
                case DIGGING -> Math.min(1.0F, hold / (float) Math.max(1, Config.PUPPETEER_WARDEN_DIG_TICKS.get()));
                case EMERGING -> Math.min(1.0F, inState(p) / (float) Math.max(1, Config.PUPPETEER_WARDEN_EMERGE_TICKS.get()));
                case BURROWED -> 1.0F - Math.min(1.0F, inState(p) / (float) Math.max(1, Config.PUPPETEER_WARDEN_BURROW_MAX_TICKS.get()));
                case SNIFFING -> Math.min(1.0F, hold / (float) Math.max(1, Config.PUPPETEER_WARDEN_SNIFF_CHARGE_TICKS.get()));
                case ROAR_WINDUP -> Math.min(1.0F, inState(p) / (float) Math.max(1, Config.PUPPETEER_WARDEN_ROAR_WINDUP_TICKS.get()));
                default -> hold > 0 && selection(p) == SHRIEK
                        ? Math.min(1.0F, hold / (float) Math.max(1, Config.PUPPETEER_WARDEN_SHRIEK_CHARGE_TICKS.get())) : -1.0F;
            };
        } catch (IllegalStateException e) {
            return -1.0F;
        }
    }

    /** how far through digging in or climbing out (0..1), or -1 if neither — the camera and the dirt over your view. */
    public static float digProgress(Player p, float partialTick) {
        try {
            return switch (state(p)) {
                case DIGGING -> Math.min(1.0F, (p.getData(WitchModAttachments.PUPPET_FUSE) + partialTick)
                        / Math.max(1, Config.PUPPETEER_WARDEN_DIG_TICKS.get()));
                case EMERGING -> Math.min(1.0F, (inState(p) + partialTick) / Math.max(1, Config.PUPPETEER_WARDEN_EMERGE_TICKS.get()));
                default -> -1.0F;
            };
        } catch (IllegalStateException e) {
            return -1.0F;
        }
    }

    /** the boom's charge as 0..1 (the zoom), or -1. */
    public static float shriekCharge(Player p) {
        int hold = p.getData(WitchModAttachments.PUPPET_FUSE);
        if (!wardenPuppet(p) || hold <= 0 || selection(p) != SHRIEK || state(p) != IDLE && state(p) != ROARING) {
            return -1.0F;
        }
        try {
            return Math.min(1.0F, hold / (float) Math.max(1, Config.PUPPETEER_WARDEN_SHRIEK_CHARGE_TICKS.get()));
        } catch (IllegalStateException e) {
            return -1.0F;
        }
    }

    // --- changing it -----------------------------------------------------------------------------------

    private static void setState(ServerPlayer player, int state) {
        int was = state(player);
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, state);
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, player.level().getGameTime());
        IN_STATE.put(player.getUUID(), 0);
        if (sized(was) || sized(state)) {
            player.refreshDimensions();
        }
    }

    /** states whose body isn't its standing one: sinking, under, rising. */
    private static boolean sized(int state) {
        return state == DIGGING || state == BURROWED || state == EMERGING;
    }

    /** a one-off animation on every client (a counter, so each plays once). */
    public static void fx(ServerPlayer player, int id) {
        int seq = (player.getData(WitchModAttachments.PUPPET_WARDEN_FX) >>> 8) + 1;
        player.setData(WitchModAttachments.PUPPET_WARDEN_FX, (seq & 0xFFFFFF) << 8 | id);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /**
     * Roar (the belt's): after its wind-up, vanilla's roar — everyone close by who can see it gives a point away, and the
     * warden surges forward (Speed) for a few seconds.
     */
    private static void roarAtThem(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        setState(player, ROARING);
        sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 1.0F);
        player.setData(WitchModAttachments.PUPPET_ACTION3_READY, level.getGameTime() + Config.PUPPETEER_WARDEN_ROAR_COOLDOWN_TICKS.get());
        double r = Config.PUPPETEER_WARDEN_ROAR_RADIUS.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e.distanceToSqr(player) <= r * r && player.hasLineOfSight(e))) {
            WardenHearing.addPoints(player, e, 1);
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Config.PUPPETEER_WARDEN_ROAR_SPEED_TICKS.get(),
                Math.max(0, Config.PUPPETEER_WARDEN_ROAR_SPEED_LEVEL.get() - 1), false, true, true));
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getEyeY(), player.getZ(), 12, 0.6, 0.4, 0.6, 0.08);
    }

    /** roar (it found someone): vanilla's roar animation and sound — on the move, it doesn't stop to. @return if it did. */
    public static boolean roar(ServerPlayer player) {
        if (state(player) != IDLE || player.getData(WitchModAttachments.PUPPET_FUSE) > 0) {
            return false;
        }
        setState(player, ROARING);
        sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 1.0F);
        return true;
    }

    // --- every tick ------------------------------------------------------------------------------------

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        UUID id = player.getUUID();
        WARDENS.add(id);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int state = state(player);
        int in = IN_STATE.merge(id, 1, Integer::sum);
        boolean held = Holds.HELD.contains(id);
        boolean pressed = held && !WAS_HELD.contains(id);
        if (held) {
            WAS_HELD.add(id);
        } else {
            WAS_HELD.remove(id);
        }
        if (state != BURROWED && player.getData(WitchModAttachments.PUPPET_HIDDEN)) {
            resurface(player, player.getData(WitchModAttachments.PUPPET_DATA)); // never left hidden once it's up
            player.setData(WitchModAttachments.PUPPET_HIDDEN, false);
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        }
        // it sees by sound, not light: darkness and blindness don't touch it
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.BLINDNESS);
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        switch (state) {
            case ROARING -> {
                if (in >= ROAR_TICKS) {
                    setState(player, IDLE);
                }
            }
            case ROAR_WINDUP -> {
                // it draws itself up: tendrils thrashing, a growl building
                if (in % 8 == 0) {
                    fx(player, FX_TENDRILS);
                }
                if (in % 20 == 10) {
                    sound(player, SoundEvents.WARDEN_ANGRY, 1.5F + in / 40.0F, 0.8F + in / 150.0F);
                }
                if (in >= Config.PUPPETEER_WARDEN_ROAR_WINDUP_TICKS.get()) {
                    roarAtThem(player);
                }
            }
            case SNIFFING -> {
                if (!held && in > 2) {
                    setState(player, IDLE); // (let go between ticks)
                } else if (hold >= Config.PUPPETEER_WARDEN_SNIFF_CHARGE_TICKS.get()) {
                    sniff(player, hold); // as wide and far as it goes: it lets the breath out itself
                }
            }
            case DIGGING -> {
                player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
                player.refreshDimensions(); // the eyes sink as it digs
                if (thrownOut(player)) {
                    kickOut(player);
                } else if (!held && in > 2) {
                    cancelDig(player);
                } else if (hold >= Config.PUPPETEER_WARDEN_DIG_TICKS.get()) {
                    burrow(player);
                } else if (in % 10 == 0) {
                    groundCrumble(player, 6, in % 20 == 0);
                }
            }
            case BURROWED -> tickBurrowed(player, in, pressed);
            case EMERGING -> {
                player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
                player.refreshDimensions(); // and rise as it climbs
                if (in % 8 == 0 && in < 100) {
                    groundCrumble(player, 10, in % 16 == 0);
                }
                if (in >= Config.PUPPETEER_WARDEN_EMERGE_TICKS.get()) {
                    setState(player, IDLE);
                    player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_WARDEN_DIG_COOLDOWN_TICKS.get());
                }
            }
            default -> {
                if (hold >= Config.PUPPETEER_WARDEN_SHRIEK_CHARGE_TICKS.get() && selection(player) == SHRIEK && !SPENT.contains(id)) {
                    boom(player);
                }
            }
        }
        Holds.tickHold(player, type);
        state = state(player);
        boolean shrieking = shriekCharge(player) >= 0.0F;
        Puppets.chargeSlow(player, shrieking || state == SNIFFING || state == ROAR_WINDUP,
                shrieking ? Config.PUPPETEER_WARDEN_SHRIEK_SLOW.get() : state == ROAR_WINDUP
                        ? Config.PUPPETEER_WARDEN_ROAR_SLOW.get() : Config.PUPPETEER_WARDEN_SNIFF_SLOW.get());
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && (state == BURROWED) != speed.hasModifier(BURROW_SPEED_ID)) {
            if (state == BURROWED) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, BURROW_SPEED_ID,
                        Config.PUPPETEER_WARDEN_BURROW_SPEED.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(BURROW_SPEED_ID);
            }
        }
        if (!untouchable(player)) {
            if ((player.tickCount + player.getId()) % 120 == 0) {
                darkness(player); // vanilla's pulse
            }
            if (player.tickCount % 4 == 0) {
                // anything inside its body (its real width) is marked at once
                AABB body = net.minecraft.world.entity.EntityType.WARDEN.getDimensions().makeBoundingBox(player.position());
                for (LivingEntity touching : level.getEntitiesOfClass(LivingEntity.class, body, e -> e != player)) {
                    WardenHearing.markNow(player, touching);
                }
            }
            footfalls(player);
            if (state == IDLE && player.getRandom().nextInt(240) == 0) {
                // its mood, aloud: calm, agitated or angry
                SoundEvent mood = AngerLevel.byAnger(player.getData(WitchModAttachments.PUPPET_WARDEN_ANGER)).getAmbientSound();
                sound(player, mood, 2.0F, 1.0F);
            }
        }
        WardenHearing.tick(player);
    }

    /** a warden's heavy tread (the player's own footsteps are light): one thud a stride. */
    private static void footfalls(ServerPlayer player) {
        Vec3 last = Puppets.LAST_POS.put(player.getUUID(), player.position());
        if (last == null || !player.onGround()) {
            return;
        }
        double dx = player.getX() - last.x;
        double dz = player.getZ() - last.z;
        float walked = STRIDE.merge(player.getUUID(), (float) Math.sqrt(dx * dx + dz * dz), Float::sum);
        if (walked >= 1.6F) {
            STRIDE.put(player.getUUID(), 0.0F);
            sound(player, SoundEvents.WARDEN_STEP, 1.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
    }

    /** vanilla's Darkness pulse, to the players around it (survival and adventure, as vanilla). */
    private static void darkness(ServerPlayer player) {
        double r = Config.PUPPETEER_WARDEN_DARKNESS_RADIUS.get();
        if (r <= 0.0) {
            return;
        }
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !other.gameMode.isSurvival() || wardenPuppet(other) || other.distanceToSqr(player) > r * r) {
                continue;
            }
            MobEffectInstance has = other.getEffect(MobEffects.DARKNESS);
            if (has == null || has.endsWithin(199)) {
                other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 260, 0, false, false), player);
            }
        }
    }

    // --- shriek ------------------------------------------------------------------------------------------

    /**
     * vanilla's sonic boom, aimed: down your look for puppeteerWardenShriekRange, straight through walls, the shock
     * rings along it, everything on the line struck (sonic damage ignores armour) and flung back — harder if marked.
     */
    private static void boom(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SPENT.add(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        Puppets.cooldown(player, Config.PUPPETEER_WARDEN_SHRIEK_COOLDOWN_TICKS.get());
        double range = Config.PUPPETEER_WARDEN_SHRIEK_RANGE.get();
        Vec3 from = player.getEyePosition(); // from your eyes, so it goes exactly where you aim
        Vec3 dir = player.getLookAngle();
        Vec3 to = from.add(dir.scale(range));
        for (int i = 1; i <= Mth.floor(range); i++) {
            Vec3 at = from.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
        fx(player, FX_BOOM);
        float damage = Config.PUPPETEER_WARDEN_SHRIEK_DAMAGE.get().floatValue();
        float markedMult = Config.PUPPETEER_WARDEN_SHRIEK_MARKED_MULTIPLIER.get().floatValue();
        AABB sweep = new AABB(from, to).inflate(1.5);
        List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != player && e.isAlive()
                && !e.isSpectator() && (e.getBoundingBox().inflate(0.6).clip(from, to).isPresent()
                || e.getBoundingBox().inflate(0.6).contains(from)));
        for (LivingEntity target : struck) {
            float amount = WardenHearing.marked(player, target) ? damage * markedMult : damage;
            Puppets.SPECIAL_DAMAGE.add(player.getUUID());
            try {
                target.hurt(player.damageSources().sonicBoom(player), amount);
            } finally {
                Puppets.SPECIAL_DAMAGE.remove(player.getUUID());
            }
            // vanilla's fling: away from the warden, scaled by what the target resists
            double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            Vec3 away = target.position().subtract(player.position()).normalize();
            target.push(away.x * 2.5 * resist, away.y * 0.5 * resist + 0.1 * resist, away.z * 2.5 * resist);
            target.hurtMarked = true;
        }
    }

    // --- sniff -------------------------------------------------------------------------------------------

    /**
     * let the breath out: a cone down your look, as wide and as far as you held it, through walls. everyone in it gives
     * a point away, and your client lights them up (WardenSenses).
     */
    private static void sniff(ServerPlayer player, int charge) {
        ServerLevel level = player.serverLevel();
        SPENT.add(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        setState(player, IDLE);
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, level.getGameTime() + Config.PUPPETEER_WARDEN_SNIFF_COOLDOWN_TICKS.get());
        float share = Mth.clamp(charge / (float) Math.max(1, Config.PUPPETEER_WARDEN_SNIFF_CHARGE_TICKS.get()), 0.0F, 1.0F);
        double range = Mth.lerp(share, Config.PUPPETEER_WARDEN_SNIFF_MIN_RANGE.get(), Config.PUPPETEER_WARDEN_SNIFF_MAX_RANGE.get());
        double half = Mth.lerp(share, Config.PUPPETEER_WARDEN_SNIFF_MIN_ANGLE.get(), Config.PUPPETEER_WARDEN_SNIFF_MAX_ANGLE.get());
        double cos = Math.cos(Math.toRadians(half));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<Integer> caught = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> WardenHearing.noticeable(player, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0E-3 || to.scale(1.0 / dist).dot(look) < cos) {
                continue;
            }
            if (WardenHearing.marked(player, e)) {
                WardenHearing.renewMark(player, e); // still on its trail: the mark's back to full
            } else {
                WardenHearing.addPoints(player, e, 1);
            }
            caught.add(e.getId());
        }
        fx(player, FX_TENDRILS);
        sound(player, caught.isEmpty() ? SoundEvents.WARDEN_LISTENING : SoundEvents.WARDEN_LISTENING_ANGRY, 1.6F, 1.0F);
        PacketDistributor.sendToPlayer(player, new WitchModNetwork.WardenSniffPayload(eye.x, eye.y, eye.z,
                player.getYRot(), player.getXRot(), (float) range, (float) half, caught));
    }

    // --- dig / burrow / emerge -------------------------------------------------------------------------

    /** let go before it's under: it climbs back out of the half-dug hole (a short cooldown, no burrow). */
    private static void cancelDig(ServerPlayer player) {
        setState(player, IDLE);
        player.setData(WitchModAttachments.PUPPET_RAGE_END,
                player.level().getGameTime() + Config.PUPPETEER_WARDEN_DIG_CANCEL_COOLDOWN_TICKS.get());
    }

    /** under: hidden, untouchable, creeping below the ground — on a timer (the data key brings you back up after a crash). */
    private static void burrow(ServerPlayer player) {
        SPENT.add(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        setState(player, BURROWED);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, true);
        player.setInvisible(true);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putBoolean(BURROW_KEY, true);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.refreshDimensions();
        groundCrumble(player, 30, true);
        sound(player, SoundEvents.WARDEN_DIG, 1.0F, 0.7F);
    }

    /**
     * below ground: it tunnels about (quickly) and still hears. the ground churns where it goes — dust and the sound of
     * digging, the only sign anything's there. a new press of right-click, or the time limit, brings it up.
     */
    private static void tickBurrowed(ServerPlayer player, int in, boolean pressed) {
        if (thrownOut(player)) {
            kickOut(player);
            return;
        }
        player.setInvisible(true); // (effects changing would show it again)
        player.resetFallDistance();
        Vec3 last = Puppets.LAST_POS.put(player.getUUID(), player.position());
        boolean moving = last != null && last.distanceToSqr(player.position()) > 1.0E-3;
        if (moving && in % 2 == 0) {
            groundCrumble(player, 5, false);
        }
        if (in % (moving ? 4 : 14) == 0) {
            digSound(player, moving);
        }
        boolean timeUp = in >= Config.PUPPETEER_WARDEN_BURROW_MAX_TICKS.get();
        if (timeUp || (pressed && in >= 10)) {
            if (!roomToStand(player, player.position())) {
                // somewhere too low for a warden (it slipped under something): it tunnels on to the nearest spot it fits
                Vec3 spot = standingRoom(player);
                player.teleportTo(spot.x, spot.y, spot.z);
            }
            emerge(player);
        }
    }

    /**
     * the nearest spot a whole warden fits standing on solid ground: around it on its own level first (then a little up
     * or down), widening; failing that, straight up out of wherever it is.
     */
    private static Vec3 standingRoom(ServerPlayer player) {
        Vec3 from = player.position();
        net.minecraft.world.level.Level level = player.level();
        int[] heights = {0, 1, -1, 2, -2, 3, -3};
        for (int r = 0; r <= 8; r++) {
            for (int dy : heights) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        Vec3 at = new Vec3(Math.floor(from.x) + 0.5 + dx, Math.floor(from.y) + dy, Math.floor(from.z) + 0.5 + dz);
                        BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
                        if (roomToStand(player, at) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                            return at;
                        }
                    }
                }
            }
        }
        return Puppets.freeSpot(level, player, standingBox(from), from);
    }

    /** the whole warden standing at {@code at} (it rises to full height as it climbs out). */
    private static AABB standingBox(Vec3 at) {
        float h = net.minecraft.world.entity.EntityType.WARDEN.getDimensions().height();
        return new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + h, at.z + 0.3);
    }

    private static boolean roomToStand(ServerPlayer player, Vec3 at) {
        return player.level().noCollision(player, standingBox(at));
    }

    /** ticks each warden's been off the ground while digging or under. */
    private static final Map<UUID, Integer> AIRBORNE = EffectState.sessionConcurrent("puppeteer/warden/airborne");

    /** dug into water or lava, or fell (off a ledge, the ground gone) for too long: the dig can't hold. */
    private static boolean thrownOut(ServerPlayer player) {
        if (player.isInWater() || player.isInLava()) {
            return true;
        }
        int air = player.onGround() ? 0 : AIRBORNE.getOrDefault(player.getUUID(), 0) + 1;
        AIRBORNE.put(player.getUUID(), air);
        return air > Config.PUPPETEER_WARDEN_BURROW_FALL_TICKS.get();
    }

    /** out of the dig at once — no climb, no burst of points, no Darkness — and the dig's cooldown starts. */
    private static void kickOut(ServerPlayer player) {
        resurface(player, player.getData(WitchModAttachments.PUPPET_DATA));
        setState(player, IDLE);
        AIRBORNE.remove(player.getUUID());
        Puppets.unstick(player);
        player.setData(WitchModAttachments.PUPPET_RAGE_END,
                player.level().getGameTime() + Config.PUPPETEER_WARDEN_DIG_COOLDOWN_TICKS.get());
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.warden_thrown_out").withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * vanilla's emergence: the ground splits, it hauls itself up (rooted, untouchable), and everyone close by when it
     * breaks the surface gives a point away — then Darkness rolls out, and the dig's cooldown starts once it's up.
     */
    private static void emerge(ServerPlayer player) {
        resurface(player, player.getData(WitchModAttachments.PUPPET_DATA));
        setState(player, EMERGING);
        player.refreshDimensions(); // (again, now it's no longer burrowed: its eyes back up where they belong)
        fx(player, FX_EMERGE);
        sound(player, SoundEvents.WARDEN_EMERGE, 3.0F, 1.0F);
        groundCrumble(player, 40, true);
        double r = Config.PUPPETEER_WARDEN_EMERGE_RADIUS.get();
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e.distanceToSqr(player) <= r * r)) {
            WardenHearing.addPoints(player, e, 1);
        }
        darkness(player);
    }

    /** up out of the ground: seen and solid again (also on stepping out, or logging in after a crash). */
    private static void resurface(ServerPlayer player, CompoundTag tag) {
        boolean was = state(player) == BURROWED || tag.getBoolean(BURROW_KEY);
        if (!was) {
            return;
        }
        tag.remove(BURROW_KEY);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, false);
        player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        player.refreshDimensions();
    }

    /** digging, under the ground where it is: the block's own dig and break sounds, rough and low. */
    private static void digSound(ServerPlayer player, boolean moving) {
        ServerLevel level = player.serverLevel();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            return;
        }
        var sounds = ground.getSoundType(level, below, player);
        float pitch = 0.5F + player.getRandom().nextFloat() * 0.25F;
        level.playSound(null, below, sounds.getHitSound(), SoundSource.HOSTILE, moving ? 0.9F : 0.5F, pitch);
        if (moving && player.getRandom().nextInt(3) == 0) {
            level.playSound(null, below, sounds.getBreakSound(), SoundSource.HOSTILE, 0.7F, pitch);
        }
    }

    /** the ground under it breaking up: its own block's dust, and (with {@code knock}) the thud of it. */
    private static void groundCrumble(ServerPlayer player, int count, boolean knock) {
        ServerLevel level = player.serverLevel();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        BlockState ground = level.getBlockState(below);
        if (ground.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
                count, 0.45, 0.05, 0.45, 0.15);
        if (knock) {
            level.playSound(null, below, ground.getSoundType(level, below, player).getHitSound(), SoundSource.HOSTILE, 0.9F, 0.45F);
        }
    }

    // --- the belt --------------------------------------------------------------------------------------

    @Override
    public boolean scrolls(PuppetType type) {
        return true;
    }

    /** scroll the belt — not mid-charge, nor while digging, under or climbing out. */
    @Override
    public void scroll(ServerPlayer player, PuppetType type, int dir) {
        int s = state(player);
        if (player.getData(WitchModAttachments.PUPPET_FUSE) > 0 || s == SNIFFING || s == ROAR_WINDUP || untouchable(player)) {
            return;
        }
        int sel = Math.floorMod(selection(player) + Integer.signum(dir), BELT.length);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, sel);
        player.playNotifySound(SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.7F, 0.8F + sel * 0.2F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.warden_select",
                Component.translatable("witchmod.puppeteer.action." + BELT[sel])).withStyle(ChatFormatting.DARK_AQUA), true);
    }

    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        if (SPENT.contains(player.getUUID())) {
            return false;
        }
        int s = state(player);
        boolean free = s == IDLE || s == ROARING;
        return switch (selection(player)) {
            case SHRIEK -> free && Puppets.ready(player);
            case SNIFF -> s == SNIFFING || free && Puppets.ready2(player);
            case ROAR -> s == ROAR_WINDUP || free && player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_ACTION3_READY);
            default -> s == DIGGING || free && player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_RAGE_END)
                    && player.onGround() && !player.isInWater() && !player.isInLava();
        };
    }

    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        if (state(player) == ROARING) {
            setState(player, IDLE);
        }
        switch (selection(player)) {
            case SHRIEK -> {
                fx(player, FX_SONIC); // vanilla's sonic boom animation: the chest swells, then it bellows
                sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
            }
            case SNIFF -> {
                setState(player, SNIFFING);
                sound(player, SoundEvents.WARDEN_SNIFF, 2.0F, 1.0F);
            }
            case ROAR -> {
                setState(player, ROAR_WINDUP); // committed: it builds whether or not you keep holding
                sound(player, SoundEvents.WARDEN_AGITATED, 3.0F, 0.8F);
                fx(player, FX_TENDRILS);
            }
            default -> {
                setState(player, DIGGING);
                sound(player, SoundEvents.WARDEN_DIG, 3.0F, 1.0F);
            }
        }
    }

    /** let go: a sniff goes off (if held long enough), a dig's abandoned, an unfinished boom fizzles. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (SPENT.remove(player.getUUID())) {
            return;
        }
        int s = state(player);
        if (s == SNIFFING) {
            if (charge >= Holds.MIN_CHARGE) {
                sniff(player, charge);
                SPENT.remove(player.getUUID());
            } else {
                setState(player, IDLE);
            }
        } else if (s == DIGGING) {
            cancelDig(player);
        } else if (charge > 0 && selection(player) == SHRIEK) {
            fx(player, FX_SONIC_STOP);
        }
    }

    // --- its swing -------------------------------------------------------------------------------------

    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return untouchable(player);
    }

    @Override
    public void meleeSwung(Player player, PuppetType type) {
        IronGolemBehaviour.recordSwing(player);
        if (player instanceof ServerPlayer sp) {
            fx(sp, FX_SWING); // vanilla's attack: both arms
        }
    }

    /** vanilla's shield-breaker: a raised shield is knocked aside before the blow lands. */
    @Override
    public boolean takesOverMelee(ServerPlayer player, PuppetType type, Entity target) {
        SWUNG_AT.put(player.getUUID(), target);
        if (target instanceof Player victim && victim.isBlocking()) {
            victim.disableShield();
        }
        return false;
    }

    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        float amount = IronGolemBehaviour.swingScaled(type.attackDamage(), swingStrength);
        return WardenHearing.marked(player, SWUNG_AT.get(player.getUUID()))
                ? amount * (1.0F + Config.PUPPETEER_WARDEN_MARKED_DAMAGE_BONUS.get().floatValue()) : amount;
    }

    /** the blow's crunch; a marked target's sent flying further. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim != player && event.getSource().getEntity() == player) {
            WardenHearing.markNow(player, victim); // whatever it hits, it knows
        }
        if (event.getSource().getDirectEntity() != player || victim == player || Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            return;
        }
        sound(player, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 1.0F);
        if (WardenHearing.marked(player, victim)) {
            double extra = Config.PUPPETEER_WARDEN_MARKED_KNOCKBACK.get() * (1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 away = victim.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
            if (extra > 0.0 && away.lengthSqr() > 1.0E-4) {
                away = away.normalize();
                victim.push(away.x * extra, 0.15 * extra, away.z * extra);
                victim.hurtMarked = true;
            }
        }
    }

    // --- its body ----------------------------------------------------------------------------------------

    /** a warden's frame: unshakeable, a heavy fist, a weapon-style swing. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        WARDENS.add(player.getUUID());
        setState(player, IDLE);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, SHRIEK);
        player.setData(WitchModAttachments.PUPPET_WARDEN_ANGER, 0);
        EffectUtil.addModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB_RESIST_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KB_ID, 1.5, AttributeModifier.Operation.ADD_VALUE);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID,
                    Config.PUPPETEER_WARDEN_ATTACK_SPEED.get() - attackSpeed.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
        }
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            WardenHearing.markNow(player, attacker); // whoever hits it, it knows exactly where they are
        }
        if (untouchable(player) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true); // vanilla: invulnerable while digging and emerging (and, here, below)
        }
    }

    @Override
    public boolean untargetable(ServerPlayer player, PuppetType type) {
        return burrowed(player);
    }

    /** digging, its eyes sink to the floor; climbing out, they rise back up. */
    @Override
    public float bodyScale(Player player, PuppetType type) {
        float low = 0.15F;
        return switch (state(player)) {
            case DIGGING -> 1.0F - (1.0F - low) * Math.max(0.0F, digProgress(player, 0.0F));
            case EMERGING -> low + (1.0F - low) * Math.max(0.0F, digProgress(player, 0.0F));
            case BURROWED -> low;
            default -> 1.0F;
        };
    }

    /** under, it's a low mound a block across and a block high: it slips through one-block gaps. */
    @Override
    public net.minecraft.world.entity.EntityDimensions size(Player player, PuppetType type, net.minecraft.world.entity.EntityDimensions dims) {
        if (!burrowed(player)) {
            return dims;
        }
        return new net.minecraft.world.entity.EntityDimensions(0.9F, 0.9F, 0.45F, dims.attachments(), dims.fixed());
    }

    /**
     * stepping out: back up first if burrowed (also after a crash — the data key). the warden left behind keeps to its
     * spot for good (no digging back down, no despawning) and doesn't wake up already furious with you.
     */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        boolean wasWarden = wardenPuppet(player);
        resurface(player, data);
        UUID id = player.getUUID();
        WARDENS.remove(id);
        IN_STATE.remove(id);
        WAS_HELD.remove(id);
        SPENT.remove(id);
        SWUNG_AT.remove(id);
        AIRBORNE.remove(id);
        STRIDE.remove(id);
        WardenHearing.forget(player);
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB_RESIST_ID);
        EffectUtil.removeModifier(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KB_ID);
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, BURROW_SPEED_ID);
        player.setData(WitchModAttachments.PUPPET_WARDEN_ANGER, 0);
        if (wasWarden) {
            data.putBoolean("PersistenceRequired", true);
            data.remove("anger");
        }
    }

    // --- the struggle ------------------------------------------------------------------------------------

    @Override
    public int bindingTicks(PuppetType type) {
        return Config.PUPPETEER_WARDEN_STRUGGLE.get() ? Config.PUPPETEER_WARDEN_POSSESS_TICKS.get() : PuppetBehaviour.super.bindingTicks(type);
    }

    /**
     * a warden fights it. its heart races (the anger it shows climbs), its tendrils thrash, it shudders, the sculk in
     * it bleeds out, Darkness rolls over everyone near — and it ends on its roar. a warning to everyone around.
     */
    @Override
    public void bindingTick(ServerPlayer player, Mob mob, float progress, long ticks) {
        if (!(mob instanceof Warden warden) || !Config.PUPPETEER_WARDEN_STRUGGLE.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long total = Math.max(1L, Config.PUPPETEER_WARDEN_POSSESS_TICKS.get());
        long roarAt = Math.max(0L, total - ROAR_TICKS);
        // its heart: the client's own beat speeds up with this (vanilla's anger scale)
        warden.getEntityData().set(com.oliver.witchmod.mixin.WardenAccessor.witchmodAngerLevel(),
                (int) Math.min(150.0F, progress * 180.0F));
        // the tendrils twitch faster and faster
        int every = Math.max(3, 20 - (int) (progress * 17.0F));
        if (ticks % every == 0) {
            level.broadcastEntityEvent(warden, (byte) 61);
            level.playSound(null, warden.blockPosition(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.HOSTILE, 1.5F, 0.8F + progress * 0.6F);
        }
        // it shudders, head thrashing
        float jerk = (level.random.nextFloat() - 0.5F) * (6.0F + progress * 22.0F);
        warden.setYHeadRot(warden.getYRot() + jerk);
        warden.yBodyRot = warden.getYRot() + jerk * 0.3F;
        // the sculk in it bleeds out
        double y = warden.getY() + warden.getBbHeight() * (0.3 + level.random.nextDouble() * 0.6);
        level.sendParticles(ParticleTypes.SCULK_SOUL, warden.getX(), y, warden.getZ(), 1, 0.4, 0.4, 0.4, 0.02);
        if (ticks % 3 == 0) {
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, warden.getX(), warden.getY() + 1.4, warden.getZ(), 3, 0.5, 0.7, 0.5, 0.02);
        }
        if (ticks == 1 || ticks == roarAt) {
            for (ServerPlayer other : level.players()) {
                if (other.distanceToSqr(warden) <= 900.0) {
                    other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false), warden);
                }
            }
            level.playSound(null, warden.blockPosition(), ticks == 1 ? SoundEvents.WARDEN_AGITATED : SoundEvents.WARDEN_ANGRY,
                    SoundSource.HOSTILE, 3.0F, 1.0F);
        }
        if (ticks == roarAt) {
            warden.setPose(Pose.ROARING); // and it roars
            level.playSound(null, warden.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, 1.0F);
        }
    }

    @Override
    public void bindingEnded(Mob mob) {
        if (mob instanceof Warden warden) {
            if (warden.getPose() == Pose.ROARING) {
                warden.setPose(Pose.STANDING);
            }
            warden.getEntityData().set(com.oliver.witchmod.mixin.WardenAccessor.witchmodAngerLevel(), 0);
        }
    }

    // --- the hud -------------------------------------------------------------------------------------------

    /** the belt (scroll, then hold), the swing — or, below ground, just the way back up. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        int s = state(player);
        if (s == BURROWED) {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.warden_emerge", 0L);
            out.note(Component.translatable("witchmod.puppeteer.warden_burrowed"));
            return;
        }
        if (s == DIGGING || s == EMERGING) {
            out.note(Component.translatable(s == DIGGING ? "witchmod.puppeteer.warden_digging" : "witchmod.puppeteer.warden_emerging"));
            return;
        }
        int sel = selection(player);
        out.add(List.of(tap(Key.SCROLL)), Component.translatable("witchmod.puppeteer.warden_select",
                Component.translatable("witchmod.puppeteer.action." + BELT[sel])), 0L);
        long ready = switch (sel) {
            case SHRIEK -> PuppetControls.ready(player);
            case SNIFF -> PuppetControls.ready2(player);
            case ROAR -> player.getData(WitchModAttachments.PUPPET_ACTION3_READY);
            default -> player.getData(WitchModAttachments.PUPPET_RAGE_END);
        };
        out.add(List.of(hold(Key.USE)), Component.translatable("witchmod.puppeteer.action." + BELT[sel]), ready);
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.warden_swing", 0L);
    }
}
