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
 * Draws the player's entries on the screen after the vanilla overlay: by default an icon grid at the top right, the
 * first entry at the right edge and each next one to its left, with a fill bar under a pool's icon, the time under
 * each icon (short form by default), and the stack count on the icon's corner; or, when the player turns names on, a
 * list of rows with names. The anchor, offset, entry and row directions ({@link HudLayout}), scale, maximum, icons
 * per row, and text size come from the client settings; entries over the maximum show as {@code +N}. With the potion
 * setting on (the default), the player's own potion effects follow the Statusworks entries, and vanilla's potion
 * icons at the top right are not drawn.
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
    private final PotionEntries potions;
    private final EntryRows rows = new EntryRows();

    HudRenderer(ClientStore store, ClientSettings settings) {
        this.store = store;
        this.settings = settings;
        this.potions = new PotionEntries(settings);
    }

    /** With the player's potion effects on this HUD, vanilla's own icons at the top right are not drawn. */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.POTION_ICONS
                && showsPotions(settings, Minecraft.getMinecraft())) {
            event.setCanceled(true);
        }
    }

    /** Whether the player's potion effects are on this HUD now: the HUD and the potion setting on, the HUD visible. */
    static boolean showsPotions(ClientSettings settings, Minecraft mc) {
        return settings.hudEnabled && settings.potionEffects && hudVisible(mc);
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
        all.addAll(potions.shown(mc));
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
        int width = Math.max(EntryRows.width(mc, drawn), EntryRows.TEXT_X + mc.fontRenderer.getStringWidth(more));
        int height = drawn.size() * EntryRows.ROW_HEIGHT + (more.isEmpty() ? 0 : 10);
        int x = originX(screenWidth, scale, width);
        int y = originY(screenHeight, scale, height);
        boolean upward = HudLayout.upward(settings.anchor, settings.rowDirection);
        // At a right-side anchor each row ends at the right edge with its icon outermost, so a text that changes
        // width never moves the icons.
        boolean fromRight = HudLayout.listFromRight(settings.anchor);
        int moreHeight = more.isEmpty() ? 0 : 10;
        for (int i = 0; i < drawn.size(); i++) {
            int rowY = HudLayout.listRowY(y, i, drawn.size(), EntryRows.ROW_HEIGHT, moreHeight, upward);
            if (fromRight) {
                rows.drawFromRight(mc, drawn.get(i), x + width, rowY);
            } else {
                rows.draw(mc, drawn.get(i), x, rowY);
            }
            hits.add(hit(x, rowY, width, EntryRows.ROW_HEIGHT, scale, drawn.get(i), Collections.emptyList()));
        }
        if (!more.isEmpty()) {
            int moreY = HudLayout.listMoreY(y, drawn.size(), EntryRows.ROW_HEIGHT, upward);
            int moreX = fromRight ? x + width - EntryRows.TEXT_X - mc.fontRenderer.getStringWidth(more)
                    : x + EntryRows.TEXT_X;
            mc.fontRenderer.drawStringWithShadow(more, moreX, moreY, 0xFFFFFF);
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
        int columns = HudLayout.gridColumns(cells, perRow);
        int gridRows = HudLayout.gridRows(cells, perRow);
        int x0 = originX(screenWidth, scale, columns * cellWidth);
        int y0 = originY(screenHeight, scale, gridRows * cellHeight);
        boolean rightToLeft = HudLayout.rightToLeft(settings.anchor, settings.entryDirection);
        boolean upward = HudLayout.upward(settings.anchor, settings.rowDirection);
        for (int i = 0; i < cells; i++) {
            // By default the first cell sits at the anchor's side and edge, and the grid grows away from them.
            int x = HudLayout.cellX(x0, i, perRow, columns, cellWidth, rightToLeft);
            int y = HudLayout.cellY(y0, i, perRow, gridRows, cellHeight, upward);
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
        return HudLayout.originX(settings.anchor, screenWidth / scale, settings.offsetX / scale, width);
    }

    private int originY(int screenHeight, double scale, int height) {
        return HudLayout.originY(settings.anchor, screenHeight / scale, settings.offsetY / scale, height);
    }
}
