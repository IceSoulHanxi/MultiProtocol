package com.ixnah.mc.ws;

import com.ixnah.mc.ws.event.InitChannelEvent;
import com.ixnah.mc.ws.handler.ProtocolSwitchHandler;
import io.netty.channel.Channel;
import net.md_5.bungee.BungeeCord;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 14:50
 */
public class ChannelHandler {

    public static void initChannel(Channel channel) {
        BungeeCord.getInstance().getPluginManager().callEvent(new InitChannelEvent(channel));
        channel.pipeline().addLast("ProtocolSwitch", new ProtocolSwitchHandler());
    }
}
