package com.mahghuuuls.statusworks.client;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The player's own display settings in {@code config/statusworks-client.cfg}: HUD on or off, anchor, offset, the
 * entry and row directions, scale, maximum entries, hidden entries by definition id (or a potion effect's registry
 * id), the icon grid (icons per row, small-text size, short times) or a list with names, the player's potion effects
 * on the HUD (and vanilla's inventory effect list hidden), buffs and debuffs in separate groups, a red border on
 * debuffs, vanilla's frame behind potion effects, and particles on or off. Saved on the client only and
 * never sent to the server, so they change what this player sees and nothing else. Read at start and again each
 * time the player joins a server.
 */
public final class ClientSettings {

    /** Shown at the top right, where vanilla draws its potion icons; with this on, those are not drawn. */
    static final boolean POTION_EFFECTS_DEFAULT = true;
    /** With the potion effects on this HUD, vanilla's list beside the inventories is not drawn either. */
    static final boolean HIDE_INVENTORY_EFFECTS_DEFAULT = true;
    /** Buffs at the anchor and debuffs in their own group after a gap, each with its own maximum. */
    static final boolean SEPARATE_DEBUFFS_DEFAULT = true;
    static final boolean DEBUFF_BORDER_DEFAULT = true;
    static final boolean POTION_FRAME_DEFAULT = true;

    private final Configuration file;
    private final Logger logger;
    volatile boolean hudEnabled = true;
    volatile HudLayout.Anchor anchor = HudLayout.DEFAULT_ANCHOR;
    volatile HudLayout.EntryDirection entryDirection = HudLayout.EntryDirection.AUTO;
    volatile HudLayout.RowDirection rowDirection = HudLayout.RowDirection.AUTO;
    volatile int offsetX = 4;
    volatile int offsetY = 4;
    volatile double scale = 1.0;
    volatile int maxEntries = 8;
    volatile Set<String> hidden = Collections.emptySet();
    volatile boolean particles = true;
    volatile boolean showNames = false;
    volatile double textScale = 0.75;
    volatile int iconsPerRow = 10;
    volatile boolean shortTime = true;
    volatile boolean potionEffects = POTION_EFFECTS_DEFAULT;
    volatile boolean hideInventoryEffects = HIDE_INVENTORY_EFFECTS_DEFAULT;
    volatile boolean separateDebuffs = SEPARATE_DEBUFFS_DEFAULT;
    volatile boolean debuffBorder = DEBUFF_BORDER_DEFAULT;
    volatile boolean potionFrame = POTION_FRAME_DEFAULT;

    public ClientSettings(File path, Logger logger) {
        this.file = new Configuration(path);
        this.logger = logger;
    }

    /** Reads the file, writing any missing setting with its default. */
    public synchronized void load() {
        file.load();
        hudEnabled = file.getBoolean("enabled", "hud", true, "Draw Statusworks entries on the screen and in the inventory.");
        // The four-corner setting of earlier development builds is gone; a file without an anchor gets the default.
        ConfigCategory hud = file.getCategory("hud");
        boolean removedCorner = hud.containsKey("corner");
        if (removedCorner) {
            hud.remove("corner");
        }
        anchor = choice(HudLayout.Anchor.class, "anchor", HudLayout.DEFAULT_ANCHOR, "Where the entries are drawn.");
        offsetX = offset("offsetX", anchor.horizontal, "Pixels in from the left or right edge (0 or more). With a"
                + " center anchor, pixels right of the center (negative: left; 0: exactly centered).");
        offsetY = offset("offsetY", anchor.vertical, "Pixels in from the top or bottom edge (0 or more). With a"
                + " middle anchor, pixels below the middle (negative: above; 0: exactly in the middle).");
        entryDirection = choice(HudLayout.EntryDirection.class, "entryDirection", HudLayout.EntryDirection.AUTO,
                "The order of the entries in a row; auto goes away from the anchor's side (left to right at a center"
                        + " anchor).");
        rowDirection = choice(HudLayout.RowDirection.class, "rowDirection", HudLayout.RowDirection.AUTO,
                "Where the next row goes; auto goes away from the anchor's edge (down at a middle anchor).");
        scale = file.getFloat("scale", "hud", 1.0f, 0.25f, 4.0f, "Size of the entries.");
        maxEntries = file.getInt("maxEntries", "hud", 8, 1, 64, "Most entries drawn; the rest show as +N.");
        showNames = file.getBoolean("showNames", "hud", false, "Draw a list of rows with names instead of the icon"
                + " grid.");
        textScale = file.getFloat("textScale", "hud", 0.75f, 0.5f, 1.5f, "Size of the small texts in the icon grid.");
        iconsPerRow = file.getInt("iconsPerRow", "hud", 10, 1, 32, "Icons in one row of the grid.");
        shortTime = file.getBoolean("shortTime", "hud", true, "In the grid, show times as 45s, 2m, 1h instead of"
                + " 45 and 1:05.");
        potionEffects = file.getBoolean("potionEffects", "hud", POTION_EFFECTS_DEFAULT, "Also show your own potion"
                + " effects on this HUD, after the Statusworks entries, and hide vanilla's potion icons at the top"
                + " right.");
        hideInventoryEffects = file.getBoolean("hideInventoryEffects", "hud", HIDE_INVENTORY_EFFECTS_DEFAULT, "While"
                + " this HUD and potionEffects are on, also hide vanilla's effect list beside the survival and creative"
                + " inventories and keep them centered, so this HUD is the only place the effects show.");
        separateDebuffs = file.getBoolean("separateDebuffs", "hud", SEPARATE_DEBUFFS_DEFAULT, "Draw harmful entries"
                + " (debuffs) as their own group after a gap: below the others at a top or middle anchor, above them at"
                + " a bottom anchor. The maximum, +N, and icons per row then apply to each group.");
        debuffBorder = file.getBoolean("debuffBorder", "hud", DEBUFF_BORDER_DEFAULT, "Draw a red border around the"
                + " icon of each harmful entry.");
        potionFrame = file.getBoolean("potionFrame", "hud", POTION_FRAME_DEFAULT, "Draw vanilla's potion frame"
                + " behind each potion effect, so potion effects look different from Statusworks entries.");
        hidden = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(file.getStringList("hiddenEntries", "hud",
                new String[0], "Definition ids, or potion effect ids such as minecraft:speed, this player does not want"
                + " to see. Hiding changes nothing in the game."))));
        particles = file.getBoolean("enabled", "particles", true, "Draw Statusworks particles.");
        if (file.hasChanged() || removedCorner) {
            file.save();
        }
    }

    /**
     * One of an enum's values by its setting text. An unknown text gives the default, with a warning naming the
     * setting, and stays in the file for the player to correct.
     */
    private <E extends Enum<E>> E choice(Class<E> type, String key, E fallback, String comment) {
        String text = file.getString(key, "hud", HudLayout.text(fallback), comment + " Values: "
                + String.join(", ", HudLayout.texts(type)) + ".", HudLayout.texts(type));
        E value = HudLayout.fromText(type, text);
        if (value == null) {
            logger.warn("Statusworks client setting hud.{} has the unknown value \"{}\"; using {}. Valid values: {}.",
                    key, text, HudLayout.text(fallback), String.join(", ", HudLayout.texts(type)));
            return fallback;
        }
        return value;
    }

    /** An offset; a negative one at an edge (side not 0) counts as 0, with a warning. */
    private int offset(String key, int side, String comment) {
        int value = file.getInt(key, "hud", 4, -10000, 10000, comment);
        if (value < 0 && side != 0) {
            logger.warn("Statusworks client setting hud.{} is {}, but the anchor is at an edge; using 0. A negative"
                    + " offset only applies with a center or middle anchor.", key, value);
        }
        return value;
    }

    public boolean particlesEnabled() {
        return particles;
    }
}
