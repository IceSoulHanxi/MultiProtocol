package com.ixnah.mc.multiprotocol.spigot;

import com.comphenix.protocol.injector.netty.ChannelListener;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.Injector;
import com.comphenix.protocol.injector.server.TemporaryPlayerFactory;
import com.ixnah.mc.multiprotocol.MultiProtocol;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtocolInjectionFactory extends InjectionFactory {
    private final InjectionFactory realFactory;

    public ProtocolInjectionFactory(InjectionFactory realFactory) {
        super(realFactory.getPlugin());
        this.realFactory = realFactory;
    }

    public InjectionFactory getRealFactory() {
        return realFactory;
    }

    @Override
    public Plugin getPlugin() {
        return realFactory.getPlugin();
    }

    @Override
    public Injector fromPlayer(Player player, ChannelListener listener) {
        return realFactory.fromPlayer(player, listener);
    }

    @Override
    public Injector fromName(String name, Player player) {
        return realFactory.fromName(name, player);
    }

    @Override
    public Injector fromChannel(Channel channel, ChannelListener listener, TemporaryPlayerFactory playerFactory) {
        for (String name : MultiProtocol.getHandlers().keySet()) {
            channel.pipeline().addFirst(name, MultiProtocol.getHandlers().get(name).get());
        }
        return realFactory.fromChannel(channel, listener, playerFactory);
    }

    @Override
    public Injector invalidate(Player player) {
        return realFactory.invalidate(player);
    }

    @Override
    public Injector cacheInjector(Player player, Injector injector) {
        return realFactory.cacheInjector(player, injector);
    }

    @Override
    public Injector cacheInjector(String name, Injector injector) {
        return realFactory.cacheInjector(name, injector);
    }

    @Override
    public boolean isClosed() {
        return realFactory.isClosed();
    }

    @Override
    public void close() {
        realFactory.close();
    }
}
