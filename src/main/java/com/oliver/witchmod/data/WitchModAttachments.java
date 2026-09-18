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

/**
 * all per-player attachment types — active effects, discovery sets, and the many synced curse/blessing
 * flags the client reads to render. many are auto-synced (to trackers, not just the owner) so client-side fx
 * work; effect state is copy-on-death since effects don't expire on death.
 */
public final class WitchModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WitchMod.MODID);

    /** persists across relog and death (effects don't expire on death by default). */
    public static final Supplier<AttachmentType<ActiveEffects>> ACTIVE_EFFECTS = ATTACHMENT_TYPES.register("active_effects",
            () -> AttachmentType.builder(ActiveEffects::empty)
                    .serialize(ActiveEffects.CODEC)
                    .copyOnDeath()
                    .build());

    private static final Codec<Set<ResourceLocation>> RESOURCE_LOCATION_SET_CODEC =
            ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    /** which curses/blessings this player has discovered — see {@link DiscoveryManager}. */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED_EFFECTS = ATTACHMENT_TYPES.register("discovered_effects",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    // synced so the client-side Compendium knows which attachments are discovered vs rumours.
                    .sync(ByteBufCodecs.collection((java.util.function.IntFunction<Set<ResourceLocation>>) HashSet::new,
                            ResourceLocation.STREAM_CODEC))
                    .copyOnDeath()
                    .build());

    /**
     * Which Table modifiers this player has discovered — marked the first time they cast a ritual USING that
     * modifier (see {@link DiscoveryManager#markModifierDiscovered}). The Compendium's Modifiers chapter always
     * shows the modifier's name + item icon, but keeps its description a rumour until it's in this set. Ids are
     * {@code witchmod:<modifier.id()>}.
     */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED_MODIFIERS = ATTACHMENT_TYPES.register("discovered_modifiers",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    .sync(ByteBufCodecs.collection((java.util.function.IntFunction<Set<ResourceLocation>>) HashSet::new,
                            ResourceLocation.STREAM_CODEC))
                    .copyOnDeath()
                    .build());

    /**
     * Effects cast with the Recovery Compass modifier — the exception to Rule 4: these do NOT persist past death.
     * Deliberately NOT {@code copyOnDeath}, and the death handler strips these ids from ACTIVE_EFFECTS before the
     * respawn copy runs.
     */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> NON_PERSISTENT_EFFECTS = ATTACHMENT_TYPES.register("non_persistent_effects",
            () -> AttachmentType.builder((Supplier<Set<ResourceLocation>>) HashSet::new)
                    .serialize(RESOURCE_LOCATION_SET_CODEC)
                    .build());

    /** game time of this player's first-ever join, -1 if not yet recorded — see {@link GracePeriod}. */
    public static final Supplier<AttachmentType<Long>> FIRST_SEEN_TICK = ATTACHMENT_TYPES.register("first_seen_tick",
            () -> AttachmentType.builder(() -> -1L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    /**
     * Thirst points for the Thirst Meter curse: {@code -1} = curse not active (bar hidden),
     * {@code 0..THIRST_MAX} = active. Auto-synced to the client so the HUD overlay can read it directly —
     * NeoForge 21.1 attachment sync, no hand-rolled payload needed. See {@code CurseThirstMeter} +
     * {@code client/ThirstHudLayer}.
     */
    public static final Supplier<AttachmentType<Integer>> THIRST = ATTACHMENT_TYPES.register("thirst",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /** guardian Angel: 1 once its once-per-blessing random-blessing gift has been spent (persisted; shown in the Scrying Mirror). */
    public static final Supplier<AttachmentType<Integer>> GUARDIAN_GIFT_USED = ATTACHMENT_TYPES.register("guardian_gift_used",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    /** guardian Angel render marker on the ALLAY: 0 = not a guardian, else current Mode ordinal + 1. Synced to
     *  trackers so the halo + movement trail render CLIENT-side (no per-tick server particle packets). */
    public static final Supplier<AttachmentType<Integer>> GUARDIAN_RENDER = ATTACHMENT_TYPES.register("guardian_render",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Extra "second stomach" hunger for the Gluttony curse: {@code -1} = curse not active
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

    /** whether the Chat (Twitch overlay) blessing is active: {@code -1} inactive, {@code 1} active. Auto-synced so the client overlay knows to render. */
    public static final Supplier<AttachmentType<Integer>> CHAT_OVERLAY = ATTACHMENT_TYPES.register("chat_overlay",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /** chat blessing: the streamer's current sub count (synced, shown in the overlay header and used for the end reward). Persists through death with the blessing. */
    public static final Supplier<AttachmentType<Integer>> CHAT_SUBS = ATTACHMENT_TYPES.register("chat_subs",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).copyOnDeath().build());

    /** chat blessing: current entertainment level 0..100 (synced, not saved). Drives the overlay's live viewer count + hype-train bar. */
    public static final Supplier<AttachmentType<Integer>> CHAT_HYPE = ATTACHMENT_TYPES.register("chat_hype",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * The Organised blessing's 9 extra inventory slots. Serialized (and copied on death, since
     * curses/blessings persist through death); NOT synced — the vanilla chest menu it's opened through
     * handles slot sync while open. Dropped when the blessing expires (see {@code BlessingOrganised}).
     */
    public static final Supplier<AttachmentType<List<ItemStack>>> ORGANISED_ITEMS = ATTACHMENT_TYPES.register("organised_items",
            () -> AttachmentType.<List<ItemStack>>builder(() -> List.of())
                    .serialize(ItemStack.OPTIONAL_CODEC.listOf())
                    .copyOnDeath()
                    .build());

    // --- Client-side curse flags. Each is an auto-synced int: -1 inactive, 1 active. The actual
    // effect (reversed input / window bounce / window rename / camera hijack) runs client-side off the flag.
    public static final Supplier<AttachmentType<Integer>> MOONWALKER_ACTIVE = ATTACHMENT_TYPES.register("moonwalker_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** builder blessing: {@code 1} active, {@code -1} inactive. Client zeroes the place/break delays while set. */
    public static final Supplier<AttachmentType<Integer>> BUILDER_ACTIVE = ATTACHMENT_TYPES.register("builder_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** berserker blessing: {@code 1} active, {@code -1} inactive. Client sends a miss packet on air-swings while set. */
    public static final Supplier<AttachmentType<Integer>> BERSERKER_ACTIVE = ATTACHMENT_TYPES.register("berserker_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** ocean's Blessing: {@code 1} active, {@code -1} inactive. Client applies the fast-swim boost while set and in water. */
    public static final Supplier<AttachmentType<Integer>> OCEANS_ACTIVE = ATTACHMENT_TYPES.register("oceans_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** spider blessing: {@code 1} active, {@code -1} inactive. Client does the wall-climbing while set. */
    /** ninja blessing: {@code 1} active, {@code -1} inactive. Client handles the mid-air double jump + swing woosh. */
    public static final Supplier<AttachmentType<Integer>> NINJA_ACTIVE = ATTACHMENT_TYPES.register("ninja_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** prop Hunt blessing: the BlockState id the player is disguised as ({@code -1} = not disguised). Synced so every client renders the disguise. */
    public static final Supplier<AttachmentType<Integer>> PROPHUNT_BLOCK = ATTACHMENT_TYPES.register("prophunt_block",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** prop Hunt: the exact world cell the disguise is ANCHORED to (a {@code BlockPos.asLong()}) while crouched, or {@code Long.MIN_VALUE} when moving (the block follows you). Synced for exact grid rendering. */
    public static final Supplier<AttachmentType<Long>> PROPHUNT_ANCHOR = ATTACHMENT_TYPES.register("prophunt_anchor",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    public static final Supplier<AttachmentType<Integer>> SPIDER_ACTIVE = ATTACHMENT_TYPES.register("spider_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** forgiveness blessing: {@code 1} active, {@code -1} inactive. Synced so the client can do the enlarged-hitbox melee assist on a near-miss. */
    public static final Supplier<AttachmentType<Integer>> FORGIVENESS_ACTIVE = ATTACHMENT_TYPES.register("forgiveness_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** blessing of Speed: {@code 1} active, {@code -1} inactive. Synced so the client can cancel the sprint FOV zoom the extra speed would cause. */
    public static final Supplier<AttachmentType<Integer>> SPEED_ACTIVE = ATTACHMENT_TYPES.register("speed_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** speed Demon blessing: {@code 1} active, {@code -1} inactive. Synced so the client can speed up a ridden (client-authoritative) BOAT. */
    public static final Supplier<AttachmentType<Integer>> SPEED_DEMON_ACTIVE = ATTACHMENT_TYPES.register("speed_demon_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** carelessness curse: {@code 1} active, {@code -1} inactive. Client renders the whole health bar as black hearts. */
    public static final Supplier<AttachmentType<Integer>> CARELESSNESS_ACTIVE = ATTACHMENT_TYPES.register("carelessness_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** narcolepsy curse: the game tick the current sleep ends ({@code 0} = awake). Client runs the sleep overlay + mash while it's in the future. */
    public static final Supplier<AttachmentType<Long>> NARCOLEPSY_SLEEP_END = ATTACHMENT_TYPES.register("narcolepsy_sleep_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * Narcolepsy sleep depth for the CURRENT sleep, encoding two things in one synced int (0 while awake):
     * the low digit is the depth tier (0 normal, 1 deep, 2 very deep — deeper = more mashing to wake), and
     * +10 flags the debug THIRD-PERSON sleep (so the caster can watch their own lying animation). The client
     * reads {@code depth % 10} for difficulty and {@code depth >= 10} for the third-person camera + local pose.
     */
    public static final Supplier<AttachmentType<Integer>> NARCOLEPSY_DEPTH = ATTACHMENT_TYPES.register("narcolepsy_depth",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * The Dweller: {@code -1} inactive, otherwise the current dread STAGE (0..3). Drives the client-side
     * black-and-white shader, the thickened fog, and how oppressive both get — the higher the anger tier, the
     * darker the world. Synced to the owning client only (it's a private horror).
     */
    public static final Supplier<AttachmentType<Integer>> DWELLER_ACTIVE = ATTACHMENT_TYPES.register("dweller_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * The Dweller: a CONTINUOUS dread level 0..1 (the curse's anger as a fraction), synced so the client can
     * roll the desaturation shader and the closing fog in SMOOTHLY as the curse progresses, rather than
     * snapping between tiers.
     */
    public static final Supplier<AttachmentType<Float>> DWELLER_DREAD = ATTACHMENT_TYPES.register("dweller_dread",
            () -> AttachmentType.builder(() -> 0.0F).sync(ByteBufCodecs.FLOAT).build());

    /**
     * The Dweller: the raw DREAD value (0..DWELLER_ANGER_MAX), PERSISTED so it survives logout/relog and a
     * normal death (curses don't expire on death). The transient per-victim server State seeds its anger from
     * this on (re)creation and writes it back each tick; only dying to the CHASE resets it to 0.
     */
    public static final Supplier<AttachmentType<Double>> DWELLER_ANGER = ATTACHMENT_TYPES.register("dweller_anger",
            () -> AttachmentType.builder(() -> -1.0)
                    .serialize(Codec.DOUBLE)
                    .copyOnDeath()
                    .build());

    /**
     * The Dweller: a synced game-tick at which a screen FLICKER ends. While the world time is below it, the
     * client briefly slams the fog in and darkens — "the lights just went out" — for Shadow-Pass / Lights-Out
     * scares. {@code Long.MIN_VALUE} = no flicker.
     */
    public static final Supplier<AttachmentType<Long>> DWELLER_FLICKER = ATTACHMENT_TYPES.register("dweller_flicker",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * The Dweller MIMIC event: {@code 0} = no impostor; any other value is a session seed. On a change the
     * victim's client spawns ONE fake player wearing a real online player's face that just stands and STARES,
     * then drops the mask into shadow + a scream — implying the Dweller was wearing it. Victim-only, like the
     * rest of the curse. (Distinct from the Delusions curse: one stalking impostor with a reveal, not a wander.)
     */
    public static final Supplier<AttachmentType<Long>> DWELLER_MIMIC = ATTACHMENT_TYPES.register("dweller_mimic",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    /** the Dweller: {@code 1} while its subtle BREATHING loop should play (it's close and watching), else {@code 0}. */
    public static final Supplier<AttachmentType<Integer>> DWELLER_BREATHING = ATTACHMENT_TYPES.register("dweller_breathing",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    /** gladiator: {@code 1} active, {@code -1} inactive. Gates the parry HUD + right-click handling on the client. */
    public static final Supplier<AttachmentType<Integer>> GLADIATOR_ACTIVE = ATTACHMENT_TYPES.register("gladiator_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** gladiator: game tick the current parry window ends (0 = no window). Synced so the client draws the parry bar. */
    public static final Supplier<AttachmentType<Long>> GLADIATOR_PARRY_END = ATTACHMENT_TYPES.register("gladiator_parry_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** gladiator: game tick the parry cooldown ends. Synced so the client bar shows the recharge. */
    public static final Supplier<AttachmentType<Long>> GLADIATOR_COOLDOWN_END = ATTACHMENT_TYPES.register("gladiator_cooldown_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** gladiator: game tick the hand-input lock ends (no switch/swing/use while parrying; extended on a whiff). Synced for the client to enforce. */
    public static final Supplier<AttachmentType<Long>> GLADIATOR_LOCK_END = ATTACHMENT_TYPES.register("gladiator_lock_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** gladiator: game tick the weapon swing is fully recharged after a parry — synced so the CLIENT can set its own attack-cooldown so the indicator actually shows it. */
    public static final Supplier<AttachmentType<Long>> GLADIATOR_WEAPON_READY = ATTACHMENT_TYPES.register("gladiator_weapon_ready",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());
    /** wonky: {@code -1} inactive, {@code 1} active — the client adds a subtle sideways wander while moving. */
    public static final Supplier<AttachmentType<Integer>> WONKY_ACTIVE = ATTACHMENT_TYPES.register("wonky_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    // --- New prototype batch (2026-09-02) client flags: -1 inactive / 1 active, auto-synced. ---
    /** left Handed: flips the rendered main hand + scrambles typed chat + a small aim wobble. Client-driven. */
    public static final Supplier<AttachmentType<Integer>> LEFT_HANDED_ACTIVE = ATTACHMENT_TYPES.register("left_handed_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** ice Skates: the ground stops holding you — client retains horizontal momentum like ice. */
    public static final Supplier<AttachmentType<Integer>> ICE_SKATES_ACTIVE = ATTACHMENT_TYPES.register("ice_skates_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** vertigo: the camera gets wobbly at high altitude. Client-driven off this flag. */
    public static final Supplier<AttachmentType<Integer>> VERTIGO_ACTIVE = ATTACHMENT_TYPES.register("vertigo_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** channels: your left/right audio is swapped. Read by the sound-listener mixin. */
    public static final Supplier<AttachmentType<Integer>> CHANNELS_ACTIVE = ATTACHMENT_TYPES.register("channels_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** hiccups: game tick the current hiccup's brief movement-freeze ends. Client zeroes input (no Slowness → no FOV change). */
    public static final Supplier<AttachmentType<Long>> HICCUPS_FREEZE_END = ATTACHMENT_TYPES.register("hiccups_freeze_end",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());
    /** spotlight: {@code -1} inactive, {@code 1} active. Synced to TRACKERS so every nearby client renders the light pillar LOCALLY (no server particle spam). */
    public static final Supplier<AttachmentType<Integer>> SPOTLIGHT_ACTIVE = ATTACHMENT_TYPES.register("spotlight_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** narrator: {@code -1} inactive, {@code 1} active. Synced so the CLIENT can report client-only events (pausing / tabbing out) back for narration. */
    public static final Supplier<AttachmentType<Integer>> NARRATOR_ACTIVE = ATTACHMENT_TYPES.register("narrator_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** siren's Call: {@code 1} while the longing is high enough to march you to water; {@code -1} otherwise. */
    public static final Supplier<AttachmentType<Integer>> SIREN_PULL_ACTIVE = ATTACHMENT_TYPES.register("siren_pull_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** siren's Call: the heading toward the nearest water, which the client walks the hijacked victim along. */
    public static final Supplier<AttachmentType<Float>> SIREN_PULL_YAW = ATTACHMENT_TYPES.register("siren_pull_yaw",
            () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());
    /** siren's Call: 0..1 magenta-shader strength; 1 while mind-controlled, fading to 0 over the water grace. */
    public static final Supplier<AttachmentType<Float>> SIREN_SHADER = ATTACHMENT_TYPES.register("siren_shader",
            () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());

    // --- Stick Drift. Mode + direction are rolled ONCE at apply and never change (a stick doesn't develop a
    // new drift mid-session); the intensity/end pair is the current episode, which the server retunes.
    /** stick Drift: {@code -1} inactive, {@code 0} camera drift, {@code 1} movement drift. */
    public static final Supplier<AttachmentType<Integer>> STICK_DRIFT_MODE = ATTACHMENT_TYPES.register("stick_drift_mode",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());
    /** stick Drift: the fixed direction it drifts, as an angle in radians. Constant for the curse's life. */
    public static final Supplier<AttachmentType<Float>> STICK_DRIFT_ANGLE = ATTACHMENT_TYPES.register("stick_drift_angle",
            () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());
    /** stick Drift: current episode intensity 0..1 ({@code 0} = not drifting right now). */
    public static final Supplier<AttachmentType<Float>> STICK_DRIFT_INTENSITY = ATTACHMENT_TYPES.register("stick_drift_intensity",
            () -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());
    /** stick Drift: game tick the current episode ends. */
    public static final Supplier<AttachmentType<Long>> STICK_DRIFT_END = ATTACHMENT_TYPES.register("stick_drift_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());
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
     * Flat Footed: game tick a nearby footstep-shudder ends, set on OTHER players near the loud victim so
     * their view rattles a little as the heavy footfalls land. Auto-synced; read in `ComputeCameraAngles`
     * alongside the Heavyweight shake.
     */
    public static final Supplier<AttachmentType<Long>> FLAT_FOOTED_SHAKE_END = ATTACHMENT_TYPES.register("flat_footed_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** amethyst Bell: game tick a subtle "toll" camera jolt ends, set for players nearby when it's rung. Synced. */
    public static final Supplier<AttachmentType<Long>> AMETHYST_BELL_SHAKE_END = ATTACHMENT_TYPES.register("amethyst_bell_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * Heavyweight: game tick the collapse camera-shake ends. Auto-synced — there is no vanilla screen shake,
     * so the client rattles the view itself off this in {@code ComputeCameraAngles}.
     */
    /** brute: game tick a smash camera jolt ends, set when you crash through a hard block. Synced. */
    public static final Supplier<AttachmentType<Long>> BRUTE_SHAKE_END = ATTACHMENT_TYPES.register("brute_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** thick Skinned: game tick a little "shrugged it off" camera jolt ends, set when a hit is neutralised. Synced. */
    public static final Supplier<AttachmentType<Long>> THICK_SKINNED_SHAKE_END = ATTACHMENT_TYPES.register("thick_skinned_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** voodoo Doll: game tick the caster's pin/squeeze camera jolt ends. Synced. */
    public static final Supplier<AttachmentType<Long>> VOODOO_SHAKE_END = ATTACHMENT_TYPES.register("voodoo_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    public static final Supplier<AttachmentType<Long>> HEAVYWEIGHT_SHAKE_END = ATTACHMENT_TYPES.register("heavyweight_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** the Dweller: camera-shake end tick (bang/lunge jumpscares + the finale). Synced to the victim. */
    public static final Supplier<AttachmentType<Long>> DWELLER_SHAKE_END = ATTACHMENT_TYPES.register("dweller_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** the Dweller: game-tick a flash-then-dark jumpscare overlay ends (bright flash, then black). Synced. */
    public static final Supplier<AttachmentType<Long>> DWELLER_FLASH_END = ATTACHMENT_TYPES.register("dweller_flash_end",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    /** gladiator: camera-shake end tick + its peak strength (synced) — parry/perfect/whiff each set a different strength. */
    public static final Supplier<AttachmentType<Long>> GLADIATOR_SHAKE_END = ATTACHMENT_TYPES.register("gladiator_shake_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());
    public static final Supplier<AttachmentType<Double>> GLADIATOR_SHAKE_STRENGTH = ATTACHMENT_TYPES.register("gladiator_shake_strength",
            () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).sync(ByteBufCodecs.DOUBLE).build());

    /** sticky: {@code -1} inactive, {@code 1} active. Synced so the client can suppress the drop KEY. */
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

    /** social Outcast: {@code -1} inactive, {@code 1} active. Auto-synced — the hiding is pure client render. */
    public static final Supplier<AttachmentType<Integer>> SOCIAL_OUTCAST_ACTIVE = ATTACHMENT_TYPES.register("social_outcast_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Trumpet: {@code -1} inactive, {@code 1} active. Synced to trackers (like {@link #UGLY_SKIN}) so EVERY
     * nearby client — not just the victim — spins up the looping fat-trumpet sound at the cursed player's
     * position while they walk, which is what gives their position away. The whole start/stop/pitch decision
     * is made client-side off this flag plus the entity's own (synced) walk animation, sprint flag and pose.
     */
    public static final Supplier<AttachmentType<Integer>> TRUMPET_ACTIVE = ATTACHMENT_TYPES.register("trumpet_active",
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
            // note the zero-arg lambda rather than List::of — the method reference is ambiguous between
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

    /**
     * Immortality: game tick the current "rebuild" recovery started, and the tick it finishes. Both synced so
     * the victim's client can lock movement input and draw the gold→white recovery overlay, computing how far
     * through the rebuild it is from {@code (now - start) / (end - start)}. {@code 0} = not recovering.
     */
    public static final Supplier<AttachmentType<Long>> IMMORTALITY_RECOVERY_START = ATTACHMENT_TYPES.register("immortality_recovery_start",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());
    public static final Supplier<AttachmentType<Long>> IMMORTALITY_RECOVERY_END = ATTACHMENT_TYPES.register("immortality_recovery_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * Immortality: how many deaths this blessing has already saved the player from. Persisted (and copied on
     * death, since blessings survive death) so the recovery time can grow each use and the blessing can break
     * after {@code immortalityMaxUses}. Server-only.
     */
    public static final Supplier<AttachmentType<Integer>> IMMORTALITY_USES = ATTACHMENT_TYPES.register("immortality_uses",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** last Stand: how many revives spent (persisted, survives death); the blessing breaks at lastStandMaxUses. */
    public static final Supplier<AttachmentType<Integer>> LAST_STAND_USES = ATTACHMENT_TYPES.register("last_stand_uses",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    /** last Stand: game-tick the escalating cooldown ends (persisted). Below it, a fatal blow is NOT saved. */
    public static final Supplier<AttachmentType<Long>> LAST_STAND_COOLDOWN_END = ATTACHMENT_TYPES.register("last_stand_cooldown_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    /**
     * Set on the ITEM ENTITIES an Immortality rebuild spills onto the ground: the owner's UUID as a string
     * ({@code ""} = not an Immortality drop). The owner can never pick these back up (the blessing's whole
     * drawback is being trivially lootable BY OTHERS), so the pickup is vetoed for that one player in
     * {@code BlessingEventHandler}. Serialized so it survives chunk unload; anyone else picks up normally.
     */
    public static final Supplier<AttachmentType<String>> IMMORTALITY_DROP_OWNER = ATTACHMENT_TYPES.register("immortality_drop_owner",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build());

    /**
     * Hawk Guy: set on a PROJECTILE the moment it's fired — the entity id of the target the shooter was aiming
     * at, acquired by a cone raycast from the shooter. The projectile then subtly homes on that entity each
     * tick (see {@code ProjectileBlessingHandler}). {@code -1} = no mark. Server-only; short-lived with the
     * projectile so it needs no sync.
     */
    public static final Supplier<AttachmentType<Integer>> HAWKGUY_TARGET = ATTACHMENT_TYPES.register("hawkguy_target",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).build());

    /**
     * Steady Hands: {@code -1} inactive, {@code 1} active. SYNCED, because the bow/crossbow charge-speedup has
     * to run on the CLIENT too — item-use ticks down on the client for the local player, and that value drives
     * both the draw animation AND the power on release. Speeding it up only server-side desyncs them (the draw
     * looks normal but fires at full power). With the flag synced, the client speeds up the visible draw to
     * match. The client can't read {@code ACTIVE_EFFECTS} (not synced), so it reads this instead.
     */
    public static final Supplier<AttachmentType<Integer>> DEXTEROUS_ACTIVE = ATTACHMENT_TYPES.register("dexterous_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** amethyst Bell: game-tick the "consume" purple-dust ramp ends on a caught player (0 = none). Synced to
     * trackers so every client renders the ramp itself — the server sends NO consume particles. */
    public static final Supplier<AttachmentType<Long>> AMETHYST_CONSUME_END = ATTACHMENT_TYPES.register("amethyst_consume_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** amethyst Bell: {@code 1} while the consume ramp is a pre-decided FIZZLE (lighter, no inward stream). Synced. */
    public static final Supplier<AttachmentType<Integer>> AMETHYST_CONSUME_FIZZLE = ATTACHMENT_TYPES.register("amethyst_consume_fizzle",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Nightowl: {@code -1} inactive, {@code 1} active. SYNCED — the client needs it to strip fog everywhere and
     * force full-bright gamma (both are client-render concerns it can't derive from {@code ACTIVE_EFFECTS},
     * which isn't synced). Blindness/Darkness immunity is handled server-side.
     */
    public static final Supplier<AttachmentType<Integer>> NIGHTOWL_ACTIVE = ATTACHMENT_TYPES.register("nightowl_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** organised: {@code 1} while active — synced so the inventory screen can show its stash button. */
    public static final Supplier<AttachmentType<Integer>> ORGANISED_ACTIVE = ATTACHMENT_TYPES.register("organised_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** farmer's Spirit: {@code 1} while active — synced so the client can draw the radius ring + plant motes. */
    public static final Supplier<AttachmentType<Integer>> FARMERS_SPIRIT_ACTIVE = ATTACHMENT_TYPES.register("farmers_spirit_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /** leader: {@code 1} while active — synced so the client can draw the aura radius ring. */
    public static final Supplier<AttachmentType<Integer>> LEADER_ACTIVE = ATTACHMENT_TYPES.register("leader_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Jesus: {@code -1} inactive, {@code 1} active. Synced — walking on the water surface is applied CLIENT-side
     * (player movement is client-authoritative), and the local player's resulting position syncs back up so
     * others see you stroll across the water.
     */
    public static final Supplier<AttachmentType<Integer>> JESUS_ACTIVE = ATTACHMENT_TYPES.register("jesus_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Unseen: {@code -1} inactive, {@code 1} active. Synced to TRACKERS (like {@link #UGLY_SKIN}) so every
     * nearby client knows to hide this player's whole render when they're beyond {@code unseenRevealDistance},
     * and to puff cloak/uncloak particles as they cross that threshold. Mob-target vetoing is server-side.
     */
    public static final Supplier<AttachmentType<Integer>> UNSEEN_ACTIVE = ATTACHMENT_TYPES.register("unseen_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Bouncy: {@code -1} inactive, {@code 1} active. Synced so the client can do the rubbery MOVEMENT physics
     * (rebounding off floors/walls/ceilings, building height with repeated jumps) — those are all
     * client-authoritative, so they have to be applied client-side. See {@code ClientCurseHandler.tickBouncy}.
     */
    public static final Supplier<AttachmentType<Integer>> BOUNCY_ACTIVE = ATTACHMENT_TYPES.register("bouncy_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Coyote: {@code -1} inactive, {@code 1} active. Synced so the client can grant the coyote-time late jump
     * and the edge magnetism — both are movement, which is client-authoritative. See
     * {@code ClientCurseHandler.tickCoyote}.
     */
    public static final Supplier<AttachmentType<Integer>> COYOTE_ACTIVE = ATTACHMENT_TYPES.register("coyote_active",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Excavation: the current mining-speed BONUS (0 = active but not built up, {@code -1} = inactive). Synced,
     * because break speed is computed client-side (mining is client-authoritative) — the client must know the
     * ramp to actually dig faster. The server drives the value (ramps on break, decays when idle).
     */
    public static final Supplier<AttachmentType<Float>> EXCAVATION_BONUS = ATTACHMENT_TYPES.register("excavation_bonus",
            () -> AttachmentType.builder(() -> -1.0F).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());

    /**
     * Main Character: current intensity tier — {@code 0} inactive, {@code 1} (surrounded), {@code 2} (really
     * surrounded). Synced so the protagonist's client plays the drum-theme loop while it's above 0. Server uses
     * it to size the buffs and the outgoing-knockback multiplier.
     */
    public static final Supplier<AttachmentType<Integer>> MAINCHAR_TIER = ATTACHMENT_TYPES.register("mainchar_tier",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Revive flash: game tick a Last Stand / Immortality on-screen "totem" animation ends. Synced so the saved
     * player's client plays the totem-style pop with the Blessed effect icon (see {@code ReviveFlashOverlay}).
     */
    public static final Supplier<AttachmentType<Long>> REVIVE_FLASH_END = ATTACHMENT_TYPES.register("revive_flash_end",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).sync(ByteBufCodecs.VAR_LONG).build());

    /** bedrock Moment (Nightcore bug): a synced game-tick at which the higher-pitch window ends. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_NIGHTCORE = ATTACHMENT_TYPES.register("bedrock_nightcore",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    /** bedrock Moment (Chunk Rejection bug): a synced game-tick at which the forced-low-render-distance window ends. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_CHUNK_REJECT = ATTACHMENT_TYPES.register("bedrock_chunk_reject",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    /** bedrock Moment: {@code 1} while the curse is on you, {@code -1} otherwise — gates the always-on client bugs (silent creepers, hotbar drift). */
    public static final Supplier<AttachmentType<Integer>> BEDROCK_ACTIVE = ATTACHMENT_TYPES.register("bedrock_active",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());

    /** bedrock Moment (Marketplace popup): a nonce that changes each time an ad should pop up on the client. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_MARKETPLACE = ATTACHMENT_TYPES.register("bedrock_marketplace",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    /** bedrock Moment (Sound Delay bug): synced game-tick the delayed-sound window ends. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_SOUND_DELAY = ATTACHMENT_TYPES.register("bedrock_sound_delay",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    /** bedrock Moment (Phantom Durability bug): synced game-tick the jittering-durability window ends. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_PHANTOM_DUR = ATTACHMENT_TYPES.register("bedrock_phantom_dur",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    /** bedrock Moment (Perspective Flip bug): synced game-tick the forced-third-person window ends. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_PERSPECTIVE = ATTACHMENT_TYPES.register("bedrock_perspective",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    // bedrock Moment — client windows for the newer bugs. Each is a synced game-tick the window ends at.
    private static Supplier<AttachmentType<Long>> bedrockWindow(String id) {
        return ATTACHMENT_TYPES.register(id, () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());
    }

    /** ghost Item: your held item renders as a random other item. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_GHOST_ITEM = bedrockWindow("bedrock_ghost_item");
    /** input Lag: movement input is applied ~0.3s late. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_INPUT_LAG = bedrockWindow("bedrock_input_lag");
    /** texture Flicker: the missing-texture checker flashes over the screen. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_TEXTURE_FLICKER = bedrockWindow("bedrock_texture_flicker");
    /** sprint Reset: sprint keeps cutting out. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_SPRINT_RESET = bedrockWindow("bedrock_sprint_reset");
    /** language Error: the game language swaps to pirate/welsh. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_LANGUAGE = bedrockWindow("bedrock_language");
    /** speed Blitz: the FREEZE half ends at this tick (movement is recorded, not applied). */
    public static final Supplier<AttachmentType<Long>> BEDROCK_SPEEDBLITZ_FREEZE = bedrockWindow("bedrock_speedblitz_freeze");
    /** speed Blitz: the whole effect (freeze + 3x replay) ends by this tick. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_SPEEDBLITZ_END = bedrockWindow("bedrock_speedblitz_end");
    /** fake BSOD: the blue-screen window ends at this tick. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_BSOD = bedrockWindow("bedrock_bsod");
    /** fake Kick: a nonce — when it CHANGES, the client shows the fake disconnect screen. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_FAKE_KICK = ATTACHMENT_TYPES.register("bedrock_fake_kick",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());
    /** air Swimming: you keep swimming through air like it's water until this tick. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_AIR_SWIM = bedrockWindow("bedrock_air_swim");
    /** hungry: right-click eats whatever you're holding until this tick. */
    public static final Supplier<AttachmentType<Long>> BEDROCK_HUNGRY = bedrockWindow("bedrock_hungry");


    // --- Splitscreen curse -----------------------------------------------------------------------------
    /** splitscreen: entity id of the partner sharing your screen, or -1. Synced (set on BOTH players). */
    public static final Supplier<AttachmentType<Integer>> SPLITSCREEN_PARTNER = ATTACHMENT_TYPES.register("splitscreen_partner",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());
    /** splitscreen phase: 0 off, 1 ENTERING (fake-load), 2 ACTIVE (split), 3 EXITING (fake-load). Synced. */
    public static final Supplier<AttachmentType<Integer>> SPLITSCREEN_PHASE = ATTACHMENT_TYPES.register("splitscreen_phase",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());
    /** splitscreen: game-tick the current enter/exit fake-loading transition ends. Synced. */
    public static final Supplier<AttachmentType<Long>> SPLITSCREEN_LOAD_END = ATTACHMENT_TYPES.register("splitscreen_load_end",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());
    /** splitscreen: 1 while EITHER of you has a sign open — both freeze (shared screen). Synced. */
    public static final Supplier<AttachmentType<Integer>> SPLITSCREEN_SIGN_LOCK = ATTACHMENT_TYPES.register("splitscreen_sign_lock",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());
    /** splitscreen: a nonce bumped when a sign UI must force-close (someone took damage). Synced. */
    public static final Supplier<AttachmentType<Long>> SPLITSCREEN_SIGN_CLOSE = ATTACHMENT_TYPES.register("splitscreen_sign_close",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    // --- Cutaway Gag curse -----------------------------------------------------------------------------
    /**
     * Cutaway Gag: entity id of the player being spectated during a cutaway (the client hijacks its camera
     * there and locks movement), or -1 when not mid-cutaway. Synced to the cursed watcher.
     */
    public static final Supplier<AttachmentType<Integer>> CUTAWAY_TARGET = ATTACHMENT_TYPES.register("cutaway_target",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());

    /** cutaway Gag (Annoying Music): synced game-tick the annoying-music window ends; the client loops a track. */
    public static final Supplier<AttachmentType<Long>> CUTAWAY_MUSIC_END = ATTACHMENT_TYPES.register("cutaway_music_end",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE).sync(ByteBufCodecs.VAR_LONG).build());

    /**
     * Cutaway Gag: PERSISTED return position while the watcher is relocated to spectate — an empty tag when
     * not mid-cutaway, otherwise {dim,x,y,z,yaw,pitch,invis,nograv}. Survives relog/world-reload so the watcher
     * is never stranded at the overhead vantage (the transient server map is lost on reload).
     */
    public static final Supplier<AttachmentType<net.minecraft.nbt.CompoundTag>> CUTAWAY_RETURN = ATTACHMENT_TYPES.register("cutaway_return",
            () -> AttachmentType.<net.minecraft.nbt.CompoundTag>builder(() -> new net.minecraft.nbt.CompoundTag()).serialize(net.minecraft.nbt.CompoundTag.CODEC).build());

    /** cutaway Gag: which looped SFX the watcher's client should play (-1 none, 0 helicopter, 1 tractor beam). Synced. */
    public static final Supplier<AttachmentType<Integer>> CUTAWAY_LOOP = ATTACHMENT_TYPES.register("cutaway_loop",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());

    /**
     * Cutaway Gag: server-chosen entity id the spectate camera should AIM at, overriding the default (the
     * victim = {@link #CUTAWAY_TARGET}). -1 = aim at the victim. Lets a gag frame something ELSE mid-cutaway
     * (e.g. the marriage objector or the exploding spouse) for dramatic angles. Synced to the watcher.
     */
    public static final Supplier<AttachmentType<Integer>> CUTAWAY_LOOK = ATTACHMENT_TYPES.register("cutaway_look",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());

    // --- Blessing of Flight: -1 off / 1 active, an energy fraction 0..1 for the bar + client rise, and a
    // lockout end-tick (knocked out of flight briefly when hit). ---
    public static final Supplier<AttachmentType<Integer>> FLIGHT_ACTIVE = ATTACHMENT_TYPES.register("flight_active",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<Float>> FLIGHT_ENERGY = ATTACHMENT_TYPES.register("flight_energy",
            () -> AttachmentType.builder(() -> 1.0F).sync(ByteBufCodecs.FLOAT).build());
    public static final Supplier<AttachmentType<Long>> FLIGHT_LOCKOUT_END = ATTACHMENT_TYPES.register("flight_lockout_end",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    // --- Blessing of Thunder: -1 off / 0..4 the current static-charge tier (drives the client aura). ---
    public static final Supplier<AttachmentType<Integer>> THUNDER_TIER = ATTACHMENT_TYPES.register("thunder_tier",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());

    // --- Blessing of Disguise: the mob the player is CURRENTLY rendered as (-1 = real player, 0 cow / 1 sheep
    // / 2 pig); the server flips it to -1 while the disguise is "broken". ---
    public static final Supplier<AttachmentType<Integer>> DISGUISE_TYPE = ATTACHMENT_TYPES.register("disguise_type",
            () -> AttachmentType.builder(() -> -1).sync(ByteBufCodecs.VAR_INT).build());

    // --- Concealment synergy (Prop Hunt + Disguise): a synced tick until which a form change keeps you briefly
    // unrendered (armour and all, like Unseen), so a switch reads as a puff-of-smoke vanish. ---
    public static final Supplier<AttachmentType<Long>> CONCEAL_FLASH_END = ATTACHMENT_TYPES.register("conceal_flash_end",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    private WitchModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
