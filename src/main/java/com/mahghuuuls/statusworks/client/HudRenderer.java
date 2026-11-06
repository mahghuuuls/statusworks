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
 * icons at the top right are not drawn. By default buffs and debuffs are drawn as two groups ({@link HudGroups}),
 * each with its own maximum, the debuffs after a gap: below the buffs at a top or middle anchor, above them at a
 * bottom anchor.
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
    private final EntryRows rows;

    HudRenderer(ClientStore store, ClientSettings settings) {
        this.store = store;
        this.settings = settings;
        this.potions = new PotionEntries(settings);
        this.rows = new EntryRows(settings);
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
        List<HudGroups.Group> groups = HudGroups.groups(all, settings.maxEntries, settings.separateDebuffs);
        if (groups.isEmpty()) {
            return hits;
        }
        double scale = settings.scale;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);
        EntryRows.prepareState();
        if (settings.showNames) {
            drawLists(mc, screenWidth, screenHeight, groups, scale, hits);
        } else {
            drawGrids(mc, screenWidth, screenHeight, groups, scale, hits);
        }
        EntryRows.restoreState();
        GlStateManager.popMatrix();
        return hits;
    }

    /** Each group as a named list, the groups stacked from the anchor's edge with a gap between them. */
    private void drawLists(Minecraft mc, int screenWidth, int screenHeight, List<HudGroups.Group> groups,
                           double scale, List<Hit> hits) {
        int[] widths = new int[groups.size()];
        int[] heights = new int[groups.size()];
        for (int g = 0; g < groups.size(); g++) {
            HudGroups.Group group = groups.get(g);
            widths[g] = Math.max(EntryRows.width(mc, group.drawn),
                    EntryRows.TEXT_X + mc.fontRenderer.getStringWidth(group.more));
            heights[g] = group.drawn.size() * EntryRows.ROW_HEIGHT + (group.more.isEmpty() ? 0 : 10);
        }
        int[][] at = origins(screenWidth, screenHeight, scale, widths, heights);
        for (int g = 0; g < groups.size(); g++) {
            drawList(mc, groups.get(g), at[0][g], at[1][g], widths[g], scale, hits);
        }
    }

    private void drawList(Minecraft mc, HudGroups.Group group, int x, int y, int width, double scale,
                          List<Hit> hits) {
        List<ClientStore.Shown> drawn = group.drawn;
        String more = group.more;
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
            hits.add(hit(x, moreY, width, 10, scale, null, group.rest));
        }
    }

    /** Each group as an icon grid with one cell size for all, the groups stacked like the lists. */
    private void drawGrids(Minecraft mc, int screenWidth, int screenHeight, List<HudGroups.Group> groups,
                           double scale, List<Hit> hits) {
        EntryRows.Style style = new EntryRows.Style(settings.textScale, settings.shortTime);
        int cellWidth = 0;
        int cellHeight = 0;
        for (HudGroups.Group group : groups) {
            cellWidth = Math.max(cellWidth, EntryRows.cellWidth(mc, group.drawn, group.more, style));
            cellHeight = Math.max(cellHeight, EntryRows.cellHeight(group.drawn, style));
        }
        int perRow = Math.max(1, settings.iconsPerRow);
        int[] widths = new int[groups.size()];
        int[] heights = new int[groups.size()];
        for (int g = 0; g < groups.size(); g++) {
            int cells = cells(groups.get(g));
            widths[g] = HudLayout.gridColumns(cells, perRow) * cellWidth;
            heights[g] = HudLayout.gridRows(cells, perRow) * cellHeight;
        }
        int[][] at = origins(screenWidth, screenHeight, scale, widths, heights);
        for (int g = 0; g < groups.size(); g++) {
            drawGrid(mc, groups.get(g), at[0][g], at[1][g], cellWidth, cellHeight, style, scale, hits);
        }
    }

    private static int cells(HudGroups.Group group) {
        return group.drawn.size() + (group.more.isEmpty() ? 0 : 1);
    }

    private void drawGrid(Minecraft mc, HudGroups.Group group, int x0, int y0, int cellWidth, int cellHeight,
                          EntryRows.Style style, double scale, List<Hit> hits) {
        int cells = cells(group);
        int perRow = Math.max(1, settings.iconsPerRow);
        int columns = HudLayout.gridColumns(cells, perRow);
        int gridRows = HudLayout.gridRows(cells, perRow);
        boolean rightToLeft = HudLayout.rightToLeft(settings.anchor, settings.entryDirection);
        boolean upward = HudLayout.upward(settings.anchor, settings.rowDirection);
        for (int i = 0; i < cells; i++) {
            // By default the first cell sits at the anchor's side and edge, and the grid grows away from them.
            int x = HudLayout.cellX(x0, i, perRow, columns, cellWidth, rightToLeft);
            int y = HudLayout.cellY(y0, i, perRow, gridRows, cellHeight, upward);
            if (i < group.drawn.size()) {
                rows.drawCell(mc, group.drawn.get(i), x, y, cellWidth, style);
                hits.add(hit(x, y, cellWidth, cellHeight, scale, group.drawn.get(i), Collections.emptyList()));
            } else {
                EntryRows.drawSmall(mc, group.more, x + cellWidth / 2.0, y + 4, settings.textScale * 1.25, 0xFFFFFF);
                hits.add(hit(x, y, cellWidth, cellHeight, scale, null, group.rest));
            }
        }
    }

    private static Hit hit(int x, int y, int width, int height, double scale, @Nullable ClientStore.Shown shown,
                           List<ClientStore.Shown> rest) {
        return new Hit((int) (x * scale), (int) (y * scale), (int) Math.ceil(width * scale),
                (int) Math.ceil(height * scale), shown, rest);
    }

    private int[][] origins(int screenWidth, int screenHeight, double scale, int[] widths, int[] heights) {
        return HudLayout.groupOrigins(settings.anchor, screenWidth / scale, screenHeight / scale,
                settings.offsetX / scale, settings.offsetY / scale, widths, heights);
    }
}
