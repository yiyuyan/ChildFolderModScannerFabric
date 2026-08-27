package cn.ksmcbrigade.cfmsF.utils;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Random;
import java.util.jar.*;

public class AgentUtils {

    public static final Random RANDOM = new Random();

    public static File createAgent() throws IOException {
        String suffix = randomNum(6);
        String simpleClassName = "CFMSfAgent_" + suffix;
        String fullClassName = "generated." + simpleClassName;
        String classPath = "generated/" + simpleClassName + ".class";

        byte[] classBytes = generateClassBytes(fullClassName);

        File jarFile = Path.of(System.getProperty("java.io.tmpdir"),
                randomNum(12) + ".jar").toFile();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            Manifest manifest = new Manifest();
            Attributes attrs = manifest.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attrs.put(new Attributes.Name("Premain-Class"), fullClassName);
            attrs.put(new Attributes.Name("Agent-Class"), fullClassName);
            attrs.put(new Attributes.Name("Can-Retransform-Classes"), "true");
            attrs.put(new Attributes.Name("Can-Redefine-Classes"), "true");

            JarEntry manifestEntry = new JarEntry(JarFile.MANIFEST_NAME);
            jos.putNextEntry(manifestEntry);
            manifest.write(jos);
            jos.closeEntry();

            JarEntry classEntry = new JarEntry(classPath);
            jos.putNextEntry(classEntry);
            jos.write(classBytes);
            jos.closeEntry();
        }

        return jarFile;
    }

    public static byte[] generateClassBytes(String fullClassName) {
        String internalName = fullClassName.replace('.', '/');

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // premain(String, Instrumentation)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "premain",
                "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
                null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, "agentmain",
                "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // agentmain(String, Instrumentation)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "agentmain",
                "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
                null, null);
        mv.visitCode();
        // System.getProperties().put("_inst_", inst);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;", false);
        mv.visitLdcInsn("_inst_");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public static Instrumentation injectTmpAgent() throws IOException {
        File file = createAgent();
        UnsafeUtils.loadAgent(file.getAbsolutePath());
        file.deleteOnExit();
        return getInst();
    }

    public static Instrumentation getInst(){
        return (Instrumentation) System.getProperties().getOrDefault("_inst_",null);
    }

    public static String randomNum(int length){
        StringBuilder builder = new StringBuilder();
        for (int i = length; i > 0; i--) {
            builder.append(RANDOM.nextInt(9)+1);
        }
        return builder.toString();
    }
}