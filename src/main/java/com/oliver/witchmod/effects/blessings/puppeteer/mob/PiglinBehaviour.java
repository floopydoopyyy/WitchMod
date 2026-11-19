package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the piglin puppet. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PiglinBehaviour implements PuppetBehaviour {
    public static final PiglinBehaviour INSTANCE = new PiglinBehaviour();

    private PiglinBehaviour() {}

    // --- piglin -------------------------------------------------------------------------------------

    /** who has hit each piglin puppet — anyone in gold among them has broken the truce, and can be fought. */
    private static final Map<UUID, Set<UUID>> PROVOKED = EffectState.sessionConcurrent("puppeteer/piglin/provoked");

    /** a barter in progress: who paid (they get the loot). PUPPET_RAGE_END is when it finishes — synced, so it's the lock. */
    private static final Map<UUID, UUID> BARTERS = EffectState.sessionConcurrent("puppeteer/piglin/barters");

    /** what scares each piglin puppet right now (the nearest soul fire / zombified piglin), re-scanned every few ticks. */
    private static final Map<UUID, java.util.Optional<Vec3>> PIGLIN_FEAR = EffectState.sessionConcurrent("puppeteer/piglin/piglin_fear");

    /** a piglin puppet locked in a barter (PUPPET_RAGE_END doubles as its end — synced, so the client holds you still). */
    public static boolean bartering(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PIGLIN
                && player.getData(WitchModAttachments.PUPPET_RAGE_END) > player.level().getGameTime();
    }

    /** dance left from killing a hoglin, in ticks (PUPPET_DASH_POWER doubles as it). */
    public static int danceLeft(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PIGLIN
                ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : 0;
    }

    /** right-click held past a tap, with dance left: dancing. */
    public static boolean dancing(Player player) {
        return danceLeft(player) > 0 && player.getData(WitchModAttachments.PUPPET_FUSE) >= Config.PUPPETEER_PIGLIN_HOLD_TICKS.get();
    }

    /** a piglin that came with a crossbow: left-click shoots it, and it has no melee. */
    public static boolean piglinCrossbow(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PIGLIN
                && player.getMainHandItem().is(Items.CROSSBOW);
    }

    /** piglin upkeep: zombifying outside the nether, fear, alerts, a barter finishing, and the hold / dance. */
    private static void tickPiglin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (tickZombify(player)) {
            return;
        }
        piglinFear(player);
        tickAlert(player);
        UUID giver = BARTERS.get(player.getUUID());
        if (giver != null && !bartering(player)) {
            BARTERS.remove(player.getUUID());
            finishBarter(player, giver);
        }
        if (HELD.contains(player.getUUID()) && !bartering(player)) {
            player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(player.getData(WitchModAttachments.PUPPET_FUSE) + 1, 1000));
        }
        if (dancing(player)) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, danceLeft(player) - 1);
            int amp = Config.PUPPETEER_PIGLIN_DANCE_REGEN_LEVEL.get() - 1;
            MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
            if (regen == null || regen.getAmplifier() < amp || regen.getDuration() < 10) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, amp, false, false, true));
            }
            if (player.tickCount % 30 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    /** a piglin or a piglin brute — the ones gold-meddling alerts and a rally speak to. */
    static boolean piglinKind(@Nullable PuppetType type) {
        return type == PuppetType.PIGLIN || type == PuppetType.PIGLIN_BRUTE;
    }

    /** outside the nether the clock runs; when it's up you zombify. true if you just did. */
    static boolean tickZombify(ServerPlayer player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        if (Config.PUPPETEER_PIGLIN_ZOMBIFIES.get() && !player.serverLevel().dimensionType().piglinSafe()
                && !tag.getBoolean("IsImmuneToZombification")
                && HOGLIN_TIME.merge(player.getUUID(), 1, Integer::sum) >= Config.PUPPETEER_PIGLIN_ZOMBIFY_TICKS.get()) {
            zombifyPiglin(player);
            return true;
        }
        return false;
    }

    /** the rally cry: nearby piglins and brutes go for whatever you're looking at (the caller sets the cooldown). */
    static void rally(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.blockPosition(), SoundEvents.PIGLIN_ANGRY, SoundSource.PLAYERS, 2.5F, 0.8F);
        LivingEntity prey = lookTarget(player, Config.PUPPETEER_ZOMBIE_RALLY_RANGE.get());
        answerCall(level, player, prey, player.position(), net.minecraft.world.entity.monster.piglin.AbstractPiglin.class,
                EntityType.PIGLIN, false, false); // piglins and brutes; nobody's summoned
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** let go of right-click: a tap rallies; a hold that was a dance just stops dancing. */
    private static void piglinRelease(ServerPlayer player, int charge) {
        if (bartering(player) || (charge >= Config.PUPPETEER_PIGLIN_HOLD_TICKS.get() && danceLeft(player) > 0) || !ready(player)) {
            return;
        }
        cooldown(player, Config.PUPPETEER_PIGLIN_RALLY_COOLDOWN_TICKS.get());
        rally(player);
    }

    /** left-click with a crossbow: a bolt, as the real piglin fires (the crossbow's enchantments, piercing etc., apply). */
    private static void piglinShot(ServerPlayer player) {
        if (!ready2(player) || bartering(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY, level.getGameTime() + Config.PUPPETEER_PIGLIN_CROSSBOW_COOLDOWN_TICKS.get());
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level, player,
                new ItemStack(Items.ARROW), player.getMainHandItem().copy());
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.15F, 1.0F);
        arrow.setCritArrow(true);
        arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /** outside the nether the piglin (or brute) shakes and becomes a zombified piglin — and so do you (it keeps its weapon). */
    private static void zombifyPiglin(ServerPlayer player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIFIED_PIGLIN).toString());
        tag.remove("TimeInOverworld");
        tag.remove("IsImmuneToZombification");
        tag.remove("CannotHunt");
        tag.remove("Inventory");
        tag.remove("Brain");
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setData(WitchModAttachments.PUPPET_TYPE, PuppetType.ZOMBIFIED_PIGLIN.id());
        HOGLIN_TIME.remove(player.getUUID());
        clearPiglin(player);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** drop every bit of piglin-only state (on zombifying or leaving). */
    static void clearPiglin(ServerPlayer player) {
        PROVOKED.remove(player.getUUID());
        BARTERS.remove(player.getUUID());
        PIGLIN_FEAR.remove(player.getUUID());
        Puppets.clearAlert(player);
        player.setData(WitchModAttachments.PUPPET_RAGE_END, 0L);
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
    }

    /** soul fire (vanilla's piglin_repellents) and zombified piglins drive a piglin away, as they scare the real thing. */
    private static void piglinFear(ServerPlayer player) {
        double range = Config.PUPPETEER_PIGLIN_FEAR_RANGE.get();
        if (range <= 0.0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        java.util.Optional<Vec3> fear = PIGLIN_FEAR.get(player.getUUID());
        if (fear == null || player.tickCount % 5 == 0) {
            Vec3 source = BlockPos.findClosestMatch(player.blockPosition(), (int) Math.ceil(range), 4,
                    p -> level.getBlockState(p).is(net.minecraft.tags.BlockTags.PIGLIN_REPELLENTS)).map(Vec3::atCenterOf).orElse(null);
            double best = source == null ? Double.MAX_VALUE : source.distanceToSqr(player.position());
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                    e -> e != player && e.isAlive() && (e instanceof net.minecraft.world.entity.monster.ZombifiedPiglin
                            || (e instanceof Player p && PuppetType.byId(p.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.ZOMBIFIED_PIGLIN)))) {
                double d = e.distanceToSqr(player);
                if (d < best && d <= range * range) {
                    best = d;
                    source = e.position();
                }
            }
            fear = java.util.Optional.ofNullable(source);
            PIGLIN_FEAR.put(player.getUUID(), fear);
        }
        fear.ifPresent(from -> {
            Vec3 away = player.position().subtract(from).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            player.setDeltaMovement(player.getDeltaMovement().add(away.scale(Config.PUPPETEER_PIGLIN_FEAR_STRENGTH.get())));
            player.hurtMarked = true;
        });
    }

    /** a piglin won't start a fight with someone in gold — only once they've hit it first. */
    private static boolean sparedForGold(ServerPlayer piglin, @Nullable Entity target) {
        return possessed(piglin) == PuppetType.PIGLIN && target instanceof LivingEntity living
                && net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingGold(living)
                && !PROVOKED.getOrDefault(piglin.getUUID(), Set.of()).contains(living.getUUID());
    }

    /** someone hands you gold: you're locked in place admiring it, then toss them the loot. */
    private static void startBarter(ServerPlayer piglin, Player giver, ItemStack gold) {
        if (bartering(piglin) || piglin.getData(WitchModAttachments.PUPPET_DATA).getBoolean("IsBaby")) {
            return;
        }
        gold.consume(1, giver);
        BARTERS.put(piglin.getUUID(), giver.getUUID());
        piglin.setData(WitchModAttachments.PUPPET_RAGE_END, piglin.level().getGameTime() + Config.PUPPETEER_PIGLIN_BARTER_TICKS.get());
        HELD.remove(piglin.getUUID());
        piglin.setData(WitchModAttachments.PUPPET_FUSE, 0);
        piglin.serverLevel().playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.PLAYERS, 1.0F, 1.0F);
        piglin.displayClientMessage(Component.translatable("witchmod.puppeteer.piglin_bartering").withStyle(ChatFormatting.GOLD), true);
    }

    /** done admiring: vanilla's barter loot, tossed to whoever paid (or just ahead, if they've gone). */
    private static void finishBarter(ServerPlayer piglin, UUID giverId) {
        ServerLevel level = piglin.serverLevel();
        net.minecraft.world.level.storage.loot.LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.PIGLIN_BARTERING);
        List<ItemStack> loot = table.getRandomItems(new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, piglin)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.PIGLIN_BARTER));
        Player giver = level.getPlayerByUUID(giverId);
        Vec3 to = giver != null && giver.distanceToSqr(piglin) < 256.0 ? giver.position()
                : piglin.position().add(piglin.getLookAngle().scale(2.0));
        piglin.swing(InteractionHand.OFF_HAND, true);
        for (ItemStack stack : loot) {
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.throwItem(piglin, stack, to.add(0.0, 1.0, 0.0));
        }
    }

    /** someone opened a chest or broke gold near a piglin (or brute) puppet: it's told, with a trail to them only it can see. */
    private static void alertPiglins(ServerLevel level, Player offender, BlockPos at) {
        double r = Config.PUPPETEER_PIGLIN_ALERT_RADIUS.get();
        if (r <= 0.0) {
            return;
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(at).inflate(r),
                p -> p != offender && piglinKind(possessed(p)))) {
            Puppets.alert(p, offender, Config.PUPPETEER_PIGLIN_ALERT_TICKS.get(), new Vector3f(1.0F, 0.78F, 0.1F));
            p.displayClientMessage(Component.translatable("witchmod.puppeteer.piglin_alert", offender.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
            p.playNotifySound(SoundEvents.PIGLIN_ANGRY, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    /** an alert's trail (Puppets.tickAlert): gold dust from you to the meddler, and a marker over their head. */
    static void tickAlert(ServerPlayer player) {
        Puppets.tickAlert(player);
    }

    /** opening a chest / barrel / shulker box near a piglin puppet sets it off (as it angers the real ones). */
    @SubscribeEvent
    public static void onOpenGuarded(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().isSpectator()
                || (event.getEntity().isSecondaryUseActive() && !event.getEntity().getMainHandItem().isEmpty())) {
            return; // (crouching with something in hand places it rather than opening)
        }
        net.minecraft.world.level.block.Block block = level.getBlockState(event.getPos()).getBlock();
        if (block instanceof net.minecraft.world.level.block.ChestBlock || block instanceof net.minecraft.world.level.block.BarrelBlock
                || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
            alertPiglins(level, event.getEntity(), event.getPos());
        }
    }

    /** breaking gold (vanilla's guarded_by_piglins blocks) near a piglin puppet sets it off. */
    @SubscribeEvent
    public static void onBreakGuarded(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getState().is(net.minecraft.tags.BlockTags.GUARDED_BY_PIGLINS)) {
            alertPiglins(level, event.getPlayer(), event.getPos());
        }
    }

    /** a piglin puppet that's hit remembers who did it (gold or not, they're fair game now); a barter is cut short. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiglinHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer piglin) || possessed(piglin) != PuppetType.PIGLIN) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != piglin) {
            PROVOKED.computeIfAbsent(piglin.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(attacker.getUUID());
        }
        if (BARTERS.remove(piglin.getUUID()) != null) {
            piglin.setData(WitchModAttachments.PUPPET_RAGE_END, 0L); // startled out of it — and the gold's gone
            piglin.serverLevel().playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_ANGRY, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickPiglin(player);
    }

    /** tap: rally; held (with a hoglin kill to its name): dance. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        piglinRelease(player, charge);
    }

    /** a crossbow piglin's left-click is its crossbow. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (piglinCrossbow(player)) {
            piglinShot(player);
        }
    }

    /** a barter's a stun: no moves, and no stepping out of it either. */
    @Override
    public boolean stunned(ServerPlayer player, PuppetType type) {
        return bartering(player);
    }

    /** no swinging mid-barter or with a crossbow; and it spares anyone in gold who hasn't hit it first. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return bartering(player) || piglinCrossbow(player) || (player instanceof ServerPlayer pig && sparedForGold(pig, target));
    }

    /** gold for a piglin puppet: it HAS to barter — locked in place admiring it (a strategic stun). */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (!stack.isPiglinCurrency()) {
            return false;
        }
        if (hand == InteractionHand.MAIN_HAND && user != puppet) {
            startBarter(puppet, user, stack);
        }
        return true;
    }

    /** a piglin puppet that kills a hoglin earns a victory dance. */
    @Override
    public void killed(ServerPlayer player, PuppetType type, LivingEntity victim) {
        if (victim instanceof net.minecraft.world.entity.monster.hoglin.Hoglin) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, Config.PUPPETEER_PIGLIN_DANCE_TICKS.get());
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.PLAYERS, 1.2F, 1.0F);
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.piglin_dance_ready").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** carries on zombifying where the mob left off. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Possession.HOGLIN_TIME.put(player.getUUID(), data.getInt("TimeInOverworld"));
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        clearPiglin(player);
    }

    /** a piglin's bolts pass straight through anyone in gold who hasn't hit it first. */
    @Override
    public void ownProjectileHit(ServerPlayer player, PuppetType type, Projectile projectile, @Nullable Entity struck,
            ProjectileImpactEvent event) {
        if (sparedForGold(player, struck)) {
            event.setCanceled(true);
        }
    }

    /** a crossbow piglin's left-click is its crossbow. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    /** bartering: just the admiring; else the rally, the dance (once a hoglin's down), and a crossbow on left-click. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (bartering(player)) {
            long left = player.getData(WitchModAttachments.PUPPET_RAGE_END) - player.level().getGameTime();
            out.note(Component.translatable("witchmod.puppeteer.piglin_admiring", (left + 19) / 20).withStyle(ChatFormatting.GOLD));
        } else {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.piglin_rally", PuppetControls.ready(player));
            int dance = danceLeft(player);
            if (dance > 0) { // earned by killing a hoglin
                out.add(List.of(hold(Key.USE)), Component.translatable("witchmod.puppeteer.action.piglin_dance", (dance + 19) / 20), 0L);
            }
        }
        if (piglinCrossbow(player)) {
            out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.piglin_crossbow", PuppetControls.ready2(player));
        }
    }
}
