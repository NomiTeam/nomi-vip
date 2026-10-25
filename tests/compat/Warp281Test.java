import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

/** 离线核对新原包完整指令、线上旧会员规则和旧 VIP 接口。 */
public final class Warp281Test {
    private static final String PREFIX = "cn/handyplus/warp/";
    private static final String HELPER = "org/nomiteam/vip/compat/WarpPricing";

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
        try (var input = Warp281Test.class.getClassLoader().getResourceAsStream(name + ".class")) {
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
        int quotes = 0, limits = 0, payments = 0, wrappers = 0;
        for (String name : List.of("inventory/CreateGui", "listener/gui/CreateClickEvent", "util/WarpUtil")) {
            ClassNode patched = read(args[1], PREFIX + name);
            for (MethodNode method : new ArrayList<>(patched.methods)) {
                if (method.name.equals("rawSlotClick")) {
                    patched.methods.remove(method);
                    wrappers++;
                    continue;
                }
                if (method.name.equals("nomi$rawSlotClick")) method.name = "rawSlotClick";
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(HELPER)) continue;
                    if (call.name.equals("quote") || call.name.equals("limit")) {
                        AbstractInsnNode previous = call.getPrevious();
                        require(previous instanceof VarInsnNode, "缺少玩家参数");
                        method.instructions.remove(previous);
                        if (call.name.equals("quote")) {
                            previous = call.getPrevious();
                            require(previous instanceof VarInsnNode, "缺少币种参数");
                            method.instructions.remove(previous);
                            quotes++;
                        } else limits++;
                        method.instructions.remove(call);
                    } else {
                        call.owner = PREFIX + "hook/" + switch (call.name) {
                            case "takePoints" -> "PlayerPointsUtil";
                            case "takeVault" -> "VaultUtil";
                            case "takeCurrency" -> "PlayerCurrencyUtil";
                            default -> throw new AssertionError("额外 helper 调用");
                        };
                        call.name = "take";
                        payments++;
                    }
                }
            }
            require(normalized(read(args[0], PREFIX + name)).equals(normalized(patched)),
                "原生指令在必要位点之外发生变化：" + name);
        }
        require(quotes == 1 && limits == 1 && payments == 3 && wrappers == 1, "变换位点数量不符");
        for (String helper : List.of(HELPER, "org/nomiteam/vip/WarpPolicy",
                "org/nomiteam/vip/QuoteBook", "org/nomiteam/vip/QuoteBook$Quote")) {
            require(normalized(read(args[2], helper)).equals(normalized(read(args[1], helper))),
                "线上 helper 语义改变：" + helper);
        }
        int links = 0;
        try (JarFile vip = new JarFile(args[3])) {
            for (var entry : Collections.list(vip.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                new ClassReader(vip.getInputStream(entry)).accept(node, 0);
                for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.startsWith(PREFIX)) {
                        require(hasMethod(call.owner, call.name, call.desc), "旧 VIP 接口无法解析：" + call.owner + call.name + call.desc);
                        links++;
                    }
                    if (insn instanceof FieldInsnNode field && field.owner.startsWith(PREFIX)) {
                        require(resource(field.owner).fields.stream().anyMatch(f -> f.name.equals(field.name)
                            && f.desc.equals(field.desc) && (f.access & Opcodes.ACC_PUBLIC) != 0), "旧 VIP 字段改变");
                        links++;
                    }
                }
            }
        }
        // 线上旧 VIP 通过 CompatibilityReport 读取资源，没有直接 Warp API 调用。
        require(links == 0, "旧 VIP 出现尚未审核的直接 Warp 接口");
        ClassNode oldVip = read(args[3], "org/nomiteam/vip/NomiVIP");
        Set<String> constants = new HashSet<>();
        for (MethodNode method : oldVip.methods) for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value) constants.add(value);
        }
        require(constants.contains(PREFIX + "inventory/CreateGui.class") && constants.contains(HELPER),
            "旧 VIP 状态探测契约改变");
        require(Warp281Test.class.getClassLoader().getResource(HELPER + ".class") != null,
            "旧 VIP 探测所需 helper 不存在");
        // 直接执行候选中的会员边界与一次性报价，不加载新 VIP。
        for (int quota : new int[] {0, 2, 4, 6}) {
            for (int owned = 0; owned <= 8; owned++) {
                int expected = owned < quota ? 0 : 731;
                require(org.nomiteam.vip.WarpPolicy.price(quota, owned, 731) == expected, "会员配额边界错误");
            }
        }
        require(org.nomiteam.vip.WarpPolicy.quota(true, true, true) == 6, "权限优先级错误");
        var book = new org.nomiteam.vip.QuoteBook();
        book.offer("p", "point", 0, 100);
        require(!book.consume("p", "vault", 0, 101), "币种串用");
        require(!book.consume("p", "point", 731, 101), "变化价格仍接受");
        require(!book.consume("p", "point", 0, 102), "失败报价未消费");
        book.offer("p", "point", 731, 100);
        require(book.consume("p", "point", 731, 101), "正常报价被拒绝");
        require(!book.consume("p", "point", 731, 102), "重复消费");
        book.offer("p", "ply", 731, 100);
        require(!book.consume("p", "ply", 731, 101 + org.nomiteam.vip.QuoteBook.TTL), "过期报价被接受");
        System.out.println("PASS 原生指令、线上四个 helper、会员边界与报价；旧 VIP 接口=" + links);
    }
}
