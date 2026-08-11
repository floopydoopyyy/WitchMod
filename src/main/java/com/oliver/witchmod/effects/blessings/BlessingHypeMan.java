package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.HypeManMessages;
import com.oliver.witchmod.data.Usernames;

/**
 * You've got a personal hype man — a crowd, really (master-spec Hype Man, sacrificial item ANY MUSIC DISC, a
 * Rule 9 tag exception). Your every move makes nearby players gush about you in chat, addressing you by name,
 * sometimes to an unhinged degree. It does NOTHING mechanical — it's purely for the comedy — hence it's cheap.
 *
 * <p>Praise is triggered by what you do: <b>combat</b>, <b>picking items up</b>, <b>looting a chest</b>, and
 * just <b>being around</b> (ambient). The action hooks live in {@code BlessingEventHandler}; the ambient one is
 * this class's tick. Every trigger routes through {@link #praise}, which shares one cooldown so a busy moment
 * can't spam chat, rolls {@code hypemanChance} so it stays a treat, and speaks the line in the mouth of a
 * random nearby player (or "a fan" if you're alone) so it reads as the crowd, not the game.
 *
 * <p>Lines come from the writable {@code data/witchmod/text/hypeman.json}, keyed by trigger, with {@code
 * {player}} filled in with your username.
 */
public final class BlessingHypeMan extends Effect {
    /** blessed player -> game tick of their last praise, so all triggers share one cooldown. */
    private static final Map<UUID, Long> LAST_PRAISE = new HashMap<>();

    /** The only trigger that fires with NOBODY around (using a made-up name); the rest need a real audience. */
    private static final String SIGHTING = "nearby";

    /** The vanilla music-disc tag — any disc selects this blessing at the Table. */
    private static final TagKey<Item> MUSIC_DISCS =
            TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("music_discs"));

    public BlessingHypeMan() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.MUSIC_DISC_CAT);
    }

    @Override
    public Optional<TagKey<Item>> sacrificialTag() {
        return Optional.of(MUSIC_DISCS);
    }

    /** You find out the first time the crowd actually gushes about you (Rule 2), not when it's cast. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        LAST_PRAISE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // Unprompted "just being here" praise, gated by the shared cooldown + chance inside praise().
        if (EffectUtil.every(ticksRemaining, Config.HYPEMAN_AMBIENT_INTERVAL.get())) {
            praise(target, "nearby");
        }
    }

    /**
     * Have the nearby crowd praise {@code blessed} for a {@code key} action, if the shared cooldown is up and
     * the chance roll passes. Safe to call from any trigger.
     */
    public static void praise(ServerPlayer blessed, String key) {
        if (!(blessed.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_PRAISE.get(blessed.getUUID());
        if (last != null && now - last < Config.HYPEMAN_COOLDOWN.get()) {
            return; // too soon since the last cheer
        }
        RandomSource random = blessed.getRandom();
        double radius = Config.HYPEMAN_RADIUS.get();
        List<ServerPlayer> nearby = level.getPlayers(p -> p != blessed
                && p.distanceToSqr(blessed) <= radius * radius);

        // Who does the praising? A real nearby player if there is one. If there isn't, ONLY the "sighting"
        // praise carries on — with a made-up name from usernames.json. The specific-action praises (combat,
        // pickup, loot, building) need a genuine audience and simply stay quiet when you're alone.
        String speaker;
        if (!nearby.isEmpty()) {
            speaker = nearby.get(random.nextInt(nearby.size())).getGameProfile().getName();
        } else if (key.equals(SIGHTING)) {
            speaker = Usernames.random(random);
        } else {
            return;
        }

        if (random.nextDouble() >= Config.HYPEMAN_CHANCE.get()) {
            return; // not this time (chance rolled AFTER the audience check so it doesn't burn on empty rooms)
        }
        String line = HypeManMessages.pick(key, random);
        if (line == null) {
            return;
        }
        LAST_PRAISE.put(blessed.getUUID(), now);

        Component message = Component.literal("<" + speaker + "> "
                + line.replace("{player}", blessed.getGameProfile().getName()));

        // Everyone in earshot hears it — including the blessed player, the subject of the adoration.
        level.getPlayers(p -> p.distanceToSqr(blessed) <= radius * radius)
                .forEach(p -> p.sendSystemMessage(message));
        Blessings.HYPE_MAN.get().markDiscoveredByVictim(blessed); // discovered on the first cheer, not on cast
    }
}
