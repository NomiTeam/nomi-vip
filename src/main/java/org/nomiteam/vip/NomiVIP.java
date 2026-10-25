package org.nomiteam.vip;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class NomiVIP extends JavaPlugin implements Listener, CommandExecutor {
    private static final class Session {
        final Player player;
        volatile ScheduledTask task;
        volatile boolean active;
        long fallUntil;
        Session(Player p) {
            player = p;
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private boolean residence;
    private JumpAuthorization jumpAuthorization;
    private DeluxeHubJump deluxeHubJump;
    @Override
    public void onEnable() {
        var bridge = getServer().getPluginManager().getPlugin("MenuReturnBridge");
        if (bridge != null && bridge.getDescription().getCommands() != null && (bridge.getDescription().getCommands().containsKey("nuomifly") || bridge.getDescription().getCommands().containsKey("nuomiscale"))) {
            getLogger().severe("检测到旧 Bridge 仍声明会员命令。请先由维护者移除旧命令和 VipControls 注册后重启；本插件拒绝双重接管。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        deluxeHubJump = new DeluxeHubJump(getServer().getPluginManager().getPlugin("DeluxeHub"));
        jumpAuthorization = deluxeHubJump;
        getLogger().info(deluxeHubJump.status());
        residence = getServer().getPluginManager().isPluginEnabled("Residence");
        for (String name : List.of("nuomiscale", "nuomifly", "nomivip")) {
            Objects.requireNonNull(getCommand(name)).setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getOnlinePlayers().forEach(this::track);
    }

    private void track(Player p) {
        Session s = new Session(p), old = sessions.put(p.getUniqueId(), s);
        if (old != null && old.task != null) {
            old.task.cancel();
        }
        s.task = p.getScheduler().runAtFixedRate(this, t -> {
            if (current(s)) {
                enforce(s);
            } else {
                t.cancel();
            }
        }, () -> sessions.remove(p.getUniqueId(), s), 1, 1);
        if (s.task == null) {
            sessions.remove(p.getUniqueId(), s);
        }
    }

    private boolean current(Session s) {
        return isEnabled() && s.player.isOnline() && sessions.get(s.player.getUniqueId()) == s;
    }

    private void later(Player p, java.util.function.Consumer<Session> action) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.player != p) {
            return;
        }
        p.getScheduler().run(this, t -> {
            if (current(s)) {
                action.accept(s);
            }
        }, null);
    }

    private static boolean nativeMode(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    private record Context(FlightPolicy.Decision decision, String claim, boolean noFly, boolean jump, FlightTransition.Plan plan) {
    }
    // 会话开关只表达主动意愿；授权每次取有效权限和当前位置，绝不反推持久化 flags。
    private Context context(Session s) {
        Player p = s.player;
        var r = residence && getServer().getPluginManager().isPluginEnabled("Residence") ? ResidenceHook.read(p) : new ResidenceHook.State(false, false, "unavailable");
        boolean jump = jumpAuthorization != null && jumpAuthorization.mayDoubleJump(p);
        boolean blocked = r.noFly() && !nativeMode(p);
        var d = FlightPolicy.decide(s.active, p.hasPermission("nuomi.vip.fly.global"), nativeMode(p), r.claimFlight(), jump && !blocked);
        if (blocked) {
            d = new FlightPolicy.Decision(false, false, d.keepSession());
        }
        var plan = FlightTransition.plan(s.active, p.hasPermission("nuomi.vip.fly.global"), nativeMode(p), r.claimFlight(), jump, r.noFly(), p.getAllowFlight(), p.isFlying());
        return new Context(d, r.claim(), r.noFly(), jump, plan);
    }

    private void enforce(Session s) {
        Player p = s.player;
        Context c = context(s);
        var plan = c.plan();
        boolean revoked = s.active && !plan.active();
        s.active = plan.active();
        if (plan.fallGrace()) {
            p.setFlying(false);
            p.setFallDistance(0);
            s.fallUntil = System.nanoTime()+10_000_000_000L;
        }
        if (p.getAllowFlight() != plan.allowFlight()) {
            p.setAllowFlight(plan.allowFlight());
        }
        ScaleControl.enforce(p);
        if (revoked) {
            p.sendMessage("§e全图飞行权限已失效，会员飞行已关闭。");
        }
    }

    private void toggle(Session s) {
        Player p = s.player;
        if (nativeMode(p)) {
            p.sendMessage("§e当前游戏模式自带飞行。");
            return;
        }
        if (s.active) {
            s.active = false;
            enforce(s);
            p.sendMessage("§e会员飞行已关闭；合法领地飞行仍由领地规则决定。");
            return;
        }
        if (!p.hasPermission("nuomi.vip.fly.global")) {
            enforce(s);
            p.sendMessage("§c你没有全图飞行权限。领地飞行由 Residence 的 fly 标志决定。");
            return;
        }
        s.active = true;
        enforce(s);
        if (context(s).decision().continuous()) {
            p.setFlying(true);
            p.sendMessage("§a会员全图飞行已开启（仅当前会话）。");
        } else {
            p.sendMessage("§e会员飞行已开启，当前位置的领地规则禁止飞行。");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("nomivip")) {
            return audit(sender, args);
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("此命令仅供玩家调整自己的状态。");
            return true;
        }
        later(p, s -> {
            if (command.getName().equals("nuomifly")) {
                if (args.length != 0) {
                    p.sendMessage("§e用法：/fly（仅切换自己）");
                } else {
                    toggle(s);
                }
                return;
            }
            ScaleControl.change(p, args);
        });
        return true;
    }

    private boolean audit(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("此只读审计命令仅供控制台使用。");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("selftest")) {
            try {
                for (String n : PolicyChecks.CASES) {
                    PolicyChecks.run(n);
                }
                sender.sendMessage("NomiVIP 领域自检通过："+PolicyChecks.CASES.size()+" 组；不代表服务端集成验收。");
            } catch (Throwable e) {
                sender.sendMessage("NomiVIP 自检失败："+e);
                getLogger().log(java.util.logging.Level.SEVERE, "Selftest failed", e);
            }
            return true;
        }
        if (args.length>=1 && args[0].equalsIgnoreCase("status") && args.length<=2) {
            sender.sendMessage("Residence: "+CompatibilityReport.inspect(getServer().getPluginManager().getPlugin("Residence"), "com/bekvon/bukkit/residence/protection/ResidencePermissions.class", "org/nomiteam/vip/compat/ResidenceGrant"));
            sender.sendMessage("PlayerWarp: "+CompatibilityReport.inspect(getServer().getPluginManager().getPlugin("PlayerWarp"), "cn/handyplus/warp/inventory/CreateGui.class", "org/nomiteam/vip/compat/WarpPricing"));
            var fly = getServer().getPluginCommand("fly");
            sender.sendMessage("fly command owner="+(fly == null ? "none" : fly.getPlugin().getName()));
            sender.sendMessage("NomiVIP 1.0.0；Residence="+residence+"；DH="+deluxeHubJump.status()+"；tracked="+sessions.size());
            for (Session s : sessions.values()) {
                if (args.length == 1 || s.player.getName().equalsIgnoreCase(args[1])) {
                    later(s.player, v -> {
                        var c = context(v);
                        var p = v.player;
                        sender.sendMessage(p.getName()+": session="+v.active+", global="+p.hasPermission("nuomi.vip.fly.global")+", allow="+p.getAllowFlight()+", flying="+p.isFlying()+", mode="+p.getGameMode()+", claim="+c.claim()+", nofly="+c.noFly()+", jump="+c.jump()+", policy="+c.decision());
                    });
                }
            }
            return true;
        }
        sender.sendMessage("用法：nomivip status [玩家] | nomivip selftest");
        return true;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        track(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && s.player == e.getPlayer() && sessions.remove(e.getPlayer().getUniqueId(), s)) {
            s.active = false;
            if (s.task != null) {
                s.task.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void world(PlayerChangedWorldEvent e) {
        later(e.getPlayer(), this::enforce);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent e) {
        later(e.getPlayer(), this::enforce);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        later(e.getPlayer(), this::enforce);
    }
    // DH 在 NORMAL 取消并消费合法单次双跳；HIGHEST 忽略已取消事件，不补发 mayfly。
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void takeoff(PlayerToggleFlightEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && s.player == e.getPlayer() && e.isFlying() && !context(s).decision().continuous()) {
            e.setCancelled(true);
        }
        later(e.getPlayer(), this::enforce);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().substring(1).trim().split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        if (Set.of("fly", "essentials:fly", "essentialsc:fly", "nomivip:fly").contains(name)) {
            e.setCancelled(true);
            if (parts.length != 1) {
                e.getPlayer().sendMessage("§c只能切换自己的飞行状态。用法：/fly");
                return;
            }
            later(e.getPlayer(), this::toggle);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Session s = sessions.get(p.getUniqueId());
            if (s != null && s.player == p && s.fallUntil != 0) {
                if (System.nanoTime()<=s.fallUntil) {
                    e.setCancelled(true);
                }
                s.fallUntil = 0;
            }
        }
    }

    @Override
    public void onDisable() {
        sessions.values().forEach(s -> {
            s.active = false;
            if (s.task != null) {
                s.task.cancel();
            }
        });
        sessions.clear();
    }
}
