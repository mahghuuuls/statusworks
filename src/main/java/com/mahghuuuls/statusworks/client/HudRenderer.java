package com.mahghuuuls.statusworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * Draws the player's entries on the screen after the vanilla overlay: by default at the top left, which keeps clear
 * of the vanilla potion icons at the top right. The corner, offset, scale, and maximum come from the client
 * settings; entries over the maximum show as {@code +N}.
 */
public final class HudRenderer {

    private final ClientStore store;
    private final ClientSettings settings;
    private final EntryRows rows = new EntryRows();

    HudRenderer(ClientStore store, ClientSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !settings.hudEnabled || mc.player == null
                || mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo) {
            return;
        }
        List<ClientStore.Shown> all = store.shown();
        if (all.isEmpty()) {
            return;
        }
        int count = HudText.shown(all.size(), settings.maxEntries);
        List<ClientStore.Shown> drawn = all.subList(0, count);
        String more = HudText.more(all.size(), settings.maxEntries);
        double scale = settings.scale;
        ScaledResolution res = event.getResolution();
        int width = Math.max(EntryRows.width(mc, drawn), mc.fontRenderer.getStringWidth(more));
        int height = count * EntryRows.ROW_HEIGHT + (more.isEmpty() ? 0 : 10);
        double areaWidth = res.getScaledWidth_double() / scale;
        double areaHeight = res.getScaledHeight_double() / scale;
        int x = (int) (settings.corner.right() ? areaWidth - settings.offsetX / scale - width : settings.offsetX / scale);
        int y = (int) (settings.corner.bottom() ? areaHeight - settings.offsetY / scale - height
                : settings.offsetY / scale);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);
        EntryRows.prepareState();
        for (int i = 0; i < count; i++) {
            rows.draw(mc, drawn.get(i), x, y + i * EntryRows.ROW_HEIGHT);
        }
        if (!more.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow(more, x + EntryRows.TEXT_X, y + count * EntryRows.ROW_HEIGHT,
                    0xFFFFFF);
        }
        EntryRows.restoreState();
        GlStateManager.popMatrix();
    }
}
