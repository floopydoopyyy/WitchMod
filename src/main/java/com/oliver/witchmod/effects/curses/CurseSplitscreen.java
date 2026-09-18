package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.tags.TagKey;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * splitscreen (sacrificial item ANY SIGN): drags the nearest player into a shared, console-style split screen —
 * each of you sees the OTHER's point of view in a side panel (client render). Pulled out of Bedrock Moment
 * because the gag was strong enough to stand alone.
 *
 * <p><b>Pairing is sticky</b> (like Soul Bond): the cursed victim grabs the nearest eligible player within
 * {@code splitscreenRange} (22 blocks) and holds onto them until they leave that range — then the split ends
 * and you both play normally until someone comes back. Only ever TWO players.
 *
 * <p><b>Transitions</b> are a fake "Entering/Exiting splitscreen…" loading screen (0.4–2s), a wink at how
 * console split screen stalls. <b>Gameplay is otherwise untouched</b> except the shared-screen quirk: while
 * EITHER of you has a sign open, both are frozen (you can't act during a shared text box) — but if either of
 * you TAKES DAMAGE, the sign is force-closed for both so you're never helpless while something hits you.
 *
 * <p>Phases (synced via {@link WitchModAttachments#SPLITSCREEN_PHASE}): 0 off · 1 entering · 2 active · 3 exiting.
 */
public final class CurseSplitscreen extends Effect {
    /** victim UUID -> partner UUID (server-authoritative; the entity-id attachment is only for the client). */
    private static final Map<UUID, UUID> PAIR = new HashMap<>();
    /** player UUID -> whether they currently have a sign editor open (reported by the client). */
    private static final Map<UUID, Boolean> SIGN_EDITING = new HashMap<>();

    public CurseSplitscreen() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 35, () -> Items.OAK_SIGN);
    }

    @Override
    public Optional<TagKey<Item>> sacrificialTag() {
        return Optional.of(ItemTags.SIGNS); // any sign selects it (tag exception, like Hype Man / Party Time)
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        endSplit(target, resolve(target, PAIR.remove(target.getUUID())));
        SIGN_EDITING.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer victim, int ticksRemaining) {
        long now = victim.serverLevel().getGameTime();
        int phase = victim.getData(WitchModAttachments.SPLITSCREEN_PHASE);
        ServerPlayer partner = resolve(victim, PAIR.get(victim.getUUID()));
        double range = Config.SPLITSCREEN_RANGE.get();

        switch (phase) {
            case 0 -> {
                ServerPlayer found = nearestEligible(victim, range);
                if (found != null) {
                    beginTransition(victim, found, now, 1);
                }
            }
            case 1, 3 -> {
                if (now >= victim.getData(WitchModAttachments.SPLITSCREEN_LOAD_END)) {
                    if (phase == 1) {
                        setBoth(victim, partner, WitchModAttachments.SPLITSCREEN_PHASE, 2);
                    } else {
                        endSplit(victim, partner);
                        PAIR.remove(victim.getUUID());
                    }
                }
            }
            case 2 -> {
                // liveness is checked against the partner ENTITY (works for a debug villager too), NOT the
                // player-only PAIR map — resolving PAIR for a villager gave null and instantly exited the split.
                int partnerId = victim.getData(WitchModAttachments.SPLITSCREEN_PARTNER);
                Entity partnerEntity = partnerId >= 0 ? victim.serverLevel().getEntity(partnerId) : null;
                boolean gone = partnerEntity == null || !partnerEntity.isAlive() || partnerEntity.isRemoved()
                        || partnerEntity.level() != victim.level() || partnerEntity.distanceTo(victim) > range;
                if (gone) {
                    beginTransition(victim, partner, now, 3);
                    return;
                }
                if (partner != null) {
                    // A real player partner: keep their side synced (ids change on relog) + coordinate the sign lock.
                    victim.setData(WitchModAttachments.SPLITSCREEN_PARTNER, partner.getId());
                    partner.setData(WitchModAttachments.SPLITSCREEN_PARTNER, victim.getId());
                    int lock = editing(victim) || editing(partner) ? 1 : 0;
                    setBoth(victim, partner, WitchModAttachments.SPLITSCREEN_SIGN_LOCK, lock);
                }
            }
            default -> { }
        }
    }

    /** begins an entering(1)/exiting(3) fake-load transition on both players. */
    private static void beginTransition(ServerPlayer victim, @Nullable ServerPlayer partner, long now, int phase) {
        if (phase == 1) {
            if (partner == null) {
                return;
            }
            PAIR.put(victim.getUUID(), partner.getUUID());
            victim.setData(WitchModAttachments.SPLITSCREEN_PARTNER, partner.getId());
            partner.setData(WitchModAttachments.SPLITSCREEN_PARTNER, victim.getId());
        }
        int min = Config.SPLITSCREEN_LOAD_MIN_TICKS.get();
        int max = Math.max(min, Config.SPLITSCREEN_LOAD_MAX_TICKS.get());
        long end = now + min + victim.getRandom().nextInt(max - min + 1);
        setBoth(victim, partner, WitchModAttachments.SPLITSCREEN_PHASE, phase);
        setBothLong(victim, partner, WitchModAttachments.SPLITSCREEN_LOAD_END, end);
    }

    /** clears the split on both players (back to playing normally). */
    private static void endSplit(ServerPlayer victim, @Nullable ServerPlayer partner) {
        for (ServerPlayer p : new ServerPlayer[]{victim, partner}) {
            if (p == null) {
                continue;
            }
            p.setData(WitchModAttachments.SPLITSCREEN_PHASE, 0);
            p.setData(WitchModAttachments.SPLITSCREEN_PARTNER, -1);
            p.setData(WitchModAttachments.SPLITSCREEN_SIGN_LOCK, 0);
            p.setData(WitchModAttachments.SPLITSCREEN_LOAD_END, Long.MIN_VALUE);
        }
    }

    /** the nearest player who's free to be pulled in (not already split, same dimension, in range). */
    @Nullable
    private static ServerPlayer nearestEligible(ServerPlayer victim, double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : victim.serverLevel().getPlayers(p -> p != victim && p.isAlive() && !p.isSpectator()
                && p.getData(WitchModAttachments.SPLITSCREEN_PHASE) == 0)) {
            double d = p.distanceToSqr(victim);
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean editing(@Nullable ServerPlayer p) {
        return p != null && SIGN_EDITING.getOrDefault(p.getUUID(), false);
    }

    @Nullable
    private static ServerPlayer resolve(ServerPlayer any, @Nullable UUID id) {
        return id == null ? null : any.serverLevel().getServer().getPlayerList().getPlayer(id);
    }

    private static void setBoth(ServerPlayer a, @Nullable ServerPlayer b, java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> attr, int value) {
        a.setData(attr, value);
        if (b != null) {
            b.setData(attr, value);
        }
    }

    private static void setBothLong(ServerPlayer a, @Nullable ServerPlayer b, java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Long>> attr, long value) {
        a.setData(attr, value);
        if (b != null) {
            b.setData(attr, value);
        }
    }

    // --- Hooks (called from CurseEventHandler / the network layer) --------------------------------------

    /** the client reports its sign-editor open/closed here. */
    public static void reportSignEditing(ServerPlayer player, boolean editing) {
        SIGN_EDITING.put(player.getUUID(), editing);
    }

    /**
     * damage on either partner during an active, sign-locked split force-closes the sign for both — you're
     * never left frozen in a text box while something hurts you.
     */
    public static void onDamaged(ServerPlayer hurt) {
        if (hurt.getData(WitchModAttachments.SPLITSCREEN_PHASE) != 2
                || hurt.getData(WitchModAttachments.SPLITSCREEN_SIGN_LOCK) != 1) {
            return;
        }
        ServerPlayer partner = resolve(hurt, PAIR.get(hurt.getUUID()));
        if (partner == null) {
            // maybe the hurt one is the partner, not the victim — find whoever points at them
            for (Map.Entry<UUID, UUID> e : PAIR.entrySet()) {
                if (hurt.getUUID().equals(e.getValue())) {
                    partner = resolve(hurt, e.getKey());
                    break;
                }
            }
        }
        long nonce = hurt.serverLevel().getGameTime() | 1L;
        for (ServerPlayer p : new ServerPlayer[]{hurt, partner}) {
            if (p == null) {
                continue;
            }
            SIGN_EDITING.put(p.getUUID(), false);
            p.setData(WitchModAttachments.SPLITSCREEN_SIGN_CLOSE, nonce);
            p.setData(WitchModAttachments.SPLITSCREEN_SIGN_LOCK, 0);
        }
    }

    // --- Debug -----------------------------------------------------------------------------------------

    /**
     * debug: {@code arg} = "villager" pairs you with the nearest VILLAGER (so you can test the split's render
     * solo — you see the villager's POV; the villager obviously has no screen). "exit" ends it. No arg = pair
     * with the nearest player, or a villager if none.
     */
    @Override
    public String debugForce(ServerPlayer victim, @Nullable String arg) {
        long now = victim.serverLevel().getGameTime();
        if (arg != null && (arg.equalsIgnoreCase("exit") || arg.equalsIgnoreCase("off"))) {
            beginTransition(victim, resolve(victim, PAIR.get(victim.getUUID())), now, 3);
            return "exiting splitscreen";
        }
        boolean wantVillager = arg != null && arg.equalsIgnoreCase("villager");
        double range = Config.SPLITSCREEN_RANGE.get();
        if (!wantVillager) {
            ServerPlayer p = nearestEligible(victim, range);
            if (p != null) {
                beginTransition(victim, p, now, 1);
                return "entering splitscreen with " + p.getName().getString();
            }
        }
        // villager fallback / explicit: one-sided split (only the victim's client is set up).
        Villager v = nearestVillager(victim, range);
        if (v == null) {
            return wantVillager ? "no villager within " + (int) range + " blocks" : "no player OR villager in range to pair with";
        }
        PAIR.remove(victim.getUUID());
        victim.setData(WitchModAttachments.SPLITSCREEN_PARTNER, v.getId());
        int min = Config.SPLITSCREEN_LOAD_MIN_TICKS.get();
        int max = Math.max(min, Config.SPLITSCREEN_LOAD_MAX_TICKS.get());
        victim.setData(WitchModAttachments.SPLITSCREEN_LOAD_END, now + min + victim.getRandom().nextInt(max - min + 1));
        victim.setData(WitchModAttachments.SPLITSCREEN_PHASE, 1);
        return "entering splitscreen with a villager (debug — your side only; you'll see its POV)";
    }

    @Override
    public java.util.List<String> debugArgs() {
        return java.util.List.of("villager", "exit");
    }

    @Nullable
    private static Villager nearestVillager(ServerPlayer victim, double range) {
        List<Villager> vs = victim.serverLevel().getEntitiesOfClass(Villager.class,
                victim.getBoundingBox().inflate(range), Entity::isAlive);
        Villager best = null;
        double bd = Double.MAX_VALUE;
        for (Villager v : vs) {
            double d = v.distanceToSqr(victim);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }
}
