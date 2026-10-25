import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

/** 离线边界：核对原生指令完整保留、线上旧 VIP 链接与旧 helper 规则。 */
public final class Residence6041Test {
    private static final String RES = "com/bekvon/bukkit/residence/protection/ResidencePermissions";
    private static final String HELPER = "org/nomiteam/vip/compat/ResidenceGrant";

    private static ClassNode read(String jar, String name) throws Exception {
        try (JarFile input = new JarFile(jar)) {
            ClassNode node = new ClassNode();
            new ClassReader(input.getInputStream(input.getJarEntry(name + ".class")))
                .accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static String normalized(ClassNode node) {
        for (MethodNode method : node.methods) {
            Set<LabelNode> used = new HashSet<>();
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof JumpInsnNode jump) used.add(jump.label);
                if (insn instanceof TableSwitchInsnNode table) {
                    used.add(table.dflt);
                    used.addAll(table.labels);
                }
                if (insn instanceof LookupSwitchInsnNode lookup) {
                    used.add(lookup.dflt);
                    used.addAll(lookup.labels);
                }
            }
            for (TryCatchBlockNode block : method.tryCatchBlocks) {
                used.add(block.start);
                used.add(block.end);
                used.add(block.handler);
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LabelNode label && !used.contains(label)) method.instructions.remove(insn);
            }
            method.maxStack = 0;
            method.maxLocals = 0;
        }
        StringWriter text = new StringWriter();
        node.accept(new TraceClassVisitor(new PrintWriter(text)));
        return text.toString();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static ClassNode resource(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = Residence6041Test.class.getClassLoader().getResourceAsStream(name + ".class")) {
            require(input != null, "Missing API class " + name);
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
        }
        return node;
    }

    private static boolean hasMethod(String owner, String name, String descriptor) throws Exception {
        ClassNode type = resource(owner);
        for (MethodNode method : type.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)
                    && (method.access & Opcodes.ACC_PUBLIC) != 0) return true;
        }
        if (type.superName != null && hasMethod(type.superName, name, descriptor)) return true;
        for (String parent : type.interfaces) if (hasMethod(parent, name, descriptor)) return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        ClassNode patched = read(args[1], RES);
        int sites = 0;
        for (MethodNode method : patched.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(HELPER)) {
                    require(method.name.equals("playerHas"), "Unexpected patch method");
                    AbstractInsnNode branch = call.getNext();
                    require(branch instanceof JumpInsnNode && branch.getOpcode() == Opcodes.IFEQ,
                        "Missing native fallback");
                    LabelNode fallback = ((JumpInsnNode) branch).label;
                    while (method.instructions.getFirst() != fallback) method.instructions.remove(method.instructions.getFirst());
                    sites++;
                }
            }
        }
        require(sites == 2, "Expected exactly two grant sites");
        require(normalized(read(args[0], RES)).equals(normalized(patched)), "Native class changed outside prefixes");
        require(normalized(read(args[2], HELPER)).equals(normalized(read(args[1], HELPER))), "Online helper rules changed");
        int links = 0;
        // 只链接给定线上旧 VIP 的 Residence 调用，不使用仓库的新 VIP 编译产物。
        try (JarFile vip = new JarFile(args[3])) {
            for (var entry : Collections.list(vip.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                new ClassReader(vip.getInputStream(entry)).accept(node, 0);
                for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.startsWith("com/bekvon/bukkit/residence/")) {
                        require(hasMethod(call.owner, call.name, call.desc), "Unresolved old VIP method: " + call.owner + "." + call.name + call.desc);
                        links++;
                    }
                    if (insn instanceof FieldInsnNode field && field.owner.startsWith("com/bekvon/bukkit/residence/")) {
                        require(resource(field.owner).fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc) && (f.access & Opcodes.ACC_PUBLIC) != 0), "Old VIP field changed");
                        links++;
                    }
                }
            }
        }
        require(links > 0, "No old VIP links checked");
        System.out.println("PASS native class retained; two prefixes; online helper unchanged; old VIP links=" + links);
    }
}
