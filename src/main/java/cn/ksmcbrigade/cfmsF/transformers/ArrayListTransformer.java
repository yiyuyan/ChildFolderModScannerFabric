package cn.ksmcbrigade.cfmsF.transformers;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class ArrayListTransformer implements ClassFileTransformer {

    private static boolean transformed = false;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!"java/util/ArrayList$Itr".equals(className)) return classfileBuffer;

        if (transformed) return classfileBuffer;

        Log.warn(LogCategory.GAME_PATCH, "Transforming " + className);

        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            reader.accept(classNode, 0);

            for (MethodNode method : classNode.methods) {
                if ("checkForComodification".equals(method.name) && "()V".equals(method.desc)) {
                    InsnList newInstructions = new InsnList();
                    newInstructions.add(new InsnNode(Opcodes.RETURN));
                    method.instructions = newInstructions;
                    method.localVariables = null;
                    method.tryCatchBlocks = null;
                    break;
                }
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            byte[] result = writer.toByteArray();

            transformed = true;
            Log.warn(LogCategory.GAME_PATCH, "Transformed " + className);
            return result;
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            Log.error(LogCategory.GAME_PATCH, "Failed to transform " + className, t);
            return classfileBuffer;
        }
    }
}