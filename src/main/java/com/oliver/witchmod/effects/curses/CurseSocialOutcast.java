package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * nobody's there. Other players and villagers simply aren't rendered on your
 * client — no model, no nametag — unless they get right on top of you, or they hit you.
 *
 * <p><b>The hiding itself is pure client-side render</b> ({@code ClientCurseHandler} cancels
 * {@code RenderLivingEvent.Pre}), so nothing about the world actually changes: they're still there, still
 * solid, still able to kill you. You just can't see them coming.
 *
 * <p><b>The damage reveal has to be SERVER-driven, and that's the whole reason this class exists.</b> Who
 * dealt the damage is server-authoritative — the client is told that it was hurt, not reliably by whom — so
 * the set of currently-revealed entity ids is tracked here and synced down via
 * {@link WitchModAttachments#SOCIAL_OUTCAST_REVEALED}. The set is tiny (only whoever has hit you inside the
 * window) and only changes on a hit or an expiry, so syncing it whole costs nothing.
 *
 * <p>The window lapsing is what makes it a curse rather than a one-time inconvenience: a running fight keeps
 * your attacker on screen, but the moment they stop hitting you for {@code DAMAGE_REVEAL_TICKS} they
 * disappear again — mid-fight, while still hunting you.
 */
public final class CurseSocialOutcast extends Effect {
    /** victim -> (revealed entity id -> game tick the reveal lapses). */
    private static final Map<UUID, Map<Integer, Long>> REVEALED = new HashMap<>();
    /** victim -> ids currently inside reveal range, so the OUT→IN transition can be spotted. */
    private static final Map<UUID, Set<Integer>> IN_RANGE = new HashMap<>();

    public CurseSocialOutcast() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 35, () -> Items.WITHER_ROSE);
    }

    /** you notice the moment the world empties out (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE, 1);
        target.setData(WitchModAttachments.SOCIAL_OUTCAST_REVEALED, List.of());
        // deliberately NOT discovered here: until somebody actually pops into view there is nothing to
        // notice, and an empty world looks like an empty world.
    }

    @Override
    public void onRemove(ServerPlayer target) {
        REVEALED.remove(target.getUUID());
        IN_RANGE.remove(target.getUUID());
        target.setData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE, -1);
        target.setData(WitchModAttachments.SOCIAL_OUTCAST_REVEALED, List.of());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE) < 0) {
            target.setData(WitchModAttachments.SOCIAL_OUTCAST_ACTIVE, 1); // self-heal after a relog
        }
        watchForReveals(target);
        expireReveals(target);
    }

    /**
     * discovery fires the moment someone actually POPS INTO VIEW — the transition from out of reveal range
     * to inside it — rather than when the curse lands. Until something appears out of nowhere there's nothing
     * to notice: an empty world just looks like an empty world.
     *
     * <p>Mirrors the client's proximity test rather than being told about it, because the server knows every
     * position anyway and a round trip for a purely informational alert isn't worth it.
     */
    private void watchForReveals(ServerPlayer target) {
        double reveal = Config.OUTCAST_REVEAL_DISTANCE.get();
        Set<Integer> nowInRange = new HashSet<>();
        for (LivingEntity nearby : target.serverLevel().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(reveal), CurseSocialOutcast::isHideable)) {
            if (nearby != target && nearby.distanceToSqr(target) <= reveal * reveal) {
                nowInRange.add(nearby.getId());
            }
        }
        Set<Integer> before = IN_RANGE.put(target.getUUID(), nowInRange);
        if (before == null) {
            return; // first sweep — no transition to read yet
        }
        for (int id : nowInRange) {
            if (!before.contains(id)) {
                markDiscoveredByVictim(target); // something just materialised in front of you
                return;
            }
        }
    }

    private static void expireReveals(ServerPlayer target) {
        Map<Integer, Long> reveals = REVEALED.get(target.getUUID());
        if (reveals == null || reveals.isEmpty()) {
            return;
        }
        long now = target.level().getGameTime();
        boolean changed = false;
        Iterator<Map.Entry<Integer, Long>> it = reveals.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue()) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            push(target, reveals);
        }
    }

    /** the same rule the client renders by, so the two never disagree about who is hidden. */
    public static boolean isHideable(Entity entity) {
        return entity instanceof Player
                || (entity instanceof Villager && Config.OUTCAST_HIDES_VILLAGERS.get());
    }

    /**
     * someone hurt you, so you can see them for a while. Called from {@code CurseEventHandler}.
     *
     * <p>Only players and villagers are tracked, because they're the only things ever hidden — revealing a
     * zombie you could already see would just be pointless network traffic.
     */
    public static void onDamagedBy(ServerPlayer victim, Entity attacker) {
        if (!(attacker instanceof Player) && !(attacker instanceof Villager)) {
            return;
        }
        Map<Integer, Long> reveals = REVEALED.computeIfAbsent(victim.getUUID(), key -> new HashMap<>());
        long until = victim.level().getGameTime() + Config.OUTCAST_DAMAGE_REVEAL_TICKS.get();
        Long previous = reveals.put(attacker.getId(), until);
        if (previous == null) {
            push(victim, reveals); // only re-sync when the SET changes, not on every hit that extends a timer
        }
    }

    private static void push(ServerPlayer victim, Map<Integer, Long> reveals) {
        victim.setData(WitchModAttachments.SOCIAL_OUTCAST_REVEALED, new ArrayList<>(reveals.keySet()));
    }
}
