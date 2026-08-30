package com.oliver.witchmod.data;

import java.util.Optional;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Reverse lookup from a Modifier item (CLAUDE.md section 4.6/section 6.4 note) back to the {@link Modifier}
 * it represents. Every modifier reuses an existing vanilla item, Recovery Compass included — a mistakenly
 * added custom {@code witchmod:recovery_compass} was removed in favour of vanilla's own.
 */
public final class ModifierItems {
    private ModifierItems() {}

    public static Optional<Modifier> findModifier(Item item) {
        if (item == Items.RECOVERY_COMPASS) {
            return Optional.of(Modifier.RECOVERY_COMPASS);
        }
        if (item == Items.CLOCK) {
            return Optional.of(Modifier.CLOCK);
        }
        if (item == Items.BELL) {
            return Optional.of(Modifier.BELL);
        }
        if (item == Items.COMPASS) {
            return Optional.of(Modifier.COMPASS);
        }
        if (item == Items.NETHER_STAR) {
            return Optional.of(Modifier.NETHERSTAR);
        }
        if (item == Items.PRISMARINE_SHARD) {
            return Optional.of(Modifier.PRISMARINE_SHARD);
        }
        if (item == Items.QUARTZ) {
            return Optional.of(Modifier.QUARTZ);
        }
        if (item == Items.DRAGON_BREATH) {
            return Optional.of(Modifier.DRAGONS_BREATH);
        }
        if (item == Items.NETHERITE_INGOT) {
            return Optional.of(Modifier.NETHERITE_INGOT);
        }
        if (item == Items.INK_SAC) {
            return Optional.of(Modifier.INK_SAC);
        }
        if (item == Items.GLOW_INK_SAC) {
            return Optional.of(Modifier.GLOW_INK_SAC);
        }
        if (item == Items.RABBIT_FOOT) {
            return Optional.of(Modifier.RABBITS_FOOT);
        }
        if (item == Items.ECHO_SHARD) {
            return Optional.of(Modifier.ECHO_SHARD);
        }
        if (item == Items.GOAT_HORN) {
            return Optional.of(Modifier.GOAT_HORN);
        }
        if (item == Items.SUGAR) {
            return Optional.of(Modifier.SUGAR);
        }
        if (item == Items.HONEYCOMB) {
            return Optional.of(Modifier.HONEYCOMB);
        }
        if (item == Items.PAPER) {
            return Optional.of(Modifier.PAPER);
        }
        if (item == Items.SLIME_BALL) {
            return Optional.of(Modifier.SLIME_BALL);
        }
        if (item == Items.SLIME_BLOCK) {
            return Optional.of(Modifier.SLIME_BLOCK);
        }
        if (item == Items.AMETHYST_SHARD) {
            return Optional.of(Modifier.AMETHYST_SHARD);
        }
        if (item == Items.WITHER_ROSE) {
            return Optional.of(Modifier.WITHER_ROSE);
        }
        // Gunpowder / Redstone / Milk Bucket are intentionally NOT modifiers (master-spec Section 9) — they
        // serve as sacrificial items / the Redstone-Dust table mechanic instead. (Paper doubles as the Yap
        // curse's sacrificial item, but the Modifier slot is separate, so there's no collision.)
        return Optional.empty();
    }

    /** Forward map: the vanilla item that represents each modifier (used by the Compendium). */
    public static Item itemFor(Modifier modifier) {
        return switch (modifier) {
            case CLOCK -> Items.CLOCK;
            case BELL -> Items.BELL;
            case COMPASS -> Items.COMPASS;
            case NETHERSTAR -> Items.NETHER_STAR;
            case PRISMARINE_SHARD -> Items.PRISMARINE_SHARD;
            case QUARTZ -> Items.QUARTZ;
            case DRAGONS_BREATH -> Items.DRAGON_BREATH;
            case NETHERITE_INGOT -> Items.NETHERITE_INGOT;
            case INK_SAC -> Items.INK_SAC;
            case GLOW_INK_SAC -> Items.GLOW_INK_SAC;
            case RABBITS_FOOT -> Items.RABBIT_FOOT;
            case ECHO_SHARD -> Items.ECHO_SHARD;
            case GOAT_HORN -> Items.GOAT_HORN;
            case SUGAR -> Items.SUGAR;
            case HONEYCOMB -> Items.HONEYCOMB;
            case PAPER -> Items.PAPER;
            case SLIME_BALL -> Items.SLIME_BALL;
            case SLIME_BLOCK -> Items.SLIME_BLOCK;
            case AMETHYST_SHARD -> Items.AMETHYST_SHARD;
            case WITHER_ROSE -> Items.WITHER_ROSE;
            case RECOVERY_COMPASS -> Items.RECOVERY_COMPASS;
        };
    }
}
