package org.nomiteam.vip;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.java.JavaPlugin;

/** 可选 LP 接口隔离；LP 不可用不改变会员飞行开关。 */
final class MembershipRegistration {
    private MembershipRegistration() {}

    static void register(JavaPlugin plugin) {
        var api = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (api == null) {
            plugin.getLogger().warning("LuckPerms API 未就绪，会员完成回执桥未启用。");
            return;
        }
        var clock = Clock.systemUTC();
        var grants = new MembershipGrant(api.getUserManager(), api.getNodeBuilderRegistry(), api.getGroupManager(), clock);
        var executor = new MembershipCommand(grants, id -> {
            var player = plugin.getServer().getPlayer(id);
            if (player == null) {
                return null;
            }
            // 固定本次连接对象；相同 UUID 退出后重连，不接收旧回调。
            return new MembershipFeedback.Audience() {
                public UUID id() {
                    return id;
                }
                public boolean current() {
                    return plugin.isEnabled() && player.isOnline()
                            && plugin.getServer().getPlayer(id) == player;
                }
                public void dispatch(Runnable message) {
                    if (plugin.isEnabled()) {
                        player.getScheduler().run(plugin, task -> message.run(), null);
                    }
                }
                public void message(String text) {
                    player.sendMessage(text);
                }
            };
        }, failure -> plugin.getLogger().log(Level.WARNING,
                "会员变更或保存未能确认成功；未发成功提示，不自动重试或扣费。", failure), clock);
        Objects.requireNonNull(plugin.getCommand("nomivipgrant")).setExecutor(executor);
    }
}
