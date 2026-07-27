package com.oliver.witchmod.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.TaxBank;
import com.oliver.witchmod.data.TaxManLines;
import com.oliver.witchmod.data.TaxValues;
import com.oliver.witchmod.data.WitchModTags;

/**
 * The Tax Man (master-spec Taxes). An invulnerable humanoid who turns up when a cursed player has been
 * careless with their valuables, then <b>walks the property</b> — going chest to chest, opening each one,
 * rifling through it, and moving on — until he's taken what he's owed.
 *
 * <p><b>He physically visits things rather than vacuuming them from a distance</b>, which is the whole
 * difference between reading as a person and reading as a script: he paths to each container, the lid
 * actually opens, he pauses over it, and he watches the victim whenever he isn't busy. "Immovable" means he
 * cannot be pushed, knocked back or killed — not that he stands still.
 *
 * <p><b>Search order is fixed and deliberate: chests → floor items → your own inventory.</b> That ordering
 * IS the counterplay. Storage is hit first, so keeping valuables in a chest where you live is the worst
 * option; your person is raided last, so travelling light genuinely works. Nearby ender chests are included,
 * because otherwise they'd be a free exemption.
 *
 * <p><b>He takes by VALUE, not by item count</b> ({@link TaxValues}), so a haul cap means the same thing
 * whether he's going through copper or netherite. The cap is deliberately small — an irritation, not a
 * robbery.
 *
 * <p><b>⚠ If the {@link TaxBank} is full he stops taking rather than discarding.</b> Items are never voided;
 * see the note on {@code TaxBank.isFull()}.
 */
public final class TaxManEntity extends Mob {
    private enum Phase { ARRIVING, TRAVELLING, RIFLING, LEAVING }

    private static final double REACH = 2.2;

    private Phase phase = Phase.ARRIVING;
    private int phaseTicks;
    private int takenValue;
    private int emptyHandedSweeps;
    private boolean placedEnderChest;
    @Nullable
    private BlockPos enderChestPos;
    @Nullable
    private BlockPos openContainer;   // the one whose lid we've animated open
    @Nullable
    private BlockPos targetContainer;
    @Nullable
    private ItemEntity targetItem;
    private boolean targetingVictim;
    private boolean enderChestTarget; // the current container target is his placed ender chest (rifles YOUR ender inv)
    @Nullable
    private UUID victimId;
    private final List<BlockPos> searched = new ArrayList<>();

    // --- Delivery mode (the Tax Man BLESSING) — he hands the bank back instead of confiscating ----------
    private boolean delivering;
    private boolean paidOut;
    private int deliveryTicks;
    private int leaveTimer;

    public TaxManEntity(EntityType<? extends TaxManEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
        setCustomNameVisible(true);
        setCustomName(Component.literal("Tax Man"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)      // an unhurried but purposeful walk
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void assignVictim(ServerPlayer victim) {
        this.victimId = victim.getUUID();
    }

    /** Put him in DELIVERY mode: he'll walk up to {@code recipient}, hand over the bank (or a gift), and go. */
    public void assignDelivery(ServerPlayer recipient) {
        this.victimId = recipient.getUUID();
        this.delivering = true;
    }

    /** True once he's actually handed the goods over (the blessing waits for him to leave, then ends). */
    public boolean hasPaidOut() {
        return paidOut;
    }

    // --- Unkillable and unmovable, but not motionless ---------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // you cannot fight the taxman
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
        // he does not get shoved out of the way
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /**
     * A DELIVERY Tax Man (the blessing) is a one-off event, so it isn't written to disk — that way a relog or
     * restart mid-hand-off leaves no orphan and the blessing simply re-sends him. A CONFISCATING Tax Man (the
     * curse) still persists, because his visit needs to survive a reload.
     */
    @Override
    public boolean shouldBeSaved() {
        return !delivering && super.shouldBeSaved();
    }

    // --- The visit --------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        phaseTicks++;
        ServerPlayer victim = victimId == null ? null
                : serverLevel.getServer().getPlayerList().getPlayer(victimId);

        if (delivering) {
            tickDelivery(serverLevel, victim);
            return;
        }

        // He keeps an eye on you whenever he isn't reading a chest — being watched by a bureaucrat is most
        // of the effect.
        if (victim != null && phase != Phase.RIFLING) {
            getLookControl().setLookAt(victim, 30.0F, 30.0F);
        }

        switch (phase) {
            case ARRIVING -> tickArriving(serverLevel, victim);
            case TRAVELLING -> tickTravelling(serverLevel, victim);
            case RIFLING -> tickRifling(serverLevel, victim);
            case LEAVING -> {
                if (phaseTicks >= Config.TAXES_LEAVE_TICKS.get()) {
                    depart(serverLevel);
                }
            }
        }
    }

    // --- Delivery (the blessing) ------------------------------------------------------------------------

    /** Walk up to the recipient, drop the goods in front of them, linger a beat, then leave for good. */
    private void tickDelivery(ServerLevel level, @Nullable ServerPlayer recipient) {
        if (recipient == null) {
            depart(level);
            return;
        }
        getLookControl().setLookAt(recipient, 30.0F, 30.0F);

        if (!paidOut) {
            deliveryTicks++;
            // Walk in, but don't path forever if something's in the way — pay out where we stand after a bit.
            if (distanceToSqr(recipient) > REACH * REACH && deliveryTicks < 300) {
                getNavigation().moveTo(recipient, 1.0);
                return;
            }
            payOut(level, recipient);
            leaveTimer = 40; // stand there a moment so it reads as a hand-off, not a teleport-drop
            return;
        }

        getNavigation().stop();
        if (--leaveTimer <= 0) {
            depart(level);
        }
    }

    /** Empty the tax bank onto the floor in front of the recipient — or, if it's empty, a random gift. */
    private void payOut(ServerLevel level, ServerPlayer recipient) {
        paidOut = true;
        say(recipient, "refund");

        List<ItemStack> payout = TaxBank.get(level.getServer()).withdrawAll();
        if (payout.isEmpty()) {
            payout = rollGift(getRandom());
        }
        for (ItemStack stack : payout) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.5, getZ(), stack.copy());
            drop.setDeltaMovement(0.0, 0.12, 0.0);
            drop.setNoPickUpDelay();
            level.addFreshEntity(drop);
        }
        swing(InteractionHand.MAIN_HAND);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.4F);
    }

    /** The consolation gift when there's nothing banked: uniform emeralds & gold, diamonds biased low. */
    private static List<ItemStack> rollGift(net.minecraft.util.RandomSource random) {
        List<ItemStack> gift = new ArrayList<>();
        gift.add(new ItemStack(Items.EMERALD, 1 + random.nextInt(Config.TAXMAN_GIFT_EMERALDS_MAX.get())));
        gift.add(new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(Config.TAXMAN_GIFT_GOLD_MAX.get())));
        int diamondMax = Config.TAXMAN_GIFT_DIAMONDS_MAX.get();
        int diamonds = Math.min(random.nextInt(diamondMax + 1), random.nextInt(diamondMax + 1)); // biased to 0
        if (diamonds > 0) {
            gift.add(new ItemStack(Items.DIAMOND, diamonds));
        }
        return gift;
    }

    private void tickArriving(ServerLevel level, @Nullable ServerPlayer victim) {
        if (phaseTicks == 1) {
            say(victim, "arrive");
            level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR,
                    SoundSource.NEUTRAL, 0.8F, 1.2F);
        }
        // Walk up to the victim first, so the visit begins with him approaching YOU.
        if (victim != null && distanceToSqr(victim) > 9.0) {
            getNavigation().moveTo(victim, 1.0);
        }
        if (phaseTicks >= Config.TAXES_ARRIVE_TICKS.get()) {
            say(victim, "searching");
            chooseNextTarget(level, victim);
        }
    }

    /** Walking to whatever he's decided to look at next. */
    private void tickTravelling(ServerLevel level, @Nullable ServerPlayer victim) {
        if (takenValue >= Config.TAXES_HAUL_CAP.get()) {
            say(victim, "satisfied");
            enter(Phase.LEAVING);
            return;
        }

        if (targetContainer != null) {
            if (within(targetContainer)) {
                openLid(level, targetContainer);
                enter(Phase.RIFLING);
                return;
            }
            getNavigation().moveTo(targetContainer.getX() + 0.5, targetContainer.getY(),
                    targetContainer.getZ() + 0.5, 1.0);
            getLookControl().setLookAt(targetContainer.getX() + 0.5, targetContainer.getY() + 0.5,
                    targetContainer.getZ() + 0.5);
        } else if (targetItem != null && targetItem.isAlive()) {
            if (distanceToSqr(targetItem) <= REACH * REACH) {
                collectFloorItem(level, victim);
                return;
            }
            getNavigation().moveTo(targetItem, 1.0);
            getLookControl().setLookAt(targetItem, 30.0F, 30.0F);
        } else if (targetingVictim && victim != null) {
            if (distanceToSqr(victim) <= REACH * REACH) {
                frisk(level, victim);
                return;
            }
            getNavigation().moveTo(victim, 1.0);
        } else {
            chooseNextTarget(level, victim);
            return;
        }

        // Couldn't get there — give up on this one rather than pathing into a wall forever.
        if (phaseTicks > 60) {
            markSearched(targetContainer);
            chooseNextTarget(level, victim);
        }
    }

    /** Standing at an open container, taking a stack every so often. */
    private void tickRifling(ServerLevel level, @Nullable ServerPlayer victim) {
        if (targetContainer == null) {
            enter(Phase.TRAVELLING);
            return;
        }
        getLookControl().setLookAt(targetContainer.getX() + 0.5, targetContainer.getY() + 0.5,
                targetContainer.getZ() + 0.5);
        getNavigation().stop();

        if (phaseTicks % Config.TAXES_COLLECT_INTERVAL.get() != 0) {
            return;
        }
        // His placed ender chest shows YOUR ender inventory, not a block container of its own — so rifle that.
        boolean took;
        if (enderChestTarget) {
            took = victim != null && takeOneFrom(victim.getEnderChestInventory(), victim);
        } else {
            BlockEntity blockEntity = level.getBlockEntity(targetContainer);
            took = blockEntity instanceof Container container && takeOneFrom(container, victim);
        }
        if (took) {
            swing(InteractionHand.MAIN_HAND);
            level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.8F);
            return;
        }
        // Nothing (else) here — shut the lid and move on.
        closeLid(level);
        markSearched(targetContainer);
        chooseNextTarget(level, victim);
    }

    /**
     * Picks the next thing to walk to, in the fixed priority order: an unsearched container, then a floor
     * item, then the victim themselves.
     */
    private void chooseNextTarget(ServerLevel level, @Nullable ServerPlayer victim) {
        closeLid(level);
        targetContainer = null;
        targetItem = null;
        targetingVictim = false;
        enderChestTarget = false;

        targetContainer = nearestUnsearchedContainer(level);
        if (targetContainer != null) {
            enter(Phase.TRAVELLING);
            return;
        }
        targetItem = nearestValuableItem(level);
        if (targetItem != null) {
            enter(Phase.TRAVELLING);
            return;
        }
        if (victim != null && hasValuables(victim.getInventory())) {
            targetingVictim = true;
            enter(Phase.TRAVELLING);
            return;
        }

        // Nothing left worth looking at.
        emptyHandedSweeps++;
        if (emptyHandedSweeps == 1 && takenValue == 0) {
            say(victim, "empty_handed");
        }
        if (!placedEnderChest && emptyHandedSweeps >= Config.TAXES_ENDER_CHEST_AFTER.get()) {
            placeEnderChest(level, victim);
            enter(Phase.TRAVELLING);
            return;
        }
        if (emptyHandedSweeps >= Config.TAXES_GIVE_UP_SWEEPS.get() || takenValue > 0) {
            say(victim, takenValue > 0 ? "satisfied" : "gave_up");
            enter(Phase.LEAVING);
        } else {
            searched.clear(); // another lap, in case something changed
            enter(Phase.TRAVELLING);
        }
    }

    private void collectFloorItem(ServerLevel level, @Nullable ServerPlayer victim) {
        if (targetItem != null && targetItem.isAlive() && deposit(targetItem.getItem().copy(), victim)) {
            swing(InteractionHand.MAIN_HAND);
            targetItem.discard();
            level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.8F);
        }
        targetItem = null;
        chooseNextTarget(level, victim);
    }

    private void frisk(ServerLevel level, @Nullable ServerPlayer victim) {
        if (victim != null) {
            takeOneFrom(victim.getInventory(), victim);
            swing(InteractionHand.MAIN_HAND);
        }
        targetingVictim = false;
        chooseNextTarget(level, victim);
    }

    // --- Taking -----------------------------------------------------------------------------------------

    private boolean takeOneFrom(Container container, @Nullable ServerPlayer victim) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(WitchModTags.VALUABLES)) {
                continue;
            }
            if (!deposit(stack.copy(), victim)) {
                return false; // bank full — leave it exactly where it is
            }
            container.setItem(slot, ItemStack.EMPTY);
            container.setChanged();
            return true;
        }
        return false;
    }

    /** @return false if the bank refused it, in which case the caller must NOT remove the items */
    private boolean deposit(ItemStack stack, @Nullable ServerPlayer victim) {
        TaxBank bank = TaxBank.get(level().getServer());
        if (!bank.deposit(stack)) {
            say(victim, "bank_full");
            enter(Phase.LEAVING);
            return false;
        }
        takenValue += TaxValues.valueOf(stack);
        emptyHandedSweeps = 0;
        if (takenValue >= Config.TAXES_HAUL_CAP.get() / 2) {
            say(victim, "taking");
        }
        return true;
    }

    private static boolean hasValuables(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(WitchModTags.VALUABLES)) {
                return true;
            }
        }
        return false;
    }

    // --- Finding things ---------------------------------------------------------------------------------

    @Nullable
    private BlockPos nearestUnsearchedContainer(ServerLevel level) {
        int radius = Config.TAXES_SCAN_RADIUS.get();
        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius / 2, -radius),
                origin.offset(radius, radius / 2, radius))) {
            if (searched.contains(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof Container container) || !hasValuables(container)) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    @Nullable
    private ItemEntity nearestValuableItem(ServerLevel level) {
        AABB area = getBoundingBox().inflate(Config.TAXES_SCAN_RADIUS.get());
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!item.getItem().is(WitchModTags.VALUABLES)) {
                continue;
            }
            double distance = distanceToSqr(item);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    private boolean within(BlockPos pos) {
        return pos.distToCenterSqr(position()) <= REACH * REACH;
    }

    private void markSearched(@Nullable BlockPos pos) {
        if (pos != null && !searched.contains(pos)) {
            searched.add(pos.immutable());
        }
    }

    // --- Lids -------------------------------------------------------------------------------------------

    /**
     * Actually animates the chest. Vanilla drives the lid through a block event ({@code id 1}, viewer count),
     * which is what {@code ChestBlockEntity.triggerEvent} listens for — so this is the same mechanism a real
     * player opening it would use, not a fake.
     */
    private void openLid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.blockEvent(pos, state.getBlock(), 1, 1);
        level.playSound(null, pos, chestSound(state, true), SoundSource.BLOCKS, 0.6F, 0.95F);
        openContainer = pos.immutable();
    }

    private void closeLid(ServerLevel level) {
        if (openContainer == null) {
            return;
        }
        BlockState state = level.getBlockState(openContainer);
        level.blockEvent(openContainer, state.getBlock(), 1, 0);
        level.playSound(null, openContainer, chestSound(state, false), SoundSource.BLOCKS, 0.6F, 0.95F);
        openContainer = null;
    }

    private static net.minecraft.sounds.SoundEvent chestSound(BlockState state, boolean opening) {
        if (state.is(Blocks.ENDER_CHEST)) {
            return opening ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE;
        }
        if (state.is(Blocks.BARREL)) {
            return opening ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        }
        return opening ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
    }

    // --- Ender chest gambit ------------------------------------------------------------------------------

    private void placeEnderChest(ServerLevel level, @Nullable ServerPlayer victim) {
        placedEnderChest = true;
        say(victim, "ender_chest");

        BlockPos spot = findEnderChestSpot(level);
        if (spot != null) {
            level.setBlockAndUpdate(spot, Blocks.ENDER_CHEST.defaultBlockState());
            enderChestPos = spot;
            // Route it through the SAME walk -> open (real lid) -> rifle -> close flow as any other chest,
            // so it no longer teleport-grabs. tickRifling reads the victim's ender inventory for it.
            targetContainer = spot;
            enderChestTarget = true;
        } else if (victim != null) {
            // Nowhere to set it down (rare) — reach in directly rather than lose the gambit entirely.
            Container ender = victim.getEnderChestInventory();
            while (takeOneFrom(ender, victim)) {
                swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    /** A replaceable cell with solid ground under it, next to him, to set the ender chest down on. */
    @Nullable
    private BlockPos findEnderChestSpot(ServerLevel level) {
        BlockPos self = blockPosition();
        for (BlockPos pos : new BlockPos[]{self.relative(getDirection()),
                self.north(), self.south(), self.east(), self.west()}) {
            if (level.getBlockState(pos).canBeReplaced()
                    && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private void depart(ServerLevel level) {
        closeLid(level);
        if (enderChestPos != null && level.getBlockState(enderChestPos).is(Blocks.ENDER_CHEST)) {
            level.removeBlock(enderChestPos, false); // he takes it with him
        }
        level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.NEUTRAL, 0.8F, 1.0F);
        discard();
    }

    /** Ends the visit early — used when the curse is cured out from under him. */
    public void dismiss() {
        if (level() instanceof ServerLevel serverLevel) {
            depart(serverLevel);
        }
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private void say(@Nullable ServerPlayer victim, String key) {
        String line = TaxManLines.pick(key, getRandom());
        if (line == null) {
            return;
        }
        Component message = Component.literal("<Tax Man> " + line);
        List<ServerPlayer> audience = new ArrayList<>();
        if (victim != null) {
            audience.add(victim);
        }
        if (level() instanceof ServerLevel serverLevel) {
            double radius = Config.TAXES_CHAT_RADIUS.get();
            audience.addAll(serverLevel.getPlayers(p -> p != victim && p.distanceToSqr(this) <= radius * radius));
        }
        audience.forEach(p -> p.sendSystemMessage(message));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Phase", phase.name());
        tag.putInt("TakenValue", takenValue);
        if (victimId != null) {
            tag.putUUID("Victim", victimId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String saved = tag.getString("Phase");
        phase = saved.isEmpty() ? Phase.ARRIVING : Phase.valueOf(saved);
        takenValue = tag.getInt("TakenValue");
        if (tag.hasUUID("Victim")) {
            victimId = tag.getUUID("Victim");
        }
    }
}
