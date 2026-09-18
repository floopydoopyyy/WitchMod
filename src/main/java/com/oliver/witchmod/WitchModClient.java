package com.oliver.witchmod;

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

/** client-side entrypoint — registers renderers, hud layers, particle/fluid extensions. never loaded on a dedicated server. */
@Mod(value = WitchMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public class WitchModClient {
    public WitchModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // holy water needs the translucent layer or its tint alpha is discarded (an unregistered fluid falls
        // back to a solid, non-blending render type); both source and flowing need it
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(WitchModFluids.PURIFYING_WATER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(WitchModFluids.PURIFYING_WATER_FLOWING.get(), RenderType.translucent());

            // player essence colour keys off the bound uuid (stable) — the model property returns index/16 so
            // the model overrides pick the matching recolour; unbound = 0 (default texture)
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

    /** ugly: re-scan the skin folder on every resource reload, so a dropped-in png shows up on f3+t. */
    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> UglySkinManager.reload());
    }

    /** custom particle factories. */
    @SubscribeEvent
    static void onRegisterParticleProviders(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.oliver.witchmod.data.WitchModParticles.SLEEP_Z.get(),
                com.oliver.witchmod.client.SleepZParticle.Provider::new);
    }

    /** custom entity model layers. */
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
        // stacked right-side bars sit above the hunger bar; the layers themselves stagger to avoid overlap
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "gluttony_bar"), new GluttonyHudLayer());
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "thirst_bar"), new ThirstHudLayer());
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "chat_overlay"), new ChatOverlayLayer());
        // gladiator parry gauge, near the crosshair
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "gladiator_parry"), new GladiatorParryLayer());
        // flight energy bar, above the xp bar
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "flight_bar"), new com.oliver.witchmod.client.FlightBarLayer());
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "scrying"), new com.oliver.witchmod.client.ScryingOverlay());
        // fullscreen washes/overlays sit above everything
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "siren_shader"), new SirenShaderOverlay());
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "immortality_recovery"), new ImmortalityRecoveryOverlay());
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "revive_flash"), new ReviveFlashOverlay());
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "loading_screen"), new LoadingScreenOverlay());
    }

    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WitchModFluids.STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WitchModFluids.FLOW_TEXTURE;
            }

            @Override
            public int getTintColor() {
                return WitchModFluids.TINT_COLOR;
            }

            // submerged in holy water: a bright near-white fog, radiant rather than murky
            @Override
            public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera, float partialTick,
                    net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorldAmount,
                    org.joml.Vector3f fluidFogColor) {
                return new org.joml.Vector3f(0.86F, 0.93F, 1.0F);
            }

            @Override
            public void modifyFogRender(net.minecraft.client.Camera camera,
                    net.minecraft.client.renderer.FogRenderer.FogMode mode, float renderDistance, float partialTick,
                    float nearDistance, float farDistance, com.mojang.blaze3d.shaders.FogShape shape) {
                // very close haze — holy water blinds you past arm's reach
                com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(0.1F);
                com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(3.5F);
            }
        }, WitchModFluids.PURIFYING_WATER_TYPE.get());

        // gladiator: give every sword/axe the parry block-pose extension (poses only while parrying)
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.AxeItem) {
                event.registerItem(GladiatorClientHandler.PARRY_POSE, item);
            }
        }
    }
}
