package com.oliver.witchmod.effects.curses.dweller;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * one discrete Dweller "moment" as its own class (the per-event refactor). The orchestration — the DREAD
 * engine, the phase/stalking state machine, and the shared helpers ({@code CurseTheDweller.playToVictim},
 * {@code setFlicker}, {@code placeAt}, …) — stays on {@link CurseTheDweller}; each event just implements
 * {@link #run} against the shared {@link CurseTheDweller.State}.
 *
 * <p>Both the weighted mini-event pool and {@code /bewitch debug force witchmod:haunted &lt;id&gt;} dispatch
 * through these, so extracting an event automatically makes it forcible under its {@link #id()}.
 */
public interface DwellerEvent {
    /** the debug id (also the sub-event name for {@code /bewitch debug force}). */
    String id();

    /**
     * fire the event. Return {@code false} if a precondition wasn't met (no doors to knock, no mob to possess…)
     * so the caller can try another candidate / report it.
     */
    boolean run(CurseTheDweller curse, ServerPlayer target, CurseTheDweller.State state, ServerLevel level);
}
