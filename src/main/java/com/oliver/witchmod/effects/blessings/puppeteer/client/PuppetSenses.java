package com.oliver.witchmod.effects.blessings.puppeteer.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermanBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermiteBehaviour;

import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetDummies.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppeteerClient.*;

/** puppeteer, client: what a puppet sees — out of the stone it hides in, and the players who stare at an enraged enderman. */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetSenses {
    private PuppetSenses() {}

    /** a silverfish puppet hidden in stone (see LocalPlayerPushMixin, onBlockOverlay, onRenderPre). */
    public static boolean isHidden(Player player) {
        return player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    /** players who've looked an enraged enderman puppet (you) in the eye → until when they glow on your screen. */
    static final Map<Integer, Long> STARERS = new HashMap<>();

    /** for MinecraftGlowMixin: this entity glows for you alone (it stared at you while you're an enraged enderman). */
    public static boolean glowsForMe(net.minecraft.world.entity.Entity entity) {
        Long until = STARERS.get(entity.getId());
        return until != null && entity.level().getGameTime() < until || sharpSenses(entity) || WardenSenses.glows(entity)
                || entity.getId() == shulkerTarget || phantomQuarry(entity);
    }

    /** for the team-colour mixin: a puppet's own outline colour for {@code entity} (a warden's red / teal, a shulker's lock), or -1. */
    public static int outlineColour(net.minecraft.world.entity.Entity entity) {
        int warden = WardenSenses.outlineColour(entity);
        if (warden >= 0) {
            return warden;
        }
        if (entity.getId() == shulkerTarget) {
            return 0xC88CFF;
        }
        if (phantomQuarry(entity)) {
            return 0x5CFF6A; // hasn't slept: a phantom's prey
        }
        Long stared = STARERS.get(entity.getId());
        if (stared != null && entity.level().getGameTime() < stared) {
            return 0xB050FF; // looked an enderman (you) in the eye
        }
        return -1;
    }

    /** enderman (you): a faint shimmer of portal dust where a teleport would put you (through a wall, if it would). */
    static void tickEnderMarker(Minecraft mc) {
        Player me = mc.player;
        if (me == null || PuppetType.byId(typeOf(me)) != PuppetType.ENDERMAN || me.getData(WitchModAttachments.PUPPET_TP_CHARGES) <= 0
                || me.tickCount % 2 != 0) {
            return;
        }
        net.minecraft.world.phys.Vec3 spot;
        try {
            spot = EndermanBehaviour.teleportTarget(me);
        } catch (IllegalStateException e) {
            return;
        }
        if (spot != null) {
            double a = me.getRandom().nextDouble() * Math.PI * 2.0;
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    spot.x + Math.cos(a) * 0.4, spot.y + 0.05, spot.z + Math.sin(a) * 0.4, 0.0, 0.02, 0.0);
        }
    }

    /** phantom (you): a player who hasn't slept for three nights, or whatever your dive has marked. */
    private static boolean phantomQuarry(net.minecraft.world.entity.Entity entity) {
        Player me = Minecraft.getInstance().player;
        return me != null && entity != me && PuppetType.byId(typeOf(me)) == PuppetType.PHANTOM
                && (com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.sleepless(entity) || me.getData(WitchModAttachments.PUPPET_MARK) == entity.getId());
    }

    /** a shulker puppet's bullet lock (entity id, or -1): outlined for you, so you know who you'd hit. */
    static int shulkerTarget = -1;

    /**
     * shulker (you): whoever your bullets would lock onto glows (and sparkles faintly); with no one, on Teleport, a faint
     * shimmer marks the block face you'd land on. yours alone.
     */
    static void tickShulker(Minecraft mc) {
        Player me = mc.player;
        shulkerTarget = -1;
        if (me == null || !com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.shulkerPuppet(me)) {
            return;
        }
        net.minecraft.world.entity.LivingEntity target;
        net.minecraft.world.phys.Vec3 spot;
        try {
            target = com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.bulletTarget(me);
            boolean teleport = com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.selection(me)
                    == com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.TELEPORT;
            spot = teleport && target == null && me.getData(WitchModAttachments.PUPPET_TP_CHARGES) > 0
                    ? com.oliver.witchmod.effects.blessings.puppeteer.mob.ShulkerBehaviour.teleportSpot(me) : null;
        } catch (IllegalStateException e) {
            return; // config not synced yet
        }
        net.minecraft.util.RandomSource random = me.getRandom();
        if (target != null) {
            shulkerTarget = target.getId();
            if (me.tickCount % 3 == 0) {
                net.minecraft.world.phys.AABB box = target.getBoundingBox();
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        box.minX + random.nextDouble() * box.getXsize(), box.minY + random.nextDouble() * box.getYsize(),
                        box.minZ + random.nextDouble() * box.getZsize(), 0.0, 0.01, 0.0);
            }
        } else if (spot != null && me.tickCount % 2 == 0) {
            // the landing spot: a faint ring of shimmer on the face you'd stand on
            double a = random.nextDouble() * Math.PI * 2.0;
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    spot.x + Math.cos(a) * 0.45, spot.y + 0.05, spot.z + Math.sin(a) * 0.45, 0.0, 0.02, 0.0);
        }
    }

    /**
     * a puppet's keen senses, shown through walls for you alone: a parrot's eyes pick out every mob near you; a skittish
     * armadillo, whatever would scare it.
     */
    private static boolean sharpSenses(net.minecraft.world.entity.Entity entity) {
        Player me = Minecraft.getInstance().player;
        PuppetType own = me == null ? null : PuppetType.byId(typeOf(me));
        if (own == PuppetType.ARMADILLO) {
            return com.oliver.witchmod.effects.blessings.puppeteer.mob.ArmadilloBehaviour.scares(me, entity);
        }
        if (!(entity instanceof net.minecraft.world.entity.Mob) || own != PuppetType.PARROT) {
            return false;
        }
        try {
            double r = com.oliver.witchmod.Config.PUPPETEER_PARROT_SIGHT_RADIUS.get();
            return entity.distanceToSqr(me) <= r * r;
        } catch (IllegalStateException e) {
            return false; // config not synced yet
        }
    }

    /** enraged enderman (you): note everyone who looks you in the eye, and keep them glowing until the rage ends. */
    static void tickStarers(Minecraft mc) {
        Player me = mc.player;
        if (me == null || PuppetType.byId(typeOf(me)) != PuppetType.ENDERMAN
                || !EndermanBehaviour.enraged(me)) {
            STARERS.clear();
            return;
        }
        long until = me.getData(WitchModAttachments.PUPPET_RAGE_END);
        for (Player other : mc.level.players()) {
            if (other != me && EndermanBehaviour.staresAt(other, me)) {
                STARERS.put(other.getId(), until);
            }
        }
        STARERS.replaceAll((id, t) -> Math.max(t, until)); // the rage was refreshed: so is their glow
    }

    /** inside blocks on purpose — hidden (silverfish) or burrowing (endermite): never pushed out of them. */
    public static boolean inBlocksOnPurpose(Player player) {
        return isHidden(player) || EndermiteBehaviour.burrowed(player);
    }

    /** hidden in stone or burrowing through it, you look OUT — no block texture over your view; fire-proof, no flames over it. */
    @SubscribeEvent
    static void onBlockOverlay(net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent event) {
        if (inBlocksOnPurpose(event.getPlayer())) {
            event.setCanceled(true);
            return;
        }
        PuppetType own = PuppetType.byId(typeOf(event.getPlayer()));
        if (event.getOverlayType() == net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.FIRE
                && own != null && own.entityType().fireImmune()) {
            event.setCanceled(true);
        }
    }
}
