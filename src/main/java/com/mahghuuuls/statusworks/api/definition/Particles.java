package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * A definition's particles: a vanilla particle type, a color for {@code reddust}, {@code mobSpell}, or
 * {@code mobSpellAmbient}, how many per second, who sees them, and whether they show beyond 32 blocks. The server
 * sends the entries to the audience and each client draws the particles itself; no particle packet is sent.
 * Particles never change gameplay. Statuses, pools, and marks can have particles; cooldowns cannot.
 *
 * <p>Build one with {@link #builder(String)} and give it to {@link Presentation.Builder#particles(Particles)}. A
 * definition without particles shows none.</p>
 */
public final class Particles {

    /**
     * No color: white for {@code mobSpell} and {@code mobSpellAmbient}, red for {@code reddust}, else the type's
     * own.
     */
    public static final int NO_COLOR = -1;

    private final String type;
    private final int color;
    private final double perSecond;
    @Nullable private final ParticleAudience audience;
    @Nullable private final String audienceText;
    private final boolean longDistance;

    private Particles(Builder b) {
        this.type = b.type;
        this.color = b.color;
        this.perSecond = b.perSecond;
        this.audience = b.audience;
        this.audienceText = b.audienceText;
        this.longDistance = b.longDistance;
    }

    /**
     * @param type a vanilla particle name as the {@code /particle} command takes it, for example {@code mobSpell},
     *             {@code reddust}, or {@code flame}; types that need extra arguments (block and item particles) are
     *             not allowed
     */
    public static Builder builder(String type) {
        return new Builder(type);
    }

    public String type() {
        return type;
    }

    /** The color as {@code 0xRRGGBB}, or {@link #NO_COLOR}. */
    public int color() {
        return color;
    }

    /** Particles per second on each entity that shows them. */
    public double perSecond() {
        return perSecond;
    }

    /** Who sees them, or null for the kind's default: nearby players, or a mark's source. */
    @Nullable
    public ParticleAudience audience() {
        return audience;
    }

    /** The audience as the author wrote it in text, or null when it was given as a value or not at all. */
    @Nullable
    public String audienceText() {
        return audienceText;
    }

    /** Whether nearby players see them as far as the game shows the entity, not only within 32 blocks. */
    public boolean longDistance() {
        return longDistance;
    }

    public static final class Builder {
        private final String type;
        private int color = NO_COLOR;
        private double perSecond = 4;
        private ParticleAudience audience;
        private String audienceText;
        private boolean longDistance;

        private Builder(String type) {
            this.type = type;
        }

        /** A color as {@code 0xRRGGBB}; only {@code reddust}, {@code mobSpell}, and {@code mobSpellAmbient} take it. */
        public Builder color(int rgb) {
            this.color = rgb;
            return this;
        }

        /** A color from red, green, and blue, each 0 to 255. */
        public Builder color(int red, int green, int blue) {
            this.color = ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
            return this;
        }

        /** Particles per second, above 0 and at most 100; 4 unless set. */
        public Builder perSecond(double rate) {
            this.perSecond = rate;
            return this;
        }

        /** Who sees them; statuses and pools take only {@link ParticleAudience#NEARBY}. */
        public Builder audience(ParticleAudience audience) {
            this.audience = audience;
            this.audienceText = null;
            return this;
        }

        /**
         * Who sees them, as text: {@code nearby}, {@code source}, {@code target}, or {@code both}. Unknown text makes
         * the definition invalid.
         */
        public Builder audience(String text) {
            this.audienceText = text;
            this.audience = ParticleAudience.fromText(text);
            return this;
        }

        /** Nearby players see them as far as the game shows the entity, not only within 32 blocks. */
        public Builder longDistance() {
            this.longDistance = true;
            return this;
        }

        public Particles build() {
            return new Particles(this);
        }
    }
}
