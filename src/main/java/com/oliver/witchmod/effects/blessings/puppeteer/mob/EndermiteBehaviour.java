package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/** puppeteer: the endermite puppet. */
public final class EndermiteBehaviour implements PuppetBehaviour {
    public static final EndermiteBehaviour INSTANCE = new EndermiteBehaviour();

    private EndermiteBehaviour() {}

    // --- endermite ----------------------------------------------------------------------------------

    private static final String BURROWED_KEY = "witchmod_burrowed";

    /** a burrowing endermite puppet — moves through blocks (both sides; see EntityBurrowMixin). */
    public static boolean burrowed(Player player) {
        return player.hasData(WitchModAttachments.PUPPET_BURROWED) && player.getData(WitchModAttachments.PUPPET_BURROWED);
    }

    /** whether a puppet is phasing through blocks right now: a burrowing endermite, or a vex while it flies. */
    public static boolean phasing(Player player) {
        return burrowed(player)
                || (PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VEX && player.getAbilities().flying);
    }

    /** the hardest block this puppet can pass: a vex gets obsidian (50), an endermite stops at obsidian-hard. */
    private static double maxHardness(Player player) {
        try {
            return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.VEX
                    ? Config.PUPPETEER_VEX_MAX_HARDNESS.get() : Config.PUPPETEER_ENDERMITE_MAX_HARDNESS.get();
        } catch (IllegalStateException e) {
            return 50.0;
        }
    }

    /** a block a phasing puppet can't pass: unbreakable (bedrock, barriers...) or at least as hard as its cap. */
    private static boolean impassable(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, double cap) {
        if (state.isAir()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness < 0.0F || hardness >= cap;
    }

    public static boolean passable(Player player, net.minecraft.world.phys.AABB box) {
        net.minecraft.world.level.Level level = player.level();
        if (box.minY < level.getMinBuildHeight()) {
            return false; // never out through the bottom of the world
        }
        double cap = maxHardness(player);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX - 1.0E-7), Mth.floor(box.maxY - 1.0E-7), Mth.floor(box.maxZ - 1.0E-7))) {
            if (impassable(level, pos, level.getBlockState(pos), cap)) {
                return false;
            }
        }
        return true;
    }

    /** how much of {@code delta} a burrowing endermite may move: all of it, or axis by axis up to an impassable block. */
    public static Vec3 burrowDelta(Player player, Vec3 delta) {
        net.minecraft.world.phys.AABB box = player.getBoundingBox();
        if (passable(player, box.move(delta))) {
            return delta;
        }
        Vec3 moved = Vec3.ZERO;
        for (Vec3 axis : List.of(new Vec3(0.0, delta.y, 0.0), new Vec3(delta.x, 0.0, 0.0), new Vec3(0.0, 0.0, delta.z))) {
            if (axis.lengthSqr() > 0.0 && passable(player, box.move(moved.add(axis)))) {
                moved = moved.add(axis);
            }
        }
        return moved;
    }

    /** inside the earth (any solid block in your box) — where a burrowing endermite has full 3d control. */
    public static boolean inEarth(Player player) {
        return !player.level().noCollision(player.getBoundingBox().deflate(1.0E-3));
    }

    /**
     * endermite: Burrow / Surface (right-click). burrowing, you sink into the ground and move through it freely (your own
     * client steers: where you look, jump to rise) — through anything short of obsidian-hard. out in the open air (a
     * cave) you just fall until you're back in the rock. surfacing takes you up to the nearest open space.
     */
    private static void toggleBurrow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (burrowed(player)) {
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            if (!surface(player, tag)) {
                player.displayClientMessage(Component.translatable("witchmod.puppeteer.endermite_no_surface").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            player.setData(WitchModAttachments.PUPPET_DATA, tag);
            cooldown(player, Config.PUPPETEER_ENDERMITE_BURROW_COOLDOWN_TICKS.get());
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
        BlockState ground = level.getBlockState(below);
        if (ground.getCollisionShape(level, below).isEmpty() || impassable(level, below, ground, maxHardness(player))) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.endermite_no_burrow").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        player.setData(WitchModAttachments.PUPPET_BURROWED, true);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putBoolean(BURROWED_KEY, true); // saved with you: a crash mid-burrow still surfaces you on login
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.teleportTo(player.getX(), player.getY() - 0.9, player.getZ()); // sink in
        Vec3 at = player.position();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.9, at.z,
                16, 0.3, 0.1, 0.3, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.9, at.z, 12, 0.3, 0.2, 0.3, 0.3);
        level.playSound(null, below, ground.getSoundType(level, below, player).getBreakSound(), SoundSource.PLAYERS, 0.8F, 1.2F);
        cooldown(player, Config.PUPPETEER_ENDERMITE_BURROW_COOLDOWN_TICKS.get());
    }

    /**
     * stop burrowing, coming up to the nearest spot above where you fit in open air (or, failing that, anywhere close).
     * @return false if there's nowhere to come up (you stay burrowed); true if surfaced or never burrowed.
     */
    public static boolean surface(ServerPlayer player, CompoundTag tag) {
        boolean was = burrowed(player) || tag.getBoolean(BURROWED_KEY);
        if (!was) {
            return true;
        }
        ServerLevel level = player.serverLevel();
        Vec3 spot = null;
        for (int dy = 0; dy <= 64 && spot == null; dy++) {
            for (int sign : new int[] {1, -1}) {
                Vec3 at = player.position().add(0.0, Math.ceil(player.getY()) - player.getY() + sign * dy, 0.0);
                if (level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position())))) {
                    spot = at;
                    break;
                }
            }
        }
        if (spot == null) {
            return false;
        }
        player.setData(WitchModAttachments.PUPPET_BURROWED, false);
        player.noPhysics = false;
        tag.remove(BURROWED_KEY);
        player.teleportTo(spot.x, spot.y, spot.z);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 0.3, spot.z, 16, 0.3, 0.2, 0.3, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMITE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** a burrowing endermite stirs up the rock it passes through (and trails a little ender dust). */
    private static void tickEndermite(ServerPlayer player) {
        if (!burrowed(player)) {
            return;
        }
        player.resetFallDistance();
        ServerLevel level = player.serverLevel();
        Vec3 last = LAST_POS.put(player.getUUID(), player.position());
        boolean moving = last != null && last.distanceToSqr(player.position()) > 1.0E-3;
        if (!moving || player.tickCount % 3 != 0) {
            return;
        }
        BlockPos at = BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ());
        BlockState state = level.getBlockState(at);
        if (!state.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                    player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.2, 0.1, 0.2, 0.05);
            if (player.tickCount % 9 == 0) {
                level.playSound(null, at, state.getSoundType(level, at, player).getHitSound(), SoundSource.PLAYERS, 0.35F, 0.8F);
            }
        }
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.1, 0.1, 0.1, 0.2);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickEndermite(player);
    }

    /** burrow / surface. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        toggleBurrow(player);
        Puppets.countAction(player);
    }

    /** a burrowing endermite can't swing. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return burrowed(player);
    }

    /** a burrowing endermite lives in the rock. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (burrowed(player) && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            event.setCanceled(true);
        }
    }

    /** a burrowing endermite comes up to open air first (also after a crash). */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        surface(player, data);
    }

    /** burrowing: the move surfaces you instead. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (burrowed(player)) {
            out.add(PuppetControls.moveInput(type.move()), "witchmod.puppeteer.action.surface", PuppetControls.moveReady(player, type.move()));
        } else {
            out.addMove(player, type);
        }
    }
}
