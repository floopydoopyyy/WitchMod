package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the shulker. it doesn't walk (configurable) and sits on the block grid, its lid's head turning to look
 * about (the model). left-click fires a BULLET (vanilla's: homing, levitation) locked onto whoever you're looking at — or
 * straight on at no one; HOLD left-click to charge a VOLLEY (the lid creaks open, your view narrows): five bullets at
 * your target and whoever's close by them, a stronger levitation. right-click is a belt you scroll:
 * <ul>
 * <li><b>Teleport</b> — tap: onto the face of the block you're looking at (two charges, each back after a while; your
 * client marks the spot, and whoever your bullets would lock onto); every hit you take speeds the recharge.</li>
 * <li><b>Shell</b> — hold: shut in, as vanilla's closed shulker — +20 armour, no knockback, projectiles glance off — up
 * to a time limit, then a cooldown.</li>
 * </ul>
 * no bite. PUPPET_DASH_POWER is the lid's peek (0 shut .. 100 open — synced for the model); PUPPET_SPELL_SELECT the belt;
 * PUPPET_TP_CHARGES the teleports left, PUPPET_ACTION_READY when the next comes back; ACTION2 the shell's cooldown;
 * PUPPET_RAGE_END the bullet's; PUPPET_LUNGE_START the volley's.
 */
public final class ShulkerBehaviour implements PuppetBehaviour {
    public static final ShulkerBehaviour INSTANCE = new ShulkerBehaviour();

    private ShulkerBehaviour() {}

    public static final int TELEPORT = 0;
    public static final int SHELL = 1;
    private static final String[] BELT = {"shulker_teleport", "shulker_shell"};

    private static final ResourceLocation SHELL_ARMOR_ID = EffectUtil.modifierId("puppeteer_shulker_shell");
    private static final ResourceLocation SHELL_KB_ID = EffectUtil.modifierId("puppeteer_shulker_shell_kb");

    /** a volley's bullets carry this tag: they lift harder. */
    private static final String VOLLEY_TAG = "witchmod_shulker_volley";

    /** a shulker's live bullets: when each expires, and where it set off from (it fizzles past its range). */
    private record Shot(ShulkerBullet bullet, long expires, Vec3 from) {}

    private static final Map<UUID, List<Shot>> SHOTS = EffectState.sessionConcurrent("puppeteer/shulker/shots");

    /** when the shell shut (absent = open), and a spent hold that must be let go before it shuts again. */
    private static final Map<UUID, Long> SHUT_AT = EffectState.sessionConcurrent("puppeteer/shulker/shut_at");
    private static final java.util.Set<UUID> SPENT = EffectState.sessionSet("puppeteer/shulker/spent");

    /** when the last bullet left (the lid opens wide for it). */
    private static final Map<UUID, Long> SHOT_AT = EffectState.sessionConcurrent("puppeteer/shulker/shot_at");

    /** a left-click waiting to see if it's a tap (a bullet) or the start of a hold (a volley): when it was pressed. */
    private static final Map<UUID, Long> PRESSED = EffectState.sessionConcurrent("puppeteer/shulker/pressed");

    /** a volley going out: who each bullet's for, one after another. */
    private record Volley(Deque<UUID> targets, long next) {}

    private static final Map<UUID, Volley> VOLLEYS = EffectState.sessionConcurrent("puppeteer/shulker/volleys");

    // --- reading it (both sides) ----------------------------------------------------------------------

    public static boolean shulkerPuppet(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.SHULKER;
    }

    /** shut in its shell. */
    public static boolean shut(Player player) {
        return shulkerPuppet(player) && player.getData(WitchModAttachments.PUPPET_DASH_POWER) == 0;
    }

    /** the lid's peek, 0-100. */
    public static int peek(Player player) {
        return shulkerPuppet(player) ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : 30;
    }

    public static int selection(Player player) {
        return Mth.clamp(player.getData(WitchModAttachments.PUPPET_SPELL_SELECT), 0, BELT.length - 1);
    }

    /** the volley's charge 0..1 (the zoom, the hud), or -1 if not charging one. */
    public static float volleyCharge(Player player) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (!shulkerPuppet(player) || hold <= 0) {
            return -1.0F;
        }
        try {
            return Math.min(1.0F, hold / (float) Math.max(1, Config.PUPPETEER_SHULKER_VOLLEY_CHARGE_TICKS.get()));
        } catch (IllegalStateException e) {
            return -1.0F;
        }
    }

    /** whoever a bullet would lock onto right now. */
    @Nullable
    public static LivingEntity bulletTarget(Player player) {
        return Puppets.beamHit(player, Config.PUPPETEER_SHULKER_BULLET_RANGE.get(), 0.6).entity();
    }

    /** a teleport's landing: where, and the face it clings to (down: the floor; a side: a wall; up: the ceiling). */
    public record Landing(Vec3 spot, Direction attach) {}

    /** where a teleport would land, or null (shared by the server and your client's marker). */
    @Nullable
    public static Vec3 teleportSpot(Player player) {
        Landing landing = landing(player);
        return landing == null ? null : landing.spot();
    }

    /**
     * the face of the block you're looking at — floor, wall or ceiling, clinging to it as a real shulker does — if you'd
     * fit there. looking at no block (or one you wouldn't fit against): as far as you can go that way, onto whatever
     * face is nearest there (or, with none, it just drops).
     */
    @Nullable
    public static Landing landing(Player player) {
        double range = Config.PUPPETEER_SHULKER_TELEPORT_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult hit = player.pick(range, 1.0F, false);
        double reach = range;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos dest = block.getBlockPos().relative(block.getDirection());
            Vec3 spot = Vec3.atBottomCenterOf(dest);
            if (fits(player, spot)) {
                return new Landing(spot, block.getDirection().getOpposite());
            }
            reach = Math.max(0.0, hit.getLocation().distanceTo(eye) - 0.5);
        }
        // the furthest spot along your look you'd fit in
        for (double d = reach; d >= 1.5; d -= 0.5) {
            BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
            Vec3 spot = Vec3.atBottomCenterOf(at);
            if (fits(player, spot) && spot.distanceToSqr(player.position()) > 2.0) {
                return new Landing(spot, clingTo(player, at));
            }
        }
        return null;
    }

    private static boolean fits(Player player, Vec3 spot) {
        AABB box = player.getBoundingBox().move(spot.subtract(player.position()));
        return player.level().noCollision(player, box);
    }

    /** the face to cling to in the block at {@code at}: the floor first, then a wall, then the ceiling; down if none. */
    private static Direction clingTo(Player player, BlockPos at) {
        for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            if (solid(player.level(), at, d)) {
                return d;
            }
        }
        return Direction.DOWN;
    }

    /** there's a sturdy face to cling to on side {@code d} of the block at {@code at}. */
    private static boolean solid(net.minecraft.world.level.Level level, BlockPos at, Direction d) {
        BlockPos next = at.relative(d);
        return level.getBlockState(next).isFaceSturdy(level, next, d.getOpposite());
    }

    /** the face it's clinging to (in its mob data, so the model shows it and the restored shulker keeps it). */
    public static Direction attachFace(Player player) {
        return Direction.from3DDataValue(player.getData(WitchModAttachments.PUPPET_DATA).getByte("AttachFace"));
    }

    private static void setAttachFace(ServerPlayer player, Direction face) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        if (tag.getByte("AttachFace") != (byte) face.get3DDataValue() || !tag.contains("AttachFace")) {
            tag.putByte("AttachFace", (byte) face.get3DDataValue());
            player.setData(WitchModAttachments.PUPPET_DATA, tag);
        }
        player.setNoGravity(face != Direction.DOWN);
    }

    // --- every tick ------------------------------------------------------------------------------------

    @Override
    public void possess(ServerPlayer player, PuppetType type, net.minecraft.world.entity.Mob mob, CompoundTag data) {
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, 30);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, TELEPORT);
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, Config.PUPPETEER_SHULKER_TELEPORT_CHARGES.get());
        player.setData(WitchModAttachments.PUPPET_ACTION_READY, 0L);
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, 0L);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        long now = level.getGameTime();
        tickCharges(player, now);
        // clinging to a wall or ceiling: held there (no gravity) — until the block it's on goes, or it's moved off
        Direction attach = attachFace(player);
        if (attach != Direction.DOWN) {
            boolean centred = Math.abs(player.getX() - (Math.floor(player.getX()) + 0.5)) < 0.3
                    && Math.abs(player.getZ() - (Math.floor(player.getZ()) + 0.5)) < 0.3;
            if (!centred || !solid(level, player.blockPosition(), attach)) {
                setAttachFace(player, Direction.DOWN); // lost its hold: it drops
            } else {
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            }
        }
        // the shell: shut while right-click's held on Shell (off its cooldown), up to its time limit
        boolean held = Holds.HELD.contains(id);
        if (!held) {
            SPENT.remove(id);
        }
        Long shutAt = SHUT_AT.get(id);
        boolean wantShut = held && selection(player) == SHELL && !SPENT.contains(id) && (shutAt != null || Puppets.ready2(player));
        if (shutAt != null && now - shutAt >= Config.PUPPETEER_SHULKER_SHELL_MAX_TICKS.get()) {
            wantShut = false;
            SPENT.add(id); // held to its limit: let go before it shuts again
        }
        if (wantShut && shutAt == null) {
            SHUT_AT.put(id, now);
            player.setData(WitchModAttachments.PUPPET_FUSE, 0); // (shutting in drops a volley's charge)
            EffectUtil.addModifier(player, Attributes.ARMOR, SHELL_ARMOR_ID, Config.PUPPETEER_SHULKER_SHELL_ARMOR.get(),
                    AttributeModifier.Operation.ADD_VALUE);
            EffectUtil.addModifier(player, Attributes.KNOCKBACK_RESISTANCE, SHELL_KB_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
            level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_CLOSE, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else if (!wantShut && shutAt != null) {
            openShell(player);
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY, now + Config.PUPPETEER_SHULKER_SHELL_COOLDOWN_TICKS.get());
        }
        tickVolleyCharge(player, now);
        // a left-click that wasn't the start of a hold: a plain bullet
        Long pressed = PRESSED.get(id);
        if (pressed != null && !Holds.ATTACK_HELD.contains(id) && now - pressed >= 2) {
            PRESSED.remove(id);
            bullet(player, null, false);
        }
        tickVolley(player, now);
        // the lid: shut; creaking open as a volley charges; wide just after a shot; else peeking
        float charge = volleyCharge(player);
        int peek = SHUT_AT.containsKey(id) ? 0 : charge >= 0.0F ? 30 + Math.round(70.0F * charge)
                : now - SHOT_AT.getOrDefault(id, Long.MIN_VALUE / 2) < 20 ? 100 : 30;
        if (peek != player.getData(WitchModAttachments.PUPPET_DASH_POWER)) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, peek);
        }
        // bullets past their time or range just fizzle
        List<Shot> shots = SHOTS.get(id);
        if (shots != null) {
            double reach = Config.PUPPETEER_SHULKER_BULLET_MAX_RANGE.get();
            shots.removeIf(shot -> {
                if (!shot.bullet().isAlive()) {
                    return true;
                }
                if (now >= shot.expires() || shot.bullet().position().distanceToSqr(shot.from()) > reach * reach) {
                    shot.bullet().discard();
                    return true;
                }
                return false;
            });
        }
    }

    /** teleports come back one at a time. */
    private static void tickCharges(ServerPlayer player, long now) {
        int max = Config.PUPPETEER_SHULKER_TELEPORT_CHARGES.get();
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        if (charges >= max) {
            return;
        }
        long back = player.getData(WitchModAttachments.PUPPET_ACTION_READY);
        if (back <= 0L) {
            player.setData(WitchModAttachments.PUPPET_ACTION_READY, now + Config.PUPPETEER_SHULKER_TELEPORT_COOLDOWN_TICKS.get());
        } else if (now >= back) {
            charges++;
            player.setData(WitchModAttachments.PUPPET_TP_CHARGES, charges);
            player.setData(WitchModAttachments.PUPPET_ACTION_READY,
                    charges < max ? now + Config.PUPPETEER_SHULKER_TELEPORT_COOLDOWN_TICKS.get() : 0L);
            player.playNotifySound(SoundEvents.SHULKER_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** holding left-click (lid open, not shut in, the volley ready): the charge builds, sparks drawing in. */
    private static void tickVolleyCharge(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        if (!Holds.ATTACK_HELD.contains(id) || SHUT_AT.containsKey(id) || now < player.getData(WitchModAttachments.PUPPET_LUNGE_START)
                || VOLLEYS.containsKey(id)) {
            return;
        }
        int full = Config.PUPPETEER_SHULKER_VOLLEY_CHARGE_TICKS.get();
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (hold == 0) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SHULKER_OPEN, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        int next = Math.min(full, hold + Holds.chargeStep(player));
        player.setData(WitchModAttachments.PUPPET_FUSE, next);
        ServerLevel level = player.serverLevel();
        float share = next / (float) Math.max(1, full);
        // sparks drawn in towards the opening shell, more and faster as it builds
        for (int i = 0; i < 1 + (int) (share * 3); i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double r = 1.2 - share * 0.5;
            level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(a) * r, player.getY() + 0.5 + level.random.nextDouble() * 0.6,
                    player.getZ() + Math.sin(a) * r, 0, -Math.cos(a), 0.0, -Math.sin(a), 0.05 + share * 0.05);
        }
        if (next >= full && hold < full) {
            level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_AMBIENT, SoundSource.PLAYERS, 1.2F, 1.4F); // ready
        }
    }

    /** a volley's bullets go out one after another. */
    private static void tickVolley(ServerPlayer player, long now) {
        Volley volley = VOLLEYS.get(player.getUUID());
        if (volley == null || now < volley.next()) {
            return;
        }
        UUID who = volley.targets().pollFirst();
        Entity target = who == null ? null : player.serverLevel().getEntity(who);
        bullet(player, target instanceof LivingEntity l && l.isAlive() ? l : null, true);
        if (volley.targets().isEmpty()) {
            VOLLEYS.remove(player.getUUID());
        } else {
            VOLLEYS.put(player.getUUID(), new Volley(volley.targets(), now + Config.PUPPETEER_SHULKER_VOLLEY_INTERVAL_TICKS.get()));
        }
    }

    private static void openShell(ServerPlayer player) {
        SHUT_AT.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.ARMOR, SHELL_ARMOR_ID);
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, SHELL_KB_ID);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SHULKER_OPEN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // --- right-click: the belt ---------------------------------------------------------------------------

    @Override
    public boolean scrolls(PuppetType type) {
        return true;
    }

    /** scroll between Teleport and Shell (not while shut in). */
    @Override
    public void scroll(ServerPlayer player, PuppetType type, int dir) {
        if (SHUT_AT.containsKey(player.getUUID())) {
            return;
        }
        int sel = Math.floorMod(selection(player) + Integer.signum(dir), BELT.length);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, sel);
        player.playNotifySound(SoundEvents.SHULKER_BULLET_HURT, SoundSource.PLAYERS, 0.5F, 1.2F + sel * 0.3F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.shulker_select",
                Component.translatable("witchmod.puppeteer.action." + BELT[sel])).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** the right-click hold doesn't charge anything (the shell's just held). */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** on Teleport, a right-click (let go) teleports. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (selection(player) == TELEPORT && !SHUT_AT.containsKey(player.getUUID())) {
            teleport(player);
        }
    }

    /** onto the face you're looking at (or as far as you can go that way), clinging there — one charge. */
    private static void teleport(ServerPlayer player) {
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        Landing landing = landing(player);
        if (charges <= 0 || landing == null) {
            return; // no charge, or nowhere to land: nothing spent
        }
        Vec3 spot = landing.spot();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.teleportTo(spot.x, spot.y, spot.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        setAttachFace(player, landing.attach());
        level.playSound(null, BlockPos.containing(spot), SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, charges - 1);
        if (player.getData(WitchModAttachments.PUPPET_ACTION_READY) <= level.getGameTime()) {
            player.setData(WitchModAttachments.PUPPET_ACTION_READY, level.getGameTime() + Config.PUPPETEER_SHULKER_TELEPORT_COOLDOWN_TICKS.get());
        }
        Puppets.countAction(player);
    }

    // --- left-click: a bullet, or (held) a volley ------------------------------------------------------

    /** pressed: a bullet if it's let go at once, a volley's charge if it's held (see tick / releaseAttackHeld). */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (!SHUT_AT.containsKey(player.getUUID())) {
            PRESSED.put(player.getUUID(), player.level().getGameTime());
        }
    }

    @Override
    public boolean holdsAttack(PuppetType type) {
        return true;
    }

    /** let go: a full charge looses the volley; anything less is just a bullet. */
    @Override
    public void releaseAttackHeld(ServerPlayer player, PuppetType type, int charge) {
        PRESSED.remove(player.getUUID());
        if (SHUT_AT.containsKey(player.getUUID())) {
            return;
        }
        if (charge >= Config.PUPPETEER_SHULKER_VOLLEY_CHARGE_TICKS.get()
                && player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_LUNGE_START)) {
            volley(player);
        } else {
            bullet(player, null, false);
        }
    }

    /**
     * the volley: puppeteerShulkerVolleyCount bullets, one after another — at your target and whoever's close by it
     * (shared out between them, your target first). no target: they all fly straight on.
     */
    private static void volley(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now + Config.PUPPETEER_SHULKER_VOLLEY_COOLDOWN_TICKS.get());
        LivingEntity main = bulletTarget(player);
        List<UUID> pool = new ArrayList<>();
        if (main != null) {
            pool.add(main.getUUID());
            double r = Config.PUPPETEER_SHULKER_VOLLEY_SPREAD_RADIUS.get();
            level.getEntitiesOfClass(LivingEntity.class, main.getBoundingBox().inflate(r), e -> e != main && e != player && e.isAlive()
                            && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(main) <= r * r)
                    .stream().sorted(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(main)))
                    .forEach(e -> pool.add(e.getUUID()));
        }
        Deque<UUID> targets = new ArrayDeque<>();
        int count = Config.PUPPETEER_SHULKER_VOLLEY_COUNT.get();
        for (int i = 0; i < count; i++) {
            targets.add(pool.isEmpty() ? new UUID(0L, 0L) : pool.get(i % pool.size()));
        }
        VOLLEYS.put(player.getUUID(), new Volley(targets, now));
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 2.5F, 0.6F);
    }

    /** one bullet: homing on {@code target} (or whoever you're looking at), or straight down your aim at no one. */
    private static void bullet(ServerPlayer player, @Nullable LivingEntity target, boolean volley) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (!volley) {
            if (now < player.getData(WitchModAttachments.PUPPET_RAGE_END) || SHUT_AT.containsKey(player.getUUID())) {
                return;
            }
            player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_SHULKER_BULLET_COOLDOWN_TICKS.get());
            target = bulletTarget(player);
        }
        ShulkerBullet bullet = new ShulkerBullet(level, player, target, Direction.Axis.Y);
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.8)).subtract(0.0, 0.2, 0.0);
        if (volley) {
            from = from.add((level.random.nextDouble() - 0.5) * 0.6, level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.6);
            bullet.addTag(VOLLEY_TAG);
        }
        bullet.moveTo(from.x, from.y, from.z, player.getYRot(), player.getXRot());
        if (target == null) {
            bullet.setNoGravity(true); // no one to home on: straight down your aim
            bullet.setDeltaMovement(look.scale(Config.PUPPETEER_SHULKER_BULLET_SPEED.get()));
        }
        level.addFreshEntity(bullet);
        SHOTS.computeIfAbsent(player.getUUID(), k -> new CopyOnWriteArrayList<>())
                .add(new Shot(bullet, now + Config.PUPPETEER_SHULKER_BULLET_LIFE_TICKS.get(), from));
        SHOT_AT.put(player.getUUID(), now);
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 2.0F,
                (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.2F + (volley ? 1.3F : 1.0F));
    }

    /** a volley's bullet lifts harder (vanilla's levitation, a level up). */
    @Override
    public void ownProjectileHit(ServerPlayer player, PuppetType type, Projectile projectile, @Nullable Entity struck,
                                 ProjectileImpactEvent event) {
        if (projectile instanceof ShulkerBullet && projectile.getTags().contains(VOLLEY_TAG) && struck instanceof LivingEntity victim) {
            // (vanilla's own weaker levitation lands after this, and doesn't replace it)
            victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200,
                    Math.max(0, Config.PUPPETEER_SHULKER_VOLLEY_LEVITATION_LEVEL.get() - 1)), player);
        }
    }

    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** shut in: projectiles glance off, as off a closed shulker. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (SHUT_AT.containsKey(player.getUUID()) && event.getSource().getDirectEntity() instanceof Projectile) {
            event.setCanceled(true);
        }
    }

    /** every hit you take brings the next teleport back sooner. */
    @Override
    public void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {
        long back = player.getData(WitchModAttachments.PUPPET_ACTION_READY);
        long now = player.level().getGameTime();
        long cut = Math.round(Config.PUPPETEER_SHULKER_HIT_COOLDOWN_CUT_SECONDS.get() * 20.0);
        if (back > now) {
            player.setData(WitchModAttachments.PUPPET_ACTION_READY, Math.max(now, back - cut));
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        UUID id = player.getUUID();
        SHUT_AT.remove(id);
        SPENT.remove(id);
        SHOT_AT.remove(id);
        SHOTS.remove(id);
        PRESSED.remove(id);
        VOLLEYS.remove(id);
        EffectUtil.removeModifier(player, Attributes.ARMOR, SHELL_ARMOR_ID);
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, SHELL_KB_ID);
        if (shulkerPuppet(player)) {
            player.setNoGravity(false); // (the shulker left behind keeps clinging where it was: its data has the face)
        }
    }

    /** a shulker's body: a block-sized box (so it fits wherever a shulker does), not a player's. */
    @Override
    public net.minecraft.world.entity.EntityDimensions size(Player player, PuppetType type, net.minecraft.world.entity.EntityDimensions dims) {
        return new net.minecraft.world.entity.EntityDimensions(dims.width(), 1.0F, Math.min(dims.eyeHeight(), 0.6F), dims.attachments(), dims.fixed());
    }

    // --- the hud -------------------------------------------------------------------------------------------

    /** the hud's bar: the volley charging. */
    public static float hudProgress(Player player) {
        return volleyCharge(player);
    }

    /** bullet / volley on left-click; the belt (scroll), and its move on right-click. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.shulker_bullet", player.getData(WitchModAttachments.PUPPET_RAGE_END));
        out.add(hold(Key.ATTACK), "witchmod.puppeteer.action.shulker_volley", player.getData(WitchModAttachments.PUPPET_LUNGE_START));
        int sel = selection(player);
        out.add(List.of(tap(Key.SCROLL)), Component.translatable("witchmod.puppeteer.shulker_select",
                Component.translatable("witchmod.puppeteer.action." + BELT[sel])), 0L);
        if (sel == TELEPORT) {
            int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
            out.add(List.of(tap(Key.USE)), Component.translatable("witchmod.puppeteer.action.shulker_teleport_charges", charges),
                    charges > 0 ? 0L : player.getData(WitchModAttachments.PUPPET_ACTION_READY));
        } else {
            out.add(hold(Key.USE), "witchmod.puppeteer.action.shulker_shell", PuppetControls.ready2(player));
        }
    }
}
