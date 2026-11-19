package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.oliver.witchmod.effects.blessings.puppeteer.mob.*;

/** which behaviour runs each puppet. a new puppet: a {@link PuppetType} constant, then a line here. */
public final class PuppetBehaviours {
    private PuppetBehaviours() {}

    private static final Map<PuppetType, PuppetBehaviour> BY_TYPE = new EnumMap<>(PuppetType.class);
    /** every distinct behaviour, once each (for the hooks that run on all of them, like stepping out). */
    private static final List<PuppetBehaviour> ALL;

    static {
        bind(ZombieBehaviour.INSTANCE, PuppetType.ZOMBIE, PuppetType.ZOMBIE_VILLAGER, PuppetType.HUSK, PuppetType.DROWNED);
        bind(CreeperBehaviour.INSTANCE, PuppetType.CREEPER);
        bind(FarmAnimalBehaviour.INSTANCE, PuppetType.PIG, PuppetType.COW, PuppetType.SHEEP, PuppetType.CHICKEN, PuppetType.MOOSHROOM);
        bind(FishBehaviour.INSTANCE, PuppetType.COD, PuppetType.SALMON, PuppetType.PUFFERFISH, PuppetType.TROPICAL_FISH, PuppetType.TADPOLE);
        bind(SkeletonBehaviour.INSTANCE, PuppetType.SKELETON, PuppetType.STRAY, PuppetType.BOGGED, PuppetType.WITHER_SKELETON);
        bind(SpiderBehaviour.INSTANCE, PuppetType.SPIDER, PuppetType.CAVE_SPIDER);
        bind(BatBehaviour.INSTANCE, PuppetType.BAT);
        bind(SquidBehaviour.INSTANCE, PuppetType.SQUID, PuppetType.GLOW_SQUID);
        bind(ZombifiedPiglinBehaviour.INSTANCE, PuppetType.ZOMBIFIED_PIGLIN);
        bind(GuardianBehaviour.INSTANCE, PuppetType.GUARDIAN, PuppetType.ELDER_GUARDIAN);
        bind(BruteBehaviour.INSTANCE, PuppetType.HOGLIN, PuppetType.ZOGLIN);
        bind(DolphinBehaviour.INSTANCE, PuppetType.DOLPHIN);
        bind(AxolotlBehaviour.INSTANCE, PuppetType.AXOLOTL);
        bind(PhantomBehaviour.INSTANCE, PuppetType.PHANTOM);
        bind(IronGolemBehaviour.INSTANCE, PuppetType.IRON_GOLEM);
        bind(SnowGolemBehaviour.INSTANCE, PuppetType.SNOW_GOLEM);
        bind(VillagerBehaviour.INSTANCE, PuppetType.VILLAGER, PuppetType.WANDERING_TRADER);
        bind(RabbitBehaviour.INSTANCE, PuppetType.RABBIT, PuppetType.KILLER_RABBIT);
        bind(SilverfishBehaviour.INSTANCE, PuppetType.SILVERFISH);
        bind(EndermiteBehaviour.INSTANCE, PuppetType.ENDERMITE);
        bind(EndermanBehaviour.INSTANCE, PuppetType.ENDERMAN);
        bind(HorseBehaviour.INSTANCE, PuppetType.HORSE, PuppetType.DONKEY, PuppetType.MULE, PuppetType.ZOMBIE_HORSE,
                PuppetType.SKELETON_HORSE);
        bind(SlimeBehaviour.INSTANCE, PuppetType.SLIME, PuppetType.MAGMA_CUBE);
        bind(LlamaBehaviour.INSTANCE, PuppetType.LLAMA, PuppetType.TRADER_LLAMA);
        bind(GhastBehaviour.INSTANCE, PuppetType.GHAST);
        bind(BlazeBehaviour.INSTANCE, PuppetType.BLAZE);
        bind(BreezeBehaviour.INSTANCE, PuppetType.BREEZE);
        bind(WitchBehaviour.INSTANCE, PuppetType.WITCH);
        bind(CamelBehaviour.INSTANCE, PuppetType.CAMEL);
        bind(GoatBehaviour.INSTANCE, PuppetType.GOAT, PuppetType.SCREAMING_GOAT);
        bind(PillagerBehaviour.INSTANCE, PuppetType.PILLAGER);
        bind(FoxBehaviour.INSTANCE, PuppetType.FOX);
        bind(VindicatorBehaviour.INSTANCE, PuppetType.VINDICATOR);
        bind(EvokerBehaviour.INSTANCE, PuppetType.EVOKER);
        bind(WolfBehaviour.INSTANCE, PuppetType.WOLF);
        bind(CatBehaviour.INSTANCE, PuppetType.CAT);
        bind(OcelotBehaviour.INSTANCE, PuppetType.OCELOT);
        bind(RavagerBehaviour.INSTANCE, PuppetType.RAVAGER);
        bind(VexBehaviour.INSTANCE, PuppetType.VEX);
        bind(AllayBehaviour.INSTANCE, PuppetType.ALLAY);
        bind(PiglinBehaviour.INSTANCE, PuppetType.PIGLIN);
        bind(PiglinBruteBehaviour.INSTANCE, PuppetType.PIGLIN_BRUTE);
        bind(PandaBehaviour.INSTANCE, PuppetType.PANDA);
        bind(StriderBehaviour.INSTANCE, PuppetType.STRIDER);
        bind(ParrotBehaviour.INSTANCE, PuppetType.PARROT);
        bind(PolarBearBehaviour.INSTANCE, PuppetType.POLAR_BEAR);
        bind(TurtleBehaviour.INSTANCE, PuppetType.TURTLE);
        bind(ArmadilloBehaviour.INSTANCE, PuppetType.ARMADILLO);
        bind(BeeBehaviour.INSTANCE, PuppetType.BEE);
        bind(FrogBehaviour.INSTANCE, PuppetType.FROG);
        bind(SnifferBehaviour.INSTANCE, PuppetType.SNIFFER);
        bind(ShulkerBehaviour.INSTANCE, PuppetType.SHULKER);
        bind(WardenBehaviour.INSTANCE, PuppetType.WARDEN);
        for (PuppetType type : PuppetType.values()) {
            if (!BY_TYPE.containsKey(type)) {
                throw new IllegalStateException("puppet " + type + " has no behaviour (PuppetBehaviours)");
            }
        }
        ALL = List.copyOf(new LinkedHashSet<>(BY_TYPE.values()));
    }

    private static void bind(PuppetBehaviour behaviour, PuppetType... types) {
        for (PuppetType type : types) {
            BY_TYPE.put(type, behaviour);
        }
    }

    public static PuppetBehaviour of(PuppetType type) {
        return BY_TYPE.get(type);
    }

    public static List<PuppetBehaviour> all() {
        return ALL;
    }
}
