package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * puppeteer: the young still follow their parent when it's a puppet, as vanilla's FollowParentGoal has them follow the
 * nearest grown one of their kind (unless a real one's nearer) — and a school of fish follows a fish puppet as its
 * leader. goal-driven mobs are walked over; brain-driven ones (goats, camels, sniffers...) get it as a walk target.
 */
public final class PuppetFollowers {
    private PuppetFollowers() {}

    /** which puppets' mobs are animals with young / schooling fish (asked of a throwaway instance, once each). */
    private static final Map<PuppetType, Integer> KIND = new EnumMap<>(PuppetType.class);
    private static final int NONE = 0;
    private static final int PARENT = 1;
    private static final int SCHOOL = 2;

    private static int kind(ServerLevel level, PuppetType type) {
        return KIND.computeIfAbsent(type, t -> {
            Entity probe = t.entityType().create(level);
            int k = probe instanceof AbstractSchoolingFish ? SCHOOL : probe instanceof Animal ? PARENT : NONE;
            if (probe != null) {
                probe.discard();
            }
            return k;
        });
    }

    /** every half second, per puppet. */
    public static void tick(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        int kind = kind(level, type);
        if (kind == NONE || player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0) {
            return; // (a baby has no young of its own)
        }
        double r = Config.PUPPETEER_FOLLOW_RADIUS.get();
        List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r, r * 0.5, r),
                m -> m.isAlive() && m.getType() == type.entityType() && !m.isNoAi());
        for (Mob follower : near) {
            if (kind == PARENT && !follower.isBaby()) {
                continue;
            }
            if (kind == SCHOOL && follower instanceof AbstractSchoolingFish fish && fish.isFollower()) {
                continue; // it already has a leader
            }
            double toYou = follower.distanceToSqr(player);
            if (toYou < 9.0) {
                continue; // close enough (vanilla stops at 3 blocks)
            }
            // a real grown one nearer: it follows that instead, as in vanilla
            boolean nearerAdult = kind == PARENT && near.stream().anyMatch(m -> !m.isBaby() && m.distanceToSqr(follower) < toYou);
            if (nearerAdult) {
                continue;
            }
            if (follower.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
                follower.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 1.1F, 2));
            } else {
                follower.getNavigation().moveTo(player, 1.1);
            }
        }
    }
}
