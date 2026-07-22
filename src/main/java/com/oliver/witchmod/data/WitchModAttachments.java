package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.oliver.witchmod.WitchMod;

public final class WitchModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WitchMod.MODID);

    /**
     * Persists across logout/relog and (per CLAUDE.md section 2.7: curses/blessings do NOT expire on
     * death by default) across death too.
     */
    public static final Supplier<AttachmentType<ActiveEffects>> ACTIVE_EFFECTS = ATTACHMENT_TYPES.register("active_effects",
            () -> AttachmentType.builder(ActiveEffects::empty)
                    .serialize(ActiveEffects.CODEC)
                    .copyOnDeath()
                    .build());

    /** Tracks which Global events are currently afflicting this player — see {@link ActiveAfflictions}. */
    public static final Supplier<AttachmentType<ActiveAfflictions>> ACTIVE_AFFLICTIONS = ATTACHMENT_TYPES.register("active_afflictions",
            () -> AttachmentType.builder(ActiveAfflictions::empty)
                    .serialize(ActiveAfflictions.CODEC)
                    .copyOnDeath()
                    .build());

    private static final Codec<Set<ResourceLocation>> RESOURCE_LOCATION_SET_CODEC =
            ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    /** Which curses/blessings this player has discovered (CLAUDE.md section 2.7) — see {@link DiscoveryManager}. */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED_EFFECTS = ATTACHMENT_TYPES.register("discovered_effects",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    .copyOnDeath()
                    .build());

    /** Which neutrals/globals this player has discovered — see {@link DiscoveryManager}. */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED_EVENTS = ATTACHMENT_TYPES.register("discovered_events",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    .copyOnDeath()
                    .build());

    /** Game time of this player's first-ever join, -1 if not yet recorded — see {@link GracePeriod}. */
    public static final Supplier<AttachmentType<Long>> FIRST_SEEN_TICK = ATTACHMENT_TYPES.register("first_seen_tick",
            () -> AttachmentType.builder(() -> -1L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    /**
     * Thirst points for the Thirst Meter curse (Phase D HUD): {@code -1} = curse not active (bar hidden),
     * {@code 0..THIRST_MAX} = active. Auto-synced to the client so the HUD overlay can read it directly —
     * NeoForge 21.1 attachment sync, no hand-rolled payload needed. See {@code CurseThirstMeter} +
     * {@code client/ThirstHudLayer}.
     */
    public static final Supplier<AttachmentType<Integer>> THIRST = ATTACHMENT_TYPES.register("thirst",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Extra "second stomach" hunger for the Gluttony curse (Phase D HUD): {@code -1} = curse not active
     * (extra row hidden), {@code 0..GLUTTONY_EXTRA_MAX} = active. Auto-synced like {@link #THIRST}; rendered
     * by {@code client/GluttonyHudLayer} as a second hunger row.
     */
    public static final Supplier<AttachmentType<Integer>> GLUTTONY_HUNGER = ATTACHMENT_TYPES.register("gluttony_hunger",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Loading Screen: a fresh random non-zero value each time a door fires the prank, {@code 0} when there's
     * nothing to show. Auto-synced; the client watches for the value CHANGING to know a new session started,
     * and seeds that session's RNG from it.
     *
     * <p>The server deliberately owns nothing but this signal. Duration, stutters, the fake restart and the
     * input lock all live client-side, because the screen's length is dynamic (a stutter extends it) and the
     * lock has to end exactly when the animation does — splitting that across the network would let the two
     * drift apart.
     */
    public static final Supplier<AttachmentType<Long>> LOADING_SCREEN_SESSION = ATTACHMENT_TYPES.register("loading_screen_session",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    /** Whether the Chat (Twitch overlay) blessing is active (Phase D): {@code -1} inactive, {@code 1} active. Auto-synced so the client overlay knows to render. */
    public static final Supplier<AttachmentType<Integer>> CHAT_OVERLAY = ATTACHMENT_TYPES.register("chat_overlay",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * The Organised blessing's 9 extra inventory slots (Phase D). Serialized (and copied on death, since
     * curses/blessings persist through death); NOT synced — the vanilla chest menu it's opened through
     * handles slot sync while open. Dropped when the blessing expires (see {@code BlessingOrganised}).
     */
    public static final Supplier<AttachmentType<List<ItemStack>>> ORGANISED_ITEMS = ATTACHMENT_TYPES.register("organised_items",
            () -> AttachmentType.<List<ItemStack>>builder(() -> List.of())
                    .serialize(ItemStack.OPTIONAL_CODEC.listOf())
                    .copyOnDeath()
                    .build());

    // --- Client-side curse flags (Phase D). Each is an auto-synced int: -1 inactive, 1 active. The actual
    // effect (reversed input / window bounce / window rename / camera hijack) runs client-side off the flag.
    public static final Supplier<AttachmentType<Integer>> MOONWALKER_ACTIVE = ATTACHMENT_TYPES.register("moonwalker_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<Integer>> SCREENSAVER_ACTIVE = ATTACHMENT_TYPES.register("screensaver_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<Integer>> MINOR_INCONVENIENCE_ACTIVE = ATTACHMENT_TYPES.register("minor_inconvenience_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Pacing's dramatic-freeze window end tick (like {@link #LOADING_SCREEN_END_TICK}) — set on an "epic
     * action". Set not only on the victim but on every player caught in the freeze, so their camera is
     * hijacked into the same cinematic too.
     */
    public static final Supplier<AttachmentType<Long>> PACING_END_TICK = ATTACHMENT_TYPES.register("pacing_end_tick",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * Pacing: the entity id the cinematic camera orbits — always the VICTIM (a player, so the camera never
     * inherits a mob's disorienting spectator shader). Synced so every included player's client can point its
     * own camera at the same subject. {@code -1} = not in a moment.
     */
    public static final Supplier<AttachmentType<Integer>> PACING_FOCUS_ID = ATTACHMENT_TYPES.register("pacing_focus_id",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Game tick the current Pacing "charge" cycle started from — the trigger chance ramps 0→1 across
     * {@code PacingManager.RAMP_TICKS} since this tick, resetting when a Pacing moment fires. Set high (a
     * time in the past) on curse apply so the chance starts high. Server-only (not synced).
     */
    public static final Supplier<AttachmentType<Long>> PACING_CHARGE_START = ATTACHMENT_TYPES.register("pacing_charge_start",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    /**
     * Which diet the Allergic curse rolled for this player (ordinal of {@code CurseAllergic.Diet}); -1 =
     * unrolled. Server-only — the victim isn't told which diet they got, they find out by eating
     * (Rule 6). Persisted so the diet stays stable across relogs for the curse's whole duration.
     */
    public static final Supplier<AttachmentType<Integer>> ALLERGIC_DIET = ATTACHMENT_TYPES.register("allergic_diet",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).build());

    /**
     * Backseat Driver: game tick the current AI-takeover episode ends. Auto-synced so the client can cut the
     * rider's steering input dead for the duration ("player control is completely shut off").
     */
    public static final Supplier<AttachmentType<Long>> BACKSEAT_EPISODE_END = ATTACHMENT_TYPES.register("backseat_episode_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * Backseat Driver: game tick after which another takeover may occur. Doubles as the ramp origin — the
     * takeover chance grows the longer you've been riding since this tick. Server-only.
     */
    public static final Supplier<AttachmentType<Long>> BACKSEAT_NEXT_ALLOWED = ATTACHMENT_TYPES.register("backseat_next_allowed",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    /**
     * Backseat Driver: the yaw the AI wants to drive toward. Auto-synced because a ridden mount takes its
     * facing from the RIDER's yaw and its throttle from the rider's forward input — so the hijack steers the
     * rider client-side and lets the mount walk there under its own movement code (real gait, gravity,
     * collision) instead of the entity being shoved around by forced velocity.
     */
    public static final Supplier<AttachmentType<Float>> BACKSEAT_DRIVE_YAW = ATTACHMENT_TYPES.register("backseat_drive_yaw",
            () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());

    /**
     * Bad Swimmer: {@code -1} inactive, {@code 1} active. Auto-synced because the curse's constant downward
     * pull has to be applied client-side — player movement is client-authoritative, and vanilla's fluid
     * gravity (the attribute half) is skipped entirely while sprinting, so the pull is the only part that
     * still bites while you're swimming. See {@code client/ClientCurseHandler}.
     */
    public static final Supplier<AttachmentType<Integer>> BAD_SWIMMER_ACTIVE = ATTACHMENT_TYPES.register("bad_swimmer_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Violence: game tick of the last forced swing. Only the IMPULSIVE urges ramp off this — the longer you
     * go without one, the likelier the next becomes. Sight-based swings don't ramp (they're near-certain
     * already) but do reset it. Server-only.
     */
    public static final Supplier<AttachmentType<Long>> VIOLENCE_LAST_SWING = ATTACHMENT_TYPES.register("violence_last_swing",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    /**
     * Butterfingers: game tick after which another fumble may happen. Deliberately ONE shared cooldown across
     * all three triggers (passive, on-damage, on-swing) so a run of hits can't strip you bare. Server-only.
     */
    public static final Supplier<AttachmentType<Long>> BUTTERFINGERS_NEXT_ALLOWED = ATTACHMENT_TYPES.register("butterfingers_next_allowed",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    /**
     * Heavyweight: game tick the collapse camera-shake ends. Auto-synced — there is no vanilla screen shake,
     * so the client rattles the view itself off this in {@code ComputeCameraAngles}.
     */
    public static final Supplier<AttachmentType<Long>> HEAVYWEIGHT_SHAKE_END = ATTACHMENT_TYPES.register("heavyweight_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** Sticky: {@code -1} inactive, {@code 1} active. Synced so the client can suppress the drop KEY. */
    public static final Supplier<AttachmentType<Integer>> STICKY_ACTIVE = ATTACHMENT_TYPES.register("sticky_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Ugly: {@code -1} inactive, otherwise a random roll picked once when the curse lands.
     *
     * <p>The server deliberately does NOT choose a specific skin — it can't, because the skins are a
     * client-side resource folder the server never sees. It rolls a plain number; each client takes it
     * modulo however many skins IT can list, so every client independently lands on the same one for that
     * player without any of them having to agree in advance.
     *
     * <p>Synced, and note that NeoForge syncs entity attachments to every player TRACKING the entity as well
     * as its owner — which is exactly what makes this visible to other people rather than just the victim.
     */
    public static final Supplier<AttachmentType<Integer>> UGLY_SKIN = ATTACHMENT_TYPES.register("ugly_skin",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /** Social Outcast: {@code -1} inactive, {@code 1} active. Auto-synced — the hiding is pure client render. */
    public static final Supplier<AttachmentType<Integer>> SOCIAL_OUTCAST_ACTIVE = ATTACHMENT_TYPES.register("social_outcast_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Social Outcast: entity ids currently REVEALED because they recently hurt you.
     *
     * <p>This has to be synced from the server rather than worked out client-side, because who dealt the
     * damage is server-authoritative — the client is told that it was hurt, not reliably by whom. The list is
     * small (only whoever has hit you inside the reveal window) and only changes when someone lands a hit or
     * a window lapses, so syncing it whole is cheap.
     */
    public static final Supplier<AttachmentType<List<Integer>>> SOCIAL_OUTCAST_REVEALED = ATTACHMENT_TYPES.register("social_outcast_revealed",
            // NOTE the zero-arg lambda rather than List::of — the method reference is ambiguous between
            // builder(Supplier) and builder(Function<IAttachmentHolder, T>). Same as ORGANISED_ITEMS.
            () -> AttachmentType.<List<Integer>>builder(() -> List.of())
                    .serialize(Codec.INT.listOf())
                    .sync(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
                    .build());

    /**
     * Heavy: {@code -1} inactive, {@code 1} active. Auto-synced because the "anchor" sink has to be applied
     * CLIENT-side — player movement is client-authoritative, and vanilla divides gravity by 16 in water, so
     * the GRAVITY attribute alone leaves you doing a slightly brisker version of the same gentle bob. Same
     * reasoning (and the same fix) as {@link #BAD_SWIMMER_ACTIVE}.
     */
    public static final Supplier<AttachmentType<Integer>> HEAVY_ACTIVE = ATTACHMENT_TYPES.register("heavy_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Delusions: {@code 0} = curse inactive (the client clears every fake player it's showing), any other
     * value = active, and the value CHANGING is the server's "spawn one now" signal — the client seeds that
     * delusion's skin/position/behaviour from it.
     *
     * <p>Same split as {@link #LOADING_SCREEN_SESSION}, and for the same reason: the server owns only the
     * schedule (so discovery and the curse's lifetime stay authoritative), while the fake players themselves
     * are pure client-side illusions. They have to be — they're {@code RemotePlayer}s that exist in one
     * player's {@code ClientLevel} only, so no other client and not the server can ever see or confirm them.
     */
    public static final Supplier<AttachmentType<Long>> DELUSIONS_SIGNAL = ATTACHMENT_TYPES.register("delusions_signal",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    private WitchModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
