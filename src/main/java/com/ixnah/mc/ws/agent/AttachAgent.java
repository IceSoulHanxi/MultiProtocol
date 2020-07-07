package com.ixnah.mc.ws.agent;

import net.md_5.bungee.netty.PipelineUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 13:10
 * <p>
 * 该类会被挂载到Jvm默认Classloader
 */
public class AttachAgent {

    @SuppressWarnings("unchecked")
    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[BungeeWebsocket] AttachAgent loaded!");
        String bungeeCordPath = PipelineUtils.Base.class.getProtectionDomain().getCodeSource().getLocation().getPath()
                .split("!")[0].replace("file:", "").replace("jar:", "");
        InputStream stream = PipelineUtils.Base.class.getClassLoader().getResourceAsStream("PipelineUtils$Base.class");
        ClassReader reader = null;
        try (JarFile jarFile = new JarFile(URLDecoder.decode(bungeeCordPath, "UTF-8"))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().equals("net/md_5/bungee/netty/PipelineUtils$Base.class")) {
                    stream = jarFile.getInputStream(entry);
                    break;
                }
            }
            reader = new ClassReader(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (reader == null) return;
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        for (MethodNode methodNode : ((List<MethodNode>) node.methods)) {
            if (methodNode.name.equals("initChannel")) {
                System.out.println("[BungeeWebsocket] Method found: " + node.name + "." + methodNode.name);
                InsnList insn = methodNode.instructions;
                insn.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/ixnah/mc/ws/agent/InitChannelHandler", "initChannel", "(Lio/netty/channel/Channel;)V", false));
                insn.insert(new VarInsnNode(Opcodes.ALOAD, 1));
                break;
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS & ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        try {
            inst.redefineClasses(new ClassDefinition(PipelineUtils.Base.class, writer.toByteArray()));
            System.out.println("[BungeeWebsocket] BungeeCord network injected!");
        } catch (ClassNotFoundException | UnmodifiableClassException e) {
            e.printStackTrace();
        }
    }
}
