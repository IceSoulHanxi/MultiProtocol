package com.ixnah.mc.ws.handler;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.protocol.KickStringWriter;
import net.md_5.bungee.protocol.MinecraftEncoder;
import net.md_5.bungee.protocol.Protocol;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 15:30
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final Map<String, String> ipMap = new ConcurrentHashMap<>();
    private static final SecretKey jwtKey = Keys.hmacShaKeyFor(UUID.randomUUID().toString().replace("-", "").getBytes());
    private WebSocketServerHandshaker handshaker;

    public WebSocketServerHandler() {
        super(false);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 推迟ChannelWrapper初始化到setRealRemoteAddress()以修改RemoteAddress
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

        // 仅允许GET请求
        if (httpRequest.method() != GET) {
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        if (!"/".equals(httpRequest.uri().split("\\?")[0])) {
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
            return;
        }

        // 检测是否携带Token(UUID或者Jwt)
        String token = httpRequest.headers().get(AUTHORIZATION);
        if (token == null || token.isEmpty()) {
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
            return;
        }

        // 通过UUID与CDN传来的真实IP生成Jwt
        // 由于Websocket经过CDN后,无法直接获取到客户端的真实IP
        // 所以说需要通过增加一次正常HTTP请求,以通过CDN传来的Header获取客户端的真实IP
        // 目前使用的Jwt来进行参数传递,可能存在IP伪造的情况
        // TODO: HTTP请求改为长链接,直接在同一条Channel上升级为Websocket
        String upgrade = httpRequest.headers().get(UPGRADE);
        if (upgrade == null || upgrade.isEmpty()) {
            try {
                if (token.length() != 32) {
                    sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                    return;
                }
                String id = token.substring(0, 8) + "-" +
                        token.substring(8, 12) + "-" +
                        token.substring(12, 16) + "-" +
                        token.substring(16, 20) + "-" +
                        token.substring(20, 32);
                UUID uuid = UUID.fromString(id);
                byte[] jwt = Jwts.builder().signWith(jwtKey)
                        .setExpiration(new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90)))
                        .claim("UUID", uuid)
                        .claim("IP", getRemoteAddress(ctx, httpRequest))
                        .compact().getBytes();
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
                httpResponse.content().writeBytes(jwt);
                ctx.channel().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
                return;
            } catch (Throwable t) {
                t.printStackTrace();
                sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                return;
            }
        }

        // 目前,从这里开始和前面的HTTP请求不在同一条Channel
        InetAddress realRemoteAddress;
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(jwtKey).build().parseClaimsJws(token).getBody();
            String ip = claims.get("IP", String.class);
            realRemoteAddress = InetAddress.getByName(ip);
            String id = claims.get("UUID", String.class);
            if (!ipMap.getOrDefault(id, "0.0.0.0").equals(ip)) {
                ProxyServer.getInstance().getLogger().info("[WebSocket] Player[" + id + "] connect from " + realRemoteAddress);
                ipMap.put(id, ip);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            sendHttpResponse(ctx, httpRequest, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
            return;
        }

        // Handshake
        WebSocketServerHandshakerFactory handshakerFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(httpRequest), null, true);
        handshaker = handshakerFactory.newHandshaker(httpRequest);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), httpRequest).syncUninterruptibly();
            if (handshakeFuture.isSuccess()) {
                // 握手成功之后,业务逻辑
                if (framePrepender == null) {
                    try {
                        Field framePrependerField = Class.forName("net.md_5.bungee.netty.PipelineUtils")
                                .getDeclaredField("framePrepender");
                        framePrependerField.setAccessible(true);
                        framePrepender = (ChannelHandler) framePrependerField.get(null);
                    } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                setRealRemoteAddress(ctx, realRemoteAddress);
                ctx.channel().pipeline()
                        .addBefore("timeout", "PacketToFrameHandler", new PacketToFrameHandler())
                        .addBefore("inbound-boss", "frame-prepender", framePrepender)
                        .addAfter("frame-prepender", "packet-encoder", new MinecraftEncoder(Protocol.HANDSHAKE, true, ProxyServer.getInstance().getProtocolVersion()))
                        .addBefore("frame-prepender", "legacy-kick", new KickStringWriter());
            } else {
                // 握手失败,输出异常
                handshakeFuture.cause().printStackTrace();
                ctx.channel().closeFuture();
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

        ChannelFuture future = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private final List<ByteBuf> frameContents = new ArrayList<>();

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        frameContents.add(frame.content());
        if (frame.isFinalFragment()) {
            ctx.fireChannelRead(Unpooled.wrappedBuffer(frameContents.toArray(new ByteBuf[0])));
            frameContents.clear();
        }
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        return "ws://" + req.headers().get(HOST); // TODO: 判断是否HTTPS
    }

    private static String getRemoteAddress(ChannelHandlerContext ctx, FullHttpRequest req) {
        String result = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        HttpHeaders header = req.headers();
        for (String key : Arrays.asList("X-Forwarded-For", "X-Real-Ip", "Client-Ip", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR")) {
            if (header.contains(key)) {
                String ip = header.get(key);
                if (ip != null && !ip.isEmpty() && !ip.equalsIgnoreCase("unknown")) {
                    result = ip;
                    break;
                }
            }
        }
        return result.split(",")[0].replace(" ", "");
    }

    private static Field remoteAddressField;

    private static void setRealRemoteAddress(ChannelHandlerContext ctx, InetAddress realRemoteAddress) {
        Channel channel = ctx.channel();
        InetSocketAddress proxyAddress = ((InetSocketAddress) channel.remoteAddress());
        InetSocketAddress realAddress = new InetSocketAddress(realRemoteAddress, proxyAddress.getPort());
        channel.attr(AttributeKey.valueOf("realAddress")).set(realAddress.toString());
        channel.attr(AttributeKey.valueOf("proxyAddress")).set(proxyAddress.toString());
        try {
            if (remoteAddressField == null) {
                remoteAddressField = AbstractChannel.class.getDeclaredField("remoteAddress");
                remoteAddressField.setAccessible(true);
            }
            remoteAddressField.set(channel, realAddress);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        // 在此处向后面的Handler传递Active信号
        ctx.fireChannelActive();
    }
}
