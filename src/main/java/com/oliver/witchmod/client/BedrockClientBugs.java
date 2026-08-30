package com.oliver.witchmod.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Client half of the newer Bedrock Moment bugs, driven off synced windows on the local player: Ghost Item,
 * Input Lag, Texture Flicker, Sprint Reset, Language Error, Speed Blitz, Fake Kick and Fake BSOD. Kept
 * separate from {@link ClientCurseHandler} to isolate the pile of new handlers.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class BedrockClientBugs {
    private BedrockClientBugs() {}

    private record InputSnap(float forward, float left, boolean up, boolean down, boolean lft, boolean right,
                             boolean jump, boolean sneak) {}

    private static final Deque<InputSnap> INPUT_BUF = new ArrayDeque<>();   // Input Lag
    private static final Deque<Vec3> BLITZ_BUF = new ArrayDeque<>();        // Speed Blitz recorded moves

    private static final Item[] GHOST_ITEMS = {
        Items.DIRT, Items.DIAMOND, Items.GOLD_INGOT, Items.TNT, Items.EGG, Items.BONE, Items.COOKED_BEEF,
        Items.ENDER_PEARL, Items.EMERALD, Items.REDSTONE, Items.OAK_SAPLING, Items.WHEAT, Items.STICK,
        Items.APPLE, Items.COAL, Items.SNOWBALL, Items.CAKE, Items.SLIME_BALL, Items.FEATHER, Items.CLOCK,
    };
    private static long ghostSeed = Long.MIN_VALUE;
    private static ItemStack ghostStack = ItemStack.EMPTY;

    private static boolean languageSwapped = false;
    private static long fakeKickSeen = 0L;
    private static boolean bsodActive = false;
    private static boolean bsodGlitchPhase = false;
    private static long bsodStartTick = 0L;
    private static int bsodVariant = 0;
    private static int savedWinX = 0;
    private static int savedWinY = 0;
    private static Boolean savedFullscreen = null;
    private static final int BSOD_GLITCH_TICKS = 7; // ~0.35s of "the machine is failing" before the blue screen

    // ---- movement bugs (Input Lag + Speed Blitz) ----------------------------------------------------

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        Input in = event.getInput();

        // Fake BSOD glitch pre-phase: dampen movement — and cut it out entirely in stutters — so it feels like
        // the OS is dying under you just before the blue screen appears.
        if (bsodGlitchPhase) {
            in.forwardImpulse *= 0.15F;
            in.leftImpulse *= 0.15F;
            if ((System.currentTimeMillis() / 80L) % 2L == 0L) {
                zero(in);
            }
            return;
        }

        // Speed Blitz: FREEZE (record + zero) then a 3x replay (still zero here; driven by velocity in tick).
        long blitzEnd = player.getData(WitchModAttachments.BEDROCK_SPEEDBLITZ_END);
        if (blitzEnd != Long.MIN_VALUE && mc.level.getGameTime() < blitzEnd) {
            long freezeEnd = player.getData(WitchModAttachments.BEDROCK_SPEEDBLITZ_FREEZE);
            if (mc.level.getGameTime() < freezeEnd) {
                double rad = Math.toRadians(player.getYRot());
                Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
                Vec3 strafe = new Vec3(fwd.z, 0, -fwd.x);
                BLITZ_BUF.addLast(fwd.scale(in.forwardImpulse).add(strafe.scale(in.leftImpulse)).scale(0.13));
            }
            zero(in);
            return;
        }

        // Input Lag: apply the input from `delay` ticks ago.
        long lagEnd = player.getData(WitchModAttachments.BEDROCK_INPUT_LAG);
        if (lagEnd != Long.MIN_VALUE && mc.level.getGameTime() < lagEnd) {
            INPUT_BUF.addLast(new InputSnap(in.forwardImpulse, in.leftImpulse, in.up, in.down, in.left, in.right, in.jumping, in.shiftKeyDown));
            if (INPUT_BUF.size() > Config.BEDROCK_INPUT_LAG_DELAY.get()) {
                InputSnap s = INPUT_BUF.pollFirst();
                in.forwardImpulse = s.forward();
                in.leftImpulse = s.left();
                in.up = s.up();
                in.down = s.down();
                in.left = s.lft();
                in.right = s.right();
                in.jumping = s.jump();
                in.shiftKeyDown = s.sneak();
            } else {
                zero(in); // buffer still filling — the initial "lag"
            }
        } else if (!INPUT_BUF.isEmpty()) {
            INPUT_BUF.clear();
        }
    }

    private static void zero(Input in) {
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    // ---- tick bugs (Sprint Reset, Language, Fake Kick, Fake BSOD, Speed Blitz replay) ----------------

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();

        // Sprint Reset — sprint cuts out for 5 of every 24 ticks.
        long sprintEnd = player.getData(WitchModAttachments.BEDROCK_SPRINT_RESET);
        if (sprintEnd != Long.MIN_VALUE && now < sprintEnd && now % 24 < 5) {
            mc.options.keySprint.setDown(false);
            player.setSprinting(false);
        }

        // Speed Blitz replay — drain 3 recorded moves per tick as a velocity burst (≈3x).
        long blitzEnd = player.getData(WitchModAttachments.BEDROCK_SPEEDBLITZ_END);
        long freezeEnd = player.getData(WitchModAttachments.BEDROCK_SPEEDBLITZ_FREEZE);
        if (blitzEnd != Long.MIN_VALUE && now >= freezeEnd && now < blitzEnd && !BLITZ_BUF.isEmpty()) {
            Vec3 sum = Vec3.ZERO;
            for (int i = 0; i < 3 && !BLITZ_BUF.isEmpty(); i++) {
                sum = sum.add(BLITZ_BUF.pollFirst());
            }
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(sum.x, v.y, sum.z);
            player.hurtMarked = true;
        } else if ((blitzEnd == Long.MIN_VALUE || now >= blitzEnd) && !BLITZ_BUF.isEmpty()) {
            BLITZ_BUF.clear();
        }

        // Language Error — swap to pirate/welsh, restore after.
        long langEnd = player.getData(WitchModAttachments.BEDROCK_LANGUAGE);
        boolean langActive = langEnd != Long.MIN_VALUE && now < langEnd;
        if (langActive && !languageSwapped) {
            languageSwapped = true;
            // Any of Minecraft's joke/novelty languages (Pirate, LOLCAT, Upside-down, Anglish...) plus Welsh —
            // but only ones actually present on this build, so it doesn't keep falling back to the same one.
            String[] fun = {"en_pt", "lol_us", "en_ud", "cy_gb", "enp", "tok"};
            java.util.List<String> valid = new java.util.ArrayList<>();
            for (String c : fun) {
                if (mc.getLanguageManager().getLanguage(c) != null) {
                    valid.add(c);
                }
            }
            String pick = valid.isEmpty() ? "en_pt"
                    : valid.get(net.minecraft.util.RandomSource.create().nextInt(valid.size()));
            setLanguage(mc, pick);
        } else if (!langActive && languageSwapped) {
            languageSwapped = false;
            setLanguage(mc, null); // restore (no resource reload)
        }

        // Fake Kick — a changed nonce pops the (fake) disconnect screen.
        long kickNonce = player.getData(WitchModAttachments.BEDROCK_FAKE_KICK);
        if (kickNonce != 0L && kickNonce != fakeKickSeen) {
            fakeKickSeen = kickNonce;
            if (!(mc.screen instanceof FakeKickScreen)) {
                mc.setScreen(new FakeKickScreen());
            }
        }

        tickBsod(mc, player, now);
        tickVibrant(mc, player);
        tickAirSwim(mc, player);
        tickHungry(mc, player);
    }

    /**
     * Air Swimming: while the window is open and you're OUT of water, you SWIM through the air exactly like
     * it's water — the swimming/crawling pose, near-neutral buoyancy, and moving forward strokes you along
     * your look direction (up, down, around). Force-cancelled when the window ends (you drop).
     */
    private static void tickAirSwim(Minecraft mc, LocalPlayer player) {
        long end = player.getData(WitchModAttachments.BEDROCK_AIR_SWIM);
        if (mc.level == null || end == Long.MIN_VALUE || mc.level.getGameTime() >= end
                || player.isInWater() || player.getAbilities().flying) {
            return;
        }
        // The crawl/swim pose in mid-air (both flags — one drives the pose, one the animation).
        player.setSwimming(true);
        player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        player.setSprinting(true); // vanilla ties the swim animation to sprinting

        Vec3 v = player.getDeltaMovement();
        Vec3 nv;
        if (mc.options.keyUp.isDown()) {
            // Swim toward where you're looking — full 3D, like a real swim stroke.
            nv = v.scale(0.55).add(player.getLookAngle().scale(0.11));
        } else {
            // Coast + near-neutral buoyancy (barely sinks) when not stroking.
            nv = new Vec3(v.x * 0.85, v.y * 0.35, v.z * 0.85);
        }
        double cap = 0.4; // swim speed cap
        if (nv.length() > cap) {
            nv = nv.normalize().scale(cap);
        }
        player.setDeltaMovement(nv);
        player.fallDistance = 0;
        player.hurtMarked = true;
    }

    private static int hungryHold = 0;
    private static int hungrySlot = -1;

    /**
     * Hungry: while the window is open, holding right-click "eats" whatever is in your main hand — eat FX build
     * over the eat duration and, if you let it finish, the server consumes one (via a C2S packet). Releasing
     * early cancels it. (For non-food items the exact arm animation can't be forced without item mixins, so
     * it's sold with the eat sounds + particles.)
     */
    private static void tickHungry(Minecraft mc, LocalPlayer player) {
        long end = player.getData(WitchModAttachments.BEDROCK_HUNGRY);
        boolean active = mc.level != null && end != Long.MIN_VALUE && mc.level.getGameTime() < end;
        int slot = player.getInventory().selected;
        boolean holding = active && mc.options.keyUse.isDown() && !player.getMainHandItem().isEmpty();
        if (!holding || slot != hungrySlot) {
            hungryHold = 0;
            hungrySlot = slot;
            if (!holding) {
                return;
            }
        }
        hungryHold++;
        player.swinging = true; // wag the arm so it reads as gnawing at it
        if (hungryHold % 4 == 0) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.0F, false);
            for (int i = 0; i < 3; i++) {
                player.level().addParticle(new net.minecraft.core.particles.ItemParticleOption(
                        net.minecraft.core.particles.ParticleTypes.ITEM, player.getMainHandItem()),
                        player.getX(), player.getEyeY() - 0.2, player.getZ(), 0, 0, 0);
            }
        }
        if (hungryHold >= 32) { // eat duration reached — consume one
            hungryHold = 0;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.oliver.witchmod.network.WitchModNetwork.BedrockHungryEatPayload());
        }
    }

    /**
     * Fake BSOD, made believable: a short GLITCH pre-phase where the machine looks like it's "coping" —
     * MC's own audio cuts out, the window jitters (windowed) and the screen stutter-flickers black while input
     * is dampened — THEN it forces fullscreen and the chosen blue-screen ROLLS down from the top and holds
     * (see {@link FakeBsodScreen}). 50% the standard image, 50% one of the three funny ones. Restores on end.
     */
    private static void tickBsod(Minecraft mc, LocalPlayer player, long now) {
        long bsodEnd = player.getData(WitchModAttachments.BEDROCK_BSOD);
        boolean bsod = bsodEnd != Long.MIN_VALUE && now < bsodEnd;
        if (bsod) {
            if (!bsodActive) {
                bsodActive = true;
                bsodStartTick = now;
                bsodVariant = player.getRandom().nextInt(100) < 50 ? 0 : 1 + player.getRandom().nextInt(3);
                savedFullscreen = mc.getWindow().isFullscreen();
                int[] wx = new int[1];
                int[] wy = new int[1];
                org.lwjgl.glfw.GLFW.glfwGetWindowPos(mc.getWindow().getWindow(), wx, wy);
                savedWinX = wx[0];
                savedWinY = wy[0];
            }
            mc.getSoundManager().pause(); // cut MC's own audio from the first glitch tick (never other apps)
            long elapsed = now - bsodStartTick;
            if (elapsed < BSOD_GLITCH_TICKS) {
                // "Device coping": jitter the window (if windowed); the flicker + input dampen run off this flag.
                bsodGlitchPhase = true;
                if (!mc.getWindow().isFullscreen()) {
                    org.lwjgl.glfw.GLFW.glfwSetWindowPos(mc.getWindow().getWindow(),
                            savedWinX + player.getRandom().nextInt(19) - 9, savedWinY + player.getRandom().nextInt(19) - 9);
                }
            } else {
                bsodGlitchPhase = false;
                if (savedFullscreen != null && !savedFullscreen && !mc.getWindow().isFullscreen()) {
                    org.lwjgl.glfw.GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), savedWinX, savedWinY); // undo jitter
                    mc.options.fullscreen().set(true);
                }
                if (!(mc.screen instanceof FakeBsodScreen)) {
                    mc.setScreen(new FakeBsodScreen(bsodVariant));
                }
            }
        } else if (bsodActive) {
            bsodActive = false;
            bsodGlitchPhase = false;
            mc.getSoundManager().resume();
            if (!mc.getWindow().isFullscreen()) {
                org.lwjgl.glfw.GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), savedWinX, savedWinY);
            }
            if (savedFullscreen != null && !savedFullscreen && mc.getWindow().isFullscreen()) {
                mc.options.fullscreen().set(false);
            }
            savedFullscreen = null;
            if (mc.screen instanceof FakeBsodScreen) {
                mc.setScreen(null);
            }
        }
    }

    private static net.minecraft.locale.Language savedLangObj = null;

    /**
     * Swaps the client's active translations WITHOUT a resource reload — builds the target language directly
     * with {@code ClientLanguage.loadFrom} and injects it via {@code Language.inject}, exactly like the
     * language screen does internally but without touching resource packs. Purely client render — nothing is
     * sent to the server, and the player's real language setting is never changed. Restore re-injects the
     * saved language object.
     */
    private static void setLanguage(Minecraft mc, String code) {
        try {
            if (code == null) { // restore
                if (savedLangObj != null) {
                    net.minecraft.locale.Language.inject(savedLangObj);
                    savedLangObj = null;
                }
                return;
            }
            savedLangObj = net.minecraft.locale.Language.getInstance();
            var lang = net.minecraft.client.resources.language.ClientLanguage.loadFrom(
                    mc.getResourceManager(), java.util.List.of("en_us", code), false);
            net.minecraft.locale.Language.inject(lang);
        } catch (Throwable t) {
            savedLangObj = null; // swap unavailable on this build — degrade silently
        }
    }

    // ---- render bugs (Ghost Item, Texture Flicker) --------------------------------------------------

    @SubscribeEvent
    static void onGhostItem(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long end = player.getData(WitchModAttachments.BEDROCK_GHOST_ITEM);
        if (end == Long.MIN_VALUE || mc.level.getGameTime() >= end) {
            return;
        }
        if (end != ghostSeed) {
            ghostSeed = end;
            ghostStack = new ItemStack(GHOST_ITEMS[(int) Math.floorMod(end, GHOST_ITEMS.length)]);
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = g.guiWidth() / 2 - 90 + player.getInventory().selected * 20 + 3;
        int y = g.guiHeight() - 16 - 3;
        g.renderItem(ghostStack, x, y);
    }

    /** Fake BSOD glitch pre-phase: stutter the screen to black (a struggling-device look, NOT a fast strobe). */
    @SubscribeEvent
    static void onBsodGlitch(RenderGuiEvent.Post event) {
        if (!bsodGlitchPhase) {
            return;
        }
        // ~90ms buckets, black ~1/3 of the time → reads as freeze/stutter rather than a rapid flash.
        if ((System.currentTimeMillis() / 90L) % 3L == 0L) {
            GuiGraphics g = event.getGuiGraphics();
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xF0000000);
        }
    }

    // Vibrant: the saturation-boost post shader (replaces the old missing-texture checker).
    private static final net.minecraft.resources.ResourceLocation VIBRANT_SHADER =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "shaders/post/bedrock_vibrant.json");
    private static boolean vibrantActive = false;
    private static boolean vibrantFailed = false;

    private static void tickVibrant(Minecraft mc, LocalPlayer player) {
        long end = player.getData(WitchModAttachments.BEDROCK_TEXTURE_FLICKER);
        boolean want = mc.level != null && end != Long.MIN_VALUE && mc.level.getGameTime() < end;
        if (want && !vibrantActive && !vibrantFailed) {
            try {
                mc.gameRenderer.loadEffect(VIBRANT_SHADER);
                vibrantActive = true;
            } catch (Throwable t) {
                vibrantFailed = true;
            }
        } else if (!want && vibrantActive) {
            mc.gameRenderer.shutdownEffect();
            vibrantActive = false;
        }
    }
}
