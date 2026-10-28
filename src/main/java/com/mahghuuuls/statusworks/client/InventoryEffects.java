package com.mahghuuuls.statusworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * While the player's potion effects are on this HUD and the player keeps vanilla's inventory list hidden, the
 * survival and creative inventories draw no effect list beside them and stay centered, so the HUD is the only place
 * the effects show. Vanilla decides both in {@link InventoryEffectRenderer}: the list is drawn at the end of the
 * screen's draw while its {@code hasActivePotionEffects} field is true, and the panel moves right unless Forge's
 * {@code PotionShiftEvent} is cancelled. The creative inventory sets the field when it opens; the survival inventory
 * sets it again at the start of every draw, so the field is cleared in the foreground layer, which comes after that
 * and before the list.
 */
public final class InventoryEffects {

    /** {@code InventoryEffectRenderer.hasActivePotionEffects}. */
    private static final String SRG_FIELD = "field_147045_u";

    private final ClientSettings settings;
    private final Logger logger;
    @Nullable private Field field;
    private boolean unavailable;

    InventoryEffects(ClientSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    /** Only on the two inventories this HUD draws on, and only while the effects are on this HUD. */
    private boolean hiding(GuiScreen screen) {
        return settings.hideInventoryEffects && HudRenderer.inventoryOpen(screen)
                && HudRenderer.showsPotions(settings, Minecraft.getMinecraft());
    }

    @SubscribeEvent
    public void onShift(GuiScreenEvent.PotionShiftEvent event) {
        if (hiding(event.getGui())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onForeground(GuiContainerEvent.DrawForeground event) {
        if (!hiding(event.getGuiContainer())) {
            return;
        }
        Field f = field();
        if (f == null) {
            return;
        }
        try {
            f.setBoolean(event.getGuiContainer(), false);
        } catch (IllegalAccessException e) {
            unavailable = true;
            field = null;
            logger.warn("Statusworks cannot hide vanilla's inventory effect list; it shows as usual.", e);
        }
    }

    @Nullable
    private Field field() {
        if (field != null || unavailable) {
            return field;
        }
        try {
            field = ObfuscationReflectionHelper.findField(InventoryEffectRenderer.class, SRG_FIELD);
        } catch (RuntimeException e) {
            unavailable = true;
            logger.warn("Statusworks cannot hide vanilla's inventory effect list; it shows as usual.", e);
        }
        return field;
    }
}
