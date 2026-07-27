package com.oliver.witchmod.effects;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.effects.blessings.BlessingArmy;
import com.oliver.witchmod.effects.blessings.BlessingBodyguard;
import com.oliver.witchmod.effects.blessings.BlessingHypeMan;
import com.oliver.witchmod.effects.blessings.BlessingSoulBond;
import com.oliver.witchmod.entities.BodyguardEntity;
import com.oliver.witchmod.effects.blessings.BlessingBlacksmith;
import com.oliver.witchmod.effects.blessings.BlessingLastStand;
import com.oliver.witchmod.effects.blessings.BlessingThickSkinned;

/** Blessing hooks that need a game event rather than onApply/onTick/onRemove. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class BlessingEventHandler {
    private BlessingEventHandler() {}

    @SubscribeEvent
    static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Immortality: save from death, breaks after this use (existing behavior).
        if (EffectManager.isActive(player, Blessings.IMMORTALITY)) {
            event.setCanceled(true);
            EffectManager.remove(player, Blessings.IMMORTALITY);
            player.setHealth(1.0F);
            return;
        }
        // Last Stand: single-use revive with brief buffs; consumes the blessing (master-spec Section 6).
        if (EffectManager.isActive(player, Blessings.LAST_STAND)) {
            event.setCanceled(true);
            EffectManager.remove(player, Blessings.LAST_STAND);
            player.setHealth(BlessingLastStand.REVIVE_HEALTH);
            EffectUtil.addTimedEffect(player, MobEffects.DAMAGE_BOOST, BlessingLastStand.BUFF_TICKS, 1);
            EffectUtil.addTimedEffect(player, MobEffects.MOVEMENT_SPEED, BlessingLastStand.BUFF_TICKS, 1);
            EffectUtil.addTimedEffect(player, MobEffects.DAMAGE_RESISTANCE, BlessingLastStand.BUFF_TICKS, 1);
        }
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.THICK_SKINNED)
                && event.getAmount() <= BlessingThickSkinned.DAMAGE_FLOOR) {
            // Small tick damage ignored — single events at or below the floor are fully negated.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.BOUNCY)) {
            // Fall damage negated (the real bounce physics/sounds are deferred — see BlessingBouncy).
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && !event.getOutput().isEmpty()
                && EffectManager.isActive(player, Blessings.BLACKSMITH)) {
            long reduced = Math.max(1L, (long) (event.getCost() * BlessingBlacksmith.ANVIL_COST_MULT));
            event.setCost(reduced);
        }
    }

    @SubscribeEvent
    static void onServerChat(ServerChatEvent event) {
        ServerPlayer speaker = event.getPlayer();
        if (!EffectManager.isActive(speaker, Blessings.LAUGH_TRACK)) {
            return;
        }
        // PROTOTYPE: a server-wide laugh on every message from the blessed player. The custom laugh-track
        // OGGs (Section 12) and the per-message cooldown are deferred — a vanilla sound stands in for now.
        for (Player online : speaker.serverLevel().players()) {
            if (online instanceof ServerPlayer listener) {
                listener.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    /**
     * Fortune: an ore-tag block hands the breaker a few EXTRA drops, additive on top of enchantment Fortune
     * (this event fires with the already-rolled drop list in hand). A silk-touched drop of the ore block
     * itself is left alone so it can't be duplicated into free ore blocks.
     */
    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.FORTUNE)
                || !event.getState().is(Tags.Blocks.ORES)) {
            return;
        }
        List<ItemEntity> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }
        int extra = rollExtraDrops(player.getRandom());
        if (extra <= 0) {
            return;
        }
        Item blockItem = event.getState().getBlock().asItem();
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || stack.is(blockItem)) {
                continue; // skip a silk-touched ore-block drop — only the resource is boosted
            }
            stack.grow(extra);
            Blessings.FORTUNE.get().markDiscoveredByVictim(player);
            return;
        }
    }

    /** 0..max extra drops, peaked at {@code fortuneExtraMode} (a triangular roll). */
    private static int rollExtraDrops(RandomSource random) {
        int min = Config.FORTUNE_EXTRA_MIN.get();
        int max = Config.FORTUNE_EXTRA_MAX.get();
        if (max <= min) {
            return Math.max(0, min);
        }
        int mode = Mth.clamp(Config.FORTUNE_EXTRA_MODE.get(), min, max);
        double range = max - min;
        double c = (mode - min) / range;
        double u = random.nextDouble();
        double x = u < c
                ? min + Math.sqrt(u * range * (mode - min))
                : max - Math.sqrt((1.0 - u) * range * (max - mode));
        return (int) Math.round(x);
    }

    /**
     * Peace: quietly cancel most hostile NATURAL spawns near a blessed player (the other half — shrunken
     * detection — lives in {@link Blessings#PEACE}'s tick). Only monsters, only natural/chunk spawns, so
     * spawners and breeding are untouched.
     */
    @SubscribeEvent
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        Player nearby = event.getLevel().getNearestPlayer(event.getX(), event.getY(), event.getZ(),
                Config.PEACE_RADIUS.get(),
                e -> e instanceof ServerPlayer sp && EffectManager.isActive(sp, Blessings.PEACE));
        if (nearby == null) {
            return;
        }
        // Allow only PEACE_SPAWN_RATE_MULT of them; cancel the rest.
        if (event.getEntity().getRandom().nextDouble() >= Config.PEACE_SPAWN_RATE_MULT.get()) {
            event.setSpawnCancelled(true);
        }
    }

    /** Luck: discovered the first time it could actually have mattered — reeling in a catch. */
    @SubscribeEvent
    static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.LUCK)) {
            Blessings.LUCK.get().markDiscoveredByVictim(player);
        }
    }

    /** Luck: also discovered on opening a loot-tabled container (its loot table is still pending pre-open). */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.LUCK)) {
            return;
        }
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
            Blessings.LUCK.get().markDiscoveredByVictim(player);
        }
    }

    /**
     * Army: a nearby hostile can't even acquire you as a target — vetoed at the source, so its own goals never
     * lock on (rather than clearing the target afterward, which flickered and let hits slip through).
     */
    @SubscribeEvent
    static void onArmyChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Mob mob)
                || !BlessingArmy.isConscriptable(mob)
                || !EffectManager.isActive(player, Blessings.ARMY)) {
            return;
        }
        double radius = Config.ARMY_RADIUS.get();
        if (mob.distanceToSqr(player) <= radius * radius) {
            event.setCanceled(true);
        }
    }

    /**
     * Army: pacified hostiles are neutral, so their damage to you is cancelled outright — and being hit by a
     * GENUINE aggressor (a player, a golem, anything not pacified) rallies the horde onto it.
     */
    @SubscribeEvent
    static void onArmyHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.ARMY)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == player) {
            return;
        }
        if (attacker instanceof Mob mob && BlessingArmy.isConscriptable(mob)) {
            event.setCanceled(true); // neutral to you — no harm, and not something to rally against
            return;
        }
        BlessingArmy.markDefend(player, attacker);
    }

    /**
     * Reflect: a projectile about to strike you is caught and sent precisely back at its shooter, faster. We
     * cancel the impact (so it deals no damage and keeps flying), re-owner it to the player (so it can't
     * re-hit them but CAN hurt the original shooter) and re-aim it. It doesn't home — a straight, quick shot
     * the attacker can dodge.
     */
    @SubscribeEvent
    static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.REFLECT)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        Entity shooter = projectile.getOwner();
        if (shooter == null || shooter == player) {
            return; // nothing to send it back to, and never reflect your own shot
        }
        Vec3 dir = shooter.getEyePosition().subtract(projectile.position());
        if (dir.lengthSqr() < 1.0e-4) {
            return;
        }
        event.setCanceled(true);
        float speed = (float) (projectile.getDeltaMovement().length() * Config.REFLECT_VELOCITY_MULT.get());
        projectile.setOwner(player);
        projectile.shoot(dir.x, dir.y, dir.z, speed, (float) (double) Config.REFLECT_INACCURACY.get());
        // Nudge it a block out of your hitbox toward the shooter so it doesn't immediately re-collide with you.
        Vec3 out = dir.normalize();
        projectile.setPos(player.getX() + out.x, player.getEyeY() + out.y * 0.5, player.getZ() + out.z);
        Blessings.REFLECT.get().markDiscoveredByVictim(player);
    }

    /**
     * Soul Bond: your bound eats {@code soulBondDamageShare} of every hit you take, in your place, and a
     * golden trail flicks out to show who paid. Shared damage is never itself re-shared (its own type is
     * skipped), so two bonded souls can't ping-pong a hit forever.
     */
    @SubscribeEvent
    static void onSoulBondDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)
                || !EffectManager.isActive(caster, Blessings.SOUL_BOND)
                || event.getSource().is(WitchModDamageTypes.SOUL_BOND)) {
            return;
        }
        ServerLevel level = caster.serverLevel();
        LivingEntity bound = BlessingSoulBond.boundEntity(level, caster);
        if (bound == null || bound == caster) {
            return;
        }
        double radius = Config.SOULBOND_RADIUS.get();
        if (bound.distanceToSqr(caster) > radius * radius) {
            return; // the tether only carries while they're actually near you
        }
        float shared = (float) (event.getAmount() * Config.SOULBOND_DAMAGE_SHARE.get());
        if (shared <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() - shared);
        bound.hurt(WitchModDamageTypes.soulBond(level, caster), shared);
        soulBondTrail(level, caster, bound);
    }

    /**
     * Bodyguard: anything striking the anchor — a player, a zombie, a golem — commits the bodyguard to
     * ATTACKING it, so it's an actual guard and not just set dressing.
     */
    @SubscribeEvent
    static void onBodyguardAnchorHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer anchor)
                || !EffectManager.isActive(anchor, Blessings.BODYGUARD)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == anchor
                || attacker instanceof BodyguardEntity) {
            return;
        }
        BodyguardEntity bodyguard = BlessingBodyguard.get(anchor);
        if (bodyguard != null) {
            bodyguard.escalateToAttacking(attacker);
        }
    }

    /** Bodyguard: the entity dying breaks the blessing instantly (master-spec). */
    @SubscribeEvent
    static void onBodyguardDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof BodyguardEntity bodyguard)
                || !(bodyguard.level() instanceof ServerLevel level)) {
            return;
        }
        UUID anchorId = bodyguard.getAnchorId();
        if (anchorId == null) {
            return;
        }
        ServerPlayer anchor = level.getServer().getPlayerList().getPlayer(anchorId);
        if (anchor != null && EffectManager.isActive(anchor, Blessings.BODYGUARD)) {
            EffectManager.remove(anchor, Blessings.BODYGUARD);
        }
        BlessingBodyguard.forget(anchorId);
    }

    /** A golden trail from the caster to the entity that just took a share of their damage. */
    private static void soulBondTrail(ServerLevel level, ServerPlayer caster, LivingEntity bound) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = bound.position().add(0.0, bound.getBbHeight() * 0.5, 0.0);
        Vec3 delta = to.subtract(from);
        int points = Math.max(6, (int) (delta.length() * 2.0));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(delta.scale((double) i / points));
            level.sendParticles(BlessingSoulBond.GOLD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // --- Hype Man: the crowd praises the blessed player for what they do ---------------------------------

    /** Combat — swinging on anything earns a cheer. */
    @SubscribeEvent
    static void onHypeManCombat(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "combat");
        }
    }

    /** Picking an item up off the floor. */
    @SubscribeEvent
    static void onHypeManPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "pickup");
        }
    }

    /** Opening a chest/barrel (a ChestMenu) — "looting". */
    @SubscribeEvent
    static void onHypeManLoot(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getContainer() instanceof ChestMenu
                && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "loot");
        }
    }
}
