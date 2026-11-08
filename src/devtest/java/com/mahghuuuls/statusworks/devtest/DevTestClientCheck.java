package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.StackChange;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Calls the API once on the client player, which must report failure: tracked state is server-only. Also reads a
 * contribution group on the client player, which must answer unavailable.
 */
final class DevTestClientCheck {

    private boolean done;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        done = true;
        StackChange result = StatusworksApi.statuses().apply(mc.player, DevTestMod.id("shared"), 1);
        DevTestMod.LOGGER.info("[stw-devtest] client-side apply result: {}", result);
        DevTestMod.LOGGER.info("[stw-devtest] client-side group read: {}",
                StatusworksApi.contributions().group(mc.player, DevTestMod.id("grouped")));
    }
}
