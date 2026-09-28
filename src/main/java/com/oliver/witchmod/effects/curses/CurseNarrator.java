package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.NarratorLines;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * A smug narrator reads out your life via your client's text-to-speech, commenting on the things you do — and
 * occasionally leaking a genuinely useful hint. It uses REAL TTS (Minecraft's bundled
 * {@code com.mojang.text2speech}) because a robotic voice reading you out is exactly the point.
 *
 * <p>Lines come from the writable {@code data/witchmod/text/narrator.json} (keyed by event category, with a
 * {@code {player}} placeholder). The line is spoken on the victim's client and on nearby players' clients
 * (within {@code narratorHearRadius}) — each flat/non-positional, since TTS isn't a world sound — AND shown as
 * a subtitle on the action bar (so non-Windows players, whose TTS may not work, can still read it).
 *
 * <p>Two layers of anti-spam:
 * <ul>
 *   <li>a per-victim {@code narratorMinGapTicks} floor so lines can NEVER overlap; and</li>
 *   <li>for a SUSTAINED activity (the same category firing over and over, e.g. swimming) the required gap
 *       ESCALATES per repeat, and each individual line may only be spoken {@code narratorVariantCap} times
 *       before it's retired — once every variant is exhausted that category goes quiet until a DIFFERENT
 *       reaction happens (which resets the run).</li>
 * </ul>
 * The one exception is the long tab-out spam ({@link #constant}), which ignores the escalation on purpose.
 *
 * <p>Event categories are fired from {@code CurseEventHandler} (and a few other systems via {@link #event});
 * ambient (idle) lines fire on a schedule here.
 */
public final class CurseNarrator extends Effect {
    /** per-victim narration state (schedule + anti-spam run tracking). */
    private static final class State {
        long nextAmbientTick;
        long lastSpokeTick = Long.MIN_VALUE / 2;
        long lineEndTick;                                  // game-tick the last line is estimated to FINISH speaking
        int nextGap;                                       // rolled 0..postGapMax ticks to wait after it finishes
        String lastCategory = "";
        int repeatCount;                                   // consecutive same-category lines this run
        boolean tabbedOut;                                 // window is out; let the constant spam dominate
        final Map<String, Integer> variantCounts = new HashMap<>(); // per specific line, this run
    }

    private static final Map<UUID, State> STATE = new HashMap<>();
    /** per-victim idle tracking: {tickTheyLastMoved, blockX, blockZ, idlingFlag}. */
    private static final Map<UUID, long[]> IDLE = new HashMap<>();

    public CurseNarrator() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.WRITTEN_BOOK);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.NARRATOR_ACTIVE, 1); // lets the client report pause / tab-out
    }

    private static State state(ServerPlayer target, long now) {
        return STATE.computeIfAbsent(target.getUUID(), k -> {
            State st = new State();
            st.nextAmbientTick = now + ambientGap(target.getRandom());
            return st;
        });
    }

    private static long ambientGap(RandomSource rng) {
        int min = Config.NARRATOR_AMBIENT_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.NARRATOR_AMBIENT_MAX_TICKS.get());
        return min + rng.nextInt(max - min);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (target.getData(WitchModAttachments.NARRATOR_ACTIVE) < 0) {
            target.setData(WitchModAttachments.NARRATOR_ACTIVE, 1); // self-heal after a relog
        }
        long now = level.getGameTime();
        State s = state(target, now);

        // situational commentary — checked on a slow cadence; the anti-spam keeps it from overlapping and from
        // firing every tick a condition holds. One situational line pre-empts an ambient one. While the victim
        // is TABBED OUT it's suppressed entirely, so the constant tabbed-out-too-long spam is the dominant voice
        // (ambient still trickles through on its own schedule).
        if (now % 40 == 0 && !s.tabbedOut) {
            String cat = situational(target, level, now);
            if (cat != null) {
                maybe(target, cat);
                return;
            }
        }
        if (now >= s.nextAmbientTick) {
            s.nextAmbientTick = now + ambientGap(target.getRandom());
            narrate(target, "ambient", false);
        }
    }

    /** the highest-priority situational category that currently applies to {@code target}, or null. */
    @Nullable
    private static String situational(ServerPlayer target, ServerLevel level, long now) {
        // A creeper skulking BEHIND you (in range, and roughly behind your look).
        Vec3 look = target.getViewVector(1.0F);
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, target.getBoundingBox().inflate(8.0), c -> c.isAlive())) {
            Vec3 toCreeper = creeper.position().subtract(target.position());
            if (toCreeper.horizontalDistanceSqr() > 1.0 && look.dot(toCreeper.normalize()) < -0.25) {
                return "creeper_behind";
            }
        }
        // in lava or ON FIRE — the urgent one.
        if (target.isInLava() || target.isOnFire()) {
            return "lava_fire";
        }
        // falling a good way.
        if (!target.onGround() && target.fallDistance > 3.0F && target.getDeltaMovement().y < -0.35) {
            return "falling";
        }
        // standing on a pressure plate / tripwire — someone's rigged a trap.
        BlockState feet = level.getBlockState(target.blockPosition());
        if (feet.getBlock() instanceof BasePressurePlateBlock
                || feet.getBlock() instanceof TripWireBlock
                || feet.getBlock() instanceof TripWireHookBlock) {
            return "pressure_plate";
        }
        // swimming (the front-crawl pose, or off the floor in water).
        if (target.isSwimming() || (target.isInWater() && !target.onGround())) {
            return "swimming";
        }
        // crouched.
        if (target.isShiftKeyDown()) {
            return "crouched";
        }
        // narrated-trumpet synergy: the fanfare scoring your every step is too good to ignore (crouch silences
        // the trumpet, so it's checked after crouched).
        if (com.oliver.witchmod.synergy.Synergies.NARRATED_TRUMPET.activeFor(target)) {
            if (target.isSprinting()) {
                return "trumpet_sprint";
            }
            if (target.walkAnimation.speed() > (float) (double) Config.TRUMPET_WALK_THRESHOLD.get()) {
                return "trumpet_walk";
            }
        }
        // sprinting.
        if (target.isSprinting()) {
            return "sprinting";
        }
        // A nearby player crouching.
        for (Player other : level.getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(10.0))) {
            if (other != target && other.isShiftKeyDown()) {
                return "player_crouched_near";
            }
        }
        // A tool or piece of armour nearly worn out.
        if (hasLowDurabilityGear(target)) {
            return "low_durability";
        }
        // idle tracking — detect "returned" (moved off after idling) now, defer "idling" until after the
        // surroundings tier so a persistent condition can't starve it.
        long[] idle = IDLE.get(target.getUUID());
        int bx = target.getBlockX();
        int bz = target.getBlockZ();
        boolean idling = false;
        if (idle == null || idle[1] != bx || idle[2] != bz) {
            boolean wasIdling = idle != null && idle[3] == 1;
            IDLE.put(target.getUUID(), new long[]{now, bx, bz, 0});
            if (wasIdling) {
                return "returned";
            }
        } else if (now - idle[0] >= 200) { // ~10s stationary
            idle[3] = 1;
            idling = true;
        }
        // surroundings ambience — commentary on the WORLD around the victim rather than their own action. Chance-
        // gated (and randomly chosen among whatever applies) so persistent conditions sprinkle in rather than
        // dominating, and never starve the idle/ambient lines.
        String env = surroundings(target, level);
        if (env != null && target.getRandom().nextInt(100) < Config.NARRATOR_ENV_CHANCE.get()) {
            return env;
        }
        return idling ? "idling" : null;
    }

    /** A random applicable SURROUNDINGS category (world/environment conditions), or null if none apply. */
    @Nullable
    private static String surroundings(ServerPlayer target, ServerLevel level) {
        var pos = target.blockPosition();
        List<String> env = new ArrayList<>();
        if (level.isThundering() && level.canSeeSky(pos)) {
            env.add("thundering");
        } else if (level.isRaining() && level.isRainingAt(pos)) {
            env.add("raining");
        }
        // night, if under open sky.
        long tod = level.getDayTime() % 24000L;
        if (tod >= 13000L && tod <= 23000L && level.canSeeSky(pos)) {
            env.add("night");
        }
        // way up high, or deep underground in the dark.
        if (target.getY() >= 150.0) {
            env.add("high_up");
        } else if (target.getY() < 30.0 && !level.canSeeSky(pos)) {
            env.add("deep_underground");
        }
        // genuinely dark here (spooky) — the EFFECTIVE light level (block + any propagated daylight), so
        // standing in daylight (even under an overhang lit by ambient sky light) never counts as dark.
        if (level.getMaxLocalRawBrightness(pos) <= Config.NARRATOR_DARK_LIGHT_LEVEL.get()) {
            env.add("dark");
        }
        // A hostile lurking nearby (any monster, not just the creeper-behind case).
        if (!level.getEntitiesOfClass(Monster.class, target.getBoundingBox().inflate(12.0),
                m -> m.isAlive() && !(m instanceof Creeper)).isEmpty()) {
            env.add("near_hostiles");
        }
        // A villager close by.
        if (!level.getEntitiesOfClass(Villager.class, target.getBoundingBox().inflate(10.0)).isEmpty()) {
            env.add("near_villager");
        }
        // surrounded by a few animals.
        if (level.getEntitiesOfClass(Animal.class, target.getBoundingBox().inflate(8.0)).size() >= 3) {
            env.add("near_animals");
        }
        // running low on food.
        if (target.getFoodData().getFoodLevel() <= 6) {
            env.add("low_hunger");
        }
        if (env.isEmpty()) {
            return null;
        }
        return env.get(target.getRandom().nextInt(env.size()));
    }

    /** true if any held/worn damageable item is at 85%+ wear (nearly broken). */
    private static boolean hasLowDurabilityGear(ServerPlayer target) {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            net.minecraft.world.item.ItemStack st = target.getItemBySlot(slot);
            if (st.isDamageableItem() && st.getMaxDamage() > 0
                    && (double) st.getDamageValue() / st.getMaxDamage() >= 0.85) {
                return true;
            }
        }
        return false;
    }

    /** narrate {@code category} on {@code target} IF the Narrator is active AND the event-chance lands. */
    public static void event(ServerPlayer target, String category) {
        if (EffectManager.isActive(target, Curses.NARRATOR)) {
            maybe(target, category);
        }
    }

    /** roll the event chance, then narrate {@code category} if it lands. Called from the event hooks. */
    public static void maybe(ServerPlayer target, String category) {
        if (target.getRandom().nextInt(100) < Config.NARRATOR_EVENT_CHANCE.get()) {
            narrate(target, category, false);
        }
    }

    /** narrate CONSTANTLY — bypasses the anti-spam escalation + variant cap (only the no-overlap floor stands). */
    public static void constant(ServerPlayer target, String category) {
        narrate(target, category, true);
    }

    /** always narrate {@code category} (no event-chance roll), subject to the normal anti-spam. */
    public static void narrate(ServerPlayer target, String category) {
        narrate(target, category, false);
    }

    /**
     * speak a line for {@code category}. Lines never overlap: the next may only start a random 0..{@code
     * narratorPostLineGapMaxTicks} AFTER the previous is estimated to FINISH. Unless {@code bypassAntispam}, a
     * sustained same-category run also escalates that gap and caps each variant at {@code narratorVariantCap}.
     */
    public static void narrate(ServerPlayer target, String category, boolean bypassAntispam) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        State s = state(target, now);
        // track tab-out state so onTick lets the constant spam dominate over idle/situational commentary.
        if (category.equals("tabbed_out") || category.equals("tabbed_out_long")) {
            s.tabbedOut = true;
        } else if (category.equals("returned")) {
            s.tabbedOut = false;
        }
        boolean sameCat = category.equals(s.lastCategory);

        // the next line may start once the previous has finished (lineEndTick) plus the rolled short gap.
        long required = s.lineEndTick + s.nextGap;
        if (!bypassAntispam && sameCat) {
            required += Math.min((long) s.repeatCount * Config.NARRATOR_REPEAT_GAP_TICKS.get(),
                    Config.NARRATOR_REPEAT_GAP_MAX_TICKS.get());
        }
        if (now < required) {
            return;
        }

        String line;
        if (bypassAntispam) {
            line = NarratorLines.pick(category, target.getRandom());
        } else {
            if (!sameCat) { // a different reaction resets the run (escalation + variant caps)
                s.repeatCount = 0;
                s.variantCounts.clear();
            }
            line = pickUncapped(category, s, target.getRandom());
        }
        if (line == null || line.isEmpty()) {
            return; // no line, or every variant exhausted this run — go quiet until the category changes
        }
        if (bypassAntispam) {
            s.repeatCount = 0;
            s.variantCounts.clear();
        } else {
            s.variantCounts.merge(line, 1, Integer::sum);
            s.repeatCount++;
        }
        s.lastCategory = category;
        s.lastSpokeTick = now;
        send(level, target, line, now, s);
    }

    /** pick a line for {@code category} whose per-run count is still under the cap, or null if all are spent. */
    @Nullable
    private static String pickUncapped(String category, State s, RandomSource rng) {
        List<String> list = NarratorLines.all(category);
        if (list.isEmpty()) {
            return null;
        }
        int cap = Config.NARRATOR_VARIANT_CAP.get();
        List<String> available = new ArrayList<>();
        for (String line : list) {
            if (s.variantCounts.getOrDefault(line, 0) < cap) {
                available.add(line);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        return available.get(rng.nextInt(available.size()));
    }

    /** substitute, size the subtitle, schedule the next gap, and dispatch the line to the victim + nearby players. */
    private static void send(ServerLevel level, ServerPlayer target, String line, long now, State s) {
        String name = target.getName().getString();
        String text = line.replace("{player}", name);
        RandomSource rng = target.getRandom();
        // A rare OS jab that REPLACES the subtitle on non-Windows clients (the client decides by its own OS).
        String callout = "";
        if (rng.nextInt(100) < Config.NARRATOR_CALLOUT_CHANCE.get()) {
            String c = NarratorLines.pick("linux_mac_callout", rng);
            if (c != null) {
                callout = c.replace("{player}", name);
            }
        }
        int subtitleTicks = Mth.clamp(
                (int) (text.length() * Config.NARRATOR_SUBTITLE_TICKS_PER_CHAR.get()) + 20, 40, 240);
        // the line is estimated to finish at now+subtitleTicks; the next may start a random 0..max ticks after.
        s.lineEndTick = now + subtitleTicks;
        s.nextGap = rng.nextInt(Config.NARRATOR_POST_LINE_GAP_MAX_TICKS.get() + 1);
        double radius = Config.NARRATOR_HEAR_RADIUS.get();
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p == target || p.distanceToSqr(target) <= r2) {
                PacketDistributor.sendToPlayer(p, new WitchModNetwork.NarratorSpeakPayload(text, callout, subtitleTicks));
            }
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        STATE.remove(target.getUUID());
        IDLE.remove(target.getUUID());
        target.setData(WitchModAttachments.NARRATOR_ACTIVE, -1);
    }

    @Override
    public @Nullable String debugForce(ServerPlayer target, @Nullable String arg) {
        String category = (arg == null || arg.isEmpty()) ? "ambient" : arg;
        // bypass the cooldown for a forced test.
        State s = state(target, target.level() instanceof ServerLevel sl ? sl.getGameTime() : 0);
        s.lastSpokeTick = Long.MIN_VALUE / 2;
        s.lineEndTick = 0;
        s.nextGap = 0;
        s.lastCategory = "";
        narrate(target, category, false);
        return "Narrator says something (" + category + ").";
    }

    @Override
    public java.util.List<String> debugArgs() {
        return com.oliver.witchmod.data.NarratorLines.categories().stream().sorted().toList();
    }
}
