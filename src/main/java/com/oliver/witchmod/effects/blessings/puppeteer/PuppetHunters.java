package com.oliver.witchmod.effects.blessings.puppeteer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;

/** puppeteer: which mobs hunt which puppets (iron golems after a zombie puppet, zombies after a villager one...). */
public final class PuppetHunters {
    private PuppetHunters() {}

    // --- hunted puppets ----------------------------------------------------------------------------

    /**
     * the monsters that go after this puppet's mob in vanilla, and so go after the puppet (the usual "monsters
     * ignore puppets" rule doesn't apply to them): golems are hated by zombies, skeletons, spiders, illagers and
     * ravagers; villagers are hunted by zombies, illagers and ravagers.
     */
    /** a hostile-monster puppet (what a village iron golem would attack: any MONSTER except a creeper). */
    private static boolean isHostilePuppet(@Nullable PuppetType type) {
        return type != null && type != PuppetType.CREEPER
                && type.entityType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
    }

    /**
     * whether {@code mob} would hunt this puppet's mob TYPE in vanilla — so the puppet is treated by the faction
     * system as the mob it is (a stray puppet is attacked by golems; a sheep puppet is left alone; ...).
     */
    public static boolean hunts(Mob mob, @Nullable PuppetType type) {
        if (type == null) {
            return false;
        }
        boolean zombie = mob instanceof Zombie && !(mob instanceof net.minecraft.world.entity.monster.ZombifiedPiglin);
        boolean raider = mob instanceof net.minecraft.world.entity.monster.AbstractIllager
                || mob instanceof net.minecraft.world.entity.monster.Ravager;
        boolean golem = mob instanceof net.minecraft.world.entity.animal.IronGolem
                || mob instanceof net.minecraft.world.entity.animal.SnowGolem;
        boolean wildWolf = mob instanceof net.minecraft.world.entity.animal.Wolf w && !w.isTame();
        boolean fox = mob instanceof net.minecraft.world.entity.animal.Fox;
        boolean polarBear = mob instanceof net.minecraft.world.entity.animal.PolarBear bear && !bear.isBaby();
        boolean axolotl = mob instanceof net.minecraft.world.entity.animal.axolotl.Axolotl;
        // villagers & wandering traders: hunted by zombies and raiders.
        if (type == PuppetType.VILLAGER || type == PuppetType.WANDERING_TRADER) {
            return zombie || raider;
        }
        // an iron-golem puppet: hated by zombies, raiders, skeletons and spiders.
        if (type == PuppetType.IRON_GOLEM) {
            return zombie || raider || mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                    || mob instanceof net.minecraft.world.entity.monster.Spider;
        }
        // a hostile puppet: village defenders (iron/snow golems) go after it, and wild wolves hunt skeletons.
        if (isHostilePuppet(type)) {
            return golem || (type.group() == PuppetGroup.SKELETON && wildWolf);
        }
        // prey: wild wolves hunt sheep & rabbits; foxes hunt chickens, rabbits and fish.
        if (type == PuppetType.SHEEP || type == PuppetType.RABBIT) {
            return wildWolf || (type == PuppetType.RABBIT && fox);
        }
        // grown polar bears hunt foxes
        if (type == PuppetType.FOX && polarBear) {
            return true;
        }
        // axolotls hunt fish (and tadpoles)
        if (type.group() == PuppetGroup.FISH && axolotl) {
            return true;
        }
        if (type == PuppetType.CHICKEN || type.group() == PuppetGroup.FISH) {
            return fox;
        }
        return false;
    }

    /** the hunters around you pick you out, as their target goals would pick out the real mob (line of sight, 16 blocks). */
    public static void drawHunters(ServerPlayer player, PuppetType type) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0),
                m -> m.isAlive() && (m.getTarget() == null || !m.getTarget().isAlive()) && hunts(m, type)
                        && m.getSensing().hasLineOfSight(player))) {
            mob.setTarget(player);
        }
    }
}
