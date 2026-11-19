package com.oliver.witchmod.effects.blessings.puppeteer;

/** what each puppet does with right-click. held moves charge while right-click is held, firing on release. */
public enum PuppetMove {
    RALLY(false), TRIDENT(true), FUSE(true), OINK(false), MOO(false), GRAZE(false), EGG(true), LEAP(false), BOW(true),
    POUNCE(false), FLY(false), FLEE(false), CONVERT(false), BEAM(true), NONE(false), LUNGE(false), INSPIRE(false), BURROW(false), TELEPORT(false), SPIT(false), FIREBALL(false), BLAZE_VOLLEY(true), POTION(true), DASH(true), RAM(true), CROSSBOW(true), MUG(false), SPELL(true),
    PLAY_DEAD(true), DIVE(true), OFFER(true), VOLLEY(true), HMM(false), MAUL(false), EMBED(false), LUNGE_HEAVY(true), SCARED(false), ROAR(true),
    GIGGLE(false), MITOSIS(true), BOLT(false), PIGLIN_CALL(true), WOLF_MAUL(true), PUFF(true), ROLL(false), RIDE(false), PERCH(true), GROWL(false), LAY_EGGS(true), CURL(true), POLLINATE(false), FROG_EAT(false), SNIFF_DIG(true), SHELL(true), WARDEN_BELT(true), BREEZE_DASH(false);

    private final boolean held;

    PuppetMove(boolean held) {
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
