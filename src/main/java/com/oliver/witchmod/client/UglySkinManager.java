package com.oliver.witchmod.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * client half of the Ugly curse: swaps a cursed player's rendered skin for one of the mod's own.
 *
 * <p><b>Skins are discovered from the resource pack at runtime</b>, from
 * {@code assets/witchmod/textures/entity/ugly/}. Any {@code .png} dropped in that folder is picked up with
 * no code change and no registration — which is the entire point of doing it this way. Names ending in
 * {@code _slim} are treated as Alex-armed. The list is sorted so every client agrees on the ordering, which
 * is what lets a single server-rolled number land everyone on the same face.
 *
 * <p><b>The override works by replacing {@code PlayerInfo.skinLookup} reflectively.</b> That field is where
 * {@code AbstractClientPlayer.getSkin()} ultimately reads from, so swapping it changes the skin everywhere at
 * once — the world model, the nametag-height model, first-person hands, the inventory doll and the tab list
 * — rather than only the places a render hook could reach. It's a private FINAL field, but non-static finals
 * are writable once {@code setAccessible(true)} succeeds, and this project already uses reflection for
 * {@code LivingEntity.attackStrengthTicker} rather than take on mixin infrastructure.
 *
 * <p><b>Everything is re-asserted every tick</b>, which is what makes it survive relogging, dying, changing
 * dimension, and other players wandering into view long after the curse landed. There is deliberately no
 * one-off "apply" moment that could be missed. The original lookup is kept per player and put back the
 * moment the curse ends.
 */
public final class UglySkinManager {
    private static final String SKIN_FOLDER = "textures/entity/ugly";

    private static final Field SKIN_LOOKUP = resolveSkinLookup();
    /** players we've overridden, and the lookup to give them back. */
    private static final Map<UUID, Supplier<PlayerSkin>> ORIGINALS = new HashMap<>();

    private static List<PlayerSkin> skins = List.of();
    private static boolean scanned;
    private static boolean warnedNoSkins;

    private UglySkinManager() {}

    private static Field resolveSkinLookup() {
        try {
            Field field = PlayerInfo.class.getDeclaredField("skinLookup");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[Ugly] Could not access PlayerInfo.skinLookup; the curse will do nothing.", e);
            return null;
        }
    }

    /** called every client tick. */
    public static void clientTick(Minecraft minecraft) {
        if (SKIN_LOOKUP == null || minecraft.level == null || minecraft.getConnection() == null) {
            // disconnected. Everything we were holding refers to PlayerInfo objects from a connection that
            // no longer exists, so it must be dropped — otherwise the stale entries make us think we've
            // already uglified someone and we never touch their NEW PlayerInfo on rejoin.
            ORIGINALS.clear();
            return;
        }
        if (!scanned) {
            reload();
        }

        Set<UUID> stillCursed = new HashSet<>();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            int roll = player.getData(WitchModAttachments.UGLY_SKIN);
            if (roll < 0 || skins.isEmpty()) {
                continue;
            }
            stillCursed.add(player.getUUID());
            apply(minecraft, player, skins.get(Math.floorMod(roll, skins.size())));
        }

        // anyone we'd uglified who no longer is — cured, or simply out of range now — gets their face back.
        ORIGINALS.keySet().removeIf(id -> {
            if (stillCursed.contains(id)) {
                return false;
            }
            restore(minecraft, id);
            return true;
        });
    }

    private static void apply(Minecraft minecraft, AbstractClientPlayer player, PlayerSkin ugly) {
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.getUUID());
        if (info == null) {
            return; // not in the tab list yet; we'll catch them next tick
        }
        // ask the CURRENT PlayerInfo what it's actually showing rather than trusting our own bookkeeping.
        // reconnecting builds a brand-new PlayerInfo, so a "have we done this player yet" flag would say yes
        // and leave the fresh one untouched — which is exactly why the skin reverted on relog.
        if (info.getSkin() == ugly) {
            return; // already wearing this exact skin
        }
        try {
            @SuppressWarnings("unchecked")
            Supplier<PlayerSkin> original = (Supplier<PlayerSkin>) SKIN_LOOKUP.get(info);
            ORIGINALS.put(player.getUUID(), original);
            SKIN_LOOKUP.set(info, (Supplier<PlayerSkin>) () -> ugly);
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[Ugly] Could not swap the skin for {}.", player.getName().getString(), e);
        }
    }

    private static void restore(Minecraft minecraft, UUID id) {
        Supplier<PlayerSkin> original = ORIGINALS.get(id);
        PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
        if (original == null || info == null) {
            return;
        }
        try {
            SKIN_LOOKUP.set(info, original);
        } catch (Exception e) {
            WitchMod.LOGGER.warn("[Ugly] Could not restore a skin.", e);
        }
    }

    /** re-reads the folder. Called on the first tick and on a resource reload, so F3+T picks up new files. */
    public static void reload() {
        scanned = true;
        List<ResourceLocation> found = new ArrayList<>(Minecraft.getInstance().getResourceManager()
                .listResources(SKIN_FOLDER, location -> location.getNamespace().equals(WitchMod.MODID)
                        && location.getPath().endsWith(".png"))
                .keySet());
        // sorted so every client walks the list in the same order — otherwise the same server roll would
        // show different faces on different machines.
        found.sort(ResourceLocation::compareTo);

        List<PlayerSkin> loaded = new ArrayList<>(found.size());
        for (ResourceLocation texture : found) {
            String path = texture.getPath();
            boolean slim = path.endsWith("_slim.png");
            loaded.add(new PlayerSkin(texture, null, null, null,
                    slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false));
        }
        skins = List.copyOf(loaded);

        if (skins.isEmpty() && !warnedNoSkins) {
            warnedNoSkins = true;
            WitchMod.LOGGER.warn("[Ugly] No skins in assets/{}/{}/ - the curse will have nothing to show. "
                    + "Drop 64x64 player-skin PNGs in there (see readme.txt in that folder).",
                    WitchMod.MODID, SKIN_FOLDER);
        } else if (!skins.isEmpty()) {
            warnedNoSkins = false;
            // worth logging the count: Minecraft silently drops any file whose path isn't lowercase
            // a-z/0-9/_/-/. (a space or a capital is enough), so a skin you added may simply not be here.
            // those show up separately as "Invalid path in pack: ... ignoring".
            WitchMod.LOGGER.info("[Ugly] Loaded {} ugly skin(s).", skins.size());
        }
    }
}
