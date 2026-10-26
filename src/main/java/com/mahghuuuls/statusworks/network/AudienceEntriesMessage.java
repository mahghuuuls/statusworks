package com.mahghuuuls.statusworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The entries of one entity that a player may see because they have particles: a full snapshot (the client drops
 * what it had for that entity), then only what changed; {@link AudienceRemoveMessage} drops them all. Entities are
 * named by their network entity id, which the client resolves in its own world.
 */
public final class AudienceEntriesMessage implements IMessage {

    int entity;
    boolean snapshot;
    List<HudEntry> changed = new ArrayList<>();
    List<String> removed = new ArrayList<>();

    public AudienceEntriesMessage() {
    }

    AudienceEntriesMessage(int entity, boolean snapshot, List<HudEntry> changed, List<String> removed) {
        this.entity = entity;
        this.snapshot = snapshot;
        this.changed = changed;
        this.removed = removed;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity);
        buf.writeBoolean(snapshot);
        ByteBufUtils.writeVarInt(buf, changed.size(), 5);
        for (HudEntry e : changed) {
            e.write(buf);
        }
        ByteBufUtils.writeVarInt(buf, removed.size(), 5);
        for (String key : removed) {
            ByteBufUtils.writeUTF8String(buf, key);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entity = buf.readInt();
        snapshot = buf.readBoolean();
        int n = ByteBufUtils.readVarInt(buf, 5);
        changed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            changed.add(HudEntry.read(buf));
        }
        int m = ByteBufUtils.readVarInt(buf, 5);
        removed = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            removed.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    /** Hands the entries to the client receiver; a server has none. */
    public static final class Handler implements IMessageHandler<AudienceEntriesMessage, IMessage> {
        @Override
        public IMessage onMessage(AudienceEntriesMessage message, MessageContext ctx) {
            ClientReceiver receiver = StatusworksNetwork.receiver;
            if (receiver != null) {
                receiver.onAudience(message.entity, message.snapshot, message.changed, message.removed);
            }
            return null;
        }
    }
}
