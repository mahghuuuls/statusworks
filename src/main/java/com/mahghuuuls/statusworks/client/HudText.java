package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text rules of an entry, with no Minecraft types: which fields show, how a time and an amount read, and the
 * client countdown between server updates.
 */
public final class HudText {

    private HudText() {
    }

    /** Seconds under one minute ({@code 45}), then minutes and seconds ({@code 1:05}); empty for no timer. */
    public static String time(int ticks) {
        return time(ticks, false);
    }

    /**
     * {@link #time(int)}, or in the short form {@code 45s} under one minute, then whole minutes rounded up
     * ({@code 2m}), then whole hours rounded up ({@code 1h}).
     */
    public static String time(int ticks, boolean shortForm) {
        if (shortForm && ticks >= 0) {
            int seconds = (ticks + 19) / 20;
            if (seconds < 60) {
                return seconds + "s";
            }
            int minutes = (seconds + 59) / 60;
            if (minutes < 60) {
                return minutes + "m";
            }
            return (seconds + 3599) / 3600 + "h";
        }
        if (ticks < 0) {
            return "";
        }
        int seconds = (ticks + 19) / 20;
        if (seconds < 60) {
            return Integer.toString(seconds);
        }
        int s = seconds % 60;
        return (seconds / 60) + ":" + (s < 10 ? "0" : "") + s;
    }

    /**
     * The exact time for a tooltip, where it stands alone: {@code 45s} under one minute, then {@code 1:05}; empty
     * for no timer.
     */
    public static String fullTime(int ticks) {
        if (ticks >= 0 && (ticks + 19) / 20 < 60) {
            return time(ticks) + "s";
        }
        return time(ticks);
    }

    /** {@code 12.3/50}: the amount with its decimals, the capacity without decimals when it is whole. */
    public static String amount(double amount, double capacity, int decimals) {
        return fixed(amount, decimals) + "/" + (capacity == Math.rint(capacity) ? fixed(capacity, 0)
                : fixed(capacity, decimals));
    }

    private static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + Math.max(0, Math.min(2, decimals)) + "f", value);
    }

    /**
     * The count shows for a status whose limit is above 1, unless the author hid it, and as the level of a potion
     * effect (above level I only).
     */
    public static boolean showsCount(HudDefinition row) {
        return (row.kind == HudDefinition.STATUS && row.limit > 1 || row.kind == HudDefinition.POTION)
                && row.showCount;
    }

    /** Whether this entry's count shows: a status above limit 1, or a potion effect above level I. */
    private static boolean showsCount(HudDefinition row, HudEntry entry) {
        return showsCount(row) && (row.kind != HudDefinition.POTION || entry.count > 1);
    }

    /** The remaining time the client shows now: the server's value counted down while the entry is active. */
    public static int remainingNow(HudEntry entry, int sentRemaining, long elapsedTicks) {
        if (sentRemaining < 0) {
            return -1;
        }
        return entry.countsDown() ? (int) Math.max(0, sentRemaining - elapsedTicks) : sentRemaining;
    }

    /** The parts of an entry's line after its name, in order: count, amount, other participant, time. */
    public static List<String> fields(HudDefinition row, HudEntry entry, int remaining) {
        List<String> parts = new ArrayList<>(4);
        if (showsCount(row, entry)) {
            parts.add(Integer.toString(entry.count));
        }
        if (row.kind == HudDefinition.POOL && row.showAmount) {
            parts.add(amount(entry.amount, entry.capacity, row.decimals));
        }
        if (row.kind == HudDefinition.MARK && entry.other != null) {
            parts.add(entry.other);
        }
        if (row.showTime && remaining >= 0) {
            parts.add(time(remaining));
        }
        return parts;
    }

    /** The count on an icon's corner in the grid, or "" when it does not show. */
    public static String corner(HudDefinition row, HudEntry entry) {
        return showsCount(row, entry) ? Integer.toString(entry.count) : "";
    }

    /**
     * The small lines under an icon in the grid: only the time, so the cells stay as narrow as the icon. A pool
     * shows a bar instead of its amount; names, amounts, and a mark's other participant show in the list layout and
     * the detail view, not in the grid.
     */
    public static List<String> under(HudDefinition row, HudEntry entry, int remaining, boolean shortTime) {
        List<String> lines = new ArrayList<>(1);
        if (row.showTime && remaining >= 0) {
            lines.add(time(remaining, shortTime));
        }
        return lines;
    }

    /** A pool's fill from 0 to 1 for the bar under its icon, or -1 when the entry has no bar. */
    public static double bar(HudDefinition row, HudEntry entry) {
        if (row.kind != HudDefinition.POOL || !row.showAmount || !(entry.capacity > 0)) {
            return -1;
        }
        return Math.max(0, Math.min(1, entry.amount / entry.capacity));
    }

    /** How many entries to draw of {@code total} with a maximum, and the {@code +N} text for the rest ("" if none). */
    public static int shown(int total, int maximum) {
        return Math.max(0, Math.min(total, maximum));
    }

    public static String more(int total, int maximum) {
        int rest = total - shown(total, maximum);
        return rest > 0 ? "+" + rest : "";
    }
}
