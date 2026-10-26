package com.mahghuuuls.statusworks.api.client;

import javax.annotation.Nullable;

/**
 * One entry as this client received it from the server: never anything the server did not send, and never an
 * author-hidden definition. Read-only; times are counted down to the moment the entry was read.
 */
public final class ClientEntry {

    private final String definitionId;
    private final String kind;
    private final String name;
    private final boolean nameIsKey;
    private final int count;
    private final double amount;
    private final double capacity;
    private final int remaining;
    private final boolean active;
    @Nullable private final String other;
    private final boolean particles;

    public ClientEntry(String definitionId, String kind, String name, boolean nameIsKey, int count, double amount,
                       double capacity, int remaining, boolean active, @Nullable String other, boolean particles) {
        this.definitionId = definitionId;
        this.kind = kind;
        this.name = name;
        this.nameIsKey = nameIsKey;
        this.count = count;
        this.amount = amount;
        this.capacity = capacity;
        this.remaining = remaining;
        this.active = active;
        this.other = other;
        this.particles = particles;
    }

    public String definitionId() {
        return definitionId;
    }

    /** {@code status}, {@code pool}, {@code mark}, or {@code cooldown}. */
    public String kind() {
        return kind;
    }

    /** The display name, or a translation key when {@link #nameIsKey()}. */
    public String name() {
        return name;
    }

    public boolean nameIsKey() {
        return nameIsKey;
    }

    /** A status's stack count; 0 for the other kinds. */
    public int count() {
        return count;
    }

    /** A pool's amount, rounded to the definition's decimals; 0 for the other kinds. */
    public double amount() {
        return amount;
    }

    public double capacity() {
        return capacity;
    }

    /** Remaining ticks now, or -1 for no timer. */
    public int remaining() {
        return remaining;
    }

    /**
     * False for an inactive mark, for a player's own entries while it is dead, and for another entity's entries
     * while the server does not update that entity (its timers stand still).
     */
    public boolean active() {
        return active;
    }

    /** For a mark on the player's own HUD: the other participant's name. */
    @Nullable
    public String other() {
        return other;
    }

    /** Whether the definition has particles. */
    public boolean particles() {
        return particles;
    }
}
