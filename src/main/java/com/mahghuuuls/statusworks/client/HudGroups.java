package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whether each shown entry is a buff or a debuff, and the groups the HUD draws them in. This is the one place that
 * decides it on the client: a potion effect is harmful when vanilla marks its potion as a bad effect, a mark uses
 * the value of the side the player sees it from, and every other entry uses its definition's value.
 *
 * <p>With debuffs separated, the buffs form the first group and the debuffs the second, each keeping the order it
 * was given (the Statusworks entries by priority, then the potion effects) and each with its own maximum and
 * {@code +N}, so a long list of buffs never hides a debuff. Otherwise every entry is in one group with one
 * maximum.</p>
 */
final class HudGroups {

    /** One group as drawn: the entries within the maximum, those over it, and the {@code +N} text (or empty). */
    static final class Group {
        final List<ClientStore.Shown> drawn;
        final List<ClientStore.Shown> rest;
        final String more;

        Group(List<ClientStore.Shown> all, int maxEntries) {
            int count = HudText.shown(all.size(), maxEntries);
            this.drawn = Collections.unmodifiableList(new ArrayList<>(all.subList(0, count)));
            this.rest = Collections.unmodifiableList(new ArrayList<>(all.subList(count, all.size())));
            this.more = HudText.more(all.size(), maxEntries);
        }
    }

    private HudGroups() {
    }

    static boolean harmful(ClientStore.Shown s) {
        boolean badPotion = s.potion != null && s.potion.getPotion().isBadEffect();
        return harmful(s.row, s.entry.side, badPotion);
    }

    /** The rule itself, apart from vanilla's potion data, so it can be tested without the game. */
    static boolean harmful(HudDefinition row, byte side, boolean badPotion) {
        if (row.kind == HudDefinition.POTION) {
            return badPotion;
        }
        if (row.kind == HudDefinition.MARK && side == HudEntry.MARK_TARGET) {
            return row.harmfulOnTarget;
        }
        return row.harmful;
    }

    /** The groups to draw, in order; an empty group is left out, so there may be none. */
    static List<Group> groups(List<ClientStore.Shown> all, int maxEntries, boolean separateDebuffs) {
        List<List<ClientStore.Shown>> parts = new ArrayList<>(2);
        if (!separateDebuffs) {
            parts.add(all);
        } else {
            List<ClientStore.Shown> buffs = new ArrayList<>();
            List<ClientStore.Shown> debuffs = new ArrayList<>();
            for (ClientStore.Shown s : all) {
                (harmful(s) ? debuffs : buffs).add(s);
            }
            parts.add(buffs);
            parts.add(debuffs);
        }
        List<Group> groups = new ArrayList<>(2);
        for (List<ClientStore.Shown> part : parts) {
            if (!part.isEmpty()) {
                groups.add(new Group(part, maxEntries));
            }
        }
        return groups;
    }
}
