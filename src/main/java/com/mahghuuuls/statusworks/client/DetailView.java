package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * With the inventory open (survival or creative), draws the same HUD layout in the same place, at full brightness.
 * Pointing at an entry shows its name, its description, a pool's amount, the full time, what its timer means, a
 * mark's other participant, and for a {@code per_source} status each source's count and time.
 *
 * Pointing at the {@code +N} cell lists the entries over the maximum.
 *
 * <p>The entries are drawn with the screen's foreground layer, before vanilla item tooltips, so a tooltip is never
 * covered; the entry tooltip is drawn last, over everything. When the screen draws no foreground layer (the survival
 * inventory with the recipe book open on a narrow screen), the entries are drawn at the end of the screen
 * instead.</p>
 */
public final class DetailView {

    private final HudRenderer hud;
    /** Where each entry was drawn in this frame, found while drawing the foreground layer. */
    private List<HudRenderer.Hit> hits = Collections.emptyList();
    /** Whether the foreground layer drew the entries in this frame. */
    private boolean drawnInForeground;

    DetailView(HudRenderer hud) {
        this.hud = hud;
    }

    @SubscribeEvent
    public void onForeground(GuiContainerEvent.DrawForeground event) {
        hits = Collections.emptyList();
        GuiContainer gui = event.getGuiContainer();
        Minecraft mc = Minecraft.getMinecraft();
        if (!HudRenderer.inventoryOpen(gui) || !HudRenderer.hudVisible(mc)) {
            return;
        }
        // The foreground layer is drawn relative to the container; move back to screen positions.
        GlStateManager.pushMatrix();
        GlStateManager.translate(-gui.getGuiLeft(), -gui.getGuiTop(), 0);
        hits = hud.draw(mc, gui.width, gui.height);
        GlStateManager.popMatrix();
        drawnInForeground = true;
        // Vanilla draws the stack on the cursor next, expecting the item lighting it set before this event.
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    @SubscribeEvent
    public void onDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        Minecraft mc = Minecraft.getMinecraft();
        List<HudRenderer.Hit> drawn = hits;
        boolean foreground = drawnInForeground;
        hits = Collections.emptyList();
        drawnInForeground = false;
        if (!HudRenderer.inventoryOpen(gui) || !HudRenderer.hudVisible(mc)) {
            return;
        }
        if (!foreground) {
            drawn = hud.draw(mc, gui.width, gui.height);
        }
        for (HudRenderer.Hit h : drawn) {
            if (h.contains(event.getMouseX(), event.getMouseY())) {
                List<String> lines = h.shown != null ? tooltip(h.shown) : names(h.rest);
                GuiUtils.drawHoveringText(lines, event.getMouseX(), event.getMouseY(), gui.width, gui.height, -1,
                        mc.fontRenderer);
                return;
            }
        }
    }

    private static List<String> names(List<ClientStore.Shown> rest) {
        List<String> lines = new ArrayList<>(rest.size());
        for (ClientStore.Shown s : rest) {
            lines.add(EntryRows.name(s.row));
        }
        return lines;
    }

    private static List<String> tooltip(ClientStore.Shown s) {
        HudDefinition row = s.row;
        List<String> lines = new ArrayList<>();
        lines.add(EntryRows.name(row));
        if (row.description != null) {
            lines.add("§7" + (row.descriptionIsKey ? I18n.format(row.description) : row.description));
        }
        if (row.kind == HudDefinition.POOL && row.showAmount) {
            lines.add("§f" + HudText.amount(s.entry.amount, s.entry.capacity, row.decimals));
        }
        if (s.remaining >= 0) {
            lines.add("§e" + HudText.fullTime(s.remaining));
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
            String time = HudText.fullTime(s.groupRemaining.get(i));
            lines.add("§7" + I18n.format("statusworks.detail.group", who, g.count, time));
        }
        return lines;
    }
}
