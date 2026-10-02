package com.oliver.witchmod.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * blessing of Disguise (client half): render any player whose {@link WitchModAttachments#DISGUISE_TYPE} is set
 * as a cow / sheep / pig instead of their player model (and no nametag). A cached dummy mob per player mirrors
 * the player's facing + walk animation so the costume moves convincingly.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class DisguiseClient {
    /** type 6 = random online player; type 7 = fish. */
    private static final int PLAYER = 6;
    private static final int FISH = 7;
    private static final Map<UUID, Mob> CACHE = new HashMap<>();
    /** entity's "in water" flag — we mirror the player's onto the dummy fish so the CodRenderer swims it upright
     * in water and lays it on its side out of water (its own flop pose), instead of always side-laid. */
    private static final Field WAS_TOUCHING_WATER = resolveWaterField();

    private DisguiseClient() {}

    private static Field resolveWaterField() {
        try {
            Field f = Entity.class.getDeclaredField("wasTouchingWater");
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[Disguise] Could not access Entity.wasTouchingWater; fish disguise won't swim upright.", e);
            return null;
        }
    }

    /** mirror a real entity's water state onto a dummy render mob, so a cod swims upright / flops on its side. */
    public static void mirrorWaterState(Entity m, boolean inWater) {
        if (WAS_TOUCHING_WATER != null) {
            try {
                WAS_TOUCHING_WATER.setBoolean(m, inWater);
            } catch (Exception ignored) {
                // already logged once; leave it side-laid
            }
        }
    }

    private static Mob mobFor(Player p, int type) {
        Mob cached = CACHE.get(p.getUUID());
        EntityType<?> want = switch (type) {
            case 1 -> EntityType.SHEEP;
            case 2 -> EntityType.PIG;
            case 3 -> EntityType.BAT;
            case 4 -> EntityType.SPIDER;
            case 5 -> EntityType.VILLAGER;
            case FISH -> EntityType.COD;
            default -> EntityType.COW;
        };
        if (cached == null || cached.getType() != want) {
            Entity e = want.create(Minecraft.getInstance().level);
            if (!(e instanceof Mob m)) {
                return null;
            }
            if (m instanceof net.minecraft.world.entity.ambient.Bat bat) {
                bat.setResting(false); // a resting bat folds its wings and T-poses — keep it flying so they flap
            }
            CACHE.put(p.getUUID(), m);
            cached = m;
        }
        return cached;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CACHE.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            int type = p.getData(WitchModAttachments.DISGUISE_TYPE);
            if (type < 0 || type == PLAYER) {
                CACHE.remove(p.getUUID()); // no dummy mob for "real player" or the random-player disguise
                continue;
            }
            Mob m = mobFor(p, type);
            if (m == null) {
                continue;
            }
            // drive the leg animation off the player's per-tick horizontal movement (like vanilla aiStep does).
            float dx = (float) (p.getX() - p.xOld);
            float dz = (float) (p.getZ() - p.zOld);
            float f = Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F);
            m.walkAnimation.update(f, 0.4F);
            m.tickCount = p.tickCount;
            if (m instanceof Bat bat) {
                // the bat flaps off its AnimationState, which the render only advances while it's STARTED — since
                // the dummy is never ticked, we start the fly state ourselves (and stop the resting one).
                bat.setResting(false);
                bat.flyAnimationState.animateWhen(true, bat.tickCount);
                bat.restAnimationState.stop();
            }
            if (m instanceof AbstractFish && WAS_TOUCHING_WATER != null) {
                try {
                    WAS_TOUCHING_WATER.setBoolean(m, p.isInWater()); // swim upright in water, flop on its side out of it
                } catch (Exception ignored) {
                    // reflection failed once already-logged; leave it side-laid
                }
            }
        }
        tickBatFlightSpeed(mc);
        CACHE.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    /** whether we've lowered the local player's fly speed for a bat disguise (so we can restore it). */
    private static boolean batSpeedApplied;

    /**
     * bat disguise flies SLOWLY and can't sprint-fly on its own — the Flight blessing is what unlocks a fast
     * sprint dash. movement is client-authoritative, so the fly speed + sprint gate live here on the local player.
     */
    /** the sprint key is held (or sprint is still latched from last tick) — i.e. vanilla will sprint-fly next tick. */
    private static boolean sprintKeyHeld(Minecraft mc) {
        return mc.options.keySprint.isDown() || (mc.player != null && mc.player.isSprinting());
    }

    private static void tickBatFlightSpeed(Minecraft mc) {
        var p = mc.player;
        if (p != null && (p.getData(WitchModAttachments.DISGUISE_TYPE) == 3 || PuppeteerClient.isFlyingPuppet(p)) && p.getAbilities().flying
                && !p.isCreative() && !p.isSpectator()) {
            boolean hasFlight = p.getData(WitchModAttachments.FLIGHT_ACTIVE) >= 1;
            // a slow flier (ghast, blaze, breeze) can NEVER sprint-fly — the Flight blessing only nudges its speed. the
            // quick ones (bat, phantom) get the blessing's sprint dash, as the bat disguise does.
            boolean slowFlier = PuppeteerClient.isSlowFlier(p);
            boolean canSprint = hasFlight && !slowFlier;
            if (!canSprint && p.isSprinting()) {
                p.setSprinting(false);
            }
            boolean sprintFly = canSprint && p.isSprinting();
            // the bat disguise / bat puppet use the bat's speeds; each other flying puppet has its own (and winding up a
            // shot slows it right down).
            Double own = PuppeteerClient.flySpeed(p, slowFlier ? hasFlight : sprintFly);
            float speed = (float) (double) (own != null ? own : sprintFly
                    ? com.oliver.witchmod.Config.BAT_FLIGHT_SPRINT_SPEED.get() : com.oliver.witchmod.Config.BAT_FLY_SPEED.get());
            // vanilla re-asserts sprint at the start of the next tick (held key / double-tap) and DOUBLES fly speed for
            // it, so clearing the flag alone still let one sprinting tick through each time — halve the base to cancel it.
            if (!canSprint && sprintKeyHeld(mc)) {
                speed *= 0.5F;
            }
            p.getAbilities().setFlyingSpeed(speed);
            batSpeedApplied = true;
        } else if (batSpeedApplied) {
            if (p != null) {
                p.getAbilities().setFlyingSpeed(0.05F); // restore vanilla creative fly speed
            }
            batSpeedApplied = false;
        }
    }

    @SubscribeEvent
    static void onBatFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        // the sprint-fly FOV zoom only kicks in when the Flight blessing has unlocked the bat's fast dash.
        if ((event.getPlayer().getData(WitchModAttachments.DISGUISE_TYPE) == 3 || PuppeteerClient.isFlyingPuppet(event.getPlayer()))
                && event.getPlayer().getData(WitchModAttachments.FLIGHT_ACTIVE) >= 1
                && event.getPlayer().getAbilities().flying && event.getPlayer().isSprinting()) {
            event.setNewFovModifier(event.getNewFovModifier() * com.oliver.witchmod.Config.FLIGHT_ELYTRA_FOV.get().floatValue());
        }
    }

    @SubscribeEvent
    static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        // concealment flash: briefly unrendered (armour and all, no floating gear) right after a form change.
        long flash = p.getData(WitchModAttachments.CONCEAL_FLASH_END);
        if (flash > 0 && p.level().getGameTime() < flash) {
            event.setCanceled(true);
            return;
        }
        int type = p.getData(WitchModAttachments.DISGUISE_TYPE);
        if (type < 0) {
            return;
        }
        // the random-player disguise renders as an ordinary player — the skin is swapped by UglySkinManager and
        // the nametag by onNameTag, so we let vanilla draw the player normally rather than a dummy mob.
        if (type == PLAYER) {
            return;
        }
        Mob m = mobFor(p, type);
        if (m == null) {
            return;
        }
        event.setCanceled(true); // no player model or nametag while disguised

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

        EntityRenderer<? super Mob> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(m);
        PoseStack pose = event.getPoseStack();
        // a fish out of water FLAILS: the CodRenderer already lays it on its side, so on top we rock and twist it
        // (the big up/down motion is the real physical bounce the server gives the player). in water it just swims.
        boolean flop = type == FISH && !p.isInWater();
        if (flop) {
            float t = (p.tickCount + pt) * 0.9F;
            pose.pushPose();
            pose.translate(0, 0.15, 0);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t) * 22.0F));      // twist left/right
            pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(t * 1.3F) * 20.0F)); // arch/curl the body
        }
        renderMob(renderer, m, bodyYaw, pt, event);
        if (flop) {
            pose.popPose();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderMob(EntityRenderer renderer, Mob m, float bodyYaw, float pt, RenderPlayerEvent.Pre event) {
        renderer.render(m, bodyYaw, pt, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /**
     * replace a nametag with an impersonated player's name — for a random-player disguise (alone → keep own
     * name), or for a real player the local Delusions curse is misidentifying as someone else.
     */
    @SubscribeEvent
    static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player p) || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        UUID target = p.getData(WitchModAttachments.DISGUISE_TYPE) == PLAYER
                ? playerDisguiseTargetId(p)
                : DelusionMisidentify.impersonatedBy(p.getUUID());
        if (target == null) {
            return;
        }
        PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(target);
        if (info != null) {
            event.setContent(Component.literal(info.getProfile().getName()));
        }
    }

    /**
     * which online player a random-player disguise impersonates — a deterministic pick over the sorted online
     * list (so every client agrees), excluding the disguised player. null when nobody else is online (alone),
     * in which case the disguise just shows your normal self. re-evaluates as players join/leave.
     */
    @Nullable
    public static UUID playerDisguiseTargetId(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            if (!id.equals(p.getUUID())) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        ids.sort(Comparator.naturalOrder());
        return ids.get(Math.floorMod(p.getUUID().hashCode(), ids.size()));
    }
}
