package com.oliver.witchmod.effects.blessings.puppeteer.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.client.DisguiseClient;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.AllayBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermanBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.PiglinBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.VillagerBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.WitchBehaviour;
import com.oliver.witchmod.mixin.CreeperAccessor;
import com.oliver.witchmod.mixin.GuardianAccessor;
import com.oliver.witchmod.mixin.SheepAccessor;

import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetMovement.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetSenses.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppeteerClient.*;

/** puppeteer, client: the dummy mob a puppet is drawn as — kept in step with the player, posed and animated by hand. */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetDummies {
    private PuppetDummies() {}

    /** a zombie keeps its arms raised this long after a swing, then lowers them back to the normal shamble. */
    static final int ARMS_UP_TICKS = 15;

    static final ItemStack TRIDENT = new ItemStack(Items.TRIDENT);

    static final Map<UUID, Mob> PUPPETS = new HashMap<>();

    static final Map<UUID, CompoundTag> LOADED = new HashMap<>();

    /** a polar bear puppet's last growl (client tick), for its rear-up. */
    static final Map<UUID, Integer> BEAR_GROWL = new java.util.HashMap<>();

    static final Map<UUID, Integer> LAST_SWING = new HashMap<>();

    static final Map<UUID, Integer> LAST_FUSE = new HashMap<>();

    static final Map<UUID, Integer> PREV_FUSE = new HashMap<>();

    static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();

    static final java.util.Set<UUID> WAS_AIRBORNE = new java.util.HashSet<>();

    static final java.util.Set<UUID> HORSE_LEAPING = new java.util.HashSet<>();

    /** a ghast puppet keeps its mouth open until this tick (just fired). */
    static final Map<UUID, Integer> GHAST_FACE = new HashMap<>();

    /** a breeze puppet plays its shoot animation until this tick (just fired). */
    static final Map<UUID, Integer> BREEZE_SHOT = new HashMap<>();

    /** a camel puppet plays its dash animation until this tick. */
    static final Map<UUID, Integer> CAMEL_DASH = new HashMap<>();

    /** a ravager puppet's remaining roar-animation ticks (set when a roar fires). */
    static final Map<UUID, Integer> RAVAGER_ROAR = new HashMap<>();

    /** runs before the disguise renderer so a puppet always wins. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Mob m = puppetFor(p);
        if (m == null) {
            return;
        }
        event.setCanceled(true);
        if (isHidden(p) || VillagerBehaviour.stealthed(p)) {
            return; // a silverfish inside its stone, or a trader that drank invisibility: nothing to see
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
        // a shulker sits square on its block: its shell never turns — only its head looks about, as you do
        boolean gridded = m instanceof net.minecraft.world.entity.monster.Shulker;
        if (gridded) {
            bodyYaw = 0.0F;
            m.setYRot(0.0F);
            m.yRotO = 0.0F;
            m.yBodyRot = 0.0F;
            m.yBodyRotO = 0.0F;
        }
        m.setPos(p.getX(), p.getY(), p.getZ());
        m.xo = p.xo;
        m.yo = p.yo;
        m.zo = p.zo;
        m.setOnGround(p.onGround());
        // only on a change: a frog's / warden's pose STARTS its animation (tongue, leap, roar, sniff, dig...) each time it's set
        net.minecraft.world.entity.Pose mobPose = p.getPose();
        if (m instanceof net.minecraft.world.entity.animal.frog.Frog) {
            // its tongue as it eats; its long-jump pose in the air
            if (com.oliver.witchmod.effects.blessings.puppeteer.mob.FrogBehaviour.tongueOut(p)) {
                mobPose = net.minecraft.world.entity.Pose.USING_TONGUE;
            } else if (!p.onGround() && !p.isInWater()) {
                mobPose = net.minecraft.world.entity.Pose.LONG_JUMPING;
            }
        } else if (m instanceof net.minecraft.world.entity.monster.warden.Warden) {
            mobPose = com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour.pose(p); // roaring, sniffing, digging, emerging
        }
        if (m.getPose() != mobPose) {
            m.setPose(mobPose);
        }
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
        } else if (m instanceof net.minecraft.world.entity.monster.piglin.PiglinBrute) {
            m.setAggressive(true); // always holds the axe out; swings animate it
        } else if (m instanceof net.minecraft.world.entity.monster.Vindicator) {
            // arms folded, as the real one stands, until it's fighting: a swing in the last 2s, a lunge charging or running
            boolean fighting = p.tickCount - LAST_SWING.getOrDefault(p.getUUID(), -1000) < 40 || hold > 0 || lungeTick(p) >= 0;
            m.setAggressive(fighting);
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
        if (m instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
            piglin.setDancing(PiglinBehaviour.dancing(p)); // a victory dance after a hoglin kill
            // bartering: gold in its off hand puts it in vanilla's admiring pose
            boolean admiring = PiglinBehaviour.bartering(p);
            if (admiring && !piglin.getOffhandItem().is(Items.GOLD_INGOT)) {
                piglin.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
            } else if (!admiring && piglin.getOffhandItem().is(Items.GOLD_INGOT)) {
                piglin.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
        }
        if (m instanceof net.minecraft.world.entity.monster.Blaze blaze) {
            // a blaze in a fight lights up (and its fire shows), like the real one attacking
            ((com.oliver.witchmod.mixin.BlazeAccessor) blaze).witchmodSetCharged(
                    p.getData(WitchModAttachments.PUPPET_RAGE_END) > p.level().getGameTime() || hold > 0);
        }
        if (m instanceof net.minecraft.world.entity.monster.breeze.Breeze breeze) {
            // its own animations: idling always, drawing breath while a gale charges, the shot on each wind charge
            breeze.idle.startIfStopped(p.tickCount);
            int bs = com.oliver.witchmod.effects.blessings.puppeteer.mob.BreezeBehaviour.state(p);
            if (hold > 0 || bs == com.oliver.witchmod.effects.blessings.puppeteer.mob.BreezeBehaviour.INHALING) {
                breeze.inhale.startIfStopped(p.tickCount);
            } else {
                breeze.inhale.stop();
            }
            if (bs == com.oliver.witchmod.effects.blessings.puppeteer.mob.BreezeBehaviour.LEAPING) {
                breeze.longJump.startIfStopped(p.tickCount);
            } else {
                breeze.longJump.stop();
            }
            if (bs == com.oliver.witchmod.effects.blessings.puppeteer.mob.BreezeBehaviour.SLIDING) {
                breeze.slide.startIfStopped(p.tickCount);
            } else {
                breeze.slide.stop();
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
        if (m instanceof net.minecraft.world.entity.animal.Wolf wolfDummy) {
            wolfDummy.setInSittingPose(p.isCrouching()); // crouch = sit, like a pet
            // no visible bristle while frenzied — it's a disguise; the rage shows only in your own faster bite + maul.
        }
        if (m instanceof net.minecraft.world.entity.animal.Cat catDummy) {
            catDummy.setInSittingPose(p.isCrouching()); // crouch = sit
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
            boolean drinking = WitchBehaviour.drinking(p);
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
            m.getEntityData().set(com.oliver.witchmod.mixin.EnderManAccessor.witchmodCreepyKey(), EndermanBehaviour.enraged(p));
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
        PuppetType shaker = PuppetType.byId(typeOf(p));
        boolean thrash = hold > 0 && shaker == PuppetType.KILLER_RABBIT;
        boolean tremble = hold > 0 && (shaker == PuppetType.GHAST || shaker == PuppetType.BLAZE
                || shaker == PuppetType.BREEZE || shaker == PuppetType.SCREAMING_GOAT
                || shaker == PuppetType.RAVAGER || com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.teleportCharging(p));
        // a sitting cat/wolf's model tucks down a touch below the feet — a small lift sits it ON the floor without floating
        // (per-species: the two models tuck by different amounts).
        boolean sittingCat = m instanceof net.minecraft.world.entity.animal.Cat && p.isCrouching();
        boolean sittingWolf = m instanceof net.minecraft.world.entity.animal.Wolf && p.isCrouching();
        boolean sitting = sittingCat || sittingWolf;
        if (sitting) {
            pose.pushPose();
            pose.translate(0.0, sittingCat ? 0.28 : 0.12, 0.0);
        } else if (flop) {
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
            float amount = shaker == PuppetType.PHANTOM ? 0.03F + 0.1F * Math.max(0.0F, com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.hudProgress(p)) // a teleport gathering
                    : 0.02F + 0.04F * Math.min(1.0F, hold / 30.0F);
            pose.pushPose();
            pose.translate(Mth.sin(t * 1.9F) * amount, Mth.cos(t * 2.3F) * amount * 0.5F, Mth.sin(t * 1.3F + 1.0F) * amount);
        }
        if (gridded) {
            double lx = Mth.lerp(pt, p.xo, p.getX());
            double lz = Mth.lerp(pt, p.zo, p.getZ());
            pose.pushPose();
            pose.translate(Math.floor(lx) + 0.5 - lx, 0.0, Math.floor(lz) + 0.5 - lz); // snapped to its block
        }
        render(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(m), m, bodyYaw, pt,
                pose, event.getMultiBufferSource(), event.getPackedLight());
        if (gridded) {
            pose.popPose();
        }
        if (sitting || flop || thrash || tremble) {
            pose.popPose();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void render(EntityRenderer renderer, Mob m, float yaw, float pt, PoseStack pose,
                               MultiBufferSource buffer, int light) {
        renderer.render(m, yaw, pt, pose, buffer, light);
    }

    /** the player wearing {@code dummy}, if it's one of the puppet models, or null. */
    static Player ownerOf(net.minecraft.world.entity.Entity dummy) {
        if (!(dummy instanceof Mob) || dummy.level() == null) {
            return null;
        }
        for (Map.Entry<UUID, Mob> e : PUPPETS.entrySet()) {
            if (e.getValue() == dummy) {
                return dummy.level().getPlayerByUUID(e.getKey());
            }
        }
        return null;
    }

    /** the cached dummy for a possessed player (reloaded whenever the puppet's data changes), or null. */
    static Mob puppetFor(Player p) {
        String type = typeOf(p);
        if (type.isEmpty()) {
            PUPPETS.remove(p.getUUID());
            LOADED.remove(p.getUUID());
            return null;
        }
        PuppetType puppet = PuppetType.byId(type);
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

    /**
     * a squid's tentacle pulse and body tilt, as its own aiStep would do it: in water it pulses and leans into its
     * motion; out of water the tentacles flail and it slumps flat.
     */
    static void swimSquid(net.minecraft.world.entity.animal.Squid s, Player p) {
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
    static void swishGuardian(GuardianAccessor g, Player p, float horizontal) {
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

    /**
     * an armadillo's curl, as its own tick + setupAnimationStates would run it: its state from the puppet, the time in
     * that state (which decides what the shell hides), and the roll / peek / unroll animations; {@code peek} replays the
     * peek out of the shell.
     */
    static void animateArmadillo(net.minecraft.world.entity.animal.armadillo.Armadillo a, Player p, boolean peek) {
        var state = com.oliver.witchmod.effects.blessings.puppeteer.mob.ArmadilloBehaviour.vanillaState(
                com.oliver.witchmod.effects.blessings.puppeteer.mob.ArmadilloBehaviour.curlState(p));
        com.oliver.witchmod.mixin.ArmadilloAccessor ticks = (com.oliver.witchmod.mixin.ArmadilloAccessor) a;
        if (a.getState() != state) {
            a.switchToState(state); // (resets its time in the state)
        } else {
            ticks.witchmodSetInStateTicks(ticks.witchmodGetInStateTicks() + 1);
        }
        long in = ticks.witchmodGetInStateTicks();
        switch (state) {
            case IDLE -> {
                a.rollOutAnimationState.stop();
                a.rollUpAnimationState.stop();
                a.peekAnimationState.stop();
            }
            case ROLLING -> {
                a.rollOutAnimationState.stop();
                a.rollUpAnimationState.startIfStopped(a.tickCount);
                a.peekAnimationState.stop();
            }
            case SCARED -> {
                a.rollOutAnimationState.stop();
                a.rollUpAnimationState.stop();
                if (peek) {
                    a.peekAnimationState.stop();
                }
                if (in == 0L) {
                    a.peekAnimationState.start(a.tickCount);
                    a.peekAnimationState.fastForward(net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.SCARED.animationDuration(), 1.0F);
                } else {
                    a.peekAnimationState.startIfStopped(a.tickCount);
                }
            }
            case UNROLLING -> {
                a.rollOutAnimationState.startIfStopped(a.tickCount);
                a.rollUpAnimationState.stop();
                a.peekAnimationState.stop();
            }
        }
    }

    /** a chicken's wing flap, as its own aiStep would do it (the dummy is never ticked). */
    static void flap(Chicken c, boolean onGround) {
        c.oFlap = c.flap;
        c.oFlapSpeed = c.flapSpeed;
        c.flapSpeed = Mth.clamp(c.flapSpeed + (onGround ? -1.0F : 4.0F) * 0.3F, 0.0F, 1.0F);
        if (!onGround && c.flapping < 1.0F) {
            c.flapping = 1.0F;
        }
        c.flapping *= 0.9F;
        c.flap += c.flapping * 2.0F;
    }

    /** leaving the world: every dummy and its remembered animation state goes. */
    static void clearAll() {
        PUPPETS.clear();
        LOADED.clear();
        LAST_SWING.clear();
        LAST_FUSE.clear();
        PREV_FUSE.clear();
        LAST_ACTION.clear();
        BEAR_GROWL.clear();
        WardenFx.clearAll();
    }

    /** advance each puppet's animations and remember swings / fuse / moves for the next frame. */
    static void tickAnimations(Minecraft mc) {
        PUPPETS.entrySet().removeIf(e -> {
            Player p = mc.level.getPlayerByUUID(e.getKey());
            if (p == null || PuppeteerClient.typeOf(p).isEmpty()) {
                LAST_SWING.remove(e.getKey());
                LAST_FUSE.remove(e.getKey());
                PREV_FUSE.remove(e.getKey());
                LAST_ACTION.remove(e.getKey());
                LOADED.remove(e.getKey());
                RAVAGER_ROAR.remove(e.getKey());
                WardenFx.forget(e.getKey());
                return true;
            }
            animate(mc, p, e.getValue());
            return false;
        });
    }

    /** one puppet's dummy, one tick on: walk cycle, swings, and each mob's own little animations, by hand (it's never ticked). */
    static void animate(Minecraft mc, Player p, Mob m) {
        float dx = (float) (p.getX() - p.xo);
        float dz = (float) (p.getZ() - p.zo);
        // a sitting cat/wolf holds still — don't drive its walk cycle, or its legs/tail splay out of the sitting curl.
        boolean sittingPet = (m instanceof net.minecraft.world.entity.animal.Cat
                || m instanceof net.minecraft.world.entity.animal.Wolf) && p.isCrouching();
        float stride = sittingPet ? 0.0F : Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F);
        if (com.oliver.witchmod.effects.blessings.puppeteer.mob.TurtleBehaviour.laying(p)) {
            stride = 0.6F; // vanilla's laying turtle digs with its flippers: its model drives that off the walk cycle
        }
        m.walkAnimation.update(stride, 0.4F);
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
        // vex: the red charging look (and arm raise) while it winds up / dashes a lunge
        if (m instanceof net.minecraft.world.entity.monster.Vex vex) {
            vex.setIsCharging(lungeTick(p) >= 0);
        }
        // panda: the pre-sneeze head-tilt and sneeze, and the roll (its own tumble, from rollCounter) — as its tick would
        if (m instanceof net.minecraft.world.entity.animal.Panda panda) {
            int sneeze = com.oliver.witchmod.effects.blessings.puppeteer.mob.PandaBehaviour.sneezeCounter(p);
            panda.sneeze(sneeze > 0);
            panda.setSneezeCounter(sneeze);
            int roll = com.oliver.witchmod.effects.blessings.puppeteer.mob.PandaBehaviour.rollTick(p);
            panda.roll(roll >= 0);
            panda.rollCounter = roll >= 0 ? roll + 1 : 0;
            com.oliver.witchmod.mixin.PandaAccessor rolling = (com.oliver.witchmod.mixin.PandaAccessor) panda;
            float was = rolling.witchmodGetRollAmount();
            rolling.witchmodSetRollAmountO(was);
            rolling.witchmodSetRollAmount(roll >= 0 ? Math.min(1.0F, was + 0.15F) : Math.max(0.0F, was - 0.19F));
        }
        // strider: shivering (the cold, purple look) out of the warm
        if (m instanceof net.minecraft.world.entity.monster.Strider strider) {
            boolean cold = com.oliver.witchmod.effects.blessings.puppeteer.mob.StriderBehaviour.cold(p);
            if (strider.isSuffocating() != cold) {
                strider.setSuffocating(cold);
            }
        }
        // pufferfish: puffed up as far as the puppet is
        if (m instanceof net.minecraft.world.entity.animal.Pufferfish puffer
                && puffer.getPuffState() != com.oliver.witchmod.effects.blessings.puppeteer.mob.FishBehaviour.puffLevel(p)) {
            puffer.setPuffState(com.oliver.witchmod.effects.blessings.puppeteer.mob.FishBehaviour.puffLevel(p));
        }
        // allay: holds whatever it's carrying out in front, like the real one — its arms come up over 5 ticks as its own
        // tick would raise them (the item alone just hangs at its side)
        if (m instanceof net.minecraft.world.entity.animal.allay.Allay allay) {
            ItemStack carried = AllayBehaviour.allayHeld(p);
            if (!ItemStack.matches(allay.getMainHandItem(), carried)) {
                allay.setItemInHand(InteractionHand.MAIN_HAND, carried);
            }
            com.oliver.witchmod.mixin.AllayAccessor arms = (com.oliver.witchmod.mixin.AllayAccessor) allay;
            float was = arms.witchmodGetHoldingTicks();
            arms.witchmodSetHoldingTicks0(was);
            arms.witchmodSetHoldingTicks(Mth.clamp(was + (carried.isEmpty() ? -1.0F : 1.0F), 0.0F, 5.0F));
        }
        int fuse = p.getData(WitchModAttachments.PUPPET_FUSE);
        PREV_FUSE.put(p.getUUID(), LAST_FUSE.getOrDefault(p.getUUID(), fuse));
        LAST_FUSE.put(p.getUUID(), fuse);
        int action = p.getData(WitchModAttachments.PUPPET_ACTION_COUNT);
        Integer lastAction = LAST_ACTION.put(p.getUUID(), action);
        if (m instanceof net.minecraft.world.entity.animal.Parrot parrot) {
            parrot.oFlap = parrot.flap;
            parrot.oFlapSpeed = parrot.flapSpeed;
            boolean aloft = !p.onGround() && !p.isPassenger();
            parrot.flapSpeed = Mth.clamp(parrot.flapSpeed + (aloft ? 4.0F : -1.0F) * 0.3F, 0.0F, 1.0F);
            parrot.flap += parrot.flapSpeed * 2.0F;
            parrot.setOnGround(!aloft); // its model flies or stands by this
            parrot.setInSittingPose(p.isPassenger() || p.isCrouching()); // on a shoulder it sits, as the shoulder parrot does — and when you crouch
            parrot.setRecordPlayingNearby(p.blockPosition(), com.oliver.witchmod.effects.blessings.puppeteer.mob.ParrotBehaviour.dancing(p));
        }
        if (m instanceof net.minecraft.world.entity.animal.PolarBear bear) {
            // rears up for a swipe, and for its warning growl (each growl bumps the action count)
            if (lastAction != null && action != lastAction) {
                BEAR_GROWL.put(p.getUUID(), p.tickCount);
            }
            boolean standing = p.tickCount - LAST_SWING.getOrDefault(p.getUUID(), -1000) < 10
                    || p.tickCount - BEAR_GROWL.getOrDefault(p.getUUID(), -1000) < 20;
            bear.setStanding(standing);
            com.oliver.witchmod.mixin.PolarBearAccessor rear = (com.oliver.witchmod.mixin.PolarBearAccessor) bear;
            float was = rear.witchmodGetStand();
            rear.witchmodSetStandO(was);
            rear.witchmodSetStand(Mth.clamp(was + (standing ? 1.0F : -1.0F), 0.0F, 6.0F));
        }
        if (m instanceof net.minecraft.world.entity.animal.armadillo.Armadillo armadillo) {
            animateArmadillo(armadillo, p, lastAction != null && action != lastAction);
        }
        if (m instanceof net.minecraft.world.entity.animal.Bee bee) {
            // the angry look while roused, or just after a sting
            boolean angry = com.oliver.witchmod.effects.blessings.puppeteer.mob.BeeBehaviour.angry(p)
                    || p.tickCount - LAST_SWING.getOrDefault(p.getUUID(), -1000) < 20;
            bee.setRemainingPersistentAngerTime(angry ? 40 : 0);
        }
        if (m instanceof net.minecraft.world.entity.animal.sniffer.Sniffer sniffer) {
            // its own states drive its own animations (sniff, scent, dig, rise, happy)
            var state = com.oliver.witchmod.effects.blessings.puppeteer.mob.SnifferBehaviour.state(p);
            if (((com.oliver.witchmod.mixin.SnifferInvoker) sniffer).witchmodState() != state) {
                sniffer.transitionTo(state);
            }
        }
        if (m instanceof net.minecraft.world.entity.monster.warden.Warden warden) {
            WardenFx.animate(mc, p, warden); // tendrils, heart, swing, boom, dig dust
        }
        if (m instanceof net.minecraft.world.entity.monster.Shulker shulker) {
            // the shell: shut, peeking or wide open, easing between as its own tick does
            com.oliver.witchmod.mixin.ShulkerInvoker shell = (com.oliver.witchmod.mixin.ShulkerInvoker) shulker;
            shell.witchmodSetPeek(com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.peek(p));
            shell.witchmodUpdatePeek();
        }
        if (m instanceof net.minecraft.world.entity.animal.Turtle turtle) {
            boolean digging = com.oliver.witchmod.effects.blessings.puppeteer.mob.TurtleBehaviour.laying(p);
            if (digging != turtle.isLayingEgg()) {
                ((com.oliver.witchmod.mixin.TurtleInvoker) turtle).witchmodSetLayingEgg(digging);
                ((com.oliver.witchmod.mixin.TurtleInvoker) turtle).witchmodSetHasEgg(digging);
            }
        }
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
        // ravager: the head pokes out over 10 ticks on each swing (the bite), and it rears into a roar when one fires.
        if (m instanceof net.minecraft.world.entity.monster.Ravager ravager) {
            com.oliver.witchmod.mixin.RavagerAccessor acc = (com.oliver.witchmod.mixin.RavagerAccessor) ravager;
            acc.witchmodSetAttackTick(swingStart ? 10 : Math.max(0, acc.witchmodGetAttackTick() - 1));
            int roar = lastAction != null && action != lastAction ? 20 : Math.max(0, RAVAGER_ROAR.getOrDefault(p.getUUID(), 0) - 1);
            RAVAGER_ROAR.put(p.getUUID(), roar);
            acc.witchmodSetRoarTick(roar);
        }
        // evoker: while a spell winds up, its arms go up and its spell's coloured sparks rise from its hands (vanilla's look)
        if (m instanceof net.minecraft.world.entity.monster.Evoker) {
            int spell = p.getData(WitchModAttachments.PUPPET_DASH_POWER);
            if (spell > 0) {
                float[] c = spell == 1 ? new float[] {0.7F, 0.7F, 0.8F} : spell == 3 ? new float[] {0.7F, 0.5F, 0.2F}
                        : spell == 4 ? new float[] {0.3F, 0.3F, 0.8F} : new float[] {0.4F, 0.3F, 0.35F}; // (4: vanish, the illusioner's blue)
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
    }
}
