package com.ixnah.mc.multiprotocol;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.ProtocolInjector;
import com.ixnah.mc.multiprotocol.data.ProtocolConfig;
import com.ixnah.mc.multiprotocol.handler.MultiProtocolHandler;
import com.ixnah.mc.multiprotocol.spigot.ProtocolInjectionFactory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class MultiProtocolSpigot extends JavaPlugin {

    private ProtocolInjector nettyInjector;
    private Field injectorFactoryField;
    private ProtocolInjectionFactory injectionFactory;

    @Override
    public void onEnable() {
        MultiProtocol.setLogger(getLogger());
        MultiProtocol.setServerName(Bukkit.getName());
        MultiProtocol.setDataFolder(this.getDataFolder());
        MultiProtocol.getHandlers().put(MultiProtocolHandler.class.getSimpleName(), MultiProtocolHandler::new);
        this.saveDefaultConfig();
        try (FileInputStream fis = new FileInputStream(new File(this.getDataFolder(), "config.yml"))) {
            Yaml yaml = new Yaml(new CustomClassLoaderConstructor(this.getClass().getClassLoader()));
            MultiProtocol.setConfig(yaml.loadAs(fis, ProtocolConfig.class));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            Class<? extends ProtocolManager> protocolManagerClass = protocolManager.getClass();
            Field nettyInjectorField = protocolManagerClass.getDeclaredField("nettyInjector");
            nettyInjectorField.setAccessible(true);
            nettyInjector = (ProtocolInjector) nettyInjectorField.get(protocolManager);
            injectorFactoryField = nettyInjector.getClass().getDeclaredField("injectionFactory");
            injectorFactoryField.setAccessible(true);
            InjectionFactory originalFactory = (InjectionFactory) injectorFactoryField.get(nettyInjector);
            injectionFactory = new ProtocolInjectionFactory(originalFactory);
            injectorFactoryField.set(nettyInjector, injectionFactory);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (nettyInjector == null || injectionFactory == null || injectorFactoryField == null) return;
        try {
            injectorFactoryField.set(nettyInjector, injectionFactory.getRealFactory());
            nettyInjector = null;
            injectorFactoryField = null;
            injectionFactory = null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
