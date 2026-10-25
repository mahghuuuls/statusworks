package com.mahghuuuls.statusworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's own entries: a full snapshot (the client drops what it had) at login, respawn, and dimension change,
 * then only the entries that changed and the keys that ended, once per tick at most.
 */
public final class EntriesMessage implements IMessage {

    boolean snapshot;
    List<HudEntry> changed = new ArrayList<>();
    List<String> removed = new ArrayList<>();

    public EntriesMessage() {
    }

    EntriesMessage(boolean snapshot, List<HudEntry> changed, List<String> removed) {
        this.snapshot = snapshot;
        this.changed = changed;
        this.removed = removed;
    }

    @Override
    public void toBytes(ByteBuf buf) {
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
    public static final class Handler implements IMessageHandler<EntriesMessage, IMessage> {
        @Override
        public IMessage onMessage(EntriesMessage message, MessageContext ctx) {
            ClientReceiver receiver = StatusworksNetwork.receiver;
            if (receiver != null) {
                receiver.onEntries(message.snapshot, message.changed, message.removed);
            }
            return null;
        }
    }
}
