package com.oliver.witchmod.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.BodyguardLines;

/**
 * the bodyguard — an armoured sunglasses-skeleton bound to the blessed player (the anchor). escalates
 * warning → aggression → attacking on anyone who crowds/hits the anchor, narrating as it goes; never
 * retaliates against the anchor. a {@link PathfinderMob}, not an {@code Enemy}, so golems ignore it and it
 * doesn't burn; all movement is manual so nothing vanilla can make it target the anchor by accident.
 */
public final class BodyguardEntity extends PathfinderMob {
    public enum State { FOLLOWING, WARNING, AGGRESSION, ATTACKING }

    private static final double REACH = 2.5;

    /**
     * anchor UUID -> the ONE bodyguard that currently belongs to them. A freshly-summoned bodyguard
     * {@link #claim}s its anchor here; any older one, on its next tick, sees the entry no longer points at it
     * and discards itself. That self-healing is what actually kills the duplication across death/relog — the
     * newest summon always wins and leftovers evaporate, no matter how they came to coexist.
     */
    private static final Map<UUID, UUID> CANONICAL = new HashMap<>();

    public static void claim(UUID anchorId, UUID bodyguardId) {
        CANONICAL.put(anchorId, bodyguardId);
    }

    public static void release(UUID anchorId) {
        CANONICAL.remove(anchorId);
    }

    @Nullable
    public static UUID canonicalFor(UUID anchorId) {
        return CANONICAL.get(anchorId);
    }

    @Nullable
    private UUID anchorId;
    @Nullable
    private UUID focusId;
    private State state = State.FOLLOWING;

    private final List<String> pendingLines = new ArrayList<>();
    private int lineGap;
    /** ticks until the next dialogue TREE may start. Only ever set when a tree starts (or an ATTACK forces
     *  a line) — never on a plain state change, which is what stopped the constant chatter. */
    private int speakCooldown;
    private int warningHitCooldown;
    private int attackCooldown;
    /** how long the current intruder has been crowding in AGGRESSION — past patience, steel comes out. */
    private int aggressionTicks;
    /** verbal warnings actually delivered to the current focus — patience won't draw steel below the minimum. */
    private int warningsSpoken;
    @Nullable
    private UUID warnedFocus;
    /** true when the current focus is a genuine attacker (chase it on the longer threat leash), not an intruder. */
    private boolean focusIsThreat;
    private boolean configured;

    public BodyguardEntity(EntityType<? extends BodyguardEntity> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
        setCustomName(Component.literal("Bodyguard"));
        setCustomNameVisible(true);
        equipArmour();
    }

    /**
     * ⚠ TRANSIENT — never written to disk. This is what stops it duplicating: the entity keeps up with the
     * anchor by teleporting, so it only ever unloads when the anchor's chunks do (logout / death far away /
     * dimension change) — and because it isn't saved, it simply vanishes then, leaving no orphan for the
     * blessing to double up on when it re-summons. (Earlier it was persistence-required and saved, so a
     * relog/death left a copy behind AND a fresh one got summoned.)
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /**
     * ⚠ Built at MOD-LOAD time, before configs exist — so these are plain literals (the config defaults). The
     * config-tunable values are re-applied from {@link Config} at spawn time in {@link #applyConfig}, once the
     * config is actually loaded (same reason the Tax Man's attributes are literals).
     */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** push the config-tunable attribute values onto this instance (config IS loaded by spawn time). */
    private void applyConfig() {
        setBase(Attributes.MAX_HEALTH, Config.BODYGUARD_HEALTH.get());
        setBase(Attributes.ATTACK_DAMAGE, Config.BODYGUARD_DAMAGE.get());
        setBase(Attributes.ARMOR, Config.BODYGUARD_ARMOR.get());
        setBase(Attributes.MOVEMENT_SPEED, Config.BODYGUARD_SPEED.get());
        setHealth(getMaxHealth());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         double value) {
        var instance = getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this)); // don't drown; everything else is manual
    }

    private void equipArmour() {
        // black-dyed leather — a bouncer's blacks, not plate. Toughness comes from the ARMOR attribute, so
        // the leather is purely the look. Bare head so the shades read.
        setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.CHEST, dyedBlack(Items.LEATHER_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, dyedBlack(Items.LEATHER_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, dyedBlack(Items.LEATHER_BOOTS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F); // the gear is cosmetic — it never drops
        }
    }

    /** vanilla's black-dye colour (0x1D1D21) — near-black, so the leather still catches a little light. */
    private static final int BLACK_DYE = 0x1D1D21;

    private static ItemStack dyedBlack(net.minecraft.world.item.Item leather) {
        ItemStack stack = new ItemStack(leather);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(BLACK_DYE, false));
        return stack;
    }

    /** steel comes out when it means business — the visible "this is now a real threat". */
    private void drawWeapon() {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }

    private void sheatheWeapon() {
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    public void setAnchor(ServerPlayer anchor) {
        this.anchorId = anchor.getUUID();
    }

    @Nullable
    public UUID getAnchorId() {
        return anchorId;
    }

    public State getState() {
        return state;
    }

    // --- Never turn on the anchor -----------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean took = super.hurt(source, amount);
        if (took && level() instanceof ServerLevel && source.getEntity() instanceof LivingEntity attacker) {
            // someone hit the bodyguard — that's a straight ticket to ATTACKING (unless it was the anchor).
            escalateToAttacking(attacker, true);
        }
        return took;
    }

    /** an intruder who won't leave (patience path) — pursued only within the normal leash. */
    public void escalateToAttacking(LivingEntity attacker) {
        escalateToAttacking(attacker, false);
    }

    /**
     * Latch onto {@code attacker} and commit — called when the anchor or the bodyguard is struck by anything
     * (a player, a zombie, a golem). It'll never turn on the anchor or itself. A genuine ATTACKER (isThreat) is
     * chased on the longer threat leash, so aggro on "anything that attacks you" actually sticks rather than
     * standing down the moment they back off a step.
     */
    public void escalateToAttacking(LivingEntity attacker, boolean isThreat) {
        if (attacker == this || attacker.getUUID().equals(anchorId)) {
            return; // never the anchor, never itself
        }
        focusId = attacker.getUUID();
        focusIsThreat = isThreat;
        setState(State.ATTACKING);
    }

    // --- The brain --------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        // self-heal duplicates: if a newer bodyguard has claimed my anchor, I'm a leftover — vanish.
        if (anchorId != null) {
            UUID canonical = CANONICAL.get(anchorId);
            if (canonical != null && !canonical.equals(getUUID())) {
                discard();
                return;
            }
        }
        if (!configured) {
            configured = true;
            applyConfig();
        }
        if (warningHitCooldown > 0) {
            warningHitCooldown--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        ServerPlayer anchor = anchorId == null ? null : level.getServer().getPlayerList().getPlayer(anchorId);

        // bound to the anchor like a tamed wolf: if it's strayed (or the anchor pearled/flew off), blink back.
        if (anchor != null) {
            double tp = Config.BODYGUARD_TELEPORT_DISTANCE.get();
            if (distanceToSqr(anchor) > tp * tp) {
                teleportToAnchor(anchor);
            }
        }

        tickDialogue(anchor);

        if (state == State.ATTACKING) {
            tickAttacking(anchor);
            return;
        }

        LivingEntity intruder = nearestIntruder(level, anchor);
        if (intruder == null) {
            setState(State.FOLLOWING);
            followAnchor(anchor);
            return;
        }

        if (!intruder.getUUID().equals(focusId)) {
            aggressionTicks = 0; // a different intruder — start their patience clock fresh
        }
        focusId = intruder.getUUID();
        getLookControl().setLookAt(intruder, 30.0F, 30.0F);
        double aggro = Config.BODYGUARD_AGGRESSION_RADIUS.get();
        boolean tooClose = anchor != null && intruder.distanceToSqr(anchor) <= aggro * aggro;
        setState(tooClose ? State.AGGRESSION : State.WARNING);

        // interpose: walk toward the intruder and hold at arm's length.
        if (distanceToSqr(intruder) > REACH * REACH) {
            getNavigation().moveTo(intruder, 1.0);
        } else {
            getNavigation().stop();
            if (state == State.AGGRESSION) {
                maybeWarningHit(intruder);
            }
        }

        // patience runs out: someone who WON'T take the hint gets the sword drawn on them, no attack from them
        // required (needs BODYGUARD_WARNINGS_BEFORE_ATTACK spoken warnings first, so it never jumps to violence
        // silently). The clock builds while the SAME intruder is present at all — WARNING or AGGRESSION — so a
        // warning-shove bumping them out to WARNING range no longer resets it (which is why it used to only
        // attack while you actively kept crowding it). It resets only when they leave entirely or a new
        // intruder takes over (above / in setState → FOLLOWING).
        if (++aggressionTicks >= Config.BODYGUARD_PATIENCE.get()
                && warningsSpoken >= Config.BODYGUARD_WARNINGS_BEFORE_ATTACK.get()) {
            escalateToAttacking(intruder);
        }
    }

    private void tickAttacking(@Nullable ServerPlayer anchor) {
        LivingEntity target = resolveFocus();
        double leash = focusIsThreat ? Config.BODYGUARD_THREAT_LEASH_RANGE.get() : Config.BODYGUARD_LEASH_RANGE.get();
        boolean lost = target == null || !target.isAlive()
                || (anchor != null && target.distanceToSqr(anchor) > leash * leash);
        if (lost) {
            setState(State.FOLLOWING); // it guards a place, it doesn't chase forever
            followAnchor(anchor);
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distanceToSqr(target) > REACH * REACH) {
            getNavigation().moveTo(target, 1.15);
        } else if (attackCooldown <= 0) {
            doHurtTarget(target);
            swing(InteractionHand.MAIN_HAND);
            attackCooldown = 20;
        }
    }

    private void followAnchor(@Nullable ServerPlayer anchor) {
        if (anchor == null) {
            return;
        }
        double follow = Config.BODYGUARD_FOLLOW_DISTANCE.get();
        if (distanceToSqr(anchor) > follow * follow) {
            getNavigation().moveTo(anchor, 1.0);
        } else {
            getNavigation().stop();
            getLookControl().setLookAt(anchor, 20.0F, 20.0F);
        }
    }

    private void maybeWarningHit(LivingEntity intruder) {
        if (warningHitCooldown > 0) {
            return;
        }
        swing(InteractionHand.MAIN_HAND);
        warningHitCooldown = Config.BODYGUARD_WARNING_HIT_INTERVAL.get();
        if (intruder instanceof Villager) {
            // villagers get SHOVED, not hurt — a warning hit would just sic the iron golem on us and slowly
            // murder the village, which isn't the bit. The push still makes the point.
            intruder.knockback(0.5, getX() - intruder.getX(), getZ() - intruder.getZ());
            intruder.hurtMarked = true;
        } else {
            intruder.hurt(damageSources().mobAttack(this), (float) (double) Config.BODYGUARD_WARNING_HIT_DAMAGE.get());
        }
    }

    /**
     * The nearest thing crowding the anchor that the bodyguard should confront — a non-anchor player (not a
     * spectator/creative one) or a villager. Villagers count "like players" so it isn't dead weight when no
     * second player is around; actual THREATS (anything that attacks you) are handled separately, by escalation.
     */
    @Nullable
    private LivingEntity nearestIntruder(ServerLevel level, @Nullable ServerPlayer anchor) {
        if (anchor == null) {
            return null;
        }
        double radius = Config.BODYGUARD_WARNING_RADIUS.get();
        AABB area = anchor.getBoundingBox().inflate(radius);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> isIntruder(e, anchor))) {
            double dist = entity.distanceToSqr(anchor);
            if (dist <= radius * radius && dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private boolean isIntruder(LivingEntity entity, ServerPlayer anchor) {
        if (entity == this || entity == anchor || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof ServerPlayer player) {
            return !player.isSpectator() && !player.isCreative();
        }
        return entity instanceof Villager;
    }

    @Nullable
    private LivingEntity resolveFocus() {
        if (focusId == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(focusId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** blink to a safe spot at the anchor's side (wolf-style), trying a few nearby cells before giving up. */
    private void teleportToAnchor(ServerPlayer anchor) {
        BlockPos base = anchor.blockPosition();
        for (int i = 0; i < 10; i++) {
            int x = base.getX() + Mth.nextInt(getRandom(), -2, 2);
            int y = base.getY() + Mth.nextInt(getRandom(), -1, 1);
            int z = base.getZ() + Mth.nextInt(getRandom(), -2, 2);
            if (canStandAt(x, y, z)) {
                teleportTo(x + 0.5, y, z + 0.5);
                getNavigation().stop();
                return;
            }
        }
        teleportTo(anchor.getX(), anchor.getY(), anchor.getZ()); // fallback: right on top of them
        getNavigation().stop();
    }

    private boolean canStandAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState below = level().getBlockState(pos.below());
        if (below.isAir() || !below.getFluidState().isEmpty()) {
            return false; // need real ground, not air or liquid
        }
        AABB box = getDimensions(getPose()).makeBoundingBox(x + 0.5, y, z + 0.5);
        return level().noCollision(this, box);
    }

    // --- Speaking ---------------------------------------------------------------------------------------

    private void setState(State next) {
        if (state == next) {
            return;
        }
        State prev = state;
        state = next;
        if (next == State.FOLLOWING) {
            aggressionTicks = 0; // only a real loss of the intruder resets patience — NOT a WARNING⇄AGGRESSION flip
        }
        // ⚠ Deliberately does NOT touch speakCooldown for WARNING/AGGRESSION/FOLLOWING. Those states flicker
        // as an intruder hovers near a radius boundary, and resetting the cooldown on each flip is exactly
        // what made it talk over itself nonstop. Timing is owned solely by tickDialogue now.
        if (next == State.ATTACKING) {
            drawWeapon();
            pendingLines.clear();
            speakCooldown = 0; // ONE prompt reaction to the escalation (setState can't re-enter ATTACKING)
            lineGap = 0;
            level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 0.8F, 0.9F);
        } else if (prev == State.ATTACKING) {
            sheatheWeapon();
        }
    }

    private void tickDialogue(@Nullable ServerPlayer anchor) {
        if (lineGap > 0) {
            lineGap--;
        }
        // still delivering the current tree, one line per gap.
        if (!pendingLines.isEmpty()) {
            if (lineGap <= 0) {
                say(anchor, pendingLines.remove(0));
                lineGap = Config.BODYGUARD_DIALOGUE_LINE_GAP.get();
            }
            return;
        }
        // between trees: a single global cooldown, immune to state flicker (only reset when a tree STARTS).
        if (speakCooldown > 0) {
            speakCooldown--;
            return;
        }
        String key = switch (state) {
            case WARNING -> "warning";
            case AGGRESSION -> "aggression";
            case ATTACKING -> "attacking";
            case FOLLOWING -> "ambient";
        };
        int base = Config.BODYGUARD_DIALOGUE_COOLDOWN.get();
        speakCooldown = state == State.FOLLOWING ? base * 3 : base; // idle chatter is sparse either way
        List<String> tree = BodyguardLines.pickTree(key, getRandom());
        if (!tree.isEmpty()) {
            pendingLines.addAll(tree);
        }
    }

    /** speak a companionship-banter line (already filled), as {@code <Bodyguard> line}, to nearby players. */
    public void speakBanter(String line) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Component message = Component.literal("<Bodyguard> " + line);
        double radius = Config.BODYGUARD_CHAT_RADIUS.get();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= radius * radius) {
                player.sendSystemMessage(message);
            }
        }
    }

    private void say(@Nullable ServerPlayer anchor, String rawLine) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity focus = resolveFocus();
        String name = focus != null ? focus.getName().getString()
                : (anchor != null ? anchor.getName().getString() : "you");

        // count warnings actually delivered to THIS focus, so patience can require a couple before it attacks.
        if (state == State.WARNING || state == State.AGGRESSION) {
            if (!Objects.equals(focusId, warnedFocus)) {
                warnedFocus = focusId;
                warningsSpoken = 0;
            }
            warningsSpoken++;
        }

        Component message = Component.literal("<Bodyguard> " + rawLine.replace("{player}", name));

        double radius = Config.BODYGUARD_CHAT_RADIUS.get();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= radius * radius) {
                player.sendSystemMessage(message);
            }
        }
    }

    // --- Housekeeping -----------------------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("BodyguardState", state.name());
        if (anchorId != null) {
            tag.putUUID("Anchor", anchorId);
        }
        if (focusId != null) {
            tag.putUUID("Focus", focusId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String saved = tag.getString("BodyguardState");
        state = saved.isEmpty() ? State.FOLLOWING : State.valueOf(saved);
        anchorId = tag.hasUUID("Anchor") ? tag.getUUID("Anchor") : null;
        focusId = tag.hasUUID("Focus") ? tag.getUUID("Focus") : null;
    }
}
