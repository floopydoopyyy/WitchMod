package com.oliver.witchmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
    private static final Map<UUID, Mob> CACHE = new HashMap<>();

    private DisguiseClient() {}

    private static Mob mobFor(Player p, int type) {
        Mob cached = CACHE.get(p.getUUID());
        EntityType<?> want = switch (type) {
            case 1 -> EntityType.SHEEP;
            case 2 -> EntityType.PIG;
            case 3 -> EntityType.BAT;
            case 4 -> EntityType.SPIDER;
            case 5 -> EntityType.VILLAGER;
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
            if (type < 0) {
                CACHE.remove(p.getUUID());
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
            if (m instanceof net.minecraft.world.entity.ambient.Bat bat) {
                bat.setResting(false); // stay in the flapping-flight pose, never the folded T-pose
            }
        }
        // bat flight GLIDES: sprinting gives a forward push (+ FOV zoom via onBatFov), like an elytra glide.
        if (mc.player != null && mc.player.getData(WitchModAttachments.DISGUISE_TYPE) == 3
                && mc.player.getAbilities().flying && mc.player.isSprinting()) {
            net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
            double push = com.oliver.witchmod.Config.FLIGHT_ELYTRA_SPRINT_SPEED.get();
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(look.x * push, 0.0, look.z * push));
        }
        CACHE.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    @SubscribeEvent
    static void onBatFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        if (event.getPlayer().getData(WitchModAttachments.DISGUISE_TYPE) == 3
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
        renderMob(renderer, m, bodyYaw, pt, event);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderMob(EntityRenderer renderer, Mob m, float bodyYaw, float pt, RenderPlayerEvent.Pre event) {
        renderer.render(m, bodyYaw, pt, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
