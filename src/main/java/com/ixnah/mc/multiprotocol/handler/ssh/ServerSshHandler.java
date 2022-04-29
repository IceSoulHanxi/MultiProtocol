package com.ixnah.mc.multiprotocol.handler.ssh;

import com.ixnah.mc.multiprotocol.handler.IProtocolHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ServerSshHandler extends SimpleChannelInboundHandler<Object> implements IProtocolHandler {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {

    }

    @Override
    public boolean isProtocol(ByteBuf buffer) {
        return false;
    }

    @Override
    public void handleProtocol(ChannelHandlerContext ctx, String baseName) {

    }
}
