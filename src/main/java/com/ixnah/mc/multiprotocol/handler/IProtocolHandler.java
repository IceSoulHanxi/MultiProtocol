package com.ixnah.mc.multiprotocol.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public interface IProtocolHandler {
    boolean isProtocol(ByteBuf buffer);
    void handleProtocol(ChannelHandlerContext ctx, String baseName);
}
