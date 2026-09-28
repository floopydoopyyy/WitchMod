package com.oliver.witchmod.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import com.mojang.serialization.Codec;
import com.oliver.witchmod.Config;

/**
 * adds a rarity-weighted named jar to a loot chest's drops. one instance per chest type (bound to its loot
 * table by the json condition); the per-type "one in N" chance is read live from config, so 0 disables it.
 */
public final class JarLootModifier extends LootModifier {
    public static final MapCodec<JarLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).and(Codec.STRING.fieldOf("chest").forGetter(m -> m.chestKey))
                    .apply(inst, JarLootModifier::new));

    private final String chestKey;

    public JarLootModifier(LootItemCondition[] conditions, String chestKey) {
        super(conditions);
        this.chestKey = chestKey;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!Config.JAR_DROPS_ENABLED.get() || !Config.JAR_CHEST_DROPS_ENABLED.get()) {
            return loot;
        }
        int oneIn = Config.jarChestOneIn(chestKey);
        RandomSource rng = context.getRandom();
        if (oneIn > 0 && rng.nextInt(oneIn) == 0) {
            ItemStack jar = NamedJars.rollStack(rng);
            if (!jar.isEmpty()) {
                loot.add(jar);
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
