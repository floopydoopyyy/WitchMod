package com.oliver.witchmod.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.OrganisedStash;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.effects.Curses;

/**
 * all of the mod's custom packets and their handlers. c2s payloads are re-checked server-side (the sender
 * still carries the effect, near the position, etc.) since a client can send anything; s2c payloads drive
 * per-player client fx/ui. kept in one place so the wire protocol is easy to audit.
 */
public final class WitchModNetwork {
    private WitchModNetwork() {}

    /** c2s: open my organised stash. */
    public record OpenOrganisedPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenOrganisedPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "open_organised"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenOrganisedPayload> STREAM_CODEC =
                StreamCodec.unit(new OpenOrganisedPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: a bouncy rebound happened client-side; server plays the sound + particles so onlookers see it too. */
    public record BouncyBoingPayload(double x, double y, double z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BouncyBoingPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "bouncy_boing"));
        public static final StreamCodec<ByteBuf, BouncyBoingPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, BouncyBoingPayload::x,
                ByteBufCodecs.DOUBLE, BouncyBoingPayload::y,
                ByteBufCodecs.DOUBLE, BouncyBoingPayload::z,
                BouncyBoingPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** s2c: one fake-twitch chat line for the chat blessing's overlay (server-driven so it can react to real play). */
    public record ChatLinePayload(String username, String message, int color, int kind) implements CustomPacketPayload {
        /** kind: 0 normal, 1 sub, 2 donation, 3 raid, 4 hype-highlight — drives overlay styling. */
        public static final CustomPacketPayload.Type<ChatLinePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "chat_line"));
        public static final StreamCodec<ByteBuf, ChatLinePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ChatLinePayload::username,
                ByteBufCodecs.STRING_UTF8, ChatLinePayload::message,
                ByteBufCodecs.INT, ChatLinePayload::color,
                ByteBufCodecs.VAR_INT, ChatLinePayload::kind,
                ChatLinePayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** push a chat line to a player's overlay. */
    public static void sendChatLine(ServerPlayer player, String username, String message, int color, int kind) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ChatLinePayload(username, message, color, kind));
    }

    /** c2s: berserker swung at air (client is authoritative on a miss) — reset the frenzy. */
    public record BerserkerMissPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BerserkerMissPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "berserker_miss"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BerserkerMissPayload> STREAM_CODEC =
                StreamCodec.unit(new BerserkerMissPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: bedrock moment (hungry) — the client's forced eat finished, so consume one of the held stack. */
    public record BedrockHungryEatPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BedrockHungryEatPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "bedrock_hungry_eat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BedrockHungryEatPayload> STREAM_CODEC =
                StreamCodec.unit(new BedrockHungryEatPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: cast the ritual at the table at {@code pos} (direct, not via the flaky container-button plumbing). */
    public record RitualCastPayload(net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RitualCastPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "ritual_cast"));
        public static final StreamCodec<ByteBuf, RitualCastPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.BlockPos.STREAM_CODEC, RitualCastPayload::pos,
                RitualCastPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * s2c: ritual outcome fx, rendered client-side to keep heavy particles off the server. kind 0=success,
     * 1=fizzle, 2=backfire; flags bit0=blessing, bit1=purple; targetId -1 = self (no lash); delay = ticks
     * before the target-side lash.
     */
    public record RitualFxPayload(int kind, int flags, net.minecraft.core.BlockPos table,
                                  net.minecraft.world.item.ItemStack item, int targetId, int delay) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RitualFxPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "ritual_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RitualFxPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind());
                    buf.writeVarInt(p.flags());
                    buf.writeBlockPos(p.table());
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.item());
                    buf.writeVarInt(p.targetId());
                    buf.writeVarInt(p.delay());
                },
                buf -> new RitualFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(),
                        net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** send ritual fx to everyone tracking the table, plus the hit player (who sees the incoming lash). */
    public static void sendRitualFx(ServerLevel level, net.minecraft.core.BlockPos table, int kind, boolean blessing,
                                    boolean purple, net.minecraft.world.item.ItemStack item, @org.jetbrains.annotations.Nullable ServerPlayer target) {
        sendRitualFxToEntity(level, table, kind, blessing, purple, item, target);
    }

    /** as {@link #sendRitualFx} but the lash target may be any living entity (debug command). */
    public static void sendRitualFxToEntity(ServerLevel level, net.minecraft.core.BlockPos table, int kind, boolean blessing,
                                            boolean purple, net.minecraft.world.item.ItemStack item,
                                            @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity target) {
        int flags = (blessing ? 1 : 0) | (purple ? 2 : 0);
        int targetId = target == null ? -1 : target.getId();
        int delay = targetId >= 0 ? 20 + level.getRandom().nextInt(61) : 0; // 1–4s (0 = self)
        net.minecraft.world.item.ItemStack fxItem = item == null ? net.minecraft.world.item.ItemStack.EMPTY : item.copyWithCount(1);
        RitualFxPayload payload = new RitualFxPayload(kind, flags, table, fxItem, targetId, delay);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(table), payload);
        if (target instanceof ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, payload);
        }
    }

    /**
     * s2c: narrator — read {@code text} via tts and show it as a subtitle for {@code subtitleTicks}
     * (accessibility on platforms without tts). {@code callout} replaces the subtitle on non-windows only.
     */
    public record NarratorSpeakPayload(String text, String callout, int subtitleTicks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NarratorSpeakPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "narrator_speak"));
        public static final StreamCodec<ByteBuf, NarratorSpeakPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, NarratorSpeakPayload::text,
                ByteBufCodecs.STRING_UTF8, NarratorSpeakPayload::callout,
                ByteBufCodecs.VAR_INT, NarratorSpeakPayload::subtitleTicks,
                NarratorSpeakPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: narrator — a client-only event (pausing / tabbing out) the server can't see, reported so it can be narrated. */
    public record NarratorClientEventPayload(String category) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NarratorClientEventPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "narrator_client_event"));
        public static final StreamCodec<ByteBuf, NarratorClientEventPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, NarratorClientEventPayload::category,
                NarratorClientEventPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: narcolepsy — the client mashed awake, so end the current sleep early. */
    public record NarcolepsyWakePayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<NarcolepsyWakePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "narcolepsy_wake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NarcolepsyWakePayload> STREAM_CODEC =
                StreamCodec.unit(new NarcolepsyWakePayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: splitscreen — whether this client has a sign editor open (freezes the shared screen). */
    public record SplitscreenSignPayload(boolean editing) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SplitscreenSignPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "splitscreen_sign"));
        public static final StreamCodec<ByteBuf, SplitscreenSignPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SplitscreenSignPayload::editing,
                SplitscreenSignPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: flight — rise mode (0 none / 1 push-up / 2 sprint-glide) so the server drains energy right. */
    public record FlightRisePayload(int mode) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FlightRisePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "flight_rise"));
        public static final StreamCodec<ByteBuf, FlightRisePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FlightRisePayload::mode,
                FlightRisePayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** c2s: voodoo doll — the client is shaking (camera whipping) a held bound doll. */
    public record VoodooShakePayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VoodooShakePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "voodoo_shake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VoodooShakePayload> STREAM_CODEC =
                StreamCodec.unit(new VoodooShakePayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** one revealed attachment for the scrying mirror overlay. kind: 0 curse / 1 blessing. */
    public record ScryEntry(String name, int kind, int seconds, String detail) {
        public static final StreamCodec<ByteBuf, ScryEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScryEntry::name,
                ByteBufCodecs.VAR_INT, ScryEntry::kind,
                ByteBufCodecs.VAR_INT, ScryEntry::seconds,
                ByteBufCodecs.STRING_UTF8, ScryEntry::detail,
                ScryEntry::new);
    }

    /** s2c: scrying mirror result — show a styled overlay of {@code title}'s active attachments. */
    public record ScryPayload(String title, java.util.List<ScryEntry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ScryPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "scry"));
        public static final StreamCodec<ByteBuf, ScryPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScryPayload::title,
                ScryEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ScryPayload::entries,
                ScryPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** one ledger line: who → whom, the effect (+ modifier), and a pre-formatted result/age string. */
    public record LedgerEntry(String caster, String target, String effect, String modifier, String result, boolean scribbled) {
        public static final StreamCodec<ByteBuf, LedgerEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, LedgerEntry::caster,
                ByteBufCodecs.STRING_UTF8, LedgerEntry::target,
                ByteBufCodecs.STRING_UTF8, LedgerEntry::effect,
                ByteBufCodecs.STRING_UTF8, LedgerEntry::modifier,
                ByteBufCodecs.STRING_UTF8, LedgerEntry::result,
                ByteBufCodecs.BOOL, LedgerEntry::scribbled,
                LedgerEntry::new);
    }

    /** s2c: open a styled screen of nearby ritual activity (right-clicking a ledger). */
    public record LedgerPayload(int range, java.util.List<LedgerEntry> entries) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LedgerPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "ledger"));
        public static final StreamCodec<ByteBuf, LedgerPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LedgerPayload::range,
                LedgerEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), LedgerPayload::entries,
                LedgerPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ScryPayload.TYPE, ScryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.oliver.witchmod.client.ScryingOverlay.receive(payload.title(), payload.entries())));
        registrar.playToClient(LedgerPayload.TYPE, LedgerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.oliver.witchmod.client.LedgerScreen.open(payload.range(), payload.entries())));
        registrar.playToServer(VoodooShakePayload.TYPE, VoodooShakePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.oliver.witchmod.items.ItemVoodooDoll.onShake(player);
                    }
                }));
        registrar.playToServer(FlightRisePayload.TYPE, FlightRisePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, Blessings.FLIGHT)) {
                        com.oliver.witchmod.effects.blessings.BlessingFlight.setMode(player, payload.mode());
                    }
                }));
        registrar.playToServer(BerserkerMissPayload.TYPE, BerserkerMissPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, Blessings.BERSERKER)) {
                        com.oliver.witchmod.effects.blessings.BlessingBerserker.onMiss(player);
                    }
                }));
        registrar.playToServer(BedrockHungryEatPayload.TYPE, BedrockHungryEatPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, com.oliver.witchmod.effects.Curses.BEDROCK_MOMENT)) {
                        com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onHungryEat(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                }));
        registrar.playToClient(ChatLinePayload.TYPE, ChatLinePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.oliver.witchmod.client.ChatOverlayLayer.receive(payload.username(), payload.message(), payload.color(), payload.kind())));
        registrar.playToClient(RitualFxPayload.TYPE, RitualFxPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.oliver.witchmod.client.RitualFxClient.play(payload.kind(), payload.flags(), payload.table(),
                                payload.item(), payload.targetId(), payload.delay())));
        registrar.playToClient(NarratorSpeakPayload.TYPE, NarratorSpeakPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.oliver.witchmod.client.NarratorClient.speak(payload.text(), payload.callout(), payload.subtitleTicks())));
        registrar.playToServer(NarratorClientEventPayload.TYPE, NarratorClientEventPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // only the whitelisted client-only categories are accepted (a client can send anything)
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, Curses.NARRATOR)) {
                        String cat = payload.category();
                        if ("tabbed_out_long".equals(cat)) {
                            com.oliver.witchmod.effects.curses.CurseNarrator.constant(player, cat);
                        } else if ("paused".equals(cat) || "tabbed_out".equals(cat) || "returned".equals(cat)) {
                            com.oliver.witchmod.effects.curses.CurseNarrator.narrate(player, cat);
                        } else if ("whiff".equals(cat)) {
                            com.oliver.witchmod.effects.curses.CurseNarrator.maybe(player, cat);
                        }
                    }
                }));
        registrar.playToServer(OpenOrganisedPayload.TYPE, OpenOrganisedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, Blessings.ORGANISED)) {
                        OrganisedStash.openMenu(player);
                    }
                }));
        registrar.playToServer(BouncyBoingPayload.TYPE, BouncyBoingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, Curses.BOUNCY)
                            && player.distanceToSqr(payload.x, payload.y, payload.z) < 16.0) { // must be near the sender
                        ServerLevel level = player.serverLevel();
                        level.playSound(null, payload.x, payload.y, payload.z, WitchModSounds.BOUNCY_BOING.get(),
                                SoundSource.PLAYERS, 0.9F, 0.9F + level.random.nextFloat() * 0.3F);
                        level.sendParticles(ParticleTypes.ITEM_SLIME, payload.x, payload.y + 0.2, payload.z,
                                8, 0.3, 0.2, 0.3, 0.02);
                    }
                }));
        registrar.playToServer(SplitscreenSignPayload.TYPE, SplitscreenSignPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.oliver.witchmod.effects.curses.CurseSplitscreen.reportSignEditing(player, payload.editing());
                    }
                }));
        registrar.playToServer(NarcolepsyWakePayload.TYPE, NarcolepsyWakePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && EffectManager.isActive(player, Curses.NARCOLEPSY)) {
                        com.oliver.witchmod.effects.curses.CurseNarcolepsy.wakeEarly(player);
                    }
                }));
        registrar.playToServer(RitualCastPayload.TYPE, RitualCastPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.level().getBlockEntity(payload.pos())
                                    instanceof com.oliver.witchmod.blocks.BewitchingTableBlockEntity table
                            && player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) <= 64.0) {
                        try {
                            com.oliver.witchmod.blocks.BewitchingTableRitual.cast((ServerLevel) player.level(), payload.pos(), table, player);
                        } catch (Throwable t) {
                            WitchMod.LOGGER.error("[ritual] cast() threw", t);
                        }
                        // the ritual clears the slots — re-sync the open menu
                        player.containerMenu.broadcastChanges();
                    }
                }));
    }
}
