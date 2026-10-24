package org.nomiteam.vip;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
/** Executes the owner-supplied DH classes with isolated Bukkit boundary doubles. */
public final class DhBytecodeTest {
 static void field(Object target,Class<?> owner,String name,Object value)throws Exception{var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 public static void main(String[] args)throws Exception{
  String base="net.zithium.deluxehub.";
  Class<?> type=Class.forName(base+"module.modules.player.DoubleJump"),parent=type.getSuperclass();
  Object module=DhTest.allocate(type),plugin=DhTest.allocate(Class.forName(base+"DeluxeHubPlugin"));
  field(module,parent,"plugin",plugin);field(module,parent,"disabledWorlds",List.of("excluded-fixture"));
  field(module,parent,"cooldownManager",Class.forName(base+"cooldown.CooldownManager").getConstructor().newInstance());
  field(plugin,plugin.getClass(),"actionManager",DhTest.allocate(Class.forName(base+"action.ActionManager")));
  field(module,type,"actions",List.of());field(module,type,"launch",1.0);field(module,type,"launchY",0.9);field(module,type,"cooldownDelay",3L);
  // Material's server registry is a boundary double; STICK represents a non-solid block here.
  field(Material.STICK,Material.class,"blockType",(java.util.function.Supplier<Object>)()->null);
  Block block=(Block)DhTest.proxy(Block.class,m->m.getName().equals("getType")?Material.STICK:null);
  String[] worldName={"enabled-fixture"};
  World world=(World)DhTest.proxy(World.class,m->m.getName().equals("getName")?worldName[0]:null);
  Location location=new Location(world,0,64,0){@Override public Block getBlock(){return block;}};
  boolean[] allow={true},bypass={false};GameMode[] mode={GameMode.SURVIVAL};int[] launches={0};
  UUID uuid=UUID.randomUUID();
  Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getAllowFlight"->allow[0];case "setAllowFlight"->{allow[0]=(Boolean)a[0];yield null;}case "getGameMode"->mode[0];case "hasPermission"->bypass[0];case "getLocation"->location.clone();case "getUniqueId"->uuid;case "setVelocity"->{launches[0]++;yield null;}default->null;});
  Class<?> javaPlugin=org.bukkit.plugin.java.JavaPlugin.class;
  field(plugin,javaPlugin,"description",new org.bukkit.plugin.PluginDescriptionFile("DeluxeHub","3.8.5",plugin.getClass().getName()));
  field(plugin,javaPlugin,"isEnabled",true);
  Object manager=Class.forName(base+"module.ModuleManager").getConstructor().newInstance();
  field(plugin,plugin.getClass(),"moduleManager",manager);
  Class<?> mt=Class.forName(base+"module.ModuleType");Object key=mt.getField("DOUBLE_JUMP").get(null);
  field(manager,manager.getClass(),"modules",new HashMap<>(Map.of(key,module)));
  var adapter=new DeluxeHubJump((org.bukkit.plugin.Plugin)plugin);
  var registered=new org.bukkit.plugin.RegisteredListener((org.bukkit.event.Listener)module,(l,e)->{},org.bukkit.event.EventPriority.NORMAL,(org.bukkit.plugin.Plugin)plugin,false);
  PlayerToggleFlightEvent.getHandlerList().register(registered);
  DhTest.check(adapter.mayDoubleJump(player),"adapter must bind real verified live module");
  PlayerToggleFlightEvent.getHandlerList().unregister(registered);
  DhTest.check(!adapter.mayDoubleJump(player),"unregistered module fails closed");
  PlayerToggleFlightEvent.getHandlerList().register(registered);
  field(plugin,javaPlugin,"isEnabled",false);DhTest.check(!adapter.mayDoubleJump(player),"disabled plugin fails closed");field(plugin,javaPlugin,"isEnabled",true);
  field(manager,manager.getClass(),"modules",new HashMap<>());DhTest.check(!adapter.mayDoubleJump(player),"disabled module fails closed");field(manager,manager.getClass(),"modules",new HashMap<>(Map.of(key,module)));
  DhTest.check(!new DeluxeHubJump(null).mayDoubleJump(player),"absent dependency fails closed");
  field(plugin,javaPlugin,"description",new org.bukkit.plugin.PluginDescriptionFile("DeluxeHub","3.8.6",plugin.getClass().getName()));
  DhTest.check(!new DeluxeHubJump((org.bukkit.plugin.Plugin)plugin).mayDoubleJump(player),"unsupported version fails closed");
  field(plugin,javaPlugin,"description",new org.bukkit.plugin.PluginDescriptionFile("DeluxeHub","3.8.5",plugin.getClass().getName()));
  Method disabled=type.getMethod("inDisabledWorld",Location.class),toggle=type.getMethod("onPlayerToggleFlight",PlayerToggleFlightEvent.class);
  DhTest.check(DeluxeHubJump.preserve(module,disabled,player),"real module eligible");
  var event=new PlayerToggleFlightEvent(player,true);toggle.invoke(module,event);
  DhTest.check(event.isCancelled()&&launches[0]==1&&!allow[0],"real DH must cancel, launch once, consume mayfly");
  DhTest.check(!DeluxeHubJump.preserve(module,disabled,player),"adapter must preserve consumption");
  type.getMethod("onPlayerMove",PlayerMoveEvent.class).invoke(module,new PlayerMoveEvent(player,new Location(world,0,63,0),new Location(world,0,64,0)));
  DhTest.check(!allow[0],"real DH airborne movement must not rearm");
  NomiVIP vip=(NomiVIP)DhTest.allocate(NomiVIP.class);field(vip,javaPlugin,"isEnabled",true);
  var guardAnnotation=NomiVIP.class.getMethod("takeoff",PlayerToggleFlightEvent.class).getAnnotation(org.bukkit.event.EventHandler.class);
  var guard=new org.bukkit.plugin.RegisteredListener(vip,(l,e)->vip.takeoff((PlayerToggleFlightEvent)e),guardAnnotation.priority(),vip,guardAnnotation.ignoreCancelled());
  guard.callEvent(event); // If wrongly dispatched, the deliberately uninitialized session map fails.
  DhTest.check(event.isCancelled()&&launches[0]==1,"DH cancellation and launch survive actual guard dispatch");
  var messages=Class.forName(base+"config.Messages");
  field(null,messages,"config",new org.bukkit.configuration.file.FileConfiguration(){@Override public Object get(String path){return "";} @Override public String saveToString(){return "";} @Override public void loadFromString(String text){} @Override protected String buildHeader(){return "";}});
  allow[0]=true;event=new PlayerToggleFlightEvent(player,true);toggle.invoke(module,event);
  DhTest.check(event.isCancelled()&&launches[0]==1&&allow[0],"cooldown cancels without launch and retains preparation");
  for(GameMode gm:List.of(GameMode.CREATIVE,GameMode.SPECTATOR)){
   mode[0]=gm;DhTest.check(!DeluxeHubJump.preserve(module,disabled,player),"native mode is not DH source");event=new PlayerToggleFlightEvent(player,true);toggle.invoke(module,event);DhTest.check(!event.isCancelled(),"DH native bypass");
  }
  mode[0]=GameMode.ADVENTURE;bypass[0]=true;DhTest.check(!DeluxeHubJump.preserve(module,disabled,player),"permission bypass");event=new PlayerToggleFlightEvent(player,true);toggle.invoke(module,event);DhTest.check(!event.isCancelled(),"real permission bypass");
  bypass[0]=false;worldName[0]="excluded-fixture";DhTest.check(!DeluxeHubJump.preserve(module,disabled,player),"disabled world");event=new PlayerToggleFlightEvent(player,true);toggle.invoke(module,event);DhTest.check(!event.isCancelled(),"real disabled world bypass");
  worldName[0]="enabled-fixture";DhTest.check(DeluxeHubJump.preserve(module,disabled,player),"adventure eligible");
  var annotation=toggle.getAnnotation(org.bukkit.event.EventHandler.class);DhTest.check(annotation.priority()==org.bukkit.event.EventPriority.NORMAL&&!annotation.ignoreCancelled(),"real DH priority");
  PlayerToggleFlightEvent.getHandlerList().unregister(registered);
  System.out.println("PASS real DH bytecode: live adapter binding/fail-closed, guard dispatch, launch/consumption, cooldown, survival/adventure, native modes, bypass, disabled world, NORMAL priority");
 }
}
