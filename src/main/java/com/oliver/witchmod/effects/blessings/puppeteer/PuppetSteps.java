package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * puppeteer: a puppet's feet. its footsteps are its mob's own step sound (as loud as the mob's) where the mob has one;
 * a mob that never walks the ground (a flier, a fish, a slime, a shulker) makes none; anything else keeps the block's.
 * and a puppet — or a player in a mob disguise — doesn't kick up a player's sprinting dust (see EntityStepMixin).
 */
public final class PuppetSteps {
    private PuppetSteps() {}

    private record Step(SoundEvent sound, float volume) {}

    private static final Map<PuppetType, Step> STEPS = new EnumMap<>(PuppetType.class);
    private static final Set<PuppetType> SILENT = java.util.EnumSet.noneOf(PuppetType.class);

    private static void step(SoundEvent sound, float volume, PuppetType... types) {
        for (PuppetType t : types) {
            STEPS.put(t, new Step(sound, volume));
        }
    }

    static {
        step(SoundEvents.ZOMBIE_STEP, 0.15F, PuppetType.ZOMBIE, PuppetType.ZOMBIFIED_PIGLIN);
        step(SoundEvents.ZOMBIE_VILLAGER_STEP, 0.15F, PuppetType.ZOMBIE_VILLAGER);
        step(SoundEvents.HUSK_STEP, 0.15F, PuppetType.HUSK);
        step(SoundEvents.DROWNED_STEP, 0.15F, PuppetType.DROWNED);
        step(SoundEvents.SKELETON_STEP, 0.15F, PuppetType.SKELETON);
        step(SoundEvents.STRAY_STEP, 0.15F, PuppetType.STRAY);
        step(SoundEvents.BOGGED_STEP, 0.15F, PuppetType.BOGGED);
        step(SoundEvents.WITHER_SKELETON_STEP, 0.15F, PuppetType.WITHER_SKELETON);
        step(SoundEvents.SPIDER_STEP, 0.15F, PuppetType.SPIDER, PuppetType.CAVE_SPIDER);
        step(SoundEvents.PIG_STEP, 0.15F, PuppetType.PIG);
        step(SoundEvents.COW_STEP, 0.15F, PuppetType.COW, PuppetType.MOOSHROOM);
        step(SoundEvents.SHEEP_STEP, 0.15F, PuppetType.SHEEP);
        step(SoundEvents.CHICKEN_STEP, 0.15F, PuppetType.CHICKEN);
        step(SoundEvents.WOLF_STEP, 0.15F, PuppetType.WOLF);
        step(SoundEvents.POLAR_BEAR_STEP, 0.15F, PuppetType.POLAR_BEAR);
        step(SoundEvents.PANDA_STEP, 0.15F, PuppetType.PANDA);
        step(SoundEvents.HOGLIN_STEP, 0.15F, PuppetType.HOGLIN);
        step(SoundEvents.ZOGLIN_STEP, 0.15F, PuppetType.ZOGLIN);
        step(SoundEvents.PIGLIN_STEP, 0.15F, PuppetType.PIGLIN);
        step(SoundEvents.PIGLIN_BRUTE_STEP, 0.15F, PuppetType.PIGLIN_BRUTE);
        step(SoundEvents.IRON_GOLEM_STEP, 1.0F, PuppetType.IRON_GOLEM);
        step(SoundEvents.RAVAGER_STEP, 0.5F, PuppetType.RAVAGER);
        step(SoundEvents.GOAT_STEP, 0.15F, PuppetType.GOAT, PuppetType.SCREAMING_GOAT);
        step(SoundEvents.CAMEL_STEP, 0.15F, PuppetType.CAMEL);
        step(SoundEvents.HORSE_STEP, 0.15F, PuppetType.HORSE, PuppetType.DONKEY, PuppetType.MULE, PuppetType.SKELETON_HORSE,
                PuppetType.ZOMBIE_HORSE);
        step(SoundEvents.LLAMA_STEP, 0.15F, PuppetType.LLAMA, PuppetType.TRADER_LLAMA);
        step(SoundEvents.STRIDER_STEP, 0.15F, PuppetType.STRIDER);
        step(SoundEvents.TURTLE_SHAMBLE, 0.15F, PuppetType.TURTLE);
        step(SoundEvents.ARMADILLO_STEP, 0.15F, PuppetType.ARMADILLO);
        step(SoundEvents.SNIFFER_STEP, 0.15F, PuppetType.SNIFFER);
        step(SoundEvents.FROG_STEP, 0.15F, PuppetType.FROG);
        step(SoundEvents.SILVERFISH_STEP, 0.15F, PuppetType.SILVERFISH);
        step(SoundEvents.ENDERMITE_STEP, 0.15F, PuppetType.ENDERMITE);
        step(SoundEvents.PARROT_STEP, 0.15F, PuppetType.PARROT);
        java.util.Collections.addAll(SILENT, PuppetType.BAT, PuppetType.PHANTOM, PuppetType.GHAST, PuppetType.VEX, PuppetType.ALLAY,
                PuppetType.BEE, PuppetType.SLIME, PuppetType.MAGMA_CUBE, PuppetType.SHULKER, PuppetType.WARDEN, PuppetType.COD,
                PuppetType.SALMON, PuppetType.PUFFERFISH, PuppetType.TROPICAL_FISH, PuppetType.TADPOLE, PuppetType.SQUID,
                PuppetType.GLOW_SQUID, PuppetType.GUARDIAN, PuppetType.ELDER_GUARDIAN, PuppetType.DOLPHIN, PuppetType.BLAZE);
    }

    @org.jetbrains.annotations.Nullable
    private static PuppetType puppet(Player player) {
        return player.hasData(WitchModAttachments.PUPPET_TYPE) ? PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) : null;
    }

    /** a step was taken: true if the puppet's mob made it (or made none), so the player's own isn't played. */
    public static boolean replaceStep(Player player) {
        PuppetType type = puppet(player);
        if (type == null) {
            return false;
        }
        if (SILENT.contains(type)) {
            return true; // (the warden's tread is its own: WardenBehaviour)
        }
        Step step = STEPS.get(type);
        if (step == null) {
            return false; // no step sound of its own: the block's, like the mob
        }
        player.playSound(step.sound(), step.volume(), 1.0F);
        return true;
    }

    /** a puppet, or a player disguised as a mob: no player sprinting dust. */
    public static boolean hidesSprintDust(Player player) {
        if (puppet(player) != null) {
            return true;
        }
        int disguise = player.hasData(WitchModAttachments.DISGUISE_TYPE) ? player.getData(WitchModAttachments.DISGUISE_TYPE) : -1;
        return disguise >= 0 && disguise != 6; // (6: the random-player disguise, which is a player)
    }
}
