package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * a puppet's controls as the HUD shows them (bottom-right: "[Right Button] Rally"). common code describes them —
 * which key, how it's pressed, what it does, when it's ready — and the client draws them with your real key names.
 * see {@link PuppetBehaviour#controls}.
 */
public final class PuppetControls {
    /** the buttons a control can be on. */
    public enum Key { USE, ATTACK, SNEAK, JUMP, DROP, FORWARD, SCROLL }

    /** how it's pressed. */
    public enum Press { TAP, HOLD, DOUBLE_TAP }

    public record Input(Key key, Press press) {}

    /** one line: its keys (pressed together), what it does, and the game time it's ready (0 / past = ready now). */
    public record Line(List<Input> keys, Component label, long readyAt) {}

    private final List<Line> lines = new ArrayList<>();

    public static Input tap(Key key) {
        return new Input(key, Press.TAP);
    }

    public static Input hold(Key key) {
        return new Input(key, Press.HOLD);
    }

    public static Input doubleTap(Key key) {
        return new Input(key, Press.DOUBLE_TAP);
    }

    /** a control on one input, labelled from a lang key, on a cooldown ending at {@code readyAt}. */
    public PuppetControls add(Input input, String labelKey, long readyAt) {
        return add(List.of(input), Component.translatable(labelKey), readyAt);
    }

    public PuppetControls add(List<Input> keys, Component label, long readyAt) {
        lines.add(new Line(keys, label, readyAt));
        return this;
    }

    /** a status line with no key (bartering, enraged, Here's Johnny!). */
    public PuppetControls note(Component text) {
        return add(List.of(), text, 0L);
    }

    public List<Line> lines() {
        return lines;
    }

    // --- the usual move line ---

    /** the input a puppet's move is on: right-click (pressed or held), a pounce's attack button, a bat's double-tap jump. */
    public static Input moveInput(PuppetMove move) {
        return switch (move) {
            case POUNCE -> tap(Key.ATTACK);
            case FLY -> doubleTap(Key.JUMP);
            default -> move.held() ? hold(Key.USE) : tap(Key.USE);
        };
    }

    /** when a puppet's move is ready: never on a cooldown (fuse, bow, crossbow), the second cooldown (egg, volley), or the main one. */
    public static long moveReady(Player player, PuppetMove move) {
        return switch (move) {
            case FUSE, BOW, CROSSBOW, FLY -> 0L;
            case EGG, VOLLEY -> player.getData(WitchModAttachments.PUPPET_ACTION2_READY);
            default -> player.getData(WitchModAttachments.PUPPET_ACTION_READY);
        };
    }

    /** the puppet's move, as its own line (nothing for a puppet with no move). */
    public PuppetControls addMove(Player player, PuppetType type) {
        PuppetMove move = type.move();
        if (move != PuppetMove.NONE) {
            add(moveInput(move), move.labelKey(), moveReady(player, move));
        }
        return this;
    }

    /** the main cooldown's end. */
    public static long ready(Player player) {
        return player.getData(WitchModAttachments.PUPPET_ACTION_READY);
    }

    /** the second cooldown's end. */
    public static long ready2(Player player) {
        return player.getData(WitchModAttachments.PUPPET_ACTION2_READY);
    }
}
