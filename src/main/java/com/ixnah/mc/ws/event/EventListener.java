package com.ixnah.mc.ws.event;

import com.ixnah.mc.ws.handler.ProtocolSwitchHandler;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/7/7 9:33
 */
public class EventListener implements Listener {

    @EventHandler
    public void onChannelInit(InitChannelEvent event) {
        event.getChannel().pipeline().addLast("ProtocolSwitch", new ProtocolSwitchHandler());
    }
}
