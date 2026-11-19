package com.oliver.witchmod.effects.blessings.puppeteer.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetGroup;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.SlimeBehaviour;
import com.oliver.witchmod.network.WitchModNetwork;

import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetDummies.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetMovement.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetSenses.*;

/**
 * client side of puppeteer. a possessed player is drawn as their puppet — a dummy mob loaded from the real mob's
 * synced data (armour, wool, baby, charge...) mirroring their movement, crouch and swings: a zombie only raises its
 * arms while attacking, a drowned raises its trident while charging, a creeper swells with the fuse, a chicken flaps
 * when airborne, a sheep grazes. the first-person hand and inventory screen are blocked, movement is locked while
 * the possession plays out, empty-handed right-clicks are relayed, and held moves report right-click held/released.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppeteerClient {
    static final Map<UUID, String> LAST_TYPE = new HashMap<>();

    static boolean held;

    /** the attack button held (a ghast puppet's volley charge), as last reported. */
    static boolean attackHeld;

    /** crouch+use held last tick (the witch leave-assist edge detector). */
    static boolean witchLeavePrev;

    private PuppeteerClient() {}

    static String typeOf(Player player) {
        return player.getData(WitchModAttachments.PUPPET_TYPE);
    }

    /** a swing at thin air never reaches the server — report it for any puppet whose LEFT-click does something. */
    @SubscribeEvent
    static void onSwingKey(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        PuppetType own = player == null ? null : PuppetType.byId(typeOf(player));
        if (event.isAttack() && own != null && own.behaviour().usesLeftClick(own)) {
            WitchModNetwork.sendPuppetSwing();
        }
    }

    /** a puppet with a belt (an evoker's spells, a warden's abilities) scrolls it instead of the hotbar. */
    @SubscribeEvent
    static void onScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        PuppetType own = mc.player == null ? null : PuppetType.byId(typeOf(mc.player));
        if (own == null || mc.screen != null || mc.player.isShiftKeyDown() || !own.behaviour().scrolls(own)) {
            return;
        }
        double dy = event.getScrollDeltaY();
        if (dy != 0.0) {
            event.setCanceled(true);
            WitchModNetwork.sendPuppetSpell(dy > 0 ? -1 : 1);
        }
    }

    static boolean binding(Player player) {
        return player.getData(WitchModAttachments.PUPPET_BINDING_END) > player.level().getGameTime();
    }

    /** no inventory while you're a puppet — there's nothing in it anyway (it's stashed), and nothing to use. */
    @SubscribeEvent
    static void onScreenOpen(ScreenEvent.Opening event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && (!typeOf(player).isEmpty() || binding(player))
                && (event.getNewScreen() instanceof InventoryScreen || event.getNewScreen() instanceof CreativeModeInventoryScreen)) {
            event.setCanceled(true);
        }
    }

    /** a fox lets go of what's in its mouth with the drop key (its inventory is stashed, so vanilla's drop does nothing). */
    @SubscribeEvent
    static void onFoxDropKey(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null
                || PuppetType.byId(typeOf(mc.player)) != PuppetType.FOX) {
            return;
        }
        boolean pressed = false;
        while (mc.options.keyDrop.consumeClick()) {
            pressed = true;
        }
        if (pressed) {
            WitchModNetwork.sendPuppetDrop();
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PuppetDummies.clearAll();
            LAST_TYPE.clear();
            held = false;
            PuppetMovement.reset();
            return;
        }
        PuppetType own = mc.player == null ? null : PuppetType.byId(typeOf(mc.player));
        // a puppet's left-click is always a puppet action (never real mining), but vanilla's 10-tick miss cooldown eats
        // the click after a swing at air or an out-of-reach entity — so clear it each tick, or e.g. a wolf only pounced
        // and a vex only lunged when aimed at a block.
        if (own != null) {
            ((com.oliver.witchmod.mixin.MinecraftMissTimeAccessor) mc).witchmodSetMissTime(0);
        }
        // held moves (creeper fuse, drowned trident, chicken egg): report right-click held / let go, on change.
        boolean want = own != null && own.move().held() && !own.move().vanillaUse() && mc.screen == null
                && mc.options.keyUse.isDown() && !mc.player.isShiftKeyDown();
        // a held LEFT-click move (a ghast's volley, a screaming goat's shriek): report that held / let go too.
        boolean wantAttack = own != null && own.behaviour().holdsAttack(own)
                && mc.screen == null && mc.options.keyAttack.isDown();
        if (wantAttack != attackHeld) {
            attackHeld = wantAttack;
            WitchModNetwork.sendPuppetAttackHold(wantAttack);
        }
        if (want != held) {
            held = want;
            WitchModNetwork.sendPuppetFuse(want);
        }
        // the witch's potions sit on a vanilla cooldown, which suppresses the right-click interaction — so crouch+use
        // (leave) gets swallowed while they recharge. detect it here and request the leave directly.
        boolean witchLeave = own == PuppetType.WITCH && mc.screen == null
                && mc.options.keyUse.isDown() && mc.player != null && mc.player.isShiftKeyDown();
        if (witchLeave && !witchLeavePrev) {
            WitchModNetwork.sendPuppetAction();
        }
        witchLeavePrev = witchLeave;
        // the camera follows the puppet's eye height: re-measure anyone whose (synced) puppet changed.
        for (Player p : mc.level.players()) {
            String type = typeOf(p);
            PuppetType puppet = PuppetType.byId(type);
            if (puppet != null && puppet.group() == PuppetGroup.SLIME) {
                type += "#" + SlimeBehaviour.slimeSize(p); // a slime that split is smaller: re-measure then too
            }
            if (puppet == PuppetType.WARDEN) {
                // its eyes sink as it digs, sit at the floor below ground, rise as it climbs out
                type += "#" + com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour.state(p) + "#"
                        + Math.round(com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour.digProgress(p, 0.0F) * 30.0F);
            }
            if (!type.equals(LAST_TYPE.put(p.getUUID(), type))) {
                p.refreshDimensions();
            }
        }
        if (mc.player != null) {
            PuppetMovement.tickLocal(mc, own);
            PuppetSenses.tickStarers(mc);
            PuppetSenses.tickShulker(mc);
            PuppetSenses.tickEnderMarker(mc);
        }
        PuppetDummies.tickAnimations(mc);
    }

    @SubscribeEvent
    static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && !typeOf(event.getEntity()).isEmpty()) {
            WitchModNetwork.sendPuppetAction();
        }
    }
}
