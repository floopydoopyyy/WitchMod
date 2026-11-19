package com.oliver.witchmod.effects.blessings.puppeteer;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.BreezeBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.CatBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.IronGolemBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.VillagerBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;

/** puppeteer: possessing, interacting, and every other world event a puppet reacts to. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PuppetEvents {
    private PuppetEvents() {}

    /**
     * a puppet sees from its mob's eyes: your eye height (camera) becomes the mob's — a chicken's view hugs the grass,
     * a wither skeleton's towers. scaled with you (babies, size effects) and the puppet's own body scale (a slime's
     * size). a mob taller than you also gets the height to match, so that camera never pokes through a ceiling.
     * swimming only ever lowers it. runs on both sides; the server refreshes on possess / release, clients when the
     * synced puppet type changes (PuppeteerClient).
     */
    @SubscribeEvent
    public static void onSize(net.neoforged.neoforge.event.entity.EntityEvent.Size event) {
        // hasData first: this fires inside the player's CONSTRUCTOR, and getData on a missing synced attachment
        // creates it and tries to sync it — to a player with no connection yet (NPE → "Invalid player data").
        if (!(event.getEntity() instanceof Player player) || !player.hasData(WitchModAttachments.PUPPET_TYPE)) {
            return;
        }
        PuppetType type = PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE));
        net.minecraft.world.entity.Pose pose = event.getPose();
        if (type == null || pose == net.minecraft.world.entity.Pose.SLEEPING || pose == net.minecraft.world.entity.Pose.DYING) {
            return;
        }
        PuppetBehaviour behaviour = type.behaviour();
        net.minecraft.world.entity.EntityDimensions cur = event.getNewSize();
        net.minecraft.world.entity.EntityDimensions mob = type.entityType().getDimensions();
        float scale = player.getScale() * behaviour.bodyScale(player, type);
        float eye = mob.eyeHeight() * scale;
        float height = cur.height();
        if (pose == net.minecraft.world.entity.Pose.SWIMMING) {
            eye = Math.min(eye, cur.eyeHeight());
        } else {
            height = Math.max(height, mob.height() * scale);
        }
        eye = Math.min(eye, height - 0.05F);
        // the mob's attachment points too (scaled), so a rider sits where it would on the real mob (a horse's saddle).
        float width = behaviour.fullWidth(type) ? mob.width() * scale : cur.width();
        event.setNewSize(behaviour.size(player, type,
                new net.minecraft.world.entity.EntityDimensions(width, height, eye, mob.scale(scale).attachments(), cur.fixed())));
    }

    /** a right-click while possessed: crouching leaves (unless the puppet uses it); otherwise its move. */
    public static void rightClick(ServerPlayer player, @Nullable Entity target) {
        PuppetType type = possessed(player);
        if (type == null) {
            return;
        }
        PuppetBehaviour behaviour = type.behaviour();
        if (behaviour.stunned(player, type)) {
            return; // no moves, and no stepping out either
        }
        if (player.isShiftKeyDown()) {
            if (!behaviour.crouchUse(player, type)) {
                release(player, null, false);
            }
            return;
        }
        if (!type.move().held()) { // (held moves charge and fire through Holds)
            behaviour.rightClick(player, type, target);
        }
    }

    /** a right-click on thin air with an empty hand only happens client-side; it's relayed here by packet. */
    public static void onRightClickAir(ServerPlayer player) {
        rightClick(player, null);
    }

    /** a left-click swing (the client reports it even at thin air): the puppet's left-click move, if it has one. */
    public static void onSwing(ServerPlayer player) {
        PuppetType type = possessed(player);
        if (type != null) {
            type.behaviour().swing(player, type);
        }
    }

    // --- events --------------------------------------------------------------------------------------

    /**
     * possess (crouch + right-click a possessable mob); a right-click on anything while inside a puppet; and other
     * players milking a cow puppet or shearing a sheep puppet.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player p = event.getEntity();
        if (!isBusy(p) && event.getTarget() instanceof ServerPlayer puppet && possessed(puppet) != null
                && interactWithPuppet(p, event.getHand(), event.getItemStack(), puppet)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (drawsBow(p)) {
            // a skeleton just draws its bow at whatever it's looking at: no interaction, but the item use goes ahead.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        if (isBusy(p)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME); // (no swing: holding right-click would flail every 4 ticks)
            if (p instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
                rightClick(player, event.getTarget());
            }
            return;
        }
        if (!(p instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !EffectManager.isActive(player, Blessings.PUPPETEER)) {
            return;
        }
        Entity clicked = event.getTarget() instanceof net.neoforged.neoforge.entity.PartEntity<?> part ? part.getParent() : event.getTarget();
        if (!(clicked instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        // always say WHY a mob can't be possessed, rather than silently doing nothing (the click still does whatever
        // it normally would).
        String refusal = refusalKey(living);
        if (refusal != null || !(living instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable(refusal != null ? refusal : "witchmod.puppeteer.cant_possess",
                    living.getType().getDescription()).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        long now = player.level().getGameTime();
        long ready = player.getData(WitchModAttachments.PUPPET_COOLDOWN_END);
        if (now < ready) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.cooldown",
                    (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        startPossession(player, mob);
    }

    /** vanilla mobs ruled out for good — bosses, and ones that break the gimmick (see docs/PUPPETEER_MOBS.md). */
    private static final Set<EntityType<?>> DISQUALIFIED = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER,
            EntityType.GIANT, EntityType.ILLUSIONER);

    /** scoreboard tags marking vanilla mobs that are really this mod's characters (guardian angel, solicitor, ...). */
    private static final Set<String> CHARACTER_TAGS = Set.of("witchmod_guardian", "witchmod_solicitor",
            "witchmod_possessed", "witchmod_character");

    /**
     * why {@code living} can't be possessed, as a lang key — or null if it can. separate messages for this mod's own
     * creatures, other mods' creatures, ruled-out vanilla mobs, and vanilla mobs that simply aren't in yet.
     */
    @Nullable
    private static String refusalKey(LivingEntity living) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        if (id.getNamespace().equals(WitchMod.MODID) || living.getTags().stream().anyMatch(CHARACTER_TAGS::contains)) {
            return "witchmod.puppeteer.refuse_own";
        }
        if (!id.getNamespace().equals("minecraft")) {
            return "witchmod.puppeteer.refuse_foreign";
        }
        if (DISQUALIFIED.contains(living.getType())) {
            return "witchmod.puppeteer.refuse_disqualified";
        }
        return PuppetType.of(living.getType()) == null ? "witchmod.puppeteer.cant_possess" : null;
    }

    /** another player uses an item on a puppet (milking, shearing, feeding, riding, trading...). @return true if handled. */
    private static boolean interactWithPuppet(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet) {
        PuppetType type = possessed(puppet);
        return type != null && type.behaviour().interact(user, hand, stack, puppet, type);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isBusy(event.getEntity())) {
            // PASS, not SUCCESS: a consumed interact-at stops the plain interact (onEntityInteract) from ever firing,
            // and that's where a puppet's move gets its target (convert's pig).
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
        }
    }

    /** no doors, chests or buttons from inside a puppet — a right-click on a block is the special move instead. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // a cat puppet sitting on a chest blocks everyone else from opening it — the classic annoying-cat mechanic.
        if (!event.getLevel().isClientSide && !isBusy(event.getEntity())
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && catSittingOnChest(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (drawsBow(event.getEntity())) {
            event.setUseBlock(TriState.FALSE); // no block use, but the bow still draws
            return;
        }
        if (isBusy(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
                rightClick(player, null);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (drawsBow(event.getEntity()) && isPuppetTool(event.getItemStack())) {
            return; // the skeleton's real bow: vanilla draw, vanilla arrow, every archery blessing
        }
        if (isBusy(event.getEntity())) {
            event.setCanceled(true);
            // no swing, no re-equip bob: the client fails it (nothing more to try — the off hand is empty)
            event.setCancellationResult(event.getLevel().isClientSide ? InteractionResult.FAIL : InteractionResult.CONSUME);
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
                rightClick(player, null);
            }
        }
    }

    /** puppets can't dig. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isBusy(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isBusy(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /** a puppet tool never becomes an item in the world (a death's drops, a stray toss): it only exists in a puppet's hands. */
    @SubscribeEvent
    public static void onToolJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                && isPuppetTool(item.getItem())) {
            event.setCanceled(true);
        }
    }

    /**
     * the inventory's backstop, every couple of seconds: out of a puppet, a stash still waiting is handed back (the way a
     * login does), and any puppet tool still about you is cleared away — so a crash or a missed step can't keep your
     * things from you, or leave a witch's potions in your hotbar.
     */
    @SubscribeEvent
    public static void onInventoryCheck(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0 || isBusy(player) || !player.isAlive()) {
            return;
        }
        if (!player.getData(WitchModAttachments.PUPPET_INVENTORY).isEmpty()) {
            release(player, null, false);
            return;
        }
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isPuppetTool(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (isPuppetTool(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** a puppet tool can't be thrown away — it bounces straight back into your hands. */
    @SubscribeEvent
    public static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (isBusy(event.getPlayer()) && isPuppetTool(stack)) {
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
        }
    }

    /** and can't be swapped into the offhand. */
    @SubscribeEvent
    public static void onSwapHands(net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && isBusy(player)) {
            event.setCanceled(true);
        }
    }

    /** nothing goes into the stashed inventory while you're a puppet. */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (isBusy(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** dazed mobs wake up; puppet tridents vanish quickly. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        thawDazed(event.getServer());
        trailGales();
        if (!ECHOES.isEmpty()) {
            tickEchoes(event.getServer().overworld().getGameTime());
        }
        SHOPS.keySet().removeIf(shop -> !(shop.getTradingPlayer() instanceof ServerPlayer customer)
                || !(customer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)); // closed shop windows
        if (!PENDING_FLING.isEmpty()) {
            PENDING_FLING.forEach((hit, up) -> {
                if (hit.isAlive()) {
                    hit.setDeltaMovement(hit.getDeltaMovement().add(0.0, up, 0.0));
                    hit.hurtMarked = true;
                }
            });
            PENDING_FLING.clear();
        }
        if (TRIDENTS.isEmpty()) {
            return;
        }
        int life = Config.PUPPETEER_DROWNED_TRIDENT_LIFE_TICKS.get();
        TRIDENTS.keySet().removeIf(t -> {
            if (t.isRemoved()) {
                return true;
            }
            if (t.tickCount > life) {
                t.discard();
                return true;
            }
            return false;
        });
    }

    /** speaking in chat while possessing something lets out the mob's own call (or nothing, if it has none). */
    @SubscribeEvent
    public static void onChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        PuppetType type = possessed(player);
        if (type != null) {
            player.serverLevel().playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS,
                    1.2F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
    }

    /** a puppet that dies is stepped out of first (its inventory back before anything drops); a puppet's kills count. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, null, false);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            PuppetType type = possessed(killer);
            if (type != null) {
                type.behaviour().killed(killer, type, event.getEntity());
            }
        }
    }

    /** lightning strikes a puppet (a creeper charges). */
    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PuppetType type = possessed(player);
            if (type != null) {
                type.behaviour().struckByLightning(player, type);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, null, false);
            LAST_PUPPET_HIT.remove(player.getUUID());
        }
    }

    /** a crash mid-possession leaves the puppet (and your inventory) saved on you — put both back on login. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setData(WitchModAttachments.PUPPET_BINDING_END, 0L);
            release(player, null, false);
            revokeBatFlight(player); // in case only the flight survived a crash
        }
    }
}
