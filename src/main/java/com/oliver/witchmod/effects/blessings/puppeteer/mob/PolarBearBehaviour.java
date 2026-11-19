package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/**
 * puppeteer: the polar bear. quick on land and a strong swimmer; never freezes (powder snow can't touch it). left-click
 * is its swipe — a weapon-style cooldown, the real bear's rear-up on the model, a little more than vanilla's damage;
 * a cub can't attack at all. right-click: its noise (an adult rears up with the warning growl, a cub calls). an adult
 * near a cub that gets hurt — a real cub or a cub puppet — is told who did it (a trail only it sees) and goes into a
 * frenzy: harder hits, a much faster swipe. real polar bears near a hurt cub puppet go for the attacker too, and real
 * adults hunt a fox puppet, as they do foxes. PUPPET_RAGE_END is the frenzy's end (synced).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PolarBearBehaviour implements PuppetBehaviour {
    public static final PolarBearBehaviour INSTANCE = new PolarBearBehaviour();

    private PolarBearBehaviour() {}

    private static final ResourceLocation BEAR_SWIM_ID = EffectUtil.modifierId("puppeteer_polar_bear_swim");
    private static final ResourceLocation BEAR_FRENZY_ID = EffectUtil.modifierId("puppeteer_polar_bear_frenzy");

    public static boolean frenzied(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.POLAR_BEAR
                && player.level().getGameTime() < player.getData(WitchModAttachments.PUPPET_RAGE_END);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        player.setTicksFrozen(0); // a polar bear never freezes
        boolean frenzy = frenzied(player);
        AttributeInstance swing = player.getAttribute(Attributes.ATTACK_SPEED);
        if (swing != null && frenzy != swing.hasModifier(BEAR_FRENZY_ID)) {
            if (frenzy) {
                EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, BEAR_FRENZY_ID, Config.PUPPETEER_POLAR_BEAR_FRENZY_SWING.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                swing.removeModifier(BEAR_FRENZY_ID);
            }
        }
        Puppets.tickAlert(player);
    }

    /** a strong swimmer; its swipe on a weapon-style cooldown. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        EffectUtil.addModifier(player, NeoForgeMod.SWIM_SPEED, BEAR_SWIM_ID, Config.PUPPETEER_POLAR_BEAR_SWIM_BONUS.get(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, IronGolemBehaviour.GOLEM_ATTACK_SPEED_ID,
                    Config.PUPPETEER_POLAR_BEAR_ATTACK_SPEED.get() - attackSpeed.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
        }
    }

    /** a cub can't attack. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0;
    }

    @Override
    public void meleeSwung(Player player, PuppetType type) {
        IronGolemBehaviour.recordSwing(player);
    }

    /** weapon-style swing scaling; a frenzy hits harder. */
    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        float amount = IronGolemBehaviour.swingScaled(type.attackDamage(), swingStrength);
        return frenzied(player) ? amount * Config.PUPPETEER_POLAR_BEAR_FRENZY_DAMAGE.get().floatValue() : amount;
    }

    /** right-click: an adult rears up with the warning growl; a cub calls. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_POLAR_BEAR_GROWL_COOLDOWN_TICKS.get());
        boolean cub = player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0;
        player.serverLevel().playSound(null, player.blockPosition(), cub ? SoundEvents.POLAR_BEAR_AMBIENT_BABY : SoundEvents.POLAR_BEAR_WARNING,
                SoundSource.PLAYERS, 1.0F, cub ? 1.3F : 1.0F);
        Puppets.countAction(player); // (the model rears up on it — PuppetDummies)
    }

    /** cold can't hurt it. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypes.FREEZE)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, NeoForgeMod.SWIM_SPEED, BEAR_SWIM_ID);
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, BEAR_FRENZY_ID);
    }

    /** a frenzied bear: a note of it. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        if (frenzied(player)) {
            out.note(Component.translatable("witchmod.puppeteer.polar_bear_frenzy").withStyle(ChatFormatting.RED));
        }
    }

    // --- a hurt cub ---

    private static boolean isCub(LivingEntity e) {
        return e instanceof PolarBear bear ? bear.isBaby()
                : e instanceof Player p && PuppetType.byId(p.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.POLAR_BEAR
                        && p.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0;
    }

    /**
     * a cub (a real one, or a cub puppet) was hurt: every adult polar bear puppet near it is told who did it — a trail to
     * them — and goes into a frenzy. a cub PUPPET's real kin answer too (a real cub's do that on their own).
     */
    @SubscribeEvent
    static void onCubHurt(LivingDamageEvent.Post event) {
        LivingEntity cub = event.getEntity();
        if (!(cub.level() instanceof ServerLevel level) || !isCub(cub)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == cub || isCub(attacker)) {
            return;
        }
        double r = Config.PUPPETEER_POLAR_BEAR_CUB_RADIUS.get();
        long until = level.getGameTime() + Config.PUPPETEER_POLAR_BEAR_FRENZY_TICKS.get();
        for (ServerPlayer bear : level.getEntitiesOfClass(ServerPlayer.class, cub.getBoundingBox().inflate(r),
                p -> p != attacker && possessed(p) == PuppetType.POLAR_BEAR && !isCub(p))) {
            boolean fresh = !frenzied(bear);
            bear.setData(WitchModAttachments.PUPPET_RAGE_END, until);
            Puppets.alert(bear, attacker, Config.PUPPETEER_POLAR_BEAR_FRENZY_TICKS.get(), new Vector3f(0.75F, 0.95F, 1.0F));
            if (fresh) {
                bear.displayClientMessage(Component.translatable("witchmod.puppeteer.polar_bear_alert", attacker.getDisplayName())
                        .withStyle(ChatFormatting.RED), true);
                level.playSound(null, bear.blockPosition(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.2F, 0.8F);
            }
        }
        if (cub instanceof Player) {
            for (PolarBear adult : level.getEntitiesOfClass(PolarBear.class, cub.getBoundingBox().inflate(r),
                    b -> !b.isBaby() && b.isAlive() && b != attacker)) {
                adult.setTarget(attacker);
                adult.setPersistentAngerTarget(attacker.getUUID());
                adult.startPersistentAngerTimer();
            }
        }
    }
}
