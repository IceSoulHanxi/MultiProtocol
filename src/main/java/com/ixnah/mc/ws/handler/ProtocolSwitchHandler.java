package com.ixnah.mc.ws.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

import java.util.Arrays;
import java.util.List;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 14:58
 */
public class ProtocolSwitchHandler extends SimpleChannelInboundHandler<Object> {

    private static final String GET = "GET";
    private static final List<String> removeChannelList = Arrays.asList("frame-prepender", "packet-encoder", "legacy-kick");

    private static final boolean debug = true;

    public ProtocolSwitchHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            byte[] checkBytes = new byte[GET.length()];
            ((ByteBuf) msg).markReaderIndex().readBytes(checkBytes).resetReaderIndex();
            if (GET.equalsIgnoreCase(new String(checkBytes))) {
//                System.out.println("[BungeeWebsocket] Detect HTTP header! " + ctx.channel().remoteAddress());
                if (debug) {
                    String context = ((ByteBuf) msg).markReaderIndex().toString();
                    ((ByteBuf) msg).resetReaderIndex();
                    System.out.println(context);
                }
                for (String removeChannel : removeChannelList) {
                    ctx.pipeline().remove(removeChannel);
                }
                ctx.channel().pipeline()
                        .addAfter("ProtocolSwitch","WebSocketServerHandler", new WebSocketServerHandler())
//                        .addAfter("ProtocolSwitch","WebSocketServerCompressionHandler", new WebSocketServerCompressionHandler())
                        .addAfter("ProtocolSwitch","HttpObjectAggregator", new HttpObjectAggregator(65536))
                        .addAfter("ProtocolSwitch","HttpServerCodec", new HttpServerCodec());
            }
        }
        ctx.channel().pipeline().remove(this);
        ctx.fireChannelRead(msg);
    }
}
