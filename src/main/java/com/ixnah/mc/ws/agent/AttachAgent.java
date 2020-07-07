package com.ixnah.mc.ws.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/31 13:10
 * <p>
 * 该类会被挂载到Jvm默认Classloader
 */
public class AttachAgent {

    private static final String className = "net.md_5.bungee.netty.PipelineUtils$Base";

    @SuppressWarnings("unchecked")
    public static void agentmain(String args, Instrumentation inst) {
        try {
            System.out.println("[BungeeWebsocket] AttachAgent loaded!");
            ClassReader reader = new ClassReader(className);
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
            ClassWriter writer = new ClassWriter(0); // ClassWriter.COMPUTE_MAXS & ClassWriter.COMPUTE_FRAMES
            node.accept(writer);
            inst.redefineClasses(new ClassDefinition(Class.forName(className), writer.toByteArray()));
            System.out.println("[BungeeWebsocket] BungeeCord network injected!");
        } catch (ClassNotFoundException | IOException | UnmodifiableClassException e) {
            e.printStackTrace();
        }
    }
}
