package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.synergy.Synergies;

/**
 * size_crisis synergy (Giant + Dwarfism): the two curses can't agree on a size, so the player flickers between
 * huge and tiny on a random timer, wearing whichever curse's traits they're currently sized as. while both
 * curses are active this OWNS the size — Giant/Dwarfism hand their onTick over — and it cleans up the instant
 * either is removed. no movement/attack-speed penalty in either mode; the giant reach is kept (halved while
 * small); shrinking on top of a mount crushes it.
 */
public final class SizeCrisis {
    private static final ResourceLocation SCALE_ID = EffectUtil.modifierId("size_crisis_scale");
    private static final ResourceLocation HEALTH_ID = EffectUtil.modifierId("size_crisis_health");
    private static final ResourceLocation REACH_ID = EffectUtil.modifierId("size_crisis_reach");

    /** true = currently GIANT-sized, false = dwarf; absent = not in a crisis. */
    private static final Map<UUID, Boolean> GIANT = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SWITCH = new HashMap<>();
    private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

    private SizeCrisis() {}

    /** true when mid-crisis and currently the SMALL form — gates the giant's damage/melee traits. */
    public static boolean isSmall(ServerPlayer player) {
        Boolean giant = GIANT.get(player.getUUID());
        return giant != null && !giant;
    }

    /** driven from both curses' onTick (guarded to run once a tick); owns the size while the synergy is live. */
    public static void tick(ServerPlayer p) {
        UUID id = p.getUUID();
        long now = p.serverLevel().getGameTime();
        if (LAST_TICK.getOrDefault(id, -1L) == now) {
            return;
        }
        LAST_TICK.put(id, now);
        if (!Synergies.SIZE_CRISIS.activeFor(p)) {
            clear(p);
            return;
        }
        boolean giant = GIANT.computeIfAbsent(id, k -> true);
        if (now >= NEXT_SWITCH.getOrDefault(id, 0L)) {
            boolean flipped = !giant; // guaranteed visible flip; the TIMING is what's random
            switchMode(p, giant, flipped);
            giant = flipped;
            GIANT.put(id, giant);
            NEXT_SWITCH.put(id, now + rollGap(p));
        }
        applySize(p, giant);
        if (giant) {
            CurseGiant.stomp(p); // wearing the giant's trait: crush anything underfoot
        }
    }

    private static long rollGap(ServerPlayer p) {
        int min = Config.SIZE_CRISIS_MIN_GAP_TICKS.get();
        int max = Math.max(min, Config.SIZE_CRISIS_MAX_GAP_TICKS.get());
        return min + p.getRandom().nextInt(max - min + 1);
    }

    private static void switchMode(ServerPlayer p, boolean wasGiant, boolean nowGiant) {
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(),
                24, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, p.blockPosition(),
                nowGiant ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.SLIME_SQUISH,
                SoundSource.PLAYERS, 0.7F, nowGiant ? 0.6F : 1.6F);
        // shrinking on top of someone/a villager CRUSHES them.
        if (wasGiant && !nowGiant && p.getVehicle() instanceof LivingEntity mount
                && (mount instanceof Player || mount instanceof Villager)) {
            mount.hurt(WitchModDamageTypes.stomp(level, p), Config.SIZE_CRISIS_CRUSH_DAMAGE.get().floatValue());
            level.sendParticles(ParticleTypes.EXPLOSION, mount.getX(),
                    mount.getY() + mount.getBbHeight() * 0.5, mount.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, mount.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    private static void applySize(ServerPlayer p, boolean giant) {
        // take the size over from the curses' own modifiers so nothing double-stacks.
        CurseGiant.clearModifiers(p);
        CurseDwarfism.clearModifiers(p);

        double scale = giant ? Config.GIANT_SCALE.get() : Config.DWARFISM_MODEL_SCALE.get();
        setMult(p, Attributes.SCALE, SCALE_ID, scale - 1.0);

        AttributeInstance health = p.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            if (giant) {
                health.removeModifier(HEALTH_ID);
            } else {
                setMultInst(health, HEALTH_ID, Config.DWARFISM_HEALTH_MULT.get() - 1.0);
                if (p.getHealth() > p.getMaxHealth()) {
                    p.setHealth(p.getMaxHealth()); // lowering the cap doesn't lower current health on its own
                }
            }
        }

        // keep the giant reach in both forms, but halve the EXTRA while small; no speed/attack-speed penalty at all.
        double reachBonus = Config.GIANT_REACH_MULT.get() - 1.0;
        if (!giant) {
            reachBonus *= Config.SIZE_CRISIS_SMALL_REACH_MULT.get();
        }
        setMult(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, reachBonus);
    }

    private static void setMult(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) {
            setMultInst(inst, id, amount);
        }
    }

    private static void setMultInst(AttributeInstance inst, ResourceLocation id, double amount) {
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** end the crisis: strip the crisis modifiers + state, so the surviving curse resumes its own size next tick. */
    public static void clear(ServerPlayer p) {
        UUID id = p.getUUID();
        GIANT.remove(id);
        NEXT_SWITCH.remove(id);
        LAST_TICK.remove(id);
        EffectUtil.removeModifier(p, Attributes.SCALE, SCALE_ID);
        EffectUtil.removeModifier(p, Attributes.MAX_HEALTH, HEALTH_ID);
        EffectUtil.removeModifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
    }
}
