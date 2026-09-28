package com.oliver.witchmod.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** registers the mod's blocks: amethyst bell, cursed essence block, warding totem, ritual table, ledger. */
public final class WitchModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchMod.MODID);

    public static final DeferredBlock<AmethystBellBlock> AMETHYST_BELL = BLOCKS.registerBlock("amethyst_bell",
            AmethystBellBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noOcclusion().strength(2.5F));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> AMETHYST_BELL_ITEM =
            describedBlockItem("amethyst_bell", AMETHYST_BELL, "item.witchmod.amethyst_bell.desc");

    /** 9× cursed essence storage block; also bulk essence at the table. */
    public static final DeferredBlock<Block> CURSED_ESSENCE_BLOCK = BLOCKS.registerSimpleBlock("cursed_essence_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 6.0F).requiresCorrectToolForDrops());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CURSED_ESSENCE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(CURSED_ESSENCE_BLOCK);

    /** radius-based curse/blessing shield. */
    public static final DeferredBlock<WardingTotemBlock> WARDING_TOTEM = BLOCKS.registerBlock("warding_totem",
            WardingTotemBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WARDING_TOTEM_ITEM =
            describedBlockItem("warding_totem", WARDING_TOTEM, "item.witchmod.warding_totem.desc");

    /** the ritual table block — right-click opens its screen. */
    public static final DeferredBlock<BewitchingTableBlock> BEWITCHING_TABLE = BLOCKS.registerBlock("bewitching_table",
            BewitchingTableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(4.0F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BEWITCHING_TABLE_ITEM = ITEMS.registerSimpleBlockItem(BEWITCHING_TABLE);

    /** read-only ledger — a lectern-style block. */
    public static final DeferredBlock<LedgerBlock> LEDGER = BLOCKS.registerBlock("ledger",
            LedgerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).noOcclusion());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LEDGER_ITEM =
            describedBlockItem("ledger", LEDGER, "item.witchmod.ledger.desc");

    private WitchModBlocks() {}

    // a block item carrying a single grey translatable tooltip line.
    private static DeferredItem<net.minecraft.world.item.BlockItem> describedBlockItem(
            String name, DeferredBlock<? extends Block> block, String descKey) {
        return ITEMS.register(name,
                () -> new net.minecraft.world.item.BlockItem(block.get(), new net.minecraft.world.item.Item.Properties()) {
                    @Override
                    public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                            net.minecraft.world.item.Item.TooltipContext context,
                            java.util.List<net.minecraft.network.chat.Component> tooltip,
                            net.minecraft.world.item.TooltipFlag flag) {
                        tooltip.add(net.minecraft.network.chat.Component.translatable(descKey)
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                });
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
