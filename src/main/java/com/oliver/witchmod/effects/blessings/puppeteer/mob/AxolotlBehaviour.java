package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.DolphinBehaviour.*;

/** puppeteer: the axolotl puppet. */
public final class AxolotlBehaviour implements PuppetBehaviour {
    public static final AxolotlBehaviour INSTANCE = new AxolotlBehaviour();

    private AxolotlBehaviour() {}

    /**
     * axolotls: endless air underwater, fine on land (until dry). HOLD right-click to play dead: you lie still,
     * regenerate quickly, and every mob after you loses interest (and can't pick you back up while you're down).
     */
    private static void tickAxolotl(ServerPlayer player, int ticksRemaining) {
        // amphibious, like a real axolotl: breathes fine in AND out of water — it never drowns, it only dries out.
        player.setAirSupply(player.getMaxAirSupply());
        tickHold(player, PuppetType.AXOLOTL);
        if (!playingDead(player)) {
            tickMoisture(player, Config.PUPPETEER_AXOLOTL_MOISTURE_TICKS.get());
            return;
        }
        // playing dead: it's resting, so the dry-out is paused — you can keep it up out of water as long as you like.
        if (ticksRemaining % 10 == 0) {
            scaleHeal(player, Config.PUPPETEER_AXOLOTL_PLAY_DEAD_REGEN.get().floatValue() / 2.0F);
        }
        if (ticksRemaining % 5 == 0) {
            double r = Config.PUPPETEER_AXOLOTL_PLAY_DEAD_RADIUS.get();
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r),
                    // hasMemoryValue first: getMemory throws on a brain that doesn't register the memory
                    m -> m.getTarget() == player || m.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
                            && m.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == player)) {
                mob.setTarget(null);
                mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET); // piglins, hoglins...
                if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
                mob.setLastHurtByMob(null);
            }
        }
    }

    private static boolean playingDead(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.AXOLOTL
                && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickAxolotl(player, ticksRemaining);
    }

    /** getting back up starts the cooldown. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge > 0) {
            Puppets.cooldown(player, Config.PUPPETEER_AXOLOTL_PLAY_DEAD_COOLDOWN_TICKS.get());
        }
    }

    /** the "dead" don't swing. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return playingDead(player);
    }

    /** nothing picks a fight with an axolotl that's playing dead — not even whatever it bit. */
    @Override
    public boolean untargetable(ServerPlayer player, PuppetType type) {
        return playingDead(player);
    }
}
