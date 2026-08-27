package cn.ksmcbrigade.cfmsF.transformers;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class ArrayListTransformer implements ClassFileTransformer {

    public static byte[] buffers = null;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!"java/util/ArrayList$Itr".equals(className)) {
            return classfileBuffer;
        }

        int asmApi = Opcodes.ASM9;
        try {
            if(Integer.parseInt((String) System.getProperties().getOrDefault("java.specification.version","11"))>=25){
                asmApi = Opcodes.ASM10_EXPERIMENTAL;
            }
        } catch (Throwable e) {
            Log.error(LogCategory.GAME_PATCH,"Failed to parse ASM version.Using ASM9.");
        }

        if(buffers==null){
            buffers = classfileBuffer;
        }
        else{
            Log.warn(LogCategory.GAME_PATCH,"Restoring " + className);
            return buffers;
        }

        Log.warn(LogCategory.GAME_PATCH,"Transforming " + className);
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = getClassVisitor(asmApi, writer);
        reader.accept(visitor, 0);
        Log.warn(LogCategory.GAME_PATCH,"Transformed " + className);
        return writer.toByteArray();
    }

    private static @NotNull ClassVisitor getClassVisitor(int asmApi, ClassWriter writer) {
        return new ClassVisitor(asmApi, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("checkForComodification".equals(name) && "()V".equals(descriptor)) {
                    return new MethodVisitor(asmApi, mv) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitMaxs(0, 0);
                            mv.visitEnd();
                        }
                    };
                }
                return mv;
            }
        };
    }
}