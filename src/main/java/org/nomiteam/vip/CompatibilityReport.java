package org.nomiteam.vip;
import org.bukkit.plugin.Plugin;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Read-only evidence from the classloaders of the plugins actually installed. */
final class CompatibilityReport {
 static String inspect(Plugin plugin,String target,String helper){
  if(plugin==null)return "absent";
  var loader=plugin.getClass().getClassLoader();
  try(InputStream in=loader.getResourceAsStream(target)){
   if(in==null)return plugin.getDescription().getVersion()+" target-class-unavailable";
   byte[] bytes=in.readAllBytes();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
   boolean reference=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains(helper);
   boolean helperPresent=loader.getResource(helper+".class")!=null;
   return "enabled="+plugin.isEnabled()+", version="+plugin.getDescription().getVersion()+", targetSHA256="+sha+", newHelperReference="+reference+", newHelperPresent="+helperPresent;
  }catch(Exception e){return "inspection-failed="+e.getClass().getSimpleName();}
 }
}
