package com.mahghuuuls.statusworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One entry a player sees: one instance on the player, or one mark the player takes part in. The amount is already
 * rounded to the definition's decimals, so an amount change the player cannot see sends nothing. The remaining
 * time is a snapshot in ticks ({@code -1} for none); clients count it down between updates.
 */
public final class HudEntry {

    /** An instance on the player itself. */
    public static final byte OWN = 0;
    /** A mark the player holds as its source. */
    public static final byte MARK_SOURCE = 1;
    /** A mark on the player as its target. */
    public static final byte MARK_TARGET = 2;

    /** One source's group of a {@code per_source} status, for the detail view. */
    public static final class Group {
        public final String source;
        public final int count;
        public final int remaining;

        public Group(String source, int count, int remaining) {
            this.source = source;
            this.count = count;
            this.remaining = remaining;
        }

        boolean sameExceptTime(Group o) {
            return count == o.count && source.equals(o.source);
        }
    }

    /** Stable identity of the entry for one player: the definition, and for a mark its side and other participant. */
    public final String key;
    public final int definition;
    public final byte side;
    public final int count;
    public final double amount;
    public final double capacity;
    public final int remaining;
    public final boolean active;
    /** For a mark: the other participant's name. */
    @Nullable public final String other;
    public final List<Group> groups;

    public HudEntry(String key, int definition, byte side, int count, double amount, double capacity, int remaining,
                    boolean active, @Nullable String other, List<Group> groups) {
        this.key = key;
        this.definition = definition;
        this.side = side;
        this.count = count;
        this.amount = amount;
        this.capacity = capacity;
        this.remaining = remaining;
        this.active = active;
        this.other = other;
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
    }

    /**
     * Whether a client counts this entry's timer down: only while the entry is active. The server sends an inactive
     * mark, and every entry of a player that is not being updated (dead, on the death screen), as inactive.
     */
    public boolean countsDown() {
        return active;
    }

    /** Whether everything a player sees, except the passing of time, is the same. */
    boolean sameExceptTime(HudEntry o) {
        if (definition != o.definition || side != o.side || count != o.count || amount != o.amount
                || capacity != o.capacity || active != o.active || !Objects.equals(other, o.other)
                || groups.size() != o.groups.size() || (remaining < 0) != (o.remaining < 0)) {
            return false;
        }
        for (int i = 0; i < groups.size(); i++) {
            if (!groups.get(i).sameExceptTime(o.groups.get(i))) {
                return false;
            }
        }
        return true;
    }

    void write(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, key);
        ByteBufUtils.writeVarInt(buf, definition, 5);
        buf.writeByte(side);
        ByteBufUtils.writeVarInt(buf, count, 5);
        buf.writeDouble(amount);
        buf.writeDouble(capacity);
        buf.writeInt(remaining);
        buf.writeBoolean(active);
        HudDefinition.writeOptional(buf, other);
        ByteBufUtils.writeVarInt(buf, groups.size(), 5);
        for (Group g : groups) {
            ByteBufUtils.writeUTF8String(buf, g.source);
            ByteBufUtils.writeVarInt(buf, g.count, 5);
            buf.writeInt(g.remaining);
        }
    }

    static HudEntry read(ByteBuf buf) {
        String key = ByteBufUtils.readUTF8String(buf);
        int definition = ByteBufUtils.readVarInt(buf, 5);
        byte side = buf.readByte();
        int count = ByteBufUtils.readVarInt(buf, 5);
        double amount = buf.readDouble();
        double capacity = buf.readDouble();
        int remaining = buf.readInt();
        boolean active = buf.readBoolean();
        String other = HudDefinition.readOptional(buf);
        int n = ByteBufUtils.readVarInt(buf, 5);
        List<Group> groups = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            groups.add(new Group(ByteBufUtils.readUTF8String(buf), ByteBufUtils.readVarInt(buf, 5), buf.readInt()));
        }
        return new HudEntry(key, definition, side, count, amount, capacity, remaining, active, other, groups);
    }
}
