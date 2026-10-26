package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * How a definition looks on the players' HUD and detail view: whether it is shown at all, its name and
 * description (plain text or translation keys), its icon, which fields it shows, how many decimals a pool amount
 * has, its order, and for a mark on whose HUD it appears. Presentation never changes gameplay.
 *
 * <p>Build one with {@link #builder()} and give it to a definition builder's {@code presentation(...)}. A
 * definition without one uses {@link #DEFAULT}: shown, named by its id's path, the generic icon, every field, 0
 * decimals, priority 0, for a mark the source's HUD only, and no particles.</p>
 */
public final class Presentation {

    /** Shown with every default. */
    public static final Presentation DEFAULT = builder().build();

    private final boolean hidden;
    @Nullable private final String name;
    private final boolean nameIsKey;
    @Nullable private final String description;
    private final boolean descriptionIsKey;
    @Nullable private final String iconItem;
    private final int iconMeta;
    @Nullable private final String iconTexture;
    private final boolean showCount;
    private final boolean showAmount;
    private final boolean showTime;
    private final int decimals;
    private final int priority;
    private final boolean markOnSource;
    private final boolean markOnTarget;
    @Nullable private final Particles particles;

    private Presentation(Builder b) {
        this.hidden = b.hidden;
        this.name = b.name;
        this.nameIsKey = b.nameIsKey;
        this.description = b.description;
        this.descriptionIsKey = b.descriptionIsKey;
        this.iconItem = b.iconItem;
        this.iconMeta = b.iconMeta;
        this.iconTexture = b.iconTexture;
        this.showCount = b.showCount;
        this.showAmount = b.showAmount;
        this.showTime = b.showTime;
        this.decimals = b.decimals;
        this.priority = b.priority;
        this.markOnSource = b.markOnSource;
        this.markOnTarget = b.markOnTarget;
        this.particles = b.particles;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Whether the author hid the definition: its entries are never sent to ordinary clients. */
    public boolean hidden() {
        return hidden;
    }

    /** The display name, or null to show the definition id's path (the part after the colon). */
    @Nullable
    public String name() {
        return name;
    }

    /** Whether {@link #name()} is a translation key rather than text. */
    public boolean nameIsKey() {
        return nameIsKey;
    }

    @Nullable
    public String description() {
        return description;
    }

    public boolean descriptionIsKey() {
        return descriptionIsKey;
    }

    /** The icon item's registry id, for example {@code minecraft:blaze_powder}, or null. */
    @Nullable
    public String iconItem() {
        return iconItem;
    }

    public int iconMeta() {
        return iconMeta;
    }

    /** The icon texture's resource location, for example {@code mymod:textures/gui/rage.png}, or null. */
    @Nullable
    public String iconTexture() {
        return iconTexture;
    }

    /** Whether the stack count shows (it shows only when the limit is above 1 anyway). */
    public boolean showCount() {
        return showCount;
    }

    /** Whether a pool's amount and capacity show. */
    public boolean showAmount() {
        return showAmount;
    }

    /** Whether the remaining time shows. */
    public boolean showTime() {
        return showTime;
    }

    /** Decimals of a pool amount: 0, 1, or 2. */
    public int decimals() {
        return decimals;
    }

    /** Lower is drawn first; equal priorities keep registration order. */
    public int priority() {
        return priority;
    }

    /** For a mark: whether it shows on the source's HUD (default true). */
    public boolean markOnSource() {
        return markOnSource;
    }

    /** For a mark: whether it shows on the target's HUD (default false). */
    public boolean markOnTarget() {
        return markOnTarget;
    }

    /** The particles, or null for none. A hidden definition shows no particles either: nothing about it is sent. */
    @Nullable
    public Particles particles() {
        return particles;
    }

    public static final class Builder {
        private boolean hidden;
        private String name;
        private boolean nameIsKey;
        private String description;
        private boolean descriptionIsKey;
        private String iconItem;
        private int iconMeta;
        private String iconTexture;
        private boolean showCount = true;
        private boolean showAmount = true;
        private boolean showTime = true;
        private int decimals;
        private int priority;
        private boolean markOnSource = true;
        private boolean markOnTarget;
        private Particles particles;

        private Builder() {
        }

        /** Hides the definition from every ordinary client; nothing about it is sent. */
        public Builder hidden() {
            this.hidden = true;
            return this;
        }

        public Builder name(String text) {
            this.name = text;
            this.nameIsKey = false;
            return this;
        }

        /** The name as a translation key, translated on each client. */
        public Builder nameKey(String key) {
            this.name = key;
            this.nameIsKey = true;
            return this;
        }

        public Builder description(String text) {
            this.description = text;
            this.descriptionIsKey = false;
            return this;
        }

        public Builder descriptionKey(String key) {
            this.description = key;
            this.descriptionIsKey = true;
            return this;
        }

        /** An item as the icon, by registry id and metadata; the player does not need to have it. */
        public Builder iconItem(String id, int meta) {
            this.iconItem = id;
            this.iconMeta = meta;
            this.iconTexture = null;
            return this;
        }

        /** A texture as the icon, by resource location, drawn at 16 by 16. */
        public Builder iconTexture(String resourceLocation) {
            this.iconTexture = resourceLocation;
            this.iconItem = null;
            return this;
        }

        public Builder showCount(boolean show) {
            this.showCount = show;
            return this;
        }

        public Builder showAmount(boolean show) {
            this.showAmount = show;
            return this;
        }

        public Builder showTime(boolean show) {
            this.showTime = show;
            return this;
        }

        /** Decimals of a pool amount: 0, 1, or 2; anything else makes the definition invalid. */
        public Builder decimals(int decimals) {
            this.decimals = decimals;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /** For a mark: on whose HUD it shows; both may be off. */
        public Builder markHud(boolean onSource, boolean onTarget) {
            this.markOnSource = onSource;
            this.markOnTarget = onTarget;
            return this;
        }

        /** Particles on the entity that has the instance (for a mark, its target); none unless set. */
        public Builder particles(Particles particles) {
            this.particles = particles;
            return this;
        }

        public Presentation build() {
            return new Presentation(this);
        }
    }
}
