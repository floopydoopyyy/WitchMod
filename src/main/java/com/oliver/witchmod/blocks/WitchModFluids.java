package com.oliver.witchmod.blocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.oliver.witchmod.WitchMod;

/**
 * purifying (holy) water — a real custom fluid (source + flowing) so it flows/fills like water, with editable
 * still/flowing textures. client rendering (textures/tint/fog) lives in {@code WitchModClient}.
 */
public final class WitchModFluids {
    public static final ResourceLocation STILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "block/purifying_water_still");
    public static final ResourceLocation FLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "block/purifying_water_flow");
    /**
     * ARGB tint multiplied over the (now already-coloured) texture. Neutral white so the texture drives the
     * colour; alpha is full because the texture itself carries the "slightly less translucent" look. Edit the
     * PNGs to restyle the water rather than this value.
     */
    public static final int TINT_COLOR = 0xFFFFFFFF;

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, WitchMod.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, WitchMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchMod.MODID);

    public static final DeferredHolder<FluidType, FluidType> PURIFYING_WATER_TYPE = FLUID_TYPES.register("purifying_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.witchmod.purifying_water")
                    // no infinite sources — a placed source only ever comes from a bucket (keeps it a scarce,
                    // minimally-spreading ritual fluid, and lets the block fire its place-sound on that one event).
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)) {
                @Override
                public boolean canDrownIn(net.minecraft.world.entity.LivingEntity entity) {
                    return false; // holy water never drowns you — the air bar stays full while submerged
                }
            });

    public static final DeferredHolder<Fluid, PurifyingWaterFluid.Source> PURIFYING_WATER =
            FLUIDS.register("purifying_water", () -> new PurifyingWaterFluid.Source(fluidProperties()));
    public static final DeferredHolder<Fluid, PurifyingWaterFluid.Flowing> PURIFYING_WATER_FLOWING =
            FLUIDS.register("purifying_water_flowing", () -> new PurifyingWaterFluid.Flowing(fluidProperties()));

    public static final DeferredBlock<PurifyingWaterBlock> PURIFYING_WATER_BLOCK = BLOCKS.registerBlock("purifying_water",
            props -> new PurifyingWaterBlock(PURIFYING_WATER.get(), props), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .noLootTable()
                    .liquid()
                    .sound(net.minecraft.world.level.block.SoundType.EMPTY)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    public static final DeferredItem<Item> PURIFYING_WATER_BUCKET = ITEMS.register("purifying_water_bucket",
            () -> new BucketItem(PURIFYING_WATER.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                        net.minecraft.world.item.Item.TooltipContext context,
                        java.util.List<net.minecraft.network.chat.Component> tooltip,
                        net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.add(net.minecraft.network.chat.Component.translatable("item.witchmod.purifying_water_bucket.desc")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    /** A holy-water cauldron (fill/empty like a water cauldron; made by consecrating a water cauldron with shards). */
    public static final DeferredBlock<net.minecraft.world.level.block.LayeredCauldronBlock> PURIFYING_WATER_CAULDRON =
            BLOCKS.registerBlock("purifying_water_cauldron",
                    props -> new net.minecraft.world.level.block.LayeredCauldronBlock(
                            net.minecraft.world.level.biome.Biome.Precipitation.NONE, HolyWaterCauldron.INTERACTIONS, props),
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER_CAULDRON));

    private WitchModFluids() {}

    private static BaseFlowingFluid.Properties fluidProperties() {
        return new BaseFlowingFluid.Properties(PURIFYING_WATER_TYPE, PURIFYING_WATER, PURIFYING_WATER_FLOWING)
                .bucket(PURIFYING_WATER_BUCKET)
                .block(PURIFYING_WATER_BLOCK)
                // bare-minimum spread: a big level drop-off per block (water = 1, lava = 2) means a source
                // barely creeps out before running dry, so it can't blanket a mountainside; short slope search.
                .levelDecreasePerBlock(4)
                .slopeFindDistance(2);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
