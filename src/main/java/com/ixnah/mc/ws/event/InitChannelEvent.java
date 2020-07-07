package com.ixnah.mc.ws.event;

import io.netty.channel.Channel;
import net.md_5.bungee.api.plugin.Event;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 15:14
 */
public class InitChannelEvent extends Event {

    private final Channel channel;

    public InitChannelEvent(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }
}
