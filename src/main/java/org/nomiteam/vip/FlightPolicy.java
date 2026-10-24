package org.nomiteam.vip;
/** No persisted flight flags are authorization. A jump lease never authorizes sustained flight. */
public final class FlightPolicy {
 private FlightPolicy(){}
 public record Decision(boolean continuous,boolean allowFlight,boolean keepSession){}
 public static Decision decide(boolean session,boolean global,boolean nativeMode,boolean claim,boolean jump){
  boolean keep=session&&global;
  boolean continuous=nativeMode||claim||keep;
  return new Decision(continuous,continuous||jump,keep);
 }
}
