package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.WitchMod;

/**
 * The Echo Shard modifier delays a cast's ONSET by a few minutes: instead of applying the effect immediately,
 * the ritual schedules it here and it lands later (with its full, normal onset). Kept as a simple server-time
 * queue — the target/caster are resolved by UUID at fire time, so it survives them wandering off (and just
 * skips if the target has logged out).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class DelayedCasts {
    private DelayedCasts() {}

    private record Pending(UUID target, ResourceLocation effectId, int durationTicks, UUID caster,
                           boolean bypassWard, int display, long fireAt) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void schedule(ServerPlayer target, Holder.Reference<Effect> effect, int durationTicks,
                                ServerPlayer caster, EffectManager.ApplyOptions opts, int delayTicks) {
        long now = target.getServer().overworld().getGameTime();
        PENDING.add(new Pending(target.getUUID(), effect.key().location(), durationTicks,
                caster == null ? null : caster.getUUID(), opts.bypassWard(), opts.display(), now + delayTicks));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (now < p.fireAt()) {
                continue;
            }
            it.remove();
            ServerPlayer target = server.getPlayerList().getPlayer(p.target());
            if (target == null) {
                continue; // target logged off — the delayed cast simply lapses
            }
            ServerPlayer caster = p.caster() == null ? null : server.getPlayerList().getPlayer(p.caster());
            EffectManager.holderOf(p.effectId()).ifPresent(holder ->
                    EffectManager.apply(target, holder, p.durationTicks(), caster,
                            new EffectManager.ApplyOptions(p.bypassWard(), p.display())));
        }
    }
}
