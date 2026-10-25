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

    /** {@code 12.3/50}: the amount with its decimals, the capacity without decimals when it is whole. */
    public static String amount(double amount, double capacity, int decimals) {
        return fixed(amount, decimals) + "/" + (capacity == Math.rint(capacity) ? fixed(capacity, 0)
                : fixed(capacity, decimals));
    }

    private static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + Math.max(0, Math.min(2, decimals)) + "f", value);
    }

    /** The count shows for a status whose limit is above 1, unless the author hid it. */
    public static boolean showsCount(HudDefinition row) {
        return row.kind == HudDefinition.STATUS && row.limit > 1 && row.showCount;
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
        if (showsCount(row)) {
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

    /** How many entries to draw of {@code total} with a maximum, and the {@code +N} text for the rest ("" if none). */
    public static int shown(int total, int maximum) {
        return Math.max(0, Math.min(total, maximum));
    }

    public static String more(int total, int maximum) {
        int rest = total - shown(total, maximum);
        return rest > 0 ? "+" + rest : "";
    }
}
