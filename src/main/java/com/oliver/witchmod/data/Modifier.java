package com.oliver.witchmod.data;

import java.util.OptionalInt;

/**
 * the table modifiers — pure data; {@link ModifierItems} maps a slot item to one of these. percentages are
 * deltas on top of the base cost/success/backfire curves ({@link ModifierCalculator} combines them).
 */
public enum Modifier {
    CLOCK(15, 0, 0, 0) {
        // guarantees exactly 45 minutes, overriding the random 35–60 base roll (like Compass's fixed 40).
        @Override
        public OptionalInt fixedDurationTicks() {
            return OptionalInt.of(CLOCK_FIXED_TICKS);
        }
    },
    BELL(0, 0, 0, 0) {
        @Override
        public int flatDurationBonusTicks(net.minecraft.util.RandomSource rng) {
            return BELL_BONUS_TICKS;
        }

        @Override
        public boolean announcesCast() {
            return true;
        }
    },
    COMPASS(5, 0, 0, 0) {
        @Override
        public OptionalInt fixedDurationTicks() {
            return OptionalInt.of(40 * 60 * 20);
        }
    },
    NETHERSTAR(50, 0, 0, 0) {
        @Override
        public OptionalInt successCapPercent() {
            return OptionalInt.of(99);
        }
    },
    PRISMARINE_SHARD(-15, 0, 0, 0),
    QUARTZ(-20, 0, 0, 0) {
        @Override
        public boolean discoveredEffectsOnly() {
            return true;
        }
    },
    DRAGONS_BREATH(30, -40, 0, 10) {
        @Override
        public boolean splashToNearby() {
            return true;
        }
    },
    NETHERITE_INGOT(40, 0, 0, 10) {
        @Override
        public boolean bypassesWardAndJar() {
            return true;
        }
    },
    INK_SAC(35, 0, 0, 5) {
        @Override
        public boolean hidesStartTell() {
            return true;
        }
    },
    GLOW_INK_SAC(-10, 0, 0, 0) {
        @Override
        public boolean revealsEffectToTarget() {
            return true;
        }
    },
    RABBITS_FOOT(10, 0, 0, -15),
    ECHO_SHARD(10, 0, 0, 0) {
        @Override
        public boolean delaysTell() {
            return true;
        }
    },
    GOAT_HORN(-5, 0, 0, 0) {
        @Override
        public boolean loudTriggerTell() {
            return true;
        }
    },
    SUGAR(-10, -50, 0, 0),
    HONEYCOMB(20, -60, 0, 0) {
        @Override
        public boolean forcesBackfireZero() {
            return true;
        }
    },
    PAPER(0, 0, 0, 0) {
        @Override
        public boolean scribblesLedger() {
            return true;
        }
    },
    SLIME_BALL(60, 0, 0, 0) {
        @Override
        public int infectiousLevel() {
            return 1;
        }
    },
    SLIME_BLOCK(100, 0, 0, 0) {
        @Override
        public int infectiousLevel() {
            return 2;
        }
    },
    AMETHYST_SHARD(0, 0, 0, 0) {
        @Override
        public boolean discountsSameCategory() {
            return true;
        }
    },
    WITHER_ROSE(15, 0, 0, 0) {
        @Override
        public boolean disguisesCategory() {
            return true;
        }
    },
    // gunpowder, redstone dust, and milk bucket are no longer modifiers — they
    // are now sacrificial items / table mechanics instead (Gunpowder → Explosive curse, Milk Bucket →
    // butterfingers curse, Redstone Dust → the random-attachment table mechanic in BewitchingTableRitual).
    RECOVERY_COMPASS(15, 0, 0, 0) {
        @Override
        public boolean reappliesShortenedEffectOnCure() {
            return true;
        }
    },
    EYE_OF_ENDER(10, 0, 0, 0) {
        @Override
        public boolean reportsBlocks() {
            return true;
        }
    };

    // clock forces a fixed 45 minutes; Bell adds a flat 15. (Compile-time constants, so they're
    // safely usable from the enum constant bodies above.)
    private static final int CLOCK_FIXED_TICKS = 45 * 60 * 20;
    private static final int BELL_BONUS_TICKS = 15 * 60 * 20;

    private final int costDeltaPercent;
    private final int durationDeltaPercent;
    private final int successDeltaPercent;
    private final int backfireDeltaPercent;

    Modifier(int costDeltaPercent, int durationDeltaPercent, int successDeltaPercent, int backfireDeltaPercent) {
        this.costDeltaPercent = costDeltaPercent;
        this.durationDeltaPercent = durationDeltaPercent;
        this.successDeltaPercent = successDeltaPercent;
        this.backfireDeltaPercent = backfireDeltaPercent;
    }

    public int costDeltaPercent() {
        return costDeltaPercent;
    }

    public int durationDeltaPercent() {
        return durationDeltaPercent;
    }

    public int successDeltaPercent() {
        return successDeltaPercent;
    }

    public int backfireDeltaPercent() {
        return backfireDeltaPercent;
    }

    /** compass (fixed 40 min) / Clock (fixed 45 min): overrides the rolled duration outright. */
    public OptionalInt fixedDurationTicks() {
        return OptionalInt.empty();
    }

    /** netherstar only: raises the normal 95% success cap to this value instead. */
    public OptionalInt successCapPercent() {
        return OptionalInt.empty();
    }

    /** quartz only: usable exclusively on effects the caster has already discovered. */
    public boolean discoveredEffectsOnly() {
        return false;
    }

    /** dragon's Breath only: also splashes a shortened dose onto players near the target. */
    public boolean splashToNearby() {
        return false;
    }

    /** netherite Ingot only: ignores Ward/Jar counterplay (Totem still applies). */
    public boolean bypassesWardAndJar() {
        return false;
    }

    /** ink Sac only: the target gets no "something happened" tell at all when this lands. */
    public boolean hidesStartTell() {
        return false;
    }

    /** glow Ink Sac only: the target is told exactly which effect landed, not just that one did. */
    public boolean revealsEffectToTarget() {
        return false;
    }

    /** echo Shard only: delays the target's tell notification, not the effect's actual onset. */
    public boolean delaysTell() {
        return false;
    }

    /** goat Horn only: the tell is loud/obvious instead of the normal subtle one. */
    public boolean loudTriggerTell() {
        return false;
    }

    /** honeycomb only: backfire chance is forced to 0% regardless of the base curve. */
    public boolean forcesBackfireZero() {
        return false;
    }

    /** recovery Compass only: on cure, reapplies the effect once more at a shortened duration. */
    public boolean reappliesShortenedEffectOnCure() {
        return false;
    }

    /** paper only: this cast's Ledger entries are scribbled out and unreadable. */
    public boolean scribblesLedger() {
        return false;
    }

    /** clock/Bell: a flat number of bonus ticks ADDED to the rolled duration (Clock 5–15 min, Bell 15 min). */
    public int flatDurationBonusTicks(net.minecraft.util.RandomSource rng) {
        return 0;
    }

    /** bell only: on a successful cast, announce to server chat who inflicted what on whom. */
    public boolean announcesCast() {
        return false;
    }

    /**
     * Slime Ball (1) / Slime Block (2): on a successful cast the target also gets a hidden Infectious /
     * Very Infectious attachment, making their attachments contagious (a hot potato / a short-range spread).
     */
    public int infectiousLevel() {
        return 0;
    }

    /** amethyst Shard: if the target already carries an effect of the SAME category, this cast is cheaper. */
    public boolean discountsSameCategory() {
        return false;
    }

    /** wither Rose: the effect reads as the OPPOSITE category (a fake blessing/curse) until it's discovered. */
    public boolean disguisesCategory() {
        return false;
    }

    /** eye of Ender ("Test the Waters"): when a cast is blocked, report every protection the target has. */
    public boolean reportsBlocks() {
        return false;
    }

    /** stable lowercase id (e.g. {@code dragons_breath}) for lang keys, discovery, and the Compendium. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
