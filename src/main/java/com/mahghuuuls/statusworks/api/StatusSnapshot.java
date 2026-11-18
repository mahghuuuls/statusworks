package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A read-only copy of the visible statuses active on one living entity at the moment it was taken
 * ({@link StatusService#snapshot}). It holds no entity, definition or Minecraft object and never changes; take a new
 * one to see later changes. Author-hidden statuses are never in it; a player's own HUD hiding does not apply.
 * Unlike {@link PoolSnapshot} and {@link MarkSnapshot}, which describe one instance, it covers every visible status
 * of one entity. Two snapshots with the same rows are equal. The rows include remaining times, which change every
 * tick for a timed status, so a mod that resends only on change should compare the rows without their times (or
 * send an end tick once) rather than compare whole snapshots.
 *
 * <p>The constructors are for Statusworks and for test doubles; they may gain parameters in a later version.</p>
 *
 * @since 1.4.0
 */
public final class StatusSnapshot {

    /** The snapshot with no statuses. */
    public static final StatusSnapshot EMPTY = new StatusSnapshot(Collections.<Entry>emptyList());

    private final List<Entry> entries;

    public StatusSnapshot(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** The statuses, in registration order; read-only. */
    public List<Entry> entries() {
        return entries;
    }

    /** Whether the snapshot lists no status. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StatusSnapshot && entries.equals(((StatusSnapshot) o).entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "StatusSnapshot" + entries;
    }

    /** One active, visible status and its presentation. */
    public static final class Entry {
        private final String definitionId;
        private final String name;
        private final boolean nameIsKey;
        @Nullable private final String iconItem;
        private final int iconMeta;
        @Nullable private final String iconTexture;
        private final int count;
        private final int remainingTicks;
        private final boolean harmful;
        private final int priority;
        private final boolean showCount;
        private final boolean showTime;
        private final List<Group> groups;

        public Entry(String definitionId, String name, boolean nameIsKey, @Nullable String iconItem,
                     int iconMeta, @Nullable String iconTexture, int count, int remainingTicks, boolean harmful,
                     int priority, boolean showCount, boolean showTime, List<Group> groups) {
            this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
            this.name = Objects.requireNonNull(name, "name");
            this.nameIsKey = nameIsKey;
            this.iconItem = iconItem;
            this.iconMeta = iconMeta;
            this.iconTexture = iconTexture;
            this.count = count;
            this.remainingTicks = remainingTicks;
            this.harmful = harmful;
            this.priority = priority;
            this.showCount = showCount;
            this.showTime = showTime;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        }

        /** The status definition id. */
        public String definitionId() {
            return definitionId;
        }

        /**
         * The name as the definition writes it, text or a translation key ({@link #nameIsKey()}); for a definition
         * without a name, the id's path (for {@code mymod:rage}, {@code rage}), as Statusworks' HUD shows it. Never
         * null.
         */
        public String name() {
            return name;
        }

        /** Whether {@link #name()} is a translation key; the server does not translate it. */
        public boolean nameIsKey() {
            return nameIsKey;
        }

        /** The icon item's registry id, for example {@code minecraft:blaze_powder}, or null. */
        @Nullable
        public String iconItem() {
            return iconItem;
        }

        /** The icon item's metadata. */
        public int iconMeta() {
            return iconMeta;
        }

        /** The icon texture's resource location, or null. With neither icon, Statusworks' HUD draws its own. */
        @Nullable
        public String iconTexture() {
            return iconTexture;
        }

        /** The total stack count, across all groups of a per-source status. */
        public int count() {
            return count;
        }

        /**
         * The remaining time in ticks, as {@link StatusService#remainingTicks(net.minecraft.entity.Entity, String)}
         * gives it: -1 for a status without expiration; for a per-source status, the soonest across its groups.
         */
        public int remainingTicks() {
            return remainingTicks;
        }

        /** Whether the status is harmful (a debuff); otherwise beneficial. */
        public boolean harmful() {
            return harmful;
        }

        /** The display priority: lower is drawn first by Statusworks' HUD; ordering is the reader's choice. */
        public int priority() {
            return priority;
        }

        /** Whether the definition's author wants the stack count shown. */
        public boolean showCount() {
            return showCount;
        }

        /** Whether the definition's author wants the remaining time shown. */
        public boolean showTime() {
            return showTime;
        }

        /** For a per-source status, one group per source; empty for a shared status. Read-only. */
        public List<Group> groups() {
            return groups;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            Entry e = (Entry) o;
            return definitionId.equals(e.definitionId) && Objects.equals(name, e.name) && nameIsKey == e.nameIsKey
                    && Objects.equals(iconItem, e.iconItem) && iconMeta == e.iconMeta
                    && Objects.equals(iconTexture, e.iconTexture) && count == e.count
                    && remainingTicks == e.remainingTicks && harmful == e.harmful && priority == e.priority
                    && showCount == e.showCount && showTime == e.showTime && groups.equals(e.groups);
        }

        @Override
        public int hashCode() {
            return Objects.hash(definitionId, name, nameIsKey, iconItem, iconMeta, iconTexture, count, remainingTicks,
                    harmful, priority, showCount, showTime, groups);
        }

        @Override
        public String toString() {
            return definitionId + "{count=" + count + ", remaining=" + remainingTicks + ", groups=" + groups + "}";
        }
    }

    /** The stacks of one source of a per-source status. */
    public static final class Group {
        private final UUID source;
        private final int count;
        private final int remainingTicks;

        public Group(UUID source, int count, int remainingTicks) {
            this.source = source;
            this.count = count;
            this.remainingTicks = remainingTicks;
        }

        /** The source's UUID, or {@link StatusService#NO_SOURCE} for the stacks applied without a source. */
        public UUID source() {
            return source;
        }

        /** This group's stack count. */
        public int count() {
            return count;
        }

        /** This group's remaining time in ticks, -1 without expiration. */
        public int remainingTicks() {
            return remainingTicks;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Group)) {
                return false;
            }
            Group g = (Group) o;
            return source.equals(g.source) && count == g.count && remainingTicks == g.remainingTicks;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, count, remainingTicks);
        }

        @Override
        public String toString() {
            return source + ":" + count + "/" + remainingTicks;
        }
    }
}
