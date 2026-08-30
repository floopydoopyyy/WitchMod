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
 * The mod's tiny bit of custom networking. Menus can only be opened server-side, so the Organised keybind
 * (client) sends this empty C2S signal and the server opens the stash — but only if the player actually has
 * the blessing.
 */
public final class WitchModNetwork {
    private WitchModNetwork() {}

    /** Empty request: "open my Organised stash". */
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

    /**
     * A Bouncy rebound happened client-side (floor/wall/ceiling). The client only knows about its own
     * collisions, so it tells the server WHERE it boinged and the server plays the sound + slime particles for
     * everyone nearby — otherwise onlookers miss half the fun.
     */
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

    /**
     * A single fake-Twitch chat line pushed from the server to the Chat blessing's owner: chatter name, the
     * message, and a colour. The overlay is server-driven so the messages can react to real gameplay and carry
     * genuinely-useful server-side info (nearby structures, players...).
     */
    public record ChatLinePayload(String username, String message, int color, int kind) implements CustomPacketPayload {
        /** kind: 0 normal, 1 sub, 2 donation, 3 raid, 4 hype-highlight. Drives special overlay styling. */
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

    /** Server helper: push a chat line to a player's overlay. */
    public static void sendChatLine(ServerPlayer player, String username, String message, int color, int kind) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ChatLinePayload(username, message, color, kind));
    }

    /** The Berserker player swung at air (a miss): the client is authoritative on that, so it tells the server to reset the frenzy. */
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

    /** Bedrock Moment (Hungry): the client's forced eat finished — the server consumes one of the held stack. */
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

    /**
     * Cast the ritual at the Bewitching Table at {@code pos}. The Cast button sends this directly rather than
     * going through the vanilla container-button plumbing (which proved unreliable here), so the server runs
     * the ritual against the real block entity unconditionally.
     */
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

    /** Narcolepsy: the client mashed its way awake — ask the server to end the current sleep early. */
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

    /** Splitscreen: the client tells the server whether it currently has a sign editor open (shared-screen freeze). */
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

    /** Flight: the client reports its rise MODE (0 none / 1 push-up / 2 sprint-glide) so the server drains right. */
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

    /** Voodoo Doll: the client reports it's shaking (camera whipping around) a held bound doll. */
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

    /** One revealed attachment for the Scrying Mirror overlay. kind: 0 curse / 1 blessing. */
    public record ScryEntry(String name, int kind, int seconds, String detail) {
        public static final StreamCodec<ByteBuf, ScryEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScryEntry::name,
                ByteBufCodecs.VAR_INT, ScryEntry::kind,
                ByteBufCodecs.VAR_INT, ScryEntry::seconds,
                ByteBufCodecs.STRING_UTF8, ScryEntry::detail,
                ScryEntry::new);
    }

    /** Scrying Mirror result → the client shows a styled overlay of {@code title}'s active attachments. */
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

    /** One Ledger line: who → whom, the effect (+ modifier), and a pre-formatted result/age string. */
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

    /** Right-clicking a Ledger → the client opens a styled screen of nearby ritual activity. */
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
                            && player.distanceToSqr(payload.x, payload.y, payload.z) < 16.0) { // sanity: near the player
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
                        // The ritual clears the block-entity slots — re-sync the open menu so the client sees it.
                        player.containerMenu.broadcastChanges();
                    }
                }));
    }
}
