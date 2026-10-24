import java.io.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
/** Structural/dataflow verification plus JVM verification, without static initialization. */
public final class VerifyGenerated {
 public static void main(String[] args)throws Exception{
  String[] classes=args[0].equals("residence")?new String[]{"com.bekvon.bukkit.residence.protection.ResidencePermissions","org.nomiteam.vip.compat.ResidenceGrant"}:new String[]{"cn.handyplus.warp.inventory.CreateGui","cn.handyplus.warp.listener.gui.CreateClickEvent","cn.handyplus.warp.util.WarpUtil","org.nomiteam.vip.compat.WarpPricing"};
  for(String name:classes){
   Class<?> c=Class.forName(name,false,VerifyGenerated.class.getClassLoader());c.getDeclaredMethods();
   try(var in=c.getResourceAsStream("/"+name.replace('.','/')+".class")){
    StringWriter report=new StringWriter();CheckClassAdapter.verify(new ClassReader(in),VerifyGenerated.class.getClassLoader(),false,new PrintWriter(report));
    if(!report.toString().isBlank())throw new AssertionError(name+"\n"+report);
   }
   System.out.println("PASS JVM/ASM verify "+name);
  }
 }
}
