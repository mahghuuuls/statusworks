package com.mahghuuuls.statusworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws the player's entries on the screen after the vanilla overlay: by default an icon grid at the top left,
 * which keeps clear of the vanilla potion icons at the top right, with a fill bar under a pool's icon, the time
 * under each icon (short form by default), and the stack count on the icon's corner; or, when the player turns
 * names on, a list of rows with names. The corner, offset, scale, maximum, icons per row, and text size come from
 * the client settings; entries over the maximum show as {@code +N}.
 *
 * <p>With the inventory open, the inventory view draws the same layout at full brightness and explains the entry
 * under the pointer, so the overlay skips its own, darkened copy.</p>
 */
public final class HudRenderer {

    /**
     * Where one entry, or the {@code +N} cell, was drawn, in screen coordinates, for pointing at it. The {@code +N}
     * cell has no entry of its own and carries the entries over the maximum instead.
     */
    static final class Hit {
        final int x;
        final int y;
        final int width;
        final int height;
        @Nullable final ClientStore.Shown shown;
        final List<ClientStore.Shown> rest;

        Hit(int x, int y, int width, int height, @Nullable ClientStore.Shown shown, List<ClientStore.Shown> rest) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.shown = shown;
            this.rest = rest;
        }

        boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final ClientStore store;
    private final ClientSettings settings;
    private final EntryRows rows = new EntryRows();

    HudRenderer(ClientStore store, ClientSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    /** Whether the HUD is drawn at all: not with the GUI hidden (F1) or the debug screen (F3) on. */
    static boolean hudVisible(Minecraft mc) {
        return !mc.gameSettings.hideGUI && !mc.gameSettings.showDebugInfo;
    }

    static boolean inventoryOpen(GuiScreen screen) {
        return screen instanceof GuiInventory || screen instanceof GuiContainerCreative;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.player == null
                || !hudVisible(mc) || inventoryOpen(mc.currentScreen)) {
            return;
        }
        draw(mc, event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
    }

    /** Draws the entries for a screen of this size and returns where each one was drawn. */
    List<Hit> draw(Minecraft mc, int screenWidth, int screenHeight) {
        List<Hit> hits = new ArrayList<>();
        if (!settings.hudEnabled) {
            return hits;
        }
        List<ClientStore.Shown> all = store.shown();
        if (all.isEmpty()) {
            return hits;
        }
        int count = HudText.shown(all.size(), settings.maxEntries);
        List<ClientStore.Shown> drawn = all.subList(0, count);
        List<ClientStore.Shown> rest = all.subList(count, all.size());
        String more = HudText.more(all.size(), settings.maxEntries);
        double scale = settings.scale;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);
        EntryRows.prepareState();
        if (settings.showNames) {
            drawList(mc, screenWidth, screenHeight, drawn, rest, more, scale, hits);
        } else {
            drawGrid(mc, screenWidth, screenHeight, drawn, rest, more, scale, hits);
        }
        EntryRows.restoreState();
        GlStateManager.popMatrix();
        return hits;
    }

    private void drawList(Minecraft mc, int screenWidth, int screenHeight, List<ClientStore.Shown> drawn,
                          List<ClientStore.Shown> rest, String more, double scale, List<Hit> hits) {
        int width = Math.max(EntryRows.width(mc, drawn), mc.fontRenderer.getStringWidth(more));
        int height = drawn.size() * EntryRows.ROW_HEIGHT + (more.isEmpty() ? 0 : 10);
        int x = originX(screenWidth, scale, width);
        int y = originY(screenHeight, scale, height);
        for (int i = 0; i < drawn.size(); i++) {
            int rowY = y + i * EntryRows.ROW_HEIGHT;
            rows.draw(mc, drawn.get(i), x, rowY);
            hits.add(hit(x, rowY, width, EntryRows.ROW_HEIGHT, scale, drawn.get(i), Collections.emptyList()));
        }
        if (!more.isEmpty()) {
            int moreY = y + drawn.size() * EntryRows.ROW_HEIGHT;
            mc.fontRenderer.drawStringWithShadow(more, x + EntryRows.TEXT_X, moreY, 0xFFFFFF);
            hits.add(hit(x, moreY, width, 10, scale, null, rest));
        }
    }

    private void drawGrid(Minecraft mc, int screenWidth, int screenHeight, List<ClientStore.Shown> drawn,
                          List<ClientStore.Shown> rest, String more, double scale, List<Hit> hits) {
        double textScale = settings.textScale;
        EntryRows.Style style = new EntryRows.Style(textScale, settings.shortTime);
        int cellWidth = EntryRows.cellWidth(mc, drawn, more, style);
        int cellHeight = EntryRows.cellHeight(drawn, style);
        int cells = drawn.size() + (more.isEmpty() ? 0 : 1);
        int perRow = Math.max(1, settings.iconsPerRow);
        int columns = Math.min(perRow, cells);
        int gridRows = (cells + perRow - 1) / perRow;
        int x0 = originX(screenWidth, scale, columns * cellWidth);
        int y0 = originY(screenHeight, scale, gridRows * cellHeight);
        boolean right = settings.corner.right();
        boolean bottom = settings.corner.bottom();
        for (int i = 0; i < cells; i++) {
            // The first cell sits in the chosen corner; the grid grows away from it.
            int col = i % perRow;
            int row = i / perRow;
            int x = x0 + (right ? (columns - 1 - col) : col) * cellWidth;
            int y = y0 + (bottom ? (gridRows - 1 - row) : row) * cellHeight;
            if (i < drawn.size()) {
                rows.drawCell(mc, drawn.get(i), x, y, cellWidth, style);
                hits.add(hit(x, y, cellWidth, cellHeight, scale, drawn.get(i), Collections.emptyList()));
            } else {
                EntryRows.drawSmall(mc, more, x + cellWidth / 2.0, y + 4, textScale * 1.25, 0xFFFFFF);
                hits.add(hit(x, y, cellWidth, cellHeight, scale, null, rest));
            }
        }
    }

    private static Hit hit(int x, int y, int width, int height, double scale, @Nullable ClientStore.Shown shown,
                           List<ClientStore.Shown> rest) {
        return new Hit((int) (x * scale), (int) (y * scale), (int) Math.ceil(width * scale),
                (int) Math.ceil(height * scale), shown, rest);
    }

    private int originX(int screenWidth, double scale, int width) {
        double area = screenWidth / scale;
        return (int) (settings.corner.right() ? area - settings.offsetX / scale - width : settings.offsetX / scale);
    }

    private int originY(int screenHeight, double scale, int height) {
        double area = screenHeight / scale;
        return (int) (settings.corner.bottom() ? area - settings.offsetY / scale - height : settings.offsetY / scale);
    }
}
