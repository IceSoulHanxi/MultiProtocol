package com.ixnah.mc.ws.agent;

import io.netty.channel.Channel;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 14:50
 * <p>
 * 该类会被挂载到Jvm默认Classloader
 */
public class InitChannelHandler {

    private static Constructor<?> newEvent;

    public static void initChannel(Channel channel) {
        try {
            if (newEvent == null) {
                Plugin plugin = ProxyServer.getInstance().getPluginManager().getPlugin("BungeeWebsocket");
                String eventClassName = "com.ixnah.mc.ws.event.InitChannelEvent";
                newEvent = Class.forName(eventClassName, true, plugin.getClass().getClassLoader())
                        .getConstructor(Channel.class);
            }
            ProxyServer.getInstance().getPluginManager().callEvent((Event) newEvent.newInstance(channel));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
