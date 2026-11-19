package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/**
 * puppeteer: the frog. big jumps, shrugs off falls (vanilla's -5), lured by slimeballs, no bite. right-click: EAT — its
 * tongue lashes out (vanilla's tongue animation) at what you're looking at, and if it's a slime or magma cube (any size —
 * no splitting), or any mob down to puppeteerFrogEatHealth, it's yanked to your mouth and swallowed whole. a magma cube
 * leaves a froglight of your frog's colour, as for a real frog. PUPPET_LUNGE_START is when the tongue went out (synced).
 */
public final class FrogBehaviour implements PuppetBehaviour {
    public static final FrogBehaviour INSTANCE = new FrogBehaviour();

    private FrogBehaviour() {}

    private static final ResourceLocation FROG_JUMP_ID = EffectUtil.modifierId("puppeteer_frog_jump");
    private static final ResourceLocation FROG_FALL_ID = EffectUtil.modifierId("puppeteer_frog_fall");

    /** the tongue's out (a few ticks after an eat): both sides, for the model. */
    public static boolean tongueOut(Player player) {
        if (PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) != PuppetType.FROG) {
            return false;
        }
        long t = player.level().getGameTime() - player.getData(WitchModAttachments.PUPPET_LUNGE_START);
        return t >= 0 && t < 10;
    }

    /**
     * what a frog will swallow: a slime or magma cube (any size); any mob hurt down to puppeteerFrogEatHealth; or a small
     * one (max health up to puppeteerFrogEatMaxHealth — a silverfish, a chicken) even at full health.
     */
    public static boolean edible(@Nullable LivingEntity target) {
        if (!(target instanceof Mob mob) || !mob.isAlive()) {
            return false;
        }
        return mob instanceof Slime || mob.getHealth() <= Config.PUPPETEER_FROG_EAT_HEALTH.get()
                || mob.getMaxHealth() <= Config.PUPPETEER_FROG_EAT_MAX_HEALTH.get();
    }

    /** what the tongue's on: the mob under your aim, a little forgiving (small things are hard to hit dead on). */
    @Nullable
    private static LivingEntity tongueTarget(Player player) {
        return beamHit(player, Config.PUPPETEER_FROG_TONGUE_RANGE.get(), 0.35).entity();
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        EffectUtil.addModifier(player, Attributes.JUMP_STRENGTH, FROG_JUMP_ID, Config.PUPPETEER_FROG_JUMP_BOOST.get(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, FROG_FALL_ID, Config.PUPPETEER_FROG_SAFE_FALL.get(),
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        FarmAnimalBehaviour.lure(player, type); // slimeballs draw it in
    }

    /** right-click: the tongue — what you're looking at, if it's edible, gets yanked in and swallowed. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        LivingEntity prey = tongueTarget(player);
        if (!edible(prey)) {
            return; // nothing to eat: no cooldown spent
        }
        ServerLevel level = player.serverLevel();
        Puppets.cooldown(player, Config.PUPPETEER_FROG_EAT_COOLDOWN_TICKS.get());
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, level.getGameTime()); // the tongue, on the model
        level.playSound(null, player.blockPosition(), SoundEvents.FROG_TONGUE, SoundSource.PLAYERS, 2.0F, 1.0F);
        // yanked to your mouth...
        Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.6)).subtract(0.0, prey.getBbHeight() * 0.5, 0.0);
        prey.teleportTo(mouth.x, mouth.y, mouth.z);
        prey.setDeltaMovement(Vec3.ZERO);
        Item froglight = prey instanceof MagmaCube ? froglightOf(player) : null;
        if (prey instanceof Slime slime) {
            slime.setSize(1, false); // swallowed whole: nothing left to split
        }
        // ...and swallowed
        SPECIAL_DAMAGE.add(player.getUUID());
        try {
            prey.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
        } finally {
            SPECIAL_DAMAGE.remove(player.getUUID());
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FROG_EAT, SoundSource.PLAYERS, 2.0F, 1.0F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)),
                mouth.x, mouth.y + prey.getBbHeight() * 0.5, mouth.z, 10, 0.2, 0.2, 0.2, 0.05);
        if (froglight != null) {
            player.spawnAtLocation(froglight);
        }
        Puppets.countAction(player);
    }

    /** a magma cube becomes the froglight of your frog's colour (temperate: ochre; warm: pearlescent; cold: verdant). */
    private static Item froglightOf(Player player) {
        String variant = player.getData(WitchModAttachments.PUPPET_DATA).getString("variant");
        if (variant.endsWith("warm")) {
            return Items.PEARLESCENT_FROGLIGHT;
        }
        return variant.endsWith("cold") ? Items.VERDANT_FROGLIGHT : Items.OCHRE_FROGLIGHT;
    }

    /** no bite: it eats instead. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.JUMP_STRENGTH, FROG_JUMP_ID);
        EffectUtil.removeModifier(player, Attributes.SAFE_FALL_DISTANCE, FROG_FALL_ID);
    }

    /** right-click: eat (when what you're looking at can be eaten). */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        LivingEntity at = tongueTarget(player);
        out.add(PuppetControls.tap(PuppetControls.Key.USE), edible(at) ? "witchmod.puppeteer.action.frog_eat_now"
                : "witchmod.puppeteer.action.frog_eat", PuppetControls.ready(player));
    }
}
