package org.nomiteam.vip;

import java.util.UUID;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/** 7yPay 1.1.55 的 %player% 适配；不将订单事件或命令派发当作保存成功。 */
final class MembershipNameCommand implements CommandExecutor {
    private final Function<String, UUID> onlineIdentity;
    private final CommandExecutor grants;

    MembershipNameCommand(Function<String, UUID> onlineIdentity, CommandExecutor grants) {
        this.onlineIdentity = onlineIdentity;
        this.grants = grants;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("此会员发货入口仅供本地控制台使用。");
            return true;
        }
        if (args.length != 3 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            sender.sendMessage("用法：nomivipgrantname <在线玩家精确名称> <vip|svip|mvip> <30|90>");
            return true;
        }
        // 只解析当前在线账号，禁止离线名称推算 UUID 或模糊匹配。
        UUID id = onlineIdentity.apply(args[0]);
        if (id == null) {
            sender.sendMessage("会员发货请求被拒绝：购买者当前连接不可用，未执行会员变更");
            return true;
        }
        return grants.onCommand(sender, command, label,
                new String[]{id.toString(), args[1], args[2]});
    }
}
