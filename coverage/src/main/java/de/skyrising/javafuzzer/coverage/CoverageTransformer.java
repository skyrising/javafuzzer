package de.skyrising.javafuzzer.coverage;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Set;

public class CoverageTransformer implements ClassFileTransformer {
    private static final Set<String> IGNORED_PACKAGES = Set.of(
        "de/skyrising/javafuzzer/coverage/"
    );
    private static final Set<String> IGNORED_CLASSLOADERS = Set.of(
        "jdk.internal.reflect.DelegatingClassLoader"
    );

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!shouldTransform(loader, className, classBeingRedefined)) {
            //System.out.println("Not transforming " + className);
            return null;
        }
        System.out.println("Transforming " + className + " for " + loader.getName());
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        new CoverageClassAdapter(node).transform();
        node.accept(writer);
        byte[] transformed = writer.toByteArray();
        Path dumpPath = Path.of("dump", className + ".class");
        try {
            Files.createDirectories(dumpPath.getParent());
            Files.write(dumpPath, transformed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("Transformed " + className);
        return transformed;
    }

    private boolean shouldTransform(ClassLoader loader, String className, Class<?> classBeingRedefined) {
        if (loader == null || classBeingRedefined != null) return false;
        for (String pkg : IGNORED_PACKAGES) {
            if (className.startsWith(pkg)) return false;
        }
        String clClass = loader.getClass().getName();
        for (String cl : IGNORED_CLASSLOADERS) {
            if (cl.equals(clClass)) return false;
        }
        return true;
    }
}
