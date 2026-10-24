package org.nomiteam.vip.compat;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import cn.handyplus.warp.api.PlayerWarpApi;
import cn.handyplus.warp.hook.PlayerPointsUtil;
import cn.handyplus.warp.hook.VaultUtil;
import cn.handyplus.warp.hook.PlayerCurrencyUtil;
import org.nomiteam.vip.WarpPolicy;
import org.nomiteam.vip.QuoteBook;
/** Injected into PlayerWarp's classloader. Prices always use the current owned entry count. */
public final class WarpPricing {
 private static final QuoteBook QUOTES=new QuoteBook();
 private static final Object[] LOCKS=new Object[64];
 static {java.util.Arrays.setAll(LOCKS,i->new Object());}
 public static Object lock(Player p){return LOCKS[(p.getUniqueId().hashCode()&Integer.MAX_VALUE)%LOCKS.length];}
 private WarpPricing(){}
 public static int quota(Player p){return WarpPolicy.quota(p.hasPermission("nuomi.warp.free.2"),p.hasPermission("nuomi.warp.free.4"),p.hasPermission("nuomi.warp.free.6"));}
 public static int remaining(Player p){int q=quota(p);return q==0?0:WarpPolicy.remaining(q,PlayerWarpApi.getPlayerWarps(p.getUniqueId()).size());}
 public static int limit(int original,Player p){return p.hasPermission("nuomi.warp.paid")?Integer.MAX_VALUE:original;}
 public static long quote(long original,Player p,String currency){
  long cost=remaining(p)>0?0:original;QUOTES.offer(p.getUniqueId().toString(),currency,cost,System.nanoTime());return cost;
 }
 private static boolean accept(Player p,String currency,long cost){
  if(QUOTES.consume(p.getUniqueId().toString(),currency,cost,System.nanoTime()))return true;
  var plugin=Bukkit.getPluginManager().getPlugin("PlayerWarp");
  if(plugin!=null&&plugin.isEnabled())p.getScheduler().run(plugin,t->p.sendMessage("§e地标创建价格已变化或报价已过期，请重新打开创建界面后确认。未扣费。"),null);
  return false;
 }
 public static boolean takePoints(Player p,int original){int cost=remaining(p)>0?0:original;return accept(p,"point",cost)&&(cost==0||PlayerPointsUtil.take(p,cost));}
 public static boolean takeVault(Player p,int original){int cost=remaining(p)>0?0:original;return accept(p,"vault",cost)&&(cost==0||VaultUtil.take(p,cost));}
 public static boolean takeCurrency(Player p,String currency,long original,String extra){long cost=remaining(p)>0?0:original;return accept(p,"ply",cost)&&(cost==0||PlayerCurrencyUtil.take(p,currency,cost,extra));}
}
