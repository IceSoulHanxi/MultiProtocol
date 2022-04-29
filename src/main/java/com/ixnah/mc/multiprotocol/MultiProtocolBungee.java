package com.ixnah.mc.multiprotocol;

import com.ixnah.mc.multiprotocol.bungee.ProtocolInitializer;
import com.ixnah.mc.multiprotocol.data.ProtocolConfig;
import com.ixnah.mc.multiprotocol.handler.MultiProtocolHandler;
import com.ixnah.mc.multiprotocol.util.ReflectionHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.logging.Level;

public final class MultiProtocolBungee extends Plugin {
    private Field serverChildField;
    private ProtocolInitializer protocolInitializer;

    @Override
    public void onEnable() {
        MultiProtocol.setLogger(getLogger());
        MultiProtocol.setServerName(ProxyServer.getInstance().getName());
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
            Class<?> pipelineUtilsClass = Class.forName("net.md_5.bungee.netty.PipelineUtils");
            serverChildField = pipelineUtilsClass.getDeclaredField("SERVER_CHILD");
            @SuppressWarnings("unchecked")
            ChannelInitializer<Channel> originInitializer = (ChannelInitializer<Channel>) serverChildField.get(null);
            protocolInitializer = new ProtocolInitializer(originInitializer);
            ReflectionHelper.setModifiers(serverChildField, serverChildField.getModifiers() & ~Modifier.FINAL);
            serverChildField.setAccessible(true);
            serverChildField.set(null, protocolInitializer);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (serverChildField == null || protocolInitializer == null) return;
        try {
            serverChildField.set(null, protocolInitializer.getRealInitializer());
            serverChildField = null;
            protocolInitializer = null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void saveDefaultConfig() {
        String configFileName = "config.yml";
        File configFile = new File(this.getDataFolder(), configFileName);
        if (configFile.exists()) return;
        try (InputStream stream = getResourceAsStream(configFileName)) {
            if (!configFile.getParentFile().isDirectory() && !configFile.getParentFile().mkdirs())
                throw new IOException("Can't create plugin data folder!");
            Files.copy(stream, configFile.toPath());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save " + configFile.getName() + " to " + configFile, e);
        }
    }
}
