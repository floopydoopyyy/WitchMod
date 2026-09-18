package com.oliver.witchmod.ui;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** registers the ritual table's screen menu type. */
public final class WitchModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, WitchMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BewitchingTableMenu>> BEWITCHING_TABLE =
            MENUS.register("bewitching_table", () -> IMenuTypeExtension.create(
                    (windowId, inv, buf) -> new BewitchingTableMenu(windowId, inv, buf.readBlockPos())));

    private WitchModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
