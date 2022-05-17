package de.skyrising.javafuzzer.coverage;

import de.skyrising.javafuzzer.coverage.CoverageTracker.ClassCounterContainer;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class CoverageClassAdapter {
    private static final String CONTAINER_TYPE = Type.getInternalName(ClassCounterContainer.class);
    private static final String CONTAINER_DESC = Type.getDescriptor(ClassCounterContainer.class);
    private static final String CONTAINER_FIELD = "$$COVERAGE";
    private static final String HIT_METHOD = "$$hit";
    private static int nextClassId = 0;

    private final ClassNode node;
    private int classId = nextClassId++;
    private int nextProbeId = 0;

    public CoverageClassAdapter(ClassNode node) {
        this.node = node;
    }

    public void transform() {
        node.version = Math.max(node.version, V17);
        for (MethodNode method : node.methods) {
            instrumentMethod(method);
        }
        if (nextProbeId != 0) {
            addInfrastructure();
        }
    }

    private void addInfrastructure() {
        int fieldAccess = ACC_STATIC| ACC_FINAL | ACC_SYNTHETIC;
        fieldAccess |= (node.access & ACC_INTERFACE) == 0 ? ACC_PRIVATE : ACC_PUBLIC;
        node.fields.add(new FieldNode(fieldAccess, CONTAINER_FIELD, CONTAINER_DESC, null, null));

        MethodNode clinit = null;
        for (MethodNode md : node.methods) {
            if ("<clinit>".equals(md.name) && "()V".equals(md.desc)) {
                clinit = md;
                break;
            }
        }
        if (clinit == null) {
            clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new InsnNode(RETURN));
            node.methods.add(clinit);
        }
        clinit.instructions.insert(createInit());

        MethodNode hit = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "$$hit", "(I)V", null, null);
        hit.instructions.add(new FieldInsnNode(GETSTATIC, node.name, CONTAINER_FIELD, CONTAINER_DESC));
        hit.instructions.add(new VarInsnNode(ILOAD, 0));
        hit.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, CONTAINER_TYPE, "hit", "(I)V", false));
        hit.instructions.add(new InsnNode(RETURN));
        node.methods.add(hit);
    }

    private InsnList createInit() {
        InsnList insns = new InsnList();
        insns.add(new TypeInsnNode(NEW, CONTAINER_TYPE));
        insns.add(new InsnNode(DUP));
        insns.add(pushInt(classId));
        insns.add(pushInt(nextProbeId));
        insns.add(new MethodInsnNode(INVOKESPECIAL, CONTAINER_TYPE, "<init>", "(II)V", false));
        insns.add(new FieldInsnNode(PUTSTATIC, node.name, CONTAINER_FIELD, CONTAINER_DESC));
        return insns;
    }

    private void instrumentMethod(MethodNode method) {
        Set<AbstractInsnNode> probePoints = new HashSet<>();
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            probePoints.add(tryCatch.handler);
        }
        InsnList instructions = method.instructions;
        for (AbstractInsnNode node : instructions) {
            if (node instanceof JumpInsnNode jump) {
                probePoints.add(jump.label);
                probePoints.add(node);
            } else if (node instanceof TableSwitchInsnNode tableSwitch) {
                probePoints.addAll(tableSwitch.labels);
                probePoints.add(tableSwitch.dflt);
            } else if (node instanceof LookupSwitchInsnNode lookupSwitch) {
                probePoints.addAll(lookupSwitch.labels);
                probePoints.add(lookupSwitch.dflt);
            } else if (shouldProbeBefore(node)) {
                probePoints.add(node);
            }
        }
        Set<AbstractInsnNode> sorted = new TreeSet<>(Comparator.comparingInt(instructions::indexOf));
        sorted.addAll(probePoints);
        Set<AbstractInsnNode> covered = new HashSet<>();
        for (AbstractInsnNode node : sorted) {
            if (!covered.add(node)) continue;
            if (shouldProbeBefore(node)) {
                instructions.insertBefore(node, createProbe(nextProbeId++));
            } else {
                AbstractInsnNode next = node.getNext();
                instructions.insert(node, createProbe(nextProbeId++));
                while (next != null) {
                    if (isFakeNode(next)) {
                        covered.add(next);
                    } else if (shouldProbeBefore(next)) {
                        covered.add(next);
                        break;
                    } else {
                        break;
                    }
                    next = next.getNext();
                }
            }
        }
    }

    private static boolean isFakeNode(AbstractInsnNode node) {
        return node.getOpcode() == -1;
    }

    private static boolean shouldProbeBefore(AbstractInsnNode node) {
        return (node.getOpcode() >= IRETURN && node.getOpcode() <= RETURN) || node.getOpcode() == ATHROW;
    }

    private InsnList createProbe(int id) {
        InsnList list = new InsnList();
        list.add(pushInt(id));
        list.add(new MethodInsnNode(INVOKESTATIC, node.name, HIT_METHOD, "(I)V", (node.access & ACC_INTERFACE) != 0));
        return list;
    }

    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(ICONST_0 + value);
        if (value == (byte) value) return new IntInsnNode(BIPUSH, value);
        if (value == (short) value) return new IntInsnNode(SIPUSH, value);
        return new LdcInsnNode(value);
    }
}
