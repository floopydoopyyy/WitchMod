package com.oliver.witchmod.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.Modifier;
import com.oliver.witchmod.data.ModifierItems;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * The Compendium's custom book UI: a Chapters sidebar (Curses / Blessings / Items / Blocks) and a two-page
 * spread showing ONE entry per page. Curses/Blessings show name, sacrificial item, power and description
 * (undiscovered ones are RUMOURS behind {@code rumour.png}); Items/Blocks show name, image, a description,
 * durability (if any) and up to TWO crafting/smelting recipes, all pulled LIVE from the recipe manager.
 */
public final class CompendiumScreen extends Screen {
    private static final int PANEL_W = 392;
    private static final int PANEL_H = 240;
    private static final int SIDEBAR_W = 88;
    private static final int SPINE_GAP = 16;
    private static final int PER_SPREAD = 2;
    private static final int MAX_RECIPES = 2;

    private static final ResourceLocation RUMOUR_TEX =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/gui/rumour.png");

    private static final int FRAME = 0xFF241A12;
    private static final int FRAME_HI = 0xFF4A3722;
    private static final int PAGE = 0xFFEFE4C8;
    private static final int PAGE_RUMOUR = 0xFFDBCBA6;
    private static final int PAGE_EDGE = 0xFFD6C39A;
    private static final int SPINE = 0xFF1C130C;
    private static final int SIDEBAR = 0xFF17110B;
    private static final int INK = 0xFF3A2E1C;
    private static final int INK_SOFT = 0xFF7A684A;
    private static final int CURSE = 0xFFB964CE;
    private static final int BLESS = 0xFFE3B23C;
    private static final int ITEMC = 0xFF00AAAA;  // aqua (§3)
    private static final int BLOCKC = 0xFF5555FF;  // blue (§9)
    private static final int MODIFIER_C = 0xFF66C070;  // green — Table modifiers
    private static final int RITUAL_C = 0xFFCC5A55;  // red — Rituals (how-to)
    private static final int MUTED = 0xFF8C7B5C;
    private static final int DIM = 0xC8000000;

    private record RecipeView(boolean smelting, ItemStack[] grid, ItemStack input, ItemStack result) {}

    private static final class Entry {
        ItemStack icon;
        String name;
        int kind;             // 0 curse, 1 bless, 2 item, 3 block, 4 modifier
        boolean discovered;   // effects
        int power;            // effects
        String descKey, rumourKey;
        int durability;       // item/block
        List<RecipeView> recipes = List.of();
        int recipeShown;
        boolean intro;        // a chapter's introductory page: title + paragraphs, no icon/power/recipe
        String titleKey, textKey;

        boolean effect() {
            return kind < 2;
        }
    }

    private record Hover(int x, int y, int w, int h, ItemStack stack) {}

    private record Toggle(int x, int y, int w, int h, Entry entry) {}

    private final List<List<Entry>> chapters = new ArrayList<>();
    private final String[] chapterNames = {"Curses", "Blessings", "Items", "Blocks", "Modifiers", "Rituals"};
    private int chapter;
    private int page;

    private final int[] chapterBtnY = new int[chapterNames.length];
    private final List<Hover> hovers = new ArrayList<>();
    private final List<Toggle> toggles = new ArrayList<>();
    private int leftArrowX, rightArrowX, arrowY;
    private int panelLeft;

    // Per-page-slot scroll state (0 = left page, 1 = right page) so long descriptions can be read in full
    // while the recipe/power stay fixed. Rebuilt each frame by drawScrollingText.
    private static final int LINE_H = 10;
    private final int[] scroll = new int[PER_SPREAD];
    private final int[] maxScroll = new int[PER_SPREAD];
    private final int[] regL = new int[PER_SPREAD];
    private final int[] regR = new int[PER_SPREAD];
    private final int[] regT = new int[PER_SPREAD];
    private final int[] regB = new int[PER_SPREAD];

    public CompendiumScreen() {
        super(Component.translatable("witchmod.compendium.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CompendiumScreen());
    }

    private static int accentOf(int kind) {
        return switch (kind) {
            case 0 -> CURSE;
            case 1 -> BLESS;
            case 2 -> ITEMC;
            case 3 -> BLOCKC;
            case 4 -> MODIFIER_C;
            default -> RITUAL_C;
        };
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        Set<ResourceLocation> discovered = mc.player != null
                ? mc.player.getData(WitchModAttachments.DISCOVERED_EFFECTS) : Set.of();

        List<Entry> curses = new ArrayList<>();
        List<Entry> blessings = new ArrayList<>();
        for (Effect e : WitchModRegistries.EFFECT_REGISTRY) {
            ResourceLocation id = WitchModRegistries.EFFECT_REGISTRY.getKey(e);
            if (id == null || !e.selectable()) {
                continue; // internal attachments (Infectious state) aren't shown as curses
            }
            String path = id.getPath();
            Entry entry = new Entry();
            entry.icon = new ItemStack(e.sacrificialItem());
            entry.name = DiscoveryManager.titleCase(path);
            entry.kind = e.category() == EffectCategory.CURSE ? 0 : 1;
            entry.power = e.powerLevel();
            entry.discovered = discovered.contains(id);
            entry.descKey = "witchmod.compendium." + path + ".desc";
            entry.rumourKey = "witchmod.compendium." + path + ".rumour";
            (entry.kind == 0 ? curses : blessings).add(entry);
        }
        Comparator<Entry> order = Comparator.comparing((Entry e) -> !e.discovered).thenComparing(e -> e.name);
        curses.sort(order);
        blessings.sort(order);

        // Items + Blocks, with recipes (crafting + smelting) pulled live from the recipe manager.
        Map<Item, List<RecipeView>> recipeMap = new HashMap<>();
        HolderLookup.Provider ra = mc.level != null ? mc.level.registryAccess() : null;
        if (mc.level != null && ra != null) {
            var rm = mc.level.getRecipeManager();
            for (RecipeHolder<CraftingRecipe> h : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack res = h.value().getResultItem(ra);
                if (!res.isEmpty()) {
                    addRecipe(recipeMap, res.getItem(), new RecipeView(false, gridOf(h.value()), ItemStack.EMPTY, res));
                }
            }
            for (RecipeHolder<SmeltingRecipe> h : rm.getAllRecipesFor(RecipeType.SMELTING)) {
                ItemStack res = h.value().getResultItem(ra);
                if (!res.isEmpty()) {
                    var ings = h.value().getIngredients();
                    ItemStack in = ings.isEmpty() ? ItemStack.EMPTY : repr(ings.get(0));
                    addRecipe(recipeMap, res.getItem(), new RecipeView(true, null, in, res));
                }
            }
        }
        List<Entry> items = new ArrayList<>();
        List<Entry> blocks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(WitchMod.MODID) || id.getPath().startsWith("example")) {
                continue;
            }
            Entry entry = new Entry();
            entry.icon = new ItemStack(item);
            entry.name = entry.icon.getHoverName().getString();
            entry.kind = item instanceof BlockItem ? 3 : 2;
            entry.durability = entry.icon.getMaxDamage();
            entry.descKey = "witchmod.compendium." + id.getPath() + ".desc";
            entry.recipes = recipeMap.getOrDefault(item, List.of());
            (entry.kind == 3 ? blocks : items).add(entry);
        }
        Comparator<Entry> byName = Comparator.comparing(e -> e.name);
        items.sort(byName);
        blocks.sort(byName);

        // Modifiers — always show the item name + icon; the description stays a rumour until the player has
        // cast a ritual using that modifier (its own discovery track, DISCOVERED_MODIFIERS).
        Set<ResourceLocation> discMods = mc.player != null
                ? mc.player.getData(WitchModAttachments.DISCOVERED_MODIFIERS) : Set.of();
        List<Entry> modifiers = new ArrayList<>();
        for (Modifier m : Modifier.values()) {
            Entry entry = new Entry();
            entry.icon = new ItemStack(ModifierItems.itemFor(m));
            entry.name = entry.icon.getHoverName().getString();
            entry.kind = 4;
            entry.discovered = discMods.contains(ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, m.id()));
            entry.descKey = "witchmod.compendium.mod." + m.id() + ".desc";
            entry.rumourKey = "witchmod.compendium.mod." + m.id() + ".rumour";
            modifiers.add(entry);
        }
        modifiers.sort(order);

        // Each of the entry chapters opens with an introductory page (title + paragraphs) explaining what it is.
        String[] introKeys = {"curses", "blessings", "items", "blocks", "modifiers"};
        List<List<Entry>> raw = List.of(curses, blessings, items, blocks, modifiers);
        chapters.clear();
        for (int c = 0; c < raw.size(); c++) {
            List<Entry> chapter = new ArrayList<>();
            chapter.add(introEntry(c, introKeys[c]));
            chapter.addAll(raw.get(c));
            chapters.add(chapter);
        }

        // Rituals — a how-to chapter: every page is a written explanation of one area of the ritual system,
        // teaching the mod's core. All info pages (title + paragraphs), so no separate intro is prepended.
        String[] ritualTopics = {"overview", "table", "sacrifice", "essence", "targeting",
                "modifiers", "outcome", "counterplay", "discovery", "example"};
        List<Entry> rituals = new ArrayList<>();
        for (String topic : ritualTopics) {
            Entry e = new Entry();
            e.intro = true;
            e.kind = 5;
            e.titleKey = "witchmod.compendium.ritual." + topic + ".title";
            e.textKey = "witchmod.compendium.ritual." + topic + ".text";
            rituals.add(e);
        }
        chapters.add(rituals);
    }

    private static Entry introEntry(int kind, String chapterId) {
        Entry e = new Entry();
        e.intro = true;
        e.kind = kind;
        e.titleKey = "witchmod.compendium.intro." + chapterId + ".title";
        e.textKey = "witchmod.compendium.intro." + chapterId + ".text";
        return e;
    }

    private static void addRecipe(Map<Item, List<RecipeView>> map, Item item, RecipeView view) {
        List<RecipeView> list = map.computeIfAbsent(item, k -> new ArrayList<>());
        if (list.size() < MAX_RECIPES) {
            list.add(view);
        }
    }

    private static ItemStack[] gridOf(CraftingRecipe recipe) {
        ItemStack[] g = new ItemStack[9];
        Arrays.fill(g, ItemStack.EMPTY);
        if (recipe instanceof ShapedRecipe sr) {
            int w = sr.getWidth();
            int h = sr.getHeight();
            var ings = sr.getIngredients();
            for (int r = 0; r < h && r < 3; r++) {
                for (int c = 0; c < w && c < 3; c++) {
                    int ii = r * w + c;
                    if (ii < ings.size()) {
                        g[r * 3 + c] = repr(ings.get(ii));
                    }
                }
            }
        } else {
            var ings = recipe.getIngredients();
            for (int i = 0; i < ings.size() && i < 9; i++) {
                g[i] = repr(ings.get(i));
            }
        }
        return g;
    }

    private static ItemStack repr(Ingredient ing) {
        if (ing == null || ing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = ing.getItems();
        return items.length > 0 ? items[0] : ItemStack.EMPTY;
    }

    private int pageCount() {
        int n = chapters.get(chapter).size();
        return Math.max(1, (n + PER_SPREAD - 1) / PER_SPREAD);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Font font = this.font;
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        panelLeft = left;
        hovers.clear();
        toggles.clear();

        int ox0 = left - 3, oy0 = top - 3, ox1 = left + PANEL_W + 3, oy1 = top + PANEL_H + 3;
        g.fill(0, 0, this.width, oy0, DIM);
        g.fill(0, oy1, this.width, this.height, DIM);
        g.fill(0, oy0, ox0, oy1, DIM);
        g.fill(ox1, oy0, this.width, oy1, DIM);

        g.fill(left - 3, top - 3, left + PANEL_W + 3, top + PANEL_H + 3, FRAME);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, FRAME_HI);

        // Sidebar.
        int sbLeft = left;
        int sbRight = left + SIDEBAR_W;
        g.fill(sbLeft, top, sbRight, top + PANEL_H, SIDEBAR);
        g.drawString(font, Component.literal("CHAPTERS").withStyle(s -> s.withBold(true)), sbLeft + 12, top + 12, 0xFFE9D9B0, false);
        g.fill(sbLeft + 10, top + 24, sbRight - 10, top + 25, 0x40FFFFFF);
        for (int c = 0; c < chapterNames.length; c++) {
            int by = top + 34 + c * 24;
            chapterBtnY[c] = by;
            boolean sel = c == chapter;
            int accent = accentOf(c);
            boolean hover = mouseX >= sbLeft + 8 && mouseX <= sbRight - 8 && mouseY >= by && mouseY <= by + 20;
            g.fill(sbLeft + 8, by, sbRight - 8, by + 20, sel ? (0x55000000 | (accent & 0xFFFFFF)) : (hover ? 0x33FFFFFF : 0x22000000));
            g.fill(sbLeft + 8, by, sbLeft + 11, by + 20, accent);
            g.drawString(font, Component.literal(chapterNames[c]), sbLeft + 16, by + 6, sel ? accent : 0xFFD8CBA8, false);
        }
        g.drawString(font, Component.literal(chapters.get(chapter).size() + " entries"), sbLeft + 12, top + PANEL_H - 18, INK_SOFT, false);

        // Pages.
        int pagesLeft = sbRight + 6;
        int pagesRight = left + PANEL_W - 6;
        int pagesTop = top + 8;
        int pagesBottom = top + PANEL_H - 20;
        int pageW = (pagesRight - pagesLeft - SPINE_GAP) / 2;
        int rightPageX = pagesLeft + pageW + SPINE_GAP;

        List<Entry> list = chapters.get(chapter);
        for (int i = 0; i < PER_SPREAD; i++) {
            int idx = page * PER_SPREAD + i;
            int px = i == 0 ? pagesLeft : rightPageX;
            drawSide(g, font, idx < list.size() ? list.get(idx) : null, i, px, pagesTop, pageW, pagesBottom);
        }
        int spineX = pagesLeft + pageW + SPINE_GAP / 2 - 2;
        g.fill(spineX, pagesTop, spineX + 4, pagesBottom, SPINE);

        // Footer nav.
        arrowY = pagesBottom + 4;
        int centre = (pagesLeft + pagesRight) / 2;
        String pageStr = "Page " + (page + 1) + " / " + pageCount();
        g.drawString(font, Component.literal(pageStr), centre - font.width(pageStr) / 2, arrowY, INK, false);
        leftArrowX = centre - font.width(pageStr) / 2 - 22;
        rightArrowX = centre + font.width(pageStr) / 2 + 12;
        boolean lh = hovering(mouseX, mouseY, leftArrowX, arrowY - 2, 12, 12) && page > 0;
        boolean rh = hovering(mouseX, mouseY, rightArrowX, arrowY - 2, 12, 12) && page < pageCount() - 1;
        g.drawString(font, Component.literal("◄"), leftArrowX, arrowY, page > 0 ? (lh ? 0xFFFFFFFF : INK) : 0x55000000, false);
        g.drawString(font, Component.literal("►"), rightArrowX, arrowY, page < pageCount() - 1 ? (rh ? 0xFFFFFFFF : INK) : 0x55000000, false);

        super.render(g, mouseX, mouseY, partial);

        // Item tooltips (main icon + recipe slots).
        for (Hover h : hovers) {
            if (!h.stack().isEmpty() && hovering(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
                g.renderTooltip(font, h.stack(), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawSide(GuiGraphics g, Font font, Entry e, int slot, int x, int top, int w, int bottom) {
        maxScroll[slot] = 0;
        // A modifier's icon is ALWAYS shown (only its description is a rumour); an effect's icon is hidden
        // behind the rumour glyph until discovered. Intro pages are never rumours.
        boolean rumourGlyph = e != null && !e.intro && e.effect() && !e.discovered;
        boolean rumour = e != null && !e.intro && (e.effect() || e.kind == 4) && !e.discovered;
        g.fill(x - 1, top - 1, x + w + 1, bottom + 1, PAGE_EDGE);
        g.fill(x, top, x + w, bottom, rumour ? PAGE_RUMOUR : PAGE);
        if (e == null) {
            return;
        }
        if (e.intro) {
            drawIntro(g, font, e, slot, x, top, w, bottom);
            return;
        }
        int accent = rumour ? MUTED : accentOf(e.kind);
        int cx = x + w / 2;

        Component name = Component.literal(e.name).withStyle(s -> s.withBold(true));
        g.drawString(font, name, cx - font.width(name) / 2, top + 6, accent, false);
        String cat = category(e, rumour);
        g.drawString(font, Component.literal(cat), cx - font.width(cat) / 2, top + 17, INK_SOFT, false);
        g.fill(x + 10, top + 28, x + w - 10, top + 29, 0x33000000 | (accent & 0xFFFFFF));

        // Big icon (image) — or the rumour glyph for undiscovered attachments.
        int boxX = cx - 18;
        int boxY = top + 36;
        g.fill(boxX - 2, boxY - 2, boxX + 34, boxY + 34, 0x33000000);
        g.fill(boxX - 2, boxY - 2, boxX + 34, boxY - 1, accent);
        if (rumourGlyph) {
            g.blit(RUMOUR_TEX, boxX, boxY, 32, 32, 0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            g.pose().pushPose();
            g.pose().translate(boxX, boxY, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.renderItem(e.icon, 0, 0);
            g.pose().popPose();
            hovers.add(new Hover(boxX, boxY, 32, 32, e.icon));
        }

        if (e.kind == 4) {
            drawModifierBody(g, font, e, slot, rumour, x, w, boxY, bottom);
        } else if (e.effect()) {
            drawEffectBody(g, font, e, rumour, slot, x, w, cx, boxY, bottom);
        } else {
            drawItemBody(g, font, e, slot, x, w, cx, boxY, bottom);
        }
    }

    private String category(Entry e, boolean rumour) {
        return switch (e.kind) {
            case 0 -> (rumour ? "Rumoured " : "") + "Curse";
            case 1 -> (rumour ? "Rumoured " : "") + "Blessing";
            case 2 -> "Item";
            case 3 -> "Block";
            default -> "Modifier";
        };
    }

    /** A chapter's opening page: a centred title, a decorative rule, then scrollable paragraph text. */
    private void drawIntro(GuiGraphics g, Font font, Entry e, int slot, int x, int top, int w, int bottom) {
        int accent = accentOf(e.kind);
        int cx = x + w / 2;

        Component title = Component.translatable(e.titleKey).withStyle(s -> s.withBold(true));
        List<FormattedCharSequence> titleLines = font.split(title, w - 12);
        int ty = top + 12;
        for (FormattedCharSequence tl : titleLines) {
            g.drawString(font, tl, cx - font.width(tl) / 2, ty, accent, false);
            ty += 11;
        }
        g.fill(x + 14, ty + 3, x + w - 14, ty + 4, 0x66000000 | (accent & 0xFFFFFF));

        // Paragraphs: the lang value may contain blank lines (\n\n), which split() renders as spacing.
        List<FormattedCharSequence> lines = font.split(Component.translatable(e.textKey), w - 14);
        drawScrollingText(g, font, slot, lines, x + 6, ty + 12, w - 14, bottom, INK);
    }

    private void drawModifierBody(GuiGraphics g, Font font, Entry e, int slot, boolean rumour, int x, int w, int boxY, int bottom) {
        // Name + item icon are already drawn by drawSide; the description scrolls (a rumour until discovered).
        Component body = Component.translatable(rumour ? e.rumourKey : e.descKey);
        List<FormattedCharSequence> lines = font.split(body, w - 14);
        drawScrollingText(g, font, slot, lines, x + 6, boxY + 44, w - 14, bottom, rumour ? INK_SOFT : INK);
    }

    private void drawEffectBody(GuiGraphics g, Font font, Entry e, boolean rumour, int slot, int x, int w, int cx, int boxY, int bottom) {
        Component cast = rumour
                ? Component.literal("Cast with: ???").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                : Component.literal("Cast with: ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                        .append(e.icon.getHoverName().copy().withStyle(net.minecraft.ChatFormatting.BLACK));
        g.drawString(font, cast, cx - font.width(cast) / 2, boxY + 40, rumour ? INK_SOFT : INK, false);

        int accent = rumour ? MUTED : accentOf(e.kind);
        drawPower(g, font, cx, boxY + 54, e.power, accent);
        g.fill(x + 10, boxY + 78, x + w - 10, boxY + 79, 0x22000000);

        // Description scrolls (power stays fixed above) so long text can be read in full.
        Component body = Component.translatable(rumour ? e.rumourKey : e.descKey);
        List<FormattedCharSequence> lines = font.split(body, w - 14);
        drawScrollingText(g, font, slot, lines, x + 6, boxY + 84, w - 14, bottom, rumour ? INK_SOFT : INK);
    }

    private void drawItemBody(GuiGraphics g, Font font, Entry e, int slot, int x, int w, int cx, int boxY, int bottom) {
        boolean hasRecipe = !e.recipes.isEmpty();

        // Durability sits where an effect's power would.
        String dur = e.durability > 0 ? "Durability: " + e.durability : null;
        if (dur != null) {
            g.drawString(font, Component.literal(dur), cx - font.width(dur) / 2, boxY + 40, INK, false);
        }
        int divY = boxY + (dur != null ? 52 : 44);
        g.fill(x + 10, divY, x + w - 10, divY + 1, 0x22000000);

        // The recipe (or "not craftable" note) is PINNED to the bottom of the page so it's always visible,
        // and the description scrolls in the space between — so long documentation reads all the way through.
        RecipeView rv = hasRecipe ? e.recipes.get(e.recipeShown % e.recipes.size()) : null;
        int footerH = !hasRecipe ? 14 : (rv.smelting() ? 46 : 70);
        int footerTop = bottom - footerH;

        List<FormattedCharSequence> lines = font.split(Component.translatable(e.descKey), w - 14);
        drawScrollingText(g, font, slot, lines, x + 6, divY + 6, w - 14, footerTop - 4, INK);
        g.fill(x + 10, footerTop - 3, x + w - 10, footerTop - 2, 0x18000000);

        if (!hasRecipe) {
            String note = "— not craftable —";
            g.drawString(font, Component.literal(note), cx - font.width(note) / 2, footerTop + 2, INK_SOFT, false);
            return;
        }

        // Recipe header (+ toggle if there are two).
        String header = "Recipe";
        g.drawString(font, Component.literal(header).withStyle(s -> s.withBold(true)), cx - font.width(header) / 2, footerTop, INK, false);
        if (e.recipes.size() > 1) {
            String tog = "‹ " + ((e.recipeShown % e.recipes.size()) + 1) + "/" + e.recipes.size() + " ›";
            int tx = x + w - 10 - font.width(tog);
            g.drawString(font, Component.literal(tog), tx, footerTop, accentOf(e.kind), false);
            toggles.add(new Toggle(tx - 2, footerTop - 2, font.width(tog) + 4, 12, e));
        }

        int gy = footerTop + 14;
        if (rv.smelting()) {
            drawSmelt(g, font, cx, gy, rv);
        } else {
            drawGrid(g, font, cx, gy, rv);
        }
    }

    /**
     * Draws a block of wrapped text clipped to [top, bottom], scrollable by the per-slot offset with a small
     * scrollbar when it overflows. Records the region so {@link #mouseScrolled} can route the wheel to it.
     */
    private void drawScrollingText(GuiGraphics g, Font font, int slot, List<FormattedCharSequence> lines,
                                   int x, int top, int width, int bottom, int col) {
        int viewport = Math.max(0, bottom - top);
        int contentH = lines.size() * LINE_H;
        int ms = Math.max(0, contentH - viewport);
        maxScroll[slot] = ms;
        scroll[slot] = Math.max(0, Math.min(ms, scroll[slot]));
        regL[slot] = x - 6;
        regR[slot] = x + width + 6;
        regT[slot] = top;
        regB[slot] = bottom;

        g.enableScissor(x - 4, top, x + width + 6, bottom);
        for (int i = 0; i < lines.size(); i++) {
            int yy = top - scroll[slot] + i * LINE_H;
            if (yy + LINE_H >= top && yy <= bottom) {
                g.drawString(font, lines.get(i), x, yy, col, false);
            }
        }
        g.disableScissor();

        if (ms > 0 && viewport > 0) {
            int trackX = x + width + 3;
            g.fill(trackX, top, trackX + 2, bottom, 0x22000000);
            int thumbH = Math.max(10, (int) ((long) viewport * viewport / contentH));
            int thumbY = top + (int) ((long) (viewport - thumbH) * scroll[slot] / ms);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAA000000 | (INK & 0xFFFFFF));
        }
    }

    private void drawGrid(GuiGraphics g, Font font, int cx, int gy, RecipeView rv) {
        int slot = 17;
        int gridW = slot * 3;
        int totalW = gridW + 10 + 20;
        int gx = cx - totalW / 2;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int sx = gx + c * slot;
                int sy = gy + r * slot;
                g.fill(sx, sy, sx + 16, sy + 16, 0x30000000);
                ItemStack ing = rv.grid()[r * 3 + c];
                if (!ing.isEmpty()) {
                    g.renderItem(ing, sx, sy);
                    hovers.add(new Hover(sx, sy, 16, 16, ing));
                }
            }
        }
        int arrowX = gx + gridW + 3;
        int midY = gy + slot;
        g.drawString(font, Component.literal("➜"), arrowX, midY + 4, INK, false);
        drawResult(g, arrowX + 12, midY, rv.result());
    }

    private void drawSmelt(GuiGraphics g, Font font, int cx, int gy, RecipeView rv) {
        int totalW = 16 + 26 + 16;
        int gx = cx - totalW / 2;
        int midY = gy + 8;
        g.fill(gx, midY, gx + 16, midY + 16, 0x30000000);
        if (!rv.input().isEmpty()) {
            g.renderItem(rv.input(), gx, midY);
            hovers.add(new Hover(gx, midY, 16, 16, rv.input()));
        }
        g.drawString(font, Component.literal("➜"), gx + 20, midY + 4, INK, false);
        g.drawString(font, Component.literal("smelt").withStyle(s -> s.withItalic(true)), gx + 8, midY + 20, 0xFFC06010, false);
        drawResult(g, gx + 42, midY, rv.result());
    }

    private void drawResult(GuiGraphics g, int rx, int midY, ItemStack result) {
        g.fill(rx, midY, rx + 16, midY + 16, 0x30000000);
        if (!result.isEmpty()) {
            g.renderItem(result, rx, midY);
            g.renderItemDecorations(this.font, result, rx, midY);
            hovers.add(new Hover(rx, midY, 16, 16, result));
        }
    }

    private void drawPower(GuiGraphics g, Font font, int cx, int y, int power, int accent) {
        String label = "Power";
        int totalW = font.width(label + " ") + Effect.POWER_PIPS * 8;
        int startX = cx - totalW / 2;
        g.drawString(font, Component.literal(label), startX, y, INK_SOFT, false);
        int px = startX + font.width(label + " ");
        int filled = Math.round(power / (100.0F / Effect.POWER_PIPS));
        for (int i = 0; i < Effect.POWER_PIPS; i++) {
            int qx = px + i * 8;
            if (i < filled) {
                g.fill(qx, y - 1, qx + 6, y + 6, accent);
            } else {
                g.fill(qx, y - 1, qx + 6, y + 6, 0x33000000);
                g.fill(qx + 1, y, qx + 5, y + 5, PAGE);
            }
        }
        String num = "(" + power + ")";
        g.drawString(font, Component.literal(num), cx - font.width(num) / 2, y + 9, INK_SOFT, false);
    }

    private static boolean hovering(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        for (int s = 0; s < PER_SPREAD; s++) {
            if (maxScroll[s] > 0 && mx >= regL[s] && mx <= regR[s] && my >= regT[s] && my <= regB[s]) {
                int step = (int) Math.signum(dy) * LINE_H * 2;
                scroll[s] = Math.max(0, Math.min(maxScroll[s], scroll[s] - step));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void resetScroll() {
        scroll[0] = 0;
        scroll[1] = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Toggle t : toggles) {
                if (hovering((int) mouseX, (int) mouseY, t.x(), t.y(), t.w(), t.h())) {
                    t.entry().recipeShown = (t.entry().recipeShown + 1) % Math.max(1, t.entry().recipes.size());
                    click();
                    return true;
                }
            }
            int sbLeft = panelLeft;
            int sbRight = panelLeft + SIDEBAR_W;
            for (int c = 0; c < chapterNames.length; c++) {
                if (mouseX >= sbLeft + 8 && mouseX <= sbRight - 8 && mouseY >= chapterBtnY[c] && mouseY <= chapterBtnY[c] + 20) {
                    chapter = c;
                    page = 0;
                    resetScroll();
                    click();
                    return true;
                }
            }
            if (page > 0 && hovering((int) mouseX, (int) mouseY, leftArrowX, arrowY - 2, 14, 14)) {
                page--;
                resetScroll();
                click();
                return true;
            }
            if (page < pageCount() - 1 && hovering((int) mouseX, (int) mouseY, rightArrowX, arrowY - 2, 14, 14)) {
                page++;
                resetScroll();
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263 && page > 0) {
            page--;
            resetScroll();
            return true;
        }
        if (key == 262 && page < pageCount() - 1) {
            page++;
            resetScroll();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
