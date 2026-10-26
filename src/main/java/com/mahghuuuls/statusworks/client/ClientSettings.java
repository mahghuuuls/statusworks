package com.mahghuuuls.statusworks.client;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The player's own display settings in {@code config/statusworks-client.cfg}: HUD on or off, corner, offset, scale,
 * maximum entries, hidden entries by definition id (or a potion effect's registry id), the icon grid (icons per row,
 * small-text size, short times) or a list with names, the player's potion effects on the HUD, and particles on or
 * off. Saved on the client only and never
 * sent to the server, so they change what this player sees and nothing else. Read at start and again each time
 * the player joins a server.
 */
public final class ClientSettings {

    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    private final Configuration file;
    volatile boolean hudEnabled = true;
    volatile Corner corner = Corner.TOP_LEFT;
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
    volatile boolean potionEffects = false;

    public ClientSettings(File path) {
        this.file = new Configuration(path);
    }

    /** Reads the file, writing any missing setting with its default. */
    public synchronized void load() {
        file.load();
        hudEnabled = file.getBoolean("enabled", "hud", true, "Draw Statusworks entries on the screen and in the inventory.");
        String cornerText = file.getString("corner", "hud", "top_left", "Where the entries are drawn.",
                new String[]{"top_left", "top_right", "bottom_left", "bottom_right"});
        try {
            corner = Corner.valueOf(cornerText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            corner = Corner.TOP_LEFT;
        }
        offsetX = file.getInt("offsetX", "hud", 4, 0, 10000, "Pixels from the left or right edge.");
        offsetY = file.getInt("offsetY", "hud", 4, 0, 10000, "Pixels from the top or bottom edge.");
        scale = file.getFloat("scale", "hud", 1.0f, 0.25f, 4.0f, "Size of the entries.");
        maxEntries = file.getInt("maxEntries", "hud", 8, 1, 64, "Most entries drawn; the rest show as +N.");
        showNames = file.getBoolean("showNames", "hud", false, "Draw a list of rows with names instead of the icon"
                + " grid.");
        textScale = file.getFloat("textScale", "hud", 0.75f, 0.5f, 1.5f, "Size of the small texts in the icon grid.");
        iconsPerRow = file.getInt("iconsPerRow", "hud", 10, 1, 32, "Icons in one row of the grid.");
        shortTime = file.getBoolean("shortTime", "hud", true, "In the grid, show times as 45s, 2m, 1h instead of"
                + " 45 and 1:05.");
        potionEffects = file.getBoolean("potionEffects", "hud", false, "Also show your own potion effects on this"
                + " HUD, after the Statusworks entries, and hide vanilla's potion icons at the top right.");
        hidden = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(file.getStringList("hiddenEntries", "hud",
                new String[0], "Definition ids, or potion effect ids such as minecraft:speed, this player does not want"
                + " to see. Hiding changes nothing in the game."))));
        particles = file.getBoolean("enabled", "particles", true, "Draw Statusworks particles.");
        if (file.hasChanged()) {
            file.save();
        }
    }

    public boolean particlesEnabled() {
        return particles;
    }
}
