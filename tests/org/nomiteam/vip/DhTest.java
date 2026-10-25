package org.nomiteam.vip;

import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;

/** Offline behavioral regression. No server is started. */
public final class DhTest {
    static Object proxy(Class<?> type, java.util.function.Function<Method, Object> fn) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> fn.apply(m));
    }

    static Object allocate(Class<?> type) throws Exception {
        var f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object u = f.get(null);
        return u.getClass().getMethod("allocateInstance", Class.class).invoke(u, type);
    }

    static void check(boolean b, String message) {
        if (!b) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean[] allow = {true}, flying = {false}, bypass = {false};
        GameMode[] mode = {GameMode.SURVIVAL};
        Player p = (Player)proxy(Player.class, m -> switch (m.getName()) {
            case "getAllowFlight" -> allow[0];
            case "isFlying" -> flying[0];
            case "getGameMode" -> mode[0];
            case "hasPermission" -> bypass[0];
            default -> null;
        });
        NomiVIP vip = (NomiVIP)allocate(NomiVIP.class);
        ServicesManager services = (ServicesManager)proxy(ServicesManager.class, m -> null);
        Server server = (Server)proxy(Server.class, m -> m.getName().equals("getServicesManager") ? services : null);
        var sf = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("server");
        sf.setAccessible(true);
        sf.set(vip, server);
        // Verified eligible DH preparation injected at the integration boundary.
        try {
            var f = NomiVIP.class.getDeclaredField("jumpAuthorization");
            f.setAccessible(true);
            f.set(vip, (JumpAuthorization)x -> x.getAllowFlight());
        } catch (NoSuchFieldException ignored) {
        }
        Class<?> session = Class.forName("org.nomiteam.vip.NomiVIP$Session");
        var ctor = session.getDeclaredConstructor(Player.class);
        ctor.setAccessible(true);
        Object s = ctor.newInstance(p);
        var context = NomiVIP.class.getDeclaredMethod("context", session);
        context.setAccessible(true);
        Object c = context.invoke(vip, s);
        var plan = c.getClass().getDeclaredMethod("plan");
        plan.setAccessible(true);
        var state = (FlightTransition.Plan)plan.invoke(c);
        check(state.allowFlight(), "eligible DH preparation must survive tick guard");
        check(!state.flying(), "DH must not grant continuous flight");
        flying[0] = true;
        c = context.invoke(vip, s);
        state = (FlightTransition.Plan)plan.invoke(c);
        check(!state.flying() && state.fallGrace(), "restored sustained flight must be cleared");
        allow[0] = false;
        flying[0] = false;
        c = context.invoke(vip, s);
        check(!((FlightTransition.Plan)plan.invoke(c)).allowFlight(), "consumed jump must not be rearmed by guard");
        var event = NomiVIP.class.getMethod("takeoff", org.bukkit.event.player.PlayerToggleFlightEvent.class).getAnnotation(org.bukkit.event.EventHandler.class);
        check(event.ignoreCancelled() && event.priority() == org.bukkit.event.EventPriority.HIGHEST, "DH NORMAL cancellation must be respected");
        System.out.println("PASS DH integration: preparation, restored flying, consumed jump, cancellation contract");
    }
}
