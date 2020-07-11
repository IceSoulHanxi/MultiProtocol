package com.ixnah.mc.ws.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.util.Arrays;
import java.util.List;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 14:58
 */
public class ProtocolSwitchHandler extends SimpleChannelInboundHandler<Object> {

    private static final List<String> removeChannelList = Arrays.asList("frame-prepender", "packet-encoder", "legacy-kick");
    private static final List<String> httpMethods = Arrays.asList("OPTIONS", "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "TRACE", "CONNECT");

    public ProtocolSwitchHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf && isHttpRequest((ByteBuf) msg)) {
            ctx.channel().config().setOption(ChannelOption.SO_KEEPALIVE, true); // 设置为长链接
            for (String removeChannel : removeChannelList)
                ctx.pipeline().remove(removeChannel); // 暂时删除MC编码器
            ctx.pipeline() // 添加HTTP/WebSocket处理器
                    .addAfter("ProtocolSwitch", "WebSocketServerHandler", new WebSocketServerHandler())
                    .addAfter("ProtocolSwitch", "HttpObjectAggregator", new HttpObjectAggregator(65536))
                    .addAfter("ProtocolSwitch", "HttpServerCodec", new HttpServerCodec());
        }
        ctx.pipeline().remove(this); // 删除当前协议切换处理器
        ctx.fireChannelRead(msg);
    }

    private boolean isHttpRequest(ByteBuf msg) {
        msg.markReaderIndex();
        int available = Math.min(10, msg.readableBytes());
        if (available == 0) {
            return false;
        }
        StringBuilder methodBuf = new StringBuilder();
        do {
            int c = msg.readUnsignedByte();
            --available;
            if (c == ' ') {
                break;
            }
            methodBuf.append((char) c);
        } while (available > 0);
        msg.resetReaderIndex();
        return httpMethods.contains(methodBuf.toString().toUpperCase());
    }
}
