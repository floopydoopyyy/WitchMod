package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EchoesChatMessages;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * You keep hearing things that aren't there (master-spec Echoes). Every so often the victim — and ONLY the
 * victim — hears a completely ordinary game sound, played at a believable spot in the world: footsteps
 * behind them, someone sprinting up, mining below, a creeper going off, a chest opening, a far-off blast.
 *
 * <p><b>Believability is the whole design.</b> Every sound is delivered as a positional
 * {@link ClientboundSoundPacket} sent down that one player's connection, so it's directional and attenuates
 * exactly like a real sound but nobody else can hear it. Placement is checked against the world too —
 * footsteps land on real ground and use that block's own step sound, mining comes from inside solid rock
 * below you, distant explosions are actually distant. Sequences (approaching footsteps, a burst of mining)
 * are queued over several ticks rather than fired at once.
 *
 * <p>Discovery is deliberately delayed by {@code echoesDiscoveryDelayTicks} after the first hallucination,
 * so the penny drops a moment later instead of the alert giving the sound away.
 */
public final class CurseEchoes extends Effect {
    /** One queued sound, delivered when the game clock reaches {@code dueTick}. */
    private record Echo(long dueTick, Holder<SoundEvent> sound, double x, double y, double z,
                        float volume, float pitch) {}

    private static final Map<UUID, List<Echo>> PENDING = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ECHO = new HashMap<>();
    private static final Map<UUID, Long> DISCOVER_AT = new HashMap<>();

    public CurseEchoes() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.ECHO_SHARD);
    }

    /** You work it out shortly AFTER the first thing you imagine hearing (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        PENDING.remove(id);
        NEXT_ECHO.remove(id);
        DISCOVER_AT.remove(id);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();
        UUID id = target.getUUID();

        // Self-heal: the schedule is transient, so a curse that persisted across a relog would otherwise
        // never fire again (the Yap lesson).
        if (!NEXT_ECHO.containsKey(id)) {
            scheduleNext(target, now);
        }

        deliverDue(target, now);

        Long discoverAt = DISCOVER_AT.get(id);
        if (discoverAt != null && now >= discoverAt) {
            DISCOVER_AT.remove(id);
            markDiscoveredByVictim(target);
        }

        if (now >= NEXT_ECHO.getOrDefault(id, Long.MAX_VALUE)) {
            hallucinate(target, level, now);
            scheduleNext(target, now);
        }
    }

    // --- Scheduling ------------------------------------------------------------------------------------

    private static void scheduleNext(ServerPlayer target, long now) {
        int min = Config.ECHOES_INTERVAL_MIN_TICKS.get();
        int max = Math.max(min, Config.ECHOES_INTERVAL_MAX_TICKS.get());
        NEXT_ECHO.put(target.getUUID(), now + min + target.getRandom().nextInt(max - min + 1));
    }

    private static void deliverDue(ServerPlayer target, long now) {
        List<Echo> queue = PENDING.get(target.getUUID());
        if (queue == null) {
            return;
        }
        Iterator<Echo> it = queue.iterator();
        while (it.hasNext()) {
            Echo echo = it.next();
            if (now >= echo.dueTick()) {
                send(target, echo);
                it.remove();
            }
        }
        if (queue.isEmpty()) {
            PENDING.remove(target.getUUID());
        }
    }

    /** Queues a sound for a future tick, at a world position, audible to this player alone. */
    private static void queue(ServerPlayer target, long dueTick, Holder<SoundEvent> sound,
                              Vec3 pos, float volume, float pitch) {
        PENDING.computeIfAbsent(target.getUUID(), k -> new ArrayList<>())
                .add(new Echo(dueTick, sound, pos.x, pos.y, pos.z, volume, pitch));
    }

    /**
     * The delivery itself: a positional sound packet down this one player's connection. Not
     * {@code level.playSound}, which every nearby player would hear — the illusion only works if nobody
     * can confirm it wasn't real.
     */
    private static void send(ServerPlayer target, Echo echo) {
        float volume = echo.volume() * (float) (double) Config.ECHOES_VOLUME.get();
        target.connection.send(new ClientboundSoundPacket(echo.sound(), SoundSource.MASTER,
                echo.x(), echo.y(), echo.z(), volume, echo.pitch(), target.getRandom().nextLong()));
    }

    // --- The hallucinations ----------------------------------------------------------------------------

    /** One kind of hallucination. Returns false if its conditions weren't met, so another can be tried. */
    @FunctionalInterface
    private interface Hallucination {
        boolean fire(ServerPlayer target, ServerLevel level, long now);
    }

    private record Weighted(int weight, Hallucination fire) {}

    /**
     * The pool, weighted by how ORDINARY each sound is. Mundane background noise comes up constantly;
     * a creeper priming behind you or a notification chime stay rare, because the ones that make you spin
     * round lose all their power if they happen every time.
     */
    private static final Weighted[] POOL = {
            new Weighted(12, (t, l, n) -> footstepsNearby(t, l, n, false)),
            new Weighted(7, (t, l, n) -> footstepsNearby(t, l, n, true)),   // sprinting, closing in
            new Weighted(10, CurseEchoes::mining),
            new Weighted(6, CurseEchoes::undeadAmbient),
            new Weighted(6, CurseEchoes::chestOrDoor),
            new Weighted(5, CurseEchoes::distantExplosion),
            new Weighted(5, CurseEchoes::landing),
            new Weighted(5, (t, l, n) -> fakeChat(t, l)),
            new Weighted(4, CurseEchoes::villager),
            new Weighted(4, CurseEchoes::tntLit),
            new Weighted(4, CurseEchoes::caveAmbience),
            new Weighted(3, CurseEchoes::creeperBehind),
            new Weighted(5, CurseEchoes::swimming),
            new Weighted(5, CurseEchoes::eating),
            new Weighted(5, CurseEchoes::animalHurt),
            new Weighted(5, CurseEchoes::swingingAtAir),
            new Weighted(6, CurseEchoes::fighting),
            new Weighted(3, CurseEchoes::miscStartle),
            new Weighted(2, CurseEchoes::ping),             // the rare one
    };

    private void hallucinate(ServerPlayer target, ServerLevel level, long now) {
        int total = 0;
        for (Weighted entry : POOL) {
            total += entry.weight();
        }
        int roll = target.getRandom().nextInt(total);
        for (Weighted entry : POOL) {
            roll -= entry.weight();
            if (roll < 0) {
                if (!entry.fire().fire(target, level, now)) {
                    // Conditions weren't met (nobody else online, no water nearby, no solid ground) — fall
                    // back to something that always works rather than wasting the slot in silence.
                    undeadAmbient(target, level, now);
                }
                break;
            }
        }
        DISCOVER_AT.putIfAbsent(target.getUUID(), now + Config.ECHOES_DISCOVERY_DELAY_TICKS.get());
    }

    /** Someone swimming — only ever placed in actual water, or it's not believable. */
    private static boolean swimming(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = findWaterNear(level, target, 14.0);
        if (at == null) {
            return false; // no water in earshot; someone swimming here would make no sense
        }
        queue(target, now, holder(SoundEvents.PLAYER_SPLASH), at, 0.8F, 1.0F);
        int strokes = 4 + rnd.nextInt(5);
        for (int i = 0; i < strokes; i++) {
            queue(target, now + 6L + i * 7L,
                    holder(rnd.nextBoolean() ? SoundEvents.PLAYER_SWIM : SoundEvents.GENERIC_SWIM),
                    at, 0.7F, 0.9F + rnd.nextFloat() * 0.2F);
        }
        return true;
    }

    /** Someone having a snack, finished off with a burp. */
    private static boolean eating(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = around(target, 2.5, 7.0);
        int bites = 3 + rnd.nextInt(4);
        for (int i = 0; i < bites; i++) {
            queue(target, now + (long) i * 8, holder(SoundEvents.GENERIC_EAT), at,
                    0.7F, 0.9F + rnd.nextFloat() * 0.2F);
        }
        queue(target, now + (long) bites * 8 + 6, holder(SoundEvents.PLAYER_BURP), at, 0.6F, 1.0F);
        return true;
    }

    /** An animal taking a hit somewhere nearby — someone's butchering livestock out of sight. */
    private static boolean animalHurt(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        SoundEvent hurt = switch (rnd.nextInt(4)) {
            case 0 -> SoundEvents.SHEEP_HURT;
            case 1 -> SoundEvents.COW_HURT;
            case 2 -> SoundEvents.PIG_HURT;
            default -> SoundEvents.CHICKEN_HURT;
        };
        Vec3 at = groundedAt(level, target, rnd.nextDouble() * Math.PI * 2.0, 4.0 + rnd.nextDouble() * 8.0);
        if (at == null) {
            at = around(target, 4.0, 12.0);
        }
        int hits = 1 + rnd.nextInt(3);
        for (int i = 0; i < hits; i++) {
            // A swing lands, then the animal cries out — the order sells it.
            queue(target, now + (long) i * 14, holder(SoundEvents.PLAYER_ATTACK_STRONG), at, 0.7F, 1.0F);
            queue(target, now + (long) i * 14 + 2, holder(hurt), at, 0.9F,
                    0.95F + rnd.nextFloat() * 0.1F);
        }
        return true;
    }

    /** Someone whiffing at thin air — the flat "no damage" swing, which is unmistakable. */
    private static boolean swingingAtAir(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = around(target, 3.0, 8.0);
        int swings = 2 + rnd.nextInt(4);
        for (int i = 0; i < swings; i++) {
            queue(target, now + (long) i * (9 + rnd.nextInt(6)),
                    holder(SoundEvents.PLAYER_ATTACK_NODAMAGE), at, 0.8F,
                    0.95F + rnd.nextFloat() * 0.1F);
        }
        return true;
    }

    /** A proper scrap with a zombie: swings landing, the zombie grunting back, sometimes finishing it. */
    private static boolean fighting(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = around(target, 4.0, 11.0);
        queue(target, now, holder(SoundEvents.ZOMBIE_AMBIENT), at, 0.9F, 1.0F);

        int exchanges = 2 + rnd.nextInt(4);
        long t = now + 10;
        for (int i = 0; i < exchanges; i++) {
            SoundEvent swing = switch (rnd.nextInt(3)) {
                case 0 -> SoundEvents.PLAYER_ATTACK_CRIT;
                case 1 -> SoundEvents.PLAYER_ATTACK_SWEEP;
                default -> SoundEvents.PLAYER_ATTACK_STRONG;
            };
            queue(target, t, holder(swing), at, 0.85F, 1.0F);
            queue(target, t + 2, holder(SoundEvents.ZOMBIE_HURT), at, 0.9F,
                    0.95F + rnd.nextFloat() * 0.1F);
            t += 12 + rnd.nextInt(8);
        }
        if (rnd.nextBoolean()) {
            queue(target, t, holder(SoundEvents.ZOMBIE_DEATH), at, 0.9F, 1.0F);
        }
        return true;
    }

    /**
     * A fake notification chime. Played at the victim's own position so it sits dead centre like a real one
     * from their headphones, rather than off in the world.
     */
    private static boolean ping(ServerPlayer target, ServerLevel level, long now) {
        queue(target, now, holder(WitchModSounds.ECHOES_PING.get()), target.position(), 1.0F, 1.0F);
        return true;
    }

    /** Finds standing water within range, so swimming sounds come from somewhere you could actually swim. */
    private static Vec3 findWaterNear(ServerLevel level, ServerPlayer target, double radius) {
        RandomSource rnd = target.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = rnd.nextDouble() * radius;
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * dist);
            for (int dy = 2; dy >= -6; dy--) {
                BlockPos pos = new BlockPos(x, target.blockPosition().getY() + dy, z);
                if (level.getFluidState(pos).is(FluidTags.WATER)) {
                    return new Vec3(x + 0.5, pos.getY() + 0.5, z + 0.5);
                }
            }
        }
        return null;
    }

    /** Footsteps from a random direction, using the real step sound of whatever they'd be walking on. */
    private static boolean footstepsNearby(ServerPlayer target, ServerLevel level, long now, boolean sprinting) {
        RandomSource rnd = target.getRandom();
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double startDist = sprinting ? 9.0 : 3.5 + rnd.nextDouble() * 2.0;
        int steps = sprinting ? 8 + rnd.nextInt(4) : 3 + rnd.nextInt(3);
        int gap = sprinting ? 4 : 11; // sprint cadence vs a calm walk

        for (int i = 0; i < steps; i++) {
            // Sprinters close the distance step by step; a calm walker just wanders past at range.
            double dist = sprinting
                    ? startDist * (1.0 - (double) i / steps) + 1.5
                    : startDist + Math.sin(i) * 0.6;
            Vec3 at = groundedAt(level, target, angle + (sprinting ? 0 : i * 0.15), dist);
            if (at == null) {
                return false;
            }
            queue(target, now + (long) i * gap, stepSoundAt(level, at), at,
                    sprinting ? 0.9F : 0.6F, 0.9F + rnd.nextFloat() * 0.2F);
        }
        return true;
    }

    /** Someone mining below you: a run of hits at a believable pace, then the block breaking. */
    private static boolean mining(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double dist = 3.0 + rnd.nextDouble() * 6.0;
        int depth = 4 + rnd.nextInt(10);
        Vec3 at = new Vec3(target.getX() + Math.cos(angle) * dist,
                target.getY() - depth,
                target.getZ() + Math.sin(angle) * dist);

        BlockPos pos = BlockPos.containing(at);
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return false; // sound would be coming from thin air — pick something else
        }
        Holder<SoundEvent> hit = holder(state.getSoundType().getHitSound());
        Holder<SoundEvent> broke = holder(state.getSoundType().getBreakSound());

        int gap = 4 + rnd.nextInt(6); // "various speeds" — a slow pick vs an efficiency V one
        int hits = 3 + rnd.nextInt(6);
        for (int i = 0; i < hits; i++) {
            queue(target, now + (long) i * gap, hit, at, 0.7F, 0.8F + rnd.nextFloat() * 0.3F);
        }
        queue(target, now + (long) hits * gap, broke, at, 0.9F, 0.9F);
        return true;
    }

    private static boolean undeadAmbient(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        SoundEvent sound = switch (rnd.nextInt(4)) {
            case 0 -> SoundEvents.ZOMBIE_AMBIENT;
            case 1 -> SoundEvents.SKELETON_AMBIENT;
            case 2 -> SoundEvents.SPIDER_AMBIENT;
            default -> SoundEvents.SKELETON_STEP;
        };
        Vec3 at = around(target, 4.0, 12.0);
        queue(target, now, holder(sound), at, 0.9F, 0.95F + rnd.nextFloat() * 0.1F);
        return true;
    }

    /** A chest or door being opened nearby — the classic "someone's in my base". */
    private static boolean chestOrDoor(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = around(target, 3.0, 10.0);
        if (rnd.nextBoolean()) {
            queue(target, now, holder(SoundEvents.CHEST_OPEN), at, 0.8F, 1.0F);
            queue(target, now + 30 + rnd.nextInt(40), holder(SoundEvents.CHEST_CLOSE), at, 0.8F, 1.0F);
        } else {
            queue(target, now, holder(SoundEvents.WOODEN_DOOR_OPEN), at, 0.9F, 1.0F);
            queue(target, now + 25 + rnd.nextInt(35), holder(SoundEvents.WOODEN_DOOR_CLOSE), at, 0.9F, 1.0F);
        }
        return true;
    }

    private static boolean distantExplosion(ServerPlayer target, ServerLevel level, long now) {
        Vec3 at = around(target, 24.0, 48.0);
        queue(target, now, SoundEvents.GENERIC_EXPLODE, at, 1.0F, 0.8F + target.getRandom().nextFloat() * 0.2F);
        return true;
    }

    /** Someone landing a jump or a fall, on actual ground. */
    private static boolean landing(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = groundedAt(level, target, rnd.nextDouble() * Math.PI * 2.0, 2.5 + rnd.nextDouble() * 4.0);
        if (at == null) {
            return false;
        }
        boolean heavy = rnd.nextBoolean();
        queue(target, now, holder(heavy ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL),
                at, 0.9F, 1.0F);
        // A step or two afterwards sells it as a person rather than a noise.
        queue(target, now + 8, stepSoundAt(level, at), at, 0.6F, 1.0F);
        return true;
    }

    /** A chat line from someone genuinely online — shown to the victim alone. */
    private static boolean fakeChat(ServerPlayer target, ServerLevel level) {
        if (level.getServer() == null) {
            return false;
        }
        List<ServerPlayer> others = new ArrayList<>(level.getServer().getPlayerList().getPlayers());
        others.remove(target);
        if (others.isEmpty()) {
            return false; // nobody to impersonate
        }
        String line = EchoesChatMessages.pick(target.getRandom());
        if (line == null) {
            return false; // no lines written
        }
        ServerPlayer speaker = others.get(target.getRandom().nextInt(others.size()));
        target.sendSystemMessage(Component.translatable("chat.type.text",
                speaker.getDisplayName(), Component.literal(line)));
        return true;
    }

    private static boolean villager(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        SoundEvent sound = rnd.nextBoolean() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE;
        queue(target, now, holder(sound), around(target, 4.0, 14.0), 0.9F, 0.95F + rnd.nextFloat() * 0.1F);
        return true;
    }

    private static boolean tntLit(ServerPlayer target, ServerLevel level, long now) {
        Vec3 at = around(target, 3.0, 8.0);
        queue(target, now, holder(SoundEvents.TNT_PRIMED), at, 1.0F, 1.0F);
        return true;
    }

    /** The cave ambience sting — arguably the most unsettling sound in the game, and totally deniable. */
    private static boolean caveAmbience(ServerPlayer target, ServerLevel level, long now) {
        queue(target, now, holder(SoundEvents.AMBIENT_CAVE.value()), around(target, 6.0, 16.0), 1.0F, 1.0F);
        return true;
    }

    /** A creeper igniting right behind you, then going off. Rare on purpose. */
    private static boolean creeperBehind(ServerPlayer target, ServerLevel level, long now) {
        Vec3 behind = behind(target, 2.0, 4.0);
        queue(target, now, holder(SoundEvents.CREEPER_PRIMED), behind, 1.0F, 1.0F);
        queue(target, now + 30, SoundEvents.GENERIC_EXPLODE, behind, 1.0F, 1.0F);
        return true;
    }

    /** Assorted one-offs that all sound like another player going about their business. */
    private static boolean miscStartle(ServerPlayer target, ServerLevel level, long now) {
        RandomSource rnd = target.getRandom();
        Vec3 at = around(target, 3.0, 12.0);
        SoundEvent sound = switch (rnd.nextInt(6)) {
            case 0 -> SoundEvents.ARROW_SHOOT;
            case 1 -> SoundEvents.ENDERMAN_TELEPORT;
            case 2 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case 3 -> SoundEvents.ITEM_PICKUP;
            case 4 -> SoundEvents.ANVIL_LAND;
            default -> SoundEvents.GENERIC_SPLASH;
        };
        queue(target, now, holder(sound), at, 0.8F, 0.95F + rnd.nextFloat() * 0.1F);
        return true;
    }

    // --- Placement helpers -----------------------------------------------------------------------------

    /** A point at a random bearing around the player, at their own height. */
    private static Vec3 around(ServerPlayer target, double minDist, double maxDist) {
        RandomSource rnd = target.getRandom();
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double dist = minDist + rnd.nextDouble() * (maxDist - minDist);
        return new Vec3(target.getX() + Math.cos(angle) * dist,
                target.getY() + rnd.nextDouble() * 2.0 - 0.5,
                target.getZ() + Math.sin(angle) * dist);
    }

    /** A point directly behind where the player is facing. */
    private static Vec3 behind(ServerPlayer target, double minDist, double maxDist) {
        RandomSource rnd = target.getRandom();
        Vec3 look = target.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        if (back.lengthSqr() < 1.0E-4) {
            return around(target, minDist, maxDist);
        }
        double dist = minDist + rnd.nextDouble() * (maxDist - minDist);
        return target.position().add(back.normalize().scale(dist));
    }

    /**
     * A point at the given bearing/distance, snapped DOWN onto real ground — so footsteps and landings come
     * from a surface something could actually be standing on, not from mid-air.
     */
    private static Vec3 groundedAt(ServerLevel level, ServerPlayer target, double angle, double dist) {
        double x = target.getX() + Math.cos(angle) * dist;
        double z = target.getZ() + Math.sin(angle) * dist;
        BlockPos probe = BlockPos.containing(x, target.getY() + 2, z);
        for (int i = 0; i < 10; i++) {
            BlockPos below = probe.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(x, probe.getY(), z);
            }
            probe = below;
        }
        return null; // nothing solid underfoot within range — this spot wouldn't be believable
    }

    /** The step sound of whatever is actually underfoot at that spot. */
    private static Holder<SoundEvent> stepSoundAt(ServerLevel level, Vec3 pos) {
        BlockPos below = BlockPos.containing(pos).below();
        return holder(level.getBlockState(below).getSoundType().getStepSound());
    }

    /** SoundEvents is a mix of raw SoundEvent and Holder fields; the packet needs a Holder either way. */
    private static Holder<SoundEvent> holder(SoundEvent event) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event);
    }
}
