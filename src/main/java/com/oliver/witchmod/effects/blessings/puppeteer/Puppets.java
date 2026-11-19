package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.CreeperBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the shared helpers — who's possessed (and as what), cooldowns, aiming, rallies, sounds. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class Puppets {
    private Puppets() {}

    /** blessing yellow — the possession's sparks are souls (blue) plus the blessing palette (yellow + white). */
    public static final DustParticleOptions BLESSED_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 1.2F);

    /** players dealing beam / thorns damage right now — it skips the "puppet hits do base damage" override. */
    public static final Set<UUID> SPECIAL_DAMAGE = EffectState.sessionSet("puppeteer/puppets/special_damage");

    /** mobs being forced to retaliate against the puppet that just hit them — so onTarget lets the grudge through. */
    public static final Set<UUID> FORCED_TARGET = EffectState.sessionSet("puppeteer/puppets/forced_target");

    /** each guardian puppet's position last tick — a guardian holding still has its spikes out (thorns). */
    public static final Map<UUID, Vec3> LAST_POS = EffectState.sessionConcurrent("puppeteer/puppets/last_pos");

    public static final Set<UUID> STILL = EffectState.sessionSet("puppeteer/puppets/still");

    public static boolean isBabyPuppet(ServerPlayer player) {
        return player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0;
    }

    /** a music check, cached per player for half a second: when it was made, the radius, and the answer. */
    private record MusicCheck(long at, double radius, boolean heard) {}

    private static final Map<UUID, MusicCheck> MUSIC = EffectState.sessionConcurrent("puppeteer/puppets/music");

    /** whether a jukebox is playing within {@code radius} of {@code player} (re-checked every 10 ticks; both sides). */
    public static boolean hearsMusic(Player player, double radius) {
        long now = player.level().getGameTime();
        MusicCheck last = MUSIC.get(player.getUUID());
        if (last != null && last.radius() == radius && now - last.at() < 10 && now >= last.at()) {
            return last.heard();
        }
        int r = (int) Math.ceil(radius);
        BlockPos at = player.blockPosition();
        boolean heard = false;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
            net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.JUKEBOX)
                    && state.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)
                    && pos.closerToCenterThan(player.position(), radius)) {
                heard = true;
                break;
            }
        }
        MUSIC.put(player.getUUID(), new MusicCheck(now, radius, heard));
        return heard;
    }

    /** someone a puppet's been told about: who, until when the trail points at them, and its colour. */
    private record Alert(UUID offender, long until, net.minecraft.core.particles.DustParticleOptions colour) {}

    private static final Map<UUID, Alert> ALERTS = EffectState.sessionConcurrent("puppeteer/puppets/alerts");

    /**
     * point {@code player} at {@code offender} for {@code ticks}: a dust trail from you to them, and a marker over their
     * head, both sent to you alone (a piglin's gold-meddler, a polar bear's cub-hurter). see {@link #tickAlert}.
     */
    public static void alert(ServerPlayer player, Entity offender, int ticks, org.joml.Vector3f colour) {
        ALERTS.put(player.getUUID(), new Alert(offender.getUUID(), player.level().getGameTime() + ticks,
                new net.minecraft.core.particles.DustParticleOptions(colour, 1.0F)));
    }

    public static void clearAlert(ServerPlayer player) {
        ALERTS.remove(player.getUUID());
    }

    /** an alert's trail, drawn a few times a second until it runs out or its target's gone. */
    public static void tickAlert(ServerPlayer player) {
        Alert alert = ALERTS.get(player.getUUID());
        if (alert == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity who = level.getEntity(alert.offender());
        if (level.getGameTime() > alert.until() || who == null || !who.isAlive() || who.level() != level) {
            ALERTS.remove(player.getUUID());
            return;
        }
        if (player.tickCount % 4 != 0) {
            return;
        }
        Vec3 to = who.getEyePosition();
        Vec3 dir = to.subtract(player.getEyePosition());
        double len = dir.length();
        if (len < 1.5) {
            return;
        }
        Vec3 step = dir.scale(0.7 / len);
        Vec3 from = player.getEyePosition().add(dir.scale(1.0 / len)).subtract(0.0, 0.3, 0.0); // a block out, below your eyes
        for (int i = 0; i * 0.7 < len - 1.0; i++) {
            Vec3 at = from.add(step.scale(i));
            level.sendParticles(player, alert.colour(), true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(player, ParticleTypes.ANGRY_VILLAGER, true, who.getX(), who.getEyeY() + 0.8, who.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** the wind-up slow of a held move (an evoker's cast, a wolf's maul, a ravager's roar): on while charging. */
    public static final net.minecraft.resources.ResourceLocation CHARGE_SLOW_ID = EffectUtil.modifierId("puppeteer_charge_slow");

    /** slows you by {@code amount} (0.4 = -40%) while {@code charging}, and lets go once you're not. */
    public static void chargeSlow(ServerPlayer player, boolean charging, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean slow = charging && amount > 0.0;
        if (speed != null && slow != speed.hasModifier(CHARGE_SLOW_ID)) {
            if (slow) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, CHARGE_SLOW_ID, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(CHARGE_SLOW_ID);
            }
        }
    }

    /** charging a shot slows you on foot (flying, the client slows the fly speed instead — see DisguiseClient). */
    public static void chargeSlow(ServerPlayer player) {
        boolean charging = player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && charging != speed.hasModifier(FUSE_SLOW_ID)) {
            if (charging) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(FUSE_SLOW_ID);
            }
        }
    }

    /** puppet-fired projectiles that have already landed a hit (some can register two hits as they pass through). */
    public static final String HIT_TAG = "witchmod_hit";

    /** switch a live puppet to another type in place (zombification, infection): type, speed, reach, size. */
    public static void retype(ServerPlayer player, PuppetType type) {
        player.setData(WitchModAttachments.PUPPET_TYPE, type.id());
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, type.speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            EffectUtil.addModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, type.reach() - reach.getBaseValue(),
                    AttributeModifier.Operation.ADD_VALUE);
        }
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        HELD.remove(player.getUUID());
        player.refreshDimensions();
    }

    /** a held, sit-still move in progress (a guardian's beam, an axolotl playing dead): held and running or ready. */
    public static boolean channelling(ServerPlayer player) {
        return HELD.contains(player.getUUID()) && (player.getData(WitchModAttachments.PUPPET_FUSE) > 0 || ready(player));
    }

    /** where a beam from {@code player}'s eye ends (first block or living thing within range) and what it's on. */
    public record BeamHit(Vec3 end, @Nullable LivingEntity entity) {}

    public static BeamHit beamHit(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        net.minecraft.world.phys.HitResult block = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            end = block.getLocation();
        }
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(), player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != player);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return new BeamHit(hit.getLocation(), living);
        }
        return new BeamHit(end, null);
    }

    /**
     * a guardian's beam: as beamHit, but anything within {@code margin} of the line counts as on it (the nearest such
     * thing along the beam), so tracking a moving target is forgiving.
     */
    public static BeamHit beamHit(Player player, double range, double margin) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        net.minecraft.world.phys.HitResult block = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            end = block.getLocation();
        }
        LivingEntity best = null;
        Vec3 bestAt = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.level().getEntities(player, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0 + margin),
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator())) {
            net.minecraft.world.phys.AABB box = e.getBoundingBox().inflate(margin);
            java.util.Optional<Vec3> at = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
            if (at.isPresent() && eye.distanceToSqr(at.get()) < bestDist) {
                best = (LivingEntity) e;
                bestAt = at.get();
                bestDist = eye.distanceToSqr(bestAt);
            }
        }
        return best != null ? new BeamHit(bestAt, best) : new BeamHit(end, null);
    }

    /** entities taking beam-trickle damage right now — their knockback is cancelled (see onKnockback). */
    public static final Set<UUID> NO_KNOCKBACK = EffectState.sessionSet("puppeteer/puppets/no_knockback");

    @SubscribeEvent
    public static void onKnockback(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if (NO_KNOCKBACK.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // --- flight ------------------------------------------------------------------------------------

    /** a puppet that flies (the bat-disguise style flight). */
    public static boolean flyingPuppet(Player player) {
        PuppetType t = player.hasData(WitchModAttachments.PUPPET_TYPE) ? PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) : null;
        return t == PuppetType.BAT || t == PuppetType.PHANTOM || t == PuppetType.GHAST || t == PuppetType.BLAZE
                || t == PuppetType.PARROT || t == PuppetType.BEE;
    }

    /** a slow flier: never allowed to sprint-fly, Flight blessing or not. */
    public static boolean slowFlier(Player player) {
        PuppetType t = player.hasData(WitchModAttachments.PUPPET_TYPE) ? PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) : null;
        return t == PuppetType.GHAST || t == PuppetType.BLAZE || t == PuppetType.BREEZE || t == PuppetType.PARROT
                || t == PuppetType.BEE || t == PuppetType.VEX || t == PuppetType.ALLAY;
    }

    /** a mob that never lands — it's always in the air, so its puppet can't walk. */
    public static boolean permanentFlier(Player player) {
        PuppetType t = player.hasData(WitchModAttachments.PUPPET_TYPE) ? PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) : null;
        return t == PuppetType.BAT || t == PuppetType.PHANTOM || t == PuppetType.GHAST || t == PuppetType.VEX
                || t == PuppetType.ALLAY || t == PuppetType.BEE;
    }

    /**
     * flying, and not allowed to sprint-fly right now: a slow flier always; a quick one (bat, phantom; the bat disguise)
     * unless the Flight blessing gives it its sprint. (vanilla doubles fly speed while sprinting: see PlayerFlySpeedMixin.)
     */
    public static boolean noSprintFly(Player player) {
        if (!player.getAbilities().flying || player.isCreative() || player.isSpectator()) {
            return false;
        }
        boolean bat = player.hasData(WitchModAttachments.DISGUISE_TYPE) && player.getData(WitchModAttachments.DISGUISE_TYPE) == 3;
        boolean puppet = flyingPuppet(player) || slowFlier(player);
        if (!bat && !puppet) {
            return false;
        }
        boolean flight = player.hasData(WitchModAttachments.FLIGHT_ACTIVE) && player.getData(WitchModAttachments.FLIGHT_ACTIVE) >= 1;
        return slowFlier(player) || !flight;
    }

    // --- room to stand -------------------------------------------------------------------------------

    /**
     * the nearest spot to {@code from} where {@code entity} fits without touching a block: straight up first (out of
     * the ground), then a widening ring. {@code from} itself if it already fits, or nothing nearby does.
     */
    public static Vec3 freeSpot(net.minecraft.world.level.Level level, Entity entity, Vec3 from) {
        return freeSpot(level, entity, entity.getDimensions(entity.getPose()).makeBoundingBox(from), from);
    }

    /** as {@link #freeSpot(net.minecraft.world.level.Level, Entity, Vec3)}, for {@code box} (placed at {@code from}). */
    public static Vec3 freeSpot(net.minecraft.world.level.Level level, Entity entity, net.minecraft.world.phys.AABB box, Vec3 from) {
        if (level.noCollision(entity, box)) {
            return from;
        }
        for (int dy = 1; dy <= 24; dy++) {
            if (level.noCollision(entity, box.move(0.0, dy, 0.0))) {
                return from.add(0.0, dy, 0.0);
            }
        }
        for (int r = 1; r <= 4; r++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == r && level.noCollision(entity, box.move(dx, dy, dz))) {
                            return from.add(dx, dy, dz);
                        }
                    }
                }
            }
        }
        return from;
    }

    /** out of any block you're stuck in (after stepping out of a puppet that lived in them, or a crash mid-way). */
    public static void unstick(ServerPlayer player) {
        player.noPhysics = false;
        Vec3 spot = freeSpot(player.level(), player, player.position());
        if (!spot.equals(player.position())) {
            player.teleportTo(spot.x, spot.y, spot.z);
            player.resetFallDistance();
        }
    }

    // --- state ---------------------------------------------------------------------------------------

    @Nullable
    public static PuppetType possessed(ServerPlayer player) {
        String id = player.getData(WitchModAttachments.PUPPET_TYPE);
        return id.isEmpty() ? null : PuppetType.byId(id);
    }

    /** isPuppet without creating the (synced) attachment — safe from anywhere, even mid-construction. */
    public static boolean isPuppetSafe(Player player) {
        return player.hasData(WitchModAttachments.PUPPET_TYPE) && !player.getData(WitchModAttachments.PUPPET_TYPE).isEmpty();
    }

    /** the puppets that swim like their mob (dive where they look in water), not just paddle. */
    public static boolean swimmer(@Nullable PuppetType type) {
        return type != null && (type == PuppetType.DROWNED || type.group() == PuppetGroup.FISH || type.group() == PuppetGroup.SQUID
                || type.group() == PuppetGroup.GUARDIAN || type.group() == PuppetGroup.DOLPHIN || type.group() == PuppetGroup.AXOLOTL
                || type == PuppetType.TURTLE);
    }

    /** true while {@code player} is wearing a puppet — effects that reshape YOUR body hold off until you step out. */
    public static boolean isPuppet(@Nullable Player player) {
        return player != null && !player.getData(WitchModAttachments.PUPPET_TYPE).isEmpty();
    }

    @Nullable
    public static PuppetMove moveOf(@Nullable Player player) {
        PuppetType type = player == null ? null : PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        return type == null ? null : type.move();
    }

    /** bat: the disguise bat's slow creative-style flight (survival only — a creative player's own flight is left alone). */
    public static void grantBatFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || player.getAbilities().mayfly) {
            return;
        }
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        player.setData(WitchModAttachments.PUPPET_GRANTED_FLIGHT, true); // saved: undone on login after a crash
    }

    public static void revokeBatFlight(ServerPlayer player) {
        if (!player.getData(WitchModAttachments.PUPPET_GRANTED_FLIGHT)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_GRANTED_FLIGHT, false);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    /** a skeleton puppet about to draw (not crouching — crouch + right-click still leaves). */
    public static boolean drawsBow(@Nullable Player player) {
        return player != null && !player.isShiftKeyDown() && (moveOf(player) == PuppetMove.BOW || moveOf(player) == PuppetMove.CROSSBOW);
    }

    /** possessing, or mid-possession: either way, no normal interaction. */
    public static boolean isBusy(@Nullable Player player) {
        return isPuppet(player) || (player != null && player.getData(WitchModAttachments.PUPPET_BINDING_END) > 0);
    }

    public static SoundEvent ambientOf(PuppetType type) {
        return switch (type) {
            case CREEPER -> SoundEvents.CREEPER_HURT;
            case HUSK -> SoundEvents.HUSK_AMBIENT;
            case DROWNED -> SoundEvents.DROWNED_AMBIENT;
            case ZOMBIE_VILLAGER -> SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
            case PIG -> SoundEvents.PIG_AMBIENT;
            case COW -> SoundEvents.COW_AMBIENT;
            case SHEEP -> SoundEvents.SHEEP_AMBIENT;
            case CHICKEN -> SoundEvents.CHICKEN_AMBIENT;
            case COD -> SoundEvents.COD_AMBIENT;
            case SALMON -> SoundEvents.SALMON_AMBIENT;
            case PUFFERFISH -> SoundEvents.PUFFER_FISH_AMBIENT;
            case TROPICAL_FISH -> SoundEvents.TROPICAL_FISH_AMBIENT;
            case SKELETON -> SoundEvents.SKELETON_AMBIENT;
            case STRAY -> SoundEvents.STRAY_AMBIENT;
            case BOGGED -> SoundEvents.BOGGED_AMBIENT;
            case SPIDER, CAVE_SPIDER -> SoundEvents.SPIDER_AMBIENT;
            case BAT -> SoundEvents.BAT_AMBIENT;
            case MOOSHROOM -> SoundEvents.COW_AMBIENT;
            case SQUID -> SoundEvents.SQUID_AMBIENT;
            case GLOW_SQUID -> SoundEvents.GLOW_SQUID_AMBIENT;
            case ZOMBIFIED_PIGLIN -> SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT;
            case WITHER_SKELETON -> SoundEvents.WITHER_SKELETON_AMBIENT;
            case GUARDIAN -> SoundEvents.GUARDIAN_AMBIENT;
            case ELDER_GUARDIAN -> SoundEvents.ELDER_GUARDIAN_AMBIENT;
            case HOGLIN -> SoundEvents.HOGLIN_AMBIENT;
            case ZOGLIN -> SoundEvents.ZOGLIN_AMBIENT;
            case DOLPHIN -> SoundEvents.DOLPHIN_AMBIENT_WATER;
            case AXOLOTL -> SoundEvents.AXOLOTL_IDLE_WATER;
            case PHANTOM -> SoundEvents.PHANTOM_AMBIENT;
            case IRON_GOLEM -> SoundEvents.IRON_GOLEM_REPAIR;
            case SNOW_GOLEM -> SoundEvents.SNOW_GOLEM_AMBIENT;
            case VILLAGER -> SoundEvents.VILLAGER_AMBIENT;
            case RABBIT -> SoundEvents.RABBIT_AMBIENT;
            case KILLER_RABBIT -> SoundEvents.RABBIT_ATTACK;
            case SILVERFISH -> SoundEvents.SILVERFISH_AMBIENT;
            case ENDERMITE -> SoundEvents.ENDERMITE_AMBIENT;
            case ENDERMAN -> SoundEvents.ENDERMAN_AMBIENT;
            case HORSE -> SoundEvents.HORSE_AMBIENT;
            case DONKEY -> SoundEvents.DONKEY_AMBIENT;
            case MULE -> SoundEvents.MULE_AMBIENT;
            case ZOMBIE_HORSE -> SoundEvents.ZOMBIE_HORSE_AMBIENT;
            case SKELETON_HORSE -> SoundEvents.SKELETON_HORSE_AMBIENT;
            case SLIME -> SoundEvents.SLIME_JUMP;
            case MAGMA_CUBE -> SoundEvents.MAGMA_CUBE_JUMP;
            case LLAMA, TRADER_LLAMA -> SoundEvents.LLAMA_AMBIENT;
            case GHAST -> SoundEvents.GHAST_AMBIENT;
            case BLAZE -> SoundEvents.BLAZE_AMBIENT;
            case BREEZE -> SoundEvents.BREEZE_IDLE_AIR;
            case WITCH -> SoundEvents.WITCH_AMBIENT;
            case CAMEL -> SoundEvents.CAMEL_AMBIENT;
            case GOAT -> SoundEvents.GOAT_AMBIENT;
            case SCREAMING_GOAT -> SoundEvents.GOAT_SCREAMING_AMBIENT;
            case PILLAGER -> SoundEvents.PILLAGER_AMBIENT;
            case FOX -> SoundEvents.FOX_AMBIENT;
            case VINDICATOR -> SoundEvents.VINDICATOR_AMBIENT;
            case PIGLIN_BRUTE -> SoundEvents.PIGLIN_BRUTE_AMBIENT;
            case EVOKER -> SoundEvents.EVOKER_AMBIENT;
            case WOLF -> SoundEvents.WOLF_AMBIENT;
            case CAT -> SoundEvents.CAT_AMBIENT;
            case OCELOT -> SoundEvents.OCELOT_AMBIENT;
            case PIGLIN -> SoundEvents.PIGLIN_AMBIENT;
            case WANDERING_TRADER -> SoundEvents.WANDERING_TRADER_AMBIENT;
            case RAVAGER -> SoundEvents.RAVAGER_AMBIENT;
            case VEX -> SoundEvents.VEX_AMBIENT;
            case ALLAY -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
            case PANDA -> SoundEvents.PANDA_AMBIENT;
            case STRIDER -> SoundEvents.STRIDER_AMBIENT;
            case PARROT -> SoundEvents.PARROT_AMBIENT;
            case POLAR_BEAR -> SoundEvents.POLAR_BEAR_AMBIENT;
            case TURTLE -> SoundEvents.TURTLE_AMBIENT_LAND;
            case ARMADILLO -> SoundEvents.ARMADILLO_AMBIENT;
            case BEE -> SoundEvents.BEE_POLLINATE;
            case TADPOLE -> SoundEvents.TADPOLE_FLOP;
            case FROG -> SoundEvents.FROG_AMBIENT;
            case SNIFFER -> SoundEvents.SNIFFER_IDLE;
            case SHULKER -> SoundEvents.SHULKER_AMBIENT;
            case WARDEN -> SoundEvents.WARDEN_AMBIENT;
            default -> SoundEvents.ZOMBIE_AMBIENT;
        };
    }

    public static SoundEvent flopOf(PuppetType type) {
        return switch (type) {
            case SALMON -> SoundEvents.SALMON_FLOP;
            case PUFFERFISH -> SoundEvents.PUFFER_FISH_FLOP;
            case TADPOLE -> SoundEvents.TADPOLE_FLOP;
            case TROPICAL_FISH -> SoundEvents.TROPICAL_FISH_FLOP;
            default -> SoundEvents.COD_FLOP;
        };
    }

    public static SoundEvent hurtOf(PuppetType type) {
        return switch (type) {
            case CREEPER -> SoundEvents.CREEPER_HURT;
            case HUSK -> SoundEvents.HUSK_HURT;
            case DROWNED -> SoundEvents.DROWNED_HURT;
            case ZOMBIE_VILLAGER -> SoundEvents.ZOMBIE_VILLAGER_HURT;
            case PIG -> SoundEvents.PIG_HURT;
            case COW -> SoundEvents.COW_HURT;
            case SHEEP -> SoundEvents.SHEEP_HURT;
            case CHICKEN -> SoundEvents.CHICKEN_HURT;
            case COD -> SoundEvents.COD_HURT;
            case SALMON -> SoundEvents.SALMON_HURT;
            case PUFFERFISH -> SoundEvents.PUFFER_FISH_HURT;
            case TROPICAL_FISH -> SoundEvents.TROPICAL_FISH_HURT;
            case SKELETON -> SoundEvents.SKELETON_HURT;
            case STRAY -> SoundEvents.STRAY_HURT;
            case BOGGED -> SoundEvents.BOGGED_HURT;
            case SPIDER, CAVE_SPIDER -> SoundEvents.SPIDER_HURT;
            case BAT -> SoundEvents.BAT_HURT;
            case MOOSHROOM -> SoundEvents.COW_HURT;
            case SQUID -> SoundEvents.SQUID_HURT;
            case GLOW_SQUID -> SoundEvents.GLOW_SQUID_HURT;
            case ZOMBIFIED_PIGLIN -> SoundEvents.ZOMBIFIED_PIGLIN_HURT;
            case WITHER_SKELETON -> SoundEvents.WITHER_SKELETON_HURT;
            case GUARDIAN -> SoundEvents.GUARDIAN_HURT;
            case ELDER_GUARDIAN -> SoundEvents.ELDER_GUARDIAN_HURT;
            case HOGLIN -> SoundEvents.HOGLIN_HURT;
            case ZOGLIN -> SoundEvents.ZOGLIN_HURT;
            case DOLPHIN -> SoundEvents.DOLPHIN_HURT;
            case AXOLOTL -> SoundEvents.AXOLOTL_HURT;
            case PHANTOM -> SoundEvents.PHANTOM_HURT;
            case IRON_GOLEM -> SoundEvents.IRON_GOLEM_HURT;
            case SNOW_GOLEM -> SoundEvents.SNOW_GOLEM_HURT;
            case VILLAGER -> SoundEvents.VILLAGER_HURT;
            case RABBIT, KILLER_RABBIT -> SoundEvents.RABBIT_HURT;
            case SILVERFISH -> SoundEvents.SILVERFISH_HURT;
            case ENDERMITE -> SoundEvents.ENDERMITE_HURT;
            case ENDERMAN -> SoundEvents.ENDERMAN_HURT;
            case HORSE -> SoundEvents.HORSE_HURT;
            case DONKEY -> SoundEvents.DONKEY_HURT;
            case MULE -> SoundEvents.MULE_HURT;
            case ZOMBIE_HORSE -> SoundEvents.ZOMBIE_HORSE_HURT;
            case SKELETON_HORSE -> SoundEvents.SKELETON_HORSE_HURT;
            case SLIME -> SoundEvents.SLIME_HURT;
            case MAGMA_CUBE -> SoundEvents.MAGMA_CUBE_HURT;
            case LLAMA, TRADER_LLAMA -> SoundEvents.LLAMA_HURT;
            case GHAST -> SoundEvents.GHAST_HURT;
            case BLAZE -> SoundEvents.BLAZE_HURT;
            case BREEZE -> SoundEvents.BREEZE_HURT;
            case WITCH -> SoundEvents.WITCH_HURT;
            case CAMEL -> SoundEvents.CAMEL_HURT;
            case GOAT -> SoundEvents.GOAT_HURT;
            case SCREAMING_GOAT -> SoundEvents.GOAT_SCREAMING_HURT;
            case PILLAGER -> SoundEvents.PILLAGER_HURT;
            case FOX -> SoundEvents.FOX_HURT;
            case VINDICATOR -> SoundEvents.VINDICATOR_HURT;
            case PIGLIN_BRUTE -> SoundEvents.PIGLIN_BRUTE_HURT;
            case EVOKER -> SoundEvents.EVOKER_HURT;
            case WOLF -> SoundEvents.WOLF_HURT;
            case CAT -> SoundEvents.CAT_HURT;
            case OCELOT -> SoundEvents.OCELOT_HURT;
            case PIGLIN -> SoundEvents.PIGLIN_HURT;
            case WANDERING_TRADER -> SoundEvents.WANDERING_TRADER_HURT;
            case RAVAGER -> SoundEvents.RAVAGER_HURT;
            case VEX -> SoundEvents.VEX_HURT;
            case ALLAY -> SoundEvents.ALLAY_HURT;
            case PANDA -> SoundEvents.PANDA_HURT;
            case STRIDER -> SoundEvents.STRIDER_HURT;
            case PARROT -> SoundEvents.PARROT_HURT;
            case POLAR_BEAR -> SoundEvents.POLAR_BEAR_HURT;
            case TURTLE -> SoundEvents.TURTLE_HURT;
            case ARMADILLO -> SoundEvents.ARMADILLO_HURT;
            case BEE -> SoundEvents.BEE_HURT;
            case TADPOLE -> SoundEvents.TADPOLE_HURT;
            case FROG -> SoundEvents.FROG_HURT;
            case SNIFFER -> SoundEvents.SNIFFER_HURT;
            case SHULKER -> SoundEvents.SHULKER_HURT;
            case WARDEN -> SoundEvents.WARDEN_HURT;
            default -> SoundEvents.ZOMBIE_HURT;
        };
    }

    public static boolean ready(ServerPlayer player) {
        return player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_ACTION_READY);
    }

    /** the second move's cooldown (the chicken's explosive egg). */
    public static boolean ready2(ServerPlayer player) {
        return player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_ACTION2_READY);
    }

    public static void cooldown(ServerPlayer player, int ticks) {
        player.setData(WitchModAttachments.PUPPET_ACTION_READY, player.level().getGameTime() + ticks);
    }

    /** the puppet did something: every client replays its action (an animation keyed to the count). */
    public static void countAction(ServerPlayer player) {
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** the living thing under your crosshair within {@code range} (line of sight — walls block it), or null. */
    @Nullable
    public static LivingEntity lookTarget(ServerPlayer player, double range) {
        return beamHit(player, range).entity();
    }

    /**
     * the shared rally: mark {@code prey}, send every {@code kin} within range of {@code centre} after it with a
     * speed boost, and if none are there (and {@code canSummon}) raise 1-2 {@code spawnType} near it.
     */
    public static void answerCall(ServerLevel level, ServerPlayer caller, @Nullable LivingEntity prey, Vec3 centre,
                                   Class<? extends Mob> kin, EntityType<?> spawnType, boolean canSummon, boolean allowWater) {
        double r = Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        int ticks = Config.PUPPETEER_ZOMBIE_RALLY_SECONDS.get() * 20;
        if (prey != null) {
            prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
            PuppetCombat.rallied(caller, prey, ticks);
        }
        List<Mob> horde = new ArrayList<>(level.getEntitiesOfClass(kin, new net.minecraft.world.phys.AABB(centre, centre).inflate(r),
                m -> m != prey && m.isAlive() && !(m instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)));
        if (horde.isEmpty() && canSummon) {
            int max = Config.PUPPETEER_ZOMBIE_RALLY_SPAWN_MAX.get();
            int count = max <= 0 ? 0 : 1 + level.random.nextInt(max);
            for (int i = 0; i < count; i++) {
                Mob m = summonNear(level, centre, spawnType, allowWater);
                if (m != null) {
                    horde.add(m);
                }
            }
        }
        for (Mob m : horde) {
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, true));
            if (prey != null) {
                m.setTarget(prey);
                // brain-driven mobs (piglins, brutes) fight from their memories, not the target field
                if (m.getBrain().checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                        net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
                    m.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, prey);
                }
                if (m.getBrain().checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT,
                        net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
                    m.getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT, prey.getUUID(), ticks);
                }
            }
        }
    }

    /** a reinforcement a few blocks from {@code centre}, standing on solid ground (or swimming, if allowed). */
    @Nullable
    public static Mob summonNear(ServerLevel level, Vec3 centre, EntityType<?> type, boolean allowWater) {
        return summonNear(level, centre, type, allowWater, 3.0, 6.0);
    }

    /** as summonNear, between {@code near} and {@code far} blocks off (the further it is, the more tries it gets). */
    @Nullable
    public static Mob summonNear(ServerLevel level, Vec3 centre, EntityType<?> type, boolean allowWater, double near, double far) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = near + level.random.nextDouble() * Math.max(0.0, far - near);
            BlockPos base = BlockPos.containing(centre.x + Math.cos(angle) * dist, centre.y, centre.z + Math.sin(angle) * dist);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos pos = base.offset(0, dy, 0);
                boolean inWater = allowWater && level.getFluidState(pos).is(FluidTags.WATER);
                boolean onGround = level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
                if (inWater || onGround) {
                    if (!(type.create(level) instanceof Mob mob)) {
                        return null;
                    }
                    mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.REINFORCEMENT, null);
                    level.addFreshEntity(mob);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.2, mob.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
                    return mob;
                }
            }
        }
        return null;
    }

    public static void sunburn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (level.isDay() && !player.isInWaterRainOrBubble() && level.canSeeSky(head)
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.igniteForSeconds(8.0F);
        }
    }

    /** heal the puppet (capped at its max). */
    public static void scaleHeal(ServerPlayer player, float amount) {
        float max = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
        if (amount > 0.0F && health < max) {
            player.setData(WitchModAttachments.PUPPET_HEALTH, Math.min(max, health + amount));
        }
    }
}
