package com.mahghuuuls.statusworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** The presentation table, sent to a player at login: one row per visible definition. */
public final class TableMessage implements IMessage {

    List<HudDefinition> rows = new ArrayList<>();

    public TableMessage() {
    }

    TableMessage(List<HudDefinition> rows) {
        this.rows = rows;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, rows.size(), 5);
        for (HudDefinition row : rows) {
            row.write(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = ByteBufUtils.readVarInt(buf, 5);
        rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(HudDefinition.read(buf));
        }
    }

    /** Hands the table to the client receiver; a server has none. */
    public static final class Handler implements IMessageHandler<TableMessage, IMessage> {
        @Override
        public IMessage onMessage(TableMessage message, MessageContext ctx) {
            ClientReceiver receiver = StatusworksNetwork.receiver;
            if (receiver != null) {
                receiver.onTable(message.rows);
            }
            return null;
        }
    }
}
