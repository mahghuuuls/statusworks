package com.mahghuuuls.statusworks.client;

/**
 * The rules for showing the player's own potion effects on the HUD, with no Minecraft types: which effects show,
 * the level on the icon's corner, and the time.
 */
final class PotionRules {

    private PotionRules() {
    }

    /**
     * Whether an effect shows, once the player turned the setting on: it does not hide its HUD icon, the player did not
     * hide it by id, and it shows its particles as vanilla's HUD requires, unless vanilla's inventory list is hidden;
     * then this HUD is the only place left, so an effect with hidden particles shows too.
     */
    static boolean shown(boolean rendersHud, boolean showsParticles, boolean hiddenByPlayer,
                         boolean inventoryListHidden) {
        return rendersHud && (showsParticles || inventoryListHidden) && !hiddenByPlayer;
    }

    /** The level as a count: amplifier 0 is level 1, which shows no number on the corner. */
    static int level(int amplifier) {
        return amplifier + 1;
    }

    /** Remaining ticks, or -1 for an effect without an end (vanilla's "**:**"). */
    static int remaining(int duration, boolean durationMax) {
        return durationMax ? -1 : Math.max(0, duration);
    }
}
