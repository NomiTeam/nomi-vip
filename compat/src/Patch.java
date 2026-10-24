import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Strict migration of the audited patched versions. No third-party implementation is included here. */
public final class Patch implements Opcodes {
 static final String RES="com/bekvon/bukkit/residence/protection/ResidencePermissions";
 static final String OLD_RES="com/bekvon/bukkit/residence/protection/NuomiResidenceFlight";
 static final String OLD_WARP="cn/handyplus/warp/util/NuomiWarpPricing";
 static final String HELPER="org/nomiteam/vip/compat/";
 static int residence,charges,labels,limits,serialized;
 static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
 static AbstractInsnNode next(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
 static void removeBefore(MethodNode m,AbstractInsnNode end){while(m.instructions.getFirst()!=end)m.instructions.remove(m.instructions.getFirst());}
 static byte[] transform(String name,byte[] bytes){
  ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
  MethodNode wrapper=null;
  for(MethodNode m:c.methods){
   if(name.equals("cn/handyplus/warp/listener/gui/CreateClickEvent")&&m.name.equals("rawSlotClick")&&m.desc.equals("(Lcn/handyplus/warp/lib/HandyInventory;Lorg/bukkit/event/inventory/InventoryClickEvent;)V")){
    require((m.access&ACC_STATIC)==0,"Unexpected static click handler");
    m.name="nomi$rawSlotClick";wrapper=serializedWrapper(name,m.desc);serialized++;
   }
   if(name.equals(RES)&&m.name.equals("playerHas")){
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(OLD_RES)){
     call.owner=HELPER+"ResidenceGrant";residence++;
    }
   }
   if(name.equals("cn/handyplus/warp/inventory/CreateGui")&&m.desc.equals("(Lorg/bukkit/entity/Player;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Integer;)Ljava/util/Map;")){
    // Legacy injection is exactly eight executable instructions, ending ASTORE 5.
    AbstractInsnNode n=m.instructions.getFirst();int removed=0;
    while(n!=null){AbstractInsnNode after=n.getNext();if(n.getOpcode()>=0)removed++;
     boolean end=n instanceof VarInsnNode v&&v.getOpcode()==ASTORE&&v.var==5;
     m.instructions.remove(n);if(end)break;n=after;
    }
    require(removed==8,"Unexpected legacy GUI prefix: "+removed);
    for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof VarInsnNode v&&v.getOpcode()==LSTORE&&v.var==9){
     InsnList q=new InsnList();q.add(new VarInsnNode(ALOAD,1));q.add(new VarInsnNode(ALOAD,2));
     q.add(new MethodInsnNode(INVOKESTATIC,HELPER+"WarpPricing","quote","(JLorg/bukkit/entity/Player;Ljava/lang/String;)J",false));
     m.instructions.insertBefore(ins,q);labels++;
    }
   }
   if(name.equals("cn/handyplus/warp/listener/gui/CreateClickEvent")){
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call){
     if(call.owner.equals(OLD_WARP)&&(call.name.equals("takePoints")||call.name.equals("takeVault"))){call.owner=HELPER+"WarpPricing";charges++;}
     else if(call.owner.equals("cn/handyplus/warp/hook/PlayerCurrencyUtil")&&call.name.equals("take")&&call.desc.equals("(Lorg/bukkit/entity/Player;Ljava/lang/String;JLjava/lang/String;)Z")){
      call.owner=HELPER+"WarpPricing";call.name="takeCurrency";charges++;
     }
    }
   }
   if(name.equals("cn/handyplus/warp/util/WarpUtil")&&m.name.equals("checkWarpNumber")&&m.desc.equals("(Lorg/bukkit/entity/Player;)Z")){
    // Drop only the legacy early bypass; native world/claim/API checks stay intact.
    JumpInsnNode branch=null;
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof JumpInsnNode j){branch=j;break;}
    require(branch!=null&&branch.getOpcode()==IFEQ,"Unexpected limit prefix");removeBefore(m,branch.label);
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("cn/handyplus/warp/util/WarpPermissionUtil")&&call.name.equals("getWarpNumber")){
     AbstractInsnNode unbox=next(call);require(unbox instanceof MethodInsnNode u&&u.name.equals("intValue"),"Unexpected quota unboxing");
     InsnList q=new InsnList();q.add(new VarInsnNode(ALOAD,0));q.add(new MethodInsnNode(INVOKESTATIC,HELPER+"WarpPricing","limit","(ILorg/bukkit/entity/Player;)I",false));m.instructions.insert(unbox,q);limits++;
    }
   }
  }
  if(wrapper!=null)c.methods.add(wrapper);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
 }
 static MethodNode serializedWrapper(String owner,String desc){
  MethodNode m=new MethodNode(ACC_PUBLIC,"rawSlotClick",desc,null,null);
  String inventory="cn/handyplus/warp/lib/HandyInventory";
  Label start=new Label(),end=new Label(),handler=new Label();
  m.visitCode();m.visitVarInsn(ALOAD,1);m.visitMethodInsn(INVOKEVIRTUAL,inventory,"getPlayer","()Lorg/bukkit/entity/Player;",false);
  m.visitMethodInsn(INVOKESTATIC,HELPER+"WarpPricing","lock","(Lorg/bukkit/entity/Player;)Ljava/lang/Object;",false);
  m.visitInsn(DUP);m.visitVarInsn(ASTORE,3);m.visitInsn(MONITORENTER);m.visitLabel(start);
  m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,1);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKEVIRTUAL,owner,"nomi$rawSlotClick",desc,false);
  m.visitLabel(end);m.visitVarInsn(ALOAD,3);m.visitInsn(MONITOREXIT);m.visitInsn(RETURN);
  m.visitLabel(handler);m.visitFrame(F_FULL,4,new Object[]{owner,inventory,"org/bukkit/event/inventory/InventoryClickEvent","java/lang/Object"},1,new Object[]{"java/lang/Throwable"});
  m.visitVarInsn(ASTORE,4);m.visitVarInsn(ALOAD,3);m.visitInsn(MONITOREXIT);m.visitVarInsn(ALOAD,4);m.visitInsn(ATHROW);
  m.visitTryCatchBlock(start,end,handler,null);m.visitMaxs(3,5);m.visitEnd();return m;
 }
 public static void main(String[] a)throws Exception{
  String kind=a[0];Path output=Path.of(a[2]);
  try(ZipFile input=new ZipFile(a[1]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(output))){
   var entries=input.entries();
   while(entries.hasMoreElements()){
    var e=entries.nextElement();String n=e.getName();
    if(n.equals(OLD_RES+".class")||n.equals(OLD_WARP+".class"))continue;
    byte[] b=input.getInputStream(e).readAllBytes();String c=n.endsWith(".class")?n.substring(0,n.length()-6):"";
    if(c.equals(RES)||Set.of("cn/handyplus/warp/inventory/CreateGui","cn/handyplus/warp/util/WarpUtil","cn/handyplus/warp/listener/gui/CreateClickEvent").contains(c))b=transform(c,b);
    ZipEntry z=new ZipEntry(n);z.setTime(0);out.putNextEntry(z);out.write(b);out.closeEntry();
   }
   List<String> helpers=kind.equals("residence")?List.of(HELPER+"ResidenceGrant"):List.of(HELPER+"WarpPricing","org/nomiteam/vip/WarpPolicy","org/nomiteam/vip/QuoteBook","org/nomiteam/vip/QuoteBook$Quote");
   for(String c:helpers){ZipEntry z=new ZipEntry(c+".class");z.setTime(0);out.putNextEntry(z);out.write(Files.readAllBytes(Path.of(a[3],c+".class")));out.closeEntry();}
  }
  if(kind.equals("residence"))require(residence==2&&charges+labels+limits==0,"Residence sites mismatch");
  else require(charges==3&&labels==1&&limits==1&&serialized==1&&residence==0,"Warp sites mismatch: "+charges+","+labels+","+limits);
  System.out.println("Verified transformation sites: residence="+residence+", payments="+charges+", display="+labels+", count-limit="+limits);
 }
}
