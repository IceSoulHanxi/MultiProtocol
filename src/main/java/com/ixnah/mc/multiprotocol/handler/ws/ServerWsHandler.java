package com.ixnah.mc.multiprotocol.handler.ws;

import com.ixnah.mc.multiprotocol.MultiProtocol;
import com.ixnah.mc.multiprotocol.data.SslConfig;
import com.ixnah.mc.multiprotocol.data.WsConfig;
import com.ixnah.mc.multiprotocol.data.WsFallbackConfig;
import com.ixnah.mc.multiprotocol.handler.IProtocolHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;

import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.Objects.requireNonNull;

public class ServerWsHandler extends SimpleChannelInboundHandler<Object> implements IProtocolHandler {
    private static final List<String> httpMethods = Arrays.asList("OPTIONS", "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "TRACE", "CONNECT");
    private static final String REAL_ADDRESS = "realAddress";
    private static final String PROXY_ADDRESS = "proxyAddress";
    private static final AttributeKey<String> REAL_ADDRESS_KEY = AttributeKey.valueOf(REAL_ADDRESS);
    private static final AttributeKey<String> PROXY_ADDRESS_KEY = AttributeKey.valueOf(PROXY_ADDRESS);
    private static final AttributeKey<String> UUID_KEY = AttributeKey.valueOf("UUID");
    private static final String BEARER_TOKEN = "Bearer ";
    private final SecretKey secretKey = Keys.hmacShaKeyFor(new SecureRandom().generateSeed(32));
    private final WsConfig config;
    private final boolean useSsl;
    private WebSocketServerHandshaker handshaker;

    public ServerWsHandler(WsConfig config) {
        super(false);
        this.config = config;
        useSsl = config.getSsl().isEnabled();
    }

    public WsConfig getConfig() {
        return config;
    }

    @Override
    public boolean isProtocol(ByteBuf buffer) {
        buffer.markReaderIndex();
        int available = Math.min(10, buffer.readableBytes());
        if (available == 0) {
            return false;
        }
        StringBuilder methodBuf = new StringBuilder();
        do {
            int c = buffer.readUnsignedByte();
            --available;
            if (c == ' ') {
                break;
            }
            methodBuf.append((char) c);
        } while (available > 0);
        buffer.resetReaderIndex();
        return httpMethods.contains(methodBuf.toString().toUpperCase());
    }

    @Override
    public void handleProtocol(ChannelHandlerContext ctx, String baseName) {
        ctx.channel().config().setOption(ChannelOption.SO_KEEPALIVE, true); // 设置为默认长链接
        ctx.pipeline()
                .addAfter(baseName, ServerWsHandler.class.getSimpleName(), this)
                .addAfter(baseName, HttpObjectAggregator.class.getSimpleName(), new HttpObjectAggregator(getConfig().getMaxContentLength()))
                .addAfter(baseName, HttpServerCodec.class.getSimpleName(), new HttpServerCodec());
        SslConfig sslConfig = getConfig().getSsl();
        if (sslConfig.isEnabled()) {
            Path dataFolder = MultiProtocol.getDataFolder().toPath();
            try (InputStream keyStoreStream = Files.newInputStream(dataFolder.resolve(sslConfig.getKeyStore()))) {
                char[] keyStorePassword = sslConfig.getKeyStorePassword().toCharArray();
                KeyStore keyStore = KeyStore.getInstance(sslConfig.getKeyStoreType());
                keyStore.load(keyStoreStream, keyStorePassword);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
                keyManagerFactory.init(keyStore, keyStorePassword);
                SslContext sslContext = SslContextBuilder.forServer(keyManagerFactory).build();
                SslHandler sslHandler = new SslHandler(sslContext.newEngine(ctx.alloc()));
                ctx.pipeline().addAfter(baseName, SslHandler.class.getSimpleName(), sslHandler);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw new RuntimeException("SSL failed to load!", throwable);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.channel().closeFuture();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!getConfig().isIpFromHttp()) { // 后续Handler初始化推迟到Websocket握手完成后以修改RemoteAddress
            super.channelActive(ctx);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            MultiProtocol.getLogger().warning("Unprocessed packets " + msg.toString());
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).release();
            }
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest msg) throws MalformedURLException {
        // Handle a bad request.
        if (!msg.decoderResult().isSuccess()) {
            sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }
        HttpMethod method = msg.method();
        HttpHeaders headers = msg.headers();
        String host = headers.get(HOST, "localhost");
        String schema = useSsl ? "https" : "http";
        URL url = new URL(schema + "://" + host + msg.uri());
        String wsPath = getConfig().getPath();
        wsPath = wsPath.startsWith("/") ? wsPath : "/" + wsPath;
        if (!url.getPath().equals(wsPath)) {
            WsFallbackConfig fallback = getConfig().getFallback();
            String fallbackUrl = fallback.getUrl();
            if (!fallback.isEnabled() || fallbackUrl == null || fallbackUrl.trim().isEmpty()) {
                sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
                return;
            }
//            int subIndex = '/' == fallbackUrl.charAt(Math.max(fallbackUrl.indexOf("${uri}") - 1, 0)) ? 1 : 0;
            fallbackUrl = fallbackUrl.replace("${uri}", msg.uri().substring(1)).trim();
            switch (fallback.getType()) {
                case redirect: {
                    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, TEMPORARY_REDIRECT);
                    response.headers().set(LOCATION, fallbackUrl);
                    sendResponse(ctx, msg, response);
                    return;
                }
                case proxy: {
                    // TODO: 反代回落
                }
            }
        }

        // 后续内容仅支持GET请求
        if (!method.equals(GET)) {
            sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED));
            return;
        }

        String upgrade = headers.get(UPGRADE);
        String realAddress = getRealAddress(headers, getConfig().getRealIpHeaders());
        if (getConfig().isIpFromHttp()) {
            // 检测是否携带Token(UUID或者Jwt)
            String token = headers.get(AUTHORIZATION);
            token = token != null ? token : "";
            token = (token.startsWith(BEARER_TOKEN) ? token.substring(BEARER_TOKEN.length()) : token).trim();
            if (token.isEmpty()) {
                sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                return;
            }

            if (!"websocket".equalsIgnoreCase(upgrade)) { // 如果还是HTTP请求
                if (realAddress == null || realAddress.isEmpty()) { // 没有提供真实地址
                    sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
                    return;
                }
                if (token.length() < 32 || token.length() > 36) { // Token不为UUID
                    sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
                    return;
                }
                if ('-' != token.charAt(8)) { // 如果不是带连接符的UUID
                    token = token.substring(0, 8) + "-" + token.substring(8, 12) + "-" + token.substring(12, 16) + "-"
                            + token.substring(16, 20) + "-" + token.substring(20, 32);
                }
                try {
                    UUID uuid = UUID.fromString(token);
                    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
                    String jwt = Jwts.builder().signWith(secretKey).setNotBefore(new Date())
                            .setExpiration(new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)))
                            .setId(uuid.toString()).claim(REAL_ADDRESS, realAddress).compact();
                    response.content().writeCharSequence(jwt, UTF_8);
                    response.headers().set(CONTENT_TYPE, "text/plain");
                    sendResponse(ctx, msg, response);
                    return;
                } catch (Throwable t) {
                    t.printStackTrace();
                    sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR));
                    return;
                }
            } else { // 如果是Websocket握手请求 从这里开始和前面的HTTP请求可能不在同一条Channel
                try {
                    Claims claims = Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
                    String uuid = ctx.channel().attr(UUID_KEY).get();
                    if (uuid == null || uuid.isEmpty()) {
                        ctx.channel().attr(UUID_KEY).set(claims.getId());
                    }
                    realAddress = claims.get(REAL_ADDRESS, String.class);
                } catch (Throwable t) {
                    t.printStackTrace();
                    sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                    return;
                }
            }
        }

        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (realAddress == null && remoteAddress instanceof InetSocketAddress) {
            realAddress = ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
        } else {
            sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        if (!"websocket".equalsIgnoreCase(upgrade)) {
            sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, UPGRADE_REQUIRED));
            return;
        }

        // Handshake
        String wsUri = schema.replace("http", "ws") + "://" + host + wsPath;
        WebSocketServerHandshakerFactory handshakerFactory = new WebSocketServerHandshakerFactory(wsUri, "Minecraft", true);
        handshaker = handshakerFactory.newHandshaker(msg);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            try {
                ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), msg).syncUninterruptibly();
                if (!handshakeFuture.isSuccess()) { // 如果握手失败 抛出异常
                    throw handshakeFuture.cause();
                }
                if (getConfig().isIpFromHttp()) {
                    setRealAddress(ctx, realAddress); // 设置真实IP
                    ctx.fireChannelActive(); // 在此处向后面的Handler传递Active信号
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                sendResponse(ctx, msg, new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR));
            }
        }
    }

    private final List<ByteBuf> frameContents = new ArrayList<>();

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        frameContents.add(frame.content());
        if (frame.isFinalFragment()) {
            ctx.fireChannelRead(Unpooled.wrappedBuffer(frameContents.toArray(new ByteBuf[0])));
            frameContents.clear();
        }
    }

    private static ChannelFuture sendResponse(ChannelHandlerContext ctx, HttpRequest request, FullHttpResponse response) {
        if (!response.status().equals(NO_CONTENT) && response.content().readableBytes() == 0) {
            response.headers().set(CONTENT_TYPE, "text/plain");
            response.content().writeCharSequence(response.status().toString(), UTF_8);
        }
        if (!response.headers().contains(CACHE_CONTROL)) {
            response.headers().set(CACHE_CONTROL, "no-cache");
        }
        response.headers().set(SERVER, MultiProtocol.getServerName());
        HttpUtil.setContentLength(response, response.content().readableBytes());
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
            return ctx.writeAndFlush(response);
        } else {
            return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static String getRealAddress(HttpHeaders headers, List<String> realIpHeaders) {
        for (String header : realIpHeaders) {
            String realAddress = headers.get(header);
            if (realAddress == null || realAddress.trim().isEmpty()) continue;
            if (realAddress.contains(",")) {
                realAddress = realAddress.split(",", 1)[0].trim();
            }
            if (realAddress.isEmpty() || "unknown".equalsIgnoreCase(realAddress)) continue;
            return realAddress.trim();
        }
        return null;
    }

    private static Field remoteAddressField;

    private static void setRealAddress(ChannelHandlerContext ctx, String realAddress) {
        requireNonNull(realAddress, "Real address is null!");
        requireNonNull(ctx, "ChannelHandlerContext is null!");
        Channel channel = ctx.channel();
        InetSocketAddress proxyRemoteAddress = ((InetSocketAddress) channel.remoteAddress());
        InetSocketAddress realRemoteAddress = new InetSocketAddress(realAddress, proxyRemoteAddress.getPort());
        channel.attr(PROXY_ADDRESS_KEY).set(proxyRemoteAddress.toString());
        channel.attr(REAL_ADDRESS_KEY).set(realRemoteAddress.toString());
        try {
            if (remoteAddressField == null) {
                remoteAddressField = AbstractChannel.class.getDeclaredField("remoteAddress");
                remoteAddressField.setAccessible(true);
            }
            remoteAddressField.set(channel, realRemoteAddress);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
