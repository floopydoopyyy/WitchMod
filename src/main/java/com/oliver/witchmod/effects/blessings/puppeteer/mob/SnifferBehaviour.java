package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the sniffer. slow and lumbering (a sniffer's own pace), lured by torchflower seeds, no bite. left-click at a
 * block SNIFFS (its nose-to-the-ground sniff); at the air, its long call (the scent "moo"). hold right-click to DIG, the
 * whole of vanilla's dig: nose down, dirt flying off the block under its head, the seed coming up (vanilla's
 * sniffer_digging loot) — then it rises and is pleased with itself. let go before the seed and it gives up; the seed puts
 * it on cooldown. like the real one it digs only diggable ground (grass, dirt, moss, mud...), never the same spot twice
 * in a row, and a snifflet can't dig. PUPPET_DASH_POWER is its vanilla state id (synced, so the model plays it).
 */
public final class SnifferBehaviour implements PuppetBehaviour {
    public static final SnifferBehaviour INSTANCE = new SnifferBehaviour();

    private SnifferBehaviour() {}

    /** ticks in the current state, and the last spots dug (vanilla remembers 20). */
    private static final Map<UUID, Integer> IN_STATE = EffectState.sessionConcurrent("puppeteer/sniffer/in_state");
    private static final Map<UUID, Deque<BlockPos>> DUG = EffectState.sessionConcurrent("puppeteer/sniffer/dug");

    public static Sniffer.State state(Player player) {
        if (PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) != PuppetType.SNIFFER) {
            return Sniffer.State.IDLING;
        }
        return Sniffer.State.BY_ID.apply(player.getData(WitchModAttachments.PUPPET_DASH_POWER));
    }

    /** digging or rising from it: nose down, going nowhere (your client holds you too). */
    public static boolean busy(Player player) {
        Sniffer.State s = state(player);
        return s == Sniffer.State.DIGGING || s == Sniffer.State.RISING;
    }

    private static void setState(ServerPlayer player, Sniffer.State state) {
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, state.id());
        IN_STATE.put(player.getUUID(), 0);
    }

    /** the block its nose is over: vanilla's head block, out in front. */
    private static BlockPos headBlock(Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double reach = 2.25 * (player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0 ? 0.5 : 1.0);
        Vec3 head = player.position().add(-Mth.sin(yaw) * reach, 0.0, Mth.cos(yaw) * reach);
        return BlockPos.containing(head.x, player.getY() + 0.2, head.z);
    }

    /** vanilla's canDig: grown, on dry ground, not riding, diggable ground under its nose it hasn't just dug. */
    private static boolean canDig(ServerPlayer player) {
        BlockPos ground = headBlock(player).below();
        return player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") >= 0 && player.onGround() && !player.isInWater()
                && !player.isPassenger() && player.level().getBlockState(ground).is(BlockTags.SNIFFER_DIGGABLE_BLOCK)
                && !DUG.getOrDefault(player.getUUID(), new ArrayDeque<>()).contains(ground);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        FarmAnimalBehaviour.lure(player, type); // torchflower seeds draw it in
        Sniffer.State state = state(player);
        int in = IN_STATE.merge(player.getUUID(), 1, Integer::sum);
        boolean held = Holds.HELD.contains(player.getUUID());
        ServerLevel level = player.serverLevel();
        int seedAt = Config.PUPPETEER_SNIFFER_SEED_TICKS.get();
        switch (state) {
            case IDLING -> {
                if (held && Puppets.ready(player) && canDig(player)) {
                    setState(player, Sniffer.State.DIGGING);
                    level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_DIGGING, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
            case SNIFFING, SCENTING, FEELING_HAPPY -> {
                if (in > Config.PUPPETEER_SNIFFER_GESTURE_TICKS.get()) {
                    setState(player, Sniffer.State.IDLING);
                }
            }
            case DIGGING -> {
                if (in < seedAt && !held) { // gave up before the seed: back up, nothing found, nothing spent
                    setState(player, Sniffer.State.RISING);
                    level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_DIGGING_STOP, SoundSource.PLAYERS, 1.0F, 1.0F);
                    return;
                }
                digParticles(player, in, seedAt);
                if (in == seedAt) {
                    dropSeed(player);
                }
                if (in >= Config.PUPPETEER_SNIFFER_DIG_TICKS.get()) {
                    setState(player, Sniffer.State.RISING);
                    level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_DIGGING_STOP, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
            case RISING -> {
                if (in > 40) { // vanilla's FinishedDigging
                    boolean found = !Puppets.ready(player); // on cooldown = the seed came up
                    setState(player, found ? Sniffer.State.FEELING_HAPPY : Sniffer.State.IDLING);
                    if (found) {
                        level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_HAPPY, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
            default -> setState(player, Sniffer.State.IDLING);
        }
        Holds.tickHold(player, type); // (counts the dig, for the hud's bar)
        if (busy(player)) {
            player.setDeltaMovement(0.0, Math.min(player.getDeltaMovement().y, 0.0), 0.0);
        }
    }

    /** vanilla's dig dust: the block under its nose, flying up through the middle of the dig, with its knock every half second. */
    private static void digParticles(ServerPlayer player, int in, int seedAt) {
        // vanilla shows dust between 1.7s and 6s into the dig animation — scaled to the seed's moment
        if (in < seedAt * 17 / 60 || in > seedAt) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos head = headBlock(player);
        BlockState ground = level.getBlockState(head.below());
        if (ground.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(head).add(0.0, -0.65, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y, at.z, 30, 0.0, 0.0, 0.0, 0.0);
        if (in % 10 == 0) {
            level.playSound(null, head, ground.getSoundType(level, head.below(), player).getHitSound(), SoundSource.PLAYERS, 0.5F, 0.5F);
        }
    }

    /** vanilla's dropSeed: the sniffer_digging loot, up at its nose; the spot's remembered; then the cooldown. */
    private static void dropSeed(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos head = headBlock(player);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(head))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        List<ItemStack> loot = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SNIFFER_DIGGING).getRandomItems(params);
        for (ItemStack stack : loot) {
            ItemEntity item = new ItemEntity(level, head.getX(), head.getY(), head.getZ(), stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_DROP_SEED, SoundSource.PLAYERS, 1.0F, 1.0F);
        Deque<BlockPos> dug = DUG.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        dug.addFirst(head.below());
        while (dug.size() > 20) {
            dug.removeLast();
        }
        Puppets.cooldown(player, Config.PUPPETEER_SNIFFER_DIG_COOLDOWN_TICKS.get());
        Puppets.countAction(player);
    }

    /** left-click: at a block, a sniff; at the air, its call (the scent). no cooldown. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (state(player) != Sniffer.State.IDLING) {
            return;
        }
        boolean atBlock = player.pick(player.blockInteractionRange(), 1.0F, false).getType() == HitResult.Type.BLOCK;
        boolean baby = player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0;
        if (atBlock) {
            setState(player, Sniffer.State.SNIFFING);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SNIFFER_SNIFFING, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            call(player, baby);
        }
    }

    /**
     * the call: head up, its long low call — and every sniffer that hears it answers, and lumbers over to you (as
     * they're drawn to their kind).
     */
    private static void call(ServerPlayer player, boolean baby) {
        ServerLevel level = player.serverLevel();
        setState(player, Sniffer.State.SCENTING);
        level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_IDLE, SoundSource.PLAYERS, 2.5F, baby ? 1.3F : 0.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.SNIFFER_SCENTING, SoundSource.PLAYERS, 1.0F, baby ? 1.3F : 1.0F);
        double r = Config.PUPPETEER_SNIFFER_CALL_RADIUS.get();
        for (Sniffer herd : level.getEntitiesOfClass(Sniffer.class, player.getBoundingBox().inflate(r), s -> s.isAlive() && !s.isNoAi())) {
            herd.playSound(SoundEvents.SNIFFER_IDLE, 1.5F, herd.isBaby() ? 1.3F : 1.0F);
            herd.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(player, 1.25F, 3));
        }
        Puppets.countAction(player);
    }

    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public boolean dealsMelee(net.minecraft.server.level.ServerPlayer player, PuppetType type) {
        return false;
    }

    /** the dig runs itself (tick) — the hold only counts while it's digging, for the hud. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return state(player) == Sniffer.State.DIGGING;
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        IN_STATE.remove(player.getUUID());
        DUG.remove(player.getUUID());
    }

    /** sniff / call on left-click; hold right-click to dig (a grown one, over diggable ground). */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        boolean atBlock = player.pick(player.blockInteractionRange(), 1.0F, false).getType() == HitResult.Type.BLOCK;
        out.add(tap(Key.ATTACK), atBlock ? "witchmod.puppeteer.action.sniffer_sniff" : "witchmod.puppeteer.action.sniffer_call", 0L);
        if (player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") >= 0) {
            out.addMove(player, type);
        }
    }
}
