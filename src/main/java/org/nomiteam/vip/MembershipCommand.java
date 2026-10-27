package org.nomiteam.vip;

import java.time.Clock;
import java.util.UUID;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.command.*;

/** 可信控制台发货入口；玩家、命令方块和远程控制台均不能自行指定目标赋权。 */
final class MembershipCommand implements CommandExecutor {
    private final MembershipGrant grants;
    private final Function<UUID, MembershipFeedback.Audience> audience;
    private final Consumer<Throwable> report;
    private final Clock clock;

    MembershipCommand(MembershipGrant grants, Function<UUID, MembershipFeedback.Audience> audience,
                      Consumer<Throwable> report, Clock clock) {
        this.grants = grants;
        this.audience = audience;
        this.report = report;
        this.clock = clock;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("此会员发货入口仅供本地控制台使用。");
            return true;
        }
        try {
            if (args.length != 3 || !args[0].matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                    || !Set.of("vip", "svip", "mvip").contains(args[1])
                    || !(args[2].equals("30") || args[2].equals("90"))) {
                throw new IllegalArgumentException("用法：nomivipgrant <UUID> <vip|svip|mvip> <30|90>");
            }
            UUID player = UUID.fromString(args[0]);
            // 通知连接仅是可选接收端；不可阻止已支付可信 UUID 的授权/保存。
            MembershipFeedback captured = null;
            try {
                var target = audience.apply(player);
                if (target != null && target.id().equals(player) && target.current()) {
                    captured = new MembershipFeedback(target, clock);
                }
            } catch (RuntimeException failure) {
                report.accept(failure);
            }
            var feedback = captured;
            grants.grant(player, args[1], Integer.parseInt(args[2])).whenComplete((receipt, failure) -> {
                if (failure != null) {
                    report.accept(failure);
                }
                if (feedback != null) feedback.complete(receipt, failure);
            });
        } catch (RuntimeException failure) {
            sender.sendMessage("会员发货请求被拒绝：" + failure.getMessage());
        }
        return true;
    }
}
