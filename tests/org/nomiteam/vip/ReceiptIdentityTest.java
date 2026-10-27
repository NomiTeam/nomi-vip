package org.nomiteam.vip;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.model.user.*;
import org.bukkit.command.ConsoleCommandSender;
public final class ReceiptIdentityTest {
 public static void main(String[] args) throws Exception {
  var builders=(NodeBuilderRegistry)Class.forName("me.lucko.luckperms.common.api.implementation.ApiNodeBuilderRegistry").getField("INSTANCE").get(null);
  UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");
  for(String mode:List.of("offline-before-delivery","stale-before-delivery","cache-empty-load-pending","wrong-audience","invalidated-during-load")) {
   var f=new MembershipGrantTest.Fixture(builders);
   var loaded=new CompletableFuture<User>(); int[] loads={0};
   var users=MembershipGrantTest.proxy(UserManager.class,(name,a)->switch(name){
    case "getUser" -> null;
    case "loadUser" -> {assert a[0].equals(id); loads[0]++; yield loaded;}
    case "saveUser" -> {assert a[0]==f.user; f.saves++; yield f.saved;}
    default -> throw new AssertionError(name);
   });
   var service=new MembershipGrant(users,builders,f.groups,Clock.systemUTC());
   var viewer=new MembershipFeedbackTest.Viewer(); viewer.current=mode.equals("invalidated-during-load");
   var audience=new MembershipFeedback.Audience(){
    public UUID id(){return mode.equals("wrong-audience")?UUID.randomUUID():id;}
    public boolean current(){return viewer.current;}
    public void dispatch(Runnable r){viewer.dispatch(r);}
    public void message(String s){viewer.message(s);}
   };
   List<Throwable> errors=new ArrayList<>();
   var cmd=new MembershipCommand(service,target->mode.equals("offline-before-delivery")?null:audience,errors::add,Clock.systemUTC());
   cmd.onCommand(MembershipGrantTest.proxy(ConsoleCommandSender.class,(n,a)->null),null,"nomivipgrant",new String[]{id.toString(),"vip","30"});
   assert loads[0]==1 : mode+": trusted UUID must load asynchronously without connection/cache";
   assert f.adds==0 && f.saves==0;
   viewer.current=false;
   loaded.complete(f.user);
   assert f.adds==1 && f.saves==1 : mode;
   f.saved.complete(null); viewer.flush();
   assert viewer.messages.isEmpty() && errors.isEmpty() : mode;
   System.out.println("PASS "+mode+": load, grant, save complete; no stale notification");
  }
 }
}
