package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForgeMod;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the horse puppet. */
public final class HorseBehaviour implements PuppetBehaviour {
    public static final HorseBehaviour INSTANCE = new HorseBehaviour();

    private HorseBehaviour() {}

    // --- horse / donkey / mule ----------------------------------------------------------------------

    public static final ResourceLocation HORSE_STEP_ID = EffectUtil.modifierId("puppeteer_horse_step");

    public static final ResourceLocation HORSE_FALL_ID = EffectUtil.modifierId("puppeteer_horse_fall");

    private static final ResourceLocation HORSE_FALL_MULT_ID = EffectUtil.modifierId("puppeteer_horse_fall_mult");

    private static final ResourceLocation HORSE_ARMOR_ID = EffectUtil.modifierId("puppeteer_horse_armor");

    /** horse puppets → how many riders their own client was last told about (see syncRiders). */
    private static final Map<UUID, Integer> RIDERS_SENT = EffectState.sessionConcurrent("puppeteer/horse/riders_sent");

    /** open saddlebag windows → whose saddlebags they are. */
    private static final Map<net.minecraft.world.SimpleContainer, UUID> SADDLEBAGS = new ConcurrentHashMap<>();

    public static double horseJump(PuppetType type) {
        return type == PuppetType.DONKEY || type == PuppetType.MULE ? Config.PUPPETEER_DONKEY_JUMP.get() : Config.PUPPETEER_HORSE_JUMP.get();
    }

    /** horse stats on possessing: steps up a full block, horse-like fall damage, and any armour it's wearing counts. */
    private static void applyHorseStats(ServerPlayer player) {
        EffectUtil.addModifier(player, Attributes.STEP_HEIGHT, HORSE_STEP_ID, 0.4, AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, HORSE_FALL_ID, 3.0, AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, HORSE_FALL_MULT_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyHorseArmor(player);
        if (possessed(player) == PuppetType.SKELETON_HORSE) {
            // a skeleton horse goes underwater like the real one: no slowing down in there (and it never drowns — see onTick)
            EffectUtil.addModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    private static void applyHorseArmor(ServerPlayer player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        ItemStack armor = tag.contains("body_armor_item")
                ? ItemStack.parse(player.registryAccess(), tag.getCompound("body_armor_item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        if (armor.getItem() instanceof net.minecraft.world.item.AnimalArmorItem a) {
            EffectUtil.addModifier(player, Attributes.ARMOR, HORSE_ARMOR_ID, a.getDefense(), AttributeModifier.Operation.ADD_VALUE);
        } else {
            EffectUtil.removeModifier(player, Attributes.ARMOR, HORSE_ARMOR_ID);
        }
    }

    private static void removeHorseStats(ServerPlayer player) {
        EffectUtil.removeModifier(player, Attributes.STEP_HEIGHT, HORSE_STEP_ID);
        EffectUtil.removeModifier(player, Attributes.SAFE_FALL_DISTANCE, HORSE_FALL_ID);
        EffectUtil.removeModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, HORSE_FALL_MULT_ID);
        EffectUtil.removeModifier(player, Attributes.ARMOR, HORSE_ARMOR_ID);
    }

    /**
     * a vanilla quirk: when a PLAYER gains or loses a passenger, its own client isn't told (the passenger packet only
     * goes to players tracking it). so a horse puppet's own client is told here whenever its riders change.
     */
    public static void syncRiders(ServerPlayer player) {
        int riders = player.getPassengers().size();
        Integer sent = RIDERS_SENT.put(player.getUUID(), riders);
        if (sent == null || sent != riders) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
        }
    }

    /**
     * another player right-clicks a horse puppet: a saddle / horse armour / chest in hand is put on it; crouching opens
     * a chested donkey's or mule's saddlebags; otherwise they climb on (no taming, no saddle needed — the puppet steers,
     * they just ride). @return true if handled.
     */
    public static boolean horseInteract(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (hand != InteractionHand.MAIN_HAND || !(user instanceof ServerPlayer rider)) {
            return true;
        }
        ServerLevel level = puppet.serverLevel();
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        if (stack.is(Items.SADDLE) && !tag.contains("SaddleItem")) {
            tag.put("SaddleItem", stack.copyWithCount(1).save(level.registryAccess()));
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            level.playSound(null, puppet.blockPosition(), SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        if (type == PuppetType.HORSE && stack.getItem() instanceof net.minecraft.world.item.AnimalArmorItem a
                && a.getBodyType() == net.minecraft.world.item.AnimalArmorItem.BodyType.EQUESTRIAN && !tag.contains("body_armor_item")) {
            tag.put("body_armor_item", stack.copyWithCount(1).save(level.registryAccess()));
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            applyHorseArmor(puppet);
            level.playSound(null, puppet.blockPosition(), SoundEvents.HORSE_ARMOR, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        boolean chested = tag.getBoolean("ChestedHorse");
        if ((type == PuppetType.DONKEY || type == PuppetType.MULE) && stack.is(Items.CHEST) && !chested) {
            tag.putBoolean("ChestedHorse", true);
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            level.playSound(null, puppet.blockPosition(), SoundEvents.DONKEY_CHEST, SoundSource.PLAYERS, 1.0F,
                    (puppet.getRandom().nextFloat() - puppet.getRandom().nextFloat()) * 0.2F + 1.0F);
            return true;
        }
        if (user.isShiftKeyDown()) {
            if (chested) {
                openSaddlebags(puppet, rider);
            }
            return true;
        }
        int seats = type == PuppetType.CAMEL ? 2 : 1; // a camel takes two riders, like the real one
        if (puppet.getPassengers().size() < seats && !rider.isPassenger() && !isBusy(rider)) {
            rider.startRiding(puppet, true);
            syncRiders(puppet);
        }
        return true;
    }

    /**
     * a chested donkey / mule's saddlebags: its 15 chest slots, straight from the puppet data (saved back on every
     * change), in a two-row chest window (the last three slots are blocked).
     */
    private static void openSaddlebags(ServerPlayer puppet, ServerPlayer viewer) {
        ServerLevel level = puppet.serverLevel();
        net.minecraft.world.SimpleContainer bags = new net.minecraft.world.SimpleContainer(18) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return slot < 15;
            }
        };
        ListTag items = puppet.getData(WitchModAttachments.PUPPET_DATA).getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getByte("Slot") & 255;
            if (slot < 15) {
                bags.setItem(slot, ItemStack.parse(level.registryAccess(), item).orElse(ItemStack.EMPTY));
            }
        }
        bags.addListener(container -> {
            if (!(level.getServer().getPlayerList().getPlayer(puppet.getUUID()) instanceof ServerPlayer owner)
                    || owner.isRemoved() || possessed(owner) == null || possessed(owner).group() != PuppetGroup.HORSE) {
                return;
            }
            ListTag list = new ListTag();
            for (int i = 0; i < 15; i++) {
                ItemStack s = bags.getItem(i);
                if (!s.isEmpty()) {
                    CompoundTag t = new CompoundTag();
                    t.putByte("Slot", (byte) i);
                    list.add(s.save(level.registryAccess(), t));
                }
            }
            CompoundTag tag = owner.getData(WitchModAttachments.PUPPET_DATA);
            tag.put("Items", list);
            owner.setData(WitchModAttachments.PUPPET_DATA, tag);
        });
        SADDLEBAGS.put(bags, puppet.getUUID());
        viewer.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new net.minecraft.world.inventory.ChestMenu(
                net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inventory, bags, 2) {
            @Override
            public void removed(Player closer) {
                super.removed(closer);
                SADDLEBAGS.remove(bags);
            }
        }, Component.translatable("witchmod.puppeteer.saddlebags", puppet.getName())));
        level.playSound(null, puppet.blockPosition(), SoundEvents.DONKEY_CHEST, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** the puppet is going away: close anyone's saddlebag window onto it (whatever they put in is already saved). */
    private static void closeSaddlebags(ServerPlayer puppet) {
        SADDLEBAGS.entrySet().removeIf(e -> {
            if (!e.getValue().equals(puppet.getUUID())) {
                return false;
            }
            for (ServerPlayer viewer : puppet.server.getPlayerList().getPlayers()) {
                if (viewer.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu && menu.getContainer() == e.getKey()) {
                    viewer.closeContainer();
                }
            }
            return true;
        });
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        syncRiders(player);
        if (type == PuppetType.SKELETON_HORSE && player.isUnderWater()) {
            player.setAirSupply(player.getMaxAirSupply()); // bones don't breathe
        }
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        applyHorseStats(player);
    }

    /** riding, saddling, armour, a chest on a donkey, the saddlebags. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        return horseInteract(user, hand, stack, puppet, type);
    }

    /** (the camel's riders too) — everyone hops off, the saddlebags shut, the horse stats come off. */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        closeSaddlebags(player);
        player.ejectPassengers(); // anyone riding a horse puppet hops off
        RIDERS_SENT.remove(player.getUUID());
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(player));
        removeHorseStats(player);
    }

    /** hold jump: the leap (the client draws its jump bar). */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(hold(Key.JUMP), "witchmod.puppeteer.action.leap", 0L);
    }
}
