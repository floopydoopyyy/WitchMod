package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.entities.ExplosiveEggEntity;
import com.oliver.witchmod.synergy.Synergies;

/**
 * puppeteer: crouch + right-click a possessable mob to climb inside it. a short possession plays out (the mob
 * freezes, your soul streams in, your body is drawn into its spot), then you ARE it: its look, health, speed,
 * reach and damage, and right-click does its special move (shown bottom-right by the hud). crouch + right-click
 * again to step out. the real mob is saved and removed while you wear it and comes back where you stand, with
 * whatever health it has left. your inventory is stashed for the duration, so nothing can be used.
 *
 * <p>your own blessings and curses carry into the puppet: damage taken / dealt, healing and max health all
 * flow through it (a tank-blessed zombie is a raid boss). body-swapping ones (giant, dwarfism, gluttony's
 * size) are suspended while you're inside — see {@link #isPuppet}.
 *
 * <p>puppets: the zombie family (zombie, zombie villager, husk, drowned — babies too, half size and faster),
 * the creeper, the farm animals (pig, cow, sheep, chicken) and the fish (cod, salmon, pufferfish, tropical
 * fish). add one via {@link PuppetType}; docs/PUPPETEER_MOBS.md tracks the rest of the vanilla roster.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class BlessingPuppeteer extends Effect {
    /** what each puppet does with right-click. held moves charge while right-click is held, firing on release. */
    public enum Move {
        RALLY(false), TRIDENT(true), FUSE(true), OINK(false), MOO(false), GRAZE(false), EGG(true), LEAP(false), BOW(true),
        POUNCE(false), FLY(false), FLEE(false), CONVERT(false), BEAM(true), NONE(false), LUNGE(false), INSPIRE(false), BURROW(false), TELEPORT(false), SPIT(false), FIREBALL(false), BLAZE_VOLLEY(true), GALE(true), POTION(true), DASH(true), RAM(true), CROSSBOW(true), MUG(false), SPELL(false),
        PLAY_DEAD(true), DIVE(false), OFFER(true), VOLLEY(true), HMM(false), MAUL(false), EMBED(false), LUNGE_HEAVY(true), SCARED(false), ROAR(true),
        GIGGLE(false), MITOSIS(true);

        private final boolean held;

        Move(boolean held) {
            this.held = held;
        }

        public boolean held() {
            return held;
        }

        /** driven by vanilla item use (the skeleton's real bow) rather than the mod's own hold reporting. */
        public boolean vanillaUse() {
            return this == BOW || this == CROSSBOW;
        }

        public String labelKey() {
            return "witchmod.puppeteer.action." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Group { ZOMBIE, SKELETON, SPIDER, PIGLIN, CREEPER, ANIMAL, FISH, SQUID, BAT, GUARDIAN, BRUTE, DOLPHIN, AXOLOTL, PHANTOM, GOLEM, SNOW_GOLEM, VILLAGER, RABBIT, SILVERFISH, ENDERMITE, ENDERMAN, HORSE, SLIME, LLAMA, GHAST, BLAZE, BREEZE, WITCH, CAMEL, GOAT, PILLAGER, FOX, VINDICATOR, EVOKER, WOLF, CAT, RAVAGER, VEX, ALLAY }

    /** the possessable mobs. the full vanilla checklist (what's done, what's ruled out) is docs/PUPPETEER_MOBS.md. */
    public enum PuppetType {
        ZOMBIE(EntityType.ZOMBIE, Group.ZOMBIE, Move.RALLY),
        ZOMBIE_VILLAGER(EntityType.ZOMBIE_VILLAGER, Group.ZOMBIE, Move.RALLY),
        HUSK(EntityType.HUSK, Group.ZOMBIE, Move.RALLY),
        DROWNED(EntityType.DROWNED, Group.ZOMBIE, Move.TRIDENT),
        CREEPER(EntityType.CREEPER, Group.CREEPER, Move.FUSE),
        PIG(EntityType.PIG, Group.ANIMAL, Move.OINK),
        COW(EntityType.COW, Group.ANIMAL, Move.MOO),
        SHEEP(EntityType.SHEEP, Group.ANIMAL, Move.GRAZE),
        CHICKEN(EntityType.CHICKEN, Group.ANIMAL, Move.EGG),
        COD(EntityType.COD, Group.FISH, Move.LEAP),
        SALMON(EntityType.SALMON, Group.FISH, Move.LEAP),
        PUFFERFISH(EntityType.PUFFERFISH, Group.FISH, Move.LEAP),
        TROPICAL_FISH(EntityType.TROPICAL_FISH, Group.FISH, Move.LEAP),
        SKELETON(EntityType.SKELETON, Group.SKELETON, Move.BOW),
        STRAY(EntityType.STRAY, Group.SKELETON, Move.BOW),
        BOGGED(EntityType.BOGGED, Group.SKELETON, Move.BOW),
        SPIDER(EntityType.SPIDER, Group.SPIDER, Move.POUNCE),
        CAVE_SPIDER(EntityType.CAVE_SPIDER, Group.SPIDER, Move.POUNCE),
        BAT(EntityType.BAT, Group.BAT, Move.FLY),
        MOOSHROOM(EntityType.MOOSHROOM, Group.ANIMAL, Move.MOO),
        SQUID(EntityType.SQUID, Group.SQUID, Move.FLEE),
        GLOW_SQUID(EntityType.GLOW_SQUID, Group.SQUID, Move.FLEE),
        ZOMBIFIED_PIGLIN(EntityType.ZOMBIFIED_PIGLIN, Group.PIGLIN, Move.CONVERT),
        WITHER_SKELETON(EntityType.WITHER_SKELETON, Group.SKELETON, Move.NONE),
        GUARDIAN(EntityType.GUARDIAN, Group.GUARDIAN, Move.BEAM),
        ELDER_GUARDIAN(EntityType.ELDER_GUARDIAN, Group.GUARDIAN, Move.BEAM),
        HOGLIN(EntityType.HOGLIN, Group.BRUTE, Move.LUNGE),
        ZOGLIN(EntityType.ZOGLIN, Group.BRUTE, Move.LUNGE),
        DOLPHIN(EntityType.DOLPHIN, Group.DOLPHIN, Move.INSPIRE),
        AXOLOTL(EntityType.AXOLOTL, Group.AXOLOTL, Move.PLAY_DEAD),
        PHANTOM(EntityType.PHANTOM, Group.PHANTOM, Move.DIVE),
        IRON_GOLEM(EntityType.IRON_GOLEM, Group.GOLEM, Move.OFFER),
        SNOW_GOLEM(EntityType.SNOW_GOLEM, Group.SNOW_GOLEM, Move.VOLLEY),
        VILLAGER(EntityType.VILLAGER, Group.VILLAGER, Move.HMM),
        RABBIT(EntityType.RABBIT, Group.RABBIT, Move.NONE),
        /** the Caerbannog variant — same entity type as RABBIT, so it has its own id (see {@link #id}, {@link #of(Mob)}). */
        KILLER_RABBIT(EntityType.RABBIT, Group.RABBIT, Move.MAUL),
        SILVERFISH(EntityType.SILVERFISH, Group.SILVERFISH, Move.EMBED),
        ENDERMITE(EntityType.ENDERMITE, Group.ENDERMITE, Move.BURROW),
        ENDERMAN(EntityType.ENDERMAN, Group.ENDERMAN, Move.TELEPORT),
        HORSE(EntityType.HORSE, Group.HORSE, Move.NONE),
        DONKEY(EntityType.DONKEY, Group.HORSE, Move.NONE),
        MULE(EntityType.MULE, Group.HORSE, Move.NONE),
        ZOMBIE_HORSE(EntityType.ZOMBIE_HORSE, Group.HORSE, Move.NONE),
        SKELETON_HORSE(EntityType.SKELETON_HORSE, Group.HORSE, Move.NONE),
        SLIME(EntityType.SLIME, Group.SLIME, Move.NONE),
        MAGMA_CUBE(EntityType.MAGMA_CUBE, Group.SLIME, Move.NONE),
        LLAMA(EntityType.LLAMA, Group.LLAMA, Move.SPIT),
        TRADER_LLAMA(EntityType.TRADER_LLAMA, Group.LLAMA, Move.SPIT),
        GHAST(EntityType.GHAST, Group.GHAST, Move.FIREBALL),
        BLAZE(EntityType.BLAZE, Group.BLAZE, Move.BLAZE_VOLLEY),
        BREEZE(EntityType.BREEZE, Group.BREEZE, Move.GALE),
        WITCH(EntityType.WITCH, Group.WITCH, Move.POTION),
        CAMEL(EntityType.CAMEL, Group.CAMEL, Move.DASH),
        GOAT(EntityType.GOAT, Group.GOAT, Move.RAM),
        /** a screaming goat — same entity as GOAT, so its own id (see {@link #id}, {@link #of(Mob)}). */
        SCREAMING_GOAT(EntityType.GOAT, Group.GOAT, Move.RAM),
        PILLAGER(EntityType.PILLAGER, Group.PILLAGER, Move.CROSSBOW),
        FOX(EntityType.FOX, Group.FOX, Move.MUG),
        VINDICATOR(EntityType.VINDICATOR, Group.VINDICATOR, Move.LUNGE_HEAVY),
        EVOKER(EntityType.EVOKER, Group.EVOKER, Move.SPELL),
        WOLF(EntityType.WOLF, Group.WOLF, Move.POUNCE), // swings pounce, like a real wolf; a right-click Maul appears while frenzied
        CAT(EntityType.CAT, Group.CAT, Move.SCARED),
        WANDERING_TRADER(EntityType.WANDERING_TRADER, Group.VILLAGER, Move.HMM), // treated like a villager (shop, hmm, hunted)
        RAVAGER(EntityType.RAVAGER, Group.RAVAGER, Move.ROAR), // fast chaser, head-poke bite, charge-up knockback roar, tramples leaves
        VEX(EntityType.VEX, Group.VEX, Move.GIGGLE), // temporary; flies + phases through soft blocks; left-click lunge, right-click laugh
        ALLAY(EntityType.ALLAY, Group.ALLAY, Move.MITOSIS); // flies, can't hit; carries/drops items, takes from containers, splits to music

        private final EntityType<?> entityType;
        private final Group group;
        private final Move move;

        PuppetType(EntityType<?> entityType, Group group, Move move) {
            this.entityType = entityType;
            this.group = group;
            this.move = move;
        }

        /** the puppet's id (synced as PUPPET_TYPE): the entity's registry id, except where two puppets share an entity. */
        public String id() {
            return this == KILLER_RABBIT ? WitchMod.MODID + ":killer_rabbit" : this == SCREAMING_GOAT ? WitchMod.MODID + ":screaming_goat"
                    : BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
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

        public Group group() {
            return group;
        }

        public Move move() {
            return move;
        }

        @Nullable
        public static PuppetType of(EntityType<?> type) {
            for (PuppetType t : values()) {
                if (t.entityType == type) {
                    return t;
                }
            }
            return null;
        }

        @Nullable
        public static PuppetType byId(String id) {
            for (PuppetType t : values()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            return null;
        }

        double speedMultiplier() {
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
                case VINDICATOR -> Config.PUPPETEER_VINDICATOR_SPEED.get(); // (Johnny gets more on top)
                case CAMEL -> Config.PUPPETEER_CAMEL_SPEED.get();
                case HORSE -> this == DONKEY || this == MULE ? Config.PUPPETEER_DONKEY_SPEED.get() : Config.PUPPETEER_HORSE_SPEED.get();
                case RABBIT -> 1.0; // it only hops (the hop's own speed, client side)
                case WOLF -> Config.PUPPETEER_WOLF_SPEED.get();
                case CAT -> Config.PUPPETEER_CAT_SPEED.get();
                case RAVAGER -> Config.PUPPETEER_RAVAGER_SPEED.get();
                case VEX, ALLAY -> 1.0; // they fly (creative-style flight)
            };
        }

        float attackDamage() {
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
                case VINDICATOR -> Config.PUPPETEER_VINDICATOR_ATTACK.get();
                case EVOKER -> Double.valueOf(0.0); // no melee — its left-click is its spells
                case WOLF -> Config.PUPPETEER_WOLF_ATTACK.get();
                case CAT -> Double.valueOf(0.0); // no bite — its left-click is a meow
                case RAVAGER -> Config.PUPPETEER_RAVAGER_ATTACK.get();
                case VEX -> Config.PUPPETEER_VEX_ATTACK.get(); // its lunge, applied specially
                case ALLAY -> Double.valueOf(0.0); // can't hit anything
            }).floatValue();
        }

        double reach() {
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
            };
        }

        boolean burnsInSun() {
            return this == ZOMBIE || this == ZOMBIE_VILLAGER || this == DROWNED || this == PHANTOM
                    || (group == Group.SKELETON && this != WITHER_SKELETON);
        }

        /** the food that draws this animal in (and so drags its puppet towards whoever holds it). */
        @Nullable
        TagKey<Item> lureFood() {
            return switch (this) {
                case PIG -> ItemTags.PIG_FOOD;
                case COW, MOOSHROOM -> ItemTags.COW_FOOD;
                case SHEEP -> ItemTags.SHEEP_FOOD;
                case CHICKEN -> ItemTags.CHICKEN_FOOD;
                case RABBIT -> ItemTags.RABBIT_FOOD; // (the killer bunny doesn't care for carrots)
                default -> null;
            };
        }
    }

    private static final ResourceLocation SPEED_ID = EffectUtil.modifierId("puppeteer_speed");
    private static final ResourceLocation REACH_ID = EffectUtil.modifierId("puppeteer_reach");
    private static final ResourceLocation FUSE_SLOW_ID = EffectUtil.modifierId("puppeteer_fuse_slow");
    private static final ResourceLocation SWIM_ID = EffectUtil.modifierId("puppeteer_swim");
    private static final ResourceLocation BABY_SCALE_ID = EffectUtil.modifierId("puppeteer_baby_scale");
    private static final ResourceLocation BABY_SPEED_ID = EffectUtil.modifierId("puppeteer_baby_speed");
    private static final ResourceLocation FISH_LAND_ID = EffectUtil.modifierId("puppeteer_fish_land");
    /** a fish puppet's breath out of water (its own counter — vanilla refills a player's air on land). */
    private static final Map<UUID, Integer> FISH_AIR = new ConcurrentHashMap<>();
    /** spiders that have used their one mid-jump pounce (cleared when they land). */
    private static final Set<UUID> AIR_POUNCED = ConcurrentHashMap.newKeySet();
    /** ticks of "invulnerability" after the puppet takes a hit, like vanilla's, so rapid sources don't shred it. */
    private static final int PUPPET_IFRAMES = 10;
    /** a held move needs at least this many ticks of charge to fire (a trident's minimum). */
    private static final int MIN_CHARGE = 10;
    /** blessing yellow — the possession's sparks are souls (blue) plus the blessing palette (yellow + white). */
    private static final DustParticleOptions BLESSED_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 1.2F);

    /** a possession in progress: the frozen mob, where the player started, and the mob's own flags to restore. */
    private record Binding(UUID mobId, Vec3 from, long start, long end, boolean mobNoAi, boolean mobInvulnerable) {}

    private static final Map<UUID, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PUPPET_HIT = new ConcurrentHashMap<>();
    /** the mob's own max health, before your blessings/curses scale it. */
    private static final Map<UUID, Float> BASE_MAX = new ConcurrentHashMap<>();
    private static final Set<UUID> HELD = ConcurrentHashMap.newKeySet();
    /** players mid-detonation — their own blast doesn't hurt them (the creeper is spent instead). */
    private static final Set<UUID> EXPLODING = ConcurrentHashMap.newKeySet();
    /** live drowned-puppet tridents → their thrower; they vanish quickly and rally drowned onto what they hit. */
    private static final Map<ThrownTrident, UUID> TRIDENTS = new ConcurrentHashMap<>();
    /** mobs just stepped out of → the game time they wake up (see {@link #daze}). */
    private static final Map<UUID, Long> DAZED = new ConcurrentHashMap<>();
    /** guardians whose current beam has already called the other guardians in. */
    private static final Map<UUID, Boolean> BEAM_RALLIED = new ConcurrentHashMap<>();
    /** players dealing beam / thorns damage right now — it skips the "puppet hits do base damage" override. */
    private static final Set<UUID> SPECIAL_DAMAGE = ConcurrentHashMap.newKeySet();
    /** mobs being forced to retaliate against the puppet that just hit them — so onTarget lets the grudge through. */
    private static final Set<UUID> FORCED_TARGET = ConcurrentHashMap.newKeySet();
    /** a ravager's bite in flight: the head pokes out a few ticks after the swing, then the hit lands. */
    private record Bite(LivingEntity target, long at) {}
    private static final Map<UUID, Bite> BITES = new ConcurrentHashMap<>();
    /** a vex puppet's dissolve tick (it's temporary); the action bar counts down to it. */
    private static final Map<UUID, Long> VEX_END = new ConcurrentHashMap<>();
    /** each guardian puppet's position last tick — a guardian holding still has its spikes out (thorns). */
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
    private static final Set<UUID> STILL = ConcurrentHashMap.newKeySet();
    private static final ResourceLocation BEAM_SLOW_ID = EffectUtil.modifierId("puppeteer_beam_slow");

    public BlessingPuppeteer() {
        super(EffectCategory.BLESSING, EffectCostTier.MAJOR, 75, () -> Items.BREEZE_ROD);
    }

    /** a puppet yanked away on a 90-second timer would be miserable. */
    @Override
    public boolean inRotations() {
        return false;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.PUPPETEER_ACTIVE, true);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        release(target, null, false);
        target.setData(WitchModAttachments.PUPPETEER_ACTIVE, false);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!target.getData(WitchModAttachments.PUPPETEER_ACTIVE)) {
            target.setData(WitchModAttachments.PUPPETEER_ACTIVE, true); // relog self-heal
        }
        Binding binding = BINDINGS.get(target.getUUID());
        if (binding != null) {
            tickBinding(target, binding);
            return;
        }
        PuppetType type = possessed(target);
        if (type == null) {
            return;
        }
        if (ticksRemaining % 20 == 0) {
            if (type.burnsInSun() && Config.PUPPETEER_ZOMBIE_BURNS.get()) {
                sunburn(target);
            }
            scaleHealth(target);
        }
        switch (type) {
            case CREEPER -> {
                tickHold(target, type);
                boolean charged = target.getData(WitchModAttachments.PUPPET_DATA).getBoolean("powered")
                        || Synergies.CHARGED_PUPPET.activeFor(target);
                if (target.getData(WitchModAttachments.PUPPET_CHARGED) != charged) {
                    target.setData(WitchModAttachments.PUPPET_CHARGED, charged);
                }
            }
            case DROWNED -> {
                tickHold(target, type);
                target.setAirSupply(target.getMaxAirSupply()); // drowned don't drown
            }
            case CHICKEN -> {
                tickHold(target, type);
                if (ticksRemaining % 10 == 0) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, false));
                }
            }
            default -> { }
        }
        if (type.group() == Group.ANIMAL || type == PuppetType.RABBIT) {
            lure(target, type);
        }
        if (type == PuppetType.KILLER_RABBIT || type == PuppetType.WOLF) {
            tickLunge(target, type); // the maul's lunge (a frenzied wolf shares the killer bunny's maul, weakened)
            tickMaul(target);
        }
        if (type == PuppetType.WOLF) {
            tickWolf(target);
        }
        if (type == PuppetType.CAT) {
            tickCat(target);
        }
        if (type == PuppetType.SILVERFISH) {
            tickSilverfish(target);
        }
        if (type == PuppetType.ENDERMITE) {
            tickEndermite(target);
        }
        if (type == PuppetType.ENDERMAN) {
            tickEnderman(target, ticksRemaining);
        }
        if (type.group() == Group.HORSE) {
            syncRiders(target);
            if (type == PuppetType.SKELETON_HORSE && target.isUnderWater()) {
                target.setAirSupply(target.getMaxAirSupply()); // bones don't breathe
            }
        }
        if (type.group() == Group.SLIME) {
            tickSlimeFx(target, type);
        }
        if (type == PuppetType.GHAST) {
            tickGhast(target);
        }
        if (type == PuppetType.BLAZE) {
            tickBlaze(target, ticksRemaining);
        }
        if (type == PuppetType.BREEZE) {
            tickBreeze(target);
        }
        if (type == PuppetType.WITCH) {
            tickWitch(target);
        }
        if (type == PuppetType.CAMEL) {
            tickHold(target, type);
            syncRiders(target);
        }
        if (type == PuppetType.PILLAGER) {
            refillQuiver(target);
        }
        if (type == PuppetType.VINDICATOR) {
            tickVindicator(target);
        }
        if (type == PuppetType.EVOKER) {
            tickEvoker(target);
        }
        if (type == PuppetType.RAVAGER) {
            tickHold(target, type); // the roar's charge (right-click held)
            tickRavager(target);
        }
        if (type == PuppetType.VEX) {
            tickVex(target);
        }
        if (type == PuppetType.ALLAY) {
            tickAllay(target);
        }
        if (type == PuppetType.FOX) {
            tickFox(target);
        }
        if (type.group() == Group.GOAT) {
            tickGoat(target, type);
        }
        if (type.entityType().fireImmune() && target.isOnFire()) {
            target.clearFire(); // fire-proof mobs never catch alight
        }
        if (type.group() == Group.SPIDER && target.onGround()) {
            AIR_POUNCED.remove(target.getUUID());
        }
        if (type.group() == Group.FISH || type.group() == Group.SQUID) {
            tickFish(target, type);
        }
        if (type.group() == Group.GUARDIAN) {
            tickGuardian(target, type);
        }
        if (type.group() == Group.BRUTE) {
            tickBrute(target, type);
        }
        if (type == PuppetType.DOLPHIN) {
            tickDolphin(target);
        }
        if (type == PuppetType.AXOLOTL) {
            tickAxolotl(target, ticksRemaining);
        }
        if (type == PuppetType.PHANTOM) {
            tickLunge(target, type); // the dive
        }
        if (ticksRemaining % 10 == 0) {
            // every puppet: nearby mobs that would hunt its mob TYPE pick it out (hunts() holds the faction logic),
            // so a stray puppet draws iron golems while a sheep puppet is left alone.
            drawHunters(target, type);
        }
        if (type == PuppetType.SNOW_GOLEM) {
            tickSnowGolem(target, ticksRemaining);
        }
        if (type == PuppetType.IRON_GOLEM) {
            tickOffer(target);
            if (target.isUnderWater()) {
                target.setAirSupply(target.getMaxAirSupply()); // golems don't breathe
            }
        }
        if (type == PuppetType.WITHER_SKELETON && target.hasEffect(MobEffects.WITHER)) {
            target.removeEffect(MobEffects.WITHER); // wither skeletons shrug off wither
        }
    }

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
        int interval = Config.PUPPETEER_ELDER_CURSE_INTERVAL_TICKS.get();
        if (type == PuppetType.ELDER_GUARDIAN && interval > 0 && player.level().getGameTime() % interval == 0) {
            elderCurse(player);
        }
    }

    // --- hoglin / zoglin: lone brutes, no rallies -----------------------------------------------------

    /** a lunge in progress (server side): when it started and who it has already hit. */
    private record Lunge(long start, Set<UUID> hit) {}

    private static final Map<UUID, Lunge> LUNGES = new ConcurrentHashMap<>();
    /** a hoglin puppet's ticks outside the nether (written back to the mob as TimeInOverworld on release). */
    private static final Map<UUID, Integer> HOGLIN_TIME = new ConcurrentHashMap<>();
    private static final ResourceLocation BRUTE_KB_RESIST_ID = EffectUtil.modifierId("puppeteer_brute_kb_resist");
    private static final ResourceLocation GOLEM_KB_RESIST_ID = EffectUtil.modifierId("puppeteer_golem_kb_resist");
    private static final ResourceLocation GOLEM_ATTACK_SPEED_ID = EffectUtil.modifierId("puppeteer_golem_attack_speed");
    /** golem-swing victims → upward fling, applied at the end of the tick (after vanilla's own hit knockback). */
    private static final Map<LivingEntity, Double> PENDING_FLING = new ConcurrentHashMap<>();
    /** a golem's attack charge, caught before vanilla resets it, so its swing damage scales like a weapon's. */
    private static final Map<UUID, Float> SWING_STRENGTH = new ConcurrentHashMap<>();
    /** a vindicator lunge in flight → its charge-scaled damage multiplier; removed when it connects (else it missed). */
    private static final Map<UUID, Float> LUNGE_BONUS = new ConcurrentHashMap<>();
    /** a vindicator that MISSED a lunge → the tick its recovery lockout ends (can't swing or lunge until then). */
    private static final Map<UUID, Long> RECOVER_UNTIL = new ConcurrentHashMap<>();

    // the lunge machinery is shared: hoglin / zoglin charges and the phantom's dive (no wind-up, steered in 3d).
    public static int lungeWindup(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_WINDUP.get();
            case PHANTOM, KILLER_RABBIT, GOAT, SCREAMING_GOAT, FOX, VINDICATOR, WOLF -> 0; // the charge/none is the wind-up
            case VEX -> Config.PUPPETEER_VEX_LUNGE_WINDUP_TICKS.get(); // a slight giggle-charge before it darts
            default -> Config.PUPPETEER_HOGLIN_LUNGE_WINDUP.get();
        };
    }

    /** a lunge's length for this player — a goat's ram / a vindicator's committed lunge depend on the charge. */
    public static int lungeTicks(Player player, PuppetType type) {
        if (type.group() == Group.GOAT) {
            return ramTicks(player);
        }
        if (type == PuppetType.VINDICATOR) {
            return vindLungeTicks(player);
        }
        return lungeTicks(type);
    }

    /** a lunge's speed for this player — a goat's ram / a vindicator's committed lunge depend on the charge. */
    public static double lungeSpeed(Player player, PuppetType type) {
        if (type.group() == Group.GOAT) {
            return ramSpeed(player);
        }
        if (type == PuppetType.VINDICATOR) {
            return vindLungeSpeed(player);
        }
        return lungeSpeed(type);
    }

    /** a vindicator lunge's share of full reach from its charge (PUPPET_DASH_POWER); even a tap commits a real gap-closer. */
    private static double vindShare(Player player) {
        return 0.6 + 0.4 * Mth.clamp(player.getData(WitchModAttachments.PUPPET_DASH_POWER) / 100.0, 0.0, 1.0);
    }

    public static int vindLungeTicks(Player player) {
        double reach = johnny(player) ? Config.PUPPETEER_JOHNNY_LUNGE_REACH.get() : 1.0;
        return Math.max(3, (int) Math.round(Config.PUPPETEER_VINDICATOR_LUNGE_TICKS.get() * vindShare(player) * reach));
    }

    public static double vindLungeSpeed(Player player) {
        double reach = johnny(player) ? Config.PUPPETEER_JOHNNY_LUNGE_REACH.get() : 1.0;
        return Config.PUPPETEER_VINDICATOR_LUNGE_SPEED.get() * vindShare(player) * reach;
    }

    public static int lungeTicks(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_TICKS.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_DIVE_TICKS.get();
            case KILLER_RABBIT -> Config.PUPPETEER_KILLER_RABBIT_MAUL_TICKS.get();
            case WOLF -> Config.PUPPETEER_WOLF_MAUL_TICKS.get();
            case FOX -> Config.PUPPETEER_FOX_POUNCE_TICKS.get();
            case VEX -> Config.PUPPETEER_VEX_LUNGE_TICKS.get();
            default -> Config.PUPPETEER_HOGLIN_LUNGE_TICKS.get();
        };
    }

    public static double lungeSpeed(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_SPEED.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_DIVE_SPEED.get();
            case KILLER_RABBIT -> Config.PUPPETEER_KILLER_RABBIT_MAUL_SPEED.get();
            case WOLF -> Config.PUPPETEER_WOLF_MAUL_SPEED.get();
            case FOX -> Config.PUPPETEER_FOX_POUNCE_SPEED.get();
            case VEX -> Config.PUPPETEER_VEX_LUNGE_SPEED.get();
            default -> Config.PUPPETEER_HOGLIN_LUNGE_SPEED.get();
        };
    }

    public static double lungeTurn(PuppetType type) {
        return switch (type) {
            case ZOGLIN -> Config.PUPPETEER_ZOGLIN_LUNGE_TURN.get();
            case PHANTOM -> Config.PUPPETEER_PHANTOM_DIVE_TURN.get();
            case KILLER_RABBIT, WOLF -> 0.0; // a maul goes dead straight
            case GOAT, SCREAMING_GOAT, FOX, VINDICATOR -> 3.0; // a ram (or a fox's rush / an axe lunge) barely turns
            case VEX -> Config.PUPPETEER_VEX_LUNGE_TURN.get(); // a vex homes in a bit as it darts
            default -> Config.PUPPETEER_HOGLIN_LUNGE_TURN.get();
        };
    }

    private static boolean isBabyPuppet(ServerPlayer player) {
        return player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0;
    }

    /** hoglins: warped fungus drives them off, they zombify outside the nether; both: the lunge's collisions. */
    private static void tickBrute(ServerPlayer player, PuppetType type) {
        if (type == PuppetType.HOGLIN) {
            repelFungus(player);
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            if (Config.PUPPETEER_HOGLIN_ZOMBIFIES.get() && !player.level().dimensionType().piglinSafe()
                    && !tag.getBoolean("IsImmuneToZombification")
                    && HOGLIN_TIME.merge(player.getUUID(), 1, Integer::sum) >= Config.PUPPETEER_HOGLIN_ZOMBIFY_TICKS.get()) {
                zombify(player);
                return;
            }
        }
        tickLunge(player, type);
    }

    /** each hoglin puppet's nearest repellent, re-scanned every few ticks (like vanilla's NEAREST_REPELLENT sensor). */
    private static final Map<UUID, java.util.Optional<BlockPos>> REPELLENT = new ConcurrentHashMap<>();

    /**
     * placed warped fungus (vanilla's hoglin_repellents: fungus, nether portals, respawn anchors) drives a hoglin
     * away, exactly as it scares the real mob off.
     */
    private static void repelFungus(ServerPlayer player) {
        int range = (int) Math.round(Config.PUPPETEER_HOGLIN_FUNGUS_RANGE.get());
        if (range <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        java.util.Optional<BlockPos> near = REPELLENT.get(player.getUUID());
        if (near == null || player.tickCount % 5 == 0) {
            near = BlockPos.findClosestMatch(player.blockPosition(), range, 4,
                    p -> level.getBlockState(p).is(net.minecraft.tags.BlockTags.HOGLIN_REPELLENTS));
            REPELLENT.put(player.getUUID(), near);
        }
        near.ifPresent(at -> {
            Vec3 away = player.position().subtract(Vec3.atCenterOf(at)).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            player.setDeltaMovement(player.getDeltaMovement().add(away.scale(Config.PUPPETEER_HOGLIN_FUNGUS_STRENGTH.get())));
            player.hurtMarked = true;
        });
    }

    /** the real thing: a hoglin outside the nether shakes, then becomes a zoglin — and so do you. */
    private static void zombify(ServerPlayer player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOGLIN).toString());
        tag.remove("TimeInOverworld");
        tag.remove("IsImmuneToZombification");
        tag.remove("CannotBeHunted");
        tag.remove("Brain");
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setData(WitchModAttachments.PUPPET_TYPE, PuppetType.ZOGLIN.id());
        HOGLIN_TIME.remove(player.getUUID());
        endLunge(player);
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, PuppetType.ZOGLIN.speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.refreshDimensions();
    }

    /** Lunge: stand still for the wind-up, then charge (your own client steers it — see PuppeteerClient.tickLunge). */
    private static void startLunge(ServerPlayer player, PuppetType type) {
        long now = player.level().getGameTime();
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        // subtle on purpose — it should pass for a real hoglin squaring up.
        player.serverLevel().playSound(null, player.blockPosition(),
                type == PuppetType.ZOGLIN ? SoundEvents.ZOGLIN_ANGRY : SoundEvents.HOGLIN_ANGRY, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void endLunge(ServerPlayer player) {
        LUNGES.remove(player.getUUID());
        if (player.getData(WitchModAttachments.PUPPET_LUNGE_START) != 0L) {
            player.setData(WitchModAttachments.PUPPET_LUNGE_START, 0L);
        }
    }

    /**
     * during the charge, whatever you run into takes the lunge's damage and is sent flying the way you're going. a
     * hoglin's charge stops on its first hit; a zoglin's ploughs on through (each thing only once).
     */
    private static void tickLunge(ServerPlayer player, PuppetType type) {
        Lunge lunge = LUNGES.get(player.getUUID());
        if (lunge == null) {
            return;
        }
        long t = player.level().getGameTime() - lunge.start();
        int windup = lungeWindup(type);
        if (t >= windup + lungeTicks(player, type)) {
            // a vindicator lunge that ran its course without connecting is a MISS — the commit's recovery lockout.
            if (type == PuppetType.VINDICATOR && LUNGE_BONUS.remove(player.getUUID()) != null) {
                RECOVER_UNTIL.put(player.getUUID(), player.level().getGameTime() + Config.PUPPETEER_VINDICATOR_LUNGE_RECOVERY_TICKS.get());
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 0.7F);
            }
            endLunge(player);
            return;
        }
        if (t < windup) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (type == PuppetType.VINDICATOR) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.4),
                    e -> e != player && e.isAlive() && !e.isSpectator())) {
                vindicatorLungeHit(player, e);
                endLunge(player);
                cooldown(player, Config.PUPPETEER_VINDICATOR_LUNGE_COOLDOWN_TICKS.get()); // a connect just goes on cooldown, no recovery
                return;
            }
            return;
        }
        if (type == PuppetType.FOX) {
            tickMug(player);
            return;
        }
        if (type.group() == Group.GOAT) {
            tickRam(player, type);
            return;
        }
        if (type == PuppetType.PHANTOM) {
            tickDive(player, lunge);
            return;
        }
        if (type == PuppetType.VEX) {
            tickVexLunge(player);
            return;
        }
        if (type == PuppetType.KILLER_RABBIT || type == PuppetType.WOLF) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.4),
                    e -> e != player && e.isAlive() && !e.isSpectator())) {
                endLunge(player);
                startMaul(player, e); // the first thing it reaches, it latches onto
                return;
            }
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        boolean zoglin = type == PuppetType.ZOGLIN;
        float damage = (zoglin ? Config.PUPPETEER_ZOGLIN_LUNGE_DAMAGE.get() : Config.PUPPETEER_HOGLIN_LUNGE_DAMAGE.get()).floatValue();
        double launch = Config.PUPPETEER_HOGLIN_LUNGE_LAUNCH.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.35),
                e -> e != player && e.isAlive() && !e.isSpectator() && !lunge.hit().contains(e.getUUID()))) {
            lunge.hit().add(e.getUUID());
            SPECIAL_DAMAGE.add(player.getUUID());
            try {
                e.hurt(player.damageSources().playerAttack(player), damage);
            } finally {
                SPECIAL_DAMAGE.remove(player.getUUID());
            }
            double power = launch * Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            e.setDeltaMovement(dir.x * power, Math.max(e.getDeltaMovement().y, 0.0) + power * 0.4, dir.z * power);
            e.hurtMarked = true;
            player.swing(InteractionHand.MAIN_HAND, true);
            level.playSound(null, e.blockPosition(), zoglin ? SoundEvents.ZOGLIN_ATTACK : SoundEvents.HOGLIN_ATTACK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!zoglin) {
                endLunge(player); // a hoglin's charge stops dead on the first thing it hits
                return;
            }
        }
    }

    // --- phantom ------------------------------------------------------------------------------------

    /**
     * phantom: Dive (right-click) — a fast free dive along where you look, steered only a little (your own client flies
     * it; see PuppeteerClient.tickLunge). a skill shot: the first thing you hit takes the dive's bonus damage and you
     * pull up. miss and it just ends (on the ground, or when it runs out). its bite is the normal left-click attack.
     */
    private static void startDive(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (LUNGES.containsKey(player.getUUID())) {
            return;
        }
        cooldown(player, Config.PUPPETEER_PHANTOM_DIVE_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void tickDive(ServerPlayer player, Lunge dive) {
        if (player.onGround() && player.level().getGameTime() - dive.start() > 2) {
            endLunge(player); // pulled up short — into the ground
            return;
        }
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            SPECIAL_DAMAGE.add(player.getUUID());
            try {
                e.hurt(player.damageSources().playerAttack(player), Config.PUPPETEER_PHANTOM_DIVE_DAMAGE.get().floatValue());
            } finally {
                SPECIAL_DAMAGE.remove(player.getUUID());
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0F, 1.0F);
            // pull up out of the dive, like a phantom peeling away after a strike.
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, 0.7, 0.0));
            player.hurtMarked = true;
            endLunge(player);
            return;
        }
    }

    // --- killer rabbit -----------------------------------------------------------------------------

    /** a killer rabbit's scrap in progress: its victim, where both are pinned, and hits landed so far. */
    private record Maul(LivingEntity target, Vec3 at, Vec3 targetAt, long start, int[] hits) {}

    private static final Map<UUID, Maul> MAULS = new ConcurrentHashMap<>();
    private static final ResourceLocation RABBIT_JUMP_ID = EffectUtil.modifierId("puppeteer_rabbit_jump");
    private static final ResourceLocation RABBIT_FALL_ID = EffectUtil.modifierId("puppeteer_rabbit_fall");

    /** killer rabbit (and a frenzied wolf): Maul (right-click) — a straight lunge (the shared lunge machinery, no wind-up). */
    private static void startMaulLunge(ServerPlayer player) {
        if (LUNGES.containsKey(player.getUUID()) || MAULS.containsKey(player.getUUID())) {
            return;
        }
        boolean wolf = possessed(player) == PuppetType.WOLF;
        long now = player.level().getGameTime();
        cooldown(player, wolf ? Config.PUPPETEER_WOLF_MAUL_COOLDOWN_TICKS.get() : Config.PUPPETEER_KILLER_RABBIT_MAUL_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(),
                wolf ? SoundEvents.WOLF_GROWL : SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.2F, wolf ? 1.0F : 0.7F);
    }

    /** the lunge reached something: both of you are pinned in a frenzied scrap (PUPPET_FUSE > 0 holds your client still). */
    private static void startMaul(ServerPlayer player, LivingEntity target) {
        MAULS.put(player.getUUID(), new Maul(target, player.position(), target.position(), player.level().getGameTime(), new int[1]));
        player.setData(WitchModAttachments.PUPPET_FUSE, 1);
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.RABBIT_ATTACK, SoundSource.PLAYERS, 1.5F, 0.8F);
    }

    /**
     * the scrap: neither of you can move, and every few ticks it lands a hit — a ball of dust and fur and crits, the
     * cartoon fight cloud — until its hits are spent or the victim drops. then the victim is thrown clear.
     */
    private static void tickMaul(ServerPlayer player) {
        Maul maul = MAULS.get(player.getUUID());
        if (maul == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = maul.target();
        boolean wolf = possessed(player) == PuppetType.WOLF;
        int maxHits = (wolf ? Config.PUPPETEER_WOLF_MAUL_HITS : Config.PUPPETEER_KILLER_RABBIT_MAUL_HITS).get();
        if (!target.isAlive() || target.level() != level || maul.hits()[0] >= maxHits) {
            endMaul(player, maul);
            return;
        }
        // pinned, both of you
        if (player.position().distanceToSqr(maul.at()) > 0.01) {
            player.teleportTo(maul.at().x, maul.at().y, maul.at().z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        if (target.position().distanceToSqr(maul.targetAt()) > 0.01) {
            target.teleportTo(maul.targetAt().x, maul.targetAt().y, maul.targetAt().z);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        Vec3 mid = maul.at().add(maul.targetAt()).scale(0.5).add(0.0, 0.4, 0.0);
        // the fight cloud churns the whole time
        level.sendParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 3, 0.4, 0.3, 0.4, 0.02);
        long t = level.getGameTime() - maul.start();
        if (t % (wolf ? Config.PUPPETEER_WOLF_MAUL_HIT_INTERVAL : Config.PUPPETEER_KILLER_RABBIT_MAUL_HIT_INTERVAL).get() != 0) {
            return;
        }
        maul.hits()[0]++;
        target.invulnerableTime = 0; // every hit in the scrap lands
        SPECIAL_DAMAGE.add(player.getUUID());
        NO_KNOCKBACK.add(target.getUUID()); // pinned, not shoved
        try {
            target.hurt(player.damageSources().playerAttack(player),
                    (wolf ? Config.PUPPETEER_WOLF_MAUL_DAMAGE : Config.PUPPETEER_KILLER_RABBIT_MAUL_DAMAGE).get().floatValue());
        } finally {
            SPECIAL_DAMAGE.remove(player.getUUID());
            NO_KNOCKBACK.remove(target.getUUID());
        }
        player.swing(level.random.nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 10, 0.4, 0.4, 0.4, 0.4);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mid.x, mid.y + 0.3, mid.z, 3, 0.3, 0.2, 0.3, 0.1);
        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.RABBIT_HIDE)),
                mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.15); // tufts of fur
        level.sendParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 4, 0.5, 0.3, 0.5, 0.05);
        level.playSound(null, target.blockPosition(), level.random.nextBoolean() ? SoundEvents.RABBIT_ATTACK : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.0F, 0.8F + level.random.nextFloat() * 0.5F);
    }

    private static void endMaul(ServerPlayer player, Maul maul) {
        MAULS.remove(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        LivingEntity target = maul.target();
        if (target.isAlive()) {
            // thrown clear of the bunny
            target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        Vec3 mid = maul.at().add(maul.targetAt()).scale(0.5).add(0.0, 0.4, 0.0);
        player.serverLevel().sendParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 16, 0.5, 0.4, 0.5, 0.08);
    }

    // --- silverfish ---------------------------------------------------------------------------------

    /** a silverfish puppet burrowing in: the block it's going into. */
    private static final Map<UUID, BlockPos> EMBEDDING = new ConcurrentHashMap<>();
    /** a silverfish puppet hidden in stone: the block, and since when. */
    private record Hideout(BlockPos pos, long since) {}

    private static final Map<UUID, Hideout> HIDEOUTS = new ConcurrentHashMap<>();
    /** where a hidden silverfish is, in its saved puppet data — so even a crash can't leave you entombed (see unhide). */
    private static final String HIDDEN_KEY = "witchmod_hidden";

    public static boolean hidden(Player player) {
        return player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    // --- endermite ----------------------------------------------------------------------------------

    private static final String BURROWED_KEY = "witchmod_burrowed";

    /** a burrowing endermite puppet — moves through blocks (both sides; see EntityBurrowMixin). */
    public static boolean burrowed(Player player) {
        return player.hasData(WitchModAttachments.PUPPET_BURROWED) && player.getData(WitchModAttachments.PUPPET_BURROWED);
    }

    /** whether a puppet is phasing through blocks right now: a burrowing endermite, or a vex while it flies. */
    public static boolean phasing(Player player) {
        return burrowed(player)
                || (PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VEX && player.getAbilities().flying);
    }

    /** the hardest block this puppet can pass: a vex gets obsidian (50), an endermite stops at obsidian-hard. */
    private static double maxHardness(Player player) {
        try {
            return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VEX
                    ? Config.PUPPETEER_VEX_MAX_HARDNESS.get() : Config.PUPPETEER_ENDERMITE_MAX_HARDNESS.get();
        } catch (IllegalStateException e) {
            return 50.0;
        }
    }

    /** a block a phasing puppet can't pass: unbreakable (bedrock, barriers...) or at least as hard as its cap. */
    private static boolean impassable(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, double cap) {
        if (state.isAir()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness < 0.0F || hardness >= cap;
    }

    private static boolean passable(Player player, net.minecraft.world.phys.AABB box) {
        net.minecraft.world.level.Level level = player.level();
        if (box.minY < level.getMinBuildHeight()) {
            return false; // never out through the bottom of the world
        }
        double cap = maxHardness(player);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX - 1.0E-7), Mth.floor(box.maxY - 1.0E-7), Mth.floor(box.maxZ - 1.0E-7))) {
            if (impassable(level, pos, level.getBlockState(pos), cap)) {
                return false;
            }
        }
        return true;
    }

    /** how much of {@code delta} a burrowing endermite may move: all of it, or axis by axis up to an impassable block. */
    public static Vec3 burrowDelta(Player player, Vec3 delta) {
        net.minecraft.world.phys.AABB box = player.getBoundingBox();
        if (passable(player, box.move(delta))) {
            return delta;
        }
        Vec3 moved = Vec3.ZERO;
        for (Vec3 axis : List.of(new Vec3(0.0, delta.y, 0.0), new Vec3(delta.x, 0.0, 0.0), new Vec3(0.0, 0.0, delta.z))) {
            if (axis.lengthSqr() > 0.0 && passable(player, box.move(moved.add(axis)))) {
                moved = moved.add(axis);
            }
        }
        return moved;
    }

    /** inside the earth (any solid block in your box) — where a burrowing endermite has full 3d control. */
    public static boolean inEarth(Player player) {
        return !player.level().noCollision(player.getBoundingBox().deflate(1.0E-3));
    }

    /**
     * endermite: Burrow / Surface (right-click). burrowing, you sink into the ground and move through it freely (your own
     * client steers: where you look, jump to rise) — through anything short of obsidian-hard. out in the open air (a
     * cave) you just fall until you're back in the rock. surfacing takes you up to the nearest open space.
     */
    private static void toggleBurrow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (burrowed(player)) {
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            if (!surface(player, tag)) {
                player.displayClientMessage(Component.translatable("witchmod.puppeteer.endermite_no_surface").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            player.setData(WitchModAttachments.PUPPET_DATA, tag);
            cooldown(player, Config.PUPPETEER_ENDERMITE_BURROW_COOLDOWN_TICKS.get());
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
        BlockState ground = level.getBlockState(below);
        if (ground.getCollisionShape(level, below).isEmpty() || impassable(level, below, ground, maxHardness(player))) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.endermite_no_burrow").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        player.setData(WitchModAttachments.PUPPET_BURROWED, true);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putBoolean(BURROWED_KEY, true); // saved with you: a crash mid-burrow still surfaces you on login
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.teleportTo(player.getX(), player.getY() - 0.9, player.getZ()); // sink in
        Vec3 at = player.position();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.9, at.z,
                16, 0.3, 0.1, 0.3, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.9, at.z, 12, 0.3, 0.2, 0.3, 0.3);
        level.playSound(null, below, ground.getSoundType(level, below, player).getBreakSound(), SoundSource.PLAYERS, 0.8F, 1.2F);
        cooldown(player, Config.PUPPETEER_ENDERMITE_BURROW_COOLDOWN_TICKS.get());
    }

    /**
     * stop burrowing, coming up to the nearest spot above where you fit in open air (or, failing that, anywhere close).
     * @return false if there's nowhere to come up (you stay burrowed); true if surfaced or never burrowed.
     */
    private static boolean surface(ServerPlayer player, CompoundTag tag) {
        boolean was = burrowed(player) || tag.getBoolean(BURROWED_KEY);
        if (!was) {
            return true;
        }
        ServerLevel level = player.serverLevel();
        Vec3 spot = null;
        for (int dy = 0; dy <= 64 && spot == null; dy++) {
            for (int sign : new int[] {1, -1}) {
                Vec3 at = player.position().add(0.0, Math.ceil(player.getY()) - player.getY() + sign * dy, 0.0);
                if (level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position())))) {
                    spot = at;
                    break;
                }
            }
        }
        if (spot == null) {
            return false;
        }
        player.setData(WitchModAttachments.PUPPET_BURROWED, false);
        player.noPhysics = false;
        tag.remove(BURROWED_KEY);
        player.teleportTo(spot.x, spot.y, spot.z);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 0.3, spot.z, 16, 0.3, 0.2, 0.3, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMITE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** a burrowing endermite stirs up the rock it passes through (and trails a little ender dust). */
    private static void tickEndermite(ServerPlayer player) {
        if (!burrowed(player)) {
            return;
        }
        player.resetFallDistance();
        ServerLevel level = player.serverLevel();
        Vec3 last = LAST_POS.put(player.getUUID(), player.position());
        boolean moving = last != null && last.distanceToSqr(player.position()) > 1.0E-3;
        if (!moving || player.tickCount % 3 != 0) {
            return;
        }
        BlockPos at = BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ());
        BlockState state = level.getBlockState(at);
        if (!state.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                    player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.2, 0.1, 0.2, 0.05);
            if (player.tickCount % 9 == 0) {
                level.playSound(null, at, state.getSoundType(level, at, player).getHitSound(), SoundSource.PLAYERS, 0.35F, 0.8F);
            }
        }
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.1, 0.1, 0.1, 0.2);
    }

    // --- enderman -----------------------------------------------------------------------------------

    private static final String CARRIED_KEY = "carriedBlockState"; // the enderman's own key — so it comes back holding it

    public static boolean enraged(Player player) {
        return player.getData(WitchModAttachments.PUPPET_RAGE_END) > player.level().getGameTime();
    }

    /**
     * vanilla's stare check (EnderMan.isLookingAtMe): {@code looker} is looking {@code at} in the eye — a narrow cone,
     * tighter the further away, with line of sight. a carved pumpkin on the looker's head makes it safe, as in vanilla.
     */
    public static boolean staresAt(Player looker, LivingEntity at) {
        if (looker == at || looker.isSpectator() || looker.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
            return false;
        }
        Vec3 view = looker.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(at.getX() - looker.getX(), at.getEyeY() - looker.getEyeY(), at.getZ() - looker.getZ());
        double dist = to.length();
        if (dist > 64.0 || dist < 1.0E-3) {
            return false;
        }
        return view.dot(to.normalize()) > 1.0 - 0.025 / dist && looker.hasLineOfSight(at);
    }

    /**
     * enderman upkeep: water and rain hurt; anyone who looks you in the eye enrages you (Speed, more and faster
     * teleport charges, the dropped jaw); teleport charges recharge one at a time.
     */
    private static void tickEnderman(ServerPlayer player, int ticksRemaining) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (ticksRemaining % 10 == 0 && player.isInWaterRainOrBubble()) {
            player.hurt(player.damageSources().drown(), 1.0F);
        }
        if (ticksRemaining % 4 == 0) {
            for (ServerPlayer other : level.players()) {
                if (other != player && staresAt(other, player)) {
                    if (!enraged(player)) {
                        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.5F, 1.0F);
                        player.playNotifySound(SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                    player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() * 20L);
                    break;
                }
            }
        }
        boolean rage = enraged(player);
        int speed = Config.PUPPETEER_ENDERMAN_RAGE_SPEED.get();
        if (rage && speed > 0 && ticksRemaining % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, speed - 1, false, false));
        }
        int max = rage ? Config.PUPPETEER_ENDERMAN_RAGE_CHARGES.get() : 1;
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        if (charges > max) {
            player.setData(WitchModAttachments.PUPPET_TP_CHARGES, max); // calmed down: back to one
        } else if (charges < max && ready(player)) {
            player.setData(WitchModAttachments.PUPPET_TP_CHARGES, charges + 1);
            if (charges + 1 < max) {
                cooldown(player, teleportCooldown(player));
            }
        }
    }

    private static int teleportCooldown(ServerPlayer player) {
        return enraged(player) ? Config.PUPPETEER_ENDERMAN_RAGE_COOLDOWN_TICKS.get() : Config.PUPPETEER_ENDERMAN_TELEPORT_COOLDOWN_TICKS.get();
    }

    /** enderman right-click: the smart teleport, spending a charge (the recharge starts if it wasn't already running). */
    private static void endermanTeleport(ServerPlayer player) {
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        if (charges <= 0) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        double range = Config.PUPPETEER_ENDERMAN_TELEPORT_RANGE.get();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        BlockPos aim = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? BlockPos.containing(end)
                : hit.getBlockPos().relative(hit.getDirection());
        Vec3 spot = landingSpot(player, aim, (int) Math.ceil(range));
        if (spot == null) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.enderman_no_spot").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        blink(player, spot);
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, charges - 1);
        if (ready(player)) {
            cooldown(player, teleportCooldown(player));
        }
    }

    /**
     * where you'd land teleporting at {@code aim}: biased to the ground — looks down from there (up to {@code down}
     * blocks) for the first spot you fit standing on something solid, then a little up; never into water.
     */
    @Nullable
    private static Vec3 landingSpot(ServerPlayer player, BlockPos aim, int down) {
        ServerLevel level = player.serverLevel();
        for (int dy = 0; dy >= -down; dy--) {
            Vec3 at = standable(player, aim.offset(0, dy, 0));
            if (at != null) {
                return at;
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            Vec3 at = standable(player, aim.offset(0, dy, 0));
            if (at != null) {
                return at;
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 standable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isInWorldBounds(pos) || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return null;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return null;
        }
        Vec3 at = Vec3.atBottomCenterOf(pos);
        return level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position()))) ? at : null;
    }

    /** the teleport itself: vanilla's enderman sound and portal puffs at both ends. */
    private static void blink(ServerPlayer player, Vec3 to) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 32, 0.3, 1.0, 0.3, 0.5);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1.0, to.z, 32, 0.3, 1.0, 0.3, 0.5);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** a projectile would hit you: like the real thing you blink a few blocks away instead (free). */
    private static boolean dodge(ServerPlayer player) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos aim = BlockPos.containing(player.getX() + (player.getRandom().nextDouble() - 0.5) * 16.0,
                    player.getY() + player.getRandom().nextInt(5) - 2, player.getZ() + (player.getRandom().nextDouble() - 0.5) * 16.0);
            Vec3 spot = landingSpot(player, aim, 6);
            if (spot != null) {
                blink(player, spot);
                return true;
            }
        }
        return false;
    }

    /**
     * enderman crouch + right-click on a block: pick it up (anything a real enderman can carry) or put the one you're
     * carrying down there. it's kept in the puppet data under the enderman's own key, so it shows in your hands and
     * the real enderman is still holding it when you step out. @return false when you weren't aiming at a block (so
     * crouch + right-click at nothing still leaves).
     */
    private static boolean endermanCarry(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return false;
        }
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        BlockPos pos = hit.getBlockPos();
        if (tag.contains(CARRIED_KEY)) {
            BlockPos at = level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(hit.getDirection());
            BlockState carried = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    tag.getCompound(CARRIED_KEY));
            carried = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(carried, level, at);
            if (!player.mayBuild() || !level.mayInteract(player, at) || !level.getBlockState(at).canBeReplaced()
                    || !carried.canSurvive(level, at) || !level.isUnobstructed(carried, at, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
                return true; // nowhere to put it there — but don't leave the puppet over it
            }
            level.setBlock(at, carried, 3);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, at,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player, carried));
            level.playSound(null, at, carried.getSoundType(level, at, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            tag.remove(CARRIED_KEY);
        } else {
            BlockState state = level.getBlockState(pos);
            if (!state.is(net.minecraft.tags.BlockTags.ENDERMAN_HOLDABLE) || !player.mayBuild() || !level.mayInteract(player, pos)) {
                player.displayClientMessage(Component.translatable("witchmod.puppeteer.enderman_cant_carry").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            tag.put(CARRIED_KEY, net.minecraft.nbt.NbtUtils.writeBlockState(state));
            level.removeBlock(pos, false);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player, state));
            level.playSound(null, pos, state.getSoundType(level, pos, player).getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    // --- horse / donkey / mule ----------------------------------------------------------------------

    private static final ResourceLocation HORSE_STEP_ID = EffectUtil.modifierId("puppeteer_horse_step");
    private static final ResourceLocation HORSE_FALL_ID = EffectUtil.modifierId("puppeteer_horse_fall");
    private static final ResourceLocation HORSE_FALL_MULT_ID = EffectUtil.modifierId("puppeteer_horse_fall_mult");
    private static final ResourceLocation HORSE_ARMOR_ID = EffectUtil.modifierId("puppeteer_horse_armor");
    /** horse puppets → how many riders their own client was last told about (see syncRiders). */
    private static final Map<UUID, Integer> RIDERS_SENT = new ConcurrentHashMap<>();
    /** open saddlebag windows → whose saddlebags they are. */
    private static final Map<net.minecraft.world.SimpleContainer, UUID> SADDLEBAGS = new ConcurrentHashMap<>();

    public static double horseJump(PuppetType type) {
        return type == PuppetType.DONKEY || type == PuppetType.MULE ? Config.PUPPETEER_DONKEY_JUMP.get() : Config.PUPPETEER_HORSE_JUMP.get();
    }

    /** horse stats on possessing: steps up a full block, horse-like fall damage, and any armour it's wearing counts. */
    private static void applyHorseStats(ServerPlayer player) {
        EffectUtil.addModifier(player, Attributes.STEP_HEIGHT, HORSE_STEP_ID, 0.4, AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, HORSE_FALL_ID, 3.0, AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, HORSE_FALL_MULT_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyHorseArmor(player);
        if (possessed(player) == PuppetType.SKELETON_HORSE) {
            // a skeleton horse goes underwater like the real one: no slowing down in there (and it never drowns — see onTick)
            EffectUtil.addModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    private static void applyHorseArmor(ServerPlayer player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        ItemStack armor = tag.contains("body_armor_item")
                ? ItemStack.parse(player.registryAccess(), tag.getCompound("body_armor_item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        if (armor.getItem() instanceof net.minecraft.world.item.AnimalArmorItem a) {
            EffectUtil.addModifier(player, Attributes.ARMOR, HORSE_ARMOR_ID, a.getDefense(), AttributeModifier.Operation.ADD_VALUE);
        } else {
            EffectUtil.removeModifier(player, Attributes.ARMOR, HORSE_ARMOR_ID);
        }
    }

    private static void removeHorseStats(ServerPlayer player) {
        EffectUtil.removeModifier(player, Attributes.STEP_HEIGHT, HORSE_STEP_ID);
        EffectUtil.removeModifier(player, Attributes.SAFE_FALL_DISTANCE, HORSE_FALL_ID);
        EffectUtil.removeModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, HORSE_FALL_MULT_ID);
        EffectUtil.removeModifier(player, Attributes.ARMOR, HORSE_ARMOR_ID);
    }

    /**
     * a vanilla quirk: when a PLAYER gains or loses a passenger, its own client isn't told (the passenger packet only
     * goes to players tracking it). so a horse puppet's own client is told here whenever its riders change.
     */
    private static void syncRiders(ServerPlayer player) {
        int riders = player.getPassengers().size();
        Integer sent = RIDERS_SENT.put(player.getUUID(), riders);
        if (sent == null || sent != riders) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
        }
    }

    /**
     * another player right-clicks a horse puppet: a saddle / horse armour / chest in hand is put on it; crouching opens
     * a chested donkey's or mule's saddlebags; otherwise they climb on (no taming, no saddle needed — the puppet steers,
     * they just ride). @return true if handled.
     */
    private static boolean horseInteract(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (hand != InteractionHand.MAIN_HAND || !(user instanceof ServerPlayer rider)) {
            return true;
        }
        ServerLevel level = puppet.serverLevel();
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        if (stack.is(Items.SADDLE) && !tag.contains("SaddleItem")) {
            tag.put("SaddleItem", stack.copyWithCount(1).save(level.registryAccess()));
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            level.playSound(null, puppet.blockPosition(), SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        if (type == PuppetType.HORSE && stack.getItem() instanceof net.minecraft.world.item.AnimalArmorItem a
                && a.getBodyType() == net.minecraft.world.item.AnimalArmorItem.BodyType.EQUESTRIAN && !tag.contains("body_armor_item")) {
            tag.put("body_armor_item", stack.copyWithCount(1).save(level.registryAccess()));
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            applyHorseArmor(puppet);
            level.playSound(null, puppet.blockPosition(), SoundEvents.HORSE_ARMOR, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        boolean chested = tag.getBoolean("ChestedHorse");
        if ((type == PuppetType.DONKEY || type == PuppetType.MULE) && stack.is(Items.CHEST) && !chested) {
            tag.putBoolean("ChestedHorse", true);
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            level.playSound(null, puppet.blockPosition(), SoundEvents.DONKEY_CHEST, SoundSource.PLAYERS, 1.0F,
                    (puppet.getRandom().nextFloat() - puppet.getRandom().nextFloat()) * 0.2F + 1.0F);
            return true;
        }
        if (user.isShiftKeyDown()) {
            if (chested) {
                openSaddlebags(puppet, rider);
            }
            return true;
        }
        int seats = type == PuppetType.CAMEL ? 2 : 1; // a camel takes two riders, like the real one
        if (puppet.getPassengers().size() < seats && !rider.isPassenger() && !isBusy(rider)) {
            rider.startRiding(puppet, true);
            syncRiders(puppet);
        }
        return true;
    }

    /**
     * a chested donkey / mule's saddlebags: its 15 chest slots, straight from the puppet data (saved back on every
     * change), in a two-row chest window (the last three slots are blocked).
     */
    private static void openSaddlebags(ServerPlayer puppet, ServerPlayer viewer) {
        ServerLevel level = puppet.serverLevel();
        net.minecraft.world.SimpleContainer bags = new net.minecraft.world.SimpleContainer(18) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return slot < 15;
            }
        };
        ListTag items = puppet.getData(WitchModAttachments.PUPPET_DATA).getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getByte("Slot") & 255;
            if (slot < 15) {
                bags.setItem(slot, ItemStack.parse(level.registryAccess(), item).orElse(ItemStack.EMPTY));
            }
        }
        bags.addListener(container -> {
            if (!(level.getServer().getPlayerList().getPlayer(puppet.getUUID()) instanceof ServerPlayer owner)
                    || owner.isRemoved() || possessed(owner) == null || possessed(owner).group() != Group.HORSE) {
                return;
            }
            ListTag list = new ListTag();
            for (int i = 0; i < 15; i++) {
                ItemStack s = bags.getItem(i);
                if (!s.isEmpty()) {
                    CompoundTag t = new CompoundTag();
                    t.putByte("Slot", (byte) i);
                    list.add(s.save(level.registryAccess(), t));
                }
            }
            CompoundTag tag = owner.getData(WitchModAttachments.PUPPET_DATA);
            tag.put("Items", list);
            owner.setData(WitchModAttachments.PUPPET_DATA, tag);
        });
        SADDLEBAGS.put(bags, puppet.getUUID());
        viewer.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new net.minecraft.world.inventory.ChestMenu(
                net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inventory, bags, 2) {
            @Override
            public void removed(Player closer) {
                super.removed(closer);
                SADDLEBAGS.remove(bags);
            }
        }, Component.translatable("witchmod.puppeteer.saddlebags", puppet.getName())));
        level.playSound(null, puppet.blockPosition(), SoundEvents.DONKEY_CHEST, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** the puppet is going away: close anyone's saddlebag window onto it (whatever they put in is already saved). */
    private static void closeSaddlebags(ServerPlayer puppet) {
        SADDLEBAGS.entrySet().removeIf(e -> {
            if (!e.getValue().equals(puppet.getUUID())) {
                return false;
            }
            for (ServerPlayer viewer : puppet.server.getPlayerList().getPlayers()) {
                if (viewer.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu && menu.getContainer() == e.getKey()) {
                    viewer.closeContainer();
                }
            }
            return true;
        });
    }

    // --- slime / magma cube ------------------------------------------------------------------------

    /** a slime puppet's size (1 small, 2 medium, 4 big — the real slime's own), from its synced data. */
    public static int slimeSize(Player player) {
        return Math.max(1, player.getData(WitchModAttachments.PUPPET_DATA).getInt("Size") + 1);
    }

    /** a slime's hit grows with it (vanilla: its attack damage is its size); a magma cube's hits harder (+2). */
    private static float slimeDamage(Player player, PuppetType type) {
        int size = slimeSize(player);
        return type == PuppetType.MAGMA_CUBE ? size + 2.0F : size;
    }

    /**
     * a slime puppet "dies": a big or medium one splits like the real thing — 2-4 smaller slimes burst out, and you
     * carry on as one of them (full health at the new size). a small one just dies. @return true if it split.
     */
    private static boolean split(ServerPlayer player, PuppetType type) {
        int size = slimeSize(player);
        if (size <= 1) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        int half = size / 2;
        int others = 1 + level.random.nextInt(3); // 2-4 offspring in all, one of them you
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        for (int i = 0; i < others; i++) {
            if (!((type == PuppetType.MAGMA_CUBE ? EntityType.MAGMA_CUBE : EntityType.SLIME).create(level)
                    instanceof net.minecraft.world.entity.monster.Slime child)) {
                break;
            }
            float ox = ((i % 2) - 0.5F) * size / 4.0F;
            float oz = ((i / 2) - 0.5F) * size / 4.0F;
            child.setSize(half, true);
            child.moveTo(player.getX() + ox, player.getY() + 0.5, player.getZ() + oz, level.random.nextFloat() * 360.0F, 0.0F);
            if (tag.getBoolean("PersistenceRequired")) {
                child.setPersistenceRequired();
            }
            level.addFreshEntity(child);
        }
        tag.putInt("Size", half - 1);
        tag.remove("attributes"); // let the restored slime re-derive its stats from the new size
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        float max = half * half; // vanilla: a slime's max health is its size squared
        BASE_MAX.put(player.getUUID(), max);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, max);
        player.setData(WitchModAttachments.PUPPET_HEALTH, max);
        scaleHealth(player);
        player.setData(WitchModAttachments.PUPPET_HEALTH, player.getData(WitchModAttachments.PUPPET_MAX_HEALTH));
        player.refreshDimensions();
        level.sendParticles(type == PuppetType.MAGMA_CUBE ? ParticleTypes.FLAME : ParticleTypes.ITEM_SLIME,
                player.getX(), player.getY() + 0.3, player.getZ(), 12 * size, 0.3 * size, 0.2 * size, 0.3 * size, 0.1);
        level.playSound(null, player.blockPosition(), type == PuppetType.MAGMA_CUBE ? SoundEvents.MAGMA_CUBE_DEATH : SoundEvents.SLIME_DEATH,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** slime puppets that were on the ground last tick (for the landing squelch / take-off boing). */
    private static final Set<UUID> SLIME_GROUNDED = ConcurrentHashMap.newKeySet();

    /**
     * a slime's sound and splash, as the real one makes them: a squelch and a ring of slime (or embers) on every
     * landing, scaled with its size, and a boing on every take-off. sent from the server so everyone gets them.
     */
    private static void tickSlimeFx(ServerPlayer player, PuppetType type) {
        boolean grounded = player.onGround();
        boolean was = grounded ? !SLIME_GROUNDED.add(player.getUUID()) : SLIME_GROUNDED.remove(player.getUUID());
        if (grounded == was) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int size = slimeSize(player);
        boolean magma = type == PuppetType.MAGMA_CUBE;
        boolean small = size <= 1;
        if (grounded) {
            net.minecraft.core.particles.ParticleOptions particle = magma ? ParticleTypes.FLAME : ParticleTypes.ITEM_SLIME;
            for (int i = 0; i < size * 8; i++) {
                float angle = level.random.nextFloat() * Mth.TWO_PI;
                float reach = (level.random.nextFloat() * 0.5F + 0.5F) * size * 0.5F;
                level.sendParticles(particle, player.getX() + Mth.sin(angle) * reach, player.getY(), player.getZ() + Mth.cos(angle) * reach,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            level.playSound(null, player.blockPosition(), magma ? (small ? SoundEvents.MAGMA_CUBE_SQUISH_SMALL : SoundEvents.MAGMA_CUBE_SQUISH)
                            : (small ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH), SoundSource.PLAYERS,
                    0.4F * size, ((level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
        } else if (player.getDeltaMovement().y > 0.0) {
            level.playSound(null, player.blockPosition(), magma ? SoundEvents.MAGMA_CUBE_JUMP : (small ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP),
                    SoundSource.PLAYERS, 0.4F * size, ((level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }
    }

    // --- llama --------------------------------------------------------------------------------------

    /** llama: Spit (right-click) — the real llama's spit, from its mouth along your aim (1 damage; mostly an insult). */
    private static void spit(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.projectile.LlamaSpit spit = new net.minecraft.world.entity.projectile.LlamaSpit(EntityType.LLAMA_SPIT, level);
        spit.setOwner(player);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double out = (EntityType.LLAMA.getWidth() + 1.0) * 0.5; // vanilla's mouth offset for a llama
        spit.setPos(player.getX() - out * Mth.sin(yaw), player.getEyeY() - 0.1, player.getZ() + out * Mth.cos(yaw));
        Vec3 look = player.getLookAngle();
        spit.shoot(look.x, look.y, look.z, 1.5F, 1.0F);
        level.addFreshEntity(spit);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS, 1.0F,
                1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F);
    }

    // --- ghast / blaze / breeze: charged shots ------------------------------------------------------

    /** projectile streams in progress (ghast volley, blaze volley) → shots still to come, and the tick of the next one. */
    private record Stream(int left, long next, int interval, boolean enhanced) {}

    private static final Map<UUID, Stream> STREAMS = new ConcurrentHashMap<>();
    /** ghasts holding left-click (their volley charge). */
    private static final Set<UUID> ATTACK_HELD = ConcurrentHashMap.newKeySet();

    /** one tick of charge, sped up by the Dexterous blessing (every few ticks it credits an extra one). */
    public static int chargeStep(Player player) {
        if (player.getData(WitchModAttachments.DEXTEROUS_ACTIVE) < 0) {
            return 1;
        }
        double bonus;
        try {
            bonus = Config.PUPPETEER_DEXTEROUS_CHARGE_BONUS.get();
        } catch (IllegalStateException e) {
            return 1;
        }
        if (bonus <= 0.0) {
            return 1;
        }
        int whole = (int) bonus;
        double frac = bonus - whole;
        int every = frac > 1.0E-3 ? Math.max(1, (int) Math.round(1.0 / frac)) : 0;
        return 1 + whole + (every > 0 && player.tickCount % every == 0 ? 1 : 0);
    }

    /** clients report the attack button held / let go: a ghast's volley, a witch's throw (held: lingering), a screaming goat's shriek. */
    public static void setAttackHeld(ServerPlayer player, boolean held) {
        PuppetType attacker = possessed(player);
        if (attacker != PuppetType.GHAST && attacker != PuppetType.SCREAMING_GOAT) {
            ATTACK_HELD.remove(player.getUUID());
            return;
        }
        if (held) {
            ATTACK_HELD.add(player.getUUID());
            return;
        }
        if (!ATTACK_HELD.remove(player.getUUID())) {
            return;
        }
        int charge = player.getData(WitchModAttachments.PUPPET_FUSE);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        if (attacker == PuppetType.SCREAMING_GOAT) {
            goatShriek(player, charge);
            return;
        }
        if (charge >= Config.PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS.get() && ready2(player) && !STREAMS.containsKey(player.getUUID())) {
            // fully charged: the stream goes — and only now the face and the cry
            player.serverLevel().levelEvent(null, 1015, player.blockPosition(), 0);
            STREAMS.put(player.getUUID(), new Stream(Config.PUPPETEER_GHAST_VOLLEY_COUNT.get(), player.level().getGameTime(), 0, true));
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                    player.level().getGameTime() + Config.PUPPETEER_GHAST_VOLLEY_COOLDOWN_TICKS.get());
        }
    }

    /** ghast upkeep: the volley charge (while left-click is held and the volley's ready), and the stream once it fires. */
    private static void tickGhast(ServerPlayer player) {
        if (ATTACK_HELD.contains(player.getUUID()) && ready2(player) && !STREAMS.containsKey(player.getUUID())) {
            int cap = Config.PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS.get();
            int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
            if (hold < cap) {
                player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(cap, hold + chargeStep(player)));
            }
        }
        tickStream(player, PuppetType.GHAST);
        chargeSlow(player);
    }

    /** ghast: right-click — one fireball, straight away (the face and the cry come with it). */
    private static void ghastShot(ServerPlayer player) {
        cooldown(player, Config.PUPPETEER_GHAST_FIREBALL_COOLDOWN_TICKS.get());
        player.serverLevel().levelEvent(null, 1015, player.blockPosition(), 0);
        ghastFireball(player, 0.0F);
    }

    /** the ghast's own fireball (its blast power from the puppet's data), launched clear of its 4-wide body. */
    private static void ghastFireball(ServerPlayer player, float scatter) {
        ServerLevel level = player.serverLevel();
        Vec3 look = scattered(player, scatter);
        int power = Math.max(1, player.getData(WitchModAttachments.PUPPET_DATA).getByte("ExplosionPower"));
        net.minecraft.world.entity.projectile.LargeFireball fireball = new net.minecraft.world.entity.projectile.LargeFireball(level, player, look, power);
        Vec3 from = player.getEyePosition().add(look.scale(3.0)); // vanilla spawns it 4 out from the ghast's middle
        fireball.setPos(from.x, from.y - 0.5, from.z);
        level.addFreshEntity(fireball);
        level.levelEvent(null, 1016, player.blockPosition(), 0); // the ghast's shot
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** your aim, nudged up to {@code degrees} off at random (a volley's scatter). */
    private static Vec3 scattered(ServerPlayer player, float degrees) {
        if (degrees <= 0.0F) {
            return player.getLookAngle();
        }
        float yaw = player.getYRot() + (player.getRandom().nextFloat() * 2.0F - 1.0F) * degrees;
        float pitch = player.getXRot() + (player.getRandom().nextFloat() * 2.0F - 1.0F) * degrees * 0.6F;
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /** fires the next shot of a stream when it's due (the ghast's / blaze's volley), following your aim with scatter. */
    private static void tickStream(ServerPlayer player, PuppetType type) {
        Stream stream = STREAMS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (stream == null || now < stream.next()) {
            return;
        }
        if (type == PuppetType.GHAST) {
            ghastFireball(player, Config.PUPPETEER_GHAST_VOLLEY_SCATTER.get().floatValue());
        } else {
            blazeFireball(player, stream.enhanced());
        }
        int interval = type == PuppetType.GHAST ? Config.PUPPETEER_GHAST_VOLLEY_INTERVAL_TICKS.get() : stream.interval();
        if (stream.left() <= 1) {
            STREAMS.remove(player.getUUID());
        } else {
            STREAMS.put(player.getUUID(), new Stream(stream.left() - 1, now + interval, stream.interval(), stream.enhanced()));
        }
    }

    /** charging a shot slows you on foot (flying, the client slows the fly speed instead — see DisguiseClient). */
    private static void chargeSlow(ServerPlayer player) {
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

    /** a blaze in a fight lights up: refreshed by hitting, being hit and firing (PUPPET_RAGE_END doubles as its timer). */
    private static void blazeCombat(ServerPlayer player) {
        player.setData(WitchModAttachments.PUPPET_RAGE_END,
                player.level().getGameTime() + Config.PUPPETEER_BLAZE_COMBAT_SECONDS.get() * 20L);
    }

    /**
     * blaze upkeep: water and rain hurt, the right-click wind-up charges (sped by Dexterous), the volley streams out,
     * and charging slows you.
     */
    private static void tickBlaze(ServerPlayer player, int ticksRemaining) {
        // keep the blaze able to fly — re-grant each tick in case anything strips mayfly (it flies like the breeze).
        if (!player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = true;
            player.setData(WitchModAttachments.PUPPET_GRANTED_FLIGHT, true);
            player.onUpdateAbilities();
        }
        if (ticksRemaining % 10 == 0 && player.isInWaterRainOrBubble()) {
            player.hurt(player.damageSources().drown(), 1.0F);
        }
        tickHold(player, PuppetType.BLAZE);
        tickStream(player, PuppetType.BLAZE);
        chargeSlow(player);
    }

    /**
     * blaze: let go of the wind-up — at least puppeteerBlazeWindupTicks or it fizzles; then a volley of its fireballs,
     * more the longer you held (min..max fireballs over the charge), one every 3 ticks.
     */
    private static void blazeRelease(ServerPlayer player, int charge) {
        int windup = Config.PUPPETEER_BLAZE_WINDUP_TICKS.get();
        if (charge > 0 && charge < windup && ready(player) && !STREAMS.containsKey(player.getUUID())) {
            // a TAP: the real blaze's burst — three of its fireballs, six ticks apart, at its own speed
            STREAMS.put(player.getUUID(), new Stream(Config.PUPPETEER_BLAZE_TAP_FIREBALLS.get(), player.level().getGameTime(), 6, false));
            cooldown(player, Config.PUPPETEER_BLAZE_VOLLEY_COOLDOWN_TICKS.get());
            blazeCombat(player);
            return;
        }
        if (charge < windup || !ready(player) || STREAMS.containsKey(player.getUUID())) {
            return;
        }
        int full = Math.max(windup + 1, Config.PUPPETEER_BLAZE_CHARGE_TICKS.get());
        float t = Mth.clamp((charge - windup) / (float) (full - windup), 0.0F, 1.0F);
        int min = Config.PUPPETEER_BLAZE_MIN_FIREBALLS.get();
        int max = Math.max(min, Config.PUPPETEER_BLAZE_MAX_FIREBALLS.get());
        int count = min + Math.round(t * (max - min));
        STREAMS.put(player.getUUID(), new Stream(count, player.level().getGameTime(), 3, true));
        cooldown(player, Config.PUPPETEER_BLAZE_VOLLEY_COOLDOWN_TICKS.get());
        blazeCombat(player);
    }

    /** one of the blaze's own small fireballs (sets things alight), with a little scatter, and its shot sound. */
    private static void blazeFireball(ServerPlayer player, boolean enhanced) {
        ServerLevel level = player.serverLevel();
        Vec3 look = scattered(player, 4.0F);
        net.minecraft.world.entity.projectile.SmallFireball fireball =
                new net.minecraft.world.entity.projectile.SmallFireball(level, player, look.scale(enhanced ? 1.4 : 1.0)); // enhanced: quicker
        Vec3 from = player.getEyePosition().add(look.scale(0.9));
        fireball.setPos(from.x, from.y - 0.2, from.z);
        level.addFreshEntity(fireball);
        level.levelEvent(null, 1018, player.blockPosition(), 0); // the blaze's shot
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /**
     * breeze upkeep: anything hostile that comes too close pushes you away on the wind (gently), the gale charges, and
     * charging slows you.
     */
    private static void tickBreeze(ServerPlayer player) {
        double r = Config.PUPPETEER_BREEZE_REPEL_RANGE.get();
        if (r > 0.0) {
            Vec3 push = Vec3.ZERO;
            for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                    e -> e != player && e.isAlive() && e.distanceToSqr(player) <= r * r
                            && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
                Vec3 away = player.position().subtract(e.position());
                push = push.add(away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.1, 0.0) : away.normalize());
            }
            if (push.lengthSqr() > 0.0) {
                player.setDeltaMovement(player.getDeltaMovement().add(push.normalize().scale(Config.PUPPETEER_BREEZE_REPEL_STRENGTH.get())));
                player.hurtMarked = true;
            }
        }
        tickHold(player, PuppetType.BREEZE);
        chargeSlow(player);
    }

    /** breeze: left-click — the real breeze's wind charge (its knockback, its gust, it trips doors and redstone). */
    private static void breezeShot(ServerPlayer player) {
        if (!ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_BREEZE_SHOT_COOLDOWN_TICKS.get());
        windCharge(player, 1.0F, false);
    }

    /** breeze: let go of the gale — at least half charged or it fizzles; then one slower, far stronger wind charge. */
    private static void breezeRelease(ServerPlayer player, int charge) {
        int full = Config.PUPPETEER_BREEZE_GALE_CHARGE_TICKS.get();
        if (charge < full / 2 || !ready(player)) {
            return;
        }
        cooldown(player, Config.PUPPETEER_BREEZE_GALE_COOLDOWN_TICKS.get());
        windCharge(player, Mth.clamp(charge / (float) full, 0.5F, 1.0F), true);
    }

    private static final String GALE_TAG = "witchmod_gale";
    /** gales in flight — they drag a howling wake of gusts behind them. */
    private static final Set<net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge> GALES = ConcurrentHashMap.newKeySet();

    /** each gale in flight leaves a trail of gusts (and a low whoosh now and then), so you see the weight of it coming. */
    private static void trailGales() {
        GALES.removeIf(gale -> {
            if (gale.isRemoved() || !(gale.level() instanceof ServerLevel level)) {
                return true;
            }
            Vec3 at = gale.position();
            level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 2, 0.25, 0.25, 0.25, 0.0);
            if (gale.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
            if (gale.tickCount % 8 == 0) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.2F, 0.5F);
            }
            return false;
        });
    }

    /** a breeze wind charge from your snout along your aim; a gale flies slower and remembers its strength (tags). */
    private static void windCharge(ServerPlayer player, float strength, boolean gale) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge charge =
                new net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE, level);
        charge.setOwner(player);
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.6));
        charge.setPos(from.x, from.y - 0.2, from.z);
        charge.shoot(look.x, look.y, look.z, gale ? 0.8F : 1.6F, 0.5F);
        if (gale) {
            charge.addTag(GALE_TAG);
            charge.addTag(GALE_TAG + ":" + Math.round(strength * 100));
        }
        level.addFreshEntity(charge);
        if (gale) {
            GALES.add(charge); // trails a howling wake (see trailGales)
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 2.0F, 0.5F);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS,
                gale ? 2.0F : 1.5F, gale ? 0.7F : 1.0F);
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /**
     * a gale lands: instead of the wind charge's own burst, a far bigger one — radius and knockback scaled by how long
     * it was charged — with the same gust and the same redstone-tripping. a direct hit also does the gale's damage.
     */
    private static boolean galeImpact(net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge charge,
                                      net.minecraft.world.phys.HitResult hit) {
        if (!charge.getTags().contains(GALE_TAG) || !(charge.level() instanceof ServerLevel level)) {
            return false;
        }
        float strength = 1.0F;
        for (String tag : charge.getTags()) {
            if (tag.startsWith(GALE_TAG + ":")) {
                strength = Integer.parseInt(tag.substring(GALE_TAG.length() + 1)) / 100.0F;
            }
        }
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() != charge.getOwner()) {
            entityHit.getEntity().hurt(charge.damageSources().windCharge(charge,
                    charge.getOwner() instanceof LivingEntity owner ? owner : null), Config.PUPPETEER_BREEZE_GALE_DAMAGE.get().floatValue() * strength);
        }
        Vec3 at = hit.getLocation();
        float radius = (float) (Config.PUPPETEER_BREEZE_GALE_RADIUS.get() * strength);
        float knockback = (float) (Config.PUPPETEER_BREEZE_GALE_KNOCKBACK.get() * strength);
        net.minecraft.world.level.ExplosionDamageCalculator calc = new net.minecraft.world.level.SimpleExplosionDamageCalculator(
                true, false, java.util.Optional.of(knockback),
                BuiltInRegistries.BLOCK.getTag(net.minecraft.tags.BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(java.util.function.Function.identity()));
        level.explode(charge, null, calc, at.x, at.y, at.z, radius, false, Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE, SoundEvents.BREEZE_WIND_CHARGE_BURST);
        charge.discard();
        return true;
    }

    // --- witch --------------------------------------------------------------------------------------

    /** the witch's potion belt — her own repertoire (what she throws, then what she drinks), one per hotbar slot. */
    private static final List<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>> WITCH_BELT = List.of(
            net.minecraft.world.item.alchemy.Potions.HARMING, net.minecraft.world.item.alchemy.Potions.POISON,
            net.minecraft.world.item.alchemy.Potions.SLOWNESS, net.minecraft.world.item.alchemy.Potions.WEAKNESS,
            net.minecraft.world.item.alchemy.Potions.HEALING, net.minecraft.world.item.alchemy.Potions.REGENERATION,
            net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE, net.minecraft.world.item.alchemy.Potions.SWIFTNESS,
            net.minecraft.world.item.alchemy.Potions.WATER_BREATHING);
    /** witches mid-swig → what they're drinking (the start tick is PUPPET_LUNGE_START, synced for the drinking pose). */
    private static final Map<UUID, net.minecraft.world.item.alchemy.PotionContents> DRINKS = new ConcurrentHashMap<>();
    private static final ResourceLocation WITCH_DRINK_SLOW_ID = EffectUtil.modifierId("puppeteer_witch_drink_slow");

    /** the belt: a potion in every hotbar slot (puppet tools — can't be dropped, swapped or kept; gone when you leave). */
    private static void giveWitchBelt(ServerPlayer player) {
        for (int i = 0; i < WITCH_BELT.size() && i < 9; i++) {
            ItemStack potion = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, WITCH_BELT.get(i));
            markPuppetTool(potion);
            player.getInventory().setItem(i, potion);
        }
        player.getInventory().selected = 0;
        player.inventoryMenu.broadcastChanges();
    }

    /** the potion picked on the belt (the held hotbar slot), or null. */
    @Nullable
    private static net.minecraft.world.item.alchemy.PotionContents pickedPotion(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return isPuppetTool(held) ? held.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) : null;
    }

    /** a witch puppet mid-swig (both sides: the drink's start is synced as PUPPET_LUNGE_START). */
    public static boolean drinking(Player player) {
        long start = player.getData(WitchModAttachments.PUPPET_LUNGE_START);
        if (start <= 0L || PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) != PuppetType.WITCH) {
            return false;
        }
        try {
            return player.level().getGameTime() < start + Config.PUPPETEER_WITCH_DRINK_TICKS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** the belt's cooldown: the server's gate, and vanilla's grey sweep across every potion on the hotbar. */
    private static void beltCooldown(ServerPlayer player, int ticks) {
        cooldown(player, ticks);
        player.getCooldowns().addCooldown(Items.POTION, ticks);
    }

    /** witch: throw the picked potion — a splash, or (held long enough) a lingering one — the way a player throws one. */
    private static void witchThrow(ServerPlayer player, boolean lingering) {
        net.minecraft.world.item.alchemy.PotionContents potion = pickedPotion(player);
        if (potion == null || !ready(player) || DRINKS.containsKey(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ItemStack flask = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
        flask.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, potion);
        net.minecraft.world.entity.projectile.ThrownPotion thrown = new net.minecraft.world.entity.projectile.ThrownPotion(level, player);
        thrown.setItem(flask);
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
        level.addFreshEntity(thrown);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0F,
                0.8F + level.random.nextFloat() * 0.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
        beltCooldown(player, lingering ? Config.PUPPETEER_WITCH_LINGER_COOLDOWN_TICKS.get() : Config.PUPPETEER_WITCH_THROW_COOLDOWN_TICKS.get());
    }

    /** witch: right-click — start drinking the picked potion (the witch's swig, slowed; it works when you finish). */
    private static void witchDrink(ServerPlayer player) {
        net.minecraft.world.item.alchemy.PotionContents potion = pickedPotion(player);
        if (potion == null || DRINKS.containsKey(player.getUUID()) || !ready(player)) {
            return;
        }
        DRINKS.put(player.getUUID(), potion);
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, player.level().getGameTime());
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, WITCH_DRINK_SLOW_ID, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 1.0F,
                0.8F + player.getRandom().nextFloat() * 0.4F);
    }

    /** witch upkeep: finish a swig (the potion takes effect, as drinking one would), and charge a lingering throw. */
    private static void tickWitch(ServerPlayer player) {
        net.minecraft.world.item.alchemy.PotionContents drinking = DRINKS.get(player.getUUID());
        if (drinking != null && player.level().getGameTime()
                >= player.getData(WitchModAttachments.PUPPET_LUNGE_START) + Config.PUPPETEER_WITCH_DRINK_TICKS.get()) {
            DRINKS.remove(player.getUUID());
            player.setData(WitchModAttachments.PUPPET_LUNGE_START, 0L);
            EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, WITCH_DRINK_SLOW_ID);
            drinking.forEachEffect(effect -> {
                if (effect.getEffect().value().isInstantenous()) {
                    effect.getEffect().value().applyInstantenousEffect(player, player, player, effect.getAmplifier(), 1.0);
                } else {
                    player.addEffect(effect);
                }
            });
            beltCooldown(player, Config.PUPPETEER_WITCH_DRINK_COOLDOWN_TICKS.get());
        }
        if (!DRINKS.containsKey(player.getUUID())) {
            tickHold(player, PuppetType.WITCH); // the right-click charge: let go early for a splash, late for a lingering potion
        }
    }

    // --- camel --------------------------------------------------------------------------------------

    /**
     * camel: let go of the dash charge — the real camel's dash (22.2222 x scale x its speed forward, 1.4285 x scale x its
     * jump up), boosted, on vanilla's jump-bar scale (a tap 40%, a full bar the whole thing).
     */
    private static void camelDash(ServerPlayer player, int charge) {
        if (!ready(player) || charge <= 0) {
            return;
        }
        int full = Config.PUPPETEER_CAMEL_DASH_CHARGE_TICKS.get();
        float scale = charge >= full ? 1.0F : 0.4F + 0.4F * charge / (float) full;
        double boost = Config.PUPPETEER_CAMEL_DASH_BOOST.get();
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
        double forward = 22.2222 * scale * 0.09 * boost; // vanilla: x the camel's movement speed (0.09)
        double up = 1.4285 * scale * 0.42; // vanilla: x the camel's jump power
        player.setDeltaMovement(look.x * forward, up, look.z * forward);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CAMEL_DASH, SoundSource.PLAYERS, 1.0F, 1.0F);
        cooldown(player, Config.PUPPETEER_CAMEL_DASH_COOLDOWN_TICKS.get());
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    // --- goat ---------------------------------------------------------------------------------------

    /** a ram's power as 0..1 → its share of the full speed / length / knockback (a tap is about a third). */
    private static double ramShare(Player player) {
        return 0.33 + 0.67 * Mth.clamp(player.getData(WitchModAttachments.PUPPET_DASH_POWER) / 100.0, 0.0, 1.0);
    }

    public static int ramTicks(Player player) {
        return Math.max(4, (int) Math.round(Config.PUPPETEER_GOAT_RAM_TICKS.get() * ramShare(player)));
    }

    public static double ramSpeed(Player player) {
        return Config.PUPPETEER_GOAT_RAM_SPEED.get() * ramShare(player);
    }

    /** goat upkeep: the ram's charge (right-click) or a screaming goat's shriek (left-click), the ram itself, the slow. */
    private static void tickGoat(ServerPlayer player, PuppetType type) {
        boolean shrieking = type == PuppetType.SCREAMING_GOAT && ATTACK_HELD.contains(player.getUUID());
        if (shrieking) {
            if (ready2(player)) {
                int cap = Config.PUPPETEER_GOAT_SHRIEK_CHARGE_TICKS.get();
                int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
                if (hold == 0) {
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5F, 1.4F);
                }
                if (hold < cap) {
                    player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(cap, hold + chargeStep(player)));
                }
            }
        } else if (!LUNGES.containsKey(player.getUUID())) {
            tickHold(player, type);
        }
        tickLunge(player, type);
        chargeSlow(player);
    }

    /** goat: let go of the ram — off it goes (your client drives it, scaled by the charge: PUPPET_DASH_POWER). */
    private static void goatRam(ServerPlayer player, PuppetType type, int charge) {
        if (charge < 5 || !ready(player) || LUNGES.containsKey(player.getUUID())) {
            return;
        }
        int power = (int) Math.round(Mth.clamp(charge / (float) Config.PUPPETEER_GOAT_RAM_CHARGE_TICKS.get(), 0.0F, 1.0F) * 100);
        long now = player.level().getGameTime();
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, power);
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        cooldown(player, Config.PUPPETEER_GOAT_RAM_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GOAT_STEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** the ram connects: no damage at all — just an absurd launch the way you were going. it stops the ram. */
    private static void tickRam(ServerPlayer player, PuppetType type) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.4),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            double kb = Config.PUPPETEER_GOAT_RAM_KNOCKBACK.get() * ramShare(player)
                    * Math.max(0.3, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            e.setDeltaMovement(dir.x * kb, 0.4 + kb * 0.15, dir.z * kb);
            e.hurtMarked = true;
            e.resetFallDistance();
            player.serverLevel().playSound(null, e.blockPosition(),
                    type == PuppetType.SCREAMING_GOAT ? SoundEvents.GOAT_SCREAMING_RAM_IMPACT : SoundEvents.GOAT_RAM_IMPACT,
                    SoundSource.PLAYERS, 1.5F, 1.0F);
            endLunge(player);
            return;
        }
    }

    /**
     * screaming goat: let go of a FULLY charged shriek — a warden-style sonic boom straight down your aim that goes
     * through walls, does no damage, and blasts everything in its path away with one insanely high knockback, to the
     * goat's own scream.
     */
    private static void goatShriek(ServerPlayer player, int charge) {
        if (charge < Config.PUPPETEER_GOAT_SHRIEK_CHARGE_TICKS.get() || !ready2(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double range = Config.PUPPETEER_GOAT_SHRIEK_RANGE.get();
        for (int i = 1; i <= range; i++) {
            Vec3 at = eye.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        double kb = Config.PUPPETEER_GOAT_SHRIEK_KNOCKBACK.get();
        Vec3 end = eye.add(dir.scale(range));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, end).inflate(1.5),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(dir);
            if (along < 0.0 || along > range || to.subtract(dir.scale(along)).lengthSqr() > 2.25) {
                continue; // not in the blast's path
            }
            e.setDeltaMovement(dir.x * kb, Math.max(0.6, dir.y * kb + 0.6), dir.z * kb);
            e.hurtMarked = true;
            e.resetFallDistance();
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.2F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.PLAYERS, 3.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_SCREAMING_RAM_IMPACT, SoundSource.PLAYERS, 2.0F, 0.6F);
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, level.getGameTime() + Config.PUPPETEER_GOAT_SHRIEK_COOLDOWN_TICKS.get());
    }

    // --- ravager ------------------------------------------------------------------------------------

    /** left-click on something in reach: a ravager's bite doesn't land at once — its head pokes out a moment later. */
    private static void startRavagerBite(ServerPlayer player, LivingEntity target) {
        if (BITES.containsKey(player.getUUID())) {
            return; // one bite in flight at a time — the head-poke paces it
        }
        BITES.put(player.getUUID(), new Bite(target, player.level().getGameTime() + Config.PUPPETEER_RAVAGER_BITE_DELAY_TICKS.get()));
    }

    /**
     * ravager upkeep: lands the delayed bite when its head-poke delay is up, and tramples leaves it pushes through
     * (like a real ravager, gated on mobGriefing).
     */
    private static void tickRavager(ServerPlayer player) {
        Bite bite = BITES.get(player.getUUID());
        if (bite != null && player.level().getGameTime() >= bite.at()) {
            BITES.remove(player.getUUID());
            LivingEntity target = bite.target();
            double reach = PuppetType.RAVAGER.reach() + 2.0; // a little slack — the head pokes out and they may have shifted
            if (target.isAlive() && target.level() == player.level() && player.distanceToSqr(target) <= reach * reach) {
                target.hurt(player.damageSources().playerAttack(player), 1.0F); // onDamageFirst sets the ravager's bite damage
                Vec3 away = target.position().subtract(player.position());
                away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
                double kb = Config.PUPPETEER_RAVAGER_BITE_KNOCKUP.get();
                target.setDeltaMovement(target.getDeltaMovement().add(away.x * kb, kb, away.z * kb)); // tossed up and back
                target.hurtMarked = true;
                ravagerRally(player, target); // the raid piles onto your quarry
            }
        }
        if (player.horizontalCollision && player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            trampleLeaves(player);
        }
    }

    /** break any leaves the ravager is pushing through (vanilla's own ravager behaviour). */
    private static void trampleLeaves(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(0.2);
        for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES)) {
                level.destroyBlock(pos, true, player);
            }
        }
    }

    /**
     * ravager: let go of a charged ROAR — everything around you is flung away and hurt, scaled by how long you charged
     * (your view shook and zoomed as it built). the ravager's own roar sound + animation.
     */
    private static void ravagerRoar(ServerPlayer player, int charge) {
        if (!ready(player)) {
            return;
        }
        int full = Config.PUPPETEER_RAVAGER_ROAR_CHARGE_TICKS.get();
        float frac = Mth.clamp(charge / (float) full, 0.0F, 1.0F);
        if (charge < full / 4) {
            return; // too brief to be a roar — nothing spent
        }
        ServerLevel level = player.serverLevel();
        double radius = Config.PUPPETEER_RAVAGER_ROAR_RADIUS.get() * frac;
        double kb = Config.PUPPETEER_RAVAGER_ROAR_KNOCKBACK.get() * frac;
        float dmg = Config.PUPPETEER_RAVAGER_ROAR_DAMAGE.get().floatValue() * frac;
        int stun = Math.round(Config.PUPPETEER_RAVAGER_ROAR_STUN_TICKS.get() * frac);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceToSqr(player) <= radius * radius)) {
            if (e instanceof net.minecraft.world.entity.raid.Raider) {
                continue; // a ravager never hurts its own — pillagers, vindicators, evokers, witches, other ravagers
            }
            Vec3 away = e.position().subtract(player.position());
            away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
            double power = kb * Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            e.setDeltaMovement(away.x * power, 0.5 + power * 0.5, away.z * power);
            e.hurtMarked = true;
            e.resetFallDistance();
            if (dmg > 0.0F) {
                SPECIAL_DAMAGE.add(player.getUUID());
                try {
                    e.hurt(player.damageSources().playerAttack(player), dmg);
                } finally {
                    SPECIAL_DAMAGE.remove(player.getUUID());
                }
            }
            if (stun > 0) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 3, false, false));
            }
        }
        // the bellow, gone all out: a central blast + expanding ground rings + a sweep ring + smoke billowing out
        Vec3 c = player.position();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.8, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.8, c.z, 6, 0.6, 0.5, 0.6, 0.0);
        for (double rr = 1.0; rr <= radius; rr += 1.0) {
            int steps = (int) Math.max(8, rr * 8);
            for (int i = 0; i < steps; i++) {
                double a = i * 2.0 * Math.PI / steps;
                double px = c.x + Math.cos(a) * rr;
                double pz = c.z + Math.sin(a) * rr;
                level.sendParticles(ParticleTypes.POOF, px, c.y + 0.2, pz, 1, 0.0, 0.02, 0.0, 0.02);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, c.y + 0.1, pz, 1, 0.0, 0.01, 0.0, 0.0);
                if (rr <= 2.0) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, px, c.y + 0.9, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0F, 1.0F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 1.2F, 0.7F);
        // nudge the action counter so every client plays the dummy's roar animation
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
        cooldown(player, Config.PUPPETEER_RAVAGER_ROAR_COOLDOWN_TICKS.get());
    }

    /** nearby pillagers/illagers pile onto whatever the ravager just bit — a soft rally (no glow, don't override a target). */
    private static void ravagerRally(ServerPlayer player, LivingEntity prey) {
        double r = Config.PUPPETEER_RAVAGER_RALLY_RADIUS.get();
        if (r <= 0.0) {
            return;
        }
        for (net.minecraft.world.entity.monster.AbstractIllager illager : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.AbstractIllager.class, player.getBoundingBox().inflate(r),
                i -> i.isAlive() && (i.getTarget() == null || !i.getTarget().isAlive()))) {
            illager.setTarget(prey);
        }
    }

    // --- vex ----------------------------------------------------------------------------------------

    /** vex upkeep: it never really lands (so it stays airborne + phasing), and it's temporary — an action-bar timer. */
    private static void tickVex(ServerPlayer player) {
        if (player.getAbilities().mayfly && !player.getAbilities().flying) {
            player.getAbilities().flying = true; // kept aloft (and so always phasing)
            player.onUpdateAbilities();
        }
        tickLunge(player, PuppetType.VEX); // the lunge's contact check
        long now = player.level().getGameTime();
        long end = VEX_END.computeIfAbsent(player.getUUID(), k -> now + Config.PUPPETEER_VEX_DURATION_TICKS.get());
        long left = end - now;
        if (left <= 0) {
            VEX_END.remove(player.getUUID());
            player.serverLevel().sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.VEX_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
            release(player, null, false); // the vex dissolves and lets you go
            return;
        }
        if (now % 4 == 0) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.vex_timer", (int) Math.ceil(left / 20.0))
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** left-click: a short charged dash — the vex's only way to hit (it can't during the cooldown). */
    private static void startVexLunge(ServerPlayer player) {
        if (!ready(player) || LUNGES.containsKey(player.getUUID())) {
            return;
        }
        long now = player.level().getGameTime();
        cooldown(player, Config.PUPPETEER_VEX_LUNGE_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** the dash's contact: the first thing it reaches takes the vex's hit, then the lunge ends. */
    private static void tickVexLunge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            SPECIAL_DAMAGE.add(player.getUUID());
            try {
                e.hurt(player.damageSources().playerAttack(player), Config.PUPPETEER_VEX_ATTACK.get().floatValue());
            } finally {
                SPECIAL_DAMAGE.remove(player.getUUID());
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            level.playSound(null, e.blockPosition(), SoundEvents.VEX_HURT, SoundSource.PLAYERS, 1.0F, 1.3F);
            endLunge(player);
            return;
        }
    }

    /** right-click: just the annoying vex giggle. */
    private static void vexLaugh(ServerPlayer player) {
        if (!ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_VEX_LAUGH_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.VEX_AMBIENT, SoundSource.PLAYERS, 1.3F, 1.0F);
    }

    // --- allay --------------------------------------------------------------------------------------

    /** allay upkeep: keep its free flight granted (no sprint-fly, enforced client-side), and build the mitosis charge. */
    private static void tickAllay(ServerPlayer player) {
        if (!player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            grantBatFlight(player);
        }
        // the mitosis charge builds ONLY while you hold right-click within earshot of a playing jukebox (and off cooldown);
        // let go (or move out of earshot) at a full charge to split. a short hold instead does the tap action (fireHeld).
        boolean holding = HELD.contains(player.getUUID());
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (holding && ready2(player) && nearMusic(player)) {
            if (hold == 0) {
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(hold + 1, Config.PUPPETEER_ALLAY_MITOSIS_CHARGE_TICKS.get()));
        } else if (hold != 0 && !holding) {
            player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        }
    }

    /** whether a playing jukebox is within earshot (the allay needs music to split). */
    private static boolean nearMusic(ServerPlayer player) {
        int r = (int) Math.ceil(Config.PUPPETEER_ALLAY_MUSIC_RADIUS.get());
        BlockPos at = player.blockPosition();
        net.minecraft.world.level.Level level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.JUKEBOX)
                    && state.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) {
                return true;
            }
        }
        return false;
    }

    /** a quick right-click: take one random item from a public container you're eyeing, else pick up / drop ground items. */
    private static void allayTap(ServerPlayer player) {
        net.minecraft.world.phys.HitResult hit = player.pick(5.0, 1.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
            net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(block.getBlockPos());
            // a public container (chests, barrels, hoppers, furnaces...) — ender chests aren't Containers, so they're out
            if (be instanceof net.minecraft.world.Container container && !container.isEmpty()) {
                takeRandomItem(player, container);
                return;
            }
        }
        allayPickupOrDrop(player);
    }

    /** pull one item out of a random non-empty slot and hand it to the allay (held), or drop it if the allay's hands are full. */
    private static void takeRandomItem(ServerPlayer player, net.minecraft.world.Container container) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        int slot = slots.get(player.getRandom().nextInt(slots.size()));
        ItemStack taken = container.removeItem(slot, 1);
        if (taken.isEmpty()) {
            return;
        }
        container.setChanged();
        if (allayHeld(player).isEmpty()) {
            setAllayHeld(player, taken);
        } else if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** right-click with nothing to take: drop what the allay holds, or pick up the nearest ground item (up to a stack). */
    private static void allayPickupOrDrop(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ItemStack held = allayHeld(player);
        if (!held.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                    player.getX(), player.getY() + 0.5, player.getZ(), held);
            drop.setDeltaMovement(player.getLookAngle().scale(0.2));
            drop.setPickUpDelay(20);
            level.addFreshEntity(drop);
            setAllayHeld(player, ItemStack.EMPTY);
            level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        double r = Config.PUPPETEER_ALLAY_PICKUP_RADIUS.get();
        net.minecraft.world.entity.item.ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(r), i -> i.isAlive() && !i.getItem().isEmpty())) {
            double d = item.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        if (nearest == null) {
            return;
        }
        ItemStack stack = nearest.getItem();
        ItemStack take = stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
        setAllayHeld(player, take);
        stack.shrink(take.getCount());
        if (stack.isEmpty()) {
            nearest.discard();
        } else {
            nearest.setItem(stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** near music, a full hold splits off a real allay copy (carrying a copy of what you hold), on a long cooldown. */
    private static void allayMitosis(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                level.getGameTime() + Config.PUPPETEER_ALLAY_MITOSIS_COOLDOWN_TICKS.get());
        net.minecraft.world.entity.animal.allay.Allay clone = EntityType.ALLAY.create(level);
        if (clone != null) {
            clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            ItemStack held = allayHeld(player);
            if (!held.isEmpty()) {
                clone.setItemInHand(InteractionHand.MAIN_HAND, held.copyWithCount(1));
            }
            level.addFreshEntity(clone);
        }
        level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.4, 0.4, 1.0);
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
    }

    private static final String ALLAY_HELD_KEY = "witchmod_allay_held";

    public static ItemStack allayHeld(Player player) {
        CompoundTag data = player.getData(WitchModAttachments.PUPPET_DATA);
        if (!data.contains(ALLAY_HELD_KEY, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(player.registryAccess(), data.getCompound(ALLAY_HELD_KEY));
    }

    private static void setAllayHeld(Player player, ItemStack stack) {
        CompoundTag data = player.getData(WitchModAttachments.PUPPET_DATA);
        if (stack.isEmpty()) {
            data.remove(ALLAY_HELD_KEY);
        } else {
            data.put(ALLAY_HELD_KEY, stack.save(player.registryAccess()));
        }
        player.setData(WitchModAttachments.PUPPET_DATA, data);
    }

    /** a camel puppet (both sides — EntityCamelSeatMixin seats its two riders like the real camel's). */
    public static boolean camelPuppet(Player player) {
        return player.hasData(WitchModAttachments.PUPPET_TYPE)
                && PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.CAMEL;
    }

    // --- pillager -----------------------------------------------------------------------------------

    /**
     * pillager: a real crossbow in every hotbar slot (unbreakable puppet tools) and a quiver that never runs dry — so
     * loading, firing and every crossbow blessing work exactly as normal. its bolts are boosted in onArrowSpawn.
     */
    private static void giveCrossbowKit(ServerPlayer player) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
        markPuppetTool(crossbow);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, crossbow.copy());
        }
        refillQuiver(player);
    }

    /** the pillager's bottomless quiver: a puppet-tool stack of arrows, topped back up as it's used. */
    private static void refillQuiver(ServerPlayer player) {
        ItemStack quiver = player.getInventory().getItem(9);
        if (quiver.is(Items.ARROW) && isPuppetTool(quiver) && quiver.getCount() >= 32) {
            return;
        }
        ItemStack arrows = new ItemStack(Items.ARROW, 64);
        markPuppetTool(arrows);
        player.getInventory().setItem(9, arrows);
        player.inventoryMenu.broadcastChanges();
    }

    // --- fox ----------------------------------------------------------------------------------------

    private static final ResourceLocation FOX_SPRINT_ID = EffectUtil.modifierId("puppeteer_fox_sprint");
    /** fox puppets → ticks they've held food in their mouth (eaten at puppeteerFoxEatTicks). */
    private static final Map<UUID, Integer> FOX_CHEWING = new ConcurrentHashMap<>();

    /** what the fox puppet has in its mouth (the real fox's main-hand item, kept in its puppet data). */
    private static ItemStack foxHeld(ServerPlayer player) {
        ListTag hands = player.getData(WitchModAttachments.PUPPET_DATA).getList("HandItems", Tag.TAG_COMPOUND);
        return hands.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(player.registryAccess(), hands.getCompound(0));
    }

    private static void setFoxHeld(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        ListTag hands = tag.getList("HandItems", Tag.TAG_COMPOUND);
        while (hands.size() < 2) {
            hands.add(new CompoundTag());
        }
        hands.set(0, stack.isEmpty() ? new CompoundTag() : stack.save(player.registryAccess()));
        tag.put("HandItems", hands);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        FOX_CHEWING.remove(player.getUUID());
    }

    /**
     * fox upkeep: a little extra sprint, total silence while crouching, picking things up in its mouth (one item,
     * like the real fox), eating food it's held a while, and the pounce.
     */
    private static void tickFox(ServerPlayer player) {
        boolean sprinting = player.isSprinting();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && sprinting != speed.hasModifier(FOX_SPRINT_ID)) {
            if (sprinting) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FOX_SPRINT_ID, Config.PUPPETEER_FOX_SPRINT_BONUS.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(FOX_SPRINT_ID);
            }
        }
        if (player.isSilent() != player.isCrouching()) {
            player.setSilent(player.isCrouching()); // a fox stalking is a fox you don't hear
        }
        ServerLevel level = player.serverLevel();
        ItemStack held = foxHeld(player);
        if (held.isEmpty()) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(0.8, 0.0, 0.8),
                    i -> i.isAlive() && !i.hasPickUpDelay() && !i.getItem().isEmpty())) {
                // one in the mouth, the rest left lying — the real fox's pick-up
                setFoxHeld(player, item.getItem().split(1));
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
                break;
            }
        } else if (held.has(net.minecraft.core.component.DataComponents.FOOD) && Config.PUPPETEER_FOX_EAT_TICKS.get() > 0) {
            int chew = FOX_CHEWING.merge(player.getUUID(), 1, Integer::sum);
            if (chew % 5 == 0) {
                level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, held),
                        player.getX(), player.getEyeY(), player.getZ(), 3, 0.15, 0.1, 0.15, 0.05);
            }
            if (chew >= Config.PUPPETEER_FOX_EAT_TICKS.get()) {
                net.minecraft.world.food.FoodProperties food = held.get(net.minecraft.core.component.DataComponents.FOOD);
                scaleHeal(player, food.nutrition()); // the fox mends on it, as the real one does
                player.getFoodData().eat(food.nutrition(), food.saturation());
                level.playSound(null, player.blockPosition(), SoundEvents.FOX_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
                setFoxHeld(player, ItemStack.EMPTY);
            }
        }
        tickLunge(player, PuppetType.FOX);
    }

    /** fox: let go of what's in your mouth (the drop key), tossed a little way ahead like a dropped item. */
    public static void foxDrop(ServerPlayer player) {
        if (possessed(player) != PuppetType.FOX) {
            return;
        }
        ItemStack held = foxHeld(player);
        if (held.isEmpty()) {
            return;
        }
        setFoxHeld(player, ItemStack.EMPTY);
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), held);
        dropped.setDeltaMovement(player.getLookAngle().scale(0.25));
        dropped.setPickUpDelay(40);
        player.level().addFreshEntity(dropped);
    }

    /** fox: Pounce (right-click) — a short rush (the lunge machinery, your client drives it). */
    private static void foxPounce(ServerPlayer player) {
        if (LUNGES.containsKey(player.getUUID())) {
            return;
        }
        long now = player.level().getGameTime();
        cooldown(player, Config.PUPPETEER_FOX_POUNCE_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FOX_AGGRO, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * the pounce reaches someone holding something: it's yours now — snatched from their hand into your mouth (and
     * whatever you were carrying is dropped for it). no one to rob: it bites the first thing it reaches. the rush stops.
     */
    private static void tickMug(ServerPlayer player) {
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.getMainHandItem().isEmpty())) {
            ItemStack loot = e.getMainHandItem().copy();
            e.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (e instanceof ServerPlayer victim) {
                victim.inventoryMenu.broadcastChanges();
            }
            foxDrop(player); // swap: what you had falls where you snatched
            setFoxHeld(player, loot);
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.FOX_BITE, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F);
            endLunge(player);
            return;
        }
        // nothing to steal: it bites the first thing it reaches instead
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable()
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            e.hurt(player.damageSources().playerAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.FOX_BITE, SoundSource.PLAYERS, 1.0F, 1.0F);
            endLunge(player);
            return;
        }
    }

    // --- the mob's own weapon ------------------------------------------------------------------------

    /** the weapon the possessed mob was holding (its main hand, from the puppet data) — empty if none. */
    private static ItemStack mobWeapon(ServerPlayer player, CompoundTag tag) {
        ListTag hands = tag.getList("HandItems", Tag.TAG_COMPOUND);
        return hands.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(player.registryAccess(), hands.getCompound(0));
    }

    /** the mob's weapon enchantments carry onto your puppet bow / crossbow (a Power bow stays a Power bow). */
    private static void copyEnchantments(ItemStack from, ItemStack onto) {
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments = from.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (enchantments == null) {
            return;
        }
        for (var entry : enchantments.entrySet()) {
            if (onto.getEnchantments().getLevel(entry.getKey()) < entry.getIntValue()) {
                onto.enchant(entry.getKey(), entry.getIntValue());
            }
        }
    }

    // --- vindicator ---------------------------------------------------------------------------------

    /** a vindicator named Johnny (the real one's flag, or just the name) — the berserker. */
    public static boolean johnny(Player player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VINDICATOR
                && (tag.getBoolean("Johnny") || tag.getString("CustomName").contains("\"Johnny\""));
    }

    private static final ResourceLocation JOHNNY_SPEED_ID = EffectUtil.modifierId("puppeteer_johnny_speed");

    /** vindicator stats: the brute's weapon-style swing speed; Johnny's faster swing and extra pace on top. */
    private static void applyVindicatorStats(ServerPlayer player) {
        boolean berserk = johnny(player);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            double speed = berserk ? Config.PUPPETEER_JOHNNY_ATTACK_SPEED.get() : Config.PUPPETEER_VINDICATOR_ATTACK_SPEED.get();
            EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, GOLEM_ATTACK_SPEED_ID, speed - attackSpeed.getBaseValue(),
                    AttributeModifier.Operation.ADD_VALUE);
        }
        if (berserk) {
            EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, JOHNNY_SPEED_ID, Config.PUPPETEER_JOHNNY_BONUS.get(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /**
     * Johnny can't help himself: whenever his swing is charged and something living is within reach and in sight, he
     * swings at it — anything at all, as the real Johnny does.
     */
    private static void tickJohnny(ServerPlayer player) {
        if (!johnny(player) || player.getAttackStrengthScale(0.5F) < 1.0F || player.isSpectator()) {
            return;
        }
        double reach = player.entityInteractionRange();
        LivingEntity nearest = null;
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable()
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                        && player.hasLineOfSight(e))) {
            if (nearest == null || e.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = e;
            }
        }
        if (nearest != null && nearest.distanceToSqr(player) <= reach * reach) {
            player.attack(nearest);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /** vindicator upkeep: the committed Lunge (charge while held, dash + heavy hit on release) and Johnny's auto-swings. */
    private static void tickVindicator(ServerPlayer player) {
        boolean lunging = LUNGES.containsKey(player.getUUID());
        if (!lunging && !recovering(player)) {
            tickHold(player, PuppetType.VINDICATOR); // builds the charge while right-click is held
        }
        tickLunge(player, PuppetType.VINDICATOR);
        vindicatorChargeSlow(player); // a light wind-up slow — not enough to make sprinting into the lunge clunky
        if (!lunging && !recovering(player)) {
            tickJohnny(player);
        }
    }

    /** the vindicator's wind-up slow — a gentler, configurable version of chargeSlow so sprinting still feels responsive. */
    private static void vindicatorChargeSlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double amount = Config.PUPPETEER_VINDICATOR_LUNGE_CHARGE_SLOW.get();
        boolean slow = amount > 0.0 && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
        if (slow != speed.hasModifier(FUSE_SLOW_ID)) {
            if (slow) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(FUSE_SLOW_ID);
            }
        }
    }

    /** whether a vindicator is in the brief lockout after a MISSED lunge — the commit. */
    private static boolean recovering(ServerPlayer player) {
        Long until = RECOVER_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /**
     * vindicator: let go of the committed Lunge — a forward dash your own client drives (see PuppeteerClient.tickLunge).
     * the first thing it reaches takes a charge-scaled heavy blow; a miss costs a recovery. Johnny's lunge is enhanced.
     */
    private static void vindicatorLunge(ServerPlayer player, int charge) {
        if (charge < MIN_CHARGE || !ready(player) || LUNGES.containsKey(player.getUUID()) || recovering(player)) {
            return;
        }
        int power = (int) Math.round(Mth.clamp(charge / (float) Config.PUPPETEER_VINDICATOR_LUNGE_CHARGE_TICKS.get(), 0.0F, 1.0F) * 100);
        double max = johnny(player) ? Config.PUPPETEER_JOHNNY_LUNGE_BONUS_MAX.get() : Config.PUPPETEER_VINDICATOR_LUNGE_BONUS_MAX.get();
        float bonus = (float) Mth.lerp(power / 100.0, Config.PUPPETEER_VINDICATOR_LUNGE_BONUS_MIN.get(), max);
        long now = player.level().getGameTime();
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, power);
        LUNGE_BONUS.put(player.getUUID(), bonus);
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** the lunge connects: the vindicator's base hit × the charge bonus (+ Johnny, + the axe's damage enchants), with weight. */
    private static void vindicatorLungeHit(ServerPlayer player, LivingEntity e) {
        ServerLevel level = player.serverLevel();
        float amount = PuppetType.VINDICATOR.attackDamage() * LUNGE_BONUS.getOrDefault(player.getUUID(), 1.5F);
        if (johnny(player)) {
            amount *= 1.0F + Config.PUPPETEER_JOHNNY_BONUS.get().floatValue();
        }
        ItemStack weapon = mobWeapon(player, player.getData(WitchModAttachments.PUPPET_DATA));
        if (!weapon.isEmpty()) {
            amount = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level, weapon, e,
                    player.damageSources().playerAttack(player), amount);
        }
        LUNGE_BONUS.remove(player.getUUID());
        SPECIAL_DAMAGE.add(player.getUUID());
        try {
            e.hurt(player.damageSources().playerAttack(player), amount);
        } finally {
            SPECIAL_DAMAGE.remove(player.getUUID());
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        double kb = 0.5 * Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        e.setDeltaMovement(dir.x * kb, Math.max(e.getDeltaMovement().y, 0.0) + kb * 0.3, dir.z * kb);
        e.hurtMarked = true;
        level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    // --- wolf ---------------------------------------------------------------------------------------

    private static final ResourceLocation WOLF_FRENZY_ATTACK_ID = EffectUtil.modifierId("puppeteer_wolf_frenzy_attack");
    /** one fading scent footprint where a mob walked; the prey's (whoever hit a frenzied wolf) burn brighter/red. */
    private record ScentPrint(double x, double y, double z, long tick, boolean prey) {}
    /** each wolf puppet's scent trail — re-sent to its own client so it lingers (a limited Bloodhound). */
    private static final Map<UUID, List<ScentPrint>> WOLF_SCENT = new ConcurrentHashMap<>();
    /** a frenzied wolf → the mob that last hit it, whose scent is burned in bright for the pursuit. */
    private static final Map<UUID, UUID> WOLF_PREY = new ConcurrentHashMap<>();

    /** whether a wolf puppet is rabid (PUPPET_RAGE_END doubles as the frenzy-until tick, synced for the angry render). */
    private static boolean frenzied(ServerPlayer player) {
        return possessed(player) == PuppetType.WOLF
                && player.level().getGameTime() < player.getData(WitchModAttachments.PUPPET_RAGE_END);
    }

    /** a hit sends a wolf rabid: Darkness, a faster bite, a pursuit Maul on right-click, the attacker's scent burned bright. */
    private static void wolfFrenzy(ServerPlayer player, LivingEntity attacker) {
        int ticks = Config.PUPPETEER_WOLF_FRENZY_TICKS.get();
        if (ticks <= 0) {
            return;
        }
        boolean fresh = !frenzied(player);
        player.setData(WitchModAttachments.PUPPET_RAGE_END, player.level().getGameTime() + ticks);
        WOLF_PREY.put(player.getUUID(), attacker.getUUID());
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, false, false));
        if (fresh) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.3F, 0.7F);
        }
    }

    /** wolf upkeep: a faster bite while rabid (and the rabid motes), plus the always-on scent sense. */
    private static void tickWolf(ServerPlayer player) {
        boolean frenzy = frenzied(player);
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atk != null) {
            boolean has = atk.hasModifier(WOLF_FRENZY_ATTACK_ID);
            if (frenzy && !has) {
                EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, WOLF_FRENZY_ATTACK_ID,
                        Config.PUPPETEER_WOLF_FRENZY_ATTACK_SPEED.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else if (!frenzy && has) {
                atk.removeModifier(WOLF_FRENZY_ATTACK_ID);
                WOLF_PREY.remove(player.getUUID());
            }
        }
        // no public rabid FX (red motes / bristle) — it's a disguise; the frenzy shows only in your own faster bite + maul.
        wolfScent(player, frenzy);
    }

    /** the scent sense: nearby living mobs leave fading footprints shown to YOU only; the prey's burn bright red. */
    private static void wolfScent(ServerPlayer player, boolean frenzy) {
        long now = player.level().getGameTime();
        List<ScentPrint> trail = WOLF_SCENT.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        int linger = Config.PUPPETEER_WOLF_SCENT_LINGER_TICKS.get();
        if (now % Config.PUPPETEER_WOLF_SCENT_INTERVAL.get() == 0) {
            double r = Config.PUPPETEER_WOLF_SCENT_RADIUS.get();
            UUID preyId = WOLF_PREY.get(player.getUUID());
            for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                    e -> e != player && e.isAlive() && !e.isSpectator() && e.onGround())) {
                trail.add(new ScentPrint(e.getX(), e.getY() + 0.06, e.getZ(), now, frenzy && e.getUUID().equals(preyId)));
            }
        }
        trail.removeIf(p -> now - p.tick() > linger);
        while (trail.size() > 300) {
            trail.remove(0);
        }
        if (now % 4 != 0) {
            return; // re-send a few times a second — enough to linger, not a flood
        }
        net.minecraft.core.particles.DustParticleOptions normal =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.4F, 0.75F, 1.0F), 1.6F);
        net.minecraft.core.particles.DustParticleOptions prey =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.05F, 0.05F), 2.4F);
        for (ScentPrint p : trail) {
            // a clear marker on the ground, plus a short rising wisp, so a trail reads at a glance (the prey's a bold red).
            player.serverLevel().sendParticles(player, p.prey() ? prey : normal, false, p.x(), p.y(), p.z(), 1, 0.0, 0.0, 0.0, 0.0);
            player.serverLevel().sendParticles(player, p.prey() ? prey : normal, false, p.x(), p.y() + 0.45, p.z(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // --- cat ----------------------------------------------------------------------------------------

    /** cat puppets → firework rocket ids near them last tick; one vanishing means it went off → a forced Scare. */
    private static final Map<UUID, Set<Integer>> CAT_ROCKETS = new ConcurrentHashMap<>();

    /** left-click: a meow (its own light cooldown on ACTION2). */
    private static void catMeow(ServerPlayer player) {
        if (!ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_CAT_MEOW_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CAT_AMBIENT, SoundSource.PLAYERS,
                1.2F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /** the startled cat: flung backwards and up with a loud comical hiss, like a cat that's just been spooked. */
    private static void catScared(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize().scale(Config.PUPPETEER_CAT_SCARE_BACK.get());
        player.setDeltaMovement(back.x, Config.PUPPETEER_CAT_SCARE_UP.get(), back.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.CAT_HISS, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.CAT_AMBIENT, SoundSource.PLAYERS, 1.5F, 1.6F);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
    }

    /** cat upkeep: scaring creepers off, and being spooked airborne by a nearby firework going off. */
    private static void tickCat(ServerPlayer player) {
        scareCreepers(player);
        catLoudScare(player);
    }

    /** nearby creepers deflate and back away, as a real cat scares them. */
    private static void scareCreepers(ServerPlayer player) {
        double r = Config.PUPPETEER_CAT_CREEPER_SCARE_RADIUS.get();
        if (r <= 0 || player.tickCount % 5 != 0) {
            return;
        }
        for (net.minecraft.world.entity.monster.Creeper creeper : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Creeper.class, player.getBoundingBox().inflate(r), LivingEntity::isAlive)) {
            creeper.setTarget(null);
            creeper.setSwellDir(-1); // stop fusing and deflate
            // actually RUN away, like vanilla's AvoidEntityGoal: path to a spot away from the cat, fast.
            Vec3 flee = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(creeper, 16, 7, player.position());
            if (flee != null) {
                creeper.getNavigation().moveTo(flee.x, flee.y, flee.z, 1.4);
            }
        }
    }

    /** a firework rocket going off nearby spooks the cat (explosions are handled in onExplosion). */
    private static void catLoudScare(ServerPlayer player) {
        double r = Config.PUPPETEER_CAT_SCARE_LOUD_RADIUS.get();
        if (r <= 0) {
            return;
        }
        Set<Integer> present = ConcurrentHashMap.newKeySet();
        for (net.minecraft.world.entity.projectile.FireworkRocketEntity rocket : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.projectile.FireworkRocketEntity.class, player.getBoundingBox().inflate(r))) {
            present.add(rocket.getId());
        }
        Set<Integer> was = CAT_ROCKETS.put(player.getUUID(), present);
        if (was == null || !ready(player)) {
            return;
        }
        for (int id : was) {
            if (!present.contains(id)) { // one that was close is gone — it went off
                cooldown(player, Config.PUPPETEER_CAT_SCARE_COOLDOWN_TICKS.get());
                catScared(player);
                return;
            }
        }
    }

    /** a nearby explosion spooks a cat puppet into the air — its forced Scare, respecting the cooldown. */
    @SubscribeEvent
    static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        double r = Config.PUPPETEER_CAT_SCARE_LOUD_RADIUS.get();
        if (r <= 0 || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = event.getExplosion().center();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(at, at).inflate(r))) {
            if (possessed(p) == PuppetType.CAT && ready(p)) {
                cooldown(p, Config.PUPPETEER_CAT_SCARE_COOLDOWN_TICKS.get());
                catScared(p);
            }
        }
    }

    /** whether a crouched cat puppet is sitting on top of {@code chestPos} (blocking others from opening it). */
    private static boolean catSittingOnChest(net.minecraft.world.level.Level level, BlockPos chestPos) {
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp && possessed(sp) == PuppetType.CAT && sp.isCrouching()
                    && sp.blockPosition().equals(chestPos.above())) {
                return true;
            }
        }
        return false;
    }

    // --- evoker -------------------------------------------------------------------------------------

    /**
     * the evoker's right-click spells. pose is vanilla's spell id (1 vex, 2 fangs, 3 wololo) — it drives the dummy's
     * raised arms and spark colour (client, from PUPPET_DASH_POWER). second = on the vex cooldown (ACTION2).
     */
    public enum EvokerSpell {
        RING(2, false), LINE(2, false), VEXES(1, true), WOLOLO(3, false), CONVERT(3, false);

        public final int pose;
        public final boolean second;

        EvokerSpell(int pose, boolean second) {
            this.pose = pose;
            this.second = second;
        }

        public String labelKey() {
            return "witchmod.puppeteer.action.evoker_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** the belt's three fang/vex spells, in scroll order — 0 ring, 1 line, 2 vexes. */
    private static final EvokerSpell[] BELT = {EvokerSpell.RING, EvokerSpell.LINE, EvokerSpell.VEXES};

    /** the belt spell the selection points at (clamped). */
    public static EvokerSpell beltSpell(Player player) {
        int sel = Mth.clamp(player.getData(WitchModAttachments.PUPPET_SPELL_SELECT), 0, BELT.length - 1);
        return BELT[sel];
    }

    /**
     * what a right-click casts right now (client and server share it, so the HUD matches): at a sheep, wololo; at a
     * villager, convert; otherwise the belt's selected spell. no hold-timing any more — you scroll the belt instead.
     */
    public static EvokerSpell evokerSpell(Player player) {
        LivingEntity target = beamHit(player, 16.0).entity();
        if (target instanceof net.minecraft.world.entity.animal.Sheep) {
            return EvokerSpell.WOLOLO;
        }
        if (target instanceof net.minecraft.world.entity.npc.Villager) {
            return EvokerSpell.CONVERT;
        }
        return beltSpell(player);
    }

    /** an evoker winding up a wololo / conversion: which, when it goes off, and at what. */
    private record Cast(EvokerSpell spell, long at, @Nullable LivingEntity target) {}

    private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();
    /** when an instant cast's arms-up pose ends (game time) — purely cosmetic. */
    private static final Map<UUID, Long> CAST_POSE = new ConcurrentHashMap<>();
    /** each evoker puppet's vexes, and who they're hunting (whoever it last hit or horned). */
    private static final Map<UUID, List<net.minecraft.world.entity.monster.Vex>> VEXES = new ConcurrentHashMap<>();
    private static final Map<UUID, LivingEntity> VEX_PREY = new ConcurrentHashMap<>();
    private static final String VEX_TAG = "witchmod_puppet_vex";

    private static boolean spellReady(ServerPlayer player, EvokerSpell spell) {
        return spell.second ? ready2(player) : ready(player);
    }

    /** scroll the spell belt (dir -1 / +1), wrapping; a soft chime + action-bar name so the change reads. */
    public static void cycleSpell(ServerPlayer player, int dir) {
        if (possessed(player) != PuppetType.EVOKER) {
            return;
        }
        int sel = Math.floorMod(player.getData(WitchModAttachments.PUPPET_SPELL_SELECT) + Integer.signum(dir), BELT.length);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, sel);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.evoker_select",
                Component.translatable(BELT[sel].labelKey())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** arms-up pose for a cast: the spell's colour for a short flash (the client reads it from PUPPET_DASH_POWER). */
    private static void castPose(ServerPlayer player, int pose, int ticks) {
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, pose);
        CAST_POSE.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    /**
     * a right-click casts {@link #evokerSpell}: a sheep/villager in your sights winds up a wololo / conversion, otherwise
     * the belt's selected spell goes off at once. each spell keeps its own cooldown, so a recharging one just doesn't fire.
     */
    private static void evokerCast(ServerPlayer player, @Nullable Entity target) {
        EvokerSpell spell = evokerSpell(player);
        if (CASTS.containsKey(player.getUUID()) || !spellReady(player, spell)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        switch (spell) {
            case WOLOLO, CONVERT -> {
                LivingEntity at = target instanceof LivingEntity hit ? hit : lookTarget(player, 16.0);
                if (!(at instanceof net.minecraft.world.entity.animal.Sheep)
                        && !(at instanceof net.minecraft.world.entity.npc.Villager)) {
                    return; // lost the target between aim and click — no cooldown spent
                }
                int windup = Config.PUPPETEER_EVOKER_WINDUP_TICKS.get();
                cooldown(player, windup + 20);
                level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 1.0F, 1.0F);
                CASTS.put(player.getUUID(), new Cast(spell, now + windup, at));
                player.setData(WitchModAttachments.PUPPET_DASH_POWER, spell.pose);
                player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
                return;
            }
            case VEXES -> {
                player.setData(WitchModAttachments.PUPPET_ACTION2_READY, now + Config.PUPPETEER_EVOKER_VEX_COOLDOWN_TICKS.get());
                summonVexes(player);
                level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            default -> {
                cooldown(player, Config.PUPPETEER_EVOKER_FANGS_COOLDOWN_TICKS.get());
                fangs(player, spell == EvokerSpell.LINE);
                level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        castPose(player, spell.pose, 8);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /**
     * evoker left-click: the RALLYING HORN. no damage — every illager (and witch, and ravager: the raiders) in range turns
     * on whatever you're pointing at, with a burst of speed. your vexes go after it too.
     */
    private static void evokerHorn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (player.getData(WitchModAttachments.PUPPET_RAGE_END) > now) {
            return; // PUPPET_RAGE_END doubles as the horn's cooldown
        }
        double range = Config.PUPPETEER_EVOKER_HORN_RANGE.get();
        LivingEntity target = lookTarget(player, range);
        if (target == null) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_EVOKER_HORN_COOLDOWN_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 4.0F, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 2.0F, 0.8F);
        VEX_PREY.put(player.getUUID(), target);
        int speedTicks = Config.PUPPETEER_EVOKER_HORN_SPEED_TICKS.get();
        int speedLevel = Config.PUPPETEER_EVOKER_HORN_SPEED_LEVEL.get() - 1;
        // mark the quarry for everyone to see: it glows for the whole rally, so it's clear who's being hunted.
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.max(speedTicks, 200), 0));
        player.swing(InteractionHand.MAIN_HAND, true);
        int count = 0;
        for (net.minecraft.world.entity.raid.Raider raider : level.getEntitiesOfClass(net.minecraft.world.entity.raid.Raider.class,
                player.getBoundingBox().inflate(range), r -> r.isAlive() && r != target)) {
            raider.setTarget(target);
            if (speedTicks > 0) {
                raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedTicks, speedLevel));
            }
            // a clear thread of anger from each raider toward the quarry
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, raider.getX(), raider.getEyeY() + 0.6, raider.getZ(), 3, 0.25, 0.2, 0.25, 0.0);
            count++;
        }
        // a bold burst over the quarry so it reads at a glance
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, target.getX(), target.getEyeY() + 0.7, target.getZ(), 16, 0.5, 0.4, 0.5, 0.0);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY() + 0.5, target.getZ(), 12, 0.5, 0.4, 0.5, 0.3);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.horn", count,
                target.getName()).withStyle(ChatFormatting.RED), true);
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** evoker upkeep: a brief cast pose fades; a wound-up wololo / conversion goes off; vexes keep after their prey. */
    private static void tickEvoker(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // clear the short arms-up flash from an instant cast (a wind-up owns the pose itself until it resolves)
        Long poseEnd = CAST_POSE.get(player.getUUID());
        if (poseEnd != null && level.getGameTime() >= poseEnd && !CASTS.containsKey(player.getUUID())) {
            CAST_POSE.remove(player.getUUID());
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
        }
        Cast cast = CASTS.get(player.getUUID());
        if (cast != null && level.getGameTime() >= cast.at()) {
            CASTS.remove(player.getUUID());
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (cast.target() instanceof net.minecraft.world.entity.animal.Sheep sheep && sheep.isAlive()) {
                // wololo: the evoker's old joke — blue goes red (and, here, red goes blue)
                sheep.setColor(sheep.getColor() == DyeColor.RED ? DyeColor.BLUE : DyeColor.RED);
            } else if (cast.target() instanceof net.minecraft.world.entity.npc.Villager villager && villager.isAlive()) {
                convertToWitch(level, villager);
            }
            player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
        }
        List<net.minecraft.world.entity.monster.Vex> vexes = VEXES.get(player.getUUID());
        if (vexes != null) {
            vexes.removeIf(v -> !v.isAlive());
            LivingEntity prey = VEX_PREY.get(player.getUUID());
            if (prey != null && !prey.isAlive()) {
                VEX_PREY.remove(player.getUUID());
                prey = null;
            }
            for (net.minecraft.world.entity.monster.Vex vex : vexes) {
                if (vex.getTarget() != prey) {
                    vex.setTarget(prey);
                }
            }
        }
    }

    /** a villager turned witch — as lightning does it (the conversion events fire, so other mods can object). */
    private static void convertToWitch(ServerLevel level, net.minecraft.world.entity.npc.Villager villager) {
        if (!net.neoforged.neoforge.event.EventHooks.canLivingConvert(villager, EntityType.WITCH, timer -> {})) {
            return;
        }
        net.minecraft.world.entity.monster.Witch witch = villager.convertTo(EntityType.WITCH, false);
        if (witch == null) {
            return;
        }
        witch.finalizeSpawn(level, level.getCurrentDifficultyAt(witch.blockPosition()), MobSpawnType.CONVERSION, null);
        witch.setPersistenceRequired();
        net.neoforged.neoforge.event.EventHooks.onLivingConvert(villager, witch);
        level.sendParticles(ParticleTypes.WITCH, witch.getX(), witch.getY() + 1.0, witch.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
        level.playSound(null, witch.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** vanilla's fangs: a tap rings you twice over (5 close, 8 further out); a longer hold sends a line of 16 down your aim. */
    private static void fangs(ServerPlayer player, boolean line) {
        double minY = player.getY() - 4.0;
        double maxY = player.getY() + 3.0;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float f = (float) Mth.atan2(Mth.cos(yaw), -Mth.sin(yaw));
        if (!line) {
            for (int i = 0; i < 5; i++) {
                float a = f + i * Mth.PI * 0.4F;
                fang(player, player.getX() + Mth.cos(a) * 1.5, player.getZ() + Mth.sin(a) * 1.5, minY, maxY, a, 0);
            }
            for (int i = 0; i < 8; i++) {
                float a = f + i * Mth.PI * 2.0F / 8.0F + 1.2566371F;
                fang(player, player.getX() + Mth.cos(a) * 2.5, player.getZ() + Mth.sin(a) * 2.5, minY, maxY, a, 3);
            }
        } else {
            for (int i = 0; i < 16; i++) {
                double d = 1.25 * (i + 1);
                fang(player, player.getX() + Mth.cos(f) * d, player.getZ() + Mth.sin(f) * d, minY, maxY, f, i);
            }
        }
    }

    /** one fang, set on the ground between minY and maxY (vanilla's createSpellEntity), credited to you. */
    private static void fang(ServerPlayer player, double x, double z, double minY, double maxY, float yRot, int delay) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double lift = 0.0;
        boolean found = false;
        do {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        lift = shape.max(Direction.Axis.Y);
                    }
                }
                found = true;
                break;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);
        if (found) {
            level.addFreshEntity(new net.minecraft.world.entity.projectile.EvokerFangs(level, x, pos.getY() + lift, z, yRot, delay, player));
        }
    }

    /** summon vexes (vanilla: three, short-lived) — they hunt whoever you last hit, and never you. */
    private static void summonVexes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<net.minecraft.world.entity.monster.Vex> vexes = VEXES.computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        for (int i = 0; i < Config.PUPPETEER_EVOKER_VEX_COUNT.get(); i++) {
            net.minecraft.world.entity.monster.Vex vex = EntityType.VEX.create(level);
            if (vex == null) {
                break;
            }
            BlockPos at = player.blockPosition().offset(-2 + level.random.nextInt(5), 1, -2 + level.random.nextInt(5));
            vex.moveTo(at, 0.0F, 0.0F);
            vex.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
            vex.setBoundOrigin(at);
            vex.setLimitedLife(20 * (30 + level.random.nextInt(90)));
            vex.addTag(VEX_TAG);
            level.addFreshEntity(vex);
            vexes.add(vex);
        }
    }

    /** an evoker puppet's vex only ever targets its master's quarry (see onTarget). */
    private static boolean vexAllowed(Mob mob, LivingEntity target) {
        if (!(mob instanceof net.minecraft.world.entity.monster.Vex vex) || !vex.getTags().contains(VEX_TAG)) {
            return true;
        }
        for (Map.Entry<UUID, List<net.minecraft.world.entity.monster.Vex>> e : VEXES.entrySet()) {
            if (e.getValue().contains(vex)) {
                return target == VEX_PREY.get(e.getKey());
            }
        }
        return false;
    }

    // --- effects gained as a puppet -----------------------------------------------------------------

    private static final String PRIOR_EFFECTS_KEY = "witchmod_prior_effects";

    /** remember which effects you had going in (saved in the puppet data, so it survives a crash too). */
    private static void notePriorEffects(ServerPlayer player, CompoundTag tag) {
        ListTag ids = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effect.getEffect().unwrapKey().ifPresent(key -> ids.add(net.minecraft.nbt.StringTag.valueOf(key.location().toString())));
        }
        tag.put(PRIOR_EFFECTS_KEY, ids);
    }

    /**
     * effects you picked up as the puppet (drinking a potion as a witch, a splash, a beacon...) belong to the mob: they
     * come off you when you step out, and go with it. @return them, to hand to the restored mob (empty if unknown).
     */
    private static List<MobEffectInstance> takePuppetEffects(ServerPlayer player, CompoundTag tag) {
        List<MobEffectInstance> taken = new ArrayList<>();
        if (!tag.contains(PRIOR_EFFECTS_KEY)) {
            return taken;
        }
        Set<String> prior = new java.util.HashSet<>();
        ListTag ids = tag.getList(PRIOR_EFFECTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < ids.size(); i++) {
            prior.add(ids.getString(i));
        }
        tag.remove(PRIOR_EFFECTS_KEY);
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            String id = effect.getEffect().unwrapKey().map(k -> k.location().toString()).orElse("");
            if (!prior.contains(id)) {
                taken.add(new MobEffectInstance(effect));
                player.removeEffect(effect.getEffect());
            }
        }
        return taken;
    }

    /** too busy to swing: a killer rabbit mid-scrap, a silverfish burrowing in or hidden, an endermite underground. */
    private static boolean busyBody(Player player) {
        if (burrowed(player)) {
            return true;
        }
        PuppetType type = PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        return (type == PuppetType.KILLER_RABBIT || type == PuppetType.SILVERFISH) && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    /**
     * silverfish right-click. hidden: BURST out. looking at stone it could infest (within reach): EMBED — burrow in over
     * puppeteerSilverfishEmbedTicks. otherwise: CALL reinforcements.
     */
    private static void silverfishAction(ServerPlayer player) {
        if (hidden(player)) {
            burst(player);
            return;
        }
        if (EMBEDDING.containsKey(player.getUUID())) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && net.minecraft.world.level.block.InfestedBlock.isCompatibleHostBlock(player.level().getBlockState(hit.getBlockPos()))) {
            EMBEDDING.put(player.getUUID(), hit.getBlockPos());
            player.setData(WitchModAttachments.PUPPET_FUSE, 1);
            return;
        }
        if (ready(player)) {
            callSilverfish(player);
        }
    }

    private static void tickSilverfish(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos burrow = EMBEDDING.get(player.getUUID());
        if (burrow != null) {
            BlockState state = level.getBlockState(burrow);
            if (!net.minecraft.world.level.block.InfestedBlock.isCompatibleHostBlock(state)
                    || player.distanceToSqr(Vec3.atCenterOf(burrow)) > 36.0) {
                EMBEDDING.remove(player.getUUID()); // the stone went (or you did)
                player.setData(WitchModAttachments.PUPPET_FUSE, 0);
                return;
            }
            int fuse = player.getData(WitchModAttachments.PUPPET_FUSE) + chargeStep(player);
            player.setData(WitchModAttachments.PUPPET_FUSE, fuse);
            if (fuse % 3 == 0) {
                Vec3 c = Vec3.atCenterOf(burrow);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                        c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, burrow, SoundEvents.SILVERFISH_STEP, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
            if (fuse >= Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get()) {
                hide(player, burrow, state);
            }
            return;
        }
        Hideout hideout = HIDEOUTS.get(player.getUUID());
        if (hideout == null) {
            return;
        }
        if (!(level.getBlockState(hideout.pos()).getBlock() instanceof net.minecraft.world.level.block.InfestedBlock)) {
            burst(player); // someone broke (or changed) the block you're in — out you come
            return;
        }
        Vec3 spot = Vec3.atBottomCenterOf(hideout.pos());
        if (player.position().distanceToSqr(spot) > 0.01) {
            player.teleportTo(spot.x, spot.y, spot.z);
        }
        // the hud counts the brood off this (capped, so it stops changing once the brood is full)
        int embed = Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get();
        int cap = embed + Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get() * Config.PUPPETEER_SILVERFISH_BROOD_MAX.get();
        int fuse = (int) Math.min(cap, embed + level.getGameTime() - hideout.since());
        if (fuse != player.getData(WitchModAttachments.PUPPET_FUSE)) {
            player.setData(WitchModAttachments.PUPPET_FUSE, fuse);
        }
    }

    /** into the stone: it becomes its infested twin, and you vanish inside it (still able to look out). */
    private static void hide(ServerPlayer player, BlockPos pos, BlockState host) {
        ServerLevel level = player.serverLevel();
        EMBEDDING.remove(player.getUUID());
        level.setBlock(pos, net.minecraft.world.level.block.InfestedBlock.infestedStateByHost(host), 3);
        Vec3 spot = Vec3.atBottomCenterOf(pos);
        player.teleportTo(spot.x, spot.y, spot.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setInvisible(true);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, true);
        HIDEOUTS.put(player.getUUID(), new Hideout(pos, level.getGameTime()));
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putLong(HIDDEN_KEY, pos.asLong());
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        level.playSound(null, pos, SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /**
     * burst out of the stone: it shatters, and the longer you were in it the bigger the brood that comes out with you
     * (one per puppeteerSilverfishBroodTicks, up to puppeteerSilverfishBroodMax), all of you with a burst of speed.
     */
    private static void burst(ServerPlayer player) {
        Hideout hideout = HIDEOUTS.remove(player.getUUID());
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        unhide(player, tag);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        if (hideout == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int brood = (int) Math.min(Config.PUPPETEER_SILVERFISH_BROOD_MAX.get(),
                (level.getGameTime() - hideout.since()) / Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get());
        int speed = Config.PUPPETEER_SILVERFISH_BURST_SPEED_TICKS.get();
        Vec3 at = Vec3.atBottomCenterOf(hideout.pos());
        for (int i = 0; i < brood; i++) {
            net.minecraft.world.entity.monster.Silverfish fish = EntityType.SILVERFISH.create(level);
            if (fish == null) {
                break;
            }
            fish.moveTo(at.x + (level.random.nextDouble() - 0.5) * 0.6, at.y, at.z + (level.random.nextDouble() - 0.5) * 0.6,
                    level.random.nextFloat() * 360.0F, 0.0F);
            fish.finalizeSpawn(level, level.getCurrentDifficultyAt(hideout.pos()), MobSpawnType.REINFORCEMENT, null);
            if (speed > 0) {
                fish.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speed, 1));
            }
            fish.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.6, 0.3, (level.random.nextDouble() - 0.5) * 0.6);
            level.addFreshEntity(fish);
            fish.spawnAnim();
        }
        if (speed > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speed, 1, false, false));
        }
        level.playSound(null, hideout.pos(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.5F, 1.2F);
        // out of the stone, the swarm goes for the closest mob (no glow — a soft rally)
        Mob closest = null;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at, at).inflate(16.0),
                m -> m.isAlive() && !(m instanceof net.minecraft.world.entity.monster.Silverfish)
                        && !(m instanceof net.minecraft.world.entity.monster.Endermite))) {
            if (closest == null || mob.distanceToSqr(at) < closest.distanceToSqr(at)) {
                closest = mob;
            }
        }
        if (closest != null) {
            silverfishRally(player, closest);
        }
    }

    /** the silverfish around you go for {@code prey} — a zombie-style rally, minus the glow and the summoning. */
    private static void silverfishRally(ServerPlayer player, LivingEntity prey) {
        double r = Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        for (net.minecraft.world.entity.monster.Silverfish fish : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Silverfish.class, player.getBoundingBox().inflate(r), f -> f.isAlive() && f != prey)) {
            fish.setTarget(prey);
        }
    }

    /**
     * leave the stone (bursting out, stepping out of the puppet, or logging back in after a crash): the infested block
     * is broken so you're free, and you're visible again. the spot comes from the puppet data, so it survives a crash.
     */
    private static void unhide(ServerPlayer player, CompoundTag tag) {
        EMBEDDING.remove(player.getUUID());
        boolean wasHidden = player.getData(WitchModAttachments.PUPPET_HIDDEN) || tag.contains(HIDDEN_KEY);
        if (!wasHidden) {
            return;
        }
        HIDEOUTS.remove(player.getUUID());
        if (tag.contains(HIDDEN_KEY)) {
            BlockPos pos = BlockPos.of(tag.getLong(HIDDEN_KEY));
            tag.remove(HIDDEN_KEY);
            if (player.serverLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.InfestedBlock) {
                player.serverLevel().destroyBlock(pos, false); // no drops, no extra silverfish — you ARE the silverfish
            }
        }
        player.setInvisible(false);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, false);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
    }

    /**
     * Call reinforcements: like a hurt silverfish's own call, every infested block nearby breaks open (with mobGriefing
     * — otherwise it just reverts, as in vanilla) and its silverfish join in; every silverfish near you goes for
     * whatever you're looking at. nothing to call: a hint, and no cooldown spent.
     */
    private static void callSilverfish(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int r = Config.PUPPETEER_SILVERFISH_CALL_RADIUS.get();
        int woken = 0;
        boolean grief = EventHooks.canEntityGrief(level, player);
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-r, -r / 2, -r), player.blockPosition().offset(r, r / 2, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.InfestedBlock infested) {
                if (grief) {
                    level.destroyBlock(pos, true, player);
                } else {
                    level.setBlock(pos, infested.hostStateByInfested(state), 3);
                }
                woken++;
            }
        }
        // seed the stone around you: up to N stone-type blocks become infested, ready for the next call.
        int infested = 0;
        int want = Config.PUPPETEER_SILVERFISH_CALL_INFEST.get();
        for (int attempt = 0; attempt < want * 12 && infested < want; attempt++) {
            BlockPos pos = player.blockPosition().offset(level.random.nextInt(r * 2 + 1) - r, level.random.nextInt(r + 1) - r / 2,
                    level.random.nextInt(r * 2 + 1) - r);
            BlockState state = level.getBlockState(pos);
            if (net.minecraft.world.level.block.InfestedBlock.isCompatibleHostBlock(state)) {
                level.setBlock(pos, net.minecraft.world.level.block.InfestedBlock.infestedStateByHost(state), 3);
                Vec3 c = Vec3.atCenterOf(pos);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                        c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.05);
                infested++;
            }
        }
        LivingEntity prey = lookTarget(player, 24.0);
        int helpers = 0;
        for (net.minecraft.world.entity.monster.Silverfish fish : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Silverfish.class,
                player.getBoundingBox().inflate(r * 1.5), LivingEntity::isAlive)) {
            helpers++;
            if (prey != null) {
                fish.setTarget(prey);
            }
        }
        if (woken == 0 && helpers == 0 && infested == 0) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.silverfish_no_call").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        cooldown(player, Config.PUPPETEER_SILVERFISH_CALL_COOLDOWN_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 2.0F, 0.6F);
    }

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
    private static final Map<UUID, Integer> BLIZZARDS = new ConcurrentHashMap<>();

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

    private static final Map<UUID, Integer> BLIZZARD_TICK = new ConcurrentHashMap<>();

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
    private static final Map<UUID, Map<UUID, Float>> BLIZZARD_DEALT = new ConcurrentHashMap<>();
    /** puppet-fired projectiles that have already landed a hit (some can register two hits as they pass through). */
    private static final String HIT_TAG = "witchmod_hit";

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

    /**
     * puppet projectiles landing: a snow golem's snowballs hurt (enhanced volley ones burst and chill, capped per target
     * per blizzard), a snowball still hurts a blaze puppet (3, like the real blaze), a breeze's gale bursts big,
     * a breeze puppet bats projectiles back like the real one, and a llama's spit only ever counts once.
     */
    @SubscribeEvent
    static void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        net.minecraft.world.entity.projectile.Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.phys.HitResult result = event.getRayTraceResult();
        Entity struck = result instanceof net.minecraft.world.phys.EntityHitResult hit ? hit.getEntity() : null;
        // a breeze puppet deflects projectiles back where they came from (wind charges excepted, as in vanilla)
        if (struck instanceof ServerPlayer breeze && possessed(breeze) == PuppetType.BREEZE
                && !(projectile instanceof net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge)) {
            net.minecraft.world.entity.projectile.ProjectileDeflection.REVERSE.deflect(projectile, breeze, level.random);
            event.setCanceled(true);
            return;
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
        if (!(projectile instanceof net.minecraft.world.entity.projectile.Snowball ball)) {
            return;
        }
        if (struck instanceof ServerPlayer blaze && possessed(blaze) == PuppetType.BLAZE) {
            blaze.hurt(ball.damageSources().thrown(ball, ball.getOwner()), 3.0F); // a snowball stings a blaze
        }
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
            // a volley is a shotgun: every snowball that lands counts (no hurt-cooldown) — up to the per-target cap.
            damage = blizzardAllowance(ball.getOwner(), target, damage);
            target.invulnerableTime = 0;
        }
        if (damage > 0.0F) {
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

    // --- villager -----------------------------------------------------------------------------------

    /** villagers due to "hmm" back at you: when, and who. */
    private record Echo(long at, net.minecraft.world.entity.npc.Villager villager) {}

    private static final List<Echo> ECHOES = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** temporary villagers standing in for a villager puppet's shop → the puppet (see openShop). */
    private static final Map<net.minecraft.world.entity.npc.AbstractVillager, UUID> SHOPS = new ConcurrentHashMap<>();
    private static final String TILL_KEY = "witchmod_till";

    /** villager: Hmm — and every villager around turns to look at you, then hmms back, one after another. */
    private static void hmm(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // your own voice is the puppet's (a wandering trader keeps its own "hmm"); the villagers that answer are real.
        level.playSound(null, player.blockPosition(), ambientOf(possessed(player)), SoundSource.PLAYERS, 1.0F, 1.0F);
        double r = Config.PUPPETEER_VILLAGER_HMM_RADIUS.get();
        long now = level.getGameTime();
        for (net.minecraft.world.entity.npc.Villager v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                player.getBoundingBox().inflate(r), v -> v.isAlive() && v.distanceToSqr(player) <= r * r)) {
            v.getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                    new net.minecraft.world.entity.ai.behavior.EntityTracker(player, true), 80L);
            ECHOES.add(new Echo(now + 8 + level.random.nextInt(25), v));
        }
    }

    /**
     * wandering trader: right-click drinks a potion of invisibility and the puppet vanishes entirely (no model, no
     * nametag, no bubbles — solid-snake stealth); right-click again opts back out. also makes mobs lose track of you.
     */
    private static void toggleTraderStealth(ServerPlayer player) {
        boolean on = !player.getData(WitchModAttachments.PUPPET_STEALTH);
        player.setData(WitchModAttachments.PUPPET_STEALTH, on);
        ServerLevel level = player.serverLevel();
        if (on) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false)); // hidden: no icon, no particles
            level.playSound(null, player.blockPosition(), SoundEvents.WANDERING_TRADER_DRINK_POTION, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.removeEffect(MobEffects.INVISIBILITY);
            level.playSound(null, player.blockPosition(), SoundEvents.WANDERING_TRADER_DRINK_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** whether a puppet is in wandering-trader stealth (hide the dummy entirely). */
    public static boolean stealthed(Player player) {
        return player.getData(WitchModAttachments.PUPPET_STEALTH);
    }

    private static void tickEchoes(long now) {
        for (Echo e : ECHOES) {
            if (now >= e.at()) {
                ECHOES.remove(e);
                if (e.villager().isAlive() && !e.villager().isSleeping()) {
                    e.villager().playSound(e.villager().isBaby() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE,
                            1.0F, 0.9F + e.villager().getRandom().nextFloat() * 0.25F);
                }
            }
        }
    }

    /**
     * another player right-clicks a villager puppet: they get its shop — the real villager's trades (it's a stand-in
     * loaded from the puppet's data), and the emeralds they pay go in the puppet's till.
     */
    private static void openShop(ServerPlayer puppet, ServerPlayer customer) {
        ServerLevel level = puppet.serverLevel();
        Entity e = EntityType.loadEntityRecursive(puppet.getData(WitchModAttachments.PUPPET_DATA).copy(), level, x -> x);
        // a villager OR a wandering trader (both are AbstractVillager merchants) can run a shop.
        if (!(e instanceof net.minecraft.world.entity.npc.AbstractVillager shop)) {
            return;
        }
        shop.moveTo(puppet.getX(), puppet.getY(), puppet.getZ());
        boolean trader = possessed(puppet) == PuppetType.WANDERING_TRADER;
        if (shop.isBaby() || shop.getOffers().isEmpty()) {
            level.playSound(null, puppet.blockPosition(), trader ? SoundEvents.WANDERING_TRADER_NO : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
            customer.displayClientMessage(Component.translatable("witchmod.puppeteer.villager_no_trades", puppet.getName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        if (!tag.contains("Offers")) {
            // one that never traded rolls its trades on first look — keep them, or they'd re-roll every visit.
            CompoundTag saved = new CompoundTag();
            shop.saveWithoutId(saved);
            if (saved.contains("Offers")) {
                tag.put("Offers", saved.get("Offers"));
                puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            }
        }
        shop.setTradingPlayer(customer);
        SHOPS.put(shop, puppet.getUUID());
        int shopLevel = shop instanceof net.minecraft.world.entity.npc.Villager v ? v.getVillagerData().getLevel() : 1;
        shop.openTradingScreen(customer, Component.translatable("witchmod.puppeteer.villager_shop", puppet.getName()), shopLevel);
        level.playSound(null, puppet.blockPosition(), trader ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** a trade at a puppet's shop: its trades / xp carry back into the puppet, and the emeralds paid go in the till. */
    @SubscribeEvent
    static void onTrade(net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent event) {
        UUID owner = SHOPS.get(event.getAbstractVillager());
        if (owner == null || !(event.getAbstractVillager().level() instanceof ServerLevel level)
                || !(level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer puppet)
                || (possessed(puppet) != PuppetType.VILLAGER && possessed(puppet) != PuppetType.WANDERING_TRADER)) {
            return;
        }
        CompoundTag saved = new CompoundTag();
        event.getAbstractVillager().saveWithoutId(saved);
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        for (String key : List.of("Offers", "Xp", "VillagerData")) {
            if (saved.contains(key)) {
                tag.put(key, saved.get(key));
            }
        }
        int paid = 0;
        for (ItemStack cost : List.of(event.getMerchantOffer().getCostA(), event.getMerchantOffer().getCostB())) {
            if (cost.is(Items.EMERALD)) {
                paid += cost.getCount();
            }
        }
        if (paid > 0) {
            tag.putInt(TILL_KEY, tag.getInt(TILL_KEY) + paid);
            puppet.displayClientMessage(Component.translatable("witchmod.puppeteer.villager_till", paid, tag.getInt(TILL_KEY))
                    .withStyle(ChatFormatting.GREEN), true);
        }
        puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
        boolean trader = possessed(puppet) == PuppetType.WANDERING_TRADER;
        puppet.serverLevel().playSound(null, puppet.blockPosition(),
                trader ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** close every customer's shop window for this puppet (it's leaving / changing). */
    private static void closeShops(ServerPlayer puppet) {
        SHOPS.entrySet().removeIf(e -> {
            if (!e.getValue().equals(puppet.getUUID())) {
                return false;
            }
            if (e.getKey().getTradingPlayer() instanceof ServerPlayer customer) {
                customer.closeContainer();
            }
            return true;
        });
    }

    /**
     * killed by a zombie, a villager puppet doesn't die: it's infected, and you carry on as a zombie villager (its
     * profession, trades and till come along — zombie villagers keep them, ready for a cure).
     */
    private static boolean infectVillager(ServerPlayer player, DamageSource source) {
        Entity killer = source.getEntity();
        boolean zombie = killer instanceof Zombie && !(killer instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)
                || killer instanceof ServerPlayer p && possessed(p) != null && possessed(p).group() == Group.ZOMBIE;
        if (!zombie || !Config.PUPPETEER_VILLAGER_INFECTION.get()) {
            return false;
        }
        infect(player);
        return true;
    }

    /** the infection itself: villager puppet → zombie villager puppet, in place. */
    private static void infect(ServerPlayer player) {
        closeShops(player);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE_VILLAGER).toString());
        tag.remove("Brain");
        tag.remove("Gossips");
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        BASE_MAX.put(player.getUUID(), 20.0F);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, 20.0F);
        player.setData(WitchModAttachments.PUPPET_HEALTH, 20.0F);
        retype(player, PuppetType.ZOMBIE_VILLAGER);
        scaleHealth(player);
        player.setData(WitchModAttachments.PUPPET_HEALTH, player.getData(WitchModAttachments.PUPPET_MAX_HEALTH));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 2.0F, 1.0F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.villager_infected").withStyle(ChatFormatting.DARK_GREEN), true);
    }

    /** switch a live puppet to another type in place (zombification, infection): type, speed, reach, size. */
    private static void retype(ServerPlayer player, PuppetType type) {
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
    private static boolean hunts(Mob mob, @Nullable PuppetType type) {
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
            return golem || (type.group() == Group.SKELETON && wildWolf);
        }
        // prey: wild wolves hunt sheep & rabbits; foxes hunt chickens, rabbits and fish.
        if (type == PuppetType.SHEEP || type == PuppetType.RABBIT) {
            return wildWolf || (type == PuppetType.RABBIT && fox);
        }
        if (type == PuppetType.CHICKEN || type.group() == Group.FISH) {
            return fox;
        }
        return false;
    }

    /** the hunters around you pick you out, as their target goals would pick out the real mob (line of sight, 16 blocks). */
    private static void drawHunters(ServerPlayer player, PuppetType type) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0),
                m -> m.isAlive() && (m.getTarget() == null || !m.getTarget().isAlive()) && hunts(m, type)
                        && m.getSensing().hasLineOfSight(player))) {
            mob.setTarget(player);
        }
    }

    // --- iron golem ---------------------------------------------------------------------------------

    /**
     * iron golem: Offer Poppy (hold right-click). you hold a poppy out (the real golem's pose) to whoever you're looking
     * at; held for puppeteerGolemPoppyHoldTicks, they take it — brief Strength I and Resistance I. nobody there: you
     * just keep holding it out.
     */
    private static void tickOffer(ServerPlayer player) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (!HELD.contains(player.getUUID()) || !ready(player)) {
            if (hold > 0 && !HELD.contains(player.getUUID())) {
                player.setData(WitchModAttachments.PUPPET_FUSE, 0);
            }
            return;
        }
        int need = Config.PUPPETEER_GOLEM_POPPY_HOLD_TICKS.get();
        int next = Math.min(hold + chargeStep(player), need);
        player.setData(WitchModAttachments.PUPPET_FUSE, next);
        if (next < need) {
            return;
        }
        LivingEntity taker = lookTarget(player, 4.0);
        if (taker == null) {
            return; // still holding it out
        }
        int ticks = Config.PUPPETEER_GOLEM_POPPY_SECONDS.get() * 20;
        taker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0), player);
        taker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0), player);
        player.serverLevel().sendParticles(ParticleTypes.HEART, taker.getX(), taker.getY() + taker.getBbHeight() + 0.3, taker.getZ(),
                2, 0.2, 0.1, 0.2, 0.0);
        player.serverLevel().playSound(null, taker.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        cooldown(player, Config.PUPPETEER_GOLEM_POPPY_COOLDOWN_TICKS.get());
    }

    /** the hoglin's tusk throw (vanilla HoglinBase.throwTarget): your swings toss what they hit up and away. */
    private static void tuskThrow(ServerPlayer player, PuppetType type, LivingEntity target) {
        double kb = (type == PuppetType.ZOGLIN ? Config.PUPPETEER_ZOGLIN_KNOCKBACK.get() : Config.PUPPETEER_HOGLIN_KNOCKBACK.get())
                - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (kb <= 0.0) {
            return;
        }
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        float twist = player.getRandom().nextInt(21) - 10;
        Vec3 push = new Vec3(dx, 0.0, dz).normalize().scale(kb * (player.getRandom().nextFloat() * 0.5F + 0.2F))
                .yRot(twist * Mth.DEG_TO_RAD);
        target.push(push.x, kb * player.getRandom().nextFloat() * 0.5, push.z);
        target.hurtMarked = true;
    }

    // --- dolphin / axolotl --------------------------------------------------------------------------

    /** a dolphin / axolotl puppet's moisture (ticks left out of water before it dries out). */
    private static final Map<UUID, Integer> MOISTURE = new ConcurrentHashMap<>();

    /** out of water (rain counts as water) the moisture runs down, then you dry out: the puppet takes dryOut damage. */
    private static void tickMoisture(ServerPlayer player, int max) {
        if (player.isInWaterRainOrBubble()) {
            MOISTURE.put(player.getUUID(), max);
            return;
        }
        int left = MOISTURE.getOrDefault(player.getUUID(), max) - 1;
        MOISTURE.put(player.getUUID(), left);
        if (left <= 0 && left % 10 == 0) {
            player.hurt(player.damageSources().dryOut(), 1.0F);
        }
    }

    /**
     * dolphins: very fast swimmers that breathe AIR — underwater your air lasts 16x as long (a real dolphin's 4800
     * ticks) but you must surface. on land you're slow and flop about, and eventually dry out. you eat fish: swim
     * into a dropped one, or be fed one.
     */
    private static void tickDolphin(ServerPlayer player) {
        boolean wet = player.isInWater();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && wet == speed.hasModifier(FISH_LAND_ID)) {
            if (wet) {
                speed.removeModifier(FISH_LAND_ID);
            } else {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FISH_LAND_ID, -Config.PUPPETEER_DOLPHIN_LAND_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
        // vanilla takes 1 air a tick underwater; give 15 of every 16 back.
        if (player.isUnderWater() && player.getAirSupply() < player.getMaxAirSupply() && player.tickCount % 16 != 0) {
            player.setAirSupply(player.getAirSupply() + 1);
        }
        tickMoisture(player, Config.PUPPETEER_DOLPHIN_MOISTURE_TICKS.get());
        if (!wet && player.onGround() && !player.getAbilities().flying) {
            player.setDeltaMovement(player.getDeltaMovement().add((player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.2,
                    0.5, (player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.2)); // a beached dolphin's hop (vanilla)
            player.hurtMarked = true;
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        // eat any fish you swim into, like a real dolphin picking one up.
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(0.8),
                i -> i.isAlive() && i.getItem().is(ItemTags.FISHES))) {
            item.getItem().shrink(1);
            if (item.getItem().isEmpty()) {
                item.discard();
            }
            dolphinEat(player);
            break;
        }
    }

    private static void dolphinEat(ServerPlayer dolphin) {
        scaleHeal(dolphin, Config.PUPPETEER_DOLPHIN_FISH_HEAL.get().floatValue());
        dolphin.getFoodData().eat(2, 0.3F);
        dolphin.serverLevel().playSound(null, dolphin.blockPosition(), SoundEvents.DOLPHIN_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
        dolphin.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, dolphin.getX(), dolphin.getY() + 0.5, dolphin.getZ(),
                3, 0.3, 0.2, 0.3, 0.0);
    }

    /** dolphin: Inspire — every player and water creature near you gets Dolphin's Grace (fast swimming). */
    private static void inspire(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double r = Config.PUPPETEER_DOLPHIN_INSPIRE_RADIUS.get();
        int ticks = Config.PUPPETEER_DOLPHIN_INSPIRE_SECONDS.get() * 20;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e.isAlive() && e.distanceToSqr(player) <= r * r
                        && (e instanceof Player || e instanceof net.minecraft.world.entity.animal.WaterAnimal
                        || e instanceof net.minecraft.world.entity.animal.axolotl.Axolotl))) {
            e.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, ticks, 0), player);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_PLAY, SoundSource.PLAYERS, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.DOLPHIN, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.6, 0.4, 0.6, 0.0);
    }

    /**
     * axolotls: endless air underwater, fine on land (until dry). HOLD right-click to play dead: you lie still,
     * regenerate quickly, and every mob after you loses interest (and can't pick you back up while you're down).
     */
    private static void tickAxolotl(ServerPlayer player, int ticksRemaining) {
        // amphibious, like a real axolotl: breathes fine in AND out of water — it never drowns, it only dries out.
        player.setAirSupply(player.getMaxAirSupply());
        tickHold(player, PuppetType.AXOLOTL);
        if (!playingDead(player)) {
            tickMoisture(player, Config.PUPPETEER_AXOLOTL_MOISTURE_TICKS.get());
            return;
        }
        // playing dead: it's resting, so the dry-out is paused — you can keep it up out of water as long as you like.
        if (ticksRemaining % 10 == 0) {
            scaleHeal(player, Config.PUPPETEER_AXOLOTL_PLAY_DEAD_REGEN.get().floatValue() / 2.0F);
        }
        if (ticksRemaining % 5 == 0) {
            double r = Config.PUPPETEER_AXOLOTL_PLAY_DEAD_RADIUS.get();
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r),
                    // hasMemoryValue first: getMemory throws on a brain that doesn't register the memory
                    m -> m.getTarget() == player || m.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
                            && m.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == player)) {
                mob.setTarget(null);
                mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET); // piglins, hoglins...
                if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
                mob.setLastHurtByMob(null);
            }
        }
    }

    public static boolean playingDead(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.AXOLOTL
                && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    /** a held, sit-still move in progress (a guardian's beam, an axolotl playing dead): held and running or ready. */
    private static boolean channelling(ServerPlayer player) {
        return HELD.contains(player.getUUID()) && (player.getData(WitchModAttachments.PUPPET_FUSE) > 0 || ready(player));
    }

    /** elder guardian: mining fatigue III (and the ghostly face) on every other survival player in range. */
    private static void elderCurse(ServerPlayer elder) {
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
        beam += chargeStep(player); // (dexterous: the beam builds to its burst quicker)
        player.setData(WitchModAttachments.PUPPET_FUSE, beam);
        BeamHit hit = beamHit(player, Config.PUPPETEER_GUARDIAN_BEAM_RANGE.get());
        if (hit.entity() != null && beam % 10 == 0) {
            // the trickle doesn't shove what it's on — only the burst has any force behind it.
            NO_KNOCKBACK.add(hit.entity().getUUID());
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

    /** entities taking beam-trickle damage right now — their knockback is cancelled (see onKnockback). */
    private static final Set<UUID> NO_KNOCKBACK = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    static void onKnockback(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if (NO_KNOCKBACK.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /** the end of the beam: full damage to what it's on, half to anything right beside it. no fireworks — vanilla-quiet. */
    private static void burst(ServerPlayer player, PuppetType type, float strength) {
        ServerLevel level = player.serverLevel();
        boolean elder = type == PuppetType.ELDER_GUARDIAN;
        BeamHit hit = beamHit(player, Config.PUPPETEER_GUARDIAN_BEAM_RANGE.get());
        Vec3 at = hit.end();
        float damage = (elder ? Config.PUPPETEER_ELDER_GUARDIAN_BURST_DAMAGE.get()
                : Config.PUPPETEER_GUARDIAN_BURST_DAMAGE.get()).floatValue() * strength;
        double radius = elder ? 2.5 : 1.5;
        if (hit.entity() != null) {
            hit.entity().invulnerableTime = 0; // the tick damage just now mustn't eat the burst
            beamDamage(player, hit.entity(), damage);
            beamRally(player, type, hit.entity());
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                e -> e != player && e != hit.entity() && e.isAlive() && !e.isSpectator())) {
            beamDamage(player, e, damage * 0.5F);
        }
        endBeam(player);
        int cd = Config.PUPPETEER_GUARDIAN_BEAM_COOLDOWN_TICKS.get();
        cooldown(player, player.isInWater() ? cd : cd * 2); // a guardian out of water is labouring
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    private static void endBeam(ServerPlayer player) {
        BEAM_RALLIED.remove(player.getUUID());
    }

    /** magic damage, like a real guardian's laser (ignores armour), credited to you. */
    private static void beamDamage(ServerPlayer player, LivingEntity target, float amount) {
        if (amount <= 0.0F) {
            return;
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
        }
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
        int want = Config.PUPPETEER_ELDER_CALL_TARGET.get();
        int have = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Guardian.class, player.getBoundingBox().inflate(r),
                g -> g.isAlive() && g.distanceToSqr(player) <= r * r).size();
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, level.getGameTime() + Config.PUPPETEER_ELDER_CALL_COOLDOWN_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 3.0F, 0.6F);
        if (have >= want) {
            return; // the call still sounds — there's just nobody else to come
        }
        int spawned = 0;
        int reach = (int) Math.ceil(r);
        for (int attempt = 0; attempt < 80 && have + spawned < want; attempt++) {
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

    /**
     * fish: in water you're quick and never short of air; out of it you can barely move, flop about like a landed
     * fish (vanilla cadence, the disguise's flop), and your air runs out until you dry out.
     */
    private static void tickFish(ServerPlayer player, PuppetType type) {
        boolean wet = player.isInWater();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && wet == speed.hasModifier(FISH_LAND_ID)) {
            if (wet) {
                speed.removeModifier(FISH_LAND_ID);
            } else {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FISH_LAND_ID, -Config.PUPPETEER_FISH_LAND_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
        if (wet) {
            FISH_AIR.put(player.getUUID(), player.getMaxAirSupply());
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }
        int air = FISH_AIR.getOrDefault(player.getUUID(), player.getMaxAirSupply()) - 1;
        if (air <= -20) {
            air = 0;
            player.hurt(player.damageSources().dryOut(), 2.0F);
        }
        FISH_AIR.put(player.getUUID(), air);
        player.setAirSupply(air);
        if (type.group() == Group.FISH && player.onGround() && !player.getAbilities().flying) {
            double power = Config.FISH_FLOP_POWER.get();
            player.setDeltaMovement(player.getDeltaMovement().add((player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.05,
                    power, (player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.05));
            player.hurtMarked = true;
            player.serverLevel().playSound(null, player.blockPosition(), flopOf(type), SoundSource.PLAYERS, 0.9F,
                    0.9F + player.getRandom().nextFloat() * 0.25F);
        }
    }

    /** fish: Leap — out of water a big arcing launch where you're looking; in water a quick straight dash. */
    private static void leap(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        if (player.isInWater()) {
            player.setDeltaMovement(look.scale(Config.PUPPETEER_FISH_DASH_POWER.get()));
            level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.3, 0.2, 0.3, 0.1);
            level.playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_SWIM, SoundSource.PLAYERS, 1.0F, 1.4F);
        } else {
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(Config.PUPPETEER_FISH_LEAP_POWER.get());
            player.setDeltaMovement(flat.x, Config.PUPPETEER_FISH_LEAP_LIFT.get(), flat.z);
            level.playSound(null, player.blockPosition(), flopOf(type), SoundSource.PLAYERS, 1.2F, 0.7F);
        }
        player.hurtMarked = true;
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        if ("release".equalsIgnoreCase(arg)) {
            return release(target, null, false) ? "released the puppet" : "not possessing anything";
        }
        if (arg != null && !arg.isBlank()) {
            return forceSpecial(target, arg.toLowerCase(java.util.Locale.ROOT));
        }
        target.setData(WitchModAttachments.PUPPET_COOLDOWN_END, 0L);
        Mob nearest = null;
        for (Mob mob : target.serverLevel().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(8.0),
                m -> PuppetType.of(m.getType()) != null)) {
            if (nearest == null || mob.distanceToSqr(target) < nearest.distanceToSqr(target)) {
                nearest = mob;
            }
        }
        if (nearest == null) {
            return "no possessable mob within 8 blocks";
        }
        return startPossession(target, nearest) ? "possessing a " + nearest.getType().getDescription().getString()
                : "couldn't possess (already inside one?)";
    }

    @Override
    public List<String> debugArgs() {
        return List.of("release", "recharge", "stare", "zombify", "infect", "split", "charge", "curse", "brood");
    }

    /**
     * debug: force a puppet's special state / event that normally needs the right circumstances. each checks you're
     * the right puppet and says what happened.
     */
    private static String forceSpecial(ServerPlayer player, String what) {
        PuppetType type = possessed(player);
        if (type == null) {
            return "not possessing anything";
        }
        ServerLevel level = player.serverLevel();
        switch (what) {
            case "recharge" -> {
                // every cooldown ready, every charge full
                player.setData(WitchModAttachments.PUPPET_ACTION_READY, 0L);
                player.setData(WitchModAttachments.PUPPET_ACTION2_READY, 0L);
                if (type == PuppetType.ENDERMAN) {
                    player.setData(WitchModAttachments.PUPPET_TP_CHARGES,
                            enraged(player) ? Config.PUPPETEER_ENDERMAN_RAGE_CHARGES.get() : 1);
                }
                return "all puppet cooldowns reset";
            }
            case "stare" -> {
                if (type != PuppetType.ENDERMAN) {
                    return "only an enderman puppet can be stared at";
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.5F, 1.0F);
                player.playNotifySound(SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.setData(WitchModAttachments.PUPPET_RAGE_END,
                        level.getGameTime() + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() * 20L);
                return "enraged (as if stared at) for " + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() + "s";
            }
            case "zombify" -> {
                if (type != PuppetType.HOGLIN) {
                    return "only a hoglin puppet zombifies";
                }
                zombify(player);
                return "zombified into a zoglin";
            }
            case "infect" -> {
                if (type != PuppetType.VILLAGER) {
                    return "only a villager puppet can be infected";
                }
                infect(player);
                return "infected into a zombie villager";
            }
            case "split" -> {
                if (type.group() != Group.SLIME) {
                    return "only a slime / magma cube puppet splits";
                }
                return split(player, type) ? "split — you're one of the offspring now" : "already the smallest size (it would just die)";
            }
            case "charge" -> {
                if (type != PuppetType.CREEPER) {
                    return "only a creeper puppet can be charged";
                }
                CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
                tag.putBoolean("powered", true);
                player.setData(WitchModAttachments.PUPPET_DATA, tag);
                return "charged (as if struck by lightning)";
            }
            case "curse" -> {
                if (type != PuppetType.ELDER_GUARDIAN) {
                    return "only an elder guardian puppet curses";
                }
                elderCurse(player);
                return "cursed every other survival player in range";
            }
            case "brood" -> {
                Hideout hideout = HIDEOUTS.get(player.getUUID());
                if (type != PuppetType.SILVERFISH || hideout == null) {
                    return "only a silverfish puppet hidden in stone has a brood";
                }
                long full = (long) Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get() * Config.PUPPETEER_SILVERFISH_BROOD_MAX.get();
                HIDEOUTS.put(player.getUUID(), new Hideout(hideout.pos(), level.getGameTime() - full));
                return "brood full — burst out to release it";
            }
            default -> {
                return "unknown: " + what;
            }
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
        return type != null && (type == PuppetType.DROWNED || type.group() == Group.FISH || type.group() == Group.SQUID
                || type.group() == Group.GUARDIAN || type.group() == Group.DOLPHIN || type.group() == Group.AXOLOTL);
    }

    /** true while {@code player} is wearing a puppet — effects that reshape YOUR body hold off until you step out. */
    public static boolean isPuppet(@Nullable Player player) {
        return player != null && !player.getData(WitchModAttachments.PUPPET_TYPE).isEmpty();
    }

    @Nullable
    private static Group groupOf(@Nullable Player player) {
        PuppetType type = player == null ? null : PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        return type == null ? null : type.group();
    }

    @Nullable
    private static Move moveOf(@Nullable Player player) {
        PuppetType type = player == null ? null : PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        return type == null ? null : type.move();
    }

    /** bat: the disguise bat's slow creative-style flight (survival only — a creative player's own flight is left alone). */
    private static void grantBatFlight(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || player.getAbilities().mayfly) {
            return;
        }
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        player.setData(WitchModAttachments.PUPPET_GRANTED_FLIGHT, true); // saved: undone on login after a crash
    }

    private static void revokeBatFlight(ServerPlayer player) {
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
    private static boolean drawsBow(@Nullable Player player) {
        return player != null && !player.isShiftKeyDown() && (moveOf(player) == Move.BOW || moveOf(player) == Move.CROSSBOW);
    }

    /** possessing, or mid-possession: either way, no normal interaction. */
    private static boolean isBusy(@Nullable Player player) {
        return isPuppet(player) || (player != null && player.getData(WitchModAttachments.PUPPET_BINDING_END) > 0);
    }

    // --- possessing ----------------------------------------------------------------------------------

    /** begin the possession animation: the mob freezes and the player is drawn in over puppeteerPossessTicks. */
    private static boolean startPossession(ServerPlayer player, Mob mob) {
        if (PuppetType.of(mob.getType()) == null || isBusy(player) || !mob.isAlive()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long end = now + Config.PUPPETEER_POSSESS_TICKS.get();
        BINDINGS.put(player.getUUID(), new Binding(mob.getUUID(), player.position(), now, end, mob.isNoAi(), mob.isInvulnerable()));
        // remember its own flags ON the mob (saved with it), so a crash mid-possession can't leave it frozen forever.
        mob.setData(WitchModAttachments.PUPPET_FROZEN, (mob.isNoAi() ? 1 : 0) | (mob.isInvulnerable() ? 2 : 0));
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setDeltaMovement(Vec3.ZERO);
        player.setData(WitchModAttachments.PUPPET_BINDING_END, end);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.8F);
        return true;
    }

    /** a thin stream of souls pours into the mob, blessed sparks gather on it, and your body is pulled in. */
    private static void tickBinding(ServerPlayer player, Binding b) {
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(b.mobId());
        if (!(e instanceof Mob mob) || !mob.isAlive()) {
            cancelBinding(player, b, null);
            return;
        }
        long now = level.getGameTime();
        float t = Mth.clamp((float) (now - b.start()) / Math.max(1, b.end() - b.start()), 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        Vec3 at = b.from().lerp(mob.position(), eased);
        player.teleportTo(at.x, at.y, at.z);
        player.setDeltaMovement(Vec3.ZERO);
        Vec3 soul = player.getEyePosition().lerp(mob.getEyePosition(), level.random.nextFloat());
        level.sendParticles(ParticleTypes.SOUL, soul.x, soul.y, soul.z, 1, 0.03, 0.03, 0.03, 0.0);
        if ((now - b.start()) % 2 == 0) {
            double y = mob.getY() + mob.getBbHeight() * 0.6;
            level.sendParticles(level.random.nextBoolean() ? ParticleTypes.END_ROD : BLESSED_DUST,
                    mob.getX(), y, mob.getZ(), 1, 0.25, 0.3, 0.25, 0.01);
        }
        if (now >= b.end()) {
            completePossession(player, mob, b);
        }
    }

    private static void cancelBinding(ServerPlayer player, Binding b, @Nullable Mob mob) {
        BINDINGS.remove(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_BINDING_END, 0L);
        Mob target = mob;
        if (target == null) {
            // search every dimension — you may have been pulled through a portal mid-possession.
            for (ServerLevel level : player.server.getAllLevels()) {
                if (level.getEntity(b.mobId()) instanceof Mob m) {
                    target = m;
                    break;
                }
            }
        }
        if (target != null) {
            target.setNoAi(b.mobNoAi());
            target.setInvulnerable(b.mobInvulnerable());
            target.removeData(WitchModAttachments.PUPPET_FROZEN);
        }
    }

    /** stray / bogged puppets fire the arrows their mob would: a stray's slow, a bogged's poison (vanilla durations). */
    @SubscribeEvent
    static void onArrowSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof net.minecraft.world.entity.projectile.Arrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        PuppetType type = possessed(shooter);
        if (type == PuppetType.STRAY) {
            arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
        } else if (type == PuppetType.BOGGED) {
            arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        } else if (type == PuppetType.PILLAGER) {
            // its bolts hit a little harder — and nobody gets to keep them
            arrow.setBaseDamage(arrow.getBaseDamage() * Config.PUPPETEER_PILLAGER_BOLT_DAMAGE.get());
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
    }

    /**
     * a mob that was frozen mid-possession and then saved (crash, unload, the possessor vanishing) thaws the next time
     * it loads, unless someone really is still mid-possession with it.
     */
    @SubscribeEvent
    static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || !mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        int flags = mob.getData(WitchModAttachments.PUPPET_FROZEN);
        if (flags < 0 || BINDINGS.values().stream().anyMatch(b -> b.mobId().equals(mob.getUUID()))) {
            return;
        }
        DAZED.remove(mob.getUUID());
        thaw(mob);
    }

    /**
     * a puppet sees from its mob's eyes: your eye height (camera) becomes the mob's — a chicken's view hugs the grass,
     * a wither skeleton's towers. scaled with you (babies, size effects). a mob taller than you also gets the height
     * to match, so that camera never pokes through a ceiling. swimming only ever lowers it. runs on both sides; the
     * server refreshes on possess / release, clients when the synced puppet type changes (PuppeteerClient).
     */
    @SubscribeEvent
    static void onSize(net.neoforged.neoforge.event.entity.EntityEvent.Size event) {
        // hasData first: this fires inside the player's CONSTRUCTOR, and getData on a missing synced attachment
        // creates it and tries to sync it — to a player with no connection yet (NPE → "Invalid player data").
        if (!(event.getEntity() instanceof Player player) || !player.hasData(WitchModAttachments.PUPPET_TYPE)) {
            return;
        }
        PuppetType type = PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        net.minecraft.world.entity.Pose pose = event.getPose();
        if (type == null || pose == net.minecraft.world.entity.Pose.SLEEPING || pose == net.minecraft.world.entity.Pose.DYING) {
            return;
        }
        net.minecraft.world.entity.EntityDimensions cur = event.getNewSize();
        net.minecraft.world.entity.EntityDimensions mob = type.entityType().getDimensions();
        float scale = player.getScale();
        if (type.group() == Group.SLIME) {
            scale *= slimeSize(player); // a slime's size scales its whole body (and your view) — and halves on each split
        }
        float eye = mob.eyeHeight() * scale;
        float height = cur.height();
        if (pose == net.minecraft.world.entity.Pose.SWIMMING) {
            eye = Math.min(eye, cur.eyeHeight());
        } else {
            height = Math.max(height, mob.height() * scale);
        }
        eye = Math.min(eye, height - 0.05F);
        // the mob's attachment points too (scaled), so a rider sits where it would on the real mob (a horse's saddle). a ghast
        // also takes the mob's full WIDTH: its 4x4x4 body is what gets hit (and what has to fit).
        float width = type == PuppetType.GHAST ? mob.width() * scale : cur.width();
        event.setNewSize(new net.minecraft.world.entity.EntityDimensions(width, height, eye, mob.scale(scale).attachments(), cur.fixed()));
    }

    /** the possession takes: save + remove the mob, stash the inventory, and become it. */
    private static void completePossession(ServerPlayer player, Mob mob, Binding b) {
        cancelBinding(player, b, mob); // restores the mob's own ai/invulnerability flags before it's saved
        PuppetType type = PuppetType.of(mob);
        // just the mob: riders / mounts stay behind (otherwise leaving would duplicate them).
        mob.ejectPassengers();
        mob.stopRiding();
        CompoundTag tag = new CompoundTag();
        if (type == null || !mob.saveAsPassenger(tag)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        player.teleportTo(mob.getX(), mob.getY(), mob.getZ());
        player.setYRot(mob.getYRot());
        player.setYHeadRot(mob.getYHeadRot());
        float health = mob.getHealth();
        BASE_MAX.put(player.getUUID(), mob.getMaxHealth());
        mob.discard();

        stashInventory(player);
        if (type.move() == Move.BOW) {
            giveBowKit(player);
        }
        if (type.move() == Move.CROSSBOW) {
            giveCrossbowKit(player);
        }
        ItemStack mobWeapon = mobWeapon(player, tag);
        if (!mobWeapon.isEmpty()) {
            // a skeleton's Power bow, a pillager's Piercing crossbow: the kit gets the mob's own enchantments
            for (int i = 0; i < 9; i++) {
                ItemStack kit = player.getInventory().getItem(i);
                if (isPuppetTool(kit) && (kit.is(Items.BOW) || kit.is(Items.CROSSBOW)) && kit.getItem() == mobWeapon.getItem()) {
                    copyEnchantments(mobWeapon, kit);
                }
            }
        }
        if (type == PuppetType.VINDICATOR) {
            applyVindicatorStats(player);
        }
        // melee-weapon mobs show their weapon in FIRST person: a visual puppet-tool copy in the main hand, which
        // vanilla swings on each attack (the bow/crossbow kits already fill the hand; this covers axes/swords/tridents).
        // (the drowned is excluded — a real held trident makes vanilla play the throw-charge USE animation; its
        // trident is rendered by hand in first person instead, raised in the spear pose while winding up.)
        if (type.move() != Move.BOW && type.move() != Move.CROSSBOW && type != PuppetType.DROWNED) {
            ItemStack display = mobWeapon.copy();
            if (!display.isEmpty()) {
                display.setCount(1);
                markPuppetTool(display);
                // fill the WHOLE hotbar (like the bow/crossbow kits) so it shows whatever slot the client has selected.
                for (int i = 0; i < 9; i++) {
                    player.getInventory().setItem(i, display.copy());
                }
            }
        }
        notePriorEffects(player, tag);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, mob.getMaxHealth());
        player.setData(WitchModAttachments.PUPPET_HEALTH, health);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        player.setData(WitchModAttachments.PUPPET_ACTION_READY, 0L);
        player.setData(WitchModAttachments.PUPPET_TYPE, type.id());
        scaleHealth(player);
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, type.speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            EffectUtil.addModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, type.reach() - reach.getBaseValue(),
                    AttributeModifier.Operation.ADD_VALUE);
        }
        if (type == PuppetType.DROWNED || type.group() == Group.FISH || type.group() == Group.SQUID || type.group() == Group.GUARDIAN
                || type.group() == Group.DOLPHIN || type.group() == Group.AXOLOTL) {
            double bonus = type == PuppetType.DROWNED ? Config.PUPPETEER_DROWNED_SWIM_BONUS.get()
                    : type == PuppetType.DOLPHIN ? Config.PUPPETEER_DOLPHIN_SWIM_BONUS.get()
                    : type == PuppetType.AXOLOTL ? Config.PUPPETEER_AXOLOTL_SWIM_BONUS.get()
                    : type.group() == Group.GUARDIAN ? Config.PUPPETEER_GUARDIAN_SWIM_BONUS.get()
                    : type.group() == Group.SQUID ? Config.PUPPETEER_SQUID_SWIM.get() : Config.PUPPETEER_FISH_SWIM_BONUS.get();
            EffectUtil.addModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
        if (mob.isBaby()) {
            // a baby puppet is baby-sized (hitbox and eye height), and baby zombies get their real speed boost.
            EffectUtil.addModifier(player, Attributes.SCALE, BABY_SCALE_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            if (type.group() == Group.ZOMBIE) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, BABY_SPEED_ID, Config.PUPPETEER_BABY_ZOMBIE_SPEED.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
        }
        if (type == PuppetType.BAT || type == PuppetType.PHANTOM || type == PuppetType.GHAST || type == PuppetType.BLAZE
                || type == PuppetType.BREEZE || type == PuppetType.VEX || type == PuppetType.ALLAY) {
            grantBatFlight(player);
        }
        if ((type == PuppetType.GHAST || type == PuppetType.VEX) && player.getAbilities().mayfly) {
            player.getAbilities().flying = true; // a ghast / vex never really lands (the vex also phases while flying)
            player.onUpdateAbilities();
        }
        if (type.group() == Group.HORSE) {
            applyHorseStats(player);
        }
        if (type == PuppetType.CAMEL) {
            // a camel steps up 1.5 blocks, like the real one
            EffectUtil.addModifier(player, Attributes.STEP_HEIGHT, HORSE_STEP_ID, 0.9, AttributeModifier.Operation.ADD_VALUE);
        }
        if (type.group() == Group.GOAT) {
            // goats shrug off falls (vanilla: 10 blocks less fall damage)
            EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, HORSE_FALL_ID, 10.0, AttributeModifier.Operation.ADD_VALUE);
        }
        if (type == PuppetType.WITCH) {
            giveWitchBelt(player);
        }
        if (type == PuppetType.ENDERMAN) {
            player.setData(WitchModAttachments.PUPPET_TP_CHARGES, 1);
            player.setData(WitchModAttachments.PUPPET_RAGE_END, 0L);
        }
        if (type.group() == Group.RABBIT) {
            // springy: higher jumps, and fall damage starts higher to match.
            double boost = type == PuppetType.KILLER_RABBIT ? Config.PUPPETEER_KILLER_RABBIT_JUMP_BOOST.get()
                    : Config.PUPPETEER_RABBIT_JUMP_BOOST.get();
            EffectUtil.addModifier(player, Attributes.JUMP_STRENGTH, RABBIT_JUMP_ID, boost, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, RABBIT_FALL_ID, boost * 4.0, AttributeModifier.Operation.ADD_VALUE);
        }
        if (type == PuppetType.IRON_GOLEM) {
            // immovable (no knockback at all), and swings on a weapon-style cooldown.
            EffectUtil.addModifier(player, Attributes.KNOCKBACK_RESISTANCE, GOLEM_KB_RESIST_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
            AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, GOLEM_ATTACK_SPEED_ID,
                        Config.PUPPETEER_GOLEM_ATTACK_SPEED.get() - attackSpeed.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
            }
        }
        if (type.group() == Group.BRUTE) {
            // hoglins shrug off knockback like the real thing.
            EffectUtil.addModifier(player, Attributes.KNOCKBACK_RESISTANCE, BRUTE_KB_RESIST_ID, 0.6, AttributeModifier.Operation.ADD_VALUE);
            if (type == PuppetType.HOGLIN) {
                HOGLIN_TIME.put(player.getUUID(), tag.getInt("TimeInOverworld"));
            }
        }
        player.refreshDimensions(); // eye height follows the mob (see onSize)
        double y = player.getY() + 1.0;
        level.sendParticles(ParticleTypes.SOUL, player.getX(), y, player.getZ(), 6, 0.3, 0.5, 0.3, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 8, 0.35, 0.5, 0.35, 0.04);
        level.sendParticles(BLESSED_DUST, player.getX(), y, player.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS, 1.0F, 0.9F);
        Blessings.PUPPETEER.get().markDiscoveredByVictim(player);
    }

    /**
     * step out (or be thrown out). the mob is put back where you stand with its remaining health (back on its own
     * scale); with {@code killedBy} it comes back only to die of that damage (normal drops), and a {@code consumed}
     * puppet (a detonated creeper) isn't restored at all. @return true if anything was possessed or binding.
     */
    private static boolean release(ServerPlayer player, @Nullable DamageSource killedBy, boolean consumed) {
        Binding binding = BINDINGS.get(player.getUUID());
        if (binding != null) {
            cancelBinding(player, binding, null);
        }
        String typeId = player.getData(WitchModAttachments.PUPPET_TYPE);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        boolean hadStash = !player.getData(WitchModAttachments.PUPPET_INVENTORY).isEmpty();
        if (typeId.isEmpty() && tag.isEmpty() && !hadStash) {
            return binding != null;
        }
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
        float scaledMax = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
        Float baseMax = BASE_MAX.remove(player.getUUID());
        Integer overworldTime = HOGLIN_TIME.remove(player.getUUID());
        if (overworldTime != null && !tag.isEmpty()) {
            tag.putInt("TimeInOverworld", overworldTime); // the restored hoglin carries on zombifying where you left off
        }
        endLunge(player);
        closeShops(player);
        closeSaddlebags(player);
        player.ejectPassengers(); // anyone riding a horse puppet hops off
        RIDERS_SENT.remove(player.getUUID());
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
        removeHorseStats(player);
        player.setData(WitchModAttachments.PUPPET_RAGE_END, 0L);
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, 0);
        MAULS.remove(player.getUUID());
        BITES.remove(player.getUUID());
        VEX_END.remove(player.getUUID());
        if (player.getData(WitchModAttachments.PUPPET_STEALTH)) { // leaving a stealthed trader: drop the invisibility
            player.removeEffect(MobEffects.INVISIBILITY);
            player.setData(WitchModAttachments.PUPPET_STEALTH, false);
        }
        ItemStack allayCarried = allayHeld(player); // an allay leaves holding something: drop it so it isn't lost
        if (!allayCarried.isEmpty()) {
            player.drop(allayCarried, false);
            setAllayHeld(player, ItemStack.EMPTY);
        }
        DRINKS.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, WITCH_DRINK_SLOW_ID);
        player.getCooldowns().removeCooldown(Items.POTION); // the belt's sweep mustn't linger on your real potions
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
        STREAMS.remove(player.getUUID());
        ATTACK_HELD.remove(player.getUUID());
        SLIME_GROUNDED.remove(player.getUUID());
        BLIZZARDS.remove(player.getUUID());
        BLIZZARD_TICK.remove(player.getUUID());
        unhide(player, tag); // a silverfish hidden in stone breaks out first (also after a crash: the spot is in the data)
        surface(player, tag); // and a burrowing endermite comes up to open air (likewise)
        EffectUtil.removeModifier(player, Attributes.JUMP_STRENGTH, RABBIT_JUMP_ID);
        EffectUtil.removeModifier(player, Attributes.SAFE_FALL_DISTANCE, RABBIT_FALL_ID);
        int till = tag.getInt(TILL_KEY); // a villager puppet's takings, paid out once your inventory is back
        tag.remove(TILL_KEY);
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, BRUTE_KB_RESIST_ID);
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, GOLEM_KB_RESIST_ID);
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, GOLEM_ATTACK_SPEED_ID);
        SWING_STRENGTH.remove(player.getUUID());
        LUNGES.remove(player.getUUID());
        LUNGE_BONUS.remove(player.getUUID());
        RECOVER_UNTIL.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, WOLF_FRENZY_ATTACK_ID);
        WOLF_SCENT.remove(player.getUUID());
        WOLF_PREY.remove(player.getUUID());
        CAT_ROCKETS.remove(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_TYPE, "");
        player.refreshDimensions();
        player.setData(WitchModAttachments.PUPPET_DATA, new CompoundTag());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        player.setData(WitchModAttachments.PUPPET_CHARGED, false);
        HELD.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID);
        EffectUtil.removeModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
        EffectUtil.removeModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID);
        EffectUtil.removeModifier(player, Attributes.SCALE, BABY_SCALE_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, BABY_SPEED_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FISH_LAND_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, BEAM_SLOW_ID);
        BEAM_RALLIED.remove(player.getUUID());
        LAST_POS.remove(player.getUUID());
        STILL.remove(player.getUUID());
        FISH_AIR.remove(player.getUUID());
        MOISTURE.remove(player.getUUID());
        REPELLENT.remove(player.getUUID());
        revokeBatFlight(player);
        List<MobEffectInstance> carried = takePuppetEffects(player, tag); // effects gained as the puppet go with the mob
        player.setSilent(false);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FOX_SPRINT_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, JOHNNY_SPEED_ID);
        CASTS.remove(player.getUUID());
        CAST_POSE.remove(player.getUUID());
        VEXES.remove(player.getUUID());
        VEX_PREY.remove(player.getUUID());
        FOX_CHEWING.remove(player.getUUID());
        restoreInventory(player);
        while (till > 0) {
            ItemStack pay = new ItemStack(Items.EMERALD, Math.min(64, till));
            till -= pay.getCount();
            if (!player.getInventory().add(pay)) {
                player.drop(pay, false);
            }
        }
        ServerLevel level = player.serverLevel();
        player.setData(WitchModAttachments.PUPPET_COOLDOWN_END,
                level.getGameTime() + Config.PUPPETEER_COOLDOWN_SECONDS.get() * 20L);
        if (!tag.isEmpty() && !consumed) {
            if (health > 0.0F) {
                // hand back the same FRACTION of health, on the mob's own (unblessed) scale.
                float fraction = scaledMax > 0.0F ? health / scaledMax : 1.0F;
                tag.putFloat("Health", Math.max(0.5F, fraction * (baseMax != null ? baseMax : scaledMax)));
            }
            Entity restored = EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                return e;
            });
            if (restored != null && level.tryAddFreshEntityWithPassengers(restored)) {
                if (killedBy != null && restored instanceof LivingEntity living) {
                    living.hurt(killedBy, Float.MAX_VALUE);
                } else if (restored instanceof Mob mob) {
                    carried.forEach(mob::addEffect); // what you picked up as it, it keeps
                    daze(mob);
                }
            }
        }
        double y = player.getY() + 1.0;
        level.sendParticles(ParticleTypes.SOUL, player.getX(), y, player.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 6, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 1.2F);
        return true;
    }

    /**
     * a mob you've just stepped out of stands dazed (no AI) for puppeteerReleaseGraceTicks, so it doesn't turn on you
     * the instant you're out. its own flags go on the mob as PUPPET_FROZEN (saved with it), so whatever happens — the
     * timer, a chunk unload, a crash, a restart — it thaws: by {@link #thawDazed} on time, or by {@link #onEntityJoin}
     * the next time it loads.
     */
    private static void daze(Mob mob) {
        int ticks = Config.PUPPETEER_RELEASE_GRACE_TICKS.get();
        if (ticks <= 0 || mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        mob.setData(WitchModAttachments.PUPPET_FROZEN, (mob.isNoAi() ? 1 : 0) | (mob.isInvulnerable() ? 2 : 0));
        mob.setNoAi(true);
        mob.setTarget(null);
        DAZED.put(mob.getUUID(), mob.level().getGameTime() + ticks);
    }

    /** gives a frozen / dazed mob back its own ai and invulnerability flags. */
    private static void thaw(Mob mob) {
        if (!mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        int flags = mob.getData(WitchModAttachments.PUPPET_FROZEN);
        mob.setNoAi((flags & 1) != 0);
        mob.setInvulnerable((flags & 2) != 0);
        mob.removeData(WitchModAttachments.PUPPET_FROZEN);
    }

    /** wakes dazed mobs whose time is up. one that's not loaded right now is simply forgotten — it thaws on load. */
    private static void thawDazed(net.minecraft.server.MinecraftServer server) {
        if (DAZED.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        DAZED.entrySet().removeIf(e -> {
            if (now < e.getValue()) {
                return false;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(e.getKey()) instanceof Mob mob) {
                    thaw(mob);
                    break;
                }
            }
            return true;
        });
    }

    /**
     * your own max health scales the puppet's: a tank-blessed player makes a proportionally tankier puppet, an
     * allergic one a frailer puppet. keeps the current health's fraction when the max moves.
     */
    private static void scaleHealth(ServerPlayer player) {
        Float base = BASE_MAX.get(player.getUUID());
        AttributeInstance maxAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (base == null || maxAttr == null || maxAttr.getBaseValue() <= 0.0) {
            return;
        }
        float max = Math.max(1.0F, (float) (base * maxAttr.getValue() / maxAttr.getBaseValue()));
        float oldMax = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
        if (Math.abs(max - oldMax) < 0.01F) {
            return;
        }
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, max);
        player.setData(WitchModAttachments.PUPPET_HEALTH, oldMax > 0.0F ? Math.min(max, health * max / oldMax) : max);
    }

    /** the whole inventory (armour and offhand too) is put away so nothing can be used while possessed. */
    private static void stashInventory(ServerPlayer player) {
        CompoundTag stash = new CompoundTag();
        stash.put("Items", player.getInventory().save(new ListTag()));
        stash.putInt("Selected", player.getInventory().selected);
        player.setData(WitchModAttachments.PUPPET_INVENTORY, stash);
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * skeleton: a real bow in every hotbar slot (so scrolling never leaves you empty-handed) plus one arrow. the bows
     * are unbreakable with Infinity — the arrow is never used up and shots can't be picked up — and every piece is
     * tagged as a puppet tool, so it can't be dropped or swapped and is simply gone when you leave.
     */
    private static void giveBowKit(ServerPlayer player) {
        ItemStack bow = new ItemStack(Items.BOW);
        player.level().registryAccess().lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .flatMap(r -> r.get(net.minecraft.world.item.enchantment.Enchantments.INFINITY))
                .ifPresent(infinity -> bow.enchant(infinity, 1));
        bow.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
        markPuppetTool(bow);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, bow.copy());
        }
        ItemStack arrow = new ItemStack(Items.ARROW);
        markPuppetTool(arrow);
        player.getInventory().setItem(9, arrow);
        player.inventoryMenu.broadcastChanges();
    }

    private static final String PUPPET_TOOL_KEY = "witchmod_puppet_tool";

    private static void markPuppetTool(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PUPPET_TOOL_KEY, true);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    public static boolean isPuppetTool(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(PUPPET_TOOL_KEY);
    }

    private static void restoreInventory(ServerPlayer player) {
        CompoundTag stash = player.getData(WitchModAttachments.PUPPET_INVENTORY);
        if (stash.isEmpty()) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_INVENTORY, new CompoundTag());
        // anything that somehow got in meanwhile is handed back on top, never lost.
        List<ItemStack> strays = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && !isPuppetTool(s)) {
                strays.add(s.copy());
            }
        }
        player.getInventory().load(stash.getList("Items", Tag.TAG_COMPOUND));
        player.getInventory().selected = stash.getInt("Selected");
        for (ItemStack s : strays) {
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static SoundEvent ambientOf(PuppetType type) {
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
            case EVOKER -> SoundEvents.EVOKER_AMBIENT;
            case WOLF -> SoundEvents.WOLF_AMBIENT;
            case CAT -> SoundEvents.CAT_AMBIENT;
            case WANDERING_TRADER -> SoundEvents.WANDERING_TRADER_AMBIENT;
            case RAVAGER -> SoundEvents.RAVAGER_AMBIENT;
            case VEX -> SoundEvents.VEX_AMBIENT;
            case ALLAY -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
            default -> SoundEvents.ZOMBIE_AMBIENT;
        };
    }

    private static SoundEvent flopOf(PuppetType type) {
        return switch (type) {
            case SALMON -> SoundEvents.SALMON_FLOP;
            case PUFFERFISH -> SoundEvents.PUFFER_FISH_FLOP;
            case TROPICAL_FISH -> SoundEvents.TROPICAL_FISH_FLOP;
            default -> SoundEvents.COD_FLOP;
        };
    }

    private static SoundEvent hurtOf(PuppetType type) {
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
            case EVOKER -> SoundEvents.EVOKER_HURT;
            case WOLF -> SoundEvents.WOLF_HURT;
            case CAT -> SoundEvents.CAT_HURT;
            case WANDERING_TRADER -> SoundEvents.WANDERING_TRADER_HURT;
            case RAVAGER -> SoundEvents.RAVAGER_HURT;
            case VEX -> SoundEvents.VEX_HURT;
            case ALLAY -> SoundEvents.ALLAY_HURT;
            default -> SoundEvents.ZOMBIE_HURT;
        };
    }

    // --- moves ---------------------------------------------------------------------------------------

    /** a right-click while possessed: crouching leaves; otherwise a pressed move (held moves go through setHeld). */
    private static void rightClick(ServerPlayer player, @Nullable Entity target) {
        PuppetType type = possessed(player);
        if (type == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            if (type == PuppetType.ENDERMAN && endermanCarry(player)) {
                return; // crouch + right-click on a block: pick up / put down (at nothing: leave, as ever)
            }
            release(player, null, false);
            return;
        }
        if (type == PuppetType.ENDERMAN) {
            endermanTeleport(player); // charges, not the plain cooldown
            return;
        }
        if (type == PuppetType.SILVERFISH) {
            silverfishAction(player); // burst / embed / call (bursting out ignores the cooldown)
            return;
        }
        if (type == PuppetType.WANDERING_TRADER) {
            toggleTraderStealth(player); // right-click: drink invisibility / opt back out
            return;
        }
        if (type == PuppetType.VEX) {
            vexLaugh(player); // right-click: just the annoying vex laugh
            return;
        }
        if (type == PuppetType.WOLF) {
            return; // a wolf is swing-only: a bite, a pounce, or (while rabid) a maul — right-click does nothing.
        }
        if (type == PuppetType.EVOKER) {
            evokerCast(player, target); // casts the selected belt spell (or a contextual wololo / convert), own cooldowns
            return;
        }
        if (type.move().held() || type.move() == Move.NONE || !ready(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (type.move()) {
            case RALLY -> {
                cooldown(player, Config.PUPPETEER_ZOMBIE_RALLY_COOLDOWN_TICKS.get());
                rally(player, type);
            }
            case OINK, MOO -> {
                cooldown(player, Config.PUPPETEER_ANIMAL_SOUND_COOLDOWN_TICKS.get());
                level.playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS, 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
            }
            case GRAZE -> {
                cooldown(player, Config.PUPPETEER_ANIMAL_SOUND_COOLDOWN_TICKS.get());
                graze(player);
            }
            case LEAP -> {
                cooldown(player, Config.PUPPETEER_FISH_LEAP_COOLDOWN_TICKS.get());
                leap(player, type);
            }
            case HMM -> {
                cooldown(player, Config.PUPPETEER_VILLAGER_HMM_COOLDOWN_TICKS.get());
                hmm(player);
            }
            case DIVE -> startDive(player); // sets its own cooldown
            case MAUL -> startMaulLunge(player); // sets its own cooldown
            case BURROW -> toggleBurrow(player);
            case FIREBALL -> ghastShot(player); // sets its own cooldown
            case MUG -> foxPounce(player); // sets its own cooldown
            case SPIT -> {
                cooldown(player, Config.PUPPETEER_LLAMA_SPIT_COOLDOWN_TICKS.get());
                spit(player);
            }
            case INSPIRE -> {
                cooldown(player, Config.PUPPETEER_DOLPHIN_INSPIRE_COOLDOWN_TICKS.get());
                inspire(player);
            }
            case LUNGE -> {
                cooldown(player, type == PuppetType.ZOGLIN ? Config.PUPPETEER_ZOGLIN_LUNGE_COOLDOWN_TICKS.get()
                        : Config.PUPPETEER_HOGLIN_LUNGE_COOLDOWN_TICKS.get());
                startLunge(player, type);
            }
            case FLEE -> {
                cooldown(player, Config.PUPPETEER_SQUID_FLEE_COOLDOWN_TICKS.get());
                flee(player, type);
            }
            case CONVERT -> {
                if (!(target instanceof net.minecraft.world.entity.animal.Pig pig)) {
                    player.displayClientMessage(Component.translatable("witchmod.puppeteer.convert_hint")
                            .withStyle(ChatFormatting.GRAY), true);
                    return; // no pig, no cooldown spent
                }
                cooldown(player, Config.PUPPETEER_PIGLIN_CONVERT_COOLDOWN_TICKS.get());
                convertPigs(player, pig);
            }
            case SCARED -> {
                cooldown(player, Config.PUPPETEER_CAT_SCARE_COOLDOWN_TICKS.get());
                catScared(player);
            }
            default -> { }
        }
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** squids: Flee — a short dash and a burst of ink that blinds everything nearby (a glow squid's also lights them up). */
    private static void flee(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        boolean glow = type == PuppetType.GLOW_SQUID;
        Vec3 dash = player.getLookAngle().scale(Config.PUPPETEER_SQUID_FLEE_DASH.get() * (player.isInWater() ? 1.0 : 0.3));
        player.setDeltaMovement(player.getDeltaMovement().add(dash));
        player.hurtMarked = true;
        double x = player.getX();
        double y = player.getY() + 0.5;
        double z = player.getZ();
        level.sendParticles(glow ? ParticleTypes.GLOW_SQUID_INK : ParticleTypes.SQUID_INK, x, y, z, 40, 0.6, 0.6, 0.6, 0.05);
        level.playSound(null, player.blockPosition(), glow ? SoundEvents.GLOW_SQUID_SQUIRT : SoundEvents.SQUID_SQUIRT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        double r = Config.PUPPETEER_SQUID_INK_RADIUS.get();
        int ticks = Config.PUPPETEER_SQUID_INK_SECONDS.get() * 20;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e != player && e.isAlive() && e.distanceToSqr(player) <= r * r)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), player);
            if (glow) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), player);
            }
        }
    }

    /**
     * zombified piglin: Convert — the pig you right-click, and every pig around it, turns into a zombified piglin the
     * way lightning would do it (golden sword, babies stay babies, names kept).
     */
    private static void convertPigs(ServerPlayer player, net.minecraft.world.entity.animal.Pig clicked) {
        ServerLevel level = player.serverLevel();
        double r = Config.PUPPETEER_PIGLIN_CONVERT_RADIUS.get();
        List<net.minecraft.world.entity.animal.Pig> pigs = new ArrayList<>(level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Pig.class, clicked.getBoundingBox().inflate(r), Entity::isAlive));
        if (!pigs.contains(clicked)) {
            pigs.add(clicked);
        }
        for (net.minecraft.world.entity.animal.Pig pig : pigs) {
            if (!EventHooks.canLivingConvert(pig, EntityType.ZOMBIFIED_PIGLIN, timer -> {})) {
                continue;
            }
            net.minecraft.world.entity.monster.ZombifiedPiglin zp = EntityType.ZOMBIFIED_PIGLIN.create(level);
            if (zp == null) {
                continue;
            }
            zp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            zp.moveTo(pig.getX(), pig.getY(), pig.getZ(), pig.getYRot(), pig.getXRot());
            zp.setNoAi(pig.isNoAi());
            zp.setBaby(pig.isBaby());
            if (pig.hasCustomName()) {
                zp.setCustomName(pig.getCustomName());
                zp.setCustomNameVisible(pig.isCustomNameVisible());
            }
            zp.setPersistenceRequired();
            EventHooks.onLivingConvert(pig, zp);
            level.addFreshEntity(zp);
            pig.discard();
            level.sendParticles(ParticleTypes.SMOKE, zp.getX(), zp.getY() + 0.6, zp.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
        level.playSound(null, clicked.blockPosition(), SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, SoundSource.PLAYERS, 1.5F, 0.8F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * zombified piglin: no rally — but hitting something, or being hit, turns the whole pack within range on it, and
     * sends you and them into a frenzy (Speed II).
     */
    private static void piglinCall(ServerPlayer player, LivingEntity foe) {
        if (foe == player || !foe.isAlive()) {
            return;
        }
        double r = Config.PUPPETEER_PIGLIN_CALL_RADIUS.get();
        int ticks = Config.PUPPETEER_PIGLIN_FRENZY_SECONDS.get() * 20;
        for (net.minecraft.world.entity.monster.ZombifiedPiglin zp : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.ZombifiedPiglin.class, player.getBoundingBox().inflate(r),
                z -> z != foe && z.isAlive())) {
            zp.setTarget(foe);
            zp.setPersistentAngerTarget(foe.getUUID());
            zp.startPersistentAngerTimer();
            zp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
    }

    private static boolean ready(ServerPlayer player) {
        return player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_ACTION_READY);
    }

    /** the second move's cooldown (the chicken's explosive egg). */
    private static boolean ready2(ServerPlayer player) {
        return player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_ACTION2_READY);
    }

    private static void cooldown(ServerPlayer player, int ticks) {
        player.setData(WitchModAttachments.PUPPET_ACTION_READY, player.level().getGameTime() + ticks);
    }

    /** a right-click on thin air with an empty hand only happens client-side; it's relayed here by packet. */
    public static void onRightClickAir(ServerPlayer player) {
        rightClick(player, null);
    }

    /**
     * zombies: a groan that MARKS whoever you're looking at (within puppeteerZombieRallyRange) — it glows, and every
     * zombie variant near you goes after it with a speed boost. with none around and dark enough, 1-2 of your own
     * kind claw up to help.
     */
    private static void rally(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS, 2.5F, 0.55F);
        LivingEntity prey = lookTarget(player, Config.PUPPETEER_ZOMBIE_RALLY_RANGE.get());
        boolean dark = level.getMaxLocalRawBrightness(player.blockPosition()) <= Config.PUPPETEER_ZOMBIE_RALLY_SPAWN_LIGHT.get();
        answerCall(level, player, prey, player.position(), Zombie.class, type.entityType(), dark, false);
    }

    /** the living thing under your crosshair within {@code range} (line of sight — walls block it), or null. */
    @Nullable
    private static LivingEntity lookTarget(ServerPlayer player, double range) {
        return beamHit(player, range).entity();
    }

    /** skeletons: whatever your arrow hits, every skeleton near you turns its bow on too. no mark, no summoning. */
    private static void volley(ServerPlayer shooter, LivingEntity hit) {
        double r = Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        for (net.minecraft.world.entity.monster.AbstractSkeleton skeleton : shooter.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.AbstractSkeleton.class, shooter.getBoundingBox().inflate(r),
                s -> s != hit && s.isAlive())) {
            skeleton.setTarget(hit);
        }
    }

    /**
     * spiders: a swing pounces you forward the way a spider leaps at its prey (on the ground, off cooldown) — and a
     * swing MID-JUMP pounces again, harder, once per jump, so pouncing doubles as travel.
     */
    public static void onSwing(ServerPlayer player) {
        PuppetType type = possessed(player);
        if (type == PuppetType.ELDER_GUARDIAN) {
            elderCall(player);
            return;
        }
        if (type == PuppetType.EVOKER) {
            evokerHorn(player); // left-click: the rallying horn
            return;
        }
        if (type == PuppetType.WITCH) {
            witchDrink(player); // left-click: drink the picked potion
            return;
        }
        if (type == PuppetType.BREEZE) {
            breezeShot(player); // left-click: a wind charge
            return;
        }
        if (type == PuppetType.CAT) {
            catMeow(player); // left-click: a meow
            return;
        }
        if (type == PuppetType.WANDERING_TRADER) {
            if (ready(player)) { // a wandering trader's hmm is on the LEFT-click (right-click is its stealth)
                cooldown(player, Config.PUPPETEER_VILLAGER_HMM_COOLDOWN_TICKS.get());
                hmm(player);
            }
            return;
        }
        if (type == PuppetType.VEX) {
            startVexLunge(player); // left-click: the lunge — its only way to hit
            return;
        }
        if (type == null || type.move() != Move.POUNCE) {
            return;
        }
        boolean wolf = type == PuppetType.WOLF;
        // a rabid wolf's swing grabs + bursts like a (tamer) killer-bunny maul instead of a plain leap.
        if (wolf && frenzied(player)) {
            if (ready(player)) {
                startMaulLunge(player);
            }
            return;
        }
        boolean air = !player.onGround();
        if (air ? AIR_POUNCED.contains(player.getUUID()) || player.isInWater() || player.onClimbable() : !ready(player)) {
            return;
        }
        double base = wolf ? Config.PUPPETEER_WOLF_POUNCE_POWER.get() : Config.PUPPETEER_SPIDER_POUNCE_POWER.get();
        double power = base * (air ? Config.PUPPETEER_SPIDER_AIR_POUNCE_POWER.get() : 1.0);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(power);
        Vec3 now = player.getDeltaMovement();
        double groundLift = wolf ? Config.PUPPETEER_WOLF_POUNCE_LIFT.get() : Config.PUPPETEER_SPIDER_POUNCE_LIFT.get();
        double lift = air ? Math.max(now.y, 0.0) + Config.PUPPETEER_SPIDER_AIR_POUNCE_LIFT.get() : groundLift;
        if (air) {
            AIR_POUNCED.add(player.getUUID()); // cleared on landing (tick)
        }
        player.setDeltaMovement(flat.x + now.x * 0.2, lift, flat.z + now.z * 0.2);
        player.hurtMarked = true;
        cooldown(player, Config.PUPPETEER_SPIDER_POUNCE_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), wolf ? SoundEvents.WOLF_GROWL : SoundEvents.SPIDER_AMBIENT,
                SoundSource.PLAYERS, 1.0F, wolf ? 1.0F : 1.2F);
    }

    /**
     * the shared rally: mark {@code prey}, send every {@code kin} within range of {@code centre} after it with a
     * speed boost, and if none are there (and {@code canSummon}) raise 1-2 {@code spawnType} near it.
     */
    private static void answerCall(ServerLevel level, ServerPlayer caller, @Nullable LivingEntity prey, Vec3 centre,
                                   Class<? extends Mob> kin, EntityType<?> spawnType, boolean canSummon, boolean allowWater) {
        double r = Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        int ticks = Config.PUPPETEER_ZOMBIE_RALLY_SECONDS.get() * 20;
        if (prey != null) {
            prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
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
            }
        }
    }

    /** a reinforcement a few blocks from {@code centre}, standing on solid ground (or swimming, if allowed). */
    @Nullable
    private static Mob summonNear(ServerLevel level, Vec3 centre, EntityType<?> type, boolean allowWater) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = 3.0 + level.random.nextDouble() * 3.0;
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

    /** sheep: eat the grass underfoot (or the tuft you're standing in) and grow your wool back, like the real thing. */
    private static void graze(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        BlockState here = level.getBlockState(at);
        boolean ate = false;
        if (here.is(Blocks.SHORT_GRASS) || here.is(Blocks.TALL_GRASS)) {
            level.destroyBlock(at, false);
            ate = true;
        } else if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK)) {
            level.levelEvent(2001, at.below(), net.minecraft.world.level.block.Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
            level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 2);
            ate = true;
        }
        if (ate) {
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            tag.putBoolean("Sheared", false);
            player.setData(WitchModAttachments.PUPPET_DATA, tag);
        }
    }

    /** clients report right-click being held / let go. held moves charge while held and fire on release. */
    public static void setHeld(ServerPlayer player, boolean held) {
        PuppetType type = possessed(player);
        if (type == null || !type.move().held()) {
            HELD.remove(player.getUUID());
            return;
        }
        if (held && !player.isShiftKeyDown()) {
            HELD.add(player.getUUID());
            return;
        }
        boolean wasHeld = HELD.remove(player.getUUID());
        if (wasHeld && type.move() != Move.FUSE) {
            int charge = player.getData(WitchModAttachments.PUPPET_FUSE);
            player.setData(WitchModAttachments.PUPPET_FUSE, 0);
            fireHeld(player, type, charge);
        }
    }

    /** creeper fuse winds up while held and back down when let go; trident / egg just build charge. */
    private static void tickHold(ServerPlayer player, PuppetType type) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        boolean held = HELD.contains(player.getUUID());
        if (type.move() == Move.FUSE) {
            int next = held ? hold + chargeStep(player) : Math.max(0, hold - 1);
            if (next == hold) {
                return;
            }
            if (hold == 0 && next > 0) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.0F, 0.5F);
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID, -Config.PUPPETEER_CREEPER_FUSE_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else if (next == 0) {
                EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FUSE_SLOW_ID);
            }
            player.setData(WitchModAttachments.PUPPET_FUSE, next);
            if (next >= Config.PUPPETEER_CREEPER_FUSE_TICKS.get()) {
                detonate(player);
            }
            return;
        }
        // a chicken charges its explosive egg on that egg's own cooldown; everything else on the main one.
        // (the snow golem's volley too: a tap still throws a plain snowball while the volley recharges.)
        boolean canCharge = type == PuppetType.CHICKEN || type == PuppetType.SNOW_GOLEM ? ready2(player) : ready(player);
        if (held && canCharge) {
            if (hold == 0 && type.group() == Group.GOAT) {
                player.serverLevel().playSound(null, player.blockPosition(), type == PuppetType.SCREAMING_GOAT
                        ? SoundEvents.GOAT_SCREAMING_PREPARE_RAM : SoundEvents.GOAT_PREPARE_RAM, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            if (hold == 0 && type == PuppetType.DROWNED) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            if (hold == 0 && type == PuppetType.RAVAGER) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F); // the building growl
            }
            player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(hold + chargeStep(player), 200));
        }
    }

    /** let go of a held move: a drowned throws its trident, a chicken lays (tap) or hurls an explosive egg (charged). */
    private static void fireHeld(ServerPlayer player, PuppetType type, int charge) {
        ServerLevel level = player.serverLevel();
        if (type == PuppetType.DROWNED) {
            if (charge < MIN_CHARGE || !ready(player)) {
                return;
            }
            ThrownTrident trident = new ThrownTrident(level, player, new ItemStack(Items.TRIDENT));
            trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.5F, 1.0F);
            trident.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(trident);
            TRIDENTS.put(trident, player.getUUID());
            level.playSound(null, trident, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.swing(InteractionHand.MAIN_HAND, true);
            cooldown(player, Config.PUPPETEER_DROWNED_TRIDENT_COOLDOWN_TICKS.get());
        } else if (type == PuppetType.BLAZE) {
            blazeRelease(player, charge);
            return;
        } else if (type == PuppetType.WITCH) {
            // a quick click throws a splash; held long enough it's brewed into a lingering potion
            witchThrow(player, charge >= Config.PUPPETEER_WITCH_LINGER_CHARGE_TICKS.get());
            return;
        } else if (type == PuppetType.CAMEL) {
            camelDash(player, charge);
            return;
        } else if (type.group() == Group.VINDICATOR) {
            vindicatorLunge(player, charge);
            return;
        } else if (type.group() == Group.GOAT) {
            goatRam(player, type, charge);
            return;
        } else if (type == PuppetType.RAVAGER) {
            ravagerRoar(player, charge);
            return;
        } else if (type == PuppetType.ALLAY) {
            if (charge >= Config.PUPPETEER_ALLAY_MITOSIS_CHARGE_TICKS.get() && ready2(player) && nearMusic(player)) {
                allayMitosis(player); // a full hold within earshot of music: split off a copy
            } else {
                allayTap(player); // a tap: take from a container you're eyeing, else pick up / drop ground items
            }
            return;
        } else if (type == PuppetType.BREEZE) {
            breezeRelease(player, charge);
            return;
        } else if (type == PuppetType.SNOW_GOLEM) {
            snowGolemRelease(player, charge);
        } else if (type.move() == Move.OFFER) {
            return; // lowering the poppy before it was taken: nothing given, nothing spent
        } else if (type.move() == Move.PLAY_DEAD) {
            if (charge > 0) { // getting back up starts the cooldown
                cooldown(player, Config.PUPPETEER_AXOLOTL_PLAY_DEAD_COOLDOWN_TICKS.get());
            }
            return;
        } else if (type.move() == Move.BEAM) {
            // let go mid-beam: a weaker burst if it ran long enough, otherwise it just fizzles out.
            if (charge >= Config.PUPPETEER_GUARDIAN_BEAM_MIN_BURST_TICKS.get()) {
                burst(player, type, Mth.clamp(charge / (float) Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get(), 0.0F, 1.0F));
            } else {
                endBeam(player);
                if (charge > 0) {
                    level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.8F);
                }
            }
            return; // burst counts its own action
        } else if (type == PuppetType.CHICKEN) {
            if (charge >= MIN_CHARGE) {
                if (!ready2(player)) {
                    return;
                }
                ExplosiveEggEntity egg = new ExplosiveEggEntity(level, player);
                egg.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                        BowItem.getPowerForTime(charge) * Config.PUPPETEER_CHICKEN_EGG_VELOCITY.get().floatValue(), 1.0F);
                level.addFreshEntity(egg);
                level.playSound(null, player.blockPosition(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
                player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                        level.getGameTime() + Config.PUPPETEER_CHICKEN_EXPLOSIVE_COOLDOWN_TICKS.get());
            } else {
                if (!ready(player)) {
                    return;
                }
                ItemEntity egg = new ItemEntity(level, player.getX(), player.getY() + 0.3, player.getZ(), new ItemStack(Items.EGG));
                level.addFreshEntity(egg);
                level.playSound(null, player.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0F,
                        (level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F);
                cooldown(player, Config.PUPPETEER_CHICKEN_EGG_COOLDOWN_TICKS.get());
            }
        }
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** the creeper goes off like a real one (radius from its own data, doubled when charged) and is spent. */
    private static void detonate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        int radius = tag.contains("ExplosionRadius") ? tag.getByte("ExplosionRadius") : 3;
        boolean charged = player.getData(WitchModAttachments.PUPPET_CHARGED);
        EXPLODING.add(player.getUUID());
        try {
            level.explode(player, player.getX(), player.getY(), player.getZ(), radius * (charged ? 2.0F : 1.0F),
                    Level.ExplosionInteraction.MOB);
        } finally {
            EXPLODING.remove(player.getUUID());
        }
        release(player, null, true);
    }

    /** a drowned's trident: whatever it hits is marked for nearby drowned, who rise from dark or water if none are around. */
    private static void tridentRally(ServerPlayer thrower, LivingEntity hit) {
        ServerLevel level = thrower.serverLevel();
        BlockPos leaderAt = thrower.blockPosition();
        boolean dark = level.getMaxLocalRawBrightness(leaderAt) <= Config.PUPPETEER_ZOMBIE_RALLY_SPAWN_LIGHT.get();
        boolean wet = thrower.isInWater() || BlockPos.betweenClosedStream(leaderAt.offset(-3, -2, -3), leaderAt.offset(3, 1, 3))
                .anyMatch(p -> level.getFluidState(p).is(FluidTags.WATER));
        // raised near YOU (the leader), not the target — like the other undead rallies.
        answerCall(level, thrower, hit, thrower.position(), Drowned.class, EntityType.DROWNED, dark || wet, true);
    }

    /** farm animals: someone holding your food drags you over to them, nose first. */
    private static void lure(ServerPlayer player, PuppetType type) {
        TagKey<Item> food = type.lureFood();
        double range = Config.PUPPETEER_ANIMAL_LURE_RANGE.get();
        if (food == null || range <= 0.0) {
            return;
        }
        Player lurer = null;
        double best = range * range;
        for (Player other : player.level().players()) {
            if (other == player || other.isSpectator() || isPuppet(other)) {
                continue;
            }
            double d = other.distanceToSqr(player);
            if (d < best && (other.getMainHandItem().is(food) || other.getOffhandItem().is(food))) {
                best = d;
                lurer = other;
            }
        }
        if (lurer == null || best < 2.25) {
            return;
        }
        Vec3 pull = lurer.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize()
                .scale(Config.PUPPETEER_ANIMAL_LURE_STRENGTH.get());
        player.setDeltaMovement(player.getDeltaMovement().add(pull));
        player.hurtMarked = true;
    }

    private static void sunburn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (level.isDay() && !player.isInWaterRainOrBubble() && level.canSeeSky(head)
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.igniteForSeconds(8.0F);
        }
    }

    /** heal the puppet (capped at its max). */
    private static void scaleHeal(ServerPlayer player, float amount) {
        float max = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
        if (amount > 0.0F && health < max) {
            player.setData(WitchModAttachments.PUPPET_HEALTH, Math.min(max, health + amount));
        }
    }

    // --- events --------------------------------------------------------------------------------------

    /**
     * possess (crouch + right-click a possessable mob); a right-click on anything while inside a puppet; and other
     * players milking a cow puppet or shearing a sheep puppet.
     */
    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player p = event.getEntity();
        if (!isBusy(p) && event.getTarget() instanceof ServerPlayer puppet && possessed(puppet) != null
                && interactWithPuppet(p, event.getHand(), event.getItemStack(), puppet)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (drawsBow(p)) {
            // a skeleton just draws its bow at whatever it's looking at: no interaction, but the item use goes ahead.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        if (isBusy(p)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (p instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
                rightClick(player, event.getTarget());
            }
            return;
        }
        if (!(p instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !EffectManager.isActive(player, Blessings.PUPPETEER)) {
            return;
        }
        Entity clicked = event.getTarget() instanceof net.neoforged.neoforge.entity.PartEntity<?> part ? part.getParent() : event.getTarget();
        if (!(clicked instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        // always say WHY a mob can't be possessed, rather than silently doing nothing (the click still does whatever
        // it normally would).
        String refusal = refusalKey(living);
        if (refusal != null || !(living instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable(refusal != null ? refusal : "witchmod.puppeteer.cant_possess",
                    living.getType().getDescription()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        long now = player.level().getGameTime();
        long ready = player.getData(WitchModAttachments.PUPPET_COOLDOWN_END);
        if (now < ready) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.cooldown",
                    (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        startPossession(player, mob);
    }

    /** vanilla mobs ruled out for good — bosses, and ones that break the gimmick (see docs/PUPPETEER_MOBS.md). */
    private static final Set<EntityType<?>> DISQUALIFIED = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER,
            EntityType.GIANT, EntityType.ILLUSIONER, EntityType.VEX);
    /** scoreboard tags marking vanilla mobs that are really this mod's characters (guardian angel, solicitor, ...). */
    private static final Set<String> CHARACTER_TAGS = Set.of("witchmod_guardian", "witchmod_solicitor",
            "witchmod_possessed", "witchmod_character");

    /**
     * why {@code living} can't be possessed, as a lang key — or null if it can. separate messages for this mod's own
     * creatures, other mods' creatures, ruled-out vanilla mobs, and vanilla mobs that simply aren't in yet.
     */
    @Nullable
    private static String refusalKey(LivingEntity living) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        if (id.getNamespace().equals(WitchMod.MODID) || living.getTags().stream().anyMatch(CHARACTER_TAGS::contains)) {
            return "witchmod.puppeteer.refuse_own";
        }
        if (!id.getNamespace().equals("minecraft")) {
            return "witchmod.puppeteer.refuse_foreign";
        }
        if (DISQUALIFIED.contains(living.getType())) {
            return "witchmod.puppeteer.refuse_disqualified";
        }
        return PuppetType.of(living.getType()) == null ? "witchmod.puppeteer.cant_possess" : null;
    }

    /**
     * shearing a mooshroom puppet: five mushrooms of its colour drop and the puppet BECOMES a cow puppet (the saved
     * mob is rewritten as a cow, so it also comes back as one) — exactly what shears do to a real mooshroom.
     */
    private static void shearMooshroom(ServerPlayer puppet, Player user, InteractionHand hand, ItemStack shears) {
        ServerLevel level = puppet.serverLevel();
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        Item mushroom = "brown".equals(tag.getString("Type")) ? Items.BROWN_MUSHROOM : Items.RED_MUSHROOM;
        for (int i = 0; i < 5; i++) {
            level.addFreshEntity(new ItemEntity(level, puppet.getX(), puppet.getY(1.0), puppet.getZ(), new ItemStack(mushroom)));
        }
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW).toString());
        tag.remove("Type");
        tag.remove("stew_effects");
        puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
        puppet.setData(WitchModAttachments.PUPPET_TYPE, PuppetType.COW.id());
        level.sendParticles(ParticleTypes.EXPLOSION, puppet.getX(), puppet.getY(0.5), puppet.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, puppet.blockPosition(), SoundEvents.MOOSHROOM_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        shears.hurtAndBreak(1, user, LivingEntity.getSlotForHand(hand));
    }

    /** a bucket on a cow puppet milks it; shears on an unshorn sheep puppet take its wool. @return true if handled. */
    private static boolean interactWithPuppet(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet) {
        PuppetType type = possessed(puppet);
        if (type != null && (type.group() == Group.HORSE || type.group() == Group.CAMEL)) {
            return horseInteract(user, hand, stack, puppet, type);
        }
        if (type == PuppetType.VILLAGER || type == PuppetType.WANDERING_TRADER) {
            // right-clicking a villager / wandering-trader puppet opens its shop (whatever's in your hand)
            if (user instanceof ServerPlayer customer && hand == InteractionHand.MAIN_HAND) {
                openShop(puppet, customer);
            }
            return true;
        }
        if (type == PuppetType.SNOW_GOLEM && stack.is(Items.SHEARS)
                && puppet.getData(WitchModAttachments.PUPPET_DATA).getBoolean("Pumpkin")) {
            if (!user.level().isClientSide) {
                CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
                tag.putBoolean("Pumpkin", false); // sheared off, like the real thing — and it stays off when you leave
                puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
                puppet.spawnAtLocation(new ItemStack(Items.CARVED_PUMPKIN), 1.7F);
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.SNOW_GOLEM_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, user, LivingEntity.getSlotForHand(hand));
            }
            return true;
        }
        if (type == PuppetType.MOOSHROOM && stack.is(Items.BOWL)) {
            if (!user.level().isClientSide) {
                user.setItemInHand(hand, ItemUtils.createFilledResult(stack, user, new ItemStack(Items.MUSHROOM_STEW), false));
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.MOOSHROOM_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return true;
        }
        if (type == PuppetType.MOOSHROOM && stack.is(Items.SHEARS)) {
            if (!user.level().isClientSide) {
                shearMooshroom(puppet, user, hand, stack);
            }
            return true;
        }
        if (type == PuppetType.IRON_GOLEM && stack.is(Items.IRON_INGOT)
                && puppet.getData(WitchModAttachments.PUPPET_HEALTH) < puppet.getData(WitchModAttachments.PUPPET_MAX_HEALTH)) {
            if (!user.level().isClientSide) {
                stack.consume(1, user); // patching up a golem, like the real thing
                scaleHeal(puppet, Config.PUPPETEER_GOLEM_IRON_HEAL.get().floatValue());
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0F,
                        1.0F + (puppet.getRandom().nextFloat() - puppet.getRandom().nextFloat()) * 0.2F);
            }
            return true;
        }
        if (type == PuppetType.DOLPHIN && stack.is(ItemTags.FISHES)) {
            if (!user.level().isClientSide) {
                stack.consume(1, user); // feeding a dolphin, like the real thing
                dolphinEat(puppet);
            }
            return true;
        }
        if (type == PuppetType.WOLF && stack.is(ItemTags.WOLF_FOOD)
                && puppet.getData(WitchModAttachments.PUPPET_HEALTH) < puppet.getData(WitchModAttachments.PUPPET_MAX_HEALTH)) {
            if (!user.level().isClientSide) {
                stack.consume(1, user); // feeding a wolf its meat heals it, exactly like a real wolf
                scaleHeal(puppet, Config.PUPPETEER_WOLF_FEED_HEAL.get().floatValue());
                ServerLevel level = puppet.serverLevel();
                level.playSound(null, puppet.blockPosition(), SoundEvents.WOLF_PANT, SoundSource.PLAYERS, 1.0F, 1.2F);
                level.sendParticles(ParticleTypes.HEART, puppet.getX(), puppet.getY() + 0.6, puppet.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            }
            return true;
        }
        if ((type == PuppetType.COW || type == PuppetType.MOOSHROOM) && stack.is(Items.BUCKET)) {
            if (!user.level().isClientSide) {
                user.setItemInHand(hand, ItemUtils.createFilledResult(stack, user, new ItemStack(Items.MILK_BUCKET)));
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return true;
        }
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        if (type == PuppetType.SHEEP && stack.is(Items.SHEARS) && !tag.getBoolean("Sheared")) {
            if (!user.level().isClientSide) {
                ServerLevel level = puppet.serverLevel();
                DyeColor color = DyeColor.byId(tag.getByte("Color"));
                Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_wool"));
                int count = 1 + level.random.nextInt(3);
                for (int i = 0; i < count; i++) {
                    ItemEntity drop = new ItemEntity(level, puppet.getX(), puppet.getY() + 1.0, puppet.getZ(), new ItemStack(wool));
                    drop.setDeltaMovement((level.random.nextFloat() - level.random.nextFloat()) * 0.1, level.random.nextFloat() * 0.05,
                            (level.random.nextFloat() - level.random.nextFloat()) * 0.1);
                    level.addFreshEntity(drop);
                }
                tag.putBoolean("Sheared", true);
                puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
                level.playSound(null, puppet.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, user, LivingEntity.getSlotForHand(hand));
            }
            return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isBusy(event.getEntity())) {
            // PASS, not SUCCESS: a consumed interact-at stops the plain interact (onEntityInteract) from ever firing,
            // and that's where a puppet's move gets its target (convert's pig).
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
        }
    }

    /** no doors, chests or buttons from inside a puppet — a right-click on a block is the special move instead. */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // a cat puppet sitting on a chest blocks everyone else from opening it — the classic annoying-cat mechanic.
        if (!event.getLevel().isClientSide && !isBusy(event.getEntity())
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && catSittingOnChest(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (drawsBow(event.getEntity())) {
            event.setUseBlock(TriState.FALSE); // no block use, but the bow still draws
            return;
        }
        if (isBusy(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
                rightClick(player, null);
            }
        }
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (drawsBow(event.getEntity()) && isPuppetTool(event.getItemStack())) {
            return; // the skeleton's real bow: vanilla draw, vanilla arrow, every archery blessing
        }
        if (isBusy(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
                rightClick(player, null);
            }
        }
    }

    /** puppets can't dig. */
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isBusy(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (isBusy(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * skeleton: draws its bow a bit faster than a player — extra charge ticks credited on a fixed cadence, so client
     * and server agree, and other draw-speed effects (dexterous) stack on top. runs on both sides.
     */
    @SubscribeEvent
    static void onUseTick(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getItem().getItem() instanceof BowItem)
                || moveOf(player) != Move.BOW) {
            return;
        }
        double speed;
        try {
            speed = Config.PUPPETEER_SKELETON_DRAW_SPEED.get();
        } catch (IllegalStateException e) {
            return;
        }
        if (speed <= 1.0) {
            return;
        }
        // every Nth remaining tick, skip one: 1.25x → every 4th, 1.5x → every 2nd, 2x+ → every tick (more per tick).
        int every = Math.max(1, (int) Math.round(1.0 / (speed - 1.0)));
        int extra = speed >= 2.0 ? (int) Math.round(speed - 1.0) : 1;
        if (event.getDuration() % every == 0) {
            event.setDuration(Math.max(0, event.getDuration() - extra));
        }
    }

    /** a puppet tool can't be thrown away — it bounces straight back into your hands. */
    @SubscribeEvent
    static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (isBusy(event.getPlayer()) && isPuppetTool(stack)) {
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
        }
    }

    /** and can't be swapped into the offhand. */
    @SubscribeEvent
    static void onSwapHands(net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && isBusy(player)) {
            event.setCanceled(true);
        }
    }

    /** nothing goes into the stashed inventory while you're a puppet. */
    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (isBusy(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /**
     * runs FIRST: sets a puppet's hits to its own base damage (so your blessings/curses then scale THAT), and
     * swallows damage the puppet is immune to — your own creeper blast, anything mid-possession, a zombified
     * piglin's fire, a chicken's fall.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDamageFirst(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof ServerPlayer attacker && source.getEntity() == attacker
                && !SPECIAL_DAMAGE.contains(attacker.getUUID())) {
            PuppetType type = possessed(attacker);
            if (type == PuppetType.GHAST || type == PuppetType.BREEZE || type == PuppetType.WITCH || type == PuppetType.SCREAMING_GOAT
                    || type == PuppetType.PILLAGER || type == PuppetType.EVOKER || type == PuppetType.WANDERING_TRADER
                    || type == PuppetType.VEX || type == PuppetType.ALLAY) {
                event.setCanceled(true); // no direct melee (the vex only hits via its lunge; the allay never hits)
                return;
            }
            if (type != null) {
                float amount = type == PuppetType.DROWNED
                        ? Config.PUPPETEER_DROWNED_MELEE_DAMAGE.get().floatValue() // its trident jab, not a bare zombie slap
                        : type.attackDamage();
                Float strength = SWING_STRENGTH.remove(attacker.getUUID());
                if ((type == PuppetType.IRON_GOLEM || type == PuppetType.VINDICATOR) && strength != null) {
                    amount *= 0.2F + strength * strength * 0.8F; // vanilla's weapon cooldown scaling
                }
                if (johnny(attacker)) {
                    amount *= 1.0F + Config.PUPPETEER_JOHNNY_BONUS.get().floatValue();
                }
                ItemStack weapon = mobWeapon(attacker, attacker.getData(WitchModAttachments.PUPPET_DATA));
                if (!weapon.isEmpty() && attacker.level() instanceof ServerLevel weaponLevel) {
                    // the mob's own weapon's damage enchantments (sharpness, smite...) add on, as they would for it
                    amount = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(weaponLevel, weapon, event.getEntity(), source, amount);
                }
                if (type.group() == Group.SLIME) {
                    amount = slimeDamage(attacker, type); // grows with the slime
                }
                if (type == PuppetType.WOLF && frenzied(attacker)) {
                    amount *= Config.PUPPETEER_WOLF_FRENZY_DAMAGE.get().floatValue(); // a rabid wolf hits harder
                }
                event.setAmount(amount);
            }
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        // (a silverfish hidden in stone is untouchable — and doesn't suffocate in there — until it bursts out)
        if (EXPLODING.contains(player.getUUID()) || ((BINDINGS.containsKey(player.getUUID()) || hidden(player)) && !bypass)) {
            event.setCanceled(true);
            return;
        }
        if (possessed(player) == PuppetType.ENDERMAN && source.is(DamageTypeTags.IS_PROJECTILE) && !bypass
                && Config.PUPPETEER_ENDERMAN_DODGE_PROJECTILES.get() && dodge(player)) {
            event.setCanceled(true); // blinked out of the way, like a real enderman
            return;
        }
        if (possessed(player) == PuppetType.WITCH && !bypass) {
            // like the real witch: her own brews don't touch her, and other magic barely does (85% off)
            if (source.getEntity() == player) {
                event.setCanceled(true);
                return;
            }
            if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
                event.setAmount(event.getAmount() * 0.15F);
            }
        }
        if (burrowed(player) && source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            event.setCanceled(true); // a burrowing endermite lives in the rock
            return;
        }
        PuppetType type = possessed(player);
        if (source.is(DamageTypeTags.IS_FALL) && type != null && (type == PuppetType.CHICKEN || type.group() == Group.FISH
                || type == PuppetType.PHANTOM || type == PuppetType.IRON_GOLEM || type.group() == Group.SLIME || type == PuppetType.GHAST
                || type == PuppetType.BLAZE || type == PuppetType.BREEZE || type == PuppetType.CAT) // cats always land on their feet
                || source.is(DamageTypeTags.IS_FIRE) && type != null && type.entityType().fireImmune()) { // only the truly fire-proof
            event.setCanceled(true);
        }
    }

    /**
     * runs LAST: whatever survived every other handler (thick skinned, glass cannon, ... have had their say) hits
     * the puppet's health instead of yours.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDamageLast(LivingIncomingDamageEvent event) {
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
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH) - event.getAmount();
        player.level().playSound(null, player.blockPosition(), hurtOf(type), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (health <= 0.0F) {
            if (type == PuppetType.VILLAGER && infectVillager(player, event.getSource())) {
                return; // not dead — a zombie villager now
            }
            if (type.group() == Group.SLIME && split(player, type)) {
                return; // not dead — one of the offspring now
            }
            release(player, event.getSource(), false);
            return;
        }
        player.setData(WitchModAttachments.PUPPET_HEALTH, health);
        if (type == PuppetType.BLAZE) {
            blazeCombat(player); // ...and when it's hit
        }
        if ((type == PuppetType.VILLAGER || type == PuppetType.WANDERING_TRADER)
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            // a hurt villager / trader panics, and the village's iron golems come for whoever did it.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
            if (!(attacker instanceof net.minecraft.world.entity.animal.IronGolem)
                    && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                for (net.minecraft.world.entity.animal.IronGolem golem : player.serverLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.animal.IronGolem.class, player.getBoundingBox().inflate(16.0), LivingEntity::isAlive)) {
                    golem.setTarget(attacker);
                }
            }
        }
        // the hit has to LOOK like a hit: the red flinch (on the puppet, for everyone — no player "oof") and knockback.
        player.serverLevel().getChunkSource().broadcastAndSend(player,
                new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(player));
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            player.knockback(0.4, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
            player.hurtMarked = true;
            if (type == PuppetType.ZOMBIFIED_PIGLIN) {
                piglinCall(player, attacker); // hit one, the pack answers
            }
            if (type == PuppetType.WOLF) {
                wolfFrenzy(player, attacker); // a hit sends a wolf rabid
            }
            // a baby hoglin bolts from whatever hurt it.
            int flee = Config.PUPPETEER_HOGLIN_BABY_FLEE_TICKS.get();
            if (type == PuppetType.HOGLIN && flee > 0 && isBabyPuppet(player)) {
                Vec3 away = player.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.6);
                player.setDeltaMovement(player.getDeltaMovement().add(away.x, 0.2, away.z));
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, flee, 1, false, false));
            }
            // a guardian holding still has its spikes out: melee attackers get pricked, like the real thing.
            DamageSource src = event.getSource();
            if (type.group() == Group.GUARDIAN && STILL.contains(player.getUUID()) && src.getDirectEntity() == attacker
                    && !src.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
                    && !src.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
                float thorns = Config.PUPPETEER_GUARDIAN_THORNS.get().floatValue();
                if (thorns > 0.0F) {
                    SPECIAL_DAMAGE.add(player.getUUID());
                    try {
                        attacker.hurt(player.damageSources().thorns(player), thorns);
                    } finally {
                        SPECIAL_DAMAGE.remove(player.getUUID());
                    }
                }
            }
        }
    }

    /** healing (regeneration, blessings, food...) mends the puppet instead of you. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && possessed(player) != null) {
            scaleHeal(player, event.getAmount());
            event.setCanceled(true);
        }
    }

    /** husk hits leave the victim hungry, like a real husk's. */
    @SubscribeEvent
    static void onHit(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer blaze && possessed(blaze) == PuppetType.BLAZE && event.getEntity() != blaze) {
            blazeCombat(blaze); // a blaze lights up when it fights
        }
        // zombified piglin: hit something and the whole pack turns on it.
        if (event.getSource().getEntity() instanceof ServerPlayer hitter && possessed(hitter) == PuppetType.ZOMBIFIED_PIGLIN
                && event.getEntity() != hitter && event.getNewDamage() > 0.0F) {
            piglinCall(hitter, event.getEntity());
        }
        if (event.getSource().getEntity() instanceof ServerPlayer evoker && possessed(evoker) == PuppetType.EVOKER && event.getEntity() != evoker) {
            VEX_PREY.put(evoker.getUUID(), event.getEntity()); // its vexes go after whoever it last hit
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && possessed(attacker) != null
                && event.getEntity() != attacker && !SPECIAL_DAMAGE.contains(attacker.getUUID())
                && event.getEntity().level() instanceof ServerLevel hitLevel) {
            // the mob's own weapon's after-hit enchantments (fire aspect...) — and Johnny's hits spark
            ItemStack weapon = mobWeapon(attacker, attacker.getData(WitchModAttachments.PUPPET_DATA));
            if (!weapon.isEmpty()) {
                net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(hitLevel, event.getEntity(), event.getSource(), weapon);
            }
            if (johnny(attacker)) {
                LivingEntity hit = event.getEntity();
                hitLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, hit.getX(), hit.getY(0.6), hit.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
                hitLevel.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY(0.6), hit.getZ(), 10, 0.3, 0.3, 0.3, 0.4);
                hitLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, hit.getX(), hit.getY(1.0) + 0.2, hit.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && possessed(attacker) == PuppetType.SILVERFISH
                && event.getEntity() != attacker && !(event.getEntity() instanceof net.minecraft.world.entity.monster.Silverfish)) {
            silverfishRally(attacker, event.getEntity()); // whatever you bite, the swarm goes for
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && possessed(attacker) == PuppetType.SNOW_GOLEM
                && event.getEntity() != attacker && !SPECIAL_DAMAGE.contains(attacker.getUUID())) {
            int chill = Config.PUPPETEER_SNOW_GOLEM_CHILL_TICKS.get(); // a snow golem's punch barely hurts, but it chills
            if (chill > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, chill, 0), attacker);
            }
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && possessed(attacker) == PuppetType.IRON_GOLEM
                && event.getEntity() != attacker && event.getNewDamage() > 0.0F && !SPECIAL_DAMAGE.contains(attacker.getUUID())) {
            // the golem's swing flings its target skywards, with the golem's clang. queued for the end of the tick:
            // vanilla applies the hit's own knockback AFTER this event, and that flattens any upward push given here.
            PENDING_FLING.put(event.getEntity(), Config.PUPPETEER_GOLEM_FLING.get());
            attacker.serverLevel().playSound(null, attacker.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && groupOf(attacker) == Group.BRUTE
                && event.getEntity() != attacker && event.getNewDamage() > 0.0F && !SPECIAL_DAMAGE.contains(attacker.getUUID())) {
            tuskThrow(attacker, possessed(attacker), event.getEntity());
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && possessed(attacker) == PuppetType.WITHER_SKELETON
                && event.getEntity() != attacker && event.getNewDamage() > 0.0F
                && !SPECIAL_DAMAGE.contains(attacker.getUUID())) {
            int seconds = Config.PUPPETEER_WITHER_SKELETON_WITHER_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, seconds * 20), attacker);
            }
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker && possessed(attacker) == PuppetType.HUSK
                && event.getNewDamage() > 0.0F) {
            int seconds = Config.PUPPETEER_HUSK_HUNGER_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.HUNGER, seconds * 20), attacker);
            }
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker
                && (possessed(attacker) == PuppetType.PUFFERFISH || possessed(attacker) == PuppetType.CAVE_SPIDER)) {
            int seconds = possessed(attacker) == PuppetType.PUFFERFISH ? Config.PUPPETEER_PUFFERFISH_POISON_SECONDS.get()
                    : Config.PUPPETEER_CAVE_SPIDER_POISON_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20), attacker);
            }
        }
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident)
                && arrow.getOwner() instanceof ServerPlayer shooter && moveOf(shooter) == Move.BOW
                && event.getEntity() != shooter) {
            volley(shooter, event.getEntity());
        }
        if (event.getSource().getDirectEntity() instanceof ThrownTrident trident) {
            UUID owner = TRIDENTS.get(trident);
            if (owner != null && trident.level() instanceof ServerLevel level
                    && level.getPlayerByUUID(owner) instanceof ServerPlayer thrower && event.getEntity() != thrower) {
                tridentRally(thrower, event.getEntity());
            }
        }
    }

    /** dazed mobs wake up; puppet tridents vanish quickly. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        thawDazed(event.getServer());
        trailGales();
        if (!ECHOES.isEmpty()) {
            tickEchoes(event.getServer().overworld().getGameTime());
        }
        SHOPS.keySet().removeIf(shop -> !(shop.getTradingPlayer() instanceof ServerPlayer customer)
                || !(customer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)); // closed shop windows
        if (!PENDING_FLING.isEmpty()) {
            PENDING_FLING.forEach((hit, up) -> {
                if (hit.isAlive()) {
                    hit.setDeltaMovement(hit.getDeltaMovement().add(0.0, up, 0.0));
                    hit.hurtMarked = true;
                }
            });
            PENDING_FLING.clear();
        }
        if (TRIDENTS.isEmpty()) {
            return;
        }
        int life = Config.PUPPETEER_DROWNED_TRIDENT_LIFE_TICKS.get();
        TRIDENTS.keySet().removeIf(t -> {
            if (t.isRemoved()) {
                return true;
            }
            if (t.tickCount > life) {
                t.discard();
                return true;
            }
            return false;
        });
    }

    /** monsters don't hunt you while you're inside a puppet. */
    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        // monsters leave a puppet alone — unless it attacked THEM: anything you hit (in survival) fights back.
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && possessed(player) != null
                && event.getEntity() instanceof Enemy && Config.PUPPETEER_MONSTERS_IGNORE.get()
                && event.getEntity().getLastHurtByMob() != player
                && !FORCED_TARGET.contains(event.getEntity().getUUID())
                && !(event.getEntity() instanceof Mob mob && hunts(mob, possessed(player)))) {
            event.setCanceled(true);
        }
        // an evoker puppet's vexes only go for whoever their master last hit
        if (event.getEntity() instanceof Mob vex && event.getNewAboutToBeSetTarget() != null && !vexAllowed(vex, event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
            return;
        }
        // nothing picks a fight with an axolotl that's playing dead — not even whatever it bit.
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && playingDead(player)) {
            event.setCanceled(true);
        }
    }

    /** speaking in chat while possessing something lets out the mob's own call (or nothing, if it has none). */
    @SubscribeEvent
    static void onChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        PuppetType type = possessed(player);
        if (type != null) {
            player.serverLevel().playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS,
                    1.2F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
    }

    /** a zombie puppet's kills spread the infection: a villager it kills rises as a zombie villager. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, null, false); // also hands the stashed inventory back before anything drops
            return;
        }
        if (event.getEntity() instanceof Villager villager && event.getSource().getEntity() instanceof ServerPlayer killer
                && possessed(killer) != null && possessed(killer).group() == Group.ZOMBIE
                && killer.level() instanceof ServerLevel level) {
            infect(level, villager);
        }
    }

    private static void infect(ServerLevel level, Villager villager) {
        if (!EventHooks.canLivingConvert(villager, EntityType.ZOMBIE_VILLAGER, timer -> {})) {
            return;
        }
        ZombieVillager zombie = villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        if (zombie == null) {
            return;
        }
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.CONVERSION,
                new Zombie.ZombieGroupData(false, true));
        zombie.setVillagerData(villager.getVillagerData());
        zombie.setGossips(villager.getGossips().store(NbtOps.INSTANCE));
        zombie.setTradeOffers(villager.getOffers().copy());
        zombie.setVillagerXp(villager.getVillagerXp());
        EventHooks.onLivingConvert(villager, zombie);
        level.levelEvent(null, 1026, zombie.blockPosition(), 0);
    }

    /** a possessed creeper struck by lightning becomes charged, just like a real one. */
    @SubscribeEvent
    static void onLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && possessed(player) == PuppetType.CREEPER) {
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            tag.putBoolean("powered", true);
            player.setData(WitchModAttachments.PUPPET_DATA, tag);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, null, false);
            LAST_PUPPET_HIT.remove(player.getUUID());
        }
    }

    /** a crash mid-possession leaves the puppet (and your inventory) saved on you — put both back on login. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setData(WitchModAttachments.PUPPET_BINDING_END, 0L);
            release(player, null, false);
            revokeBatFlight(player); // in case only the flight survived a crash
        }
    }

    /** bats can't attack at all. */
    @SubscribeEvent
    static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Group group = groupOf(event.getEntity());
        // bats can't; the "dead" don't; nor can a rabbit mid-maul or a silverfish hidden in stone. a ghast's left-click
        // is its volley and a breeze's its wind charge — neither has a melee.
        if (group == Group.BAT || group == Group.GHAST || group == Group.BREEZE || group == Group.WITCH || group == Group.PILLAGER
                || group == Group.CAT
                || PuppetType.byId(event.getEntity().getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.SCREAMING_GOAT || playingDead(event.getEntity())
                || busyBody(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        // the vindicator's lunge-miss commit: locked out of swinging during the recovery.
        if (group == Group.VINDICATOR && event.getEntity() instanceof ServerPlayer sp && recovering(sp)) {
            event.setCanceled(true);
            return;
        }
        if ((group == Group.GOLEM || group == Group.VINDICATOR) && !event.getEntity().level().isClientSide) {
            SWING_STRENGTH.put(event.getEntity().getUUID(), event.getEntity().getAttackStrengthScale(0.5F)); // before vanilla resets it
        }
        // anything you attack while possessed turns on YOU and keeps the grudge after you unpossess — you were the
        // attacker. neutral mobs (iron golems, wolves...) get real persistent anger so it sticks, like being hit for real.
        if (event.getEntity() instanceof ServerPlayer sp && possessed(sp) != null && !sp.level().isClientSide
                && event.getTarget() instanceof Mob victim) {
            FORCED_TARGET.add(victim.getUUID()); // lets onTarget through even for an Enemy puppet's victim
            try {
                if (victim instanceof net.minecraft.world.entity.NeutralMob neutral) {
                    neutral.setPersistentAngerTarget(sp.getUUID());
                    neutral.startPersistentAngerTimer();
                }
                victim.setTarget(sp);
            } finally {
                FORCED_TARGET.remove(victim.getUUID());
            }
        }
        // a ravager's bite is delayed: cancel the instant hit and schedule it for when the head pokes out.
        if (group == Group.RAVAGER && event.getEntity() instanceof ServerPlayer rav && event.getTarget() instanceof LivingEntity tgt) {
            event.setCanceled(true);
            startRavagerBite(rav, tgt);
        }
    }
}
