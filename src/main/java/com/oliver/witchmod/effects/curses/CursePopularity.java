package com.oliver.witchmod.effects.curses;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.MobSpawnSettings;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * You're a bit too well-liked (master-spec Popularity). Hostiles keep spawning around you and every hostile
 * that spots you commits to the chase — the "whole server chasing one guy" TikTok, made real.
 *
 * <p>Two halves, both on a tick:
 * <ul>
 *   <li><b>Conjured horde</b> — extra hostiles are spawned in a ring around you using VANILLA spawn rules:
 *       the mob type comes from the local biome's monster list (so drowned appear in water, husks in the
 *       desert, ...) and both the ground-fit and the light/daylight checks are vanilla's own, so shade and
 *       daytime still keep you safe. Spawned mobs are tagged and capped.</li>
 *   <li><b>Dedicated hunters</b> — every nearby hostile is set up once as a committed hunter: a big follow
 *       range, PROLONGED tracking after losing line of sight (but it still has to see you to lock on — no
 *       x-ray), and — for zombies — the ability to break doors on any difficulty. If it can currently see
 *       you it's re-aimed at you, so you stay its priority.</li>
 * </ul>
 */
public final class CursePopularity extends Effect {
    /** Scoreboard tag marking a curse-spawned mob (counted toward the cap and the discovery horde size). */
    private static final String TAG_SPAWNED = "witchmod_popularity";
    /** Scoreboard tag marking a hostile we've already turned into a dedicated hunter, so we do it once. */
    private static final String TAG_HUNTER = "witchmod_popularity_hunter";

    private static final ResourceLocation FOLLOW_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "popularity_follow_range");

    public CursePopularity() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 32, () -> Items.BELL);
    }

    /** Discovered once the crowd is unmistakable (see the horde-size check), not on the first spawn. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();

        if (now % Config.POPULARITY_RETARGET_INTERVAL_TICKS.get() == 0) {
            enlistHunters(target, level);
        }
        if (now % Config.POPULARITY_SPAWN_INTERVAL_TICKS.get() == 0) {
            spawnHorde(target, level);
        }
    }

    // --- Dedicated hunters -----------------------------------------------------------------------------

    /** Turns nearby hostiles into committed hunters and re-aims the ones that can see the victim. */
    private void enlistHunters(ServerPlayer target, ServerLevel level) {
        double r = Config.POPULARITY_DETECTION_RADIUS.get();
        // NeutralMobs (endermen, zombified piglins, ...) are deliberately EXCLUDED — force-aggroing them is
        // what a different curse (Neutral Aggression) does, and doing it here would make that one pointless.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(r),
                m -> m instanceof Enemy && !(m instanceof NeutralMob) && m.isAlive())) {
            configureHunter(mob, target);
            // "Always prioritise the player" — but only when it can actually SEE you (no through-wall x-ray).
            if (mob.hasLineOfSight(target) && mob.getTarget() != target) {
                mob.setTarget(target);
            }
        }
    }

    /** One-time setup per hostile: follow range, prolonged unseen memory, and door-breaking for zombies. */
    private static void configureHunter(Mob mob, ServerPlayer target) {
        if (!mob.addTag(TAG_HUNTER)) {
            return; // addTag returns false if it was already present — already configured
        }

        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && !followRange.hasModifier(FOLLOW_RANGE_ID)) {
            double bump = Config.POPULARITY_DETECTION_RADIUS.get() - followRange.getBaseValue();
            if (bump > 0) {
                followRange.addOrUpdateTransientModifier(new AttributeModifier(FOLLOW_RANGE_ID, bump,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // A high-priority target goal that requires sight to LOCK ON (mustSee=true) but then remembers you
        // for a prolonged time after you break line of sight — dogged tracking, not wall-hacks.
        NearestAttackableTargetGoal<Player> hunt = new NearestAttackableTargetGoal<>(mob, Player.class, true);
        hunt.setUnseenMemoryTicks(Config.POPULARITY_PROLONGED_TRACKING_TICKS.get());
        mob.targetSelector.addGoal(1, hunt);

        // Zombies (and their kin — husk/zombie villager extend Zombie) smash doors on ANY difficulty: a
        // custom BreakDoorGoal with an always-true predicate, bypassing vanilla's hard-only gate.
        // BreakDoorGoal's constructor REQUIRES ground-path navigation and throws otherwise — drowned use
        // water navigation, so they're correctly excluded by this guard (they'd never path to a door anyway).
        if (mob instanceof Zombie && mob.getNavigation() instanceof GroundPathNavigation) {
            mob.goalSelector.addGoal(1, new BreakDoorGoal(mob, difficulty -> true));
        }
    }

    // --- Conjured horde (vanilla spawn rules) ----------------------------------------------------------

    private void spawnHorde(ServerPlayer target, ServerLevel level) {
        double detect = Config.POPULARITY_DETECTION_RADIUS.get();
        long living = countSpawned(target, level, detect);
        if (living >= Config.POPULARITY_MAX_MOBS.get()) {
            return;
        }
        for (int i = 0; i < Config.POPULARITY_SPAWN_ATTEMPTS.get()
                && living < Config.POPULARITY_MAX_MOBS.get(); i++) {
            if (trySpawnOne(target, level)) {
                living++;
            }
        }
        // Discovered only once a proper crowd has gathered (Rule 2, per Oliver).
        if (living >= Config.POPULARITY_DISCOVERY_HORDE_SIZE.get()) {
            markDiscoveredByVictim(target);
        }
    }

    private static long countSpawned(ServerPlayer target, ServerLevel level, double radius) {
        return level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(radius),
                m -> m.isAlive() && m.getTags().contains(TAG_SPAWNED)).size();
    }

    private boolean trySpawnOne(ServerPlayer target, ServerLevel level) {
        double min = Config.POPULARITY_SPAWN_RADIUS_MIN.get();
        double max = Config.POPULARITY_SPAWN_RADIUS_MAX.get();
        double angle = target.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = min + target.getRandom().nextDouble() * (max - min);
        int x = (int) Math.floor(target.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(target.getZ() + Math.sin(angle) * dist);
        int startY = target.blockPosition().getY();

        // Find a candidate spot near the victim's height: either standing WATER (→ a water hostile) or a
        // standable ground spot (→ a biome-appropriate land hostile). Water is checked first so a submerged
        // victim reliably gets drowned regardless of which biome the pond happens to be in.
        for (int dy = 4; dy >= -8; dy--) {
            BlockPos pos = new BlockPos(x, startY + dy, z);
            boolean water = level.getFluidState(pos).is(FluidTags.WATER)
                    && level.getFluidState(pos.above()).is(FluidTags.WATER);
            boolean ground = level.getBlockState(pos.below()).blocksMotion()
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
            if (!water && !ground) {
                continue;
            }

            // Light is the safety gate (daylight + torches keep you clear), matching vanilla's dark threshold.
            if (level.getMaxLocalRawBrightness(pos) > Config.POPULARITY_MAX_SPAWN_LIGHT.get()) {
                return false;
            }

            EntityType<?> type = water ? EntityType.DROWNED : pickLandMob(target, level, pos);
            if (type == null) {
                return false;
            }
            return spawnAt(target, level, pos, type);
        }
        return false;
    }

    /** A biome-appropriate land hostile (husk in desert, stray in snow, ...), skipping non-Mob picks. */
    private static EntityType<?> pickLandMob(ServerPlayer target, ServerLevel level, BlockPos pos) {
        var mobs = level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER);
        if (mobs.isEmpty()) {
            return null;
        }
        Optional<MobSpawnSettings.SpawnerData> pick = mobs.getRandom(target.getRandom());
        return pick.map(d -> d.type).orElse(null);
    }

    /** Creates, places and enlists one conjured hostile; refuses neutral mobs and bad placements. */
    private boolean spawnAt(ServerPlayer target, ServerLevel level, BlockPos pos, EntityType<?> type) {
        SpawnPlacementType placement = SpawnPlacements.getPlacementType(type);
        if (!placement.isSpawnPositionOk(level, pos, type)) {
            return false; // wrong footing for this mob (e.g. a ground mob at a water spot)
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob mob) || mob instanceof NeutralMob) {
            // NeutralMobs (endermen, zombified piglins) are never conjured — they wouldn't chase you anyway,
            // and force-aggroing them is Neutral Aggression's whole job.
            if (created != null) {
                created.discard();
            }
            return false;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                target.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) {
            mob.discard();
            return false;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        mob.addTag(TAG_SPAWNED);
        configureHunter(mob, target);
        mob.setTarget(target);
        level.addFreshEntity(mob);
        return true;
    }

    // No onRemove teardown: conjured mobs aren't persistence-locked, so the crowd despawns naturally after
    // the curse ends. The hunter goals stay on whatever mobs are still alive until they too despawn.
}
