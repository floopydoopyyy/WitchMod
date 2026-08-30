package com.oliver.witchmod.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.WitchMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.blocks.BewitchingTableBlockEntity;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.ModifierCalculator;
import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.SacrificialItems;

/**
 * The Bewitching Table screen (CLAUDE.md section 2.1/9). Deliberately styled to match vanilla's plain
 * light-grey container look (no bespoke texture yet — a drop-in PNG upgrade later), with:
 * <ul>
 *   <li>a working <b>success bar</b> that fills with the computed chance of the cast landing (read live from
 *       the slots via the same pure {@link ModifierCalculator} functions the server casts with);</li>
 *   <li><b>red-highlighted slots</b> for any wrong item (slots now accept anything so the player gets a
 *       reason rather than a silent bounce), and a <b>disabled Cast button</b> while the ritual isn't valid;</li>
 *   <li>a shimmer on the bar and ambient particles drifting up off the real block while a cast is ready.</li>
 * </ul>
 */
public final class BewitchingTableScreen extends AbstractContainerScreen<BewitchingTableMenu> {
    /** The editable background art (panel + rune-lines + ritual circle). Slot sprites + dynamic bits draw on top. */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/container/bewitching_table.png");
    // Each slot outline is its OWN 18x18 texture (like the enchant table's lapis slot), blitted per slot.
    private static ResourceLocation slotTex(String name) {
        return ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/container/" + name + ".png");
    }
    private static final ResourceLocation SLOT_PLAIN = slotTex("slot");
    private static final ResourceLocation SLOT_TARGET = slotTex("slot_target");
    private static final ResourceLocation SLOT_SACRIFICE = slotTex("slot_sacrifice");
    private static final ResourceLocation SLOT_ESSENCE = slotTex("slot_essence");
    private static final ResourceLocation SLOT_MODIFIER = slotTex("slot_modifier");

    // Palette for the procedurally-drawn dynamic elements.
    private static final int SLOT_EDGE_DARK = 0xFF373737;
    private static final int TEXT_DARK = 0x404040;
    private static final int BAR_TRACK = 0xFF6E6E6E;
    private static final int BAR_SUCCESS = 0xFF55B845;
    private static final int BAR_SUCCESS_HI = 0xFF8CE060;
    private static final int BAR_BACKFIRE = 0xFFC0392B;
    private static final int SLOT_BAD_FILL = 0x66FF2B2B;
    private static final int SLOT_BAD_EDGE = 0xFFFF4040;

    private static final int BAR_X = 18;
    private static final int BAR_Y = 97;
    private static final int BAR_WIDTH = 140;
    private static final int BAR_HEIGHT = 9;
    private static final int CAST_BUTTON_X = 58;
    private static final int CAST_BUTTON_Y = 111;
    private static final int CAST_BUTTON_WIDTH = 60;
    private static final int CAST_BUTTON_HEIGHT = 16;

    private int clientTick;

    public BewitchingTableScreen(BewitchingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 228;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 134;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        clientTick++;
        // Ambient particles drift up off the real block while a valid cast is ready — the block reacts to
        // being "loaded" without waiting for the Cast button.
        RitualSnapshot snapshot = readSnapshot();
        if (isCastable() && snapshot.hasEffect && this.minecraft != null && this.minecraft.level != null && clientTick % 6 == 0) {
            BlockPos pos = this.menu.pos();
            var level = this.minecraft.level;
            // Neutral "ready" shimmer (purple witch motes) — deliberately NOT the green success particle, so
            // the ready cue can't be mistaken for a completed cast.
            ParticleOptions particle = ParticleTypes.WITCH;
            var rng = level.random;
            for (int i = 0; i < 2; i++) {
                double px = pos.getX() + 0.25 + rng.nextDouble() * 0.5;
                double py = pos.getY() + 1.0 + rng.nextDouble() * 0.25;
                double pz = pos.getZ() + 0.25 + rng.nextDouble() * 0.5;
                level.addParticle(particle, px, py, pz, 0, 0.03, 0);
                if (i == 0) {
                    level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
                            (rng.nextDouble() - 0.5) * 0.6, -0.4, (rng.nextDouble() - 0.5) * 0.6);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        if (this.hoveredSlot instanceof RitualSlot ritualSlot && !ritualSlot.hasItem()) {
            guiGraphics.renderTooltip(this.font, Component.literal(labelFor(ritualSlot.kind())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_DARK, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT_DARK, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // The static panel art (background + rune-lines + circle) comes from the editable PNG.
        guiGraphics.blit(TEXTURE, x, y, imageWidth, imageHeight, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        // Each slot outline is its own 18x18 sprite blitted at the slot: ritual slots get their symboled
        // texture, the player inventory gets the plain one.
        for (Slot slot : this.menu.slots) {
            ResourceLocation tex = slot instanceof RitualSlot ritual ? slotTexFor(ritual.kind()) : SLOT_PLAIN;
            guiGraphics.blit(tex, x + slot.x - 1, y + slot.y - 1, 18, 18, 0.0F, 0.0F, 18, 18, 18, 18);
            if (slot instanceof RitualSlot ritual && !ritual.isCorrect(ritual.getItem())) {
                markSlotBad(guiGraphics, x + slot.x, y + slot.y);
            }
        }

        renderProbabilityBar(guiGraphics, x, y);
        renderCastButton(guiGraphics, x, y, mouseX, mouseY);
    }

    private static ResourceLocation slotTexFor(RitualSlot.Kind kind) {
        return switch (kind) {
            case PLAYER_ESSENCE -> SLOT_TARGET;
            case SACRIFICIAL_ITEM -> SLOT_SACRIFICE;
            case CURSED_ESSENCE -> SLOT_ESSENCE;
            case MODIFIER -> SLOT_MODIFIER;
        };
    }

    /** A red wash + border over a slot that holds the wrong item. */
    private void markSlotBad(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 16, sy + 16, SLOT_BAD_FILL);
        g.fill(sx - 1, sy - 1, sx + 17, sy, SLOT_BAD_EDGE);
        g.fill(sx - 1, sy + 16, sx + 17, sy + 17, SLOT_BAD_EDGE);
        g.fill(sx - 1, sy - 1, sx, sy + 17, SLOT_BAD_EDGE);
        g.fill(sx + 16, sy - 1, sx + 17, sy + 17, SLOT_BAD_EDGE);
    }

    private void renderProbabilityBar(GuiGraphics guiGraphics, int x, int y) {
        RitualSnapshot snapshot = readSnapshot();

        String label;
        if (snapshot.random) {
            label = "Random attachment — success varies";
        } else if (snapshot.hasEffect) {
            label = String.format("Success: %d%%    (Backfire %d%%)",
                    Math.round(snapshot.successChance * 100), Math.round(snapshot.backfireChance * 100));
        } else {
            label = "Insert a Sacrificial Item";
        }
        int labelWidth = this.font.width(label);
        guiGraphics.drawString(this.font, label, x + imageWidth / 2 - labelWidth / 2, y + BAR_Y - 12, TEXT_DARK, false);

        int bx = x + BAR_X;
        int by = y + BAR_Y;
        // Inset track.
        guiGraphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, SLOT_EDGE_DARK);
        guiGraphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, BAR_TRACK);

        if (snapshot.hasEffect && !snapshot.random) {
            int successWidth = Math.round(BAR_WIDTH * snapshot.successChance);
            guiGraphics.fill(bx, by, bx + successWidth, by + BAR_HEIGHT, BAR_SUCCESS);
            // A moving shimmer highlight sliding across the filled portion.
            if (successWidth > 4) {
                int shimmer = (int) ((clientTick * 2) % (successWidth + 20)) - 10;
                int sxa = Math.max(0, shimmer);
                int sxb = Math.min(successWidth, shimmer + 6);
                if (sxb > sxa) {
                    guiGraphics.fill(bx + sxa, by, bx + sxb, by + 2, BAR_SUCCESS_HI);
                }
            }
            // Backfire marked as a thin red cap on the right end.
            int backfireWidth = Math.round(BAR_WIDTH * snapshot.backfireChance);
            if (backfireWidth > 0) {
                guiGraphics.fill(bx + BAR_WIDTH - backfireWidth, by, bx + BAR_WIDTH, by + BAR_HEIGHT, BAR_BACKFIRE);
            }
        }
    }

    private void renderCastButton(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        boolean castable = isCastable();
        boolean hovered = castable && isWithinCastButton(x, y, mouseX, mouseY);
        int bx = x + CAST_BUTTON_X;
        int by = y + CAST_BUTTON_Y;

        int fill = !castable ? 0xFF565656 : (hovered ? 0xFF8A8A8A : 0xFF737373);
        guiGraphics.fill(bx, by, bx + CAST_BUTTON_WIDTH, by + CAST_BUTTON_HEIGHT, fill);
        // Bevel (flattened when disabled).
        int light = castable ? 0xFFB0B0B0 : 0xFF6A6A6A;
        int dark = castable ? 0xFF3A3A3A : 0xFF454545;
        guiGraphics.fill(bx, by, bx + CAST_BUTTON_WIDTH, by + 1, light);
        guiGraphics.fill(bx, by, bx + 1, by + CAST_BUTTON_HEIGHT, light);
        guiGraphics.fill(bx, by + CAST_BUTTON_HEIGHT - 1, bx + CAST_BUTTON_WIDTH, by + CAST_BUTTON_HEIGHT, dark);
        guiGraphics.fill(bx + CAST_BUTTON_WIDTH - 1, by, bx + CAST_BUTTON_WIDTH, by + CAST_BUTTON_HEIGHT, dark);

        int textColor = castable ? 0xFFFFFFFF : 0xFF9A9A9A;
        guiGraphics.drawCenteredString(this.font, "Cast", bx + CAST_BUTTON_WIDTH / 2, by + 4, textColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isWithinCastButton(this.leftPos, this.topPos, (int) mouseX, (int) mouseY)) {
            if (isCastable()) {
                // Trigger via our own C2S payload rather than the vanilla container-button plumbing — the
                // server runs the ritual directly against the block entity at this position.
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.oliver.witchmod.network.WitchModNetwork.RitualCastPayload(this.menu.pos()));
            }
            return true; // swallow the click either way so it doesn't fall through to slots behind the button
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isWithinCastButton(int panelX, int panelY, int mouseX, int mouseY) {
        int left = panelX + CAST_BUTTON_X;
        int top = panelY + CAST_BUTTON_Y;
        return mouseX >= left && mouseX < left + CAST_BUTTON_WIDTH && mouseY >= top && mouseY < top + CAST_BUTTON_HEIGHT;
    }

    /** A cast is allowed only when every ritual slot holds a correct item AND there's a Sacrificial Item to cast. */
    private boolean isCastable() {
        boolean hasSacrificial = false;
        for (Slot slot : this.menu.slots) {
            if (slot instanceof RitualSlot ritual) {
                ItemStack stack = ritual.getItem();
                if (!ritual.isCorrect(stack)) {
                    return false;
                }
                if (ritual.kind() == RitualSlot.Kind.SACRIFICIAL_ITEM && !stack.isEmpty()) {
                    hasSacrificial = true;
                }
            }
        }
        return hasSacrificial;
    }

    private RitualSnapshot readSnapshot() {
        ItemStack sacrificialStack = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_SACRIFICIAL_ITEM).getItem();
        if (sacrificialStack.is(Items.REDSTONE)) {
            return RitualSnapshot.RANDOM;
        }
        // A coin uses a fixed base cost and fizzles (no backfire) on failure.
        com.oliver.witchmod.data.CoinGamble.Type coinType = com.oliver.witchmod.data.CoinGamble.typeOf(sacrificialStack.getItem());
        if (coinType != null) {
            ItemStack coinModStack = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_MODIFIER).getItem();
            Modifier coinMod = coinModStack.isEmpty() ? null : ModifierItems.findModifier(coinModStack.getItem()).orElse(null);
            int coinEssence = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE).getItem().getCount();
            int coinCost = ModifierCalculator.applyCost(com.oliver.witchmod.data.CoinGamble.BASE_COST, coinMod);
            float coinSuccess = ModifierCalculator.applySuccessChance(
                    ModifierCalculator.baseSuccessChance(coinEssence, coinCost), coinMod);
            return new RitualSnapshot(true, false, coinType == com.oliver.witchmod.data.CoinGamble.Type.BLESSED, coinSuccess, 0F);
        }
        var maybeEffect = SacrificialItems.findEffect(sacrificialStack.getItem());
        if (maybeEffect.isEmpty()) {
            return RitualSnapshot.NONE;
        }
        Holder.Reference<Effect> effect = maybeEffect.get();

        ItemStack modifierStack = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_MODIFIER).getItem();
        Modifier modifier = modifierStack.isEmpty() ? null
                : ModifierItems.findModifier(modifierStack.getItem()).orElse(null);

        int essenceSpent = this.menu.slots.get(BewitchingTableBlockEntity.SLOT_CURSED_ESSENCE).getItem().getCount();
        int adjustedCost = ModifierCalculator.applyCost(effect.value().baseCost(), modifier);
        float successChance = ModifierCalculator.applySuccessChance(
                ModifierCalculator.baseSuccessChance(essenceSpent, adjustedCost), modifier);
        float backfireChance = ModifierCalculator.applyBackfireChance(
                ModifierCalculator.baseBackfireChance(essenceSpent, adjustedCost), modifier);
        backfireChance = ModifierCalculator.applyTierBackfire(backfireChance, effect.value().baseCost());
        boolean blessing = effect.value().category() == EffectCategory.BLESSING;
        return new RitualSnapshot(true, false, blessing, successChance, backfireChance);
    }

    private static String labelFor(RitualSlot.Kind kind) {
        return switch (kind) {
            case PLAYER_ESSENCE -> "Player Essence / Jar (target - empty = self)";
            case CURSED_ESSENCE -> "Cursed Essence (stack size = essence spent)";
            case SACRIFICIAL_ITEM -> "Sacrificial Item (selects the effect)";
            case MODIFIER -> "Modifier (optional)";
        };
    }

    private record RitualSnapshot(boolean hasEffect, boolean random, boolean blessing, float successChance, float backfireChance) {
        static final RitualSnapshot NONE = new RitualSnapshot(false, false, false, 0F, 0F);
        static final RitualSnapshot RANDOM = new RitualSnapshot(true, true, false, 0F, 0F);
    }
}
