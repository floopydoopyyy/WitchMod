package com.oliver.witchmod.items;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** Registers all items from CLAUDE.md section 3, plus the Recovery Compass modifier item (section 4.6). */
public final class WitchModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchMod.MODID);

    public static final DeferredItem<Item> CURSED_ESSENCE = ITEMS.registerSimpleItem("cursed_essence");

    public static final DeferredItem<PlayerEssenceItem> PLAYER_ESSENCE =
            ITEMS.register("player_essence", () -> new PlayerEssenceItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemCompendium> COMPENDIUM =
            ITEMS.register("compendium", () -> new ItemCompendium(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemVoodooDoll> VOODOO_DOLL =
            ITEMS.register("voodoo_doll", () -> new ItemVoodooDoll(new Item.Properties().stacksTo(1).durability(8)));

    public static final DeferredItem<ItemNeedle> NEEDLE = ITEMS.register("needle", () -> new ItemNeedle(new Item.Properties()));

    public static final DeferredItem<ItemWard> WARD =
            ITEMS.register("ward", () -> new ItemWard(new Item.Properties().stacksTo(1).durability(8)));

    public static final DeferredItem<ItemScryingMirror> SCRYING_MIRROR =
            ITEMS.register("scrying_mirror", () -> new ItemScryingMirror(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemEffigy> EFFIGY =
            ITEMS.register("effigy", () -> new ItemEffigy(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemGambleCoin> CURSED_COIN =
            ITEMS.register("cursed_coin", () -> new ItemGambleCoin(new Item.Properties(),
                    com.oliver.witchmod.data.CoinGamble.Type.CURSED));

    public static final DeferredItem<ItemGambleCoin> BLESSED_COIN =
            ITEMS.register("blessed_coin", () -> new ItemGambleCoin(new Item.Properties(),
                    com.oliver.witchmod.data.CoinGamble.Type.BLESSED));

    public static final DeferredItem<ItemGambleCoin> EXECUTIONERS_COIN =
            ITEMS.register("executioners_coin", () -> new ItemGambleCoin(new Item.Properties(),
                    com.oliver.witchmod.data.CoinGamble.Type.EXECUTIONER));

    // The jar family is one dynamic item: an empty JAR collects Player Essence; once it holds a curse/blessing
    // it renders + names itself as the Cursed / Blessed / Mixed variant (JarContents.itemFor) and is THROWN like
    // a splash potion. All four are the same ItemJar behaviour — only texture + display name differ.
    public static final DeferredItem<ItemJar> JAR = ITEMS.register("jar",
            () -> new ItemJar(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemJar> CURSED_JAR = ITEMS.register("cursed_jar",
            () -> new ItemJar(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemJar> BLESSED_JAR = ITEMS.register("blessed_jar",
            () -> new ItemJar(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemJar> MIXED_JAR = ITEMS.register("mixed_jar",
            () -> new ItemJar(new Item.Properties().stacksTo(1)));

    private WitchModItems() {}

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
