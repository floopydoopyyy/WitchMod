package com.oliver.witchmod.effects.curses.bedrock;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.State;

/**
 * bedrock Moment's ACTIVE bugs, each as its own {@link BedrockEvent}, plus the registry the weighted pool and
 * {@code /bewitch debug force} both route through. Delegates to the (isolated + package-visible) trigger
 * methods on {@link CurseBedrockMoment}.
 */
public final class BedrockEvents {
    private BedrockEvents() {}

    public static final BedrockEvent CREEPER_BOAT = of("creeperboat", (c, t, s, l) -> c.creeperBoat(t, s, l));
    public static final BedrockEvent BLITZ = of("blitz", (c, t, s, l) -> c.blitz(t, s, l));
    public static final BedrockEvent MITOSIS = of("mitosis", (c, t, s, l) -> c.mitosis(t, l));
    public static final BedrockEvent RUBBERBAND = of("rubberband", (c, t, s, l) -> c.rubberband(t, s));
    public static final BedrockEvent SERVER_LAG = of("serverlag", (c, t, s, l) -> { c.serverLag(t, s, l); return true; });
    public static final BedrockEvent POP = of("pop", (c, t, s, l) -> c.pop(t, l));
    public static final BedrockEvent NIGHTCORE = of("nightcore", (c, t, s, l) -> { c.nightcore(t, l); return true; });
    public static final BedrockEvent INVENTORY_SHUFFLE = of("shuffle", (c, t, s, l) -> c.inventoryShuffle(t));
    public static final BedrockEvent BLUETOOTH = of("bluetooth", (c, t, s, l) -> { c.bluetoothEvent(t, s, l); return true; });
    public static final BedrockEvent CHUNK_REJECT = of("chunkreject", (c, t, s, l) -> { c.chunkReject(t, l); return true; });
    public static final BedrockEvent HELICOPTER = of("helicopter", (c, t, s, l) -> c.helicopter(t, s, l));
    public static final BedrockEvent TICKSPEED = of("tickspeed", (c, t, s, l) -> c.tickspeed(t, s, l));
    public static final BedrockEvent SOUND_DELAY = of("sounddelay", (c, t, s, l) -> { c.soundDelay(t, l); return true; });
    public static final BedrockEvent PHANTOM_DUR = of("phantomdur", (c, t, s, l) -> { c.phantomDur(t, l); return true; });
    public static final BedrockEvent MARKETPLACE = of("marketplace", (c, t, s, l) -> { c.marketplace(t, l); return true; });
    public static final BedrockEvent PERSPECTIVE = of("perspective", (c, t, s, l) -> { c.perspectiveFlip(t, l); return true; });
    public static final BedrockEvent DROWNING = of("drowning", (c, t, s, l) -> c.drownEvent(t, s));
    // newer bugs.
    public static final BedrockEvent GHOST_ITEM = of("ghostitem", (c, t, s, l) -> { c.ghostItem(t, l); return true; });
    public static final BedrockEvent INPUT_LAG = of("inputlag", (c, t, s, l) -> { c.inputLag(t, l); return true; });
    public static final BedrockEvent VIBRANT = of("vibrant", (c, t, s, l) -> { c.vibrant(t, l); return true; });
    public static final BedrockEvent SPRINT_RESET = of("sprintreset", (c, t, s, l) -> { c.sprintReset(t, l); return true; });
    public static final BedrockEvent GHOST_PHASE = of("ghostphase", (c, t, s, l) -> { c.ghostPhase(t, s, l); return true; });
    public static final BedrockEvent LANGUAGE_ERROR = of("language", (c, t, s, l) -> { c.languageError(t, l); return true; });
    public static final BedrockEvent SPEED_BLITZ = of("speedblitz", (c, t, s, l) -> { c.speedBlitz(t, l); return true; });
    public static final BedrockEvent FAKE_KICK = of("fakekick", (c, t, s, l) -> { c.fakeKick(t, l); return true; });
    public static final BedrockEvent FAKE_BSOD = of("bsod", (c, t, s, l) -> { c.fakeBsod(t, l); return true; });
    public static final BedrockEvent PAUSE = of("pause", (c, t, s, l) -> c.pauseEntities(t, s, l));
    public static final BedrockEvent FLOAT = of("float", (c, t, s, l) -> c.floatEvent(t, s, l));
    public static final BedrockEvent HUNGRY = of("hungry", (c, t, s, l) -> { c.hungry(t, l); return true; });
    public static final BedrockEvent AIR_SWIM = of("airswim", (c, t, s, l) -> { c.airSwim(t, l); return true; });
    /** BENEFICIAL: quarters your weapon swing cooldown for 8-28s, mimicking Bedrock's no-cooldown swinging. */
    public static final BedrockEvent COOLDOWNS = of("cooldowns", (c, t, s, l) -> c.cooldownsEvent(t, s));
    /** sticky TNT (debug/force): spawns a lit TNT already stuck to you (the passive version needs live TNT nearby). */
    public static final BedrockEvent STICKY_TNT = of("stickytnt", (c, t, s, l) -> c.stickyTntEvent(t, l));

    public static final List<BedrockEvent> ALL = List.of(
            CREEPER_BOAT, BLITZ, MITOSIS, RUBBERBAND, SERVER_LAG, POP, NIGHTCORE, INVENTORY_SHUFFLE, BLUETOOTH,
            CHUNK_REJECT, HELICOPTER, TICKSPEED, SOUND_DELAY, PHANTOM_DUR, MARKETPLACE, PERSPECTIVE, DROWNING,
            GHOST_ITEM, INPUT_LAG, VIBRANT, SPRINT_RESET, GHOST_PHASE, LANGUAGE_ERROR, SPEED_BLITZ,
            FAKE_KICK, FAKE_BSOD, PAUSE, FLOAT, HUNGRY, AIR_SWIM, COOLDOWNS, STICKY_TNT);

    public static BedrockEvent byId(String id) {
        for (BedrockEvent e : ALL) {
            if (e.id().equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Body {
        boolean run(CurseBedrockMoment curse, ServerPlayer target, State state, ServerLevel level);
    }

    private static BedrockEvent of(String id, Body body) {
        return new BedrockEvent() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean run(CurseBedrockMoment curse, ServerPlayer target, State state, ServerLevel level) {
                return body.run(curse, target, state, level);
            }
        };
    }
}
