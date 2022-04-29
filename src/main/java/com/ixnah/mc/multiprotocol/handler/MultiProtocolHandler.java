package com.ixnah.mc.multiprotocol.handler;

import com.ixnah.mc.multiprotocol.MultiProtocol;
import com.ixnah.mc.multiprotocol.data.ProtocolConfig;
import com.ixnah.mc.multiprotocol.handler.ws.ServerWsHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Map;

public class MultiProtocolHandler extends SimpleChannelInboundHandler<Object> {
    private final ProtocolConfig config;
    private final boolean useSsl;

    public MultiProtocolHandler() {
        super(false);
        this.config = MultiProtocol.getConfig();
        useSsl = config.getWebsocket().getSsl().isEnabled();
    }

    public ProtocolConfig getConfig() {
        return config;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buffer = (ByteBuf) msg;
            if (useSsl) {
                // TODO: Check SSL
            } else if (getConfig().getWebsocket().isEnabled()) {
                ServerWsHandler wsHandler = new ServerWsHandler(getConfig().getWebsocket());
                if (wsHandler.isProtocol(buffer)) {
                    wsHandler.handleProtocol(ctx, getCurrentName(ctx));
                }
            }
        }
        ctx.pipeline().remove(this); // 删除当前协议切换处理器
        ctx.fireChannelRead(msg);
    }

    private String getCurrentName(ChannelHandlerContext ctx) {
        for (Map.Entry<String, ChannelHandler> entry : ctx.pipeline()) {
            if (this.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("The handler is not on the pipeline!");
    }
}
