package com.mahghuuuls.statusworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nullable;

/**
 * One row of the presentation table: what a client needs to draw the entries of one visible definition, including
 * its particles. Rows of author-hidden definitions are never built, so they never reach a client. Who sees the
 * particles is the server's decision and is not in the row.
 */
public final class HudDefinition {

    public static final byte STATUS = 0;
    public static final byte POOL = 1;
    public static final byte MARK = 2;
    public static final byte COOLDOWN = 3;
    /** A player's own potion effect: built on the client from vanilla's data, never in the server's table. */
    public static final byte POTION = 4;

    /** The row's index in the table; entries name their definition by it. Lower indexes registered earlier. */
    public final int index;
    public final String id;
    public final byte kind;
    public final String name;
    public final boolean nameIsKey;
    @Nullable public final String description;
    public final boolean descriptionIsKey;
    @Nullable public final String iconItem;
    public final int iconMeta;
    @Nullable public final String iconTexture;
    public final boolean showCount;
    public final boolean showAmount;
    public final boolean showTime;
    public final int decimals;
    public final int priority;
    /** A status's stack limit (the count shows only above 1); 1 for the other kinds. */
    public final int limit;
    /** What the timer means: an expiration mode for a status, or {@code lifetime}, {@code cooldown}, {@code mark}. */
    public final String timerMode;
    /** The particle type, or null for no particles. */
    @Nullable public final String particleType;
    /** The particle color as {@code 0xRRGGBB}, or -1 for the type's own. */
    public final int particleColor;
    public final float particlesPerSecond;
    /** Whether the particles show beyond 32 blocks, as far as the client has the entity. */
    public final boolean particlesFar;
    /** Whether the definition's entries are harmful; for a mark, on the source's HUD. */
    public final boolean harmful;
    /** For a mark: whether it is harmful on the target's HUD. */
    public final boolean harmfulOnTarget;

    public HudDefinition(int index, String id, byte kind, String name, boolean nameIsKey, @Nullable String description,
                         boolean descriptionIsKey, @Nullable String iconItem, int iconMeta,
                         @Nullable String iconTexture, boolean showCount, boolean showAmount, boolean showTime,
                         int decimals, int priority, int limit, String timerMode) {
        this(index, id, kind, name, nameIsKey, description, descriptionIsKey, iconItem, iconMeta, iconTexture,
                showCount, showAmount, showTime, decimals, priority, limit, timerMode, null, -1, 0, false, false, true);
    }

    public HudDefinition(int index, String id, byte kind, String name, boolean nameIsKey, @Nullable String description,
                         boolean descriptionIsKey, @Nullable String iconItem, int iconMeta,
                         @Nullable String iconTexture, boolean showCount, boolean showAmount, boolean showTime,
                         int decimals, int priority, int limit, String timerMode, @Nullable String particleType,
                         int particleColor, float particlesPerSecond, boolean particlesFar, boolean harmful,
                         boolean harmfulOnTarget) {
        this.index = index;
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.nameIsKey = nameIsKey;
        this.description = description;
        this.descriptionIsKey = descriptionIsKey;
        this.iconItem = iconItem;
        this.iconMeta = iconMeta;
        this.iconTexture = iconTexture;
        this.showCount = showCount;
        this.showAmount = showAmount;
        this.showTime = showTime;
        this.decimals = decimals;
        this.priority = priority;
        this.limit = limit;
        this.timerMode = timerMode;
        this.particleType = particleType;
        this.particleColor = particleColor;
        this.particlesPerSecond = particlesPerSecond;
        this.particlesFar = particlesFar;
        this.harmful = harmful;
        this.harmfulOnTarget = harmfulOnTarget;
    }

    void write(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, index, 5);
        ByteBufUtils.writeUTF8String(buf, id);
        buf.writeByte(kind);
        ByteBufUtils.writeUTF8String(buf, name);
        buf.writeBoolean(nameIsKey);
        writeOptional(buf, description);
        buf.writeBoolean(descriptionIsKey);
        writeOptional(buf, iconItem);
        ByteBufUtils.writeVarInt(buf, iconMeta, 5);
        writeOptional(buf, iconTexture);
        buf.writeByte((showCount ? 1 : 0) | (showAmount ? 2 : 0) | (showTime ? 4 : 0) | (harmful ? 8 : 0)
                | (harmfulOnTarget ? 16 : 0));
        buf.writeByte(decimals);
        buf.writeInt(priority);
        ByteBufUtils.writeVarInt(buf, limit, 5);
        ByteBufUtils.writeUTF8String(buf, timerMode);
        writeOptional(buf, particleType);
        if (particleType != null) {
            buf.writeInt(particleColor);
            buf.writeFloat(particlesPerSecond);
            buf.writeBoolean(particlesFar);
        }
    }

    static HudDefinition read(ByteBuf buf) {
        int index = ByteBufUtils.readVarInt(buf, 5);
        String id = ByteBufUtils.readUTF8String(buf);
        byte kind = buf.readByte();
        String name = ByteBufUtils.readUTF8String(buf);
        boolean nameIsKey = buf.readBoolean();
        String description = readOptional(buf);
        boolean descriptionIsKey = buf.readBoolean();
        String iconItem = readOptional(buf);
        int iconMeta = ByteBufUtils.readVarInt(buf, 5);
        String iconTexture = readOptional(buf);
        int flags = buf.readByte();
        int decimals = buf.readByte();
        int priority = buf.readInt();
        int limit = ByteBufUtils.readVarInt(buf, 5);
        String timerMode = ByteBufUtils.readUTF8String(buf);
        String particleType = readOptional(buf);
        int particleColor = -1;
        float particlesPerSecond = 0;
        boolean particlesFar = false;
        if (particleType != null) {
            particleColor = buf.readInt();
            particlesPerSecond = buf.readFloat();
            particlesFar = buf.readBoolean();
        }
        return new HudDefinition(index, id, kind, name, nameIsKey, description, descriptionIsKey, iconItem, iconMeta,
                iconTexture, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, decimals, priority, limit,
                timerMode, particleType, particleColor, particlesPerSecond, particlesFar, (flags & 8) != 0,
                (flags & 16) != 0);
    }

    static void writeOptional(ByteBuf buf, @Nullable String text) {
        buf.writeBoolean(text != null);
        if (text != null) {
            ByteBufUtils.writeUTF8String(buf, text);
        }
    }

    @Nullable
    static String readOptional(ByteBuf buf) {
        return buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
    }
}
