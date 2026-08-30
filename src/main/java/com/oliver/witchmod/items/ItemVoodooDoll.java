package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * Voodoo Doll (CLAUDE.md section 4). Bound to a named player, sympathetic-magic style: what you do to the doll
 * happens to them. Passive: while it sits in your inventory, any curse you cast at the Bewitching Table is
 * forwarded onto its target (see {@link com.oliver.witchmod.blocks.BewitchingTableRitual}). Active:
 * <ul>
 *   <li>Off-hand Player Essence + use → (re)bind (consumes the essence).</li>
 *   <li>Off-hand food + use → FEED the victim (saturation + hunger, bad effects stripped, food consumed).</li>
 *   <li>Hold use (no off-hand item) → SQUEEZE: ramping tick damage + slow with clicks, on a use-cooldown.</li>
 * </ul>
 * See {@link ItemNeedle} (jab), {@link VoodooDollHazards} (fire/water/etc.), and the toss/lightning handlers.
 */
public final class ItemVoodooDoll extends BoundPlayerItem {
    public ItemVoodooDoll(Properties properties) {
        super(properties);
    }

    // --- shared helpers ------------------------------------------------------------------------------

    /** The first bound Voodoo Doll in the player's inventory (main or off-hand), or {@link ItemStack#EMPTY}. */
    public static ItemStack findBoundDoll(ServerPlayer caster) {
        for (ItemStack s : caster.getInventory().items) {
            if (s.getItem() instanceof ItemVoodooDoll && s.has(WitchModDataComponents.BOUND_PLAYER)) {
                return s;
            }
        }
        ItemStack off = caster.getOffhandItem();
        if (off.getItem() instanceof ItemVoodooDoll && off.has(WitchModDataComponents.BOUND_PLAYER)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /** The bound player if they're ONLINE, else null (voodoo needs them present). */
    public static ServerPlayer onlineTarget(ServerPlayer caster, ItemStack doll) {
        PlayerEssenceData bound = doll.get(WitchModDataComponents.BOUND_PLAYER);
        return bound == null ? null : caster.getServer().getPlayerList().getPlayer(bound.playerId());
    }

    /** Bind (or re-bind) a doll to a player, giving it its configurable low durability, fresh. */
    public static void bind(ItemStack doll, PlayerEssenceData essence) {
        doll.set(WitchModDataComponents.BOUND_PLAYER, essence);
        doll.set(DataComponents.MAX_DAMAGE, Config.VOODOO_DOLL_DURABILITY.get());
        doll.set(DataComponents.DAMAGE, 0);
    }

    /**
     * Deal voodoo damage: only {@code voodooUnprotectedFraction} of it ignores armour; the rest is reduced by
     * the vanilla armour formula — so armour helps, but only about half as much as against a normal hit.
     */
    public static void voodooHurt(ServerPlayer target, ServerPlayer caster, float base) {
        float unprotected = (float) (base * Config.VOODOO_UNPROTECTED_FRACTION.get());
        float protectedPart = base - unprotected;
        float armored = afterArmour(protectedPart, (float) target.getArmorValue(),
                (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        target.hurt(WitchModDamageTypes.voodoo(target.serverLevel(), caster), armored + unprotected);
        damageFx(target.serverLevel(), target); // all feedback is FX + sound, no chat text
    }

    /** The satisfying smack + burst that stands in for chat feedback on a voodoo hit. */
    public static void damageFx(ServerLevel level, ServerPlayer target) {
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.6F);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 14, 0.3, 0.5, 0.3, 0.2);
        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.2, target.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
    }

    /** A short camera jolt on the CASTER when they pin/squeeze. */
    public static void casterShake(ServerPlayer caster) {
        caster.setData(WitchModAttachments.VOODOO_SHAKE_END,
                caster.level().getGameTime() + Config.VOODOO_SHAKE_TICKS.get());
    }

    /** Client reported the holder is whipping a bound doll around → slightly disorient the victim. */
    public static void onShake(ServerPlayer caster) {
        ItemStack doll = findBoundDoll(caster);
        if (doll.isEmpty()) {
            return;
        }
        ServerPlayer target = onlineTarget(caster, doll);
        if (target == null) {
            return;
        }
        // Only slightly: a short, low Nausea warps their view + a tiny movement wobble. Refreshed while shaking.
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false, false));
    }

    /** The vanilla armour damage-reduction formula (so we don't depend on CombatRules' shifting signature). */
    private static float afterArmour(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float reduction = Mth.clamp(armor - damage / f, armor * 0.2F, 20.0F);
        return damage * (1.0F - reduction / 25.0F);
    }

    // --- active use ----------------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer caster)) {
            // Client: only START using if it'll be a squeeze (bound, no off-hand item), so the hold registers.
            if (level.isClientSide() && stack.has(WitchModDataComponents.BOUND_PLAYER)
                    && player.getOffhandItem().isEmpty() && hand == InteractionHand.MAIN_HAND) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.success(stack);
        }

        ItemStack offhand = caster.getOffhandItem();

        // Off-hand Player Essence → (re)bind (a magical chime + spark instead of chat text).
        if (offhand.getItem() == WitchModItems.PLAYER_ESSENCE.get()) {
            PlayerEssenceData essence = offhand.get(WitchModDataComponents.BOUND_PLAYER);
            if (essence != null) {
                bind(stack, essence);
                offhand.shrink(1);
                ServerLevel sl = caster.serverLevel();
                sl.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
                sl.sendParticles(ParticleTypes.WITCH, caster.getX(), caster.getY() + 1.2, caster.getZ(), 12, 0.3, 0.4, 0.3, 0.0);
                return InteractionResultHolder.success(stack);
            }
        }

        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        if (bound == null) {
            return InteractionResultHolder.fail(stack); // unbound — nothing to do (tooltip says so)
        }

        // Off-hand food → feed the victim.
        FoodProperties food = offhand.get(DataComponents.FOOD);
        if (food != null) {
            return feed(caster, stack, offhand, bound, food);
        }

        // Otherwise: SQUEEZE (held). Respect the use-cooldown (silent — a fizzle sound conveys it).
        if (caster.getCooldowns().isOnCooldown(this)) {
            caster.serverLevel().playSound(null, caster.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.5F, 0.7F);
            return InteractionResultHolder.fail(stack);
        }
        caster.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private InteractionResultHolder<ItemStack> feed(ServerPlayer caster, ItemStack doll, ItemStack offhand,
                                                    PlayerEssenceData bound, FoodProperties food) {
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            offlineFizzle(caster);
            return InteractionResultHolder.fail(doll);
        }
        // ONLY the nutrition/saturation — never the food's effects, so rotten flesh etc. become "edible".
        target.getFoodData().eat(food.nutrition(), food.saturation());
        offhand.shrink(1);
        ServerLevel level = caster.serverLevel();
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.7F, 1.1F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
        int cost = Config.VOODOO_FEED_DOLL_COST.get();
        if (cost > 0) {
            doll.hurtAndBreak(cost, level, caster, it -> {});
        }
        return InteractionResultHolder.success(doll);
    }

    /** Fling the victim horizontally along {@code lookDir} (shared by throw + fishing rod). */
    public static void fling(ServerPlayer target, net.minecraft.world.phys.Vec3 lookDir, double force, double up) {
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(lookDir.x, 0, lookDir.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new net.minecraft.world.phys.Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        target.push(flat.x * force, up, flat.z * force);
        target.hurtMarked = true;
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.9F);
        target.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.2, 0.3, 0.2, 0.0);
    }

    /** A soft fizzle at the caster when the bound player is offline (voodoo can't reach them). */
    public static void offlineFizzle(ServerPlayer caster) {
        caster.serverLevel().playSound(null, caster.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.5F, 0.7F);
        caster.serverLevel().sendParticles(ParticleTypes.SMOKE, caster.getX(), caster.getY() + 1.2, caster.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
    }

    // --- SQUEEZE (hold) ------------------------------------------------------------------------------

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // effectively "held until released"; we drive it from onUseTick
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer caster)) {
            return;
        }
        int interval = Config.VOODOO_SQUEEZE_TICK_INTERVAL.get();
        int elapsed = getUseDuration(stack, entity) - remainingUseDuration;
        if (elapsed <= 0 || elapsed % interval != 0) {
            return;
        }
        ServerPlayer target = onlineTarget(caster, stack);
        if (target == null) {
            caster.stopUsingItem();
            return;
        }
        int tier = elapsed / interval; // ramps each click
        float damage = (float) (Config.VOODOO_SQUEEZE_BASE_DAMAGE.get() + (tier - 1) * Config.VOODOO_SQUEEZE_RAMP.get());
        voodooHurt(target, caster, damage); // (FX on the victim handled inside)
        int slowLevel = Math.min(3, tier / 2); // slow tier ramps more slowly
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, interval + 10, slowLevel, false, false, true));

        // Squeezing costs the CASTER too: a camera jolt each click + a movement penalty while they hold it.
        casterShake(caster);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, interval + 6,
                Config.VOODOO_SQUEEZE_SELF_SLOW.get(), false, false, false));

        ServerLevel sl = caster.serverLevel();
        // A satisfying click per tick — pitch rises with the tier so you can hear it ramping.
        sl.playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS,
                0.8F, 0.8F + Math.min(1.2F, tier * 0.08F));
        sl.playSound(null, caster.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.4F);

        stack.hurtAndBreak(Config.VOODOO_SQUEEZE_DOLL_COST.get(), sl, caster, it -> caster.stopUsingItem());
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide() && entity instanceof ServerPlayer caster) {
            caster.getCooldowns().addCooldown(this, Config.VOODOO_SQUEEZE_COOLDOWN.get());
        }
    }

    // --- inventory pin (needle carried onto the doll in a slot) --------------------------------------

    /**
     * Right-click a carried Needle onto a bound doll in an inventory slot to PIN the victim — the same jab as
     * using the needle in-hand, but done in the GUI (pick up the needle, right-click over the doll).
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof ItemNeedle)
                || !stack.has(WitchModDataComponents.BOUND_PLAYER)) {
            return false;
        }
        if (!(player instanceof ServerPlayer caster)) {
            return true; // client just consumes the click; the server does the real work
        }
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        ServerPlayer target = caster.getServer().getPlayerList().getPlayer(bound.playerId());
        if (target == null) {
            offlineFizzle(caster);
            return true;
        }
        voodooHurt(target, caster, (float) (double) Config.VOODOO_NEEDLE_BASE_DAMAGE.get());
        casterShake(caster);
        other.shrink(1); // spend the needle
        stack.hurtAndBreak(Config.VOODOO_NEEDLE_DOLL_COST.get(), caster.serverLevel(), caster, it -> {});
        return true;
    }

    // --- tooltip -------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
        if (bound == null) {
            tooltip.add(Component.literal("Unbound — bind to a player with a Player Essence").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.literal("Bound to: " + bound.playerName()).withStyle(ChatFormatting.GRAY));
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            Boolean online = EssenceTooltipClient.isOnline(bound.playerId());
            if (online != null) {
                tooltip.add(online
                        ? Component.literal("● Online").withStyle(ChatFormatting.GREEN)
                        : Component.literal("● Offline — voodoo won't work").withStyle(ChatFormatting.RED));
            }
        }
    }
}
