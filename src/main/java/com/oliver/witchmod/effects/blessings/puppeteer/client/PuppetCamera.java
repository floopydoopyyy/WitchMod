package com.oliver.witchmod.effects.blessings.puppeteer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.client.DisguiseClient;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetGroup;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;
import com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour;

import static com.oliver.witchmod.effects.blessings.puppeteer.client.PuppeteerClient.*;

/** puppeteer, client: flying puppets' speeds, and the view — a shot's zoom, a roar's shake. */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class PuppetCamera {
    private PuppetCamera() {}

    /** your shot's charge as 0..1 (ghast volley / blaze volley / breeze gale), or -1 if you're not winding one up. */
    public static float shotCharge(Player p) {
        PuppetType t = PuppetType.byId(typeOf(p));
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
                case RAVAGER -> com.oliver.witchmod.Config.PUPPETEER_RAVAGER_ROAR_CHARGE_TICKS.get();
                case GUARDIAN, ELDER_GUARDIAN -> com.oliver.witchmod.Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get(); // the build to its burst
                case WOLF -> p == Minecraft.getInstance().player && held
                        ? com.oliver.witchmod.Config.PUPPETEER_WOLF_MAUL_CHARGE_TICKS.get() : 0; // (not the scrap's own marker)
                case SHULKER -> com.oliver.witchmod.Config.PUPPETEER_SHULKER_VOLLEY_CHARGE_TICKS.get(); // its volley
                case WARDEN -> com.oliver.witchmod.effects.blessings.puppeteer.mob.WardenBehaviour.shriekCharge(p) >= 0.0F
                        ? com.oliver.witchmod.Config.PUPPETEER_WARDEN_SHRIEK_CHARGE_TICKS.get() : 0; // (the boom, not a sniff or dig)
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
        PuppetType who = PuppetType.byId(typeOf(event.getPlayer()));
        if (charge >= 0.0F && who != PuppetType.CAMEL) { // (a camel's dash charge is a jump bar, not an aim)
            // the ravager's roar zooms in harder than a drawn bow — the build-up to the bellow; a guardian's beam
            // closes right in as it nears the burst (an elder's most of all)
            float pull = switch (who) {
                case RAVAGER -> 0.35F;
                case ELDER_GUARDIAN -> 0.5F;
                case GUARDIAN -> 0.4F;
                case WOLF -> 0.25F; // eyes on the prey before the maul
                case WARDEN -> 0.3F; // the breath drawn in before the boom
                case SHULKER -> 0.35F; // drawing a bead with the volley
                case WITCH -> 0.28F; // sizing up the throw
                default -> 0.15F;
            };
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F - pull * charge * charge));
        }
    }

    /** a spider-family puppet — climbs walls like the Spider blessing (see ClientCurseHandler.tickSpider). */
    public static boolean isSpiderPuppet(Player player) {
        PuppetType t = PuppetType.byId(typeOf(player));
        return t != null && t.group() == PuppetGroup.SPIDER;
    }

    /** a bat puppet — flies like the bat disguise (see DisguiseClient.tickBatFlightSpeed). */
    public static boolean isBatPuppet(Player player) {
        return PuppetType.byId(typeOf(player)) == PuppetType.BAT;
    }

    /**
     * a flying puppet (bat, phantom) — flies like the bat disguise: slow, no sprint-flying, unless the Flight blessing
     * unlocks a fast sprint glide (see DisguiseClient.tickBatFlightSpeed).
     */
    public static boolean isFlyingPuppet(Player player) {
        return com.oliver.witchmod.effects.blessings.puppeteer.Puppets.flyingPuppet(player);
    }

    /**
     * a flying puppet's own fly speed (normal or the Flight blessing's sprint), slowed while winding up a shot — or null
     * for the bat (it uses the bat disguise's speeds).
     */
    public static Double flySpeed(Player p, boolean sprint) {
        PuppetType t = PuppetType.byId(typeOf(p));
        if (t == null) {
            return null;
        }
        Double speed = switch (t) {
            case PHANTOM -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_PHANTOM_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_PHANTOM_FLY_SPEED).get();
            case GHAST -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_GHAST_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_GHAST_FLY_SPEED).get();
            case BLAZE -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_BLAZE_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_BLAZE_FLY_SPEED).get();
            case BREEZE -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_BREEZE_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_BREEZE_FLY_SPEED).get();
            case PARROT -> (sprint ? com.oliver.witchmod.Config.PUPPETEER_PARROT_FLY_SPRINT_SPEED : com.oliver.witchmod.Config.PUPPETEER_PARROT_FLY_SPEED).get();
            case BEE -> (sprint || com.oliver.witchmod.effects.blessings.puppeteer.mob.BeeBehaviour.angry(p)
                    ? com.oliver.witchmod.Config.PUPPETEER_BEE_RAGE_FLY_SPEED : com.oliver.witchmod.Config.PUPPETEER_BEE_FLY_SPEED).get(); // roused: a rush
            default -> null;
        };
        return speed != null && shotCharge(p) >= 0.0F ? speed * 0.4 : speed;
    }

    /** a slow flier — never allowed to sprint-fly, Flight blessing or not (vanilla's sprint doubles fly speed). */
    public static boolean isSlowFlier(Player player) {
        return com.oliver.witchmod.effects.blessings.puppeteer.Puppets.slowFlier(player);
    }

    /** a ghast puppet — always flying, slowly (see DisguiseClient.tickBatFlightSpeed for its speed). */
    public static boolean isGhastPuppet(Player player) {
        return PuppetType.byId(typeOf(player)) == PuppetType.GHAST;
    }

    /** a slight zoom while an iron golem offers its poppy, so it feels like aiming. */
    @SubscribeEvent
    static void onPuppetFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        Player p = event.getPlayer();
        if (PuppetType.byId(typeOf(p)) == PuppetType.IRON_GOLEM
                && p.getData(WitchModAttachments.PUPPET_FUSE) > 0) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.9F);
        }
    }

    /** a guardian's beam charge last tick, to catch the burst (the charge dropping to nothing). */
    private static float lastBeam = -1.0F;
    /** the burst's kick to the view: ticks left, and how hard. */
    private static int kickTicks;
    private static float kickStrength;
    private static final int KICK_TICKS = 8;

    /** kick your view (a warden's sonic boom, its emergence): {@code strength} like a guardian burst's (1.4-2.2). */
    public static void kick(float strength) {
        if (strength > 0.0F) {
            kickStrength = kickTicks > 0 ? Math.max(strength, kickStrength) : strength;
            kickTicks = KICK_TICKS;
        }
    }

    /** a guardian's burst (its charge snapping back to nothing, past the burst's minimum) kicks the view. */
    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Player p = Minecraft.getInstance().player;
        PuppetType t = p == null ? null : PuppetType.byId(typeOf(p));
        float beam = t == PuppetType.GUARDIAN || t == PuppetType.ELDER_GUARDIAN ? shotCharge(p) : -1.0F;
        float min;
        try {
            min = com.oliver.witchmod.Config.PUPPETEER_GUARDIAN_BEAM_MIN_BURST_TICKS.get()
                    / (float) Math.max(1, com.oliver.witchmod.Config.PUPPETEER_GUARDIAN_BEAM_TICKS.get());
        } catch (IllegalStateException e) {
            min = 1.0F;
        }
        if (beam < 0.0F && lastBeam >= min) {
            kickTicks = KICK_TICKS;
            kickStrength = lastBeam * (t == PuppetType.ELDER_GUARDIAN ? 2.2F : 1.4F);
        } else if (kickTicks > 0) {
            kickTicks--;
        }
        lastBeam = beam;
    }

    /**
     * the ravager's roar charge rattles your first-person view, building with the charge; a guardian's burst kicks it,
     * dying away over a few ticks (both respect the shake setting).
     */
    @SubscribeEvent
    static void onPuppetCameraShake(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Player p = Minecraft.getInstance().player;
        double mult = com.oliver.witchmod.ClientConfig.cameraShakeMultiplier();
        if (p == null || mult <= 0.0) {
            return;
        }
        float t = p.tickCount + (float) event.getPartialTick();
        if (kickTicks > 0) {
            float fade = (kickTicks - (float) event.getPartialTick()) / KICK_TICKS;
            float kick = (float) (kickStrength * fade * fade * mult);
            event.setPitch(event.getPitch() - 2.5F * kick + Mth.cos(t * 53.0F) * 0.8F * kick);
            event.setYaw(event.getYaw() + Mth.sin(t * 47.0F) * 0.8F * kick);
            event.setRoll(event.getRoll() + Mth.sin(t * 31.0F) * 1.5F * kick);
        }
        float dig = WardenBehaviour.digProgress(p, (float) event.getPartialTick());
        if (dig >= 0.0F) {
            // digging in, you look down at the ground you're tearing into (your eyes sink with the body — see
            // WardenBehaviour.bodyScale) with the view wobbling from the effort; climbing out, it lifts back up
            boolean in = WardenBehaviour.state(p) == WardenBehaviour.DIGGING;
            float down = in ? Math.min(1.0F, dig * 2.5F) : 1.0F - Math.min(1.0F, dig * 1.5F);
            float strain = (float) mult * (in ? 0.5F + 0.7F * dig : 1.2F - 0.7F * dig);
            event.setPitch(event.getPitch() + (55.0F - event.getPitch()) * 0.6F * down + Mth.sin(t * 0.8F) * 1.6F * strain);
            event.setRoll(event.getRoll() + Mth.sin(t * 0.55F) * 2.0F * strain + Mth.sin(t * 2.1F) * 0.5F * strain);
            event.setYaw(event.getYaw() + Mth.sin(t * 0.9F + 1.3F) * 0.8F * strain);
        }
        float gathering = com.oliver.witchmod.effects.blessings.puppeteer.mob.PhantomBehaviour.hudProgress(p);
        if (gathering > 0.0F && PuppetType.byId(typeOf(p)) == PuppetType.PHANTOM) {
            // a phantom gathering a teleport: the view trembles, harder and harder as it builds
            float amount = (float) ((0.15F + 1.1F * gathering * gathering) * mult);
            event.setYaw(event.getYaw() + Mth.sin(t * 33.0F) * 0.6F * amount);
            event.setPitch(event.getPitch() + Mth.cos(t * 39.0F) * 0.5F * amount);
            event.setRoll(event.getRoll() + Mth.sin(t * 27.0F) * 1.0F * amount);
            return;
        }
        if (PuppetType.byId(typeOf(p)) != PuppetType.RAVAGER) {
            return;
        }
        float charge = shotCharge(p);
        if (charge <= 0.0F) {
            return;
        }
        float amount = (float) (charge * charge * mult); // grows with the charge
        event.setYaw(event.getYaw() + Mth.sin(t * 37.0F) * 0.7F * amount);
        event.setPitch(event.getPitch() + Mth.cos(t * 41.0F) * 0.6F * amount);
        event.setRoll(event.getRoll() + Mth.sin(t * 29.0F) * 1.1F * amount);
    }
}
