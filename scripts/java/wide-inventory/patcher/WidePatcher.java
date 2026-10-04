import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Rewires the installed Paper 1.12.2 server jar to the JasprWide hooks (scripts/java/wide-inventory/src, see JasprWide.java for
 * the layout). Every edit names its method by descriptor and checks how many instructions it changes, so a different
 * server build fails loudly instead of patching the wrong place.
 *
 * Usage: java -cp <asm from the server jar>;. WidePatcher <input jar> <output jar> [JasprWide classes dir]
 * Without the classes dir it writes the rewired jar only (used to compile JasprWide against the new field).
 */
public class WidePatcher implements Opcodes {
    static final String NMS = "net/minecraft/server/v1_12_R1/";
    static final String WIDE = NMS + "JasprWide";
    static final String INV = NMS + "PlayerInventory";
    static final List<String> report = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]), out = new File(args[1]);
        File classes = args.length > 2 ? new File(args[2]) : null;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(in)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.isDirectory()) { entries.put(entry.getName(), null); continue; }
                try (InputStream s = zip.getInputStream(entry)) { entries.put(entry.getName(), s.readAllBytes()); }
            }
        }
        if (entries.containsKey(WIDE + ".class")) throw new IllegalStateException("input jar is already patched (JasprWide.class present)");
        patch(entries, INV, WidePatcher::playerInventory);
        patch(entries, NMS + "ContainerPlayer", WidePatcher::containerPlayer);
        patch(entries, NMS + "Container", WidePatcher::container);
        patch(entries, NMS + "PlayerConnection", WidePatcher::playerConnection);
        patch(entries, "org/bukkit/craftbukkit/v1_12_R1/inventory/CraftInventoryPlayer", WidePatcher::craftInventoryPlayer);
        patch(entries, "org/bukkit/inventory/InventoryView", WidePatcher::inventoryView);
        patch(entries, "org/bukkit/craftbukkit/v1_12_R1/inventory/CraftInventoryView", WidePatcher::craftInventoryView);
        if (classes != null) {
            File dir = new File(classes, WIDE.substring(0, WIDE.lastIndexOf('/')));
            File[] own = dir.listFiles((d, n) -> n.startsWith("JasprWide") && n.endsWith(".class"));
            if (own == null || own.length == 0) throw new IllegalStateException("no compiled JasprWide classes in " + dir);
            for (File f : own) { entries.put(NMS + f.getName(), Files.readAllBytes(f.toPath())); report.add("added " + f.getName()); }
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                if (e.getValue() != null) zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        for (String line : report) System.out.println(line);
        System.out.println("WIDE_PATCH_OK edits=" + report.size());
    }

    interface Edit { void apply(ClassNode cn); }

    static void patch(Map<String, byte[]> entries, String name, Edit edit) {
        byte[] bytes = entries.get(name + ".class");
        if (bytes == null) throw new IllegalStateException("missing class " + name);
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.EXPAND_FRAMES);
        edit.apply(cn);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        entries.put(name + ".class", cw.toByteArray());
    }

    static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException("missing method " + cn.name + "." + name + desc);
    }

    /** Replaces the body with: load every argument, call JasprWide.hook with the same arguments, return its result. */
    static void delegate(ClassNode cn, String name, String desc, String hook, String hookDesc) {
        MethodNode m = method(cn, name, desc);
        InsnList code = new InsnList();
        int local = 0;
        boolean isStatic = (m.access & ACC_STATIC) != 0;
        if (!isStatic) { code.add(new VarInsnNode(ALOAD, 0)); local = 1; }
        for (Type t : Type.getArgumentTypes(desc)) { code.add(new VarInsnNode(t.getOpcode(ILOAD), local)); local += t.getSize(); }
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, hook, hookDesc, false));
        code.add(new InsnNode(Type.getReturnType(desc).getOpcode(IRETURN)));
        replaceBody(m, code);
        report.add("delegated " + cn.name + "." + name + " -> " + hook);
    }

    static void replaceBody(MethodNode m, InsnList code) {
        m.instructions.clear();
        m.instructions.add(code);
        m.tryCatchBlocks.clear();
        if (m.localVariables != null) m.localVariables.clear();
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;
    }

    /** Changes the single BIPUSH from -> to inside the method. */
    static void constant(ClassNode cn, MethodNode m, int from, int to) {
        IntInsnNode hit = null;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() == BIPUSH && ((IntInsnNode) i).operand == from) {
                if (hit != null) throw new IllegalStateException(cn.name + "." + m.name + ": more than one BIPUSH " + from);
                hit = (IntInsnNode) i;
            }
        }
        if (hit == null) throw new IllegalStateException(cn.name + "." + m.name + ": no BIPUSH " + from);
        hit.operand = to;
        report.add("constant " + cn.name + "." + m.name + " " + from + " -> " + to);
    }

    /** The first call to owner.name in the method (the insertion point right after it). */
    static MethodInsnNode firstCall(MethodNode m, String owner, String name) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext())
            if (i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.equals(owner) && ((MethodInsnNode) i).name.equals(name))
                return (MethodInsnNode) i;
        throw new IllegalStateException(m.name + m.desc + ": no call to " + owner + "." + name);
    }

    /** Full frame of a method's entry state (this + arguments), used at labels inserted before any local is stored. */
    static FrameNode entryFrame(ClassNode cn, MethodNode m, Object... stack) {
        List<Object> locals = new ArrayList<>();
        if ((m.access & ACC_STATIC) == 0) locals.add(cn.name);
        for (Type t : Type.getArgumentTypes(m.desc)) {
            switch (t.getSort()) {
                case Type.BOOLEAN: case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT: locals.add(INTEGER); break;
                case Type.FLOAT: locals.add(FLOAT); break;
                case Type.LONG: locals.add(LONG); break;
                case Type.DOUBLE: locals.add(DOUBLE); break;
                default: locals.add(t.getInternalName());
            }
        }
        return new FrameNode(F_NEW, locals.size(), locals.toArray(), stack.length, stack);
    }

    /** After `after` (or at the start): `if (JasprWide.hook(this, arg1)) return;` for a void handler. */
    static void earlyReturn(ClassNode cn, MethodNode m, AbstractInsnNode after, String hook, String hookDesc) {
        InsnList code = new InsnList();
        LabelNode go = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, hook, hookDesc, false));
        code.add(new JumpInsnNode(IFEQ, go));
        code.add(new InsnNode(RETURN));
        code.add(go);
        code.add(entryFrame(cn, m));
        if (after == null) m.instructions.insert(code); else m.instructions.insert(after, code);
        report.add("hooked " + cn.name + "." + m.name + m.desc + " -> " + hook);
    }

    // ---- the edits ------------------------------------------------------------------------------------------------

    static void playerInventory(ClassNode cn) {
        cn.fields.add(new FieldNode(ACC_PUBLIC, "jasprWide", "Z", null, null));
        report.add("field " + cn.name + ".jasprWide");
        constant(cn, method(cn, "<init>", "(L" + NMS + "EntityHuman;)V"), 36, 56);
        String inv = "L" + INV + ";", stack = "L" + NMS + "ItemStack;";
        delegate(cn, "getFirstEmptySlotIndex", "()I", "firstEmpty", "(" + inv + ")I");
        delegate(cn, "firstPartial", "(" + stack + ")I", "firstPartial", "(" + inv + stack + ")I");
        delegate(cn, "canHold", "(" + stack + ")I", "canHold", "(" + inv + stack + ")I");
        delegate(cn, "e", "(I)Z", "hotbar", "(I)Z");
    }

    static void containerPlayer(ClassNode cn) {
        MethodNode init = method(cn, "<init>", "(L" + INV + ";ZL" + NMS + "EntityHuman;)V");
        constant(cn, init, 36, 56);   // armour: new Slot(inventory, 36 + (3 - i), ...)
        constant(cn, init, 40, 60);   // off hand: new Slot(inventory, 40, 77, 62)
        String stack = NMS + "ItemStack";
        MethodNode shift = method(cn, "shiftClick", "(L" + NMS + "EntityHuman;I)L" + stack + ";");
        InsnList code = new InsnList();
        LabelNode vanilla = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new VarInsnNode(ILOAD, 2));
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, "shiftPlayer", "(L" + cn.name + ";L" + NMS + "EntityHuman;I)L" + stack + ";", false));
        code.add(new InsnNode(DUP));
        code.add(new JumpInsnNode(IFNULL, vanilla));
        code.add(new InsnNode(ARETURN));
        code.add(vanilla);
        code.add(entryFrame(cn, shift, stack));
        code.add(new InsnNode(POP));
        shift.instructions.insert(code);
        report.add("hooked " + cn.name + ".shiftClick -> shiftPlayer");
    }

    static void container(ClassNode cn) {
        MethodNode listen = method(cn, "addSlotListener", "(L" + NMS + "ICrafting;)V");
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, "listen", "(L" + cn.name + ";L" + NMS + "ICrafting;)V", false));
        listen.instructions.insert(code);
        report.add("hooked " + cn.name + ".addSlotListener -> listen");
    }

    static void playerConnection(ClassNode cn) {
        String utils = NMS + "PlayerConnectionUtils", self = "L" + cn.name + ";";
        MethodNode held = method(cn, "a", "(L" + NMS + "PacketPlayInHeldItemSlot;)V");
        earlyReturn(cn, held, firstCall(held, utils, "ensureMainThread"), "held", "(" + self + "L" + NMS + "PacketPlayInHeldItemSlot;)Z");
        MethodNode payload = method(cn, "a", "(L" + NMS + "PacketPlayInCustomPayload;)V");
        earlyReturn(cn, payload, firstCall(payload, utils, "ensureMainThread"), "payload", "(" + self + "L" + NMS + "PacketPlayInCustomPayload;)Z");
        // Creative mode: `slot >= 1 && slot <= 45` accepts the 20 extra window slots of a wide client.
        MethodNode creative = method(cn, "a", "(L" + NMS + "PacketPlayInSetCreativeSlot;)V");
        IntInsnNode hit = null;
        for (AbstractInsnNode i = creative.instructions.getFirst(); i != null; i = i.getNext())
            if (i.getOpcode() == BIPUSH && ((IntInsnNode) i).operand == 45) {
                if (hit != null) throw new IllegalStateException("creative slot: more than one BIPUSH 45");
                hit = (IntInsnNode) i;
            }
        if (hit == null || hit.getNext().getOpcode() != IF_ICMPGT) throw new IllegalStateException("creative slot: `<= 45` not found");
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, "creativeMax", "(" + self + ")I", false));
        creative.instructions.insert(hit, code);
        creative.instructions.remove(hit);
        report.add("hooked " + cn.name + ".a(PacketPlayInSetCreativeSlot) 45 -> creativeMax");
    }

    static void craftInventoryPlayer(ClassNode cn) {
        String bukkitStack = "Lorg/bukkit/inventory/ItemStack;";
        MethodNode set = method(cn, "setItem", "(I" + bukkitStack + ")V");
        InsnList code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ILOAD, 1));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new MethodInsnNode(INVOKESPECIAL, cn.superName, "setItem", "(I" + bukkitStack + ")V", false));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, cn.name, "getInventory", "()L" + INV + ";", false));
        code.add(new VarInsnNode(ILOAD, 1));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, "bukkitSlot", "(L" + INV + ";I" + bukkitStack + ")V", false));
        code.add(new InsnNode(RETURN));
        replaceBody(set, code);
        report.add("replaced " + cn.name + ".setItem -> bukkitSlot");
        MethodNode held = method(cn, "setHeldItemSlot", "(I)V");
        code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, cn.name, "getInventory", "()L" + INV + ";", false));
        code.add(new VarInsnNode(ILOAD, 1));
        code.add(new MethodInsnNode(INVOKESTATIC, WIDE, "bukkitHeld", "(L" + INV + ";I)V", false));
        code.add(new InsnNode(RETURN));
        replaceBody(held, code);
        report.add("replaced " + cn.name + ".setHeldItemSlot -> bukkitHeld");
    }

    static void inventoryView(ClassNode cn) {
        delegate(cn, "convertSlot", "(I)I", "convertSlot", "(L" + cn.name + ";I)I");
    }

    static void craftInventoryView(ClassNode cn) {
        String view = "Lorg/bukkit/inventory/InventoryView;", type = "Lorg/bukkit/event/inventory/InventoryType$SlotType;";
        String desc = "(" + view + "I)" + type;
        MethodNode vanilla = method(cn, "getSlotType", desc);
        vanilla.name = "jasprVanillaSlotType";
        vanilla.access = (vanilla.access & ~(ACC_PUBLIC | ACC_PROTECTED)) | ACC_PRIVATE;
        MethodNode wrap = new MethodNode(ACC_PUBLIC | ACC_STATIC, "getSlotType", desc, null, null);
        wrap.instructions.add(new VarInsnNode(ALOAD, 0));
        wrap.instructions.add(new VarInsnNode(ILOAD, 1));
        wrap.instructions.add(new VarInsnNode(ALOAD, 0));
        wrap.instructions.add(new VarInsnNode(ILOAD, 1));
        wrap.instructions.add(new MethodInsnNode(INVOKESTATIC, cn.name, "jasprVanillaSlotType", desc, false));
        wrap.instructions.add(new MethodInsnNode(INVOKESTATIC, WIDE, "slotType", "(" + view + "I" + type + ")" + type, false));
        wrap.instructions.add(new InsnNode(ARETURN));
        cn.methods.add(wrap);
        report.add("wrapped " + cn.name + ".getSlotType -> slotType");
    }
}
