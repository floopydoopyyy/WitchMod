package com.oliver.witchmod.effects.curses.dweller;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lights-Out (tier 3): every nearby light dies at once and the dark rushes in — Darkness + a screen flicker.
 * The reference extraction of the per-event refactor (see {@link DwellerEvent}).
 */
public final class LightsOutEvent implements DwellerEvent {
    @Override
    public String id() {
        return "lightsout";
    }

    @Override
    public boolean run(CurseTheDweller curse, ServerPlayer target, CurseTheDweller.State state, ServerLevel level) {
        BlockPos origin = target.blockPosition();
        int snuffed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, -4, -7), origin.offset(7, 4, 7))) {
            BlockState st = level.getBlockState(pos);
            if (st.is(BlockTags.CANDLES) || st.getBlock() == Blocks.TORCH || st.getBlock() == Blocks.WALL_TORCH
                    || st.getBlock() == Blocks.LANTERN || st.is(BlockTags.CAMPFIRES)) {
                level.destroyBlock(pos.immutable(), false);
                if (++snuffed >= 16) {
                    break;
                }
            }
        }
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
        CurseTheDweller.setFlicker(target, 14);
        CurseTheDweller.playToVictim(target, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.4F);
        CurseTheDweller.playToVictim(target, SoundEvents.WARDEN_ANGRY, 0.6F, 0.6F);
        return true;
    }
}
