package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the witch puppet. */
public final class WitchBehaviour implements PuppetBehaviour {
    public static final WitchBehaviour INSTANCE = new WitchBehaviour();

    private WitchBehaviour() {}

    // --- witch --------------------------------------------------------------------------------------

    /** the witch's potion belt — her own repertoire (what she throws, then what she drinks), one per hotbar slot. */
    private static final List<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>> WITCH_BELT = List.of(
            net.minecraft.world.item.alchemy.Potions.HARMING, net.minecraft.world.item.alchemy.Potions.POISON,
            net.minecraft.world.item.alchemy.Potions.SLOWNESS, net.minecraft.world.item.alchemy.Potions.WEAKNESS,
            net.minecraft.world.item.alchemy.Potions.HEALING, net.minecraft.world.item.alchemy.Potions.REGENERATION,
            net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE, net.minecraft.world.item.alchemy.Potions.SWIFTNESS,
            net.minecraft.world.item.alchemy.Potions.WATER_BREATHING);

    /** witches mid-swig → what they're drinking (the start tick is PUPPET_LUNGE_START, synced for the drinking pose). */
    public static final Map<UUID, net.minecraft.world.item.alchemy.PotionContents> DRINKS = EffectState.sessionConcurrent("puppeteer/witch/drinks");

    /** witches whose held throw has brewed into a lingering one (its cue's been given). */
    private static final java.util.Set<UUID> LINGERING = EffectState.sessionSet("puppeteer/witch/lingering");

    private static final ResourceLocation WITCH_DRINK_SLOW_ID = EffectUtil.modifierId("puppeteer_witch_drink_slow");

    /** the belt: a potion in every hotbar slot (puppet tools — can't be dropped, swapped or kept; gone when you leave). */
    private static void giveWitchBelt(ServerPlayer player) {
        for (int i = 0; i < WITCH_BELT.size() && i < 9; i++) {
            ItemStack potion = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, WITCH_BELT.get(i));
            markPuppetTool(potion);
            player.getInventory().setItem(i, potion);
        }
        player.getInventory().selected = 0;
        player.inventoryMenu.broadcastChanges();
    }

    /** the potion picked on the belt (the held hotbar slot), or null. */
    @Nullable
    private static net.minecraft.world.item.alchemy.PotionContents pickedPotion(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return isPuppetTool(held) ? held.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) : null;
    }

    /** a witch puppet mid-swig (both sides: the drink's start is synced as PUPPET_LUNGE_START). */
    public static boolean drinking(Player player) {
        long start = player.getData(WitchModAttachments.PUPPET_LUNGE_START);
        if (start <= 0L || PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) != PuppetType.WITCH) {
            return false;
        }
        try {
            return player.level().getGameTime() < start + Config.PUPPETEER_WITCH_DRINK_TICKS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** the belt's cooldown: the server's gate, and vanilla's grey sweep across every potion on the hotbar. */
    private static void beltCooldown(ServerPlayer player, int ticks) {
        cooldown(player, ticks);
        player.getCooldowns().addCooldown(Items.POTION, ticks);
    }

    /** witch: throw the picked potion — a splash, or (held long enough) a lingering one — the way a player throws one. */
    private static void witchThrow(ServerPlayer player, boolean lingering) {
        net.minecraft.world.item.alchemy.PotionContents potion = pickedPotion(player);
        if (potion == null || !ready(player) || DRINKS.containsKey(player.getUUID())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ItemStack flask = new ItemStack(lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION);
        flask.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, potion);
        net.minecraft.world.entity.projectile.ThrownPotion thrown = new net.minecraft.world.entity.projectile.ThrownPotion(level, player);
        thrown.setItem(flask);
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
        level.addFreshEntity(thrown);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0F,
                0.8F + level.random.nextFloat() * 0.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
        beltCooldown(player, lingering ? Config.PUPPETEER_WITCH_LINGER_COOLDOWN_TICKS.get() : Config.PUPPETEER_WITCH_THROW_COOLDOWN_TICKS.get());
    }

    /** witch: right-click — start drinking the picked potion (the witch's swig, slowed; it works when you finish). */
    private static void witchDrink(ServerPlayer player) {
        net.minecraft.world.item.alchemy.PotionContents potion = pickedPotion(player);
        if (potion == null || DRINKS.containsKey(player.getUUID()) || !ready(player)) {
            return;
        }
        DRINKS.put(player.getUUID(), potion);
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, player.level().getGameTime());
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, WITCH_DRINK_SLOW_ID, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 1.0F,
                0.8F + player.getRandom().nextFloat() * 0.4F);
    }

    /** witch upkeep: finish a swig (the potion takes effect, as drinking one would), and charge a lingering throw. */
    private static void tickWitch(ServerPlayer player) {
        net.minecraft.world.item.alchemy.PotionContents drinking = DRINKS.get(player.getUUID());
        if (drinking != null && player.level().getGameTime()
                >= player.getData(WitchModAttachments.PUPPET_LUNGE_START) + Config.PUPPETEER_WITCH_DRINK_TICKS.get()) {
            DRINKS.remove(player.getUUID());
            player.setData(WitchModAttachments.PUPPET_LUNGE_START, 0L);
            EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, WITCH_DRINK_SLOW_ID);
            drinking.forEachEffect(effect -> {
                if (effect.getEffect().value().isInstantenous()) {
                    effect.getEffect().value().applyInstantenousEffect(player, player, player, effect.getAmplifier(), 1.0);
                } else {
                    player.addEffect(effect);
                }
            });
            beltCooldown(player, Config.PUPPETEER_WITCH_DRINK_COOLDOWN_TICKS.get());
        }
        if (!DRINKS.containsKey(player.getUUID())) {
            tickHold(player, PuppetType.WITCH); // the right-click charge: let go early for a splash, late for a lingering potion
        }
        tickLingerCue(player);
    }

    /**
     * the held throw brewing into a lingering one: a soft bubble as it turns, then a faint wisp of the brew's own colour
     * curling round the witch's hand for as long as it's held — subtle, but plain to whoever's watching.
     */
    private static void tickLingerCue(ServerPlayer player) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (hold < Config.PUPPETEER_WITCH_LINGER_CHARGE_TICKS.get() || !ready(player)) {
            LINGERING.remove(player.getUUID());
            return;
        }
        ServerLevel level = player.serverLevel();
        net.minecraft.world.item.alchemy.PotionContents potion = pickedPotion(player);
        if (LINGERING.add(player.getUUID())) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        if (player.tickCount % 3 == 0 && potion != null) {
            double a = player.tickCount * 0.35;
            double yaw = Math.toRadians(player.yBodyRot);
            // the right hand, a little out in front
            double hx = player.getX() - Math.sin(yaw) * 0.45 - Math.cos(yaw) * 0.35;
            double hz = player.getZ() + Math.cos(yaw) * 0.45 - Math.sin(yaw) * 0.35;
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT,
                            net.minecraft.util.FastColor.ARGB32.opaque(potion.getColor())),
                    hx + Math.cos(a) * 0.25, player.getY() + 1.1 + Math.sin(a * 0.7) * 0.1, hz + Math.sin(a) * 0.25, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickWitch(player);
    }

    /** left-click: drink the picked potion. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        witchDrink(player);
    }

    /** a quick click throws a splash; held long enough it's brewed into a lingering potion. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        witchThrow(player, charge >= Config.PUPPETEER_WITCH_LINGER_CHARGE_TICKS.get());
    }

    /** no melee: the belt is the weapon. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true;
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** her potion belt as the hotbar (after the mob's own held item, so the belt wins). */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        giveWitchBelt(player);
    }

    /** like the real witch: her own brews don't touch her, and other magic barely does (85% off). */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (event.getSource().getEntity() == player) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            event.setAmount(event.getAmount() * 0.15F);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        DRINKS.remove(player.getUUID());
        LINGERING.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, WITCH_DRINK_SLOW_ID);
        player.getCooldowns().removeCooldown(Items.POTION); // the belt's sweep mustn't linger on your real potions
    }

    /** left-click: drink. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.drink", PuppetControls.ready(player));
    }
}
