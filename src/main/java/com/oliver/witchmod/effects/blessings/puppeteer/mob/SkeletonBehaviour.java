package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;

/** puppeteer: the skeleton puppet. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class SkeletonBehaviour implements PuppetBehaviour {
    public static final SkeletonBehaviour INSTANCE = new SkeletonBehaviour();

    private SkeletonBehaviour() {}

    /**
     * skeleton: a real bow in every hotbar slot (so scrolling never leaves you empty-handed) plus one arrow. the bows
     * are unbreakable with Infinity — the arrow is never used up and shots can't be picked up — and every piece is
     * tagged as a puppet tool, so it can't be dropped or swapped and is simply gone when you leave.
     */
    private static void giveBowKit(ServerPlayer player) {
        ItemStack bow = new ItemStack(Items.BOW);
        player.level().registryAccess().lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .flatMap(r -> r.get(net.minecraft.world.item.enchantment.Enchantments.INFINITY))
                .ifPresent(infinity -> bow.enchant(infinity, 1));
        bow.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
        markPuppetTool(bow);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, bow.copy());
        }
        ItemStack arrow = new ItemStack(Items.ARROW);
        markPuppetTool(arrow);
        player.getInventory().setItem(9, arrow);
        player.inventoryMenu.broadcastChanges();
    }

    /** skeletons: whatever your arrow hits, every skeleton near you turns its bow on too. no mark, no summoning. */
    public static void volley(ServerPlayer shooter, LivingEntity hit) {
        double r = Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        for (net.minecraft.world.entity.monster.AbstractSkeleton skeleton : shooter.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.AbstractSkeleton.class, shooter.getBoundingBox().inflate(r),
                s -> s != hit && s.isAlive())) {
            skeleton.setTarget(hit);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        if (type == PuppetType.WITHER_SKELETON && player.hasEffect(MobEffects.WITHER)) {
            player.removeEffect(MobEffects.WITHER); // wither skeletons shrug off wither
        }
    }

    /** a real bow in every hotbar slot (the wither skeleton has its sword instead). */
    @Override
    public void giveKit(ServerPlayer player, PuppetType type) {
        if (type.move() == PuppetMove.BOW) {
            giveBowKit(player);
        }
    }

    /** stray / bogged puppets fire the arrows their mob would: a stray's slow, a bogged's poison (vanilla durations). */
    @Override
    public void arrowFired(ServerPlayer player, PuppetType type, Arrow arrow) {
        if (type == PuppetType.STRAY) {
            arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
        } else if (type == PuppetType.BOGGED) {
            arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        }
    }

    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        // a wither skeleton's hits inflict wither
        if (type == PuppetType.WITHER_SKELETON && event.getSource().getDirectEntity() == player && event.getEntity() != player
                && event.getNewDamage() > 0.0F && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            int seconds = Config.PUPPETEER_WITHER_SKELETON_WITHER_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, seconds * 20), player);
            }
        }
        // whatever your arrow hits, the skeletons around you turn their bows on too
        if (type.move() == PuppetMove.BOW && event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                && !(arrow instanceof ThrownTrident) && arrow.getOwner() == player && event.getEntity() != player) {
            volley(player, event.getEntity());
        }
    }

    /**
     * a skeleton draws its bow a bit faster than a player — extra charge ticks credited on a fixed cadence, so client
     * and server agree, and other draw-speed effects (dexterous) stack on top. runs on both sides.
     */
    @SubscribeEvent
    public static void onUseTick(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getItem().getItem() instanceof BowItem)
                || Puppets.moveOf(player) != PuppetMove.BOW) {
            return;
        }
        double speed;
        try {
            speed = Config.PUPPETEER_SKELETON_DRAW_SPEED.get();
        } catch (IllegalStateException e) {
            return;
        }
        if (speed <= 1.0) {
            return;
        }
        // every Nth remaining tick, skip one: 1.25x → every 4th, 1.5x → every 2nd, 2x+ → every tick (more per tick).
        int every = Math.max(1, (int) Math.round(1.0 / (speed - 1.0)));
        int extra = speed >= 2.0 ? (int) Math.round(speed - 1.0) : 1;
        if (event.getDuration() % every == 0) {
            event.setDuration(Math.max(0, event.getDuration() - extra));
        }
    }
}
