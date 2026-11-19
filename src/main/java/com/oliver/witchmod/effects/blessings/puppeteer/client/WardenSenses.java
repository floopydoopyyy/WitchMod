package com.oliver.witchmod.effects.blessings.puppeteer.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import com.mojang.blaze3d.shaders.FogShape;
import com.oliver.witchmod.ClientConfig;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenHearing;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * puppeteer, client: seeing as a warden does — barely. a black-blue fog a few blocks deep that opens up only when
 * something living is close; a post shader that drains the world to black and cold blue (red survives: the marked),
 * a faint glint at its edges on each heartbeat; the sky gone. what it hears it SEES, through the fog and the walls
 * (WardenFx draws the sounds, the footprints, the sniffs, the marks): sniffed creatures glow teal, the marked red.
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class WardenSenses {
    private WardenSenses() {}

    private static final ResourceLocation SIGHT = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "shaders/post/warden_sight.json");

    /** what the warden knows of each creature it's noticed (entity id →), as last told. */
    record Known(int points, int markTicks, float x, float y, float z, long told) {}

    static final Map<Integer, Known> KNOWN = new HashMap<>();

    /** creatures just sniffed (entity id → until when they glow). */
    static final Map<Integer, Long> SNIFFED = new HashMap<>();

    /** the fog's reach, now and last tick (eased), and a loud sound's flash of echo-sight. */
    private static float far = 4.0F;
    private static float farO = 4.0F;
    private static float echo;

    /** the sight shader: on, or broken (never retried). */
    private static boolean shaderOn;
    private static boolean shaderFailed;
    private static java.lang.reflect.Field postEffectField;

    /** how far into the warden's sight we are (eases in and out). */
    private static float amount;
    private static float amountO;
    private static int lastBeat = -1;

    /** your own one-off animation counter, last tick (a boom or an emergence kicks your view). */
    private static int ownFx = -1;

    // --- who's a warden ---

    /** you, as a warden. */
    static boolean active() {
        Player me = Minecraft.getInstance().player;
        return me != null && WardenBehaviour.wardenPuppet(me);
    }

    // --- what it's told ---

    public static void heard(WitchModNetwork.WardenHearPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !active()) {
            return;
        }
        WardenFx.sound(mc, p);
        // a loud sound close by is a flash of echo: the fog parts for a moment
        int weight = p.kind() & 3;
        double d = mc.player.distanceToSqr(p.x(), p.y(), p.z());
        if (weight == WardenHearing.KIND_OBVIOUS && d < 144.0) {
            echo = Math.max(echo, 1.0F - (float) Math.sqrt(d) / 14.0F);
        }
    }

    public static void noticed(List<WitchModNetwork.WardenNotice> notices) {
        Minecraft mc = Minecraft.getInstance();
        KNOWN.clear();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (WitchModNetwork.WardenNotice n : notices) {
            KNOWN.put(n.entity(), new Known(n.points(), n.markTicks(), n.x(), n.y(), n.z(), now));
        }
    }

    public static void sniffed(WitchModNetwork.WardenSniffPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // a full sniff shows them for a good while; one let go early, barely a moment
        int show;
        try {
            show = p.range() >= com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_MAX_RANGE.get() - 1.0E-3
                    ? com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_REVEAL_TICKS.get()
                    : com.oliver.witchmod.Config.PUPPETEER_WARDEN_SNIFF_PARTIAL_REVEAL_TICKS.get();
        } catch (IllegalStateException e) {
            show = 80;
        }
        long until = mc.level.getGameTime() + show;
        for (int id : p.caught()) {
            SNIFFED.put(id, until);
        }
        WardenFx.sniff(mc, p);
    }

    /** the mark's ticks left on {@code id} (counted down since we were told), or 0. */
    static int markLeft(int id) {
        Known k = KNOWN.get(id);
        Minecraft mc = Minecraft.getInstance();
        if (k == null || k.markTicks() <= 0 || mc.level == null) {
            return 0;
        }
        return (int) Math.max(0L, k.markTicks() - (mc.level.getGameTime() - k.told()));
    }

    // --- glowing ---

    /** for PuppetSenses.glowsForMe: what a warden picks out through everything. */
    public static boolean glows(Entity entity) {
        return outlineColour(entity) >= 0;
    }

    /** for the team-colour mixin: the marked in red, the sniffed in teal; else -1 (a sound shows as footprints, not a glow). */
    public static int outlineColour(Entity entity) {
        if (!(entity instanceof LivingEntity) || !active() || entity == Minecraft.getInstance().player) {
            return -1;
        }
        long now = entity.level().getGameTime();
        int id = entity.getId();
        if (markLeft(id) > 0) {
            return 0xFF2018;
        }
        if (SNIFFED.getOrDefault(id, 0L) > now) {
            return 0x2FFFD0;
        }
        return -1;
    }

    // --- every tick ---

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = mc.level != null && active();
        amountO = amount;
        amount = Mth.clamp(amount + (on ? 0.05F : -0.1F), 0.0F, 1.0F);
        if (!on) {
            ownFx = -1;
            KNOWN.clear();
            SNIFFED.clear();
            far = farO = 4.0F;
            echo = 0.0F;
        } else {
            long now = mc.level.getGameTime();
            SNIFFED.values().removeIf(t -> t <= now);
            tickSight(mc);
            tickHeart(mc);
            int fx = mc.player.getData(WitchModAttachments.PUPPET_WARDEN_FX);
            if (ownFx != -1 && fx != ownFx) {
                switch (fx & 0xFF) {
                    case WardenBehaviour.FX_BOOM -> PuppetCamera.kick(2.4F);
                    case WardenBehaviour.FX_EMERGE -> PuppetCamera.kick(1.0F);
                    default -> {
                    }
                }
            }
            ownFx = fx;
        }
        tickShader(mc, amount > 0.0F && mc.level != null);
    }

    /** the fog opens to the nearest living thing within sight radius (and a loud sound's echo), and eases there. */
    private static void tickSight(Minecraft mc) {
        Player me = mc.player;
        float blind;
        float sight;
        float under;
        try {
            blind = com.oliver.witchmod.Config.PUPPETEER_WARDEN_BLIND_RANGE.get().floatValue();
            sight = com.oliver.witchmod.Config.PUPPETEER_WARDEN_SIGHT_RADIUS.get().floatValue();
            under = com.oliver.witchmod.Config.PUPPETEER_WARDEN_BURROW_SIGHT.get().floatValue();
        } catch (IllegalStateException e) {
            blind = 4.0F;
            sight = 12.0F;
            under = 18.0F;
        }
        float target = blind;
        if (sight > 0.0F) {
            for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(sight), e -> e instanceof LivingEntity l && l.isAlive()
                    && !l.isSpectator())) {
                float d = me.distanceTo(e);
                if (d <= sight) {
                    // the closer, the clearer: just past them at the edge, wide open right on top of you
                    float open = d + 2.5F + (sight - d) * 0.35F;
                    target = Math.max(target, open);
                }
            }
        }
        if (WardenBehaviour.burrowed(me)) {
            target = Math.max(target, under); // the ground hides you, not them
        }
        target += echo * 7.0F;
        echo = Math.max(0.0F, echo - 0.07F);
        farO = far;
        far += (target - far) * (target > far ? 0.25F : 0.08F); // opens quickly, closes slowly
    }

    /** your own heartbeat, felt more than heard, at the pace of your mood. */
    private static void tickHeart(Minecraft mc) {
        Player me = mc.player;
        int delay = heartDelay(me.getData(WitchModAttachments.PUPPET_WARDEN_ANGER));
        int beat = me.tickCount / delay;
        if (beat != lastBeat && me.tickCount % delay == 0) {
            lastBeat = beat;
            float anger = Math.min(1.0F, me.getData(WitchModAttachments.PUPPET_WARDEN_ANGER) / 80.0F);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.9F + anger * 0.2F, 0.55F + anger * 0.35F));
        }
    }

    /** vanilla's heartbeat pace: every 40 ticks calm, down to every 10 when angry. */
    static int heartDelay(int anger) {
        return 40 - Mth.floor(Mth.clamp(anger / 80.0F, 0.0F, 1.0F) * 30.0F);
    }

    /** 0..1: the beat, a quick lub-dub falling away, at {@code ticks} since the last one. */
    static float pulse(float ticks, int delay) {
        float t = ticks / Math.max(1, delay);
        float lub = (float) Math.exp(-t * 9.0);
        float dub = t > 0.16F ? 0.6F * (float) Math.exp(-(t - 0.16F) * 11.0) : 0.0F;
        return Math.min(1.0F, lub + dub);
    }

    // --- the shader ---

    private static void tickShader(Minecraft mc, boolean want) {
        want = want && ClientConfig.postShadersEnabled() && ClientConfig.screenShaderIntensity() > 0.0;
        PostChain current = mc.gameRenderer.currentEffect();
        boolean ours = current != null && SIGHT.toString().equals(current.getName());
        if (want && !ours && !shaderFailed) {
            try {
                mc.gameRenderer.loadEffect(SIGHT); // (taking over from any other effect while you're a warden)
                PostChain loaded = mc.gameRenderer.currentEffect();
                shaderOn = loaded != null && SIGHT.toString().equals(loaded.getName());
                shaderFailed = !shaderOn; // (vanilla logs a broken one and carries on: don't retry it every tick)
            } catch (Throwable t) {
                shaderFailed = true;
            }
        } else if (!want && shaderOn) {
            if (ours) {
                mc.gameRenderer.shutdownEffect();
            }
            shaderOn = false;
        }
    }

    /** each frame, before the post pass: the shader's strength, its heartbeat glint and its mood. */
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !shaderOn) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int anger = mc.player.getData(WitchModAttachments.PUPPET_WARDEN_ANGER);
        int delay = heartDelay(anger);
        float pulse = pulse(mc.player.tickCount % delay + pt, delay);
        if (ClientConfig.reduceFlashing()) {
            pulse *= 0.4F;
        }
        float strength = Mth.lerp(pt, amountO, amount) * (float) ClientConfig.screenShaderIntensity();
        try {
            if (postEffectField == null) {
                postEffectField = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("postEffect");
                postEffectField.setAccessible(true);
            }
            if (postEffectField.get(mc.gameRenderer) instanceof PostChain chain && SIGHT.toString().equals(chain.getName())) {
                chain.setUniform("Amount", strength);
                chain.setUniform("Pulse", pulse);
                chain.setUniform("Clock", (mc.player.tickCount + pt) / 20.0F % 3600.0F);
                chain.setUniform("Rage", Mth.clamp(anger / 120.0F, 0.0F, 1.0F));
            }
        } catch (Throwable ignored) {
            // the shader still runs at its defaults
        }
    }

    // --- the fog ---

    /**
     * the blindness: a heavy haze that starts at your face, so the world's only a dim outline a little way out (enough to
     * find your way), clearing when something's near; the sky swallowed. with no haze configured it's a solid wall a few
     * blocks out.
     */
    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        if (amount <= 0.0F || Minecraft.getInstance().player == null) {
            return;
        }
        float pt = (float) event.getPartialTick();
        float reach = Mth.lerp(pt, farO, far);
        float ease = Mth.lerp(pt, amountO, amount);
        float haze;
        float blind;
        float sight;
        try {
            haze = com.oliver.witchmod.Config.PUPPETEER_WARDEN_HAZE_RANGE.get().floatValue();
            blind = com.oliver.witchmod.Config.PUPPETEER_WARDEN_BLIND_RANGE.get().floatValue();
            sight = com.oliver.witchmod.Config.PUPPETEER_WARDEN_SIGHT_RADIUS.get().floatValue();
        } catch (IllegalStateException e) {
            haze = 0.0F;
            blind = 4.0F;
            sight = 12.0F;
        }
        // how far the fog's been opened (by someone close, an echo, being below ground): 0 shut .. 1 wide
        float open = Mth.clamp((reach - blind) / Math.max(1.0F, sight + 2.5F - blind), 0.0F, 1.0F);
        float near = haze > 0.0F ? Mth.lerp(open, -0.45F * haze, reach * 0.15F) : reach * 0.15F;
        float out = Math.max(haze, reach);
        boolean sky = event.getMode() == FogRenderer.FogMode.FOG_SKY;
        float end = Mth.lerp(ease, event.getFarPlaneDistance(), sky ? reach * 0.5F : out);
        float start = Mth.lerp(ease, event.getNearPlaneDistance(), sky ? 0.0F : near);
        event.setNearPlaneDistance(start);
        event.setFarPlaneDistance(Math.max(end, start + 0.5F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    /** the fog's colour: the near-black of the deep dark, a touch of blue. */
    @SubscribeEvent
    static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (amount <= 0.0F) {
            return;
        }
        float ease = amount;
        event.setRed(Mth.lerp(ease, event.getRed(), 0.004F));
        event.setGreen(Mth.lerp(ease, event.getGreen(), 0.012F));
        event.setBlue(Mth.lerp(ease, event.getBlue(), 0.035F));
    }

    /**
     * creatures you haven't sensed aren't seen at all — out in the haze, only what's close (that the fog opens for), what
     * you've marked or what you've just sniffed shows. (their footprints and sounds still do.)
     */
    @SubscribeEvent
    static void onRenderLiving(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (amount < 0.5F || mc.player == null || !active()) {
            return;
        }
        LivingEntity e = event.getEntity();
        Player owner = PuppetDummies.ownerOf(e); // a puppet's model: the player inside it
        Entity who = owner != null ? owner : e;
        if (who == mc.player || sensed(mc.player, who)) {
            return;
        }
        event.setCanceled(true);
    }

    /** close enough that the fog opens for them, marked, or just sniffed. */
    private static boolean sensed(Player me, Entity who) {
        double sight;
        try {
            sight = com.oliver.witchmod.Config.PUPPETEER_WARDEN_SIGHT_RADIUS.get();
        } catch (IllegalStateException e) {
            sight = 12.0;
        }
        return who.distanceToSqr(me) <= sight * sight || markLeft(who.getId()) > 0
                || SNIFFED.getOrDefault(who.getId(), 0L) > me.level().getGameTime();
    }

    /** for the sky mixin: no sky at all (as under Darkness). */
    public static boolean blocksSky() {
        return amount > 0.5F;
    }
}
