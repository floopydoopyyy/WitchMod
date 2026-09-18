package com.oliver.witchmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * client-only config — visual/local preferences that live on the rendering machine, not synced from a server.
 * in {@code config/witchmod-client.toml}; applies without a world reload.
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SPLITSCREEN_LIVE_POV = BUILDER
            .comment("Splitscreen: render the partner's LIVE point of view in the side panel (a real second render pass).",
                    "EXPERIMENTAL — off by default. When off, the panel shows a 'PLAYER 2' screen (partner name + live",
                    "position/facing) and everything else works. Set true to try the live POV.")
            .define("splitscreenLivePov", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
