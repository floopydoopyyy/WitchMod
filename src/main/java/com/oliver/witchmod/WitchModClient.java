package com.oliver.witchmod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
import com.oliver.witchmod.client.GluttonyHudLayer;
import com.oliver.witchmod.client.LoadingScreenOverlay;
import com.oliver.witchmod.client.ThirstHudLayer;
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
    public WitchModClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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

    /** The Tax Man's humanoid model and renderer. */
    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TaxManRenderer.LAYER, TaxManRenderer::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WitchModEntities.TAX_MAN.get(), TaxManRenderer::new);
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
    }
}
