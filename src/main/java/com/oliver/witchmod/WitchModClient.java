package com.oliver.witchmod;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.blocks.WitchModFluids;
import com.oliver.witchmod.client.ChatOverlayLayer;
import com.oliver.witchmod.client.GladiatorClientHandler;
import com.oliver.witchmod.client.GladiatorParryLayer;
import com.oliver.witchmod.client.GluttonyHudLayer;
import com.oliver.witchmod.client.ImmortalityRecoveryOverlay;
import com.oliver.witchmod.client.LoadingScreenOverlay;
import com.oliver.witchmod.client.ReviveFlashOverlay;
import com.oliver.witchmod.client.SirenShaderOverlay;
import com.oliver.witchmod.client.ThirstHudLayer;
import com.oliver.witchmod.client.BodyguardRenderer;
import com.oliver.witchmod.client.TaxManRenderer;
import com.oliver.witchmod.client.UglySkinManager;
import com.oliver.witchmod.entities.WitchModEntities;
import com.oliver.witchmod.ui.BewitchingTableScreen;
import com.oliver.witchmod.ui.WitchModMenus;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = WitchMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public class WitchModClient {
    /** Opens the Organised blessing's extra inventory row (default: O). Only does anything if you have it. */
    public static final KeyMapping ORGANISED_KEY = new KeyMapping(
            "key.witchmod.organised", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.witchmod");

    public WitchModClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ORGANISED_KEY);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        WitchMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        WitchMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Holy Water has to be on the TRANSLUCENT layer or the alpha in its tint colour is simply discarded
        // — an unregistered fluid falls back to a solid render type, which does no blending at all. Vanilla
        // registers its own water the same way. Both the source and flowing fluids need it.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(WitchModFluids.PURIFYING_WATER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(WitchModFluids.PURIFYING_WATER_FLOWING.get(), RenderType.translucent());

            // Player Essence's "colour" is picked by the bound player's UUID (stable forever) — a purely visual
            // differential. This model property returns index/16 so the item model's overrides select the matching
            // recolour; an unbound essence returns 0 (the default player_essence texture).
            net.minecraft.client.renderer.item.ItemProperties.register(
                    com.oliver.witchmod.items.WitchModItems.PLAYER_ESSENCE.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "essence_colour"),
                    (stack, level, entity, seed) -> {
                        com.oliver.witchmod.data.PlayerEssenceData d =
                                stack.get(com.oliver.witchmod.data.WitchModDataComponents.BOUND_PLAYER);
                        return d == null ? 0.0F : Math.floorMod(d.playerId().hashCode(), 16) / 16.0F;
                    });
        });
    }

    /**
     * Ugly: re-scan the skin folder on every resource reload, so dropping a new PNG in and pressing F3+T
     * picks it up without a restart — which is the whole promise of that folder.
     */
    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> UglySkinManager.reload());
    }

    /** Custom particle factories. */
    @SubscribeEvent
    static void onRegisterParticleProviders(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.oliver.witchmod.data.WitchModParticles.SLEEP_Z.get(),
                com.oliver.witchmod.client.SleepZParticle.Provider::new);
    }

    /** The custom entities' model layers. */
    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TaxManRenderer.LAYER, TaxManRenderer::createBodyLayer);
        event.registerLayerDefinition(BodyguardRenderer.SUNGLASSES_LAYER, BodyguardRenderer::createSunglassesLayer);
        event.registerLayerDefinition(com.oliver.witchmod.client.SnailRenderer.LAYER, com.oliver.witchmod.client.SnailModel::createLayer);
        event.registerLayerDefinition(com.oliver.witchmod.client.SpaghettiManRenderer.LAYER, com.oliver.witchmod.client.SpaghettiManModel::createLayer);
        event.registerLayerDefinition(com.oliver.witchmod.client.WatcherEyesRenderer.LAYER, com.oliver.witchmod.client.WatcherEyesModel::createLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WitchModEntities.TAX_MAN.get(), TaxManRenderer::new);
        event.registerEntityRenderer(WitchModEntities.BODYGUARD.get(), BodyguardRenderer::new);
        event.registerEntityRenderer(WitchModEntities.SNAIL.get(), com.oliver.witchmod.client.SnailRenderer::new);
        event.registerEntityRenderer(WitchModEntities.SPAGHETTI_MAN.get(), com.oliver.witchmod.client.SpaghettiManRenderer::new);
        event.registerEntityRenderer(WitchModEntities.WATCHER_EYES.get(), com.oliver.witchmod.client.WatcherEyesRenderer::new);
        event.registerEntityRenderer(WitchModEntities.DREAM.get(), com.oliver.witchmod.client.DreamRenderer::new);
        event.registerEntityRenderer(WitchModEntities.CLONE.get(), com.oliver.witchmod.client.CloneRenderer::new);
        event.registerEntityRenderer(WitchModEntities.JAR_THROW.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        event.registerBlockEntityRenderer(com.oliver.witchmod.blocks.WitchModBlockEntities.LEDGER.get(),
                com.oliver.witchmod.client.LedgerRenderer::new);
        event.registerBlockEntityRenderer(com.oliver.witchmod.blocks.WitchModBlockEntities.AMETHYST_BELL.get(),
                com.oliver.witchmod.client.AmethystBellRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(WitchModMenus.BEWITCHING_TABLE.get(), BewitchingTableScreen::new);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // WitchMod's stacked right-side bars sit above the vanilla hunger bar (Phase D / Section 13.4);
        // HudBars keeps them from overlapping when several are active at once.
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "gluttony_bar"), new GluttonyHudLayer());
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "thirst_bar"), new ThirstHudLayer());
        // Chat blessing's Twitch overlay (a side panel, above the HUD but below any fullscreen overlay).
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "chat_overlay"), new ChatOverlayLayer());
        // Gladiator parry-stage bar, drawn near the crosshair (below the vanilla attack indicator).
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "gladiator_parry"), new GladiatorParryLayer());
        // Blessing of Flight energy bar, just above the XP bar.
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "flight_bar"), new com.oliver.witchmod.client.FlightBarLayer());
        // Scrying Mirror result panel.
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "scrying"), new com.oliver.witchmod.client.ScryingOverlay());
        // Siren's Call magenta mind-control tint — over the HUD but below the loading-screen prank.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "siren_shader"), new SirenShaderOverlay());
        // Immortality's gold->white rebuild wash while recovering from a death.
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "immortality_recovery"), new ImmortalityRecoveryOverlay());
        // The Last Stand / Immortality revive "totem" pop (Blessed icon, gold->white).
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "revive_flash"), new ReviveFlashOverlay());
        // Loading Screen prank overlay sits above everything (it's a fake fullscreen loading screen).
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "loading_screen"), new LoadingScreenOverlay());
    }

    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WitchModFluids.WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WitchModFluids.WATER_FLOW;
            }

            @Override
            public int getTintColor() {
                return WitchModFluids.TINT_COLOR;
            }
        }, WitchModFluids.PURIFYING_WATER_TYPE.get());

        // Gladiator: give every sword/axe the parry block-pose extension (it only poses while parrying).
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.AxeItem) {
                event.registerItem(GladiatorClientHandler.PARRY_POSE, item);
            }
        }
    }
}
