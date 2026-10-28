package com.mahghuuuls.statusworks.client;

import com.google.common.collect.Ordering;
import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The player's own potion effects as HUD entries, when the player turned the setting on. Built on the client from
 * vanilla's own effect data each time the HUD draws; Statusworks sends nothing for them. They come after the
 * Statusworks entries, in vanilla's order, and look like any entry: the icon, the level on the corner, the time.
 */
final class PotionEntries {

    /** What a potion entry's timer means, in the language file. */
    static final String TIMER_MODE = "potion";

    private final ClientSettings settings;

    PotionEntries(ClientSettings settings) {
        this.settings = settings;
    }

    List<ClientStore.Shown> shown(Minecraft mc) {
        if (!settings.potionEffects || mc.player == null) {
            return Collections.emptyList();
        }
        List<ClientStore.Shown> out = new ArrayList<>();
        int index = -1;
        for (PotionEffect effect : Ordering.natural().reverse().sortedCopy(mc.player.getActivePotionEffects())) {
            Potion potion = effect.getPotion();
            ResourceLocation id = potion.getRegistryName();
            if (id == null || !PotionRules.shown(potion.shouldRenderHUD(effect), effect.doesShowParticles(),
                    settings.hidden.contains(id.toString()), settings.hideInventoryEffects)) {
                continue;
            }
            // A row of its own, never sent and never in the server's table: negative indexes keep it apart. An
            // effect without an end explains itself as a timer of none.
            boolean endless = effect.getIsPotionDurationMax();
            HudDefinition row = new HudDefinition(index--, id.toString(), HudDefinition.POTION, potion.getName(), true,
                    null, false, null, 0, null, true, false, true, 0, Integer.MAX_VALUE, 1,
                    endless ? "none" : TIMER_MODE);
            int remaining = PotionRules.remaining(effect.getDuration(), endless);
            HudEntry entry = new HudEntry("potion|" + id, row.index, HudEntry.OWN,
                    PotionRules.level(effect.getAmplifier()), 0, 0, remaining, true, null,
                    Collections.<HudEntry.Group>emptyList());
            out.add(new ClientStore.Shown(row, entry, remaining, Collections.<Integer>emptyList(), effect));
        }
        return out;
    }
}
