package com.oliver.witchmod.effects.curses;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Curses;

/**
 * Every block you break has something living in it (master-spec Pests). A decent chance per block mined —
 * ANY block, not just stone — that 1–3 silverfish pour out of the gap and come straight for you.
 *
 * <p><b>The nearby ceiling isn't balance, it's a safety guard.</b> Silverfish call MORE silverfish out of
 * surrounding stone when they're hit, so a curse that adds them on every few blocks mined can snowball into
 * a swarm the victim genuinely cannot recover from — and that a server would rather not tick. Above
 * {@code MAX_NEARBY} the spawn is simply skipped until the crowd thins out.
 *
 * <p>Note the chance looks low because mining is such a high-frequency action: at 8% per block, ordinary
 * tunnelling still produces a steady trickle.
 *
 * <p>The trigger lives in {@code CurseEventHandler} — it needs the block-break event, not a tick.
 */
public final class CursePests extends Effect {
    public CursePests() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.COBBLESTONE);
    }

    /** You find out the first time something crawls out of a block (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Hook for breaking a block — see {@code CurseEventHandler}. */
    public static void onBlockMined(ServerPlayer player, BlockPos pos) {
        if (player.getRandom().nextInt(100) >= Config.PESTS_CHANCE.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();

        double radius = Config.PESTS_NEARBY_RADIUS.get();
        AABB around = new AABB(pos).inflate(radius);
        if (level.getEntitiesOfClass(Silverfish.class, around).size() >= Config.PESTS_MAX_NEARBY.get()) {
            return; // already infested — see the class note on why this ceiling exists
        }

        int min = Config.PESTS_MIN_PER_TRIGGER.get();
        int max = Math.max(min, Config.PESTS_MAX_PER_TRIGGER.get());
        int count = min + player.getRandom().nextInt(max - min + 1);

        boolean spawned = false;
        for (int i = 0; i < count; i++) {
            Silverfish silverfish = EntityType.SILVERFISH.create(level);
            if (silverfish == null) {
                continue;
            }
            silverfish.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    player.getRandom().nextFloat() * 360.0F, 0.0F);
            silverfish.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null);
            silverfish.setTarget(player); // they know exactly whose fault this is
            level.addFreshEntity(silverfish);
            spawned = true;
        }
        if (spawned) {
            Curses.PESTS.value().markDiscoveredByVictim(player);
        }
    }
}
