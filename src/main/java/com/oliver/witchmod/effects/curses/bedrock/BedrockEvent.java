package com.oliver.witchmod.effects.curses.bedrock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One of Bedrock Moment's ACTIVE "bugs" as its own class (parallel to the Dweller's {@code DwellerEvent}). The
 * weighted active-event pool and {@code /bewitch debug force witchmod:bedrock_moment &lt;id&gt;} both dispatch
 * through these; the trigger logic currently lives on {@link CurseBedrockMoment} (isolated + package-visible),
 * so each event's body can be inlined into its class incrementally without touching call sites. Passive bugs
 * (aimbot, pause, delay, ghost blocks, silent creeper, hotbar drift) are event-hook-driven and are NOT in here.
 */
public interface BedrockEvent {
    /** Debug id (also the sub-event name for {@code /bewitch debug force}). */
    String id();

    /** Fire the bug. Returns false if a precondition wasn't met (no creeper to blitz, no mob to duplicate…). */
    boolean run(CurseBedrockMoment curse, ServerPlayer target, CurseBedrockMoment.State state, ServerLevel level);
}
