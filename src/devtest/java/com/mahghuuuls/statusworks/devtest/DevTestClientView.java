package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.client.ClientEntry;
import com.mahghuuuls.statusworks.api.client.StatusworksClient;
import com.mahghuuuls.statusworks.api.client.StatusworksClientView;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the client view the way an addon would and logs what it sees whenever that changes (checked once a
 * second): the player's own definitions, and each nearby entity's entries with counts and times. A card compares
 * these lines with what the server sent; an author-hidden definition must never appear.
 */
final class DevTestClientView {

    private int ticks;
    private String last = "";

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        StatusworksClientView view = StatusworksClient.view();
        if (view == null || mc.world == null) {
            return;
        }
        StringBuilder own = new StringBuilder();
        for (ClientEntry e : view.ownEntries()) {
            own.append(e.definitionId()).append(e.harmful() ? "(harmful)" : "").append(',');
        }
        StringBuilder nearby = new StringBuilder();
        StringBuilder times = new StringBuilder();
        for (Map.Entry<Integer, List<ClientEntry>> e : new TreeMap<>(view.nearbyEntries()).entrySet()) {
            Entity entity = mc.world.getEntityByID(e.getKey());
            nearby.append(entity == null ? "#" + e.getKey() : entity.getName() + "#" + e.getKey()).append('[');
            for (ClientEntry c : e.getValue()) {
                nearby.append(c.definitionId()).append(" count=").append(c.count()).append(" amount=")
                        .append(c.amount()).append(" particles=").append(c.particles())
                        .append(" harmful=").append(c.harmful()).append(';');
                times.append(c.definitionId()).append('=').append(c.remaining() < 0 ? "none"
                        : (c.remaining() + 19) / 20 + "s").append(' ');
            }
            nearby.append("] ");
        }
        // Times change every second; the line is logged only when what is seen changes, with the times then.
        String now = "own=" + own + " nearby=" + nearby;
        if (!now.equals(last)) {
            last = now;
            DevTestMod.LOGGER.info("[stw-devtest] client view {} times={}", now, times);
        }
    }
}
