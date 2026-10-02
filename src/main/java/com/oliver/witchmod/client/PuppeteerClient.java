package com.oliver.witchmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;
import com.oliver.witchmod.mixin.CreeperAccessor;
import com.oliver.witchmod.mixin.GuardianAccessor;
import com.oliver.witchmod.mixin.SheepAccessor;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * client side of puppeteer. a possessed player is drawn as their puppet — a dummy mob loaded from the real mob's
 * synced data (armour, wool, baby, charge...) mirroring their movement, crouch and swings: a zombie only raises its
 * arms while attacking, a drowned raises its trident while charging, a creeper swells with the fuse, a chicken flaps
 * when airborne, a sheep grazes. the first-person hand and inventory screen are blocked, movement is locked while
 * the possession plays out, empty-handed right-clicks are relayed, and held moves report right-click held/released.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppeteerClient {
    /** a zombie keeps its arms raised this long after a swing, then lowers them back to the normal shamble. */
    private static final int ARMS_UP_TICKS = 15;

    private static final ItemStack TRIDENT = new ItemStack(Items.TRIDENT);
    private static final Map<UUID, Mob> PUPPETS = new HashMap<>();
    private static final Map<UUID, CompoundTag> LOADED = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SWING = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FUSE = new HashMap<>();
    private static final Map<UUID, Integer> PREV_FUSE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, String> LAST_TYPE = new HashMap<>();
    private static final java.util.Set<UUID> WAS_AIRBORNE = new java.util.HashSet<>();
    private static final java.util.Set<UUID> HORSE_LEAPING = new java.util.HashSet<>();
    /** a ghast puppet keeps its mouth open until this tick (just fired). */
    private static final Map<UUID, Integer> GHAST_FACE = new HashMap<>();
    /** a breeze puppet plays its shoot animation until this tick (just fired). */
    private static final Map<UUID, Integer> BREEZE_SHOT = new HashMap<>();
    /** a camel puppet plays its dash animation until this tick. */
    private static final Map<UUID, Integer> CAMEL_DASH = new HashMap<>();

    /** your shot's charge as 0..1 (ghast volley / blaze volley / breeze gale), or -1 if you're not winding one up. */
    public static float shotCharge(Player p) {
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        int hold = p.getData(WitchModAttachments.PUPPET_FUSE);
        if (hold <= 0 || t == null) {
            return -1.0F;
        }
        try {
            int full = switch (t) {
                case GHAST -> com.oliver.witchmod.Config.PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS.get();
                case BLAZE -> com.oliver.witchmod.Config.PUPPETEER_BLAZE_CHARGE_TICKS.get();
                case BREEZE -> com.oliver.witchmod.Config.PUPPETEER_BREEZE_GALE_CHARGE_TICKS.get();
                case WITCH -> com.oliver.witchmod.Config.PUPPETEER_WITCH_LINGER_CHARGE_TICKS.get();
                case CAMEL -> com.oliver.witchmod.Config.PUPPETEER_CAMEL_DASH_CHARGE_TICKS.get();
                case GOAT -> com.oliver.witchmod.Config.PUPPETEER_GOAT_RAM_CHARGE_TICKS.get();
                case SCREAMING_GOAT -> p == Minecraft.getInstance().player && attackHeld
                        ? com.oliver.witchmod.Config.PUPPETEER_GOAT_SHRIEK_CHARGE_TICKS.get() : com.oliver.witchmod.Config.PUPPETEER_GOAT_RAM_CHARGE_TICKS.get();
                default -> 0;
            };
            return full <= 0 ? -1.0F : Math.min(1.0F, hold / (float) full);
        } catch (IllegalStateException e) {
            return -1.0F;
        }
    }

    /** winding up a shot narrows your view a little, like drawing a bow. */
    @SubscribeEvent
    static void onShotFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        float charge = shotCharge(event.getPlayer());
        BlessingPuppeteer.PuppetType who = BlessingPuppeteer.PuppetType.byId(typeOf(event.getPlayer()));
        if (charge >= 0.0F && who != BlessingPuppeteer.PuppetType.CAMEL) { // (a camel's dash charge is a jump bar, not an aim)
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F - 0.15F * charge * charge));
        }
    }
    private static boolean held;
    /** the attack button held (a ghast puppet's volley charge), as last reported. */
    private static boolean attackHeld;
    /** the lunge's current heading (your own), and the lunge it belongs to. */
    private static net.minecraft.world.phys.Vec3 lungeDir;
    private static long lungeFor;
    /** a lunge you ran into a wall with — stopped here, before the server's own timer ends it. */
    private static long lungeStopped;

    private PuppeteerClient() {}

    static String typeOf(Player player) {
        return player.getData(WitchModAttachments.PUPPET_TYPE);
    }

    /** a spider-family puppet — climbs walls like the Spider blessing (see ClientCurseHandler.tickSpider). */
    public static boolean isSpiderPuppet(Player player) {
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(player));
        return t != null && t.group() == BlessingPuppeteer.Group.SPIDER;
    }

    /** a bat puppet — flies like the bat disguise (see DisguiseClient.tickBatFlightSpeed). */
    public static boolean isBatPuppet(Player player) {
        return BlessingPuppeteer.PuppetType.byId(typeOf(player)) == BlessingPuppeteer.PuppetType.BAT;
    }

    /**
     * a flying puppet (bat, phantom) — flies like the bat disguise: slow, no sprint-flying, unless the Flight blessing
     * unlocks a fast sprint glide (see DisguiseClient.tickBatFlightSpeed).
     */
    public static boolean isFlyingPuppet(Player player) {
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(player));
        return t == BlessingPuppeteer.PuppetType.BAT || t == BlessingPuppeteer.PuppetType.PHANTOM || t == BlessingPuppeteer.PuppetType.GHAST
                || t == BlessingPuppeteer.PuppetType.BLAZE || t == BlessingPuppeteer.PuppetType.BREEZE;
    }

    /**
     * a flying puppet's own fly speed (normal or the Flight blessing's sprint), slowed while winding up a shot — or null
     * for the bat (it uses the bat disguise's speeds).
     */
    public static Double flySpeed(Player p, boolean sprint) {
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        if (t == null) {
            return null;
        }
        Double speed = switch (t) {
            case PHANTOM -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_PHANTOM_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_PHANTOM_FLY_SPEED).get();
            case GHAST -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_GHAST_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_GHAST_FLY_SPEED).get();
            case BLAZE -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_BLAZE_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_BLAZE_FLY_SPEED).get();
            case BREEZE -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_BREEZE_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_BREEZE_FLY_SPEED).get();
            default -> null;
        };
        return speed != null && shotCharge(p) >= 0.0F ? speed * 0.4 : speed;
    }

    /** a slow flier — never allowed to sprint-fly, Flight blessing or not (vanilla's sprint doubles fly speed). */
    public static boolean isSlowFlier(Player player) {
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(player));
        return t == BlessingPuppeteer.PuppetType.GHAST || t == BlessingPuppeteer.PuppetType.BLAZE || t == BlessingPuppeteer.PuppetType.BREEZE;
    }

    /** a ghast puppet — always flying, slowly (see DisguiseClient.tickBatFlightSpeed for its speed). */
    public static boolean isGhastPuppet(Player player) {
        return BlessingPuppeteer.PuppetType.byId(typeOf(player)) == BlessingPuppeteer.PuppetType.GHAST;
    }

    /** spiders pounce on every swing — a swing at thin air never reaches the server, so report it. */
    @SubscribeEvent
    static void onSwingKey(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        BlessingPuppeteer.PuppetType own = player == null ? null : BlessingPuppeteer.PuppetType.byId(typeOf(player));
        if (event.isAttack() && player != null && (isSpiderPuppet(player)
                || own == BlessingPuppeteer.PuppetType.ELDER_GUARDIAN || own == BlessingPuppeteer.PuppetType.BREEZE
                || own == BlessingPuppeteer.PuppetType.WITCH || own == BlessingPuppeteer.PuppetType.EVOKER)) {
            WitchModNetwork.sendPuppetSwing();
        }
    }

    static boolean binding(Player player) {
        return player.getData(WitchModAttachments.PUPPET_BINDING_END) > player.level().getGameTime();
    }

    /** runs before the disguise renderer so a puppet always wins. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Mob m = puppetFor(p);
        if (m == null) {
            return;
        }
        event.setCanceled(true);
        if (isHidden(p)) {
            return; // a silverfish inside its stone: nothing to see
        }
        float pt = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        m.setYRot(bodyYaw);
        m.yRotO = bodyYaw;
        m.yBodyRot = bodyYaw;
        m.yBodyRotO = bodyYaw;
        m.yHeadRot = headYaw;
        m.yHeadRotO = headYaw;
        m.setXRot(pitch);
        m.xRotO = pitch;
        m.setPos(p.getX(), p.getY(), p.getZ());
        m.xo = p.xo;
        m.yo = p.yo;
        m.zo = p.zo;
        m.setOnGround(p.onGround());
        m.setPose(p.getPose());
        m.attackAnim = p.attackAnim;
        m.oAttackAnim = p.oAttackAnim;
        m.swinging = p.swinging;
        // burning shows as on the real mob: vanilla wipes a player's fire ticks client-side, so go by the synced flag. a
        // fire-proof mob (blaze, ghast, magma cube...) never shows flames, as its own isOnFire says.
        m.setSharedFlagOnFire(p.isOnFire());
        m.hurtTime = p.hurtTime; // the red flinch when the puppet takes a hit
        m.hurtDuration = p.hurtDuration;
        int hold = p.getData(WitchModAttachments.PUPPET_FUSE);
        boolean recentSwing = p.tickCount - LAST_SWING.getOrDefault(p.getUUID(), -1000) < ARMS_UP_TICKS;
        // arms: a drowned raises its trident only while charging a throw, a skeleton aims only while drawing its
        // bow (the vanilla poses — both models key off "aggressive" + the held item), zombies after a swing.
        if (m instanceof Drowned) {
            m.setItemSlot(EquipmentSlot.MAINHAND, TRIDENT); // a possessed drowned always has its trident
            m.setAggressive(hold > 0);
        } else if (m instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                && !(m instanceof net.minecraft.world.entity.monster.WitherSkeleton)) { // a wither skeleton keeps its sword
            if (!m.getMainHandItem().is(Items.BOW)) {
                m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
            m.setAggressive(p.isUsingItem());
        } else {
            m.setAggressive(recentSwing);
        }
        if (m instanceof net.minecraft.world.entity.ambient.Bat bat) {
            bat.setResting(false); // a bat puppet is always on the wing, flapping
            bat.flyAnimationState.animateWhen(true, bat.tickCount);
            bat.restAnimationState.stop();
        }
        if (m instanceof net.minecraft.world.entity.monster.Ghast ghast) {
            // the open-mouthed charging face while you hold a shot, and for a moment after it flies
            // the open-mouthed face only as it actually fires (and a moment after) — not while charging
            ghast.setCharging(p.tickCount < GHAST_FACE.getOrDefault(p.getUUID(), 0));
        }
        if (m instanceof net.minecraft.world.entity.monster.Blaze blaze) {
            // a blaze in a fight lights up (and its fire shows), like the real one attacking
            ((com.oliver.witchmod.mixin.BlazeAccessor) blaze).witchmodSetCharged(
                    p.getData(WitchModAttachments.PUPPET_RAGE_END) > p.level().getGameTime() || hold > 0);
        }
        if (m instanceof net.minecraft.world.entity.monster.breeze.Breeze breeze) {
            // its own animations: idling always, drawing breath while a gale charges, the shot on each wind charge
            breeze.idle.startIfStopped(p.tickCount);
            if (hold > 0) {
                breeze.inhale.startIfStopped(p.tickCount);
            } else {
                breeze.inhale.stop();
            }
            if (p.tickCount < BREEZE_SHOT.getOrDefault(p.getUUID(), 0)) {
                breeze.shoot.startIfStopped(p.tickCount);
            } else {
                breeze.shoot.stop();
            }
        }
        if (m instanceof net.minecraft.world.entity.monster.Evoker evoker) {
            // arms up while a spell winds up (the spell's id — vanilla's own numbering — in its casting flag)
            evoker.getEntityData().set(com.oliver.witchmod.mixin.SpellcasterIllagerAccessor.witchmodSpellKey(),
                    (byte) p.getData(WitchModAttachments.PUPPET_DASH_POWER).intValue());
        }
        if (m instanceof net.minecraft.world.entity.animal.Fox fox) {
            fox.setIsCrouching(p.isCrouching()); // the stalking crouch
            fox.setIsPouncing(lungeTick(p) >= 0); // and the pounce
        }
        if (m instanceof net.minecraft.world.entity.monster.Pillager pillager) {
            // its own crossbow poses: aimed while held, drawn back while loading
            if (!pillager.getMainHandItem().is(Items.CROSSBOW)) {
                pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            }
            pillager.setChargingCrossbow(p.isUsingItem());
        }
        if (m instanceof net.minecraft.world.entity.monster.Witch witch) {
            // the witch's swig: the potion up at her mouth (the one picked on your belt) while you drink
            boolean drinking = BlessingPuppeteer.drinking(p);
            witch.setUsingItem(drinking);
            witch.setItemSlot(EquipmentSlot.MAINHAND, drinking ? p.getMainHandItem().copy() : ItemStack.EMPTY);
        }
        if (m instanceof net.minecraft.world.entity.animal.camel.Camel camel) {
            if (p.tickCount < CAMEL_DASH.getOrDefault(p.getUUID(), 0)) {
                camel.dashAnimationState.startIfStopped(p.tickCount);
            } else {
                camel.dashAnimationState.stop();
            }
        }
        if (m instanceof net.minecraft.world.entity.animal.goat.Goat goat) {
            // head down while charging a ram (or a shriek) and through the ram itself, up again after — its own curve
            com.oliver.witchmod.mixin.GoatAccessor acc = (com.oliver.witchmod.mixin.GoatAccessor) goat;
            boolean lowering = hold > 0 || lungeTick(p) >= 0;
            acc.witchmodSetLowerHeadTick(Mth.clamp(acc.witchmodGetLowerHeadTick() + (lowering ? 1 : -2), 0, 20));
        }
        if (m instanceof net.minecraft.world.entity.monster.EnderMan) {
            // enraged: the jaw drops and it shakes, as a real one does when stared at
            m.getEntityData().set(com.oliver.witchmod.mixin.EnderManAccessor.witchmodCreepyKey(), BlessingPuppeteer.enraged(p));
        }
        if (m instanceof Creeper creeper) {
            ((CreeperAccessor) creeper).witchmodSetOldSwell(PREV_FUSE.getOrDefault(p.getUUID(), hold));
            ((CreeperAccessor) creeper).witchmodSetSwell(hold);
            creeper.getEntityData().set(CreeperAccessor.witchmodPoweredKey(), p.getData(WitchModAttachments.PUPPET_CHARGED));
        }
        // a fish swims upright in water and lies on its side out of it (the renderer reads its water state); on
        // land it also writhes, like the disguise fish.
        // models that read the mob's own motion (dolphin tilt, axolotl swim / walk / lie-still) get the player's.
        m.setDeltaMovement(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
        if (m instanceof net.minecraft.world.entity.animal.Dolphin) {
            DisguiseClient.mirrorWaterState(m, p.isInWater());
        }
        if (m instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl) {
            DisguiseClient.mirrorWaterState(m, p.isInWater());
            axolotl.setPlayingDead(hold > 0); // flops over belly-up while playing dead
        }
        boolean flop = false;
        if (m instanceof net.minecraft.world.entity.animal.AbstractFish) {
            DisguiseClient.mirrorWaterState(m, p.isInWater());
            flop = !p.isInWater();
        }
        PoseStack pose = event.getPoseStack();
        // a killer bunny mid-scrap thrashes about in its fight cloud; a ghast / blaze / breeze winding up a shot trembles
        BlessingPuppeteer.PuppetType shaker = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        boolean thrash = hold > 0 && shaker == BlessingPuppeteer.PuppetType.KILLER_RABBIT;
        boolean tremble = hold > 0 && (shaker == BlessingPuppeteer.PuppetType.GHAST || shaker == BlessingPuppeteer.PuppetType.BLAZE
                || shaker == BlessingPuppeteer.PuppetType.BREEZE || shaker == BlessingPuppeteer.PuppetType.SCREAMING_GOAT);
        if (flop) {
            float t = (p.tickCount + pt) * 0.9F;
            pose.pushPose();
            pose.translate(0.0, 0.15, 0.0);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(Mth.sin(t) * 22.0F));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.cos(t * 1.3F) * 20.0F));
        } else if (thrash) {
            float t = (p.tickCount + pt) * 2.7F;
            pose.pushPose();
            pose.translate(Mth.sin(t * 1.7F) * 0.12, Math.abs(Mth.sin(t)) * 0.2, Mth.cos(t * 1.3F) * 0.12);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(Mth.sin(t * 0.9F) * 40.0F));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.cos(t * 1.1F) * 25.0F));
        } else if (tremble) {
            // a slight shake that builds as the charge does
            float t = (p.tickCount + pt) * 3.1F;
            float amount = 0.02F + 0.04F * Math.min(1.0F, hold / 30.0F);
            pose.pushPose();
            pose.translate(Mth.sin(t * 1.9F) * amount, Mth.cos(t * 2.3F) * amount * 0.5F, Mth.sin(t * 1.3F + 1.0F) * amount);
        }
        render(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(m), m, bodyYaw, pt,
                pose, event.getMultiBufferSource(), event.getPackedLight());
        if (flop || thrash || tremble) {
            pose.popPose();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void render(EntityRenderer renderer, Mob m, float yaw, float pt, PoseStack pose,
                               MultiBufferSource buffer, int light) {
        renderer.render(m, yaw, pt, pose, buffer, light);
    }

    /** the cached dummy for a possessed player (reloaded whenever the puppet's data changes), or null. */
    private static Mob puppetFor(Player p) {
        String type = typeOf(p);
        if (type.isEmpty()) {
            PUPPETS.remove(p.getUUID());
            LOADED.remove(p.getUUID());
            return null;
        }
        BlessingPuppeteer.PuppetType puppet = BlessingPuppeteer.PuppetType.byId(type);
        EntityType<?> want = puppet == null ? null : puppet.entityType(); // (not byString: the killer rabbit has its own id)
        if (want == null) {
            return null;
        }
        Mob m = PUPPETS.get(p.getUUID());
        if (m == null || m.getType() != want) {
            if (!(want.create(p.level()) instanceof Mob mob)) {
                return null;
            }
            m = mob;
            PUPPETS.put(p.getUUID(), m);
            LOADED.remove(p.getUUID());
        }
        CompoundTag tag = p.getData(WitchModAttachments.PUPPET_DATA);
        if (!tag.isEmpty() && !tag.equals(LOADED.get(p.getUUID()))) {
            try {
                m.load(tag.copy()); // the real mob's look: armour, held items, wool, baby, charge, profession...
            } catch (RuntimeException ignored) {
                // unreadable data just leaves the default look
            }
            LOADED.put(p.getUUID(), tag.copy());
        }
        return m;
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || typeOf(player).isEmpty()) {
            return;
        }
        event.setCanceled(true); // a puppet has no hands
        // ...but a fox can see what it's carrying: the item in its mouth, low in the middle of your view
        if (event.getHand() == InteractionHand.MAIN_HAND && BlessingPuppeteer.PuppetType.byId(typeOf(player)) == BlessingPuppeteer.PuppetType.FOX) {
            net.minecraft.nbt.ListTag hands = player.getData(WitchModAttachments.PUPPET_DATA).getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
            ItemStack carried = hands.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(player.registryAccess(), hands.getCompound(0));
            if (!carried.isEmpty()) {
                PoseStack pose = event.getPoseStack();
                pose.pushPose();
                pose.translate(0.0, -0.42, -0.72);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20.0F));
                pose.scale(0.65F, 0.65F, 0.65F);
                Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, carried,
                        net.minecraft.world.item.ItemDisplayContext.GROUND, false, pose, event.getMultiBufferSource(), event.getPackedLight());
                pose.popPose();
            }
        }
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

    /**
     * sitting still on purpose: a guardian channelling its beam (easier tracking) or an axolotl playing dead —
     * right-click held (and off cooldown), or the move already running.
     */
    static boolean holdingStill(Player p) {
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        if (t == BlessingPuppeteer.PuppetType.KILLER_RABBIT || t == BlessingPuppeteer.PuppetType.SILVERFISH) {
            return p.getData(WitchModAttachments.PUPPET_FUSE) > 0; // mid-scrap / burrowing in or hidden
        }
        if (t == null || (t.move() != BlessingPuppeteer.Move.BEAM && t.move() != BlessingPuppeteer.Move.PLAY_DEAD)) {
            return false;
        }
        return p.getData(WitchModAttachments.PUPPET_FUSE) > 0
                || (p == Minecraft.getInstance().player && held
                && p.level().getGameTime() >= p.getData(WitchModAttachments.PUPPET_ACTION_READY));
    }

    /** ticks into the current lunge (wind-up then charge), or -1 if not lunging (or the charge was stopped locally). */
    static long lungeTick(Player p) {
        long start = p.getData(WitchModAttachments.PUPPET_LUNGE_START);
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        if (start == 0L || t == null || start == lungeStopped
                || (t.move() != BlessingPuppeteer.Move.LUNGE && t != BlessingPuppeteer.PuppetType.PHANTOM
                && t != BlessingPuppeteer.PuppetType.KILLER_RABBIT && t.group() != BlessingPuppeteer.Group.GOAT
                && t != BlessingPuppeteer.PuppetType.FOX)) {
            return -1L;
        }
        long tick = p.level().getGameTime() - start;
        try {
            return tick >= 0 && tick < BlessingPuppeteer.lungeWindup(t) + BlessingPuppeteer.lungeTicks(p, t) ? tick : -1L;
        } catch (IllegalStateException e) {
            return -1L;
        }
    }

    /** your own movement keys do nothing: mid-possession, channelling a beam (anchored), or winding up / charging a lunge. */
    static boolean anchored(Player p) {
        return binding(p) || holdingStill(p) || lungeTick(p) >= 0;
    }

    /** frozen in place while your soul crosses over, a beam channels, or a lunge runs. */
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        if (anchored(p)) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
            return;
        }
        BlessingPuppeteer.PuppetType own = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        if (own != null && own.group() == BlessingPuppeteer.Group.HORSE) {
            horseInput(p, own, event.getInput());
            return;
        }
        if (BlessingPuppeteer.burrowed(p)) {
            // a burrowing endermite steers itself (tickBurrow) — no walking or jumping
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().jumping = false;
            return;
        }
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(p));
        if (t != null && (t.group() == BlessingPuppeteer.Group.RABBIT || t.group() == BlessingPuppeteer.Group.SLIME)
                && !p.isInWater() && !p.isInLava() && !p.getAbilities().flying) {
            // rabbits and slimes only hop: on the ground a movement key launches a hop (its direction kept for onHop)
            // instead of walking. mid-air the keys still steer, so you never get stuck against a ledge.
            float forward = event.getInput().forwardImpulse;
            float left = event.getInput().leftImpulse;
            int pause = 0;
            if (t.group() == BlessingPuppeteer.Group.SLIME) {
                try {
                    pause = com.oliver.witchmod.Config.PUPPETEER_SLIME_HOP_PAUSE.get(); // the slime's boing... boing rhythm
                } catch (IllegalStateException ignored) {
                    // not synced yet: no pause
                }
            }
            if (p.onGround()) {
                if ((Math.abs(forward) > 0.01F || Math.abs(left) > 0.01F) && slimeGroundTicks >= pause) {
                    event.getInput().jumping = true;
                    float yaw = p.getYRot() * Mth.DEG_TO_RAD;
                    hopDir = new net.minecraft.world.phys.Vec3(-Mth.sin(yaw) * forward + Mth.cos(yaw) * left, 0.0,
                            Mth.cos(yaw) * forward + Mth.sin(yaw) * left).normalize();
                    // (sprint is read from the key: vanilla drops the sprint flag on the ground with no walking input)
                    hopSprint = Minecraft.getInstance().options.keySprint.isDown() || p.isSprinting();
                }
                event.getInput().forwardImpulse = 0.0F;
                event.getInput().leftImpulse = 0.0F;
            }
        }
    }

    /** whether the hop about to be launched is a sprinting one (a rabbit bolting). */
    private static boolean hopSprint;
    /** ticks you've been on the ground (a slime sits squashed a moment between bounces). */
    private static int slimeGroundTicks;

    /** a horse puppet's jump charge (ticks the jump key has been held on the ground). */
    private static int horseCharge;

    /** the horse's jump bar, 0..1, for the hud (0 when not charging). */
    public static float horseJumpProgress() {
        try {
            return horseCharge <= 0 ? 0.0F : Math.min(1.0F, horseCharge / (float) com.oliver.witchmod.Config.PUPPETEER_HORSE_JUMP_CHARGE_TICKS.get());
        } catch (IllegalStateException e) {
            return 0.0F;
        }
    }

    /**
     * horses control like a ridden horse: HOLD jump to charge a leap (vanilla's scale: a tap is a 40% hop, a full bar
     * the whole jump, carried forward if you're moving), sprint freely, and in water they can't swim — no rising, no
     * swim-sprint, and they slowly sink.
     */
    private static void horseInput(Player p, BlessingPuppeteer.PuppetType type, net.minecraft.client.player.Input input) {
        if (p.isInWater() && type == BlessingPuppeteer.PuppetType.SKELETON_HORSE) {
            horseCharge = 0;
            return; // a skeleton horse goes right on underwater: it moves (and rises) as on land, never drowns
        }
        if (p.isInWater()) {
            horseCharge = 0;
            input.jumping = false;
            p.setSprinting(false);
            p.setDeltaMovement(p.getDeltaMovement().add(0.0, -0.02, 0.0));
            return;
        }
        boolean holding = input.jumping;
        input.jumping = false; // never a plain player jump
        if (holding && p.onGround()) {
            horseCharge += BlessingPuppeteer.chargeStep(p); // (dexterous fills the bar quicker)
            return;
        }
        if (!holding && horseCharge > 0) {
            int full;
            double strength;
            try {
                full = com.oliver.witchmod.Config.PUPPETEER_HORSE_JUMP_CHARGE_TICKS.get();
                strength = BlessingPuppeteer.horseJump(type);
            } catch (IllegalStateException e) {
                horseCharge = 0;
                return;
            }
            float scale = horseCharge >= full ? 1.0F : 0.4F + 0.4F * horseCharge / (float) full;
            horseCharge = 0;
            if (!p.onGround()) {
                return;
            }
            net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
            double fx = 0.0;
            double fz = 0.0;
            if (input.forwardImpulse > 0.0F) {
                float yaw = p.getYRot() * Mth.DEG_TO_RAD;
                fx = -0.4 * Mth.sin(yaw) * scale;
                fz = 0.4 * Mth.cos(yaw) * scale;
            }
            p.setDeltaMovement(v.x + fx, strength * scale, v.z + fz);
            p.hasImpulse = true;
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.HORSE_JUMP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.0F, false);
        }
        if (!p.onGround()) {
            horseCharge = 0;
        }
    }

    /** the direction of the hop about to be launched (set from the movement keys, used as the jump fires). */
    private static net.minecraft.world.phys.Vec3 hopDir;

    /** a rabbit's hop: the jump carries it forward at the hop speed (more for the killer bunny). */
    @SubscribeEvent
    static void onHop(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || hopDir == null) {
            return;
        }
        BlessingPuppeteer.PuppetType t = BlessingPuppeteer.PuppetType.byId(typeOf(mc.player));
        if (t == null || (t.group() != BlessingPuppeteer.Group.RABBIT && t.group() != BlessingPuppeteer.Group.SLIME)) {
            hopDir = null;
            return;
        }
        double speed;
        double lift;
        try {
            if (t.group() == BlessingPuppeteer.Group.SLIME) {
                // a bigger slime bounds further (+10% per size step); a magma cube bounces higher (+0.1 per step)
                int size = BlessingPuppeteer.slimeSize(mc.player);
                boolean magma = t == BlessingPuppeteer.PuppetType.MAGMA_CUBE;
                speed = (magma ? com.oliver.witchmod.Config.PUPPETEER_MAGMA_CUBE_HOP_SPEED : com.oliver.witchmod.Config.PUPPETEER_SLIME_HOP_SPEED).get()
                        * (1.0 + 0.1 * size);
                lift = magma ? com.oliver.witchmod.Config.PUPPETEER_MAGMA_CUBE_HOP_LIFT.get() + 0.1 * size
                        : com.oliver.witchmod.Config.PUPPETEER_SLIME_HOP_LIFT.get();
            } else {
                boolean killer = t == BlessingPuppeteer.PuppetType.KILLER_RABBIT;
                speed = (killer ? com.oliver.witchmod.Config.PUPPETEER_KILLER_RABBIT_HOP_SPEED : com.oliver.witchmod.Config.PUPPETEER_RABBIT_HOP_SPEED).get();
                lift = (killer ? com.oliver.witchmod.Config.PUPPETEER_KILLER_RABBIT_HOP_LIFT : com.oliver.witchmod.Config.PUPPETEER_RABBIT_HOP_LIFT).get();
                if (hopSprint) {
                    speed *= com.oliver.witchmod.Config.PUPPETEER_RABBIT_SPRINT_HOP.get(); // bolting
                }
            }
        } catch (IllegalStateException e) {
            return;
        }
        net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
        // a movement hop is a low, vanilla-style hop; the jump key gives the full jump, still carried forward.
        double up = mc.options.keyJump.isDown() ? Math.max(v.y, lift) : lift;
        mc.player.setDeltaMovement(hopDir.x * speed, up, hopDir.z * speed);
        hopDir = null;
    }

    /** a silverfish puppet hidden in stone (see LocalPlayerPushMixin, onBlockOverlay, onRenderPre). */
    public static boolean isHidden(Player player) {
        return player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    /** players who've looked an enraged enderman puppet (you) in the eye → until when they glow on your screen. */
    private static final Map<Integer, Long> STARERS = new HashMap<>();

    /** for MinecraftGlowMixin: this entity glows for you alone (it stared at you while you're an enraged enderman). */
    public static boolean glowsForMe(net.minecraft.world.entity.Entity entity) {
        Long until = STARERS.get(entity.getId());
        return until != null && entity.level().getGameTime() < until;
    }

    /** enraged enderman (you): note everyone who looks you in the eye, and keep them glowing until the rage ends. */
    private static void tickStarers(Minecraft mc) {
        Player me = mc.player;
        if (me == null || BlessingPuppeteer.PuppetType.byId(typeOf(me)) != BlessingPuppeteer.PuppetType.ENDERMAN
                || !BlessingPuppeteer.enraged(me)) {
            STARERS.clear();
            return;
        }
        long until = me.getData(WitchModAttachments.PUPPET_RAGE_END);
        for (Player other : mc.level.players()) {
            if (other != me && BlessingPuppeteer.staresAt(other, me)) {
                STARERS.put(other.getId(), until);
            }
        }
        STARERS.replaceAll((id, t) -> Math.max(t, until)); // the rage was refreshed: so is their glow
    }

    /** inside blocks on purpose — hidden (silverfish) or burrowing (endermite): never pushed out of them. */
    public static boolean inBlocksOnPurpose(Player player) {
        return isHidden(player) || BlessingPuppeteer.burrowed(player);
    }

    /** hidden in stone or burrowing through it, you look OUT — no block texture over your view. */
    @SubscribeEvent
    static void onBlockOverlay(net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent event) {
        if (inBlocksOnPurpose(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * a burrowing endermite, steered here (your movement is client-side): in the rock you go where you look (forward /
     * back / strafe; jump rises, sneak sinks) at the burrow speed; out in open air (a cave) you only drift sideways and
     * fall until you're back in the rock. the hardness limit is enforced in EntityBurrowMixin.
     */
    private static void tickBurrow(Minecraft mc) {
        Player p = mc.player;
        if (p == null || !BlessingPuppeteer.burrowed(p) || mc.screen != null) {
            return;
        }
        double speed;
        try {
            speed = com.oliver.witchmod.Config.PUPPETEER_ENDERMITE_BURROW_SPEED.get();
        } catch (IllegalStateException e) {
            return;
        }
        int forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        int rise = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        net.minecraft.world.phys.Vec3 v;
        if (BlessingPuppeteer.inEarth(p)) {
            v = p.getLookAngle().scale(forward).add(side.scale(strafe)).add(0.0, rise, 0.0);
            v = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(speed) : net.minecraft.world.phys.Vec3.ZERO;
        } else {
            net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(-Mth.sin(yaw) * forward, 0.0, Mth.cos(yaw) * forward)
                    .add(side.scale(strafe));
            flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(speed * 0.5) : net.minecraft.world.phys.Vec3.ZERO;
            v = new net.minecraft.world.phys.Vec3(flat.x, Math.max(-1.0, p.getDeltaMovement().y - 0.08), flat.z);
        }
        p.setDeltaMovement(v);
        p.resetFallDistance();
    }

    /** a fox lets go of what's in its mouth with the drop key (its inventory is stashed, so vanilla's drop does nothing). */
    @SubscribeEvent
    static void onFoxDropKey(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null
                || BlessingPuppeteer.PuppetType.byId(typeOf(mc.player)) != BlessingPuppeteer.PuppetType.FOX) {
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
            PUPPETS.clear();
            LOADED.clear();
            LAST_SWING.clear();
            LAST_FUSE.clear();
            PREV_FUSE.clear();
            LAST_ACTION.clear();
            LAST_TYPE.clear();
            held = false;
            lungeDir = null;
            return;
        }
        // held moves (creeper fuse, drowned trident, chicken egg): report right-click held / let go, on change.
        BlessingPuppeteer.PuppetType own = mc.player == null ? null : BlessingPuppeteer.PuppetType.byId(typeOf(mc.player));
        boolean want = own != null && own.move().held() && !own.move().vanillaUse() && mc.screen == null
                && mc.options.keyUse.isDown() && !mc.player.isShiftKeyDown();
        // a ghast charges its volley on the ATTACK button: report that held / let go too.
        boolean wantAttack = (own == BlessingPuppeteer.PuppetType.GHAST || own == BlessingPuppeteer.PuppetType.SCREAMING_GOAT)
                && mc.screen == null && mc.options.keyAttack.isDown();
        if (wantAttack != attackHeld) {
            attackHeld = wantAttack;
            WitchModNetwork.sendPuppetAttackHold(wantAttack);
        }
        if (want != held) {
            held = want;
            WitchModNetwork.sendPuppetFuse(want);
        }
        // the camera follows the puppet's eye height: re-measure anyone whose (synced) puppet changed.
        for (Player p : mc.level.players()) {
            String type = typeOf(p);
            BlessingPuppeteer.PuppetType puppet = BlessingPuppeteer.PuppetType.byId(type);
            if (puppet != null && puppet.group() == BlessingPuppeteer.Group.SLIME) {
                type += "#" + BlessingPuppeteer.slimeSize(p); // a slime that split is smaller: re-measure then too
            }
            if (!type.equals(LAST_TYPE.put(p.getUUID(), type))) {
                p.refreshDimensions();
            }
        }
        if (mc.player != null) {
            // a ghast never lands: vanilla drops a survival player out of flight on touching the ground, so put it back
            if (isGhastPuppet(mc.player) && mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }
            // puppets never enter swimming mode (EntitySwimMixin: the camera stays put), so a swimmer gets the one thing it
            // gave — diving and rising where you look while you swim forward — by hand, at vanilla's swimming rate.
            if (BlessingPuppeteer.swimmer(own) && mc.player.isInWater() && mc.player.input.forwardImpulse > 0.0F) {
                double lookY = mc.player.getLookAngle().y;
                double rate = lookY < -0.2 ? 0.085 : 0.06;
                net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x, v.y + (lookY - v.y) * rate, v.z);
            }
            // a blaze out of flight drifts down slowly, like the real one
            if (own == BlessingPuppeteer.PuppetType.BLAZE && !mc.player.getAbilities().flying && !mc.player.onGround()
                    && mc.player.getDeltaMovement().y < 0.0) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(1.0, 0.6, 1.0));
            }
            if (mc.player.onGround()) {
                slimeGroundTicks++;
            } else {
                slimeGroundTicks = 0;
            }
            tickStarers(mc);
            tickBurrow(mc);
            tickLunge(mc.player, own);
            if (holdingStill(mc.player)) {
                // sit dead still: no drift or sinking in water; on land only gravity (so you settle, not hover).
                net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(0.0, mc.player.isInWater() ? 0.0 : Math.min(v.y, 0.0), 0.0);
            }
        }
        // advance each puppet's animations and remember swings / fuse / moves for the next frame.
        PUPPETS.entrySet().removeIf(e -> {
            Player p = mc.level.getPlayerByUUID(e.getKey());
            if (p == null || typeOf(p).isEmpty()) {
                LAST_SWING.remove(e.getKey());
                LAST_FUSE.remove(e.getKey());
                PREV_FUSE.remove(e.getKey());
                LAST_ACTION.remove(e.getKey());
                LOADED.remove(e.getKey());
                return true;
            }
            Mob m = e.getValue();
            float dx = (float) (p.getX() - p.xo);
            float dz = (float) (p.getZ() - p.zo);
            m.walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F), 0.4F);
            m.tickCount = p.tickCount;
            boolean swingStart = p.swinging && p.tickCount - LAST_SWING.getOrDefault(p.getUUID(), -1000) > 1;
            if (p.swinging) {
                LAST_SWING.put(p.getUUID(), p.tickCount);
            }
            // hoglin / zoglin: the tusk toss plays on each swing and runs down over 10 ticks, like the real one.
            if (m instanceof net.minecraft.world.entity.monster.hoglin.Hoglin hoglin) {
                ((com.oliver.witchmod.mixin.HoglinAccessor) hoglin).witchmodSetAttackAnimationTicks(
                        swingStart ? 10 : Math.max(0, hoglin.getAttackAnimationRemainingTicks() - 1));
            }
            // iron golem: arms swing up on each attack (10 ticks, like the real one), and it holds out a poppy while offering.
            if (m instanceof net.minecraft.world.entity.animal.IronGolem golem) {
                ((com.oliver.witchmod.mixin.IronGolemAccessor) golem).witchmodSetAttackAnimationTick(
                        swingStart ? 10 : Math.max(0, golem.getAttackAnimationTick() - 1));
                boolean offering = p.getData(WitchModAttachments.PUPPET_FUSE) > 0;
                if (offering != golem.getOfferFlowerTick() > 0) {
                    golem.offerFlower(offering);
                }
            }
            if (m instanceof net.minecraft.world.entity.monster.Zoglin zoglin) {
                ((com.oliver.witchmod.mixin.ZoglinAccessor) zoglin).witchmodSetAttackAnimationTicks(
                        swingStart ? 10 : Math.max(0, zoglin.getAttackAnimationRemainingTicks() - 1));
            }
            int fuse = p.getData(WitchModAttachments.PUPPET_FUSE);
            PREV_FUSE.put(p.getUUID(), LAST_FUSE.getOrDefault(p.getUUID(), fuse));
            LAST_FUSE.put(p.getUUID(), fuse);
            int action = p.getData(WitchModAttachments.PUPPET_ACTION_COUNT);
            Integer lastAction = LAST_ACTION.put(p.getUUID(), action);
            if (m instanceof Sheep sheep) {
                SheepAccessor acc = (SheepAccessor) sheep;
                if (lastAction != null && action != lastAction) {
                    sheep.handleEntityEvent((byte) 10); // start the grazing head-dip
                }
                acc.witchmodSetEatAnimationTick(Math.max(0, acc.witchmodGetEatAnimationTick() - 1));
            }
            // ghast: its mouth stays open a moment after each fireball (the hold's charging face is set at render)
            if (m instanceof net.minecraft.world.entity.monster.Ghast && lastAction != null && action != lastAction) {
                GHAST_FACE.put(p.getUUID(), p.tickCount + 10);
            }
            // evoker: while a spell winds up, its arms go up and its spell's coloured sparks rise from its hands (vanilla's look)
            if (m instanceof net.minecraft.world.entity.monster.Evoker) {
                int spell = p.getData(WitchModAttachments.PUPPET_DASH_POWER);
                if (spell > 0) {
                    float[] c = spell == 1 ? new float[] {0.7F, 0.7F, 0.8F} : spell == 3 ? new float[] {0.7F, 0.5F, 0.2F} : new float[] {0.4F, 0.3F, 0.35F};
                    float a = p.yBodyRot * Mth.DEG_TO_RAD + Mth.cos(p.tickCount * 0.6662F) * 0.25F;
                    double handX = Mth.cos(a) * 0.6;
                    double handZ = Mth.sin(a) * 0.6;
                    net.minecraft.core.particles.ParticleOptions sparks = net.minecraft.core.particles.ColorParticleOption.create(
                            net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, c[0], c[1], c[2]);
                    mc.level.addParticle(sparks, p.getX() + handX, p.getY() + 1.8, p.getZ() + handZ, 0.0, 0.0, 0.0);
                    mc.level.addParticle(sparks, p.getX() - handX, p.getY() + 1.8, p.getZ() - handZ, 0.0, 0.0, 0.0);
                }
            }
            if (m instanceof net.minecraft.world.entity.animal.camel.Camel && lastAction != null && action != lastAction) {
                CAMEL_DASH.put(p.getUUID(), p.tickCount + 20);
            }
            if (m instanceof net.minecraft.world.entity.monster.breeze.Breeze && lastAction != null && action != lastAction) {
                BREEZE_SHOT.put(p.getUUID(), p.tickCount + 12);
            }
            // horse / donkey / mule: rears up while you charge a leap and through the leap itself, as a ridden horse does
            if (m instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
                com.oliver.witchmod.mixin.AbstractHorseAccessor acc = (com.oliver.witchmod.mixin.AbstractHorseAccessor) horse;
                boolean airborne = !p.onGround();
                if (airborne && !WAS_AIRBORNE.contains(p.getUUID()) && p.getY() > p.yo) {
                    HORSE_LEAPING.add(p.getUUID());
                } else if (!airborne) {
                    HORSE_LEAPING.remove(p.getUUID());
                }
                // (only once it's actually leaping — a ridden horse doesn't rear while you build the jump)
                boolean rearing = HORSE_LEAPING.contains(p.getUUID());
                float stand = acc.witchmodGetStandAnim();
                acc.witchmodSetStandAnimO(stand);
                stand = rearing ? Math.min(1.0F, stand + (1.0F - stand) * 0.4F + 0.05F)
                        : Math.max(0.0F, stand + ((0.8F * stand * stand * stand - stand) * 0.6F - 0.05F)); // vanilla's curve
                acc.witchmodSetStandAnim(stand);
                if (airborne) {
                    WAS_AIRBORNE.add(p.getUUID());
                } else {
                    WAS_AIRBORNE.remove(p.getUUID());
                }
            }
            // slime / magma cube: stretch on take-off, squash on landing, settle in between — its own tick, by hand
            if (m instanceof net.minecraft.world.entity.monster.Slime slime) {
                boolean airborne = !p.onGround();
                boolean was = WAS_AIRBORNE.contains(p.getUUID());
                if (airborne && !was && p.getY() > p.yo) {
                    slime.targetSquish = 1.0F;
                } else if (!airborne && was) {
                    slime.targetSquish = -0.5F;
                }
                slime.oSquish = slime.squish;
                slime.squish += (slime.targetSquish - slime.squish) * 0.5F;
                slime.targetSquish *= 0.6F;
                if (airborne) {
                    WAS_AIRBORNE.add(p.getUUID());
                } else {
                    WAS_AIRBORNE.remove(p.getUUID());
                }
            }
            // rabbit: the hop animation plays from each take-off, like the real one's (10 ticks)
            if (m instanceof net.minecraft.world.entity.animal.Rabbit rabbit) {
                com.oliver.witchmod.mixin.RabbitAccessor acc = (com.oliver.witchmod.mixin.RabbitAccessor) rabbit;
                boolean airborne = !p.onGround();
                if (airborne && !WAS_AIRBORNE.contains(p.getUUID()) && p.getY() > p.yo) {
                    acc.witchmodSetJumpDuration(10);
                    acc.witchmodSetJumpTicks(0);
                } else if (acc.witchmodGetJumpTicks() < acc.witchmodGetJumpDuration()) {
                    acc.witchmodSetJumpTicks(acc.witchmodGetJumpTicks() + 1);
                }
                if (airborne) {
                    WAS_AIRBORNE.add(p.getUUID());
                } else {
                    WAS_AIRBORNE.remove(p.getUUID());
                }
            }
            if (m instanceof Chicken chicken) {
                flap(chicken, p.onGround());
            }
            if (m instanceof net.minecraft.world.entity.animal.Squid squid) {
                swimSquid(squid, p);
            }
            if (m instanceof net.minecraft.world.entity.monster.Guardian guardian) {
                swishGuardian((GuardianAccessor) guardian, p, (float) Math.sqrt(dx * dx + dz * dz));
            }
            return false;
        });
    }

    /**
     * your own lunge, driven here so it's smooth: stand still through the wind-up, then charge at the lunge's speed
     * along a heading that can only turn towards where you look by a few degrees a tick. a wall stops it.
     */
    private static void tickLunge(Player p, @org.jetbrains.annotations.Nullable BlessingPuppeteer.PuppetType type) {
        long tick = lungeTick(p);
        if (tick < 0 || type == null) {
            lungeDir = null;
            return;
        }
        long start = p.getData(WitchModAttachments.PUPPET_LUNGE_START);
        net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
        if (type == BlessingPuppeteer.PuppetType.PHANTOM) {
            dive(p, type, start, tick);
            return;
        }
        if (tick < BlessingPuppeteer.lungeWindup(type)) {
            p.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3); // dig in
            return;
        }
        net.minecraft.world.phys.Vec3 look = p.getLookAngle();
        net.minecraft.world.phys.Vec3 want = new net.minecraft.world.phys.Vec3(look.x, 0.0, look.z);
        want = want.lengthSqr() < 1.0E-4 ? new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0) : want.normalize();
        if (lungeDir == null || lungeFor != start) {
            lungeDir = want;
            lungeFor = start;
        } else {
            double have = Math.atan2(lungeDir.z, lungeDir.x);
            double diff = Mth.wrapDegrees((Math.atan2(want.z, want.x) - have) * Mth.RAD_TO_DEG);
            double turn = Mth.clamp(diff, -BlessingPuppeteer.lungeTurn(type), BlessingPuppeteer.lungeTurn(type)) * Mth.DEG_TO_RAD;
            lungeDir = new net.minecraft.world.phys.Vec3(Math.cos(have + turn), 0.0, Math.sin(have + turn));
            if (p.horizontalCollision && tick > BlessingPuppeteer.lungeWindup(type) + 2) {
                lungeStopped = start; // ran into a wall
                lungeDir = null;
                return;
            }
        }
        double speed = BlessingPuppeteer.lungeSpeed(p, type);
        p.setDeltaMovement(lungeDir.x * speed, v.y, lungeDir.z * speed);
    }

    /**
     * the phantom's dive: along your look in 3d, turning towards where you look only a few degrees a tick, and faster
     * the steeper it goes (up to 1.5x straight down). the ground ends it.
     */
    private static void dive(Player p, BlessingPuppeteer.PuppetType type, long start, long tick) {
        net.minecraft.world.phys.Vec3 want = p.getLookAngle().normalize();
        if (lungeDir == null || lungeFor != start) {
            lungeDir = want;
            lungeFor = start;
        } else {
            double max = BlessingPuppeteer.lungeTurn(type) * Mth.DEG_TO_RAD;
            double angle = Math.acos(Mth.clamp(lungeDir.dot(want), -1.0, 1.0));
            if (angle > 1.0E-4) {
                // slerp towards the look, capped at the turn rate
                double f = Math.min(1.0, max / angle);
                lungeDir = lungeDir.scale(Math.sin((1.0 - f) * angle)).add(want.scale(Math.sin(f * angle)))
                        .scale(1.0 / Math.sin(angle)).normalize();
            }
            if ((p.onGround() || p.horizontalCollision) && tick > 2) {
                lungeStopped = start;
                lungeDir = null;
                return;
            }
        }
        double speed = BlessingPuppeteer.lungeSpeed(type) * (1.0 + 0.5 * Math.max(0.0, -lungeDir.y));
        p.setDeltaMovement(lungeDir.scale(speed));
    }

    /**
     * a squid's tentacle pulse and body tilt, as its own aiStep would do it: in water it pulses and leans into its
     * motion; out of water the tentacles flail and it slumps flat.
     */
    private static void swimSquid(net.minecraft.world.entity.animal.Squid s, Player p) {
        boolean wet = p.isInWater();
        s.oldTentacleMovement = s.tentacleMovement;
        s.oldTentacleAngle = s.tentacleAngle;
        s.xBodyRotO = s.xBodyRot;
        s.zBodyRotO = s.zBodyRot;
        s.tentacleMovement += wet ? 0.2F : 0.12F;
        if (s.tentacleMovement > Mth.TWO_PI) {
            s.tentacleMovement -= Mth.TWO_PI;
        }
        if (wet) {
            if (s.tentacleMovement < Mth.PI) {
                float f = s.tentacleMovement / Mth.PI;
                s.tentacleAngle = Mth.sin(f * f * Mth.PI) * Mth.PI * 0.25F;
            } else {
                s.tentacleAngle = 0.0F;
            }
            double dx = p.getX() - p.xo;
            double dy = p.getY() - p.yo;
            double dz = p.getZ() - p.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float target = horizontal + Math.abs(dy) < 1.0E-3 ? 0.0F : -(float) Mth.atan2(horizontal, dy) * Mth.RAD_TO_DEG;
            s.xBodyRot += (target - s.xBodyRot) * 0.1F;
        } else {
            s.tentacleAngle = Mth.abs(Mth.sin(s.tentacleMovement)) * Mth.PI * 0.25F;
            s.xBodyRot += (-90.0F - s.xBodyRot) * 0.02F;
        }
    }

    /**
     * a guardian's tail and spikes, as its own aiStep would do it: on land the tail thrashes and the spikes twitch;
     * swimming, the tail beats and the spikes fold in; holding still (spikes out = thorns), it idles with them raised.
     */
    private static void swishGuardian(GuardianAccessor g, Player p, float horizontal) {
        boolean wet = p.isInWater();
        boolean moving = horizontal + Math.abs(p.getY() - p.yo) > 0.01;
        float speed = g.witchmodGetTailSpeed();
        float tail = g.witchmodGetTail();
        g.witchmodSetTailO(tail);
        if (!wet) {
            speed = 2.0F;
        } else if (moving) {
            speed = speed < 0.5F ? 4.0F : speed + (0.5F - speed) * 0.1F;
        } else {
            speed += (0.125F - speed) * 0.2F;
        }
        g.witchmodSetTailSpeed(speed);
        g.witchmodSetTail(tail + speed);
        float spikes = g.witchmodGetSpikes();
        g.witchmodSetSpikesO(spikes);
        if (!wet) {
            g.witchmodSetSpikes(p.getRandom().nextFloat());
        } else if (moving) {
            g.witchmodSetSpikes(spikes + (0.0F - spikes) * 0.25F);
        } else {
            g.witchmodSetSpikes(spikes + (1.0F - spikes) * 0.06F);
        }
    }

    /** a chicken's wing flap, as its own aiStep would do it (the dummy is never ticked). */
    private static void flap(Chicken c, boolean onGround) {
        c.oFlap = c.flap;
        c.oFlapSpeed = c.flapSpeed;
        c.flapSpeed = Mth.clamp(c.flapSpeed + (onGround ? -1.0F : 4.0F) * 0.3F, 0.0F, 1.0F);
        if (!onGround && c.flapping < 1.0F) {
            c.flapping = 1.0F;
        }
        c.flapping *= 0.9F;
        c.flap += c.flapping * 2.0F;
    }

    @SubscribeEvent
    static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && !typeOf(event.getEntity()).isEmpty()) {
            WitchModNetwork.sendPuppetAction();
        }
    }
}
