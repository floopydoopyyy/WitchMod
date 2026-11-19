package com.oliver.witchmod.effects.blessings.puppeteer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetGroup;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetMove;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;

/**
 * puppeteer's hud. while you're a puppet, your hearts / hunger / armour / hotbar are swapped for the puppet's
 * own health ({@link Hearts}); a console-style prompt panel in the bottom-right ({@link Controls}) lists what
 * your buttons do, shows the possession's progress, and — when you have the blessing and look at something
 * possessable — offers "possess". a soul-blue flash marks the moment control is handed over.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetHud {
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    /** the vanilla (and mod) layers a puppet doesn't have. */
    private static final Set<ResourceLocation> HIDDEN = Set.of(VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.HOTBAR, VanillaGuiLayers.SELECTED_ITEM_NAME,
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "allergic_hearts"),
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "gluttony_bar"),
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "thirst_bar"));
    private static final int FLASH_TICKS = 12;

    private static int flash;
    private static boolean wasPuppet;

    private PuppetHud() {}

    private static boolean isPuppet(LocalPlayer player) {
        return !PuppeteerClient.typeOf(player).isEmpty();
    }

    @SubscribeEvent
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        // (a witch keeps her hotbar — it's her potion belt)
        boolean belt = player != null && PuppetType.byId(PuppeteerClient.typeOf(player)) == PuppetType.WITCH
                && (event.getName().equals(VanillaGuiLayers.HOTBAR) || event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME));
        if (player != null && isPuppet(player) && HIDDEN.contains(event.getName()) && !belt) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean puppet = player != null && isPuppet(player);
        if (puppet && !wasPuppet) {
            flash = FLASH_TICKS;
        } else if (flash > 0) {
            flash--;
        }
        wasPuppet = puppet;
    }

    /** the puppet's health as hearts, where yours normally sit, with its name above. */
    public static final class Hearts implements LayeredDraw.Layer {
        @Override
        public void render(GuiGraphics g, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.options.hideGui || !isPuppet(player)) {
                return;
            }
            float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
            float max = Math.max(1.0F, player.getData(WitchModAttachments.PUPPET_MAX_HEALTH));
            if (max > com.oliver.witchmod.ClientConfig.puppetHealthBarAbove()) {
                bar(g, mc, player, health, max, deltaTracker); // a warden's 250 hearts would fill the screen
                return;
            }
            int hearts = Mth.ceil(max / 2.0F);
            int rows = Mth.ceil(hearts / 10.0F);
            int rowHeight = Math.max(10 - (rows - 2), 3);
            int x = g.guiWidth() / 2 - 91;
            int y = g.guiHeight() - 39;
            for (int i = hearts - 1; i >= 0; i--) {
                int hx = x + (i % 10) * 8;
                int hy = y - (i / 10) * rowHeight;
                g.blitSprite(HEART_CONTAINER, hx, hy, 9, 9);
                if (health >= i * 2 + 2) {
                    g.blitSprite(HEART_FULL, hx, hy, 9, 9);
                } else if (health > i * 2) {
                    g.blitSprite(HEART_HALF, hx, hy, 9, 9);
                }
            }
            PuppetType puppetType = PuppetType.byId(PuppeteerClient.typeOf(player));
            if (puppetType != null) {
                Component name = puppetType == PuppetType.KILLER_RABBIT
                        ? Component.translatable("witchmod.puppeteer.killer_rabbit")
                        : puppetType == PuppetType.SCREAMING_GOAT ? Component.translatable("witchmod.puppeteer.screaming_goat")
                        : puppetType.entityType().getDescription();
                g.drawString(mc.font, name, x, y - (rows - 1) * rowHeight - 10, 0xFF9EE7A0, true);
            }
            mc.gui.leftHeight += (rows - 1) * rowHeight + 10;
        }

        /** a big puppet's health, minecraft-style: its name, then one heart and the number (a warden's 250 hearts won't fit). */
        private static void bar(GuiGraphics g, Minecraft mc, LocalPlayer player, float health, float max, DeltaTracker delta) {
            int x = g.guiWidth() / 2 - 91;
            int y = g.guiHeight() - 39;
            g.blitSprite(HEART_CONTAINER, x, y, 9, 9);
            g.blitSprite(health / max > 0.0F ? HEART_FULL : HEART_HALF, x, y, 9, 9);
            g.drawString(mc.font, String.valueOf(Mth.ceil(health)), x + 11, y + 1, 0xFFFFFFFF, true);
            PuppetType puppetType = PuppetType.byId(PuppeteerClient.typeOf(player));
            if (puppetType != null) {
                g.drawString(mc.font, puppetType.entityType().getDescription(), x, y - 10, 0xFF9EE7A0, true);
            }
            mc.gui.leftHeight += 10;
        }
    }

    /** bottom-right, console style: "[Right Button] Rally". also the possession progress and the soul flash. */
    public static final class Controls implements LayeredDraw.Layer {
        private record Line(List<Component> keys, Component action, boolean ready) {}

        private static final float SCALE = 0.85F;

        @Override
        public void render(GuiGraphics g, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.options.hideGui) {
                return;
            }
            if (flash > 0) {
                float a = (flash - deltaTracker.getGameTimeDeltaPartialTick(false)) / FLASH_TICKS;
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), (Math.round(Mth.clamp(a, 0.0F, 1.0F) * 120) << 24) | 0xFFF0C8);
            }
            long now = player.level().getGameTime();
            Component use = mc.options.keyUse.getTranslatedKeyMessage();
            Component sneak = mc.options.keyShift.getTranslatedKeyMessage();
            List<Line> lines = new ArrayList<>();
            float progress = -1.0F;

            long bindingEnd = player.getData(WitchModAttachments.PUPPET_BINDING_END);
            String type = PuppeteerClient.typeOf(player);
            if (bindingEnd != seenBindingEnd) { // a new possession: its length is however long it has left now
                seenBindingEnd = bindingEnd;
                bindingTotal = Math.max(1L, bindingEnd - now);
            }
            if (bindingEnd > now) {
                long total = bindingTotal;
                progress = 1.0F - Mth.clamp((bindingEnd - now - deltaTracker.getGameTimeDeltaPartialTick(false)) / total, 0.0F, 1.0F);
                lines.add(new Line(List.of(), Component.translatable("witchmod.puppeteer.possessing"), true));
            } else if (!type.isEmpty()) {
                PuppetType puppet = PuppetType.byId(type);
                int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
                if (puppet != null) {
                    PuppetMove move = puppet.move();
                    // the puppet says what its controls are (PuppetBehaviour.controls); drawn here in your own key names
                    PuppetControls controls = new PuppetControls();
                    try {
                        puppet.behaviour().controls(player, puppet, controls);
                    } catch (IllegalStateException e) {
                        // the server's config hasn't reached us yet: no control lines this frame
                    }
                    for (PuppetControls.Line control : controls.lines()) {
                        lines.add(line(mc, control, now));
                    }
                    if (puppet.group() == PuppetGroup.HORSE) {
                        float leap = PuppetMovement.horseJumpProgress();
                        if (leap > 0.0F) {
                            progress = leap;
                        }
                    }
                    if (move == PuppetMove.BOW && player.isUsingItem()) {
                        progress = net.minecraft.world.item.BowItem.getPowerForTime(player.getTicksUsingItem());
                    } else if (move == PuppetMove.CROSSBOW && player.isUsingItem()) {
                        progress = Math.min(1.0F, player.getTicksUsingItem()
                                / (float) net.minecraft.world.item.CrossbowItem.getChargeDuration(player.getUseItem(), player));
                    } else if (hold > 0) {
                        progress = switch (move) {
                            case FUSE -> hold / (float) Math.max(1, fuseTicks());
                            case EGG -> Math.min(1.0F, hold / 20.0F);
                            case BEAM -> Math.min(1.0F, hold / (float) Math.max(1, beamTicks()));
                            case PLAY_DEAD -> -1.0F; // held for as long as you like: nothing to fill
                            case OFFER -> Math.min(1.0F, hold / (float) Math.max(1, poppyTicks()));
                            case VOLLEY -> Math.min(1.0F, hold / (float) Math.max(1, volleyTicks()));
                            case EMBED -> player.getData(WitchModAttachments.PUPPET_HIDDEN) ? -1.0F
                                    : Math.min(1.0F, hold / (float) Math.max(1, embedTicks()));
                            case MAUL -> -1.0F; // mid-scrap: no bar
                            case PERCH, CURL -> -1.0F; // a dance, a curl: nothing to fill
                            case DIVE -> com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.hudProgress(player); // its teleport
                            case SHELL -> com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.hudProgress(player); // its volley
                            case WARDEN_BELT -> com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour.hudProgress(player);
                            case SNIFF_DIG -> Math.min(1.0F, hold / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_SNIFFER_SEED_TICKS.get()));
                            case LAY_EGGS -> Math.min(1.0F, hold / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_TURTLE_LAY_CHARGE_TICKS.get()));
                            case PUFF -> com.oliver.witchmod.effects.blessings.puppeteer.mob.FishBehaviour.puffLevel(player) / 2.0F; // how puffed
                            case WOLF_MAUL -> PuppetMovement.holdingStill(player) ? -1.0F
                                    : Math.min(1.0F, hold / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_WOLF_MAUL_CHARGE_TICKS.get()));
                            case LUNGE_HEAVY -> Math.min(1.0F, hold / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_VINDICATOR_LUNGE_CHARGE_TICKS.get()));
                            case FIREBALL, BLAZE_VOLLEY, POTION, DASH, RAM, ROAR -> Math.max(0.0F, PuppetCamera.shotCharge(player));
                            case BREEZE_DASH -> hold > 0 ? Math.max(0.0F, PuppetCamera.shotCharge(player)) : -1.0F; // its gale
                            case PIGLIN_CALL -> -1.0F; // a tap or a dance: nothing to fill
                            case SPELL -> Math.min(1.0F, hold / (float) com.oliver.witchmod.effects.blessings.puppeteer.mob.EvokerBehaviour
                                    .chargeTicks(com.oliver.witchmod.effects.blessings.puppeteer.mob.EvokerBehaviour.evokerSpell(player)));
                            case MITOSIS -> Math.min(1.0F, hold / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_ALLAY_MITOSIS_CHARGE_TICKS.get()));
                            default -> Math.min(1.0F, hold / 10.0F);
                        };
                    }
                }
                lines.add(new Line(List.of(sneak, use), Component.translatable("witchmod.puppeteer.leave"), true));
            } else if (player.getData(WitchModAttachments.PUPPETEER_ACTIVE) && lookingAtPuppet(mc)) {
                long ready = player.getData(WitchModAttachments.PUPPET_COOLDOWN_END);
                lines.add(moveLine(List.of(sneak, use), "witchmod.puppeteer.possess", ready, now));
            }
            if (lines.isEmpty()) {
                return;
            }
            g.pose().pushPose();
            // drawn at 85% size, anchored to the bottom-right corner — present, but out of the way.
            g.pose().translate(g.guiWidth(), g.guiHeight(), 0.0F);
            g.pose().scale(SCALE, SCALE, 1.0F);
            draw(g, mc.font, lines, Mth.clamp(progress, -1.0F, 1.0F));
            g.pose().popPose();
        }

        /** a control line in your own key names — "[Right Button] Rally" — greyed with the seconds left while it recharges. */
        private static Line line(Minecraft mc, PuppetControls.Line control, long now) {
            List<Component> keys = new ArrayList<>();
            for (PuppetControls.Input input : control.keys()) {
                keys.add(keyName(mc, input));
            }
            return control.readyAt() > now
                    ? new Line(keys, Component.translatable("witchmod.puppeteer.cooldown_short", control.label(), (control.readyAt() - now + 19) / 20), false)
                    : new Line(keys, control.label(), true);
        }

        /** a key cap's text: the key you've bound (or the wheel), held / double-tapped as the control says. */
        private static Component keyName(Minecraft mc, PuppetControls.Input input) {
            Component key = switch (input.key()) {
                case USE -> mc.options.keyUse.getTranslatedKeyMessage();
                case ATTACK -> mc.options.keyAttack.getTranslatedKeyMessage();
                case SNEAK -> mc.options.keyShift.getTranslatedKeyMessage();
                case JUMP -> mc.options.keyJump.getTranslatedKeyMessage();
                case DROP -> mc.options.keyDrop.getTranslatedKeyMessage();
                case FORWARD -> mc.options.keyUp.getTranslatedKeyMessage();
                case SCROLL -> Component.translatable("witchmod.puppeteer.scroll");
            };
            return switch (input.press()) {
                case TAP -> key;
                case HOLD -> Component.translatable("witchmod.puppeteer.hold", key);
                case DOUBLE_TAP -> Component.translatable("witchmod.puppeteer.double_tap", key);
            };
        }

        private static Line moveLine(List<Component> keys, String labelKey, long ready, long now) {
            Component label = Component.translatable(labelKey);
            return ready > now
                    ? new Line(keys, Component.translatable("witchmod.puppeteer.cooldown_short", label, (ready - now + 19) / 20), false)
                    : new Line(keys, label, true);
        }

        /** draws relative to (0, 0) = the bottom-right corner (the caller translates and scales). */
        private static void draw(GuiGraphics g, Font font, List<Line> lines, float progress) {
            int lineH = 13;
            int pad = 3;
            int width = 0;
            for (Line line : lines) {
                width = Math.max(width, lineWidth(font, line));
            }
            int boxW = width + pad * 2;
            int boxH = lines.size() * lineH + pad * 2 - 2 + (progress >= 0 ? 4 : 0);
            int right = -4;
            int bottom = -4;
            int left = right - boxW;
            int top = bottom - boxH;
            g.fill(left, top, right, bottom, 0x50000000);
            int y = top + pad;
            for (Line line : lines) {
                int x = right - pad - lineWidth(font, line);
                for (int i = 0; i < line.keys().size(); i++) {
                    if (i > 0) {
                        g.drawString(font, "+", x, y + 2, 0x90FFFFFF, false);
                        x += font.width("+") + 2;
                    }
                    Component key = line.keys().get(i);
                    int kw = font.width(key) + 6;
                    // a faint outlined key cap
                    g.fill(x, y, x + kw, y + 1, 0x70FFFFFF);
                    g.fill(x, y + 10, x + kw, y + 11, 0x70FFFFFF);
                    g.fill(x, y, x + 1, y + 11, 0x70FFFFFF);
                    g.fill(x + kw - 1, y, x + kw, y + 11, 0x70FFFFFF);
                    g.drawString(font, key, x + 3, y + 2, 0xD0FFFFFF, false);
                    x += kw + 4;
                }
                g.drawString(font, line.action(), x, y + 2, line.ready() ? 0xD0FFFFFF : 0x80A0A0A0, false);
                y += lineH;
            }
            if (progress >= 0) {
                int barTop = bottom - pad - 2;
                g.fill(left + pad, barTop, right - pad, barTop + 2, 0x40FFFFFF);
                g.fill(left + pad, barTop, left + pad + Math.round((boxW - pad * 2) * progress), barTop + 2, 0xB07FE6FF);
            }
        }

        private static int lineWidth(Font font, Line line) {
            int w = font.width(line.action());
            for (int i = 0; i < line.keys().size(); i++) {
                w += font.width(line.keys().get(i)) + 6 + 4;
                if (i > 0) {
                    w += font.width("+") + 2;
                }
            }
            return w;
        }

        private static boolean lookingAtPuppet(Minecraft mc) {
            Entity target = mc.crosshairPickEntity;
            return target != null && PuppetType.of(target.getType()) != null;
        }

        /** the possession being shown: its end, and its whole length. */
        private static long seenBindingEnd;
        private static long bindingTotal = 1L;

        private static int embedTicks() {
            try {
                return Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get();
            } catch (IllegalStateException e) {
                return 30;
            }
        }

        private static int volleyTicks() {
            try {
                return Config.PUPPETEER_SNOW_GOLEM_VOLLEY_CHARGE_TICKS.get();
            } catch (IllegalStateException e) {
                return 30;
            }
        }

        private static int poppyTicks() {
            try {
                return Config.PUPPETEER_GOLEM_POPPY_HOLD_TICKS.get();
            } catch (IllegalStateException e) {
                return 20;
            }
        }

        private static int beamTicks() {
            try {
                return Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get();
            } catch (IllegalStateException e) {
                return 60;
            }
        }

        private static int fuseTicks() {
            try {
                return Config.PUPPETEER_CREEPER_FUSE_TICKS.get();
            } catch (IllegalStateException e) {
                return 30;
            }
        }
    }
}
