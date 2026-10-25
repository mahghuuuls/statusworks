package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * With the inventory open (survival or creative), draws every entry beside it, to its right, so the vanilla potion
 * list on the left stays clear; on a narrow screen the column moves in to stay on the screen. Pointing at an entry
 * shows its name, its description, what its timer means, and for a {@code per_source} status each source's count
 * and time.
 *
 * <p>The rows are drawn with the screen's foreground layer, before vanilla item tooltips, so a tooltip is never
 * covered; the entry tooltip is drawn last, over everything.</p>
 */
public final class DetailView {

    private final ClientStore store;
    private final ClientSettings settings;
    private final EntryRows rows = new EntryRows();
    /** The entry under the pointer in this frame, found while drawing the rows. */
    @Nullable private ClientStore.Shown pointed;

    DetailView(ClientStore store, ClientSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    private static boolean applies(GuiScreen gui) {
        return gui instanceof GuiInventory || gui instanceof GuiContainerCreative;
    }

    @SubscribeEvent
    public void onForeground(GuiContainerEvent.DrawForeground event) {
        pointed = null;
        GuiContainer gui = event.getGuiContainer();
        if (!settings.hudEnabled || !applies(gui)) {
            return;
        }
        List<ClientStore.Shown> all = store.shown();
        if (all.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int rowsFit = Math.max(1, (gui.height - gui.getGuiTop()) / EntryRows.ROW_HEIGHT);
        List<ClientStore.Shown> drawn = all.subList(0, Math.min(all.size(), rowsFit));
        int width = EntryRows.width(mc, drawn);
        // Screen position of the column, then drawn relative to the container, which the foreground layer is.
        int x = Math.max(0, Math.min(gui.getGuiLeft() + gui.getXSize() + 4, gui.width - width - 2));
        int y = gui.getGuiTop();
        int relX = x - gui.getGuiLeft();
        EntryRows.prepareState();
        for (int i = 0; i < drawn.size(); i++) {
            int rowY = y + i * EntryRows.ROW_HEIGHT;
            rows.draw(mc, drawn.get(i), relX, rowY - gui.getGuiTop());
            if (event.getMouseX() >= x && event.getMouseX() < x + width && event.getMouseY() >= rowY
                    && event.getMouseY() < rowY + EntryRows.ROW_HEIGHT) {
                pointed = drawn.get(i);
            }
        }
        // Vanilla draws the stack on the cursor next, expecting the item lighting it set before this event.
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    @SubscribeEvent
    public void onDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (pointed == null || !applies(gui)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GuiUtils.drawHoveringText(tooltip(pointed), event.getMouseX(), event.getMouseY(), gui.width, gui.height, -1,
                mc.fontRenderer);
        pointed = null;
    }

    private static List<String> tooltip(ClientStore.Shown s) {
        HudDefinition row = s.row;
        List<String> lines = new ArrayList<>();
        lines.add(EntryRows.name(row));
        if (row.description != null) {
            lines.add("§7" + (row.descriptionIsKey ? I18n.format(row.description) : row.description));
        }
        lines.add("§7" + I18n.format("statusworks.timer." + row.timerMode));
        if (row.kind == HudDefinition.MARK && s.entry.other != null) {
            lines.add("§7" + I18n.format(s.entry.side == HudEntry.MARK_SOURCE ? "statusworks.detail.target"
                    : "statusworks.detail.source", s.entry.other));
            if (!s.entry.active) {
                lines.add("§7" + I18n.format("statusworks.detail.inactive"));
            }
        }
        for (int i = 0; i < s.entry.groups.size(); i++) {
            HudEntry.Group g = s.entry.groups.get(i);
            String who = g.source.isEmpty() ? I18n.format("statusworks.detail.nosource") : g.source;
            String time = HudText.time(s.groupRemaining.get(i));
            lines.add("§7" + I18n.format("statusworks.detail.group", who, g.count, time));
        }
        return lines;
    }
}
