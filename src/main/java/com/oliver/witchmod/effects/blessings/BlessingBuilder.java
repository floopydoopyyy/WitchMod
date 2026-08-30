package com.oliver.witchmod.effects.blessings;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Master builder (master-spec-style Builder, sacrificial item ANY PLANKS): the little cooldowns vanilla puts
 * between placing and breaking blocks are gone, so you can lay down and tear through blocks as fast as you can
 * click. Great for big builds and fast teardown.
 *
 * <p>Those delays live entirely client-side ({@code Minecraft.rightClickDelay} for place/use,
 * {@code MultiPlayerGameMode.destroyDelay} after a break), so the whole effect is a client one driven off the
 * synced {@link WitchModAttachments#BUILDER_ACTIVE} flag — the server just flips the flag and
 * {@code client/ClientCurseHandler} zeroes the two counters each tick. Discovered on apply.
 */
public final class BlessingBuilder extends Effect {
    private static final TagKey<Item> PLANKS = ItemTags.PLANKS;

    public BlessingBuilder() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.OAK_PLANKS);
    }

    @Override
    public Optional<TagKey<Item>> sacrificialTag() {
        return Optional.of(PLANKS); // any plank variant selects Builder at the Table
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.BUILDER_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.BUILDER_ACTIVE) != 1) {
            target.setData(WitchModAttachments.BUILDER_ACTIVE, 1); // self-heal after a respawn/relog
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.BUILDER_ACTIVE, -1);
    }
}
