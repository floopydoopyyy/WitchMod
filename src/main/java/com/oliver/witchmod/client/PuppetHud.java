package com.oliver.witchmod.client;

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
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;

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
        boolean belt = player != null && BlessingPuppeteer.PuppetType.byId(PuppeteerClient.typeOf(player)) == BlessingPuppeteer.PuppetType.WITCH
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
            BlessingPuppeteer.PuppetType puppetType = BlessingPuppeteer.PuppetType.byId(PuppeteerClient.typeOf(player));
            if (puppetType != null) {
                Component name = puppetType == BlessingPuppeteer.PuppetType.KILLER_RABBIT
                        ? Component.translatable("witchmod.puppeteer.killer_rabbit")
                        : puppetType == BlessingPuppeteer.PuppetType.SCREAMING_GOAT ? Component.translatable("witchmod.puppeteer.screaming_goat")
                        : puppetType.entityType().getDescription();
                g.drawString(mc.font, name, x, y - (rows - 1) * rowHeight - 10, 0xFF9EE7A0, true);
            }
            mc.gui.leftHeight += (rows - 1) * rowHeight + 10;
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
            if (bindingEnd > now) {
                int total = Math.max(1, possessTicks());
                progress = 1.0F - Mth.clamp((bindingEnd - now - deltaTracker.getGameTimeDeltaPartialTick(false)) / total, 0.0F, 1.0F);
                lines.add(new Line(List.of(), Component.translatable("witchmod.puppeteer.possessing"), true));
            } else if (!type.isEmpty()) {
                BlessingPuppeteer.PuppetType puppet = BlessingPuppeteer.PuppetType.byId(type);
                long ready = player.getData(WitchModAttachments.PUPPET_ACTION_READY);
                int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
                if (puppet != null) {
                    BlessingPuppeteer.Move move = puppet.move();
                    if (move == BlessingPuppeteer.Move.EGG) {
                        lines.add(moveLine(List.of(use), "witchmod.puppeteer.action.lay_egg", ready, now));
                    }
                    if (move == BlessingPuppeteer.Move.VOLLEY) {
                        lines.add(moveLine(List.of(use), "witchmod.puppeteer.action.snowball", ready, now));
                    }
                    long moveReady = switch (move) {
                        case FUSE, BOW, CROSSBOW -> 0L;                                     // no cooldown
                        case EGG, VOLLEY -> player.getData(WitchModAttachments.PUPPET_ACTION2_READY); // the second move's own
                        default -> ready;
                    };
                    // the move's key: right-click (pressed or held), except a spider's pounce (your attack button) and a
                    // bat's flight (double-tap jump).
                    Component key = switch (move) {
                        case POUNCE -> mc.options.keyAttack.getTranslatedKeyMessage();
                        case FLY -> Component.translatable("witchmod.puppeteer.double_tap", mc.options.keyJump.getTranslatedKeyMessage());
                        default -> move.held() ? Component.translatable("witchmod.puppeteer.hold", use) : use;
                    };
                    if (move == BlessingPuppeteer.Move.EMBED && player.getData(WitchModAttachments.PUPPET_HIDDEN)) {
                        // hidden in stone: the brood that'll burst out with you grows the longer you wait
                        int brood = Math.max(0, (hold - embedTicks()) / Math.max(1, broodTicks()));
                        lines.add(new Line(List.of(use), Component.translatable("witchmod.puppeteer.action.burst", brood), true));
                    } else if (move == BlessingPuppeteer.Move.TELEPORT) {
                        // charges: usable whenever one's ready, even while the next recharges
                        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
                        lines.add(charges > 0
                                ? new Line(List.of(use), Component.translatable("witchmod.puppeteer.action.teleport_charges", charges), true)
                                : moveLine(List.of(use), move.labelKey(), ready, now));
                        lines.add(new Line(List.of(sneak, use), Component.translatable("witchmod.puppeteer.action.carry"), true));
                        if (BlessingPuppeteer.enraged(player)) {
                            long left = player.getData(WitchModAttachments.PUPPET_RAGE_END) - now;
                            lines.add(new Line(List.of(), Component.translatable("witchmod.puppeteer.enraged", (left + 19) / 20)
                                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true));
                        }
                    } else if (move == BlessingPuppeteer.Move.BURROW && player.getData(WitchModAttachments.PUPPET_BURROWED)) {
                        lines.add(moveLine(List.of(key), "witchmod.puppeteer.action.surface", moveReady, now));
                    } else if (move != BlessingPuppeteer.Move.NONE && move != BlessingPuppeteer.Move.SPELL) { // a wither skeleton / plain rabbit has no special move
                        lines.add(moveLine(List.of(key), move.labelKey(), move == BlessingPuppeteer.Move.FLY ? 0L : moveReady, now));
                    }
                    if (puppet.group() == BlessingPuppeteer.Group.HORSE) {
                        lines.add(new Line(List.of(Component.translatable("witchmod.puppeteer.hold", mc.options.keyJump.getTranslatedKeyMessage())),
                                Component.translatable("witchmod.puppeteer.action.leap"), true));
                        float leap = PuppeteerClient.horseJumpProgress();
                        if (leap > 0.0F) {
                            progress = leap;
                        }
                    }
                    if (puppet.group() == BlessingPuppeteer.Group.RABBIT || puppet.group() == BlessingPuppeteer.Group.SLIME) {
                        lines.add(moveLine(List.of(mc.options.keyUp.getTranslatedKeyMessage()), "witchmod.puppeteer.action.hop", 0L, now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.EVOKER) {
                        // one right-click line that changes with what a release would cast (aim, and how long it's held)
                        BlessingPuppeteer.EvokerSpell spell = BlessingPuppeteer.evokerSpell(player, hold);
                        lines.add(moveLine(List.of(use), spell.labelKey(),
                                spell.second ? player.getData(WitchModAttachments.PUPPET_ACTION2_READY) : ready, now));
                        lines.add(moveLine(List.of(mc.options.keyAttack.getTranslatedKeyMessage()), "witchmod.puppeteer.action.horn",
                                player.getData(WitchModAttachments.PUPPET_RAGE_END), now));
                    }
                    if (BlessingPuppeteer.johnny(player)) {
                        lines.add(new Line(List.of(), Component.translatable("witchmod.puppeteer.johnny")
                                .withStyle(net.minecraft.ChatFormatting.RED), true));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.FOX) {
                        lines.add(moveLine(List.of(mc.options.keyDrop.getTranslatedKeyMessage()), "witchmod.puppeteer.action.drop_item", 0L, now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.WITCH) {
                        lines.add(moveLine(List.of(mc.options.keyAttack.getTranslatedKeyMessage()), "witchmod.puppeteer.action.drink", ready, now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.SCREAMING_GOAT) {
                        lines.add(moveLine(List.of(Component.translatable("witchmod.puppeteer.hold", mc.options.keyAttack.getTranslatedKeyMessage())),
                                "witchmod.puppeteer.action.shriek", player.getData(WitchModAttachments.PUPPET_ACTION2_READY), now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.GHAST) {
                        lines.add(moveLine(List.of(Component.translatable("witchmod.puppeteer.hold", mc.options.keyAttack.getTranslatedKeyMessage())),
                                "witchmod.puppeteer.action.ghast_volley", player.getData(WitchModAttachments.PUPPET_ACTION2_READY), now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.BREEZE) {
                        lines.add(moveLine(List.of(mc.options.keyAttack.getTranslatedKeyMessage()), "witchmod.puppeteer.action.wind_charge",
                                player.getData(WitchModAttachments.PUPPET_ACTION2_READY), now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.PHANTOM) {
                        lines.add(moveLine(List.of(mc.options.keyAttack.getTranslatedKeyMessage()), "witchmod.puppeteer.action.bite", 0L, now));
                        lines.add(moveLine(List.of(Component.translatable("witchmod.puppeteer.double_tap",
                                mc.options.keyJump.getTranslatedKeyMessage())), "witchmod.puppeteer.action.fly", 0L, now));
                    }
                    if (puppet == BlessingPuppeteer.PuppetType.ELDER_GUARDIAN) {
                        lines.add(moveLine(List.of(mc.options.keyAttack.getTranslatedKeyMessage()), "witchmod.puppeteer.action.call",
                                player.getData(WitchModAttachments.PUPPET_ACTION2_READY), now));
                    }
                    if (move == BlessingPuppeteer.Move.BOW && player.isUsingItem()) {
                        progress = net.minecraft.world.item.BowItem.getPowerForTime(player.getTicksUsingItem());
                    } else if (move == BlessingPuppeteer.Move.CROSSBOW && player.isUsingItem()) {
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
                            case FIREBALL, BLAZE_VOLLEY, GALE, POTION, DASH, RAM -> Math.max(0.0F, PuppeteerClient.shotCharge(player));
                            case SPELL -> Math.min(1.0F, hold / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_EVOKER_VEX_HOLD_TICKS.get()));
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
            return target != null && BlessingPuppeteer.PuppetType.of(target.getType()) != null;
        }

        private static int possessTicks() {
            try {
                return Config.PUPPETEER_POSSESS_TICKS.get();
            } catch (IllegalStateException e) {
                return 30;
            }
        }

        private static int ghastVolleyTicks() {
            try {
                return Config.PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS.get();
            } catch (IllegalStateException e) {
                return 25;
            }
        }

        private static int embedTicks() {
            try {
                return Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get();
            } catch (IllegalStateException e) {
                return 30;
            }
        }

        private static int broodTicks() {
            try {
                return Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get();
            } catch (IllegalStateException e) {
                return 40;
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
