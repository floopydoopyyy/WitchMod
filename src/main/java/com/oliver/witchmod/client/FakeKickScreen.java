package com.oliver.witchmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Bedrock Moment (Fake Kick): a convincing lookalike of vanilla's disconnect screen — the "Connection Lost"
 * title, a realistic netty/timeout reason, and a "Back to Server List" button. It's entirely fake: nothing
 * touches the connection, and dismissing it (button, Esc, or a ~12s timeout) just drops you back into the
 * still-running game.
 */
public final class FakeKickScreen extends Screen {
    private static final String[] REASONS = {
        "Internal Exception: io.netty.handler.timeout.ReadTimeoutException",
        "Timed out",
        "Connection reset",
        "Internal Exception: java.io.IOException: An existing connection was forcibly closed by the remote host",
        "Internal Exception: io.netty.handler.codec.DecoderException",
    };

    private final Component reason;
    private int ticks;

    public FakeKickScreen() {
        super(Component.translatable("disconnect.lost")); // "Connection Lost"
        RandomSource r = RandomSource.create();
        this.reason = Component.literal(REASONS[r.nextInt(REASONS.length)]);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.toMenu"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 + 48, 0xFFFFFF);
        g.drawCenteredString(this.font, this.reason, this.width / 2, this.height / 4 + 70, 0xFFFFFF);
    }

    @Override
    public void tick() {
        if (++ticks > 240) { // auto-dismiss after ~12s so it's never a genuine soft-lock
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null); // fake — straight back into the game
        }
    }
}
