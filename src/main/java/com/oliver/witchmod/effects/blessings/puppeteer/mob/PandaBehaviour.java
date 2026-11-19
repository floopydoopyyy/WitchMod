package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the panda. no bite — left-click SNEEZES (vanilla's wind-up, the sneeze cloud, the slimeball it sometimes
 * blows out, every panda nearby jumping) and the force of it sends you back a step. right-click ROLLS you, vanilla's
 * tumble, the way you're looking (your own client drives it, see PuppetMovement). both animate on the model as the
 * real one's do. PUPPET_DASH_POWER is the sneeze's counter (synced for the model); PUPPET_LUNGE_START the roll's start.
 */
public final class PandaBehaviour implements PuppetBehaviour {
    public static final PandaBehaviour INSTANCE = new PandaBehaviour();

    private PandaBehaviour() {}

    /** the sneeze's wind-up so far (0 = not sneezing). */
    public static int sneezeCounter(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PANDA
                ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : 0;
    }

    /** ticks into a roll (0 the first), or -1 when not rolling (both sides). */
    public static int rollTick(Player player) {
        if (PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) != PuppetType.PANDA) {
            return -1;
        }
        long start = player.getData(WitchModAttachments.PUPPET_LUNGE_START);
        long t = player.level().getGameTime() - start;
        try {
            return start != 0L && t >= 0 && t < Config.PUPPETEER_PANDA_ROLL_TICKS.get() ? (int) t : -1;
        } catch (IllegalStateException e) {
            return -1; // config not synced yet
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        FarmAnimalBehaviour.lure(player, type); // bamboo draws it in
        int counter = sneezeCounter(player);
        if (counter <= 0) {
            return;
        }
        counter++;
        if (counter > Config.PUPPETEER_PANDA_SNEEZE_WINDUP_TICKS.get()) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
            sneeze(player);
        } else {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, counter);
        }
    }

    /** vanilla's afterSneeze — the cloud, the sound, nearby pandas jumping, the gift — and the kick back. */
    private static void sneeze(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double reach = (player.getBbWidth() + 1.0F) * 0.5;
        Vec3 v = player.getDeltaMovement();
        level.sendParticles(ParticleTypes.SNEEZE, player.getX() - reach * Mth.sin(yaw), player.getEyeY() - 0.1,
                player.getZ() + reach * Mth.cos(yaw), 0, v.x, 0.0, v.z, 1.0);
        level.playSound(null, player.blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 1.0F, 1.0F);
        for (net.minecraft.world.entity.animal.Panda panda : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Panda.class,
                player.getBoundingBox().inflate(10.0))) {
            if (!panda.isBaby() && panda.onGround() && !panda.isInWater() && panda.canPerformAction()) {
                panda.jumpFromGround();
            }
        }
        if (level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.GIFT);
            for (ItemStack stack : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PANDA_SNEEZE)
                    .getRandomItems(params)) {
                player.spawnAtLocation(stack);
            }
        }
        // the force of it: a hop back from where you're facing
        double kick = Config.PUPPETEER_PANDA_SNEEZE_KNOCKBACK.get();
        if (kick > 0.0) {
            Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw)).scale(kick);
            player.setDeltaMovement(back.x, Math.max(v.y, 0.0) + 0.25 * kick, back.z);
            player.hurtMarked = true;
        }
        Puppets.countAction(player);
    }

    /** left-click: the sneeze winds up (vanilla's pre-sneeze, then the real thing). */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (sneezeCounter(player) > 0 || !Puppets.ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_PANDA_SNEEZE_COOLDOWN_TICKS.get());
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, 1);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PANDA_PRE_SNEEZE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** right-click: roll the way you're looking. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (rollTick(player) >= 0 || !Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_PANDA_ROLL_COOLDOWN_TICKS.get());
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, player.level().getGameTime());
        Puppets.countAction(player);
    }

    /** no bite: left-click is its sneeze. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        if (PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PANDA) {
            player.setData(WitchModAttachments.PUPPET_LUNGE_START, 0L);
        }
    }

    /** left-click sneezes, right-click rolls. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.add(tap(Key.USE), PuppetMove.ROLL.labelKey(), PuppetControls.ready(player));
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.sneeze", PuppetControls.ready2(player));
    }
}
