package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the evoker puppet. */
public final class EvokerBehaviour implements PuppetBehaviour {
    public static final EvokerBehaviour INSTANCE = new EvokerBehaviour();

    private EvokerBehaviour() {}

    // --- evoker -------------------------------------------------------------------------------------

    /**
     * the evoker's right-click spells. pose is vanilla's spell id (1 vex, 2 fangs, 3 wololo) — it drives the dummy's
     * raised arms and spark colour (client, from PUPPET_DASH_POWER). second = on the vex cooldown (ACTION2).
     */
    public enum EvokerSpell {
        RING(2, false), LINE(2, false), VEXES(1, true), WOLOLO(3, false), CONVERT(3, false), VANISH(4, false);

        public final int pose;
        public final boolean second;

        EvokerSpell(int pose, boolean second) {
            this.pose = pose;
            this.second = second;
        }

        public String labelKey() {
            return "witchmod.puppeteer.action.evoker_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** the belt's spells, in scroll order — 0 ring, 1 line, 2 vexes, 3 vanish. */
    private static final EvokerSpell[] BELT = {EvokerSpell.RING, EvokerSpell.LINE, EvokerSpell.VEXES, EvokerSpell.VANISH};

    /** a fully charged spell went off by itself: nothing more until right-click's let go. */
    private static final java.util.Set<UUID> SPENT = EffectState.sessionSet("puppeteer/evoker/spent");

    /** each witch nearby → when it next throws you something (they keep their raid's evoker going). */
    private static final Map<UUID, Long> WITCH_NEXT = EffectState.sessionConcurrent("puppeteer/evoker/witch_next");

    /** the belt spell the selection points at (clamped). */
    public static EvokerSpell beltSpell(Player player) {
        int sel = Mth.clamp(player.getData(WitchModAttachments.PUPPET_SPELL_SELECT), 0, BELT.length - 1);
        return BELT[sel];
    }

    /**
     * what a right-click casts right now (client and server share it, so the HUD matches): at a sheep, wololo; at a
     * villager, convert; otherwise the belt's selected spell. no hold-timing any more — you scroll the belt instead.
     */
    public static EvokerSpell evokerSpell(Player player) {
        LivingEntity target = beamHit(player, 16.0).entity();
        if (target instanceof net.minecraft.world.entity.animal.Sheep) {
            return EvokerSpell.WOLOLO;
        }
        if (target instanceof net.minecraft.world.entity.npc.Villager) {
            return EvokerSpell.CONVERT;
        }
        return beltSpell(player);
    }

    /** after a cast the arms stay up for the rest of vanilla's casting time: the tick that ends. */
    private static final Map<UUID, Long> POSE_UNTIL = EffectState.sessionConcurrent("puppeteer/evoker/pose_until");

    /** an evoker puppet mid-Vanish → when it comes back into sight. */
    private static final Map<UUID, Long> VANISHED = EffectState.sessionConcurrent("puppeteer/evoker/vanished");

    /** each evoker puppet's vexes, and who they're hunting (whoever it last hit or horned). */
    public static final Map<UUID, List<net.minecraft.world.entity.monster.Vex>> VEXES = EffectState.sessionConcurrent("puppeteer/evoker/vexes");

    private static final Map<UUID, LivingEntity> VEX_PREY = EffectState.sessionConcurrent("puppeteer/evoker/vex_prey");

    private static final String VEX_TAG = "witchmod_puppet_vex";

    private static boolean spellReady(ServerPlayer player, EvokerSpell spell) {
        if (spell == EvokerSpell.VANISH) {
            return player.level().getGameTime() >= player.getData(WitchModAttachments.PUPPET_ACTION3_READY);
        }
        return spell.second ? ready2(player) : ready(player);
    }

    /** scroll the spell belt (dir -1 / +1), wrapping; a soft chime + action-bar name so the change reads. */
    public static void cycleSpell(ServerPlayer player, int dir) {
        if (possessed(player) != PuppetType.EVOKER) {
            return;
        }
        int sel = Math.floorMod(player.getData(WitchModAttachments.PUPPET_SPELL_SELECT) + Integer.signum(dir), BELT.length);
        player.setData(WitchModAttachments.PUPPET_SPELL_SELECT, sel);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.evoker_select",
                Component.translatable(BELT[sel].labelKey())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** how long {@code spell} takes to charge — vanilla's warm-up: a second for fangs and vexes, two for a wololo. */
    public static int chargeTicks(EvokerSpell spell) {
        return Math.max(1, switch (spell) {
            case WOLOLO, CONVERT -> Config.PUPPETEER_EVOKER_WINDUP_TICKS.get();
            case VEXES -> Config.PUPPETEER_EVOKER_VEX_CAST_TICKS.get();
            case VANISH -> Config.PUPPETEER_EVOKER_VANISH_CAST_TICKS.get();
            default -> Config.PUPPETEER_EVOKER_CAST_TICKS.get();
        });
    }

    /** still in the arms-up end of a cast: no new charge yet. */
    private static boolean posing(ServerPlayer player) {
        return POSE_UNTIL.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }

    /**
     * let go of right-click: a full charge casts {@link #evokerSpell} — a sheep or villager in your sights gets the wololo
     * / conversion, else the belt's spell. let go early and it fizzles, free. each spell keeps its own cooldown.
     */
    private static void evokerCast(ServerPlayer player, int charge) {
        EvokerSpell spell = evokerSpell(player);
        if (posing(player) || !spellReady(player, spell)) {
            return;
        }
        if (charge < chargeTicks(spell)) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0); // arms down, nothing spent
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int pose = Config.PUPPETEER_EVOKER_POSE_TICKS.get();
        switch (spell) {
            case WOLOLO, CONVERT -> {
                LivingEntity at = lookTarget(player, 16.0);
                if (at instanceof net.minecraft.world.entity.animal.Sheep sheep) {
                    // wololo: the evoker's old joke — blue goes red (and, here, red goes blue)
                    sheep.setColor(sheep.getColor() == DyeColor.RED ? DyeColor.BLUE : DyeColor.RED);
                } else if (at instanceof net.minecraft.world.entity.npc.Villager villager) {
                    convertToWitch(level, villager);
                } else {
                    player.setData(WitchModAttachments.PUPPET_DASH_POWER, 0);
                    return; // lost the target while charging: nothing spent
                }
                cooldown(player, pose);
            }
            case VEXES -> {
                pose = Config.PUPPETEER_EVOKER_SUMMON_POSE_TICKS.get();
                player.setData(WitchModAttachments.PUPPET_ACTION2_READY, now + Config.PUPPETEER_EVOKER_VEX_COOLDOWN_TICKS.get());
                summonVexes(player);
            }
            case VANISH -> vanish(player, now);
            default -> {
                cooldown(player, Config.PUPPETEER_EVOKER_FANGS_COOLDOWN_TICKS.get());
                fangs(player, spell == EvokerSpell.LINE);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
        // the arms stay up (in the spell's colour) for the rest of vanilla's casting time
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, spell.pose);
        POSE_UNTIL.put(player.getUUID(), now + pose);
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /**
     * the raid horn, as a raid sounds it: each listener within range hears it from up to 13 blocks off in the
     * horn's direction, at raid volume — so it carries like the real thing rather than a sound at your feet.
     */
    private static void blowHorn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double range = Config.PUPPETEER_EVOKER_HORN_HEAR_RANGE.get();
        long seed = level.random.nextLong();
        for (ServerPlayer listener : level.players()) {
            double dx = player.getX() - listener.getX();
            double dz = player.getZ() - listener.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range) {
                continue;
            }
            double scale = dist < 13.0 ? 1.0 : 13.0 / dist; // never further than 13 blocks out: a horn in the distance
            listener.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(SoundEvents.RAID_HORN,
                    SoundSource.NEUTRAL, listener.getX() + dx * scale, listener.getY(), listener.getZ() + dz * scale, 64.0F, 1.0F, seed));
        }
    }

    /**
     * evoker left-click: the RALLYING HORN. no damage — every illager (and witch, and ravager: the raiders) in range turns
     * on whatever you're pointing at, with a burst of speed. your vexes go after it too.
     */
    private static void evokerHorn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (player.getData(WitchModAttachments.PUPPET_RAGE_END) > now) {
            return; // PUPPET_RAGE_END doubles as the horn's cooldown
        }
        double range = Config.PUPPETEER_EVOKER_HORN_RANGE.get();
        LivingEntity target = lookTarget(player, range);
        if (target == null) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_EVOKER_HORN_COOLDOWN_TICKS.get());
        blowHorn(player);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 2.0F, 0.8F);
        VEX_PREY.put(player.getUUID(), target);
        int speedTicks = Config.PUPPETEER_EVOKER_HORN_SPEED_TICKS.get();
        int speedLevel = Config.PUPPETEER_EVOKER_HORN_SPEED_LEVEL.get() - 1;
        // mark the quarry for everyone to see: it glows for the whole rally, so it's clear who's being hunted.
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.max(speedTicks, 200), 0));
        player.swing(InteractionHand.MAIN_HAND, true);
        int count = 0;
        for (net.minecraft.world.entity.raid.Raider raider : level.getEntitiesOfClass(net.minecraft.world.entity.raid.Raider.class,
                player.getBoundingBox().inflate(range), r -> r.isAlive() && r != target)) {
            raider.setTarget(target);
            if (speedTicks > 0) {
                raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedTicks, speedLevel));
            }
            // a clear thread of anger from each raider toward the quarry
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, raider.getX(), raider.getEyeY() + 0.6, raider.getZ(), 3, 0.25, 0.2, 0.25, 0.0);
            count++;
        }
        // a thin raid: the horn calls more of it — pillagers (now and then a vindicator)
        if (count < Config.PUPPETEER_EVOKER_HORN_SUMMON_BELOW.get()) {
            int calls = 1 + level.random.nextInt(Math.max(1, Config.PUPPETEER_EVOKER_HORN_SUMMON_MAX.get()));
            for (int i = 0; i < calls; i++) {
                EntityType<?> kind = level.random.nextInt(100) < Config.PUPPETEER_EVOKER_HORN_VINDICATOR_CHANCE.get()
                        ? EntityType.VINDICATOR : EntityType.PILLAGER;
                // all around it, and further out — a war band closing in from every side
                Mob raider = Puppets.summonNear(level, player.position(), kind, false,
                        Config.PUPPETEER_EVOKER_HORN_SUMMON_MIN_DISTANCE.get(), Config.PUPPETEER_EVOKER_HORN_SUMMON_MAX_DISTANCE.get());
                if (raider != null) {
                    raider.setTarget(target);
                    if (speedTicks > 0) {
                        raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedTicks, speedLevel));
                    }
                    level.sendParticles(ParticleTypes.POOF, raider.getX(), raider.getY() + 0.5, raider.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                    count++;
                }
            }
        }
        // a bold burst over the quarry so it reads at a glance
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, target.getX(), target.getEyeY() + 0.7, target.getZ(), 16, 0.5, 0.4, 0.5, 0.0);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY() + 0.5, target.getZ(), 12, 0.5, 0.4, 0.5, 0.3);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.horn", count,
                target.getName()).withStyle(ChatFormatting.RED), true);
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /**
     * Vanish (the illusioner's trick): gone from sight and quick on your feet for a few seconds — but the magic costs:
     * every one of its cooldowns gets puppeteerEvokerVanishPenaltyTicks longer.
     */
    private static void vanish(ServerPlayer player, long now) {
        ServerLevel level = player.serverLevel();
        int ticks = Config.PUPPETEER_EVOKER_VANISH_TICKS.get();
        // you're already hidden — it's the puppet that has to go: the trader's stealth (no model, no name) for the length
        player.setData(WitchModAttachments.PUPPET_STEALTH, true);
        VANISHED.put(player.getUUID(), now + ticks);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true)); // (mobs lose you too)
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, Math.max(0, Config.PUPPETEER_EVOKER_VANISH_SPEED_LEVEL.get() - 1),
                false, false, true));
        player.setData(WitchModAttachments.PUPPET_ACTION3_READY, now + Config.PUPPETEER_EVOKER_VANISH_COOLDOWN_TICKS.get());
        int penalty = Config.PUPPETEER_EVOKER_VANISH_PENALTY_TICKS.get();
        for (var slot : List.of(WitchModAttachments.PUPPET_ACTION_READY, WitchModAttachments.PUPPET_ACTION2_READY,
                WitchModAttachments.PUPPET_ACTION3_READY, WitchModAttachments.PUPPET_RAGE_END)) {
            player.setData(slot, Math.max(now, player.getData(slot)) + penalty);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.02);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.7, 0.4, 0.0);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * the witches of its raid keep their evoker going: each one near throws it a splash of something useful
     * (regeneration, healing, swiftness, fire resistance) every so often — as witches buff their raiders.
     */
    private static void tickWitches(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        double r = Config.PUPPETEER_EVOKER_WITCH_RADIUS.get();
        for (net.minecraft.world.entity.monster.Witch witch : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Witch.class,
                player.getBoundingBox().inflate(r), w -> w.isAlive() && !w.isNoAi() && w.getTarget() != player && w.hasLineOfSight(player))) {
            Long next = WITCH_NEXT.get(witch.getUUID());
            int lo = Config.PUPPETEER_EVOKER_WITCH_MIN_TICKS.get();
            int hi = Math.max(lo, Config.PUPPETEER_EVOKER_WITCH_MAX_TICKS.get());
            if (next == null) {
                WITCH_NEXT.put(witch.getUUID(), now + lo / 2 + level.random.nextInt(hi - lo / 2 + 1));
                continue;
            }
            if (now < next) {
                continue;
            }
            WITCH_NEXT.put(witch.getUUID(), now + lo + level.random.nextInt(hi - lo + 1));
            var potions = List.of(net.minecraft.world.item.alchemy.Potions.REGENERATION, net.minecraft.world.item.alchemy.Potions.HEALING,
                    net.minecraft.world.item.alchemy.Potions.SWIFTNESS, net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE);
            var potion = player.getHealth() < player.getMaxHealth() * 0.5F ? net.minecraft.world.item.alchemy.Potions.HEALING
                    : potions.get(level.random.nextInt(potions.size()));
            net.minecraft.world.entity.projectile.ThrownPotion thrown = new net.minecraft.world.entity.projectile.ThrownPotion(level, witch);
            thrown.setItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.SPLASH_POTION, potion));
            thrown.setXRot(thrown.getXRot() - 20.0F);
            double dx = player.getX() - witch.getX();
            double dy = player.getEyeY() - 1.1 - witch.getEyeY();
            double dz = player.getZ() - witch.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            thrown.shoot(dx, dy + dist * 0.2, dz, 0.75F, 8.0F);
            level.addFreshEntity(thrown);
            witch.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F,
                    0.8F + level.random.nextFloat() * 0.4F);
        }
        WITCH_NEXT.entrySet().removeIf(e -> now - e.getValue() > 6000);
    }

    /** evoker upkeep: the spell charges while right-click is held (arms up in its colour); vexes keep after their prey. */
    private static void tickEvoker(ServerPlayer player, PuppetType type) {
        Long vanished = VANISHED.get(player.getUUID());
        if (vanished != null && player.level().getGameTime() >= vanished) {
            VANISHED.remove(player.getUUID()); // back in sight
            player.setData(WitchModAttachments.PUPPET_STEALTH, false);
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.7, 0.4, 0.02);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
        boolean posing = posing(player);
        if (!posing) {
            Holds.tickHold(player, type);
        }
        tickWitches(player);
        // fully charged: it goes off by itself, at once
        int charged = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (!posing && charged > 0 && charged >= chargeTicks(evokerSpell(player)) && !SPENT.contains(player.getUUID())) {
            player.setData(WitchModAttachments.PUPPET_FUSE, 0);
            SPENT.add(player.getUUID());
            evokerCast(player, charged);
            posing = posing(player);
        }
        boolean charging = !posing && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
        int pose = charging ? evokerSpell(player).pose : posing ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : 0;
        if (player.getData(WitchModAttachments.PUPPET_DASH_POWER) != pose) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, pose);
        }
        Puppets.chargeSlow(player, charging, Config.PUPPETEER_EVOKER_CHARGE_SLOW.get()); // vanilla stands still to cast
        List<net.minecraft.world.entity.monster.Vex> vexes = VEXES.get(player.getUUID());
        if (vexes != null) {
            vexes.removeIf(v -> !v.isAlive());
            LivingEntity prey = VEX_PREY.get(player.getUUID());
            if (prey != null && !prey.isAlive()) {
                VEX_PREY.remove(player.getUUID());
                prey = null;
            }
            for (net.minecraft.world.entity.monster.Vex vex : vexes) {
                if (vex.getTarget() != prey) {
                    vex.setTarget(prey);
                }
            }
        }
    }

    /** a villager turned witch — as lightning does it (the conversion events fire, so other mods can object). */
    private static void convertToWitch(ServerLevel level, net.minecraft.world.entity.npc.Villager villager) {
        if (!net.neoforged.neoforge.event.EventHooks.canLivingConvert(villager, EntityType.WITCH, timer -> {})) {
            return;
        }
        net.minecraft.world.entity.monster.Witch witch = villager.convertTo(EntityType.WITCH, false);
        if (witch == null) {
            return;
        }
        witch.finalizeSpawn(level, level.getCurrentDifficultyAt(witch.blockPosition()), MobSpawnType.CONVERSION, null);
        witch.setPersistenceRequired();
        net.neoforged.neoforge.event.EventHooks.onLivingConvert(villager, witch);
        level.sendParticles(ParticleTypes.WITCH, witch.getX(), witch.getY() + 1.0, witch.getZ(), 20, 0.4, 0.6, 0.4, 0.0);
        level.playSound(null, witch.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** vanilla's fangs: a tap rings you twice over (5 close, 8 further out); a longer hold sends a line of 16 down your aim. */
    public static void fangs(ServerPlayer player, boolean line) {
        double minY = player.getY() - 4.0;
        double maxY = player.getY() + 3.0;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float f = (float) Mth.atan2(Mth.cos(yaw), -Mth.sin(yaw));
        if (!line) {
            for (int i = 0; i < 5; i++) {
                float a = f + i * Mth.PI * 0.4F;
                fang(player, player.getX() + Mth.cos(a) * 1.5, player.getZ() + Mth.sin(a) * 1.5, minY, maxY, a, 0);
            }
            for (int i = 0; i < 8; i++) {
                float a = f + i * Mth.PI * 2.0F / 8.0F + 1.2566371F;
                fang(player, player.getX() + Mth.cos(a) * 2.5, player.getZ() + Mth.sin(a) * 2.5, minY, maxY, a, 3);
            }
        } else {
            for (int i = 0; i < 16; i++) {
                double d = 1.25 * (i + 1);
                fang(player, player.getX() + Mth.cos(f) * d, player.getZ() + Mth.sin(f) * d, minY, maxY, f, i);
            }
        }
    }

    /** one fang, set on the ground between minY and maxY (vanilla's createSpellEntity), credited to you. */
    public static void fang(ServerPlayer player, double x, double z, double minY, double maxY, float yRot, int delay) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double lift = 0.0;
        boolean found = false;
        do {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        lift = shape.max(Direction.Axis.Y);
                    }
                }
                found = true;
                break;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);
        if (found) {
            level.addFreshEntity(new net.minecraft.world.entity.projectile.EvokerFangs(level, x, pos.getY() + lift, z, yRot, delay, player));
        }
    }

    /** summon vexes (vanilla: three, short-lived) — they hunt whoever you last hit, and never you. */
    private static void summonVexes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<net.minecraft.world.entity.monster.Vex> vexes = VEXES.computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        for (int i = 0; i < Config.PUPPETEER_EVOKER_VEX_COUNT.get(); i++) {
            net.minecraft.world.entity.monster.Vex vex = EntityType.VEX.create(level);
            if (vex == null) {
                break;
            }
            BlockPos at = player.blockPosition().offset(-2 + level.random.nextInt(5), 1, -2 + level.random.nextInt(5));
            vex.moveTo(at, 0.0F, 0.0F);
            vex.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
            vex.setBoundOrigin(at);
            vex.setLimitedLife(20 * (30 + level.random.nextInt(90)));
            vex.addTag(VEX_TAG);
            level.addFreshEntity(vex);
            vexes.add(vex);
        }
    }

    /** an evoker puppet's vex only ever targets its master's quarry (see onTarget). */
    public static boolean vexAllowed(Mob mob, LivingEntity target) {
        if (!(mob instanceof net.minecraft.world.entity.monster.Vex vex) || !vex.getTags().contains(VEX_TAG)) {
            return true;
        }
        for (Map.Entry<UUID, List<net.minecraft.world.entity.monster.Vex>> e : VEXES.entrySet()) {
            if (e.getValue().contains(vex)) {
                return target == VEX_PREY.get(e.getKey());
            }
        }
        return false;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickEvoker(player, type);
    }

    /** a spell only charges while it's off cooldown (the one you'd cast right now) — and not again till you let go. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return !SPENT.contains(player.getUUID()) && spellReady(player, evokerSpell(player));
    }

    /** its chant, as vanilla's arms go up. */
    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        var sound = switch (evokerSpell(player)) {
            case VEXES -> SoundEvents.EVOKER_PREPARE_SUMMON;
            case WOLOLO, CONVERT -> SoundEvents.EVOKER_PREPARE_WOLOLO;
            case VANISH -> SoundEvents.ILLUSIONER_PREPARE_MIRROR;
            default -> SoundEvents.EVOKER_PREPARE_ATTACK;
        };
        player.serverLevel().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** let go: an early release fizzles (a full charge has already gone off by itself). */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (SPENT.remove(player.getUUID())) {
            return;
        }
        evokerCast(player, charge);
    }

    /** left-click: the rallying horn. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        evokerHorn(player);
    }

    /** no melee: its spells are its weapon. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** its vexes go after whoever it last hit. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getEntity() != player) {
            VEX_PREY.put(player.getUUID(), event.getEntity());
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        if (VANISHED.remove(player.getUUID()) != null) {
            player.setData(WitchModAttachments.PUPPET_STEALTH, false);
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        SPENT.remove(player.getUUID());
        POSE_UNTIL.remove(player.getUUID());
        VEXES.remove(player.getUUID());
        VEX_PREY.remove(player.getUUID());
    }

    /** the wheel scrolls the spell belt. */
    @Override
    public boolean scrolls(PuppetType type) {
        return true;
    }

    @Override
    public void scroll(ServerPlayer player, PuppetType type, int dir) {
        cycleSpell(player, dir);
    }

    /** left-click: the rallying horn. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    /** the spell belt: scroll picks ring / line / vexes, right-click casts what you're aiming at; left-click the horn. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        EvokerSpell belt = beltSpell(player);
        EvokerSpell cast = evokerSpell(player);
        out.add(List.of(tap(Key.SCROLL)), Component.translatable("witchmod.puppeteer.evoker_select", Component.translatable(belt.labelKey())), 0L);
        // the cast line names a contextual wololo / convert, else "Cast <belt spell>"
        Component castLabel = cast == EvokerSpell.WOLOLO || cast == EvokerSpell.CONVERT
                ? Component.translatable(cast.labelKey())
                : Component.translatable("witchmod.puppeteer.action.evoker_cast", Component.translatable(belt.labelKey()));
        long castReady = cast == EvokerSpell.VANISH ? player.getData(WitchModAttachments.PUPPET_ACTION3_READY)
                : cast.second ? PuppetControls.ready2(player) : PuppetControls.ready(player);
        out.add(List.of(hold(Key.USE)), castLabel, castReady);
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.horn", player.getData(WitchModAttachments.PUPPET_RAGE_END));
    }
}
