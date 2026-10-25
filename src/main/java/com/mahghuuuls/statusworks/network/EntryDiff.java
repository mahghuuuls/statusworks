package com.mahghuuuls.statusworks.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What one player was last sent, and the decision of what to send now. An entry is sent again only when something
 * the player sees changed, or when its timer is no longer where the client's countdown has it (a refresh, a pause,
 * or a reset); the countdown itself sends nothing. Plain values; no Minecraft types.
 */
public final class EntryDiff {

    /** Ticks a timer may differ from the client's countdown before it is sent again. */
    static final int DRIFT_TICKS = 2;

    private static final class Sent {
        final HudEntry entry;
        final long tick;

        Sent(HudEntry entry, long tick) {
            this.entry = entry;
            this.tick = tick;
        }
    }

    /** The changes of one update: entries to add or replace, and keys to remove. */
    public static final class Changes {
        public final List<HudEntry> changed = new ArrayList<>();
        public final List<String> removed = new ArrayList<>();

        public boolean isEmpty() {
            return changed.isEmpty() && removed.isEmpty();
        }
    }

    private final Map<String, Sent> sent = new HashMap<>();

    /** Forgets everything sent, so the next update is a full snapshot. */
    public void clear() {
        sent.clear();
    }

    /**
     * Compares the entries the player should see now with what was sent, remembers the new state, and returns the
     * difference.
     */
    public Changes update(Map<String, HudEntry> now, long tick) {
        Changes changes = new Changes();
        for (Iterator<Map.Entry<String, Sent>> it = sent.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Sent> e = it.next();
            if (!now.containsKey(e.getKey())) {
                changes.removed.add(e.getKey());
                it.remove();
            }
        }
        for (HudEntry entry : now.values()) {
            Sent last = sent.get(entry.key);
            if (last == null || !last.entry.sameExceptTime(entry) || drifted(last, entry, tick)) {
                changes.changed.add(entry);
                sent.put(entry.key, new Sent(entry, tick));
            }
        }
        return changes;
    }

    private static boolean drifted(Sent last, HudEntry now, long tick) {
        long elapsed = last.entry.countsDown() ? tick - last.tick : 0;
        if (off(last.entry.remaining, now.remaining, elapsed)) {
            return true;
        }
        for (int i = 0; i < now.groups.size(); i++) {
            if (off(last.entry.groups.get(i).remaining, now.groups.get(i).remaining, elapsed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean off(int sentRemaining, int nowRemaining, long elapsed) {
        if (sentRemaining < 0 || nowRemaining < 0) {
            return sentRemaining != nowRemaining;
        }
        long expected = Math.max(0, sentRemaining - elapsed);
        return Math.abs(expected - nowRemaining) > DRIFT_TICKS;
    }
}
