package com.ixnah.mc.ws.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.netty.PipelineUtils;
import net.md_5.bungee.protocol.KickStringWriter;
import net.md_5.bungee.protocol.MinecraftEncoder;
import net.md_5.bungee.protocol.Protocol;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 15:30
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;

    public WebSocketServerHandler() {
        super(false);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private static ChannelHandler framePrepender;

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest httpRequest) {
        // Handle a bad request.
        if (!httpRequest.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        // Allow only GET methods.
        if (httpRequest.method() != GET) {
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        if (!"/".equals(httpRequest.uri())) {
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
            return;
        }

        String upgrade;
        if ((upgrade = httpRequest.headers().get(UPGRADE)) == null || upgrade.isEmpty()) {

            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, OK));
            return;
        }

        // Handshake
        WebSocketServerHandshakerFactory handshakerFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(httpRequest), null, true);
        handshaker = handshakerFactory.newHandshaker(httpRequest);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            ChannelFuture channelFuture = handshaker.handshake(ctx.channel(), httpRequest);
            // 握手成功之后,业务逻辑
            if (channelFuture.syncUninterruptibly().isSuccess()) {
                if (framePrepender == null) {
                    try {
                        Field framePrependerField = PipelineUtils.class.getDeclaredField("framePrepender");
                        framePrependerField.setAccessible(true);
                        framePrepender = (ChannelHandler) framePrependerField.get(null);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                ctx.channel().pipeline()
                        .addBefore("timeout","PacketToFrameHandler", new PacketToFrameHandler())
                        .addBefore("inbound-boss","frame-prepender", framePrepender)
                        .addAfter("frame-prepender", "packet-encoder", new MinecraftEncoder(Protocol.HANDSHAKE, true, ProxyServer.getInstance().getProtocolVersion()))
                        .addBefore("frame-prepender", "legacy-kick", new KickStringWriter());
            }
        }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }

        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static final List<ByteBuf> frameContents = new ArrayList<>();

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        synchronized (frameContents) { // TODO: 如果在同一线程则不用加锁
            frameContents.add(frame.content());
            if (frame.isFinalFragment()) {
                ctx.fireChannelRead(Unpooled.wrappedBuffer(frameContents.toArray(new ByteBuf[0])));
                frameContents.clear();
            }
        }
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        return "ws://" + req.headers().get(HOST); // TODO: 判断是否HTTPS
    }
}
