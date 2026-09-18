package com.oliver.witchmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModMobEffects;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.blocks.WardingTotemBlock;
import com.oliver.witchmod.blocks.WitchModBlockEntities;
import com.oliver.witchmod.blocks.WitchModBlocks;
import com.oliver.witchmod.blocks.WitchModFluids;
import com.oliver.witchmod.items.ItemWard;
import com.oliver.witchmod.items.WitchModItems;
import com.oliver.witchmod.ui.WitchModMenus;

/**
 * common mod entrypoint — owns the shared registers (blocks/items/tabs), wires every deferred register and
 * subsystem onto the event bus, and registers the configs. runs on both sides; client-only setup lives in
 * {@link WitchModClient}.
 */
@Mod(WitchMod.MODID)
public class WitchMod {
    public static final String MODID = "witchmod";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // creative tab — every item + block, iconed by cursed essence
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WITCHMOD_TAB = CREATIVE_MODE_TABS.register("witchmod", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.witchmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> WitchModItems.CURSED_ESSENCE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // items
                output.accept(WitchModItems.CURSED_ESSENCE.get());
                output.accept(WitchModItems.PLAYER_ESSENCE.get());
                output.accept(WitchModItems.COMPENDIUM.get());
                output.accept(WitchModItems.VOODOO_DOLL.get());
                output.accept(WitchModItems.NEEDLE.get());
                output.accept(WitchModItems.WARD.get());
                output.accept(WitchModItems.SCRYING_MIRROR.get());
                output.accept(WitchModItems.EFFIGY.get());
                output.accept(WitchModItems.CURSED_COIN.get());
                output.accept(WitchModItems.BLESSED_COIN.get());
                output.accept(WitchModItems.EXECUTIONERS_COIN.get());
                output.accept(WitchModItems.JAR.get());
                output.accept(WitchModItems.CURSED_JAR.get());
                output.accept(WitchModItems.BLESSED_JAR.get());
                output.accept(WitchModItems.MIXED_JAR.get());
                // (recovery compass modifier is backed by the vanilla item — left out to avoid a duplicate)
                // blocks
                output.accept(WitchModBlocks.BEWITCHING_TABLE_ITEM.get());
                output.accept(WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get());
                output.accept(WitchModBlocks.LEDGER_ITEM.get());
                output.accept(WitchModBlocks.WARDING_TOTEM_ITEM.get());
                output.accept(WitchModBlocks.AMETHYST_BELL_ITEM.get());
                output.accept(WitchModFluids.PURIFYING_WATER_BUCKET.get());
            }).build());

    public WitchMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        WitchModRegistries.register(modEventBus);
        WitchModAttachments.register(modEventBus);
        WitchModMobEffects.register(modEventBus);
        WitchModSounds.register(modEventBus);
        com.oliver.witchmod.data.WitchModParticles.register(modEventBus);
        com.oliver.witchmod.items.WitchModRecipes.register(modEventBus);
        com.oliver.witchmod.entities.WitchModEntities.register(modEventBus);
        com.oliver.witchmod.entities.WitchModEntityAttributes.register(modEventBus);
        WitchModDataComponents.register(modEventBus);
        WitchModItems.register(modEventBus);
        WitchModBlocks.register(modEventBus);
        WitchModFluids.register(modEventBus);
        WitchModBlockEntities.register(modEventBus);
        WitchModMenus.register(modEventBus);
        com.oliver.witchmod.loot.WitchModLootModifiers.register(modEventBus);

        // force the curse/blessing holders to class-load so their register entries exist before RegisterEvent
        Curses.bootstrap();
        Blessings.bootstrap();

        // ward hook so EffectManager doesn't depend on the item directly; totem/holy-water protect via the
        // PROTECTED effect instead (gate lives in EffectManager.apply), so they need no hook here
        EffectManager.setWardHook(ItemWard::hasActiveWard, ItemWard::onBlock);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(com.oliver.witchmod.network.WitchModNetwork::onRegisterPayloads);

        // SERVER, not COMMON: these values decide cast resolution the client must agree with, and server
        // configs are per-world + auto-synced to clients on join (so the table's live odds preview matches)
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // cauldron interaction maps are shared mutable state — touch them on the main thread
        event.enqueueWork(com.oliver.witchmod.blocks.HolyWaterCauldron::registerInteractions);
        // read/merge the power-levels file now the effect registry is frozen
        event.enqueueWork(com.oliver.witchmod.data.PowerLevels::load);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // re-read power-levels so title-screen edits apply on world load (also /reload-able)
        com.oliver.witchmod.data.PowerLevels.load();
    }
}
