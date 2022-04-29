package com.ixnah.mc.multiprotocol.handler.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;

import java.util.List;

public class PacketToFrameHandler extends MessageToMessageEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        int maxFrameSize = 65536, length = msg.readableBytes();
        if (length < maxFrameSize) {
            out.add(new BinaryWebSocketFrame(msg.retain()));
        } else {
            out.add(new BinaryWebSocketFrame(false, 0, msg.readRetainedSlice(maxFrameSize)));
            for (int i = 1, size = (int) Math.ceil(length / (double) maxFrameSize) - 1; i < size; i++) {
                out.add(new ContinuationWebSocketFrame(false, 0, msg.readRetainedSlice(maxFrameSize)));
            }
            out.add(new ContinuationWebSocketFrame(true, 0, msg.readRetainedSlice(msg.readableBytes())));
        }
    }
}
