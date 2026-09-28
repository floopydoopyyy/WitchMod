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
 * A prickle at the back of your neck: every so often you
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

    /** {@code nameKey} is a translatable lang key for the sensed thing's name (organised under witchmod.sixth_sense.*). */
    private record Sense(TagKey<Structure> tag, String nameKey) {}

    /** ordinary structures — common enough to be flavour, on the normal cadence. */
    private static final List<Sense> STRUCTURES = List.of(
            new Sense(StructureTags.VILLAGE, "witchmod.sixth_sense.structure.village"),
            new Sense(StructureTags.MINESHAFT, "witchmod.sixth_sense.structure.mineshaft"),
            new Sense(StructureTags.SHIPWRECK, "witchmod.sixth_sense.structure.shipwreck"),
            new Sense(StructureTags.RUINED_PORTAL, "witchmod.sixth_sense.structure.ruined_portal"),
            new Sense(StructureTags.OCEAN_RUIN, "witchmod.sixth_sense.structure.ocean_ruins"),
            new Sense(StructureTags.ON_TRIAL_CHAMBERS_MAPS, "witchmod.sixth_sense.structure.trial_chamber"));

    /** rare, high-value structures — the override list, each its own witchmod tag so it can be named. */
    private static final List<Sense> RARE_STRUCTURES = List.of(
            new Sense(rareTag("rare_ancient_city"), "witchmod.sixth_sense.structure.ancient_city"),
            new Sense(rareTag("rare_end_city"), "witchmod.sixth_sense.structure.end_city"),
            new Sense(rareTag("rare_bastion_remnant"), "witchmod.sixth_sense.structure.bastion_remnant"),
            new Sense(rareTag("rare_fortress"), "witchmod.sixth_sense.structure.nether_fortress"),
            new Sense(rareTag("rare_stronghold"), "witchmod.sixth_sense.structure.stronghold"),
            new Sense(rareTag("rare_buried_treasure"), "witchmod.sixth_sense.structure.buried_treasure"));

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

    /** you find out the first time an insight surfaces (Rule 2). */
    @Override
    public java.util.Optional<net.minecraft.network.chat.Component> scryingDetail(ServerPlayer target) {
        int next = NEXT.getOrDefault(target.getUUID(), 0);
        return java.util.Optional.of(next <= 20
                ? net.minecraft.network.chat.Component.translatable("witchmod.scry.sixth_sense.ready")
                : net.minecraft.network.chat.Component.translatable("witchmod.scry.sixth_sense.recharging", next / 20));
    }

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

        // rare override FIRST: if something valuable is nearby, announce it and wait the longer cooldown.
        Component rare = senseRareStructure(level, target);
        if (rare != null) {
            show(target, rare);
            NEXT.put(target.getUUID(), rollRare(target.getRandom()));
            return;
        }

        // otherwise an ordinary hint: try the three categories in a random order, show the first that resolves.
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

    /** the nearest rare structure that can generate in this dimension, or null if none is in range. */
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
                bestName = sense.nameKey();
            }
        }
        if (bestPos == null) {
            return null;
        }
        int dist = (int) Math.sqrt(bestDist);
        return Component.translatable("witchmod.sixth_sense.rare", Component.translatable(bestName),
                dirComponent(self.getX(), self.getZ(), bestPos.getX(), bestPos.getZ()), dist)
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
        return hint(Component.literal(nearest.getGameProfile().getName()),
                dirComponent(self.getX(), self.getZ(), nearest.getX(), nearest.getZ()), (int) Math.sqrt(best));
    }

    @Nullable
    private static Component senseStructure(ServerLevel level, ServerPlayer self) {
        Sense sense = STRUCTURES.get(self.getRandom().nextInt(STRUCTURES.size()));
        BlockPos pos = level.findNearestMapStructure(sense.tag(),
                self.blockPosition(), Config.SIXTHSENSE_STRUCTURE_RADIUS_CHUNKS.get(), false);
        if (pos == null) {
            return null;
        }
        int dist = (int) Math.sqrt(distSqr2d(self.getX(), self.getZ(), pos.getX(), pos.getZ()));
        return hint(Component.translatable(sense.nameKey()),
                dirComponent(self.getX(), self.getZ(), pos.getX(), pos.getZ()), dist);
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
        // only RARE_BIOMES match, so the found biome has a witchmod.sixth_sense.biome.<path> key; fall back to a generic one.
        Component name = found.getSecond().unwrapKey()
                .map(k -> (Component) Component.translatable("witchmod.sixth_sense.biome." + k.location().getPath()))
                .orElse(Component.translatable("witchmod.sixth_sense.biome.unknown"));
        int dist = (int) Math.sqrt(distSqr2d(self.getX(), self.getZ(), pos.getX(), pos.getZ()));
        return hint(name, dirComponent(self.getX(), self.getZ(), pos.getX(), pos.getZ()), dist);
    }

    /** "You sense &lt;thing&gt; to the &lt;dir&gt;, ~&lt;dist&gt; blocks" — the ordinary action-bar insight. */
    private static Component hint(Component thing, Component dir, int dist) {
        return Component.translatable("witchmod.sixth_sense.hint", thing, dir, dist).withStyle(ChatFormatting.AQUA);
    }

    private static double distSqr2d(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    /** the translatable 8-point compass bearing from (x1,z1) to (x2,z2), keyed under witchmod.sixth_sense.dir.*. */
    private static Component dirComponent(double x1, double z1, double x2, double z2) {
        double angle = Mth.atan2(x2 - x1, -(z2 - z1)); // 0 = north (−Z), clockwise
        int octant = (int) Math.round(angle / (Math.PI / 4.0)) & 7;
        String key = switch (octant) {
            case 0 -> "north";
            case 1 -> "north_east";
            case 2 -> "east";
            case 3 -> "south_east";
            case 4 -> "south";
            case 5 -> "south_west";
            case 6 -> "west";
            default -> "north_west";
        };
        return Component.translatable("witchmod.sixth_sense.dir." + key);
    }
}
