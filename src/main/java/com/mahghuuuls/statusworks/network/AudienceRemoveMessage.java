package com.mahghuuuls.statusworks.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Drops every entry a player had for one other entity: it left the player's range or tracking, stopped showing
 * particles, or ended.
 */
public final class AudienceRemoveMessage implements IMessage {

    int entity;

    public AudienceRemoveMessage() {
    }

    AudienceRemoveMessage(int entity) {
        this.entity = entity;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entity = buf.readInt();
    }

    /** Hands the removal to the client receiver; a server has none. */
    public static final class Handler implements IMessageHandler<AudienceRemoveMessage, IMessage> {
        @Override
        public IMessage onMessage(AudienceRemoveMessage message, MessageContext ctx) {
            ClientReceiver receiver = StatusworksNetwork.receiver;
            if (receiver != null) {
                receiver.onAudienceRemove(message.entity);
            }
            return null;
        }
    }
}
