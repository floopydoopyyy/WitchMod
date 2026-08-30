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

/**
 * Consumes the Table's 4 slots and runs the section 5.7 success/backfire formulas, applying the resulting
 * effect or one of the 3 documented failure outcomes (CLAUDE.md section 2.1): a Neutral event, a Mirror
 * backfire (curse onto the caster), or — rare/high-risk — the table exploding.
 *
 * <p>A failed roll that isn't a backfire simply fizzles. A backfire ({@link #doBackfire}) picks one of six
 * outcomes (table explodes / same attachment onto the caster / Woolliam sheep / random low-biased curse /
 * inventory shuffle / a chunk of {@code witchmod:ritual_backfire} damage), several scaling with the
 * attachment's ⚠ PLACEHOLDER baseCost-derived strength (see CLAUDE.md "Attachment strength/tier TODO").
 */
public final class BewitchingTableRitual {
    private static final int DEFAULT_DURATION_TICKS = 45 * 60 * 20;
    /** How long the Slime Ball modifier's hidden Infectious attachment lasts (~1 hour). */
    private static final int INFECTIOUS_DURATION_TICKS = 60 * 60 * 20;
    /** Amethyst Shard: cost cut when the target already carries a same-category effect. */
    private static final float AMETHYST_DISCOUNT = 0.30F;
    /** Netherite Ingot: chance a cast bypasses the Ward (Warding Totem still applies). */
    private static final float NETHERITE_BYPASS_CHANCE = 0.80F;
    /** Echo Shard: the onset is delayed by a random 5–10 minutes. */
    private static final int ECHO_DELAY_MIN_TICKS = 5 * 60 * 20;
    private static final int ECHO_DELAY_MAX_TICKS = 10 * 60 * 20;
    /** Dragon's Breath: radius around the CASTER that catches a quarter-duration splash. */
    private static final double DRAGONS_BREATH_RADIUS = 6.0;
    /** Redstone-Dust random pick: weight = 1 / cost^bias, so cheaper attachments are favoured (master-spec Section 3). */
    private static final double REDSTONE_RANDOM_LOW_BIAS = 1.5;

    private BewitchingTableRitual() {}

    /** Also called by {@link com.oliver.witchmod.ui.BewitchingTableMenu}'s Cast button (Phase 5's real screen). */
    public static void cast(ServerLevel level, BlockPos pos, BewitchingTableBlockEntity table, ServerPlayer caster) {
        ItemStack sacrificialStack = table.getItem(BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM);
        if (sacrificialStack.isEmpty()) {
            caster.displayClientMessage(Component.literal("No Sacrificial Item — add the item that selects your effect.").withStyle(ChatFormatting.RED), true);
            return;
        }

        // A coin gambles 1–3 effects rather than selecting one — its own path (see castCoin).
        CoinGamble.Type coinType = CoinGamble.typeOf(sacrificialStack.getItem());
        if (coinType != null) {
            castCoin(level, pos, table, caster, coinType);
            return;
        }

        Holder.Reference<Effect> effect;
        if (sacrificialStack.is(net.minecraft.world.item.Items.REDSTONE)) {
            // Redstone Dust = random attachment, weighted toward lower-cost ones (master-spec Section 3).
            effect = pickRandomLowBiased(caster);
        } else {
            Optional<Holder.Reference<Effect>> maybeEffect = SacrificialItems.findEffect(sacrificialStack.getItem());
            if (maybeEffect.isEmpty()) {
                caster.displayClientMessage(Component.literal("Invalid Sacrificial Item — it selects no curse or blessing.").withStyle(ChatFormatting.RED), true);
                return;
            }
            effect = maybeEffect.get();
        }

        // Slots now accept any item (so a wrong one shows red in the UI); a wrong item blocks the cast here too,
        // as an authoritative backstop for the client's disabled Cast button.
        ItemStack essenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE);
        if (!essenceStack.isEmpty() && essenceStack.getItem() != WitchModItems.CURSED_ESSENCE.get()) {
            caster.displayClientMessage(Component.literal("Only Cursed Essence goes in the essence slot.").withStyle(ChatFormatting.RED), true);
            return;
        }
        int essenceSpent = essenceStack.getCount();

        ItemStack modifierStack = table.getItem(BewitchingTableBlockEntity.SLOT_MODIFIER);
        if (!modifierStack.isEmpty() && ModifierItems.findModifier(modifierStack.getItem()).isEmpty()) {
            caster.displayClientMessage(Component.literal("That item isn't a valid Modifier.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem()).orElse(null);

        // Quartz: only works on spells the caster has already discovered.
        if (modifier != null && modifier.discoveredEffectsOnly()
                && !DiscoveryManager.hasDiscoveredEffect(caster, effect.key().location())) {
            caster.displayClientMessage(Component.literal("Quartz only refines a spell you've already discovered.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // The target slot takes EITHER a Player Essence (cast at a player) OR a jar (bottle the effect into it,
        // rather than hitting anyone). A jar in the slot switches the ritual into "fill" mode.
        ItemStack playerEssenceStack = table.getItem(BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE);
        boolean fillingJar = !playerEssenceStack.isEmpty() && playerEssenceStack.getItem() instanceof ItemJar;
        ItemStack jarToFill = ItemStack.EMPTY;
        ItemStack dollInSlot = ItemStack.EMPTY; // a bound Voodoo Doll used AS an essence — returned, not consumed
        ServerPlayer target = caster;
        if (fillingJar) {
            if (JarContents.isFull(playerEssenceStack)) {
                caster.displayClientMessage(Component.literal(
                        "That jar is already full — it can't hold any more.").withStyle(ChatFormatting.RED), true);
                return; // refuse before spending ingredients
            }
            jarToFill = playerEssenceStack.copy();
            jarToFill.setCount(1);
        } else if (!playerEssenceStack.isEmpty()) {
            boolean isDoll = playerEssenceStack.getItem() instanceof com.oliver.witchmod.items.ItemVoodooDoll
                    && playerEssenceStack.has(WitchModDataComponents.BOUND_PLAYER);
            if (!isDoll && playerEssenceStack.getItem() != WitchModItems.PLAYER_ESSENCE.get()) {
                caster.displayClientMessage(Component.literal(
                        "The target slot takes a Player Essence, a bound Voodoo Doll, or a jar.").withStyle(ChatFormatting.RED), true);
                return;
            }
            PlayerEssenceData bound = playerEssenceStack.get(WitchModDataComponents.BOUND_PLAYER);
            ServerPlayer online = bound == null ? null : caster.getServer().getPlayerList().getPlayer(bound.playerId());
            if (online == null) {
                caster.displayClientMessage(Component.literal("Target offline — the essence's owner must be online to be hit.").withStyle(ChatFormatting.RED), true);
                return;
            }
            target = online;
            if (isDoll) {
                // A bound doll works like a Player Essence but is RETURNED (with a small durability cost), not spent.
                dollInSlot = playerEssenceStack.copy();
                dollInSlot.setCount(1);
            }
        }

        // Voodoo Doll: a CURSE cast at the table while you hold a bound doll is FORWARDED onto the doll's target
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
                    caster.displayClientMessage(Component.literal(
                            "The voodoo doll twists the curse toward " + dollBound.playerName() + "...")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                } else {
                    caster.displayClientMessage(Component.literal(
                            "Your voodoo doll's target isn't available — the curse isn't forwarded.")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }

        int rawBaseCost = effectCost(effect); // ⚠ PLACEHOLDER tier proxy (see ModifierCalculator.applyTierBackfire)
        int baseCost = rawBaseCost;
        if (!fillingJar && EffectManager.isActive(target, effect)) {
            // "Casting on an already-affected target costs more" (CLAUDE.md section 2.7).
            baseCost = Math.round(baseCost * (1 + Config.ESCALATING_COST_PERCENT.get() / 100F));
        }
        // Amethyst Shard: a follow-up of the SAME category on an already-affected target is cheaper.
        if (!fillingJar && modifier != null && modifier.discountsSameCategory()
                && EffectManager.hasActiveOfCategory(target, effect.value().category())) {
            baseCost = Math.round(baseCost * (1 - AMETHYST_DISCOUNT));
        }
        int adjustedCost = ModifierCalculator.applyCost(baseCost, modifier);
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        // Honeycomb: guaranteed success unless the attachment is Major-tier or above.
        if (modifier == Modifier.HONEYCOMB && effect.value().tier() != EffectCostTier.MAJOR) {
            successChance = 1.0F;
        }
        float backfireChance = ModifierCalculator.applyBackfireChance(
                ModifierCalculator.baseBackfireChance(essenceSpent, adjustedCost), modifier);
        if (!fillingJar && EffectManager.activeCount(target) >= Config.MAX_ACTIVE_EFFECTS_PER_PLAYER.get()) {
            // "Exceeding the limit risks backfire" (CLAUDE.md section 2.7).
            backfireChance = Math.min(1F, backfireChance + Config.LIMIT_BACKFIRE_BOOST_PERCENT.get() / 100F);
        }
        // Low-tier attachments never backfire; high-tier keep a small floor even at full essence (PLACEHOLDER tier).
        backfireChance = ModifierCalculator.applyTierBackfire(backfireChance, rawBaseCost);
        if (!Config.BACKFIRES_ENABLED.get()) {
            // That probability mass falls through to the fizzle branch below instead.
            backfireChance = 0F;
        }
        int durationTicks = ModifierCalculator.applyDuration(DEFAULT_DURATION_TICKS, modifier, caster.getRandom());

        // Ingredients are spent regardless of outcome — that's the risk of casting.
        table.clearAll();

        // Using a modifier at all (success or failure) reveals it in the Compendium — its own discovery track.
        if (modifier != null) {
            DiscoveryManager.markModifierDiscovered(caster, modifier);
        }
        // Paper modifier: every Ledger entry from this cast is scribbled out and unreadable.
        boolean scribbled = modifier != null && modifier.scribblesLedger();
        // Ledger: anchor entries + particle feedback to the ritual site (the table), and note the modifier used.
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
                // Bottle it: the essence settles into the jar, which comes back the correct (possibly changed)
                // variant. The jar itself is never consumed — only the essence + sacrificial item are.
                ItemStack filled = JarContents.withAdded(jarToFill, effectId, durationTicks);
                giveOrDrop(caster, filled);
                caster.displayClientMessage(Component.literal("Bottled — the essence settles into the jar.").withStyle(ChatFormatting.AQUA), true);
                outcomeFx(level, pos, Outcome.JAR_FILLED);
                LedgerLog.log(Optional.of(caster.getName().getString()), "jar", effectId, "jar_filled", level.getGameTime(), scribbled, eventPos, modifierName);
                return;
            }
            // ⚠ Taxes is refused outright when the tax bank has hit its memory ceiling. It must never be
            // cast into a state where the Tax Man would have to void what he takes — see TaxBank.isFull().
            if (effect.value() instanceof CurseAudit && TaxBank.get(caster.server).isFull()) {
                caster.displayClientMessage(Component.literal(
                        "The ritual fizzles — the tax vaults are full.").withStyle(ChatFormatting.RED), false);
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        effectId, "refused_bank_full", level.getGameTime(), scribbled, eventPos, modifierName);
                return;
            }
            // Modifier application options: Netherite bypasses the Ward (a high chance); Ink Sac hides the
            // wrapper/tell until discovery; Wither Rose disguises it as the opposite category until discovery.
            boolean bypassWard = modifier != null && modifier.bypassesWardAndJar()
                    && caster.getRandom().nextFloat() < NETHERITE_BYPASS_CHANCE;
            int display = modifier == null ? ActiveEffectInstance.DISPLAY_NORMAL
                    : modifier.hidesStartTell() ? ActiveEffectInstance.DISPLAY_HIDDEN
                    : modifier.disguisesCategory() ? ActiveEffectInstance.DISPLAY_DISGUISED
                    : ActiveEffectInstance.DISPLAY_NORMAL;
            EffectManager.ApplyOptions opts = new EffectManager.ApplyOptions(bypassWard, display);

            // Echo Shard: the cast succeeds now, but the effect's ONSET is delayed 5–10 minutes (it lands later
            // with its full, normal onset — see DelayedCasts).
            if (modifier != null && modifier.delaysTell()) {
                int delay = ECHO_DELAY_MIN_TICKS + caster.getRandom().nextInt(ECHO_DELAY_MAX_TICKS - ECHO_DELAY_MIN_TICKS + 1);
                DelayedCasts.schedule(target, effect, durationTicks, caster, opts, delay);
                boolean blessingDelayed = effect.value().category() == EffectCategory.BLESSING;
                caster.displayClientMessage(Component.literal("The ritual takes — " + effectDisplayName(effect) + " will creep in later...")
                        .withStyle(blessingDelayed ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
                outcomeFx(level, pos, blessingDelayed ? Outcome.SUCCESS_BLESSING : Outcome.SUCCESS_CURSE);
                RitualFx.startCircle(level, pos, blessingDelayed);
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        effectId, "success_delayed", level.getGameTime(), scribbled, eventPos, modifierName);
                LedgerFeedback.pulse(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), blessingDelayed);
                return;
            }

            boolean landed = EffectManager.apply(target, effect, durationTicks, caster, opts);
            if (!landed) {
                // apply() refused (grace period / warding totem / category disabled) and already told the
                // caster why — don't falsely claim success. The Ledger records it as BLOCKED (shielded by a
                // Warding Totem) or REFUSED (grace / disabled), and reacts either way.
                boolean shielded = EffectManager.wouldTotemBlock(target, caster);
                WitchMod.LOGGER.info("[ritual] {} on {} {} — cast by {}", effectId, target.getName().getString(),
                        shielded ? "BLOCKED (totem)" : "REFUSED (grace/disabled)", caster.getName().getString());
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
                        Component.literal("The voodoo doll crumbles apart.").withStyle(ChatFormatting.GRAY), true));
                LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(),
                        effectId, "doll_forward", level.getGameTime(), scribbled, eventPos, modifierName);
            }
            caster.displayClientMessage(Component.literal("The ritual succeeds — " + effectDisplayName(effect) + "!")
                    .withStyle(blessing ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
            outcomeFx(level, pos, blessing ? Outcome.SUCCESS_BLESSING : Outcome.SUCCESS_CURSE);
            // The over-the-top ritual circle around the block, plus a directional "lash" into a far-away victim.
            RitualFx.startCircle(level, pos, blessing);
            if (target != caster && target.level() == level
                    && target.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 16.0) {
                RitualFx.startLash(level, pos, target, blessing);
            }
            // Slime Ball / Slime Block modifiers: plant the hidden Infectious / Very Infectious attachment.
            applyInfectious(level, target, modifier, caster, durationTicks);
            // Goat Horn / Glow Ink Sac / Dragon's Breath / Recovery Compass.
            applyModifierAftereffects(level, target, modifier, caster, effect, durationTicks, blessing);
            // Ledger: any Ledger within range reacts with particle feedback flowing into it.
            LedgerFeedback.pulse(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), blessing);
            // Bell modifier: broadcast the cast to everyone.
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

        // Most failures are harmless — a real backfire only fires on the (tier-scaled) backfire chance, so
        // "nothing happens" is the most likely failure. backfireChance is already 0 for low-tier / when
        // backfires are disabled. The FX/sound now match the outcome: a firework BANG on a backfire, a quick
        // FIZZLE when nothing happens.
        if (caster.getRandom().nextFloat() < backfireChance) {
            outcomeFx(level, pos, Outcome.BACKFIRE);
            String kind = doBackfire(level, pos, caster, effect, rawBaseCost, durationTicks);
            LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, kind, level.getGameTime(), scribbled, eventPos, modifierName);
            return;
        }

        outcomeFx(level, pos, Outcome.FIZZLE);
        caster.displayClientMessage(Component.literal("The ritual fails — nothing happens.").withStyle(ChatFormatting.RED), true);
        LedgerLog.log(Optional.of(caster.getName().getString()), caster.getName().getString(), effectId, "fail_nothing", level.getGameTime(), scribbled, eventPos, modifierName);
    }

    /** The visible/audible result played at the table block, so the cast reads even from across the room. */
    private enum Outcome { SUCCESS_CURSE, SUCCESS_BLESSING, JAR_FILLED, FIZZLE, BACKFIRE }

    private static void outcomeFx(ServerLevel level, BlockPos pos, Outcome outcome) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;
        switch (outcome) {
            case SUCCESS_CURSE -> {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, cx, cy, cz, 55, 0.4, 0.7, 0.4, 0.03);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, cx, cy, cz, 70, 0.35, 0.7, 0.35, 0.8);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, cx, cy + 0.4, cz, 12, 0.2, 0.5, 0.2, 0.05);
                // A little magic jingle on success (curse-tinted, a touch lower).
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_SUCCESS.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.92F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.1F);
            }
            case SUCCESS_BLESSING -> {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, cx, cy, cz, 40, 0.4, 0.7, 0.4, 0.12);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, cx, cy, cz, 35, 0.3, 0.6, 0.3, 0.04);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, cx, cy + 0.4, cz, 20, 0.3, 0.5, 0.3, 0.15);
                // The same jingle, brighter, for a blessing.
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_SUCCESS.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.12F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.6F);
            }
            case JAR_FILLED -> {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, cx, cy, cz, 30, 0.3, 0.5, 0.3, 0.02);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, cx, cy, cz, 40, 0.3, 0.5, 0.3, 0.6);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL_DRAGONBREATH, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            case FIZZLE -> {
                // Nothing happens: a quick puff of grey smoke that sags off the block + the quick fizzle sound.
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, cx, cy, cz, 18, 0.28, 0.3, 0.28, 0.01);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, cx, cy, cz, 8, 0.25, 0.25, 0.25, 0.0);
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_FIZZLE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            case BACKFIRE -> {
                // A real backfire — a firework BANG: a bright, colourful burst + flash to match the sound.
                net.minecraft.core.particles.DustParticleOptions red =
                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.90F, 0.20F, 0.15F), 1.6F);
                net.minecraft.core.particles.DustParticleOptions gold =
                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.75F, 0.25F), 1.4F);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, cx, cy + 0.3, cz, 80, 0.1, 0.1, 0.1, 0.35);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, cx, cy + 0.3, cz, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(red, cx, cy + 0.3, cz, 40, 0.4, 0.4, 0.4, 0.05);
                level.sendParticles(gold, cx, cy + 0.3, cz, 30, 0.4, 0.4, 0.4, 0.05);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, cx, cy, cz, 20, 0.3, 0.3, 0.3, 0.02);
                level.playSound(null, pos, com.oliver.witchmod.data.WitchModSounds.RITUAL_BACKFIRE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.4F, 1.0F);
            }
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
        if (!essenceStack.isEmpty() && essenceStack.getItem() != WitchModItems.CURSED_ESSENCE.get()) {
            caster.displayClientMessage(Component.literal("Only Cursed Essence goes in the essence slot.").withStyle(ChatFormatting.RED), true);
            return;
        }
        int essenceSpent = essenceStack.getCount();

        ItemStack modifierStack = table.getItem(BewitchingTableBlockEntity.SLOT_MODIFIER);
        if (!modifierStack.isEmpty() && ModifierItems.findModifier(modifierStack.getItem()).isEmpty()) {
            caster.displayClientMessage(Component.literal("That item isn't a valid Modifier.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem()).orElse(null);

        // Target: a Player Essence / bound Voodoo Doll (returned, not consumed), or empty = the caster. Jars refused.
        ItemStack targetStack = table.getItem(BewitchingTableBlockEntity.SLOT_PLAYER_ESSENCE);
        ServerPlayer target = caster;
        ItemStack dollInSlot = ItemStack.EMPTY;
        if (!targetStack.isEmpty()) {
            if (targetStack.getItem() instanceof ItemJar) {
                caster.displayClientMessage(Component.literal("A jar can't bottle a coin gamble.").withStyle(ChatFormatting.RED), true);
                return;
            }
            boolean isDoll = targetStack.getItem() instanceof com.oliver.witchmod.items.ItemVoodooDoll
                    && targetStack.has(WitchModDataComponents.BOUND_PLAYER);
            if (!isDoll && targetStack.getItem() != WitchModItems.PLAYER_ESSENCE.get()) {
                caster.displayClientMessage(Component.literal("The target slot takes a Player Essence, a bound Voodoo Doll, or nothing.").withStyle(ChatFormatting.RED), true);
                return;
            }
            PlayerEssenceData bound = targetStack.get(WitchModDataComponents.BOUND_PLAYER);
            ServerPlayer online = bound == null ? null : caster.getServer().getPlayerList().getPlayer(bound.playerId());
            if (online == null) {
                caster.displayClientMessage(Component.literal("Target offline — the essence's owner must be online to be hit.").withStyle(ChatFormatting.RED), true);
                return;
            }
            target = online;
            if (isDoll) {
                dollInSlot = targetStack.copy();
                dollInSlot.setCount(1);
            }
        }

        // Ingredients spent regardless of outcome.
        table.clearAll();
        if (modifier != null) {
            DiscoveryManager.markModifierDiscovered(caster, modifier);
        }
        boolean scribbled = modifier != null && modifier.scribblesLedger();
        // Ledger: anchor entries + particle feedback to the ritual site (the table), and note the modifier used.
        net.minecraft.core.GlobalPos eventPos = net.minecraft.core.GlobalPos.of(level.dimension(), pos);
        String modifierName = modifier == null ? null : DiscoveryManager.titleCase(modifier.id());
        if (!dollInSlot.isEmpty()) {
            dollInSlot.hurtAndBreak(Config.VOODOO_TABLE_DOLL_COST.get(), level, caster, it -> {});
            if (!dollInSlot.isEmpty()) {
                giveOrDrop(caster, dollInSlot);
            }
        }

        int adjustedCost = ModifierCalculator.applyCost(CoinGamble.BASE_COST, modifier);
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        int durationTicks = ModifierCalculator.applyDuration(DEFAULT_DURATION_TICKS, modifier, caster.getRandom());
        ResourceLocation coinId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(coinItemFor(coinType));

        if (caster.getRandom().nextFloat() < successChance) {
            List<Holder.Reference<Effect>> got = CoinGamble.gamble(target, coinType, durationTicks, caster, caster.getRandom());
            if (got.isEmpty()) {
                outcomeFx(level, pos, Outcome.FIZZLE);
                caster.displayClientMessage(Component.literal("The coin finds nothing to give.").withStyle(ChatFormatting.RED), true);
                return;
            }
            boolean allBlessing = got.stream().allMatch(h -> h.value().category() == EffectCategory.BLESSING);
            boolean badDay = CoinGamble.isBadDay(got);
            String names = got.stream().map(BewitchingTableRitual::effectDisplayName).reduce((a, b) -> a + ", " + b).orElse("");
            Component msg = badDay
                    ? Component.literal("A BAD DAY! Three heavy curses take hold — " + names).withStyle(ChatFormatting.DARK_RED)
                    : Component.literal("The coin lands " + got.size() + (got.size() == 1 ? " effect: " : " effects: ") + names)
                            .withStyle(allBlessing ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE);
            caster.displayClientMessage(msg, true);
            outcomeFx(level, pos, allBlessing ? Outcome.SUCCESS_BLESSING : Outcome.SUCCESS_CURSE);
            RitualFx.startCircle(level, pos, allBlessing);
            if (target != caster && target.level() == level
                    && target.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 16.0) {
                RitualFx.startLash(level, pos, target, allBlessing);
            }
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
        caster.displayClientMessage(Component.literal("The coin clatters flat — nothing happens.").withStyle(ChatFormatting.RED), true);
        LedgerLog.log(Optional.of(caster.getName().getString()), target.getName().getString(), coinId, "coin_fail", level.getGameTime(), scribbled, eventPos, modifierName);
    }

    private static net.minecraft.world.item.Item coinItemFor(CoinGamble.Type type) {
        return switch (type) {
            case CURSED -> WitchModItems.CURSED_COIN.get();
            case BLESSED -> WitchModItems.BLESSED_COIN.get();
            case EXECUTIONER -> WitchModItems.EXECUTIONERS_COIN.get();
        };
    }

    /** Slime Ball / Slime Block modifiers: also plant the hidden Infectious / Very Infectious attachment. */
    private static void applyInfectious(ServerLevel level, ServerPlayer target, Modifier modifier, ServerPlayer caster, int durationTicks) {
        if (modifier == null || modifier.infectiousLevel() == 0) {
            return;
        }
        if (modifier.infectiousLevel() == 1) {
            EffectManager.applyExact(target, Curses.INFECTIOUS, INFECTIOUS_DURATION_TICKS, caster);
        } else {
            EffectManager.applyExact(target, Curses.VERY_INFECTIOUS, durationTicks, caster);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                target.getX(), target.getY() + 1.0, target.getZ(), 14, 0.3, 0.5, 0.3, 0.0);
    }

    /** Goat Horn (sound), Glow Ink Sac (chat reveal), Dragon's Breath (splash), Recovery Compass (no persist). */
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

    /** Bell modifier: tell the whole server who inflicted what on whom. */
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

    /** Config.EFFECT_COST_OVERRIDES (Phase 6) wins over the effect's code-defined default (section 5.1/5.2). */
    private static int effectCost(Holder.Reference<Effect> effect) {
        Integer override = Config.parsedEffectCostOverrides().get(effect.key().location().toString());
        return override != null ? override : effect.value().baseCost();
    }

    /**
     * Picks a random curse/blessing weighted by inverse cost — {@code weight = 1 / cost^BIAS} — so
     * lower-cost attachments are more likely (master-spec Section 3's Redstone-Dust mechanic).
     */
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

    /** Put a stack in the caster's inventory, or drop it at their feet if there's no room. */
    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * A backfire turns the ritual on the caster — one of six outcomes, weighted. Several scale with the
     * attachment's "strength", which is ⚠ PLACEHOLDER-derived from its baseCost (see CLAUDE.md "Attachment
     * strength/tier TODO"). Returns a short ledger key for the chosen outcome.
     */
    private static String doBackfire(ServerLevel level, BlockPos pos, ServerPlayer caster,
                                     Holder.Reference<Effect> effect, int rawBaseCost, int durationTicks) {
        // ⚠ PLACEHOLDER: 0..1 "strength" from baseCost, mapped between the two config anchor costs.
        int lo = Config.BACKFIRE_STRENGTH_COST_MIN.get();
        int hi = Math.max(lo + 1, Config.BACKFIRE_STRENGTH_COST_MAX.get());
        float strength = net.minecraft.util.Mth.clamp((float) (rawBaseCost - lo) / (hi - lo), 0F, 1F);

        // Weighted pick among the six outcomes (explosion rarer since it's the most destructive).
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
                caster.displayClientMessage(Component.literal("The table couldn't take it...").withStyle(ChatFormatting.RED), true);
                float power = net.minecraft.util.Mth.lerp(strength,
                        Config.BACKFIRE_EXPLOSION_POWER_MIN.get().floatValue(), Config.BACKFIRE_EXPLOSION_POWER_MAX.get().floatValue());
                // ⚠ source entity is null so the caster is caught in their own blast (Explosion excludes its source).
                level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, Level.ExplosionInteraction.MOB);
                return "backfire_explode";
            }
            case 1 -> { // The same attachment lands on the caster instead.
                EffectManager.apply(caster, effect, durationTicks, null);
                caster.displayClientMessage(Component.literal("The ritual backfires — right onto you.").withStyle(ChatFormatting.RED), true);
                return "backfire_mirror";
            }
            case 2 -> { // A sheep named Woolliam pops into existence.
                spawnWoolliam(level, pos);
                caster.displayClientMessage(Component.literal("...a sheep named Woolliam appears.").withStyle(ChatFormatting.RED), true);
                return "backfire_woolliam";
            }
            case 3 -> { // A random curse, biased to lower tiers.
                Holder.Reference<Effect> curse = pickRandomCurseLowBiased(caster);
                if (curse != null) {
                    EffectManager.apply(caster, curse, durationTicks, null);
                }
                caster.displayClientMessage(Component.literal("The ritual lashes back with something else entirely.").withStyle(ChatFormatting.RED), true);
                return "backfire_random_curse";
            }
            case 4 -> { // Inventory shuffle.
                shuffleInventory(caster);
                caster.displayClientMessage(Component.literal("Everything you're carrying is thrown into disarray.").withStyle(ChatFormatting.RED), true);
                return "backfire_shuffle";
            }
            default -> { // A chunk of the ritual-backfire damage type, scaled by strength.
                float dmg = net.minecraft.util.Mth.lerp(strength,
                        Config.BACKFIRE_DAMAGE_MIN.get().floatValue(), Config.BACKFIRE_DAMAGE_MAX.get().floatValue());
                caster.hurt(WitchModDamageTypes.ritualBackfire(level), dmg);
                caster.displayClientMessage(Component.literal("The ritual's energy tears through you.").withStyle(ChatFormatting.RED), true);
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

    /** Fisher-Yates over the caster's 36 main inventory slots; the open menu re-syncs it to the client. */
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
