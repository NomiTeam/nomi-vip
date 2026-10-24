package org.nomiteam.vip;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.Plugin;

/** Read-only adapter for the audited 3.8.5 listener. Never arms or launches a jump. */
final class DeluxeHubJump implements JumpAuthorization {
 private static final String BASE="net.zithium.deluxehub.";
 private final Plugin plugin;
 private Method manager,getModule,disabled;
 private Class<?> moduleClass;
 private Object moduleType;
 private volatile String status="closed: DeluxeHub absent";
 DeluxeHubJump(Plugin plugin){
  this.plugin=plugin;
  if(plugin==null)return;
  try{
   if(!plugin.getDescription().getVersion().equals("3.8.5"))throw new IllegalStateException("unsupported version");
   var loader=plugin.getClass().getClassLoader();
   for(var e:Map.of("module/modules/player/DoubleJump","0614cc90d195183170cbd61be17fb01c915c38e1f45754b738729bfbe090b486","module/Module","23876be99ffaea9403124150ccffb2340ba1333bc54ec4984df231f0b20c994c","module/ModuleManager","aae1b1af82a68d2c4fcb9a32fb6e4e7133302a3ee8280e137b345ae058bf22ff","Permissions","5775e00a8cf05db7bd90358989e3b7e99dd29bbfe2e9a5fad832045787b75833").entrySet()){
    try(var in=loader.getResourceAsStream(BASE.replace('.','/')+e.getKey()+".class")){
     if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(e.getValue()))throw new IllegalStateException("unsupported bytecode");
    }
   }
   Class<?> main=loader.loadClass(BASE+"DeluxeHubPlugin"),type=loader.loadClass(BASE+"module.ModuleType");
   if(!main.isInstance(plugin)||!type.isEnum())throw new IllegalStateException("unexpected types");
   moduleType=type.getField("DOUBLE_JUMP").get(null);
   manager=main.getMethod("getModuleManager");
   getModule=loader.loadClass(BASE+"module.ModuleManager").getMethod("getModule",type);
   moduleClass=loader.loadClass(BASE+"module.modules.player.DoubleJump");
   disabled=moduleClass.getMethod("inDisabledWorld",Location.class);
   if(disabled.getReturnType()!=boolean.class)throw new IllegalStateException("unexpected world result");
   status="ready: DeluxeHub 3.8.5 verified; live module/world/bypass checked per player";
  }catch(Exception|LinkageError e){manager=null;status="closed: DH adapter validation failed ("+e.getClass().getSimpleName()+"); check supported version/bytecode/dependencies";}
 }
 String status(){
  if(manager==null)return status;
  if(!plugin.isEnabled())return "closed: DeluxeHub disabled";
  try{
   Object owner=manager.invoke(plugin);
   if(!getModule.getDeclaringClass().isInstance(owner)||!moduleClass.isInstance(getModule.invoke(owner,moduleType)))return "closed: DH double jump module disabled/unavailable";
  }catch(Exception|LinkageError e){return "closed: DH live module query failed ("+e.getClass().getSimpleName()+")";}
  return status;
 }
 @Override public boolean mayDoubleJump(Player player){
  if(manager==null||!plugin.isEnabled())return false;
  try{
   Object owner=manager.invoke(plugin);
   if(!getModule.getDeclaringClass().isInstance(owner))return false;
   Object module=getModule.invoke(owner,moduleType);
   if(module==null||module.getClass()!=moduleClass)return false;
   // A module remaining in a stale manager is not sufficient authorization.
   boolean registered=false;
   for(var listener:PlayerToggleFlightEvent.getHandlerList().getRegisteredListeners())
    if(listener.getPlugin()==plugin&&listener.getListener()==module&&listener.getPriority()==EventPriority.NORMAL)registered=true;
   return registered&&preserve(module,disabled,player);
  }catch(Exception|LinkageError e){status="closed: DH live query failed ("+e.getClass().getSimpleName()+")";return false;}
 }
 static boolean preserve(Object module,Method disabled,Player player)throws ReflectiveOperationException{
  if(player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR||player.hasPermission("deluxehub.bypass.doublejump"))return false;
  Object result=disabled.invoke(module,player.getLocation());
  // Preserve only existing preparation. DH clears it after launch and rearms on movement.
  return result instanceof Boolean blocked&&!blocked&&player.getAllowFlight();
 }
}
