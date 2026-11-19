package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/** the possessable mobs. the full vanilla checklist (what's done, what's ruled out) is docs/PUPPETEER_MOBS.md. */
public enum PuppetType {
    ZOMBIE(EntityType.ZOMBIE, PuppetGroup.ZOMBIE, PuppetMove.RALLY),
    ZOMBIE_VILLAGER(EntityType.ZOMBIE_VILLAGER, PuppetGroup.ZOMBIE, PuppetMove.RALLY),
    HUSK(EntityType.HUSK, PuppetGroup.ZOMBIE, PuppetMove.RALLY),
    DROWNED(EntityType.DROWNED, PuppetGroup.ZOMBIE, PuppetMove.TRIDENT),
    CREEPER(EntityType.CREEPER, PuppetGroup.CREEPER, PuppetMove.FUSE),
    PIG(EntityType.PIG, PuppetGroup.ANIMAL, PuppetMove.OINK),
    COW(EntityType.COW, PuppetGroup.ANIMAL, PuppetMove.MOO),
    SHEEP(EntityType.SHEEP, PuppetGroup.ANIMAL, PuppetMove.GRAZE),
    CHICKEN(EntityType.CHICKEN, PuppetGroup.ANIMAL, PuppetMove.EGG),
    COD(EntityType.COD, PuppetGroup.FISH, PuppetMove.LEAP),
    SALMON(EntityType.SALMON, PuppetGroup.FISH, PuppetMove.LEAP),
    PUFFERFISH(EntityType.PUFFERFISH, PuppetGroup.FISH, PuppetMove.PUFF), // tap: leap (as cod); hold: puff up
    TROPICAL_FISH(EntityType.TROPICAL_FISH, PuppetGroup.FISH, PuppetMove.LEAP),
    SKELETON(EntityType.SKELETON, PuppetGroup.SKELETON, PuppetMove.BOW),
    STRAY(EntityType.STRAY, PuppetGroup.SKELETON, PuppetMove.BOW),
    BOGGED(EntityType.BOGGED, PuppetGroup.SKELETON, PuppetMove.BOW),
    SPIDER(EntityType.SPIDER, PuppetGroup.SPIDER, PuppetMove.POUNCE),
    CAVE_SPIDER(EntityType.CAVE_SPIDER, PuppetGroup.SPIDER, PuppetMove.POUNCE),
    BAT(EntityType.BAT, PuppetGroup.BAT, PuppetMove.FLY),
    MOOSHROOM(EntityType.MOOSHROOM, PuppetGroup.ANIMAL, PuppetMove.MOO),
    SQUID(EntityType.SQUID, PuppetGroup.SQUID, PuppetMove.FLEE),
    GLOW_SQUID(EntityType.GLOW_SQUID, PuppetGroup.SQUID, PuppetMove.FLEE),
    ZOMBIFIED_PIGLIN(EntityType.ZOMBIFIED_PIGLIN, PuppetGroup.PIGLIN, PuppetMove.CONVERT),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, PuppetGroup.SKELETON, PuppetMove.NONE),
    GUARDIAN(EntityType.GUARDIAN, PuppetGroup.GUARDIAN, PuppetMove.BEAM),
    ELDER_GUARDIAN(EntityType.ELDER_GUARDIAN, PuppetGroup.GUARDIAN, PuppetMove.BEAM),
    HOGLIN(EntityType.HOGLIN, PuppetGroup.BRUTE, PuppetMove.LUNGE),
    ZOGLIN(EntityType.ZOGLIN, PuppetGroup.BRUTE, PuppetMove.LUNGE),
    DOLPHIN(EntityType.DOLPHIN, PuppetGroup.DOLPHIN, PuppetMove.INSPIRE),
    AXOLOTL(EntityType.AXOLOTL, PuppetGroup.AXOLOTL, PuppetMove.PLAY_DEAD),
    PHANTOM(EntityType.PHANTOM, PuppetGroup.PHANTOM, PuppetMove.DIVE),
    IRON_GOLEM(EntityType.IRON_GOLEM, PuppetGroup.GOLEM, PuppetMove.OFFER),
    SNOW_GOLEM(EntityType.SNOW_GOLEM, PuppetGroup.SNOW_GOLEM, PuppetMove.VOLLEY),
    VILLAGER(EntityType.VILLAGER, PuppetGroup.VILLAGER, PuppetMove.HMM),
    RABBIT(EntityType.RABBIT, PuppetGroup.RABBIT, PuppetMove.NONE),
    /** the Caerbannog variant — same entity type as RABBIT, so it has its own id (see {@link #id}, {@link #of(Mob)}). */
    KILLER_RABBIT(EntityType.RABBIT, PuppetGroup.RABBIT, PuppetMove.MAUL),
    SILVERFISH(EntityType.SILVERFISH, PuppetGroup.SILVERFISH, PuppetMove.EMBED),
    ENDERMITE(EntityType.ENDERMITE, PuppetGroup.ENDERMITE, PuppetMove.BURROW),
    ENDERMAN(EntityType.ENDERMAN, PuppetGroup.ENDERMAN, PuppetMove.TELEPORT),
    HORSE(EntityType.HORSE, PuppetGroup.HORSE, PuppetMove.NONE),
    DONKEY(EntityType.DONKEY, PuppetGroup.HORSE, PuppetMove.NONE),
    MULE(EntityType.MULE, PuppetGroup.HORSE, PuppetMove.NONE),
    ZOMBIE_HORSE(EntityType.ZOMBIE_HORSE, PuppetGroup.HORSE, PuppetMove.NONE),
    SKELETON_HORSE(EntityType.SKELETON_HORSE, PuppetGroup.HORSE, PuppetMove.NONE),
    SLIME(EntityType.SLIME, PuppetGroup.SLIME, PuppetMove.NONE),
    MAGMA_CUBE(EntityType.MAGMA_CUBE, PuppetGroup.SLIME, PuppetMove.NONE),
    LLAMA(EntityType.LLAMA, PuppetGroup.LLAMA, PuppetMove.SPIT),
    TRADER_LLAMA(EntityType.TRADER_LLAMA, PuppetGroup.LLAMA, PuppetMove.SPIT),
    GHAST(EntityType.GHAST, PuppetGroup.GHAST, PuppetMove.FIREBALL),
    BLAZE(EntityType.BLAZE, PuppetGroup.BLAZE, PuppetMove.BLAZE_VOLLEY),
    BREEZE(EntityType.BREEZE, PuppetGroup.BREEZE, PuppetMove.BREEZE_DASH), // left: wind charge (tap) / gale (hold); right: dash
    WITCH(EntityType.WITCH, PuppetGroup.WITCH, PuppetMove.POTION),
    CAMEL(EntityType.CAMEL, PuppetGroup.CAMEL, PuppetMove.DASH),
    GOAT(EntityType.GOAT, PuppetGroup.GOAT, PuppetMove.RAM),
    /** a screaming goat — same entity as GOAT, so its own id (see {@link #id}, {@link #of(Mob)}). */
    SCREAMING_GOAT(EntityType.GOAT, PuppetGroup.GOAT, PuppetMove.RAM),
    PILLAGER(EntityType.PILLAGER, PuppetGroup.PILLAGER, PuppetMove.CROSSBOW),
    FOX(EntityType.FOX, PuppetGroup.FOX, PuppetMove.MUG),
    VINDICATOR(EntityType.VINDICATOR, PuppetGroup.VINDICATOR, PuppetMove.LUNGE_HEAVY),
    PIGLIN_BRUTE(EntityType.PIGLIN_BRUTE, PuppetGroup.VINDICATOR, PuppetMove.LUNGE_HEAVY), // the vindicator's kit; rallies piglins
    EVOKER(EntityType.EVOKER, PuppetGroup.EVOKER, PuppetMove.SPELL),
    WOLF(EntityType.WOLF, PuppetGroup.WOLF, PuppetMove.WOLF_MAUL), // swings pounce, like a real wolf; charge right-click to Maul while frenzied
    CAT(EntityType.CAT, PuppetGroup.CAT, PuppetMove.SCARED),
    WANDERING_TRADER(EntityType.WANDERING_TRADER, PuppetGroup.VILLAGER, PuppetMove.HMM), // treated like a villager (shop, hmm, hunted)
    RAVAGER(EntityType.RAVAGER, PuppetGroup.RAVAGER, PuppetMove.ROAR), // fast chaser, head-poke bite, charge-up knockback roar, tramples leaves
    VEX(EntityType.VEX, PuppetGroup.VEX, PuppetMove.GIGGLE), // temporary; flies + phases through soft blocks; left-click lunge, right-click laugh
    ALLAY(EntityType.ALLAY, PuppetGroup.ALLAY, PuppetMove.MITOSIS), // flies, can't hit; carries/drops items, takes from containers, splits to music
    OCELOT(EntityType.OCELOT, PuppetGroup.CAT, PuppetMove.BOLT), // a wild cat: meows, scares creepers, bolts away
    PIGLIN(EntityType.PIGLIN, PuppetGroup.PIGLIN, PuppetMove.PIGLIN_CALL), // sword or crossbow; rally / dance, barters, spares gold-wearers
    PANDA(EntityType.PANDA, PuppetGroup.PANDA, PuppetMove.ROLL), // left-click sneezes (and recoils), right-click rolls; no bite
    STRIDER(EntityType.STRIDER, PuppetGroup.STRIDER, PuppetMove.RIDE), // walks on lava, shivers in the cold; saddle + warped fungus stick to ride
    PARROT(EntityType.PARROT, PuppetGroup.PARROT, PuppetMove.PERCH), // slow flier; dances to music, perches on shoulders, mimics mobs, sees them all
    POLAR_BEAR(EntityType.POLAR_BEAR, PuppetGroup.POLAR_BEAR, PuppetMove.GROWL), // swipes on a cooldown, swims fast, frenzies for a hurt cub
    TURTLE(EntityType.TURTLE, PuppetGroup.TURTLE, PuppetMove.LAY_EGGS), // quick in water, slow on land; hold to lay eggs
    ARMADILLO(EntityType.ARMADILLO, PuppetGroup.ARMADILLO, PuppetMove.CURL), // hold to curl up; sheds scutes; senses threats
    BEE(EntityType.BEE, PuppetGroup.BEE, PuppetMove.POLLINATE), // slow flier; one sting; nectar, hives; rouses to its kin's anger
    TADPOLE(EntityType.TADPOLE, PuppetGroup.FISH, PuppetMove.NONE), // a fish with no move (flops, dries out)
    FROG(EntityType.FROG, PuppetGroup.FROG, PuppetMove.FROG_EAT), // big jumps, soft landings; right-click swallows the weak
    SNIFFER(EntityType.SNIFFER, PuppetGroup.SNIFFER, PuppetMove.SNIFF_DIG), // lumbering; sniffs, calls, holds to dig up seeds
    SHULKER(EntityType.SHULKER, PuppetGroup.SHULKER, PuppetMove.SHELL), // bullets; tap to teleport, hold to shut in its shell
    WARDEN(EntityType.WARDEN, PuppetGroup.WARDEN, PuppetMove.WARDEN_BELT); // blind; hunts by ear and nose; shriek / sniff / dig belt

    private final EntityType<?> entityType;
    private final PuppetGroup group;
    private final PuppetMove move;

    PuppetType(EntityType<?> entityType, PuppetGroup group, PuppetMove move) {
        this.entityType = entityType;
        this.group = group;
        this.move = move;
    }

    /** id → type and entity → type, built once (the ids need the entity registry, so lazily on first use). */
    private static volatile Map<String, PuppetType> byId;
    private static volatile Map<EntityType<?>, PuppetType> byEntity;
    @Nullable
    private String id;

    /** the puppet's id (synced as PUPPET_TYPE): the entity's registry id, except where two puppets share an entity. */
    public String id() {
        String cached = id;
        if (cached == null) {
            cached = this == KILLER_RABBIT ? WitchMod.MODID + ":killer_rabbit" : this == SCREAMING_GOAT ? WitchMod.MODID + ":screaming_goat"
                    : BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
            id = cached;
        }
        return cached;
    }

    private static void index() {
        Map<String, PuppetType> ids = new HashMap<>();
        Map<EntityType<?>, PuppetType> entities = new HashMap<>();
        for (PuppetType t : values()) {
            ids.put(t.id(), t);
            entities.putIfAbsent(t.entityType, t); // the first wins: the plain rabbit / goat, not their variants
        }
        byEntity = entities;
        byId = ids;
    }

    /** the puppet for this particular mob (the killer bunny is a rabbit variant, not its own entity). */
    @Nullable
    public static PuppetType of(Mob mob) {
        if (mob instanceof net.minecraft.world.entity.animal.Rabbit rabbit
                && rabbit.getVariant() == net.minecraft.world.entity.animal.Rabbit.Variant.EVIL) {
            return KILLER_RABBIT;
        }
        if (mob instanceof net.minecraft.world.entity.animal.goat.Goat goat && goat.isScreamingGoat()) {
            return SCREAMING_GOAT;
        }
        return of(mob.getType());
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    public PuppetGroup group() {
        return group;
    }

    public PuppetMove move() {
        return move;
    }

    /** what this puppet does (see {@link PuppetBehaviours}). */
    public PuppetBehaviour behaviour() {
        return PuppetBehaviours.of(this);
    }

    @Nullable
    public static PuppetType of(EntityType<?> type) {
        if (byEntity == null) {
            index();
        }
        return byEntity.get(type);
    }

    @Nullable
    public static PuppetType byId(String id) {
        if (byId == null) {
            index();
        }
        return id.isEmpty() ? null : byId.get(id);
    }

    public double speedMultiplier() {
        return switch (group) {
            case ZOMBIE -> Config.PUPPETEER_ZOMBIE_SPEED.get();
            case CREEPER -> Config.PUPPETEER_CREEPER_SPEED.get();
            case ANIMAL -> Config.PUPPETEER_ANIMAL_SPEED.get();
            case FISH -> 1.0; // in water it's the swim bonus; on land the land-slow applies separately
            case SKELETON -> Config.PUPPETEER_SKELETON_SPEED.get();
            case SPIDER -> Config.PUPPETEER_SPIDER_SPEED.get();
            case PIGLIN -> Config.PUPPETEER_PIGLIN_SPEED.get();
            case SQUID -> 1.0; // in water it's the (negative) swim bonus; on land the land-slow applies
            case BAT -> 1.0; // a bat flies (the disguise's slow creative-style flight)
            case GUARDIAN -> 1.0; // in water it's the swim bonus; on land the land-slow applies
            case BRUTE -> this == ZOGLIN ? Config.PUPPETEER_ZOGLIN_SPEED.get() : Config.PUPPETEER_HOGLIN_SPEED.get();
            case DOLPHIN, AXOLOTL -> 1.0; // in water it's the swim bonus (a dolphin's land-slow applies separately)
            case PHANTOM -> 1.0; // it flies (its own fly speed, client side)
            case GOLEM -> Config.PUPPETEER_GOLEM_SPEED.get();
            case SNOW_GOLEM -> Config.PUPPETEER_SNOW_GOLEM_SPEED.get();
            case VILLAGER, SILVERFISH, ENDERMITE, ENDERMAN -> 1.0;
            case SLIME -> 1.0; // it bounces (the bounce's own speed, client side)
            case LLAMA -> Config.PUPPETEER_LLAMA_SPEED.get();
            case GHAST -> 1.0; // it flies (its own slow fly speed, client side)
            case BLAZE, BREEZE -> 1.0; // walks normally; flies at its own fly speed (client side)
            case WITCH, GOAT, PILLAGER, FOX, EVOKER -> 1.0;
            case VINDICATOR -> this == PIGLIN_BRUTE ? Config.PUPPETEER_PIGLIN_BRUTE_SPEED.get()
                    : Config.PUPPETEER_VINDICATOR_SPEED.get(); // (Johnny gets more on top)
            case CAMEL -> Config.PUPPETEER_CAMEL_SPEED.get();
            case HORSE -> this == DONKEY || this == MULE ? Config.PUPPETEER_DONKEY_SPEED.get() : Config.PUPPETEER_HORSE_SPEED.get();
            case RABBIT -> 1.0; // it only hops (the hop's own speed, client side)
            case WOLF -> Config.PUPPETEER_WOLF_SPEED.get();
            case CAT -> this == OCELOT ? Config.PUPPETEER_OCELOT_SPEED.get() : Config.PUPPETEER_CAT_SPEED.get();
            case RAVAGER -> Config.PUPPETEER_RAVAGER_SPEED.get();
            case VEX, ALLAY -> 1.0; // they fly (creative-style flight)
            case PANDA -> Config.PUPPETEER_PANDA_SPEED.get();
            case STRIDER -> Config.PUPPETEER_STRIDER_SPEED.get(); // (faster on lava, slower in the cold: StriderBehaviour)
            case PARROT -> 1.0; // it flies (its own slow fly speed, client side)
            case POLAR_BEAR -> Config.PUPPETEER_POLAR_BEAR_SPEED.get();
            case TURTLE -> 1.0; // the swim bonus in water; the land slow out of it (TurtleBehaviour)
            case ARMADILLO -> Config.PUPPETEER_ARMADILLO_SPEED.get();
            case BEE -> 1.0; // it flies (its own slow fly speed, client side)
            case FROG -> Config.PUPPETEER_FROG_SPEED.get();
            case SNIFFER -> Config.PUPPETEER_SNIFFER_SPEED.get();
            case SHULKER -> 1.0; // (it doesn't walk unless configured to — PuppetMovement)
            case WARDEN -> Config.PUPPETEER_WARDEN_SPEED.get();
        };
    }

    public float attackDamage() {
        if (this == WITHER_SKELETON) {
            return Config.PUPPETEER_WITHER_SKELETON_ATTACK.get().floatValue();
        }
        return (switch (group) {
            case ZOMBIE -> Config.PUPPETEER_ZOMBIE_ATTACK.get();
            case CREEPER -> Config.PUPPETEER_CREEPER_ATTACK.get();
            case ANIMAL -> Config.PUPPETEER_ANIMAL_ATTACK.get();
            case FISH -> Config.PUPPETEER_FISH_ATTACK.get();
            case SKELETON -> Config.PUPPETEER_SKELETON_ATTACK.get();
            case SPIDER -> Config.PUPPETEER_SPIDER_ATTACK.get();
            case PIGLIN -> Config.PUPPETEER_PIGLIN_ATTACK.get();
            case SQUID -> Config.PUPPETEER_ANIMAL_ATTACK.get();
            case BAT -> Double.valueOf(0.0); // bats can't attack (blocked outright, too)
            case GUARDIAN -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // a nibble — the laser is the weapon
            case BRUTE -> this == ZOGLIN ? Config.PUPPETEER_ZOGLIN_ATTACK.get() : Config.PUPPETEER_HOGLIN_ATTACK.get();
            case DOLPHIN -> Config.PUPPETEER_DOLPHIN_ATTACK.get();
            case AXOLOTL -> Config.PUPPETEER_AXOLOTL_ATTACK.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_BITE_DAMAGE.get(); // its bite, on left-click
            case GOLEM -> Config.PUPPETEER_GOLEM_ATTACK.get();
            case SNOW_GOLEM -> Config.PUPPETEER_SNOW_GOLEM_ATTACK.get();
            case VILLAGER -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // a villager's slap
            case RABBIT -> this == KILLER_RABBIT ? Config.PUPPETEER_KILLER_RABBIT_ATTACK.get() : Config.PUPPETEER_ANIMAL_ATTACK.get();
            case SILVERFISH -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // vanilla silverfish: 1
            case ENDERMITE -> Config.PUPPETEER_SKELETON_ATTACK.get(); // vanilla endermite: 2
            case ENDERMAN -> Config.PUPPETEER_ENDERMAN_ATTACK.get();
            case HORSE -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // a basic animal kick
            case SLIME -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // (really size-based — see slimeDamage)
            case LLAMA, GHAST -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // a llama's kick; a ghast's nudge
            case BLAZE -> Config.PUPPETEER_BLAZE_ATTACK.get();
            case BREEZE -> Double.valueOf(0.0); // no melee at all (blocked outright, too)
            case WITCH, CAMEL, GOAT -> Config.PUPPETEER_ANIMAL_ATTACK.get(); // (the witch and the screaming goat's left-click is their own)
            case PILLAGER -> Double.valueOf(0.0); // no melee (blocked outright, too)
            case FOX -> Config.PUPPETEER_SKELETON_ATTACK.get(); // a fox's bite: 2
            case VINDICATOR -> this == PIGLIN_BRUTE ? Config.PUPPETEER_PIGLIN_BRUTE_ATTACK.get() : Config.PUPPETEER_VINDICATOR_ATTACK.get();
            case EVOKER -> Double.valueOf(0.0); // no melee — its left-click is its spells
            case WOLF -> Config.PUPPETEER_WOLF_ATTACK.get();
            case CAT -> Double.valueOf(0.0); // no bite — its left-click is a meow
            case PANDA -> Double.valueOf(0.0); // no bite — its left-click is a sneeze
            case STRIDER -> Double.valueOf(0.0); // a strider never attacks
            case PARROT, TURTLE -> Double.valueOf(0.0); // no bite
            case POLAR_BEAR -> Config.PUPPETEER_POLAR_BEAR_ATTACK.get();
            case ARMADILLO, FROG -> Double.valueOf(0.0); // no bite (a frog eats instead)
            case BEE -> Config.PUPPETEER_BEE_ATTACK.get(); // its sting
            case SNIFFER, SHULKER -> Double.valueOf(0.0); // no bite (a shulker's weapon is its bullets)
            case WARDEN -> Config.PUPPETEER_WARDEN_ATTACK.get();
            case RAVAGER -> Config.PUPPETEER_RAVAGER_ATTACK.get();
            case VEX -> Config.PUPPETEER_VEX_ATTACK.get(); // its lunge, applied specially
            case ALLAY -> Double.valueOf(0.0); // can't hit anything
        }).floatValue();
    }

    public double reach() {
        if (this == WITHER_SKELETON) {
            return Config.PUPPETEER_WITHER_SKELETON_REACH.get();
        }
        return switch (group) {
            case ZOMBIE -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case CREEPER -> Config.PUPPETEER_CREEPER_REACH.get();
            case ANIMAL, FISH -> Config.PUPPETEER_ANIMAL_REACH.get();
            case SKELETON -> Config.PUPPETEER_SKELETON_REACH.get();
            case SPIDER -> Config.PUPPETEER_SPIDER_REACH.get();
            case PIGLIN -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case SQUID -> Config.PUPPETEER_ANIMAL_REACH.get();
            case BAT, GUARDIAN, DOLPHIN, AXOLOTL -> Config.PUPPETEER_ANIMAL_REACH.get();
            case PHANTOM -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case GOLEM -> Config.PUPPETEER_GOLEM_REACH.get();
            case SNOW_GOLEM, VILLAGER -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case RABBIT, SILVERFISH, ENDERMITE, HORSE, SLIME, LLAMA, BREEZE -> Config.PUPPETEER_ANIMAL_REACH.get();
            case BLAZE -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case WITCH -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case CAMEL, GOAT, FOX -> Config.PUPPETEER_ANIMAL_REACH.get();
            case PILLAGER, VINDICATOR, EVOKER -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case GHAST -> Config.PUPPETEER_GOLEM_REACH.get(); // it's huge
            case ENDERMAN -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case BRUTE -> Config.PUPPETEER_HOGLIN_REACH.get();
            case WOLF, CAT -> Config.PUPPETEER_ANIMAL_REACH.get();
            case RAVAGER -> Config.PUPPETEER_RAVAGER_REACH.get();
            case VEX, ALLAY -> Config.PUPPETEER_ANIMAL_REACH.get();
            case PANDA, STRIDER, PARROT, TURTLE -> Config.PUPPETEER_ANIMAL_REACH.get();
            case POLAR_BEAR -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case ARMADILLO, BEE, FROG -> Config.PUPPETEER_ANIMAL_REACH.get();
            case SNIFFER, SHULKER -> Config.PUPPETEER_ZOMBIE_REACH.get();
            case WARDEN -> Config.PUPPETEER_WARDEN_REACH.get();
        };
    }

    public boolean burnsInSun() {
        return this == ZOMBIE || this == ZOMBIE_VILLAGER || this == DROWNED || this == PHANTOM
                || (group == PuppetGroup.SKELETON && this != WITHER_SKELETON);
    }

    /** the food that draws this animal in (and so drags its puppet towards whoever holds it). */
    @Nullable
    public TagKey<Item> lureFood() {
        return switch (this) {
            case PIG -> ItemTags.PIG_FOOD;
            case COW, MOOSHROOM -> ItemTags.COW_FOOD;
            case SHEEP -> ItemTags.SHEEP_FOOD;
            case CHICKEN -> ItemTags.CHICKEN_FOOD;
            case RABBIT -> ItemTags.RABBIT_FOOD; // (the killer bunny doesn't care for carrots)
            case STRIDER -> ItemTags.STRIDER_TEMPT_ITEMS;
            case PANDA -> ItemTags.PANDA_FOOD;
            case ARMADILLO -> ItemTags.ARMADILLO_FOOD;
            case BEE -> ItemTags.BEE_FOOD;
            case TADPOLE, FROG -> ItemTags.FROG_FOOD;
            case SNIFFER -> ItemTags.SNIFFER_FOOD;
            default -> null;
        };
    }
}
