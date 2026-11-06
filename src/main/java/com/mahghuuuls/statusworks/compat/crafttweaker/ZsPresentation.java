package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.definition.Particles;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * How a script definition looks: the Java {@code Presentation} with its particles flattened into the same builder.
 * Create one with {@code Presentation.create()} and give it to a definition builder's {@code presentation(...)}.
 */
@ZenRegister
@ZenClass("mods.statusworks.Presentation")
public final class ZsPresentation {

    private final Presentation.Builder b = Presentation.builder();
    private String particleType;
    private int[] particleColor;
    private Double particlesPerSecond;
    private String particleAudience;
    private boolean particlesLongDistance;

    @ZenMethod
    public static ZsPresentation create() {
        return new ZsPresentation();
    }

    @ZenMethod
    public ZsPresentation hidden() {
        b.hidden();
        return this;
    }

    @ZenMethod
    public ZsPresentation name(String text) {
        b.name(text);
        return this;
    }

    @ZenMethod
    public ZsPresentation nameKey(String key) {
        b.nameKey(key);
        return this;
    }

    @ZenMethod
    public ZsPresentation description(String text) {
        b.description(text);
        return this;
    }

    @ZenMethod
    public ZsPresentation descriptionKey(String key) {
        b.descriptionKey(key);
        return this;
    }

    @ZenMethod
    public ZsPresentation iconItem(String id, int meta) {
        b.iconItem(id, meta);
        return this;
    }

    @ZenMethod
    public ZsPresentation iconTexture(String resourceLocation) {
        b.iconTexture(resourceLocation);
        return this;
    }

    @ZenMethod
    public ZsPresentation showCount(boolean show) {
        b.showCount(show);
        return this;
    }

    @ZenMethod
    public ZsPresentation showAmount(boolean show) {
        b.showAmount(show);
        return this;
    }

    @ZenMethod
    public ZsPresentation showTime(boolean show) {
        b.showTime(show);
        return this;
    }

    @ZenMethod
    public ZsPresentation decimals(int decimals) {
        b.decimals(decimals);
        return this;
    }

    @ZenMethod
    public ZsPresentation priority(int priority) {
        b.priority(priority);
        return this;
    }

    @ZenMethod
    public ZsPresentation markHud(boolean onSource, boolean onTarget) {
        b.markHud(onSource, onTarget);
        return this;
    }

    /** Makes the entry harmful (a debuff) wherever it shows, for a mark on both HUDs. */
    @ZenMethod
    public ZsPresentation harmful() {
        b.harmful();
        return this;
    }

    /**
     * For a mark: whether it is harmful on the source's HUD and on the target's HUD (default false, true). On a
     * status, pool, or cooldown, {@code onSource} sets its value and {@code onTarget} has no use.
     */
    @ZenMethod
    public ZsPresentation markHarmful(boolean onSource, boolean onTarget) {
        b.markHarmful(onSource, onTarget);
        return this;
    }

    /** Turns particles on with a vanilla particle name, for example {@code mobSpell}. */
    @ZenMethod
    public ZsPresentation particles(String type) {
        this.particleType = type;
        return this;
    }

    /** A particle color from red, green, and blue, each 0 to 255. */
    @ZenMethod
    public ZsPresentation particleColor(int red, int green, int blue) {
        this.particleColor = new int[]{red, green, blue};
        return this;
    }

    @ZenMethod
    public ZsPresentation particlesPerSecond(double rate) {
        this.particlesPerSecond = rate;
        return this;
    }

    /** {@code nearby}, {@code source}, {@code target}, or {@code both}. */
    @ZenMethod
    public ZsPresentation particleAudience(String audience) {
        this.particleAudience = audience;
        return this;
    }

    @ZenMethod
    public ZsPresentation particlesLongDistance() {
        this.particlesLongDistance = true;
        return this;
    }

    Presentation build() {
        if (particleType != null) {
            Particles.Builder p = Particles.builder(particleType);
            if (particleColor != null) {
                p.color(particleColor[0], particleColor[1], particleColor[2]);
            }
            if (particlesPerSecond != null) {
                p.perSecond(particlesPerSecond);
            }
            if (particleAudience != null) {
                p.audience(particleAudience);
            }
            if (particlesLongDistance) {
                p.longDistance();
            }
            b.particles(p.build());
        }
        return b.build();
    }
}
