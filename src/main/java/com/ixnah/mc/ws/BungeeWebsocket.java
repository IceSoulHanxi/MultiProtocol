package com.ixnah.mc.ws;

import com.ixnah.mc.ws.event.EventListener;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/30 16:24
 */
public class BungeeWebsocket extends Plugin {

    @Override
    public void onLoad() {
        tryAttach();
        ProxyServer.getInstance().getPluginManager().registerListener(this, new EventListener());
    }

    public static void tryAttach() {
        VirtualMachine machine = null;
        try {
            machine = VirtualMachine.attach(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
            String path = BungeeWebsocket.class.getProtectionDomain().getCodeSource().getLocation().getPath().split("!")[0]
                    .replace("file:", "").replace("jar:", "");
            machine.loadAgent(new File(URLDecoder.decode(path, "UTF-8")).getAbsolutePath());
        } catch (AttachNotSupportedException | IOException | AgentLoadException | AgentInitializationException e) {
            ProxyServer.getInstance().getLogger().severe("[BungeeWebsocket] Only supports JDK! add JVM option -Djdk.attach.allowAttachSelf=true");
            e.printStackTrace();
        } finally {
            if (machine != null) {
                try {
                    machine.detach();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
