package org.nomiteam.vip;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NomiVIP extends JavaPlugin implements Listener, CommandExecutor {
    private static final class Connection {
        final Player player;
        final FlightSession flight;
        volatile ScheduledTask task;

        Connection(Player player) {
            this.player = player;
            this.flight = new FlightSession(player);
        }
    }

    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("nuomifly")).setExecutor(this);
        Objects.requireNonNull(getCommand("nomivipgrant")).setExecutor((sender, command, label, args) -> {
            sender.sendMessage("会员发货桥不可用，未执行会员变更。");
            return true;
        });
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            MembershipRegistration.register(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getOnlinePlayers().forEach(this::track);
    }

    private void track(Player player) {
        var connection = new Connection(player);
        var previous = connections.put(player.getUniqueId(), connection);
        if (previous != null && previous.task != null) {
            previous.task.cancel();
        }
        // Only this entity's scheduler may read permissions/mode or mutate its flight flags.
        connection.task = player.getScheduler().runAtFixedRate(this, task -> {
            if (current(connection)) {
                connection.flight.enforce();
            } else {
                task.cancel();
            }
        }, () -> connections.remove(player.getUniqueId(), connection), 1, 1);
        if (connection.task == null) {
            connections.remove(player.getUniqueId(), connection);
        }
    }

    private boolean current(Connection connection) {
        return isEnabled() && connection.player.isOnline()
                && connections.get(connection.player.getUniqueId()) == connection;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令仅供玩家切换自己的飞行。");
            return true;
        }
        var connection = connections.get(player.getUniqueId());
        if (connection == null || connection.player != player) {
            return true;
        }
        boolean validArguments = args.length == 0;
        player.getScheduler().run(this, task -> {
            if (!current(connection)) {
                return;
            }
            if (validArguments) {
                connection.flight.toggle();
            } else {
                player.sendMessage("§e用法：/fly（仅切换自己）");
            }
        }, null);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void flyAlias(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        if (parts[0].equalsIgnoreCase("fly")) {
            // Route only our public alias; namespaced commands from other plugins remain native.
            event.setCancelled(true);
            onCommand(event.getPlayer(), getCommand("nuomifly"), "fly",
                    Arrays.copyOfRange(parts, 1, parts.length));
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var connection = connections.get(player.getUniqueId());
        if (connection != null && connection.player == player
                && connections.remove(player.getUniqueId(), connection)) {
            // Quit events execute on the departing player's entity thread.
            connection.flight.stop();
            if (connection.task != null) {
                connection.task.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            var connection = connections.get(player.getUniqueId());
            if (connection != null && connection.player == player && connection.flight.protectFall()) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onDisable() {
        // No entity writes or new tasks from the global disable thread; use a full server restart.
        for (var connection : connections.values()) {
            if (connection.task != null) {
                connection.task.cancel();
            }
        }
        connections.clear();
    }
}
