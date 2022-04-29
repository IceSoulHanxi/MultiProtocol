package com.ixnah.mc.multiprotocol;

import com.ixnah.mc.multiprotocol.data.ProtocolConfig;
import io.netty.channel.ChannelHandler;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class MultiProtocol {
    private static ProtocolConfig config;
    private static Logger logger;
    private static String serverName;
    private static File dataFolder;
    private static final Map<String, Supplier<ChannelHandler>> handlers = new LinkedHashMap<>(8);

    private MultiProtocol() {
    }

    public static ProtocolConfig getConfig() {
        return config;
    }

    static void setConfig(ProtocolConfig config) {
        MultiProtocol.config = config;
    }

    public static Logger getLogger() {
        return logger;
    }

    static void setLogger(Logger logger) {
        MultiProtocol.logger = logger;
    }

    public static String getServerName() {
        return serverName;
    }

    public static void setServerName(String serverName) {
        MultiProtocol.serverName = serverName;
    }

    public static File getDataFolder() {
        return dataFolder;
    }

    public static void setDataFolder(File dataFolder) {
        MultiProtocol.dataFolder = dataFolder;
    }

    public static Map<String, Supplier<ChannelHandler>> getHandlers() {
        return handlers;
    }
}
