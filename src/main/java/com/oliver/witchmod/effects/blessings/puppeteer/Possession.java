package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetEvents.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.WitchBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: climbing in and stepping out — the binding animation, the saved mob, the stashed inventory, health scaling. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class Possession {
    private Possession() {}

    public static final ResourceLocation SPEED_ID = EffectUtil.modifierId("puppeteer_speed");

    public static final ResourceLocation REACH_ID = EffectUtil.modifierId("puppeteer_reach");

    public static final ResourceLocation SWIM_ID = EffectUtil.modifierId("puppeteer_swim");

    private static final ResourceLocation BABY_SCALE_ID = EffectUtil.modifierId("puppeteer_baby_scale");

    private static final ResourceLocation BABY_SPEED_ID = EffectUtil.modifierId("puppeteer_baby_speed");

    /** a possession in progress: the frozen mob, where the player started, and the mob's own flags to restore. */
    public record Binding(UUID mobId, Vec3 from, long start, long end, boolean mobNoAi, boolean mobInvulnerable) {}

    public static final Map<UUID, Binding> BINDINGS = EffectState.sessionConcurrent("puppeteer/possession/bindings");

    /** the mob's own max health, before your blessings/curses scale it. */
    public static final Map<UUID, Float> BASE_MAX = EffectState.sessionConcurrent("puppeteer/possession/base_max");

    /** mobs just stepped out of → the game time they wake up (see {@link #daze}). */
    private static final Map<UUID, Long> DAZED = EffectState.sessionConcurrent("puppeteer/possession/dazed");

    /** a hoglin puppet's ticks outside the nether (written back to the mob as TimeInOverworld on release). */
    public static final Map<UUID, Integer> HOGLIN_TIME = EffectState.sessionConcurrent("puppeteer/possession/hoglin_time");

    // --- the mob's own weapon ------------------------------------------------------------------------

    /** the weapon the possessed mob was holding (its main hand, from the puppet data) — empty if none. */
    public static ItemStack mobWeapon(ServerPlayer player, CompoundTag tag) {
        ListTag hands = tag.getList("HandItems", Tag.TAG_COMPOUND);
        return hands.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(player.registryAccess(), hands.getCompound(0));
    }

    /** the mob's weapon enchantments carry onto your puppet bow / crossbow (a Power bow stays a Power bow). */
    private static void copyEnchantments(ItemStack from, ItemStack onto) {
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments = from.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (enchantments == null) {
            return;
        }
        for (var entry : enchantments.entrySet()) {
            if (onto.getEnchantments().getLevel(entry.getKey()) < entry.getIntValue()) {
                onto.enchant(entry.getKey(), entry.getIntValue());
            }
        }
    }

    // --- effects gained as a puppet -----------------------------------------------------------------

    private static final String PRIOR_EFFECTS_KEY = "witchmod_prior_effects";

    /** remember which effects you had going in (saved in the puppet data, so it survives a crash too). */
    // --- hunger is set aside while possessing --------------------------------------------------------

    private static final String PRIOR_FOOD_KEY = "witchmod_prior_food";

    /** remember your hunger going in (in the puppet data, so a crash or relog keeps it). */
    private static void notePriorFood(ServerPlayer player, CompoundTag tag) {
        net.minecraft.world.food.FoodData food = player.getFoodData();
        CompoundTag saved = new CompoundTag();
        saved.putInt("food", food.getFoodLevel());
        saved.putFloat("saturation", food.getSaturationLevel());
        saved.putFloat("exhaustion", food.getExhaustionLevel());
        tag.put(PRIOR_FOOD_KEY, saved);
    }

    /**
     * as a puppet you count as fed: sprinting allowed, no starving. held at 17 with nothing in reserve, so it never
     * feeds natural regen either (that would heal the puppet, which a real mob wouldn't). the bar's hidden anyway.
     */
    public static void holdHunger(ServerPlayer player) {
        net.minecraft.world.food.FoodData food = player.getFoodData();
        if (food.getFoodLevel() != 17) {
            food.setFoodLevel(17);
        }
        food.setSaturation(0.0F);
        food.setExhaustion(0.0F);
    }

    /** step out: your hunger is exactly as you left it. */
    private static void restorePriorFood(ServerPlayer player, CompoundTag tag) {
        if (!tag.contains(PRIOR_FOOD_KEY)) {
            return;
        }
        CompoundTag saved = tag.getCompound(PRIOR_FOOD_KEY);
        net.minecraft.world.food.FoodData food = player.getFoodData();
        food.setFoodLevel(saved.getInt("food"));
        food.setSaturation(saved.getFloat("saturation"));
        food.setExhaustion(saved.getFloat("exhaustion"));
        tag.remove(PRIOR_FOOD_KEY);
    }

    private static void notePriorEffects(ServerPlayer player, CompoundTag tag) {
        ListTag ids = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effect.getEffect().unwrapKey().ifPresent(key -> ids.add(net.minecraft.nbt.StringTag.valueOf(key.location().toString())));
        }
        tag.put(PRIOR_EFFECTS_KEY, ids);
    }

    /**
     * effects you picked up as the puppet (drinking a potion as a witch, a splash, a beacon...) belong to the mob: they
     * come off you when you step out, and go with it. @return them, to hand to the restored mob (empty if unknown).
     */
    private static List<MobEffectInstance> takePuppetEffects(ServerPlayer player, CompoundTag tag) {
        List<MobEffectInstance> taken = new ArrayList<>();
        if (!tag.contains(PRIOR_EFFECTS_KEY)) {
            return taken;
        }
        Set<String> prior = new java.util.HashSet<>();
        ListTag ids = tag.getList(PRIOR_EFFECTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < ids.size(); i++) {
            prior.add(ids.getString(i));
        }
        tag.remove(PRIOR_EFFECTS_KEY);
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            String id = effect.getEffect().unwrapKey().map(k -> k.location().toString()).orElse("");
            if (!prior.contains(id)) {
                taken.add(new MobEffectInstance(effect));
                player.removeEffect(effect.getEffect());
            }
        }
        return taken;
    }

    // --- possessing ----------------------------------------------------------------------------------

    /** begin the possession animation: the mob freezes and the player is drawn in over puppeteerPossessTicks. */
    public static boolean startPossession(ServerPlayer player, Mob mob) {
        PuppetType kind = PuppetType.of(mob);
        if (kind == null || isBusy(player) || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        // one possessor at a time: someone else is already drawing into it
        if (BINDINGS.values().stream().anyMatch(other -> other.mobId().equals(mob.getUUID()))) {
            return false;
        }
        // still dazed from someone stepping out of it: give it back its own flags first, so they're what's remembered
        // (recording the daze's no-ai as its own would leave it frozen for good once you step out)
        if (mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            DAZED.remove(mob.getUUID());
            thaw(mob);
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long end = now + Math.max(1, kind.behaviour().bindingTicks(kind)); // (a warden fights it, and takes longer)
        BINDINGS.put(player.getUUID(), new Binding(mob.getUUID(), player.position(), now, end, mob.isNoAi(), mob.isInvulnerable()));
        // remember its own flags ON the mob (saved with it), so a crash mid-possession can't leave it frozen forever.
        mob.setData(WitchModAttachments.PUPPET_FROZEN, (mob.isNoAi() ? 1 : 0) | (mob.isInvulnerable() ? 2 : 0));
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setDeltaMovement(Vec3.ZERO);
        player.setData(WitchModAttachments.PUPPET_BINDING_END, end);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.8F);
        return true;
    }

    /** a thin stream of souls pours into the mob, blessed sparks gather on it, and your body is pulled in. */
    public static void tickBinding(ServerPlayer player, Binding b) {
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(b.mobId());
        if (!(e instanceof Mob mob) || !mob.isAlive()) {
            cancelBinding(player, b, null);
            return;
        }
        long now = level.getGameTime();
        float t = Mth.clamp((float) (now - b.start()) / Math.max(1, b.end() - b.start()), 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        Vec3 at = b.from().lerp(mob.position(), eased);
        player.teleportTo(at.x, at.y, at.z);
        player.setDeltaMovement(Vec3.ZERO);
        PuppetType kind = PuppetType.of(mob);
        if (kind != null) {
            kind.behaviour().bindingTick(player, mob, t, now - b.start()); // the mob's own reaction (a warden's struggle)
        }
        Vec3 soul = player.getEyePosition().lerp(mob.getEyePosition(), level.random.nextFloat());
        level.sendParticles(ParticleTypes.SOUL, soul.x, soul.y, soul.z, 1, 0.03, 0.03, 0.03, 0.0);
        if ((now - b.start()) % 2 == 0) {
            double y = mob.getY() + mob.getBbHeight() * 0.6;
            level.sendParticles(level.random.nextBoolean() ? ParticleTypes.END_ROD : BLESSED_DUST,
                    mob.getX(), y, mob.getZ(), 1, 0.25, 0.3, 0.25, 0.01);
        }
        if (now >= b.end()) {
            completePossession(player, mob, b);
        }
    }

    private static void cancelBinding(ServerPlayer player, Binding b, @Nullable Mob mob) {
        BINDINGS.remove(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_BINDING_END, 0L);
        Mob target = mob;
        if (target == null) {
            // search every dimension — you may have been pulled through a portal mid-possession.
            for (ServerLevel level : player.server.getAllLevels()) {
                if (level.getEntity(b.mobId()) instanceof Mob m) {
                    target = m;
                    break;
                }
            }
        }
        if (target != null) {
            target.setNoAi(b.mobNoAi());
            target.setInvulnerable(b.mobInvulnerable());
            target.removeData(WitchModAttachments.PUPPET_FROZEN);
            PuppetType kind = PuppetType.of(target);
            if (kind != null) {
                kind.behaviour().bindingEnded(target); // whatever its reaction left on it (a warden's roar pose)
            }
        }
    }

    /**
     * a mob that was frozen mid-possession and then saved (crash, unload, the possessor vanishing) thaws the next time
     * it loads, unless someone really is still mid-possession with it.
     */
    @SubscribeEvent
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || !mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        int flags = mob.getData(WitchModAttachments.PUPPET_FROZEN);
        if (flags < 0 || BINDINGS.values().stream().anyMatch(b -> b.mobId().equals(mob.getUUID()))) {
            return;
        }
        DAZED.remove(mob.getUUID());
        thaw(mob);
    }

    /** the possession takes: save + remove the mob, stash the inventory, and become it. */
    private static void completePossession(ServerPlayer player, Mob mob, Binding b) {
        cancelBinding(player, b, mob); // restores the mob's own ai/invulnerability flags before it's saved
        PuppetType type = PuppetType.of(mob);
        // just the mob: riders / mounts stay behind (otherwise leaving would duplicate them).
        mob.ejectPassengers();
        mob.stopRiding();
        CompoundTag tag = new CompoundTag();
        if (type == null || !mob.saveAsPassenger(tag)) {
            return;
        }
        PuppetBehaviour behaviour = type.behaviour();
        ServerLevel level = player.serverLevel();
        player.teleportTo(mob.getX(), mob.getY(), mob.getZ());
        player.setYRot(mob.getYRot());
        player.setYHeadRot(mob.getYHeadRot());
        float health = mob.getHealth();
        BASE_MAX.put(player.getUUID(), mob.getMaxHealth());
        mob.discard();

        stashInventory(player);
        behaviour.giveKit(player, type); // a skeleton's bows, a pillager's crossbows
        ItemStack mobWeapon = mobWeapon(player, tag);
        if (!mobWeapon.isEmpty()) {
            // a skeleton's Power bow, a pillager's Piercing crossbow: the kit gets the mob's own enchantments
            for (int i = 0; i < 9; i++) {
                ItemStack kit = player.getInventory().getItem(i);
                if (isPuppetTool(kit) && (kit.is(Items.BOW) || kit.is(Items.CROSSBOW)) && kit.getItem() == mobWeapon.getItem()) {
                    copyEnchantments(mobWeapon, kit);
                }
            }
        }
        // melee-weapon mobs show their weapon in FIRST person: a visual puppet-tool copy in the main hand, which
        // vanilla swings on each attack (the bow/crossbow kits already fill the hand; this covers axes/swords/tridents).
        // (the drowned is excluded — a real held trident makes vanilla play the throw-charge USE animation; its
        // trident is rendered by hand in first person instead, raised in the spear pose while winding up.)
        if (type.move() != PuppetMove.BOW && type.move() != PuppetMove.CROSSBOW && type != PuppetType.DROWNED) {
            ItemStack display = mobWeapon.copy();
            if (!display.isEmpty()) {
                display.setCount(1);
                markPuppetTool(display);
                // just for show: the item's own attack speed would stack on the puppet's (an axe's -3 drove it to 0,
                // so every swing was "uncharged" and spamming cost nothing). the puppet's stats are its own.
                display.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
                // fill the WHOLE hotbar (like the bow/crossbow kits) so it shows whatever slot the client has selected.
                for (int i = 0; i < 9; i++) {
                    player.getInventory().setItem(i, display.copy());
                }
            }
        }
        notePriorEffects(player, tag);
        notePriorFood(player, tag);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, mob.getMaxHealth());
        player.setData(WitchModAttachments.PUPPET_HEALTH, health);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        player.setData(WitchModAttachments.PUPPET_ACTION_READY, 0L);
        player.setData(WitchModAttachments.PUPPET_TYPE, type.id());
        scaleHealth(player);
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, type.speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            EffectUtil.addModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, type.reach() - reach.getBaseValue(),
                    AttributeModifier.Operation.ADD_VALUE);
        }
        if (swimmer(type)) {
            double bonus = type == PuppetType.DROWNED ? Config.PUPPETEER_DROWNED_SWIM_BONUS.get()
                    : type == PuppetType.DOLPHIN ? Config.PUPPETEER_DOLPHIN_SWIM_BONUS.get()
                    : type == PuppetType.AXOLOTL ? Config.PUPPETEER_AXOLOTL_SWIM_BONUS.get()
                    : type.group() == PuppetGroup.GUARDIAN ? Config.PUPPETEER_GUARDIAN_SWIM_BONUS.get()
                    : type == PuppetType.TURTLE ? Config.PUPPETEER_TURTLE_SWIM_BONUS.get()
                    : type.group() == PuppetGroup.SQUID ? Config.PUPPETEER_SQUID_SWIM.get() : Config.PUPPETEER_FISH_SWIM_BONUS.get();
            EffectUtil.addModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
        if (mob.isBaby()) {
            // a baby puppet is baby-sized (hitbox and eye height), and baby zombies get their real speed boost.
            EffectUtil.addModifier(player, Attributes.SCALE, BABY_SCALE_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            if (type.group() == PuppetGroup.ZOMBIE) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, BABY_SPEED_ID, Config.PUPPETEER_BABY_ZOMBIE_SPEED.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
        }
        behaviour.possess(player, type, mob, tag); // the mob's own stats, flight, starting state
        player.refreshDimensions(); // eye height follows the mob (see onSize)
        double y = player.getY() + 1.0;
        level.sendParticles(ParticleTypes.SOUL, player.getX(), y, player.getZ(), 6, 0.3, 0.5, 0.3, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 8, 0.35, 0.5, 0.35, 0.04);
        level.sendParticles(BLESSED_DUST, player.getX(), y, player.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS, 1.0F, 0.9F);
        Blessings.PUPPETEER.get().markDiscoveredByVictim(player);
    }

    /**
     * step out (or be thrown out). the mob is put back where you stand with its remaining health (back on its own
     * scale); with {@code killedBy} it comes back only to die of that damage (normal drops), and a {@code consumed}
     * puppet (a detonated creeper) isn't restored at all. @return true if anything was possessed or binding.
     */
    public static boolean release(ServerPlayer player, @Nullable DamageSource killedBy, boolean consumed) {
        Binding binding = BINDINGS.get(player.getUUID());
        if (binding != null) {
            cancelBinding(player, binding, null);
        }
        String typeId = player.getData(WitchModAttachments.PUPPET_TYPE);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        if (killedBy == null && PuppetType.byId(typeId) == PuppetType.VEX && Config.PUPPETEER_VEX_DIES_ON_RELEASE.get()) {
            // a vex is summoned and short-lived: it doesn't fly off when you leave it — it dies, as its life running out does
            killedBy = player.damageSources().starve();
        }
        boolean hadStash = !player.getData(WitchModAttachments.PUPPET_INVENTORY).isEmpty();
        if (typeId.isEmpty() && tag.isEmpty() && !hadStash) {
            return binding != null;
        }
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
        float scaledMax = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
        Float baseMax = BASE_MAX.remove(player.getUUID());
        Integer overworldTime = HOGLIN_TIME.remove(player.getUUID());
        if (overworldTime != null && !tag.isEmpty()) {
            tag.putInt("TimeInOverworld", overworldTime); // the restored hoglin / piglin carries on zombifying where you left off
        }
        endLunge(player);
        // every behaviour drops its own state — you may have changed type while worn (zombified, infected)
        for (PuppetBehaviour behaviour : PuppetBehaviours.all()) {
            behaviour.clear(player, tag);
        }
        player.setData(WitchModAttachments.PUPPET_RAGE_END, 0L);
        player.setData(WitchModAttachments.PUPPET_ACTION3_READY, 0L);
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, 0);
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
        ATTACK_HELD.remove(player.getUUID());
        LUNGES.remove(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_TYPE, "");
        player.refreshDimensions();
        player.setData(WitchModAttachments.PUPPET_DATA, new CompoundTag());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        player.setData(WitchModAttachments.PUPPET_CHARGED, false);
        HELD.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        EffectUtil.removeModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
        EffectUtil.removeModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID);
        EffectUtil.removeModifier(player, Attributes.SCALE, BABY_SCALE_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, BABY_SPEED_ID);
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, CHARGE_SLOW_ID);
        LAST_POS.remove(player.getUUID());
        STILL.remove(player.getUUID());
        revokeBatFlight(player);
        List<MobEffectInstance> carried = takePuppetEffects(player, tag); // effects gained as the puppet go with the mob
        restorePriorFood(player, tag);
        player.setSilent(false);
        restoreInventory(player);
        for (PuppetBehaviour behaviour : PuppetBehaviours.all()) {
            behaviour.released(player); // (a villager's takings, paid out now your inventory's back)
        }
        // back to your own size wherever the puppet left you — burrowed, phased, sunk in, logged out mid-move, crashed:
        // never left stuck in the rock
        unstick(player);
        ServerLevel level = player.serverLevel();
        player.setData(WitchModAttachments.PUPPET_COOLDOWN_END,
                level.getGameTime() + cooldownSeconds(baseMax != null ? baseMax : scaledMax) * 20L);
        if (!tag.isEmpty() && !consumed) {
            if (health > 0.0F) {
                // hand back the same FRACTION of health, on the mob's own (unblessed) scale.
                float fraction = scaledMax > 0.0F ? health / scaledMax : 1.0F;
                tag.putFloat("Health", Math.max(0.5F, fraction * (baseMax != null ? baseMax : scaledMax)));
            }
            Entity restored = EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                Vec3 room = freeSpot(level, e, e.position()); // a bigger mob than you (a warden) gets room to stand
                e.moveTo(room.x, room.y, room.z, player.getYRot(), 0.0F);
                return e;
            });
            if (restored != null && level.tryAddFreshEntityWithPassengers(restored)) {
                if (killedBy != null && restored instanceof LivingEntity living) {
                    living.hurt(killedBy, Float.MAX_VALUE);
                } else if (restored instanceof Mob mob) {
                    carried.forEach(mob::addEffect); // what you picked up as it, it keeps
                    daze(mob);
                    PuppetCombat.handOverGrudges(player, mob); // whatever was after you is after it
                }
            }
        }
        int unseen = Config.PUPPETEER_RELEASE_INVISIBILITY_TICKS.get();
        if (unseen > 0) {
            // a moment unseen as you step out of it
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, unseen, 0, false, false, true));
        }
        double y = player.getY() + 1.0;
        level.sendParticles(ParticleTypes.SOUL, player.getX(), y, player.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 6, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 1.2F);
        return true;
    }

    /**
     * a mob you've just stepped out of stands dazed (no AI) for puppeteerReleaseGraceTicks, so it doesn't turn on you
     * the instant you're out. its own flags go on the mob as PUPPET_FROZEN (saved with it), so whatever happens — the
     * timer, a chunk unload, a crash, a restart — it thaws: by {@link #thawDazed} on time, or by {@link #onEntityJoin}
     * the next time it loads.
     */
    private static void daze(Mob mob) {
        int ticks = Config.PUPPETEER_RELEASE_GRACE_TICKS.get();
        if (ticks <= 0 || mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        mob.setData(WitchModAttachments.PUPPET_FROZEN, (mob.isNoAi() ? 1 : 0) | (mob.isInvulnerable() ? 2 : 0));
        mob.setNoAi(true);
        mob.setTarget(null);
        DAZED.put(mob.getUUID(), mob.level().getGameTime() + ticks);
    }

    /**
     * the backstop: once a second, any mob still carrying the frozen flag with no one possessing it and no daze running
     * thaws — whatever left it that way (a crash, a lost timer, an unload at the wrong moment).
     */
    @SubscribeEvent
    public static void onMobTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || mob.tickCount % 20 != 0
                || !mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        if (BINDINGS.values().stream().anyMatch(b -> b.mobId().equals(mob.getUUID()))) {
            return;
        }
        Long wake = DAZED.get(mob.getUUID());
        if (wake != null && wake > mob.level().getGameTime()) {
            return;
        }
        DAZED.remove(mob.getUUID());
        if (mob.getData(WitchModAttachments.PUPPET_FROZEN) < 0) {
            mob.removeData(WitchModAttachments.PUPPET_FROZEN);
            return;
        }
        thaw(mob);
    }

    /** the server's going down: everything frozen mid-possession or dazed is given its own flags back before it's saved. */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (UUID id : new java.util.HashSet<>(DAZED.keySet())) {
                if (level.getEntity(id) instanceof Mob mob) {
                    thaw(mob);
                }
            }
            for (Binding b : BINDINGS.values()) {
                if (level.getEntity(b.mobId()) instanceof Mob mob) {
                    mob.setNoAi(b.mobNoAi());
                    mob.setInvulnerable(b.mobInvulnerable());
                    mob.removeData(WitchModAttachments.PUPPET_FROZEN);
                }
            }
        }
        DAZED.clear();
    }

    /** gives a frozen / dazed mob back its own ai and invulnerability flags. */
    private static void thaw(Mob mob) {
        if (!mob.hasData(WitchModAttachments.PUPPET_FROZEN)) {
            return;
        }
        int flags = mob.getData(WitchModAttachments.PUPPET_FROZEN);
        mob.setNoAi((flags & 1) != 0);
        mob.setInvulnerable((flags & 2) != 0);
        mob.removeData(WitchModAttachments.PUPPET_FROZEN);
    }

    /** wakes dazed mobs whose time is up. one that's not loaded right now is simply forgotten — it thaws on load. */
    public static void thawDazed(net.minecraft.server.MinecraftServer server) {
        if (DAZED.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        DAZED.entrySet().removeIf(e -> {
            if (now < e.getValue()) {
                return false;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(e.getKey()) instanceof Mob mob) {
                    thaw(mob);
                    break;
                }
            }
            return true;
        });
    }

    /**
     * your own max health scales the puppet's: a tank-blessed player makes a proportionally tankier puppet, an
     * allergic one a frailer puppet. keeps the current health's fraction when the max moves.
     */
    public static void scaleHealth(ServerPlayer player) {
        Float base = BASE_MAX.get(player.getUUID());
        AttributeInstance maxAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (base == null || maxAttr == null || maxAttr.getBaseValue() <= 0.0) {
            return;
        }
        float max = Math.max(1.0F, (float) (base * maxAttr.getValue() / maxAttr.getBaseValue()));
        float oldMax = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
        if (Math.abs(max - oldMax) < 0.01F) {
            return;
        }
        float health = player.getData(WitchModAttachments.PUPPET_HEALTH);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, max);
        player.setData(WitchModAttachments.PUPPET_HEALTH, oldMax > 0.0F ? Math.min(max, health * max / oldMax) : max);
    }

    /** the cooldown after leaving a puppet: the base for weak mobs, rising with the mob's own max health to the strong one. */
    static long cooldownSeconds(float maxHealth) {
        double weak = Config.PUPPETEER_COOLDOWN_WEAK_HEALTH.get();
        double strong = Math.max(weak + 1.0, Config.PUPPETEER_COOLDOWN_STRONG_HEALTH.get());
        double share = Mth.clamp((maxHealth - weak) / (strong - weak), 0.0, 1.0);
        return Math.round(Mth.lerp(share, Config.PUPPETEER_COOLDOWN_SECONDS.get(), Config.PUPPETEER_STRONG_COOLDOWN_SECONDS.get()));
    }

    /** the whole inventory (armour and offhand too) is put away so nothing can be used while possessed. */
    private static void stashInventory(ServerPlayer player) {
        if (!player.getData(WitchModAttachments.PUPPET_INVENTORY).isEmpty()) {
            restoreInventory(player); // an old stash never gets written over: it's handed back first
        }
        CompoundTag stash = new CompoundTag();
        stash.put("Items", player.getInventory().save(new ListTag()));
        stash.putInt("Selected", player.getInventory().selected);
        player.setData(WitchModAttachments.PUPPET_INVENTORY, stash);
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
    }

    private static final String PUPPET_TOOL_KEY = "witchmod_puppet_tool";

    public static void markPuppetTool(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PUPPET_TOOL_KEY, true);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    public static boolean isPuppetTool(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(PUPPET_TOOL_KEY);
    }

    private static void restoreInventory(ServerPlayer player) {
        CompoundTag stash = player.getData(WitchModAttachments.PUPPET_INVENTORY);
        if (stash.isEmpty()) {
            return;
        }
        // anything that somehow got in meanwhile is handed back on top, never lost.
        List<ItemStack> strays = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && !isPuppetTool(s)) {
                strays.add(s.copy());
            }
        }
        try {
            player.getInventory().load(stash.getList("Items", Tag.TAG_COMPOUND));
        } catch (RuntimeException e) {
            // never drop the stash on a bad load: it stays saved on you and is tried again next login
            com.oliver.witchmod.WitchMod.LOGGER.error("puppeteer: couldn't restore {}'s inventory, keeping it stashed", player.getName().getString(), e);
            player.getInventory().clearContent(); // back to just the strays: nothing half-loaded to double up next time
            for (ItemStack s : strays) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
            return;
        }
        player.setData(WitchModAttachments.PUPPET_INVENTORY, new CompoundTag()); // (only once it's safely back)
        player.getInventory().selected = Mth.clamp(stash.getInt("Selected"), 0, 8);
        for (ItemStack s : strays) {
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }
}
