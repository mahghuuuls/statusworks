package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws entries: a grid cell (a 16 by 16 icon, the count on its corner, a pool's bar, the time under it) or a list
 * row (the icon and one line of text). Shared by the HUD and the inventory view.
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

    /** Icon stacks by item id and metadata. */
    private final Map<String, ItemStack> items = new HashMap<>();

    /** The name, translated when the author gave a key. */
    static String name(HudDefinition row) {
        return row.nameIsKey ? I18n.format(row.name) : row.name;
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
        icon(mc, s.row, iconX, y);
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
        icon(mc, s.row, x, y);
        int colour = s.entry.active ? ACTIVE : INACTIVE;
        mc.fontRenderer.drawStringWithShadow(line(s), x + TEXT_X, y + 4, colour);
    }

    private void icon(Minecraft mc, HudDefinition row, int x, int y) {
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
