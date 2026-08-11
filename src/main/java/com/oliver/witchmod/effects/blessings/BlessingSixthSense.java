package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.datafixers.util.Pair;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.Structure;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Blessings;

/**
 * A prickle at the back of your neck (master-spec Sixth Sense, sacrificial item COMPASS): every so often you
 * get a detailed hint on the ACTION BAR about something nearby — another player, an ordinary structure, or a
 * rare biome — with a compass direction and rough distance.
 *
 * <p>The cadence is a randomised gap ({@code sixthSenseIntervalMin..Max}), so it's an occasional prickle, not
 * a spam. RARE structures (ancient cities, end cities, bastion remnants, nether fortresses, buried treasure)
 * are an <b>override</b>: on every scheduled sense it checks for one nearby FIRST and, if found, announces it
 * and then waits a longer {@code sixthSenseRareCooldown} — so you're reliably told when something valuable is
 * close, without it drowning out the ordinary hints.
 */
public final class BlessingSixthSense extends Effect {
    /** player -> ticks until the next sense. */
    private static final Map<UUID, Integer> NEXT = new HashMap<>();

    private record Sense(TagKey<Structure> tag, String name) {}

    /** Ordinary structures — common enough to be flavour, on the normal cadence. */
    private static final List<Sense> STRUCTURES = List.of(
            new Sense(StructureTags.VILLAGE, "a village"),
            new Sense(StructureTags.MINESHAFT, "a mineshaft"),
            new Sense(StructureTags.SHIPWRECK, "a shipwreck"),
            new Sense(StructureTags.RUINED_PORTAL, "a ruined portal"),
            new Sense(StructureTags.OCEAN_RUIN, "ocean ruins"),
            new Sense(StructureTags.EYE_OF_ENDER_LOCATED, "a stronghold"),
            new Sense(StructureTags.ON_TRIAL_CHAMBERS_MAPS, "a trial chamber"));

    /** Rare, high-value structures — the override list, each its own witchmod tag so it can be named. */
    private static final List<Sense> RARE_STRUCTURES = List.of(
            new Sense(rareTag("rare_ancient_city"), "an ancient city"),
            new Sense(rareTag("rare_end_city"), "an end city"),
            new Sense(rareTag("rare_bastion_remnant"), "a bastion remnant"),
            new Sense(rareTag("rare_fortress"), "a nether fortress"),
            new Sense(rareTag("rare_buried_treasure"), "buried treasure"));

    private static final List<ResourceKey<Biome>> RARE_BIOMES = List.of(
            Biomes.MUSHROOM_FIELDS, Biomes.ICE_SPIKES, Biomes.FLOWER_FOREST, Biomes.CHERRY_GROVE,
            Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS,
            Biomes.DEEP_DARK, Biomes.LUSH_CAVES, Biomes.SUNFLOWER_PLAINS, Biomes.OLD_GROWTH_PINE_TAIGA);

    private static TagKey<Structure> rareTag(String path) {
        return TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, path));
    }

    public BlessingSixthSense() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.COMPASS);
    }

    /** You find out the first time an insight surfaces (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        NEXT.put(target.getUUID(), 60 + target.getRandom().nextInt(100)); // first sense comes soon
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT.remove(target.getUUID());
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        NEXT.put(target.getUUID(), 0); // sense on the very next tick, through the real sensing logic
        return "a sixth-sense hint will fire on the next tick";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int countdown = NEXT.computeIfAbsent(target.getUUID(), k -> rollNormal(target.getRandom()));
        if (countdown > 0) {
            NEXT.put(target.getUUID(), countdown - 1);
            return;
        }
        ServerLevel level = target.serverLevel();

        // Rare override FIRST: if something valuable is nearby, announce it and wait the longer cooldown.
        Component rare = senseRareStructure(level, target);
        if (rare != null) {
            show(target, rare);
            NEXT.put(target.getUUID(), rollRare(target.getRandom()));
            return;
        }

        // Otherwise an ordinary hint: try the three categories in a random order, show the first that resolves.
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        java.util.Collections.shuffle(order, new java.util.Random(target.getRandom().nextLong()));
        for (int category : order) {
            Component hint = switch (category) {
                case 0 -> sensePlayer(level, target);
                case 1 -> senseStructure(level, target);
                default -> senseBiome(level, target);
            };
            if (hint != null) {
                show(target, hint);
                break;
            }
        }
        NEXT.put(target.getUUID(), rollNormal(target.getRandom()));
    }

    private static int rollNormal(RandomSource random) {
        int min = Config.SIXTHSENSE_INTERVAL_MIN.get();
        int max = Math.max(min, Config.SIXTHSENSE_INTERVAL_MAX.get());
        return min + random.nextInt(max - min + 1);
    }

    private static int rollRare(RandomSource random) {
        int min = Config.SIXTHSENSE_RARE_COOLDOWN_MIN.get();
        int max = Math.max(min, Config.SIXTHSENSE_RARE_COOLDOWN_MAX.get());
        return min + random.nextInt(max - min + 1);
    }

    private void show(ServerPlayer target, Component hint) {
        target.displayClientMessage(hint, true); // action bar
        markDiscoveredByVictim(target);
    }

    /** The nearest rare structure that can generate in this dimension, or null if none is in range. */
    @Nullable
    private static Component senseRareStructure(ServerLevel level, ServerPlayer self) {
        int radius = Config.SIXTHSENSE_RARE_RADIUS_CHUNKS.get();
        String bestName = null;
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (Sense sense : RARE_STRUCTURES) {
            BlockPos pos = level.findNearestMapStructure(sense.tag(), self.blockPosition(), radius, false);
            if (pos == null) {
                continue;
            }
            double d = distSqr2d(self.getX(), self.getZ(), pos.getX(), pos.getZ());
            if (d < bestDist) {
                bestDist = d;
                bestPos = pos;
                bestName = sense.name();
            }
        }
        if (bestPos == null) {
            return null;
        }
        String dir = direction(self.getX(), self.getZ(), bestPos.getX(), bestPos.getZ());
        int dist = (int) Math.sqrt(bestDist);
        return Component.literal("Your sixth sense stirs — " + bestName + " to the " + dir + ", ~" + dist + " blocks")
                .withStyle(ChatFormatting.GOLD);
    }

    @Nullable
    private static Component sensePlayer(ServerLevel level, ServerPlayer self) {
        double range = Config.SIXTHSENSE_PLAYER_RANGE.get();
        ServerPlayer nearest = null;
        double best = range * range;
        for (ServerPlayer other : level.getPlayers(p -> p != self && p.isAlive() && !p.isSpectator())) {
            double d = other.distanceToSqr(self);
            if (d <= best) {
                best = d;
                nearest = other;
            }
        }
        if (nearest == null) {
            return null;
        }
        String dir = direction(self.getX(), self.getZ(), nearest.getX(), nearest.getZ());
        return hint("You sense " + nearest.getGameProfile().getName() + " to the " + dir + ", ~" + (int) Math.sqrt(best) + " blocks");
    }

    @Nullable
    private static Component senseStructure(ServerLevel level, ServerPlayer self) {
        Sense sense = STRUCTURES.get(self.getRandom().nextInt(STRUCTURES.size()));
        BlockPos pos = level.findNearestMapStructure(sense.tag(),
                self.blockPosition(), Config.SIXTHSENSE_STRUCTURE_RADIUS_CHUNKS.get(), false);
        if (pos == null) {
            return null;
        }
        String dir = direction(self.getX(), self.getZ(), pos.getX(), pos.getZ());
        int dist = (int) Math.sqrt(distSqr2d(self.getX(), self.getZ(), pos.getX(), pos.getZ()));
        return hint("You sense " + sense.name() + " to the " + dir + ", ~" + dist + " blocks");
    }

    @Nullable
    private static Component senseBiome(ServerLevel level, ServerPlayer self) {
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                holder -> RARE_BIOMES.stream().anyMatch(holder::is),
                self.blockPosition(), Config.SIXTHSENSE_BIOME_RADIUS.get(), 32, 64);
        if (found == null) {
            return null;
        }
        BlockPos pos = found.getFirst();
        String name = found.getSecond().unwrapKey().map(k -> prettify(k.location().getPath())).orElse("a rare biome");
        String dir = direction(self.getX(), self.getZ(), pos.getX(), pos.getZ());
        int dist = (int) Math.sqrt(distSqr2d(self.getX(), self.getZ(), pos.getX(), pos.getZ()));
        return hint("You sense " + name + " to the " + dir + ", ~" + dist + " blocks");
    }

    private static Component hint(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static double distSqr2d(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    /** 8-point compass bearing from (x1,z1) to (x2,z2). */
    private static String direction(double x1, double z1, double x2, double z2) {
        double angle = Mth.atan2(x2 - x1, -(z2 - z1)); // 0 = north (−Z), clockwise
        int octant = (int) Math.round(angle / (Math.PI / 4.0)) & 7;
        return switch (octant) {
            case 0 -> "north";
            case 1 -> "north-east";
            case 2 -> "east";
            case 3 -> "south-east";
            case 4 -> "south";
            case 5 -> "south-west";
            case 6 -> "west";
            default -> "north-west";
        };
    }

    private static String prettify(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
