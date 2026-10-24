package org.nomiteam.vip.compat;
import java.util.UUID;
import org.bukkit.Bukkit;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
/** Injected into Residence's own classloader. No hidden external helper is required. */
public final class ResidenceGrant {
 private ResidenceGrant(){}
 public static boolean grants(UUID id,String flag,ClaimedResidence claim){
  if(id==null||claim==null||!"fly".equalsIgnoreCase(flag)||!Flags.fly.isGlobalyEnabled())return false;
  var p=Bukkit.getPlayer(id);var r=Residence.getInstance();
  return p!=null&&p.isOnline()&&r!=null&&!r.isDisabledWorldListener(p.getLocation())
   &&claim.containsLoc(p.getLocation())&&p.hasPermission("nuomi.vip.fly.anyres");
 }
}
