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

/** Draws entry rows: a 16 by 16 icon and one line of text. Shared by the HUD and the detail view. */
final class EntryRows {

    static final int ROW_HEIGHT = 18;
    static final int TEXT_X = 20;
    private static final ResourceLocation GENERIC = new ResourceLocation("statusworks", "textures/gui/generic.png");
    private static final int ACTIVE = 0xFFFFFF;
    private static final int INACTIVE = 0xAAAAAA;

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
