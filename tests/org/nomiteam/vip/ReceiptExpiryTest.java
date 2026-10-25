package org.nomiteam.vip;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import me.lucko.luckperms.common.model.*;
import me.lucko.luckperms.common.event.*;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.api.implementation.*;
import net.luckperms.api.model.data.*;
import net.luckperms.api.model.user.*;
import net.luckperms.api.model.group.*;
import net.luckperms.api.node.types.InheritanceNode;
public final class ReceiptExpiryTest {
 // Only cache invalidation/events are isolated. NodeMap, setNode, audit and merge are actual LP5.5.85.
 static final class Holder extends PermissionHolder {
  Holder(LuckPermsPlugin p){super(p,"fixture");}
  public HolderType getType(){return HolderType.GROUP;}
  public String getPlainDisplayName(){return "fixture";}
  public me.lucko.luckperms.lib.adventure.text.Component getFormattedDisplayName(){return null;}
  public net.luckperms.api.query.QueryOptions getQueryOptions(){return null;}
  public me.lucko.luckperms.common.cacheddata.HolderCachedDataManager<?> getCachedData(){return null;}
  protected void invalidateCache(){}
 }
 public static void main(String[] args) throws Exception {
  var dispatcher=new EventDispatcher();
  var plugin=MembershipGrantTest.proxy(LuckPermsPlugin.class,(n,a)->{if(n.equals("getEventDispatcher"))return dispatcher; throw new AssertionError(n);});
  var constructor=ApiPermissionHolder.class.getDeclaredConstructor(PermissionHolder.class); constructor.setAccessible(true);
  var builders=ApiNodeBuilderRegistry.INSTANCE;
  var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  for(String mode:List.of("just-expired","valid-renewal")) {
   var holder=new Holder(plugin);
   var api=constructor.newInstance(holder);
   Instant old=mode.equals("just-expired")?now.minusSeconds(2):now.plus(Duration.ofDays(7));
   var oldNode=builders.forInheritance().group("vip").expiry(old).build();
   holder.normalData().add(oldNode);
   var user=MembershipGrantTest.proxy(net.luckperms.api.model.user.User.class,(n,a)->switch(n){
    case "getUniqueId" -> MembershipGrantTestUUID.ID;
    case "data" -> api.data();
    case "auditTemporaryNodes" -> {api.auditTemporaryNodes();yield null;}
    default -> throw new AssertionError(n);
   });
   int[] saves={0}; var saved=new CompletableFuture<Void>();
   var users=MembershipGrantTest.proxy(UserManager.class,(n,a)->switch(n){
    case "getUser" -> user;
    case "loadUser" -> CompletableFuture.completedFuture(user);
    case "saveUser" -> {saves[0]++;yield saved;}
    default -> throw new AssertionError(n);
   });
   var groups=MembershipGrantTest.proxy(GroupManager.class,(n,a)->MembershipGrantTest.proxy(net.luckperms.api.model.group.Group.class,(x,y)->"vip"));
   var result=new MembershipGrant(users,builders,groups,Clock.fixed(now,ZoneOffset.UTC)).grant(MembershipGrantTestUUID.ID,"vip",30);
   assert saves[0]==1 : mode+": mutation must reach save, failure="+(result.isCompletedExceptionally()?result.handle((r,e)->e).join():null);
   assert !result.isDone(); saved.complete(null);
   Instant expected=(mode.equals("just-expired")?now:old).plus(Duration.ofDays(30));
   // LP computes added duration against wall clock; allow its integer-second execution boundary.
   long delta=Duration.between(expected,result.join().expiry()).getSeconds();
   assert Math.abs(delta)<=2 : mode+" expiry "+result.join().expiry()+" expected "+expected;
   var nodes=api.data().toCollection();
   assert nodes.size()==1 && nodes.iterator().next().getExpiry().equals(result.join().expiry());
   System.out.println("PASS actual LP5.5.85 "+mode+": full purchased duration, merged node persisted after pending save");
  }
 }
 static final class MembershipGrantTestUUID {static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000001");}
}
