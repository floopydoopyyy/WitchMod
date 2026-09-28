package com.oliver.witchmod.effects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.CompanionshipLines;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.effects.blessings.BlessingBodyguard;
import com.oliver.witchmod.effects.curses.CurseSolicitor;
import com.oliver.witchmod.entities.BodyguardEntity;

/**
 * idle banter between a player's bodyguard, solicitor and guardian angel — the "companionship" jar's flavour.
 * lines are data ({@code data/witchmod/text/companionship.json}); a category only fires while its speakers are
 * actually present, so it stops the instant one is removed. an "argue" category speaks an opener now and the
 * other party's reply a couple of seconds later.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class CompanionshipBanter {
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** a scheduled reply in an argue exchange (true = the bodyguard speaks it, false = the solicitor). */
    private record Pending(long fireAt, boolean bodyguardSpeaks, String line) {}

    private CompanionshipBanter() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer anchor)) {
            return;
        }
        long now = anchor.level().getGameTime();

        // deliver a due argue reply first.
        Pending pending = PENDING.get(anchor.getUUID());
        if (pending != null && now >= pending.fireAt()) {
            PENDING.remove(anchor.getUUID());
            if (pending.bodyguardSpeaks()) {
                BodyguardEntity bg = BlessingBodyguard.get(anchor);
                if (bg != null) {
                    bg.speakBanter(pending.line());
                }
            } else {
                CurseSolicitor.speakBanter(anchor, pending.line());
            }
        }

        boolean bgActive = EffectManager.isActive(anchor, Blessings.BODYGUARD);
        boolean solActive = EffectManager.isActive(anchor, Curses.SOLICITOR);
        if (!bgActive && !solActive) {
            NEXT.remove(anchor.getUUID());
            return;
        }
        long next = NEXT.computeIfAbsent(anchor.getUUID(), k -> now + gap(anchor.getRandom()));
        if (now < next) {
            return;
        }
        NEXT.put(anchor.getUUID(), now + gap(anchor.getRandom()));
        banter(anchor);
    }

    /**
     * a companion died — the surviving one occasionally remarks on it. {@code deadKind} is
     * {@code solicitor}/{@code bodyguard}/{@code guardian}; only fires at {@code companionshipDeathReactChancePercent}
     * so it stays a rare touch, and only if a suitable reactor is actually present.
     */
    public static void reactToDeath(ServerPlayer anchor, String deadKind) {
        if (anchor.getRandom().nextInt(100) >= Config.COMPANIONSHIP_DEATH_REACT_CHANCE_PERCENT.get()) {
            return;
        }
        BodyguardEntity bodyguard = BlessingBodyguard.get(anchor);
        WanderingTrader trader = CurseSolicitor.getTrader(anchor);
        boolean bodyguardSpeaks;
        String category;
        if ("solicitor".equals(deadKind) && bodyguard != null) {
            category = "solicitor_died";
            bodyguardSpeaks = true;
        } else if ("bodyguard".equals(deadKind) && trader != null) {
            category = "bodyguard_died";
            bodyguardSpeaks = false;
        } else if ("guardian".equals(deadKind) && bodyguard != null && (trader == null || anchor.getRandom().nextBoolean())) {
            category = "guardian_died_bodyguard";
            bodyguardSpeaks = true;
        } else if ("guardian".equals(deadKind) && trader != null) {
            category = "guardian_died_solicitor";
            bodyguardSpeaks = false;
        } else {
            return; // no suitable survivor present to react
        }
        List<String> lines = CompanionshipLines.pick(category, anchor.getRandom());
        if (lines.isEmpty()) {
            return;
        }
        String traderName = trader != null ? trader.getName().getString() : "the salesman";
        speak(anchor, bodyguard, bodyguardSpeaks, fill(lines.get(0), anchor, traderName));
    }

    private static void banter(ServerPlayer anchor) {
        BodyguardEntity bodyguard = BlessingBodyguard.get(anchor);
        WanderingTrader trader = CurseSolicitor.getTrader(anchor);
        boolean bg = bodyguard != null;
        boolean sol = trader != null;
        boolean guardian = EffectManager.isActive(anchor, Blessings.GUARDIAN_ANGEL);

        List<String> options = new ArrayList<>();
        if (bg && sol) {
            options.add("stray_bodyguard");
            options.add("stray_solicitor");
            options.add("argue_bodyguard");
            options.add("argue_solicitor");
        }
        if (sol && guardian) {
            options.add("stray_solicitor_guardian");
        }
        if (bg && guardian) {
            options.add("stray_bodyguard_guardian");
        }
        if (bg && sol && guardian) {
            options.add("full_house_bodyguard");
            options.add("full_house_solicitor");
        }
        options.removeIf(category -> !CompanionshipLines.has(category)); // only categories with lines
        if (options.isEmpty()) {
            return;
        }

        RandomSource rng = anchor.getRandom();
        String category = options.get(rng.nextInt(options.size()));
        List<String> lines = CompanionshipLines.pick(category, rng);
        if (lines.isEmpty()) {
            return;
        }

        String traderName = trader != null ? trader.getName().getString() : "the salesman";
        boolean openerIsBodyguard = switch (category) {
            case "stray_solicitor", "argue_solicitor", "stray_solicitor_guardian", "full_house_solicitor" -> false;
            default -> true;
        };
        speak(anchor, bodyguard, openerIsBodyguard, fill(lines.get(0), anchor, traderName));

        // an argue exchange has a second line, spoken back by the OTHER party after a short delay.
        boolean isArgue = category.equals("argue_bodyguard") || category.equals("argue_solicitor");
        if (isArgue && lines.size() >= 2) {
            long fireAt = anchor.level().getGameTime() + Config.COMPANIONSHIP_ARGUE_DELAY_TICKS.get();
            PENDING.put(anchor.getUUID(), new Pending(fireAt, !openerIsBodyguard, fill(lines.get(1), anchor, traderName)));
        }
    }

    private static void speak(ServerPlayer anchor, BodyguardEntity bodyguard, boolean bodyguardSpeaks, String line) {
        if (bodyguardSpeaks) {
            if (bodyguard != null) {
                bodyguard.speakBanter(line);
            }
        } else {
            CurseSolicitor.speakBanter(anchor, line);
        }
    }

    private static String fill(String line, ServerPlayer anchor, String traderName) {
        return line.replace("{player}", anchor.getGameProfile().getName())
                .replace("{solicitor}", traderName)
                .replace("{bodyguard}", "Bodyguard")
                .replace("{angel}", "Guardian Angel");
    }

    private static long gap(RandomSource rng) {
        int min = Config.COMPANIONSHIP_BANTER_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.COMPANIONSHIP_BANTER_MAX_TICKS.get());
        return min + rng.nextInt(max - min);
    }
}
