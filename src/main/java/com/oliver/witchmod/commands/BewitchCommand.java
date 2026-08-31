package com.oliver.witchmod.commands;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.ActiveEffectInstance;
import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.LedgerLog;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.OrganisedStash;
import com.oliver.witchmod.data.TaxBank;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.curses.CurseAudit;
import com.oliver.witchmod.items.JarContents;

/**
 * The {@code /bewitch} operator command tree — the main hands-on entry point into the mod alongside the
 * Bewitching Table. Grouped by what each action touches, so related commands sit together:
 *
 * <pre>
 *   /bewitch apply     &lt;effect&gt; [targets] [duration]   cast as yourself (you are the caster)
 *   /bewitch dummy     &lt;effect&gt; [targets] [duration]   cast anonymously (caster "dummy" — Wards/Totems block it)
 *   /bewitch remove    &lt;effect&gt; [targets]              strip one effect
 *   /bewitch clear     [targets] [all|curses|blessings] strip all effects, or one category
 *
 *   /bewitch discovery add|remove  effect &lt;id&gt; | modifier &lt;id&gt; | all   [targets]
 *
 *   /bewitch give jar make &lt;e1&gt; [e2] [e3]              a filled jar from effect ids
 *   /bewitch give jar copy &lt;player&gt;                    a jar snapshotting a player's active effects
 *   /bewitch give essence  &lt;player&gt; | uuid &lt;uuid&gt;      a Player Essence bound to a target (uuid = offline)
 *   /bewitch give voodoo   &lt;player&gt; | uuid &lt;uuid&gt;      a Voodoo Doll bound to a target
 *
 *   /bewitch debug force   &lt;effect&gt; [targets] [arg]     force an effect's signature event / sub-event
 *   /bewitch debug voodoo  &lt;interaction&gt; &lt;player&gt;       force a voodoo interaction on a player
 *
 *   /bewitch organised                                  open your Organised-blessing stash
 * </pre>
 *
 * Every effect action defaults its target to the command source when {@code [targets]} is omitted.
 */
public final class BewitchCommand {
    /** Curses/blessings normally run 30-60 minutes; the default duration when none is given. */
    private static final int DEFAULT_EFFECT_DURATION_TICKS = 45 * 60 * 20;

    private BewitchCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("bewitch")
                .requires(source -> source.hasPermission(2))
                // --- effect management (the hot path — kept top-level and short) ---
                .then(applyNode(context))
                .then(dummyNode(context))
                .then(removeNode(context))
                .then(clearNode(context))
                // --- grouped families ---
                .then(discoveryNode(context))
                .then(giveNode(context))
                .then(debugNode(context))
                // --- standalone ---
                .then(Commands.literal("organised").executes(BewitchCommand::openOrganised)));
    }

    // =====================================================================================================
    // Effect management: apply / dummy / remove / clear
    // =====================================================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> applyNode(CommandBuildContext context) {
        // /bewitch apply <effect> [targets] [duration] — cast attributed to you (self if no targets).
        return Commands.literal("apply")
                .then(Commands.argument("effect", effectArg(context))
                        .executes(ctx -> applyEffect(ctx, self(ctx), DEFAULT_EFFECT_DURATION_TICKS, false))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> applyEffect(ctx, targets(ctx), DEFAULT_EFFECT_DURATION_TICKS, false))
                                .then(Commands.argument("duration", TimeArgument.time(1))
                                        .executes(ctx -> applyEffect(ctx, targets(ctx), duration(ctx), false)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dummyNode(CommandBuildContext context) {
        // /bewitch dummy <effect> [targets] [duration] — cast with no real caster. Attributed to "dummy" in
        // the Ledger, and (unlike a self-cast) a Ward/Warding Totem WILL block it, so it's the way to test them.
        return Commands.literal("dummy")
                .then(Commands.argument("effect", effectArg(context))
                        .executes(ctx -> applyEffect(ctx, self(ctx), DEFAULT_EFFECT_DURATION_TICKS, true))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> applyEffect(ctx, targets(ctx), DEFAULT_EFFECT_DURATION_TICKS, true))
                                .then(Commands.argument("duration", TimeArgument.time(1))
                                        .executes(ctx -> applyEffect(ctx, targets(ctx), duration(ctx), true)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeNode(CommandBuildContext context) {
        // /bewitch remove <effect> [targets] — same arg order as apply (effect first, self if no targets).
        return Commands.literal("remove")
                .then(Commands.argument("effect", effectArg(context))
                        .executes(ctx -> removeEffect(ctx, self(ctx)))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> removeEffect(ctx, targets(ctx)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearNode(CommandBuildContext context) {
        // /bewitch clear [targets] [all|curses|blessings] — strip everything, or just one category.
        return Commands.literal("clear")
                .executes(ctx -> clearEffects(ctx, self(ctx), null))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> clearEffects(ctx, targets(ctx), null))
                        .then(Commands.literal("all")
                                .executes(ctx -> clearEffects(ctx, targets(ctx), null)))
                        .then(Commands.literal("curses")
                                .executes(ctx -> clearEffects(ctx, targets(ctx), EffectCategory.CURSE)))
                        .then(Commands.literal("blessings")
                                .executes(ctx -> clearEffects(ctx, targets(ctx), EffectCategory.BLESSING))));
    }

    /** Shared apply handler. {@code dummy} = cast with no caster (attributed "dummy"; blocked by Wards/Totems). */
    private static int applyEffect(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                   int durationTicks, boolean dummy) throws CommandSyntaxException {
        Holder.Reference<Effect> effect = effect(ctx);
        ServerPlayer caster = dummy ? null : ctx.getSource().getPlayer();
        String casterName = dummy ? "dummy" : (caster != null ? caster.getName().getString() : "command");
        long gameTime = ctx.getSource().getLevel().getGameTime();

        // Audit is refused while the tax bank is at its memory ceiling — never cast it into a state where the
        // Tax Man would have to void what he takes (TaxBank never discards items to make room).
        if (effect.value() instanceof CurseAudit) {
            TaxBank bank = TaxBank.get(ctx.getSource().getServer());
            if (bank.isFull()) {
                ctx.getSource().sendFailure(Component.literal("The tax bank is full ("
                        + bank.contents().size() + " stacks) — pay it out with the Tax Man blessing first."));
                return 0;
            }
            if (bank.shouldWarn()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Heads up: the tax bank is "
                        + Math.round(bank.fullness() * 100) + "% full."), false);
            }
        }

        int landed = 0;
        for (ServerPlayer target : targets) {
            boolean ok = EffectManager.apply(target, effect, durationTicks, caster);
            String result = ok ? (dummy ? "success" : "command")
                    : (EffectManager.wouldBlock(target, caster) ? "blocked" : "refused");
            LedgerLog.log(Optional.of(casterName), target.getName().getString(), effect.key().location(),
                    result, gameTime, false,
                    net.minecraft.core.GlobalPos.of(target.level().dimension(), target.blockPosition()), null);
            if (ok) {
                landed++;
            }
        }

        // Voodoo Doll hint: a self-cast isn't forwarded (redirect is Table-only), but a caster holding a bound
        // doll is reminded that a natural Table cast would have redirected this curse.
        if (caster != null && effect.value().category() == EffectCategory.CURSE) {
            net.minecraft.world.item.ItemStack doll = com.oliver.witchmod.items.ItemVoodooDoll.findBoundDoll(caster);
            if (!doll.isEmpty()) {
                com.oliver.witchmod.data.PlayerEssenceData db = doll.get(com.oliver.witchmod.data.WitchModDataComponents.BOUND_PLAYER);
                String who = db == null ? "its target" : db.playerName();
                caster.sendSystemMessage(Component.literal(
                        "(Your bound voodoo doll would redirect this curse to " + who
                        + " if cast at the Ritual Table.)").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }

        int applied = landed;
        String verb = dummy ? "'dummy' cast" : "Cast";
        ctx.getSource().sendSuccess(() -> Component.literal(verb + " " + effect.key().location().getPath()
                + " on " + targets.size() + " player(s) — " + applied + " landed."), true);
        return applied;
    }

    private static int removeEffect(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets)
            throws CommandSyntaxException {
        Holder.Reference<Effect> effect = effect(ctx);
        int removed = 0;
        for (ServerPlayer target : targets) {
            if (EffectManager.remove(target, effect)) {
                removed++;
            }
        }
        int finalRemoved = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + effect.key().location().getPath()
                + " from " + finalRemoved + " player(s)."), true);
        return removed;
    }

    private static int clearEffects(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                    @Nullable EffectCategory category) {
        int total = 0;
        for (ServerPlayer target : targets) {
            total += EffectManager.removeAll(target, category);
        }
        int finalTotal = total;
        String what = category == null ? "attachment(s)"
                : (category == EffectCategory.CURSE ? "curse(s)" : "blessing(s)");
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + finalTotal + " " + what + "."), true);
        return total;
    }

    // =====================================================================================================
    // Discovery: /bewitch discovery add|remove  effect <id> | modifier <id> | all  [targets]
    // =====================================================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> discoveryNode(CommandBuildContext context) {
        return Commands.literal("discovery")
                .then(discoverySide(context, "add", true))
                .then(discoverySide(context, "remove", false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> discoverySide(CommandBuildContext context, String name, boolean add) {
        return Commands.literal(name)
                .then(Commands.literal("effect")
                        .then(Commands.argument("effect", effectArg(context))
                                .executes(ctx -> discoveryEffect(ctx, self(ctx), add))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> discoveryEffect(ctx, targets(ctx), add)))))
                .then(Commands.literal("modifier")
                        .then(Commands.argument("modifier", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(Modifier.values()).map(Modifier::id), b))
                                .executes(ctx -> discoveryModifier(ctx, self(ctx), add))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> discoveryModifier(ctx, targets(ctx), add)))))
                .then(Commands.literal("all")
                        .executes(ctx -> discoveryAll(ctx, self(ctx), add))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> discoveryAll(ctx, targets(ctx), add))));
    }

    private static int discoveryEffect(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, boolean add) throws CommandSyntaxException {
        Holder.Reference<Effect> effect = effect(ctx);
        for (ServerPlayer target : targets) {
            DiscoveryManager.setEffectDiscovered(target, effect.key().location(), add);
        }
        ctx.getSource().sendSuccess(() -> Component.literal((add ? "Discovered " : "Un-discovered ")
                + effect.key().location().getPath() + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int discoveryModifier(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, boolean add) {
        String id = StringArgumentType.getString(ctx, "modifier");
        Modifier found = null;
        for (Modifier m : Modifier.values()) {
            if (m.id().equals(id)) {
                found = m;
                break;
            }
        }
        if (found == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown modifier: " + id));
            return 0;
        }
        Modifier modifier = found;
        for (ServerPlayer target : targets) {
            DiscoveryManager.setModifierDiscovered(target, modifier, add);
        }
        ctx.getSource().sendSuccess(() -> Component.literal((add ? "Discovered " : "Un-discovered ")
                + "modifier " + id + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int discoveryAll(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, boolean add) {
        var effects = WitchModRegistries.EFFECT_REGISTRY.holders().toList();
        for (ServerPlayer target : targets) {
            for (var holder : effects) {
                DiscoveryManager.setEffectDiscovered(target, holder.key().location(), add);
            }
            for (Modifier m : Modifier.values()) {
                DiscoveryManager.setModifierDiscovered(target, m, add);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal((add ? "Discovered ALL " : "Cleared ALL ")
                + "attachments + modifiers for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    // =====================================================================================================
    // Give: /bewitch give jar|essence|voodoo ...
    // =====================================================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> giveNode(CommandBuildContext context) {
        return Commands.literal("give")
                .then(giveJarNode(context))
                .then(Commands.literal("essence")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    return giveEssence(ctx, p.getUUID(), p.getName().getString());
                                }))
                        .then(Commands.literal("uuid")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .executes(ctx -> {
                                            java.util.UUID uuid = UuidArgument.getUuid(ctx, "uuid");
                                            return giveEssence(ctx, uuid, resolveName(ctx.getSource().getServer(), uuid));
                                        }))))
                .then(Commands.literal("voodoo")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    return giveDoll(ctx, p.getUUID(), p.getName().getString());
                                }))
                        .then(Commands.literal("uuid")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .executes(ctx -> {
                                            java.util.UUID uuid = UuidArgument.getUuid(ctx, "uuid");
                                            return giveDoll(ctx, uuid, resolveName(ctx.getSource().getServer(), uuid));
                                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> giveJarNode(CommandBuildContext context) {
        var effectArg = effectArg(context);
        return Commands.literal("jar")
                .then(Commands.literal("make")
                        .then(Commands.argument("effect1", effectArg)
                                .executes(ctx -> makeJar(ctx, "effect1", null, null))
                                .then(Commands.argument("effect2", effectArg)
                                        .executes(ctx -> makeJar(ctx, "effect1", "effect2", null))
                                        .then(Commands.argument("effect3", effectArg)
                                                .executes(ctx -> makeJar(ctx, "effect1", "effect2", "effect3"))))))
                .then(Commands.literal("copy")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> copyJar(ctx, EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int giveEssence(CommandContext<CommandSourceStack> ctx, java.util.UUID id, String name) throws CommandSyntaxException {
        net.minecraft.world.item.ItemStack essence =
                new net.minecraft.world.item.ItemStack(com.oliver.witchmod.items.WitchModItems.PLAYER_ESSENCE.get());
        essence.set(com.oliver.witchmod.data.WitchModDataComponents.BOUND_PLAYER,
                new com.oliver.witchmod.data.PlayerEssenceData(id, name));
        giveOrDrop(ctx, essence);
        ctx.getSource().sendSuccess(() -> Component.literal("Made a Player Essence bound to " + name + "."), false);
        return 1;
    }

    private static int giveDoll(CommandContext<CommandSourceStack> ctx, java.util.UUID id, String name) throws CommandSyntaxException {
        net.minecraft.world.item.ItemStack doll =
                new net.minecraft.world.item.ItemStack(com.oliver.witchmod.items.WitchModItems.VOODOO_DOLL.get());
        com.oliver.witchmod.items.ItemVoodooDoll.bind(doll, new com.oliver.witchmod.data.PlayerEssenceData(id, name));
        giveOrDrop(ctx, doll);
        ctx.getSource().sendSuccess(() -> Component.literal("Made a Voodoo Doll bound to " + name + "."), false);
        return 1;
    }

    private static int makeJar(CommandContext<CommandSourceStack> ctx, String key1, @Nullable String key2, @Nullable String key3)
            throws CommandSyntaxException {
        java.util.List<CapturedEffect> effects = new java.util.ArrayList<>();
        effects.add(capturedFromArg(ctx, key1));
        if (key2 != null) {
            effects.add(capturedFromArg(ctx, key2));
        }
        if (key3 != null) {
            effects.add(capturedFromArg(ctx, key3));
        }
        return giveJar(ctx, effects);
    }

    private static CapturedEffect capturedFromArg(CommandContext<CommandSourceStack> ctx, String key) throws CommandSyntaxException {
        Holder.Reference<Effect> effect = ResourceArgument.getResource(ctx, key, WitchModRegistries.EFFECT_REGISTRY_KEY);
        return new CapturedEffect(effect.key().location(), DEFAULT_EFFECT_DURATION_TICKS);
    }

    private static int copyJar(CommandContext<CommandSourceStack> ctx, ServerPlayer source) throws CommandSyntaxException {
        java.util.List<CapturedEffect> effects = snapshot(source);
        if (effects.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(source.getName().getString() + " has no curses or blessings to bottle."));
            return 0;
        }
        return giveJar(ctx, effects);
    }

    /** The player's currently-active curses/blessings, up to {@link JarContents#MAX}, with their remaining times. */
    private static java.util.List<CapturedEffect> snapshot(ServerPlayer player) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        java.util.List<CapturedEffect> effects = new java.util.ArrayList<>();
        if (active != null) {
            for (net.minecraft.resources.ResourceLocation id : active.activeIds()) {
                if (effects.size() >= JarContents.MAX) {
                    break;
                }
                Optional<ActiveEffectInstance> instance = active.get(id);
                instance.ifPresent(inst -> effects.add(new CapturedEffect(id, Math.max(1, inst.remainingTicks()))));
            }
        }
        return effects;
    }

    private static int giveJar(CommandContext<CommandSourceStack> ctx, java.util.List<CapturedEffect> effects) throws CommandSyntaxException {
        giveOrDrop(ctx, JarContents.stackFor(effects, 1));
        ctx.getSource().sendSuccess(() -> Component.literal("Made a jar holding " + effects.size() + " attachment(s)."), false);
        return effects.size();
    }

    /** Best-effort display name for a UUID: online player -> profile cache -> the UUID string. */
    private static String resolveName(MinecraftServer server, java.util.UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString();
        }
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isBlank()) {
                return profile.get().getName();
            }
        }
        return uuid.toString();
    }

    // =====================================================================================================
    // Debug: /bewitch debug force <effect> [targets] [arg]  |  /bewitch debug voodoo <interaction> <player>
    // =====================================================================================================

    private static final String[] VOODOO_INTERACTIONS =
            {"stab", "throw", "squeeze", "feed", "ignite", "freeze", "wet", "lightning",
             "shake", "potion", "arrow", "fishing"};

    private static LiteralArgumentBuilder<CommandSourceStack> debugNode(CommandBuildContext context) {
        return Commands.literal("debug")
                .then(Commands.literal("force")
                        .then(Commands.argument("effect", effectArg(context))
                                .executes(ctx -> debugForce(ctx, self(ctx), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> debugForce(ctx, targets(ctx), null))
                                        .then(Commands.argument("arg", StringArgumentType.greedyString())
                                                .suggests(BewitchCommand::suggestDebugArgs)
                                                .executes(ctx -> debugForce(ctx, targets(ctx),
                                                        StringArgumentType.getString(ctx, "arg")))))))
                .then(Commands.literal("voodoo")
                        .then(Commands.argument("interaction", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(VOODOO_INTERACTIONS, b))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> forceVoodoo(ctx,
                                                StringArgumentType.getString(ctx, "interaction"),
                                                EntityArgument.getPlayer(ctx, "player"))))));
    }

    /** Suggests the selected effect's valid debug args (e.g. the Cutaway gag names) as you type. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDebugArgs(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        try {
            Holder.Reference<Effect> effect = effect(ctx);
            java.util.List<String> args = effect.value().debugArgs();
            if (!args.isEmpty()) {
                // Suggest against the LAST whitespace-separated token so combos like "villager <gag>" complete.
                String remaining = builder.getRemaining();
                int lastSpace = remaining.lastIndexOf(' ');
                var b = lastSpace >= 0 ? builder.createOffset(builder.getStart() + lastSpace + 1) : builder;
                return SharedSuggestionProvider.suggest(args, b);
            }
        } catch (Exception ignored) {
            // effect not resolvable yet — no suggestions
        }
        return builder.buildFuture();
    }

    private static int debugForce(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, @Nullable String arg) throws CommandSyntaxException {
        Holder.Reference<Effect> effect = effect(ctx);
        String id = effect.key().location().toString();
        int ok = 0;
        for (ServerPlayer target : targets) {
            String feedback;
            try {
                feedback = effect.value().debugForce(target, arg == null || arg.isBlank() ? null : arg.trim());
            } catch (Exception e) {
                ctx.getSource().sendFailure(Component.literal("[debug] " + id + " on " + target.getName().getString() + " threw: " + e));
                continue;
            }
            if (feedback == null) {
                ctx.getSource().sendFailure(Component.literal(effect.key().location().getPath() + " has no forcible debug event."));
                return ok;
            }
            String line = "[debug] " + id + " -> " + target.getName().getString() + ": " + feedback;
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            ok++;
        }
        return ok;
    }

    /** Force a voodoo interaction directly onto a target, as if a doll bound to them had been used. */
    private static int forceVoodoo(CommandContext<CommandSourceStack> ctx, String interaction, ServerPlayer target) {
        ServerPlayer caster = ctx.getSource().getPlayer();
        ServerLevel level = target.serverLevel();
        switch (interaction.toLowerCase(java.util.Locale.ROOT)) {
            case "stab" -> com.oliver.witchmod.items.ItemVoodooDoll.voodooHurt(target,
                    caster != null ? caster : target, (float) (double) Config.VOODOO_NEEDLE_BASE_DAMAGE.get());
            case "squeeze" -> {
                com.oliver.witchmod.items.ItemVoodooDoll.voodooHurt(target, caster != null ? caster : target, 3.0F);
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false, true));
            }
            case "throw" -> {
                net.minecraft.world.phys.Vec3 dir = caster != null ? caster.getLookAngle() : target.getLookAngle();
                dir = new net.minecraft.world.phys.Vec3(dir.x, 0, dir.z).normalize();
                target.push(dir.x * Config.VOODOO_THROW_FORCE.get(), 0.42, dir.z * Config.VOODOO_THROW_FORCE.get());
                target.hurtMarked = true;
            }
            case "feed" -> target.getFoodData().eat(6, 0.6F);
            case "ignite" -> target.setRemainingFireTicks(Config.VOODOO_LAVA_FIRE_TICKS.get());
            case "freeze" -> target.setTicksFrozen(target.getTicksRequiredToFreeze() + 60);
            case "wet" -> {
                target.clearFire();
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                        target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.35, 0.5, 0.35, 0.0);
            }
            case "lightning" -> {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.getX(), target.getY(), target.getZ());
                    level.addFreshEntity(bolt);
                }
            }
            case "shake" -> target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.CONFUSION, 60, 0, false, false, false));
            case "potion" -> {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100, 0));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 0));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.EFFECT,
                        target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
            }
            case "arrow" -> {
                target.hurt(level.damageSources().generic(), 3.0F);
                target.setArrowCount(target.getArrowCount() + 1);
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.ARROW_HIT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.1F);
            }
            case "fishing" -> com.oliver.witchmod.items.ItemVoodooDoll.fling(target,
                    caster != null ? caster.getLookAngle() : target.getLookAngle(),
                    Config.VOODOO_FISHING_FORCE.get(), 0.55);
            default -> {
                ctx.getSource().sendFailure(Component.literal("Unknown interaction '" + interaction
                        + "'. Try: " + String.join(", ", VOODOO_INTERACTIONS)));
                return 0;
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Forced voodoo '" + interaction + "' on "
                + target.getName().getString() + "."), false);
        return 1;
    }

    // =====================================================================================================
    // Organised stash
    // =====================================================================================================

    /** Opens the caller's Organised-blessing stash as a plain 1-row chest. */
    private static int openOrganised(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        OrganisedStash.openMenu(ctx.getSource().getPlayerOrException());
        return 1;
    }

    // =====================================================================================================
    // Small shared helpers — keep the node builders terse and consistent
    // =====================================================================================================

    private static com.mojang.brigadier.arguments.ArgumentType<Holder.Reference<Effect>> effectArg(CommandBuildContext context) {
        return ResourceArgument.resource(context, WitchModRegistries.EFFECT_REGISTRY_KEY);
    }

    private static Holder.Reference<Effect> effect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "effect", WitchModRegistries.EFFECT_REGISTRY_KEY);
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Collections.singletonList(ctx.getSource().getPlayerOrException());
    }

    private static int duration(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("duration", Integer.class);
    }

    private static void giveOrDrop(CommandContext<CommandSourceStack> ctx, net.minecraft.world.item.ItemStack stack) throws CommandSyntaxException {
        ServerPlayer receiver = ctx.getSource().getPlayerOrException();
        if (!receiver.getInventory().add(stack)) {
            receiver.drop(stack, false);
        }
    }
}
