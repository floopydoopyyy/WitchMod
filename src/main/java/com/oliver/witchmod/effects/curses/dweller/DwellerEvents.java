package com.oliver.witchmod.effects.curses.dweller;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.curses.dweller.CurseTheDweller.State;

/**
 * The Dweller's discrete "moments", each a {@link DwellerEvent} with a clear snake_case id, plus a registry
 * that {@code /bewitch debug force} routes through. Some fire automatically (the curated {@code oneSetPiece}
 * shortlist + the reworked eyes/watch/explode/lunge that the state machine drives); the rest are jumpscares
 * kept purely for debug/manual use.
 */
public final class DwellerEvents {
    private DwellerEvents() {}

    // --- Fully-extracted (logic lives in its own file) ---
    public static final DwellerEvent LIGHTS_OUT = new LightsOutEvent();
    public static final DwellerEvent WHISPER = new WhisperEvent();

    // --- Reworked events the state machine actually uses ---
    /** In the dark, several pairs of glowing eyes open around you and watch — auto-fires when it's dark. */
    public static final DwellerEvent WATCHING_EYES = of("watching_eyes", (c, t, s, l) -> c.eyesEvent(t, s));
    /** A watch whose distance band is chosen by dread (FAR when calm → CLOSE near max). */
    public static final DwellerEvent DISTANT_WATCH = of("watch", (c, t, s, l) -> c.forceDistantWatch(t, s));
    /** Force a FAR watch (30-50 blocks, needs line of sight). */
    public static final DwellerEvent WATCH_FAR = of("watch_far", (c, t, s, l) -> c.forceWatch(t, s, CurseTheDweller.WATCH_FAR));
    /** Force a MEDIUM watch (18-29 blocks, needs line of sight). */
    public static final DwellerEvent WATCH_MEDIUM = of("watch_medium", (c, t, s, l) -> c.forceWatch(t, s, CurseTheDweller.WATCH_MEDIUM));
    /** Force a CLOSE watch (8-16 blocks). */
    public static final DwellerEvent WATCH_CLOSE = of("watch_close", (c, t, s, l) -> c.forceWatch(t, s, CurseTheDweller.WATCH_CLOSE));
    /** Force a WINDOW watch — it peers through a wall/window and vanishes on a clear line of sight. */
    public static final DwellerEvent WINDOW_WATCH = of("window_watch", (c, t, s, l) -> c.forceWindowWatch(t, s));
    /** Nearby passive mobs/villagers fall silent and stare at you for 3-8s. Low-tier ambient. */
    public static final DwellerEvent MOB_STARE = of("mob_stare", (c, t, s, l) -> c.mobStare(t, s));
    /** Suddenly detonates a nearby UNTAMED mob — a jumpscare and a danger signal. */
    public static final DwellerEvent EXPLODE_MOB = of("explode_mob", (c, t, s, l) -> c.explodeUntamedMob(t, l));
    /** It rushes in from the watch spot and stops in your face — triggered by staring too long at high dread. */
    public static final DwellerEvent LUNGE = of("lunge", (c, t, s, l) -> c.lungeScare(t, s));
    /** It manifests RIGHT IN FRONT of you, passive and staring — approach/stare too long → lunge/hunt. */
    public static final DwellerEvent SIZEUP = of("sizeup", (c, t, s, l) -> c.enterSizeUp(t, s, CurseTheDweller.tier(s.anger)));
    /** Shatters a small SHAPE (cross / line / ring) out of a wall nearby, just out of your view. */
    public static final DwellerEvent BREAK = of("break", (c, t, s, l) -> c.breakShapeEvent(t, l));

    // --- Curated auto-fired set-pieces (the oneSetPiece shortlist) ---
    public static final DwellerEvent SHADOW_PASS = of("shadow_pass", (c, t, s, l) -> { c.shadowPass(t, s); return true; });
    /** It meddles with nearby fixtures — a door swings, a chest/barrel opens (1, rarely 2-4 blocks). */
    public static final DwellerEvent INTERACTION = of("interaction", (c, t, s, l) -> c.interactionEvent(t, s, l));
    public static final DwellerEvent KNOCK = of("knock", (c, t, s, l) -> c.knockEvent(t, s, l));
    public static final DwellerEvent ITEM_POLTERGEIST = of("item_poltergeist", (c, t, s, l) -> c.itemPoltergeist(t, l));

    // --- Jumpscares kept for debug / manual use ---
    public static final DwellerEvent BANG_BEHIND = of("bang_behind", (c, t, s, l) -> { c.startle(t); return true; });
    public static final DwellerEvent SNUFF_LIGHT = of("snuff_light", (c, t, s, l) -> {
        if (c.snuffNearbyLight(t, l)) {
            CurseTheDweller.playToVictim(t, SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.5F);
            return true;
        }
        return false;
    });
    public static final DwellerEvent BREAK_BLOCK = of("break_block", (c, t, s, l) -> {
        if (t.getRandom().nextFloat() < Config.DWELLER_DOOR_BREAK_CHANCE.get() && c.breakNearbyBlock(t, l)) {
            CurseTheDweller.playToVictim(t, SoundEvents.WOOD_BREAK, 1.0F, 0.7F);
            return true;
        }
        return false;
    });
    /** The impostor-in-disguise vignette (its own event, not the Delusions curse). */
    public static final DwellerEvent MIMIC = of("mimic", (c, t, s, l) -> c.mimicEvent(t, s));
    /** Tier 2+: puppeteers a passive untamed mob — it twitches, then marches at you and flashes/vanishes or explodes. */
    public static final DwellerEvent POSSESSION = of("possession", (c, t, s, l) -> c.possessionEvent(t, s, l));
    /** Tier 2+: a fast turn has a chance to briefly reveal him watching you (also auto-fired by real turns). */
    public static final DwellerEvent BEHIND = of("behind", (c, t, s, l) -> c.behindGlimpse(t, s, l));

    /** All events, so {@code /bewitch debug force} can look one up by {@link DwellerEvent#id()}. */
    public static final List<DwellerEvent> ALL = List.of(
            LIGHTS_OUT, WHISPER, WATCHING_EYES, DISTANT_WATCH, WATCH_FAR, WATCH_MEDIUM, WATCH_CLOSE, WINDOW_WATCH,
            MOB_STARE, EXPLODE_MOB, LUNGE, SIZEUP, BREAK, SHADOW_PASS, INTERACTION, KNOCK, ITEM_POLTERGEIST,
            BANG_BEHIND, SNUFF_LIGHT, BREAK_BLOCK, MIMIC, POSSESSION, BEHIND);

    /** Finds an event by its debug id, or null. */
    public static DwellerEvent byId(String id) {
        for (DwellerEvent e : ALL) {
            if (e.id().equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Body {
        boolean run(CurseTheDweller curse, ServerPlayer target, State state, ServerLevel level);
    }

    private static DwellerEvent of(String id, Body body) {
        return new DwellerEvent() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean run(CurseTheDweller curse, ServerPlayer target, State state, ServerLevel level) {
                return body.run(curse, target, state, level);
            }
        };
    }
}
