package org.nomiteam.vip;
/** Complete output state, including flags restored outside the command path. */
public final class FlightTransition {
 private FlightTransition(){}
 public record Plan(boolean active,boolean allowFlight,boolean flying,boolean fallGrace){}
 public static Plan plan(boolean active,boolean global,boolean nativeMode,boolean claim,boolean jump,boolean nofly,boolean allow,boolean flying){
  var d=FlightPolicy.decide(active,global,nativeMode,claim,jump);
  boolean continuous=d.continuous()&&(!nofly||nativeMode);
  boolean mayfly=d.allowFlight()&&(!nofly||nativeMode);
  return new Plan(d.keepSession(),mayfly,flying&&continuous,flying&&!continuous);
 }
}
