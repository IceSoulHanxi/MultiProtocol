package com.ixnah.mc.multiprotocol.bungee;

import com.ixnah.mc.multiprotocol.MultiProtocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;

import java.lang.reflect.Method;

public class ProtocolInitializer extends ChannelInitializer<Channel> {
    private final ChannelInitializer<Channel> realInitializer;
    private Method initChannel;

    public ProtocolInitializer(ChannelInitializer<Channel> realInitializer) {
        this.realInitializer = realInitializer;
        try {
            initChannel = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
            initChannel.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public ChannelInitializer<Channel> getRealInitializer() {
        return realInitializer;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        for (String name : MultiProtocol.getHandlers().keySet()) {
            channel.pipeline().addFirst(name, MultiProtocol.getHandlers().get(name).get());
        }
        initChannel.invoke(realInitializer, channel);
    }
}
