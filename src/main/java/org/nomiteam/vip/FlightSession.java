package org.nomiteam.vip;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** One connection's opt-in state. Every method runs on that player's entity thread. */
final class FlightSession {
    private final Player player;
    private boolean active;
    private boolean grantedAllowFlight;
    private boolean startedFlying;
    private long fallUntil;

    FlightSession(Player player) {
        this.player = player;
    }

    private boolean nativeMode() {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    void toggle() {
        if (nativeMode()) {
            player.sendMessage("§e当前游戏模式自带飞行。");
            return;
        }
        if (active) {
            stop();
            player.sendMessage("§e会员飞行已关闭。");
            return;
        }
        if (!player.hasPermission("nuomi.vip.fly.global")) {
            player.sendMessage("§c你没有会员飞行权限。");
            return;
        }
        active = true;
        grantedAllowFlight = !player.getAllowFlight();
        startedFlying = !player.isFlying();
        if (grantedAllowFlight) {
            player.setAllowFlight(true);
        }
        if (startedFlying) {
            player.setFlying(true);
        }
        player.sendMessage("§a会员飞行已开启（仅当前会话）。");
    }

    void enforce() {
        // Inactive sessions never inspect or rewrite flight flags owned by native commands/plugins.
        if (!active) {
            return;
        }
        if (nativeMode()) {
            forget();
        } else if (!player.hasPermission("nuomi.vip.fly.global")) {
            stop();
            player.sendMessage("§e会员飞行权限已失效，主动飞行已关闭。");
        }
    }

    void stop() {
        if (!active) {
            return;
        }
        if (!nativeMode()) {
            boolean wasFlying = player.isFlying();
            // Remove only state this switch supplied; pre-existing native flight is not ours.
            if (grantedAllowFlight || startedFlying) {
                player.setFlying(false);
                if (wasFlying) {
                    player.setFallDistance(0);
                    fallUntil = System.nanoTime() + 10_000_000_000L;
                }
            }
            if (grantedAllowFlight) {
                player.setAllowFlight(false);
            }
        }
        forget();
    }

    private void forget() {
        active = false;
        grantedAllowFlight = false;
        startedFlying = false;
    }

    boolean protectFall() {
        boolean protect = fallUntil != 0 && System.nanoTime() <= fallUntil;
        fallUntil = 0;
        return protect;
    }
}
