package com.oliver.witchmod.blocks;

import net.minecraft.ChatFormatting;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.ActiveEffectInstance;
import com.oliver.witchmod.data.CoinGamble;
import com.oliver.witchmod.data.DelayedCasts;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.LedgerLog;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.ModifierCalculator;
import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.SacrificialItems;
import com.oliver.witchmod.data.TaxBank;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.effects.curses.CurseAudit;
import com.oliver.witchmod.items.ItemJar;
import com.oliver.witchmod.items.JarContents;
import com.oliver.witchmod.items.WitchModItems;
import com.oliver.witchmod.network.WitchModNetwork;

/**
 * consumes the table's 4 slots and runs the success/backfire roll: applies the rolled effect, or fizzles, or
 * (rare, tier-scaled) backfires. {@link #doBackfire} picks one of six outcomes (explode / mirror onto caster
 * / woolliam sheep / random low-biased curse / inventory shuffle / backfire damage), several scaling with the
 * attachment's baseCost-derived strength.
 */
public final class BewitchingTableRitual {
    /** amethyst Shard: cost cut when the target already carries a same-category effect. */
    private static final float AMETHYST_DISCOUNT = 0.30F;
    /** netherite Ingot: chance a cast bypasses the Ward (Warding Totem still applies). */
    private static final float NETHERITE_BYPASS_CHANCE = 0.80F;
    /** echo Shard: the onset is delayed by a random 5–10 minutes. */
    private static final int ECHO_DELAY_MIN_TICKS = 5 * 60 * 20;
    private static final int ECHO_DELAY_MAX_TICKS = 10 * 60 * 20;
    /** dragon's Breath: radius around the CASTER that catches a quarter-duration splash. */
    private static final double DRAGONS_BREATH_RADIUS = 6.0;
    /** redstone-dust random pick: weight = 1 / cost^bias, so cheaper attachments are favoured. */
    private static final double REDSTONE_RANDOM_LOW_BIAS = 1.5;

    private BewitchingTableRitual() {}

    /** also called by the table menu's cast-button fallback. */
    public static void cast(ServerLevel level, BlockPos pos, BewitchingTableBlockEntity table, ServerPlayer caster) {
        ItemStack sacrificialStack = table.getItem(BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM);
        if (sacrificialStack.isEmpty()) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.no_sacrificial").withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack fxItem = sacrificialStack.copy(); // survives clearAll — the item that floats over the table in the FX

        // A coin gambles 1–3 effects rather than selecting one — its own path (see castCoin).
        CoinGamble.Type coinType = CoinGamble.typeOf(sacrificialStack.getItem());
        if (coinType != null) {
            castCoin(level, pos, table, caster, coinType);
            return;
        }

        Holder.Reference<Effect> effect;
        if (sacrificialStack.is(net.minecraft.world.item.Items.REDSTONE)) {
            // redstone dust = random attachment, weighted toward lower-cost ones
            effect = pickRandomLowBiased(caster);
        } else {
            Optional<Holder.Reference<Effect>> maybeEffect = SacrificialItems.findEffect(sacrificialStack.getItem());
            if (maybeEffect.isEmpty()) {
                caster.displayClientMessage(Component.translatable("witchmod.ritual.invalid_sacrificial").withStyle(ChatFormatting.RED), true);
                return;
            }
            effect = maybeEffect.get();
        }

        // slots now accept any item (so a wrong one shows red in the UI); a wrong item blocks the cast here too,
        // as an authoritative backstop for the client's disabled Cast button.
        ItemStack essenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE);
        if (!essenceStack.isEmpty() && !isEssenceItem(essenceStack)) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.bad_essence").withStyle(ChatFormatting.RED), true);
            return;
        }
        // A Block of Cursed Essence in the slot is worth 9, so you can invest well over a stack at once.
        int essenceAvailable = essenceValue(essenceStack);
        boolean essenceWasBlock = essenceStack.getItem() == WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get();

        ItemStack modifierStack = table.getItem(BewitchingTableBlockEntity.SLOT_MODIFIER);
        if (!modifierStack.isEmpty() && ModifierItems.findModifier(modifierStack.getItem()).isEmpty()) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.bad_modifier").withStyle(ChatFormatting.RED), true);
            return;
        }
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem()).orElse(null);

        // quartz: only works on spells the caster has already discovered.
        if (modifier != null && modifier.discoveredEffectsOnly()
                && !DiscoveryManager.hasDiscoveredEffect(caster, effect.key().location())) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.quartz_undiscovered")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // the target slot takes EITHER a Player Essence (cast at a player) OR a jar (bottle the effect into it,
        // rather than hitting anyone). A jar in the slot switches the ritual into "fill" mode.
        ItemStack playerEssenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE);
        boolean fillingJar = !playerEssenceStack.isEmpty() && playerEssenceStack.getItem() instanceof ItemJar;
        ItemStack jarToFill = ItemStack.EMPTY;
        ItemStack dollInSlot = ItemStack.EMPTY; // a bound Voodoo Doll used AS an essence — returned, not consumed
        ServerPlayer target = caster;
        if (fillingJar) {
            if (JarContents.isFull(playerEssenceStack)) {
                caster.displayClientMessage(Component.translatable(
                        "witchmod.ritual.jar_full").withStyle(ChatFormatting.RED), true);
                return; // refuse before spending ingredients
            }
            jarToFill = playerEssenceStack.copy();
            jarToFill.setCount(1);
        } else if (!playerEssenceStack.isEmpty()) {
            boolean isDoll = playerEssenceStack.getItem() instanceof com.oliver.witchmod.items.ItemVoodooDoll
                    && playerEssenceStack.has(WitchModDataComponents.BOUND_PLAYER);
            if (!isDoll && playerEssenceStack.getItem() != WitchModItems.PLAYER_ESSENCE.get()) {
                caster.displayClientMessage(Component.translatable(
                        "witchmod.ritual.bad_target_jar").withStyle(ChatFormatting.RED), true);
                return;
            }
            PlayerEssenceData bound = playerEssenceStack.get(WitchModDataComponents.BOUND_PLAYER);
            ServerPlayer online = bound == null ? null : caster.getServer().getPlayerList().getPlayer(bound.playerId());
            if (online == null) {
                caster.displayClientMessage(Component.translatable("witchmod.ritual.target_offline").withStyle(ChatFormatting.RED), true);
                return;
            }
            target = online;
            if (isDoll) {
                // A bound doll works like a Player Essence but is RETURNED (with a small durability cost), not spent.
                dollInSlot = playerEssenceStack.copy();
                dollInSlot.setCount(1);
            }
        }

        // voodoo Doll: a CURSE cast at the table while you hold a bound doll is FORWARDED onto the doll's target
        // (redirect, not copy). Table-only by design; command casts get a note instead (see BewitchCommand).
        ItemStack forwardingDoll = ItemStack.EMPTY;
        if (!fillingJar && effect.value().category() == EffectCategory.CURSE) {
            ItemStack doll = com.oliver.witchmod.items.ItemVoodooDoll.findBoundDoll(caster);
            if (!doll.isEmpty()) {
                PlayerEssenceData dollBound = doll.get(WitchModDataComponents.BOUND_PLAYER);
                ServerPlayer dollTarget = dollBound == null ? null
                        : caster.getServer().getPlayerList().getPlayer(dollBound.playerId());
                if (dollTarget != null && dollTarget.isAlive()) {
                    target = dollTarget;
                    forwardingDoll = doll;
                    caster.displayClientMessage(Component.translatable(
                            "witchmod.ritual.voodoo_forward", dollBound.playerName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                } else {
                    caster.displayClientMessage(Component.translatable(
                            "witchmod.ritual.voodoo_unavailable")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }

        int rawBaseCost = effectCost(effect); // ⚠ PLACEHOLDER tier proxy (see ModifierCalculator.applyTierBackfire)
        int baseCost = rawBaseCost;
        if (!fillingJar && EffectManager.isActive(target, effect)) {
            // casting on an already-affected target costs more
            baseCost = Math.round(baseCost * (1 + Config.ESCALATING_COST_PERCENT.get() / 100F));
        }
        // amethyst Shard: a follow-up of the SAME category on an already-affected target is cheaper.
        if (!fillingJar && modifier != null && modifier.discountsSameCategory()
                && EffectManager.hasActiveOfCategory(target, effect.value().category())) {
            baseCost = Math.round(baseCost * (1 - AMETHYST_DISCOUNT));
        }
        int adjustedCost = ModifierCalculator.applyCost(baseCost, modifier);
        // only ever SPEND what's needed for the best odds (essence == cost gives the 95% cap); the rest is
        // change, refunded below. When paying with BLOCKS, round the spend UP to a whole block (no loose
        // remainder — 3.5 blocks' worth costs 4 blocks), so change is only ever whole blocks.
        int essenceSpent = Math.min(essenceAvailable, adjustedCost);
        if (essenceWasBlock) {
            essenceSpent = Math.min(essenceAvailable, (essenceSpent + 8) / 9 * 9);
        }
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        // honeycomb: guaranteed success unless the attachment is Major-tier or above.
        if (modifier == Modifier.HONEYCOMB && effect.value().tier() != EffectCostTier.MAJOR) {
            successChance = 1.0F;
        }
        float backfireChance = ModifierCalculator.applyBackfireChance(
                ModifierCalculator.baseBackfireChance(essenceSpent, adjustedCost), modifier);
        if (!fillingJar && EffectManager.activeCount(target) >= Config.MAX_ACTIVE_EFFECTS_PER_PLAYER.get()) {
            // exceeding the limit risks backfire
            backfireChance = Math.min(1F, backfireChance + Config.LIMIT_BACKFIRE_BOOST_PERCENT.get() / 100F);
        }
        // low-tier attachments never backfire; high-tier keep a small floor even at full essence (PLACEHOLDER tier).
        backfireChance = ModifierCalculator.applyTierBackfire(backfireChance, rawBaseCost);
        if (!Config.BACKFIRES_ENABLED.get()) {
            // that probability mass falls through to the fizzle branch below instead.
            backfireChance = 0F;
        }
        int durationTicks = ModifierCalculator.applyDuration(rollBaseDuration(caster.getRandom()), modifier, caster.getRandom());

        // ingredients are spent regardless of outcome — that's the risk of casting.
        table.clearAll();
        // hand back any essence beyond what the odds required (essence is never over-spent).
        refundEssence(caster, essenceWasBlock, essenceAvailable - essenceSpent);

        // using a modifier at all (success or failure) reveals it in the Compendium — its own discovery track.
        if (modifier != null) {
            DiscoveryManager.markModifierDiscovered(caster, modifier);
        }
        // paper modifier: every Ledger entry from this cast is scribbled out and unreadable.
        boolean scribbled = modifier != null && modifier.scribblesLedger();
        // ledger: anchor entries + particle feedback to the ritual site (the table), and note the modifier used.
        net.minecraft.core.GlobalPos eventPos = net.minecraft.core.GlobalPos.of(level.dimension(), pos);
        String modifierName = modifier == null ? null : DiscoveryManager.titleCase(modifier.id());

        // A Voodoo Doll used as the target is NOT consumed — hand it back with a small durability cost.
        if (!dollInSlot.isEmpty()) {
            dollInSlot.hurtAndBreak(Config.VOODOO_TABLE_DOLL_COST.get(), level, caster, it -> {});
            if (!dollInSlot.isEmpty()) {
                giveOrDrop(caster, dollInSlot);
            }
        }

        ResourceLocation effectId = effect.key().location();
        float roll = caster.getRandom().nextFloat();

        if (roll < successChance) {
            if (fillingJar) {
                // bottle it: the essence settles into the jar, which comes back the correct (possibly changed)
                // variant. The jar itself is never consumed — only the essence + sacrificial item are.
                ItemStack filled = JarContents.withAdded(jarToFill, effectId, durationTicks);
                giveOrDrop(caster, filled);
                caster.displayClientMessage(Component.translatable("witchmod.ritual.jar_bottled").withStyle(ChatFormatting.AQUA), true);
                outcomeFx(level, pos, Outcome.JAR_FILLED);
                LedgerLog.log(Optional.of(caster.getName().getString()), "jar", effectId, "jar_filled", level.getGameTime(), scribbled, eventPos, modifierName);
                return;
            }
            // ⚠ Taxes is refused outright when the tax bank has hit its memory ceiling. It must never be
            // cast into a state where the Tax Man would have to void what he takes — see TaxBank.isFull().
            if (effect.value() instanceof CurseAudit && TaxBank.get(caster.server).isFull()) {
                caster.displayClientMessage(Component.translatable(
                        "witchmod.ritual.tax_full").withStyle(ChatFormatting.RED), false);
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        effectId, "refused_bank_full", level.getGameTime(), scribbled, eventPos, modifierName);
                return;
            }
            // modifier application options: Netherite bypasses the Ward (a high chance); Ink Sac hides the
            // wrapper/tell until discovery; Wither Rose disguises it as the opposite category until discovery.
            boolean bypassWard = modifier != null && modifier.bypassesWardAndJar()
                    && caster.getRandom().nextFloat() < NETHERITE_BYPASS_CHANCE;
            int display = modifier == null ? ActiveEffectInstance.DISPLAY_NORMAL
                    : modifier.hidesStartTell() ? ActiveEffectInstance.DISPLAY_HIDDEN
                    : modifier.disguisesCategory() ? ActiveEffectInstance.DISPLAY_DISGUISED
                    : ActiveEffectInstance.DISPLAY_NORMAL;
            EffectManager.ApplyOptions opts = new EffectManager.ApplyOptions(bypassWard, display);

            // echo Shard: the cast succeeds now, but the effect's ONSET is delayed 5–10 minutes (it lands later
            // with its full, normal onset — see DelayedCasts).
            if (modifier != null && modifier.delaysTell()) {
                int delay = ECHO_DELAY_MIN_TICKS + caster.getRandom().nextInt(ECHO_DELAY_MAX_TICKS - ECHO_DELAY_MIN_TICKS + 1);
                DelayedCasts.schedule(target, effect, durationTicks, caster, opts, delay);
                boolean blessingDelayed = effect.value().category() == EffectCategory.BLESSING;
                caster.displayClientMessage(Component.translatable("witchmod.ritual.delayed", effectDisplayName(effect))
                        .withStyle(blessingDelayed ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
                outcomeFx(level, pos, blessingDelayed ? Outcome.SUCCESS_BLESSING : Outcome.SUCCESS_CURSE);
                // delayed onset (Echo Shard): show the item launch at the table, but no target lash — it hasn't landed.
                WitchModNetwork.sendRitualFx(level, pos, 0, blessingDelayed, false, fxItem, null);
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        effectId, "success_delayed", level.getGameTime(), scribbled, eventPos, modifierName);
                LedgerFeedback.pulse(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), blessingDelayed);
                return;
            }

            boolean landed = EffectManager.apply(target, effect, durationTicks, caster, opts);
            if (!landed) {
                // apply() refused (grace period / warding totem / category disabled) and already told the
                // caster why — don't falsely claim success. The Ledger records it as BLOCKED (shielded by a
                // warding Totem) or REFUSED (grace / disabled), and reacts either way.
                boolean shielded = EffectManager.wouldBlock(target, caster);
                WitchMod.LOGGER.info("[ritual] {} on {} {} — cast by {}", effectId, target.getName().getString(),
                        shielded ? "BLOCKED (totem)" : "REFUSED (grace/disabled)", caster.getName().getString());
                // feedback: tell the caster it was BLOCKED (target protected) or that it simply didn't take.
                caster.displayClientMessage(Component.translatable(
                        shielded ? "witchmod.ritual.blocked" : "witchmod.ritual.refused",
                        target.getName().getString()).withStyle(ChatFormatting.RED), true);
                // eye of Ender ("Test the Waters"): spell out every protection the target is carrying.
                if (shielded && modifier != null && modifier.reportsBlocks()) {
                    reportProtections(caster, target);
                }
                outcomeFx(level, pos, Outcome.FIZZLE);
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(), effectId,
                        shielded ? "blocked" : "refused", level.getGameTime(), scribbled, eventPos, modifierName);
                LedgerFeedback.pulse(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), false);
                return;
            }
            boolean blessing = effect.value().category() == EffectCategory.BLESSING;
            WitchMod.LOGGER.info("[ritual] {} APPLIED to {} for {} ticks (cast by {})",
                    effectId, target.getName().getString(), durationTicks, caster.getName().getString());
            LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(), effectId, "success", level.getGameTime(), scribbled, eventPos, modifierName);
            // A successful FORWARD spends a point of the doll's durability (breaks at zero) + logs the redirect.
            if (!forwardingDoll.isEmpty()) {
                forwardingDoll.hurtAndBreak(1, level, caster, it -> caster.displayClientMessage(
                        Component.translatable("witchmod.ritual.voodoo_crumbles").withStyle(ChatFormatting.GRAY), true));
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        effectId, "doll_forward", level.getGameTime(), scribbled, eventPos, modifierName);
            }
            // feedback: it LANDED — name the effect, whom it hit, and roughly how long it will last.
            caster.displayClientMessage(Component.translatable("witchmod.ritual.success",
                            effectDisplayName(effect), target.getName().getString(), durationPhrase(durationTicks))
                    .withStyle(blessing ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
            outcomeFx(level, pos, blessing ? Outcome.SUCCESS_BLESSING : Outcome.SUCCESS_CURSE);
            // the "magic missile": item float + orbs at the table, a lash to the target, then one INTO the
            // target after a short delay — all rendered client-side (see RitualFxClient).
            WitchModNetwork.sendRitualFx(level, pos, 0, blessing, false, fxItem,
                    target != caster && target.level() == level ? target : null);
            // slime Ball / Slime Block modifiers: plant the hidden Infectious / Very Infectious attachment.
            applyInfectious(level, target, modifier, caster, durationTicks);
            // goat Horn / Glow Ink Sac / Dragon's Breath / Recovery Compass.
            applyModifierAftereffects(level, target, modifier, caster, effect, durationTicks, blessing);
            // ledger: any Ledger within range reacts with particle feedback flowing into it.
            LedgerFeedback.pulse(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), blessing);
            // bell modifier: broadcast the cast to everyone.
            if (modifier != null && modifier.announcesCast()) {
                announceCast(level, caster, target, effectDisplayName(effect), blessing);
            }
            return;
        }

        // --- FAILURE (there is no third "nothing" outcome — every non-success is a failure). ---
        // A failed fill doesn't destroy the jar — it just doesn't gain anything. Hand it back.
        if (fillingJar) {
            giveOrDrop(caster, jarToFill);
        }

        // most failures are harmless — a real backfire only fires on the (tier-scaled) backfire chance, so
        // "nothing happens" is the most likely failure. backfireChance is already 0 for low-tier / when
        // backfires are disabled. The FX/sound now match the outcome: a firework BANG on a backfire, a quick
        // FIZZLE when nothing happens.
        if (caster.getRandom().nextFloat() < backfireChance) {
            String kind = doBackfire(level, pos, caster, effect, rawBaseCost, durationTicks);
            // purple when the attachment mirrors onto the caster; messy red otherwise. Client-rendered.
            outcomeFx(level, pos, Outcome.BACKFIRE);
            WitchModNetwork.sendRitualFx(level, pos, 2, false, kind.equals("backfire_mirror"), ItemStack.EMPTY, null);
            LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, kind, level.getGameTime(), scribbled, eventPos, modifierName);
            return;
        }

        outcomeFx(level, pos, Outcome.FIZZLE);
        WitchModNetwork.sendRitualFx(level, pos, 1, false, false, ItemStack.EMPTY, null);
        caster.displayClientMessage(Component.translatable("witchmod.ritual.fail_nothing").withStyle(ChatFormatting.RED), true);
        LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, "fail_nothing", level.getGameTime(), scribbled, eventPos, modifierName);
    }

    /** the visible/audible result played at the table block, so the cast reads even from across the room. */
    private enum Outcome { SUCCESS_CURSE, SUCCESS_BLESSING, JAR_FILLED, FIZZLE, BACKFIRE }

    private static void outcomeFx(ServerLevel level, BlockPos pos, Outcome outcome) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;
        // only the SOUNDS play here — the heavy particle work moved CLIENT-side (RitualFxClient via a payload).
        // JAR_FILLED keeps its small particle puff since it has no client animation.
        switch (outcome) {
            case SUCCESS_CURSE -> {
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_SUCCESS.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.92F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.1F);
            }
            case SUCCESS_BLESSING -> {
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_SUCCESS.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.12F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.6F);
            }
            case JAR_FILLED -> {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, cx, cy, cz, 30, 0.3, 0.5, 0.3, 0.02);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, cx, cy, cz, 40, 0.3, 0.5, 0.3, 0.6);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL_DRAGONBREATH, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            case FIZZLE ->
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_FIZZLE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            case BACKFIRE ->
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_BACKFIRE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.4F, 1.0F);
        }
    }

    /** A readable name for chat, title-cased from the effect id path (e.g. {@code neutral_aggression} → "Neutral Aggression"). */
    /**
     * A coin cast: gambles 1–3 effects onto the target (or the caster, if the target slot is empty), per the
     * coin's {@link CoinGamble} biases. Its own self-contained flow — it deliberately does NOT support jar-fill
     * or Voodoo forwarding (a coin can't be bottled or redirected). Coin failures simply fizzle (no backfire).
     */
    private static void castCoin(ServerLevel level, BlockPos pos, BewitchingTableBlockEntity table,
                                 ServerPlayer caster, CoinGamble.Type coinType) {
        ItemStack essenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE);
        if (!essenceStack.isEmpty() && !isEssenceItem(essenceStack)) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.bad_essence").withStyle(ChatFormatting.RED), true);
            return;
        }
        // A Block of Cursed Essence in the slot is worth 9, so you can invest well over a stack at once.
        int essenceAvailable = essenceValue(essenceStack);
        boolean essenceWasBlock = essenceStack.getItem() == WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get();

        ItemStack modifierStack = table.getItem(BewitchingTableBlockEntity.SLOT_MODIFIER);
        if (!modifierStack.isEmpty() && ModifierItems.findModifier(modifierStack.getItem()).isEmpty()) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.bad_modifier").withStyle(ChatFormatting.RED), true);
            return;
        }
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem()).orElse(null);

        // target: a Player Essence / bound Voodoo Doll (returned, not consumed), or empty = the caster. Jars refused.
        ItemStack targetStack = table.getItem(BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE);
        ServerPlayer target = caster;
        ItemStack dollInSlot = ItemStack.EMPTY;
        if (!targetStack.isEmpty()) {
            if (targetStack.getItem() instanceof ItemJar) {
                caster.displayClientMessage(Component.translatable("witchmod.ritual.coin_jar").withStyle(ChatFormatting.RED), true);
                return;
            }
            boolean isDoll = targetStack.getItem() instanceof com.oliver.witchmod.items.ItemVoodooDoll
                    && targetStack.has(WitchModDataComponents.BOUND_PLAYER);
            if (!isDoll && targetStack.getItem() != WitchModItems.PLAYER_ESSENCE.get()) {
                caster.displayClientMessage(Component.translatable("witchmod.ritual.coin_bad_target").withStyle(ChatFormatting.RED), true);
                return;
            }
            PlayerEssenceData bound = targetStack.get(WitchModDataComponents.BOUND_PLAYER);
            ServerPlayer online = bound == null ? null : caster.getServer().getPlayerList().getPlayer(bound.playerId());
            if (online == null) {
                caster.displayClientMessage(Component.translatable("witchmod.ritual.target_offline").withStyle(ChatFormatting.RED), true);
                return;
            }
            target = online;
            if (isDoll) {
                dollInSlot = targetStack.copy();
                dollInSlot.setCount(1);
            }
        }

        // ingredients spent regardless of outcome.
        ItemStack fxItem = table.getItem(BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM).copy(); // the coin, for the FX float
        table.clearAll();
        if (modifier != null) {
            DiscoveryManager.markModifierDiscovered(caster, modifier);
        }
        boolean scribbled = modifier != null && modifier.scribblesLedger();
        // ledger: anchor entries + particle feedback to the ritual site (the table), and note the modifier used.
        net.minecraft.core.GlobalPos eventPos = net.minecraft.core.GlobalPos.of(level.dimension(), pos);
        String modifierName = modifier == null ? null : DiscoveryManager.titleCase(modifier.id());
        if (!dollInSlot.isEmpty()) {
            dollInSlot.hurtAndBreak(Config.VOODOO_TABLE_DOLL_COST.get(), level, caster, it -> {});
            if (!dollInSlot.isEmpty()) {
                giveOrDrop(caster, dollInSlot);
            }
        }

        int adjustedCost = ModifierCalculator.applyCost(CoinGamble.BASE_COST, modifier);
        int essenceSpent = Math.min(essenceAvailable, adjustedCost); // spend only what the odds need
        if (essenceWasBlock) {
            essenceSpent = Math.min(essenceAvailable, (essenceSpent + 8) / 9 * 9); // whole blocks only
        }
        refundEssence(caster, essenceWasBlock, essenceAvailable - essenceSpent);
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        int durationTicks = ModifierCalculator.applyDuration(rollBaseDuration(caster.getRandom()), modifier, caster.getRandom());
        ResourceLocation coinId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(coinItemFor(coinType));

        if (caster.getRandom().nextFloat() < successChance) {
            List<Holder.Reference<Effect>> got = CoinGamble.gamble(target, coinType, durationTicks, caster, caster.getRandom());
            if (got.isEmpty()) {
                outcomeFx(level, pos, Outcome.FIZZLE);
                caster.displayClientMessage(Component.translatable("witchmod.ritual.coin_nothing").withStyle(ChatFormatting.RED), true);
                return;
            }
            boolean allBlessing = got.stream().allMatch(h -> h.value().category() == EffectCategory.BLESSING);
            boolean badDay = CoinGamble.isBadDay(got);
            String names = got.stream().map(BewitchingTableRitual::effectDisplayName).reduce((a, b) -> a + ", " + b).orElse("");
            Component msg = badDay
                    ? Component.translatable("witchmod.ritual.coin_bad_day", names).withStyle(ChatFormatting.DARK_RED)
                    : Component.translatable("witchmod.ritual.coin_landed", got.size(), names)
                            .withStyle(allBlessing ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE);
            caster.displayClientMessage(msg, true);
            outcomeFx(level, pos, allBlessing ? Outcome.SUCCESS_BLESSING : Outcome.SUCCESS_CURSE);
            WitchModNetwork.sendRitualFx(level, pos, 0, allBlessing, false, fxItem,
                    target != caster && target.level() == level ? target : null);
            applyInfectious(level, target, modifier, caster, durationTicks);
            LedgerFeedback.pulse(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), allBlessing);
            for (Holder.Reference<Effect> h : got) {
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        h.key().location(), "coin", level.getGameTime(), scribbled, eventPos, modifierName);
                if (modifier != null && modifier.announcesCast()) {
                    announceCast(level, caster, target, effectDisplayName(h), h.value().category() == EffectCategory.BLESSING);
                }
            }
            return;
        }

        // A failed coin just clatters flat — no backfire.
        outcomeFx(level, pos, Outcome.FIZZLE);
        WitchModNetwork.sendRitualFx(level, pos, 1, false, false, ItemStack.EMPTY, null);
        caster.displayClientMessage(Component.translatable("witchmod.ritual.coin_flat").withStyle(ChatFormatting.RED), true);
        LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(), coinId, "coin_fail", level.getGameTime(), scribbled, eventPos, modifierName);
    }

    private static net.minecraft.world.item.Item coinItemFor(CoinGamble.Type type) {
        return switch (type) {
            case CURSED -> WitchModItems.CURSED_COIN.get();
            case BLESSED -> WitchModItems.BLESSED_COIN.get();
            case EXECUTIONER -> WitchModItems.EXECUTIONERS_COIN.get();
        };
    }

    /** slime Ball / Slime Block modifiers: also plant the hidden Infectious / Very Infectious attachment. */
    private static void applyInfectious(ServerLevel level, ServerPlayer target, Modifier modifier, ServerPlayer caster, int durationTicks) {
        if (modifier == null || modifier.infectiousLevel() == 0) {
            return;
        }
        if (modifier.infectiousLevel() == 1) {
            EffectManager.applyExact(target, Curses.INFECTIOUS, Config.RITUAL_INFECTIOUS_DURATION_TICKS.get(), caster);
        } else {
            EffectManager.applyExact(target, Curses.VERY_INFECTIOUS, durationTicks, caster);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                target.getX(), target.getY() + 1.0, target.getZ(), 14, 0.3, 0.5, 0.3, 0.0);
    }

    /** goat Horn (sound), Glow Ink Sac (chat reveal), Dragon's Breath (splash), Recovery Compass (no persist). */
    private static void applyModifierAftereffects(ServerLevel level, ServerPlayer target, Modifier modifier,
                                                  ServerPlayer caster, Holder.Reference<Effect> effect, int durationTicks, boolean blessing) {
        if (modifier == null) {
            return;
        }
        if (modifier.loudTriggerTell()) { // Goat Horn — a horn blares as it lands
            level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 3.0F, 0.6F);
        }
        if (modifier.revealsEffectToTarget()) { // Glow Ink Sac — the target is told exactly what they got
            target.displayClientMessage(Component.literal("You've been " + (blessing ? "blessed" : "cursed") + " with ")
                    .withStyle(blessing ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(effectDisplayName(effect) + "!").withStyle(ChatFormatting.BOLD)), false);
            DiscoveryManager.markEffectDiscovered(target, effect.key().location());
        }
        if (modifier.splashToNearby()) { // Dragon's Breath — a quarter-dose splashes onto OTHERS near the caster
            int splashDur = Math.max(1, durationTicks / 4);
            for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
                    caster.getBoundingBox().inflate(DRAGONS_BREATH_RADIUS))) {
                if (nearby != caster && nearby != target) {
                    EffectManager.apply(nearby, effect, splashDur, caster);
                }
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH,
                    caster.getX(), caster.getY() + 1.0, caster.getZ(), 50, DRAGONS_BREATH_RADIUS / 2, 0.7, DRAGONS_BREATH_RADIUS / 2, 0.02);
        }
        if (modifier == Modifier.RECOVERY_COMPASS) { // this attachment does NOT persist past death
            java.util.Set<ResourceLocation> np = target.getData(com.oliver.witchmod.data.WitchModAttachments.NON_PERSISTENT_EFFECTS);
            np.add(effect.key().location());
            target.setData(com.oliver.witchmod.data.WitchModAttachments.NON_PERSISTENT_EFFECTS, np);
        }
    }

    /** bell modifier: tell the whole server who inflicted what on whom. */
    static void announceCast(ServerLevel level, ServerPlayer caster, ServerPlayer target, String effectName, boolean blessing) {
        Component msg = Component.literal("🔔 ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caster.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(blessing ? " blessed " : " cursed ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" with ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(effectName).withStyle(blessing ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static String effectDisplayName(Holder.Reference<Effect> effect) {
        String[] words = effect.key().location().getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** a config cost override wins over the effect's code-defined default. */
    private static int effectCost(Holder.Reference<Effect> effect) {
        Integer override = Config.parsedEffectCostOverrides().get(effect.key().location().toString());
        return override != null ? override : effect.value().baseCost();
    }

    /** picks a random curse/blessing weighted by inverse cost, so cheaper ones are more likely (the redstone mechanic). */
    private static Holder.Reference<Effect> pickRandomLowBiased(ServerPlayer caster) {
        List<Holder.Reference<Effect>> all = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().selectable()).toList();
        double totalWeight = 0.0;
        for (Holder.Reference<Effect> holder : all) {
            totalWeight += weightFor(holder);
        }
        double roll = caster.getRandom().nextDouble() * totalWeight;
        for (Holder.Reference<Effect> holder : all) {
            roll -= weightFor(holder);
            if (roll <= 0.0) {
                return holder;
            }
        }
        return all.get(all.size() - 1); // fallback for floating-point drift
    }

    private static double weightFor(Holder.Reference<Effect> holder) {
        int cost = Math.max(1, effectCost(holder));
        return 1.0 / Math.pow(cost, REDSTONE_RANDOM_LOW_BIAS);
    }

    /**
     * Eye of Ender ("Test the Waters"): send the caster a breakdown of everything shielding the target — a held
     * Ward, a Warding Totem's radius, or bathing in Holy Water — so they know exactly what to counter. Chat
     * (not the action bar) so the whole list is readable at once.
     */
    private static void reportProtections(ServerPlayer caster, ServerPlayer target) {
        caster.displayClientMessage(Component.translatable("witchmod.ritual.probe.header",
                target.getName().getString()).withStyle(ChatFormatting.AQUA), false);
        boolean any = false;
        if (com.oliver.witchmod.items.ItemWard.hasActiveWard(target)) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.probe.ward").withStyle(ChatFormatting.GRAY), false);
            any = true;
        }
        if (WardingTotemBlock.isInRange(target)) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.probe.totem").withStyle(ChatFormatting.GRAY), false);
            any = true;
        }
        if (HolyWater.isProtecting(target)) {
            caster.displayClientMessage(Component.translatable("witchmod.ritual.probe.holy_water").withStyle(ChatFormatting.GRAY), false);
            any = true;
        }
        if (!any) {
            // protected, but not by any of the known sources (e.g. an /effect protected) — say so rather than lie.
            caster.displayClientMessage(Component.translatable("witchmod.ritual.probe.unknown").withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** A random base duration in the 35–60 min window; the Clock modifier later overrides it to a fixed 45. */
    private static int rollBaseDuration(net.minecraft.util.RandomSource rng) {
        int min = Config.RITUAL_MIN_DURATION_TICKS.get();
        int max = Math.max(min, Config.RITUAL_MAX_DURATION_TICKS.get());
        return min + rng.nextInt(max - min + 1);
    }

    /** A translatable "~N minutes" phrase, so the caster's feedback can state how long the effect will last. */
    private static Component durationPhrase(int durationTicks) {
        int minutes = Math.max(1, Math.round(durationTicks / (60F * 20F)));
        return Component.translatable("witchmod.ritual.duration", minutes);
    }

    /** put a stack in the caster's inventory, or drop it at their feet if there's no room. */
    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** loose Cursed Essence OR a Block of Cursed Essence (each block = 9) is valid in the essence slot. */
    public static boolean isEssenceItem(ItemStack s) {
        return s.getItem() == WitchModItems.CURSED_ESSENCE.get()
                || s.getItem() == WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get();
    }

    /** essence value of the slot: loose = count, a Block of Cursed Essence = 9 each. */
    public static int essenceValue(ItemStack s) {
        if (s.getItem() == WitchModItems.CURSED_ESSENCE.get()) {
            return s.getCount();
        }
        if (s.getItem() == WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get()) {
            return s.getCount() * 9;
        }
        return 0;
    }

    /** give unspent essence back — WHOLE blocks if the input was blocks (spend rounds up, no loose), else loose. */
    private static void refundEssence(ServerPlayer caster, boolean wasBlock, int change) {
        if (change <= 0) {
            return;
        }
        if (wasBlock) {
            int blocks = change / 9; // change is a whole-block multiple (spend was rounded up)
            if (blocks > 0) {
                giveOrDrop(caster, new ItemStack(WitchModBlocks.CURSED_ESSENCE_BLOCK_ITEM.get(), blocks));
            }
        } else {
            giveOrDrop(caster, new ItemStack(WitchModItems.CURSED_ESSENCE.get(), change));
        }
    }

    /**
     * a backfire turns the ritual on the caster — one of six weighted outcomes; several scale with the
     * attachment's baseCost-derived strength. returns a short ledger key for the chosen outcome.
     */
    private static String doBackfire(ServerLevel level, BlockPos pos, ServerPlayer caster,
                                     Holder.Reference<Effect> effect, int rawBaseCost, int durationTicks) {
        // 0..1 strength from baseCost, mapped between the two config anchor costs
        int lo = Config.BACKFIRE_STRENGTH_COST_MIN.get();
        int hi = Math.max(lo + 1, Config.BACKFIRE_STRENGTH_COST_MAX.get());
        float strength = net.minecraft.util.Mth.clamp((float) (rawBaseCost - lo) / (hi - lo), 0F, 1F);

        // weighted pick among the six outcomes (explosion rarer since it's the most destructive).
        int[] weights = {1, 3, 2, 3, 2, 3};
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = caster.getRandom().nextInt(total);
        int choice = 0;
        for (int i = 0; i < weights.length; i++) {
            if ((r -= weights[i]) < 0) {
                choice = i;
                break;
            }
        }

        switch (choice) {
            case 0 -> { // The table explodes — power scales with strength, block damage gated on mobGriefing.
                caster.displayClientMessage(Component.translatable("witchmod.ritual.backfire_table").withStyle(ChatFormatting.RED), true);
                float power = net.minecraft.util.Mth.lerp(strength,
                        Config.BACKFIRE_EXPLOSION_POWER_MIN.get().floatValue(), Config.BACKFIRE_EXPLOSION_POWER_MAX.get().floatValue());
                // ⚠ source entity is null so the caster is caught in their own blast (Explosion excludes its source).
                level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, Level.ExplosionInteraction.MOB);
                return "backfire_explode";
            }
            case 1 -> { // The same attachment lands on the caster instead.
                EffectManager.apply(caster, effect, durationTicks, null);
                caster.displayClientMessage(Component.translatable("witchmod.ritual.backfire_mirror").withStyle(ChatFormatting.RED), true);
                return "backfire_mirror";
            }
            case 2 -> { // A sheep named Woolliam pops into existence.
                spawnWoolliam(level, pos);
                caster.displayClientMessage(Component.translatable("witchmod.ritual.backfire_woolliam").withStyle(ChatFormatting.RED), true);
                return "backfire_woolliam";
            }
            case 3 -> { // A random curse, biased to lower tiers.
                Holder.Reference<Effect> curse = pickRandomCurseLowBiased(caster);
                if (curse != null) {
                    EffectManager.apply(caster, curse, durationTicks, null);
                }
                caster.displayClientMessage(Component.translatable("witchmod.ritual.backfire_random").withStyle(ChatFormatting.RED), true);
                return "backfire_random_curse";
            }
            case 4 -> { // Inventory shuffle.
                shuffleInventory(caster);
                caster.displayClientMessage(Component.translatable("witchmod.ritual.backfire_shuffle").withStyle(ChatFormatting.RED), true);
                return "backfire_shuffle";
            }
            default -> { // A chunk of the ritual-backfire damage type, scaled by strength.
                float dmg = net.minecraft.util.Mth.lerp(strength,
                        Config.BACKFIRE_DAMAGE_MIN.get().floatValue(), Config.BACKFIRE_DAMAGE_MAX.get().floatValue());
                caster.hurt(WitchModDamageTypes.ritualBackfire(level), dmg);
                caster.displayClientMessage(Component.translatable("witchmod.ritual.backfire_damage").withStyle(ChatFormatting.RED), true);
                return "backfire_damage";
            }
        }
    }

    private static void spawnWoolliam(ServerLevel level, BlockPos pos) {
        net.minecraft.world.entity.animal.Sheep sheep = net.minecraft.world.entity.EntityType.SHEEP.create(level);
        if (sheep == null) {
            return;
        }
        sheep.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                level.random.nextFloat() * 360F, 0F);
        sheep.setCustomName(Component.literal("Woolliam"));
        sheep.setCustomNameVisible(true);
        sheep.setPersistenceRequired();
        level.addFreshEntity(sheep);
    }

    /** fisher-Yates over the caster's 36 main inventory slots; the open menu re-syncs it to the client. */
    private static void shuffleInventory(ServerPlayer caster) {
        var inv = caster.getInventory();
        for (int i = 35; i > 0; i--) {
            int j = caster.getRandom().nextInt(i + 1);
            ItemStack tmp = inv.getItem(i);
            inv.setItem(i, inv.getItem(j));
            inv.setItem(j, tmp);
        }
        inv.setChanged();
        caster.containerMenu.broadcastChanges();
    }

    private static Holder.Reference<Effect> pickRandomCurseLowBiased(ServerPlayer caster) {
        List<Holder.Reference<Effect>> curses = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().selectable())
                .filter(holder -> holder.value().category() == EffectCategory.CURSE)
                .toList();
        if (curses.isEmpty()) {
            return null;
        }
        double totalWeight = 0.0;
        for (Holder.Reference<Effect> holder : curses) {
            totalWeight += weightFor(holder);
        }
        double roll = caster.getRandom().nextDouble() * totalWeight;
        for (Holder.Reference<Effect> holder : curses) {
            roll -= weightFor(holder);
            if (roll <= 0.0) {
                return holder;
            }
        }
        return curses.get(curses.size() - 1);
    }
}
