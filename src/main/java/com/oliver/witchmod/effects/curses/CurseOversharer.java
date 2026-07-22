package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.OversharerMessages;

/**
 * You just... tell everyone (master-spec Oversharer). Every so often the victim blurts a piece of personal
 * information into server chat — as if they'd typed it themselves — wrapped in a goofy line from a writable
 * list rather than stated flatly.
 *
 * <p>Its purpose is to make a distant player <b>trackable</b>: coordinates, Y level, which way they're
 * facing and what dimension they're in are all real intel for someone hunting them, while held item, armour
 * and health say how ready for a fight they are.
 *
 * <p>The lines live in {@code data/witchmod/text/oversharer.json}, keyed by category, each with a
 * {@code {value}} placeholder this fills in. A category is only ever picked if the file actually has lines
 * for it AND a value can be computed, so leaks can be turned off just by emptying their list — and new ones
 * can be added by anyone who wants to write templates for a key this already computes.
 */
public final class CurseOversharer extends Effect {
    /** Every category this knows how to compute a value for. Templates opt in by category name. */
    private static final String[] KNOWN_CATEGORIES = {
            "coords", "biome", "y", "spawn", "held", "armour",
            "health", "facing", "dimension", "standing", "xp"
    };

    public CurseOversharer() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 32, () -> Items.MAP);
    }

    /** You find out the first time you catch yourself broadcasting your own coordinates (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int min = Config.OVERSHARER_INTERVAL_MIN.get();
        int max = Math.max(min, Config.OVERSHARER_INTERVAL_MAX.get());
        // A jittered interval rather than a fixed one, seeded off the remaining time so it isn't a metronome.
        int interval = min + Math.floorMod(ticksRemaining, max - min + 1);
        if (!EffectUtil.every(ticksRemaining, interval)) {
            return;
        }
        overshare(target);
    }

    private void overshare(ServerPlayer target) {
        // Only the categories that have templates loaded — so an empty/edited file can't force a blank line.
        List<String> pool = new ArrayList<>();
        List<String> available = OversharerMessages.availableCategories();
        for (String known : KNOWN_CATEGORIES) {
            if (available.contains(known)) {
                pool.add(known);
            }
        }
        if (pool.isEmpty()) {
            return;
        }

        // Pick a category that actually has a value right now, trying a few before giving up (e.g. "held"
        // when your hand is empty may still have a value, but this guards anything that can return null).
        for (int attempt = 0; attempt < pool.size(); attempt++) {
            String category = pool.get(target.getRandom().nextInt(pool.size()));
            String value = valueFor(target, category);
            if (value == null) {
                continue;
            }
            String template = OversharerMessages.pickTemplate(category, target.getRandom());
            if (template == null) {
                continue;
            }
            String line = template.replace("{value}", value)
                    .replace("{player}", target.getGameProfile().getName());
            broadcastAsChat(target, line);
            markDiscoveredByVictim(target);
            return;
        }
    }

    /** Sent as {@code chat.type.text}, so it appears exactly as if the victim typed it into chat. */
    private static void broadcastAsChat(ServerPlayer target, String line) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            return;
        }
        Component chat = Component.translatable("chat.type.text",
                target.getDisplayName(), Component.literal(line));
        server.getPlayerList().broadcastSystemMessage(chat, false);
    }

    @Nullable
    private static String valueFor(ServerPlayer player, String category) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        return switch (category) {
            case "coords" -> pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            case "y" -> String.valueOf(pos.getY());
            case "biome" -> friendly(level.getBiome(pos).unwrapKey()
                    .map(key -> key.location().getPath()).orElse("somewhere"));
            case "spawn" -> spawnText(player, level);
            case "held" -> heldText(player);
            case "armour" -> armourText(player);
            case "health" -> Math.round(player.getHealth()) + " out of " + Math.round(player.getMaxHealth());
            case "facing" -> capitalise(player.getDirection().getName());
            case "dimension" -> dimensionText(level);
            case "standing" -> standingText(level, pos);
            case "xp" -> String.valueOf(player.experienceLevel);
            default -> null;
        };
    }

    private static String spawnText(ServerPlayer player, ServerLevel level) {
        BlockPos bed = player.getRespawnPosition();
        BlockPos spawn = bed != null ? bed : level.getSharedSpawnPos();
        return spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ();
    }

    private static String heldText(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() ? "absolutely nothing" : held.getHoverName().getString();
    }

    private static String armourText(ServerPlayer player) {
        List<String> pieces = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                pieces.add(stack.getHoverName().getString());
            }
        }
        if (pieces.isEmpty()) {
            return "no armour whatsoever";
        }
        if (pieces.size() == 1) {
            return pieces.get(0) + " and nothing else";
        }
        return String.join(", ", pieces.subList(0, pieces.size() - 1))
                + " and " + pieces.get(pieces.size() - 1);
    }

    private static String dimensionText(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return "the Overworld";
        }
        if (level.dimension() == Level.NETHER) {
            return "the Nether";
        }
        if (level.dimension() == Level.END) {
            return "the End";
        }
        return friendly(level.dimension().location().getPath());
    }

    private static String standingText(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.isAir()) {
            return "thin air, apparently";
        }
        return friendly(below.getBlock().getName().getString());
    }

    /** Turns a registry-ish path (snake_case) into readable words. */
    private static String friendly(String raw) {
        String spaced = raw.replace('_', ' ').trim();
        return spaced.isEmpty() ? raw : capitalise(spaced);
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
