package de.skyrising.javafuzzer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class AsmTarget implements FuzzTarget{
    @Override
    public void fuzz(byte[] data) {
        try {
            ClassReader cr = new ClassReader(data);
            ClassNode node = new ClassNode();
            cr.accept(node, ClassReader.EXPAND_FRAMES);
        } catch (IllegalArgumentException|ArrayIndexOutOfBoundsException ignored) {}
    }
}
