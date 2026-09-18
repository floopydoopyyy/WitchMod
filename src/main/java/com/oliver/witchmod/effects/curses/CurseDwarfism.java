package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * you're half the man you were. Half height, half health — and small enough that the
 * only sensible way to get anywhere is to climb on someone.
 *
 * <p><b>Both halves are attribute modifiers</b>, so nothing here fights vanilla. {@code Attributes.SCALE}
 * drives the bounding box as well as the model, which means shrinking genuinely lets you fit through
 * one-block gaps rather than just looking like it. {@code MAX_HEALTH} is scaled the same way.
 *
 * <p><b>⚠ Both are TRANSIENT, so {@code onTick} re-applies them.</b> A world reload drops transient modifiers
 * while the curse itself persists — you'd come back full-sized with full health and the curse still running.
 * This trap has now caught Gluttony, Heavy and Bad Swimmer in this project, so it's checked every tick.
 *
 * <p><b>⚠ Health must be clamped on the way in.</b> Lowering max health does not lower current health, so
 * without the clamp you'd sit at 20/10 until something damaged you — and vanilla renders that as a full bar,
 * hiding the entire downside of the curse.
 *
 * <p>Riding is handled in {@code CurseEventHandler} off the entity-interact event.
 */
public final class CurseDwarfism extends Effect {
    private static final ResourceLocation SCALE_MODIFIER_ID = EffectUtil.modifierId("curse_dwarfism_scale");
    private static final ResourceLocation HEALTH_MODIFIER_ID = EffectUtil.modifierId("curse_dwarfism_health");

    public CurseDwarfism() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.TURTLE_EGG);
    }

    /** you notice the moment you shrink (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        shrink(target);
        markDiscoveredByVictim(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // size-crisis synergy (with Giant): the oscillator owns the size while both are active.
        if (com.oliver.witchmod.synergy.Synergies.SIZE_CRISIS.activeFor(target)) {
            SizeCrisis.tick(target);
            return;
        }
        shrink(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        clearModifiers(target);
        SizeCrisis.clear(target); // end the oscillation cleanly if it was running
        target.stopRiding();
    }

    /** strip the dwarfism attribute modifiers (used by onRemove and the size-crisis synergy takeover). */
    public static void clearModifiers(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.SCALE, SCALE_MODIFIER_ID);
        EffectUtil.removeModifier(target, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID);
    }

    private static void shrink(ServerPlayer target) {
        AttributeInstance scale = target.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(SCALE_MODIFIER_ID)) {
            scale.addOrUpdateTransientModifier(new AttributeModifier(SCALE_MODIFIER_ID,
                    Config.DWARFISM_MODEL_SCALE.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && !health.hasModifier(HEALTH_MODIFIER_ID)) {
            health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID,
                    Config.DWARFISM_HEALTH_MULT.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            // lowering the maximum doesn't lower what you currently have — without this you'd sit above your
            // own cap, which vanilla just draws as a full bar, hiding the downside entirely.
            if (target.getHealth() > target.getMaxHealth()) {
                target.setHealth(target.getMaxHealth());
            }
        }
    }

    /**
     * hook for right-clicking something rideable — see {@code CurseEventHandler}.
     *
     * @return true if the interaction was consumed (so it doesn't also open a trade screen)
     */
    public static boolean tryRide(ServerPlayer rider, Entity mount) {
        if (rider.isPassenger() || mount == rider) {
            return false;
        }
        boolean allowed = (mount instanceof Player && Config.DWARFISM_RIDE_PLAYERS.get())
                || (mount instanceof Villager && Config.DWARFISM_RIDE_VILLAGERS.get());
        if (!allowed) {
            return false;
        }
        // force=true because a Player is not normally a valid vehicle — vanilla has no reason to allow it,
        // but nothing about it actually misbehaves. The mount keeps full control of itself: a rider only
        // steers a vehicle that reads rider input, and neither players nor villagers do.
        return rider.startRiding(mount, true);
    }
}
