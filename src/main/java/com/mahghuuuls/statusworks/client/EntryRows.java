package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws entries: a grid cell (a 16 by 16 icon, the count on its corner, a pool's bar, the time under it) or a list
 * row (the icon and one line of text). Shared by the HUD and the inventory view. As the player's settings say, a
 * potion effect sits on vanilla's potion frame and a harmful entry has a red border around its icon.
 */
final class EntryRows {

    static final int ROW_HEIGHT = 18;
    static final int TEXT_X = 20;
    private static final ResourceLocation GENERIC = new ResourceLocation("statusworks", "textures/gui/generic.png");
    private static final int ACTIVE = 0xFFFFFF;
    private static final int INACTIVE = 0xAAAAAA;
    /** Time under an icon, in the yellow of other games' buff timers. */
    private static final int TIME = 0xFFD24A;
    /** A pool's bar fill. */
    private static final int BAR = 0xFF3FA9F5;
    /** The border around a harmful entry's icon. */
    private static final int HARMFUL_BORDER = 0xFFD0302A;
    /** The width of the potion frame's outline and rim: two texels, drawn as two GUI pixels. */
    private static final int FRAME_EDGE = 2;

    private final ClientSettings settings;

    /** Icon stacks by item id and metadata. */
    private final Map<String, ItemStack> items = new HashMap<>();

    EntryRows(ClientSettings settings) {
        this.settings = settings;
    }

    /** The name, translated when the author gave a key. */
    static String name(HudDefinition row) {
        return row.nameIsKey ? I18n.format(row.name) : row.name;
    }

    /** The name with a potion effect's level as vanilla writes it ({@code Speed II}); other entries keep the name. */
    static String title(ClientStore.Shown s) {
        String name = name(s.row);
        if (s.potion == null || s.entry.count <= 1) {
            return name;
        }
        String key = "enchantment.level." + s.entry.count;
        return name + " " + (I18n.hasKey(key) ? I18n.format(key) : Integer.toString(s.entry.count));
    }

    static String line(ClientStore.Shown s) {
        StringBuilder text = new StringBuilder(name(s.row));
        for (String part : HudText.fields(s.row, s.entry, s.remaining)) {
            text.append("  ").append(part);
        }
        return text.toString();
    }

    /** The widest row of these entries, icon included. */
    static int width(Minecraft mc, List<ClientStore.Shown> rows) {
        int widest = 0;
        for (ClientStore.Shown s : rows) {
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(line(s)));
        }
        return TEXT_X + widest;
    }

    /** Line height of small text at a text scale. */
    private static int lineHeight(double textScale) {
        return (int) Math.ceil(9 * textScale);
    }

    /** How the grid draws: small-text size and short times. */
    static final class Style {
        final double textScale;
        final boolean shortTime;

        Style(double textScale, boolean shortTime) {
            this.textScale = textScale;
            this.shortTime = shortTime;
        }

        List<String> under(ClientStore.Shown s) {
            return HudText.under(s.row, s.entry, s.remaining, shortTime);
        }
    }

    private static final int BAR_HEIGHT = 3;

    /**
     * The grid's cell width: the icon with a little space. Only a time wider than the icon (a long form or large
     * text) widens the cells, so the small texts never run into each other.
     */
    static int cellWidth(Minecraft mc, List<ClientStore.Shown> cells, String more, Style style) {
        double textScale = style.textScale;
        int widest = 16;
        for (ClientStore.Shown s : cells) {
            for (String line : style.under(s)) {
                widest = Math.max(widest, (int) Math.ceil(mc.fontRenderer.getStringWidth(line) * textScale));
            }
        }
        widest = Math.max(widest, (int) Math.ceil(mc.fontRenderer.getStringWidth(more) * textScale * 1.25));
        return widest + 4;
    }

    /** The grid's cell height: the icon, a pool bar if any cell has one, and the most small lines of a cell. */
    static int cellHeight(List<ClientStore.Shown> cells, Style style) {
        int lines = 0;
        boolean bars = false;
        for (ClientStore.Shown s : cells) {
            lines = Math.max(lines, style.under(s).size());
            bars |= HudText.bar(s.row, s.entry) >= 0;
        }
        return 16 + 1 + (bars ? BAR_HEIGHT + 1 : 0) + lines * lineHeight(style.textScale) + 3;
    }

    /** One grid cell: the icon centred, the count on its lower right corner, a pool's bar, small lines under it. */
    void drawCell(Minecraft mc, ClientStore.Shown s, int x, int y, int cellWidth, Style style) {
        double textScale = style.textScale;
        int iconX = x + (cellWidth - 16) / 2;
        icon(mc, s, iconX, y);
        String corner = HudText.corner(s.row, s.entry);
        if (!corner.isEmpty()) {
            double cornerScale = Math.max(textScale, 0.75);
            int w = (int) Math.ceil(mc.fontRenderer.getStringWidth(corner) * cornerScale);
            drawSmall(mc, corner, iconX + 17 - w / 2.0, y + 16 - 9 * cornerScale + 1, cornerScale, 0xFFFFFF);
        }
        int textY = y + 17;
        double fill = HudText.bar(s.row, s.entry);
        if (fill >= 0) {
            GlStateManager.disableDepth();
            Gui.drawRect(iconX, textY, iconX + 16, textY + BAR_HEIGHT, 0xC0101010);
            int filled = (int) Math.round(16 * fill);
            if (filled > 0) {
                Gui.drawRect(iconX, textY, iconX + filled, textY + BAR_HEIGHT, s.entry.active ? BAR : 0xFF808080);
            }
            GlStateManager.enableDepth();
            textY += BAR_HEIGHT + 1;
        }
        List<String> lines = style.under(s);
        for (int i = 0; i < lines.size(); i++) {
            boolean time = i == lines.size() - 1 && s.row.showTime && s.remaining >= 0;
            int colour = !s.entry.active ? INACTIVE : time ? TIME : ACTIVE;
            drawSmall(mc, lines.get(i), x + cellWidth / 2.0, textY + i * lineHeight(textScale), textScale, colour);
        }
    }

    /** Small text centred on {@code centreX}, drawn over items (depth off, as vanilla stack counts are). */
    static void drawSmall(Minecraft mc, String text, double centreX, double y, double textScale, int colour) {
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.translate(centreX, y, 0);
        GlStateManager.scale(textScale, textScale, 1);
        mc.fontRenderer.drawStringWithShadow(text, -mc.fontRenderer.getStringWidth(text) / 2f, 0, colour);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    void draw(Minecraft mc, ClientStore.Shown s, int x, int y) {
        icon(mc, s, x, y);
        int colour = s.entry.active ? ACTIVE : INACTIVE;
        mc.fontRenderer.drawStringWithShadow(line(s), x + TEXT_X, y + 4, colour);
    }

    /** A list row mirrored to end at {@code right}: the icon at the right end, its text right-aligned before it. */
    void drawFromRight(Minecraft mc, ClientStore.Shown s, int right, int y) {
        icon(mc, s, right - 16, y);
        int colour = s.entry.active ? ACTIVE : INACTIVE;
        String text = line(s);
        mc.fontRenderer.drawStringWithShadow(text, right - TEXT_X - mc.fontRenderer.getStringWidth(text), y + 4,
                colour);
    }

    /** The icon with its frame and border, each as the player's settings say. */
    private void icon(Minecraft mc, ClientStore.Shown s, int x, int y) {
        if (s.potion != null && settings.potionFrame) {
            potionFrame(mc, s.potion, x, y);
        }
        plainIcon(mc, s, x, y);
        if (settings.debuffBorder && HudGroups.harmful(s)) {
            harmfulBorder(x, y);
        }
    }

    private void plainIcon(Minecraft mc, ClientStore.Shown s, int x, int y) {
        if (s.potion != null) {
            potionIcon(mc, s.potion, x, y);
            return;
        }
        HudDefinition row = s.row;
        if (row.iconItem != null) {
            ItemStack stack = items.computeIfAbsent(row.iconItem + "@" + row.iconMeta, k -> {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(row.iconItem));
                return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, row.iconMeta);
            });
            if (!stack.isEmpty()) {
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                RenderHelper.disableStandardItemLighting();
                return;
            }
        }
        ResourceLocation texture = row.iconTexture != null ? new ResourceLocation(row.iconTexture) : GENERIC;
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        mc.getTextureManager().bindTexture(texture);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
        GlStateManager.disableBlend();
    }

    /**
     * A potion effect's icon as vanilla draws it on its HUD (the status icon, then the effect's own
     * {@code renderHUDEffect} for modded effects), without the background box: vanilla's 18 by 18 icon, which sits
     * 3 pixels into its 24 by 24 box, is scaled to fill the 16 by 16 icon place, and a modded effect draws in the
     * same box coordinates as on vanilla's HUD. The icon is drawn at depth 0 with the depth test off, like the small
     * texts: Forge keeps the in-game HUD's depth at -90, and an inventory screen leaves the depth test on, so an icon
     * drawn at the HUD's depth there is hidden behind the screen's dimmed background.
     */
    private static void potionIcon(Minecraft mc, PotionEffect effect, int x, int y) {
        Potion potion = effect.getPotion();
        GlStateManager.disableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(16 / 18.0, 16 / 18.0, 1);
        GlStateManager.translate(-3, -3, 0);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        mc.getTextureManager().bindTexture(GuiContainer.INVENTORY_BACKGROUND);
        if (potion.hasStatusIcon()) {
            int i = potion.getStatusIconIndex();
            Gui.drawModalRectWithCustomSizedTexture(3, 3, i % 8 * 18, 198 + i / 8 * 18, 18, 18, 256, 256);
        }
        potion.renderHUDEffect(effect, mc.ingameGUI, 0, 0, 0, 1);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    /**
     * Vanilla's potion frame, the 24 by 24 box its HUD draws behind each effect (the ambient one for an ambient
     * effect), fitted into 18 by 18 so it sits one pixel around the 16 by 16 icon and inside the cell. Its two-pixel
     * edge (outline and rim) is drawn at full size and only the inside is shrunk, so the edge looks the same on all
     * four sides. Drawn with the depth test off, like the potion icon.
     */
    private static void potionFrame(Minecraft mc, PotionEffect effect, int x, int y) {
        GlStateManager.disableDepth();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        mc.getTextureManager().bindTexture(GuiContainer.INVENTORY_BACKGROUND);
        int u = effect.getIsAmbient() ? 165 : 141;
        int v = 166;
        int[] from = {0, FRAME_EDGE, 24 - FRAME_EDGE};
        int[] fromSize = {FRAME_EDGE, 24 - 2 * FRAME_EDGE, FRAME_EDGE};
        int[] to = {0, FRAME_EDGE, 18 - FRAME_EDGE};
        int[] toSize = {FRAME_EDGE, 18 - 2 * FRAME_EDGE, FRAME_EDGE};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Gui.drawScaledCustomSizeModalRect(x - 1 + to[col], y - 1 + to[row], u + from[col], v + from[row],
                        fromSize[col], fromSize[row], toSize[col], toSize[row], 256, 256);
            }
        }
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
    }

    /** A one-pixel red border just outside the 16 by 16 icon. */
    private static void harmfulBorder(int x, int y) {
        GlStateManager.disableDepth();
        Gui.drawRect(x - 1, y - 1, x + 17, y, HARMFUL_BORDER);
        Gui.drawRect(x - 1, y + 16, x + 17, y + 17, HARMFUL_BORDER);
        Gui.drawRect(x - 1, y, x, y + 16, HARMFUL_BORDER);
        Gui.drawRect(x + 16, y, x + 17, y + 16, HARMFUL_BORDER);
        GlStateManager.enableDepth();
    }

    /** Puts back the state vanilla overlays and screens expect after an item was drawn. */
    static void restoreState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Turns off what a screen may have left on (lighting), so rows draw at full brightness. */
    static void prepareState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
    }
}
