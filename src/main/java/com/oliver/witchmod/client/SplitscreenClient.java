package com.oliver.witchmod.client;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.oliver.witchmod.ClientConfig;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * client half of the Splitscreen curse: the shared-screen side panel (the partner's screen), the
 * "Entering/Exiting splitscreen…" fake-load transitions, and the shared-sign freeze + damage force-close.
 *
 * <p>The live partner POV is a real second render pass ({@link SplitscreenPov}); if that's disabled or fails
 * on the GPU it degrades to a static "PLAYER 2" panel showing the partner's live position/facing — the split
 * and all gameplay still work.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class SplitscreenClient {
    private static boolean lastEditing = false;
    private static long lastSignCloseNonce = 0L;
    /** the graphics mode to hand back when the split ends — non-null only while we've forced them off Fabulous. */
    private static GraphicsStatus savedGraphics = null;

    private SplitscreenClient() {}

    private static boolean loading(LocalPlayer p) {
        int phase = p.getData(WitchModAttachments.SPLITSCREEN_PHASE);
        return (phase == 1 || phase == 3) && p.level().getGameTime() < p.getData(WitchModAttachments.SPLITSCREEN_LOAD_END);
    }

    private static boolean active(LocalPlayer p) {
        return p.getData(WitchModAttachments.SPLITSCREEN_PHASE) == 2;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        // report whether we have a sign editor open (drives the shared freeze).
        boolean editing = mc.screen instanceof AbstractSignEditScreen;
        if (editing != lastEditing) {
            lastEditing = editing;
            if (mc.getConnection() != null) {
                mc.getConnection().send(new WitchModNetwork.SplitscreenSignPayload(editing));
            }
        }
        // force-close our sign when the server says someone took damage.
        long close = player.getData(WitchModAttachments.SPLITSCREEN_SIGN_CLOSE);
        if (close != lastSignCloseNonce) {
            lastSignCloseNonce = close;
            if (mc.screen instanceof AbstractSignEditScreen) {
                mc.setScreen(null);
            }
        }

        enforceSafeGraphics(mc, player);
    }

    /**
     * while the split's live POV is running, keep the player OFF Fabulous — its whole-screen transparency
     * chain fights our second render pass into a dangerous strobe. We flip Fabulous→Fancy each tick it's
     * engaged (so re-selecting Fabulous mid-curse is undone immediately) and hand Fabulous back once the
     * split ends. Fabulous↔Fancy share chunk meshes, so no rebuild/hitch is needed.
     */
    private static void enforceSafeGraphics(Minecraft mc, LocalPlayer player) {
        boolean engaged = active(player) && ClientConfig.SPLITSCREEN_LIVE_POV.get();
        var opt = mc.options.graphicsMode();
        if (engaged && opt.get() == GraphicsStatus.FABULOUS) {
            if (savedGraphics == null) {
                savedGraphics = GraphicsStatus.FABULOUS;
            }
            opt.set(GraphicsStatus.FANCY);
            mc.options.save();
        } else if (!engaged && savedGraphics != null) {
            opt.set(savedGraphics);
            savedGraphics = null;
            mc.options.save();
        }
    }

    /** freeze movement during a transition, and for the non-editing partner while a shared sign is open. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean signLocked = player.getData(WitchModAttachments.SPLITSCREEN_SIGN_LOCK) == 1
                && !(Minecraft.getInstance().screen instanceof AbstractSignEditScreen);
        if (loading(player) || signLocked) {
            event.getInput().leftImpulse = 0.0F;
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    /** render the partner POV into its framebuffer before the HUD draws (world is already rendered here). */
    @SubscribeEvent
    static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !active(player)) {
            return;
        }
        int partnerId = player.getData(WitchModAttachments.SPLITSCREEN_PARTNER);
        if (partnerId < 0) {
            return;
        }
        SplitscreenPov.renderPartnerView(partnerId, event.getPartialTick());
    }

    /** draw the split panel + any transition overlay on top of the HUD. */
    @SubscribeEvent
    static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();

        // --- Enter/exit fake-loading screen ---
        if (loading(player)) {
            int phase = player.getData(WitchModAttachments.SPLITSCREEN_PHASE);
            g.fill(0, 0, w, h, 0xF0101014);
            String label = (phase == 1 ? "Entering splitscreen" : "Exiting splitscreen")
                    + ".".repeat((int) ((mc.level.getGameTime() / 6) % 4));
            g.drawCenteredString(mc.font, label, w / 2, h / 2 - 4, 0xFFE6E4DF);
            return;
        }
        if (!active(player)) {
            return;
        }

        // --- True console half-and-half: your view is the left half (the full-screen render underneath,
        // its right side covered here), the partner's screen fills the right half, grey divider between. ---
        int half = w / 2;
        int px = half;
        int py = 0;
        int pw = w - half;
        int ph = h;
        Entity partner = mc.level != null ? mc.level.getEntity(player.getData(WitchModAttachments.SPLITSCREEN_PARTNER)) : null;

        boolean live = SplitscreenPov.blit(g, px, py, pw, ph);
        if (!live) {
            // fallback = an honest error screen (so testers can file a clear bug report).
            g.fill(px, py, px + pw, py + ph, 0xFF14141C);
            int cx = px + pw / 2;
            int cy = ph / 2;
            String name = partner != null ? partner.getName().getString() : "PLAYER 2";
            g.drawCenteredString(mc.font, "§lPLAYER 2 — " + name, cx, cy - 40, 0xFF8FD0FF);
            if (partner != null) {
                g.drawCenteredString(mc.font, String.format("%d %d %d", (int) partner.getX(), (int) partner.getY(), (int) partner.getZ()),
                        cx, cy - 24, 0xFFBBBBBB);
                g.drawCenteredString(mc.font, "facing " + compass(partner.getYRot()), cx, cy - 12, 0xFF888888);
            }
            if (ClientConfig.SPLITSCREEN_LIVE_POV.get()) {
                g.drawCenteredString(mc.font, "§cthis is technically an error screen,", cx, cy + 14, 0xFFFF6666);
                g.drawCenteredString(mc.font, "§crendering failed lol", cx, cy + 26, 0xFFFF6666);
            } else {
                g.drawCenteredString(mc.font, "§7(live POV disabled in config)", cx, cy + 14, 0xFF888888);
            }
        }
        // grey console divider.
        g.fill(half - 2, 0, half + 2, h, 0xFF9A9A9A);
        g.drawString(mc.font, "P2", px + 4, 4, 0xFFFFFFFF, true);
        g.drawString(mc.font, "P1", 4, 4, 0xFFFFFFFF, true);
    }

    private static String compass(float yaw) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int i = Mth.floor((Mth.wrapDegrees(yaw) + 180.0F) / 45.0F + 0.5F) & 7;
        return dirs[i];
    }
}
